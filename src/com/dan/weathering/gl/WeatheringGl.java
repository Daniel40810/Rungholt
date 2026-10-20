package com.dan.weathering.gl;

import com.dan.weathering.Weathering;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Die Verwitterung auf der Grafikkarte. Das Paket zeichnet keine eigenen Flächen: Es liefert den Shader-Baustein
 * {@code weathering.glsl}, den ein fremder Fragment-Shader einbindet ({@link #includeSource()}), setzt dessen Uniforms
 * und hält die Nässekarte als Textur ({@link #bind}). {@link #selfCheck} rechnet die Masken auf der Grafikkarte für ein
 * Gitter von Proben und vergleicht sie mit {@link Weathering#surface} auf dem Prozessor.
 */
public final class WeatheringGl {
    /** Name des Bausteins (Ressource neben dieser Klasse). */
    public static final String INCLUDE = "weathering.glsl";

    private int mapTex, mapW, mapH, mapVersion = -1;
    private int checkProg, checkFbo, checkTex, vao;
    private boolean ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }

    /** Der Shader-Baustein als Text, zum Einsetzen in einen eigenen Shader (nach #version). */
    public static String includeSource() { return read(INCLUDE); }

    public void init(GL4 gl) {
        int[] v = new int[1];
        gl.glGenTextures(1, v, 0);
        mapTex = v[0];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        checkProg = program(gl, "#version 430 core\n" + read("check.vert"), "#version 430 core\n" + read(INCLUDE) + "\n" + read("check.frag"));
        ready = true;
        info = "Baustein weathering.glsl, Nässekarte folgt";
    }

    /** Lädt die Nässekarte hoch, wenn sie sich geändert hat (je Bild aufrufen, billig). */
    public void update(GL4 gl, Weathering.Snapshot s) {
        if (s.mapVersion() == mapVersion && s.mapW() == mapW) return;
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(s.map());
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        if (s.mapW() != mapW || s.mapH() != mapH) {
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R16F, s.mapW(), s.mapH(), 0, GL4.GL_RED, GL.GL_FLOAT, fb);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            mapW = s.mapW();
            mapH = s.mapH();
        } else {
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, mapW, mapH, GL4.GL_RED, GL.GL_FLOAT, fb);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        mapVersion = s.mapVersion();
        info = String.format(Locale.GERMAN, "Baustein weathering.glsl, Nässekarte %d × %d", mapW, mapH);
    }

    /** Setzt die Uniforms des Bausteins im gebundenen Programm und bindet die Nässekarte an die Einheit unit. */
    public void bind(GL4 gl, int program, Weathering.Snapshot s, int unit) {
        gl.glUniform4f(gl.glGetUniformLocation(program, "uWthWater"), (float) s.level(), (float) s.mhw(), (float) s.mlw(), (float) s.runup());
        gl.glUniform4f(gl.glGetUniformLocation(program, "uWthWet"), (float) s.sprayTop(), (float) s.wetLine(), (float) s.rainWet(), (float) s.spray());
        gl.glUniform4f(gl.glGetUniformLocation(program, "uWthAmount"), (float) s.rust(), (float) s.algae(), (float) s.salt(), (float) s.moss());
        gl.glUniform2f(gl.glGetUniformLocation(program, "uWthRainFrom"), (float) s.rainFromX(), (float) s.rainFromZ());
        gl.glUniform1i(gl.glGetUniformLocation(program, "uWthSeed"), s.seed());
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, mapTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uWthMap"), unit);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uWthHasMap"), mapW > 0 ? 1 : 0);
    }

    public int mapTexture() { return mapTex; }

    // ------------------------------------------------------------------ Selbsttest

    /** Proben des Selbsttests: 64 × 64, Höhe von MNW − 1 m bis Spritzhöhe + 3 m, Normale reihum, Kante alle 1,2 m. */
    public static final int CHECK_N = 64;

    /** Eingänge einer Probe (i, j in 0..CHECK_N−1), wie check.frag sie bildet. */
    public static double[] probe(Weathering.Snapshot s, int i, int j) {
        double y0 = s.mlw() - 1, y1 = s.level() + s.sprayTop() + 3;
        double y = y0 + (y1 - y0) * (j + 0.5) / CHECK_N;
        double ss = (i + 0.5) * 0.37;
        double a = (i % 8) * 0.785398;
        double ny = (i % 5) * 0.2 - 0.2;
        double r = Math.sqrt(Math.max(0, 1 - ny * ny));
        double[] n = {Math.cos(a) * r, ny, Math.sin(a) * r};
        double below = (y1 - y) % 1.2;
        double sus = (j % 4) / 3.0;
        double u = (i + 0.5) / CHECK_N, v = (j + 0.5) / CHECK_N;
        return new double[]{y, ss, n[0], n[1], n[2], below, sus, u, v};
    }

    /**
     * Rechnet die Masken für das Probengitter auf der Grafikkarte, liest sie zurück und vergleicht mit dem Prozessor.
     * „Grafikkarte = Prozessor: Verwitterung ± 0,000 (Median), größte 0,004 (4 096 Proben)“
     */
    public String selfCheck(GL4 gl, Weathering.Snapshot s) {
        int n = CHECK_N;
        update(gl, s);
        int[] v = new int[1];
        if (checkTex == 0) {
            gl.glGenTextures(1, v, 0);
            checkTex = v[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, checkTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, n, n * 2);
            gl.glGenFramebuffers(1, v, 0);
            checkFbo = v[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, checkFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, checkTex, 0);
        }
        int[] vp = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        int[] prevFbo = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, checkFbo);
        gl.glViewport(0, 0, n, n * 2);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        bind(gl, checkProg, s, 5);
        gl.glUniform1i(gl.glGetUniformLocation(checkProg, "uN"), n);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(n * n * 2 * 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 4);
        gl.glReadPixels(0, 0, n, n * 2, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        double[] err = new double[n * n];
        double worst = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] p = probe(s, i, j);
                Weathering.Surface c = Weathering.surface(s, new double[]{0.5, 0.5, 0.5}, 0.5, 0.5, new double[]{0, p[0], 0},
                        new double[]{p[2], p[3], p[4]}, p[1], p[0], p[5], p[6], s.mapAt(p[7], p[8]));
                int a = (j * n + i) * 4, b = ((j + n) * n + i) * 4;
                double e = Math.max(Math.max(Math.abs(fb.get(a) - c.wet()), Math.abs(fb.get(a + 1) - c.rust())),
                        Math.max(Math.abs(fb.get(a + 2) - c.algae()), Math.abs(fb.get(a + 3) - c.salt())));
                e = Math.max(e, Math.max(Math.abs(fb.get(b) - c.r()), Math.max(Math.abs(fb.get(b + 1) - c.g()), Math.abs(fb.get(b + 2) - c.b()))));
                e = Math.max(e, Math.abs(fb.get(b + 3) - c.rough()));
                err[j * n + i] = e;
                worst = Math.max(worst, e);
            }
        Arrays.sort(err);
        int over = 0;
        for (double e : err) if (e > 0.01) over++;
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Verwitterung Median ± %.3f, größte %.3f, %d von %d über 0,01",
                err[err.length / 2], worst, over, err.length);
    }

    public void dispose(GL4 gl) {
        if (mapTex != 0) gl.glDeleteTextures(1, new int[]{mapTex}, 0);
        if (checkTex != 0) gl.glDeleteTextures(1, new int[]{checkTex}, 0);
        if (checkFbo != 0) gl.glDeleteFramebuffers(1, new int[]{checkFbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        mapTex = checkTex = checkFbo = vao = checkProg = 0;
        mapW = mapH = 0;
        mapVersion = -1;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    static String read(String file) {
        try (InputStream in = WeatheringGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("weathering: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("weathering: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt Vertex- und Fragment-Shader (fertiger Text mit #version) und bindet sie (auch für Demos). */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "check.vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "check.frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("weathering: Prüfprogramm lässt sich nicht binden");
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
            throw new IllegalStateException("weathering: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
