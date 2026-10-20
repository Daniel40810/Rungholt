package com.dan.rungholt.gl;

import com.dan.rungholt.scene.Suedfall;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.List;

/**
 * Phase 9: stille Teiche (Fething und Pütten auf Südfall) als ebene Wasserflächen mit ausgefranstem Rand: Himmel nach
 * Fresnel, dunkles, moorbraunes Wasser, Kräuselung im Wind; im Frost Eis (grauweiß, matt), darauf Schnee. Das Gelände
 * schneidet die Fläche am Ufer.
 */
public final class TeichGl {
    private final Shader shader = new Shader("Teich", null, "teich.vert", "teich.frag");
    private int vao, vbo, count;
    private boolean ready;

    public boolean ready() { return ready; }

    public void init(GL4 gl, List<Suedfall.Pond> ponds) {
        shader.build(gl);
        int seg = 24;
        float[] v = new float[ponds.size() * seg * 3 * 6];
        int k = 0;
        for (Suedfall.Pond p : ponds) {
            double h = Math.toRadians(p.headingDeg()), ax = Math.sin(h), az = -Math.cos(h), bx = Math.cos(h), bz = Math.sin(h);
            for (int i = 0; i < seg; i++) {
                for (int c = 0; c < 3; c++) {
                    double lx = 0, lz = 0, edge = 0;
                    if (c > 0) {
                        double t = 2 * Math.PI * (i + c - 1) / seg, ct = Math.cos(t), st = Math.sin(t);
                        // Rechteck mit runden Ecken (Superellipse)
                        lx = Math.signum(ct) * Math.pow(Math.abs(ct), 0.35) * p.hx();
                        lz = Math.signum(st) * Math.pow(Math.abs(st), 0.35) * p.hz();
                        edge = 1;
                    }
                    v[k++] = (float) (p.x() + ax * lx + bx * lz); v[k++] = (float) p.level(); v[k++] = (float) (p.z() + az * lx + bz * lz);
                    v[k++] = (float) lx; v[k++] = (float) lz; v[k++] = (float) edge;
                }
            }
        }
        count = k / 6;
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        gl.glGenBuffers(1, t, 0);
        vbo = t[0];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, k * 4L, Buffers.newDirectFloatBuffer(v, 0, k), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glBindVertexArray(0);
        ready = true;
    }

    /** Zeichnen: Licht wie die Tiere; ice 0..1 (Eis), snow (m) auf dem Eis, Wind (m/s), Zeit (s). */
    public void draw(GL4 gl, float[] vp, double[] eye, com.dan.seal.gl.SealGl.Light l, double ice, double snow, double wind, double t) {
        if (!ready) return;
        shader.use(gl);
        shader.mat(gl, "uVP", vp);
        shader.set(gl, "uCam", eye);
        shader.set(gl, "uSunDir", l.sunDir);
        shader.set(gl, "uSunCol", l.sunColor);
        shader.set(gl, "uSkyTop", l.skyTop);
        shader.set(gl, "uSkyHor", l.skyHorizon);
        shader.set(gl, "uFogDist", (float) l.fogDistance);
        shader.set(gl, "uIce", (float) ice, (float) snow, (float) wind, (float) (t % 3600));
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
}
