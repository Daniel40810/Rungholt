package com.dan.lightning;

import java.util.Arrays;

/**
 * Eine Entladung: der Kanal als Liste gerader Stücke und ihr Ablauf in der Zeit.
 * <p>
 * <b>Kanal.</b> Jedes Stück hat Anfang und Ende in m (Szenenraum: x Ost, y oben, z Süd), seine Ordnung (0 = Hauptkanal,
 * 1 = Ast, 2 = Ast eines Asts …), den Weg des Leitblitzes bis zu seinem Anfang in m und eine Sichtbarkeit 0..1 (1 =
 * frei, kleiner = in der Wolke verborgen). Der Hauptkanal ist eine zusammenhängende Kette von der Wolke bis zum
 * Einschlag; die Äste zweigen von ihm oder voneinander ab.
 * <p>
 * <b>Ablauf.</b> Der Leitblitz wächst mit {@link Lightning#leaderSpeed} (rund 200 km/s) Stufe für Stufe nach unten,
 * schwach leuchtend. Erreicht er den Boden, folgt der erste Hauptschlag, dann die Folgeschläge im Abstand einiger
 * Dutzend Millisekunden durch denselben Kanal, manche mit Nachstrom. Wie hell der Kanal zu einer Zeit erscheint, gibt
 * {@link #mainLevel(double)} und {@link #branchLevel(double, int)} (1 = ein Schlag von 30 kA, gesehen mit der
 * Trägheit des Auges).
 */
public final class Bolt {
    /** Art der Entladung. */
    public enum Kind {
        /** Wolke–Boden (über See: Wolke–Wasser), negativ, nach unten verästelt. */
        CLOUD_GROUND,
        /** Wolke–Wolke im Innern, meist verborgen: die Wolke leuchtet. */
        INTRA_CLOUD,
        /** Spinnenblitz: waagerecht knapp unter der Wolkenuntergrenze, weit verästelt, gut zu sehen. */
        SPIDER,
        /** Wetterleuchten: ferne Entladung ohne sichtbaren Kanal, nur das Leuchten der Wolken. */
        SHEET
    }

    public final Kind kind;
    /** Startzeit in Szenensekunden. */
    public final double t0;
    /** Stücke: Anfang (ax, ay, az), Ende (bx, by, bz). */
    public final float[] ax, ay, az, bx, by, bz;
    /** Ordnung je Stück (0 = Hauptkanal). */
    public final byte[] order;
    /** Weg des Leitblitzes bis zum Anfang jedes Stücks in m. */
    public final float[] path;
    /** Sichtbarkeit je Stück 0..1 (Wolke davor oder darum). */
    public final float[] visible;
    /** Zahl der Stücke. */
    public final int n;
    /** Einschlag (CLOUD_GROUND) oder Mitte der Entladung. */
    public final double hitX, hitY, hitZ;
    /** Index des getroffenen Fangpunkts, sonst −1 (Wasser oder keine Bodenberührung). */
    public final int attractor;
    /** Länge des Hauptkanals und aller Stücke in m. */
    public final double mainLength, totalLength;
    /** Weg des Leitblitzes bis zum Einschlag in m. */
    public final double leaderPath;
    /** Zeit vom Start bis zum ersten Hauptschlag in s. */
    public final double leaderTime;
    /** Hauptschläge: Zeit nach dem Start in s, Strom in kA, Nachstrom in s (0 = keiner). */
    public final double[] strokeTime, strokeKA, continuing;
    /** Ende allen Leuchtens nach dem Start in s. */
    public final double duration;

    Bolt(Kind kind, double t0, float[][] seg, byte[] order, float[] path, int n, double[] hit, int attractor,
         double leaderPath, double leaderSpeed, double[] strokeTime, double[] strokeKA, double[] continuing) {
        this.kind = kind;
        this.t0 = t0;
        this.ax = Arrays.copyOf(seg[0], n);
        this.ay = Arrays.copyOf(seg[1], n);
        this.az = Arrays.copyOf(seg[2], n);
        this.bx = Arrays.copyOf(seg[3], n);
        this.by = Arrays.copyOf(seg[4], n);
        this.bz = Arrays.copyOf(seg[5], n);
        this.order = Arrays.copyOf(order, n);
        this.path = Arrays.copyOf(path, n);
        this.visible = new float[n];
        Arrays.fill(visible, 1f);
        this.n = n;
        this.hitX = hit[0];
        this.hitY = hit[1];
        this.hitZ = hit[2];
        this.attractor = attractor;
        double ml = 0, tl = 0;
        for (int i = 0; i < n; i++) {
            double l = length(i);
            tl += l;
            if (order[i] == 0) ml += l;
        }
        this.mainLength = ml;
        this.totalLength = tl;
        this.leaderPath = leaderPath;
        this.leaderTime = leaderPath / leaderSpeed;
        this.strokeTime = strokeTime;
        this.strokeKA = strokeKA;
        this.continuing = continuing;
        double end = 0;
        for (int k = 0; k < strokeTime.length; k++) end = Math.max(end, strokeTime[k] + continuing[k] + 6 * AFTERGLOW);
        this.duration = end;
    }

