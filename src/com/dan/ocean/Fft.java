package com.dan.ocean;

/**
 * Schnelle Fourier-Transformation für Quadrate der Kantenlänge 2<sup>n</sup>, komplex, an Ort und Stelle
 * (Radix 2, Bit-Umkehr). Nur für den Prozessor-Teil des Ozeans; die Grafikkarte rechnet dieselbe Transformation
 * im Compute-Shader. Vorzeichen: invers = +i, ohne Normierung, also f(x) = Σ F(k)·e^{+ikx}.
 */
public final class Fft {
    private Fft() { }

    /** Drehfaktoren je Länge (cos, sin von 2π·k/n), einmal gerechnet. */
    private static final java.util.Map<Integer, double[][]> TWIDDLE = new java.util.concurrent.ConcurrentHashMap<>();

    private static double[][] twiddle(int n) {
        return TWIDDLE.computeIfAbsent(n, m -> {
            double[][] t = new double[2][m / 2];
            for (int k = 0; k < m / 2; k++) { t[0][k] = Math.cos(2 * Math.PI * k / m); t[1][k] = Math.sin(2 * Math.PI * k / m); }
            return t;
        });
    }

    /** 1D an Ort und Stelle auf zusammenhängenden Werten ab off, n Werte. */
    static void transform(double[] re, double[] im, int off, int n, boolean inverse) {
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                int a = off + i, b = off + j;
                double t = re[a]; re[a] = re[b]; re[b] = t;
                t = im[a]; im[a] = im[b]; im[b] = t;
            }
        }
        double[][] tw = twiddle(n);
        double sign = inverse ? 1 : -1;
        for (int len = 2; len <= n; len <<= 1) {
            int half = len >> 1, step = n / len;
            for (int i = 0; i < n; i += len)
                for (int k = 0; k < half; k++) {
                    double cr = tw[0][k * step], ci = sign * tw[1][k * step];
                    int a = off + i + k, b = a + half;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                }
        }
    }

    /** 2D an Ort und Stelle, n × n, Zeilen zuerst (Index = z·n + x); Spalten über einen Zwischenspeicher. */
    public static void transform2d(double[] re, double[] im, int n, boolean inverse) {
        if (Integer.bitCount(n) != 1) throw new IllegalArgumentException("n muss eine Zweierpotenz sein: " + n);
        for (int z = 0; z < n; z++) transform(re, im, z * n, n, inverse);
        double[] cr = new double[n * n], ci = new double[n * n];
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) { cr[x * n + z] = re[z * n + x]; ci[x * n + z] = im[z * n + x]; }
        for (int x = 0; x < n; x++) transform(cr, ci, x * n, n, inverse);
        for (int z = 0; z < n; z++)
            for (int x = 0; x < n; x++) { re[z * n + x] = cr[x * n + z]; im[z * n + x] = ci[x * n + z]; }
    }
}
