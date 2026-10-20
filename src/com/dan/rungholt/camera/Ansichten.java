package com.dan.rungholt.camera;

import com.dan.rungholt.scene.Buehne;

import java.util.List;
import java.util.Locale;

/**
 * Die Ansichten auf den Tasten 1 bis 9 (F20 A). Seit Phase 2 an echten Orten aus den Höhendaten: der Deich von
 * Nordstrand bei Fuhlehörn (westlichste Deichkrone über NHN +7 m), das Watt östlich von Südfall, die Warft von
 * Südfall, die Kante des Priels im Süden, eine hohe Sandbank an einem Priel westlich von Südfall zwischen Südfall und der
 * Norderhever als Seehundbank [G]. Seit Phase 6 „Am Priel“ an der steilsten Prielkante im Osten (4 m Abbruch auf 40 m
 * in den BAW-Höhen, Blick nach Nordwest über den Priel) und „Unter Wasser“ 1,2 m über dem Grund dieses Priels (taucht,
 * solange Wasser steht; bei Niedrigwasser noch 1,4 m). Seit Phase 7 „Seehundbank“ auf dem Rücken der
 * {@code Seehundbank} 45 m nordwestlich der Stelle, an der die Tiere bei Niedrigwasser am Priel liegen, Blick nach
 * Südost über die Gruppe zum Wasser. Seit Phase 11 die Ansichten von 1362 (sie schalten die Zeitreise auf 1362): „Rungholt
 * 1362“ aus den Schallöffnungen des Kirchturms (19 m über der Kirchwarft, Blick nach Westsüdwest über die Warftenkette
 * zum Sielhafen) und „Sielhafen“ von der Deichkrone östlich des Siels über das Hafenbecken und das Sieltief zur Hever.
 * Die Augenhöhe wird über dem Gelände genommen (bei 8 und 9 über dem von 1362).
 */
public final class Ansichten {
    /** Eine Ansicht: Taste, Name, Lage (x, z), Blickrichtung (Grad von Nord), Neigung (Grad nach unten), Augenhöhe über Grund, ab Phase. */
    public record Ansicht(int key, String name, double x, double z, double heading, double pitch, double eyeAboveGround, int phase) {
        @Override public String toString() { return key + " " + name; }
    }

    public static final List<Ansicht> ALLE = List.of(
            new Ansicht(1, "Vom Deich", 4764, -1404, 256, 2, 1.8, 1),
            new Ansicht(2, "Auf dem Watt", 300, 500, 280, 4, 1.7, 1),
            new Ansicht(3, "Südfall", -997, -8, 95, 3, 1.8, 1),   // seit Phase 15 neben dem Friesenhaus (vorher stand das Auge darin)
            new Ansicht(4, "Am Priel", 3487, 1559, 315, 4, 1.7, 1),
            new Ansicht(5, "Seehundbank", -2857, 333, 135, 4, 2.0, 1),
            new Ansicht(6, "Vogelauge", 900, 900, 255, 32, 200, 1),
            new Ansicht(7, "Unter Wasser", 3463, 1535, 135, -4, 1.2, 6),
            new Ansicht(8, "Rungholt 1362", -17, -1, 268, 9, 19, 11),
            new Ansicht(9, "Sielhafen", -3545, 1292, 196, 9, 3.5, 11));

    /** Ob die Ansicht schon einen Ort hat („Unter Wasser“ ab Phase 6, 1362 ab Phase 11). */
    public static boolean ready(Ansicht a, int phase) { return a.phase() <= phase; }

    public static Ansicht byName(String name) {
        for (Ansicht a : ALLE) if (a.name().equalsIgnoreCase(name) || a.name().replace(" ", "").equalsIgnoreCase(name)) return a;
        // ohne Umlaute (Umgebungsvariablen unter Windows): „Suedfall“, „Voegel“ …
        String n = name.replace(" ", "").toLowerCase(java.util.Locale.ROOT);
        for (Ansicht a : ALLE) {
            String k = a.name().replace(" ", "").toLowerCase(java.util.Locale.ROOT).replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss");
            if (k.equals(n)) return a;
        }
        try { return byKey(Integer.parseInt(name.trim())); } catch (NumberFormatException e) { return null; }
    }

    public static Ansicht byKey(int key) {
        for (Ansicht a : ALLE) if (a.key() == key) return a;
        return null;
    }

    /** Stellt die Kamera auf die Ansicht über dem Boden {@code ground}; false, wenn sie in dieser Phase noch keinen Ort hat. */
    public static boolean apply(Ansicht a, Camera c, int phase, java.util.function.DoubleBinaryOperator ground) {
        if (!ready(a, phase)) return false;
        c.stopFahrt();
        c.diving = a.key() == 7;   // „Unter Wasser“ darf unter den Spiegel
        c.place(a.x(), ground.applyAsDouble(a.x(), a.z()) + a.eyeAboveGround(), a.z(), a.heading(), a.pitch());
        return true;
    }

    /** Wie oben, über der Bühne von Phase 1. */
    public static boolean apply(Ansicht a, Camera c, int phase) { return apply(a, c, phase, Buehne::height); }

    /** „3 Südfall (Auge 1,8 m über Grund, Blick 095°)“ */
    public static String describe(Ansicht a, int phase) {
        if (!ready(a, phase)) return a + " (folgt in Phase " + a.phase() + ")";
        return String.format(Locale.GERMAN, "%s (Auge %.1f m über Grund, Blick %03.0f°)", a, a.eyeAboveGround(), a.heading());
    }

    private Ansichten() { }
}
