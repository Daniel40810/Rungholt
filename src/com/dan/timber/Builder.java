package com.dan.timber;

import java.util.ArrayList;
import java.util.List;

/**
 * Baut aus einem {@link HouseType} die Netze in drei Stufen (örtliches Maß, siehe dort):
 * <ul>
 * <li><b>Stufe 0 (nah):</b> Wände mit Türöffnungen und Sturz, Türblatt mit Zarge, Wandständer (Flechtwerk alle 1 m)
 * oder Eckpfosten und Schwelle (Spaltbohlen), Schrägstützen, Rähm, Giebeldreiecke mit Rauchloch, das Reetdach als
 * dicke Schale (Oberseite, Unterseite, Traufkante, Giebelkanten), Firstsoden; beim Grubenhaus Firstpfosten und ein
 * dunkler Eingang, beim Schuppen Pfosten, Rückwand aus Flechtwerk und eine Werkbank.</li>
 * <li><b>Stufe 1 (mittel):</b> glatte Wände mit aufgemaltem Türblatt, Eckpfosten, Dach mit Dicke, Firstsoden.</li>
 * <li><b>Stufe 2 (fern):</b> ein Kasten mit Satteldach ohne Dicke, gut 20 Dreiecke.</li>
 * </ul>
 * Die Wände reichen 0,4 m in den Boden, damit ein Haus auf leicht geneigtem Gelände keine Lücke zeigt.
 */
public final class Builder {
    private Builder() { }

    /** Abstand der Wandständer bei Flechtwerk (m) [G nach Haus 1: Ständer in rund 1 m]. */
    public static final double POST_STEP = 1.0;
    /** Abstand der Schrägstützen (m) [G]. */
    public static final double SUPPORT_STEP = 2.5;

    public static Mesh build(HouseType t, int lod) {
        Mesh m = new Mesh();
        new Parts(t, m, lod, false).all();
        return m;
    }

    /**
     * Seit Haithabu Phase 11: Stufe 0 eines begehbaren Hauses. Die Türen stehen offen (das Blatt nach innen an die Wand
     * geschwenkt), die Rauchlöcher sind echte Öffnungen in beiden Giebeln (außen und innen, mit Laibung), so dass
     * Licht und Rauch hindurch können. Das Innere baut {@link Interior}.
     */
    public static Mesh buildOpen(HouseType t) {
        Mesh m = new Mesh();
        new Parts(t, m, 0, true).all();
        return m;
    }

    /**
     * Ein Rauchloch in örtlichen Maßen ohne Streckung: Unterkante (m über dem Boden), Spitze, halbe Breite unten. Das
     * Loch ist ein Dreieck im Giebel, die Spitze oben in der Mitte.
     */
    public static double[] smokeHoleShape(HouseType t) {
        double tp = t.tanPitch(), yr = t.ridgeY();
        double top = yr - 0.22, bot = Math.max(t.wallHeight + 0.1, yr - 0.95), hw = (top - bot) / tp * 0.9;
        return new double[]{bot, top, hw};
    }

    /** Rauchlöcher in örtlichen Maßen: Mitte der Öffnung in jedem Giebel (leer ohne Rauchloch). */
    public static List<double[]> smokeHoles(HouseType t) {
        List<double[]> l = new ArrayList<>();
        if (!t.smokeHole) return l;
        double y = t.ridgeY() - 0.5;
        if (t == HouseType.GRUBENHAUS) l.add(new double[]{-t.length / 2 - 0.02, y, 0});
        else {
            l.add(new double[]{-t.length / 2 - 0.02, y, 0});
            l.add(new double[]{t.length / 2 + 0.02, y, 0});
        }
        return l;
    }

    // ==================================================================== Teile
    private static final class Parts {
        final HouseType t;
        final Mesh m;
        final int lod;
        /** Begehbar: Türen offen, Rauchlöcher offen (Phase 11). */
        final boolean opened;
        final double L, W, H, hx, hz, tp, cp, sp, wt, floorY, yb, eo, ey, yr, thk, g;

