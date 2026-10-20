package com.dan.spray.gl;

import com.dan.spray.Body;
import com.dan.spray.Parcel;
import com.dan.spray.Spray;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Gischt auf der Grafikkarte. Je Bild werden die lebenden Bündel in einen Speicherpuffer geladen (96 Byte je
 * Bündel, nach Art sortiert); gezeichnet wird ohne Eckpuffer, eine Instanz je Tropfen. Lage und Bewegung jedes Tropfens
 * rechnet der Vertex-Shader aus seinem Bündel, genau wie {@link Parcel#particle} auf dem Prozessor.
 * <p>
 * Aufruf nach Meer und Szene, ins gebundene HDR-Ziel. Für weiche Ränder kann die App eine Kopie der Szenentiefe geben
 * ({@link View#depthTexture}); ohne Kopie wird nur gegen den Tiefenpuffer geprüft.
 */
public final class SprayGl {
    /** Ansicht: Projektion·Blick, Auge, Blickrichtung, Tiefenkonvention, Bildgröße, Szenentiefe (optional). */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3], forward = {0, 0, -1};
        public boolean reversedZ;
        public double near = 0.2, far = 60_000;
        public int width = 1, height = 1, depthTexture;
        /** Belichtungszeit für Streifen in s (1/60 wirkt natürlich). */
        public double shutter = 1 / 60.0;
        /** Öffnungswinkel senkrecht (Grad), für die Größe der Bälle im Bild. */
        public double fovyDeg = 55;
        /**
         * Zufällige Ausdünnung großer Nebel- und Weißwasserbälle (seit Phase 13): Bälle über so vielen Bildpunkten
         * werden mit Wahrscheinlichkeit (lodPixels / Durchmesser)² gezeichnet, mit entsprechend mehr Deckkraft
         * (Nebel höchstens auf ein Achtel, Weißwasser auf die Hälfte ausgedünnt). Nah am Turm bei Sturm deckten sonst Tausende Bälle das Bild viele Male. 0 = aus.
         */
        public double lodPixels = 110;
    }

    /** Licht, vorbelichtet: Sonne und Mond (Richtung, Farbe), Himmel oben und am Horizont, Dunstlänge. */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {1, 1, 1}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyTop = {0.2, 0.3, 0.5}, skyHorizon = {0.5, 0.6, 0.7};
        public double fogDistance = 40_000;
    }

    static final int FLOATS = 24;
    private int prog, checkProg, ssbo, vao, capacity;
    private final int[] first = new int[4], count = new int[4];
    private Spray.Quality quality = Spray.Quality.MEDIUM;
    private List<Body> bodies = new ArrayList<>();
    private boolean ready;
    private String info = "";
    private long drawn;

    public boolean ready() { return ready; }
    public String info() { return info; }
    /** Tropfen und Nebelbälle im letzten Bild (Obergrenze; tote werden im Shader verworfen). */
    public long drawn() { return drawn; }

    public void init(GL4 gl) {
        String common = read("spray_common.glsl");
        prog = program(gl, "#version 430 core\n" + common + "\n" + strip(read("spray.vert")), "#version 430 core\n" + read("spray.frag"));
        checkProg = program(gl, "#version 430 core\n" + read("check.vert"), "#version 430 core\n" + common + "\n" + read("check.frag"));
        int[] v = new int[1];
        gl.glGenBuffers(1, v, 0);
        ssbo = v[0];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
        info = "Tropfen aus Bündeln im Vertex-Shader";
    }

    /** Lädt die Bündel hoch (Alter relativ zur Zeit des Kerns), nach Art sortiert. */
    public void update(GL4 gl, Spray spray) {
        update(gl, spray.parcels(), spray.time(), spray.quality, spray.bodies());
    }

    public void update(GL4 gl, List<Parcel> list, double time, Spray.Quality q, List<Body> bodyList) {
        quality = q;
        bodies = bodyList;
        int n = list.size();
        float[] data = new float[Math.max(1, n) * FLOATS];
        int o = 0;
        for (int kind = 0; kind < 4; kind++) {
            first[kind] = o / FLOATS;
            for (Parcel p : list) {
                if (p.kind() != kind) continue;
                put(data, o, p, time);
                o += FLOATS;
            }
            count[kind] = o / FLOATS - first[kind];
        }
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        if (data.length > capacity) {
            capacity = Math.max(data.length, 256 * FLOATS);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, capacity * 4L, null, GL4.GL_DYNAMIC_DRAW);
        }
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, data.length * 4L, Buffers.newDirectFloatBuffer(data));
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        info = String.format(Locale.GERMAN, "%d Schwälle × %d, %d Nebel × %d, %d Fahnen × %d, %d Weißwasser × %d Tropfen", count[0], q.splash, count[1], q.mist, count[2], q.drift, count[3], q.wash);
    }

    static void put(float[] d, int o, Parcel p, double time) {
        float[] v = {(float) p.x(), (float) p.y(), (float) p.z(), (float) (time - p.t0()),
                (float) p.vx(), (float) p.vy(), (float) p.vz(), (float) p.life(),
                (float) p.k(), (float) p.spreadPos(), (float) p.spreadVel(), (float) p.grav(),
                Float.intBitsToFloat(p.seed()), (float) p.size(), (float) p.waterY(), (float) p.mass(),
                (float) p.tx(), (float) p.tz(), (float) p.nx(), (float) p.nz(),
                (float) p.wx(), (float) p.wz(), (float) p.burst(), p.kind()};
        System.arraycopy(v, 0, d, o, FLOATS);
    }

    /** Zeichnet Nebel, dann Spritzwasser und Fahnen, ins gebundene Ziel (Tiefentest an, Tiefe schreiben aus). */
    public void draw(GL4 gl, View view, Light light, double strength) {
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, view.viewProj, 0);
        set3(gl, "uEye", view.eye);
        set3(gl, "uFwd", view.forward);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uShutter"), (float) view.shutter);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uPixScale"), (float) (view.height / (2 * Math.tan(Math.toRadians(view.fovyDeg) / 2))));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uLodPix"), (float) view.lodPixels);
        float[] ba = new float[32], br = new float[16];
        int nb = Math.min(8, bodies.size());
        for (int i = 0; i < nb; i++) {
            Body b = bodies.get(i);
            ba[i * 4] = (float) b.x(); ba[i * 4 + 1] = (float) b.z(); ba[i * 4 + 2] = (float) b.y0(); ba[i * 4 + 3] = (float) b.y1();
            br[i * 2] = (float) b.r0(); br[i * 2 + 1] = (float) b.r1();
        }
        gl.glUniform4fv(gl.glGetUniformLocation(prog, "uBodyA[0]"), 8, ba, 0);
        gl.glUniform2fv(gl.glGetUniformLocation(prog, "uBodyR[0]"), 8, br, 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uBodies"), nb);
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunColor", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonColor", light.moonColor);
        set3(gl, "uSkyTop", light.skyTop);
        set3(gl, "uSkyHorizon", light.skyHorizon);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), (float) light.fogDistance);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uStrength"), (float) Math.min(1.5, Math.max(0, strength)));
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uHasDepth"), view.depthTexture != 0 ? 1 : 0);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uNear"), (float) view.near);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFar"), (float) view.far);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uSceneDepth"), 0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBindVertexArray(vao);
        drawn = 0;
        for (int kind : new int[]{Parcel.MIST, Parcel.WASH, Parcel.SPLASH, Parcel.DRIFT}) {
            if (count[kind] == 0) continue;
            int per = quality.perParcel(kind);
            gl.glUniform1i(gl.glGetUniformLocation(prog, "uFirst"), first[kind]);
            gl.glUniform1i(gl.glGetUniformLocation(prog, "uPer"), per);
            gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, 6, count[kind] * per);
            drawn += (long) count[kind] * per;
        }
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    private void set3(GL4 gl, String name, double[] v) {
        gl.glUniform3f(gl.glGetUniformLocation(prog, name), (float) v[0], (float) v[1], (float) v[2]);
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Rechnet für bis zu 64 Bündel je 64 Tropfen die Lage auf der Grafikkarte, liest sie zurück und vergleicht mit
     * {@link Parcel#particle}. „Grafikkarte = Prozessor: Tropfen ± 0,3 mm (Median), größte 2 mm, 4 096 Proben“
     */
    public String selfCheck(GL4 gl, List<Parcel> list, double time) {
        int nP = Math.min(64, list.size());
        if (nP == 0) return "";
        List<Parcel> sel = new ArrayList<>(list.subList(0, nP));
        float[] data = new float[nP * FLOATS];
        for (int i = 0; i < nP; i++) put(data, i * FLOATS, sel.get(i), time);
        int[] v = new int[3];
        gl.glGenBuffers(1, v, 0);
        int buf = v[0];
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, buf);
        gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, data.length * 4L, Buffers.newDirectFloatBuffer(data), GL4.GL_STATIC_DRAW);
        gl.glGenTextures(1, v, 0);
        int tex = v[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_RGBA32F, 64, nP);
        gl.glGenFramebuffers(1, v, 1);
        int fbo = v[1];
        int[] prev = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        gl.glViewport(0, 0, 64, nP);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(checkProg);
        int step = 7;
        gl.glUniform1i(gl.glGetUniformLocation(checkProg, "uStep"), step);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, buf);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * nP * 4);
        gl.glReadPixels(0, 0, 64, nP, GL4.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        gl.glDeleteBuffers(1, new int[]{buf}, 0);
        List<Double> err = new ArrayList<>();
        int aliveMismatch = 0;
        for (int j = 0; j < nP; j++)
            for (int i = 0; i < 64; i++) {
                double[] c = sel.get(j).particle(i * step, time);
                int o = (j * 64 + i) * 4;
                boolean gAlive = fb.get(o + 3) >= 0;
                if (gAlive != (c[4] > 0)) { aliveMismatch++; continue; }
                if (!gAlive) continue;
                err.add(Math.max(Math.abs(fb.get(o) - c[0]), Math.max(Math.abs(fb.get(o + 1) - c[1]), Math.abs(fb.get(o + 2) - c[2]))));
            }
        if (err.isEmpty()) return "Grafikkarte = Prozessor: keine lebenden Tropfen zum Vergleichen";
        double[] e = err.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Tropfen ± %.1f mm (Median), größte %.1f mm, %d Proben, %d am Rand von lebt/tot",
                e[e.length / 2] * 1000, e[e.length - 1] * 1000, e.length, aliveMismatch);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (checkProg != 0) gl.glDeleteProgram(checkProg);
        if (ssbo != 0) gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        prog = checkProg = ssbo = vao = capacity = 0;
        ready = false;
    }

    // ------------------------------------------------------------------ Shader

    /** Entfernt #include-Zeilen (der gemeinsame Teil wird vorn eingesetzt). */
    static String strip(String s) {
        StringBuilder b = new StringBuilder();
        for (String l : s.split("\r?\n", -1)) b.append(l.trim().startsWith("#include") ? "" : l).append('\n');
        return b.toString();
    }

    static String read(String file) {
        try (InputStream in = SprayGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("spray: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("spray: " + file + ": " + e.getMessage(), e);
        }
    }

    /** Übersetzt und bindet zwei Stufen (fertiger Text mit #version); auch für Demos. */
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
            throw new IllegalStateException("spray: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
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
            throw new IllegalStateException("spray: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
