package com.dan.ocean;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Meer und Wellen, ohne Grafikkarte: Seegang nach Beaufort, Windsee als FFT-Spektrum in Kaskaden, Dünung als
 * Gerstner-Wellen, Ringwellen nach Einschlägen, Brandungsringe um Hindernisse. Das Package {@code .gl} zeichnet
 * genau diese See; hier liegen die Zahlen und die Abfragen für alles, was schwimmt, spritzt oder fliegt.
 * <p>
 * Koordinaten: x Ost, y oben, z Süd, Meter. Richtungen wie auf der Seekarte in Grad von Nord im Uhrzeigersinn,
 * Wind und Dünung als Richtung, aus der sie kommen. Alle Felder sind öffentlich und zur Laufzeit änderbar; ändert
 * sich etwas am Spektrum, wird es im Hintergrund neu gebaut und ohne Ruck übernommen.
 */
public final class Ocean {
    /** Qualitätsstufen: FFT-Größe und Zahl der Kaskaden. */
    public enum Quality {
        LOW(128, 3), MEDIUM(256, 3), HIGH(256, 4), ULTRA(512, 4);
        public final int n, cascades;
        Quality(int n, int cascades) { this.n = n; this.cascades = cascades; }
    }

    // ------------------------------------------------------------------ Eingänge (Paketvertrag Punkt 5)
    /** Wind in m/s (10 m über dem Wasser) und Richtung, aus der er kommt. */
    public volatile double windSpeed = SeaState.MID[4], windFromDeg = 240;
    /** Streichlänge in m: wie weit der Wind schon über Wasser weht (Deutsche Bucht: rund 150 km). */
    public volatile double fetch = 150_000;
    /** Wassertiefe in m für die Dispersion; groß = Tiefwasser. */
    public volatile double depth = 10_000;
    /** Horizontale Zuspitzung der Kämme (0 = Sinus, 1 = sehr spitz). */
    public volatile double choppiness = 0.85;
    /** H_s der Windsee in m statt aus der Beaufort-Tabelle (NaN = Tabelle). */
    public volatile double heightOverride = Double.NaN;
    /**
     * Seit Haithabu (Phase 2): H_s aus Wind und Streichlänge (JONSWAP, {@link SeaState#fetchLimitedHeight}) statt aus
     * der Tabelle, höchstens so hoch wie die Tabelle. Für Binnengewässer und Buchten, wo die See nicht ausreift.
     */
    public volatile boolean fetchLimited;
    /** Dünung: signifikante Höhe in m, Periode in s, Richtung, aus der sie kommt. */
    public volatile double swellHeight = 0.6, swellPeriod = 9.5, swellFromDeg = 290;
    /** Wasserstand (Gezeiten) in m. */
    public volatile double waterLevel = 0;
    /** Nachleuchten des Schaums in s und Schaummenge (Faktor auf die Tabelle). */
    public volatile double foamDecay = 2.5, foamAmount = 1;
    public volatile long seed = 1;
    public volatile Quality quality = Quality.HIGH;

