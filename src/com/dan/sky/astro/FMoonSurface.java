package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Das Albedo der Mondoberflaeche als Funktion der selenografischen Laenge und
 * Breite - ohne Bilddatei, rein gerechnet.
 *
 * <p>Aufgebaut ist sie aus drei Lagen: den Maria als weiche Ellipsen an ihren
 * tatsaechlichen Koordinaten, einigen hellen Strahlenkratern (Tycho,
 * Kopernikus, Aristarch) und einem feinen Rauschen, das die Hochlaender
 * fleckig macht. Die Lage der Maria stammt aus der selenografischen
 * Gradnetzangabe der jeweiligen Formation; ihre Umrisse sind vereinfacht.</p>
 *
 * <p>Warum keine Textur aus einer Datei: Die Bibliothek soll als einzelne JAR
 * ausliefern und in der NetBeans-Palette erscheinen. Eine Bilddatei mit
 * ausreichender Aufloesung waere groesser als der ganze uebrige Code, und die
 * Rechenvorschrift laesst sich an jeder Stelle beliebig fein abtasten - beim
 * Zoom auf die Kugel entstehen keine Klotzchen.</p>
 *
 * @author Claude fuer Daniel
 */
public final class FMoonSurface {

    /**
     * Ein Mare: selenografische Laenge und Breite des Mittelpunkts, die beiden
     * Halbachsen in Grad, der Drehwinkel und die Verdunkelung.
     */
    private static final double[][] MARIA = {
        // lon,  lat,  aLon, aLat, dreh, staerke
        {-57.0, 20.0, 26.0, 30.0, 8.0, 0.50},    // Oceanus Procellarum
        {-45.0, -8.0, 16.0, 16.0, 0.0, 0.52},    // Procellarum, suedlicher Teil
        {-16.0, 33.0, 18.0, 14.0, 0.0, 0.50},    // Mare Imbrium
        {18.0, 27.0, 12.0, 10.0, -25.0, 0.50},   // Mare Serenitatis
        {31.0, 8.0, 13.0, 11.0, 30.0, 0.52},     // Mare Tranquillitatis
        {59.0, 17.0, 8.0, 6.0, 0.0, 0.50},       // Mare Crisium
        {52.0, -8.0, 8.0, 10.0, 0.0, 0.54},      // Mare Fecunditatis
        {34.0, -15.0, 7.0, 6.0, 20.0, 0.56},     // Mare Nectaris
        {-39.0, -24.0, 7.0, 6.0, 0.0, 0.55},     // Mare Humorum
        {-17.0, -21.0, 13.0, 7.0, 0.0, 0.58},    // Mare Nubium
        {-8.0, -13.0, 8.0, 5.0, 15.0, 0.62},     // Mare Cognitum/Insularum
        {1.0, 55.0, 42.0, 4.5, 0.0, 0.62},       // Mare Frigoris
        {4.0, 13.0, 6.0, 4.0, 0.0, 0.62},        // Mare Vaporum
        {-3.0, 3.0, 5.0, 4.0, 0.0, 0.70},        // Sinus Medii
        {24.0, 38.0, 6.0, 5.0, 0.0, 0.64},       // Lacus Somniorum
        {-86.0, -8.0, 10.0, 22.0, 0.0, 0.60},    // Randmeere im Westen
        {68.0, -20.0, 7.0, 8.0, 0.0, 0.62}       // Mare Australe, am Ostrand
    };

    /**
     * Ein Strahlenkrater: Laenge, Breite, Radius des hellen Hofes in Grad,
     * Aufhellung, Laenge der Strahlen in Grad.
     */
    private static final double[][] RAY_CRATERS = {
        {-11.0, -43.0, 3.5, 0.42, 52.0},   // Tycho
        {-20.0, 10.0, 2.5, 0.26, 18.0},    // Kopernikus
        {-38.0, 8.0, 1.8, 0.22, 12.0},     // Kepler
        {-47.0, 24.0, 1.6, 0.40, 9.0},     // Aristarch
        {-9.0, 51.0, 3.5, -0.22, 0.0},     // Plato, dunkel
        {60.0, -58.0, 4.0, 0.20, 14.0},    // Stevinus-Gebiet im Suedosten
        {-25.0, -58.0, 3.0, 0.18, 10.0}    // Hochland um Clavius
    };

