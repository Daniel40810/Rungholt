package com.dan.ice;

/**
 * Die Optik des Eises ohne Grafikkarte, Zeile für Zeile wie {@code gl/ice_common.glsl}.
 * <p>
 * <b>Absorption:</b> reines Eis schluckt Rot stark und Blau kaum. Je Farbkanal ein Absorptionskoeffizient (1/m) für
 * das breite Band des Kanals: Rot um 620 nm 0,25 /m (Grenfell &amp; Perovich, in Warren &amp; Brandt 2008 ab 600 nm
 * übernommen), Grün über das Band 500 bis 570 nm 0,035 /m (Picard et al. 2016: 0,03 bei 500 nm, 0,064 bei 550 nm),
 * Blau um 460 nm 0,006 /m (zwischen Warren et al. 2006 / Warren &amp; Brandt 2008 und dem zehnfach höheren Wert von
 * Picard et al. 2016) [G, gerundet]. Rot ist damit nach knapp 3 m halbiert, Grün nach 20 m, Blau erst nach über
 * 100 m: dickes Eis wird blau, mit einem Stich ins Türkis.
 * <p>
 * <b>Streuung:</b> die Luftblasen. Sie schlucken nichts, lenken das Licht aber stark vorwärts um (Asymmetrie
 * {@link #G}); Firn mit vielen kleinen Blasen streut so stark, dass kaum Licht tief eindringt – er ist weiß. Altes,
 * gepresstes Eis ohne Blasen lässt das Licht tief hinein und kommt blau zurück. {@link #scattering} bildet den Anteil
 * der Blasen 0..1 logarithmisch auf 0,05 bis 200 /m ab [G].
 * <p>
 * <b>Schicht:</b> Reflexion R und diffuse Durchlässigkeit T einer Schicht der Dicke d nach Kubelka-Munk mit
 * K = 2α und S = ¾ σ(1 − g), dazu der ungestreute Anteil exp(−(α + σ) d), durch den man klares Eis hindurch sieht.
 * Die Formeln sind für große Dicken stabil geschrieben (tanh und sech statt sinh und cosh).
 */
public final class IceOptics {
    /** Absorption von reinem Eis je Kanal R, G, B in 1/m (Bandmittel) [G, nach Warren &amp; Brandt 2008, Picard 2016]. */
    public static final double[] ABSORPTION = {0.25, 0.035, 0.006};
    /** Brechzahl von Eis im Sichtbaren. */
    public static final double N = 1.31;
    /** Reflexion bei senkrechtem Einfall ((n − 1)/(n + 1))². */
    public static final double F0 = Math.pow((N - 1) / (N + 1), 2);
    /** Asymmetrie der Streuung an Luftblasen im Eis (große Kugeln, vorwärts) [G]. */
    public static final double G = 0.8;
    /** Streuung bei 0 und 1 Blasenanteil (1/m). */
    public static final double SIGMA_MIN = 0.05, SIGMA_MAX = 200;

    private IceOptics() { }

    /** Streukoeffizient der Blasen (1/m) für den Anteil 0..1, logarithmisch zwischen 0,05 und 200. */
    public static double scattering(double bubbles) {
        double b = Math.max(0, Math.min(1, bubbles));
        return SIGMA_MIN * Math.pow(SIGMA_MAX / SIGMA_MIN, b);
    }

    /** Länge (m), nach der ein Kanal im klaren Eis auf die Hälfte gesunken ist. */
    public static double halfLength(int channel) { return Math.log(2) / ABSORPTION[channel]; }

    /**
     * Kubelka-Munk für eine Schicht: {R, T} bei Absorption K und Streuung S (1/m) und Dicke d (m). Stabil für große
     * Dicken (wie km() in ice_common.glsl).
     */
    public static double[] km(double k, double s, double d) {
        s = Math.max(s, 1e-6);
        double a = 1 + k / s, b = Math.sqrt(Math.max(a * a - 1, 0)), x = Math.min(b * s * Math.max(d, 0), 40);
        double e = Math.exp(-2 * x), th = (1 - e) / (1 + e), sech = 2 * Math.exp(-x) / (1 + e);
        double den = a * th + b;
        if (den <= 0) return new double[]{0, 1};
        return new double[]{th / den, b * sech / den};
    }

