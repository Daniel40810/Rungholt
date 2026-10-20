package com.dan.flock.gl;

import com.dan.flock.Flock;
import com.dan.flock.BirdMesh;
import com.dan.flock.GullMesh;
import com.dan.flock.Species;
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
 * Die Möwen auf der Grafikkarte: ein Netz ({@link GullMesh}), je Möwe eine Instanz mit Ort, Richtung, Querneigung und
 * Flügelstellung. Der Vertex-Shader biegt die Flügel. Aufruf nach dem Meer, ins gebundene Ziel mit Tiefentest.
 */
public final class FlockGl {
    /** Ansicht: Projektion·Blick, Auge, Tiefenvergleich, Möwe, auf der das Auge reitet (−1 = keine). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        public int hide = -1;
    }

    /** Licht, vorbelichtet: Sonne und Mond als Bestrahlung, Himmel als Strahldichte, Dunst. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
    }

    static final int INST = 13;
    private int prog, checkProg, vao, vbo, ivbo, emptyVao, count, capacity;
    /** Seit Haithabu Phase 13: ein Netz je Art und Spielart (Index wie {@link #meshIndex}); das der Möwe ist vao. */
    private final java.util.List<int[]> meshes = new java.util.ArrayList<>();     // vao, vbo, count
    private final java.util.List<double[]> joints = new java.util.ArrayList<>();  // Schulter x, y, z, Handgelenk x
    private final java.util.Map<Integer, Integer> meshOf = new java.util.HashMap<>();
    private boolean ready;
    private String info = "";
    private int drawn;

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int drawn() { return drawn; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("gull.vert"), "#version 430 core\n" + read("gull.frag"));
        checkProg = program(gl, "#version 430 core\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n",
                "#version 430 core\n" + read("check.frag"));
        GullMesh m = GullMesh.build();
        float[] v = m.vertices();
        count = m.count();
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        vao = t[0];
        emptyVao = t[1];
        gl.glGenBuffers(2, t, 0);
        vbo = t[0];
        ivbo = t[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int stride = GullMesh.FLOATS * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, stride, 24);
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, stride, 36);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        capacity = 64;
        gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        int is = INST * 4;
        gl.glEnableVertexAttribArray(4);
        gl.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, is, 0);
        gl.glVertexAttribDivisor(4, 1);
        gl.glEnableVertexAttribArray(5);
        gl.glVertexAttribPointer(5, 4, GL.GL_FLOAT, false, is, 16);
        gl.glVertexAttribDivisor(5, 1);
        gl.glEnableVertexAttribArray(6);
        gl.glVertexAttribPointer(6, 4, GL.GL_FLOAT, false, is, 32);
        gl.glVertexAttribDivisor(6, 1);
        gl.glEnableVertexAttribArray(7);
        gl.glVertexAttribPointer(7, 1, GL.GL_FLOAT, false, is, 48);
        gl.glVertexAttribDivisor(7, 1);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        meshes.add(new int[]{vao, vbo, count});
        joints.add(new double[]{GullMesh.SHOULDER_X, GullMesh.SHOULDER_Y, GullMesh.SHOULDER_Z, GullMesh.WRIST_X});
        meshOf.put(key(Species.MOEWE, 0), 0);
        int tris = count / 3;
        for (Species sp : Species.values()) {
            for (int k = sp == Species.MOEWE ? 1 : 0; k < BirdMesh.variants(sp); k++) {   // Möwe 0 ist das GullMesh oben
                BirdMesh bm = BirdMesh.build(sp, k);
                meshOf.put(key(sp, k), meshes.size());
                meshes.add(upload(gl, bm.vertices(), bm.count()));
                joints.add(new double[]{bm.shoulder[0], bm.shoulder[1], bm.shoulder[2], bm.wrist});
                tris += bm.count() / 3;
            }
        }
        ready = true;
        info = String.format(Locale.GERMAN, "%d Netze (%d Arten), %d Dreiecke, je Vogel eine Instanz", meshes.size(), Species.values().length, tris);
    }

    static int key(Species sp, int variant) { return sp.ordinal() * 4 + variant; }

    /** Netz eines Vogels: Art und Spielart (bei der Stockente nach der Nummer: Erpel und Weibchen). */
    int meshIndex(Flock.Gull g) {
        int v = BirdMesh.variantOf(g);
        Integer i = meshOf.get(key(g.species, v));
        return i == null ? 0 : i;
    }

    /** Ein Netz mit derselben Belegung wie das der Möwe; die Instanzen kommen aus demselben Puffer. */
    private int[] upload(GL4 gl, float[] v, int n) {
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        int a = t[0];
        gl.glGenBuffers(1, t, 0);
        int b = t[0];
        gl.glBindVertexArray(a);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int stride = GullMesh.FLOATS * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, stride, 24);
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, stride, 36);
        bindInstances(gl);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return new int[]{a, b, n};
    }

    private void bindInstances(GL4 gl) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        int is = INST * 4;
        gl.glEnableVertexAttribArray(4);
        gl.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, is, 0);
        gl.glVertexAttribDivisor(4, 1);
        gl.glEnableVertexAttribArray(5);
        gl.glVertexAttribPointer(5, 4, GL.GL_FLOAT, false, is, 16);
        gl.glVertexAttribDivisor(5, 1);
        gl.glEnableVertexAttribArray(6);
        gl.glVertexAttribPointer(6, 4, GL.GL_FLOAT, false, is, 32);
        gl.glVertexAttribDivisor(6, 1);
        gl.glEnableVertexAttribArray(7);
        gl.glVertexAttribPointer(7, 1, GL.GL_FLOAT, false, is, 48);
        gl.glVertexAttribDivisor(7, 1);
    }

    /** Instanzdaten eines Vogels (13 Zahlen, auch für Vorschau und Prüfung). */
    public static float[] instance(Flock.Gull g) {
        float[] d = new float[INST];
        put(d, 0, g);
        return d;
    }

    /** Instanzdaten einer Möwe: Ort, Größe, Richtung, Querneigung, Flügel, Beine. */
    static void put(float[] d, int o, Flock.Gull g) {
        double vx = g.vel[0], vy = g.vel[1], vz = g.vel[2], l = Math.sqrt(vx * vx + vy * vy + vz * vz);
        double fx, fy, fz;
        if (g.state == Flock.State.SWIM || g.state == Flock.State.WALK) {
            // schwimmend oder gehend: waagerecht; Gründeln und Picken neigen den Kopf nach unten
            double a = Math.toRadians(g.facing), dip = g.dabble * (g.state == Flock.State.SWIM ? 1.15 : 0.6);
            fx = Math.sin(a) * Math.cos(dip); fy = -Math.sin(dip); fz = -Math.cos(a) * Math.cos(dip);
        } else if (g.state == Flock.State.PERCH || l < 0.5) {
            double a = Math.toRadians(g.facing);
            // der Kormoran sitzt aufrecht
            double up = g.species == com.dan.flock.Species.KORMORAN ? 0.7 : 0;
            fx = Math.sin(a) * Math.cos(up); fy = Math.sin(up); fz = -Math.cos(a) * Math.cos(up);
        } else if (g.state == Flock.State.SOAR) {
            // im Aufwind schaut sie in den Wind, auch wenn sie kaum vorankommt (Richtung aus der Geschwindigkeit gegen die Luft genähert)
            double a = Math.toRadians(g.facing);
            fx = Math.sin(a); fy = 0.05; fz = -Math.cos(a);
        } else { fx = vx / l; fy = Math.max(-0.5, Math.min(0.5, vy / l)); fz = vz / l; }
        double[] w = g.species == com.dan.flock.Species.MOEWE ? GullMesh.wing(g) : BirdMesh.wing(g);
        boolean legs = g.state == Flock.State.PERCH || g.state == Flock.State.WALK;
        float[] v = {(float) g.pos[0], (float) g.pos[1], (float) g.pos[2], 1f, (float) fx, (float) fy, (float) fz, (float) g.bank,
                (float) w[0], (float) w[1], (float) w[2], (float) w[3], legs ? 1f : 0f};
        System.arraycopy(v, 0, d, o, INST);
    }

    /** Alle sichtbaren Vögel zeichnen (nach Netz geordnet, ein Aufruf je Netz). */
    public void draw(GL4 gl, Flock f, View view, Light light) {
        List<Flock.Gull> gs = f.gulls();
        float[] d = new float[Math.max(1, gs.size()) * INST];
        int nm = meshes.size();
        int[] per = new int[nm], start = new int[nm];
        java.util.List<Flock.Gull> vis = new java.util.ArrayList<>();
        for (Flock.Gull g : gs) {
            if (g.state == Flock.State.AWAY || g.state == Flock.State.DIVE || g.id == view.hide) continue;
            double dx = g.pos[0] - view.eye[0], dz = g.pos[2] - view.eye[2];
            if (dx * dx + dz * dz > 4000.0 * 4000.0) continue;
            vis.add(g);
            per[meshIndex(g)]++;
        }
        for (int i = 1; i < nm; i++) start[i] = start[i - 1] + per[i - 1];
        int[] fill = start.clone();
        for (Flock.Gull g : vis) put(d, fill[meshIndex(g)]++ * INST, g);
        int n = vis.size();
        drawn = n;
        if (n == 0) return;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo);
        if (n > capacity) {
            capacity = n * 2;
            gl.glBufferData(GL.GL_ARRAY_BUFFER, capacity * INST * 4L, null, GL.GL_DYNAMIC_DRAW);
        }
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, n * INST * 4L, Buffers.newDirectFloatBuffer(d, 0, n * INST));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, view.viewProj, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uShoulder"), (float) GullMesh.SHOULDER_X, (float) GullMesh.SHOULDER_Y, (float) GullMesh.SHOULDER_Z);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWrist"), (float) GullMesh.WRIST_X);
        set3(gl, "uCam", view.eye);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunCol", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonCol", light.moonColor);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHor", light.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) light.fogDistance);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        int sh = gl.glGetUniformLocation(prog, "uShoulder"), wr = gl.glGetUniformLocation(prog, "uWrist");
        for (int i = 0; i < nm; i++) {
            if (per[i] == 0) continue;
            double[] j = joints.get(i);
            gl.glUniform3f(sh, (float) j[0], (float) j[1], (float) j[2]);
            gl.glUniform1f(wr, (float) j[3]);
            int[] m = meshes.get(i);
            gl.glBindVertexArray(m[0]);
            gl.glDrawArraysInstancedBaseInstance(GL.GL_TRIANGLES, 0, m[2], per[i], start[i]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    /** Flügelspitze (rechts) in Körperkoordinaten wie im Shader, auf dem Prozessor. */
    public static double[] tipCpu(double[] w) {
        double[] sh = {GullMesh.SHOULDER_X, GullMesh.SHOULDER_Y, GullMesh.SHOULDER_Z}, wr = {GullMesh.WRIST_X, sh[1], -0.07}, tip = {0.70, sh[1], 0.06};
        double[] a = sub(wr, sh), b = sub(tip, wr);
        double[] ra = rotY(-w[2], rotZ(w[0], a)), rb = rotY(-w[2], rotZ(w[0], rotY(-w[3], rotZ(w[1], b))));
        return new double[]{sh[0] + ra[0] + rb[0], sh[1] + ra[1] + rb[1], sh[2] + ra[2] + rb[2]};
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static double[] rotZ(double a, double[] p) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c * p[0] - s * p[1], s * p[0] + c * p[1], p[2]}; }
    static double[] rotY(double a, double[] p) { double c = Math.cos(a), s = Math.sin(a); return new double[]{c * p[0] + s * p[2], p[1], -s * p[0] + c * p[2]}; }

    /**
     * Flügelstellungen auf der Grafikkarte gegen den Prozessor: für bis zu 64 Möwen die rechte Flügelspitze.
     * „Grafikkarte = Prozessor: Flügelspitze ± 0,00 mm (Median), größte 0,01 mm, 16 Möwen“
     */
    public String selfCheck(GL4 gl, Flock f) {
        List<Flock.Gull> gs = f.gulls();
        int n = Math.min(64, gs.size());
        if (n == 0) return "";
        float[] w = new float[64 * 4];
        double[][] want = new double[n][];
        for (int i = 0; i < n; i++) {
            double[] ww = GullMesh.wing(gs.get(i));
            for (int k = 0; k < 4; k++) w[i * 4 + k] = (float) ww[k];
            want[i] = tipCpu(ww);
        }
        int[] v = new int[2];
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, 1);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        gl.glUniform4fv(gl.glGetUniformLocation(checkProg, "uI[0]"), 64, w, 0);
        gl.glUniform3f(gl.glGetUniformLocation(checkProg, "uShoulder"), (float) GullMesh.SHOULDER_X, (float) GullMesh.SHOULDER_Y, (float) GullMesh.SHOULDER_Z);
        gl.glUniform1f(gl.glGetUniformLocation(checkProg, "uWrist"), (float) GullMesh.WRIST_X);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 4);
        gl.glReadPixels(0, 0, 64, 1, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        double[] e = new double[n];
        for (int i = 0; i < n; i++) {
            double dx = fb.get(i * 4) - want[i][0], dy = fb.get(i * 4 + 1) - want[i][1], dz = fb.get(i * 4 + 2) - want[i][2];
            e[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        java.util.Arrays.sort(e);
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Flügelspitze ± %.2f mm (Median), größte %.2f mm, %d Möwen", e[n / 2] * 1000, e[n - 1] * 1000, n);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        for (int i = 1; i < meshes.size(); i++) {
            gl.glDeleteBuffers(1, new int[]{meshes.get(i)[1]}, 0);
            gl.glDeleteVertexArrays(1, new int[]{meshes.get(i)[0]}, 0);
        }
        meshes.clear();
        joints.clear();
        meshOf.clear();
        gl.glDeleteBuffers(2, new int[]{vbo, ivbo}, 0);
        gl.glDeleteVertexArrays(2, new int[]{vao, emptyVao}, 0);
        prog = checkProg = vbo = ivbo = vao = emptyVao = 0;
        ready = false;
    }

    static String read(String file) {
        try (InputStream in = FlockGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("flock: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("flock: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für die Demo. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("flock: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
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
            throw new IllegalStateException("flock: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
