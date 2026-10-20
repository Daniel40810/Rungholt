package com.dan.ice;

import java.util.ArrayList;
import java.util.List;

/**
 * Die Front eines {@link Glacier} als Wand: entlang jeder Frontlinie (Punkte alle {@link Glacier#STEP} m) eine Säule
 * von der Oberkante bis 30 m unter den Wasserspiegel (oder bis zum Grund). Die Oberkante ist die Oberfläche knapp
 * hinter der Front – wo Spalten die Front schneiden, steht die Wand in Türmen. Die Fläche selbst:
 * <ul>
 * <li><b>Furchen:</b> senkrechte Rillen, wie sie Schmelzwasser und abbrechende Platten hinterlassen (auf allen Fotos
 * der Front zu sehen);</li>
 * <li><b>Abbruchnarben:</b> Abschnitte von 12 bis 45 m, in denen ein Stück der Wand bis in eine zufällige Tiefe
 * fehlt – flache, frische Flächen 2 bis 9 m hinter der übrigen Wand [G];</li>
 * <li><b>Überhang:</b> die obersten 15 m neigen sich leicht nach vorn;</li>
 * <li><b>Schmelzkerbe:</b> an der Wasserlinie frisst das wärmere Wasser eine Kerbe von bis zu 5 m Tiefe, die die
 * Wand unterhöhlt [G] – dort beginnt die Kalbung (Phase 7).</li>
 * </ul>
 * Die oberste Reihe greift 3 m unter die Oberfläche zurück, damit zwischen Wand und Kacheln kein Spalt bleibt.
 */
public final class GlacierWall {
    /** Tiefe der Schmelzkerbe (m) und ihre Höhe über dem Wasser (Mitte, m) [G]. */
    public static final double NOTCH_DEPTH = 4.5, NOTCH_Y = 1.2;

    private GlacierWall() { }

    /** Die Wand aller Frontlinien als ein Netz. */
    public static IceMesh build(Glacier g) {
        int verts = 0, tris = 0;
        List<IceMesh> meshes = new ArrayList<>();
        List<double[]>[] snap = g.frontSnapshot();
        List<double[]> lines = snap[0], normals = snap[1];
        for (int l = 0; l < lines.size(); l++) {
            IceMesh m = line(g, lines.get(l), normals.get(l), l);
            meshes.add(m);
            verts += m.pos.length / 3;
            tris += m.idx.length;
        }
        float[] pos = new float[verts * 3], nrm = new float[verts * 3], ao = new float[verts];
        int[] idx = new int[tris];
        int vo = 0, io = 0;
        for (IceMesh m : meshes) {
            System.arraycopy(m.pos, 0, pos, vo * 3, m.pos.length);
            System.arraycopy(m.nrm, 0, nrm, vo * 3, m.nrm.length);
            System.arraycopy(m.ao, 0, ao, vo, m.ao.length);
            for (int k = 0; k < m.idx.length; k++) idx[io + k] = m.idx[k] + vo;
            vo += m.pos.length / 3;
            io += m.idx.length;
        }
        return new IceMesh(pos, nrm, idx, ao);
    }

    /** Reihen je Säule: Kappe, 22 bis zur Kerbe, 9 durch die Kerbe, 6 darunter. */
    static final int UPPER = 22, NOTCH = 9, LOWER = 6, ROWS = 1 + 1 + UPPER + NOTCH + LOWER;

