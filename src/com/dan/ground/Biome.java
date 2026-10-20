package com.dan.ground;

import java.util.ArrayList;
import java.util.List;

/**
 * Welche Arten wie dicht wachsen. Jeder Eintrag nennt die Art, die Schicht der Bedeckung, aus der sie
 * ihre Dichte nimmt (0 Gras, 1 Blumen, 2 Steine, 3 Erde), die Dichte je Quadratmeter bei voller
 * Bedeckung und ob sie es feucht (+1) oder trocken (−1) mag.
 */
public final class Biome {
    public final String name;

    public static final class Entry {
        public final Plant plant;
        public final int layer;
        public float perM2;
        public final float moisture;

        Entry(Plant plant, int layer, float perM2, float moisture) { this.plant = plant; this.layer = layer; this.perM2 = perM2; this.moisture = moisture; }
    }

    public final List<Entry> entries = new ArrayList<>();

    public Biome(String name) { this.name = name; }

    public Biome add(Plant p, int layer, float perM2, float moisture) { entries.add(new Entry(p, layer, perM2, moisture)); return this; }

    @Override public String toString() { return name; }

    /** Bergwiese in Yellowstone: Schwingel und Weizengras, Seggen in den Senken, Sommerblumen, Geröll. */
    public static Biome yellowstone() {
        return new Biome("Wiese · Yellowstone")
                .add(Plant.idahoFescue(), 0, 9.0f, -0.3f).add(Plant.wheatgrass(), 0, 3.0f, -0.5f).add(Plant.sedge(), 0, 4.0f, 1)
                .add(Plant.lupine(), 1, 2.0f, 0).add(Plant.paintbrush(), 1, 1.4f, -0.3f).add(Plant.balsamroot(), 1, 0.7f, -0.6f)
                .add(Plant.harebell(), 1, 1.6f, 0)
                .add(Plant.pebble(), 2, 14, 0).add(Plant.stone(), 2, 1.2f, 0)
                .add(Plant.soil(), 3, 0.25f, 0);
    }

    /** Blumenwiese in Mitteleuropa: dichtes Rispengras, Margeriten, Mohn, Kornblumen, Löwenzahn. */
    public static Biome europe() {
        return new Biome("Wiese · Mitteleuropa")
                .add(Plant.meadowGrass(), 0, 14.0f, 0).add(Plant.sedge(), 0, 2.0f, 1)
                .add(Plant.oxeyeDaisy(), 1, 2.5f, 0).add(Plant.poppy(), 1, 1.3f, -0.5f).add(Plant.cornflower(), 1, 1.3f, -0.3f)
                .add(Plant.dandelion(), 1, 2.5f, 0.2f)
                .add(Plant.pebble(), 2, 8, 0).add(Plant.stone(), 2, 0.6f, 0)
                .add(Plant.soil(), 3, 0.2f, 0);
    }

    /**
     * Deich und Salzwiese an der Nordsee (seit Rungholt Phase 3): kurz geweidetes Deichgras (das hohe zeichnet
     * {@code com.dan.reed}), Seggen in den feuchten Senken, wenig Weißklee-Ersatz (Löwenzahn, Margeriten), kaum Steine
     * (Marschboden). Gut halb so dicht wie {@link #europe()}, damit die Bodendecke auf dem Prozessor billig bleibt.
     * Seit Rungholt Phase 10 im Nassen (Vorland) auch Andel, Queller und Strandaster.
     */
    public static Biome deich() {
        return new Biome("Deich · Nordsee")
                .add(Plant.meadowGrass(), 0, 5.2f, 0).add(Plant.sedge(), 0, 1.2f, 1)
                .add(Plant.andel(), 0, 0.8f, 1).add(Plant.queller(), 0, 0.4f, 1)
                .add(Plant.dandelion(), 1, 1.2f, 0.2f).add(Plant.oxeyeDaisy(), 1, 0.6f, -0.2f).add(Plant.strandaster(), 1, 0.25f, 1)
                .add(Plant.pebble(), 2, 0.8f, 0)
                .add(Plant.soil(), 3, 0.1f, 0);
    }

