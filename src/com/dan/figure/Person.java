package com.dan.figure;

/**
 * Eine Figur: Ort, Richtung, Haltung und Kleidung. Die Haltung sind vier Zahlen (Schrittphase, Gang, Bücken, Zeigen),
 * aus denen {@link FigureMesh#pose} und der Scheitelshader die Gelenke rechnen; dazu Größe, Blick zur Seite und die
 * Rolle in einer Gruppe ({@link Hike}).
 */
public final class Person {
    public enum Role { GUIDE, ADULT, CHILD }

    public final int id;
    public final Role role;
    public Outfit outfit;
    /** Fußpunkt (m), Richtung (Azimut von Nord im Uhrzeigersinn, rad). */
    public double x, y, z, heading;
    /** Schrittphase 0..1 je Doppelschritt, Gang 0 Stehen … 1 Gehen, Bücken 0..1, Zeigen 0..1, Blick zur Seite (rad). */
    public double phase, gait, bend, point, look;
    /** Größe (1 = 1,78 m). */
    public double scale = 1;
    /** Geschwindigkeit über Grund (m/s), Richtung der Bewegung. */
    public double speed, vx, vz;
    /** Wasser an den Füßen (m), vom Benutzer der Welt gesetzt. */
    public double wet;
    // Gruppe: Abstand hinter dem Führer (m), Platz zur Seite, Takt des Schlenderns
    double lag, side, sideGoal, wander, sprint, footAcc;
    int lastFoot = -1;

    public Person(int id, Role role, Outfit outfit) { this.id = id; this.role = role; this.outfit = outfit; }

    /** Die Gelenke dieser Figur ({@link FigureMesh#pose}). */
    public double[] pose() { return FigureMesh.pose(phase, gait, bend, point); }

    /** Um ds Meter weitergehen: Schrittphase nach der Schrittlänge. */
    public void advance(double ds) {
        double st = FigureMesh.stride(Math.max(gait, 0.3), scale);
        phase = (phase + ds / (2 * st)) % 1.0;
    }

    @Override public String toString() {
        return String.format(java.util.Locale.GERMAN, "%s %d bei %.0f/%.0f, %.1f m/s", role == Role.GUIDE ? "Führer" : role == Role.CHILD ? "Kind" : "Wanderer", id, x, z, speed);
    }
}
