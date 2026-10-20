package com.dan.store;

import java.util.Locale;

/** Zahlen als Text mit Punkt und ohne überflüssige Nullen, wie im Textformat und in den Skripten. */
public final class Zahl {
    private Zahl() { }

    public static String text(double v) {
        if (Double.isNaN(v)) return "";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        String s = String.format(Locale.ROOT, "%.6f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** Liest Punkt oder Komma; leer oder „NaN“ wird NaN. */
    public static double lies(String s) {
        if (s == null) return Double.NaN;
        s = s.strip();
        if (s.isEmpty() || s.equalsIgnoreCase("nan")) return Double.NaN;
        return Double.parseDouble(s.replace(',', '.'));
    }

    /** Großkreisabstand in km (Haversine, mittlerer Erdradius). */
    public static double abstandKm(double b1, double l1, double b2, double l2) {
        double p1 = Math.toRadians(b1), p2 = Math.toRadians(b2), dp = p2 - p1, dl = Math.toRadians(l2 - l1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * 6371.0 * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
