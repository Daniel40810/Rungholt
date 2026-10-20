package com.dan.figure.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.figure.Hike;
import com.dan.figure.Outfit;
import com.dan.figure.Person;
import com.dan.figure.gl.FigureGl;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Vorschau der Figuren: eine geführte Wanderung über {@link DemoFlat} im Zeitraffer, die Kamera folgt der Gruppe.
 * Ohne Fenster: {@code --bild datei.png} schreibt Kleidung im Juli und im Januar, den Gang in acht Bildern, das Bücken
 * und Zeigen, und eine Gruppe am Halt ({@code datei.png}).
 */
public final class FigureDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(91, 167, 179);

    final DemoFlat flat = new DemoFlat();
    Hike hike;
    final FigureGl fgl = new FigureGl();
    volatile double[] eye = {-20, 2.5, 18}, look = {0, 1, 0};
    volatile double fovy = 40, lapse = 4, follow = 14;
    volatile int month = 7;
    List<Person> show;
    private int groundProg, postProg, gvao, gvbo, gcount, emptyVao, fbo, colTex, depTex, fw, fh;
    private long last = System.nanoTime();

    FigureDemo() { newHike(); }

    void newHike() {
        hike = new Hike(DemoFlat.route(), 13, month, 7);
        hike.stops.add(new Hike.Stop(380, 240, "Wattwurm"));
        hike.stops.add(new Hike.Stop(850, 180, "Priel"));
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        fgl.init(gl);
        groundProg = prog(gl, GROUND_VS, GROUND_FS);
        postProg = prog(gl, POST_VS, POST_FS);
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        gvao = t[0];
        emptyVao = t[1];
        gl.glGenBuffers(1, t, 0);
        gvbo = t[0];
        // Boden: fein nah am Ursprung, grob weiter weg (Ringe aus Quadraten, 2 bis 32 m)
        List<Float> v = new ArrayList<>();
        int n = 220;
        double x0 = -200, z0 = -300, c = 2400.0 / n;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] p : q) {
                    double x = x0 + p[0] * c, z = z0 + p[1] * c * 0.25, y = flat.ground(x, z);
                    double dx = flat.ground(x + 1, z) - flat.ground(x - 1, z), dz = flat.ground(x, z + 1) - flat.ground(x, z - 1), nl = Math.sqrt(dx * dx / 4 + 1 + dz * dz / 4);
                    v.add((float) x); v.add((float) y); v.add((float) z);
                    v.add((float) (-dx / 2 / nl)); v.add((float) (1 / nl)); v.add((float) (-dz / 2 / nl));
                }
            }
        float[] a = new float[v.size()];
        for (int i = 0; i < a.length; i++) a[i] = v.get(i);
        gcount = a.length / 6;
        gl.glBindVertexArray(gvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gvbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, a.length * 4L, Buffers.newDirectFloatBuffer(a), GL.GL_STATIC_DRAW);
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
        hike.step(dt * lapse, flat, null);
        double[] c = hike.centre(), g = hike.at(hike.s, 0);
        look = new double[]{c[0], 1.0, c[1]};
        eye = new double[]{c[0] - g[2] * follow + g[3] * follow * 0.6, 2.2 + follow * 0.08, c[1] - g[3] * follow - g[2] * follow * 0.6};
        show = null;
        render(d);
    }

    void render(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double[] sun = norm(new double[]{0.45, 0.62, -0.55});
        double[] sunC = {3.0, 2.8, 2.5}, top = {0.20, 0.32, 0.55}, hor = {0.55, 0.62, 0.70};
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
        gl.glUniform1f(gl.glGetUniformLocation(groundProg, "uLevel"), (float) flat.level);
        gl.glBindVertexArray(gvao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, gcount);
        FigureGl.View v = new FigureGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        FigureGl.Light l = new FigureGl.Light();
        l.sunDir = sun; l.sunColor = sunC; l.skyTop = top; l.skyHorizon = hor; l.fogDistance = 6000;
        fgl.draw(gl, show != null ? show : hike.people(), v, l);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(postProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glUniform1i(gl.glGetUniformLocation(postProg, "uHdr"), 0);
        gl.glBindVertexArray(emptyVao);
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

    @Override public void dispose(GLAutoDrawable d) { fgl.dispose(d.getGL().getGL4()); }

    static final String GROUND_VS = "layout(location=0) in vec3 aPos; layout(location=1) in vec3 aNrm; uniform mat4 uVP; out vec3 vW, vN; void main(){ vW=aPos; vN=aNrm; gl_Position=uVP*vec4(aPos,1.0); }\n";
    static final String GROUND_FS = """
            in vec3 vW, vN; out vec4 frag; uniform vec3 uSun, uSunC, uTop, uHor, uCam; uniform float uLevel;
            void main() {
                vec3 N = normalize(vN);
                bool mud = vW.x > 300.0 && vW.x < 450.0;
                vec3 c0 = mud ? vec3(0.13, 0.11, 0.08) : vec3(0.30, 0.25, 0.17) * (0.93 + 0.1 * sin(vW.x * 25.0 + sin(vW.z * 3.0)));
                float wet = smoothstep(0.5, 0.0, vW.y - uLevel);
                c0 *= mix(1.0, 0.55, wet);
                vec3 c = c0 * (uSunC * max(dot(N, uSun), 0.0) / 3.14159 + mix(uHor, uTop, 0.5));
                if (vW.y < uLevel) c = mix(c, vec3(0.05, 0.07, 0.07), 0.8);
                frag = vec4(mix(c, uHor, 1.0 - exp(-length(vW - uCam) / 3000.0)), 1.0);
            }
            """;
    static final String POST_VS = "void main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    static final String POST_FS = "uniform sampler2D uHdr; out vec4 frag; void main(){ vec3 c = texelFetch(uHdr, ivec2(gl_FragCoord.xy), 0).rgb * 1.6; c = c / (1.0 + c); frag = vec4(pow(c, vec3(1.0/2.2)), 1.0); }\n";

    // ------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("--bild")) { bild(new File(a[1])); System.exit(0); }
        SwingUtilities.invokeLater(() -> new FigureDemo().open());
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

    /** Eine Reihe stehender Personen im Monat m, nebeneinander auf x. */
    static List<Person> lineup(int month, long seed, double z) {
        List<Person> l = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(seed);
        for (int i = 0; i < 8; i++) {
            boolean child = i == 3 || i == 6;
            Person p = new Person(i, i == 0 ? Person.Role.GUIDE : child ? Person.Role.CHILD : Person.Role.ADULT, Outfit.forMonth(month, r, i == 0, child));
            p.scale = child ? 0.7 : 0.95 + 0.03 * (i % 3);
            p.x = 20 + i * 1.1; p.z = z; p.heading = Math.PI * 0.85;   // halb zur Kamera
            l.add(p);
        }
        return l;
    }

    static void bild(File out) throws Exception {
        File dir = out.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        int w = 640, h = 400;
        GLOffscreenAutoDrawable d = offscreen(w, h);
        FigureDemo demo = new FigureDemo();
        d.addGLEventListener(new GLEventListener() {
            @Override public void init(GLAutoDrawable a) { demo.init(a); }
            @Override public void dispose(GLAutoDrawable a) { }
            @Override public void display(GLAutoDrawable a) { demo.render(a); }
            @Override public void reshape(GLAutoDrawable a, int x, int y, int ww, int hh) { }
        });
        d.display();
        BufferedImage sheet = new BufferedImage(3 * w, 2 * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        java.util.function.BiConsumer<Integer, String> shot = (k, s) -> {
            d.display();
            BufferedImage[] img = new BufferedImage[1];
            d.invoke(true, x -> { img[0] = demo.grab(x); return true; });
            g.drawImage(img[0], (k % 3) * w, (k / 3) * h, null);
            label(g, s, (k % 3) * w + 6, (k / 3) * h + 6);
        };
        // 1, 2: Kleidung im Juli und im Januar
        for (int k = 0; k < 2; k++) {
            demo.show = lineup(k == 0 ? 7 : 1, 4 + k, 0);
            for (Person p : demo.show) p.y = demo.flat.ground(p.x, p.z);
            demo.look = new double[]{23.9, 0.9, 0};
            demo.eye = new double[]{23.9, 1.3, 9.5};
            demo.fovy = 50;
            shot.accept(k, k == 0 ? "Juli: barfuß, kurz oder hochgekrempelt" : "Januar: Winterjacke, Mütze, Stiefel");
        }
        // 3: der Gang, acht Phasen nebeneinander von der Seite
        List<Person> gait = new ArrayList<>();
        Outfit o = Outfit.walker();
        for (int i = 0; i < 8; i++) {
            Person p = new Person(i, Person.Role.ADULT, o);
            p.x = 20 + i * 1.0; p.z = 0; p.heading = Math.PI / 2; p.phase = i / 8.0; p.gait = 1;
            p.y = demo.flat.ground(p.x, p.z);
            gait.add(p);
        }
        demo.show = gait;
        demo.look = new double[]{23.5, 0.9, 0};
        demo.eye = new double[]{23.5, 1.0, 8.5};
        shot.accept(2, "Gang: acht Phasen eines Doppelschritts");
        // 4: Bücken und Zeigen
        List<Person> poses = new ArrayList<>();
        double[][] pp = {{0, 0}, {0.5, 0}, {1, 0}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            Person p = new Person(i, Person.Role.GUIDE, Outfit.forMonth(9, new SplittableRandom(9), true, false));
            p.x = 21 + i * 1.3; p.z = 0; p.heading = Math.PI * 0.7; p.bend = pp[i][0]; p.point = pp[i][1];
            p.y = demo.flat.ground(p.x, p.z);
            poses.add(p);
        }
        demo.show = poses;
        demo.look = new double[]{23, 0.8, 0};
        demo.eye = new double[]{23, 1.4, 7};
        shot.accept(3, "Führer: stehen, bücken, ganz gebückt, zeigen");
        // 5, 6: die Wanderung unterwegs und am Halt
        demo.show = null;
        Hike hk = demo.hike;
        for (int i = 0; i < 260; i++) hk.step(1, demo.flat, null);
        double[] c = hk.centre(), gg = hk.at(hk.s, 0);
        demo.look = new double[]{c[0], 1, c[1]};
        demo.eye = new double[]{c[0] + gg[2] * 16 + gg[3] * 9, 3.2, c[1] + gg[3] * 16 - gg[2] * 9};
        demo.fovy = 45;
        shot.accept(4, String.format(Locale.GERMAN, "unterwegs: %s", hk.describe()));
        while (hk.state != Hike.State.HALT || hk.stopElapsed() < 70) hk.step(1, demo.flat, null);
        c = hk.centre(); gg = hk.at(hk.s, 0);
        demo.look = new double[]{c[0], 0.8, c[1]};
        demo.eye = new double[]{c[0] - gg[3] * 13 + gg[2] * 4, 4.5, c[1] + gg[2] * 13 + gg[3] * 4};
        shot.accept(5, "am Halt: " + hk.currentStop().label() + ", die Gruppe im Halbkreis");
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
    }

    // ------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        FFrame f = new FFrame("Figuren · Vorschau des Packages com.dan.figure");
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
        JLabel head = new JLabel("FIGUREN");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        slider("Zeitraffer", 1, 30, 4, "×", v -> lapse = v);
        slider("Abstand der Kamera", 4, 60, 14, " m", v -> follow = v);
        slider("Monat", 1, 12, 7, "", v -> { month = v; newHike(); });
        com.dan.fbutton.FButton restart = new com.dan.fbutton.FButton("Neu starten");
        restart.addActionListener(e -> newHike());
        add(restart);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(250, e -> info.setText("<html><body style='width:230px'>" + hike.describe() + "</body></html>")).start();
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
