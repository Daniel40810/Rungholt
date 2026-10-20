package com.dan.weathering.demo;

import com.dan.weathering.Weathering;
import com.dan.weathering.gl.WeatheringGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Demo-Szene ohne Leuchtturm: ein Stahlpfahl (unten schwarz und rostanfällig, darüber weiß und rot) mit
 * Plattenstößen und einem Kragen, daneben ein Steinblock, dazu Wasser mit Wellen. Die Gezeit läuft auf Wunsch im
 * Zeitraffer; die Verwitterung folgt Wasserstand, Seegang, Regen und Sonne.
 */
final class WeatheringScene implements GLEventListener {
    static final double PILE_R = 1.6, PILE_TOP = 9, PILE_BOTTOM = -4, SEAM = 1.2;

    final Weathering w;
    final WeatheringGl wgl = new WeatheringGl();
    double yaw = 215, pitch = 7, dist = 24, targetY = 2.6, fovy = 50;
    double sunAlt = 35, sunAz = 200, time;
    /** Zeitraffer: Szenensekunden je Sekunde; Gezeit von Hand (tideAuto aus) oder als halbtägige Welle. */
    volatile double lapse = 600;
    volatile boolean tideAuto = true, frozen;
    volatile int show;
    volatile String status = "", selfCheck = "";
    private int prog, vao, vbo, ibo, count, waterFirst, waterCount;
    private long last;

    WeatheringScene(Weathering w) {
        this.w = w;
        w.mapWidthM = 2 * Math.PI * PILE_R;
        w.mapHeightM = PILE_TOP - PILE_BOTTOM;
        w.mapBottom = PILE_BOTTOM;
    }

