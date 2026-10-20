package com.dan.rungholt.scene;

import java.time.LocalDate;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Phase 13: die Sage von den Glocken (F19 A, Abschnitt 6 des Werkbuchs). Den Klang gibt seit Phase 14 {@code gl.Klang}
 * (dumpf, vom Wind getragen); das Regiebuch „Die Glocken“ steht seit Phase 15 als Text in {@code regie/glocken.txt}
 * ({@link Regie}). Hier bleiben die Bühne (was die Hörproben stellen) und die Blicke.
 * <p>
 * <b>Die Glocken.</b> Nach der Sage läuten in Sturmnächten die Glocken von Rungholt aus dem Watt. Sie läuten nur, wenn
 * heute ist, die Sonne tiefer als −6° steht, der Wind mit mindestens Bft 8 aus Südwest bis Nordwest weht und das Wasser
 * mehr als einen halben Meter über der Kirchwarft steht ({@link #sturmnacht}). Dann kommen sie in unregelmäßigen
 * Abständen, nie lange (40 bis 120 s, Pausen 10 bis 40 min) [G]; für die Dauer des Läutens steht die Kirche als Schemen
 * über dem Wasser ({@link #ghost}, 0..1, blendet in 8 s). {@link #force} läutet sofort (Knopf, Regie).
 * <p>
 * <b>Das Regiebuch.</b> 30. November 2026 [G: ein Spätherbsttag nahe der Springtide mit dem Hochwasser am Abend um
 * 17:31 und dem Niedrigwasser am nächsten Mittag um 12:20]: der Abend im Weststurm vom Deich bei Fuhlehörn, die
 * Glocken zum Hochwasser bei der Kirche, die Nacht im Zeitraffer, am Mittag bei Ebbe die Spuren und die Grabung, die
 * Magnetik von oben, die Überblendung nach 1362 und zurück.
 */
public final class Glocken {
    /** Was Regie und Hörproben an der Szene stellen (die App setzt es um). */
    public interface Buehne {
        void zeit(LocalDate day, double hour);
        double stunde();                  // Szenenzeit (Stunden seit 1970)
        void zeitraffer(double lapse);
        void sturm(boolean an);           // Weststurm Bft 9 an, oder abklingen lassen
        void kamera(Blick b);
        void magnetik(boolean an);
        void aera(double ziel);           // 0 heute, 1 = 1362
        void grabungSetzen();
        double hochwasser(double nachStunde);
        double niedrigwasser(double nachStunde);
    }

    public enum Blick { FUHLEHOERN, KIRCHE_IM_STURM, GRABUNG, DRAUFSICHT, KIRCHWARFT }

    public static final LocalDate DAY = LocalDate.of(2026, 11, 30);
    public static final double START_HOUR = 16.25;

    // ------------------------------------------------------------------ die Sage

    /** Bft 8 untere Grenze (m/s). */
    public static final double BFT8 = 17.2;

    /** Ist Sturmnacht, in der die Glocken läuten können? */
    public static boolean sturmnacht(boolean heute, double sunDeg, double windMs, double windFromDeg, double waterOverChurch) {
        double f = ((windFromDeg % 360) + 360) % 360;
        return heute && sunDeg < -6 && windMs >= BFT8 && f >= 225 && f <= 315 && waterOverChurch > 0.5;
    }

    private final SplittableRandom rnd = new SplittableRandom(1362);
    public boolean ringing;
    /** Rest des Läutens oder der Pause (s Szenenzeit). */
    public double left = 120;
    /** Der Schemen der Kirche 0..1. */
    public double ghost;
    public int peals;
    public boolean condition;

    /** dt Sekunden Szenenzeit weiter (in großen Sprüngen wird nur die Bedingung nachgeführt). */
    public void step(double dt, boolean cond) {
        condition = cond;
        if (!cond && !forced) { ringing = false; ghost = Math.max(0, ghost - dt / 8); left = Math.min(left, 180); return; }
        double rest = Math.min(dt, 3600);
        while (rest > 1e-9) {
            double h = Math.min(rest, Math.max(1e-3, left));
            rest -= h;
            left -= h;
            if (left <= 0) {
                if (forced && ringing && !cond) { ringing = false; forced = false; left = 180; break; }
                forced = false;
                ringing = !ringing;
                if (ringing) peals++;
                left = ringing ? 40 + 80 * rnd.nextDouble() : 600 + 1800 * rnd.nextDouble();
            }
        }
        double target = ringing ? 1 : 0;
        double k = Math.min(1, dt / 8);
        ghost += (target - ghost) * k;
        if (dt > 60) ghost = target;
    }

    /** Läuten sofort (Knopf „Glocken jetzt“, Regie). */
    public void force() { ringing = true; forced = true; left = 40 + 40 * rnd.nextDouble(); peals++; }
    /** Von Hand oder von der Regie ausgelöst: läutet zu Ende, auch ohne Sturmnacht. */
    public boolean forced;

    public String describe() {
        String s = "Glocken";
        if (ringing) s += String.format(Locale.GERMAN, " · sie läuten, Schemen %.0f %%", ghost * 100);
        else if (condition) s += String.format(Locale.GERMAN, " · Sturmnacht: still, nächstes Läuten in %.0f min", left / 60);
        else s += " · keine Sturmnacht";
        return s + (peals > 0 ? " · " + peals + "× geläutet" : "");
    }
}