    private final double[] noise;
    private final int noiseSize = 64;

    /**
     * Legt eine Oberflaeche mit festem Rauschmuster an. Der Startwert ist
     * fest verdrahtet, damit zwei Laeufe dasselbe Bild ergeben - ein Mond,
     * der bei jedem Programmstart anders aussieht, waere ein Fehler.
     */
    public FMoonSurface() {
        noise = new double[noiseSize * noiseSize];
        long seed = 0x4D4F4F4EL;   // "MOON"
        for (int i = 0; i < noise.length; i++) {
            seed = seed * 6364136223846793005L + 1442695040888963407L;
            noise[i] = ((seed >>> 40) / (double) (1 << 24)) - 0.5;
        }
    }

    /** Kantenlaenge der Wertetabelle in Laengenrichtung. */
    private static final int TABLE_W = 1024;

    /** Kantenlaenge der Wertetabelle in Breitenrichtung. */
    private static final int TABLE_H = 512;

    private float[] table;

    /**
     * Albedo an einem Punkt der Oberflaeche.
     *
     * <p>Ausgewertet wird aus einer Wertetabelle von 1024 mal 512 Stellen,
     * die beim ersten Zugriff einmal aus {@link #computeAlbedo} gefuellt und
     * danach bilinear abgetastet wird. Die Rechenvorschrift unmittelbar je
     * Bildpunkt auszuwerten war rund zehnmal langsamer, und beim Aufbau eines
     * Monatskalenders mit dreissig Monden war das deutlich zu spueren. Die
     * Tabelle ist feiner als jede Darstellung: Auf einer Kugel von 400
     * Bildpunkten liegen ueber die sichtbare Haelfte 512 Stuetzstellen.</p>
     *
     * @param lonDeg selenografische Laenge in Grad, Ost positiv
     * @param latDeg selenografische Breite in Grad, Nord positiv
     * @return Albedo zwischen etwa 0,5 und 1,2 (1,0 = mittleres Hochland)
     */
    public double albedoAt(double lonDeg, double latDeg) {
        float[] t = table;
        if (t == null) {
            t = buildTable();
        }
        double u = (lonDeg + 180.0) / 360.0 * TABLE_W;
        double v = (latDeg + 90.0) / 180.0 * (TABLE_H - 1);
        int u0 = (int) Math.floor(u);
        int v0 = (int) Math.floor(v);
        double fu = u - u0;
        double fv = v - v0;
        int u1 = u0 + 1;
        int v1 = v0 + 1;
        u0 = ((u0 % TABLE_W) + TABLE_W) % TABLE_W;
        u1 = ((u1 % TABLE_W) + TABLE_W) % TABLE_W;
        if (v0 < 0) {
            v0 = 0;
        }
        if (v0 > TABLE_H - 1) {
            v0 = TABLE_H - 1;
        }
        if (v1 < 0) {
            v1 = 0;
        }
        if (v1 > TABLE_H - 1) {
            v1 = TABLE_H - 1;
        }
        double a00 = t[v0 * TABLE_W + u0];
        double a10 = t[v0 * TABLE_W + u1];
        double a01 = t[v1 * TABLE_W + u0];
        double a11 = t[v1 * TABLE_W + u1];
        return (a00 * (1 - fu) + a10 * fu) * (1 - fv)
                + (a01 * (1 - fu) + a11 * fu) * fv;
    }

    private synchronized float[] buildTable() {
        if (table != null) {
            return table;
        }
        float[] t = new float[TABLE_W * TABLE_H];
        for (int v = 0; v < TABLE_H; v++) {
            double lat = v / (double) (TABLE_H - 1) * 180.0 - 90.0;
            for (int u = 0; u < TABLE_W; u++) {
                double lon = u / (double) TABLE_W * 360.0 - 180.0;
                t[v * TABLE_W + u] = (float) computeAlbedo(lon, lat);
            }
        }
        table = t;
        return t;
    }

