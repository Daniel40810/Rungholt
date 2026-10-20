package com.dan.rungholt.wiring;

/**
 * Ein Einschlag auf einer Fläche: Regentropfen, Spritzer, Hagel. Gibt: rain, spray. Nimmt: {@code ocean.addImpulse},
 * {@code weathering.wet}. Die App verteilt nach dem, was getroffen wurde.
 */
@FunctionalInterface
public interface SurfaceHit {
    enum Kind { TROPFEN, SPRITZER, HAGEL }

    /** Ort, Masse in kg, Geschwindigkeit in m/s, Art. */
    void hit(double x, double y, double z, double massKg, double vx, double vy, double vz, Kind kind);
}
