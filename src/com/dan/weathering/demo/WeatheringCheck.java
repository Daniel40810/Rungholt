package com.dan.weathering.demo;

import com.dan.weathering.Weathering;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10): Rauschen, Algenband, Rostläufer, Salz, Nässe nach Gezeit, Regen und
 * Karte, Trocknung, Voreinstellungen, Reproduzierbarkeit. Endet mit „Alles in Ordnung.“ oder den Verstößen.
 */
public final class WeatheringCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        WeatheringCheck c = new WeatheringCheck();
        for (String l : c.run()) System.out.println(l);
        System.exit(c.bad == 0 ? 0 : 1);
    }

    /** Alle Prüfungen; jede Zeile beginnt mit „  ok    “ oder „  FEHLER “, die letzte ist das Ergebnis. */
    public List<String> run() {
        lines.clear();
        bad = 0;
        // Rauschen: Bereich, Mittel, Glätte, seed
        double min = 1, max = 0, sum = 0, jump = 0;
        int n = 0;
        for (int i = 0; i < 200; i++)
            for (int j = 0; j < 200; j++) {
                double v = Weathering.noise(i * 0.137 - 13, j * 0.211 + 5, 7);
                min = Math.min(min, v);
                max = Math.max(max, v);
                sum += v;
                n++;
                jump = Math.max(jump, Math.abs(Weathering.noise(i * 0.137 - 13 + 1e-4, j * 0.211 + 5, 7) - v));
            }
        check(min >= 0 && max <= 1 && Math.abs(sum / n - 0.5) < 0.03, f("Rauschen in 0..1, Mittel %.3f", sum / n));
        check(jump < 1e-3, f("Rauschen ist glatt (Sprung bei 0,1 mm: %.1e)", jump));
        check(Weathering.noise(3.3, 4.4, 1) != Weathering.noise(3.3, 4.4, 2) && Weathering.noise(3.3, 4.4, 1) == Weathering.noise(3.3, 4.4, 1),
                "gleicher seed, gleiches Rauschen; anderer seed, anderes");
        check(Weathering.noise(-7.25, -0.5, 3) >= 0 && Weathering.noise(-7.25, -0.5, 3) <= 1, "auch für negative Koordinaten (Ganzzahl-Hash ohne Vorzeichen)");

        Weathering w = new Weathering();
        w.waterLevel = 0;
        w.waveHeight = 1;
        w.settle();
        Weathering.Snapshot s = w.snapshot();
        // Algen: Band um das Niedrigwasser, nichts weit darüber
        double low = avgAlgae(s, -1.2), mid = avgAlgae(s, 0.0), high = avgAlgae(s, 1.6), top = avgAlgae(s, 4.0), under = avgAlgae(s, -2.6);
        check(low > 0.4 && mid > high && top == 0 && under < low, f("Algen: um MNW %.2f, Mitte %.2f, MHW %.2f, 4 m %.2f, tief darunter %.2f", low, mid, high, top, under));
        double gLow = Weathering.algae(s, -1.3, 3, -1.3)[1], gHigh = Weathering.algae(s, 0.6, 3, 0.6)[1], gDeep = Weathering.algae(s, -2.6, 3, -2.6)[1];
        check(gLow > 0.8 && gHigh < 0.3 && gDeep < 0.3, f("am Niedrigwasser grüner Schleim (%.2f), darüber (%.2f) und tief darunter (%.2f) braun", gLow, gHigh, gDeep));
        // Rost: unter Kanten mehr als weit darunter, ohne Anfälligkeit nichts
        double near = 0, far = 0;
        for (int i = 0; i < 400; i++) {
            near += Weathering.rust(s, i * 0.05, 5, 0.05, 0.4);
            far += Weathering.rust(s, i * 0.05, 5, 6.0, 0.4);
        }
        check(near > far * 2, f("Rostläufer auf Farbe: direkt unter der Kante %.2f, 6 m darunter %.2f", near / 400, far / 400));
        check(Weathering.rust(s, 1, 1, 0.1, 0) == 0, "frische Farbe (Anfälligkeit 0) rostet nicht");
        // Salz nur über dem Auflauf
        double saltIn = 0, saltOut = 0;
        for (int i = 0; i < 400; i++) {
            saltIn += Weathering.salt(s, s.level() + s.runup() + 0.8, 0, i * 0.07, 2);
            saltOut += Weathering.salt(s, s.level() - 0.5, 0, i * 0.07, 2) + Weathering.salt(s, s.level() + s.sprayTop() + 4, 0, i * 0.07, 2);
        }
        check(saltIn > 0.05 * 400 && saltOut == 0, f("Salz über dem Auflauf (%.2f), nicht unter Wasser und nicht hoch oben", saltIn / 400));
        // Nässe: unter Wasser 1, im Auflauf nass, darüber trocken
        double[] up = {1, 0, 0};
        check(Weathering.wetness(s, -0.5, 1, 0, 0, 1, 1, 0) == 1, "unter Wasser ganz nass");
        check(avgWet(s, s.runup() - 0.4, up) > 0.9 && avgWet(s, 6, up) == 0, f("Auflauf %.1f m nass, 6 m über dem Wasser trocken", s.runup()));
        // fallendes Wasser: die nasse Zone bleibt zurück und trocknet nach
        Weathering e = new Weathering();
        e.waveHeight = 0.5;
        e.windSpeed = 7;
        e.sun = 0.5;
        e.waterLevel = 1.5;
        e.step(1);
        double hw = e.wetLine();
        for (int i = 0; i < 3600; i++) { e.waterLevel = 1.5 - 1.0 * i / 3600.0; e.step(1); }
        double lag = e.wetLine() - (e.waterLevel + Weathering.runup(e.waveHeight));
        check(hw > 1.7 && lag > 0.2 && lag < 1.0, f("Ebbe fällt 1 m in einer Stunde: die nasse Linie hinkt %.2f m nach (trocknet %.2f m/h)", lag, e.dryingRate()));
        for (int i = 0; i < 7200; i++) e.step(1);
        check(Math.abs(e.wetLine() - (e.waterLevel + Weathering.runup(e.waveHeight))) < 1e-9, "bei stehendem Wasser ist die Fläche darüber nach zwei Stunden trocken");
        // Regen: Wetterseite nasser als Leeseite; trocknet nach dem Regen
        Weathering r = new Weathering();
        r.waterLevel = -1;
        r.windFromDeg = 270;
        r.rainRate = 3;
        for (int i = 0; i < 1200; i++) r.step(1);
        Weathering.Snapshot rs = r.snapshot();
        double windward = Weathering.wetness(rs, 5, -1, 0, 0, 2, 5, 0), lee = Weathering.wetness(rs, 5, 1, 0, 0, 2, 5, 0);
        check(rs.rainWet() > 0.95 && windward > 0.95 && lee < 0.5, f("Regen aus West: Westseite %.2f nass, Ostseite %.2f", windward, lee));
        r.rainRate = 0;
        for (int i = 0; i < 3600; i++) r.step(1);
        check(r.snapshot().rainWet() < 0.05, f("eine Stunde nach dem Regen fast trocken (%.2f)", r.snapshot().rainWet()));
        // Karte: Brecher macht nass, unten länger als oben, trocknet
        Weathering m = new Weathering();
        m.waterLevel = -1;
        m.quality = Weathering.Quality.MEDIUM;
        m.wet(0.25, 0.5, 2, 1);
        Weathering.Snapshot ms = m.snapshot();
        double hit = ms.mapAt(0.25, 0.5), below = ms.mapAt(0.25, 0.5 - 2.5 / m.mapHeightM), above = ms.mapAt(0.25, 0.5 + 2.5 / m.mapHeightM);
        check(hit > 0.9 && below > above, f("Brecher auf der Karte: Treffer %.2f, darunter %.2f, darüber %.2f", hit, below, above));
        check(ms.mapAt(0.25 + 1, 0.5) == hit, "Karte läuft rundum (u = 1,25 wie 0,25)");
        for (int i = 0; i < 1800; i++) m.step(1);
        check(m.snapshot().mapAt(0.25, 0.5) < 0.5 * hit, f("nach 30 min hat die Karte mehr als die Hälfte verloren (%.2f)", m.snapshot().mapAt(0.25, 0.5)));
        check(m.snapshot().mapVersion() != ms.mapVersion(), "neue Version der Karte, wenn sie sich ändert (Hochladen nur dann)");
        // Fläche: nass ist dunkler und glatter
        Weathering.Surface dry = Weathering.surface(s, new double[]{0.5, 0.5, 0.5}, 0.6, 0.8, new double[]{0, 5, 0}, up, 1, 5, 3, 0, 0);
        Weathering.Surface wet = Weathering.surface(s, new double[]{0.5, 0.5, 0.5}, 0.6, 0.8, new double[]{0, -0.5, 0}, up, 1, 5, 3, 0, 0);
        check(wet.g() < dry.g() * 0.7 && wet.rough() < 0.3 && dry.rough() == 0.6, f("nass dunkler (%.2f statt %.2f) und glatter (%.2f)", wet.g(), dry.g(), wet.rough()));
        // Voreinstellungen
        check(Weathering.calm().waveHeight < Weathering.breeze().waveHeight && Weathering.breeze().waveHeight < Weathering.gale().waveHeight
                && Weathering.gale().waveHeight < Weathering.storm().waveHeight && Weathering.storm().rainRate > 0, "Voreinstellungen steigen von calm bis storm");
        check(Weathering.beaufort(-2).windSpeed == Weathering.beaufort(0).windSpeed && Weathering.beaufort(20).waveHeight == Weathering.beaufort(12).waveHeight,
                "beaufort(n) begrenzt auf 0..12");
        check(Weathering.runup(3) > Weathering.runup(1) && Weathering.sprayTop(3, 20) > Weathering.sprayTop(3, 5), "Auflauf wächst mit dem Seegang, Spritzwasser mit Seegang und Wind");
        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        return new ArrayList<>(lines);
    }

    static double avgAlgae(Weathering.Snapshot s, double y) {
        double a = 0;
        for (int i = 0; i < 300; i++) a += Weathering.algae(s, y, i * 0.13, y)[0];
        return a / 300;
    }

    static double avgWet(Weathering.Snapshot s, double y, double[] n) {
        double a = 0;
        for (int i = 0; i < 300; i++) a += Weathering.wetness(s, s.level() + y, n[0], n[1], n[2], i * 0.13, y, 0);
        return a / 300;
    }

    private void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }
}
