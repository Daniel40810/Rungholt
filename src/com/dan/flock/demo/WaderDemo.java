package com.dan.flock.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.flock.WaderSpecies;
import com.dan.flock.Waders;
import com.dan.flock.gl.WaderGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorschau der Wattvögel (seit Rungholt Phase 8): ein Stück Watt ({@link DemoWatt}) im Zeitraffer einer Tide mit allen
 * Arten; „Aufscheuchen“ lässt den größten Strandläufer-Schwarm wenden. Ohne Fenster: {@code --bild datei.png} schreibt
 * die Arten von nah ({@code datei.png}), den wendenden Schwarm ({@code datei_schwarm.png}) und eine Tide als Karte
 * ({@code datei_karte.png}).
 */
public final class WaderDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(91, 167, 179);

    final DemoWatt watt = new DemoWatt();
    final Waders waders;
    final WaderGl wgl = new WaderGl();
    volatile double[] eye = {-300, 3, 200}, look = {-100, 0, 0};
    volatile double fovy = 35, lapse = 60, sunAlt = 35, sunAz = 200;
    volatile boolean running = true;
    private int groundProg, waterProg, postProg, gvao, gvbo, gcount, emptyVao, fbo, colTex, depTex, fw, fh;
    private long last = System.nanoTime();

    WaderDemo(int month) {
        waders = DemoWatt.waders(watt, month, 1);
        watt.t = 4 * 3600;
        waders.settle();
    }

    // ------------------------------------------------------------ GL

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        wgl.init(gl);
        groundProg = prog(gl, GROUND_VS, GROUND_FS);
        waterProg = prog(gl, WATER_VS, WATER_FS);
        postProg = prog(gl, POST_VS, POST_FS);
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        gvao = t[0];
        emptyVao = t[1];
        gl.glGenBuffers(1, t, 0);
        gvbo = t[0];
        int n = 200;
        float[] v = new float[n * n * 6 * 6];
        int k = 0;
        double x0 = -800, z0 = -800, c = 1700.0 / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] p : q) {
                    double x = x0 + p[0] * c, z = z0 + p[1] * c, y = watt.ground(x, z);
                    double dx = watt.ground(x + 1, z) - watt.ground(x - 1, z), dz = watt.ground(x, z + 1) - watt.ground(x, z - 1), nl = Math.sqrt(dx * dx / 4 + 1 + dz * dz / 4);
                    v[k++] = (float) x; v[k++] = (float) y; v[k++] = (float) z;
                    v[k++] = (float) (-dx / 2 / nl); v[k++] = (float) (1 / nl); v[k++] = (float) (-dz / 2 / nl);
                }
            }
        gcount = k / 6;
        gl.glBindVertexArray(gvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gvbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, k * 4L, Buffers.newDirectFloatBuffer(v, 0, k), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glBindVertexArray(0);
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void display(GLAutoDrawable d) {
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (running) { watt.t += dt * lapse; waders.step(dt * lapse); }
        render(d);
    }

    void render(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double se = Math.toRadians(sunAlt), sa = Math.toRadians(sunAz);
        double[] sun = {Math.cos(se) * Math.sin(sa), Math.sin(se), -Math.cos(se) * Math.cos(sa)};
        double si = 3.2 * smooth(-0.02, 0.15, sun[1]);
        double[] sunC = {si, si * 0.93, si * 0.85};
        double sky = 0.15 + 0.85 * smooth(-0.1, 0.4, sun[1]);
        double[] top = {0.20 * sky, 0.32 * sky, 0.55 * sky}, hor = {0.55 * sky, 0.62 * sky, 0.70 * sky};
        float[] vp = viewProj(w / (double) h);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor((float) hor[0], (float) hor[1], (float) hor[2], 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(groundProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(groundProg, "uVP"), 1, false, vp, 0);
        u3(gl, groundProg, "uSun", sun); u3(gl, groundProg, "uSunC", sunC); u3(gl, groundProg, "uTop", top); u3(gl, groundProg, "uHor", hor); u3(gl, groundProg, "uCam", eye);
        gl.glUniform1f(gl.glGetUniformLocation(groundProg, "uLevel"), (float) watt.level());
        gl.glBindVertexArray(gvao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, gcount);
        WaderGl.View v = new WaderGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        WaderGl.Light l = new WaderGl.Light();
        l.sunDir = sun; l.sunColor = sunC; l.skyTop = top; l.skyHorizon = hor; l.fogDistance = 8000;
        Waders r = render != null ? render : waders;
        wgl.draw(gl, r, v, l);
        gl.glUseProgram(waterProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(waterProg, "uVP"), 1, false, vp, 0);
        gl.glUniform1f(gl.glGetUniformLocation(waterProg, "uLevel"), (float) watt.level());
        u3(gl, waterProg, "uSun", sun); u3(gl, waterProg, "uSunC", sunC); u3(gl, waterProg, "uTop", top); u3(gl, waterProg, "uHor", hor); u3(gl, waterProg, "uCam", eye);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(postProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glUniform1i(gl.glGetUniformLocation(postProg, "uHdr"), 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) { gl.glDeleteFramebuffers(1, new int[]{fbo}, 0); gl.glDeleteTextures(2, new int[]{colTex, depTex}, 0); }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        colTex = t[0];
        depTex = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, w, h);
        gl.glGenFramebuffers(1, t, 0);
        fbo = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, colTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        fw = w;
        fh = h;
    }

    float[] viewProj(double aspect) {
        double[] f = norm(new double[]{look[0] - eye[0], look[1] - eye[1], look[2] - eye[2]}), r = norm(cross(f, new double[]{0, 1, 0})), u = cross(r, f);
        double n = 0.05, fa = 6000, t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        double[] view = {r[0], u[0], -f[0], 0, r[1], u[1], -f[1], 0, r[2], u[2], -f[2], 0, -dot(r, eye), -dot(u, eye), dot(f, eye), 1};
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

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    static void u3(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) v[0], (float) v[1], (float) v[2]); }

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null);
            gl.glCompileShader(s);
            gl.glAttachShader(p, s);
        }
        gl.glLinkProgram(p);
        return p;
    }

    BufferedImage grab(GLAutoDrawable a) {
        GL4 gl = a.getGL().getGL4();
        int w = a.getSurfaceWidth(), h = a.getSurfaceHeight();
        java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultDrawFramebuffer();
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    @Override public void dispose(GLAutoDrawable d) { wgl.dispose(d.getGL().getGL4()); }

    static final String GROUND_VS = "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNrm; uniform mat4 uVP; out vec3 vW, vN; void main(){ vW=aPos; vN=aNrm; gl_Position=uVP*vec4(aPos,1.0); }\n";
    static final String GROUND_FS = """
            in vec3 vW, vN; out vec4 frag; uniform vec3 uSun, uSunC, uTop, uHor, uCam; uniform float uLevel;
            void main() {
                vec3 N = normalize(vN);
                bool marsh = vW.x > 700.0;
                vec3 c0 = marsh ? vec3(0.09, 0.13, 0.05) : vec3(0.30, 0.25, 0.17) * (0.95 + 0.08 * sin(vW.x * 30.0 + sin(vW.z * 3.0)));
                float wet = smoothstep(0.8, 0.0, vW.y - uLevel);
                c0 *= mix(1.0, 0.6, wet * (marsh ? 0.0 : 1.0));
                vec3 c = c0 * (uSunC * max(dot(N, uSun), 0.0) / 3.14159 + mix(uHor, uTop, 0.5));
                vec3 V = normalize(uCam - vW), R = reflect(-V, N);
                c += mix(uHor, uTop, clamp(R.y, 0.0, 1.0)) * wet * 0.5 * pow(1.0 - max(dot(N, V), 0.0), 4.0) * (marsh ? 0.0 : 1.0);
                frag = vec4(mix(c, uHor, 1.0 - exp(-length(vW - uCam) / 4000.0)), 1.0);
            }
            """;
    static final String WATER_VS = "uniform mat4 uVP; uniform float uLevel; out vec3 vW; void main(){ vec2 c = vec2((gl_VertexID & 1)==0?-1.0:1.0, (gl_VertexID & 2)==0?-1.0:1.0)*3000.0; vW=vec3(c.x,uLevel,c.y); gl_Position=uVP*vec4(vW,1.0); }\n";
    static final String WATER_FS = """
            in vec3 vW; out vec4 frag; uniform vec3 uSun, uSunC, uTop, uHor, uCam;
            void main() {
                vec3 V = normalize(uCam - vW), N = normalize(vec3(0.015 * sin(vW.x * 1.1 + vW.z * 0.6), 1.0, 0.015 * cos(vW.z * 0.9)));
                vec3 R = reflect(-V, N);
                float f = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
                vec3 body = vec3(0.05, 0.06, 0.045) * (uSunC * max(uSun.y, 0.0) / 3.14159 + uTop);
                frag = vec4(mix(body, mix(uHor, uTop, clamp(R.y * 2.0, 0.0, 1.0)), f), mix(0.85, 1.0, f));
            }
            """;
    static final String POST_VS = "void main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    static final String POST_FS = "uniform sampler2D uHdr; out vec4 frag; void main(){ vec3 c = texelFetch(uHdr, ivec2(gl_FragCoord.xy), 0).rgb * 1.6; c = c / (1.0 + c); frag = vec4(pow(c, vec3(1.0/2.2)), 1.0); }\n";

    // ------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("--bild")) { bild(new File(a[1])); System.exit(0); }
        SwingUtilities.invokeLater(() -> new WaderDemo(9).open());
    }

    static GLOffscreenAutoDrawable offscreen(int w, int h) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        return GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
    }

    static void label(Graphics2D g, String s, int x, int y) {
        int tw = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(x, y, tw + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(s, x + 6, y + 16);
    }

    static Graphics2D sheetGraphics(BufferedImage im) {
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        return g;
    }

    static void bild(File out) throws Exception {
        File dir = out.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        int w = 480, h = 300;
        // 1) die Arten von nah: je Art eine kleine Gruppe am Saum bei Ebbe, Kamera davor
        WaderDemo demo = new WaderDemo(6);
        demo.running = false;
        GLOffscreenAutoDrawable d = offscreen(w, h);
        d.addGLEventListener(demo);
        int cols = 4;
        BufferedImage sheet = new BufferedImage(cols * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheetGraphics(sheet);
        int k = 0;
        for (WaderSpecies s : WaderSpecies.ALL) {
            Waders.Group gr = null;
            for (Waders.Group x : demo.waders.groups()) if (x.species == s) { gr = x; break; }
            if (gr == null) {   // Ringelgans im Juni nicht da: eigene Kolonie im Januar
                WaderDemo dj = new WaderDemo(1);
                dj.watt.t = demo.watt.t;
                for (Waders.Group x : dj.waders.groups()) if (x.species == s) { gr = x; break; }
                if (gr == null) continue;
                demo.tmp = dj.waders;
            }
            Waders.Bird b = gr.birds.get(gr.birds.size() / 2);
            double dist = 2.2 + s.length() * 6;
            demo.eye = new double[]{b.x - dist * 0.8, b.y + 0.35 + s.length() * 0.8, b.z + dist * 0.6};
            demo.look = new double[]{b.x, b.y + s.legLength() + 0.05, b.z};
            demo.fovy = 30;
            Waders keep = demo.waders;
            demo.render = demo.tmp != null ? demo.tmp : keep;
            d.display();
            final BufferedImage[] img = new BufferedImage[1];
            d.invoke(true, x -> { img[0] = demo.grab(x); return true; });
            demo.tmp = null;
            demo.render = null;
            int cx = (k % cols) * w, cy = (k / cols) * h;
            g.drawImage(img[0], cx, cy, null);
            label(g, s.label() + " · " + gr.mode.label, cx + 6, cy + 6);
            k++;
        }
        // Kachel 8: dieselbe Gruppe Alpenstrandläufer im Flug
        demo.waders.flush(-100, 0);
        for (int i = 0; i < 100; i++) demo.waders.step(0.2);
        Waders.Group fl = null;
        for (Waders.Group x : demo.waders.groups()) if (x.mode == Waders.Mode.SCHWARM) fl = x;
        if (fl != null) {
            double cx0 = 0, cy0 = 0, cz0 = 0;
            for (Waders.Bird b : fl.birds) { cx0 += b.x; cy0 += b.y; cz0 += b.z; }
            int n = fl.birds.size();
            demo.look = new double[]{cx0 / n, cy0 / n, cz0 / n};
            demo.eye = new double[]{cx0 / n - 60, 2, cz0 / n + 60};
            demo.fovy = 40;
            d.display();
            final BufferedImage[] img = new BufferedImage[1];
            d.invoke(true, x -> { img[0] = demo.grab(x); return true; });
            g.drawImage(img[0], 3 * w, h, null);
            label(g, "Alpenstrandläufer im Schwarm", 3 * w + 6, h + 6);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath());
        // 2) der wendende Schwarm: sechs Bilder im Abstand von 1 s von unten (die Bäuche blitzen)
        if (fl != null) {
            BufferedImage sw = new BufferedImage(3 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
            Graphics2D gs = sheetGraphics(sw);
            for (int f = 0; f < 6 && fl.mode == Waders.Mode.SCHWARM; f++) {
                for (int i = 0; i < 5; i++) demo.waders.step(0.2);
                double cx0 = 0, cy0 = 0, cz0 = 0, pos = 0, neg = 0;
                for (Waders.Bird b : fl.birds) { cx0 += b.x; cy0 += b.y; cz0 += b.z; if (b.bank > 0.35) pos++; else if (b.bank < -0.35) neg++; }
                int n = fl.birds.size();
                demo.look = new double[]{cx0 / n, cy0 / n, cz0 / n};
                demo.eye = new double[]{cx0 / n - 32, 1.8, cz0 / n + 20};
                demo.fovy = 24;
                d.display();
                final BufferedImage[] img = new BufferedImage[1];
                d.invoke(true, x -> { img[0] = demo.grab(x); return true; });
                gs.drawImage(img[0], (f % 3) * w, (f / 3) * h, null);
                label(gs, String.format(Locale.GERMAN, "%d s · %.0f %% nach rechts, %.0f %% nach links geneigt", f + 1, pos * 100 / n, neg * 100 / n), (f % 3) * w + 6, (f / 3) * h + 6);
            }
            gs.dispose();
            ImageIO.write(sw, "png", new File(out.getPath().replace(".png", "_schwarm.png")));
        }
        d.destroy();
        karte(new File(out.getPath().replace(".png", "_karte.png")));
    }

    Waders tmp, render;

    /** Eine Tide als Karte von oben: Wasser, Watt, Salzwiese, Gruppen nach Art und Zustand. */
    static void karte(File out) throws Exception {
        DemoWatt w = new DemoWatt();
        Waders v = DemoWatt.waders(w, 9, 1);
        w.t = 0;
        v.settle();
        double[] hours = {0.5, 2.5, 4.0, 5.5, 6.2, 7.5, 9.0, 10.5, 11.8};
        int tw = 420, th = 330, cols = 3;
        BufferedImage sheet = new BufferedImage(cols * tw, 3 * th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheetGraphics(sheet);
        double t = 0;
        Color[] col = {new Color(250, 220, 120), new Color(30, 30, 30), new Color(255, 255, 255), new Color(200, 110, 60), new Color(170, 190, 210), new Color(230, 60, 50), new Color(60, 160, 60)};
        for (int k = 0; k < hours.length; k++) {
            while (t < hours[k] * 3600) { t += 5; w.t = t; v.step(5); }
            int ox = (k % cols) * tw, oy = (k / cols) * th;
            double x0 = -800, z0 = -600, sc = tw / 1700.0;
            BufferedImage img = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            for (int j = 0; j < th; j++)
                for (int i = 0; i < tw; i++) {
                    double x = x0 + i / sc, z = z0 + j / sc, gr = w.ground(x, z), dd = w.level() - gr;
                    int rgb = w.marsh(x, z) ? 0x3d5a2a : dd > 0 ? mix(0x6a9a92, 0x183c42, Math.min(1, dd / 2.5)) : mix(0x5a4a34, 0xb39a70, Math.min(1, -dd / 2));
                    img.setRGB(i, j, rgb);
                }
            g.drawImage(img, ox, oy, null);
            for (Waders.Group gr : v.groups()) {
                Color c = col[WaderSpecies.ALL.indexOf(gr.species)];
                g.setColor(c);
                for (Waders.Bird b : gr.birds) {
                    int px = ox + (int) ((b.x - x0) * sc), pz = oy + (int) ((b.z - z0) * sc);
                    if (px < ox || pz < oy || px >= ox + tw || pz >= oy + th) continue;
                    if (b.air) g.drawLine(px - 1, pz, px + 1, pz); else g.fillRect(px, pz, 1, 1);
                }
            }
            label(g, String.format(Locale.GERMAN, "%.1f h nach HW · Pegel %+.2f m · %s", hours[k], w.level(), census(v)), ox + 4, oy + 4);
        }
        // Legende
        int ly = 3 * th - 20 - 18 * WaderSpecies.ALL.size();
        for (int i = 0; i < WaderSpecies.ALL.size(); i++) {
            g.setColor(col[i]);
            g.fillRect(8, ly + i * 18, 12, 12);
            g.setColor(Color.WHITE);
            g.drawString(WaderSpecies.ALL.get(i).label(), 26, ly + i * 18 + 11);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath() + " · " + v.describe() + " · Abflüge " + v.takeoffs);
    }

    static String census(Waders v) {
        int[] c = v.census();
        return String.format(Locale.GERMAN, "%d fressen, %d rasten, %d fliegen", c[0], c[1], c[2] + c[3]);
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        FFrame f = new FFrame("Wattvögel · Vorschau des Packages com.dan.flock");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1400, 840));
        f.setSize(1400, 840);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("WATTVÖGEL");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        slider("Zeitraffer", 1, 600, 60, "×", v -> lapse = v);
        slider("Blick: Abstand", 30, 900, 300, " m", v -> eye = new double[]{-100 - v, 3 + v * 0.06, v * 0.6});
        com.dan.fbutton.FButton flush = new com.dan.fbutton.FButton("Aufscheuchen");
        flush.addActionListener(e -> waders.flush(-100, 0));
        add(flush);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(250, e -> info.setText(String.format(Locale.GERMAN, "<html><body style='width:230px'>Pegel %+.2f m · %.1f h nach Hochwasser<br>%s</body></html>",
                watt.level(), (watt.t % DemoWatt.PERIOD) / 3600, waders.describe()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer set) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(l);
        FSlider s = new FSlider(min, max, val);
        s.addChangeListener(e -> { l.setText(name + "  " + s.getValue() + unit); set.accept(s.getValue()); });
        add(s);
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }
}
