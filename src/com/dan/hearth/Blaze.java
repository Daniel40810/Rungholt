package com.dan.hearth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Ein Brand, der sich über Häuser ausbreitet. Jedes Haus hat drei Brandlasten: das Dach (Reet oder Bretter), die
 * Wände, das Gerüst (Pfosten, Rähm). Jede Last brennt an, wächst zum Höchstwert ihrer Wärmefreisetzung und brennt
 * ab, bis ihr Brennstoff verbraucht ist. Das Dach zündet die Wände, die Wände das Gerüst; ist das Gerüst geschwächt
 * oder das Dach durchgebrannt, stürzt das Dach ein: was von ihm übrig ist, fällt ins Haus und brennt dort mit einem
 * Schub nieder. Danach glimmt die Glut.
 * <p>
 * Zwischen Häusern: Wärmestrahlung q = Σ 0,3 · HRR / (4π d²), mit dem Wind verstärkt nach Lee; ein Reetdach fängt
 * Feuer, wenn es genug Wärme gesammelt hat (300 kJ/m² trocken, nass mehr; Verlust 8 kW/m² an die Luft). Dazu
 * Flugfeuer: brennende Halme fallen mit dem Wind auf Reetdächer in Lee (Zufall mit festem Startwert).
 * <p>
 * Zahlen [G nach Brandversuchen an Reetdächern]: Reet 36 kg/m² zu 15 MJ/kg, bis 300 kW/m² Dachfläche; Wände
 * 20 kg/m² zu 16 MJ/kg; Gerüst 2 t zu 16 MJ/kg, langsam. Ein Wohnhaus von 12 × 5 m erreicht so gut 10 MW nach fünf
 * Minuten, das Dach stürzt nach einer Viertelstunde ein, nach gut einer Stunde ist nur noch Glut übrig.
 */
public final class Blaze {
    /** Ein Haus als Kasten mit Satteldach: Mitte, Boden, First in Grad von Nord, halbe Länge und Breite, Traufe, First, Reet. */
    public record Building(double x, double y, double z, double headingDeg, double halfLength, double halfWidth, double eave, double ridge, boolean reet) {
        /** Dachfläche (m²), beide Seiten, mit 0,6 m Überstand. */
        public double roofArea() {
            double run = halfWidth + 0.6, rise = ridge - eave;
            return 2 * (2 * halfLength + 1.2) * Math.hypot(run, rise);
        }

        public double wallArea() { return 2 * (2 * halfLength + 2 * halfWidth) * eave; }

        /** Punkt im Haus (u längs −1..1, v quer −1..1, h Höhe über dem Boden) in der Welt. */
        public double[] at(double u, double v, double h) {
            double a = Math.toRadians(headingDeg);
            double fx = Math.sin(a), fz = -Math.cos(a), rx = Math.cos(a), rz = Math.sin(a);
            return new double[]{x + fx * u * halfLength + rx * v * halfWidth, y + h, z + fz * u * halfLength + rz * v * halfWidth};
        }
    }

    /** Zustand eines Hauses. */
    public enum Phase { INTAKT, BRENNT, EINGESTUERZT, GLUT, ASCHE }

    public static final int ROOF = 0, WALLS = 1, FRAME = 2;
    static final double REET_KG = 8, REET_MJ = 15, BOARD_KG = 10, WALL_KG = 12, WOOD_MJ = 16, FRAME_KG = 800;
    static final double ROOF_PEAK = 110, WALL_PEAK = 90, FRAME_PEAK = 2500;   // kW/m² Dach, kW/m² Wand, kW gesamt
    static final double IGNITE_KJ = 300, LOSS_KW = 8;

