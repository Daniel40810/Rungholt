package com.dan.ice;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ein Gletscher als Körper (Werkbuch Glacier, Phase 5): Oberfläche, Grund und Fließfeld aus einem Höhenraster, daraus
 * die Dehnung und mit ihr die Spalten, Seracs im Eisfall und an der Front, die Front selbst als Linie mit Wand,
 * Schmelzrinnen und der Vorstoß. Der Kern kennt nur das Raster ({@link Grid}), keine App: Höhe, Grund und Art jeder Zelle
 * (Fels, Wasser, Eis) gibt der Aufrufer.
 * <p>
 * <b>Oberfläche.</b> Die gemessene Höhe über dem Eis, geglättet, an der Front auf {@code frontHeight} angehoben (die
 * Höhendaten glätten die Wand zur Rampe und unterschätzen sie); die Anhebung klingt mit 1,5 km Abstand von der Front ab
 * und läuft zu den Rändern auf null.
 * <p>
 * <b>Fließen.</b> Das Eis fließt das geglättete Gefälle hinab, in der Mitte am schnellsten (Pfropfen, zu den Rändern
 * steil abfallend, wie bei Gletschern, die am Grund gleiten), zur Front hin schneller, weil das Wasser den Fuß
 * entlastet. {@code speed} gibt die Geschwindigkeit in der Mitte an der Front (m/Tag) [G].
 * <p>
 * <b>Spalten.</b> Eis reißt quer zur größten Dehnung. Die Dehnungsraten folgen aus dem Fließfeld; wo die größere
 * Hauptdehnung eine Schwelle übersteigt, öffnen sich Spalten quer zu ihr: Querspalten, wo das Eis schneller wird,
 * Randspalten schräg (45°) im Scherband an den Flanken, Längs- und Fächerspalten, wo es sich an der Front ausbreitet.
 * Wo beide Hauptdehnungen ziehen oder das Gefälle steil ist (Eisfall), kreuzen sich zwei Scharen: Seracs. Abstand,
 * Breite und Tiefe wachsen mit der Öffnung [G: 20 bis 40 m Abstand, bis 8 m breit, bis 30 m tief].
 * <p>
 * Alle Abstände in m, y nach oben, x und z wie im Raster. Gleicher seed, gleicher Gletscher.
 */
public final class Glacier {
    /** Arten der Zellen im Raster. */
    public static final byte ROCK = 0, WATER = 1, ICE = 2;

    /** Das Höhenraster: Mittelpunkte der Zellen bei x0 + (i + ½)·cell, z0 + (j + ½)·cell; Index j·nx + i. */
    public static final class Grid {
        public final int nx, nz;
        public final double x0, z0, cell;
        public final float[] height, bed;
        public final byte[] kind;

        public Grid(int nx, int nz, double x0, double z0, double cell, float[] height, float[] bed, byte[] kind) {
            this.nx = nx; this.nz = nz; this.x0 = x0; this.z0 = z0; this.cell = cell;
            this.height = height; this.bed = bed; this.kind = kind;
            if (height.length != nx * nz || bed.length != nx * nz || kind.length != nx * nz) throw new IllegalArgumentException("Glacier.Grid: Größen passen nicht");
        }
    }

    public final Grid grid;
    public final long seed;
    /** Wasserspiegel (y), Höhe der Front über dem Wasser, Geschwindigkeit an der Front (m/Tag). */
    public final double waterLevel, frontHeight, speed;
    /** Faktor auf die Spalten (0 = glatte Zunge, 1 = wie gerechnet, 2 = doppelt so offen). */
    public volatile double crevasseScale = 1;

    // Felder im feinen Raster
    private final int nx, nz;
    private final float[] iceF, lakeF, base, dFront, dMargin;
    // grobes Raster (Faktor C) für Fließen und Dehnung
    static final int C = 4;
    private final int cx, cz;
    private final float[] u, v, e1, e2, ang, open, serac, slope;
    private double meanFrontTop, frontLift;
    /** Linien der Front (je Linie x, z abwechselnd, Abstand {@link #STEP}), Normale nach außen (zum Wasser). */
    private volatile List<double[]> frontLines = new ArrayList<>(), frontNormals = new ArrayList<>();
    private final List<double[]> frontLinesBase = new ArrayList<>();
    private volatile double advance;
    private final PointHash frontHash = new PointHash(32);
    /** Rinnen (Schmelzbäche): Linien x, z; halbe Breite, Tiefe. */
    private final List<double[]> channels = new ArrayList<>();
    private final List<double[]> channelDims = new ArrayList<>();
    private final PointHash channelHash = new PointHash(24);
    /** Abstand der Punkte auf den Frontlinien (m). */
    public static final double STEP = 2;

    /** Spalten: Größe der Zellen, in denen eine Richtung gilt (m). */
    static final double CELL = 140;

