package com.dan.ground.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.ground.Biome;
import com.dan.ground.GNoise;
import com.dan.ground.Meadow;
import com.dan.ground.Site;

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
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Vorschau des Boden-Pakets: ein welliger Hang mit Trampelpfad, Blumeninseln, Geröll und offenen
 * Stellen. Das Gras wogt im Wind, Böen laufen als Wellen darüber; ein Spaziergänger geht den Pfad
 * entlang und tritt das Gras nieder, das sich hinter ihm wieder aufrichtet. Maus ziehen dreht die
 * Kamera, rechts ziehen verschiebt, das Rad zoomt. Rechts das Bedienfeld (FStyle).
 * <p>
 * Ohne Fenster: {@code --bild datei.png [biom 0..2] [tag] [wind m/s] [blick 0..2] [breite] [höhe]}.
 */
public final class MeadowDemo {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(150, 200, 90);
    static final String[] BIOMES = {"Wiese · Yellowstone", "Wiese · Mitteleuropa", "Kiesbank"};
    static final String[] MONTHS = {"Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez."};
    static final int[] MONTH_START = {1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};
    /** Blicke: Ziel x, y, z, Abstand, Drehung, Neigung. */
    static final double[][] CAMS = {{0, 0.4, 0, 9, 0.5, 0.22}, {0, 0.2, 0, 3.2, 1.2, 0.35}, {0, 0, 0, 26, 0.9, 0.5}};

    /** Der Hang der Vorschau. */
    static final Site SITE = new Site() {
        @Override public float height(double x, double z) {
            float fx = (float) x, fz = (float) z;
            return 2.5f * GNoise.fbm(fx * 0.018f, fz * 0.018f, 3) + 0.25f * GNoise.value(fx * 0.12f, fz * 0.12f) - 1.3f;
        }

        /** Abstand zum Trampelpfad (eine sanfte Welle quer durchs Bild). */
        float path(double x, double z) {
            double f = 6 * Math.sin(x * 0.07) + 3, df = 0.42 * Math.cos(x * 0.07);
            return (float) (Math.abs(z - f) / Math.sqrt(1 + df * df));
        }

        @Override public void cover(double x, double z, float[] out) {
            float fx = (float) x, fz = (float) z;
            float p = path(x, z);
            float onPath = 1 - smooth(0.45f, 0.9f, p);
            float edge = smooth(0.4f, 0.8f, p) * (1 - smooth(0.9f, 1.6f, p));
            float n1 = GNoise.value(fx * 0.09f, fz * 0.09f);
            out[0] = (0.55f + 0.45f * n1) * (1 - onPath) * (1 - 0.5f * edge);
            out[1] = (0.18f + Math.max(0, GNoise.value(fx * 0.06f + 9, fz * 0.06f + 3) * 1.5f - 0.3f)) * (1 - onPath);
            float rocks = Math.max(0, GNoise.value(fx * 0.05f + 4, fz * 0.05f + 8) * 2.4f - 1.45f);
            out[2] = Math.min(1, rocks + edge * 0.8f + onPath * 0.3f);
            out[3] = Math.min(1, onPath + Math.max(0, GNoise.value(fx * 0.03f + 2, fz * 0.03f + 5) * 2.4f - 1.75f));
            out[4] = 1 - smooth(-1.2f, 1.0f, height(x, z));
        }
    };

    static float smooth(float a, float b, float x) {
        float t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private Meadow meadow = new Meadow(Biome.yellowstone(), SITE);
    private final GroundRenderer renderer = new GroundRenderer(SITE, 60, 0.5f);
    private final View view = new View();
    private volatile double yaw, pitch, dist, tx, ty, tz;
    private volatile int day = 180, biomeIdx;
    private volatile float snow;
    private volatile boolean walker = true;
    private volatile String status = "";
    private final JLabel statusLbl = new JLabel(" ");

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--bild")) { still(a); return; }
        MeadowDemo d = new MeadowDemo();
        SwingUtilities.invokeLater(d::open);
    }

    private void camera(int v) {
        double[] c = CAMS[v];
        tx = c[0]; ty = c[1] + SITE.height(c[0], c[2]); tz = c[2]; dist = c[3]; yaw = c[4]; pitch = c[5];
    }

