package com.dan.rungholt.data;

import com.dan.rungholt.camera.Ansichten;
import com.dan.rungholt.scene.Land1362;
import com.dan.rungholt.scene.Mandraenke;
import com.dan.rungholt.scene.Regie;
import com.dan.rungholt.scene.Site;
import com.dan.rungholt.scene.Spuren;
import com.dan.rungholt.scene.Stadt1362;
import com.dan.rungholt.scene.Terrain;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.tide.Tide;
import com.dan.weather.Weather;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die mitgelieferten Daten (Phase 16, Werkbuch Abschnitt 8, F25–F36 „alle A“): Quellen, Befunde und Sturmfluten aus
 * {@code quellen.txt}, {@code befunde.txt} und {@code sturmfluten.txt} in diesem Package; Pegelorte mit Partialtiden
 * und der Tidenkalender aus {@code com.dan.tide.Tide}; Warften, Bauwerke und Fundstellen aus {@code scene.Land1362},
 * {@code scene.Stadt1362} und {@code scene.Spuren}; Wetterlagen, Regiebücher, Ansichten, Fahrten und die Presets der
 * Packages aus dem Code; die beiden Gelände aus {@code daten/gelaende}. Daraus schreibt {@link StartdatenSql} das Skript
 * {@code db/sql/04_startdaten.sql}; ohne Datenbank liest {@link DateiSpeicher} dieselben Listen.
 */
public final class Startdaten {
    private Startdaten() { }

    // ------------------------------------------------------------------ Textdateien im Package

