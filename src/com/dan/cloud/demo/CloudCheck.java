package com.dan.cloud.demo;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.cloud.Noise;
import com.dan.cloud.gl.CloudGl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10): Rauschen (reproduzierbar,
 * kachelbar), Bedeckung von unten gegen die eingestellten Achtel für alle Arten an anderen Orten als bei der Eichung,
 * Höhen und Amboss, Zug mit dem Wind, Schatten, Regen, Helligkeit und die Vollständigkeit der Uniforms. Endet mit
 * „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class CloudCheck {
    /** Prüfsummen der Rauschtexturen (CRC32 der Bytes); ändern sich nur, wenn {@link Noise} geändert wird. */
    static final long CRC_BASE = 0xE11F578FL, CRC_DETAIL = 0x0E705552L, CRC_WEATHER = 0xE05D4934L;
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        CloudCheck c = new CloudCheck();
        long t0 = System.nanoTime();
        c.run();
        for (String l : c.lines) System.out.println(l);
        System.out.printf(Locale.GERMAN, "(%.1f s)%n", (System.nanoTime() - t0) / 1e9);
        System.out.println(c.bad == 0 ? "Alles in Ordnung." : c.bad + " Verstöße.");
        System.exit(c.bad == 0 ? 0 : 1);
    }

    public List<String> run() {
        noise();
        coverage();
        shape();
        drift();
        shadowAndRain();
        lens();
        gpuContract();
        return lines;
    }

    public int failures() { return bad; }

    void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static long crc(byte[] b) {
        CRC32 c = new CRC32();
        c.update(b);
        return c.getValue();
    }

    // ------------------------------------------------------------------ Rauschen

    void noise() {
        lines.add("Rauschen");
        long t0 = System.nanoTime();
        byte[] b = Noise.base(), d = Noise.detail(), w = Noise.weather();
        double ms = (System.nanoTime() - t0) / 1e6;
        check(b.length == 128 * 128 * 128 * 4 && d.length == 32 * 32 * 32 * 4 && w.length == 512 * 512 * 4,
                f("Größen 128³, 32³, 512² RGBA (gebaut in %.0f ms)", ms));
        long cb = crc(b), cd = crc(d), cw = crc(w);
        boolean known = CRC_BASE != 0;
        check(!known || (cb == CRC_BASE && cd == CRC_DETAIL && cw == CRC_WEATHER),
                f("reproduzierbar: Prüfsummen %08X %08X %08X%s", cb, cd, cw, known ? "" : " (noch nicht hinterlegt)"));
        // kachelbar: Abtasten bei u und u + 1 gibt dasselbe; die Ränder gehen stetig ineinander über
        double worst = 0, jump = 0;
        double[] p = new double[4], q = new double[4];
        for (int i = 0; i < 200; i++) {
            double u = i * 0.0137, v = i * 0.0291, s = i * 0.0073;
            sample3(b, 128, u, v, s, p);
            sample3(b, 128, u + 1, v - 2, s + 3, q);
            for (int k = 0; k < 4; k++) worst = Math.max(worst, Math.abs(p[k] - q[k]));
            int x = i % 128, y = (i * 7) % 128;
            int o1 = ((y * 128 + x) * 128 + 127) * 4, o2 = ((y * 128 + x) * 128) * 4;
            jump = Math.max(jump, Math.abs((b[o1] & 255) - (b[o2] & 255)) / 255.0);
        }
        check(worst < 1e-9 && jump < 0.35, f("kachelbar: Abweichung bei u+1 %.1e, Sprung über den Rand höchstens %.2f", worst, jump));
        double[] hist = new double[4];
        for (int i = 0; i < w.length; i += 4) for (int k = 0; k < 4; k++) hist[k] += (w[i + k] & 255) / 255.0;
        for (int k = 0; k < 4; k++) hist[k] /= w.length / 4.0;
        check(hist[0] > 0.3 && hist[0] < 0.7 && hist[1] > 0.3 && hist[1] < 0.7, f("Wetterkarte im Mittel R %.2f, G %.2f, B %.2f, A %.2f", hist[0], hist[1], hist[2], hist[3]));
    }

    /** Trilinear wie die Grafikkarte (der Kern hält das package-intern; hier nachgebaut). */
    static void sample3(byte[] t, int n, double u, double v, double w, double[] out) {
        double x = u * n - 0.5, y = v * n - 0.5, z = w * n - 0.5;
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y), z0 = (int) Math.floor(z);
        double fx = x - x0, fy = y - y0, fz = z - z0;
        out[0] = out[1] = out[2] = out[3] = 0;
        for (int c = 0; c < 8; c++) {
            int dx = c & 1, dy = (c >> 1) & 1, dz = (c >> 2) & 1;
            double g = (dx == 1 ? fx : 1 - fx) * (dy == 1 ? fy : 1 - fy) * (dz == 1 ? fz : 1 - fz);
            int o = ((Math.floorMod(z0 + dz, n) * n + Math.floorMod(y0 + dy, n)) * n + Math.floorMod(x0 + dx, n)) * 4;
            for (int k = 0; k < 4; k++) out[k] += g * (t[o + k] & 255) / 255.0;
        }
    }

    // ------------------------------------------------------------------ Bedeckung

    /** Mittel über 256 Orte gleichmäßig über die Kachel der Wetterkarte, versetzt gegen die Orte der Eichung. */
    static double observed(Clouds c) {
        Clouds.Snapshot s = c.snapshot();
        double sum = 0, tile = s.tile();
        for (int i = 0; i < 16; i++)
            for (int j = 0; j < 16; j++) sum += Clouds.observedCoverage(s, (i + 0.75) * tile / 16, (j + 0.75) * tile / 16, 10);
        return sum / 256;
    }

    /** Szenen-y, das an (x, z) über der gekrümmten Erde die Höhe h ergibt. */
    static double yAt(double x, double z, double h) {
        double r = Clouds.EARTH_R + h;
        return Math.sqrt(r * r - x * x - z * z) - Clouds.EARTH_R;
    }

    void coverage() {
        lines.add("Bedeckung von unten (256 Orte, Richtungen bis 70° vom Zenit, optische Dicke ab 1)");
        for (CloudType t : CloudType.values()) {
            StringBuilder sb = new StringBuilder();
            double worst = 0, prev = -1;
            boolean mono = true;
            for (int o = 0; o <= 8; o += 2) {
                Clouds c = Clouds.of(t, o);
                double v = observed(c);
                worst = Math.max(worst, Math.abs(v - o / 8.0));
                if (v < prev - 0.02) mono = false;
                prev = v;
                sb.append(f(" %d/8→%.2f", o, v * 8));
            }
            check(worst < 0.08 && mono, f("%-14s%s (größte Abweichung %.2f Achtel)", t.label, sb, worst * 8));
        }
    }

    // ------------------------------------------------------------------ Form

    /** Seit Glacier Phase 3: Lentikularis als ortsfeste Linsen einer Leewelle. */
    void lens() {
        lines.add("Lentikularis");
        Clouds c = Clouds.fair();
        c.lens = 0.9;
        c.lensFromDeg = 270;
        Clouds.Snapshot s0 = c.snapshot();
        c.step(600);
        Clouds.Snapshot s1 = c.snapshot();
        int hit = 0, hitTop = 0, n = 0;
        boolean fixed = true;
        double maxTau = 0;
        for (int i = 0; i < 4000; i++) {
            double x = (i * 7919) % 40_000 - 20_000, z = (i * 104_729) % 40_000 - 20_000;
            double t0 = Clouds.lensTau(s0, x, z, 0), t2 = Clouds.lensTau(s0, x, z, 2);
            fixed &= Math.abs(t0 - Clouds.lensTau(s1, x, z, 0)) < 1e-12;
            if (t0 > 0.5) hit++;
            if (t2 > 0.5) hitTop++;
            maxTau = Math.max(maxTau, t0);
            n++;
        }
        // Wind aus West: die Kämme liegen quer zum Wind, also Nord-Süd; eine Linse reicht quer weiter als längs
        int base = 0, keepAlong = 0, keepAcross = 0;
        for (int i = 0; i < 6000; i++) {
            double x = (i * 7919) % 60_000 - 30_000, z = (i * 104_729) % 60_000 - 30_000;
            if (Clouds.lensTau(s0, x, z, 0) <= 0.5) continue;
            base++;
            if (Clouds.lensTau(s0, x + 1500, z, 0) > 0.5) keepAlong++;
            if (Clouds.lensTau(s0, x, z + 1500, 0) > 0.5) keepAcross++;
        }
        double along = keepAlong / (double) Math.max(1, base), across = keepAcross / (double) Math.max(1, base);
        c.lens = 0;
        boolean none = Clouds.lensTau(c.snapshot(), 0, 0, 0) == 0;
        check(hit > n * 0.03 && hit < n * 0.4 && hitTop < hit && fixed && none && maxTau > 1,
                f("Linsen auf %.0f %% der Fläche (obere Lage %.0f %%, Stapel), τ bis %.1f, ortsfest beim Ziehen der Wolken, aus bei 0", hit * 100.0 / n, hitTop * 100.0 / n, maxTau));
        check(across > 2 * along, f("Linsen quer zum Wind gestreckt: 1,5 km weiter noch Linse quer %.0f %%, längs %.0f %%", across * 100, along * 100));
    }

    void shape() {
        lines.add("Form");
        Clouds cu = Clouds.of(CloudType.CUMULUS, 3);
        Clouds.Snapshot s = cu.snapshot();
        double below = 0, inside = 0, above = 0;
        for (int i = 0; i < 4000; i++) {
            double x = (i * 7919) % 30_000, z = (i * 104_729) % 30_000;
            below = Math.max(below, Clouds.density(s, x, yAt(x, z, 850), z, true));
            inside = Math.max(inside, Clouds.density(s, x, yAt(x, z, 1300), z, true));
            above = Math.max(above, Clouds.density(s, x, yAt(x, z, 2600), z, true));
        }
        check(below == 0 && inside > 0 && above == 0, f("Cumulus: nichts unter 900 m und über 2 500 m, dicht dazwischen (größte Auslöschung %.3f/m)", inside));
        Clouds cb = Clouds.of(CloudType.CUMULONIMBUS, 3);
        Clouds.Snapshot sb = cb.snapshot();
        int high = 0, mid = 0, n = 6000;
        for (int i = 0; i < n; i++) {
            double x = (i * 7919) % 120_000, z = (i * 104_729) % 120_000;
            if (Clouds.density(sb, x, yAt(x, z, 10_300), z, false) > 0) high++;
            if (Clouds.density(sb, x, yAt(x, z, 5_000), z, false) > 0) mid++;
        }
        check(high > mid && mid > 0, f("Cumulonimbus: Amboss in 10,3 km breiter als die Türme in 5 km (%.1f %% gegen %.1f %% der Fläche)", 100.0 * high / n, 100.0 * mid / n));
        boolean order = CloudType.CUMULONIMBUS.top() > CloudType.NIMBOSTRATUS.top() && CloudType.STRATUS.base < CloudType.CUMULUS.base;
        check(order, f("Höhen: Stratus ab %.0f m, Cumulus ab %.0f m, Cumulonimbus bis %.0f m", CloudType.STRATUS.base, CloudType.CUMULUS.base, CloudType.CUMULONIMBUS.top()));
        Clouds e = Clouds.of(CloudType.STRATOCUMULUS, 0);
        Clouds.Snapshot se = e.snapshot();
        double any = 0;
        for (int i = 0; i < 2000; i++) any = Math.max(any, Clouds.density(se, i * 137.0, yAt(i * 137.0, i * 71.0, 1000), i * 71.0, true));
        check(any == 0, "0/8: keine Wolke");
    }

    // ------------------------------------------------------------------ Zug

    void drift() {
        lines.add("Zug mit dem Wind");
        Clouds c = Clouds.of(CloudType.CUMULUS, 4);
        c.windSpeed = 12;
        c.windFromDeg = 270;   // aus West: die Wolken ziehen nach Osten (+x)
        c.evolve = 0;
        c.setTime(0);
        Clouds.Snapshot a = c.snapshot();
        c.setTime(100);
        Clouds.Snapshot b = c.snapshot();
        double worst = 0;
        for (int i = 0; i < 400; i++) {
            double x = i * 53.0, z = i * 31.0;
            worst = Math.max(worst, Math.abs(Clouds.coverage(a, x, z) - Clouds.coverage(b, x + 1200, z)));
        }
        check(worst < 1e-12, f("Wind 12 m/s aus West: nach 100 s steht dieselbe Wolke 1 200 m weiter östlich (± %.1e)", worst));
        Clouds d = Clouds.of(CloudType.CUMULUS, 4);
        d.windSpeed = 10;
        d.windFromDeg = 0;
        d.step(10);
        Clouds.Snapshot sd = d.snapshot();
        Clouds e = Clouds.of(CloudType.CUMULUS, 4);
        e.windSpeed = 10;
        e.windFromDeg = 0;
        e.setTime(10);
        check(Math.abs(sd.offX() - e.snapshot().offX()) < 1e-9 && Math.abs(sd.offZ() - e.snapshot().offZ()) < 1e-9,
                f("step und setTime stimmen überein (Versatz %.1f / %.1f m)", sd.offX(), sd.offZ()));
    }

    // ------------------------------------------------------------------ Schatten, Regen, Helligkeit

    void shadowAndRain() {
        lines.add("Schatten, Regen, Helligkeit");
        double[] sun = {0, 0.6, -0.8};
        double ns = Clouds.shadow(Clouds.of(CloudType.NIMBOSTRATUS, 8).snapshot(), 0, 0, sun);
        double clear = Clouds.shadow(Clouds.of(CloudType.CUMULUS, 0).snapshot(), 0, 0, sun);
        Clouds cu = Clouds.of(CloudType.CUMULUS, 4);
        Clouds.Snapshot s = cu.snapshot();
        int lit = 0, n = 400;
        for (int i = 0; i < n; i++) if (Clouds.shadow(s, i * 211.0, i * 97.0, sun) > 0.5) lit++;
        check(ns < 0.01 && clear == 1, f("Schatten: 8/8 Nimbostratus %.3f, wolkenlos %.2f", ns, clear));
        check(lit > n * 0.2 && lit < n * 0.8, f("Cumulus 4/8: %.0f %% des Meeres in der Sonne", 100.0 * lit / n));
        Clouds hi = Clouds.of(CloudType.CUMULUS, 0);
        hi.highCover = 1;
        double h = Clouds.shadow(hi.snapshot(), 0, 0, sun);
        check(h > 0.3 && h < 0.95, f("Hohe Schicht ganz: %.2f des Sonnenlichts kommt durch", h));
        Clouds dry = Clouds.of(CloudType.CUMULONIMBUS, 6);
        Clouds wet = Clouds.storm();
        double maxDry = 0, maxWet = 0;
        for (int i = 0; i < 2000; i++) {
            maxDry = Math.max(maxDry, Clouds.rain(dry.snapshot(), i * 397.0, i * 211.0));
            maxWet = Math.max(maxWet, Clouds.rain(wet.snapshot(), i * 397.0, i * 211.0));
        }
        check(maxDry == 0 && maxWet > 0.3, f("Regen: ohne Niederschlag 0, Gewitter bis %.2f", maxWet));
        double prev = 2;
        boolean mono = true;
        for (int o = 0; o <= 8; o++) {
            double b = Clouds.of(CloudType.NIMBOSTRATUS, o).brightnessFactor();
            if (b > prev + 1e-12) mono = false;
            prev = b;
        }
        check(mono && prev < 0.3, f("Helligkeit fürs Auge fällt mit der Bedeckung (8/8 Nimbostratus %.2f)", prev));
    }

    // ------------------------------------------------------------------ Vertrag mit der Grafikkarte

    /** Jedes Uniform, das ein Shader deklariert, kommt in der Liste von CloudGl.uniforms vor (Sampler und uTarget ausgenommen). */
    void gpuContract() {
        lines.add("Grafikkarte");
        Clouds c = Clouds.storm();
        CloudGl.View v = new CloudGl.View();
        v.viewProj = new float[16];
        v.invViewProj = new float[16];
        Map<String, Map<String, CloudGl.U>> u = new CloudGl().uniforms(c.snapshot(), new CloudGl.Light(), v);
        String[][] progs = {{"march", "clouds.frag", "cloud_common.glsl"}, {"pano", "clouds.frag", "cloud_common.glsl"}, {"shadow", "shadow.frag", "cloud_common.glsl"}};
        Pattern decl = Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)");
        List<String> missing = new ArrayList<>();
        for (String[] p : progs) {
            Set<String> need = new HashSet<>();
            for (int i = 1; i < p.length; i++) {
                Matcher m = decl.matcher(read(p[i]));
                while (m.find()) if (!m.group(1).startsWith("sampler") && !m.group(2).equals("uTarget")) need.add(m.group(2));
            }
            for (String n : need) if (!u.get(p[0]).containsKey(n)) missing.add(p[0] + "." + n);
        }
        check(missing.isEmpty(), "Alle Uniforms der Shader werden gesetzt" + (missing.isEmpty() ? "" : ": fehlen " + missing));
        boolean q = true;
        for (Clouds.Quality k : Clouds.Quality.values()) if (k.divisor < 1) q = false;
        check(q && Clouds.Quality.MEDIUM.divisor == 4 && Clouds.Quality.HIGH.divisor == 2,
                f("Stufen: Mittel 1/%d bis %d Schritte, Hoch 1/%d bis %d Schritte", Clouds.Quality.MEDIUM.divisor, Clouds.Quality.MEDIUM.maxSteps,
                        Clouds.Quality.HIGH.divisor, Clouds.Quality.HIGH.maxSteps));
    }

    static String read(String file) {
        try (InputStream in = CloudGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IOException("fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