        Parts(HouseType t, Mesh m, int lod, boolean opened) {
            this.t = t;
            this.m = m;
            this.lod = lod;
            this.opened = opened;
            L = t.length;
            W = t.width;
            H = t.wallHeight;
            hx = L / 2;
            hz = W / 2;
            double p = Math.toRadians(t.pitchDeg);
            tp = Math.tan(p);
            cp = Math.cos(p);
            sp = Math.sin(p);
            wt = t.wall == Material.DAUB ? 0.18 : t.wall == Material.PLANK ? 0.10 : t.wall == Material.BRICK ? 0.36 : 0.08;
            floorY = -t.sunk;
            yb = t.sunk > 0 ? floorY - 0.1 : -0.4;
            eo = t.eaveOut();
            ey = t.eaveY();
            yr = t.ridgeY();
            thk = HouseType.THATCH;
            g = HouseType.GABLE;
        }

        void all() {
            if (lod >= 2) { far(); return; }
            if (t.open) shed();
            else {
                walls();
                gables();
                if (t.wall == Material.BRICK) { }   // gemauert: keine Pfosten, kein Rähm
                else if (lod == 0) posts();
                else cornerPosts(4);
            }
            roof();
            if (t == HouseType.GRUBENHAUS) pitHouse();
        }

        // ---------------------------------------------------------------- Wände

        /** Verdeckung nach der Höhe: unten am Boden und oben unter der Traufe dunkler. */
        double aoY(double y) {
            double base = 0.72 + 0.28 * clamp((y - yb - 0.3) / 0.8);
            double eave = 1 - 0.38 * clamp((y - (H - 0.9)) / 0.9);
            return base * eave;
        }

        /** Quader in Achsenrichtung mit Verdeckung nach der Höhe; Faser senkrecht (grainY) oder waagerecht. */
        void boxAO(double x0, double x1, double y0, double y1, double z0, double z1, Material mat, boolean grainY) {
            double[][] p = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            side(p[1], p[0], p[3], p[2], mat, grainY);
            side(p[4], p[5], p[6], p[7], mat, grainY);
            side(p[0], p[4], p[7], p[3], mat, grainY);
            side(p[5], p[1], p[2], p[6], mat, grainY);
            m.quad(p[3], p[7], p[6], p[2], mat, 0, 0, aoY(y1));
        }

        private void side(double[] p0, double[] p1, double[] p2, double[] p3, Material mat, boolean grainY) {
            double a0 = aoY(p0[1]), a3 = aoY(p3[1]);
            if (grainY) m.quadUV(p0, p1, p2, p3, mat, a0, a0, a3, a3);
            else m.quad(p0, p1, p2, p3, mat, 0, p0[1], a0, a0, a3, a3);
        }

        double doorW() { return 1.0; }
        double doorH() { return t == HouseType.GRUBENHAUS ? 1.5 : Math.min(1.85, H - 0.12); }

        void walls() {
            boolean gy = t.wall == Material.PLANK;
            for (int s = -1; s <= 1; s += 2) {
                HouseType.Side side = s > 0 ? HouseType.Side.SOUTH : HouseType.Side.NORTH;
                double z0 = s > 0 ? hz - wt : -hz, z1 = s > 0 ? hz : -hz + wt;
                wallRun(-hx, hx, z0, z1, side, gy, false);
            }
            for (int e = -1; e <= 1; e += 2) {
                HouseType.Side side = e > 0 ? HouseType.Side.EAST : HouseType.Side.WEST;
                double x0 = e > 0 ? hx - wt : -hx, x1 = e > 0 ? hx : -hx + wt;
                wallRun(-hz + wt, hz - wt, x0, x1, side, gy, true);
            }
        }

