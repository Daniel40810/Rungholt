package com.dan.cloud.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.Noise;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Wolken auf der Grafikkarte (OpenGL 4.3, ohne Compute-Shader). Je Bild:
 * <ol>
 * <li>{@link #update}: Schattenkarte von oben (256², 24 km um das Auge) für Meer und Szene.</li>
 * <li>{@link #draw}: Raymarching in halber oder viertel Auflösung mit zitternden Startpunkten, Wiederverwendung des
 * letzten Bilds (an die neue Blickrichtung geholt, auf die Nachbarn begrenzt), dann über das gebundene Ziel gelegt:
 * Ziel · Durchlässigkeit + Wolkenlicht.</li>
 * <li>{@link #drawPanorama}: dieselben Wolken grob ins Rundumbild für Spiegelungen.</li>
 * </ol>
 * Alle Lichtwerte kommen vorbelichtet herein (wie bei {@code com.dan.sky}). Die Uniforms rechnet {@link #uniforms}
 * ohne Grafikkarte; Stufe {@link Clouds.Quality#FLAT} zeichnet nur die hohe Schicht und die untere als dünne Fläche
 * (2.5D, ein Schritt).
 */
public final class CloudGl {
    public static final int SHADOW_N = 256;
    public static final double SHADOW_SIZE = 24_000;
    /** Höhen der Stützstellen für das Licht des Gestirns (wie {@code sunAt} im Shader). */
    public static final double[] LIGHT_HEIGHTS = {0, 500, 1000, 2000, 4000, 7000, 10_000, 14_000};

    /** Blick: Matrizen (spaltenweise), Auge in m, Tiefenkonvention, Bildgröße, Laufzeit. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public double[] eye = {0, 10, 0};
        public boolean reversedZ;
        public int width = 1, height = 1;
    }

    /**
     * Licht (vorbelichtet): Richtung zum Gestirn (Sonne, nachts der Mond), seine Farbe in den Höhen
     * {@link #LIGHT_HEIGHTS} (gedämpft, im Erdschatten aus), Himmelslicht von oben und Meerlicht von unten (Strahldichte),
     * Dunst je m, Blitz (Ort, Stärke).
     */
    /** Helligkeit der fernen Decke gegen das Licht, das auf sie fällt (am Bild gegen die nahen Wolken abgeglichen). */
    static final double DECK_GAIN = 0.35;

    public static final class Light {
        public double[] dir = {0, 1, 0};
        public double[][] colorAt = new double[8][3];
        public double[] ambTop = {0.1, 0.12, 0.15}, ambBot = {0.01, 0.012, 0.012};
        public double haze = 1e-4;
        /** Blitzleuchten im Raymarching (alt, Phase 3: Ort, Stärke); Phase 10 nimmt die Lichtpunkte unten. */
        public double[] flash = {0, 0, 0, 0};
        /**
         * Lichtpunkte von Blitzen in oder unter der Wolke (höchstens vier): Ort relativ zur Szene und Lichtstärke als
         * RGB in cd mal Vorbelichtung, je vier Zahlen (x, y, z, —) und (r, g, b, —). Sie beleuchten die Wolke beim
         * Auflegen, ohne zeitliches Nachziehen.
         */
        public float[] flashPos = new float[16], flashRgb = new float[16];
        public int flashCount;
        /** Diffusionslänge des Lichts in der Wolke (m). */
        public double flashDiffusion = 1300;
    }

    /** Ein Uniform: Art (f, v3a = vec3-Feld, m4, i) und Werte. */
    public record U(String kind, float[] v) {
        static U f(double... x) {
            float[] a = new float[x.length];
            for (int i = 0; i < x.length; i++) a[i] = (float) x[i];
            return new U("f", a);
        }
        static U i(int x) { return new U("i", new float[]{x}); }
    }

    private int progMarch, progPano, progResolve, progComposite, progShadow;
    private int texBase, texDetail, texWeather, texShadow, fboShadow;
    private int texCur, texDist, fboCur, texPanoCl, texPanoDist, fboPano;
    private final int[] texHist = new int[2], fboHist = new int[2];
    private int vao, cw, ch, pw, ph, hist, frame;
    private float[] prevVP;
    private boolean histValid, ready;
    private double[] shadowRect = {0, 0, SHADOW_SIZE};
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }
    /** Schattenkarte (R16F, Anteil direkten Lichts) und ihr Rechteck: x, z der Ecke, Kantenlänge. */
    public int shadowTexture() { return texShadow; }
    public double[] shadowRect() { return shadowRect.clone(); }

    // ------------------------------------------------------------------ Aufbau

    public void init(GL4 gl) {
        progMarch = Glsl.program(gl, "Wolken", new String[0], "fullscreen.vert", "clouds.frag");
        progPano = Glsl.program(gl, "Wolken rundum", new String[]{"PANORAMA"}, "fullscreen.vert", "clouds.frag");
        progResolve = Glsl.program(gl, "Wolken mischen", new String[0], "fullscreen.vert", "resolve.frag");
        progComposite = Glsl.program(gl, "Wolken auflegen", new String[0], "fullscreen.vert", "composite.frag");
        progShadow = Glsl.program(gl, "Wolkenschatten", new String[0], "fullscreen.vert", "shadow.frag");
        texBase = tex3d(gl, Noise.BASE, Noise.base());
        texDetail = tex3d(gl, Noise.DETAIL, Noise.detail());
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        texWeather = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, texWeather);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, Noise.WEATHER, Noise.WEATHER, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(Noise.weather()));
        params(gl, GL.GL_TEXTURE_2D, GL.GL_REPEAT);
        texShadow = tex2d(gl, SHADOW_N, SHADOW_N, GL.GL_R16F, GL.GL_CLAMP_TO_EDGE);
        fboShadow = fbo(gl, texShadow, 0);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        info = String.format(Locale.GERMAN, "Rauschen %d³ + %d³, Karte %d², Schatten %d² über %.0f km", Noise.BASE, Noise.DETAIL, Noise.WEATHER,
                SHADOW_N, SHADOW_SIZE / 1000);
        ready = true;
    }

    private static int tex3d(GL4 gl, int n, byte[] data) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL4.GL_TEXTURE_3D, 0, GL.GL_RGBA8, n, n, n, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(data));
        params(gl, GL4.GL_TEXTURE_3D, GL.GL_REPEAT);
        gl.glTexParameteri(GL4.GL_TEXTURE_3D, GL4.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, 0);
        return t[0];
    }

    private static void params(GL4 gl, int target, int wrap) {
        gl.glTexParameteri(target, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(target, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(target, GL.GL_TEXTURE_WRAP_S, wrap);
        gl.glTexParameteri(target, GL.GL_TEXTURE_WRAP_T, wrap);
    }

    private static int tex2d(GL4 gl, int w, int h, int fmt, int wrap) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        params(gl, GL.GL_TEXTURE_2D, wrap);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private static int fbo(GL4 gl, int c0, int c1) {
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, c0, 0);
        if (c1 != 0) {
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL4.GL_COLOR_ATTACHMENT1, GL.GL_TEXTURE_2D, c1, 0);
            gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL4.GL_COLOR_ATTACHMENT1}, 0);
        }
        int s = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        if (s != GL.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("cloud: Zielpuffer unvollständig (0x" + Integer.toHexString(s) + ")");
        return f[0];
    }

    private void targets(GL4 gl, int w, int h) {
        if (fboCur != 0) {
            gl.glDeleteFramebuffers(3, new int[]{fboCur, fboHist[0], fboHist[1]}, 0);
            gl.glDeleteTextures(4, new int[]{texCur, texDist, texHist[0], texHist[1]}, 0);
        }
        texCur = tex2d(gl, w, h, GL.GL_RGBA16F, GL.GL_CLAMP_TO_EDGE);
        texDist = tex2d(gl, w, h, GL4.GL_R32F, GL.GL_CLAMP_TO_EDGE);
        fboCur = fbo(gl, texCur, texDist);
        for (int i = 0; i < 2; i++) {
            texHist[i] = tex2d(gl, w, h, GL.GL_RGBA16F, GL.GL_CLAMP_TO_EDGE);
            fboHist[i] = fbo(gl, texHist[i], 0);
        }
        cw = w;
        ch = h;
        histValid = false;
    }

    private void panoTargets(GL4 gl, int w, int h) {
        if (fboPano != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fboPano}, 0);
            gl.glDeleteTextures(2, new int[]{texPanoCl, texPanoDist}, 0);
        }
        texPanoCl = tex2d(gl, w, h, GL.GL_RGBA16F, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texPanoCl);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        texPanoDist = tex2d(gl, w, h, GL4.GL_R32F, GL.GL_CLAMP_TO_EDGE);
        fboPano = fbo(gl, texPanoCl, texPanoDist);
        pw = w;
        ph = h;
    }

    // ------------------------------------------------------------------ Uniforms (ohne Grafikkarte)

    /** Gemeinsame Uniforms (Art, Schwelle, Drift) und die des Lichts. */
    public static Map<String, U> common(Clouds.Snapshot s, Light l, double[] eye) {
        Map<String, U> m = new LinkedHashMap<>();
        var t = s.type();
        m.put("uType1", U.f(t.base, t.thickness, t.maxThickness, t.strat));
        m.put("uType2", U.f(t.sigma, t.anvil, s.tile(), s.noiseTile()));
        m.put("uMisc", U.f(s.detailTile(), s.threshold(), s.time() * s.evolve(), s.highThreshold()));
        m.put("uOff", U.f(s.offX(), s.offZ(), s.highCover(), s.precipitation()));
        m.put("uCore", U.f(s.core()));
        m.put("uLens", U.f(s.lens(), Math.toRadians(s.lensFromDeg()), 0, 0));
        float[] sun = new float[24];
        for (int i = 0; i < 8; i++) for (int k = 0; k < 3; k++) sun[i * 3 + k] = (float) l.colorAt[i][k];
        m.put("uSunE", new U("v3a", sun));
        m.put("uLightDir", U.f(norm(l.dir)));
        m.put("uAmbTop", U.f(l.ambTop));
        m.put("uAmbBot", U.f(l.ambBot));
        m.put("uHaze", U.f(l.haze));
        m.put("uFlash", U.f(l.flash));
        m.put("uCam", U.f(eye));
        m.put("uBase", U.i(0));
        m.put("uDetail", U.i(1));
        m.put("uWeather", U.i(2));
        return m;
    }

    /** Uniforms je Programm: "march", "pano", "shadow". */
    public Map<String, Map<String, U>> uniforms(Clouds.Snapshot s, Light l, View v) {
        Map<String, Map<String, U>> all = new LinkedHashMap<>();
        Clouds.Quality q = s.quality();
        Map<String, U> march = new LinkedHashMap<>(common(s, l, v.eye));
        march.put("uInvVP", new U("m4", v.invViewProj != null ? v.invViewProj : id4()));
        march.put("uReversed", U.i(v.reversedZ ? 1 : 0));
        boolean flat = q == Clouds.Quality.FLAT;
        march.put("uSteps", U.f(flat ? 1 : q.maxSteps, flat ? 1 : q.stepNear));
        march.put("uLightSteps", U.i(flat ? 2 : q.lightSteps));
        march.put("uMaxDist", U.f(s.maxDistance()));
        maxDist = s.maxDistance();
        march.put("uFlat", U.i(flat ? 1 : 0));
        march.put("uFrame", U.i(frame));
        // geschlossene Decke: hinter der größten Entfernung und im Dunst bleibt sie grau statt durchsichtig
        double deck = Clouds.smooth(4, 8, s.oktas()), sy = Math.max(0.05, l.dir[1]);
        double[] dc = new double[3];
        // Licht über der Decke (4 km, weniger gerötet), in der dicken Wolke zur Hälfte entfärbt (Vielfachstreuung)
        double[] sc = l.colorAt[4];
        double lum = 0.2126 * sc[0] + 0.7152 * sc[1] + 0.0722 * sc[2];
        for (int c = 0; c < 3; c++)
            dc[c] = ((sc[c] + lum) * 0.5 * sy + l.ambTop[c] * Math.PI) * s.type().overcastBrightness * DECK_GAIN / Math.PI;
        march.put("uDeck", U.f(dc[0], dc[1], dc[2], deck));
        all.put("march", march);
        Map<String, U> pano = new LinkedHashMap<>(march);
        pano.put("uSteps", U.f(64, 150));
        pano.put("uFlat", U.i(0));
        pano.put("uLightSteps", U.i(3));
        all.put("pano", pano);
        Map<String, U> sh = new LinkedHashMap<>(common(s, l, v.eye));
        sh.put("uRect", U.f(shadowRect[0], shadowRect[1], shadowRect[2], 0));
        all.put("shadow", sh);
        return all;
    }

    /** Rechteck der Schattenkarte um das Auge, auf Texel gerastet (kein Flimmern beim Fahren). */
    public static double[] rectAround(double[] eye) {
        double cell = SHADOW_SIZE / SHADOW_N;
        double x = Math.floor(eye[0] / cell) * cell - SHADOW_SIZE / 2, z = Math.floor(eye[2] / cell) * cell - SHADOW_SIZE / 2;
        return new double[]{x, z, SHADOW_SIZE};
    }

    // ------------------------------------------------------------------ je Bild

    /** Schattenkarte rechnen (vor dem Meer). */
    public void update(GL4 gl, Clouds.Snapshot s, Light l, double[] eye) {
        int[] prevFbo = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        shadowRect = rectAround(eye);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        bindNoise(gl);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboShadow);
        gl.glViewport(0, 0, SHADOW_N, SHADOW_N);
        gl.glUseProgram(progShadow);
        View v = new View();
        v.eye = eye;
        apply(gl, progShadow, uniforms(s, l, v).get("shadow"));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    private void bindNoise(GL4 gl) {
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, texBase);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL4.GL_TEXTURE_3D, texDetail);
        gl.glActiveTexture(GL.GL_TEXTURE2);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texWeather);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    /**
     * Wolken rechnen und über das gebundene Ziel legen (dessen Viewport bleibt). Ohne Tiefentest: vor die Geometrie
     * aufrufen, also nach dem Himmel und vor Meer und Turm.
     */
    public void draw(GL4 gl, Clouds.Snapshot s, Light l, View v) {
        if (s.oktas() <= 0 && s.highCover() <= 0) { histValid = false; return; }
        int[] prevFbo = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        int div = Math.max(1, s.quality().divisor);
        int w = Math.max(16, v.width / div), h = Math.max(16, v.height / div);
        if (w != cw || h != ch) targets(gl, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        bindNoise(gl);
        // 1 rechnen
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboCur);
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(progMarch);
        Map<String, U> m = uniforms(s, l, v).get("march");
        m.put("uTarget", U.f(w, h));
        apply(gl, progMarch, m);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        // 2 mit dem letzten Bild mischen
        int next = hist ^ 1;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboHist[next]);
        gl.glUseProgram(progResolve);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texCur);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texDist);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texHist[hist]);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        Map<String, U> r = new LinkedHashMap<>();
        r.put("uCurr", U.i(3));
        r.put("uCurrDist", U.i(4));
        r.put("uHist", U.i(5));
        r.put("uInvVP", new U("m4", v.invViewProj));
        r.put("uPrevVP", new U("m4", prevVP != null ? prevVP : v.viewProj));
        r.put("uReversed", U.i(v.reversedZ ? 1 : 0));
        r.put("uHistValid", U.i(histValid ? 1 : 0));
        r.put("uCam", U.f(v.eye));
        r.put("uBlend", U.f(0.85));
        apply(gl, progResolve, r);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        hist = next;
        histValid = true;
        prevVP = v.viewProj.clone();
        frame++;
        // 3 auflegen
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        composite(gl, texHist[hist], texDist, l, v.invViewProj, v.reversedZ, v.eye, false);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    /** Legt ein Wolkenbild über das gebundene Ziel: Ziel · Durchlässigkeit + Licht, dazu das Licht der Blitze. */
    private double maxDist = 60_000;

    private void composite(GL4 gl, int tex, int dist, Light l, float[] inv, boolean reversed, double[] eye, boolean pano) {
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glUseProgram(progComposite);
        gl.glActiveTexture(GL.GL_TEXTURE3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glActiveTexture(GL.GL_TEXTURE4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, dist);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        int p = progComposite;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uClouds"), 3);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uDist"), 4);
        int n = l != null ? Math.min(4, l.flashCount) : 0;
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFlashN"), n);
        if (n > 0) {
            gl.glUniform4fv(gl.glGetUniformLocation(p, "uFlashPos"), 4, l.flashPos, 0);
            gl.glUniform4fv(gl.glGetUniformLocation(p, "uFlashRgb"), 4, l.flashRgb, 0);
            gl.glUniform1f(gl.glGetUniformLocation(p, "uDiff"), (float) l.flashDiffusion);
            if (inv != null) gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, "uInvVP"), 1, false, inv, 0);
            gl.glUniform1i(gl.glGetUniformLocation(p, "uReversed"), reversed ? 1 : 0);
            gl.glUniform1i(gl.glGetUniformLocation(p, "uPano"), pano ? 1 : 0);
            gl.glUniform3f(gl.glGetUniformLocation(p, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
            gl.glUniform1f(gl.glGetUniformLocation(p, "uMaxDist"), (float) maxDist);
        }
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
    }

    /** Wolken ins gebundene Rundumbild (Viewport = ganzes Bild). */
    public void drawPanorama(GL4 gl, Clouds.Snapshot s, Light l, double[] eye) {
        if (s.oktas() <= 0 && s.highCover() <= 0) return;
        int[] prevFbo = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        if (vp[2] != pw || vp[3] != ph) panoTargets(gl, vp[2], vp[3]);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        bindNoise(gl);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboPano);
        gl.glViewport(0, 0, pw, ph);
        gl.glUseProgram(progPano);
        View v = new View();
        v.eye = eye;
        apply(gl, progPano, uniforms(s, l, v).get("pano"));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        composite(gl, texPanoCl, texPanoDist, l, null, true, eye, true);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    static void apply(GL4 gl, int prog, Map<String, U> m) {
        for (Map.Entry<String, U> e : m.entrySet()) {
            int loc = gl.glGetUniformLocation(prog, e.getKey());
            if (loc < 0) loc = gl.glGetUniformLocation(prog, e.getKey() + "[0]");
            if (loc < 0) continue;
            float[] v = e.getValue().v();
            switch (e.getValue().kind()) {
                case "i" -> gl.glUniform1i(loc, (int) v[0]);
                case "m4" -> gl.glUniformMatrix4fv(loc, 1, false, v, 0);
                case "v3a" -> gl.glUniform3fv(loc, v.length / 3, v, 0);
                default -> {
                    switch (v.length) {
                        case 1 -> gl.glUniform1f(loc, v[0]);
                        case 2 -> gl.glUniform2f(loc, v[0], v[1]);
                        case 3 -> gl.glUniform3f(loc, v[0], v[1], v[2]);
                        default -> gl.glUniform4f(loc, v[0], v[1], v[2], v[3]);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Selbsttest

    /** Liest die Schattenkarte zurück und vergleicht 64 Texel mit {@link Clouds#shadow} (siehe {@link #compare}). */
    public String selfCheck(GL4 gl, Clouds.Snapshot s, Light l) {
        FloatBuffer fb = Buffers.newDirectFloatBuffer(SHADOW_N * SHADOW_N);
        gl.glBindTexture(GL.GL_TEXTURE_2D, texShadow);
        gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL4.GL_RED, GL.GL_FLOAT, fb);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        float[] gpu = new float[SHADOW_N * SHADOW_N];
        fb.get(gpu);
        return compare(s, l, shadowRect, gpu);
    }

    /**
     * Vergleich einer zurückgelesenen Schattenkarte mit dem Prozessor (auch für Werkzeuge ohne JOGL). An Wolkenrändern
     * kippt ein Schritt zwischen „trifft“ und „trifft nicht“, wenn die Grafikkarte linear mit wenigen Bits filtert;
     * darum zählen der Median und die Zahl der Ausreißer, nicht der größte Fehler.
     */
    public static String compare(Clouds.Snapshot s, Light l, double[] rect, float[] gpu) {
        double[] err = new double[64];
        double mean = 0;
        for (int k = 0; k < 64; k++) {
            int i = (k * 37 + 11) % SHADOW_N, j = (k * 53 + 5) % SHADOW_N;
            double x = rect[0] + (i + 0.5) / SHADOW_N * rect[2], z = rect[1] + (j + 0.5) / SHADOW_N * rect[2];
            double cpu = Clouds.shadow(s, x, z, norm(l.dir));
            err[k] = Math.abs(cpu - gpu[j * SHADOW_N + i]);
            mean += cpu;
        }
        java.util.Arrays.sort(err);
        int out = 0;
        for (double e : err) if (e > 0.05) out++;
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Wolkenschatten Median ± %.3f, %d von 64 Randpunkten über 0,05%s (mittleres Licht %.2f)",
                err[32], out, err[32] < 0.01 && out <= 8 ? "" : " – ABWEICHUNG", mean / 64);
    }

    static double[] norm(double[] v) {
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return n < 1e-12 ? new double[]{0, 1, 0} : new double[]{v[0] / n, v[1] / n, v[2] / n};
    }

    static float[] id4() { return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        for (int p : new int[]{progMarch, progPano, progResolve, progComposite, progShadow}) gl.glDeleteProgram(p);
        gl.glDeleteTextures(4, new int[]{texBase, texDetail, texWeather, texShadow}, 0);
        gl.glDeleteFramebuffers(1, new int[]{fboShadow}, 0);
        if (fboCur != 0) {
            gl.glDeleteFramebuffers(3, new int[]{fboCur, fboHist[0], fboHist[1]}, 0);
            gl.glDeleteTextures(4, new int[]{texCur, texDist, texHist[0], texHist[1]}, 0);
        }
        if (fboPano != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fboPano}, 0);
            gl.glDeleteTextures(2, new int[]{texPanoCl, texPanoDist}, 0);
        }
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ready = false;
    }
}
