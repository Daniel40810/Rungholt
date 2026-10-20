package com.dan.lightning.demo;

import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.lightning.gl.LightningGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.util.Locale;

/**
 * Die Szene der Vorführung: Nacht über der See unter einer Gewitterwolke, ein Mast von 30 m als Fangpunkt. Ohne
 * Dreiecke: Himmel, Wolkenunterseite, See und Mast sind ein Bilddurchgang (scene.frag). Ablauf je Bild: Rundumbild
 * mit Himmel, Wolken und Kanälen (für die Spiegelung in der See), dann die Szene mit Tiefe, die Kanäle mit Tiefentest,
 * das Leuchten der Luft und die Filmkurve.
 */
public final class LightningScene implements GLEventListener {
    public final Lightning lightning = Lightning.storm();
    public final LightningGl gl = new LightningGl();
    public volatile double yaw = 0, pitch = 8, eyeH = 6, eyeX = 0, eyeZ = 400, fovy = 62;
    /** Belichtung ohne Blitz und im Augenblick (gibt im Blitz nach). */
    public volatile double basePre = 1500, pre = 1500, skyL = 2.5e-5, cover = 0.8, rough = 0.5;
    private double flashGive = 1;
    /** Regen in mm/h (Streuung 0,25 · R^0,63 je km, fast alles Streuung) und Sicht ohne Regen in km. */
    public volatile double rain = 0, visibilityKm = 25;
    /** Zeitlupe: Faktor auf die Zeit (1 = echt). */
    public volatile double timeScale = 1;
    public volatile boolean frozen;
    public volatile String check = "";
    /** Mast: x, z, Höhe. */
    public final double mastX = 0, mastZ = -260, mastH = 30;
    private int sceneProg, postProg, vao, hdrTex, depthTex, fbo, copyTex, copyFbo, panoTex, panoFbo, w, h;
    private static final int PW = 512, PH = 256;
    private long last;
    private double t;

    public LightningScene() {
        lightning.seed = 12;
        lightning.cloudBase = 950;
        lightning.cx = 0;
        lightning.cz = -5000;
        lightning.radius = 4500;
        lightning.attractors.add(new Lightning.Attractor(mastX, mastH + 1, mastZ, "Mast"));
    }

    public static double rainSigma(double mmh) { return mmh <= 0 ? 0 : 0.25 * Math.pow(mmh, 0.63) / 1000; }

    public double haze() { return 2.996 / (visibilityKm * 1000); }

    /** Szenenzeit um dt weiter (Standbilder). */
    public void advance(double dt) {
        t += dt;
        double[] e = eye();
        lightning.listenerX = e[0];
        lightning.listenerY = e[1];
        lightning.listenerZ = e[2];
        lightning.step(dt);
    }

    public double time() { return t; }

    public double[] eye() { return new double[]{eyeX, eyeH, eyeZ}; }

    public double[] forward() {
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        return new double[]{Math.sin(yr) * Math.cos(pr), Math.sin(pr), -Math.cos(yr) * Math.cos(pr)};
    }

    /** Blick auf einen Punkt richten. */
    public void lookAt(double x, double y, double z) {
        double dx = x - eyeX, dy = y - eyeH, dz = z - eyeZ;
        yaw = Math.toDegrees(Math.atan2(dx, -dz));
        pitch = Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
    }

