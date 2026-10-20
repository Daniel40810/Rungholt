package com.dan.flock.gl;

import com.dan.flock.WaderMesh;
import com.dan.flock.WaderSpecies;
import com.dan.flock.Waders;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Seit Rungholt Phase 8: die Vögel des Watts auf der Grafikkarte. Je Art und Spielart (Sommer, Winter) zwei Netze aus
 * {@link WaderMesh}: nah (bis {@link #nearRange} m) fein, fern grob. Je Vogel eine Instanz (14 Werte); {@code wader.vert}
 * biegt Flügel, Beine und Hals, {@code gull.frag} beleuchtet wie die Möwen. Aufruf nach dem Gelände, vor dem Wasser
 * (wer watet, steht mit den Beinen im Wasser); Tiefentest wie die App ihn setzt.
 */
public final class WaderGl {
    /** Ansicht: Projektion·Blick und Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
    }

    /** Licht, vorbelichtet (wie {@link FlockGl.Light}). */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
    }

    static final int INST = 14;
    /** Bis hierher das feine Netz (m); jenseits von {@link #maxRange} wird nichts gezeichnet. */
    public volatile double nearRange = 70, maxRange = 6000;
    private int prog;
    private final int[] counts = new int[WaderSpecies.ALL.size() * 4], vaos = new int[WaderSpecies.ALL.size() * 4], vbos = new int[WaderSpecies.ALL.size() * 4],
            ibos = new int[WaderSpecies.ALL.size() * 4], caps = new int[WaderSpecies.ALL.size() * 4], used = new int[WaderSpecies.ALL.size() * 4];
    private final WaderMesh[] meshes = new WaderMesh[WaderSpecies.ALL.size() * 4];
    private float[][] data = new float[WaderSpecies.ALL.size() * 4][];
    private boolean ready;
    private int drawn, tris;

    public boolean ready() { return ready; }
    public int drawn() { return drawn; }

    /** Index eines Netzes: Art, Spielart (0 Sommer, 1 Winter), fern. */
    static int idx(int species, int variant, boolean far) { return species * 4 + variant * 2 + (far ? 1 : 0); }

    /** Spielart nach dem Monat: Sommerkleid von April bis August. */
    public static int variant(int month) { return month >= 4 && month <= 8 ? 0 : 1; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("wader.vert"), "#version 430 core\n" + read("gull.frag"));
        int n = WaderSpecies.ALL.size() * 4;
        gl.glGenVertexArrays(n, vaos, 0);
        gl.glGenBuffers(n, vbos, 0);
        gl.glGenBuffers(n, ibos, 0);
        int t = 0;
        for (int s = 0; s < WaderSpecies.ALL.size(); s++)
            for (int v = 0; v < 2; v++)
                for (int f = 0; f < 2; f++) {
                    int i = idx(s, v, f == 1);
                    WaderMesh m = WaderMesh.build(WaderSpecies.ALL.get(s), v, f == 1);
                    meshes[i] = m;
                    float[] vv = m.vertices();
                    counts[i] = m.count();
                    t += m.count() / 3;
                    gl.glBindVertexArray(vaos[i]);
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbos[i]);
                    gl.glBufferData(GL.GL_ARRAY_BUFFER, vv.length * 4L, Buffers.newDirectFloatBuffer(vv), GL.GL_STATIC_DRAW);
                    int st = WaderMesh.FLOATS * 4;
                    attrib(gl, 0, 3, st, 0, false);
                    attrib(gl, 1, 3, st, 12, false);
                    attrib(gl, 2, 3, st, 24, false);
                    attrib(gl, 3, 1, st, 36, false);
                    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibos[i]);
                    caps[i] = 64;
                    gl.glBufferData(GL.GL_ARRAY_BUFFER, caps[i] * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
                    attrib(gl, 4, 4, INST * 4, 0, true);
                    attrib(gl, 5, 4, INST * 4, 16, true);
                    attrib(gl, 6, 4, INST * 4, 32, true);
                    attrib(gl, 7, 2, INST * 4, 48, true);
                    data[i] = new float[64 * INST];
                }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        tris = t;
        ready = true;
    }

    private static void attrib(GL4 gl, int loc, int n, int stride, long off, boolean inst) {
        gl.glEnableVertexAttribArray(loc);
        gl.glVertexAttribPointer(loc, n, GL.GL_FLOAT, false, stride, off);
        if (inst) gl.glVertexAttribDivisor(loc, 1);
    }

    /** Die Vögel einsortieren (je Netz) und zeichnen. */
    public void draw(GL4 gl, Waders w, View v, Light l) {
        if (!ready) return;
        java.util.Arrays.fill(used, 0);
        int var = variant(w.month);
        double ex = v.eye[0], ey = v.eye[1], ez = v.eye[2], max2 = maxRange * maxRange, near2 = nearRange * nearRange;
        drawn = 0;
        java.util.List<Waders.Group> all = new java.util.ArrayList<>(w.groups());
        all.addAll(w.skeins());   // seit Rungholt Phase 9: ziehende Keile
        for (Waders.Group g : all) {
            int s = WaderSpecies.ALL.indexOf(g.species);
            double gd = Math.hypot(g.cx - ex, g.cz - ez);
            boolean groupFar = gd > maxRange + 1000 && g.mode != Waders.Mode.FLIEGEN && g.mode != Waders.Mode.SCHWARM;
            if (groupFar) continue;
            for (Waders.Bird b : g.birds) {
                double dx = b.x - ex, dy = b.y - ey, dz = b.z - ez, d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > max2) continue;
                int i = idx(s, var, d2 > near2);
                float[] a = data[i];
                int k = used[i];
                if ((k + 1) * INST > a.length) data[i] = a = java.util.Arrays.copyOf(a, a.length * 2);
                int o = k * INST;
                double fx, fy, fz;
                if (b.air && (Math.abs(b.vx) + Math.abs(b.vz) > 0.3)) {
                    double vl = Math.sqrt(b.vx * b.vx + b.vy * b.vy * 0.3 + b.vz * b.vz);
                    fx = b.vx / vl; fy = b.vy * 0.55 / vl; fz = b.vz / vl;
                } else { fx = Math.sin(b.heading); fy = 0; fz = -Math.cos(b.heading); }
                double[] wg = Waders.wing(b);
                WaderMesh m = meshes[i];
                a[o] = (float) b.x; a[o + 1] = (float) (b.y - m.footY * b.legs - (1 - b.legs) * 0.0); a[o + 2] = (float) b.z; a[o + 3] = 1f;
                a[o + 4] = (float) fx; a[o + 5] = (float) fy; a[o + 6] = (float) fz; a[o + 7] = (float) b.bank;
                a[o + 8] = (float) wg[0]; a[o + 9] = (float) wg[1]; a[o + 10] = (float) wg[2]; a[o + 11] = (float) wg[3];
                a[o + 12] = (float) b.legs; a[o + 13] = (float) b.probe;
                used[i]++;
                drawn++;
            }
        }
        if (drawn == 0) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, v.viewProj, 0);
        set3(gl, "uCam", v.eye);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunCol", l.sunColor);
        set3(gl, "uMoonDir", l.moonDir);
        set3(gl, "uMoonCol", l.moonColor);
        set3(gl, "uSkyTop", l.skyTop);
        set3(gl, "uSkyHor", l.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) l.fogDistance);
        gl.glDisable(GL.GL_CULL_FACE);
        for (int i = 0; i < used.length; i++) {
            if (used[i] == 0) continue;
            WaderMesh m = meshes[i];
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uShoulder"), (float) m.shoulder[0], (float) m.shoulder[1], (float) m.shoulder[2]);
            gl.glUniform1f(gl.glGetUniformLocation(prog, "uWrist"), (float) m.wrist);
            gl.glUniform3f(gl.glGetUniformLocation(prog, "uNeck"), (float) m.neck[0], (float) m.neck[1], (float) m.neck[2]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibos[i]);
            if (used[i] > caps[i]) {
                caps[i] = used[i] * 2;
                gl.glBufferData(GL.GL_ARRAY_BUFFER, caps[i] * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
            }
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, used[i] * INST * 4L, FloatBuffer.wrap(data[i], 0, used[i] * INST));
            gl.glBindVertexArray(vaos[i]);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, counts[i], used[i]);
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(prog, n), (float) v[0], (float) v[1], (float) v[2]); }

    /** „14 Netze, 9 800 Dreiecke · 640 Vögel gezeichnet“ */
    public String info() { return ready ? String.format(Locale.GERMAN, "%d Netze, %,d Dreiecke · %,d Vögel gezeichnet", meshes.length, tris, drawn) : "Wattvögel: nicht gestartet"; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteVertexArrays(vaos.length, vaos, 0);
        gl.glDeleteBuffers(vbos.length, vbos, 0);
        gl.glDeleteBuffers(ibos.length, ibos, 0);
        ready = false;
    }

    static String read(String name) {
        try (InputStream in = WaderGl.class.getResourceAsStream(name)) {
            if (in == null) throw new IllegalStateException(name + " fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int program(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int sh = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{k == 0 ? vs : fs}, null);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Wattvögel: Shader: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) throw new IllegalStateException("Wattvögel: Programm lässt sich nicht binden");
        return p;
    }
}
