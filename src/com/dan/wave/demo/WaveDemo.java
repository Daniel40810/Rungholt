package com.dan.wave.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.wave.Wave;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.wave} ohne Glacier-Code (Paketvertrag Punkt 3 und 10): ein Becken von 400 × 240 m.
 * Links eine senkrechte Wand (wie eine Gletscherfront) über 60 m Wasser, nach rechts wird es flacher bis zu einem
 * Strand 1:12, oben und unten Ufer mit einer Bucht. Klick ins Wasser: ein Einschlag. Oben die Draufsicht (blau über,
 * dunkel unter dem Ruhespiegel; Land braun, nass, wo die Welle war), darunter der Schnitt längs der Mitte und der Pegel
 * am Strand. Ohne Fenster: {@code WaveDemo --bild datei.png [breite]} zeigt drei Augenblicke und die Kurve.
 */
public final class WaveDemo {
    static final double W = 400, H = 240;

    private WaveDemo() { }

    /** Das Becken: Ruhetiefe am Ort (m). */
    static double depth(double x, double z) {
        double u = x + W / 2, v = z + H / 2;            // 0..W, 0..H
        if (u < 10) return Double.NaN;                  // die Wand
        double d = 60 - 0.2 * (u - 10);                 // nach rechts flacher
        if (u > 250) d = Math.min(d, 6 - (u - 250) / 12.0);   // Strand 1:12
        double side = Math.min(v, H - v);                // Ufer oben und unten
        double bay = Math.exp(-Math.pow((u - 170) / 40, 2)) * 25;   // eine Bucht oben
        double sideLimit = (v < H / 2 ? side + bay : side) * 0.6 - 4;
        return Math.min(d, sideLimit);
    }

    static Wave make() {
        Wave w = Wave.basin();
        w.fineCell = 1;
        w.fineN = (int) W;
        w.fineNz = (int) H;
        w.place(0, 0, 0, 0).build(WaveDemo::depth);
        w.gauge("Strand", 80, 0);
        return w;
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(WaveDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        Wave w = make();
        w.addVolumeOver(-150, 0, 3000, 8, 3);
        double[] shots = {4, 9, 16};
        int tw = width / 3, th = (int) (tw * H / W);
        BufferedImage img = new BufferedImage(width, th + 360, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int k = 0; k < 3; k++) {
            while (w.time() < shots[k] - 1e-9) w.step(1 / 30.0);
            BufferedImage t = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D tg = t.createGraphics();
            plan(tg, w, tw, th);
            tg.dispose();
            g.drawImage(t, k * tw, 0, null);
            profile(g, w, k * tw, th + 10, tw - 10, 160);
        }
        while (w.time() < 40) w.step(1 / 30.0);
        curve(g, w, 0, th + 190, width, 160, 40);
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath() + " · " + w.describe() + String.format(Locale.GERMAN, " · Pegel am Strand höchstens %.2f m", w.gauges().get(0).max()));
    }

