package com.dan.rungholt.tools;

import com.dan.rungholt.scene.Geo;
import com.dan.rungholt.scene.Site;
import com.dan.rungholt.scene.Terrain;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import java.util.Properties;

/**
 * Phase 2: liest die Küstenhöhen der BAW als GeoTIFF und schreibt das Gelände der Szene nach {@code daten/gelaende}
 * ({@link Terrain}: 2048² zu 8 m, Höhen in m über NHN, Art je Zelle) und ein Prüfbild.
 * <pre>
 * GelaendeImport templates/rasters_BAW/rungholt_topo_10m.tif daten/gelaende docs/status/Gelaende_Import.png
 * </pre>
 * Die Eingabe ist ein Zuschnitt aus {@code topography_max_z_2015-2021_ger.tif} (1,8 GB, ganze deutsche Küste) auf
 * Ost 472 500–490 000, Nord 6 026 500–6 044 000 (ETRS89/UTM 32), etwa mit GDAL:
 * <pre>
 * gdal_translate -projwin 472500 6044000 490000 6026500 -co COMPRESS=LZW topography_max_z_2015-2021_ger.tif rungholt_topo_10m.tif
 * </pre>
 * Je Zelle der Szene wird die Lage nach UTM 32 umgerechnet ({@link Geo#UTM32N}) und bilinear gelesen; fehlen Werte
 * (die Köge auf dem Festland und auf Nordstrand liegen nicht in den Daten), setzt der Import NHN +0,5 m [G] und die
 * Art {@link Terrain#RAND}. Danach ordnet {@link Terrain#classify} jede Zelle: Watt, Vorland, Deich/Warft, Koog.
 */
public final class GelaendeImport {
    private GelaendeImport() { }

    /** Höhe für Zellen ohne Messwert (Köge hinter den Deichen) [G]. */
    public static final double KOOG_FUELLUNG = 0.5;

    public static void main(String[] a) throws Exception {
        File in = new File(a.length > 0 ? a[0] : "templates/rasters_BAW/rungholt_topo_10m.tif");
        File out = new File(a.length > 1 ? a[1] : "daten/gelaende");
        File png = a.length > 2 ? new File(a[2]) : new File("docs/status/Gelaende_Import.png");
        long t0 = System.nanoTime();
        GeoTiff g = GeoTiff.read(in);
        System.out.println("Eingabe: " + g);
        if (g.epsg != 25832 && g.epsg != 0) System.out.println("Achtung: EPSG " + g.epsg + " statt 25832 (ETRS89/UTM 32)");
        Terrain t = build(g, in.getName());
        t.save(out);
        writeImage(t, png);
        System.out.println(t.describe());
        for (String k : new String[]{"warft.x", "warft.z", "warft.y", "watt.km2", "tiefste", "hoechste", "ohneWert"})
            System.out.println("  " + k + " = " + t.props.getProperty(k));
        System.out.printf(Locale.GERMAN, "Gelände nach %s, Prüfbild %s, %.1f s%n", out, png, (System.nanoTime() - t0) / 1e9);
    }

