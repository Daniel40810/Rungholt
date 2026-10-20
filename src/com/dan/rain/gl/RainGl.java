package com.dan.rain.gl;

import com.dan.rain.Rain;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Regen auf der Grafikkarte, in drei Teilen:
 * <ul>
 * <li><b>Vorhänge</b>: ein Bilddurchgang, der vom Rand des Tropfenkastens bis zur Szene durch die Karte der Vorhänge
 * läuft und auslöscht und streut (Schauerwände in der Ferne, Grau im Landregen).</li>
 * <li><b>Streifen</b>: eine Instanz je Tropfen, ohne Eckpuffer; Lage und Fall rechnet der Vertex-Shader aus Nummer,
 * Zeit und Wind, genau wie {@link Rain.Snapshot#drop} auf dem Prozessor.</li>
 * <li><b>Krönchen</b>: Spritzer, wo große Tropfen aufschlagen, in Zellen um das Auge; die Höhe der Fläche kommt aus
 * der Tiefe der Szene.</li>
 * </ul>
 * Dazu die <b>Ringkarte</b>: eine kachelbare Steigungstextur ({@link Rain#RIPPLE_TILE} m, RG16F mit Mipmaps), die ein
 * Wasser-Shader auf seine Normale legen kann ({@link #rippleTexture()}, {@link #rippleStrength()}). Das Paket kennt
 * das Meer nicht; die App reicht die Textur weiter.
 * <p>
 * Aufruf: {@link #update} vor dem Wasser (rechnet die Ringkarte), {@link #draw} nach der Szene ins HDR-Ziel, mit
 * einer Kopie der Szenentiefe ({@link View#depthTexture}).
 */
public final class RainGl {
    /** Ansicht: Projektion·Blick und Umkehrung, Auge, Blickrichtung, Tiefenkonvention, Bildgröße, Szenentiefe. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public double[] eye = new double[3], forward = {0, 0, -1};
        public boolean reversedZ;
        public double near = 0.2, far = 60_000, fovyDeg = 50;
        public int width = 1, height = 1, depthTexture;
        /** Belichtungszeit der Streifen in s: das Auge sieht Regen etwa wie eine Kamera mit 1/30 s. */
        public double shutter = 1 / 30.0;
    }

    /** Licht, vorbelichtet: Sonne und Mond, Himmel oben und am Horizont, Dunstlänge. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
    }

    /** Zellen je Seite und Lagen der Ringkarte; Zellgröße der Krönchen in m. */
    public static final int RIPPLE_CELLS = 16, RIPPLE_LAYERS = 4;
    public static final double CROWN_CELL = 0.6;

    private int streakProg, crownProg, curtainProg, rippleProg, slopeProg, checkProg, vao;
    private int mapTex, mapVersion = -1, mapN;
    private int rippleH, rippleS, rippleFboH, rippleFboS, rippleRes;
    private double rippleStrength;
    private boolean rippleFlat;
    private Rain.Snapshot snap;
    private boolean ready;
    private String info = "";
    private long drawn;

    public boolean ready() { return ready; }
    public String info() { return info; }
    /** Gezeichnete Streifen und Spritzer im letzten Bild (Obergrenze; tote werden im Shader verworfen). */
    public long drawn() { return drawn; }

    public void init(GL4 gl) {
        String common = read("rain_common.glsl");
        streakProg = program(gl, "#version 430 core\n" + common + "\n" + strip(read("streak.vert")), "#version 430 core\n" + read("streak.frag"));
        crownProg = program(gl, "#version 430 core\n" + common + "\n" + strip(read("crown.vert")), "#version 430 core\n" + read("streak.frag"));
        curtainProg = program(gl, "#version 430 core\n" + read("fullscreen.vert"), "#version 430 core\n" + common + "\n" + strip(read("curtain.frag")));
        rippleProg = program(gl, "#version 430 core\n" + read("fullscreen.vert"), "#version 430 core\n" + read("ripple.frag"));
        slopeProg = program(gl, "#version 430 core\n" + read("fullscreen.vert"), "#version 430 core\n" + read("slope.frag"));
        checkProg = program(gl, "#version 430 core\n" + read("check.vert"), "#version 430 core\n" + common + "\n" + read("check.frag"));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenTextures(1, v, 0);
        mapTex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, 1, 1, 0, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(new float[]{1}));
        filter(gl, GL.GL_NEAREST, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        ready = true;
        info = "Streifen, Vorhänge, Krönchen und Ringkarte aus Nummer, Zeit und Wind";
    }

    private static void filter(GL4 gl, int f, int wrap) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, f);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, f == GL.GL_LINEAR_MIPMAP_LINEAR ? GL.GL_LINEAR : f);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrap);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, wrap);
    }

    // ------------------------------------------------------------------ Lauf

    /** Karte der Vorhänge hochladen (wenn neu) und die Ringkarte rechnen. Danach sind Rahmenpuffer und Ansicht wiederhergestellt. */
    public void update(GL4 gl, Rain.Snapshot s) {
        snap = s;
        if (s.mapVersion() != mapVersion) {
            mapVersion = s.mapVersion();
            mapN = s.mapN();
            gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
            if (mapN > 0) gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R32F, mapN, mapN, 0, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(s.map()));
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
        updateRipples(gl, s);
    }

    private void updateRipples(GL4 gl, Rain.Snapshot s) {
        int res = s.quality().rippleRes;
        if (res != rippleRes) {
            if (rippleH != 0) {
                gl.glDeleteFramebuffers(2, new int[]{rippleFboH, rippleFboS}, 0);
                gl.glDeleteTextures(2, new int[]{rippleH, rippleS}, 0);
            }
            int[] t = new int[2], f = new int[2];
            gl.glGenTextures(2, t, 0);
            gl.glGenFramebuffers(2, f, 0);
            rippleH = t[0];
            rippleS = t[1];
            rippleFboH = f[0];
            rippleFboS = f[1];
            int levels = 1 + (int) Math.floor(Math.log(res) / Math.log(2));
            gl.glBindTexture(GL.GL_TEXTURE_2D, rippleH);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R16F, res, res);
            filter(gl, GL.GL_NEAREST, GL.GL_REPEAT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, rippleS);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, levels, GL4.GL_RG16F, res, res);
            filter(gl, GL.GL_LINEAR_MIPMAP_LINEAR, GL.GL_REPEAT);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            bindTarget(gl, rippleFboH, rippleH);
            bindTarget(gl, rippleFboS, rippleS);
            rippleRes = res;
            rippleFlat = false;
        }
        double here = s.rateAt(s.eyeX(), s.eyeZ());
        rippleStrength = Math.min(1, here / Math.max(1e-6, s.rate())) * Math.min(1, Math.sqrt(s.rate() / 2));
        double cell = Rain.RIPPLE_TILE / RIPPLE_CELLS, perCell = Rain.impacts(s.rate(), 1.0) * cell * cell;
        double period = perCell <= 0 ? 1e6 : Math.max(1.0, RIPPLE_LAYERS / perCell);
        double active = Math.min(1, perCell * period / RIPPLE_LAYERS);
        if (active <= 0 && rippleFlat) return;
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        boolean depth = gl.glIsEnabled(GL.GL_DEPTH_TEST), blend = gl.glIsEnabled(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        gl.glViewport(0, 0, rippleRes, rippleRes);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, rippleFboH);
        gl.glUseProgram(rippleProg);
        gl.glUniform1f(gl.glGetUniformLocation(rippleProg, "uTime"), (float) (s.time() % 3600));
        gl.glUniform1f(gl.glGetUniformLocation(rippleProg, "uTile"), (float) Rain.RIPPLE_TILE);
        gl.glUniform1f(gl.glGetUniformLocation(rippleProg, "uPeriod"), (float) Math.min(period, 1e5));
        gl.glUniform1f(gl.glGetUniformLocation(rippleProg, "uActive"), (float) active);
        gl.glUniform1i(gl.glGetUniformLocation(rippleProg, "uCells"), RIPPLE_CELLS);
        gl.glUniform1i(gl.glGetUniformLocation(rippleProg, "uLayers"), RIPPLE_LAYERS);
        gl.glUniform1ui(gl.glGetUniformLocation(rippleProg, "uSeed"), s.seed());
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, rippleFboS);
        gl.glUseProgram(slopeProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, rippleH);
        gl.glUniform1i(gl.glGetUniformLocation(slopeProg, "uHeight"), 0);
        gl.glUniform1f(gl.glGetUniformLocation(slopeProg, "uTexel"), (float) (Rain.RIPPLE_TILE / rippleRes));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindTexture(GL.GL_TEXTURE_2D, rippleS);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        if (blend) gl.glEnable(GL.GL_BLEND);
        rippleFlat = active <= 0;
    }

    private static void bindTarget(GL4 gl, int fbo, int tex) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    /** Steigungen der Ringe (RG16F, kachelbar, mit Mipmaps; 0 vor dem ersten {@link #update}). */
    public int rippleTexture() { return rippleS; }
    /** Kantenlänge einer Kachel der Ringkarte in m. */
    public double rippleTile() { return Rain.RIPPLE_TILE; }
    /** Wie stark die Ringe am Auge sind (0 = trocken, 1 = voller Regen): für den Wasser-Shader. */
    public double rippleStrength() { return rippleStrength; }

    private void setRain(GL4 gl, int p, Rain.Snapshot s) {
        gl.glUniform4f(gl.glGetUniformLocation(p, "uRainA"), (float) s.eyeX(), (float) s.eyeY(), (float) s.eyeZ(), (float) s.box());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uRainB"), (float) s.driftX(), (float) s.driftZ(), (float) s.fall(), (float) s.lambda());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uRainC"), (float) s.windX(), (float) s.windZ(), (float) s.rate(), (float) s.boxY());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uRainMap"), (float) s.mapX0(), (float) s.mapZ0(), (float) s.mapCell(), s.mapN());
        gl.glUniform1f(gl.glGetUniformLocation(p, "uCloudBase"), (float) s.cloudBase());
        gl.glUniform1ui(gl.glGetUniformLocation(p, "uRainSeed"), s.seed());
        float[] sh = new float[16];
        System.arraycopy(s.shelter(), 0, sh, 0, s.shelter().length);
        gl.glUniform4fv(gl.glGetUniformLocation(p, "uRainShelter[0]"), 4, sh, 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uRainShelterN"), s.shelter().length / 4);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uRainMapTex"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
    }

    private void setLight(GL4 gl, int p, View v, Light l, double strength) {
        set3(gl, p, "uEye", v.eye);
        set3(gl, p, "uSunDir", l.sunDir);
        set3(gl, p, "uSunColor", l.sunColor);
        set3(gl, p, "uMoonDir", l.moonDir);
        set3(gl, p, "uMoonColor", l.moonColor);
        set3(gl, p, "uSkyTop", l.skyTop);
        set3(gl, p, "uSkyHorizon", l.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uStrength"), (float) Math.max(0, Math.min(2, strength)));
        gl.glUniform1i(gl.glGetUniformLocation(p, "uReversed"), v.reversedZ ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uNear"), (float) v.near);
        gl.glUniform1f(gl.glGetUniformLocation(p, "uFar"), (float) v.far);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, v.depthTexture);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uSceneDepth"), 0);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uHasDepth"), v.depthTexture != 0 ? 1 : 0);
    }

    /**
     * Vorhänge, Streifen und Krönchen ins gebundene Ziel (vormultipliziert). {@code strength} 0..2 regelt die Deckkraft
     * (1 = nach Rechnung). Ohne Regen (Rate 0) zeichnet es nichts.
     */
    public void draw(GL4 gl, View v, Light l, double strength) {
        Rain.Snapshot s = snap;
        drawn = 0;
        if (s == null || s.rate() <= 0 || strength <= 0) return;
        gl.glBindVertexArray(vao);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        // 1 Vorhänge: über die ganze Szene, Tiefe aus der Kopie
        if (v.depthTexture != 0 && v.invViewProj != null) {
            gl.glDisable(GL.GL_DEPTH_TEST);
            gl.glUseProgram(curtainProg);
            setRain(gl, curtainProg, s);
            setLight(gl, curtainProg, v, l, strength);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(curtainProg, "uInvVP"), 1, false, v.invViewProj, 0);
            gl.glUniform1f(gl.glGetUniformLocation(curtainProg, "uStart"), (float) (s.box() * 0.8));
            gl.glUniform1i(gl.glGetUniformLocation(curtainProg, "uSteps"), s.quality().curtainSteps);
            gl.glUniform1f(gl.glGetUniformLocation(curtainProg, "uTime"), (float) (s.time() % 3600));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glEnable(GL.GL_DEPTH_TEST);
        double pixel = 2 * Math.tan(Math.toRadians(v.fovyDeg) / 2) / Math.max(1, v.height);
        // 2 Streifen
        gl.glUseProgram(streakProg);
        setRain(gl, streakProg, s);
        setLight(gl, streakProg, v, l, strength);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(streakProg, "uVP"), 1, false, v.viewProj, 0);
        set3(gl, streakProg, "uFwd", v.forward);
        gl.glUniform1f(gl.glGetUniformLocation(streakProg, "uShutter"), (float) v.shutter);
        gl.glUniform1f(gl.glGetUniformLocation(streakProg, "uPixel"), (float) pixel);
        gl.glUniform1f(gl.glGetUniformLocation(streakProg, "uAlpha"), (float) Rain.streakAlpha(s.rate()));
        int n = s.quality().drops;
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, n);
        drawn += n;
        // 3 Krönchen
        if (v.depthTexture != 0 && v.invViewProj != null) {
            int cells = (int) Math.ceil(2 * s.quality().crownRadius / CROWN_CELL), per = s.quality().crownDrops;
            double big = Rain.impacts(s.rate(), 2.0) * CROWN_CELL * CROWN_CELL;
            double period = Math.max(0.3, big <= 0 ? 1e5 : 1 / big);
            double active = Math.min(1, big * period);
            gl.glUseProgram(crownProg);
            setRain(gl, crownProg, s);
            setLight(gl, crownProg, v, l, strength);
            gl.glUniform1i(gl.glGetUniformLocation(crownProg, "uHasDepth"), 0);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(crownProg, "uVP"), 1, false, v.viewProj, 0);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(crownProg, "uInvVP"), 1, false, v.invViewProj, 0);
            set3(gl, crownProg, "uFwd", v.forward);
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uPixel"), (float) pixel);
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uCell"), (float) CROWN_CELL);
            gl.glUniform1i(gl.glGetUniformLocation(crownProg, "uCells"), cells);
            gl.glUniform1i(gl.glGetUniformLocation(crownProg, "uPer"), per);
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uPeriod"), (float) Math.min(period, 1e5));
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uTime"), (float) (s.time() % 3600));
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uLevel"), (float) s.waterLevel());
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uAlpha"), (float) (Rain.streakAlpha(s.rate()) * 1.6));
            gl.glUniform1f(gl.glGetUniformLocation(crownProg, "uActive"), (float) active);
            gl.glUniform2f(gl.glGetUniformLocation(crownProg, "uViewport"), v.width, v.height);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, cells * cells * per);
            drawn += (long) cells * cells * per;
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
        info = String.format(Locale.GERMAN, "%,d Streifen (%.0f %% leben hier), Vorhang %d Schritte, Ringkarte %d²",
                n, 100 * Rain.activeFraction(s.rateAt(s.eyeX(), s.eyeZ())), s.quality().curtainSteps, rippleRes);
    }

    private static void set3(GL4 gl, int p, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(p, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet die ersten 4 096 Tropfen auf der Grafikkarte, liest sie zurück und vergleicht mit
     * {@link Rain.Snapshot#drop}. „Grafikkarte = Prozessor: Regentropfen ± 0,2 mm (Median), größte 3 mm, 900 Proben“
     */
    public String selfCheck(GL4 gl, Rain.Snapshot s) {
        if (s.rate() <= 0) return "";
        Rain.Snapshot keep = snap;
        update(gl, s);
        snap = keep;
        int[] v = new int[2];
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, 64);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        boolean depth = gl.glIsEnabled(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, 64);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        setRain(gl, checkProg, s);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 64 * 4);
        gl.glReadPixels(0, 0, 64, 64, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (depth) gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        List<Double> err = new ArrayList<>();
        int edge = 0;
        for (int i = 0; i < 4096; i++) {
            double[] c = s.drop(i);
            int o = i * 4;
            boolean gLive = fb.get(o + 3) >= 0;
            double dx = Math.abs(fb.get(o) - c[0]), dy = Math.abs(fb.get(o + 1) - c[1]), dz = Math.abs(fb.get(o + 2) - c[2]);
            // am Rand des Kastens springt ein Tropfen um eine Kastenbreite: dort zählt er nicht
            if (gLive != (c[4] > 0) || dx > s.box() || dy > s.boxY() || dz > s.box()) { edge++; continue; }
            if (!gLive) continue;
            err.add(Math.max(dx, Math.max(dy, dz)));
        }
        if (err.isEmpty()) return "Grafikkarte = Prozessor: keine lebenden Tropfen zum Vergleichen";
        double[] e = err.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Regentropfen ± %.1f mm (Median), größte %.1f mm, %d Proben, %d am Rand",
                e[e.length / 2] * 1000, e[e.length - 1] * 1000, e.length, edge);
    }

    public void dispose(GL4 gl) {
        for (int p : new int[]{streakProg, crownProg, curtainProg, rippleProg, slopeProg, checkProg}) if (p != 0) gl.glDeleteProgram(p);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        if (rippleH != 0) {
            gl.glDeleteFramebuffers(2, new int[]{rippleFboH, rippleFboS}, 0);
            gl.glDeleteTextures(2, new int[]{rippleH, rippleS}, 0);
        }
        streakProg = crownProg = curtainProg = rippleProg = slopeProg = checkProg = vao = mapTex = rippleH = rippleS = rippleRes = 0;
        mapVersion = -1;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    /** Entfernt #include-Zeilen (der gemeinsame Teil wird vorn eingesetzt). */
    static String strip(String s) {
        StringBuilder b = new StringBuilder();
        for (String l : s.split("\r?\n", -1)) b.append(l.trim().startsWith("#include") ? "" : l).append('\n');
        return b.toString();
    }

    static String read(String file) {
        try (InputStream in = RainGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("rain: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("rain: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für Demos. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("rain: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            gl.glDeleteShader(s);
            throw new IllegalStateException("rain: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
