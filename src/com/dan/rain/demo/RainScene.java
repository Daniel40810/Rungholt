package com.dan.rain.demo;

import com.dan.rain.Rain;
import com.dan.rain.gl.RainGl;
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
 * Die Demo-Szene ohne Leuchtturm: Wasser, drei Pfähle und ein Steg, darüber der Regen aus {@code com.dan.rain}.
 * Entweder Landregen (überall gleich) oder Schauer, die mit dem Wind ziehen ({@link ShowerField}). Gerechnet wird in
 * ein eigenes HDR-Bild; die Tiefe wird für Vorhänge, Krönchen und weiche Ränder kopiert.
 */
final class RainScene implements GLEventListener {
    static final String[] MODES = {"Landregen", "Schauer"};

    final Rain rain = new Rain();
    final RainGl rgl = new RainGl();
    volatile int mode = 1, bft = 5;
    volatile double yaw = 200, pitch = 4, dist = 22, targetY = 1.5, fovy = 55, sunAlt = 20, sunAz = 220, lapse = 1, strength = 1;
    volatile boolean frozen;
    volatile String status = "", selfCheck = "";
    volatile ShowerField showers = new ShowerField(7, 40, 30_000);
    private int prog, post, vao, vbo, ibo, count, emptyVao, fbo, color, depth, copyFbo, copyDepth, w, h;
    private long last;
    double gpuMs, t;
    private final int[] query = new int[4];
    private int frame;
    private boolean built;

    RainScene() { setup(); }

    /** Wind nach bft, Vorhang nach mode. Die Rate bleibt. */
    void setup() {
        Rain p = Rain.beaufort(bft);
        rain.windSpeed = p.windSpeed;
        rain.windFromDeg = 240;
        rain.gust = p.gust;
        rain.cloudBase = 700;
        rain.waterLevel = 0;
        double[] wt = rain.windTo();
        ShowerField s = showers;
        s.windX = wt[0] * 1.6;
        s.windZ = wt[1] * 1.6;
        rain.curtain = mode == 1 ? s : null;
    }

    /** Läuft t Sekunden mit festem Schritt (Standbilder). */
    void advance(double sec) {
        double dt = 1 / 30.0;
        for (double s = 0; s < sec; s += dt) step(dt);
    }

    private void step(double dt) {
        t += dt;
        showers.time = t;
        rain.step(dt);
    }

    double[] eye() {
        double cp = Math.cos(Math.toRadians(pitch)), a = Math.toRadians(yaw);
        return new double[]{-Math.sin(a) * cp * dist, targetY + Math.sin(Math.toRadians(pitch)) * dist, Math.cos(a) * cp * dist};
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        prog = RainGl.program(gl, "#version 430 core\n" + read("scene.vert"), "#version 430 core\n" + read("scene.frag"));
        post = RainGl.program(gl, "#version 430 core\n" + read("post.vert"), "#version 430 core\n" + read("post.frag"));
        rgl.init(gl);
        int[] v = new int[2];
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
        int g = 220;
        double size = 6000;
        for (int j = 0; j <= g; j++)
            for (int i = 0; i <= g; i++) {
                double u = (i / (double) g - 0.5) * 2, w2 = (j / (double) g - 0.5) * 2;
                double x = Math.signum(u) * Math.pow(Math.abs(u), 3) * size / 2, z = Math.signum(w2) * Math.pow(Math.abs(w2), 3) * size / 2;
                vs.add(new float[]{(float) x, 0, (float) z, 0, 1, 0, 0});
            }
        for (int j = 0; j < g; j++)
            for (int i = 0; i < g; i++) {
                int a = j * (g + 1) + i, b = a + 1, c = a + g + 1, e = c + 1;
                idx.add(a); idx.add(c); idx.add(e); idx.add(a); idx.add(e); idx.add(b);
            }
        // drei Pfähle
        double[][] piles = {{-6, 4, 0.5, 5}, {3, -2, 0.45, 4}, {10, 6, 0.6, 6}};
        int seg = 32;
        for (double[] p : piles) {
            int base = vs.size();
            for (int r = 0; r < 2; r++) {
                double y = r == 0 ? -2 : p[3];
                for (int s = 0; s <= seg; s++) {
                    double a = 2 * Math.PI * s / seg, sx = Math.sin(a), sz = -Math.cos(a);
                    vs.add(new float[]{(float) (p[0] + p[2] * sx), (float) y, (float) (p[1] + p[2] * sz), (float) sx, 0, (float) sz, 1});
                }
            }
            for (int s = 0; s < seg; s++) {
                int b0 = base + s, b1 = b0 + 1, c0 = b0 + seg + 1, c1 = c0 + 1;
                idx.add(b0); idx.add(c1); idx.add(b1); idx.add(b0); idx.add(c0); idx.add(c1);
            }
        }
        // Steg: flacher Kasten 12 × 2,4 m, Oberkante 1,2 m
        box(vs, idx, -2, 1.0, 8, 12, 0.4, 2.4);
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
        built = true;
    }

