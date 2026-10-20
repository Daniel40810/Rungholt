package com.dan.ground.demo;

import com.dan.ground.Batch;
import com.dan.ground.Biome;
import com.dan.ground.Meadow;
import com.dan.ground.Plant;
import com.dan.ground.Site;

/**
 * Selbsttest des Boden-Pakets ohne Fenster: Streuung (gleicher Startwert, gleiche Wiese; Bedeckung
 * wird eingehalten; Ausdünnung in der Ferne), Wind (Spitzen bewegen sich mit dem Wind, Böen wandern),
 * Niedertreten und Erholen, Jahreszeit (Grün, Gold, Blüte, Schnee) und Rechenzeit.
 */
public final class GroundCheck {
    private static int bad, all;

    public static void main(String[] a) {
        // gleicher Startwert, gleiche Wiese
        Meadow m1 = new Meadow(Biome.yellowstone(), Site.MEADOW), m2 = new Meadow(Biome.yellowstone(), Site.MEADOW);
        m1.setSeason(180, 0); m2.setSeason(180, 0);
        m1.wind.speed = 0; m2.wind.speed = 0;
        m1.update(1, 0, 0); m2.update(1, 0, 0);
        check("Streuung: gleicher Startwert, gleiche Wiese", m1.batch.nv == m2.batch.nv && m1.batch.nt == m2.batch.nt && m1.batch.nv > 1000, m1.batch.nv + " Ecken");
        Meadow m3 = new Meadow(Biome.yellowstone(), Site.MEADOW);
        m3.seed = 99; m3.setSeason(180, 0); m3.wind.speed = 0; m3.update(1, 0, 0);
        check("Streuung: anderer Startwert, andere Wiese", m3.batch.nv != m1.batch.nv, m3.batch.nv + " / " + m1.batch.nv);

        // Bedeckung: links (x < 0) kein Gras, rechts nur Gras
        Site half = new Site() {
            @Override public float height(double x, double z) { return 0; }
            @Override public void cover(double x, double z, float[] o) { o[0] = x < 0 ? 0 : 1; o[1] = 0; o[2] = x < 0 ? 1 : 0; o[3] = 0; o[4] = 0.5f; }
        };
        Meadow mh = new Meadow(Biome.yellowstone(), half);
        mh.setSeason(180, 0); mh.wind.speed = 0; mh.update(1, 0, 0);
        int grassLeft = 0, stoneRight = 0;
        Batch b = mh.batch;
        for (int i = 0; i < b.nv; i++) {
            if (b.xyz[3 * i] < -0.3f && b.solid[i] == 0) grassLeft++;
            if (b.xyz[3 * i] > 0.3f && b.solid[i] == 1) stoneRight++;
        }
        check("Bedeckung: kein Gras, wo keines sein soll; keine Steine im reinen Gras", grassLeft == 0 && stoneRight == 0, grassLeft + " / " + stoneRight);

        // Ausdünnung: nah dichter als fern (je Fläche)
        int nearV = 0, farV = 0;
        b = m1.batch;
        for (int i = 0; i < b.nv; i++) {
            float d = (float) Math.hypot(b.xyz[3 * i], b.xyz[3 * i + 2]);
            if (d < 6) nearV++;
            else if (d > 24 && d < 30) farV++;
        }
        float nearDen = nearV / (float) (Math.PI * 36), farDen = farV / (float) (Math.PI * (900 - 576));
        check("Ausdünnung: in der Ferne weniger je Quadratmeter", farDen < nearDen * 0.4f && farDen > 0, String.format("%.0f / %.0f Ecken je m²", nearDen, farDen));

        // Wind: Spitzen gehen mit dem Wind, Wurzeln bleiben
        Meadow mw = new Meadow(Biome.yellowstone(), Site.MEADOW);
        mw.setSeason(180, 0);
        mw.wind.speed = 0; mw.update(1, 0, 0);
        float[] rest = java.util.Arrays.copyOf(mw.batch.xyz, 3 * mw.batch.nv);
        int nv = mw.batch.nv;
        mw.wind.speed = 10; mw.wind.direction = 0; mw.wind.gustiness = 0;
        mw.update(2, 0, 0);
        double sumDx = 0, maxBase = 0;
        int tips = 0;
        for (int i = 0; i < nv; i++) {
            float dx = mw.batch.xyz[3 * i] - rest[3 * i], dy = mw.batch.xyz[3 * i + 1] - rest[3 * i + 1];
            if (rest[3 * i + 1] > 0.25f) { sumDx += dx; tips++; }
            if (rest[3 * i + 1] < 0.005f && mw.batch.solid[i] == 0) maxBase = Math.max(maxBase, Math.abs(dx));
        }
        check("Wind: die Spitzen neigen sich mit dem Wind (+x)", tips > 100 && sumDx / tips > 0.03, String.format("%.3f m im Mittel", sumDx / tips));
        check("Wind: die Wurzeln bleiben", maxBase < 0.01, String.format("%.4f m", maxBase));
        mw.wind.speed = 18;
        mw.update(3, 0, 0);
        double sumDx2 = 0;
        for (int i = 0; i < nv; i++) if (rest[3 * i + 1] > 0.25f) sumDx2 += mw.batch.xyz[3 * i] - rest[3 * i];
        check("Wind: Sturm biegt stärker", sumDx2 > sumDx * 1.3, String.format("%.3f / %.3f", sumDx2 / tips, sumDx / tips));

        // Böen wandern mit dem Wind
        com.dan.ground.Breeze br = new com.dan.ground.Breeze();
        br.speed = 8; br.direction = 0; br.gustiness = 1;
        float[] o = new float[3];
        float best = -1, bestShift = 0;
        float[] line0 = new float[200], line1 = new float[200];
        for (int i = 0; i < 200; i++) { br.bend(i * 0.5f, 3, 0, o); line0[i] = o[0]; br.bend(i * 0.5f, 3, 2, o); line1[i] = o[0]; }
        for (int s = -40; s <= 40; s++) {
            float c = 0;
            for (int i = 40; i < 160; i++) c += line0[i] * line1[i + s];
            if (c > best) { best = c; bestShift = s * 0.5f; }
        }
        check("Wind: Böen laufen in Windrichtung", bestShift > 5 && bestShift < 20, bestShift + " m in 2 s");

        // Niedertreten und Erholen
        Meadow mp = new Meadow(Biome.yellowstone(), Site.MEADOW);
        mp.setSeason(180, 0); mp.wind.speed = 0;
        mp.update(1, 0, 0);
        float h0 = avgTipHeight(mp.batch, 0, 0, 0.5f);
        mp.pushers = new float[]{0, 0, 0.8f, 1}; mp.pusherCount = 1;
        mp.update(1.1f, 0, 0);
        float h1 = avgTipHeight(mp.batch, 0, 0, 0.5f);
        mp.pusherCount = 0;
        for (int k = 0; k < 200; k++) mp.update(1.1f + k * 0.1f, 0, 0);
        float h2 = avgTipHeight(mp.batch, 0, 0, 0.5f);
        check("Niedertreten: Gras unter dem Fuß liegt flach", h1 < h0 * 0.6f, String.format("%.2f → %.2f m", h0, h1));
        check("Niedertreten: richtet sich wieder auf", h2 > h0 * 0.9f, String.format("%.2f m nach 20 s", h2));

        // Jahreszeit
        Meadow ms = new Meadow(Biome.yellowstone(), Site.MEADOW);
        float[] cJune = new float[3], cAug = new float[3], cJan = new float[3];
        ms.setSeason(170, 0); ms.update(1, 0, 0); avgGrass(ms.batch, cJune);
        ms.setSeason(245, 0); ms.update(2, 0, 0); avgGrass(ms.batch, cAug);
        check("Jahreszeit: im Juni grün, im August golden", cJune[1] > cJune[0] && cAug[0] > cAug[1] * 1.05f,
                String.format("Juni %.3f/%.3f, Aug. %.3f/%.3f (rot/grün)", cJune[0], cJune[1], cAug[0], cAug[1]));
        ms.setSeason(Plant.lupine().bloomPeak, 0); ms.update(3, 0, 0);
        int blueJuly = countColor(ms.batch, 0.1f, 0.45f);
        ms.setSeason(20, 0); ms.update(4, 0, 0);
        int blueJan = countColor(ms.batch, 0.1f, 0.45f);
        check("Jahreszeit: Lupinen blühen im Sommer, nicht im Winter", blueJuly > 50 && blueJan < blueJuly / 10, blueJuly + " / " + blueJan);
        ms.setSeason(20, 1); ms.update(5, 0, 0);
        int thin = 0;
        for (int i = 0; i < ms.batch.nv; i++) if (ms.batch.solid[i] == 0) thin++;
        check("Schnee: Gras und Blumen liegen darunter", thin == 0, thin + " Ecken");

        // Rechenzeit
        Meadow mt = new Meadow(Biome.yellowstone(), Site.MEADOW);
        mt.setSeason(180, 0);
        mt.update(0, 0, 0);
        long t0 = System.nanoTime();
        for (int k = 1; k <= 20; k++) mt.update(k / 30f, k * 0.05, 0);
        double ms1 = (System.nanoTime() - t0) / 1e6 / 20;
        check("Rechenzeit: Bild unter 40 ms (" + mt.batch.nt + " Dreiecke)", ms1 < 40, String.format(java.util.Locale.GERMANY, "%.1f ms", ms1));

        // Schlei im Herbst (seit Haithabu Phase 2b): Farn rostbraun, Schafgarbe blüht noch, Findlinge selten
        Biome sb = Biome.schlei();
        Plant fern = null, yarrow = null;
        for (Biome.Entry e : sb.entries) { if (e.plant.name.equals("Adlerfarn")) fern = e.plant; if (e.plant.name.equals("Schafgarbe")) yarrow = e.plant; }
        Meadow msl = new Meadow(sb, Site.MEADOW);
        check("Schlei: Adlerfarn im Oktober schon braun, Schafgarbe blüht noch, im Januar nicht", fern != null && yarrow != null && 289 > fern.dryStart
                && msl.bloom(yarrow, 289) > 0 && msl.bloom(yarrow, 20) == 0, "");

        // Deich an der Nordsee (seit Rungholt Phase 3): weniger als halb so viele Dreiecke wie die Blumenwiese, und schneller
        Meadow me = new Meadow(Biome.europe(), Site.MEADOW), md = new Meadow(Biome.deich(), Site.MEADOW);
        me.setSeason(200, 0);
        md.setSeason(200, 0);
        me.update(0, 0, 0);
        md.update(0, 0, 0);
        long te = System.nanoTime();
        for (int k = 1; k <= 10; k++) me.update(k / 30f, 0, 0);
        double msE = (System.nanoTime() - te) / 1e6 / 10;
        long td = System.nanoTime();
        for (int k = 1; k <= 10; k++) md.update(k / 30f, 0, 0);
        double msD = (System.nanoTime() - td) / 1e6 / 10;
        check("Deich: höchstens halb so viele Dreiecke wie die Blumenwiese (" + md.batch.nt + " zu " + me.batch.nt + ")",
                md.batch.nt > 0 && md.batch.nt * 2 <= me.batch.nt, String.format(java.util.Locale.GERMANY, "%.1f zu %.1f ms", msD, msE));

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + all + " Prüfungen fehlgeschlagen.");
        if (bad > 0) System.exit(1);
    }

    /** Mittlere Höhe der Pflanzenecken im Kreis r um (x, z): niedergetretenes Gras liegt tiefer. */
    private static float avgTipHeight(Batch b, float x, float z, float r) {
        float sum = 0;
        int n = 0;
        for (int i = 0; i < b.nv; i++) {
            if (b.solid[i] == 1) continue;
            float dx = b.xyz[3 * i] - x, dz = b.xyz[3 * i + 2] - z;
            if (dx * dx + dz * dz > r * r) continue;
            sum += b.xyz[3 * i + 1]; n++;
        }
        return n == 0 ? 0 : sum / n;
    }

    private static void avgGrass(Batch b, float[] out) {
        double r = 0, g = 0;
        int n = 0;
        for (int i = 0; i < b.nv; i++) if (b.solid[i] == 0) { r += b.rgb[3 * i]; g += b.rgb[3 * i + 1]; n++; }
        out[0] = (float) (r / n); out[1] = (float) (g / n);
    }

    private static int countColor(Batch b, float maxG, float minB) {
        int n = 0;
        for (int i = 0; i < b.nv; i++) if (b.rgb[3 * i + 2] > minB && b.rgb[3 * i + 1] < maxG + 0.1f && b.rgb[3 * i + 2] > b.rgb[3 * i] * 2) n++;
        return n;
    }

    private static void check(String what, boolean ok, String detail) {
        all++;
        if (!ok) bad++;
        System.out.printf("  %-4s  %s%s%n", ok ? "ok" : "FEHL", what, detail.isEmpty() ? "" : "  (" + detail + ")");
    }
}