        /**
         * Eine Wand von a0 bis a1 (entlang x, oder entlang z bei Giebeln), Dicke zwischen c0 und c1, mit den Türen
         * dieser Seite als Öffnungen (Stufe 0) oder aufgemaltem Türblatt (Stufe 1).
         */
        void wallRun(double a0, double a1, double c0, double c1, HouseType.Side side, boolean gy, boolean gable) {
            double top = H;
            List<double[]> gaps = new ArrayList<>();
            for (HouseType.Door d : t.doors) {
                if (d.side() != side || t == HouseType.GRUBENHAUS) continue;
                double span = gable ? W : L, start = gable ? -hz : -hx;
                double c = start + d.at() * span;
                gaps.add(new double[]{c - doorW() / 2, c + doorW() / 2});
            }
            gaps.sort((p, q) -> Double.compare(p[0], q[0]));
            double outer = (gable ? (side == HouseType.Side.EAST ? 1 : -1) : (side == HouseType.Side.SOUTH ? 1 : -1));
            if (lod >= 1 || gaps.isEmpty()) {
                seg(a0, a1, yb, top, c0, c1, gable, gy);
                for (double[] gp : gaps) doorLeafFlat((gp[0] + gp[1]) / 2, gable, outer, c0, c1);
                return;
            }
            double cur = a0;
            for (double[] gp : gaps) {
                if (gp[0] > cur) seg(cur, gp[0], yb, top, c0, c1, gable, gy);
                if (top - doorH() > 0.03) seg(gp[0], gp[1], doorH(), top, c0, c1, gable, gy);
                door((gp[0] + gp[1]) / 2, gable, outer, c0, c1);
                cur = gp[1];
            }
            if (a1 > cur) seg(cur, a1, yb, top, c0, c1, gable, gy);
        }

        void seg(double a0, double a1, double y0, double y1, double c0, double c1, boolean gable, boolean gy) {
            if (gable) boxAO(c0, c1, y0, y1, a0, a1, t.wall, gy);
            else boxAO(a0, a1, y0, y1, c0, c1, t.wall, gy);
        }

        /** Tür in der Öffnung: Schwelle, Türblatt zurückgesetzt, Zarge aus zwei Pfosten und Sturz. */
        void door(double c, boolean gable, double outer, double c0, double c1) {
            double dh = doorH(), hw = doorW() / 2, mid = (c0 + c1) / 2, outFace = outer > 0 ? Math.max(c0, c1) : Math.min(c0, c1);
            double inFace = outer > 0 ? Math.min(c0, c1) : Math.max(c0, c1);
            if (opened) {
                // offen: das Blatt hängt am linken Pfosten und steht nach innen, fast an der Wand (95°)
                double hinge = c - hw + 0.04, leafL = doorW() - 0.08, ang = Math.toRadians(95);
                double along = Math.cos(ang) * leafL, into = Math.sin(ang) * leafL;
                double[] a0 = gable ? new double[]{inFace, floorY, hinge} : new double[]{hinge, floorY, inFace};
                double[] b0 = gable ? new double[]{inFace - outer * into, floorY, hinge + along} : new double[]{hinge + along, floorY, inFace - outer * into};
                doorLeafOpen(a0, b0, dh);
            } else {
            // Türblatt zurückgesetzt in der Wand
            double leafC = mid - outer * wt * 0.15;
            if (gable) boxAO(leafC - 0.025, leafC + 0.025, floorY, dh, c - hw + 0.04, c + hw - 0.04, Material.DOOR, true);
            else boxAO(c - hw + 0.04, c + hw - 0.04, floorY, dh, leafC - 0.025, leafC + 0.025, Material.DOOR, true);
            }
            // Zarge
            double f = outFace + outer * 0.04;
            double[] a = gable ? new double[]{f, floorY - 0.2, c - hw} : new double[]{c - hw, floorY - 0.2, f};
            double[] b = gable ? new double[]{f, dh + 0.12, c - hw} : new double[]{c - hw, dh + 0.12, f};
            m.beam(a, b, 0.07, 4, Material.POST, true, aoY(floorY), aoY(dh));
            a = gable ? new double[]{f, floorY - 0.2, c + hw} : new double[]{c + hw, floorY - 0.2, f};
            b = gable ? new double[]{f, dh + 0.12, c + hw} : new double[]{c + hw, dh + 0.12, f};
            m.beam(a, b, 0.07, 4, Material.POST, true, aoY(floorY), aoY(dh));
            a = gable ? new double[]{f, dh + 0.08, c - hw - 0.15} : new double[]{c - hw - 0.15, dh + 0.08, f};
            b = gable ? new double[]{f, dh + 0.08, c + hw + 0.15} : new double[]{c + hw + 0.15, dh + 0.08, f};
            m.beam(a, b, 0.07, 4, Material.POST, true, aoY(dh), aoY(dh));
            // Schwelle
            a = gable ? new double[]{f, floorY + 0.05, c - hw} : new double[]{c - hw, floorY + 0.05, f};
            b = gable ? new double[]{f, floorY + 0.05, c + hw} : new double[]{c + hw, floorY + 0.05, f};
            m.beam(a, b, 0.07, 4, Material.POST, true, aoY(floorY), aoY(floorY));
        }

