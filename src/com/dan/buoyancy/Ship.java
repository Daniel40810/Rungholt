package com.dan.buoyancy;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein Küstenmotorschiff (Kümo) von 82 m mit dem Deckshaus achtern, fährt eine Route ab und trägt Positionslichter nach
 * den Kollisionsverhütungsregeln (KVR/COLREG, Fahrzeug ab 50 m): zwei weiße Topplichter (das achtere höher) über
 * 225°, Seitenlichter rot an Backbord und grün an Steuerbord über je 112,5°, das weiße Hecklicht über 135°. Die
 * Lichtstärken folgen der Formel aus Anhang I für 6 sm (Topplicht) und 3 sm (Seiten- und Hecklicht).
 * <p>
 * Die Brücke ist ein Standort für die Kamera: Auge, Blickrichtung und die drei Frontscheiben in Körperkoordinaten,
 * vor der mittleren ein Scheibenwischer.
 */
public final class Ship extends Floater {
    /** Länge, Breite, Tiefgang, Freibord (Deck über der Wasserlinie mittschiffs). */
    public final double length, beam, draftDesign, freeboard;
    /** Fahrt durchs Wasser (m/s) und größte Drehrate (Grad/s). */
    public double speed = 6.2, turnRate = 1.2;
    /** Route als Wegpunkte {x, z}; ist der letzte erreicht, ist die Fahrt zu Ende. */
    public final List<double[]> route = new ArrayList<>();
    private int leg = 1;
    private boolean done;
    /** Die drei Frontscheiben der Brücke: Mitte (s, q), Breite, Unter- und Oberkante (y), alle in m. */
    public final double[][] windows;
    /** Auge auf der Brücke (s, q, y). */
    public final double[] bridgeEye;
    public final Wiper wiper;
    private double yawPhase;
    /** Lichter: Name, Ort (s, q, y), Farbe, cd, Sektor von–bis (Grad relativ zum Bug, Steuerbord positiv). */
    public record Light(String name, double s, double q, double y, double[] rgb, double candela, double from, double to) { }
    public final List<Light> lights = new ArrayList<>();

    public Ship(String name, double length, double beam, double draft, double freeboard) {
        super(name, columns(length, beam, draft, freeboard), -draft / 2 + 1.5, 0.25 * length, 0.38 * beam);
        this.length = length;
        this.beam = beam;
        this.draftDesign = draft;
        this.freeboard = freeboard;
        zetaRoll = 0.06;
        zetaPitch = 0.25;
        zetaHeave = 0.25;
        addedHeave = 0.8;
        double front = houseFront(), floor = bridgeFloor();
        bridgeEye = new double[]{front - 1.7, 0, floor + 1.7};
        windows = new double[][]{{front, -2.55, 2.3, floor + 1.05, floor + 2.35}, {front, 0, 2.6, floor + 1.05, floor + 2.35}, {front, 2.55, 2.3, floor + 1.05, floor + 2.35}};
        wiper = new Wiper(1.3, 0.05, 0.92);
        double mast = 0.38 * length, aft = houseFront() - 4;
        lights.add(new Light("Topplicht vorn", mast, 0, freeboard + 10.5, new double[]{1, 0.92, 0.8}, colregCd(6), -112.5, 112.5));
        lights.add(new Light("Topplicht achtern", aft, 0, floor + 7.0, new double[]{1, 0.92, 0.8}, colregCd(6), -112.5, 112.5));
        lights.add(new Light("Seitenlicht Steuerbord", front - 1, beam / 2 + 0.6, floor + 0.8, new double[]{0.05, 1, 0.45}, colregCd(3), 0, 112.5));
        lights.add(new Light("Seitenlicht Backbord", front - 1, -beam / 2 - 0.6, floor + 0.8, new double[]{1, 0.08, 0.05}, colregCd(3), -112.5, 0));
        lights.add(new Light("Hecklicht", -length / 2 + 0.6, 0, freeboard + 2.2, new double[]{1, 0.92, 0.8}, colregCd(3), 112.5, 247.5));
    }

    /** Ein Kümo wie auf der Weser: 82 × 12,5 m, Tiefgang 4,6 m, Freibord 2,6 m. */
    public static Ship coaster(String name) { return new Ship(name, 82, 12.5, 4.6, 2.6); }