    private final List<Obstacle> obstacles = new CopyOnWriteArrayList<>();
    private final Ripples ripples = new Ripples();
    private volatile State state;
    private volatile double time;
    private int nextVersion = 1;
    private final ExecutorService builder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Ozean-Spektrum");
        t.setDaemon(true);
        return t;
    });
    private Future<State> pending;
    private Params pendingParams;

    public Ocean() { state = build(params(), nextVersion++); }

    // ------------------------------------------------------------------ Presets
    public static Ocean calm() { return beaufort(1); }
    public static Ocean breeze() { return beaufort(4); }
    public static Ocean gale() { return beaufort(8); }
    public static Ocean storm() { return beaufort(10); }

    /**
     * Seit Haithabu (Phase 2): ein flaches, kleines Binnengewässer wie das Haddebyer Noor. Streichlänge 1,5 km, Tiefe
     * 3 m, Wellenhöhe aus der Streichlänge, keine Dünung, weniger Schaum, weichere Kämme. Wind wie bei {@link #beaufort}.
     */
    public static Ocean noor(int bft) {
        Ocean o = beaufort(bft);
        o.fetch = 1_500;
        o.depth = 3;
        o.fetchLimited = true;
        o.swellHeight = 0;
        o.choppiness = 0.55;
        o.foamAmount = 0.35;
        o.ringHsLow = 0.03;
        o.ringHsHigh = 0.35;
        o.rebuildNow();
        return o;
    }

    /**
     * Seit Glacier (Phase 2): ein großer, tiefer Gletschersee wie der Lago Argentino vor dem Perito Moreno.
     * Streichlänge 10 km, Tiefe 100 m, Wellenhöhe aus der Streichlänge, keine Dünung (kein Meer), wenig Schaum, die
     * Brandungsringe erst ab mittlerem Seegang. Das milchige Türkis vom Gletschermehl setzt der Benutzer über die
     * Wasserfarbe in {@code gl.OceanGl.Light} ({@link #LAGO_WATER}, {@link #LAGO_SSS}).
     */
    public static Ocean lago(int bft) {
        Ocean o = beaufort(bft);
        o.fetch = 10_000;
        o.depth = 100;
        o.fetchLimited = true;
        o.swellHeight = 0;
        o.choppiness = 0.7;
        o.foamAmount = 0.5;
        o.ringHsLow = 0.08;
        o.ringHsHigh = 0.9;
        o.rebuildNow();
        return o;
    }

    /**
     * Seit Rungholt (Phase 2): das Wattenmeer mit seinen Strömen und Prielen. Streichlänge 6 km (die Bucht zwischen den
     * Inseln), Tiefe 4 m im Mittel über dem Watt, Wellenhöhe aus der Streichlänge, eine schwache Dünung aus der offenen
     * See über die Außensände (0,25 m aus Westnordwest), kurze steile See, Schaum mittel. Die grüngraue, trübe Farbe
     * setzt der Benutzer über {@link #WATT_WATER}, {@link #WATT_SSS}; den Grund (Watt, Priele, Strom) über die
     * Höhentextur in {@code gl.OceanGl.Light}, die Tide über {@link #waterLevel}.
     */
    public static Ocean watt(int bft) {
        Ocean o = beaufort(bft);
        o.fetch = 6_000;
        o.depth = 4;
        o.fetchLimited = true;
        o.swellHeight = 0.25;
        o.swellPeriod = 7;
        o.swellFromDeg = 290;
        o.choppiness = 0.8;
        o.foamAmount = 0.3;   // seit Rungholt Phase 4 (Befund Status_Phase3): kurze Windsee im Watt bricht selten
        o.ringHsLow = 0.05;
        o.ringHsHigh = 0.6;
        o.rebuildNow();
        return o;
    }

    /** Streulicht und Kamm des Wattenmeers: trübes Grüngrau vom Schwebstoff (linear) [G]. */
    public static final double[] WATT_WATER = {0.030, 0.050, 0.042}, WATT_SSS = {0.10, 0.17, 0.12};

    /** Streulicht und Kamm eines Gletschersees: milchiges Türkis vom Gletschermehl (linear) [G]. */
    public static final double[] LAGO_WATER = {0.030, 0.105, 0.105}, LAGO_SSS = {0.07, 0.36, 0.33};

    /** Seegang nach Beaufort 0..12: Wind in der Mitte der Stufe, Dünung passend dazu. */
    public static Ocean beaufort(int bft) {
        Ocean o = new Ocean();
        o.setBeaufort(bft);
        o.rebuildNow();
        return o;
    }

    /** Stellt Wind und Dünung auf eine Beaufort-Stufe (ohne sofortigen Neubau). */
    public void setBeaufort(int bft) {
        bft = Math.max(0, Math.min(12, bft));
        windSpeed = SeaState.MID[bft];
        swellHeight = Math.min(3.5, 0.3 + 0.18 * SeaState.HS[bft]);
        swellPeriod = 8 + 0.35 * bft;
    }

    // ------------------------------------------------------------------ Zustand

    /** Alles, woraus die See in einem Augenblick besteht; unveränderlich, wird bei Änderungen ersetzt. */
    public static final class State {
        public final int version;
        public final List<Spectrum.Cascade> cascades;
        /** Dünung: {@link Swell#STRIDE} Werte je Zug. */
        public final double[] swell;
        public final double windSeaHs, peakPeriod, foamCoverage, windToRad, choppiness, depth, windSpeed;
        public final Quality quality;
        final CpuField cpu;
        final double[][] jacobi;
        final int jacobiN;

        State(int version, Params p, List<Spectrum.Cascade> cascades, double[] swell, double hs, double tp, double foam,
              CpuField cpu, double[][] jacobi, int jn) {
            this.version = version;
            this.cascades = cascades;
            this.swell = swell;
            this.windSeaHs = hs;
            this.peakPeriod = tp;
            this.foamCoverage = foam;
            this.windToRad = Math.toRadians(p.windFrom + 180);
            this.choppiness = p.chop;
            this.depth = p.depth;
            this.windSpeed = p.wind;
            this.quality = p.quality;
            this.cpu = cpu;
            this.jacobi = jacobi;
            this.jacobiN = jn;
        }

        /** H_s von Windsee und Dünung zusammen. */
        public double totalHs() {
            double s = 0;
            for (int i = 0; i < Swell.COUNT; i++) s += swell[i * Swell.STRIDE + 4] * swell[i * Swell.STRIDE + 4] / 2;
            return 4 * Math.sqrt(windSeaHs * windSeaHs / 16 + s);
        }
    }

    record Params(double wind, double windFrom, double fetch, double depth, double chop, double hs, double swellH, double swellT,
                  double swellFrom, double foamAmount, long seed, Quality quality) { }

    Params params() {
        double hs = Double.isNaN(heightOverride) ? SeaState.significantHeight(windSpeed) : Math.max(0, heightOverride);
        if (Double.isNaN(heightOverride) && fetchLimited) hs = Math.min(hs, SeaState.fetchLimitedHeight(windSpeed, fetch, depth));
        return new Params(windSpeed, windFromDeg, fetch, depth, choppiness, hs, swellHeight, swellPeriod, swellFromDeg, foamAmount, seed, quality);
    }

    public State state() { return state; }
    public int version() { return state.version; }
    public double time() { return time; }
    public void setTime(double t) { time = t; }

    /** Läuft um dt Sekunden weiter; übernimmt ein fertig neu gebautes Spektrum. */
    public void step(double dt) {
        time += dt;
        ripples.expire(time);
        Params p = params();
        synchronized (this) {
            if (pending != null && pending.isDone()) {
                try { state = pending.get(); } catch (Exception e) { System.err.println("Ozean: " + e); }
                pending = null;
            }
            if (pending == null && !p.equals(lastBuilt) && !p.equals(pendingParams)) {
                pendingParams = p;
                int v = nextVersion++;
                pending = builder.submit(() -> build(p, v));
            }
        }
    }

    /** Die Werte, aus denen das zuletzt fertige Spektrum gebaut wurde. */
    private Params lastBuilt;

    /** Baut sofort neu (Prüfungen, Standbilder). */
    public synchronized void rebuildNow() {
        if (pending != null) { pending.cancel(false); pending = null; }
        state = build(params(), nextVersion++);
    }

    /** Ob gerade im Hintergrund neu gebaut wird. */
    public synchronized boolean rebuilding() { return pending != null && !pending.isDone(); }

    State build(Params p, int version) {
        int n = p.quality.n;
        List<Spectrum.Cascade> cs = Spectrum.build(p.wind, Math.toRadians(p.windFrom + 180), p.fetch, p.depth, p.hs, n, p.quality.cascades, p.seed);
        double[] sw = Swell.components(p.swellH, p.swellT, p.swellFrom, p.depth);
        double tp = 2 * Math.PI / Spectrum.peakOmega(p.wind, p.fetch);
        double cover = Math.min(0.95, SeaState.foamCoverage(p.wind) * p.foamAmount);
        int jn = Math.min(n, 256);
        double[][] jac = calibrateFoam(cs, p.chop, p.depth, cover, jn);
        CpuField cpu = new CpuField(cs, p.depth, p.chop);
        synchronized (this) { lastBuilt = p; }
        return new State(version, p, cs, sw, p.hs, tp, cover, cpu, jac, jn);
    }

    // ------------------------------------------------------------------ Schaum

    /** Anteil der Bedeckung, den jede Kaskade trägt (die feinen tragen keinen: ihr Schaum wären nur Sprenkel). */
    static final double[] FOAM_SHARE = {0.6, 0.4, 0, 0};

    /**
     * Rechnet je Kaskade die Jacobi-Determinante J auf einem jn²-Gitter (t = 0) und legt die Schwellen so, dass die
     * Vereinigung der Schaumflächen die gewünschte Bedeckung ergibt. Gibt die J-Felder zurück (für die Prüfung).
     */
    static double[][] calibrateFoam(List<Spectrum.Cascade> cs, double chop, double depth, double cover, int jn) {
        double[][] jac = new double[cs.size()][];
        double[][] sorted = new double[cs.size()][];
        for (Spectrum.Cascade c : cs) {
            jac[c.index] = jacobian(c, chop, jn);
            sorted[c.index] = jac[c.index].clone();
            Arrays.sort(sorted[c.index]);
        }
        if (cover <= 0) {
            for (Spectrum.Cascade c : cs) c.foamThreshold = -10;
            return jac;
        }
        double lo = 0, hi = 4;
        for (int it = 0; it < 40; it++) {
            double s = (lo + hi) / 2, keep = 1;
            for (Spectrum.Cascade c : cs) keep *= 1 - Math.min(0.99, FOAM_SHARE[Math.min(c.index, 3)] * s * cover * 1.4);
            if (1 - keep < cover) lo = s; else hi = s;
        }
        double s = (lo + hi) / 2;
        for (Spectrum.Cascade c : cs) {
            double p = Math.min(0.99, FOAM_SHARE[Math.min(c.index, 3)] * s * cover * 1.4);
            double[] so = sorted[c.index];
            c.foamThreshold = p <= 0 ? -10 : so[Math.max(0, Math.min(so.length - 1, (int) Math.round(p * so.length) - 1))];
        }
        return jac;
    }

    /** J = (1 + ∂dx/∂x)(1 + ∂dz/∂z) − (∂dx/∂z)² einer Kaskade auf einem jn²-Gitter zur Zeit 0. */
    static double[] jacobian(Spectrum.Cascade c, double chop, int jn) {
        int n = c.n;
        double[] ar = new double[jn * jn], ai = new double[jn * jn], br = new double[jn * jn], bi = new double[jn * jn];
        double dk = 2 * Math.PI / c.length;
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                int mx = c.wave(ix), mz = c.wave(iz);
                if (Math.abs(mx) >= jn / 2 || Math.abs(mz) >= jn / 2) continue;
                int t = (iz * n + ix) * 4;
                double hr = c.h0[t] + c.h0[t + 2], hi = c.h0[t + 1] + c.h0[t + 3];
                if (hr == 0 && hi == 0) continue;
                double kx = mx * dk, kz = mz * dk, k = Math.hypot(kx, kz);
                double fxx = -kx * kx / k * chop, fzz = -kz * kz / k * chop, fxz = -kx * kz / k * chop;
                int g = Math.floorMod(mz, jn) * jn + Math.floorMod(mx, jn);
                // A = jxx + i·jzz, B = jxz
                ar[g] += fxx * hr - fzz * hi;
                ai[g] += fxx * hi + fzz * hr;
                br[g] += fxz * hr;
                bi[g] += fxz * hi;
            }
        Fft.transform2d(ar, ai, jn, true);
        Fft.transform2d(br, bi, jn, true);
        double[] j = new double[jn * jn];
        for (int i = 0; i < j.length; i++) j[i] = (1 + ar[i]) * (1 + ai[i]) - br[i] * br[i];
        return j;
    }

    /** Wie viel Schaum bei Jacobi-Wert j und Schwelle thr (0..1, 0,5 genau auf der Schwelle; gleich im Shader). */
    public static double foamFromJacobian(double j, double thr) {
        double t = Math.max(0, Math.min(1, (thr + 0.06 - j) / 0.12));
        return t * t * (3 - 2 * t);
    }

    /**
     * Anteil der Fläche mit Schaum (Wert über 0,5) nach den J-Feldern aller Kaskaden, auf einem gemeinsamen
     * Gitter über die größte Kachel gelesen; ohne Nachleuchten, ohne Streifen. Für die Prüfung gegen die Tabelle.
     */
    public double measuredFoamCoverage() {
        State s = state;
        int samples = 200, foam = 0;
        double l0 = s.cascades.get(0).length;
        for (int i = 0; i < samples; i++)
            for (int j = 0; j < samples; j++) {
                double x = (i + 0.5) / samples * l0, z = (j + 0.5) / samples * l0, keep = 1;
                for (Spectrum.Cascade c : s.cascades) {
                    if (c.foamThreshold <= -9) continue;
                    double[] jf = s.jacobi[c.index];
                    int jn = s.jacobiN;
                    int u = Math.floorMod((int) Math.floor(x / c.length * jn), jn), v = Math.floorMod((int) Math.floor(z / c.length * jn), jn);
                    keep *= 1 - foamFromJacobian(jf[v * jn + u], c.foamThreshold);
                }
                if (1 - keep > 0.5) foam++;
            }
        return foam / (double) (samples * samples);
    }

    /** Schaumstraßen in Windrichtung ab Bft 7 (gleiche Formel im Shader). */
    public static double streaks(double x, double z, double windToRad, double windMs) {
        double amount = smooth(13.0, 17.5, windMs);
        if (amount <= 0) return 0;
        double dx = Math.sin(windToRad), dz = -Math.cos(windToRad);
        double along = x * dx + z * dz, across = -x * dz + z * dx;
        double warp = vnoise(along / 23, across / 3.1) * 1.6;
        double n = vnoise(along / 34 + warp, across / 0.9) * 0.6 + vnoise(along / 9 - warp, across / 0.35) * 0.4;
        return amount * smooth(0.58, 0.82, n) * 0.75;
    }

    /** Brandungsring: Schaum im Abstand ds von einem Hindernis bei H_s und Zeit t (gleiche Formel im Shader). */
    public static double surfRing(double ds, double hs, double x, double z, double t) { return surfRing(ds, hs, x, z, t, 0.15, 1.6); }

    /** Brandungsring mit eigener Schwelle (seit Haithabu Phase 8): voll ab hsHigh, nichts unter hsLow. */
    public static double surfRing(double ds, double hs, double x, double z, double t, double hsLow, double hsHigh) {
        if (ds < 0) return 1;
        // Phase 16: bei ruhiger See (Bft 1, nur Dünung) nur ein Saum, kein Kragen
        double width = 0.5 + 0.8 * hs;
        double strength = smooth(hsLow, hsHigh, hs);
        double n = vnoise(x * 0.8 + t * 0.35, z * 0.8 - t * 0.2);
        return strength * Math.exp(-ds / width) * (0.45 + 0.55 * n);
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Wertrauschen 0..1 auf ganzzahligem Gitter, weich (dieselbe Formel im Shader, dort in float). */
    static double vnoise(double x, double z) {
        double ix = Math.floor(x), iz = Math.floor(z), fx = x - ix, fz = z - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz), b = hash(ix + 1, iz), c = hash(ix, iz + 1), d = hash(ix + 1, iz + 1);
        return a + (b - a) * fx + (c - a) * fz + (a - b - c + d) * fx * fz;
    }

    static double hash(double x, double z) {
        double h = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
        return h - Math.floor(h);
    }

    // ------------------------------------------------------------------ Hindernisse und Einschläge

    public void addObstacle(Obstacle o) { obstacles.add(o); }
    /** Seit Haithabu Phase 8: viele Körper als Abstandsraster (Pfähle, Pfahlreihen); null = keins. */
    public volatile ObstacleField obstacleField;
    /**
     * Seit Haithabu Phase 8: ab welcher Wellenhöhe H<sub>s</sub> (m) der Brandungsring um Körper beginnt und wann er
     * voll ist. Offene See 0,15 bis 1,6; das Noor ({@link #noor}) 0,03 bis 0,35, sonst bliebe es an den Pfählen glatt.
     */
    public double ringHsLow = 0.15, ringHsHigh = 1.6;
    /** Seit Haithabu Phase 9: Rümpfe in der Wasserlinie (bis 8 werden gezeichnet); innen kein Wasser. */
    public volatile java.util.List<HullMask> hulls = java.util.List.of();
    public void clearObstacles() { obstacles.clear(); }
    public List<Obstacle> obstacles() { return new ArrayList<>(obstacles); }

    /** Ein Einschlag bei (x, z), Stärke 0..1 (zurückfallende Gischt, Brecher, Schiff): eine Ringwelle läuft los. */
    public void addImpulse(double x, double z, double strength) { ripples.add(x, z, time, strength); }

    public List<Ripples.Impulse> impulses() { return ripples.snapshot(); }

    // ------------------------------------------------------------------ Abfragen ohne Grafikkarte

    /** Alles an einem Punkt der Wasseroberfläche. */
    public static final class Sample {
        public double height, foam;
        public final double[] normal = new double[3], velocity = new double[3];
        /** Der Ruhepunkt, dessen Teilchen gerade über (x, z) liegt. */
        public double restX, restZ;
    }

    /** Wasseroberfläche über (x, z) zur aktuellen Zeit: Höhe, Normale, Geschwindigkeit, Schaum. */
    public Sample sample(double x, double z) { return sample(x, z, CpuField.ALL); }

    private Sample sample(double x, double z, int mask) {
        State s = state;
        double t = time;
        double[] f = new double[CpuField.FIELDS], w = new double[9];
        double x0 = x, z0 = z;
        synchronized (s.cpu) {
            s.cpu.update(t, mask);
            for (int it = 0; it < 6; it++) {
                Arrays.fill(f, 0);
                Arrays.fill(w, 0);
                s.cpu.sample(x0, z0, f, CpuField.HEIGHT);
                Swell.add(s.swell, x0, z0, t, w);
                double nx = x - f[0] - w[0], nz = z - f[2] - w[2];
                boolean done = Math.abs(nx - x0) + Math.abs(nz - z0) < 1e-4;
                x0 = nx;
                z0 = nz;
                if (done) break;
            }
            Arrays.fill(f, 0);
            Arrays.fill(w, 0);
            s.cpu.sample(x0, z0, f, mask);
            Swell.add(s.swell, x0, z0, t, w);
        }
        Sample o = new Sample();
        o.restX = x0;
        o.restZ = z0;
        o.height = waterLevel + f[1] + w[1] + ripples.height(x, z, t);
        double sx = (f[6] + w[6]) / (1 + f[8] + w[8] * 0.5), sz = (f[7] + w[7]) / (1 + f[9] + w[8] * 0.5);
        double len = Math.sqrt(sx * sx + 1 + sz * sz);
        o.normal[0] = -sx / len;
        o.normal[1] = 1 / len;
        o.normal[2] = -sz / len;
        o.velocity[0] = f[3] + w[3];
        o.velocity[1] = f[4] + w[4];
        o.velocity[2] = f[5] + w[5];
        double jac = (1 + f[8]) * (1 + f[9]);
        double thr = s.cascades.get(0).foamThreshold;
        double foam = foamFromJacobian(jac, thr);
        foam = Math.max(foam, streaks(x, z, s.windToRad, s.windSpeed));
        double hsAll = s.totalHs();
        for (Obstacle ob : obstacles) foam = Math.max(foam, surfRing(ob.distance(x, z), hsAll, x, z, t, ringHsLow, ringHsHigh));
        ObstacleField of = obstacleField;
        if (of != null) foam = Math.max(foam, surfRing(of.distance(x, z), hsAll, x, z, t, ringHsLow, ringHsHigh));
        o.foam = Math.min(1, foam);
        return o;
    }

    /** Nur die Höhe (rechnet auf dem Prozessor nur die dafür nötigen Felder, rund ein Viertel der Arbeit). */
    public double heightAt(double x, double z) { return sample(x, z, CpuField.HEIGHT).height; }
    public double[] normalAt(double x, double z) { return sample(x, z).normal.clone(); }
    public double[] velocityAt(double x, double z) { return sample(x, z).velocity.clone(); }
    /** Phase 13: nur die senkrechte Geschwindigkeit der Oberfläche (m/s); so billig wie {@link #heightAt} (für Schwimmkörper). */
    public double verticalVelocityAt(double x, double z) { return sample(x, z, CpuField.HEIGHT).velocity[1]; }
    public double foamAt(double x, double z) { return sample(x, z).foam; }

    /**
     * Genaue Auslenkung des Ruhepunkts (x0, z0) zur Zeit t aus allen Moden aller Kaskaden, ohne Gitter (langsam; für
     * die Prüfung und den Abgleich mit der Grafikkarte). Ergebnis: dx, dy, dz der Windsee (ohne Dünung).
     */
    public double[] exactWindSea(double x0, double z0, double t) {
        double[] sum = new double[3];
        for (Spectrum.Cascade c : state.cascades) {
            double[] d = exactCascade(c.index, x0, z0, t);
            for (int i = 0; i < 3; i++) sum[i] += d[i];
        }
        return sum;
    }

    /** Wie {@link #exactWindSea}, aber nur Kaskade c (für den Abgleich mit ihrer Textur auf der Grafikkarte). */
    public double[] exactCascade(int ci, double x0, double z0, double t) {
        State s = state;
        Spectrum.Cascade c = s.cascades.get(ci);
        double tt = t % Spectrum.REPEAT, dx = 0, dy = 0, dz = 0;
        int n = c.n;
        double dk = 2 * Math.PI / c.length;
        for (int iz = 0; iz < n; iz++)
            for (int ix = 0; ix < n; ix++) {
                int i = (iz * n + ix) * 4;
                if (c.h0[i] == 0 && c.h0[i + 1] == 0 && c.h0[i + 2] == 0 && c.h0[i + 3] == 0) continue;
                double kx = c.wave(ix) * dk, kz = c.wave(iz) * dk, k = Math.hypot(kx, kz);
                double w = Spectrum.omegaQ(k, s.depth) * tt, co = Math.cos(w), si = Math.sin(w);
                double hr = c.h0[i] * co - c.h0[i + 1] * si + c.h0[i + 2] * co + c.h0[i + 3] * si;
                double hi = c.h0[i] * si + c.h0[i + 1] * co - c.h0[i + 2] * si + c.h0[i + 3] * co;
                double ph = kx * x0 + kz * z0, pc = Math.cos(ph), ps = Math.sin(ph);
                double er = hr * pc - hi * ps, ei = hr * ps + hi * pc;   // h·e^{ik·x}
                dy += er;
                // dx = Re(i·kx/k·λ·h·e^{ikx}) = −(kx/k)·λ·Im(h·e^{ikx})
                dx += -kx / k * s.choppiness * ei;
                dz += -kz / k * s.choppiness * ei;
            }
        return new double[]{dx, dy, dz};
    }

    /** „Bft 6 · Starker Wind · Douglas 5 grob · Windsee Hs 3,0 m, Tp 6,7 s · Dünung 1,1 m aus 290° · Schaum 10 %“ */
    public String describe() {
        State s = state;
        return String.format(Locale.GERMAN, "%s · Windsee Hs %.1f m, Tp %.1f s · Dünung %.1f m, %.0f s aus %03.0f° · Schaum %.0f %%",
                SeaState.describe(windSpeed), s.windSeaHs, s.peakPeriod, swellHeight, swellPeriod, swellFromDeg, s.foamCoverage * 100);
    }
}
