package com.dan.tide.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.tide.Flood;
import com.dan.tide.Tide;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Vorschau des Pakets: ein Becken 6 × 4 km mit offener See im Westen, einem verzweigten Priel, Watt in Höhen zwischen
 * Niedrig- und Hochwasser, zwei Mulden, einer Hallig mit Warft und einem Deich mit Koog im Osten. Die Tide läuft im
 * Zeitraffer: die Flut kommt durch die Priele und schließt die Flächen von den Rinnen her, die Ebbe geht denselben Weg,
 * in den Mulden bleiben Pfützen. Unten die Pegelkurve von 25 Stunden. Rechts das Bedienfeld (FStyle).
 * <p>
 * Ohne Fenster: {@code --bild datei.png [stunden …]} zeichnet das Becken zu den Stunden nach Mitternacht (21.7.2026,
 * Ortszeit) nebeneinander.
 */
public final class TideDemo {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(110, 190, 220);
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    static final int NX = 600, NZ = 400;
    static final double CELL = 10;
    static final String[] PRESETS = {"Husum", "Pellworm [G]", "Norderhever [G]", "Becken (M2 + M4)"};
    static final double START = ZonedDateTime.of(2026, 7, 21, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli() / 3_600_000.0;

    final float[] h = new float[NX * NZ];
    final boolean[] seed = new boolean[NX * NZ];
    final Flood flood;
    volatile Tide tide = Tide.husum();
    volatile double hours = START + 6, speed = 600;   // Szenensekunden je Sekunde
    volatile boolean arrows = true;
    volatile double surge;
    private final JComponent view = new JComponent() {
        @Override protected void paintComponent(Graphics g) { paintView((Graphics2D) g, getWidth(), getHeight()); }
    };
    private final JLabel status = new JLabel(" ");

    TideDemo() {
        for (int j = 0; j < NZ; j++)
            for (int i = 0; i < NX; i++) {
                double x = (i + 0.5) * CELL, z = (j + 0.5) * CELL;
                h[j * NX + i] = (float) ground(x, z);
                seed[j * NX + i] = i == 0;
            }
        flood = Flood.build(h, NX, NZ, 0, 0, CELL, seed, null, 1.6, 1);
        flood.step(tide, hours);
    }

    /** Das Becken [G]: Watt um −0,3 m mit Wellen, Priel mit zwei Ästen bis −4 m, Mulden, Hallig, Deich, Koog. */
    static double ground(double x, double z) {
        double g = -0.3 + 0.5 * Math.sin(x / 700.0 + 1.3) * Math.cos(z / 520.0) + 0.6 * (x / 6000.0) + 0.15 * Math.sin(x / 90.0) * Math.sin(z / 130.0);
        // Priel: Hauptarm von West, zwei Äste nach Nordosten und Südosten
        double main = Math.abs(z - (2000 + 250 * Math.sin(x / 800.0))), w0 = 60 * (1 - x / 7000.0);
        if (x < 3200) g = Math.min(g, -4 + 3.5 * Math.min(1, Math.pow(main / w0, 2)) + x / 3200.0);
        double a1 = Math.abs((z - 2000) + (x - 3000) * 0.55 + 120 * Math.sin(x / 300.0)), a2 = Math.abs((z - 2000) - (x - 3000) * 0.6 - 90 * Math.sin(x / 260.0));
        if (x > 2900 && x < 5000) {
            double depth = -2.2 + (x - 2900) / 2100.0 * 1.6;
            g = Math.min(g, depth + 2.4 * Math.min(1, Math.pow(a1 / 35.0, 2)));
            g = Math.min(g, depth + 2.4 * Math.min(1, Math.pow(a2 / 30.0, 2)));
        }
        // Mulden mit Rand
        for (double[] m : new double[][]{{1800, 700, 120}, {4100, 3300, 150}}) {
            double d = Math.hypot(x - m[0], z - m[1]);
            if (d < m[2]) g = Math.min(g, 0.2 + 0.7 * (d / m[2]) * (d / m[2]) - 0.5);
            else if (d < m[2] * 1.35) g = Math.max(g, 0.45);
        }
        // Hallig mit Warft
        double hd = Math.hypot((x - 1500) / 1.6, z - 3100);
        if (hd < 420) g = Math.max(g, 1.6 + 0.4 * (1 - hd / 420));
        if (hd < 60) g = Math.max(g, 5.5);
        // Deich und Koog
        if (x > 5600 && x < 5660) g = 8;
        if (x >= 5660) g = 0.6 + 0.1 * Math.sin(z / 60.0);
        return g;
    }

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("--bild")) { bild(a); return; }
        SwingUtilities.invokeLater(() -> new TideDemo().open());
    }

    /** Ohne Fenster: Bilder zu Stunden nebeneinander in eine Datei. */
    static void bild(String[] a) throws Exception {
        TideDemo d = new TideDemo();
        double[] hs = new double[Math.max(1, a.length - 2)];
        if (a.length <= 2) hs = new double[]{1.5, 4.5, 7.5, 10.5};
        else for (int k = 2; k < a.length; k++) hs[k - 2] = Double.parseDouble(a[k].replace(',', '.'));
        int w = 600, hh = 470;
        BufferedImage out = new BufferedImage(w * hs.length, hh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        double t = START;
        for (int k = 0; k < hs.length; k++) {
            double target = START + hs[k];
            for (; t < target; t += 2 / 60.0) d.flood.step(d.tide, t);
            d.hours = target;
            d.flood.step(d.tide, target);
            BufferedImage im = new BufferedImage(w, hh, BufferedImage.TYPE_INT_RGB);
            Graphics2D gi = im.createGraphics();
            d.paintView(gi, w, hh);
            gi.dispose();
            g.drawImage(im, k * w, 0, null);
        }
        g.dispose();
        ImageIO.write(out, "png", new File(a[1]));
        System.out.println("Bild: " + a[1] + " · " + d.flood.describe());
    }

    // ------------------------------------------------------------ Zeichnen

    void paintView(Graphics2D g, int w, int hgt) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, hgt);
        int curveH = Math.max(70, hgt / 6);
        int mh = hgt - curveH - 10;
        double sc = Math.min(w / (double) NX, mh / (double) NZ);
        int iw = (int) (NX * sc), ih = (int) (NZ * sc), ox = (w - iw) / 2, oy = 4;
        BufferedImage img = new BufferedImage(NX, NZ, BufferedImage.TYPE_INT_RGB);
        Flood f = flood;
        for (int k = 0; k < NX * NZ; k++) {
            float gr = f.ground[k], d = f.surface[k] - gr;
            int rgb;
            if (d > Flood.WET) {
                double t = Math.min(1, d / 4);
                int r = (int) (70 - 50 * t), gg = (int) (130 - 70 * t), b = (int) (150 - 40 * t);
                if (f.connected[k] == 0) { r = 120; gg = 175; b = 200; }   // Pfütze
                rgb = r << 16 | gg << 8 | b;
            } else if (gr > 3) rgb = 0x5f7a3c;                              // Deich, Warft
            else if (gr > 1.0 && f.ground[k] < 7) rgb = 0x6f8a48;               // Hallig, Koog
            else {
                double wet = Math.max(0, 1 - f.dryHours[k] / 3);                // frisch trocken: dunkel und nass
                int base = (int) (175 - 25 * Math.max(-1, Math.min(1, gr)));
                int r = (int) (base * (1 - 0.35 * wet)), gg = (int) (base * 0.86 * (1 - 0.35 * wet)), b = (int) (base * 0.62 * (1 - 0.3 * wet));
                rgb = r << 16 | gg << 8 | b;
            }
            img.setRGB(k % NX, k / NX, rgb);
        }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, ox, oy, iw, ih, null);
        if (arrows) {
            g.setColor(new Color(255, 255, 255, 170));
            g.setStroke(new BasicStroke(1.2f));
            for (int j = 10; j < NZ; j += 20)
                for (int i = 10; i < NX; i += 20) {
                    int k = j * NX + i;
                    double uu = f.u[k], vv = f.v[k], s = Math.hypot(uu, vv);
                    if (s < 0.05) continue;
                    double len = Math.min(18, 6 + 14 * s) * sc / 1.4;
                    int x1 = (int) (ox + i * sc), y1 = (int) (oy + j * sc), x2 = (int) (x1 + uu / s * len), y2 = (int) (y1 + vv / s * len);
                    g.drawLine(x1, y1, x2, y2);
                    double an = Math.atan2(y2 - y1, x2 - x1);
                    g.drawLine(x2, y2, (int) (x2 - 4 * Math.cos(an - 0.5)), (int) (y2 - 4 * Math.sin(an - 0.5)));
                    g.drawLine(x2, y2, (int) (x2 - 4 * Math.cos(an + 0.5)), (int) (y2 - 4 * Math.sin(an + 0.5)));
                }
        }
        // Kurve der nächsten 25 Stunden mit dem Jetzt
        Tide t = tide;
        int cy = oy + ih + 8, cx0 = ox, cw = iw;
        g.setColor(new Color(8, 12, 15));
        g.fillRect(cx0, cy, cw, curveH);
        double t0 = hours - 6;
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(ACCENT);
        int px = -1, py = 0;
        for (int x = 0; x < cw; x++) {
            double th = t0 + 25.0 * x / cw, l = t.levelAt(th);
            int y = (int) (cy + curveH / 2.0 - l / 3.5 * curveH * 0.8);
            if (px >= 0) g.drawLine(cx0 + px, py, cx0 + x, y);
            px = x;
            py = y;
        }
        int nowX = (int) (cx0 + 6.0 / 25 * cw);
        g.setColor(new Color(240, 180, 90));
        g.drawLine(nowX, cy, nowX, cy + curveH);
        g.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g.setColor(INK);
        ZonedDateTime z = Instant.ofEpochMilli((long) (hours * 3_600_000)).atZone(ZONE);
        g.drawString(String.format(Locale.GERMAN, "%td.%tm. %tR · Pegel %+.2f m · %s · %s", z, z, z, t.levelAt(hours), t.rising(hours) ? "Flut" : "Ebbe", t.gauge), cx0 + 8, cy + 16);
    }

    // ------------------------------------------------------------ Fenster

    private void open() {
        FFrame f = new FFrame("Tide · Vorschau des Pakets com.dan.tide");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        view.setPreferredSize(new Dimension(1000, 760));
        root.add(view, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        status.setForeground(MUTED);
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(8, 12, 15));
        south.add(status);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1380, 900));
        f.setSize(1380, 900);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Thread th = new Thread(this::loop, "Tide-Vorschau");
        th.setDaemon(true);
        th.start();
    }

    private void loop() {
        long last = System.nanoTime();
        while (true) {
            long now = System.nanoTime();
            double dt = (now - last) / 1e9;
            last = now;
            Tide t = tide;
            t.surge = surge;
            hours += dt * speed / 3600;
            flood.step(t, hours);
            SwingUtilities.invokeLater(() -> {
                view.repaint();
                status.setText(flood.describe() + " · " + t.describe());
            });
            try { Thread.sleep(40); } catch (InterruptedException e) { return; }
        }
    }

    private JPanel panel;

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        head("TIDE");
        FComboBox pb = new FComboBox(PRESETS);
        pb.addActionListener(e -> {
            int i = pb.getSelectedIndex();
            tide = i == 0 ? Tide.husum() : i == 1 ? Tide.pellworm() : i == 2 ? Tide.hever() : Tide.basin();
        });
        add(pb);
        JLabel sl = label("Zeitraffer  10 min/s");
        add(sl);
        FSlider sp = new FSlider(1, 120, 10);
        sp.addChangeListener(e -> { speed = sp.getValue() * 60.0; sl.setText("Zeitraffer  " + sp.getValue() + " min/s"); });
        add(sp);
        JLabel su = label("Windstau  0,0 m");
        add(su);
        FSlider ss = new FSlider(-10, 80, 0);
        ss.addChangeListener(e -> { surge = ss.getValue() / 10.0; su.setText(String.format(Locale.GERMAN, "Windstau  %+.1f m", surge)); });
        add(ss);
        FCheckBox ar = new FCheckBox("Strömung als Pfeile");
        ar.setSelected(true);
        ar.addActionListener(e -> arrows = ar.isSelected());
        add(ar);
        add(Box.createVerticalStrut(12));
        add(small("<html><body style='width:230px'>Blau: Wasser mit Verbindung zum Meer, hell: Pfützen in den Mulden. "
                + "Sand dunkler, wo er gerade erst trocken gefallen ist. Mit Windstau ab +6,5 m geht das Wasser über den Deich in den Koog "
                + "(die Mandränke im Kleinen).</body></html>"));
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void head(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(ACCENT);
        l.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(l);
        add(Box.createVerticalStrut(8));
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(MUTED);
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }
}
