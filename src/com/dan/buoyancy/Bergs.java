package com.dan.buoyancy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Eisberge auf einem See (seit Glacier Phase 9): alle {@link Berg}e eines Gewässers mit Wind, Strömung, Schmelze,
 * Stößen untereinander und dem Treibeis ({@link BrashField}). Gerechnet wird mit festem Schritt (Vorgabe 1/60 s) in
 * dem Faden, der {@link #step} aufruft; die Liste darf aus anderen Fäden gelesen werden.
 * <ul>
 * <li><b>Stöße:</b> Proben an der Oberfläche zweier Berge, die sich durchdringen, drücken sich mit Feder und Dämpfer
 * auseinander (vorher grob nach Hüllkugeln sortiert);</li>
 * <li><b>Schmelze:</b> m je Tag unter Wasser, an der Wasserlinie und an der Luft (Szenenzeit, also mit Zeitraffer);
 * was unter {@link #minVolume} fällt, ist weg;</li>
 * <li><b>Ereignisse:</b> {@link #onCapsize} (ein Berg ist gekentert), {@link #onMelted}.</li>
 * </ul>
 */
public final class Bergs {
    /** Ein Berg hat sich umgedreht oder ist weggeschmolzen. */
    @FunctionalInterface public interface Event { void berg(Berg b); }

    public volatile Sea sea;
    /** Wind (m/s) und woher (Grad, meteorologisch); der Berg treibt mit wenigen Prozent davon. */
    public volatile double windSpeed = 5, windFromDeg = 270;
    /** Luft (kg/m³), Beiwert des Winds an der Fläche über Wasser, quadratischer Widerstand im Wasser, Dämpfung je s. */
    public double rhoAir = 1.25, windCoef = 1.3, dragCoef = 1.0, waterDamping = 0.12;
    /** Schmelze (m je Tag): unter Wasser, an der Wasserlinie, an der Luft [G: Gletschersee um 1–3 °C]. */
    public volatile double meltWater = 0.8, meltLine = 2.0, meltAir = 0.2;
    /** Kleiner wird nichts gezeigt (m³). */
    public volatile double minVolume = 3;
    /** Höchstzahl; der kleinste geht, wenn ein neuer kommt. */
    public volatile int max = 400;
    public double fixedStep = 1 / 60.0;
    public volatile Event onCapsize, onMelted;
    /** Treibeis vor der Front (null: keins). */
    public volatile BrashField brash;

    public final List<Berg> bergs = new CopyOnWriteArrayList<>();
    double windX, windZ;
    private double acc, time;
    private long steps, contacts;
    private double stepMs;
    private int capsized, melted, removed;

    /** Einen Berg dazu (sicher aus jedem Faden). Ist die Höchstzahl erreicht, geht der kleinste. */
    public Berg add(Berg b) {
        if (bergs.size() >= max) {
            Berg small = null;
            for (Berg x : bergs) if (small == null || x.volume < small.volume) small = x;
            if (small != null && small.volume < b.volume) bergs.remove(small); else return null;
        }
        bergs.add(b);
        return b;
    }

    public void remove(Berg b) { if (bergs.remove(b)) removed++; }
    /** Alle weg (zählt als entfernt, nicht als geschmolzen). */
    public void clear() { removed += bergs.size(); bergs.clear(); contacts = 0; }

    /** dt echte Sekunden, davon {@code sceneDt} Szenenzeit für die Schmelze (Zeitraffer). */
    public void step(double dt, double sceneDt) {
        Sea s = sea;
        if (s == null) return;
        long t0 = System.nanoTime();
        acc += dt;
        double meltPer = dt > 0 ? sceneDt / dt : 0;
        double to = Math.toRadians(windFromDeg + 180);
        windX = windSpeed * Math.sin(to);
        windZ = -windSpeed * Math.cos(to);
        int k = 0;
        while (acc >= fixedStep && k < 4) {
            acc -= fixedStep;
            one(s, fixedStep, fixedStep * meltPer);
            k++;
        }
        if (acc > fixedStep) acc = 0;   // nicht nachholen, was der Faden nicht schafft
        BrashField bf = brash;
        if (bf != null) bf.step(dt, sceneDt, windX, windZ, s);
        stepMs = stepMs * 0.95 + (System.nanoTime() - t0) / 1e6 * 0.05;
    }

    private void one(Sea s, double dt, double meltDt) {
        time += dt;
        steps++;
        List<Berg> list = new ArrayList<>(bergs);
        int n = list.size();
        double[][] f = new double[n][6];
        collide(list, f);
        for (int i = 0; i < n; i++) {
            Berg b = list.get(i);
            b.step(dt, meltDt, s, this, new double[]{f[i][0], f[i][1], f[i][2]}, new double[]{f[i][3], f[i][4], f[i][5]});
            if (b.volume < minVolume || b.samples() == 0) {
                bergs.remove(b);
                melted++;
                Event e = onMelted;
                if (e != null) e.berg(b);
            }
        }
    }

    void capsized(Berg b) {
        capsized++;
        Event e = onCapsize;
        if (e != null) e.berg(b);
    }

    /** Stöße: nach x sortiert, Hüllkugeln, dann Proben an der Oberfläche gegeneinander. */
    private void collide(List<Berg> list, double[][] f) {
        int n = list.size();
        if (n < 2) { contacts = 0; return; }
        Integer[] order = new Integer[n];
        double[][] c = new double[n][];
        for (int i = 0; i < n; i++) { order[i] = i; c[i] = list.get(i).cgWorld(); }
        Arrays.sort(order, (a, b) -> Double.compare(c[a][0] - list.get(a).radius, c[b][0] - list.get(b).radius));
        double[] pa = new double[3], pb = new double[3];
        long cnt = 0;
        for (int ii = 0; ii < n; ii++) {
            int i = order[ii];
            Berg a = list.get(i);
            for (int jj = ii + 1; jj < n; jj++) {
                int j = order[jj];
                Berg b = list.get(j);
                if (c[j][0] - b.radius > c[i][0] + a.radius) break;
                double dx = c[j][0] - c[i][0], dy = c[j][1] - c[i][1], dz = c[j][2] - c[i][2], rr = a.radius + b.radius;
                if (dx * dx + dy * dy + dz * dz > rr * rr) continue;
                double reach = (a.cell + b.cell) / 2;
                double k = rhoWater(a) * Berg.G * Math.min(a.cell, b.cell) * Math.min(a.cell, b.cell) * 30;
                for (int sa = 0; sa < a.n; sa++) {
                    if (!a.surface[sa]) continue;
                    a.sampleWorld(sa, pa);
                    // nur Proben in der Nähe des anderen
                    double ex = pa[0] - c[j][0], ey = pa[1] - c[j][1], ez = pa[2] - c[j][2];
                    if (ex * ex + ey * ey + ez * ez > (b.radius + reach) * (b.radius + reach)) continue;
                    for (int sb = 0; sb < b.n; sb++) {
                        if (!b.surface[sb]) continue;
                        b.sampleWorld(sb, pb);
                        double nx = pa[0] - pb[0], ny = pa[1] - pb[1], nz = pa[2] - pb[2], d2 = nx * nx + ny * ny + nz * nz;
                        if (d2 >= reach * reach || d2 < 1e-12) continue;
                        double d = Math.sqrt(d2), pen = reach - d;
                        nx /= d; ny /= d; nz /= d;
                        // Relativgeschwindigkeit längs der Normalen dämpfen
                        double[] va = pointVel(a, c[i], pa), vb = pointVel(b, c[j], pb);
                        double vn = (va[0] - vb[0]) * nx + (va[1] - vb[1]) * ny + (va[2] - vb[2]) * nz;
                        double fn = k * pen - k * 0.12 * Math.min(0, vn);
                        if (fn <= 0) continue;
                        cnt++;
                        double fx = fn * nx, fy = fn * ny, fz = fn * nz;
                        add(f[i], c[i], pa, fx, fy, fz);
                        add(f[j], c[j], pb, -fx, -fy, -fz);
                    }
                }
            }
        }
        contacts = cnt;
    }

    private static double rhoWater(Berg b) { return b.rhoWater; }

    private static double[] pointVel(Berg b, double[] c, double[] p) {
        double rx = p[0] - c[0], ry = p[1] - c[1], rz = p[2] - c[2];
        return new double[]{b.v[0] + b.w[1] * rz - b.w[2] * ry, b.v[1] + b.w[2] * rx - b.w[0] * rz, b.v[2] + b.w[0] * ry - b.w[1] * rx};
    }

    private static void add(double[] f, double[] c, double[] p, double fx, double fy, double fz) {
        double rx = p[0] - c[0], ry = p[1] - c[1], rz = p[2] - c[2];
        f[0] += fx; f[1] += fy; f[2] += fz;
        f[3] += ry * fz - rz * fy; f[4] += rz * fx - rx * fz; f[5] += rx * fy - ry * fx;
    }

    // ------------------------------------------------------------------ Abfragen

    public int count() { return bergs.size(); }
    public double totalVolume() { double v = 0; for (Berg b : bergs) v += b.volume; return v; }
    public Berg largest() { Berg m = null; for (Berg b : bergs) if (m == null || b.volume > m.volume) m = b; return m; }
    /** Der Berg, dessen Hüllkugel (x, z) am nächsten ist (höchstens maxDist m). */
    public Berg nearest(double x, double z, double maxDist) {
        Berg m = null;
        double best = maxDist;
        for (Berg b : bergs) {
            double[] c = b.cgWorld();
            double d = Math.hypot(c[0] - x, c[2] - z) - b.radius;
            if (d < best) { best = d; m = b; }
        }
        return m;
    }
    public double time() { return time; }
    public double stepMs() { return stepMs; }
    public int capsized() { return capsized; }
    public int melted() { return melted; }
    public long contacts() { return contacts; }
    /** Seit Glacier Phase 11: von Hand entfernt (Alle weg, remove) – nicht geschmolzen. */
    public int removed() { return removed; }

    /** „Eisberge: 120, 1,2 Mio. m³, größter 24 000 m³ · 3 gekentert · 2,1 ms je Schritt“ */
    public String describe() {
        Berg l = largest();
        BrashField bf = brash;
        return String.format(Locale.GERMAN, "Eisberge: %d, %,.0f m³, größter %,.0f m³ · %d gekentert, %d geschmolzen%s · %d Stöße · %.2f ms je Schritt%s",
                count(), totalVolume(), l == null ? 0 : l.volume, capsized, melted, removed > 0 ? ", " + removed + " entfernt" : "", contacts, stepMs, bf == null ? "" : " · " + bf.describe());
    }
}
