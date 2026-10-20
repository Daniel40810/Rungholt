package com.dan.mudflat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Der Zustand des Watts je Zelle (Phase 5): Nässe, Sediment und die Richtung der letzten Strömung, auf einem Gitter
 * über dem Grund (in Rungholt dasselbe wie die Überflutung von {@code tide}). Kennt keine Tide: Wassertiefe und Strömung
 * je Zelle kommen von außen ({@link #step}), ebenso das Wetter ({@link Weather}).
 * <ul>
 * <li><b>Nässe</b> springt mit dem Wasser auf 1 und trocknet danach mit einer Halbwertszeit, die zwischen Schatten und
 * Windstille (Stunden) und Sonne und Wind (Minuten) liegt; Wärme beschleunigt, Regen macht wieder nass, und jedes
 * Sediment behält eine Restnässe (Schlick bleibt feucht) [G].</li>
 * <li><b>Sediment</b> 0 = Sand … 1 = Schlick aus der Lage [G]: Sand auf den seewärtigen, offenen Flächen und in den
 * Rinnen, Schlick landwärts im Schutz der Deiche und Halligen (lange Laufzeit der Flut), Mischwatt dazwischen, dazu
 * Flecken.</li>
 * <li><b>Rippel</b> quer zur letzten Strömung: die Richtung wird gemerkt, solange Wasser über der Zelle fließt.</li>
 * <li><b>Spuren</b> (Füße, Räder, Robben) bis zur nächsten Flut.</li>
 * </ul>
 */
public final class WetField {
    /** Das Wetter für die Trocknung: Sonne 0..1 (Höhe und Wolken), Wind (m/s), Luft (°C), Regen (mm/h). */
    public record Weather(double sun, double wind, double airC, double rainMmH) {
        public static final Weather CALM_SHADE = new Weather(0, 0, 10, 0), SUNNY_WINDY = new Weather(1, 10, 20, 0);
    }

    /** Eine Spur: Ort, Richtung, Art (0 Fuß, 1 Rad, 2 Robbe), Zeit (Stunden). */
    public record Track(double x, double z, double dirX, double dirZ, int kind, double hours) { }

    public final int nx, nz;
    public final double x0, z0, cell;
    /** Nässe 0..1, Sediment 0..1, Richtung der letzten Strömung (Länge = Stärke 0..1, 1 ab 0,5 m/s), Stunden seit dem Trockenfallen. */
    public final float[] wet, sediment, dirX, dirZ, dryHours;
    public final Mudflat material;
    public volatile int version;
    public volatile double stepMs;
    private double lastHours = Double.NaN;
    private final java.util.ArrayDeque<Track> tracks = new java.util.ArrayDeque<>();
    /** Höchstzahl der Spuren (die ältesten gehen zuerst). */
    public static final int MAX_TRACKS = 30_000;
    /** Mittlere Nässe der Zellen über Wasser im letzten Schritt (für das HUD). */
    public volatile double meanWet;

    public WetField(Mudflat material, int nx, int nz, double x0, double z0, double cell) {
        this.material = material;
        this.nx = nx;
        this.nz = nz;
        this.x0 = x0;
        this.z0 = z0;
        this.cell = cell;
        int n = nx * nz;
        wet = new float[n];
        sediment = new float[n];
        dirX = new float[n];
        dirZ = new float[n];
        dryHours = new float[n];
        Arrays.fill(wet, 1f);
        Arrays.fill(dirX, 1f);
    }

    // ------------------------------------------------------------------ Sediment

    /**
     * Sediment aus der Lage [G]: {@code ground} Höhe je Zelle (m), {@code travel} Laufzeit der Flut vom offenen Meer (s,
     * unendlich = nie erreicht), mtnw/mthw das mittlere Niedrig- und Hochwasser. Sand seewärts und in den Rinnen, Schlick
     * landwärts in geschützten Buchten; Flecken aus Rauschen.
     */
    public void classify(float[] ground, float[] travel, double mtnw, double mthw) {
        double tmax = 1;
        for (float t : travel) if (!Float.isInfinite(t)) tmax = Math.max(tmax, t);
        final double tm = tmax;
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                float g = ground[k], t = travel[k];
                double tn = Float.isInfinite(t) ? 1 : t / tm;
                double s = smooth(0.30, 0.85, tn);                        // landwärts schlickiger
                double hr = (g - mtnw) / (mthw - mtnw);                    // 0 am Niedrig-, 1 am Hochwasser
                s += 0.15 * smooth(0.6, 1.0, hr);                          // obere Watten nahe dem Vorland: Schlick
                if (g < mtnw - 0.8) s *= 0.35;                             // Rinnen und Priele: Sand und Schill
                double gx = ground[j * nx + Math.min(nx - 1, i + 1)] - ground[j * nx + Math.max(0, i - 1)];
                double gz = ground[Math.min(nz - 1, j + 1) * nx + i] - ground[Math.max(0, j - 1) * nx + i];
                double slope = Math.hypot(gx, gz) / (2 * cell);
                s -= 4 * slope;                                            // Kanten: ausgespült, sandig
                s += 0.25 * (noise((x0 + (i + 0.5) * cell) / 900, (z0 + (j + 0.5) * cell) / 900) - 0.5);
                sediment[k] = (float) Math.max(0, Math.min(1, s));
            }
        });
        version++;
    }

    // ------------------------------------------------------------------ Schritt

    /**
     * Ein Schritt zur Zeit {@code hours} (Stunden seit einem beliebigen Bezug): {@code depth} Wassertiefe je Zelle (m),
     * {@code u, v} Strömung (m/s) oder null. Sprünge über eine Stunde setzen nur den Zustand neu: nass, wo Wasser steht,
     * sonst die Nässe nach der Trockenzeit {@code dryHoursOrNull} (etwa aus der Überflutung) oder unverändert.
     */
    public void step(double hours, float[] depth, float[] u, float[] v, float[] dryHoursOrNull, Weather w) {
        long t0 = System.nanoTime();
        double dt = Double.isNaN(lastHours) ? Double.POSITIVE_INFINITY : hours - lastHours;
        boolean jump = !(dt >= 0 && dt < 1);
        lastHours = hours;
        double half = halfLife(w);
        final float decay = jump ? 1 : (float) Math.pow(0.5, dt / half);
        final float rainWet = (float) Math.min(1, w.rainMmH() / 2.0);
        final float dtH = jump ? 0 : (float) dt;
        final double halfF = half;
        final float minDepth = 0.01f;
        double[] sums = new double[nz], counts = new double[nz];
        IntStream.range(0, nz).parallel().forEach(j -> {
            double s = 0;
            int c = 0;
            for (int k = j * nx; k < (j + 1) * nx; k++) {
                float rest = residual(sediment[k]);
                if (depth[k] > minDepth) {
                    wet[k] = 1;
                    dryHours[k] = 0;
                    if (u != null) {
                        // die Rippel richten sich quer zur Strömung aus; gemerkt wird Richtung und Stärke (Länge des
                        // Vektors: 1 ab 0,5 m/s, Phase 6), schwache Strömung baut sie nur langsam um
                        float uu = u[k], vv = v[k], sp = (float) Math.hypot(uu, vv);
                        if (sp > 0.03f) {
                            float a = Math.min(1, sp * 2) * 0.5f, tl = Math.min(1, sp / 0.5f) / sp;
                            float nxv = dirX[k] * (1 - a) + uu * tl * a, nzv = dirZ[k] * (1 - a) + vv * tl * a, l = (float) Math.hypot(nxv, nzv);
                            if (l > 1) { nxv /= l; nzv /= l; }
                            if (l > 1e-4f) { dirX[k] = nxv; dirZ[k] = nzv; }
                        }
                    }
                    continue;
                }
                if (jump) {
                    float dh = dryHoursOrNull != null ? dryHoursOrNull[k] : dryHours[k];
                    dryHours[k] = dh;
                    wet[k] = (float) (rest + (1 - rest) * Math.pow(0.5, dh / halfF));
                } else {
                    dryHours[k] += dtH;
                    wet[k] = rest + (wet[k] - rest) * decay;
                }
                if (rainWet > wet[k]) wet[k] = rainWet;
                s += wet[k];
                c++;
            }
            sums[j] = s;
            counts[j] = c;
        });
        double s = 0, c = 0;
        for (int j = 0; j < nz; j++) { s += sums[j]; c += counts[j]; }
        meanWet = c == 0 ? 1 : s / c;
        // Spuren verschwinden mit der Flut
        synchronized (tracks) {
            tracks.removeIf(t -> { int k = index(t.x(), t.z()); return k >= 0 && depth[k] > minDepth; });
        }
        version++;
        double ms = (System.nanoTime() - t0) / 1e6;
        stepMs = stepMs <= 0 ? ms : stepMs * 0.9 + ms * 0.1;
    }

    /**
     * Restnässe je Sediment [G]: das Watt liegt über dem Grundwasser und trocknet nie ganz; Sand wird matt und hell
     * (22 %), Schlick bleibt feucht (52 %).
     */
    public static float residual(float sediment) { return 0.22f + 0.30f * sediment * sediment; }

    /**
     * Halbwertszeit der Nässe (Stunden) bei diesem Wetter [G]: zwischen Schatten und Windstille
     * ({@link Mudflat#halfDryShadeHours}) und Sonne mit frischem Wind ({@link Mudflat#halfDrySunWindMinutes}); Wärme
     * hilft, Regen hält nass.
     */
    public double halfLife(Weather w) {
        double sun = clamp01(w.sun()), wind = smooth(0, 10, w.wind()), warm = clamp01((w.airC() - 5) / 20);
        double k = clamp01(0.6 * sun + 0.4 * wind + 0.1 * warm * sun);
        double h = material.halfDryShadeHours * (1 - k) + material.halfDrySunWindMinutes / 60.0 * k;
        if (w.airC() < 0) h *= 3;          // Frost: kaum Verdunstung
        if (w.rainMmH() > 0.1) h *= 20;    // Regen: es trocknet nichts
        return h;
    }

    // ------------------------------------------------------------------ Spuren

    /**
     * Eine Spur hinterlassen (bleibt bis zur nächsten Flut, höchstens {@link #MAX_TRACKS}). Arten: 0 Stiefel (links und
     * rechts im Wechsel), 1 Rad, 2 Robbe, seit Rungholt Phase 9: 3 nackter Fuß, 4 Stiefel genau am Ort (der Fuß einer
     * Figur, schon zur Seite versetzt).
     */
    public void addTrack(double x, double z, double dirX, double dirZ, int kind) {
        synchronized (tracks) {
            tracks.add(new Track(x, z, dirX, dirZ, kind, lastHours));
            if (tracks.size() > MAX_TRACKS) tracks.pollFirst();
        }
    }

    public List<Track> tracks() { synchronized (tracks) { return new ArrayList<>(tracks); } }

    // ------------------------------------------------------------------ Abfragen

    public int index(double x, double z) {
        int i = (int) Math.floor((x - x0) / cell), j = (int) Math.floor((z - z0) / cell);
        if (i < 0 || j < 0 || i >= nx || j >= nz) return -1;
        return j * nx + i;
    }

    public double wetnessAt(double x, double z) { int k = index(x, z); return k < 0 ? 1 : wet[k]; }
    public double sedimentAt(double x, double z) { int k = index(x, z); return k < 0 ? 0.3 : sediment[k]; }
    public double dryHoursAt(double x, double z) { int k = index(x, z); return k < 0 ? 0 : dryHours[k]; }
    /** Stärke der letzten Strömung 0..1 (1 ab 0,5 m/s): hohe, scharfe Rippel bei 1, flache bei 0. */
    public double currentAt(double x, double z) { int k = index(x, z); return k < 0 ? 1 : Math.hypot(dirX[k], dirZ[k]); }

    /** Sediment als Klasse: unter 0,33 Sand, über 0,66 Schlick, dazwischen Mischwatt. */
    public Mudflat.Sediment classAt(double x, double z) {
        double s = sedimentAt(x, z);
        return s < 0.33 ? Mudflat.Sediment.SAND : s > 0.66 ? Mudflat.Sediment.SCHLICK : Mudflat.Sediment.MISCHWATT;
    }

    /**
     * Farbe am Ort (linear, Albedo) aus Sediment und Nässe, wie im Shader im Mittel über die Rippel: für Vögel und
     * Seehunde (wo ist Schlick, wo Sand) und für Prüfungen.
     */
    public double[] colorAt(double x, double z) {
        double s = sedimentAt(x, z), w = wetnessAt(x, z);
        return color(s, w, material.wetDarkening);
    }

    /** Albedo (linear) aus Sediment 0..1 und Nässe 0..1 [G, verglichen mit templates/Watt.png]. */
    public static double[] color(double sediment, double wetness, double wetDarkening) {
        double[] sand = {0.340, 0.280, 0.190}, misch = {0.250, 0.200, 0.130}, schlick = {0.150, 0.125, 0.085};
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double a = sediment < 0.5 ? lerp(sand[i], misch[i], sediment * 2) : lerp(misch[i], schlick[i], (sediment - 0.5) * 2);
            c[i] = a * (1 - wetDarkening * clamp01(wetness));
        }
        return c;
    }

    /**
     * Den Zustand als RGBA8 je Zelle schreiben: R Nässe, G Sediment, B und A die Richtung der letzten Strömung
     * (−1..1 auf 0..255). Für eine Textur der Grafikkarte.
     */
    public void fill(byte[] rgba) {
        IntStream.range(0, nz).parallel().forEach(j -> {
            for (int k = j * nx; k < (j + 1) * nx; k++) {
                int o = k * 4;
                rgba[o] = (byte) Math.round(clamp01(wet[k]) * 255);
                rgba[o + 1] = (byte) Math.round(clamp01(sediment[k]) * 255);
                rgba[o + 2] = (byte) Math.round((dirX[k] * 0.5 + 0.5) * 255);
                rgba[o + 3] = (byte) Math.round((dirZ[k] * 0.5 + 0.5) * 255);
            }
        });
    }

    /** Anteile Sand, Mischwatt, Schlick über alle Zellen (für das HUD und die Prüfung). */
    public double[] shares(float[] groundOrNull, double minG, double maxG) {
        double[] s = new double[3];
        int n = 0;
        for (int k = 0; k < sediment.length; k++) {
            if (groundOrNull != null && (groundOrNull[k] < minG || groundOrNull[k] > maxG)) continue;
            float v = sediment[k];
            s[v < 0.33f ? 0 : v > 0.66f ? 2 : 1]++;
            n++;
        }
        for (int i = 0; i < 3; i++) s[i] /= Math.max(1, n);
        return s;
    }

    /** „Watt 1024² zu 16 m · Nässe im Mittel 54 % · 0,8 ms je Schritt · 12 Spuren“ */
    public String describe() {
        int nt;
        synchronized (tracks) { nt = tracks.size(); }
        return String.format(Locale.GERMAN, "Watt %d×%d zu %.0f m · Nässe über Wasser im Mittel %.0f %% · %.2f ms je Schritt · %d Spuren",
                nx, nz, cell, meanWet * 100, stepMs, nt);
    }

    // ------------------------------------------------------------------ Hilfen

    static double clamp01(double x) { return Math.max(0, Math.min(1, x)); }
    static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    static double smooth(double a, double b, double x) { double t = clamp01((x - a) / (b - a)); return t * t * (3 - 2 * t); }

    /** Glattes Wertrauschen 0..1 (zwei Oktaven). */
    static double noise(double x, double z) { return 0.65 * vnoise(x, z) + 0.35 * vnoise(x * 2.3 + 17.1, z * 2.3 + 5.7); }

    static double vnoise(double x, double z) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = hash(i, j), b = hash(i + 1, j), c = hash(i, j + 1), d = hash(i + 1, j + 1);
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fz);
    }

    static double hash(int i, int j) {
        long h = i * 374761393L + j * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xffffff) / (double) 0xffffff;
    }
}
