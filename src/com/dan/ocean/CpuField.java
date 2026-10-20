package com.dan.ocean;

import java.util.stream.IntStream;

/**
 * Die See auf dem Prozessor, für Abfragen ohne Grafikkarte ({@link Ocean#heightAt} und Verwandte). Je Kaskade
 * wird der langwellige Teil desselben Spektrums, das die Grafikkarte rechnet, auf ein kleines Gitter transformiert
 * (Kaskade 0 mit 128², Kaskade 1 mit 64², die feineren tragen zur Höhe kaum bei) und dazwischen mit Catmull-Rom
 * gelesen. Felder: Auslenkung x, y, z, Geschwindigkeit x, y, z, Steigung ∂h/∂x, ∂h/∂z, Dehnung ∂dx/∂x, ∂dz/∂z (für den Schaum).
 */
final class CpuField {
    static final int[] GRID = {128, 64};
    static final int FIELDS = 10, PAIRS = 5;
    /** Welche Felder ein Paar füllt (Realteil, Imaginärteil). */
    static final int[] FIRST = {0, 2, 3, 6, 8}, SECOND = {1, 4, 5, 7, 9};
    /** Zu welchem Paar ein Feld gehört. */
    static final int[] PAIR_OF = {0, 0, 1, 2, 1, 2, 3, 3, 4, 4};
    /** Höchste Mode je Kaskade auf dem Prozessor (mindestens drei Gitterpunkte je Wellenlänge). */
    static final int[] MAX_MODE = {42, 21};

