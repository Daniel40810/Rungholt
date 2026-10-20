package com.dan.hearth.gl;

import com.dan.hearth.Flame;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet Feuer mit JOGL (OpenGL 4.3): Flammenzungen, Glut, Funken und Ruß aus einer Liste von {@link Flame}. Alles
 * sind Tafeln ohne Eckpuffer; der Vertex-Shader rechnet Ort und Alter aus Strahler, Nummer und Zeit. Leuchten
 * additiv, Ruß deckend (eine Mischung: ONE, ONE_MINUS_SRC_ALPHA), Tiefentest an, Tiefe schreiben aus. Leuchtdichten in
 * cd/m² mal {@link Light#lumScale}; so passt das Feuer zu jeder Belichtung.
 */
public final class HearthGl {
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public double near = 0.2, far = 60_000;
        public int depthTexture;
    }

    public static final class Light {
        /** Umrechnung cd/m² → Ausgabe (vorbelichtet); ohne Belichtung etwa 1/3000. */
        public double lumScale = 1 / 3000.0;
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7}, fogColor = {0.6, 0.65, 0.7};
        public double fogDistance = 25_000;
        /** Wind: Richtung, in die er weht (x, z), und Geschwindigkeit (m/s). */
        public double windX = 1, windZ = 0, windSpeed = 2;
    }

    static final int FLOATS = 8, MAX_FLAMES = 4, MAX_SPARKS = 30, MAX_SOOT = 8;
    private int prog, buf, vao, count, sparks, soot;
    private String info = "";

    public String info() { return info; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("hearth.vert"), "#version 430 core\n" + read("hearth.frag"));
        int[] v = new int[1];
        gl.glGenBuffers(1, v, 0);
        buf = v[0];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
    }

    /** Die Strahler dieses Bilds hochladen. */
    public void update(GL4 gl, List<Flame> flames) {
        count = flames.size();
        float[] d = new float[Math.max(1, count) * FLOATS];
        sparks = 0;
        soot = 0;
        for (int i = 0; i < count; i++) {
            Flame f = flames.get(i);
            int o = i * FLOATS;
            d[o] = (float) f.x(); d[o + 1] = (float) f.y(); d[o + 2] = (float) f.z(); d[o + 3] = (float) f.width();
            d[o + 4] = (float) f.height(); d[o + 5] = (float) f.intensity(); d[o + 6] = Float.intBitsToFloat(f.seed()); d[o + 7] = f.kind();
            sparks += f.kind() == Flame.CAMPFIRE ? 16 : f.kind() == Flame.TORCH ? 4 : f.kind() == Flame.FORGE ? 30 : f.kind() == Flame.EMBER ? 3 : 16;
            if (f.kind() == Flame.BLAZE) soot += 8;
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(d);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, d.length * 4L, fb, GL4.GL_DYNAMIC_DRAW);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        info = String.format(Locale.GERMAN, "Feuer %d Strahler · %,d Funken · %d Rußballen", count, sparks, soot);
    }

    /** Zeichnet ins gebundene HDR-Ziel. */
    public void draw(GL4 gl, View view, Light light, double time) {
        if (count == 0) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(loc(gl, "uEye"), (float) view.eye[0], (float) view.eye[1], (float) view.eye[2]);
        gl.glUniform1f(loc(gl, "uTime"), (float) (time % 3600));
        gl.glUniform3f(loc(gl, "uWind"), (float) light.windX, (float) light.windZ, (float) light.windSpeed);
        gl.glUniform1f(loc(gl, "uLumScale"), (float) light.lumScale);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHorizon", light.skyHorizon);
        set3(gl, "uFogColor", light.fogColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1i(loc(gl, "uHasDepth"), view.depthTexture != 0 ? 1 : 0);
        gl.glUniform1i(loc(gl, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1f(loc(gl, "uNear"), (float) view.near);
        gl.glUniform1f(loc(gl, "uFar"), (float) view.far);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(loc(gl, "uSceneDepth"), 0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, buf);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBindVertexArray(vao);
        // Flammen und Glut, dann Funken, zuletzt der Ruß darüber
        int[][] modes = {{0, MAX_FLAMES}, {1, MAX_SPARKS}, {2, MAX_SOOT}};
        for (int[] m : modes) {
            if (m[0] == 2 && soot == 0) continue;
            gl.glUniform1i(loc(gl, "uMode"), m[0]);
            gl.glUniform1i(loc(gl, "uPer"), m[1]);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, count * m[1]);
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        gl.glDeleteBuffers(1, new int[]{buf}, 0);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = 0;
    }

    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(prog, n); }

    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(loc(gl, n), (float) v[0], (float) v[1], (float) v[2]); }

    public static String read(String file) {
        try (InputStream in = HearthGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("hearth: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("hearth: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int[] sh = {shader(gl, GL4.GL_VERTEX_SHADER, vert, "hearth.vert"), shader(gl, GL4.GL_FRAGMENT_SHADER, frag, "hearth.frag")};
        for (int s : sh) gl.glAttachShader(p, s);
        gl.glLinkProgram(p);
        for (int s : sh) gl.glDeleteShader(s);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("hearth: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return p;
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
            throw new IllegalStateException("hearth: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return s;
    }
}
