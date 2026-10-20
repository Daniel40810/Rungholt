package com.dan.ocean.gl;

import com.dan.ocean.Obstacle;
import com.dan.ocean.Ocean;
import com.dan.ocean.Ripples;
import com.dan.ocean.Spectrum;
import com.dan.ocean.Swell;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet die See aus {@link Ocean} mit JOGL (OpenGL 4.3, Compute-Shader). Je Bild:
 * <ol>
 * <li>{@code spectrum.comp}: h(k, t) aus h₀ für alle Kaskaden, acht Felder als vier komplexe Paare,</li>
 * <li>{@code fft.comp}: inverse FFT über Zeilen, dann Spalten (geteilter Speicher, Stockham),</li>
 * <li>{@code assemble.comp}: Auslenkung, Ableitungen und Schaum mit Nachleuchten, dann Mipmaps,</li>
 * <li>{@code surface.vert/frag}: das Ringgitter um die Kamera mit der ganzen Optik.</li>
 * </ol>
 * Die Startamplituden kommen aus dem Kern; ändert sich dort das Spektrum ({@link Ocean#version()}), werden sie neu
 * hochgeladen. Benutzung: {@code init} einmal im GL-Faden, {@code update} und {@code draw} je Bild, {@code dispose}.
 */
public final class OceanGl {
    /** Blick: Projektion·Blick, Auge, Tiefenkonvention. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
    }

    /**
     * Licht: Sonne und Mond (Richtung zum Gestirn, Farbe linear), der Himmel als Panorama-Textur (RGBA, Azimut von
     * Nord über die Breite, Höhe −90°…+90° über die Höhe), Sichtweite des Dunstes. Maßstab: Sonne und Mond als
     * Bestrahlung, der Himmel als Strahldichte in denselben Einheiten (so liefert es {@code com.dan.sky}); das Wasser
     * gibt rund 1 % je Steradiant zurück, Schaum ist matt mit Albedo 0,6.
     */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public int skyTexture;
        public float skyGain = 1;
        public double fogDistance = 40_000;
        /** Wasserfarbe (Streulicht) und Farbe im durchscheinenden Kamm; Vorgabe Nordsee. */
        public double[] waterColor = {0.020, 0.072, 0.066}, sssColor = {0.05, 0.30, 0.24};
        /** Schattenkarte von oben (Anteil direkten Lichts, Kanal R; 0 = keine) und ihr Rechteck: x, z der Ecke, Kantenlänge in m. */
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 1};
        /**
         * Zweite Schattenkarte für den Schatten eines Körpers auf dem Wasser (Turm, Schiff): Anteil direkten Lichts,
         * Kanal R, 0 = keine; Rechteck wie oben. Gilt nur für das Sonnen- bzw. Mondlicht, nicht für den Himmel.
         */
        public int bodyShadowTexture;
        public double[] bodyShadowRect = {0, 0, 1};
        /**
         * Feine Störung der Fläche (Regenringe, Tropfen, Wind auf glattem Wasser): kachelbare Steigungstextur
         * (dh/dx, dh/dz in RG, 0 = keine), Kantenlänge einer Kachel in m, Stärke 0..1 und die Entfernung in m, über
         * die sie ausklingt.
         */
        public int detailTexture;
        public double detailTile = 3, detailStrength, detailRange = 60;
        /**
         * Ein Punktlicht (Leuchtfeuer, Lampe) mit Lichtverteilung: Ort in m und eine Rundum-Textur der Lichtstärke in
         * cd je Richtung vom Licht weg (RGB; u = Azimut von Nord / 360°, v = ½ + ½ · sign(h) · √(|h| / 90°), h =
         * Höhenwinkel), dazu ein Faktor (z. B. die Vorbelichtung) und die Auslöschung der Luft in 1/m. 0 = keins.
         * Es gibt Glitzer auf den Wellen und Licht auf dem Schaum.
         */
        public int pointTexture;
        public double[] pointPos = {0, 0, 0};
        public double pointGain = 1, pointSigma;
        /**
         * Bis zu acht einfache Punktlichter ohne Lichtverteilung (Blitze): Ort in m und Größe der Quelle in m (x, y, z,
         * R), Lichtstärke als RGB in cd mal Vorbelichtung (r, g, b, —). Sie beleuchten Wasser und Schaum; den
         * Glanz der Quelle selbst zeigt die Spiegelung des Rundumbilds.
         */
        public float[] flashPos = new float[32], flashRgb = new float[32];
        public int flashCount;
        /**
         * Seit Haithabu (Phase 2): der Grund unter dem Wasser, optional. Eine Höhentextur (R, normiert oder float)
         * über dem Rechteck bedRect (x, z der Nordwestecke, Kantenlänge in m); Höhe = Wert · bedScale + bedOffset. Mit
         * ihr dämpft das Paket die Wellen zum Ufer hin (unter 1,5 m Tiefe), zeigt im Flachwasser den Grund (Trübung
         * bedTurbidity je m) und legt einen Schaumsaum an das Ufer. Außerhalb des Rechtecks und über dem Grund wird
         * kein Wasser gezeichnet. Das Paket weiß nicht, woher der Grund kommt.
         */
        public int bedTexture;
        /**
         * Seit Glacier Phase 8: eine lange Welle von außen (etwa {@code com.dan.wave}): je Gitter eine Textur R16F/R32F
         * mit dem Wasserstand über dem Ruhespiegel (trocken ≤ −29) und {x0, z0, Zelle, an}. 0 ist das feine Gitter, 1 das
         * grobe, das feine hat Vorrang.
         */
        public int longTexture0, longTexture1;
        /** Seit Glacier Phase 9: Treibeis als Textur (Anteil der bedeckten Fläche 0..1) und {x0, z0, Breite, Tiefe}. */
        public int brashTexture;
        public double[] brashRect = {0, 0, 1, 1};
        public double[] longRect0 = {0, 0, 1, 0}, longRect1 = {0, 0, 1, 0};
        public double[] bedRect = {0, 0, 1};
        /**
         * Seit Rungholt (Phase 2): Höhe des Grunds außerhalb von bedRect (m). NaN: dort kein Wasser (wie bisher); ein
         * Wert: dort ein ebener Grund in dieser Höhe, das Meer geht über den Rand der Höhentextur hinaus weiter.
         */
        public double bedOutside = Double.NaN;
        public double bedScale = 1, bedOffset = 0, bedTurbidity = 0.9;
        public double[] bedColor = {0.20, 0.17, 0.12};
        /**
         * Seit Glacier Phase 13: ein zweiter Spiegel (ein gestauter Seearm über dem Hauptsee). {@code level}: Wasserstand
         * dieses Zeichnens (NaN: der des Ozeans). {@code maskTexture} (R8, 1 = dieses Wasser) mit {x0, z0, Kantenlänge}
         * und {@code maskMode}: 1 nur innen, −1 nur außen, 0 ohne Maske.
         */
        public double level = Double.NaN;
        public int maskTexture, maskMode;
        public double[] maskRect = {0, 0, 1};
        /**
         * Seit Rungholt Phase 6: Strömung als Textur (RG16F oder RG32F, m/s nach Osten und Süden) über {x0, z0, Zelle,
         * an}: die Fläche kräuselt sich darin, Schaum zieht in Streifen mit (ab 0,4 m/s), das Wasser wird trüb
         * (siltColor, siltStrength 0..1). 0 = keine.
         */
        public int flowTexture;
        public double[] flowRect = {0, 0, 1, 0};
        public double[] siltColor = {0.085, 0.075, 0.050};
        public double siltStrength = 0.6;
        /**
         * Seit Rungholt Phase 6: gegen das Kachelmuster der Kaskaden (Rauten im flachen Wasser) schwanken die beiden
         * größten Kaskaden und die Dünung mit flachem Rauschen in der Stärke; 0 = aus (wie bisher), 1 = voll.
         */
        public double tileBreak;
        /**
         * Seit Rungholt Phase 6: das Auge liegt unter dem Spiegel. Dann zeigt die Fläche ihre Unterseite (Snellsches
         * Fenster mit dem Himmel, außerhalb Totalreflexion des Wassers); das trübe Wasser selbst legt
         * {@link #drawUnderwater} über die Szene.
         */
        public boolean under;
        /**
         * Seit Rungholt Phase 6: weicher Rand der langen Welle (0..1): am Rand der nassen Zellen sinkt der Spiegel mit
         * Rauschen unter den Grund, die Wasserlinie läuft weich statt in Treppen des Gitters. 0 = wie bisher.
         */
        public double longSoft;
    }

    /**
     * Seit Rungholt Phase 6: der Blick unter Wasser über die gezeichnete Szene (Tiefe als Kopie): Matrix zurück, Auge,
     * umgekehrte Tiefe, Bildgröße, Tiefentextur; Wasserspiegel über dem Auge (m), Streulicht dicht unter dem Spiegel
     * (Strahldichte, linear), Auslöschung im Blick (1/m, Wattenmeer trüb: rund 1) und Abnahme des Lichts mit der Tiefe (1/m).
     */
    public static final class Under {
        public float[] invViewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
        public int width = 1, height = 1, depthTexture;
        public double surface;
        public double[] scatter = {0.01, 0.012, 0.008};
        public double sigma = 1.0, darkening = 0.35;
    }

    private int progUnder;

    /** Trübes Wasser über die Szene legen (in den gebundenen Bildpuffer, Mischung ONE, SRC_ALPHA). */
    public void drawUnderwater(GL4 gl, Under u) {
        if (progUnder == 0) progUnder = Glsl.program(gl, "Unter Wasser", new String[0], "underwater.vert", "underwater.frag");
        gl.glUseProgram(progUnder);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(progUnder, "uInvVP"), 1, false, u.invViewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(progUnder, "uCam"), (float) u.eye[0], (float) u.eye[1], (float) u.eye[2]);
        gl.glUniform1i(gl.glGetUniformLocation(progUnder, "uReversed"), u.reversedZ ? 1 : 0);
        gl.glUniform2f(gl.glGetUniformLocation(progUnder, "uViewport"), u.width, u.height);
        gl.glUniform1f(gl.glGetUniformLocation(progUnder, "uSurface"), (float) u.surface);
        gl.glUniform3f(gl.glGetUniformLocation(progUnder, "uScatter"), (float) u.scatter[0], (float) u.scatter[1], (float) u.scatter[2]);
        gl.glUniform2f(gl.glGetUniformLocation(progUnder, "uSigma"), (float) u.sigma, (float) u.darkening);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, u.depthTexture);
        gl.glUniform1i(gl.glGetUniformLocation(progUnder, "uDepth"), 0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(0);
    }

    private int progSpectrum, progFft, progAssemble, progSurface;
    /** Die FFT-Programme je Größe N, einmal übersetzt und behalten: Ein Wechsel der Stufe übersetzt sonst jedes Mal neu, auf manchen Treibern 1 bis 3 s. */
    private final java.util.Map<Integer, Integer> fftByN = new java.util.HashMap<>();
    private int texH0, texA, texB, texDisp, texDeriv;
    private int n, cascades, uploadedVersion = -1;
    private int vao, vbo, ibo, indexCount;
    private final RingMesh mesh;
    private double lastTime = Double.NaN;
    private boolean ready;
    private String info = "";

    public OceanGl() { this(new RingMesh(200, 256, 0.12, 60_000)); }

    public OceanGl(RingMesh mesh) { this.mesh = mesh; }

    public boolean ready() { return ready; }
    /** „FFT 4 × 256², Ringgitter 51 456 Ecken“ */
    public String info() { return info; }

    public void init(GL4 gl) {
        progSpectrum = Glsl.program(gl, "Spektrum", new String[0], "spectrum.comp");
        progAssemble = Glsl.program(gl, "Zusammensetzen", new String[0], "assemble.comp");
        progSurface = Glsl.program(gl, "Oberfläche", new String[0], "surface.vert", "surface.frag");
        int[] v = new int[2];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenBuffers(2, v, 0);
        vbo = v[0];
        ibo = v[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) mesh.verts.length * 4, Buffers.newDirectFloatBuffer(mesh.verts), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) mesh.idx.length * 4, Buffers.newDirectIntBuffer(mesh.idx), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 12, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 1, GL.GL_FLOAT, false, 12, 8);
        gl.glBindVertexArray(0);
        indexCount = mesh.idx.length;
        ready = true;
    }

    /** Legt die Texturen für n² × Kaskaden an (beim ersten Mal und wenn sich die Qualität ändert). */
    private void allocate(GL4 gl, int newN, int newCascades) {
        deleteTextures(gl);
        n = newN;
        cascades = newCascades;
        progFft = fftByN.computeIfAbsent(n, k -> Glsl.program(gl, "FFT", new String[]{"N " + k, "HALF " + (k / 2)}, "fft.comp"));
        texH0 = array(gl, GL.GL_RGBA32F, 1, GL.GL_NEAREST);
        texA = array(gl, GL.GL_RGBA32F, 1, GL.GL_NEAREST);
        texB = array(gl, GL.GL_RGBA32F, 1, GL.GL_NEAREST);
        int levels = 1 + Integer.numberOfTrailingZeros(n);
        texDisp = array(gl, GL.GL_RGBA16F, levels, GL.GL_LINEAR_MIPMAP_LINEAR);
        texDeriv = array(gl, GL.GL_RGBA16F, levels, GL.GL_LINEAR_MIPMAP_LINEAR);
        // Schaum beginnt bei null
        FloatBuffer zero = Buffers.newDirectFloatBuffer(n * n * 4 * cascades);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDisp);
        gl.glTexSubImage3D(GL4.GL_TEXTURE_2D_ARRAY, 0, 0, 0, 0, n, n, cascades, GL.GL_RGBA, GL.GL_FLOAT, zero);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
        uploadedVersion = -1;
        info = String.format(Locale.GERMAN, "FFT %d × %d², Ringgitter %,d Ecken", cascades, n, mesh.verts.length / 3);
    }

    private int array(GL4 gl, int format, int levels, int minFilter) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, t[0]);
        gl.glTexStorage3D(GL4.GL_TEXTURE_2D_ARRAY, levels, format, n, n, cascades);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MIN_FILTER, minFilter);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAG_FILTER, minFilter == GL.GL_NEAREST ? GL.GL_NEAREST : GL.GL_LINEAR);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        if (levels > 1 && gl.isExtensionAvailable("GL_EXT_texture_filter_anisotropic"))
            gl.glTexParameterf(GL4.GL_TEXTURE_2D_ARRAY, GL.GL_TEXTURE_MAX_ANISOTROPY_EXT, 8f);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
        return t[0];
    }

    /** Rechnet die See für die aktuelle Zeit des Kerns auf der Grafikkarte. */
    public void update(GL4 gl, Ocean ocean) {
        Ocean.State s = ocean.state();
        int cn = s.cascades.get(0).n, cc = s.cascades.size();
        if (cn != n || cc != cascades) allocate(gl, cn, cc);
        if (s.version != uploadedVersion) {
            gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texH0);
            for (Spectrum.Cascade c : s.cascades)
                gl.glTexSubImage3D(GL4.GL_TEXTURE_2D_ARRAY, 0, 0, 0, c.index, n, n, 1, GL.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(c.h0));
            gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
            uploadedVersion = s.version;
        }
        double t = ocean.time();
        double dt = Double.isNaN(lastTime) ? 0 : Math.max(0, Math.min(0.5, t - lastTime));
        lastTime = t;
        float[] lengths = lengths(s);

        // 1 Spektrum
        gl.glUseProgram(progSpectrum);
        gl.glBindImageTexture(0, texH0, 0, true, 0, GL4.GL_READ_ONLY, GL.GL_RGBA32F);
        gl.glBindImageTexture(1, texA, 0, true, 0, GL4.GL_WRITE_ONLY, GL.GL_RGBA32F);
        gl.glBindImageTexture(2, texB, 0, true, 0, GL4.GL_WRITE_ONLY, GL.GL_RGBA32F);
        gl.glUniform1d(gl.glGetUniformLocation(progSpectrum, "uCycles"), t / Spectrum.REPEAT);
        gl.glUniform1fv(gl.glGetUniformLocation(progSpectrum, "uLength[0]"), 4, lengths, 0);
        gl.glUniform1f(gl.glGetUniformLocation(progSpectrum, "uChop"), (float) s.choppiness);
        gl.glUniform1f(gl.glGetUniformLocation(progSpectrum, "uDepth"), (float) s.depth);
        gl.glUniform1i(gl.glGetUniformLocation(progSpectrum, "uN"), n);
        gl.glDispatchCompute(n / 16, n / 16, cascades);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        // 2 FFT: Zeilen, dann Spalten, je für A und B
        gl.glUseProgram(progFft);
        gl.glBindImageTexture(0, texA, 0, true, 0, GL4.GL_READ_WRITE, GL.GL_RGBA32F);
        gl.glBindImageTexture(1, texB, 0, true, 0, GL4.GL_READ_WRITE, GL.GL_RGBA32F);
        int vert = gl.glGetUniformLocation(progFft, "uVertical");
        gl.glUniform1i(vert, 0);
        gl.glDispatchCompute(n, cascades, 2);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glUniform1i(vert, 1);
        gl.glDispatchCompute(n, cascades, 2);
        gl.glMemoryBarrier(GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

        // 3 Zusammensetzen mit Schaum
        gl.glUseProgram(progAssemble);
        gl.glBindImageTexture(0, texA, 0, true, 0, GL4.GL_READ_ONLY, GL.GL_RGBA32F);
        gl.glBindImageTexture(1, texB, 0, true, 0, GL4.GL_READ_ONLY, GL.GL_RGBA32F);
        gl.glBindImageTexture(2, texDisp, 0, true, 0, GL4.GL_READ_WRITE, GL.GL_RGBA16F);
        gl.glBindImageTexture(3, texDeriv, 0, true, 0, GL4.GL_WRITE_ONLY, GL.GL_RGBA16F);
        gl.glUniform1f(gl.glGetUniformLocation(progAssemble, "uDt"), (float) dt);
        gl.glUniform1f(gl.glGetUniformLocation(progAssemble, "uFoamDecay"), (float) ocean.foamDecay);
        float[] thr = new float[4];
        java.util.Arrays.fill(thr, -10);
        for (Spectrum.Cascade c : s.cascades) thr[c.index] = (float) c.foamThreshold;
        gl.glUniform1fv(gl.glGetUniformLocation(progAssemble, "uFoamThr[0]"), 4, thr, 0);
        gl.glDispatchCompute(n / 16, n / 16, cascades);
        gl.glMemoryBarrier(GL4.GL_TEXTURE_FETCH_BARRIER_BIT | GL4.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDisp);
        gl.glGenerateMipmap(GL4.GL_TEXTURE_2D_ARRAY);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDeriv);
        gl.glGenerateMipmap(GL4.GL_TEXTURE_2D_ARRAY);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
        for (int i = 0; i < 4; i++) gl.glBindImageTexture(i, 0, 0, false, 0, GL4.GL_READ_ONLY, GL.GL_RGBA32F);
        gl.glUseProgram(0);
    }

    private float[] lengths(Ocean.State s) {
        float[] l = new float[4];
        for (Spectrum.Cascade c : s.cascades) l[c.index] = (float) c.length;
        for (int i = s.cascades.size(); i < 4; i++) l[i] = 1;
        return l;
    }

    /** Zeichnet die Oberfläche in den gebundenen Bildspeicher (Tiefentest an, Rückseiten werden mitgezeichnet). */
    public void draw(GL4 gl, Ocean ocean, View view, Light light) {
        Ocean.State s = ocean.state();
        int p = progSurface;
        gl.glUseProgram(p);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        float cx = (float) (Math.floor(view.eye[0] / 0.25) * 0.25), cz = (float) (Math.floor(view.eye[2] / 0.25) * 0.25);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uCenter"), cx, cz);
        double t = ocean.time();
        gl.glUniform1f(gl.glGetUniformLocation(p, "uTime"), (float) (t % Spectrum.REPEAT));
        gl.glUniform1f(gl.glGetUniformLocation(p, "uLevel"), (float) (Double.isNaN(light.level) ? ocean.waterLevel : light.level));
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCascades"), cascades);
        gl.glUniform1fv(gl.glGetUniformLocation(p, "uLength[0]"), 4, lengths(s), 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uReversed"), view.reversedZ ? 1 : 0);
        float[] sa = new float[16], sb = new float[16];
        for (int i = 0; i < Swell.COUNT; i++) {
            int o = i * Swell.STRIDE;
            sa[i * 4] = (float) s.swell[o]; sa[i * 4 + 1] = (float) s.swell[o + 1]; sa[i * 4 + 2] = (float) s.swell[o + 2]; sa[i * 4 + 3] = (float) s.swell[o + 3];
            sb[i * 4] = (float) s.swell[o + 4]; sb[i * 4 + 1] = (float) s.swell[o + 5]; sb[i * 4 + 2] = (float) s.swell[o + 6];
        }
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uSwellA[0]"), 4, sa, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uSwellB[0]"), 4, sb, 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunDir"), (float) light.sunDir[0], (float) light.sunDir[1], (float) light.sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSunColor"), (float) light.sunColor[0], (float) light.sunColor[1], (float) light.sunColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uMoonDir"), (float) light.moonDir[0], (float) light.moonDir[1], (float) light.moonDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uMoonColor"), (float) light.moonColor[0], (float) light.moonColor[1], (float) light.moonColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uWaterColor"), (float) light.waterColor[0], (float) light.waterColor[1], (float) light.waterColor[2]);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uSssColor"), (float) light.sssColor[0], (float) light.sssColor[1], (float) light.sssColor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uSkyGain"), light.skyGain);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uHs"), (float) s.totalHs());
        gl.glUniform1f(gl.glGetUniformLocation(p, "uWindTo"), (float) s.windToRad);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uWindSpeed"), (float) s.windSpeed);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFoamGain"), 1f);
        List<Obstacle> obs = ocean.obstacles();
        float[] ob = new float[32];
        int oc = Math.min(8, obs.size());
        for (int i = 0; i < oc; i++) { ob[i * 4] = (float) obs.get(i).x(); ob[i * 4 + 1] = (float) obs.get(i).z(); ob[i * 4 + 2] = (float) obs.get(i).radius(); }
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uObst[0]"), 8, ob, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uObstCount"), oc);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uRingHs"), (float) ocean.ringHsLow, (float) ocean.ringHsHigh);
        java.util.List<com.dan.ocean.HullMask> hm = ocean.hulls;
        float[] ha = new float[32], hb = new float[32];
        int hc = Math.min(8, hm.size());
        for (int i = 0; i < hc; i++) {
            com.dan.ocean.HullMask m = hm.get(i);
            double hr = Math.toRadians(m.headingDeg());
            ha[i * 4] = (float) m.x(); ha[i * 4 + 1] = (float) m.z(); ha[i * 4 + 2] = (float) Math.sin(hr); ha[i * 4 + 3] = (float) Math.cos(hr);
            hb[i * 4] = (float) m.halfLength(); hb[i * 4 + 1] = (float) m.halfBeam(); hb[i * 4 + 2] = (float) m.fullness();
        }
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uHullA[0]"), 8, ha, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uHullB[0]"), 8, hb, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uHullCount"), hc);
        com.dan.ocean.ObstacleField of = ocean.obstacleField;
        if (of != fieldUp) {
            if (fieldTex != 0) { gl.glDeleteTextures(1, new int[]{fieldTex}, 0); fieldTex = 0; }
            if (of != null) {
                int[] tx = new int[1];
                gl.glGenTextures(1, tx, 0);
                fieldTex = tx[0];
                gl.glBindTexture(GL.GL_TEXTURE_2D, fieldTex);
                gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
                gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, of.w(), of.h(), 0, GL4.GL_RED, GL.GL_FLOAT, java.nio.FloatBuffer.wrap(of.dist()));
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
                gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            }
            fieldUp = of;
        }
        gl.glActiveTexture(GL.GL_TEXTURE0 + 8);
        gl.glBindTexture(GL.GL_TEXTURE_2D, fieldTex);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uObstField"), 8);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uObstFieldOn"), of != null && fieldTex != 0 ? 1 : 0);
        if (of != null) gl.glUniform4f(gl.glGetUniformLocation(p, "uObstFieldRect"), (float) of.x0(), (float) of.z0(), (float) (of.w() * of.cell()), (float) (of.h() * of.cell()));
        List<Ripples.Impulse> rp = ocean.impulses();
        float[] rr = new float[128];
        int rc = Math.min(32, rp.size());
        for (int i = 0; i < rc; i++) {
            Ripples.Impulse im = rp.get(rp.size() - rc + i);
            rr[i * 4] = (float) im.x(); rr[i * 4 + 1] = (float) im.z(); rr[i * 4 + 2] = (float) (t - im.t0()); rr[i * 4 + 3] = (float) im.strength();
        }
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uRipple[0]"), 32, rr, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uRippleCount"), rc);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDisp);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uDisp"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDeriv);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uDeriv"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.skyTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uSky"), 2);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.shadowTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uCloudShadow"), 3);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uShadowRect"), (float) light.shadowRect[0], (float) light.shadowRect[1],
                (float) Math.max(1, light.shadowRect[2]), light.shadowTexture != 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.bodyShadowTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBodyShadow"), 4);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uBodyRect"), (float) light.bodyShadowRect[0], (float) light.bodyShadowRect[1],
                (float) Math.max(1, light.bodyShadowRect[2]), light.bodyShadowTexture != 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.detailTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uDetail"), 5);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uDetailP"), (float) Math.max(0.01, light.detailTile), (float) light.detailStrength,
                (float) Math.max(1, light.detailRange), light.detailTexture != 0 && light.detailStrength > 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.pointTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uPointTex"), 6);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uPointPos"), (float) light.pointPos[0], (float) light.pointPos[1], (float) light.pointPos[2],
                light.pointTexture != 0 && light.pointGain > 0 ? (float) light.pointGain : 0f);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uPointSigma"), (float) light.pointSigma);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFlashN"), Math.min(8, light.flashCount));
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uFlashPos"), 8, light.flashPos, 0);
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uFlashRgb"), 8, light.flashRgb, 0);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.bedTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBed"), 7);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uBedRect"), (float) light.bedRect[0], (float) light.bedRect[1],
                (float) Math.max(1, light.bedRect[2]), light.bedTexture != 0 ? 1f : 0f);
        gl.glUniform2f(gl.glGetUniformLocation(p, "uBedOutside"), Double.isNaN(light.bedOutside) ? 0f : (float) light.bedOutside, Double.isNaN(light.bedOutside) ? 0f : 1f);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uBedMap"), (float) light.bedScale, (float) light.bedOffset, (float) light.bedTurbidity);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uBedColor"), (float) light.bedColor[0], (float) light.bedColor[1], (float) light.bedColor[2]);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 9);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.longTexture0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uLongH0"), 9);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 10);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.longTexture1);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uLongH1"), 10);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uLongR0"), (float) light.longRect0[0], (float) light.longRect0[1],
                (float) Math.max(0.01, light.longRect0[2]), light.longTexture0 != 0 && light.longRect0[3] > 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 11);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.brashTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBrash"), 11);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uBrashOn"), light.brashTexture != 0 ? 1 : 0);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uBrashRect"), (float) light.brashRect[0], (float) light.brashRect[1],
                (float) Math.max(1, light.brashRect[2]), (float) Math.max(1, light.brashRect[3]));
        gl.glActiveTexture(GL.GL_TEXTURE0 + 12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.maskTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uMask"), 12);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uMaskMode"), light.maskTexture != 0 ? light.maskMode : 0);
        gl.glUniform3f(gl.glGetUniformLocation(p, "uMaskRect"), (float) light.maskRect[0], (float) light.maskRect[1], (float) Math.max(1, light.maskRect[2]));
        gl.glUniform4f(gl.glGetUniformLocation(p, "uLongR1"), (float) light.longRect1[0], (float) light.longRect1[1],
                (float) Math.max(0.01, light.longRect1[2]), light.longTexture1 != 0 && light.longRect1[3] > 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE0 + 13);
        gl.glBindTexture(GL.GL_TEXTURE_2D, light.flowTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFlow"), 13);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFlowRect"), (float) light.flowRect[0], (float) light.flowRect[1],
                (float) Math.max(0.01, light.flowRect[2]), light.flowTexture != 0 && light.flowRect[3] > 0 ? 1f : 0f);
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFlowSilt"), (float) light.siltColor[0], (float) light.siltColor[1], (float) light.siltColor[2], (float) light.siltStrength);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uTileBreak"), (float) light.tileBreak);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uUnder"), light.under ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uLongSoft"), (float) light.longSoft);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, indexCount, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /**
     * Selbsttest: liest von jeder Kaskade einige Texel der Auslenkung zurück und vergleicht sie mit der genauen
     * Rechnung des Kerns (alle Moden, doppelte Genauigkeit) zur selben Zeit. Nach {@link #update} aufrufen.
     * Ergebnis: größte Abweichung in m und als Text.
     */
    public String selfCheck(GL4 gl, Ocean ocean) {
        Ocean.State s = ocean.state();
        gl.glFinish();
        FloatBuffer fb = Buffers.newDirectFloatBuffer(n * n * 4 * cascades);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, texDisp);
        gl.glGetTexImage(GL4.GL_TEXTURE_2D_ARRAY, 0, GL.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindTexture(GL4.GL_TEXTURE_2D_ARRAY, 0);
        double worst = 0, scale = 0;
        int[][] probe = {{0, 0}, {n / 3, n / 5}, {n / 2, n - 1}, {n - 7, n / 4}, {11, n / 2 + 3}};
        for (Spectrum.Cascade c : s.cascades) {
            for (int[] pr : probe) {
                double x0 = pr[0] * c.length / n, z0 = pr[1] * c.length / n;
                double[] e = ocean.exactCascade(c.index, x0, z0, ocean.time());
                int i = ((c.index * n + pr[1]) * n + pr[0]) * 4;
                double d = Math.max(Math.abs(fb.get(i) - e[0]), Math.max(Math.abs(fb.get(i + 1) - e[1]), Math.abs(fb.get(i + 2) - e[2])));
                worst = Math.max(worst, d);
                scale = Math.max(scale, Math.abs(e[1]));
            }
        }
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: größte Abweichung %.1f mm (Höhen bis %.2f m)", worst * 1000, scale);
    }

    private void deleteTextures(GL4 gl) {
        for (int t : new int[]{texH0, texA, texB, texDisp, texDeriv}) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        texH0 = texA = texB = texDisp = texDeriv = 0;
    }

    private int fieldTex;
    private com.dan.ocean.ObstacleField fieldUp;

    public void dispose(GL4 gl) {
        if (fieldTex != 0) { gl.glDeleteTextures(1, new int[]{fieldTex}, 0); fieldTex = 0; fieldUp = null; }
        deleteTextures(gl);
        for (int p : new int[]{progSpectrum, progAssemble, progSurface, progUnder}) if (p != 0) gl.glDeleteProgram(p);
        progUnder = 0;
        for (int p : fftByN.values()) gl.glDeleteProgram(p);
        fftByN.clear();
        progSpectrum = progFft = progAssemble = progSurface = 0;
        if (vao != 0) {
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
            gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        }
        vao = vbo = ibo = 0;
        ready = false;
    }
}
