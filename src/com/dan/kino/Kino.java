package com.dan.kino;

import java.util.ArrayList;
import java.util.List;

/**
 * Der Kinomodus: ein Regiebuch (Drehbuch) wie im Kino zeigen. Das Package kennt keine Szene; es sagt der App nur, was
 * über dem fertigen Bild liegen soll, und die App fragt es jedes Bild:
 * <ul>
 * <li><b>Balken</b> oben und unten auf Breitwand 2,39 : 1 ({@link #balkenPx}), sie fahren in 1,6 s ein und aus;</li>
 * <li><b>Vorspann</b>: aus dem Schwarz eine Titeltafel (Kopfzeile, Titel, Unterzeile), dann blendet das Bild auf;</li>
 * <li><b>Untertitel</b>: die Texte der Stichworte im unteren Balken, je nach Länge 3,5 bis 8 s, weich ein und aus;</li>
 * <li><b>Überblendung</b> bei jedem Schnitt (Ansicht, Fahrt, Boot): die App merkt sich das letzte Bild vor dem Schnitt
 * ({@link #schnittFaellig()}) und legt es 1,6 s lang abnehmend über das neue ({@link #ueberblende()});</li>
 * <li><b>Abspann</b>: das Bild blendet ab, eine Tafel mit den Zeilen des Abspanns, dann fahren die Balken heraus;</li>
 * <li><b>ruhige Kamera</b>: ein langsamer Schwenk um ±3° über 70 s ({@link #schwenk()}), nach jedem Schnitt in die
 * andere Richtung, damit stehende Einstellungen nicht wie Standbilder wirken;</li>
 * <li><b>Ton</b> folgt dem Schwarz ({@link #ton()}), <b>Filmkorn</b> und Randabdunklung als Stärke ({@link #film()}).</li>
 * </ul>
 * Alle Zeiten sind echte Sekunden; die App ruft {@link #schritt(double)} einmal je Bild (auch wenn die Szene steht).
 * Was gezeichnet wird, malt {@link KinoMaler} (Java2D) und legt {@code gl/KinoGl} (JOGL) über das Bild. Ein Faden ruft
 * {@code schritt} und fragt ab (der Bildlauf); die übrigen Aufrufe dürfen aus anderen Fäden kommen.
 */
public final class Kino {

    /** Wo der Film steht. */
    public enum Lage { AUS, VORSPANN, FILM, ABSPANN, AUSKLANG }

    /** Was als Text über dem Bild liegt. */
    public enum Art { TITEL, UNTERTITEL, ABSPANN }

    /**
     * Eine Texttafel: Art, Zeilen (beim Titel: Kopfzeile, Titel, Unterzeile; beim Abspann: Titel, dann die Zeilen),
     * Deckkraft 0..1 und eine Nummer, die sich nur ändert, wenn neu gemalt werden muss (nicht bei der Deckkraft).
     */
    public record Tafel(Art art, List<String> zeilen, double deckkraft, int nummer) { }

    /** Seitenverhältnis der Breitwand (Cinemascope). */
    public static final double BREITWAND = 2.39;

    // Zeiten (s), für die App einstellbar
    public volatile double balkenS = 1.6, titelS = 5.0, aufblendeS = 2.5, ueberblendeS = 1.6, abblendeS = 3.0, abspannS = 8.0, ausklangS = 1.6;
    /** Schwenk: Weite (Grad) und Dauer einer ganzen Schwingung (s). 0 = aus. */
    public volatile double schwenkGrad = 3.0, schwenkS = 70;
    /** Filmkorn und Randabdunklung, 0..1 (0 = aus). */
    public volatile double filmStaerke = 1.0;
    /** Ob Untertitel gezeigt werden. */
    public volatile boolean untertitelAn = true;

    private Lage lage = Lage.AUS;
    private double t;                  // Sekunden in der Lage
    private double balken;             // 0..1 (ohne Glättung)
    private double schwarz;            // 0..1
    private double seitSchnitt = Double.NaN;
    private double schwenkT;
    private int schwenkRichtung = 1;
    private boolean schnittWunsch;
    private List<String> titel = List.of(), abspann = List.of();
    private String utText;
    private double utT, utDauer;
    private Tafel tafel;
    private int nummer;
    private boolean endeGemeldet = true;
    private final List<String> warteUt = new ArrayList<>();

    // ------------------------------------------------------------------ Steuerung