    public Glacier(Grid g, double waterLevel, double frontHeight, double speed, long seed) {
        this.grid = g;
        this.waterLevel = waterLevel;
        this.frontHeight = frontHeight;
        this.speed = speed;
        this.seed = seed;
        nx = g.nx;
        nz = g.nz;
        int n = nx * nz;
        // weiche Masken (Eis, Wasser) über rund 12 m
        float[] mi = new float[n], ml = new float[n];
        for (int k = 0; k < n; k++) { mi[k] = g.kind[k] == ICE ? 1 : 0; ml[k] = g.kind[k] == WATER ? 1 : 0; }
        int r1 = Math.max(1, (int) Math.round(6 / g.cell));
        iceF = blur(mi, nx, nz, r1, 2);
        lakeF = blur(ml, nx, nz, r1, 2);
        // Abstand zur Front (Eis neben Wasser) und zum Rand (Eis neben Fels), mit nächster Frontzelle
        boolean[] fs = new boolean[n], ms = new boolean[n];
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int k = j * nx + i;
                if (g.kind[k] != ICE) continue;
                for (int d = 0; d < 4; d++) {
                    int a = i + (d == 0 ? 1 : d == 1 ? -1 : 0), b = j + (d == 2 ? 1 : d == 3 ? -1 : 0);
                    if (a < 0 || b < 0 || a >= nx || b >= nz) continue;
                    byte kk = g.kind[b * nx + a];
                    if (kk == WATER) fs[k] = true;
                    if (kk == ROCK) ms[k] = true;
                }
            }
        int[] nearFront = new int[n];
        dFront = distance(fs, nx, nz, (float) g.cell, nearFront);
        dMargin = distance(ms, nx, nz, (float) g.cell, null);
        // Oberkante an der Front im Mittel (über dem Wasser)
        double s = 0;
        int c = 0;
        // (die Zellen direkt an der Front liegen im Raster auf der Rampe; gemessen wird 25 bis 60 m dahinter)
        for (int k = 0; k < n; k++) if (g.kind[k] == ICE && dFront[k] > 25 && dFront[k] < 60 && dMargin[k] > 100) { s += g.height[k] - waterLevel; c++; }
        meanFrontTop = c > 0 ? s / c : frontHeight;
        frontLift = Math.max(0, frontHeight - meanFrontTop);
        // Oberfläche: Höhe nur über dem Eis gemittelt (25 m), dann angehoben; außerhalb die nächste Frontzelle (für den Vorstoß)
        // die Zellen der Rampe (bis 25 m hinter der Front) zählen nicht: dort glätten die Höhendaten die Wand weg
        float[] hm = new float[n], mw = new float[n];
        for (int k = 0; k < n; k++) { mw[k] = mi[k] > 0 && dFront[k] >= 25 ? 1 : 0; hm[k] = mw[k] * g.height[k]; }
        int r2 = Math.max(1, (int) Math.round(18 / g.cell));
        float[] bh = blur(hm, nx, nz, r2, 3), bm = blur(mw, nx, nz, r2, 3);
        base = new float[n];
        for (int k = 0; k < n; k++) {
            double h = bm[k] > 0.02 ? bh[k] / bm[k] : g.height[k];
            if (g.kind[k] == ICE) h += lift(dFront[k], dMargin[k]);
            base[k] = (float) h;
        }
        for (int k = 0; k < n; k++)
            if (g.kind[k] != ICE && nearFront[k] >= 0 && dFront[k] < 600) base[k] = base[nearFront[k]];

