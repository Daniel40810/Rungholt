package com.dan.buoyancy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Alles, was auf dem Wasser schwimmt: Tonnen an einem Fahrwasser und gelegentlich ein Schiff, das es entlangfährt.
 * <p>
 * Je Schritt fragt jeder Körper die Wasserhöhe an seinen Säulen ({@link Sea}) und rechnet Tauchen, Stampfen und
 * Rollen; Tonnen hängen an ihrer Kette, Schiffe folgen ihrer Route. Ein Schiff erscheint im Mittel alle
 * {@link #shipEveryMin} Minuten (lognormal gestreut), fährt rund 14 km und verschwindet. Im Sturm fahren weniger.
 * Am Bug meldet es Stöße ins Wasser ({@link #onWake}), damit ein Meer Ringwellen werfen kann.
 * <p>
 * Nachts (unter {@link #lightsOnLux}) brennen die Tonnenfeuer und die Positionslichter; {@link #lights} gibt alle,
 * die man vom Auge aus sieht.
 */
public final class Buoyancy {
    /** Stufen: Schritte je Sekunde für die Abfrage des Meeres, höchstens gleichzeitige Schiffe. */
    public enum Quality {
        LOW(15, 1), MEDIUM(30, 1), HIGH(30, 2), ULTRA(60, 2);
        public final int sampleRate, maxShips;
        Quality(int s, int m) { sampleRate = s; maxShips = m; }
    }

    /** Ein Stoß ins Wasser (Bugwelle): Ort und Stärke 0..1. */
    @FunctionalInterface public interface Wake { void wake(double x, double z, double strength); }

    /** Ein Schiff kommt oder geht. */
    @FunctionalInterface public interface ShipEvent { void ship(Ship s, boolean arrived); }

    // ------------------------------------------------------------------ Eingänge
    public volatile Sea sea;
    public volatile long seed = 1;
    public volatile double windSpeed = 5, windFromDeg = 240;
    /** Helligkeit in lx; darunter brennen die Lichter. */
    public volatile double ambientLux = 10_000, lightsOnLux = 200;
    public volatile Quality quality = Quality.HIGH;
    /** Mittlerer Abstand der Schiffe in Minuten Szenenzeit (0 = keine von selbst). */
    public volatile double shipEveryMin = 25;
    public volatile Wake onWake;
    public volatile ShipEvent onShip;
    /** Das Fahrwasser: Punkt (x, z), Richtung (Grad) und halbe Länge (m). */
    public volatile double fairwayX = 900, fairwayZ = 0, fairwayDeg = 330, fairwayHalf = 7000;

    public final List<Buoy> buoys = new CopyOnWriteArrayList<>();
    public final List<Ship> ships = new CopyOnWriteArrayList<>();

    // ------------------------------------------------------------------ Zustand
    private double time, nextShip = Double.NaN, sampleAcc;
    private Random rnd = new Random(1);
    private int shipNo;
    private long arrivals;

    // ------------------------------------------------------------------ Presets
    public static Buoyancy calm() { return beaufort(1); }
    public static Buoyancy breeze() { return beaufort(4); }
    public static Buoyancy gale() { return beaufort(8); }
    public static Buoyancy storm() { return beaufort(10); }

    /** Preset nach Beaufort: Wind, und ab Bft 9 fahren weniger Schiffe (doppelter, ab 11 vierfacher Abstand). */
    public static Buoyancy beaufort(int bft) {
        Buoyancy b = new Buoyancy();
        bft = Math.max(0, Math.min(12, bft));
        b.windSpeed = BFT_MID[bft];
        b.shipEveryMin = bft >= 11 ? 100 : bft >= 9 ? 50 : 25;
        return b;
    }

    static final double[] BFT_MID = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    /**
     * Tonnen beiderseits des Fahrwassers: alle {@code spacing} Meter ein Paar (Steuerbord grün, Backbord rot in
     * Fahrtrichtung zur Mündung), dazu am seewärtigen Ende eine Ansteuerungstonne und eine Kardinaltonne Nord.
     */
    public void layFairway(double width, double spacing, int pairs) {
        buoys.clear();
        double a = Math.toRadians(fairwayDeg), fx = Math.sin(a), fz = -Math.cos(a), rx = Math.cos(a), rz = Math.sin(a);
        for (int k = 0; k < pairs; k++) {
            double t = (k - (pairs - 1) / 2.0) * spacing;
            double cx = fairwayX + fx * t, cz = fairwayZ + fz * t;
            int no = 20 - 2 * k;
            // Fahrtrichtung landeinwärts ist gegen fairwayDeg: von See kommend liegt Steuerbord rechts
            add(new Buoy(Buoy.Kind.STEUERBORD, String.valueOf(no + 1), null, cx - rx * width / 2, cz - rz * width / 2), k * 1.3);
            add(new Buoy(Buoy.Kind.BACKBORD, String.valueOf(no), null, cx + rx * width / 2, cz + rz * width / 2), k * 0.7 + 2);
        }
        double t = ((pairs - 1) / 2.0 + 1.2) * spacing;
        add(new Buoy(Buoy.Kind.ANSTEUERUNG, "Weser", null, fairwayX + fx * t, fairwayZ + fz * t), 0);
        add(new Buoy(Buoy.Kind.NORD, "N", null, fairwayX - fx * spacing * 0.4 - rx * width * 1.6, fairwayZ - fz * spacing * 0.4 - rz * width * 1.6), 0.3);
    }

    private void add(Buoy b, double phase) {
        b.phase = phase;
        b.heading = (phase * 47) % 360;
        buoys.add(b);
        if (sea != null) b.settle(sea);
    }

    // ------------------------------------------------------------------ Zeit

    /** Um dt Sekunden weiter (Szenenzeit). */
    public void step(double dt) {
        if (dt <= 0 || sea == null) return;
        if (Double.isNaN(nextShip)) { rnd = new Random(seed); nextShip = time + firstGap(); }
        // große Sprünge (Zeitraffer): in Stücken von höchstens 0,25 s rechnen, aber nicht mehr als 2 s je Bild
        double left = Math.min(dt, 2.0);
        double skip = dt - left;
        if (skip > 0) for (Ship s : ships) { s.x += s.vx * skip; s.z += s.vz * skip; }
        while (left > 1e-9) {
            double h = Math.min(0.25, left);
            left -= h;
            time += h;
            sampleAcc += h;
            boolean sample = sampleAcc >= 1.0 / quality.sampleRate;
            if (sample) sampleAcc = 0;
            double seaState = Math.min(1, windSpeed / 25);
            for (Buoy b : buoys) {
                if (sample) b.sample(sea);
                b.moor(sea, windSpeed, windFromDeg, h);
                b.integrate(h);
            }
            for (Ship s : ships) {
                s.steer(h, seaState);
                if (sample) s.sample(sea);
                s.integrate(h);
                Wake w = onWake;
                if (w != null && sample && rnd.nextDouble() < 0.25) {
                    double[] bow = s.toWorld(s.length * 0.47, 0, 0);
                    w.wake(bow[0], bow[2], Math.min(1, 0.25 + Math.abs(s.vPitch) * 4 + Math.max(0, -s.vy) * 0.5));
                }
                if (s.done()) {
                    ships.remove(s);
                    ShipEvent e = onShip;
                    if (e != null) e.ship(s, false);
                }
            }
        }
        time += skip;
        if (shipEveryMin > 0 && time >= nextShip) {
            if (ships.size() < quality.maxShips) spawn(rnd.nextBoolean(), 0);
            nextShip = time + gap();
        }
    }

    private double firstGap() { return shipEveryMin <= 0 ? Double.POSITIVE_INFINITY : 60 * shipEveryMin * (0.2 + 0.4 * rnd.nextDouble()); }
    private double gap() { return shipEveryMin <= 0 ? Double.POSITIVE_INFINITY : 60 * shipEveryMin * Math.exp(0.45 * rnd.nextGaussian() - 0.1); }

    /**
     * Ein Schiff auf das Fahrwasser setzen: einlaufend (gegen fairwayDeg) oder auslaufend, schon {@code along} Meter
     * weit gefahren. Es fährt in der Mitte des Fahrwassers, versetzt um 60 m nach Steuerbord.
     */
    public Ship spawn(boolean inbound, double along) {
        Ship s = Ship.coaster(String.format(Locale.GERMAN, "Kümo %d", ++shipNo));
        double a = Math.toRadians(fairwayDeg + (inbound ? 180 : 0)), fx = Math.sin(a), fz = -Math.cos(a), rx = Math.cos(a), rz = Math.sin(a);
        double ox = fairwayX + rx * 60, oz = fairwayZ + rz * 60;
        s.route.add(new double[]{ox - fx * fairwayHalf, oz - fz * fairwayHalf});
        s.route.add(new double[]{ox + fx * fairwayHalf, oz + fz * fairwayHalf});
        s.start();
        s.x += fx * along;
        s.z += fz * along;
        s.speed = 5.6 + 1.2 * rnd.nextDouble();
        s.settle(sea);
        s.steer(0.01, 0);
        ships.add(s);
        arrivals++;
        ShipEvent e = onShip;
        if (e != null) e.ship(s, true);
        return s;
    }

    public double time() { return time; }
    public long arrivals() { return arrivals; }
    /** Szenenzeit bis zum nächsten Schiff (s). */
    public double nextShipIn() { return Double.isNaN(nextShip) ? Double.NaN : nextShip - time; }

    /** Alle Körper auf das Wasser setzen (nach Sprüngen). */
    public void settleAll() {
        if (sea == null) return;
        for (Buoy b : buoys) b.settle(sea);
        for (Ship s : ships) s.settle(sea);
    }

    // ------------------------------------------------------------------ Lichter

    /** Brennen die Lichter (Dämmerung)? */
    public boolean lightsOn() { return ambientLux < lightsOnLux; }

    /** Alle Lichter, die man vom Auge aus sieht, mit Stärke in diese Richtung. */
    public List<LightPoint> lights(double ex, double ey, double ez) {
        List<LightPoint> out = new ArrayList<>();
        if (!lightsOn()) return out;
        for (Buoy b : buoys) out.addAll(b.lights(time, true));
        for (Ship s : ships) out.addAll(s.lights(ex, ey, ez));
        return out;
    }

    /** „8 Tonnen · Kümo 3 in 1,4 km, 11,8 kn · nächstes Schiff in 12 min · Lichter an“ */
    public String describe(double ex, double ez) {
        StringBuilder b = new StringBuilder();
        b.append(buoys.size()).append(" Tonnen");
        for (Ship s : ships)
            b.append(String.format(Locale.GERMAN, " · %s in %.1f km, %.1f kn, rollt %.0f°", s.name, Math.hypot(s.x - ex, s.z - ez) / 1000, s.speed * 1.944, Math.toDegrees(Math.abs(s.roll))));
        double n = nextShipIn();
        if (ships.isEmpty() && Double.isFinite(n)) b.append(String.format(Locale.GERMAN, " · nächstes Schiff in %.0f min", n / 60));
        b.append(lightsOn() ? " · Lichter an" : "");
        return b.toString();
    }
}
