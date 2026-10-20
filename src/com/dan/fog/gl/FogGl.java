package com.dan.fog.gl;

import com.dan.fog.Fog;
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
 * Der Seenebel auf der Grafikkarte. Zwei Schritte je Bild:
 * <ol>
 * <li>{@link #update}: die Karte der Bänke um das Auge (Bank und Obergrenze je Texel, RG16F) neu rechnen, wenn sich
 * der Nebel bewegt oder das Auge weit gegangen ist.</li>
 * <li>{@link #draw}: ein Bilddurchgang über die fertige Szene. Er braucht eine Kopie der Szenentiefe und schreibt
 * (Streulicht, Durchlässigkeit) mit der Mischung {@code ONE, SRC_ALPHA} ins gebundene Ziel.</li>
 * </ol>
 * Das Licht kommt vorbelichtet von außen ({@link Light}), die Schritte aus der Stufe des Kerns. Ein Feuer im Nebel
 * leuchtet nicht hier, sondern dort, wo die App das Streufeld des Nebels einem Strahl gibt.
 */
public final class FogGl {
    /** Ansicht: inverse Projektion·Blick, Auge, Tiefenkonvention, Bildgröße, Kopie der Szenentiefe. */
    public static final class View {
        public float[] invViewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public int width = 1, height = 1, depthTexture;
        /** Größte Entfernung des Blicks (m). */
        public double maxDistance = 9000;
        /** Fester Versatz der Schritte 0..1, oder negativ für Rauschen je Bildpunkt. */
        public double jitter = -1;
        public int frame;
    }

    /** Licht, vorbelichtet: Sonne und Mond (Richtung, Farbe), Himmel oben und am Horizont (Strahldichte). */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] ambientTop = {0.2, 0.3, 0.5}, ambientHorizon = {0.5, 0.6, 0.7};
    }

    private int prog, mapProg, checkProg, vao, mapTex, mapFbo, mapSize;
    private double mapX0, mapZ0, mapExtent = 18_000;
    private Fog.Snapshot mapFor;
    private double[] mapEye;
    private boolean ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }

    public void init(GL4 gl) {
        String common = read("fog_common.glsl"), vert = "#version 430 core\n" + read("fog.vert");
        prog = program(gl, vert, "#version 430 core\n" + common + "\n" + strip(read("fog.frag")));
        mapProg = program(gl, vert, "#version 430 core\n" + common + "\n" + strip(read("fog_map.frag")));
        checkProg = program(gl, vert, "#version 430 core\n" + common + "\n" + strip(read("check.frag")));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
        info = "Nebel im Bilddurchgang";
    }

    /**
     * Karte der Bänke um das Auge neu rechnen, wenn nötig (Nebel gezogen, Werte geändert, Auge mehr als 1 km gegangen).
     * Die Karte ist auf ihre Texel gerastet, damit nichts flimmert, wenn sie nachrückt.
     */
    public void update(GL4 gl, Fog.Snapshot s, double[] eye) {
        int size = s.quality().mapSize;
        if (size != mapSize) {
            if (mapTex != 0) { gl.glDeleteFramebuffers(1, new int[]{mapFbo}, 0); gl.glDeleteTextures(1, new int[]{mapTex}, 0); }
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            mapTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RG16F, size, size);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            gl.glGenFramebuffers(1, t, 0);
            mapFbo = t[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, mapFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, mapTex, 0);
            mapSize = size;
            mapFor = null;
        }
        boolean moved = mapEye == null || Math.hypot(eye[0] - mapEye[0], eye[2] - mapEye[2]) > 1000;
        if (!moved && mapFor != null && same(mapFor, s)) return;
        if (!s.any() && mapFor != null && !mapFor.any()) return;
        double texel = mapExtent / size;
        mapX0 = Math.floor((eye[0] - mapExtent / 2) / texel) * texel;
        mapZ0 = Math.floor((eye[2] - mapExtent / 2) / texel) * texel;
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, mapFbo);
        gl.glViewport(0, 0, size, size);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(mapProg);
        setFog(gl, mapProg, s);
        gl.glUniform4f(gl.glGetUniformLocation(mapProg, "uMapRect"), (float) mapX0, (float) mapZ0, (float) mapExtent, (float) texel);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        mapFor = s;
        mapEye = eye.clone();
        info = String.format(Locale.GERMAN, "Karte %d² über %.0f km, %d Schritte", size, mapExtent / 1000, s.quality().steps);
    }

    private static boolean same(Fog.Snapshot a, Fog.Snapshot b) {
        return a.sigma() == b.sigma() && a.threshold() == b.threshold() && a.top() == b.top() && a.bankSize() == b.bankSize() && a.coverage() == b.coverage()
                && a.seed() == b.seed() && Math.abs(a.offX() - b.offX()) < 1 && Math.abs(a.offZ() - b.offZ()) < 1;
    }

    /** Der Nebel über das gebundene Ziel (Tiefentest aus, Mischung ONE, SRC_ALPHA). */
    public void draw(GL4 gl, Fog.Snapshot s, View view, Light light) {
        if (!s.any() || mapTex == 0) return;
        gl.glUseProgram(prog);
        setFog(gl, prog, s);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uInvVP"), 1, false, view.invViewProj, 0);
        set3(gl, prog, "uCam", view.eye);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uSteps"), s.quality().steps);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uMaxDist"), (float) view.maxDistance);
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uViewport"), view.width, view.height);
        gl.glUniform4f(gl.glGetUniformLocation(prog, "uMapRect"), (float) mapX0, (float) mapZ0, (float) mapExtent, (float) (1 / mapExtent));
        set3(gl, prog, "uSunDir", light.sunDir);
        set3(gl, prog, "uSunCol", light.sunColor);
        set3(gl, prog, "uMoonDir", light.moonDir);
        set3(gl, prog, "uMoonCol", light.moonColor);
        set3(gl, prog, "uAmbTop", light.ambientTop);
        set3(gl, prog, "uAmbHor", light.ambientHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uJitter"), (float) view.jitter);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uFrame"), view.frame);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uDepth"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uMap"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    /** Die Karte der Bänke (RG: Bank, Obergrenze) und ihr Rechteck {x0, z0, Kantenlänge} für fremde Shader. */
    public int mapTexture() { return mapTex; }
    public double[] mapRect() { return new double[]{mapX0, mapZ0, mapExtent}; }

    static void setFog(GL4 gl, int p, Fog.Snapshot s) {
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFogA"), (float) s.sigma(), (float) s.threshold(), (float) s.top(), (float) s.softness());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFogB"), (float) s.bankSize(), (float) s.wisps(), (float) s.offX(), (float) s.offZ());
        gl.glUniform4f(gl.glGetUniformLocation(p, "uFogC"), (float) s.waterLevel(), (float) s.coverage(), (float) s.base(), 0f);
        gl.glUniform1i(gl.glGetUniformLocation(p, "uFogSeed"), s.seed());
    }

    private static void set3(GL4 gl, int p, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(p, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet die Dichte an 64 Orten in und um die Schicht auf der Grafikkarte und vergleicht mit {@link Fog#density}.
     * „Grafikkarte = Prozessor: Nebel ± 0,02 % (Median), größte 0,3 %, 64 Orte, 41 im Nebel“
     */
    public String selfCheck(GL4 gl, Fog fog, double[] center) {
        Fog.Snapshot s = fog.snapshot();
        if (!s.any()) return "";
        float[] pts = new float[64 * 4];
        java.util.Random r = new java.util.Random(64);
        for (int i = 0; i < 64; i++) {
            pts[i * 4] = (float) (center[0] + (r.nextDouble() - 0.5) * 6000);
            pts[i * 4 + 1] = (float) (s.waterLevel() + r.nextDouble() * s.top() * 1.4);
            pts[i * 4 + 2] = (float) (center[2] + (r.nextDouble() - 0.5) * 6000);
        }
        int[] v = new int[2];
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, 1);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        setFog(gl, checkProg, s);
        gl.glUniform4fv(gl.glGetUniformLocation(checkProg, "uP[0]"), 64, pts, 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 4);
        gl.glReadPixels(0, 0, 64, 1, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        List<Double> err = new ArrayList<>();
        int inFog = 0;
        for (int i = 0; i < 64; i++) {
            double c = Fog.density(s, pts[i * 4], pts[i * 4 + 1], pts[i * 4 + 2]);
            double g = fb.get(i * 4) / 1000.0;
            if (c > 0) inFog++;
            // bezogen auf die Dichte im Kern der Bänke
            err.add(Math.abs(g - c) / s.sigma());
        }
        double[] e = err.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Nebel ± %.2f %% (Median), größte %.2f %%, 64 Orte, %d im Nebel",
                e[e.length / 2] * 100, e[e.length - 1] * 100, inFog);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (mapProg != 0) gl.glDeleteProgram(mapProg);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        if (mapFbo != 0) gl.glDeleteFramebuffers(1, new int[]{mapFbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = mapProg = checkProg = mapTex = mapFbo = vao = mapSize = 0;
        mapFor = null;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    static String strip(String s) {
        StringBuilder b = new StringBuilder();
        for (String l : s.split("\r?\n", -1)) b.append(l.trim().startsWith("#include") ? "" : l).append('\n');
        return b.toString();
    }

    static String read(String file) {
        try (InputStream in = FogGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("fog: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("fog: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für die Demo. */
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
            throw new IllegalStateException("fog: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
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
            throw new IllegalStateException("fog: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
