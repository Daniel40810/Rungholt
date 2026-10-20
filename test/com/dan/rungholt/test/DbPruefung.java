package com.dan.rungholt.test;

import com.dan.rungholt.data.DateiSpeicher;
import com.dan.rungholt.data.Daten;
import com.dan.rungholt.data.DbSpeicher;
import com.dan.rungholt.data.Pegel;
import com.dan.rungholt.data.Speicher;
import com.dan.rungholt.data.Startdaten;
import com.dan.rungholt.data.Tageskarte;
import com.dan.rungholt.data.Uebernahme;
import com.dan.rungholt.data.Verbindung;
import com.dan.rungholt.data.Wasserlinie;
import com.dan.rungholt.scene.Land1362;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Terrain;
import com.dan.store.Ablage;
import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Phase 16: die Prüfung gegen die echte Datenbank (PDBORCL, Schema RUNGHOLT aus {@code db/db.properties}). In NetBeans:
 * Rechtsklick, „Run File“. Sie richtet ein (wiederholbar), läuft den Einrichter ein zweites Mal (muss ohne Fehler
 * durchgehen), prüft mit ihm, rechnet Kette, Kronen und Ausschnitt auf dem Ellipsoid nach, prüft alle Geometrien auf
 * Gültigkeit, liest die Tide aus der Datenbank (F26, höchstens 1 mm vom Code), fragt den Tidenkalender und die Sichten
 * (F27, F33), schreibt eine Chronik für den 31.12.2099 (Tide mit Wasserlinie, Zählungen, Lauf der Mandränke mit
 * Bresche, Glocken, Lauf eines Regiebuchs mit Blatt) und liest sie über die Sichten und als Tageskarte zurück, legt
 * eine eigene Fundstelle an und fragt die Zellkarte (räumliche Operatoren), speichert Regiebuch, Ansicht und Preset
 * unter „~Prüfung …“, sichert eine Sitzung, übernimmt Dateien und räumt hinterher auf. Ende: „Alles in Ordnung.“
 * <p>
 * Die Ausgabe bitte als Text oder Bildschirmfoto schicken; jede Zeile mit FEHLER sagt, was nicht ging.
 */
public final class DbPruefung {
    private static int ok, bad;
    private static final String P = "~Prüfung ";
    /** Der Tag der Prüfchronik: kommt in keiner Szene vor. */
    static final LocalDate TAG = LocalDate.of(2099, 12, 31);

    static void check(boolean c, String was) {
        if (c) ok++; else bad++;
        System.out.println((c ? "  ok     " : "  FEHLER ") + was);
    }