    /** Länge des Stücks i in m. */
    public double length(int i) {
        double dx = bx[i] - ax[i], dy = by[i] - ay[i], dz = bz[i] - az[i];
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Ob die Entladung zur Szenenzeit t (absolut) noch leuchtet oder wächst. */
    public boolean alive(double t) { return t >= t0 && t - t0 <= duration; }

    // ------------------------------------------------------------------ Helligkeit

    /** Trägheit des Auges: ein Schlag von 0,1 ms erscheint als Puls, der mit dieser Zeit abklingt (s). */
    public static final double PERSIST = 0.035;
    /** Nachleuchten des heißen Kanals (s), schwächer. */
    public static final double AFTERGLOW = 0.12;

    /** Lichtmenge eines Schlags relativ zu 30 kA (die optische Leistung wächst etwas schneller als der Strom). */
    static double energy(double kA) { return Math.pow(Math.max(0, kA) / 30.0, 1.4); }

    /**
     * Helligkeit des Hauptkanals zur Zeit t nach dem Start (relativ, 1 = Schlag von 30 kA im ersten Augenblick):
     * jeder Schlag als Puls mit Nachleuchten, dazu Nachstrom als gleichmäßiges Glühen mit leichtem Flackern.
     */
    public double mainLevel(double t) {
        double s = 0;
        for (int k = 0; k < strokeTime.length; k++) {
            double a = t - strokeTime[k];
            if (a < 0) continue;
            double e = energy(strokeKA[k]);
            s += e * (0.85 * Math.exp(-a / PERSIST) + 0.15 * Math.exp(-a / AFTERGLOW));
            if (continuing[k] > 0 && a < continuing[k] + 0.05) {
                double cc = 0.09 * smooth(0, 0.01, a) * (1 - smooth(continuing[k] - 0.04, continuing[k] + 0.05, a));
                s += cc * (0.75 + 0.25 * Math.sin(a * 97 + k * 1.7) * Math.sin(a * 41 + k));
            }
        }
        return s;
    }

    /**
     * Helligkeit eines Asts der Ordnung ord zur Zeit t: Äste leuchten nur beim ersten Schlag (die Folgeschläge nehmen
     * den Hauptkanal) und schwächer, je weiter sie verzweigt sind. Bei Wolkenblitzen leuchten alle Äste mit jedem Puls.
     */
    public double branchLevel(double t, int ord) {
        if (ord <= 0) return mainLevel(t);
        double f = 0.25 * Math.pow(0.55, ord - 1);
        if (kind != Kind.CLOUD_GROUND) return mainLevel(t) * Math.min(1, f * 2.2);
        if (strokeTime.length == 0) return 0;
        double a = t - strokeTime[0];
        if (a < 0) return 0;
        return f * energy(strokeKA[0]) * Math.exp(-a / (PERSIST * 0.7));
    }

    /**
     * Leuchten des wachsenden Leitblitzes: wie weit er ist (m entlang seines Wegs) und wie hell (relativ), 0 vor dem
     * Start und nach dem ersten Schlag. Der Leitblitz ist rund tausendmal schwächer als ein Schlag.
     */
    public double leaderFront(double t) {
        if (kind != Kind.CLOUD_GROUND || t < 0) return 0;
        return Math.min(leaderPath, leaderPath * t / Math.max(1e-6, leaderTime));
    }

    /** Helligkeit des Leitblitzes (relativ) zur Zeit t nach dem Start. */
    public double leaderLevel(double t) {
        if (kind != Kind.CLOUD_GROUND || t < 0 || t > leaderTime + 0.002) return 0;
        return 0.004;
    }

    /** Helligkeit des Stücks i zur Zeit t nach dem Start (relativ, ohne Sichtbarkeit). */
    public double segmentLevel(int i, double t) {
        double lv = order[i] == 0 ? mainLevel(t) : branchLevel(t, order[i]);
        if (kind == Kind.CLOUD_GROUND && t < leaderTime + 0.002) {
            double front = leaderFront(t);
            if (path[i] <= front) lv += leaderLevel(t) * (0.5 + 0.5 * Math.exp(-(front - path[i]) / 120));
        }
        return lv;
    }

    /** Ob der Kanal sichtbar zu Boden reicht (für Anzeigen). */
    public boolean touchesGround() { return kind == Kind.CLOUD_GROUND; }

    static double smooth(double e0, double e1, double x) {
        double t = Math.max(0, Math.min(1, (x - e0) / (e1 - e0)));
        return t * t * (3 - 2 * t);
    }
}
