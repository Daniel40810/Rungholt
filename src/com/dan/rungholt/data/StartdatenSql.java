package com.dan.rungholt.data;

import com.dan.rungholt.scene.Regie;
import com.dan.rungholt.scene.Terrain;
import com.dan.store.Zahl;
import com.dan.weather.Weather;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Schreibt die mitgelieferten Daten als SQL: {@code db/sql/04_startdaten.sql} (erzeugt von {@code tools.StartdatenSql},
 * nicht von Hand ändern). MERGE je Eintrag mit Namen; Kinder (Partialtiden, Stichworte, Werte, Belege), der
 * Tidenkalender und die Fundstellen werden gelöscht und neu angelegt. Eigene Zeilen (HERKUNFT = 'EIGEN') bleiben
 * unberührt. Beliebig oft wiederholbar.
 * <p>
 * Geometrien: Punkte als {@code SDO_POINT_TYPE}, Linien und Flächen als WKT über {@code SDO_GEOMETRY(wkt, 8307)}; lange
 * Texte (WKT, Regiebücher) in Stücken zu höchstens 1 000 Zeichen als {@code TO_CLOB(…) || TO_CLOB(…)}, je Stück eine
 * Zeile. Der Tidenkalender kommt in Blöcken zu 100 Zeilen ({@code INSERT ALL}).
 */
public final class StartdatenSql {
    private StartdatenSql() { }

    static final String M = "'MITGELIEFERT'";

    static String s(String v) {
        if (v == null) return "NULL";
        return "'" + v.replace("'", "''").replace("\r", "").replace("\n", "' || CHR(10) || '") + "'";
    }

