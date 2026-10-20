package com.dan.fracture;

/**
 * Der Umriss eines Körpers, der in Zellen zerlegt wird. Ein Umriss kennt seine Hülle (Quader), schneidet eine Zelle
 * auf seine Grenzen zu und sagt, ob ein Punkt innen liegt. Konvexe Umrisse schneiden exakt; das Höhenfeld schneidet
 * jede Zelle mit der Tangentialebene von Ober- und Unterseite an der Stelle ihrer Saat, und Zellen außerhalb des
 * Grundrisses fallen weg – so entsteht eine Kante aus Zellflächen, wie eine Abbruchkante.
 */
public interface Shape {
    /** Hülle {x0, y0, z0, x1, y1, z1} (m). */
    double[] bounds();

    /** Schneidet die Zelle der Saat (x, y, z) auf den Umriss zu (Flächenmarke −1). */
    void clip(Poly cell, double x, double y, double z);

    /** Liegt der Punkt im Körper? */
    boolean inside(double x, double y, double z);

    /** Höhe über (x, z) (m), etwa Ober- oder Unterseite eines Höhenfelds. */
    @FunctionalInterface interface Height { double at(double x, double z); }

    /** Grundriss: liegt (x, z) im Körper? */
    @FunctionalInterface interface Outline { boolean inside(double x, double z); }

    /** Ein Quader. */
    static Shape box(double x0, double y0, double z0, double x1, double y1, double z1) {
        return convex(new double[]{x0, y0, z0, x1, y1, z1});
    }

    /**
     * Ein konvexer Körper aus seiner Hülle und Ebenen {nx, ny, nz, d, …} (n·x ≤ d ist innen; n zeigt nach außen).
     */
    static Shape convex(double[] bounds, double... planes) {
        double[] b = bounds.clone(), pl = planes.clone();
        return new Shape() {
            @Override public double[] bounds() { return b.clone(); }
            @Override public void clip(Poly c, double x, double y, double z) {
                c.clip(-1, 0, 0, -b[0], -1); c.clip(1, 0, 0, b[3], -1);
                c.clip(0, -1, 0, -b[1], -1); c.clip(0, 1, 0, b[4], -1);
                c.clip(0, 0, -1, -b[2], -1); c.clip(0, 0, 1, b[5], -1);
                for (int k = 0; k + 3 < pl.length; k += 4) c.clip(pl[k], pl[k + 1], pl[k + 2], pl[k + 3], -1);
            }
            @Override public boolean inside(double x, double y, double z) {
                if (x < b[0] || y < b[1] || z < b[2] || x > b[3] || y > b[4] || z > b[5]) return false;
                for (int k = 0; k + 3 < pl.length; k += 4) {
                    double l = Math.sqrt(pl[k] * pl[k] + pl[k + 1] * pl[k + 1] + pl[k + 2] * pl[k + 2]);
                    if ((pl[k] * x + pl[k + 1] * y + pl[k + 2] * z - pl[k + 3]) / l > 0) return false;
                }
                return true;
            }
        };
    }

    /**
     * Ein Körper zwischen zwei Höhen über einem Grundriss im Rechteck x0..x1, z0..z1 – eine Felswand, eine Gletscherfront,
     * eine Staumauer. Jede Zelle wird mit den Tangentialebenen von {@code top} und {@code bottom} an ihrer Saat
     * geschnitten.
     */
    static Shape field(double x0, double z0, double x1, double z1, Height top, Height bottom, Outline outline) {
        double y0 = Double.POSITIVE_INFINITY, y1 = Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 32; i++)
            for (int j = 0; j <= 32; j++) {
                double x = x0 + (x1 - x0) * i / 32, z = z0 + (z1 - z0) * j / 32;
                if (outline != null && !outline.inside(x, z)) continue;
                y0 = Math.min(y0, bottom.at(x, z));
                y1 = Math.max(y1, top.at(x, z));
            }
        double[] b = {x0, y0 - 1, z0, x1, y1 + 1, z1};
        return new Shape() {
            @Override public double[] bounds() { return b.clone(); }
            @Override public void clip(Poly c, double x, double y, double z) {
                double h = 0.5;
                double t = top.at(x, z), tx = (top.at(x + h, z) - top.at(x - h, z)) / (2 * h), tz = (top.at(x, z + h) - top.at(x, z - h)) / (2 * h);
                // Oberseite: y − t − tx (x' − x) − tz (z' − z) ≤ 0
                c.clip(-tx, 1, -tz, t - tx * x - tz * z, -1);
                double u = bottom.at(x, z), ux = (bottom.at(x + h, z) - bottom.at(x - h, z)) / (2 * h), uz = (bottom.at(x, z + h) - bottom.at(x, z - h)) / (2 * h);
                c.clip(ux, -1, uz, -(u - ux * x - uz * z), -1);
            }
            @Override public boolean inside(double x, double y, double z) {
                if (x < b[0] || z < b[2] || x > b[3] || z > b[5]) return false;
                if (outline != null && !outline.inside(x, z)) return false;
                return y <= top.at(x, z) && y >= bottom.at(x, z);
            }
        };
    }
}