        /** Offenes Türblatt von a nach b (unten, waagerecht), 5 cm dick, bis zur Höhe dh; Faser senkrecht. */
        void doorLeafOpen(double[] a, double[] b, double dh) {
            double dx = b[0] - a[0], dz = b[2] - a[2], l = Math.hypot(dx, dz);
            double nx = -dz / l * 0.025, nz = dx / l * 0.025;
            double[][] p = {{a[0] - nx, floorY, a[2] - nz}, {b[0] - nx, floorY, b[2] - nz}, {b[0] - nx, dh, b[2] - nz}, {a[0] - nx, dh, a[2] - nz},
                    {a[0] + nx, floorY, a[2] + nz}, {b[0] + nx, floorY, b[2] + nz}, {b[0] + nx, dh, b[2] + nz}, {a[0] + nx, dh, a[2] + nz}};
            double[] n1 = {-nx, 0, -nz}, n2 = {nx, 0, nz}, n3 = {-dx, 0, -dz}, n4 = {dx, 0, dz};
            quadOut(p[0], p[1], p[2], p[3], n1, Material.DOOR, false, aoY(floorY), aoY(floorY), aoY(dh), aoY(dh));
            quadOut(p[4], p[5], p[6], p[7], n2, Material.DOOR, false, aoY(floorY), aoY(floorY), aoY(dh), aoY(dh));
            quadOut(p[0], p[4], p[7], p[3], n3, Material.DOOR, false, 0.6);
            quadOut(p[1], p[5], p[6], p[2], n4, Material.DOOR, false, 0.6);
            quadOut(p[3], p[7], p[6], p[2], new double[]{0, 1, 0}, Material.DOOR, false, 0.6);
        }

        /** Stufe 1: Türblatt als Fläche knapp vor der Wand. */
        void doorLeafFlat(double c, boolean gable, double outer, double c0, double c1) {
            double f = (outer > 0 ? Math.max(c0, c1) : Math.min(c0, c1)) + outer * 0.015, dh = doorH(), hw = doorW() / 2;
            double[] p0, p1, p2, p3;
            if (gable) {
                p0 = new double[]{f, floorY, c - hw}; p1 = new double[]{f, floorY, c + hw}; p2 = new double[]{f, dh, c + hw}; p3 = new double[]{f, dh, c - hw};
            } else {
                p0 = new double[]{c - hw, floorY, f}; p1 = new double[]{c + hw, floorY, f}; p2 = new double[]{c + hw, dh, f}; p3 = new double[]{c - hw, dh, f};
            }
            double[] out = gable ? new double[]{outer, 0, 0} : new double[]{0, 0, outer};
            quadOut(p0, p1, p2, p3, out, Material.DOOR, false, aoY(floorY), aoY(floorY), aoY(dh), aoY(dh));
        }

        // ---------------------------------------------------------------- Giebel
        void gables() {
            if (opened && t.smokeHole && t != HouseType.GRUBENHAUS) { gablesWithHoles(); return; }
            for (int e = -1; e <= 1; e += 2) {
                double xo = e * hx, xi = e * (hx - wt);
                double[] a = {xo, H, -hz}, b = {xo, H, hz}, c = {xo, yr, 0};
                triOut(a, b, c, new double[]{e, 0, 0}, t.wall, aoY(H) * 0.9);
                double[] ai = {xi, H, -hz}, bi = {xi, H, hz}, ci = {xi, yr, 0};
                triOut(ai, bi, ci, new double[]{-e, 0, 0}, t.wall, 0.5);
                if (t.smokeHole && t != HouseType.GRUBENHAUS) smokeHole(e);
            }
        }