    /** Draufsicht: Wasserstand in Farbe, Land braun (dunkel, wo es nass ist), die Wand grau. */
    static void plan(Graphics2D g, Wave w, int pw, int ph) {
        BufferedImage pic = new BufferedImage((int) W, (int) H, BufferedImage.TYPE_INT_RGB);
        for (int j = 0; j < H; j++)
            for (int i = 0; i < W; i++) {
                double x = i - W / 2 + 0.5, z = j - H / 2 + 0.5;
                double d = depth(x, z), e = w.levelAt(x, z);
                int rgb;
                if (Double.isNaN(d)) rgb = 0x9aa4ae;
                else if (Double.isNaN(e)) {
                    double ago = w.wetAgo(x, z), wet = Double.isInfinite(ago) ? 0 : Math.max(0, 1 - ago / 60);
                    double l = 1 - 0.45 * wet, hgt = Math.min(1, -d / 15);
                    rgb = rgb((0.42 + 0.2 * hgt) * l, (0.36 + 0.15 * hgt) * l, (0.26 + 0.1 * hgt) * l);
                } else {
                    double s = Math.max(-1, Math.min(1, e / 0.6)), deep = Math.min(1, Math.max(0, d) / 60);
                    double r = 0.05 + 0.75 * Math.max(0, s), gg = 0.35 - 0.2 * deep + 0.55 * Math.max(0, s) - 0.15 * Math.max(0, -s),
                            b = 0.55 - 0.15 * deep + 0.4 * Math.max(0, s) - 0.3 * Math.max(0, -s);
                    rgb = rgb(r, gg, b);
                }
                pic.setRGB(i, j, rgb);
            }
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(pic, 0, 0, pw, ph, null);
        g.setColor(new Color(230, 234, 238));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(String.format(Locale.GERMAN, "%.1f s · größte Höhe %.2f m", w.time(), w.maxHeight()), 8, 18);
        Wave.Gauge p = w.gauges().get(0);
        int gx = (int) ((p.x + W / 2) / W * pw), gz = (int) ((p.z + H / 2) / H * ph);
        g.setColor(new Color(255, 210, 90));
        g.fillOval(gx - 4, gz - 4, 8, 8);
    }