    private static IceMesh line(Glacier g, double[] p, double[] nn, int lineNo) {
        int cols = p.length / 2;
        double wl = g.waterLevel;
        // Abbruchnarben: Abschnitte zufälliger Länge mit Tiefe und Unterkante
        double[] scarDepth = new double[cols], scarBottom = new double[cols], scarEdge = new double[cols];
        {
            double s = 0, segLen = 0, segStart = 0, depth = 0, bottom = 0;
            int seg = 0;
            for (int c = 0; c < cols; c++) {
                s = c * Glacier.STEP;
                if (s >= segStart + segLen) {
                    segStart = s;
                    segLen = 12 + 33 * g.hash(seg, lineNo, 21);
                    boolean scar = g.hash(seg, lineNo, 22) < 0.55;
                    depth = scar ? 2 + 7 * g.hash(seg, lineNo, 23) : 0;
                    bottom = 0.15 + 0.75 * g.hash(seg, lineNo, 24);   // Anteil der Höhe über dem Wasser, bis zu dem die Narbe reicht
                    seg++;
                }
                double e = Math.min(s - segStart, segStart + segLen - s);
                scarEdge[c] = Glacier.smooth(0, 1.5 + 2 * g.hash(seg, lineNo, 25), e);
                scarDepth[c] = depth;
                scarBottom[c] = bottom;
            }
        }
        float[] pos = new float[cols * ROWS * 3], ao = new float[cols * ROWS];
        for (int c = 0; c < cols; c++) {
            double x = p[c * 2], z = p[c * 2 + 1], nx = nn[c * 2], nz = nn[c * 2 + 1];
            double s = c * Glacier.STEP + lineNo * 10_000;
            double top = g.surface(x - nx * 0.5, z - nz * 0.5);
            double above = Math.max(4, top - wl);
            double bottom = Math.max(g.bed(x, z), wl - 30);
            double notchTop = wl + NOTCH_Y + 3.5, notchBot = wl - 1.8;
            for (int r = 0; r < ROWS; r++) {
                double y, back;
                if (r == 0) { y = top - 3; back = 3; }                              // Kappe unter der Oberfläche
                else {
                    int q = r - 1;
                    if (q <= UPPER) y = top + (notchTop - top) * q / UPPER;
                    else if (q <= UPPER + NOTCH) y = notchTop + (notchBot - notchTop) * (q - UPPER) / NOTCH;
                    else y = notchBot + (bottom - notchBot) * (q - UPPER - NOTCH) / LOWER;
                    y = Math.min(y, top);
                    back = recess(g, s, y, top, above, wl, scarDepth[c] * scarEdge[c], scarBottom[c]);
                }
                int k = c * ROWS + r;
                pos[k * 3] = (float) (x - nx * back);
                pos[k * 3 + 1] = (float) y;
                pos[k * 3 + 2] = (float) (z - nz * back);
                double notch = Math.exp(-Math.pow((y - wl - NOTCH_Y) / 1.6, 2));
                double o = 1 - 0.55 * notch - 0.35 * Glacier.smooth(wl, wl - 25, y);
                if (r == 0) o = 0.5;
                ao[k] = (float) Math.max(0.15, o);
            }
        }
        // Dreiecke: Säule c mit c+1, Reihe r mit r+1; Umlauf so, dass die Normale zum Wasser zeigt
        List<int[]> tri = new ArrayList<>();
        for (int c = 0; c < cols - 1; c++)
            for (int r = 0; r < ROWS - 1; r++) {
                int a = c * ROWS + r, b = (c + 1) * ROWS + r, d = c * ROWS + r + 1, e = (c + 1) * ROWS + r + 1;
                tri.add(new int[]{a, d, b});
                tri.add(new int[]{b, d, e});
            }
        // Ausrichtung prüfen an einer Fläche in der Mitte: Normale muss zum Wasser zeigen
        float[] nrm = new float[pos.length];
        int mid = Math.max(0, Math.min(tri.size() - 1, (cols / 2) * (ROWS - 1) * 2 + 10));
        double[] fn = face(pos, tri.get(mid));
        int cc = Math.min(cols - 1, cols / 2);
        boolean flip = fn[0] * nn[cc * 2] + fn[2] * nn[cc * 2 + 1] < 0;
        for (int[] t : tri) {
            if (flip) { int tmp = t[1]; t[1] = t[2]; t[2] = tmp; }
            double[] f = face(pos, t);
            for (int v : t) { nrm[v * 3] += f[0]; nrm[v * 3 + 1] += f[1]; nrm[v * 3 + 2] += f[2]; }
        }
        for (int v = 0; v < nrm.length / 3; v++) {
            double l = Math.sqrt(nrm[v * 3] * nrm[v * 3] + nrm[v * 3 + 1] * nrm[v * 3 + 1] + nrm[v * 3 + 2] * nrm[v * 3 + 2]);
            if (l < 1e-12) continue;
            nrm[v * 3] /= l; nrm[v * 3 + 1] /= l; nrm[v * 3 + 2] /= l;
        }
        int[] idx = new int[tri.size() * 3];
        int k = 0;
        for (int[] t : tri) { idx[k++] = t[0]; idx[k++] = t[1]; idx[k++] = t[2]; }
        return new IceMesh(pos, nrm, idx, ao);
    }

    /** Wie weit die Wand bei Bogenlänge s und Höhe y hinter der Frontlinie liegt (m; negativ = vor ihr). */
    static double recess(Glacier g, double s, double y, double top, double above, double wl, double scar, double scarBottom) {
        double r = 0;
        // senkrechte Furchen: gestreckt in der Höhe
        r += 2.0 * (Glacier.fbm(s / 4.5, y / 45, 3) - 0.5) + 0.7 * (Glacier.fbm(s / 1.6 + 40, y / 14, 2) - 0.5);
        // breitere Rippen und Buchten
        r += 4.0 * (Glacier.fbm(s / 28 + 7, y / 120, 2) - 0.5);
        // Abbruchnarbe: oberhalb ihrer Unterkante zurückgesetzt; die Unterkante ist ausgefranst, nicht gerade
        double yb = wl + above * scarBottom + 9 * (Glacier.fbm(s / 7 + 3.1, 0.5, 2) - 0.5);
        r += scar * Glacier.smooth(yb - 2.5, yb + 1, y);
        // Überhang oben
        r -= 1.5 * Glacier.smooth(top - 15, top - 1, y);
        // Schmelzkerbe an der Wasserlinie
        r += NOTCH_DEPTH * (0.7 + 0.6 * Glacier.vnoise(s / 30, 3.3)) * Math.exp(-Math.pow((y - wl - NOTCH_Y) / 1.6, 2));
        // unter Wasser etwas vorstehender Fuß
        r -= 2.5 * Glacier.smooth(wl - 2, wl - 20, y);
        return r;
    }

    private static double[] face(float[] p, int[] t) {
        double ux = p[t[1] * 3] - p[t[0] * 3], uy = p[t[1] * 3 + 1] - p[t[0] * 3 + 1], uz = p[t[1] * 3 + 2] - p[t[0] * 3 + 2];
        double wx = p[t[2] * 3] - p[t[0] * 3], wy = p[t[2] * 3 + 1] - p[t[0] * 3 + 1], wz = p[t[2] * 3 + 2] - p[t[0] * 3 + 2];
        return new double[]{uy * wz - uz * wy, uz * wx - ux * wz, ux * wy - uy * wx};
    }
}
