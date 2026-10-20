package com.dan.lightning.demo;

import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“.
 * Geprüft werden Presets und Wiederholbarkeit, die Form der Kanäle (zusammenhängend, Windung, Äste ohne Bodenberührung),
 * die Statistik der Schläge gegen die Messungen von Berger und CIGRE, der Fang durch einen Turm über die Schlagweite,
 * Rate und Zellen, die Sichtbarkeit in der Wolke, Licht und Donner mit Laufzeit.
 */
public final class LightningCheck {
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        for (String l : new LightningCheck().run()) System.out.println(l);
    }

    private void check(boolean ok, String what) {
        lines.add((ok ? "  ok    " : "  FEHLER") + " " + what);
        if (!ok) bad++;
    }

    private static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static double median(double[] v) {
        double[] c = v.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }

    public List<String> run() {
        lines.clear();
        bad = 0;
        // ------------------------------------------------ Presets, Wiederholbarkeit
        check(Lightning.beaufort(6).level() == Lightning.Level.OFF && Lightning.gale().level() == Lightning.Level.DISTANT
                        && Lightning.storm().level() == Lightning.Level.STRONG && Lightning.beaufort(12).level() == Lightning.Level.SEVERE,
                "Presets: bis Bft 7 kein Gewitter, 8 Wetterleuchten, 10 kräftig, 12 schwer");
        Lightning a = new Lightning(), b = new Lightning();
        a.seed = b.seed = 42;
        Bolt ba = a.strike(300, -200), bb = b.strike(300, -200);
        check(ba.n == bb.n && Arrays.equals(ba.bx, bb.bx) && Arrays.equals(ba.strokeKA, bb.strokeKA), "gleicher seed, gleicher Blitz (" + ba.n + " Stücke)");
        Lightning c2 = new Lightning();
        c2.seed = 43;
        check(c2.strike(300, -200).n != ba.n || !Arrays.equals(c2.strike(300, -200).bx, ba.bx), "anderer seed, anderer Blitz");

        // ------------------------------------------------ Form des Erdblitzes
        Lightning L = new Lightning();
        L.seed = 7;
        L.cloudBase = 900;
        L.groundLevel = 0.5;
        Bolt g = L.strike(0, 0);
        boolean chain = true;
        int prev = -1, mains = 0;
        for (int i = 0; i < g.n; i++) {
            if (g.order[i] != 0) continue;
            if (prev >= 0) chain &= g.ax[i] == g.bx[prev] && g.ay[i] == g.by[prev] && g.az[i] == g.bz[prev];
            prev = i;
            mains++;
        }
        int first = -1;
        for (int i = 0; i < g.n; i++) if (g.order[i] == 0) { first = i; break; }
        check(chain && Math.abs(g.by[prev] - 0.5) < 0.01 && g.ay[first] > L.cloudBase,
                f("Hauptkanal zusammenhängend aus %.0f m (über der Untergrenze %.0f m) bis aufs Wasser (%.2f m), %d Stücke, %.1f km", g.ay[first], L.cloudBase, g.by[prev], mains, g.mainLength / 1000));
        // Windung: Winkel zwischen aufeinanderfolgenden Stücken des Hauptkanals, über viele Blitze
        double angSum = 0;
        int angN = 0;
        boolean branchesFree = true;
        int branchCount = 0;
        for (int k = 0; k < 40; k++) {
            Bolt q = L.strike(k * 50, 0);
            double rs = Lightning.strikingDistance(q.strokeKA[0]);
            int pv = -1;
            for (int i = 0; i < q.n; i++) {
                if (q.order[i] != 0) {
                    branchCount++;
                    branchesFree &= Math.min(q.ay[i], q.by[i]) > L.groundLevel + rs;
                    continue;
                }
                if (pv >= 0 && i < q.n - 2) {
                    double[] u = dir(q, pv), v = dir(q, i);
                    angSum += Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, u[0] * v[0] + u[1] * v[1] + u[2] * v[2]))));
                    angN++;
                }
                pv = i;
            }
        }
        double meanAng = angSum / angN;
        check(meanAng > 11 && meanAng < 22, f("Windung des Kanals im Mittel %.1f° je Stufe (gemessen rund 16°)", meanAng));
        check(branchesFree && branchCount > 40, f("%d Äste in 40 Blitzen, keiner erreicht das Wasser (alle über der Schlagweite)", branchCount));
        L.clear();

        // ------------------------------------------------ Schläge
        int N = 3000;
        double[] strokes = new double[N], first30 = new double[N];
        List<Double> later = new ArrayList<>(), gaps = new ArrayList<>();
        int withCC = 0, multi = 0;
        Lightning S = new Lightning();
        S.seed = 11;
        S.quality = Lightning.Quality.LOW;
        for (int k = 0; k < N; k++) {
            Bolt q = S.strike(0, 0);
            S.clear();
            strokes[k] = q.strokeTime.length;
            first30[k] = q.strokeKA[0];
            if (q.strokeTime.length > 1) multi++;
            for (int j = 1; j < q.strokeTime.length; j++) {
                later.add(q.strokeKA[j]);
                gaps.add(q.strokeTime[j] - q.strokeTime[j - 1]);
            }
            for (double cc : q.continuing) if (cc > 0) { withCC++; break; }
        }
        double meanStrokes = Arrays.stream(strokes).average().orElse(0);
        double medFirst = median(first30), medLater = median(later.stream().mapToDouble(Double::doubleValue).toArray());
        double medGap = median(gaps.stream().mapToDouble(Double::doubleValue).toArray());
        check(meanStrokes > 3.0 && meanStrokes < 4.3 && multi > 0.75 * N, f("im Mittel %.2f Schläge je Erdblitz, %.0f %% mit mehreren (gemessen 3–4, rund 80 %%)", meanStrokes, 100.0 * multi / N));
        check(Math.abs(medFirst - 30) < 3 && Math.abs(medLater - 12) < 1.5, f("Median des Stroms: erster Schlag %.1f kA, folgende %.1f kA (Berger: 30 und 12)", medFirst, medLater));
        check(medGap > 0.035 && medGap < 0.06, f("Median der Pause zwischen Schlägen %.0f ms (gemessen 33–60 ms)", medGap * 1000));
        check(withCC > 0.25 * N && withCC < 0.75 * N, f("%.0f %% der Blitze mit Nachstrom (gemessen 30–50 %%)", 100.0 * withCC / N));
        check(Math.abs(Lightning.strikingDistance(30) - 91.4) < 1, f("Schlagweite bei 30 kA %.1f m (10 · I^0,65)", Lightning.strikingDistance(30)));

        // ------------------------------------------------ Fang durch einen Turm
        Lightning T = new Lightning();
        T.seed = 5;
        T.quality = Lightning.Quality.LOW;
        T.attractors.add(new Lightning.Attractor(0, 30.7, 0, "Turm"));
        double[] radii = {20, 60, 120, 300};
        double[] frac = new double[radii.length];
        for (int k = 0; k < radii.length; k++) {
            int hit = 0, n = 300;
            for (int j = 0; j < n; j++) {
                double ang = j * 2.399;
                Bolt q = T.strike(Math.sin(ang) * radii[k], Math.cos(ang) * radii[k]);
                T.clear();
                if (q.attractor == 0) hit++;
            }
            frac[k] = hit / (double) n;
        }
        check(frac[0] > 0.75 && frac[1] > 0.4 && frac[3] < 0.05 && frac[0] > frac[1] && frac[1] > frac[2],
                f("Turm von 30,7 m fängt Blitze, die in 20/60/120/300 m herunterkommen: %.0f/%.0f/%.0f/%.0f %% (Anziehungsradius nach Eriksson rund 80 m)", frac[0] * 100, frac[1] * 100, frac[2] * 100, frac[3] * 100));
        Bolt tb = T.strikeAttractor(0);
        check(tb.attractor == 0 && Math.abs(tb.hitY - 30.7) < 1e-6 && Math.abs(tb.by[lastMain(tb)] - 30.7) < 1e-3, "„Blitz in den Turm“ endet an der Spitze des Blitzableiters");
        T.clear();

        // ------------------------------------------------ Rate, Zellen, Arten
        Lightning R = Lightning.storm();
        R.seed = 3;
        R.quality = Lightning.Quality.LOW;
        R.listenerX = 0;
        R.listenerZ = 0;
        R.cells = (x, z) -> x > 0 ? 1 : 0;
        int[] kinds = new int[4];
        boolean allEast = true;
        R.onStroke = null;
        int count = 0;
        double dt = 0.1;
        List<Bolt> seen = new ArrayList<>();
        for (int i = 0; i < 18000; i++) {
            R.step(dt);
            for (Bolt q : R.bolts()) if (!seen.contains(q)) {
                seen.add(q);
                kinds[q.kind.ordinal()]++;
                count++;
                if (q.kind != Bolt.Kind.SHEET) allEast &= q.hitX > -2000;
            }
            if (seen.size() > 50) seen.subList(0, 25).clear();
        }
        double perMin = count / 30.0;
        check(Math.abs(perMin - 8) < 1.2, f("Stufe „kräftig“: %.1f Blitze je Minute über 30 min (eingestellt 8)", perMin));
        double cgf = kinds[0] / (double) (count - kinds[3]);
        check(Math.abs(cgf - 0.28) < 0.06, f("Anteil der Erdblitze %.0f %% (eingestellt 28 %%; über See gemessen 20–30 %%)", cgf * 100));
        check(allEast, "Blitze nur dort, wo die Zellen sie zulassen (Osthälfte)");
        Lightning D = Lightning.gale();
        D.seed = 9;
        int far = 0, total = 0;
        for (int i = 0; i < 3000; i++) {
            D.step(0.1);
            for (Bolt q : D.bolts()) if (q.t0 > D.time() - 0.1 - 1e-9) {
                total++;
                if (q.kind == Bolt.Kind.SHEET && Math.hypot(q.hitX, q.hitZ) > 28_000) far++;
            }
        }
        check(total > 10 && far == total && D.pendingThunder().isEmpty() && D.lastThunder() == null,
                f("Wetterleuchten (Bft 8): %d Entladungen, alle weiter als 28 km, ohne Donner", total));

        // ------------------------------------------------ Sichtbarkeit
        Lightning V = new Lightning();
        V.seed = 21;
        V.cloudBase = 1000;
        Bolt vb = V.strike(0, 0);
        double visBelow = 0, visAbove = 0;
        int nb = 0, na = 0;
        for (int i = 0; i < vb.n; i++) {
            double my = (vb.ay[i] + vb.by[i]) / 2;
            if (my < 900) { visBelow += vb.visible[i]; nb++; }
            if (my > 1400) { visAbove += vb.visible[i]; na++; }
        }
        check(nb > 0 && na > 0 && visBelow / nb > 0.99 && visAbove / na < 0.1, f("unter der Wolke frei sichtbar (%.2f), 400 m darüber verborgen (%.3f)", visBelow / nb, visAbove / na));
        Bolt sp = V.discharge(Bolt.Kind.SPIDER, 2000, 0);
        double minY = 1e9, maxY = -1e9, minX = 1e9, maxX = -1e9;
        for (int i = 0; i < sp.n; i++) {
            minY = Math.min(minY, sp.ay[i]);
            maxY = Math.max(maxY, sp.ay[i]);
            minX = Math.min(minX, Math.min(sp.ax[i], sp.az[i]));
            maxX = Math.max(maxX, Math.max(sp.ax[i], sp.az[i]));
        }
        check(minY > 600 && maxY < 1600 && maxX - minX > 2500, f("Spinnenblitz waagerecht um die Untergrenze (%.0f–%.0f m), %.1f km weit", minY, maxY, (maxX - minX) / 1000));
        V.clear();

        // ------------------------------------------------ Ablauf und Licht
        Lightning P = new Lightning();
        P.seed = 31;
        P.cloudBase = 1000;
        Bolt pb = P.strike(0, 0);
        check(pb.leaderTime > 0.008 && pb.leaderTime < 0.06 && Math.abs(pb.leaderFront(pb.leaderTime) - pb.leaderPath) < 1e-6,
                f("Leitblitz: %.1f km Weg in %.0f ms (200 km/s), erreicht das Wasser zum ersten Schlag", pb.leaderPath / 1000, pb.leaderTime * 1000));
        double before = pb.mainLevel(pb.leaderTime * 0.5), at = pb.mainLevel(pb.strokeTime[0] + 1e-4), after = pb.mainLevel(pb.duration + 0.01);
        check(before == 0 && at > 0.3 && after < 1e-3 && pb.segmentLevel(0, pb.leaderTime * 0.5) > 0 && pb.segmentLevel(0, pb.leaderTime * 0.5) < 0.01,
                f("Helligkeit: vor dem Schlag nur der Leitblitz (%.4f), beim Schlag %.2f, danach aus", pb.segmentLevel(0, pb.leaderTime * 0.5), at));
        // Licht: Punkte tragen alles Licht
        P.setTime(pb.t0 + pb.strokeTime[0] + 0.004);
        Lightning.Snapshot s = P.snapshot();
        List<Lightning.Light> lights = s.lights(4, 8);
        double sum = lights.stream().mapToDouble(Lightning.Light::candela).sum();
        check(Math.abs(sum / s.totalCandela(false) - 1) < 1e-9 && lights.size() <= 4, f("%d Lichtpunkte tragen die ganze Lichtstärke (%.2e cd)", lights.size(), sum));
        double e5 = s.illuminanceAt(5000, 2, 0, 0), ep = 0;
        for (Lightning.Light l : lights) {
            double dx = l.x() - 5000, dy = l.y() - 2, dz = l.z();
            ep += l.candela() * (l.visible() + Lightning.escape(l.y(), P.cloudBase) * (1 - l.visible())) / (dx * dx + dy * dy + dz * dz + 1);
        }
        check(Math.abs(ep / e5 - 1) < 0.1, f("in 5 km: %.2f lx aus dem Kanal, %.2f lx aus den Lichtpunkten", e5, ep));
        double e1 = e5 / Math.pow(pb.strokeKA[0] / 30, 1.4);
        check(e1 > 0.2 && e1 < 3, f("Beleuchtungsstärke in 5 km bei einem Schlag von 30 kA %.2f lx (heller als Vollmond 0,2 lx, dunkler als Dämmerung 10 lx)", e1));
        P.clear();

        // ------------------------------------------------ Melden: Schläge und Donner
        Lightning M = new Lightning();
        M.seed = 77;
        M.listenerX = 3000;
        M.listenerY = 2;
        int[] strokesSeen = {0};
        List<Lightning.Thunder> heard = new ArrayList<>();
        M.onStroke = (bolt, k, x, y, z, cd) -> strokesSeen[0]++;
        M.onThunder = heard::add;
        Bolt mb = M.strike(0, 0);
        Lightning.Thunder pend = M.pendingThunder().get(0);
        for (int i = 0; i < 2000; i++) M.step(1 / 100.0);
        double c = M.speedOfSound();
        check(strokesSeen[0] == mb.strokeTime.length, f("jeder Schlag wird genau einmal gemeldet (%d)", strokesSeen[0]));
        check(heard.size() == 1 && Math.abs(heard.get(0).time() - (mb.t0 + mb.strokeTime[0] + pend.distance() / c)) < 1e-9 && pend.distance() > 2600 && pend.distance() < 3600,
                f("Donner in %.0f m Abstand nach %.1f s (Schall %.1f m/s bei 15 °C), rollt %.1f s", pend.distance(), pend.time() - mb.t0, c, pend.duration()));
        check(pend.levelDb() > 85 && pend.levelDb() < 115 && !pend.crack(), f("Pegel in 3 km %.0f dB", pend.levelDb()));
        Lightning Q = new Lightning();
        Q.seed = 3;
        Q.listenerX = 30;
        Q.attractors.add(new Lightning.Attractor(0, 30.7, 0, "Turm"));
        Bolt qb = Q.strikeAttractor(0);
        Lightning.Thunder near = Q.pendingThunder().get(0);
        check(near.crack() && near.levelDb() > 115 && near.time() - qb.t0 - qb.strokeTime[0] < 0.2, f("Einschlag in den Turm vom Fuß aus: Knall nach %.2f s, %.0f dB", near.time() - qb.t0 - qb.strokeTime[0], near.levelDb()));
        Lightning F = new Lightning();
        F.seed = 4;
        F.strike(40_000, 0);
        check(F.pendingThunder().isEmpty(), "in 40 km kein Donner mehr zu hören");

        // ------------------------------------------------ Stufen
        int[] segs = new int[4];
        for (Lightning.Quality qq : Lightning.Quality.values()) {
            Lightning Z = new Lightning();
            Z.seed = 8;
            Z.quality = qq;
            int sumN = 0;
            for (int k = 0; k < 10; k++) { sumN += Z.strike(k * 100, 0).n; Z.clear(); }
            segs[qq.ordinal()] = sumN / 10;
        }
        check(segs[0] < segs[1] && segs[1] < segs[2] && segs[2] < segs[3] && segs[3] <= Lightning.Quality.ULTRA.maxSegments,
                f("Stufen: im Mittel %d/%d/%d/%d Stücke je Erdblitz", segs[0], segs[1], segs[2], segs[3]));

        lines.add(bad == 0 ? "Alles in Ordnung." : bad + " Fehler.");
        return lines;
    }

    static double[] dir(Bolt b, int i) {
        double dx = b.bx[i] - b.ax[i], dy = b.by[i] - b.ay[i], dz = b.bz[i] - b.az[i], l = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return new double[]{dx / l, dy / l, dz / l};
    }

    static int lastMain(Bolt b) {
        int last = -1;
        for (int i = 0; i < b.n; i++) if (b.order[i] == 0) last = i;
        return last;
    }

    public int failures() { return bad; }
}
