package com.dan.rungholt.wiring;

/**
 * Ein Ereignis für den Klang. Gibt: tide (Kenterung, Priel rauscht), seal, flock, fracture (Deichbruch), wave
 * (Brecher), lightning, die Regie (Glocken). Nimmt: {@code soundscape} (Phase 14, F23 A).
 */
@FunctionalInterface
public interface SoundEvent {
    enum Kind { PRIEL, KNISTERN, AUSTERNFISCHER, STRANDLAEUFER, BRANDGANS, MOEWE, SEEHUND, HERD, VIEH, HAFEN, GLOCKE,
        GLOCKEN_IM_WATT, BRECHER, DEICHBRUCH, DONNER }

    void sound(Kind kind, double x, double y, double z, double strength);
}
