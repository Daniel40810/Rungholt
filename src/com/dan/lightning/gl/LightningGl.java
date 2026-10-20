package com.dan.lightning.gl;

import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Blitze auf der Grafikkarte (OpenGL 4.3, ohne Compute-Shader).
 * <ol>
 * <li>{@link #update}: die Stücke neuer Blitze einmal in einen Speicherpuffer; je Bild nur die Helligkeiten.</li>
 * <li>{@link #drawChannels}: die Kanäle als Bänder quer zum Blick, additiv (Kern und Hof).</li>
 * <li>{@link #drawGlow}: das Leuchten der Luft um den Blitz (Einfachstreuung, geschlossen gerechnet).</li>
 * <li>{@link #drawPanorama}: Kanäle und Leuchten ins Rundumbild, damit See und Scheiben sie spiegeln.</li>
 * <li>{@link #bind}: Lichtpunkte für fremde Shader mit dem Baustein {@code lightning.glsl}.</li>
 * </ol>
 * Alles, was ins Bild geht, ist linear und vorbelichtet (View.preExposure) und wird mit ONE, ONE addiert.
 */
public final class LightningGl {

    /** Blick und Ziel. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public double[] eye = {0, 2, 0};
        public boolean reversedZ = true;
        public int width = 1, height = 1;
        public double fovyDeg = 50;
        /** Tiefe der Szene (0: keine). Für das Leuchten eine Kopie, die nicht das Ziel ist. */
        public int depthTexture;
        /** Tiefentest gegen den Tiefenpuffer des Ziels (die Kanäle werden von der Szene verdeckt). */
        public boolean depthTest;
        public int targetFbo;
        public double preExposure = 1;
        public double maxDistance = 60_000;
        /** Auslöschung und Streuung der Luft in 1/m (Dunst, Regen, Gischt). */
        public double extinction = 1e-4, scatter = 0.8e-4;
        /** Wolkenuntergrenze in m (≤ 0: keine): dort endet das Leuchten der Luft. */
        public double ceiling;
    }

    private int boltProg, glowProg, checkProg, vao, ssbo;
    private long ssboBytes;
    private final Map<Bolt, Integer> offsets = new IdentityHashMap<>();
    private List<Bolt> uploaded = List.of();
    private Lightning.Snapshot snap;
    private final float[] lightPos = new float[32], lightRgb = new float[32], cloudPos = new float[16], cloudI = new float[4];
    private int lightCount, cloudCount;
    private double cloudBase = -1;
    private boolean ready;

    public boolean ready() { return ready; }

    public void init(GL4 gl) {
        String fs = read("fullscreen.vert");
        boltProg = program(gl, read("bolt.vert"), read("bolt.frag"));
        glowProg = program(gl, fs, read("glow.frag"));
        checkProg = program(gl, fs, read("check.frag"));
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenBuffers(1, v, 0);
        ssbo = v[0];
        ready = true;
    }

    /**
     * Den Zustand übernehmen: neue Blitze in den Speicherpuffer, Lichtpunkte für Szene und Wolken rechnen. Licht aus
     * der Wolke wird für die Szene an die Untergrenze gesetzt und gedämpft (35 % kommen gestreut heraus).
     */
    public void update(GL4 gl, Lightning.Snapshot s) {
        snap = s;
        cloudBase = s.cloudBase();
        if (!sameBolts(s.bolts())) upload(gl, s.bolts());
        List<Lightning.Light> ls = s.lights(4, 8);
        lightCount = 0;
        for (Lightning.Light l : ls) {
            int i = lightCount++;
            double v = l.visible(), y = l.y();
            double cd = l.candela() * (v + Lightning.escape(y, cloudBase) * (1 - v));
            if (v < 0.5 && cloudBase > 0 && y > cloudBase) y = cloudBase;
            lightPos[i * 4] = (float) l.x();
            lightPos[i * 4 + 1] = (float) y;
            lightPos[i * 4 + 2] = (float) l.z();
            // Größe der Lichtquelle: ein Stück Kanal einige zehn Meter, Licht, das aus der Wolke kommt, eine
            // leuchtende Fläche von rund einem Kilometer
            lightPos[i * 4 + 3] = (float) (30 + (1 - v) * 900);
            for (int k = 0; k < 3; k++) lightRgb[i * 4 + k] = (float) (cd * Lightning.CHANNEL_RGB[k]);
        }
        cloudCount = 0;
        for (Lightning.Light l : ls) {
            if (cloudCount == 4) break;
            int i = cloudCount++;
            cloudPos[i * 4] = (float) l.x();
            cloudPos[i * 4 + 1] = (float) l.y();
            cloudPos[i * 4 + 2] = (float) l.z();
            cloudI[i] = (float) l.candela();
        }
    }

    private boolean sameBolts(List<Bolt> bs) {
        if (bs.size() != uploaded.size()) return false;
        for (int i = 0; i < bs.size(); i++) if (bs.get(i) != uploaded.get(i)) return false;
        return true;
    }

    /** Nach {@link Lightning#updateVisibility()} die Sichtbarkeit neu hochladen. */
    public void invalidate() { uploaded = List.of(); }

    private void upload(GL4 gl, List<Bolt> bs) {
        int total = 0;
        for (Bolt b : bs) total += b.n;
        FloatBuffer fb = Buffers.newDirectFloatBuffer(Math.max(8, total * 8));
        offsets.clear();
        int off = 0;
        for (Bolt b : bs) {
            offsets.put(b, off);
            for (int i = 0; i < b.n; i++) {
                fb.put(b.ax[i]).put(b.ay[i]).put(b.az[i]).put(b.path[i]);
                fb.put(b.bx[i]).put(b.by[i]).put(b.bz[i]).put(b.order[i] + 0.5f * Math.min(0.999f, b.visible[i]));
            }
            off += b.n;
        }
        fb.flip();
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, ssbo);
        long bytes = (long) Math.max(8, total * 8) * 4;
        if (bytes > ssboBytes) {
            ssboBytes = Math.max(bytes, ssboBytes * 2);
            gl.glBufferData(GL4.GL_SHADER_STORAGE_BUFFER, ssboBytes, null, GL.GL_DYNAMIC_DRAW);
        }
        gl.glBufferSubData(GL4.GL_SHADER_STORAGE_BUFFER, 0, (long) fb.remaining() * 4, fb);
        gl.glBindBuffer(GL4.GL_SHADER_STORAGE_BUFFER, 0);
        uploaded = List.copyOf(bs);
    }

    /** Ob gerade etwas zu zeichnen ist. */
    public boolean active() { return snap != null && !snap.bolts().isEmpty(); }

    // ------------------------------------------------------------------ Kanäle

    /** Die Kanäle über das Ziel (additiv). */
    public void drawChannels(GL4 gl, View v) {
        if (!active()) return;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, v.targetFbo);
        gl.glViewport(0, 0, v.width, v.height);
        double pixelAngle = 2 * Math.tan(Math.toRadians(v.fovyDeg) / 2) / v.height;
        drawBolts(gl, v.viewProj, v.eye, v.width, v.height, pixelAngle, v.preExposure, v.extinction, v.scatter, false, v.depthTest, v.reversedZ);
    }

    private void drawBolts(GL4 gl, float[] vp, double[] eye, int w, int h, double pixelAngle, double pre, double sigma, double scatter,
                           boolean pano, boolean depth, boolean reversed) {
        if (depth) {
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversed ? GL.GL_GREATER : GL.GL_LESS);
        } else gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glUseProgram(boltProg);
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, ssbo);
        if (vp != null) gl.glUniformMatrix4fv(loc(boltProg, gl, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(loc(boltProg, gl, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform2f(loc(boltProg, gl, "uViewport"), w, h);
        gl.glUniform1f(loc(boltProg, gl, "uPixelAngle"), (float) pixelAngle);
        gl.glUniform1f(loc(boltProg, gl, "uSigma"), (float) sigma);
        gl.glUniform1f(loc(boltProg, gl, "uScatter"), (float) scatter);
        gl.glUniform1i(loc(boltProg, gl, "uPano"), pano ? 1 : 0);
        gl.glUniform3f(loc(boltProg, gl, "uColour"), (float) Lightning.CHANNEL_RGB[0], (float) Lightning.CHANNEL_RGB[1], (float) Lightning.CHANNEL_RGB[2]);
        gl.glBindVertexArray(vao);
        double cpm = snap.candelaPerMetre() * pre;
        for (Bolt b : snap.bolts()) {
            Integer off = offsets.get(b);
            if (off == null) continue;
            double t = snap.time() - b.t0;
            if (t < 0) continue;
            double main = b.mainLevel(t), b1 = b.branchLevel(t, 1), b2 = b.branchLevel(t, 2), b3 = b.branchLevel(t, 3);
            double lead = b.leaderLevel(t);
            if (main + b1 + lead <= 1e-6) continue;
            gl.glUniform1i(loc(boltProg, gl, "uBase"), off);
            gl.glUniform4f(loc(boltProg, gl, "uLevel"), (float) (cpm * main), (float) (cpm * b1), (float) (cpm * b2), (float) (cpm * b3));
            gl.glUniform2f(loc(boltProg, gl, "uLeader"), (float) b.leaderFront(t), (float) (cpm * lead));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, b.n * 6);
        }
        gl.glBindBufferBase(GL4.GL_SHADER_STORAGE_BUFFER, 0, 0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(true);
        gl.glUseProgram(0);
    }

    // ------------------------------------------------------------------ Leuchten der Luft

    /** Das Leuchten der Luft um die Blitze über das Ziel (additiv), bis zur Szene (Tiefe) oder zur Wolkenuntergrenze. */
    public void drawGlow(GL4 gl, View v) {
        if (!active() || lightCount == 0 || v.scatter <= 0) return;
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, v.targetFbo);
        gl.glViewport(0, 0, v.width, v.height);
        glow(gl, v.invViewProj, v.eye, v.depthTexture, v.reversedZ, false, v.maxDistance, v.preExposure, v.extinction, v.scatter, v.ceiling);
    }

    private void glow(GL4 gl, float[] inv, double[] eye, int depth, boolean reversed, boolean pano, double maxDist, double pre,
                      double sigma, double scatter, double ceiling) {
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glUseProgram(glowProg);
        if (inv != null) gl.glUniformMatrix4fv(loc(glowProg, gl, "uInvVP"), 1, false, inv, 0);
        gl.glUniform3f(loc(glowProg, gl, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depth);
        gl.glUniform1i(loc(glowProg, gl, "uDepth"), 0);
        gl.glUniform1i(loc(glowProg, gl, "uUseDepth"), depth != 0 && !pano ? 1 : 0);
        gl.glUniform1i(loc(glowProg, gl, "uReversed"), reversed ? 1 : 0);
        gl.glUniform1i(loc(glowProg, gl, "uPano"), pano ? 1 : 0);
        gl.glUniform1f(loc(glowProg, gl, "uMaxDist"), (float) maxDist);
        gl.glUniform1f(loc(glowProg, gl, "uScatter"), (float) scatter);
        gl.glUniform1f(loc(glowProg, gl, "uSigma"), (float) sigma);
        gl.glUniform1f(loc(glowProg, gl, "uCeil"), (float) ceiling);
        gl.glUniform1i(loc(glowProg, gl, "uCount"), lightCount);
        gl.glUniform4fv(loc(glowProg, gl, "uPos"), 8, lightPos, 0);
        float[] rgb = new float[32];
        for (int i = 0; i < 32; i++) rgb[i] = (float) (lightRgb[i] * pre);
        gl.glUniform4fv(loc(glowProg, gl, "uRgb"), 8, rgb, 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
    }

    // ------------------------------------------------------------------ Rundumbild

    /**
     * Kanäle und Leuchten in ein Rundumbild (u = ½ + Azimut von Nord / 2π, v = ½ + Höhe / π), das gerade als Ziel
     * gebunden ist (Größe w × h), gesehen vom Ort eye. So spiegeln See und Scheiben den Blitz.
     */
    public void drawPanorama(GL4 gl, int w, int h, double[] eye, double pre, double sigma, double scatter, double ceiling) {
        if (!active()) return;
        int[] vp = new int[4];
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glViewport(0, 0, w, h);
        if (scatter > 0 && lightCount > 0) glow(gl, null, eye, 0, true, true, 60_000, pre, sigma, scatter, ceiling);
        drawBolts(gl, null, eye, w, h, 2 * Math.PI / w, pre, sigma, scatter, true, false, true);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
    }

    // ------------------------------------------------------------------ für fremde Shader

    /** Setzt die Uniforms von {@code lightning.glsl} im (gerade benutzten) Programm prog; pre = Vorbelichtung. */
    public void bind(GL4 gl, int prog, double pre, double sigma) {
        float[] rgb = new float[32];
        for (int i = 0; i < 32; i++) rgb[i] = (float) (lightRgb[i] * pre);
        gl.glUniform1i(loc(prog, gl, "ltgCount"), active() ? lightCount : 0);
        gl.glUniform4fv(loc(prog, gl, "ltgPos"), 8, lightPos, 0);
        gl.glUniform4fv(loc(prog, gl, "ltgRgb"), 8, rgb, 0);
        gl.glUniform1f(loc(prog, gl, "ltgSigma"), (float) sigma);
    }

    /** Lichtpunkte für fremde Shader ohne Baustein (z. B. ein Meer mit eigenen Punktlichtern): Zahl. */
    public int lightCount() { return active() ? lightCount : 0; }

    /** Ort des Lichtpunkts i für die Szene (Licht aus der Wolke an die Untergrenze gesetzt) und seine Größe (m). */
    public double[] lightPos(int i) { return new double[]{lightPos[i * 4], lightPos[i * 4 + 1], lightPos[i * 4 + 2], lightPos[i * 4 + 3]}; }

    /** Lichtstärke des Lichtpunkts i als RGB in cd (ohne Vorbelichtung). */
    public double[] lightRgb(int i) { return new double[]{lightRgb[i * 4], lightRgb[i * 4 + 1], lightRgb[i * 4 + 2]}; }

    /** Lichtpunkte für das Leuchten von Wolken von innen (ungedämpft, am Ort im Kanal): Zahl. */
    public int cloudLightCount() { return active() ? cloudCount : 0; }

    /** Ort und Lichtstärke (cd) des Wolkenlichts i: x, y, z, cd. */
    public double[] cloudLight(int i) { return new double[]{cloudPos[i * 4], cloudPos[i * 4 + 1], cloudPos[i * 4 + 2], cloudI[i]}; }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Vergleicht die Grafikkarte mit dem Prozessor: die Beleuchtungsstärke aus {@code lightning.glsl} an 64 Orten und
     * die Lichtmenge eines gezeichneten Kanals (Summe der Leuchtdichte mal Raumwinkel aller Bildpunkte) mit seiner
     * Beleuchtungsstärke am Auge.
     */
    public String selfCheck(GL4 gl, Lightning.Snapshot s) {
        update(gl, s);
        int[] prev = new int[1], vpo = new int[4], va = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prev, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vpo, 0);
        gl.glGetIntegerv(GL2ES3.GL_VERTEX_ARRAY_BINDING, va, 0);
        int[] t = new int[2], f = new int[2];
        gl.glGenTextures(2, t, 0);
        gl.glGenFramebuffers(2, f, 0);
        // 1 Beleuchtungsstärke an 64 Orten
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, 8, 8);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[0], 0);
        gl.glViewport(0, 0, 8, 8);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glUseProgram(checkProg);
        bind(gl, checkProg, 1, 2e-4);
        float[] P = new float[256], N = new float[256];
        java.util.Random rnd = new java.util.Random(5);
        for (int i = 0; i < 64; i++) {
            double a = rnd.nextDouble() * 2 * Math.PI, r = 200 + rnd.nextDouble() * 8000;
            P[i * 4] = (float) (Math.sin(a) * r);
            P[i * 4 + 1] = (float) (rnd.nextDouble() * 40);
            P[i * 4 + 2] = (float) (-Math.cos(a) * r);
            double[] n = {rnd.nextGaussian(), Math.abs(rnd.nextGaussian()) + 0.3, rnd.nextGaussian()};
            double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
            for (int k = 0; k < 3; k++) N[i * 4 + k] = (float) (n[k] / l);
        }
        gl.glUniform4fv(loc(checkProg, gl, "uP"), 64, P, 0);
        gl.glUniform4fv(loc(checkProg, gl, "uN"), 64, N, 0);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        FloatBuffer fb = Buffers.newDirectFloatBuffer(64 * 4);
        gl.glReadPixels(0, 0, 8, 8, GL.GL_RGBA, GL.GL_FLOAT, fb);
        double worst = 0, scale = 0;
        double[] cpu = new double[64];
        for (int i = 0; i < 64; i++) {
            double e = 0;
            for (int k = 0; k < lightCount(); k++) {
                double lx = lightPos[k * 4] - P[i * 4], ly = lightPos[k * 4 + 1] - P[i * 4 + 1], lz = lightPos[k * 4 + 2] - P[i * 4 + 2];
                double R = lightPos[k * 4 + 3], d2 = lx * lx + ly * ly + lz * lz + R * R, d = Math.sqrt(lx * lx + ly * ly + lz * lz + 1);
                double cos = Math.max(0, (lx * N[i * 4] + ly * N[i * 4 + 1] + lz * N[i * 4 + 2]) / d);
                e += lightRgb[k * 4 + 1] * cos * Math.exp(-2e-4 * d) / d2;
            }
            cpu[i] = e;
            scale = Math.max(scale, e);
        }
        for (int i = 0; i < 64; i++) worst = Math.max(worst, Math.abs(fb.get(i * 4 + 1) - cpu[i]) / Math.max(cpu[i], scale * 1e-3 + 1e-30));
        // 2 Lichtmenge eines Kanals: Blick von 3 km quer auf den Blitz, ohne Dunst
        String chan = "";
        if (!s.bolts().isEmpty()) {
            Bolt b = s.bolts().get(0);
            int W = 512, H = 512;
            gl.glBindTexture(GL.GL_TEXTURE_2D, t[1]);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA32F, W, H);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[1]);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[1], 0);
            gl.glViewport(0, 0, W, H);
            gl.glClearColor(0, 0, 0, 0);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            double[] c = {0, 0, 0};
            double minX = 1e30, maxX = -1e30, minY = 1e30, maxY = -1e30, minZ = 1e30, maxZ = -1e30;
            for (int i = 0; i < b.n; i++) {
                minX = Math.min(minX, Math.min(b.ax[i], b.bx[i])); maxX = Math.max(maxX, Math.max(b.ax[i], b.bx[i]));
                minY = Math.min(minY, Math.min(b.ay[i], b.by[i])); maxY = Math.max(maxY, Math.max(b.ay[i], b.by[i]));
                minZ = Math.min(minZ, Math.min(b.az[i], b.bz[i])); maxZ = Math.max(maxZ, Math.max(b.az[i], b.bz[i]));
            }
            c[0] = (minX + maxX) / 2; c[1] = (minY + maxY) / 2; c[2] = (minZ + maxZ) / 2;
            double ext = Math.max(maxY - minY, Math.max(maxX - minX, maxZ - minZ));
            double dist = ext * 2.5;
            double[] eye = {c[0], c[1], c[2] + dist};
            double fov = 2 * Math.toDegrees(Math.atan(ext * 0.75 / dist));
            float[] vp = lookAt(eye, c, fov, 1);
            double pixelAngle = 2 * Math.tan(Math.toRadians(fov) / 2) / H;
            drawBolts(gl, vp, eye, W, H, pixelAngle, 1, 0, 0, false, false, false);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[1]);
            FloatBuffer img = Buffers.newDirectFloatBuffer(W * H * 4);
            gl.glReadPixels(0, 0, W, H, GL.GL_RGBA, GL.GL_FLOAT, img);
            double sum = 0, th = Math.tan(Math.toRadians(fov) / 2);
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++) {
                    double u = ((x + 0.5) / W * 2 - 1) * th, v = ((y + 0.5) / H * 2 - 1) * th;
                    double solid = Math.pow(2 * th / W, 2) / Math.pow(1 + u * u + v * v, 1.5);
                    sum += img.get((y * W + x) * 4 + 1) / Lightning.CHANNEL_RGB[1] * solid;
                }
            double e = 0, t0 = s.time() - b.t0;
            for (int i = 0; i < b.n; i++) {
                double mx = (b.ax[i] + b.bx[i]) * 0.5 - eye[0], my = (b.ay[i] + b.by[i]) * 0.5 - eye[1], mz = (b.az[i] + b.bz[i]) * 0.5 - eye[2];
                double d2 = mx * mx + my * my + mz * mz, d = Math.sqrt(d2);
                double cos = Math.abs(mz) / d;      // Fläche quer zur Blickachse
                e += s.candelaPerMetre() * b.segmentLevel(i, t0) * b.length(i) * b.visible[i] / d2 * cos;
            }
            chan = String.format(Locale.GERMAN, "; Kanal %+.1f %%", (sum / Math.max(1e-30, e) - 1) * 100);
        }
        gl.glDeleteFramebuffers(2, f, 0);
        gl.glDeleteTextures(2, t, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prev[0]);
        gl.glViewport(vpo[0], vpo[1], vpo[2], vpo[3]);
        gl.glBindVertexArray(va[0]);
        gl.glUseProgram(0);
        return String.format(Locale.GERMAN, "GPU = CPU Licht ± %.3f %%%s", worst * 100, chan);
    }

    /** Blickmatrix mal Projektion (OpenGL, Tiefe −1..1), für den Selbsttest und Demos. */
    public static float[] lookAt(double[] eye, double[] at, double fovyDeg, double aspect) {
        double[] f = sub(at, eye);
        f = nrm(f);
        double[] r = nrm(new double[]{-f[2], 0, f[0]});
        if (Math.abs(f[1]) > 0.999) r = new double[]{1, 0, 0};
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double[] view = {
                r[0], u[0], -f[0], 0,
                r[1], u[1], -f[1], 0,
                r[2], u[2], -f[2], 0,
                -(r[0] * eye[0] + r[1] * eye[1] + r[2] * eye[2]), -(u[0] * eye[0] + u[1] * eye[1] + u[2] * eye[2]), f[0] * eye[0] + f[1] * eye[1] + f[2] * eye[2], 1};
        double t = 1 / Math.tan(Math.toRadians(fovyDeg) / 2), n = 1, fa = 200_000;
        double[] proj = {t / aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (fa + n) / (n - fa), -1, 0, 0, 2 * fa * n / (n - fa), 0};
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += proj[k * 4 + rr] * view[c * 4 + k];
                m[c * 4 + rr] = (float) s;
            }
        return m;
    }

    static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }

    static double[] nrm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(boltProg);
        gl.glDeleteProgram(glowProg);
        gl.glDeleteProgram(checkProg);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        gl.glDeleteBuffers(1, new int[]{ssbo}, 0);
        ready = false;
        uploaded = List.of();
        ssboBytes = 0;
    }

    // ------------------------------------------------------------------ Shader

    private static int loc(int prog, GL4 gl, String name) { return gl.glGetUniformLocation(prog, name); }

    /** Liest eine Shader-Datei des Pakets und setzt {@code #include "datei"} aus demselben Ordner ein. */
    public static String read(String file) {
        String text;
        try (InputStream in = LightningGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("lightning: Shader-Datei fehlt: " + file);
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("lightning: " + file + ": " + e.getMessage(), e);
        }
        StringBuilder b = new StringBuilder();
        for (String l : text.split("\r?\n", -1)) {
            String t = l.trim();
            if (t.startsWith("#include")) b.append(read(t.substring(t.indexOf('"') + 1, t.lastIndexOf('"')))).append('\n');
            else b.append(l).append('\n');
        }
        return b.toString();
    }

    /** Übersetzt und bindet zwei Stufen (ohne #version; wird vorangestellt); auch für Demos. */
    public static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL2ES2.GL_VERTEX_SHADER, "#version 430 core\n" + vert, "vert"), fs = stage(gl, GL2ES2.GL_FRAGMENT_SHADER, "#version 430 core\n" + frag, "frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("lightning: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    private static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES2.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL2ES2.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            throw new IllegalStateException("lightning: Shader " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