    static double zahl(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getDouble(1) : Double.NaN; }
    }

    static String text(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) { return r.next() ? r.getString(1) : null; }
    }

    static long ms(LocalDate d, int h, int m) { return ZonedDateTime.of(d.getYear(), d.getMonthValue(), d.getDayOfMonth(), h, m, 0, 0, SceneClock.ZONE).toInstant().toEpochMilli(); }

    public static void main(String[] args) throws Exception {
        long t0 = System.nanoTime();
        System.out.println("Rungholt · Prüfung der Datenbank (Phase 16)");
        Db.Konfig k = Verbindung.konfig();
        System.out.println("Verbindung " + k.kurz());
        if (!k.hatPasswort()) {
            System.out.println("  FEHLER Kein Passwort in db/db.properties und keine Umgebungsvariable " + Verbindung.ENV
                    + ". Einmal mit dem Startbild verbinden (es trägt das Passwort ein) oder es von Hand eintragen.");
            System.exit(1);
        }
        DbSpeicher db = new DbSpeicher(k);
        System.out.println(db.wo());
        Connection c = db.verbindung();
        Einrichter ein = Verbindung.einrichter();

        System.out.println("Einrichter");
        Einrichter.Ergebnis e1 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        for (String z : e1.zeilen()) if (!z.startsWith("ok")) System.out.println("    " + z);
        check(e1.ok(), "Erster Lauf: " + e1.kurz() + " (" + e1.ausgefuehrt() + " ausgeführt, " + e1.vorhanden() + " schon da)");
        Einrichter.Ergebnis e2 = ein.einrichten(c, Verbindung.skripte(), (t, a) -> { });
        check(e2.ok() && e2.fehler() == 0, "Zweiter Lauf geht ohne Fehler durch (wiederholbar): " + e2.kurz());
        Einrichter.Ergebnis pr = ein.pruefen(c, Verbindung.skripte());
        for (String z : pr.zeilen()) System.out.println("    " + z);
        check(pr.ok(), "Einrichter prüft: " + pr.kurz());
        check(ein.stufe(c) == Verbindung.VERSION, "Ausbaustufe " + ein.stufe(c));

        System.out.println("Trigger und Spatial");
        try (Statement st = c.createStatement()) {
            st.executeUpdate("INSERT INTO RH_WETTERLAGE (NAME, HERKUNFT) VALUES ('" + P + "Trigger', 'EIGEN')");
            try (ResultSet r = st.executeQuery("SELECT ID, ANGELEGT, GEAENDERT FROM RH_WETTERLAGE WHERE NAME = '" + P + "Trigger'")) {
                check(r.next() && r.getLong(1) > 0 && r.getTimestamp(2) != null && r.getTimestamp(3) != null, "Trigger setzt ID, ANGELEGT und GEAENDERT");
            }
            st.executeUpdate("DELETE FROM RH_WETTERLAGE WHERE NAME = '" + P + "Trigger'");
        }
        double kette = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_LENGTH(GEOM, 0.05, 'unit=M') FROM RH_BAUWERK WHERE NAME = 'Warftenkette'"),
                flaeche = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_AREA(AUSSCHNITT, 0.05, 'unit=SQ_KM') FROM RH_GELAENDE WHERE AERA = 'HEUTE'"),
                kronen = zahl(c, "SELECT SUM(KRONE_M2) FROM RH_WARFT_V");
        double kronenSzene = 0;
        for (Daten.Warft w : Startdaten.warften()) kronenSzene += w.flaeche().flaecheM2();
        double ausschnitt = Math.pow(Terrain.N * Terrain.CELL / 1000, 2);
        check(Math.abs(kette / Land1362.chainLength() - 1) < 0.01 && Math.abs(flaeche / ausschnitt - 1) < 0.01 && Math.abs(kronen / kronenSzene - 1) < 0.01, String.format(Locale.GERMAN,
                "Auf dem Ellipsoid (SDO_LENGTH, SDO_AREA): Warftenkette %.2f km (Szene %.2f km), Ausschnitt %.1f km² (Szene %.1f km²), Kronen der 54 Warften %.2f ha (Szene %.2f ha)",
                kette / 1000, Land1362.chainLength() / 1000, flaeche, ausschnitt, kronen / 1e4, kronenSzene / 1e4));
        double ungueltig = 0;
        for (String m : Verbindung.METADATEN) {
            String[] tc = m.split("\\.");
            double u = zahl(c, "SELECT COUNT(*) FROM " + tc[0] + " WHERE " + tc[1] + " IS NOT NULL AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) <> 'TRUE'");
            if (u > 0) System.out.println("    ungültig in " + m + ": " + (int) u + " · "
                    + text(c, "SELECT MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) FROM " + tc[0] + " WHERE " + tc[1] + " IS NOT NULL AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(" + tc[1] + ", 0.05) <> 'TRUE' AND ROWNUM = 1"));
            ungueltig += u;
        }
        check(ungueltig == 0, "Alle Geometrien gültig (VALIDATE_GEOMETRY_WITH_CONTEXT) in " + Verbindung.METADATEN.length + " Spalten");
        double kw = zahl(c, "SELECT FUNDSTELLEN FROM RH_WARFT_V WHERE ART = 'KIRCHWARFT'"), mitFunden = zahl(c, "SELECT COUNT(*) FROM RH_WARFT_V WHERE FUNDSTELLEN > 0");
        String funde = text(c, "SELECT FUNDE FROM RH_WARFT_V WHERE ART = 'KIRCHWARFT'");
        check(kw >= 2 && mitFunden >= 5 && funde != null && funde.contains("Kirche"), String.format(Locale.GERMAN,
                "RH_WARFT_V (SDO_WITHIN_DISTANCE 100 m): Kirchwarft mit %.0f Fundstellen (%s), %.0f Warften mit Funden", kw, funde, mitFunden));
        double rv = zahl(c, "SELECT COUNT(*) FROM RH_RUNGHOLT_V"), rvw = zahl(c, "SELECT COUNT(*) FROM RH_RUNGHOLT_V WHERE EBENE = 'WARFT'");
        check(rv == 54 + Startdaten.bauwerke(Terrain.load(Terrain.defaultDir())).size() + zahl(c, "SELECT COUNT(*) FROM RH_FUNDSTELLE") && rvw == 54,
                String.format(Locale.GERMAN, "RH_RUNGHOLT_V für QGIS: %.0f Objekte (54 Warften, Bauwerke, Fundstellen)", rv));
        String befund = text(c, "SELECT QUELLEN FROM RH_BEFUND_V WHERE GEGENSTAND = 'Kirche'");
        check(befund != null && befund.contains("JGU23"), "RH_BEFUND_V mit Quellen: „Kirche“ ← " + befund);
        double sf = zahl(c, "SELECT COUNT(*) FROM RH_STURMFLUT"), capella = zahl(c, "SELECT SCHEITEL_HUSUM_CM FROM RH_STURMFLUT WHERE JAHR = 1976 AND TAG = 3");
        check(sf == Startdaten.sturmfluten().size() && capella == 566, String.format(Locale.GERMAN, "%.0f Sturmfluten; Capella 1976 Scheitel Husum %.0f cm über NN", sf, capella));

        System.out.println("Tide aus der Datenbank (F26, F27, F33)");
        List<Daten.Pegelort> po = db.pegelorte();
        Daten.Pegelort rand = po.stream().filter(p -> p.kurz().equals(Startdaten.RAND)).findFirst().orElse(null),
                husum = po.stream().filter(p -> p.kurz().equals(Startdaten.HUSUM)).findFirst().orElse(null);
        double dR = rand == null ? 1 : Pegel.abweichung(rand, com.dan.tide.Tide.hever(), 2026), dH = husum == null ? 1 : Pegel.abweichung(husum, com.dan.tide.Tide.husum(), 2026);
        check(po.size() == 3 && dR <= 0.001 && dH <= 0.001, String.format(Locale.GERMAN, "Partialtiden aus RH_PARTIALTIDE: Rand %.3f mm, Husum %.3f mm vom Code über 2026", dR * 1000, dH * 1000));
        List<Daten.Kalender> kt = db.kalender(Startdaten.RAND, ms(LocalDate.of(2026, 7, 21), 0, 0), ms(LocalDate.of(2026, 7, 22), 0, 0));
        Daten.Kalender nw = kt.stream().filter(x -> x.art().equals("NW") && x.stunde() > 12).findFirst().orElse(null);
        check(kt.size() == 4 && nw != null && nw.text().startsWith("21.07.2026 13:"), "Tidenkalender am Rand, 21.7.2026: " + kt.size() + " Wenden, Niedrigwasser " + (nw == null ? "—" : nw.text()));
        double spring = zahl(c, "SELECT COUNT(*) FROM RH_SPRINGNIPP_V WHERE PEGEL = 'NORDERHEVER' AND TIDE = 'SPRING' AND TAG LIKE '2026-%'"),
                nwTag = zahl(c, "SELECT COUNT(*) FROM RH_NW_TAG_V WHERE TAG LIKE '2026-07-%'");
        check(spring > 40 && spring < 200 && nwTag > 15, String.format(Locale.GERMAN, "RH_SPRINGNIPP_V: %.0f Springtage am Rand 2026; RH_NW_TAG_V: %.0f Niedrigwasser bei Tag im Juli", spring, nwTag));
        double j1362 = zahl(c, "SELECT COUNT(*) FROM RH_TIDENKALENDER WHERE SZENE_TAG LIKE '1362-01-%'");
        String t1362 = text(c, "SELECT SZENE_TEXT FROM RH_TIDENKALENDER WHERE SZENE_TAG = '1362-01-15' AND ART = 'HW' ORDER BY SZENE_MS FETCH FIRST 1 ROWS ONLY");
        check(j1362 > 110 && t1362 != null && t1362.endsWith("Ortszeit"), String.format(Locale.GERMAN, "Januar 1362 am Rand: %.0f Wenden, erstes Hochwasser am 15. %s (F33: ms und Text, proleptisch gregorianisch)", j1362, t1362));

        System.out.println("Sitzung und Chronik (31.12.2099)");
        long sid = db.sitzungBeginnen(P.strip(), "Prüfung", "MITTEL");
        Long s = sid;
        long nwMs = ms(TAG, 13, 0);
        // eine nasse Fläche um die Kirche: Ring mit Loch und Insel (wie die Wasserlinie bei Niedrigwasser)
        String[] rows = {"########", "########", "##....##", "##.##.##", "##.##.##", "##....##", "########", "########"};
        boolean[] n = new boolean[64];
        for (int j = 0; j < 8; j++) for (int i = 0; i < 8; i++) n[j * 8 + i] = rows[j].charAt(i) == '#';
        Daten.Geom wl = Wasserlinie.flaeche(new Wasserlinie.Gitter(n, 8, 8, -256, -256, 64));
        long tid = db.tide(new Daten.Tide(0, nwMs, Daten.HEUTE, "NW", -1.62, -1.75, 0.13, 0.58, 240, 0.9, wl), s);
        db.tide(new Daten.Tide(0, ms(TAG, 19, 12), Daten.HEUTE, "HW", 1.71, 1.58, 0.13, 0.05, 0, 0.6, null), s);
        db.zaehlungen(tid, List.of(new Daten.Zaehlung(nwMs, "SEEHUND", "Seehundbank [G]", 31, 24, 5, 2, -2980, 200),
                new Daten.Zaehlung(nwMs, "VOGEL", "Austernfischer", 150, null, null, null, 300, 500)), s);
        double gueltig = zahl(c, "SELECT COUNT(*) FROM RH_TIDE WHERE ID = " + tid + " AND MDSYS.SDO_GEOM.VALIDATE_GEOMETRY_WITH_CONTEXT(WASSERLINIE, 0.05) = 'TRUE'"),
                nassM2 = zahl(c, "SELECT MDSYS.SDO_GEOM.SDO_AREA(WASSERLINIE, 0.05, 'unit=SQ_M') FROM RH_TIDE WHERE ID = " + tid),
                ringe = zahl(c, "SELECT RINGE FROM RH_TIDE WHERE ID = " + tid);
        check(gueltig == 1 && Math.abs(nassM2 / wl.flaecheM2() - 1) < 0.01 && ringe == 3, String.format(Locale.GERMAN,
                "Wasserlinie als gültige Mehrfachfläche (Ring mit Loch, Insel darin): %,.0f m² auf dem Ellipsoid (Szene %,.0f m²), %.0f Ringe", nassM2, wl.flaecheM2(), ringe));
        Daten.Geom bresche = Daten.Geom.linie(new double[][]{{-675, com.dan.rungholt.scene.Stadt1362.dikeZ(-675)}, {-625, com.dan.rungholt.scene.Stadt1362.dikeZ(-625)}});
        long b0 = ms(TAG, 16, 0);
        long lid = db.mandraenkeLauf(new Daten.MandraenkeLauf(0, "KIRCHE", b0, null, null, null, null, null, "VORABEND", 2.1, Double.NaN, 86, 0, 0, null, null, "Die Grote Mandränke"), s);
        db.mandraenkeLauf(new Daten.MandraenkeLauf(lid, "KIRCHE", b0, b0 + 5_400_000L, b0 + 7_000_000L, b0 + 10_600_000L, b0 + 10_780_000L, null, "FLUT", 5.1, 2.4, 86, 12, 9,
                new double[]{-650, com.dan.rungholt.scene.Stadt1362.dikeZ(-650)}, bresche, "Die Grote Mandränke"), s);
        double brM = zahl(c, "SELECT BRESCHE_M FROM RH_MANDRAENKE_V WHERE ID = " + lid), bruchS = zahl(c, "SELECT BRUCH_S FROM RH_MANDRAENKE_V WHERE ID = " + lid);
        String rbM = text(c, "SELECT REGIEBUCH FROM RH_MANDRAENKE_V WHERE ID = " + lid);
        check(Math.abs(brM - bresche.laenge()) < 1 && bruchS == 180 && "Die Grote Mandränke".equals(rbM), String.format(Locale.GERMAN,
                "Lauf der Mandränke %d angelegt und fortgeschrieben: Bresche %.1f m auf dem Ellipsoid, %.0f s vom Bruch bis zur Flut, Regiebuch „%s“ (RH_MANDRAENKE_V)", lid, brM, bruchS, rbM));
        db.glocken(new Daten.Glocken(ms(TAG, 21, 30), 84, 24.5, 280, 1.6, true), s);
        byte[] png = new byte[7000];
        for (int i = 0; i < png.length; i++) png[i] = (byte) (i * 13);
        long now = System.currentTimeMillis();
        db.regielauf(new Daten.Regielauf("Eine Tide", now - 360_000, now, true, true, 53.2, 9.4, 21, 10, "MITTEL", png, "docs/status/x.png"), s);
        double blob = zahl(c, "SELECT DBMS_LOB.GETLENGTH(BLATT) FROM RH_REGIELAUF WHERE SITZUNG_ID = " + sid),
                rbid = zahl(c, "SELECT COUNT(*) FROM RH_REGIELAUF WHERE SITZUNG_ID = " + sid + " AND REGIEBUCH_ID IS NOT NULL AND KINO = 1");
        check(blob == png.length && rbid == 1, "Lauf eines Regiebuchs mit Blatt (BLOB " + (int) blob + " Bytes) im Kino, dem mitgelieferten Regiebuch zugeordnet");
        double karte = zahl(c, "SELECT COUNT(*) FROM RH_TAGESKARTE_V WHERE TAG = '2099-12-31'");
        String ebenen = text(c, "SELECT LISTAGG(EBENE, ',') WITHIN GROUP (ORDER BY EBENE) FROM (SELECT DISTINCT EBENE FROM RH_TAGESKARTE_V WHERE TAG = '2099-12-31')");
        double tv = zahl(c, "SELECT SEEHUNDE FROM RH_TIDE_TAG_V WHERE TAG = '2099-12-31'");
        check(karte == 5 && "BRESCHE,BRUCHSTELLE,WASSERLINIE,ZAEHLUNG".equals(ebenen) && tv == 31,
                String.format(Locale.GERMAN, "RH_TAGESKARTE_V am 31.12.2099: %.0f Objekte in den Ebenen %s; RH_TIDE_TAG_V %.0f Seehunde", karte, ebenen, tv));
        List<Daten.Tide> am = db.tidenAm(TAG);
        Daten.Tide nwz = am.stream().filter(x -> x.wasserlinie() != null).findFirst().orElse(null);
        check(am.size() == 2 && nwz != null && nwz.wasserlinie().ringe().size() == 2 && Math.abs(nwz.wasserlinie().flaecheM2() - wl.flaecheM2()) < 20,
                "Tiden des Tages zurückgelesen: die Wasserlinie aus WKT wieder in Szenenmetern (Fläche auf 20 m² gleich)");

        System.out.println("Fundstelle und Zellkarte (F31, F36)");
        long fid = db.fundstelle(new Daten.Fundstelle(0, P + "Scherbe", "eigener Fund", 2099, "Prüfung", 30, 20, Daten.GESTALTET, null, "Prüfung", Daten.EIGEN), s);
        Daten.Zelle z1 = db.zelle(-256 + 2.5 * 64, -256 + 2.5 * 64), z2 = db.zelle(-256 + 0.5 * 64, -256 + 0.5 * 64), z3 = db.zelle(0, 0);
        double nwAlle = zahl(c, "SELECT COUNT(*) FROM RH_TIDE WHERE ART = 'NW' AND WASSERLINIE IS NOT NULL");
        check(z3.funde().stream().anyMatch(f -> f.startsWith(P + "Scherbe")) && z3.funde().stream().anyMatch(f -> f.startsWith("Kirche")) && "Kirchwarft".equals(z3.warft())
                        && z3.warftAbstandM() == 0 && z1.niedrigwasser() == nwAlle && z2.nass() >= 1,
                String.format(Locale.GERMAN, "Zellkarte (SDO_WITHIN_DISTANCE, SDO_NN, SDO_ANYINTERACT): an der Kirche %s; Kirchwarft; Ring %d-mal, Loch %d-mal nass von %d Niedrigwassern",
                        z3.funde(), z2.nass(), z1.nass(), z1.niedrigwasser()));
        boolean geschuetzt = false;
        long mitgel = (long) zahl(c, "SELECT MIN(ID) FROM RH_FUNDSTELLE WHERE HERKUNFT = 'MITGELIEFERT'");
        try { db.loeschenFundstelle(mitgel); } catch (Ablage.Fehler e) { geschuetzt = true; }
        check(geschuetzt, "Eine mitgelieferte Fundstelle wird nicht gelöscht");
        File tmp = Files.createTempDirectory("rungholt-dbpruefung").toFile();
        Tageskarte.Ergebnis tk = Tageskarte.zeichnen(db, Terrain.load(Terrain.defaultDir()), TAG, tmp);
        check(tk.datei().isFile() && tk.tiden() == 2 && tk.wasserlinie() && tk.zaehlungen() == 2, "Tageskarte aus der Datenbank: " + tk.datei().getName() + ", "
                + tk.tiden() + " Tiden mit Wasserlinie, " + tk.zaehlungen() + " Zählungen, " + tk.fundstellen() + " Fundstellen");

        System.out.println("Regiebuch, Ansicht, Preset, Sitzung");
        String rt = "Regiebuch: " + P + "Abend\nLänge: 0:30\n0:00 zeit 21.07.2026 19:00 | Abend\n0:05 ansicht Vom Deich\n0:20 foto Abend\n0:29 blatt\n";
        db.speichern(new Daten.Regiebuch(P + "Abend", rt, "", null, Daten.EIGEN));
        long aid = db.speichern(new Daten.Ansicht(0, P + "Ansicht", null, "GEMERKT", new double[]{209, 40, -216, 224, -2}, null, Daten.A1362, false, ms(LocalDate.of(1362, 1, 15), 18, 0),
                "a=1\nb=2\n", png, Daten.EIGEN), s);
        var oc = com.dan.ocean.Ocean.watt(4);
        db.speichern(new Daten.PaketPreset("com.dan.ocean", "Ocean", P + "Watt", "Prüfung", Daten.EIGEN, Felder.lesen(oc)));
        Daten.Regiebuch rb = db.regiebuecher().stream().filter(x -> x.name().equals(P + "Abend")).findFirst().orElseThrow();
        Daten.Ansicht an = db.ansicht(aid);
        double sw = zahl(c, "SELECT COUNT(*) FROM RH_STICHWORT x JOIN RH_REGIEBUCH r ON r.ID = x.REGIEBUCH_ID WHERE r.NAME = '" + P + "Abend'");
        double lageA = zahl(c, "SELECT a.LAGE.SDO_POINT.Y FROM RH_ANSICHT a WHERE ID = " + aid);
        String aText = text(c, "SELECT SZENE_TEXT FROM RH_ANSICHT WHERE ID = " + aid);
        var oc2 = com.dan.ocean.Ocean.calm();
        Felder.schreiben(oc2, db.presets("com.dan.ocean", "Ocean").stream().filter(x -> x.name().equals(P + "Watt")).findFirst().orElseThrow().felder());
        check(rb.text().equals(rt) && sw == 4 && java.util.Arrays.equals(an.vorschau(), png) && an.szeneMs() == ms(LocalDate.of(1362, 1, 15), 18, 0) && "1362".equals(an.aera())
                        && Math.abs(lageA - Daten.lonLat(209, -216)[1]) < 1e-7 && Felder.lesen(oc2).equals(Felder.lesen(oc)) && "15.01.1362 18:00 Ortszeit".equals(aText),
                "Regiebuch (Text als CLOB, 4 Stichworte), Ansicht von 1362 mit Vorschau (BLOB), Szenenzeit als ms und Text, Lage als Punkt, Preset gespeichert und gelesen");
        boolean abgelehnt = false;
        try { db.speichern(new Daten.Regiebuch("Eine Tide", rt.replace(P + "Abend", "Eine Tide"), "", null, Daten.EIGEN)); } catch (Ablage.Fehler e) { abgelehnt = true; }
        check(abgelehnt, "Ein mitgeliefertes Regiebuch („Eine Tide“) wird nicht überschrieben");
        Map<String, String> sp = new LinkedHashMap<>();
        sp.put(Speicher.SZENE_MS, Long.toString(ms(LocalDate.of(1362, 1, 15), 22, 30)));
        sp.put(Speicher.REGIEBUCH, P + "Abend");
        sp.put(Speicher.REGIE_SEKUNDE, "12.5");
        sp.put(Speicher.ZEITRAFFER, "60");
        sp.put(Speicher.AERA, "1");
        sp.put(Speicher.PEGEL, "4.81");
        sp.put(Speicher.WINDSTAU, "3.4");
        sp.put(Speicher.STUFE, "UEBERLAUF");
        db.sitzungSichern(sid, new Ablage.Sicherung("uhr.ms=" + sp.get(Speicher.SZENE_MS) + "\n", sp));
        Daten.Sitzung ls = db.letzteSitzung(-1);
        String lsv = text(c, "SELECT SZENE_TEXT || ' · ' || NVL(TO_CHAR(SZENENZEIT, 'YYYY'), 'ohne TIMESTAMP') FROM RH_LETZTE_SITZUNG_V");
        check(ls != null && ls.id() == sid && !ls.sauber() && ls.szeneMs() == ms(LocalDate.of(1362, 1, 15), 22, 30) && (P + "Abend").equals(ls.regiebuch()) && ls.aera() == 1
                        && "UEBERLAUF".equals(ls.mandraenkeStufe()) && lsv != null && lsv.endsWith("ohne TIMESTAMP"),
                "Sitzung gesichert: Szenenzeit 1362 als ms (" + lsv + "), Regiebuch mit Stelle, Ära, Stufe der Mandränke; noch nicht beendet");
        Protokoll.info("DbPrüfung", "eine Zeile aus der Prüfung");
        db.protokoll(Protokoll.abholen(), sid);
        db.sitzungBeenden(sid);
        double logz = zahl(c, "SELECT COUNT(*) FROM RH_LOG WHERE SITZUNG_ID = " + sid);
        check(logz >= 1 && zahl(c, "SELECT SAUBER_BEENDET FROM RH_SITZUNG WHERE ID = " + sid) == 1, "Protokoll in RH_LOG (" + (int) logz + " Zeilen), Sitzung sauber beendet");

        System.out.println("Übernahme aus Dateien (F34)");
        File dir = Files.createTempDirectory("rungholt-dbpruefung-dateien").toFile();
        DateiSpeicher d = new DateiSpeicher(dir);
        long dt = d.tide(new Daten.Tide(0, ms(TAG, 1, 30), Daten.HEUTE, "NW", -1.5, -1.6, 0.1, 0.55, 100, 0.8, wl), 7L);
        d.zaehlungen(dt, List.of(new Daten.Zaehlung(ms(TAG, 1, 30), "VOGEL", "Ringelgans", 300, null, null, null, 0, 900)), 7L);
        d.fundstelle(new Daten.Fundstelle(0, P + "aus Datei", "eigener Fund", 2099, "Prüfung", -40, 70, Daten.GESTALTET, null, "Prüfung", Daten.EIGEN), 7L);
        String ue = Uebernahme.uebernehmen(d, db);
        double tD = zahl(c, "SELECT COUNT(*) FROM RH_TIDE WHERE SZENE_MS = " + ms(TAG, 1, 30) + " AND WASSERLINIE IS NOT NULL"),
                zD = zahl(c, "SELECT COUNT(*) FROM RH_ZAEHLUNG z JOIN RH_TIDE t ON t.ID = z.TIDE_ID WHERE t.SZENE_MS = " + ms(TAG, 1, 30)),
                fD = zahl(c, "SELECT COUNT(*) FROM RH_FUNDSTELLE WHERE NAME = '" + P + "aus Datei'");
        check(tD == 1 && zD == 1 && fD == 1 && new File(dir, "chronik/tiden.csv.uebernommen").isFile(), "Übernommen: " + ue + "; die Zählung hängt an ihrer Tide, die CSV heißen *.uebernommen");
        Daten.Stand st = db.stand();
        check(st.warften() == 54 && st.presets() >= Startdaten.presets().size() && st.befunde() >= Startdaten.befunde().size() && st.eigeneFunde() >= 2,
                "Stand für das Startbild: " + st.warften() + " Warften, " + st.fundstellen() + " Fundstellen (" + st.eigeneFunde() + " eigene), " + st.kalender() + " Zeilen Tidenkalender, "
                        + st.tiden() + " Tiden, " + st.regiebuecher() + " Regiebücher, " + st.befunde() + " Befunde");

        System.out.println("Aufräumen");
        int weg1 = db.chronikLeeren(sid);
        db.loeschenRegiebuch(P + "Abend");
        db.loeschenAnsicht(aid);
        db.loeschenPreset("com.dan.ocean", "Ocean", P + "Watt");
        db.loeschenFundstelle(fid);
        try (Statement x = c.createStatement()) {
            x.executeUpdate("DELETE FROM RH_TIDE WHERE SZENE_TAG = '2099-12-31'");
            x.executeUpdate("DELETE FROM RH_FUNDSTELLE WHERE NAME LIKE '~%' AND HERKUNFT = 'EIGEN'");
            x.executeUpdate("DELETE FROM RH_LOG WHERE SITZUNG_ID = " + sid);
            x.executeUpdate("DELETE FROM RH_SITZUNG WHERE ID = " + sid);
        }
        double rest = zahl(c, "SELECT (SELECT COUNT(*) FROM RH_REGIEBUCH WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM RH_ANSICHT WHERE NAME LIKE '~%') "
                + "+ (SELECT COUNT(*) FROM RH_PAKET_PRESET WHERE NAME LIKE '~%') + (SELECT COUNT(*) FROM RH_FUNDSTELLE WHERE NAME LIKE '~%') "
                + "+ (SELECT COUNT(*) FROM RH_TAGESKARTE_V WHERE TAG = '2099-12-31') FROM DUAL");
        check(rest == 0 && weg1 >= 6, "Aufgeräumt: " + weg1 + " Zeilen der Prüfsitzung (Chronik leeren), nichts mit „~“ und nichts am 31.12.2099 bleibt");
        db.schliessen();

        System.out.printf(Locale.GERMAN, "%d geprüft, %d Fehler · %.1f s%n", ok + bad, bad, (System.nanoTime() - t0) / 1e9);
        System.out.println(bad == 0 ? "Alles in Ordnung." : "Nicht alles in Ordnung.");
        System.exit(bad == 0 ? 0 : 1);
    }
}
