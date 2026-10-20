package com.dan.rungholt.wiring;

/**
 * Das Gelände: Höhe am Ort über NHN. Gibt: das Gelände heute aus den Höhendaten (Phase 2), das Gelände 1362
 * modelliert [G] (Phase 10); die Ära blendet zwischen beiden. Nimmt: tide (Überflutungskarte), mudflat, ground, reed,
 * forest, road, timber, Kamera.
 */
public interface Terrain {
    /** Höhe des Bodens (Watt, Priel, Hallig, Deich) in m über NHN am Ort (x, z). */
    double height(double x, double z);

    /** Ob am Ort Wasser steht und wie tief es ist (m, 0 = trocken). */
    double waterDepth(double x, double z);

    /** Ob am Ort das Meer hinkommt (Watt, Priel, Vorland); hinter den Deichen (Köge) nicht. Ohne Angabe: überall. */
    default boolean seaward(double x, double z) { return true; }

    /** Neigung des Bodens als Normale (Einheitsvektor). */
    default double[] normal(double x, double z) {
        double e = 1, hx = height(x + e, z) - height(x - e, z), hz = height(x, z + e) - height(x, z - e);
        double nx = -hx / (2 * e), nz = -hz / (2 * e), l = Math.sqrt(nx * nx + 1 + nz * nz);
        return new double[]{nx / l, 1 / l, nz / l};
    }
}
