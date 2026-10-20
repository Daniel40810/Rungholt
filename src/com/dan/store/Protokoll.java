package com.dan.store;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;

/**
 * Das Protokoll einer App (aus Lighthouse, F43 dort): Fehler und Warnungen mit Stapel, dazu Bedienschritte. Jede
 * Zeile trägt Szenenzeit und Ort. Gesammelt wird im Speicher des Programms; {@link Dienst} schreibt alle fünf Sekunden
 * in einem Zug in die {@link Ablage}. Ereignisse einer Szene (Blitz, Möwe) gehören nicht hinein.
 */
public final class Protokoll {
    private Protokoll() { }

    public static final String FEHLER = "FEHLER", WARNUNG = "WARNUNG", INFO = "INFO";

    /** Eine Zeile: Zeit (ms seit 1970), Stufe, Quelle, Text, Szenenzeit als Text (oder null), Ort, Stapel. */
    public record Zeile(long zeitMs, String stufe, String quelle, String text, String szenenzeit, String ort, String detail) { }

    static final ConcurrentLinkedQueue<Zeile> WARTET = new ConcurrentLinkedQueue<>();
    static final List<Zeile> ZULETZT = new ArrayList<>();
    static volatile Supplier<String> szenenzeit = () -> null;
    static volatile Supplier<String> ort = () -> null;
    /** Vorsatz für Meldungen auf stderr, etwa „Haithabu“. */
    public static volatile String app = "App";

    /** Woher Szenenzeit und Ort kommen (die App setzt das, sobald die Szene steht). */
    public static void kontext(Supplier<String> zeit, Supplier<String> wo) {
        szenenzeit = zeit;
        ort = wo;
    }

    public static void info(String quelle, String text) { add(INFO, quelle, text, null); }
    public static void warnung(String quelle, String text) { add(WARNUNG, quelle, text, null); }
    public static void fehler(String quelle, String text, Throwable t) { add(FEHLER, quelle, text, t); }

    static void add(String stufe, String quelle, String text, Throwable t) {
        String sz = null, o = null;
        try { sz = szenenzeit.get(); o = ort.get(); } catch (RuntimeException e) { /* ohne Kontext */ }
        String det = null;
        if (t != null) {
            StringWriter w = new StringWriter();
            t.printStackTrace(new PrintWriter(w));
            det = w.toString();
        }
        Zeile z = new Zeile(System.currentTimeMillis(), stufe, quelle, text, sz, o, det);
        WARTET.add(z);
        synchronized (ZULETZT) {
            ZULETZT.add(z);
            while (ZULETZT.size() > 200) ZULETZT.remove(0);
        }
        if (!INFO.equals(stufe)) System.err.println(app + " · " + stufe + " · " + quelle + ": " + text);
    }

    /** Die letzten n Zeilen dieser Sitzung. */
    public static List<Zeile> zuletzt(int n) {
        synchronized (ZULETZT) {
            return new ArrayList<>(ZULETZT.subList(Math.max(0, ZULETZT.size() - n), ZULETZT.size()));
        }
    }

    /** Alles Wartende holen (für den schreibenden Faden). */
    public static List<Zeile> abholen() {
        List<Zeile> l = new ArrayList<>();
        Zeile z;
        while ((z = WARTET.poll()) != null) l.add(z);
        return l;
    }

    /** Eine Zeile zum Anzeigen: „20:15:03 · WARNUNG · Einrichter: …“. */
    public static String zeile(Zeile z) {
        java.time.LocalTime t = java.time.Instant.ofEpochMilli(z.zeitMs()).atZone(java.time.ZoneId.systemDefault()).toLocalTime();
        return String.format("%02d:%02d:%02d · %s · %s: %s", t.getHour(), t.getMinute(), t.getSecond(), z.stufe(), z.quelle(), z.text());
    }
}
