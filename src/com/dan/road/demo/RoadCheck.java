package com.dan.road.demo;

import com.dan.road.Ground;
import com.dan.road.Mover;
import com.dan.road.Network;
import com.dan.road.Roads;
import com.dan.road.Traffic;
import com.dan.road.Vehicle;
import com.dan.road.Way;
import com.dan.road.WayType;

/**
 * Selbsttest des Wege-Pakets ohne Fenster: Trassierung (Steigung, Brücken über den Bach, Querneigung),
 * Abfragen und Formung des Geländes, Geometrie in drei Stufen, Verkehr (keine Auffahrunfälle,
 * Kurvengeschwindigkeit, Spurwechsel, Stau vor Hindernissen), Wetter (nass, abtrocknen, Pfützen),
 * Licht, Staub und Rechenzeit.
 */
public final class RoadCheck {
    private static int bad, all;

    public static void main(String[] a) {
        Network net = Landscape.network();
        long b0 = System.nanoTime();
        net.build(Landscape.BASE);
        double msNet = (System.nanoTime() - b0) / 1e6;
        Way mw = find(net, WayType.Kind.MOTORWAY), rd = find(net, WayType.Kind.ROAD), tk = find(net, WayType.Kind.TRACK), pt = find(net, WayType.Kind.PATH);

        // Trassierung
        for (Way w : net.ways) {
            float worst = 0;
            for (int i = 0; i + 1 < w.n; i++) worst = Math.max(worst, Math.abs(w.y[i + 1] - w.y[i]) / w.step());
            check(w.type.name + ": Steigung höchstens " + Math.round(w.type.maxGrade * 100) + " %", worst <= w.type.maxGrade * 1.02f, String.format("%.1f %%", worst * 100));
        }
        int brM = spans(mw), brR = spans(rd);
        check("Autobahn und Straße überqueren den Bach auf Brücken", brM >= 2 && brR >= 2, brM + " / " + brR + " Brücken");
        boolean clear = true;
        for (Way w : new Way[]{mw, rd})
            for (int i = 0; i < w.n; i++) {
                float wl = Landscape.BASE.water(w.x[i], w.z[i]);
                if (wl == wl && w.y[i] < wl + w.type.clearance - 0.01f) clear = false;
            }
        check("Brücken halten die lichte Höhe über dem Wasser", clear, "");
        float maxBank = 0;
        for (int i = 0; i < mw.n; i++) maxBank = Math.max(maxBank, Math.abs(mw.bank[i]));
        check("Querneigung in Kurven, höchstens wie eingestellt", maxBank > 0.005f && maxBank <= mw.type.maxBank + 1e-4f, String.format("%.1f %%", maxBank * 100));
        // Außen liegt höher: in einer Rechtskurve fällt die Fahrbahn nach rechts
        int iK = 0;
        for (int i = 0; i < rd.n; i++) if (Math.abs(rd.curv[i]) > Math.abs(rd.curv[iK])) iK = i;
        float[] f = new float[6];
        rd.at(iK * rd.step(), 3, f);
        float yr = f[1];
        rd.at(iK * rd.step(), -3, f);
        float yl = f[1];
        check("Kurve: die Außenseite liegt höher", Math.signum(rd.curv[iK]) > 0 ? yl > yr : yr > yl, String.format("links %.2f, rechts %.2f", yl, yr));

        // Abfragen
        Network.Hit h = new Network.Hit();
        rd.at(300, 0, f);
        float cx = f[0], cz = f[2], dx = -f[4], dz = f[3];
        boolean ok = net.locate(cx + dx * 2.5f, cz + dz * 2.5f, h) && h.way == rd && Math.abs(h.u - 2.5) < 0.05 && Math.abs(h.s - 300) < 0.5;
        check("Abfrage: Weg, Bogenlänge und Querlage (rechts positiv)", ok, String.format("s %.2f, u %.2f", h.s, h.u));
        check("Abfrage: auf der Fahrbahn befestigt, weit daneben nicht", net.cover(cx, cz) > 0.99f && net.cover(cx + dx * 40, cz + dz * 40) < 0.01f, "");
        double g0 = net.shape(cx, cz, Landscape.BASE.height(cx, cz));
        rd.at(300, 0, f);
        check("Gelände unter der Fahrbahn knapp darunter", g0 < f[1] && g0 > f[1] - 0.4, String.format("%.2f unter der Fahrbahn", f[1] - g0));
        double far = net.shape(cx + dx * 200, cz + dz * 200, 5.0);
        check("Gelände weit weg unverändert", far == 5.0 || !net.locate(cx + dx * 200, cz + dz * 200, h), "");

        // Geometrie
        Ground shaped = net.shaped(Landscape.BASE);
        Roads roads = new Roads(net, shaped, 3);
        roads.update(0, 0.01f, cx, f[1] + 2, cz);
        int nNear = roads.batch.nt;
        roads.update(0.02f, 0.01f, cx, f[1] + 600, cz);
        int nFar = roads.batch.nt;
        check("Detailstufen: nah feiner als von hoch oben", nNear > nFar && nFar > 0, nNear + " / " + nFar + " Dreiecke");

        // Verkehr: 4 Minuten fahren
        Traffic tr = roads.traffic;
        int crashes = 0;
        String worstM = "";
        float curveExcess = 0;
        for (int k = 0; k < 2400; k++) {
            tr.step(0.1f);
            if (k % 20 == 0) {
                crashes += overlaps(tr);
                for (Mover m : tr.movers) {
                    if (m.kind.walks() || m.kind.pedals()) continue;
                    Way w = m.way();
                    int i = Math.min(w.n - 1, Math.round(m.s() / w.step()));
                    float lat = m.v * m.v * Math.abs(w.curv[i]);
                    float ex = lat - (w.type.lateralAccel + 9.81f * Math.abs(w.bank[i])) * 1.25f;
                    if (ex > curveExcess) { curveExcess = ex; worstM = m.kind + " " + w.type.name + String.format(" v %.1f vc %.1f k %.4f", m.v, w.vc[i], w.curv[i]); }
                }
            }
        }
        check("Verkehr: keine Auffahrunfälle", crashes == 0, crashes + " Überlappungen");
        check("Verkehr: Kurven nicht schneller als erlaubt", curveExcess <= 0.2f, String.format("%.2f m/s² darüber %s", Math.max(0, curveExcess), worstM));
        check("Verkehr: Spurwechsel auf der Autobahn", tr.laneChanges > 5, tr.laneChanges + " Wechsel");
        int trucksRight = 0, trucks = 0;
        for (Mover m : tr.movers) if (m.kind == Vehicle.TRUCK) { trucks++; if (m.u * m.dir() > mw.type.laneCenter(0) - 0.8f && m.u * m.dir() < mw.type.laneCenter(0) + 0.8f) trucksRight++; }
        check("Verkehr: Lastwagen meist rechts", trucks > 0 && trucksRight >= trucks * 0.7f, trucksRight + " von " + trucks);
        float vMean = 0;
        int nm = 0;
        for (Mover m : tr.movers) if (m.way() == mw && !m.kind.walks()) { vMean += m.v; nm++; }
        vMean /= Math.max(1, nm);
        check("Verkehr: Autobahn fließt", vMean > 20, String.format("%.0f km/h im Mittel", vMean * 3.6f));
        int hikers = 0;
        for (Mover m : tr.movers) if (m.way() == pt && m.kind == Vehicle.HIKER) hikers++;
        check("Wanderer auf dem Pfad", hikers > 0, hikers + "");

        // Stau vor einem Hindernis auf der Straße
        rd.at(rd.length * 0.5f, rd.type.laneCenter(0), f);
        float[] obs = {f[0], f[2], 1.5f};
        for (int k = 0; k < 1200; k++) { tr.obstacles(obs, 1); tr.step(0.1f); }
        int past = 0, waiting = 0;
        boolean hazard = false;
        for (Mover m : tr.movers) {
            if (m.way() != rd || m.dir() < 0 || m.kind.pedals()) continue;
            float d = m.s() - rd.length * 0.5f;
            if (d > -1.5f && d < 20) past++;
            if (d < -1.5f && d > -150 && m.v < 0.3f) waiting++;
            hazard |= m.hazard;
        }
        check("Hindernis: niemand fährt hindurch, dahinter staut es sich", past == 0 && waiting >= 3, waiting + " warten, " + past + " durch");
        check("Hindernis: das erste Fahrzeug blinkt warnend", hazard, "");
        tr.obstacles(obs, 0);
        for (int k = 0; k < 600; k++) tr.step(0.1f);
        int moving = 0;
        for (Mover m : tr.movers) if (m.way() == rd && m.dir() > 0 && m.v > 5) moving++;
        check("Hindernis weg: der Verkehr fährt wieder", moving >= 3, moving + " fahren");

        // Wetter
        Roads r2 = new Roads(net, shaped, 4);
        r2.weather.rain = 1;
        for (int k = 0; k < 600; k++) r2.weather.step(1);
        float wet = r2.weather.wet, pud = r2.weather.puddles;
        r2.weather.rain = 0;
        for (int k = 0; k < 600; k++) r2.weather.step(1);
        check("Wetter: im Regen nass, Pfützen voll", wet > 0.95f && pud > 0.5f, String.format("nass %.2f, Pfützen %.2f", wet, pud));
        check("Wetter: danach trocknen die Radspuren schneller, Pfützen bleiben länger",
                r2.weather.wetTracks < r2.weather.wet * 0.8f && r2.weather.puddles / pud > r2.weather.wet / wet,
                String.format("Fläche %.2f, Spuren %.2f, Pfützen %.2f", r2.weather.wet, r2.weather.wetTracks, r2.weather.puddles));

        // Licht
        r2.weather.dark = 1;
        rd.at(500, 0, f);
        r2.update(1, 0.05f, f[0], f[1] + 3, f[2]);
        check("Nachts: Scheinwerfer an", r2.lightCount > 10, r2.lightCount + " Kegel");
        float[] L = r2.lights;
        float[] e1 = new float[3], e2 = new float[3];
        r2.illuminate(L[0] + L[3] * 15, L[1] - 0.7f, L[2] + L[5] * 15, 0, 1, 0, e1);
        r2.illuminate(L[0] - L[3] * 15, L[1] - 0.7f, L[2] - L[5] * 15, 0, 1, 0, e2);
        check("Scheinwerfer leuchten nach vorn, nicht nach hinten", e1[0] > 0.02f && e2[0] < e1[0] * 0.2f, String.format("%.2f / %.2f", e1[0], e2[0]));

        // Staub auf dem trockenen Feldweg, nicht im Nassen
        Roads r3 = new Roads(net, shaped, 5);
        r3.traffic.flow[tk.index] = 200;
        r3.traffic.rates();
        r3.traffic.populate();
        for (int k = 0; k < 60; k++) r3.update(k * 0.1f, 0.1f, 390, 10, 0);
        int dry = r3.particles(0);
        r3.weather.rain = 1;
        r3.weather.settle();
        for (int k = 0; k < 150; k++) r3.update(6 + k * 0.1f, 0.1f, 390, 10, 0);
        check("Staub hinter Fahrzeugen auf dem trockenen Feldweg, im Nassen keiner", dry > 20 && r3.particles(0) < dry / 3, dry + " / " + r3.particles(0) + " Teilchen");
        check("Gischt hinter schnellen Fahrzeugen auf nasser Fahrbahn", r3.particles(1) > 20, r3.particles(1) + " Teilchen");

        // Rechenzeit
        long t0 = System.nanoTime();
        for (int k = 0; k < 20; k++) roads.update(100 + k * 0.05f, 0.05f, cx, f[1] + 2, cz);
        double ms = (System.nanoTime() - t0) / 20e6;
        check("Rechenzeit je Bild unter 40 ms", ms < 40, String.format("%.1f ms (Netz bauen %.0f ms)", ms, msNet));

        System.out.println();
        System.out.println(bad == 0 ? "Alles in Ordnung." : bad + " von " + all + " Prüfungen fehlgeschlagen.");
        if (bad > 0) System.exit(1);
    }

