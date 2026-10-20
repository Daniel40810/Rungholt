package com.dan.buoyancy;

import java.util.Locale;

/**
 * Ein Eisberg (seit Glacier Phase 9): ein beliebiger geschlossener Körper als Starrkörper mit sechs Freiheiten, nicht
 * nur senkrechte Säulen wie {@link Floater}. Der Körper wird beim Anlegen in Proben zerlegt – ein Raster im Inneren des
 * Netzes, jede Probe ein Würfel mit Volumen. Je Schritt trägt jede Probe so viel, wie von ihr unter Wasser liegt;
 * Summe und Momente ergeben Schwimmen, Kippen und Rollen von selbst, auch bei Körpern, die kentern.
 * <ul>
 * <li><b>Schwimmen:</b> Auftrieb ρ<sub>w</sub>·g·V je Probe nach dem Anteil unter der Oberfläche, Schwere im
 * Schwerpunkt. Gletschereis (900 kg/m³) im Süßwasser schwimmt mit 90 % unter Wasser.</li>
 * <li><b>Widerstand:</b> gegen das Wasser linear und quadratisch je Probe, gegen die Luft über Wasser mit dem Wind –
 * so treibt ein Berg mit wenigen Prozent der Windgeschwindigkeit.</li>
 * <li><b>Schmelze mit wanderndem Schwerpunkt:</b> Proben an der Oberfläche verlieren Volumen, unter Wasser schneller
 * als an der Luft, an der Wasserlinie am schnellsten. Masse, Schwerpunkt und Trägheit werden neu gerechnet; wird der
 * Berg kopflastig, kippt er.</li>
 * <li><b>Grund:</b> Proben, die unter den Grund geraten, werden zurückgedrückt (Auflaufen, Stranden).</li>
 * </ul>
 * Koordinaten: Welt x Ost, y oben, z Süd. Der Körper hat ein eigenes Achsenkreuz (das des Netzes); {@link #pose()}
 * gibt die Modellmatrix mit der Schrumpfung durch die Schmelze.
 */
public final class Berg {
    public static final double G = 9.81;
    private static int ids;

    public final int id = ++ids;
    /** Dichte des Eises und des Wassers (kg/m³): Gletschereis mit Blasen im Süßwasser eines Gletschersees. */
    public double rhoIce = 900, rhoWater = 1000;
    /** Was der App dazu gehört (etwa das Netz zum Zeichnen). */
    public volatile Object tag;
    /** Woher: Text für die Anzeige (etwa „Turm, 14:32“). */
    public volatile String origin = "";

    // Proben im eigenen Achsenkreuz
    final double[] sx, sy, sz, sv;
    final boolean[] alive;
    final int[] neighbors;     // je Probe 6 Nachbarn (−1: keiner)
    final boolean[] surface;
    final double cell;
    final int n;
    private final double v0;
    private final float[] meshPos;
    private final int[] meshIdx;

    // Masse
    double mass, volume;
    final double[] cg = new double[3];          // Schwerpunkt im eigenen Achsenkreuz
    final double[] invI = new double[9];        // Kehrwert der Trägheit um den Schwerpunkt, eigenes Achsenkreuz
    double radius;                              // Hüllkugel um den Schwerpunkt

    // Lage: Ursprung des Achsenkreuzes in der Welt, Drehung, Geschwindigkeit des Schwerpunkts, Drehgeschwindigkeit (Welt)
    final double[] o = new double[3], q = {1, 0, 0, 0}, R = new double[9], v = new double[3], w = new double[3];
    double age, submerged, grounded;
    private final double[] upRef = {0, 1, 0};
    int capsizes;
    boolean dirtyMass;

