package com.dan.timber;

/**
 * Seit Rungholt Phase 11: eine Dorfkirche aus Backstein (Mauerwerk), wie sie an der Nordseeküste im 13. und 14.
 * Jahrhundert stand: Westturm mit Schindelhelm, Schiff mit Reetdach, eingezogener Chor und halbrunde Apsis im Osten,
 * Sockel aus Feldstein, Portale im Süden und Norden. Örtlich wie die Häuser: +x längs (zum Chor, also nach Osten, wenn
 * die Richtung 90° ist), +y nach oben, +z quer; der Boden liegt bei y = 0, die Mauern reichen 0,6 m darunter (auf einer
 * Warft steht der Boden nicht eben).
 * <p>
 * Die Fenster malt der Shader in den Werkstoff {@link Material#CHURCH}: schmale Rundbogenfenster im Schiff und im Chor,
 * Schallöffnungen oben im Turm. Maße als {@link Spec} [G]; Vorgabe nach dem Grundriss der 2023 gefundenen Kirche von
 * Rungholt (rund 40 × 15 m, Ost-West, Apsis im Osten, Westturm).
 */
public final class Church {
    /**
     * Maße (m): Turm (Seitenlänge, Mauerhöhe, Helm), Schiff (Länge, Breite, Traufe), Chor (Länge, Breite, Traufe),
     * Halbmesser der Apsis, Dachneigung (Grad).
     */
    public record Spec(double towerSide, double towerWall, double towerHelm, double naveLength, double naveWidth, double naveEave,
                       double choirLength, double choirWidth, double choirEave, double apseRadius, double roofDeg) {
        /** Länge über alles von der Westwand des Turms bis zum Scheitel der Apsis. */
        public double length() { return towerSide + naveLength + choirLength + apseRadius; }
        /** Westende (örtlich x), damit die Mitte der Länge bei x = 0 liegt. */
        public double west() { return -length() / 2; }
        /** Höhe der Turmspitze über dem Boden. */
        public double height() { return towerWall + towerHelm; }
    }

    /** Die Kirche von Rungholt [G]: 41 m lang, Schiff 13 m breit, Turm 7,5 m im Geviert, 24 m Mauer und 9 m Helm. */
    public static Spec rungholt() { return new Spec(7.5, 24, 9, 24.5, 13, 8, 4.5, 9, 7, 4.5, 50); }

