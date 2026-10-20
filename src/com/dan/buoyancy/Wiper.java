package com.dan.buoyancy;

/**
 * Ein Scheibenwischer: Arm um einen Drehpunkt an der Unterkante der Scheibe, Wischblatt auf dem äußeren Teil des Arms.
 * Er schwingt weich zwischen zwei Endlagen hin und her (eine Periode = hin und zurück) und parkt links unten.
 * Koordinaten auf der Scheibe in m: u von links, v von unten.
 */
public final class Wiper {
    public double pivotU, pivotV, arm = 0.95, bladeFrom = 0.25, sweepDeg = 110, cycleSeconds = 1.6;
    public boolean on;
    private double phase, angle;

    public Wiper(double pivotU, double pivotV, double arm) { this.pivotU = pivotU; this.pivotV = pivotV; this.arm = arm; angle = parked(); }

    /** Winkel in der Endlage links (Grad, 0 = senkrecht nach oben, positiv nach rechts). */
    public double parked() { return -sweepDeg / 2; }

    /** Um dt weiter; gibt {alter Winkel, neuer Winkel} zurück. */
    public double[] step(double dt) {
        double before = angle;
        if (on || Math.abs(angle - parked()) > 0.5) {
            phase += dt / cycleSeconds;
            if (!on && phase % 1 > 0.9) phase = Math.ceil(phase);   // nach dem Ausschalten bis zur Endlage
            angle = parked() + sweepDeg * (0.5 - 0.5 * Math.cos(2 * Math.PI * phase));
        }
        return new double[]{before, angle};
    }

    public double angle() { return angle; }

    /** Das Wischblatt beim Winkel a: {u0, v0, u1, v1}. */
    public double[] blade(double aDeg) {
        double a = Math.toRadians(aDeg), su = Math.sin(a), cv = Math.cos(a);
        return new double[]{pivotU + su * arm * bladeFrom, pivotV + cv * arm * bladeFrom, pivotU + su * arm, pivotV + cv * arm};
    }

    /**
     * Punkte, die das Blatt zwischen zwei Winkeln überstreicht, im Abstand step (m), als {u0, v0, u1, v1, …}. Damit
     * wischt die App ihre Tropfen weg.
     */
    public double[] sweep(double a0, double a1, double step) {
        int nr = Math.max(2, (int) Math.ceil(arm * (1 - bladeFrom) / step) + 1);
        double arc = Math.abs(Math.toRadians(a1 - a0)) * arm;
        int na = Math.max(1, (int) Math.ceil(arc / step));
        double[] out = new double[(na + 1) * nr * 2];
        int o = 0;
        for (int i = 0; i <= na; i++) {
            double a = Math.toRadians(a0 + (a1 - a0) * i / na);
            for (int j = 0; j < nr; j++) {
                double r = arm * (bladeFrom + (1 - bladeFrom) * j / (nr - 1));
                out[o++] = pivotU + Math.sin(a) * r;
                out[o++] = pivotV + Math.cos(a) * r;
            }
        }
        return out;
    }
}