    /** Vorderkante des Deckshauses (s) und Boden des Steuerhauses (y). */
    public double houseFront() { return -0.24 * length; }
    public double houseBack() { return -0.37 * length; }
    public double bridgeFloor() { return freeboard + 6.2; }

    /**
     * Lichtstärke nach KVR Anhang I: I = 3,43·10⁶ · T · D² · K^(−D) mit T = 2·10⁻⁷ lx und K = 0,8 (Sicht rund 13 sm).
     */
    public static double colregCd(double nm) { return 3.43e6 * 2e-7 * nm * nm * Math.pow(0.8, -nm); }

    /** Halbe Breite der Wasserlinie bei s (m): voll mittschiffs, zum Bug elliptisch schmal, achtern etwas eingezogen. */
    public static double halfBeam(double s, double length, double beam) {
        double u = s / (length / 2);
        double b = beam / 2;
        if (u > 0.3) b *= Math.sqrt(Math.max(0, 1 - Math.pow((u - 0.3) / 0.72, 2)));
        else if (u < -0.65) b *= 1 - 0.3 * Math.pow((-u - 0.65) / 0.35, 1.5);
        return Math.max(0, b);
    }

    /** Tiefgang bei s: an den Enden flacher (Bugaufkimmung, Heck über dem Ruder). */
    public static double draftAt(double s, double length, double draft) {
        double u = s / (length / 2);
        return draft * (u > 0.75 ? 1 - 0.35 * (u - 0.75) / 0.25 : u < -0.8 ? 1 - 0.45 * (-u - 0.8) / 0.2 : 1);
    }

    /** Säulen: 14 Stationen längs, je drei quer. */
    static double[][] columns(double length, double beam, double draft, double freeboard) {
        int ns = 14;
        List<double[]> l = new ArrayList<>();
        double ds = length / ns;
        for (int i = 0; i < ns; i++) {
            double s = -length / 2 + ds * (i + 0.5), hb = halfBeam(s, length, beam);
            if (hb < 0.2) continue;
            double t = draftAt(s, length, draft), w = 2 * hb / 3;
            // Oberkante mit Reserve: Lukensülle, Back und Deckshaus tragen mit, wenn Wasser übers Deck geht
            for (int k = -1; k <= 1; k++) l.add(new double[]{s, k * w, ds * w, -t, freeboard + 4.5});
        }
        return l.toArray(new double[0][]);
    }

    // ------------------------------------------------------------------ Fahrt

    /** Am ersten Wegpunkt beginnen, Kurs auf den zweiten. */
    public void start() {
        if (route.size() < 2) throw new IllegalStateException("Route braucht zwei Punkte");
        double[] a = route.get(0), b = route.get(1);
        x = a[0];
        z = a[1];
        heading = Math.toDegrees(Math.atan2(b[0] - a[0], -(b[1] - a[1])));
        heading = (heading + 360) % 360;
        leg = 1;
        done = false;
    }

    /** Ist die Route zu Ende? */
    public boolean done() { return done; }

    /** Abstand zum Ende der Route (m). */
    public double remaining() {
        if (done) return 0;
        double r = Math.hypot(route.get(leg)[0] - x, route.get(leg)[1] - z);
        for (int i = leg; i + 1 < route.size(); i++) r += Math.hypot(route.get(i + 1)[0] - route.get(i)[0], route.get(i + 1)[1] - route.get(i)[1]);
        return r;
    }

    /** Steuern: Kurs auf den nächsten Wegpunkt mit begrenzter Drehrate, dazu ein leichtes Gieren im Seegang. */
    void steer(double dt, double seaState) {
        if (done) { vx = vz = 0; return; }
        double[] t = route.get(leg);
        double dx = t[0] - x, dz = t[1] - z;
        if (Math.hypot(dx, dz) < Math.max(40, speed * 8)) {
            if (++leg >= route.size()) { done = true; vx = vz = 0; return; }
            t = route.get(leg);
            dx = t[0] - x;
            dz = t[1] - z;
        }
        double want = Math.toDegrees(Math.atan2(dx, -dz));
        double diff = ((want - heading) % 360 + 540) % 360 - 180;
        yawPhase += dt;
        double yaw = seaState * 1.5 * Math.sin(yawPhase * 2 * Math.PI / 11) + seaState * 0.6 * Math.sin(yawPhase * 2 * Math.PI / 4.3 + 1);
        heading = (heading + Math.max(-turnRate * dt, Math.min(turnRate * dt, diff + yaw * dt)) + 360) % 360;
        double v = speed * (1 - 0.05 * Math.max(0, seaState * 10 - 4));
        double[] f = forward();
        vx = f[0] * v;
        vz = f[1] * v;
    }