    @Override
    public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        String fs = LightningGl.read("fullscreen.vert");
        sceneProg = LightningGl.program(g, fs, LightningGl.read("lightning.glsl") + read("scene.frag"));
        postProg = LightningGl.program(g, fs, read("post.frag"));
        int[] v = new int[1];
        g.glGenVertexArrays(1, v, 0);
        vao = v[0];
        int[] t2 = new int[1];
        g.glGenTextures(1, t2, 0);
        panoTex = t2[0];
        g.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, PW, PH);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        g.glGenFramebuffers(1, t2, 0);
        panoFbo = t2[0];
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, panoTex, 0);
        gl.init(g);
        last = System.nanoTime();
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int ww, int hh) { }

    private void targets(GL4 g, int ww, int hh) {
        if (ww == w && hh == h) return;
        if (fbo != 0) {
            g.glDeleteFramebuffers(2, new int[]{fbo, copyFbo}, 0);
            g.glDeleteTextures(3, new int[]{hdrTex, depthTex, copyTex}, 0);
        }
        int[] t3 = new int[3], f2 = new int[2];
        g.glGenTextures(3, t3, 0);
        g.glGenFramebuffers(2, f2, 0);
        hdrTex = t3[0];
        depthTex = t3[1];
        copyTex = t3[2];
        fbo = f2[0];
        copyFbo = f2[1];
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, ww, hh);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        for (int tx : new int[]{depthTex, copyTex}) {
            g.glBindTexture(GL.GL_TEXTURE_2D, tx);
            g.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, ww, hh);
            g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, copyFbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, copyTex, 0);
        g.glBindTexture(GL.GL_TEXTURE_2D, 0);
        w = ww;
        h = hh;
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) advance(dt * timeScale);
        int ww = d.getSurfaceWidth(), hh = d.getSurfaceHeight();
        targets(g, ww, hh);
        double rs = rainSigma(rain), sigma = rs + haze(), scatter = rs * 0.98 + haze() * 0.9;
        Lightning.Snapshot s = lightning.snapshot();
        gl.update(g, s);
        double[] e = eye(), f = forward();
        // Belichtung gibt im Blitz nach (wie ein Auge, das kurz geblendet wird, oder eine Kamera, die auf den Blitz
        // belichtet) und erholt sich in einer Viertelsekunde
        double lf = s.flashLuminance(e[0], e[1], e[2], lightning.cloudBase);
        double give = 1 + basePre * lf / 1.5;
        flashGive = frozen ? give : Math.max(give, flashGive * Math.exp(-dt * timeScale / 0.25));
        if (frozen) flashGive = give;
        pre = basePre / flashGive;
        float[] proj = perspective(fovy, ww / (double) hh, 0.5, 80_000);
        float[] view = lookAtMatrix(e, new double[]{e[0] + f[0], e[1] + f[1], e[2] + f[2]});
        float[] vp = mul(proj, view), inv = invert(vp);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glDisable(GL.GL_BLEND);
        g.glBindVertexArray(vao);
        // 1 Rundumbild: Himmel und Wolken, dann Kanäle und Leuchten hinein
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
        g.glViewport(0, 0, PW, PH);
        g.glUseProgram(sceneProg);
        uniforms(g, vp, inv, e, sigma, 1);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.drawPanorama(g, PW, PH, e, pre, sigma, scatter, lightning.cloudBase);
        // 2 Szene mit Tiefe
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        g.glViewport(0, 0, ww, hh);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_ALWAYS);
        g.glUseProgram(sceneProg);
        uniforms(g, vp, inv, e, sigma, 0);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        g.glUniform1i(g.glGetUniformLocation(sceneProg, "uPanoTex"), 0);
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glDepthFunc(GL.GL_LESS);
        g.glDisable(GL.GL_DEPTH_TEST);
        // 3 Tiefe kopieren, Kanäle (verdeckt von See und Mast) und Leuchten
        g.glBindFramebuffer(GL2ES3.GL_READ_FRAMEBUFFER, fbo);
        g.glBindFramebuffer(GL2ES3.GL_DRAW_FRAMEBUFFER, copyFbo);
        g.glBlitFramebuffer(0, 0, ww, hh, 0, 0, ww, hh, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        LightningGl.View v = new LightningGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = e;
        v.reversedZ = false;
        v.width = ww;
        v.height = hh;
        v.fovyDeg = fovy;
        v.depthTexture = copyTex;
        v.depthTest = true;
        v.targetFbo = fbo;
        v.preExposure = pre;
        v.extinction = sigma;
        v.scatter = scatter;
        v.ceiling = lightning.cloudBase;
        gl.drawChannels(g, v);
        gl.drawGlow(g, v);
        // 4 ins Fenster
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, ww, hh);
        g.glUseProgram(postProg);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glUniform1i(g.glGetUniformLocation(postProg, "uHdr"), 0);
        g.glBindVertexArray(vao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glUseProgram(0);
    }

    private void uniforms(GL4 g, float[] vp, float[] inv, double[] e, double sigma, int pano) {
        int p = sceneProg;
        g.glUniformMatrix4fv(g.glGetUniformLocation(p, "uInvVP"), 1, false, inv, 0);
        g.glUniformMatrix4fv(g.glGetUniformLocation(p, "uVP"), 1, false, vp, 0);
        g.glUniform3f(g.glGetUniformLocation(p, "uCam"), (float) e[0], (float) e[1], (float) e[2]);
        g.glUniform1f(g.glGetUniformLocation(p, "uSkyL"), (float) (skyL * pre));
        g.glUniform1f(g.glGetUniformLocation(p, "uBase"), (float) lightning.cloudBase);
        g.glUniform1f(g.glGetUniformLocation(p, "uCover"), (float) cover);
        g.glUniform1f(g.glGetUniformLocation(p, "uTime"), (float) (t % 1000));
        g.glUniform1f(g.glGetUniformLocation(p, "uRough"), (float) rough);
        g.glUniform1f(g.glGetUniformLocation(p, "uSigma"), (float) sigma);
        g.glUniform1i(g.glGetUniformLocation(p, "uPano"), pano);
        g.glUniform3f(g.glGetUniformLocation(p, "uMast"), (float) mastX, (float) mastZ, (float) mastH);
        int n = gl.cloudLightCount();
        float[] pos = new float[16], in = new float[16];
        for (int i = 0; i < n; i++) {
            double[] c = gl.cloudLight(i);
            pos[i * 4] = (float) c[0];
            pos[i * 4 + 1] = (float) c[1];
            pos[i * 4 + 2] = (float) c[2];
            for (int k = 0; k < 3; k++) in[i * 4 + k] = (float) (c[3] * Lightning.CHANNEL_RGB[k] * pre);
        }
        g.glUniform1i(g.glGetUniformLocation(p, "uClCount"), n);
        g.glUniform4fv(g.glGetUniformLocation(p, "uClPos"), 4, pos, 0);
        g.glUniform4fv(g.glGetUniformLocation(p, "uClI"), 4, in, 0);
        gl.bind(g, p, pre, sigma);
    }

    /** Selbsttest der Grafikkarte (im GL-Faden). */
    public String check(GLAutoDrawable d) {
        Lightning.Snapshot s = lightning.snapshot();
        if (s.bolts().isEmpty()) return check;
        check = gl.selfCheck(d.getGL().getGL4(), s);
        return check;
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.dispose(g);
        g.glDeleteProgram(sceneProg);
        g.glDeleteProgram(postProg);
    }

    /** „Kräftig · 7 Blitze, 2 Erdblitze · Donner in 4,2 s“ */
    public String status() {
        Lightning.Thunder next = null;
        for (Lightning.Thunder th : lightning.pendingThunder()) if (next == null || th.time() < next.time()) next = th;
        String th = next != null ? String.format(Locale.GERMAN, " · Donner in %.1f s (%.1f km)", next.time() - lightning.time(), next.distance() / 1000) : "";
        Lightning.Thunder last = lightning.lastThunder();
        String lt = last != null && lightning.time() - last.time() < last.duration() ? String.format(Locale.GERMAN, " · es donnert (%.0f dB%s)", last.levelDb(), last.crack() ? ", Knall" : "") : "";
        return String.format(Locale.GERMAN, "%s · %d Blitze, %d Erdblitze, %d in den Mast%s%s", levelName(lightning.level()), lightning.flashes(), lightning.groundFlashes(), lightning.attractorHits(), th, lt);
    }

    public static String levelName(Lightning.Level l) {
        return switch (l) {
            case OFF -> "kein Gewitter";
            case DISTANT -> "Wetterleuchten";
            case MODERATE -> "mäßig";
            case STRONG -> "kräftig";
            case SEVERE -> "schwer";
        };
    }

    static String kindName(Bolt.Kind k) {
        return switch (k) {
            case CLOUD_GROUND -> "Erdblitz";
            case INTRA_CLOUD -> "Wolkenblitz";
            case SPIDER -> "Spinnenblitz";
            case SHEET -> "Wetterleuchten";
        };
    }

    // ------------------------------------------------------------------ Matrizen (spaltenweise, wie OpenGL)

    static float[] perspective(double fovy, double aspect, double n, double f) {
        double tt = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (tt / aspect);
        m[5] = (float) tt;
        m[10] = (float) ((f + n) / (n - f));
        m[11] = -1;
        m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] lookAtMatrix(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sy = 0, sz = fx, sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        sx /= sl; sy /= sl; sz /= sl;
        double ux = sy * fz - sz * fy, uy = sz * fx - sx * fz, uz = sx * fy - sy * fx;
        return new float[]{(float) sx, (float) ux, (float) -fx, 0, (float) sy, (float) uy, (float) -fy, 0, (float) sz, (float) uz, (float) -fz, 0,
                (float) -(sx * e[0] + sy * e[1] + sz * e[2]), (float) -(ux * e[0] + uy * e[1] + uz * e[2]), (float) (fx * e[0] + fy * e[1] + fz * e[2]), 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = (float) s;
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

    static String read(String file) {
        try (java.io.InputStream in = LightningScene.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("lightning demo: Shader fehlt: " + file);
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
