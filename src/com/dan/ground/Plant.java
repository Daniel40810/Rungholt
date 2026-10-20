package com.dan.ground;

/**
 * Eine Art der Bodendecke: ein Gras (Horst aus Halmen), eine Blume (Stängel, Blätter, Blüte), ein Stein
 * oder offene Erde. Alle Felder sind öffentlich und lassen sich ändern; die Voreinstellungen stehen als
 * statische Methoden bereit. Farben linear (nicht sRGB), Längen in Metern, Tage im Jahr 1..365.
 */
public final class Plant {
    public enum Kind { GRASS, FLOWER, STONE, SOIL }

    public final String name, latin;
    public final Kind kind;
    /** Höhe (Gras, Blume) bzw. Durchmesser (Stein, Erde) und Streuung ±. */
    public float size = 0.4f, sizeVar = 0.3f;
    /** Gras: Halme je Horst, Breite eines Halms, wie weit sie auseinanderstehen, Neigung nach außen. */
    public int blades = 7;
    public float bladeWidth = 0.012f, spread = 0.06f, lean = 0.35f;
    /** Wie steif (1 = mittel; Blumenstängel steifer, feines Gras weicher). */
    public float stiffness = 1;
    /** Gras: Farbe frisch, trocken (Hochsommer), im Winter; Spitzen heller. */
    public float[] green = {0.06f, 0.14f, 0.03f}, dry = {0.30f, 0.24f, 0.11f}, winter = {0.20f, 0.17f, 0.12f};
    /** Tage: ergrünt, beginnt zu trocknen, ganz trocken. */
    public int greenUp = 120, dryStart = 200, dryFull = 240;
    /** Blume: Blütenblätter (Anzahl, Farbe), Mitte, Größe der Blüte; Blütezeit (Beginn, Höhepunkt, Ende). */
    public int petals = 5;
    public float[] petal = {0.6f, 0.05f, 0.05f}, center = {0.5f, 0.35f, 0.02f};
    public float head = 0.03f;
    /** Blütenstand als Ähre (Lupine, Indian Paintbrush): Blüten entlang der Spitze statt einer Scheibe. */
    public boolean spike;
    public int bloomStart = 150, bloomPeak = 180, bloomEnd = 215;
    /** Stein, Erde: Farbe. */
    public float[] color = {0.25f, 0.24f, 0.22f};

    public Plant(String name, String latin, Kind kind) { this.name = name; this.latin = latin; this.kind = kind; }

    @Override public String toString() { return name; }

    // ------------------------------------------------------------ Gräser

    /** Idaho-Schwingel (Festuca idahoensis): blaugrüne Horste, trocken goldgelb; häufig in Yellowstone. */
    public static Plant idahoFescue() {
        Plant p = new Plant("Idaho-Schwingel", "Festuca idahoensis", Kind.GRASS);
        p.size = 0.35f; p.blades = 13; p.bladeWidth = 0.010f; p.spread = 0.06f; p.lean = 0.45f; p.stiffness = 0.8f;
        p.green = new float[]{0.07f, 0.13f, 0.06f}; p.dry = new float[]{0.34f, 0.27f, 0.12f};
        p.greenUp = 130; p.dryStart = 195; p.dryFull = 230;
        return p;
    }

    /** Ährenweizengras (Pseudoroegneria spicata): hohe, lockere Horste. */
    public static Plant wheatgrass() {
        Plant p = new Plant("Ährenweizengras", "Pseudoroegneria spicata", Kind.GRASS);
        p.size = 0.6f; p.blades = 8; p.bladeWidth = 0.012f; p.spread = 0.08f; p.lean = 0.3f; p.stiffness = 0.9f;
        p.green = new float[]{0.08f, 0.15f, 0.05f}; p.dry = new float[]{0.36f, 0.29f, 0.14f};
        p.greenUp = 125; p.dryStart = 200; p.dryFull = 240;
        return p;
    }

    /** Segge (Carex): dunkelgrün, bleibt lange frisch; in feuchten Senken und am Ufer. */
    public static Plant sedge() {
        Plant p = new Plant("Segge", "Carex", Kind.GRASS);
        p.size = 0.45f; p.blades = 10; p.bladeWidth = 0.013f; p.spread = 0.06f; p.lean = 0.5f; p.stiffness = 0.7f;
        p.green = new float[]{0.04f, 0.11f, 0.03f}; p.dry = new float[]{0.20f, 0.18f, 0.08f};
        p.greenUp = 115; p.dryStart = 245; p.dryFull = 285;
        return p;
    }

