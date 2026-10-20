package com.dan.tide;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Tide (Werkbuch Rungholt, Abschnitt 4, F8–F10 A): Wasserstand als Pegelkurve. Kennt keine Grafik und keine App;
 * die Überflutung eines Geländes rechnet {@link Flood}.
 * <ul>
 * <li><b>Pegel</b> aus zehn Partialtiden: M2, S2, N2, K2 (halbtägig), K1, O1 (eintägig) und M4, MS4, MN4, M6 (die
 * Oberschwingungen im Flachwasser: Flut und Ebbe ungleich lang, flache Scheitel). Spring und Nipp folgen von selbst aus
 * M2, S2, N2 und K2, die tägliche Ungleichheit aus K1 und O1. Die Zeit zählt in Stunden seit 1970 (UTC):
 * {@code Pegel = Mittel + Σ A·cos(ω·t − φ) + Windstau}.</li>
 * <li><b>Windstau</b> als Aufschlag: Ziel aus Wind und Richtung gegen die Küste ({@link #surgeTarget}), der Stau folgt
 * mit einer Zeitkonstante von Stunden ({@link #stepSurge}).</li>
 * <li><b>Abfragen</b>: nächstes Hoch- und Niedrigwasser ({@link #extremes}), Tidenkalender eines Tags oder Jahrs
 * ({@link #calendar}), Spring und Nipp ({@link #springFactor}, {@link #nextSpring}), Flut oder Ebbe.</li>
 * <li><b>Ereignisse</b> {@link #onHighWater}, {@link #onLowWater}, {@link #onTurn} (Kenterung) aus {@link #step}.</li>
 * </ul>
 * Konstanten für Husum: angepasst an die Hoch- und Niedrigwasserzeiten des Gezeitenkalenders 2026 (BSH, 475 Zeiten aus
 * Januar, April, Juli und Oktober; Zeitfehler im Mittel 11 min, Hochwasser 7 bis 10 min) und an den mittleren Hub von
 * 3,50 m der Husumer Bucht; MThw NHN +1,60 m, MTnw −1,90 m [G]. Ohne Knotenkorrektur (18,6 Jahre): die Amplituden
 * gelten für 2026, für andere Jahre einige Prozent daneben.
 */
public final class Tide {
    public enum Quality {
        LOW(4), MEDIUM(2), HIGH(1), ULTRA(1);
        /** Vergröberung des Grunds für die Überflutung (1 = jede Zelle). */
        public final int coarse;
        Quality(int c) { coarse = c; }
    }

    /** Stand des Packages: 1 = Hülle; 4 = Pegel und Überflutung; 6 = Strömung im Bild. */
    public static final int STAGE = 4;

    /** Eine Partialtide: Name, Winkelgeschwindigkeit (Grad je Stunde), Amplitude (m), Phase (Grad, Bezug 1970 UTC). */
    public record Constituent(String name, double speedDegPerHour, double amplitude, double phaseDeg) {
        /** Periode in Stunden. */
        public double periodHours() { return 360 / speedDegPerHour; }
    }

    /** Winkelgeschwindigkeiten (Grad je Stunde, nach Doodson). */
    public static final double M2 = 28.9841042, S2 = 30.0, N2 = 28.4397295, K2 = 30.0821373, K1 = 15.0410686, O1 = 13.9430356,
            M4 = 57.9682084, MS4 = 58.9841042, MN4 = 57.4238337, M6 = 86.9523127;

    /** Hoch- oder Niedrigwasser: Zeit (Stunden seit 1970, UTC), Pegel (m). */
    @FunctionalInterface public interface Extreme { void extreme(double hours, double level); }
    /** Eine Zelle oder Fläche fällt trocken oder wird nass: Spalte, Zeile (oder −1, Nummer der Fläche), Zeit (Stunden). */
    @FunctionalInterface public interface CellEvent { void cell(int i, int j, double hours); }

    /** Ein Hoch- oder Niedrigwasser als Datensatz. */
    public record Turn(double hours, double level, boolean high) {
        public Instant instant() { return Instant.ofEpochMilli(Math.round(hours * 3_600_000)); }
    }

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Name des Pegels (Preset). */
    public String gauge = "ohne Ort";
    /** Mittelwasser in m über dem Bezug (NHN). */
    public double mean;
    /** Windstau (m), wird zum Pegel addiert; {@link #stepSurge} führt ihn nach. */
    public double surge;
    /** Zellgröße des Grundes (m), Versickerung in Pfützen (m je Stunde) [G]. */
    public double cellSize = 10, seepage = 0.02;
    /**
     * Größte Tiefe einer Pfütze (m) [G]: Was darüber in einer Mulde steht, läuft nach der Ebbe durch kleine Rinnen ab, die
     * das Raster nicht kennt. Ohne diese Grenze hielte jede Senke des Rasters Wasser bis zu ihrem Rand.
     */
    public double maxPuddle = 0.15;
    public final List<Constituent> constituents = new ArrayList<>();
    public Extreme onHighWater, onLowWater, onTurn;
    public CellEvent onDry, onWet;
    private double lastH = Double.NaN, lastLevel, lastSlope;

    // ------------------------------------------------------------------ Presets

    /**
     * Pegel Husum: zehn Partialtiden, angepasst an den Gezeitenkalender 2026 des BSH (Hoch- und Niedrigwasserzeiten) und
     * an den mittleren Hub 3,50 m; Mittel so, dass MThw NHN +1,60 m und MTnw −1,90 m [G].
     */
    public static Tide husum() {
        Tide t = new Tide();
        t.gauge = "Husum";
        t.mean = -0.1861;
        t.add("M2", M2, 1.7816, 195.03).add("S2", S2, 0.3769, 91.29).add("N2", N2, 0.2626, 70.85).add("K2", K2, 0.1643, 232.23)
                .add("K1", K1, 0.0531, 39.58).add("O1", O1, 0.0602, 115.05)
                .add("M4", M4, 0.0403, 25.71).add("MS4", MS4, 0.0221, 218.30).add("MN4", MN4, 0.0090, 263.23).add("M6", M6, 0.1080, 27.23);
        return t;
    }

    /**
     * Pellworm [G]: der Hub kleiner (3,3 statt 3,5 m), Hochwasser rund 8 min vor Husum (Vergleich zweier Vorhersagen,
     * nicht amtlich). Die Oberschwingungen wie in Husum.
     */
    public static Tide pellworm() { Tide t = husum().shifted(-8, 3.3 / 3.5); t.gauge = "Pellworm [G]"; return t; }

    /**
     * Der seeseitige Rand des Watts zwischen Pellworm und Nordstrand (Norderhever) [G]: Husum 27 min früher, der Hub wie
     * Pellworm. Mit der Laufzeit aus {@link Flood} (rund 22 min bis zur Hallig Südfall) hat Südfall sein Hochwasser
     * dann 5 min vor Husum, Pellworm 8 min.
     */
    public static Tide hever() { Tide t = husum().shifted(-27, 3.3 / 3.5); t.gauge = "Norderhever [G]"; return t; }

    /**
     * Seit Rungholt Phase 17 (Rückfluss nach Lighthouse): Pegel Bremerhaven Alter Leuchtturm, zehn Partialtiden, angepasst
     * an 112 Hoch- und Niedrigwasser vom 20.10. bis 17.11.2026 (tide-forecast.com, nicht amtlich; Zeiten im Mittel
     * 10 min, Höhen 14 cm genau; K2 wie in Husum an S2 gebunden). Höhen in m um das Mittelwasser ({@code mean} = 0),
     * mittlerer Hub 3,6 m. Gegenprobe: am 17.10.2026 Hochwasser 05:34 MESZ, das BSH nennt 05:17 [G].
     */
    public static Tide bremerhaven() {
        Tide t = new Tide();
        t.gauge = "Bremerhaven [G]";
        t.mean = 0;
        t.add("M2", M2, 1.8989, 185.01).add("S2", S2, 0.3226, 67.97).add("N2", N2, 0.1887, 71.52).add("K2", K2, 0.1406, 208.91)
                .add("K1", K1, 0.0599, 20.01).add("O1", O1, 0.0874, 103.09)
                .add("M4", M4, 0.0735, 183.21).add("MS4", MS4, 0.0647, 51.48).add("MN4", MN4, 0.0101, 96.23).add("M6", M6, 0.1320, 19.13);
        return t;
    }

    /**
     * Seit Rungholt Phase 17: der Leuchtturm Roter Sand in der Außenweser [G]: Bremerhaven eine Stunde früher, der Hub auf
     * 3,0 m im Mittel gestreckt (Werkbuch Lighthouse, Abschnitt 3): Nipp rund 2,0 m, Spring bis 3,8 m.
     */
    public static Tide roterSand() { Tide t = bremerhaven().shifted(-60, 3.0 / 3.61); t.gauge = "Roter Sand [G]"; return t; }

    /** Ein Becken für die Demo: reine M2 mit 1 m Amplitude und etwas M4. */
    public static Tide basin() {
        Tide t = new Tide();
        t.gauge = "Becken";
        t.add("M2", M2, 1.0, 0).add("M4", M4, 0.12, -60);
        return t;
    }

    /** Eine Partialtide anhängen. */
    public Tide add(String name, double speed, double amplitude, double phaseDeg) {
        constituents.add(new Constituent(name, speed, amplitude, phaseDeg));
        return this;
    }

    /**
     * Derselbe Pegel, um {@code minutes} verschoben (negativ = früher) und im Hub mit {@code rangeFactor} gestreckt (um
     * das Mittel). Für Nachbarorte, deren Kurve wie die des Bezugspegels läuft [G].
     */
    public Tide shifted(double minutes, double rangeFactor) {
        Tide t = new Tide();
        t.gauge = gauge + String.format(Locale.GERMAN, " %+.0f min", minutes);
        t.mean = mean;
        t.cellSize = cellSize;
        t.seepage = seepage;
        t.maxPuddle = maxPuddle;
        t.quality = quality;
        for (Constituent c : constituents)
            t.add(c.name(), c.speedDegPerHour(), c.amplitude() * rangeFactor, c.phaseDeg() + c.speedDegPerHour() * minutes / 60.0);
        return t;
    }

    // ------------------------------------------------------------------ Abfragen

    /** Pegel zur Zeit in Stunden seit 1970 (UTC): Mittel + Σ A·cos(ω·t − φ) + Windstau. */
    public double levelAt(double hours) { return astronomical(hours) + surge; }

    /** Pegel ohne Windstau (der Rechenwert der Gezeitentafel). */
    public double astronomical(double hours) {
        double h = mean;
        for (Constituent c : constituents) h += c.amplitude() * Math.cos(Math.toRadians(c.speedDegPerHour() * hours - c.phaseDeg()));
        return h;
    }

    /** Änderung des Pegels (m je Stunde). */
    public double slopeAt(double hours) {
        double s = 0;
        for (Constituent c : constituents) {
            double w = Math.toRadians(c.speedDegPerHour());
            s -= c.amplitude() * w * Math.sin(w * hours - Math.toRadians(c.phaseDeg()));
        }
        return s;
    }

    /** Pegel zur Zeit in ms seit 1970. */
    public double levelAtMillis(double epochMs) { return levelAt(epochMs / 3_600_000.0); }

    /** Steigt das Wasser (Flut)? */
    public boolean rising(double hours) { return slopeAt(hours) > 0; }

    /** Hoch- und Niedrigwasser zwischen zwei Zeiten (Stunden seit 1970), ohne Windstau, auf eine halbe Minute genau. */
    public List<Turn> extremes(double fromHours, double toHours) {
        List<Turn> out = new ArrayList<>();
        double step = 1 / 6.0, t = fromHours, s0 = slopeAt(t);
        while (t < toHours) {
            double t1 = t + step, s1 = slopeAt(t1);
            if (s0 > 0 != s1 > 0) {
                double a = t, b = t1, sa = s0;
                for (int k = 0; k < 30; k++) {
                    double m = (a + b) / 2, sm = slopeAt(m);
                    if (sm > 0 == sa > 0) { a = m; sa = sm; } else b = m;
                }
                double tx = (a + b) / 2;
                if (tx >= fromHours && tx < toHours) out.add(new Turn(tx, astronomical(tx), s0 > 0));
            }
            t = t1;
            s0 = s1;
        }
        return out;
    }

    /** Das nächste Hoch- (high) oder Niedrigwasser nach der Zeit. */
    public Turn next(double hours, boolean high) {
        for (Turn x : extremes(hours, hours + 14)) if (x.high() == high) return x;
        return null;
    }

    /** Tidenkalender eines Tags in einer Zeitzone: alle Hoch- und Niedrigwasser zwischen 0 und 24 Uhr Ortszeit. */
    public List<Turn> calendar(LocalDate day, ZoneId zone) {
        double a = day.atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000.0;
        double b = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000.0;
        return extremes(a, b);
    }

    /** Tidenkalender eines Jahrs (rund 1 410 Hoch- und Niedrigwasser). */
    public List<Turn> calendar(int year, ZoneId zone) {
        double a = LocalDate.of(year, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000.0;
        double b = LocalDate.of(year + 1, 1, 1).atStartOfDay(zone).toInstant().toEpochMilli() / 3_600_000.0;
        return extremes(a, b);
    }

    /** „Hochwasser 7:35 +1,62 m“ in einer Zeitzone. */
    public static String format(Turn t, ZoneId zone) {
        ZonedDateTime z = t.instant().atZone(zone);
        return String.format(Locale.GERMAN, "%s %d:%02d %+.2f m", t.high() ? "Hochwasser" : "Niedrigwasser", z.getHour(), z.getMinute(), t.level());
    }

    /** Periode von Spring zu Spring (Tage) aus M2 und S2: 14,77 Tage. */
    public static double springNeapDays() { return 360 / (S2 - M2) / 24; }

    /**
     * Spring oder Nipp jetzt: der halbtägige Hub dieser Tide geteilt durch den mittleren, aus M2, S2, N2 und K2 als
     * Zeiger (1,3 Springtide, 0,7 Nipptide).
     */
    public double springFactor(double hours) {
        double re = 0, im = 0, m2 = 0, sumA = 0;
        for (Constituent c : constituents) {
            String n = c.name();
            if (!(n.equals("M2") || n.equals("S2") || n.equals("N2") || n.equals("K2"))) continue;
            // Zeiger relativ zu M2: Phase der Differenzfrequenz
            double ph = Math.toRadians((c.speedDegPerHour() - M2) * hours - c.phaseDeg());
            re += c.amplitude() * Math.cos(ph);
            im += c.amplitude() * Math.sin(ph);
            if (n.equals("M2")) m2 = c.amplitude();
            sumA += c.amplitude();
        }
        return m2 <= 0 ? 1 : Math.hypot(re, im) / m2;
    }

    /** Die nächste Springtide (größter halbtägiger Hub) nach der Zeit, in Stunden seit 1970. */
    public double nextSpring(double hours) {
        double best = hours, bv = -1, prev = springFactor(hours);
        boolean rising = springFactor(hours + 1) > prev;
        for (double t = hours + 1; t < hours + 16 * 24; t += 1) {
            double v = springFactor(t);
            if (!rising && v > prev) rising = true;
            if (rising && v < prev) { best = t - 1; bv = prev; break; }
            prev = v;
        }
        return bv < 0 ? hours : best;
    }

    /** Zeit, die eine lange Welle für eine Strecke bei einer Tiefe braucht (s): Strecke / √(g·h). */
    public static double travelSeconds(double distance, double depth) { return distance / Math.sqrt(9.81 * Math.max(0.05, depth)); }

    // ------------------------------------------------------------------ Windstau

    /** Richtung, aus der der Wind am stärksten aufstaut (Grad), und Stau bei 22 m/s aus ihr (m) [G]. */
    public static final double SURGE_FROM_DEG = 290, SURGE_AT_22 = 1.5;

    /**
     * Windstau, auf den der Pegel bei anhaltendem Wind zuläuft (m) [G]: quadratisch im Wind, der Anteil gegen die Küste
     * aus Westnordwest; Sturm (Bft 9, 22 m/s) 1,5 m, Orkan (33 m/s) rund 3,4 m. Ablandiger Wind (Ost) senkt den Pegel um
     * höchstens die Hälfte davon.
     */
    public static double surgeTarget(double windMs, double fromDeg) {
        double c = Math.cos(Math.toRadians(fromDeg - SURGE_FROM_DEG));
        double k = SURGE_AT_22 / (22.0 * 22.0);
        return c >= 0 ? k * windMs * windMs * c : 0.5 * k * windMs * windMs * c;
    }

    /** Zeitkonstante des Windstaus (Stunden Szenenzeit) [G]: die Bucht füllt und leert sich nicht schlagartig. */
    public static final double SURGE_HOURS = 3;

    /** Den Windstau um dtHours auf das Ziel zuführen. */
    public void stepSurge(double target, double dtHours) {
        double a = 1 - Math.exp(-Math.max(0, dtHours) / SURGE_HOURS);
        surge += (target - surge) * a;
    }

    // ------------------------------------------------------------------ Ereignisse

    /**
     * Zeit weiter (Stunden seit 1970) und Ereignisse melden: Hoch- und Niedrigwasser, Kenterung (Wechsel von Flut und
     * Ebbe). Sprünge über mehr als eine Stunde melden nichts (nur der neue Stand gilt).
     */
    public void step(double hours) {
        double lv = levelAt(hours), sl = slopeAt(hours);
        if (!Double.isNaN(lastH) && hours > lastH && hours - lastH < 1 && lastSlope > 0 != sl > 0) {
            Turn x = extremes(lastH, hours + 1e-6).stream().findFirst().orElse(new Turn(hours, lv, lastSlope > 0));
            if (x.high() && onHighWater != null) onHighWater.extreme(x.hours(), x.level() + surge);
            if (!x.high() && onLowWater != null) onLowWater.extreme(x.hours(), x.level() + surge);
            if (onTurn != null) onTurn.extreme(x.hours(), x.level() + surge);
        }
        lastH = hours;
        lastLevel = lv;
        lastSlope = sl;
    }

    /** „tide: Stufe 4 · Husum · 10 Partialtiden · MEDIUM“ */
    public String describe() {
        return String.format(Locale.GERMAN, "tide: %s · %s · %d Partialtiden · Windstau %+.2f m · %s",
                STAGE == 1 ? "Hülle (Phase 1)" : "Stufe " + STAGE, gauge, constituents.size(), surge, quality);
    }
}
