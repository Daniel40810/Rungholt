package com.dan.rungholt.wiring;

/**
 * Störer (neu in Rungholt): Figuren und Boote, vor denen Seehunde und Vögel fliehen. Gibt: muster (Wattwanderer,
 * Archäologen), buoyancy (Boot). Nimmt: seal (Fluchtdistanz 300 m [G]), flock.
 */
public interface Disturber {
    /** Zahl der Störer. */
    int count();

    /** Störer i: x, z (m) und Stärke 0..1 (ein Boot mehr als eine Figur). */
    double[] at(int i);
}
