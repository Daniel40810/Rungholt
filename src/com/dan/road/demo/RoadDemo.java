package com.dan.road.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.road.Batch;
import com.dan.road.Ground;
import com.dan.road.Mover;
import com.dan.road.Network;
import com.dan.road.Roads;
import com.dan.road.Vehicle;
import com.dan.road.Way;
import com.dan.road.WayType;

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
 * Vorschau des Wege-Pakets: eine Hügellandschaft mit Bach, darin eine Autobahn, eine Landstraße mit
 * zwei Brücken, ein Feldweg und ein Pfad über den Hügel, alle als Rundkurse mit Verkehr. Regen macht
 * die Fahrbahn nass und füllt Pfützen, danach trocknet sie langsam ab, in den Radspuren zuerst. Nachts
 * leuchten Scheinwerfer, Rückleuchten und Rückstrahler; Staub steht hinter den Fahrzeugen auf dem
 * Feldweg. „Bisons auf die Straße“ stellt eine Herde auf die Fahrbahn, der Verkehr staut sich.
 * Maus ziehen dreht die Kamera, rechts ziehen verschiebt, das Rad zoomt.
 * <p>
 * Ohne Fenster: {@code --bild datei.png [blick 0..5] [regen 0..1] [nacht 0..1] [schnee 0..1] [breite] [höhe]}.
 */
public final class RoadDemo {
    static final Color BG = new Color(14, 18, 22), INK = new Color(226, 230, 226), MUTED = new Color(142, 150, 156), ACCENT = new Color(230, 170, 60);
    static final String[] VIEWS = {"Überblick", "Autobahn", "Straße mit Brücke", "Feldweg", "Pfad über den Hügel", "Mitfahren"};

    final Ground base = Landscape.BASE;
    final Network net = Landscape.network();
    final Ground shaped;
    final Roads roads;
    final RoadRenderer renderer;
    private final View view = new View();
    private volatile double yaw, pitch, dist, tx, ty, tz;
    private volatile int viewIdx;
    private volatile boolean herd;
    private volatile float trafficK = 1;
    private volatile String status = "";
    private volatile boolean repopulate;
    private volatile int pendingView = -1;
    private final JLabel statusLbl = new JLabel(" ");
    private Mover follow;

    RoadDemo() {
        net.build(base);
        shaped = net.shaped(base);
        roads = new Roads(net, shaped, 7);
        // auf dem kurzen Feldweg etwas mehr Betrieb als draußen, damit man Staub sieht
        roads.traffic.flow[way(WayType.Kind.TRACK).index] = 60;
        roads.traffic.rates();
        roads.traffic.populate();
        renderer = new RoadRenderer(shaped, net, Landscape.HALF, 4);
        renderer.roads = roads;
    }

    public static void main(String[] a) throws Exception {
        if (a.length > 0 && a[0].equals("--bild")) { still(a); return; }
        RoadDemo d = new RoadDemo();
        SwingUtilities.invokeLater(d::open);
    }

    // ------------------------------------------------------------ Blicke

    private Way way(WayType.Kind k) { for (Way w : net.ways) if (w.type.kind == k) return w; return net.ways.get(0); }

    private void camera(int v) {
        viewIdx = v;
        follow = null;
        float[] f = new float[6];
        switch (v) {
            case 0 -> { tx = -60; ty = 0; tz = 20; dist = 1150; yaw = -0.75; pitch = 0.5; }
            case 1 -> { Way w = way(WayType.Kind.MOTORWAY); w.at(w.length * 0.36, 0, f); aim(f, 60, 0.9, 0.1); }
            case 2 -> {
                Way w = way(WayType.Kind.ROAD);
                float s = 0;
                for (int i = 0; i < w.n; i++) if (w.bridge[i]) { s = i * w.step(); break; }
                w.at(s + 12, 0, f);
                aim(f, 45, 1.25, 0.12);
            }
            case 3 -> { Way w = way(WayType.Kind.TRACK); w.at(w.length * 0.15, 0, f); aim(f, 22, 0.5, 0.14); }
            case 4 -> { Way w = way(WayType.Kind.PATH); w.at(w.length * 0.3, 0, f); aim(f, 12, 0.35, 0.32); }
            case 5 -> {
                for (Mover m : roads.traffic.movers) if (m.kind == Vehicle.CAR && m.way().type.kind == WayType.Kind.ROAD) { follow = m; break; }
                if (follow == null) {
                    for (Mover m : roads.traffic.movers) if (!m.kind.walks() && !m.kind.pedals()) { follow = m; break; }
                }
                dist = 10; pitch = 0.2; yaw = 0;
            }
        }
    }