    /** Reflexion einer unendlich dicken Schicht R∞ = 1 + K/S − √((K/S)² + 2 K/S). */
    public static double rInf(double k, double s) {
        double q = k / Math.max(s, 1e-9);
        return 1 + q - Math.sqrt(q * q + 2 * q);
    }

    /** K = 2α je Kanal. */
    public static double absorptionK(int c) { return 2 * ABSORPTION[c]; }

    /** S = ¾ σ (1 − g) für den Blasenanteil, dazu die Streuung an Rissen (1/m). */
    public static double scatterS(double bubbles, double crackScatter) { return 0.75 * scattering(bubbles) * (1 - G) + crackScatter; }

    /**
     * Streuung (1/m) und Dicke (m) der verwitterten Haut: Eis an der Luft wird an der Oberfläche von Sonne und Wind
     * zu einer körnigen, weißen Kruste aus feinen Rissen und Hohlräumen [G]. Frisch gebrochene oder nasse Flächen
     * haben keine (blau), alte, besonnte eine dicke (weiß).
     */
    public static final double CRUST_SIGMA = 400, CRUST_DEPTH = 0.12;

    /** Dicke der Kruste (m) für den Grad der Verwitterung 0..1. */
    public static double crustDepth(double weathering) { return CRUST_DEPTH * Math.max(0, Math.min(1, weathering)); }

    /**
     * Zwei Schichten übereinander (Kruste über dem Eis): R = R₁ + T₁² R₂ / (1 − R₁ R₂), T = T₁ T₂ / (1 − R₁ R₂),
     * je Kanal; {R, T}.
     */
    public static double[] layered(double k, double s1, double d1, double s2, double d2) {
        double[] a = km(k, s1, d1), b = km(k, s2, d2);
        double den = Math.max(1e-9, 1 - a[0] * b[0]);
        return new double[]{a[0] + a[1] * a[1] * b[0] / den, a[1] * b[1] / den};
    }

    /** Diffuse Reflexion je Kanal (RGB) für Eis {@code ice} der Dicke d, mit Kruste. */
    public static double[] reflectance(Ice ice, double d) {
        double s = scatterS(ice.bubbles, 0), sc = 0.75 * CRUST_SIGMA * (1 - G), dc = crustDepth(ice.weathering);
        double[] r = new double[3];
        for (int c = 0; c < 3; c++) r[c] = layered(absorptionK(c), sc, dc, s, d)[0];
        return r;
    }

    /** Diffuse Durchlässigkeit je Kanal (RGB), mit Kruste. */
    public static double[] transmittance(Ice ice, double d) {
        double s = scatterS(ice.bubbles, 0), sc = 0.75 * CRUST_SIGMA * (1 - G), dc = crustDepth(ice.weathering);
        double[] t = new double[3];
        for (int c = 0; c < 3; c++) t[c] = layered(absorptionK(c), sc, dc, s, d)[1];
        return t;
    }

    /** Ungestreuter Anteil exp(−(α + σ) d) je Kanal, durch die Kruste: so viel Hintergrund sieht man durch das Eis. */
    public static double[] direct(Ice ice, double d) {
        double s = scattering(ice.bubbles), dc = crustDepth(ice.weathering);
        double[] t = new double[3];
        for (int c = 0; c < 3; c++) t[c] = Math.exp(-(ABSORPTION[c] + s) * d - CRUST_SIGMA * dc);
        return t;
    }

    /** Fresnel nach Schlick für den Kosinus des Einfallswinkels. */
    public static double fresnel(double cos) {
        double m = 1 - Math.max(0, Math.min(1, cos));
        return F0 + (1 - F0) * m * m * m * m * m;
    }

    /** Verhältnis Blau zu Rot der diffusen Reflexion (wie blau das Eis aussieht). */
    public static double blueness(Ice ice, double d) {
        double[] r = reflectance(ice, d);
        return r[2] / Math.max(1e-9, r[0]);
    }
}
