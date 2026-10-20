package com.dan.timber;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Seit Haithabu Phase 11: das Innere eines begehbaren Hauses. Ein Raum ({@link Room}) ist die Hülle innen (Wände,
 * Dach, Boden) mit ihren Öffnungen (Türen, Rauchlöcher); er beantwortet, ob ein Punkt drin liegt, durch welche Öffnung
 * ein Strahl hinausgeht (Sonne im Haus) und wie viel Himmel ein Punkt durch die Öffnungen sieht. {@link #hall} baut
 * die Einrichtung einer Halle wie des Versammlungshauses im Museum: Lehmboden, Langfeuer mit Randsteinen,
 * Wandbänke mit Fellen, Sparren und Querbalken unter dem Reet, Kessel an der Kette, Gewichtswebstuhl am Giebel,
 * Truhe, Fass, Töpfe, Schilde und Werkzeug an der Wand, Talglampen, Fisch zum Räuchern unter dem First.
 * <p>
 * Belegt: Wandbänke und Langfeuer in der Mitte (Hallenhäuser der Wikingerzeit), Gewichtswebstühle (Webgewichte in
 * Haithabu zu Hunderten gefunden), Kessel an Ketten, Specksteintöpfe. Gestaltet [G]: Lage, Zahl und Maß jedes Stücks.
 * <p>
 * Örtliches Maß wie {@link HouseType}: x entlang des Firsts, z quer, y nach oben ab dem Boden; hier in wahren Metern
 * (mit der Streckung des Hauses). Für das instanzierte Zeichnen teilt {@link #hall} die Lage durch die Streckung, die
 * der Shader wieder anwendet.
 */
public final class Interior {
    private Interior() { }

    /** Wand-Seiten des Raums: Giebel +x, Giebel −x, Traufseite +z, Traufseite −z; dazu die beiden Dachflächen. */
    public static final int GABLE_PX = 0, GABLE_NX = 1, SIDE_PZ = 2, SIDE_NZ = 3, ROOF_PZ = 4, ROOF_NZ = 5, FLOOR = 6;

    /**
     * Eine Öffnung in der Innenfläche einer Seite: entlang der Seite von a0 bis a1 (für Giebel z, für Traufseiten x),
     * von y0 bis y1; ein Dreieck (Rauchloch) läuft von der ganzen Breite unten zur Spitze oben in der Mitte.
     */
    public record Opening(int face, double a0, double a1, double y0, double y1, boolean triangle, String label) {
        public double area() { return (a1 - a0) * (y1 - y0) * (triangle ? 0.5 : 1); }

        /** Liegt der Punkt (entlang a, Höhe y) in der Öffnung? */
        public boolean contains(double a, double y) {
            if (y < y0 || y > y1) return false;
            if (!triangle) return a >= a0 && a <= a1;
            double c = (a0 + a1) / 2, hw = (a1 - a0) / 2 * (y1 - y) / (y1 - y0);
            return Math.abs(a - c) <= hw;
        }

        /** Mitte der Fläche (entlang, Höhe). */
        public double[] centroid() { return new double[]{(a0 + a1) / 2, triangle ? y0 + (y1 - y0) / 3 : (y0 + y1) / 2}; }
    }

    /**
     * Der Raum innen: Haus, halbe Länge und Breite innen (m), Wandhöhe, Firsthöhe innen, Neigung der Dachunterseite
     * (tan, wahre Meter), Öffnungen. Alle Maße örtlich vom Boden des Hauses.
     */
    public record Room(House house, double hx, double hz, double wallH, double ridgeY, double tanRoof, List<Opening> openings) {

        /** Szene → örtlich (wahre Meter): {x entlang des Firsts, y über dem Boden, z quer}. */
        public double[] toLocal(double x, double y, double z) {
            double h = Math.toRadians(house.headingDeg()), dx = x - house.x(), dz = z - house.z();
            return new double[]{dx * Math.sin(h) - dz * Math.cos(h), y - house.y(), dx * Math.cos(h) + dz * Math.sin(h)};
        }

        /** Richtung Szene → örtlich. */
        public double[] dirToLocal(double[] d) {
            double h = Math.toRadians(house.headingDeg());
            return new double[]{d[0] * Math.sin(h) - d[2] * Math.cos(h), d[1], d[0] * Math.cos(h) + d[2] * Math.sin(h)};
        }

        /** Örtlich → Szene. */
        public double[] toWorld(double lx, double ly, double lz) {
            double h = Math.toRadians(house.headingDeg());
            return new double[]{house.x() + lx * Math.sin(h) + lz * Math.cos(h), house.y() + ly, house.z() - lx * Math.cos(h) + lz * Math.sin(h)};
        }

        /** Höhe der Dachunterseite über dem Boden bei z (örtlich). */
        public double roofY(double lz) { return ridgeY - Math.abs(lz) * tanRoof; }

        /** Liegt der örtliche Punkt im Raum (mit Rand m)? */
        public boolean insideLocal(double lx, double ly, double lz, double m) {
            return Math.abs(lx) < hx - m && Math.abs(lz) < hz - m && ly > -0.3 && ly < Math.min(roofY(lz), ridgeY) - m;
        }

        public boolean contains(double x, double y, double z) {
            double[] l = toLocal(x, y, z);
            return insideLocal(l[0], l[1], l[2], 0);
        }

        /** Rauminhalt (m³) und Oberfläche innen (m²). */
        public double volume() { return 4 * hx * hz * wallH + 2 * hx * hz * (ridgeY - wallH); }
        public double surface() {
            double slope = Math.hypot(hz, ridgeY - wallH);
            // Boden, Traufwände, Giebel (Rechteck und Dreieck), Dachunterseite
            return 4 * hx * hz + 2 * (2 * hx) * wallH + 2 * (2 * hz) * wallH + 2 * hz * (ridgeY - wallH) + 2 * (2 * hx) * slope;
        }
        public double openArea() { double s = 0; for (Opening o : openings) s += o.area(); return s; }

        /**
         * Wo verlässt der Strahl von p (örtlich, im Raum) in Richtung d (örtlich, Einheitsvektor) die Hülle?
         * {t, Seite, entlang, Höhe}; Seite nach {@link #GABLE_PX} usw.
         */
        public double[] exit(double[] p, double[] d) {
            double best = 1e9;
            int face = -1;
            if (d[0] > 1e-9) { double t = (hx - p[0]) / d[0]; if (t > -1e-3 && t < best) { best = t; face = GABLE_PX; } }
            if (d[0] < -1e-9) { double t = (-hx - p[0]) / d[0]; if (t > -1e-3 && t < best) { best = t; face = GABLE_NX; } }
            // Traufseiten nur bis zur Wandhöhe, darüber das Dach
            if (d[2] > 1e-9) { double t = (hz - p[2]) / d[2]; if (t > -1e-3 && t < best && p[1] + d[1] * t <= wallH) { best = t; face = SIDE_PZ; } }
            if (d[2] < -1e-9) { double t = (-hz - p[2]) / d[2]; if (t > -1e-3 && t < best && p[1] + d[1] * t <= wallH) { best = t; face = SIDE_NZ; } }
            // Dachflächen y = ridgeY ∓ tanRoof · z
            for (int s = -1; s <= 1; s += 2) {
                double den = d[1] + s * tanRoof * d[2];
                if (den > 1e-9) {
                    double t = (ridgeY - p[1] - s * tanRoof * p[2]) / den;
                    double zz = p[2] + d[2] * t;
                    if (t > 0 && t < best && zz * s >= -1e-6) { best = t; face = s > 0 ? ROOF_PZ : ROOF_NZ; }
                }
            }
            if (d[1] < -1e-9) { double t = -p[1] / d[1]; if (t < best) { best = t; face = FLOOR; } }
            double[] q = {p[0] + d[0] * best, p[1] + d[1] * best, p[2] + d[2] * best};
            double along = face == GABLE_PX || face == GABLE_NX ? q[2] : q[0];
            return new double[]{best, face, along, q[1]};
        }

        /** Sieht der Punkt p (örtlich) in Richtung d (örtlich) ins Freie, durch welche Öffnung? null = nein. */
        public Opening through(double[] p, double[] d) {
            double[] e = exit(p, d);
            int face = (int) e[1];
            for (Opening o : openings) if (o.face == face && o.contains(e[2], e[3])) return o;
            return null;
        }

        /** Mitte einer Öffnung örtlich und ihre Normale nach außen. */
        public double[][] openingFrame(Opening o) {
            double[] c = o.centroid();
            return switch (o.face) {
                case GABLE_PX -> new double[][]{{hx, c[1], c[0]}, {1, 0, 0}};
                case GABLE_NX -> new double[][]{{-hx, c[1], c[0]}, {-1, 0, 0}};
                case SIDE_PZ -> new double[][]{{c[0], c[1], hz}, {0, 0, 1}};
                default -> new double[][]{{c[0], c[1], -hz}, {0, 0, -1}};
            };
        }

        /**
         * Anteil des Himmels, den ein Punkt mit Normale n (örtlich) durch die Öffnungen sieht, als Beleuchtung relativ
         * zu einer waagerechten Fläche im Freien unter einem gleichmäßigen Himmel (Tageslichtquotient ohne
         * Rückstrahlung); jede Öffnung als Fläche mit ihrem Raumwinkel, die Hälfte ihres Blicks geht auf Boden und
         * Nachbarn [G].
         */
        public double skyFactor(double[] p, double[] n) {
            double s = 0;
            for (Opening o : openings) {
                double[][] f = openingFrame(o);
                double dx = f[0][0] - p[0], dy = f[0][1] - p[1], dz = f[0][2] - p[2], d2 = dx * dx + dy * dy + dz * dz, d = Math.sqrt(d2);
                double co = (dx * f[1][0] + dy * f[1][1] + dz * f[1][2]) / d, cp = (dx * n[0] + dy * n[1] + dz * n[2]) / d;
                if (co <= 0 || cp <= 0) continue;
                s += o.area() * co * cp / (Math.PI * Math.max(d2, o.area())) * (o.triangle ? 0.55 : 0.5);
            }
            return s;
        }

        /** Mittlerer Tageslichtquotient im Raum (mit Rückstrahlung der Flächen, Rückstrahlung ρ): für die Augenanpassung. */
        public double daylightFactor(double rho) {
            double s = 0;
            for (Opening o : openings) s += o.area() * (o.triangle ? 0.55 : 0.5);
            return s / (surface() * (1 - rho * rho));
        }

        /**
         * Zahlen für die Grafik (vier vec4 + je Öffnung zwei vec4, höchstens 8 Öffnungen): Ort und Richtung des Hauses,
         * Maße innen, Neigung und Zahl der Öffnungen, dann die Öffnungen {Seite, a0, a1, Dreieck} {y0, y1, Fläche, 0}.
         */
        public float[] pack() {
            float[] a = new float[16 + 8 * 8];
            a[0] = (float) house.x(); a[1] = (float) house.y(); a[2] = (float) house.z(); a[3] = (float) Math.toRadians(house.headingDeg());
            a[4] = (float) hx; a[5] = (float) hz; a[6] = (float) wallH; a[7] = (float) ridgeY;
            a[8] = (float) tanRoof; a[9] = Math.min(8, openings.size()); a[10] = 1; a[11] = 0;
            for (int i = 0; i < Math.min(8, openings.size()); i++) {
                Opening o = openings.get(i);
                int k = 16 + i * 8;
                a[k] = o.face; a[k + 1] = (float) o.a0; a[k + 2] = (float) o.a1; a[k + 3] = o.triangle ? 1 : 0;
                a[k + 4] = (float) o.y0; a[k + 5] = (float) o.y1; a[k + 6] = (float) o.area(); a[k + 7] = 0;
            }
            return a;
        }

        /** „Raum 17,1 × 5,7 m, First 6,2 m, 300 m³ · 2 Türen, 2 Rauchlöcher (4,5 m² offen)“ */
        public String describe() {
            int doors = 0, holes = 0;
            for (Opening o : openings) if (o.triangle) holes++; else doors++;
            return String.format(Locale.GERMAN, "Raum %.1f × %.1f m, First %.1f m, %.0f m³ · %d Türen, %d Rauchlöcher (%.1f m² offen)",
                    2 * hx, 2 * hz, ridgeY, volume(), doors, holes, openArea());
        }
    }

    /** Der Raum eines Hauses (Türen und Rauchlöcher offen wie {@link Builder#buildOpen}). */
    public static Room room(House h) {
        HouseType t = h.type();
        double ls = h.lengthScale(), ws = h.widthScale();
        double wt = t.wall == Material.DAUB ? 0.18 : t.wall == Material.PLANK ? 0.10 : 0.08;
        double hx = t.length * ls / 2 - wt, hzOut = t.width * ws / 2, hz = hzOut - wt;
        double tanRoof = t.tanPitch() / ws;
        double ridge = t.ridgeY();                       // Unterseite des Reets am First (die Streckung ändert die Höhe nicht)
        List<Opening> l = new ArrayList<>();
        double doorH = t == HouseType.GRUBENHAUS ? 1.5 : Math.min(1.85, t.wallHeight - 0.12), doorW = 1.0;
        for (HouseType.Door d : t.doors) {
            boolean gable = d.side() == HouseType.Side.EAST || d.side() == HouseType.Side.WEST;
            double span = gable ? t.width * ws : t.length * ls, c = -span / 2 + d.at() * span;
            int face = switch (d.side()) { case EAST -> GABLE_PX; case WEST -> GABLE_NX; case SOUTH -> SIDE_PZ; default -> SIDE_NZ; };
            // das offene Blatt steht innen am linken Pfosten und nimmt gut 8 cm der Öffnung
            l.add(new Opening(face, c - doorW / 2 + 0.09, c + doorW / 2 - 0.02, 0, doorH, false, "Tür " + sideName(d.side())));
        }
        if (t.smokeHole && t != HouseType.GRUBENHAUS) {
            double[] sh = Builder.smokeHoleShape(t);
            double hw = sh[2] * ws;
            l.add(new Opening(GABLE_PX, -hw, hw, sh[0], sh[1], true, "Rauchloch Giebel +x"));
            l.add(new Opening(GABLE_NX, -hw, hw, sh[0], sh[1], true, "Rauchloch Giebel −x"));
        }
        return new Room(h, hx, hz, t.wallHeight, ridge, tanRoof, List.copyOf(l));
    }

    private static String sideName(HouseType.Side s) {
        return switch (s) { case SOUTH -> "Traufe +z"; case NORTH -> "Traufe −z"; case EAST -> "Giebel +x"; default -> "Giebel −x"; };
    }

    // ==================================================================== Einrichtung

    /** Wo das Feuer brennt, örtlich (wahre Meter): Mitte des Langfeuers, auf der Asche. */
    public static double[] hearthSpot() { return new double[]{-0.2, 0.06, 0}; }

    /** Wo die Talglampen stehen, örtlich (wahre Meter), für die Lichter der Szene. */
    public static List<double[]> lampSpots(Room r) {
        return List.of(new double[]{r.hx() - 1.2, 1.55, r.hz() - 0.25}, new double[]{-r.hx() + 2.0, 1.55, -r.hz() + 0.25});
    }

    /** Wo der Kessel hängt (Mitte des Bauchs), örtlich. */
    public static double[] cauldronSpot() { return new double[]{0.55, 0.82, 0}; }

    /**
     * Die Einrichtung einer Halle (für {@link HouseType#HALLE}; andere Typen bekommen Boden, Herd und Bänke) als Netz in
     * örtlichen Maßen <i>vor</i> der Streckung, so dass {@code timber.vert} sie wieder richtig streckt. Gleicher seed,
     * gleiche Einrichtung.
     */
    public static Mesh hall(House h, long seed) {
        Room r = room(h);
        Mesh m = new Mesh();
        new Furnish(r, m, new SplittableRandom(seed)).all();
        return unstretch(m, h.lengthScale(), h.widthScale());
    }

    /** Teilt die Lage durch die Streckung und richtet die Normalen so, dass sie nach dem Strecken stimmen. */
    static Mesh unstretch(Mesh m, double ls, double ws) {
        Mesh o = new Mesh();
        float[] v = m.vertexData();
        int[] idx = m.indexData();
        for (int i = 0; i < m.vertices(); i++) {
            int k = i * Mesh.STRIDE;
            double nx = v[k + 3] * ls, ny = v[k + 4], nz = v[k + 5] * ws, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
            o.vertex(v[k] / ls, v[k + 1], v[k + 2] / ws, nx / nl, ny / nl, nz / nl, v[k + 6], v[k + 7], Material.values()[(int) v[k + 8]], v[k + 9]);
        }
        for (int i = 0; i < idx.length; i += 3) o.tri(idx[i], idx[i + 1], idx[i + 2]);
        return o;
    }

    private static final class Furnish {
        final Room r;
        final Mesh m;
        final SplittableRandom rnd;
        final double hx, hz, H, yr;

        Furnish(Room r, Mesh m, SplittableRandom rnd) {
            this.r = r;
            this.m = m;
            this.rnd = rnd;
            hx = r.hx();
            hz = r.hz();
            H = r.wallH();
            yr = r.ridgeY();
        }

        void all() {
            floor();
            hearth();
            benches();
            roofTimber();
            if (r.house().type() == HouseType.HALLE) {
                cauldron();
                loom();
                chest();
                wallGear();
                pots();
                lamps();
                fish();
            }
        }

        double u(double a, double b) { return a + (b - a) * rnd.nextDouble(); }

        /** Türen dieser Traufseite (entlang x), damit Bänke und Pfosten frei bleiben. */
        boolean nearDoor(double x, int side, double margin) {
            for (Opening o : r.openings())
                if (!o.triangle() && o.face() == (side > 0 ? SIDE_PZ : SIDE_NZ) && x > o.a0() - margin && x < o.a1() + margin) return true;
            return false;
        }

        void floor() {
            // gestampfter Lehm, 3 cm über dem Hausboden (das Gelände darunter blendet die Szene aus)
            double y = 0.03;
            m.quad(new double[]{-hx, y, hz}, new double[]{hx, y, hz}, new double[]{hx, y, -hz}, new double[]{-hx, y, -hz}, Material.FLOOR, -hx, -hz, 0.8);
            // Schwellen unter den offenen Türen
            for (Opening o : r.openings()) {
                if (o.triangle()) continue;
                double[][] f = r.openingFrame(o);
                if (o.face() == SIDE_PZ || o.face() == SIDE_NZ)
                    m.box((o.a0() + o.a1()) / 2, 0.035, f[0][2] - Math.signum(f[0][2]) * 0.1, (o.a1() - o.a0()) / 2 + 0.05, 0.035, 0.12, Material.FLOOR, false, 0.7);
            }
        }

        void hearth() {
            // Langfeuer: 2,4 × 1,0 m, Randsteine, darin Asche und gebrannter Lehm
            double cx = hearthSpot()[0], L = 1.2, W = 0.5;
            m.quad(new double[]{cx - L, 0.045, W}, new double[]{cx + L, 0.045, W}, new double[]{cx + L, 0.045, -W}, new double[]{cx - L, 0.045, -W}, Material.HEARTH, 0, 0, 0.9);
            int n = 0;
            for (double x = cx - L - 0.1; x <= cx + L + 0.11; x += u(0.22, 0.3)) {
                for (int s = -1; s <= 1; s += 2) stone(x, s * (W + 0.1), u(0.09, 0.14));
                n++;
            }
            for (double z = -W + 0.05; z <= W - 0.04; z += u(0.22, 0.28)) {
                stone(cx - L - 0.12, z, u(0.09, 0.13));
                stone(cx + L + 0.12, z, u(0.09, 0.13));
            }
            // ein paar Scheite neben dem Feuer, ein Stapel am Ende
            for (int i = 0; i < 6; i++) {
                double x = cx + L + 0.55 + (i % 3) * 0.16, y = 0.07 + (i / 3) * 0.13, z = u(-0.35, -0.05);
                m.beam(new double[]{x, y, z - 0.38}, new double[]{x + u(-0.04, 0.04), y, z + 0.38}, 0.065, 6, Material.POST, true, 0.6, 0.6);
            }
        }

        void stone(double x, double z, double s) {
            m.box(x, s * 0.55, z, s * u(0.85, 1.15), s * 0.55, s * u(0.8, 1.1), Material.STONE, false, 0.55);
        }

        void benches() {
            // Wandbänke: Erdbank mit Bohlenfront, 0,45 m hoch, 0,9 m tief, an beiden Traufseiten, an den Türen offen
            double bh = 0.45, bd = 0.9;
            for (int s = -1; s <= 1; s += 2) {
                double x = -hx + 0.6;
                while (x < hx - 0.6) {
                    double x0 = x, x1 = x;
                    while (x1 < hx - 0.6 && !nearDoor(x1, s, 0.35)) x1 += 0.1;
                    if (x1 - x0 > 0.8) bench(x0, x1, s, bh, bd);
                    x = x1 + 0.1;
                    while (x < hx - 0.6 && nearDoor(x, s, 0.35)) x += 0.1;
                }
            }
        }

        void bench(double x0, double x1, int s, double bh, double bd) {
            double zw = s * hz, zf = s * (hz - bd);
            double zc = (zw + zf) / 2;
            // Erdkern und Sitzbohlen
            m.box((x0 + x1) / 2, bh / 2, zc, (x1 - x0) / 2, bh / 2 - 0.02, bd / 2, Material.FLOOR, false, 0.5);
            m.box((x0 + x1) / 2, bh - 0.02, zc + s * 0.03, (x1 - x0) / 2, 0.025, bd / 2 - 0.03, Material.DECK, false, 0.8);
            // Front aus senkrechten Bohlen, Pfosten alle 1,5 m
            double[] a = {x0, 0, zf}, b = {x1, 0, zf}, c = {x1, bh - 0.04, zf}, d = {x0, bh - 0.04, zf};
            if (s > 0) m.quadUV(a, b, c, d, Material.PLANK, 0.5, 0.5, 0.85, 0.85);
            else m.quadUV(b, a, d, c, Material.PLANK, 0.5, 0.5, 0.85, 0.85);
            for (double x = x0 + 0.1; x < x1; x += 1.5)
                m.beam(new double[]{x, 0, zf - s * 0.05}, new double[]{x, bh + 0.06, zf - s * 0.05}, 0.06, 6, Material.POST, true, 0.6, 0.8);
            // Felle auf der Bank, unregelmäßig
            for (double x = x0 + 0.3; x < x1 - 0.5; x += u(1.2, 2.4)) {
                double w = u(0.55, 0.9), l = u(0.8, 1.3), y = bh + 0.012;
                double hzF = Math.min(w / 2, bd / 2 - 0.02), xc = x + l / 2, skew = u(-0.12, 0.12);
                double zz = zc + s * u(-0.05, 0.1);
                zz = Math.signum(zz) * Math.min(Math.abs(zz), hz - 0.03 - hzF);
                m.box(xc, y + 0.015, zz, l / 2, 0.025, hzF, Material.FUR, false, 0.8);
                // ein Fell hängt über die Kante
                if (rnd.nextDouble() < 0.5)
                    m.quadUV(new double[]{xc - l / 2 * 0.7, bh - 0.32, zf - s * 0.035}, new double[]{xc + l / 2 * 0.7 + skew, bh - 0.32, zf - s * 0.035},
                            new double[]{xc + l / 2 * 0.7 + skew, bh + 0.02, zf - s * 0.035}, new double[]{xc - l / 2 * 0.7, bh + 0.02, zf - s * 0.035}, Material.FUR, 0.7, 0.7, 0.9, 0.9);
            }
        }

        void roofTimber() {
            // Sparren unter dem Reet alle 0,9 m, Querbalken auf Wandhöhe, Firstpfette
            double step = 0.9;
            int n = (int) Math.floor(2 * hx / step);
            for (int i = 0; i <= n; i++) {
                double x = -hx + 0.25 + (2 * hx - 0.5) * i / n;
                for (int s = -1; s <= 1; s += 2) {
                    // knapp unter der Dachfläche (senkrecht gemessen: Halbmesser durch den Kosinus der Neigung), vom Rähm bis unter die Firstpfette
                    double off = 0.065 / Math.cos(Math.atan(r.tanRoof())) + 0.01;
                    double[] a = {x, r.roofY(hz - 0.05) - off, s * (hz - 0.05)}, b = {x, r.roofY(0.12) - off, s * 0.12};
                    m.beam(a, b, 0.065, 6, Material.POST, false, 0.45, 0.3);
                }
            }
            m.beam(new double[]{-hx, yr - 0.24, 0}, new double[]{hx, yr - 0.24, 0}, 0.1, 6, Material.POST, false, 0.3, 0.3);
            // Rähm innen an den Traufwänden
            for (int s = -1; s <= 1; s += 2)
                m.beam(new double[]{-hx, H - 0.12, s * (hz - 0.08)}, new double[]{hx, H - 0.12, s * (hz - 0.08)}, 0.09, 4, Material.POST, false, 0.45, 0.45);
            // Querbalken: der mittlere trägt die Kesselkette
            double[] xs = {-6.4, -3.3, cauldronSpot()[0], 3.3, 6.4};
            for (double x : xs) {
                if (Math.abs(x) > hx - 0.3) continue;
                m.beam(new double[]{x, H + 0.05, -hz + 0.11}, new double[]{x, H + 0.05, hz - 0.11}, 0.1, 6, Material.POST, true, 0.4, 0.4);
            }
            // zwei Reihen Innenpfosten tragen die Querbalken an den Enden des Feuers (frei von Türen)
            for (double x : new double[]{-3.3, 3.3})
                for (int s = -1; s <= 1; s += 2)
                    m.beam(new double[]{x, 0, s * (hz - 1.15)}, new double[]{x, H + 0.05, s * (hz - 1.15)}, 0.11, 8, Material.POST, false, 0.6, 0.5);
        }

        void cauldron() {
            // Eisenkessel an der Kette über dem Ostende des Feuers
            double[] c = cauldronSpot();
            double rr = 0.26, depth = 0.3;
            int seg = 14;
            Mesh k = m;
            // Bauch als Ringe (außen), Rand oben
            double[] ys = {c[1] - depth / 2, c[1] - depth / 4, c[1], c[1] + depth / 4, c[1] + depth / 2};
            double[] rs = {rr * 0.45, rr * 0.82, rr, rr * 0.98, rr * 0.92};
            int base = k.vertices();
            for (int j = 0; j < ys.length; j++)
                for (int i = 0; i <= seg; i++) {
                    double a = 2 * Math.PI * i / seg, ca = Math.cos(a), sa = Math.sin(a);
                    double dr = j == 0 ? rs[1] - rs[0] : j == ys.length - 1 ? rs[j] - rs[j - 1] : rs[j + 1] - rs[j - 1];
                    double dy = j == 0 ? ys[1] - ys[0] : j == ys.length - 1 ? ys[j] - ys[j - 1] : ys[j + 1] - ys[j - 1];
                    double nx = ca * dy, ny = -dr, nz = sa * dy, nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    k.vertex(c[0] + ca * rs[j], ys[j], c[2] + sa * rs[j], nx / nl, ny / nl, nz / nl, rs[j] * a, ys[j], Material.IRON, j == 0 ? 0.5 : 0.8);
                }
            for (int j = 0; j < ys.length - 1; j++)
                for (int i = 0; i < seg; i++) {
                    int a0 = base + j * (seg + 1) + i, a1 = a0 + 1, b0 = a0 + seg + 1, b1 = b0 + 1;
                    k.tri(a0, b1, a1);
                    k.tri(a0, b0, b1);
                }
            // Boden
            int ci = k.vertex(c[0], ys[0] - 0.02, c[2], 0, -1, 0, 0, 0, Material.IRON, 0.4);
            for (int i = 0; i < seg; i++) k.tri(ci, base + i + 1, base + i);
            // Inhalt: dunkle Brühe
            double ty = c[1] + depth / 2 - 0.05;
            int cc = k.vertex(c[0], ty, c[2], 0, 1, 0, 0, 0, Material.DARK, 0.6);
            int f0 = k.vertices();
            for (int i = 0; i < seg; i++) {
                double a = 2 * Math.PI * i / seg;
                k.vertex(c[0] + Math.cos(a) * rr * 0.9, ty, c[2] + Math.sin(a) * rr * 0.9, 0, 1, 0, 0, 0, Material.DARK, 0.6);
            }
            for (int i = 0; i < seg; i++) k.tri(cc, f0 + (i + 1) % seg, f0 + i);
            // Bügel und Kette bis zum Querbalken
            m.beam(new double[]{c[0], c[1] + depth / 2, c[2] - rr}, new double[]{c[0], c[1] + depth / 2 + 0.25, c[2]}, 0.01, 4, Material.IRON, false, 0.7, 0.7);
            m.beam(new double[]{c[0], c[1] + depth / 2, c[2] + rr}, new double[]{c[0], c[1] + depth / 2 + 0.25, c[2]}, 0.01, 4, Material.IRON, false, 0.7, 0.7);
            double top = H + 0.0;
            for (double y = c[1] + depth / 2 + 0.25; y < top; y += 0.07) {
                boolean alt = ((int) Math.round(y / 0.07)) % 2 == 0;
                m.box(c[0], y + 0.035, c[2], alt ? 0.012 : 0.03, 0.035, alt ? 0.03 : 0.012, Material.IRON, true, 0.7);
            }
        }

        void loom() {
            // Gewichtswebstuhl am Giebel +x, schräg an die Wand gelehnt: zwei Bäume, Tuchbaum oben, Kette mit Gewichten
            double xw = hx - 0.12, lean = 0.42, h2 = 2.05, w2 = 0.85;
            double zc = -0.9;
            for (int s = -1; s <= 1; s += 2) {
                double z = zc + s * w2;
                m.beam(new double[]{xw - lean, 0, z}, new double[]{xw - 0.04, h2, z}, 0.06, 6, Material.POST, true, 0.6, 0.5);
            }
            double xt = xw - 0.04 - 0.05, yt = h2 - 0.1;
            m.beam(new double[]{xt, yt, zc - w2 - 0.12}, new double[]{xt, yt, zc + w2 + 0.12}, 0.06, 8, Material.POST, true, 0.5, 0.5);
            // Gewebe oben (fertiges Stück aufgewickelt) und die Kettfäden darunter als feine Fläche
            double xb = xw - lean + 0.08, yb = 0.45;
            double[] t0 = {xt - 0.03, yt - 0.06, zc - w2 + 0.06}, t1 = {xt - 0.03, yt - 0.06, zc + w2 - 0.06};
            double fy = yt - 0.62, fx = xt - 0.03 - (yt - 0.06 - fy) * (lean / h2);
            double[] m0 = {fx, fy, zc - w2 + 0.06}, m1 = {fx, fy, zc + w2 - 0.06};
            double[] b0 = {xb, yb, zc - w2 + 0.06}, b1 = {xb, yb, zc + w2 - 0.06};
            // Vorder- und Rückseite, damit man sie von beiden Seiten sieht
            m.quad(m0, m1, t1, t0, Material.WOOL, 0, 0, 0.8);
            m.quad(t0, t1, m1, m0, Material.WOOL, 0, 0, 0.6);
            m.quad(b0, b1, m1, m0, Material.WOOL, 0, 1.0, 0.75);
            m.quad(m0, m1, b1, b0, Material.WOOL, 0, 1.0, 0.55);
            // Trennstab und Litzenstab quer
            m.beam(new double[]{fx - 0.05, fy - 0.35, zc - w2 - 0.05}, new double[]{fx - 0.05, fy - 0.35, zc + w2 + 0.05}, 0.025, 6, Material.POST, true, 0.6, 0.6);
            m.beam(new double[]{fx - 0.16, fy - 0.55, zc - w2 - 0.05}, new double[]{fx - 0.16, fy - 0.55, zc + w2 + 0.05}, 0.025, 6, Material.POST, true, 0.6, 0.6);
            // Webgewichte: Ringe aus Ton, zwei Reihen
            for (int row = 0; row < 2; row++)
                for (double z = zc - w2 + 0.1; z < zc + w2 - 0.05; z += 0.11) {
                    double y = yb - 0.08 - row * 0.05, x = xb + row * 0.06;
                    m.beam(new double[]{x, y - 0.05, z}, new double[]{x, y + 0.05, z}, 0.045, 6, Material.HEARTH, true, 0.6, 0.7);
                }
        }

        void chest() {
            // Truhe am Giebel −x, Deckel mit Eisenbändern
            double x = -hx + 0.55, z = 1.0;
            m.box(x, 0.24, z, 0.32, 0.24, 0.55, Material.PLANK, true, 0.55);
            m.box(x, 0.51, z, 0.34, 0.035, 0.57, Material.DOOR, false, 0.8);
            for (int s = -1; s <= 1; s += 2) m.box(x, 0.3, z + s * 0.35, 0.335, 0.26, 0.02, Material.IRON, true, 0.7);
            // Fass daneben
            double fx = -hx + 0.6, fz = -0.4;
            m.beam(new double[]{fx, 0, fz}, new double[]{fx, 0.75, fz}, 0.3, 12, Material.PLANK, true, 0.55, 0.85);
            for (double y : new double[]{0.12, 0.62}) m.beam(new double[]{fx, y, fz}, new double[]{fx, y + 0.04, fz}, 0.305, 12, Material.IRON, false, 0.6, 0.6);
        }

        void wallGear() {
            // Rundschilde an der Traufwand −z über der Bank, ein Beil, eine Lanze, Werkzeug am Pfosten
            double zw = -hz + 0.04;
            double[] xs = {-6.0, -4.7, 4.4, 5.7};
            for (double x : xs) {
                if (nearDoor(x, -1, 0.6)) continue;
                shield(x, 1.45, zw, 0.42);
            }
            // Beil: Stiel und Blatt
            double bx = 2.7, by = 1.25, bz = hz - 0.05;
            m.beam(new double[]{bx, by - 0.35, bz - 0.02}, new double[]{bx + 0.05, by + 0.35, bz - 0.02}, 0.017, 6, Material.POST, true, 0.7, 0.7);
            m.box(bx + 0.13, by + 0.27, bz - 0.02, 0.1, 0.06, 0.008, Material.IRON, false, 0.7);
            // Lanze am Pfosten
            m.beam(new double[]{-3.15, 0.02, -hz + 1.0}, new double[]{-3.25, 2.35, -hz + 1.08}, 0.016, 6, Material.POST, true, 0.6, 0.6);
            m.box(-3.25, 2.42, -hz + 1.08, 0.02, 0.12, 0.012, Material.IRON, true, 0.7);
        }

        void shield(double x, double y, double z, double rad) {
            int seg = 16;
            int c = m.vertex(x, y, z + 0.05, 0, 0, 1, 0, 0, Material.DOOR, 0.9);
            int f = m.vertices();
            for (int i = 0; i < seg; i++) {
                double a = 2 * Math.PI * i / seg;
                m.vertex(x + Math.cos(a) * rad, y + Math.sin(a) * rad, z + 0.02, 0, 0, 1, Math.cos(a) * rad, Math.sin(a) * rad, Material.DOOR, 0.8);
            }
            for (int i = 0; i < seg; i++) m.tri(c, f + i, f + (i + 1) % seg);
            // Buckel
            m.beam(new double[]{x, y, z + 0.04}, new double[]{x, y, z + 0.1}, 0.07, 8, Material.IRON, true, 0.8, 0.9);
        }

        void pots() {
            // Specksteintöpfe und eine Holzschale am Feuer
            double[][] p = {{-1.75, 0.0, 0.45, 0.16, 0.24}, {-1.85, 0.0, -0.4, 0.12, 0.18}, {1.75, 0.0, 0.55, 0.14, 0.2}};
            for (double[] q : p) m.beam(new double[]{q[0], q[1], q[2]}, new double[]{q[0], q[1] + q[4], q[2]}, q[3], 10, Material.SOAPSTONE, true, 0.55, 0.85);
            // ein Hocker
            m.beam(new double[]{2.3, 0, -0.9}, new double[]{2.3, 0.42, -0.9}, 0.17, 10, Material.POST, true, 0.55, 0.8);
        }

        void lamps() {
            // Talglampen: Tonschälchen auf einem Brett an der Wand
            for (double[] l : lampSpots(r)) {
                double zw = Math.signum(l[2]) * (hz - 0.02), zf = l[2] - Math.signum(l[2]) * 0.08;
                m.box(l[0], l[1] - 0.04, (zw + zf) / 2, 0.16, 0.02, Math.abs(zw - zf) / 2, Material.DECK, false, 0.7);
                m.beam(new double[]{l[0], l[1] - 0.02, l[2]}, new double[]{l[0], l[1] + 0.03, l[2]}, 0.05, 8, Material.HEARTH, true, 0.7, 0.8);
            }
        }

        void fish() {
            // Fisch zum Räuchern an einer Stange unter dem First, im Rauch
            double y = yr - 1.25, z = 0.35;
            double x0 = -2.4, x1 = 2.0;
            m.beam(new double[]{x0, y, z}, new double[]{x1, y, z}, 0.025, 6, Material.POST, true, 0.4, 0.4);
            for (double x = x0 + 0.25; x < x1 - 0.1; x += u(0.3, 0.42)) {
                double l = u(0.35, 0.5), w = u(0.09, 0.13);
                m.quadUV(new double[]{x - w, y - l, z}, new double[]{x + w, y - l, z}, new double[]{x + w * 0.7, y - 0.02, z}, new double[]{x - w * 0.7, y - 0.02, z}, Material.FISH, 0.5, 0.5, 0.6, 0.6);
                m.quadUV(new double[]{x + w, y - l, z}, new double[]{x - w, y - l, z}, new double[]{x - w * 0.7, y - 0.02, z}, new double[]{x + w * 0.7, y - 0.02, z}, Material.FISH, 0.5, 0.5, 0.6, 0.6);
            }
        }
    }
}
