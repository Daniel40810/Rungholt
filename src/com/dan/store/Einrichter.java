package com.dan.store;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Der Einrichter (aus Lighthouse, F34 dort, Haithabu F26): führt nummerierte SQL-Skripte der Reihe nach aus, trägt
 * fehlende Spalten nach, übersetzt ungültige Trigger und Sichten neu und prüft danach jede Tabelle, Spalte, Sequence,
 * jeden Trigger, Index, die Sichten, die Spatial-Metadaten, die räumlichen Indizes und die Startdaten. Er löscht nie
 * etwas. Am Ende meldet er „alles in Ordnung“ oder genau, was fehlt.
 * <p>
 * Was schon da ist, meldet Oracle beim zweiten Lauf mit einer bekannten Nummer (ORA-00955 Name vergeben, ORA-01430
 * Spalte vorhanden, ORA-02260/02261/02264/02275 Schlüssel vorhanden, ORA-01408 Index vorhanden, ORA-29879 räumlicher
 * Index vorhanden, ORA-00001 Zeile vorhanden, ORA-13223 Metadaten vorhanden); diese Meldungen sind kein Fehler.
 * <p>
 * Was eine App einrichtet, steht in den öffentlichen Feldern: Präfix, Skripte, Ausbaustufe, Sichten, Metadaten und
 * die Soll-Liste der Startdaten. Tabellen, Spalten, Sequenzen, Trigger und Indizes liest er aus dem ersten Skript.
 */
public final class Einrichter {

    /** Präfix der Objekte, etwa „HB_“. */
    public String praefix = "HB_";
    /** Die Skripte in der Reihenfolge, in der sie laufen; das erste legt die Tabellen an. */
    public String[] skripte = {"01_tabellen.sql", "02_spatial.sql", "03_sichten.sql", "04_startdaten.sql"};
    /** Die Ausbaustufe, die die App erwartet (Spalte VERSION der Tabelle {praefix}SCHEMA). */
    public int version = 1;
    /** Sichten, die gültig sein müssen. */
    public String[] sichten = {};
    /** Spatial-Metadaten „TABELLE.SPALTE“, die mit {@link #srid} eingetragen sein müssen. */
    public String[] metadaten = {};
    public int srid = 8307;
    /** Räumliche Indizes, die gültig sein müssen (DOMIDX_STATUS und DOMIDX_OPSTATUS VALID). */
    public String[] raeumlich = {};
    /** Startdaten: was, SQL mit COUNT(*), Soll-Zahl. */
    public Supplier<List<Soll>> startdaten = List::of;
    /** Weitere Abfragen, die rechnen müssen (räumliche Sichten): was, SQL mit einem Wert als Ergebnis. */
    public Supplier<List<Soll>> rechnet = List::of;

    /** Fortschritt für die Oberfläche: Text und Anteil 0..1. */
    public interface Fortschritt { void melde(String text, double anteil); }

    /** Eine Erwartung: was, SQL, Soll (für {@link #rechnet} ist soll -1: nur rechnen). */
    public record Soll(String was, String sql, int soll) { }

    /** Ergebnis: ok, Zeilen zum Anzeigen (je Befund eine), Zähler. */
    public record Ergebnis(boolean ok, List<String> zeilen, int ausgefuehrt, int vorhanden, int fehler) {
        public String kurz() {
            return ok ? "alles in Ordnung" : fehler + (fehler == 1 ? " Befund" : " Befunde") + ": "
                    + zeilen.stream().filter(z -> z.startsWith("FEHLT") || z.startsWith("FEHLER")).findFirst().orElse("");
        }
    }

    /** Oracle-Nummern, die beim Wiederholen kommen und nichts bedeuten. */
    public static final Set<Integer> HARMLOS = Set.of(1, 955, 1430, 2260, 2261, 2264, 2275, 1408, 29879, 13223);

    private String like() { return "'" + praefix.replace("_", "\\_") + "%' ESCAPE '\\'"; }

    // ------------------------------------------------------------------ einrichten

