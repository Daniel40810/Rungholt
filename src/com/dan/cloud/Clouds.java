package com.dan.cloud;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Die Wolken als Zahlen: eine untere Schicht einer {@link CloudType Wolkenart} mit Bedeckung in Achteln, darüber auf
 * Wunsch eine hohe Schicht (Altostratus/Cirrostratus als dünner Schleier), seit Glacier Phase 3 Lentikularis als
 * ortsfeste Linsen einer Leewelle ({@link #lens}), Drift mit dem Wind in Wolkenhöhe und
 * langsamer Formwandel. Die Dichte an jedem Punkt, der Schatten am Boden, die Bedeckung und das Regenfeld lassen sich
 * ohne Grafikkarte fragen; {@code gl/CloudGl} zeichnet mit denselben Formeln und denselben Rauschtexturen.
 * <p>
 * Raum wie in der Szene: x Ost, y oben, z Süd, Meter, Ursprung auf Meereshöhe; die Höhe zählt über der gekrümmten
 * Erde (Radius {@link #EARTH_R}), damit die Wolkendecke zur Kimm hin abtaucht.
 * <p>
 * Die Bedeckung ist geeicht: Für jede Art wird aus der Wetterkarte die Schwelle bestimmt, über der genau
 * {@code oktas/8} der Fläche bewölkt ist (wie die Schaumbedeckung im Meer).
 */
public final class Clouds {
    public static final double EARTH_R = 6_360_000;
    /** Weiche Kante der Bedeckung auf der Wetterkarte. */
    public static final double SOFT = 0.12;
    /** Haufen: Dichte im Kern der Zelle (darunter frisst die Grundform Lücken und Türmchen heraus). */
    static final double CU_CORE = 0.65;
    /** Reichweite des Ambosses über den Turm hinaus, in Kacheln der Wetterkarte (gut eine Drittelzelle). */
    static final double ANVIL_R = 0.35 / 16;
    /** Hohe Schicht: Höhe, Kachelgröße der Karte, Dicke (optisch) bei voller Bedeckung. */
    public static final double HIGH_HEIGHT = 6500, HIGH_TILE = 60_000, HIGH_TAU = 0.6;
    /**
     * Seit Glacier Phase 3: Lentikularis (Altocumulus lenticularis) im Lee eines Gebirges – glatte Linsen auf den Kämmen
     * einer stehenden Leewelle, oft als Stapel. Untere Höhe der Linsen, Abstand der Lagen, Wellenlänge längs des Winds,
     * Länge der Rauschzellen quer dazu (m), optische Dicke im Kern, Zahl der Lagen [G]. Die Linsen ziehen nicht mit dem
     * Wind: sie stehen über dem Gebirge, die Luft strömt durch sie hindurch.
     */
    public static final double LENS_HEIGHT = 4600, LENS_STEP = 320, LENS_WAVE = 9000, LENS_CELL = 12_000, LENS_TAU = 3.0;
    public static final int LENS_SLICES = 3;
    /** Schatten werden durch Mehrfachstreuung weicher: τ wirkt nur zu diesem Anteil. */
    public static final double SHADOW_SOFT = 0.6;

    /** Rechenstufen: Teiler der Auflösung, Schritte im Zenit und an der Kimm, Schritte zum Licht. FLAT = nur 2.5D. */
    public enum Quality {
        FLAT(2, 0, 0, 0), MEDIUM(4, 72, 100, 5), HIGH(2, 96, 70, 6), ULTRA(2, 128, 45, 6);
        /** Teiler der Auflösung, höchste Schrittzahl, Schrittweite nah am Auge in m (wächst mit der Entfernung), Schritte zum Licht. */
        public final int divisor, maxSteps, stepNear, lightSteps;
        Quality(int divisor, int ms, int sn, int ls) { this.divisor = divisor; maxSteps = ms; stepNear = sn; lightSteps = ls; }
    }

    // ------------------------------------------------------------------ Eingänge

    /** Bedeckung der unteren Schicht in Achteln (0..8). */
    public volatile double oktas = 3;
    public volatile CloudType type = CloudType.CUMULUS;
    /** Hohe Schicht 0..1 (0 = keine). */
    public volatile double highCover;
    /** Niederschlag 0..1: Regenvorhänge unter den dichten Stellen (für {@code rain}). */
    public volatile double precipitation;
    /** Wind in Wolkenhöhe (m/s) und Richtung, aus der er kommt (Grad). */
    public volatile double windSpeed = 10, windFromDeg = 250;
    /** Formwandel: 1 = Haufen wachsen und vergehen in rund 10 Minuten. */
    public volatile double evolve = 1;
    public volatile Quality quality = Quality.HIGH;
    /** Seit Glacier Phase 3: Lentikularis 0..1 (0 = keine) und die Richtung der Strömung, aus der die Leewelle kommt (Grad). */
    public volatile double lens, lensFromDeg = 270;
    /** Wie weit gerechnet wird (m); dahinter verschwinden die Wolken im Dunst. */
    public volatile double maxDistance = 60_000;

    private double offX, offZ, time;
    private final Map<CloudType, double[]> quantiles = new EnumMap<>(CloudType.class);
    private double[] highQuantile;

    public Clouds() { }

    /** Wolken einer Art mit einer Bedeckung in Achteln. */
    public static Clouds of(CloudType type, double oktas) {
        Clouds c = new Clouds();
        c.type = type;
        c.oktas = oktas;
        return c;
    }

    /** Schönwetter: 3/8 Cumulus. */
    public static Clouds fair() { return of(CloudType.CUMULUS, 3); }

    /** Grau verhangen: 8/8 Stratocumulus. */
    public static Clouds overcast() { return of(CloudType.STRATOCUMULUS, 8); }

    /** Gewitter: 6/8 Cumulonimbus mit hohem Schirm und Regen. */
    public static Clouds storm() {
        Clouds c = of(CloudType.CUMULONIMBUS, 6);
        c.highCover = 0.5;
        c.precipitation = 0.8;
        c.windSpeed = 22;
        return c;
    }

    /** Zieht die Wolken mit dem Wind weiter und lässt sie wandeln. */
    public synchronized void step(double dt) {
        double a = Math.toRadians(windFromDeg + 180);
        offX -= Math.sin(a) * windSpeed * dt;
        offZ -= -Math.cos(a) * windSpeed * dt;
        time += dt;
    }

    /** Laufzeit setzen (Standbilder), die Drift folgt aus Wind und Zeit. */
    public synchronized void setTime(double t) {
        double a = Math.toRadians(windFromDeg + 180);
        offX = -Math.sin(a) * windSpeed * t;
        offZ = Math.cos(a) * windSpeed * t;
        time = t;
    }

    // ------------------------------------------------------------------ Eichung

    /** Feld der Wetterkarte für eine Art (Mischung aus Zellen und Flecken). */
    static double field(CloudType t, double[] w) { return w[0] + (w[1] - w[0]) * t.strat; }

    private synchronized double[] quantile(CloudType t) {
        return quantiles.computeIfAbsent(t, k -> {
            byte[] m = Noise.weather();
            int n = Noise.WEATHER * Noise.WEATHER;
            double[] v = new double[n];
            for (int i = 0; i < n; i++) v[i] = (m[i * 4] & 255) / 255.0 + ((m[i * 4 + 1] & 255) - (m[i * 4] & 255)) / 255.0 * k.strat;
            Arrays.sort(v);
            return v;
        });
    }

    private synchronized double[] highQuantile() {
        if (highQuantile == null) {
            byte[] m = Noise.weather();
            int n = Noise.WEATHER * Noise.WEATHER;
            double[] v = new double[n];
            for (int i = 0; i < n; i++) v[i] = (m[i * 4 + 3] & 255) / 255.0;
            Arrays.sort(v);
            highQuantile = v;
        }
        return highQuantile;
    }

    static double thresholdFor(double[] sorted, double fraction) {
        if (fraction <= 0) return 2;
        if (fraction >= 1) return -1;
        return sorted[(int) Math.min(sorted.length - 1, Math.round((1 - fraction) * sorted.length))] - SOFT / 2;
    }

    /** Schwelle der unteren Schicht für die eingestellte Bedeckung. */
    public double threshold() { return thresholdFor(quantile(type), areaFraction(type, oktas)); }

    /**
     * Welcher Flächenanteil auf der Karte bewölkt sein muss, damit man von unten {@code oktas/8} des Himmels bedeckt
     * sieht. Die Grundform frisst Lücken in die Zellen (lockere Haufen brauchen mehr Fläche), hohe Türme verdecken
     * schräg mehr Himmel, als sie Fläche haben; die Tabelle ist mit
     * {@link #observedCoverage} gemessen (256 Standorte gleichmäßig über die Kachel, Richtungen bis 70° vom Zenit) und wird in der Prüfung
     * nachgemessen.
     */
    public static double areaFraction(CloudType t, double oktas) {
        double[] tab = AREA.get(t);
        double o = Math.max(0, Math.min(8, oktas));
        int i = Math.min(7, (int) o);
        return tab[i] + (tab[i + 1] - tab[i]) * (o - i);
    }

    static final Map<CloudType, double[]> AREA = new EnumMap<>(CloudType.class);
    static {
        AREA.put(CloudType.CUMULUS, new double[]{0, 0.217, 0.412, 0.574, 0.725, 0.795, 0.813, 0.883, 1});
        AREA.put(CloudType.STRATOCUMULUS, new double[]{0, 0.205, 0.377, 0.529, 0.660, 0.762, 0.841, 0.923, 1});
        AREA.put(CloudType.STRATUS, new double[]{0, 0.121, 0.260, 0.396, 0.515, 0.632, 0.757, 0.882, 1});
        AREA.put(CloudType.NIMBOSTRATUS, new double[]{0, 0.123, 0.242, 0.368, 0.504, 0.620, 0.742, 0.863, 1});
        AREA.put(CloudType.CUMULONIMBUS, new double[]{0, 0.026, 0.060, 0.100, 0.149, 0.215, 0.312, 0.469, 1});
    }

    /** Schwelle der hohen Schicht. */
    public double highThreshold() { return thresholdFor(highQuantile(), Math.max(0, Math.min(1, highCover))); }

    // ------------------------------------------------------------------ Zustand

    /** Ein fester Stand für Abfragen und die Grafikkarte. */
    public record Snapshot(CloudType type, double oktas, double threshold, double highCover, double highThreshold, double offX,
                           double offZ, double time, double precipitation, double evolve, Quality quality, double maxDistance,
                           double windSpeed, double windFromDeg, double lens, double lensFromDeg) {
        public double tile() { return type.cell * 16; }
        /** Kachel der Grundform: Haufen und Türme mit Blumenkohl von etwa 1 km, Schichten mit der Zelle wachsend. */
        public double noiseTile() { return 4000 + (type.cell * 2.2 - 4000) * type.strat * type.strat; }
        public double detailTile() { return noiseTile() * 0.115; }
        /** Dichte im Kern einer Haufenzelle: bei wenig Bedeckung locker, gegen 8/8 geschlossen. */
        public double core() { return CU_CORE + (1 - CU_CORE) * smooth(4, 8, oktas); }
    }

    public synchronized Snapshot snapshot() {
        return new Snapshot(type, oktas, threshold(), highCover, highThreshold(), offX, offZ, time, precipitation, evolve, quality,
                maxDistance, windSpeed, windFromDeg, Math.max(0, Math.min(1, lens)), lensFromDeg);
    }

    // ------------------------------------------------------------------ Dichte (wie cloud_common.glsl)

    public static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double remap(double v, double lo, double hi, double a, double b) {
        return a + (v - lo) / Math.max(1e-6, hi - lo) * (b - a);
    }

    /** Höhe über der gekrümmten Erde für einen Szenenpunkt. */
    public static double height(double x, double y, double z) {
        double yy = y + EARTH_R;
        return Math.sqrt(x * x + yy * yy + z * z) - EARTH_R;
    }

    /** Bedeckung der Wetterkarte an (x, z) ohne Höhe, 0..1. */
    public static double coverage(Snapshot s, double x, double z) {
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, (x + s.offX()) / s.tile(), (z + s.offZ()) / s.tile(), w);
        double f = field(s.type(), w);
        return smooth(s.threshold(), s.threshold() + SOFT, f);
    }

    /**
     * Auslöschung je m am Szenenpunkt (x, y, z). detail = false lässt das feine Rauschen weg (Lichtweg, Schatten).
     */
    public static double density(Snapshot s, double x, double y, double z, boolean detail) {
        CloudType t = s.type();
        double h = height(x, y, z);
        if (h < t.base || h > t.base + t.maxThickness) return 0;
        double wx = x + s.offX(), wz = z + s.offZ();
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, wx / s.tile(), wz / s.tile(), w);
        double f = field(t, w), thr = s.threshold();
        double tall = smooth(thr, thr + 0.15 + 0.2 * t.strat - 0.07 * t.anvil, f);
        double top = t.base + t.thickness + (t.maxThickness - t.thickness) * tall * tall;
        double tt = (h - t.base) / (top - t.base);
        // Haufen werden nach oben schmaler (Kuppe statt Säule)
        double tc = Math.max(0, Math.min(1, tt)), rise = (1 - t.strat) * (0.2 - 0.12 * t.anvil) * tc * tc;
        double cov = smooth(thr + rise, thr + rise + SOFT, f);
        double anv = 0;
        if (t.anvil > 0) {
            // Amboss nur über starken Türmen, ein Drittel einer Zelle über sie hinaus
            double ta = (h - t.base) / t.maxThickness;
            double band = smooth(0.8, 0.88, ta) * (1 - smooth(0.94, 0.99, ta));
            if (band > 0) {
                double u = wx / s.tile(), v = wz / s.tile(), fn = f;
                double[] q = new double[4];
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    Noise.sample2(Noise.weather(), Noise.WEATHER, u + ANVIL_R * (float) Math.cos(a), v + ANVIL_R * (float) Math.sin(a), q);
                    fn = Math.max(fn, field(t, q));
                }
                anv = t.anvil * band * smooth(thr + 0.06, thr + 0.14, fn);
            }
        }
        double prof = 0;
        if (tt <= 1) {
            double cu = smooth(0, 0.06, tt) * (1 - smooth(0.45 + 0.35 * t.anvil, 1, tt)), st = smooth(0, 0.18, tt) * (1 - smooth(0.65, 1, tt));
            prof = cu + (st - cu) * t.strat;
        }
        double core = s.core(), fill = Math.max(t.strat, t.anvil), cc = cov * prof * (core + (1 - core) * fill), c = Math.max(cc, anv);
        double sheet = Math.max(t.strat, smooth(0, 0.1, anv - cc));   // Schicht oder Amboss: glatt
        if (c <= 0) return 0;
        double ns = s.noiseTile();
        double[] b = new double[4];
        Noise.sample3(Noise.base(), Noise.BASE, wx / ns, (h - s.time() * 0.6 * s.evolve()) / ns, wz / ns, b);
        double low = b[1] * 0.625 + b[2] * 0.25 + b[3] * 0.125;
        double spread = Math.max(0, Math.min(1, (b[0] * 0.7 + low * 0.3 - 0.2) / 0.6)), packed = remap(b[0], low - 1, 1, 0, 1);
        double base = spread + (packed - spread) * sheet;
        base = remap(base, 1 - c, 1, 0, 1) * c;
        if (base <= 0) return 0;
        if (detail) {
            double ds = s.detailTile();
            double[] d = new double[4];
            Noise.sample3(Noise.detail(), Noise.DETAIL, wx / ds, (h - s.time() * 1.5 * s.evolve()) / ds, wz / ds, d);
            double hf = d[0] * 0.625 + d[1] * 0.25 + d[2] * 0.125;
            double mod = hf + (1 - 2 * hf) * Math.max(0, Math.min(1, tt * 4));
            base = remap(base, mod * (0.55 - 0.25 * sheet), 1, 0, 1);
        }
        return Math.max(0, base) * t.sigma;
    }

    /** Optische Dicke der hohen Schicht senkrecht an (x, z). */
    public static double highTau(Snapshot s, double x, double z) {
        if (s.highCover() <= 0) return 0;
        double[] w = new double[4];
        double qx = x + s.offX() * 1.5, qz = z + s.offZ() * 1.5;
        double[] f = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, qx / (HIGH_TILE * 3), qz / HIGH_TILE, w);
        Noise.sample2(Noise.weather(), Noise.WEATHER, qx / (HIGH_TILE * 2), qz / (HIGH_TILE * 0.12), f);
        return HIGH_TAU * smooth(s.highThreshold(), s.highThreshold() + 0.2, w[3]) * (0.35 + 0.65 * f[2]);
    }

    /**
     * Seit Glacier Phase 3: optische Dicke der Linsenlage {@code slice} (0 unten) senkrecht an (x, z). Längs des Winds
     * die Kämme der stehenden Welle (Kosinus, nur die Spitzen), quer dazu Abschnitte aus einem Rauschen je Kamm; obere Lagen sind kleiner (Stapel). Ortsfest, ohne Drift. Wie lensTau in cloud_common.glsl.
     */
    public static double lensTau(Snapshot s, double x, double z, int slice) {
        if (s.lens() <= 0) return 0;
        double a = Math.toRadians(s.lensFromDeg()), tx = -Math.sin(a), tz = Math.cos(a);
        double u = x * tx + z * tz, v = -x * tz + z * tx;
        double cu = 0.5 + 0.5 * Math.cos(2 * Math.PI * u / LENS_WAVE);
        if (cu < 0.68) return 0;
        int k = (int) Math.floor(u / LENS_WAVE + 0.5);
        double q = v / LENS_CELL;
        int j = (int) Math.floor(q);
        double f = q - j, n = lensHash(k, j) + (lensHash(k, j + 1) - lensHash(k, j)) * f * f * (3 - 2 * f);
        // Mandelform: wo das Rauschen hoch ist, ist die Linse breit und dick, zu den Enden läuft sie spitz aus
        double thr = 1 - 0.55 * s.lens() + 0.07 * slice;
        double b2 = Math.max(0, Math.min(1, (n - thr) / 0.4));
        if (b2 <= 0) return 0;
        double c0 = 1 - (0.30 - 0.06 * slice) * Math.sqrt(b2);
        double bu = Math.max(0, Math.min(1, (cu - c0) / Math.max(1e-6, 1 - c0)));
        return LENS_TAU * Math.sqrt(s.lens()) * Math.pow(bu, 0.6) * Math.sqrt(b2);
    }

    /** Ganzzahliges Rauschen 0..1 je Kamm k und Abschnitt j (bitgleich mit lensHash in cloud_common.glsl). */
    static double lensHash(int k, int j) {
        int h = k * 374761393 + j * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / 16777216.0;
    }

    /** Abstände t0, t1 entlang d (Einheitsvektor) vom Punkt o, in denen der Strahl zwischen den Höhen h0 und h1 läuft. */
    public static double[] shell(double[] o, double[] d, double h0, double h1) {
        double ox = o[0], oy = o[1] + EARTH_R, oz = o[2];
        double b = ox * d[0] + oy * d[1] + oz * d[2], oo = ox * ox + oy * oy + oz * oz;
        double r0 = EARTH_R + h0, r1 = EARTH_R + h1, r = Math.sqrt(oo);
        double disc1 = b * b - (oo - r1 * r1);
        if (disc1 < 0) return null;
        double far1 = -b + Math.sqrt(disc1), near1 = -b - Math.sqrt(disc1);
        double disc0 = b * b - (oo - r0 * r0);
        if (r < r0) {
            // unter der Schicht: vom inneren zum äußeren Rand
            return new double[]{-b + Math.sqrt(Math.max(0, disc0)), far1};
        }
        if (r <= r1) {
            // in der Schicht: bis zum inneren Rand (nach unten) oder zum äußeren
            if (disc0 > 0 && -b - Math.sqrt(disc0) > 0) return new double[]{0, -b - Math.sqrt(disc0)};
            return new double[]{0, far1};
        }
        // darüber
        if (near1 < 0) return null;
        if (disc0 > 0 && -b - Math.sqrt(disc0) > 0) return new double[]{near1, -b - Math.sqrt(disc0)};
        return new double[]{near1, far1};
    }

    /** Wie viel direktes Licht (0..1) an (x, 0, z) ankommt, aus Richtung l (zum Gestirn). 16 Schritte wie shadow.frag. */
    public static double shadow(Snapshot s, double x, double z, double[] l) {
        double ly = Math.max(0.05, l[1]);
        double n = Math.sqrt(l[0] * l[0] + ly * ly + l[2] * l[2]);
        double[] d = {l[0] / n, ly / n, l[2] / n};
        double[] o = {x, 0, z};
        double[] seg = shell(o, d, s.type().base, s.type().top());
        double tau = 0;
        if (seg != null) {
            double t0 = seg[0], t1 = Math.min(seg[1], seg[0] + 40_000), dt = (t1 - t0) / 16;
            for (int k = 0; k < 16; k++) {
                double t = t0 + (k + 0.5) * dt;
                tau += density(s, x + d[0] * t, d[1] * t, z + d[2] * t, false) * dt;
            }
        }
        double th = (HIGH_HEIGHT) / d[1];
        tau += highTau(s, x + d[0] * th, z + d[2] * th) / d[1];
        for (int i = 0; i < LENS_SLICES && s.lens() > 0; i++) {
            double tl = (LENS_HEIGHT + i * LENS_STEP) / d[1];
            tau += lensTau(s, x + d[0] * tl, z + d[2] * tl, i) / d[1];
        }
        return Math.exp(-tau * SHADOW_SOFT);
    }

    /** Regenvorhang 0..1 an (x, z) (für {@code rain}, Phase 7): Niederschlag unter dichten Stellen. */
    public static double rain(Snapshot s, double x, double z) {
        if (s.precipitation() <= 0) return 0;
        double[] w = new double[4];
        Noise.sample2(Noise.weather(), Noise.WEATHER, (x + s.offX()) / s.tile(), (z + s.offZ()) / s.tile(), w);
        double f = field(s.type(), w), thr = s.threshold();
        return s.precipitation() * smooth(thr + 0.1, thr + 0.35, f) * (0.6 + 0.4 * w[2]);
    }

    /**
     * Bedeckung, wie man sie von unten sieht: Anteil der Richtungen bis 70° vom Zenit, deren Strahl mehr als
     * optische Dicke 1 durch Wolken läuft. Für Prüfungen (die Eichung gilt für die Fläche).
     */
    public static double observedCoverage(Snapshot s0, double x, double z, int n) {
        // Standort über die Drift verschieben und am Ursprung messen: die Szene ist eine Tangentialebene, weit draußen
        // läge das Auge sonst schon Hunderte Meter über dem Meer
        Snapshot s = new Snapshot(s0.type(), s0.oktas(), s0.threshold(), s0.highCover(), s0.highThreshold(), s0.offX() + x,
                s0.offZ() + z, s0.time(), s0.precipitation(), s0.evolve(), s0.quality(), s0.maxDistance(), s0.windSpeed(), s0.windFromDeg(), s0.lens(), s0.lensFromDeg());
        int hit = 0, all = 0;
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double u = (i + 0.5) / n, v = (j + 0.5) / n;
                double cz = 1 - u * (1 - Math.cos(Math.toRadians(70))), sz = Math.sqrt(1 - cz * cz), a = v * 2 * Math.PI;
                double[] d = {sz * Math.sin(a), cz, -sz * Math.cos(a)};
                double[] seg = shell(new double[]{0, 10, 0}, d, s.type().base, s.type().top());
                double tau = 0;
                if (seg != null) {
                    double t0 = seg[0], t1 = Math.min(seg[1], seg[0] + 30_000), dt = (t1 - t0) / 48;
                    for (int k = 0; k < 48 && tau < 1; k++) {
                        double t = t0 + (k + 0.5) * dt;
                        tau += density(s, d[0] * t, 10 + d[1] * t, d[2] * t, true) * dt;
                    }
                }
                if (tau >= 1) hit++;
                all++;
            }
        return hit / (double) all;
    }

    // ------------------------------------------------------------------ für die Szene

    /** Faktor für die Anpassungshelligkeit des Auges: ein bedeckter Himmel ist dunkler als der klare. */
    public double brightnessFactor() {
        double low = Math.pow(Math.max(0, Math.min(8, oktas)) / 8, 1.3) * (1 - type.overcastBrightness);
        double high = Math.max(0, Math.min(1, highCover)) * 0.25;
        return Math.max(0.05, (1 - low) * (1 - high));
    }

    /** „Cumulus 3/8 · Basis 900 m · Gipfel bis 2 500 m · Wind 10 m/s aus 250°“ */
    public String describe() {
        CloudType t = type;
        String s = String.format(Locale.GERMAN, "%s %.0f/8 · Basis %.0f m · Gipfel bis %.0f m · Zug %.0f m/s aus %03.0f°", t.label,
                oktas, t.base, t.top(), windSpeed, windFromDeg);
        if (highCover > 0) s += String.format(Locale.GERMAN, " · hoher Schleier %.0f %%", highCover * 100);
        if (precipitation > 0) s += String.format(Locale.GERMAN, " · Niederschlag %.0f %%", precipitation * 100);
        if (lens > 0.02) s += String.format(Locale.GERMAN, " · Lentikularis %.0f %%", lens * 100);
        return s;
    }
}
