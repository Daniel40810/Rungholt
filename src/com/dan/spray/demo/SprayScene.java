package com.dan.spray.demo;

import com.dan.spray.Body;
import com.dan.spray.Spray;
import com.dan.spray.gl.SprayGl;
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
 * Die Demo-Szene ohne Leuchtturm: eine See aus {@link SineSea} und einer von drei Körpern (Zylinder wie ein Turmsockel,
 * eine Mole aus sieben Stümpfen, ein Fels), dazu die Gischt aus {@code com.dan.spray}. Gerechnet wird in ein eigenes
 * HDR-Bild; die Tiefe wird für weiche Ränder kopiert.
 */
final class SprayScene implements GLEventListener {
    static final String[] SETS = {"Zylinder", "Mole", "Fels"};

    final Spray spray = new Spray();
    SineSea sea;
    final SprayGl sgl = new SprayGl();
    volatile int set = 0, bft = 8;
    volatile double yaw = 30, pitch = 6, dist = 70, targetY = 4, fovy = 50, sunAlt = 25, sunAz = 200, lapse = 1;
    volatile boolean frozen;
    volatile String status = "", selfCheck = "";
    private int prog, post, vao, vbo, ibo, count, emptyVao, fbo, color, depth, copyFbo, copyDepth, w, h, builtSet = -1, builtBft = -1;
    private long last;
    double gpuMs;
    private final int[] query = new int[4];
    private int frame;

    SprayScene() { setup(); }

    /** See und Körper nach set und bft neu. */
    void setup() {
        Spray p = Spray.beaufort(bft);
        spray.windSpeed = p.windSpeed;
        spray.waveHeight = p.waveHeight;
        spray.windFromDeg = 240;
        spray.focusX = 0;
        spray.focusZ = 0;
        spray.focusRadius = 80;
        sea = new SineSea(p.waveHeight, p.windSpeed, 240, 7);
        spray.sea = sea;
        spray.clearBodies();
        for (Body b : bodies(set)) spray.addBody(b);
        spray.clear();
    }

    static List<Body> bodies(int set) {
        List<Body> l = new ArrayList<>();
        switch (set) {
            case 1 -> { for (int i = -3; i <= 3; i++) l.add(Body.cylinder(i * 4.2, i * 1.4, 2.3, -5, 3.5)); }
            case 2 -> { l.add(new Body(0, 0, -5, 6, 8, 1.2)); l.add(new Body(4, 3, -5, 3, 4, 1)); }
            default -> { l.add(new Body(0, 0, -5, 9, 5.2, 4.0)); l.add(new Body(0, 0, 9, 22, 3.8, 3.0)); }
        }
        return l;
    }

    /**
     * Läuft weiter, bis ein großer Schwall gerade steigt (Alter 0,9 bis 1,5 s, Gipfel über minApex), höchstens maxT
     * Sekunden. Für Standbilder, die zeigen sollen, wie hoch es spritzt.
     */
    void advanceToSplash(double minApex, double maxT) {
        double dt = 1 / 30.0;
        for (double s = 0; s < maxT; s += dt) {
            for (com.dan.spray.Parcel p : spray.parcels()) {
                double a = spray.time() - p.t0();
                if (p.kind() == com.dan.spray.Parcel.SPLASH && a > 0.9 && a < 1.5 && Spray.apex(p.vy(), p.k()) > minApex) return;
            }
            sea.t += dt;
            spray.step(dt);
        }
    }

    /** Läuft die Gischt t Sekunden mit festem Schritt (Standbilder). */
    void advance(double t) {
        double dt = 1 / 30.0;
        for (double s = 0; s < t; s += dt) { sea.t += dt; spray.step(dt); }
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        prog = SprayGl.program(gl, "#version 430 core\n" + read("scene.vert"), "#version 430 core\n" + read("scene.frag"));
        post = SprayGl.program(gl, "#version 430 core\n" + read("post.vert"), "#version 430 core\n" + read("post.frag"));
        sgl.init(gl);
        int[] v = new int[3];
        gl.glGenVertexArrays(2, v, 0);
        vao = v[0];
        emptyVao = v[1];
        gl.glGenBuffers(2, v, 0);
        vbo = v[0];
        ibo = v[1];
        gl.glGenQueries(4, query, 0);
        last = System.nanoTime();
    }

