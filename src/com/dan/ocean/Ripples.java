package com.dan.ocean;

import java.util.ArrayList;
import java.util.List;

/**
 * Ringwellen nach einem Einschlag ({@link Ocean#addImpulse}): ein Wellenpaket läuft vom Einschlagort nach außen,
 * wird breiter und klingt in rund vier Sekunden ab. Für einzelne große Ereignisse (zurückfallende Gischt, ein
 * Brecher am Sockel, ein Schiff); Regen bringt in Phase 7 seine eigenen, billigeren Ringe mit.
 */
public final class Ripples {
    public static final int MAX = 32;
    public static final double LIFE = 4.0, SPEED = 0.9, WAVELENGTH = 0.35;

    /** Ein Einschlag: Ort, Zeit, Stärke 0..1. */
    public record Impulse(double x, double z, double t0, double strength) { }

    private final List<Impulse> list = new ArrayList<>();

    synchronized void add(double x, double z, double t, double strength) {
        if (list.size() >= MAX) list.remove(0);
        list.add(new Impulse(x, z, t, Math.max(0, Math.min(1, strength))));
    }

    synchronized void expire(double t) { list.removeIf(i -> t - i.t0 > LIFE || t < i.t0); }

    public synchronized List<Impulse> snapshot() { return new ArrayList<>(list); }

    /** Höhe eines Rings im Abstand r nach τ Sekunden (dieselbe Formel im Shader). */
    public static double ring(double r, double tau, double strength) {
        if (tau < 0 || tau > LIFE) return 0;
        double front = r - SPEED * tau, w = 0.15 + 0.12 * tau;
        double env = Math.exp(-front * front / (w * w)) * Math.exp(-tau / 1.4) / (1 + r * 1.5);
        return strength * 0.06 * env * Math.cos(2 * Math.PI * front / WAVELENGTH);
    }

    synchronized double height(double x, double z, double t) {
        double h = 0;
        for (Impulse i : list) h += ring(Math.hypot(x - i.x, z - i.z), t - i.t0, i.strength);
        return h;
    }
}