    final List<Building> buildings = new ArrayList<>();
    final double[][] fuel, fuel0, burn;
    final boolean[] lit0, lit1, lit2, collapsed;
    final double[] heat, igniteAt, collapseAt;
    final double[] hrr;
    final double[][] part;
    public volatile double windFromDeg = 90, windSpeed = 4, wetness;
    private final SplittableRandom rnd;
    private double time;
    private double released;
    private final List<String> log = new ArrayList<>();
    /** Wird gerufen, wenn ein Dach einstürzt (Klang, Funkenschauer): Haus, Zeit. */
    public Collapse onCollapse;

    @FunctionalInterface public interface Collapse { void at(int building, double time); }

    public Blaze(List<Building> list, long seed) {
        buildings.addAll(list);
        int n = list.size();
        fuel = new double[n][3];
        fuel0 = new double[n][3];
        burn = new double[n][3];
        lit0 = new boolean[n];
        lit1 = new boolean[n];
        lit2 = new boolean[n];
        collapsed = new boolean[n];
        heat = new double[n];
        igniteAt = new double[n];
        collapseAt = new double[n];
        hrr = new double[n];
        part = new double[n][3];
        for (int i = 0; i < n; i++) {
            Building b = list.get(i);
            fuel0[i][ROOF] = b.roofArea() * (b.reet() ? REET_KG * REET_MJ : BOARD_KG * WOOD_MJ) * 1000;   // kJ
            fuel0[i][WALLS] = b.wallArea() * WALL_KG * WOOD_MJ * 1000;
            fuel0[i][FRAME] = FRAME_KG * (b.halfLength() * b.halfWidth() / 15) * WOOD_MJ * 1000;
            fuel[i] = fuel0[i].clone();
            igniteAt[i] = collapseAt[i] = Double.NaN;
        }
        rnd = new SplittableRandom(seed);
    }

    public int size() { return buildings.size(); }
    public Building building(int i) { return buildings.get(i); }
    public double time() { return time; }

    /** Feuer im Dach von Haus i (Fackel, Flugfeuer von außen, Blitz). */
    public void ignite(int i) {
        if (lit0[i] || phase(i) == Phase.ASCHE) return;
        lit0[i] = true;
        burn[i][ROOF] = Math.max(burn[i][ROOF], 0.02);
        if (Double.isNaN(igniteAt[i])) igniteAt[i] = time;
        log.add(String.format(Locale.GERMAN, "%.0f s: Haus %d brennt", time, i));
    }

    /** Um dt weiter (in Schritten von höchstens 0,5 s). */
    public void step(double dt) {
        int steps = Math.max(1, (int) Math.ceil(dt / 0.5));
        double h = dt / steps;
        for (int s = 0; s < steps; s++) step1(h);
    }

