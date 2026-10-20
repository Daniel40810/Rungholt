package com.dan.muster.demo;

import com.dan.muster.Muster;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Jedes Package hat so eine Klasse; sie läuft ohne Grafikkarte und ohne die App.
 */
public final class MusterCheck {
    private static int bad;

    public static void main(String[] args) {
        Muster a = Muster.breeze(), b = Muster.breeze();
        a.seed = b.seed = 42;
        check(a.valueAt(12.5, -7.25, 3) == b.valueAt(12.5, -7.25, 3), "gleicher seed, gleicher Wert");
        b.seed = 43;
        check(a.valueAt(12.5, -7.25, 3) != b.valueAt(12.5, -7.25, 3), "anderer seed, anderer Wert");
        check(Muster.calm().strength < Muster.breeze().strength && Muster.breeze().strength < Muster.gale().strength
                && Muster.gale().strength < Muster.storm().strength, "Presets steigen von calm bis storm");
        boolean range = true;
        for (int i = 0; i < 10_000; i++) {
            double v = a.valueAt(i * 3.7, i * -1.3, i * 0.01);
            if (v < 0 || v > 1 || Double.isNaN(v)) range = false;
        }
        check(range, "Werte liegen in 0..1");
        int[] calls = new int[1];
        a.onPeak = (x, z, v) -> calls[0]++;
        a.peakAbove = 0.6;
        int found = a.scan(0, 0, 200, 40, 0);
        check(found == calls[0], "scan meldet jeden Fund über die eigene Schnittstelle");
        check(Muster.beaufort(-3).windSpeed == Muster.beaufort(0).windSpeed && Muster.beaufort(99).windSpeed == Muster.beaufort(12).windSpeed,
                "beaufort(n) begrenzt auf 0..12");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private MusterCheck() { }
}