    /**
     * Den Film beginnen: Balken fahren ein, Schwarz, Titeltafel (kopf über dem Titel, unterzeile darunter; jede darf
     * leer sein), dann blendet das Bild auf.
     */
    public synchronized void beginnen(String kopf, String titelText, String unterzeile) {
        lage = Lage.VORSPANN;
        t = 0;
        schwarz = 1;
        seitSchnitt = Double.NaN;
        schnittWunsch = false;
        schwenkT = 0;
        utText = null;
        warteUt.clear();
        titel = List.of(n(kopf), n(titelText), n(unterzeile));
        endeGemeldet = false;
    }

    /** Ein Untertitel (ersetzt den laufenden). Im Vorspann wartet er, bis das Bild aufblendet. */
    public synchronized void untertitel(String text) {
        if (text == null || text.isBlank() || lage == Lage.AUS) return;
        if (lage == Lage.VORSPANN && t < titelS) { warteUt.clear(); warteUt.add(text.strip()); return; }
        if (lage == Lage.ABSPANN || lage == Lage.AUSKLANG) return;
        zeigeUt(text.strip());
    }

    private void zeigeUt(String s) {
        utText = s;
        utT = 0;
        utDauer = Math.max(3.5, Math.min(8, 2.5 + 0.06 * s.length()));
    }

    /** Den Film regulär beenden: abblenden, Abspann (erste Zeile groß), dann Balken heraus. */
    public synchronized void beenden(List<String> zeilen) {
        if (lage == Lage.AUS || lage == Lage.ABSPANN || lage == Lage.AUSKLANG) return;
        abspann = zeilen == null ? List.of() : List.copyOf(zeilen);
        lage = Lage.ABSPANN;
        t = 0;
        utText = null;
    }

    /** Sofort heraus (Esc): kein Abspann, Balken und Schwarz gehen in kurzer Zeit weg. */
    public synchronized void abbrechen() {
        if (lage == Lage.AUS) return;
        lage = Lage.AUS;
        utText = null;
        warteUt.clear();
        seitSchnitt = Double.NaN;
        schnittWunsch = false;
    }

    /**
     * Ein Schnitt soll überblendet werden. true: die App merkt sich das jetzige Bild, bevor sie schneidet
     * ({@link #schnittFaellig()}, dann {@link #geschnitten()}); false: gleich schneiden (Kino aus oder Bild schwarz).
     */
    public synchronized boolean schnitt() {
        if (lage == Lage.AUS || schwarz > 0.9) { neuerSchwenk(); return false; }
        schnittWunsch = true;
        return true;
    }

    /** Ob die App jetzt das Bild merken und dann schneiden soll. */
    public synchronized boolean schnittFaellig() { return schnittWunsch; }

    /** Die App hat das Bild gemerkt und geschnitten: die Überblendung beginnt. */
    public synchronized void geschnitten() {
        schnittWunsch = false;
        seitSchnitt = 0;
        neuerSchwenk();
    }

    private void neuerSchwenk() {
        schwenkT = 0;
        schwenkRichtung = -schwenkRichtung;
    }

    /** Um dt echte Sekunden weiter. */
    public synchronized void schritt(double dt) {
        dt = Math.max(0, Math.min(0.25, dt));
        t += dt;
        schwenkT += dt;
        if (!Double.isNaN(seitSchnitt)) { seitSchnitt += dt; if (seitSchnitt > ueberblendeS) seitSchnitt = Double.NaN; }
        double soll;
        switch (lage) {
            case VORSPANN -> {
                soll = 1;
                schwarz = t < titelS ? 1 : 1 - glatt((t - titelS) / aufblendeS);
                if (t >= titelS && !warteUt.isEmpty()) { zeigeUt(warteUt.get(0)); warteUt.clear(); }
                if (t >= titelS + aufblendeS) { lage = Lage.FILM; t = 0; schwarz = 0; }
            }
            case FILM -> { soll = 1; schwarz = 0; }
            case ABSPANN -> {
                soll = 1;
                schwarz = glatt(t / abblendeS);
                if (t >= abblendeS + abspannS) { lage = Lage.AUSKLANG; t = 0; }
            }
            case AUSKLANG -> {
                soll = 0;
                schwarz = 1 - glatt(t / ausklangS);
                if (t >= ausklangS) { lage = Lage.AUS; schwarz = 0; }
            }
            default -> { soll = 0; schwarz = Math.max(0, schwarz - dt / 0.6); }
        }
        double v = dt / Math.max(0.05, lage == Lage.AUS ? 0.8 : balkenS);
        balken = soll > balken ? Math.min(soll, balken + v) : Math.max(soll, balken - v);
        if (utText != null) { utT += dt; if (utT > utDauer) utText = null; }
        tafel = neueTafel();
    }

