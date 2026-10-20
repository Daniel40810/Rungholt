package com.dan.hearth.demo;

import com.dan.hearth.Blaze;
import com.dan.hearth.Fire;
import com.dan.hearth.Flame;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ */
public final class HearthCheck {
    private static int bad;

    public static void main(String[] a) {
        flames();
        flicker();
        light();
        ignite();
        embers();
        house();
        spread();
        wet();
        seed();
        state();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void check(boolean ok, String what) {
        if (!ok) bad++;
        System.out.println((ok ? "  ok   " : "  FEHLER ") + what);
    }

    static void flames() {
        double herd = Fire.flameHeight(10), torch = Fire.flameHeight(3), house = Fire.flameHeight(8000);
        check(herd > 0.5 && herd < 0.7 && torch > 0.3 && torch < 0.45 && house > 7 && house < 9.5,
                String.format(Locale.GERMAN, "Flammenhöhe nach Heskestad: Herdfeuer %.2f m, Fackel %.2f m, Haus mit 8 MW %.1f m", herd, torch, house));
        Fire f = new Fire(Fire.Kind.HERD, 0, 0, 0, 1);
        check(f.flame() == null && new Fire(Fire.Kind.FACKEL, 0, 2, 0, 2).flame() != null, "Der Herd im Haus zeigt keine Flamme nach außen, die Fackel schon");
    }

    static void flicker() {
        Fire t = new Fire(Fire.Kind.FACKEL, 0, 0, 0, 7), h = new Fire(Fire.Kind.HERD, 0, 0, 0, 7);
        double sum = 0, min = 9, max = 0, sh = 0, minH = 9, maxH = 0;
        int n = 6000;
        for (int i = 0; i < n; i++) {
            double v = t.flicker(i * 0.01), w = h.flicker(i * 0.01);
            sum += v; min = Math.min(min, v); max = Math.max(max, v);
            sh += w; minH = Math.min(minH, w); maxH = Math.max(maxH, w);
        }
        check(Math.abs(sum / n - 1) < 0.05 && max - min > maxH - minH && t.flicker(12.34) == t.flicker(12.34),
                String.format(Locale.GERMAN, "Flackern um 1 (Mittel %.3f): Fackel %.2f bis %.2f, Herd ruhiger %.2f bis %.2f; gleiche Zeit, gleicher Wert", sum / n, min, max, minH, maxH));
    }

    static void light() {
        Fire c = new Fire(Fire.Kind.FEUERSTELLE, 0, 0, 0, 3);
        double lm = Fire.lumenOf(c.heatKw());
        double[] rgb = c.intensity(1.0);
        check(lm > 1500 && lm < 5000 && rgb[0] > rgb[1] && rgb[1] > rgb[2] * 3,
                String.format(Locale.GERMAN, "Licht der Feuerstelle %,.0f lm, warm (R %.0f, G %.0f, B %.0f cd)", lm, rgb[0], rgb[1], rgb[2]));
    }

    static void ignite() {
        Fire f = new Fire(Fire.Kind.FEUERSTELLE, 0, 0, 0, 3);
        f.level = 0;
        double t = 0;
        while (f.level < 0.9 && t < 200) { f.step(0.1); t += 0.1; }
        f.lit = false;
        double t2 = 0;
        while (f.level > 0 && t2 < 400) { f.step(0.1); t2 += 0.1; }
        check(t > 10 && t < 30 && t2 > 60 && t2 < 200, String.format(Locale.GERMAN, "Anzünden %.0f s bis 90 %%, Ausgehen %.0f s", t, t2));
    }

    /** Seit Haithabu Phase 11: nach dem Ausgehen glimmt die Glut stundenlang, tiefrot und schwach. */
    static void embers() {
        Fire f = new Fire(Fire.Kind.HERD, 0, 0, 0, 5);
        for (int i = 0; i < 1800; i++) f.step(1);
        double full = f.glow;
        f.lit = false;
        for (int i = 0; i < 4 * 3600; i++) f.step(1);
        double later = f.glow, lm = f.emberLumen(0), lmFlame = Fire.lumenOf(Fire.Kind.HERD.heatKw);
        double[] rgb = f.intensity(0);
        Flame e = f.ember();
        check(full > 0.9 && later > 0.15 && later < 0.4 && f.level == 0 && lm > 2 && lm < lmFlame / 50 && rgb[0] > rgb[1] * 5 && e != null && e.kind() == Flame.EMBER,
                String.format(Locale.GERMAN, "Glut: nach 30 min Feuer voll (%.2f), 4 h nach dem Ausgehen noch %.2f; %,.1f lm statt %,.0f lm der Flamme, tiefrot (R %.2f, G %.2f cd)",
                        full, later, lm, lmFlame, rgb[0], rgb[1]));
    }

    static Blaze.Building house(double x, double z) { return new Blaze.Building(x, 0, z, 90, 6, 2.5, 2, 6, true); }

    static void house() {
        Blaze b = new Blaze(List.of(house(0, 0)), 1);
        b.ignite(0);
        double peak = 0, tPeak = 0, black = 0, blackLate = 1;
        boolean sawFlames = false, sawFloor = false;
        while (b.time() < 3 * 3600) {
            b.step(5);
            if (b.hrr(0) > peak) { peak = b.hrr(0); tPeak = b.time(); }
            if (b.time() > 120 && b.time() < 400) black = Math.max(black, b.blackness(0));
            if (b.phase(0) == Blaze.Phase.GLUT) blackLate = Math.min(blackLate, b.blackness(0));
            List<Flame> fl = b.flames(0);
            if (!b.collapsed(0) && fl.size() > 4) sawFlames = true;
            if (b.collapsed(0) && !fl.isEmpty() && fl.get(0).y() < 1) sawFloor = true;
        }
        double c = b.collapsedAt(0), energy = b.released() / b.fuelTotal();
        check(peak > 10_000 && peak < 40_000 && c > 8 * 60 && c < 25 * 60 && b.phase(0) == Blaze.Phase.ASCHE,
                String.format(Locale.GERMAN, "Ein Wohnhaus brennt: höchstens %.1f MW nach %.0f min, das Dach stürzt nach %.0f min ein, nach 3 h Asche", peak / 1000, tPeak / 60, c / 60));
        check(energy > 0.98 && energy < 1.0001 && black > 0.7 && blackLate < 0.4 && sawFlames && sawFloor,
                String.format(Locale.GERMAN, "Energie %.1f %% des Brennstoffs; Rauch erst schwarz (%.2f), in der Glut hell (%.2f); Flammen erst auf dem Dach, dann am Boden", energy * 100, black, blackLate));
    }

    static void spread() {
        List<Blaze.Building> l = new ArrayList<>();
        l.add(house(0, 0));      // brennt zuerst
        l.add(house(0, 13));     // 13 m in Lee (Wind aus Nord weht nach Süden, +z)
        l.add(house(0, -13));    // 13 m in Luv
        l.add(house(45, 0));     // 45 m quer
        Blaze b = new Blaze(l, 3);
        b.windFromDeg = 0;
        b.windSpeed = 6;
        b.ignite(0);
        b.step(1800);
        boolean lee = b.phase(1) != Blaze.Phase.INTAKT, luv = b.phase(2) != Blaze.Phase.INTAKT, far = b.phase(3) != Blaze.Phase.INTAKT;
        Blaze calm = new Blaze(List.of(house(0, 0), house(0, 13)), 3);
        calm.windSpeed = 0;
        calm.ignite(0);
        calm.step(1800);
        check(lee && !luv && !far && calm.phase(1) == Blaze.Phase.INTAKT,
                String.format(Locale.GERMAN, "Ausbreitung bei 6 m/s: das Haus 13 m in Lee fängt nach %.0f s Feuer, das in Luv und das 45 m entfernte nicht; bei Windstille springt es nicht über", b.ignitedAt(1)));
    }

    static void wet() {
        double dry = timeToIgnite(0), wet = timeToIgnite(0.8);
        check(Double.isFinite(dry) && (Double.isNaN(wet) || wet > dry * 1.5),
                String.format(Locale.GERMAN, "Nasses Reet fängt später oder gar nicht (trocken nach %.0f s, nass %s)", dry, Double.isNaN(wet) ? "nicht in 30 min" : String.format(Locale.GERMAN, "nach %.0f s", wet)));
    }

    static double timeToIgnite(double wetness) {
        Blaze b = new Blaze(List.of(house(0, 0), house(0, 9)), 11);
        b.windFromDeg = 0;
        b.windSpeed = 5;
        b.wetness = wetness;
        b.ignite(0);
        b.step(1800);
        return b.ignitedAt(1);
    }

    static void seed() {
        double[] r = new double[2];
        for (int k = 0; k < 2; k++) {
            List<Blaze.Building> l = new ArrayList<>();
            for (int i = 0; i < 8; i++) l.add(house((i % 4) * 14, (i / 4) * 12));
            Blaze b = new Blaze(l, 950);
            b.windFromDeg = 270;
            b.windSpeed = 7;
            b.ignite(0);
            b.step(3600);
            r[k] = b.released() + b.ignitedAt(3);
        }
        check(r[0] == r[1], "Gleicher Startwert, gleicher Brand");
    }

    /** Seit Haithabu Phase 16: der Zustand eines Brands als Text hin und zurück, danach läuft er gleich weiter. */
    static void state() {
        List<Blaze.Building> l = new ArrayList<>();
        l.add(house(0, 0));
        l.add(house(0, 13));
        l.add(house(45, 0));
        Blaze a = new Blaze(l, 3);
        a.windFromDeg = 0;
        a.windSpeed = 6;
        a.ignite(0);
        a.step(400);
        String st = a.state();
        Blaze b = new Blaze(l, 3);
        b.windFromDeg = 0;
        b.windSpeed = 6;
        b.setState(st);
        boolean same = b.state().equals(st) && b.time() == a.time() && b.phase(0) == a.phase(0) && b.hrr(0) == a.hrr(0);
        a.step(300);
        b.step(300);
        check(same && Math.abs(a.totalHrr() - b.totalHrr()) < 1e-6 * Math.max(1, a.totalHrr()) && a.phase(1) == b.phase(1),
                String.format(Locale.GERMAN, "Zustand als Text (%d Zeichen) hin und zurück gleich; 300 s später dieselbe Leistung (%.0f kW)", st.length(), a.totalHrr()));
    }
}