    /** Wiesen-Rispengras (Poa pratensis): dichte, weiche Wiese in Mitteleuropa. */
    public static Plant meadowGrass() {
        Plant p = new Plant("Wiesen-Rispengras", "Poa pratensis", Kind.GRASS);
        p.size = 0.3f; p.blades = 14; p.bladeWidth = 0.011f; p.spread = 0.08f; p.lean = 0.4f; p.stiffness = 0.75f;
        p.green = new float[]{0.06f, 0.16f, 0.03f}; p.dry = new float[]{0.24f, 0.22f, 0.10f};
        p.greenUp = 80; p.dryStart = 215; p.dryFull = 260;
        return p;
    }

    /** Adlerfarn (Pteridium aquilinum): breite Wedel im lichten Wald und am Waldrand; im Herbst rostbraun (seit Haithabu Phase 2b). */
    public static Plant bracken() {
        Plant p = new Plant("Adlerfarn", "Pteridium aquilinum", Kind.GRASS);
        p.size = 0.75f; p.sizeVar = 0.35f; p.blades = 6; p.bladeWidth = 0.075f; p.spread = 0.12f; p.lean = 0.55f; p.stiffness = 1.2f;
        p.green = new float[]{0.05f, 0.11f, 0.025f}; p.dry = new float[]{0.24f, 0.11f, 0.04f}; p.winter = new float[]{0.16f, 0.09f, 0.05f};
        p.greenUp = 135; p.dryStart = 262; p.dryFull = 305;
        return p;
    }

    // ------------------------------------------------------------ Blumen

    /** Indian Paintbrush (Castilleja): leuchtend rote Hochblätter an der Spitze. */
    public static Plant paintbrush() {
        Plant p = new Plant("Indian Paintbrush", "Castilleja miniata", Kind.FLOWER);
        p.size = 0.35f; p.spike = true; p.head = 0.07f; p.petal = new float[]{0.62f, 0.06f, 0.03f}; p.center = new float[]{0.5f, 0.08f, 0.03f};
        p.bloomStart = 165; p.bloomPeak = 190; p.bloomEnd = 225; p.stiffness = 1.3f;
        return p;
    }

    /** Silberlupine (Lupinus argenteus): violettblaue Ähre. */
    public static Plant lupine() {
        Plant p = new Plant("Silberlupine", "Lupinus argenteus", Kind.FLOWER);
        p.size = 0.45f; p.spike = true; p.head = 0.11f; p.petal = new float[]{0.14f, 0.10f, 0.45f}; p.center = new float[]{0.20f, 0.15f, 0.50f};
        p.bloomStart = 160; p.bloomPeak = 185; p.bloomEnd = 215; p.stiffness = 1.3f;
        return p;
    }

    /** Pfeilblättrige Balsamwurzel (Balsamorhiza sagittata): große gelbe Sonnen im Frühsommer. */
    public static Plant balsamroot() {
        Plant p = new Plant("Balsamwurzel", "Balsamorhiza sagittata", Kind.FLOWER);
        p.size = 0.35f; p.petals = 13; p.head = 0.05f; p.petal = new float[]{0.80f, 0.55f, 0.02f}; p.center = new float[]{0.45f, 0.30f, 0.02f};
        p.bloomStart = 140; p.bloomPeak = 160; p.bloomEnd = 185; p.stiffness = 1.4f;
        return p;
    }

    /** Glockenblume (Campanula rotundifolia): kleine hellblaue Glocken, spät im Sommer. */
    public static Plant harebell() {
        Plant p = new Plant("Rundblättrige Glockenblume", "Campanula rotundifolia", Kind.FLOWER);
        p.size = 0.3f; p.petals = 5; p.head = 0.025f; p.petal = new float[]{0.20f, 0.25f, 0.62f}; p.center = new float[]{0.3f, 0.35f, 0.7f};
        p.bloomStart = 190; p.bloomPeak = 215; p.bloomEnd = 250; p.stiffness = 0.8f;
        return p;
    }

    /** Margerite (Leucanthemum vulgare): weiß mit gelber Mitte. */
    public static Plant oxeyeDaisy() {
        Plant p = new Plant("Margerite", "Leucanthemum vulgare", Kind.FLOWER);
        p.size = 0.45f; p.petals = 16; p.head = 0.035f; p.petal = new float[]{0.85f, 0.85f, 0.80f}; p.center = new float[]{0.75f, 0.55f, 0.02f};
        p.bloomStart = 140; p.bloomPeak = 165; p.bloomEnd = 200; p.stiffness = 1.1f;
        return p;
    }

