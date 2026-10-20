package com.dan.rungholt.scene;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

/**
 * Das Gelände heute (Phase 2, F2 A): ein Höhenraster von 2048 × 2048 Zellen zu 8 m, also 16,4 km, das den Ausschnitt
 * aus F1 mit Rand deckt; Höhen in m über NHN (y = 0). Erzeugt von {@code tools.GelaendeImport} aus den Küstenhöhen der
 * BAW (topography_max_z 2015–2021, 10 m), gelesen aus {@code daten/gelaende/}. Ohne diese Dateien entsteht das Gelände
 * aus der Bühne von Phase 1 ({@link Buehne}).
 * <p>
 * Je Zelle die Art: {@link #WATT} (seeseitig, unter dem mittleren Hochwasser plus 0,3 m: Watt, Priel, Strom),
 * {@link #VORLAND} (seeseitig darüber: Hallig, Salzwiese, Sandbank über Hochwasser), {@link #ERHOEHT} (Deich und Warft,
 * ab NHN +3,5 m), {@link #KOOG} (hinter den Deichen, dort steht kein Meer) und {@link #RAND} (ohne Messwert, als Koog
 * gefüllt). Seeseitig heißt: vom offenen Meer her über Zellen unter NHN +3,5 m erreichbar.
 * <p>
 * Höhen liegen als 16-Bit-Werte vor ({@link #HMIN} bis {@link #HMAX}, 1 mm Stufe); dieselben Werte gehen als R16-Textur
 * auf die Grafikkarte, darum sehen Kamera und Bild dieselbe Fläche (bilinear).
 */
public final class Terrain implements com.dan.rungholt.wiring.Terrain {
    public static final int N = 2048;
    public static final double CELL = 8;
    /** Lage der Nordwestecke des Rasters in der Szene; Zelle (i, j) hat ihre Mitte bei X0 + (i + 0,5) · CELL. */
    public static final double X0 = -10_000, Z0 = -8_200;
    public static final double HMIN = -40, HMAX = 25;
    /** Art je Zelle. */
    public static final byte WATT = 0, VORLAND = 1, ERHOEHT = 2, KOOG = 3, RAND = 4;
    /** Seit Phase 10, nur im Gelände von 1362 ({@link Land1362}): Marsch, Warft, Seedeich, Sielzug (Wasser im Binnenland). */
    public static final byte MARSCH = 5, WARFT = 6, DEICH1362 = 7, SIELZUG = 8;
    public static final String[] ART = {"Watt", "Vorland", "Deich/Warft", "Koog", "ohne Messwert", "Marsch", "Warft", "Seedeich", "Sielzug"};
    /** Ab dieser Höhe zählt eine Zelle als Deich oder Warft; die Flutung vom Meer her geht nur darunter. */
    public static final double DEICHFUSS = 3.5;

    /** Höhen als vorzeichenlose 16-Bit-Werte, Zeile für Zeile von Nord nach Süd. */
    public final short[] h;
    public final byte[] art;
    public final String source;
    public final Properties props;
    private final float[][] minPyr = new float[LEVELS][], maxPyr = new float[LEVELS][];
    private final int[] side = new int[LEVELS];
    private static final int LEVELS = 12;

    public Terrain(short[] h, byte[] art, Properties props) {
        this.h = h;
        this.art = art;
        this.props = props;
        this.source = props.getProperty("quelle", "Bühne von Phase 1 (keine Höhendaten)");
        pyramid();
    }

    /** Ob echte Höhendaten geladen sind (sonst die Bühne von Phase 1). */
    public boolean measured() { return props.containsKey("quelle"); }

    // ------------------------------------------------------------------ Abfragen

    public static short encode(double y) {
        double v = (y - HMIN) / (HMAX - HMIN) * 65535.0;
        return (short) (int) Math.max(0, Math.min(65535, Math.round(v)));
    }

    public static double decode(short s) { return HMIN + (s & 0xFFFF) / 65535.0 * (HMAX - HMIN); }

    public double at(int i, int j) { return decode(h[clampIndex(i, j)]); }

