package com.dan.rungholt.app;

import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.scene.Mandraenke;
import com.dan.rungholt.scene.Regie;
import com.dan.rungholt.scene.SceneClock;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 17: der Dauerlauf (Abnahme „fertig“, nach Glacier). Die App läuft mit Fenster und Grafikkarte in Runden; jede
 * Runde hat drei Tiden und eine Mandränke:
 * <ol>
 * <li>„Eine Tide“ im Kino (Hochwasser bis Hochwasser am 21.7.2026),</li>
 * <li>zwei Tiden frei im Zeitraffer 360 (gut vier Minuten; Tide, Überflutung, Watt, Seehunde, Vögel, Chronik),</li>
 * <li>„Die Grote Mandränke“ im Kino (der Deich bricht im Faden der Physik), danach zurück nach heute.</li>
 * </ol>
 * Es laufen mindestens zwei Runden, sonst so viele, bis die Minuten um sind (Vorgabe 30; eine Runde dauert rund
 * 16 min). Alle 5 s misst er Bilder/s, Grafikkarte, Prozessor, Speicher, Fäden, Seehunde, Vögel, treibende Häuser,
 * Schritte der Physik, die nicht nachkamen, und Fehler. Am Ende schreibt er {@code docs/status/Dauerlauf_….png} (Kurven)
 * und {@code …txt} mit dem Urteil:
 * <ul>
 * <li>keine Fehler (Fäden, GL, Chronik);</li>
 * <li>jede Runde vollständig: drei Tiden und eine Mandränke bis zum Morgen danach;</li>
 * <li>im Mittel mindestens 30 Bilder/s;</li>
 * <li>kein Leck: der Speicher nach einer Bereinigung am Ende jeder Runde (immer an derselben Stelle der Szene) wächst von
 * der ersten bis zur letzten Runde um höchstens 10 % und 40 MB;</li>
 * <li>die Grenzen der Szene halten: höchstens 60 Seehunde und dreimal 680 Wattvögel;</li>
 * <li>die Physik rechnete mindestens 99 % der Szenenzeit.</li>
 * </ul>
 * Start: Block TECHNIK → „Dauerlauf 30 min“, oder {@code -Drungholt.dauerlauf=Minuten} beim Start. Esc beendet nur das
 * Kino, nicht den Dauerlauf; der endet von selbst oder mit „Abbrechen“.
 */
public final class Dauerlauf {
    /** Ein Messpunkt. */
    record Punkt(double minute, double fps, double gpu, double cpu, double heapMb, double lebendMb, int faeden, int seehunde, int voegel, int treiben,
                 long nichtNach, int fehler, int runde) { }

    private static volatile Dauerlauf laufend;
    /** Fehler aus Fäden (RungholtApp zählt sie). */
    public static final java.util.concurrent.atomic.AtomicInteger FEHLER = new java.util.concurrent.atomic.AtomicInteger();
    /** Zeitraffer der freien Tiden und ihre Zahl je Runde. */
    public static final double RAFFER = 360;
    public static final int FREIE_TIDEN = 2;
    public static final int MIN_RUNDEN = 2;

    private final Renderer r;
    private final double minuten;
    private final List<Punkt> punkte = new ArrayList<>();
    private final List<double[]> runden = new ArrayList<>();   // je Runde: Minute, Speicher nach Bereinigung (MB), Tiden, Mandränke (1/0)
    private final long beginn = System.nanoTime();
    private volatile boolean aus;
    private volatile String stand = "";
    private int runde;

    private Dauerlauf(Renderer r, double minuten) {
        this.r = r;
        this.minuten = minuten;
    }

    public static boolean laeuft() { return laufend != null; }

    /** „Dauerlauf Runde 1 · 12 von 30 min · Kino: Eine Tide“ oder leer. */
    public static String zeile() { Dauerlauf d = laufend; return d == null ? "" : d.stand; }

    /** Startet den Dauerlauf (einer zur Zeit); danach läuft {@code fertig} mit der Datei des Berichts (im Faden des Laufs). */
    public static synchronized boolean starten(Renderer r, double minuten, java.util.function.Consumer<File> fertig) {
        if (laufend != null) return false;
        Dauerlauf d = new Dauerlauf(r, Math.max(1, minuten));
        laufend = d;
        Thread t = new Thread(() -> {
            File f = null;
            try { f = d.lauf(); }
            catch (Exception e) { System.err.println("Dauerlauf: " + e); FEHLER.incrementAndGet(); }
            finally { laufend = null; }
            if (fertig != null) fertig.accept(f);
        }, "Rungholt-Dauerlauf");
        t.setDaemon(true);
        t.start();
        return true;
    }

