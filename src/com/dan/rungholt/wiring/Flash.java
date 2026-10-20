package com.dan.rungholt.wiring;

/** Ein Blitz. Gibt: {@code lightning} (Phase 3). Nimmt: cloud, ocean, mudflat (Spiegel im nassen Sand), weathering, sky, soundscape. */
@FunctionalInterface
public interface Flash {
    /** Ort des Kanals (Mitte), Stärke 0..1, Szenenzeit in s, Dauer in s. */
    void flash(double x, double y, double z, double strength, double time, double duration);
}