    /** Kasten mit Mitte x, y, z und Maßen sx, sy, sz (sechs Flächen, Normalen nach außen). */
    private static void box(List<float[]> vs, List<Integer> idx, double x, double y, double z, double sx, double sy, double sz) {
        double[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        double[] half = {sx / 2, sy / 2, sz / 2};
        for (double[] f : n) {
            int ax = f[0] != 0 ? 0 : f[1] != 0 ? 1 : 2, u = (ax + 1) % 3, v = (ax + 2) % 3;
            int base = vs.size();
            for (int k = 0; k < 4; k++) {
                double[] p = {x, y, z};
                p[ax] += f[ax] * half[ax];
                p[u] += (k == 1 || k == 2 ? 1 : -1) * half[u];
                p[v] += (k >= 2 ? 1 : -1) * half[v];
                vs.add(new float[]{(float) p[0], (float) p[1], (float) p[2], (float) f[0], (float) f[1], (float) f[2], 1});
            }
            boolean flip = f[ax] < 0;
            if (!flip) { idx.add(base); idx.add(base + 1); idx.add(base + 2); idx.add(base); idx.add(base + 2); idx.add(base + 3); }
            else { idx.add(base); idx.add(base + 2); idx.add(base + 1); idx.add(base); idx.add(base + 3); idx.add(base + 2); }
        }
    }

    private void targets(GL4 gl, int W, int H) {
        if (W == w && H == h) return;
        int[] t2 = new int[3];
        if (fbo != 0) {
            gl.glDeleteFramebuffers(2, new int[]{fbo, copyFbo}, 0);
            gl.glDeleteTextures(3, new int[]{color, depth, copyDepth}, 0);
        }
        gl.glGenTextures(3, t2, 0);
        color = t2[0]; depth = t2[1]; copyDepth = t2[2];
        gl.glBindTexture(GL.GL_TEXTURE_2D, color);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, W, H);
        for (int dt : new int[]{depth, copyDepth}) {
            gl.glBindTexture(GL.GL_TEXTURE_2D, dt);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, W, H);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        }
        gl.glGenFramebuffers(2, t2, 0);
        fbo = t2[0];
        copyFbo = t2[1];
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
        if (!built) build(gl);
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        double[] eye = eye();
        rain.eyeX = eye[0];
        rain.eyeY = eye[1];
        rain.eyeZ = eye[2];
        if (!frozen) step(dt * lapse);
        else rain.step(0);
        if (frame > 1) {
            int o = ((frame - 1) & 1) * 2;
            long[] r = new long[2];
            gl.glGetQueryObjecti64v(query[o], GL4.GL_QUERY_RESULT, r, 0);
            gl.glGetQueryObjecti64v(query[o + 1], GL4.GL_QUERY_RESULT, r, 1);
            gpuMs = gpuMs * 0.9 + Math.max(0, r[1] - r[0]) / 1e6 * 0.1;
        }
        int W = d.getSurfaceWidth(), H = d.getSurfaceHeight();
        targets(gl, W, H);
        Rain.Snapshot s = rain.snapshot();
        // je mehr Regen am Auge, desto dunkler und grauer der Himmel (die Wolke darüber)
        double here = s.rateAt(eye[0], eye[2]);
        double wet = Math.min(1, Math.sqrt(here / 10));
        double sun = Math.max(0.03, Math.sin(Math.toRadians(sunAlt)) * 1.3) * (1 - 0.85 * Math.min(1, here / 2 + (mode == 0 ? 0.6 : 0.2)));
        double skyB = (0.3 + 0.7 * Math.max(0, Math.sin(Math.toRadians(sunAlt)))) * (1 - 0.55 * wet);
        double[] skyTop = {0.20 * skyB, 0.27 * skyB, 0.38 * skyB}, skyHor = {0.55 * skyB, 0.58 * skyB, 0.62 * skyB};
        double alt = Math.toRadians(sunAlt), az = Math.toRadians(sunAz);
        double[] sunDir = {Math.cos(alt) * Math.sin(az), Math.sin(alt), -Math.cos(alt) * Math.cos(az)};
        double[] sunC = {2.2 * sun, 2.1 * sun, 1.95 * sun};
        double[] fwd = {-eye[0], targetY - eye[1], -eye[2]};
        double fl = Math.sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
        for (int i = 0; i < 3; i++) fwd[i] /= fl;
        float[] proj = perspective(fovy, W / (double) Math.max(1, H), 0.2, 20_000);
        float[] vp = mul(proj, lookAt(eye, new double[]{0, targetY, 0}));
        // Ringkarte vor dem Wasser
        gl.glQueryCounter(query[(frame & 1) * 2], GL4.GL_TIMESTAMP);
        rgl.update(gl, s);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, W, H);
        gl.glClearColor((float) skyHor[0], (float) skyHor[1], (float) skyHor[2], 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(prog, "uVP"), 1, false, vp, 0);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uT"), (float) (t % 3600));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uAmp"), (float) (0.05 + 0.04 * bft));
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunDir"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSunColor"), (float) sunC[0], (float) sunC[1], (float) sunC[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSkyTop"), (float) skyTop[0], (float) skyTop[1], (float) skyTop[2]);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uSkyHorizon"), (float) skyHor[0], (float) skyHor[1], (float) skyHor[2]);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uFogDist"), 9000f);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uWet"), (float) wet);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, rgl.rippleTexture());
        gl.glUniform1i(gl.glGetUniformLocation(prog, "uRipple"), 0);
        gl.glUniform3f(gl.glGetUniformLocation(prog, "uRippleP"), (float) rgl.rippleTile(), (float) rgl.rippleStrength(), rgl.rippleTexture() != 0 ? 1f : 0f);
        gl.glBindVertexArray(vao);
        gl.glDrawElements(GL.GL_TRIANGLES, count, GL.GL_UNSIGNED_INT, 0);
        // Tiefe kopieren, dann der Regen
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, W, H, 0, 0, W, H, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        RainGl.View v = new RainGl.View();
        v.viewProj = vp;
        v.invViewProj = invert(vp);
        v.eye = eye;
        v.forward = fwd;
        v.reversedZ = false;
        v.near = 0.2;
        v.far = 20_000;
        v.fovyDeg = fovy;
        v.width = W;
        v.height = H;
        v.depthTexture = copyDepth;
        RainGl.Light l = new RainGl.Light();
        l.sunDir = sunDir;
        l.sunColor = sunC;
        l.skyTop = skyTop;
        l.skyHorizon = skyHor;
        l.fogDistance = 9000;
        rgl.draw(gl, v, l, strength);
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
        gl.glUniform1f(gl.glGetUniformLocation(post, "uExposure"), (float) (1.1 / Math.max(0.12, skyB)));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        status = String.format(Locale.GERMAN, "%s · Wind %.1f m/s · %s · %s · Regen %.2f ms", MODES[mode], rain.windSpeed, rain.describe(), rgl.info(), gpuMs);
    }

    String check(GLAutoDrawable d) {
        selfCheck = rgl.selfCheck(d.getGL().getGL4(), rain.snapshot());
        return selfCheck;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        rgl.dispose(gl);
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

    /** Umkehrung einer 4×4-Matrix (Spalten zuerst), in double gerechnet. */
    static float[] invert(float[] m) {
        double[] a = new double[16];
        for (int i = 0; i < 16; i++) a[i] = m[i];
        double[] inv = new double[16];
        inv[0] = a[5] * a[10] * a[15] - a[5] * a[11] * a[14] - a[9] * a[6] * a[15] + a[9] * a[7] * a[14] + a[13] * a[6] * a[11] - a[13] * a[7] * a[10];
        inv[4] = -a[4] * a[10] * a[15] + a[4] * a[11] * a[14] + a[8] * a[6] * a[15] - a[8] * a[7] * a[14] - a[12] * a[6] * a[11] + a[12] * a[7] * a[10];
        inv[8] = a[4] * a[9] * a[15] - a[4] * a[11] * a[13] - a[8] * a[5] * a[15] + a[8] * a[7] * a[13] + a[12] * a[5] * a[11] - a[12] * a[7] * a[9];
        inv[12] = -a[4] * a[9] * a[14] + a[4] * a[10] * a[13] + a[8] * a[5] * a[14] - a[8] * a[6] * a[13] - a[12] * a[5] * a[10] + a[12] * a[6] * a[9];
        inv[1] = -a[1] * a[10] * a[15] + a[1] * a[11] * a[14] + a[9] * a[2] * a[15] - a[9] * a[3] * a[14] - a[13] * a[2] * a[11] + a[13] * a[3] * a[10];
        inv[5] = a[0] * a[10] * a[15] - a[0] * a[11] * a[14] - a[8] * a[2] * a[15] + a[8] * a[3] * a[14] + a[12] * a[2] * a[11] - a[12] * a[3] * a[10];
        inv[9] = -a[0] * a[9] * a[15] + a[0] * a[11] * a[13] + a[8] * a[1] * a[15] - a[8] * a[3] * a[13] - a[12] * a[1] * a[11] + a[12] * a[3] * a[9];
        inv[13] = a[0] * a[9] * a[14] - a[0] * a[10] * a[13] - a[8] * a[1] * a[14] + a[8] * a[2] * a[13] + a[12] * a[1] * a[10] - a[12] * a[2] * a[9];
        inv[2] = a[1] * a[6] * a[15] - a[1] * a[7] * a[14] - a[5] * a[2] * a[15] + a[5] * a[3] * a[14] + a[13] * a[2] * a[7] - a[13] * a[3] * a[6];
        inv[6] = -a[0] * a[6] * a[15] + a[0] * a[7] * a[14] + a[4] * a[2] * a[15] - a[4] * a[3] * a[14] - a[12] * a[2] * a[7] + a[12] * a[3] * a[6];
        inv[10] = a[0] * a[5] * a[15] - a[0] * a[7] * a[13] - a[4] * a[1] * a[15] + a[4] * a[3] * a[13] + a[12] * a[1] * a[7] - a[12] * a[3] * a[5];
        inv[14] = -a[0] * a[5] * a[14] + a[0] * a[6] * a[13] + a[4] * a[1] * a[14] - a[4] * a[2] * a[13] - a[12] * a[1] * a[6] + a[12] * a[2] * a[5];
        inv[3] = -a[1] * a[6] * a[11] + a[1] * a[7] * a[10] + a[5] * a[2] * a[11] - a[5] * a[3] * a[10] - a[9] * a[2] * a[7] + a[9] * a[3] * a[6];
        inv[7] = a[0] * a[6] * a[11] - a[0] * a[7] * a[10] - a[4] * a[2] * a[11] + a[4] * a[3] * a[10] + a[8] * a[2] * a[7] - a[8] * a[3] * a[6];
        inv[11] = -a[0] * a[5] * a[11] + a[0] * a[7] * a[9] + a[4] * a[1] * a[11] - a[4] * a[3] * a[9] - a[8] * a[1] * a[7] + a[8] * a[3] * a[5];
        inv[15] = a[0] * a[5] * a[10] - a[0] * a[6] * a[9] - a[4] * a[1] * a[10] + a[4] * a[2] * a[9] + a[8] * a[1] * a[6] - a[8] * a[2] * a[5];
        double det = a[0] * inv[0] + a[1] * inv[4] + a[2] * inv[8] + a[3] * inv[12];
        float[] r = new float[16];
        for (int i = 0; i < 16; i++) r[i] = (float) (inv[i] / det);
        return r;
    }

    static String read(String f) {
        try (InputStream in = RainScene.class.getResourceAsStream(f)) {
            if (in == null) throw new IllegalStateException("rain demo: " + f + " fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
