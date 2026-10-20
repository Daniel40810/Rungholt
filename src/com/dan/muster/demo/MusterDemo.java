package com.dan.muster.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.muster.Muster;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Eigene kleine Vorführung des Packages (Paketvertrag Punkt 3 und 10): läuft ohne Leuchtturm-Code, nur sie darf
 * FStyle nutzen. Ohne Fenster schreibt {@code MusterDemo --bild datei.png [bft]} ein Standbild (für Statusbilder).
 */
public final class MusterDemo {
    private MusterDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            Muster m = Muster.beaufort(args.length > 2 ? Integer.parseInt(args[2]) : 4);
            javax.imageio.ImageIO.write(render(m, 480, 320, 0), "png", new File(args[1]));
            System.out.println("Bild: " + args[1]);
            return;
        }
        SwingUtilities.invokeLater(MusterDemo::open);
    }

    static BufferedImage render(Muster m, int w, int h, double t) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double v = m.valueAt(x * 0.5, y * 0.5, t);
                int r = (int) (10 + 50 * v), g = (int) (26 + 150 * v), b = (int) (40 + 130 * v);
                im.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        return im;
    }

    private static void open() {
        Muster m = Muster.breeze();
        FFrame f = new FFrame("Muster-Package · Demo");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(new Color(11, 17, 24));
        long t0 = System.nanoTime();
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                g.drawImage(render(m, Math.max(1, getWidth() / 2), Math.max(1, getHeight() / 2), (System.nanoTime() - t0) / 1e9), 0, 0, getWidth(), getHeight(), null);
            }
        };
        view.setPreferredSize(new Dimension(640, 420));
        FSlider bft = new FSlider(0, 12, 4);
        JLabel lbl = new JLabel("Beaufort 4");
        lbl.setForeground(new Color(230, 234, 238));
        bft.addChangeListener(e -> {
            Muster p = Muster.beaufort(bft.getValue());
            m.windSpeed = p.windSpeed;
            m.strength = p.strength;
            lbl.setText("Beaufort " + bft.getValue());
        });
        JPanel south = new JPanel(new BorderLayout(10, 0));
        south.setBackground(new Color(11, 17, 24));
        south.add(lbl, BorderLayout.WEST);
        south.add(bft, BorderLayout.CENTER);
        root.add(view, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);
        f.setSize(720, 520);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(40, e -> view.repaint()).start();
    }
}
