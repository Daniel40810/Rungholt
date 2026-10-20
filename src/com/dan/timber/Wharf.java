package com.dan.timber;

import java.util.SplittableRandom;

/**
 * Hafenbau aus Holz (seit Haithabu Phase 8): breite Plattformen auf Pfahlreihen, eine Pfahlreihe im Wasser bis zu
 * einer festen Höhe (Hafenpalisade), eine Uferbefestigung aus Bohlen und Waren auf den Brücken (Fässer, Ballen,
 * Mühlsteine, Specksteintöpfe). Wie die übrigen {@link Structures} feste Netze in Szenenmaßen mit grober Form; die
 * schmale Landebrücke ist {@link Structures#pier}. Maße nach den Befunden im Hafen von Haithabu, Einzelheiten [G].
 */
public final class Wharf {
    private Wharf() { }

    // ==================================================================== Plattform

    /**
     * Plattform: ein Bohlendeck width breit in der Höhe deckY entlang der Linie, getragen von Pfahlreihen alle 2,5 m
     * längs und höchstens 2,6 m quer, die bis zum Grund reichen (auch unter Wasser); Querbalken auf jeder Reihe,
     * Längshölzer auf jeder Pfahlspur, die Bohlen quer zur Linie.
     */
    public static Mesh platform(double[][] line, double width, double deckY, Structures.Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double hw = width / 2;
        int cols = Math.max(2, (int) Math.ceil(width / 2.6) + 1);
        Structures.walk(line, 2.5, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx;
            for (int c = 0; c < cols; c++) {
                double q = -hw + 0.18 + (width - 0.36) * c / (cols - 1);
                double px = x + nx * q + dx * (r.nextDouble() - 0.5) * 0.25, pz = z + nz * q + dz * (r.nextDouble() - 0.5) * 0.25;
                m.beam(new double[]{px, Math.min(deckY - 1, g.height(px, pz)) - 0.6, pz}, new double[]{px, deckY - 0.12, pz}, 0.12 + r.nextDouble() * 0.04, 6, Material.POST, false, 0.35, 0.8);
            }
            m.beam(new double[]{x - nx * hw, deckY - 0.2, z - nz * hw}, new double[]{x + nx * hw, deckY - 0.2, z + nz * hw}, 0.11, 4, Material.POST, true, 0.5, 0.5);
        });
        Structures.segments(line, 5.0, (x0, z0, x1, z1) -> {
            double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
            if (l < 1e-6) return;
            double nx = -dz / l, nz = dx / l;
            for (int c = 0; c < cols; c++) {
                double q = -hw + 0.18 + (width - 0.36) * c / (cols - 1);
                m.beam(new double[]{x0 + nx * q, deckY - 0.1, z0 + nz * q}, new double[]{x1 + nx * q, deckY - 0.1, z1 + nz * q}, 0.08, 4, Material.POST, false, 0.45, 0.45);
            }
        });
        Structures.walk(line, 0.27, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx, shift = (r.nextDouble() - 0.5) * 0.1;
            Structures.plank(m, x + nx * shift, deckY, z + nz * shift, dx, dz, hw * (0.98 + r.nextDouble() * 0.04), 0.12, 0.035, Material.DECK, (r.nextDouble() - 0.5) * 0.015);
        });
        return m;
    }

    /** Plattform grob: Deck als Band, die äußeren Pfähle alle 5 m kantig. */
    public static Mesh coarsePlatform(double[][] line, double width, double deckY, Structures.Ground g) {
        Mesh m = Structures.band(line, width, (x, z) -> deckY, 0, Material.DECK);
        double hw = width / 2;
        Structures.walk(line, 5.0, (x, z, dx, dz, first) -> {
            for (int s = -1; s <= 1; s += 2) {
                double px = x - dz * s * (hw - 0.2), pz = z + dx * s * (hw - 0.2);
                m.beam(new double[]{px, Math.min(deckY - 1, g.height(px, pz)), pz}, new double[]{px, deckY, pz}, 0.13, 4, Material.POST, false, 0.4, 0.8);
            }
        });
        return m;
    }

    // ==================================================================== Pfahlreihe im Wasser

    /**
     * Hafenpalisade: dicht gesetzte Rundhölzer alle 0,3 m vom Grund (0,6 m eingerammt) bis zur festen Höhe topY
     * (± 10 cm), oben stumpf; für eine Sperre im Wasser, deren Pfähle verschieden tief stehen.
     */
    public static Mesh palisadeTo(double[][] line, double topY, Structures.Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        Structures.walk(line, 0.3, (x, z, dx, dz, first) -> {
            double y = g.height(x, z), top = topY + (r.nextDouble() - 0.5) * 0.2, rad = 0.12 + r.nextDouble() * 0.04;
            if (top - y < 0.3) return;
            m.beam(new double[]{x, y - 0.6, z}, new double[]{x, top, z}, rad, 6, Material.POST, true, 0.3, 1);
        });
        // ein Gurt aus Balken in Höhe des Mittelwassers hält die Reihe zusammen
        Structures.segments(line, 4.0, (x0, z0, x1, z1) -> {
            double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
            if (l < 1e-6) return;
            double nx = -dz / l * 0.22, nz = dx / l * 0.22;
            for (int s = -1; s <= 1; s += 2)
                m.beam(new double[]{x0 + nx * s, topY - 0.55, z0 + nz * s}, new double[]{x1 + nx * s, topY - 0.55, z1 + nz * s}, 0.09, 4, Material.POST, false, 0.6, 0.6);
        });
        return m;
    }

    /** Hafenpalisade grob: eine Wand vom Grund bis topY, beidseitig. */
    public static Mesh coarsePalisadeTo(double[][] line, double topY, Structures.Ground g) {
        Mesh m = new Mesh();
        Structures.segments(line, 6.0, (x0, z0, x1, z1) -> {
            double[] a = {x0, g.height(x0, z0) - 0.3, z0}, b = {x1, g.height(x1, z1) - 0.3, z1}, c = {x1, topY, z1}, d = {x0, topY, z0};
            m.quadUV(a, b, c, d, Material.POST, 0.4, 0.4, 1, 1);
            m.quadUV(b, a, d, c, Material.POST, 0.4, 0.4, 1, 1);
        });
        return m;
    }

    // ==================================================================== Uferbefestigung

    /**
     * Uferbefestigung: Pfosten alle 1,1 m, dahinter waagerechte Bohlen von 0,3 m unter dem Boden bis height darüber
     * (Bohlenwand an der Wasserlinie, die das Ufer hält). Die Linie läuft mit dem Land zur Linken.
     */
    public static Mesh revetment(double[][] line, double height, Structures.Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        Structures.walk(line, 1.1, (x, z, dx, dz, first) -> {
            double nx = dz * 0.12, nz = -dx * 0.12;         // nach rechts (zum Wasser)
            double y = g.height(x, z);
            m.beam(new double[]{x + nx, y - 0.9, z + nz}, new double[]{x + nx, y + height + 0.15 + r.nextDouble() * 0.1, z + nz}, 0.09, 5, Material.POST, false, 0.4, 1);
        });
        Structures.segments(line, 1.1, (x0, z0, x1, z1) -> {
            double y0 = g.height(x0, z0), y1 = g.height(x1, z1);
            double[] a = {x0, y0 - 0.3, z0}, b = {x1, y1 - 0.3, z1}, c = {x1, y1 + height, z1}, d = {x0, y0 + height, z0};
            m.quad(b, a, d, c, Material.PLANK, 0, 0, 0.5, 0.5, 0.95, 0.95);
            m.quad(a, b, c, d, Material.PLANK, 0, 0, 0.4);
        });
        return m;
    }

    // ==================================================================== Waren

    /** Fass: zwölf Dauben mit Bauch, zwei Reifen, Deckel; stehend auf (x, y, z), Halbmesser r, Höhe h. */
    public static void barrel(Mesh m, double x, double y, double z, double r, double h) {
        int n = 12, rings = 6;
        double[][][] p = new double[rings + 1][n + 1][];
        for (int j = 0; j <= rings; j++) {
            double t = j / (double) rings, rr = r * (0.86 + 0.14 * Math.sin(Math.PI * t));
            for (int i = 0; i <= n; i++) {
                double a = 2 * Math.PI * i / n;
                p[j][i] = new double[]{x + Math.cos(a) * rr, y + h * t, z + Math.sin(a) * rr};
            }
        }
        for (int j = 0; j < rings; j++)
            for (int i = 0; i < n; i++) {
                double[] a = p[j][i], b = p[j][i + 1], c = p[j + 1][i + 1], d = p[j + 1][i];
                Material mat = (j == 1 || j == rings - 2) ? Material.DOOR : Material.PLANK;
                if (Mesh.dot(Mesh.normal(a, d, b), new double[]{a[0] - x, 0, a[2] - z}) >= 0) m.quadUV(a, d, c, b, mat, 0.6, 1, 1, 0.6);
                else m.quadUV(a, b, c, d, mat, 0.6, 0.6, 1, 1);
            }
        double[] top = {x, y + h, z};
        for (int i = 0; i < n; i++) {
            double[] a = p[rings][i], b = p[rings][i + 1];
            double[] nn = Mesh.normal(a, top, b);
            if (nn[1] >= 0) m.triangle(a, top, b, Material.DECK, 1); else m.triangle(a, b, top, Material.DECK, 1);
        }
    }

    /** Ballen: ein weicher Kasten aus Tuch (Länge l in Richtung headingDeg, Breite w, Höhe h). */
    public static void bale(Mesh m, double x, double y, double z, double headingDeg, double l, double w, double h) {
        double a = Math.toRadians(headingDeg);
        double dx = Math.sin(a), dz = -Math.cos(a);
        Structures.plank(m, x, y + h / 2, z, -dz, dx, l / 2, w / 2, h / 2, Material.CLOTH, 0);
    }

    /** Mühlstein aus Basalt: Scheibe mit Loch (Halbmesser r, Dicke t), flach liegend oder auf der Kante gegen Kippe. */
    public static void millstone(Mesh m, double x, double y, double z, double r, double t, double tiltDeg, double headingDeg) {
        int n = 18;
        double tilt = Math.toRadians(tiltDeg), hd = Math.toRadians(headingDeg);
        double lift = r * Math.sin(tilt) + 0.01;            // gekippt steht er auf seiner Kante
        // örtlich: Scheibe in der xz-Ebene, dann um die Achse quer zur Richtung gekippt
        java.util.function.Function<double[], double[]> tf = q -> {
            double qx = q[0], qy = q[1], qz = q[2];
            double y1 = qy * Math.cos(tilt) - qz * Math.sin(tilt), z1 = qy * Math.sin(tilt) + qz * Math.cos(tilt);
            double x2 = qx * Math.cos(hd) - z1 * Math.sin(hd), z2 = qx * Math.sin(hd) + z1 * Math.cos(hd);
            return new double[]{x + x2, y + y1 + lift, z + z2};
        };
        double hole = r * 0.16;
        double[] c = tf.apply(new double[]{0, 0, 0});
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            double[] o0b = tf.apply(new double[]{Math.cos(a0) * r, 0, Math.sin(a0) * r}), o1b = tf.apply(new double[]{Math.cos(a1) * r, 0, Math.sin(a1) * r});
            double[] o0t = tf.apply(new double[]{Math.cos(a0) * r, t, Math.sin(a0) * r}), o1t = tf.apply(new double[]{Math.cos(a1) * r, t, Math.sin(a1) * r});
            double[] i0t = tf.apply(new double[]{Math.cos(a0) * hole, t, Math.sin(a0) * hole}), i1t = tf.apply(new double[]{Math.cos(a1) * hole, t, Math.sin(a1) * hole});
            double[] i0b = tf.apply(new double[]{Math.cos(a0) * hole, 0, Math.sin(a0) * hole}), i1b = tf.apply(new double[]{Math.cos(a1) * hole, 0, Math.sin(a1) * hole});
            outward(m, o0b, o1b, o1t, o0t, c, Material.STONE);
            outward(m, o0t, o1t, i1t, i0t, tf.apply(new double[]{0, -1, 0}), Material.STONE);
            outward(m, i0b, i1b, o1b, o0b, tf.apply(new double[]{0, t + 1, 0}), Material.STONE);
            outward(m, i1t, i0t, i0b, i1b, tf.apply(new double[]{Math.cos((a0 + a1) / 2) * r * 3, t / 2, Math.sin((a0 + a1) / 2) * r * 3}), Material.DARK);
        }
    }

    /** Topf aus Speckstein: Halbmesser r, Höhe h, nach oben leicht weiter, offen (innen dunkel). */
    public static void pot(Mesh m, double x, double y, double z, double r, double h) {
        int n = 14;
        double[] ctr = {x, y + h / 2, z};
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            double[] b0 = {x + Math.cos(a0) * r * 0.85, y, z + Math.sin(a0) * r * 0.85}, b1 = {x + Math.cos(a1) * r * 0.85, y, z + Math.sin(a1) * r * 0.85};
            double[] t0 = {x + Math.cos(a0) * r, y + h, z + Math.sin(a0) * r}, t1 = {x + Math.cos(a1) * r, y + h, z + Math.sin(a1) * r};
            outwardUV(m, b0, b1, t1, t0, ctr, Material.SOAPSTONE);
            double[] ib0 = {x + Math.cos(a0) * r * 0.75, y + 0.06, z + Math.sin(a0) * r * 0.75}, ib1 = {x + Math.cos(a1) * r * 0.75, y + 0.06, z + Math.sin(a1) * r * 0.75};
            outward(m, t0, t1, ib1, ib0, new double[]{x, y + h + 1, z}, Material.DARK);
        }
    }

    /** Viereck mit der Normale weg von from. */
    private static void outward(Mesh m, double[] a, double[] b, double[] c, double[] d, double[] from, Material mat) {
        double[] mid = {(a[0] + c[0]) / 2 - from[0], (a[1] + c[1]) / 2 - from[1], (a[2] + c[2]) / 2 - from[2]};
        if (Mesh.dot(Mesh.normal(a, b, d), mid) >= 0) m.quad(a, b, c, d, mat, 0, 0, 0.9);
        else m.quad(a, d, c, b, mat, 0, 0, 0.9);
    }

    /** Wie {@link #outward}, mit v von unten (0) nach oben (1) für den Ruß am Topf. */
    private static void outwardUV(Mesh m, double[] a, double[] b, double[] c, double[] d, double[] from, Material mat) {
        double[] mid = {(a[0] + c[0]) / 2 - from[0], (a[1] + c[1]) / 2 - from[1], (a[2] + c[2]) / 2 - from[2]};
        int i0 = m.vertices();
        double[] n = Mesh.normal(a, b, d);
        boolean flip = Mesh.dot(n, mid) < 0;
        if (flip) n = new double[]{-n[0], -n[1], -n[2]};
        double w = Math.hypot(b[0] - a[0], b[2] - a[2]);
        m.vertex(a[0], a[1], a[2], n[0], n[1], n[2], 0, 0, mat, 0.8);
        m.vertex(b[0], b[1], b[2], n[0], n[1], n[2], w, 0, mat, 0.8);
        m.vertex(c[0], c[1], c[2], n[0], n[1], n[2], w, 1, mat, 1);
        m.vertex(d[0], d[1], d[2], n[0], n[1], n[2], 0, 1, mat, 1);
        if (!flip) { m.tri(i0, i0 + 1, i0 + 2); m.tri(i0, i0 + 2, i0 + 3); }
        else { m.tri(i0, i0 + 2, i0 + 1); m.tri(i0, i0 + 3, i0 + 2); }
    }
}
