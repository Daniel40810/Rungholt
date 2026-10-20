package com.dan.ice.demo;

import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.IceNoise;
import com.dan.ice.IceOptics;
import com.dan.ice.IceShapes;
import com.dan.ice.gl.IceGl;

import java.util.Arrays;

/**
 * Prüfklasse ohne Fenster (Paketvertrag Punkt 10). Endet mit „Alles in Ordnung.“ oder mit den Verstößen
 * (Rückgabewert 1). Hülle (Phase 1), Material (Phase 4): Optik gegen die Physik, Netze, Risskachel, Shadervertrag.
 */
public final class IceCheck {
    private static int bad;

    public static void main(String[] args) {
        System.out.println("Hülle");
        check(Ice.firn().bubbles > Ice.glacier().bubbles && Ice.glacier().bubbles > Ice.blue().bubbles, "Presets: Firn weißer als Gletschereis, das weißer als Blaueis");
        double f = new Ice().submergedFraction(1000);
        check(Math.abs(f - 0.9) < 1e-9, "Gletschereis schwimmt mit 9 Zehnteln unter Wasser");
        check(Ice.STAGE == 5 && new Ice().describe().contains("Blasen"), "Stand: Gletscher (Phase 5)");
        check(Ice.Quality.values().length == 4, "vier Qualitätsstufen LOW bis ULTRA");
        Ice c = Ice.glacier().copy();
        check(c.bubbles == 0.5 && c.moraine == 0.15 && c.bandDir != Ice.glacier().bandDir, "copy() übernimmt alle Werte, eigene Felder");

        System.out.println("Optik");
        double h0 = IceOptics.halfLength(0), h1 = IceOptics.halfLength(1), h2 = IceOptics.halfLength(2);
        check(h0 > 2 && h0 < 4 && h1 > 15 && h1 < 30 && h2 > 100, String.format("Halbwertslängen Rot %.1f m, Grün %.0f m, Blau %.0f m (Rot wenige m, Blau über 100 m)", h0, h1, h2));
        check(Math.abs(IceOptics.F0 - 0.0181) < 2e-4, "Fresnel senkrecht 1,8 % (n = 1,31)");
        check(Math.abs(IceOptics.fresnel(0) - 1) < 1e-9 && IceOptics.fresnel(1) == IceOptics.F0, "Fresnel streifend 1, senkrecht F0");
        boolean energy = true;
        for (double k : new double[]{0.01, 0.5, 5}) for (double s : new double[]{0.01, 1, 300}) for (double d : new double[]{0, 0.1, 3, 500}) {
            double[] rt = IceOptics.km(k, s, d);
            energy &= rt[0] >= -1e-12 && rt[1] >= -1e-12 && rt[0] + rt[1] <= 1 + 1e-9 && Double.isFinite(rt[0]);
        }
        check(energy, "Kubelka-Munk: R, T ≥ 0 und R + T ≤ 1 für alle Dicken, auch 500 m (stabil)");
        double[] thin = IceOptics.km(0.1, 10, 0), deep = IceOptics.km(0.1, 10, 1000);
        check(thin[0] < 1e-9 && Math.abs(thin[1] - 1) < 1e-9, "Dicke 0: nichts reflektiert, alles durch");
        check(Math.abs(deep[0] - IceOptics.rInf(0.1, 10)) < 1e-6 && deep[1] < 1e-9, "unendlich dick: R → R∞, T → 0");
        double[] noK = IceOptics.km(0, 5, 2);
        check(Math.abs(noK[0] + noK[1] - 1) < 1e-6, "ohne Absorption: R + T = 1");
        Ice firn = Ice.firn(), glac = Ice.glacier(), blue = Ice.blue();
        double[] rf = IceOptics.reflectance(firn, 10), rb = IceOptics.reflectance(blue, 10);
        check(rf[0] > 0.6 && rf[2] / rf[0] < 1.15, String.format("Firn weiß: R = %.2f/%.2f/%.2f", rf[0], rf[1], rf[2]));
        Ice clear = blue.copy();
        clear.weathering = 0;
        check(IceOptics.blueness(clear, 10) > 3 * IceOptics.blueness(firn, 10), String.format("frisch gebrochenes Blaueis blauer als Firn (Blau/Rot %.1f gegen %.2f)", IceOptics.blueness(clear, 10), IceOptics.blueness(firn, 10)));
        double[] tb = IceOptics.transmittance(blue, 10);
        check(tb[2] > 2 * tb[0], String.format("Licht durch 10 m Blaueis: blau (T = %.2f/%.2f/%.2f)", tb[0], tb[1], tb[2]));
        check(IceOptics.blueness(clear, 30) > IceOptics.blueness(clear, 1), "dicker wird blauer");
        Ice crusty = blue.copy();
        crusty.weathering = 1;
        check(IceOptics.reflectance(crusty, 10)[0] > 2 * IceOptics.reflectance(clear, 10)[0], "Kruste macht weiß (verwittert gegen frisch gebrochen)");
        double[] dg = IceOptics.direct(clear, 2), dgThick = IceOptics.direct(clear, 20);
        check(dg[2] > dg[0] && dgThick[2] < dg[2], "Durchsicht: Blau kommt weiter als Rot, mit der Dicke weniger");
        double[] l1 = IceOptics.layered(0.1, 50, 0, 2, 5), l2 = IceOptics.km(0.1, 2, 5);
        check(Math.abs(l1[0] - l2[0]) < 1e-9 && Math.abs(l1[1] - l2[1]) < 1e-9, "zwei Schichten mit Kruste 0 = eine Schicht");
        check(IceOptics.scattering(0) == IceOptics.SIGMA_MIN && Math.abs(IceOptics.scattering(1) - IceOptics.SIGMA_MAX) < 1e-9, "Streuung 0,05 bis 200 /m");
        check(glac.weathering > blue.weathering, "Gletschereis verwitterter als Blaueis");

        System.out.println("Netze");
        IceMesh blk = IceShapes.block(10, 0.35);
        double vb = blk.volume();
        check(vb > 975 && vb < 1000, String.format("Block 10 m mit Fase: %.1f m³", vb));
        check(Math.abs(blk.volumeBelow(0) - vb / 2) < 1e-6 * vb, "Block halb unter y = 0: halbes Volumen");
        double[] cen = blk.centroid();
        check(Math.abs(cen[0]) + Math.abs(cen[1]) + Math.abs(cen[2]) < 1e-6, "Schwerpunkt des Blocks in der Mitte");
        check(outward(blk), "Block: alle Flächen zeigen nach außen");
        IceMesh berg = IceShapes.iceberg(7, 7, 0.8, 3);
        double vg = berg.volume(), wl = berg.waterline(0.9);
        check(vg > 100 && outward(berg), String.format("Eisberg: %.0f m³, %d Dreiecke, Flächen nach außen", vg, berg.triangles()));
        check(Math.abs(berg.volumeBelow(wl) / vg - 0.9) < 1e-3, "Wasserlinie: 90 % unter Wasser");
        IceMesh b2 = IceShapes.iceberg(7, 7, 0.8, 3), b3 = IceShapes.iceberg(8, 7, 0.8, 3);
        check(Arrays.equals(berg.pos, b2.pos) && !Arrays.equals(berg.pos, b3.pos), "gleicher seed, gleicher Eisberg; anderer seed, anderer");
        IceMesh sh = IceShapes.shifted(berg, 0, -wl, 0);
        check(Math.abs(sh.volumeBelow(0) / vg - 0.9) < 1e-3, "verschoben auf die Wasserlinie: 90 % unter y = 0");

        System.out.println("Risse");
        byte[] t = IceNoise.cracks(1);
        check(t.length == IceNoise.N * IceNoise.N * IceNoise.N * 4 && t == IceNoise.cracks(1), "Kachel 64³ RGBA, einmal gerechnet");
        int near = 0, n = IceNoise.N * IceNoise.N * IceNoise.N;
        boolean unit = true;
        for (int i = 0; i < n; i++) {
            if ((t[i * 4] & 255) > 128) near++;
            if ((t[i * 4] & 255) > 200) {
                double x = (t[i * 4 + 1] & 255) / 127.5 - 1, y = (t[i * 4 + 2] & 255) / 127.5 - 1, z = (t[i * 4 + 3] & 255) / 127.5 - 1;
                unit &= Math.abs(Math.sqrt(x * x + y * y + z * z) - 1) < 0.05;
            }
        }
        double share = near / (double) n;
        check(share > 0.02 && share < 0.3, String.format("Rissflächen: %.0f %% der Zellen nah an einer Fläche", share * 100));
        check(unit, "Normalen der Rissflächen sind Einheitsvektoren");
        // der Sprung über den Rand (letzte zur ersten Schicht) ist im Mittel nicht größer als zwischen zwei Schichten innen
        double seam = 0, inner = 0, q = 1.0 / IceNoise.N;
        for (int i = 0; i < IceNoise.N; i++) for (int j = 0; j < IceNoise.N; j++) {
            double y = (i + 0.5) * q, z = (j + 0.5) * q;
            seam += Math.abs(IceNoise.sample(t, 0.5 * q, y, z)[0] - IceNoise.sample(t, 1 - 0.5 * q, y, z)[0]);
            inner += Math.abs(IceNoise.sample(t, 31.5 * q, y, z)[0] - IceNoise.sample(t, 32.5 * q, y, z)[0]);
        }
        check(seam < 1.5 * inner + 1 && Arrays.equals(IceNoise.sample(t, 0.1, 0.2, 0.3), IceNoise.sample(t, 1.1, -0.8, 2.3)), "Kachel nahtlos (wiederholt sich, keine harte Kante am Rand)");
        check(!Arrays.equals(IceNoise.cracks(2), t), "anderer seed, andere Risse");

        System.out.println("Shader");
        String common = IceGl.source("ice_common.glsl"), frag = IceGl.source("ice.frag");
        check(common.contains("void km(") && common.contains("CRUST_SIGMA = " + (int) IceOptics.CRUST_SIGMA + ".0"), "km() und CRUST_SIGMA in GLSL wie in Java");
        check(common.contains("N_ICE = " + IceOptics.N) && common.contains("F0_ICE = 0.0181"), "Brechzahl und F0 in GLSL wie in Java");
        check(frag.contains("void km(") && !frag.contains("#include"), "ice.frag mit eingesetztem ice_common.glsl");
        for (String u : new String[]{"uAlpha", "uSigma", "uBack", "uCrack", "uMat", "uCrust", "uWater"})
            check(frag.contains("uniform") && frag.contains(u), "ice.frag hat " + u);

        gletscher();

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " Verstöße.");
        System.exit(bad == 0 ? 0 : 1);
    }

    /** Phase 5: der Gletscher im ausgedachten Tal der Demo. */
    static void gletscher() {
        System.out.println("Gletscher");
        long t0 = System.nanoTime();
        com.dan.ice.Glacier g = new com.dan.ice.Glacier(GlacierValley.grid(), 0, 60, 1.5, 3);
        double ms = (System.nanoTime() - t0) / 1e6;
        check(g.areaKm2() > 6 && g.areaKm2() < 10 && ms < 5000, String.format("Zunge %.1f km², gerechnet in %.0f ms", g.areaKm2(), ms));
        check(g.frontLines().size() == 1 && g.frontLength() > 900 && g.frontLength() < 1600, String.format("eine Frontlinie, %.0f m lang", g.frontLength()));
        double top = g.frontTop();
        check(top > 40 && top < 75, String.format("Front steht im Mittel %.0f m über dem Wasser (Ziel 60, mit Spalten und Türmen)", top));
        double cz = GlacierValley.center(-1500);
        double[] vc = g.velocity(-1500, cz), vm = g.velocity(-1500, cz + GlacierValley.halfWidth(-1500) - 40);
        check(vc[0] > 0.5 && Math.hypot(vc[0], vc[1]) > 3 * Math.hypot(vm[0], vm[1]), String.format("fließt talab (+x), Mitte %.2f m/Tag, Rand %.2f m/Tag", Math.hypot(vc[0], vc[1]), Math.hypot(vm[0], vm[1])));
        double fz = GlacierValley.center(0);
        double vFront = Math.hypot(g.velocity(-150, fz)[0], g.velocity(-150, fz)[1]);
        check(vFront > Math.hypot(vc[0], vc[1]), String.format("zur Front schneller (%.2f m/Tag)", vFront));
        check(g.openness(-3020, GlacierValley.center(-3020)) > 0.7 && g.openness(-1500, cz) < g.openness(-3020, GlacierValley.center(-3020)), String.format("Eisfall zerrissen (Öffnung %.2f), Zunge ruhiger (%.2f)",
                g.openness(-3020, GlacierValley.center(-3020)), g.openness(-1500, cz)));
        check(g.openness(-80, fz) > 0.8 && g.seracs(-80, fz) > 0.6, "die letzten Meter vor der Front: Seracs");
        // Randspalten: im Scherband schräg zum Fließen (zwischen 20° und 70°)
        double zm = cz + GlacierValley.halfWidth(-1500) - 120;
        double[] st = g.strain(-1500, zm);
        double[] vv = g.velocity(-1500, zm);
        double flow = Math.toDegrees(Math.atan2(vv[1], vv[0])), rel = Math.abs(((st[2] - flow) % 180 + 270) % 180 - 90);
        check(rel > 20 && rel < 70, String.format("am Rand: größte Dehnung %.0f° schräg zum Fließen (Randspalten)", rel));
        double x0 = -1500;
        check(g.covers(x0, cz) && !g.covers(600, fz) && !g.covers(x0, cz + 1200), "Eis im Tal, nicht im See, nicht auf der Flanke");
        double maxCarve = 0;
        for (int k = 0; k < 4000; k++) {
            double x = -2500 + k * 0.6, z = cz + 30;
            maxCarve = Math.max(maxCarve, g.base(x, z) - g.surface(x, z));
        }
        check(maxCarve > 5 && maxCarve < 40, String.format("Spalten bis %.0f m tief", maxCarve));
        check(g.surface(-1200.3, cz + 7.7) == new com.dan.ice.Glacier(GlacierValley.grid(), 0, 60, 1.5, 3).surface(-1200.3, cz + 7.7), "gleicher seed, gleicher Gletscher");
        // Vorstoß
        double[] fp = g.nearestFront(160, fz, 300);
        check(fp != null, "Frontlinie gefunden");
        if (fp != null) {
            double px = fp[0] + fp[2] * 60, pz = fp[1] + fp[3] * 60;
            boolean before = g.covers(px, pz);
            g.setAdvance(100);
            boolean after = g.covers(px, pz);
            g.setAdvance(-100);
            boolean back = g.covers(fp[0] - fp[2] * 50, fp[1] - fp[3] * 50);
            g.setAdvance(0);
            check(!before && after && !back, "Vorstoß 100 m deckt 60 m vor der Front, Rückzug 100 m gibt 50 m dahinter frei");
        }
        // Kacheln
        com.dan.ice.GlacierTiles tl = new com.dan.ice.GlacierTiles(g);
        com.dan.ice.GlacierTiles.Tile near = null;
        for (com.dan.ice.GlacierTiles.Tile t : tl.tiles) if (t.x0 <= 100 && t.x0 + com.dan.ice.GlacierTiles.TILE > 100 && t.z0 <= fz && t.z0 + com.dan.ice.GlacierTiles.TILE > fz) near = t;
        check(tl.tiles.size() > 80 && near != null, tl.tiles.size() + " Kacheln, eine an der Front");
        if (near != null) {
            t0 = System.nanoTime();
            IceMesh m = tl.build(near, 0);
            double tms = (System.nanoTime() - t0) / 1e6;
            int up = 0, onLine = 0, out = 0, nUsed = 0;
            boolean[] used = new boolean[m.pos.length / 3];
            for (int i : m.idx) used[i] = true;
            for (int v = 0; v < m.pos.length / 3; v++) {
                if (!used[v]) continue;
                nUsed++;
                if (m.nrm[v * 3 + 1] > 0) up++;
                double x = m.pos[v * 3], z = m.pos[v * 3 + 2];
                double[] q = g.nearestFront(x, z, 20);
                if (q != null && Math.hypot(x - q[0], z - q[1]) < 0.01) onLine++;
                if (q != null && (x - q[0]) * q[2] + (z - q[1]) * q[3] > 0.5) out++;
            }
            check(m.triangles() > 5000 && up > nUsed * 0.8, String.format("Kachel 2 m: %,d Dreiecke in %.0f ms, Normalen nach oben", m.triangles(), tms));
            check(onLine > 20 && out == 0, onLine + " Punkte auf der Frontlinie, keiner davor im Wasser");
        }
        // Wand
        IceMesh w = com.dan.ice.GlacierWall.build(g);
        double[] f0 = g.frontLines().get(0), n0 = g.frontNormals().get(0);
        int c = f0.length / 4, rows = w.pos.length / 3 / (f0.length / 2);
        double minR = 1e9, notchR = 0, topDiff = 0;
        for (int r = 1; r < rows; r++) {
            int k = c * rows + r;
            double dx = w.pos[k * 3] - f0[c * 2], dz = w.pos[k * 3 + 2] - f0[c * 2 + 1], y = w.pos[k * 3 + 1];
            double rec = -(dx * n0[c * 2] + dz * n0[c * 2 + 1]);
            if (Math.abs(y - com.dan.ice.GlacierWall.NOTCH_Y) < 0.8) notchR = Math.max(notchR, rec);
            if (y > 10 && y < 30) minR = Math.min(minR, rec);
            if (r == 1) topDiff = Math.abs(y - g.surface(f0[c * 2] - n0[c * 2] * 0.5, f0[c * 2 + 1] - n0[c * 2 + 1] * 0.5));
        }
        check(w.triangles() > 5000 && rows == 39, String.format("Wand: %,d Dreiecke, %d Reihen je Säule", w.triangles(), rows));
        check(notchR > minR + 1.5, String.format("Schmelzkerbe an der Wasserlinie %.1f m tiefer als die Wand darüber", notchR - minR));
        check(topDiff < 0.01, "Oberkante der Wand = Oberfläche an der Front (kein Spalt zu den Kacheln)");
        int toWater = 0, tot = 0;
        for (int k = 0; k < w.pos.length / 3; k += 7) {
            double y = w.pos[k * 3 + 1];
            if (y < 5 || y > 40) continue;
            int col = k / rows;
            if (col >= f0.length / 2) continue;
            tot++;
            if (w.nrm[k * 3] * n0[col * 2] + w.nrm[k * 3 + 2] * n0[col * 2 + 1] > 0) toWater++;
        }
        check(toWater > tot * 0.85, String.format("Wand zeigt zum Wasser (%.0f %% der Normalen)", 100.0 * toWater / Math.max(1, tot)));
        // Rinnen
        double[] path = g.downhill(-2200, cz - 150, 4, 1500);
        boolean down = g.base(path[path.length - 2], path[path.length - 1]) < g.base(path[0], path[1]) - 5;
        check(path.length / 2 > 50 && down, String.format("Schmelzwasser fließt bergab: %.0f m Weg, %.0f m tiefer", path.length / 2 * 4.0, g.base(path[0], path[1]) - g.base(path[path.length - 2], path[path.length - 1])));
        double mx = path[path.length / 4 * 2], mz = path[path.length / 4 * 2 + 1];
        double s0 = g.surface(mx, mz);
        g.addChannel(path, 2, 1.5);
        check(s0 - g.surface(mx, mz) > 1.2, "die Rinne schneidet ihr Bett (1,5 m)");
    }

    /** Jede Dreiecksnormale zeigt vom Schwerpunkt weg (bei den konvexen Formen genügt das). */
    private static boolean outward(IceMesh m) {
        double[] c = m.centroid();
        int wrong = 0;
        for (int t = 0; t < m.idx.length; t += 3) {
            int a = m.idx[t] * 3;
            double px = (m.pos[a] + m.pos[m.idx[t + 1] * 3] + m.pos[m.idx[t + 2] * 3]) / 3 - c[0];
            double py = (m.pos[a + 1] + m.pos[m.idx[t + 1] * 3 + 1] + m.pos[m.idx[t + 2] * 3 + 1]) / 3 - c[1];
            double pz = (m.pos[a + 2] + m.pos[m.idx[t + 1] * 3 + 2] + m.pos[m.idx[t + 2] * 3 + 2]) / 3 - c[2];
            if (px * m.nrm[a] + py * m.nrm[a + 1] + pz * m.nrm[a + 2] <= 0) wrong++;
        }
        return wrong <= m.triangles() / 50;
    }

    static void check(boolean c, String what) {
        if (!c) bad++;
        System.out.println((c ? "  ok    " : "  FEHLER ") + what);
    }

    private IceCheck() { }
}
