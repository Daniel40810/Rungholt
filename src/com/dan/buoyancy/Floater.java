package com.dan.buoyancy;

/**
 * Ein schwimmender Körper mit Tauchen, Stampfen und Rollen auf einer Wasseroberfläche.
 * <p>
 * <b>Streifenmodell:</b> Der Rumpf ist eine Menge senkrechter Säulen (Ort in Körperkoordinaten, Wasserlinienfläche,
 * Kiel und Oberkante). Jede Säule trägt so viel, wie Wasser über ihrem Kiel steht (ρ g a d), gedämpft nach der
 * Geschwindigkeit gegen das Wasser. Summe und Momente dieser Kräfte ergeben Tauchen, Stampfen und Rollen; die
 * Wasserlinienfläche liefert so von selbst das Moment der Formstabilität (BM), dazu kommt der Anteil der Gewichtsstabilität
 * (Schwerpunkt über dem Auftriebsschwerpunkt). Die Wellen kommen über {@link Sea}: Laufen sie unter dem Körper
 * durch, hebt und kippt er sich mit, kurze Wellen glättet er.
 * <p>
 * <b>Koordinaten:</b> Körper: s nach vorn, q nach Steuerbord (rechts), y aufwärts ab der Konstruktionswasserlinie.
 * Welt: x Ost, y oben, z Süd (Nord = −z). Kurs in Grad von Nord im Uhrzeigersinn; Stampfen positiv = Bug hoch,
 * Rollen positiv = Steuerbord tief. Integriert wird halbimplizit in Schritten von höchstens 1/120 s.
 */
public class Floater {
    public static final double G = 9.81, RHO = 1025;

    public final String name;
    /** Säulen: Ort s (vorn), q (Steuerbord), Fläche a (m²), Kiel und Oberkante (m über der Wasserlinie). */
    final double[] cs, cq, ca, ckeel, ctop;
    final double awp, mass, cgY, kbY, kPitch, kRoll, lcb;
    /** Quadratische Rolldämpfung (Schlingerkiele): Anteil des Trägheitsmoments. */
    public double rollDrag = 8.0;
    /** Mitschwingende Wassermasse beim Tauchen und beim Drehen (Anteil der Masse bzw. des Trägheitsmoments). */
    public double addedHeave = 0.6, addedInertia = 0.3;
    /** Dämpfung als Anteil der kritischen: Tauchen, Stampfen, Rollen. */
    public double zetaHeave = 0.18, zetaPitch = 0.15, zetaRoll = 0.07;

    // ------------------------------------------------------------------ Zustand
    /** Ort des Bezugspunkts (Mitte der Wasserlinie) in der Welt und Kurs (Grad). */
    public double x, y, z, heading;
    /** Tauchgeschwindigkeit, Stampfen und Rollen (rad) mit ihren Geschwindigkeiten. */
    public double vy, pitch, vPitch, roll, vRoll;
    /** Waagerechte Geschwindigkeit (m/s), von außen gesetzt (Fahrt) oder aus Strömung und Verankerung. */
    public double vx, vz;
    private double[] wh, ww;
    private double sinceSample;
    private final double cHeave, kRollStiff, kPitchStiff;
    /** Geprüfte Größen der Hydrostatik (für Anzeige und Prüfung). */
    public final double draft, gm;

    /**
     * @param cols Säulen als {s, q, a, kiel, oberkante} je Zeile
     * @param cgY  Höhe des Schwerpunkts über der Wasserlinie (m)
     * @param kPitch, kRoll Trägheitsradien (m)
     */
    public Floater(String name, double[][] cols, double cgY, double kPitch, double kRoll) {
        this.name = name;
        int n = cols.length;
        cs = new double[n]; cq = new double[n]; ca = new double[n]; ckeel = new double[n]; ctop = new double[n];
        double a = 0, vol = 0, mom = 0, lmom = 0;
        for (int i = 0; i < n; i++) {
            cs[i] = cols[i][0]; cq[i] = cols[i][1]; ca[i] = cols[i][2]; ckeel[i] = cols[i][3]; ctop[i] = cols[i][4];
            a += ca[i];
            vol += ca[i] * -ckeel[i];
            mom += ca[i] * -ckeel[i] * ckeel[i] / 2;
            lmom += ca[i] * -ckeel[i] * cs[i];
        }
        // Schwerpunkt längs über dem Auftriebsschwerpunkt: der Körper schwimmt ohne Trimm
        lcb = lmom / Math.max(1e-9, vol);
        awp = a;
        // Masse so, dass der Körper auf seiner Konstruktionswasserlinie schwimmt
        mass = RHO * vol;
        draft = vol / a;
        kbY = mom / Math.max(1e-9, vol);
        this.cgY = cgY;
        this.kPitch = kPitch;
        this.kRoll = kRoll;
        double ir = 0, ip = 0;
        for (int i = 0; i < n; i++) { ir += ca[i] * cq[i] * cq[i]; ip += ca[i] * cs[i] * cs[i]; }
        // Steifigkeit: Formstabilität aus der Wasserlinie minus Gewichtsstabilität
        kRollStiff = RHO * G * ir - mass * G * (cgY - kbY);
        kPitchStiff = RHO * G * ip - mass * G * (cgY - kbY);
        gm = kRollStiff / (mass * G);
        cHeave = 2 * zetaHeave * Math.sqrt(RHO * G * awp * mass * (1 + addedHeave));
        wh = new double[n];
        ww = new double[n];
    }

