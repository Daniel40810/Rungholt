package com.dan.rungholt.scene;

/**
 * Die flache Bühne von Phase 1 (Platzhalter bis zum Gelände aus den Höhendaten in Phase 2). Ein Höhenfeld aus wenigen
 * Formen, die Lage und Größe nach der Prüfung der Höhendaten (topography_max_z, 10 m) grob nachstellen, alle Maße [G]
 * und nur zur Ausrichtung; y = 0 ist NHN:
 * <ul>
 * <li>das Watt um NHN −0,5 m mit sanften Wellen von ±0,6 m (Sandbänke und Senken);</li>
 * <li>die Norderhever als breite Rinne von Südwest nach Nordost westlich von Südfall, 3,2 km breit, bis NHN −18 m;</li>
 * <li>ein Priel im Süden von West nach Ost, 0,9 km breit, bis NHN −8 m;</li>
 * <li>Südfall als flache Hallig auf NHN +1,8 m (1,1 × 0,6 km) mit der Warft auf NHN +5,8 m;</li>
 * <li>Pellworm im Nordwesten und Nordstrand im Osten als Köge auf NHN +0,8 m hinter Deichen von NHN +8 m.</li>
 * </ul>
 * Dieselben Formen zeichnet {@code stage.frag}; die Kamera hält sich mit {@link #height} darüber. Hinter den Deichen
 * steht kein Meer ({@link #diked}), auch wenn die Köge unter dem Hochwasser liegen.
 */
public final class Buehne {
    private Buehne() { }

    /** Die Norderhever: ein Punkt auf ihrer Mittellinie, Richtung (Einheitsvektor nach Nordost), halbe Breite, Tiefe. */
    public static final double HEVER_X = -5770, HEVER_Z = -80, HEVER_DX = 0.734, HEVER_DZ = -0.679, HEVER_HALF = 1600, HEVER_BED = -18;
    /** Der Priel im Süden: Mittellinie z, halbe Breite, Tiefe. */
    public static final double PRIEL_Z = 2320, PRIEL_HALF = 450, PRIEL_BED = -8;
    /** Südfall: Mitte, Halbachsen, Höhe; die Warft (Mitte, Halbmesser oben, Höhe). */
    public static final double HALLIG_X = -753, HALLIG_Z = -80, HALLIG_A = 555, HALLIG_B = 315, HALLIG_H = 1.8;
    public static final double WARFT_X = -1000, WARFT_Z = 0, WARFT_R = 45, WARFT_H = Site.SUEDFALL_WARFT;
    /** Pellworm und Nordstrand als Ellipsen (Mitte x, z, Halbachsen a, b); Koog und Deichkrone über NHN. */
    public static final double[] PELLWORM = {-6170, -5580, 4800, 2900}, NORDSTRAND = {9030, -2180, 3800, 3300};
    public static final double KOOG = 0.8, DEICH = 8, DEICH_RAND = 0.03;

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }

    /** Das Watt ohne Rinnen: NHN −0,5 m mit flachen Wellen. */
    public static double watt(double x, double z) {
        return -0.5 + 0.35 * Math.sin(x / 1300 + 0.7) * Math.cos(z / 1700 - 0.3) + 0.2 * Math.sin((x + z) / 650) + 0.08 * Math.sin(x / 210 - z / 330);
    }

    /** Abstand von der Mittellinie der Hever (m). */
    public static double heverDist(double x, double z) {
        double px = x - HEVER_X, pz = z - HEVER_Z;
        return Math.abs(px * HEVER_DZ - pz * HEVER_DX);
    }

    /** Wert der Ellipse ((x−cx)/a)² + ((z−cz)/b)²: unter 1 innen. */
    public static double ellipse(double[] e, double x, double z) {
        double u = (x - e[0]) / e[2], v = (z - e[1]) / e[3];
        return u * u + v * v;
    }

    /** Liegt (x, z) hinter einem Deich (Pellworm, Nordstrand)? Dort steht kein Meer. */
    public static boolean diked(double x, double z) { return ellipse(PELLWORM, x, z) < 1 || ellipse(NORDSTRAND, x, z) < 1; }

    /** Höhe der Bühne über NHN (m). */
    public static double height(double x, double z) {
        double h = watt(x, z);
        h = Math.min(h, h + (HEVER_BED - h) * (1 - smooth(0.55, 1.0, heverDist(x, z) / HEVER_HALF)));
        h = Math.min(h, h + (PRIEL_BED - h) * (1 - smooth(0.3, 1.0, Math.abs(z - PRIEL_Z) / PRIEL_HALF)));
        double hu = (x - HALLIG_X) / HALLIG_A, hv = (z - HALLIG_Z) / HALLIG_B, he = hu * hu + hv * hv;
        h = Math.max(h, h + (HALLIG_H - h) * (1 - smooth(0.85, 1.15, he)));
        double wr = Math.hypot(x - WARFT_X, z - WARFT_Z);
        if (wr < WARFT_R * 2.2) h = Math.max(h, HALLIG_H + (WARFT_H - HALLIG_H) * (1 - smooth(WARFT_R, WARFT_R * 2.2, wr)));
        for (double[] e : new double[][]{PELLWORM, NORDSTRAND}) {
            double q = ellipse(e, x, z);
            if (q < 1 - DEICH_RAND) h = KOOG;
            else if (q < 1 + DEICH_RAND) h = Math.max(q < 1 ? KOOG : h, DEICH * (1 - smooth(0, DEICH_RAND, Math.abs(q - 1))));
        }
        return h;
    }

    /** „Norderhever“, „Priel“, „Südfall“, „Warft von Südfall“, „Pellworm“, „Nordstrand“, „Rungholt-Watt“. */
    public static String zone(double x, double z) {
        if (ellipse(PELLWORM, x, z) < 1 + DEICH_RAND) return "Pellworm";
        if (ellipse(NORDSTRAND, x, z) < 1 + DEICH_RAND) return "Nordstrand";
        if (Math.hypot(x - WARFT_X, z - WARFT_Z) < WARFT_R * 1.6) return "Warft von Südfall";
        double hu = (x - HALLIG_X) / HALLIG_A, hv = (z - HALLIG_Z) / HALLIG_B;
        if (hu * hu + hv * hv < 1) return "Südfall";
        if (heverDist(x, z) < HEVER_HALF * 0.8) return "Norderhever";
        if (Math.abs(z - PRIEL_Z) < PRIEL_HALF * 0.7) return "Priel";
        return "Rungholt-Watt";
    }
}