        /**
         * Giebel mit offenem Rauchloch (Phase 11): unter dem Loch ein Trapez über die ganze Breite, links und rechts
         * davon je ein Viereck bis zum First; außen und innen, dazu die Laibung des Lochs.
         */
        void gablesWithHoles() {
            double[] sh = smokeHoleShape(t);
            double bot = sh[0], top = sh[1], hw = sh[2];
            double wb = hz * (yr - bot) / (yr - H);
            for (int e = -1; e <= 1; e += 2) {
                for (int side = 0; side < 2; side++) {
                    double x = side == 0 ? e * hx : e * (hx - wt);
                    double[] out = side == 0 ? new double[]{e, 0, 0} : new double[]{-e, 0, 0};
                    double ao = side == 0 ? aoY(H) * 0.9 : 0.5;
                    quadOut(new double[]{x, H, -hz}, new double[]{x, H, hz}, new double[]{x, bot, wb}, new double[]{x, bot, -wb}, out, t.wall, false, ao);
                    quadOut(new double[]{x, bot, -wb}, new double[]{x, bot, -hw}, new double[]{x, top, 0}, new double[]{x, yr, 0}, out, t.wall, false, ao);
                    quadOut(new double[]{x, bot, hw}, new double[]{x, bot, wb}, new double[]{x, yr, 0}, new double[]{x, top, 0}, out, t.wall, false, ao);
                }
                // Laibung: Unterkante und die beiden schrägen Seiten des Lochs, durch die Wand
                double xo = e * hx, xi = e * (hx - wt);
                quadOut(new double[]{xo, bot, -hw}, new double[]{xo, bot, hw}, new double[]{xi, bot, hw}, new double[]{xi, bot, -hw}, new double[]{0, 1, 0}, Material.POST, false, 0.45);
                for (int s = -1; s <= 1; s += 2) {
                    double sl = top - bot, ln = Math.hypot(hw, sl);           // Normale der Seite zeigt zur Lochmitte (nach unten innen)
                    quadOut(new double[]{xo, bot, s * hw}, new double[]{xo, top, 0}, new double[]{xi, top, 0}, new double[]{xi, bot, s * hw},
                            new double[]{0, -hw / ln, -s * sl / ln}, Material.POST, false, 0.45);
                }
            }
        }

        void smokeHole(int e) {
            double x = e * (hx + 0.012), top = yr - 0.22, bot = Math.max(H + 0.1, yr - 0.95), hw = (top - bot) / tp * 0.9;
            triOut(new double[]{x, bot, -hw}, new double[]{x, bot, hw}, new double[]{x, top, 0}, new double[]{e, 0, 0}, Material.DARK, 0.6);
        }

        // ---------------------------------------------------------------- Pfosten
        void cornerPosts(int sides) {
            for (int s = -1; s <= 1; s += 2)
                for (int e = -1; e <= 1; e += 2)
                    m.beam(new double[]{e * (hx + 0.02), -0.3 + floorY, s * (hz + 0.02)}, new double[]{e * (hx + 0.02), H + 0.06, s * (hz + 0.02)},
                            0.13, sides, Material.POST, sides > 4, aoY(-0.3), aoY(H));
        }

