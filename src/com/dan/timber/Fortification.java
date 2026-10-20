package com.dan.timber;

import java.util.SplittableRandom;

/**
 * Wehrbau aus Holz (seit Haithabu Phase 7): der Wehrgang hinter einer Palisade und der Torbau in einem Erdwall. Wie die
 * übrigen {@link Structures} feste Netze in Szenenmaßen, mit einer feinen und einer groben Form. Maße nach Befunden an
 * wikingerzeitlichen Wällen (Haithabu, Danewerk, Trelleborg), Einzelheiten gestaltet [G].
 */
public final class Fortification {
    private Fortification() { }

    // ==================================================================== Wehrgang

    /**
     * Wehrgang: ein Bohlensteg width breit, lift Meter über dem Boden auf Pfostenpaaren alle 2,5 m, mit Längshölzern
     * unter den Bohlen. Er folgt dem Boden (anders als die Landebrücke mit fester Höhe), steht also auch auf einem Wall,
     * dessen Kamm steigt und fällt. Typisch: 1,2 m hinter einer Palisade von 2,5 m, so dass ein Mann darüber blickt.
     */
    public static Mesh wallWalk(double[][] line, double width, double lift, Structures.Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double hw = width / 2;
        Structures.walk(line, 2.5, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx;
            for (int s = -1; s <= 1; s += 2) {
                double px = x + nx * s * (hw - 0.12), pz = z + nz * s * (hw - 0.12), y = g.height(px, pz);
                m.beam(new double[]{px, y - 0.4, pz}, new double[]{px, g.height(x, z) + lift - 0.05, pz}, 0.1, 5, Material.POST, false, 0.5, 0.9);
            }
        });
        Structures.segments(line, 2.5, (x0, z0, x1, z1) -> {
            double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz);
            if (l < 1e-6) return;
            double nx = -dz / l, nz = dx / l;
            for (double f : new double[]{-0.7, 0.7})
                m.beam(new double[]{x0 + nx * f * hw, g.height(x0, z0) + lift - 0.12, z0 + nz * f * hw},
                        new double[]{x1 + nx * f * hw, g.height(x1, z1) + lift - 0.12, z1 + nz * f * hw}, 0.07, 4, Material.POST, false, 0.5, 0.5);
        });
        Structures.walk(line, 0.27, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx, shift = (r.nextDouble() - 0.5) * 0.08;
            Structures.plank(m, x + nx * shift, g.height(x, z) + lift, z + nz * shift, dx, dz, hw * (0.95 + r.nextDouble() * 0.08), 0.12, 0.03,
                    Material.DECK, (r.nextDouble() - 0.5) * 0.03);
        });
        return m;
    }

    /** Wehrgang grob: das Deck als Band in der Höhe lift. */
    public static Mesh coarseWallWalk(double[][] line, double width, double lift, Structures.Ground g) {
        return Structures.band(line, width, g, lift, Material.DECK);
    }

    // ==================================================================== Torbau

    /**
     * Ein Tor im Erdwall. (x, z) Mitte der Durchfahrt auf der Kammlinie, headingDeg ihre Richtung von innen nach außen
     * (Grad von Nord im Uhrzeigersinn). Die Torgasse fällt von floorIn (inneres Ende) nach floorOut (äußeres Ende);
     * width lichte Breite, depth Länge durch den Wall. Der Wall hat den Kamm topY, oben flach bis ±flat um die Kammlinie,
     * dann fallen seine Flanken mit slope (m je m); so hoch reicht die Fassung. Die Plattform reicht außen bis platformS
     * (über der Palisade), tower setzt einen Torturm darauf, leaves zwei Torflügel darunter. Ohne Flügel und Turm ist es
     * ein Durchlass, etwa für einen Bach, mit dem Wehrgang als Steg darüber.
     */
    public record Gate(double x, double z, double headingDeg, double floorIn, double floorOut, double topY, double width, double depth,
                       double flat, double slope, double platformS, boolean tower, boolean leaves) {
        /** Ein Ort im Rahmen des Tors: s entlang der Durchfahrt (+ nach außen), q quer (+ rechts von innen gesehen), y. */
        public double[] at(double s, double q, double y) {
            double a = Math.toRadians(headingDeg);
            double ax = Math.sin(a), az = -Math.cos(a), bx = Math.cos(a), bz = Math.sin(a);
            return new double[]{x + ax * s + bx * q, y, z + az * s + bz * q};
        }

        /** Boden der Torgasse an der Stelle s. */
        public double floor(double s) { return floorIn + (floorOut - floorIn) * Math.max(0, Math.min(1, (s + depth / 2) / depth)); }

        /** Oberkante der Fassung an der Stelle s: der Wallkörper. */
        public double top(double s) { return Math.max(floor(s) + 0.4, topY - Math.max(0, Math.abs(s) - flat) * slope); }

        /** Lichte Höhe unter der Plattform (m). */
        public double clearance() { return topY - floor(platformS) - 0.35; }
    }

    /**
     * Torbau [G nach Trelleborg und dem Danewerk]: die Torgasse ist mit senkrechten Spaltbohlen gefasst, die das Erdreich
     * halten, Pfosten alle 2 m davor; unter der Plattform zwei Torflügel, tagsüber nach innen offen; die Plattform liegt
     * in Höhe des Wehrgangs. Mit Turm stehen auf vier Eckpfosten eine Brustwehr aus Spitzpfählen und ein Bretterdach.
     */
    public static Mesh gate(Gate gt, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double hw = gt.width() / 2, hd = gt.depth() / 2, y1 = gt.topY();
        // Fassung der Torgasse: Bohlen zur Gasse hin, oben ein Rähm, Pfosten davor
        for (int side = -1; side <= 1; side += 2) {
            double q = side * (hw + 0.08);
            for (double s = -hd; s < hd - 1e-6; s += 1.0) {
                double s1 = Math.min(hd, s + 1.0);
                double[] a = gt.at(s, q, gt.floor(s) - 0.3), b = gt.at(s1, q, gt.floor(s1) - 0.3), c = gt.at(s1, q, gt.top(s1)), d = gt.at(s, q, gt.top(s));
                double[] n = Mesh.normal(a, b, d), toward = sub(gt.at(s, 0, a[1]), a);
                if (Mesh.dot(n, toward) >= 0) { m.quadUV(a, b, c, d, Material.PLANK, 0.55, 0.55, 0.95, 0.95); m.quadUV(b, a, d, c, Material.PLANK, 0.3); }
                else { m.quadUV(b, a, d, c, Material.PLANK, 0.55, 0.55, 0.95, 0.95); m.quadUV(a, b, c, d, Material.PLANK, 0.3); }
                m.beam(gt.at(s, q, gt.top(s) + 0.08), gt.at(s1, q, gt.top(s1) + 0.08), 0.11, 4, Material.POST, false, 0.8, 0.8);
            }
            for (double s = -hd + 0.4; s <= hd - 0.3; s += 2.0) {
                double[] p = gt.at(s, side * (hw - 0.1), 0);
                m.beam(new double[]{p[0], gt.floor(s) - 0.6, p[2]}, new double[]{p[0], gt.top(s) + 0.15, p[2]}, 0.15, 6, Material.POST, true, 0.5, 0.9);
            }
        }
        double sp1 = gt.platformS(), sp0 = sp1 - 3.4;
        if (gt.leaves()) {
            // Schwelle und Torflügel unter der Plattform, nach innen offen
            double sg = sp1 - 0.7, f = gt.floor(sg);
            m.beam(gt.at(sg, -hw, f + 0.06), gt.at(sg, hw, f + 0.06), 0.12, 4, Material.POST, true, 0.6, 0.6);
            double leafH = Math.min(3.6, gt.clearance() - 0.3), open = Math.toRadians(68 + r.nextDouble() * 10);
            double[] in = sub(gt.at(-1, 0, 0), gt.at(0, 0, 0));
            for (int side = -1; side <= 1; side += 2) {
                double[] hinge = gt.at(sg - 0.15, side * (hw - 0.3), 0);
                double[] across = sub(gt.at(0, -side, 0), gt.at(0, 0, 0));
                double dx = across[0] * Math.cos(open) + in[0] * Math.sin(open), dz = across[2] * Math.cos(open) + in[2] * Math.sin(open);
                slab(m, hinge[0], f + 0.12, hinge[2], dx, dz, hw - 0.35, leafH, 0.09, Material.DOOR);
                m.beam(new double[]{hinge[0], f + 0.1, hinge[2]}, new double[]{hinge[0], f + leafH + 0.25, hinge[2]}, 0.11, 6, Material.POST, true, 0.6, 1);
            }
        }
        // Plattform in Höhe des Wehrgangs
        for (double sb : new double[]{sp0 + 0.15, sp1 - 0.15})
            m.beam(gt.at(sb, -hw - 0.6, y1 - 0.22), gt.at(sb, hw + 0.6, y1 - 0.22), 0.16, 4, Material.POST, true, 0.6, 0.6);
        double[] dirS = sub(gt.at(1, 0, 0), gt.at(0, 0, 0));
        for (double s = sp0 + 0.14; s <= sp1 - 0.1; s += 0.27) {
            double[] c = gt.at(s, 0, y1);
            Structures.plank(m, c[0], c[1], c[2], dirS[0], dirS[2], hw + 0.55 + (r.nextDouble() - 0.5) * 0.06, 0.12, 0.035, Material.DECK, 0);
        }
        if (gt.tower()) {
            double top = y1 + 2.35;
            for (double s : new double[]{sp0, sp1 - 0.1})
                for (int side = -1; side <= 1; side += 2) {
                    double[] p = gt.at(s, side * (hw + 0.45), 0);
                    m.beam(new double[]{p[0], gt.floor(s) - 0.5, p[2]}, new double[]{p[0], top + 0.1, p[2]}, 0.17, 6, Material.POST, true, 0.6, 1);
                }
            // Brustwehr: Spitzpfähle am äußeren Rand und an den Seiten der Plattform
            for (double q = -hw - 0.45; q <= hw + 0.45 + 1e-6; q += 0.3) stake(m, gt.at(sp1 - 0.1, q, y1), 1.25, 0.09);
            for (int side = -1; side <= 1; side += 2)
                for (double s = sp0 + 0.3; s < sp1 - 0.3; s += 0.3) stake(m, gt.at(s, side * (hw + 0.45), y1), 1.1, 0.09);
            // Bretterdach, First quer zur Gasse (parallel zum Wall)
            double eave = top - 0.05, ridge = top + 1.5, ov = 0.6, qa = -hw - 1.0, qb = hw + 1.0, sm = (sp0 + sp1) / 2;
            double[] e0a = gt.at(sp0 - ov, qa, eave), e0b = gt.at(sp0 - ov, qb, eave), ra = gt.at(sm, qa, ridge), rb = gt.at(sm, qb, ridge),
                    e1a = gt.at(sp1 + ov, qa, eave), e1b = gt.at(sp1 + ov, qb, eave);
            roof(m, e0a, e0b, rb, ra);
            roof(m, e1b, e1a, ra, rb);
            for (int side = -1; side <= 1; side += 2) {
                double q = side * (hw + 0.5);
                double[] a = gt.at(sp0, q, eave), b = gt.at(sp1 - 0.1, q, eave), c = gt.at(sm, q, ridge - 0.05);
                double[] n = Mesh.normal(a, b, c), out = sub(gt.at(0, side, 0), gt.at(0, 0, 0));
                if (Mesh.dot(n, out) >= 0) m.triangle(a, b, c, Material.PLANK, 0.8); else m.triangle(b, a, c, Material.PLANK, 0.8);
            }
            m.beam(gt.at(sm, qa, ridge + 0.04), gt.at(sm, qb, ridge + 0.04), 0.09, 4, Material.POST, true, 1, 1);
        }
        return m;
    }

    /** Torbau grob: Fassung als Flächen, Plattform als Kasten, Dach mit zwei Flächen. */
    public static Mesh coarseGate(Gate gt) {
        Mesh m = new Mesh();
        double hw = gt.width() / 2, hd = gt.depth() / 2, y1 = gt.topY();
        for (int side = -1; side <= 1; side += 2) {
            double q = side * (hw + 0.08);
            for (double s = -hd; s < hd - 1e-6; s += 4.0) {
                double s1 = Math.min(hd, s + 4.0);
                double[] a = gt.at(s, q, gt.floor(s) - 0.3), b = gt.at(s1, q, gt.floor(s1) - 0.3), c = gt.at(s1, q, gt.top(s1)), d = gt.at(s, q, gt.top(s));
                double[] n = Mesh.normal(a, b, d), toward = sub(gt.at(s, 0, a[1]), a);
                if (Mesh.dot(n, toward) >= 0) m.quadUV(a, b, c, d, Material.PLANK, 0.6, 0.6, 0.95, 0.95); else m.quadUV(b, a, d, c, Material.PLANK, 0.6, 0.6, 0.95, 0.95);
            }
        }
        double sp1 = gt.platformS(), sp0 = sp1 - 3.4;
        double[] c = gt.at((sp0 + sp1) / 2, 0, y1 - 0.12);
        double[] dirS = sub(gt.at(1, 0, 0), gt.at(0, 0, 0));
        Structures.plank(m, c[0], c[1], c[2], dirS[0], dirS[2], hw + 0.6, (sp1 - sp0) / 2, 0.15, Material.DECK, 0);
        if (gt.tower()) {
            double top = y1 + 2.35, eave = top - 0.05, ridge = top + 1.5, ov = 0.6, qa = -hw - 1.0, qb = hw + 1.0, sm = (sp0 + sp1) / 2;
            for (double s : new double[]{sp0, sp1 - 0.1})
                for (int side = -1; side <= 1; side += 2) {
                    double[] p = gt.at(s, side * (hw + 0.45), 0);
                    m.beam(new double[]{p[0], y1 - 0.5, p[2]}, new double[]{p[0], top, p[2]}, 0.17, 4, Material.POST, false, 0.6, 1);
                }
            roof(m, gt.at(sp0 - ov, qa, eave), gt.at(sp0 - ov, qb, eave), gt.at(sm, qb, ridge), gt.at(sm, qa, ridge));
            roof(m, gt.at(sp1 + ov, qb, eave), gt.at(sp1 + ov, qa, eave), gt.at(sm, qa, ridge), gt.at(sm, qb, ridge));
            double[] a = gt.at(sp1 - 0.1, -hw - 0.45, y1), b = gt.at(sp1 - 0.1, hw + 0.45, y1), cc = gt.at(sp1 - 0.1, hw + 0.45, y1 + 1.3), d = gt.at(sp1 - 0.1, -hw - 0.45, y1 + 1.3);
            m.quadUV(a, b, cc, d, Material.POST, 0.7, 0.7, 1, 1);
            m.quadUV(b, a, d, cc, Material.POST, 0.7, 0.7, 1, 1);
        }
        return m;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Dachfläche aus Brettern, Faser von der Traufe (a, b) zum First (c, d); Normale nach oben. */
    private static void roof(Mesh m, double[] a, double[] b, double[] c, double[] d) {
        double[] n = Mesh.normal(a, b, d);
        if (n[1] >= 0) { m.quadUV(a, b, c, d, Material.PLANK, 0.85, 0.85, 1, 1); m.quadUV(b, a, d, c, Material.PLANK, 0.45); }
        else { m.quadUV(b, a, d, c, Material.PLANK, 0.85, 0.85, 1, 1); m.quadUV(a, b, c, d, Material.PLANK, 0.45); }
    }

    /** Senkrechte Platte: Fuß in (x, y, z), Länge len in Richtung (dx, dz), Höhe h, Dicke t (beidseitig). */
    static void slab(Mesh m, double x, double y, double z, double dx, double dz, double len, double h, double t, Material mat) {
        double l = Math.hypot(dx, dz);
        dx /= l; dz /= l;
        double nx = -dz * t / 2, nz = dx * t / 2;
        double[] a0 = {x - nx, y, z - nz}, b0 = {x + dx * len - nx, y, z + dz * len - nz}, a1 = {x + nx, y, z + nz}, b1 = {x + dx * len + nx, y, z + dz * len + nz};
        double[] a0t = {a0[0], y + h, a0[2]}, b0t = {b0[0], y + h, b0[2]}, a1t = {a1[0], y + h, a1[2]}, b1t = {b1[0], y + h, b1[2]};
        double[] ctr = {x + dx * len / 2, y + h / 2, z + dz * len / 2};
        face(m, a0, b0, b0t, a0t, mat, ctr);
        face(m, b1, a1, a1t, b1t, mat, ctr);
        face(m, b0, b1, b1t, b0t, mat, ctr);
        face(m, a1, a0, a0t, a1t, mat, ctr);
        face(m, a0t, b0t, b1t, a1t, mat, ctr);
    }

    /** Fläche mit der Normale vom Mittelpunkt weg; Faser senkrecht. */
    private static void face(Mesh m, double[] a, double[] b, double[] c, double[] d, Material mat, double[] ctr) {
        double[] mid = {(a[0] + c[0]) / 2 - ctr[0], (a[1] + c[1]) / 2 - ctr[1], (a[2] + c[2]) / 2 - ctr[2]};
        if (Mesh.dot(Mesh.normal(a, b, d), mid) >= 0) m.quadUV(a, b, c, d, mat, 0.8, 0.8, 1, 1);
        else m.quadUV(b, a, d, c, mat, 0.8, 0.8, 1, 1);
    }

    /** Spitzpfahl von p nach oben, h hoch, Radius r. */
    private static void stake(Mesh m, double[] p, double h, double r) {
        m.beam(new double[]{p[0], p[1] - 0.05, p[2]}, new double[]{p[0], p[1] + h, p[2]}, r, 5, Material.POST, false, 0.7, 1);
        Structures.spike(m, p[0], p[1] + h, p[2], r, 0.22);
    }

    private static double[] sub(double[] a, double[] b) { return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
}
