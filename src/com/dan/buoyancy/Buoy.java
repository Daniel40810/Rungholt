package com.dan.buoyancy;

import java.util.List;

/**
 * Eine Leuchttonne nach IALA, Region A (Deutsche Bucht): Schwimmkörper von 2,4 m Durchmesser mit 1,1 m Tiefgang,
 * darauf ein Gittergerüst mit Feuer und Toppzeichen. Verankert an einer Kette (waagerecht eine weiche Feder mit
 * rund 40 s Eigenperiode); Strom und Wind ziehen sie ein paar Meter aus der Mitte. Der Ballast liegt tief, darum
 * richtet sie sich schnell auf (Rollperiode gut 3 s) und folgt den Wellenhängen.
 */
public final class Buoy extends Floater {

    /** Art der Tonne mit Farbe, Toppzeichen und üblicher Kennung. */
    public enum Kind {
        BACKBORD("Backbordtonne", "Fl R 4s"), STEUERBORD("Steuerbordtonne", "Fl G 4s"), ANSTEUERUNG("Ansteuerungstonne", "Iso 8s"), NORD("Kardinaltonne Nord", "Q");
        public final String label, defaultLight;
        Kind(String l, String d) { label = l; defaultLight = d; }
    }

    public final Kind kind;
    /** Aufschrift (Nummer oder Name) und Kennung des Feuers. */
    public final String mark;
    public final Rhythm rhythm;
    /** Ankerpunkt in der Welt (x, z). */
    public double anchorX, anchorZ;
    /** Lichtstärke des Feuers in cd (rund 3 sm Tragweite) und Phase im Takt (s). */
    public double candela = 12, phase;

    /** Durchmesser, Tiefgang, Freibord des Schwimmkörpers; Höhe des Feuers über der Wasserlinie. */
    public static final double D = 2.4, DRAFT = 1.1, FREEBOARD = 1.0, LIGHT_Y = 5.2, TOP_Y = 6.4;

    public Buoy(Kind kind, String mark, String light, double x, double z) {
        super(kind.label + " " + mark, buoyColumns(), -0.85, 1.0, 1.0);
        this.kind = kind;
        this.mark = mark;
        this.rhythm = new Rhythm(light != null ? light : kind.defaultLight);
        this.anchorX = this.x = x;
        this.anchorZ = this.z = z;
        zetaRoll = 0.12;
        zetaPitch = 0.12;
        addedHeave = 0.5;
    }

    /** Säulen: Mitte und sechs im Ring, gleiche Fläche, Kiel −1,1 m, Oberkante +1,0 m. */
    static double[][] buoyColumns() {
        double a = Math.PI * D * D / 4 / 7, r = D * 0.31;
        double[][] c = new double[7][];
        c[0] = new double[]{0, 0, a, -DRAFT, FREEBOARD};
        for (int i = 0; i < 6; i++) {
            double t = Math.PI / 3 * i;
            c[i + 1] = new double[]{Math.cos(t) * r, Math.sin(t) * r, a, -DRAFT, FREEBOARD};
        }
        return c;
    }

    /** Verankerung, Strom und Wind waagerecht (vor {@link #integrate}). */
    void moor(Sea sea, double windSpeed, double windFromDeg, double dt) {
        double[] u = sea.velocity(x, z);
        double k = mass() * Math.pow(2 * Math.PI / 40, 2), c = 2 * 0.5 * Math.sqrt(k * mass());
        double wa = Math.toRadians(windFromDeg + 180);
        double fw = 0.5 * 1.25 * 1.2 * 3.5 * windSpeed * windSpeed;   // Wind auf 3,5 m² Gerüst
        double cd = 0.5 * RHO * 1.0 * D * DRAFT;                        // Strömungswiderstand des Körpers
        double rx = u[0] - vx, rz = u[2] - vz, rl = Math.hypot(rx, rz);
        double fx = -k * (x - anchorX) - c * vx + cd * rx * rl + fw * Math.sin(wa);
        double fz = -k * (z - anchorZ) - c * vz + cd * rz * rl - fw * Math.cos(wa);
        vx += fx / (mass() * 1.5) * dt;
        vz += fz / (mass() * 1.5) * dt;
        // langsam drehen
        heading = (heading + dt * 3 * Math.sin(0.05 * x + 0.07 * z + 0.02 * anchorX)) % 360;
    }

    /** Das Feuer zur Zeit t (s): Ort in der Welt und Lichtstärke je Farbe (cd); leer, wenn es aus ist. */
    public List<LightPoint> lights(double t, boolean on) {
        if (!on) return List.of();
        double lv = rhythm.level(t + phase);
        if (lv <= 0) return List.of();
        double[] p = toWorld(0, 0, LIGHT_Y), c = rhythm.rgb();
        return List.of(new LightPoint(name, p[0], p[1], p[2], c[0] * candela * lv, c[1] * candela * lv, c[2] * candela * lv));
    }

    /** Leuchtet das Feuer gerade (0..1)? */
    public double lightLevel(double t) { return rhythm.level(t + phase); }
}
