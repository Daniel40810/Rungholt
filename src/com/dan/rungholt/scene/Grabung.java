package com.dan.rungholt.scene;

import com.dan.figure.Outfit;
import com.dan.figure.Person;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 13: die Archäologen bei Ebbe (F17 A) [G: Ablauf und Gerät nach den Bildern der Grabungen von 2023 im Watt;
 * Ort, Zahl und Zeiten gesetzt]. Sechs Leute kommen von Südfall über das Watt, sobald die Fundstelle an der Kirche
 * trockengefallen ist und nach der Tide mindestens 1¾ Stunden trocken bleibt; sie legen Rahmen von 1 m im Geviert über
 * die Nordwand des Schiffs, graben mit der Kelle (drei knien an den Quadraten), einer trägt die Eimer zum Sieb, einer
 * siebt, einer misst mit dem Stab; eine Dreiviertelstunde bevor die Flut die Stelle erreicht, gehen sie zurück. Nur bei
 * Tag und nicht im Sturm. Gerät (Rahmen, Eimer, Sieb, Stab, Fluchtstangen) als ein Netz in Weltkoordinaten
 * ({@code com.dan.buoyancy.Mesh}); Fußspuren und Störung wie die Wattwanderung.
 */
public final class Grabung {
    /** Was die Welt der Grabung sagt (die App verdrahtet Tide, Gelände und Sonne). */
    public interface Welt {
        /** Wasser über dem Grund (m) jetzt. */
        double depth(double x, double z);
        /** Grund (m NHN) mit Feinform und Spur. */
        double ground(double x, double z);
        /** Wird die Stelle in dt Stunden ab jetzt nass sein? (Pegel am Rand mit der Laufzeit gegen die Verbindungshöhe) */
        boolean wetIn(double x, double z, double hoursAhead);
        /** Sonnenhöhe (Grad), Wind (m/s). */
        double sunDeg();
        double windSpeed();
    }

    public enum State {
        FORT("nicht im Watt"), ANMARSCH("unterwegs zur Kirche"), ARBEIT("bei der Arbeit"), ABMARSCH("auf dem Rückweg");
        public final String label;
        State(String l) { label = l; }
    }

    public enum Job { KELLE, TRAEGER, SIEB, MESSEN }

    /** Die Mitte der Grabungsfläche, örtlich an der Kirche (längs der Achse u, quer v; v &lt; 0 Norden) [G]. */
    public static final double SITE_U = -2, SITE_V = -6.5;
    /** Rahmen: Spalten längs, Reihen quer (je 1 m). */
    public static final int COLS = 4, ROWS = 3;
    /** Wie lange die Stelle nach der Tide mindestens trocken bleiben muss, damit sie kommen (h), und wann sie gehen. */
    public static final double COME_HOURS = 1.75, LEAVE_HOURS = 0.75;
    /** Gehgeschwindigkeit im Watt (m/s). */
    public static final double SPEED = 1.15;

    public final List<Person> people = new ArrayList<>();
    public final Job[] jobs;
    public State state = State.FORT;
    /** Weg: Start am Rand von Südfall, Ziel an der Grabung. */
    public final double[] base, site;
    /** Plätze je Person bei der Arbeit (x, z, Richtung in Grad). */
    final double[][] place;
    final double[] lag;
    double s, time, carry;
    /** Seit wann sie da sind (Szenenzeit in s), wie oft sie kamen. */
    public double workSeconds;
    public int visits;
    /** Abstand der Stelle zur Kirche (für die Prüfung) und der Weg (m). */
    public final double pathLength;

