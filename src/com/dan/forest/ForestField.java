package com.dan.forest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Ein großer Wald zum Zeichnen auf der Grafikkarte: wo welche Bäume stehen und welche Detailstufe jeder im
 * nächsten Bild bekommt. Die Fläche ist in Kacheln geteilt; jede wird beim ersten Bedarf aus einem festen
 * Startwert bepflanzt (immer gleich) und behalten, solange sie in Reichweite liegt. Wo was wächst, sagt der
 * Benutzer über {@link Density} (je Art 0..1) und {@link Ground}. Je Bild sortiert {@link #update} die Bäume in
 * Eimer nach Art, Variante und Stufe; die Eimer gehen als Instanzen an {@code gl.ForestGl} oder einen eigenen
 * Renderer.
 * <p>
 * Stufen: 0 und 1 wie {@link TreeMesh#build} (Blattbüschel, gröbere Büschel), 2 jedes siebte Büschel doppelt so groß, 3 die Silhouette
 * mit gut 50 Dreiecken, 4 eine sehr grobe Silhouette (unter 40) für die Ferne. Jenseits von {@link #thinFrom}
 * der Reichweite wird ausgedünnt; die verbleibenden Kronen werden dafür breiter, so dass ein ferner Waldrand
 * geschlossen bleibt (seit Haithabu Phase 2b).
 */
public final class ForestField {
    /** Wie dicht Art s an (x, z) wächst: 0 = nie, 1 = so dicht wie der Abstand erlaubt. */
    @FunctionalInterface
    public interface Density { double at(double x, double z, int species); }

    /**
     * Qualitätsstufen (Paketvertrag Punkt 7): Reichweite, Grenze der Stufe 2 und der Stufe 3 (m), Abstand der Plätze.
     */
    public enum Quality {
        LOW(1100, 100, 420, 7.5), MEDIUM(1800, 150, 600, 6.5), HIGH(2500, 220, 760, 6.0), ULTRA(3200, 300, 950, 5.5);
        public final double radius, lod2, lod3, spacing;
        Quality(double radius, double lod2, double lod3, double spacing) { this.radius = radius; this.lod2 = lod2; this.lod3 = lod3; this.spacing = spacing; }
    }

    public static final int LODS = 5;
    /** Je Baum im Eimer: x, y, z, Drehung (rad), Maßstab, Tönung 0..1, Phase (rad), Breite (Faktor). */
    public static final int STRIDE = 8;
    /** Je Baum in einer Kachel: x, y, z, Drehung, Maßstab, Tönung, Art, Variante. */
    static final int TSTRIDE = 8;

    public final int species, variants;
    public double tile = 64, spacing = 6.5;
    /** Reichweite (m), Grenzen der Stufen 0..3 (m; Stufe 4 bis zur Reichweite), Beginn der Ausdünnung (Anteil). */
    public double radius = 1800, thinFrom = 0.4;
    public double[] lod = {12, 30, 220, 600};
    /** Höhe eines Baums mit Maßstab 1 je Art (m) für die Stufenwahl; gesetzt von {@link #setHeights}. */
    private double[] heights;
    private final Density density;
    private final Ground ground;
    private final long seed;

    private final Map<Long, float[]> tiles = new HashMap<>();
    private final Map<Long, float[]> bounds = new HashMap<>();
    public final float[][] bucket;
    public final int[] count;
    public int planted, shown, tilesKept;

    public ForestField(int species, int variants, Density density, Ground ground, long seed) {
        this.species = species;
        this.variants = variants;
        this.density = density;
        this.ground = ground == null ? Ground.FLAT : ground;
        this.seed = seed;
        bucket = new float[buckets()][];
        count = new int[buckets()];
        for (int i = 0; i < bucket.length; i++) bucket[i] = new float[STRIDE * 64];
        heights = new double[species];
        Arrays.fill(heights, 20);
    }

    public int buckets() { return species * variants * LODS; }
    public int bucketOf(int s, int v, int lod) { return (s * variants + v) * LODS + lod; }
    public static int lodOf(int bucket) { return bucket % LODS; }
    public int speciesOf(int bucket) { return bucket / LODS / variants; }
    public int variantOf(int bucket) { return bucket / LODS % variants; }

    /** Stellt Reichweite, Stufen und Abstand ein; ein anderer Abstand bepflanzt neu. */
    public void apply(Quality q) {
        radius = q.radius;
        lod[2] = q.lod2;
        lod[3] = q.lod3;
        if (spacing != q.spacing) { spacing = q.spacing; clear(); }
    }

    /** Höhen der Modelle je Art (m), damit hohe Bäume ihre feine Stufe weiter hinaus behalten. */
    public void setHeights(double... h) { heights = Arrays.copyOf(h, species); }

    /** Alle Kacheln vergessen (etwa nach einer neuen Dichte). */
    public void clear() { tiles.clear(); bounds.clear(); planted = 0; }

    // ------------------------------------------------------------------ Pflanzen

    /** Bepflanzt die Kachel (ti, tj): je Platz eines Gitters mit Abstand {@link #spacing} höchstens ein Baum. */
    float[] plant(int ti, int tj) {
        SplittableRandom r = new SplittableRandom(seed ^ (ti * 73856093L) ^ (tj * 19349663L) ^ 0x5EED_F0E5L);
        double x0 = ti * tile, z0 = tj * tile;
        int n = Math.max(1, (int) Math.round(tile / spacing));
        double sp = tile / n;
        float[] out = new float[TSTRIDE * 32];
        int k = 0;
        double[] d = new double[species];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double x = x0 + (i + 0.1 + 0.8 * r.nextDouble()) * sp, z = z0 + (j + 0.1 + 0.8 * r.nextDouble()) * sp;
                double roll = r.nextDouble(), size = r.nextDouble(), rot = r.nextDouble() * 2 * Math.PI, tint = r.nextDouble();
                int variant = r.nextInt(variants);
                double sum = 0;
                for (int s = 0; s < species; s++) { d[s] = Math.max(0, Math.min(1, density.at(x, z, s))); sum += d[s]; }
                if (sum <= 1e-6) continue;
                if (roll >= Math.min(1, sum)) continue;
                double pick = roll / Math.min(1, sum) * sum;
                int s = 0;
                while (s < species - 1 && pick > d[s]) { pick -= d[s]; s++; }
                // dünne Bestände: einzelne, eher größere Bäume; dichte: gleichmäßig
                double scale = 0.62 + 0.45 * size;
                if (k + TSTRIDE > out.length) out = Arrays.copyOf(out, out.length * 2);
                out[k++] = (float) x;
                out[k++] = ground.height(x, z) - 0.2f * (float) scale;
                out[k++] = (float) z;
                out[k++] = (float) rot;
                out[k++] = (float) scale;
                out[k++] = (float) tint;
                out[k++] = s;
                out[k++] = variant;
            }
        return Arrays.copyOf(out, k);
    }

    private static long key(int i, int j) { return ((long) i << 32) ^ (j & 0xffffffffL); }

    // ------------------------------------------------------------------ Auswahl je Bild

    private final double[][] planes = new double[4][4];

    /**
     * Wählt die Bäume um das Auge (eye) für ein Bild mit Projektion·Blick vp (null: alle im Umkreis, etwa für den
     * Schatten) und füllt die Eimer. Höchstens maxNew Kacheln werden je Aufruf neu bepflanzt, die nächsten zuerst.
     */
    public void update(double[] eye, float[] vp, int maxNew) {
        Arrays.fill(count, 0);
        shown = 0;
        if (radius <= 0) return;
        boolean cull = vp != null;
        if (cull) frustum(vp);
        int r = (int) Math.ceil(radius / tile) + 1;
        int ci = (int) Math.floor(eye[0] / tile), cj = (int) Math.floor(eye[2] / tile);
        List<long[]> missing = new ArrayList<>();
        double rr = (radius + tile) * (radius + tile);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double tx = (i + 0.5) * tile, tz = (j + 0.5) * tile;
                double dx = tx - eye[0], dz = tz - eye[2], d2 = dx * dx + dz * dz;
                if (d2 > rr) continue;
                long kk = key(i, j);
                float[] t = tiles.get(kk);
                if (t == null) { missing.add(new long[]{i, j, (long) d2}); continue; }
                if (t.length == 0) continue;
                float[] b = bounds.get(kk);
                if (cull && !visible(tx, (b[0] + b[1]) * 0.5 + 12, tz, Math.sqrt(tile * tile * 0.5 + Math.pow((b[1] - b[0]) * 0.5 + 20, 2)) + 25)) continue;
                emit(t, eye);
            }
        missing.sort((a, b) -> Long.compare(a[2], b[2]));
        for (int q = 0; q < Math.min(maxNew, missing.size()); q++) {
            int i = (int) missing.get(q)[0], j = (int) missing.get(q)[1];
            float[] t = plant(i, j);
            float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
            for (int k = 0; k < t.length; k += TSTRIDE) { lo = Math.min(lo, t[k + 1]); hi = Math.max(hi, t[k + 1]); }
            if (t.length == 0) lo = hi = 0;
            tiles.put(key(i, j), t);
            bounds.put(key(i, j), new float[]{lo, hi});
            planted += t.length / TSTRIDE;
        }
        if (tiles.size() > 3 * (2 * r + 1) * (2 * r + 1)) {
            double keep = (radius * 1.5) * (radius * 1.5);
            tiles.keySet().removeIf(kk -> {
                int i = (int) (kk >> 32), j = (int) (long) kk;
                double dx = (i + 0.5) * tile - eye[0], dz = (j + 0.5) * tile - eye[2];
                boolean drop = dx * dx + dz * dz > keep;
                if (drop) bounds.remove(kk);
                return drop;
            });
        }
        tilesKept = tiles.size();
    }

    /** Sind alle Kacheln im Umkreis bepflanzt (für Standbilder)? */
    public boolean complete(double[] eye) {
        int r = (int) Math.ceil(radius / tile) + 1;
        int ci = (int) Math.floor(eye[0] / tile), cj = (int) Math.floor(eye[2] / tile);
        double rr = (radius + tile) * (radius + tile);
        for (int j = cj - r; j <= cj + r; j++)
            for (int i = ci - r; i <= ci + r; i++) {
                double dx = (i + 0.5) * tile - eye[0], dz = (j + 0.5) * tile - eye[2];
                if (dx * dx + dz * dz <= rr && !tiles.containsKey(key(i, j))) return false;
            }
        return true;
    }

    /** Bepflanzt alle fehlenden Kacheln im Umkreis auf einmal. */
    public void completeNow(double[] eye) { while (!complete(eye)) update(eye, null, 4096); }

    private void emit(float[] t, double[] eye) {
        double thin0 = radius * thinFrom;
        for (int k = 0; k < t.length; k += TSTRIDE) {
            double dx = t[k] - eye[0], dy = t[k + 1] + 8 - eye[1], dz = t[k + 2] - eye[2];
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > radius) continue;
            float scale = t[k + 4];
            float widen = 1;
            if (d > thin0) {
                // behalten mit p; die Krone der Verbleibenden wächst mit 1/√p (höchstens 1,7)
                double p = 1 - smooth(thin0, radius, d) * 0.75;
                if (hash(t[k], t[k + 2]) > p) continue;
                widen = (float) Math.min(1.7, 1 / Math.sqrt(p));
            }
            int s = (int) t[k + 6], v = (int) t[k + 7];
            double hf = heights[s] * scale / 20.0;
            double e = d / Math.max(0.6, hf);
            int l = e < lod[0] ? 0 : e < lod[1] ? 1 : d < lod[2] ? 2 : d < lod[3] ? 3 : 4;
            int b = bucketOf(s, v, l);
            float[] o = bucket[b];
            int c = count[b];
            if (STRIDE * c + STRIDE > o.length) o = bucket[b] = Arrays.copyOf(o, o.length * 2);
            int q = STRIDE * c;
            o[q] = t[k]; o[q + 1] = t[k + 1]; o[q + 2] = t[k + 2]; o[q + 3] = t[k + 3];
            o[q + 4] = scale; o[q + 5] = t[k + 5]; o[q + 6] = (float) (hash(t[k + 2], t[k]) * 2 * Math.PI); o[q + 7] = widen;
            count[b] = c + 1;
            shown++;
        }
    }

    /** Anzahl Bäume je Stufe im letzten {@link #update}. */
    public int[] lodCounts() {
        int[] n = new int[LODS];
        for (int b = 0; b < count.length; b++) n[lodOf(b)] += count[b];
        return n;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double hash(double x, double z) {
        long h = Double.doubleToLongBits(x * 12.9898 + z * 78.233);
        h ^= h >>> 33; h *= 0xff51afd7ed558ccdL; h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private void frustum(float[] m) {
        // Spaltenweise abgelegte Matrix (OpenGL): Zeile r ist m[c*4+r]; links, rechts, unten, oben
        for (int p = 0; p < 4; p++) {
            int row = p >> 1;
            double s = (p & 1) == 0 ? 1 : -1;
            double[] q = planes[p];
            for (int c = 0; c < 4; c++) q[c] = m[c * 4 + 3] + s * m[c * 4 + row];
            double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2]);
            for (int c = 0; c < 4; c++) q[c] /= l;
        }
    }

    private boolean visible(double x, double y, double z, double rad) {
        for (double[] q : planes) if (q[0] * x + q[1] * y + q[2] * z + q[3] < -rad) return false;
        return true;
    }
}
