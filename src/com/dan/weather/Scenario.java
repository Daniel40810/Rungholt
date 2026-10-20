package com.dan.weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Ein Regiebuch: Name, Beginn (Uhrzeit), empfohlener Zeitraffer und eine Liste von Schritten. Jeder Schritt hat eine
 * Lage, eine Dauer in Stunden, einen Text, Vorgaben für einzelne Größen (sonst gelten die Zielwerte der Lage) und
 * einen Kamerawunsch für die App.
 * <p>
 * Als Text (für Datei oder Datenbank, Phase 15), eine Zeile je Angabe:
 * <pre>
 * Szenario: Ein Sturmtag
 * Beginn: 06:00
 * Zeitraffer: 420
 * Schritt: 3 | KLAR | Morgenstille mit Dunst | WIND=1.5 VISIBILITY=6 | Aussen1
 * </pre>
 */
public record Scenario(String name, double startHour, double lapse, List<Step> steps) {

    /** Ein Schritt; values hat je {@link Weather.Var} einen Wert oder NaN. */
    public record Step(String text, Weather.Phase phase, double hours, double[] values, String camera) {
        public double value(Weather.Var v) { return values == null ? Double.NaN : values[v.ordinal()]; }

        /** Schritt mit Vorgaben als Paare (Größe, Wert). */
        public static Step of(double hours, Weather.Phase p, String text, String camera, Object... pairs) {
            double[] v = new double[Weather.N];
            Arrays.fill(v, Double.NaN);
            for (int i = 0; i + 1 < pairs.length; i += 2) v[((Weather.Var) pairs[i]).ordinal()] = ((Number) pairs[i + 1]).doubleValue();
            return new Step(text, p, hours, v, camera == null ? "" : camera);
        }
    }

    public Scenario {
        steps = List.copyOf(steps);
    }

    /** Länge in Stunden. */
    public double hours() {
        double h = 0;
        for (Step s : steps) h += s.hours();
        return h;
    }

    /** Beginn des Schritts i in Stunden ab Szenariobeginn. */
    public double stepStart(int i) {
        double h = 0;
        for (int k = 0; k < i; k++) h += steps.get(k).hours();
        return h;
    }

    /**
     * „Ein Sturmtag“ (Werkbuch, Abschnitt 6): Morgenstille bei Bft 1 mit Dunst, aufziehende Dünung und Cirren, Front mit
     * Regenvorhang am Mittag, Bft 9 am Nachmittag, Gewitter in der Dämmerung, Nacht mit Sturm und Leuchtfeuer, am
     * nächsten Morgen nach Sonnenaufgang ein letzter Schauer und Aufklaren mit Regenbogen. 28,3 Stunden, im Zeitraffer
     * 420 : 1 gut 4 Minuten.
     */
    public static Scenario stormDay() {
        Weather.Var W = Weather.Var.WIND, V = Weather.Var.VISIBILITY, F = Weather.Var.FOG, C = Weather.Var.COVER, H = Weather.Var.HIGH,
                R = Weather.Var.RAIN, K = Weather.Var.CLOUD, D = Weather.Var.DIRECTION, T = Weather.Var.THUNDER, S = Weather.Var.SWELL;
        List<Step> l = new ArrayList<>();
        l.add(Step.of(3.0, Weather.Phase.KLAR, "Morgenstille mit Dunst", "Aussen1", W, 1.2, V, 7, F, 0.25, C, 1.0, H, 0.0, S, 0.4, D, 140));
        l.add(Step.of(3.0, Weather.Phase.AUFZIEHEND, "Dünung und Cirren ziehen auf", "Drohne", W, 5.5, C, 2.5, H, 0.85));
        l.add(Step.of(3.0, Weather.Phase.FRONT, "Front mit Regenvorhang", "Aussen1"));
        l.add(Step.of(3.5, Weather.Phase.STURM, "Sturm, Bft 9", "Brecher", W, 23.5));
        l.add(Step.of(2.0, Weather.Phase.GEWITTER, "Gewitter in der Dämmerung", "Aussen3", K, 4, T, 3));
        l.add(Step.of(6.5, Weather.Phase.STURM, "Sturmnacht mit Leuchtfeuer", "Laternenraum", W, 20.0, R, 4.0, T, 1));
        l.add(Step.of(5.0, Weather.Phase.ABKLINGEND, "Der Sturm flaut ab", "Aussen1"));
        l.add(Step.of(0.8, Weather.Phase.ABKLINGEND, "Ein letzter Schauer", "Aussen1", R, 3.0, C, 5.5, K, 0, W, 9.0));
        l.add(Step.of(1.5, Weather.Phase.AUFKLAREND, "Aufklaren mit Regenbogen", "Regenbogen", C, 3.5));
        return new Scenario("Ein Sturmtag", 6.0, 420, l);
    }

