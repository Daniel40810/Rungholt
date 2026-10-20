package com.dan.ice.gl;

import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.IceNoise;
import com.dan.ice.IceOptics;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet Eiskörper mit dem Material aus {@code ice.frag} (Werkbuch Glacier, F10 A). Je Bild zwei Durchgänge:
 * <ol>
 * <li>Rückseiten aller Körper in eine eigene Gleitkommatextur (Abstand vom Auge, m) – daraus die Dicke je
 * Bildpunkt;</li>
 * <li>Vorderseiten in das Ziel des Aufrufers (sein Bildpuffer, seine Tiefe) mit Licht im Eis nach Kubelka-Munk,
 * Rissen aus der Zellkachel ({@link IceNoise}), Kruste, Moräne, Schnee, Film und Glitzer.</li>
 * </ol>
 * Das Licht kommt wie bei den anderen Packages von außen ({@link Light}: Sonne, Mond, Himmel, auf Wunsch ein
 * Rundumbild des Himmels, Dunst), vorbelichtet. Umgekehrte Tiefe (0..1) wird unterstützt. Das Package kennt weder die
 * App noch ein anderes Package.
 */
public final class IceGl {
    /** Ein hochgeladener Körper. */
    public static final class Body {
        int vao, vbo, ibo, count;
        /** Das Netz (für Kollision und Volumen; wer es nicht braucht, setzt es auf null und spart Speicher). */
        public IceMesh mesh;
        Body(IceMesh m) { mesh = m; }
        public int triangles() { return count / 3; }
    }

    /** Ein Körper an seinem Ort mit seinem Eis. {@code model}: Spaltenmatrix 4 × 4 (Drehung und Verschiebung). */
    public static final class Instance {
        public final Body body;
        public Ice ice;
        public float[] model = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        /**
         * Ob der Körper geschlossen ist und seine Rückseiten die Dicke geben (Eisberg, Block); offene Flächen wie die
         * Oberfläche eines Gletschers nehmen {@link #thickness} (m; 0 = {@link Light#defaultThickness}).
         */
        public boolean closed = true;
        public double thickness;
        /** Ob die Ausschnitte ({@link #setCuts}) für diesen Körper gelten (Phase 7: die Wand, wo ein Abschnitt bricht). */
        public boolean cut;
        public Instance(Body body, Ice ice) { this.body = body; this.ice = ice; }

        /** Eine offene Fläche (keine Rückseiten) mit der Grunddicke d (m). */
        public Instance open(double d) { closed = false; thickness = d; return this; }

        /** Ort und Drehung um die Hochachse (Grad). */
        public Instance place(double x, double y, double z, double yawDeg) {
            double a = Math.toRadians(yawDeg), c = Math.cos(a), s = Math.sin(a);
            model = new float[]{(float) c, 0, (float) -s, 0, 0, 1, 0, 0, (float) s, 0, (float) c, 0, (float) x, (float) y, (float) z, 1};
            return this;
        }
    }

    public static final class View {
        public float[] viewProj;
        public double[] eye = {0, 0, 0};
        public boolean reversedZ;
        public int width = 1, height = 1;
    }

    public static final class Light {
        public double[] sunDir = {0.4, 0.6, -0.5}, sunColor = {2.6, 2.45, 2.2};
        public double[] moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        /** Himmel oben und am Horizont (Leuchtdichte wie bei den anderen Packages), Dunst. */
        public double[] skyTop = {0.22, 0.36, 0.62}, skyHorizon = {0.62, 0.72, 0.82}, fogColor = {0.62, 0.72, 0.82};
        public double fogDistance = 30_000;
        /** Rundumbild des Himmels (Azimut von Nord, Höhe; 0 = Verlauf aus skyTop und skyHorizon). */
        public int skyTexture;
        /** Wasserspiegel (y); darunter färbt sich das Eis mit der Auslöschung des Wassers. Sehr tief = kein Wasser. */
        public double waterLevel = -1e9;
        public double[] waterAbsorption = {0.45, 0.10, 0.08};
        /** Farbe, in der das Wasser selbst im Licht leuchtet (Streualbedo; trübes Gletscherwasser türkis). */
        public double[] waterColor = {0.04, 0.16, 0.17};
        /** Dicke, wenn keine Rückseite zu sehen ist (m), und ein Faktor auf alle Dicken (Demo-Regler). */
        public double defaultThickness = 2, thicknessScale = 1;
    }

    private int progBack, progFront, crackTex, backFbo, backTex, backDepth, backW, backH;
    private long crackSeed = Long.MIN_VALUE;
    private final List<Body> bodies = new ArrayList<>();
    private String info = "Eis: nicht gestartet";
    private int lastDrawn, lastTris;

