package com.dan.ice;

/**
 * Eis als Material und als Körper (Werkbuch Glacier, Abschnitt 4). Stand Phase 4: das Material – die Werte hier,
 * die Optik in {@link IceOptics}, die Risse in {@link IceNoise}, Formen in {@link IceShapes} und {@link IceMesh},
 * gezeichnet von {@code gl/IceGl}. Der Kern kennt nur Java 21; das Package kennt weder die App noch ein anderes Package.
 * <ul>
 * <li><b>Material (Phase 4):</b> Licht im Eis nach Beer-Lambert je Farbkanal (Absorption von reinem Eis nach Warren &amp;
 * Brandt 2008), Dicke aus einem Rückseiten-Tiefenbild, Blasen als Streuung (Firn weiß, altes Eis blau), Risse als
 * Zellgrenzen im Volumen mit Totalreflexion, Moränenbänder, Schmelzwasserfilm, Glitzer, Schnee.</li>
 * <li><b>Körper (Phase 5):</b> ein Gletscher aus Oberfläche, Grund und Fließfeld; Spalten quer und längs aus der Dehnung,
 * Türme im Eisfall, die Front mit Schmelzkerbe, Vorstoß; Eisberge mit wanderndem Schwerpunkt; Zellen mit Festigkeit
 * für {@code fracture}.</li>
 * </ul>
 */
public final class Ice {
    /** Qualitätsstufen, eigene Kopie im Package (Paketvertrag Punkt 7). */
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Stand des Packages: 1 = Hülle; 4 = Material; 5 = Gletscher. */
    public static final int STAGE = 5;

    /** Startwert des Zufalls: gleicher seed, gleiches Eis (Punkt 6). */
    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Anteil der Luftblasen 0..1 (Firn 1, altes gepresstes Eis 0) und Dichte der Risse 0..1 (Phase 4). */
    public double bubbles = 0.5, cracks = 0.3;
    /**
     * Phase 4: Moränenbänder 0..1 (dunkler Schutt in Streifen), Schmelzwasserfilm 0..1 (glatt, glänzend), Schnee auf
     * waagerechten Flächen 0..1, Glitzer der Kristallflächen in der Sonne 0..1.
     */
    public double moraine = 0, melt = 0.2, snow = 0, glitter = 0.5;
    /** Verwitterung der Oberfläche 0..1: weiße Kruste (alt, besonnt) bis keine (frisch gebrochen, nass: blau). */
    public double weathering = 0.6;
    /** Größe einer Risszelle (m): so weit liegen die Rissflächen im Mittel auseinander. */
    public double crackCell = 2.5;
    /** Richtung der Moränenbänder (Einheitsvektor im Körper; die Bänder liegen quer dazu). */
    public double[] bandDir = {1, 0.15, 0.2};
    /** Abstand der Moränenbänder (m): am Block wenige Meter, auf einer Gletscherzunge Hunderte (Mittelmoränen). */
    public double bandSpacing = 9;
    /** Dichte des Eises (kg/m³); Gletschereis rund 900 (Werkbuch, Abschnitt 2). */
    public double density = 900;
    /** Lufttemperatur (°C) und Wassertemperatur (°C) für Schmelze und Schmelzkerbe (Phase 5). */
    public double airTempC = 8, waterTempC = 2;

    // ------------------------------------------------------------------ Presets (Punkt 5)
    /** Junges Eis und Firn: viele Blasen, weiß. */
    public static Ice firn() { Ice i = new Ice(); i.bubbles = 0.9; i.cracks = 0.1; i.density = 830; i.melt = 0.05; i.glitter = 0.8; i.weathering = 1; return i; }
    /** Gletschereis an der Front: wenig Blasen, blau in der Tiefe, viele Risse. */
    public static Ice glacier() { Ice i = new Ice(); i.bubbles = 0.5; i.cracks = 0.5; i.moraine = 0.15; i.weathering = 0.6; return i; }
    /** Altes, gepresstes Eis aus der Tiefe einer Spalte oder dem Fuß eines Eisbergs: fast klar, tiefblau. */
    public static Ice blue() { Ice i = new Ice(); i.bubbles = 0.1; i.cracks = 0.2; i.density = 917; i.melt = 0.6; i.glitter = 0.3; i.weathering = 0.05; return i; }

    /** Eisberg, der eine Weile geschwommen ist: glatt geschmolzen, nass, wenig Blasen an der Oberfläche. */
    public static Ice iceberg() { Ice i = new Ice(); i.bubbles = 0.3; i.cracks = 0.35; i.melt = 0.7; i.glitter = 0.4; i.weathering = 0.3; return i; }

    /**
     * Oberfläche einer Gletscherzunge im Sommer: verwittert und weiß, Mittelmoränen längs des Fließens alle 180 m
     * (Bänder quer zu z, also längs x – für eine Zunge, die nach +x fließt), wenig Schnee.
     */
    public static Ice tongue() {
        Ice i = new Ice(); i.bubbles = 0.55; i.cracks = 0.45; i.moraine = 0.3; i.melt = 0.25; i.glitter = 0.6; i.weathering = 0.8;
        i.bandSpacing = 180; i.bandDir = new double[]{0, 0.02, 1}; return i;
    }

    /** Die Wand der Front: frisch gebrochen, wenig Kruste, nass vom Spritzwasser und Schmelzen, blau in den Narben. */
    public static Ice front() { Ice i = new Ice(); i.bubbles = 0.42; i.cracks = 0.55; i.melt = 0.45; i.glitter = 0.4; i.weathering = 0.3; i.moraine = 0.05; return i; }

    /** Eine Kopie mit denselben Werten. */
    public Ice copy() {
        Ice i = new Ice();
        i.seed = seed; i.quality = quality; i.bubbles = bubbles; i.cracks = cracks; i.density = density; i.airTempC = airTempC;
        i.waterTempC = waterTempC; i.weathering = weathering; i.moraine = moraine; i.melt = melt; i.snow = snow; i.glitter = glitter; i.crackCell = crackCell;
        i.bandDir = bandDir.clone();
        i.bandSpacing = bandSpacing;
        return i;
    }

    /** Anteil eines schwimmenden Stücks unter Wasser bei der Dichte des Wassers (kg/m³): ρ_Eis / ρ_Wasser. */
    public double submergedFraction(double waterDensity) { return Math.min(1, density / waterDensity); }

    /** „Eis: Blasen 50 % (Streuung 3,2 /m) · Risse 30 % · Moräne 15 % · Film 20 % · Schnee 0 % · 900 kg/m³ · MEDIUM“ */
    public String describe() {
        return String.format(java.util.Locale.GERMAN, "Eis: Blasen %.0f %% (Streuung %.2g /m) · Kruste %.0f %% · Risse %.0f %% · Moräne %.0f %% · Film %.0f %% · Schnee %.0f %% · %.0f kg/m³ · %s",
                bubbles * 100, IceOptics.scattering(bubbles), weathering * 100, cracks * 100, moraine * 100, melt * 100, snow * 100, density, quality);
    }
}