    /**
     * Seit Rungholt Phase 10: Salzwiese an der Nordsee: Andelrasen, im nassen Teil Queller (im Herbst rot), Strandaster,
     * kaum Steine, offener Klei am Priel.
     */
    public static Biome salzwiese() {
        return new Biome("Salzwiese · Nordsee")
                .add(Plant.andel(), 0, 8.0f, 0.4f).add(Plant.queller(), 0, 6.0f, 1)
                .add(Plant.strandaster(), 1, 0.8f, 0.6f)
                .add(Plant.klei(), 3, 0.15f, 1);
    }

    /**
     * Seit Rungholt Phase 10: Marsch der Edomsharde 1362: Fettweide mit Wiesen-Rispengras und Weißklee, Hahnenfuß, Seggen
     * an den Grabenrändern, Klei, wo das Vieh tritt; keine Steine (Marschboden). Seit Rungholt Phase 12 dünner (Befund
     * Status_Phase11: 4,3 ms Bodendecke auf dem Prozessor über der Marsch).
     */
    public static Biome marsch() {
        return new Biome("Marsch · 1362")
                .add(Plant.meadowGrass(), 0, 4.6f, 0).add(Plant.sedge(), 0, 0.9f, 1)
                .add(Plant.whiteClover(), 1, 1.6f, 0).add(Plant.buttercup(), 1, 0.7f, 0.2f).add(Plant.dandelion(), 1, 0.35f, 0)
                .add(Plant.klei(), 3, 0.2f, 0.5f);
    }

    /**
     * Schlei im Herbst (seit Haithabu Phase 2b): im Wald Adlerfarn und Seggen (die Laubstreu malt das Gelände), auf den Wiesen späte
     * Schafgarbe und Rainfarn, Kiesel und eiszeitliche Findlinge. Das Gras selbst liefert dort {@code com.dan.reed}.
     */
    public static Biome schlei() {
        return new Biome("Schlei · Herbst")
                .add(Plant.bracken(), 0, 1.6f, -0.2f).add(Plant.sedge(), 0, 2.2f, 1)
                .add(Plant.yarrow(), 1, 1.2f, -0.3f).add(Plant.tansy(), 1, 0.6f, 0.1f)
                .add(Plant.pebble(), 2, 6, 0).add(Plant.stone(), 2, 0.6f, 0).add(Plant.boulder(), 2, 0.025f, 0)
                .add(Plant.soil(), 3, 0.06f, 0);
    }

    /**
     * Moräne (seit Glacier, Phase 2): frischer Schutt am Rand des Gletschers, Kies, Steine und Blöcke, kaum Bewuchs,
     * einzelne Horste und Seggen in den nassen Rinnen.
     */
    public static Biome moraine() {
        return new Biome("Moräne")
                .add(Plant.idahoFescue(), 0, 0.5f, -0.5f).add(Plant.sedge(), 0, 0.8f, 1)
                .add(Plant.pebble(), 2, 55, 0).add(Plant.stone(), 2, 6, 0).add(Plant.boulder(), 2, 0.12f, 0)
                .add(Plant.soil(), 3, 0.3f, 0);
    }

    /**
     * Patagonien am See (seit Glacier, Phase 2): unter den Südbuchen Laubstreu und Seggen, auf den offenen Hängen
     * Horstgras (das hohe zeichnet {@code com.dan.reed}), wenige Sommerblumen, viel Kies und Steine vom Eis.
     */
    public static Biome patagonia() {
        return new Biome("Patagonien · Lago Argentino")
                .add(Plant.idahoFescue(), 0, 3.0f, -0.6f).add(Plant.sedge(), 0, 1.6f, 1)
                .add(Plant.harebell(), 1, 0.4f, -0.2f).add(Plant.yarrow(), 1, 0.3f, -0.4f)
                .add(Plant.pebble(), 2, 18, 0).add(Plant.stone(), 2, 1.6f, 0).add(Plant.boulder(), 2, 0.04f, 0)
                .add(Plant.soil(), 3, 0.15f, 0);
    }

    /** Kiesbank am Fluss: viele Kiesel und Steine, wenig Gras, Seggen am Wasser. */
    public static Biome gravelBar() {
        return new Biome("Kiesbank")
                .add(Plant.sedge(), 0, 2.0f, 1).add(Plant.wheatgrass(), 0, 0.6f, -1)
                .add(Plant.harebell(), 1, 0.3f, 0)
                .add(Plant.pebble(), 2, 60, 0).add(Plant.stone(), 2, 5, 0)
                .add(Plant.soil(), 3, 0.1f, 0);
    }
}
