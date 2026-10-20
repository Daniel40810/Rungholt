package com.dan.smoke;

/**
 * Eine Rauchquelle: Austritt (Rauchloch, Firstöffnung, offene Glut) in m, Wärmeleistung in kW, Rauchmenge 0..1 und
 * Schwärze 0..1 (0 weißer Holzrauch, 1 schwarzer Rauch aus Reet und Teer), Zufall für das Zerfasern.
 *
 * @param x       Austritt Ost (m)
 * @param y       Austritt Höhe (m)
 * @param z       Austritt Süd (m), Norden = −z
 * @param heatKw  Wärmeleistung (kW): Herdfeuer 6 bis 15, Schmiede 30, brennendes Haus einige tausend
 * @param amount  Rauchmenge 0..1 (0 = aus)
 * @param black   Schwärze 0..1
 * @param seed    Zufall
 */
public record Source(double x, double y, double z, double heatKw, double amount, double black, int seed) {
    public Source {
        if (!(heatKw >= 0)) throw new IllegalArgumentException("smoke: Wärmeleistung muss ≥ 0 sein: " + heatKw);
        amount = Math.max(0, Math.min(1, amount));
        black = Math.max(0, Math.min(1, black));
    }

    /** Herdfeuer unter dem Rauchloch eines Hauses (10 kW, weißgrau). */
    public static Source hearth(double x, double y, double z, int seed) { return new Source(x, y, z, 10, 0.8, 0.08, seed); }

    /** Auftriebsfluss F nach Briggs (m⁴/s³): g · Q / (π · c<sub>p</sub> · ρ · T), Luft bei 15 °C. */
    public double buoyancyFlux() { return Plume.buoyancyFlux(heatKw); }
}