        void posts() {
            cornerPosts(6);
            if (t.wall == Material.DAUB) {
                // Wandständer alle 1 m, außen halb in der Wand
                for (int s = -1; s <= 1; s += 2) postsAlong(-hx, hx, s * (hz + 0.02), false, sideOf(s, false));
                for (int e = -1; e <= 1; e += 2) postsAlong(-hz, hz, e * (hx + 0.02), true, sideOf(e, true));
            } else if (t.wall == Material.PLANK && t != HouseType.GRUBENHAUS) {
                // Spaltbohlen: Schwelle rundum, Ständer alle gut 2 m
                for (int s = -1; s <= 1; s += 2) {
                    double z = s * (hz + 0.06);
                    m.box(0, 0.11, z, hx + 0.12, 0.13, 0.11, Material.POST, false, 0.7);
                    postsAlongStep(-hx, hx, z, false, sideOf(s, false), 2.2);
                }
                for (int e = -1; e <= 1; e += 2) m.box(e * (hx + 0.06), 0.11, 0, 0.11, 0.13, hz + 0.12, Material.POST, false, 0.7);
            }
            // Rähm auf den Traufwänden
            for (int s = -1; s <= 1; s += 2)
                m.beam(new double[]{-hx - 0.15, H + 0.08, s * (hz - wt / 2)}, new double[]{hx + 0.15, H + 0.08, s * (hz - wt / 2)}, 0.11, 4, Material.POST, true, 0.55, 0.55);
            if (t.supports) {
                for (int s = -1; s <= 1; s += 2) {
                    int n = Math.max(2, (int) Math.round(L / SUPPORT_STEP));
                    for (int i = 0; i <= n; i++) {
                        double x = -hx + 0.4 + (L - 0.8) * i / n;
                        if (nearDoor(x, sideOf(s, false), 1.0)) continue;
                        m.beam(new double[]{x, -0.25, s * (hz + 1.05)}, new double[]{x, H - 0.12, s * (hz + 0.08)}, 0.085, 6, Material.POST, true, 0.85, 0.6);
                    }
                }
            }
        }

        HouseType.Side sideOf(int s, boolean gable) {
            return gable ? (s > 0 ? HouseType.Side.EAST : HouseType.Side.WEST) : (s > 0 ? HouseType.Side.SOUTH : HouseType.Side.NORTH);
        }

        boolean nearDoor(double a, HouseType.Side side, double margin) {
            for (HouseType.Door d : t.doors) {
                if (d.side() != side) continue;
                boolean gable = side == HouseType.Side.EAST || side == HouseType.Side.WEST;
                double c = gable ? -hz + d.at() * W : -hx + d.at() * L;
                if (Math.abs(a - c) < doorW() / 2 + margin) return true;
            }
            return false;
        }

        void postsAlong(double a0, double a1, double c, boolean gable, HouseType.Side side) { postsAlongStep(a0, a1, c, gable, side, POST_STEP); }

        void postsAlongStep(double a0, double a1, double c, boolean gable, HouseType.Side side, double step) {
            int n = Math.max(1, (int) Math.round((a1 - a0) / step));
            for (int i = 1; i < n; i++) {
                double a = a0 + (a1 - a0) * i / n;
                if (nearDoor(a, side, 0.12)) continue;
                double[] p = gable ? new double[]{c, -0.3 + floorY, a} : new double[]{a, -0.3 + floorY, c};
                double[] q = gable ? new double[]{c, H + 0.02, a} : new double[]{a, H + 0.02, c};
                m.beam(p, q, 0.095, 6, Material.POST, false, aoY(-0.3), aoY(H));
            }
        }