    public void init(GL4 gl) {
        progBack = program(gl, "Eis Rückseiten", "ice.vert", "ice_back.frag");
        progFront = program(gl, "Eis", "ice.vert", "ice.frag");
        info = "Eis: bereit";
    }

    /** Lädt einen Körper hoch (einmal je Form). */
    public Body upload(GL4 gl, IceMesh m) {
        Body b = new Body(m);
        int n = m.pos.length / 3, stride = m.ao != null ? 7 : 6;
        FloatBuffer fb = Buffers.newDirectFloatBuffer(n * stride);
        for (int i = 0; i < n; i++) {
            fb.put(m.pos, i * 3, 3).put(m.nrm, i * 3, 3);
            if (m.ao != null) fb.put(m.ao[i]);
        }
        fb.flip();
        IntBuffer ib = Buffers.newDirectIntBuffer(m.idx);
        int[] t = new int[3];
        gl.glGenVertexArrays(1, t, 0);
        gl.glGenBuffers(2, t, 1);
        b.vao = t[0]; b.vbo = t[1]; b.ibo = t[2];
        gl.glBindVertexArray(b.vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b.vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride * 4, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride * 4, 12);
        if (m.ao != null) {
            gl.glEnableVertexAttribArray(2);
            gl.glVertexAttribPointer(2, 1, GL.GL_FLOAT, false, stride * 4, 24);
        }
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b.ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) ib.limit() * 4, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        b.count = m.idx.length;
        bodies.add(b);
        return b;
    }

    /** Höchstzahl der Ausschnitte. */
    public static final int MAX_CUTS = 8;
    private float[] cuts = new float[0];

    /**
     * Ausschnitte (Phase 7): je 8 Zahlen {Mitte x, Mitte z, Tangente x, Tangente z, halbe Breite, halbe Tiefe, y unten,
     * y oben}. Körper mit {@link Instance#cut} werden darin nicht gezeichnet; dort stehen dann andere Körper (die Zellen
     * eines Abschnitts der Front, der bricht). Höchstens {@link #MAX_CUTS}.
     */
    public void setCuts(float[] c) { cuts = c == null ? new float[0] : java.util.Arrays.copyOf(c, Math.min(c.length, MAX_CUTS * 8)); }

    /** Zeichnet die Körper in das gerade gebundene Ziel (dessen Tiefe gilt). */
    public void draw(GL4 gl, List<Instance> list, View v, Light l) {
        locGl = gl;
        if (list.isEmpty() || progFront == 0) { lastDrawn = 0; return; }
        long seed = list.get(0).ice.seed;
        if (crackTex == 0 || seed != crackSeed) uploadCracks(gl, seed);
        int[] prev = new int[1], vpt = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vpt, 0);
        ensureBack(gl, v.width, v.height);
        float[] eye = {(float) v.eye[0], (float) v.eye[1], (float) v.eye[2]};

        // 1 Rückseiten: Abstand vom Auge
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, backFbo);
        gl.glViewport(0, 0, backW, backH);
        gl.glClearColor(0, 0, 0, 0);
        gl.glClearDepth(v.reversedZ ? 0 : 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glFrontFace(GL.GL_CCW);
        gl.glCullFace(GL.GL_FRONT);
        gl.glUseProgram(progBack);
        uMat4(gl, progBack, "uVP", v.viewProj);
        u3(gl, progBack, "uEye", eye[0], eye[1], eye[2]);
        int tris = 0;
        for (Instance in : list) {
            tris += in.body.count / 3;
            if (!in.closed) continue;
            uMat4(gl, progBack, "uModel", in.model);
            gl.glBindVertexArray(in.body.vao);
            gl.glDrawElements(GL.GL_TRIANGLES, in.body.count, GL.GL_UNSIGNED_INT, 0);
        }

        // 2 Vorderseiten in das Ziel des Aufrufers
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vpt[0], vpt[1], vpt[2], vpt[3]);
        gl.glCullFace(GL.GL_BACK);
        gl.glUseProgram(progFront);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, backTex);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, crackTex);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        int p = progFront;
        u1i(gl, p, "uBack", 0);
        u1i(gl, p, "uCrack", 1);
        u1i(gl, p, "uPano", 2);
        u1i(gl, p, "uSkyTex", l.skyTexture != 0 ? 1 : 0);
        uMat4(gl, p, "uVP", v.viewProj);
        u3(gl, p, "uEye", eye[0], eye[1], eye[2]);
        u3d(gl, p, "uSun", norm(l.sunDir));
        u3d(gl, p, "uSunColor", l.sunColor);
        u3d(gl, p, "uMoonDir", norm(l.moonDir));
        u3d(gl, p, "uMoonColor", l.moonColor);
        u3d(gl, p, "uSkyTop", l.skyTop);
        u3d(gl, p, "uSkyHor", l.skyHorizon);
        u3d(gl, p, "uFogColor", l.fogColor);
        u1(gl, p, "uFogDist", l.fogDistance);
        u2(gl, p, "uTarget", backW, backH);
        gl.glVertexAttrib1f(2, 1f);   // Verdeckung für Körper ohne eigenes Feld
        u1(gl, p, "uThickScale", l.thicknessScale);
        u1(gl, p, "uWater", l.waterLevel);
        u3d(gl, p, "uWaterAbs", l.waterAbsorption);
        u3d(gl, p, "uWaterColor", l.waterColor);
        u3d(gl, p, "uAlpha", IceOptics.ABSORPTION);
        u1(gl, p, "uG", IceOptics.G);
        Ice lastIce = null;
        float[] lastModel = null;
        double lastThick = -1;
        int nCut = cuts.length / 8;
        if (nCut > 0) {
            float[] a = new float[nCut * 4], b = new float[nCut * 4];
            for (int k = 0; k < nCut; k++) {
                System.arraycopy(cuts, k * 8, a, k * 4, 4);
                System.arraycopy(cuts, k * 8 + 4, b, k * 4, 4);
            }
            gl.glUniform4fv(loc(p, "uCutA"), nCut, a, 0);
            gl.glUniform4fv(loc(p, "uCutB"), nCut, b, 0);
        }
        u1i(gl, p, "uCutN", nCut);
        int lastCut = -1;
        for (Instance in : list) {
            int useCut = in.cut && nCut > 0 ? 1 : 0;
            if (useCut != lastCut) { u1i(gl, p, "uUseCut", useCut); lastCut = useCut; }
            Ice ice = in.ice;
            float[] m = in.model;
            if (m != lastModel) {
                uMat4(gl, p, "uModel", m);
                gl.glUniformMatrix3fv(loc(p, "uRot"), 1, false, new float[]{m[0], m[1], m[2], m[4], m[5], m[6], m[8], m[9], m[10]}, 0);
                lastModel = m;
            }
            double th = in.thickness > 0 ? in.thickness : l.defaultThickness;
            if (th != lastThick) { u1(gl, p, "uDefThick", th); lastThick = th; }
            if (ice != lastIce) {
            lastIce = ice;
            u1(gl, p, "uSigma", IceOptics.scattering(ice.bubbles));
            gl.glUniform4f(loc(p, "uMat"), (float) ice.cracks, (float) ice.moraine, (float) ice.melt, (float) ice.snow);
            u1(gl, p, "uCrust", IceOptics.crustDepth(ice.weathering));
            u1(gl, p, "uGlitter", ice.glitter);
            u1(gl, p, "uCrackCell", ice.crackCell);
            u1(gl, p, "uBandSpacing", ice.bandSpacing);
            u1i(gl, p, "uSteps", steps(ice.quality));
            u3d(gl, p, "uBandDir", ice.bandDir);
            long h = ice.seed * 0x9E3779B97F4A7C15L;
            u3(gl, p, "uSeedOff", (float) ((h >>> 40) % 997), (float) ((h >>> 20) % 991), (float) (h % 983));
            }
            gl.glBindVertexArray(in.body.vao);
            gl.glDrawElements(GL.GL_TRIANGLES, in.body.count, GL.GL_UNSIGNED_INT, 0);
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUseProgram(0);
        lastDrawn = list.size();
        lastTris = tris;
        info = String.format(Locale.GERMAN, "Eis: %d Körper, %,d Dreiecke, Dicke aus Rückseiten %d×%d, Risszellen %d³", lastDrawn, lastTris, backW, backH, IceNoise.N);
    }

    /** Gibt einen Körper frei (etwa eine Kachel, die eine andere Stufe bekommt). */
    public void release(GL4 gl, Body b) {
        if (b == null || b.vao == 0) return;
        gl.glDeleteVertexArrays(1, new int[]{b.vao}, 0);
        gl.glDeleteBuffers(2, new int[]{b.vbo, b.ibo}, 0);
        b.vao = 0;
        bodies.remove(b);
    }

    /** Proben längs des Strahls durch die Risse je Stufe: LOW 2, MEDIUM 4, HIGH 6, ULTRA 8. */
    public static int steps(Ice.Quality q) { return 2 + 2 * q.ordinal(); }

    /** „Eis: 2 Körper, 1 324 Dreiecke, …“ */
    public String info() { return info; }

    // ------------------------------------------------------------------ Hilfen

    private void ensureBack(GL4 gl, int w, int h) {
        w = Math.max(1, w); h = Math.max(1, h);
        if (backFbo != 0 && w == backW && h == backH) return;
        if (backFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{backFbo}, 0);
            gl.glDeleteTextures(2, new int[]{backTex, backDepth}, 0);
        }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        backTex = t[0];
        backDepth = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, backTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, w, h, 0, GL4.GL_RED, GL.GL_FLOAT, null);
        param2d(gl, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, backDepth);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, w, h, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        param2d(gl, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        backFbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, backFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, backTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, backDepth, 0);
        int st = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        if (st != GL.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("ice: Puffer der Rückseiten unvollständig (0x" + Integer.toHexString(st) + ")");
        backW = w;
        backH = h;
    }

    private static void param2d(GL4 gl, int filter) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, filter);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    private void uploadCracks(GL4 gl, long seed) {
        byte[] c = IceNoise.cracks(seed);
        if (crackTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            crackTex = t[0];
        }
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, crackTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_RGBA8, IceNoise.N, IceNoise.N, IceNoise.N, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, ByteBuffer.wrap(c));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
        crackSeed = seed;
    }

    public void dispose(GL4 gl) {
        for (Body b : bodies) {
            gl.glDeleteVertexArrays(1, new int[]{b.vao}, 0);
            gl.glDeleteBuffers(2, new int[]{b.vbo, b.ibo}, 0);
        }
        bodies.clear();
        if (backFbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{backFbo}, 0);
            gl.glDeleteTextures(2, new int[]{backTex, backDepth}, 0);
            backFbo = 0;
        }
        if (crackTex != 0) gl.glDeleteTextures(1, new int[]{crackTex}, 0);
        crackTex = 0;
        locs.clear();
        if (progBack != 0) gl.glDeleteProgram(progBack);
        if (progFront != 0) gl.glDeleteProgram(progFront);
        progBack = progFront = 0;
    }

    /** Quelltext eines Shaders aus den Ressourcen des Packages, mit eingesetzten {@code #include} (für die Prüfung). */
    public static String source(String file) {
        StringBuilder sb = new StringBuilder("#version 430 core\n");
        include(sb, file, 0);
        return sb.toString();
    }

    private static void include(StringBuilder sb, String file, int depth) {
        if (depth > 4) throw new IllegalStateException("ice: #include zu tief: " + file);
        for (String line : read(file).split("\n", -1)) {
            String t = line.trim();
            if (t.startsWith("#include")) {
                int a = t.indexOf('"'), b = t.lastIndexOf('"');
                include(sb, t.substring(a + 1, b), depth + 1);
            } else sb.append(line).append('\n');
        }
    }

    private static String read(String file) {
        try (InputStream in = IceGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("ice: Shader " + file + " fehlt");
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            in.transferTo(o);
            return o.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("ice: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int program(GL4 gl, String name, String vert, String frag) {
        int vs = stage(gl, GL2ES2.GL_VERTEX_SHADER, source(vert), vert), fs = stage(gl, GL2ES2.GL_FRAGMENT_SHADER, source(frag), frag);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, log.length, null, 0, log, 0);
            throw new IllegalStateException("ice: " + name + " lässt sich nicht binden:\n" + new String(log, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] log = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
            throw new IllegalStateException("ice: " + name + " übersetzt nicht:\n" + new String(log, StandardCharsets.UTF_8).trim());
        }
        return s;
    }

    private static double[] norm(double[] v) {
        double l = Math.max(1e-12, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]));
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** Orte der Uniforms je Programm, einmal erfragt (der Treiber fragt sonst je Körper und Bild). */
    private final java.util.Map<Integer, java.util.Map<String, Integer>> locs = new java.util.HashMap<>();
    private GL4 locGl;

    private int loc(int p, String n) {
        return locs.computeIfAbsent(p, k -> new java.util.HashMap<>()).computeIfAbsent(n, k -> locGl.glGetUniformLocation(p, k));
    }

    private void uMat4(GL4 gl, int p, String n, float[] m) { gl.glUniformMatrix4fv(loc(p, n), 1, false, m, 0); }
    private void u1(GL4 gl, int p, String n, double v) { gl.glUniform1f(loc(p, n), (float) v); }
    private void u1i(GL4 gl, int p, String n, int v) { gl.glUniform1i(loc(p, n), v); }
    private void u2(GL4 gl, int p, String n, double a, double b) { gl.glUniform2f(loc(p, n), (float) a, (float) b); }
    private void u3(GL4 gl, int p, String n, float a, float b, float c) { gl.glUniform3f(loc(p, n), a, b, c); }
    private void u3d(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(loc(p, n), (float) v[0], (float) v[1], (float) v[2]); }
}