    public double mass() { return mass; }
    public double waterplaneArea() { return awp; }
    public int columns() { return cs.length; }

    /** Eigenperiode beim Tauchen (s), aus Masse, mitschwingendem Wasser und Wasserlinie. */
    public double heavePeriod() { return 2 * Math.PI * Math.sqrt(mass * (1 + addedHeave) / (RHO * G * awp)); }

    /** Eigenperiode beim Rollen (s). */
    public double rollPeriod() { return 2 * Math.PI * Math.sqrt(mass * kRoll * kRoll * (1 + addedInertia) / Math.max(1e-9, kRollStiff)); }

    /** Eigenperiode beim Stampfen (s). */
    public double pitchPeriod() { return 2 * Math.PI * Math.sqrt(mass * kPitch * kPitch * (1 + addedInertia) / Math.max(1e-9, kPitchStiff)); }

    // ------------------------------------------------------------------ Richtungen

    /** Vorwärts in der Fläche (x, z) beim Kurs. */
    public double[] forward() { double h = Math.toRadians(heading); return new double[]{Math.sin(h), -Math.cos(h)}; }

    /** Steuerbord in der Fläche (x, z). */
    public double[] right() { double h = Math.toRadians(heading); return new double[]{Math.cos(h), Math.sin(h)}; }

    /**
     * Ein Punkt in Körperkoordinaten (s vorn, q Steuerbord, y oben) in der Welt, mit Kurs, Stampfen und Rollen.
     * Gleich der Modellmatrix aus {@link #model()}.
     */
    public double[] toWorld(double s, double q, double yy) {
        double[] m = model();
        // Modellkoordinaten: x = q, y = yy, z = −s
        double lx = q, ly = yy, lz = -s;
        return new double[]{m[0] * lx + m[4] * ly + m[8] * lz + m[12], m[1] * lx + m[5] * ly + m[9] * lz + m[13], m[2] * lx + m[6] * ly + m[10] * lz + m[14]};
    }

    /** Eine Richtung in Körperkoordinaten (s, q, y) in der Welt. */
    public double[] dirToWorld(double s, double q, double yy) {
        double[] m = model();
        double lx = q, ly = yy, lz = -s;
        return new double[]{m[0] * lx + m[4] * ly + m[8] * lz, m[1] * lx + m[5] * ly + m[9] * lz, m[2] * lx + m[6] * ly + m[10] * lz};
    }

    /**
     * Modellmatrix (Spalten zuerst) für Netze in Modellkoordinaten (x Steuerbord, y oben, z achteraus):
     * Verschiebung · Kurs · Stampfen · Rollen.
     */
    public double[] model() {
        double h = Math.toRadians(-heading), p = pitch, r = -roll;
        double ch = Math.cos(h), sh = Math.sin(h), cp = Math.cos(p), sp = Math.sin(p), cr = Math.cos(r), sr = Math.sin(r);
        // Ry(h) · Rx(p) · Rz(r)
        double[] ry = {ch, 0, -sh, 0, 0, 1, 0, 0, sh, 0, ch, 0, 0, 0, 0, 1};
        double[] rx = {1, 0, 0, 0, 0, cp, sp, 0, 0, -sp, cp, 0, 0, 0, 0, 1};
        double[] rz = {cr, sr, 0, 0, -sr, cr, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1};
        double[] m = mul(ry, mul(rx, rz));
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    static double[] mul(double[] a, double[] b) {
        double[] r = new double[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + rr] * b[c * 4 + k];
                r[c * 4 + rr] = s;
            }
        return r;
    }

