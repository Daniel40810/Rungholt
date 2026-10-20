package com.dan.fog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ein Nebelschallsignal mit Kennung in der Schreibweise des Leuchtfeuerverzeichnisses: {@code Horn Mo(R) 30s},
 * {@code Horn(2) 20s}, {@code Tyfon 120s}, {@code Sirene(3) 60s}. Aus der Kennung wird der Takt: wann innerhalb der
 * Periode ein Ton beginnt und wie lange er dauert.
 * <ul>
 * <li><b>Morse</b> {@code Mo(X)}: Punkt 1 s, Strich 3 s, Pause zwischen den Zeichen 1 s, zwischen Buchstaben 3 s.</li>
 * <li><b>Gruppe</b> {@code (n)}: n Töne von je 2 s mit 2 s Pause.</li>
 * <li><b>Einzelton</b> ohne Klammer: ein Ton von 3 s; {@code Tyfon} (Schiff in Fahrt, KVR Regel 35): 5 s.</li>
 * </ul>
 * Die Tonlängen sind gestaltet, nicht aus einem Verzeichnis; die Periode und das Muster kommen aus der Kennung.
 */
public final class Signal {
    /** Art des Schallgebers (für den Klang in einem späteren Package). */
    public enum Kind { HORN, TYFON, SIRENE, GLOCKE }

    /** Ein Ton: Beginn in s ab Periodenbeginn und Dauer in s. */
    public record Blast(double start, double seconds) { }

    public final String name, text;
    public final Kind kind;
    public final double period;
    public final List<Blast> blasts;
    /** Ort des Schallgebers (m) und Lautstärke 0..1. */
    public volatile double x, y, z, strength = 1;
    /** Phase in s (verschiebt den Takt, damit zwei Signale nicht gleichzeitig tönen). */
    public volatile double phase;
    /** Tönt das Signal gerade (Nebel unter der Schwelle)? Wird von {@link Fog#step} gesetzt. */
    volatile boolean active;
    volatile long count;
    volatile double lastStart = -1e9, lastSeconds;

    static final Pattern P = Pattern.compile("^\\s*(Horn|Tyfon|Sirene|Glocke)\\s*(?:Mo\\s*\\(\\s*([A-Za-z]+)\\s*\\)|\\(\\s*(\\d+)\\s*\\))?\\s*(\\d+(?:[.,]\\d+)?)\\s*s\\s*$",
            Pattern.CASE_INSENSITIVE);

    static final String[] MORSE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};

    public Signal(String name, String characteristic, double x, double y, double z) {
        this.name = name;
        this.text = characteristic.strip();
        Matcher m = P.matcher(text);
        if (!m.matches()) throw new IllegalArgumentException("Nebelsignal: Kennung nicht lesbar: „" + characteristic + "“ (Beispiele: Horn Mo(R) 30s, Horn(2) 20s, Tyfon 120s)");
        kind = Kind.valueOf(m.group(1).toUpperCase(Locale.ROOT));
        period = Double.parseDouble(m.group(4).replace(',', '.'));
        List<Blast> b = new ArrayList<>();
        double t = 0;
        if (m.group(2) != null) {
            String letters = m.group(2).toUpperCase(Locale.ROOT);
            for (int li = 0; li < letters.length(); li++) {
                char c = letters.charAt(li);
                if (c < 'A' || c > 'Z') throw new IllegalArgumentException("Nebelsignal: Morsezeichen „" + c + "“");
                String code = MORSE[c - 'A'];
                for (int k = 0; k < code.length(); k++) {
                    double d = code.charAt(k) == '.' ? 1 : 3;
                    b.add(new Blast(t, d));
                    t += d + (k + 1 < code.length() ? 1 : 0);
                }
                t += 3;
            }
        } else {
            int n = m.group(3) != null ? Integer.parseInt(m.group(3)) : 1;
            double d = n == 1 && m.group(3) == null ? (kind == Kind.TYFON ? 5 : 3) : 2;
            for (int k = 0; k < n; k++) {
                b.add(new Blast(t, d));
                t += d + 2;
            }
        }
        double end = b.get(b.size() - 1).start() + b.get(b.size() - 1).seconds();
        if (end > period) throw new IllegalArgumentException("Nebelsignal: Die Töne (" + fmt(end) + " s) passen nicht in die Periode von " + fmt(period) + " s");
        blasts = List.copyOf(b);
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Ist zur Zeit t (s) ein Ton an? */
    public boolean soundingAt(double t) {
        double p = mod(t + phase, period);
        for (Blast b : blasts) if (p >= b.start() && p < b.start() + b.seconds()) return true;
        return false;
    }

    /** Die Töne, die im Zeitraum [t0, t1) beginnen, als Beginnzeiten mit Dauer: {t, s, t, s, …}. */
    public double[] startsBetween(double t0, double t1) {
        if (t1 <= t0) return new double[0];
        List<Double> out = new ArrayList<>();
        double k0 = Math.floor((t0 + phase) / period);
        for (double k = k0; (k * period - phase) < t1; k++)
            for (Blast b : blasts) {
                double s = k * period - phase + b.start();
                if (s >= t0 && s < t1) { out.add(s); out.add(b.seconds()); }
            }
        double[] r = new double[out.size()];
        for (int i = 0; i < r.length; i++) r[i] = out.get(i);
        return r;
    }

    /** Gesamte Tonzeit je Periode in s. */
    public double onSeconds() {
        double s = 0;
        for (Blast b : blasts) s += b.seconds();
        return s;
    }

    /** Tönt es (Nebel unter der Schwelle)? */
    public boolean active() { return active; }
    /** Zahl der Töne seit dem Start. */
    public long count() { return count; }
    /** Beginn (Szenenzeit) und Dauer des letzten Tons. */
    public double lastStart() { return lastStart; }
    public double lastSeconds() { return lastSeconds; }

    /** „Horn Mo(R) 30s: kurz-lang-kurz, 5 s Ton je 30 s“ */
    public String describe() {
        StringBuilder b = new StringBuilder();
        for (Blast bl : blasts) b.append(b.length() > 0 ? "-" : "").append(bl.seconds() >= 3 ? "lang" : "kurz");
        return String.format(Locale.GERMAN, "%s: %s, %s s Ton je %s s", text, b, fmt(onSeconds()), fmt(period));
    }

    static double mod(double a, double m) { double r = a % m; return r < 0 ? r + m : r; }

    static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.GERMAN, "%.1f", v); }
}
