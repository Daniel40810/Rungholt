package com.dan.ocean.gl;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

/**
 * Hilfen für Szenen ohne eigenes Himmels-Package (die Demo, spätere kleine Projekte): ein einfaches
 * Himmelspanorama aus Zenit-, Horizont- und Sonnenfarbe, der Himmel als Hintergrund und das Bringen des HDR-Bilds
 * ins Fenster. Die Leuchtturm-App braucht das nicht; sie gibt der See ihr eigenes Panorama.
 */
public final class Backdrop {
    private int program, vao, skyTex;

    public void init(GL4 gl) {
        program = Glsl.program(gl, "Hintergrund", new String[0], "backdrop.vert", "backdrop.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
    }

    /**
     * Rechnet ein Panorama (256 × 128, Azimut von Nord, Höhe −90°…+90°) auf dem Prozessor und lädt es hoch:
     * Zenit- und Horizontfarbe, Sonne (Richtung, Farbe) mit Hof. Gibt die Textur zurück (für {@link OceanGl.Light}).
     */
    public int sky(GL4 gl, double[] zenith, double[] horizon, double[] sunDir, double[] sunColor) {
        int w = 256, h = 128;
        float[] px = new float[w * h * 4];
        for (int y = 0; y < h; y++) {
            double el = ((y + 0.5) / h - 0.5) * Math.PI;
            for (int x = 0; x < w; x++) {
                double az = ((x + 0.5) / w - 0.5) * 2 * Math.PI;
                double dx = Math.sin(az) * Math.cos(el), dy = Math.sin(el), dz = -Math.cos(az) * Math.cos(el);
                double up = Math.max(0, dy), t = Math.pow(up, 0.45);
                double cs = Math.max(0, dx * sunDir[0] + dy * sunDir[1] + dz * sunDir[2]);
                double glow = Math.pow(cs, 24) * 0.25 + Math.pow(cs, 400) * 2.5;
                double below = dy < 0 ? 0.6 : 1;
                int i = (y * w + x) * 4;
                for (int c = 0; c < 3; c++) px[i + c] = (float) ((horizon[c] + (zenith[c] - horizon[c]) * t) * below + sunColor[c] * glow * 0.15);
                px[i + 3] = 1;
            }
        }
        if (skyTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            skyTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, skyTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, skyTex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, GL.GL_RGBA, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(px));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return skyTex;
    }

    /** Zeichnet das Panorama als Hintergrund (vor der See, ohne Tiefe). */
    public void drawSky(GL4 gl, float[] invViewProj, int skyTexture) {
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(program);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uMode"), 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(program, "uInvVP"), 1, false, invViewProj, 0);
        bind(gl, skyTexture);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    /** Bringt ein HDR-Bild mit Belichtung und Filmkurve in den gebundenen Bildspeicher. */
    public void toneMap(GL4 gl, int hdrTexture, float exposure) {
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(program);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uMode"), 1);
        gl.glUniform1f(gl.glGetUniformLocation(program, "uExposure"), exposure);
        bind(gl, hdrTexture);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    private void bind(GL4 gl, int tex) {
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uTex"), 0);
    }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (skyTex != 0) gl.glDeleteTextures(1, new int[]{skyTex}, 0);
        program = vao = skyTex = 0;
    }

    /** Projektion (klassische Tiefe −1…1) für die Demo: Öffnung in Grad, Seitenverhältnis, nah, fern. */
    public static float[] perspective(double fovyDeg, double aspect, double near, double far) {
        double f = 1.0 / Math.tan(Math.toRadians(fovyDeg) / 2);
        float[] m = new float[16];
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + near) / (near - far));
        m[11] = -1;
        m[14] = (float) (2 * far * near / (near - far));
        return m;
    }

    /** Blick von e nach c, oben ist +y. */
    public static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sz = fx, sl = Math.hypot(sx, sz);
        sx /= sl; sz /= sl;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        float[] m = new float[16];
        m[0] = (float) sx; m[4] = 0; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]);
        m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]);
        m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    public static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int row = 0; row < 4; row++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k];
                r[c * 4 + row] = s;
            }
        return r;
    }

    /** Inverse über Gauß-Jordan (für den Sichtstrahl des Hintergrunds). */
    public static float[] invert(float[] m) {
        double[][] a = new double[4][8];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r];
            a[r][4 + r] = 1;
        }
        for (int c = 0; c < 4; c++) {
            int p = c;
            for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c]; a[c] = a[p]; a[p] = t;
            double d = a[c][c];
            if (Math.abs(d) < 1e-20) return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
            for (int k = 0; k < 8; k++) a[c][k] /= d;
            for (int r = 0; r < 4; r++) {
                if (r == c) continue;
                double f = a[r][c];
                for (int k = 0; k < 8; k++) a[r][k] -= f * a[c][k];
            }
        }
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) a[r][4 + c];
        return out;
    }
}
