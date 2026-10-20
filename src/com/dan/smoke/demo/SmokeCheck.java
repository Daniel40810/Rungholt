package com.dan.smoke.demo;

import com.dan.smoke.Plume;
import com.dan.smoke.Smoke;
import com.dan.smoke.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class SmokeCheck {
    private static int bad;

    public static void main(String[] a) {
        flux();
        rise();
        wind();
        spread();
        identity();
        select();
        density();
        stability();
        room();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    /** Seit Haithabu Phase 11: eine Halle wie das Versammlungshaus (17 × 6 m, First 6,2 m), zwei Türen, zwei Rauchlöcher. */
    static com.dan.smoke.RoomSmoke hall(boolean holes) {
        java.util.List<com.dan.smoke.RoomSmoke.Vent> v = new java.util.ArrayList<>();
        v.add(new com.dan.smoke.RoomSmoke.Vent(0.89, 0, 1.85, false));
        v.add(new com.dan.smoke.RoomSmoke.Vent(0.89, 0, 1.85, false));
        if (holes) {
            v.add(new com.dan.smoke.RoomSmoke.Vent(1.02, 5.24, 5.97, true));
            v.add(new com.dan.smoke.RoomSmoke.Vent(1.02, 5.24, 5.97, true));
        }
        com.dan.smoke.RoomSmoke r = new com.dan.smoke.RoomSmoke(new com.dan.smoke.RoomSmoke.Box(8.57, 2.83, 2.3, 6.19, 1.29), v);
        r.fireY = 0.06;
        return r;
    }

    static void room() {
        com.dan.smoke.RoomSmoke r = hall(true);
        com.dan.smoke.RoomSmoke.Steady s = r.steady(12, com.dan.smoke.RoomSmoke.YIELD);
        double bal = Math.abs(s.plumeFlow() - s.outFlow()) / s.plumeFlow();
        check(s.zi() > 1.7 && s.zi() < 3.5 && s.dT() > 2 && s.dT() < 25 && bal < 0.02 && 3 / s.sigma() > 3 && 3 / s.sigma() < 30,
                String.format(Locale.GERMAN, "Herdfeuer 12 kW in der Halle: Rauchschicht ab %.2f m, %.1f K wärmer, Fahne %.2f kg/s = Abfluss %.2f kg/s (± %.1f %%), Sicht in der Schicht %.0f m",
                        s.zi(), s.dT(), s.plumeFlow(), s.outFlow(), bal * 100, 3 / s.sigma()));
        com.dan.smoke.RoomSmoke.Steady shut = hall(false).steady(12, com.dan.smoke.RoomSmoke.YIELD);
        check(shut.zi() < s.zi() && shut.zi() < 1.85 && shut.sigma() > s.sigma(),
                String.format(Locale.GERMAN, "Ohne Rauchlöcher sinkt die Schicht unter den Türsturz (%.2f m statt %.2f m) und wird dichter", shut.zi(), s.zi()));
        // Füllen und Leeren
        com.dan.smoke.RoomSmoke f = hall(true);
        f.heatKw = 0;
        f.settle();
        f.heatKw = 12;
        double z2 = 0, z30 = 0, s2 = 0;
        for (int i = 0; i < 1800; i++) { f.step(1); if (i == 119) { z2 = f.layerBottom(); s2 = f.sigma(); } }
        z30 = f.layerBottom();
        double s30 = f.sigma();
        f.heatKw = 1.2 * 0.5;
        f.yield = 0.012;
        for (int i = 0; i < 7200; i++) f.step(1);
        double s2h = f.sigma();
        check(z2 > z30 && s2 < s30 && Math.abs(z30 - s.zi()) < 0.3 && s2h < s30 * 0.2,
                String.format(Locale.GERMAN, "Füllen: nach 2 min Schicht ab %.1f m (σ %.2f/m), nach 30 min ab %.1f m (σ %.2f/m); 2 h nur Glut: σ %.3f/m",
                        z2, s2, z30, s30, s2h));
        com.dan.smoke.RoomSmoke d = hall(true);
        d.heatKw = 12;
        d.settle();
        double lo = d.density(-4, 1.0, 1.5), up = d.density(-4, d.layerBottom() + 1, 1.5), plume = d.density(0, 1.0, 0);
        check(up > lo * 3 && plume > lo && lo > 0,
                String.format(Locale.GERMAN, "Dichte: unten Dunst %.3f/m, in der Schicht %.2f/m, in der Fahne über dem Feuer %.2f/m", lo, up, plume));
    }

    static float[] c(Source s, double u, double stab) { return Plume.constants(s, 1, 0, u, stab, 40, 150, 1, 0); }

    static void flux() {
        double f = Plume.buoyancyFlux(10);
        check(f > 0.08 && f < 0.1, String.format(Locale.GERMAN, "Auftriebsfluss eines Herdfeuers (10 kW) nach Briggs: %.3f m⁴/s³", f));
    }

    static void rise() {
        Source h = Source.hearth(0, 5, 0, 1), big = new Source(0, 5, 0, 3000, 1, 0.8, 2);
        double day = c(h, 2, 0)[11], eve = c(h, 2, 1)[11], windy = c(h, 8, 0)[11], fire = c(big, 2, 1)[11];
        check(day > 25 && day < 45 && eve > 6 && eve < 12 && windy < day && fire > eve * 5,
                String.format(Locale.GERMAN, "Endhöhe über dem Dach: Herdfeuer am Tag %.0f m, in der Abendinversion %.1f m, bei 8 m/s Wind %.0f m; brennendes Haus abends %.0f m", day, eve, windy, fire));
        float[] k = c(h, 2, 1);
        double r10 = Plume.rise(k, 10), r60 = Plume.rise(k, 60), r300 = Plume.rise(k, 300);
        check(r10 < r60 && r60 < r300 && r300 < k[11] && r300 > k[11] * 0.95,
                String.format(Locale.GERMAN, "Aufstieg steigt und läuft an die Endhöhe: nach 10 s %.1f m, 60 s %.1f m, 300 s %.1f m (Ende %.1f m)", r10, r60, r300, k[11]));
    }

    static void wind() {
        float[] k = c(Source.hearth(0, 5, 0, 1), 4, 0);
        double[] m = Plume.center(k, 60);
        float[] calm = c(Source.hearth(0, 5, 0, 1), 0, 0);
        double[] mc = Plume.center(calm, 60);
        check(m[0] > 150 && m[0] < 250 && Math.abs(m[2]) < 1e-9 && mc[0] < 40 && mc[1] > m[1],
                String.format(Locale.GERMAN, "Abknicken im Wind: bei 4 m/s nach 60 s %.0f m weit und %.0f m hoch; bei Windstille %.0f m weit, %.0f m hoch", m[0], m[1] - 5, mc[0], mc[1] - 5));
    }

    static void spread() {
        float[] eve = c(Source.hearth(0, 5, 0, 1), 2, 1), day = c(Source.hearth(0, 5, 0, 1), 2, 0);
        double[] s10 = Plume.sigma(eve, 10), s100 = Plume.sigma(eve, 100), d100 = Plume.sigma(day, 100);
        check(s100[0] > s10[0] && s100[0] > s100[1] * 1.5 && d100[1] > s100[1],
                String.format(Locale.GERMAN, "Zerfasern: abends nach 100 s %.1f m breit und %.1f m hoch (flach unter der Inversion), am Tag %.1f m hoch", s100[0], s100[1], d100[1]));
    }

    /** Ein Ball behält seine Nummer: einen Altersschritt später ist er Ball i + 1, mit demselben Versatz. */
    static void identity() {
        Source s = Source.hearth(3, 4, -2, 77);
        float[] k0 = Plume.constants(s, 0.6, 0.8, 3, 0.4, 40, 150, 1, 1000);
        double da = k0[7];
        float[] k1 = Plume.constants(s, 0.6, 0.8, 3, 0.4, 40, 150, 1, 1000 + da);
        double worst = 0;
        for (int i = 0; i < 39; i++) {
            double[] p = Plume.particle(k0, i), q = Plume.particle(k1, i + 1);
            double[] cp = Plume.center(k0, p[Plume.AGE]), cq = Plume.center(k1, q[Plume.AGE]);
            double[] sp = Plume.sigma(k0, p[Plume.AGE]), sq = Plume.sigma(k1, q[Plume.AGE]);
            worst = Math.max(worst, Math.abs((p[0] - cp[0]) / sp[0] - (q[0] - cq[0]) / sq[0]) + Math.abs((p[1] - cp[1]) / sp[1] - (q[1] - cq[1]) / sq[1]));
            worst = Math.max(worst, Math.abs(q[Plume.AGE] - p[Plume.AGE] - da));
        }
        Source s2 = Source.hearth(3, 4, -2, 78);
        double[] a = Plume.particle(k0, 5), b = Plume.particle(Plume.constants(s2, 0.6, 0.8, 3, 0.4, 40, 150, 1, 1000), 5), a2 = Plume.particle(Plume.constants(s, 0.6, 0.8, 3, 0.4, 40, 150, 1, 1000), 5);
        check(worst < 1e-4 && a[0] != b[0] && a[0] == a2[0],
                String.format(Locale.GERMAN, "Bälle behalten ihre Nummer und ihren Versatz über die Zeit (Abweichung %.1e), anderer Zufall andere Fahne, gleicher gleiche", worst));
    }

    static void select() {
        Smoke sm = new Smoke();
        List<Source> l = new ArrayList<>();
        for (int i = 0; i < 120; i++) l.add(new Source((i % 12) * 30, 5, (i / 12) * 30, 10, i % 10 == 9 ? 0 : 0.8, 0.1, i));
        sm.setSources(l);
        sm.select(0, 10, 0);
        Smoke.Quality q = sm.quality;
        check(sm.nearCount() == q.near && sm.nearCount() + sm.farCount() == 108 && sm.balls() == q.near * q.nearCount + (108 - q.near) * q.farCount,
                String.format(Locale.GERMAN, "Auswahl: %d Fahnen an (12 aus), %d nah, %d fern, %,d Bälle", 108, sm.nearCount(), sm.farCount(), sm.balls()));
        float[] n = sm.nearData();
        double d0 = Math.hypot(n[0], n[2]), dl = Math.hypot(n[(sm.nearCount() - 1) * Plume.FLOATS], n[(sm.nearCount() - 1) * Plume.FLOATS + 2]);
        float[] f = sm.farData();
        double df = Math.hypot(f[0], f[2]);
        check(d0 <= dl && dl <= df, String.format(Locale.GERMAN, "Die nächsten Fahnen sind die nahen: %.0f m bis %.0f m, die erste ferne %.0f m", d0, dl, df));
    }

    static void density() {
        Smoke day = new Smoke(), eve = new Smoke();
        for (Smoke s : new Smoke[]{day, eve}) { s.setSources(List.of(Source.hearth(0, 5, 0, 5))); s.windFromDeg = 270; s.windSpeed = 2; }
        day.stability = 0;
        eve.stability = 1;
        double hEve = 5 + Plume.rise(Plume.constants(Source.hearth(0, 5, 0, 5), 1, 0, 2, 1, 40, 150, 1, 0), 150 / Plume.roofWind(2));
        double eLow = eve.density(150, hEve, 0), dLow = day.density(150, hEve, 0);
        double eHigh = eve.density(150, 30, 0), dHigh = day.density(150, 30, 0), up = eve.density(-40, 12, 0);
        check(eLow > dLow && dHigh > eHigh * 10 && up < eLow * 0.01,
                String.format(Locale.GERMAN, "Dichte 150 m in Lee: auf %.0f m Höhe abends %.4f, am Tag %.4f; in 30 m Höhe abends %.5f, am Tag %.4f (der Rauch bleibt unter der Inversion); in Luv %.5f", hEve, eLow, dLow, eHigh, dHigh, up));
    }

    static void stability() {
        double evening = Smoke.stability(-2, 1.5, 0.1), day = Smoke.stability(30, 1.5, 0.1), windy = Smoke.stability(-5, 9, 0.1), cloudy = Smoke.stability(-5, 1.5, 1);
        check(evening > 0.8 && day == 0 && windy < 0.05 && cloudy < evening * 0.5,
                String.format(Locale.GERMAN, "Stabilität: Abend windstill klar %.2f, Mittag %.2f, Nacht bei 9 m/s %.2f, Nacht bedeckt %.2f", evening, day, windy, cloudy));
    }
}
