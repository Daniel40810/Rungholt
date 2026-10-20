package com.dan.buoyancy;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Die Kennung eines Tonnenfeuers in kurzer Schreibweise: {@code Fl G 4s}, {@code Fl(2) R 6s}, {@code Q G},
 * {@code VQ(3) 5s}, {@code Iso 8s}, {@code Oc R 4s}, {@code LFl 10s}, {@code Mo(A) 6s}, {@code F R}. Eigene kleine
 * Kopie (das Leuchtfeuer der App hat einen vollständigen Parser); hier reicht, was Tonnen tragen.
 * Blitz 0,5 s, langer Blitz 2 s, Q 1 s Periode (0,3 s an), VQ 0,5 s (0,2 s an), Morse-Punkt 0,5 s, Strich 1,5 s.
 */
public final class Rhythm {
    public final String text;
    /** Farbe des Feuers: 'W', 'R', 'G', 'Y'. */
    public final char colour;
    public final double period;
    private final double[] on;   // Paare Beginn, Dauer innerhalb der Periode

    static final Pattern P = Pattern.compile("^\\s*(F|Fl|LFl|Q|VQ|Iso|Oc|Mo)\\s*(?:\\(\\s*([0-9A-Za-z]+)\\s*\\))?\\s*([WRGY])?\\s*(?:(\\d+(?:[.,]\\d+)?)\\s*s)?\\s*$");
    static final String[] MORSE = {".-", "-...", "-.-.", "-..", ".", "..-.", "--.", "....", "..", ".---", "-.-", ".-..", "--", "-.", "---", ".--.", "--.-", ".-.",
            "...", "-", "..-", "...-", ".--", "-..-", "-.--", "--.."};

    public Rhythm(String characteristic) {
        text = characteristic.strip();
        Matcher m = P.matcher(text);
        if (!m.matches()) throw new IllegalArgumentException("Tonnenfeuer: Kennung nicht lesbar: „" + characteristic + "“ (Beispiele: Fl G 4s, Q R, Iso 8s)");
        String k = m.group(1), grp = m.group(2);
        colour = m.group(3) != null ? m.group(3).charAt(0) : 'W';
        double per = m.group(4) != null ? Double.parseDouble(m.group(4).replace(',', '.')) : Double.NaN;
        java.util.List<Double> l = new java.util.ArrayList<>();
        switch (k) {
            case "F" -> { per = 1; l.add(0.0); l.add(1.0); }
            case "Iso" -> { if (Double.isNaN(per)) per = 4; l.add(0.0); l.add(per / 2); }
            case "Oc" -> { if (Double.isNaN(per)) per = 4; l.add(0.0); l.add(per * 0.75); }
            case "Mo" -> {
                if (grp == null) throw new IllegalArgumentException("Tonnenfeuer: Mo braucht einen Buchstaben, etwa Mo(A) 6s");
                double t = 0;
                for (char c : grp.toUpperCase(Locale.ROOT).toCharArray()) {
                    String code = MORSE[c - 'A'];
                    for (int i = 0; i < code.length(); i++) {
                        double d = code.charAt(i) == '.' ? 0.5 : 1.5;
                        l.add(t); l.add(d);
                        t += d + 0.5;
                    }
                    t += 1;
                }
                if (Double.isNaN(per)) per = t + 2;
            }
            default -> {
                int n = grp != null ? Integer.parseInt(grp) : 1;
                double flash = k.equals("LFl") ? 2 : k.equals("Q") ? 0.3 : k.equals("VQ") ? 0.2 : 0.5;
                double step = k.equals("Q") ? 1 : k.equals("VQ") ? 0.5 : flash + 1;
                if (Double.isNaN(per)) per = k.equals("Q") || k.equals("VQ") ? (grp == null ? step : step * n + 2 * step) : 4;
                for (int i = 0; i < n; i++) { l.add(i * step); l.add(flash); }
            }
        }
        double end = l.get(l.size() - 2) + l.get(l.size() - 1);
        if (end > per + 1e-9) throw new IllegalArgumentException("Tonnenfeuer: „" + text + "“ passt nicht in " + per + " s");
        period = per;
        on = new double[l.size()];
        for (int i = 0; i < on.length; i++) on[i] = l.get(i);
    }

    /** Ist das Feuer zur Zeit t (s) an? (weicher Rand von 50 ms, damit nichts flackert) */
    public double level(double t) {
        double p = t % period;
        if (p < 0) p += period;
        double best = 0;
        for (int i = 0; i < on.length; i += 2) {
            double a = on[i], b = on[i] + on[i + 1];
            double v = Math.min(1, Math.min(Math.max(0, (p - a) / 0.05 + 1), Math.max(0, (b - p) / 0.05)));
            best = Math.max(best, Math.min(1, Math.max(0, v)));
        }
        return best;
    }

    /** Anteil der Zeit, in der es leuchtet. */
    public double dutyCycle() {
        double s = 0;
        for (int i = 1; i < on.length; i += 2) s += on[i];
        return s / period;
    }

    /** Farbe als linearer Anteil (Signalfarben nach IALA, gerundet). */
    public double[] rgb() {
        return switch (colour) {
            case 'R' -> new double[]{1, 0.08, 0.05};
            case 'G' -> new double[]{0.05, 1, 0.45};
            case 'Y' -> new double[]{1, 0.75, 0.1};
            default -> new double[]{1, 0.92, 0.8};
        };
    }
}
