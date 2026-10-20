package com.dan.figure;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Eine geführte Wanderung: ein Führer geht einen Weg (Linienzug) mit gleichmäßigem Schritt, die Gruppe folgt locker in
 * einer Kolonne, Kinder schlendern zur Seite und laufen voraus. An Halten bleibt der Führer stehen, dreht sich zur
 * Gruppe, bückt sich (zeigt einen Wattwurm) und zeigt dann in die Weite; die Gruppe stellt sich im Halbkreis vor ihm
 * auf. Fällt jemand zurück, wartet er. Steht vor ihm Wasser, das tiefer ist als {@link #maxWade}, bleibt die Gruppe
 * stehen ({@link State#WARTEN}). Je Schritt meldet die Gruppe eine Fußspur ({@link Steps}); ihre Orte sind Störer für
 * Tiere ({@link #count}, {@link #at}).
 * <p>
 * Werte [G]: Führer 1,15 m/s auf Sand, im Schlick bis 40 % langsamer; Abstand in der Kolonne 1,5 bis 2,5 m je Person;
 * wartet, wenn der Letzte mehr als 45 m zurück ist.
 */
public final class Hike {
    /** Grund, Wasser und Schlick am Ort. */
    public interface World {
        double ground(double x, double z);
        double depth(double x, double z);
        /** Schlick 0..1 (macht langsamer). */
        default double mud(double x, double z) { return 0; }
    }

    /** Eine Fußspur: Ort, Richtung, barfuß, Größe der Person. */
    @FunctionalInterface public interface Steps { void step(double x, double z, double dirX, double dirZ, boolean barefoot, double scale); }

    /** Ein Halt am Weg: Strecke ab Start (m), Dauer (s), was es dort gibt. */
    public record Stop(double at, double seconds, String label) { }

    public enum State {
        GEHEN("geht"), HALT("hält"), WARTEN("wartet"), ANGEKOMMEN("angekommen");
        public final String label;
        State(String l) { label = l; }
    }

    public final double[] rx, rz, rs;
    public final List<Stop> stops = new ArrayList<>();
    private final List<Person> people = new ArrayList<>();
    public final Person guide;
    public double guideSpeed = 1.15, maxWade = 0.3;
    /** Strecke des Führers ab Start (m), Zeit (s), Zustand. */
    public double s, time;
    public State state = State.GEHEN;
    private int nextStop;
    private double stopTime, stopLen, waitTime;
    private Stop current;
    private final SplittableRandom rng;
    /** Zähler: Schritte, Halte, Wartezeit (s), höchste Wassertiefe an einem Fuß (m). */
    public int steps, halts;
    public double waited, wettest;

    /**
     * Eine Gruppe auf dem Weg route ({x, z}-Paare): Führer plus walkers Personen, davon etwa ein Viertel Kinder,
     * Kleidung nach dem Monat.
     */
    public Hike(double[][] route, int walkers, int month, long seed) {
        if (route.length < 2) throw new IllegalArgumentException("Weg mit weniger als zwei Punkten");
        rx = new double[route.length]; rz = new double[route.length]; rs = new double[route.length];
        for (int i = 0; i < route.length; i++) {
            rx[i] = route[i][0]; rz[i] = route[i][1];
            if (i > 0) rs[i] = rs[i - 1] + Math.hypot(rx[i] - rx[i - 1], rz[i] - rz[i - 1]);
        }
        rng = new SplittableRandom(seed);
        guide = new Person(0, Person.Role.GUIDE, Outfit.forMonth(month, rng, true, false));
        guide.scale = 1.0 + 0.04 * rng.nextGaussian();
        people.add(guide);
        for (int i = 1; i <= walkers; i++) {
            boolean child = i % 4 == 3;
            Person p = new Person(i, child ? Person.Role.CHILD : Person.Role.ADULT, Outfit.forMonth(month, rng, false, child));
            p.scale = child ? 0.62 + 0.16 * rng.nextDouble() : Math.max(0.9, Math.min(1.1, 0.98 + 0.045 * rng.nextGaussian()));
            p.lag = 3 + i * (1.5 + rng.nextDouble()) + (child ? -2 * rng.nextDouble() : 0);
            p.side = p.sideGoal = (rng.nextDouble() - 0.5) * (child ? 6 : 2.5);
            p.wander = rng.nextDouble() * 100;
            people.add(p);
        }
        place();
    }

    public List<Person> people() { return Collections.unmodifiableList(people); }
    public double length() { return rs[rs.length - 1]; }

    /** Alle an den Anfang des Wegs (in der Kolonne). */
    public void place() {
        s = 0; time = 0; state = State.GEHEN; nextStop = 0; current = null;
        for (Person p : people) {
            double[] q = at(Math.max(0, s - p.lag), p.side);
            p.x = q[0]; p.z = q[1];
            p.heading = Math.atan2(q[2], -q[3]);
            p.speed = p.gait = p.bend = p.point = 0;
        }
    }

    /** Punkt auf dem Weg bei Strecke d, um side m zur rechten Seite versetzt: {x, z, Richtung x, Richtung z}. */
    public double[] at(double d, double side) {
        d = Math.max(0, Math.min(length(), d));
        int i = 1;
        while (i < rs.length - 1 && rs[i] < d) i++;
        double seg = Math.max(1e-9, rs[i] - rs[i - 1]), t = (d - rs[i - 1]) / seg;
        double dx = (rx[i] - rx[i - 1]) / seg, dz = (rz[i] - rz[i - 1]) / seg;
        double x = rx[i - 1] + (rx[i] - rx[i - 1]) * t, z = rz[i - 1] + (rz[i] - rz[i - 1]) * t;
        // rechts von der Richtung (dx, dz) bei Nord = −z: (−dz, dx)
        return new double[]{x - dz * side, z + dx * side, dx, dz};
    }

    /** Szenenzeit dt (s) weiter. */
    public void step(double dt, World w, Steps onStep) {
        if (dt <= 0) return;
        double rest = dt;
        while (rest > 1e-9) {
            double h = Math.min(rest, 0.25);
            rest -= h;
            stepOnce(h, w, onStep);
        }
    }

    private void stepOnce(double dt, World w, Steps onStep) {
        time += dt;
        double[] g = at(s, 0);
        double vG = 0;
        // ---- der Führer
        switch (state) {
            case GEHEN -> {
                double behind = 0;
                for (Person p : people) if (p != guide) behind = Math.max(behind, Math.hypot(p.x - guide.x, p.z - guide.z) - p.lag);
                double[] ahead = at(s + 20, 0);
                if (w.depth(ahead[0], ahead[1]) > maxWade) { state = State.WARTEN; break; }
                if (behind > 45) { waited += dt; break; }   // auf die Letzten warten
                if (nextStop < stops.size() && s >= stops.get(nextStop).at()) {
                    current = stops.get(nextStop++);
                    state = State.HALT; stopTime = 0; stopLen = current.seconds(); halts++;
                    break;
                }
                if (s >= length()) { state = State.ANGEKOMMEN; break; }
                vG = guideSpeed * (1 - 0.4 * Math.max(0, Math.min(1, w.mud(guide.x, guide.z))));
                s = Math.min(length(), s + vG * dt);
            }
            case HALT -> {
                stopTime += dt;
                if (stopTime >= stopLen) { state = State.GEHEN; current = null; }
            }
            case WARTEN -> {
                waited += dt;
                double[] ahead = at(s + 20, 0);
                if (w.depth(ahead[0], ahead[1]) < maxWade * 0.6) state = State.GEHEN;
            }
            case ANGEKOMMEN -> { }
        }
        g = at(s, 0);
        boolean gathered = state == State.HALT || state == State.ANGEKOMMEN || state == State.WARTEN;
        double backX = -g[2], backZ = -g[3];
        // Führer: Ort, Haltung
        moveTo(guide, g[0], g[1], dt, vG, 1.6, w, onStep);
        if (gathered) {
            turnTo(guide, Math.atan2(backX, -backZ), dt);
            double f = stopLen > 0 ? stopTime / stopLen : 0;
            guide.bend = approach(guide.bend, state == State.HALT && stopTime > 4 && f < 0.45 ? 1 : 0, dt * 0.8);
            guide.point = approach(guide.point, state == State.HALT && f > 0.55 && f < 0.85 ? 1 : 0, dt * 1.2);
        } else { guide.bend = approach(guide.bend, 0, dt); guide.point = approach(guide.point, 0, dt * 2); }
        // ---- die Gruppe
        int k = 0, n = people.size() - 1;
        for (Person p : people) {
            if (p == guide) continue;
            p.wander += dt;
            double tx, tz;
            if (gathered) {
                // Halbkreis vor dem Führer, zwei Reihen
                int row = k % 2 == 0 ? 0 : 1;
                double ang = ((k + 0.5) / Math.max(1, n) - 0.5) * Math.toRadians(150);
                double r = 4.2 + 1.6 * row + (p.role == Person.Role.CHILD ? -1.2 : 0);
                double cx = Math.cos(ang), sx = Math.sin(ang);
                double dx = backX * cx - backZ * sx, dz = backX * sx + backZ * cx;
                tx = guide.x + dx * r; tz = guide.z + dz * r;
            } else {
                if (rng.nextDouble() < dt / (p.role == Person.Role.CHILD ? 8 : 40)) p.sideGoal = (rng.nextDouble() - 0.5) * (p.role == Person.Role.CHILD ? 9 : 3);
                p.side += (p.sideGoal - p.side) * Math.min(1, dt * 0.3);
                double lag = p.lag;
                if (p.role == Person.Role.CHILD) {
                    if (p.sprint <= 0 && rng.nextDouble() < dt / 60) p.sprint = 15 + 15 * rng.nextDouble();
                    if (p.sprint > 0) { p.sprint -= dt; lag = 1.5; }
                }
                double[] q = at(s - lag, p.side);
                tx = q[0]; tz = q[1];
            }
            moveTo(p, tx, tz, dt, gathered ? 0 : vG, p.role == Person.Role.CHILD ? 2.4 : 1.7, w, onStep);
            if (p.speed < 0.15) {
                double fx = gathered ? guide.x - p.x : g[2], fz = gathered ? guide.z - p.z : g[3];
                turnTo(p, Math.atan2(fx, -fz), dt);
            }
            p.look = 0.5 * Math.sin(p.wander * 0.13 + p.id) * (gathered ? 0.4 : 1);
            p.bend = approach(p.bend, gathered && p.role == Person.Role.CHILD && Math.sin(p.wander * 0.21 + p.id * 1.7) > 0.6 ? 1 : 0, dt * 0.8);
            k++;
        }
        // auseinanderhalten (0,8 m)
        for (int i = 0; i < people.size(); i++)
            for (int j = i + 1; j < people.size(); j++) {
                Person a = people.get(i), b = people.get(j);
                double dx = b.x - a.x, dz = b.z - a.z, d = Math.hypot(dx, dz);
                if (d < 0.8 && d > 1e-6) {
                    double push = (0.8 - d) * 0.5;
                    if (a == guide) { b.x += dx / d * push * 2; b.z += dz / d * push * 2; }
                    else { a.x -= dx / d * push; a.z -= dz / d * push; b.x += dx / d * push; b.z += dz / d * push; }
                }
            }
        for (Person p : people) {
            p.y = w.ground(p.x, p.z);
            p.wet = w.depth(p.x, p.z);
            wettest = Math.max(wettest, p.wet);
        }
    }

    private void moveTo(Person p, double tx, double tz, double dt, double feed, double vmax, World w, Steps onStep) {
        double dx = tx - p.x, dz = tz - p.z, d = Math.hypot(dx, dz);
        double want = d < 0.25 ? 0 : Math.min(vmax, 0.45 * d + feed * 0.95);
        want *= 1 - 0.3 * Math.max(0, Math.min(1, w.mud(p.x, p.z)));
        p.speed += (want - p.speed) * Math.min(1, dt * 2.5);
        double ds = Math.min(d, p.speed * dt);
        if (d > 1e-6 && ds > 0) {
            p.vx = dx / d * p.speed; p.vz = dz / d * p.speed;
            p.x += dx / d * ds; p.z += dz / d * ds;
            if (p.speed > 0.15) turnTo(p, Math.atan2(dx, -dz), dt * 1.5);
        } else { p.vx = p.vz = 0; }
        p.gait = Math.min(1.4, p.speed / (1.25 * p.scale));
        double before = p.phase;
        p.advance(ds);
        // ein Fuß setzt auf, wenn die Phase 0 oder 0,5 überschreitet
        int foot = -1;
        if (before > p.phase) foot = 0; else if (before < 0.5 && p.phase >= 0.5) foot = 1;
        if (foot >= 0 && ds > 0) {
            steps++;
            double hx = Math.sin(p.heading), hz = -Math.cos(p.heading), off = (foot == 0 ? -0.1 : 0.1) * p.scale;
            if (onStep != null && w.depth(p.x, p.z) < 0.03) onStep.step(p.x + (-hz) * off, p.z + hx * off, hx, hz, p.outfit.barefoot, p.scale);
        }
    }

    private static void turnTo(Person p, double target, double k) {
        double d = Math.IEEEremainder(target - p.heading, 2 * Math.PI);
        p.heading += d * Math.min(1, k * 3);
    }

    private static double approach(double v, double goal, double rate) { return v + Math.max(-rate, Math.min(rate, goal - v)); }

    // ------------------------------------------------------------------ Ausgabe

    /** Zahl der Personen (Störer für Tiere). */
    public int count() { return people.size(); }

    /** {x, z, Stärke} der Person i. */
    public double[] at(int i) { Person p = people.get(i); return new double[]{p.x, p.z, 1}; }

    /** Mitte der Gruppe. */
    public double[] centre() {
        double x = 0, z = 0;
        for (Person p : people) { x += p.x; z += p.z; }
        return new double[]{x / people.size(), z / people.size()};
    }

    /** Größte Ausdehnung der Gruppe (m). */
    public double spread() {
        double m = 0;
        for (Person a : people) for (Person b : people) m = Math.max(m, Math.hypot(a.x - b.x, a.z - b.z));
        return m;
    }

    /** Wie lange der laufende Halt schon dauert (s). */
    public double stopElapsed() { return current == null ? 0 : stopTime; }

    /** Der laufende Halt (oder null). */
    public Stop currentStop() { return current; }

    /** „Wattwanderung 14 Personen · 3,2 von 7,0 km · hält: Wattwurm · 1:12 h“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Wattwanderung %d Personen · %.1f von %.1f km · %s%s · %d:%02d h", people.size(), s / 1000, length() / 1000,
                state.label, current != null ? ": " + current.label() : "", (int) (time / 3600), (int) (time / 60) % 60);
    }
}
