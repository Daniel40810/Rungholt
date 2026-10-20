package com.dan.rungholt.scene;

import com.dan.buoyancy.Cog;
import com.dan.buoyancy.Sea;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 11: der Sielhafen von Rungholt und die Kogge, die mit der Flut einläuft [G]. Sie liegt auf Reede in der Hever
 * vor der Mündung des Sieltiefs. Steigt das Wasser im Tief so weit, dass es sie trägt (Pegel über NHN +0,7 m), läuft sie
 * mit gesetztem Segel das Tief hinauf ins Hafenbecken und macht an der Kaje fest. Bei Ebbe fällt sie trocken und sitzt
 * auf ihrem flachen Boden im Schlick. Mit dem nächsten Hochwasser (frühestens nach 11 h) läuft sie wieder aus, wendet
 * im Becken und geht auf Reede; nach einer Liegezeit kommt sie mit der nächsten Flut am Tag wieder.
 */
public final class Sielhafen {
    public enum State { REEDE, EINLAUFEN, LIEGT, AUSLAUFEN }

    /** Pegel, ab dem das Wasser im Tief die Kogge trägt: Sohle −2,3 m, Tiefgang 2,25 m, 0,75 m Handbreit unter dem Kiel. */
    public static final double NEED = -2.3 + 2.25 + 0.75;

    public final Cog cog = new Cog("Kogge");
    public State state = State.REEDE;
    /** Seit wann im jetzigen Zustand (h Szenenzeit). */
    public double since;
    public final double[] berth, anchorage;
    /** Das Fahrwasser vom Ende des Sieltiefs zur Reede (dem tiefsten Wasser nach). */
    public final double[][] fairway;
    private final List<double[]> in = new ArrayList<>(), out = new ArrayList<>();
    private boolean placed;
    /** Wie viele Male sie eingelaufen ist (Prüfung). */
    public int arrivals;

    /** @param bed Grund von 1362 (für die Reede: in Verlängerung des Tiefs, wo die Hever tiefer als NHN −4 m ist) */
    public Sielhafen(double[][] tief, double[] hb, java.util.function.DoubleBinaryOperator bed) {
        berth = Stadt1362.berth(hb);
        double[] end = tief[tief.length - 1];
        double ax = hb[2], az = hb[3];
        // vom Ende des Tiefs dem tiefsten Wasser nach (Priel, wie ihn das Watt von heute noch zeigt) bis in die Hever
        List<double[]> fair = new ArrayList<>();
        double fx = end[0], fz = end[1], dx = ax, dz = az, run = 0;
        fair.add(new double[]{fx, fz});
        for (int k = 0; k < 90; k++) {
            double best = 1e9, bx = 0, bz = 0;
            for (int a = -60; a <= 60; a += 15) {
                double r = Math.toRadians(a), cx = dx * Math.cos(r) - dz * Math.sin(r), cz = dx * Math.sin(r) + dz * Math.cos(r);
                double b = bed.applyAsDouble(fx + cx * 40, fz + cz * 40) + 0.004 * Math.abs(a);
                if (b < best) { best = b; bx = cx; bz = cz; }
            }
            dx = dx * 0.5 + bx * 0.5; dz = dz * 0.5 + bz * 0.5;
            double l = Math.hypot(dx, dz);
            dx /= l; dz /= l;
            fx += bx * 40; fz += bz * 40; run += 40;
            fair.add(new double[]{fx, fz});
            if (run >= 400 && bed.applyAsDouble(fx, fz) < -5) break;
        }
        anchorage = new double[]{fx, fz};
        fairway = fair.toArray(new double[0][]);
        // hinein: von der Reede das Fahrwasser hinauf, das Tief hinauf bis ins Becken, an der Kaje entlang zum Liegeplatz
        in.add(new double[]{anchorage[0], anchorage[1], 1.5});
        for (int i = fair.size() - 3; i > 0; i -= 3) in.add(new double[]{fair.get(i)[0], fair.get(i)[1], 1.3});
        for (int i = tief.length - 1; i >= 0; i -= 6) {
            double d = Math.hypot(tief[i][0] - Land1362.SIEL[0], tief[i][1] - Land1362.SIEL[1]);
            if (d < Land1362.BASIN_FROM_SIEL + Land1362.BASIN_ALONG) break;
            in.add(new double[]{tief[i][0], tief[i][1], 0.9});   // im Tief langsam (gut eine Handbreit Wasser unter dem Kiel)
        }
        double qx = berth[0] - hb[0], qz = berth[1] - hb[1];
        in.add(new double[]{hb[0] + ax * 40 + qx * 0.6, hb[1] + az * 40 + qz * 0.6, 0.6});
        in.add(new double[]{berth[0], berth[1], 0.4});
        // hinaus: auf die Westseite des Beckens, wenden, das Tief hinunter zur Reede
        out.add(new double[]{berth[0], berth[1], 0.5});
        out.add(new double[]{hb[0] - qx * 0.5 - ax * 25, hb[1] - qz * 0.5 - az * 25, 0.8});
        out.add(new double[]{hb[0] - qx * 0.4 + ax * 20, hb[1] - qz * 0.4 + az * 20, 1.0});
        for (int i = 0; i < in.size() - 2; i++) out.add(new double[]{in.get(in.size() - 3 - i)[0], in.get(in.size() - 3 - i)[1], 2.2});
    }

