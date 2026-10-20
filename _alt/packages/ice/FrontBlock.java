package com.dan.ice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ein Abschnitt der Front als Körper für einen Bruch (Phase 7): ein Quader, der an der Front liegt – längs der Front
 * {@link #width} breit, von {@link #out} Metern vor der Front bis {@link #depth} Meter ins Eis, vom Grund (höchstens
 * {@link #below} unter dem Wasser) bis über die Oberfläche. Innen ist, was der Gletscher dort an Eis hat. Dazu die
 * Festigkeit je Ort (Spalten schwächen den oberen Teil, die Regie legt Schwächen hinein) und die Dichte der Zellen
 * (fein an der Front, grob dahinter).
 * <p>
 * Reines Java ohne Abhängigkeit: Wer den Abschnitt in Zellen zerlegt, fragt nur Zahlen ab (Paketvertrag Punkt 1).
 * Örtliche Achsen: a längs der Front (Tangente), b ins Eis (gegen die Normale), y oben.
 */
public final class FrontBlock {
    public final Glacier glacier;
    /** Punkt auf der Front (Mitte des Abschnitts) und Normale nach außen. */
    public final double px, pz, nx, nz;
    /** Breite längs der Front, Tiefe ins Eis, Rand vor der Front, Tiefe unter dem Wasser (m). */
    public final double width, depth, out, below;
    /** Höhenbereich des Quaders (m, y). */
    public final double yBottom, yTop;
    /** Wasserspiegel (m, y). */
    public final double waterLevel;
    /** Feinheit der Oberseite (m): so grob wird die Oberfläche mit Spalten gemittelt. */
    public double topSpacing = 10;
    private final List<double[]> weak = new ArrayList<>();
    private final List<double[]> gone = new ArrayList<>();

    /**
     * Eine Schwäche: ein Band durch den Abschnitt. along = false: parallel zur Front bei b = at (m ins Eis), zwischen
     * a = lo und hi; along = true: quer zur Front bei a = at, zwischen b = lo und hi. Halbe Dicke, Faktor, Höhen.
     */
    public record Weakness(boolean along, double at, double half, double factor, double y0, double y1, double lo, double hi) {
        /** Parallel zur Front (eine Spalte hinter einem Turm). */
        public static Weakness behind(double b, double half, double factor, double y0, double y1, double a0, double a1) {
            return new Weakness(false, b, half, factor, y0, y1, a0, a1);
        }
        /** Quer zur Front (die Flanke eines Turms). */
        public static Weakness side(double a, double half, double factor, double y0, double y1, double b0, double b1) {
            return new Weakness(true, a, half, factor, y0, y1, b0, b1);
        }
    }

    FrontBlock(Glacier g, double[] front, double width, double depth, double out, double below) {
        glacier = g;
        px = front[0];
        pz = front[1];
        double l = Math.hypot(front[2], front[3]);
        nx = front[2] / l;
        nz = front[3] / l;
        this.width = width;
        this.depth = depth;
        this.out = out;
        this.below = below;
        waterLevel = g.waterLevel;
        double lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 12; i++)
            for (int j = 0; j <= 12; j++) {
                double[] w = world(-width / 2 + width * i / 12.0, -out + (depth + out) * j / 12.0);
                if (!g.covers(w[0], w[1])) continue;
                hi = Math.max(hi, g.surface(w[0], w[1]));
                lo = Math.min(lo, g.bed(w[0], w[1]));
            }
        if (hi == Double.NEGATIVE_INFINITY) { hi = waterLevel + g.frontHeight; lo = waterLevel - below; }
        yTop = hi + 2;
        yBottom = Math.max(lo, waterLevel - below);
    }

    /**
     * Der Abschnitt an der Front nächst (x, z) (höchstens 400 m entfernt), oder null. Breite längs der Front, Tiefe
     * ins Eis, Tiefe unter dem Wasser.
     */
    public static FrontBlock near(Glacier g, double x, double z, double width, double depth, double below) {
        double[] f = g.nearestFront(x, z, 400);
        if (f == null) return null;
        // Befund Status_Phase7: am Ende einer Frontlinie (am Ufer der Halbinsel) ragte der Quader über die Front
        // hinaus. Darum längs der Front verschieben, bis die Front über die ganze Breite reicht.
        FrontBlock best = null;
        double bestE = Double.MAX_VALUE;
        for (int k = 0; k < 16; k++) {
            FrontBlock b = new FrontBlock(g, f, width, depth, 12, below);
            double e0 = b.offFront(-width / 2 + 4), e1 = b.offFront(width / 2 - 4);
            if (e0 < 8 && e1 < 8) return b;
            if (Math.max(e0, e1) < bestE) { bestE = Math.max(e0, e1); best = b; }
            // zur Seite hin, an der die Front weiter reicht
            double[] w = b.world(e0 > e1 ? 10 : -10, 0);
            f = g.nearestFront(w[0], w[1], 100);
            if (f == null) break;
        }
        // Seit Glacier Phase 12 (Befund Status_Phase11a): an einer gewellten Front reicht der beste Versuch, wenn die
        // Enden höchstens 12 % der Breite (mindestens 8 m) neben der Front liegen
        return best != null && bestE < Math.max(8, 0.12 * width) ? best : null;
    }

    /** Abstand (m) des Punkts a längs der Front (auf b = 0) von der Frontlinie. */
    double offFront(double a) {
        double[] w = world(a, 0);
        double[] f = glacier.nearestFront(w[0], w[1], 60);
        return f == null ? 1e9 : Math.hypot(f[0] - w[0], f[1] - w[1]);
    }

    // ------------------------------------------------------------------ Achsen

    /** Welt (x, z) aus örtlich (a längs, b ins Eis). */
    public double[] world(double a, double b) {
        double tx = -nz, tz = nx;
        return new double[]{px + tx * a - nx * b, pz + tz * a - nz * b};
    }

    /** Örtlich {a, b} aus Welt (x, z). */
    public double[] local(double x, double z) {
        double dx = x - px, dz = z - pz;
        return new double[]{-nz * dx + nx * dz, -(nx * dx + nz * dz)};
    }

    /** Im Quader (ohne Rücksicht auf das Eis)? */
    public boolean inBox(double x, double y, double z) {
        double[] l = local(x, z);
        return Math.abs(l[0]) <= width / 2 && l[1] >= -out && l[1] <= depth && y >= yBottom && y <= yTop;
    }

    /**
     * Die Hülle in Welt {x0, y0, z0, x1, y1, z1} und die Ebenen des Quaders {nx, ny, nz, d, …} (n·p ≤ d innen),
     * wie ein konvexer Umriss sie braucht.
     */
    public double[] bounds() {
        double x0 = 1e30, z0 = 1e30, x1 = -1e30, z1 = -1e30;
        for (int k = 0; k < 4; k++) {
            double[] w = world((k & 1) == 0 ? -width / 2 : width / 2, (k & 2) == 0 ? -out : depth);
            x0 = Math.min(x0, w[0]); x1 = Math.max(x1, w[0]);
            z0 = Math.min(z0, w[1]); z1 = Math.max(z1, w[1]);
        }
        return new double[]{x0, yBottom, z0, x1, yTop, z1};
    }

    public double[] planes() {
        double tx = -nz, tz = nx;
        double ca = tx * px + tz * pz, cb = nx * px + nz * pz;
        return new double[]{
                tx, 0, tz, ca + width / 2,          // a ≤ W/2
                -tx, 0, -tz, -ca + width / 2,       // a ≥ −W/2
                nx, 0, nz, cb + out,                // −b ≤ out  (n·p ≤ n·P + out)
                -nx, 0, -nz, -cb + depth,           // b ≤ D
                0, -1, 0, -yBottom,
                0, 1, 0, yTop};
    }

    /**
     * Der Ausschnitt für das Zeichnen: {Mitte x, Mitte z, Tangente x, Tangente z, halbe Breite, halbe Tiefe, y unten,
     * y oben}. Was darin vom Gletscher gezeichnet würde, ersetzen die Zellen.
     */
    public float[] cut() {
        double bc = (depth - out) / 2;
        double cx = px - nx * bc, cz = pz - nz * bc;
        return new float[]{(float) cx, (float) cz, (float) -nz, (float) nx, (float) (width / 2), (float) ((depth + out) / 2), (float) yBottom, (float) yTop};
    }

    // ------------------------------------------------------------------ Eis

    /** Oberseite des Eises (m): die Oberfläche, über {@link #topSpacing} gemittelt (Spalten als flachere Mulden). */
    public double top(double x, double z) { return glacier.surface(x, z, topSpacing, null); }

    /**
     * Die Front bei (x, z) als senkrechte Ebene {nx, nz, d} (Eis: nx·x + nz·z ≤ d), aus dem nächsten Punkt der
     * Frontlinie mit Vorstoß; null, wenn keine Front näher als 150 m ist. Wer Zellen schneidet, schneidet sie damit an
     * der Wand ab und nicht erst am Quader.
     */
    public double[] front(double x, double z) {
        double[] f = glacier.nearestFront(x, z, 150);
        if (f == null) return null;
        double l = Math.hypot(f[2], f[3]);
        double fx = f[2] / l, fz = f[3] / l;
        return new double[]{fx, fz, fx * f[0] + fz * f[1]};
    }

    /** Unterseite: der Grund, höchstens {@link #below} unter dem Wasser. */
    public double bottom(double x, double z) { return Math.max(yBottom, glacier.bed(x, z)); }

    /** Liegt (x, z) im Eis und im Quader? */
    public boolean insideXZ(double x, double z) {
        double[] l = local(x, z);
        if (Math.abs(l[0]) > width / 2 || l[1] < -out || l[1] > depth) return false;
        if (!glacier.covers(x, z)) return false;
        for (double[] g : gone) if (l[1] < g[0] && Math.abs(l[0]) < g[1]) return false;
        return true;
    }

    public boolean inside(double x, double y, double z) {
        return insideXZ(x, z) && y <= top(x, z) && y >= bottom(x, z);
    }

    /** Was zuvor abgebrochen ist: vor b (m ins Eis) über die halbe Breite a fehlt das Eis (für den nächsten Abschnitt). */
    public void removeBefore(double b, double halfA) { gone.add(new double[]{b, halfA}); }

    /** Lage (at) der Schwäche Nummer i. */
    public double weaknessAt(int i) { return weak.get(i)[0]; }

    /** Schwäche hinzufügen (etwa eine Spalte hinter einem Turm, die Flanken daneben). */
    public FrontBlock weaken(Weakness w) {
        weak.add(new double[]{w.at(), w.half(), w.factor(), w.y0(), w.y1(), w.lo(), w.hi(), w.along() ? 1 : 0});
        return this;
    }

    /**
     * Festigkeit am Ort als Faktor: Spalten schwächen das Eis bis 30 m unter der Oberfläche nach ihrer Öffnung, die
     * Schwächen der Regie wirken als Bänder mit weichem Rand.
     */
    public double strength(double x, double y, double z) {
        double k = 1;
        double o = glacier.openness(x, z);
        if (o > 0) {
            double t = top(x, z);
            k *= 1 - 0.6 * o * Glacier.smooth(t - 30, t - 5, y);
        }
        double[] l = local(x, z);
        for (double[] w : weak) {
            boolean along = w[7] > 0;
            double other = along ? l[1] : l[0], self = along ? l[0] : l[1];
            if (y < w[3] || y > w[4] || other < w[5] || other > w[6]) continue;
            double d = Math.abs(self - w[0]);
            if (d > 2 * w[1]) continue;
            double s = d <= w[1] ? 1 : 1 - (d - w[1]) / w[1];
            k *= 1 - (1 - w[2]) * s;
        }
        return k;
    }

    /** Dichte der Zellen 0..1: fein an der Front und um die Wasserlinie, grob dahinter. */
    public double density(double x, double y, double z) {
        double b = local(x, z)[1];
        double front = 1 - Glacier.smooth(5, 0.55 * depth, b);
        double line = Math.exp(-Math.pow((y - waterLevel) / 12, 2));
        return Math.min(1, 0.06 + 0.45 * front + 0.5 * line * front);
    }

    /** Abstand b ins Eis (m) des Punkts. */
    public double into(double x, double z) { return local(x, z)[1]; }

    @Override public String toString() {
        return String.format(Locale.GERMAN, "Abschnitt %.0f × %.0f m an (%.0f, %.0f), y %.0f bis %.0f", width, depth, px, pz, yBottom, yTop);
    }
}
