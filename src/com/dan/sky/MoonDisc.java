package com.dan.sky;

import com.dan.sky.astro.FMoonSurface;

/**
 * Die Mondscheibe: Oberfläche aus FMoon (17 Maria, Strahlenkrater, Rauschen; gerechnet, keine Bilddatei),
 * Beleuchtung nach Lommel-Seeliger mit etwas Lambert wie in FMoon (der volle Mond wird zum Rand hin nicht dunkler),
 * Libration und Erdschein. Die Helligkeit wird so geeicht, dass die ganze Scheibe genau die Bestrahlung bringt, die
 * die Helligkeitsformel des Monds verlangt; so stimmen Sichel und Vollmond untereinander und mit dem Mondlicht auf
 * dem Wasser.
 * <p>
 * Scheibenkoordinaten: x nach Westen am Himmel (zu Mare Crisium), y zum Nordpol des Monds, z zum Betrachter.
 */
public final class MoonDisc {
    private MoonDisc() { }

    public static final int TEX_W = 512, TEX_H = 256;
    private static volatile float[] albedo;

    /** Relatives Albedo (1 = Hochland) als Tabelle, selenografische Länge −180..180 über die Breite, Breite −90..90. */
    public static float[] albedoTable() {
        float[] a = albedo;
        if (a == null) {
            synchronized (MoonDisc.class) {
                if (albedo == null) {
                    FMoonSurface s = new FMoonSurface();
                    float[] t = new float[TEX_W * TEX_H];
                    for (int j = 0; j < TEX_H; j++)
                        for (int i = 0; i < TEX_W; i++)
                            t[j * TEX_W + i] = (float) s.albedoAt((i + 0.5) / TEX_W * 360 - 180, (j + 0.5) / TEX_H * 180 - 90);
                    albedo = t;
                }
                a = albedo;
            }
        }
        return a;
    }

    /** Albedo bilinear aus der Tabelle (Länge umlaufend). */
    static double albedoAt(float[] t, double lonDeg, double latDeg) {
        double u = ((lonDeg + 180) / 360 * TEX_W - 0.5), v = Math.max(0, Math.min(TEX_H - 1.001, (latDeg + 90) / 180 * TEX_H - 0.5));
        int i0 = (int) Math.floor(u), j0 = (int) v;
        double fx = u - i0, fy = v - j0;
        int a = Math.floorMod(i0, TEX_W), b = Math.floorMod(i0 + 1, TEX_W);
        double p = t[j0 * TEX_W + a] + (t[j0 * TEX_W + b] - t[j0 * TEX_W + a]) * fx;
        double q = t[(j0 + 1) * TEX_W + a] + (t[(j0 + 1) * TEX_W + b] - t[(j0 + 1) * TEX_W + a]) * fx;
        return p + (q - p) * fy;
    }

    /** Beleuchtung eines Flächenstücks: 86 % Lommel-Seeliger, 14 % Lambert (wie FMoonShader). */
    public static double shade(double cosI, double cosE) {
        if (cosI <= 0) return 0;
        return 0.86 * cosI / (cosI + Math.max(1e-4, cosE)) * 2 + 0.14 * cosI;
    }

    /** Die drei Achsen der Scheibe in der Szene: x (West am Himmel), y (Nord des Monds), z (zum Betrachter). */
    public static double[][] frame(double[] moonDir, double[] moonNorth) {
        double[] z = {-moonDir[0], -moonDir[1], -moonDir[2]};
        double dn = moonNorth[0] * z[0] + moonNorth[1] * z[1] + moonNorth[2] * z[2];
        double[] y = Sky.normalize(new double[]{moonNorth[0] - z[0] * dn, moonNorth[1] - z[1] * dn, moonNorth[2] - z[2] * dn});
        double[] x = {y[1] * z[2] - y[2] * z[1], y[2] * z[0] - y[0] * z[2], y[0] * z[1] - y[1] * z[0]};
        return new double[][]{x, y, z};
    }

    /**
     * Eichung für den Zustand: Faktor je Kanal (Strahldichte = Faktor · Albedo · shade), höchste Strahldichte auf
     * der Scheibe (für die Anzeige) und Erdschein (Strahldichte je Einheit Albedo auf der Nachtseite).
     * Ergebnis: {kR, kG, kB, peak, esR, esG, esB}.
     */
    public static double[] calibrate(Sky.State s) {
        float[] t = albedoTable();
        double[][] f = frame(s.moonDirTrue, s.moonNorth);
        double[] L = {Scattering.dot(s.moonLight, f[0]), Scattering.dot(s.moonLight, f[1]), Scattering.dot(s.moonLight, f[2])};
        int n = 64;
        double sum = 0, peak = 0;
        int count = 0;
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double qx = (i + 0.5) / n * 2 - 1, qy = (j + 0.5) / n * 2 - 1, q2 = qx * qx + qy * qy;
                if (q2 >= 1) continue;
                double nz = Math.sqrt(1 - q2);
                double v = shade(qx * L[0] + qy * L[1] + nz * L[2], nz) * albedoAt(t, lon(qx, nz, s), lat(qy, s));
                sum += v;
                peak = Math.max(peak, v);
                count++;
            }
        double mean = sum / count;
        double r = Math.toRadians(s.moonRadiusDeg), omega = 2 * Math.PI * (1 - Math.cos(r));
        double[] k = new double[3];
        for (int c = 0; c < 3; c++) k[c] = mean > 1e-9 ? s.moonToa[c] / (omega * mean) : 0;
        // Erdschein: die Erde steht vom Mond aus in der Gegenphase; voller Erdschein rund 1/5000 des Vollmonds
        double earthPhase = (1 - Math.cos(Math.toRadians(s.moonPhaseAngle))) / 2;
        double full = Sky.SUN_TOA * Math.pow(10, -0.4 * (-12.73 - Sky.SUN_MAG)) / omega;
        double es = full * 2e-4 * earthPhase;
        return new double[]{k[0], k[1], k[2], Sky.lum(k) * peak, es * 0.95, es, es * 1.08};
    }

    /** Selenografische Länge und Breite eines Scheibenpunkts (mit Libration). */
    static double lon(double qx, double nz, Sky.State s) { return Math.toDegrees(Math.atan2(qx, nz)) + s.librationLon; }

    static double lat(double qy, Sky.State s) { return Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, qy)))) + s.librationLat; }
}