    /** Klatschmohn (Papaver rhoeas): große rote Blüte, dunkle Mitte. */
    public static Plant poppy() {
        Plant p = new Plant("Klatschmohn", "Papaver rhoeas", Kind.FLOWER);
        p.size = 0.5f; p.petals = 4; p.head = 0.045f; p.petal = new float[]{0.70f, 0.03f, 0.02f}; p.center = new float[]{0.03f, 0.03f, 0.03f};
        p.bloomStart = 145; p.bloomPeak = 170; p.bloomEnd = 205; p.stiffness = 0.9f;
        return p;
    }

    /** Kornblume (Centaurea cyanus): tiefblau. */
    public static Plant cornflower() {
        Plant p = new Plant("Kornblume", "Centaurea cyanus", Kind.FLOWER);
        p.size = 0.5f; p.petals = 9; p.head = 0.028f; p.petal = new float[]{0.06f, 0.14f, 0.62f}; p.center = new float[]{0.10f, 0.08f, 0.35f};
        p.bloomStart = 155; p.bloomPeak = 180; p.bloomEnd = 225; p.stiffness = 1.0f;
        return p;
    }

    /** Löwenzahn (Taraxacum officinale): gelb im Frühling, dann weiße Pusteblumen. */
    public static Plant dandelion() {
        Plant p = new Plant("Löwenzahn", "Taraxacum officinale", Kind.FLOWER);
        p.size = 0.22f; p.petals = 18; p.head = 0.028f; p.petal = new float[]{0.85f, 0.62f, 0.02f}; p.center = new float[]{0.8f, 0.55f, 0.02f};
        p.bloomStart = 95; p.bloomPeak = 120; p.bloomEnd = 150; p.stiffness = 1.2f;
        return p;
    }

    /** Gemeine Schafgarbe (Achillea millefolium): weiße Dolden, blüht bis in den Oktober (seit Haithabu Phase 2b). */
    public static Plant yarrow() {
        Plant p = new Plant("Schafgarbe", "Achillea millefolium", Kind.FLOWER);
        p.size = 0.4f; p.petals = 12; p.head = 0.04f; p.petal = new float[]{0.72f, 0.70f, 0.62f}; p.center = new float[]{0.60f, 0.55f, 0.45f};
        p.bloomStart = 165; p.bloomPeak = 215; p.bloomEnd = 300; p.stiffness = 1.2f;
        return p;
    }

    /** Rainfarn (Tanacetum vulgare): gelbe Knöpfe in Doldentrauben, an Wegen und Ufern, bis Oktober (seit Haithabu Phase 2b). */
    public static Plant tansy() {
        Plant p = new Plant("Rainfarn", "Tanacetum vulgare", Kind.FLOWER);
        p.size = 0.6f; p.petals = 10; p.head = 0.03f; p.petal = new float[]{0.62f, 0.45f, 0.02f}; p.center = new float[]{0.55f, 0.40f, 0.02f};
        p.bloomStart = 190; p.bloomPeak = 230; p.bloomEnd = 290; p.stiffness = 1.3f;
        return p;
    }

    // ------------------------------------------------------------ Salzwiese und Marsch (seit Rungholt Phase 10)

    /**
     * Queller (Salicornia europaea): fingerhohe, fleischige Sprosse ohne Blätter im untersten Vorland; im Sommer
     * frischgrün, ab Ende August rot, im Winter braun und abgestorben.
     */
    public static Plant queller() {
        Plant p = new Plant("Queller", "Salicornia europaea", Kind.GRASS);
        p.size = 0.14f; p.sizeVar = 0.4f; p.blades = 7; p.bladeWidth = 0.009f; p.spread = 0.035f; p.lean = 0.25f; p.stiffness = 1.6f;
        p.green = new float[]{0.08f, 0.17f, 0.04f}; p.dry = new float[]{0.30f, 0.05f, 0.04f}; p.winter = new float[]{0.14f, 0.09f, 0.06f};
        p.greenUp = 150; p.dryStart = 235; p.dryFull = 275;
        return p;
    }