    public Grabung(int month, long seed) {
        SplittableRandom r = new SplittableRandom(seed);
        double[] sf = Site.suedfall();
        site = local(SITE_U, SITE_V);
        double dx = site[0] - sf[0], dz = site[1] - sf[1], l = Math.hypot(dx, dz);
        base = new double[]{sf[0] + dx / l * 330, sf[1] + dz / l * 330};   // am Rand des Vorlands von Südfall [G]
        pathLength = Math.hypot(site[0] - base[0], site[1] - base[1]);
        jobs = new Job[]{Job.KELLE, Job.KELLE, Job.KELLE, Job.TRAEGER, Job.SIEB, Job.MESSEN};
        place = new double[jobs.length][];
        lag = new double[jobs.length];
        for (int i = 0; i < jobs.length; i++) {
            Person p = new Person(i, Person.Role.ADULT, Outfit.archaeologist(month, r));
            p.scale = 0.92 + 0.14 * r.nextDouble();
            people.add(p);
            lag[i] = i * 2.2 + r.nextDouble();
            double head = Stadt1362.CHURCH_HEADING;
            place[i] = switch (jobs[i]) {
                case KELLE -> { double[] q = local(SITE_U - 1.5 + i * 1.0, SITE_V - 1.9); yield new double[]{q[0], q[1], head + 180 + 10 * (r.nextDouble() - 0.5)}; }
                case TRAEGER -> { double[] q = local(SITE_U + 2.6, SITE_V - 2.6); yield new double[]{q[0], q[1], head}; }
                case SIEB -> { double[] q = sieve(); yield new double[]{q[0] + 0.9, q[1], head - 90}; }
                case MESSEN -> { double[] q = local(SITE_U + 7.5, SITE_V - 3.5); yield new double[]{q[0], q[1], head - 140}; }
            };
        }
    }

    /** Örtlich an der Kirche (u längs der Achse nach Osten, v quer nach Süden) in die Welt. */
    public static double[] local(double u, double v) {
        double h = Math.toRadians(Stadt1362.CHURCH_HEADING), ax = Math.sin(h), az = -Math.cos(h);
        return new double[]{Stadt1362.CHURCH_X + u * ax - v * az, Stadt1362.CHURCH_Z + u * az + v * ax};
    }

    /** Lage des Siebs (nördlich der Rahmen). */
    public static double[] sieve() { return local(SITE_U - 1, SITE_V - 5.2); }

    /** Sind sie im Watt (zu sehen)? */
    public boolean present() { return state != State.FORT; }

    /** Mitte der Gruppe (für Störer) oder null. */
    public double[] centre() {
        if (!present()) return null;
        double x = 0, z = 0;
        for (Person p : people) { x += p.x; z += p.z; }
        return new double[]{x / people.size(), z / people.size()};
    }

    /** Dürfen sie kommen? Tag, kein Sturm, die Stelle trocken und noch lange genug trocken (Weg mitgerechnet). */
    boolean canCome(Welt w) {
        if (w.sunDeg() < 4 || w.windSpeed() > 14) return false;
        if (w.depth(site[0], site[1]) > 0.02 || w.depth(base[0], base[1]) > 0.05) return false;
        for (double h = 0; h <= COME_HOURS + pathLength / SPEED / 3600; h += 1 / 12.0) if (w.wetIn(site[0], site[1], h)) return false;
        return true;
    }

    /** Müssen sie gehen? Die Flut erreicht die Stelle bald (Weg mitgerechnet), oder es wird dunkel, oder Sturm. */
    boolean mustLeave(Welt w) {
        if (w.sunDeg() < 1 || w.windSpeed() > 17) return true;
        double ahead = LEAVE_HOURS + pathLength / SPEED / 3600;
        for (double h = 0; h <= ahead; h += 1 / 12.0) if (w.wetIn(site[0], site[1], h)) return true;
        return false;
    }

    /** dt Sekunden Szenenzeit weiter; steps bekommt Fußspuren (x, z, Richtung). */
    public void step(double dt, Welt w, com.dan.figure.Hike.Steps steps) {
        time += dt;
        switch (state) {
            case FORT -> {
                if (canCome(w)) { state = State.ANMARSCH; s = 0; visits++; }
            }
            case ANMARSCH -> {
                s += SPEED * dt;
                if (s >= pathLength + lag[lag.length - 1] + 2) { state = State.ARBEIT; workSeconds = 0; }
                else if (mustLeave(w)) { state = State.ABMARSCH; s = Math.min(s, pathLength); }
            }
            case ARBEIT -> {
                workSeconds += dt;
                if (mustLeave(w)) { state = State.ABMARSCH; s = pathLength; }
            }
            case ABMARSCH -> {
                s -= SPEED * dt;
                if (s <= -lag[lag.length - 1] - 2) { state = State.FORT; s = 0; }
            }
        }
        pose(dt, w, steps);
    }

