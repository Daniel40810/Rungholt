package com.dan.rungholt.wiring;

/**
 * Wasser je Zelle (neu in Rungholt). Gibt: {@code tide} (Phase 4: Überflutungskarte mit Laufzeit, Pfützen). Nimmt:
 * ocean (Wasser als Textur statt eines Spiegels), mudflat, seal, flock, muster (Gehen im Schlick), weathering.
 */
public interface Water {
    /** Wassertiefe am Ort (m, 0 = trocken). */
    double depthAt(double x, double z);

    /** Wasserspiegel am Ort (m über NHN); ohne Wasser der Grund. */
    double surfaceAt(double x, double z);

    /** Ob die Zelle mit dem offenen Meer verbunden ist (sonst Pfütze). */
    default boolean connected(double x, double z) { return depthAt(x, z) > 0; }
}
