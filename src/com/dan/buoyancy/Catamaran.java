package com.dan.buoyancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Seit Glacier Phase 10: ein Ausflugskatamaran wie vor den Gletschern am Lago Argentino. Zwei schlanke Rümpfe, darüber
 * das Brückendeck mit dem Salon (Fenster rundum) und oben das offene Sonnendeck mit Geländer, von dem die Leute die Front
 * sehen. Maße [G]: 26 × 9,2 m, Rümpfe je 2,3 m breit mit 6,9 m Abstand der Mitten, Tiefgang 1,4 m.
 * <p>
 * Schwimmen wie jeder {@link Floater} (Säulen in beiden Rümpfen, der weite Abstand gibt die große Formstabilität eines
 * Katamarans); dazu die Fahrt eine Route entlang, Wegpunkt für Wegpunkt mit eigener Geschwindigkeit, und die waagerechte
 * Strömung aus {@link Sea#velocity} (die lange Welle eines Abbruchs schiebt das Boot). {@link #step} rechnet alles.
 */
public final class Catamaran extends Floater {
    /** Länge, Breite über alles, Abstand der Rumpfmitten, Breite eines Rumpfs, Tiefgang, Höhe des Brückendecks. */
    public final double length, beam, hullSpacing, hullBeam, draftDesign, deck;
    /** Höhe des Sonnendecks und seines Geländers über der Wasserlinie (m). */
    public final double sunDeck, rail;
    /** Wegpunkte {x, z, Fahrt m/s} (die Fahrt gilt auf dem Weg zu diesem Punkt). */
    public final List<double[]> route = new ArrayList<>();
    /** Größte Drehrate (Grad/s) und wie stark die Fahrt mit der Strömung treibt (0..1). */
    public double turnRate = 4, drift = 1;
    /** Wie oft das Wasser unter den Säulen neu abgefragt wird (s); dazwischen rechnet {@link Floater} mit der Steiggeschwindigkeit weiter. */
    public double sampleInterval = 0.05;
    private double sampleClock;
    private int leg = 1;
    private boolean done;
    private double speedNow, travelled, time, maxHeave, minHeave, maxRoll, maxPitch;
    private double y0 = Double.NaN;

    public Catamaran(String name) { this(name, 26, 9.2, 6.9, 2.3, 1.4); }

    public Catamaran(String name, double length, double beam, double hullSpacing, double hullBeam, double draft) {
        super(name, columns(length, hullSpacing, hullBeam, draft), 2.2, 0.27 * length, 0.42 * beam);
        this.length = length;
        this.beam = beam;
        this.hullSpacing = hullSpacing;
        this.hullBeam = hullBeam;
        this.draftDesign = draft;
        this.deck = 1.7;
        this.sunDeck = 4.6;
        this.rail = sunDeck + 1.05;
        zetaHeave = 0.3;
        zetaPitch = 0.3;
        zetaRoll = 0.2;
        addedHeave = 0.7;
        rollDrag = 4;
    }

    /** Säulen: je Rumpf 12 Stationen, vorn und hinten schmaler und flacher. */
    static double[][] columns(double length, double spacing, double hb, double draft) {
        int ns = 12;
        double ds = length / ns;
        List<double[]> l = new ArrayList<>();
        for (int side = -1; side <= 1; side += 2)
            for (int i = 0; i < ns; i++) {
                double s = -length / 2 + ds * (i + 0.5), u = s / (length / 2);
                double w = hb * (u > 0.4 ? Math.sqrt(Math.max(0.05, 1 - Math.pow((u - 0.4) / 0.62, 2))) : u < -0.85 ? 0.85 : 1);
                double t = draft * (u > 0.6 ? 1 - 0.4 * (u - 0.6) / 0.4 : 1);
                l.add(new double[]{s, side * spacing / 2, ds * w * 0.85, -t, 1.7 + 0.6});
            }
        return l.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------ Fahrt

    /** Ein Wegpunkt mit der Fahrt dorthin (m/s). */
    public Catamaran via(double x, double z, double speed) { route.add(new double[]{x, z, speed}); return this; }

    /** Am ersten Wegpunkt beginnen, Kurs auf den zweiten, auf das Wasser gesetzt. */
    public void start(Sea sea) {
        if (route.size() < 2) throw new IllegalStateException("Route braucht zwei Punkte");
        double[] a = route.get(0), b = route.get(1);
        x = a[0];
        z = a[1];
        heading = (Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1]))) + 360) % 360;
        leg = 1;
        done = false;
        speedNow = 0;
        travelled = time = 0;
        vx = vz = 0;
        settle(sea);
        y0 = y;
        maxHeave = minHeave = maxRoll = maxPitch = 0;
    }

    /** Am ersten Wegpunkt festmachen (auf dem Wasser, ohne Fahrt): die Route beginnt erst mit {@link #start}. */
    public void moor(Sea sea) {
        start(sea);
        done = true;
        leg = route.size() - 1;
    }

    public boolean done() { return done; }
    /** Nummer des Wegpunkts, auf den es gerade zuhält. */
    public int leg() { return leg; }
    public double travelled() { return travelled; }
    public double speed() { return speedNow; }

    /** Abstand bis zum Ende der Route (m). */
    public double remaining() {
        if (done) return 0;
        double r = Math.hypot(route.get(leg)[0] - x, route.get(leg)[1] - z);
        for (int i = leg; i + 1 < route.size(); i++) r += Math.hypot(route.get(i + 1)[0] - route.get(i)[0], route.get(i + 1)[1] - route.get(i)[1]);
        return r;
    }

    /** Fahren, Wasser abfragen, Tauchen, Stampfen, Rollen (dt s). */
    public void step(Sea sea, double dt) {
        if (dt <= 0) return;
        time += dt;
        double want = 0;
        if (!done) {
            double[] t = route.get(leg);
            double dx = t[0] - x, dz = t[1] - z, d = Math.hypot(dx, dz);
            // Wegpunkt erreicht: früher einlenken, je schneller, desto früher
            if (d < Math.max(15, speedNow * 6)) {
                if (++leg >= route.size()) { done = true; leg = route.size() - 1; }
                else { t = route.get(leg); dx = t[0] - x; dz = t[1] - z; }
            }
            if (!done) {
                want = t.length > 2 ? t[2] : 5;
                double h = Math.toDegrees(Math.atan2(dx, -dz));
                double diff = ((h - heading) % 360 + 540) % 360 - 180;
                heading = (heading + Math.max(-turnRate * dt, Math.min(turnRate * dt, diff)) + 360) % 360;
                // in engen Kurven langsamer
                want *= 1 - 0.4 * Math.min(1, Math.abs(diff) / 90);
            }
        }
        // Fahrt nimmt in einigen Sekunden zu oder ab
        speedNow += (want - speedNow) * Math.min(1, dt / 6);
        double[] f = forward(), cur = sea.velocity(x, z);
        vx = f[0] * speedNow + drift * cur[0];
        vz = f[1] * speedNow + drift * (cur.length > 2 ? cur[2] : 0);
        travelled += speedNow * dt;
        sampleClock += dt;
        if (sampleClock >= sampleInterval) { sampleClock = 0; sample(sea); }
        integrate(dt);
        double h = y - (Double.isNaN(y0) ? y : y0);
        maxHeave = Math.max(maxHeave, h);
        minHeave = Math.min(minHeave, h);
        maxRoll = Math.max(maxRoll, Math.abs(roll));
        maxPitch = Math.max(maxPitch, Math.abs(pitch));
    }

    /** Hub über der Ruhelage seit dem Start: größter und kleinster (m), größtes Rollen und Stampfen (Grad). */
    public double[] motion() { return new double[]{maxHeave, minHeave, Math.toDegrees(maxRoll), Math.toDegrees(maxPitch)}; }
    public void resetMotion() { maxHeave = minHeave = maxRoll = maxPitch = 0; y0 = y; }

    /** Auge eines Gasts auf dem Sonnendeck: vorn an Backbord (zur Front hin, wenn sie links liegt), 1,6 m über dem Deck. */
    public double[] eye(boolean port) { return toWorld(length * 0.18, (port ? -1 : 1) * (beam / 2 - 1.1), sunDeck + 1.6); }

    /**
     * Die Pose für die Mitfahrt der Kamera: {x, y, z, Kurs, Stampfen in Grad (Bug hoch positiv), Rollen in Grad}.
     */
    public double[] pose(boolean port) {
        double[] e = eye(port);
        return new double[]{e[0], e[1], e[2], heading, Math.toDegrees(pitch), Math.toDegrees(roll)};
    }

    /** „Katamaran: 4,2 m/s, Kurs 213°, Wegpunkt 3 von 7 · 820 m bis zum Ende · Hub +0,6/−0,4 m, Rollen 3,1°, Stampfen 2,0°“ */
    public String describe() {
        double[] m = motion();
        return String.format(Locale.GERMAN, "%s: %.1f m/s, Kurs %.0f°, %s · %,.0f m gefahren · Hub %+.2f/%+.2f m, Rollen %.1f°, Stampfen %.1f°",
                name, speedNow, heading, done ? "am Ziel" : String.format(Locale.GERMAN, "Wegpunkt %d von %d, noch %,.0f m", leg + 1, route.size(), remaining()),
                travelled, m[0], m[1], m[2], m[3]);
    }
}
