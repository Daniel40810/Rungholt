package com.dan.reed.demo;

import com.dan.reed.Reed;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Läuft ohne Grafikkarte und ohne die App.
 */
public final class ReedCheck {
    private static int bad;

    public static void main(String[] args) {
        Reed.Density band = (x, z, k) -> k == Reed.Kind.SCHILF ? (Math.abs(z) < 10 ? 1 : 0) : k == Reed.Kind.GRAS ? (z > 15 ? 0.8 : 0) : 0;
        Reed.Ground flat = (x, z) -> 0.25;
        Reed a = Reed.breeze(), b = Reed.breeze();
        a.seed = b.seed = 7;
        float[] ca = a.cell(2, 0, Reed.Kind.SCHILF, band, flat), cb = b.cell(2, 0, Reed.Kind.SCHILF, band, flat);
        check(java.util.Arrays.equals(ca, cb) && ca.length > 0, "gleicher seed, gleiche Halme (" + ca.length / Reed.STRIDE + " in einer Zelle)");
        b.seed = 8;
        check(!java.util.Arrays.equals(ca, b.cell(2, 0, Reed.Kind.SCHILF, band, flat)), "anderer seed, andere Halme");
        boolean inBand = true, onGround = true;
        for (int i = 0; i < ca.length; i += Reed.STRIDE) {
            if (Math.abs(ca[i + 2]) >= 10) inBand = false;
            if (ca[i + 1] != 0.25f) onGround = false;
        }
        check(inBand, "Schilf nur, wo die Dichte > 0 ist");
        check(onGround, "Halme stehen auf dem Boden der Schnittstelle Ground");
        float[] none = a.cell(0, 3, Reed.Kind.BINSE, band, flat);
        check(none.length == 0, "keine Halme bei Dichte 0");
        double per = a.cell(0, -1, Reed.Kind.SCHILF, (x, z, k) -> 1, flat).length / (double) Reed.STRIDE / (Reed.CELL * Reed.CELL);
        check(Math.abs(per - Reed.Kind.SCHILF.perM2) < 0.6, String.format("Dichte 1 ergibt %.1f Halme je m² (Soll %.1f)", per, Reed.Kind.SCHILF.perM2));
        double avgH = 0;
        int n = 0;
        for (int i = 0; i < ca.length; i += Reed.STRIDE) { avgH += ca[i + 3]; n++; }
        avgH /= Math.max(1, n);
        check(avgH > 1.8 && avgH < 3.0, String.format("Schilf im Mittel %.2f m hoch", avgH));
        check(Reed.calm().windSpeed < Reed.breeze().windSpeed && Reed.breeze().windSpeed < Reed.fresh().windSpeed && Reed.fresh().windSpeed < Reed.gale().windSpeed,
                "Presets steigen von calm bis gale");
        check(Reed.lean(12, Reed.Kind.SCHILF.stiffness) > Reed.lean(4, Reed.Kind.SCHILF.stiffness)
                && Reed.lean(12, Reed.Kind.BINSE.stiffness) < Reed.lean(12, Reed.Kind.SCHILF.stiffness), "Neigung wächst mit dem Wind, steife Binsen neigen weniger");
        Reed w = Reed.fresh();
        double lo = 9, hi = -9;
        for (int i = 0; i < 400; i++) { double s = w.sway(0, 0, 1.3, Reed.Kind.SCHILF.stiffness, i * 0.05)[0]; lo = Math.min(lo, s); hi = Math.max(hi, s); }
        check(hi - lo > 0.05 && lo > 0, String.format("Böen bewegen den Schilf sichtbar (%.2f bis %.2f der Höhe)", lo, hi));
        double[] s1 = w.sway(0, 0, 1, 0.55, 2), s2 = w.sway(9, 0, 1, 0.55, 2);
        check(Math.abs(s1[0] - s2[0]) > 1e-3, "Böenwellen laufen über die Fläche (Nachbarn biegen sich anders)");
        Reed c = Reed.calm();
        check(Math.abs(c.sway(3, 4, 0, 0.55, 5)[0]) < 0.05, "Windstille: fast keine Bewegung");
        double[] summer, autumn;
        c.season = 0; summer = c.color(Reed.Kind.SCHILF, 0.5);
        c.season = 0.75; autumn = c.color(Reed.Kind.SCHILF, 0.5);
        check(summer[1] > summer[0] && autumn[0] > autumn[1], "Sommer grün, Herbst gold");
        float[] around = a.around(0, 0, 40, Reed.Kind.SCHILF, band, flat);
        boolean within = true;
        for (int i = 0; i < around.length; i += Reed.STRIDE) if (Math.hypot(around[i], around[i + 2]) > 40 + Reed.CELL * 1.5) within = false;
        check(around.length > 0 && within, "around() sammelt die Zellen im Umkreis");
        check(Reed.Quality.LOW.range < Reed.Quality.ULTRA.range && Reed.Quality.LOW.density < Reed.Quality.ULTRA.density, "Qualitätsstufen steigen");
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private ReedCheck() { }
}
