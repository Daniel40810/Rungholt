package com.dan.store;

import java.util.Locale;

/**
 * Geometrien als Text (Well-Known Text) für Oracle Spatial, ohne {@code sdoapi}: Die App schreibt sie mit
 * {@code SDO_GEOMETRY(?, 8307)} oder {@code SDO_UTIL.FROM_WKTGEOMETRY}, Länge vor Breite. Sieben Nachkommastellen
 * reichen auf rund 1 cm.
 */
public final class Wkt {
    private Wkt() { }

    /** Stellen hinter dem Komma (7 ≈ 1 cm). */
    public static int stellen = 7;

    private static String n(double v) {
        String s = String.format(Locale.ROOT, "%." + stellen + "f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String pt(double[] p) { return n(p[0]) + " " + n(p[1]); }

    /** POINT (lon lat) */
    public static String punkt(double lon, double lat) { return "POINT (" + n(lon) + " " + n(lat) + ")"; }

    /** LINESTRING aus {lon, lat}-Paaren. */
    public static String linie(double[][] p) {
        if (p.length < 2) throw new IllegalArgumentException("Linie braucht zwei Punkte");
        StringBuilder b = new StringBuilder("LINESTRING (");
        for (int i = 0; i < p.length; i++) b.append(i > 0 ? ", " : "").append(pt(p[i]));
        return b.append(')').toString();
    }

    /**
     * POLYGON aus {lon, lat}-Paaren (Außenring, ohne Wiederholung des ersten Punkts). Oracle will den Außenring gegen
     * den Uhrzeigersinn; er wird bei Bedarf umgedreht.
     */
    public static String flaeche(double[][] p) {
        if (p.length < 3) throw new IllegalArgumentException("Fläche braucht drei Punkte");
        double a = 0;
        for (int i = 0; i < p.length; i++) {
            double[] q = p[i], r = p[(i + 1) % p.length];
            a += q[0] * r[1] - r[0] * q[1];
        }
        StringBuilder b = new StringBuilder("POLYGON ((");
        for (int k = 0; k <= p.length; k++) {
            int i = a >= 0 ? k % p.length : (p.length - k) % p.length;
            b.append(k > 0 ? ", " : "").append(pt(p[i]));
        }
        return b.append("))").toString();
    }

    /** Die Zahlenpaare eines WKT (für Prüfungen und Dateien): {lon, lat} je Punkt. */
    public static double[][] punkte(String wkt) {
        String in = wkt.replaceAll("[A-Za-z()]", " ").strip();
        if (in.isEmpty()) return new double[0][];
        String[] ps = in.split(",");
        double[][] r = new double[ps.length][];
        for (int i = 0; i < ps.length; i++) {
            String[] xy = ps[i].strip().split("\\s+");
            r[i] = new double[]{Double.parseDouble(xy[0]), Double.parseDouble(xy[1])};
        }
        return r;
    }
}