    /**
     * „Ein Nebeltag“ (Phase 13): klare Morgendämmerung, dann zieht eine Nebelbank auf, mittags dichter Seenebel mit
     * Nebelhorn und einem Schiff, das sich mit dem Typhon meldet, nachmittags liegt der Nebel so flach, dass der Turm
     * herausragt, gegen Abend löst er sich auf. 14 Stunden, im Zeitraffer 200 : 1 gut 4 Minuten.
     */
    public static Scenario fogDay() {
        Weather.Var W = Weather.Var.WIND, F = Weather.Var.FOG, D = Weather.Var.DIRECTION, C = Weather.Var.COVER, V = Weather.Var.VISIBILITY, H = Weather.Var.HUMIDITY;
        List<Step> l = new ArrayList<>();
        l.add(Step.of(2.0, Weather.Phase.KLAR, "Klare Morgendämmerung", "Aussen1", W, 3.0, D, 200, C, 1.0, F, 0.0, V, 25));
        l.add(Step.of(1.5, Weather.Phase.NEBEL, "Eine Nebelbank zieht auf", "Drohne", F, 0.55));
        l.add(Step.of(4.0, Weather.Phase.NEBEL, "Dichter Seenebel, das Nebelhorn ruft", "Schiffsbrücke", F, 1.0, W, 2.0));
        l.add(Step.of(2.5, Weather.Phase.NEBEL, "Flacher Nebel, der Turm ragt heraus", "Drohne", F, 0.42, W, 3.0));
        l.add(Step.of(2.0, Weather.Phase.KLAR, "Der Nebel lichtet sich", "Möwenflug", F, 0.12, W, 4.0, H, 85));
        l.add(Step.of(2.0, Weather.Phase.KLAR, "Klarer Nachmittag", "Aussen1", F, 0.0, W, 5.0));
        return new Scenario("Ein Nebeltag", 5.5, 200, l);
    }

    /**
     * „Ein Nebelmorgen im Herbst“ (seit Haithabu Phase 3): Strahlungsnebel nach einer klaren, windstillen Nacht über
     * Wasser und Niederungen, kühl und feucht. Nach Sonnenaufgang wärmt die Sonne den Boden, der Nebel hebt sich als
     * Schicht (die App nimmt dafür die Untergrenze von com.dan.fog), reißt in Fetzen auf und ist am Vormittag fort; ein
     * klarer, milder Herbsttag folgt. 10 Stunden ab 05:00, im Zeitraffer 240 : 1 gut 2½ Minuten. Kamerawünsche: die
     * Namen der Ansichten der App.
     */
    public static Scenario autumnFogMorning() {
        Weather.Var W = Weather.Var.WIND, F = Weather.Var.FOG, D = Weather.Var.DIRECTION, C = Weather.Var.COVER, V = Weather.Var.VISIBILITY,
                H = Weather.Var.HUMIDITY, T = Weather.Var.TEMPERATURE, G = Weather.Var.GUST, P = Weather.Var.PRESSURE;
        List<Step> l = new ArrayList<>();
        l.add(Step.of(1.5, Weather.Phase.NEBEL, "Nebel vor Tag, windstill", "Hochburg", F, 0.9, W, 0.6, D, 160, C, 0.5, V, 10, H, 99, T, 5, G, 0.05, P, 1026));
        l.add(Step.of(1.25, Weather.Phase.NEBEL, "Sonnenaufgang im Nebel", "Hochburg", F, 0.85, W, 0.8, H, 99, T, 5.5));
        l.add(Step.of(1.5, Weather.Phase.NEBEL, "Der Nebel steigt", "Hochburg", F, 0.6, W, 1.4, H, 96, T, 8));
        l.add(Step.of(1.25, Weather.Phase.KLAR, "Nebelfetzen über dem Wasser", "Vom Noor", F, 0.22, W, 2.2, H, 88, T, 10, C, 1.0));
        l.add(Step.of(4.5, Weather.Phase.KLAR, "Ein klarer Herbsttag", "Vom Noor", F, 0.0, W, 3.0, C, 2.0, V, 30, H, 75, T, 13));
        return new Scenario("Ein Nebelmorgen im Herbst", 5.0, 240, l);
    }

