package com.dan.ocean.demo;

import com.dan.ocean.Ocean;
import com.dan.ocean.gl.Backdrop;
import com.dan.ocean.gl.OceanGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

/**
 * Die Szene der Demo: eine oder mehrere Seen nebeneinander (je ein Bildausschnitt), Himmel aus {@link Backdrop},
 * Kamera auf einer Kreisbahn um den Ursprung. Ohne Leuchtturm-Code, nur {@code com.dan.ocean} und JOGL.
 */
final class DemoScene implements GLEventListener {
    final Ocean[] oceans;
    private final OceanGl[] gls;
    private final Backdrop backdrop = new Backdrop();
    /** Kamera: Blickrichtung (Grad von Nord), Neigung (Grad nach unten), Abstand, Augenhöhe über dem Drehpunkt. */
    volatile double yaw = 250, pitch = 6, dist = 60, targetY = 2;
    volatile double sunAz = 210, sunEl = 22;
    volatile boolean frozen;
    volatile String status = "";
    private long last;
    private int hdrTex, depthTex, fbo, fw, fh;
    private int skyTex;
    private double skyAz = Double.NaN, skyEl = Double.NaN;
    String selfCheck = "";

    DemoScene(Ocean... oceans) {
        this.oceans = oceans;
        gls = new OceanGl[oceans.length];
        for (int i = 0; i < gls.length; i++) gls[i] = new OceanGl();
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (OceanGl g : gls) g.init(gl);
        backdrop.init(gl);
        last = System.nanoTime();
    }

    double[] sunDir() {
        double e = Math.toRadians(sunEl), a = Math.toRadians(sunAz);
        return new double[]{Math.cos(e) * Math.sin(a), Math.sin(e), -Math.cos(e) * Math.cos(a)};
    }

    double[] eye() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double fx = Math.sin(y) * Math.cos(p), fy = -Math.sin(p), fz = -Math.cos(y) * Math.cos(p);
        return new double[]{-fx * dist, targetY - fy * dist, -fz * dist};
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double[] sun = sunDir();
        double day = Math.max(0, Math.min(1, (sunEl + 2) / 20));
        double[] sunColor = {2.6 * day, 2.45 * day, 2.2 * day};
        if (sunAz != skyAz || sunEl != skyEl) {
            skyTex = backdrop.sky(gl, new double[]{0.16 * day + 0.01, 0.33 * day + 0.015, 0.70 * day + 0.04},
                    new double[]{0.60 * day + 0.03, 0.70 * day + 0.03, 0.82 * day + 0.05}, sun, sunColor);
            skyAz = sunAz;
            skyEl = sunEl;
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        int n = oceans.length, pw = w / n;
        double[] e = eye();
        for (int i = 0; i < n; i++) {
            Ocean o = oceans[i];
            if (!frozen) o.step(dt);
            o.waterLevel = 0;
            double floor = o.heightAt(e[0], e[2]) + 1.0;
            double[] eye = {e[0], Math.max(e[1], floor), e[2]};
            gls[i].update(gl, o);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glViewport(i * pw, 0, pw, h);
            float[] proj = Backdrop.perspective(55, pw / (double) h, 0.3, 80_000);
            float[] vp = Backdrop.mul(proj, Backdrop.lookAt(eye, new double[]{0, targetY + (eye[1] - e[1]), 0}));
            backdrop.drawSky(gl, Backdrop.invert(vp), skyTex);
            OceanGl.View v = new OceanGl.View();
            v.viewProj = vp;
            v.eye = eye;
            v.reversedZ = false;
            OceanGl.Light l = new OceanGl.Light();
            l.sunDir = sun;
            l.sunColor = sunColor;
            l.skyTexture = skyTex;
            gls[i].draw(gl, o, v, l);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        backdrop.toneMap(gl, hdrTex, (float) (0.9 / Math.max(0.05, day)));
        status = oceans[0].describe();
    }

    /** Selbsttest Grafikkarte gegen Prozessor für die erste See (im GL-Faden nach einem Bild). */
    String check(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        selfCheck = gls[0].selfCheck(gl, oceans[0]);
        return selfCheck;
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(2, new int[]{hdrTex, depthTex}, 0);
        }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        hdrTex = t[0];
        depthTex = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, w, h);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        fw = w;
        fh = h;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (OceanGl g : gls) g.dispose(gl);
        backdrop.dispose(gl);
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(2, new int[]{hdrTex, depthTex}, 0);
        }
    }
}