    /** Ziel an der Stelle f, Kamera im Abstand d, seitlich um den Winkel side gedreht. */
    private void aim(float[] f, double d, double side, double p) {
        tx = f[0]; ty = f[1] + 1; tz = f[2];
        dist = d; pitch = p;
        // Blick längs des Weges, etwas von der Seite
        yaw = Math.atan2(-f[3], -f[4]) + side;
    }

    private double[] eye() {
        if (follow != null && roads.traffic.movers.contains(follow)) {
            Mover m = follow;
            double back = dist, yw = yaw;
            double hx = m.hx * Math.cos(yw) - m.hz * Math.sin(yw), hz = m.hz * Math.cos(yw) + m.hx * Math.sin(yw);
            tx = m.x + m.hx * 14; tz = m.z + m.hz * 14; ty = m.y + 1.2;
            double[] e = {m.x - hx * back, m.y + 1.4 + back * Math.sin(pitch), m.z - hz * back};
            e[1] = Math.max(e[1], shaped.height(e[0], e[2]) + 0.8);
            return e;
        }
        double[] e = {tx + dist * Math.cos(pitch) * Math.sin(yaw), ty + dist * Math.sin(pitch), tz + dist * Math.cos(pitch) * Math.cos(yaw)};
        e[1] = Math.max(e[1], shaped.height(e[0], e[2]) + 1.2);
        return e;
    }

    // ------------------------------------------------------------ Herde

    private final float[] herdPos = new float[3 * 8];

    /** Eine Bisonherde auf der Landstraße vor dem Blick; als Hindernis für den Verkehr. */
    private void herd() {
        if (!herd) { roads.traffic.obstacles(herdPos, 0); return; }
        Way w = way(WayType.Kind.ROAD);
        float[] f = new float[6];
        float s0 = w.length * 0.08f;
        for (int k = 0; k < 8; k++) {
            w.at(s0 + k * 5.5f + (k % 3) * 1.3f, ((k * 37) % 7 - 3) * 1.2f, f);
            herdPos[3 * k] = f[0]; herdPos[3 * k + 1] = f[2]; herdPos[3 * k + 2] = 1.4f;
        }
        roads.traffic.obstacles(herdPos, 8);
    }

    /** Bisons als dunkle Körper (Buckel, Kopf, Beine) in den Batch. */
    private void drawHerd(float t) {
        if (!herd) return;
        Batch b = roads.batch;
        for (int k = 0; k < 8; k++) {
            float x = herdPos[3 * k], z = herdPos[3 * k + 1], y = shaped.height(x, z);
            Way w = way(WayType.Kind.ROAD);
            float[] f = new float[6];
            w.at(w.length * 0.08f + k * 5.5f, 0, f);
            y = f[1];
            float a = k * 1.7f + 0.3f * (float) Math.sin(t * 0.3 + k);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            box(b, x, y + 0.6f, z, ca, sa, 2.6f, 1.0f, 1.0f, 0.06f, 0.04f, 0.025f);
            box(b, x + ca * 0.7f, y + 1.1f, z + sa * 0.7f, ca, sa, 1.2f, 1.05f, 0.75f, 0.05f, 0.035f, 0.02f);
            box(b, x + ca * 1.6f, y + 0.75f, z + sa * 1.6f, ca, sa, 0.6f, 0.55f, 0.7f, 0.04f, 0.03f, 0.02f);
            for (int l = 0; l < 4; l++) {
                float lx = (l < 2 ? 0.9f : -0.9f), lz = (l % 2 == 0 ? 0.3f : -0.3f);
                box(b, x + ca * lx - sa * lz, y, z + sa * lx + ca * lz, ca, sa, 0.18f, 0.18f, 0.62f, 0.04f, 0.03f, 0.02f);
            }
        }
    }

