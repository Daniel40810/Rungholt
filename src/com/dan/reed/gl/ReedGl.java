package com.dan.reed.gl;

import com.dan.reed.Reed;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Zeichnet die Halme aus {@link Reed} mit JOGL (OpenGL 4.3): je Halm eine Instanz, die Form entsteht im
 * Vertex-Shader aus der Nummer der Ecke (kein Eckpuffer). Nah hat jede Instanz fünf Blätter zu vier Abschnitten, fern
 * zwei Blätter zu zwei Abschnitten; zum Rand der Reichweite werden die Halme schmal, damit nichts aufpoppt. Die
 * Biegung im Wind rechnet der Shader mit der Formel aus {@link Reed#sway}. Die Halme um die Kamera werden in einem
 * eigenen Faden neu verteilt, wenn sie sich mehr als 6 m bewegt hat; das Bild wartet nie darauf.
 * <p>
 * Shader: {@code reed.vert}, {@code reed.frag} als Ressourcen in diesem Package.
 */
public final class ReedGl {
    /** Blick: Projektion·Blick, Auge, Tiefenkonvention. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
    }

    /**
     * Licht in den Einheiten des Benutzers (linear, HDR): Sonne, Himmel von oben (mit Gewicht), Dunst; seit Haithabu
     * Phase 3 dazu der Mond und eine Schattenkarte von oben (r = Durchlässigkeit für Sonne und Mond, etwa die Wolken),
     * die jeder Halm an seinem Ort liest. Das Package weiß nicht, wer den Schatten wirft.
     */
    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.4, 0.6}, fogColor = {0.6, 0.65, 0.7};
        public double[] moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double fogDistance = 25_000;
        /** Gewicht des Himmelslichts (0,45 passt zu einem Himmel, dessen Farbe die Helligkeit des Zenits ist). */
        public double skyWeight = 0.45;
        /** Schattenkarte (0 = keine) und ihr Rechteck: x, z der Ecke, Kantenlänge in m. */
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 1};
    }

    static final int NEAR_BLADES = 5, NEAR_SEGS = 4, FAR_BLADES = 2, FAR_SEGS = 2;
    private int program, vao, vboNear, vboFar, countNear, countFar;
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Schilf-Verteilung");
        t.setDaemon(true);
        return t;
    });
    private Future<float[][]> pending;
    private double builtX = Double.NaN, builtZ = Double.NaN;
    private long builtKey;
    private volatile String info = "";
    /** Nah gilt bis zu dieser Entfernung (m). */
    public double nearDistance = 45;

    public String info() { return info; }

    /** Baut die Halme beim nächsten {@link #update} neu, auch ohne Bewegung (etwa wenn sich die Dichte am Ort geändert hat). */
    public void invalidate() { builtKey = Long.MIN_VALUE; }
    public int instances() { return countNear + countFar; }

    public void init(GL4 gl) {
        program = link(gl, "reed.vert", "reed.frag");
        int[] t = new int[1];
        gl.glGenVertexArrays(1, t, 0);
        vao = t[0];
        int[] b = new int[2];
        gl.glGenBuffers(2, b, 0);
        vboNear = b[0];
        vboFar = b[1];
    }

    /**
     * Verteilt neu, wenn die Kamera sich bewegt hat oder seed, Dichte, Qualität sich geändert haben, und lädt fertige
     * Verteilungen hoch. Die Dichte- und Bodenabfragen laufen im Hintergrund.
     */
    public void update(GL4 gl, Reed r, double camX, double camZ, Reed.Density d, Reed.Ground g) {
        if (pending != null && pending.isDone()) {
            try {
                float[][] res = pending.get();
                upload(gl, vboNear, res[0]);
                upload(gl, vboFar, res[1]);
                countNear = res[0].length / Reed.STRIDE;
                countFar = res[1].length / Reed.STRIDE;
                info = String.format(Locale.GERMAN, "%,d Halme nah, %,d fern", countNear, countFar);
            } catch (Exception e) {
                System.err.println("reed: " + e);
            }
            pending = null;
        }
        long key = r.seed * 31 + Double.hashCode(r.density) * 17 + r.quality.ordinal();
        boolean moved = Double.isNaN(builtX) || Math.hypot(camX - builtX, camZ - builtZ) > 6;
        if (pending == null && (moved || key != builtKey)) {
            builtX = camX;
            builtZ = camZ;
            builtKey = key;
            final double nd = nearDistance;
            pending = builder.submit(() -> split(r, camX, camZ, d, g, nd));
        }
    }

    /** Alle Arten um (cx, cz), aufgeteilt in nah und fern. */
    static float[][] split(Reed r, double cx, double cz, Reed.Density d, Reed.Ground g, double near) {
        float[] nearA = new float[0], farA = new float[0];
        int nn = 0, nf = 0;
        for (Reed.Kind k : Reed.Kind.values()) {
            float[] a = r.around(cx, cz, r.range(k), k, d, g);
            double kn = k == Reed.Kind.SCHILF ? near * 1.4 : near;
            for (int i = 0; i < a.length; i += Reed.STRIDE) {
                double dx = a[i] - cx, dz = a[i + 2] - cz, dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > r.range(k)) continue;
                boolean isNear = dist < kn;
                if (isNear) {
                    if (nn + Reed.STRIDE > nearA.length) nearA = java.util.Arrays.copyOf(nearA, Math.max(1024, nearA.length * 2));
                    System.arraycopy(a, i, nearA, nn, Reed.STRIDE);
                    nn += Reed.STRIDE;
                } else {
                    if (nf + Reed.STRIDE > farA.length) farA = java.util.Arrays.copyOf(farA, Math.max(1024, farA.length * 2));
                    System.arraycopy(a, i, farA, nf, Reed.STRIDE);
                    nf += Reed.STRIDE;
                }
            }
        }
        return new float[][]{java.util.Arrays.copyOf(nearA, nn), java.util.Arrays.copyOf(farA, nf)};
    }

    private static void upload(GL4 gl, int vbo, float[] data) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(Math.max(1, data.length));
        fb.put(data).flip();
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, data.length * 4L), fb, GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Zeichnet in den gebundenen Bildspeicher (Tiefentest an, beide Seiten). t: Laufzeit in s. */
    public void draw(GL4 gl, Reed r, View v, Light l, double t) {
        if (countNear + countFar == 0) return;
        gl.glUseProgram(program);
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        gl.glUniform1f(loc(gl, "uTime"), (float) (t % 3600));
        double[] to = r.windTo();
        gl.glUniform2f(loc(gl, "uWindTo"), (float) to[0], (float) to[1]);
        gl.glUniform3f(loc(gl, "uWind"), (float) r.windSpeed, (float) r.gust, (float) r.wetness);
        int nk = Reed.Kind.values().length;
        float[] stiff = new float[nk], range = new float[nk], col = new float[nk * 3];
        for (Reed.Kind k : Reed.Kind.values()) {
            stiff[k.ordinal()] = (float) k.stiffness;
            range[k.ordinal()] = (float) r.range(k);
            double[] c = r.color(k, 0.5);
            col[k.ordinal() * 3] = (float) c[0];
            col[k.ordinal() * 3 + 1] = (float) c[1];
            col[k.ordinal() * 3 + 2] = (float) c[2];
        }
        gl.glUniform1fv(loc(gl, "uStiff[0]"), nk, stiff, 0);
        gl.glUniform1fv(loc(gl, "uRange[0]"), nk, range, 0);
        gl.glUniform3fv(loc(gl, "uColor[0]"), nk, col, 0);
        gl.glUniform1f(loc(gl, "uSeason"), (float) r.season);
        gl.glUniform2f(loc(gl, "uSnowFrost"), (float) Math.max(0, r.snowDepth), (float) Math.max(0, Math.min(1, r.frost)));
        gl.glUniform3f(loc(gl, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(loc(gl, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(loc(gl, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform3f(loc(gl, "uMoonDir"), (float) l.moonDir[0], (float) l.moonDir[1], (float) l.moonDir[2]);
        gl.glUniform3f(loc(gl, "uMoonColor"), (float) l.moonColor[0], (float) l.moonColor[1], (float) l.moonColor[2]);
        gl.glUniform1f(loc(gl, "uSkyWeight"), (float) l.skyWeight);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, "uShadow"), 5);
        gl.glUniform4f(loc(gl, "uShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1], (float) Math.max(1, l.shadowRect[2]), l.shadowTexture != 0 ? 1f : 0f);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        drawSet(gl, vboNear, countNear, NEAR_BLADES, NEAR_SEGS);
        drawSet(gl, vboFar, countFar, FAR_BLADES, FAR_SEGS);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void drawSet(GL4 gl, int vbo, int count, int blades, int segs) {
        if (count == 0) return;
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, Reed.STRIDE * 4, 0);
        gl.glVertexAttribDivisor(0, 1);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 4, GL.GL_FLOAT, false, Reed.STRIDE * 4, 16);
        gl.glVertexAttribDivisor(1, 1);
        gl.glUniform1i(loc(gl, "uBlades"), blades);
        gl.glUniform1i(loc(gl, "uSegs"), segs);
        gl.glDrawArraysInstanced(GL.GL_TRIANGLES, 0, blades * segs * 6, count);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private int loc(GL4 gl, String name) { return gl.glGetUniformLocation(program, name); }

    public void dispose(GL4 gl) {
        if (program != 0) gl.glDeleteProgram(program);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (vboNear != 0) gl.glDeleteBuffers(2, new int[]{vboNear, vboFar}, 0);
        program = vao = vboNear = vboFar = 0;
        builder.shutdownNow();
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package. */
    public static String source(String file) {
        try (InputStream in = ReedGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("reed: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("reed: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) throw new IllegalStateException("reed: Programm lässt sich nicht binden: " + programLog(gl, p));
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("reed: " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return sh;
    }

    private static String programLog(GL4 gl, int p) {
        int[] len = new int[1];
        gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
        byte[] b = new byte[Math.max(1, len[0])];
        gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
        return new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8);
    }
}
