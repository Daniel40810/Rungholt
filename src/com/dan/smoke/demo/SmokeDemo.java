package com.dan.smoke.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.smoke.RoomSmoke;
import com.dan.smoke.Smoke;
import com.dan.smoke.Source;
import com.dan.smoke.gl.RoomSmokeGl;
import com.dan.smoke.gl.SmokeGl;
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

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Eigene Vorführung (Paketvertrag Punkt 3 und 10), ohne Haithabu-Code: ein Dorf aus 140 Dächern, aus hundert davon
 * steigt Rauch.
 * <ul>
 * <li><b>abend:</b> windstill, Inversion: die Fahnen steigen wenige Meter und legen sich flach über die Dächer.</li>
 * <li><b>tag:</b> neutrale Luft, 4 m/s Wind: die Fahnen steigen höher und knicken lang ab.</li>
 * <li><b>nah:</b> eine Schmiede und zwei Herdfeuer von nahem, ein brennendes Haus dahinter (schwarzer Rauch).</li>
 * <li><b>raum</b> (seit Haithabu Phase 11): eine Halle von 17 × 6 m mit Herdfeuer ({@link RoomSmoke}), Morgensonne
 * durch die Tür und die Rauchlöcher; Lichtstrahlen im Rauch unter dem Dach. Der Regler „Sonne“ stellt die Höhe.</li>
 * </ul>
 * Fenster: Ziehen dreht, Rad zoomt. Ohne Fenster: {@code SmokeDemo --bild datei.png [abend|tag|nah|raum] [breite höhe] [sekunden]}.
 */