        // grobes Raster: Gefälle stark geglättet, Fließen, Dehnung
        cx = (nx + C - 1) / C;
        cz = (nz + C - 1) / C;
        int m = cx * cz;
        float[] cb = new float[m], cmi = new float[m], cdm = new float[m], cdf = new float[m];
        for (int j = 0; j < cz; j++)
            for (int i = 0; i < cx; i++) {
                int k = idx(Math.min(nx - 1, i * C + C / 2), Math.min(nz - 1, j * C + C / 2));
                cb[j * cx + i] = base[k];
                cmi[j * cx + i] = iceF[k] > 0.5 || (g.kind[k] != ROCK && dFront[k] < 600) ? 1 : 0;
                cdm[j * cx + i] = dMargin[k];
                cdf[j * cx + i] = dFront[k];
            }
        double cc = g.cell * C;
        int r3 = Math.max(1, (int) Math.round(150 / cc));
        float[] sm = blur(mul(cb, cmi), cx, cz, r3, 3), smm = blur(cmi, cx, cz, r3, 3);
        for (int k = 0; k < m; k++) sm[k] = smm[k] > 0.05 ? sm[k] / smm[k] : cb[k];
        u = new float[m];
        v = new float[m];
        slope = new float[m];
        for (int j = 0; j < cz; j++)
            for (int i = 0; i < cx; i++) {
                int k = j * cx + i;
                if (cmi[k] == 0) continue;
                double gx = (sm[j * cx + Math.min(cx - 1, i + 1)] - sm[j * cx + Math.max(0, i - 1)]) / (2 * cc);
                double gz = (sm[Math.min(cz - 1, j + 1) * cx + i] - sm[Math.max(0, j - 1) * cx + i]) / (2 * cc);
                double gl = Math.hypot(gx, gz);
                double shape = 1 - Math.pow(1 - Math.min(1, cdm[k] / 450.0), 4);
                double front = 1 + 0.8 * Math.exp(-cdf[k] / 700.0);
                double mag = speed / 1.8 * shape * front * Math.min(1.6, 0.6 + gl * 25);
                double sx = Math.atan2(gz, gx);
                u[k] = (float) (-Math.cos(sx) * mag);
                v[k] = (float) (-Math.sin(sx) * mag);
                // Neigung der eigenen (wenig geglätteten) Oberfläche für den Eisfall
                double hx = (cb[j * cx + Math.min(cx - 1, i + 1)] - cb[j * cx + Math.max(0, i - 1)]) / (2 * cc);
                double hz = (cb[Math.min(cz - 1, j + 1) * cx + i] - cb[Math.max(0, j - 1) * cx + i]) / (2 * cc);
                slope[k] = (float) Math.hypot(hx, hz);
            }
        // Geschwindigkeit leicht glätten (die Richtung aus dem Gefälle springt sonst an Graten)
        float[] us = blur(u, cx, cz, 1, 2), vs = blur(v, cx, cz, 1, 2);
        System.arraycopy(us, 0, u, 0, m);
        System.arraycopy(vs, 0, v, 0, m);
        e1 = new float[m];
        e2 = new float[m];
        ang = new float[m];
        open = new float[m];
        serac = new float[m];
        double ec = speed / 1.8 * 0.0016;  // Schwelle der Dehnung (1/Tag) [G]
        for (int j = 0; j < cz; j++)
            for (int i = 0; i < cx; i++) {
                int k = j * cx + i;
                if (cmi[k] == 0) continue;
                int ip = j * cx + Math.min(cx - 1, i + 1), im = j * cx + Math.max(0, i - 1), jp = Math.min(cz - 1, j + 1) * cx + i, jm = Math.max(0, j - 1) * cx + i;
                double exx = (u[ip] - u[im]) / (2 * cc), ezz = (v[jp] - v[jm]) / (2 * cc);
                double exz = 0.5 * ((u[jp] - u[jm]) / (2 * cc) + (v[ip] - v[im]) / (2 * cc));
                double mid = (exx + ezz) / 2, rad = Math.sqrt((exx - ezz) * (exx - ezz) / 4 + exz * exz);
                e1[k] = (float) (mid + rad);
                e2[k] = (float) (mid - rad);
                ang[k] = (float) (0.5 * Math.atan2(2 * exz, exx - ezz));   // Richtung der größten Dehnung
                double o = smooth(ec, 5 * ec, e1[k]);
                double fall = smooth(0.06, 0.14, slope[k]);
                // die letzten 600 m vor der Front sind ganz zerbrochen (Seracs), wie auf allen Fotos der Front
                double nearF = 1 - smooth(120, 900, cdf[k]);
                o = Math.max(o, Math.max(fall, nearF));
                open[k] = (float) (o * smooth(0, 60, cdm[k] + 40 * nearF));
                serac[k] = (float) Math.max(Math.max(smooth(0.2 * ec, 2 * ec, e2[k]) * o, fall), (1 - smooth(80, 700, cdf[k])) * smooth(0, 80, cdm[k]));
            }
        float[] os = blur(open, cx, cz, 1, 2), ss = blur(serac, cx, cz, 1, 2);
        System.arraycopy(os, 0, open, 0, m);
        System.arraycopy(ss, 0, serac, 0, m);
        traceFront();
        setAdvance(0);
    }

    // ------------------------------------------------------------------ Abfragen

    /** Ob die Stelle unter dem Gletscher liegt (mit Vorstoß). */
    public boolean covers(double x, double z) {
        double f = sample(iceF, x, z);
        double adv = advance;
        if (adv == 0) return f >= 0.5;
        // Befund Status_Phase7: die Suche nach der Front kostete je Aufruf Tausende Abfragen (bis 400 m); gesucht wird
        // jetzt nur, wo es auf sie ankommt, und nur so weit, wie der Vorstoß reicht
        if (adv > 0) {
            if (f >= 0.5) return true;
            // vor rückt sie nur ins Wasser, nicht aufs Land (Halbinsel, Ufer)
            if (sample(lakeF, x, z) <= 0.4) return false;
            double[] q = nearest(frontLinesBase, frontHash, x, z, adv + 1);
            return q != null && ((x - q[0]) * q[2] + (z - q[1]) * q[3] < 0 || Math.hypot(x - q[0], z - q[1]) < adv);
        }
        if (f < 0.5) return false;
        double[] q = nearest(frontLinesBase, frontHash, x, z, -adv + 1);
        if (q == null) return true;
        double d = Math.hypot(x - q[0], z - q[1]);
        double sd = (x - q[0]) * q[2] + (z - q[1]) * q[3] >= 0 ? d : -d;
        return sd < adv;
    }

    /**
     * Abstand zur Frontlinie ohne Vorstoß (m), außen (zum Wasser) positiv; NaN, wenn keine Front näher als 400 m ist.
     */
    public double frontSigned(double x, double z) {
        double[] q = nearest(frontLinesBase, frontHash, x, z, 400);
        if (q == null) return Double.NaN;
        double d = Math.hypot(x - q[0], z - q[1]);
        return (x - q[0]) * q[2] + (z - q[1]) * q[3] >= 0 ? d : -d;
    }

    /** Gemessene Höhe des Rasters (Gelände, über dem Eis die Höhendaten) und der Grund (m, y). */
    public double ground(double x, double z) { return sample(grid.height, x, z); }
    public double bed(double x, double z) { return sample(grid.bed, x, z); }

    /** Anteil Eis 0..1 aus der weichen Maske (ohne Vorstoß). */
    public double iceShare(double x, double z) { return sample(iceF, x, z); }

    /** Ausdehnung des Eises im Raster: {xmin, zmin, xmax, zmax} (mit 600 m Rand für den Vorstoß). */
    public double[] bounds() {
        double x0 = 1e30, z0 = 1e30, x1 = -1e30, z1 = -1e30;
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++)
                if (iceF[j * nx + i] >= 0.3) {
                    double x = grid.x0 + (i + 0.5) * grid.cell, z = grid.z0 + (j + 0.5) * grid.cell;
                    x0 = Math.min(x0, x); x1 = Math.max(x1, x); z0 = Math.min(z0, z); z1 = Math.max(z1, z);
                }
        return new double[]{Math.max(grid.x0, x0 - 600), Math.max(grid.z0, z0 - 600), Math.min(grid.x0 + nx * grid.cell, x1 + 600), Math.min(grid.z0 + nz * grid.cell, z1 + 600)};
    }

    /** Glatte Oberfläche ohne Spalten (m, y). */
    public double base(double x, double z) { return sample(base, x, z); }

    /** Geschwindigkeit {vx, vz} (m/Tag). */
    public double[] velocity(double x, double z) { return new double[]{sampleC(u, x, z), sampleC(v, x, z)}; }

    /** Größere Hauptdehnung (1/Tag) und ihre Richtung (Grad von +x). */
    public double[] strain(double x, double z) { return new double[]{sampleC(e1, x, z), sampleC(e2, x, z), Math.toDegrees(angleAt(x, z))}; }

    /** Öffnung der Spalten 0..1 und Stärke der zweiten Schar (Seracs) 0..1. */
    public double openness(double x, double z) { return Math.min(1, sampleC(open, x, z) * crevasseScale); }
    public double seracs(double x, double z) { return Math.min(1, sampleC(serac, x, z) * crevasseScale); }

    /** Abstand zur Front und zum Rand (m) im Raster. */
    public double distFront(double x, double z) { return sample(dFront, x, z); }
    public double distMargin(double x, double z) { return sample(dMargin, x, z); }

    /** Höhe der Oberfläche mit Spalten, Seracs und Rinnen (m, y) – für Kamera und Kollision. */
    public double surface(double x, double z) { return surface(x, z, 0, null); }

    /**
     * Höhe der Oberfläche für ein Netz mit dem Punktabstand {@code spacing} (m): feiner als ein Drittel des
     * Spaltenabstands mit Spalten, gröber gemittelt (die Spalten werden dort zu einer flacheren, dunkleren Fläche).
     * {@code ao}, wenn nicht null, bekommt die Verdeckung 0..1 (1 = offen, 0 = Grund einer tiefen Spalte).
     */
    public double surface(double x, double z, double spacing, double[] ao) {
        double h = sample(base, x, z);
        double o = openness(x, z), sr = seracs(x, z);
        double hum = 1.6 * (fbm(x / 30, z / 30, 3) - 0.5) + 0.5 * (fbm(x / 7 + 31, z / 7, 2) - 0.5);
        double carve = 0, cover = 0;
        if (o > 0.01) {
            double sp = spacingAt(o);
            // an der Kante selbst sind die Lücken zwischen den Türmen flacher (die Wand bricht als Ganzes, Fotos der Front)
            double depth = (4 + 26 * o) * (0.4 + 0.6 * smooth(10, 160, sample(dFront, x, z)));
            double avgCover = 0.5 * (2 + 9 * o) / sp * (1 + sr);
            double full = 1 - smooth(sp / 5, sp / 2.5, spacing);
            double c = 0;
            if (full > 0) {
                double[] r = crevasses(x, z, o, sr, sp, depth);
                c = r[0];
                cover = r[1];
            }
            double avg = depth * Math.min(0.9, avgCover) * 0.45;
            carve = full * c + (1 - full) * avg;
            cover = full * cover + (1 - full) * Math.min(1, avgCover);
            if (ao != null) ao[0] = full > 0 ? 1 - 0.85 * Math.pow(Math.min(1, c / depth), 0.85) : 1 - 0.45 * Math.min(1, avgCover);
        } else if (ao != null) ao[0] = 1;
        double ch = channel(x, z);
        if (ch > 0 && ao != null) ao[0] = Math.min(ao[0], 1 - 0.3 * ch);
        return h + hum * (1 - 0.6 * cover) - carve - ch * channelDepth(x, z);
    }

    /** Abstand der Spalten bei der Öffnung o (m). */
    static double spacingAt(double o) { return 46 - 22 * o; }

    /**
     * Tiefe der Spalten am Ort: in Zellen von {@link #CELL} m gilt je eine Richtung (aus dem Dehnungsfeld in der
     * Zellmitte), zwischen den Zellen weich überblendet; quer zur größten Dehnung die erste Schar, bei Seracs dazu
     * eine zweite quer dazu. Zwischen den Spalten gerundete Rücken. {Tiefe, Anteil offen}.
     */
    private double[] crevasses(double x, double z, double o, double sr, double sp, double depth) {
        double gx = x / CELL - 0.5, gz = z / CELL - 0.5;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double fx = gx - ix, fz = gz - iz;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double warp = 0.55 * (fbm(x / 90 + 7, z / 90, 2) - 0.5), warp2 = 0.55 * (fbm(x / 90, z / 90 + 13, 2) - 0.5);
        double hw = 0.5 * (2 + 9 * o);
        double sum = 0, cov = 0;
        for (int c = 0; c < 4; c++) {
            int a = ix + (c & 1), b = iz + (c >> 1);
            double w = ((c & 1) == 1 ? fx : 1 - fx) * ((c >> 1) == 1 ? fz : 1 - fz);
            if (w < 1e-4) continue;
            double ccx = (a + 0.5) * CELL, ccz = (b + 0.5) * CELL;
            double th = angleAt(ccx, ccz) + (hash(a, b, 3) - 0.5) * 0.45;
            double ex = Math.cos(th), ez = Math.sin(th);
            // Phase 6 (Befund Status_Phase5a, „Wellblech“): der Abstand der Spalten schwankt von Spalte zu Spalte
            // (0,7- bis 1,9-fach), statt fest zu sein; das Rauschen läuft quer mit 1,6 Abständen und längs mit 140 m
            double across = (x - ccx) * ex + (z - ccz) * ez;
            double jit = 0.76 * (vnoise(across / (1.6 * sp) + a * 1.7, (-(x - ccx) * ez + (z - ccz) * ex) / 140 + b * 2.3) - 0.5);
            double ph = across / sp + warp + jit + hash(a, b, 1);
            double along = (-(x - ccx) * ez + (z - ccz) * ex);
            double d1 = profile(ph, sp, hw, along, o, a * 7 + b * 13, x, z);
            double d = d1;
            if (sr > 0.02) {
                double across2 = -(x - ccx) * ez + (z - ccz) * ex;
                double jit2 = 0.76 * (vnoise(across2 / (2.0 * sp) + b * 1.9, across / 140 + a * 2.9) - 0.5);
                double ph2 = across2 / (sp * 1.25) + warp2 + jit2 + hash(a, b, 2);
                double along2 = (x - ccx) * ex + (z - ccz) * ez;
                double d2 = profile(ph2, sp * 1.25, hw * 0.9, along2, sr, a * 5 + b * 11 + 3, x + 77, z) * sr;
                d = Math.max(d1, d2);
            }
            sum += w * d;
            cov += w * (d > 0.3 ? 1 : 0);
        }
        return new double[]{sum * depth, cov};
    }

    /**
     * 0..1 Tiefe einer Schar am Ort: jede Spalte (Nummer floor(ph)) hat eigene Breite und reißt nur stückweise
     * (en échelon, Stücke von 60 bis 250 m), je offener, desto durchgehender; steile Wände, ein enger werdender Grund.
     */
    private double profile(double ph, double sp, double hw, double along, double o, int cell, double x, double z) {
        double fl = Math.floor(ph);
        int nr = (int) fl;
        double q = ph - fl;
        double hs = hash(nr, cell, 5);
        double width = hw * (0.5 + hs);
        double dist = Math.abs(q - 0.5) * sp;       // Abstand von der Mitte der Spalte (m)
        double shoulderW = 0.28 * sp;
        if (dist >= width * 1.3 + shoulderW) return 0;
        // Stücke längs der Spalte: Rauschen in der Längsrichtung, Schwelle sinkt mit der Öffnung
        double seg = vnoise(along / (60 + 190 * hash(nr, cell, 6)) + nr * 3.17, nr * 1.31 + cell * 0.7);
        double keep = smooth(0.62 - 0.55 * o, 0.72 - 0.5 * o, seg);
        if (keep <= 0) return 0;
        double wl = width * (0.35 + 0.65 * keep) * (0.75 + 0.5 * vnoise(x / 17 + 3, z / 17));
        double loc = (0.6 + 0.4 * keep) * (0.75 + 0.5 * vnoise(x / 23, z / 23));
        if (dist >= wl) {
            // abgerundete Schultern: die Kanten der Türme sind abgeschmolzen, die Oberseiten gewölbt
            double t = 1 - smooth(wl, wl + shoulderW, dist);
            return loc * 0.32 * t * t * o;
        }
        double t = dist / wl;
        return Math.max(loc * (1 - smooth(0.55, 1.0, t)) * (0.85 + 0.15 * (1 - t * t)), loc * 0.32 * o);
    }

    /** Richtung der größten Dehnung (Bogenmaß von +x), Mittel im groben Raster (Winkel verdoppelt gemittelt). */
    private double angleAt(double x, double z) {
        double gx = (x - grid.x0) / (grid.cell * C) - 0.5, gz = (z - grid.z0) / (grid.cell * C) - 0.5;
        int i = (int) Math.floor(gx), j = (int) Math.floor(gz);
        double fx = gx - i, fz = gz - j, sx = 0, sz = 0;
        for (int c = 0; c < 4; c++) {
            int a = Math.max(0, Math.min(cx - 1, i + (c & 1))), b = Math.max(0, Math.min(cz - 1, j + (c >> 1)));
            double w = ((c & 1) == 1 ? fx : 1 - fx) * ((c >> 1) == 1 ? fz : 1 - fz);
            double t = ang[b * cx + a] * 2;
            double wt = w * (0.2 + Math.abs(e1[b * cx + a]) * 1000);
            sx += Math.cos(t) * wt;
            sz += Math.sin(t) * wt;
        }
        return 0.5 * Math.atan2(sz, sx);
    }

    // ------------------------------------------------------------------ Front

    /** Die Frontlinien (mit Vorstoß): je Linie Punkte x, z im Abstand {@link #STEP}. */
    public List<double[]> frontLines() { return frontLines; }

    /** Linien und Normalen zusammen, aus demselben Stand (für Netze, die nebenher gebaut werden). */
    public synchronized List<double[]>[] frontSnapshot() {
        @SuppressWarnings("unchecked") List<double[]>[] r = new List[]{frontLines, frontNormals};
        return r;
    }

    /** Normalen der Frontlinien (nach außen, zum Wasser): je Linie nx, nz. */
    public List<double[]> frontNormals() { return frontNormals; }

    /** Vorstoß der Front (m; negativ = Rückzug), längs ihrer Normalen. */
    public double advance() { return advance; }

    /** Setzt den Vorstoß (m) und verschiebt die Frontlinien; Netze müssen danach neu gebaut werden. */
    public synchronized void setAdvance(double a) {
        List<double[]> lines = new ArrayList<>(), norms = new ArrayList<>();
        for (double[] p : frontLinesBase) {
            double[] q = new double[p.length];
            double[] nn = normals(p);
            for (int k = 0; k < p.length; k += 2) { q[k] = p[k] + nn[k] * a; q[k + 1] = p[k + 1] + nn[k + 1] * a; }
            lines.add(q);
            norms.add(normals(q));
        }
        frontLines = lines;
        frontNormals = norms;
        advance = a;
    }

    /** Länge aller Frontlinien (m). */
    public double frontLength() {
        double s = 0;
        for (double[] p : frontLines) for (int k = 2; k < p.length; k += 2) s += Math.hypot(p[k] - p[k - 2], p[k + 1] - p[k - 1]);
        return s;
    }

    /** Nächster Punkt auf der (verschobenen) Front: {x, z, nx, nz} oder null (weiter als maxDist). */
    public double[] nearestFront(double x, double z, double maxDist) {
        double[] q = nearest(frontLinesBase, frontHash, x, z, maxDist + Math.abs(advance) + 4);
        if (q == null) return null;
        return new double[]{q[0] + q[2] * advance, q[1] + q[3] * advance, q[2], q[3]};
    }

    /**
     * Die Frontlinien aus der weichen Eismaske (Höhenlinie 0,5 nach Marching Squares), nur wo Wasser daneben liegt;
     * zu Linien verkettet, mit 15 m Fenster geglättet, auf {@link #STEP} m neu verteilt; kürzer als 120 m fällt weg.
     */
    private void traceFront() {
        List<double[]> segs = new ArrayList<>();
        double cl = grid.cell;
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx - 1; i++) {
                double a = iceF[idx(i, j)] - 0.5, b = iceF[idx(i + 1, j)] - 0.5, c = iceF[idx(i + 1, j + 1)] - 0.5, d = iceF[idx(i, j + 1)] - 0.5;
                int code = (a > 0 ? 1 : 0) | (b > 0 ? 2 : 0) | (c > 0 ? 4 : 0) | (d > 0 ? 8 : 0);
                if (code == 0 || code == 15) continue;
                double lk = (lakeF[idx(i, j)] + lakeF[idx(i + 1, j)] + lakeF[idx(i + 1, j + 1)] + lakeF[idx(i, j + 1)]) / 4;
                double rk = 1 - lk - (iceF[idx(i, j)] + iceF[idx(i + 1, j)] + iceF[idx(i + 1, j + 1)] + iceF[idx(i, j + 1)]) / 4;
                if (lk < 0.08 || lk < rk) continue;
                double x0 = grid.x0 + (i + 0.5) * cl, z0 = grid.z0 + (j + 0.5) * cl;
                // Kanten: unten (a-b), rechts (b-c), oben (d-c), links (a-d)
                double[][] pts = new double[4][];
                if ((a > 0) != (b > 0)) pts[0] = new double[]{x0 + cl * a / (a - b), z0};
                if ((b > 0) != (c > 0)) pts[1] = new double[]{x0 + cl, z0 + cl * b / (b - c)};
                if ((d > 0) != (c > 0)) pts[2] = new double[]{x0 + cl * d / (d - c), z0 + cl};
                if ((a > 0) != (d > 0)) pts[3] = new double[]{x0, z0 + cl * a / (a - d)};
                List<double[]> p = new ArrayList<>();
                for (double[] q : pts) if (q != null) p.add(q);
                if (p.size() == 2) segs.add(new double[]{p.get(0)[0], p.get(0)[1], p.get(1)[0], p.get(1)[1]});
                else if (p.size() == 4) {
                    segs.add(new double[]{p.get(0)[0], p.get(0)[1], p.get(1)[0], p.get(1)[1]});
                    segs.add(new double[]{p.get(2)[0], p.get(2)[1], p.get(3)[0], p.get(3)[1]});
                }
            }
        // verketten über gerundete Endpunkte
        java.util.Map<Long, List<Integer>> ends = new java.util.HashMap<>();
        for (int s = 0; s < segs.size(); s++) {
            double[] g = segs.get(s);
            ends.computeIfAbsent(key(g[0], g[1]), k -> new ArrayList<>()).add(s);
            ends.computeIfAbsent(key(g[2], g[3]), k -> new ArrayList<>()).add(s);
        }
        boolean[] used = new boolean[segs.size()];
        for (int s0 = 0; s0 < segs.size(); s0++) {
            if (used[s0]) continue;
            used[s0] = true;
            java.util.ArrayDeque<double[]> line = new java.util.ArrayDeque<>();
            double[] g = segs.get(s0);
            line.add(new double[]{g[0], g[1]});
            line.add(new double[]{g[2], g[3]});
            for (int dir = 0; dir < 2; dir++) {
                while (true) {
                    double[] end = dir == 0 ? line.peekLast() : line.peekFirst();
                    List<Integer> cand = ends.get(key(end[0], end[1]));
                    int next = -1;
                    if (cand != null) for (int ci : cand) if (!used[ci]) { next = ci; break; }
                    if (next < 0) break;
                    used[next] = true;
                    double[] h = segs.get(next);
                    double[] other = key(h[0], h[1]) == key(end[0], end[1]) ? new double[]{h[2], h[3]} : new double[]{h[0], h[1]};
                    if (dir == 0) line.addLast(other); else line.addFirst(other);
                }
            }
            double[] raw = new double[line.size() * 2];
            int k = 0;
            for (double[] q : line) { raw[k++] = q[0]; raw[k++] = q[1]; }
            double[] res = resample(smoothLine(resample(raw, 3), 5), STEP);
            if (res.length / 2 * STEP < 120) continue;
            // Umlauf so, dass die Normale (links der Laufrichtung gedreht) zum Wasser zeigt
            double[] nn = normals(res);
            int mid = (res.length / 4) * 2;
            double lx = res[mid] + nn[mid] * 15, lz = res[mid + 1] + nn[mid + 1] * 15;
            if (sample(iceF, lx, lz) > 0.5) res = reverse(res);
            frontLinesBase.add(res);
        }
        for (double[] p : frontLinesBase) {
            double[] nn = normals(p);
            for (int k = 0; k < p.length; k += 2) frontHash.add(p[k], p[k + 1], nn[k], nn[k + 1]);
        }
    }

    private static long key(double x, double z) { return Math.round(x * 8) * 4_000_003L + Math.round(z * 8); }

    /** Normalen einer Linie (x, z abwechselnd): Tangente um 90° gedreht (rechts der Laufrichtung). */
    static double[] normals(double[] p) {
        int n = p.length / 2;
        double[] r = new double[p.length];
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 2), b = Math.min(n - 1, k + 2);
            double tx = p[b * 2] - p[a * 2], tz = p[b * 2 + 1] - p[a * 2 + 1], l = Math.max(1e-9, Math.hypot(tx, tz));
            r[k * 2] = -tz / l;
            r[k * 2 + 1] = tx / l;
        }
        return r;
    }

    static double[] reverse(double[] p) {
        double[] r = new double[p.length];
        for (int k = 0; k < p.length; k += 2) { r[p.length - 2 - k] = p[k]; r[p.length - 1 - k] = p[k + 1]; }
        return r;
    }

    /** Linie auf gleichen Abstand ds verteilen. */
    static double[] resample(double[] p, double ds) {
        int n = p.length / 2;
        if (n < 2) return p.clone();
        double[] cum = new double[n];
        for (int k = 1; k < n; k++) cum[k] = cum[k - 1] + Math.hypot(p[k * 2] - p[k * 2 - 2], p[k * 2 + 1] - p[k * 2 - 1]);
        int m = Math.max(2, (int) Math.round(cum[n - 1] / ds) + 1);
        double[] r = new double[m * 2];
        int seg = 0;
        for (int k = 0; k < m; k++) {
            double s = cum[n - 1] * k / (m - 1);
            while (seg < n - 2 && cum[seg + 1] < s) seg++;
            double f = (s - cum[seg]) / Math.max(1e-9, cum[seg + 1] - cum[seg]);
            r[k * 2] = p[seg * 2] + (p[seg * 2 + 2] - p[seg * 2]) * f;
            r[k * 2 + 1] = p[seg * 2 + 1] + (p[seg * 2 + 3] - p[seg * 2 + 1]) * f;
        }
        return r;
    }

    /** Gleitendes Mittel über ±w Punkte (Enden fest). */
    static double[] smoothLine(double[] p, int w) {
        int n = p.length / 2;
        double[] r = p.clone();
        for (int pass = 0; pass < 2; pass++) {
            double[] s = r.clone();
            for (int k = 1; k < n - 1; k++) {
                int a = Math.max(0, k - w), b = Math.min(n - 1, k + w);
                double sx = 0, sz = 0;
                for (int q = a; q <= b; q++) { sx += s[q * 2]; sz += s[q * 2 + 1]; }
                r[k * 2] = sx / (b - a + 1);
                r[k * 2 + 1] = sz / (b - a + 1);
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ Rinnen (Schmelzbäche)

    /**
     * Eine Rinne in die Oberfläche: Linie (x, z abwechselnd), halbe Breite und Tiefe (m). Schmelzbäche schneiden sich
     * so ihr Bett; das Wasser darin zeichnet der Aufrufer.
     */
    public synchronized void addChannel(double[] line, double halfWidth, double depth) {
        double[] p = resample(line, 1.5);
        channels.add(p);
        channelDims.add(new double[]{halfWidth, depth});
        int id = channels.size() - 1;
        for (int k = 0; k < p.length; k += 2) channelHash.add(p[k], p[k + 1], id, 0);
    }

    public List<double[]> channels() { return channels; }

    /** Abstand der Rinnen-Linie: 0..1 Tiefe am Ort. */
    private double channel(double x, double z) {
        if (channels.isEmpty()) return 0;
        double[] q = channelHash.nearest(x, z, 8);
        if (q == null) return 0;
        double hw = channelDims.get((int) q[2])[0];
        double d = Math.hypot(x - q[0], z - q[1]);
        if (d >= hw * 1.6) return 0;
        return 1 - smooth(hw * 0.6, hw * 1.6, d);
    }

    private double channelDepth(double x, double z) {
        double[] q = channelHash.nearest(x, z, 8);
        return q == null ? 0 : channelDims.get((int) q[2])[1];
    }

    /**
     * Ein Weg des Wassers über die glatte Oberfläche: vom Startpunkt immer das Gefälle hinab (Schritt step m), bis
     * zur Front, zum Rand, in eine Spalte (Gletschermühle) oder nach maxLen m. Linie x, z abwechselnd.
     */
    public double[] downhill(double x, double z, double step, double maxLen) {
        List<double[]> pts = new ArrayList<>();
        pts.add(new double[]{x, z});
        double vx = 0, vz = 0;
        for (double s = 0; s < maxLen; s += step) {
            double e = 6;
            double gx = (base(x + e, z) - base(x - e, z)) / (2 * e), gz = (base(x, z + e) - base(x, z - e)) / (2 * e);
            double l = Math.hypot(gx, gz);
            if (l < 1e-5) break;
            // Trägheit: der Bach mäandert leicht, statt an jeder Delle abzuknicken
            vx = 0.7 * vx - 0.3 * gx / l + 0.08 * (vnoise(x / 40, z / 40) - 0.5);
            vz = 0.7 * vz - 0.3 * gz / l + 0.08 * (vnoise(x / 40 + 9, z / 40) - 0.5);
            double vl = Math.hypot(vx, vz);
            x += vx / vl * step;
            z += vz / vl * step;
            pts.add(new double[]{x, z});
            if (!covers(x, z) || distMargin(x, z) < 40) break;
            double[] fr = nearestFront(x, z, 25);
            if (fr != null) break;
        }
        double[] r = new double[pts.size() * 2];
        for (int k = 0; k < pts.size(); k++) { r[k * 2] = pts.get(k)[0]; r[k * 2 + 1] = pts.get(k)[1]; }
        return r;
    }

    // ------------------------------------------------------------------ Kennzahlen

    /** Fläche des Gletschers im Raster (km²). */
    public double areaKm2() {
        long c = 0;
        for (float f : iceF) if (f >= 0.5) c++;
        return c * grid.cell * grid.cell / 1e6;
    }

    /** Mittlere Oberkante an der Front über dem Wasser, gemessen (vor der Anhebung) und gebaut. */
    public double measuredFrontTop() { return meanFrontTop; }

    /** Mittlere Höhe der Frontlinie über dem Wasser (mit Spalten, m). */
    public double frontTop() {
        double s = 0;
        int n = 0;
        for (double[] p : frontLines)
            for (int k = 0; k < p.length; k += 10) {
                double[] nn = nearestFront(p[k], p[k + 1], 3);
                double x = p[k] - (nn != null ? nn[2] : 0) * 3, z = p[k + 1] - (nn != null ? nn[3] : 0) * 3;
                s += surface(x, z) - waterLevel;
                n++;
            }
        return n > 0 ? s / n : 0;
    }

    /** Anteil der Fläche mit offenen Spalten (Öffnung über 0,3). */
    public double crevassedShare() {
        long a = 0, b = 0;
        for (int j = 0; j < cz; j++)
            for (int i = 0; i < cx; i++) {
                double x = grid.x0 + (i * C + C / 2.0 + 0.5) * grid.cell, z = grid.z0 + (j * C + C / 2.0 + 0.5) * grid.cell;
                if (sample(iceF, x, z) < 0.5) continue;
                b++;
                if (open[j * cx + i] * crevasseScale > 0.3) a++;
            }
        return b == 0 ? 0 : a / (double) b;
    }

    /** Größte Geschwindigkeit an der Front (m/Tag). */
    public double frontSpeed() {
        double m = 0;
        for (double[] p : frontLines)
            for (int k = 0; k < p.length; k += 20) {
                double[] q = nearestFront(p[k], p[k + 1], 3);
                double x = p[k] - (q != null ? q[2] : 0) * 30, z = p[k + 1] - (q != null ? q[3] : 0) * 30;
                double[] vv = velocity(x, z);
                m = Math.max(m, Math.hypot(vv[0], vv[1]));
            }
        return m;
    }

    /** „Gletscher: 24,8 km² · Front 5,6 km, 74 m über dem Wasser (gemessen 42 m) · 2,0 m/Tag · Spalten 46 % · Vorstoß +0 m“ */
    public String describe() {
        return String.format(Locale.GERMAN, "Gletscher: %.1f km² · Front %.1f km in %d Linien, %.0f m über dem Wasser (gemessen %.0f m) · bis %.1f m/Tag · Spalten auf %.0f %% · Vorstoß %+.0f m",
                areaKm2(), frontLength() / 1000, frontLines.size(), meanFrontTop + frontLift, meanFrontTop, frontSpeed(), crevassedShare() * 100, advance);
    }

    // ------------------------------------------------------------------ Hilfen

    private double lift(double df, double dm) {
        return frontLift * Math.exp(-df / 1500) * smooth(0, 250, dm);
    }

    private int idx(int i, int j) { return j * nx + i; }

    /** Bilinear im feinen Raster (Zellmitten). */
    double sample(float[] a, double x, double z) {
        double gx = (x - grid.x0) / grid.cell - 0.5, gz = (z - grid.z0) / grid.cell - 0.5;
        int i = (int) Math.floor(gx), j = (int) Math.floor(gz);
        double fx = gx - i, fz = gz - j;
        int i0 = Math.max(0, Math.min(nx - 1, i)), i1 = Math.max(0, Math.min(nx - 1, i + 1));
        int j0 = Math.max(0, Math.min(nz - 1, j)), j1 = Math.max(0, Math.min(nz - 1, j + 1));
        return (a[j0 * nx + i0] * (1 - fx) + a[j0 * nx + i1] * fx) * (1 - fz) + (a[j1 * nx + i0] * (1 - fx) + a[j1 * nx + i1] * fx) * fz;
    }

    /** Bilinear im groben Raster. */
    double sampleC(float[] a, double x, double z) {
        double gx = (x - grid.x0) / (grid.cell * C) - 0.5, gz = (z - grid.z0) / (grid.cell * C) - 0.5;
        int i = (int) Math.floor(gx), j = (int) Math.floor(gz);
        double fx = gx - i, fz = gz - j;
        int i0 = Math.max(0, Math.min(cx - 1, i)), i1 = Math.max(0, Math.min(cx - 1, i + 1));
        int j0 = Math.max(0, Math.min(cz - 1, j)), j1 = Math.max(0, Math.min(cz - 1, j + 1));
        return (a[j0 * cx + i0] * (1 - fx) + a[j0 * cx + i1] * fx) * (1 - fz) + (a[j1 * cx + i0] * (1 - fx) + a[j1 * cx + i1] * fx) * fz;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private static float[] mul(float[] a, float[] b) {
        float[] r = new float[a.length];
        for (int k = 0; k < a.length; k++) r[k] = a[k] * b[k];
        return r;
    }

    /** Kastenfilter mit Halbmesser r (Zellen), passes-mal (drei Durchgänge ≈ Gauß). */
    static float[] blur(float[] a, int nx, int nz, int r, int passes) {
        float[] s = a.clone(), t = new float[a.length];
        for (int p = 0; p < passes; p++) {
            for (int j = 0; j < nz; j++) {
                double acc = 0;
                int row = j * nx;
                for (int i = -r; i <= r; i++) acc += s[row + Math.max(0, Math.min(nx - 1, i))];
                for (int i = 0; i < nx; i++) {
                    t[row + i] = (float) (acc / (2 * r + 1));
                    acc += s[row + Math.min(nx - 1, i + r + 1)] - s[row + Math.max(0, i - r)];
                }
            }
            for (int i = 0; i < nx; i++) {
                double acc = 0;
                for (int j = -r; j <= r; j++) acc += t[Math.max(0, Math.min(nz - 1, j)) * nx + i];
                for (int j = 0; j < nz; j++) {
                    s[j * nx + i] = (float) (acc / (2 * r + 1));
                    acc += t[Math.min(nz - 1, j + r + 1) * nx + i] - t[Math.max(0, j - r) * nx + i];
                }
            }
        }
        return s;
    }

    /**
     * Abstand jeder Zelle zur nächsten Saatzelle (m), zwei Durchgänge mit Weitergabe der nächsten Saat (fast
     * euklidisch); {@code near}, wenn nicht null, bekommt den Index der nächsten Saat (−1 = keine).
     */
    static float[] distance(boolean[] seed, int nx, int nz, float cell, int[] near) {
        int n = nx * nz;
        int[] s = near != null ? near : new int[n];
        Arrays.fill(s, -1);
        float[] d = new float[n];
        for (int k = 0; k < n; k++) { if (seed[k]) { s[k] = k; d[k] = 0; } else d[k] = Float.MAX_VALUE; }
        int[][] fw = {{-1, 0}, {0, -1}, {-1, -1}, {1, -1}}, bw = {{1, 0}, {0, 1}, {1, 1}, {-1, 1}};
        for (int pass = 0; pass < 2; pass++) {
            for (int j = 0; j < nz; j++)
                for (int i = 0; i < nx; i++) relax(i, j, fw, s, d, nx, nz);
            for (int j = nz - 1; j >= 0; j--)
                for (int i = nx - 1; i >= 0; i--) relax(i, j, bw, s, d, nx, nz);
        }
        for (int k = 0; k < n; k++) d[k] = d[k] == Float.MAX_VALUE ? 1e6f : d[k] * cell;
        return d;
    }

    private static void relax(int i, int j, int[][] nb, int[] s, float[] d, int nx, int nz) {
        int k = j * nx + i;
        for (int[] o : nb) {
            int a = i + o[0], b = j + o[1];
            if (a < 0 || b < 0 || a >= nx || b >= nz) continue;
            int q = s[b * nx + a];
            if (q < 0) continue;
            int qi = q % nx, qj = q / nx;
            float dd = (float) Math.hypot(i - qi, j - qj);
            if (dd < d[k]) { d[k] = dd; s[k] = q; }
        }
    }

    double hash(int a, int b, int k) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ (k + seed * 31) * 0x165667B19E3779F9L;
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Glattes Wertrauschen 0..1 in der Ebene. */
    static double vnoise(double x, double z) {
        int i = (int) Math.floor(x), j = (int) Math.floor(z);
        double fx = x - i, fz = z - j;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = h2(i, j), b = h2(i + 1, j), c = h2(i, j + 1), d = h2(i + 1, j + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }

    static double fbm(double x, double z, int oct) {
        double s = 0, a = 0.5, n = 0;
        for (int o = 0; o < oct; o++) { s += a * vnoise(x, z); n += a; x = x * 2.03 + 11.7; z = z * 2.03 + 5.3; a *= 0.5; }
        return s / n;
    }

    private static double h2(int i, int j) {
        int h = i * 374761393 + j * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Nächster Punkt auf Linien über ein Raster aus Eimern. */
    private static double[] nearest(List<double[]> lines, PointHash h, double x, double z, double maxDist) {
        return h.nearest(x, z, maxDist);
    }

    /** Punkte mit zwei Begleitwerten in Eimern der Kante b (m) für die Suche nach dem nächsten. */
    static final class PointHash {
        final double b;
        final java.util.Map<Long, List<double[]>> map = new java.util.HashMap<>();
        PointHash(double b) { this.b = b; }
        void add(double x, double z, double p, double q) {
            map.computeIfAbsent(cell((int) Math.floor(x / b), (int) Math.floor(z / b)), k -> new ArrayList<>()).add(new double[]{x, z, p, q});
        }
        static long cell(int i, int j) { return ((long) i << 32) ^ (j & 0xFFFFFFFFL); }
        double[] nearest(double x, double z, double maxDist) {
            int r = (int) Math.ceil(maxDist / b);
            int ci = (int) Math.floor(x / b), cj = (int) Math.floor(z / b);
            double best = maxDist * maxDist;
            double[] res = null;
            // in Ringen nach außen; aufhören, sobald der Ring weiter weg ist als der beste Punkt
            for (int k = 0; k <= r; k++) {
                if (res != null && (k - 1) * b > Math.sqrt(best)) break;
                for (int j = cj - k; j <= cj + k; j++) {
                    boolean edge = j == cj - k || j == cj + k;
                    for (int i = ci - k; i <= ci + k; i += edge ? 1 : 2 * k) {
                        List<double[]> l = map.get(cell(i, j));
                        if (l != null)
                            for (double[] p : l) {
                                double d = (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z);
                                if (d < best) { best = d; res = p; }
                            }
                        if (k == 0) break;
                    }
                }
            }
            return res;
        }
    }
}
