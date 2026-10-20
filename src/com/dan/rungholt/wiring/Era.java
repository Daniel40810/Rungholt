package com.dan.rungholt.wiring;

/**
 * Die Ära (neu in Rungholt, F17 A): Heute oder 1362, mit Überblendung in 4 s. Gibt: die App (Schalter im Block
 * ZEITREISE, Taste Z). Nimmt: Gelände, mudflat, ground, timber, muster, flock, soundscape, Kamera. Die Tide läuft in
 * beiden Welten.
 */
public interface Era {
    /** Überblendung 0 = heute … 1 = 1362. */
    double blend();

    /** Ob die Szene (überwiegend) in 1362 steht. */
    default boolean is1362() { return blend() >= 0.5; }

    /** Jahr der Welt, die man sieht: 1362 oder das Jahr der Uhr. */
    default String label() { return blend() <= 0 ? "Heute" : blend() >= 1 ? "1362" : String.format(java.util.Locale.GERMAN, "Zeitreise %.0f %%", blend() * 100); }
}
