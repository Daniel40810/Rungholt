package com.dan.store;

import java.util.List;
import java.util.Map;

/**
 * Was jede Ablage einer App können muss, damit {@link Dienst} Sitzung und Protokoll im Hintergrund führen kann: in
 * die Datenbank oder, ohne Datenbank, in Dateien. Die App erweitert die Schnittstelle um ihre eigenen Daten.
 */
public interface Ablage {

    /** Ein Fehler beim Lesen oder Schreiben, mit lesbarer Meldung. */
    final class Fehler extends Exception {
        public Fehler(String m, Throwable cause) { super(m, cause); }
        public Fehler(String m) { super(m); }
    }

    /**
     * Was beim Sichern einer Sitzung geschrieben wird: der Zustand im Textformat von {@link Felder#alsText} und Werte
     * für eigene Spalten der App (etwa Szenenzeit, Regiebuch, Stelle), Schlüssel wie die Spalten.
     */
    record Sicherung(String zustand, Map<String, String> spalten) { }

    /** „Datenbank HAITHABU auf PDBORCL“ oder „Dateien in …/daten“. */
    String wo();

    boolean datenbank();

    /** Neue Sitzung; gibt ihre Nummer zurück. */
    long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler;

    void sitzungSichern(long id, Sicherung s) throws Fehler;

    void sitzungBeenden(long id) throws Fehler;

    void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler;

    void schliessen();
}
