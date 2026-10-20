package com.dan.timber.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.timber.House;
import com.dan.timber.HouseType;
import com.dan.timber.Material;
import com.dan.timber.Mesh;
import com.dan.timber.Structures;
import com.dan.timber.Timber;
import com.dan.timber.gl.TimberGl;
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
import java.awt.GridLayout;
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
 * Eigene Vorführung (Paketvertrag Punkt 3 und 10), ohne Haithabu-Code. Drei Bilder:
 * <ul>
 * <li><b>typen:</b> die sechs Haustypen nebeneinander, davor Flechtzaun und Bohlenweg, dahinter eine Palisade, am
 * Rand eine Landebrücke.</li>
 * <li><b>reet:</b> Nahaufnahme von Traufe und First eines Wohnhauses: Halme, Lagen, Moos, Firstsoden.</li>
 * <li><b>dorf:</b> 500 Häuser mit Zäunen und Wegen auf 900 × 900 m; misst die Zeit je Bild.</li>
 * </ul>
 * Fenster: Ziehen dreht, Rad zoomt. Ohne Fenster: {@code TimberDemo --bild datei.png [typen|reet|dorf] [breite höhe] [zustand]}.
 */
public final class TimberDemo implements GLEventListener {
    final Timber timber = new Timber();
    final TimberGl gl = new TimberGl();
    volatile String scene = "typen";
    volatile double state = 0.45, sunAz = 205, wet, yaw, pitch, dist = 1;
    final List<House> houses = new ArrayList<>();
    Mesh staticMesh;
    Structures.Set structures;
    double[] reetAt = {0, 0, 0};
    volatile boolean rebuild = true;
    double frameMs;
    int[] shadow;
    static final int SHADOW = 2048;
    int frames;

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            TimberDemo d = new TimberDemo();
            d.scene = a.length > 2 ? a[2] : "typen";
            int w = a.length > 3 ? Integer.parseInt(a[3]) : 1280, h = a.length > 4 ? Integer.parseInt(a[4]) : 720;
            if (a.length > 5) d.state = Double.parseDouble(a[5]);
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
            dr.addGLEventListener(d);
            dr.display();
            long t0 = System.nanoTime();
            int n = d.scene.equals("dorf") ? 20 : 3;
            for (int i = 0; i < n; i++) {
                dr.display();
                dr.getContext().makeCurrent();
                dr.getGL().glFinish();
                dr.getContext().release();
            }
            double ms = (System.nanoTime() - t0) / 1e6 / n;
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), w, h); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println(String.format(Locale.GERMAN, "Bild: %s · %s · %d Häuser · %.1f ms je Bild (mit glFinish)", a[1], d.gl.info(), d.houses.size(), ms));
            System.out.println(d.timber.describe());
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            TimberDemo d = new TimberDemo();
            FFrame f = new FFrame("Timber-Package · Holzbau aus Regeln");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            // 24 Bit Tiefe: die Voreinstellung (16 Bit) lässt ferne Dächer und Zäune mit dem Boden flimmern (Befund Phase 4)
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
            FComboBox sc = new FComboBox(new Object[]{"typen", "reet", "dorf"});
            FSlider st = new FSlider(0, 100, 45), sun = new FSlider(0, 359, 205), wt = new FSlider(0, 100, 0);
            FComboBox qu = new FComboBox(Timber.Quality.values());
            qu.setSelectedItem(Timber.Quality.MEDIUM);
            JLabel ls = new JLabel("Zustand 0,45"), lsun = new JLabel("Sonne aus 205°"), lwt = new JLabel("Nässe 0,00"), info = new JLabel(" ");
            for (JLabel l : new JLabel[]{ls, lsun, lwt, info}) l.setForeground(new Color(232, 230, 222));
            wt.addChangeListener(e -> { d.wet = wt.getValue() / 100.0; lwt.setText(String.format(Locale.GERMAN, "Nässe %.2f", d.wet)); });
            qu.addActionListener(e -> d.timber.quality = (Timber.Quality) qu.getSelectedItem());
            sc.addActionListener(e -> { d.scene = (String) sc.getSelectedItem(); d.yaw = d.pitch = 0; d.dist = 1; d.rebuild = true; });
            st.addChangeListener(e -> { d.state = st.getValue() / 100.0; d.rebuild = true; ls.setText(String.format(Locale.GERMAN, "Zustand %.2f (0 neu, 1 alt)", d.state)); });
            sun.addChangeListener(e -> { d.sunAz = sun.getValue(); lsun.setText("Sonne aus " + sun.getValue() + "°"); });
            // Feste Zeilenhöhen: die Infozeile darf die GL-Fläche nicht wegdrücken (GridLayout wuchs mit dem umbrochenen Text)
            JPanel south = new JPanel(new java.awt.GridBagLayout());
            south.setBackground(new Color(15, 19, 17));
            south.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.insets = new java.awt.Insets(2, 6, 2, 6);
            g.fill = java.awt.GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            java.awt.Component[][] grid = {{sc, ls, lsun, lwt}, {qu, st, sun, wt}};
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 4; x++) {
                    java.awt.Component k = grid[y][x];
                    k.setPreferredSize(new java.awt.Dimension(200, y == 0 && x > 0 ? 22 : 34));
                    g.gridx = x; g.gridy = y;
                    south.add(k, g);
                }
            info.setPreferredSize(new java.awt.Dimension(400, 22));
            g.gridx = 0; g.gridy = 2; g.gridwidth = 4;
            south.add(info, g);
            root.add(south, BorderLayout.SOUTH);
            c.setPreferredSize(new java.awt.Dimension(1200, 640));
            c.setMinimumSize(new java.awt.Dimension(200, 150));
            new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN, "%.1f ms · %s", d.frameMs, d.gl.info()))).start();
            f.setPreferredFrameSize(new java.awt.Dimension(1280, 860));
            f.setSize(1280, 860);
            // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in der App und den Lighthouse-Demos)
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    // ------------------------------------------------------------------ Szenen

    void build() {
        houses.clear();
        Mesh st = ground(scene.equals("dorf") ? 1100 : 260);
        structures = new Structures.Set();
        if (scene.equals("dorf")) village(500, 950, houses, structures, state);
        else {
            // sechs Typen nebeneinander, First von West nach Ost, Traufseite nach Süden zur Kamera
            double x = -48;
            long seed = 1;
            for (HouseType t : HouseType.values()) {
                double l = t.length + 2 * HouseType.GABLE;
                x += l / 2;
                houses.add(new House(t, x, 0, 0, 90, 1, 1, state, seed++));
                if (t == HouseType.WOHNHAUS) reetAt = new double[]{x, 0, 0};
                x += l / 2 + 4.5;
            }
            structures.fence(new double[][]{{-50, 10}, {-10, 11}, {30, 9.5}, {56, 10}}, Structures.FLAT, 3);
            structures.walkway(new double[][]{{-55, 14}, {60, 14}}, 2.4, Structures.FLAT, 4);
            // Palisade mit Tor und Wehrgang (Wehrbau seit Haithabu Phase 7)
            structures.palisade(new double[][]{{-60, -20}, {-20, -24}, {-2.3, -24}}, 3.4, Structures.FLAT, 5);
            structures.palisade(new double[][]{{2.3, -24}, {20, -24}, {60, -20}}, 3.4, Structures.FLAT, 7);
            structures.wallWalk(new double[][]{{-58, -18.6}, {-20, -22.6}, {-3.2, -22.6}}, 1.4, 1.2, Structures.FLAT, 8);
            structures.wallWalk(new double[][]{{3.2, -22.6}, {20, -22.6}, {58, -18.6}}, 1.4, 1.2, Structures.FLAT, 9);
            structures.gate(new com.dan.timber.Fortification.Gate(0, -23.2, 0, 0, 0, 3.6, 3.6, 6, 1.5, 2.0, 0.8, true, true), 10);
            structures.pier(new double[][]{{64, 6}, {64, 40}}, 4.0, 0.9, (px, pz) -> pz > 12 ? -2.5 : 0, 6);
        }
        staticMesh = st;
    }

    static Mesh ground(double half) {
        Mesh m = new Mesh();
        m.quad(new double[]{-half, 0, half}, new double[]{half, 0, half}, new double[]{half, 0, -half}, new double[]{-half, 0, -half}, Material.GROUND, 0, 0, 1);
        return m;
    }

    /**
     * Ein Dorf zum Messen: n Häuser auf einem verschobenen Raster (Zellen zu 38 m), Typen gewichtet wie eine
     * Siedlung (viele Wohn- und Bohlenhäuser, wenige Hallen), Firste meist quer zum Weg, Zäune um einen Teil der
     * Parzellen, Bohlenwege zwischen den Reihen.
     */
    public static void village(int n, long seed, List<House> out, Structures.Set structures, double state) {
        SplittableRandom r = new SplittableRandom(seed);
        int side = (int) Math.ceil(Math.sqrt(n));
        double cell = 38, off = -side * cell / 2;
        HouseType[] pick = {HouseType.WOHNHAUS, HouseType.WOHNHAUS, HouseType.WOHNHAUS, HouseType.BOHLENHAUS, HouseType.BOHLENHAUS, HouseType.UFERHAUS,
                HouseType.GRUBENHAUS, HouseType.GRUBENHAUS, HouseType.SCHUPPEN, HouseType.HALLE};
        int k = 0;
        for (int j = 0; j < side && k < n; j++) {
            for (int i = 0; i < side && k < n; i++, k++) {
                HouseType t = pick[r.nextInt(pick.length)];
                double x = off + (i + 0.5) * cell + (r.nextDouble() - 0.5) * 8, z = off + (j + 0.5) * cell + (r.nextDouble() - 0.5) * 6;
                double head = (r.nextBoolean() ? 90 : 0) + (r.nextDouble() - 0.5) * 16;
                out.add(new House(t, x, 0, z, head, 0.88 + r.nextDouble() * 0.24, 0.9 + r.nextDouble() * 0.2, Math.min(1, Math.max(0, state + (r.nextDouble() - 0.5) * 0.6)), r.nextLong()));
                if (structures != null && r.nextDouble() < 0.3) {
                    double hx = x - cell * 0.45, hz = z - cell * 0.4;
                    structures.fence(new double[][]{{hx, hz}, {hx + cell * 0.9, hz}, {hx + cell * 0.9, hz + cell * 0.8}}, Structures.FLAT, r.nextLong());
                }
            }
            if (structures != null && j % 2 == 0)
                structures.walkway(new double[][]{{off, off + (j + 1) * cell}, {-off, off + (j + 1) * cell}}, 2.2, Structures.FLAT, r.nextLong());
        }
    }

    // ------------------------------------------------------------------ GL

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g, timber);
        shadow = TimberGl.createShadowTarget(g, SHADOW);
    }

    @Override public void display(GLAutoDrawable d) {
        long t0 = System.nanoTime();
        GL4 g = d.getGL().getGL4();
        if (rebuild) {
            rebuild = false;
            build();
            gl.setStatic(g, staticMesh, state);
            gl.setStructures(g, structures, state);
        }
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        // Sonne und ihre Schattenkarte zuerst
        double az = Math.toRadians(sunAz), el = Math.toRadians(28);
        double[] sunDir = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        boolean dorf = scene.equals("dorf");
        double[] sc = dorf ? new double[]{-120, 0, 200} : new double[]{2, 0, 0};
        double half = dorf ? 260 : 75;
        float[] lightVP = TimberGl.sunMatrix(sunDir, sc[0], sc[1], sc[2], half);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadow[1]);
        g.glViewport(0, 0, SHADOW, SHADOW);
        g.glClearDepth(1);
        g.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.drawShadow(g, timber, houses, lightVP, sc[0], sc[2], half * 1.5);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, w, h);
        double[] fogLin = {1.05, 1.18, 1.38};
        float[] clr = tone(fogLin, 0.5);
        g.glClearColor(clr[0], clr[1], clr[2], 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        double[] eye, at;
        switch (scene) {
            // Nahaufnahme: Südostecke des Wohnhauses, Traufe, Giebelkante und First
            case "reet" -> { eye = new double[]{reetAt[0] + 9.0, 2.2, reetAt[2] + 6.2}; at = new double[]{reetAt[0] + 4.6, 3.3, reetAt[2] + 1.2}; }
            case "dorf" -> { eye = new double[]{-380, 60, 470}; at = new double[]{-40, 0, 60}; }
            default -> { eye = new double[]{2, 9, 40}; at = new double[]{2, 2.8, 0}; }
        }
        eye = orbit(eye, at);
        double fovy = 50;
        float[] vp = mul(persp(fovy, w / (double) h, 0.3, 9000), look(eye, at));
        TimberGl.View v = new TimberGl.View();
        v.viewProj = vp;
        v.eye = eye;
        double fl = Math.sqrt(sq(at[0] - eye[0]) + sq(at[1] - eye[1]) + sq(at[2] - eye[2]));
        v.forward = new double[]{(at[0] - eye[0]) / fl, (at[1] - eye[1]) / fl, (at[2] - eye[2]) / fl};
        v.fovyDeg = fovy;
        v.aspect = w / (double) h;
        v.tonemap = true;
        v.exposure = 0.95;
        TimberGl.Light l = new TimberGl.Light();
        l.sunDir = sunDir;
        l.sunShadowTexture = shadow[0];
        l.sunShadowMatrix = lightVP;
        l.sunShadowBias = dorf ? 0.002 : 0.0012;
        l.sunColor = new double[]{2.7, 2.45, 2.1};
        l.skyColor = new double[]{0.30, 0.36, 0.46};
        l.fogColor = fogLin;
        l.fogDistance = scene.equals("dorf") ? 2600 : 1200;
        l.wetness = wet;
        gl.draw(g, timber, houses, v, l);
        frameMs = frameMs * 0.9 + (System.nanoTime() - t0) / 1e6 * 0.1;
        frames++;
    }

    double[] orbit(double[] eye, double[] at) {
        double dx = eye[0] - at[0], dy = eye[1] - at[1], dz = eye[2] - at[2];
        double r = Math.sqrt(dx * dx + dy * dy + dz * dz) * dist, a = Math.atan2(dx, dz) + Math.toRadians(yaw);
        double e = Math.asin(dy / Math.sqrt(dx * dx + dy * dy + dz * dz)) + Math.toRadians(pitch);
        e = Math.max(Math.toRadians(1), Math.min(Math.toRadians(85), e));
        return new double[]{at[0] + r * Math.cos(e) * Math.sin(a), at[1] + r * Math.sin(e), at[2] + r * Math.cos(e) * Math.cos(a)};
    }

    static double sq(double v) { return v * v; }

    static float[] tone(double[] c, double exp) {
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            double x = c[i] * exp;
            double y = Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)));
            o[i] = (float) Math.pow(y, 1 / 2.2);
        }
        return o;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

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

    // kleine Matrizen (Spaltenreihenfolge), damit die Demo ohne andere Packages auskommt
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
        m[0] = (float) sx; m[4] = 0; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]); m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]); m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k]; r[c * 4 + row] = s; }
        return r;
    }
}