    public static void anhalten() { Dauerlauf d = laufend; if (d != null) d.aus = true; }

    private double minute() { return (System.nanoTime() - beginn) / 6e10; }

    private File lauf() throws Exception {
        int fehler0 = FEHLER.get();
        long nichtNach0 = r.physik.dropped(), schritte0 = r.physik.steps();
        verloren0 = r.physik.lostSeconds();
        // warten, bis die Szene steht (Überflutung und Land von 1362 im Faden der Tide)
        for (int i = 0; i < 480 && (r.flood() == null || r.land1362 == null); i++) Thread.sleep(250);
        r.ohneBlatt = true;
        try {
            while (!aus && (runde < MIN_RUNDEN || minute() < minuten)) runde(fehler0);
        } finally {
            r.ohneBlatt = false;
            if (r.kino.laeuft()) r.kinoVerlassen();
            if (r.regie.running()) r.regieStop();
        }
        long nichtNach = r.physik.dropped() - nichtNach0, schritte = Math.max(1, r.physik.steps() - schritte0);
        return bericht(FEHLER.get() - fehler0, nichtNach, schritte);
    }

    private void runde(int fehler0) throws InterruptedException {
        runde++;
        long naechste = 0;
        int tiden = 0;
        boolean morgen = false;
        // 1 „Eine Tide“ im Kino
        if (kino(Regie.tide(), fehler0)) tiden++;
        // 2 zwei Tiden frei im Zeitraffer
        if (!aus) {
            if (r.regie.running()) r.regieStop();
            r.wiring.era.target = 0;
            r.clock.lapse = RAFFER;
            double h0 = r.clock.epochMillis() / 3_600_000.0, bis = h0 + FREIE_TIDEN * com.dan.rungholt.scene.Site.TIDE_HOURS;
            while (!aus && r.clock.epochMillis() / 3_600_000.0 < bis) {
                stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · frei im Zeitraffer %.0f (%s)", runde, minute(), minuten, RAFFER, r.clock.label());
                naechste = messen(naechste, fehler0);
                Thread.sleep(250);
            }
            r.clock.lapse = 1;
            if (!aus) tiden += FREIE_TIDEN;
        }
        // 3 „Die Grote Mandränke“ im Kino, dann zurück nach heute
        if (!aus) {
            final boolean[] m = {false};
            kino(Regie.mandraenke(), fehler0, () -> { Mandraenke md = r.mandraenke; if (md != null && md.stage == Mandraenke.Stage.MORGEN) m[0] = true; });
            morgen = m[0];
            r.stopMandraenke(false);
            r.wiring.era.target = 0;
            r.clock.set(SceneClock.START_DATE, SceneClock.START_HOUR);
        }
        Thread.sleep(2000);
        synchronized (runden) { runden.add(new double[]{minute(), heapNachGc(), tiden, morgen ? 1 : 0}); }
    }

    private boolean kino(Regie.Book b, int fehler0) throws InterruptedException { return kino(b, fehler0, () -> { }); }

    /** Ein Regiebuch im Kino bis zum Ende; true, wenn es durchlief (nicht abgebrochen). */
    private boolean kino(Regie.Book b, int fehler0, Runnable beobachten) throws InterruptedException {
        if (aus) return false;
        long naechste = 0;
        r.kinoStart(b);
        Thread.sleep(500);
        while (!aus && (r.kino.laeuft() || r.regie.running())) {
            stand = String.format(Locale.GERMAN, "Dauerlauf Runde %d · %.0f von %.0f min · Kino: %s", runde, minute(), minuten, b.name());
            beobachten.run();
            naechste = messen(naechste, fehler0);
            Thread.sleep(250);
        }
        beobachten.run();
        if (r.kino.laeuft()) r.kinoVerlassen();
        return !aus;
    }

    private double verloren0;
    private int letzteMinute = -1;

