package com.dan.smoke;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Rauch aus vielen Quellen: Herdfeuer unter Rauchlöchern, Schmieden, offene Feuer, ein brennendes Haus. Jede Quelle
 * gibt eine Fahne nach {@link Plume}: sie steigt mit ihrem Auftrieb, knickt im Wind ab, zerfasert und legt sich in
 * stabiler Abendluft flach über die Dächer. Gezeichnet wird jede Fahne als Kette weicher Bälle, deren Ort eine Formel
 * aus Fahne, Nummer und Alter ist; die nahen Fahnen ({@link Quality#near}) mit vielen kleinen Bällen, die fernen mit
 * wenigen großen. So kosten hundert Säulen wenig.
 * <p>
 * Eingänge je Bild: Wind (Richtung, Geschwindigkeit in 10 m), Stabilität der Luft 0..1 ({@link #stability}), Zeit.
 * Ausgaben: die Konstanten der Fahnen für die Grafik ({@link #select}), Dichte an einem Ort ({@link #density}) für
 * Licht und Sicht. Nur Java 21; keine Abhängigkeit zu anderen Packages (Paketvertrag).
 */
public final class Smoke {
    /** Stufen: wie viele Fahnen nah gezeichnet werden, Bälle je naher und ferner Fahne, Lebensdauer (s), Reichweite (m). */
    public enum Quality {
        LOW(12, 28, 8, 120, 2500), MEDIUM(24, 40, 10, 150, 4000), HIGH(40, 56, 14, 180, 6000), ULTRA(64, 72, 18, 220, 9000);
        public final int near, nearCount, farCount;
        public final double life, far;
        Quality(int near, int nearCount, int farCount, double life, double far) {
            this.near = near; this.nearCount = nearCount; this.farCount = farCount; this.life = life; this.far = far;
        }
    }

    public volatile double windFromDeg = 245, windSpeed = 3, stability;
    public volatile Quality quality = Quality.MEDIUM;
    private double time;
    private final List<Source> sources = new ArrayList<>();
    /** Ergebnis von {@link #select}: Konstanten der nahen und fernen Fahnen, je {@link Plume#FLOATS} Zahlen. */
    private float[] near = new float[0], far = new float[0];
    private int nearN, farN;

    public synchronized void setSources(List<Source> list) { sources.clear(); sources.addAll(list); }
    public synchronized List<Source> sources() { return Collections.unmodifiableList(new ArrayList<>(sources)); }
    public double time() { return time; }
    public void setTime(double t) { time = t; }
    public void step(double dt) { if (dt > 0) time += dt; }

    /** Richtung, in die der Wind weht (Einheitsvektor x, z). */
    public double[] windTo() {
        double a = Math.toRadians(windFromDeg + 180);
        return new double[]{Math.sin(a), -Math.cos(a)};
    }

    /**
     * Stabilität der Luft am Boden 0..1 aus Sonnenhöhe (Grad), Wind (m/s) und Bedeckung 0..1: am Abend und in der
     * Nacht bei schwachem Wind und klarem Himmel kräftig (Inversion), am Tag und bei Wind neutral [G nach den
     * Pasquill-Klassen E/F].
     */
    public static double stability(double sunAltDeg, double wind, double cloudCover) {
        double night = smooth(10, -3, sunAltDeg);
        double calm = 1 - smooth(2.5, 7, wind);
        double clear = 1 - 0.6 * Math.max(0, Math.min(1, cloudCover));
        return Math.max(0, Math.min(1, night * calm * clear));
    }

    /**
     * Wählt die Fahnen für die Ansicht: die {@link Quality#near} nächsten zum Auge mit vielen Bällen, die übrigen bis
     * {@link Quality#far} mit wenigen; ausgeschaltete (Menge 0) fallen weg.
     */
    public synchronized void select(double ex, double ey, double ez) {
        Quality q = quality;
        double[] w = windTo();
        List<Source> live = new ArrayList<>();
        for (Source s : sources) if (s.amount() > 0 && Math.hypot(s.x() - ex, s.z() - ez) < q.far) live.add(s);
        live.sort((a, b) -> Double.compare(Math.hypot(a.x() - ex, a.z() - ez), Math.hypot(b.x() - ex, b.z() - ez)));
        nearN = Math.min(q.near, live.size());
        farN = live.size() - nearN;
        near = new float[nearN * Plume.FLOATS];
        far = new float[farN * Plume.FLOATS];
        for (int i = 0; i < live.size(); i++) {
            Source s = live.get(i);
            boolean isNear = i < nearN;
            // große Feuer bekommen größere Bälle und leben länger
            double big = Math.min(3, 1 + Math.log10(Math.max(1, s.heatKw() / 10)));
            float[] c = Plume.constants(s, w[0], w[1], windSpeed, stability, isNear ? q.nearCount : q.farCount, q.life * (0.8 + 0.2 * big), (isNear ? 1.2 : 2.6) * big, time);
            System.arraycopy(c, 0, isNear ? near : far, (isNear ? i : i - nearN) * Plume.FLOATS, Plume.FLOATS);
        }
    }

    public synchronized float[] nearData() { return near.clone(); }
    public synchronized float[] farData() { return far.clone(); }
    public synchronized int nearCount() { return nearN; }
    public synchronized int farCount() { return farN; }
    /** Bälle insgesamt nach dem letzten {@link #select}. */
    public synchronized int balls() { return nearN * quality.nearCount + farN * quality.farCount; }

    /**
     * Rauchdichte am Ort 0..∞ (Summe der Fahnen als Gaußsche Wolken um ihre Mitte; 1 ≈ deutlich sichtbar auf 10 m):
     * für Licht, Sicht und Prüfung. Je Fahne wird die nächste Stelle der Mittellinie in 24 Schritten gesucht.
     */
    public synchronized double density(double px, double py, double pz) {
        double[] w = windTo();
        double sum = 0;
        for (Source s : sources) {
            if (s.amount() <= 0) continue;
            float[] c = Plume.constants(s, w[0], w[1], windSpeed, stability, 1, quality.life, 1, 0);
            if (Math.hypot(px - s.x(), pz - s.z()) > c[13] * quality.life * 1.3 + 60) continue;
            double best = 1e18, bestA = 0;
            for (int k = 0; k <= 24; k++) {
                double a = quality.life * Math.pow(k / 24.0, 1.6);
                double[] m = Plume.center(c, a);
                double d = sq(m[0] - px) + sq(m[1] - py) + sq(m[2] - pz);
                if (d < best) { best = d; bestA = a; }
            }
            double[] m = Plume.center(c, bestA), sg = Plume.sigma(c, bestA);
            double g = Math.exp(-0.5 * (sq(m[0] - px) / sq(sg[0]) + sq(m[1] - py) / sq(sg[1]) + sq(m[2] - pz) / sq(sg[0])));
            double fade = 1 - Plume.smooth(0.6 * quality.life, quality.life, bestA);
            sum += s.amount() * (1 + s.black()) * g * fade * 3 / (1 + c[19] * sg[0] * sg[1]);
        }
        return sum;
    }

    /** Wie hoch die Fahne einer Quelle am Ende über ihrem Austritt steht (m), für Anzeige und Prüfung. */
    public double finalRise(Source s) {
        double[] w = windTo();
        return Plume.constants(s, w[0], w[1], windSpeed, stability, 1, quality.life, 1, 0)[11];
    }

    /** „120 Fahnen (24 nah) · 1 800 Bälle · Wind 3,0 m/s aus 245° · Inversion 0,72“ */
    public synchronized String describe() {
        int on = 0;
        for (Source s : sources) if (s.amount() > 0) on++;
        return String.format(Locale.GERMAN, "%d Fahnen (%d nah, %d fern) · %,d Bälle · Wind %.1f m/s aus %03.0f° · Inversion %.2f",
                on, nearN, farN, balls(), windSpeed, windFromDeg, stability);
    }

    static double sq(double v) { return v * v; }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