    static int clampIndex(int i, int j) {
        i = Math.max(0, Math.min(N - 1, i));
        j = Math.max(0, Math.min(N - 1, j));
        return j * N + i;
    }

    /** Mitte der Zelle i (x) bzw. j (z). */
    public static double cx(int i) { return X0 + (i + 0.5) * CELL; }
    public static double cz(int j) { return Z0 + (j + 0.5) * CELL; }

    /** Art am Ort; außerhalb des Rasters Watt (offenes Meer). */
    public byte artAt(double x, double z) {
        int i = (int) Math.floor((x - X0) / CELL), j = (int) Math.floor((z - Z0) / CELL);
        if (i < 0 || j < 0 || i >= N || j >= N) return WATT;
        return art[j * N + i];
    }

    /** Ob am Ort das Meer stehen kann (nicht hinter einem Deich). */
    public boolean seaward(double x, double z) { byte a = artAt(x, z); return a == WATT || a == VORLAND; }

    /** Ob die Zelle Land ist, auf dem Gras wächst (Vorland, Deich, Warft, Koog, Marsch). */
    public static boolean grass(byte a) { return a != WATT && a != RAND && a != SIELZUG; }

    /** Oberfläche an (x, z), bilinear zwischen den Zellmitten wie die Textur; außerhalb des Rasters der Rand. */
    @Override
    public double height(double x, double z) {
        double u = (x - X0) / CELL - 0.5, v = (z - Z0) / CELL - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        double p = at(i, j), q = at(i + 1, j), r = at(i, j + 1), s = at(i + 1, j + 1);
        return (p * (1 - fx) + q * fx) * (1 - fz) + (r * (1 - fx) + s * fx) * fz;
    }

    private volatile float[] smooth4;

    /** Phase 12: nach einer Änderung von {@link #h} (Bresche) die Hilfsstufen neu rechnen. */
    public void refresh() {
        smooth4 = null;
        pyramid();
    }

