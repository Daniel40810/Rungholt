package com.dan.rungholt.data;

import com.dan.flock.Waders;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.scene.Glocken;
import com.dan.rungholt.scene.Mandraenke;
import com.dan.rungholt.scene.Regie;
import com.dan.rungholt.scene.Seehundbank;
import com.dan.rungholt.scene.Stadt1362;
import com.dan.rungholt.wiring.Wiring;
import com.dan.seal.Colony;
import com.dan.store.Ablage;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;
import com.dan.tide.Flood;
import com.dan.tide.Tide;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Die Chronik der Szene (Phase 16, F28, F29): beobachtet die laufende Szene alle 0,5 s und schreibt über den gültigen
 * {@link Speicher} in die Datenbank oder in die Dateien. Das Bild wartet nie: beobachtet wird in einem eigenen Faden,
 * geschrieben in einem zweiten (der Reihe nach, damit eine Tide ihre Nummer hat, bevor ihre Zählung sie braucht).
 * <ul>
 * <li><b>Tiden</b>: jedes Hoch- und Niedrigwasser am Rand (die Wenden der Tide ohne Windstau, auch im Zeitraffer, bis zu
 * zwei Tage zurück): Pegel mit Windstau, trockener Anteil, Pfützen, größte Strömung; bei Niedrigwasser die nasse Fläche
 * als Mehrfachfläche ({@link Wasserlinie}, 64 m), höchstens alle {@link #LINIE_ALLE_S} s echter Zeit.</li>
 * <li><b>Zählungen</b> bei Niedrigwasser: die Seehunde der Bank (alle, liegend, Heuler, auf der Flucht) und die
 * Wattvögel je Art mit der Mitte ihrer Gruppen.</li>
 * <li><b>Mandränke</b>: ein Lauf je Nacht mit den Zeiten der Stufen, höchstem Pegel vor dem Deich und in der Marsch,
 * Häusern, Bruchstelle und Bresche (die Breite des Bruchs entlang des Deichs).</li>
 * <li><b>Glocken</b>: jedes Läuten mit Dauer, Wind und Wasser über der Kirchwarft.</li>
 * <li><b>Regiebücher</b>: jeder Lauf mit Blatt (oder abgebrochen), ob im Kino und wie lange gewartet wurde.</li>
 * </ul>
 */
public final class Chronik {
    /** Echte Sekunden zwischen zwei Wasserlinien (im Zeitraffer fällt alle 12 s ein Niedrigwasser). */
    public static final double LINIE_ALLE_S = 30;
    /** Größte Lücke der Uhr, die noch nachgetragen wird (ms, zwei Tage); weiter ist ein Sprung. */
    public static final double LUECKE_MS = 2 * 86_400_000.0;

    private final Renderer r;
    private final ScheduledExecutorService schauen;
    private final ExecutorService schreiben;
    private volatile boolean an = true;
    /** Zähler für Block CHRONIK und Prüfung. */
    public volatile int tiden, linien, zaehlungen, laeufe, glocken, regielaeufe, fehler;
    public volatile String letzte = "";

    private double zuletztMs = Double.NaN;
    private long linieMs;
    // Mandränke
    private Mandraenke mand;
    private Daten.MandraenkeLauf lauf;
    private final AtomicLong laufId = new AtomicLong(-1);
    private Mandraenke.Stage stufe;
    private double pegelMax = Double.NEGATIVE_INFINITY, marschMax = Double.NEGATIVE_INFINITY;
    private long laufGeschriebenMs;
    // Glocken
    private boolean laeutet;
    private double laeutetSeitMs;
    private boolean vonHand;
    // Regie
    private Regie.Book buch;
    private long buchBeginn;
    private boolean buchKino;
    private File blattVorher;

    public Chronik(Renderer r) {
        this.r = r;
        schauen = Executors.newSingleThreadScheduledExecutor(x -> faden(x, "Rungholt-Chronik"));
        schreiben = Executors.newSingleThreadExecutor(x -> faden(x, "Rungholt-Chronik-Schreiben"));
        schauen.scheduleWithFixedDelay(this::schauen, 500, 500, TimeUnit.MILLISECONDS);
    }

    private static Thread faden(Runnable x, String n) {
        Thread t = new Thread(x, n);
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    }

    /** Die Chronik schreibt (true) oder nicht; die Beobachtung läuft weiter. */
    public void schreiben(boolean ja) { an = ja; }
    public boolean schreibt() { return an; }

    /** Für Prüfungen: einmal beobachten (im Faden des Aufrufers) und warten, bis alles geschrieben ist. */
    public void jetzt() {
        schauen();
        warten();
    }

    /** Warten, bis die Schreibschlange leer ist (höchstens 30 s). */
    public void warten() {
        try { schreiben.submit(() -> { }).get(30, TimeUnit.SECONDS); } catch (Exception e) { /* weiter */ }
    }

    public void beenden() {
        try { synchronized (this) { if (mand != null) mandraenkeEnde(); } } catch (RuntimeException e) { /* weiter */ }
        schauen.shutdownNow();
        schreiben.shutdown();
        try { schreiben.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Long sitzung() { long s = Dienst.sitzung(); return s > 0 ? s : null; }

    private interface Schreib { void tun(Speicher s) throws Ablage.Fehler; }

    private void spaeter(String was, Schreib w) {
        if (!an) return;
        schreiben.submit(() -> {
            try {
                w.tun(Datenseite.speicher());
                letzte = was;
            } catch (Ablage.Fehler | RuntimeException e) {
                fehler++;
                Protokoll.warnung("Chronik", was + ": " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------ beobachten

    synchronized void schauen() {
        try {
            Wiring w = r.wiring;
            if (w == null) return;
            double jetzt = r.clock.epochMillis();
            if (Double.isNaN(zuletztMs) || jetzt < zuletztMs || jetzt - zuletztMs > LUECKE_MS) zuletztMs = jetzt;
            else if (jetzt > zuletztMs) {
                if (Double.isNaN(w.level.hand))
                    for (Tide.Turn t : w.level.tide.extremes(zuletztMs / 3_600_000.0, jetzt / 3_600_000.0)) wende(w, t);
                zuletztMs = jetzt;
            }
            mandraenke(w, jetzt);
            glocken(w, jetzt);
            regie();
        } catch (RuntimeException e) {
            fehler++;
            Protokoll.fehler("Chronik", "Beobachten: " + e, e);
        }
    }

    /** Ein Hoch- oder Niedrigwasser am Rand. */
    private void wende(Wiring w, Tide.Turn t) {
        Flood f = w.level.flood;
        boolean nw = !t.high();
        long ms = Math.round(t.hours() * 3_600_000.0);
        String aera = w.era.is1362() ? Daten.A1362 : Daten.HEUTE;
        double surge = w.level.tide.surge;
        Daten.Geom linie = null;
        if (nw && f != null && System.currentTimeMillis() - linieMs >= LINIE_ALLE_S * 1000) {
            linie = Wasserlinie.flaeche(Wasserlinie.gitter(f, Wasserlinie.RASTER));
            linieMs = System.currentTimeMillis();
            if (linie != null) linien++;
        }
        Daten.Tide td = new Daten.Tide(0, ms, aera, nw ? "NW" : "HW", t.level() + surge, t.level(), surge, f == null ? Double.NaN : 1 - f.wetShare,
                f == null ? 0 : f.puddles, f == null ? Double.NaN : f.maxCurrent, linie);
        List<Daten.Zaehlung> zs = nw && f != null ? zaehlen(ms) : List.of();
        tiden++;
        zaehlungen += zs.size();
        spaeter((nw ? "Niedrigwasser " : "Hochwasser ") + Daten.text(ms), s -> {
            long id = s.tide(td, sitzung());
            if (!zs.isEmpty()) s.zaehlungen(id, zs, sitzung());
        });
    }

    /** Die Zählung bei Niedrigwasser: Seehunde der Bank, Wattvögel je Art. */
    List<Daten.Zaehlung> zaehlen(long ms) {
        List<Daten.Zaehlung> l = new ArrayList<>();
        if (r.showSeals) {
            int alle = 0, liegend = 0, heuler = 0, flucht = 0;
            double cx = 0, cz = 0;
            for (Colony.Animal a : r.colony.animals()) {
                alle++;
                if (a.act == Colony.Activity.LIEGEN) { liegend++; cx += a.x; cz += a.z; }
                if (a.pup) heuler++;
                if (a.act == Colony.Activity.FLUCHT) flucht++;
            }
            if (alle > 0)
                l.add(new Daten.Zaehlung(ms, "SEEHUND", "Seehundbank [G]", alle, liegend, heuler, flucht, liegend > 0 ? cx / liegend : Seehundbank.X,
                        liegend > 0 ? cz / liegend : Seehundbank.Z));
        }
        if (r.showBirds) {
            Map<String, double[]> je = new LinkedHashMap<>();
            for (Waders.Group g : r.waders.groups()) {
                if (g.size() == 0) continue;
                double[] s = je.computeIfAbsent(g.species.label(), k -> new double[3]);
                s[0] += g.size();
                s[1] += g.cx * g.size();
                s[2] += g.cz * g.size();
            }
            for (Map.Entry<String, double[]> e : je.entrySet()) {
                double[] s = e.getValue();
                l.add(new Daten.Zaehlung(ms, "VOGEL", e.getKey(), (int) s[0], null, null, null, s[1] / s[0], s[2] / s[0]));
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ Mandränke

    private void mandraenke(Wiring w, double jetzt) {
        Mandraenke m = r.mandraenke;
        if (m != mand) {
            if (mand != null) mandraenkeEnde();
            mand = m;
            stufe = null;
            laufId.set(-1);
            pegelMax = marschMax = Double.NEGATIVE_INFINITY;
            if (m == null) return;
            lauf = new Daten.MandraenkeLauf(0, m.stelle.name(), (long) jetzt, null, null, null, null, null, m.stage.name(), Double.NaN, Double.NaN, 0, 0, 0, null, null,
                    buch == null ? null : buch.name());
            laufs++;
        }
        if (m == null) return;
        pegelMax = Math.max(pegelMax, w.level.level());
        if (!Double.isNaN(m.polder)) marschMax = Math.max(marschMax, m.polder);
        long ms = (long) jetzt;
        Daten.MandraenkeLauf l = lauf;
        Long sturm = l.sturmMs(), ueber = l.ueberlaufMs(), bruch = l.bruchMs(), flut = l.flutMs(), morgen = l.morgenMs();
        switch (m.stage) {
            case STURM -> { if (sturm == null) sturm = ms; }
            case UEBERLAUF -> { if (ueber == null) ueber = ms; }
            case BRUCH -> { if (bruch == null) bruch = Double.isNaN(m.breachH) ? ms : Math.round(m.breachH * 3_600_000.0); }
            case FLUT -> { if (flut == null) flut = Double.isNaN(m.openH) ? ms : Math.round(m.openH * 3_600_000.0); }
            case MORGEN -> { if (morgen == null) morgen = ms; }
            default -> { }
        }
        if (bruch == null && !Double.isNaN(m.breachH)) bruch = Math.round(m.breachH * 3_600_000.0);
        if (flut == null && !Double.isNaN(m.openH)) flut = Math.round(m.openH * 3_600_000.0);
        double bx = m.stelle.x, bz = Stadt1362.dikeZ(bx);
        Daten.Geom bresche = bruch == null ? null
                : Daten.Geom.linie(new double[][]{{bx - com.dan.rungholt.scene.Deichbruch.WIDTH / 2, Stadt1362.dikeZ(bx - com.dan.rungholt.scene.Deichbruch.WIDTH / 2)},
                {bx + com.dan.rungholt.scene.Deichbruch.WIDTH / 2, Stadt1362.dikeZ(bx + com.dan.rungholt.scene.Deichbruch.WIDTH / 2)}});
        int haeuser = m.houses().size(), fort = m.gone(), treiben = m.afloat();
        lauf = new Daten.MandraenkeLauf(l.id(), m.stelle.name(), l.beginnMs(), sturm, ueber, bruch, flut, morgen, m.stage.name(), pegelMax,
                marschMax == Double.NEGATIVE_INFINITY ? Double.NaN : marschMax, haeuser, fort, treiben, new double[]{bx, bz}, bresche, l.regiebuch());
        boolean neu = stufe != m.stage;
        if (neu || System.currentTimeMillis() - laufGeschriebenMs > 60_000) {
            stufe = m.stage;
            laufGeschriebenMs = System.currentTimeMillis();
            schreibeLauf(neu ? "Mandränke: " + m.stage.label : "Mandränke fortgeschrieben");
        }
    }

    private int laufs;

    private void schreibeLauf(String was) {
        Daten.MandraenkeLauf l = lauf;
        if (l == null) return;
        spaeter(was, s -> {
            long id = laufId.get();
            Daten.MandraenkeLauf mit = id > 0 ? withId(l, id) : l;
            long neu = s.mandraenkeLauf(mit, sitzung());
            laufId.set(neu);
        });
    }

    private static Daten.MandraenkeLauf withId(Daten.MandraenkeLauf l, long id) {
        return new Daten.MandraenkeLauf(id, l.stelle(), l.beginnMs(), l.sturmMs(), l.ueberlaufMs(), l.bruchMs(), l.flutMs(), l.morgenMs(), l.stufe(), l.pegelMaxM(),
                l.marschMaxM(), l.haeuser(), l.haeuserZerfallen(), l.stuecke(), l.bruchstelle(), l.bresche(), l.regiebuch());
    }

    private void mandraenkeEnde() {
        if (lauf != null) {
            laeufe++;
            schreibeLauf("Mandränke beendet: " + lauf.stufe());
        }
        lauf = null;
    }

    // ------------------------------------------------------------------ Glocken

    private void glocken(Wiring w, double jetzt) {
        Glocken g = r.glocken;
        boolean l = g.ringing;
        if (l && !laeutet) { laeutetSeitMs = jetzt; vonHand = g.forced; }
        if (!l && laeutet) {
            double dauer = Math.max(0, (jetzt - laeutetSeitMs) / 1000);
            Daten.Glocken gl = new Daten.Glocken((long) laeutetSeitMs, dauer, w.wind.speed(), w.wind.fromDeg(), w.level.depthAt(0, 0), vonHand);
            glocken++;
            spaeter("Glocken " + Daten.text(laeutetSeitMs), s -> s.glocken(gl, sitzung()));
        }
        laeutet = l;
    }

    // ------------------------------------------------------------------ Regiebücher

    private void regie() {
        Regie.Book b = r.regie.running() ? r.regie.book() : null;
        if (b != buch) {
            if (buch != null) regieEnde(buch);
            buch = b;
            if (b != null) { buchBeginn = System.currentTimeMillis(); buchKino = r.kino.laeuft(); blattVorher = r.lastSheet; }
        } else if (b != null && r.kino.laeuft()) buchKino = true;
    }

    private void regieEnde(Regie.Book b) {
        File blatt = r.lastSheet;
        boolean neu = blatt != null && blatt != blattVorher && blatt.isFile();
        byte[] png = null;
        if (neu) try { png = java.nio.file.Files.readAllBytes(blatt.toPath()); } catch (Exception e) { /* ohne Blatt */ }
        double gewartet = r.regie.waitedTotal();
        Daten.Regielauf l = new Daten.Regielauf(b.name(), buchBeginn, System.currentTimeMillis(), neu, buchKino, r.fps(), r.gpuMs(), gewartet,
                b.fotos(), r.quality().name(), png, neu ? blatt.getPath() : null);
        regielaeufe++;
        spaeter("Regiebuch „" + b.name() + "“" + (neu ? " mit Blatt" : " abgebrochen"), s -> s.regielauf(l, sitzung()));
    }

    /** „Chronik: 14 Tiden (5 Wasserlinien), 38 Zählungen, 1 Mandränke, 2 Glocken, 1 Regiebuch · zuletzt …“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Chronik%s: %d Tiden (%d Wasserlinien), %d Zählungen, %d Mandränke, %d× Glocken, %d Regiebücher%s%s",
                an ? "" : " (angehalten)", tiden, linien, zaehlungen, laufs, glocken, regielaeufe, letzte.isEmpty() ? "" : " · zuletzt " + letzte,
                fehler > 0 ? " · " + fehler + " Fehler (Protokoll)" : "");
    }
}
