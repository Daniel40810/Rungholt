package com.dan.fracture;

/** Kleine Helfer für 3×3-Matrizen (zeilenweise) und Quaternionen {w, x, y, z}. */
final class Mat {
    private Mat() { }

    static double[] transpose(double[] a) {
        return new double[]{a[0], a[3], a[6], a[1], a[4], a[7], a[2], a[5], a[8]};
    }

    /** Aᵀ · v */
    static double[] mulT(double[] a, double[] v) {
        return new double[]{a[0] * v[0] + a[3] * v[1] + a[6] * v[2], a[1] * v[0] + a[4] * v[1] + a[7] * v[2], a[2] * v[0] + a[5] * v[1] + a[8] * v[2]};
    }

    /**
     * Eigenwerte und -vektoren einer symmetrischen 3×3-Matrix (Jacobi). Die Spalten von vec sind die Vektoren,
     * rechtshändig (det = +1).
     */
    static void eigen(double[] a0, double[] vec, double[] val) {
        double[] a = a0.clone();
        double[] v = {1, 0, 0, 0, 1, 0, 0, 0, 1};
        for (int sweep = 0; sweep < 50; sweep++) {
            double off = a[1] * a[1] + a[2] * a[2] + a[5] * a[5];
            if (off < 1e-30 * (a[0] * a[0] + a[4] * a[4] + a[8] * a[8] + 1e-300)) break;
            for (int pq = 0; pq < 3; pq++) {
                int p = pq == 2 ? 1 : 0, q = pq == 0 ? 1 : 2;
                double apq = a[p * 3 + q];
                if (Math.abs(apq) < 1e-300) continue;
                double app = a[p * 3 + p], aqq = a[q * 3 + q];
                double theta = (aqq - app) / (2 * apq);
                double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1));
                if (theta == 0) t = 1;
                double c = 1 / Math.sqrt(t * t + 1), s = t * c;
                // A' = Jᵀ A J
                for (int k = 0; k < 3; k++) {
                    double akp = a[k * 3 + p], akq = a[k * 3 + q];
                    a[k * 3 + p] = c * akp - s * akq;
                    a[k * 3 + q] = s * akp + c * akq;
                }
                for (int k = 0; k < 3; k++) {
                    double apk = a[p * 3 + k], aqk = a[q * 3 + k];
                    a[p * 3 + k] = c * apk - s * aqk;
                    a[q * 3 + k] = s * apk + c * aqk;
                }
                for (int k = 0; k < 3; k++) {
                    double vkp = v[k * 3 + p], vkq = v[k * 3 + q];
                    v[k * 3 + p] = c * vkp - s * vkq;
                    v[k * 3 + q] = s * vkp + c * vkq;
                }
            }
        }
        val[0] = a[0]; val[1] = a[4]; val[2] = a[8];
        double det = v[0] * (v[4] * v[8] - v[5] * v[7]) - v[1] * (v[3] * v[8] - v[5] * v[6]) + v[2] * (v[3] * v[7] - v[4] * v[6]);
        if (det < 0) { v[2] = -v[2]; v[5] = -v[5]; v[8] = -v[8]; }
        System.arraycopy(v, 0, vec, 0, 9);
    }

    static void fromQuat(double[] q, double[] r) {
        double w = q[0], x = q[1], y = q[2], z = q[3];
        r[0] = 1 - 2 * (y * y + z * z); r[1] = 2 * (x * y - w * z); r[2] = 2 * (x * z + w * y);
        r[3] = 2 * (x * y + w * z); r[4] = 1 - 2 * (x * x + z * z); r[5] = 2 * (y * z - w * x);
        r[6] = 2 * (x * z - w * y); r[7] = 2 * (y * z + w * x); r[8] = 1 - 2 * (x * x + y * y);
    }

    static void toQuat(double[] m, double[] q) {
        double tr = m[0] + m[4] + m[8];
        if (tr > 0) {
            double s = Math.sqrt(tr + 1) * 2;
            q[0] = 0.25 * s; q[1] = (m[7] - m[5]) / s; q[2] = (m[2] - m[6]) / s; q[3] = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            q[0] = (m[7] - m[5]) / s; q[1] = 0.25 * s; q[2] = (m[1] + m[3]) / s; q[3] = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            q[0] = (m[2] - m[6]) / s; q[1] = (m[1] + m[3]) / s; q[2] = 0.25 * s; q[3] = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            q[0] = (m[3] - m[1]) / s; q[1] = (m[2] + m[6]) / s; q[2] = (m[5] + m[7]) / s; q[3] = 0.25 * s;
        }
        normalize(q);
    }

    static double[] qmul(double[] a, double[] b) {
        return new double[]{a[0] * b[0] - a[1] * b[1] - a[2] * b[2] - a[3] * b[3],
                a[0] * b[1] + a[1] * b[0] + a[2] * b[3] - a[3] * b[2],
                a[0] * b[2] - a[1] * b[3] + a[2] * b[0] + a[3] * b[1],
                a[0] * b[3] + a[1] * b[2] - a[2] * b[1] + a[3] * b[0]};
    }

    static void normalize(double[] q) {
        double l = Math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3]);
        for (int k = 0; k < 4; k++) q[k] /= l;
    }
}
