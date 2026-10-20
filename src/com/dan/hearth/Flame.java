package com.dan.hearth;

/**
 * Ein Flammenstrahler für die Grafik: Fuß der Flamme, Breite und Höhe (m), Stärke 0..1, Zufall, Art (bestimmt Zahl
 * der Zungen, Funken und Ruß). Ein brennendes Haus sind viele davon ({@link Blaze#flames}).
 */
public record Flame(double x, double y, double z, double width, double height, double intensity, int seed, int kind) {
    /** Arten: offene Feuerstelle, Fackel, Esse (viele Funken), Hausbrand (Ruß, viele Funken), Glut (nur Leuchten). */
    public static final int CAMPFIRE = 0, TORCH = 1, FORGE = 2, BLAZE = 3, EMBER = 4;
}
