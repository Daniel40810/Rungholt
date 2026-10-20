package com.dan.rungholt.tools;

import com.dan.rungholt.wiring.Beaufort;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.gl.Klang;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.scene.Glocken;
import com.dan.rungholt.scene.Land1362;
import com.dan.rungholt.scene.Mandraenke;
import com.dan.rungholt.scene.Seehundbank;
import com.dan.rungholt.scene.Stadt1362;
import com.dan.rungholt.scene.Terrain;
import com.dan.rungholt.wiring.Wiring;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Wav;
import com.dan.soundscape.demo.Analysis;
import com.dan.tide.Flood;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 14: die Hörproben als WAV mit Spektrogramm nach {@code docs/hoerproben}, ohne Grafikkarte. Die Szene läuft
 * wie im Bildlauf (Uhr, Wetter, Tide, Tiere, Mandränke, Glocken) in Schritten von 20 ms Echtzeit, der Klang hört an
 * der Kamera zu ({@link Klang} im Modus offline). Ohne automatische Lautstärke; die Datei wird zum Anhören auf
 * −1 dBFS Spitze angehoben (Angabe im Bericht), die beiden Glockenproben mit derselben Anhebung.
 * <p>
 * Aufruf: {@code java … com.dan.rungholt.tools.Hoerproben [Ordner] [nur]}, etwa {@code docs/hoerproben glocken}.
 */