    private void step1(double dt) {
        time += dt;
        int n = buildings.size();
        // 1 jedes Haus für sich
        for (int i = 0; i < n; i++) {
            Building b = buildings.get(i);
            double q = 0;
            boolean[] lit = {lit0[i], lit1[i], lit2[i]};
            double[] peak = {(b.reet() ? ROOF_PEAK : ROOF_PEAK * 0.5) * b.roofArea(), WALL_PEAK * b.wallArea() * 0.5, FRAME_PEAK * b.halfLength() * b.halfWidth() / 15};
            double[] grow = {b.reet() ? 1 / 70.0 : 1 / 150.0, 1 / 120.0, 1 / 300.0};
            if (collapsed[i]) { peak[ROOF] *= 1.6; grow[ROOF] *= 3; }
            for (int p = 0; p < 3; p++) {
                part[i][p] = 0;
                if (!lit[p] || fuel[i][p] <= 0) { if (fuel[i][p] <= 0) burn[i][p] = 0; continue; }
                burn[i][p] += grow[p] * (1 - burn[i][p]) * dt * 4 * (1 - 0.6 * wetness);
                burn[i][p] = Math.min(1, burn[i][p]);
                // gegen Ende fehlt der Brennstoff: die Leistung sinkt mit dem Rest
                double rest = fuel[i][p] / fuel0[i][p];
                double rate = peak[p] * burn[i][p] * Math.min(1, rest * 4);
                double used = Math.min(fuel[i][p], rate * dt);
                fuel[i][p] -= used;
                released += used;
                q += used / dt;
                part[i][p] = used / dt;
            }
            hrr[i] = q;
            // Dach zündet Wände, Wände zünden Gerüst
            if (lit0[i] && !lit1[i] && burn[i][ROOF] > 0.45) lit1[i] = true;
            if (lit1[i] && !lit2[i] && burn[i][WALLS] > 0.35) lit2[i] = true;
            // Einsturz: Gerüst zu einem Drittel verbrannt oder Dach zu drei Vierteln
            if (!collapsed[i] && lit0[i] && (fuel[i][FRAME] < fuel0[i][FRAME] * 0.67 || fuel[i][ROOF] < fuel0[i][ROOF] * 0.25)) {
                collapsed[i] = true;
                collapseAt[i] = time;
                log.add(String.format(Locale.GERMAN, "%.0f s: Dach von Haus %d stürzt ein", time, i));
                if (onCollapse != null) onCollapse.at(i, time);
            }
        }
        // 2 Wärmestrahlung und Flugfeuer zwischen den Häusern
        double wa = Math.toRadians(windFromDeg + 180), wx = Math.sin(wa), wz = -Math.cos(wa);
        double wf = Math.min(1, windSpeed / 6);
        for (int i = 0; i < n; i++) {
            if (lit0[i]) continue;
            Building bi = buildings.get(i);
            double flux = 0;
            for (int j = 0; j < n; j++) {
                if (j == i || hrr[j] <= 0) continue;
                Building bj = buildings.get(j);
                double dx = bi.x() - bj.x(), dz = bi.z() - bj.z(), dc = Math.hypot(dx, dz);
                double d = Math.max(2, dc - 0.7 * (bi.halfWidth() + bj.halfWidth()));
                double cos = dc > 1e-6 ? (dx * wx + dz * wz) / dc : 0;
                double windF = 1 + 2.5 * Math.max(0, cos) * wf - 0.5 * Math.max(0, -cos) * wf;
                double qj = 0.3 * hrr[j] / (4 * Math.PI * d * d) * windF;
                flux += qj;
                // Flugfeuer auf Reet in Lee
                if (bi.reet() && cos > 0.3 && d < 70) {
                    double p = 0.00015 * hrr[j] / 1000 * cos * wf * Math.exp(-d / 22) * (1 - wetness) * dt;
                    if (rnd.nextDouble() < p) { log.add(String.format(Locale.GERMAN, "%.0f s: Flugfeuer von Haus %d auf Haus %d", time, j, i)); ignite(i); }
                }
            }
            double need = IGNITE_KJ * (bi.reet() ? 1 : 2.5) * (1 + 3 * wetness);
            heat[i] = Math.max(0, heat[i] + (flux - LOSS_KW) * dt);
            if (heat[i] >= need && !lit0[i]) ignite(i);
        }
    }

    /** Wärmefreisetzung von Haus i jetzt (kW). */
    public double hrr(int i) { return hrr[i]; }

    /** Summe aller Häuser (kW). */
    public double totalHrr() { double s = 0; for (double v : hrr) s += v; return s; }

    /** Freigesetzte Energie bisher (kJ) und Brennstoff am Anfang (kJ). */
    public double released() { return released; }
    public double fuelTotal() { double s = 0; for (double[] f : fuel0) for (double v : f) s += v; return s; }

    public Phase phase(int i) {
        double left = fuel[i][0] + fuel[i][1] + fuel[i][2], all = fuel0[i][0] + fuel0[i][1] + fuel0[i][2];
        if (!lit0[i]) return Phase.INTAKT;
        if (left < all * 0.02) return Phase.ASCHE;
        if (hrr[i] < 1500 && collapsed[i]) return Phase.GLUT;
        return collapsed[i] ? Phase.EINGESTUERZT : Phase.BRENNT;
    }

