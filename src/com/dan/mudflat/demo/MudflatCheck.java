package com.dan.mudflat.demo;

import com.dan.mudflat.Mudflat;
import com.dan.mudflat.WetField;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

import java.util.Locale;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Phase 1 prüft die Hülle; Phase 5 hängt den Fotovergleich mit {@code templates/Watt.png}, die
 * Trocknung je Texel und das Zeitbudget an.
 */
public final class MudflatCheck {
    private static int bad;

    public static void main(String[] args) {
        check(Math.abs(Mudflat.fresnel(1) - 0.02) < 1e-9 && Mudflat.fresnel(0) > 0.999, "Fresnel: 2 % senkrecht, Spiegel bei streifendem Blick");
        check(Mudflat.fresnel(Math.cos(Math.toRadians(85))) > 0.6, String.format(Locale.GERMAN, "Blick 5° über der Fläche: %.0f %% Spiegel", Mudflat.fresnel(Math.cos(Math.toRadians(85))) * 100));
        Mudflat m = Mudflat.wadden();
        check(m.albedo(Mudflat.Sediment.SAND, 1) < m.albedo(Mudflat.Sediment.SAND, 0) * 0.6, "nasser Sand ist deutlich dunkler als trockener");
        check(Mudflat.dryAlbedo(Mudflat.Sediment.SCHLICK) < Mudflat.dryAlbedo(Mudflat.Sediment.MISCHWATT)
                && Mudflat.dryAlbedo(Mudflat.Sediment.MISCHWATT) < Mudflat.dryAlbedo(Mudflat.Sediment.SAND), "Schlick < Mischwatt < Sand");
        double shade = m.wetnessAfter(1, 0, 0), sun = m.wetnessAfter(1, 1, 1);
        check(shade > 0.7 && sun < 0.2, String.format(Locale.GERMAN, "nach 1 h: Schatten %.0f %% nass, Sonne und Wind %.0f %%", shade * 100, sun * 100));
        check(Mudflat.beach().halfDrySunWindMinutes < m.halfDrySunWindMinutes, "Strand trocknet schneller als Watt");
        check(m.describe().startsWith("mudflat: Stufe 6"), m.describe());
        material(m);
        relief();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    static void material(Mudflat m) {
        // Sediment: eine Rampe vom Priel (Laufzeit 0, tief) zur Bucht am Deich (Laufzeit groß, hoch)
        int n = 64;
        WetField f = new WetField(m, n, n, 0, 0, 16);
        float[] ground = new float[n * n], travel = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                ground[j * n + i] = (float) (-2.5 + 3.5 * i / (n - 1.0));
                travel[j * n + i] = (float) (i * 60.0);
            }
        f.classify(ground, travel, -1.6, 1.5);
        double seaward = 0, landward = 0;
        for (int j = 0; j < n; j++) { seaward += f.sediment[j * n + 2]; landward += f.sediment[j * n + n - 3]; }
        seaward /= n; landward /= n;
        check(seaward < 0.25 && landward > 0.6, String.format(Locale.GERMAN, "Sediment: seewärts %.2f (Sand), landwärts %.2f (Schlick)", seaward, landward));
        double[] sh = f.shares(null, -99, 99);
        check(sh[0] > 0.1 && sh[2] > 0.1 && Math.abs(sh[0] + sh[1] + sh[2] - 1) < 1e-9, String.format(Locale.GERMAN, "Anteile Sand %.0f %%, Mischwatt %.0f %%, Schlick %.0f %%", sh[0] * 100, sh[1] * 100, sh[2] * 100));
        check(f.classAt(2 * 16 + 8, 8) == Mudflat.Sediment.SAND && f.classAt((n - 3) * 16 + 8, 8) == Mudflat.Sediment.SCHLICK, "Klassen am Ort: Sand am Priel, Schlick am Deich");

        // Wasser: die linke Hälfte unter Wasser mit Strömung nach Süden, die rechte trocken
        float[] depth = new float[n * n], u = new float[n * n], v = new float[n * n];
        for (int j = 0; j < n; j++) for (int i = 0; i < n / 2; i++) { depth[j * n + i] = 0.5f; v[j * n + i] = 0.4f; }
        WetField.Weather sunny = WetField.Weather.SUNNY_WINDY, shade = WetField.Weather.CALM_SHADE;
        f.addTrack(8, 8, 1, 0, 0);
        f.addTrack(n * 16 - 8, 8, 1, 0, 0);
        f.step(0, depth, u, v, null, sunny);
        check(f.tracks().size() == 1, "die Flut löscht Spuren, auf dem Trockenen bleiben sie");
        for (int s = 1; s <= 30; s++) f.step(s / 600.0, depth, u, v, null, sunny);   // 3 min Strömung nach Süden
        int c0 = 5 * n + 3;
        double ang = Math.toDegrees(Math.atan2(f.dirX[c0], f.dirZ[c0])), str = f.currentAt(3 * 16 + 8, 5 * 16 + 8);
        check(Math.abs(ang) < 8 && Math.abs(str - 0.8) < 0.08, String.format(Locale.GERMAN, "Rippel folgen der Strömung: Richtung %.1f° neben Süd, Stärke %.2f bei 0,4 m/s", ang, str));
        float[] weak = new float[n * n];
        for (int k = 0; k < weak.length; k++) weak[k] = v[k] * 0.25f;
        for (int s = 31; s <= 200; s++) f.step(s / 600.0, depth, u, weak, null, sunny);
        check(f.currentAt(3 * 16 + 8, 5 * 16 + 8) < 0.3, String.format(Locale.GERMAN, "bei 0,1 m/s werden sie flach: Stärke %.2f", f.currentAt(3 * 16 + 8, 5 * 16 + 8)));
        // ablaufen lassen: nach 1 h Sonne und Wind trocken, im Schatten noch nass
        java.util.Arrays.fill(depth, 0);
        WetField g = new WetField(m, n, n, 0, 0, 16);
        System.arraycopy(f.sediment, 0, g.sediment, 0, n * n);
        f.step(0.0, depth, null, null, null, sunny);
        g.step(0.0, depth, null, null, null, shade);
        for (int s = 1; s <= 12; s++) { f.step(s / 12.0, depth, null, null, null, sunny); g.step(s / 12.0, depth, null, null, null, shade); }
        int sand = 2 * n + 2, schlick = 2 * n + n - 3;
        check(f.wet[sand] < 0.42 && f.wet[sand] < WetField.residual(f.sediment[sand]) + 0.2 && g.wet[sand] > 0.75, String.format(Locale.GERMAN, "Sand nach 1 h: Sonne und Wind %.0f %%, Schatten %.0f %%", f.wet[sand] * 100, g.wet[sand] * 100));
        check(f.wet[schlick] > f.wet[sand] + 0.1, String.format(Locale.GERMAN, "Schlick bleibt feuchter: %.0f %% gegen Sand %.0f %%", f.wet[schlick] * 100, f.wet[sand] * 100));
        check(Math.abs(f.dryHours[sand] - 1) < 1e-4, String.format(Locale.GERMAN, "Trockenzeit gezählt: %.2f h", f.dryHours[sand]));
        f.step(1.1, depth, null, null, null, new WetField.Weather(0, 2, 12, 4));
        check(f.wet[sand] > 0.99, "Regen macht wieder nass");
        check(f.halfLife(new WetField.Weather(1, 10, -3, 0)) > f.halfLife(sunny) * 2.5, "Frost bremst das Trocknen");
        // Sprung: die Nässe folgt aus der übergebenen Trockenzeit
        float[] dh = new float[n * n];
        java.util.Arrays.fill(dh, 6);
        WetField j = new WetField(m, n, n, 0, 0, 16);
        j.step(100, depth, null, null, dh, sunny);
        check(Math.abs(j.wet[sand] - WetField.residual(j.sediment[sand])) < 0.02, String.format(Locale.GERMAN, "Sprung: 6 h trocken in der Sonne → %.0f %% (Rest)", j.wet[sand] * 100));

        // Textur
        byte[] tex = new byte[n * n * 4];
        f.fill(tex);
        check((tex[sand * 4] & 255) > 250 && Math.abs((tex[sand * 4 + 1] & 255) - Math.round(f.sediment[sand] * 255)) <= 1, "Textur: R Nässe, G Sediment");
        int o = (5 * n + 3) * 4;
        check(Math.abs((tex[o + 2] & 255) - Math.round((f.dirX[5 * n + 3] * 0.5 + 0.5) * 255)) <= 1 && Math.abs((tex[o + 3] & 255) - Math.round((f.dirZ[5 * n + 3] * 0.5 + 0.5) * 255)) <= 1, "Textur: BA Richtung");

        // Farben: nass dunkler, Schlick dunkler, und im Bereich der Vorlage
        double[] wetSand = WetField.color(0.1, 1, m.wetDarkening), drySand = WetField.color(0.1, 0, m.wetDarkening), wetMud = WetField.color(0.9, 1, m.wetDarkening);
        check(lum(wetSand) < lum(drySand) * 0.6 && lum(wetMud) < lum(wetSand), String.format(Locale.GERMAN, "Albedo: trockener Sand %.2f, nasser Sand %.2f, nasser Schlick %.2f", lum(drySand), lum(wetSand), lum(wetMud)));
        check(drySand[0] > drySand[1] && drySand[1] > drySand[2], "Farbe braun-gelb (R > G > B)");
        File ref = new File("templates/Watt.png");
        if (ref.isFile()) {
            try {
                BufferedImage img = ImageIO.read(ref);
                // untere Hälfte: das Watt im Vordergrund; mittlere Farbe linear, gegen Albedo × Licht ~ 1
                double[] mean = new double[3];
                int c = 0;
                for (int y = img.getHeight() / 2; y < img.getHeight(); y += 2)
                    for (int x = 0; x < img.getWidth(); x += 2) {
                        int p = img.getRGB(x, y);
                        double r = lin((p >> 16) & 255), gg = lin((p >> 8) & 255), b = lin(p & 255);
                        if (r + gg + b > 1.5) continue;   // Himmelsspiegel auslassen
                        mean[0] += r; mean[1] += gg; mean[2] += b; c++;
                    }
                for (int i = 0; i < 3; i++) mean[i] /= Math.max(1, c);
                double hueFoto = mean[0] / Math.max(1e-4, mean[2]), hueModel = WetField.color(0.6, 0.9, m.wetDarkening)[0] / WetField.color(0.6, 0.9, m.wetDarkening)[2];
                check(Math.abs(hueFoto - hueModel) / hueModel < 0.45, String.format(Locale.GERMAN, "Vorlage Watt.png: R/B im Foto %.2f, im Modell %.2f", hueFoto, hueModel));
            } catch (Exception e) {
                check(false, "Vorlage nicht lesbar: " + e);
            }
        } else System.out.println("  (ohne templates/Watt.png: kein Fotovergleich)");

        // Zeitbudget: ein Schritt auf dem Gitter der App (1 024²) unter 4 ms im Mittel
        WetField big = new WetField(m, 1024, 1024, 0, 0, 16);
        float[] d0 = new float[1024 * 1024];
        for (int k = 0; k < d0.length; k += 3) d0[k] = 0.3f;
        for (int s = 0; s < 5; s++) big.step(s * 0.01, d0, null, null, null, sunny);
        long t0 = System.nanoTime();
        for (int s = 5; s < 25; s++) big.step(s * 0.01, d0, null, null, null, sunny);
        double ms = (System.nanoTime() - t0) / 1e6 / 20;
        check(ms < 15, String.format(Locale.GERMAN, "Schritt 1 024²: %.2f ms (Ziel unter 4 ms auf dem Ryzen; hier unter 15)", ms));
        check(f.describe().startsWith("Watt 64×64"), f.describe());
    }

