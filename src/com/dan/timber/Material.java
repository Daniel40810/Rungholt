package com.dan.timber;

/**
 * Die Werkstoffe des Holzbaus. Der Shader in {@code gl/} malt jeden aus Rauschen: die Faser läuft in Richtung u der
 * Flächenkoordinate ({@link Mesh}).
 */
public enum Material {
    /** Pfosten, Schwellen, Stützen: Eiche, gespalten und mit dem Beil behauen; Faser entlang. */
    POST,
    /** Spaltbohlen der Wände: senkrecht, 20 bis 30 cm breit, mit Fugen. */
    PLANK,
    /** Flechtwerk mit Lehmputz: lehmgelb bis grau, Risse, wo der Putz trocknet. */
    DAUB,
    /** Reet: Halme vom First zur Traufe, in Lagen gebunden; alt grau, Moos auf der Nordseite. */
    THATCH,
    /** Firstsoden: Grasnarbe auf dem First. */
    SOD,
    /** Türblatt: dunkle Bohlen mit Querriegeln. */
    DOOR,
    /** Flechtwerk ohne Putz: Zäune, Rückwand des Schuppens, Hürden. */
    WATTLE,
    /** Dunkle Öffnung: Rauchloch, offene Tür, Inneres. */
    DARK,
    /** Bohlen eines Wegs oder einer Brücke: waagerecht, abgetreten. */
    DECK,
    /** Boden für Vorführungen (gestampfter Lehm und Gras). */
    GROUND,
    /** Gespaltener, getrockneter Fisch am Gestell: graubraun, innen heller, leicht glänzend (seit Haithabu Phase 5). */
    FISH,
    /** Basaltlava: Mühlsteine aus der Eifel (seit Haithabu Phase 8). */
    STONE,
    /** Tuch und Fell: Ballen, mit Schnur gebunden (seit Haithabu Phase 8). */
    CLOTH,
    /** Speckstein: Töpfe aus Norwegen, unten verrußt (v = Höhe 0..1; seit Haithabu Phase 8). */
    SOAPSTONE,
    /** Gestampfter Lehmboden im Haus mit Binsen und Stroh, um den Herd voll Asche und Ruß (seit Haithabu Phase 11). */
    FLOOR,
    /** Fell auf Bänken: Schaf, Ziege, Rind; zottig (seit Haithabu Phase 11). */
    FUR,
    /** Eisen: Kessel, Kette, Beil, Zange; geschmiedet, dunkel, an Kanten heller (seit Haithabu Phase 11). */
    IRON,
    /** Wolle am Webstuhl: Kettfäden und Gewebe in Naturfarben und Krapprot (seit Haithabu Phase 11). */
    WOOL,
    /** Herdstelle: gebrannter Lehm und Asche zwischen den Randsteinen (seit Haithabu Phase 11). */
    HEARTH,
    /**
     * Backstein im Läuferverband mit hellen Fugen, rot bis braunrot, unten Spritzwasser; seit Rungholt Phase 9. Darin
     * gemalt weiße Sprossenfenster alle 2,4 m (0,95 bis 1,85 m über dem Boden) für das Friesenhaus.
     */
    BRICK,
    /**
     * Backstein der Kirche (seit Rungholt Phase 11): Klosterformat (28 × 9 cm) im Wendischen Verband, dunkler und
     * unregelmäßiger als {@link #BRICK}, ohne Sprossenfenster; gemalt schmale Rundbogenfenster (1,1 × 3,4 m, alle
     * 4,2 m, 3,2 m über dem Boden) und oben im Turm Schallöffnungen (über 17,5 m).
     */
    CHURCH,
    /** Holzschindeln am Turmhelm (seit Rungholt Phase 11): Eiche, silbergrau verwittert, in Reihen. */
    SHINGLE
}
