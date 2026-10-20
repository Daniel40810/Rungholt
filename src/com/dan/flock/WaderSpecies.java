package com.dan.flock;

import java.util.List;
import java.util.Locale;

/**
 * Seit Rungholt Phase 8: die Vögel des Watts als Datensätze (F14 A). Je Art Maße, Farben, Flug, Gang und Fressen, wo
 * sie frisst (Abstand zur Wasserkante, wie tief sie watet, ob sie Schlick mag), wie dicht die Gruppe steht, wie groß
 * die Gruppen sind, Fluchtdistanz, die Monate, in denen sie da ist, und ob sie im Schwarm wendet. Eine neue Art ist ein
 * neuer Eintrag in {@link #ALL}; {@link WaderMesh} baut ihr Netz aus den Zahlen. Werte nach Bestimmungsbüchern und den
 * Zählungen im Wattenmeer gerundet [G].
 *
 * @param key        kurzer Name (Bedienfeld, Prüfung)
 * @param label      deutscher Name
 * @param length     Länge Schnabel bis Schwanz (m)
 * @param span       Spannweite (m)
 * @param legLength  Bein bis zum Bauch (m)
 * @param bill       Länge des Schnabels (m); {@code billCurve} &gt; 0 nach oben gebogen (Säbelschnäbler), &lt; 0 nach unten
 * @param neck       Hals zwischen Rumpf und Kopf (m)
 * @param back       Farbe Rücken und Flügel oben (linear)
 * @param belly      Farbe Bauch und Flügel unten
 * @param head       Farbe Kopf und Hals
 * @param wingTip    Farbe der Handschwingen oben
 * @param billColor  Farbe des Schnabels
 * @param legColor   Farbe der Beine
 * @param breast     Farbe eines Brustbands oder Bauchflecks (NaN-frei; gleich {@code belly}, wenn keins)
 * @param speed      Reiseflug (m/s)
 * @param flapHz     Flügelschläge je Sekunde
 * @param walk       Gehen beim Fressen (m/s)
 * @param probeHz    Stochern je Sekunde (0 = pickt selten)
 * @param edgeMin    am liebsten so weit von der Wasserkante (m, Untergrenze)
 * @param edgeMax    … bis so weit
 * @param wade       so tief watet sie (m)
 * @param mud        Vorliebe für Schlick: −1 Sand, 0 egal, 1 Schlick
 * @param spacing    Abstand in der Gruppe am Boden (m)
 * @param groupMin   kleinste Gruppe
 * @param groupMax   größte Gruppe
 * @param flight     Fluchtdistanz (m)
 * @param months     Anteil, der je Monat (Jan…Dez) da ist (0..1)
 * @param murmur     wendet als Schwarm (Alpenstrandläufer)
 * @param graze      weidet auf der Salzwiese (Ringelgans) statt im Watt zu stochern
 */
