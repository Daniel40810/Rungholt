package com.dan.fracture;

/**
 * Spannung in einem Netz starrer Zellen, die über ihre gemeinsamen Flächen federn (Rigid-Body-Spring-Modell nach
 * Kawai, wie es für Beton und Fels mit Voronoi-Zellen üblich ist). Jede Zelle hat sechs Freiheiten (Verschiebung und
 * Drehung um ihre Saat); jede Fläche trägt eine Feder für den Versatz in ihrer Mitte (Steifigkeit E·A/d in alle
 * Richtungen) und eine für die Verdrehung (E·I/d mit I = A²/4π). Weil die Flächen eines Voronoi-Netzes senkrecht auf
 * der Verbindung der Saaten stehen, gibt ein gleichmäßiger Spannungszustand genau die Spannung σ·n auf jeder Fläche –
 * die Handrechnung ist im Netz enthalten, nicht nur genähert.
 * <p>
 * Gelöst wird K·u = f mit vorkonditionierten konjugierten Gradienten ohne Matrix (Jacobi-Vorkonditionierer,
 * Warmstart aus der letzten Lösung). Danach je Fläche: Normalspannung (Zug positiv), Schub und Biegerandspannung.
 */
final class Stress {
    // Eingang
    int n;
    double[] x;         // Saaten (Knoten), 3n
    boolean[] fixed;    // gehaltene Zellen
    double[] f;         // Kräfte und Momente um die Saat, 6n
    int m;
    int[] bi, bj;           // bj < 0: Anker an festen Grund jenseits der Fläche
    double[] c, nrm, d, area;   // Flächenmitte, Normale i→j, Abstand der Saaten (beim Anker: Saat–Fläche), Fläche
    boolean[] active;
    double young = 1e10;
    // Ergebnis
    double[] u;         // 6n
    double[] sn, tau, sb;   // je Fläche: Normalspannung (Zug +), Schub, Biegerandspannung (Pa)
    double[] force, moment; // je Fläche: Kraft und Federmoment von i auf j (N, N·m), 3m
    int iterations;
    double residual;

    void init(int n, int m) {
        this.n = n;
        this.m = m;
        x = new double[3 * n];
        fixed = new boolean[n];
        f = new double[6 * n];
        u = new double[6 * n];
        bi = new int[m];
        bj = new int[m];
        c = new double[3 * m];
        nrm = new double[3 * m];
        d = new double[m];
        area = new double[m];
        active = new boolean[m];
        sn = new double[m];
        tau = new double[m];
        sb = new double[m];
        force = new double[3 * m];
        moment = new double[3 * m];
    }

    private double[] kc = new double[0], krc = new double[0];
    private double k(int b) { return kc[b]; }
    private double kr(int b) { return krc[b]; }

    private void stiffness() {
        if (kc.length != m) { kc = new double[m]; krc = new double[m]; }
        for (int b = 0; b < m; b++) {
            kc[b] = young * area[b] / d[b];
            krc[b] = young * area[b] * area[b] / (4 * Math.PI) / d[b];
        }
    }

