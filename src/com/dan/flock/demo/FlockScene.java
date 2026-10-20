package com.dan.flock.demo;

import com.dan.flock.Flock;
import com.dan.flock.gl.FlockGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Die Szene der Vorführung: See, ein rot-weißer Turm (Halbmesser 3,5 m, 27,6 m hoch) mit Galerie in 23 m, darauf zehn
 * Sitzplätze auf dem Geländer und einer auf der Kuppel; Himmel mit Sonne. Ohne Dreiecke (scene.frag), dann die Möwen.
 * Kamera frei um den Turm oder auf einer Möwe („Möwenflug“).
 */
public final class FlockScene implements GLEventListener {
    public static final double TOWER_R = 3.5, TOWER_H = 27.6, GALLERY_R = 4.3, GALLERY_Y = 23.0, RAIL_Y = 24.1;
    public final Flock flock = Flock.beaufort(5);
    public final FlockGl gl = new FlockGl();
    public volatile double yaw = 60, pitch = 4, dist = 90, ty = 18, fovy = 50;
    public volatile double sunAlt = 25, sunAz = 150, exposure = 1;
    public volatile boolean frozen, ride;
    public volatile int rideId = 0;
    public volatile double timeScale = 1;
    public volatile String check = "";
    private int sceneProg, postProg, vao, hdrTex, depthTex, fbo, w, h;
    private long last;
    private double t;
    private double[] rideEye, rideFwd;

    public FlockScene() {
        flock.obstacles.add(new Flock.Obstacle(0, 0, TOWER_R, -2, TOWER_H));
        for (int i = 0; i < 10; i++) {
            double a = 2 * Math.PI * i / 10;
            flock.perches.add(new Flock.Perch("Geländer " + (i + 1), Math.sin(a) * GALLERY_R, RAIL_Y, -Math.cos(a) * GALLERY_R));
        }
        flock.perches.add(new Flock.Perch("Kuppel", 0, TOWER_H + 0.2, 0));
        flock.homeRadius = 140;
    }

    public void advance(double dt) {
        t += dt;
        flock.ambientLux = sunAlt > -2 ? 20_000 : 5;
        flock.step(dt);
    }

