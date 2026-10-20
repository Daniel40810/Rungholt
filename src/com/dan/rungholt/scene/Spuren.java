package com.dan.rungholt.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 13: die Spuren von Rungholt im Watt von heute (F17 A) [G: wie sie aussehen, ist gestaltet; belegt sind Lage und
 * Art: Warften, Kirche, Gräben, Deich mit Sielhafen, Brunnen, erodiert und oft nur als Abdruck im Sediment].
 * <p>
 * Alles kommt aus demselben Land von 1362 ({@link Land1362}), darum liegen die Spuren genau unter den Warften. Eine Karte
 * von 2048² Zellen zu 3,5 m (7,2 km um die Kirche) hält vier Kanäle, die Java und der Shader gleich lesen:
 * <ul>
 *   <li><b>Klei</b> (R): der Rest des Warftkörpers, ein flacher Rücken aus festem Klei (höchstens 0,25 m über dem Watt,
 *       {@link #HEIGHT}), ausgefranst vom Rauschen; dazu ein Band am Fuß des alten Seedeichs.</li>
 *   <li><b>Kulturschicht</b> (G): dunkle Flecken mit Kleibrocken, Ziegel- und Torfresten in der Mitte der Warften, an der
 *       Kirche und am Siel.</li>
 *   <li><b>Magnetik</b> (B, 128 = 0 nT, ±{@link #MAG_RANGE} nT): ein Magnetogramm nach dem Muster der veröffentlichten
 *       Bilder von 2023 [G, nicht die Daten]: Warften leicht positiv, Gräben negativ, Brunnen, Kirche und Siel als starke
 *       Dipole, je Quelle das Plus südlich und das Minus nördlich (Inklination 69°).</li>
 *   <li><b>Messfläche</b> (A): wo gemessen wurde (Streifen der Messfahrten, rund 10 km²).</li>
 * </ul>
 * Brunnen und Fundstellen sind Listen ({@link #wells()}, {@link #FINDS}); Gräben, Sielzüge und der Grundriss der Kirche
 * zeichnet der Shader aus dem Land von 1362 und den Maßen der Kirche.
 */
public final class Spuren {
    /** Raster der Spurenkarte: Zellen je Seite, Zellgröße (m), Nordwestecke. */
    public static final int N = 2048;
    public static final double CELL = 3.5, X0 = -N * CELL / 2, Z0 = -N * CELL / 2;
    /** Höhe des Kleirückens über dem Watt bei voller Spur (m) [G]. */
    public static final double HEIGHT = 0.25;
    /** Spanne der Magnetik (nT) für die Grautöne. */
    public static final double MAG_RANGE = 6;

    /** Eine Fundstelle: Name, Lage, Jahr, Art (0 Busch ab 1921, 1 Funde im Museum, 2 Messung und Bohrung 2023), Quelle. */
    public record Fund(String name, double x, double z, int year, int kind, String source) { }

    /** Die Fundstellen [G: die Punkte gesetzt; belegt sind Art und Quelle, nicht die Lage, außer der Kirche „nahe Südfall“]. */
    public static final List<Fund> FINDS;

    /** Brunnen (x, z) auf den Warften: Sodenbrunnen, wie Busch sie im Watt fand [G]. */
    private static List<double[]> wells;

    public final byte[] rgba = new byte[N * N * 4];
    public double buildMs;
    /** Anteil der Messfläche (km²) und Zahl der Zellen mit Spur, für HUD und Prüfung. */
    public double surveyKm2, kleiKm2;

    private Spuren() { }

    static {
        List<Fund> f = new ArrayList<>();
        f.add(new Fund("Kirche: Fundamente, 40 × 15 m", 0, 0, 2023, 2, "Uni Kiel, JGU Mainz, ZBSA, ALSH 2023"));
        f.add(new Fund("Siel und Sielhafen: Holz des Sieltors", Land1362.SIEL[0], Land1362.SIEL[1] - 6, 1921, 0, "Andreas Busch, Nordstrand"));
        f.add(new Fund("Seedeich: Kleikern im Watt", 900, 610, 2023, 2, "Magnetik und Bohrung 2023"));
        f.add(new Fund("Kleine Kirche (Warft im Westen)", -1080, -240, 2023, 2, "Magnetik 2023: zwei kleine Kirchen"));
        f.add(new Fund("Kleine Kirche (Warft im Osten)", 1010, 238, 2023, 2, "Magnetik 2023: zwei kleine Kirchen"));
        f.add(new Fund("Bohrkern: Warftkörper aus Klei", -320, -66, 2023, 2, "Bohrungen 2023"));
        f.add(new Fund("Bohrkern: Altmarsch unter dem Watt", 420, 300, 2023, 2, "Bohrungen 2023"));
        f.add(new Fund("Scherben, Krüge (Grauware)", -700, -175, 1921, 1, "Rungholt-Museum Pellworm (Hellmut Bahnsen)"));
        f.add(new Fund("Grüne Ofenkacheln", 352, 66, 1938, 1, "Rungholt-Museum Pellworm"));
        f.add(new Fund("Pferdeschädel", 760, 150, 1921, 1, "Museum Nordstrand"));
        f.add(new Fund("Sodenbrunnen", -305, -54, 1921, 0, "Andreas Busch"));
        f.add(new Fund("Grabensystem der Fennen", -1650, 260, 1921, 0, "Andreas Busch"));
        f.add(new Fund("Pflugspuren", 1700, -350, 1921, 0, "Andreas Busch"));
        f.add(new Fund("Warftfuß mit Sodenkante", 1050, 252, 1921, 0, "Andreas Busch"));
        FINDS = List.copyOf(f);
    }

    // ------------------------------------------------------------------ Zugriff (Java gleich Shader)

    /** Kanal 0..3 an der Zelle (i, j) als 0..1. */
    public double channel(int i, int j, int c) {
        if (i < 0 || j < 0 || i >= N || j >= N) return c == 2 ? 0.5 : 0;
        return (rgba[(j * N + i) * 4 + c] & 0xff) / 255.0;
    }

    /** Kanal bilinear an (x, z), wie {@code texture()} im Shader (Zellmitten bei (i + 0,5) · CELL). */
    public double sample(double x, double z, int c) {
        double u = (x - X0) / CELL - 0.5, v = (z - Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double a = channel(clampI(i), clampI(j), c), b = channel(clampI(i + 1), clampI(j), c);
        double cc = channel(clampI(i), clampI(j + 1), c), d = channel(clampI(i + 1), clampI(j + 1), c);
        if (u < -0.5 || v < -0.5 || u > N - 0.5 || v > N - 0.5) return c == 2 ? 0.5 : 0;
        return (a + (b - a) * fx) * (1 - fz) + (cc + (d - cc) * fx) * fz;
    }

    private static int clampI(int k) { return Math.max(0, Math.min(N - 1, k)); }

    /** Höhe des Kleirückens über dem Watt (m) an (x, z). */
    public double height(double x, double z) { return HEIGHT * sample(x, z, 0); }

    /** Magnetik (nT) an (x, z); NaN außerhalb der Messfläche. */
    public double magnetik(double x, double z) {
        if (sample(x, z, 3) < 0.5) return Double.NaN;
        return (sample(x, z, 2) - 128 / 255.0) * 255 / 127.0 * MAG_RANGE;
    }

    // ------------------------------------------------------------------ Bau

    /** Die Brunnen: auf jeder Warft der Kette einer, auf jedem zweiten Hof einer, auf der Kirchwarft zwei [G]. */
    public static synchronized List<double[]> wells() {
        if (wells != null) return wells;
        List<double[]> l = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(13620);
        for (Land1362.Warft w : Land1362.warften()) {
            int n = w.name().startsWith("Kirchwarft") ? 2 : w.name().startsWith("Warft ") ? 1 : (r.nextBoolean() ? 1 : 0);
            double h = Math.toRadians(w.headingDeg()), ax = Math.sin(h), az = -Math.cos(h);
            for (int k = 0; k < n; k++) {
                double u = (r.nextDouble() - 0.5) * w.a() * 0.9, v = (r.nextDouble() * 0.5 + 0.25) * w.b() * (r.nextBoolean() ? 1 : -1);
                if (w.name().startsWith("Kirchwarft")) { u = k == 0 ? -34 : 30; v = k == 0 ? 18 : -20; }
                l.add(new double[]{w.x() + ax * u - az * v, w.z() + az * u + ax * v});
            }
        }
        wells = List.copyOf(l);
        return wells;
    }

    /**
     * Die Messfläche von 2023 [G]: Streifen von 24 m Breite (eine Messfahrt mit dem Gespann) quer zur Kette, je nach
     * Lage verschieden lang, über der Kette, um die Kirche, zum Deich und über einzelne Hofwarften; rund 10 km².
     */
    static boolean surveyed(double x, double z) {
        // Abstand längs und quer zur Kette (die Kette läuft grob von West nach Ost, leicht nach Norden)
        double along = x * 0.97 + z * 0.25, across = -x * 0.25 + z * 0.97;
        if (along < -2500 || along > 2300) return false;
        int strip = (int) Math.floor((along + 2600) / 24);
        // Länge und Mitte der Fahrten ändern sich von Block zu Block (je 10 Fahrten), dazwischen nur wenig
        double blk = Math.floor(strip / 10.0), fb = strip / 10.0 - blk;
        double l0 = 1250 + 700 * hash1(blk * 0.37), l1 = 1250 + 700 * hash1((blk + 1) * 0.37);
        double len = l0 + (l1 - l0) * Land1362.smooth(0.7, 1.0, fb) + 40 * (hash1(strip * 0.91) - 0.5);
        double off = 60 + 120 * (hash1(blk * 1.91) - 0.5) + 20 * (hash1(strip * 2.3) - 0.5);   // Mitte etwas zum Deich hin
        if (Math.abs(across - off) < len / 2) {
            // Lücken, wo das Gespann nicht fahren konnte (Priel, Schlick)
            double gap = hash1(Math.floor(strip / 2.0) * 3.3 + Math.floor((across + 2000) / 160) * 7.7);
            return gap > 0.02;
        }
        // einzelne Blöcke über Hofwarften
        for (Land1362.Warft w : Land1362.warften())
            if (w.name().startsWith("Hofwarft") && hash1(w.x() * 0.01 + w.z() * 0.013) < 0.45 && Math.abs(x - w.x()) < 110 && Math.abs(z - w.z()) < 90) return true;
        return false;
    }

    static double hash1(double s) { double v = Math.sin(s * 127.1 + 311.7) * 43758.5453; return v - Math.floor(v); }

    /** Baut die Karte (gut eine Sekunde). */
    public static Spuren build() {
        long t0 = System.nanoTime();
        Spuren s = new Spuren();
        float[] klei = new float[N * N], kult = new float[N * N], susc = new float[N * N];
        List<Land1362.Warft> ws = Land1362.warften();
        // Warften: der Körper aus Klei, oben flach abgetragen, am Rand ausgefranst
        for (Land1362.Warft w : ws) {
            double rad = w.radius() + 4;
            int i0 = (int) Math.floor((w.x() - rad - X0) / CELL), i1 = (int) Math.ceil((w.x() + rad - X0) / CELL);
            int j0 = (int) Math.floor((w.z() - rad - Z0) / CELL), j1 = (int) Math.ceil((w.z() + rad - Z0) / CELL);
            boolean kirche = w.name().startsWith("Kirchwarft");
            for (int j = Math.max(0, j0); j <= Math.min(N - 1, j1); j++)
                for (int i = Math.max(0, i0); i <= Math.min(N - 1, i1); i++) {
                    double x = X0 + (i + 0.5) * CELL, z = Z0 + (j + 0.5) * CELL;
                    double m = Land1362.marsh(x, z);
                    double wh = w.height(x, z, m);
                    if (Double.isNaN(wh)) continue;
                    double t = (wh - m) / Math.max(0.3, w.top() - m);
                    double e = Land1362.fbm(x / 23 + w.x() * 0.001, z / 23);
                    double k = Land1362.smooth(0.08, 0.55, t + 0.35 * (e - 0.5)) * (0.6 + 0.4 * Land1362.smooth(0.3, 0.6, Land1362.fbm(x / 7 + 1.7, z / 7)));
                    int idx = j * N + i;
                    klei[idx] = (float) Math.max(klei[idx], k);
                    // Kulturschicht: in der Mitte, fleckig
                    double c = Land1362.smooth(0.55, 0.95, t) * Land1362.smooth(0.35, 0.6, Land1362.fbm(x / 9, z / 9 + 3.3));
                    if (kirche) c = Math.max(c, 0.6 * Land1362.smooth(0.7, 1.0, t));
                    kult[idx] = (float) Math.max(kult[idx], c);
                    susc[idx] += (float) (0.8 * k + 1.6 * c);
                }
        }
        // der alte Seedeich: ein Band aus Klei, wo sein Kern im Watt geblieben ist; der Siel als Fleck
        java.util.stream.IntStream.range(0, N).parallel().forEach(j -> {
            for (int i = 0; i < N; i++) {
                double x = X0 + (i + 0.5) * CELL, z = Z0 + (j + 0.5) * CELL;
                double side = Land1362.dikeSide(x, z);
                int idx = j * N + i;
                if (Math.abs(side) < 14) {
                    double keep = Land1362.smooth(0.42, 0.55, Land1362.fbm(x / 60, z / 60 + 9.1));
                    double k = (1 - Land1362.smooth(4, 13, Math.abs(side))) * keep * 0.8;
                    klei[idx] = (float) Math.max(klei[idx], k);
                    susc[idx] += (float) (0.6 * k);
                }
                double sd = Math.hypot(x - Land1362.SIEL[0], z - Land1362.SIEL[1]);
                if (sd < 22) { kult[idx] = (float) Math.max(kult[idx], 1 - sd / 22); susc[idx] += (float) (4 * (1 - sd / 22)); }
                // Gräben (gefüllt mit Sand: weniger magnetisch) und Sielzüge, wie terrain.frag sie malt
                double cd = Land1362.canalDist(x, z);
                if (cd < 9 && side > 0) susc[idx] -= (float) (1.2 * (1 - cd / 9));
                susc[idx] -= (float) (0.5 * (fennenGraben(x, z) - 0.07) * (side > 30 ? 1 : 0));   // ohne Versatz der ganzen Marsch
            }
        });
        // Kirche: Backstein und Feldstein (stark), Brunnen (Soden, gebrannt: starke Dipole)
        com.dan.timber.Church.Spec cs = com.dan.timber.Church.rungholt();
        for (int j = 0; j < N; j++) {
            double z = Z0 + (j + 0.5) * CELL;
            if (Math.abs(z) > 40) continue;
            for (int i = 0; i < N; i++) {
                double x = X0 + (i + 0.5) * CELL;
                if (Math.abs(x) > 40) continue;
                double d = churchSdf(x, z, cs);
                int idx = j * N + i;
                double wall = 1 - Land1362.smooth(0.6, 2.4, Math.abs(d));
                susc[idx] += (float) (9 * wall + (d < 0 ? 1.5 : 0));
                kult[idx] = (float) Math.max(kult[idx], 0.8 * wall + (d < 0 ? 0.5 : 0));
            }
        }
        for (double[] w : wells()) {
            int ci = (int) ((w[0] - X0) / CELL), cj = (int) ((w[1] - Z0) / CELL);
            for (int j = cj - 1; j <= cj + 1; j++) for (int i = ci - 1; i <= ci + 1; i++)
                if (i >= 0 && j >= 0 && i < N && j < N) susc[j * N + i] += (i == ci && j == cj) ? 14 : 3;
        }
        // Magnetogramm: die Quellen mit einem geneigten Dipol falten (Inklination 69°, Feld nach Norden): Plus südlich
        // der Quelle, Minus nördlich; Rauschen der Sonde und der Streifen
        int R = 6;
        double[] ker = new double[(2 * R + 1) * (2 * R + 1)];
        double inc = Math.toRadians(69), depth = 1.6;   // Quelle 1,6 m unter der Sonde [G]
        double sum = 0;
        for (int b = -R; b <= R; b++)
            for (int a = -R; a <= R; a++) {
                // Feld eines Dipols in Richtung des Erdfelds, gemessen in Feldrichtung (z nach Süden positiv in der Szene)
                double dx = a * CELL, dz = b * CELL, dy = depth;
                double rr = Math.sqrt(dx * dx + dz * dz + dy * dy);
                // Einheitsvektor des Erdfelds: nach Norden (−z) und nach unten (−y): (0, −sin i, −cos i)
                double fx = 0, fy = -Math.sin(inc), fz = -Math.cos(inc);
                // Ort der Sonde relativ zur Quelle: (dx, dy, dz); Dipolmoment parallel zum Feld
                double rx = dx / rr, ry = dy / rr, rz = dz / rr;
                double mr = fx * rx + fy * ry + fz * rz;
                double bx = 3 * mr * rx - fx, by = 3 * mr * ry - fy, bz = 3 * mr * rz - fz;
                double val = (bx * fx + by * fy + bz * fz) / (rr * rr * rr);
                ker[(b + R) * (2 * R + 1) + a + R] = val;
                sum += Math.abs(val);
            }
        for (int k = 0; k < ker.length; k++) ker[k] /= sum / 6.0;
        byte[] out = s.rgba;
        final double[] kerF = ker;
        java.util.stream.IntStream.range(0, N).parallel().forEach(j -> {
            double z = Z0 + (j + 0.5) * CELL;
            for (int i = 0; i < N; i++) {
                double x = X0 + (i + 0.5) * CELL;
                int idx = j * N + i;
                boolean sv = surveyed(x, z);
                double mag = 0;
                if (sv) {
                    for (int b = -R; b <= R; b++) {
                        int jj = j - b;   // Quelle bei −(a, b): der Kern gilt für die Sonde relativ zur Quelle
                        if (jj < 0 || jj >= N) continue;
                        int row = jj * N, kr = (b + R) * (2 * R + 1) + R;
                        for (int a = -R; a <= R; a++) {
                            int ii = i - a;
                            if (ii < 0 || ii >= N) continue;
                            float q = susc[row + ii];
                            if (q != 0) mag += q * kerF[kr + a];
                        }
                    }
                    // Sondenrauschen, Streifen der Messfahrten (Versatz je Fahrt), Geologie (Prielfüllungen)
                    double along = x * 0.97 + z * 0.25;
                    int strip = (int) Math.floor((along + 2600) / 24);
                    mag += 0.35 * (hashGl(i * 0.731, j * 1.377) - 0.5) + 0.5 * (hash1(strip * 5.1) - 0.5) + 1.2 * (Land1362.fbm(x / 140, z / 140 + 4.4) - 0.5);
                }
                out[idx * 4] = (byte) Math.round(255 * Math.max(0, Math.min(1, klei[idx])));
                out[idx * 4 + 1] = (byte) Math.round(255 * Math.max(0, Math.min(1, kult[idx])));
                out[idx * 4 + 2] = (byte) Math.round(Math.max(0, Math.min(255, 128 + mag / MAG_RANGE * 127)));
                out[idx * 4 + 3] = (byte) (sv ? 255 : 0);
            }
        });
        int cover = 0, kc = 0;
        for (int idx = 0; idx < N * N; idx++) { if (out[idx * 4 + 3] != 0) cover++; if (klei[idx] > 0.3) kc++; }
        s.surveyKm2 = cover * CELL * CELL / 1e6;
        s.kleiKm2 = kc * CELL * CELL / 1e6;
        s.buildMs = (System.nanoTime() - t0) / 1e6;
        return s;
    }

    /**
     * Gräben der Fennen wie terrain.frag (Schläge von 400 m mit eigener Richtung, Gräben alle 40 m): 1 in der Mitte
     * eines Grabens, 0 daneben (Breite gut 1 m). Das Rauschen ist nicht dasselbe wie im Shader; für die Magnetik reicht
     * die Lage.
     */
    static double fennenGraben(double x, double z) {
        double cx = Math.floor(x / 400), cz = Math.floor(z / 400);
        double ang = hashGl(cx + 3.7, cz + 3.7) * Math.PI;
        double qx = x - (cx + 0.5) * 400, qz = z - (cz + 0.5) * 400;
        double qy = -Math.sin(ang) * qx + Math.cos(ang) * qz;   // mat2(c, −s, s, c) · q in GLSL (Spalten): y = −s·qx + c·qz
        double dg = Math.abs(((qy / 40) - Math.floor(qy / 40)) - 0.5) * 40;
        return 1 - Land1362.smooth(18.2, 19.6, dg);
    }

    /** Der Hash aus terrain.frag (fract(sin(dot(p, (127,1, 311,7))) · 43758,5453)). */
    static double hashGl(double x, double z) { double v = Math.sin(x * 127.1 + z * 311.7) * 43758.5453; return v - Math.floor(v); }

    /**
     * Abstand zum Grundriss der Kirche (m, innen negativ): Turm, Schiff, Chor und Apsis, Achse nach
     * {@link Stadt1362#CHURCH_HEADING}; so zeichnet ihn auch terrain.frag.
     */
    public static double churchSdf(double x, double z, com.dan.timber.Church.Spec s) {
        double h = Math.toRadians(Stadt1362.CHURCH_HEADING), ax = Math.sin(h), az = -Math.cos(h);
        double dx = x - Stadt1362.CHURCH_X, dz = z - Stadt1362.CHURCH_Z;
        double u = dx * ax + dz * az, v = -dx * az + dz * ax;
        double x0 = s.west(), xt = x0 + s.towerSide(), xn = xt + s.naveLength(), xc = xn + s.choirLength();
        double d = box(u, v, (x0 + xt) / 2, (xt - x0) / 2, s.towerSide() / 2);
        d = Math.min(d, box(u, v, (xt + xn) / 2, (xn - xt) / 2, s.naveWidth() / 2));
        d = Math.min(d, box(u, v, (xn + xc) / 2, (xc - xn) / 2, s.choirWidth() / 2));
        if (u >= xc) d = Math.min(d, Math.hypot(u - xc, v) - s.apseRadius());
        return d;
    }

    private static double box(double u, double v, double cu, double hu, double hv) {
        double qx = Math.abs(u - cu) - hu, qy = Math.abs(v) - hv;
        return Math.hypot(Math.max(qx, 0), Math.max(qy, 0)) + Math.min(Math.max(qx, qy), 0);
    }

    /** Die Warft, auf deren Spur oder Hügel (x, z) liegt (die nächste mit Hügel dort), oder null. */
    public static Land1362.Warft warftAt(double x, double z) {
        Land1362.Warft best = null;
        double bd = Double.MAX_VALUE;
        for (Land1362.Warft w : Land1362.warften()) {
            if (Math.abs(x - w.x()) > w.radius() + 6 || Math.abs(z - w.z()) > w.radius() + 6) continue;
            double m = Land1362.marsh(x, z);
            if (Double.isNaN(w.height(x, z, m))) continue;
            double d = Math.hypot(x - w.x(), z - w.z());
            if (d < bd) { bd = d; best = w; }
        }
        return best;
    }

    /** Fundstellen in r Metern um (x, z). */
    public static List<Fund> findsNear(double x, double z, double r) {
        List<Fund> l = new ArrayList<>();
        for (Fund f : FINDS) if (Math.hypot(f.x() - x, f.z() - z) <= r) l.add(f);
        return l;
    }

    public String describe() {
        return String.format(Locale.GERMAN, "Spuren: %d Warften als Abdruck (%.2f km² Klei) · %d Brunnen · %d Fundstellen · Magnetik auf %.1f km² · gebaut in %.1f s",
                Land1362.warften().size(), kleiKm2, wells().size(), FINDS.size(), surveyKm2, buildMs / 1000);
    }
}