    private static void box(Batch b, float x, float y, float z, float ca, float sa, float l, float w, float h, float r, float g, float bl) {
        float[][] c = new float[8][];
        for (int k = 0; k < 8; k++) {
            float sl = (k & 1) == 0 ? -l / 2 : l / 2, sw = (k & 2) == 0 ? -w / 2 : w / 2, sh = (k & 4) == 0 ? 0 : h;
            c[k] = new float[]{x + ca * sl - sa * sw, y + sh, z + sa * sl + ca * sw};
        }
        int[][] faces = {{0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}, {4, 6, 7, 5}};
        float[][] nn = {{-ca, 0, -sa}, {ca, 0, sa}, {sa, 0, -ca}, {-sa, 0, ca}, {0, 1, 0}};
        for (int q = 0; q < 5; q++) {
            int base = b.nv;
            for (int k = 0; k < 4; k++) {
                float[] p = c[faces[q][k]];
                add(b, p, nn[q], r, g, bl);
            }
            addTri(b, base, base + 1, base + 2);
            addTri(b, base, base + 2, base + 3);
        }
    }

    private static void add(Batch b, float[] p, float[] n, float r, float g, float bl) {
        grow(b);
        int i = b.nv++;
        b.xyz[3 * i] = p[0]; b.xyz[3 * i + 1] = p[1]; b.xyz[3 * i + 2] = p[2];
        b.nrm[3 * i] = n[0]; b.nrm[3 * i + 1] = n[1]; b.nrm[3 * i + 2] = n[2];
        b.rgb[3 * i] = r; b.rgb[3 * i + 1] = g; b.rgb[3 * i + 2] = bl;
        b.kind[i] = Batch.SOLID; b.gloss[i] = 0; b.fade[i] = 0;
    }

    private static void addTri(Batch b, int a, int c, int d) {
        if (3 * (b.nt + 1) > b.tri.length) b.tri = java.util.Arrays.copyOf(b.tri, b.tri.length * 2);
        b.tri[3 * b.nt] = a; b.tri[3 * b.nt + 1] = c; b.tri[3 * b.nt + 2] = d;
        b.nt++;
    }

    private static void grow(Batch b) {
        if (3 * (b.nv + 1) <= b.xyz.length) return;
        int c = b.nv * 2 + 16;
        b.xyz = java.util.Arrays.copyOf(b.xyz, 3 * c); b.nrm = java.util.Arrays.copyOf(b.nrm, 3 * c); b.rgb = java.util.Arrays.copyOf(b.rgb, 3 * c);
        b.kind = java.util.Arrays.copyOf(b.kind, c); b.gloss = java.util.Arrays.copyOf(b.gloss, c); b.fade = java.util.Arrays.copyOf(b.fade, c);
    }

    // ------------------------------------------------------------ Bild

    private BufferedImage frame(float t, float dt, int W, int H) {
        herd();
        double[] e = eye();
        roads.update(t, dt, e[0], e[1], e[2]);
        drawHerd(t);
        renderer.dark = roads.weather.dark;
        renderer.rain = roads.weather.rain;
        renderer.exposure = renderer.autoExposure() * 1.09f;
        renderer.setSize(W, H);
        renderer.camera(e[0], e[1], e[2], tx, ty, tz, viewIdx == 0 ? 50 : 60);
        return renderer.render(roads.batch, t);
    }