public record WaderSpecies(String key, String label, double length, double span, double legLength, double bill, double billCurve, double neck,
                           double[] back, double[] belly, double[] head, double[] wingTip, double[] billColor, double[] legColor, double[] breast,
                           double speed, double flapHz, double walk, double probeHz, double edgeMin, double edgeMax, double wade, double mud,
                           double spacing, int groupMin, int groupMax, double flight, double[] months, boolean murmur, boolean graze) {

    static double[] c(double r, double g, double b) { return new double[]{r, g, b}; }

    /** Austernfischer: schwarz-weiß, roter Schnabel, rosa Beine; ganzjährig, Gruppen bis 200. */
    public static final WaderSpecies AUSTERNFISCHER = new WaderSpecies("austernfischer", "Austernfischer", 0.42, 0.83, 0.075, 0.08, 0, 0.02,
            c(0.018, 0.018, 0.02), c(0.80, 0.80, 0.78), c(0.018, 0.018, 0.02), c(0.015, 0.015, 0.018), c(0.85, 0.22, 0.03), c(0.75, 0.40, 0.38), c(0.80, 0.80, 0.78),
            12, 4.5, 0.35, 0.6, 15, 250, 0.03, 0.0, 2.5, 20, 200, 90,
            new double[]{0.9, 0.9, 1, 0.8, 0.7, 0.7, 0.8, 1, 1, 1, 1, 0.95}, false, false);
    /** Alpenstrandläufer: klein, graubraun, weißer Bauch (im Sommer schwarzer Bauchfleck); Schwärme bis zu einigen Tausend, wendet. */
    public static final WaderSpecies ALPENSTRANDLAEUFER = new WaderSpecies("alpenstrandlaeufer", "Alpenstrandläufer", 0.18, 0.38, 0.035, 0.032, -0.15, 0.008,
            c(0.17, 0.14, 0.11), c(0.80, 0.80, 0.78), c(0.19, 0.17, 0.14), c(0.06, 0.055, 0.05), c(0.02, 0.02, 0.02), c(0.03, 0.03, 0.03), c(0.03, 0.03, 0.035),
            14, 12, 0.45, 3.0, 0, 25, 0.02, 0.4, 1.1, 200, 3000, 50,
            new double[]{0.7, 0.6, 0.6, 0.8, 1, 0.25, 0.3, 0.8, 1, 1, 0.9, 0.8}, true, false);
    /** Säbelschnäbler: weiß mit schwarzer Kappe und Flügelbändern, aufgebogener Schnabel, lange blaugraue Beine; watet und säbelt. */
    public static final WaderSpecies SAEBELSCHNAEBLER = new WaderSpecies("saebelschnaebler", "Säbelschnäbler", 0.44, 0.77, 0.12, 0.085, 0.6, 0.03,
            c(0.82, 0.82, 0.80), c(0.84, 0.84, 0.82), c(0.82, 0.82, 0.80), c(0.02, 0.02, 0.022), c(0.02, 0.02, 0.02), c(0.30, 0.38, 0.45), c(0.84, 0.84, 0.82),
            12, 4.0, 0.3, 1.2, 0, 15, 0.12, 0.5, 1.5, 20, 250, 120,
            new double[]{0, 0, 0.3, 0.9, 1, 1, 1, 1, 0.9, 0.5, 0.1, 0}, false, false);
    /** Brandgans: weiß, schwarzer Kopf, rotbraunes Brustband, roter Schnabel; seiht im Schlick. */
    public static final WaderSpecies BRANDGANS = new WaderSpecies("brandgans", "Brandgans", 0.62, 1.10, 0.09, 0.055, 0.1, 0.07,
            c(0.80, 0.80, 0.78), c(0.80, 0.80, 0.78), c(0.015, 0.03, 0.02), c(0.02, 0.02, 0.022), c(0.70, 0.07, 0.06), c(0.70, 0.30, 0.30), c(0.42, 0.15, 0.05),
            15, 4.2, 0.25, 0.8, 20, 300, 0.05, 1.0, 3.5, 10, 120, 150,
            new double[]{0.9, 0.9, 1, 1, 0.9, 0.6, 0.5, 0.4, 0.7, 1, 1, 0.9}, false, false);
    /** Silbermöwe: weiß, Mantel silbergrau, schwarze Spitzen, gelber Schnabel; überall im Watt. */
    public static final WaderSpecies SILBERMOEWE = new WaderSpecies("silbermoewe", "Silbermöwe", 0.60, 1.40, 0.12, 0.065, -0.1, 0.04,
            c(0.33, 0.36, 0.40), c(0.80, 0.80, 0.78), c(0.80, 0.80, 0.78), c(0.025, 0.025, 0.03), c(0.75, 0.55, 0.05), c(0.60, 0.38, 0.30), c(0.80, 0.80, 0.78),
            10.5, 3.1, 0.4, 0.2, 0, 400, 0.08, 0.0, 4.0, 5, 60, 70,
            new double[]{1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}, false, false);
    /** Lachmöwe: kleiner, roter Schnabel und rote Beine, im Sommer schokoladenbraune Kappe. */
    public static final WaderSpecies LACHMOEWE = new WaderSpecies("lachmoewe", "Lachmöwe", 0.38, 1.00, 0.09, 0.04, -0.1, 0.03,
            c(0.45, 0.48, 0.52), c(0.82, 0.82, 0.80), c(0.10, 0.06, 0.035), c(0.02, 0.02, 0.022), c(0.55, 0.06, 0.05), c(0.60, 0.08, 0.06), c(0.82, 0.82, 0.80),
            10, 3.6, 0.45, 0.4, 0, 120, 0.06, 0.2, 2.0, 10, 150, 60,
            new double[]{0.9, 0.9, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0.9}, false, false);
    /** Ringelgans: dunkel, schwarzer Hals mit weißem Fleck, weißer Steiß; nur im Winterhalbjahr, weidet auf der Salzwiese. */
    public static final WaderSpecies RINGELGANS = new WaderSpecies("ringelgans", "Ringelgans", 0.58, 1.15, 0.08, 0.035, 0, 0.09,
            c(0.06, 0.055, 0.05), c(0.13, 0.12, 0.11), c(0.012, 0.012, 0.014), c(0.03, 0.03, 0.03), c(0.02, 0.02, 0.02), c(0.03, 0.03, 0.03), c(0.012, 0.012, 0.014),
            16, 4.0, 0.3, 1.5, -400, 0, 0.0, 0.0, 1.6, 50, 600, 200,
            new double[]{1, 1, 1, 0.9, 0.3, 0, 0, 0, 0.05, 0.8, 1, 1}, false, true);

    /** Alle Arten des Watts, in der Reihenfolge des Bedienfelds. */
    public static final List<WaderSpecies> ALL = List.of(ALPENSTRANDLAEUFER, AUSTERNFISCHER, SAEBELSCHNAEBLER, BRANDGANS, SILBERMOEWE, LACHMOEWE, RINGELGANS);

    /** Anteil, der im Monat (1..12) da ist. */
    public double presence(int month) { return months[Math.max(1, Math.min(12, month)) - 1]; }

    /** Die Lachmöwe trägt ihre braune Kappe von März bis Juli; sonst nur einen dunklen Fleck hinter dem Auge. */
    public double[] headIn(int month) {
        if (this == LACHMOEWE && (month < 3 || month > 7)) return belly;
        return head;
    }

    /** Der Alpenstrandläufer hat im Brutkleid (April bis August) einen schwarzen Bauchfleck. */
    public boolean summerPatch(int month) { return this == ALPENSTRANDLAEUFER && month >= 4 && month <= 8; }

    public static WaderSpecies byKey(String key) {
        for (WaderSpecies s : ALL) if (s.key.equalsIgnoreCase(key) || s.label.equalsIgnoreCase(key)) return s;
        return null;
    }

    @Override public String toString() { return label; }

    /** „Alpenstrandläufer 0,18 m · Gruppen 200–3000 · Flucht 50 m“ */
    public String describe() { return String.format(Locale.GERMAN, "%s %.2f m · Gruppen %d–%d · Flucht %.0f m", label, length, groupMin, groupMax, flight); }
}