public final class Hoerproben {
    static final double DT = 0.02;
    static final List<String> report = new ArrayList<>();
    static double lastGain = Double.NaN;

    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "docs/hoerproben");
        String only = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "";
        dir.mkdirs();
        SceneClock clock = new SceneClock(LocalDate.of(2026, 7, 21), 13.5);
        clock.lapse = 1;
        Wiring w = new Wiring(clock);
        w.wind.speed = Beaufort.mid(3);
        Renderer r = new Renderer(clock, w);
        r.cacheDir = Files.createTempDirectory("rungholt-tide").toFile();
        long t0 = System.nanoTime();
        r.buildFlood(2);
        System.out.printf(Locale.GERMAN, "Überflutung gebaut in %.1f s%n", (System.nanoTime() - t0) / 1e9);
        Klang k = r.klang;
        k.offline = true;
        k.s.autoGain = false;
        k.s.volume = 1;
        k.s.quality = Soundscape.Quality.HIGH;

        if (only.isEmpty() || only.contains("priel")) priel(r, w, clock, dir);
        if (only.isEmpty() || only.contains("bank")) bank(r, w, clock, dir);
        if (only.isEmpty() || only.contains("hafen")) hafen(r, w, clock, dir);
        if (only.isEmpty() || only.contains("glocken")) glocken(r, w, clock, dir);
        if (only.isEmpty() || only.contains("mandraenke")) mandraenke(r, w, clock, dir);   // zuletzt: verändert das Land 1362
        Files.writeString(new File(dir, "hoerproben.txt").toPath(), String.join(System.lineSeparator(), report) + System.lineSeparator(), StandardCharsets.UTF_8);
        System.out.println("FERTIG");
        System.exit(0);
    }

    // ------------------------------------------------------------------ die Proben

    /** Ebbe am Priel: auf trockenem Watt an der Kante eines Priels, der bei halber Ebbe läuft. */
    static void priel(Renderer r, Wiring w, SceneClock c, File dir) throws Exception {
        heute(r, w);
        w.wind.speed = Beaufort.mid(3);
        w.wind.fromDeg = 250;
        double lw = w.level.husum.next(w.level.hours(), false).hours(), hw = w.level.husum.next(lw - 13, true).hours();
        vorlauf(r, w, c, hw + 3.4);
        Flood f = r.flood();
        double best = 0, bx = 0, bz = 0, wx = 0, wz = 0;
        System.out.printf(Locale.GERMAN, "Priel: Hochwasser %.2f, jetzt %.2f h, Pegel %+.2f m, Strömung bis %.2f m/s%n", hw, w.level.hours(), r.level(), f.maxCurrent);
        for (double z = -3000; z <= 3000; z += 24)
            for (double x = -4500; x <= 3500; x += 24) {
                if (f.depthAt(x, z) > 0 || r.land().artAt(x, z) != Terrain.WATT) continue;
                double gd = 1e9;   // nah bei fressenden Vögeln
                for (var g : r.waders.groups()) if (g.mode == com.dan.flock.Waders.Mode.FRESSEN) gd = Math.min(gd, Math.hypot(g.cx - x, g.cz - z));
                if (gd > 900) continue;
                double dh = f.dryHoursAt(x, z);
                if (dh > 4) continue;
                for (int q = 0; q < 32; q++) {
                    double rad = q < 16 ? 30 : 60, ang = q * Math.PI / 8, px = x + Math.cos(ang) * rad, pz = z + Math.sin(ang) * rad;
                    if (f.depthAt(px, pz) < 0.5) continue;
                    double[] v = f.velocityAt(px, pz);
                    double sp = Math.hypot(v[0], v[1]) * (rad < 40 ? 1 : 0.85);
                    if (sp > best) { best = sp; bx = x; bz = z; wx = px; wz = pz; }
                }
            }
        stellen(r, bx, bz, 1.7, wx, wz);
        record(r, w, c, dir, "1_Ebbe_am_Priel", String.format(Locale.GERMAN,
                "Ebbe am Priel: 21.7.2026, halbe Ebbe, auf frisch trockenem Watt %.0f m vor einem Priel mit %.1f m/s, Bft 3 aus WSW", Math.hypot(wx - bx, wz - bz), best), 40);
    }

    /** Die Bank: 150 m vor der Seehundbank bei Niedrigwasser, Seehunde und Heuler. */
    static void bank(Renderer r, Wiring w, SceneClock c, File dir) throws Exception {
        heute(r, w);
        c.set(LocalDate.of(2026, 6, 20), 12);   // Wurfzeit: Heuler auf der Bank
        w.wind.speed = Beaufort.mid(2);
        w.wind.fromDeg = 250;
        double lw = w.level.husum.next(w.level.hours(), false).hours();
        vorlauf(r, w, c, lw - 0.5);
        r.settleSeals();
        for (int i = 0; i < 20; i++) { c.setEpochMillis(c.epochMillis() + 60_000); r.stepTideNow(); r.stepAnimalsScene(60); }
        Flood f = r.flood();
        double px = Seehundbank.X, pz = Seehundbank.Z;
        for (int q = 0; q < 36; q++) {
            double ang = q * Math.PI / 18, x = Seehundbank.X + Math.cos(ang) * 150, z = Seehundbank.Z + Math.sin(ang) * 150;
            if (f.depthAt(x, z) <= 0) { px = x; pz = z; break; }
        }
        stellen(r, px, pz, 1.7, Seehundbank.X, Seehundbank.Z);
        record(r, w, c, dir, "2_Die_Bank", "Die Bank: 20.6.2026, Niedrigwasser, 150 m vor der Seehundbank, Bft 2", 40);
    }

    /** Der Hafen 1362: am Sielhafen zur Mittagsstunde, die Glocke zum Angelus. */
    static void hafen(Renderer r, Wiring w, SceneClock c, File dir) throws Exception {
        if (r.land1362 == null) r.buildLand1362();
        w.era.target = w.era.blend = 1;
        r.selectEra();
        c.set(LocalDate.of(2026, 7, 21), 11 + 59 / 60.0 + 36 / 3600.0);
        w.wind.speed = Beaufort.mid(3);
        w.wind.fromDeg = 250;
        for (int i = 0; i < 12; i++) { c.setEpochMillis(c.epochMillis() - 0); r.stepTideNow(); r.stepTownScene(60); }
        Renderer.Town t = r.town;
        double[] hb = t.basin;
        double ax = hb[2], az = hb[3], qx = -az, qz = ax;
        if (qx < 0) { qx = -qx; qz = -qz; }
        double off = Land1362.BASIN_ACROSS + 3;
        double x = hb[0] + qx * off, z = hb[1] + qz * off;
        stellen(r, x, z, 1.7, hb[0], hb[1]);
        double[] tower = Stadt1362.tower(Land1362::estimate);
        record(r, w, c, dir, "3_Der_Hafen_1362", String.format(Locale.GERMAN,
                "Der Hafen 1362: an der Kaje des Sielhafens am Mittag, Wasser an den Pfählen, Schiffszimmerer und Säge, Leute beim Laden, um 12 Uhr fern die Glocke der Kirche (%.0f m)", Math.hypot(tower[0] - x, tower[2] - z)), 45);
    }

    /** Die Glocken im Sturm von Fuhlehörn, mit dem Wind (aus West) und gegen den Wind (aus Ost). */
    static void glocken(Renderer r, Wiring w, SceneClock c, File dir) throws Exception {
        heute(r, w);
        Glocken.Buehne b = r.glockenBuehne;
        b.zeit(Glocken.DAY, Glocken.START_HOUR);
        b.sturm(true);
        double hw = b.hochwasser(b.stunde());
        vorlauf(r, w, c, hw - 0.1);
        for (String gegen : new String[]{"mit", "gegen"}) {
            b.sturm(true);
            if (gegen.equals("gegen")) w.hand(com.dan.weather.Weather.Var.DIRECTION, 95);
            for (int i = 0; i < 300; i++) w.feedWeather(DT);   // Wind und Richtung stellen sich ein
            r.glockenView(Glocken.Blick.FUHLEHOERN);
            r.glocken.force();
            r.glocken.left = 600;
            String name = gegen.equals("mit") ? "5_Glocken_Fuhlehoern_mit_dem_Wind" : "6_Glocken_Fuhlehoern_gegen_den_Wind";
            record(r, w, c, dir, name, String.format(Locale.GERMAN, "Die Glocken im Sturm von Fuhlehörn (%.1f km östlich der Kirche), %s: Wind %.0f m/s aus %03.0f°, 30.11.2026 zum Hochwasser",
                    Math.hypot(r.camera.eye[0], r.camera.eye[2]) / 1000, gegen.equals("mit") ? "mit dem Wind" : "gegen den Wind (Ostwind, nur für den Vergleich)", w.wind.speed(), w.wind.fromDeg()), 45);
        }
        r.glocken.ringing = false;
        r.glocken.forced = false;
        b.sturm(false);
    }

    /**
     * Die Nacht der Mandränke in zwei Einstellungen: zuerst auf der Kirchwarft unter dem Turm, während Sturm und Überlauf
     * (Orkan, Sturmläuten, Vieh), dann auf dem Seedeich 150 m neben der Bruchstelle die letzten Sekunden bis zum Bruch.
     */
    static void mandraenke(Renderer r, Wiring w, SceneClock c, File dir) throws Exception {
        if (r.land1362 == null) r.buildLand1362();
        r.manualMandraenke = true;
        if (!r.startMandraenke(Mandraenke.Stelle.KIRCHE)) { report.add("Mandränke: startet nicht"); return; }
        r.selectEra();
        Mandraenke m = r.mandraenke;
        boolean warft = false;
        for (int k = 0; k < 4000 && m.stage.ordinal() < Mandraenke.Stage.BRUCH.ordinal(); k++) {
            if (m.stage == Mandraenke.Stage.UEBERLAUF && m.overtopH > Mandraenke.OVERTOP_HOURS - 20 / 3600.0) break;
            if (!warft && m.stage == Mandraenke.Stage.UEBERLAUF) {
                warft = true;
                stellen(r, 46.5, 23.3, 1.7, 0, 0);
                record(r, w, c, dir, "4a_Mandraenke_Sturmlaeuten_auf_der_Kirchwarft", String.format(Locale.GERMAN,
                        "Die Nacht der Mandränke, 23./24.1.1362 (julianisch 15./16.): auf der Kirchwarft unter dem Turm, die See läuft über den Deich; Orkan %.0f m/s, Sturmläuten, das Vieh", w.wind.speed()), 30);
            }
            int dt = m.stage != Mandraenke.Stage.UEBERLAUF ? 300 : m.overtopH < Mandraenke.OVERTOP_HOURS - 0.05 ? 30 : 2;
            c.setEpochMillis(c.epochMillis() + dt * 1000L);
            if (w.regie) w.feedWeather(dt);
            r.stepTideNow();
            r.stepMandraenke(dt, true);
            r.stepTownScene(dt);
        }
        double x = m.stelle.x + 150, z = Stadt1362.dikeZ(x);
        stellen(r, x, z, 1.7, m.stelle.x, Stadt1362.dikeZ(m.stelle.x));
        r.camera.place(x, Land1362.CREST + 1.7, z, r.camera.fyaw, 0);
        record(r, w, c, dir, "4b_Mandraenke_der_Bruch", String.format(Locale.GERMAN,
                "Die Nacht der Mandränke: auf dem Seedeich 150 m neben der Bruchstelle an der Kirche; Brecher über die Krone, der Deich bricht (Wind %.0f m/s)", w.wind.speed()), 45);
    }

    // ------------------------------------------------------------------ Hilfen

    static void heute(Renderer r, Wiring w) {
        if (r.mandraenke != null) r.stopMandraenke(true);
        w.era.target = w.era.blend = 0;
        r.selectEra();
        w.regie = false;
        w.resumeRegie();
        w.surgeScript = Double.NaN;
        w.level.tide.surge = 0;
        w.rainRate = 0;
    }

    /** Tide, Tiere und Glocken in 5-min-Schritten bis zur Stunde (seit 1970); vorher 6 h, damit Pfützen stehen. */
    static void vorlauf(Renderer r, Wiring w, SceneClock c, double untilHours) {
        double target = untilHours * 3_600_000;
        c.setEpochMillis(Math.min(c.epochMillis(), target - 6 * 3_600_000));
        r.settleSeals();
        for (double t = c.epochMillis(); t < target; ) {
            double step = Math.min(300_000, target - t);
            t += step;
            c.setEpochMillis(t);
            if (w.regie) w.feedWeather(1 / 60.0);
            w.feedTide(step / 3_600_000.0);
            r.stepTideNow();
            r.stepAnimalsScene(step / 1000);
            r.glocken.step(step / 1000, r.sturmnacht());
        }
    }

    /** Das Ohr in 1,7 m (oder h) über Grund oder Wasser, mit Blick auf (lx, lz). */
    static void stellen(Renderer r, double x, double z, double h, double lx, double lz) {
        double g = r.groundHeight(x, z), wl = r.wiring.level.depthAt(x, z) > 0 ? r.wiring.level.surfaceAt(x, z) : g;
        double yaw = Math.toDegrees(Math.atan2(lx - x, -(lz - z)));
        r.camera.place(x, Math.max(g, wl) + h, z, (yaw + 360) % 360, 0);
    }

    /** Eine Probe aufnehmen: 6 s Vorlauf (verworfen), dann sec Sekunden; WAV, Spektrogramm, Zeile im Bericht. */
    static void record(Renderer r, Wiring w, SceneClock c, File dir, String name, String what, double sec) throws Exception {
        Klang k = r.klang;
        Soundscape s = k.s;
        int sr = s.sampleRate();
        int b0 = k.birdCalls, s0 = k.sealCalls, w0 = k.wattBells, st0 = k.stormBells, a0 = k.angelusBells, be0 = k.beastCalls, br0 = k.breakers;
        double[] lv = new double[Soundscape.PARTS.length];
        int nLv = 0;
        double crackle = 0, sea = 0;
        String stages = "";
        float[] all = new float[(int) Math.round(sec * sr) * 2 + 4096];
        int pos = 0;
        long t0 = System.nanoTime();
        int frames = (int) Math.round((sec + 6) / DT);
        for (int i = 0; i < frames; i++) {
            step(r, w, c, DT, i);
            float[] o = s.render(DT);
            if (i * DT >= 6) {
                int n = Math.min(o.length, all.length - pos);
                System.arraycopy(o, 0, all, pos, n);
                pos += n;
                double[] l = s.levels();
                for (int q = 0; q < lv.length; q++) lv[q] += Math.pow(10, l[q] / 10);
                nLv++;
                crackle += k.crackle;
                sea += k.seaFactor;
                if (r.mandraenke != null && !stages.endsWith(r.mandraenke.stage.name())) stages += (stages.isEmpty() ? "" : " → ") + r.mandraenke.stage.name();
            }
        }
        {
            double nd = 1e9; int ng = 0; String modes = "";
            for (var g : r.waders.groups()) { ng++; nd = Math.min(nd, Math.hypot(g.cx - r.camera.eye[0], g.cz - r.camera.eye[2])); modes += g.species.key().substring(0, 3) + ":" + g.mode.label + " "; }
            System.out.printf(Locale.GERMAN, "  [Ohr %.0f, %.0f · %d Gruppen, nächste %.0f m · %s · %s]%n", r.camera.eye[0], r.camera.eye[2], ng, nd, k.note, modes);
        }
        float[] out = java.util.Arrays.copyOf(all, pos);
        // zum Anhören angehoben (Spitze −1 dBFS, höchstens +40 dB); die beiden Glockenproben mit derselben Anhebung
        float peak = 1e-9f;
        for (float v : out) peak = Math.max(peak, Math.abs(v));
        double gainDb = name.startsWith("6_") && !Double.isNaN(lastGain) ? lastGain : Math.min(40, -1 - 20 * Math.log10(peak));
        if (name.startsWith("6_")) gainDb = Math.min(gainDb, -1 - 20 * Math.log10(peak));
        lastGain = gainDb;
        float gl = (float) Math.pow(10, gainDb / 20);
        float[] loud = out.clone();
        for (int q = 0; q < loud.length; q++) loud[q] *= gl;
        Wav.write(new File(dir, name + ".wav"), loud, sr);
        double rms = Analysis.rms(out, sr, 0, pos / 2.0 / sr);
        StringBuilder parts = new StringBuilder();
        for (int q = 0; q < lv.length; q++) {
            double db = 10 * Math.log10(lv[q] / Math.max(1, nLv) + 1e-30);
            if (db > 20) parts.append(String.format(Locale.GERMAN, "%s %.0f · ", Soundscape.PARTS[q], db));
        }
        String line = String.format(Locale.GERMAN, "%s (zum Anhören um %+.0f dB angehoben)%n  %s%n  Pegel gesamt %.1f dBFS (%.0f dB SPL) · %s%n  Rufe: Vögel %d, Seehunde %d, Vieh %d · Glocke Kirche %d, Sturmläuten %d, Glocken aus dem Watt %d · Brecher %d · Knistern %.0f %% · See %.0f %%%s · gerechnet in %.1f s",
                name + ".wav", gainDb, what, 20 * Math.log10(rms + 1e-12), 20 * Math.log10(rms + 1e-12) + Soundscape.REF_DB, parts.length() > 3 ? parts.substring(0, parts.length() - 3) : "—",
                k.birdCalls - b0, k.sealCalls - s0, k.beastCalls - be0, k.angelusBells - a0, k.stormBells - st0, k.wattBells - w0, k.breakers - br0,
                crackle / Math.max(1, nLv) * 100, sea / Math.max(1, nLv) * 100, stages.isEmpty() ? "" : " · " + stages, (System.nanoTime() - t0) / 1e9);
        System.out.println(line);
        report.add(line);
        spectrogram(new File(dir, name + ".png"), out, sr, what);
    }

    /** Ein Schritt wie im Bildlauf (ohne Grafik). */
    static void step(Renderer r, Wiring w, SceneClock c, double dt, int i) {
        c.setEpochMillis(c.epochMillis() + dt * 1000);
        if (w.regie) w.feedWeather(dt);
        if (i % 50 == 0) {
            w.feedTide(1 / 3600.0);
            r.stepTideNow();
            w.feed(r.ocean);
            r.ocean.step(1);
        }
        r.stepAnimalsScene(dt);
        if (r.mandraenke != null) r.stepMandraenke(dt, true);
        r.glocken.step(dt, r.sturmnacht());
        var cam = r.camera;
        cam.groundLevel = r.groundHeight(cam.eye[0], cam.eye[2]);
        cam.waterLevel = w.level.depthAt(cam.eye[0], cam.eye[2]) > 0 ? w.level.surfaceAt(cam.eye[0], cam.eye[2]) : cam.groundLevel;
        r.klang.feed(dt);
    }

    static void spectrogram(File png, float[] st, int sr, String what) throws Exception {
        int W = 1600, H = 520, top = 56, left = 70;
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 18, 24));
        g.fillRect(0, 0, W, H);
        double lvl = 20 * Math.log10(Analysis.rms(st, sr, 0, st.length / 2.0 / sr) + 1e-9);
        Analysis.spectrogram(g, st, sr, left, top, W - left - 20, H - top - 70, lvl - 30);   // Farbskala 70 dB unter dem Pegel bis 40 dB darüber
        g.setColor(new Color(225, 230, 235));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        g.drawString(what.length() > 175 ? what.substring(0, 172) + " …" : what, left, 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        double fMin = 30, fMax = Math.min(16_000, sr / 2.0);
        int ph = H - top - 70;
        for (double f : new double[]{50, 100, 200, 500, 1000, 2000, 5000, 10000}) {
            int y = top + (int) ((ph - 1) * (1 - Math.log(f / fMin) / Math.log(fMax / fMin)));
            g.drawString(f >= 1000 ? (int) (f / 1000) + " kHz" : (int) f + " Hz", 8, y + 4);
        }
        double dur = st.length / 2.0 / sr;
        for (int s = 0; s <= (int) dur; s += 5) {
            int x = left + (int) ((W - left - 20) * s / dur);
            g.drawString(s + " s", x - 8, H - 50);
        }
        // Pegel über der Zeit (dBFS je 0,25 s)
        g.setColor(new Color(255, 210, 90));
        int px = -1, py = 0;
        for (double t = 0; t < dur - 0.25; t += 0.25) {
            double db = 20 * Math.log10(Analysis.rms(st, sr, t, 0.25) + 1e-9);
            int x = left + (int) ((W - left - 20) * t / dur), y = H - 8 - (int) Math.max(0, Math.min(30, (db + 70) / 70 * 30));
            if (px >= 0) g.drawLine(px, py, x, y);
            px = x; py = y;
        }
        g.dispose();
        ImageIO.write(img, "png", png);
    }
}