    private static void still(String[] a) throws Exception {
        RoadDemo d = new RoadDemo();
        int v = a.length > 2 ? Integer.parseInt(a[2]) : 0;
        Roads r = d.roads;
        r.weather.rain = a.length > 3 ? Float.parseFloat(a[3]) : 0;
        r.weather.dark = a.length > 4 ? Float.parseFloat(a[4]) : 0;
        r.weather.snow = a.length > 5 ? Float.parseFloat(a[5]) : 0;
        r.weather.poles = r.weather.snow > 0;
        r.weather.settle();
        int W = a.length > 6 ? Integer.parseInt(a[6]) : 1600, H = a.length > 7 ? Integer.parseInt(a[7]) : 900;
        d.herd = a.length > 8 && a[8].equals("herde");
        // Verkehr einschwingen
        for (int k = 0; k < 900; k++) { d.herd(); r.traffic.step(0.1f); }
        d.camera(v);
        BufferedImage img = null;
        long t0 = System.nanoTime();
        for (int k = 0; k < 30; k++) img = d.frame(90 + k / 30f, 1 / 30f, W, H);
        ImageIO.write(img, "png", new File(a[1]));
        System.out.printf(java.util.Locale.GERMANY, "%s  %s  %,d Dreiecke  %d Fahrzeuge  Wege %.0f ms  Bild %.0f ms  (%.0f ms je Bild)%n", a[1], VIEWS[v],
                d.renderer.triangles(), r.traffic.movers.size(), r.msBuild, d.renderer.msRaster, (System.nanoTime() - t0) / 30e6);
    }

    // ------------------------------------------------------------ Fenster