    public double time() { return t; }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        String full = "#version 430 core\n" + read("full.vert");
        sceneProg = FlockGl.program(g, full, "#version 430 core\n" + read("scene.frag"));
        postProg = FlockGl.program(g, full, "#version 430 core\n" + read("post.frag"));
        gl.init(g);
        int[] v = new int[1];
        g.glGenVertexArrays(1, v, 0);
        vao = v[0];
        last = System.nanoTime();
    }

    private void targets(GL4 g, int nw, int nh) {
        if (nw == w && nh == h) return;
        if (fbo != 0) { g.glDeleteFramebuffers(1, new int[]{fbo}, 0); g.glDeleteTextures(2, new int[]{hdrTex, depthTex}, 0); }
        w = nw;
        h = nh;
        int[] t2 = new int[2];
        g.glGenTextures(2, t2, 0);
        hdrTex = t2[0];
        depthTex = t2[1];
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        g.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, w, h);
        int[] f = new int[1];
        g.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
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

    /** Auge und Blick: frei um den Turm oder hinter einer Möwe (weich nachgeführt). */
    public double[][] eyeAndView() {
        if (ride) {
            Flock.Gull g = null;
            for (Flock.Gull x : flock.gulls()) if (x.id == rideId) g = x;
            if (g != null && g.state != Flock.State.AWAY) {
                double vx = g.vel[0], vy = g.vel[1], vz = g.vel[2], l = Math.sqrt(vx * vx + vy * vy + vz * vz);
                double[] f = l > 0.5 ? new double[]{vx / l, vy / l * 0.5, vz / l} : new double[]{Math.sin(Math.toRadians(g.facing)), 0, -Math.cos(Math.toRadians(g.facing))};
                double[] e = {g.pos[0] - f[0] * 0.15, g.pos[1] + 0.12, g.pos[2] - f[2] * 0.15};
                if (rideFwd == null) { rideFwd = f; rideEye = e; }
                for (int k = 0; k < 3; k++) rideFwd[k] += (f[k] - rideFwd[k]) * 0.15;
                rideEye = e;
                double[] fw = rideFwd.clone();
                double fl = Math.sqrt(fw[0] * fw[0] + fw[1] * fw[1] + fw[2] * fw[2]);
                double[] r = {-fw[2] / fl, 0, fw[0] / fl};
                double rl = Math.hypot(r[0], r[2]);
                r[0] /= rl; r[2] /= rl;
                double[] u = {r[1] * fw[2] - r[2] * fw[1], r[2] * fw[0] - r[0] * fw[2], r[0] * fw[1] - r[1] * fw[0]};
                double c = Math.cos(g.bank * 0.7), sn = Math.sin(g.bank * 0.7);
                return new double[][]{rideEye, fw, {r[0] * sn + u[0] * c, r[1] * sn + u[1] * c, r[2] * sn + u[2] * c}};
            }
        }
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double[] f = {Math.sin(a) * Math.cos(p), -Math.sin(p), -Math.cos(a) * Math.cos(p)};
        return new double[][]{{-f[0] * dist, Math.max(1.5, ty - f[1] * dist), -f[2] * dist}, f, {0, 1, 0}};
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) advance(dt * timeScale);
        targets(g, Math.max(1, d.getSurfaceWidth()), Math.max(1, d.getSurfaceHeight()));
        double[][] ev = eyeAndView();
        double[] eye = ev[0];
        float[] vp = M4.mul(M4.perspective(fovy, w / (double) h, 0.1, 20_000), M4.look(eye, ev[1], ev[2])), inv = M4.invert(vp);
        double[][] L = light();
        double pre = exposure;
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glViewport(0, 0, w, h);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_ALWAYS);
        g.glDepthMask(true);
        g.glUseProgram(sceneProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(sceneProg, "uInvVP"), 1, false, inv, 0);
        g.glUniformMatrix4fv(g.glGetUniformLocation(sceneProg, "uVP"), 1, false, vp, 0);
        u3(g, sceneProg, "uCam", eye, 1);
        u3(g, sceneProg, "uSunDir", L[0], 1);
        u3(g, sceneProg, "uSunCol", L[1], pre);
        u3(g, sceneProg, "uSkyTop", L[2], pre);
        u3(g, sceneProg, "uSkyHor", L[3], pre);
        g.glUniform4f(g.glGetUniformLocation(sceneProg, "uTower"), (float) TOWER_R, (float) TOWER_H, (float) GALLERY_R + 0.3f, (float) GALLERY_Y);
        g.glUniform1f(g.glGetUniformLocation(sceneProg, "uTime"), (float) (t % 1000));
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glDepthFunc(GL.GL_LESS);
        FlockGl.View v = new FlockGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.hide = ride ? rideId : -1;
        FlockGl.Light fl = new FlockGl.Light();
        fl.sunDir = L[0];
        fl.sunColor = scale(L[1], pre);
        fl.skyTop = scale(L[2], pre);
        fl.skyHorizon = scale(L[3], pre);
        fl.fogDistance = 8000;
        gl.draw(g, flock, v, fl);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, w, h);
        g.glUseProgram(postProg);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glUniform1i(g.glGetUniformLocation(postProg, "uHdr"), 0);
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glBindVertexArray(0);
        g.glUseProgram(0);
        if (check.isEmpty() && !flock.gulls().isEmpty()) check = gl.selfCheck(g, flock);
    }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    private static void u3(GL4 g, int p, String n, double[] v, double k) {
        g.glUniform3f(g.glGetUniformLocation(p, n), (float) (v[0] * k), (float) (v[1] * k), (float) (v[2] * k));
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    static String read(String f) {
        try (InputStream in = FlockScene.class.getResourceAsStream(f)) {
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