    private Tafel neueTafel() {
        Art art = null;
        List<String> z = null;
        double a = 0;
        if (lage == Lage.VORSPANN && t < titelS) {
            art = Art.TITEL;
            z = titel;
            a = Math.min(glatt((t - 0.6) / 1.2), 1 - glatt((t - (titelS - 1.2)) / 1.0));
        } else if (lage == Lage.ABSPANN && t > abblendeS * 0.6) {
            art = Art.ABSPANN;
            z = abspann;
            double u = t - abblendeS * 0.6, len = abspannS + abblendeS * 0.4;
            a = Math.min(glatt(u / 1.4), 1 - glatt((u - (len - 1.4)) / 1.2));
        } else if (utText != null && untertitelAn && lage != Lage.AUS) {
            art = Art.UNTERTITEL;
            z = List.of(utText);
            a = Math.min(glatt(utT / 0.5), 1 - glatt((utT - (utDauer - 0.6)) / 0.6));
        }
        if (art == null || a <= 0.001) return null;
        Tafel alt = tafel;
        if (alt == null || alt.art() != art || !alt.zeilen().equals(z)) nummer++;
        return new Tafel(art, z, a, nummer);
    }

    // ------------------------------------------------------------------ Abfragen (Bildlauf)

    public synchronized Lage lage() { return lage; }

    /** Ob der Film läuft (Vorspann bis Ausklang); die App blendet so lange Bedienfeld und HUD aus. */
    public synchronized boolean laeuft() { return lage != Lage.AUS; }

    /** Ob noch etwas zu zeichnen ist (auch während die Balken nach Esc herausfahren). */
    public synchronized boolean sichtbar() { return lage != Lage.AUS || balken > 0 || schwarz > 0 || !Double.isNaN(seitSchnitt); }

    /** Balken 0..1, geglättet. */
    public synchronized double balken() { return glatt(balken); }

    /** Höhe eines Balkens in Bildpunkten für ein Bild von w × h (0, wenn das Bild schon breiter ist als 2,39 : 1). */
    public int balkenPx(int w, int h) { return balkenPx(w, h, balken()); }

    public static int balkenPx(int w, int h, double anteil) {
        double voll = (h - w / BREITWAND) / 2;
        return voll <= 0 ? 0 : (int) Math.round(voll * Math.max(0, Math.min(1, anteil)));
    }

    /** Schwarz über dem Bild, 0..1 (Vorspann, Abspann). */
    public synchronized double schwarz() { return schwarz; }

    /** Deckkraft des gemerkten Bilds vor dem letzten Schnitt, 1 → 0 (0: keine Überblendung). */
    public synchronized double ueberblende() { return Double.isNaN(seitSchnitt) ? 0 : 1 - glatt(seitSchnitt / ueberblendeS); }

    /** Die Texttafel jetzt oder null. */
    public synchronized Tafel tafel() { return tafel; }

    /** Versatz des Blicks in Grad (Schwenk), sanft aus der Ruhe nach jedem Schnitt. Die App wendet die Änderung an. */
    public synchronized double schwenk() {
        if (lage == Lage.AUS || schwenkGrad <= 0 || schwenkS <= 0) return 0;
        double anlauf = glatt(schwenkT / 4);
        return schwenkRichtung * schwenkGrad * Math.sin(2 * Math.PI * schwenkT / schwenkS) * anlauf;
    }

    /** Lautstärke als Anteil: folgt dem Schwarz (im Schwarz noch 15 %). */
    public synchronized double ton() { return 1 - 0.85 * schwarz; }

    /** Stärke von Filmkorn und Randabdunklung, 0..1 (mit den Balken). */
    public synchronized double film() { return filmStaerke * glatt(balken); }

    /**
     * Einmal true, wenn der Film ganz zu Ende ist (nach Abspann und Ausklang oder nach Esc, wenn die Balken weg sind);
     * die App stellt dann Bedienfeld und HUD wieder her.
     */
    public synchronized boolean endeMelden() {
        if (endeGemeldet || lage != Lage.AUS || balken > 0 || schwarz > 0) return false;
        endeGemeldet = true;
        return true;
    }

    /** „Kino: Vorspann 3,2 s“ für das HUD und die Statuszeile. */
    public synchronized String beschreibung() {
        return switch (lage) {
            case AUS -> "Kino aus";
            case VORSPANN -> String.format(java.util.Locale.GERMAN, "Kino: Vorspann %.1f s", t);
            case FILM -> "Kino: Film" + (utText != null ? " · „" + utText + "“" : "");
            case ABSPANN -> "Kino: Abspann";
            case AUSKLANG -> "Kino: Ausklang";
        };
    }

    static double glatt(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    private static String n(String s) { return s == null ? "" : s.strip(); }
}
