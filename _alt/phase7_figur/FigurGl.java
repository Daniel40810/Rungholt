package com.dan.rungholt.gl;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase 7: die Figur des Wattwanderers, aus Zylindern und einer Kugel (Gummistiefel, dunkle Hose, rote Regenjacke,
 * Mütze) [G]. Beine und Arme schwingen im Shader nach der Schrittphase. Die Figuren bekommen mit der Wattwanderung
 * (Phase 9) ein eigenes Package; bis dahin zeichnet die App diese eine selbst.
 */
public final class FigurGl {
    static final int FLOATS = 11;
    private final Shader shader = new Shader("Figur", null, "figur.vert", "figur.frag");
    private int vao, vbo, count;
    private boolean ready;

    public boolean ready() { return ready; }

    public void init(GL4 gl) {
        shader.build(gl);
        float[] v = mesh();
        count = v.length / FLOATS;
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(1, t, 0);
        vbo = t[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int st = FLOATS * 4;
        int[][] a = {{0, 3, 0}, {1, 3, 12}, {2, 3, 24}, {3, 2, 36}};
        for (int[] x : a) { gl.glEnableVertexAttribArray(x[0]); gl.glVertexAttribPointer(x[0], x[1], GL.GL_FLOAT, false, st, x[2]); }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
    }

    /** Eine Figur zeichnen: Fuß bei (x, y, z), Richtung (Azimut, rad), Schrittphase; Licht wie die Seehunde. */
    public void draw(GL4 gl, float[] vp, double[] eye, double x, double y, double z, double heading, double phase, com.dan.seal.gl.SealGl.Light l) {
        if (!ready) return;
        shader.use(gl);
        shader.mat(gl, "uVP", vp);
        shader.set(gl, "uFig", (float) x, (float) y, (float) z, (float) heading);
        shader.set(gl, "uPhase", (float) phase);
        shader.set(gl, "uCam", eye);
        shader.set(gl, "uSunDir", l.sunDir);
        shader.set(gl, "uSunCol", l.sunColor);
        shader.set(gl, "uMoonDir", l.moonDir);
        shader.set(gl, "uMoonCol", l.moonColor);
        shader.set(gl, "uSkyTop", l.skyTop);
        shader.set(gl, "uSkyHor", l.skyHorizon);
        shader.set(gl, "uFogDist", (float) l.fogDistance);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, count);
        gl.glBindVertexArray(0);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        shader.dispose(gl);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(1, new int[]{vbo}, 0);
        ready = false;
    }

    /** Die Programme der Figur (für die Prüfung). */
    public static String[] files() { return new String[]{"figur.vert", "figur.frag"}; }

    // ------------------------------------------------------------------ Netz

    static final float[] JACKE = {0.42f, 0.045f, 0.035f}, HOSE = {0.045f, 0.05f, 0.065f}, STIEFEL = {0.035f, 0.075f, 0.04f},
            HAUT = {0.50f, 0.36f, 0.28f}, MUETZE = {0.06f, 0.10f, 0.20f};

