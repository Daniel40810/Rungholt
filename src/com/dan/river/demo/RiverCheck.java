package com.dan.river.demo;

import com.dan.river.Drift;
import com.dan.river.FlowField;
import com.dan.river.RiverPath;
import com.dan.river.Rock;
import com.dan.river.WaterOptics;
import com.dan.river.WaterSurface;

/**
 * Selbsttest des Fluss-Pakets ohne Fenster: Lauf, Strömung (Profil, Kurve, Gefälle, Abfluss, Steine),
 * Oberfläche (stetig, bewegt), Treibgut, Optik und Rechenzeit. Endet mit „Alles in Ordnung.“ oder
 * der Zahl der Fehler.
 */
public final class RiverCheck {
    private static int bad, all;

    public static void main(String[] a) {
        // ein gerader und ein gebogener Lauf, 12 m breit, gleichmäßiges Gefälle
        RiverPath straight = new RiverPath(new double[][]{{0, 0, 5, 6}, {0, 100, 4.5, 6}, {0, 200, 4, 6}}, 2, 20);
        check("Lauf: Länge etwa 200 m", Math.abs(straight.length - 200) < 1, straight.length + " m");
        RiverPath.Loc L = new RiverPath.Loc();
        check("Lauf: Mitte gefunden, Querlage 0", straight.locate(0, 50, L) && Math.abs(L.n) < 1e-3 && Math.abs(L.s - 50) < 0.5, "n = " + L.n + ", s = " + L.s);
        straight.locate(-3, 50, L);
        float nLeft = L.n;
        straight.locate(3, 50, L);
        check("Lauf: Seiten mit Vorzeichen", Math.signum(nLeft) == -Math.signum(L.n) && Math.abs(Math.abs(L.n) - 3) < 1e-3, nLeft + " / " + L.n);
        check("Lauf: weit weg kein Treffer", !straight.locate(80, 50, L), "");
        check("Lauf: Gefälle 0,5 %", straight.locate(0, 100, L) && Math.abs(L.slope - 0.005) < 0.001, "" + L.slope);

        FlowField fs = new FlowField(straight);
        FlowField.Flow f = new FlowField.Flow();
        fs.sample(0, 100, f);
        float vMid = f.speed, dMid = f.depth, lvl = f.level;
        fs.sample(5.4, 100, f);
        check("Strömung: Mitte schneller als am Ufer", vMid > 1.5f * f.speed && f.speed > 0, vMid + " / " + f.speed + " m/s");
        check("Strömung: Mitte tiefer als am Ufer", dMid > f.depth, dMid + " / " + f.depth + " m");
        check("Strömung: plausible Geschwindigkeit (0,3–3 m/s)", vMid > 0.3f && vMid < 3, vMid + " m/s");
        fs.sample(0, 100, f);
        check("Strömung: fließt in Richtung des Laufs", f.vz > 0 && Math.abs(f.vx) < 0.05f * f.vz, f.vx + ", " + f.vz);
        fs.sample(7, 100, f);
        check("Strömung: außerhalb des Ufers trocken", !f.wet && f.speed == 0, "");
        fs.discharge = 2.5f;
        fs.sample(0, 100, f);
        check("Abfluss: Hochwasser schneller und höher", f.speed > vMid * 1.15f && f.level > lvl + 0.1f, f.speed + " m/s, Spiegel +" + (f.level - lvl) + " m");
        fs.discharge = 1;

        // Kurve: Viertelkreis mit Radius 40 m
        double[][] arc = new double[13][];
        for (int i = 0; i <= 12; i++) {
            double an = Math.PI / 2 * i / 12;
            arc[i] = new double[]{40 - 40 * Math.cos(an), 40 * Math.sin(an), 5 - 0.01 * i, 6};
        }
        RiverPath bend = new RiverPath(arc, 2, 20);
        FlowField fb = new FlowField(bend);
        float[] c = new float[6];
        bend.at(bend.length / 2, c);
        double nx = -c[5], nz = c[4];
        fb.sample(c[0] + nx * 4, c[1] + nz * 4, f);
        float vA = f.speed, dA = f.depth;
        fb.sample(c[0] - nx * 4, c[1] - nz * 4, f);
        float vB = f.speed, dB = f.depth;
        // außen ist die Seite, von der der Fluss wegbiegt
        boolean aOuter = bend.locate(c[0] + nx * 4, c[1] + nz * 4, L) && L.curv * L.n < 0;
        float vOut = aOuter ? vA : vB, vIn = aOuter ? vB : vA, dOut = aOuter ? dA : dB, dIn = aOuter ? dB : dA;
        check("Kurve: außen schneller", vOut > vIn * 1.05f, vOut + " / " + vIn + " m/s");
        check("Kurve: außen tiefer (Kolk), innen flach", dOut > dIn * 1.1f, dOut + " / " + dIn + " m");

        // Stein mitten im geraden Lauf
        FlowField fr = new FlowField(straight);
        fr.add(new Rock(0, 100, 1, 0.4));
        fr.sample(0, 100, f);
        check("Stein: im Stein kein Wasser", f.solid && !f.wet, "");
        fs.sample(0, 102.5, f);
        float free = f.vz;
        fr.sample(0, 102.5, f);
        check("Stein: dahinter Kehrwasser (zurück oder fast still)", f.vz < 0.25f * free && f.eddy > 0.3f, f.vz + " statt " + free + " m/s, Kehrwasser " + f.eddy);
        fs.sample(1.4, 100, f);
        float freeSide = f.speed;
        fr.sample(1.4, 100, f);
        check("Stein: daneben schneller (das Wasser drängt vorbei)", f.speed > freeSide * 1.15f, f.speed + " / " + freeSide + " m/s");
        fr.sample(0, 102.5, f);
        float foamBehind = f.foam;
        fr.sample(0, 90, f);
        check("Stein: Schaum dahinter, nicht weit oberhalb", foamBehind > 0.2f && f.foam < 0.1f, foamBehind + " / " + f.foam);
        check("Stein: Unruhe dahinter", foamBehind > 0 && fr.sample(0, 102.5, f) && f.turb > 0.3f, "" + f.turb);

        // Oberfläche: stetig über die Phasen, bewegt, Schaum nur wo er entsteht
        WaterSurface ws = new WaterSurface();
        WaterSurface.Surf s1 = new WaterSurface.Surf(), s2 = new WaterSurface.Surf();
        fs.sample(1, 100, f);
        float maxJump = 0;
        for (float t = 0; t < 10; t += 0.01f) {
            ws.sample(1, 100, t, f, 0, s1);
            ws.sample(1, 100, t + 0.01f, f, 0, s2);
            maxJump = Math.max(maxJump, Math.abs(s2.height - s1.height));
        }
        check("Oberfläche: kein Sprung von Bild zu Bild", maxJump < 0.01f, maxJump + " m in 1/100 s");
        ws.sample(1, 100, 0, f, 0, s1);
        ws.sample(1, 100, 1.3f, f, 0, s2);
        check("Oberfläche: bewegt sich", Math.abs(s1.height - s2.height) + Math.abs(s1.dhdx - s2.dhdx) > 1e-4f, "");
        float maxSlope = 0;
        for (int i = 0; i < 2000; i++) {
            ws.sample(i * 0.37f % 5 - 2.5f, 60 + i * 0.05f, i * 0.1f, f, 0, s1);
            maxSlope = Math.max(maxSlope, Math.abs(s1.dhdx) + Math.abs(s1.dhdz));
        }
        check("Oberfläche: Neigung im Rahmen (keine Spitzen)", maxSlope < 1.5f, "" + maxSlope);
        fs.sample(0, 60, f);
        float calmFoam = 0;
        for (int i = 0; i < 200; i++) { ws.sample(i * 0.03f, 60, i * 0.1f, f, 0, s1); calmFoam = Math.max(calmFoam, s1.foam); }
        float rockFoam = 0;
        fr.sample(0.3, 102, f);
        for (int i = 0; i < 200; i++) { ws.sample(0.3f, 102, i * 0.1f, f, 0, s1); rockFoam = Math.max(rockFoam, s1.foam); }
        check("Schaum: hinter dem Stein ja, im ruhigen Wasser kaum", rockFoam > 0.5f && calmFoam < 0.2f, rockFoam + " / " + calmFoam);

        // Treibgut
        Drift d = new Drift(500);
        d.foamDensity = 0; d.leafDensity = 3; d.twigDensity = 0;
        for (int k = 0; k < 10; k++) d.step(0.1f, fs, 0, 100, 30);
        int n0 = d.n;
        float s0 = 0;
        for (int i = 0; i < d.n; i++) { straight.locate(d.x[i], d.z[i], L); s0 += L.s; }
        s0 /= Math.max(1, d.n);
        check("Treibgut: taucht auf", n0 > 10, n0 + " Stücke");
        float[] zs = new float[d.n];
        for (int i = 0; i < d.n; i++) zs[i] = d.z[i];
        int m0 = d.n;
        d.leafDensity = 0;
        for (int k = 0; k < 30; k++) d.step(0.1f, fs, 0, 100, 60);
        int moved = 0;
        for (int i = 0; i < Math.min(m0, d.n); i++) if (d.z[i] > zs[i] + 0.3f) moved++;
        check("Treibgut: zieht flussab", moved > Math.min(m0, d.n) / 2, moved + " von " + Math.min(m0, d.n));
        boolean allWet = true;
        for (int i = 0; i < d.n; i++) if (Math.abs(d.x[i]) > 6.01f) allWet = false;
        check("Treibgut: bleibt auf dem Wasser", allWet, "");

        // Optik
        WaterOptics o = new WaterOptics();
        float[] t1 = new float[3], t2 = new float[3];
        o.transmit(0.5f, t1);
        o.transmit(3, t2);
        check("Optik: tieferes Wasser schluckt mehr", t2[0] < t1[0] && t2[1] < t1[1] && t2[2] < t1[2], "");
        check("Optik: Rot verschwindet zuerst", t2[0] < t2[1] && t2[0] < t2[2], t2[0] + " " + t2[1] + " " + t2[2]);
        check("Optik: Fresnel 2 % senkrecht, fast 1 streifend", Math.abs(WaterOptics.fresnel(1) - 0.02f) < 1e-3f && WaterOptics.fresnel(0.02f) > 0.85f, "");
        float cm = 0;
        for (int i = 0; i < 4000; i++) cm += WaterOptics.caustics(i * 0.173f, i * 0.071f, i * 0.01f, 0.5f, 0.5f, 0);
        cm /= 4000;
        check("Optik: Kaustik im Mittel um 1", cm > 0.7f && cm < 1.5f, "" + cm);

        // Rechenzeit einer Abfrage (Strömung und Oberfläche)
        long t0 = System.nanoTime();
        int N = 400_000;
        float acc = 0;
        for (int i = 0; i < N; i++) {
            fr.sample((i % 97) * 0.12 - 5.8, (i % 1009) * 0.19, f);
            ws.sample((i % 97) * 0.12f - 5.8f, (i % 1009) * 0.19f, i * 0.001f, f, 0, s1);
            acc += s1.height;
        }
        double us = (System.nanoTime() - t0) / 1e3 / N;
        check("Rechenzeit je Pixel unter 3 µs", us < 3, String.format(java.util.Locale.GERMANY, "%.2f µs", us) + (acc == 42 ? "" : ""));

        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + all + " Prüfungen fehlgeschlagen.");
        if (bad > 0) System.exit(1);
    }

    private static void check(String what, boolean ok, String detail) {
        all++;
        if (!ok) bad++;
        System.out.printf("  %-4s  %s%s%n", ok ? "ok" : "FEHL", what, detail.isEmpty() ? "" : "  (" + detail + ")");
    }
}
