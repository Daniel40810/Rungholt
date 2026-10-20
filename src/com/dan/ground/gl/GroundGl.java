package com.dan.ground.gl;

import com.dan.ground.Batch;
import com.dan.ground.Meadow;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Zeichnet die Bodendecke aus {@link Meadow} mit JOGL (OpenGL 4.3), aus Kīlauea ({@code GroundCoverRenderer}): die
 * Halme bewegt {@code Meadow} auf der CPU, hier werden sie je Bild als ein Netz hochgeladen (Blöcke statt
 * verschränkter Ecken, damit sie am Stück kopiert werden) und gezeichnet. Solange das Auge steht oder langsam geht,
 * genügt jedes zweite Bild. Dünne Flächen beidseitig mit Durchscheinen, Steine und Erde nur von vorn; zum Rand der
 * Reichweite blendet ein Raster aus (ohne Sortieren).
 * <p>
 * Licht wie {@code com.dan.forest.gl.ForestGl}: Sonne mit Sonnenschattenkarte und Wolkenschatten, Mond, Himmel, Dunst.
 * Shader: {@code ground.vert}, {@code ground.frag} als Ressourcen in diesem Package.
 */
public final class GroundGl {
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        /** Für Demos ohne eigene Nachbearbeitung: Tonemapping und sRGB im Shader. */
        public boolean tonemap;
        public double exposure = 1;
    }

    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, fogColor = {0.6, 0.65, 0.7};
        public double[] moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double fogDistance = 25_000, skyWeight = 1.0;
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 1};
        /**
         * Himmel als Rundumbild (0 = keins, dann gilt fogColor): der Dunst nimmt die Farbe des Himmels am Horizont in
         * Blickrichtung. Abbildung: u = atan(x, −z)/2π + 0,5, v = asin(y)/π + 0,5 (wie com.dan.ocean).
         */
        public int skyTexture;
        public int sunShadowTexture;
        public float[] sunShadowMatrix;
        public double sunShadowBias = 0.0008;
    }

    private int program, vao, vbo, ibo, count;
    private FloatBuffer vb = Buffers.newDirectFloatBuffer(11 * 4096);
    private IntBuffer ib = Buffers.newDirectIntBuffer(3 * 4096);
    private float[] sf = new float[2 * 4096];
    private int tick, triangles;
    private double lastX = Double.NaN, lastZ;
    private long lastNanos;
    private double cpuMs;

    public int triangles() { return triangles; }
    public String info() { return count == 0 ? "Bodendecke: aus" : String.format(Locale.GERMAN, "Bodendecke: %,d Dreiecke, %.1f ms auf dem Prozessor", triangles, cpuMs); }

    public void init(GL4 gl) {
        program = link(gl, "ground.vert", "ground.frag");
        int[] t = new int[2];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ibo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 44 * 64, null, GL4.GL_STREAM_DRAW);
        for (int a = 0; a < 5; a++) gl.glEnableVertexAttribArray(a);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBindVertexArray(0);
    }

    /** Bewegt die Wiese um (x, z) zur Zeit t und lädt sie hoch; off schaltet aus. */
    public void update(GL4 gl, Meadow meadow, float t, double x, double z, boolean off) {
        if (off || vao == 0) {
            count = 0;
            triangles = 0;
            lastX = Double.NaN;
            return;
        }
        tick++;
        boolean moved = Double.isNaN(lastX) || Math.hypot(x - lastX, z - lastZ) > 0.6;
        if (!moved && (tick & 1) == 1 && count > 0) return;
        lastX = x;
        lastZ = z;
        long t0 = System.nanoTime();
        meadow.update(t, x, z);
        Batch b = meadow.batch;
        if (b.nt == 0) { count = 0; triangles = 0; return; }
        int nv = b.nv;
        if (vb.capacity() < nv * 11) vb = Buffers.newDirectFloatBuffer(nv * 11 * 3 / 2);
        if (ib.capacity() < b.nt * 3) ib = Buffers.newDirectIntBuffer(b.nt * 3 * 3 / 2);
        if (sf.length < 2 * nv) sf = new float[2 * nv * 3 / 2];
        for (int i = 0; i < nv; i++) { sf[2 * i] = b.solid[i]; sf[2 * i + 1] = b.fade[i]; }
        vb.clear();
        vb.put(0, b.xyz, 0, 3 * nv).put(3 * nv, b.nrm, 0, 3 * nv).put(6 * nv, b.rgb, 0, 3 * nv).put(9 * nv, sf, 0, 2 * nv);
        vb.limit(11 * nv);
        ib.clear();
        ib.put(0, b.tri, 0, b.nt * 3);
        ib.limit(b.nt * 3);
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) nv * 44, vb, GL4.GL_STREAM_DRAW);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0L);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 12, 12L * nv);
        gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, 12, 24L * nv);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, 8, 36L * nv);
        gl.glVertexAttribPointer(4, 1, GL.GL_FLOAT, false, 8, 36L * nv + 4);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) b.nt * 12, ib, GL4.GL_STREAM_DRAW);
        gl.glBindVertexArray(0);
        count = b.nt * 3;
        triangles = b.nt;
        cpuMs = cpuMs * 0.8 + (System.nanoTime() - t0) / 1e6 * 0.2;
    }

    public void draw(GL4 gl, View v, Light l) {
        if (count == 0) return;
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        set3(gl, "uSunDir", l.sunDir); set3(gl, "uSunColor", l.sunColor); set3(gl, "uSkyColor", l.skyColor); set3(gl, "uFogColor", l.fogColor);
        set3(gl, "uMoonDir", l.moonDir); set3(gl, "uMoonColor", l.moonColor);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, "uSkyWeight"), (float) l.skyWeight);
        gl.glUniform2f(loc(gl, "uTone"), v.tonemap ? 1f : 0f, (float) v.exposure);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.sunShadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, "uShadow"), 5);
        gl.glUniform4f(loc(gl, "uShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1], (float) Math.max(1, l.shadowRect[2]), l.shadowTexture != 0 ? 1f : 0f);
        gl.glUniform1i(loc(gl, "uSunShadow"), 6);
        gl.glUniform1i(loc(gl, "uSky"), 7);
        gl.glUniform1i(loc(gl, "uSkyOn"), l.skyTexture != 0 ? 1 : 0);
        boolean ss = l.sunShadowTexture != 0 && l.sunShadowMatrix != null;
        gl.glUniformMatrix4fv(loc(gl, "uSunShadowM"), 1, false, ss ? l.sunShadowMatrix : new float[16], 0);
        gl.glUniform2f(loc(gl, "uSunShadowP"), ss ? 1f : 0f, (float) l.sunShadowBias);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }
    private int loc(GL4 gl, String n) { return gl.glGetUniformLocation(program, n); }

    public void dispose(GL4 gl) {
        if (vao == 0) return;
        gl.glDeleteProgram(program);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        vao = 0;
    }

    public static String source(String file) {
        try (InputStream in = GroundGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("ground: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("ground: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int[] sh = new int[2];
        String[] files = {vert, frag};
        for (int k = 0; k < 2; k++) {
            sh[k] = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh[k], 1, new String[]{"#version 430 core\n" + source(files[k])}, null, 0);
            gl.glCompileShader(sh[k]);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh[k], GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh[k], b.length, len, 0, b, 0);
                throw new IllegalStateException("ground: " + files[k] + " lässt sich nicht übersetzen:\n" + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh[k]);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(sh[0]);
        gl.glDeleteShader(sh[1]);
        if (ok[0] == 0) {
            byte[] b = new byte[8192];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("ground: Programm lässt sich nicht binden: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return p;
    }
}
