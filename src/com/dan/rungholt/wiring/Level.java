package com.dan.rungholt.wiring;

/**
 * Der Pegel (neu in Rungholt): Wasserstand des offenen Meers in m über NHN. Gibt: {@code tide} (Phase 4: Partialtiden
 * und Windstau), bis dahin ein Platzhalter aus M2 und M4. Nimmt: ocean (Norderhever), buoyancy, Kamera, HUD.
 */
public interface Level {
    /** Pegel jetzt (m über NHN). */
    double level();

    /** Windstau im Pegel (m), 0 ohne Wind. */
    default double surge() { return 0; }

    /** Steigt das Wasser (Flut) oder fällt es (Ebbe)? */
    default boolean rising() { return false; }
}