    public Ergebnis einrichten(Connection c, File dir, Fortschritt f) throws IOException, SQLException {
        if (f == null) f = (t, a) -> { };
        List<String> zeilen = new ArrayList<>();
        int run = 0, da = 0, err = 0;
        List<List<Skript.Anweisung>> alle = new ArrayList<>();
        int gesamt = 0;
        for (String s : skripte) {
            List<Skript.Anweisung> as = Skript.zerlegen(Files.readString(new File(dir, s).toPath(), StandardCharsets.UTF_8));
            alle.add(as);
            gesamt += as.size();
        }
        boolean auto = c.getAutoCommit();
        c.setAutoCommit(true);
        int k = 0;
        try (Statement st = c.createStatement()) {
            for (int i = 0; i < skripte.length; i++) {
                for (Skript.Anweisung a : alle.get(i)) {
                    k++;
                    if (k % 25 == 1 || a.plsql()) f.melde(skripte[i] + " · " + a.kurz(), k / (double) Math.max(1, gesamt));
                    if (a.sql().equalsIgnoreCase("COMMIT")) continue;
                    try {
                        st.execute(a.sql());
                        run++;
                    } catch (SQLException e) {
                        if (HARMLOS.contains(e.getErrorCode())) { da++; continue; }
                        err++;
                        zeilen.add("FEHLER " + skripte[i] + " Zeile " + a.zeile() + ": " + Db.erste(e) + " · " + a.kurz());
                    }
                }
                if (i == 0) {
                    int n = spaltenNachtragen(c, alle.get(0), zeilen);
                    if (n > 0) zeilen.add("ok " + n + (n == 1 ? " Spalte" : " Spalten") + " nachgetragen");
                    kompilieren(c);
                }
            }
            kompilieren(c);
        } finally {
            c.setAutoCommit(auto);
        }
        f.melde("Prüfen …", 1);
        Ergebnis p = pruefen(c, dir);
        List<String> z = new ArrayList<>();
        z.add(String.format(Locale.GERMAN, "ok %d Anweisungen ausgeführt, %d schon vorhanden", run, da));
        z.addAll(zeilen);
        z.addAll(p.zeilen());
        return new Ergebnis(err == 0 && p.ok(), z, run, da, err + p.fehler());
    }

