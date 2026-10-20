package com.dan.smoke;

import java.util.List;
import java.util.Locale;

/**
 * Seit Haithabu Phase 11: Rauch in einem Raum mit einem Feuer, nach dem Zwei-Zonen-Modell des Brandschutzes. Über
 * dem Feuer steigt eine Fahne und reißt Luft mit (Massenstrom nach Heskestad, ṁ = 0,071 · Q<sub>c</sub><sup>1/3</sup>
 * · z<sup>5/3</sup> · (1 + 0,026 · Q<sub>c</sub><sup>2/3</sup> · z<sup>−5/3</sup>) kg/s, Q<sub>c</sub> konvektiv in
 * kW, z über dem Brandherd in m). Oben sammelt sich eine warme Rauchschicht; sie fließt durch die Teile der Öffnungen
 * ab, die über ihrer Unterkante liegen (Ausfluss ρ · C<sub>d</sub> · √(2 g ΔT / T · (y − z<sub>i</sub>)) über die
 * Höhe der Öffnung, C<sub>d</sub> = 0,6). Im Gleichgewicht trägt die Fahne an der Unterkante z<sub>i</sub> so viel
 * hinein, wie oben hinausgeht; die Wärme der Schicht folgt aus ΔT = Q<sub>c</sub> · (1 − Verlust) / (c<sub>p</sub> ·
 * ṁ), die Hälfte geht an Reet und Wände [G].
 * <p>
 * Die Dichte des Rauchs: ein Holzfeuer auf dem Herd verbrennt bei 15 MJ/kg rund 0,7 g Holz je Sekunde und je 10 kW,
 * davon werden 3 % Rauch (Ruß und Teer, offenes Herdfeuer [G]); verteilt auf den Volumenstrom, der oben abfließt,
 * gibt das mit 7,6 m²/g eine Auslöschung von einigen Zehnteln je Meter – wenige Meter Sicht in der Schicht, wie man es
 * aus nachgebauten Hallen kennt. Unter der Schicht bleibt ein Dunst (12 % [G]: Mischung an der Grenze, Zug durch die
 * Türen). Die Schicht füllt sich und leert sich mit der Zeit Rauminhalt / Abfluss (einige Minuten). Rückstreuung 0,45:
 * Herdrauch ist teerig und rußig, dunkler als der weiße Rauch eines Schwelfeuers [G].
 * <p>
 * Ort im Raum: örtliche Meter des Raums, x entlang des Firsts, y über dem Boden, z quer; die Hülle ist ein Kasten mit
 * Satteldach ({@link Box}). Nur Java 21; keine Abhängigkeit zu anderen Packages (Paketvertrag).
 */
public final class RoomSmoke {
    /** Hülle innen: halbe Länge, halbe Breite, Wandhöhe, Firsthöhe (Unterseite des Dachs), Neigung der Dachunterseite. */
    public record Box(double hx, double hz, double wallH, double ridgeY, double tanRoof) {
        /** Grundfläche des Raums auf der Höhe y (m²): unter der Wandhöhe voll, darüber schmaler bis zum First. */
        public double area(double y) {
            if (y <= wallH) return 4 * hx * hz;
            double w = Math.max(0, (ridgeY - y) / tanRoof);
            return 4 * hx * Math.min(hz, w);
        }

        /** Rauminhalt über der Höhe y (m³). */
        public double volumeAbove(double y) {
            double v = 0, dy = 0.05;
            for (double h = Math.max(0, y); h < ridgeY; h += dy) v += area(h + dy / 2) * Math.min(dy, ridgeY - h);
            return v;
        }
    }

    /** Eine Öffnung: Breite unten (m), Unterkante, Oberkante (m über dem Boden); ein Dreieck läuft nach oben spitz zu. */
    public record Vent(double width, double y0, double y1, boolean triangle) {
        public double widthAt(double y) {
            if (y < y0 || y > y1) return 0;
            return triangle ? width * (y1 - y) / (y1 - y0) : width;
        }
        public double area() { return width * (y1 - y0) * (triangle ? 0.5 : 1); }
    }

    /** Gleichgewicht: Unterkante der Schicht (m), Erwärmung (K), Massenstrom der Fahne dort und Abfluss (kg/s), Auslöschung oben (1/m). */
    public record Steady(double zi, double dT, double plumeFlow, double outFlow, double sigma) { }

