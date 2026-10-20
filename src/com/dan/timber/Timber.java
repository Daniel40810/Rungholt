package com.dan.timber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Der Holzbau: Häuser aus Regeln statt aus Modellen. Ein Haus ist ein {@link House} (Typ, Lage, Richtung, Streckung,
 * Zustand); {@link Builder} macht aus jedem {@link HouseType} drei Netze (nah, mittel, fern), die die Grafik
 * instanziert zeichnet. Dazu Zäune, Bohlenwege, Palisaden und Landebrücken ({@link Structures}).
 * <p>
 * Für andere Packages: Rauchlöcher ({@link #smokeHoles}) für Rauch, Kollisionskästen ({@link #box}) für Regen, Wasser
 * und Kamera. Je Bild wählt {@link #select} für jedes Haus die Stufe nach der Entfernung und lässt aus, was hinter
 * dem Auge oder zu weit weg liegt.
 * <p>
 * Nur Java 21; keine Abhängigkeit zu anderen Packages (Paketvertrag).
 */
public final class Timber {
    /** Stufen: bis wohin nah (Stufe 0) und mittel (Stufe 1) gezeichnet wird, bis wohin überhaupt (m). */
    public enum Quality {
        LOW(35, 160, 2500), MEDIUM(60, 280, 4000), HIGH(90, 420, 6000), ULTRA(140, 650, 9000);
        public final double near, mid, far;
        Quality(double near, double mid, double far) { this.near = near; this.mid = mid; this.far = far; }
    }

    /** Zahlen je Instanz: x, y, z, Richtung (rad), Streckung Länge, Breite, Zustand, Zufall 0..1. */
    public static final int INSTANCE = 8;
    public static final int LODS = 3;

    public volatile Quality quality = Quality.MEDIUM;

    /**
     * Seit Haithabu Phase 15: Brand je Haus für die Grafik, 0 = heil; bis 1 verkohlt das Haus (stehend), über 1 ist
     * es eingestürzt (1 + Verkohlung): ein niedriger, schwarzer Haufen. null = kein Brand.
     */
    @FunctionalInterface public interface Burn { double at(House h); }
    public volatile Burn burn;

    /** Brand eines Hauses nach {@link #burn} (0 ohne). */
    public double burnOf(House h) { Burn b = burn; return b == null ? 0 : Math.max(0, Math.min(2, b.at(h))); }
    private final Mesh[][] meshes = new Mesh[HouseType.values().length][LODS];

    /** Das Netz eines Typs in einer Stufe (einmal gebaut, dann gemerkt). */
    public synchronized Mesh mesh(HouseType t, int lod) {
        Mesh m = meshes[t.ordinal()][lod];
        if (m == null) meshes[t.ordinal()][lod] = m = Builder.build(t, lod);
        return m;
    }

    /** Ergebnis der Auswahl: je Typ und Stufe die Instanzen (INSTANCE Zahlen je Haus) und ihre Zahl. */
    public static final class Selection {
        public final float[][][] data = new float[HouseType.values().length][LODS][];
        public final int[][] count = new int[HouseType.values().length][LODS];
        public int culled;

        public int total(int lod) { int s = 0; for (int[] c : count) s += c[lod]; return s; }

        public long triangles(Timber t) {
            long s = 0;
            for (HouseType h : HouseType.values()) for (int l = 0; l < LODS; l++) s += (long) count[h.ordinal()][l] * t.mesh(h, l).triangles();
            return s;
        }
    }

    /**
     * Wählt für jedes Haus die Stufe nach der Entfernung zum Auge und lässt Häuser hinter dem Auge (fwd, Blickrichtung,
     * darf null sein; mit halbem Bildwinkel cosHalfFov) und jenseits von {@link Quality#far} aus.
     */
    public Selection select(List<House> houses, double[] eye, double[] fwd, double cosHalfFov) {
        Selection s = new Selection();
        Quality q = quality;
        int nt = HouseType.values().length;
        for (int t = 0; t < nt; t++) for (int l = 0; l < LODS; l++) s.data[t][l] = new float[16 * INSTANCE];
        for (House h : houses) {
            double dx = h.x() - eye[0], dy = h.y() + 3 - eye[1], dz = h.z() - eye[2], d = Math.sqrt(dx * dx + dy * dy + dz * dz), r = h.radius();
            if (d - r > q.far) { s.culled++; continue; }
            if (fwd != null && d > r) {
                // Kegel des Blicks, um den Radius erweitert
                double c = (dx * fwd[0] + dy * fwd[1] + dz * fwd[2]) / d, sinR = Math.min(1, r / d);
                double cosR = Math.sqrt(1 - sinR * sinR), sinF = Math.sqrt(Math.max(0, 1 - cosHalfFov * cosHalfFov));
                double cosLimit = cosHalfFov * cosR - sinF * sinR;
                if (c < cosLimit) { s.culled++; continue; }
            }
            int lod = d - r * 0.5 < q.near ? 0 : d - r * 0.5 < q.mid ? 1 : 2;
            int t = h.type().ordinal();
            float[] a = s.data[t][lod];
            int k = s.count[t][lod] * INSTANCE;
            if (k + INSTANCE > a.length) s.data[t][lod] = a = java.util.Arrays.copyOf(a, a.length * 2);
            pack(h, a, k, burnOf(h));
            s.count[t][lod]++;
        }
        return s;
    }

    /** Ein Haus in INSTANCE Zahlen. */
    public static void pack(House h, float[] a, int k) { pack(h, a, k, 0); }

    /**
     * Ein Haus in INSTANCE Zahlen mit Brand 0..2 (seit Haithabu Phase 15): Der Brand steckt im Zufall als
     * 2 · round(Brand · 255) + Zufall; der Shader nimmt beides wieder auseinander ({@link #unpackBurn}).
     */
    public static void pack(House h, float[] a, int k, double burn) {
        a[k] = (float) h.x();
        a[k + 1] = (float) h.y();
        a[k + 2] = (float) h.z();
        a[k + 3] = (float) Math.toRadians(h.headingDeg());
        a[k + 4] = (float) h.lengthScale();
        a[k + 5] = (float) h.widthScale();
        a[k + 6] = (float) h.state();
        double rnd = (h.seed() * 0x9E3779B97F4A7C15L >>> 40) / (double) (1L << 24);
        a[k + 7] = (float) (Math.min(0.999, rnd) + 2 * Math.round(Math.max(0, Math.min(2, burn)) * 255));
    }

    /** Aus der letzten Zahl einer Instanz: {Brand 0..2, Zufall 0..1} (wie der Shader). */
    public static double[] unpackBurn(float w) {
        double q = Math.floor(w * 0.5);
        return new double[]{q / 255, w - 2 * q};
    }

    /** Rauchlöcher eines Hauses in Szenenmaßen (für {@code smoke}). */
    public static List<double[]> smokeHoles(House h) {
        List<double[]> l = new ArrayList<>();
        for (double[] p : Builder.smokeHoles(h.type())) l.add(h.toWorld(p[0], p[1], p[2]));
        return l;
    }

    /**
     * Kollisionskasten eines Hauses: Mitte {x, y, z}, halbe Ausdehnung entlang des Firsts, nach oben, quer; Richtung
     * in Grad (wie {@link House#headingDeg}). Umfasst Dach und Traufe.
     */
    public record Box(double x, double y, double z, double halfLength, double halfHeight, double halfWidth, double headingDeg) {
        /** Liegt der Punkt drin? */
        public boolean contains(double px, double py, double pz) {
            double h = Math.toRadians(headingDeg);
            double dx = px - x, dz = pz - z;
            double u = dx * Math.sin(h) - dz * Math.cos(h), v = dx * Math.cos(h) + dz * Math.sin(h);
            return Math.abs(u) <= halfLength && Math.abs(py - y) <= halfHeight && Math.abs(v) <= halfWidth;
        }
    }

    public static Box box(House h) {
        HouseType t = h.type();
        double hh = (t.height() + t.sunk) / 2;
        return new Box(h.x(), h.y() - t.sunk + hh, h.z(), t.outerHalfLength() + (h.length() - t.length) / 2, hh, t.outerHalfWidth() + (h.width() - t.width) / 2, h.headingDeg());
    }

    /** „Holzbau: 6 Typen, nah 1 228 / mittel 76 / fern 18 Dreiecke (Hallenhaus)“ */
    public String describe() {
        StringBuilder b = new StringBuilder("Holzbau: 6 Typen");
        for (HouseType t : HouseType.values())
            b.append(String.format(Locale.GERMAN, " · %s %,d/%d/%d", t.label, mesh(t, 0).triangles(), mesh(t, 1).triangles(), mesh(t, 2).triangles()));
        return b.append(" Dreiecke (nah/mittel/fern)").toString();
    }
}