    private double[] eye() {
        double[] e = {tx + dist * Math.cos(pitch) * Math.sin(yaw), ty + dist * Math.sin(pitch), tz + dist * Math.cos(pitch) * Math.cos(yaw)};
        e[1] = Math.max(e[1], SITE.height(e[0], e[2]) + 0.3);
        return e;
    }

    private void setBiome(int i) {
        biomeIdx = i;
        Meadow m = new Meadow(i == 0 ? Biome.yellowstone() : i == 1 ? Biome.europe() : Biome.gravelBar(), SITE);
        m.wind.speed = meadow.wind.speed; m.wind.direction = meadow.wind.direction; m.wind.gustiness = meadow.wind.gustiness;
        m.radius = meadow.radius;
        meadow = m;
    }

    // ------------------------------------------------------------ Spaziergänger

    private final float[] fig = new float[9 * 8], figRgb = new float[3 * 8];

    /** Lage des Spaziergängers auf dem Pfad zur Zeit t (geht hin und her). */
    private static double[] walkerAt(float t) {
        double x = 22 * Math.sin(t * 0.045), z = 6 * Math.sin(x * 0.07) + 3;
        double dx = 22 * 0.045 * Math.cos(t * 0.045), dz = 0.42 * Math.cos(x * 0.07) * dx, l = Math.hypot(dx, dz) + 1e-9;
        return new double[]{x, SITE.height(x, z), z, dx / l, dz / l};
    }

    /** Figur als zwei gekreuzte Tafeln (Körper, Kopf); setzt den Gehenden für das Niedertreten. */
    private int figure(float t) {
        double[] w = walkerAt(t);
        meadow.pushers = new float[]{(float) w[0], (float) w[2], 0.75f, 1};
        meadow.pusherCount = 1;
        float x = (float) w[0], y = (float) w[1], z = (float) w[2];
        float sx = (float) -w[4] * 0.2f, sz = (float) w[3] * 0.2f, fx = (float) w[3] * 0.13f, fz = (float) w[4] * 0.13f;
        int k = 0;
        float[][] quads = {{sx, sz, 0, 1.45f}, {fx, fz, 0, 1.45f}, {sx * 0.55f, sz * 0.55f, 1.5f, 1.78f}, {fx * 0.8f, fz * 0.8f, 1.5f, 1.78f}};
        float[][] col = {{0.45f, 0.06f, 0.04f}, {0.40f, 0.05f, 0.04f}, {0.35f, 0.22f, 0.15f}, {0.33f, 0.21f, 0.14f}};
        for (int q = 0; q < 4; q++) {
            float ax = quads[q][0], az = quads[q][1], y0 = y + quads[q][2], y1 = y + quads[q][3];
            float[][] v = {{x - ax, y0, z - az}, {x + ax, y0, z + az}, {x + ax, y1, z + az}, {x - ax, y1, z - az}};
            int[][] ts = {{0, 1, 2}, {0, 2, 3}};
            for (int[] tr : ts) {
                for (int c = 0; c < 3; c++) { fig[9 * k + 3 * c] = v[tr[c]][0]; fig[9 * k + 3 * c + 1] = v[tr[c]][1]; fig[9 * k + 3 * c + 2] = v[tr[c]][2]; }
                figRgb[3 * k] = col[q][0]; figRgb[3 * k + 1] = col[q][1]; figRgb[3 * k + 2] = col[q][2];
                k++;
            }
        }
        return k;
    }

    // ------------------------------------------------------------ Standbild

    private static void still(String[] a) throws Exception {
        MeadowDemo d = new MeadowDemo();
        int bi = a.length > 2 ? Integer.parseInt(a[2]) : 0;
        d.setBiome(bi);
        int day = a.length > 3 ? Integer.parseInt(a[3]) : 180;
        d.meadow.wind.speed = a.length > 4 ? Float.parseFloat(a[4]) : 5;
        d.camera(a.length > 5 ? Integer.parseInt(a[5]) : 0);
        int W = a.length > 6 ? Integer.parseInt(a[6]) : 1600, H = a.length > 7 ? Integer.parseInt(a[7]) : 900;
        d.meadow.setSeason(day, day < 60 || day > 330 ? 0.8f : 0);
        d.renderer.setSize(W, H);
        double[] e = d.eye();
        float t = 0;
        int nf = 0;
        long u0 = 0;
        for (int k = 0; k < 90; k++) {
            t = 20 + k / 30f;
            nf = d.figure(t);
            u0 = System.nanoTime();
            d.meadow.update(t, e[0], e[2]);
            u0 = System.nanoTime() - u0;
        }
        d.renderer.camera(e[0], e[1], e[2], d.tx, d.ty, d.tz, 55);
        d.renderer.carpet(d.meadow);
        BufferedImage img = d.renderer.render(d.meadow.batch, d.fig, d.figRgb, nf);
        ImageIO.write(img, "png", new File(a[1]));
        System.out.printf(java.util.Locale.GERMANY, "%s  %s  Tag %d  %,d Dreiecke  Wiese %.0f ms  Bild %.0f ms%n", a[1], BIOMES[bi], day, d.renderer.triangles(), u0 / 1e6, d.renderer.msRaster);
    }