    /** Baut das Gelände aus dem Zuschnitt. */
    public static Terrain build(GeoTiff g, String name) {
        int n = Terrain.N;
        double[] y = new double[n * n];
        boolean[] missing = new boolean[n * n];
        int miss = 0;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double[] ll = Site.latLon(Terrain.cx(i), Terrain.cz(j));
                double[] en = Geo.UTM32N.forward(ll[0], ll[1]);
                double v = sample(g, en[0], en[1]);
                int k = j * n + i;
                if (Double.isNaN(v)) { missing[k] = true; miss++; v = KOOG_FUELLUNG; }
                else { lo = Math.min(lo, v); hi = Math.max(hi, v); }
                y[k] = v;
            }
        byte[] art = Terrain.classify(y, missing);
        short[] h = new short[n * n];
        for (int k = 0; k < h.length; k++) h[k] = Terrain.encode(y[k]);
        Properties p = new Properties();
        p.setProperty("quelle", "BAW Küstenhöhen topography_max_z 2015–2021 (10 m, NHN)");
        p.setProperty("datei", name);
        p.setProperty("raster", n + " x " + n + " zu " + Terrain.CELL + " m, Ecke " + Terrain.X0 + " / " + Terrain.Z0);
        p.setProperty("tiefste", String.format(Locale.ROOT, "%.2f", lo));
        p.setProperty("hoechste", String.format(Locale.ROOT, "%.2f", hi));
        p.setProperty("ohneWert", String.format(Locale.ROOT, "%.4f", miss / (double) (n * n)));
        Terrain t = new Terrain(h, art, p);
        // die Warft von Südfall: höchste Zelle in 600 m um die Lage aus der Datenprüfung
        double[] sf = Site.suedfall();
        double best = -99, bx = 0, bz = 0;
        for (double z = sf[1] - 600; z <= sf[1] + 600; z += Terrain.CELL)
            for (double x = sf[0] - 600; x <= sf[0] + 600; x += Terrain.CELL) {
                double v = t.height(x, z);
                if (v > best) { best = v; bx = x; bz = z; }
            }
        p.setProperty("warft.x", String.format(Locale.ROOT, "%.0f", bx));
        p.setProperty("warft.z", String.format(Locale.ROOT, "%.0f", bz));
        p.setProperty("warft.y", String.format(Locale.ROOT, "%.2f", best));
        p.setProperty("watt.km2", String.format(Locale.ROOT, "%.1f", t.areaBetween(Site.MTNW, Site.MTHW)));
        return t;
    }

    /** Bilinear aus dem Zuschnitt an (Ost, Nord); fehlen einzelne Nachbarn, zählen die übrigen; NaN, wenn keiner da ist. */
    static double sample(GeoTiff g, double east, double north) {
        double u = (east - g.east0) / g.sizeX - 0.5, v = (g.north0 - north) / g.sizeY - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j, s = 0, w = 0;
        for (int b = 0; b <= 1; b++)
            for (int c = 0; c <= 1; c++) {
                float val = g.at(i + c, j + b);
                if (Float.isNaN(val)) continue;
                double wt = (c == 0 ? 1 - fx : fx) * (b == 0 ? 1 - fz : fz);
                s += val * wt;
                w += wt;
            }
        return w > 1e-6 ? s / w : Double.NaN;
    }

    /** Prüfbild 1 024²: Höhe als Farbe mit Schummerung, Deiche und Köge hervorgehoben, Südfall und die Kirche [G] markiert. */
    static void writeImage(Terrain t, File png) throws Exception {
        int s = 1024, n = Terrain.N, f = n / s;
        BufferedImage img = new BufferedImage(s, s + 60, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < s; y++)
            for (int x = 0; x < s; x++) {
                int i = x * f, j = y * f;
                double h = t.at(i, j), hx = t.at(i + f, j) - t.at(i - f, j), hz = t.at(i, j + f) - t.at(i, j - f);
                double shade = Math.max(0.55, Math.min(1.3, 0.95 + (-hx + hz) * 0.25));
                byte art = t.art[j * n + i];
                double[] c;
                if (art == Terrain.KOOG || art == Terrain.RAND) c = new double[]{0.42, 0.55, 0.32};
                else if (art == Terrain.ERHOEHT) c = new double[]{0.30, 0.48, 0.22};
                else if (art == Terrain.VORLAND) c = new double[]{0.55, 0.62, 0.38};
                else if (h < Site.MTNW) { double d = Math.min(1, (Site.MTNW - h) / 20); c = new double[]{0.25 - 0.17 * d, 0.45 - 0.3 * d, 0.62 - 0.3 * d}; }
                else { double d = (h - Site.MTNW) / (Site.MTHW - Site.MTNW); c = new double[]{0.45 + 0.3 * d, 0.40 + 0.25 * d, 0.30 + 0.15 * d}; }
                if (art == Terrain.RAND) shade *= 0.8;
                int r = (int) Math.max(0, Math.min(255, c[0] * shade * 255)), gg = (int) Math.max(0, Math.min(255, c[1] * shade * 255)), b = (int) Math.max(0, Math.min(255, c[2] * shade * 255));
                img.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double sc = s / (n * Terrain.CELL);
        java.util.function.BiConsumer<double[], String> mark = (p, label) -> {
            int x = (int) ((p[0] - Terrain.X0) * sc), y = (int) ((p[1] - Terrain.Z0) * sc);
            g.setColor(new Color(227, 184, 92));
            g.drawOval(x - 7, y - 7, 14, 14);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
            g.drawString(label, x + 10, y + 5);
        };
        mark.accept(new double[]{Double.parseDouble(t.props.getProperty("warft.x", "0")), Double.parseDouble(t.props.getProperty("warft.z", "0"))}, "Südfall");
        mark.accept(new double[]{0, 0}, "Kirche [G]");
        // Ausschnitt F1
        double[] nw = Site.xz(new double[]{Site.NORTH, Site.WEST}), se = Site.xz(new double[]{Site.SOUTH, Site.EAST});
        g.setColor(new Color(240, 244, 243, 160));
        g.drawRect((int) ((nw[0] - Terrain.X0) * sc), (int) ((nw[1] - Terrain.Z0) * sc), (int) ((se[0] - nw[0]) * sc), (int) ((se[1] - nw[1]) * sc));
        g.setColor(new Color(14, 20, 22));
        g.fillRect(0, s, s, 60);
        g.setColor(new Color(232, 236, 234));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(t.describe(), 10, s + 20);
        g.drawString(String.format(Locale.GERMAN, "Raster 2048² zu 8 m · NHN %s bis %s m · Watt zwischen MTnw und MThw %s km² · Warft von Südfall %s m · Rahmen: Ausschnitt F1",
                t.props.getProperty("tiefste"), t.props.getProperty("hoechste"), t.props.getProperty("watt.km2"), t.props.getProperty("warft.y")), 10, s + 42);
        g.dispose();
        File dir = png.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(img, "png", png);
    }
}
