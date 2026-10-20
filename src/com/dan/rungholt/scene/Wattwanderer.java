package com.dan.rungholt.scene;

import com.dan.rungholt.wiring.Disturber;

/**
 * Ein Wattwanderer (Phase 7): eine Figur, die bei Ebbe über das trockene Watt auf die Seehundbank zugeht, bis 25 m
 * vor die Stelle, an der die Tiere lagen, oder bis vor das Wasser, und dann umkehrt. Sie stört die Seehunde
 * ({@link Disturber}, Fluchtdistanz 300 m [G]) und hinterlässt Stiefelabdrücke. Die Figuren aus einem eigenen Package
 * kommen seit Phase 9 aus {@code com.dan.figure}: der Renderer zeichnet ihn als {@code Person} mit der Kleidung {@code Outfit.walker()}.
 */
public final class Wattwanderer implements Disturber {
    /** Grund und Wasser am Ort (m). */
    public interface Ground {
        double depth(double x, double z);
    }

    /** Eine Fußspur: Ort und Richtung. */
    @FunctionalInterface public interface Step { void step(double x, double z, double dirX, double dirZ); }

    public volatile boolean active;
    /** Lage (m), Richtung (Azimut, rad), Schrittphase (0..1 je Doppelschritt). */
    public volatile double x, z, heading, phase;
    /** Gehgeschwindigkeit im Watt (m/s) [G]. */
    public double speed = 1.3;
    private double sx, sz, tx, tz, acc;
    private boolean back;
    /** Abstand der Schritte (m). */
    public static final double STRIDE = 0.75;

    /** Losgehen von (fx, fz) zum Ziel (tx, tz). */
    public void start(double fx, double fz, double toX, double toZ) {
        x = sx = fx;
        z = sz = fz;
        tx = toX;
        tz = toZ;
        heading = Math.atan2(toX - fx, -(toZ - fz));
        back = false;
        active = true;
    }

    /**
     * Ein Start auf dem Trockenen, bis {@code dist} m vom Ziel: von den 24 Richtungen die mit dem längsten trockenen Weg
     * (in 10-m-Schritten geprüft), bevorzugt Westen und Norden, wo der Rücken der Bank liegt.
     */
    public boolean startToward(double toX, double toZ, double dist, Ground g) {
        double best = 0, bx = 0, bz = 0;
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI / 12, cx = Math.sin(a), cz = -Math.cos(a), len = 0;
            for (double r = 10; r <= dist; r += 10) {
                if (g.depth(toX + cx * r, toZ + cz * r) > 0.05) break;
                len = r;
            }
            double score = len + (cx < 0 ? 30 : 0) + (cz < 0 ? 20 : 0);
            if (score > best) { best = score; bx = toX + cx * len; bz = toZ + cz * len; }
        }
        if (best < 150) return false;
        start(bx, bz, toX, toZ);
        return true;
    }

    /** Zurückgehen (Bedienfeld). */
    public void turnBack() { back = true; }

    /** Szenenzeit dt (s) weiter; kehrt um vor dem Ziel oder vor Wasser, ist am Start wieder fort. */
    public void step(double dt, Ground g, Step onStep) {
        if (!active || dt <= 0) return;
        double gx = back ? sx : tx, gz = back ? sz : tz, dx = gx - x, dz = gz - z, d = Math.hypot(dx, dz);
        if (!back && d < 25) { back = true; return; }
        if (back && d < 1) { active = false; return; }
        double ux = dx / d, uz = dz / d, s = Math.min(d, speed * dt);
        if (!back && g.depth(x + ux * 6, z + uz * 6) > 0.15) { back = true; return; }   // nicht ins Wasser
        x += ux * s;
        z += uz * s;
        heading = Math.atan2(ux, -uz);
        phase = (phase + s / (2 * STRIDE)) % 1.0;
        acc += s;
        while (acc >= STRIDE) {
            acc -= STRIDE;
            if (onStep != null && g.depth(x, z) < 0.03) onStep.step(x, z, ux, uz);
        }
    }

    /** „Wattwanderer 420 m vor der Bank, geht hin“ */
    public String describe(double bx, double bz) {
        if (!active) return "Wattwanderer: keiner unterwegs";
        return String.format(java.util.Locale.GERMAN, "Wattwanderer %.0f m von der Bank, %s", Math.hypot(x - bx, z - bz), back ? "geht zurück" : "geht hin");
    }

    @Override public int count() { return active ? 1 : 0; }

    @Override public double[] at(int i) { return new double[]{x, z, 1}; }
}
