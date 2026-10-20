package com.dan.buoyancy.demo;

import com.dan.buoyancy.Buoyancy;
import com.dan.buoyancy.LightPoint;
import com.dan.buoyancy.Ship;
import com.dan.buoyancy.gl.BuoyancyGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Die Szene der Vorführung: eine See aus zwölf Wellenzügen ({@link DemoSea}), darauf ein Fahrwasser mit Tonnen und
 * ein Kümo, darüber ein schlichter Himmel. Kamera frei um einen Punkt oder auf der Brücke des Schiffs (mit Stampfen
 * und Rollen, Brücke von innen und Wischer). Lichter als weiche Punkte.
 */
public final class BuoyancyScene implements GLEventListener {
    public final Buoyancy fleet = Buoyancy.beaufort(5);
    public volatile DemoSea sea = new DemoSea(2, 9.4, 240, 5);
    public final BuoyancyGl bodies = new BuoyancyGl();
    public volatile double yaw = 20, pitch = 6, dist = 90, tx = 0, ty = 3, tz = 0, fovy = 50;
    public volatile double sunAlt = 25, sunAz = 160, exposure = 1;
    public volatile boolean onBridge, frozen;
    public volatile double timeScale = 1;
    public volatile String check = "";
    private int skyProg, seaProg, glowProg, postProg, vao, gridVao, gridVbo, gridIbo, gridCount, glowVao, glowVbo, hdrTex, depthTex, fbo, w, h;
    private long last;
    private double t;

    public BuoyancyScene() {
        fleet.sea = sea;
        fleet.fairwayX = 0;
        fleet.fairwayZ = 0;
        fleet.fairwayDeg = 330;
        fleet.layFairway(220, 700, 3);
        fleet.shipEveryMin = 0;
    }

    /** Neue See (Beaufort) und alle Körper darauf setzen. */
    public void setSea(DemoSea s, double windSpeed, double windFrom) {
        s.t = t;
        sea = s;
        fleet.sea = s;
        fleet.windSpeed = windSpeed;
        fleet.windFromDeg = windFrom;
        fleet.settleAll();
    }

    public void advance(double dt) {
        int n = Math.max(1, (int) Math.ceil(dt / 0.05));
        for (int i = 0; i < n; i++) {
            t += dt / n;
            sea.t = t;
            fleet.step(dt / n);
        }
        if (onBridge && !fleet.ships.isEmpty()) fleet.ships.get(0).wiper.step(dt);
    }

