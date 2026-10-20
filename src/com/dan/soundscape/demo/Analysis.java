package com.dan.soundscape.demo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Auswertung für Demo und Prüfung: FFT, Spektrum, Spitze, Schwerpunkt, Pegel, Spektrogramm als Bild. */
public final class Analysis {
    private Analysis() { }

    /** FFT in place (Länge Zweierpotenz). */
    public static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double a = -2 * Math.PI / len;
            for (int i = 0; i < n; i += len)
                for (int k = 0; k < len / 2; k++) {
                    double c = Math.cos(a * k), s = Math.sin(a * k);
                    int p = i + k, q = i + k + len / 2;
                    double tr = re[q] * c - im[q] * s, ti = re[q] * s + im[q] * c;
                    re[q] = re[p] - tr; im[q] = im[p] - ti;
                    re[p] += tr; im[p] += ti;
                }
        }
    }

    /** Mono aus verschränktem Stereo, ab Sekunde t0, Dauer d. */
    public static double[] mono(float[] st, int sr, double t0, double d) {
        int a = (int) (t0 * sr), n = (int) (d * sr);
        double[] m = new double[n];
        for (int i = 0; i < n && 2 * (a + i) + 1 < st.length; i++) m[i] = 0.5 * (st[2 * (a + i)] + st[2 * (a + i) + 1]);
        return m;
    }

    /** Leistungsspektrum (gemittelt über Fenster der Länge n mit Hann), Index k ↔ k · sr / n. */
    public static double[] spectrum(double[] x, int n) {
        double[] p = new double[n / 2];
        int cnt = 0;
        for (int s = 0; s + n <= x.length; s += n / 2) {
            double[] re = new double[n], im = new double[n];
            for (int i = 0; i < n; i++) re[i] = x[s + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            fft(re, im);
            for (int k = 0; k < n / 2; k++) p[k] += re[k] * re[k] + im[k] * im[k];
            cnt++;
        }
        for (int k = 0; k < p.length; k++) p[k] /= Math.max(1, cnt);
        return p;
    }

    /** Frequenz der stärksten Linie zwischen f0 und f1 (Hz). */
    public static double peak(double[] p, int sr, int n, double f0, double f1) {
        int k0 = Math.max(1, (int) (f0 * n / sr)), k1 = Math.min(p.length - 2, (int) (f1 * n / sr));
        int best = k0;
        for (int k = k0; k <= k1; k++) if (p[k] > p[best]) best = k;
        // parabolische Verfeinerung
        double a = Math.log(p[best - 1] + 1e-30), b = Math.log(p[best] + 1e-30), c = Math.log(p[best + 1] + 1e-30);
        double off = 0.5 * (a - c) / (a - 2 * b + c);
        return (best + (Double.isFinite(off) ? off : 0)) * sr / (double) n;
    }

    /** Spektraler Schwerpunkt (Hz). */
    public static double centroid(double[] p, int sr, int n) {
        double s = 0, w = 0;
        for (int k = 1; k < p.length; k++) { s += p[k] * k; w += p[k]; }
        return w > 0 ? s / w * sr / n : 0;
    }

    /** Effektivwert eines Abschnitts (beide Kanäle). */
    public static double rms(float[] st, int sr, double t0, double d) {
        int a = (int) (t0 * sr) * 2, b = Math.min(st.length, a + (int) (d * sr) * 2);
        double s = 0;
        for (int i = a; i < b; i++) s += st[i] * st[i];
        return Math.sqrt(s / Math.max(1, b - a));
    }

    /** Effektivwert eines Kanals (0 links, 1 rechts). */
    public static double rms(float[] st, int sr, double t0, double d, int ch) {
        int a = (int) (t0 * sr), b = Math.min(st.length / 2, a + (int) (d * sr));
        double s = 0;
        for (int i = a; i < b; i++) s += st[2 * i + ch] * st[2 * i + ch];
        return Math.sqrt(s / Math.max(1, b - a));
    }

    /** Erster Zeitpunkt (s) ab t0, an dem der Pegel über 20 ms die Schwelle (Effektivwert) übersteigt. */
    public static double onset(float[] st, int sr, double t0, double threshold) {
        int w = (int) (0.02 * sr);
        for (int i = (int) (t0 * sr); 2 * (i + w) < st.length; i += w / 4) {
            double s = 0;
            for (int k = 0; k < w; k++) { double v = 0.5 * (st[2 * (i + k)] + st[2 * (i + k) + 1]); s += v * v; }
            if (Math.sqrt(s / w) > threshold) return i / (double) sr;
        }
        return Double.NaN;
    }

    /** Spektrogramm (logarithmische Frequenzachse 30 Hz bis 16 kHz) in ein Rechteck. */
    public static void spectrogram(Graphics2D g, float[] st, int sr, int x, int y, int w, int h, double dbFloor) {
        int n = 2048;
        double[] mono = mono(st, sr, 0, st.length / 2.0 / sr);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double fMin = 30, fMax = Math.min(16_000, sr / 2.0);
        for (int c = 0; c < w; c++) {
            int s = (int) ((long) c * Math.max(0, mono.length - n) / Math.max(1, w - 1));
            double[] re = new double[n], im = new double[n];
            for (int i = 0; i < n && s + i < mono.length; i++) re[i] = mono[s + i] * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1)));
            fft(re, im);
            for (int r = 0; r < h; r++) {
                double f = fMin * Math.pow(fMax / fMin, (h - 1 - r) / (double) (h - 1));
                int k = Math.min(n / 2 - 1, (int) (f * n / sr));
                double p = re[k] * re[k] + im[k] * im[k];
                double db = 10 * Math.log10(p / n + 1e-20) - dbFloor;
                img.setRGB(c, r, heat(Math.max(0, Math.min(1, db / 70))).getRGB());
            }
        }
        g.drawImage(img, x, y, null);
    }

    /** Farbskala dunkelblau – türkis – gelb – weiß. */
    public static Color heat(double v) {
        double[][] st = {{0.02, 0.03, 0.06}, {0.05, 0.2, 0.4}, {0.1, 0.55, 0.6}, {0.95, 0.75, 0.2}, {1, 1, 0.92}};
        double p = v * (st.length - 1);
        int i = Math.min(st.length - 2, (int) p);
        double t = p - i;
        float[] c = new float[3];
        for (int k = 0; k < 3; k++) c[k] = (float) (st[i][k] + (st[i + 1][k] - st[i][k]) * t);
        return new Color(c[0], c[1], c[2]);
    }
}