    /** Ein langer Text als CLOB in Stücken. */
    static String clob(String v) {
        if (v == null) return "NULL";
        String t = v.replace("\r", "");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < t.length(); ) {
            int e = Math.min(t.length(), i + 1000);
            if (e < t.length() && Character.isHighSurrogate(t.charAt(e - 1))) e--;
            b.append(i > 0 ? "\n    || " : "").append("TO_CLOB(").append(s(t.substring(i, e))).append(")");
            i = e;
        }
        return b.toString();
    }

    static String n(Double v) { return v == null || Double.isNaN(v) || Double.isInfinite(v) ? "NULL" : Zahl.text(Math.round(v * 1e6) / 1e6); }

    static String n(double v) { return n((Double) v); }

    static String n8(double v) { return exakt(Math.round(v * 1e8) / 1e8); }

    /** Eine Zahl mit allen Stellen, die sie hat (für Partialtiden und Lagen; Zahl.text kürzt auf sechs). */
    static String exakt(double v) { return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString(); }

    static String i(Integer v) { return v == null ? "NULL" : v.toString(); }

    static String b(boolean v) { return v ? "1" : "0"; }

    static String d(LocalDate d) { return d == null ? "NULL" : "DATE '" + d + "'"; }

    static String quelle(String kurz) { return kurz == null ? "NULL" : "(SELECT MAX(ID) FROM RH_QUELLE WHERE KURZ = " + s(kurz) + ")"; }

    /** Ein Punkt in Länge und Breite. */
    static String punkt(double x, double z) {
        double[] ll = Daten.lonLat(x, z);
        return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(" + n8(ll[0]) + ", " + n8(ll[1]) + ", NULL), NULL, NULL)";
    }

    static String geom(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return punkt(g.xz()[0][0], g.xz()[0][1]);
        String w = g.wkt();
        if (w.length() <= 1000) return "MDSYS.SDO_GEOMETRY(" + s(w) + ", 8307)";
        return "MDSYS.SDO_GEOMETRY(" + clob(w) + ", 8307)";
    }

    private static void kopf(StringBuilder b, String t) {
        b.append("\n-- ").append("-".repeat(Math.max(4, 100 - t.length()))).append(' ').append(t).append('\n');
    }

    /** Die mitgelieferten Daten auf einmal (Gelände und Bauwerke brauchen das Gelände von heute). */
    public record Alles(List<Daten.Gelaende> gelaende, List<Daten.Bauwerk> bauwerke) {
        public static Alles aus(File gelaendeDir, Terrain heute) {
            return new Alles(Startdaten.gelaende(gelaendeDir), Startdaten.bauwerke(heute));
        }
    }

    /** Die Zahlen der mitgelieferten Zeilen je Art (Zeile „-- ZAHLEN:“, der Einrichter prüft sie). */
    public static Map<String, Integer> zahlen(Alles a) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("Quellen", Startdaten.quellen().size());
        m.put("Befunde", Startdaten.befunde().size());
        int belege = 0;
        for (Daten.Befund f : Startdaten.befunde()) belege += f.quellen().size();
        m.put("Belege", belege);
        m.put("Gelände", a.gelaende().size());
        m.put("Pegelorte", Startdaten.pegelorte().size());
        int pt = 0;
        for (Daten.Pegelort p : Startdaten.pegelorte()) pt += p.tiden().size();
        m.put("Partialtiden", pt);
        m.put("Tidenkalender", Startdaten.kalender().size());
        m.put("Warften", Startdaten.warften().size());
        m.put("Bauwerke", a.bauwerke().size());
        m.put("Fundstellen", Startdaten.fundstellen().size());
        m.put("Sturmfluten", Startdaten.sturmfluten().size());
        m.put("Wetterlagen", Startdaten.wetterlagen().size());
        m.put("Regiebücher", Startdaten.regiebuecher().size());
        int sw = 0;
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) sw += Regie.parse(r.text()).zeilen().size();
        m.put("Stichworte", sw);
        m.put("Ansichten", Startdaten.ansichten().size());
        m.put("Fahrten", Startdaten.fahrten().size());
        m.put("Presets", Startdaten.presets().size());
        int pw = 0;
        for (Daten.PaketPreset p : Startdaten.presets()) pw += p.felder().size();
        m.put("Presetwerte", pw);
        return m;
    }

    /** MERGE INTO t USING (SELECT key AS col …) q ON (…) WHEN MATCHED THEN UPDATE … WHERE HERKUNFT … WHEN NOT MATCHED THEN INSERT …. */
    private static void merge(StringBuilder b, String tabelle, String[] schluessel, String[] schluesselWerte, String[] spalten, String[] werte, boolean herkunft) {
        StringBuilder using = new StringBuilder(), on = new StringBuilder(), upd = new StringBuilder(), cols = new StringBuilder(), vals = new StringBuilder();
        for (int k = 0; k < schluessel.length; k++) {
            using.append(k > 0 ? ", " : "").append(schluesselWerte[k]).append(" AS ").append(schluessel[k]);
            on.append(k > 0 ? " AND " : "").append("t.").append(schluessel[k]).append(" = q.").append(schluessel[k]);
            cols.append(k > 0 ? ", " : "").append(schluessel[k]);
            vals.append(k > 0 ? ", " : "").append(schluesselWerte[k]);
        }
        for (int k = 0; k < spalten.length; k++) {
            upd.append(k > 0 ? ", " : "").append("t.").append(spalten[k]).append(" = ").append(werte[k]);
            cols.append(", ").append(spalten[k]);
            vals.append(", ").append(werte[k]);
        }
        b.append("MERGE INTO ").append(tabelle).append(" t USING (SELECT ").append(using).append(" FROM DUAL) q ON (").append(on).append(")\n");
        if (spalten.length > 0) b.append("WHEN MATCHED THEN UPDATE SET ").append(upd).append(herkunft ? " WHERE t.HERKUNFT = " + M : "").append('\n');
        b.append("WHEN NOT MATCHED THEN INSERT (").append(cols).append(herkunft ? ", HERKUNFT" : "").append(")\n  VALUES (").append(vals)
                .append(herkunft ? ", " + M : "").append(");\n");
    }

    /** Das ganze Skript. */
    public static String sql(Alles a) {
        StringBuilder b = new StringBuilder();
        Map<String, Integer> z = zahlen(a);
        b.append("""
                -- =====================================================================================================
                -- Rungholt · Datenbank · 04_startdaten.sql
                -- ERZEUGT aus com.dan.rungholt.data.Startdaten (tools.StartdatenSql) – nicht von Hand ändern.
                -- Mitgelieferte Zeilen (HERKUNFT = 'MITGELIEFERT') werden angelegt oder wiederhergestellt; eigene Zeilen
                -- bleiben unberührt. Beliebig oft wiederholbar. Pegelorte und Tidenkalender aus com.dan.tide (F26, F27),
                -- Warften, Bauwerke und Fundstellen aus der Szene (F30, F31), die Sturmfluten aus data/sturmfluten.txt
                -- (recherchiert am 20.10.2026, Quellen in RH_QUELLE, F32). Szenenzeit als ms seit 1970 und Text (F33).
                -- =====================================================================================================
                """);
        StringBuilder zl = new StringBuilder("-- ZAHLEN:");
        for (Map.Entry<String, Integer> e : z.entrySet()) zl.append(' ').append(e.getKey()).append('=').append(e.getValue()).append(';');
        b.append(zl).append("\n\n-- Die Quellen enthalten „&“: in SQL Developer keine Ersetzungsvariablen\nSET DEFINE OFF\n");

        kopf(b, "Ausbaustufe");
        b.append("MERGE INTO RH_SCHEMA t USING (SELECT 1 AS VERSION FROM DUAL) q ON (t.VERSION = q.VERSION)\n")
                .append("WHEN NOT MATCHED THEN INSERT (VERSION, APP_VERSION, TEXT) VALUES (1, 'Phase 16', ")
                .append(s("Datenbank: 27 Tabellen, Pegel und Tidenkalender, Warften und Fundstellen als Karte, Chronik der Tiden, Zählungen, Mandränke und Glocken"))
                .append(");\n");

        kopf(b, "Quellen");
        for (Daten.Quelle q : Startdaten.quellen())
            merge(b, "RH_QUELLE", new String[]{"KURZ"}, new String[]{s(q.kurz())}, new String[]{"TITEL", "URL", "ART", "ABGERUFEN", "BEMERKUNG"},
                    new String[]{s(q.titel()), s(q.url()), s(q.art()), d(q.abgerufen()), s(q.bemerkung())}, true);

        kopf(b, "Befunde");
        for (Daten.Befund f : Startdaten.befunde())
            merge(b, "RH_BEFUND", new String[]{"THEMA", "GEGENSTAND"}, new String[]{s(f.thema()), s(f.gegenstand())}, new String[]{"BEFUND", "TEXT"},
                    new String[]{s(f.befund()), s(f.text())}, true);
        b.append("DELETE FROM RH_BEFUND_QUELLE WHERE BEFUND_ID IN (SELECT ID FROM RH_BEFUND WHERE HERKUNFT = ").append(M).append(");\n");
        for (Daten.Befund f : Startdaten.befunde())
            for (String q : f.quellen())
                b.append("INSERT INTO RH_BEFUND_QUELLE (BEFUND_ID, QUELLE_ID) SELECT b.ID, q.ID FROM RH_BEFUND b, RH_QUELLE q WHERE b.THEMA = ").append(s(f.thema()))
                        .append(" AND b.GEGENSTAND = ").append(s(f.gegenstand())).append(" AND b.HERKUNFT = ").append(M).append(" AND q.KURZ = ").append(s(q)).append(";\n");

        kopf(b, "Gelände (heute und 1362)");
        for (Daten.Gelaende g : a.gelaende())
            merge(b, "RH_GELAENDE", new String[]{"NAME"}, new String[]{s(g.name())},
                    new String[]{"AERA", "AUSSCHNITT", "URSPRUNG_LAENGE", "URSPRUNG_BREITE", "ZELLE_M", "ZELLEN", "DATEIEN", "QUELLE_ID", "BEMERKUNG"},
                    new String[]{s(g.aera()), geom(g.ausschnitt()), n8(g.ursprungLon()), n8(g.ursprungLat()), n(g.zelleM()), Integer.toString(g.zellen()),
                            s(g.dateien().isEmpty() ? null : g.dateienText()), quelle(g.quelle()), s(g.bemerkung())}, true);

        kopf(b, "Pegelorte und Partialtiden (F26)");
        for (Daten.Pegelort p : Startdaten.pegelorte()) {
            merge(b, "RH_PEGELORT", new String[]{"KURZ"}, new String[]{s(p.kurz())},
                    new String[]{"NAME", "LAGE", "X_M", "Z_M", "MITTEL_M", "VERSATZ_MIN", "HUB_FAKTOR", "BEZUG", "SZENE", "BESCHREIBUNG", "QUELLE_ID"},
                    new String[]{s(p.name()), punkt(p.x(), p.z()), n(Math.round(p.x() * 10) / 10.0), n(Math.round(p.z() * 10) / 10.0), n(p.mittelM()), n(p.versatzMin()),
                            n(Math.round(p.hubFaktor() * 1e4) / 1e4), "'NHN'", b(p.szene()), s(p.beschreibung()), quelle(p.quelle())}, true);
            String eltern = "FROM RH_PEGELORT WHERE KURZ = " + s(p.kurz()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM RH_PARTIALTIDE WHERE HERKUNFT = ").append(M).append(" AND PEGELORT_ID IN (SELECT ID ").append(eltern).append(");\n");
            for (Daten.Partialtide t : p.tiden())
                b.append("INSERT INTO RH_PARTIALTIDE (PEGELORT_ID, NAME, GRAD_JE_H, AMPLITUDE_M, PHASE_GRAD, HERKUNFT) SELECT ID, ").append(s(t.name())).append(", ")
                        .append(exakt(Math.round(t.gradJeH() * 1e7) / 1e7)).append(", ").append(exakt(Math.round(t.amplitudeM() * 1e6) / 1e6)).append(", ")
                        .append(exakt(Math.round(norm(t.phaseGrad()) * 1e5) / 1e5)).append(", ").append(M).append(' ').append(eltern).append(";\n");
        }

        kopf(b, "Tidenkalender (F27): Husum und Rand 2026–2027, der Rand im Januar 1362");
        b.append("DELETE FROM RH_TIDENKALENDER WHERE HERKUNFT = ").append(M).append(";\n");
        Map<String, List<Daten.Kalender>> je = new LinkedHashMap<>();
        for (Daten.Kalender k : Startdaten.kalender()) je.computeIfAbsent(k.pegel(), x -> new ArrayList<>()).add(k);
        for (Map.Entry<String, List<Daten.Kalender>> e : je.entrySet()) {
            List<Daten.Kalender> l = e.getValue();
            for (int i0 = 0; i0 < l.size(); i0 += 100) {
                b.append("INSERT ALL\n");
                for (int k = i0; k < Math.min(l.size(), i0 + 100); k++) {
                    Daten.Kalender c = l.get(k);
                    b.append("  INTO RH_TIDENKALENDER (PEGELORT_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, STUNDE, ART, HOEHE_M, SPRING_NIPP, HERKUNFT) VALUES (P, ")
                            .append(c.ms()).append(", ").append(s(c.text())).append(", ").append(s(c.tag())).append(", ").append(n(Math.round(c.stunde() * 100) / 100.0))
                            .append(", ").append(s(c.art())).append(", ").append(n(c.hoeheM())).append(", ").append(n(c.springNipp())).append(", ").append(M).append(")\n");
                }
                b.append("SELECT (SELECT ID FROM RH_PEGELORT WHERE KURZ = ").append(s(e.getKey())).append(") AS P FROM DUAL;\n");
            }
        }

        kopf(b, "Warften von 1362 (F30)");
        for (Daten.Warft w : Startdaten.warften())
            merge(b, "RH_WARFT", new String[]{"NR"}, new String[]{Integer.toString(w.nr())},
                    new String[]{"NAME", "ART", "MITTE", "FLAECHE", "X_M", "Z_M", "HALBACHSE_A_M", "HALBACHSE_B_M", "RICHTUNG_GRAD", "KRONE_NHN_M", "HAEUSER", "BRUNNEN",
                            "BEFUND", "QUELLE_ID", "BEMERKUNG"},
                    new String[]{s(w.name()), s(w.art()), punkt(w.x(), w.z()), geom(w.flaeche()), n(w.x()), n(w.z()), n(w.a()), n(w.b()), n(w.richtungGrad()),
                            n(w.kroneM()), Integer.toString(w.haeuser()), Integer.toString(w.brunnen()), s(w.befund()), quelle(w.quelle()), s(w.bemerkung())}, true);

        kopf(b, "Bauwerke von 1362 (F30)");
        for (Daten.Bauwerk w : a.bauwerke()) {
            Daten.Geom g = w.geom();
            Double laenge = g.art().contains("LINIE") ? Math.round(g.laenge() * 10) / 10.0 : null;
            Double flaeche = g.art().contains("FLAECHE") ? Math.round(g.flaecheM2() * 10) / 10.0 : null;
            String[] cols = {"ART", "LAENGE_M", "FLAECHE_M2", "HOEHE_NHN_M", "BEFUND", "QUELLE_ID", "BEMERKUNG"};
            String[] vals = {s(w.art()), n(laenge), n(flaeche), n(w.hoeheM()), s(w.befund()), quelle(w.quelle()), s(w.bemerkung())};
            merge(b, "RH_BAUWERK", new String[]{"NAME"}, new String[]{s(w.name())}, cols, vals, true);
            b.append("UPDATE RH_BAUWERK SET GEOM = ").append(geom(g)).append("\n  WHERE NAME = ").append(s(w.name())).append(" AND HERKUNFT = ").append(M).append(";\n");
        }

        kopf(b, "Fundstellen (F31): die mitgelieferten neu, eigene bleiben");
        b.append("DELETE FROM RH_FUNDSTELLE WHERE HERKUNFT = ").append(M).append(";\n");
        for (Daten.Fundstelle f : Startdaten.fundstellen())
            b.append("INSERT INTO RH_FUNDSTELLE (NAME, FUND, JAHR, FINDER, LAGE, X_M, Z_M, BEFUND, QUELLE_ID, BEMERKUNG, HERKUNFT)\n  VALUES (").append(s(f.name()))
                    .append(", ").append(s(f.fund())).append(", ").append(i(f.jahr())).append(", ").append(s(f.finder())).append(", ").append(punkt(f.x(), f.z()))
                    .append(", ").append(n(f.x())).append(", ").append(n(f.z())).append(", ").append(s(f.befund())).append(", ").append(quelle(f.quelle())).append(", ")
                    .append(s(f.bemerkung())).append(", ").append(M).append(");\n");

        kopf(b, "Sturmfluten (F32, belegt)");
        for (Daten.Sturmflut f : Startdaten.sturmfluten())
            merge(b, "RH_STURMFLUT", new String[]{"NAME"}, new String[]{s(f.name())},
                    new String[]{"DATUM_TEXT", "JAHR", "MONAT", "TAG", "KALENDER", "SCHEITEL_HUSUM_CM", "BEZUG", "FOLGEN", "QUELLE_ID"},
                    new String[]{s(f.datumText()), Integer.toString(f.jahr()), i(f.monat()), i(f.tag()), s(f.kalender()), i(f.scheitelCm()), s(f.bezug()), s(f.folgen()),
                            quelle(f.quelle())}, true);

        kopf(b, "Wetterlagen");
        for (Daten.Wetterlage w : Startdaten.wetterlagen()) {
            List<String> cols = new ArrayList<>(List.of("BEAUFORT", "PHASE")), vals = new ArrayList<>(List.of(n(w.beaufort()), s(w.phase())));
            for (Map.Entry<Weather.Var, String> e : Verbindung.WETTER.entrySet()) { cols.add(e.getValue()); vals.add(wert(e.getKey(), w.werte()[e.getKey().ordinal()])); }
            cols.add("MONATE"); vals.add(s(w.monate()));
            cols.add("BESCHREIBUNG"); vals.add(s(w.beschreibung()));
            merge(b, "RH_WETTERLAGE", new String[]{"NAME"}, new String[]{s(w.name())}, cols.toArray(new String[0]), vals.toArray(new String[0]), true);
        }

        kopf(b, "Regiebücher");
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) {
            Regie.Book bk = Regie.parse(r.text());
            Startdaten.Kopf k = Startdaten.Kopf.aus(bk);
            String[] cols = {"BESCHREIBUNG", "DATEI", "SZENE_MS", "SZENE_TEXT", "ZEITRAFFER", "LAENGE_S", "FOTOS", "WARTEN", "BLATT", "WETTER"};
            String[] vals = {s(r.beschreibung()), s(r.datei()), k.szeneMs() == null ? "NULL" : k.szeneMs().toString(), s(k.szeneMs() == null ? null : Daten.text(k.szeneMs())),
                    n(k.raffer()), n(bk.length()), Integer.toString(k.fotos()), Integer.toString(k.warten()), b(k.blatt()), s(Startdaten.wetterBlock(r.text()))};
            b.append("MERGE INTO RH_REGIEBUCH t USING (SELECT ").append(s(r.name())).append(" AS NAME FROM DUAL) q ON (t.NAME = q.NAME)\n")
                    .append("WHEN NOT MATCHED THEN INSERT (NAME, LAENGE_S, TEXT, HERKUNFT) VALUES (").append(s(r.name())).append(", ").append(n(bk.length()))
                    .append(", '-', ").append(M).append(");\n");
            StringBuilder upd = new StringBuilder();
            for (int c = 0; c < cols.length; c++) upd.append(c > 0 ? ", " : "").append(cols[c]).append(" = ").append(vals[c]);
            b.append("UPDATE RH_REGIEBUCH SET ").append(upd).append(",\n  TEXT = ").append(clob(r.text())).append("\n  WHERE NAME = ").append(s(r.name()))
                    .append(" AND HERKUNFT = ").append(M).append(";\n");
            String eltern = "FROM RH_REGIEBUCH WHERE NAME = " + s(r.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM RH_STICHWORT WHERE REGIEBUCH_ID IN (SELECT ID ").append(eltern).append(");\n");
            int nr = 0;
            for (Regie.Zeile zz : bk.zeilen())
                b.append("INSERT INTO RH_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) SELECT ID, ").append(++nr).append(", ").append(n(zz.sekunde())).append(", ")
                        .append(s(zz.befehl().toUpperCase(Locale.ROOT))).append(", ").append(s(zz.argumente().isEmpty() ? null : zz.argumente())).append(", ")
                        .append(s(zz.text().isEmpty() ? null : zz.text())).append(' ').append(eltern).append(";\n");
        }

        kopf(b, "Ansichten (Tasten 1–9)");
        for (Daten.Ansicht an : Startdaten.ansichten()) {
            double[] c = an.kamera();
            String[] cols = {"TASTE", "KAM_X", "KAM_Z", "KAM_GIER_GRAD", "KAM_NICK_GRAD", "AUGE_UEBER_GRUND_M", "AERA", "UNTER_WASSER", "LAGE"};
            String[] vals = {i(an.taste()), n(c[0]), n(c[2]), n(c[3]), n(c[4]), n(an.augeUeberGrund()), s(an.aera()), b(an.unterWasser()), punkt(c[0], c[2])};
            merge(b, "RH_ANSICHT", new String[]{"NAME", "ART"}, new String[]{s(an.name()), "'FEST'"}, cols, vals, true);
        }

        kopf(b, "Kamerafahrten");
        for (Daten.Fahrt f : Startdaten.fahrten())
            merge(b, "RH_FAHRT", new String[]{"NAME"}, new String[]{s(f.name())}, new String[]{"ART", "DAUER_S", "LAENGE_M", "BESCHREIBUNG"},
                    new String[]{s(f.art()), n(f.dauerS()), n(f.laengeM() == null ? null : Math.round(f.laengeM() * 10) / 10.0), s(f.beschreibung())}, true);

        kopf(b, "Presets der Packages");
        for (Daten.PaketPreset p : Startdaten.presets()) {
            merge(b, "RH_PAKET_PRESET", new String[]{"PAKET", "KLASSE", "NAME"}, new String[]{s(p.paket()), s(p.klasse()), s(p.name())}, new String[]{"BESCHREIBUNG"},
                    new String[]{s(p.beschreibung())}, true);
            String eltern = "FROM RH_PAKET_PRESET WHERE PAKET = " + s(p.paket()) + " AND KLASSE = " + s(p.klasse()) + " AND NAME = " + s(p.name()) + " AND HERKUNFT = " + M;
            b.append("DELETE FROM RH_PAKET_WERT WHERE PAKET_PRESET_ID IN (SELECT ID ").append(eltern).append(");\n");
            Map<String, String> typ = Startdaten.typen(p);
            for (Map.Entry<String, String> e : p.felder().entrySet())
                b.append("INSERT INTO RH_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) SELECT ID, ").append(s(e.getKey())).append(", ").append(s(typ.getOrDefault(e.getKey(), "STRING")))
                        .append(", ").append(s(e.getValue().isEmpty() ? null : e.getValue())).append(' ').append(eltern).append(";\n");
        }
        b.append("\nCOMMIT;\n\n-- Ende 04_startdaten.sql\n");
        return b.toString();
    }

    static double norm(double deg) { double d = deg % 360; return d < 0 ? d + 360 : d; }

    /** Die Zahl einer Wetterspalte als SQL (Wolkenart als Name). */
    static String wert(Weather.Var v, double x) {
        if (Double.isNaN(x)) return "NULL";
        if (v == Weather.Var.CLOUD) return s(Weather.CloudKind.values()[(int) Math.max(0, Math.min(Weather.CloudKind.values().length - 1, Math.round(x)))].name());
        return n(x);
    }
}