    // ------------------------------------------------------------------ Physik

    /** Wasserhöhe und -geschwindigkeit an allen Säulen neu abfragen (einmal je Bild genügt). */
    public void sample(Sea sea) {
        double[] f = forward(), r = right();
        for (int i = 0; i < cs.length; i++) {
            double px = x + f[0] * cs[i] + r[0] * cq[i], pz = z + f[1] * cs[i] + r[1] * cq[i];
            wh[i] = sea.height(px, pz);
            ww[i] = sea.velocity(px, pz)[1];
        }
        sinceSample = 0;
    }

    /** Auf das ruhige Wasser setzen (Wasserlinie auf den Wasserspiegel, gerade, in Ruhe). */
    public void settle(Sea sea) {
        sample(sea);
        double m = 0, a = 0;
        for (int i = 0; i < cs.length; i++) { m += wh[i] * ca[i]; a += ca[i]; }
        y = m / a;
        vy = pitch = vPitch = roll = vRoll = 0;
    }

    /** Tauchen, Stampfen und Rollen um dt weiter (die Wasserwerte gelten seit dem letzten {@link #sample}). */
    public void integrate(double dt) {
        if (dt <= 0) return;
        int n = Math.max(1, (int) Math.ceil(dt / (1 / 120.0)));
        double h = dt / n;
        double mH = mass * (1 + addedHeave), ip = mass * kPitch * kPitch * (1 + addedInertia), ir = mass * kRoll * kRoll * (1 + addedInertia);
        double cP = 2 * zetaPitch * Math.sqrt(Math.max(1, kPitchStiff) * ip), cR = 2 * zetaRoll * Math.sqrt(Math.max(1, kRollStiff) * ir);
        double cPerA = cHeave / awp;
        for (int k = 0; k < n; k++) {
            sinceSample += h;
            double sp = Math.sin(pitch), cpp = Math.cos(pitch), sr = Math.sin(roll), crr = Math.cos(roll);
            double F = -mass * G, Mp = 0, Mr = 0;
            for (int i = 0; i < cs.length; i++) {
                double yc = y + cs[i] * sp - cq[i] * sr;
                double water = wh[i] + ww[i] * sinceSample;
                double d = Math.max(0, Math.min(ctop[i] - ckeel[i], water - (yc + ckeel[i])));
                double f = RHO * G * ca[i] * d;
                if (d > 0) {
                    double vyc = vy + cs[i] * cpp * vPitch - cq[i] * crr * vRoll;
                    // Dämpfung nur, so weit die Säule eintaucht (bis zur halben Tiefe voll)
                    double wet = Math.min(1, d / Math.max(0.1, -ckeel[i] * 0.5));
                    f -= cPerA * ca[i] * (vyc - ww[i]) * wet;
                }
                F += f;
                Mp += f * cs[i] * cpp;
                Mr -= f * cq[i] * crr;
            }
            // Gewichtsstabilität: Schwerpunkt über dem Auftriebsschwerpunkt wirkt aufrichtend nur, wenn er tiefer liegt
            Mp += mass * G * (cgY - kbY) * sp - mass * G * lcb * cpp;
            Mr += mass * G * (cgY - kbY) * sr;
            Mp -= cP * vPitch;
            Mr -= cR * vRoll + rollDrag * ir * vRoll * Math.abs(vRoll);
            vy += F / mH * h;
            vPitch += Mp / ip * h;
            vRoll += Mr / ir * h;
            y += vy * h;
            pitch += vPitch * h;
            roll += vRoll * h;
            x += vx * h;
            z += vz * h;
        }
        // Grenzen gegen Ausreißer (Kentern ist nicht vorgesehen)
        pitch = Math.max(-0.6, Math.min(0.6, pitch));
        roll = Math.max(-0.8, Math.min(0.8, roll));
        if (!Double.isFinite(y)) { y = 0; vy = 0; }
    }

    /** Wasserstand über dem Kiel an der Mitte (m), für Anzeige und Prüfung. */
    public double immersion() {
        double m = 0, a = 0;
        for (int i = 0; i < cs.length; i++) { m += (wh[i] - y) * ca[i]; a += ca[i]; }
        return m / a;
    }

    /** Mittlere Wasserhöhe unter dem Körper seit dem letzten {@link #sample}. */
    public double meanWater() {
        double m = 0, a = 0;
        for (int i = 0; i < cs.length; i++) { m += wh[i] * ca[i]; a += ca[i]; }
        return m / a;
    }
}