        // ---------------------------------------------------------------- Dach
        void roof() {
            double xa = -hx - g, xb = hx + g;
            double ytop0 = yr + thk / cp;
            for (int s = -1; s <= 1; s += 2) {
                double[] n = {0, cp, s * sp};
                double ze = s * (hz + eo);
                double[] rbA = {xa, yr, 0}, rbB = {xb, yr, 0}, ebA = {xa, ey, ze}, ebB = {xb, ey, ze};
                double[] rtA = {xa, ytop0, 0}, rtB = {xb, ytop0, 0};
                double[] etA = {xa, ey + cp * thk, ze + s * sp * thk}, etB = {xb, ey + cp * thk, ze + s * sp * thk};
                // Oberseite: Halme vom First zur Traufe (u entlang der Neigung)
                quadOut(rtA, rtB, etB, etA, n, Material.THATCH, false, 1, 1, 0.92, 0.92);
                // Unterseite (im Schatten)
                quadOut(rbA, rbB, ebB, ebA, new double[]{0, -cp, -s * sp}, Material.THATCH, false, 0.32, 0.32, 0.5, 0.5);
                // Traufkante: die geschnittenen Halmenden
                quadOut(ebA, ebB, etB, etA, new double[]{0, -sp, s * cp}, Material.THATCH, true, 0.7, 0.7, 0.85, 0.85);
                // Giebelkanten
                quadOut(new double[]{xa, yr, 0}, ebA, etA, rtA, new double[]{-1, 0, 0}, Material.THATCH, true, 0.7, 0.7, 0.8, 0.8);
                quadOut(new double[]{xb, yr, 0}, ebB, etB, rtB, new double[]{1, 0, 0}, Material.THATCH, true, 0.7, 0.7, 0.8, 0.8);
            }
            // Firstsoden
            double w = 0.62, base = ytop0 - w * tp, apex = ytop0 + 0.12, x0 = xa - 0.06, x1 = xb + 0.06;
            double[] lA = {x0, base, -w}, lB = {x1, base, -w}, rA = {x0, base, w}, rB = {x1, base, w}, tA = {x0, apex, 0}, tB = {x1, apex, 0};
            quadOut(lA, lB, tB, tA, new double[]{0, 1, -1}, Material.SOD, true, 0.85, 0.85, 1, 1);
            quadOut(rA, rB, tB, tA, new double[]{0, 1, 1}, Material.SOD, true, 0.85, 0.85, 1, 1);
            triOut(lA, rA, tA, new double[]{-1, 0, 0}, Material.SOD, 0.8);
            triOut(lB, rB, tB, new double[]{1, 0, 0}, Material.SOD, 0.8);
        }

        // ---------------------------------------------------------------- Sonderfälle
        void shed() {
            double r = 0.12;
            double[] xs = {-hx + r, 0, hx - r};
            for (double x : xs)
                for (int s = -1; s <= 1; s += 2)
                    m.beam(new double[]{x, -0.3, s * (hz - r)}, new double[]{x, H + 0.05, s * (hz - r)}, r, lod == 0 ? 6 : 4, Material.POST, true, 0.75, 0.6);
            for (int s = -1; s <= 1; s += 2)
                m.beam(new double[]{-hx, H + 0.1, s * (hz - r)}, new double[]{hx, H + 0.1, s * (hz - r)}, 0.11, 4, Material.POST, true, 0.55, 0.55);
            // Rückwand aus Flechtwerk zwischen den hinteren Pfosten
            boxAO(-hx + r, hx - r, -0.05, H - 0.15, -hz + r - 0.04, -hz + r + 0.04, Material.WATTLE, false);
            if (lod == 0) {
                // Werkbank und Hackklotz
                m.box(-0.6, 0.42, -hz + 0.75, 1.2, 0.06, 0.32, Material.DECK, false, 0.6);
                for (int i = -1; i <= 1; i += 2) m.box(-0.6 + i * 1.0, 0.18, -hz + 0.75, 0.06, 0.2, 0.25, Material.POST, true, 0.6);
                m.beam(new double[]{1.2, 0, 0.6}, new double[]{1.2, 0.5, 0.6}, 0.22, 8, Material.POST, true, 0.6, 0.9);
            }
        }

        void pitHouse() {
            // Firstpfosten an beiden Giebeln, sichtbar vor dem Giebeldreieck
            for (int e = -1; e <= 1; e += 2)
                m.beam(new double[]{e * (hx + 0.05), -0.4, 0}, new double[]{e * (hx + 0.05), yr + 0.05, 0}, 0.11, lod == 0 ? 6 : 4, Material.POST, true, 0.7, 0.6);
            // dunkler Eingang im Ostgiebel, hinab in die Grube
            double x = hx + 0.015, hw = 0.42;
            quadOut(new double[]{x, 0.0, -hw}, new double[]{x, 0.0, hw}, new double[]{x, 1.25, hw}, new double[]{x, 1.25, -hw}, new double[]{1, 0, 0}, Material.DARK, false, 0.5);
            if (lod == 0) {
                double f = x + 0.03;
                m.beam(new double[]{f, -0.2, -hw - 0.03}, new double[]{f, 1.32, -hw - 0.03}, 0.06, 4, Material.POST, true, 0.7, 0.8);
                m.beam(new double[]{f, -0.2, hw + 0.03}, new double[]{f, 1.32, hw + 0.03}, 0.06, 4, Material.POST, true, 0.7, 0.8);
                if (t.smokeHole) smokeHole(-1);
            }
        }

