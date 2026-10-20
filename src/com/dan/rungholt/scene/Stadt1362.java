package com.dan.rungholt.scene;

import com.dan.figure.Outfit;
import com.dan.figure.Person;
import com.dan.timber.Church;
import com.dan.timber.House;
import com.dan.timber.HouseType;
import com.dan.timber.Structures;
import com.dan.timber.Timber;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.function.DoubleBinaryOperator;

/**
 * Phase 11: die Stadt Rungholt am Abend vor der Mandränke, als Sommertag 1361 [G]. Auf den Warften der Kette stehen
 * zwei bis vier Häuser aus {@code com.dan.timber} (Hallenhäuser als Wohnstallhäuser, Wohnhäuser, Schuppen), auf den
 * Hofwarften in der Marsch ein Wohnstallhaus und oft eine Scheune, auf der Kirchwarft die Kirche aus Backstein
 * ({@link Church}) und das Pfarrhaus. Dazu die Herdfeuer (Rauch aus den Rauchlöchern), die Leute auf den Warften und
 * dem Weg, das Vieh auf den Fennen und die Schafe am Deich, und der Sielhafen vor dem Siel (Kaje, Boote; die Kogge in
 * {@link Sielhafen}). Alles fest aus dem Startwert 1361.
 */
public final class Stadt1362 {
    /** Lage der Kirche auf der Kirchwarft und ihre Richtung (Apsis nach Osten, leicht wie die Warft gedreht) [G]. */
    public static final double CHURCH_X = 0, CHURCH_Z = 0, CHURCH_HEADING = 94;

    private Stadt1362() { }

    /** Die Kirche als Haus (Typ für die Auswahl; gezeichnet wird {@link #churchMesh}) auf dem Grund. */
    public static House church(DoubleBinaryOperator ground) {
        return new House(HouseType.HALLE, CHURCH_X, ground.applyAsDouble(CHURCH_X, CHURCH_Z), CHURCH_Z, CHURCH_HEADING, 1, 1, 0.35, 1362);
    }

    public static com.dan.timber.Mesh churchMesh() { return Church.build(Church.rungholt()); }

    /** Höhe des Kirchturms über NHN (Schallöffnungen) für Ansicht 8. */
    public static double[] tower(DoubleBinaryOperator ground) {
        Church.Spec s = Church.rungholt();
        double lx = s.west() + s.towerSide() / 2;
        double h = Math.toRadians(CHURCH_HEADING);
        double x = CHURCH_X + Math.sin(h) * lx, z = CHURCH_Z - Math.cos(h) * lx;
        return new double[]{x, ground.applyAsDouble(CHURCH_X, CHURCH_Z) + 19, z};
    }

    // ------------------------------------------------------------------ Häuser