    /** Nach einem Sprung der Uhr: gleich in den Zustand, der jetzt passt (arbeiten oder fort). */
    public void settle(Welt w) {
        boolean dry = w.depth(site[0], site[1]) <= 0.02 && w.sunDeg() >= 4 && w.windSpeed() <= 14;
        state = dry && !mustLeave(w) ? State.ARBEIT : State.FORT;
        if (state == State.ARBEIT) { s = pathLength; workSeconds = 600; visits++; }
        pose(0, w, null);
    }

    private void pose(double dt, Welt w, com.dan.figure.Hike.Steps steps) {
        double ux = site[0] - base[0], uz = site[1] - base[1], l = Math.max(1e-6, Math.hypot(ux, uz));
        ux /= l; uz /= l;
        double walkHead = Math.toDegrees(Math.atan2(ux, -uz));
        for (int i = 0; i < people.size(); i++) {
            Person p = people.get(i);
            double ox = p.x, oz = p.z;
            if (state == State.ANMARSCH || state == State.ABMARSCH) {
                // in einer Reihe auf dem Weg, je lag Meter dahinter; die letzten Meter zum Platz
                double d = Math.max(0, Math.min(pathLength, s - lag[i]));
                double side = (i % 2 == 0 ? 1 : -1) * 0.6;
                double x = base[0] + ux * d - uz * side, z = base[1] + uz * d + ux * side;
                boolean moving = state == State.ANMARSCH ? s - lag[i] > 0 && s - lag[i] < pathLength : s - lag[i] > 0 && s - lag[i] < pathLength;
                if (state == State.ANMARSCH && s - lag[i] >= pathLength) {   // zum Platz
                    double t = Math.min(1, (s - lag[i] - pathLength) / 2);
                    x += (place[i][0] - x) * t; z += (place[i][1] - z) * t;
                }
                p.x = x; p.z = z;
                p.heading = state == State.ANMARSCH ? walkHead : (walkHead + 180) % 360;
                p.gait = moving ? 1 : 0;
                p.bend = 0;
                p.point = 0;
                if (moving) p.advance(SPEED * dt);
            } else if (state == State.ARBEIT) {
                double ph = time * 0.9 + i * 1.7;
                switch (jobs[i]) {
                    case KELLE -> { p.x = place[i][0]; p.z = place[i][1]; p.heading = place[i][2]; p.gait = 0; p.bend = 0.86 + 0.07 * Math.sin(ph * 2.3); p.point = 0.12 + 0.12 * Math.max(0, Math.sin(ph * 3.1)); }
                    case SIEB -> { p.x = place[i][0]; p.z = place[i][1]; p.heading = place[i][2]; p.gait = 0; p.bend = 0.32 + 0.05 * Math.sin(ph * 9); p.point = 0.25 + 0.1 * Math.sin(ph * 9); }
                    case MESSEN -> {
                        p.x = place[i][0]; p.z = place[i][1]; p.gait = 0;
                        double c = (time / 40 + i) % 1;   // alle 40 s: zum Stab sehen, zeigen
                        p.heading = place[i][2] + (c < 0.3 ? 0 : 60);
                        p.point = c < 0.3 ? 0.7 : 0; p.bend = 0;
                    }
                    case TRAEGER -> {
                        // zwischen den Quadraten und dem Sieb hin und her (je 25 s), dort kurz bücken
                        double[] a = place[i], b = sieve();
                        double cyc = (time % 50) / 50, t = cyc < 0.4 ? cyc / 0.4 : cyc < 0.5 ? 1 : cyc < 0.9 ? 1 - (cyc - 0.5) / 0.4 : 0;
                        double nx = a[0] + (b[0] - 0.6 - a[0]) * t, nz = a[1] + (b[1] - a[1]) * t;
                        boolean walking = (cyc < 0.4 || (cyc >= 0.5 && cyc < 0.9));
                        if (walking) { p.heading = (Math.toDegrees(Math.atan2(nx - p.x, -(nz - p.z))) + 360) % 360; p.advance(Math.hypot(nx - p.x, nz - p.z)); }
                        p.x = nx; p.z = nz; p.gait = walking ? 0.8 : 0; p.bend = walking ? 0 : 0.6; p.point = 0;
                    }
                }
            }
            p.y = w.ground(p.x, p.z);
            double mv = Math.hypot(p.x - ox, p.z - oz);
            if (steps != null && mv > 1e-3 && p.gait > 0.5) {
                p.wet = w.depth(p.x, p.z);
                // ein Abdruck alle 0,75 m (wie der Wanderer)
                carryStep(i, mv, p, steps);
            }
        }
    }

