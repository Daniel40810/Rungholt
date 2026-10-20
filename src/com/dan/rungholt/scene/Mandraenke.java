package com.dan.rungholt.scene;

import com.dan.timber.House;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 12: die Grote Mandränke, die Nacht vom 15. auf den 16. Januar 1362 (julianisch; gregorianisch 23./24.), als Ereignis aus der Physik (F16 A) [G für
 * Ablauf und Zahlen]. Die Regie dieses Abends:
 * <ol>
 *   <li><b>Vorabend</b> (ab 22 Uhr): der Wind frischt aus Westnordwest auf, die Leute sind draußen.</li>
 *   <li><b>Sturm</b> (ab 23:30): Orkan aus der Regie ({@code weather}), der Windstau steigt auf das Hochwasser der
 *       Nacht zu ({@link #surge}: 3,6 m über dem Rechenwert [G], F16 A: 3,5 m); die Leute gehen auf die Kirchwarft.</li>
 *   <li><b>Überlauf</b>: steht das Wasser keinen Meter mehr unter der Krone, schlagen die Wellen über den Deich; an der
 *       Bruchstelle spült das die Binnenböschung aus (nach rund einer Stunde ein Drittel).</li>
 *   <li><b>Bruch</b> ({@link Deichbruch}, {@code com.dan.fracture}): der Abschnitt wird ein Körper aus Klei, die Regie
 *       geht in Echtzeit; die Kopferosion frisst sich durch, das Wasser drückt, Stücke brechen ab und kippen in die Bresche.</li>
 *   <li><b>Flut</b>: ist die Hälfte des Abschnitts fort, ist die Bresche offen; das Wasser stürzt mit {@code com.dan.wave}
 *       in die Marsch, die Überflutung von 1362 rechnet mit dem gebrochenen Deich weiter. Häuser, die tiefer als 1,5 m im
 *       Wasser stehen, schwimmen auf und treiben mit der Strömung, bis sie zerfallen; die Kirche steht.</li>
 *   <li><b>Morgen</b> (6 h nach dem Hochwasser der Nacht): nach der Flut.</li>
 * </ol>
 */
public final class Mandraenke {
    public enum Stage {
        VORABEND("Vorabend"), STURM("Sturm"), UEBERLAUF("Wellen über der Krone"), BRUCH("der Deich bricht"), FLUT("Flut in der Stadt"), MORGEN("nach der Flut");
        public final String label;
        Stage(String l) { label = l; }
    }

    /** Wo der Deich bricht: vor der Kirche (Vorgabe), am Siel, im Osten. */
    public enum Stelle {
        KIRCHE("vor der Kirche", -650), SIEL("westlich des Siels", -3000), OST("im Osten", 900);
        public final String label;
        public final double x;
        Stelle(String l, double x) { label = l; this.x = x; }
    }

    /**
     * Der Abend: 15. Januar 1362 julianisch, in der Uhr der Szene der 23. Januar (Phase 17: dort steht die Springtide,
     * Faktor 1,19; am 15. gregorianisch war fast Nipp). Das Hochwasser der Nacht kommt am 24. um 4:36 Uhr Ortszeit; die
     * Nacht beginnt 22 Uhr, wie vorher gut fünfeinhalb Stunden davor [G].
     */
    public static final LocalDate DAY = SceneClock.gregorian(1362, 1, 15);
    public static final double START_HOUR = 22;
    /** Höchster Windstau (m) und ab welchem Stand unter der Krone die Wellen überschlagen (m). */
    public static final double SURGE_PEAK = 3.6, OVERTOP_BELOW_CREST = 1.0;
    /** Stunden Überlauf, bis die Binnenböschung so weit ausgespült ist, dass der Abschnitt bricht. */
    public static final double OVERTOP_HOURS = 1.0;

    public Stage stage = Stage.VORABEND;
    /** Bruchstelle und Abschnitt; bis zum Bruch umzusetzen ({@link #relocate}). */
    public volatile Stelle stelle;
    public volatile Deichbruch bruch;
    /** Szenenzeit in Stunden seit 1970: Beginn (23.1. 22 Uhr) und Hochwasser der Nacht. */
    public final double startH, hwH;
    /** Stunden Überlauf, Zeit des Bruchs (Beginn der Echtzeit) und der offenen Bresche (h seit 1970). */
    public double overtopH, breachH = Double.NaN, openH = Double.NaN;
    /** Ereignisse für die App (einmal true): Gelände ausheben (Bruch), Bresche offen (Welle, Überflutung neu). */
    public boolean carveNow, openNow;
    /**
     * Der Spiegel in der Marsch hinter dem Deich (m NHN, NaN: trocken), als Becken aus Zu- und Abfluss: durch die
     * Bresche und über die ganze Länge des Seedeichs als breitkroniges Wehr (q = 1,7·h^1,5 m²/s, mit Rückstau nach
     * Villemonte), geteilt durch die Fläche der Marsch unter dem Spiegel ({@link #hypsometry}). So steigt das Wasser in der
     * Stadt in Stunden, nicht in Minuten, und läuft am Morgen langsam wieder ab [G].
     */
    public double polder = Double.NaN;
    /** Fläche der Marsch unter dem Spiegel (m²) je 5 cm von NHN 0 an (die App rechnet sie aus dem Gelände). */
    public double[] hypsometry;
    /** Länge des Seedeichs (m). */
    public static final double DIKE_LENGTH = dikeLength();

    /** Häuser der Stadt mit ihrem Zustand in der Flut. */
    private final List<Drift> drift = new ArrayList<>();

    /** Ein Haus in der Flut: Ort, Richtung, schwimmt es, wie lange, ist es zerfallen. */
    public static final class Drift {
        public final House home;
        public double x, z, y, heading, afloat = -1;
        public boolean gone;
        final double life;
        Drift(House h, double life) { home = h; x = h.x(); z = h.z(); y = h.y(); heading = h.headingDeg(); this.life = life; }
        public House house() { return new House(home.type(), x, y, z, heading, home.lengthScale(), home.widthScale(), Math.min(1, home.state() + (afloat >= 0 ? 0.4 : 0)), home.seed()); }
    }

    /** Wasser für die Häuser: Spiegel (NaN: trocken) und Strömung am Ort. */
    public interface Water { double surface(double x, double z); double[] velocity(double x, double z); }

    public Mandraenke(Stelle stelle, double startHours, double nightHighWater, List<House> houses) {
        this.stelle = stelle;
        this.bruch = new Deichbruch(stelle.x);
        this.startH = startHours;
        this.hwH = nightHighWater;
        SplittableRandom r = new SplittableRandom(1362);
        for (House h : houses) drift.add(new Drift(h, 0.5 + 1.0 * r.nextDouble()));
    }

    /**
     * Windstau (m) zur Stunde h: steigt in 14 h auf {@link #SURGE_PEAK} zum Hochwasser der Nacht, fällt danach in 8 h auf
     * 1,6 m [G].
     */
    public double surge(double h) {
        double d = h - hwH;
        if (d <= 0) return SURGE_PEAK * smooth(-14, 0, d);
        return SURGE_PEAK - 2.0 * smooth(0, 8, d);
    }

    /** Soll die Uhr in Echtzeit laufen (vom Bruch bis 3 min nach der offenen Bresche)? */
    public boolean realtime(double h) {
        return stage == Stage.BRUCH || (stage == Stage.FLUT && !Double.isNaN(openH) && h < openH + 3.0 / 60);
    }

    /**
     * Die Regie um dt Sekunden Szenenzeit weiter: sea ist der Pegel vor der Bruchstelle (m NHN). Die Stufen wechseln;
     * {@link #carveNow} und {@link #openNow} melden der App, was zu tun ist. Der Körper selbst rechnet in
     * {@link #stepBreach} (im Faden der Physik).
     */
    public void step(double dt, double h, double sea) {
        switch (stage) {
            case VORABEND -> { if (h >= startH + 1.5) stage = Stage.STURM; }
            case STURM -> { if (sea >= Land1362.CREST - OVERTOP_BELOW_CREST) stage = Stage.UEBERLAUF; }
            case UEBERLAUF -> {
                overtopH += dt / 3600 * (1 + Math.max(0, sea - (Land1362.CREST - OVERTOP_BELOW_CREST)));
                bruch.eroded = Math.min(0.3, overtopH / OVERTOP_HOURS * 0.3);
                if (overtopH >= OVERTOP_HOURS) breakNow(h);
            }
            case BRUCH -> { }
            case FLUT -> { if (h >= hwH + 6) stage = Stage.MORGEN; }
            case MORGEN -> { }
        }
        if (stage.ordinal() >= Stage.UEBERLAUF.ordinal()) stepPolder(dt, sea);
    }

    /**
     * Die Bruchstelle wechseln, solange der Deich noch steht (bis zum Überlauf); das schon Ausgespülte geht mit. false,
     * wenn der Deich schon gebrochen ist (dann gilt die Wahl erst beim nächsten Abend).
     */
    public synchronized boolean relocate(Stelle s) {
        if (s == stelle) return true;
        if (stage.ordinal() >= Stage.BRUCH.ordinal()) return false;
        Deichbruch b = new Deichbruch(s.x);
        b.eroded = bruch.eroded;
        b.world.quality = bruch.world.quality;   // Phase 17: die Stufe geht mit
        bruch = b;
        stelle = s;
        return true;
    }

    /** Auslöser von Hand oder aus dem Überlauf: der Abschnitt wird zum Körper (die App hebt das Gelände aus). */
    public void breakNow(double h) {
        if (stage.ordinal() >= Stage.BRUCH.ordinal()) return;
        bruch.eroded = Math.max(bruch.eroded, 0.3);
        breachH = h;
        stage = Stage.BRUCH;
        carveNow = true;
    }

    /** Einen festen Schritt des Bruchs (Physik-Faden); meldet die offene Bresche. */
    public void stepBreach(double dt, double sea, double land, double h) {
        if (bruch.solid == null) return;
        boolean live = stage == Stage.BRUCH || (stage.ordinal() >= Stage.FLUT.ordinal() && !Double.isNaN(openH) && h < openH + 0.25);
        if (!live) return;
        bruch.step(dt, sea, land);
        if (stage == Stage.BRUCH && bruch.attached() <= bruch.solid.cells() / 2) { stage = Stage.FLUT; openH = h; openNow = true; }
    }

    /**
     * Sprung „nach der Flut“: die Bresche ist offen (ohne Körper), alle Häuser, über denen das Wasser beim Höchststand
     * (peak, m NHN) mehr als 1,5 m stand, sind fort; die Uhr steht 7 h nach dem Hochwasser der Nacht.
     */
    public void afterTheFlood(double peak, double h) {
        if (stage.ordinal() < Stage.BRUCH.ordinal()) { breachH = h; carveNow = true; }
        if (Double.isNaN(openH)) { openH = h; openNow = true; }
        bruch.eroded = 1.2;
        stage = Stage.MORGEN;
        for (Drift d : drift) if (d.home.y() + 1.5 < peak) d.gone = true;
    }

    /** Höchster Pegel der Nacht (m NHN): Hochwasser plus voller Windstau. */
    public double peak(double astronomicalHighWater) { return astronomicalHighWater + SURGE_PEAK; }

    /** Den Spiegel in der Marsch um dt Sekunden weiter (sea: Pegel vor dem Deich). */
    public void stepPolder(double dt, double sea) {
        if (hypsometry == null || dt <= 0) return;
        boolean open = !Double.isNaN(openH);
        double p = Double.isNaN(polder) ? Land1362.INNER_WATER : polder;
        double rest = dt;
        while (rest > 1e-9) {
            double h = Math.min(rest, 30);
            rest -= h;
            double q = weir(sea, p, Land1362.CREST) * DIKE_LENGTH + (open ? weir(sea, p, Deichbruch.BOTTOM) * Deichbruch.WIDTH : 0);
            p += q * h / Math.max(1e5, area(p));
            p = Math.max(Land1362.INNER_WATER, p);
        }
        polder = p > Land1362.INNER_WATER + 0.01 || open ? p : Double.NaN;
    }

    /** Fläche der Marsch unter dem Spiegel p (m²). */
    public double area(double p) {
        double[] a = hypsometry;
        int k = (int) Math.floor(p / 0.05);
        if (k < 0) return a[0];
        if (k >= a.length - 1) return a[a.length - 1];
        double f = p / 0.05 - k;
        return a[k] + (a[k + 1] - a[k]) * f;
    }

    /**
     * Durchfluss je Meter über ein breitkroniges Wehr mit der Krone c (m²/s; positiv von up nach down): frei 1,7·h^1,5,
     * mit Rückstau nach Villemonte (1 − (h₂/h₁)^1,5)^0,385.
     */
    public static double weir(double up, double down, double c) {
        double hi = Math.max(up, down), lo = Math.min(up, down);
        double h1 = hi - c;
        if (h1 <= 0) return 0;
        double h2 = Math.max(0, lo - c);
        double q = 1.7 * Math.pow(h1, 1.5) * Math.pow(Math.max(0, 1 - Math.pow(h2 / h1, 1.5)), 0.385);
        return up >= down ? q : -q;
    }

    static double dikeLength() {
        double l = 0;
        double[][] d = Land1362.DIKE;
        for (int i = 1; i < d.length; i++) l += Math.hypot(d[i][0] - d[i - 1][0], d[i][1] - d[i - 1][1]);
        return l;
    }

    /** Die Häuser in der Flut weiter: aufschwimmen ab 1,5 m Wasser, treiben mit der Strömung, nach 0,5 bis 1,5 h zerfallen. */
    public void stepHouses(double dt, double h, Water w) {
        if (stage.ordinal() < Stage.FLUT.ordinal()) return;
        for (Drift d : drift) {
            if (d.gone) continue;
            double s = w.surface(d.x, d.z);
            double depth = Double.isNaN(s) ? 0 : s - d.home.y();
            if (d.afloat < 0) {
                if (depth > 1.5) d.afloat = 0;
                else continue;
            }
            d.afloat += dt / 3600;
            if (d.afloat > d.life) { d.gone = true; continue; }
            if (Double.isNaN(s)) continue;   // gestrandet
            double[] v = w.velocity(d.x, d.z);
            if (Math.hypot(v[0], v[1]) < 0.05) v = new double[]{0.28, 0.10};   // ohne Strömung treibt der Orkan aus WNW sie nach OSO
            double sp = Math.hypot(v[0], v[1]), k = sp > 1.5 ? 1.5 / sp : 1;
            d.x += v[0] * k * 0.6 * dt;
            d.z += v[1] * k * 0.6 * dt;
            d.y = s - 1.6;
            d.heading = (d.heading + dt * 0.4 * Math.sin(d.home.seed() % 7 + h * 3)) % 360;
        }
    }

    /** Die Häuser, wie sie jetzt stehen oder treiben (ohne die zerfallenen). */
    public List<House> houses() {
        List<House> l = new ArrayList<>(drift.size());
        for (Drift d : drift) if (!d.gone) l.add(d.afloat >= 0 ? d.house() : d.home);
        return l;
    }

    public int afloat() { int k = 0; for (Drift d : drift) if (!d.gone && d.afloat >= 0) k++; return k; }
    public int gone() { int k = 0; for (Drift d : drift) if (d.gone) k++; return k; }

    /** „Mandränke: der Deich bricht (vor der Kirche) · Windstau 3,1 m · …“ */
    public String describe(double h, double sea) {
        String s = String.format(Locale.GERMAN, "Mandränke: %s · Pegel NHN %+.2f m (Windstau %.1f m) · Bruchstelle %s", stage.label, sea, surge(h), stelle.label);
        if (stage == Stage.UEBERLAUF) s += String.format(Locale.GERMAN, " · Binnenböschung %.0f %% bis zum Bruch ausgespült", Math.min(100, overtopH / OVERTOP_HOURS * 100));
        if (stage == Stage.BRUCH || stage == Stage.FLUT) s += " · " + bruch.describe();
        if (!Double.isNaN(polder)) s += String.format(Locale.GERMAN, " · Marsch NHN %+.2f m", polder);
        if (stage.ordinal() >= Stage.FLUT.ordinal()) s += String.format(Locale.GERMAN, " · %d Häuser treiben, %d zerfallen", afloat(), gone());
        return s;
    }

    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
}
