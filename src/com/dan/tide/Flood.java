package com.dan.tide;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Die Überflutung eines Geländes mit Laufzeit (Werkbuch Rungholt, F9 A). Einmal beim Bau rechnet sie je Zelle des
 * Grunds:
 * <ul>
 * <li>die <b>Verbindungshöhe</b>: den Wasserstand, ab dem die Zelle mit dem offenen Meer verbunden ist (Flutung nach
 * Priorität vom Rand her, wie beim Füllen eines Reliefs: die niedrigste Schwelle auf dem besten Weg zum Meer);</li>
 * <li>die <b>Laufzeit</b> der Tidewelle vom Rand bis dorthin: Weg durch das Gelände mit {@code √(g·Tiefe)} bei mittlerem
 * Hochwasser, gebremst durch Reibung ({@link #FRICTION}); durch die Priele schnell, über das Watt langsam.</li>
 * </ul>
 * Je Schritt ist das Wasser in einer Zelle {@code Pegel(t − Laufzeit)}, sobald dieser über der Verbindungshöhe liegt.
 * Sinkt er darunter, bleibt in Mulden das Wasser bis zur Schwelle stehen (Pfütze) und versickert langsam
 * ({@link Tide#seepage}). Dazu die Trockenzeit je Zelle (seit wann frei) und die Strömung: durch jede Linie gleicher
 * Laufzeit fließt, was dahinter steigt oder fällt, verteilt nach Tiefe hoch 3/2 (Chézy: u ∝ √h); Richtung entlang der
 * Laufzeit, landwärts bei Flut, seewärts bei Ebbe.
 * <p>
 * Gerechnet wird auf einem vergröberten Gitter ({@link Tide.Quality#coarse}): je Block die tiefste Schwelle, der tiefste
 * Grund und die Laufzeit dort. Ein Schritt ist eine Rechnung je Zelle, verteilt auf alle Kerne; die Pegel für alle
 * Laufzeiten kommen aus einer Tabelle. Kennt keine Grafik.
 */
public final class Flood {
    /** Reibung der Tidewelle im flachen Wasser [G]: die Welle läuft mit 70 % von √(g·h). */
    public static final double FRICTION = 0.7;
    /** Tiefe für die Laufzeit, mindestens und höchstens (m). */
    public static final double MIN_DEPTH = 0.3, MAX_DEPTH = 40;
    /** Trocken heißt flacher als 1 cm. */
    public static final float WET = 0.01f;
    /** Größte Laufzeit, die zählt (s); dahinter liegende Zellen gelten als 4 h entfernt. */
    public static final double MAX_TRAVEL = 4 * 3600;
    /** Breite der Laufzeitbänder für die Strömung (s). */
    public static final double BAND = 120;
    /** Kennung im Kopf der Zwischendatei. */
    private static final int MAGIC = 0x52484644, VERSION = 3;

    public final int nx, nz;
    /** Nordwestecke (x, z) und Zellgröße des vergröberten Gitters (m). */
    public final double x0, z0, cell;
    /** Je Zelle: tiefster Grund (m), Verbindungshöhe (m, +∞ = nie), Laufzeit (s, +∞ = nie). */
    public final float[] ground, spill, travel;
    /** Je Zelle: Richtung zum Meer (Einheitsvektor entlang fallender Laufzeit). */
    final float[] seaX, seaZ;
    /** Laufzeitband je Zelle (−1 = nie verbunden) und Zahl der Bänder. */
    final short[] band;
    final int bands;
    /** Zahl der Zellen mit Verbindung zum Meer (irgendwann). */
    final int reachable;
    /** Wasserstand für die Laufzeit (m) und je Zelle die Länge der Laufzeitlinie, die sie trägt (Zelle²/(c·Band)). */
    public final double refLevel;
    final float[] lineLen, wgt;

    // Zustand
    /** Wasserspiegel je Zelle (m); trocken = Grund. */
    public final float[] surface;
    /** Ob die Zelle gerade mit dem Meer verbunden ist (1) oder nur eine Pfütze hält (0). */
    public final byte[] connected;
    /** Stunden seit dem Trockenfallen (0 unter Wasser). */
    public final float[] dryHours;
    /** Strömung an der Oberfläche (m/s), x nach Osten, z nach Süden. */
    public final float[] u, v;
    /** Zählt jeden Schritt (für das Hochladen). */
    public volatile int version;
    /** Dauer des letzten Schritts (ms), gleitend gemittelt. */
    public volatile double stepMs;
    public volatile double lastHours = Double.NaN;
    /** Strömung rechnen (Phase 4: ja, für die Prüfung und das HUD; im Bild ab Phase 6), und in jedem wievielten Schritt. */
    public volatile boolean currents = true;
    public volatile int currentEvery = 4;
    /** Zählt jede neue Rechnung der Strömung (für das Hochladen, seit Rungholt Phase 6). */
    public volatile int currentVersion;
    private int stepCount;
    /** Statistik des letzten Schritts: Anteil nasser Zellen, Pfützen, größte Strömung (m/s). */
    public volatile double wetShare, maxCurrent;
    /**
     * Seit Rungholt Phase 12: ein Becken mit eigenem Spiegel (ein Polder hinter einem gebrochenen Deich, dessen Stand die
     * App aus Zu- und Abfluss rechnet): in den Zellen von {@code basinMask} steht das Wasser auf {@code basinLevel} (m),
     * wo der Grund tiefer liegt, unabhängig vom Pegel am Rand. null oder NaN: aus.
     */
    public volatile boolean[] basinMask;
    public volatile double basinLevel = Double.NaN;
    public volatile int puddles;
    private final List<double[]> watches = new ArrayList<>();
    private final List<Boolean> watchWet = new ArrayList<>();
    public Tide.CellEvent onDry, onWet;
    /** Dauer des Baus (ms) und ob er aus der Zwischendatei kam. */
    public double buildMs;
    public boolean fromCache;

    private Flood(int nx, int nz, double x0, double z0, double cell, float[] ground, float[] spill, float[] travel, double refLevel) {
        this.refLevel = refLevel;
        this.nx = nx;
        this.nz = nz;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        this.ground = ground;
        this.spill = spill;
        this.travel = travel;
        int n = nx * nz;
        seaX = new float[n];
        seaZ = new float[n];
        band = new short[n];
        surface = ground.clone();
        connected = new byte[n];
        dryHours = new float[n];
        u = new float[n];
        v = new float[n];
        wgt = new float[n];
        lineLen = new float[n];
        for (int k = 0; k < n; k++) lineLen[k] = (float) (cell * cell / (celerity(ground[k], refLevel) * BAND));
        int maxB = 0, reach = 0;
        for (int k = 0; k < n; k++) {
            float t = travel[k];
            if (Float.isInfinite(t) || Float.isInfinite(spill[k])) { band[k] = -1; continue; }
            reach++;
            int b = (int) Math.min(Short.MAX_VALUE, t / BAND);
            band[k] = (short) b;
            maxB = Math.max(maxB, b);
        }
        bands = maxB + 1;
        reachable = reach;
        // Richtung zum Meer: fallende Laufzeit (zentrale Differenzen über nasse Nachbarn)
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                if (band[k] < 0) continue;
                double gx = diff(i, j, 1, 0), gz = diff(i, j, 0, 1), l = Math.hypot(gx, gz);
                if (l > 1e-9) { seaX[k] = (float) (-gx / l); seaZ[k] = (float) (-gz / l); }
            }
    }

    private double diff(int i, int j, int di, int dj) {
        int k = j * nx + i;
        float c = travel[k];
        int ia = Math.max(0, i - di), ja = Math.max(0, j - dj), ib = Math.min(nx - 1, i + di), jb = Math.min(nz - 1, j + dj);
        float a = travel[ja * nx + ia], b = travel[jb * nx + ib];
        if (Float.isInfinite(a)) a = c;
        if (Float.isInfinite(b)) b = c;
        return (b - a) / Math.max(1, (ib - ia) + (jb - ja));
    }

    // ------------------------------------------------------------------ Bau

    /**
     * Aus einem Höhenfeld (Zeilen von Nord nach Süd, m über dem Bezug) die Überflutungskarte bauen.
     *
     * @param h        Höhe je Zelle (nx · nz)
     * @param x0, z0   Nordwestecke (m), cell: Zellgröße (m)
     * @param seed     Zellen, an denen das offene Meer anliegt (etwa der seeseitige Rand); null = der ganze Rand
     * @param blocked  Zellen, die nie Wasser bekommen (etwa Köge hinter dem Deich), oder null
     * @param refLevel Wasserstand für die Tiefe der Laufzeit (etwa mittleres Hochwasser)
     * @param coarse   Vergröberung (1, 2, 4 …)
     */
    public static Flood build(float[] h, int nx, int nz, double x0, double z0, double cell, boolean[] seed, boolean[] blocked, double refLevel, int coarse) {
        long t0 = System.nanoTime();
        int n = nx * nz;
        float[] spillF = new float[n], travelF = new float[n];
        Arrays.fill(spillF, Float.POSITIVE_INFINITY);
        Arrays.fill(travelF, Float.POSITIVE_INFINITY);
        // 1 Verbindungshöhe: Flutung nach Priorität vom Rand her
        Heap q = new Heap(n);
        for (int k = 0; k < n; k++) {
            int i = k % nx, j = k / nx;
            boolean edge = i == 0 || j == 0 || i == nx - 1 || j == nz - 1;
            boolean s = seed != null ? seed[k] : edge;
            if (s && (blocked == null || !blocked[k])) { spillF[k] = h[k]; q.push(k, h[k]); }
        }
        boolean[] done = new boolean[n];
        while (q.size > 0) {
            int k = q.pop();
            if (done[k]) continue;
            done[k] = true;
            int i = k % nx, j = k / nx;
            for (int d = 0; d < 8; d++) {
                int ii = i + DI[d], jj = j + DJ[d];
                if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                int m = jj * nx + ii;
                if (done[m] || (blocked != null && blocked[m])) continue;
                float c = Math.max(h[m], spillF[k]);
                if (c < spillF[m]) { spillF[m] = c; q.push(m, c); }
            }
        }
        // 2 Laufzeit: kürzester Weg mit √(g·Tiefe) bei refLevel, gebremst
        q = new Heap(n);
        Arrays.fill(done, false);
        for (int k = 0; k < n; k++) {
            int i = k % nx, j = k / nx;
            boolean edge = i == 0 || j == 0 || i == nx - 1 || j == nz - 1;
            boolean s = seed != null ? seed[k] : edge;
            if (s && !Float.isInfinite(spillF[k])) { travelF[k] = 0; q.push(k, 0); }
        }
        while (q.size > 0) {
            int k = q.pop();
            if (done[k]) continue;
            done[k] = true;
            int i = k % nx, j = k / nx;
            double ck = celerity(h[k], refLevel);
            for (int d = 0; d < 8; d++) {
                int ii = i + DI[d], jj = j + DJ[d];
                if (ii < 0 || jj < 0 || ii >= nx || jj >= nz) continue;
                int m = jj * nx + ii;
                if (done[m] || Float.isInfinite(spillF[m])) continue;
                double dist = cell * (d < 4 ? 1 : Math.sqrt(2));
                float t = (float) (travelF[k] + dist * 2 / (ck + celerity(h[m], refLevel)));
                if (t < travelF[m]) { travelF[m] = t; q.push(m, t); }
            }
        }
        // 3 vergröbern: je Block die tiefste Schwelle mit ihrer Laufzeit, der tiefste Grund
        int c = Math.max(1, coarse), cx = (nx + c - 1) / c, cz = (nz + c - 1) / c;
        float[] g = new float[cx * cz], sp = new float[cx * cz], tr = new float[cx * cz];
        for (int bj = 0; bj < cz; bj++)
            for (int bi = 0; bi < cx; bi++) {
                float gmin = Float.POSITIVE_INFINITY, smin = Float.POSITIVE_INFINITY, tmin = Float.POSITIVE_INFINITY;
                for (int j = bj * c; j < Math.min(nz, bj * c + c); j++)
                    for (int i = bi * c; i < Math.min(nx, bi * c + c); i++) {
                        int k = j * nx + i;
                        gmin = Math.min(gmin, h[k]);
                        if (spillF[k] < smin || (spillF[k] == smin && travelF[k] < tmin)) { smin = spillF[k]; tmin = travelF[k]; }
                    }
                int b = bj * cx + bi;
                g[b] = gmin;
                sp[b] = smin;
                tr[b] = (float) Math.min(tmin, MAX_TRAVEL);
                if (Float.isInfinite(smin)) tr[b] = Float.POSITIVE_INFINITY;
            }
        Flood f = new Flood(cx, cz, x0, z0, cell * c, g, sp, tr, refLevel);
        f.buildMs = (System.nanoTime() - t0) / 1e6;
        return f;
    }

    /** Geschwindigkeit der Tidewelle über einer Zelle (m/s). */
    static double celerity(double ground, double refLevel) {
        double d = Math.max(MIN_DEPTH, Math.min(MAX_DEPTH, refLevel - ground));
        return Math.sqrt(9.81 * d) * FRICTION;
    }

    private static final int[] DI = {1, -1, 0, 0, 1, 1, -1, -1}, DJ = {0, 0, 1, -1, 1, -1, 1, -1};

    /** Binärer Haufen über Zellnummern mit float-Schlüssel (ohne Objekte, für Millionen Zellen). */
    static final class Heap {
        int[] idx;
        float[] key;
        int size;
        Heap(int cap) { idx = new int[Math.max(16, cap / 4)]; key = new float[idx.length]; }
        void push(int i, float k) {
            if (size == idx.length) { idx = Arrays.copyOf(idx, size * 2); key = Arrays.copyOf(key, size * 2); }
            int p = size++;
            while (p > 0) {
                int par = (p - 1) >> 1;
                if (key[par] <= k) break;
                idx[p] = idx[par];
                key[p] = key[par];
                p = par;
            }
            idx[p] = i;
            key[p] = k;
        }
        int pop() {
            int top = idx[0];
            int li = idx[--size];
            float lk = key[size];
            int p = 0;
            while (true) {
                int a = 2 * p + 1;
                if (a >= size) break;
                int b = a + 1 < size && key[a + 1] < key[a] ? a + 1 : a;
                if (key[b] >= lk) break;
                idx[p] = idx[b];
                key[p] = key[b];
                p = b;
            }
            idx[p] = li;
            key[p] = lk;
            return top;
        }
    }

    // ------------------------------------------------------------------ Schritt

    /**
     * Das Wasser zur Zeit {@code hours} (Stunden seit 1970) nach der Tide. Läuft die Zeit weniger als eine Stunde vor,
     * bleiben Pfützen stehen und versickern; ein Sprung (rückwärts oder weiter) setzt nur den neuen Stand.
     */
    public void step(Tide tide, double hours) {
        long t0 = System.nanoTime();
        double prev = lastHours;
        double dt = Double.isNaN(prev) ? 0 : hours - prev;
        boolean jump = Double.isNaN(prev) || !(dt >= 0 && dt < 1);
        // Pegel für alle Laufzeiten: Tabelle zu 15 s
        int nt = (int) (MAX_TRAVEL / 15) + 2;
        float[] lv = new float[nt], sl = new float[nt];
        for (int k = 0; k < nt; k++) {
            double th = hours - k * 15 / 3600.0;
            lv[k] = (float) tide.levelAt(th);
            sl[k] = (float) (tide.slopeAt(th) / 3600);   // m/s
        }
        final float maxPuddle = (float) tide.maxPuddle;
        final boolean[] bm = Double.isNaN(basinLevel) ? null : basinMask;
        final float bl = (float) basinLevel;
        final float seepDt = (float) (tide.seepage * Math.max(0, jump ? 0 : dt));
        final float dtH = (float) (jump ? 0 : dt);
        final boolean reset = jump;
        // nach einem Sprung: die Trockenzeit aus der Tide der letzten 12,5 h schätzen (Pegel zu 5 min, ohne Laufzeit)
        final float[] back = reset ? new float[151] : null;
        if (back != null) for (int k = 0; k < back.length; k++) back[k] = (float) tide.levelAt(hours - k / 12.0);
        int rows = nz, chunks = Math.max(1, Math.min(64, rows / 8));
        int[] wet = new int[chunks], pud = new int[chunks];
        boolean doCur = currents && (reset || stepCount++ % Math.max(1, currentEvery) == 0);
        double[][] bandFlux = doCur ? new double[chunks][bands] : null, bandSec = doCur ? new double[chunks][bands] : null;
        IntStream.range(0, chunks).parallel().forEach(ch -> {
            int ja = ch * rows / chunks, jb = (ch + 1) * rows / chunks;
            int w = 0, p = 0;
            double[] bf = bandFlux != null ? bandFlux[ch] : null, bs = bandSec != null ? bandSec[ch] : null;
            for (int k = ja * nx; k < jb * nx; k++) {
                float g = ground[k], sp = spill[k], s;
                byte con = 0;
                if (Float.isInfinite(sp)) { s = g; }
                else {
                    float tt = travel[k] / 15f;
                    int i0 = Math.min(nt - 2, (int) tt);
                    float f = tt - i0, L = lv[i0] + (lv[i0 + 1] - lv[i0]) * f;
                    if (L > sp) { s = L; con = 1; }
                    else if (!reset && surface[k] > g + WET) {
                        s = Math.min(Math.min(surface[k], sp), g + maxPuddle) - seepDt;   // Pfütze bis zur Schwelle, höchstens maxPuddle tief, versickert
                        if (s < g) s = g;
                    } else s = g;
                    if (bf != null && con == 1 && band[k] >= 0) {
                        float slope = sl[i0] + (sl[i0 + 1] - sl[i0]) * f;
                        bf[band[k]] += slope * cell * cell;     // m³/s, die diese Zelle steigt oder fällt
                        float d = s - g;
                        if (d > WET) {
                            float w15 = d * (float) Math.sqrt(d);   // d^(3/2) nach Chézy
                            wgt[k] = w15;
                            bs[band[k]] += w15 * lineLen[k];
                        } else wgt[k] = 0;
                    }
                }
                if (bm != null && bm[k]) { s = Math.max(g, bl); con = 0; }   // Phase 12: Becken mit eigenem Spiegel (ohne Strömung der Tide)
                float depth = s - g;
                surface[k] = s;
                connected[k] = con;
                if (depth > WET) { w++; dryHours[k] = 0; if (con == 0) p++; }
                else dryHours[k] = reset ? dryEstimate(back, Float.isInfinite(sp) ? Float.POSITIVE_INFINITY : Math.max(sp, g + WET)) : dryHours[k] + dtH;
            }
            wet[ch] = w;
            pud[ch] = p;
        });
        int w = 0, p = 0;
        for (int ch = 0; ch < chunks; ch++) { w += wet[ch]; p += pud[ch]; }
        wetShare = reachable == 0 ? 0 : w / (double) reachable;
        puddles = p;
        if (doCur) currents(bandFlux, bandSec);
        lastHours = hours;
        checkWatches(hours);
        version++;
        double ms = (System.nanoTime() - t0) / 1e6;
        stepMs = stepMs <= 0 ? ms : stepMs * 0.9 + ms * 0.1;
    }

    /** Strömung aus den Bändern: Fluss durch jedes Band = alles, was dahinter steigt; verteilt nach Tiefe^3/2. */
    private void currents(double[][] bandFlux, double[][] bandSec) {
        double[] q = new double[bands], sec = new double[bands];
        for (int ch = 0; ch < bandFlux.length; ch++)
            for (int b = 0; b < bands; b++) { q[b] += bandFlux[ch][b]; sec[b] += bandSec[ch][b]; }
        // Durchfluss durch Band b: alles hinter b (größere Laufzeit) plus die Hälfte von b selbst
        double acc = 0;
        double[] through = new double[bands];
        for (int b = bands - 1; b >= 0; b--) { through[b] = acc + q[b] * 0.5; acc += q[b]; }
        float[] fb = new float[bands];
        for (int b = 0; b < bands; b++) fb[b] = sec[b] > 0 ? (float) (through[b] / sec[b]) : 0;
        int rows = nz, chunks = Math.max(1, Math.min(64, rows / 8));
        double[] mx = new double[chunks];
        IntStream.range(0, chunks).parallel().forEach(ch -> {
            int ja = ch * rows / chunks, jb = (ch + 1) * rows / chunks;
            float m = 0;
            for (int k = ja * nx; k < jb * nx; k++) {
                int b = band[k];
                float d = surface[k] - ground[k];
                if (b < 0 || connected[k] == 0 || d <= WET) { u[k] = 0; v[k] = 0; continue; }
                float qi = fb[b] * wgt[k];                                     // m²/s je m Breite
                float sp = Math.min(3f, Math.abs(qi) / Math.max(0.1f, d));     // m/s, gekappt
                // Flut (Durchfluss > 0, das Hinterland steigt): landwärts, gegen die Richtung zum Meer
                float sv = qi > 0 ? -sp : sp;
                u[k] = sv * seaX[k];
                v[k] = sv * seaZ[k];
                if (sp > m) m = sp;
            }
            mx[ch] = m;
        });
        double m = 0;
        for (double x : mx) m = Math.max(m, x);
        maxCurrent = m;
        currentVersion++;
    }

    // ------------------------------------------------------------------ Abfragen

    int index(double x, double z) {
        int i = (int) Math.floor((x - x0) / cell), j = (int) Math.floor((z - z0) / cell);
        if (i < 0 || j < 0 || i >= nx || j >= nz) return -1;
        return j * nx + i;
    }

    /** Liegt der Ort im Gitter? */
    public boolean inside(double x, double z) { return index(x, z) >= 0; }

    /** Wasserspiegel am Ort (m); trocken: der Grund der Zelle; außerhalb: NaN. */
    public double surfaceAt(double x, double z) { int k = index(x, z); return k < 0 ? Double.NaN : surface[k]; }

    /** Wassertiefe am Ort (m) über dem tiefsten Grund der Zelle. */
    public double depthAt(double x, double z) { int k = index(x, z); return k < 0 ? 0 : Math.max(0, surface[k] - ground[k]); }

    /** Ob am Ort Wasser steht, das mit dem Meer verbunden ist. */
    public boolean connectedAt(double x, double z) { int k = index(x, z); return k >= 0 && connected[k] == 1; }

    /** Verbindungshöhe (m) und Laufzeit (s) am Ort. */
    public double spillAt(double x, double z) { int k = index(x, z); return k < 0 ? Double.NaN : spill[k]; }
    public double travelAt(double x, double z) { int k = index(x, z); return k < 0 ? Double.NaN : travel[k]; }

    /** Stunden seit dem Trockenfallen am Ort. */
    public double dryHoursAt(double x, double z) { int k = index(x, z); return k < 0 ? 0 : dryHours[k]; }

    /** Stunden seit der Pegel (Tabelle zu 5 min rückwärts) zuletzt über {@code thr} stand; 12,5 h, wenn nie. */
    static float dryEstimate(float[] back, float thr) {
        for (int i = 0; i < back.length; i++) if (back[i] > thr) return i / 12f;
        return (back.length - 1) / 12f;
    }

    /** Strömung am Ort (m/s), {vx, vz}. */
    public double[] velocityAt(double x, double z) { int k = index(x, z); return k < 0 ? new double[2] : new double[]{u[k], v[k]}; }

    /**
     * Den Wasserspiegel für eine Textur: je Zelle Spiegel − {@code base} (m), trocken {@code dry} (etwa −30). Für
     * {@code com.dan.ocean} (lange Welle über dem Ruhespiegel).
     */
    public void fill(float[] out, double base, float dry) {
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int k = j * nx; k < (j + 1) * nx; k++)
                out[k] = surface[k] - ground[k] > WET ? (float) (surface[k] - base) : dry;
        });
    }

    /**
     * Eine Fläche beobachten (Mitte, Halbmesser in m): {@link #onDry} und {@link #onWet} melden, wenn mehr als die
     * Hälfte ihrer Zellen trocken fällt oder nass wird. Gibt die Nummer der Fläche.
     */
    public int watch(double x, double z, double radius) {
        watches.add(new double[]{x, z, radius});
        watchWet.add(null);
        return watches.size() - 1;
    }

    /** Anteil nasser Zellen in einer beobachteten Fläche. */
    public double wetShareOf(int id) {
        double[] w = watches.get(id);
        int all = 0, wet = 0, r = (int) Math.ceil(w[2] / cell);
        int ci = (int) Math.floor((w[0] - x0) / cell), cj = (int) Math.floor((w[1] - z0) / cell);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                if (i < 0 || j < 0 || i >= nx || j >= nz) continue;
                double dx = (i + 0.5) * cell + x0 - w[0], dz = (j + 0.5) * cell + z0 - w[1];
                if (dx * dx + dz * dz > w[2] * w[2]) continue;
                int k = j * nx + i;
                all++;
                if (surface[k] - ground[k] > WET) wet++;
            }
        return all == 0 ? 0 : wet / (double) all;
    }

    private void checkWatches(double hours) {
        for (int id = 0; id < watches.size(); id++) {
            boolean wet = wetShareOf(id) > 0.5;
            Boolean was = watchWet.get(id);
            watchWet.set(id, wet);
            if (was == null || was == wet) continue;
            if (wet && onWet != null) onWet.cell(-1, id, hours);
            if (!wet && onDry != null) onDry.cell(-1, id, hours);
        }
    }

    /** Anteil der Zellen (mit Meerzugang) über einer Höhe, die gerade trocken liegen. */
    public double dryShareAbove(double minGround) {
        int all = 0, dry = 0;
        for (int k = 0; k < ground.length; k++) {
            if (Float.isInfinite(spill[k]) || ground[k] < minGround) continue;
            all++;
            if (surface[k] - ground[k] <= WET) dry++;
        }
        return all == 0 ? 0 : dry / (double) all;
    }

    /** Fläche (km²), die zwischen zwei Pegeln nass wird: Zellen mit Verbindungshöhe in (a, b]. */
    public double areaBetween(double a, double b) {
        long k = 0;
        for (float s : spill) if (s > a && s <= b) k++;
        return k * cell * cell / 1e6;
    }

    /** „Überflutung 1024² zu 16 m · nass 38 % · 12 Pfützen · Strömung bis 1,4 m/s · 2,1 ms je Schritt“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Überflutung %d×%d zu %.0f m · nass %.0f %% · %d Pfützenzellen · Strömung bis %.1f m/s · %.2f ms je Schritt%s",
                nx, nz, cell, wetShare * 100, puddles, maxCurrent, stepMs, fromCache ? " · aus der Zwischendatei" : String.format(Locale.GERMAN, " · gebaut in %.1f s", buildMs / 1000));
    }

    // ------------------------------------------------------------------ Zwischendatei

    /** Prüfsumme über das Höhenfeld und die Eingänge, damit die Zwischendatei nur passt, wenn alles gleich ist. */
    public static long key(float[] h, boolean[] seed, boolean[] blocked, double refLevel, int coarse, double cell) {
        long s = 1125899906842597L;
        for (int k = 0; k < h.length; k += 7) s = 31 * s + Float.floatToIntBits(h[k]);
        if (seed != null) for (int k = 0; k < seed.length; k += 3) s = 31 * s + (seed[k] ? 1 : 0);
        if (blocked != null) for (int k = 0; k < blocked.length; k += 3) s = 31 * s + (blocked[k] ? 1 : 0);
        s = 31 * s + Double.doubleToLongBits(refLevel) + 17L * coarse + Double.doubleToLongBits(cell) + Double.doubleToLongBits(FRICTION);
        return s ^ h.length;
    }

    /** Karte in eine Datei schreiben (Grund, Schwelle, Laufzeit; little endian). */
    public void save(File f, long key) throws IOException {
        File dir = f.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        int n = nx * nz;
        ByteBuffer b = ByteBuffer.allocate(56 + n * 12).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(MAGIC).putInt(VERSION).putLong(key).putInt(nx).putInt(nz).putDouble(x0).putDouble(z0).putDouble(cell).putDouble(refLevel);
        for (float x : ground) b.putFloat(x);
        for (float x : spill) b.putFloat(x);
        for (float x : travel) b.putFloat(x);
        b.flip();
        try (FileChannel c = FileChannel.open(f.toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            while (b.hasRemaining()) c.write(b);
        }
    }

    /** Karte aus der Datei, wenn Schlüssel und Kopf passen; sonst null. */
    public static Flood load(File f, long key) {
        if (!f.isFile()) return null;
        try {
            ByteBuffer b = ByteBuffer.wrap(Files.readAllBytes(f.toPath())).order(ByteOrder.LITTLE_ENDIAN);
            if (b.getInt() != MAGIC || b.getInt() != VERSION || b.getLong() != key) return null;
            int nx = b.getInt(), nz = b.getInt();
            double x0 = b.getDouble(), z0 = b.getDouble(), cell = b.getDouble(), ref = b.getDouble();
            int n = nx * nz;
            if (b.remaining() != n * 12) return null;
            float[] g = new float[n], s = new float[n], t = new float[n];
            for (int k = 0; k < n; k++) g[k] = b.getFloat();
            for (int k = 0; k < n; k++) s[k] = b.getFloat();
            for (int k = 0; k < n; k++) t[k] = b.getFloat();
            Flood fl = new Flood(nx, nz, x0, z0, cell, g, s, t, ref);
            fl.fromCache = true;
            return fl;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Bauen oder aus der Zwischendatei laden (und sie danach schreiben). */
    public static Flood buildCached(File cache, float[] h, int nx, int nz, double x0, double z0, double cellSize, boolean[] seed, boolean[] blocked,
            double refLevel, int coarse) {
        long key = key(h, seed, blocked, refLevel, coarse, cellSize);
        if (cache != null) {
            long t0 = System.nanoTime();
            Flood f = load(cache, key);
            if (f != null) { f.buildMs = (System.nanoTime() - t0) / 1e6; return f; }
        }
        Flood f = build(h, nx, nz, x0, z0, cellSize, seed, blocked, refLevel, coarse);
        if (cache != null) try { f.save(cache, key); } catch (IOException e) { /* ohne Zwischendatei weiter */ }
        return f;
    }
}