    private void open() {
        FFrame f = new FFrame("Wege · Vorschau des Pakets com.dan.road");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        statusLbl.setForeground(MUTED);
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLbl.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(8, 11, 14));
        south.add(statusLbl);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1440, 880));
        f.setSize(1440, 880);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Thread t = new Thread(this::loop, "Wege-Vorschau");
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
        head("BLICK");
        FComboBox cams = new FComboBox(VIEWS);
        camera(1);
        cams.setSelectedIndex(1);
        cams.addActionListener(e -> pendingView = cams.getSelectedIndex());
        add(cams);
        note("Autobahn, Landstraße mit Brücken über den Bach, Feldweg und Pfad, jeweils als Rundkurs mit Verkehr.");

        gap();
        head("TAGESZEIT");
        JLabel nl = label("Tag");
        FSlider ns = new FSlider(0, 100, 0);
        ns.addChangeListener(e -> { roads.weather.dark = ns.getValue() / 100f; nl.setText(ns.getValue() < 25 ? "Tag" : ns.getValue() < 65 ? "Dämmerung" : "Nacht"); });
        add(ns);
        note("In der Dämmerung und nachts gehen die Scheinwerfer an; Rückstrahler leuchten auf, wenn Licht auf sie fällt.");

        gap();
        head("WETTER");
        JLabel rl = label("Regen  0 %");
        FSlider rs = new FSlider(0, 100, 0);
        rs.addChangeListener(e -> { roads.weather.rain = rs.getValue() / 100f; rl.setText("Regen  " + rs.getValue() + " %"); });
        add(rs);
        JLabel sl = label("Schnee  0 cm");
        FSlider ss = new FSlider(0, 100, 0);
        ss.addChangeListener(e -> { roads.weather.snow = ss.getValue() / 100f; roads.weather.poles = ss.getValue() > 0; sl.setText("Schnee  " + ss.getValue() * 30 / 100 + " cm"); });
        add(ss);
        JLabel tl = label("Zeitraffer  60 ×");
        FSlider ts = new FSlider(1, 600, 60);
        roads.weather.timeScale = 60;
        ts.addChangeListener(e -> { roads.weather.timeScale = ts.getValue(); tl.setText("Zeitraffer  " + ts.getValue() + " ×"); });
        add(ts);
        JLabel wl = label("Wind  3 m/s");
        FSlider ws = new FSlider(0, 20, 3);
        ws.addChangeListener(e -> { roads.weather.windSpeed = ws.getValue(); wl.setText("Wind  " + ws.getValue() + " m/s"); });
        add(ws);
        note("Im Regen wird die Fahrbahn nass und glänzt, Pfützen laufen voll. Danach trocknet sie, in den Radspuren zuerst. Der Zeitraffer beschleunigt nur das Wetter.");

        gap();
        head("VERKEHR");
        FCheckBox tr = new FCheckBox("Verkehr");
        tr.setSelected(true);
        tr.setTextColor(INK);
        tr.addActionListener(e -> roads.showTraffic = tr.isSelected());
        add(tr);
        JLabel dl = label("Dichte  100 %");
        FSlider ds = new FSlider(20, 300, 100);
        ds.addChangeListener(e -> { trafficK = ds.getValue() / 100f; dl.setText("Dichte  " + ds.getValue() + " %"); });
        add(ds);
        FButton fill = new FButton("Neu verteilen");
        fill.addActionListener(e -> repopulate = true);
        add(fill);
        FCheckBox hb = new FCheckBox("Bisons auf der Straße");
        hb.setTextColor(INK);
        hb.addActionListener(e -> herd = hb.isSelected());
        add(hb);
        note("Fahrer halten Abstand, bremsen vor Kurven und wechseln auf der Autobahn mit Blinker die Spur. Vor der Herde staut es sich, das erste Auto schaltet die Warnblinker ein.");

        gap();
        head("ANZEIGE");
        FCheckBox half = new FCheckBox("Halbe Auflösung (schneller)");
        half.setSelected(true);
        half.setTextColor(INK);
        half.addActionListener(e -> view.half = half.isSelected());
        add(half);
        note("Ziehen: drehen · rechts ziehen: verschieben · Rad: näher und weiter.");
        panel.add(Box.createVerticalGlue());
        javax.swing.JScrollPane sc = new javax.swing.JScrollPane(panel, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(BG);
        sc.setPreferredSize(new Dimension(320, 600));
        return sc;
    }

    private final float[] defaults = new float[16];

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
        System.arraycopy(roads.traffic.flow, 0, defaults, 0, roads.traffic.flow.length);
        long last = System.nanoTime();
        float t = 0;
        double fpsAcc = 0, fps = 0;
        int frames = 0;
        while (true) {
            int pv = pendingView;
            if (pv >= 0) {
                pendingView = -1;
                camera(pv);
            }
            if (repopulate) {
                repopulate = false;
                Roads r = roads;
                float[] f0 = defaults;
                for (int i = 0; i < r.traffic.flow.length; i++) r.traffic.flow[i] = f0[i] * trafficK;
                r.traffic.rates();
                r.traffic.populate();
                if (viewIdx == 5) camera(5);
            }
            long now = System.nanoTime();
            float dt = (float) Math.min(0.1, (now - last) / 1e9);
            last = now;
            t += dt;
            int W = Math.max(64, view.getWidth()), H = Math.max(48, view.getHeight());
            if (view.half) { W /= 2; H /= 2; }
            long r0 = System.nanoTime();
            BufferedImage img = frame(t, dt, W, H);
            double rMs = (System.nanoTime() - r0) / 1e6;
            view.show(img);
            frames++;
            fpsAcc += dt;
            if (fpsAcc > 0.5) { fps = frames / fpsAcc; frames = 0; fpsAcc = 0; }
            var w = roads.weather;
            status = String.format(java.util.Locale.GERMANY, "%s  ·  %d Fahrzeuge  ·  %,d Dreiecke  ·  nass %.0f %%  Pfützen %.0f %%  ·  %.1f Bilder/s (Wege %.0f ms, Bild %.0f ms)  ·  %d × %d",
                    VIEWS[viewIdx], roads.traffic.movers.size(), renderer.triangles(), w.wet * 100, w.puddles * 100, fps, roads.msBuild + roads.msTraffic, rMs, W, H);
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
                    if (SwingUtilities.isRightMouseButton(e) && follow == null) {
                        double s = dist * 0.002;
                        tx -= Math.cos(yaw) * dx * s; tz += Math.sin(yaw) * dx * s; ty += dy * s;
                    } else {
                        yaw -= dx * 0.006;
                        pitch = Math.max(-0.1, Math.min(1.45, pitch + dy * 0.005));
                    }
                }

                @Override public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(3, Math.min(2500, dist * Math.pow(1.12, e.getPreciseWheelRotation()))); }
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
