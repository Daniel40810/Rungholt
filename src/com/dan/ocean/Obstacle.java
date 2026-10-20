package com.dan.ocean;

/**
 * Ein fester Körper im Wasser, an dem die Wellen brechen: ein senkrechter Zylinder (Turmsockel, Pfahl, Mole aus
 * mehreren Zylindern). Die eigene Form des Packages; die App übersetzt ihre Collider hierher. Um jeden Körper legt
 * sich ein Brandungsring aus Schaum, der mit dem Seegang breiter und dichter wird.
 */
public record Obstacle(double x, double z, double radius) {
    public Obstacle {
        if (!(radius > 0)) throw new IllegalArgumentException("Radius muss positiv sein: " + radius);
    }

    /** Abstand eines Punkts von der Oberfläche (negativ innen). */
    public double distance(double px, double pz) { return Math.hypot(px - x, pz - z) - radius; }
}
