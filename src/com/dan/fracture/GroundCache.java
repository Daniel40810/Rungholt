package com.dan.fracture;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Seit Glacier Phase 10: ein Zwischenspeicher für einen teuren {@link Fracture.Ground}. Der Boden wird in Kacheln von
 * 64 × 64 Zellen einmal abgefragt und dann zwischen den Ecken einer Zelle linear gemischt. Beim Fallen fragt jede Zelle
 * an der Oberfläche jedes Stücks in jedem Schritt den Boden ab; ein Gletscher mit Spalten kostet je Abfrage einige
 * Mikrosekunden, die Kachel nur ein Nachschlagen.
 * <p>
 * Ändert sich der Boden (der Gletscher rückt vor), {@link #clear()} aufrufen. Höchstens {@link #maxTiles} Kacheln; ist
 * der Speicher voll, beginnt er von vorn.
 */
public final class GroundCache implements Fracture.Ground {
    private static final int N = 64;
    private final Fracture.Ground source;
    public final double cell;
    public volatile int maxTiles = 1024;
    private final ConcurrentHashMap<Long, float[]> tiles = new ConcurrentHashMap<>();
    private long hits, misses;

    /** @param cell Zellgröße (m), 1 m genügt für Stöße mit Stücken ab einigen Metern */
    public GroundCache(Fracture.Ground source, double cell) {
        this.source = source;
        this.cell = cell;
    }

    @Override public double heightAt(double x, double z) {
        double u = x / cell, v = z / cell;
        long i = (long) Math.floor(u), j = (long) Math.floor(v);
        double fu = u - i, fv = v - j;
        long ti = Math.floorDiv(i, N), tj = Math.floorDiv(j, N);
        int li = (int) (i - ti * N), lj = (int) (j - tj * N);
        float[] t = tile(ti, tj);
        int k = lj * (N + 1) + li;
        float a = t[k], b = t[k + 1], c = t[k + N + 1], d = t[k + N + 2];
        return (a * (1 - fu) + b * fu) * (1 - fv) + (c * (1 - fu) + d * fu) * fv;
    }

    private float[] tile(long ti, long tj) {
        long key = (ti << 32) ^ (tj & 0xffffffffL);
        float[] t = tiles.get(key);
        if (t != null) { hits++; return t; }
        misses++;
        if (tiles.size() >= maxTiles) tiles.clear();
        t = new float[(N + 1) * (N + 1)];
        for (int b = 0; b <= N; b++)
            for (int a = 0; a <= N; a++) t[b * (N + 1) + a] = (float) source.heightAt((ti * N + a) * cell, (tj * N + b) * cell);
        tiles.put(key, t);
        return t;
    }

    /** Alles vergessen (der Boden hat sich geändert). */
    public void clear() { tiles.clear(); }
    public int tiles() { return tiles.size(); }
    /** Anteil der Abfragen, die eine vorhandene Kachel fanden. */
    public double hitRate() { long h = hits, m = misses; return h + m == 0 ? 0 : (double) h / (h + m); }
}