    /** Alle Häuser der Stadt (ohne Kirche), auf den Grund gestellt. */
    public static List<House> houses(DoubleBinaryOperator ground) {
        List<House> out = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(1361);
        for (Land1362.Warft w : Land1362.warften()) {
            boolean kirche = w.name().startsWith("Kirchwarft"), kette = w.name().startsWith("Warft ");
            List<double[]> slots = new ArrayList<>();   // {u, v, Richtung relativ, Typ}
            if (kirche) {
                // Pfarrhaus und Scheune nördlich der Kirche, ein Schuppen am Westende
                slots.add(new double[]{-8, -22, 0, HouseType.WOHNHAUS.ordinal()});
                slots.add(new double[]{14, -24, 0, HouseType.HALLE.ordinal()});
                slots.add(new double[]{-40, -6, 90, HouseType.SCHUPPEN.ordinal()});
            } else if (kette) {
                // längs der Kette: Wohnstallhaus in der Mitte, je nach Länge der Krone ein bis drei weitere
                slots.add(new double[]{0, w.b() * 0.25, 0, HouseType.HALLE.ordinal()});
                if (w.a() > 24) slots.add(new double[]{-w.a() * 0.55, -w.b() * 0.3, 0, (r.nextDouble() < 0.6 ? HouseType.WOHNHAUS : HouseType.HALLE).ordinal()});
                if (w.a() > 27) slots.add(new double[]{w.a() * 0.55, -w.b() * 0.3, 0, HouseType.WOHNHAUS.ordinal()});
                if (w.b() > 18 && r.nextDouble() < 0.6) slots.add(new double[]{w.a() * 0.15, -w.b() * 0.55, 90, HouseType.SCHUPPEN.ordinal()});
            } else {
                // Hofwarft: Wohnstallhaus, oft eine Scheune dahinter
                slots.add(new double[]{0, w.b() * 0.2, 0, HouseType.HALLE.ordinal()});
                if (r.nextDouble() < 0.7) slots.add(new double[]{(r.nextDouble() - 0.5) * w.a() * 0.6, -w.b() * 0.45, r.nextDouble() < 0.5 ? 0 : 90,
                        (r.nextDouble() < 0.5 ? HouseType.SCHUPPEN : HouseType.WOHNHAUS).ordinal()});
            }
            double h = Math.toRadians(w.headingDeg()), ax = Math.sin(h), az = -Math.cos(h);
            for (double[] sl : slots) {
                double x = w.x() + ax * sl[0] - az * sl[1], z = w.z() + az * sl[0] + ax * sl[1];
                HouseType t = HouseType.values()[(int) sl[3]];
                double head = w.headingDeg() + sl[2] + (r.nextDouble() - 0.5) * 8;
                House hs = new House(t, x, 0, z, head, 0.9 + 0.25 * r.nextDouble(), 0.9 + 0.2 * r.nextDouble(), 0.25 + 0.5 * r.nextDouble(), r.nextLong());
                boolean free = true;
                for (House o : out) if (Math.hypot(o.x() - x, o.z() - z) < o.radius() + hs.radius() - 2) { free = false; break; }
                if (kirche && Math.hypot(x - CHURCH_X, z - CHURCH_Z) < 28 && Math.abs(sl[1]) < 15) free = false;
                if (free) out.add(placed(hs, ground));
            }
        }
        return out;
    }

