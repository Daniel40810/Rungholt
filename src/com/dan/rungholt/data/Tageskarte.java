package com.dan.rungholt.data;

import com.dan.rungholt.scene.Terrain;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Die Tageskarte (F35 A): eine Draufsicht als PNG mit Rungholt (Warften, Seedeich, Kette, Sielzüge, Kirche), den
 * Fundstellen (eigene rot), der nassen Fläche des letzten Niedrigwassers des Tages, den Zählungen der Seehunde und
 * Vögel und, in einer Mandränkenacht, Bruchstelle und Bresche. Dieselbe Karte zeichnet QGIS aus
 * {@code RH_TAGESKARTE_V}. Gelesen wird über den {@link Speicher}; im Hintergrund das Gelände von heute.
 */
public final class Tageskarte {
    private Tageskarte() { }

    public static final int W = 1600, H = 1200;
    /** Ausschnitt um die Kirche (m): 12 × 9 km. */
    public static final double X0 = -7000, Z0 = -4500, BREITE = 12000;
    static final Color HINTER = new Color(11, 18, 25), WATT = new Color(150, 138, 112), PRIEL = new Color(40, 70, 84), LAND = new Color(92, 112, 70),
            TEXT = new Color(232, 236, 234), GRAU = new Color(146, 163, 163), NASS = new Color(70, 140, 170, 120), WARFT = new Color(178, 120, 74),
            DEICH = new Color(214, 196, 150), FUND = new Color(240, 210, 120), EIGEN = new Color(220, 80, 60), SEEHUND = new Color(200, 200, 205), VOGEL = new Color(250, 250, 250),
            BRUCH = new Color(230, 60, 60);

    /** Was gezeichnet wurde, für die Meldung. */
    public record Ergebnis(File datei, int tiden, int zaehlungen, int fundstellen, boolean wasserlinie) { }

