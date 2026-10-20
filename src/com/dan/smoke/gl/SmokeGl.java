package com.dan.smoke.gl;

import com.dan.smoke.Plume;
import com.dan.smoke.Smoke;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Zeichnet den Rauch aus {@link Smoke} mit JOGL (OpenGL 4.3): die Fahnen liegen in zwei Speicherpuffern (nah, fern),
 * jeder Ball ist eine Tafel, deren Ort der Vertex-Shader aus Fahne und Nummer rechnet ({@code smoke_common.glsl}, wie
 * {@link Plume#particle}). Ohne Eckpuffer, ohne Sortieren; vormultipliziert in ein HDR-Ziel, Tiefentest an, Tiefe
 * schreiben aus. {@link #selfCheck} vergleicht die Grafikkarte mit dem Prozessor.
 */
public final class SmokeGl {
    /** Ansicht: Projektion·Blick, Auge, Tiefenkonvention, Szenentiefe (optional, für weiche Ränder). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public double near = 0.2, far = 60_000;
        public int depthTexture;
    }

    /** Licht, vorbelichtet: Sonne, Mond, Himmel oben und am Horizont, Dunst; Feuerschein als Punktlichter (bis 8). */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000;
        /** Punktlichter: je 4 Zahlen Ort und Größe der Quelle (m), je 4 Zahlen Lichtstärke RGB (vorbelichtet) und frei. */
        public float[] firePos = new float[32], fireRgb = new float[32];
        public int fireCount;
    }

    private int prog, checkProg, nearBuf, farBuf, vao, noiseTex;
    private int nearN, farN, nearPer, farPer;
    private String info = "";
    private long drawn;

    public String info() { return info; }
    public long drawn() { return drawn; }

    public void init(GL4 gl) {
        String common = read("smoke_common.glsl");
        prog = program(gl, "#version 430 core\n" + common + "\n" + read("smoke.vert"), "#version 430 core\n" + read("smoke.frag"));
        checkProg = compute(gl, "#version 430 core\n" + common + "\n" + read("check.comp"));
        int[] v = new int[2];
        gl.glGenBuffers(2, v, 0);
        nearBuf = v[0];
        farBuf = v[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        noiseTex = noise2d(gl, 128, 31);
    }

    /**
     * Seit Haithabu Phase 11: kachelbares Wertrauschen in vier Lagen (128², eine Lage je Oktave wie das frühere fbm),
     * damit der Pixel-Shader nicht für jedes Pixel 16 Zufallszahlen rechnet (Befund Phase 10: 4 bis 7 ms für Feuer und
     * Rauch auf der Radeon).
     */
    static int noise2d(GL4 gl, int n, long seed) {
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        int[] g = {8, 16, 32, 64};
        double[] w = {0.5, 0.27, 0.15, 0.08};
        double[][] lat = new double[4][];
        for (int o = 0; o < 4; o++) { lat[o] = new double[g[o] * g[o]]; for (int i = 0; i < lat[o].length; i++) lat[o][i] = r.nextDouble(); }
        java.nio.ByteBuffer b = Buffers.newDirectByteBuffer(n * n);
        for (int y = 0; y < n; y++)
            for (int x = 0; x < n; x++) {
                double v = 0;
                for (int o = 0; o < 4; o++) {
                    int gg = g[o];
                    double fx = x * gg / (double) n, fy = y * gg / (double) n;
                    int x0 = (int) fx, y0 = (int) fy;
                    double tx = fx - x0, ty = fy - y0;
                    tx = tx * tx * (3 - 2 * tx);
                    ty = ty * ty * (3 - 2 * ty);
                    double a = lat[o][x0 % gg + gg * (y0 % gg)], bb = lat[o][(x0 + 1) % gg + gg * (y0 % gg)];
                    double c = lat[o][x0 % gg + gg * ((y0 + 1) % gg)], d = lat[o][(x0 + 1) % gg + gg * ((y0 + 1) % gg)];
                    v += w[o] * ((a + (bb - a) * tx) + ((c + (d - c) * tx) - (a + (bb - a) * tx)) * ty);
                }
                b.put((byte) Math.round(Math.max(0, Math.min(1, v)) * 255));
            }
        b.flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_R8, n, n, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, b);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    /** Lädt die Fahnen aus dem letzten {@link Smoke#select} hoch. */
    public void update(GL4 gl, Smoke smoke) {
        float[] n = smoke.nearData(), f = smoke.farData();
        nearN = n.length / Plume.FLOATS;
        farN = f.length / Plume.FLOATS;
        nearPer = smoke.quality.nearCount;
        farPer = smoke.quality.farCount;
        upload(gl, nearBuf, n);
        upload(gl, farBuf, f);
        info = String.format(Locale.GERMAN, "Rauch %d nah × %d, %d fern × %d Bälle", nearN, nearPer, farN, farPer);
    }

    private static void upload(GL4 gl, int buf, float[] d) {
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(d.length == 0 ? new float[Plume.FLOATS] : d);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, fb.capacity() * 4L, fb, GL4.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
    }

    /** Zeichnet alle Bälle ins gebundene Ziel. Stärke 0..1.5 (Bedienfeld). */
    public void draw(GL4 gl, View view, Light light, double strength, double time) {
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        set3(gl, "uEye", view.eye);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunColor", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonColor", light.moonColor);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHorizon", light.skyHorizon);
        set3(gl, "uFogColor", light.fogColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1f(loc(gl, "uStrength"), (float) Math.max(0, Math.min(1.5, strength)));
        gl.glUniform1f(loc(gl, "uTime"), (float) (time % 10_000));
        gl.glUniform1i(loc(gl, "uFireN"), Math.min(8, light.fireCount));
        gl.glUniform4fv(loc(gl, "uFirePos[0]"), 8, light.firePos, 0);
        gl.glUniform4fv(loc(gl, "uFireRgb[0]"), 8, light.fireRgb, 0);
        gl.glUniform1i(loc(gl, "uHasDepth"), view.depthTexture != 0 ? 1 : 0);
        gl.glUniform1i(loc(gl, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1f(loc(gl, "uNear"), (float) view.near);
        gl.glUniform1f(loc(gl, "uFar"), (float) view.far);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, noiseTex);
        gl.glUniform1i(loc(gl, "uNoise"), 1);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(loc(gl, "uSceneDepth"), 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBindVertexArray(vao);
        drawn = 0;
        // erst die fernen (großen) Fahnen, dann die nahen: ohne Sortieren liegt so das Nahe meist vorn
        if (farN > 0) { gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, farBuf); gl.glUniform1i(loc(gl, "uPer"), farPer); gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, farN * farPer); drawn += (long) farN * farPer; }
        if (nearN > 0) { gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, nearBuf); gl.glUniform1i(loc(gl, "uPer"), nearPer); gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, nearN * nearPer); drawn += (long) nearN * nearPer; }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    /**
     * Rechnet die Bälle der nahen Fahnen (höchstens 16 Fahnen × 64) auf der Grafikkarte und vergleicht Ort, Halbmesser
     * und Deckkraft mit {@link Plume#particle}. „Grafikkarte = Prozessor: Rauch ± 0,4 mm, 1 024 Bälle“
     */
    public String selfCheck(GL4 gl, Smoke smoke) {
        float[] n = smoke.nearData();
        int plumes = Math.min(16, n.length / Plume.FLOATS), per = Math.min(64, smoke.quality.nearCount);
        if (plumes == 0) return "Rauch: keine Fahne zum Vergleichen";
        float[] part = java.util.Arrays.copyOf(n, plumes * Plume.FLOATS);
        int[] b = new int[3];
        gl.glGenBuffers(3, b, 0);
        upload(gl, b[0], part);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[1]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, plumes * per * 16L, null, GL4.GL_DYNAMIC_READ);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[2]);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, plumes * per * 4L, null, GL4.GL_DYNAMIC_READ);
        gl.glUseProgram(checkProg);
        gl.glUniform1i(gl.glGetUniformLocation(checkProg, "uPer"), per);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, b[0]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 1, b[1]);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 2, b[2]);
        gl.glDispatchCompute(plumes, 1, 1);
        gl.glMemoryBarrier(GL4.GL_BUFFER_UPDATE_BARRIER_BIT);
        FloatBuffer pos = Buffers.newDirectFloatBuffer(plumes * per * 4), al = Buffers.newDirectFloatBuffer(plumes * per);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[1]);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, plumes * per * 16L, pos);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, b[2]);
        gl.glGetBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, plumes * per * 4L, al);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        gl.glDeleteBuffers(3, b, 0);
        gl.glUseProgram(0);
        double[] r = compare(part, plumes, per, pos, al);
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Rauch ± %.1f mm (größte %.1f mm), Deckkraft ± %.4f, %,d Bälle", r[0] * 1000, r[1] * 1000, r[2], plumes * per);
    }

    /** {Median, größte Abweichung im Ort (m), größte in der Deckkraft}. */
    public static double[] compare(float[] constants, int plumes, int per, FloatBuffer pos, FloatBuffer alpha) {
        double[] dev = new double[plumes * per];
        double worst = 0, worstA = 0;
        for (int p = 0; p < plumes; p++) {
            float[] c = java.util.Arrays.copyOfRange(constants, p * Plume.FLOATS, (p + 1) * Plume.FLOATS);
            for (int i = 0; i < per; i++) {
                double[] q = Plume.particle(c, i);
                int o = p * per + i;
                double d = Math.sqrt(sq(q[0] - pos.get(o * 4)) + sq(q[1] - pos.get(o * 4 + 1)) + sq(q[2] - pos.get(o * 4 + 2))) + Math.abs(q[3] - pos.get(o * 4 + 3));
                dev[o] = d;
                worst = Math.max(worst, d);
                worstA = Math.max(worstA, Math.abs(q[4] - alpha.get(o)));
            }
        }
        java.util.Arrays.sort(dev);
        return new double[]{dev[dev.length / 2], worst, worstA};
    }

    static double sq(double v) { return v * v; }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        gl.glDeleteBuffers(2, new int[]{nearBuf, farBuf}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (noiseTex != 0) gl.glDeleteTextures(1, new int[]{noiseTex}, 0);
        prog = checkProg = noiseTex = 0;
    }

    // ------------------------------------------------------------------ Shader

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(prog, n); }

    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(loc(gl, n), (float) v[0], (float) v[1], (float) v[2]); }

    public static String read(String file) {
        try (InputStream in = SmokeGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("smoke: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("smoke: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = shader(gl, GL4.GL_VERTEX_SHADER, vert, "smoke.vert"), fs = shader(gl, GL4.GL_FRAGMENT_SHADER, frag, "smoke.frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        checkLink(gl, p);
        return p;
    }

    static int compute(GL4 gl, String src) {
        int p = gl.glCreateProgram();
        int cs = shader(gl, GL4.GL_COMPUTE_SHADER, src, "check.comp");
        gl.glAttachShader(p, cs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(cs);
        checkLink(gl, p);
        return p;
    }

    static void checkLink(GL4 gl, int p) {
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("smoke: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
    }

    static int shader(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("smoke: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return s;
    }
}
