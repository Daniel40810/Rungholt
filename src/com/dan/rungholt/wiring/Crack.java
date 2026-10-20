package com.dan.rungholt.wiring;

/**
 * Ein Riss, der läuft. Gibt: {@code fracture.onCrack} (Phase 12: der Deich aus Klei in der Mandränke). Nimmt:
 * soundscape (das Brechen im Orkan), die App (die Bresche wird sichtbar).
 */
@FunctionalInterface
public interface Crack {
    /** Weg des Risses als Punkte {x, y, z, x, y, z, …} (m), freigesetzte Energie (J), ob das Stück damit frei ist. */
    void crack(double[] path, double energyJ, boolean broken);
}
