package com.dan.season.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.season.Year;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
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
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung des Jahrs ohne Szene: ein gewürfeltes Jahr vom 1. September des Vorjahrs bis Silvester als Kurven —
 * Tagesmittel der Luft und Klima, Schnee am Boden und auf den Dächern, Eis auf dem Noor und die Wassertemperatur, Reif,
 * darunter Gras, Laub, Herbstfärbung und Schilf als Bänder. Regler: Startwert und Verschiebung der Temperatur (ein
 * harter oder milder Winter). Die vier Tage der Abnahme (16. Januar, April, Juli, Oktober) sind markiert.
 * <pre>
 * SeasonDemo                      Fenster
 * SeasonDemo --bild datei.png     das Jahr 950 als Bild (1800 × 1000)
 * </pre>
 */
public final class SeasonDemo {
    private SeasonDemo() { }

    static final Color BG = new Color(11, 17, 24), INK = new Color(230, 234, 238), MUTED = new Color(130, 145, 158), GRID = new Color(36, 46, 56),
            TEMP = new Color(226, 120, 70), CLIM = new Color(170, 120, 90), SNOW = new Color(225, 235, 245), ROOF = new Color(150, 170, 200),
            ICE = new Color(110, 190, 230), WATER = new Color(60, 120, 170), FROST = new Color(190, 200, 255);

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            Year y = new Year();
            BufferedImage img = new BufferedImage(1800, 1000, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            draw(g, 1800, 1000, y, 950);
            g.dispose();
            File f = new File(a[1]);
            if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("Bild: " + f.getPath());
            return;
        }
        SwingUtilities.invokeLater(SeasonDemo::window);
    }

    static void window() {
        Year y = new Year();
        JComponent plot = new JComponent() {
            @Override protected void paintComponent(Graphics g0) { draw((Graphics2D) g0, getWidth(), getHeight(), y, 950); }
        };
        plot.setPreferredSize(new Dimension(1300, 800));
        JPanel side = new JPanel();
        side.setBackground(BG);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = label("com.dan.season"), seedL = label("Startwert " + y.seed), shiftL = label("Temperatur ±0,0 K");
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FSlider seed = new FSlider(1, 60, (int) y.seed), shift = new FSlider(-40, 40, 0);
        seed.addChangeListener(e -> { y.seed = seed.getValue(); seedL.setText("Startwert " + y.seed); plot.repaint(); });
        shift.addChangeListener(e -> { y.shift = shift.getValue() / 10.0; shiftL.setText(String.format(Locale.GERMAN, "Temperatur %+.1f K", y.shift)); plot.repaint(); });
        JLabel help = label("<html><body style='width:240px;color:#96a5b2'>Ein Jahr nach dem Klima von Schleswig: Tagesmittel aus einer "
                + "Reihe mit Gedächtnis, Niederschlag an Regentagen. Schnee nach dem Gradstundenverfahren mit Sonne, Eis nach Stefan, "
                + "erst wenn das flache Noor ausgekühlt ist. Startwert 14 ist der Winter der Szene (950).</body></html>");
        for (Component c : new Component[]{title, seedL, seed, shiftL, shift, help}) {
            if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(8));
        }
        side.setPreferredSize(new Dimension(290, 800));
        FFrame fr = new FFrame("com.dan.season – das Jahr am Noor");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(plot, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        fr.setPreferredFrameSize(new Dimension(1600, 860));
        fr.setSize(1600, 860);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    static JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setForeground(INK);
        return l;
    }

    /** Das Jahr als Kurven in (0, 0, w, h). */
    public static void draw(Graphics2D g, int w, int h, Year y, int year) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        List<Year.State> morning = y.trace(year, 8);
        List<Year.State> tr = y.trace(year, 12);
        int n = tr.size();
        int x0 = 70, x1 = w - 30, top = 50;
        double dx = (x1 - x0) / (double) (n - 1);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
        g.setColor(INK);
        g.drawString(String.format(Locale.GERMAN, "Das Jahr %d am Noor · Startwert %d · Temperatur %+.1f K · Klima Schleswig", year, y.seed, y.shift), x0, 30);
        // Felder: Temperatur (oben), Schnee und Eis (Mitte), Pflanzen (unten)
        int tA = top, tB = top + (int) ((h - top) * 0.36), sA = tB + 26, sB = sA + (int) ((h - top) * 0.34), pA = sB + 26, pB = h - 40;
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        // Monatsgitter
        String[] mon = {"Sep", "Okt", "Nov", "Dez", "Jan", "Feb", "Mär", "Apr", "Mai", "Jun", "Jul", "Aug", "Sep", "Okt", "Nov", "Dez"};
        int[] start = {-121, -91, -60, -30, 1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};
        for (int i = 0; i < start.length; i++) {
            int x = (int) (x0 + (start[i] - Year.FIRST) * dx);
            g.setColor(start[i] == 1 ? MUTED : GRID);
            g.drawLine(x, tA, x, pB);
            g.setColor(MUTED);
            g.drawString(mon[i] + (start[i] == 1 ? " " + year : ""), x + 4, pB + 16);
        }
        // Abnahme-Tage
        for (int[] md : new int[][]{{1, 16}, {4, 16}, {7, 16}, {10, 16}}) {
            int d = Year.dayOfYear(md[0], md[1]);
            int x = (int) (x0 + (d - Year.FIRST) * dx);
            g.setColor(new Color(226, 67, 47, 160));
            g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{5, 4}, 0));
            g.drawLine(x, tA, x, pB);
            g.setStroke(new BasicStroke(1));
            g.drawString(md[1] + "." + md[0] + ".", x + 3, tA + 12);
        }
        // Temperatur −15..25 °C
        double tLo = -15, tHi = 25;
        g.setColor(GRID);
        for (int c = -10; c <= 20; c += 10) { int yy = (int) (tB - (c - tLo) / (tHi - tLo) * (tB - tA)); g.drawLine(x0, yy, x1, yy); g.setColor(MUTED); g.drawString(c + " °C", 14, yy + 4); g.setColor(GRID); }
        int zero = (int) (tB - (0 - tLo) / (tHi - tLo) * (tB - tA));
        g.setColor(MUTED);
        g.drawLine(x0, zero, x1, zero);
        Path2D t = new Path2D.Double(), c = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            int d = i + Year.FIRST, dd = d <= 0 ? d + 365 : d;
            double x = x0 + i * dx;
            double tv = y.dayMean(year, d), cv = y.climate.meanTemp(dd) + y.shift;
            double yt = tB - (Math.max(tLo, Math.min(tHi, tv)) - tLo) / (tHi - tLo) * (tB - tA), yc = tB - (cv - tLo) / (tHi - tLo) * (tB - tA);
            if (i == 0) { t.moveTo(x, yt); c.moveTo(x, yc); } else { t.lineTo(x, yt); c.lineTo(x, yc); }
        }
        g.setColor(CLIM);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{6, 4}, 0));
        g.draw(c);
        g.setStroke(new BasicStroke(1.6f));
        g.setColor(TEMP);
        g.draw(t);
        g.drawString("Tagesmittel der Luft (gewürfelt)  · · ·  Klima", x0 + 8, tB - 6);
        // Schnee 0..40 cm, Eis 0..40 cm
        double sHi = 0.4;
        for (int k = 1; k <= 4; k++) { int yy = (int) (sB - k * 0.1 / sHi * (sB - sA)); g.setColor(GRID); g.drawLine(x0, yy, x1, yy); g.setColor(MUTED); g.drawString(k * 10 + " cm", 14, yy + 4); }
        Path2D snow = new Path2D.Double(), roof = new Path2D.Double(), ice = new Path2D.Double();
        snow.moveTo(x0, sB);
        for (int i = 0; i < n; i++) {
            Year.State s = tr.get(i);
            double x = x0 + i * dx;
            snow.lineTo(x, sB - Math.min(sHi, s.snowDepth()) / sHi * (sB - sA));
            double yr = sB - Math.min(sHi, s.roofSnow()) / sHi * (sB - sA), yi = sB - Math.min(sHi, s.ice()) / sHi * (sB - sA);
            if (i == 0) { roof.moveTo(x, yr); ice.moveTo(x, yi); } else { roof.lineTo(x, yr); ice.lineTo(x, yi); }
            if (morning.get(i).frost() > 0.5) { g.setColor(FROST); g.fillRect((int) x, sA - 8, Math.max(1, (int) dx), 5); }
        }
        snow.lineTo(x1, sB);
        snow.closePath();
        g.setColor(new Color(SNOW.getRed(), SNOW.getGreen(), SNOW.getBlue(), 120));
        g.fill(snow);
        g.setColor(ROOF);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(roof);
        g.setColor(ICE);
        g.setStroke(new BasicStroke(2f));
        g.draw(ice);
        g.setStroke(new BasicStroke(1));
        g.setColor(INK);
        g.drawString("Schnee am Boden (Fläche) · auf den Dächern (Linie) · Eis auf dem Noor (blau) · Reif um 8 Uhr (Striche oben)", x0 + 8, sA + 14);
        // Pflanzen als Bänder
        String[] names = {"Gras", "Laub", "Herbst", "Schilf"};
        int bh = (pB - pA) / 4;
        for (int b = 0; b < 4; b++) {
            int yy = pA + b * bh;
            for (int i = 0; i < n; i++) {
                Year.State s = tr.get(i);
                double v = switch (b) { case 0 -> s.grassLush(); case 1 -> s.foliage(); case 2 -> s.autumn(); default -> s.reedSeason(); };
                Color col = switch (b) {
                    case 0 -> mix(new Color(95, 85, 55), new Color(70, 150, 40), v);
                    case 1 -> mix(new Color(70, 60, 52), new Color(50, 110, 35), v);
                    case 2 -> mix(new Color(70, 60, 52), mix(new Color(50, 110, 35), new Color(190, 110, 30), v), s.foliage());
                    default -> v < 0.75 ? mix(new Color(70, 120, 40), new Color(190, 150, 70), v / 0.75) : mix(new Color(190, 150, 70), new Color(180, 170, 145), (v - 0.75) / 0.25);
                };
                g.setColor(col);
                g.fillRect((int) (x0 + i * dx), yy + 2, Math.max(1, (int) Math.ceil(dx)), bh - 4);
            }
            g.setColor(MUTED);
            g.drawString(names[b], 14, yy + bh / 2 + 4);
        }
    }

    static Color mix(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t), (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }
}