    /** Auf den Grund: die Mitte zwischen tiefster und höchster Ecke, höchstens 0,35 m über der tiefsten (wie Südfall). */
    public static House placed(House h, DoubleBinaryOperator ground) {
        double lo = 1e9, hi = -1e9;
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                double[] p = h.toWorld(sx * h.type().length / 2, 0, sz * h.type().width / 2);
                double g = ground.applyAsDouble(p[0], p[2]);
                lo = Math.min(lo, g); hi = Math.max(hi, g);
            }
        return new House(h.type(), h.x(), Math.min(lo + 0.35, (lo + hi) / 2), h.z(), h.headingDeg(), h.lengthScale(), h.widthScale(), h.state(), h.seed());
    }

    /** Die Herdfeuer: aus jedem Rauchloch eines Hauses mit Herd (im Sommer klein, nicht in jedem Haus) [G]. */
    public static List<com.dan.smoke.Source> hearths(List<House> houses, int month) {
        List<com.dan.smoke.Source> l = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(1362);
        boolean summer = month >= 5 && month <= 9;
        int k = 0;
        for (House h : houses) {
            if (!h.type().smokeHole) continue;
            if (summer && r.nextDouble() < 0.35) continue;   // im Sommer kocht nicht jedes Haus gleichzeitig
            for (double[] p : Timber.smokeHoles(h)) {
                l.add(new com.dan.smoke.Source(p[0], p[1], p[2], summer ? 7 : 12, summer ? 0.45 : 0.75, 0.12, k++));
                break;   // ein Herd je Haus
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ Vieh

    /** Ein Tier auf der Weide: Art (0 Rind, 1 Schaf), Ort, Richtung, Zufall. */
    public record Beast(int kind, double x, double z, double headingDeg, long seed) { }

    /**
     * Rinder in kleinen Herden auf den Fennen beiderseits der Kette (nicht auf Warften, nicht in Gräben), Schafe auf
     * der Landseite des Seedeichs [G].
     */
    public static List<Beast> beasts(Terrain land) {
        List<Beast> l = new ArrayList<>();
        SplittableRandom r = new SplittableRandom(13621);
        int herds = 0;
        for (int tries = 0; tries < 4000 && herds < 14; tries++) {
            double s = r.nextDouble(), off = (r.nextBoolean() ? 1 : -1) * (180 + 500 * r.nextDouble());
            double[] c = alongChain(s);
            double hx = c[0] - c[3] * off, hz = c[1] + c[2] * off;
            if (!marshAt(land, hx, hz)) continue;
            herds++;
            int n = 4 + r.nextInt(9);
            double dir = r.nextDouble() * 360;
            for (int i = 0, t = 0; i < n && t < 60; t++) {
                double x = hx + (r.nextDouble() - 0.5) * 60, z = hz + (r.nextDouble() - 0.5) * 60;
                if (!marshAt(land, x, z)) continue;
                l.add(new Beast(0, x, z, dir + (r.nextDouble() - 0.5) * 120, r.nextLong()));
                i++;
            }
        }
        // Schafe am Deich: auf der Landseite zwischen Fuß und Krone, in lockeren Gruppen
        for (int g = 0; g < 10; g++) {
            double gx = -2400 + 4400 * r.nextDouble();
            for (int i = 0; i < 6 + r.nextInt(8); i++) {
                double x = gx + (r.nextDouble() - 0.5) * 70;
                double z = dikeZ(x) - 4 - 7 * r.nextDouble();
                if (land != null && land.artAt(x, z) != Terrain.DEICH1362) continue;
                l.add(new Beast(1, x, z, r.nextDouble() * 360, r.nextLong()));
            }
        }
        return l;
    }

    private static boolean marshAt(Terrain land, double x, double z) {
        if (land != null && land.artAt(x, z) != Terrain.MARSCH) return false;
        if (Land1362.dikeSide(x, z) < 60 || Land1362.canalDist(x, z) < 25) return false;
        for (Land1362.Warft w : Land1362.warften()) if (Math.hypot(w.x() - x, w.z() - z) < w.radius() + 8) return false;
        // nicht in den Gräben der Fennen (alle 40 m, wie terrain.frag ungefähr)
        return true;
    }

    /** z des Seedeichs (Krone) bei x. */
    public static double dikeZ(double x) {
        double[][] d = Land1362.DIKE;
        for (int i = 1; i < d.length; i++)
            if (x >= d[i - 1][0] && x <= d[i][0]) { double t = (x - d[i - 1][0]) / (d[i][0] - d[i - 1][0]); return d[i - 1][1] + (d[i][1] - d[i - 1][1]) * t; }
        return x < d[0][0] ? d[0][1] : d[d.length - 1][1];
    }

    /** Punkt der Kette bei s 0..1: {x, z, Richtung x, Richtung z}. */
    public static double[] alongChain(double s) {
        double[][] c = Land1362.CHAIN;
        double len = Land1362.chainLength(), want = s * len, acc = 0;
        for (int i = 1; i < c.length; i++) {
            double l = Math.hypot(c[i][0] - c[i - 1][0], c[i][1] - c[i - 1][1]);
            if (acc + l >= want || i == c.length - 1) {
                double t = Math.max(0, Math.min(1, (want - acc) / l));
                return new double[]{c[i - 1][0] + (c[i][0] - c[i - 1][0]) * t, c[i - 1][1] + (c[i][1] - c[i - 1][1]) * t, (c[i][0] - c[i - 1][0]) / l, (c[i][1] - c[i - 1][1]) / l};
            }
            acc += l;
        }
        return new double[]{0, 0, 1, 0};
    }

    /**
     * Rind und Schaf als ein Netz in Weltkoordinaten ({@code com.dan.buoyancy.Mesh}, angestrichen): Rumpf, Beine, Hals,
     * Kopf gesenkt zum Grasen; Rinder rotbraun, schwarz oder gescheckt, Schafe wollweiß bis grau [G].
     */
    public static com.dan.buoyancy.Mesh herdMesh(List<Beast> beasts, DoubleBinaryOperator ground) {
        com.dan.buoyancy.Mesh m = new com.dan.buoyancy.Mesh();
        double[][] cows = {{0.16, 0.06, 0.03}, {0.03, 0.025, 0.02}, {0.25, 0.14, 0.07}, {0.10, 0.05, 0.03}};
        double[][] sheep = {{0.50, 0.47, 0.40}, {0.42, 0.40, 0.35}, {0.12, 0.10, 0.08}};
        for (Beast b : beasts) {
            SplittableRandom r = new SplittableRandom(b.seed());
            boolean cow = b.kind() == 0;
            double[] col = cow ? cows[r.nextInt(cows.length)] : sheep[r.nextInt(10) == 0 ? 2 : r.nextInt(2)];
            double L = cow ? 1.9 : 1.05, H = cow ? 0.62 : 0.42, W = cow ? 0.55 : 0.4, leg = cow ? 0.65 : 0.35;
            double s = 0.9 + 0.2 * r.nextDouble();
            L *= s; H *= s; W *= s; leg *= s;
            double y0 = ground.applyAsDouble(b.x(), b.z());
            double h = Math.toRadians(b.headingDeg()), fx = Math.sin(h), fz = -Math.cos(h), rx = Math.cos(h), rz = Math.sin(h);
            java.util.function.Function<double[], double[]> w = p -> new double[]{b.x() + rx * p[0] + fx * p[2], y0 + p[1], b.z() + rz * p[0] + fz * p[2]};
            // Rumpf als gedrehter Quader aus acht Ecken
            double by = leg + H / 2;
            box(m, w, 0, by, 0, W / 2, H / 2, L / 2, col);
            // Beine
            double[] legCol = cow ? scale(col, 0.8) : new double[]{0.06, 0.05, 0.04};
            for (int i = -1; i <= 1; i += 2) for (int j = -1; j <= 1; j += 2)
                box(m, w, i * W * 0.32, leg / 2, j * L * 0.36, 0.06 * s, leg / 2, 0.07 * s, legCol);
            // Hals und Kopf gesenkt (grasend) oder erhoben
            boolean graze = r.nextDouble() < 0.7;
            double hy = graze ? 0.25 * s : by + H * 0.3, hz = L / 2 + (graze ? 0.35 : 0.3) * s;
            box(m, w, 0, (by + hy) / 2 + 0.05, L / 2 + 0.12 * s, W * 0.22, Math.abs(by - hy) / 2 + 0.1, 0.16 * s, col);
            box(m, w, 0, hy + 0.08 * s, hz, W * 0.2, 0.13 * s, (cow ? 0.28 : 0.17) * s, cow && r.nextBoolean() ? new double[]{0.45, 0.42, 0.36} : col);
            if (cow) for (int i = -1; i <= 1; i += 2) box(m, w, i * W * 0.24, hy + 0.24 * s, hz - 0.12 * s, 0.10 * s, 0.025, 0.025, new double[]{0.5, 0.47, 0.4});   // Hörner
        }
        return m;
    }

    private static double[] scale(double[] c, double k) { return new double[]{c[0] * k, c[1] * k, c[2] * k}; }

    /** Quader im Tierraum (x quer, y oben, z vorn) über die Abbildung w in die Welt. */
    private static void box(com.dan.buoyancy.Mesh m, java.util.function.Function<double[], double[]> w, double cx, double cy, double cz, double hx, double hy, double hz, double[] col) {
        double[][] c = new double[8][];
        for (int i = 0; i < 8; i++) c[i] = w.apply(new double[]{cx + ((i & 1) == 0 ? -hx : hx), cy + ((i & 2) == 0 ? -hy : hy), cz + ((i & 4) == 0 ? -hz : hz)});
        // je Fläche die Reihenfolge so, dass die Normale nach außen zeigt (die Abbildung spiegelt: x rechts, z vorn)
        double[] mid = w.apply(new double[]{cx, cy, cz});
        int[][] f = {{0, 2, 3, 1}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 6, 7, 3}, {0, 4, 6, 2}, {1, 3, 7, 5}};
        for (int[] q : f) {
            double[] a = c[q[0]], b = c[q[1]], d = c[q[3]];
            double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
            double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            double fx = (a[0] + c[q[2]][0]) / 2 - mid[0], fy = (a[1] + c[q[2]][1]) / 2 - mid[1], fz = (a[2] + c[q[2]][2]) / 2 - mid[2];
            if (nx * fx + ny * fy + nz * fz >= 0) m.quad(a, b, c[q[2]], d, col, com.dan.buoyancy.Mesh.PAINT);
            else m.quad(d, c[q[2]], b, a, col, com.dan.buoyancy.Mesh.PAINT);
        }
    }

    // ------------------------------------------------------------------ Leute

    /**
     * Die Leute von Rungholt [G]: ein Teil geht auf dem Weg der Warftenkette hin und her, die anderen stehen und arbeiten
     * an den Häusern (bücken sich, schauen). Nachts sind sie im Haus.
     */
    public static final class People {
        public final List<Person> people = new ArrayList<>();
        private final double[][] target, home;
        private final double[] wait;
        private final boolean[] walker;
        private final SplittableRandom r = new SplittableRandom(1361);

        public People(List<House> houses, int month, int count) {
            target = new double[count][];
            home = new double[count][];
            wait = new double[count];
            walker = new boolean[count];
            homeSaved = new double[count][];
            walkerSaved = new boolean[count];
            for (int i = 0; i < count; i++) {
                boolean child = r.nextDouble() < 0.2;
                Person p = new Person(i, child ? Person.Role.CHILD : Person.Role.ADULT, Outfit.medieval(month, r, child));
                if (child) p.scale = 0.7 + 0.15 * r.nextDouble();
                walker[i] = r.nextDouble() < 0.45;
                House h = houses.get(r.nextInt(houses.size()));
                double[] d = h.toWorld(0, 0, (h.type().width / 2 + 2.5) * (r.nextBoolean() ? 1 : -1));
                home[i] = new double[]{d[0] + (r.nextDouble() - 0.5) * 6, d[2] + (r.nextDouble() - 0.5) * 6};
                if (walker[i]) { double[] c = alongChain(r.nextDouble()); p.x = c[0]; p.z = c[1]; }
                else { p.x = home[i][0]; p.z = home[i][1]; }
                p.heading = r.nextDouble() * 360;
                target[i] = new double[]{p.x, p.z};
                wait[i] = r.nextDouble() * 60;
                people.add(p);
            }
        }

        /** dt Sekunden weiter; ground gibt die Höhe, light 0..1 das Tageslicht (unter 0,15 bleiben sie drinnen). */
        public void step(double dt, DoubleBinaryOperator ground, double light) {
            for (int i = 0; i < people.size(); i++) {
                Person p = people.get(i);
                double left = dt;
                for (int guard = 0; guard < 50 && left > 1e-6; guard++) {
                    if (wait[i] > 0) {
                        double w = Math.min(wait[i], left);
                        wait[i] -= w; left -= w;
                        p.gait = 0;
                        if (wait[i] <= 0) pick(i, p);
                        continue;
                    }
                    double dx = target[i][0] - p.x, dz = target[i][1] - p.z, d = Math.hypot(dx, dz);
                    double v = p.role == Person.Role.CHILD ? 1.1 : 1.3, step = Math.min(d, v * left);
                    if (d < 0.05) { wait[i] = walker[i] ? 5 + 40 * r.nextDouble() : 20 + 200 * r.nextDouble(); p.bend = walker[i] ? 0 : r.nextDouble() < 0.4 ? 0.8 : 0; continue; }
                    p.heading = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
                    p.x += dx / d * step;
                    p.z += dz / d * step;
                    p.gait = 1;
                    p.advance(step);
                    p.bend = 0;
                    left -= step / v;
                }
                p.y = ground.applyAsDouble(p.x, p.z);
            }
            visible = !hidden && (always || light >= 0.15);
        }

        /** Sind sie draußen (Tag)? */
        public boolean visible = true;
        /** Phase 12: auch nachts draußen (in der Sturmnacht), oder alle in der Kirche. */
        public boolean always, hidden;

        /**
         * Phase 12: alle gehen zu einem Ort (die Kirchwarft) und bleiben im Umkreis r (m); null-Ort: zurück nach Hause
         * und auf die Kette wie vorher.
         */
        public void gather(double[] at, double r) {
            for (int i = 0; i < people.size(); i++) {
                if (at == null) { if (homeSaved[i] != null) home[i] = homeSaved[i]; walker[i] = walkerSaved[i]; continue; }
                if (homeSaved[i] == null) { homeSaved[i] = home[i]; walkerSaved[i] = walker[i]; }
                double a = this.r.nextDouble() * 2 * Math.PI, d = r * Math.sqrt(this.r.nextDouble());
                home[i] = new double[]{at[0] + Math.cos(a) * d, at[1] + Math.sin(a) * d};
                walker[i] = false;
                target[i] = home[i].clone();
                wait[i] = 0;
            }
        }
        private double[][] homeSaved;
        private boolean[] walkerSaved;

        private void pick(int i, Person p) {
            if (walker[i]) {
                double[] c = alongChain(r.nextDouble());
                target[i] = new double[]{c[0] + (r.nextDouble() - 0.5) * 4, c[1] + (r.nextDouble() - 0.5) * 4};
            } else target[i] = new double[]{home[i][0] + (r.nextDouble() - 0.5) * 16, home[i][1] + (r.nextDouble() - 0.5) * 16};
        }
    }

    // ------------------------------------------------------------------ Sielhafen (Holz)

    /**
     * Kaje, Uferbefestigung und Boote im Sielhafen ({@code com.dan.timber.Structures}): eine Bohlenkaje an der Ostseite
     * des Hafenbeckens (60 m, 6 m breit, Deck NHN +2,2 m), Faschinen und Pfähle am Westufer, drei Boote im Schlick [G].
     */
    public static Structures.Set harbour(double[] hb, Structures.Ground g) {
        Structures.Set s = new Structures.Set();
        double ax = hb[2], az = hb[3], qx = -az, qz = ax;   // längs (nach See) und quer (nach Osten, wenn das Tief nach Süden läuft)
        if (qx < 0) { qx = -qx; qz = -qz; }
        double off = Land1362.BASIN_ACROSS + 1;
        double[][] kaje = {{hb[0] + qx * off - ax * 30, hb[1] + qz * off - az * 30}, {hb[0] + qx * off + ax * 30, hb[1] + qz * off + az * 30}};
        s.platform(kaje, 6, 2.2, g, 1361);
        double[][] west = {{hb[0] - qx * (off + 2) - ax * 40, hb[1] - qz * (off + 2) - az * 40}, {hb[0] - qx * (off + 2) + ax * 40, hb[1] - qz * (off + 2) + az * 40}};
        s.revetment(west, 0.9, g, 1362);
        double hd = Math.toDegrees(Math.atan2(ax, -az));
        for (int i = 0; i < 3; i++) {
            double t = -35 + 22 * i;
            double x = hb[0] - qx * (off - 4) + ax * t, z = hb[1] - qz * (off - 4) + az * t;
            s.boat(x, z, hd + 70 + 20 * i, 6.5, 6 + 4 * i, g, 1370 + i);
        }
        return s;
    }

    /** Liegeplatz der Kogge an der Kaje: {x, z, Richtung (Bug zum Siel)}. */
    public static double[] berth(double[] hb) {
        double ax = hb[2], az = hb[3], qx = -az, qz = ax;
        if (qx < 0) { qx = -qx; qz = -qz; }
        double off = Land1362.BASIN_ACROSS + 1 - 3 - 4.4;
        return new double[]{hb[0] + qx * off, hb[1] + qz * off, (Math.toDegrees(Math.atan2(-ax, az)) + 360) % 360};
    }
}
