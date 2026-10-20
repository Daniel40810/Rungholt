package com.dan.rungholt.wiring;

/**
 * Ein fester Körper: der Deich, die Kirche, eine Warft, ein Haus, der Rumpf der Kogge. Gibt: timber, die App
 * (Deich, Kirche, Warften; Phase 10). Nimmt: ocean, rain, spray, weathering. Beschrieben als vorzeichenbehafteter Abstand:
 * negativ innen, positiv außen.
 */
@FunctionalInterface
public interface Collider {
    double distance(double x, double y, double z);

    /** Senkrechter Zylinder um (cx, cz) mit Radius r von y0 bis y1 (Pfahl im Sielhafen). */
    static Collider cylinder(double cx, double cz, double r, double y0, double y1) {
        return (x, y, z) -> {
            double dr = Math.hypot(x - cx, z - cz) - r;
            double dy = Math.max(y0 - y, y - y1);
            if (dr <= 0 && dy <= 0) return Math.max(dr, dy);
            return Math.hypot(Math.max(dr, 0), Math.max(dy, 0));
        };
    }

    /** Quader mit Mitte (cx, cy, cz) und halben Kanten (hx, hy, hz), achsparallel (die Kirche, ein Haus). */
    static Collider box(double cx, double cy, double cz, double hx, double hy, double hz) {
        return (x, y, z) -> {
            double qx = Math.abs(x - cx) - hx, qy = Math.abs(y - cy) - hy, qz = Math.abs(z - cz) - hz;
            double out = Math.sqrt(Math.max(qx, 0) * Math.max(qx, 0) + Math.max(qy, 0) * Math.max(qy, 0) + Math.max(qz, 0) * Math.max(qz, 0));
            return out + Math.min(Math.max(qx, Math.max(qy, qz)), 0);
        };
    }
}
