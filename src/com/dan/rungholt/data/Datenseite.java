package com.dan.rungholt.data;

import com.dan.store.Ablage;
import com.dan.store.Db;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;

/**
 * Die Datenseite der App (Phase 16): welcher {@link Speicher} gilt und die Brücke zu {@link Dienst} (Sitzung,
 * Protokoll) aus {@code com.dan.store}. Ohne Datenbank gilt {@link DateiSpeicher}; beides kann dasselbe (F34 A).
 */
public final class Datenseite {
    private Datenseite() { }

    private static volatile Speicher speicher;
    /** Das Ergebnis des letzten Einrichtens („alles in Ordnung“ oder der erste Befund), für Startbild und DATEN. */
    public static volatile String einrichterStand = "";
    /** Woher die Tide kam (F26): „Datenbank“, „Dateien“ oder „Code“. */
    public static volatile String tideHerkunft = "Code";

    static {
        Protokoll.app = "Rungholt";
        Dienst.faden = "Rungholt-Daten";
        Dienst.ersatz = () -> new DateiSpeicher(DateiSpeicher.ordner());
    }

    /** Der gültige Speicher (ohne Datenbank die Dateien in daten/). */
    public static Speicher speicher() {
        Speicher s = speicher;
        if (s == null) { s = new DateiSpeicher(DateiSpeicher.ordner()); speicher = s; Dienst.setzen(s); }
        return s;
    }

    /** Ob schon ein Speicher gesetzt ist (ohne einen anzulegen). */
    public static boolean gesetzt() { return speicher != null; }

    /** Den Speicher wechseln (nach Verbinden, Einrichten oder „Ohne Datenbank“); die Sitzung zieht mit um. */
    public static void setzen(Speicher s) {
        speicher = s;
        Dienst.setzen(s);
        Protokoll.info("Daten", "Speicher: " + s.wo());
    }

    /** Verbinden, wenn db/db.properties ein Passwort hat; sonst null (und warum in grund[0]). */
    public static DbSpeicher verbinden(Db.Konfig k, String[] grund) {
        try {
            if (!k.hatPasswort()) { if (grund != null) grund[0] = "kein Passwort"; return null; }
            return new DbSpeicher(k);
        } catch (Ablage.Fehler e) {
            if (grund != null) grund[0] = e.getMessage();
            return null;
        }
    }

    /** „Datenbank RUNGHOLT auf PDBORCL“ oder „Dateien in …/daten“ */
    public static String wo() { return speicher().wo(); }
}
