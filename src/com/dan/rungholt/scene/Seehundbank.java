package com.dan.rungholt.scene;

import com.dan.rungholt.wiring.Haulout;
import com.dan.seal.Colony;

import java.util.List;

/**
 * Die Seehundbank (Phase 7, F13 A, Zone 4): ein Sandrücken westlich von Südfall zwischen Südfall und der Norderhever,
 * der in den BAW-Küstenhöhen auf NHN 0 bis +0,5 m liegt, mit einem Priel bis unter NHN −4 m an seiner Ostseite. Er
 * fällt bei Ebbe als einer der ersten trocken; die Seehunde liegen an seinem Rand zum Priel [G: die Lage der echten
 * Liegeplätze bei Südfall steht nicht in den Daten]. Gibt die Bank als {@link Haulout} an die Verdrahtung und als
 * {@link Colony.Bank} an {@code com.dan.seal}.
 */
public final class Seehundbank implements Haulout {
    /** Mitte (m, Szene) und Halbmesser (m) [G, aus den Höhen gewählt]. */
    public static final double X = -2980, Z = 200, R = 280;
    private final Terrain terrain;

    public Seehundbank(Terrain terrain) { this.terrain = terrain; }

    @Override public int count() { return 1; }

    @Override public double[] bank(int i) { return new double[]{X, Z, R, terrain.height(X, Z)}; }

    /** Die Bänke für die Kolonie. */
    public List<Colony.Bank> banks() { return List.of(new Colony.Bank(X, Z, R)); }
}