    /** Fehlende Spalten aus dem ersten Skript als ALTER TABLE … ADD nachtragen (ohne NOT NULL). */
    int spaltenNachtragen(Connection c, List<Skript.Anweisung> as, List<String> zeilen) throws SQLException {
        Map<String, Set<String>> da = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT TABLE_NAME, COLUMN_NAME FROM USER_TAB_COLUMNS WHERE TABLE_NAME LIKE " + like())) {
            while (r.next()) da.computeIfAbsent(r.getString(1), x -> new java.util.HashSet<>()).add(r.getString(2));
        }
        int n = 0;
        try (Statement st = c.createStatement()) {
            for (Skript.Spalte s : Skript.spalten(as)) {
                Set<String> cols = da.get(s.tabelle());
                if (cols == null || cols.contains(s.name())) continue;
                String def = s.definition().replaceAll("(?i)\\s+NOT\\s+NULL", "");
                try {
                    st.execute("ALTER TABLE " + s.tabelle() + " ADD (" + s.name() + " " + def + ")");
                    n++;
                } catch (SQLException e) {
                    if (!HARMLOS.contains(e.getErrorCode())) zeilen.add("FEHLER Spalte " + s.tabelle() + "." + s.name() + " nicht nachgetragen: " + Db.erste(e));
                }
            }
        }
        return n;
    }

    /** Ungültige Trigger und Sichten neu übersetzen. */
    void kompilieren(Connection c) throws SQLException {
        List<String[]> l = new ArrayList<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT OBJECT_TYPE, OBJECT_NAME FROM USER_OBJECTS WHERE OBJECT_NAME LIKE " + like()
                     + " AND STATUS = 'INVALID' AND OBJECT_TYPE IN ('TRIGGER', 'VIEW')")) {
            while (r.next()) l.add(new String[]{r.getString(1), r.getString(2)});
        }
        try (Statement st = c.createStatement()) {
            for (String[] o : l) {
                try { st.execute("ALTER " + o[0] + " " + o[1] + " COMPILE"); } catch (SQLException e) { /* die Prüfung meldet es */ }
            }
        }
    }

    // ------------------------------------------------------------------ prüfen

    /** Prüft alles, was die Skripte anlegen; schreibt nichts. */
    public Ergebnis pruefen(Connection c, File dir) throws IOException, SQLException {
        List<String> z = new ArrayList<>();
        int fehler = 0;
        List<Skript.Anweisung> t01 = Skript.zerlegen(Files.readString(new File(dir, skripte[0]).toPath(), StandardCharsets.UTF_8));
        Set<String> tabellen = new LinkedHashSet<>(), sequenzen = new LinkedHashSet<>(), trigger = new LinkedHashSet<>(), indizes = new LinkedHashSet<>();
        for (Skript.Anweisung a : t01) {
            String o = Skript.objekt(a.sql());
            if (o == null) continue;
            String[] p = o.split(" ");
            switch (p[0]) {
                case "TABLE" -> tabellen.add(p[1]);
                case "SEQUENCE" -> sequenzen.add(p[1]);
                case "TRIGGER" -> trigger.add(p[1]);
                case "INDEX" -> indizes.add(p[1]);
                default -> { }
            }
        }
        indizes.addAll(List.of(raeumlich));
        Set<String> daT = Db.namen(c, "SELECT TABLE_NAME FROM USER_TABLES"), daS = Db.namen(c, "SELECT SEQUENCE_NAME FROM USER_SEQUENCES"),
                daI = Db.namen(c, "SELECT INDEX_NAME FROM USER_INDEXES");
        int fT = 0;
        for (String t : tabellen) if (!daT.contains(t)) { z.add("FEHLT Tabelle " + t); fT++; }
        fehler += fT;
        if (fT == 0) z.add("ok " + tabellen.size() + " Tabellen");
        Map<String, String> typ = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM USER_TAB_COLUMNS WHERE TABLE_NAME LIKE " + like())) {
            while (r.next()) typ.put(r.getString(1) + "." + r.getString(2), r.getString(3));
        }
        int fS = 0, nS = 0;
        for (Skript.Spalte s : Skript.spalten(t01)) {
            nS++;
            String ist = typ.get(s.tabelle() + "." + s.name());
            String soll = basis(s.typ());
            if (ist == null) { if (daT.contains(s.tabelle())) { z.add("FEHLT Spalte " + s.tabelle() + "." + s.name()); fS++; } }
            else if (!basis(ist).equals(soll)) { z.add("FEHLT Spalte " + s.tabelle() + "." + s.name() + " hat " + ist + " statt " + soll); fS++; }
        }
        fehler += fS;
        if (fS == 0 && fT == 0) z.add("ok " + nS + " Spalten mit passendem Typ");
        int f2 = 0;
        for (String s : sequenzen) if (!daS.contains(s)) { z.add("FEHLT Sequence " + s); f2++; }
        if (f2 == 0) z.add("ok " + sequenzen.size() + " Sequenzen");
        fehler += f2;
        Map<String, String> trg = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT t.TRIGGER_NAME, t.STATUS, o.STATUS FROM USER_TRIGGERS t JOIN USER_OBJECTS o "
                     + "ON o.OBJECT_NAME = t.TRIGGER_NAME AND o.OBJECT_TYPE = 'TRIGGER'")) {
            while (r.next()) trg.put(r.getString(1), r.getString(2) + "/" + r.getString(3));
        }
        int f3 = 0;
        for (String t : trigger) {
            String s = trg.get(t);
            if (s == null) { z.add("FEHLT Trigger " + t); f3++; }
            else if (!s.equals("ENABLED/VALID")) { z.add("FEHLT Trigger " + t + " ist " + s); f3++; }
        }
        if (f3 == 0) z.add("ok " + trigger.size() + " Trigger eingeschaltet und gültig");
        fehler += f3;
        int f4 = 0;
        for (String i : indizes) if (!daI.contains(i)) { z.add("FEHLT Index " + i + (i.endsWith("_SX") ? " (räumlich; Rechte siehe 02_spatial.sql)" : "")); f4++; }
        for (String sx : raeumlich) {
            if (!daI.contains(sx)) continue;
            String st = Db.einWert(c, "SELECT DOMIDX_STATUS || '/' || DOMIDX_OPSTATUS FROM USER_INDEXES WHERE INDEX_NAME = ?", sx);
            if (!"VALID/VALID".equals(st)) { z.add("FEHLT räumlicher Index " + sx + " ist " + st); f4++; }
        }
        fehler += f4;
        if (f4 == 0) z.add("ok " + indizes.size() + " Indizes" + (raeumlich.length > 0 ? ", davon " + raeumlich.length + " räumlich und gültig" : ""));
        int f6 = 0;
        for (String m : metadaten) {
            String[] tc = m.split("\\.");
            String s = Db.einWert(c, "SELECT TO_CHAR(SRID) FROM USER_SDO_GEOM_METADATA WHERE TABLE_NAME = ? AND COLUMN_NAME = ?", tc[0], tc[1]);
            if (!Integer.toString(srid).equals(s)) { z.add("FEHLT Spatial-Metadaten für " + m + " (SRID " + srid + "), gefunden: " + s); f6++; }
        }
        fehler += f6;
        if (f6 == 0 && metadaten.length > 0) z.add("ok Spatial-Metadaten für " + metadaten.length + " Spalten mit SRID " + srid);
        Map<String, String> views = new HashMap<>();
        try (Statement st = c.createStatement();
             ResultSet r = st.executeQuery("SELECT OBJECT_NAME, STATUS FROM USER_OBJECTS WHERE OBJECT_TYPE = 'VIEW'")) {
            while (r.next()) views.put(r.getString(1), r.getString(2));
        }
        int f5 = 0;
        for (String v : sichten) if (!"VALID".equals(views.get(v))) { z.add("FEHLT Sicht " + v + (views.containsKey(v) ? " ist " + views.get(v) : "")); f5++; }
        fehler += f5;
        if (f5 == 0 && sichten.length > 0) z.add("ok " + sichten.length + " Sichten gültig");
        if (fT > 0) return new Ergebnis(false, z, 0, 0, fehler);
        String v = Db.einWert(c, "SELECT TO_CHAR(MAX(VERSION)) FROM " + praefix + "SCHEMA");
        if (v == null || Integer.parseInt(v) < version) { z.add("FEHLT " + praefix + "SCHEMA: Stufe " + v + ", erwartet " + version); fehler++; }
        else z.add("ok Ausbaustufe " + v);
        for (Soll s : startdaten.get()) {
            String ist = Db.einWert(c, s.sql());
            int n = ist == null ? 0 : Integer.parseInt(ist);
            if (n != s.soll()) { z.add("FEHLT " + s.was() + ": " + n + " statt " + s.soll() + " mitgeliefert"); fehler++; }
            else z.add("ok " + n + " " + s.was());
        }
        for (Soll s : rechnet.get()) {
            try {
                z.add("ok " + s.was() + ": " + Db.einWert(c, s.sql()));
            } catch (SQLException e) {
                z.add("FEHLER " + s.was() + " rechnet nicht: " + Db.erste(e));
                fehler++;
            }
        }
        if (fehler == 0) z.add("alles in Ordnung");
        return new Ergebnis(fehler == 0, z, 0, 0, fehler);
    }

    static String basis(String t) {
        String s = t.toUpperCase(Locale.ROOT);
        int i = s.indexOf('(');
        if (i > 0) s = s.substring(0, i);
        if (s.startsWith("MDSYS.")) s = s.substring(6);
        if (s.startsWith("TIMESTAMP")) s = "TIMESTAMP";
        return s;
    }

    /** Für ein Startbild: Stufe des Schemas (0 = keine Tabellen mit dem Präfix, -1 = {praefix}SCHEMA fehlt, sonst VERSION). */
    public int stufe(Connection c) throws SQLException {
        String n = Db.einWert(c, "SELECT TO_CHAR(COUNT(*)) FROM USER_TABLES WHERE TABLE_NAME LIKE " + like());
        if (n == null || Integer.parseInt(n) == 0) return 0;
        try {
            String v = Db.einWert(c, "SELECT TO_CHAR(MAX(VERSION)) FROM " + praefix + "SCHEMA");
            return v == null ? -1 : Integer.parseInt(v);
        } catch (SQLException e) {
            return -1;
        }
    }
}
