package com.dan.buoyancy.gl;

import com.dan.buoyancy.Buoy;
import com.dan.buoyancy.Buoyancy;
import com.dan.buoyancy.Floater;
import com.dan.buoyancy.Mesh;
import com.dan.buoyancy.Ship;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * Tonnen und Schiffe auf der Grafikkarte. Die Netze baut der Kern ({@link Mesh}); hier werden sie einmal
 * hochgeladen und je Körper mit seiner Modellmatrix gezeichnet. Tonnen ohne Rückseitenverwerfung (dünne Stäbe),
 * Schiffe mit (wer im Steuerhaus steht, sieht dessen Wände nicht). Für den Blick von der Brücke kommen die Brücke
 * von innen und der Wischer dazu.
 * <p>
 * Aufruf nach dem Meer (das den Rumpf unter Wasser verdeckt), ins gebundene Ziel mit Tiefentest.
 */
public final class BuoyancyGl {
    /** Ansicht: Projektion·Blick, Auge, Tiefenvergleich (umgekehrte Tiefe: GREATER). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        /** Schiff, auf dessen Brücke das Auge steht (Brücke innen und Wischer zeichnen), oder null. */
        public Ship bridge;
    }

    /** Licht, vorbelichtet: Sonne und Mond als Bestrahlung, Himmel als Strahldichte, Dunst, Lampen, Fenster, Anzeigen. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
        /** Leuchtdichte einer brennenden Lampe (vorbelichtet); 0 = aus. */
        public double lamp = 0;
        /** Licht hinter den Fenstern nachts und Helligkeit der Anzeigen auf der Brücke (vorbelichtet). */
        public double windowGlow = 0, screen = 0;
    }

    private static final class Part { int vao, vbo, count; }

    private int prog, checkProg, emptyVao;
    private final Map<Buoy.Kind, Part> buoyParts = new EnumMap<>(Buoy.Kind.class);
    private Part ship, bridge, wiper;
    private boolean ready;
    private String info = "";
    private int drawnBodies, drawnTriangles;

    public boolean ready() { return ready; }
    public String info() { return info; }
    public int drawnBodies() { return drawnBodies; }

    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("body.vert"), "#version 430 core\n" + read("body.frag"));
        checkProg = program(gl, "#version 430 core\n" + read("check.vert"), "#version 430 core\n" + read("check.frag"));
        for (Buoy.Kind k : Buoy.Kind.values()) buoyParts.put(k, upload(gl, Mesh.buoy(k)));
        Ship proto = Ship.coaster("Vorlage");
        ship = upload(gl, Mesh.ship(proto));
        bridge = upload(gl, Mesh.bridgeInterior(proto));
        wiper = upload(gl, Mesh.wiper(proto.wiper));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        ready = true;
        int bt = 0;
        for (Part p : buoyParts.values()) bt = Math.max(bt, p.count / 3);
        info = String.format(Locale.GERMAN, "Kümo %,d Dreiecke, Tonne bis %,d", ship.count / 3, bt);
    }

    private static Part upload(GL4 gl, Mesh m) {
        Part p = new Part();
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        p.vao = t[0];
        gl.glGenBuffers(1, t, 0);
        p.vbo = t[0];
        float[] v = m.vertices();
        gl.glBindVertexArray(p.vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, p.vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int stride = Mesh.FLOATS * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, stride, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, stride, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, stride, 24);
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 1, GL.GL_FLOAT, false, stride, 36);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        p.count = m.count();
        return p;
    }

    /** Alle Tonnen und Schiffe zeichnen (Tiefentest und -schreiben an). */
    public void draw(GL4 gl, Buoyancy b, View view, Light light) {
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, view.viewProj, 0);
        set3(gl, "uCam", view.eye);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunCol", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonCol", light.moonColor);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHor", light.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWindowGlow"), (float) light.windowGlow);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uScreen"), (float) light.screen);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        drawnBodies = drawnTriangles = 0;
        int loc = gl.glGetUniformLocation(prog, "uModel"), lampLoc = gl.glGetUniformLocation(prog, "uLamp");
        gl.glDisable(GL.GL_CULL_FACE);
        double t = b.time();
        boolean on = b.lightsOn();
        for (Buoy by : b.buoys) {
            if (Math.hypot(by.x - view.eye[0], by.z - view.eye[2]) > 12_000) continue;
            gl.glUniform1f(lampLoc, (float) (on ? light.lamp * by.lightLevel(t) : 0));
            drawPart(gl, loc, buoyParts.get(by.kind), by.model());
        }
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        gl.glFrontFace(GL.GL_CCW);
        gl.glUniform1f(lampLoc, (float) (on ? light.lamp : 0));
        for (Ship s : b.ships) drawPart(gl, loc, ship, s.model());
        gl.glDisable(GL.GL_CULL_FACE);
        if (view.bridge != null) {
            drawPart(gl, loc, bridge, view.bridge.model());
            drawPart(gl, loc, wiper, view.bridge.wiperModel());
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private final Map<Mesh, Part> extra = new java.util.IdentityHashMap<>();

    /**
     * Seit Glacier Phase 10: beliebige Netze aus {@link Mesh} mit ihrer Modellmatrix (Spalten zuerst; null: die Welt),
     * etwa der Katamaran ({@link Mesh#catamaran}) und Stege einer App. Jedes Netz wird beim ersten Mal hochgeladen und
     * bleibt, bis {@link #forget} oder {@link #dispose}. Mit Rückseitenverwerfung, wenn {@code cull}.
     */
    public void drawMeshes(GL4 gl, View view, Light light, java.util.List<Object[]> items, boolean cull) {
        if (!ready || items.isEmpty()) return;
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, view.viewProj, 0);
        set3(gl, "uCam", view.eye);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunCol", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonCol", light.moonColor);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHor", light.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWindowGlow"), (float) light.windowGlow);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uScreen"), (float) light.screen);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uLamp"), (float) light.lamp);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(view.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        if (cull) { gl.glEnable(GL.GL_CULL_FACE); gl.glCullFace(GL.GL_BACK); gl.glFrontFace(GL.GL_CCW); } else gl.glDisable(GL.GL_CULL_FACE);
        int loc = gl.glGetUniformLocation(prog, "uModel");
        double[] id = {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        for (Object[] it : items) {
            Mesh m = (Mesh) it[0];
            Part p = extra.get(m);
            if (p == null) { p = upload(gl, m); extra.put(m, p); }
            drawPart(gl, loc, p, it.length > 1 && it[1] != null ? (double[]) it[1] : id);
        }
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Ein Netz von {@link #drawMeshes} wieder freigeben (etwa ein Steg, der neu gebaut wird). */
    public void forget(GL4 gl, Mesh m) { Part p = extra.remove(m); free(gl, p); }

    private void drawPart(GL4 gl, int loc, Part p, double[] model) {
        float[] m = new float[16];
        for (int i = 0; i < 16; i++) m[i] = (float) model[i];
        gl.glUniformMatrix4fv(loc, 1, false, m, 0);
        gl.glBindVertexArray(p.vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, p.count);
        drawnBodies++;
        drawnTriangles += p.count / 3;
    }

    private void set3(GL4 gl, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Lage eines Körpers auf der Grafikkarte gegen den Prozessor: 64 Punkte in Körperkoordinaten mit der Modellmatrix
     * in die Welt, verglichen mit {@link Floater#toWorld}. „Grafikkarte = Prozessor: Lage ± 0,1 mm (Median), größte 0,4 mm, 64 Punkte“
     */
    public String selfCheck(GL4 gl, Floater f) {
        float[] pts = new float[64 * 4];
        Random r = new Random(9);
        double[][] want = new double[64][];
        for (int i = 0; i < 64; i++) {
            double s = (r.nextDouble() - 0.5) * 60, q = (r.nextDouble() - 0.5) * 12, y = (r.nextDouble() - 0.3) * 15;
            pts[i * 4] = (float) q;
            pts[i * 4 + 1] = (float) y;
            pts[i * 4 + 2] = (float) -s;
            want[i] = f.toWorld(s, q, y);
        }
        double[] model = f.model();
        float[] m = new float[16];
        // in Modellnähe rechnen (die Grafikkarte kennt nur float): Verschiebung abziehen und wieder addieren
        for (int i = 0; i < 16; i++) m[i] = (float) model[i];
        m[12] = m[13] = m[14] = 0;
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
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUseProgram(checkProg);
        gl.glUniform4fv(gl.glGetUniformLocation(checkProg, "uP[0]"), 64, pts, 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(checkProg, "uModel"), 1, false, m, 0);
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
        double[] e = new double[64];
        for (int i = 0; i < 64; i++) {
            double dx = fb.get(i * 4) + model[12] - want[i][0], dy = fb.get(i * 4 + 1) + model[13] - want[i][1], dz = fb.get(i * 4 + 2) + model[14] - want[i][2];
            e[i] = Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
        java.util.Arrays.sort(e);
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Lage ± %.2f mm (Median), größte %.2f mm, 64 Punkte", e[32] * 1000, e[63] * 1000);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        for (Part p : buoyParts.values()) free(gl, p);
        free(gl, ship);
        free(gl, bridge);
        free(gl, wiper);
        buoyParts.clear();
        for (Part p : extra.values()) free(gl, p);
        extra.clear();
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        prog = checkProg = emptyVao = 0;
        ship = bridge = wiper = null;
        ready = false;
    }

    private static void free(GL4 gl, Part p) {
        if (p == null) return;
        gl.glDeleteVertexArrays(1, new int[]{p.vao}, 0);
        gl.glDeleteBuffers(1, new int[]{p.vbo}, 0);
    }

    // ------------------------------------------------------------------ Shader

    static String read(String file) {
        try (InputStream in = BuoyancyGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("buoyancy: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("buoyancy: " + file + ": " + e.getMessage(), e);
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
            throw new IllegalStateException("buoyancy: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
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
            throw new IllegalStateException("buoyancy: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
