package com.dan.figure.gl;

import com.dan.figure.FigureMesh;
import com.dan.figure.Outfit;
import com.dan.figure.Person;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Figuren auf der Grafikkarte: ein Netz nah (12 Ecken je Ring) und eins fern (6), je Person eine Instanz mit zwölf
 * Werten; {@code figure.vert} beugt die Gelenke, {@code figure.frag} beleuchtet. Bis 16 Kleidungen je Aufruf als
 * Farbtafel; mehr Personen mit eigener Kleidung werden in Gruppen zu 16 gezeichnet. Aufruf nach dem Gelände, vor dem
 * Wasser (wer watet, steht mit den Füßen im Wasser).
 */
public final class FigureGl {
    /** Ansicht: Projektion·Blick und Auge. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
    }

    /** Licht, vorbelichtet; sunShadow 0 bedeckt … 1 volle Sonne. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000, sunShadow = 1;
    }

    static final int INST = 12, PAL = 16;
    /** Bis hierher das feine Netz (m); weiter als {@link #maxRange} wird nichts gezeichnet. */
    public volatile double nearRange = 60, maxRange = 4000;
    private int prog;
    private final int[] vaos = new int[2], vbos = new int[2], ibos = new int[2], counts = new int[2], caps = {64, 64};
    private float[][] data = {new float[64 * INST], new float[64 * INST]};
    private final float[] pal = new float[PAL * Outfit.SLOTS * 3];
    private boolean ready;
    private int drawn, tris;

    public boolean ready() { return ready; }
    public int drawn() { return drawn; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("figure.vert"), "#version 430 core\n" + read("figure.frag"));
        gl.glGenVertexArrays(2, vaos, 0);
        gl.glGenBuffers(2, vbos, 0);
        gl.glGenBuffers(2, ibos, 0);
        for (int i = 0; i < 2; i++) {
            FigureMesh m = FigureMesh.build(i == 0 ? 12 : 6);
            counts[i] = m.count();
            if (i == 0) tris = m.count() / 3;
            gl.glBindVertexArray(vaos[i]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbos[i]);
            float[] v = m.vertices();
            gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
            int st = FigureMesh.FLOATS * 4;
            attrib(gl, 0, 3, st, 0, false);
            attrib(gl, 1, 3, st, 12, false);
            attrib(gl, 2, 2, st, 24, false);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibos[i]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, caps[i] * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
            attrib(gl, 3, 4, INST * 4, 0, true);
            attrib(gl, 4, 4, INST * 4, 16, true);
            attrib(gl, 5, 4, INST * 4, 32, true);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        ready = true;
    }

    private static void attrib(GL4 gl, int loc, int n, int stride, long off, boolean inst) {
        gl.glEnableVertexAttribArray(loc);
        gl.glVertexAttribPointer(loc, n, GL.GL_FLOAT, false, stride, off);
        if (inst) gl.glVertexAttribDivisor(loc, 1);
    }

    /** Die Personen zeichnen (in Gruppen zu 16 Kleidungen). */
    public void draw(GL4 gl, List<Person> people, View v, Light l) {
        drawn = 0;
        if (!ready || people.isEmpty()) return;
        List<Person> todo = new ArrayList<>(people);
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
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uShadow"), (float) l.sunShadow);
        gl.glDisable(GL.GL_CULL_FACE);
        double max2 = maxRange * maxRange, near2 = nearRange * nearRange;
        while (!todo.isEmpty()) {
            Map<Outfit, Integer> idx = new IdentityHashMap<>();
            int[] used = new int[2];
            List<Person> later = new ArrayList<>();
            for (Person p : todo) {
                Integer o = idx.get(p.outfit);
                if (o == null) {
                    if (idx.size() >= PAL) { later.add(p); continue; }
                    o = idx.size();
                    idx.put(p.outfit, o);
                    System.arraycopy(p.outfit.rgb, 0, pal, o * Outfit.SLOTS * 3, Outfit.SLOTS * 3);
                }
                double dx = p.x - v.eye[0], dy = p.y - v.eye[1], dz = p.z - v.eye[2], d2 = dx * dx + dy * dy + dz * dz;
                if (d2 > max2) continue;
                int m = d2 > near2 ? 1 : 0;
                float[] a = data[m];
                if ((used[m] + 1) * INST > a.length) data[m] = a = java.util.Arrays.copyOf(a, a.length * 2);
                int k = used[m]++ * INST;
                a[k] = (float) p.x; a[k + 1] = (float) p.y; a[k + 2] = (float) p.z; a[k + 3] = (float) p.heading;
                a[k + 4] = (float) p.phase; a[k + 5] = (float) p.gait; a[k + 6] = (float) p.bend; a[k + 7] = (float) p.point;
                a[k + 8] = (float) p.scale; a[k + 9] = o; a[k + 10] = (float) p.look; a[k + 11] = (p.outfit.hat ? 1 : 0) + (p.outfit.pack ? 2 : 0);
                drawn++;
            }
            gl.glUniform3fv(gl.glGetUniformLocation(prog, "uPal"), PAL * Outfit.SLOTS, pal, 0);
            for (int m = 0; m < 2; m++) {
                if (used[m] == 0) continue;
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ibos[m]);
                if (used[m] > caps[m]) { caps[m] = used[m] * 2; gl.glBufferData(GL.GL_ARRAY_BUFFER, caps[m] * INST * 4L, null, GL.GL_DYNAMIC_DRAW); }
                gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, used[m] * INST * 4L, FloatBuffer.wrap(data[m], 0, used[m] * INST));
                gl.glBindVertexArray(vaos[m]);
                gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, counts[m], used[m]);
            }
            todo = later;
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(prog, n), (float) v[0], (float) v[1], (float) v[2]); }

    /** „Figuren: 1 520 Dreiecke je Person · 14 gezeichnet“ */
    public String info() { return ready ? String.format(Locale.GERMAN, "%,d Dreiecke je Person · %d gezeichnet", tris, drawn) : "Figuren: nicht gestartet"; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteVertexArrays(2, vaos, 0);
        gl.glDeleteBuffers(2, vbos, 0);
        gl.glDeleteBuffers(2, ibos, 0);
        ready = false;
    }

    /** Die Programme (für Prüfungen). */
    public static String[] files() { return new String[]{"figure.vert", "figure.frag"}; }

    static String read(String name) {
        try (InputStream in = FigureGl.class.getResourceAsStream(name)) {
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
                throw new IllegalStateException((k == 0 ? "figure.vert: " : "figure.frag: ") + new String(b, 0, len[0], StandardCharsets.UTF_8));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            byte[] b = new byte[4096];
            int[] len = new int[1];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("figure: " + new String(b, 0, len[0], StandardCharsets.UTF_8));
        }
        return p;
    }
}
