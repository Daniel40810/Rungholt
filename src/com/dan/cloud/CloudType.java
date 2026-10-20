package com.dan.cloud;

/**
 * Die Wolkenarten der unteren Schicht mit ihren Maßen. Höhen in m über dem Meer, typische Werte für die Deutsche
 * Bucht (Wolkenatlas der WMO, Basis über See eher niedrig). {@code strat} mischt zwischen haufenförmig (0) und
 * schichtförmig (1); {@code cell} ist die Größe der Zellen auf der Wetterkarte in m, {@code sigma} die Auslöschung
 * je m im dichtesten Kern.
 */
public enum CloudType {
    /** Schönwetter-Cumulus (humilis bis mediocris): einzelne Haufen, flache Basis, blumenkohlartige Kuppen. */
    CUMULUS("Cumulus", 900, 900, 1600, 0.0, 1800, 0.045, 0.0, 0.85),
    /** Stratocumulus: Schollen und Walzen, oft geschlossen, graue Unterseiten. */
    STRATOCUMULUS("Stratocumulus", 700, 500, 800, 0.55, 3200, 0.05, 0.0, 0.5),
    /** Stratus: tiefe, fast gleichförmige graue Schicht. */
    STRATUS("Stratus", 300, 350, 450, 0.95, 6000, 0.06, 0.0, 0.35),
    /** Nimbostratus: dicke dunkle Regenschicht. */
    NIMBOSTRATUS("Nimbostratus", 600, 2200, 2800, 0.85, 7000, 0.07, 0.0, 0.18),
    /** Cumulonimbus: Gewittertürme bis an die Tropopause, oben der Amboss. */
    CUMULONIMBUS("Cumulonimbus", 700, 2500, 10_500, 0.1, 9000, 0.06, 1.0, 0.15);

    public final String label;
    /** Untergrenze, gewöhnliche Dicke, größte Dicke (in den Türmen) in m. */
    public final double base, thickness, maxThickness;
    /** 0 haufenförmig, 1 schichtförmig. */
    public final double strat;
    /** Zellgröße auf der Wetterkarte in m. */
    public final double cell;
    /** Auslöschung je m im Kern. */
    public final double sigma;
    /** Amboss 0..1. */
    public final double anvil;
    /** Helligkeit eines ganz bedeckten Himmels dieser Art gegen den klaren (für die Belichtung), grob nach Messungen [G]. */
    public final double overcastBrightness;

    CloudType(String label, double base, double thickness, double maxThickness, double strat, double cell, double sigma,
              double anvil, double overcastBrightness) {
        this.label = label;
        this.base = base;
        this.thickness = thickness;
        this.maxThickness = maxThickness;
        this.strat = strat;
        this.cell = cell;
        this.sigma = sigma;
        this.anvil = anvil;
        this.overcastBrightness = overcastBrightness;
    }

    /** Höchste Obergrenze dieser Art in m. */
    public double top() { return base + maxThickness; }

    @Override public String toString() { return label; }
}
