package com.dan.rungholt.wiring;

/**
 * Liegeplätze der Seehunde (neu in Rungholt). Gibt: die App (Bankflächen bei Südfall [G], Phase 7). Nimmt: seal.
 */
public interface Haulout {
    /** Zahl der Bänke. */
    int count();

    /** Bank i: Mitte x, z (m), Halbmesser (m), Höhe über NHN (m). */
    double[] bank(int i);
}