        void far() {
            // Stufe 2: Kasten (ohne Boden), Giebeldreiecke, Dach ohne Dicke
            if (t.open) {
                boxAO(-hx, hx, 0, H, -hz, -hz + 0.1, Material.WATTLE, false);
                for (int e = -1; e <= 1; e += 2) boxAO(e * hx - 0.12, e * hx + 0.12, 0, H, hz - 0.24, hz, Material.POST, true);
            } else {
                double x0 = -hx, x1 = hx, z0 = -hz, z1 = hz, y0 = Math.min(-0.3, floorY), y1 = H;
                double[][] p = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                boolean gy = t.wall == Material.PLANK;
                side(p[1], p[0], p[3], p[2], t.wall, gy);
                side(p[4], p[5], p[6], p[7], t.wall, gy);
                side(p[0], p[4], p[7], p[3], t.wall, gy);
                side(p[5], p[1], p[2], p[6], t.wall, gy);
                for (int e = -1; e <= 1; e += 2)
                    triOut(new double[]{e * hx, H, -hz}, new double[]{e * hx, H, hz}, new double[]{e * hx, yr, 0}, new double[]{e, 0, 0}, t.wall, 0.8);
            }
            double xa = -hx - g, xb = hx + g, yt = yr + thk / cp;
            for (int s = -1; s <= 1; s += 2) {
                double ze = s * (hz + eo);
                quadOut(new double[]{xa, yt, 0}, new double[]{xb, yt, 0}, new double[]{xb, ey + cp * thk, ze}, new double[]{xa, ey + cp * thk, ze},
                        new double[]{0, cp, s * sp}, Material.THATCH, false, 1, 1, 0.9, 0.9);
                quadOut(new double[]{xa, yt, 0}, new double[]{xb, yt, 0}, new double[]{xb, ey + cp * thk, ze}, new double[]{xa, ey + cp * thk, ze},
                        new double[]{0, -cp, -s * sp}, Material.THATCH, false, 0.35, 0.35, 0.5, 0.5);
            }
        }

        // ---------------------------------------------------------------- Helfer
        /** Viereck, dessen Vorderseite nach out zeigt; u entlang a → b (uAlongAB) oder a → d. */
        void quadOut(double[] a, double[] b, double[] c, double[] d, double[] out, Material mat, boolean uAlongAB, double... ao) {
            double[] n = Mesh.normal(a, b, d);
            double a0 = ao.length > 0 ? ao[0] : 1, a1 = ao.length > 1 ? ao[1] : a0, a2 = ao.length > 2 ? ao[2] : a0, a3 = ao.length > 3 ? ao[3] : a0;
            if (Mesh.dot(n, out) >= 0) {
                if (uAlongAB) m.quad(a, b, c, d, mat, 0, 0, a0, a1, a2, a3);
                else m.quadUV(a, b, c, d, mat, a0, a1, a2, a3);
            } else {
                // umgekehrt: a, d, c, b — dann liegt a → b als a → d der neuen Folge
                if (uAlongAB) m.quadUV(a, d, c, b, mat, a0, a3, a2, a1);
                else m.quad(a, d, c, b, mat, 0, 0, a0, a3, a2, a1);
            }
        }

        void triOut(double[] a, double[] b, double[] c, double[] out, Material mat, double ao) {
            double[] n = Mesh.normal(a, b, c);
            if (Mesh.dot(n, out) >= 0) m.triangle(a, b, c, mat, ao);
            else m.triangle(a, c, b, mat, ao);
        }

        static double clamp(double v) { return Math.max(0, Math.min(1, v)); }
    }
}
