package com.dan.sky;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Die Sterne bis 6,5 mag (8 920, alles, was das bloße Auge unter dunklem Himmel sieht) und die Sternbildfiguren.
 * <ul>
 * <li>{@code stars.bin}: aus der HYG-Datenbank v4.1 (astronexus, CC BY-SA 4.0), Position J2000 in Grad, Helligkeit V
 * in mag, Farbindex B−V; nach Helligkeit sortiert. Je Stern vier Zahlen, big-endian.</li>
 * <li>{@code figures.txt}: die Figuren aus dem Projekt Sternbild (BuiltinCatalog), 24 Sternbilder mit deutschen Namen;
 * seit Glacier Phase 3 dazu vier des Südhimmels (Kreuz des Südens, Zentaur, Kiel des Schiffs, Südliches Dreieck).</li>
 * </ul>
 * Beides liegt als Ressource neben dieser Klasse und wird einmal gelesen.
 */
public final class StarCatalog {
    /** Rektaszension und Deklination J2000 in Grad, Helligkeit, B−V. */
    public final float[] ra, dec, mag, bv;
    public final List<Figure> figures;

    /** Eine Figur: Kürzel, deutscher Name, Linien als (RA1, Dec1, RA2, Dec2) in Grad. */
    public record Figure(String code, String name, double[][] lines) { }

    private static volatile StarCatalog instance;

    /** Der Katalog (beim ersten Aufruf gelesen). */
    public static StarCatalog get() {
        StarCatalog c = instance;
        if (c == null) {
            synchronized (StarCatalog.class) {
                if (instance == null) instance = new StarCatalog();
                c = instance;
            }
        }
        return c;
    }

    private StarCatalog() {
        try (InputStream raw = open("stars.bin"); DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(raw))) {
            byte[] magic = new byte[4];
            in.readFully(magic);
            if (!"STR1".equals(new String(magic, StandardCharsets.US_ASCII))) throw new IOException("stars.bin: falsche Kennung");
            int n = in.readInt();
            ra = new float[n];
            dec = new float[n];
            mag = new float[n];
            bv = new float[n];
            for (int i = 0; i < n; i++) {
                ra[i] = in.readFloat();
                dec[i] = in.readFloat();
                mag[i] = in.readFloat();
                bv[i] = in.readFloat();
            }
        } catch (IOException e) {
            throw new IllegalStateException("sky: Sternkatalog nicht lesbar: " + e.getMessage(), e);
        }
        List<Figure> f = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(open("figures.txt"), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) {
                if (l.isBlank() || l.startsWith("#")) continue;
                String[] p = l.split(";");
                String[] segs = p[2].split(",");
                double[][] lines = new double[segs.length][];
                for (int i = 0; i < segs.length; i++) {
                    String[] v = segs[i].trim().split("\\s+");
                    lines[i] = new double[]{Double.parseDouble(v[0]), Double.parseDouble(v[1]), Double.parseDouble(v[2]), Double.parseDouble(v[3])};
                }
                f.add(new Figure(p[0], p[1], lines));
            }
        } catch (IOException e) {
            throw new IllegalStateException("sky: Sternbildfiguren nicht lesbar: " + e.getMessage(), e);
        }
        figures = List.copyOf(f);
    }

    static InputStream open(String name) throws IOException {
        InputStream in = StarCatalog.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Ressource fehlt: " + name);
        return in;
    }

    public int size() { return mag.length; }

    /** Index des hellsten Sterns nahe (ra, dec) innerhalb von tol Grad, sonst −1. */
    public int find(double raDeg, double decDeg, double tol) {
        double[] v = Celestial.equatorial(raDeg, decDeg);
        double best = Math.cos(Math.toRadians(tol));
        int idx = -1;
        for (int i = 0; i < size(); i++) {
            double[] w = Celestial.equatorial(ra[i], dec[i]);
            double c = v[0] * w[0] + v[1] * w[1] + v[2] * w[2];
            if (c > best) { best = c; idx = i; }
        }
        return idx;
    }

    /** Oberflächentemperatur in K aus B−V (Ballesteros 2012). */
    /**
     * Seit Glacier Phase 3: die Magellanschen Wolken, die in den Helligkeitsstufen der Milchstraße (d3-celestial) fehlen.
     * Helligkeit 0..1 an RA/Dec (Grad, J2000) im Maß der Milchstraßenkarte: die Große Wolke (LMC) bei 5h 23m, −69,8°,
     * rund 10° × 9° mit dem hellen Balken, die Kleine (SMC) bei 0h 53m, −72,8°, rund 5° × 3° [G]. Mit bloßem Auge
     * gut so hell wie die hellen Teile der Milchstraße.
     */
    public static double magellanic(double raDeg, double decDeg) {
        return cloudBlob(raDeg, decDeg, 80.89, -69.76, 4.6, 3.9, 0.0, 0.55)        // LMC, Scheibe
                + cloudBlob(raDeg, decDeg, 80.0, -69.6, 2.6, 0.9, 20.0, 0.25)      // LMC, Balken
                + cloudBlob(raDeg, decDeg, 13.19, -72.83, 2.2, 1.3, 45.0, 0.5);   // SMC
    }

    /** Ein weicher, gedrehter Fleck (Gauß) am Himmel; Abstände in der Tangentialebene um die Mitte. */
    static double cloudBlob(double ra, double dec, double ra0, double dec0, double sa, double sb, double rotDeg, double peak) {
        double dra = ((ra - ra0) % 360 + 540) % 360 - 180;
        double x = dra * Math.cos(Math.toRadians(dec0)), y = dec - dec0;
        double r = Math.toRadians(rotDeg), u = x * Math.cos(r) + y * Math.sin(r), v = -x * Math.sin(r) + y * Math.cos(r);
        return peak * Math.exp(-0.5 * (u * u / (sa * sa) + v * v / (sb * sb)));
    }

    public static double temperature(double bv) {
        return 4600 * (1 / (0.92 * bv + 1.7) + 1 / (0.92 * bv + 0.62));
    }

    /**
     * Farbe eines Sterns als RGB mit Leuchtdichte 1: Planck bei 610, 550 und 465 nm, auf Grün bezogen und zu 60 %
     * gesättigt (Sterne sehen fürs Auge blasser aus als ihr Spektrum, weil schwaches Licht die Zapfen kaum reizt).
     */
    public static double[] color(double bv) {
        double t = temperature(Math.max(-0.4, Math.min(2.0, bv)));
        double r = planck(610e-9, t), g = planck(550e-9, t), b = planck(465e-9, t);
        double[] c = {r / g, 1, b / g};
        double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        for (int i = 0; i < 3; i++) c[i] = 1 + 0.6 * (c[i] / y - 1);
        y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
        for (int i = 0; i < 3; i++) c[i] /= y;
        return c;
    }

    static double planck(double lambda, double t) {
        return 1 / (Math.pow(lambda, 5) * (Math.exp(0.014388 / (lambda * t)) - 1));
    }
}