    /** Schnitt längs der Mitte (z = 0): Grund, Ruhespiegel, Welle (überhöht). */
    static void profile(Graphics2D g, Wave w, int x0, int y0, int pw, int ph) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double top = 4, bot = -62;
        java.util.function.DoubleUnaryOperator Y = v -> y0 + (top - v) / (top - bot) * ph;
        Path2D.Double bed = new Path2D.Double(), sea = new Path2D.Double();
        bed.moveTo(x0, y0 + ph);
        boolean pen = false;
        for (int i = 0; i <= pw; i++) {
            double x = -W / 2 + W * i / pw;
            double d = depth(x, 0);
            bed.lineTo(x0 + i, Y.applyAsDouble(Double.isNaN(d) ? top : -d));
            double e = w.levelAt(x, 0);
            if (Double.isNaN(e)) { pen = false; continue; }
            double yy = Y.applyAsDouble(e * 10);   // zehnfach überhöht
            if (pen) sea.lineTo(x0 + i, yy); else sea.moveTo(x0 + i, yy);
            pen = true;
        }
        bed.lineTo(x0 + pw, y0 + ph);
        bed.closePath();
        g.setColor(new Color(16, 34, 48));
        g.fillRect(x0, (int) Y.applyAsDouble(0), pw, (int) (y0 + ph - Y.applyAsDouble(0)));
        g.setColor(new Color(92, 78, 58));
        g.fill(bed);
        g.setColor(new Color(60, 80, 95));
        g.drawLine(x0, (int) Y.applyAsDouble(0), x0 + pw, (int) Y.applyAsDouble(0));
        g.setColor(new Color(110, 215, 235));
        g.setStroke(new BasicStroke(2f));
        g.draw(sea);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(150, 165, 178));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("Schnitt längs der Mitte, Welle zehnfach überhöht", x0 + 6, y0 + ph - 6);
    }

    /** Der Pegel am Strand über die Zeit. */
    static void curve(Graphics2D g, Wave w, int x0, int y0, int pw, int ph, double seconds) {
        Wave.Gauge p = w.gauges().get(0);
        float[][] s = p.series(seconds);
        double amp = Math.max(0.1, Math.max(p.max(), -p.min()));
        int mid = y0 + ph / 2;
        g.setColor(new Color(40, 60, 75));
        g.drawLine(x0 + 40, mid, x0 + pw - 10, mid);
        g.setColor(new Color(110, 215, 235));
        g.setStroke(new BasicStroke(2f));
        Path2D.Double path = new Path2D.Double();
        boolean pen = false;
        double t0 = s[0].length > 0 ? s[0][0] : 0;
        for (int i = 0; i < s[0].length; i++) {
            if (Float.isNaN(s[1][i])) { pen = false; continue; }
            double x = x0 + 40 + (pw - 50) * (s[0][i] - t0) / seconds, y = mid - (ph / 2.0 - 8) * s[1][i] / amp;
            if (pen) path.lineTo(x, y); else path.moveTo(x, y);
            pen = true;
        }
        g.draw(path);
        g.setStroke(new BasicStroke(1f));
        g.setColor(new Color(200, 215, 225));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(String.format(Locale.GERMAN, "Pegel am Strand (gelber Punkt), %.0f s: höchste %.2f m, tiefste %.2f m", seconds, p.max(), p.min()), x0 + 40, y0 + 14);
    }

    static int rgb(double r, double g, double b) {
        return (int) (255 * Math.max(0, Math.min(1, r))) << 16 | (int) (255 * Math.max(0, Math.min(1, g))) << 8 | (int) (255 * Math.max(0, Math.min(1, b)));
    }

    private static void open() {
        Wave[] w = {make()};
        double[] vol = {3000};
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = (Graphics2D) g0;
                int pw = getWidth(), ph = (int) (pw * H / W);
                if (ph > getHeight() - 360) { ph = getHeight() - 360; pw = (int) (ph * W / H); }
                plan(g, w[0], pw, ph);
                profile(g, w[0], 0, ph + 10, pw, 160);
                curve(g, w[0], 0, ph + 190, pw, 150, 60);
            }
        };
        view.setBackground(new Color(11, 17, 24));
        view.addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int pw = view.getWidth(), ph = (int) (pw * H / W);
                if (ph > view.getHeight() - 360) { ph = view.getHeight() - 360; pw = (int) (ph * W / H); }
                double x = e.getX() / (double) pw * W - W / 2, z = e.getY() / (double) ph * H - H / 2;
                if (e.getY() < ph) w[0].addVolumeOver(x, z, vol[0], Math.max(3, Math.cbrt(vol[0]) * 0.5), 3);
            }
        });

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.wave · eine Welle, die läuft");
        title.setForeground(new Color(90, 205, 225));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel volL = new JLabel();
        volL.setForeground(ink);
        FSlider volS = new FSlider(2, 200, 30);
        Runnable txt = () -> volL.setText(String.format(Locale.GERMAN, "Einschlag: %,.0f m³ in 3 s", vol[0]));
        volS.addChangeListener(e -> { vol[0] = volS.getValue() * 100.0; txt.run(); });
        txt.run();
        FButton hit = new FButton("Einschlag an der Wand");
        hit.addActionListener(e -> w[0].addVolumeOver(-W / 2 + 22, 0, vol[0], Math.max(3, Math.cbrt(vol[0]) * 0.5), 3));
        FButton calm = new FButton("Ruhig");
        calm.addActionListener(e -> w[0] = make());
        FCheckBox slow = new FCheckBox("Zeitlupe (¼)");
        FCheckBox pause = new FCheckBox("Anhalten");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        row.setOpaque(false);
        row.add(hit);
        row.add(calm);
        JLabel state = new JLabel(" ");
        state.setForeground(muted);
        List<Component> items = List.of(title, volL, volS, row, slow, pause, state,
                new JLabel("<html><body style='width:290px;color:#96a5b2'>Klick ins Wasser: ein Einschlag. Links die Wand wirft "
                        + "die Welle zurück, nach rechts wird es flach, sie wird langsamer, steiler und läuft am Strand auf; "
                        + "nass bleibt, wo sie war. Flachwasser auf 1-m-Zellen, Tempo √(g·h).</body></html>"));
        for (Component c : items) {
            if (c instanceof JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(340, 10));
        FFrame f = new FFrame("com.dan.wave – eine Welle, die läuft");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(view, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 900));
        f.setSize(1500, 900);
        f.setMinimumSize(new Dimension(900, 640));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(33, e -> {
            if (!pause.isSelected()) w[0].step(slow.isSelected() ? 1 / 120.0 : 1 / 30.0);
            view.repaint();
        }).start();
        new Timer(400, e -> state.setText("<html><body style='width:290px'>" + w[0].describe().replace(" · ", "<br>") + "</body></html>")).start();
    }
}
