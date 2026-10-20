package com.dan.sky.astro;

// Kopie aus FMoon (com.dan.fmoon, Stand 13.09.2026), nur das Package geändert. Geprüft in FMoon gegen die Beispiele
// in Meeus, „Astronomical Algorithms“; Änderungen bitte dort vornehmen und hierher kopieren.

/**
 * Kleine Winkelhilfen fuer den FMoon-Rechenkern. Alle Methoden rechnen in
 * Grad, sofern der Name nichts anderes sagt.
 *
 * @author Claude fuer Daniel
 */
public final class FAngle {

    private FAngle() {
    }

    /**
     * Bringt einen Winkel in das Intervall 0 bis 360 Grad.
     *
     * @param deg Winkel in Grad
     * @return derselbe Winkel, 0 &lt;= x &lt; 360
     */
    public static double norm360(double deg) {
        double x = deg % 360.0;
        return (x < 0.0) ? x + 360.0 : x;
    }

    /**
     * Bringt einen Winkel in das Intervall -180 bis +180 Grad.
     *
     * @param deg Winkel in Grad
     * @return derselbe Winkel, -180 &lt;= x &lt; 180
     */
    public static double norm180(double deg) {
        double x = norm360(deg);
        return (x >= 180.0) ? x - 360.0 : x;
    }

    /**
     * Bringt einen Winkel in das Intervall 0 bis 2 pi.
     *
     * @param rad Winkel im Bogenmass
     * @return derselbe Winkel, 0 &lt;= x &lt; 2 pi
     */
    public static double norm2Pi(double rad) {
        double x = rad % (2.0 * Math.PI);
        return (x < 0.0) ? x + 2.0 * Math.PI : x;
    }

    /**
     * Sinus eines Winkels in Grad.
     *
     * @param deg Winkel in Grad
     * @return Sinus
     */
    public static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    /**
     * Kosinus eines Winkels in Grad.
     *
     * @param deg Winkel in Grad
     * @return Kosinus
     */
    public static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    /**
     * Tangens eines Winkels in Grad.
     *
     * @param deg Winkel in Grad
     * @return Tangens
     */
    public static double tan(double deg) {
        return Math.tan(Math.toRadians(deg));
    }

    /**
     * Kuerzeste Differenz zweier Winkel, -180 bis +180 Grad.
     *
     * @param a erster Winkel in Grad
     * @param b zweiter Winkel in Grad
     * @return a - b auf dem kuerzeren Bogen
     */
    public static double difference(double a, double b) {
        return norm180(a - b);
    }

    /**
     * Formatiert einen Winkel als Grad, Bogenminuten und Bogensekunden.
     *
     * @param deg Winkel in Grad
     * @return Text der Form "133 09 35,7"
     */
    public static String toDms(double deg) {
        double x = Math.abs(deg);
        int d = (int) Math.floor(x);
        double mFull = (x - d) * 60.0;
        int m = (int) Math.floor(mFull);
        double s = (mFull - m) * 60.0;
        String sign = (deg < 0.0) ? "-" : "";
        return String.format("%s%d° %02d' %04.1f\"", sign, d, m, s);
    }
}
