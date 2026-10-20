package com.dan.rungholt.wiring;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.fog.Fog;
import com.dan.lightning.Lightning;
import com.dan.rain.Rain;
import com.dan.sky.Sky;
import com.dan.tide.Flood;
import com.dan.tide.Tide;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Die Verdrahtung der App (Paketvertrag Punkt 4): Hier und nur hier reden die Packages miteinander. Jedes Package
 * bietet Eingänge an und meldet Ereignisse über die kleinen Schnittstellen dieses Pakets; die Phasen 2 bis 16 hängen
 * ihre Verbindungen hier ein. Wo ein Package eine eigene Kopie einer Schnittstelle mitbringt (gleicher Name, gleiche
 * Form), übersetzt {@code wiring} mit einer Zeile.
 * <p>
 * Phase 12: die Grote Mandränke: Windstau nach Drehbuch an die Tide, Orkan aus der Regie, der Deichbruch mit
 * {@code fracture} (Wasser, Strömung, Grund), die Bresche im Gelände, der Dammbruch mit {@code wave}, die Marsch als Becken
 * in der Überflutung, Häuser und Leute in der Flut.
 * <p>
 * Phase 11: die Stadt von 1362: Häuser und Kirche ({@code timber}) auf dem Gelände von 1362, die Kogge ({@code buoyancy})
 * auf dem Wasser der Überflutung von 1362 mit dem Pegel als Fahrplan, Herdrauch ({@code smoke}) im Wind, die Leute
 * ({@code figure}).
 * <p>
 * Phase 10: das Land von 1362 ({@code scene.Land1362}): Marsch, Warften, Seedeich und Siel als zweites Gelände; die Ära
 * blendet das Gelände über und schaltet Überflutung, Watt, Halme und Bodendecke auf die Welt von 1362. Die Tide läuft
 * weiter, die Marsch hinter dem Deich bleibt trocken.
 * <p>
 * Phase 9: das Jahr ({@link Jahreszeit} mit {@code com.dan.season}) an Gelände, Halme, Bodendecke, Häuser und Regie;
 * die Wattwanderung mit den Figuren aus {@code com.dan.figure}: Weg, Wasser und Schlick, Spuren, Störer für Seehunde
 * und Vögel.
 * <p>
 * Phase 8: die Wattvögel ({@code com.dan.flock.Waders}) bekommen Grund, Wasser und Schlick am Ort, die Rastplätze auf
 * dem Vorland, den Wanderer als Störer, Monat, Sonne und Wind.
 * <p>
 * Phase 7: die Seehunde ({@code com.dan.seal}) bekommen ihre Bank ({@link Haulout}), Grund und Wasser am Ort, den
 * Wattwanderer als Störer ({@link Disturber}), Monat und Sonne; ihre Spuren und die Stiefelabdrücke gehen ans Watt.
 * <p>
 * Phase 4: die Tide aus {@code com.dan.tide} statt des Platzhalters: Pegel aus zehn Partialtiden am seeseitigen Rand,
 * Windstau aus dem Wind der Regie, Wasser je Zelle mit Laufzeit, Pfützen und Strömung aus der Überflutungskarte des
 * Geländes; das Meer bekommt das Wasser als Textur, Kamera und HUD den Spiegel am Ort.
 * <p>
 * Phase 3: die Wetterregie ({@code com.dan.weather}) mit dem Klima Nordfrieslands ({@link #NORDFRIESLAND}, Monatsmittel
 * am Pegel Husum [G]; Lagen der Deutschen Bucht aus dem Package, dazu der Orkan aus Westnordwest) setzt je Bild Wind,
 * Böen, Bedeckung, Wolkenart, Regen, Sicht, Seenebel und Gewitter; daraus bekommen Himmel ({@code sky}), Wolken
 * ({@code cloud}), Regen ({@code rain}), Nebel ({@code fog}) und Blitz ({@code lightning}) ihre Eingänge, der Wind
 * geht an Meer, Gras und Bodendecke. Der Windstau am Pegel folgt mit {@code tide} in Phase 4.
 * <p>
 * Phase 2: das Gelände aus den BAW-Küstenhöhen geht an Meer, Gras, Bodendecke und Kamera, der Pegel an das Meer.
 * Phase 1: Uhr, Wind von Hand (Bft 4 aus Westsüdwest [G]), ein Platzhalter-Pegel aus M2 und M4 (bis {@code tide} in
 * Phase 4) und die Ära mit Überblendung (Heute · 1362, F17 A). Alle Schnittstellen aus dem Werkbuch (Abschnitt 4)
 * stehen; verbunden ist die Uhr mit Kamera, Himmel-Platzhalter und Bühne, der Pegel mit Bühne und Kamera.
 * {@link #planned()} nennt, was nach dem Phasenplan kommt, {@link #links()} was schon verbunden ist.
 */
public final class Wiring {
    /** Eine Verbindung: von wem, an wen, über welche Schnittstelle, in welcher Phase. */
    public record Link(String from, String to, String via, int phase) {
        @Override public String toString() { return from + " → " + to + " (" + via + ", Phase " + phase + ")"; }
    }

    /** Wind von Hand, bis {@code weather} ihn in Phase 3 setzt. Start: Bft 4 aus Westsüdwest, der häufigste Wind an der Küste [G]. */
    public static final class ManualWind implements Wind {
        public volatile double speed = Beaufort.mid(4), fromDeg = 250, gust = 0.3;
        @Override public double speed() { return speed; }
        @Override public double fromDeg() { return fromDeg; }
        @Override public double gustiness() { return gust; }
    }

    /**
     * Phase 4: die Tide aus {@code com.dan.tide}. Der Pegel am seeseitigen Rand (Norderhever: Husum 27 min früher, Hub
     * wie Pellworm [G]) mit Windstau aus dem Wetter; das Wasser je Zelle, Pfützen und Strömung aus der Überflutungskarte,
     * sobald die App sie gebaut hat ({@link #flood}). Mit {@link #hand} steht der Pegel fest (auch für die Überflutung).
     * Der Pegel Husum ({@link #husum}) dient für den Tidenkalender im Bedienfeld.
     */
    public static final class TidePegel implements Level, Water, Current {
        public final Tide tide = Tide.hever(), husum = Tide.husum();
        private final Clock clock;
        /** Pegel von Hand (m über NHN); NaN = aus der Tide. */
        public volatile double hand = Double.NaN;
        /** Die Überflutungskarte (die App setzt sie, sobald sie gebaut ist). */
        public volatile Flood flood;
        /** Gelände für den Ersatz, solange die Karte fehlt. */
        volatile Terrain terrain;

        public TidePegel(Clock clock) { this.clock = clock; }

        /** Szenenzeit in Stunden seit 1970 (UTC). */
        public double hours() { return clock.epochMillis() / 3_600_000.0; }

        @Override public double level() { double h = hand; return Double.isNaN(h) ? tide.levelAt(hours()) : h; }
        @Override public double surge() { return tide.surge; }
        @Override public boolean rising() { return Double.isNaN(hand) && tide.rising(hours()); }

        /** Die Tide für die Überflutung: von Hand ein fester Spiegel ohne Partialtiden. */
        public Tide floodTide() {
            double h = hand;
            if (Double.isNaN(h)) return tide;
            Tide t = new Tide();
            t.gauge = "von Hand";
            t.mean = h;
            t.seepage = tide.seepage;
            t.maxPuddle = tide.maxPuddle;
            return t;
        }

        @Override public double depthAt(double x, double z) {
            Flood f = flood;
            if (f != null && f.inside(x, z)) return f.depthAt(x, z);
            Terrain t = terrain;
            return t == null || !t.seaward(x, z) ? 0 : Math.max(0, level() - t.height(x, z));
        }

        @Override public double surfaceAt(double x, double z) {
            Flood f = flood;
            if (f != null && f.inside(x, z)) return f.surfaceAt(x, z);
            Terrain t = terrain;
            double g = t == null ? -100 : t.height(x, z);
            return t != null && t.seaward(x, z) ? Math.max(g, level()) : g;
        }

        @Override public boolean connected(double x, double z) {
            Flood f = flood;
            return f != null && f.inside(x, z) ? f.connectedAt(x, z) : depthAt(x, z) > 0;
        }

        @Override public double[] velocityAt(double x, double z) {
            Flood f = flood;
            return f == null ? new double[2] : f.velocityAt(x, z);
        }
    }

    /**
     * Phase 5: die Nässe des Watts aus {@code com.dan.mudflat}, sobald die App das Feld gebaut hat ({@link #field}); bis
     * dahin aus der Trockenzeit der Überflutung nach dem Material.
     */
    public static final class WattNaesse implements Wetness {
        public volatile com.dan.mudflat.WetField field;
        private final TidePegel level;

        WattNaesse(TidePegel level) { this.level = level; }

        @Override public double wetnessAt(double x, double z) {
            com.dan.mudflat.WetField f = field;
            if (f != null && f.index(x, z) >= 0) return f.wetnessAt(x, z);
            return level.depthAt(x, z) > 0.01 ? 1 : com.dan.mudflat.Mudflat.wadden().wetnessAfter(dryHoursAt(x, z), 0.5, 0.5);
        }

        @Override public double dryHoursAt(double x, double z) {
            Flood f = level.flood;
            return f != null && f.inside(x, z) ? f.dryHoursAt(x, z) : 0;
        }

        /** Sediment am Ort 0 Sand … 1 Schlick (0,3 ohne Feld). */
        public double sedimentAt(double x, double z) {
            com.dan.mudflat.WetField f = field;
            return f == null ? 0.3 : f.sedimentAt(x, z);
        }
    }

    /** Die Ära mit Überblendung: {@link #target} 0 = heute, 1 = 1362; {@link #step} führt nach (4 s für den ganzen Weg). */
    public static final class Zeitreise implements Era {
        public static final double SECONDS = 4;
        public volatile double target, blend;
        public void step(double dt) {
            double d = target - blend, s = Math.max(0, dt) / SECONDS;
            blend = Math.abs(d) <= s ? target : blend + Math.signum(d) * s;
        }
        public void toggle() { target = target < 0.5 ? 1 : 0; }
        @Override public double blend() { return blend; }
    }

    public final Clock clock;
    public final ManualWind wind = new ManualWind();
    public final TidePegel level;
    /** Phase 5: Nässe des Watts. */
    public final WattNaesse wetness;
    public final Zeitreise era = new Zeitreise();
    /** Phase 2: das Gelände (die App setzt es, sobald es geladen ist). */
    public volatile Terrain terrain;
    /** Phase 7: die Seehundbank (die App setzt sie) und der Störer (Wattwanderer). */
    public volatile Haulout haulout;
    public volatile Disturber disturber;
    /** Phase 4: das Gelände auch an die Tide (Ersatz, solange die Überflutungskarte fehlt). */
    public void setTerrain(Terrain t) { terrain = t; level.terrain = t; }
    private final List<Link> links = new ArrayList<>();

    // ------------------------------------------------------------------ Phase 3: Wetter
    /**
     * Klima „Nordfriesland“ [G]: Monatsmittel der Lufttemperatur an der Küste bei Husum (°C, Januar bis Dezember,
     * Mittel 1991–2020 gerundet). Die Regie legt damit ihre Lagen um das Mittel des Tags; Sommer um den 15. Juli.
     */
    public static final double[] NORDFRIESLAND = {1.5, 1.8, 4.0, 7.8, 11.8, 15.0, 17.3, 17.2, 14.3, 10.4, 6.0, 2.9};
    /** Phase 9: das Jahr aus {@code com.dan.season} mit dem Klima Nordfrieslands (Schnee, Reif, Salzwiese, Gras). */
    public final Jahreszeit season;
    /** Die Wetterregie mit dem Klima Nordfrieslands; Tagesgang in Ortszeit (Europe/Berlin). */
    public final Weather weather = new Weather();
    /** Ob die Regie die Eingänge setzt (die App schaltet sie ein; Werkzeuge und Prüfungen setzen die Felder selbst). */
    public volatile boolean regie;
    /** Wind in der laufenden Böe (m/s), Luftdruck (hPa) und Tendenz, Lufttemperatur (°C). */
    public volatile double gustNow = Beaufort.mid(4), pressureHPa = 1015, pressureTendency, airTempC = 17;
    /** Sicht in km (Nordseeluft), Regen in mm/h, Bedeckung in Achteln, hohe Schicht 0..1. */
    public volatile double visibilityKm = 30, rainRate = 0, oktas = 2.5, highCover = 0.05;
    public volatile CloudType cloudType = CloudType.CUMULUS;
    /** Gewitter 0..4 wie {@link Lightning.Level}. */
    public volatile Lightning.Level thunderstorm = Lightning.Level.OFF;
    /** Seenebel 0..1 aus der Regie; Nebel zeichnen (Schalter im Bedienfeld). */
    public volatile double fogAmount;
    public volatile boolean fogOn = true;
    /** Lage nach einem Schauer für einen Regenbogen (0..1); Regenbogen von Hand, negativ = aus der Regie. */
    public volatile double showerEnd, rainbowHand = -1;
    /** Zeichnet die App echte Wolken? Dann bekommt der Himmel keinen Schleier mehr. */
    public volatile boolean cloudsDrawn;
    /** Unter- und Obergrenze des Nebels zuletzt (m über dem Pegel; für HUD und Prüfung). */
    public volatile double fogBase, fogTop;

    /** Der Plan aus dem Werkbuch, Abschnitt 4: wer wem was gibt, ab welcher Phase. */
    private static final List<Link> PLANNED = List.of(
            new Link("Gelände heute und 1362", "tide, mudflat, forest, road, timber", "Terrain", 4));

    /**
     * Phase 17: was bis Phase 16 als „Phase 17“ geplant war und nicht in den Schliff kam, steht im Ideenspeicher des
     * Werkbuchs. Die Packages dahinter laufen in Rungholt nicht ({@code weathering}, {@code forest}) oder bekamen ihre
     * Daten schon anders ({@code ground} folgt dem Gelände, nicht der Nässe des Watts).
     */
    public static final List<Link> IDEENSPEICHER = List.of(
            new Link("tide (Wasser je Zelle, Trockenzeit)", "weathering (nasse Mauern nach der Flut)", "Water", 0),
            new Link("mudflat (Nässe, Trockenzeit, Sediment)", "ground, weathering", "Wetness", 0),
            new Link("season (Klima Nordfriesland)", "forest, mudflat (Eis im Watt)", "Season", 0),
            new Link("timber, App (Kirche, Häuser auf den Warften)", "rain, spray, weathering", "Collider, SurfaceHit", 0),
            new Link("App: tide.levelAt + wave.heightAt", "buoyancy (die Kogge in der Welle der Bresche)", "Sea (Summe)", 0));

    public Wiring(Clock clock) {
        this.clock = clock;
        this.level = new TidePegel(clock);
        this.wetness = new WattNaesse(level);
        this.season = new Jahreszeit();
        weather.zone = java.time.ZoneId.of("Europe/Berlin");
        weather.climate = NORDFRIESLAND.clone();
        weather.summerDay = 196;
        weather.seed = 1362;
        link("Uhr (SceneClock)", "Himmel-Platzhalter, Bühne, Kamera, HUD", "Clock (Datum, Sonne über Süden)", 1);
        link("App (Hand)", "Bühne (Platzhalter-Wellen)", "Wind", 1);
        link("Platzhalter-Pegel (M2 + M4)", "Bühne, Kamera, HUD", "Level", 1);
        link("App (Schalter Z)", "HUD (Gelände und Stadt ab Phase 10)", "Era", 1);
        link("Gelände heute (BAW-Küstenhöhen, 8 m)", "ocean (Grund, Maske der Köge), reed, ground, Kamera", "Terrain", 2);
        link("Platzhalter-Pegel", "ocean (Watt)", "Level als Wasserstand", 2);
        link("App (Hand)", "ocean (Watt), reed", "Wind", 2);
        link("weather (Klima Nordfriesland, Orkan aus WNW)", "Wind → ocean, reed, ground, cloud, rain, fog", "Wind, Böe", 3);
        link("weather", "cloud, rain, fog, sky, lightning", "Bedeckung, Art, Regen, Sicht, Seenebel, Gewitter", 3);
        link("Uhr", "sky", "Clock → Zeit, Breite, Länge (Sonne über Süden, Mond, Sterne über der Nordsee)", 3);
        link("sky", "Gelände, ocean, ground, reed, cloud, fog, rain", "Lighting (Sonne, Mond, Himmel, Dunst, vorbelichtet)", 3);
        link("sky, cloud", "Nachbearbeitung", "Augenanpassung, Stäbchensehen", 3);
        link("cloud", "Gelände, ocean, ground, reed", "Wolkenschatten (Karte um das Auge)", 3);
        link("cloud", "rain, lightning", "Regenfeld, Durchlässigkeit", 3);
        link("lightning", "cloud, ocean, Gelände", "Flash (Lichtpunkte)", 3);
        link("Gelände, Pegel", "lightning, fog, rain", "Terrain (Einschlag), Level (Nebel und Regen über dem Wasser)", 3);
        link("tide (Norderhever, 10 Partialtiden)", "ocean, Kamera, HUD, Bedienfeld", "Level (Pegel am Rand, Flut oder Ebbe, Kalender)", 4);
        link("weather (Wind gegen die Küste)", "tide", "Windstau (Orkan aus WNW: über 3 m)", 4);
        link("Gelände (BAW-Küstenhöhen)", "tide (Überflutungskarte)", "Terrain → Verbindungshöhe und Laufzeit je Zelle", 4);
        link("tide (Überflutung)", "ocean (Wasser als Textur), Gelände (Nässe am Saum), Kamera", "Water (Spiegel je Zelle, Pfützen)", 4);
        link("tide (Strömung)", "HUD (im Bild ab Phase 6)", "Current", 4);
        link("Gelände, tide (Laufzeit der Flut)", "mudflat (Sediment)", "Terrain → Sand seewärts und in Rinnen, Schlick in den Buchten", 5);
        link("tide (Überflutung)", "mudflat", "Water (Tiefe, Trockenzeit), Current (Rippel quer zur letzten Strömung)", 5);
        link("sky, cloud, weather", "mudflat (Trocknen)", "Lighting (Sonne am Boden), Wind, Lufttemperatur, Regen", 5);
        link("mudflat (gl/mudflat.glsl)", "Gelände", "Material des Watts (Albedo, Rippel, Wasserfilm, Rauigkeit)", 5);
        link("mudflat", "HUD, Bedienfeld", "Wetness (Nässe, Trockenzeit, Sediment am Ort)", 5);
        link("tide (Strömung)", "ocean (Kräuselung, Schaumstreifen, Trübung), mudflat (Höhe der Rippel)", "Current", 6);
        link("mudflat (Feinform, mudflat_relief.glsl)", "Gelände, Kamera", "Prielkante mit Abbruch, Rinnsale, Wellenrücken", 6);
        link("Kamera (Auge unter dem Spiegel)", "ocean (Unterseite, trübes Wasser)", "Level, Water", 6);
        link("lightning", "mudflat (Blitz im Wasserfilm über das Rundumbild)", "Flash", 6);
        link("App (Seehundbank am Priel westlich von Südfall [G])", "seal", "Haulout (Bank: Mitte, Halbmesser)", 7);
        link("tide (Wasser je Zelle), mudflat (Feinform)", "seal", "Water, Terrain (Wasserspiegel und Grund am Ort: kommen, liegen, gehen)", 7);
        link("App (Wattwanderer)", "seal", "Disturber (Fluchtdistanz 300 m [G])", 7);
        link("seal, Wattwanderer", "mudflat (Robbenspuren, Stiefelabdrücke bis zur nächsten Flut)", "Spuren (TrackMap)", 7);
        link("sky, cloud", "seal, Figur (Licht, Schlagschatten nach der Sonne)", "Lighting", 7);
        link("Uhr (Monat, Sonnenhöhe)", "seal (Heuler im Juni und Juli, Banane in der Sonne, nachts mehr im Wasser)", "Clock", 7);
        link("tide (Wasser je Zelle), mudflat (Feinform, Sediment)", "flock (Wattvögel: fressen am Saum, Schlick für die Brandgans)", "Water, Wetness, Terrain", 8);
        link("App (Vorland von Südfall und Nordstrand)", "flock (Hochwasserrastplätze, Weide der Ringelgans)", "Haulout", 8);
        link("App (Wattwanderer)", "flock (Fluchtdistanz je Art, der Schwarm wendet)", "Disturber", 8);
        link("weather, Uhr (Monat, Sonne)", "flock (Arten nach Monat, in den Wind, Möwen und Gänse rasten nachts)", "Wind, Clock", 8);
        link("season (Klima Nordfriesland, gewürfeltes Jahr)", "Gelände (Schnee auf Land und Hochwatt, Reif, Queller rot, Salzwiese winterbraun), reed, ground, timber (Dachschnee)", "Season", 9);
        link("season", "weather (Abweichung des Tags), rain (unter 2,5 °C als Schnee: SnowfallGl)", "Season, Lufttemperatur", 9);
        link("App (Wattwanderung nach Südfall)", "figure (Weg, Halte, Kleidung nach dem Monat)", "Clock, Route", 9);
        link("tide (Wasser je Zelle), mudflat (Feinform, Sediment)", "figure (Grund, nicht ins tiefe Wasser, im Schlick langsamer)", "Water, Wetness, Terrain", 9);
        link("figure (Fußspuren: barfuß oder Stiefel)", "mudflat (Spuren bis zur nächsten Flut)", "Spuren", 9);
        link("figure (die Gruppe)", "seal, flock", "Disturber", 9);
        link("App (Land1362: Marsch, Warften, Seedeich, Siel, Gräben [G])", "Gelände (zweite Höhen- und Artenkarte, Überblendung)", "Terrain 1362", 10);
        link("Era (Überblendung 4 s)", "Gelände, ocean (Grund, Maske), mudflat, reed, ground (Salzwiese, Marsch), Kamera, HUD", "Era", 10);
        link("Gelände 1362", "tide (eigene Überflutungskarte: bei Springflut Wasser am Deich, nicht dahinter)", "Terrain → Verbindungshöhe", 10);
        link("tide (Überflutung 1362)", "ocean, mudflat (Watt vor dem Deich), Kamera", "Water", 10);
        link("App (Stadt1362: Häuser auf den Warften, Kirche [G]), Gelände 1362", "timber (Häuser, Kirche aus Backstein, Kaje und Boote)", "Terrain 1362 (Grund der Häuser)", 11);
        link("tide (Überflutung 1362: Spiegel und Strömung), Gelände 1362 (Grund)", "buoyancy (die Kogge schwimmt, treibt, fällt trocken)", "Sea (height, velocity, bed)", 11);
        link("tide (Pegel, Flut oder Ebbe)", "Sielhafen → buoyancy.Cog (mit der Flut ein, zum nächsten Hochwasser aus)", "Level", 11);
        link("timber (Rauchlöcher), weather (Wind), sky (Sonne), cloud (Bedeckung)", "smoke (Herdrauch über der Stadt)", "Source, Wind, Stabilität", 11);
        link("App (die Leute von Rungholt), Uhr (Monat, Sonne)", "figure (Kleidung von 1362, Gang auf der Kette, drinnen bei Nacht)", "Clock, Terrain", 11);
        link("App (Mandraenke: Windstau nach Drehbuch, 3,6 m zum Hochwasser der Nacht)", "tide (Pegel am Rand)", "Windstau", 12);
        link("weather (Lage Orkan aus WNW)", "ocean, cloud, rain, smoke, reed (die Sturmnacht)", "Wind, Regen", 12);
        link("tide (Pegel vor dem Deich), Gelände 1362 (Grund)", "fracture (Deichbruch aus Klei: Wasserdruck, Strömung in der Bresche, Grund)", "Water, Current, Ground", 12);
        link("fracture (Bresche offen)", "Gelände 1362 (ausgehoben), tide (Überflutung neu), wave (Dammbruch mit gehaltener See)", "Terrain", 12);
        link("wave (Flut durch die Bresche)", "ocean (lange Welle im feinen Gitter), Häuser in der Flut", "Water, Current", 12);
        link("App (Polder: Zu- und Abfluss über Bresche und Krone)", "tide (Becken mit eigenem Spiegel)", "Water", 12);
        link("tide, wave (Spiegel, Strömung)", "timber (Häuser schwimmen auf, treiben, zerfallen), figure (Flucht auf die Kirchwarft)", "Water, Current", 12);
        link("App (Land1362: Warften, Kirche, Gräben, Sielzüge, Seedeich, Siel [G])", "Gelände heute (Spurenkarte: Kleirücken, Kulturschicht, Brunnen, Magnetik), Kamera", "Terrain (Spuren)", 13);
        link("tide (Pegel mit Laufzeit, Verbindungshöhe), sky (Sonne), weather (Wind)", "Grabung (figure: Archäologen kommen bei Ebbe, gehen vor der Flut)", "Level, Water, Clock, Wind", 13);
        link("Grabung (die Archäologen)", "seal, flock (Störer), mudflat (Stiefelabdrücke bis zur Flut)", "Disturber, Spuren", 13);
        link("Gelände 1362 (Kette, Hauptsielzug)", "road (Kleiweg auf der Warftenkette, Pfad am Sielzug, ohne Verkehr)", "Terrain", 13);
        link("Era (Überblendung 4 s)", "timber (Häuser steigen aus dem Boden), reed (Gras wächst über den Sand), Vieh, Leute, Rauch zuletzt, Kamera (auf der Spur)", "Era", 13);
        link("weather (Wind, Richtung), sky (Sonne), tide (Wasser über der Kirchwarft)", "Glocken (Sturmnacht nach der Sage, Schemen der Kirche aus timber)", "Wind, Lighting, Water", 13);
        // Phase 14: der Klang (App: gl.Klang, Ohr = Kamera)
        link("weather (Wind, Böen, Regen, Temperatur)", "soundscape (Wind am Ohr, Regen, der Wind trägt den Schall oder verschluckt ihn)", "Wind", 14);
        link("ocean (Seegang), tide (offenes Wasser in der Nähe)", "soundscape (See nach Abstand, Brandung an Deich und Warft)", "Sea, Water", 14);
        link("tide (Trockenzeit je Zelle), Gelände (Watt)", "soundscape (Knistern des frisch trockengefallenen Watts)", "Water, Wetness", 14);
        link("tide (Strömung der Überflutung)", "soundscape (Priele: Rauschen und Glucksen an der Kante)", "Current", 14);
        link("seal (Rufe), flock (Rufe nach Art, Schwärme), lightning (Donner)", "soundscape", "SoundEvent", 14);
        link("App 1362 (Herdfeuer aus smoke, Vieh, Kaje, Glocke zu den Stunden [G])", "soundscape (Feuer, Rufe, Werkstätten, Leute, Wellen an Pfählen)", "SoundEvent", 14);
        link("Mandränke (Wasser an der Krone), fracture (Bruch, Bresche)", "soundscape (Brecher, Einsturz, Brocken, das Wasser durch die Bresche, Sturmläuten)", "Runup, Crack, Impact", 14);
        link("Glocken (Sage: Läuten in Sturmnächten)", "soundscape (Glocken aus dem Watt, dumpf, vom Wind getragen)", "SoundEvent", 14);
        // Phase 15: die Regiebücher als Text, die Prielfahrt, der Kinomodus
        link("Regie (Regiebücher als Text: zeit, raffer, wetter, sturm, ansicht, fahrt, aera, mandraenke, morgen, glocken, grabung, magnetik, warte)",
                "Uhr, Wetterregie, Kamera, Ära, Mandränke, Glocken, Grabung, Magnetik; warte auf Tide und Nacht", "Clock, Wind, Era, Level", 15);
        link("Prielfahrt (das Boot im Priel, Kamera 1,6 m über dem Wasser der Tide)", "seal, flock (Störer)", "Disturber, Water", 15);
        link("kino (Kinomodus: Balken, Blenden, Untertitel aus den Stichworten)", "Bild über der Nachbearbeitung, soundscape (Lautstärke folgt dem Schwarz), Fenster", "Regie", 15);
        // Phase 16: die Datenbank (Schema RUNGHOLT oder daten/)
        link("store (RH_PEGELORT, RH_PARTIALTIDE)", "tide (Partialtiden am Rand und in Husum; der Code ist der Rückfall)", "Level", 16);
        link("tide (Wenden am Rand, Überflutung), seal, flock", "store (Chronik: RH_TIDE mit Wasserlinie als Mehrfachfläche, RH_ZAEHLUNG)", "Water, Haulout", 16);
        link("Mandränke, Glocken, Regie", "store (Chronik: RH_MANDRAENKE_LAUF mit Bruchstelle und Bresche, RH_GLOCKEN, RH_REGIELAUF)", "Clock, Era", 16);
        link("store (RH_WARFT, RH_FUNDSTELLE, RH_TIDE)", "Warftkarte und Zellkarte im Bild, eigene Fundstelle mit Umschalt + Klick", "Terrain (Spuren)", 16);
        link("alle Packages (öffentliche Felder)", "store (Zustand der Sitzung, gemerkte Ansichten, Presets; „Weiter wie zuletzt“)", "Felder", 16);
        // Phase 17: die Gischt über der Deichkrone in der Mandränke (offen seit Phase 12)
        link("weather", "spray", "Wind (Fahnen des Orkans über der Krone)", 17);
        link("App: tide.levelAt + Windstau der Mandränke", "spray", "Sea (Spiegel vor dem Deich)", 17);
        link("Mandränke (Wellen über der Krone), fracture, wave", "spray", "Impact, Runup (Brecher an der Seeseite der Krone)", 17);
    }

    // ------------------------------------------------------------------ Phase 3

    /**
     * Phase 4: der Windstau folgt dem Wind (Szenenzeit in Stunden; Zeitkonstante 3 h). Der Wind kommt aus der Regie
     * oder von Hand.
     */
    /** Phase 12: Windstau nach Drehbuch (die Mandränke); NaN = aus dem Wind. */
    public volatile double surgeScript = Double.NaN;

    public void feedTide(double sceneHours) {
        double sc = surgeScript;
        if (!Double.isNaN(sc)) { level.tide.surge = sc; level.husum.surge = sc; return; }
        double target = Tide.surgeTarget(wind.speed(), wind.fromDeg());
        if (sceneHours > 6) level.tide.surge = target;   // nach einem großen Sprung gleich am Ziel
        else level.tide.stepSurge(target, Math.max(0, sceneHours));
        level.husum.surge = level.tide.surge;
    }

    /** Je Bild: Wind (in Schritten, weil das Meer sein Spektrum bei jeder Änderung neu baut) und Pegel an das Meer. */
    public void feed(com.dan.ocean.Ocean o) {
        double ws = Math.min(wind.speed(), 28), wd = wind.fromDeg();   // höchstens Bft 10 im Watt (flach, kurzer Fetch)
        if (Math.abs(ws - o.windSpeed) >= 0.5 || (ws < 0.5) != (o.windSpeed < 0.5)) o.windSpeed = Math.round(ws * 2) / 2.0;
        if (Math.abs(((wd - o.windFromDeg) % 360 + 540) % 360 - 180) >= 5) o.windFromDeg = Math.round(wd / 5) * 5.0 % 360;
        o.waterLevel = level.level();
    }

    /** Je Bild: Wind und Böigkeit an die Halme (mit der Böe des Augenblicks, wenn die Regie läuft). */
    public void feed(com.dan.reed.Reed r) {
        r.windSpeed = regie ? gustNow : wind.speed();
        r.windFromDeg = wind.fromDeg();
        r.gust = wind.gustiness() + 0.1;
    }

    /** Die Regie einen Schritt weiter (Szenenzeit aus der Uhr, Böen in Echtzeit) und ihre Werte übernehmen. */
    public Weather.Snapshot feedWeather(double realDt) {
        Weather.Snapshot s = weather.update(clock.epochMillis() / 1000.0, realDt);
        apply(s);
        return s;
    }

    /** Handregler: eine Größe der Regie sofort setzen und halten, bis „Regie weiter“. */
    public void hand(Weather.Var v, double value) {
        weather.set(v, value);
        Weather.Snapshot s = weather.snapshot();
        if (s != null) apply(s);
    }

    /** „Regie weiter“: alle Handregler lösen. */
    public void resumeRegie() {
        weather.release();
        rainbowHand = -1;
    }

    /** Freie Regie ab jetzt, beginnend mit einer Lage (etwa dem Orkan). */
    public void startFree(Weather.Phase p) {
        weather.startFree(clock.epochMillis() / 1000.0, p);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    /** Ein Szenario der Regie ab jetzt. */
    public void start(com.dan.weather.Scenario s) {
        weather.start(s, clock.epochMillis() / 1000.0);
        apply(weather.update(clock.epochMillis() / 1000.0, 0));
    }

    private void apply(Weather.Snapshot s) {
        if (s == null) return;
        wind.speed = s.wind();
        wind.fromDeg = s.fromDeg();
        wind.gust = s.gustiness();
        gustNow = s.gustNow();
        oktas = s.oktas();
        highCover = s.high();
        cloudType = CloudType.valueOf(s.cloud().name());
        rainRate = Math.max(0, s.rain());
        visibilityKm = Math.max(0.2, s.visibilityKm());
        fogAmount = s.fog();
        thunderstorm = Lightning.Level.values()[Math.max(0, Math.min(4, s.thunder()))];
        airTempC = s.temperature();
        pressureHPa = s.pressure();
        pressureTendency = s.tendency();
        showerEnd = s.showerEnd();
    }

    /** Dunst als Auslöschung je m aus der Sicht (Koschmieder). */
    public double hazePerM() { return 2.996 / (Math.max(0.5, visibilityKm) * 1000); }

    /**
     * Dem Himmel Zeit, Ort, Augenhöhe über dem Meer (NHN ≈ Meereshöhe), Sicht und Bedeckung (je Bild, vor
     * {@code sky.step}). Rückstrahlung des Bodens [G]: nasses Watt und Wasser dunkel, Salzwiese und Köge grün, um 0,10.
     */
    public void feed(Sky sky, double eyeAsl) {
        sky.epochMillis = clock.epochMillis();
        sky.latDeg = clock.latDeg();
        sky.lonDeg = clock.lonDeg();
        sky.eyeHeight = Math.max(1, eyeAsl);
        sky.visibilityKm = visibilityKm;
        sky.groundAlbedo = 0.10;
        sky.cloudCover = cloudsDrawn ? 0 : oktas / 8;
        sky.cloudVeil = !cloudsDrawn;
        Sky.State st = sky.state();
        double alt = st != null ? st.sunAlt : 10;
        double low = smooth(1, 6, alt) * (1 - smooth(36, 42, alt));
        sky.rainbow = rainbowHand >= 0 ? rainbowHand : 0.85 * showerEnd * low;
    }

    /** Den Wolken Bedeckung, Art, hohe Schicht und den Wind in Wolkenhöhe (1,6-fach, 20° rechtsdrehend) [G]; keine Lentikularis an der Küste. */
    public void feed(Clouds c) {
        c.oktas = oktas;
        c.type = cloudType;
        c.highCover = highCover;
        c.windSpeed = Math.max(3, wind.speed() * 1.6);
        c.windFromDeg = (wind.fromDeg() + 20) % 360;
        c.lens = 0;
    }

    /** Dem Regen Rate, Wind, Wolkenuntergrenze, Pegel und Auge; mit gezeichneten Wolken nur unter ihren dichten Stellen. */
    public void feed(Rain r, Clouds c, Clouds.Snapshot cs, boolean cloudsShown, double[] eye) {
        double rate = Math.max(0, rainRate);
        c.precipitation = rate > 0 ? Math.min(1, 0.35 + rate / 25) : 0;
        r.rate = rate;
        r.windSpeed = regie ? gustNow : wind.speed();
        r.windFromDeg = wind.fromDeg();
        r.gust = wind.gustiness();
        r.waterLevel = level.level();
        r.cloudBase = cs.type().base;
        r.eyeX = eye[0];
        r.eyeY = eye[1];
        r.eyeZ = eye[2];
        if (!cloudsShown || rate <= 0) { r.curtain = null; return; }
        double p = Math.max(1e-3, cs.precipitation());
        r.curtain = (x, z) -> Math.min(1, Clouds.rain(cs, x, z) / p * 2);
    }

    /** Dem Gewitter Stärke, Wolken, Boden (Gelände oder Wasser) und Ohr (je Bild, vor {@code lightning.step}). */
    public void feed(Lightning l, Clouds.Snapshot cs, boolean cloudsShown, double[] eye) {
        if (l.level() != thunderstorm) l.setLevel(thunderstorm);
        l.cx = eye[0];
        l.cz = eye[2];
        l.radius = 16_000;
        l.listenerX = eye[0];
        l.listenerY = eye[1];
        l.listenerZ = eye[2];
        double lv = level.level();
        l.groundLevel = lv;
        Terrain t = terrain;
        l.ground = t == null ? null : (x, z) -> t.seaward(x, z) ? Math.max(lv, t.height(x, z)) : t.height(x, z);
        l.temperatureC = airTempC;
        if (cloudsShown && cs != null) {
            double base = cs.type().base, top = base + cs.type().maxThickness;
            l.cloudBase = base;
            l.cells = (x, z) -> Math.max(Clouds.rain(cs, x, z) * 1.5, Clouds.coverage(cs, x, z) * 0.35);
            l.occlusion = (px, py, pz, ex, ey, ez) -> cloudTransmittance(cs, px, py, pz, ex, ey, ez, base, top);
        } else {
            l.cloudBase = 1200;
            l.cells = null;
            l.occlusion = null;
        }
    }

    /** Durchlässigkeit der Wolken zwischen Kanal und Auge (zehn Proben in der Schicht, halbe Auslöschung; wie Haithabu). */
    static double cloudTransmittance(Clouds.Snapshot cs, double px, double py, double pz, double ex, double ey, double ez, double base, double top) {
        double dx = ex - px, dy = ey - py, dz = ez - pz, len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1) return 1;
        double t0 = 0, t1 = 1;
        if (Math.abs(dy) > 1e-6) {
            double ta = (base - py) / dy, tb = (top - py) / dy;
            t0 = Math.max(0, Math.min(ta, tb));
            t1 = Math.min(1, Math.max(ta, tb));
        } else if (py < base || py > top) return 1;
        if (t1 <= t0) return 1;
        double tau = 0, step = (t1 - t0) / 10;
        for (int i = 0; i < 10; i++) {
            double t = t0 + (i + 0.5) * step;
            tau += Clouds.density(cs, px + dx * t, py + dy * t, pz + dz * t, false) * step * len;
            if (tau > 20) break;
        }
        return Math.exp(-0.5 * tau);
    }

    /**
     * Dem Seenebel über dem Watt Menge, Sicht, Bedeckung, Höhe und Untergrenze [G]: Nebel n (0..1) gibt die Menge (ab
     * 0,02 bis 0,6 voll), die Sicht in der Bank 3 km · (50/3000)^(n^0,6), die Obergrenze 15 + 90 n² m über dem Pegel
     * (Warften und Deiche ragen aus dünnem Nebel nicht heraus, Kirchtürme schon). Steigt die Sonne über 2°, hebt er sich;
     * Wind über 6 m/s reißt ihn auf.
     */
    public void feed(Fog f, double sunAltDeg) {
        double n = Math.max(0, Math.min(1, fogAmount));
        double windCut = 1 - smooth(6, 11, wind.speed());
        double lift = smooth(2, 16, sunAltDeg);
        double top = (15 + 90 * n * n) * (1 + 0.6 * lift);
        f.amount = fogOn ? smooth(0.02, 0.6, n) * windCut : 0;
        f.visibilityM = 3000 * Math.pow(50 / 3000.0, Math.pow(n, 0.6));
        f.coverage = Math.min(1, 0.55 + 0.6 * n);
        f.top = top;
        f.base = 0.85 * top * lift;
        f.bankSize = 2500;
        f.wisps = 0.4;
        f.windSpeed = wind.speed();
        f.windFromDeg = wind.fromDeg();
        f.waterLevel = level.level();
        f.hazeVisibilityM = visibilityKm * 1000;
        fogTop = top;
        fogBase = f.base;
    }

    /** Licht des Himmels als Schnittstelle für andere Packages (Lighting). */
    public static Lighting lighting(Sky sky) {
        return new Lighting() {
            Sky.State st() { Sky.State s = sky.state(); return s != null ? s : sky.update(); }
            @Override public double[] sunDir() { return st().sunDir.clone(); }
            @Override public double[] moonDir() { return st().moonDir.clone(); }
            @Override public double[] sunColor() { return st().sunColor.clone(); }
            @Override public double[] moonColor() { return st().moonColor.clone(); }
            @Override public double[] skyAmbient() { return st().skyIrradiance.clone(); }
            @Override public double haze() { return st().groundExtinction; }
        };
    }

    /** „Wind Bft 7 aus 285° (15,6 m/s, Böe 19,2) · freie Regie, Sturm“ */
    public String weatherLine() {
        Weather.Snapshot s = weather.snapshot();
        String src = !regie || s == null ? "von Hand" : (s.scenario().isEmpty() ? "freie Regie, " + s.phase().label : s.scenario() + ": " + s.stepText())
                + (s != null && regie && weather.overrides() > 0 ? ", " + weather.overrides() + " von Hand" : "");
        return String.format(Locale.GERMAN, "Wind Bft %d aus %03.0f° (%.1f m/s, Böe %.1f) · %s", wind.beaufort(), wind.fromDeg(), wind.speed(),
                regie ? gustNow : wind.speed(), src);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Eine Verbindung eintragen (das Bedienfeld zeigt die Liste). */
    public void link(String from, String to, String via, int phase) { links.add(new Link(from, to, via, phase)); }

    public List<Link> links() { return Collections.unmodifiableList(links); }

    /** Was nach dem Phasenplan noch kommt (ohne das, was schon verbunden ist). */
    public static List<Link> planned() { return PLANNED; }

    /** Die Schnittstellen des Pakets, wie sie im Werkbuch stehen (Prüfung). */
    public static final String[] INTERFACES = {"Clock", "Wind", "Era", "Terrain", "Lighting", "Flash", "Level", "Water", "Wetness", "Current",
            "Haulout", "Disturber", "Collider", "SurfaceHit", "Sea", "Impact", "Crack", "Runup", "Season", "SoundEvent"};

    /** „7 Verbindungen, 13 geplant“ */
    public String summary() {
        return String.format(Locale.GERMAN, "%d %s, %d geplant", links.size(), links.size() == 1 ? "Verbindung" : "Verbindungen", PLANNED.size());
    }

    // ------------------------------------------------------------------ Phase 9: das Jahr

    /**
     * Phase 9: die Jahreszeit aus {@code com.dan.season} ({@link com.dan.season.Year}) mit dem Klima Nordfrieslands
     * ({@link com.dan.season.Climate#nordfriesland()}). Ein Sprung im Kalender rechnet den Winter aus dem gewürfelten Jahr
     * vor (Startwert 26: im Januar 2026 ein Wintereinbruch mit rund 17 cm Schnee am 21. [G]); je Schritt gehen
     * Lufttemperatur, Niederschlag, Sonne, Bedeckung und Wind der Regie ein. Läuft die Regie nicht, gelten das Tagesmittel
     * des gewürfelten Jahrs mit einem Tagesgang und kein Niederschlag. Gibt Schnee, Reif und den Stand der Pflanzen an
     * Gelände (Deich, Vorland, Koog, Warft), Halme und Bodendecke; die Abweichung des Tags an die Regie
     * ({@code weather.anomaly}), damit ihr Wetter zum gewürfelten Winter passt.
     */
    public final class Jahreszeit implements Season {
        public final com.dan.season.Year year = new com.dan.season.Year(com.dan.season.Climate.nordfriesland());
        private volatile com.dan.season.Year.State state;
        { year.seed = 26; }

        /** Um sceneDt Sekunden weiter (oder neu vorrechnen nach einem Sprung). */
        public com.dan.season.Year.State step(double sceneDt) {
            java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli((long) clock.epochMillis()).atZone(java.time.ZoneId.of("Europe/Berlin"));
            int y = z.getYear(), doy = com.dan.season.Year.dayOfYear(z.getMonthValue(), z.getDayOfMonth());
            double hour = z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
            double air, precip;
            if (regie) { air = airTempC; precip = rainRate; }
            else { air = year.dayMean(y, doy) + 3.0 * Math.sin(2 * Math.PI * (hour - 9) / 24); precip = 0; }
            double sunAlt = com.dan.season.Year.sunAltitude(doy, hour);
            com.dan.season.Year.State st = year.step(Math.max(0, sceneDt), y, doy, hour, air, precip, sunAlt, oktas, wind.speed());
            state = st;
            weather.anomaly = year.anomaly(y, doy);
            return st;
        }

        public com.dan.season.Year.State state() { return state; }

        /**
         * Phase 16: einen milden Winter einstellen (Verschiebung aller Temperaturen in K, 0 = wie gewürfelt) und das Jahr
         * bis jetzt neu vorrechnen; die Mandränke nimmt +5 K (Orkan aus WNW: Regen bei rund 5 °C, kein Schnee).
         */
        public void mild(double shiftK) {
            year.shift = shiftK;
            java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli((long) clock.epochMillis()).atZone(java.time.ZoneId.of("Europe/Berlin"));
            state = year.settle(z.getYear(), com.dan.season.Year.dayOfYear(z.getMonthValue(), z.getDayOfMonth()), z.getHour() + z.getMinute() / 60.0);
        }

        @Override public double foliage() { com.dan.season.Year.State s = state; return s == null ? 1 : s.foliage(); }
        @Override public double autumn() { com.dan.season.Year.State s = state; return s == null ? 0 : s.autumn(); }
        @Override public double snowDepth() { com.dan.season.Year.State s = state; return s == null ? 0 : s.snowDepth() * s.snowCover(); }
        @Override public double iceReach() { return 0; }
        @Override public double temperature() { com.dan.season.Year.State s = state; return s == null ? 10 : s.airTemp(); }
        @Override public boolean pupping() {
            int m = java.time.Instant.ofEpochMilli((long) clock.epochMillis()).atZone(java.time.ZoneId.of("Europe/Berlin")).getMonthValue();
            return m == 6 || m == 7;
        }

        /** Reif 0..1, Schilf 0 Sommer … 1 Winter, Gras 0..1 (saftig). */
        public double frost() { com.dan.season.Year.State s = state; return s == null ? 0 : s.frost(); }
        public double reedSeason() { com.dan.season.Year.State s = state; return s == null ? 0.25 : s.reedSeason(); }
        public double grass() { com.dan.season.Year.State s = state; return s == null ? 1 : s.grassLush(); }

        /**
         * Färbung der Salzwiese 0..1 nach dem Tag [G]: der Queller wird ab Ende August rot (am stärksten Anfang Oktober),
         * im Winter liegt das Vorland braungrau; im Mai treibt es grün aus.
         */
        public double marshRed() {
            com.dan.season.Year.State s = state;
            int d = s == null ? 200 : s.doy();
            return smooth(235, 280, d) * (1 - smooth(300, 335, d));
        }

        public double marshWinter() {
            com.dan.season.Year.State s = state;
            int d = s == null ? 200 : s.doy();
            return Math.max(1 - smooth(95, 140, d), smooth(300, 345, d));
        }

        private double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }

        /** „Schnee 12 cm · Reif · Salzwiese rot“ */
        public String describe() {
            com.dan.season.Year.State s = state;
            if (s == null) return "Jahr: noch nicht gerechnet";
            String d = s.snowDepth() >= 0.005 ? String.format(Locale.GERMAN, "Schnee %.0f cm%s", s.snowDepth() * 100, s.snowWet() > 0.5 ? " (nass)" : "") : "schneefrei";
            if (s.frost() > 0.3) d += " · Reif";
            double r = marshRed(), w = marshWinter();
            d += r > 0.4 ? " · Queller rot" : w > 0.6 ? " · Salzwiese winterbraun" : " · Salzwiese grün";
            return String.format(Locale.GERMAN, "Jahr: %s · Luft %.1f °C (Klima %.1f °C)", d, s.airTemp(), year.climate.meanTemp(s.doy()));
        }
    }
}