    static List<String[]> zeilen(String datei) {
        try (InputStream in = Startdaten.class.getResourceAsStream(datei)) {
            if (in == null) throw new IllegalStateException(datei + " fehlt im Package com.dan.rungholt.data");
            List<String[]> l = new ArrayList<>();
            for (String z : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                if (z.isBlank() || z.startsWith("#")) continue;
                String[] p = z.split("\\|", -1);
                for (int i = 0; i < p.length; i++) p[i] = p[i].strip();
                l.add(p);
            }
            return l;
        } catch (IOException e) {
            throw new IllegalStateException(datei + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static List<String> liste(String s) {
        List<String> l = new ArrayList<>();
        for (String x : s.split(",")) if (!x.isBlank()) l.add(x.strip());
        return l;
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }

    private static Integer ganz(String s) { return s == null || s.isBlank() ? null : Integer.valueOf(s.strip()); }

    public static List<Daten.Quelle> quellen() {
        List<Daten.Quelle> l = new ArrayList<>();
        for (String[] p : zeilen("quellen.txt"))
            l.add(new Daten.Quelle(p[0], p[3], leer(p[4]), p[1], p[2].isEmpty() ? null : LocalDate.parse(p[2]), leer(p[5])));
        return l;
    }

    public static List<Daten.Befund> befunde() {
        List<Daten.Befund> l = new ArrayList<>();
        for (String[] p : zeilen("befunde.txt")) l.add(new Daten.Befund(p[0], p[1], p[2], p[3], liste(p.length > 4 ? p[4] : "")));
        return l;
    }

    /** Die belegten Sturmfluten (F32 A), recherchiert am 20.10.2026. */
    public static List<Daten.Sturmflut> sturmfluten() {
        List<Daten.Sturmflut> l = new ArrayList<>();
        for (String[] p : zeilen("sturmfluten.txt"))
            l.add(new Daten.Sturmflut(p[0], p[1], Integer.parseInt(p[2]), ganz(p[3]), ganz(p[4]), p[5], ganz(p[6]), leer(p[7]), leer(p[8]), leer(p[9])));
        return l;
    }

    // ------------------------------------------------------------------ Pegelorte und Tidenkalender (F26, F27)

    /** Kurzzeichen der Pegelorte; NORDERHEVER ist der Rand der Szene. */
    public static final String HUSUM = "HUSUM", PELLWORM = "PELLWORM", RAND = "NORDERHEVER";

    /** Lage der Pegel (Länge, Breite), gerundet [G]. */
    static final double[] HUSUM_LL = {9.0256, 54.4722}, PELLWORM_LL = {8.6985, 54.5010};
    /** Der Rand der Szene in der Norderhever, Szenenmeter [G]. */
    static final double[] RAND_XZ = {-3500, 2600};

    static Daten.Pegelort pegelort(String name, String kurz, double[] xz, Tide t, double versatz, double hub, boolean szene, String text, String quelle) {
        List<Daten.Partialtide> p = new ArrayList<>();
        for (Tide.Constituent c : t.constituents) p.add(new Daten.Partialtide(c.name(), c.speedDegPerHour(), c.amplitude(), c.phaseDeg()));
        return new Daten.Pegelort(name, kurz, xz[0], xz[1], t.mean, versatz, hub, szene, text, quelle, p);
    }

    /** Husum, Pellworm und der Rand (Norderhever) mit ihren Partialtiden aus dem Code (F26). */
    public static List<Daten.Pegelort> pegelorte() {
        double h = 3.3 / 3.5;
        return List.of(
                pegelort("Husum", HUSUM, Daten.xz(HUSUM_LL[0], HUSUM_LL[1]), Tide.husum(), 0, 1, false,
                        "Pegel Husum: zehn Partialtiden aus dem Gezeitenkalender 2026 (475 Zeiten), Mittel so, dass MThw NHN +1,60 m, MTnw −1,90 m; Lage gerundet [G]",
                        "GZK26"),
                pegelort("Pellworm [G]", PELLWORM, Daten.xz(PELLWORM_LL[0], PELLWORM_LL[1]), Tide.pellworm(), -8, h, false,
                        "Husum 8 min früher, Hub 3,3 statt 3,5 m (Vergleich zweier Vorhersagen, nicht amtlich); Lage gerundet [G]", "TIDEFC"),
                pegelort("Norderhever [G]", RAND, RAND_XZ, Tide.hever(), -27, h, true,
                        "Der seeseitige Rand der Szene: Husum 27 min früher, Hub wie Pellworm; von hier läuft die Flut über das Watt [G]", "APP"));
    }

    /** Eine Tide aus einem Pegelort (wie die App sie aus der Datenbank baut). */
    public static Tide tide(Daten.Pegelort p) {
        Tide t = new Tide();
        t.gauge = p.name();
        t.mean = p.mittelM();
        for (Daten.Partialtide c : p.tiden()) t.add(c.name(), c.gradJeH(), c.amplitudeM(), c.phaseGrad());
        return t;
    }

    /** Die Jahre des Tidenkalenders und der Monat 1362 (F27). */
    public static final int KALENDER_VON = 2026, KALENDER_BIS = 2027;

    /** Hoch- und Niedrigwasser eines Pegels zwischen zwei Tagen (von einschließlich, bis ausschließlich), deutsche Zeit. */
    public static List<Daten.Kalender> kalender(Daten.Pegelort p, LocalDate von, LocalDate bis) {
        Tide t = tide(p);
        double a = Daten.beginn(von) / 3_600_000.0, b = Daten.beginn(bis) / 3_600_000.0;
        List<Daten.Kalender> l = new ArrayList<>();
        for (Tide.Turn x : t.extremes(a, b)) {
            long ms = Math.round(x.hours() * 60) * 60_000L;   // auf die Minute
            l.add(new Daten.Kalender(p.kurz(), ms, x.high() ? "HW" : "NW", Math.round(x.level() * 1000) / 1000.0,
                    Math.round(t.springFactor(x.hours()) * 1000) / 1000.0));
        }
        return l;
    }

    /** Der ganze mitgelieferte Tidenkalender: Husum und Rand 2026–2027, der Rand im Januar 1362. */
    public static List<Daten.Kalender> kalender() {
        List<Daten.Kalender> l = new ArrayList<>();
        for (Daten.Pegelort p : pegelorte()) {
            if (p.kurz().equals(PELLWORM)) continue;
            l.addAll(kalender(p, LocalDate.of(KALENDER_VON, 1, 1), LocalDate.of(KALENDER_BIS + 1, 1, 1)));
            if (p.szene()) l.addAll(kalender(p, LocalDate.of(1362, 1, 1), LocalDate.of(1362, 2, 1)));
        }
        return l;
    }

    // ------------------------------------------------------------------ Rungholt (F30, F31)

    /** Art einer Warft aus ihrem Namen. */
    public static String art(Land1362.Warft w) {
        return w.name().startsWith("Kirchwarft") ? "KIRCHWARFT" : w.name().startsWith("Warft ") ? "KETTE" : "HOF";
    }

    /** Die nächste Warft (elliptischer Abstand, 1 = Rand der Krone) zu (x, z): Index in {@link Land1362#warften()}. */
    static int naechste(double x, double z) {
        List<Land1362.Warft> ws = Land1362.warften();
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < ws.size(); i++) {
            Land1362.Warft w = ws.get(i);
            double h = Math.toRadians(w.headingDeg()), ax = Math.sin(h), az = -Math.cos(h);
            double dx = x - w.x(), dz = z - w.z(), u = dx * ax + dz * az, v = -dx * az + dz * ax;
            double r = Math.sqrt(u * u / (w.a() * w.a()) + v * v / (w.b() * w.b()));
            if (r < bd) { bd = r; best = i; }
        }
        return best;
    }

    private static List<Daten.Warft> warften;

    /** Die 54 Warften mit Häusern (aus scene.Stadt1362) und Sodenbrunnen (aus scene.Spuren). */
    public static synchronized List<Daten.Warft> warften() {
        if (warften != null) return warften;
        List<Land1362.Warft> ws = Land1362.warften();
        int[] haeuser = new int[ws.size()], brunnen = new int[ws.size()];
        for (var h : Stadt1362.houses(Land1362::estimate)) { int i = naechste(h.x(), h.z()); if (i >= 0) haeuser[i]++; }
        for (double[] b : Spuren.wells()) { int i = naechste(b[0], b[1]); if (i >= 0) brunnen[i]++; }
        List<Daten.Warft> l = new ArrayList<>();
        for (int i = 0; i < ws.size(); i++) {
            Land1362.Warft w = ws.get(i);
            String art = art(w);
            boolean kirche = art.equals("KIRCHWARFT");
            l.add(new Daten.Warft(i + 1, w.name(), art, r1(w.x()), r1(w.z()), r1(w.a()), r1(w.b()), r1(norm(w.headingDeg())), r2(w.top()), haeuser[i], brunnen[i],
                    kirche ? Daten.BELEGT : Daten.GESTALTET, kirche ? "JGU23" : "APP",
                    kirche ? "Die Kirche auf einer Warft nahe Südfall ist belegt (2023); Form und Höhe der Warft gestaltet [G]"
                            : art.equals("KETTE") ? "Warftenkette von 2 km belegt (2023); Lage, Größe und Höhe dieser Warft gestaltet [G]"
                            : "54 Warften belegt (2023); Lage, Größe und Höhe dieser Hofwarft gestaltet [G]"));
        }
        warften = Collections.unmodifiableList(l);
        return warften;
    }

    static double r1(double v) { return Math.round(v * 10) / 10.0; }
    static double r2(double v) { return Math.round(v * 100) / 100.0; }
    static double norm(double deg) { double d = deg % 360; return d < 0 ? d + 360 : d; }

    /**
     * Die Bauwerke von 1362: Kirche (Grundriss 40 × 15 m), Seedeich, Warftenkette, Sielzüge, Siel, Sieltief und
     * Hafenbecken (beide aus dem Gelände von heute, wie in der Szene).
     */
    public static List<Daten.Bauwerk> bauwerke(Terrain heute) {
        List<Daten.Bauwerk> l = new ArrayList<>();
        double h = Math.toRadians(Stadt1362.CHURCH_HEADING), ax = Math.sin(h), az = -Math.cos(h), hl = Site.CHURCH_LENGTH / 2, hw = Site.CHURCH_WIDTH / 2;
        double[][] k = new double[4][];
        double[][] uv = {{-hl, -hw}, {hl, -hw}, {hl, hw}, {-hl, hw}};
        for (int i = 0; i < 4; i++) k[i] = new double[]{Stadt1362.CHURCH_X + ax * uv[i][0] - az * uv[i][1], Stadt1362.CHURCH_Z + az * uv[i][0] + ax * uv[i][1]};
        l.add(new Daten.Bauwerk("Hauptkirche", "KIRCHE", Daten.Geom.flaeche(k), 3.8, Daten.BELEGT, "JGU23",
                "40 × 15 m, gefunden im Mai 2023 auf einer Warft nahe Südfall; Richtung und Lage in der Szene gestaltet [G]"));
        l.add(new Daten.Bauwerk("Seedeich", "SEEDEICH", Daten.Geom.linie(Land1362.DIKE), Land1362.CREST, Daten.BELEGT, "JGU23",
                "Ein Seedeich mit Sielhafen ist belegt; Verlauf und Krone NHN +3,5 m gestaltet [G]"));
        l.add(new Daten.Bauwerk("Warftenkette", "KETTE", Daten.Geom.linie(Land1362.CHAIN), null, Daten.BELEGT, "JGU23",
                "Die Kette von 2 km ist belegt; ihr Verlauf durch die Kirchwarft gestaltet [G]"));
        String[] namen = {"Sielzug zum Siel", "Nebenzug im Nordosten", "Nebenzug im Westen"};
        for (int i = 0; i < Land1362.CANALS.length; i++)
            l.add(new Daten.Bauwerk(namen[i], "SIELZUG", Daten.Geom.linie(Land1362.CANALS[i]), Land1362.INNER_WATER, Daten.GESTALTET, "APP",
                    "Entwässerung belegt (2023); dieser Zug gestaltet, 12 bis 16 m breit, Wasser bei NHN +0,1 m [G]"));
        l.add(new Daten.Bauwerk("Siel", "SIEL", Daten.Geom.punkt(Land1362.SIEL[0], Land1362.SIEL[1]), null, Daten.BELEGT, "JGU23",
                "Das Siel im Seedeich; Lage gestaltet [G]"));
        if (heute != null) {
            double[][] tief = Land1362.sieltief(heute);
            l.add(new Daten.Bauwerk("Sieltief", "SIELZUG", Daten.Geom.linie(tief), -2.3, Daten.GESTALTET, "APP",
                    "Vom Siel durch Vorland und Watt bis in die Hever, auf NHN −2,3 m ausgehoben [G]"));
            double[] hb = Land1362.harbour(tief);
            double head = Math.toDegrees(Math.atan2(hb[2], -hb[3]));
            l.add(new Daten.Bauwerk("Sielhafen", "HAFENBECKEN", Daten.Geom.flaeche(Daten.ellipse(hb[0], hb[1], Land1362.BASIN_ALONG, Land1362.BASIN_ACROSS, head, 24)),
                    null, Daten.BELEGT, "JGU23", "Ein Sielhafen ist belegt; das Becken 100 × 60 m vor dem Siel gestaltet [G]"));
        }
        return l;
    }

    /** Die 14 Fundstellen aus scene.Spuren (F31). Art 0 Busch ab 1921, 1 Funde im Museum, 2 Messung und Bohrung 2023. */
    public static List<Daten.Fundstelle> fundstellen() {
        List<Daten.Fundstelle> l = new ArrayList<>();
        String[] art = {"Spur im Watt (Busch)", "Fund im Museum", "Messung und Bohrung 2023"};
        String[] quelle = {"VORLAGEN", "VORLAGEN", "JGU23"};
        for (Spuren.Fund f : Spuren.FINDS) {
            boolean kirche = f.name().startsWith("Kirche");
            l.add(new Daten.Fundstelle(0, f.name(), art[f.kind()], f.year(), f.source(), r1(f.x()), r1(f.z()), Daten.BELEGT, quelle[f.kind()],
                    kirche ? "Lage „nahe Südfall“ belegt" : "Art und Quelle belegt, die Lage gestaltet [G]", Daten.MITGELIEFERT));
        }
        return l;
    }

    // ------------------------------------------------------------------ Wetterlagen

    public static List<Daten.Wetterlage> wetterlagen() {
        List<Daten.Wetterlage> l = new ArrayList<>();
        Map<Weather.Phase, String[]> t = new LinkedHashMap<>();
        t.put(Weather.Phase.KLAR, new String[]{"Klar", "1-12", "Hoch über der Nordsee, leichter Westwind, Schönwetterwolken über dem Watt"});
        t.put(Weather.Phase.AUFZIEHEND, new String[]{"Aufziehend", "1-12", "Ein Tief über der Nordsee: Cirren von Westen, der Wind dreht auf Süd und frischt auf"});
        t.put(Weather.Phase.FRONT, new String[]{"Front", "1-12", "Die Front zieht über die Inseln: geschlossene Wolken, Regen"});
        t.put(Weather.Phase.STURM, new String[]{"Sturm", "9-4", "Weststurm über der Deutschen Bucht mit Schauern, Windstau im Watt"});
        t.put(Weather.Phase.GEWITTER, new String[]{"Gewitter", "6-8", "Sommergewitter über dem Festland, zieht über das Watt"});
        t.put(Weather.Phase.ABKLINGEND, new String[]{"Abklingend", "1-12", "Der Sturm flaut ab, Schauer, Lücken"});
        t.put(Weather.Phase.AUFKLAREND, new String[]{"Aufklarend", "1-12", "Rückseite: klare Nordseeluft, weite Sicht bis Pellworm"});
        t.put(Weather.Phase.NEBEL, new String[]{"Seenebel", "3-6", "Warme Luft über kaltem Wasser: Seenebel liegt über dem Watt"});
        t.put(Weather.Phase.FOEHN, new String[]{"Föhnsturm", "", "Aus Glacier; an der Nordsee nicht zu erwarten"});
        t.put(Weather.Phase.ORKAN, new String[]{"Orkan", "10-3", "Orkan aus Westnordwest: Wind um 33 m/s, Regen und Graupel, hohe See; die Sturmflut"});
        for (Map.Entry<Weather.Phase, String[]> e : t.entrySet()) {
            double[] w = Weather.target(e.getKey());
            l.add(new Daten.Wetterlage(e.getValue()[0], (double) Weather.beaufort(w[0]), e.getKey().name(), w, leer(e.getValue()[1]), e.getValue()[2], Daten.MITGELIEFERT));
        }
        double n = Double.NaN;
        //                                                                         Wind Richt Böig Bed hoch Wolke Regen Sicht Nebel Druck Temp Feuchte Dünung Gewitter
        l.add(new Daten.Wetterlage("Sommertag im Watt", 3.0, "KLAR", new double[]{4, 260, 0.15, 3, 0.1, 0, 0, 50, 0, 1018, 21, 60, 0.4, 0}, "6-8",
                "Wie der Mittag von „Eine Tide“: leichter Westwind, ein paar Quellwolken, 21 °C [G]", Daten.MITGELIEFERT));
        l.add(new Daten.Wetterlage("Mandränke-Nacht (mild)", 12.0, "ORKAN", new double[]{33, 292, 0.35, 8, 0.9, 3, 6, 4, 0, 960, 5, 92, 5.5, 0}, "1",
                "Die Nacht vom 15. auf den 16. Januar 1362 (julianisch; gregorianisch 23./24.): Orkan aus Westnordwest, Regen bei 5 °C, kein Schnee (Phase 16) [G]", Daten.MITGELIEFERT));
        l.add(new Daten.Wetterlage("Sturmnacht im November", 10.0, "STURM", new double[]{24, 270, 0.3, 7, 0.7, 3, 3, 8, 0, 975, 7, 88, 3.5, 0}, "11",
                "Wie „Die Glocken“: Weststurm am Abend des 30.11.2026 [G]", Daten.MITGELIEFERT));
        return l;
    }

    // ------------------------------------------------------------------ Regiebücher

    /** Kopfwerte eines Regiebuchs für RH_REGIEBUCH: Szenenzeit des ersten zeit (oder der Mandränke), Zeitraffer, Fotos, Warten, Blatt. */
    public record Kopf(Long szeneMs, Double raffer, int fotos, int warten, boolean blatt) {
        public static Kopf aus(Regie.Book b) {
            Long t = null;
            Double r = null;
            int fotos = 0, warten = 0;
            boolean blatt = false;
            for (Regie.Zeile z : b.zeilen()) {
                switch (z.befehl()) {
                    case "zeit" -> {
                        if (t != null) break;
                        try {
                            String[] p = z.argumente().strip().split("\\s+");
                            String[] d = p[0].split("\\.");
                            String[] u = p.length > 1 ? p[1].split(":") : new String[]{"0", "0"};
                            t = ZonedDateTime.of(LocalDate.of(Integer.parseInt(d[2]), Integer.parseInt(d[1]), Integer.parseInt(d[0])),
                                    LocalTime.of(Integer.parseInt(u[0]), Integer.parseInt(u[1])), com.dan.rungholt.scene.SceneClock.ZONE).toInstant().toEpochMilli();
                        } catch (RuntimeException e) { /* bleibt leer */ }
                    }
                    case "mandraenke" -> {
                        if (t == null) t = ZonedDateTime.of(Mandraenke.DAY, LocalTime.of((int) Mandraenke.START_HOUR, 0), com.dan.rungholt.scene.SceneClock.ZONE)
                                .toInstant().toEpochMilli();
                    }
                    case "raffer" -> { if (r == null) r = Zahl.lies(z.argumente().strip()); }
                    case "foto" -> fotos++;
                    case "warte" -> warten++;
                    case "blatt" -> blatt = true;
                    default -> { }
                }
            }
            return new Kopf(t, r, fotos, warten, blatt);
        }
    }

    /** Die Zeilen „Wetter:“ und „Schritt:“ eines Regiebuchs (der Wetterplan) oder null. */
    public static String wetterBlock(String text) {
        StringBuilder b = new StringBuilder();
        for (String z : text.split("\\R")) {
            String s = z.strip(), low = s.toLowerCase(Locale.ROOT);
            if (low.startsWith("wetter:") || low.startsWith("schritt:")) b.append(b.length() > 0 ? "\n" : "").append(s);
        }
        return b.length() == 0 ? null : b.toString();
    }

    public static List<Daten.Regiebuch> regiebuecher() {
        List<Daten.Regiebuch> l = new ArrayList<>();
        for (String d : Regie.DATEIEN) {
            String t = Regie.text(d);
            Regie.Book b = Regie.parse(t);
            l.add(new Daten.Regiebuch(b.name(), t, b.about(), d, Daten.MITGELIEFERT));
        }
        return l;
    }

    // ------------------------------------------------------------------ Ansichten und Fahrten

    /** Die neun festen Ansichten (Tasten 1–9) aus {@link Ansichten#ALLE}. Kamera: x, Augenhöhe NaN (aus dem Gelände), z, Blick, Neigung. */
    public static List<Daten.Ansicht> ansichten() {
        List<Daten.Ansicht> l = new ArrayList<>();
        for (Ansichten.Ansicht a : Ansichten.ALLE)
            l.add(new Daten.Ansicht(0, a.name(), a.key(), "FEST", new double[]{a.x(), Double.NaN, a.z(), a.heading(), a.pitch()}, a.eyeAboveGround(),
                    a.phase() >= 11 ? Daten.A1362 : Daten.HEUTE, a.eyeAboveGround() < 0 || a.pitch() < 0, null, null, null, Daten.MITGELIEFERT));
        return l;
    }

    /** Die drei Fahrten; ihre Wege rechnet die App aus der Szene (Gruppe, Kette, Rinne des Priels). */
    public static List<Daten.Fahrt> fahrten() {
        return List.of(
                new Daten.Fahrt("Wattwanderung", "WANDERUNG", null, null, "Seitlich hinter der geführten Gruppe von Fuhlehörn nach Südfall, 5,8 km über die hohen Watten",
                        Daten.MITGELIEFERT, List.of()),
                new Daten.Fahrt("Über die Warftenkette", "KETTE", null, Land1362.chainLength(), "30 m hoch die Kette von West nach Ost entlang, die Stadt links (1362)",
                        Daten.MITGELIEFERT, List.of()),
                new Daten.Fahrt("Prielfahrt", "PRIEL", null, null, "Mit dem Boot bei Flut den Priel südlich der hohen Watten hinauf nach Nordosten, 12 m/s; "
                        + "die App sucht den Weg in der Rinne (Phase 15)", Daten.MITGELIEFERT, List.of()));
    }

    // ------------------------------------------------------------------ Presets

    /** Die Klassen, deren Presets mitkommen: jede öffentliche statische Methode ohne Argument, die die Klasse liefert. */
    public static final String[] PAKETE = {"com.dan.ocean.Ocean", "com.dan.reed.Reed", "com.dan.cloud.Clouds", "com.dan.rain.Rain", "com.dan.fog.Fog",
            "com.dan.lightning.Lightning", "com.dan.weathering.Weathering", "com.dan.buoyancy.Buoyancy", "com.dan.flock.Flock", "com.dan.soundscape.Soundscape",
            "com.dan.spray.Spray", "com.dan.wave.Wave", "com.dan.fracture.Fracture", "com.dan.muster.Muster", "com.dan.tide.Tide", "com.dan.mudflat.Mudflat",
            "com.dan.seal.Seal", "com.dan.season.Climate"};
    private static List<Daten.PaketPreset> presets;

    public static synchronized List<Daten.PaketPreset> presets() {
        if (presets != null) return presets;
        List<Daten.PaketPreset> l = new ArrayList<>();
        for (String k : PAKETE) {
            try {
                Class<?> c = Class.forName(k);
                List<Method> ms = new ArrayList<>();
                for (Method m : c.getMethods())
                    if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 0 && m.getReturnType() == c) ms.add(m);
                ms.sort(Comparator.comparing(Method::getName));
                for (Method m : ms) {
                    Object o = m.invoke(null);
                    l.add(new Daten.PaketPreset(c.getPackageName(), c.getSimpleName(), m.getName(), "Preset " + m.getName() + "() aus " + c.getPackageName(),
                            Daten.MITGELIEFERT, Felder.lesen(o)));
                }
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Preset von " + k + " nicht lesbar: " + e, e);
            }
        }
        presets = Collections.unmodifiableList(l);
        return presets;
    }

    /** Die Typen der Felder eines Presets (DOUBLE, INT, BOOLEAN, STRING, ENUM) aus seiner Klasse. */
    public static Map<String, String> typen(Daten.PaketPreset p) {
        try {
            return Felder.typen(Class.forName(p.paket() + "." + p.klasse()));
        } catch (ClassNotFoundException e) {
            return Map.of();
        }
    }

    // ------------------------------------------------------------------ Gelände

    /** Die beiden Gelände: heute (aus den Dateien in dir) und 1362 (gerechnet aus heute). */
    public static List<Daten.Gelaende> gelaende(File dir) {
        double x1 = Terrain.X0 + Terrain.N * Terrain.CELL, z1 = Terrain.Z0 + Terrain.N * Terrain.CELL;
        Daten.Geom aus = Daten.Geom.flaeche(new double[][]{{Terrain.X0, Terrain.Z0}, {x1, Terrain.Z0}, {x1, z1}, {Terrain.X0, z1}});
        Map<String, String> dateien = new LinkedHashMap<>();
        for (String f : new String[]{Terrain.HOEHE, Terrain.ARTEN, Terrain.INFO}) dateien.put(f, sha256(new File(dir, f)));
        String km = String.format(Locale.GERMAN, "%.1f × %.1f km", Terrain.N * Terrain.CELL / 1000, Terrain.N * Terrain.CELL / 1000);
        return List.of(
                new Daten.Gelaende("Watt heute (BAW)", Daten.HEUTE, aus, Site.LON, Site.LAT, Terrain.CELL, Terrain.N, dateien, "BAW",
                        "Ausschnitt " + km + " um die Kirche von Rungholt; Szenenmeter x nach Osten, z nach Süden, Ursprung an der Kirche [G]; Höhen NHN"),
                new Daten.Gelaende("Land 1362", Daten.A1362, aus, Site.LON, Site.LAT, Terrain.CELL, Terrain.N, Map.of(), "APP",
                        "Aus dem Watt von heute gerechnet (scene.Land1362): Marsch, Seedeich, 54 Warften, Sielzüge, Sieltief [G]; keine Dateien"));
    }

    /** SHA-256 einer Datei (hex) oder null, wenn sie fehlt. */
    public static String sha256(File f) {
        if (!f.isFile()) return null;
        try (InputStream in = new java.io.FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[1 << 16];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder b = new StringBuilder();
            for (byte x : md.digest()) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /** Für Prüfungen: alle Zahlen auf einen Blick. */
    public static String zusammenfassung() {
        return String.format(Locale.GERMAN, "%d Quellen, %d Befunde, %d Sturmfluten, %d Pegelorte, %d Kalenderzeilen, %d Warften, %d Fundstellen, "
                        + "%d Wetterlagen, %d Regiebücher, %d Ansichten, %d Fahrten, %d Presets",
                quellen().size(), befunde().size(), sturmfluten().size(), pegelorte().size(), kalender().size(), warften().size(), fundstellen().size(),
                wetterlagen().size(), regiebuecher().size(), ansichten().size(), fahrten().size(), presets().size());
    }
}