    public double time() { return t; }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        String full = "#version 430 core\n" + read("full.vert");
        skyProg = BuoyancyGl.program(gl, full, "#version 430 core\n" + read("sky.frag"));
        seaProg = BuoyancyGl.program(gl, "#version 430 core\n" + read("sea.vert"), "#version 430 core\n" + read("sea.frag"));
        glowProg = BuoyancyGl.program(gl, "#version 430 core\n" + read("glow.vert"), "#version 430 core\n" + read("glow.frag"));
        postProg = BuoyancyGl.program(gl, full, "#version 430 core\n" + read("post.frag"));
        bodies.init(gl);
        int[] v = new int[3];
        gl.glGenVertexArrays(3, v, 0);
        vao = v[0];
        gridVao = v[1];
        glowVao = v[2];
        // Gitter 400 × 400 Felder um das Auge
        int n = 400;
        float[] g = new float[(n + 1) * (n + 1) * 2];
        int o = 0;
        for (int j = 0; j <= n; j++) for (int i = 0; i <= n; i++) { g[o++] = (i - n / 2f) * 2f; g[o++] = (j - n / 2f) * 2f; }
        int[] idx = new int[n * n * 6];
        o = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int a = j * (n + 1) + i, b = a + 1, c = a + n + 1, e = c + 1;
                idx[o++] = a; idx[o++] = c; idx[o++] = b; idx[o++] = b; idx[o++] = c; idx[o++] = e;
            }
        gridCount = idx.length;
        int[] bufs = new int[3];
        gl.glGenBuffers(3, bufs, 0);
        gridVbo = bufs[0];
        gridIbo = bufs[1];
        glowVbo = bufs[2];
        gl.glBindVertexArray(gridVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gridVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, g.length * 4L, Buffers.newDirectFloatBuffer(g), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 8, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, gridIbo);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(glowVao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, glowVbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 64 * 7 * 4L, null, GL.GL_DYNAMIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 4, GL.GL_FLOAT, false, 28, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 28, 16);
        gl.glBindVertexArray(0);
        last = System.nanoTime();
    }

    private void targets(GL4 gl, int nw, int nh) {
        if (nw == w && nh == h) return;
        if (fbo != 0) { gl.glDeleteFramebuffers(1, new int[]{fbo}, 0); gl.glDeleteTextures(2, new int[]{hdrTex, depthTex}, 0); }
        w = nw;
        h = nh;
        int[] t2 = new int[2];
        gl.glGenTextures(2, t2, 0);
        hdrTex = t2[0];
        depthTex = t2[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, w, h);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
    }

    double[][] light() {
        double e = Math.sin(Math.toRadians(sunAlt)), day = smooth(-0.1, 0.25, e), low = 1 - smooth(0.05, 0.4, e);
        double[] sun = {Math.sin(Math.toRadians(sunAz)) * Math.cos(Math.toRadians(sunAlt)), e, -Math.cos(Math.toRadians(sunAz)) * Math.cos(Math.toRadians(sunAlt))};
        double vis = smooth(-0.03, 0.02, e);
        double[] sc = {2.6 * vis, (2.4 - 1.0 * low) * vis, (2.2 - 1.6 * low) * vis};
        double[] top = {0.12 * day + 0.0015, 0.22 * day + 0.0022, 0.45 * day + 0.004}, hor = {(0.45 + 0.25 * low) * day + 0.003, (0.5 + 0.05 * low) * day + 0.004, (0.6 - 0.2 * low) * day + 0.006};
        return new double[][]{sun, sc, top, hor};
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Auge und Blick: frei um den Zielpunkt, oder auf der Brücke des ersten Schiffs. */
    public double[][] eyeAndView() {
        if (onBridge && !fleet.ships.isEmpty()) {
            Ship s = fleet.ships.get(0);
            double[][] v = s.bridgeView();
            return new double[][]{v[0], v[1], v[2]};
        }
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] f = {Math.sin(a) * Math.cos(p), -Math.sin(p), -Math.cos(a) * Math.cos(p)};
        double[] e = {tx - f[0] * dist, Math.max(sea.level + 1.5, ty - f[1] * dist), tz - f[2] * dist};
        return new double[][]{e, f, {0, 1, 0}};
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) advance(dt * timeScale);
        targets(gl, Math.max(1, d.getSurfaceWidth()), Math.max(1, d.getSurfaceHeight()));
        double[][] ev = eyeAndView();
        double[] eye = ev[0];
        float[] vp = M4.mul(M4.perspective(fovy, w / (double) h, onBridge ? 0.1 : 0.5, 20_000), M4.look(eye, ev[1], ev[2])), inv = M4.invert(vp);
        double[][] L = light();
        double pre = exposure, fogDist = 6000;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(skyProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(skyProg, "uInvVP"), 1, false, inv, 0);
        u3(gl, skyProg, "uSunDir", L[0], 1);
        u3(gl, skyProg, "uSunCol", L[1], pre);
        u3(gl, skyProg, "uSkyTop", L[2], pre);
        u3(gl, skyProg, "uSkyHor", L[3], pre);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glUseProgram(seaProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(seaProg, "uVP"), 1, false, vp, 0);
        u3(gl, seaProg, "uCam", eye, 1);
        gl.glUniform4fv(gl.glGetUniformLocation(seaProg, "uWave[0]"), 12, sea.waves(), 0);
        gl.glUniform1fv(gl.glGetUniformLocation(seaProg, "uPhase[0]"), 12, sea.phases(), 0);
        gl.glUniform1f(gl.glGetUniformLocation(seaProg, "uTime"), (float) sea.t);
        gl.glUniform1f(gl.glGetUniformLocation(seaProg, "uLevel"), (float) sea.level);
        gl.glUniform1f(gl.glGetUniformLocation(seaProg, "uCell"), 2f);
        gl.glUniform1f(gl.glGetUniformLocation(seaProg, "uFogDist"), (float) fogDist);
        u3(gl, seaProg, "uSunDir", L[0], 1);
        u3(gl, seaProg, "uSunCol", L[1], pre);
        u3(gl, seaProg, "uSkyTop", L[2], pre);
        u3(gl, seaProg, "uSkyHor", L[3], pre);
        gl.glBindVertexArray(gridVao);
        gl.glDrawElements(GL.GL_TRIANGLES, gridCount, GL.GL_UNSIGNED_INT, 0);
        // Körper
        BuoyancyGl.View v = new BuoyancyGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.bridge = onBridge && !fleet.ships.isEmpty() ? fleet.ships.get(0) : null;
        BuoyancyGl.Light bl = new BuoyancyGl.Light();
        bl.sunDir = L[0];
        bl.sunColor = scale(L[1], pre);
        bl.skyTop = scale(L[2], pre);
        bl.skyHorizon = scale(L[3], pre);
        bl.fogDistance = fogDist;
        boolean night = sunAlt < 1;
        fleet.ambientLux = night ? 5 : 20_000;
        bl.lamp = night ? 6 * pre : 0;
        bl.windowGlow = night ? 0.02 * pre : 0;
        bl.screen = 0.4 * pre * (night ? 0.3 : 1);
        bodies.draw(gl, fleet, v, bl);
        // Lichter als Punkte
        List<LightPoint> lp = fleet.lights(eye[0], eye[1], eye[2]);
        if (!lp.isEmpty()) {
            float[] g = new float[Math.min(64, lp.size()) * 7];
            int o = 0;
            for (LightPoint p : lp) {
                if (o >= g.length) break;
                double dd = Math.max(1, Math.hypot(Math.hypot(p.x() - eye[0], p.z() - eye[2]), p.y() - eye[1]));
                double lum = p.candela() / (dd * dd) * 500 * pre;
                double c = Math.max(1e-6, p.candela());
                g[o++] = (float) p.x(); g[o++] = (float) p.y(); g[o++] = (float) p.z(); g[o++] = (float) lum;
                g[o++] = (float) (p.r() / c); g[o++] = (float) (p.g() / c); g[o++] = (float) (p.b() / c);
            }
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, glowVbo);
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, o * 4L, Buffers.newDirectFloatBuffer(g, 0, o));
            gl.glEnable(GL3.GL_PROGRAM_POINT_SIZE);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
            gl.glDepthMask(false);
            gl.glUseProgram(glowProg);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(glowProg, "uVP"), 1, false, vp, 0);
            gl.glUniform1f(gl.glGetUniformLocation(glowProg, "uScale"), (float) (h / 900.0 * 4));
            gl.glBindVertexArray(glowVao);
            gl.glDrawArrays(GL.GL_POINTS, 0, o / 7);
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
        }
        // Filmkurve
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(postProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glUniform1i(gl.glGetUniformLocation(postProg, "uHdr"), 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        if (check.isEmpty() && !fleet.ships.isEmpty()) check = bodies.selfCheck(gl, fleet.ships.get(0));
    }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    private static void u3(GL4 gl, int p, String n, double[] v, double k) {
        gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) (v[0] * k), (float) (v[1] * k), (float) (v[2] * k));
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) { bodies.dispose(d.getGL().getGL4()); }

    static String read(String f) {
        try (InputStream in = BuoyancyScene.class.getResourceAsStream(f)) {
            if (in == null) throw new IllegalStateException("Shader fehlt: " + f);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Kleine Matrizenhilfe der Vorführung (Spalten zuerst, Tiefe −1..1). */
    static final class M4 {
        static float[] perspective(double fovyDeg, double aspect, double n, double f) {
            double c = 1 / Math.tan(Math.toRadians(fovyDeg) / 2);
            float[] m = new float[16];
            m[0] = (float) (c / aspect);
            m[5] = (float) c;
            m[10] = (float) ((f + n) / (n - f));
            m[11] = -1;
            m[14] = (float) (2 * f * n / (n - f));
            return m;
        }

        /** Blick vom Auge in Richtung f mit „oben“ u. */
        static float[] look(double[] e, double[] f, double[] u0) {
            double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
            double fx = f[0] / fl, fy = f[1] / fl, fz = f[2] / fl;
            double sx = fy * u0[2] - fz * u0[1], sy = fz * u0[0] - fx * u0[2], sz = fx * u0[1] - fy * u0[0];
            double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
            sx /= sl; sy /= sl; sz /= sl;
            double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
            float[] m = new float[16];
            m[0] = (float) sx; m[4] = (float) sy; m[8] = (float) sz;
            m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
            m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
            m[12] = (float) -(sx * e[0] + sy * e[1] + sz * e[2]);
            m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]);
            m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
            m[15] = 1;
            return m;
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

        static float[] invert(float[] m) {
            double[][] a = new double[4][8];
            for (int r = 0; r < 4; r++) { for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r]; a[r][4 + r] = 1; }
            for (int c = 0; c < 4; c++) {
                int p = c;
                for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
                double[] tmp = a[c]; a[c] = a[p]; a[p] = tmp;
                double d = a[c][c];
                for (int k = 0; k < 8; k++) a[c][k] /= d;
                for (int r = 0; r < 4; r++) if (r != c) { double f = a[r][c]; for (int k = 0; k < 8; k++) a[r][k] -= f * a[c][k]; }
            }
            float[] out = new float[16];
            for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) a[r][4 + c];
            return out;
        }
    }
}