    private final double[] footAcc = new double[8];

    private void carryStep(int i, double mv, Person p, com.dan.figure.Hike.Steps steps) {
        footAcc[i] += mv;
        while (footAcc[i] >= 0.75) {
            footAcc[i] -= 0.75;
            double h = Math.toRadians(p.heading);
            steps.step(p.x, p.z, Math.sin(h), -Math.cos(h), false, p.scale);
        }
    }

    // ------------------------------------------------------------------ Gerät

    /**
     * Das Gerät als ein Netz in Weltkoordinaten: zwölf Rahmen aus Latten (1 m, 4 cm hoch) mit Schnur, sechs Eimer
     * (schwarz und orange), das Sieb auf vier Beinen, der Messstab mit Antenne auf dem Dreibein, zwei Fluchtstangen
     * rot-weiß, eine Kiste für die Funde [G].
     */
    public static com.dan.buoyancy.Mesh equipment(java.util.function.DoubleBinaryOperator ground) {
        com.dan.buoyancy.Mesh m = new com.dan.buoyancy.Mesh();
        float P = com.dan.buoyancy.Mesh.PAINT;
        double[] wood = {0.30, 0.22, 0.13}, string = {0.75, 0.70, 0.55}, black = {0.02, 0.02, 0.022}, orange = {0.70, 0.20, 0.02};
        double[] red = {0.55, 0.03, 0.02}, white = {0.75, 0.75, 0.73}, steel = {0.35, 0.36, 0.37}, yellow = {0.62, 0.45, 0.02};
        // Rahmen
        for (int c = 0; c <= COLS; c++) {
            double[] a = local(SITE_U - COLS / 2.0 + c, SITE_V - ROWS / 2.0), b = local(SITE_U - COLS / 2.0 + c, SITE_V + ROWS / 2.0);
            m.beam(new double[]{a[0], ground.applyAsDouble(a[0], a[1]), a[1]}, new double[]{b[0], ground.applyAsDouble(b[0], b[1]), b[1]}, 0.045, 0.04, c == 0 || c == COLS ? wood : string, P);
        }
        for (int rr = 0; rr <= ROWS; rr++) {
            double[] a = local(SITE_U - COLS / 2.0, SITE_V - ROWS / 2.0 + rr), b = local(SITE_U + COLS / 2.0, SITE_V - ROWS / 2.0 + rr);
            m.beam(new double[]{a[0], ground.applyAsDouble(a[0], a[1]), a[1]}, new double[]{b[0], ground.applyAsDouble(b[0], b[1]), b[1]}, 0.045, 0.04, rr == 0 || rr == ROWS ? wood : string, P);
        }
        // Eimer
        SplittableRandom r = new SplittableRandom(2023);
        double[][] spots = {{SITE_U - 2.6, SITE_V - 2.2}, {SITE_U - 0.3, SITE_V - 2.4}, {SITE_U + 1.6, SITE_V - 2.3}, {SITE_U - 1.6, SITE_V - 5.0}, {SITE_U - 0.4, SITE_V - 5.9}, {SITE_U + 2.6, SITE_V - 2.9}};
        for (int i = 0; i < spots.length; i++) {
            double[] q = local(spots[i][0] + 0.2 * r.nextDouble(), spots[i][1] + 0.2 * r.nextDouble());
            double g = ground.applyAsDouble(q[0], q[1]);
            double[] col = i % 3 == 1 ? orange : black;
            m.cylinder(q[0], q[1], 0.13, 0.16, g, g + 0.3, 12, new double[][]{col}, P, true);
            m.cylinder(q[0], q[1], 0.15, 0.15, g + 0.04, g + 0.24, 12, new double[][]{{0.09, 0.075, 0.05}}, P, true);   // Inhalt: Sediment
        }
        // Sieb auf vier Beinen (0,8 m hoch, 0,6 × 0,9 m), Gitter dunkel
        double[] sv = sieve();
        double gs = ground.applyAsDouble(sv[0], sv[1]);
        for (int a = -1; a <= 1; a += 2)
            for (int b = -1; b <= 1; b += 2) {
                double[] q = local(SITE_U - 1 + a * 0.4, SITE_V - 5.2 + b * 0.25);
                m.beam(new double[]{q[0], gs - 0.05, q[1]}, new double[]{q[0], gs + 0.8, q[1]}, 0.04, 0.04, wood, P);
            }
        double[] s0 = local(SITE_U - 1.45, SITE_V - 5.2), s1 = local(SITE_U - 0.55, SITE_V - 5.2);
        m.beam(new double[]{s0[0], gs + 0.8, s0[1]}, new double[]{s1[0], gs + 0.8, s1[1]}, 0.6, 0.08, wood, P);
        m.beam(new double[]{s0[0], gs + 0.885, s0[1]}, new double[]{s1[0], gs + 0.885, s1[1]}, 0.52, 0.005, steel, P);
        // Messstab mit GNSS-Antenne auf dem Dreibein
        double[] st = local(SITE_U + 6.5, SITE_V - 3.0);
        double gt = ground.applyAsDouble(st[0], st[1]);
        for (int k = 0; k < 3; k++) {
            double a = k * 2 * Math.PI / 3;
            m.beam(new double[]{st[0] + Math.cos(a) * 0.55, gt, st[1] + Math.sin(a) * 0.55}, new double[]{st[0], gt + 1.4, st[1]}, 0.025, 0.025, steel, P);
        }
        m.cylinder(st[0], st[1], 0.015, 0.015, gt, gt + 1.9, 6, new double[][]{yellow}, P, false);
        m.cylinder(st[0], st[1], 0.09, 0.09, gt + 1.9, gt + 1.97, 12, new double[][]{white}, P, true);
        // Fluchtstangen rot-weiß, je 2 m in 0,5-m-Abschnitten
        for (double[] fs : new double[][]{{SITE_U - COLS / 2.0 - 1.2, SITE_V - ROWS / 2.0}, {SITE_U + COLS / 2.0 + 1.2, SITE_V + ROWS / 2.0}}) {
            double[] q = local(fs[0], fs[1]);
            double g = ground.applyAsDouble(q[0], q[1]);
            for (int k = 0; k < 4; k++) m.cylinder(q[0], q[1], 0.016, 0.016, g + k * 0.5, g + (k + 1) * 0.5, 6, new double[][]{k % 2 == 0 ? red : white}, P, k == 3);
        }
        // Fundkiste
        double[] kq = local(SITE_U + 3.2, SITE_V - 1.0);
        double gk = ground.applyAsDouble(kq[0], kq[1]);
        m.box(kq[0], gk + 0.15, kq[1], 0.3, 0.15, 0.2, new double[]{0.12, 0.25, 0.45}, P);
        return m;
    }

    public String describe() {
        return String.format(Locale.GERMAN, "Grabung an der Kirche: %d Leute %s%s", people.size(), state.label,
                state == State.ARBEIT ? String.format(Locale.GERMAN, " seit %.0f min", workSeconds / 60) : "");
    }
}
