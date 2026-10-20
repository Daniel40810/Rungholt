package com.dan.store.demo;

import com.dan.store.Ablage;
import com.dan.store.DateiAblage;
import com.dan.store.Dienst;
import com.dan.store.Einrichter;
import com.dan.store.Felder;
import com.dan.store.Protokoll;
import com.dan.store.Skript;
import com.dan.store.Wkt;
import com.dan.store.Zahl;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Prüfung des Packages ohne Fenster und ohne Datenbank (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class StoreCheck {
    private static int bad;

    /** Ein Objekt wie ein Package: öffentliche Felder aller Arten. */
    public static final class Probe {
        public enum Art { RUHIG, STURM }
        public double wind = 4.25, nan = Double.NaN;
        public int zahl = 7;
        public long lang = 1234567890123L;
        public boolean an = true;
        public String text = "Zeile 1\nZeile 2 mit \\ und =";
        public Art art = Art.STURM;
        public final double fest = 1;
        public static double global = 2;
        public double[] feld = {1, 2};
    }

    public static void main(String[] a) throws Exception {
        felder();
        skript();
        wkt();
        ablage();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Für die Prüfung der App: alles ohne System.exit; gibt die Zahl der Verstöße zurück. */
    public static int pruefen() throws Exception {
        bad = 0;
        felder();
        skript();
        wkt();
        ablage();
        return bad;
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    static void felder() {
        Probe p = new Probe();
        LinkedHashMap<String, String> m = Felder.lesen(p);
        check(m.size() == 7 && !m.containsKey("fest") && !m.containsKey("global") && !m.containsKey("feld"),
                "Felder: 7 einfache öffentliche Felder gelesen, finale, statische und Arrays bleiben außen vor (" + m.keySet() + ")");
        String t = Felder.alsText(m, "Kopf\nzweite Zeile");
        LinkedHashMap<String, String> back = Felder.ausText(t);
        check(back.equals(m), "Textformat hin und zurück gleich, auch mit Zeilenumbruch, Rückstrich und =");
        Probe q = new Probe();
        q.wind = 0; q.zahl = 0; q.lang = 0; q.an = false; q.text = ""; q.art = Probe.Art.RUHIG; q.nan = 3;
        List<String> f = Felder.schreiben(q, back);
        check(f.isEmpty() && q.wind == 4.25 && q.zahl == 7 && q.lang == 1234567890123L && q.an && q.text.equals(p.text) && q.art == Probe.Art.STURM && Double.isNaN(q.nan),
                "Felder schreiben stellt alle Werte wieder her (auch NaN und die Aufzählung)");
        Map<String, String> falsch = new LinkedHashMap<>();
        falsch.put("wind", "viel");
        falsch.put("gibtsnicht", "1");
        falsch.put("an", "vielleicht");
        check(Felder.schreiben(q, falsch).size() == 3 && q.wind == 4.25, "Falsche Werte und unbekannte Felder werden gemeldet und nicht geschrieben");
        check(Zahl.text(0.1 + 0.2).equals("0.3") && Zahl.text(5).equals("5") && Double.isNaN(Zahl.lies("")) && Zahl.lies("2,5") == 2.5,
                "Zahlen: Punkt, ohne überflüssige Nullen, Komma wird gelesen");
    }

    static void skript() {
        String sql = "-- Kommentar\nSET DEFINE OFF\nCREATE TABLE HB_X (\n  ID NUMBER(10) NOT NULL,\n  NAME VARCHAR2(60 CHAR),\n"
                + "  LAGE MDSYS.SDO_GEOMETRY,\n  CONSTRAINT HB_X_PK PRIMARY KEY (ID)\n);\nCREATE SEQUENCE HB_X_SEQ;\n"
                + "CREATE OR REPLACE TRIGGER HB_X_BIU BEFORE INSERT ON HB_X FOR EACH ROW\nBEGIN\n  :NEW.ID := HB_X_SEQ.NEXTVAL;\nEND;\n/\n"
                + "INSERT INTO HB_X (NAME) VALUES ('a;b');\nCOMMIT;\n";
        List<Skript.Anweisung> as = Skript.zerlegen(sql);
        check(as.size() == 5 && as.get(2).plsql() && as.get(2).sql().contains(":NEW.ID") && as.get(3).sql().endsWith("('a;b')"),
                "Skript: 5 Anweisungen, der Trigger als PL/SQL-Block bis /, ; in der Zeichenkette bleibt");
        List<Skript.Spalte> sp = Skript.spalten(as);
        check(sp.size() == 3 && sp.get(2).typ().equals("MDSYS.SDO_GEOMETRY") && Skript.laenge(sp.get(1)) == 60 && "TRIGGER HB_X_BIU".equals(Skript.objekt(as.get(2).sql())),
                "Spalten aus CREATE TABLE gelesen (3, mit Typ und Länge), Objekt des Triggers erkannt");
        Einrichter e = new Einrichter();
        check(e.praefix.equals("HB_") && e.skripte.length == 4, "Einrichter: Präfix und vier Skripte als Vorgabe");
    }

    static void wkt() {
        String p = Wkt.flaeche(new double[][]{{9, 54}, {9, 54.001}, {9.001, 54.001}, {9.001, 54}});
        double[][] q = Wkt.punkte(p);
        double area = 0;
        for (int i = 0; i + 1 < q.length; i++) area += q[i][0] * q[i + 1][1] - q[i + 1][0] * q[i][1];
        check(q.length == 5 && q[0][0] == q[4][0] && q[0][1] == q[4][1] && area > 0, "WKT: Fläche geschlossen und gegen den Uhrzeigersinn, auch wenn sie im Uhrzeigersinn kam");
        check(Wkt.punkt(9.5682101, 54.4911).equals("POINT (9.5682101 54.4911)") && Wkt.linie(new double[][]{{1, 2}, {3, 4}}).equals("LINESTRING (1 2, 3 4)"),
                "WKT: Punkt und Linie, Länge vor Breite");
    }

    static void ablage() throws Exception {
        File dir = Files.createTempDirectory("store").toFile();
        DateiAblage d = new DateiAblage(dir);
        Map<String, String> w = new LinkedHashMap<>();
        w.put("name", "Ostwind");
        w.put("wind", "8");
        d.eintrag("wetterlagen", "Ostwind über dem Noor", w, null);
        check(d.eintraege("wetterlagen").size() == 1 && d.eintraege("wetterlagen").get(0).equals(w)
                        && new File(dir, "wetterlagen/Ostwind ueber dem Noor.txt").isFile(),
                "Dateiablage: ein Eintrag geschrieben und gelesen, Dateiname ohne Umlaute");
        Protokoll.kontext(() -> "16.10.950 17:30", () -> "Hafen");
        Dienst.ersatz = () -> null;
        Dienst.setzen(d);
        long[] zahl = {0};
        Dienst.starten("Prüfung", "keine", "MITTEL", () -> {
            zahl[0]++;
            return new Ablage.Sicherung(Felder.alsText(Felder.lesen(new Probe()), null), Map.of("regiebuch", "Die Nacht von 1050"));
        });
        long s = Dienst.sitzung();
        Protokoll.warnung("Prüfung", "eine Warnung");
        Dienst.sichern(false);
        LinkedHashMap<String, String> ls = d.letzteSitzungText(-1);
        check(s >= 1 && ls != null && "Die Nacht von 1050".equals(ls.get("regiebuch")) && "false".equals(ls.get("sauber")) && Felder.ausText(ls.get("zustand")).get("wind").equals("4.25"),
                "Sitzung begonnen und gesichert: noch nicht sauber beendet, der Zustand ist lesbar");
        Dienst.beenden();
        ls = d.letzteSitzungText(-1);
        List<String> pz = d.letzteZeilen(10);
        check("true".equals(ls.get("sauber")) && zahl[0] == 2 && pz.stream().anyMatch(x -> x.contains("eine Warnung") && x.contains("Hafen") && x.contains("16.10.950")),
                "Beendet: sauber vermerkt, zweimal gesichert, das Protokoll mit Szenenzeit und Ort in protokoll.txt");
        check(d.letzteSitzungText(s) == null, "„Weiter wie zuletzt“ übergeht die laufende Sitzung");
    }
}