    /** Das Netz (rund 400 Dreiecke). */
    public static Mesh build(Spec s) {
        Mesh m = new Mesh();
        double x0 = s.west(), xt = x0 + s.towerSide, xn = xt + s.naveLength, xc = xn + s.choirLength;
        double ht = s.towerSide / 2, hn = s.naveWidth / 2, hc = s.choirWidth / 2, base = -0.6;
        double tan = Math.tan(Math.toRadians(s.roofDeg));
        // Sockel aus Feldstein, 0,5 m über dem Boden, etwas vorstehend
        m.box((x0 + xc) / 2, (base + 0.5) / 2, 0, (xc - x0) / 2 + 0.12, (0.5 - base) / 2, hn + 0.12, Material.STONE, false, 0.6);
        // Turm, Schiff, Chor als Mauerblöcke
        wall(m, x0, xt, ht, base, s.towerWall);
        wall(m, xt, xn, hn, base, s.naveEave);
        wall(m, xn, xc, hc, base, s.choirEave);
        // Dächer: Schiff und Chor als Sattel aus Reet, mit Überstand
        gable(m, xt, xn, hn, s.naveEave, tan, 0.45, true, false);
        gable(m, xn, xc, hc, s.choirEave, tan, 0.35, false, false);
        // Apsis: halbrunde Mauer und ein halber Kegel aus Reet
        int seg = 9;
        double r = s.apseRadius, u = 0;
        double[] prevB = null, prevT = null;
        for (int i = 0; i <= seg; i++) {
            double th = Math.PI / 2 - Math.PI * i / seg;   // von Süd (+z) über Ost nach Nord
            double[] b = {xc + r * Math.cos(th), base, r * Math.sin(th)}, t = {b[0], s.choirEave, b[2]};
            if (prevB != null) {
                m.quad(prevB, b, t, prevT, Material.CHURCH, u, base, 0.8, 0.8, 1, 1);
                u += Mesh.dist(prevB, b);
            }
            prevB = b;
            prevT = t;
        }
        double ro = r + 0.35, apex = s.choirEave + ro * tan * 0.85;
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI / 2 - Math.PI * i / seg, a1 = Math.PI / 2 - Math.PI * (i + 1) / seg;
            double[] a = {xc + ro * Math.cos(a0), s.choirEave - 0.3, ro * Math.sin(a0)}, b = {xc + ro * Math.cos(a1), s.choirEave - 0.3, ro * Math.sin(a1)};
            m.triangle(a, b, new double[]{xc, apex, 0}, Material.THATCH, 1);
        }
        // Helm des Turms: Pyramide aus Schindeln mit kleinem Überstand
        double o = 0.3, hy = s.towerWall, ay = s.towerWall + s.towerHelm, tx0 = x0 - o, tx1 = xt + o, tz = ht + o, mx = (x0 + xt) / 2;
        double[] ap = {mx, ay, 0};
        m.triangle(new double[]{tx0, hy, tz}, new double[]{tx1, hy, tz}, ap, Material.SHINGLE, 1);
        m.triangle(new double[]{tx1, hy, -tz}, new double[]{tx0, hy, -tz}, ap, Material.SHINGLE, 1);
        m.triangle(new double[]{tx1, hy, tz}, new double[]{tx1, hy, -tz}, ap, Material.SHINGLE, 1);
        m.triangle(new double[]{tx0, hy, -tz}, new double[]{tx0, hy, tz}, ap, Material.SHINGLE, 1);
        // Wetterhahn: ein Stab mit Kugel
        m.beam(ap, new double[]{mx, ay + 1.6, 0}, 0.05, 6, Material.IRON, true, 1, 1);
        m.box(mx, ay + 1.7, 0, 0.12, 0.12, 0.12, Material.IRON, false, 1);
        // Portale im Süden und Norden des Schiffs, das Westportal im Turm (Türblatt mit Bogenfeld aus Backstein)
        double px = xt + s.naveLength * 0.3;
        m.box(px, 1.75, hn + 0.06, 0.9, 1.75, 0.08, Material.DOOR, true, 0.7);
        m.box(px, 1.75, -hn - 0.06, 0.8, 1.6, 0.08, Material.DOOR, true, 0.7);
        m.box(x0 - 0.06, 1.9, 0, 0.08, 1.9, 1.0, Material.DOOR, true, 0.7);
        return m;
    }

    /** Ein Mauerblock von x0 bis x1, halbe Breite hw, von y0 bis zur Traufe y1. */
    private static void wall(Mesh m, double x0, double x1, double hw, double y0, double y1) {
        // Seiten einzeln, damit die Flächenkoordinate u an jeder Ecke neu beginnt (Fenster nie über Eck)
        double[] a = {x0, y0, hw}, b = {x1, y0, hw}, c = {x1, y0, -hw}, d = {x0, y0, -hw};
        double[] at = {x0, y1, hw}, bt = {x1, y1, hw}, ct = {x1, y1, -hw}, dt = {x0, y1, -hw};
        m.quad(a, b, bt, at, Material.CHURCH, 0, y0, 0.8, 0.8, 1, 1);    // Süd (+z)
        m.quad(c, d, dt, ct, Material.CHURCH, 0, y0, 0.8, 0.8, 1, 1);    // Nord
        m.quad(b, c, ct, bt, Material.CHURCH, 0, y0, 0.8, 0.8, 1, 1);    // Ost
        m.quad(d, a, at, dt, Material.CHURCH, 0, y0, 0.8, 0.8, 1, 1);    // West
        m.quad(at, bt, ct, dt, Material.CHURCH, 0, 0, 1);                // oben (unter dem Dach)
    }

    /**
     * Satteldach aus Reet über x0..x1 mit halber Breite hw, Traufe y, Neigung tan, Überstand o; die Giebeldreiecke aus
     * Backstein, wo sie frei stehen (westGable, eastGable).
     */
    private static void gable(Mesh m, double x0, double x1, double hw, double y, double tan, double o, boolean eastGable, boolean westGable) {
        double ridge = y + hw * tan, e = y - o * tan, w = hw + o, xa = x0 - (westGable ? o : 0), xb = x1 + o * 0.6;
        m.quadUV(new double[]{xb, ridge, 0}, new double[]{xa, ridge, 0}, new double[]{xa, e, w}, new double[]{xb, e, w}, Material.THATCH, 1, 1, 0.8, 0.8);
        m.quadUV(new double[]{xa, ridge, 0}, new double[]{xb, ridge, 0}, new double[]{xb, e, -w}, new double[]{xa, e, -w}, Material.THATCH, 1, 1, 0.8, 0.8);
        if (eastGable) m.triangle(new double[]{x1, y, hw}, new double[]{x1, y, -hw}, new double[]{x1, ridge, 0}, Material.CHURCH, 1);
        if (westGable) m.triangle(new double[]{x0, y, -hw}, new double[]{x0, y, hw}, new double[]{x0, ridge, 0}, Material.CHURCH, 1);
        // First mit Soden
        m.beam(new double[]{xa, ridge + 0.05, 0}, new double[]{xb, ridge + 0.05, 0}, 0.28, 4, Material.SOD, true, 1, 1);
    }

    private Church() { }
}
