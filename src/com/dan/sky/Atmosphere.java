package com.dan.sky;

/**
 * Die Lufthülle als Zahlen: Rayleigh-Streuung der Luft, Mie-Streuung des Dunsts, Ozon, Erdradius und Höhe der
 * Atmosphäre (Werte nach Hillaire 2020 und Bruneton 2017). Längen in m, Koeffizienten je m für die drei Kanäle
 * R, G, B (680, 550, 440 nm).
 * <p>
 * Der Dunst kommt aus der meteorologischen Sichtweite (Koschmieder: Sicht = 3,912 / Auslöschung am Boden bei 550 nm).
 * Was die Luft selbst nicht auslöscht, übernimmt der Dunst; er liegt mit 1,2 km Skalenhöhe dicht über dem Wasser.
 * Seedunst streut fast alles, was er auslöscht (Albedo 0,98), und streut nach vorn (g = 0,85): das gibt die helle
 * Aureole um die Sonne und die milchige Kimm.
 */
public final class Atmosphere {
    /** Erdradius und Oberkante der Atmosphäre in m. */
    public static final double BOTTOM = 6_360_000, TOP = 6_460_000;
    public static final double[] RAYLEIGH = {5.802e-6, 13.558e-6, 33.1e-6};
    public static final double RAYLEIGH_H = 8000;
    public static final double MIE_EXT_CLEAR = 4.40e-6, MIE_ALBEDO = 0.98, MIE_H = 1200, MIE_G = 0.85;
    public static final double[] OZONE = {0.650e-6, 1.881e-6, 0.085e-6};
    public static final double OZONE_CENTER = 25_000, OZONE_HALF_WIDTH = 15_000;
    /** Größte Sicht, die die reine Luft zulässt (nur Rayleigh und klarster Dunst), rund 220 km. */
    public static final double MAX_VISIBILITY_KM = 3.912 / (RAYLEIGH[1] + MIE_EXT_CLEAR) / 1000;

    /** Mie-Auslöschung und -Streuung am Boden je m, Albedo des Bodens (Meer rund 0,06 bis 0,1). */
    public final double mieExtinction, mieScattering, groundAlbedo;
    /** Sichtweite in km, aus der dieser Zustand gebaut ist. */
    public final double visibilityKm;

    private Atmosphere(double visibilityKm, double mieExtinction, double groundAlbedo) {
        this.visibilityKm = visibilityKm;
        this.mieExtinction = mieExtinction;
        this.mieScattering = mieExtinction * MIE_ALBEDO;
        this.groundAlbedo = groundAlbedo;
    }

    /** Atmosphäre für eine Sichtweite in km (1 bis 220) und eine Bodenalbedo. */
    public static Atmosphere forVisibility(double km, double groundAlbedo) {
        km = Math.max(1, Math.min(MAX_VISIBILITY_KM, km));
        double mie = Math.max(MIE_EXT_CLEAR, 3.912 / (km * 1000) - RAYLEIGH[1]);
        return new Atmosphere(km, mie, Math.max(0, Math.min(1, groundAlbedo)));
    }

    /** Dichteprofile über der Höhe h in m. */
    public static double rayleighDensity(double h) { return Math.exp(-Math.max(0, h) / RAYLEIGH_H); }
    public static double mieDensity(double h) { return Math.exp(-Math.max(0, h) / MIE_H); }
    public static double ozoneDensity(double h) { return Math.max(0, 1 - Math.abs(h - OZONE_CENTER) / OZONE_HALF_WIDTH); }

    /** Auslöschung je m in Höhe h, Kanal c. */
    public double extinction(double h, int c) {
        return RAYLEIGH[c] * rayleighDensity(h) + mieExtinction * mieDensity(h) + OZONE[c] * ozoneDensity(h);
    }

    /** Aerosol-Schichtdicke (senkrecht, 550 nm), zur Anzeige. */
    public double aerosolOpticalDepth() { return mieExtinction * MIE_H; }

    /** Auslöschung am Boden bei 550 nm je m; daraus die Sicht 3,912 / β. */
    public double groundExtinction() { return RAYLEIGH[1] + mieExtinction + OZONE[1] * ozoneDensity(0); }

    /** Für die Grafikkarte: Mie-Streuung, Mie-Auslöschung, Bodenalbedo, g. */
    public float[] uniforms() {
        return new float[]{(float) mieScattering, (float) mieExtinction, (float) groundAlbedo, (float) MIE_G};
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "Sicht %.0f km, Dunst τ %.2f", visibilityKm, aerosolOpticalDepth());
    }
}