    /** Wasserstand der Gezeit-Welle (Hub 3 m, 12 h 25 min). */
    static double tide(double t) { return 1.5 * Math.cos(2 * Math.PI * t / 44_712.0); }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        prog = WeatheringGl.program(gl, "#version 430 core\n" + read("scene.vert"),
                "#version 430 core\n" + WeatheringGl.includeSource() + "\n" + read("scene.frag"));
        wgl.init(gl);
        build(gl);
        last = System.nanoTime();
    }

    void build(GL4 gl) {
        List<float[]> v = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int seg = 64;
        // Pfahl: Mantel in Ringen (für glatte Wasserlinie), Kragen auf 4 m, Deckel
        int rings = 52;
        for (int r = 0; r <= rings; r++) {
            double y = PILE_BOTTOM + (PILE_TOP - PILE_BOTTOM) * r / rings;
            for (int s = 0; s <= seg; s++) {
                double a = 2 * Math.PI * s / seg;
                v.add(new float[]{(float) (PILE_R * Math.cos(a)), (float) y, (float) (-PILE_R * Math.sin(a)), (float) Math.cos(a), 0, (float) -Math.sin(a),
                        (float) (a * PILE_R), (float) y, (float) (y < 4 ? 4 : PILE_TOP), (float) SEAM, 0});
            }
        }
        for (int r = 0; r < rings; r++)
            for (int s = 0; s < seg; s++) {
                int a0 = r * (seg + 1) + s, a1 = a0 + 1, b0 = a0 + seg + 1, b1 = b0 + 1;
                idx.add(a0); idx.add(a1); idx.add(b1); idx.add(a0); idx.add(b1); idx.add(b0);
            }
        // Kragen: Ring 0,25 m breit auf 4 m (Kante, unter der Rost herunterläuft)
        ring(v, idx, seg, PILE_R, PILE_R + 0.25, 4.0, 4.15);
        // Deckel
        int c = v.size();
        v.add(new float[]{0, (float) PILE_TOP, 0, 0, 1, 0, 0, 0, (float) PILE_TOP, 0, 0});
        for (int s = 0; s <= seg; s++) {
            double a = 2 * Math.PI * s / seg;
            v.add(new float[]{(float) (PILE_R * Math.cos(a)), (float) PILE_TOP, (float) (-PILE_R * Math.sin(a)), 0, 1, 0,
                    (float) (PILE_R * Math.cos(a)), (float) (PILE_R * Math.sin(a)), (float) PILE_TOP, 0, 0});
        }
        for (int s = 0; s < seg; s++) { idx.add(c); idx.add(c + 1 + s); idx.add(c + 2 + s); }
        // Steinblock (Mole) 5 × 3 × 1,6 m, Oberkante 1,2 m
        box(v, idx, 3.2, -3.5, -2.2, 8.2, 1.2, 1.0);
        // Wasser: Gitter 80 × 80 m
        waterFirst = idx.size();
        int g = 120, w0 = v.size();
        for (int j = 0; j <= g; j++)
            for (int i = 0; i <= g; i++) {
                float x = -40 + 80f * i / g, z = -40 + 80f * j / g;
                v.add(new float[]{x, 0, z, 0, 1, 0, x, z, 0, 0, 2});
            }
        for (int j = 0; j < g; j++)
            for (int i = 0; i < g; i++) {
                int a0 = w0 + j * (g + 1) + i, a1 = a0 + 1, b0 = a0 + g + 1, b1 = b0 + 1;
                idx.add(a0); idx.add(b0); idx.add(b1); idx.add(a0); idx.add(b1); idx.add(a1);
            }
        waterCount = idx.size() - waterFirst;
        float[] fv = new float[v.size() * 11];
        for (int i = 0; i < v.size(); i++) System.arraycopy(v.get(i), 0, fv, i * 11, 11);
        int[] fi = idx.stream().mapToInt(Integer::intValue).toArray();
        int[] b = new int[2];
        gl.glGenVertexArrays(1, b, 0);
        vao = b[0];
        gl.glGenBuffers(2, b, 0);
        vbo = b[0];
        ibo = b[1];
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, fv.length * 4L, Buffers.newDirectFloatBuffer(fv), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, fi.length * 4L, Buffers.newDirectIntBuffer(fi), GL.GL_STATIC_DRAW);
        int[] sizes = {3, 3, 2, 2, 1};
        int off = 0;
        for (int a = 0; a < sizes.length; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, sizes[a], GL.GL_FLOAT, false, 44, off * 4L);
            off += sizes[a];
        }
        gl.glBindVertexArray(0);
        count = fi.length;
    }

    private static void ring(List<float[]> v, List<Integer> idx, int seg, double r0, double r1, double y0, double y1) {
        // Unterseite (Normale nach unten), Außenkante, Oberseite
        double[][] faces = {{r0, y0, r1, y0, 0, -1}, {r1, y0, r1, y1, 1, 0}, {r1, y1, r0, y1, 0, 1}};
        for (double[] f : faces) {
            int base = v.size();
            for (int k = 0; k < 2; k++) {
                double r = k == 0 ? f[0] : f[2], y = k == 0 ? f[1] : f[3];
                for (int s = 0; s <= seg; s++) {
                    double a = 2 * Math.PI * s / seg;
                    double nx = f[4] * Math.cos(a), nz = -f[4] * Math.sin(a);
                    v.add(new float[]{(float) (r * Math.cos(a)), (float) y, (float) (-r * Math.sin(a)), (float) nx, (float) f[5], (float) nz,
                            (float) (a * r1), (float) y, (float) y1, 0, 0});
                }
            }
            for (int s = 0; s < seg; s++) {
                int a0 = base + s, a1 = a0 + 1, b0 = a0 + seg + 1, b1 = b0 + 1;
                idx.add(a0); idx.add(a1); idx.add(b1); idx.add(a0); idx.add(b1); idx.add(b0);
            }
        }
    }

    private static void box(List<float[]> v, List<Integer> idx, double x0, double z0, double y0, double x1, double y1, double z1) {
        double[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (double[] f : n) {
            double[][] q;
            if (f[0] != 0) {
                double x = f[0] > 0 ? x1 : x0;
                q = f[0] > 0 ? new double[][]{{x, y0, z1}, {x, y0, z0}, {x, y1, z0}, {x, y1, z1}} : new double[][]{{x, y0, z0}, {x, y0, z1}, {x, y1, z1}, {x, y1, z0}};
            } else if (f[1] != 0) {
                q = new double[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            } else {
                double z = f[2] > 0 ? z1 : z0;
                q = f[2] > 0 ? new double[][]{{x0, y0, z}, {x1, y0, z}, {x1, y1, z}, {x0, y1, z}} : new double[][]{{x1, y0, z}, {x0, y0, z}, {x0, y1, z}, {x1, y1, z}};
            }
            int base = v.size();
            for (double[] p : q) {
                double s = f[1] != 0 ? p[0] : f[0] != 0 ? p[2] : p[0];
                double t = f[1] != 0 ? p[2] : p[1];
                v.add(new float[]{(float) p[0], (float) p[1], (float) p[2], (float) f[0], (float) f[1], (float) f[2], (float) s + 40, (float) t, (float) y1, 0, 1});
            }
            idx.add(base); idx.add(base + 1); idx.add(base + 2); idx.add(base); idx.add(base + 2); idx.add(base + 3);
        }
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int width, int height) { }

    /** Einen Schritt weiter (dt in echten Sekunden). */
    void step(double dt) {
        double sdt = dt * lapse;
        time += sdt;
        if (tideAuto) w.waterLevel = tide(time);
        w.sun = Math.max(0, Math.sin(Math.toRadians(sunAlt)));
        w.step(sdt);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) step(dt);
        Weathering.Snapshot s = w.snapshot();
        wgl.update(gl, s);
        int W = d.getSurfaceWidth(), H = d.getSurfaceHeight();
        gl.glViewport(0, 0, W, H);
        gl.glClearColor(0.62f, 0.70f, 0.80f, 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        double cy = Math.cos(Math.toRadians(pitch));
        double[] eye = {Math.sin(Math.toRadians(yaw)) * cy * dist, targetY + Math.sin(Math.toRadians(pitch)) * dist, -Math.cos(Math.toRadians(yaw)) * cy * dist};
        // Kamera schaut aus Richtung yaw auf den Pfahl (yaw = woher man schaut, von Nord)
        float[] vp = mul(perspective(fovy, W / (double) Math.max(1, H), 0.2, 500), lookAt(eye, new double[]{0, targetY, 0}));
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        double alt = Math.toRadians(sunAlt), az = Math.toRadians(sunAz);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunDir"), (float) (Math.cos(alt) * Math.sin(az)), (float) Math.sin(alt), (float) (-Math.cos(alt) * Math.cos(az)));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uSunGain"), (float) Math.max(0.05, Math.sin(alt) * 1.4));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uLevel"), (float) s.level());
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uTime"), (float) ((System.nanoTime() / 1e9) % 1000));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uHs"), (float) Math.min(1.6, w.waveHeight));
        gl.glUniform2f(gl.glGetUniformLocation(prog, "uMapScale"), (float) w.mapWidthM, (float) w.mapHeightM);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uMapBottom"), (float) w.mapBottom);
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uShow"), show);
        wgl.bind(gl, prog, s, 1);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, waterFirst, GL.GL_UNSIGNED_INT, 0);
        if (show == 0) {
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDrawElements(GL.GL_TRIANGLES, waterCount, GL.GL_UNSIGNED_INT, waterFirst * 4L);
            gl.glDisable(GL.GL_BLEND);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        status = s.describe() + String.format(Locale.GERMAN, " · trocknet %.2f m/h · %s", w.dryingRate(), wgl.info());
    }

    /** Selbsttest (im GL-Faden). */
    String check(GLAutoDrawable d) {
        selfCheck = wgl.selfCheck(d.getGL().getGL4(), w.snapshot());
        return selfCheck;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        wgl.dispose(gl);
        if (prog != 0) gl.glDeleteProgram(prog);
        if (vao != 0) {
            gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
            gl.glDeleteBuffers(2, new int[]{vbo, ibo}, 0);
        }
    }

    // ------------------------------------------------------------------ Matrizen (spaltenweise wie OpenGL)

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect);
        m[5] = (float) t;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAt(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = fy * 0 - fz * 1, sy = fz * 0 - fx * 0, sz = fx * 1 - fy * 0, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    static String read(String f) {
        try (InputStream in = WeatheringScene.class.getResourceAsStream(f)) {
            if (in == null) throw new IllegalStateException("weathering demo: " + f + " fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