    public boolean collapsed(int i) { return collapsed[i]; }
    public double ignitedAt(int i) { return igniteAt[i]; }
    public double collapsedAt(int i) { return collapseAt[i]; }
    /** Wie weit Haus i verkohlt ist 0..1 (Anteil des verbrannten Holzes von Wänden und Gerüst). */
    public double char_(int i) {
        double w = fuel0[i][WALLS] + fuel0[i][FRAME];
        return 1 - (fuel[i][WALLS] + fuel[i][FRAME]) / w;
    }
    /** Wie viel vom Dach noch da ist 0..1. */
    public double roofLeft(int i) { return collapsed[i] ? 0 : fuel[i][ROOF] / fuel0[i][ROOF]; }

    /** Rauch 0..1 und Schwärze 0..1 von Haus i: brennendes Reet und Teer qualmen schwarz, Glut weiß. */
    public double smoke(int i) { return Math.min(1, hrr[i] / 3000); }
    public double blackness(int i) {
        if (hrr[i] <= 0) return 0;
        return Math.min(1, 0.15 + 0.8 * part[i][ROOF] / hrr[i]);
    }

    /** Flammenhöhe von Haus i (m): L = 0,23 · Q^0,4. */
    public double flameHeight(int i) { return Fire.flameHeight(hrr[i]); }

    /**
     * Die Flammen von Haus i als Strahler: solange das Dach steht, auf dem First und den Dachflächen, so weit das Dach
     * brennt, dazu aus Fenstern und Tür, wenn die Wände brennen; nach dem Einsturz über der ganzen Grundfläche.
     */
    public List<Flame> flames(int i) {
        List<Flame> out = new ArrayList<>();
        Building b = buildings.get(i);
        double q = hrr[i];
        if (q <= 0) return out;
        int seed = 9001 + i * 131;
        if (!collapsed[i]) {
            double share = burn[i][ROOF];
            int nu = Math.max(1, (int) Math.round(b.halfLength() / 1.5)), k = 0;
            double per = q / (nu * 3);
            for (int u = 0; u < nu; u++)
                for (int v = -1; v <= 1; v++, k++) {
                    double uu = -0.9 + 1.8 * (u + 0.5) / nu;
                    if (Fire.rnd(seed, k) > share + 0.15) continue;
                    double hh = v == 0 ? b.ridge() : (b.eave() + b.ridge()) / 2;
                    double[] p = b.at(uu, v * 0.5, hh - 0.2);
                    out.add(new Flame(p[0], p[1], p[2], 1.4, Fire.flameHeight(per) * 1.6, Math.min(1, share * 1.5), seed + k, Flame.BLAZE));
                }
        } else {
            int nu = Math.max(2, (int) Math.round(b.halfLength() / 1.8)), k = 0;
            double per = q / (nu * 2);
            for (int u = 0; u < nu; u++)
                for (int v = -1; v <= 1; v += 2, k++) {
                    double[] p = b.at(-0.85 + 1.7 * (u + 0.5) / nu, v * 0.45, 0.3);
                    boolean glow = phase(i) == Phase.GLUT;
                    out.add(new Flame(p[0], p[1], p[2], glow ? 2.0 : 1.6, glow ? 0.4 : Fire.flameHeight(per) * 1.4, glow ? 0.5 : 1, seed + 50 + k, glow ? Flame.EMBER : Flame.BLAZE));
                }
        }
        return out;
    }

    /** Licht von Haus i: Ort (Mitte in Traufhöhe) und Lichtstärke RGB (cd); null, wenn es nicht brennt. */
    public double[][] light(int i, double t) {
        if (hrr[i] <= 0) return null;
        Building b = buildings.get(i);
        double[] p = b.at(0, 0, collapsed[i] ? 1.5 : b.eave() + 1);
        double f = 1 + 0.12 * Math.sin(t * 7.3 + i) + 0.08 * Math.sin(t * 13.1 + 2 * i);
        double cd = Fire.lumenOf(hrr[i]) / (4 * Math.PI) * f;
        return new double[][]{p, {Fire.FLAME_RGB[0] * cd, Fire.FLAME_RGB[1] * cd, Fire.FLAME_RGB[2] * cd}};
    }