    // ------------------------------------------------------------ Fenster

    private void open() {
        FFrame f = new FFrame("Boden · Vorschau des Pakets com.dan.ground");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        statusLbl.setForeground(MUTED);
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLbl.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(8, 12, 15));
        south.add(statusLbl);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1400, 860));
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Thread t = new Thread(this::loop, "Boden-Vorschau");
        t.setDaemon(true);
        t.start();
        new javax.swing.Timer(250, e -> statusLbl.setText(status)).start();
    }

    private JPanel panel;

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        head("BODEN");
        FComboBox bx = new FComboBox(BIOMES);
        bx.addActionListener(e -> setBiome(bx.getSelectedIndex()));
        add(bx);
        FComboBox cams = new FComboBox(new Object[]{"Blick über die Wiese", "Nah am Boden", "Von oben"});
        camera(0);
        cams.addActionListener(e -> camera(cams.getSelectedIndex()));
        add(cams);
        FButton again = new FButton("Neuer Startwert");
        again.addActionListener(e -> { meadow.seed++; meadow.clear(); });
        add(again);
        note("Gras wächst in Horsten, Blumen in Inseln, Steine liegen in Geröllfeldern und am Pfad; auf dem Pfad ist der Boden offen.");

        gap();
        head("JAHR");
        JLabel dl = label(dateLabel(day));
        FSlider ds = new FSlider(1, 365, day);
        ds.addChangeListener(e -> { day = ds.getValue(); dl.setText(dateLabel(day)); });
        add(ds);
        JLabel sl = label("Schnee  0 %");
        FSlider ss = new FSlider(0, 100, 0);
        ss.addChangeListener(e -> { snow = ss.getValue() / 100f; sl.setText("Schnee  " + ss.getValue() + " %"); });
        add(ss);
        note("Gräser ergrünen im Frühling und werden im Hochsommer golden; jede Blume blüht in ihrer eigenen Zeit.");

        gap();
        head("WIND");
        JLabel wl = label("Wind  4 m/s");
        FSlider ws = new FSlider(0, 20, 4);
        ws.addChangeListener(e -> { meadow.wind.speed = ws.getValue(); wl.setText("Wind  " + ws.getValue() + " m/s"); });
        add(ws);
        JLabel gl = label("Böen  60 %");
        FSlider gs = new FSlider(0, 100, 60);
        gs.addChangeListener(e -> { meadow.wind.gustiness = gs.getValue() / 100f; gl.setText("Böen  " + gs.getValue() + " %"); });
        add(gs);
        JLabel rl = label("Richtung  30°");
        FSlider rs = new FSlider(0, 359, 30);
        rs.addChangeListener(e -> { meadow.wind.direction = rs.getValue(); rl.setText("Richtung  " + rs.getValue() + "°"); });
        add(rs);
        note("Böen laufen als Wellen über die Wiese; die Halme biegen sich nach Höhe und Steifheit, Blüten nicken.");

        gap();
        head("ANZEIGE");
        FCheckBox wk = new FCheckBox("Spaziergänger auf dem Pfad");
        wk.setSelected(true);
        wk.setTextColor(INK);
        wk.addActionListener(e -> walker = wk.isSelected());
        add(wk);
        JLabel radL = label("Reichweite  32 m");
        FSlider rad = new FSlider(10, 60, 32);
        rad.addChangeListener(e -> { meadow.radius = rad.getValue(); radL.setText("Reichweite  " + rad.getValue() + " m"); });
        add(rad);
        FCheckBox half = new FCheckBox("Halbe Auflösung (schneller)");
        half.setSelected(true);
        half.setTextColor(INK);
        half.addActionListener(e -> view.half = half.isSelected());
        add(half);
        note("Wer durchs Gras geht, drückt es zur Seite; hinter ihm richtet es sich in einigen Sekunden wieder auf.");
        note("Ziehen: drehen · rechts ziehen: verschieben · Rad: näher und weiter.");
        panel.add(Box.createVerticalGlue());
        javax.swing.JScrollPane sc = new javax.swing.JScrollPane(panel, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(BG);
        sc.setPreferredSize(new Dimension(320, 600));
        return sc;
    }

    static String dateLabel(int d) {
        int m = 11;
        while (m > 0 && d < MONTH_START[m]) m--;
        return "Tag  " + (d - MONTH_START[m] + 1) + ". " + MONTHS[m];
    }

    private void head(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(ACCENT);
        add(l);
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(INK);
        add(l);
        return l;
    }

    private void note(String s) {
        JLabel l = new JLabel("<html><body style='width:190px'>" + s + "</body></html>");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(MUTED);
        add(l);
    }

    private void gap() { panel.add(Box.createVerticalStrut(14)); }

    private void add(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        c.setPreferredSize(new Dimension(250, c.getPreferredSize().height));
        panel.add(p);
    }

    private void loop() {
        long last = System.nanoTime();
        float t = 0;
        double fpsAcc = 0, fps = 0;
        int frames = 0;
        while (true) {
            long now = System.nanoTime();
            float dt = (float) Math.min(0.1, (now - last) / 1e9);
            last = now;
            t += dt;
            Meadow m = meadow;
            m.setSeason(day, snow);
            int nf = 0;
            if (walker) nf = figure(t); else m.pusherCount = 0;
            double[] e = eye();
            long u0 = System.nanoTime();
            m.update(t, e[0], e[2]);
            double uMs = (System.nanoTime() - u0) / 1e6;
            int W = Math.max(64, view.getWidth()), H = Math.max(48, view.getHeight());
            if (view.half) { W /= 2; H /= 2; }
            renderer.setSize(W, H);
            renderer.camera(e[0], e[1], e[2], tx, ty, tz, 55);
            renderer.carpet(m);
            long r0 = System.nanoTime();
            BufferedImage img = renderer.render(m.batch, fig, figRgb, nf);
            double rMs = (System.nanoTime() - r0) / 1e6;
            view.show(img);
            frames++;
            fpsAcc += dt;
            if (fpsAcc > 0.5) { fps = frames / fpsAcc; frames = 0; fpsAcc = 0; }
            status = String.format(java.util.Locale.GERMANY, "%s  ·  %s  ·  %d Kacheln  ·  %,d Dreiecke  ·  Wind %.0f m/s  ·  %.1f Bilder/s (Wiese %.0f ms, Bild %.0f ms)  ·  %d × %d",
                    BIOMES[biomeIdx], dateLabel(day), m.tileCount(), renderer.triangles(), m.wind.speed, fps, uMs, rMs, W, H);
            long spent = (System.nanoTime() - now) / 1_000_000L;
            try { Thread.sleep(Math.max(1, 16 - spent)); } catch (InterruptedException ex) { return; }
        }
    }

    /** Die Bildfläche mit Mauskamera. */
    private final class View extends JPanel {
        volatile boolean half = true;
        private volatile BufferedImage img;
        private int lx, ly;

        View() {
            setBackground(Color.BLACK);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }

                @Override public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lx, dy = e.getY() - ly;
                    lx = e.getX(); ly = e.getY();
                    if (SwingUtilities.isRightMouseButton(e)) {
                        double s = dist * 0.002;
                        tx -= Math.cos(yaw) * dx * s; tz += Math.sin(yaw) * dx * s; ty += dy * s;
                    } else {
                        yaw -= dx * 0.006;
                        pitch = Math.max(0.02, Math.min(1.45, pitch + dy * 0.005));
                    }
                }

                @Override public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(1, Math.min(200, dist * Math.pow(1.12, e.getPreciseWheelRotation()))); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void show(BufferedImage i) { img = i; repaint(); }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            BufferedImage i = img;
            if (i == null) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(i, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
