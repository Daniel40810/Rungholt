package com.dan.sky.demo;

import com.dan.sky.Atmosphere;
import com.dan.sky.Celestial;
import com.dan.sky.MoonDisc;
import com.dan.sky.Planets;
import com.dan.sky.Scattering;
import com.dan.sky.Sky;
import com.dan.sky.StarCatalog;
import com.dan.sky.astro.FAstroTime;
import com.dan.sky.astro.FGeoLocation;
import com.dan.sky.astro.FMoonPhase;
import com.dan.sky.astro.FMoonPosition;
import com.dan.sky.astro.FRiseSet;
import com.dan.sky.astro.FSun;
import com.dan.sky.gl.SkyGl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prüfung des Packages ohne Fenster und ohne Grafikkarte (Paketvertrag Punkt 10): Rechenkern aus FMoon gegen Meeus,
 * Präzession und Planeten, Brechung, Streuung gegen die genaue Rechnung und gegen den CIE-Normhimmel, Dämmerung,
 * Nachthimmel, Mond- und Sonnenlicht, Grenzgröße, Sternkatalog, Figuren, Milchstraße und die Vollständigkeit der
 * Uniforms. Endet mit „Alles in Ordnung.“ oder mit den Verstößen (Rückgabewert 1).
 */
public final class SkyCheck {
    static final double LAT = 53.855, LON = 8.079;
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");
    private final List<String> lines = new ArrayList<>();
    private int bad;

    public static void main(String[] args) {
        SkyCheck c = new SkyCheck();
        c.run();
        for (String l : c.lines) System.out.println(l);
        System.out.println(c.bad == 0 ? "Alles in Ordnung." : c.bad + " Verstöße.");
        System.exit(c.bad == 0 ? 0 : 1);
    }

    /** Alle Prüfungen; Ergebnis als Zeilen „ok …“ / „FEHLER …“. */
    public List<String> run() {
        meeus();
        sky();
        scattering();
        light();
        catalog();
        gpuContract();
        return lines;
    }

    public int failures() { return bad; }

