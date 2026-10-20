package com.dan.rungholt.data;

import com.dan.store.Ablage;

import java.time.LocalDate;
import java.util.List;

/**
 * Wohin die App speichert: in das Schema RUNGHOLT ({@link DbSpeicher}) oder, ohne Datenbank, in Dateien im Ordner
 * {@code daten/} ({@link DateiSpeicher}, F34 A). Beide können dasselbe; die Oberfläche kennt nur diese Schnittstelle.
 * Sitzung und Protokoll kommen aus {@link Ablage} (com.dan.store).
 * <p>
 * Die Chronik (Tiden, Zählungen, Läufe der Mandränke, Glocken, Läufe der Regiebücher) schreibt {@link Chronik} im
 * Faden der Daten. Mitgelieferte Einträge werden nie überschrieben, sondern abgelehnt.
 */
public interface Speicher extends Ablage {

    /** Schlüssel der Werte, die eine {@link Ablage.Sicherung} über die Sitzung mitbringt (Spalten von RH_SITZUNG). */
    String REGIEBUCH = "regiebuch", REGIE_SEKUNDE = "regieSekunde", SZENE_MS = "szeneMs", ZEITRAFFER = "zeitraffer", AERA = "aera", PEGEL = "pegel",
            WINDSTAU = "windstau", STUFE = "mandraenkeStufe";

    Daten.Stand stand() throws Fehler;

    // ------------------------------------------------------------------ Stammdaten (F25, F26, F27, F32)

    List<Daten.Quelle> quellen() throws Fehler;

    List<Daten.Befund> befunde() throws Fehler;

    List<Daten.Sturmflut> sturmfluten() throws Fehler;

    /** Die Pegelorte mit ihren Partialtiden (F26: die App baut daraus die Tide, der Code ist Rückfall). */
    List<Daten.Pegelort> pegelorte() throws Fehler;

    /** Hoch- und Niedrigwasser eines Pegels (Kurzzeichen) zwischen zwei Zeiten (ms, von einschließlich, bis ausschließlich). */
    List<Daten.Kalender> kalender(String pegel, long vonMs, long bisMs) throws Fehler;

    // ------------------------------------------------------------------ Rungholt (F30, F31)

    List<Daten.Warft> warften() throws Fehler;

    List<Daten.Bauwerk> bauwerke() throws Fehler;

    /** Alle Fundstellen: mitgelieferte, dann eigene (neueste zuerst). */
    List<Daten.Fundstelle> fundstellen() throws Fehler;

    /** Eine eigene Fundstelle anlegen (Umschalt + Klick ins Watt); gibt ihre Nummer zurück. */
    long fundstelle(Daten.Fundstelle f, Long sitzung) throws Fehler;

    /** Eine eigene Fundstelle löschen (mitgelieferte nie). */
    void loeschenFundstelle(long id) throws Fehler;

    /** Die Zellkarte: Fundstellen in 100 m, Warft, Niedrigwasser der Chronik und wie oft die Stelle nass blieb. */
    Daten.Zelle zelle(double x, double z) throws Fehler;

    // ------------------------------------------------------------------ Chronik (F28, F29)

    /** Ein Hoch- oder Niedrigwasser schreiben; gibt seine Nummer zurück. */
    long tide(Daten.Tide t, Long sitzung) throws Fehler;

    /** Die Zählungen eines Niedrigwassers. */
    void zaehlungen(long tide, List<Daten.Zaehlung> z, Long sitzung) throws Fehler;

    /** Einen Lauf der Mandränke anlegen (id 0) oder fortschreiben; gibt seine Nummer zurück. */
    long mandraenkeLauf(Daten.MandraenkeLauf l, Long sitzung) throws Fehler;

    void glocken(Daten.Glocken g, Long sitzung) throws Fehler;

    void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler;

    /** Die Tiden eines Szenentags (mit Wasserlinie), für die Tageskarte. */
    List<Daten.Tide> tidenAm(LocalDate tag) throws Fehler;

    /** Die Zählungen eines Szenentags. */
    List<Daten.Zaehlung> zaehlungenAm(LocalDate tag) throws Fehler;

    /** Die Läufe der Mandränke, neueste zuerst. */
    List<Daten.MandraenkeLauf> mandraenkeLaeufe() throws Fehler;

    /** Die Tiden der Chronik, neueste zuerst, höchstens n (ohne Wasserlinie). */
    List<Daten.Tide> tiden(int n) throws Fehler;

    /** Die eigenen Zeilen der Chronik einer Sitzung löschen; gibt zurück, wie viele. */
    int chronikLeeren(long sitzung) throws Fehler;

    /** Sitzungen mit Chronik: {id, Beginn ms, Tiden, Zählungen}, neueste zuerst. */
    List<long[]> sitzungenMitChronik(int n) throws Fehler;

    // ------------------------------------------------------------------ Regie und Kamera

    List<Daten.Wetterlage> wetterlagen() throws Fehler;

    List<Daten.Regiebuch> regiebuecher() throws Fehler;
    void speichern(Daten.Regiebuch r) throws Fehler;
    void loeschenRegiebuch(String name) throws Fehler;

    List<Daten.Fahrt> fahrten() throws Fehler;

    /** Ansichten ohne Zustand und Vorschau (für Listen): feste zuerst, dann gemerkte, neueste zuerst. */
    List<Daten.Ansicht> ansichten() throws Fehler;
    /** Eine gemerkte Ansicht ganz, mit Zustand und Vorschau. */
    Daten.Ansicht ansicht(long id) throws Fehler;
    /** Speichert eine neue gemerkte Ansicht und gibt ihre Nummer zurück. */
    long speichern(Daten.Ansicht a, Long sitzung) throws Fehler;
    void loeschenAnsicht(long id) throws Fehler;

    // ------------------------------------------------------------------ Packages

    /** Presets eines Package (paket = „com.dan.ocean“, klasse = „Ocean“); null für alle. */
    List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler;
    void speichern(Daten.PaketPreset p) throws Fehler;
    void loeschenPreset(String paket, String klasse, String name) throws Fehler;

    // ------------------------------------------------------------------ Betrieb

    /** Die letzte Sitzung mit Zustand vor der laufenden (oder null). */
    Daten.Sitzung letzteSitzung(long ausser) throws Fehler;

    /** Die letzten n Zeilen des Protokolls zum Anzeigen. */
    List<String> letzteZeilen(int n) throws Fehler;
}
