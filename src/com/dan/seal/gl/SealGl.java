package com.dan.seal.gl;

import com.dan.seal.Colony;
import com.dan.seal.SealBody;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Seehunde auf der Grafikkarte (Phase 7): ein Netz ({@link SealBody#mesh()}), je Tier eine Instanz mit Ort, Länge,
 * Richtung, Rolle, Nässe, Fell und Haltung; {@code seal.vert} biegt den Körper. Davor je Tier an Land ein Schatten auf
 * dem Grund ({@code shadow.vert}, {@code shadow.frag}), der den Grund abdunkelt (Mischung „mal Quelle“).
 * <p>
 * Aufruf nach dem Gelände und vor dem Wasser, ins gebundene Ziel, Tiefentest an (Richtung wie die App), ohne
 * Rückseitenentfernung: Was unter dem Wasser liegt, deckt das Meer zu.
 */
public final class SealGl {
    /** Ansicht: Projektion·Blick, Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
    }

    /** Licht, vorbelichtet: Sonne und Mond als Bestrahlung, Himmel als Strahldichte, Dunst; Stärke des Sonnenschattens. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
        /** 0 bedeckt … 1 volle Sonne (der Schlagschatten). */
        public double sunShadow = 1;
    }

    static final int INST = 16, SHADOW = 5;
    private int prog, shadowProg, vao, vbo, ivbo, svao, sbuf, count, capacity, drawn, shadows;
    private float[] inst = new float[64 * INST], sh = new float[64 * SHADOW];
    private boolean ready;
    private String info = "";
    /** Schatten zeichnen. */
    public volatile boolean shadowsOn = true;

    public boolean ready() { return ready; }
    public int drawn() { return drawn; }

    /** „1 Netz, 1 492 Dreiecke · 24 Tiere, 18 Schatten“ */
    public String info() { return ready ? String.format(Locale.GERMAN, "%s · %d Tiere, %d Schatten", info, drawn, shadows) : "Seehunde: nicht gestartet"; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("seal.vert"), "#version 430 core\n" + read("seal.frag"));
        shadowProg = program(gl, "#version 430 core\n" + read("shadow.vert"), "#version 430 core\n" + read("shadow.frag"));
        float[] v = SealBody.mesh();
        count = v.length / SealBody.FLOATS;
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        vao = t[0];
        svao = t[1];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ivbo = t[1];
        int[] t1 = new int[1];
        gl.glGenBuffers(1, t1, 0);
        sbuf = t1[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int stride = SealBody.FLOATS * 4;
        attrib(gl, 0, 3, stride, 0);
        attrib(gl, 1, 3, stride, 12);
        attrib(gl, 2, 1, stride, 24);
        attrib(gl, 3, 1, stride, 28);
        attrib(gl, 4, 1, stride, 32);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        capacity = 64;
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        for (int k = 0; k < 4; k++) {
            attrib(gl, 5 + k, 4, INST * 4, k * 16);
            gl.glVertexAttribDivisor(5 + k, 1);
        }
        gl.glBindVertexArray(svao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sbuf);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * SHADOW * 4L, null, GL.GL_DYNAMIC_DRAW);
        attrib(gl, 0, 4, SHADOW * 4, 0);
        gl.glVertexAttribDivisor(0, 1);
        attrib(gl, 1, 1, SHADOW * 4, 16);
        gl.glVertexAttribDivisor(1, 1);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
        info = String.format(Locale.GERMAN, "1 Netz, %d Dreiecke", count / 3);
    }

    private static void attrib(GL4 gl, int loc, int n, int stride, long off) {
        gl.glEnableVertexAttribArray(loc);
        gl.glVertexAttribPointer(loc, n, GL.GL_FLOAT, false, stride, off);
    }

    /** Die Instanzen aus der Kolonie übernehmen (jedes Bild, im GL-Faden). */
    public void update(GL4 gl, Colony c) {
        if (!ready) return;
        int need = Math.max(1, c.animals().size());
        if (inst.length < need * INST) { inst = new float[need * INST * 2]; sh = new float[need * SHADOW * 2]; }
        drawn = c.instances(inst);
        shadows = shadowsOn ? c.shadows(sh) : 0;
        if (drawn > capacity) {
            capacity = drawn * 2;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sbuf);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * SHADOW * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        if (drawn > 0) {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, drawn * INST * 4L, FloatBuffer.wrap(inst, 0, drawn * INST));
        }
        if (shadows > 0) {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sbuf);
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, shadows * SHADOW * 4L, FloatBuffer.wrap(sh, 0, shadows * SHADOW));
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Instanzen von Hand (Demo, Werkzeuge): je Tier 16 Werte wie {@link Colony#instances}, je Schatten 5 Werte wie
     * {@link Colony#shadows}.
     */
    public void update(GL4 gl, float[] animals, int n, float[] shade, int ns) {
        if (!ready) return;
        if (n > capacity || ns > capacity) {
            capacity = Math.max(n, ns) * 2;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sbuf);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * SHADOW * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        drawn = n;
        shadows = shadowsOn ? ns : 0;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        if (n > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * INST * 4L, FloatBuffer.wrap(animals, 0, n * INST));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sbuf);
        if (shadows > 0) gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, shadows * SHADOW * 4L, FloatBuffer.wrap(shade, 0, shadows * SHADOW));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Schatten, dann Körper. Tiefentest bleibt, wie die App ihn gesetzt hat; danach ist das Mischen aus. */
    public void draw(GL4 gl, View v, Light l) {
        if (!ready || drawn == 0) return;
        gl.glDisable(GL.GL_CULL_FACE);
        if (shadows > 0) {
            gl.glUseProgram(shadowProg);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(shadowProg, "uVP"), 1, false, v.viewProj, 0);
            set3(gl, shadowProg, "uSunDir", l.sunDir);
            gl.glUniform1f(gl.glGetUniformLocation(shadowProg, "uSunShadow"), (float) Math.max(0, Math.min(1, l.sunShadow)));
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ZERO, GL.GL_SRC_COLOR);
            gl.glDepthMask(false);
            gl.glBindVertexArray(svao);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLE_STRIP, 0, 4, shadows);
            gl.glDepthMask(true);
            gl.glDisable(GL.GL_BLEND);
        }
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, v.viewProj, 0);
        set3(gl, prog, "uCam", v.eye);
        set3(gl, prog, "uSunDir", l.sunDir);
        set3(gl, prog, "uSunCol", l.sunColor);
        set3(gl, prog, "uMoonDir", l.moonDir);
        set3(gl, prog, "uMoonCol", l.moonColor);
        set3(gl, prog, "uSkyTop", l.skyTop);
        set3(gl, prog, "uSkyHor", l.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) l.fogDistance);
        gl.glBindVertexArray(vao);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, count, drawn);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private static void set3(GL4 gl, int p, String n, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) v[0], (float) v[1], (float) v[2]);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteProgram(shadowProg);
        gl.glDeleteVertexArrays(2, new int[]{vao, svao}, 0);
        gl.glDeleteBuffers(3, new int[]{vbo, ivbo, sbuf}, 0);
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    /** Ein Shader aus dem Package als Text. */
    public static String read(String name) {
        try (InputStream in = SealGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException(name + " fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int v = shader(gl, GL4.GL_VERTEX_SHADER, vs), f = shader(gl, GL4.GL_FRAGMENT_SHADER, fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("seal: Programm lässt sich nicht binden: " + log(gl, p, false));
        gl.glDeleteShader(v);
        gl.glDeleteShader(f);
        return p;
    }

    static int shader(GL4 gl, int type, String src) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("seal: Shader lässt sich nicht übersetzen: " + log(gl, s, true));
        return s;
    }

    static String log(GL4 gl, int id, boolean shader) {
        int[] len = new int[1];
        if (shader) gl.glGetShaderiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0); else gl.glGetProgramiv(id, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        if (shader) gl.glGetShaderInfoLog(id, b.length, len, 0, b, 0); else gl.glGetProgramInfoLog(id, b.length, len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }
}
