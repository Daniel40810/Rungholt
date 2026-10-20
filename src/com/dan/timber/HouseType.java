package com.dan.timber;

import java.util.List;

/**
 * Die sechs Haustypen. Maße und Bauweise nach den Befunden von Haithabu (Grundflächen 3,5 × 17 m bis 7 × 17,5 m,
 * Wände aus Spaltbohlen oder lehmverputztem Flechtwerk zwischen Wandständern, Reetdächer, am Ufer leichte Holzhäuser,
 * höher Grubenhäuser); die Zuordnung zu sechs Typen und alle Einzelmaße sind gestaltet [G]. Ein Typ ist eine Zeile
 * Zahlen, kein Modell: {@link Builder} macht daraus die Netze.
 * <p>
 * Örtliches Maß: x entlang des Firsts (Länge), z quer (Breite), y nach oben, Ursprung in der Mitte auf dem Boden.
 */
public enum HouseType {
    /** Hallenhaus wie das Versammlungshaus des Museums: 17,5 × 7 m, Flechtwerk, Schrägstützen, zwei Türen. */
    HALLE("Hallenhaus", 17.5, 7.0, 2.3, 48, Material.DAUB, 0, true, false,
            List.of(new Door(Side.SOUTH, 0.42), new Door(Side.NORTH, 0.58)), true),
    /** Wohnhaus wie Haus 1 (um 870): 12 × 5 m, drei Räume, Flechtwerk, Schrägstützen, Tür an der Traufseite und im Giebel. */
    WOHNHAUS("Wohnhaus", 12.0, 5.0, 2.0, 47, Material.DAUB, 0, true, false,
            List.of(new Door(Side.SOUTH, 0.35), new Door(Side.EAST, 0.5)), true),
    /** Spaltbohlenhaus eines Händlers: 9 × 4,5 m, senkrechte Bohlen auf der Schwelle, Tür im Giebel. */
    BOHLENHAUS("Spaltbohlenhaus", 9.0, 4.5, 2.1, 45, Material.PLANK, 0, false, false,
            List.of(new Door(Side.EAST, 0.5)), true),
    /** Schmales Uferhaus: 17 × 3,5 m, leicht gebaut aus Bohlen, zwei Türen zur Traufseite. */
    UFERHAUS("Uferhaus", 17.0, 3.5, 1.9, 50, Material.PLANK, 0, false, false,
            List.of(new Door(Side.SOUTH, 0.25), new Door(Side.SOUTH, 0.7)), true),
    /** Grubenhaus: 4 × 3 m, 0,7 m eingetieft, das Dach reicht fast bis zum Boden, Firstpfosten, Eingang im Giebel. */
    GRUBENHAUS("Grubenhaus", 4.0, 3.0, 0.35, 52, Material.PLANK, 0.7, false, false,
            List.of(new Door(Side.EAST, 0.5)), true),
    /** Offener Schuppen einer Werkstatt: 6 × 4 m, Pfosten, Rückwand aus Flechtwerk, sonst offen. */
    SCHUPPEN("Werkstattschuppen", 6.0, 4.0, 2.0, 40, Material.WATTLE, 0, false, true, List.of(), false),
    /**
     * Seit Rungholt Phase 9: das Friesenhaus einer Hallig, 14 × 7,5 m, Wände aus Backstein (36 cm) mit Sprossenfenstern,
     * steiles Reetdach (50°), Haustür an der Südseite, Stalltür im Ostgiebel [G].
     */
    FRIESENHAUS("Friesenhaus", 14.0, 7.5, 2.4, 50, Material.BRICK, 0, false, false,
            List.of(new Door(Side.SOUTH, 0.36), new Door(Side.EAST, 0.5)), false);

    /** Seite des Hauses: SOUTH/NORTH die Traufseiten (+z/−z), EAST/WEST die Giebel (+x/−x). */
    public enum Side { SOUTH, NORTH, EAST, WEST }

    /** Eine Tür: Seite und Lage entlang der Wand (0..1). */
    public record Door(Side side, double at) { }

    public final String label;
    /** Länge (entlang des Firsts), Breite, Wandhöhe über dem Boden (m), Dachneigung (Grad). */
    public final double length, width, wallHeight, pitchDeg;
    /** Werkstoff der Wände. */
    public final Material wall;
    /** Wie tief der Boden eingetieft ist (m, Grubenhaus). */
    public final double sunk;
    /** Schrägstützen außen an den Traufseiten. */
    public final boolean supports;
    /** Offen bis auf die Rückwand. */
    public final boolean open;
    public final List<Door> doors;
    /** Rauchloch im Giebel (Ulenloch). */
    public final boolean smokeHole;

    HouseType(String label, double length, double width, double wallHeight, double pitchDeg, Material wall, double sunk, boolean supports, boolean open,
              List<Door> doors, boolean smokeHole) {
        this.label = label;
        this.length = length;
        this.width = width;
        this.wallHeight = wallHeight;
        this.pitchDeg = pitchDeg;
        this.wall = wall;
        this.sunk = sunk;
        this.supports = supports;
        this.open = open;
        this.doors = doors;
        this.smokeHole = smokeHole;
    }

    /** Überstand des Reets an der Traufe und am Giebel, Dicke der Reetschicht (m). */
    public static final double EAVE = 0.55, GABLE = 0.35, THATCH = 0.38;

    public double tanPitch() { return Math.tan(Math.toRadians(pitchDeg)); }

    /** Höhe der Traufkante (unten, außen): beim Grubenhaus knapp über dem Boden. */
    public double eaveY() {
        double y = wallHeight - eaveOut() * tanPitch();
        return y;
    }

    /** Überstand an der Traufe: beim Grubenhaus so weit, dass die Traufe 5 cm über dem Boden liegt. */
    public double eaveOut() { return sunk > 0 ? Math.max(0.2, (wallHeight - 0.05) / tanPitch()) : EAVE; }

    /** Höhe des Firsts (Unterseite der Reetschicht in der Mitte). */
    public double ridgeY() { return wallHeight + width / 2 * tanPitch(); }

    /** Höhe über alles (mit Reetschicht und Firstsoden). */
    public double height() { return ridgeY() + THATCH / Math.cos(Math.toRadians(pitchDeg)) + 0.25; }

    /** Grundfläche innen (m²). */
    public double area() { return length * width; }

    /** Halbe Außenlänge bis über die Giebelkante des Dachs (m). */
    public double outerHalfLength() { return length / 2 + GABLE + 0.1; }

    /** Halbe Außenbreite bis über die Traufkante des Reets oder die Füße der Schrägstützen (m). */
    public double outerHalfWidth() {
        return Math.max(width / 2 + eaveOut() + THATCH * Math.sin(Math.toRadians(pitchDeg)) + 0.05, supports ? width / 2 + 1.15 : 0);
    }
}