    /** out = K·v */
    private void apply(double[] v, double[] out) {
        java.util.Arrays.fill(out, 0);
        for (int b = 0; b < m; b++) {
            if (!active[b]) continue;
            int i = bi[b], j = bj[b];
            double cx = c[b * 3], cy = c[b * 3 + 1], cz = c[b * 3 + 2];
            double rix = cx - x[i * 3], riy = cy - x[i * 3 + 1], riz = cz - x[i * 3 + 2];
            int I = i * 6;
            // Versatz in der Flächenmitte: (u_j + θ_j × r_j) − (u_i + θ_i × r_i); j < 0: fester Grund
            double dx = -(v[I] + v[I + 4] * riz - v[I + 5] * riy);
            double dy = -(v[I + 1] + v[I + 5] * rix - v[I + 3] * riz);
            double dz = -(v[I + 2] + v[I + 3] * riy - v[I + 4] * rix);
            double ax = -v[I + 3], ay = -v[I + 4], az = -v[I + 5];
            double rjx = 0, rjy = 0, rjz = 0;
            int J = -1;
            if (j >= 0) {
                J = j * 6;
                rjx = cx - x[j * 3]; rjy = cy - x[j * 3 + 1]; rjz = cz - x[j * 3 + 2];
                dx += v[J] + v[J + 4] * rjz - v[J + 5] * rjy;
                dy += v[J + 1] + v[J + 5] * rjx - v[J + 3] * rjz;
                dz += v[J + 2] + v[J + 3] * rjy - v[J + 4] * rjx;
                ax += v[J + 3]; ay += v[J + 4]; az += v[J + 5];
            }
            double kk = k(b), krr = kr(b);
            double Fx = kk * dx, Fy = kk * dy, Fz = kk * dz;
            double Mx = krr * ax, My = krr * ay, Mz = krr * az;
            if (J >= 0) {
                out[J] += Fx; out[J + 1] += Fy; out[J + 2] += Fz;
                out[J + 3] += rjy * Fz - rjz * Fy + Mx;
                out[J + 4] += rjz * Fx - rjx * Fz + My;
                out[J + 5] += rjx * Fy - rjy * Fx + Mz;
            }
            out[I] -= Fx; out[I + 1] -= Fy; out[I + 2] -= Fz;
            out[I + 3] -= riy * Fz - riz * Fy + Mx;
            out[I + 4] -= riz * Fx - rix * Fz + My;
            out[I + 5] -= rix * Fy - riy * Fx + Mz;
        }
        for (int i = 0; i < n; i++) if (fixed[i]) for (int q = 0; q < 6; q++) out[i * 6 + q] = 0;
    }

    private double[] diagonal() {
        double[] g = new double[6 * n];
        for (int b = 0; b < m; b++) {
            if (!active[b]) continue;
            int i = bi[b], j = bj[b];
            double kk = k(b), krr = kr(b);
            for (int s = 0; s < 2; s++) {
                int q = s == 0 ? i : j;
                if (q < 0) continue;
                double rx = c[b * 3] - x[q * 3], ry = c[b * 3 + 1] - x[q * 3 + 1], rz = c[b * 3 + 2] - x[q * 3 + 2];
                double r2 = rx * rx + ry * ry + rz * rz;
                g[q * 6] += kk; g[q * 6 + 1] += kk; g[q * 6 + 2] += kk;
                g[q * 6 + 3] += kk * (r2 - rx * rx) + krr;
                g[q * 6 + 4] += kk * (r2 - ry * ry) + krr;
                g[q * 6 + 5] += kk * (r2 - rz * rz) + krr;
            }
        }
        for (int q = 0; q < g.length; q++) g[q] = g[q] > 0 ? 1 / g[q] : 0;
        for (int i = 0; i < n; i++) if (fixed[i]) for (int q = 0; q < 6; q++) g[i * 6 + q] = 0;
        return g;
    }

    /** Löst K·u = f (Warmstart aus u) und rechnet die Spannungen je Fläche. */
    void solve(double tol, int maxIter) {
        stiffness();
        int N = 6 * n;
        double[] r = new double[N], z = new double[N], p = new double[N], Ap = new double[N];
        for (int i = 0; i < n; i++) if (fixed[i]) for (int q = 0; q < 6; q++) u[i * 6 + q] = 0;
        apply(u, Ap);
        double fn = 0;
        for (int q = 0; q < N; q++) {
            boolean fx = fixed[q / 6];
            r[q] = fx ? 0 : f[q] - Ap[q];
            if (!fx) fn += f[q] * f[q];
        }
        double[] Minv = diagonal();
        double rz = 0;
        for (int q = 0; q < N; q++) { z[q] = Minv[q] * r[q]; p[q] = z[q]; rz += r[q] * z[q]; }
        fn = Math.sqrt(fn);
        iterations = 0;
        double rn = norm(r);
        if (fn == 0) { java.util.Arrays.fill(u, 0); stresses(); residual = 0; return; }
        while (iterations < maxIter && rn > tol * fn) {
            apply(p, Ap);
            double pAp = 0;
            for (int q = 0; q < N; q++) pAp += p[q] * Ap[q];
            if (pAp <= 0) break;
            double a = rz / pAp;
            for (int q = 0; q < N; q++) { u[q] += a * p[q]; r[q] -= a * Ap[q]; }
            double rz2 = 0;
            for (int q = 0; q < N; q++) { z[q] = Minv[q] * r[q]; rz2 += r[q] * z[q]; }
            double beta = rz2 / rz;
            rz = rz2;
            for (int q = 0; q < N; q++) p[q] = z[q] + beta * p[q];
            rn = norm(r);
            iterations++;
        }
        residual = rn / fn;
        stresses();
    }