    /** Die Figur, 1,78 m [G]. */
    static float[] mesh() {
        List<float[]> out = new ArrayList<>();
        // Beine: Hose und Stiefel, Drehpunkt Hüfte 0,93 m
        for (int s = -1; s <= 1; s += 2) {
            int part = s < 0 ? 1 : 2;
            cyl(out, 0.095 * s, 0.38, 0.93, 0.0, 0.075, 0.068, HOSE, part, 0.93);
            cyl(out, 0.095 * s, 0.0, 0.40, 0.02, 0.064, 0.062, STIEFEL, part, 0.93);
            cyl(out, 0.095 * s, 0.0, 0.07, 0.07, 0.060, 0.055, STIEFEL, part, 0.93);   // Schuhspitze
        }
        // Rumpf (Jacke bis über die Hüfte)
        cyl(out, 0, 0.82, 1.47, 0, 0.175, 0.16, JACKE, 0, 0);
        // Arme, Drehpunkt Schulter 1,42 m
        for (int s = -1; s <= 1; s += 2) {
            cyl(out, 0.215 * s, 0.86, 1.43, 0, 0.055, 0.05, JACKE, s < 0 ? 3 : 4, 1.42);
            cyl(out, 0.215 * s, 0.80, 0.87, 0, 0.042, 0.04, HAUT, s < 0 ? 3 : 4, 1.42);
        }
        // Hals, Kopf, Mütze
        cyl(out, 0, 1.47, 1.55, 0, 0.055, 0.055, HAUT, 5, 0);
        sphere(out, 0, 1.64, 0.01, 0.105, HAUT);
        sphere(out, 0, 1.69, 0.0, 0.098, MUETZE);
        float[] r = new float[out.size() * FLOATS];
        for (int i = 0; i < out.size(); i++) System.arraycopy(out.get(i), 0, r, i * FLOATS, FLOATS);
        return r;
    }

    /** Zylinder mit Deckeln, senkrecht von y0 bis y1, um (x, z), Radien unten/oben (seitlich etwas flacher). */
    static void cyl(List<float[]> o, double x, double y0, double y1, double z, double r0, double r1, float[] col, int part, double pivot) {
        int n = 10;
        for (int k = 0; k < n; k++) {
            double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
            double[][] p = {{Math.cos(a0), Math.sin(a0)}, {Math.cos(a1), Math.sin(a1)}};
            double[] b0 = {x + p[0][0] * r0, y0, z + p[0][1] * r0 * 0.75}, b1 = {x + p[1][0] * r0, y0, z + p[1][1] * r0 * 0.75};
            double[] t0 = {x + p[0][0] * r1, y1, z + p[0][1] * r1 * 0.75}, t1 = {x + p[1][0] * r1, y1, z + p[1][1] * r1 * 0.75};
            double[] n0 = {p[0][0], 0, p[0][1]}, n1 = {p[1][0], 0, p[1][1]};
            v(o, b0, n0, col, part, pivot); v(o, b1, n1, col, part, pivot); v(o, t1, n1, col, part, pivot);
            v(o, b0, n0, col, part, pivot); v(o, t1, n1, col, part, pivot); v(o, t0, n0, col, part, pivot);
            v(o, new double[]{x, y1, z}, new double[]{0, 1, 0}, col, part, pivot); v(o, t0, new double[]{0, 1, 0}, col, part, pivot); v(o, t1, new double[]{0, 1, 0}, col, part, pivot);
            v(o, new double[]{x, y0, z}, new double[]{0, -1, 0}, col, part, pivot); v(o, b1, new double[]{0, -1, 0}, col, part, pivot); v(o, b0, new double[]{0, -1, 0}, col, part, pivot);
        }
    }

    static void sphere(List<float[]> o, double x, double y, double z, double r, float[] col) {
        int nu = 10, nv = 6;
        for (int j = 0; j < nv; j++)
            for (int i = 0; i < nu; i++) {
                double[][] q = new double[4][];
                int[][] c = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int k = 0; k < 4; k++) {
                    double th = Math.PI * c[k][1] / nv, ph = 2 * Math.PI * c[k][0] / nu;
                    q[k] = new double[]{Math.sin(th) * Math.cos(ph), Math.cos(th), Math.sin(th) * Math.sin(ph)};
                }
                int[] idx = {0, 1, 2, 0, 2, 3};
                for (int k : idx) v(o, new double[]{x + q[k][0] * r, y + q[k][1] * r, z + q[k][2] * r}, q[k], col, 5, 0);
            }
    }

    static void v(List<float[]> o, double[] p, double[] n, float[] c, int part, double pivot) {
        o.add(new float[]{(float) p[0], (float) p[1], (float) p[2], (float) n[0], (float) n[1], (float) n[2], c[0], c[1], c[2], part, (float) pivot});
    }
}
