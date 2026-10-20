package com.dan.rungholt.scene;

/**
 * Der Ort der Szene: das Rungholt-Watt zwischen Pellworm, Südfall und Nordstrand im Nordfriesischen Wattenmeer (F1 A).
 * Echter Maßstab: 1 Einheit = 1 m, x nach Osten, y nach oben, Nord = −z, y = 0 ist NHN (Normalhöhennull). Ursprung
 * ist die Hauptkirche von Rungholt [G: 1 km östlich der Warft von Südfall, bis die Lage genauer bekannt ist]. Lage nach
 * SRID 8307 (WGS84). Die Szene ist eine transversale Mercator-Abbildung um den Ursprung ({@link #SCENE}).
 * <p>
 * Belegt aus den Höhendaten ({@code templates/rasters_COP30/EasyGeo}, topography_max_z 2015–2021, 10 m): die Warft von
 * Südfall als höchste Zelle der Hallig (5,8 m über NHN) bei 54,4648° N, 8,7225° O, die Hallig mit 0,39 km² über
 * NHN +1,5 m, die Norderhever bis 30,7 m unter NHN, rund 111 km² Watt zwischen dem mittleren Niedrig- und
 * Hochwasser im Ausschnitt, die Niedrigwasserlinie im Mittel bei NHN −1,7 m. Die übrigen Maße stammen aus dem Werkbuch,
 * Abschnitt 2.
 */
public final class Site {
    private Site() { }

    /** Warft von Südfall (aus den Höhendaten: höchste Zelle der Hallig). */
    public static final double SUEDFALL_LAT = 54.46476, SUEDFALL_LON = 8.72251;
    /** Ursprung der Szene: die Kirche von Rungholt [G], 1 km östlich der Warft von Südfall auf gleicher Breite. */
    public static final double LAT = SUEDFALL_LAT, LON = SUEDFALL_LON + 1000 / metersPerDegreeLon(SUEDFALL_LAT);
    /** Der Ausschnitt (F1 A) in Grad: West, Süd, Ost, Nord. */
    public static final double WEST = 8.60, SOUTH = 54.41, EAST = 8.82, NORTH = 54.52;
    public static final int SRID = 8307;
    /** Mittlerer Erdradius in m und der wirksame Radius für die Sicht mit normaler Lichtbrechung (k = 0,13). */
    public static final double EARTH_R = 6_371_000, EARTH_R_OPTICAL = EARTH_R / (1 - 0.13);

    /** Mittleres Hoch- und Niedrigwasser (m über NHN) nach Pegel Husum [G, zu prüfen in Phase 4]; Tidenhub. */
    public static final double MTHW = 1.6, MTNW = -1.9, TIDAL_RANGE = MTHW - MTNW;
    /** Mittlere Dauer einer Tide (Hochwasser bis Hochwasser, M2) in Stunden: 12 h 25 min 14 s. */
    public static final double TIDE_HOURS = 12.4206012;
    /** Warft von Südfall über NHN (m, aus den Höhendaten) und die tiefste Stelle der Norderhever im Ausschnitt. */
    public static final double SUEDFALL_WARFT = 5.8, HEVER_DEPTH = 30.7;
    /** Rungholt: Kirche 40 × 15 m, 54 Warften auf einer Kette von 2 km (Kiel/Mainz 2023). */
    public static final double CHURCH_LENGTH = 40, CHURCH_WIDTH = 15, WARFT_CHAIN = 2000;
    public static final int WARFTEN = 54;
    /**
     * Die Grote Mandränke: Nacht vom 15. auf den 16. Januar 1362 nach dem julianischen Kalender der Quellen, in der Uhr
     * der Szene (proleptisch gregorianisch) die Nacht vom 23. auf den 24. Januar; das ist der 24. (Phase 17).
     */
    public static final java.time.LocalDate MANDRAENKE = SceneClock.gregorian(1362, 1, 16);

    /** Länge eines Längengrads auf dem Ellipsoid (GRS80) bei der Breite (m). */
    public static double metersPerDegreeLon(double latDeg) {
        double p = Math.toRadians(latDeg), e2 = Geo.F * (2 - Geo.F), sp = Math.sin(p);
        return Math.PI / 180 * Geo.A_AXIS * Math.cos(p) / Math.sqrt(1 - e2 * sp * sp);
    }

    /** Absenkung der Oberfläche in d m Entfernung durch die Erdkrümmung: d²/2R. */
    public static double curvatureDrop(double d) { return d * d / (2 * EARTH_R); }

    /** Entfernung der Kimm für ein Auge h m über dem Wasser, mit normaler Lichtbrechung (m). */
    public static double horizon(double h) { return Math.sqrt(2 * EARTH_R_OPTICAL * Math.max(0, h)); }

    /** Tempo einer langen Welle im Flachwasser der Tiefe h (m): √(g·h). */
    public static double waveSpeed(double depth) { return Math.sqrt(9.81 * Math.max(0, depth)); }

    /** Die Abbildung der Szene: transversale Mercator mit Maßstab 1 um die Kirche (Nordwert = −z). */
    public static final Geo.Tm SCENE = Geo.local(LAT, LON);

    /** Breite und Länge (Grad) eines Szenenpunkts. */
    public static double[] latLon(double x, double z) { return SCENE.inverse(x, -z); }

    /** Szenenpunkt (x, z) aus Breite und Länge (Grad, als {lat, lon}). */
    public static double[] xz(double[] latLon) { double[] en = SCENE.forward(latLon[0], latLon[1]); return new double[]{en[0], -en[1]}; }

    /** Szenenpunkt (x, z) aus UTM 32 Nord (Ost- und Nordwert in m, wie die Höhendaten). */
    public static double[] fromUtm(double east, double north) { return xz(Geo.UTM32N.inverse(east, north)); }

    /** Breite eines Punkts auf der Nord-Süd-Achse durch den Ursprung, Länge eines Punkts auf der Ost-West-Achse. */
    public static double lat(double z) { return latLon(0, z)[0]; }
    public static double lon(double x) { return latLon(x, 0)[1]; }

    /** Die Warft von Südfall in der Szene (x, z). */
    public static double[] suedfall() { return xz(new double[]{SUEDFALL_LAT, SUEDFALL_LON}); }

    /** „54°27′53″ N · 8°44′17″ E“ */
    public static String dms(double lat, double lon) {
        return dms(lat, lat >= 0 ? "N" : "S") + " · " + dms(lon, lon >= 0 ? "E" : "W");
    }

    private static String dms(double v, String h) {
        v = Math.abs(v);
        int d = (int) v;
        double mm = (v - d) * 60;
        int m = (int) mm;
        int s = (int) Math.round((mm - m) * 60);
        if (s == 60) { s = 0; m++; }
        if (m == 60) { m = 0; d++; }
        return String.format("%d°%02d′%02d″ %s", d, m, s, h);
    }
}
