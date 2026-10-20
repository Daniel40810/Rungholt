package com.dan.store;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Hält die laufende {@link Ablage}, die Sitzung und die beiden Hintergrundarbeiten: das Protokoll alle fünf Sekunden
 * schreiben und den Zustand alle zwei Minuten sichern (aus Lighthouse, F39/F43 dort). Ein eigener Faden mit
 * niedriger Priorität, damit eine Szene nie auf die Datenbank wartet. Scheitert die Datenbank beim Protokoll, gehen
 * die Zeilen in die {@link #ersatz Ersatzablage}.
 */
public final class Dienst {
    private Dienst() { }

    /** Abstände in Sekunden. */
    public static int protokollS = 5, sichernS = 120;
    /** Name des Fadens, etwa „Haithabu-Daten“. */
    public static volatile String faden = "Daten";
    /** Wohin das Protokoll geht, wenn die Datenbank scheitert (Dateien). */
    public static volatile Supplier<Ablage> ersatz = () -> null;

    private static volatile Ablage ablage;
    private static volatile long sitzung = -1;
    private static volatile Supplier<Ablage.Sicherung> zustand;
    private static volatile long gesichert;
    private static volatile String letzterFehler = "";
    private static ScheduledExecutorService exec;
    private static String version = "", gpu = "", qualitaet = "";

    public static Ablage ablage() { return ablage; }
    public static long sitzung() { return sitzung; }
    /** Wann zuletzt gesichert wurde (ms seit 1970, 0 = noch nie). */
    public static long gesichert() { return gesichert; }
    public static String letzterFehler() { return letzterFehler; }

    /** Ablage setzen (beim Start oder nach dem Einrichten); die alte wird geschlossen, eine neue Sitzung beginnt dort. */
    public static synchronized void setzen(Ablage a) {
        Ablage alt = ablage;
        ablage = a;
        if (alt != a && alt != null) {
            sitzung = -1;
            alt.schliessen();
            if (zustand != null) beginnen(version, gpu, qualitaet);
        }
    }

    /** Sitzung beginnen und die Hintergrundarbeiten starten; erfassen läuft im Faden des Dienstes. */
    public static synchronized void starten(String v, String g, String q, Supplier<Ablage.Sicherung> erfassen) {
        zustand = erfassen;
        beginnen(v, g, q);
        if (exec != null) return;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, faden);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        });
        exec.scheduleWithFixedDelay(Dienst::protokollSchreiben, protokollS, protokollS, TimeUnit.SECONDS);
        exec.scheduleWithFixedDelay(() -> sichern(false), sichernS, sichernS, TimeUnit.SECONDS);
    }

    /**
     * Eine Sitzung beginnen, falls keine läuft: etwa wenn die Ablage beim Setzen noch nicht eingerichtet war
     * (Haithabu, Befund Phase 16: nach „Verbinden“ und „Einrichten“ blieb die Sitzung -1).
     */
    public static synchronized void neuBeginnen() {
        if (zustand != null && sitzung < 0) beginnen(version, gpu, qualitaet);
    }

    static synchronized void beginnen(String v, String g, String q) {
        version = v; gpu = g; qualitaet = q;
        Ablage a = ablage;
        if (a == null) return;
        try {
            sitzung = a.sitzungBeginnen(v, g, q);
            Protokoll.info("Sitzung", "Sitzung " + sitzung + " begonnen · " + a.wo());
        } catch (Ablage.Fehler e) {
            sitzung = -1;
            Protokoll.fehler("Sitzung", "Sitzung nicht begonnen: " + e.getMessage(), null);
        }
    }

    /** Protokoll schreiben; scheitert die Datenbank, gehen die Zeilen in die Ersatzablage. */
    public static void protokollSchreiben() {
        List<Protokoll.Zeile> l = Protokoll.abholen();
        Ablage a = ablage;
        if (l.isEmpty() || a == null) return;
        try {
            a.protokoll(l, sitzung >= 0 ? sitzung : null);
        } catch (Ablage.Fehler e) {
            letzterFehler = e.getMessage();
            System.err.println(Protokoll.app + ": Protokoll nicht geschrieben: " + e.getMessage());
            Ablage x = ersatz.get();
            if (a.datenbank() && x != null) {
                try { x.protokoll(l, null); } catch (Ablage.Fehler e2) { /* dann eben nicht */ }
            }
        }
    }

    /** Zustand sichern (alle zwei Minuten und beim Ende); mit ende wird die Sitzung abgeschlossen. */
    public static void sichern(boolean ende) {
        Supplier<Ablage.Sicherung> z = zustand;
        Ablage a = ablage;
        if (z == null || sitzung < 0 || a == null) return;
        try {
            a.sitzungSichern(sitzung, z.get());
            gesichert = System.currentTimeMillis();
            if (ende) a.sitzungBeenden(sitzung);
        } catch (Ablage.Fehler | RuntimeException e) {
            letzterFehler = e.getMessage();
            Protokoll.fehler("Sitzung", "Zustand nicht gesichert: " + e.getMessage(), e instanceof RuntimeException ? e : null);
        }
    }

    /** Beim Beenden: letzter Stand, Protokoll, Verbindung zu. */
    public static synchronized void beenden() {
        Protokoll.info("Sitzung", "Sitzung " + sitzung + " geordnet beendet");
        sichern(true);
        protokollSchreiben();
        if (exec != null) exec.shutdownNow();
        exec = null;
        Ablage a = ablage;
        if (a != null) a.schliessen();
    }

    /** Eine Arbeit im Faden des Dienstes (Laden, Speichern aus der Oberfläche). */
    public static void spaeter(Runnable r) {
        ScheduledExecutorService e = exec;
        if (e != null) e.execute(r);
        else {
            Thread t = new Thread(r, faden + "-einmal");
            t.setDaemon(true);
            t.start();
        }
    }
}