    /**
     * Ein Berg aus einem geschlossenen Dreiecksnetz im eigenen Achsenkreuz (Umlauf gleich, Normalen nach außen),
     * zerlegt in rund {@code samples} Proben. Lage danach mit {@link #place}.
     */
    public Berg(float[] pos, int[] idx, int samples) {
        meshPos = pos;
        meshIdx = idx;
        double[] lo = {1e30, 1e30, 1e30}, hi = {-1e30, -1e30, -1e30};
        for (int i = 0; i < pos.length; i++) { lo[i % 3] = Math.min(lo[i % 3], pos[i]); hi[i % 3] = Math.max(hi[i % 3], pos[i]); }
        double vol = Math.abs(meshVolume(pos, idx));
        // Rasterweite so, dass rund „samples“ Proben im Inneren liegen
        double c = Math.cbrt(vol / Math.max(8, samples));
        int nx = Math.max(2, (int) Math.ceil((hi[0] - lo[0]) / c)), ny = Math.max(2, (int) Math.ceil((hi[1] - lo[1]) / c)), nz = Math.max(2, (int) Math.ceil((hi[2] - lo[2]) / c));
        // bei flachen Körpern dichter, damit jede Richtung mindestens zwei Proben hat
        c = Math.min(c, Math.min((hi[0] - lo[0]) / 2, Math.min((hi[1] - lo[1]) / 2, (hi[2] - lo[2]) / 2)));
        nx = Math.max(2, (int) Math.round((hi[0] - lo[0]) / c));
        ny = Math.max(2, (int) Math.round((hi[1] - lo[1]) / c));
        nz = Math.max(2, (int) Math.round((hi[2] - lo[2]) / c));
        double cx = (hi[0] - lo[0]) / nx, cy = (hi[1] - lo[1]) / ny, cz = (hi[2] - lo[2]) / nz;
        int[] grid = new int[nx * ny * nz];
        java.util.Arrays.fill(grid, -1);
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int k = 0; k < nz; k++)
            for (int j = 0; j < ny; j++)
                for (int i = 0; i < nx; i++) {
                    double px = lo[0] + (i + 0.5) * cx, py = lo[1] + (j + 0.5) * cy, pz = lo[2] + (k + 0.5) * cz;
                    if (!inside(pos, idx, px, py, pz)) continue;
                    grid[(k * ny + j) * nx + i] = pts.size();
                    pts.add(new double[]{px, py, pz, i, j, k});
                }
        if (pts.isEmpty()) pts.add(new double[]{(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2, 0, 0, 0});
        n = pts.size();
        sx = new double[n]; sy = new double[n]; sz = new double[n]; sv = new double[n];
        alive = new boolean[n];
        surface = new boolean[n];
        neighbors = new int[6 * n];
        double each = vol / n;   // das Raster trifft das Volumen nur ungefähr: auf das genaue verteilen
        int[][] dirs = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int s = 0; s < n; s++) {
            double[] p = pts.get(s);
            sx[s] = p[0]; sy[s] = p[1]; sz[s] = p[2]; sv[s] = each; alive[s] = true;
            for (int d = 0; d < 6; d++) {
                int i = (int) p[3] + dirs[d][0], j = (int) p[4] + dirs[d][1], k = (int) p[5] + dirs[d][2];
                neighbors[s * 6 + d] = i < 0 || j < 0 || k < 0 || i >= nx || j >= ny || k >= nz ? -1 : grid[(k * ny + j) * nx + i];
            }
        }
        cell = Math.cbrt(each);
        v0 = vol;
        updateSurface();
        massProperties();
        setRotation(1, 0, 0, 0);
    }

    /** Lage: Ursprung des Achsenkreuzes (Welt), Drehung als Quaternion {w, x, y, z}, Geschwindigkeit, Drehung (rad/s). */
    public Berg place(double x, double y, double z, double[] quat, double[] vel, double[] spin) {
        o[0] = x; o[1] = y; o[2] = z;
        if (quat != null) setRotation(quat[0], quat[1], quat[2], quat[3]);
        if (vel != null) System.arraycopy(vel, 0, v, 0, 3);
        if (spin != null) System.arraycopy(spin, 0, w, 0, 3);
        rememberUp();
        return this;
    }

    /** Lage aus einer Drehmatrix (zeilenweise, Welt = R · lokal + o). */
    public Berg placeMatrix(double x, double y, double z, double[] rot, double[] vel, double[] spin) {
        double[] qq = quat(rot);
        return place(x, y, z, qq, vel, spin);
    }

    /** Auf die Schwimmlage im ruhigen Wasser der Höhe {@code level} setzen (ohne Drehung zu ändern). */
    public void settle(double level) {
        // Halbierung über die Höhe: Auftrieb = Gewicht
        double lo = o[1] - 2 * radius - 50, hi = o[1] + 2 * radius + 50;
        for (int it = 0; it < 60; it++) {
            double m = (lo + hi) / 2;
            o[1] = m;
            double b = 0;
            for (int s = 0; s < n; s++) {
                if (!alive[s]) continue;
                double y = worldY(s);
                b += sv[s] * frac(level - y);
            }
            if (b * rhoWater > mass) lo = m; else hi = m;
        }
        o[1] = (lo + hi) / 2;
        java.util.Arrays.fill(v, 0);
        java.util.Arrays.fill(w, 0);
    }

    double worldY(int s) {
        double lx = sx[s], ly = sy[s], lz = sz[s];
        return R[3] * lx + R[4] * ly + R[5] * lz + o[1];
    }

    /** Anteil einer Probe unter Wasser, wenn die Oberfläche h über ihrer Mitte liegt (Würfel der Kante cell). */
    double frac(double h) { return Math.max(0, Math.min(1, h / cell + 0.5)); }

    // ------------------------------------------------------------------ Masse

    void massProperties() {
        double m = 0, x = 0, y = 0, z = 0, vol = 0, rho = rhoIce;
        for (int s = 0; s < n; s++) {
            if (!alive[s]) continue;
            vol += sv[s];
            x += sv[s] * sx[s]; y += sv[s] * sy[s]; z += sv[s] * sz[s];
        }
        volume = vol;
        mass = vol * rho;
        if (vol <= 0) { radius = 0; return; }
        cg[0] = x / vol; cg[1] = y / vol; cg[2] = z / vol;
        double[] I = new double[9];
        double r2max = 0;
        for (int s = 0; s < n; s++) {
            if (!alive[s]) continue;
            double mi = sv[s] * rho, dx = sx[s] - cg[0], dy = sy[s] - cg[1], dz = sz[s] - cg[2];
            double own = mi * Math.pow(sv[s], 2.0 / 3.0) / 6;   // Würfel um die eigene Mitte
            I[0] += mi * (dy * dy + dz * dz) + own; I[4] += mi * (dx * dx + dz * dz) + own; I[8] += mi * (dx * dx + dy * dy) + own;
            I[1] -= mi * dx * dy; I[2] -= mi * dx * dz; I[5] -= mi * dy * dz;
            r2max = Math.max(r2max, dx * dx + dy * dy + dz * dz);
        }
        I[3] = I[1]; I[6] = I[2]; I[7] = I[5];
        invert3(I, invI);
        radius = Math.sqrt(r2max) + cell;
        dirtyMass = false;
    }

    void updateSurface() {
        for (int s = 0; s < n; s++) {
            if (!alive[s]) { surface[s] = false; continue; }
            boolean out = false;
            for (int d = 0; d < 6 && !out; d++) { int t = neighbors[s * 6 + d]; out = t < 0 || !alive[t]; }
            surface[s] = out;
        }
    }

    // ------------------------------------------------------------------ Schritt

    /**
     * Ein Schritt dt (s). {@code meltDt}: Szenenzeit für die Schmelze (s, mit Zeitraffer). Kräfte von außen (Stöße)
     * sind schon in {@code fExt} und {@code tExt} (Welt, um den Schwerpunkt).
     */
    void step(double dt, double meltDt, Sea sea, Bergs world, double[] fExt, double[] tExt) {
        age += dt;
        double[] c = cgWorld();
        double fx = fExt[0], fy = fExt[1] - mass * G, fz = fExt[2], tx = tExt[0], ty = tExt[1], tz = tExt[2];
        double area = cell * cell, sub = 0;
        double windX = world.windX, windZ = world.windZ;
        double[] cur = sea.velocity(c[0], c[2]);
        grounded = 0;
        // Grund nur fragen, wo er nah ist (unter dem Schwerpunkt tiefer als der Berg reicht: frei)
        double bedC = sea.bed(c[0], c[2]);
        boolean nearBed = sea.level() - bedC < radius * 1.3 + 3 || c[1] - radius < bedC + 2;
        for (int s = 0; s < n; s++) {
            if (!alive[s]) continue;
            double lx = sx[s], ly = sy[s], lz = sz[s];
            double px = R[0] * lx + R[1] * ly + R[2] * lz + o[0], py = R[3] * lx + R[4] * ly + R[5] * lz + o[1], pz = R[6] * lx + R[7] * ly + R[8] * lz + o[2];
            double rx = px - c[0], ry = py - c[1], rz = pz - c[2];
            // Geschwindigkeit des Punkts
            double ux = v[0] + w[1] * rz - w[2] * ry, uy = v[1] + w[2] * rx - w[0] * rz, uz = v[2] + w[0] * ry - w[1] * rx;
            double h = sea.height(px, pz);
            double f = frac(h - py);
            double gx = 0, gy = 0, gz = 0;
            if (f > 0) {
                double vol = sv[s] * f;
                sub += vol;
                gy += rhoWater * G * vol;
                // Widerstand gegen das Wasser: linear (Zähigkeit, Wellenbildung) und quadratisch (Form)
                double wx = ux - cur[0], wy = uy - (cur.length > 2 ? cur[1] : 0), wz = uz - (cur.length > 2 ? cur[2] : cur[1]);
                double sp = Math.sqrt(wx * wx + wy * wy + wz * wz);
                // linear: senkrecht (Abstrahlung von Wellen beim Tauchen und Rollen), waagerecht kaum
                double kv = rhoWater * vol * world.waterDamping, kh = kv * 0.02;
                // quadratisch (Form): nur an der Oberfläche des Körpers
                double kq = surface[s] ? 0.5 * rhoWater * world.dragCoef * area * f * sp : 0;
                gx -= (kh + kq) * wx; gy -= (kv + kq) * wy; gz -= (kh + kq) * wz;
            }
            if (f < 1 && surface[s]) {
                // Wind auf den Teil über Wasser
                double ax = windX - ux, az = windZ - uz, sp = Math.hypot(ax, az);
                double k = 0.5 * world.rhoAir * world.windCoef * area * (1 - f) * sp;
                gx += k * ax; gz += k * az;
            }
            // Grund: zurückdrücken, Reibung
            double bed = nearBed ? sea.bed(px, pz) : -1e9;
            double pen = Math.min(cell, bed - (py - cell / 2));   // höchstens eine Probe tief: an einer Wand nicht hinaufschleudern
            if (pen > 0) {
                double kk = rhoWater * G * sv[s] * 20 / cell;
                double nfor = Math.min(kk * pen - 2 * Math.sqrt(kk * sv[s] * rhoIce) * Math.min(0, uy), 4 * rhoWater * G * sv[s]);
                if (nfor > 0) {
                    gy += nfor;
                    double t = Math.hypot(ux, uz);
                    if (t > 1e-4) { double fr = Math.min(0.5 * nfor, sv[s] * rhoIce * t / dt) / t; gx -= fr * ux; gz -= fr * uz; }
                    grounded += pen;
                }
            }
            fx += gx; fy += gy; fz += gz;
            tx += ry * gz - rz * gy; ty += rz * gx - rx * gz; tz += rx * gy - ry * gx;
        }
        submerged = volume > 0 ? sub / volume : 0;
        // Integration (halb implizit): Geschwindigkeit, dann Lage
        double im = 1 / mass;
        v[0] += fx * im * dt; v[1] += fy * im * dt; v[2] += fz * im * dt;
        // Trägheit in der Welt: R · I⁻¹ · Rᵀ
        double[] Iw = worldInvInertia();
        double ax = Iw[0] * tx + Iw[1] * ty + Iw[2] * tz, ay = Iw[3] * tx + Iw[4] * ty + Iw[5] * tz, az = Iw[6] * tx + Iw[7] * ty + Iw[8] * tz;
        w[0] += ax * dt; w[1] += ay * dt; w[2] += az * dt;
        // Grenzen gegen Ausreißer (ein Berg dreht sich nicht schneller als eine Umdrehung in 6 s)
        double ws = Math.sqrt(w[0] * w[0] + w[1] * w[1] + w[2] * w[2]);
        if (ws > 1) { w[0] /= ws; w[1] /= ws; w[2] /= ws; }
        // neue Lage des Schwerpunkts, dann Drehung um ihn
        double ncx = c[0] + v[0] * dt, ncy = c[1] + v[1] * dt, ncz = c[2] + v[2] * dt;
        double[] dq = {0, w[0], w[1], w[2]};
        double[] qd = qmul(dq, q);
        for (int k = 0; k < 4; k++) q[k] += 0.5 * dt * qd[k];
        double ql = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        setRotation(q[0] / ql, q[1] / ql, q[2] / ql, q[3] / ql);
        o[0] = ncx - (R[0] * cg[0] + R[1] * cg[1] + R[2] * cg[2]);
        o[1] = ncy - (R[3] * cg[0] + R[4] * cg[1] + R[5] * cg[2]);
        o[2] = ncz - (R[6] * cg[0] + R[7] * cg[1] + R[8] * cg[2]);
        // Schmelze
        if (meltDt > 0) melt(meltDt, sea, world);
        // gekentert? (die Richtung, die oben war, zeigt jetzt zur Seite oder nach unten, und er ist zur Ruhe gekommen)
        double[] up = worldDir(upLocal);
        if (up[0] * upRef[0] + up[1] * upRef[1] + up[2] * upRef[2] < 0.35 && ws < 0.05) {
            capsizes++;
            world.capsized(this);
            rememberUp();
        }
    }

    private final double[] upLocal = new double[3];
    private double pendingMelt;

    private void rememberUp() {
        // die Richtung, die in der Welt gerade oben ist, im eigenen Achsenkreuz
        upLocal[0] = R[3]; upLocal[1] = R[4]; upLocal[2] = R[5];
        upRef[0] = 0; upRef[1] = 1; upRef[2] = 0;
    }

    /** Schmelze an der Oberfläche: unter Wasser, an der Wasserlinie, an der Luft (m je Tag aus {@link Bergs}). */
    private void melt(double dtScene, Sea sea, Bergs world) {
        double perDay = dtScene / 86400, area = cell * cell;
        boolean lost = false;
        double changed = 0;
        for (int s = 0; s < n; s++) {
            if (!surface[s] || !alive[s]) continue;
            double y = worldY(s), h = sea.level();
            double f = frac(h - y);
            double rate = f >= 1 ? world.meltWater : f <= 0 ? world.meltAir : world.meltLine;
            double dv = rate * perDay * area;
            sv[s] -= dv;
            changed += dv;
            if (sv[s] <= 0.02 * cell * cell * cell) { sv[s] = 0; alive[s] = false; lost = true; }
        }
        if (lost) updateSurface();
        pendingMelt += changed;
        if (lost || pendingMelt > 0.002 * volume) { massProperties(); pendingMelt = 0; }
    }

    // ------------------------------------------------------------------ Abfragen

    /** Schwerpunkt in der Welt. */
    public double[] cgWorld() {
        return new double[]{R[0] * cg[0] + R[1] * cg[1] + R[2] * cg[2] + o[0], R[3] * cg[0] + R[4] * cg[1] + R[5] * cg[2] + o[1], R[6] * cg[0] + R[7] * cg[1] + R[8] * cg[2] + o[2]};
    }

    double[] worldDir(double[] l) {
        return new double[]{R[0] * l[0] + R[1] * l[1] + R[2] * l[2], R[3] * l[0] + R[4] * l[1] + R[5] * l[2], R[6] * l[0] + R[7] * l[1] + R[8] * l[2]};
    }

    private double[] worldInvInertia() {
        double[] t = new double[9], out = new double[9];
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += R[i * 3 + k] * invI[k * 3 + j];
                t[i * 3 + j] = s;
            }
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 3; j++) {
                double s = 0;
                for (int k = 0; k < 3; k++) s += t[i * 3 + k] * R[j * 3 + k];
                out[i * 3 + j] = s;
            }
        return out;
    }

    /**
     * Modellmatrix (spaltenweise, 16 Werte) zum Zeichnen des Netzes: Drehung, Lage und die Schrumpfung der Schmelze
     * um den Schwerpunkt (das Netz wird gleichmäßig kleiner, die Physik schmilzt Probe für Probe).
     */
    public float[] pose() {
        double s = Math.cbrt(Math.max(1e-9, volume / v0));
        // Welt = o + R·(cg + s·(p − cg))  =  (o + R·cg·(1 − s)) + s·R·p
        double tx = o[0] + (R[0] * cg[0] + R[1] * cg[1] + R[2] * cg[2]) * (1 - s);
        double ty = o[1] + (R[3] * cg[0] + R[4] * cg[1] + R[5] * cg[2]) * (1 - s);
        double tz = o[2] + (R[6] * cg[0] + R[7] * cg[1] + R[8] * cg[2]) * (1 - s);
        return new float[]{(float) (s * R[0]), (float) (s * R[3]), (float) (s * R[6]), 0,
                (float) (s * R[1]), (float) (s * R[4]), (float) (s * R[7]), 0,
                (float) (s * R[2]), (float) (s * R[5]), (float) (s * R[8]), 0, (float) tx, (float) ty, (float) tz, 1};
    }

    /** Seit Glacier Phase 10: eine Ecke des Netzes in der Welt (wie gezeichnet, mit Schmelze). */
    public double[] vertexWorld(int i) {
        float[] m = pose();
        double x = meshPos[i * 3], y = meshPos[i * 3 + 1], z = meshPos[i * 3 + 2];
        return new double[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]};
    }

    /** Seit Glacier Phase 10: die höchste Ecke des Netzes (Index), etwa als Sitzplatz einer Möwe. */
    public int topVertex() {
        float[] m = pose();
        int best = 0;
        double by = -1e18;
        for (int i = 0; i < meshPos.length / 3; i++) {
            double y = m[1] * meshPos[i * 3] + m[5] * meshPos[i * 3 + 1] + m[9] * meshPos[i * 3 + 2];
            if (y > by) { by = y; best = i; }
        }
        return best;
    }

    public float[] meshPositions() { return meshPos; }
    public int[] meshIndices() { return meshIdx; }
    public double volume() { return volume; }
    /** Schwerpunkt im eigenen Achsenkreuz (wandert mit der Schmelze). */
    public double[] cgLocal() { return cg.clone(); }
    public double initialVolume() { return v0; }
    public double mass() { return mass; }
    /** Anteil des Volumens unter Wasser beim letzten Schritt (0..1). */
    public double submerged() { return submerged; }
    /** Halbmesser der Hüllkugel um den Schwerpunkt (m). */
    public double radius() { return radius; }
    public double age() { return age; }
    public int capsizes() { return capsizes; }
    public boolean grounded() { return grounded > 0; }
    public double[] velocity() { return v.clone(); }
    public double[] spin() { return w.clone(); }
    public double[] rotation() { return R.clone(); }
    /** Tiefgang unter der Oberfläche {@code level} und Höhe über ihr (m). */
    public double[] draftAndFreeboard(double level) {
        double lo = 1e9, hi = -1e9;
        for (int s = 0; s < n; s++) if (alive[s]) { double y = worldY(s); lo = Math.min(lo, y - cell / 2); hi = Math.max(hi, y + cell / 2); }
        return new double[]{level - lo, hi - level};
    }
    /** Halbmesser in der Wasserlinie (m) für Umrisse auf dem Wasser. */
    public double waterlineRadius(double level) {
        double[] c = cgWorld(), p = new double[3];
        double r = 0;
        for (int s = 0; s < n; s++) {
            if (!alive[s]) continue;
            sampleWorld(s, p);
            if (Math.abs(p[1] - level) > cell) continue;
            r = Math.max(r, Math.hypot(p[0] - c[0], p[2] - c[2]));
        }
        return r + cell / 2;
    }
    public int samples() { int k = 0; for (boolean a : alive) if (a) k++; return k; }

    /** Zahl der Proben (auch geschmolzene), ob Probe s noch da ist, ihre Mitte in der Welt, Kante einer Probe (m). */
    public int sampleCount() { return n; }
    public boolean sampleAlive(int s) { return alive[s]; }
    public double[] sample(int s) { return sampleWorld(s, new double[3]); }
    public double cell() { return cell; }

    /** Probe s in der Welt (Mitte) und ihr Halbmesser, für Stöße; null, wenn geschmolzen. */
    double[] sampleWorld(int s, double[] out) {
        double lx = sx[s], ly = sy[s], lz = sz[s];
        out[0] = R[0] * lx + R[1] * ly + R[2] * lz + o[0];
        out[1] = R[3] * lx + R[4] * ly + R[5] * lz + o[1];
        out[2] = R[6] * lx + R[7] * ly + R[8] * lz + o[2];
        return out;
    }

    public String describe(double level) {
        double[] df = draftAndFreeboard(level);
        return String.format(Locale.GERMAN, "Eisberg %d: %,.0f m³ (%.0f %%), Tiefgang %.1f m, %.1f m über Wasser, %.0f %% unter Wasser%s%s",
                id, volume, 100 * volume / v0, df[0], df[1], 100 * submerged, grounded() ? ", gestrandet" : "", capsizes > 0 ? ", " + capsizes + "× gekentert" : "");
    }

    // ------------------------------------------------------------------ Mathe

    private void setRotation(double a, double b, double c, double d) {
        q[0] = a; q[1] = b; q[2] = c; q[3] = d;
        R[0] = 1 - 2 * (c * c + d * d); R[1] = 2 * (b * c - a * d); R[2] = 2 * (b * d + a * c);
        R[3] = 2 * (b * c + a * d); R[4] = 1 - 2 * (b * b + d * d); R[5] = 2 * (c * d - a * b);
        R[6] = 2 * (b * d - a * c); R[7] = 2 * (c * d + a * b); R[8] = 1 - 2 * (b * b + c * c);
    }

    static double[] qmul(double[] a, double[] b) {
        return new double[]{a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3], a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1], a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    /** Quaternion aus einer Drehmatrix (zeilenweise). */
    static double[] quat(double[] m) {
        double tr = m[0] + m[4] + m[8], w, x, y, z;
        if (tr > 0) {
            double s = Math.sqrt(tr + 1) * 2;
            w = 0.25 * s; x = (m[7] - m[5]) / s; y = (m[2] - m[6]) / s; z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s; x = 0.25 * s; y = (m[1] + m[3]) / s; z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s; x = (m[1] + m[3]) / s; y = 0.25 * s; z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s; x = (m[2] + m[6]) / s; y = (m[5] + m[7]) / s; z = 0.25 * s;
        }
        double l = Math.sqrt(w * w + x * x + y * y + z * z);
        return new double[]{w / l, x / l, y / l, z / l};
    }

    static void invert3(double[] a, double[] out) {
        double det = a[0] * (a[4] * a[8] - a[5] * a[7]) - a[1] * (a[3] * a[8] - a[5] * a[6]) + a[2] * (a[3] * a[7] - a[4] * a[6]);
        double id = Math.abs(det) < 1e-30 ? 0 : 1 / det;
        out[0] = (a[4] * a[8] - a[5] * a[7]) * id; out[1] = (a[2] * a[7] - a[1] * a[8]) * id; out[2] = (a[1] * a[5] - a[2] * a[4]) * id;
        out[3] = (a[5] * a[6] - a[3] * a[8]) * id; out[4] = (a[0] * a[8] - a[2] * a[6]) * id; out[5] = (a[2] * a[3] - a[0] * a[5]) * id;
        out[6] = (a[3] * a[7] - a[4] * a[6]) * id; out[7] = (a[1] * a[6] - a[0] * a[7]) * id; out[8] = (a[0] * a[4] - a[1] * a[3]) * id;
    }

    /** Volumen eines geschlossenen Netzes (m³; Vorzeichen nach dem Umlauf). */
    public static double meshVolume(float[] p, int[] idx) {
        double v = 0;
        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
            v += (p[a] * (p[b + 1] * p[c + 2] - p[b + 2] * p[c + 1]) - p[a + 1] * (p[b] * p[c + 2] - p[b + 2] * p[c]) + p[a + 2] * (p[b] * p[c + 1] - p[b + 1] * p[c])) / 6;
        }
        return v;
    }

    /** Liegt der Punkt im geschlossenen Netz? Strahl in eine schiefe Richtung, Schnitte zählen. */
    static boolean inside(float[] p, int[] idx, double x, double y, double z) {
        double dx = 0.5773, dy = 0.5774, dz = 0.5772;   // nicht achsparallel: Kanten treffen fast nie genau
        int hits = 0;
        for (int t = 0; t < idx.length; t += 3) {
            int a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
            double e1x = p[b] - p[a], e1y = p[b + 1] - p[a + 1], e1z = p[b + 2] - p[a + 2];
            double e2x = p[c] - p[a], e2y = p[c + 1] - p[a + 1], e2z = p[c + 2] - p[a + 2];
            double hx = dy * e2z - dz * e2y, hy = dz * e2x - dx * e2z, hz = dx * e2y - dy * e2x;
            double det = e1x * hx + e1y * hy + e1z * hz;
            if (Math.abs(det) < 1e-12) continue;
            double inv = 1 / det, sx = x - p[a], sy = y - p[a + 1], sz = z - p[a + 2];
            double u = (sx * hx + sy * hy + sz * hz) * inv;
            if (u < 0 || u > 1) continue;
            double qx = sy * e1z - sz * e1y, qy = sz * e1x - sx * e1z, qz = sx * e1y - sy * e1x;
            double vv = (dx * qx + dy * qy + dz * qz) * inv;
            if (vv < 0 || u + vv > 1) continue;
            double t2 = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (t2 > 0) hits++;
        }
        return (hits & 1) == 1;
    }
}