    /** Zeichnet die Karte des Tages und legt sie nach dir (docs/status) als Tageskarte_yyyy-mm-dd.png. */
    public static Ergebnis zeichnen(Speicher sp, Terrain t, LocalDate tag, File dir) throws Exception {
        List<Daten.Tide> ts = sp.tidenAm(tag);
        List<Daten.Zaehlung> zs = sp.zaehlungenAm(tag);
        List<Daten.Fundstelle> fs = sp.fundstellen();
        List<Daten.Warft> ws = sp.warften();
        List<Daten.Bauwerk> bs = sp.bauwerke();
        Daten.Tide nw = null;
        for (Daten.Tide x : ts) if (x.wasserlinie() != null) nw = x;
        double s = W / BREITE, ox = X0, oz = Z0;

        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(HINTER);
        g.fillRect(0, 0, W, H);
        if (t != null) {
            for (int py = 0; py < H; py += 2)
                for (int px = 0; px < W; px += 2) {
                    double x = ox + px / s, z = oz + py / s, h = t.height(x, z);
                    Color c = h > 1.6 ? LAND : h < -2.5 ? PRIEL : WATT;
                    double sh = h > 1.6 ? 1 : h < -2.5 ? 0.8 + 0.2 * Math.max(0, 1 + (h + 2.5) / 10) : 0.75 + 0.25 * Math.max(0, Math.min(1, (h + 2.5) / 4));
                    g.setColor(new Color(c8(c.getRed() * sh), c8(c.getGreen() * sh), c8(c.getBlue() * sh)));
                    g.fillRect(px, py, 2, 2);
                }
        }
        // die nasse Fläche des letzten Niedrigwassers
        if (nw != null) flaeche(g, nw.wasserlinie(), ox, oz, s, NASS);
        // Rungholt 1362
        for (Daten.Bauwerk b : bs) {
            switch (b.art()) {
                case "SEEDEICH" -> linie(g, b.geom(), ox, oz, s, DEICH, 3f, false);
                case "KETTE" -> linie(g, b.geom(), ox, oz, s, new Color(WARFT.getRed(), WARFT.getGreen(), WARFT.getBlue(), 120), 2f, true);
                case "SIELZUG" -> linie(g, b.geom(), ox, oz, s, new Color(120, 170, 190), 1.6f, false);
                case "KIRCHE", "HAFENBECKEN" -> flaeche(g, b.geom(), ox, oz, s, new Color(200, 90, 60));
                default -> { }
            }
        }
        for (Daten.Warft w : ws) {
            flaeche(g, w.flaeche(), ox, oz, s, new Color(WARFT.getRed(), WARFT.getGreen(), WARFT.getBlue(), 200));
        }
        // Fundstellen
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        for (Daten.Fundstelle f : fs) {
            boolean e = Daten.EIGEN.equals(f.herkunft());
            int x = px(f.x(), ox, s), y = py(f.z(), oz, s);
            g.setColor(e ? EIGEN : FUND);
            g.fillRect(x - 3, y - 3, 7, 7);
            if (e) { g.setColor(TEXT); g.drawString(f.name(), x + 6, y + 4); }
        }
        // Zählungen
        int seehunde = 0, voegel = 0;
        for (Daten.Zaehlung z : zs) {
            int x = px(z.x(), ox, s), y = py(z.z(), oz, s);
            boolean seal = "SEEHUND".equals(z.art());
            int r = (int) Math.max(3, Math.min(18, Math.sqrt(z.anzahl()) * (seal ? 2 : 0.6)));
            g.setColor(seal ? new Color(SEEHUND.getRed(), SEEHUND.getGreen(), SEEHUND.getBlue(), 170) : new Color(255, 255, 255, 110));
            g.fillOval(x - r, y - r, 2 * r, 2 * r);
            if (seal) { seehunde += z.anzahl(); g.setColor(TEXT); g.drawString(z.anzahl() + " Seehunde", x + r + 3, y + 4); } else voegel += z.anzahl();
        }
        // Mandränke
        int laeufe = 0;
        for (Daten.MandraenkeLauf m : sp.mandraenkeLaeufe()) {
            if (!Daten.tag(m.beginnMs()).equals(tag.toString())) continue;
            laeufe++;
            if (m.bresche() != null) linie(g, m.bresche(), ox, oz, s, BRUCH, 5f, false);
            if (m.bruchstelle() != null) {
                int x = px(m.bruchstelle()[0], ox, s), y = py(m.bruchstelle()[1], oz, s);
                g.setColor(BRUCH);
                g.setStroke(new BasicStroke(2f));
                g.drawOval(x - 10, y - 10, 20, 20);
                g.setColor(TEXT);
                g.drawString("Bruch " + (m.bruchText() == null ? "(nicht erreicht)" : m.bruchText()), x + 13, y + 4);
            }
        }
        // die Kirche zur Orientierung
        int kx = px(0, ox, s), ky = py(0, oz, s);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        g.drawString("Kirche", kx + 8, ky - 6);
        // Kopf, Legende, Maßstab
        g.setColor(new Color(11, 18, 25, 220));
        g.fillRect(0, 0, W, 64);
        g.fillRect(0, H - 48, W, 48);
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 22));
        g.drawString("Rungholt · Tageskarte " + DateTimeFormatter.ofPattern("dd.MM.uuuu").format(tag) + " · " + sp.wo(), 16, 30);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(GRAU);
        StringBuilder tl = new StringBuilder();
        for (Daten.Tide x : ts) tl.append(tl.length() > 0 ? ", " : "").append(x.art()).append(' ').append(Daten.text(x.ms()), 11, 16)
                .append(String.format(Locale.GERMAN, " %+.2f m", x.pegelM()));
        g.drawString(String.format(Locale.GERMAN, "%d Tiden (%s) · %d Seehunde, %d Wattvögel · %d Warften, %d Fundstellen%s%s",
                ts.size(), tl.length() == 0 ? "keine in der Chronik" : tl, seehunde, voegel, ws.size(), fs.size(),
                nw == null ? "" : " · nass bei NW " + Daten.text(nw.ms()).substring(11, 16), laeufe > 0 ? " · Mandränke" : ""), 16, 52);
        int ly = H - 20, lx = 16;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        for (Object[] e : new Object[][]{{NASS, "nass bei Niedrigwasser"}, {WARFT, "Warft 1362"}, {DEICH, "Seedeich 1362"}, {FUND, "Fundstelle"}, {EIGEN, "eigene Fundstelle"},
                {SEEHUND, "Seehunde"}, {BRUCH, "Bruch"}}) {
            Color c = (Color) e[0];
            g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue()));
            g.fillOval(lx, ly - 11, 12, 12);
            g.setColor(TEXT);
            g.drawString((String) e[1], lx + 18, ly);
            lx += 34 + g.getFontMetrics().stringWidth((String) e[1]);
        }
        double bar = 2000;
        int bw = (int) (bar * s);
        g.setColor(TEXT);
        g.fillRect(W - 40 - bw, H - 16, bw, 4);
        g.drawString("2 km · Norden oben", W - 40 - bw, H - 24);
        g.dispose();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        File f = new File(dir, "Tageskarte_" + tag + ".png");
        ImageIO.write(img, "png", f);
        return new Ergebnis(f, ts.size(), zs.size(), fs.size(), nw != null);
    }

    private static int c8(double v) { return (int) Math.max(0, Math.min(255, Math.round(v))); }
    private static int px(double x, double ox, double s) { return (int) Math.round((x - ox) * s); }
    private static int py(double z, double oz, double s) { return (int) Math.round((z - oz) * s); }

    private static void linie(Graphics2D g, Daten.Geom geom, double ox, double oz, double s, Color c, float w, boolean gestrichelt) {
        if (geom == null) return;
        g.setColor(c);
        g.setStroke(gestrichelt ? new BasicStroke(w, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{8, 6}, 0) : new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (double[][] p : geom.teile()) {
            if (p.length < 2) continue;
            Path2D.Double path = new Path2D.Double();
            path.moveTo((p[0][0] - ox) * s, (p[0][1] - oz) * s);
            for (int i = 1; i < p.length; i++) path.lineTo((p[i][0] - ox) * s, (p[i][1] - oz) * s);
            g.draw(path);
        }
    }

    /** Eine Fläche oder Mehrfachfläche (mit Löchern, gerade-ungerade) füllen. */
    private static void flaeche(Graphics2D g, Daten.Geom geom, double ox, double oz, double s, Color c) {
        if (geom == null) return;
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        List<List<double[][]>> teile = geom.art().equals("MULTIFLAECHE") ? geom.ringe() : List.of(List.<double[][]>of(geom.xz()));
        for (List<double[][]> t : teile)
            for (double[][] p : t) {
                if (p.length < 3) continue;
                path.moveTo((p[0][0] - ox) * s, (p[0][1] - oz) * s);
                for (int i = 1; i < p.length; i++) path.lineTo((p[i][0] - ox) * s, (p[i][1] - oz) * s);
                path.closePath();
            }
        g.setColor(c);
        g.fill(path);
    }
}
