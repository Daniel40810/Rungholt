package com.dan.buoyancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Seit Rungholt Phase 11: eine Kogge, das Handelsschiff der Hanse und der Nordseeküste im 14. Jahrhundert. Maße nach
 * der Bremer Kogge von 1380: 23,3 m lang, 7,6 m breit, 2,25 m Tiefgang, ein Mast mit Rahsegel, Achterkastell. Ein
 * flacher Boden, damit sie im Watt und im Sielhafen bei Ebbe trockenfallen kann und aufrecht auf dem Grund sitzt.
 * <p>
 * Schwimmen wie jeder {@link Floater} (drei Säulenreihen über die Breite für die Formstabilität). Dazu die Fahrt eine
 * Route entlang wie der {@link Catamaran}, das Segel (0 = an der Rah aufgetucht, 1 = gesetzt) nach der Fahrt, und das
 * <b>Trockenfallen</b>: Liegt der Grund ({@link Sea#bed}) höher als der Kiel, sitzt die Kogge auf, wird gerade und bewegt
 * sich nicht mehr, bis das Wasser sie wieder trägt.
 */
public final class Cog extends Floater {
    /** Länge, Breite, Tiefgang, Höhe des Schanzkleids mittschiffs über der Wasserlinie, Masthöhe über der Wasserlinie. */
    public final double length, beam, draftDesign, freeboard, mastHeight;
    /** Wegpunkte {x, z, Fahrt m/s}. */
    public final List<double[]> route = new ArrayList<>();
    /** Größte Drehrate (Grad/s), Mitnahme durch die Strömung (0..1). */
    public double turnRate = 3, drift = 0.6;
    /** Segel 0 (aufgetucht) .. 1 (gesetzt); es folgt {@link #sailTarget} in rund 20 s. */
    public double sail, sailTarget;
    public double sampleInterval = 0.1;
    private double sampleClock, speedNow, travelled;
    private int leg = 1;
    private boolean done = true, grounded;

    public Cog(String name) { this(name, 23.3, 7.6, 2.25); }

    public Cog(String name, double length, double beam, double draft) {
        super(name, columns(length, beam, draft), 0.0, 0.27 * length, 0.36 * beam);   // Schwerpunkt auf der Wasserlinie (Ballast im flachen Boden)
        this.length = length;
        this.beam = beam;
        this.draftDesign = draft;
        this.freeboard = 2.0;
        this.mastHeight = 24;
        zetaHeave = 0.35;
        zetaPitch = 0.35;
        zetaRoll = 0.15;
        rollDrag = 6;
    }

    /** Halbe Breite der Wasserlinie an der Stelle u = s / (L/2) (−1 achtern … 1 vorn): völlig, an den Enden spitz. */
    public static double halfBeam(double u, double beam) {
        double a = Math.abs(u);
        return beam / 2 * Math.sqrt(Math.max(0.0, 1 - Math.pow(a, 3.2))) + 0.08;
    }

    /** Säulen: 14 Stationen, je drei über die Breite (Steuerbord, Mitte, Backbord); flacher Boden, Enden flacher. */
    static double[][] columns(double length, double beam, double draft) {
        int ns = 14;
        double ds = length / ns;
        List<double[]> l = new ArrayList<>();
        for (int i = 0; i < ns; i++) {
            double s = -length / 2 + ds * (i + 0.5), u = s / (length / 2);
            double w = halfBeam(u, beam) * 2, t = draft * (1 - 0.35 * Math.pow(Math.abs(u), 3));
            for (int k = -1; k <= 1; k++) l.add(new double[]{s, k * w / 3, ds * w / 3 * 0.9, -t, 2.0 + 0.9 * u * u});
        }
        return l.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------ Fahrt

    public Cog via(double x, double z, double speed) { route.add(new double[]{x, z, speed}); return this; }

    /** Ohne Fahrt an einen Ort legen (Kurs in Grad), aufs Wasser oder auf den Grund. */
    public void place(Sea sea, double px, double pz, double headingDeg) {
        x = px;
        z = pz;
        heading = headingDeg;
        vx = vz = 0;
        speedNow = 0;
        done = true;
        settle(sea);
        ground(sea);
    }

    /** Die Route vom ersten Punkt an abfahren (Kurs auf den zweiten). */
    public void start(Sea sea) {
        if (route.size() < 2) throw new IllegalStateException("Route braucht zwei Punkte");
        double[] a = route.get(0), b = route.get(1);
        place(sea, a[0], a[1], (Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1]))) + 360) % 360);
        leg = 1;
        done = false;
        travelled = 0;
    }

    /** Die Route ab dem Ort weiterfahren, an dem das Schiff gerade ist (für die Rückfahrt). */
    public void go() {
        if (route.size() < 2) return;
        leg = 1;
        done = false;
    }

    public boolean done() { return done; }
    public boolean grounded() { return grounded; }
    public int leg() { return leg; }
    public double speed() { return speedNow; }
    public double travelled() { return travelled; }

    /** Abstand bis zum Ende der Route (m). */
    public double remaining() {
        if (done) return 0;
        double r = Math.hypot(route.get(leg)[0] - x, route.get(leg)[1] - z);
        for (int i = leg; i + 1 < route.size(); i++) r += Math.hypot(route.get(i + 1)[0] - route.get(i)[0], route.get(i + 1)[1] - route.get(i)[1]);
        return r;
    }

    /** Wasser unter dem Kiel mittschiffs (m; negativ: der Grund liegt höher als der Kiel). */
    public double underKeel(Sea sea) { return sea.height(x, z) - sea.bed(x, z) - draftDesign; }

    /** Fahren, Segel, Wasser abfragen, Tauchen, Stampfen, Rollen, Aufsitzen (dt s). */
    public void step(Sea sea, double dt) {
        if (dt <= 0) return;
        double want = 0;
        if (!done) {
            double[] t = route.get(leg);
            double dx = t[0] - x, dz = t[1] - z, d = Math.hypot(dx, dz);
            boolean last = leg == route.size() - 1;
            if (d < (last ? 4 : Math.max(12, speedNow * 8))) {
                if (++leg >= route.size()) { done = true; leg = route.size() - 1; }
                else { t = route.get(leg); dx = t[0] - x; dz = t[1] - z; d = Math.hypot(dx, dz); }
            }
            if (!done) {
                want = t.length > 2 ? t[2] : 2;
                if (leg == route.size() - 1) want = Math.min(want, Math.max(0.3, d / 25));   // zum Liegeplatz langsam
                double h = Math.toDegrees(Math.atan2(dx, -dz));
                double diff = ((h - heading) % 360 + 540) % 360 - 180;
                if (!grounded) heading = (heading + Math.max(-turnRate * dt, Math.min(turnRate * dt, diff)) + 360) % 360;
                want *= 1 - 0.5 * Math.min(1, Math.abs(diff) / 90);
            }
        }
        if (grounded) want = 0;
        speedNow += (want - speedNow) * Math.min(1, dt / 15);
        sailTarget = done || speedNow < 0.2 ? 0 : 1;
        sail += Math.max(-dt / 20, Math.min(dt / 20, sailTarget - sail));
        double[] f = forward(), cur = sea.velocity(x, z);
        double dr = grounded ? 0 : drift * (done ? 0 : 1);
        vx = f[0] * speedNow + dr * cur[0];
        vz = f[1] * speedNow + dr * (cur.length > 2 ? cur[2] : 0);
        travelled += speedNow * dt;
        // in Schritten zu höchstens 1 s, damit die Fahrt um Ecken stimmt
        int n = Math.max(1, (int) Math.ceil(dt / 1.0));
        for (int k = 0; k < n; k++) {
            sampleClock += dt / n;
            if (sampleClock >= sampleInterval) { sampleClock = 0; sample(sea); }
            integrate(dt / n);
            ground(sea);
        }
    }

    /** Aufsitzen: liegt der Kiel unter dem Grund, steht die Kogge auf ihm, gerade und ruhig. */
    private void ground(Sea sea) {
        double[] f = forward();
        double bed = Math.max(sea.bed(x, z), Math.max(sea.bed(x + f[0] * length * 0.3, z + f[1] * length * 0.3), sea.bed(x - f[0] * length * 0.3, z - f[1] * length * 0.3)));
        double keel = y - draftDesign;
        if (keel <= bed + 1e-3) {
            y = bed + draftDesign;
            vy = Math.max(0, vy);
            pitch *= 0.9;
            roll *= 0.9;
            vPitch = vRoll = 0;
            grounded = sea.height(x, z) < bed + draftDesign * 0.92;
        } else grounded = false;
    }

    /** „Kogge 23,3 m · 6 % gesetzt · 1,4 m/s · 1,2 m unter dem Kiel“ */
    public String describe(Sea sea) {
        return String.format(Locale.GERMAN, "%s %.1f m · %s · Segel %.0f %% · %.1f m/s · %s", name, length,
                grounded ? "sitzt auf dem Grund" : done ? "liegt" : "fährt", sail * 100, speedNow,
                String.format(Locale.GERMAN, "%.1f m Wasser unter dem Kiel", Math.max(0, underKeel(sea))));
    }
}
