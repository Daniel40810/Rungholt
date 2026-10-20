package com.dan.river.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.river.Drift;

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
 * Vorschau des Fluss-Pakets: ein Bach mit Kurve, Kolk, Schnelle und Steinen. Das Wasser fließt, teilt
 * sich an den Steinen, kreist im Kehrwasser und schäumt in der Schnelle; Schaumflocken, Blätter und
 * Zweige treiben mit. Maus ziehen dreht die Kamera, rechts ziehen verschiebt, das Rad zoomt. Rechts das
 * Bedienfeld (FStyle): Blick, Abfluss, Trübe, Wind, Sonne, Treibgut, Strömung sichtbar machen.
 * <p>
 * Ohne Fenster: {@code --bild datei.png [blick] [abfluss%] [strömung 0/1] [breite] [höhe]} rechnet ein
 * Standbild (nach ein paar Sekunden Treibgut).
 */
public final class RiverDemo {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(86, 190, 214);
    static final String[] VIEWS = {"Schnelle mit Steinen", "Kurve mit Kolk", "Stein im Kolk", "Von oben", "Oberlauf"};
    /**
     * Kameras relativ zum Lauf: Bogenlänge des Ziels, Querlage des Ziels (Anteil der halben Breite),
     * Kamera längs (m, positiv flussab), quer (m, positiv zur Seite n > 0) und Höhe über dem Wasser.
     */
    static final double[][] CAMS = {
            {232, 0, 20, 9, 4.5}, {104, 0, -30, -26, 14}, {170, -0.3, 9, 8, 3.2}, {200, 0, 0.1, 0, 170}, {50, 0, 22, -10, 5}};

