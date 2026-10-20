package com.dan.fracture;

import java.util.Locale;

/**
 * Der Stoff eines Körpers: Dichte, Steifigkeit, Festigkeit und wie er stößt. {@code fracture} kennt kein Eis und keinen
 * Fels; die Presets sind nur Zahlen. Die Festigkeit gilt je Zellfläche: Zug {@link #tensile}, Druck {@link #compressive}
 * und dazwischen Schub nach Mohr-Coulomb mit dem Reibungswinkel {@link #frictionAngle} – die Kohäsion folgt daraus so,
 * dass ein Pfeiler unter reinem Druck bei {@link #compressive} bricht.
 */
public final class Material {
    public String name = "Stoff";
    /** Dichte (kg/m³) und Elastizitätsmodul (Pa; ändert die Verformung, nicht die Spannung). */
    public double density = 900, young = 9.0e9;
    /** Zug- und Druckfestigkeit (Pa) und innerer Reibungswinkel (Grad). */
    public double tensile = 1.0e6, compressive = 5.0e6, frictionAngle = 30;
    /** Streuung der Festigkeit von Fläche zu Fläche (0 = gleich, 0,2 = ±20 %). */
    public double scatter = 0.2;
    /** Reibung und Stoßzahl beim Stoß mit Boden, Wand und anderen Stücken. */
    public double friction = 0.5, restitution = 0.1;

    /** Kohäsion (Pa) aus Druckfestigkeit und Reibungswinkel: c = σ_c (1 − sin φ) / (2 cos φ). */
    public double cohesion() {
        double p = Math.toRadians(frictionAngle);
        return compressive * (1 - Math.sin(p)) / (2 * Math.cos(p));
    }

    /** Reibungszahl der Coulomb-Geraden tan φ. */
    public double mu() { return Math.tan(Math.toRadians(frictionAngle)); }

    public Material copy() {
        Material m = new Material();
        m.name = name; m.density = density; m.young = young; m.tensile = tensile; m.compressive = compressive;
        m.frictionAngle = frictionAngle; m.scatter = scatter; m.friction = friction; m.restitution = restitution;
        return m;
    }

    /**
     * Reines Eis im Labor: 917 kg/m³, E 9 GPa, Zug 1,0 MPa, Druck 5 MPa, Reibungswinkel 30° [G]. Nachgesehen
     * (Wikipedia „Ice“, Kennwerte): Zug 5 bis 18 kgf/cm² (0,5 bis 1,8 MPa), Druck 24 bis 60 kgf/cm² (2,4 bis 5,9 MPa);
     * beides hängt stark von Temperatur, Korngröße und Dehnrate ab (Übersicht: Petrovic 2003, „Review: Mechanical
     * properties of ice and snow“).
     */
    public static Material ice() {
        Material m = new Material();
        m.name = "Eis";
        m.density = 917;
        m.young = 9.0e9;
        m.tensile = 1.0e6;
        m.compressive = 5.0e6;
        m.frictionAngle = 30;
        m.friction = 0.15;
        m.restitution = 0.05;
        return m;
    }

    /**
     * Gletschereis im Großen [G]: 900 kg/m³ mit Blasen; Zug 400 kPa, Druck 2,5 MPa. Eine 60 bis 100 m hohe Wand trägt
     * unter ihrem Eigengewicht oben hinter der Kante Zug in der Größenordnung von 0,2 bis 0,25 · ρ·g·H (200 bis 250 kPa
     * bei 95 m Eis): Mit weniger Festigkeit stünde keine Front. Schwächer sind die Spalten (die Kalbung legt sie als
     * Schwächen hinein). Phase 6 nahm 150 kPa; Befund Phase 7: damit zerfiel jeder Abschnitt der Front sofort.
     */
    public static Material glacierIce() {
        Material m = ice();
        m.name = "Gletschereis";
        m.density = 900;
        m.tensile = 4.0e5;
        m.compressive = 2.5e6;
        m.scatter = 0.25;
        return m;
    }

    /** Fels (Granit) für einen Felssturz: 2 700 kg/m³, E 50 GPa, Zug 10 MPa, Druck 150 MPa, φ 45° [G]. */
    public static Material rock() {
        Material m = new Material();
        m.name = "Fels";
        m.density = 2700;
        m.young = 5.0e10;
        m.tensile = 1.0e7;
        m.compressive = 1.5e8;
        m.frictionAngle = 45;
        m.friction = 0.6;
        m.restitution = 0.2;
        return m;
    }

    /**
     * Seit Rungholt Phase 12: Klei eines mittelalterlichen Seedeichs, nass [G]: 1 900 kg/m³, E 30 MPa, Zug 15 kPa, Druck
     * 150 kPa, φ 25° (steifer, toniger Marschboden; die Werte liegen im Bereich undrainierter Scherfestigkeiten von
     * 20 bis 75 kPa, der Zug als kleiner Teil davon). Schwach wird er durch Erosion, nicht durch Spannung allein.
     */
    public static Material klei() {
        Material m = new Material();
        m.name = "Klei";
        m.density = 1900;
        m.young = 3.0e7;
        m.tensile = 1.5e4;
        m.compressive = 1.5e5;
        m.frictionAngle = 25;
        m.scatter = 0.3;
        m.friction = 0.6;
        m.restitution = 0.05;
        return m;
    }

    /** Beton einer Staumauer: 2 400 kg/m³, E 30 GPa, Zug 3 MPa, Druck 30 MPa, φ 37° [G]. */
    public static Material concrete() {
        Material m = new Material();
        m.name = "Beton";
        m.density = 2400;
        m.young = 3.0e10;
        m.tensile = 3.0e6;
        m.compressive = 3.0e7;
        m.frictionAngle = 37;
        m.friction = 0.6;
        m.restitution = 0.15;
        return m;
    }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "%s %.0f kg/m³ · Zug %s · Druck %s · φ %.0f°", name, density, pa(tensile), pa(compressive), frictionAngle);
    }

    static String pa(double p) {
        return p >= 1e6 ? String.format(Locale.GERMAN, "%.1f MPa", p / 1e6) : String.format(Locale.GERMAN, "%.0f kPa", p / 1e3);
    }
}
