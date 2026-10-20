package com.dan.ice;

/**
 * Die Risse im Eis als Zellen (Worley-Rauschen in 3D): Jede Zelle ist ein Kristallkorn oder ein Block zwischen alten
 * Rissen, die Grenzen zweier Zellen sind die Rissflächen. Eine Kachel aus {@link #N}³ Punkten mit {@link #CELLS}³
 * Zellen, an allen Rändern nahtlos. Je Punkt vier Bytes:
 * <ul>
 * <li>R: Nähe zur nächsten Rissfläche (255 auf der Fläche, 0 ab 15 % einer Zelle Abstand);</li>
 * <li>G, B, A: die Normale dieser Rissfläche (Richtung vom nächsten zum zweitnächsten Kern, 0..255 für −1..1). An ihr
 * spiegelt sich die Sonne – mit Totalreflexion, wenn der Riss Luft enthält.</li>
 * </ul>
 * {@code gl/IceGl} lädt dieselben Bytes als 3D-Textur; {@link #sample} gibt sie ohne Grafikkarte.
 */
public final class IceNoise {
    /** Punkte je Kante der Kachel und Zellen je Kante. */
    public static final int N = 64, CELLS = 8;
    /** Breite der Rissfläche als Anteil einer Zelle. */
    public static final double EDGE = 0.15;

    private IceNoise() { }

    private static final java.util.Map<Long, byte[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    /** Die Kachel für einen Startwert (zwischengespeichert). */
    public static byte[] cracks(long seed) { return CACHE.computeIfAbsent(seed, IceNoise::build); }

    static double rnd(int x, int y, int z, long seed, int k) {
        long h = x * 374761393L + y * 668265263L + z * 1274126177L + seed * 1442695041L + k * 2246822519L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0x1000000;
    }

    private static byte[] build(long seed) {
        int c = CELLS;
        double[][] kern = new double[c * c * c][3];
        for (int z = 0; z < c; z++)
            for (int y = 0; y < c; y++)
                for (int x = 0; x < c; x++) {
                    int i = (z * c + y) * c + x;
                    kern[i][0] = x + 0.1 + 0.8 * rnd(x, y, z, seed, 0);
                    kern[i][1] = y + 0.1 + 0.8 * rnd(x, y, z, seed, 1);
                    kern[i][2] = z + 0.1 + 0.8 * rnd(x, y, z, seed, 2);
                }
        byte[] out = new byte[N * N * N * 4];
        double cs = (double) N / c;
        for (int z = 0; z < N; z++)
            for (int y = 0; y < N; y++)
                for (int x = 0; x < N; x++) {
                    double px = (x + 0.5) / cs, py = (y + 0.5) / cs, pz = (z + 0.5) / cs;
                    int cx = (int) Math.floor(px), cy = (int) Math.floor(py), cz = (int) Math.floor(pz);
                    double f1 = 1e9, f2 = 1e9;
                    double[] a = null, b = null;
                    for (int dz = -1; dz <= 1; dz++)
                        for (int dy = -1; dy <= 1; dy++)
                            for (int dx = -1; dx <= 1; dx++) {
                                int ix = cx + dx, iy = cy + dy, iz = cz + dz;
                                int wx = Math.floorMod(ix, c), wy = Math.floorMod(iy, c), wz = Math.floorMod(iz, c);
                                double[] k = kern[(wz * c + wy) * c + wx];
                                double kx = k[0] + (ix - wx), ky = k[1] + (iy - wy), kz = k[2] + (iz - wz);
                                double d = Math.sqrt((kx - px) * (kx - px) + (ky - py) * (ky - py) + (kz - pz) * (kz - pz));
                                double[] kp = {kx, ky, kz};
                                if (d < f1) { f2 = f1; b = a; f1 = d; a = kp; }
                                else if (d < f2) { f2 = d; b = kp; }
                            }
                    // Abstand zur Mittelebene zwischen den zwei nächsten Kernen
                    double nx = b[0] - a[0], ny = b[1] - a[1], nz = b[2] - a[2], nl = Math.sqrt(nx * nx + ny * ny + nz * nz);
                    nx /= nl; ny /= nl; nz /= nl;
                    double mx = (a[0] + b[0]) / 2, my = (a[1] + b[1]) / 2, mz = (a[2] + b[2]) / 2;
                    double dist = Math.abs((px - mx) * nx + (py - my) * ny + (pz - mz) * nz);
                    double edge = Math.max(0, 1 - dist / EDGE);
                    int o = ((z * N + y) * N + x) * 4;
                    out[o] = (byte) (int) Math.round(edge * edge * 255);
                    out[o + 1] = (byte) (int) Math.round((nx * 0.5 + 0.5) * 255);
                    out[o + 2] = (byte) (int) Math.round((ny * 0.5 + 0.5) * 255);
                    out[o + 3] = (byte) (int) Math.round((nz * 0.5 + 0.5) * 255);
                }
        return out;
    }

    /** Wert der Kachel am Punkt p (in Kacheln; nächster Punkt, nahtlos): {Nähe 0..1, nx, ny, nz}. */
    public static double[] sample(byte[] t, double x, double y, double z) {
        int ix = Math.floorMod((int) Math.floor(x * N), N), iy = Math.floorMod((int) Math.floor(y * N), N), iz = Math.floorMod((int) Math.floor(z * N), N);
        int o = ((iz * N + iy) * N + ix) * 4;
        return new double[]{(t[o] & 255) / 255.0, (t[o + 1] & 255) / 127.5 - 1, (t[o + 2] & 255) / 127.5 - 1, (t[o + 3] & 255) / 127.5 - 1};
    }
}