    private long messen(long naechste, int fehler0) {
        long jetzt = System.currentTimeMillis();
        if (jetzt < naechste) return naechste;
        Runtime rt = Runtime.getRuntime();
        double heap = (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
        double lebend = 0;
        for (java.lang.management.MemoryPoolMXBean mp : ManagementFactory.getMemoryPoolMXBeans()) {
            if (mp.getType() != java.lang.management.MemoryType.HEAP) continue;
            java.lang.management.MemoryUsage u = mp.getCollectionUsage();
            if (u != null) lebend += u.getUsed() / 1048576.0;
        }
        int seehunde = 0, voegel = 0;
        for (int c : r.colony.census()) seehunde += c;
        for (int c : r.waders.census()) voegel += c;
        Mandraenke m = r.mandraenke;
        if ((int) minute() != letzteMinute) { letzteMinute = (int) minute(); System.out.println(stand + String.format(Locale.GERMAN, " · %.0f Bilder/s · %s", r.fps(), r.clock.label())); }
        synchronized (punkte) {
            punkte.add(new Punkt(minute(), r.fps(), r.gpuMs(), r.cpuMs(), heap, lebend, ManagementFactory.getThreadMXBean().getThreadCount(), seehunde, voegel,
                    m == null ? 0 : m.afloat(), r.physik.dropped(), FEHLER.get() - fehler0, runde));
        }
        return jetzt + 5000;
    }

    /** Speicher nach einer Bereinigung (MB). */
    private static double heapNachGc() {
        System.gc();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1048576.0;
    }

    private File bericht(int fehler, long nichtNach, long schritte) throws Exception {
        List<Punkt> p;
        synchronized (punkte) { p = new ArrayList<>(punkte); }
        List<double[]> rs;
        synchronized (runden) { rs = new ArrayList<>(runden); }
        int n = p.size();
        double fps = 0, fpsMin = Double.MAX_VALUE, gpu = 0;
        int seehundMax = 0, vogelMax = 0, treibMax = 0, faedenMax = 0;
        for (Punkt q : p) {
            fps += q.fps(); gpu += q.gpu();
            if (q.minute() > 1) fpsMin = Math.min(fpsMin, q.fps());
            seehundMax = Math.max(seehundMax, q.seehunde());
            vogelMax = Math.max(vogelMax, q.voegel());
            treibMax = Math.max(treibMax, q.treiben());
            faedenMax = Math.max(faedenMax, q.faeden());
        }
        fps /= Math.max(1, n);
        gpu /= Math.max(1, n);
        boolean vollstaendig = rs.size() >= MIN_RUNDEN && rs.stream().allMatch(x -> x[2] >= 1 + FREIE_TIDEN && x[3] > 0);
        StringBuilder rz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) rz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f Tiden%s", rs.get(i)[2], rs.get(i)[3] > 0 ? " + Mandränke" : ""));
        double erst = rs.isEmpty() ? 0 : rs.get(0)[1], letzt = rs.isEmpty() ? 0 : rs.get(rs.size() - 1)[1];
        boolean dicht = rs.size() < 2 || letzt <= erst * 1.10 + 40;
        StringBuilder sz = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) sz.append(i == 0 ? "" : ", ").append(String.format(Locale.GERMAN, "%.0f", rs.get(i)[1]));
        double verloren = r.physik.lostSeconds() - verloren0, gerechnet = schritte * com.dan.rungholt.scene.Physik.DT;
        double nachAnteil = gerechnet / Math.max(1e-9, gerechnet + verloren);
        int haenger = 0;
        for (Punkt q : p) if (q.minute() > 1 && q.fps() < 20) haenger++;
        List<String[]> urteil = new ArrayList<>();
        urteil.add(new String[]{fehler == 0 ? "ok" : "FEHLER", fehler + " Fehler (Fäden, GL, Chronik)"});
        urteil.add(new String[]{vollstaendig ? "ok" : "FEHLER", String.format(Locale.GERMAN, "%d Runden: %s", rs.size(), rz.length() == 0 ? "keine" : rz)});
        urteil.add(new String[]{fps >= 30 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Ø %.1f Bilder/s (geringste %.0f), Grafikkarte Ø %.2f ms", fps, fpsMin == Double.MAX_VALUE ? 0 : fpsMin, gpu)});
        urteil.add(new String[]{dicht ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Speicher nach Bereinigung am Ende jeder Runde %s MB (höchstens +10 %% und 40 MB)", sz.length() == 0 ? "—" : sz)});
        urteil.add(new String[]{seehundMax <= 60 && vogelMax <= 3 * 680 ? "ok" : "FEHLER", String.format(Locale.GERMAN,
                "höchstens %d Seehunde (Grenze 60), %d Wattvögel (Grenze 2.040), %d Häuser treibend zugleich, %d Fäden", seehundMax, vogelMax, treibMax, faedenMax)});
        urteil.add(new String[]{nachAnteil >= 0.99 ? "ok" : "FEHLER", String.format(Locale.GERMAN, "Physik rechnete %.2f %% der Zeit (%.1f s verfallen, %d×); Bild unter 20 Bilder/s in %d von %d Messungen",
                nachAnteil * 100, verloren, nichtNach, haenger, p.size())});
        boolean gut = urteil.stream().allMatch(u -> u[0].equals("ok"));
        String stempel = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
        File dir = r.photoDir != null ? r.photoDir : new File("docs/status");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
        StringBuilder t = new StringBuilder();
        t.append("Rungholt · Dauerlauf ").append(stempel).append(" · ").append(Renderer.VERSION).append(" · ").append(String.format(Locale.GERMAN, "%.1f min", minute()))
                .append(" · ").append(r.quality().label).append(" · ").append(r.glInfo()).append('\n');
        for (String[] u : urteil) t.append(String.format("%-7s %s%n", u[0], u[1]));
        t.append(gut ? "ERGEBNIS: Dauerlauf bestanden\n" : "ERGEBNIS: Dauerlauf nicht bestanden\n");
        t.append("\nMinute;Runde;Bilder/s;Grafikkarte ms;Prozessor ms;Speicher MB;lebend MB;Fäden;Seehunde;Vögel;treibende Häuser;nicht nachgekommen;Fehler\n");
        for (Punkt q : p)
            t.append(String.format(Locale.GERMAN, "%.2f;%d;%.1f;%.2f;%.2f;%.0f;%.0f;%d;%d;%d;%d;%d;%d%n", q.minute(), q.runde(), q.fps(), q.gpu(), q.cpu(), q.heapMb(), q.lebendMb(), q.faeden(),
                    q.seehunde(), q.voegel(), q.treiben(), q.nichtNach(), q.fehler()));
        File txt = new File(dir, "Dauerlauf_" + stempel + ".txt");
        Files.writeString(txt.toPath(), t.toString(), StandardCharsets.UTF_8);
        File png = new File(dir, "Dauerlauf_" + stempel + ".png");
        ImageIO.write(bild(p, rs, urteil, gut), "png", png);
        System.out.print(t.substring(0, t.indexOf("\nMinute")));
        return png;
    }

    private BufferedImage bild(List<Punkt> p, List<double[]> rs, List<String[]> urteil, boolean gut) {
        int w = 1600, h = 900, l = 80, rt = 30, top = 90, bot = 250;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 18, 25));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(230, 236, 240));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
        g.drawString(String.format(Locale.GERMAN, "Rungholt · Dauerlauf %.0f min · %d Runden · %s · %s", minute(), rs.size(), r.quality().label, gut ? "bestanden" : "nicht bestanden"), l, 40);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.setColor(new Color(143, 162, 174));
        g.drawString("Bilder/s (blau, 0–90) · lebender Speicher (gelb, 0–Höchstwert) · Seehunde (weiß, 0–60) · treibende Häuser (rot, 0–100) · Runden (senkrecht)", l, 66);
        int pw = w - l - rt, ph = h - top - bot;
        g.setColor(new Color(40, 54, 66));
        for (int i = 0; i <= 6; i++) g.drawLine(l, top + ph * i / 6, l + pw, top + ph * i / 6);
        double tMax = p.isEmpty() ? 1 : Math.max(1, p.get(p.size() - 1).minute());
        g.setColor(new Color(70, 90, 100));
        for (double[] x : rs) { int xx = l + (int) (pw * x[0] / tMax); g.drawLine(xx, top, xx, top + ph); }
        double hMax = 1;
        for (Punkt q : p) hMax = Math.max(hMax, q.lebendMb());
        kurve(g, p, q -> q.fps() / 90, tMax, l, top, pw, ph, new Color(58, 143, 199));
        final double hm = hMax;
        kurve(g, p, q -> q.lebendMb() / hm, tMax, l, top, pw, ph, new Color(224, 190, 120));
        kurve(g, p, q -> q.seehunde() / 60.0, tMax, l, top, pw, ph, new Color(240, 246, 249));
        kurve(g, p, q -> q.treiben() / 100.0, tMax, l, top, pw, ph, new Color(210, 96, 47));
        g.setColor(new Color(143, 162, 174));
        for (int m = 0; m <= (int) tMax; m += Math.max(1, (int) tMax / 10)) {
            int x = l + (int) (pw * m / tMax);
            g.drawString(m + " min", x - 12, top + ph + 20);
        }
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));
        int y = h - bot + 60;
        for (String[] u : urteil) {
            g.setColor(u[0].equals("ok") ? new Color(61, 177, 165) : new Color(220, 90, 70));
            g.drawString(String.format("%-7s", u[0]), l, y);
            g.setColor(new Color(230, 236, 240));
            g.drawString(u[1], l + 90, y);
            y += 26;
        }
        g.dispose();
        return im;
    }

    private static void kurve(Graphics2D g, List<Punkt> p, java.util.function.ToDoubleFunction<Punkt> f, double tMax, int l, int top, int pw, int ph, Color c) {
        if (p.size() < 2) return;
        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < p.size(); i++) {
            double x = l + pw * p.get(i).minute() / tMax, y = top + ph * (1 - Math.max(0, Math.min(1, f.applyAsDouble(p.get(i)))));
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(2f));
        g.draw(path);
    }
}
