package com.dan.weather.demo;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“. Geprüft wird „Ein Sturmtag“
 * in Zahlen (Barometer fällt vor dem Sturm, Dünung vor dem Wind, Cirren vor der Front, Bft 9 am Nachmittag, Gewitter
 * in der Dämmerung, Aufklaren mit Regenbogen), weiche Übergänge, die freie Regie über 60 Tage, Handregler und
 * „Regie weiter“, Anhalten, Sprünge der Uhr, Böen, das Regiebuch als Text und die Hilfen (Beaufort, Taupunkt).
 */
public final class WeatherCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public static void main(String[] args) {
        for (String l : new WeatherCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    /** 16.10.2026, Stunde h in Berlin, als s seit 1970. */
    static double at(double h) {
        return LocalDate.of(2026, 10, 16).atStartOfDay(ZONE).toEpochSecond() + h * 3600;
    }

    static double hourOf(double t) {
        var z = Instant.ofEpochSecond((long) t).atZone(ZONE);
        return z.getHour() + z.getMinute() / 60.0 + (z.getDayOfMonth() - 16) * 24;
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Regiebuch
        Scenario s = Scenario.stormDay();
        check(s.steps().size() == 9 && Math.abs(s.hours() * 3600 / s.lapse() / 60 - 4) < 0.1,
                f("„Ein Sturmtag“: %d Schritte, %.0f h, im Zeitraffer %.0f : 1 = %.1f min", s.steps().size(), s.hours(), s.lapse(), s.hours() * 60 / s.lapse()));
        Scenario back = Scenario.parse(s.toText());
        boolean same = back.name().equals(s.name()) && back.steps().size() == s.steps().size() && back.startHour() == s.startHour() && back.lapse() == s.lapse();
        for (int i = 0; same && i < s.steps().size(); i++) {
            Scenario.Step a = s.steps().get(i), b = back.steps().get(i);
            same = a.phase() == b.phase() && a.hours() == b.hours() && a.text().equals(b.text()) && a.camera().equals(b.camera());
            for (Weather.Var v : Weather.Var.values()) same &= Double.compare(a.value(v), b.value(v)) == 0;
        }
        String err = "";
        try { Scenario.parse("Szenario: x\nSchritt: 2 | STURMFLUT | zu viel"); } catch (IllegalArgumentException e) { err = e.getMessage(); }
        check(same && err.contains("Zeile 2") && err.contains("STURMFLUT"), "Regiebuch als Text: hin und zurück gleich; Fehler mit Zeilennummer („" + err + "“)");

        // ------------------------------------------------ Sturmtag in Zahlen
        double t0 = at(s.startHour());
        List<Weather.Snapshot> run = Weather.run(s, t0, s.hours(), 300, 1885);
        List<Weather.Snapshot> again = Weather.run(s, t0, s.hours(), 300, 1885);
        boolean rep = true;
        for (int i = 0; i < run.size(); i++) rep &= run.get(i).pressure() == again.get(i).pressure() && run.get(i).gustNow() == again.get(i).gustNow();
        List<Weather.Snapshot> other = Weather.run(s, t0, s.hours(), 300, 99);
        boolean diff = false;
        for (int i = 0; i < run.size(); i++) diff |= run.get(i).gustNow() != other.get(i).gustNow();
        check(rep && diff && Math.abs(run.get(100).pressure() - other.get(100).pressure()) < 1e-9,
                "gleicher seed, gleicher Tag; anderer seed: andere Böen, aber dasselbe Regiebuch");
        Weather.Snapshot m = byHour(run, 7);
        check(m.beaufort() <= 1 && m.effectiveVisibilityKm() <= 10 && m.oktas() <= 2,
                f("07:00 Morgenstille: Bft %d, Sicht %.1f km (Dunst), %.1f/8", m.beaufort(), m.effectiveVisibilityKm(), m.oktas()));
        Weather.Snapshot storm = null;
        for (Weather.Snapshot r : run) if (r.beaufort() >= 9) { storm = r; break; }
        double stormH = storm == null ? -1 : hourOf(storm.time());
        check(storm != null && stormH >= 14 && stormH <= 17, f("Bft 9 am Nachmittag: erstmals um %s", hhmm(stormH)));
        Weather.Snapshot before6 = storm == null ? m : byTime(run, storm.time() - 6 * 3600), before3 = storm == null ? m : byTime(run, storm.time() - 3 * 3600);
        double fall = storm == null ? 0 : before6.pressure() - storm.pressure();
        check(fall >= 6 && before3.tendency() < -1.5 && before3.tendencyText().contains("fallend"),
                f("Barometer fällt vor dem Sturm: −%.1f hPa in den 6 h davor, 3 h davor %+.1f hPa/3 h (%s)", fall, before3.tendency(), before3.tendencyText()));
        double pMin = 1e9, pMax = -1e9;
        for (Weather.Snapshot r : run) { pMin = Math.min(pMin, r.pressure()); pMax = Math.max(pMax, r.pressure()); }
        check(pMin > 975 && pMin < 995 && pMax < 1030, f("Luftdruck zwischen %.1f und %.1f hPa (Tief über der Deutschen Bucht)", pMin, pMax));
        double swellHalf = firstAbove(run, Weather.Snapshot::swellHeight, 0.5), windHalf = firstAbove(run, Weather.Snapshot::wind, 0.5);
        check(windHalf - swellHalf >= 1.0, f("Dünung vor dem Wind: halbe Höhe um %s, halber Wind erst um %s", hhmm(hourOf(swellHalf)), hhmm(hourOf(windHalf))));
        double cirrus = Double.NaN, rain = Double.NaN;
        for (Weather.Snapshot r : run) {
            if (Double.isNaN(cirrus) && r.high() >= 0.5) cirrus = r.time();
            if (Double.isNaN(rain) && r.rain() >= 1) rain = r.time();
        }
        check(!Double.isNaN(cirrus) && rain - cirrus >= 2 * 3600, f("Cirren vor der Front: hohe Schicht um %s, Regen ab %s", hhmm(hourOf(cirrus)), hhmm(hourOf(rain))));
        double thunder = Double.NaN;
        for (Weather.Snapshot r : run) if (Double.isNaN(thunder) && r.thunder() >= 2) thunder = r.time();
        check(!Double.isNaN(thunder) && hourOf(thunder) >= 17.5 && hourOf(thunder) <= 20, f("Gewitter in der Dämmerung: ab %s", hhmm(hourOf(thunder))));
        double dw = 0, dp = 0, dc = 0;
        boolean wet = true;
        for (int i = 1; i < run.size(); i++) {
            dw = Math.max(dw, Math.abs(run.get(i).wind() - run.get(i - 1).wind()));
            dp = Math.max(dp, Math.abs(run.get(i).pressure() - run.get(i - 1).pressure()));
            dc = Math.max(dc, Math.abs(run.get(i).oktas() - run.get(i - 1).oktas()));
            if (run.get(i).rain() > 1 && run.get(i).oktas() < 3.5) wet = false;
        }
        check(dw < 1.5 && dp < 0.6 && dc < 0.6, f("weiche Übergänge: je 5 min höchstens %.2f m/s, %.2f hPa, %.2f Achtel", dw, dp, dc));
        check(wet, "Regen nur unter Wolken (ab 1 mm/h mindestens 3,5/8; im Schauer bricht die Decke schon auf)");
        double bow = 0;
        for (Weather.Snapshot r : run) if (hourOf(r.time()) > 32) bow = Math.max(bow, r.showerEnd());
        Weather.Snapshot end = run.get(run.size() - 1);
        check(bow > 0.3 && end.rain() < 0.3 && end.oktas() < 4.5 && end.pressure() > 1005,
                f("Aufklaren: Regenbogen-Lage %.2f nach dem letzten Schauer, am Ende %.1f mm/h, %.1f/8, %.0f hPa %s", bow, end.rain(), end.oktas(), end.pressure(), end.tendencyText()));
        double veerA = byHour(run, 10).fromDeg(), veerB = byHour(run, 29).fromDeg();
        check(veerA > 160 && veerA < 230 && veerB > 270 && veerB < 330, f("Wind dreht mit der Front von %03.0f° (vormittags) auf %03.0f° (nach dem Sturm)", veerA, veerB));

        // ------------------------------------------------ Böen
        Weather w = new Weather();
        w.start(Scenario.stormDay(), t0);
        w.update(t0 + 9 * 3600, 0);
        double sum = 0, mx = 0;
        int n = 6000;
        for (int i = 0; i < n; i++) {
            Weather.Snapshot r = w.update(t0 + 9 * 3600, 0.1);
            sum += r.gustNow() / r.wind();
            mx = Math.max(mx, r.gustNow() / r.wind());
        }
        Weather.Snapshot g = w.snapshot();
        check(Math.abs(sum / n - 1) < 0.06 && mx > 1 + 1.5 * g.gustiness() && mx <= 1 + 3 * g.gustiness() + 1e-9,
                f("Böen in Echtzeit: Mittel %.3f · Wind, stärkste in 10 min %.2f · Wind (Böigkeit %.2f, Böe nach WMO bis %.2f · Wind)", sum / n, mx, g.gustiness(), g.gustMax() / g.wind()));

        // ------------------------------------------------ Handregler, Regie weiter, Anhalten, Sprünge
        Weather h = new Weather();
        h.start(Scenario.stormDay(), t0);
        h.update(t0 + 3600, 1);
        h.set(Weather.Var.WIND, 30);
        boolean held = true;
        for (int i = 1; i <= 24; i++) held &= Math.abs(h.update(t0 + 3600 + i * 300, 1).wind() - 30) < 1e-9;
        h.release();
        for (int i = 1; i <= 48; i++) h.update(t0 + 3 * 3600 + i * 300, 1);
        Weather ref = new Weather();
        ref.start(Scenario.stormDay(), t0);
        for (int i = 0; i <= 84; i++) ref.update(t0 + i * 300, 1);
        double back4 = h.snapshot().wind(), ref4 = ref.snapshot().wind();
        check(held && Math.abs(back4 - ref4) < 0.15 * Math.max(1, ref4) && h.overrides() == 0,
                f("Handregler hält Wind 30 m/s zwei Stunden; nach „Regie weiter“ in 4 h zurück: %.1f gegen %.1f m/s", back4, ref4));
        Weather p = new Weather();
        p.start(Scenario.stormDay(), t0);
        p.update(t0 + 8 * 3600, 1);
        Weather.Snapshot a0 = p.snapshot();
        p.running = false;
        for (int i = 1; i <= 36; i++) p.update(t0 + 8 * 3600 + i * 300, 1);
        Weather.Snapshot a1 = p.snapshot();
        p.running = true;
        p.update(t0 + 11 * 3600 + 300, 1);
        check(a1.pressure() == a0.pressure() && a1.phase() == a0.phase() && Math.abs(p.snapshot().pressure() - a0.pressure()) < 0.6,
                f("Regie angehalten: 3 h lang %.1f hPa, %s; danach geht es dort weiter", a1.pressure(), a1.phase().label));
        Weather j = new Weather();
        j.start(Scenario.stormDay(), t0);
        j.update(t0 + 3600, 1);
        Weather.Snapshot jump = j.update(t0 + 12 * 3600, 1);
        Weather jr = new Weather();
        jr.start(Scenario.stormDay(), t0);
        for (int i = 0; i <= 144; i++) jr.update(t0 + i * 300, 1);
        check(Math.abs(jump.wind() - jr.snapshot().wind()) < 2.5 && jump.phase() == jr.snapshot().phase(),
                f("Uhr springt 11 h vor: sofort %.1f m/s, %s (durchgerechnet %.1f m/s)", jump.wind(), jump.phase().label, jr.snapshot().wind()));

        // ------------------------------------------------ Phase 13: ein Nebeltag
        Scenario fd = Scenario.fogDay();
        double tf0 = at(fd.startHour());
        List<Weather.Snapshot> fr0 = Weather.run(fd, tf0, fd.hours(), 300, 1885);
        Weather.Snapshot dawn = byHour(fr0, 6.5), noon = byHour(fr0, 11.5), flat = byHour(fr0, 15.0), after = byHour(fr0, 19.3);
        Scenario fdBack = Scenario.parse(fd.toText());
        check(dawn.fog() < 0.05 && noon.fog() > 0.9 && noon.phase() == Weather.Phase.NEBEL && flat.fog() > 0.3 && flat.fog() < 0.6 && after.fog() < 0.05
                        && noon.beaufort() <= 2 && fdBack.steps().size() == fd.steps().size() && fdBack.steps().get(2).camera().equals("Schiffsbrücke"),
                f("„Ein Nebeltag“: 06:30 Nebel %.2f, 11:30 %.2f (Bft %d, %s), 15:00 flach %.2f, 19:20 %.2f; Regiebuch als Text hin und zurück",
                        dawn.fog(), noon.fog(), noon.beaufort(), noon.phase().label, flat.fog(), after.fog()));

        // ------------------------------------------------ seit Haithabu Phase 3: ein Nebelmorgen im Herbst
        Scenario am = Scenario.autumnFogMorning();
        List<Weather.Snapshot> ar = Weather.run(am, at(am.startHour()), am.hours(), 300, 1885);
        Weather.Snapshot a6 = byHour(ar, 6.0), a9 = byHour(ar, 9.0), a13 = byHour(ar, 13.0);
        Scenario amBack = Scenario.parse(am.toText());
        check(a6.fog() > 0.75 && a6.beaufort() <= 1 && a9.fog() > 0.3 && a9.fog() < a6.fog() - 0.1 && a13.fog() < 0.05 && a13.temperature() > a6.temperature() + 4
                        && amBack.steps().size() == am.steps().size(),
                f("„Ein Nebelmorgen im Herbst“: 06:00 Nebel %.2f (Bft %d, %.1f °C), 09:00 %.2f, 13:00 %.2f (%.1f °C); als Text hin und zurück",
                        a6.fog(), a6.beaufort(), a6.temperature(), a9.fog(), a13.fog(), a13.temperature()));

        // ------------------------------------------------ freie Regie über 60 Tage
        Weather fr = new Weather();
        fr.seed = 4711;
        double tf = at(0);
        fr.startFree(tf, Weather.Phase.KLAR);
        double[] share = new double[Weather.Phase.values().length];
        boolean legal = true;
        Weather.Phase last = null;
        List<Weather.Phase> seen = new ArrayList<>();
        double warm = 0, cold = 0;
        int nWarm = 0, nCold = 0;
        for (double t = tf; t < tf + 60 * 86400; t += 600) {
            Weather.Snapshot r = fr.update(t, 0.05);
            share[r.phase().ordinal()]++;
            if (last != null && r.phase() != last) {
                legal &= !(last == Weather.Phase.KLAR && r.phase() == Weather.Phase.STURM) && !(last == Weather.Phase.STURM && r.phase() == Weather.Phase.KLAR);
                seen.add(r.phase());
            }
            last = r.phase();
            double hh = hourOf(t) % 24;
            if (r.phase() == Weather.Phase.KLAR) {
                if (Math.abs(hh - 15) < 0.5) { warm += r.temperature(); nWarm++; }
                if (Math.abs(hh - 4) < 0.5) { cold += r.temperature(); nCold++; }
            }
        }
        double tot = Arrays.stream(share).sum(), stormShare = (share[3] + share[4]) / tot;
        boolean all = true;
        int chain = 0;
        for (Weather.Phase ph : Weather.Phase.values()) {
            if (ph == Weather.Phase.FOEHN || ph == Weather.Phase.ORKAN) { all &= share[ph.ordinal()] == 0; continue; }   // nur mit eigenem Klima oder im Szenario
            all &= share[ph.ordinal()] > 0;
            chain++;
        }
        check(all && legal && stormShare > 0.05 && stormShare < 0.3 && seen.size() > 30,
                f("Freie Regie, 60 Tage: alle %d Lagen der Kette (Föhnsturm und Orkan nur mit eigenem Klima), %d Wechsel nur entlang der Kette, Sturm und Gewitter %.0f %%, Seenebel %.0f %% der Zeit",
                        chain, seen.size(), stormShare * 100, share[Weather.Phase.NEBEL.ordinal()] / tot * 100));
        // Seit Glacier Phase 3: eigenes Klima mit Föhnsturm
        Weather pf = new Weather();
        pf.seed = 4711;
        double[][] mk = new double[9][9];
        mk[0][8] = 1; mk[8][0] = 1;
        for (int i = 1; i < 8; i++) mk[i][0] = 1;
        double[][] tg = new double[9][];
        for (int i = 0; i < 9; i++) tg[i] = Weather.target(Weather.Phase.values()[i]);   // eine alte Tabelle mit neun Zeilen (ohne Orkan)
        tg[8][0] = 21;
        pf.transitions = mk;
        pf.targets = tg;
        pf.startFree(tf, Weather.Phase.KLAR);
        int foehn = 0;
        double maxW = 0;
        for (double t = tf; t < tf + 10 * 86400; t += 600) {
            Weather.Snapshot r = pf.update(t, 0.05);
            if (r.phase() == Weather.Phase.FOEHN) { foehn++; maxW = Math.max(maxW, r.wind()); }
        }
        check(foehn > 100 && maxW > 20, f("Eigenes Klima: Föhnsturm erreicht (%d von 1440 Proben), Wind bis %.1f m/s aus der eigenen Zielzeile", foehn, maxW));
        // ------------------------------------------------ seit Rungholt Phase 3: Orkan aus WNW, auch mit alten Tabellen (9 Zeilen)
        Weather ok = new Weather();
        ok.transitions = pf.transitions;
        ok.startFree(tf, Weather.Phase.ORKAN);
        double maxO = 0, minP = 2000, dirO = 0;
        Weather.Phase nachOrkan = null;
        for (double t = tf; t < tf + 2 * 86400; t += 600) {
            Weather.Snapshot r = ok.update(t, 0.05);
            if (r.phase() == Weather.Phase.ORKAN) { if (r.wind() > maxO) { maxO = r.wind(); dirO = r.fromDeg(); } minP = Math.min(minP, r.pressure()); }
            else if (nachOrkan == null) nachOrkan = r.phase();
        }
        check(maxO > 28 && minP < 975 && dirO > 270 && dirO < 320 && (nachOrkan == Weather.Phase.STURM || nachOrkan == Weather.Phase.ABKLINGEND),
                f("Orkan: Wind bis %.1f m/s aus %03.0f°, Druck %.0f hPa, danach %s (mit einer alten Tabelle aus neun Zeilen)", maxO, dirO, minP, nachOrkan == null ? "—" : nachOrkan.label));
        check(nWarm > 0 && nCold > 0 && warm / nWarm - cold / nCold > 2.5, f("Tagesgang bei klarem Wetter: 15 Uhr %.1f °C, 4 Uhr %.1f °C", warm / nWarm, cold / nCold));

        // ------------------------------------------------ Hilfen
        check(Weather.beaufort(0.2) == 0 && Weather.beaufort(0.3) == 1 && Weather.beaufort(20.79) == 8 && Weather.beaufort(20.8) == 9 && Weather.beaufort(33) == 12
                        && Weather.beaufort(Weather.beaufortMid(7)) == 7, "Beaufort nach WMO (Grenzen 0,3 … 32,7 m/s), Mitten liegen in ihrer Stufe");
        double td = Weather.dewPoint(20, 50);
        check(Math.abs(td - 9.3) < 0.2, f("Taupunkt nach Magnus: 20 °C, 50 %% → %.1f °C", td));
        Weather.Snapshot st = byHour(run, 16);
        check(st.barometerWord().equals("Sturm") || st.barometerWord().equals("Regen"), f("Barometer um 16 Uhr: %.0f hPa, Feld „%s“, Tendenz %s", st.pressure(), st.barometerWord(), st.tendencyText()));

        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        return lines;
    }

    interface F { double of(Weather.Snapshot s); }

    /** Erste Zeit, zu der f die Hälfte (frac) seines Höchstwerts erreicht. */
    static double firstAbove(List<Weather.Snapshot> run, F f, double frac) {
        double mx = 0;
        for (Weather.Snapshot r : run) mx = Math.max(mx, f.of(r));
        double lo = f.of(run.get(0));
        for (Weather.Snapshot r : run) if (f.of(r) >= lo + (mx - lo) * frac) return r.time();
        return Double.NaN;
    }

    static Weather.Snapshot byHour(List<Weather.Snapshot> run, double h) { return byTime(run, at(h)); }

    static Weather.Snapshot byTime(List<Weather.Snapshot> run, double t) {
        Weather.Snapshot best = run.get(0);
        for (Weather.Snapshot r : run) if (Math.abs(r.time() - t) < Math.abs(best.time() - t)) best = r;
        return best;
    }

    static String hhmm(double h) {
        if (Double.isNaN(h)) return "nie";
        int m = (int) Math.round(h * 60);
        return String.format("%02d:%02d%s", (m / 60) % 24, m % 60, m >= 24 * 60 ? " (+1)" : "");
    }
}
