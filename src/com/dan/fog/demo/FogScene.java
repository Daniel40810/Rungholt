package com.dan.fog.demo;

import com.dan.fog.Fog;
import com.dan.fog.Signal;
import com.dan.fog.gl.FogGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Szene der Vorführung: See, ein rot-weißer Turm von 30 m in 420 m und sechs Pfähle von 60 m bis 2 km, darüber
 * ein schlichter Himmel mit Sonne. Ohne Dreiecke (scene.frag); danach die Tiefe kopiert, der Nebel aus
 * {@code com.dan.fog.gl} darüber und die Filmkurve. Am Turm tönt ein Nebelhorn {@code Horn Mo(R) 30s}.
 */
public final class FogScene implements GLEventListener {
    public final Fog fog = Fog.banks();
    public final FogGl fogGl = new FogGl();
    public final Signal horn = new Signal("Turm", "Horn Mo(R) 30s", 0, 28, -420);
    public volatile double yaw = 0, pitch = 2, eyeX = 0, eyeY = 6, eyeZ = 0, fovy = 60;
    public volatile double sunAlt = 22, sunAz = 200, exposure = 1;
    public volatile boolean frozen;
    public volatile double timeScale = 1;
    public volatile String check = "", status = "";
    private int sceneProg, postProg, vao, hdrTex, depthTex, fbo, copyTex, copyFbo, w, h;
    private long last;
    private double t;
    private int frame;

    public FogScene() {
        fog.signals.add(horn);
        fog.onHorn = (x, y, z, s, sec, sig) -> status = String.format(Locale.GERMAN, "%s tönt %.0f s", sig.name, sec);
    }

    public void advance(double dt) {
        t += dt;
        fog.step(dt);
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        String vert = "#version 430 core\n" + read("full.vert");
        sceneProg = FogGl.program(gl, vert, "#version 430 core\n" + read("scene.frag"));
        postProg = FogGl.program(gl, vert, "#version 430 core\n" + read("post.frag"));
        fogGl.init(gl);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        last = System.nanoTime();
    }

    private void targets(GL4 gl, int nw, int nh) {
        if (nw == w && nh == h) return;
        if (fbo != 0) {
            gl.glDeleteFramebuffers(2, new int[]{fbo, copyFbo}, 0);
            gl.glDeleteTextures(3, new int[]{hdrTex, depthTex, copyTex}, 0);
        }
        w = nw;
        h = nh;
        hdrTex = tex(gl, GL.GL_RGBA16F, w, h);
        depthTex = tex(gl, GL4.GL_DEPTH_COMPONENT32F, w, h);
        copyTex = tex(gl, GL4.GL_DEPTH_COMPONENT32F, w, h);
        int[] f = new int[2];
        gl.glGenFramebuffers(2, f, 0);
        fbo = f[0];
        copyFbo = f[1];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, copyFbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, copyTex, 0);
        gl.glDrawBuffer(GL.GL_NONE);
    }

    private static int tex(GL4 gl, int fmt, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    /** Licht aus der Sonnenhöhe: Sonne, Himmel oben und am Horizont, Lampe am Turm in der Dämmerung. */
    double[][] light() {
        double e = Math.sin(Math.toRadians(sunAlt)), day = smooth(-0.1, 0.25, e), low = 1 - smooth(0.05, 0.4, e);
        double[] sun = {Math.sin(Math.toRadians(sunAz)) * Math.cos(Math.toRadians(sunAlt)), e, -Math.cos(Math.toRadians(sunAz)) * Math.cos(Math.toRadians(sunAlt))};
        double vis = smooth(-0.03, 0.02, e);
        double[] sc = {2.6 * vis, (2.4 - 1.0 * low) * vis, (2.2 - 1.6 * low) * vis};
        double[] top = {0.12 * day + 0.004, 0.22 * day + 0.006, 0.45 * day + 0.012}, hor = {(0.45 + 0.25 * low) * day + 0.01, (0.5 + 0.05 * low) * day + 0.012, (0.6 - 0.2 * low) * day + 0.018};
        return new double[][]{sun, sc, top, hor};
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) advance(dt * timeScale);
        targets(gl, Math.max(1, d.getSurfaceWidth()), Math.max(1, d.getSurfaceHeight()));
        double[] eye = {eyeX, eyeY, eyeZ};
        float[] vp = M4.mul(M4.perspective(fovy, w / (double) h, 0.5, 30_000), M4.view(eye, yaw, pitch)), inv = M4.invert(vp);
        double[][] L = light();
        double pre = exposure;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_ALWAYS);
        gl.glDepthMask(true);
        gl.glUseProgram(sceneProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(sceneProg, "uInvVP"), 1, false, inv, 0);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(sceneProg, "uVP"), 1, false, vp, 0);
        u3(gl, sceneProg, "uCam", eye, 1);
        u3(gl, sceneProg, "uSunDir", L[0], 1);
        u3(gl, sceneProg, "uSunCol", L[1], pre);
        u3(gl, sceneProg, "uSkyTop", L[2], pre);
        u3(gl, sceneProg, "uSkyHor", L[3], pre);
        gl.glUniform1f(gl.glGetUniformLocation(sceneProg, "uTime"), (float) (t % 1000));
        gl.glUniform1f(gl.glGetUniformLocation(sceneProg, "uLamp"), (float) (sunAlt < 2 ? 3.0 : 0.0));
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDepthFunc(GL.GL_LESS);
        // Tiefe kopieren, dann der Nebel
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, copyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        Fog.Snapshot s = fog.snapshot();
        fogGl.update(gl, s, eye);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        FogGl.View v = new FogGl.View();
        v.invViewProj = inv;
        v.eye = eye;
        v.width = w;
        v.height = h;
        v.depthTexture = copyTex;
        v.jitter = frozen ? 0.5 : -1;
        v.frame = frame++;
        FogGl.Light fl = new FogGl.Light();
        fl.sunDir = L[0];
        fl.sunColor = scale(L[1], pre);
        fl.ambientTop = scale(L[2], pre);
        fl.ambientHorizon = scale(L[3], pre);
        fogGl.draw(gl, s, v, fl);
        // Filmkurve ins Fenster
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
        if (check.isEmpty() && s.any() && frame > 3) check = fogGl.selfCheck(gl, fog, eye);
    }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    private static void u3(GL4 gl, int p, String n, double[] v, double k) {
        gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) (v[0] * k), (float) (v[1] * k), (float) (v[2] * k));
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        fogGl.dispose(gl);
    }

    static String read(String f) {
        try (InputStream in = FogScene.class.getResourceAsStream(f)) {
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

        /** Blick vom Auge nach yaw (Grad von Nord, Uhrzeigersinn) und pitch (Grad, positiv nach unten). */
        static float[] view(double[] e, double yaw, double pitch) {
            double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
            double fx = Math.sin(a) * Math.cos(p), fy = -Math.sin(p), fz = -Math.cos(a) * Math.cos(p);
            double sx = -fz, sz = fx, sl = Math.hypot(sx, sz);
            sx /= sl;
            sz /= sl;
            double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
            float[] m = new float[16];
            m[0] = (float) sx; m[4] = 0; m[8] = (float) sz;
            m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
            m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
            m[12] = (float) -(sx * e[0] + sz * e[2]);
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
    }
}