    /** Was geschah (Zündungen, Flugfeuer, Einstürze). */
    public List<String> log() { return new ArrayList<>(log); }

    /** „3 von 8 Häusern brennen, 1 eingestürzt · 18,4 MW · 22 min“ */
    public String describe() {
        int b = 0, c = 0, a = 0;
        for (int i = 0; i < size(); i++) {
            Phase p = phase(i);
            if (p == Phase.BRENNT) b++;
            if (p == Phase.EINGESTUERZT || p == Phase.GLUT) c++;
            if (p == Phase.ASCHE) a++;
        }
        return String.format(Locale.GERMAN, "%d von %d Häusern brennen, %d eingestürzt, %d Asche · %.1f MW · %.0f min", b, size(), c, a, totalHrr() / 1000, time / 60);
    }

    // ------------------------------------------------------------------ Zustand als Text (seit Haithabu Phase 16)

    /**
     * Der ganze Zustand des Brands als eine Zeile Text (Zeit, freigesetzte Energie, je Haus Brennstoff, Brand der
     * Teile, gezündete Teile, Wärme, Zeiten, Leistung); {@link #setState} stellt ihn auf derselben Liste von Häusern
     * wieder her. Der Zufall läuft danach neu. So kann eine App einen Brand mitten im Lauf sichern.
     */
    public String state() {
        StringBuilder b = new StringBuilder();
        b.append(num(time)).append(' ').append(num(released)).append(' ').append(buildings.size());
        for (int i = 0; i < buildings.size(); i++) {
            b.append(" |");
            for (int k = 0; k < 3; k++) b.append(' ').append(num(fuel[i][k])).append(' ').append(num(burn[i][k])).append(' ').append(num(part[i][k]));
            b.append(' ').append((lit0[i] ? 1 : 0) + (lit1[i] ? 2 : 0) + (lit2[i] ? 4 : 0) + (collapsed[i] ? 8 : 0));
            b.append(' ').append(num(heat[i])).append(' ').append(num(igniteAt[i])).append(' ').append(num(collapseAt[i])).append(' ').append(num(hrr[i]));
        }
        return b.toString();
    }

    /** Stellt einen Zustand aus {@link #state} her; die Zahl der Häuser muss passen. */
    public void setState(String text) {
        String[] parts = text.split("\\|");
        String[] head = parts[0].trim().split(" ");
        int n = Integer.parseInt(head[2]);
        if (n != buildings.size() || parts.length != n + 1) throw new IllegalArgumentException("Zustand für " + n + " Häuser, hier sind es " + buildings.size());
        time = Double.parseDouble(head[0]);
        released = Double.parseDouble(head[1]);
        for (int i = 0; i < n; i++) {
            String[] v = parts[i + 1].trim().split(" ");
            int c = 0;
            for (int k = 0; k < 3; k++) { fuel[i][k] = Double.parseDouble(v[c++]); burn[i][k] = Double.parseDouble(v[c++]); part[i][k] = Double.parseDouble(v[c++]); }
            int f = Integer.parseInt(v[c++]);
            lit0[i] = (f & 1) != 0; lit1[i] = (f & 2) != 0; lit2[i] = (f & 4) != 0; collapsed[i] = (f & 8) != 0;
            heat[i] = Double.parseDouble(v[c++]);
            igniteAt[i] = Double.parseDouble(v[c++]);
            collapseAt[i] = Double.parseDouble(v[c++]);
            hrr[i] = Double.parseDouble(v[c]);
        }
    }

    private static String num(double v) {
        if (Double.isNaN(v)) return "NaN";
        if (v == Math.rint(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return Double.toString(v);
    }
}
