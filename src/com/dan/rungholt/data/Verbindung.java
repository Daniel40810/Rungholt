package com.dan.rungholt.data;

import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.weather.Weather;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Das Schema RUNGHOLT (Phase 16, Werkbuch Abschnitt 8): Ort von {@code db/}, Zugang aus {@code db/db.properties} und
 * der Einrichter mit allem, was er für Rungholt prüft. Schema und Benutzer legt {@code db/USER.sql} an (Rungholt auf
 * PDBORCL).
 */
public final class Verbindung {
    private Verbindung() { }

    public static final String PRAEFIX = "RH_";
    public static final String ENV = "RUNGHOLT_DB_PASSWORD";
    /** Die Ausbaustufe dieser App (RH_SCHEMA.VERSION). */
    public static final int VERSION = 1;
    public static final String[] SICHTEN = {"RH_WARFT_V", "RH_RUNGHOLT_V", "RH_TAGESKARTE_V", "RH_SPRINGNIPP_V", "RH_NW_TAG_V", "RH_TIDE_TAG_V",
            "RH_MANDRAENKE_V", "RH_BEFUND_V", "RH_LETZTE_SITZUNG_V"};
    public static final String[] METADATEN = {"RH_GELAENDE.AUSSCHNITT", "RH_PEGELORT.LAGE", "RH_TIDE.WASSERLINIE", "RH_ZAEHLUNG.LAGE",
            "RH_MANDRAENKE_LAUF.BRUCHSTELLE", "RH_MANDRAENKE_LAUF.BRESCHE", "RH_WARFT.MITTE", "RH_WARFT.FLAECHE", "RH_BAUWERK.GEOM", "RH_FUNDSTELLE.LAGE",
            "RH_ANSICHT.LAGE"};

    /** Die Wetterspalten von RH_WETTERLAGE in der Reihenfolge von {@code Weather.Var}. */
    public static final Map<Weather.Var, String> WETTER = new LinkedHashMap<>();
    static {
        WETTER.put(Weather.Var.WIND, "WIND_MS");
        WETTER.put(Weather.Var.DIRECTION, "RICHTUNG_GRAD");
        WETTER.put(Weather.Var.GUST, "BOEIGKEIT");
        WETTER.put(Weather.Var.COVER, "BEDECKUNG_ACHTEL");
        WETTER.put(Weather.Var.HIGH, "HOHE_SCHICHT");
        WETTER.put(Weather.Var.CLOUD, "WOLKENART");
        WETTER.put(Weather.Var.RAIN, "REGEN_MMH");
        WETTER.put(Weather.Var.VISIBILITY, "SICHT_KM");
        WETTER.put(Weather.Var.FOG, "NEBEL");
        WETTER.put(Weather.Var.PRESSURE, "LUFTDRUCK_HPA");
        WETTER.put(Weather.Var.TEMPERATURE, "TEMPERATUR_C");
        WETTER.put(Weather.Var.HUMIDITY, "FEUCHTE_PROZ");
        WETTER.put(Weather.Var.SWELL, "DUENUNG_M");
        WETTER.put(Weather.Var.THUNDER, "GEWITTER");
    }

    /** Der Ordner db: -Drungholt.db.dir, sonst ./db, sonst db neben dem Jar. */
    public static File ordner() { return Db.ordner("rungholt.db.dir", Verbindung.class); }

    public static File skripte() { return new File(ordner(), "sql"); }

    /**
     * Der Zugang aus db/db.properties. Fehlt die Datei, wird sie aus der Vorlage db.properties.example angelegt (ohne
     * Passwort); das Startbild fragt dann danach und trägt es nach dem Verbinden ein.
     */
    public static Db.Konfig konfig() throws IOException {
        File f = new File(ordner(), "db.properties"), vorlage = new File(ordner(), "db.properties.example");
        if (!f.isFile() && vorlage.isFile()) Files.copy(vorlage.toPath(), f.toPath());
        return Db.konfig(f, "Rungholt", ENV);
    }

    /** Die räumlichen Indizes (je Geometriespalte einer: RH_WARFT_FLAECHE_SX …). */
    public static String[] raeumlich() {
        String[] r = new String[METADATEN.length];
        for (int i = 0; i < r.length; i++) r[i] = METADATEN[i].replace('.', '_') + "_SX";
        return r;
    }

    /** Die Zahlen der Startdaten aus der Zeile {@code -- ZAHLEN: Art=n; …} von 04_startdaten.sql. */
    public static Map<String, Integer> zahlen(File dir) {
        Map<String, Integer> m = new LinkedHashMap<>();
        try (var lines = Files.lines(new File(dir, "04_startdaten.sql").toPath(), StandardCharsets.UTF_8)) {
            String z = lines.filter(l -> l.startsWith("-- ZAHLEN:")).findFirst().orElse(null);
            if (z != null)
                for (String p : z.substring(10).split(";")) {
                    String[] kv = p.strip().split("=");
                    if (kv.length == 2) m.put(kv[0].strip(), Integer.parseInt(kv[1].strip()));
                }
        } catch (IOException | RuntimeException e) { /* dann prüft der Einrichter ohne Zahlen */ }
        return m;
    }

