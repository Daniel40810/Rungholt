package com.dan.wave;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Eine Welle, die läuft (Werkbuch Glacier, Abschnitt 4; Löser seit Phase 8): Flachwassergleichungen auf einem
 * Höhenfeld mit zwei Gittern. Das grobe Gitter (Vorgabe 8 m, 1 024², also gut 8 km) trägt die Welle über den See, das
 * feine (2 m, 512²) liegt darin, wo es auf das Ufer ankommt; das feine bekommt am Rand den Durchfluss des groben, das
 * grobe in der Fläche des feinen dessen Stand zurück.
 * <ul>
 * <li>Quellen: verdrängtes Volumen eines Einschlags ({@link #addVolume}), auch als Abfluss (negativ) oder Zufluss
 * (Dammbruch, Ruptura);</li>
 * <li>Auflaufen am Ufer und Trockenfallen ohne Sonderfälle (die Tiefe an der Kante zählt ab dem höheren Grund);
 * Reflexion an Wänden (Land, das höher ist als das Wasser, eine Gletscherfront); Reibung am Grund nach Manning;
 * Dämpfung in einem Saum am äußeren Rand;</li>
 * <li>Zeitschritt fest nach CFL, das feine Gitter in Unterschritten; gerechnet wird nur, wo etwas los ist (ein
 * Fenster, das mit der Welle wächst) – ein ruhiger See kostet nichts;</li>
 * <li>Abfragen {@link #heightAt}, {@link #velocityAt}, {@link #runupAt} ohne Grafikkarte, sicher aus jedem Faden;
 * Pegel ({@link #gauge}) zeichnen den Verlauf an einem Ort auf; {@code gl/} lädt die Höhe als Textur.</li>
 * </ul>
 * Reines Java; kennt keine App und kein anderes Package (Paketvertrag Punkt 1).
 */
public final class Wave {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Stand des Packages: 1 = Hülle; 8 = Löser. */
    public static final int STAGE = 8;

    /** Eine Welle läuft am Ufer auf: Ort (m), Auflaufhöhe über dem Ruhespiegel (m), Laufzeit (s). */
    @FunctionalInterface public interface Runup { void runup(double x, double z, double heightM, double t); }

    /** Ruhetiefe am Ort (m): positiv unter dem Wasser, negativ an Land (so hoch über dem Ruhespiegel); NaN: Wand. */
    @FunctionalInterface public interface Bed { double depth(double x, double z); }

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    public double gravity = 9.81;
    /** Feines Gitter: Zellgröße (m) und Zellen je Kante; grobes Gitter ebenso (0: keins) (Werkbuch, Abschnitt 10). */
    public double fineCell = 2, coarseCell = 8;
    public int fineN = 512, coarseN = 1024;
    /** Feines Gitter in z, wenn es nicht quadratisch sein soll (0: wie {@link #fineN}; nur ohne grobes Gitter). */
    public int fineNz = 0;
    /** Reibung am Grund (Manning n), Dämpfung im Randsaum (je s) und seine Breite (Zellen), Tempo-Grenze (m/s). */
    public double manning = 0.025, edgeDamping = 0.5, maxSpeed = 25;
    public int spongeCells = 24;
    public Runup onRunup;

    private Grid fine, coarse;
    private double fineCx, fineCz, coarseCx, coarseCz;
    private double dtc, dtf;
    private int subs;
    private double accum, time;
    private long steps, cellsDone;
    private final ConcurrentLinkedQueue<double[]> sources = new ConcurrentLinkedQueue<>();
    private final List<Gauge> gauges = new ArrayList<>();
    private final Map<Long, Double> runupSeen = new HashMap<>();
    private double sampleClock, shrinkClock;
    private float[] bndNewX, bndNewZ;
    private double busyNs, busyWindow, busyMs;
    private double maxEta;
    private volatile Snapshot snap;

    // ------------------------------------------------------------------ Presets
    /** Ein See vor einer Gletscherfront (Glacier): zwei Gitter wie oben. */
    public static Wave lake() { return new Wave(); }

    /** Ein kleines Becken für die Demo und die Prüfung: nur das feine Gitter, 256² zu 1 m, geschlossene Wände. */
    public static Wave basin() {
        Wave w = new Wave();
        w.fineCell = 1;
        w.fineN = 256;
        w.coarseN = 0;
        w.spongeCells = 0;
        return w;
    }

    /** Tempo einer langen Welle bei der Tiefe h (m): √(g·h). */
    public double celerity(double depth) { return Math.sqrt(gravity * Math.max(0, depth)); }

    /** Größter stabiler Schritt (s) auf Zellen der Größe dx bei der größten Tiefe h (CFL 0,5). */
    public double cflStep(double dx, double maxDepth) { return 0.5 * dx / Math.max(1e-9, celerity(maxDepth)); }

    // ------------------------------------------------------------------ Aufbau

    /**
     * Lage der Gitter (Mitten, m). Das feine wird auf die Zellen des groben gerastert, damit seine Kanten auf Kanten des
     * groben liegen. Danach {@link #build}.
     */
    public Wave place(double fineX, double fineZ, double coarseX, double coarseZ) {
        fineCx = fineX; fineCz = fineZ; coarseCx = coarseX; coarseCz = coarseZ;
        return this;
    }

    /**
     * Gitter anlegen und den Grund abtasten (dauert bei 1 024² gut eine Sekunde: in einem eigenen Faden aufrufen). Der
     * See steht danach still.
     */
    public synchronized Wave build(Bed bed) {
        coarse = null;
        if (coarseN > 0) {
            double half = coarseN * coarseCell / 2;
            coarse = new Grid(coarseN, coarseN, coarseCell, Math.floor((coarseCx - half) / coarseCell) * coarseCell,
                    Math.floor((coarseCz - half) / coarseCell) * coarseCell);
            coarse.sponge = spongeCells;
            coarse.bathymetry(bed);
        }
        double half = fineN * fineCell / 2, fx = fineCx - half, fz = fineCz - half;
        if (coarse != null) {
            fx = coarse.x0 + Math.round((fx - coarse.x0) / coarseCell) * coarseCell;
            fz = coarse.z0 + Math.round((fz - coarse.z0) / coarseCell) * coarseCell;
        }
        int fnz = coarse == null && fineNz > 0 ? fineNz : fineN;
        if (coarse == null && fineNz > 0) fz = fineCz - fnz * fineCell / 2;
        fine = new Grid(fineN, fnz, fineCell, fx, fz);
        fine.sponge = coarse == null ? spongeCells : 0;
        fine.bathymetry(bed);
        dtf = fine.cfl(gravity);
        if (coarse != null) restrictBed();
        if (coarse != null) {
            dtc = coarse.cfl(gravity);
            subs = (int) Math.ceil(dtc / dtf - 1e-9);
            dtf = dtc / subs;
            bndNewX = new float[2 * fineN];
            bndNewZ = new float[2 * fineN];
        } else {
            dtc = dtf;
            subs = 1;
        }
        accum = 0;
        time = 0;
        runupSeen.clear();
        publish(true);
        return this;
    }

    public boolean built() { return fine != null; }

    /**
     * Unter dem feinen Gitter bekommt das grobe den gemittelten Grund des feinen (sonst gibt die Rückgabe des
     * Wasserstands dort Unsinn: eine grobe Zelle, deren Mitte auf Land liegt, unter feinen Zellen voller Wasser).
     */
    private void restrictBed() {
        int r = (int) Math.round(coarse.dx / fine.dx);
        int ci0 = (int) Math.round((fine.x0 - coarse.x0) / coarse.dx), cj0 = (int) Math.round((fine.z0 - coarse.z0) / coarse.dx);
        int n = fine.nx / r;
        for (int b = 0; b < n; b++)
            for (int a = 0; a < n; a++) {
                int ci = ci0 + a, cj = cj0 + b;
                if (ci < 0 || cj < 0 || ci >= coarse.nx || cj >= coarse.nz) continue;
                double sum = 0;
                int walls = 0, k = 0;
                for (int y = 0; y < r; y++)
                    for (int x = 0; x < r; x++) {
                        float h = fine.h[(b * r + y) * fine.nx + a * r + x];
                        if (h <= -999) { walls++; continue; }
                        sum += h;
                        k++;
                    }
                int c = cj * coarse.nx + ci;
                float h = walls * 2 > r * r || k == 0 ? -1000f : (float) (sum / k);
                coarse.h[c] = h;
                coarse.eta[c] = h < 0 ? -h : 0f;
            }
    }

    /** Anfangszustand (für Prüfungen und Vorführungen): Wasserstand und Durchfluss je Ort im feinen Gitter. */
    public interface Init { double eta(double x, double z); default double qx(double x, double z) { return 0; } }

    /** Setzt den Anfangszustand im feinen Gitter (Wasserstand nur, wo Wasser ist; Durchfluss in x auf den Kanten). */
    public synchronized void initial(Init in) {
        Grid g = fine;
        for (int j = 0; j < g.nz; j++)
            for (int i = 0; i < g.nx; i++) {
                int c = j * g.nx + i;
                if (g.h[c] <= 0) continue;
                g.eta[c] = (float) in.eta(g.x0 + (i + 0.5) * g.dx, g.z0 + (j + 0.5) * g.dx);
            }
        for (int j = 0; j < g.nz; j++)
            for (int i = 1; i < g.nx; i++) {
                int a = j * g.nx + i - 1, b = a + 1;
                if (g.h[a] <= 0 || g.h[b] <= 0) continue;
                g.qx[j * (g.nx + 1) + i] = (float) in.qx(g.x0 + i * g.dx, g.z0 + (j + 0.5) * g.dx);
            }
        g.touch(g.nx / 2, g.nz / 2, Math.max(g.nx, g.nz));
        publish(true);
    }

    // ------------------------------------------------------------------ Quellen

    // ------------------------------------------------------------------ seit Rungholt Phase 12: gehaltener Wasserstand

    /**
     * Ein Wasserstand, der im feinen Gitter festgehalten wird (über dem Ruhespiegel), etwa das offene Meer vor einem
     * Deichbruch, das nicht leerläuft: {@code held} sagt einmal je Zelle, ob sie dazugehört, {@code eta} gibt den Stand
     * zur Zeit t. Nach jedem Schritt des feinen Gitters gesetzt (nie unter den Grund).
     */
    public interface Hold { boolean held(double x, double z); double eta(double t); }
    private Hold hold;
    private int[] heldCells;

    /** Gehaltenen Wasserstand setzen (null: keiner). */
    public synchronized void hold(Hold h) { hold = h; heldCells = null; if (h != null && fine != null) { applyHold(fine); publish(true); } }

    /** Zahl der gehaltenen Zellen (nach dem ersten Schritt). */
    public int heldCount() { int[] c = heldCells; return c == null ? 0 : c.length; }

    private void applyHold(Grid g) {
        if (hold == null) return;
        if (heldCells == null) {
            int[] tmp = new int[g.nx * g.nz];
            int k = 0;
            for (int j = 0; j < g.nz; j++)
                for (int i = 0; i < g.nx; i++) {
                    int c = j * g.nx + i;
                    if (g.h[c] > -999 && hold.held(g.x0 + (i + 0.5) * g.dx, g.z0 + (j + 0.5) * g.dx)) { tmp[k++] = c; g.touch(i, j, 2); }
                }
            heldCells = java.util.Arrays.copyOf(tmp, k);
        }
        float e = (float) hold.eta(time);
        for (int c : heldCells) g.eta[c] = Math.max(e, -g.h[c]);
    }

    /** Ein Einschlag: so viel Volumen (m³) wird am Ort verdrängt (negativ: entnommen). Sicher aus jedem Faden. */
    public void addVolume(double x, double z, double volume) { addVolume(x, z, volume, Double.NaN); }

    /** Wie oben, mit Halbmesser der Quelle (m; NaN: aus dem Volumen). */
    public void addVolume(double x, double z, double volume, double radius) {
        if (volume != 0 && Double.isFinite(volume)) sources.add(new double[]{x, z, volume, radius});
    }

    /**
     * Ein Einschlag, der sich über {@code seconds} Sekunden Laufzeit verteilt (ein Turm taucht nicht auf einmal ein):
     * je Schritt der passende Teil. Sicher aus jedem Faden.
     */
    public void addVolumeOver(double x, double z, double volume, double radius, double seconds) {
        if (seconds <= 0) { addVolume(x, z, volume, radius); return; }
        if (volume != 0 && Double.isFinite(volume)) sources.add(new double[]{x, z, volume, radius, seconds});
    }

    private final List<double[]> running = new ArrayList<>();

    private void applySource(double[] s) {
        if (s.length > 4) { running.add(new double[]{s[0], s[1], s[2] / s[4], s[3], s[4]}); return; }
        Grid g = fine != null && fine.contains(s[0], s[1]) ? fine : coarse != null && coarse.contains(s[0], s[1]) ? coarse : null;
        if (g == null) return;
        double r = Double.isNaN(s[3]) ? Math.max(1.5 * g.dx, 0.5 * Math.cbrt(Math.abs(s[2]))) : Math.max(g.dx, s[3]);
        int ci = (int) Math.floor((s[0] - g.x0) / g.dx), cj = (int) Math.floor((s[1] - g.z0) / g.dx);
        int pad = (int) Math.ceil(2.5 * r / g.dx);
        double sum = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int j = Math.max(0, cj - pad); j <= Math.min(g.nz - 1, cj + pad); j++)
                for (int i = Math.max(0, ci - pad); i <= Math.min(g.nx - 1, ci + pad); i++) {
                    int c = j * g.nx + i;
                    if (g.h[c] <= 0 && g.eta[c] + g.h[c] <= Grid.DMIN) continue;   // nur ins Wasser
                    double dx = g.x0 + (i + 0.5) * g.dx - s[0], dz = g.z0 + (j + 0.5) * g.dx - s[1];
                    double w = Math.exp(-(dx * dx + dz * dz) / (r * r));
                    if (pass == 0) sum += w;
                    else {
                        float add = (float) (s[2] * w / (sum * g.dx * g.dx));
                        g.eta[c] = Math.max(-g.h[c], g.eta[c] + add);
                    }
                }
            if (sum <= 0) return;
        }
        g.touch(ci, cj, pad + 2);
        if (g == fine && coarse != null) touchCoarseOverFine();
    }

    private void touchCoarseOverFine() {
        int a = (int) Math.round((fine.x0 - coarse.x0) / coarse.dx), b = (int) Math.round((fine.z0 - coarse.z0) / coarse.dx);
        int n = (int) Math.round(fine.nx * fine.dx / coarse.dx);
        coarse.touch(a + n / 2, b + n / 2, n / 2 + 3);
    }

    // ------------------------------------------------------------------ Schritt

    /** dt Sekunden weiterrechnen (fester Schritt nach CFL, Reste werden mitgenommen). Ruhig: fast nichts zu tun. */
    public void step(double dt) {
        long t0 = System.nanoTime();
        synchronized (this) {
            if (fine == null) return;
            double[] s;
            while ((s = sources.poll()) != null) applySource(s);
            accum += dt;
            boolean any = false;
            while (accum >= dtc) {
                accum -= dtc;
                if (!fine.active && (coarse == null || !coarse.active) && running.isEmpty()) { time += dtc; continue; }
                one();
                any = true;
            }
            if (any) publish(false);
        }
        busyNs += System.nanoTime() - t0;
        busyWindow += dt;
        if (busyWindow >= 1) {
            busyMs = busyNs / 1e6 / busyWindow;
            busyNs = 0;
            busyWindow = 0;
        }
    }

    /** Ein grober Schritt mit den feinen Unterschritten. */
    private void one() {
        // Quellen, die sich über die Zeit verteilen: Volumen je Sekunde mal Schritt
        for (int k = running.size() - 1; k >= 0; k--) {
            double[] q = running.get(k);
            double dtq = Math.min(dtc, q[4]);
            applySource(new double[]{q[0], q[1], q[2] * dtq, q[3]});
            q[4] -= dtc;
            if (q[4] <= 0) running.remove(k);
        }
        if (coarse == null) {
            fine.step(dtf, gravity, manning, edgeDamping, maxSpeed, null);
            applyHold(fine);
            cellsDone += window(fine);
            time += dtf;
        } else {
            // was am Rand des feinen ankommt, braucht das feine; was im feinen läuft, braucht das grobe darüber
            if (fine.active) touchCoarseOverFine();
            coarse.step(dtc, gravity, manning, edgeDamping, maxSpeed, null);
            cellsDone += window(coarse);
            boundary(bndNewX, bndNewZ);
            if (!fine.active && coarseNearFine()) fine.touch(fine.nx / 2, fine.nz / 2, fine.nx);
            // am Rand des feinen genau der Durchfluss, mit dem das grobe gerechnet hat: so geht kein Volumen verloren
            Grid.Boundary edge = new Grid.Boundary() {
                @Override public double qx(Grid g, int i, int j) { return bndNewX[(i == 0 ? 0 : 1) * g.nz + j]; }
                @Override public double qz(Grid g, int i, int j) { return bndNewZ[(j == 0 ? 0 : 1) * g.nx + i]; }
            };
            for (int k = 0; k < subs; k++) {
                fine.step(dtf, gravity, manning, 0, maxSpeed, edge);
                applyHold(fine);
                cellsDone += window(fine);
            }
            if (fine.active) feedback();
            time += dtc;
        }
        steps++;
        // Auflaufen
        runups(fine);
        if (coarse != null) runups(coarse);
        // Pegel
        sampleClock += dtc;
        if (sampleClock >= 0.05) {
            sampleClock = 0;
            for (Gauge g : gauges) g.record(time, level(g.x, g.z));
        }
        // ruhig geworden? Fenster verkleinern
        shrinkClock += dtc;
        if (shrinkClock >= 1) {
            shrinkClock = 0;
            fine.shrink(2e-4f);
            if (coarse != null) coarse.shrink(2e-4f);
        }
    }

    private static long window(Grid g) { return g.active ? (long) (g.i1 - g.i0 + 1) * (g.j1 - g.j0 + 1) : 0; }

    private boolean coarseNearFine() {
        if (!coarse.active) return false;
        double x0 = coarse.x0 + coarse.i0 * coarse.dx, x1 = coarse.x0 + (coarse.i1 + 1) * coarse.dx;
        double z0 = coarse.z0 + coarse.j0 * coarse.dx, z1 = coarse.z0 + (coarse.j1 + 1) * coarse.dx;
        return x1 >= fine.x0 && x0 <= fine.x0 + fine.nx * fine.dx && z1 >= fine.z0 && z0 <= fine.z0 + fine.nz * fine.dx;
    }

    /** Durchfluss des groben Gitters auf den Randkanten des feinen. */
    private void boundary(float[] bx, float[] bz) {
        int r = (int) Math.round(coarse.dx / fine.dx);
        int ci0 = (int) Math.round((fine.x0 - coarse.x0) / coarse.dx), cj0 = (int) Math.round((fine.z0 - coarse.z0) / coarse.dx);
        int nC = fine.nx / r;
        for (int side = 0; side < 2; side++) {
            int ci = ci0 + side * nC;
            for (int j = 0; j < fine.nz; j++) {
                int cj = cj0 + j / r;
                bx[side * fine.nz + j] = ci >= 0 && ci <= coarse.nx && cj >= 0 && cj < coarse.nz ? coarse.qx[cj * (coarse.nx + 1) + ci] : 0;
            }
            int cj = cj0 + side * (fine.nz / r);
            for (int i = 0; i < fine.nx; i++) {
                int cii = ci0 + i / r;
                bz[side * fine.nx + i] = cii >= 0 && cii < coarse.nx && cj >= 0 && cj <= coarse.nz ? coarse.qz[cj * coarse.nx + cii] : 0;
            }
        }
    }

    /** Das feine Gitter schreibt seinen Stand und Durchfluss in die groben Zellen darüber (ohne den Rand). */
    private void feedback() {
        int r = (int) Math.round(coarse.dx / fine.dx);
        int ci0 = (int) Math.round((fine.x0 - coarse.x0) / coarse.dx), cj0 = (int) Math.round((fine.z0 - coarse.z0) / coarse.dx);
        int n = fine.nx / r;
        for (int b = 0; b < n; b++)
            for (int a = 0; a < n; a++) {
                int ci = ci0 + a, cj = cj0 + b;
                if (ci < 0 || cj < 0 || ci >= coarse.nx || cj >= coarse.nz) continue;
                // Wasserstand: Mittel der nassen feinen Zellen (der Grund ist gemischt, der Spiegel nicht)
                float e = 0;
                int wet = 0;
                for (int y = 0; y < r; y++)
                    for (int x = 0; x < r; x++) {
                        int c = (b * r + y) * fine.nx + a * r + x;
                        if (fine.eta[c] + fine.h[c] <= Grid.DMIN) continue;
                        e += fine.eta[c];
                        wet++;
                    }
                int c = cj * coarse.nx + ci;
                if (coarse.h[c] <= -999) continue;   // Wand bleibt Wand
                coarse.eta[c] = wet == 0 ? -coarse.h[c] : Math.max(-coarse.h[c], e / wet);
                // Durchfluss auf der linken und der oberen Kante der groben Zelle (innen)
                float qx = 0, qz = 0;
                for (int y = 0; y < r; y++) qx += fine.qx[(b * r + y) * (fine.nx + 1) + a * r];
                for (int x = 0; x < r; x++) qz += fine.qz[(b * r) * fine.nx + a * r + x];
                if (a > 0) coarse.qx[cj * (coarse.nx + 1) + ci] = qx / r;
                if (b > 0) coarse.qz[cj * coarse.nx + ci] = qz / r;
            }
    }

    private void runups(Grid g) {
        if (!g.active) return;
        g.runup(time, c -> {
            int i = c % g.nx, j = c / g.nx;
            double x = g.x0 + (i + 0.5) * g.dx, z = g.z0 + (j + 0.5) * g.dx;
            if (g == coarse && fine.contains(x, z)) return;
            long key = ((long) Math.floor(x / 25) << 32) ^ (Math.floor(z / 25) < 0 ? 0xFFFFFFFFL & (long) Math.floor(z / 25) : (long) Math.floor(z / 25));
            Double last = runupSeen.get(key);
            if (last != null && time - last < 1) return;
            runupSeen.put(key, time);
            if (onRunup != null) onRunup.runup(x, z, g.runup[c], time);
        });
    }

    // ------------------------------------------------------------------ Abfragen

    /** Stand der Welle zum Ablesen in anderen Fäden: je Gitter der Wasserstand, trocken als {@link #DRY}. */
    public static final float DRY = -30f;

    /**
     * Ein Gitter im Stand: Ecke, Zellgröße, Zellen, Wasserstand (m über dem Ruhespiegel; trocken {@link #DRY}) und je
     * Zelle an Land die Zeit, zu der sie zuletzt nass war (s Laufzeit; nie: −1e9).
     */
    public record Level(double x0, double z0, double cell, int nx, int nz, float[] eta, float[] wet) { }

    /** Stand beider Gitter; {@code version} wächst mit jedem Schritt, in dem sich etwas bewegt hat. */
    public record Snapshot(long version, double time, Level fine, Level coarse, boolean calm, int[] dirtyFine, int[] dirtyCoarse) { }

    private float[] pubF, pubC, wetF, wetC;
    private long version;
    private final int[] dirtyF = {Integer.MAX_VALUE, -1, Integer.MAX_VALUE, -1}, dirtyC = {Integer.MAX_VALUE, -1, Integer.MAX_VALUE, -1};

    private void publish(boolean all) {
        if (pubF == null || pubF.length != fine.eta.length) { pubF = new float[fine.eta.length]; wetF = new float[fine.eta.length]; all = true; }
        if (coarse != null && (pubC == null || pubC.length != coarse.eta.length)) { pubC = new float[coarse.eta.length]; wetC = new float[coarse.eta.length]; all = true; }
        maxEta = 0;
        copy(fine, pubF, wetF, all, dirtyF);
        if (coarse != null) copy(coarse, pubC, wetC, all, dirtyC);
        version++;
        boolean calm = !fine.active && (coarse == null || !coarse.active);
        snap = new Snapshot(version, time, new Level(fine.x0, fine.z0, fine.dx, fine.nx, fine.nz, pubF, wetF),
                coarse == null ? null : new Level(coarse.x0, coarse.z0, coarse.dx, coarse.nx, coarse.nz, pubC, wetC), calm,
                dirtyF.clone(), coarse == null ? null : dirtyC.clone());
    }

    private void copy(Grid g, float[] out, float[] wet, boolean all, int[] dirty) {
        int a = all ? 0 : g.i0, b = all ? g.nx - 1 : g.i1, c = all ? 0 : g.j0, d = all ? g.nz - 1 : g.j1;
        if (!all && !g.active) {
            // ein letztes Mal das alte Fenster, damit der Stand ruhig wird
            if (g.i1 < g.i0) return;
        }
        for (int j = c; j <= d; j++)
            for (int i = a; i <= b; i++) {
                int q = j * g.nx + i;
                float e = g.eta[q], dep = e + g.h[q];
                out[q] = dep > 0.05f ? e : DRY;   // ein Film unter 5 cm zählt als trocken (sonst flimmert der Spiegel auf dem Ufer)
                wet[q] = g.wetAt[q];
                if (dep > Grid.DMIN && g.h[q] > 0) maxEta = Math.max(maxEta, Math.abs(e));
            }
        dirty[0] = Math.min(dirty[0], a); dirty[1] = Math.max(dirty[1], b);
        dirty[2] = Math.min(dirty[2], c); dirty[3] = Math.max(dirty[3], d);
    }

    /** Für {@code gl/}: Fenster, die sich seit dem letzten Aufruf geändert haben, zurücksetzen (nach dem Hochladen). */
    public synchronized void clearDirty(long seen) {
        if (seen != version) return;   // inzwischen ein neuer Stand: dessen Fenster schließt das alte ein
        dirtyF[0] = dirtyC[0] = dirtyF[2] = dirtyC[2] = Integer.MAX_VALUE;
        dirtyF[1] = dirtyC[1] = dirtyF[3] = dirtyC[3] = -1;
    }

    public Snapshot snapshot() { return snap; }

    /** Höhe der laufenden Welle am Ort (m über dem Ruhespiegel); 0, wo trocken oder außerhalb der Gitter. */
    public double heightAt(double x, double z) {
        double v = level(snap, x, z);
        return Double.isNaN(v) ? 0 : v;
    }

    /** Wie {@link #heightAt}, aber NaN, wo trocken ist (für Auftrieb und Nässe). */
    public double levelAt(double x, double z) { return level(snap, x, z); }

    private double level(double x, double z) {
        // im Faden des Schritts: der eigene Stand
        Grid g = fine.contains(x, z) ? fine : coarse != null && coarse.contains(x, z) ? coarse : null;
        if (g == null) return Double.NaN;
        return Grid.sample(g.eta, g.h, g.nx, g.nz, g.dx, g.x0, g.z0, x, z);
    }

    private static double level(Snapshot s, double x, double z) {
        if (s == null) return Double.NaN;
        Level l = inside(s.fine, x, z) ? s.fine : inside(s.coarse, x, z) ? s.coarse : null;
        if (l == null) return Double.NaN;
        double u = (x - l.x0) / l.cell - 0.5, v = (z - l.z0) / l.cell - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fu = u - i, fv = v - j, sum = 0, w = 0;
        for (int b = 0; b <= 1; b++)
            for (int a = 0; a <= 1; a++) {
                int ii = Math.max(0, Math.min(l.nx - 1, i + a)), jj = Math.max(0, Math.min(l.nz - 1, j + b));
                float e = l.eta[jj * l.nx + ii];
                if (e <= DRY + 1) continue;
                double ww = (a == 0 ? 1 - fu : fu) * (b == 0 ? 1 - fv : fv);
                sum += ww * e;
                w += ww;
            }
        return w < 1e-6 ? Double.NaN : sum / w;
    }

    private static boolean inside(Level l, double x, double z) {
        return l != null && x >= l.x0 && z >= l.z0 && x < l.x0 + l.nx * l.cell && z < l.z0 + l.nz * l.cell;
    }

    /** Neigung der Welle (dη/dx, dη/dz) am Ort, aus dem Stand. */
    public double[] slopeAt(double x, double z) {
        Snapshot s = snap;
        double e = s == null ? 2 : s.fine != null && inside(s.fine, x, z) ? s.fine.cell : 8;
        double a = heightAt(x + e, z), b = heightAt(x - e, z), c = heightAt(x, z + e), d = heightAt(x, z - e);
        return new double[]{(a - b) / (2 * e), (c - d) / (2 * e)};
    }

    /** Strömung der Welle am Ort (m/s, x und z): Durchfluss durch Tiefe (aus dem eigenen Stand). */
    public synchronized double[] velocityAt(double x, double z) {
        Grid g = fine != null && fine.contains(x, z) ? fine : coarse != null && coarse.contains(x, z) ? coarse : null;
        if (g == null) return new double[2];
        int i = (int) Math.floor((x - g.x0) / g.dx), j = (int) Math.floor((z - g.z0) / g.dx);
        int c = j * g.nx + i;
        double d = g.eta[c] + g.h[c];
        if (d <= Grid.DMIN) return new double[2];
        double qx = 0.5 * (g.qx[j * (g.nx + 1) + i] + g.qx[j * (g.nx + 1) + i + 1]), qz = 0.5 * (g.qz[j * g.nx + i] + g.qz[(j + 1) * g.nx + i]);
        return new double[]{qx / d, qz / d};
    }

    /** Höchster Stand, den das Wasser an Land am Ort erreicht hat (m über dem Ruhespiegel; NaN: hier nie). */
    public synchronized double runupAt(double x, double z) {
        Grid g = fine != null && fine.contains(x, z) ? fine : coarse != null && coarse.contains(x, z) ? coarse : null;
        if (g == null) return Double.NaN;
        int i = (int) Math.floor((x - g.x0) / g.dx), j = (int) Math.floor((z - g.z0) / g.dx);
        return g.runup[j * g.nx + i];
    }

    /** Sekunden, seit der Ort an Land zuletzt nass war (unendlich: nie; im Wasser 0). */
    public synchronized double wetAgo(double x, double z) {
        Grid g = fine != null && fine.contains(x, z) ? fine : coarse != null && coarse.contains(x, z) ? coarse : null;
        if (g == null) return Double.POSITIVE_INFINITY;
        int i = (int) Math.floor((x - g.x0) / g.dx), j = (int) Math.floor((z - g.z0) / g.dx);
        int c = j * g.nx + i;
        if (g.h[c] > 0) return 0;
        return g.wetAt[c] < -1e8 ? Double.POSITIVE_INFINITY : time - g.wetAt[c];
    }

    /** Ruhetiefe am Ort, wie das Gitter sie kennt (m; NaN außerhalb). */
    public synchronized double depthAt(double x, double z) {
        Grid g = fine != null && fine.contains(x, z) ? fine : coarse != null && coarse.contains(x, z) ? coarse : null;
        if (g == null) return Double.NaN;
        return g.h[(int) Math.floor((z - g.z0) / g.dx) * g.nx + (int) Math.floor((x - g.x0) / g.dx)];
    }

    /** Volumen über dem Ruhespiegel in beiden Gittern (m³; das grobe ohne die Fläche des feinen). */
    public synchronized double volume() {
        if (coarse == null) return fine.volume();
        double v = fine.volume();
        for (int c = 0; c < coarse.eta.length; c++) {
            int i = c % coarse.nx, j = c / coarse.nx;
            if (fine.contains(coarse.x0 + (i + 0.5) * coarse.dx, coarse.z0 + (j + 0.5) * coarse.dx)) continue;
            v += (coarse.eta[c] + coarse.h[c] - Math.max(0, coarse.h[c])) * coarse.dx * coarse.dx;
        }
        return v;
    }

    /** Läuft gerade eine Welle? */
    public boolean active() { Snapshot s = snap; return s != null && !s.calm(); }
    public double time() { return time; }
    public long steps() { return steps; }
    /** Rechenzeit je Sekunde Welle (ms), gemittelt über die letzte Sekunde. */
    public double busyMs() { return busyMs; }
    /** Größte Auslenkung im Wasser beim letzten Stand (m). */
    public double maxHeight() { return maxEta; }
    /** Schritt des groben Gitters (s) und Zahl der feinen Unterschritte. */
    public double coarseStep() { return dtc; }
    public int subSteps() { return subs; }

    /** Feines und grobes Gitter: {x0, z0, Breite} (m); grobes null, wenn keins. */
    public double[][] rects() {
        Snapshot s = snap;
        if (s == null) return new double[2][];
        return new double[][]{{s.fine.x0(), s.fine.z0(), s.fine.nx() * s.fine.cell()},
                s.coarse == null ? null : new double[]{s.coarse.x0(), s.coarse.z0(), s.coarse.nx() * s.coarse.cell()}};
    }

    // ------------------------------------------------------------------ Pegel

    /** Ein Pegel: zeichnet den Wasserstand an einem Ort alle 0,05 s auf (die letzten 6 000 Werte, 5 min). */
    public static final class Gauge {
        public final String name;
        public final double x, z;
        private final float[] t = new float[6000], v = new float[6000];
        private int n, head;
        private double max, min;

        Gauge(String name, double x, double z) { this.name = name; this.x = x; this.z = z; }

        synchronized void record(double time, double level) {
            float e = Double.isNaN(level) ? Float.NaN : (float) level;
            t[head] = (float) time;
            v[head] = e;
            head = (head + 1) % t.length;
            n = Math.min(n + 1, t.length);
            if (!Float.isNaN(e)) { max = Math.max(max, e); min = Math.min(min, e); }
        }

        /** Verlauf {Zeiten, Werte} der letzten {@code seconds} Sekunden (NaN: trocken). */
        public synchronized float[][] series(double seconds) {
            int k = Math.min(n, (int) Math.ceil(seconds / 0.05));
            float[] tt = new float[k], vv = new float[k];
            for (int q = 0; q < k; q++) {
                int idx = Math.floorMod(head - k + q, t.length);
                tt[q] = t[idx];
                vv[q] = v[idx];
            }
            return new float[][]{tt, vv};
        }

        public synchronized double max() { return max; }
        public synchronized double min() { return min; }
        public synchronized void reset() { max = min = 0; }
    }

    /** Einen Pegel setzen. */
    public synchronized Gauge gauge(String name, double x, double z) {
        Gauge g = new Gauge(name, x, z);
        gauges.add(g);
        return g;
    }

    public synchronized List<Gauge> gauges() { return new ArrayList<>(gauges); }

    /** Der See wieder still (nach einer Prüfung). */
    public synchronized void calm() {
        sources.clear();
        for (Grid g : coarse == null ? new Grid[]{fine} : new Grid[]{fine, coarse}) {
            for (int c = 0; c < g.eta.length; c++) g.eta[c] = g.h[c] < 0 ? -g.h[c] : 0;
            java.util.Arrays.fill(g.qx, 0);
            java.util.Arrays.fill(g.qz, 0);
            g.active = false;
        }
        publish(true);
    }

    /** „wave: läuft · größte Höhe 1,2 m · 512² zu 2 m, 1 024² zu 8 m · 3,4 ms je s · MEDIUM“ */
    public String describe() {
        Snapshot s = snap;
        String state = fine == null ? "kein Grund" : s == null || s.calm() ? "ruhig" : String.format(Locale.GERMAN, "läuft, größte Höhe %.2f m", maxEta);
        return String.format(Locale.GERMAN, "wave: %s · %d² zu %.0f m, %,d² zu %.0f m · Schritt %.3f s (%d×) · %.1f ms je s · %s",
                state, fineN, fineCell, coarseN, coarseCell, dtc, subs, busyMs, quality);
    }
}