    /** Phase 6: die Feinform. */
    static void relief() {
        com.dan.mudflat.Relief r = new com.dan.mudflat.Relief();
        // eine Prielkante: das Raster fällt über 60 m von NHN +0,3 auf −2,5 m; geglättet über 32 m wie die Mip-Stufe 2
        double maxRaw = 0, maxRel = 0, step = 0.5;
        for (int line = 0; line < 20; line++) {
            double z = line * 37.0, prevRaw = Double.NaN, prevRel = Double.NaN;
            for (double x = -60; x <= 60; x += step) {
                double hf = edgeProfile(x), hs = smoothProfile(x), sl = Math.abs(smoothProfile(x + 16) - smoothProfile(x - 16)) / 32;
                double rel = hf + r.edge(x + 1000, z, hf, hs, sl);
                if (!Double.isNaN(prevRaw)) { maxRaw = Math.max(maxRaw, Math.abs(hf - prevRaw) / step); maxRel = Math.max(maxRel, Math.abs(rel - prevRel) / step); }
                prevRaw = hf;
                prevRel = rel;
            }
        }
        check(maxRel > maxRaw * 1.8, String.format(Locale.GERMAN, "Prielkante: steilster Hang %.0f %% statt %.0f %% im Raster (Abbruch)", maxRel * 100, maxRaw * 100));
        // Rinnsale: ein Netz, das einen kleinen Teil der Fläche deckt, nicht im Priel
        int n = 0, in = 0;
        double sum = 0;
        for (int j = 0; j < 400; j++)
            for (int i = 0; i < 400; i++) {
                double x = i * 0.73, z = j * 0.73;
                float d = r.runnelDepth(x, z, -0.5, 0.01);
                if (d < -0.06) in++;
                sum += d;
                n++;
            }
        double share = in / (double) n;
        check(share > 0.02 && share < 0.2, String.format(Locale.GERMAN, "Rinnsale decken %.1f %% der Fläche (tiefer als 6 cm), im Mittel %.1f cm", share * 100, -sum / n * 100));
        check(r.runnelDepth(100, 100, -2.5, 0.01) == 0 && r.runnelDepth(100, 100, -0.5, 0.2) == 0, "keine Rinnsale im Priel und an der Kante");
        // gleiche Rechnung wie im Shader: die Konstanten des Bausteins
        String g = com.dan.mudflat.gl.MudflatGl.reliefGlsl();
        check(g.contains("374761393u") && g.contains("xz / 23.0 + w * 1.6") && g.contains("0.055") && g.contains("exp(-e * e / 1.6)") && g.contains("xz / 4.1"),
                "mudflat_relief.glsl rechnet wie Relief.java (Hash, Rinnsale, Kante)");
        r.on = false;
        check(r.delta(5, 5, 0, 0, 0.05) == 0, r.describe());
    }

    static double edgeProfile(double x) { return -1.1 - 1.4 * Math.tanh(x / 12.0); }
    static double smoothProfile(double x) { double s = 0; for (int k = -16; k <= 16; k++) s += edgeProfile(x + k); return s / 33; }

    static double lum(double[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }
    static double lin(int v) { double c = v / 255.0; return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private MudflatCheck() { }
}