    /** Die SQL-Abfrage je Zahl der Startdaten. */
    static final Map<String, String> ZAEHLEN = new LinkedHashMap<>();
    static {
        String m = " WHERE HERKUNFT = 'MITGELIEFERT'";
        ZAEHLEN.put("Quellen", "SELECT COUNT(*) FROM RH_QUELLE" + m);
        ZAEHLEN.put("Befunde", "SELECT COUNT(*) FROM RH_BEFUND" + m);
        ZAEHLEN.put("Belege", "SELECT COUNT(*) FROM RH_BEFUND_QUELLE x JOIN RH_BEFUND b ON b.ID = x.BEFUND_ID WHERE b.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Gelände", "SELECT COUNT(*) FROM RH_GELAENDE" + m);
        ZAEHLEN.put("Pegelorte", "SELECT COUNT(*) FROM RH_PEGELORT" + m);
        ZAEHLEN.put("Partialtiden", "SELECT COUNT(*) FROM RH_PARTIALTIDE" + m);
        ZAEHLEN.put("Tidenkalender", "SELECT COUNT(*) FROM RH_TIDENKALENDER" + m);
        ZAEHLEN.put("Warften", "SELECT COUNT(*) FROM RH_WARFT" + m);
        ZAEHLEN.put("Bauwerke", "SELECT COUNT(*) FROM RH_BAUWERK" + m);
        ZAEHLEN.put("Fundstellen", "SELECT COUNT(*) FROM RH_FUNDSTELLE" + m);
        ZAEHLEN.put("Sturmfluten", "SELECT COUNT(*) FROM RH_STURMFLUT" + m);
        ZAEHLEN.put("Wetterlagen", "SELECT COUNT(*) FROM RH_WETTERLAGE" + m);
        ZAEHLEN.put("Regiebücher", "SELECT COUNT(*) FROM RH_REGIEBUCH" + m);
        ZAEHLEN.put("Stichworte", "SELECT COUNT(*) FROM RH_STICHWORT x JOIN RH_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.HERKUNFT = 'MITGELIEFERT'");
        ZAEHLEN.put("Ansichten", "SELECT COUNT(*) FROM RH_ANSICHT" + m + " AND ART = 'FEST'");
        ZAEHLEN.put("Fahrten", "SELECT COUNT(*) FROM RH_FAHRT" + m);
        ZAEHLEN.put("Presets", "SELECT COUNT(*) FROM RH_PAKET_PRESET" + m);
        ZAEHLEN.put("Presetwerte", "SELECT COUNT(*) FROM RH_PAKET_WERT x JOIN RH_PAKET_PRESET p ON p.ID = x.PAKET_PRESET_ID WHERE p.HERKUNFT = 'MITGELIEFERT'");
    }

    /** Der Einrichter für das Schema RUNGHOLT. */
    public static Einrichter einrichter() {
        Einrichter e = new Einrichter();
        e.praefix = PRAEFIX;
        e.version = VERSION;
        e.sichten = SICHTEN.clone();
        e.metadaten = METADATEN.clone();
        e.raeumlich = raeumlich();
        e.startdaten = () -> {
            List<Einrichter.Soll> l = new ArrayList<>();
            Map<String, Integer> z = zahlen(skripte());
            for (Map.Entry<String, String> q : ZAEHLEN.entrySet()) if (z.containsKey(q.getKey())) l.add(new Einrichter.Soll(q.getKey(), q.getValue(), z.get(q.getKey())));
            return l;
        };
        e.rechnet = () -> List.of(
                new Einrichter.Soll("Warftenkette (km auf dem Ellipsoid, Szene 2,3)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_LENGTH(GEOM, 0.05, 'unit=KM'), 'FM990.00') FROM RH_BAUWERK WHERE NAME = 'Warftenkette'", -1),
                new Einrichter.Soll("Kronen der 54 Warften (ha auf dem Ellipsoid)",
                        "SELECT TO_CHAR(SUM(KRONE_M2) / 10000, 'FM990.0') || ' ha, Kirchwarft ' || TO_CHAR(MAX(CASE WHEN ART = 'KIRCHWARFT' THEN KRONE_M2 END), 'FM99990') || ' m²' FROM RH_WARFT_V", -1),
                new Einrichter.Soll("Fundstellen in 100 m um eine Warft (räumlich)",
                        "SELECT TO_CHAR(COUNT(*)) || ' Warften mit Funden, ' || TO_CHAR(SUM(FUNDSTELLEN)) || ' Treffer' FROM RH_WARFT_V WHERE FUNDSTELLEN > 0", -1),
                new Einrichter.Soll("Ausschnitt des Geländes (km², Szene 16,4 × 16,4 = 268)",
                        "SELECT TO_CHAR(MDSYS.SDO_GEOM.SDO_AREA(AUSSCHNITT, 0.05, 'unit=SQ_KM'), 'FM9990.0') FROM RH_GELAENDE WHERE AERA = 'HEUTE'", -1),
                new Einrichter.Soll("Springtiden am Rand 2026 (Tage)", "SELECT TO_CHAR(COUNT(*)) FROM RH_SPRINGNIPP_V WHERE PEGEL = 'NORDERHEVER' AND TIDE = 'SPRING' AND TAG LIKE '2026-%'", -1),
                new Einrichter.Soll("Sturmfluten belegt, höchster Scheitel Husum",
                        "SELECT TO_CHAR(COUNT(*)) || ', ' || TO_CHAR(MAX(SCHEITEL_HUSUM_CM)) || ' cm' FROM RH_STURMFLUT", -1),
                new Einrichter.Soll("Chronik: Tiden, Zählungen, Läufe der Mandränke, Glocken",
                        "SELECT (SELECT COUNT(*) FROM RH_TIDE) || ', ' || (SELECT COUNT(*) FROM RH_ZAEHLUNG) || ', ' || (SELECT COUNT(*) FROM RH_MANDRAENKE_LAUF) || ', ' "
                                + "|| (SELECT COUNT(*) FROM RH_GLOCKEN) FROM DUAL", -1),
                new Einrichter.Soll("Tageskarte: Objekte in RH_TAGESKARTE_V", "SELECT TO_CHAR(COUNT(*)) FROM RH_TAGESKARTE_V", -1));
        return e;
    }
}