    /** Die Kogge zur Stunde h an ihren Platz: auf Reede, oder (zwischen Mittag und Abend) schon im Hafen (Start). */
    public void place(Sea sea, boolean inHarbour, double hours) {
        if (inHarbour) { cog.place(sea, berth[0], berth[1], berth[2]); state = State.LIEGT; }
        else {
            double h = Math.toDegrees(Math.atan2(in.get(1)[0] - in.get(0)[0], -(in.get(1)[1] - in.get(0)[1])));
            cog.place(sea, anchorage[0], anchorage[1], (h + 360) % 360);
            state = State.REEDE;
        }
        since = hours;
        placed = true;
    }

    /**
     * dt Sekunden weiter zur Stunde hours (Szenenzeit) beim Pegel level, der gerade steigt oder fällt.
     */
    public void step(Sea sea, double dt, double hours, double level, boolean rising) {
        if (!placed) place(sea, false, hours);
        double stay = hours - since;
        switch (state) {
            case REEDE -> {
                if (rising && level > NEED && (stay > 4 || arrivals == 0)) { setRoute(in); cog.start(sea); state = State.EINLAUFEN; since = hours; }
            }
            case EINLAUFEN -> {
                if (cog.done()) { state = State.LIEGT; since = hours; arrivals++; }
            }
            case LIEGT -> {
                // zum nächsten Hochwasser wieder hinaus: frühestens nach 11 h, wenn das Wasser hoch steht und fällt
                if (stay > 11 && !rising && level > NEED + 0.2) { setRoute(out); cog.go(); state = State.AUSLAUFEN; since = hours; }
            }
            case AUSLAUFEN -> {
                if (cog.done()) { state = State.REEDE; since = hours; }
            }
        }
        // in Schritten zu höchstens 2 s rechnen (die Kogge teilt selbst weiter)
        int n = Math.max(1, (int) Math.ceil(dt / 2));
        for (int i = 0; i < n; i++) cog.step(sea, dt / n);
    }

    /** Nach einem Sprung der Uhr: eine Fahrt, die gerade lief, ist zu Ende (an der Kaje oder auf Reede). */
    public void settle(Sea sea, double hours) {
        if (!placed) { place(sea, false, hours); return; }
        if (state == State.EINLAUFEN) { cog.place(sea, berth[0], berth[1], berth[2]); state = State.LIEGT; since = hours; arrivals++; }
        else if (state == State.AUSLAUFEN) { place(sea, false, hours); }
        else cog.place(sea, cog.x, cog.z, cog.heading);
    }

    private void setRoute(List<double[]> r) {
        cog.route.clear();
        for (double[] p : r) cog.route.add(p.clone());
        if (r == out) cog.route.set(0, new double[]{cog.x, cog.z, r.get(0)[2]});
    }

    /** „Kogge: liegt an der Kaje, sitzt auf dem Grund · seit 3,2 h“ */
    public String describe(Sea sea) {
        String st = switch (state) {
            case REEDE -> "auf Reede in der Hever";
            case EINLAUFEN -> String.format(Locale.GERMAN, "läuft mit der Flut ein, noch %.0f m", cog.remaining());
            case LIEGT -> "liegt an der Kaje";
            case AUSLAUFEN -> String.format(Locale.GERMAN, "läuft aus, noch %.0f m", cog.remaining());
        };
        return "Kogge: " + st + (cog.grounded() ? ", sitzt auf dem Grund" : String.format(Locale.GERMAN, ", %.1f m Wasser unter dem Kiel", Math.max(0, cog.underKeel(sea))))
                + String.format(Locale.GERMAN, " · Segel %.0f %%", cog.sail * 100);
    }
}