    /** Eine ruhige Lage für die freie Regie zum Vergleich: zwei Tage Hochdruck mit Seenebel am Morgen. */
    public static Scenario calmDays() {
        List<Step> l = new ArrayList<>();
        l.add(Step.of(4, Weather.Phase.KLAR, "Seenebel am Morgen", "Aussen1", Weather.Var.FOG, 0.8, Weather.Var.WIND, 2.0, Weather.Var.HUMIDITY, 98));
        l.add(Step.of(20, Weather.Phase.KLAR, "Hochdruck", "Aussen1"));
        l.add(Step.of(4, Weather.Phase.KLAR, "Seenebel am Morgen", "Aussen1", Weather.Var.FOG, 0.7, Weather.Var.WIND, 2.5, Weather.Var.HUMIDITY, 97));
        l.add(Step.of(20, Weather.Phase.KLAR, "Hochdruck", "Aussen1"));
        return new Scenario("Ruhige Tage", 5.0, 600, l);
    }

    /** Als Text (siehe Klassenkommentar). */
    public String toText() {
        StringBuilder b = new StringBuilder();
        b.append("Szenario: ").append(name).append('\n');
        b.append(String.format(Locale.ROOT, "Beginn: %02d:%02d%n", (int) startHour, (int) Math.round((startHour - (int) startHour) * 60)));
        b.append(String.format(Locale.ROOT, "Zeitraffer: %s%n", num(lapse)));
        for (Step s : steps) {
            b.append("Schritt: ").append(num(s.hours())).append(" | ").append(s.phase().name()).append(" | ").append(s.text()).append(" |");
            for (Weather.Var v : Weather.Var.values()) if (!Double.isNaN(s.value(v))) b.append(' ').append(v.name()).append('=').append(num(s.value(v)));
            b.append(" | ").append(s.camera()).append('\n');
        }
        return b.toString();
    }

    private static String num(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    /** Liest ein Regiebuch aus Text; unbekannte Zeilen sind Fehler mit Zeilennummer. */
    public static Scenario parse(String text) {
        String name = "Szenario";
        double start = 6, lapse = 1;
        List<Step> l = new ArrayList<>();
        int no = 0;
        for (String raw : text.split("\\R")) {
            no++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int c = line.indexOf(':');
            if (c < 0) throw new IllegalArgumentException("Zeile " + no + ": ohne Doppelpunkt");
            String key = line.substring(0, c).strip(), val = line.substring(c + 1).strip();
            switch (key) {
                case "Szenario" -> name = val;
                case "Beginn" -> {
                    String[] hm = val.split(":");
                    start = Integer.parseInt(hm[0].strip()) + (hm.length > 1 ? Integer.parseInt(hm[1].strip()) / 60.0 : 0);
                }
                case "Zeitraffer" -> lapse = Double.parseDouble(val.replace(',', '.'));
                case "Schritt" -> {
                    String[] f = val.split("\\|", -1);
                    if (f.length < 3) throw new IllegalArgumentException("Zeile " + no + ": Schritt braucht Dauer | Lage | Text");
                    double h = Double.parseDouble(f[0].strip().replace(',', '.'));
                    Weather.Phase p;
                    try { p = Weather.Phase.valueOf(f[1].strip()); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Zeile " + no + ": unbekannte Lage " + f[1].strip()); }
                    double[] v = new double[Weather.N];
                    Arrays.fill(v, Double.NaN);
                    if (f.length > 3)
                        for (String kv : f[3].strip().split("\\s+")) {
                            if (kv.isEmpty()) continue;
                            String[] q = kv.split("=");
                            try { v[Weather.Var.valueOf(q[0]).ordinal()] = Double.parseDouble(q[1].replace(',', '.')); }
                            catch (RuntimeException e) { throw new IllegalArgumentException("Zeile " + no + ": Vorgabe " + kv); }
                        }
                    l.add(new Step(f[2].strip(), p, h, v, f.length > 4 ? f[4].strip() : ""));
                }
                default -> throw new IllegalArgumentException("Zeile " + no + ": unbekannt „" + key + "“");
            }
        }
        if (l.isEmpty()) throw new IllegalArgumentException("Szenario ohne Schritte");
        return new Scenario(name, start, lapse, l);
    }
}
