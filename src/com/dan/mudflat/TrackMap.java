package com.dan.mudflat;

import java.util.List;
import java.util.Locale;

/**
 * Die Spuren im Watt als Raster um einen Mittelpunkt (Phase 7, Rungholt): Fußabdrücke (Stiefel links und rechts im
 * Wechsel), Robbenspuren (ein breites, flaches Band, wo der Körper geschleift ist, mit den Abdrücken der Vorderflossen
 * an den Rändern) und Radspuren (zwei Linien). Je Zelle die Tiefe 0..1 (1 = {@link #DEPTH} m). Das Raster folgt der
 * Kamera; {@code gl/MudflatGl} lädt es als Textur, {@code mudflat.glsl} macht daraus Mulden, in denen das Wasser
 * zuletzt steht. Die Spuren selbst hält {@link WetField}; die nächste Flut löscht sie.
 */
public final class TrackMap {
    /** Zellen je Seite, Größe einer Zelle (m), Tiefe einer vollen Spur (m). */
    public final int n;
    public final double cell;
    public static final double DEPTH = 0.03;
    public final byte[] depth;
    /** Linke untere Ecke (m) und Fassung (steigt mit jedem Neuzeichnen). */
    public volatile double x0, z0;
    public volatile int version;
    /** Zahl der gezeichneten Spuren beim letzten Mal. */
    public volatile int drawn;
    private int lastCount = -1;
    private double lastCx = Double.NaN, lastCz = Double.NaN;
    private long lastHash;

    public TrackMap(int n, double cell) {
        this.n = n;
        this.cell = cell;
        depth = new byte[n * n];
    }

    /** Für Rungholt: 1024² zu 6 cm (61 m um die Kamera). */
    public static TrackMap standard() { return new TrackMap(1024, 0.06); }

    /** Seitenlänge des Fensters (m). */
    public double size() { return n * cell; }

    /**
     * Neu zeichnen, wenn sich die Spuren geändert haben oder die Mitte mehr als ein Sechstel des Fensters gewandert ist.
     * Gibt true, wenn neu gezeichnet wurde.
     */
    public boolean update(List<WetField.Track> tracks, double cx, double cz) {
        long h = tracks.size();
        if (!tracks.isEmpty()) {
            WetField.Track a = tracks.get(0), b = tracks.get(tracks.size() - 1);
            h = h * 31 + Double.hashCode(a.x()) * 17 + Double.hashCode(b.x()) * 13 + Double.hashCode(b.z());
        }
        boolean moved = Double.isNaN(lastCx) || Math.abs(cx - lastCx) > size() / 6 || Math.abs(cz - lastCz) > size() / 6;
        if (!moved && h == lastHash && tracks.size() == lastCount) return false;
        if (moved) { lastCx = cx; lastCz = cz; }
        lastHash = h;
        lastCount = tracks.size();
        draw(tracks, lastCx - size() / 2, lastCz - size() / 2);
        return true;
    }

    /** Alle Spuren ins Raster mit der linken unteren Ecke (x0, z0). */
    public void draw(List<WetField.Track> tracks, double ox, double oz) {
        java.util.Arrays.fill(depth, (byte) 0);
        int k = 0, count = 0;
        for (WetField.Track t : tracks) {
            k++;
            double lx = t.x() - ox, lz = t.z() - oz;
            if (lx < -1 || lz < -1 || lx > size() + 1 || lz > size() + 1) continue;
            count++;
            double dl = Math.hypot(t.dirX(), t.dirZ());
            double fx = dl > 1e-9 ? t.dirX() / dl : 1, fz = dl > 1e-9 ? t.dirZ() / dl : 0, rx = -fz, rz = fx;
            switch (t.kind()) {
                case 0 -> {   // Stiefel: links und rechts im Wechsel, 28 × 11 cm
                    double side = (k & 1) == 0 ? 0.09 : -0.09;
                    stamp(t.x() + rx * side - ox, t.z() + rz * side - oz, fx, fz, 0.14, 0.055, 0.8, 2.5);
                }
                case 1 -> {   // Rad: zwei Linien, 1,5 m Spur
                    for (double side : new double[]{-0.75, 0.75}) stamp(t.x() + rx * side - ox, t.z() + rz * side - oz, fx, fz, 0.3, 0.05, 0.6, 1.5);
                }
                case 3 -> {   // nackter Fuß: Sohle, Ballen und fünf Zehen
                    double lx0 = t.x() - ox, lz0 = t.z() - oz;
                    stamp(lx0 - fx * 0.03, lz0 - fz * 0.03, fx, fz, 0.07, 0.035, 0.55, 2.2);
                    stamp(lx0 + fx * 0.06, lz0 + fz * 0.06, fx, fz, 0.045, 0.045, 0.6, 2.2);
                    for (int toe = 0; toe < 5; toe++) {
                        double o = (toe - 2) * 0.019;
                        stamp(lx0 + fx * 0.115 + rx * o, lz0 + fz * 0.115 + rz * o, fx, fz, 0.012, 0.011, 0.5, 2.0);
                    }
                }
                case 4 -> stamp(t.x() - ox, t.z() - oz, fx, fz, 0.14, 0.055, 0.8, 2.5);   // Stiefel genau am Ort
                default -> {  // Robbe: Band des Körpers, dazu die Vorderflossen an den Rändern
                    stamp(t.x() - ox, t.z() - oz, fx, fz, 0.26, 0.19, 0.45, 1.2);
                    double side = (k & 1) == 0 ? 0.22 : -0.22;
                    stamp(t.x() + rx * side + fx * 0.1 - ox, t.z() + rz * side + fz * 0.1 - oz, fx, fz, 0.06, 0.035, 0.9, 2.0);
                }
            }
        }
        x0 = ox;
        z0 = oz;
        drawn = count;
        version++;
    }

    /** Eine Mulde: Ellipse (Halbachsen a längs, b quer, m), Tiefe 0..1, Schärfe des Randes (≥ 1). */
    private void stamp(double lx, double lz, double fx, double fz, double a, double b, double d, double sharp) {
        double r = Math.max(a, b) + cell;
        int i0 = (int) Math.floor((lx - r) / cell), i1 = (int) Math.ceil((lx + r) / cell);
        int j0 = (int) Math.floor((lz - r) / cell), j1 = (int) Math.ceil((lz + r) / cell);
        for (int j = Math.max(0, j0); j <= Math.min(n - 1, j1); j++)
            for (int i = Math.max(0, i0); i <= Math.min(n - 1, i1); i++) {
                double px = (i + 0.5) * cell - lx, pz = (j + 0.5) * cell - lz;
                double u = (px * fx + pz * fz) / a, v = (-px * fz + pz * fx) / b;
                double q = u * u + v * v;
                if (q >= 1) continue;
                double val = d * Math.min(1, (1 - q) * sharp);
                int idx = j * n + i, old = depth[idx] & 255, nv = (int) Math.round(val * 255);
                if (nv > old) depth[idx] = (byte) nv;
            }
    }

    /** Tiefe am Ort (0..1), 0 außerhalb. */
    public double depthAt(double x, double z) {
        int i = (int) Math.floor((x - x0) / cell), j = (int) Math.floor((z - z0) / cell);
        if (i < 0 || j < 0 || i >= n || j >= n) return 0;
        return (depth[j * n + i] & 255) / 255.0;
    }

    /** „Spuren 1024² zu 6 cm · 312 gezeichnet“ */
    public String describe() { return String.format(Locale.GERMAN, "Spuren %d² zu %.0f cm · %d gezeichnet", n, cell * 100, drawn); }
}