    void check(boolean c, String what) {
        if (!c) bad++;
        lines.add((c ? "  ok    " : "  FEHLER ") + what);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static double epoch(String local) { return LocalDateTime.parse(local).atZone(ZONE).toInstant().toEpochMilli(); }

    static Sky sky(String local) {
        Sky s = new Sky(epoch(local), LAT, LON);
        s.visibilityKm = 40;
        s.rebuildNow();
        return s;
    }

    static double lum(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    // ------------------------------------------------------------------ Astronomie

    void meeus() {
        // Kopie aus FMoon unverändert? Drei Beispiele aus Meeus, „Astronomical Algorithms“
        FSun sun = FSun.at(2448908.5);
        check(Math.abs(sun.heliocentricLongitude - 19.907372) < 1e-6 && Math.abs(sun.distanceAu - 0.99760775) < 1e-8,
                f("Sonne nach VSOP87 wie Meeus 25.b: L %.6f° (19,907372), R %.8f AE", sun.heliocentricLongitude, sun.distanceAu));
        FMoonPosition moon = FMoonPosition.at(2448724.5);
        check(Math.abs(moon.longitudeWithoutNutation - 133.162655) < 1e-5 && Math.abs(moon.latitude + 3.229126) < 1e-5
                        && Math.abs(moon.distanceKm - 368409.7) < 0.1,
                f("Mond wie Meeus 47.a: λ %.6f°, β %.6f°, Δ %.1f km", moon.longitudeWithoutNutation, moon.latitude, moon.distanceKm));
        FMoonPhase ph = FMoonPhase.at(2448724.5);
        check(Math.abs(ph.phaseAngle - 69.0756) < 1e-4 && Math.abs(ph.illuminatedFraction - 0.6786) < 1e-4,
                f("Mondphase wie Meeus 48.a: i %.4f°, k %.4f", ph.phaseAngle, ph.illuminatedFraction));
        // Präzession: Einheit zu J2000, 50,3″ je Jahr in der Länge
        double[] p0 = Celestial.precession(2451545.0);
        double dev = 0;
        for (int i = 0; i < 9; i++) dev = Math.max(dev, Math.abs(p0[i] - (i % 4 == 0 ? 1 : 0)));
        check(dev < 1e-12, f("Präzession zu J2000 ist die Einheit (Abweichung %.1e)", dev));
        double[] pole = Celestial.apply(Celestial.precession(2451545.0 + 36525), new double[]{0, 0, 1});
        double moved = Math.toDegrees(Math.acos(pole[2]));
        check(Math.abs(moved - 0.5567) < 0.002, f("Himmelspol wandert in 100 Jahren um %.4f° (θ = 2004,3″ → 0,5567°)", moved));
        // Venus nach Standish gegen Meeus 33.a (1992 Dez 20, scheinbar α 21h04m41,454s, δ −18°53′16,84″)
        Planets.Planet v = Planets.at(2448976.5)[1];
        double[] rd = Celestial.raDec(Celestial.apply(Celestial.precession(2448976.5), v.equatorialJ2000()));
        check(Math.abs(rd[0] - 316.17273) < 0.02 && Math.abs(rd[1] + 18.88801) < 0.02 && Math.abs(v.distanceAu() - 0.910947) < 0.001,
                f("Venus wie Meeus 33.a: α %.4f° (316,1727), δ %.4f° (−18,8880), Δ %.5f AE", rd[0], rd[1], v.distanceAu()));
        double vmin = 9, vmax = -9, jmin = 9, jmax = -9;
        for (int d = 0; d < 584; d += 4) {
            Planets.Planet[] pl = Planets.at(2461000.5 + d);
            vmin = Math.min(vmin, pl[1].magnitude());
            vmax = Math.max(vmax, pl[1].magnitude());
            jmin = Math.min(jmin, pl[3].magnitude());
            jmax = Math.max(jmax, pl[3].magnitude());
        }
        check(vmin > -5.0 && vmin < -4.4 && vmax > -4.2 && vmax < -3.6, f("Venus über ihren Zyklus zwischen %.2f und %.2f mag (−4,9 … −3,8)", vmin, vmax));
        check(jmin > -3.0 && jmax < -1.5, f("Jupiter zwischen %.2f und %.2f mag (−2,9 … −1,6)", jmin, jmax));
        // Brechung an der Kimm rund 34′ für die scheinbare, 29′ für die wahre Höhe 0°
        check(Math.abs(Celestial.refractionApparent(0) * 60 - 34.5) < 1 && Math.abs(Celestial.refraction(0) * 60 - 28.9) < 1,
                f("Brechung an der Kimm %.1f′ (scheinbar) und %.1f′ (wahr)", Celestial.refractionApparent(0) * 60, Celestial.refraction(0) * 60));
        double worst = 0;
        for (double h = -0.5; h < 60; h += 0.5) worst = Math.max(worst, Math.abs(h + Celestial.refraction(h) - Celestial.refractionApparent(h + Celestial.refraction(h)) - h));
        check(worst < 0.01, f("Brechung hin und zurück (Sæmundsson, Bennett) auf %.4f° gleich", worst));
    }

    void sky() {
        // Sonnenwende: größte Höhe 90° − Breite + 23,44°
        Sky s = new Sky(epoch("2026-06-21T00:00"), LAT, LON);
        double max = -90;
        for (int m = 12 * 60; m < 15 * 60; m += 1) {
            s.epochMillis = epoch("2026-06-21T00:00") + m * 60_000.0;
            max = Math.max(max, s.updateNow().sunAlt);
        }
        double expect = 90 - LAT + 23.437 + Celestial.refraction(90 - LAT + 23.437);
        check(Math.abs(max - expect) < 0.03, f("Mittagshöhe zur Sonnenwende %.3f° (%.3f°)", max, expect));
        // Sonnenaufgang zwei Wege: Höhe des Mittelpunkts (scheinbar) −0,27° gegen FRiseSet (−0,8333°, wahr)
        FRiseSet rs = FRiseSet.sunFor(FAstroTime.julianDay(2026, 10, 15.0), new FGeoLocation("Roter Sand", LAT, LON, 0, "Europe/Berlin"));
        s.epochMillis = epoch("2026-10-15T06:00");
        double prev = s.updateNow().sunAlt, rise = Double.NaN;
        for (int sec = 6 * 3600 + 30; sec < 10 * 3600; sec += 30) {
            s.epochMillis = epoch("2026-10-15T00:00") + sec * 1000.0;
            double h = s.updateNow().sunAlt + 0.2666;
            if (prev + 0.2666 < 0 && h >= 0) { rise = s.epochMillis; break; }
            prev = h - 0.2666;
        }
        double riseRs = (rs.riseJdUt - 2440587.5) * 86_400_000;
        check(Math.abs(rise - riseRs) < 120_000, f("Sonnenaufgang 15.10.2026: Oberrand an der Kimm %.1f min neben FRiseSet",
                (rise - riseRs) / 60_000));
        // Polarstern steht so hoch wie die Breite (± 0,8°, plus Brechung)
        StarCatalog cat = StarCatalog.get();
        int pol = cat.find(37.95, 89.26, 0.5);
        double pw = 0;
        for (int hh = 0; hh < 24; hh += 3) {
            Sky.State st = sky("2026-10-15T" + String.format("%02d", hh) + ":00").state();
            double[] d = Celestial.apply(st.eqToScene, Celestial.equatorial(cat.ra[pol], cat.dec[pol]));
            pw = Math.max(pw, Math.abs(Celestial.altAz(d)[0] - LAT));
        }
        check(pol >= 0 && pw < 0.8, f("Polarstern über den Tag höchstens %.2f° neben der Breite", pw));
        // Mond: Phase aus den Vektoren gegen FMoonPhase, Vollmond am 26.10.2026
        Sky.State full = sky("2026-10-26T23:00").state();
        double iVec = Math.toDegrees(Math.acos(-(full.moonLight[0] * full.moonDirTrue[0] + full.moonLight[1] * full.moonDirTrue[1] + full.moonLight[2] * full.moonDirTrue[2])));
        check(Math.abs(iVec - full.moonPhaseAngle) < 0.5, f("Phasenwinkel aus der Lage %.2f° gegen FMoon %.2f°", iVec, full.moonPhaseAngle));
        check(full.moonFraction > 0.97, f("Vollmond am 26.10.2026: %.1f %% beleuchtet", full.moonFraction * 100));
        double lux = lum(full.moonColor) * Sky.CD_PER_UNIT;
        check(lux > 0.1 && lux < 0.35, f("Vollmond in %.0f° Höhe beleuchtet mit %.2f lx (0,1 … 0,35)", full.moonAlt, lux));
    }

    // ------------------------------------------------------------------ Streuung

    void scattering() {
        Scattering sc = new Scattering(Atmosphere.forVisibility(40, 0.08));
        double worst = 0, absWorst = 0;
        double r0 = Atmosphere.BOTTOM + 10;
        for (double mu : new double[]{1, 0.7, 0.4, 0.2, 0.1, 0.05, 0.02})
            for (double r : new double[]{r0, Atmosphere.BOTTOM + 2000, Atmosphere.BOTTOM + 20_000}) {
                double[] a = sc.transmittance(r, mu), b = sc.transmittanceExact(r, mu, 4000);
                for (int c = 0; c < 3; c++) {
                    if (b[c] > 0.05) worst = Math.max(worst, Math.abs(a[c] - b[c]) / b[c]);
                    absWorst = Math.max(absWorst, Math.abs(a[c] - b[c]));
                }
            }
        check(worst < 0.015 && absWorst < 0.003, f("Durchlässigkeit aus der Tabelle auf %.2f %% wie die genaue Rechnung (absolut ± %.4f)", worst * 100, absWorst));
        double[] tz = sc.transmittance(r0, 1);
        check(tz[0] > tz[1] && tz[1] > tz[2] && Math.abs(tz[1] - 0.79) < 0.05, f("Zenit-Durchlässigkeit R %.3f > G %.3f > B %.3f", tz[0], tz[1], tz[2]));
        Atmosphere a20 = Atmosphere.forVisibility(20, 0.08);
        check(Math.abs(3.912 / a20.groundExtinction() / 1000 - 20) < 0.2, f("Sicht aus der Auslöschung %.2f km (20)", 3.912 / a20.groundExtinction() / 1000));

        Sky.State noon = sky("2026-10-15T13:00").state();
        double[] zen = noon.radiance(new double[]{0, 1, 0});
        check(zen[2] > zen[1] && zen[1] > zen[0], f("Mittag: Zenit blau (B %.3f > G %.3f > R %.3f)", zen[2], zen[1], zen[0]));
        double lz = lum(zen), dev = 0;
        double zs = Math.toRadians(90 - noon.sunAlt), cz = cie(zs) * (1 - Math.exp(-0.32));
        for (double[] q : new double[][]{{27.5, 206}, {27.5, 236}, {10, 266}, {60, 86}, {40, 356}}) {
            double[] d = Celestial.direction(q[0], q[1]);
            double g = Math.acos(d[0] * noon.sunDir[0] + d[1] * noon.sunDir[1] + d[2] * noon.sunDir[2]);
            double ratio = lum(noon.radiance(d)) / lz, want = cie(g) * (1 - Math.exp(-0.32 / Math.cos(Math.toRadians(90 - q[0])))) / cz;
            dev = Math.max(dev, Math.abs(ratio / want - 1));
        }
        check(dev < 0.3, f("Leuchtdichteverteilung abseits der Sonne wie CIE-Normhimmel „klar“ (größte Abweichung %.0f %%)", dev * 100));
        double horiz = lum(noon.radiance(Celestial.direction(2, noon.sunAz + 90)));
        check(horiz > lz, f("Kimm heller als Zenit (%.2f gegen %.2f)", horiz, lz));

        // Sonne tief: rot; Dämmerung: von Stufe zu Stufe dunkler
        Sky.State low = sky("2026-10-15T18:20").state();
        check(low.sunAlt > 0 && low.sunAlt < 3 && low.sunColor[0] > 5 * low.sunColor[2],
                f("Sonne %.1f° hoch: Licht rot (R/B %.0f)", low.sunAlt, low.sunColor[0] / Math.max(1e-12, low.sunColor[2])));
        double last = Double.MAX_VALUE;
        boolean down = true;
        StringBuilder sb = new StringBuilder();
        for (String t : new String[]{"18:30", "18:50", "19:10", "19:30", "19:50", "20:10", "20:30"}) {
            Sky.State st = sky("2026-10-15T" + t).state();
            double l = lum(st.skyZenith);
            down &= l < last;
            last = l;
            sb.append(f(" %.0f°:%.0e", st.sunAlt, l * Sky.CD_PER_UNIT));
        }
        check(down, "Dämmerung wird stetig dunkler (cd/m² im Zenit)" + sb);
        Sky.State night = sky("2026-10-15T23:30").state();
        double b = -2.5 * Math.log10(lum(night.skyZenith) * Sky.CD_PER_UNIT / 1.08e5);
        check(Math.abs(b - 22.0) < 0.4, f("Nacht ohne Mond: Zenit %.2f mag/″² (natürlicher Himmel 22)", b));
        check(night.limitingMagnitude > 6.2, f("Grenzgröße ohne Mond %.1f mag", night.limitingMagnitude));
        Sky.State fm = sky("2026-10-26T23:00").state();
        check(fm.limitingMagnitude > 3.5 && fm.limitingMagnitude < 5.5, f("Grenzgröße bei Vollmond %.1f mag (3,5 … 5,5)", fm.limitingMagnitude));
        check(night.scotopic() > 0.95 && noon.scotopic() < 0.01, f("Stäbchensehen nachts %.2f, mittags %.2f", night.scotopic(), noon.scotopic()));
    }

    static double cie(double g) {
        return 1 + 10 * (Math.exp(-3 * g) - Math.exp(-3 * Math.PI / 2)) + 0.45 * Math.cos(g) * Math.cos(g);
    }

    // ------------------------------------------------------------------ Licht

    void light() {
        Sky.State noon = sky("2026-10-15T13:00").state();
        double h = (lum(noon.sunColor) * Math.sin(Math.toRadians(noon.sunAlt)) + lum(noon.skyIrradiance)) * Sky.CD_PER_UNIT;
        check(h > 35_000 && h < 80_000, f("Beleuchtung einer waagerechten Fläche mittags %.0f lx (Oktober, 27,5°)", h));
        double ratio = lum(noon.skyIrradiance) / (lum(noon.sunColor) * Math.sin(Math.toRadians(noon.sunAlt)) + lum(noon.skyIrradiance));
        check(ratio > 0.1 && ratio < 0.35, f("Anteil des Himmelslichts %.0f %% (klar: 10 … 35 %%)", ratio * 100));
        check(Math.abs(noon.exposure(0.85) - 0.35 / Sky.REFERENCE_LUMINANCE * Math.pow(noon.adaptation / Sky.REFERENCE_LUMINANCE, -0.85)) < 1e-9
                && noon.adaptation > Sky.REFERENCE_LUMINANCE * 0.7 && noon.adaptation < Sky.REFERENCE_LUMINANCE * 1.4,
                f("Mittag nahe der Bezugshelligkeit (%.3f gegen %.3f)", noon.adaptation, Sky.REFERENCE_LUMINANCE));
        double toSun = noon.viewLuminance(Celestial.direction(noon.sunAlt, noon.sunAz), new double[]{0, 1, 0}, 55, 16 / 9.0);
        double away = noon.viewLuminance(Celestial.direction(20, noon.sunAz + 180), new double[]{0, 1, 0}, 55, 16 / 9.0);
        check(toSun > 2 * away, f("Blickfeld gegen die Sonne %.1f-mal heller als von ihr weg", toSun / away));
        // Mondscheibe: die geeichte Scheibe bringt genau die Bestrahlung des Monds
        Sky.State fm = sky("2026-10-21T22:00").state();
        double[] cal = MoonDisc.calibrate(fm);
        double[][] fr = MoonDisc.frame(fm.moonDirTrue, fm.moonNorth);
        double[] L = {dot(fm.moonLight, fr[0]), dot(fm.moonLight, fr[1]), dot(fm.moonLight, fr[2])};
        int n = 200;
        double sum = 0;
        float[] alb = MoonDisc.albedoTable();
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                double qx = (i + 0.5) / n * 2 - 1, qy = (j + 0.5) / n * 2 - 1, q2 = qx * qx + qy * qy;
                if (q2 >= 1) continue;
                double nz = Math.sqrt(1 - q2);
                double lon = Math.toDegrees(Math.atan2(qx, nz)) + fm.librationLon, lat = Math.toDegrees(Math.asin(qy)) + fm.librationLat;
                int ti = Math.floorMod((int) ((lon + 180) / 360 * MoonDisc.TEX_W), MoonDisc.TEX_W), tj = Math.max(0, Math.min(MoonDisc.TEX_H - 1, (int) ((lat + 90) / 180 * MoonDisc.TEX_H)));
                sum += cal[1] * MoonDisc.shade(qx * L[0] + qy * L[1] + nz * L[2], nz) * alb[tj * MoonDisc.TEX_W + ti] * (4.0 / (n * n));
            }
        double r = Math.toRadians(fm.moonRadiusDeg), irr = sum * r * r;
        check(Math.abs(irr / fm.moonToa[1] - 1) < 0.03, f("Mondscheibe (%.0f %%) bringt %.3f der Bestrahlung aus der Helligkeitsformel",
                fm.moonFraction * 100, irr / fm.moonToa[1]));
        // Blitz
        Sky s = new Sky(epoch("2026-10-15T23:00"), LAT, LON);
        s.rebuildNow();
        s.flash(3000, 2000, -4000, 1, 0, 0.4);
        Sky.State f1 = s.step(0.01);
        Sky.State f2 = s.step(2.0);
        check(f1.flash > 0.5 && f2.flash < 1e-6, f("Blitz leuchtet auf (%.2f) und klingt ab (%.1e)", f1.flash, f2.flash));
        // Bedeckung: Himmel grau, Sonne gedämpft, Sterne aus
        Sky c = new Sky(epoch("2026-10-15T13:00"), LAT, LON);
        c.cloudCover = 1;
        Sky.State ov = c.rebuildNow();
        check(lum(ov.sunColor) < 0.15 * lum(noon.sunColor) && Math.abs(ov.skyZenith[2] / ov.skyZenith[0] - 1.07) < 0.05,
                f("Bedeckt: Sonne %.0f %% der klaren, Zenit grau (B/R %.2f)", 100 * lum(ov.sunColor) / lum(noon.sunColor), ov.skyZenith[2] / ov.skyZenith[0]));
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    // ------------------------------------------------------------------ Katalog

    void catalog() {
        StarCatalog cat = StarCatalog.get();
        check(cat.size() == 8920, f("Sternkatalog: %d Sterne bis 6,5 mag", cat.size()));
        check(Math.abs(cat.mag[0] + 1.44) < 0.05 && Math.abs(cat.ra[0] - 101.287) < 0.05 && Math.abs(cat.dec[0] + 16.716) < 0.05,
                f("Hellster Stern Sirius: %.2f mag bei %.3f° / %.3f°", cat.mag[0], cat.ra[0], cat.dec[0]));
        int bet = cat.find(88.79, 7.41, 0.3), rig = cat.find(78.63, -8.20, 0.3);
        double[] cb = StarCatalog.color(cat.bv[bet]), cr = StarCatalog.color(cat.bv[rig]);
        check(bet >= 0 && rig >= 0 && cb[0] / cb[2] > 1.3 && cr[2] / cr[0] > 1.1,
                f("Beteigeuze rötlich (R/B %.2f, %.0f K), Rigel bläulich (B/R %.2f, %.0f K)", cb[0] / cb[2], StarCatalog.temperature(cat.bv[bet]),
                        cr[2] / cr[0], StarCatalog.temperature(cat.bv[rig])));
        boolean wagen = cat.figures.stream().anyMatch(fg -> fg.name().equals("Großer Wagen") && fg.lines().length == 7);
        check(cat.figures.size() == 27 && wagen, f("Sternbildfiguren: %d (23 aus Sternbild, die Plejaden ohne Linien, dazu 4 des Südhimmels), Großer Wagen mit 7 Linien", cat.figures.size()));
        boolean kreuz = cat.figures.stream().anyMatch(fg -> fg.code().equals("Cru") && fg.lines().length == 2);
        double lmc = StarCatalog.magellanic(80.9, -69.8), smc = StarCatalog.magellanic(13.2, -72.8), off = StarCatalog.magellanic(80.9, -40);
        check(kreuz && lmc > 0.5 && smc > 0.35 && off < 0.01,
                f("Südhimmel (seit Glacier Phase 3): Kreuz des Südens mit 2 Balken; Magellansche Wolken in der Milchstraßenkarte (LMC %.2f, SMC %.2f, daneben %.3f)", lmc, smc, off));
        // Jeder Linienendpunkt liegt auf einem Katalogstern
        int miss = 0, total = 0;
        for (StarCatalog.Figure fg : cat.figures)
            for (double[] l : fg.lines()) {
                total += 2;
                if (cat.find(l[0], l[1], 0.15) < 0) miss++;
                if (cat.find(l[2], l[3], 0.15) < 0) miss++;
            }
        check(miss == 0, f("Alle %d Linienenden der Figuren treffen einen Stern des Katalogs (%d nicht)", total, miss));
        try (InputStream in = Sky.class.getResourceAsStream("milkyway.png")) {
            java.awt.image.BufferedImage im = javax.imageio.ImageIO.read(in);
            double gc = mw(im, 266.4, -28.9), cyg = mw(im, 305, 40), polm = mw(im, 0, 89), ori = mw(im, 85, -20);
            check(gc > 0.4 && cyg > 0.15 && polm == 0 && ori < 0.05 && gc > cyg,
                    f("Milchstraße: Zentrum %.2f > Schwan %.2f, Himmelspol %.2f, abseits %.2f", gc, cyg, polm, ori));
        } catch (IOException | RuntimeException e) {
            check(false, "Milchstraße lesbar: " + e.getMessage());
        }
    }

    static double mw(java.awt.image.BufferedImage im, double ra, double dec) {
        int x = (int) (ra / 360 * im.getWidth()) % im.getWidth(), y = Math.min(im.getHeight() - 1, (int) ((90 - dec) / 180 * im.getHeight()));
        return im.getRaster().getSample(x, y, 0) / 255.0;
    }

    // ------------------------------------------------------------------ Vertrag mit der Grafikkarte

    /** Jedes Uniform, das ein Shader deklariert, kommt in der Liste von SkyGl.uniforms vor (Sampler ausgenommen). */
    void gpuContract() {
        Sky.State st = sky("2026-10-26T23:00").state();
        SkyGl.View v = new SkyGl.View();
        Map<String, Map<String, SkyGl.U>> u = new SkyGl().uniforms(st, v);
        String[][] progs = {{"view", "skyview.frag", "sky_common.glsl", "sky_view.glsl"}, {"sky", "sky.frag", "sky_common.glsl", "sky_view.glsl"},
                {"stars", "stars.vert", "sky_common.glsl"}, {"lines", "lines.vert", "lines.frag", "sky_common.glsl"}};
        Pattern decl = Pattern.compile("uniform\\s+(\\w+)\\s+(\\w+)");
        List<String> missing = new ArrayList<>();
        for (String[] p : progs) {
            Set<String> need = new HashSet<>();
            for (int i = 1; i < p.length; i++) {
                Matcher m = decl.matcher(read(p[i]));
                while (m.find()) if (!m.group(1).startsWith("sampler")) need.add(m.group(2));
            }
            for (String n : need) if (!u.get(p[0]).containsKey(n)) missing.add(p[0] + "." + n);
        }
        check(missing.isEmpty(), "Alle Uniforms der Shader werden gesetzt" + (missing.isEmpty() ? "" : ": fehlen " + missing));
        float[] sv = SkyGl.starVertices(StarCatalog.get());
        check(sv.length == 7 * 8920 && Math.abs(sv[3] + 1.44) < 0.05, f("Sternpuffer: %d Werte, erster Stern %.2f mag", sv.length, sv[3]));
        double[] d = SkyGl.skyViewDir(0.25, 0.25, Atmosphere.BOTTOM + 10);
        check(Math.abs(Celestial.altAz(d)[1] - 90) < 1e-6 && d[1] > 0, f("Himmelstabelle: u = 0,25 zeigt nach Osten (%.1f°)", Celestial.altAz(d)[1]));
    }

    static String read(String file) {
        try (InputStream in = SkyGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IOException("fehlt");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