    public static final double G = 9.81, RHO = 1.2, CP = 1.005, CD = 0.6, LOSS = 0.5, CONVECTIVE = 0.7;
    /** Massenspezifische Auslöschung des Holzrauchs (m²/g), Rauchausbeute (g/g), Heizwert (kJ/g), Rückstreuung 0..1. */
    public static final double KAPPA = 7.6, YIELD = 0.03, HEAT = 15.0, ALBEDO = 0.45;
    /** Anteil der Schichtdichte, der unten als Dunst bleibt [G]. */
    public static final double LOWER = 0.12;

    public final Box box;
    public final List<Vent> vents;
    /** Feuer: Ort (örtlich), Wärmeleistung (kW), Rauchausbeute (g/g); Außentemperatur (°C). */
    public volatile double fireX, fireY = 0.06, fireZ, heatKw, yield = YIELD, outsideC = 10;
    private double zi, sigma, sigmaLow, dT, outFlow, plumeFlow, time;
    private boolean settled;

    public RoomSmoke(Box box, List<Vent> vents) {
        this.box = box;
        this.vents = List.copyOf(vents);
        this.zi = box.ridgeY();
    }

    // ------------------------------------------------------------------ Formeln

    /** Massenstrom der Fahne (kg/s) z Meter über dem Brandherd bei Q kW (konvektiv). */
    public static double plumeFlow(double qc, double z) {
        if (qc <= 0) return 0;
        z = Math.max(0.05, z);
        double z53 = Math.pow(z, 5.0 / 3.0);
        return 0.071 * Math.cbrt(qc) * z53 * (1 + 0.026 * Math.pow(qc, 2.0 / 3.0) / z53);
    }

    /** Halbmesser der Fahne (m) z Meter über dem Brandherd. */
    public static double plumeRadius(double z) { return 0.12 * Math.max(0, z) + 0.15; }

    /** Abfluss der Schicht (kg/s) durch alle Öffnungen über der Unterkante zi bei Erwärmung dT. */
    public double outFlow(double zi, double dT) {
        if (dT <= 0) return 0;
        double ta = outsideC + 273.15, rhoL = RHO * ta / (ta + dT), s = 0;
        for (Vent v : vents) {
            double lo = Math.max(zi, v.y0), hi = v.y1;
            if (hi <= lo) continue;
            int n = 24;
            double dy = (hi - lo) / n;
            for (int i = 0; i < n; i++) {
                double y = lo + (i + 0.5) * dy;
                s += v.widthAt(y) * Math.sqrt(2 * G * dT / ta * (y - zi)) * dy;
            }
        }
        return rhoL * CD * s;
    }

    /**
     * Das Gleichgewicht bei Q kW und Rauchausbeute y (g/g): Unterkante der Schicht so, dass die Fahne dort so viel
     * hineinträgt, wie oben abfließt (Halbierung zwischen dem Brandherd und dem First).
     */
    public Steady steady(double kw, double yld) {
        double qc = CONVECTIVE * Math.max(0, kw);
        if (qc < 0.05) return new Steady(box.ridgeY(), 0, 0, 0, 0);
        double lo = fireY + 0.25, hi = box.ridgeY() - 0.05;
        for (int it = 0; it < 48; it++) {
            double mid = (lo + hi) / 2;
            double mp = plumeFlow(qc, mid - fireY), dt = qc * (1 - LOSS) / (CP * mp);
            if (mp - outFlow(mid, dt) > 0) hi = mid; else lo = mid;
        }
        double z = (lo + hi) / 2, mp = plumeFlow(qc, z - fireY), dt = qc * (1 - LOSS) / (CP * mp), mo = outFlow(z, dt);
        double ta = outsideC + 273.15, rhoL = RHO * ta / (ta + dt);
        double soot = Math.max(0, kw) / HEAT * yld;                 // g/s
        double vdot = Math.max(1e-3, mo / rhoL);                    // m³/s
        return new Steady(z, dt, mp, mo, KAPPA * soot / vdot);
    }

    // ------------------------------------------------------------------ Verlauf