    final int cascades;
    final int[] m;
    final double[] length;
    /** Modenlisten je Kaskade: Gitterindex, kx, kz, k, ωq, h₀ re/im, conj(h₀(−k)) re/im. */
    final int[][] at;
    final double[][] kx, kz, kk, om, ar, ai, br, bi;
    /** Felder je Kaskade: [Feld][z·m + x], Feld 0..9 = dx, dy, dz, vx, vy, vz, sx, sz, jx, jz. */
    final float[][][] field;
    /** Zeit, für die jedes Feldpaar zuletzt gerechnet wurde (gerechnet wird nur, was eine Abfrage braucht). */
    final double[] time = {Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
    /** Masken der Paare: Höhe braucht 0 (dx, dy) und 1 (dz, vy), alles braucht 0..4. */
    static final int HEIGHT = 0b00011, ALL = 0b11111;
    final double chop;
    /** cos und sin der Phase je Mode zur Zeit trigTime (für alle Paare gemeinsam). */
    final double[][] cos, sin;
    double trigTime = Double.NaN;

    CpuField(java.util.List<Spectrum.Cascade> cs, double depth, double chop) {
        this.chop = chop;
        cascades = Math.min(GRID.length, cs.size());
        m = new int[cascades];
        length = new double[cascades];
        at = new int[cascades][];
        kx = new double[cascades][]; kz = new double[cascades][]; kk = new double[cascades][]; om = new double[cascades][];
        ar = new double[cascades][]; ai = new double[cascades][]; br = new double[cascades][]; bi = new double[cascades][];
        field = new float[cascades][FIELDS][];
        cos = new double[cascades][];
        sin = new double[cascades][];
        for (int c = 0; c < cascades; c++) {
            Spectrum.Cascade sc = cs.get(c);
            int mm = GRID[c], n = sc.n, lim = Math.min(MAX_MODE[c], n / 2 - 1);
            m[c] = mm;
            length[c] = sc.length;
            java.util.List<int[]> sel = new java.util.ArrayList<>();
            for (int iz = 0; iz < n; iz++)
                for (int ix = 0; ix < n; ix++) {
                    int wx = sc.wave(ix), wz = sc.wave(iz);
                    if (Math.abs(wx) > lim || Math.abs(wz) > lim) continue;
                    int t = (iz * n + ix) * 4;
                    if (sc.h0[t] == 0 && sc.h0[t + 1] == 0 && sc.h0[t + 2] == 0 && sc.h0[t + 3] == 0) continue;
                    sel.add(new int[]{ix, iz, wx, wz});
                }
            int s = sel.size();
            at[c] = new int[s];
            kx[c] = new double[s]; kz[c] = new double[s]; kk[c] = new double[s]; om[c] = new double[s];
            ar[c] = new double[s]; ai[c] = new double[s]; br[c] = new double[s]; bi[c] = new double[s];
            double dk = 2 * Math.PI / sc.length;
            for (int i = 0; i < s; i++) {
                int[] e = sel.get(i);
                int t = (e[1] * n + e[0]) * 4;
                int gx = ((e[2] % mm) + mm) % mm, gz = ((e[3] % mm) + mm) % mm;
                at[c][i] = gz * mm + gx;
                kx[c][i] = e[2] * dk;
                kz[c][i] = e[3] * dk;
                kk[c][i] = Math.hypot(kx[c][i], kz[c][i]);
                om[c][i] = Spectrum.omegaQ(kk[c][i], depth);
                ar[c][i] = sc.h0[t]; ai[c][i] = sc.h0[t + 1]; br[c][i] = sc.h0[t + 2]; bi[c][i] = sc.h0[t + 3];
            }
            for (int f = 0; f < FIELDS; f++) field[c][f] = new float[mm * mm];
            cos[c] = new double[s];
            sin[c] = new double[s];
        }
    }

    /** Rechnet die Feldpaare der Maske zur Zeit t (Phase wie im Shader: ωq · (t mod T)), soweit noch nicht geschehen. */
    synchronized void update(double t, int mask) {
        int need = 0;
        for (int p = 0; p < PAIRS; p++) if ((mask & (1 << p)) != 0 && time[p] != t) need |= 1 << p;
        if (need == 0) return;
        double tt = t % Spectrum.REPEAT;
        if (trigTime != t) {
            IntStream.range(0, cascades).parallel().forEach(c -> {
                for (int i = 0; i < om[c].length; i++) {
                    double ph = om[c][i] * tt;
                    cos[c][i] = Math.cos(ph);
                    sin[c][i] = Math.sin(ph);
                }
            });
            trigTime = t;
        }
        final int todo = need;
        IntStream.range(0, cascades * PAIRS).filter(job -> (todo & (1 << (job % PAIRS))) != 0).parallel()
                .forEach(job -> transform(job / PAIRS, job % PAIRS, tt));
        for (int p = 0; p < PAIRS; p++) if ((need & (1 << p)) != 0) time[p] = t;
    }

    /** Ein komplexes Feldpaar: 0 = dx + i·dy, 1 = dz + i·vy, 2 = vx + i·vz, 3 = sx + i·sz, 4 = jx + i·jz. */
    private void transform(int c, int pair, double t) {
        int mm = m[c];
        double[] re = new double[mm * mm], im = new double[mm * mm];
        int s = at[c].length;
        for (int i = 0; i < s; i++) {
            double co = cos[c][i], si = sin[c][i];
            // h = h0·e^{iωt} + conj(h0(−k))·e^{−iωt}
            double hr = ar[c][i] * co - ai[c][i] * si + br[c][i] * co + bi[c][i] * si;
            double hi = ar[c][i] * si + ai[c][i] * co - br[c][i] * si + bi[c][i] * co;
            // ∂h/∂t = iω·(h0·e^{iωt} − conj(h0(−k))·e^{−iωt})
            double dr = ar[c][i] * co - ai[c][i] * si - (br[c][i] * co + bi[c][i] * si);
            double di = ar[c][i] * si + ai[c][i] * co - (-br[c][i] * si + bi[c][i] * co);
            double w = om[c][i], vr = -w * di, vi = w * dr;
            double k = kk[c][i], fx = kx[c][i] / k * chop, fz = kz[c][i] / k * chop;
            // i·h und i·v
            double ihr = -hi, ihi = hr, ivr = -vi, ivi = vr;
            double f1r, f1i, f2r, f2i;
            switch (pair) {
                case 0 -> { f1r = fx * ihr; f1i = fx * ihi; f2r = hr; f2i = hi; }           // dx, dy
                case 1 -> { f1r = fz * ihr; f1i = fz * ihi; f2r = vr; f2i = vi; }           // dz, vy
                case 2 -> { f1r = fx * ivr; f1i = fx * ivi; f2r = fz * ivr; f2i = fz * ivi; } // vx, vz
                case 3 -> { f1r = kx[c][i] * ihr; f1i = kx[c][i] * ihi; f2r = kz[c][i] * ihr; f2i = kz[c][i] * ihi; } // sx, sz
                default -> { f1r = -kx[c][i] * fx * hr; f1i = -kx[c][i] * fx * hi; f2r = -kz[c][i] * fz * hr; f2i = -kz[c][i] * fz * hi; } // jx, jz
            }
            // F1 + i·F2
            int g = at[c][i];
            re[g] += f1r - f2i;
            im[g] += f1i + f2r;
        }
        Fft.transform2d(re, im, mm, true);
        float[] a = field[c][FIRST[pair]], b = field[c][SECOND[pair]];
        for (int i = 0; i < mm * mm; i++) { a[i] = (float) re[i]; b[i] = (float) im[i]; }
    }

    /** Summe aller Kaskaden am Ruhepunkt (x0, z0): out[0..9] wie die Felder (addiert), nur die Paare der Maske. */
    void sample(double x0, double z0, double[] out, int mask) {
        for (int c = 0; c < cascades; c++) {
            int mm = m[c];
            double u = x0 / length[c] * mm, v = z0 / length[c] * mm;
            int iu = (int) Math.floor(u), iv = (int) Math.floor(v);
            double fu = u - iu, fv = v - iv;
            double[] wu = cr(fu), wv = cr(fv);
            for (int f = 0; f < FIELDS; f++) {
                if ((mask & (1 << PAIR_OF[f])) == 0) continue;
                float[] a = field[c][f];
                double s = 0;
                for (int j = 0; j < 4; j++) {
                    int row = Math.floorMod(iv - 1 + j, mm) * mm;
                    double r = 0;
                    for (int i = 0; i < 4; i++) r += wu[i] * a[row + Math.floorMod(iu - 1 + i, mm)];
                    s += wv[j] * r;
                }
                out[f] += s;
            }
        }
    }

    /** Gewichte nach Catmull-Rom für die Punkte −1, 0, 1, 2. */
    static double[] cr(double t) {
        double t2 = t * t, t3 = t2 * t;
        return new double[]{-0.5 * t3 + t2 - 0.5 * t, 1.5 * t3 - 2.5 * t2 + 1, -1.5 * t3 + 2 * t2 + 0.5 * t, 0.5 * t3 - 0.5 * t2};
    }
}