    /** Andel (Puccinellia maritima): niedriger, graugrüner Rasen der Salzwiese, von Schafen kurz gehalten. */
    public static Plant andel() {
        Plant p = new Plant("Andel", "Puccinellia maritima", Kind.GRASS);
        p.size = 0.12f; p.blades = 12; p.bladeWidth = 0.008f; p.spread = 0.07f; p.lean = 0.55f; p.stiffness = 0.6f;
        p.green = new float[]{0.07f, 0.12f, 0.06f}; p.dry = new float[]{0.20f, 0.19f, 0.12f}; p.winter = new float[]{0.15f, 0.14f, 0.10f};
        p.greenUp = 100; p.dryStart = 250; p.dryFull = 300;
        return p;
    }

    /** Strandaster (Tripolium pannonicum): violette Strahlen um eine gelbe Mitte, August bis Oktober in der Salzwiese. */
    public static Plant strandaster() {
        Plant p = new Plant("Strandaster", "Tripolium pannonicum", Kind.FLOWER);
        p.size = 0.35f; p.petals = 16; p.head = 0.022f; p.petal = new float[]{0.35f, 0.22f, 0.55f}; p.center = new float[]{0.75f, 0.55f, 0.04f};
        p.bloomStart = 215; p.bloomPeak = 245; p.bloomEnd = 290; p.stiffness = 1.2f;
        return p;
    }

    /** Weißklee (Trifolium repens): weiße Köpfchen in der Weide, Mai bis September. */
    public static Plant whiteClover() {
        Plant p = new Plant("Weißklee", "Trifolium repens", Kind.FLOWER);
        p.size = 0.12f; p.petals = 14; p.head = 0.012f; p.petal = new float[]{0.72f, 0.72f, 0.66f}; p.center = new float[]{0.62f, 0.60f, 0.52f};
        p.bloomStart = 135; p.bloomPeak = 180; p.bloomEnd = 260; p.stiffness = 1.0f;
        return p;
    }

    /** Scharfer Hahnenfuß (Ranunculus acris): glänzend gelbe Schalen über der Fettweide, Mai bis Juli. */
    public static Plant buttercup() {
        Plant p = new Plant("Hahnenfuß", "Ranunculus acris", Kind.FLOWER);
        p.size = 0.4f; p.petals = 5; p.head = 0.012f; p.petal = new float[]{0.85f, 0.65f, 0.02f}; p.center = new float[]{0.7f, 0.5f, 0.02f};
        p.bloomStart = 130; p.bloomPeak = 160; p.bloomEnd = 200; p.stiffness = 1.1f;
        return p;
    }

    /** Klei: dunkle, graubraune Schollen aus Marschton, wo das Vieh tritt und am Grabenrand. */
    public static Plant klei() {
        Plant p = new Plant("Klei", "", Kind.SOIL);
        p.size = 0.9f; p.sizeVar = 0.5f; p.color = new float[]{0.085f, 0.075f, 0.06f};
        return p;
    }

    // ------------------------------------------------------------ Steine und Erde

    /** Kiesel: klein, rund, grau bis braun. */
    public static Plant pebble() {
        Plant p = new Plant("Kiesel", "", Kind.STONE);
        p.size = 0.07f; p.sizeVar = 0.5f; p.color = new float[]{0.15f, 0.135f, 0.12f};
        return p;
    }

    /** Stein: faustgroß bis kopfgroß, eckig. */
    public static Plant stone() {
        Plant p = new Plant("Stein", "", Kind.STONE);
        p.size = 0.25f; p.sizeVar = 0.6f; p.color = new float[]{0.13f, 0.12f, 0.11f};
        return p;
    }

    /** Findling: großer Stein aus der Eiszeit, halb eingesunken, flechtengrau (seit Haithabu Phase 2b). */
    public static Plant boulder() {
        Plant p = new Plant("Findling", "", Kind.STONE);
        p.size = 0.7f; p.sizeVar = 0.5f; p.color = new float[]{0.17f, 0.165f, 0.15f};
        return p;
    }

    /** Laubstreu: braune Decke aus gefallenem Laub unter Bäumen (seit Haithabu Phase 2b). */
    public static Plant litter() {
        Plant p = new Plant("Laubstreu", "", Kind.SOIL);
        p.size = 1.6f; p.sizeVar = 0.5f; p.color = new float[]{0.16f, 0.085f, 0.035f};
        return p;
    }

    /** Offene Erde: kahle Stelle (Tritt, Wühlstelle, Suhle). */
    public static Plant soil() {
        Plant p = new Plant("Offene Erde", "", Kind.SOIL);
        p.size = 1.2f; p.sizeVar = 0.5f; p.color = new float[]{0.13f, 0.10f, 0.07f};
        return p;
    }
}