    private final Valley valley = new Valley();
    private final WaterRenderer renderer = new WaterRenderer();
    private final Drift drift = new Drift(2500);
    private final View view = new View();
    private volatile double yaw, pitch, dist, tx, ty, tz;
    private volatile String status = "";
    private final JLabel statusLbl = new JLabel(" ");
    private volatile boolean driftOn = true;
    private int startView;

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--bild")) { still(a); return; }
        RiverDemo d = new RiverDemo();
        if (a.length > 0) d.startView = Integer.parseInt(a[0]);
        SwingUtilities.invokeLater(d::open);
    }

    RiverDemo() {
        renderer.setValley(valley);
    }

    private void camera(int v) {
        double[] c = CAMS[v];
        float[] p = new float[6];
        valley.path.at(c[0], p);
        double nxv = -p[5], nzv = p[4];
        tx = p[0] + nxv * c[1] * p[3]; ty = p[2]; tz = p[1] + nzv * c[1] * p[3];
        double ex = tx + p[4] * c[2] + nxv * c[3], ey = ty + c[4], ez = tz + p[5] * c[2] + nzv * c[3];
        double dx = ex - tx, dy = ey - ty, dz = ez - tz;
        dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        pitch = Math.asin(dy / dist);
        yaw = Math.atan2(dx, dz);
    }

    private double[] eye() {
        return new double[]{tx + dist * Math.cos(pitch) * Math.sin(yaw), ty + dist * Math.sin(pitch), tz + dist * Math.cos(pitch) * Math.cos(yaw)};
    }

    private static void still(String[] a) throws Exception {
        RiverDemo d = new RiverDemo();
        int v = a.length > 2 ? Integer.parseInt(a[2]) : 0;
        d.camera(v);
        if (a.length > 3) d.valley.flow.discharge = Integer.parseInt(a[3]) / 100f;
        d.renderer.showFlow = a.length > 4 && a[4].equals("1");
        int W = a.length > 5 ? Integer.parseInt(a[5]) : 1600, H = a.length > 6 ? Integer.parseInt(a[6]) : 900;
        d.renderer.setSize(W, H);
        double[] e = d.eye();
        float t = 0;
        for (int k = 0; k < 400; k++) { t += 1 / 30f; d.drift.step(1 / 30f, d.valley.flow, d.tx, d.tz, 70); }
        d.renderer.camera(e[0], e[1], e[2], d.tx, d.ty, d.tz, 55);
        float[] q = new float[12 * d.drift.capacity], rgb = new float[3 * d.drift.capacity];
        int n = d.drift.quads(q, rgb);
        BufferedImage img = d.renderer.render(t, d.drift, n, q, rgb);
        ImageIO.write(img, "png", new File(a[1]));
        System.out.println(a[1] + "  " + VIEWS[v] + "  " + W + " × " + H + "  " + d.drift.n + " Stücke Treibgut");
    }

    private void open() {
        FFrame f = new FFrame("Fluss · Vorschau des Pakets com.dan.river");
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
        Thread t = new Thread(this::loop, "Fluss-Vorschau");
        t.setDaemon(true);
        t.start();
        new javax.swing.Timer(250, e -> statusLbl.setText(status)).start();
    }

    // ------------------------------------------------------------ Bedienfeld

    private JPanel panel;

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        head("BLICK");
        FComboBox v = new FComboBox(VIEWS);
        v.setSelectedIndex(startView);
        camera(startView);
        v.addActionListener(e -> camera(v.getSelectedIndex()));
        add(v);
        note("Oben ein ruhiger Lauf, dann eine Kurve mit tiefem Kolk außen und Kiesbank innen, darunter eine Schnelle mit Steinen.");

        gap();
        head("WASSER");
        JLabel ql = label("Abfluss  100 %");
        FSlider qs = new FSlider(20, 300, 100);
        qs.addChangeListener(e -> { valley.flow.discharge = qs.getValue() / 100f; ql.setText("Abfluss  " + qs.getValue() + " %"); });
        add(qs);
        JLabel tl = label("Trübe  15 %");
        FSlider ts = new FSlider(0, 100, 15);
        ts.addChangeListener(e -> { renderer.optics.turbidity = ts.getValue() / 100f; tl.setText("Trübe  " + ts.getValue() + " %"); });
        add(ts);
        note("Mehr Abfluss hebt den Spiegel, macht das Wasser schneller und die Schnelle weißer. Trübes Wasser verbirgt den Grund.");

        gap();
        head("WIND UND SONNE");
        JLabel wl = label("Wind  2 m/s");
        FSlider ws = new FSlider(0, 15, 2);
        ws.addChangeListener(e -> { renderer.surface.wind = ws.getValue(); wl.setText("Wind  " + ws.getValue() + " m/s"); });
        add(ws);
        JLabel sl = label("Sonnenhöhe  40°");
        FSlider ss = new FSlider(5, 80, 40);
        ss.addChangeListener(e -> { sun(ss.getValue()); sl.setText("Sonnenhöhe  " + ss.getValue() + "°"); });
        add(ss);
        sun(40);

        gap();
        head("ANZEIGE");
        FCheckBox dr = new FCheckBox("Treibgut (Schaum, Blätter, Zweige)");
        dr.setSelected(true);
        dr.setTextColor(INK);
        dr.addActionListener(e -> { driftOn = dr.isSelected(); if (!driftOn) drift.n = 0; });
        add(dr);
        FCheckBox fl = new FCheckBox("Strömung zeigen");
        fl.setTextColor(INK);
        fl.addActionListener(e -> renderer.showFlow = fl.isSelected());
        add(fl);
        FCheckBox half = new FCheckBox("Halbe Auflösung (schneller)");
        half.setSelected(true);
        half.setTextColor(INK);
        half.addActionListener(e -> view.half = half.isSelected());
        add(half);
        FButton autumn = new FButton("Herbstlaub ins Wasser");
        autumn.addActionListener(e -> leaves());
        add(autumn);
        note("Strömung zeigen: blau langsam, gelb schnell, violett Kehrwasser; die Striche laufen mit dem Wasser.");
        note("Ziehen: drehen · rechts ziehen: verschieben · Rad: näher und weiter.");
        panel.add(Box.createVerticalGlue());
        javax.swing.JScrollPane sc = new javax.swing.JScrollPane(panel, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(BG);
        sc.setPreferredSize(new Dimension(320, 600));
        return sc;
    }

    private void sun(int deg) {
        double el = Math.toRadians(deg), az = Math.toRadians(-35);
        renderer.sunX = (float) (Math.cos(el) * Math.sin(az)); renderer.sunY = (float) Math.sin(el); renderer.sunZ = (float) (-Math.cos(el) * Math.cos(az));
        float k = (float) Math.min(1, 0.35 + Math.sin(el));
        renderer.sunR = 3.2f * k; renderer.sunG = 3.0f * k * (0.85f + 0.15f * k); renderer.sunB = 2.7f * k * k;
    }

    /** Eine Handvoll Herbstlaub vor die Kamera ins Wasser streuen. */
    private void leaves() {
        java.util.Random r = new java.util.Random();
        com.dan.river.FlowField.Flow f = new com.dan.river.FlowField.Flow();
        for (int i = 0, got = 0; i < 2000 && got < 120; i++) {
            float x = (float) (tx + (r.nextDouble() - 0.5) * 40), z = (float) (tz + (r.nextDouble() - 0.5) * 40);
            if (!valley.flow.sample(x, z, f) || !f.wet) continue;
            float[][] c = {{0.62f, 0.38f, 0.03f}, {0.55f, 0.18f, 0.03f}, {0.70f, 0.52f, 0.05f}};
            float[] cc = c[r.nextInt(3)];
            if (drift.add(x, z, Drift.LEAF, cc[0], cc[1], cc[2], 0.07f + 0.04f * r.nextFloat())) { drift.y[drift.n - 1] = f.level; got++; }
        }
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

    // ------------------------------------------------------------ Bildschleife

    private void loop() {
        long last = System.nanoTime();
        float t = 0;
        double fpsAcc = 0, fps = 0;
        int frames = 0;
        float[] q = new float[12 * drift.capacity], rgb = new float[3 * drift.capacity];
        while (true) {
            long now = System.nanoTime();
            float dt = (float) Math.min(0.1, (now - last) / 1e9);
            last = now;
            t += dt;
            if (driftOn) drift.step(dt, valley.flow, tx, tz, 70);
            int n = driftOn ? drift.quads(q, rgb) : 0;
            int W = Math.max(64, view.getWidth()), H = Math.max(48, view.getHeight());
            if (view.half) { W /= 2; H /= 2; }
            renderer.setSize(W, H);
            double[] e = eye();
            renderer.camera(e[0], Math.max(e[1], valley.height((float) e[0], (float) e[2]) + 0.6), e[2], tx, ty, tz, 55);
            long r0 = System.nanoTime();
            BufferedImage img = renderer.render(t, drift, n, q, rgb);
            double rMs = (System.nanoTime() - r0) / 1e6;
            view.show(img);
            frames++;
            fpsAcc += dt;
            if (fpsAcc > 0.5) { fps = frames / fpsAcc; frames = 0; fpsAcc = 0; }
            status = String.format(java.util.Locale.GERMANY, "Abfluss %.0f %%  ·  %d Steine  ·  %d Stücke Treibgut  ·  %,d Dreiecke  ·  %.1f Bilder/s (Bild %.0f ms)  ·  %d × %d",
                    valley.flow.discharge * 100, valley.flow.rocks().size(), drift.n, renderer.triangles(), fps, rMs, W, H);
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
                        tx -= Math.cos(yaw) * dx * s; tz += Math.sin(yaw) * dx * s; ty = Math.max(0, ty + dy * s);
                    } else {
                        yaw -= dx * 0.006;
                        pitch = Math.max(0.03, Math.min(1.45, pitch + dy * 0.005));
                    }
                }

                @Override public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(3, Math.min(400, dist * Math.pow(1.12, e.getPreciseWheelRotation()))); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void show(BufferedImage i) {
            img = i;
            repaint();
        }

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