    private void build(GL4 gl) {
        List<float[]> vs = new ArrayList<>();
        List<Integer> idx = new ArrayList<>();
        int g = 200;
        double size = 400;
        for (int j = 0; j <= g; j++)
            for (int i = 0; i <= g; i++) {
                // dichter in der Mitte
                double u = (i / (double) g - 0.5) * 2, w2 = (j / (double) g - 0.5) * 2;
                double x = Math.signum(u) * u * u * size / 2, z = Math.signum(w2) * w2 * w2 * size / 2;
                vs.add(new float[]{(float) x, 0, (float) z, 0, 1, 0, 0});
            }
        for (int j = 0; j < g; j++)
            for (int i = 0; i < g; i++) {
                int a = j * (g + 1) + i, b = a + 1, c = a + g + 1, e = c + 1;
                idx.add(a); idx.add(c); idx.add(e); idx.add(a); idx.add(e); idx.add(b);
            }
        int seg = 48;
        for (Body bd : spray.bodies()) {
            int base = vs.size();
            for (int r = 0; r < 2; r++) {
                double y = r == 0 ? bd.y0() : bd.y1(), rr = r == 0 ? bd.r0() : bd.r1();
                double dr = bd.r1() - bd.r0(), dy = bd.y1() - bd.y0(), l = Math.hypot(dr, dy);
                for (int s = 0; s <= seg; s++) {
                    double a = 2 * Math.PI * s / seg, sx = Math.sin(a), sz = -Math.cos(a);
                    vs.add(new float[]{(float) (bd.x() + rr * sx), (float) y, (float) (bd.z() + rr * sz), (float) (dy / l * sx), (float) (-dr / l), (float) (dy / l * sz), 1});
                }
            }
            for (int s = 0; s < seg; s++) {
                int b0 = base + s, b1 = b0 + 1, c0 = b0 + seg + 1, c1 = c0 + 1;
                idx.add(b0); idx.add(c1); idx.add(b1); idx.add(b0); idx.add(c0); idx.add(c1);
            }
            int c = vs.size();
            vs.add(new float[]{(float) bd.x(), (float) bd.y1(), (float) bd.z(), 0, 1, 0, 1});
            for (int s = 0; s <= seg; s++) {
                double a = 2 * Math.PI * s / seg;
                vs.add(new float[]{(float) (bd.x() + bd.r1() * Math.sin(a)), (float) bd.y1(), (float) (bd.z() - bd.r1() * Math.cos(a)), 0, 1, 0, 1});
            }
            for (int s = 0; s < seg; s++) { idx.add(c); idx.add(c + 2 + s); idx.add(c + 1 + s); }
        }
        float[] fv = new float[vs.size() * 7];
        for (int i = 0; i < vs.size(); i++) System.arraycopy(vs.get(i), 0, fv, i * 7, 7);
        int[] fi = idx.stream().mapToInt(Integer::intValue).toArray();
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, fv.length * 4L, Buffers.newDirectFloatBuffer(fv), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, fi.length * 4L, Buffers.newDirectIntBuffer(fi), GL.GL_STATIC_DRAW);
        int[] sz = {3, 3, 1};
        int off = 0;
        for (int a = 0; a < 3; a++) {
            gl.glEnableVertexAttribArray(a);
            gl.glVertexAttribPointer(a, sz[a], GL.GL_FLOAT, false, 28, off * 4L);
            off += sz[a];
        }
        gl.glBindVertexArray(0);
        count = fi.length;
        builtSet = set;
        builtBft = bft;
    }

    private void targets(GL4 gl, int W, int H) {
        if (W == w && H == h) return;
        int[] t = new int[3];
        if (fbo != 0) {
            gl.glDeleteFramebuffers(2, new int[]{fbo, copyFbo}, 0);
            gl.glDeleteTextures(3, new int[]{color, depth, copyDepth}, 0);
        }
        gl.glGenTextures(3, t, 0);
        color = t[0]; depth = t[1]; copyDepth = t[2];
        gl.glBindTexture(GL.GL_TEXTURE_2D, color);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, W, H);
        for (int dt : new int[]{depth, copyDepth}) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, dt);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, W, H);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        }
        gl.glGenFramebuffers(2, t, 0);
        fbo = t[0];
        copyFbo = t[1];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, color, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depth, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, copyFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, copyDepth, 0);
        gl.glDrawBuffer(GL.GL_NONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        w = W;
        h = H;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int width, int height) { }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (builtSet != set || builtBft != bft) { setup(); build(gl); }
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) {
            double sdt = dt * lapse;
            sea.t += sdt;
            spray.step(sdt);
        }
        if (frame > 1) {
            // Zeit der Gischt im vorigen Bild
            int o = ((frame - 1) & 1) * 2;
            long[] r = new long[2];
            gl.glGetQueryObjecti64v(query[o], GL4.GL_QUERY_RESULT, r, 0);
            gl.glGetQueryObjecti64v(query[o + 1], GL4.GL_QUERY_RESULT, r, 1);
            gpuMs = gpuMs * 0.9 + Math.max(0, r[1] - r[0]) / 1e6 * 0.1;
        }
        int W = d.getSurfaceWidth(), H = d.getSurfaceHeight();
        targets(gl, W, H);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, W, H);
        double sky = 0.3 + 0.7 * Math.max(0, Math.sin(Math.toRadians(sunAlt)));
        gl.glClearColor((float) (0.5 * sky), (float) (0.56 * sky), (float) (0.64 * sky), 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        double cp = Math.cos(Math.toRadians(pitch)), a = Math.toRadians(yaw);
        double[] eye = {-Math.sin(a) * cp * dist, targetY + Math.sin(Math.toRadians(pitch)) * dist, Math.cos(a) * cp * dist};
        double[] fwd = {-eye[0], targetY - eye[1], -eye[2]};
        double fl = Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        for (int i = 0; i < 3; i++) fwd[i] /= fl;
        float[] vp = mul(perspective(fovy, W / (double) Math.max(1, H), 0.5, 8000), lookAt(eye, new double[]{0, targetY, 0}));
        double alt = Math.toRadians(sunAlt), az = Math.toRadians(sunAz);
        double[] sunDir = {Math.cos(alt) * Math.sin(az), Math.sin(alt), -Math.cos(alt) * Math.cos(az)};
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, vp, 0);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uT"), (float) sea.t);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uLevel"), (float) sea.level);
        float[] fa = new float[12], fw = new float[12], fp = new float[12], fk = new float[24];
        for (int i = 0; i < 12; i++) { fa[i] = (float) sea.a[i]; fw[i] = (float) sea.w[i]; fp[i] = (float) sea.ph[i]; fk[i * 2] = (float) sea.kx[i]; fk[i * 2 + 1] = (float) sea.kz[i]; }
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uA[0]"), 12, fa, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uW[0]"), 12, fw, 0);
        gl.glUniform1fv(gl.glGetUniformLocation(prog, "uPh[0]"), 12, fp, 0);
        gl.glUniform2fv(gl.glGetUniformLocation(prog, "uK[0]"), 12, fk, 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunDir"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uSun"), (float) Math.max(0.03, Math.sin(alt) * 1.3));
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        // Tiefe kopieren, dann die Gischt
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, W, H, 0, 0, W, H, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glQueryCounter(query[(frame & 1) * 2], GL4.GL_TIMESTAMP);
        sgl.update(gl, spray);
        SprayGl.View v = new SprayGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.forward = fwd;
        v.reversedZ = false;
        v.near = 0.5;
        v.far = 8000;
        v.width = W;
        v.height = H;
        v.depthTexture = copyDepth;
        SprayGl.Light l = new SprayGl.Light();
        double sg = Math.max(0.03, Math.sin(alt) * 1.3);
        l.sunDir = sunDir;
        l.sunColor = new double[]{2.2 * sg, 2.1 * sg, 1.95 * sg};
        l.skyTop = new double[]{0.20 * sky, 0.30 * sky, 0.48 * sky};
        l.skyHorizon = new double[]{0.55 * sky, 0.60 * sky, 0.66 * sky};
        l.fogDistance = 6000;
        sgl.draw(gl, v, l, 1);
        gl.glQueryCounter(query[(frame & 1) * 2 + 1], GL4.GL_TIMESTAMP);
        frame++;
        // ins Fenster
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, W, H);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(post);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, color);
        gl.glUniform1i(gl.glGetUniformLocation(post, "uImg"), 0);
        gl.glUniform1f(gl.glGetUniformLocation(post, "uExposure"), (float) (1.3 / Math.max(0.15, sky)));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        status = String.format(Locale.GERMAN, "%s · Bft %d · H_s %.1f m · %s · %s · Gischt %.2f ms", SETS[set], bft, sea.hs(), spray.describe(), sgl.info(), gpuMs);
    }

    String check(GLAutoDrawable d) {
        selfCheck = sgl.selfCheck(d.getGL().getGL4(), spray.parcels(), spray.time());
        return selfCheck;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        sgl.dispose(gl);
        gl.glDeleteProgram(prog);
        gl.glDeleteProgram(post);
    }

    // ------------------------------------------------------------------ Matrizen

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
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sz * sz);
        sx /= sl; sz /= sl;
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
        try (InputStream in = SprayScene.class.getResourceAsStream(f)) {
            if (in == null) throw new IllegalStateException("spray demo: " + f + " fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
