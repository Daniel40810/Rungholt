package com.dan.sky.demo;

import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

/**
 * Die Szene der Demo: ein oder mehrere Himmel nebeneinander (je ein Bildausschnitt mit eigenem Blick), darunter
 * eine schlichte Wasserfläche. Augenanpassung wie in einer App: halb an den ganzen Himmel, halb an das Blickfeld,
 * über eine Sekunde geglättet. Ohne Leuchtturm-Code, nur {@code com.dan.sky} und JOGL.
 */
final class SkyScene implements GLEventListener {
    final Sky[] skies;
    final SkyGl[] gls;
    /** Blick je Himmel: Azimut und Höhe der Blickrichtung in Grad. */
    final double[] yaw, pitch;
    volatile double fovy = 60;
    volatile boolean frozen;
    /** Zeitraffer: Szenensekunden je Sekunde. */
    volatile double lapse = 1;
    volatile String status = "";
    private final double[] adapt;
    private long last;
    private double time;
    private int hdrTex, fbo, fw, fh;
    String selfCheck = "";

    SkyScene(Sky... skies) {
        this.skies = skies;
        gls = new SkyGl[skies.length];
        yaw = new double[skies.length];
        pitch = new double[skies.length];
        adapt = new double[skies.length];
        for (int i = 0; i < gls.length; i++) {
            gls[i] = new SkyGl();
            yaw[i] = 180;
            pitch[i] = 12;
        }
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (SkyGl g : gls) g.init(gl);
        last = System.nanoTime();
    }

    double[] forward(int i) {
        double y = Math.toRadians(yaw[i]), p = Math.toRadians(pitch[i]);
        return new double[]{Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p)};
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) time += dt;
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        int n = skies.length, pw = w / n;
        double[] scot = new double[n];
        for (int i = 0; i < n; i++) {
            Sky s = skies[i];
            if (!frozen) s.epochMillis += dt * lapse * 1000;
            Sky.State st = frozen ? s.updateNow() : s.step(dt);
            double[] f = forward(i);
            double view = st.viewLuminance(f, new double[]{0, 1, 0}, fovy, pw / (double) h);
            double target = Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, view));
            adapt[i] = adapt[i] <= 0 || frozen ? target : Math.exp(Math.log(adapt[i]) + (Math.log(target) - Math.log(adapt[i])) * Math.min(1, dt / 1.0));
            gls[i].update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
            gl.glViewport(i * pw, 0, pw, h);
            SkyGl.View v = new SkyGl.View();
            float[] proj = perspective(fovy, pw / (double) h);
            v.viewProj = mul(proj, lookAt(f));
            v.invViewProj = invert(v.viewProj);
            v.reversedZ = false;
            v.width = pw;
            v.height = h;
            v.fovyDeg = fovy;
            v.preExposure = Sky.State.exposureFor(adapt[i], 0.85);
            v.time = time;
            v.sea = true;
            gls[i].draw(gl, st, v);
            scot[i] = st.scotopic();
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        for (int i = 0; i < n; i++) {
            gl.glViewport(0, 0, w, h);
            gl.glEnable(GL.GL_SCISSOR_TEST);
            gl.glScissor(i * pw, 0, pw, h);
            gls[0].toneMap(gl, hdrTex, 1, scot[i]);
            gl.glDisable(GL.GL_SCISSOR_TEST);
        }
        status = skies[0].state().describe();
    }

    /** Selbsttest Grafikkarte gegen Prozessor für den ersten Himmel (im GL-Faden nach einem Bild). */
    String check(GLAutoDrawable d) {
        selfCheck = gls[0].selfCheck(d.getGL().getGL4(), skies[0].state());
        return selfCheck;
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{hdrTex}, 0);
        }
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        hdrTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        fw = w;
        fh = h;
    }

    // ------------------------------------------------------------------ Matrizen (spaltenweise)

    static float[] perspective(double fovy, double aspect) {
        double f = 1 / Math.tan(Math.toRadians(fovy) / 2), n = 0.1, far = 1000;
        float[] m = new float[16];
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + n) / (n - far));
        m[11] = -1;
        m[14] = (float) (2 * far * n / (n - far));
        return m;
    }

    /** Blick vom Ursprung in Richtung f (nur Drehung; der Himmel liegt im Unendlichen). */
    static float[] lookAt(double[] f) {
        double[] up = {0, 1, 0};
        double[] r = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rn = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        for (int i = 0; i < 3; i++) r[i] /= rn;
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        return new float[]{(float) r[0], (float) u[0], (float) -f[0], 0, (float) r[1], (float) u[1], (float) -f[1], 0,
                (float) r[2], (float) u[2], (float) -f[2], 0, 0, 0, 0, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                m[c * 4 + r] = s;
            }
        return m;
    }

    /** Inverse einer 4×4-Matrix (Gauß-Jordan, spaltenweise). */
    static float[] invert(float[] m) {
        double[][] a = new double[4][8];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r];
            a[r][4 + r] = 1;
        }
        for (int c = 0; c < 4; c++) {
            int p = c;
            for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c];
            a[c] = a[p];
            a[p] = t;
            double d = a[c][c];
            for (int k = 0; k < 8; k++) a[c][k] /= d;
            for (int r = 0; r < 4; r++)
                if (r != c) {
                    double x = a[r][c];
                    for (int k = 0; k < 8; k++) a[r][k] -= x * a[c][k];
                }
        }
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) a[r][4 + c];
        return out;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        for (SkyGl g : gls) g.dispose(gl);
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{hdrTex}, 0);
        }
    }
}