    private static double norm(double[] v) {
        double s = 0;
        for (double a : v) s += a * a;
        return Math.sqrt(s);
    }

    private void stresses() {
        for (int b = 0; b < m; b++) {
            if (!active[b]) { sn[b] = tau[b] = sb[b] = 0; force[b * 3] = force[b * 3 + 1] = force[b * 3 + 2] = 0; moment[b * 3] = moment[b * 3 + 1] = moment[b * 3 + 2] = 0; continue; }
            int i = bi[b], j = bj[b];
            double cx = c[b * 3], cy = c[b * 3 + 1], cz = c[b * 3 + 2];
            double rix = cx - x[i * 3], riy = cy - x[i * 3 + 1], riz = cz - x[i * 3 + 2];
            int I = i * 6;
            double dx = -(u[I] + u[I + 4] * riz - u[I + 5] * riy);
            double dy = -(u[I + 1] + u[I + 5] * rix - u[I + 3] * riz);
            double dz = -(u[I + 2] + u[I + 3] * riy - u[I + 4] * rix);
            double ax = -u[I + 3], ay = -u[I + 4], az = -u[I + 5];
            if (j >= 0) {
                int J = j * 6;
                double rjx = cx - x[j * 3], rjy = cy - x[j * 3 + 1], rjz = cz - x[j * 3 + 2];
                dx += u[J] + u[J + 4] * rjz - u[J + 5] * rjy;
                dy += u[J + 1] + u[J + 5] * rjx - u[J + 3] * rjz;
                dz += u[J + 2] + u[J + 3] * rjy - u[J + 4] * rjx;
                ax += u[J + 3]; ay += u[J + 4]; az += u[J + 5];
            }
            double nx = nrm[b * 3], ny = nrm[b * 3 + 1], nz = nrm[b * 3 + 2];
            double e = young / d[b];          // Spannung je Meter Versatz
            double kk = k(b);
            force[b * 3] = -kk * dx; force[b * 3 + 1] = -kk * dy; force[b * 3 + 2] = -kk * dz;
            double krr = kr(b);
            moment[b * 3] = -krr * ax; moment[b * 3 + 1] = -krr * ay; moment[b * 3 + 2] = -krr * az;
            double dn = dx * nx + dy * ny + dz * nz;
            sn[b] = e * dn;
            double tx = dx - dn * nx, ty = dy - dn * ny, tz = dz - dn * nz;
            double tauD = e * Math.sqrt(tx * tx + ty * ty + tz * tz);
            // Verdrehung: Biegung quer zur Normale, Torsion um sie
            double an = ax * nx + ay * ny + az * nz;
            double bx = ax - an * nx, by = ay - an * ny, bz = az - an * nz;
            double R = Math.sqrt(area[b] / Math.PI);
            // M = E I/d · θ, σ = M R / I = E R θ / d
            sb[b] = e * R * Math.sqrt(bx * bx + by * by + bz * bz);
            tau[b] = tauD + 0.5 * e * R * Math.abs(an);
        }
    }

    /** Auslastung einer Fläche: Zug, Druck oder Schub nach Mohr-Coulomb (1 = bricht). */
    static double utilization(double sn, double tau, double sb, double ft, double fc, double coh, double mu) {
        double ten = (sn + sb) / ft;
        double comp = (-sn + sb) / fc;
        double sh = tau / (coh + mu * Math.max(0, -sn));
        return Math.max(ten, Math.max(comp, sh));
    }
}