    /**
     * Die geglättete Höhe (Phase 6): wie die Mip-Stufe 2 der Höhentextur (Mittel aus 4 × 4 Zellen, 32 m), bilinear
     * zwischen deren Mitten. Für die Feinform des Watts (com.dan.mudflat.Relief) auf dem Prozessor.
     */
    public double smoothHeight(double x, double z) {
        float[] s = smooth4;
        int m = N / 4;
        if (s == null) {
            s = new float[m * m];
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    double sum = 0;
                    for (int b = 0; b < 4; b++) for (int a = 0; a < 4; a++) sum += at(i * 4 + a, j * 4 + b);
                    s[j * m + i] = (float) (sum / 16);
                }
            smooth4 = s;
        }
        double c = CELL * 4, u = (x - X0) / c - 0.5, v = (z - Z0) / c - 0.5;
        int i = (int) Math.floor(u), j = (int) Math.floor(v);
        double fx = u - i, fz = v - j;
        int i0 = Math.max(0, Math.min(m - 1, i)), i1 = Math.max(0, Math.min(m - 1, i + 1)), j0 = Math.max(0, Math.min(m - 1, j)), j1 = Math.max(0, Math.min(m - 1, j + 1));
        return (s[j0 * m + i0] * (1 - fx) + s[j0 * m + i1] * fx) * (1 - fz) + (s[j1 * m + i0] * (1 - fx) + s[j1 * m + i1] * fx) * fz;
    }

    /** Wassertiefe am Ort bei NHN 0 (m, 0 = trocken oder hinter dem Deich); den Pegel kennt erst die Tide. */
    @Override
    public double waterDepth(double x, double z) { return waterDepth(x, z, 0); }

    /** Wassertiefe beim Pegel {@code level} (m über NHN). */
    public double waterDepth(double x, double z, double level) {
        if (!seaward(x, z)) return 0;
        return Math.max(0, level - height(x, z));
    }

    /** Kleinste und größte Höhe im Quadrat (x0, z0, Kante) aus der Min-Max-Pyramide (für das Ausblenden von Knoten). */
    public void range(double x0, double z0, double size, double[] out) {
        int i0 = (int) Math.floor((x0 - X0) / CELL) - 1, j0 = (int) Math.floor((z0 - Z0) / CELL) - 1;
        int i1 = (int) Math.ceil((x0 + size - X0) / CELL) + 1, j1 = (int) Math.ceil((z0 + size - Z0) / CELL) + 1;
        i0 = Math.max(0, Math.min(N - 1, i0)); j0 = Math.max(0, Math.min(N - 1, j0));
        i1 = Math.max(0, Math.min(N - 1, i1)); j1 = Math.max(0, Math.min(N - 1, j1));
        int span = Math.max(i1 - i0, j1 - j0) + 1, k = 0;
        while (k < LEVELS - 1 && (1 << (k + 1)) * 2 <= span) k++;
        int s = side[k];
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (int j = j0 >> k; j <= j1 >> k; j++)
            for (int i = i0 >> k; i <= i1 >> k; i++) {
                int ii = Math.min(s - 1, i), jj = Math.min(s - 1, j);
                lo = Math.min(lo, minPyr[k][jj * s + ii]);
                hi = Math.max(hi, maxPyr[k][jj * s + ii]);
            }
        out[0] = lo;
        out[1] = hi;
    }

    private void pyramid() {
        float[] base = new float[N * N];
        for (int i = 0; i < base.length; i++) base[i] = (float) decode(h[i]);
        minPyr[0] = base;
        maxPyr[0] = base;
        side[0] = N;
        int n = N;
        for (int k = 1; k < LEVELS; k++) {
            int m = (n + 1) / 2;
            float[] mn = new float[m * m], mx = new float[m * m], pmn = minPyr[k - 1], pmx = maxPyr[k - 1];
            for (int j = 0; j < m; j++)
                for (int i = 0; i < m; i++) {
                    float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                    for (int b = 0; b < 2; b++)
                        for (int a = 0; a < 2; a++) {
                            int ii = Math.min(n - 1, 2 * i + a), jj = Math.min(n - 1, 2 * j + b);
                            lo = Math.min(lo, pmn[jj * n + ii]);
                            hi = Math.max(hi, pmx[jj * n + ii]);
                        }
                    mn[j * m + i] = lo;
                    mx[j * m + i] = hi;
                }
            minPyr[k] = mn;
            maxPyr[k] = mx;
            side[k] = m;
            n = m;
        }
    }

    /** Anteil der Zellen je Art. */
    public double[] shares() {
        long[] c = new long[ART.length];
        for (byte b : art) c[Math.max(0, Math.min(ART.length - 1, b))]++;
        double[] r = new double[c.length];
        for (int k = 0; k < c.length; k++) r[k] = c[k] / (double) art.length;
        return r;
    }

    /** Fläche (km²) der seeseitigen Zellen zwischen zwei Höhen: etwa das Watt, das eine Tide frei legt. */
    public double areaBetween(double lo, double hi) {
        long n = 0;
        for (int k = 0; k < h.length; k++) {
            if (art[k] != WATT && art[k] != VORLAND) continue;
            double y = decode(h[k]);
            if (y >= lo && y < hi) n++;
        }
        return n * CELL * CELL / 1e6;
    }

    /** „BAW topography_max_z 2015–2021 · Watt 71 %, Vorland 1 %, Deich/Warft 1 %, Koog 20 %, ohne Messwert 7 %“ */
    public String describe() {
        double[] s = shares();
        StringBuilder b = new StringBuilder(source);
        for (int k = 0; k < s.length; k++) b.append(k == 0 ? " · " : ", ").append(ART[k]).append(String.format(Locale.GERMAN, " %.0f %%", s[k] * 100));
        return b.toString();
    }

    // ------------------------------------------------------------------ Dateien

    public static final String HOEHE = "hoehe.r16", ARTEN = "art.u8", INFO = "gelaende.properties";

    /** Liest {@code daten/gelaende} (oder die Bühne, wenn es fehlt). */
    public static Terrain load(File dir) {
        File hf = new File(dir, HOEHE), af = new File(dir, ARTEN), pf = new File(dir, INFO);
        if (!hf.isFile() || !af.isFile() || !pf.isFile()) return flat();
        try {
            Properties p = new Properties();
            try (InputStream in = new FileInputStream(pf)) { p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)); }
            short[] h = readShorts(hf);
            byte[] a = Files.readAllBytes(af.toPath());
            if (a.length != N * N) throw new IOException(af + ": falsche Größe");
            return new Terrain(h, a, p);
        } catch (IOException | RuntimeException e) {
            System.err.println("Gelände: " + e.getMessage() + " – es bleibt die Bühne von Phase 1");
            return flat();
        }
    }

    private static short[] readShorts(File f) throws IOException {
        byte[] raw = Files.readAllBytes(f.toPath());
        if (raw.length != N * N * 2) throw new IOException(f + ": " + raw.length + " Bytes statt " + N * N * 2);
        short[] s = new short[N * N];
        ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(s);
        return s;
    }

    /** Ordner {@code daten/gelaende} im Arbeitsverzeichnis ({@code -Drungholt.daten=…} setzt ihn anders). */
    public static File defaultDir() {
        String d = System.getProperty("rungholt.daten");
        return new File(d != null ? d : "daten", "gelaende");
    }

    public void save(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("Ordner " + dir + " lässt sich nicht anlegen");
        ByteBuffer bb = ByteBuffer.allocate(N * N * 2).order(ByteOrder.LITTLE_ENDIAN);
        bb.asShortBuffer().put(h);
        Files.write(new File(dir, HOEHE).toPath(), bb.array());
        Files.write(new File(dir, ARTEN).toPath(), art);
        try (OutputStream out = new java.io.FileOutputStream(new File(dir, INFO))) {
            props.store(new java.io.OutputStreamWriter(out, java.nio.charset.StandardCharsets.UTF_8), "Gelaende der Szene Rungholt (tools.GelaendeImport)");
        }
    }

    /** Das Gelände aus der Bühne von Phase 1 ({@link Buehne}), wenn {@code daten/gelaende} fehlt. */
    public static Terrain flat() {
        short[] h = new short[N * N];
        double[] y = new double[N * N];
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                y[j * N + i] = Buehne.height(cx(i), cz(j));
                h[j * N + i] = encode(y[j * N + i]);
            }
        byte[] a = classify(y, null);
        return new Terrain(h, a, new Properties());
    }

    /**
     * Ordnet jede Zelle einer Art zu: Flutung vom Rand des Rasters (nur von Zellen unter dem mittleren Niedrigwasser,
     * also offenem Meer; ein Koog, den der Rand anschneidet, bleibt Koog) über alle Zellen unter {@link #DEICHFUSS}; was sie
     * erreicht, ist seeseitig (Watt oder Vorland nach {@link Site#MTHW} + 0,3 m), der Rest Deich/Warft (hoch) oder
     * Koog (dahinter). {@code missing} markiert Zellen ohne Messwert (RAND), oder null.
     */
    public static byte[] classify(double[] y, boolean[] missing) {
        byte[] a = new byte[N * N];
        boolean[] sea = new boolean[N * N];
        int[] queue = new int[N * N];
        int head = 0, tail = 0;
        for (int k = 0; k < N; k++)
            for (int e : new int[]{k, (N - 1) * N + k, k * N, k * N + N - 1})
                if (!sea[e] && y[e] < Site.MTNW && (missing == null || !missing[e])) { sea[e] = true; queue[tail++] = e; }
        while (head < tail) {
            int c = queue[head++], i = c % N, j = c / N;
            int[] nb = {i > 0 ? c - 1 : -1, i < N - 1 ? c + 1 : -1, j > 0 ? c - N : -1, j < N - 1 ? c + N : -1};
            for (int n : nb) {
                if (n < 0 || sea[n] || y[n] >= DEICHFUSS || (missing != null && missing[n])) continue;
                sea[n] = true;
                queue[tail++] = n;
            }
        }
        for (int k = 0; k < a.length; k++) {
            if (missing != null && missing[k]) a[k] = RAND;
            else if (sea[k]) a[k] = y[k] < Site.MTHW + 0.3 ? WATT : VORLAND;
            else a[k] = y[k] >= DEICHFUSS ? ERHOEHT : KOOG;
        }
        return a;
    }
}