    /** Um dt Sekunden weiter: die Schicht füllt sich oder leert sich mit der Zeit Rauminhalt über ihr / Abfluss. */
    public void step(double dt) {
        Steady s = steady(heatKw, yield);
        if (!settled || dt > 1800) { settle(s); return; }
        if (dt <= 0) return;
        time += dt;
        double ta = outsideC + 273.15;
        double flow = Math.max(s.outFlow, outFlow) / (RHO * ta / (ta + Math.max(1, s.dT)));
        double tau = Math.max(60, box.volumeAbove(Math.min(zi, s.zi)) / Math.max(0.05, flow));
        double k = 1 - Math.exp(-dt / tau);
        // ohne Feuer steigt die Schicht langsam auf und dünnt aus (Abfluss nur durch Wärme und Zug, langsamer)
        if (s.sigma <= 0) k = 1 - Math.exp(-dt / Math.max(tau, 900));
        zi += (s.zi - zi) * k;
        sigma += (s.sigma - sigma) * k;
        sigmaLow += (LOWER * s.sigma - sigmaLow) * k;
        dT += (s.dT - dT) * k;
        outFlow += (s.outFlow - outFlow) * k;
        plumeFlow = s.plumeFlow;
    }

    /** Sofort ins Gleichgewicht (nach Sprüngen der Uhr). */
    public void settle() { settle(steady(heatKw, yield)); }

    private void settle(Steady s) {
        zi = s.zi;
        sigma = s.sigma;
        sigmaLow = LOWER * s.sigma;
        dT = s.dT;
        outFlow = s.outFlow;
        plumeFlow = s.plumeFlow;
        settled = true;
    }

    public double layerBottom() { return zi; }
    public double sigma() { return sigma; }
    public double sigmaLow() { return sigmaLow; }
    public double heating() { return dT; }
    public double outFlowKgS() { return outFlow; }
    /** Wie viele Meter man in der Schicht sieht (Sichtweite 3 / σ). */
    public double visibility() { return sigma > 1e-6 ? 3 / sigma : Double.POSITIVE_INFINITY; }

    /** Dicke des weichen Übergangs zwischen Dunst und Schicht (m). */
    public static double blend() { return 0.3; }

    /** Auslöschung in der Fahne (1/m) z Meter über dem Brandherd (Rauch auf den mitgerissenen Volumenstrom verteilt). */
    public double plumeSigma(double z) {
        double qc = CONVECTIVE * Math.max(0, heatKw);
        if (qc < 0.05 || z <= 0) return 0;
        double soot = heatKw / HEAT * yield, vdot = plumeFlow(qc, z) / RHO;
        return Math.min(3, KAPPA * soot / Math.max(1e-3, vdot));
    }

    /**
     * Auslöschung des Rauchs (1/m) am örtlichen Ort: Schicht oben, Dunst unten (weicher Übergang), dazu die Fahne über
     * dem Feuer als Gaußsche Säule. Wie {@code gl/room_smoke.frag} ohne das Rauschen.
     */
    public double density(double x, double y, double z) {
        double w = blend();
        double t = smooth(zi - w, zi + w, y);
        double s = sigmaLow + (sigma - sigmaLow) * t;
        double zz = y - fireY;
        if (zz > 0 && y < zi + w) {
            double b = plumeRadius(zz), r2 = (x - fireX) * (x - fireX) + (z - fireZ) * (z - fireZ);
            s += plumeSigma(zz) * Math.exp(-r2 / (b * b));
        }
        return s;
    }

    /**
     * Zahlen für die Grafik (zwei vec4): Unterkante, Auslöschung oben, unten, Übergang; Feuer x, y, z, Q konvektiv.
     * Dazu {@link #plumeCoefficient}: KAPPA · Rauch (g/s) · RHO, so dass σ<sub>Fahne</sub>(z) = c / ṁ(z).
     */
    public float[] pack() {
        return new float[]{(float) zi, (float) sigma, (float) sigmaLow, (float) blend(),
                (float) fireX, (float) fireY, (float) fireZ, (float) (CONVECTIVE * Math.max(0, heatKw))};
    }

    public double plumeCoefficient() { return KAPPA * Math.max(0, heatKw) / HEAT * yield * RHO; }

    public double time() { return time; }

    /** „Schicht ab 2,4 m · σ 0,30/m (Sicht 10 m) · +9 K · Abfluss 0,47 kg/s“ */
    public String describe() {
        if (sigma < 1e-3) return "kein Rauch im Raum";
        return String.format(Locale.GERMAN, "Schicht ab %.1f m · σ %.2f/m (Sicht %.0f m), unten %.2f/m · +%.0f K · Abfluss %.2f kg/s",
                zi, sigma, visibility(), sigmaLow, dT, outFlow);
    }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