    private static Way find(Network n, WayType.Kind k) { for (Way w : n.ways) if (w.type.kind == k) return w; return null; }

    private static int spans(Way w) {
        int c = 0;
        for (int i = 0; i < w.n; i++) if (w.bridge[i] && !w.bridge[(i + w.n - 1) % w.n]) c++;
        return c;
    }

    /** Fahrzeuge desselben Streifens, die sich überlappen. */
    private static int overlaps(Traffic tr) {
        int c = 0;
        for (Traffic.Lane l : tr.lanes) {
            java.util.List<Mover> ms = new java.util.ArrayList<>();
            for (Mover m : tr.movers) if (m.u == m.u && laneOf(tr, m) == l) ms.add(m);
            ms.sort((x, y) -> Float.compare(x.p, y.p));
            for (int i = 0; i + 1 < ms.size(); i++) {
                Mover x = ms.get(i), y = ms.get(i + 1);
                if (y.p - x.p < (x.kind.length + y.kind.length) * 0.5f - 0.3f) c++;
            }
        }
        return c;
    }

    private static Traffic.Lane laneOf(Traffic tr, Mover m) {
        for (Traffic.Lane l : tr.lanes) if (l.way == m.way() && l.dir == m.dir() && Math.abs(l.u - m.u) < 0.01f) return l;
        return null;
    }

    private static void check(String what, boolean ok, String detail) {
        all++;
        if (!ok) bad++;
        System.out.printf("%s  %s%s%n", ok ? "ok    " : "FEHLER", what, detail.isEmpty() ? "" : "  (" + detail + ")");
    }
}