public final class SmokeDemo implements GLEventListener {
    final Smoke smoke = new Smoke();
    final SmokeGl gl = new SmokeGl();
    volatile String scene = "abend";
    volatile double yaw, pitch, dist = 1, sunAz = 240, sunEl = 4;
    volatile boolean rebuild = true;
    double frameMs;
    long last;
    int boxProg, boxVao, boxCount;
    final List<double[]> roofs = new ArrayList<>();
    /** Szene „raum“: der Rauch in der Halle und seine Grafik; der Raum als Zahlenfeld wie RoomSmokeGl.View#room. */
    RoomSmoke room;
    final RoomSmokeGl roomGl = new RoomSmokeGl();
    float[] roomPack;
    static final double RHX = 8.57, RHZ = 2.83, RWH = 2.3, RRIDGE = 6.19, RTAN = 1.29, DOOR_X = -1.4, HOLE_Y0 = 5.24, HOLE_Y1 = 5.97, HOLE_W = 0.51;

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            SmokeDemo d = new SmokeDemo();
            d.scene = a.length > 2 ? a[2] : "abend";
            int w = a.length > 3 ? Integer.parseInt(a[3]) : 1280, h = a.length > 4 ? Integer.parseInt(a[4]) : 720;
            double secs = a.length > 5 ? Double.parseDouble(a[5]) : 240;
            d.build();
            d.rebuild = false;
            d.smoke.setTime(secs);
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
            d.last = -1;
            dr.addGLEventListener(d);
            dr.display();
            dr.display();
            BufferedImage[] img = new BufferedImage[1];
            String[] chk = new String[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), w, h); chk[0] = d.gl.selfCheck(x.getGL().getGL4(), d.smoke); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println("Bild: " + a[1] + " · " + d.smoke.describe() + " · " + d.gl.info());
            System.out.println(chk[0]);
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            SmokeDemo d = new SmokeDemo();
            FFrame f = new FFrame("Smoke-Package · Rauch aus Firsten und Feuern");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
            caps.setDepthBits(24);
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(d);
            root.add(c, BorderLayout.CENTER);
            MouseAdapter ma = new MouseAdapter() {
                int lx, ly;
                @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
                @Override public void mouseDragged(MouseEvent e) {
                    d.yaw += (e.getX() - lx) * 0.3;
                    d.pitch = Math.max(-25, Math.min(60, d.pitch + (e.getY() - ly) * 0.2));
                    lx = e.getX(); ly = e.getY();
                }
                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { d.dist = Math.max(0.2, Math.min(4, d.dist * Math.pow(1.1, e.getWheelRotation()))); }
            };
            c.addMouseListener(ma);
            c.addMouseMotionListener(ma);
            c.addMouseWheelListener(ma);
            FComboBox sc = new FComboBox(new Object[]{"abend", "tag", "nah", "raum"});
            FSlider wind = new FSlider(0, 100, 15), stab = new FSlider(0, 100, 90), sun = new FSlider(-8, 40, 4);
            JLabel lw = new JLabel("Wind 1,5 m/s"), ls = new JLabel("Inversion 0,90"), lsun = new JLabel("Sonne 4°"), info = new JLabel(" ");
            for (JLabel l : new JLabel[]{lw, ls, lsun, info}) l.setForeground(new Color(232, 230, 222));
            sc.addActionListener(e -> { d.scene = (String) sc.getSelectedItem(); d.yaw = d.pitch = 0; d.dist = 1; d.rebuild = true; });
            wind.addChangeListener(e -> { d.smoke.windSpeed = wind.getValue() / 10.0; lw.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", d.smoke.windSpeed)); });
            stab.addChangeListener(e -> { d.smoke.stability = stab.getValue() / 100.0; ls.setText(String.format(Locale.GERMAN, "Inversion %.2f", d.smoke.stability)); });
            sun.addChangeListener(e -> { d.sunEl = sun.getValue(); lsun.setText("Sonne " + sun.getValue() + "°"); });
            JPanel south = new JPanel(new java.awt.GridBagLayout());
            south.setBackground(new Color(15, 19, 17));
            south.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.insets = new java.awt.Insets(2, 6, 2, 6);
            g.fill = java.awt.GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            java.awt.Component[][] grid = {{new JLabel(" "), lw, ls, lsun}, {sc, wind, stab, sun}};
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 4; x++) {
                    java.awt.Component k = grid[y][x];
                    k.setPreferredSize(new java.awt.Dimension(200, y == 0 ? 22 : 34));
                    g.gridx = x; g.gridy = y;
                    south.add(k, g);
                }
            info.setPreferredSize(new java.awt.Dimension(400, 22));
            g.gridx = 0; g.gridy = 2; g.gridwidth = 4;
            south.add(info, g);
            root.add(south, BorderLayout.SOUTH);
            c.setPreferredSize(new java.awt.Dimension(1200, 640));
            c.setMinimumSize(new java.awt.Dimension(200, 150));
            new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN, "%.1f ms · %s · %s", d.frameMs, d.smoke.describe(), d.gl.info()))).start();
            f.setPreferredFrameSize(new java.awt.Dimension(1280, 860));
            f.setSize(1280, 860);
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    // ------------------------------------------------------------------ Szenen

    void build() {
        roofs.clear();
        List<Source> src = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(950);
        room = null;
        switch (scene) {
            case "raum" -> {
                // Halle wie das Versammlungshaus: Tür an der Traufseite +z (Osten), Rauchlöcher in beiden Giebeln
                List<RoomSmoke.Vent> v = List.of(new RoomSmoke.Vent(0.89, 0, 1.85, false), new RoomSmoke.Vent(0.89, 0, 1.85, false),
                        new RoomSmoke.Vent(2 * HOLE_W, HOLE_Y0, HOLE_Y1, true), new RoomSmoke.Vent(2 * HOLE_W, HOLE_Y0, HOLE_Y1, true));
                room = new RoomSmoke(new RoomSmoke.Box(RHX, RHZ, RWH, RRIDGE, RTAN), v);
                room.fireX = -0.2;
                room.heatKw = 12;
                room.settle();
                float[] rp = new float[16 + 64];
                rp[3] = 0; rp[4] = (float) RHX; rp[5] = (float) RHZ; rp[6] = (float) RWH; rp[7] = (float) RRIDGE; rp[8] = (float) RTAN; rp[9] = 4;
                float[][] o = {{2, (float) (DOOR_X - 0.45), (float) (DOOR_X + 0.45), 0, 0, 1.85f, 1.65f, 0}, {3, 0.95f, 1.85f, 0, 0, 1.85f, 1.65f, 0},
                        {0, (float) -HOLE_W, (float) HOLE_W, 1, (float) HOLE_Y0, (float) HOLE_Y1, 0.37f, 0}, {1, (float) -HOLE_W, (float) HOLE_W, 1, (float) HOLE_Y0, (float) HOLE_Y1, 0.37f, 0}};
                for (int i = 0; i < 4; i++) System.arraycopy(o[i], 0, rp, 16 + i * 8, 8);
                roomPack = rp;
                sunAz = 131;
                sunEl = 15;
            }
            case "nah" -> {
                roofs.add(new double[]{0, 0, 9, 5, 5.5});
                roofs.add(new double[]{-16, 6, 12, 5, 6});
                roofs.add(new double[]{14, -4, 6, 4, 3.5});
                roofs.add(new double[]{30, 40, 17, 7, 7});
                src.add(Source.hearth(0, 5.3, 0, 11));
                src.add(Source.hearth(-16, 5.8, 6, 12));
                src.add(new Source(14, 3.6, -4, 35, 1, 0.3, 13));
                src.add(new Source(30, 7, 40, 4000, 1, 0.9, 14));
                smoke.windSpeed = 2.5;
                smoke.stability = 0.5;
                sunEl = 18;
            }
            default -> {
                for (int i = 0; i < 140; i++) {
                    double x = (i % 14 - 6.5) * 28 + r.nextDouble() * 8, z = (i / 14 - 4.5) * 26 + r.nextDouble() * 6;
                    double len = 9 + r.nextDouble() * 6, wid = 5 + r.nextDouble() * 1.5, ridge = 4.5 + r.nextDouble() * 2;
                    roofs.add(new double[]{x, z, len, wid, ridge});
                    if (src.size() < 100 && r.nextDouble() < 0.75) src.add(new Source(x + len * 0.3, ridge - 0.2, z, 6 + r.nextDouble() * 10, 0.6 + 0.4 * r.nextDouble(), 0.05 + 0.1 * r.nextDouble(), i));
                }
                boolean eve = scene.equals("abend");
                smoke.windSpeed = eve ? 1.5 : 4;
                smoke.stability = eve ? 0.9 : 0;
                sunEl = eve ? 4 : 30;
            }
        }
        smoke.windFromDeg = 250;
        if (!scene.equals("raum")) sunAz = 240;
        smoke.setSources(src);
        boxCount = -1;
    }

    // ------------------------------------------------------------------ GL

    static final String BOX_VS = """
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNrm;
            uniform mat4 uVP;
            out vec3 vN; out vec3 vW;
            void main() { vN = aNrm; vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String BOX_FS = """
            in vec3 vN; in vec3 vW;
            out vec4 frag;
            uniform vec3 uSunDir, uSunColor, uSky, uFog, uCam;
            uniform float uExp, uDim;
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                vec3 n = normalize(vN);
                // Szene „raum“: Wände innen nur schwach im Streulicht (keine Sonne durch die Wand)
                if (uDim > 0.0) { frag = vec4(vec3(0.16, 0.12, 0.08) * uDim * (0.6 + 0.4 * abs(n.y)), 1.0); return; }
                vec3 alb = vW.y < 0.05 ? vec3(0.07, 0.08, 0.04) : n.y > 0.3 ? vec3(0.16, 0.13, 0.08) : vec3(0.20, 0.16, 0.11);
                vec3 c = alb * (uSunColor * max(dot(n, uSunDir), 0.0) + uSky * (0.6 + 0.4 * n.y));
                c = mix(c, uFog, 1.0 - exp(-length(uCam - vW) / 2500.0));
                frag = vec4(c, 1.0);
            }
            """;
    static final String POST_VS = """
            out vec2 vUv;
            void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }
            """;
    static final String POST_FS = """
            in vec2 vUv; out vec4 frag; uniform sampler2D uHdr; uniform float uExp;
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() { frag = vec4(pow(aces(texture(uHdr, vUv).rgb * uExp), vec3(1.0 / 2.2)), 1.0); }
            """;
    int postProg, hdrFbo, hdrTex, hdrDepth, hdrW, hdrH, emptyVao;

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        roomGl.init(g);
        boxProg = program(g, BOX_VS, BOX_FS);
        postProg = program(g, POST_VS, POST_FS);
        int[] v = new int[1];
        g.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
    }

    void uploadBoxes(GL4 g) {
        List<float[]> tri = new ArrayList<>();
        double half = 700;
        quad(tri, new double[]{-half, 0, -half}, new double[]{half, 0, -half}, new double[]{half, 0, half}, new double[]{-half, 0, half});
        if (room != null) roomWalls(tri);
        for (double[] r : roofs) {
            double x = r[0], z = r[1], hl = r[2] / 2, hw = r[3] / 2, eave = 2.0, ridge = r[4];
            double[][] p = {{x - hl, 0, z - hw}, {x + hl, 0, z - hw}, {x + hl, 0, z + hw}, {x - hl, 0, z + hw}};
            for (int k = 0; k < 4; k++) {
                double[] a = p[k], b = p[(k + 1) % 4];
                quad(tri, a, b, new double[]{b[0], eave, b[2]}, new double[]{a[0], eave, a[2]});
            }
            quad(tri, new double[]{x - hl - 0.4, eave - 0.3, z - hw - 0.6}, new double[]{x + hl + 0.4, eave - 0.3, z - hw - 0.6}, new double[]{x + hl + 0.4, ridge, z}, new double[]{x - hl - 0.4, ridge, z});
            quad(tri, new double[]{x + hl + 0.4, eave - 0.3, z + hw + 0.6}, new double[]{x - hl - 0.4, eave - 0.3, z + hw + 0.6}, new double[]{x - hl - 0.4, ridge, z}, new double[]{x + hl + 0.4, ridge, z});
        }
        float[] buf = new float[tri.size() * 6];
        int o = 0;
        for (float[] t : tri) { System.arraycopy(t, 0, buf, o, 6); o += 6; }
        int[] v = new int[2];
        g.glGenVertexArrays(1, v, 0);
        g.glGenBuffers(1, v, 1);
        boxVao = v[0];
        g.glBindVertexArray(boxVao);
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, v[1]);
        g.glBufferData(GL.GL_ARRAY_BUFFER, buf.length * 4L, com.jogamp.common.nio.Buffers.newDirectFloatBuffer(buf), GL.GL_STATIC_DRAW);
        g.glEnableVertexAttribArray(0);
        g.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        g.glEnableVertexAttribArray(1);
        g.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        g.glBindVertexArray(0);
        boxCount = tri.size();
    }

    /** Die Hülle der Halle (örtlich x entlang des Firsts = Szene −z, z quer = Szene +x), mit Türen und Rauchlöchern. */
    static void roomWalls(List<float[]> t) {
        java.util.function.Function<double[], double[]> w = p -> new double[]{p[2], p[1], -p[0]};
        java.util.function.Consumer<double[][]> q = c -> quad(t, w.apply(c[0]), w.apply(c[1]), w.apply(c[2]), w.apply(c[3]));
        double hx = RHX, hz = RHZ, H = RWH, yr = RRIDGE;
        // Traufwände mit je einer Tür (+z bei DOOR_X, −z bei −DOOR_X)
        for (int s = -1; s <= 1; s += 2) {
            double dx = s > 0 ? DOOR_X : -DOOR_X, z = s * hz;
            q.accept(new double[][]{{-hx, 0, z}, {dx - 0.45, 0, z}, {dx - 0.45, H, z}, {-hx, H, z}});
            q.accept(new double[][]{{dx + 0.45, 0, z}, {hx, 0, z}, {hx, H, z}, {dx + 0.45, H, z}});
            q.accept(new double[][]{{dx - 0.45, 1.85, z}, {dx + 0.45, 1.85, z}, {dx + 0.45, H, z}, {dx - 0.45, H, z}});
            // Dachfläche
            q.accept(new double[][]{{-hx, H, z}, {hx, H, z}, {hx, yr, 0}, {-hx, yr, 0}});
        }
        // Giebel mit Rauchloch: Rechteck unten, darüber zwei Schrägen neben dem Loch
        for (int e = -1; e <= 1; e += 2) {
            double x = e * hx, wb = hz * (yr - HOLE_Y0) / (yr - H);
            q.accept(new double[][]{{x, 0, -hz}, {x, 0, hz}, {x, H, hz}, {x, H, -hz}});
            q.accept(new double[][]{{x, H, -hz}, {x, H, hz}, {x, HOLE_Y0, wb}, {x, HOLE_Y0, -wb}});
            q.accept(new double[][]{{x, HOLE_Y0, -wb}, {x, HOLE_Y0, -HOLE_W}, {x, HOLE_Y1, 0}, {x, yr, 0}});
            q.accept(new double[][]{{x, HOLE_Y0, HOLE_W}, {x, HOLE_Y0, wb}, {x, yr, 0}, {x, HOLE_Y1, 0}});
        }
    }

    static void quad(List<float[]> t, double[] a, double[] b, double[] c, double[] d) {
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, w = {d[0] - a[0], d[1] - a[1], d[2] - a[2]};
        double[] n = {u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]};
        double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (n[1] < 0) l = -l;
        for (double[] p : new double[][]{a, b, c, a, c, d}) t.add(new float[]{(float) p[0], (float) p[1], (float) p[2], (float) (n[0] / l), (float) (n[1] / l), (float) (n[2] / l)});
    }

    @Override public void display(GLAutoDrawable d) {
        long now = System.nanoTime();
        if (rebuild) { rebuild = false; build(); }
        if (last > 0) smoke.step(Math.min(0.1, (now - last) / 1e9));
        last = now;
        GL4 g = d.getGL().getGL4();
        if (boxCount < 0) uploadBoxes(g);
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        ensureHdr(g, w, h);
        double az = Math.toRadians(sunAz), el = Math.toRadians(sunEl);
        double[] sunDir = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        double warm = Math.max(0, Math.min(1, (12 - sunEl) / 14));
        double sunI = 2.6 * Math.max(0, Math.min(1, (sunEl + 2) / 8));
        double[] sunC = {sunI, sunI * (0.92 - 0.3 * warm), sunI * (0.85 - 0.55 * warm)};
        double[] sky = {0.30 - 0.12 * warm, 0.36 - 0.14 * warm, 0.48 - 0.16 * warm}, horizon = {0.62 + 0.2 * warm, 0.64, 0.68 - 0.2 * warm}, fog = horizon;
        double[] eye, at;
        switch (scene) {
            case "raum" -> { eye = new double[]{0.6, 1.6, 6.8}; at = new double[]{0.9, 2.2, -2.0}; }
            case "nah" -> { eye = new double[]{-40, 9, -55}; at = new double[]{5, 10, 5}; }
            default -> { eye = new double[]{-330, 55, -260}; at = new double[]{0, 10, 0}; }
        }
        eye = orbit(eye, at);
        float[] vp = mul(persp(50, w / (double) h, 0.3, 6000), look(eye, at));
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        g.glViewport(0, 0, w, h);
        g.glClearColor((float) fog[0], (float) fog[1], (float) fog[2], 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LESS);
        g.glUseProgram(boxProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(boxProg, "uVP"), 1, false, vp, 0);
        g.glUniform1f(g.glGetUniformLocation(boxProg, "uDim"), room != null ? 0.25f : 0f);
        u3(g, boxProg, "uSunDir", sunDir); u3(g, boxProg, "uSunColor", sunC); u3(g, boxProg, "uSky", sky); u3(g, boxProg, "uFog", fog); u3(g, boxProg, "uCam", eye);
        g.glBindVertexArray(boxVao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, boxCount);
        g.glBindVertexArray(0);
        smoke.select(eye[0], eye[1], eye[2]);
        gl.update(g, smoke);
        SmokeGl.View v = new SmokeGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.far = 6000;
        v.near = 0.3;
        SmokeGl.Light l = new SmokeGl.Light();
        l.sunDir = sunDir;
        l.sunColor = sunC;
        l.skyTop = sky;
        l.skyHorizon = horizon;
        l.fogColor = fog;
        l.fogDistance = 2500;
        gl.draw(g, v, l, 1, smoke.time());
        if (room != null) {
            // Rauch der Halle: Sonne durch Tür und Rauchlöcher, das Herdfeuer (für die Vorführung kräftig), wenig Streulicht
            RoomSmokeGl.View rv = new RoomSmokeGl.View();
            rv.invViewProj = invert(vp);
            rv.eye = eye;
            double fl = Math.sqrt((at[0] - eye[0]) * (at[0] - eye[0]) + (at[1] - eye[1]) * (at[1] - eye[1]) + (at[2] - eye[2]) * (at[2] - eye[2]));
            rv.forward = new double[]{(at[0] - eye[0]) / fl, (at[1] - eye[1]) / fl, (at[2] - eye[2]) / fl};
            rv.width = w;
            rv.height = h;
            rv.room = roomPack;
            RoomSmokeGl.Light rl = new RoomSmokeGl.Light();
            rl.sunDir = sunDir;
            rl.sunColor = sunC;
            rl.ambient = new double[]{0.004, 0.0035, 0.003};
            rl.lightCount = 1;
            rl.lightPos[0] = 0; rl.lightPos[1] = 0.3f; rl.lightPos[2] = -0.2f; rl.lightPos[3] = 0.35f;
            rl.lightRgb[0] = 0.08f; rl.lightRgb[1] = 0.035f; rl.lightRgb[2] = 0.008f;
            g.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            roomGl.draw(g, rv, rl, room, 1, smoke.time());
        }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, w, h);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(postProg);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glUniform1i(g.glGetUniformLocation(postProg, "uHdr"), 0);
        g.glUniform1f(g.glGetUniformLocation(postProg, "uExp"), (float) (sunEl < 6 ? 1.6 : 1.0));
        g.glBindVertexArray(emptyVao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glBindVertexArray(0);
        frameMs = frameMs * 0.9 + (System.nanoTime() - now) / 1e6 * 0.1;
    }

    void ensureHdr(GL4 g, int w, int h) {
        if (hdrFbo != 0 && w == hdrW && h == hdrH) return;
        int[] t = new int[2];
        g.glGenTextures(2, t, 0);
        hdrTex = t[0];
        hdrDepth = t[1];
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrDepth);
        g.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT24, w, h, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        int[] f = new int[1];
        g.glGenFramebuffers(1, f, 0);
        hdrFbo = f[0];
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, hdrDepth, 0);
        hdrW = w;
        hdrH = h;
    }

    static void u3(GL4 g, int p, String n, double[] v) { g.glUniform3f(g.glGetUniformLocation(p, n), (float) v[0], (float) v[1], (float) v[2]); }

    double[] orbit(double[] eye, double[] at) {
        double dx = eye[0] - at[0], dy = eye[1] - at[1], dz = eye[2] - at[2];
        double r0 = Math.sqrt(dx * dx + dy * dy + dz * dz), r = r0 * dist, a = Math.atan2(dx, dz) + Math.toRadians(yaw);
        double e = Math.asin(dy / r0) + Math.toRadians(pitch);
        e = Math.max(Math.toRadians(1), Math.min(Math.toRadians(85), e));
        return new double[]{at[0] + r * Math.cos(e) * Math.sin(a), at[1] + r * Math.sin(e), at[2] + r * Math.cos(e) * Math.cos(a)};
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); roomGl.dispose(d.getGL().getGL4()); }

    static int program(GL4 g, String vs, String fs) {
        int p = g.glCreateProgram();
        for (int[] s : new int[][]{{GL4.GL_VERTEX_SHADER, 0}, {GL4.GL_FRAGMENT_SHADER, 1}}) {
            int sh = g.glCreateShader(s[0]);
            g.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + (s[1] == 0 ? vs : fs)}, null, 0);
            g.glCompileShader(sh);
            int[] ok = new int[1];
            g.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                g.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Demo-Shader: " + new String(b, 0, len[0]));
            }
            g.glAttachShader(p, sh);
        }
        g.glLinkProgram(p);
        return p;
    }

    static BufferedImage grab(GL4 g, int w, int h) {
        ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        g.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    static float[] persp(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect); m[5] = (float) t; m[10] = (float) ((f + n) / (n - f)); m[11] = -1; m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] look(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sz = fx, sl = Math.hypot(sx, sz);
        sx /= sl; sz /= sl;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        float[] m = new float[16];
        m[0] = (float) sx; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]); m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]); m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    /** Inverse einer 4×4-Matrix (Spaltenreihenfolge), für die Sichtstrahlen des Raums. */
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

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k]; r[c * 4 + row] = s; }
        return r;
    }
}
