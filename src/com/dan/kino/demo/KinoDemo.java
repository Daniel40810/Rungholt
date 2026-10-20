package com.dan.kino.demo;

import com.dan.kino.Kino;
import com.dan.kino.KinoBild;
import com.dan.kino.KinoMaler;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Vorführung ohne Grafikkarte und ohne Szene: ein kleiner Film aus vier gemalten Einstellungen (Morgen, Mittag, Abend,
 * Nacht über Bergen am See) mit Vorspann, Untertiteln, Überblendungen, Schwenk und Abspann, alles über
 * {@link KinoBild}. Run File; Esc bricht ab, Leertaste beginnt von vorn.
 */
public final class KinoDemo extends JPanel {
    private static final String[][] SZENEN = {
            {"6 Uhr: Nebel über dem See", "0.62,0.66,0.70", "0.80,0.80,0.80"},
            {"Mittag: Westwind vom Eisfeld", "0.25,0.48,0.78", "0.75,0.86,0.95"},
            {"Abend: Licht von Westen", "0.30,0.20,0.36", "0.96,0.62,0.36"},
            {"Nacht: der Mond über dem Eis", "0.02,0.03,0.07", "0.08,0.12,0.22"}};
    private final Kino kino = new Kino();
    private final KinoMaler maler = new KinoMaler();
    private double t, yaw;
    private int szene = -1;
    private BufferedImage bild, gemerkt;

    public KinoDemo() {
        setPreferredSize(new Dimension(1280, 720));
        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) kino.abbrechen();
                if (e.getKeyCode() == KeyEvent.VK_SPACE) los();
            }
        });
        los();
        new Timer(33, e -> schritt(1 / 30.0)).start();
    }

    private void los() {
        t = 0;
        szene = 0;
        kino.beginnen("com.dan.kino · Vorführung", "Ein Tag am See", "Vier Einstellungen mit Vorspann, Untertiteln, Überblendung und Abspann – ohne Grafikkarte");
        kino.untertitel(SZENEN[0][0]);
    }

    private void schritt(double dt) {
        t += dt;
        int soll = Math.min(SZENEN.length - 1, (int) Math.max(0, (t - 7) / 8));
        if (soll != szene && kino.lage() != Kino.Lage.AUS) {
            if (kino.schnitt()) { gemerkt = bild; kino.geschnitten(); }
            szene = soll;
            kino.untertitel(SZENEN[szene][0]);
        }
        if (t > 7 + 8 * SZENEN.length && kino.lage() == Kino.Lage.FILM)
            kino.beenden(List.of("Ein Tag am See", "Kino: com.dan.kino", "Bilder: gemalt mit Java2D", "Esc: abbrechen · Leertaste: von vorn"));
        kino.schritt(dt);
        yaw = kino.schwenk();
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        bild = landschaft(Math.max(0, szene), w, h, yaw);
        g0.drawImage(KinoBild.ueber(bild, gemerkt, kino, maler, t), 0, 0, null);
    }

    /** Himmel, zwei Bergketten, See; der Schwenk verschiebt die Berge. */
    private static BufferedImage landschaft(int i, int w, int h, double yaw) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color oben = farbe(SZENEN[i][1]), unten = farbe(SZENEN[i][2]);
        g.setPaint(new GradientPaint(0, 0, oben, 0, h * 0.62f, unten));
        g.fillRect(0, 0, w, h);
        double dx = yaw * w / 40;
        for (int k = 0; k < 2; k++) {
            Path2D p = new Path2D.Double();
            p.moveTo(-50, h * 0.62);
            for (int x = -50; x <= w + 50; x += 20) {
                double u = (x + dx * (k + 1)) / w;
                p.lineTo(x, h * (0.62 - (0.10 + 0.08 * k) * (0.6 + 0.4 * Math.sin(u * 9 + k * 2) * Math.cos(u * 4.3 + i))));
            }
            p.lineTo(w + 50, h * 0.62);
            p.closePath();
            g.setColor(mische(oben, Color.BLACK, 0.45 + 0.2 * k));
            g.fill(p);
        }
        g.setPaint(new GradientPaint(0, h * 0.62f, mische(unten, new Color(30, 90, 100), 0.6), 0, h, mische(oben, Color.BLACK, 0.7)));
        g.fillRect(0, (int) (h * 0.62), w, h);
        g.dispose();
        return im;
    }

    private static Color farbe(String s) {
        String[] p = s.split(",");
        return new Color(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]));
    }

    private static Color mische(Color a, Color b, double f) {
        return new Color((int) (a.getRed() * (1 - f) + b.getRed() * f), (int) (a.getGreen() * (1 - f) + b.getGreen() * f), (int) (a.getBlue() * (1 - f) + b.getBlue() * f));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("com.dan.kino · Vorführung");
            KinoDemo d = new KinoDemo();
            f.setContentPane(d);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setVisible(true);
            d.requestFocusInWindow();
        });
    }
}