    // ------------------------------------------------------------------ Lichter

    /**
     * Die Lichter, die man vom Auge aus sieht, mit ihrer Stärke in diese Richtung (cd je Farbe). Die Sektoren schneiden
     * über 2° weich ab (KVR: höchstens 3°).
     */
    public List<LightPoint> lights(double ex, double ey, double ez) {
        List<LightPoint> out = new ArrayList<>();
        double[] f = forward(), r = right();
        for (Light l : lights) {
            double[] p = toWorld(l.s(), l.q(), l.y());
            double dx = ex - p[0], dz = ez - p[2];
            double rel = Math.toDegrees(Math.atan2(dx * r[0] + dz * r[1], dx * f[0] + dz * f[1]));
            double k = sector(rel, l.from(), l.to());
            if (k <= 0) continue;
            double cd = l.candela() * k;
            out.add(new LightPoint(name + " · " + l.name(), p[0], p[1], p[2], l.rgb()[0] * cd, l.rgb()[1] * cd, l.rgb()[2] * cd));
        }
        return out;
    }

    /** Anteil 0..1 eines Sektors von–bis (Grad, im Uhrzeigersinn) beim Winkel rel, Rand 2° weich. */
    public static double sector(double rel, double from, double to) {
        double span = ((to - from) % 360 + 360) % 360;
        double d = ((rel - from) % 360 + 360) % 360;
        double in = Math.min(d, span - d);
        if (d > span) in = -Math.min(d - span, 360 - d);
        return Math.max(0, Math.min(1, 0.5 + in / 2.0));
    }

    /** Auge auf der Brücke in der Welt, dazu vorwärts, oben und Steuerbord (mit Stampfen und Rollen): {Auge, vorn, oben, rechts}. */
    public double[][] bridgeView() {
        double[] e = toWorld(bridgeEye[0], bridgeEye[1], bridgeEye[2]);
        return new double[][]{e, dirToWorld(1, 0, 0), dirToWorld(0, 0, 1), dirToWorld(0, 1, 0)};
    }

    /**
     * Modellmatrix für das Netz des Wischers ({@link Mesh#wiper}): an der mittleren Frontscheibe, 6 cm vor dem Glas,
     * um den Drehpunkt auf den laufenden Winkel gedreht. Scheibenkoordinaten u, v, w → Modell x, y, −z.
     */
    public double[] wiperModel() {
        double[] w = windows[1];
        double px = w[1] - w[2] / 2 + wiper.pivotU, py = w[3] + wiper.pivotV, pz = -w[0] - 0.06;
        double a = Math.toRadians(wiper.angle()), c = Math.cos(a), sn = Math.sin(a);
        // Rz(−a) · S(1, 1, −1), dann verschoben
        double[] local = {c, -sn, 0, 0, sn, c, 0, 0, 0, 0, -1, 0, px, py, pz, 1};
        return mul(model(), local);
    }

    /**
     * Eine Frontscheibe in der Welt: vier Ecken (unten links, unten rechts, oben rechts, oben links, von innen gesehen),
     * Normale nach außen und die Richtung „rechts“ auf der Scheibe.
     */
    public double[][] windowWorld(int i) {
        double[] w = windows[i];
        double s = w[0] + 0.01, q0 = w[1] - w[2] / 2, q1 = w[1] + w[2] / 2;
        return new double[][]{toWorld(s, q0, w[3]), toWorld(s, q1, w[3]), toWorld(s, q1, w[4]), toWorld(s, q0, w[4]), dirToWorld(1, 0, 0), dirToWorld(0, 1, 0)};
    }
}