    /**
     * Rechnet das Albedo unmittelbar aus, ohne Tabelle. Wird beim Aufbau der
     * Tabelle benutzt und ist fuer Pruefungen oeffentlich.
     *
     * @param lonDeg selenografische Laenge in Grad, Ost positiv
     * @param latDeg selenografische Breite in Grad, Nord positiv
     * @return Albedo
     */
    public double computeAlbedo(double lonDeg, double latDeg) {
        double a = 1.0;

        // Maria abdunkeln
        for (int i = 0; i < MARIA.length; i++) {
            double[] m = MARIA[i];
            double dLon = wrapLon(lonDeg - m[0]) * Math.cos(Math.toRadians(latDeg));
            double dLat = latDeg - m[1];
            double c = Math.cos(Math.toRadians(m[4]));
            double s = Math.sin(Math.toRadians(m[4]));
            double x = (dLon * c + dLat * s) / m[2];
            double y = (-dLon * s + dLat * c) / m[3];
            double r2 = x * x + y * y;
            if (r2 < 4.0) {
                // Die Maria haben scharfe Kueste. Ein einfaches Gauss-Profil
                // macht sie zu Wattebaeuschen; deshalb ein flacher Kern mit
                // steilem Abfall (Exponent vier statt zwei) und ein leicht
                // welliger Rand, damit keine glatte Ellipse stehen bleibt.
                double rand = 1.0 + 0.10 * noiseAt(lonDeg * 2.3, latDeg * 2.3) * 4.0;
                double w = Math.exp(-Math.pow(r2 / (rand * rand), 2.2) * 0.9);
                a = a * (1.0 - w) + m[5] * w;
            }
        }

        // Strahlenkrater
        for (int i = 0; i < RAY_CRATERS.length; i++) {
            double[] k = RAY_CRATERS[i];
            double dLon = wrapLon(lonDeg - k[0]) * Math.cos(Math.toRadians(latDeg));
            double dLat = latDeg - k[1];
            double dist = Math.sqrt(dLon * dLon + dLat * dLat);
            if (dist < k[2] * 2.5) {
                double w = Math.exp(-(dist / k[2]) * (dist / k[2]));
                a += k[3] * w;
            }
            if (k[4] > 0.0 && dist < k[4] && dist > k[2]) {
                // Strahlen: schmale Buendel, nach aussen schwaecher werdend.
                // Bewusst zurueckhaltend - Tycho ueberstrahlt sonst die halbe
                // Suedhalbkugel und macht die Maria unkenntlich.
                double winkel = Math.atan2(dLat, dLon);
                double strahl = Math.cos(winkel * 11.0 + 0.7)
                        * Math.cos(winkel * 4.0 + 1.3);
                double abfall = 1.0 - dist / k[4];
                if (strahl > 0.45) {
                    a += k[3] * 0.28 * (strahl - 0.45) * abfall * abfall;
                }
            }
        }

        // feines Rauschen der Hochlaender
        a += noiseAt(lonDeg, latDeg) * 0.07;
        a += noiseAt(lonDeg * 3.7 + 40.0, latDeg * 3.7) * 0.04;
        a += noiseAt(lonDeg * 11.0 - 25.0, latDeg * 11.0) * 0.025;

        // Randverdunkelung der Hochlaender zu den Polen hin
        a -= 0.04 * Math.pow(Math.abs(latDeg) / 90.0, 3.0);

        return Math.max(0.35, Math.min(1.35, a));
    }

    private double noiseAt(double lonDeg, double latDeg) {
        double u = (lonDeg + 180.0) / 360.0 * noiseSize;
        double v = (latDeg + 90.0) / 180.0 * noiseSize;
        int u0 = (int) Math.floor(u);
        int v0 = (int) Math.floor(v);
        double fu = u - u0;
        double fv = v - v0;
        double n00 = rawNoise(u0, v0);
        double n10 = rawNoise(u0 + 1, v0);
        double n01 = rawNoise(u0, v0 + 1);
        double n11 = rawNoise(u0 + 1, v0 + 1);
        double su = fu * fu * (3.0 - 2.0 * fu);
        double sv = fv * fv * (3.0 - 2.0 * fv);
        return (n00 * (1 - su) + n10 * su) * (1 - sv)
                + (n01 * (1 - su) + n11 * su) * sv;
    }

    private double rawNoise(int u, int v) {
        int uu = ((u % noiseSize) + noiseSize) % noiseSize;
        int vv = ((v % noiseSize) + noiseSize) % noiseSize;
        return noise[vv * noiseSize + uu];
    }

    private static double wrapLon(double d) {
        double x = d % 360.0;
        if (x > 180.0) {
            x -= 360.0;
        }
        if (x < -180.0) {
            x += 360.0;
        }
        return x;
    }
}
