package com.dan.rungholt.ui;

import com.dan.fstyle.FTheme;

import java.awt.Color;

/**
 * Die Farben der Oberfläche aus FTheme (Bedienelemente in FTheme-Farben, die Szene hat ihren eigenen Look; aus
 * Glacier). Liefert das Thema keine dunklen Farben, nimmt die App ihre Nacht über dem Watt, damit der Rand um die Szene
 * nicht hell aufleuchtet. Dazu die Farben des Werkbuchs: Schaum, Nordsee, Priel, Watt, Reet, Backstein, Abendgold.
 */
public final class Theme {
    private Theme() { }

    public static final Color NACHT = new Color(14, 20, 22), SCHAUM = new Color(240, 244, 243), SEE = new Color(79, 155, 142),
            PRIEL = new Color(91, 167, 179), TIEFE = new Color(31, 59, 68), WATT = new Color(164, 143, 114), REET = new Color(199, 178, 113),
            ZIEGEL = new Color(201, 96, 63), GOLD = new Color(227, 184, 92);

    public static Color bg() { return pick(FTheme::getBackground, NACHT, Role.DARK); }
    public static Color surface() { return pick(FTheme::getSurface, new Color(21, 29, 33), Role.DARK); }
    public static Color ink() { return pick(FTheme::getText, new Color(230, 236, 240), Role.LIGHT); }
    public static Color muted() { return pick(FTheme::getTextMuted, new Color(143, 162, 174), Role.LIGHT); }
    public static Color accent() { return pick(FTheme::getAccent, PRIEL, Role.ANY); }
    public static Color status() { return new Color(9, 13, 15); }
    public static Color line() { return new Color(39, 52, 57); }

    private enum Role { DARK, LIGHT, ANY }

    private static Color pick(java.util.function.Function<FTheme, Color> f, Color fallback, Role role) {
        try {
            Color c = f.apply(FTheme.getInstance());
            if (c == null) return fallback;
            int lum = (c.getRed() * 3 + c.getGreen() * 6 + c.getBlue()) / 10;
            if (role == Role.DARK && lum > 70) return fallback;
            if (role == Role.LIGHT && lum < 120) return fallback;
            return c;
        } catch (RuntimeException | Error e) {
            return fallback;
        }
    }
}
