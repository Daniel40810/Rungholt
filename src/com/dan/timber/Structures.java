package com.dan.timber;

import java.util.SplittableRandom;

/**
 * Bauwerke entlang von Linien, als Netz in Szenenmaßen (nicht instanziert; einmal gebaut, dann gezeichnet):
 * Flechtzäune, Bohlenwege, Palisaden und Landebrücken. Die Linie ist eine Folge von Punkten {x, z}; die Bodenhöhe
 * kommt über {@link Ground}. Maße nach Befunden in Haithabu, Einzelheiten gestaltet [G].
 */
public final class Structures {
    private Structures() { }

    /** Bodenhöhe der Szene am Ort (m). */
    @FunctionalInterface
    public interface Ground { double height(double x, double z); }

    public static final Ground FLAT = (x, z) -> 0;

    // ==================================================================== Kacheln mit zwei Stufen

    /** Eine Kachel: Mitte, Radius, feines und grobes Netz. */
    public record Tile(double cx, double cz, double radius, Mesh detail, Mesh coarse) { }

    /**
     * Sammelt Bauwerke in Kacheln zu {@link #TILE} m, jede mit einem feinen Netz (für die Nähe) und einem groben (Zaun
     * als Fläche mit wenigen Pfosten, Weg und Deck als Band, Palisade als Wand), damit eine ganze Siedlung mit
     * Kilometern an Zäunen und Wegen billig bleibt. Linien werden in Stücke zu höchstens 40 m geteilt.
     */
    public static final class Set {
        public static final double TILE = 128;
        private final java.util.Map<Long, Mesh[]> map = new java.util.LinkedHashMap<>();
        private final java.util.Map<Long, double[]> ext = new java.util.HashMap<>();

        public void fence(double[][] line, Ground g, long seed) { pieces(line, (pc, k) -> add(pc, Structures.fence(pc, g, seed + k), coarseFence(pc, g))); }
        public void walkway(double[][] line, double width, Ground g, long seed) { pieces(line, (pc, k) -> add(pc, Structures.walkway(pc, width, g, seed + k), band(pc, width, g, 0.1, Material.DECK))); }
        public void palisade(double[][] line, double height, Ground g, long seed) { pieces(line, (pc, k) -> add(pc, Structures.palisade(pc, height, g, seed + k), wall(pc, height, g))); }
        public void pier(double[][] line, double width, double deckY, Ground g, long seed) {
            pieces(line, (pc, k) -> add(pc, Structures.pier(pc, width, deckY, g, seed + k), coarsePier(pc, width, deckY, g)));
        }

        /** Fischgestell, Brunnen, Boot (seit Haithabu Phase 5): feines und grobes Netz an ihrem Ort. */
        public void rack(double x, double z, double headingDeg, double length, Ground g, long seed) {
            add(around(x, z, length / 2 + 1), Structures.rack(x, z, headingDeg, length, g, seed), coarseRack(x, z, headingDeg, length, g));
        }
        public void well(double x, double z, double headingDeg, Ground g, long seed) {
            add(around(x, z, 4), Structures.well(x, z, headingDeg, g, seed), coarseWell(x, z, headingDeg, g));
        }
        public void boat(double x, double z, double headingDeg, double length, double rollDeg, Ground g, long seed) {
            add(around(x, z, length / 2 + 0.5), Structures.boat(x, z, headingDeg, length, rollDeg, g, seed, 12), Structures.boat(x, z, headingDeg, length, rollDeg, g, seed, 5));
        }
        /** Wehrgang auf Pfosten, lift über dem Boden (seit Haithabu Phase 7, {@link Fortification#wallWalk}). */
        public void wallWalk(double[][] line, double width, double lift, Ground g, long seed) {
            pieces(line, (pc, k) -> add(pc, Fortification.wallWalk(pc, width, lift, g, seed + k), Fortification.coarseWallWalk(pc, width, lift, g)));
        }
        /** Torbau im Erdwall (seit Haithabu Phase 7, {@link Fortification#gate}). */
        public void gate(Fortification.Gate gt, long seed) {
            add(around(gt.x(), gt.z(), gt.depth() / 2 + 2), Fortification.gate(gt, seed), Fortification.coarseGate(gt));
        }
        /** Plattform auf Pfahlreihen (seit Haithabu Phase 8, {@link Wharf#platform}). */
        public void platform(double[][] line, double width, double deckY, Ground g, long seed) {
            pieces(line, (pc, k) -> add(pc, Wharf.platform(pc, width, deckY, g, seed + k), Wharf.coarsePlatform(pc, width, deckY, g)));
        }
        /** Pfahlreihe im Wasser bis zur Höhe topY (seit Haithabu Phase 8, {@link Wharf#palisadeTo}). */
        public void palisadeTo(double[][] line, double topY, Ground g, long seed) {
            pieces(line, (pc, k) -> add(pc, Wharf.palisadeTo(pc, topY, g, seed + k), Wharf.coarsePalisadeTo(pc, topY, g)));
        }
        /** Uferbefestigung aus Bohlen (seit Haithabu Phase 8, {@link Wharf#revetment}). */
        public void revetment(double[][] line, double height, Ground g, long seed) {
            pieces(line, (pc, k) -> { Mesh d = Wharf.revetment(pc, height, g, seed + k); add(pc, d, d); });
        }
        /** Ein beliebiges Netz (fein und grob) um (x, z) mit Halbmesser r. */
        public void mesh(double x, double z, double r, Mesh detail, Mesh coarse) { add(around(x, z, r), detail, coarse); }

        private static double[][] around(double x, double z, double r) { return new double[][]{{x - r, z - r}, {x + r, z + r}}; }

        private interface Piece { void piece(double[][] pc, int k); }

        private static void pieces(double[][] line, Piece p) {
            java.util.List<double[]> cur = new java.util.ArrayList<>();
            double len = 0;
            int k = 0;
            cur.add(line[0]);
            for (int i = 1; i < line.length; i++) {
                double[] a = line[i - 1], b = line[i];
                double l = Math.hypot(b[0] - a[0], b[1] - a[1]);
                int n = Math.max(1, (int) Math.ceil(l / 40));
                for (int j = 1; j <= n; j++) {
                    double[] q = {a[0] + (b[0] - a[0]) * j / n, a[1] + (b[1] - a[1]) * j / n};
                    cur.add(q);
                    len += l / n;
                    if (len >= 39.9) {
                        p.piece(cur.toArray(new double[0][]), k++);
                        cur = new java.util.ArrayList<>();
                        cur.add(q);
                        len = 0;
                    }
                }
            }
            if (cur.size() > 1) p.piece(cur.toArray(new double[0][]), k);
        }

        private void add(double[][] pc, Mesh detail, Mesh coarse) {
            double mx = 0, mz = 0;
            for (double[] q : pc) { mx += q[0]; mz += q[1]; }
            mx /= pc.length;
            mz /= pc.length;
            long key = (((long) Math.floor(mx / TILE)) << 32) ^ (((long) Math.floor(mz / TILE)) & 0xffffffffL);
            Mesh[] t = map.computeIfAbsent(key, x -> new Mesh[]{new Mesh(), new Mesh()});
            t[0].append(detail);
            t[1].append(coarse);
            double[] e = ext.computeIfAbsent(key, x -> new double[]{Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE});
            for (double[] q : pc) { e[0] = Math.min(e[0], q[0]); e[1] = Math.min(e[1], q[1]); e[2] = Math.max(e[2], q[0]); e[3] = Math.max(e[3], q[1]); }
        }

        /** Die Kacheln (Mitte und Radius aus der Ausdehnung der Linien, plus 5 m). */
        public java.util.List<Tile> tiles() {
            java.util.List<Tile> l = new java.util.ArrayList<>();
            for (var en : map.entrySet()) {
                double[] e = ext.get(en.getKey());
                double cx = (e[0] + e[2]) / 2, cz = (e[1] + e[3]) / 2;
                l.add(new Tile(cx, cz, Math.hypot(e[2] - e[0], e[3] - e[1]) / 2 + 5, en.getValue()[0], en.getValue()[1]));
            }
            return l;
        }

        public long triangles(boolean coarse) {
            long s = 0;
            for (Mesh[] m : map.values()) s += m[coarse ? 1 : 0].triangles();
            return s;
        }
    }

    // ------------------------------------------------------------------ grobe Formen

    /** Zaun grob: Flechtwerk als beidseitige Fläche, Pfosten alle 3,6 m kantig. */
    static Mesh coarseFence(double[][] line, Ground g) {
        Mesh m = new Mesh();
        segments(line, 4.0, (x0, z0, x1, z1) -> {
            double y0 = g.height(x0, z0), y1 = g.height(x1, z1);
            double[] a = {x0, y0 + 0.05, z0}, b = {x1, y1 + 0.05, z1}, c = {x1, y1 + 1.0, z1}, d = {x0, y0 + 1.0, z0};
            m.quad(a, b, c, d, Material.WATTLE, 0, 0, 0.7, 0.7, 1, 1);
            m.quad(b, a, d, c, Material.WATTLE, 0, 0, 0.7, 0.7, 1, 1);
            m.beam(new double[]{x0, y0 - 0.1, z0}, new double[]{x0, y0 + 1.2, z0}, 0.05, 3, Material.POST, false, 0.6, 1);
        });
        return m;
    }

    /** Band auf dem Boden (Weg, Deck): eine Fläche je Stück. */
    static Mesh band(double[][] line, double width, Ground g, double lift, Material mat) {
        Mesh m = new Mesh();
        double hw = width / 2;
        segments(line, 6.0, (x0, z0, x1, z1) -> {
            double l = Math.hypot(x1 - x0, z1 - z0), nx = -(z1 - z0) / l * hw, nz = (x1 - x0) / l * hw;
            double y0 = g.height(x0, z0) + lift, y1 = g.height(x1, z1) + lift;
            double[] a = {x0 - nx, y0, z0 - nz}, b = {x0 + nx, y0, z0 + nz}, c = {x1 + nx, y1, z1 + nz}, d = {x1 - nx, y1, z1 - nz};
            double[] n = Mesh.normal(a, b, d);
            if (n[1] >= 0) m.quad(a, b, c, d, mat, 0, 0, 1); else m.quad(a, d, c, b, mat, 0, 0, 1);
        });
        return m;
    }

    /** Palisade grob: Wand aus zwei Flächen. */
    static Mesh wall(double[][] line, double h, Ground g) {
        Mesh m = new Mesh();
        segments(line, 6.0, (x0, z0, x1, z1) -> {
            double y0 = g.height(x0, z0), y1 = g.height(x1, z1);
            double[] a = {x0, y0 - 0.2, z0}, b = {x1, y1 - 0.2, z1}, c = {x1, y1 + h + 0.2, z1}, d = {x0, y0 + h + 0.2, z0};
            m.quadUV(a, b, c, d, Material.POST, 0.6, 0.6, 1, 1);
            m.quadUV(b, a, d, c, Material.POST, 0.6, 0.6, 1, 1);
        });
        return m;
    }

    /** Brücke grob: Deck als Band, Pfostenpaare alle 5 m kantig. */
    static Mesh coarsePier(double[][] line, double width, double deckY, Ground g) {
        Mesh m = band(line, width, (x, z) -> deckY, 0, Material.DECK);
        double hw = width / 2;
        walk(line, 5.0, (x, z, dx, dz, first) -> {
            for (int s2 = -1; s2 <= 1; s2 += 2) {
                double px = x - dz * s2 * (hw - 0.15), pz = z + dx * s2 * (hw - 0.15);
                m.beam(new double[]{px, Math.min(deckY - 1, g.height(px, pz)), pz}, new double[]{px, deckY + 0.3, pz}, 0.13, 4, Material.POST, false, 0.4, 1);
            }
        });
        return m;
    }

    /**
     * Flechtzaun: Pfosten alle 0,9 m (rund, 1,2 m über dem Boden), dazwischen Ruten waagerecht geflochten (1,0 m hoch,
     * beidseitig sichtbar). Folgt dem Boden.
     */
    public static Mesh fence(double[][] line, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        walk(line, 0.9, (x, z, dx, dz, first) -> {
            double y = g.height(x, z), tilt = (r.nextDouble() - 0.5) * 0.05;
            m.beam(new double[]{x, y - 0.3, z}, new double[]{x + tilt, y + 1.15 + r.nextDouble() * 0.1, z + tilt}, 0.045, 5, Material.POST, true, 0.6, 1);
        });
        // Flechtwerk zwischen den Punkten (in Stücken zu höchstens 2 m, damit es dem Boden folgt)
        segments(line, 2.0, (x0, z0, x1, z1) -> {
            double y0 = g.height(x0, z0), y1 = g.height(x1, z1);
            double nx = -(z1 - z0), nz = x1 - x0, l = Math.hypot(nx, nz);
            nx /= l; nz /= l;
            double o = 0.03;
            double[] a = {x0 + nx * o, y0 + 0.05, z0 + nz * o}, b = {x1 + nx * o, y1 + 0.05, z1 + nz * o}, c = {x1 + nx * o, y1 + 1.0, z1 + nz * o}, d = {x0 + nx * o, y0 + 1.0, z0 + nz * o};
            m.quad(a, b, c, d, Material.WATTLE, 0, 0, 0.7, 0.7, 1, 1);
            double[] a2 = {x1 - nx * o, y1 + 0.05, z1 - nz * o}, b2 = {x0 - nx * o, y0 + 0.05, z0 - nz * o}, c2 = {x0 - nx * o, y0 + 1.0, z0 - nz * o}, d2 = {x1 - nx * o, y1 + 1.0, z1 - nz * o};
            m.quad(a2, b2, c2, d2, Material.WATTLE, 0, 0, 0.7, 0.7, 1, 1);
        });
        return m;
    }

    /**
     * Bohlenweg: quer liegende Spaltbohlen (0,25 m breit, 6 cm dick) auf zwei Längshölzern, width breit; jede Bohle
     * etwas versetzt und verkantet. Folgt dem Boden, 8 cm darüber.
     */
    public static Mesh walkway(double[][] line, double width, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double hw = width / 2;
        segments(line, 2.4, (x0, z0, x1, z1) -> {
            double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz), nx = -dz / l, nz = dx / l;
            for (int s = -1; s <= 1; s += 2) {
                double ox = nx * s * hw * 0.65, oz = nz * s * hw * 0.65;
                m.beam(new double[]{x0 + ox, g.height(x0, z0) + 0.02, z0 + oz}, new double[]{x1 + ox, g.height(x1, z1) + 0.02, z1 + oz}, 0.07, 4, Material.POST, false, 0.5, 0.5);
            }
        });
        walk(line, 0.27, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx, shift = (r.nextDouble() - 0.5) * 0.12, w = hw * (0.92 + r.nextDouble() * 0.12);
            double y = g.height(x, z) + 0.1 + (r.nextDouble() - 0.5) * 0.02;
            plank(m, x + nx * shift, y, z + nz * shift, dx, dz, w, 0.12, 0.03, Material.DECK, (r.nextDouble() - 0.5) * 0.04);
        });
        return m;
    }

    /**
     * Palisade: dicht an dicht gerammte Rundhölzer (Radius 0,14 m) mit gespitztem Kopf, height über dem Boden.
     */
    public static Mesh palisade(double[][] line, double height, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        walk(line, 0.29, (x, z, dx, dz, first) -> {
            double y = g.height(x, z), h = height * (0.95 + r.nextDouble() * 0.1), rad = 0.13 + r.nextDouble() * 0.03;
            m.beam(new double[]{x, y - 0.5, z}, new double[]{x, y + h, z}, rad, 6, Material.POST, false, 0.55, 1);
            spike(m, x, y + h, z, rad, 0.35 + r.nextDouble() * 0.1);
        });
        return m;
    }

    /**
     * Landebrücke: Bohlendeck (width breit) in deckY über dem Wasser auf Pfostenpaaren alle 2,5 m, die bis zum Grund
     * (Bodenhöhe aus g, unter Wasser) reichen; Längsbalken unter dem Deck.
     */
    public static Mesh pier(double[][] line, double width, double deckY, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double hw = width / 2;
        walk(line, 2.5, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx;
            for (int s = -1; s <= 1; s += 2) {
                double px = x + nx * s * (hw - 0.15), pz = z + nz * s * (hw - 0.15);
                m.beam(new double[]{px, Math.min(deckY - 1, g.height(px, pz)) - 0.5, pz}, new double[]{px, deckY + 0.3 + r.nextDouble() * 0.2, pz}, 0.13, 6, Material.POST, true, 0.4, 1);
            }
            plank(m, x, deckY - 0.18, z, dx, dz, hw, 0.12, 0.1, Material.POST, 0);
        });
        segments(line, 5.0, (x0, z0, x1, z1) -> {
            double dx = x1 - x0, dz = z1 - z0, l = Math.hypot(dx, dz), nx = -dz / l, nz = dx / l;
            for (double f : new double[]{-0.75, 0, 0.75})
                m.beam(new double[]{x0 + nx * f * hw, deckY - 0.12, z0 + nz * f * hw}, new double[]{x1 + nx * f * hw, deckY - 0.12, z1 + nz * f * hw}, 0.09, 4, Material.POST, false, 0.45, 0.45);
        });
        walk(line, 0.27, (x, z, dx, dz, first) -> {
            double nx = -dz, nz = dx, shift = (r.nextDouble() - 0.5) * 0.08;
            plank(m, x + nx * shift, deckY, z + nz * shift, dx, dz, hw * (0.97 + r.nextDouble() * 0.06), 0.12, 0.035, Material.DECK, (r.nextDouble() - 0.5) * 0.02);
        });
        return m;
    }

    // ------------------------------------------------------------------ Hof und Ufer (seit Haithabu Phase 5)

    /** Örtlicher Rahmen: +s entlang der Richtung (Grad von Nord im Uhrzeigersinn), +q quer (nach rechts), y oben. */
    private static double[] at(double x, double z, double h, double sAlong, double q, double y) {
        double a = Math.toRadians(h);
        double ax = Math.sin(a), az = -Math.cos(a), bx = Math.cos(a), bz = Math.sin(a);
        return new double[]{x + ax * sAlong + bx * q, y, z + az * sAlong + bz * q};
    }

    /**
     * Fischgestell: zwei Reihen Pfosten (1,9 m) im Abstand 1,2 m, alle 1,8 m, dazwischen Stangen in zwei Höhen; über
     * jeder Stange hängen gespaltene Dorsche zum Trocknen (Werkstoff {@link Material#FISH}), paarweise über die Stange.
     */
    public static Mesh rack(double x, double z, double headingDeg, double length, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        int n = Math.max(2, (int) Math.round(length / 1.8) + 1);
        double[] rails = {1.85, 1.35};
        for (int side = -1; side <= 1; side += 2) {
            double q = side * 0.6;
            for (int i = 0; i < n; i++) {
                double sa = -length / 2 + length * i / (n - 1);
                double[] b = at(x, z, headingDeg, sa, q, 0);
                double y = g.height(b[0], b[2]);
                double[] top = at(x, z, headingDeg, sa + (r.nextDouble() - 0.5) * 0.05, q, y + 1.95);
                m.beam(new double[]{b[0], y - 0.4, b[2]}, top, 0.055, 5, Material.POST, true, 0.6, 1);
            }
            for (double ry : rails) {
                double[] a = at(x, z, headingDeg, -length / 2 - 0.15, q, 0), b = at(x, z, headingDeg, length / 2 + 0.15, q, 0);
                double ya = g.height(a[0], a[2]) + ry, yb = g.height(b[0], b[2]) + ry;
                a[1] = ya; b[1] = yb;
                m.beam(a, b, 0.032, 5, Material.POST, true, 0.85, 0.85);
                // Fische: je 0,2 m ein Paar über der Stange (vorn und hinten), leicht verdreht, verschieden lang
                for (double sa = -length / 2 + 0.2; sa < length / 2 - 0.1; sa += 0.2) {
                    if (r.nextDouble() < 0.12) continue;
                    double len = 0.35 + r.nextDouble() * 0.2, w = 0.07 + r.nextDouble() * 0.03, yy = g.height(at(x, z, headingDeg, sa, q, 0)[0], at(x, z, headingDeg, sa, q, 0)[2]) + ry;
                    for (int f = -1; f <= 1; f += 2) {
                        double sw = (r.nextDouble() - 0.5) * 0.08;
                        double[] t0 = at(x, z, headingDeg, sa - w / 2, q + f * 0.02, yy), t1 = at(x, z, headingDeg, sa + w / 2, q + f * 0.02, yy);
                        double[] b1 = at(x, z, headingDeg, sa + w / 2 + sw, q + f * 0.09, yy - len), b0 = at(x, z, headingDeg, sa - w / 2 + sw, q + f * 0.09, yy - len);
                        m.quad(b0, b1, t1, t0, Material.FISH, 0, 0, 0.8, 0.8, 1, 1);
                        m.quad(b1, b0, t0, t1, Material.FISH, 0, 0, 0.8, 0.8, 1, 1);
                    }
                }
            }
        }
        return m;
    }

    static Mesh coarseRack(double x, double z, double headingDeg, double length, Ground g) {
        Mesh m = new Mesh();
        for (int side = -1; side <= 1; side += 2) {
            double q = side * 0.6;
            double[] a = at(x, z, headingDeg, -length / 2, q, 0), b = at(x, z, headingDeg, length / 2, q, 0);
            double ya = g.height(a[0], a[2]), yb = g.height(b[0], b[2]);
            double[] p0 = {a[0], ya + 1.0, a[2]}, p1 = {b[0], yb + 1.0, b[2]}, p2 = {b[0], yb + 1.85, b[2]}, p3 = {a[0], ya + 1.85, a[2]};
            m.quad(p0, p1, p2, p3, Material.FISH, 0, 0, 0.8, 0.8, 1, 1);
            m.quad(p1, p0, p3, p2, Material.FISH, 0, 0, 0.8, 0.8, 1, 1);
            m.beam(new double[]{a[0], ya - 0.2, a[2]}, new double[]{a[0], ya + 1.95, a[2]}, 0.06, 3, Material.POST, false, 0.6, 1);
            m.beam(new double[]{b[0], yb - 0.2, b[2]}, new double[]{b[0], yb + 1.95, b[2]}, 0.06, 3, Material.POST, false, 0.6, 1);
        }
        return m;
    }

    /**
     * Brunnen: ein Kasten aus Spaltbohlen (1,6 m im Geviert, 0,75 m hoch) über dem mit Holz ausgekleideten Schacht,
     * innen dunkles Wasser; daneben ein Ziehbrunnen-Schwengel: Gabelpfosten (2,6 m), Wippbaum (6 m) mit
     * Gegengewicht, Stange und Eimer über dem Kasten.
     */
    public static Mesh well(double x, double z, double headingDeg, Ground g, long seed) {
        Mesh m = new Mesh();
        SplittableRandom r = new SplittableRandom(seed);
        double y = g.height(x, z), hs = 0.8, t = 0.06, h = 0.75;
        double a = Math.toRadians(headingDeg), ax = Math.sin(a), az = -Math.cos(a);
        // vier Wände aus Bohlen (je 3 übereinander), als Kästen; die Kästen sind achsparallel im örtlichen Rahmen
        for (int k = 0; k < 4; k++) {
            double ang = a + k * Math.PI / 2;
            double dx = Math.sin(ang), dz = -Math.cos(ang);          // nach außen zeigende Richtung der Wand
            for (int b = 0; b < 3; b++) {
                double yb = y - 0.1 + b * h / 3 + h / 6;
                double cx = x + dx * (hs - t), cz = z + dz * (hs - t);
                // Bohle: lang entlang (−dz, dx)
                plank(m, cx, yb, cz, dx, dz, hs + (k % 2 == 0 ? t : -t) + r.nextDouble() * 0.04, t, h / 6 - 0.01, Material.PLANK, 0);
            }
        }
        // Wasser innen
        double wy = y + 0.1;
        double[] c = {x, wy, z};
        double ih = hs - 2 * t;
        double[] w0 = {x - ih, wy, z - ih}, w1 = {x + ih, wy, z - ih}, w2 = {x + ih, wy, z + ih}, w3 = {x - ih, wy, z + ih};
        m.quad(w0, w3, w2, w1, Material.DARK, 0, 0, 0.3);
        // Schwengel
        double px = x - ax * 2.3, pz = z - az * 2.3, py = g.height(px, pz);
        m.beam(new double[]{px, py - 0.5, pz}, new double[]{px, py + 2.6, pz}, 0.1, 6, Material.POST, true, 0.6, 1);
        double tilt = 0.35 + r.nextDouble() * 0.1;
        double[] pivot = {px, py + 2.55, pz};
        double[] front = {pivot[0] + ax * 3.6 * Math.cos(tilt), pivot[1] - 3.6 * Math.sin(tilt) + 0.9, pivot[2] + az * 3.6 * Math.cos(tilt)};
        double[] back = {pivot[0] - ax * 2.4 * Math.cos(tilt), pivot[1] + 2.4 * Math.sin(tilt) - 0.6, pivot[2] - az * 2.4 * Math.cos(tilt)};
        m.beam(back, front, 0.065, 6, Material.POST, true, 0.9, 0.9);
        m.box(back[0], back[1] - 0.15, back[2], 0.2, 0.18, 0.2, Material.POST, false, 0.7);   // Gegengewicht (Klotz)
        double[] hook = {x, front[1], z};
        m.beam(front, new double[]{x + ax * 0.0, y + 1.0, z}, 0.02, 4, Material.POST, false, 0.9, 0.9);
        m.box(x, y + 0.9, z, 0.13, 0.14, 0.13, Material.PLANK, true, 0.7);                    // Eimer
        return m;
    }

    static Mesh coarseWell(double x, double z, double headingDeg, Ground g) {
        Mesh m = new Mesh();
        double y = g.height(x, z);
        m.box(x, y + 0.3, z, 0.8, 0.4, 0.8, Material.PLANK, true, 0.7);
        double a = Math.toRadians(headingDeg), px = x - Math.sin(a) * 2.3, pz = z + Math.cos(a) * 2.3, py = g.height(px, pz);
        m.beam(new double[]{px, py, pz}, new double[]{px, py + 2.6, pz}, 0.1, 3, Material.POST, false, 0.6, 1);
        return m;
    }

    /**
     * Kleines Boot, am Strand aufgezogen [G]: geklinkerter Rumpf (Länge length, Breite 0,25 · length, Tiefe 0,55 m),
     * spitz an beiden Enden, die Steven hochgezogen, zwei Duchten; um die Längsachse um rollDeg gekippt, der Kiel auf
     * dem Boden. Außen und innen Bohlen, die Fugen laufen als Plankengänge. sections bestimmt die Feinheit.
     */
    public static Mesh boat(double x, double z, double headingDeg, double length, double rollDeg, Ground g, long seed, int sections) {
        Mesh m = new Mesh();
        double L = length, W = 0.25 * length, D = 0.55;
        int ns = Math.max(4, sections), nr = sections > 6 ? 5 : 3;
        double roll = Math.toRadians(rollDeg);
        double y0 = g.height(x, z) + 0.12;
        // örtlich: s entlang (−L/2..L/2), q quer, h über dem Kiel; Rollen um die Längsachse in Kielhöhe
        double[][][] P = new double[ns + 1][2 * nr + 1][];
        for (int i = 0; i <= ns; i++) {
            double t = (double) i / ns, e = 2 * t - 1;
            double hw = W / 2 * Math.pow(Math.max(0, 1 - e * e), 0.55), gh = D * (1 + 0.55 * Math.pow(Math.abs(e), 3.5));
            double sa = e * L / 2;
            for (int j = -nr; j <= nr; j++) {
                double u = Math.abs(j) / (double) nr, sg = Math.signum(j);
                double q = sg * hw * Math.pow(Math.sin(u * Math.PI / 2), 0.75), h = gh * Math.pow(u, 1.35);
                double qq = q * Math.cos(roll) - h * Math.sin(roll), hh = q * Math.sin(roll) + h * Math.cos(roll);
                P[i][j + nr] = at(x, z, headingDeg, sa, qq, y0 + hh);
            }
        }
        double[] ctrLine0 = at(x, z, headingDeg, 0, -Math.sin(roll) * D * 0.6, y0 + Math.cos(roll) * D * 0.6);
        for (int i = 0; i < ns; i++)
            for (int j = 0; j < 2 * nr; j++) {
                double[] a = P[i][j], b = P[i + 1][j], c = P[i + 1][j + 1], d = P[i][j + 1];
                double vv = Math.abs(j - nr + 0.5) * 0.24;
                // außen: Normale weg von der Mittellinie des Rumpfs auf dieser Höhe
                double[] mid = {(a[0] + b[0] + c[0] + d[0]) / 4, (a[1] + b[1] + c[1] + d[1]) / 4, (a[2] + b[2] + c[2] + d[2]) / 4};
                double[] axisPt = at(x, z, headingDeg, 0, 0, 0);
                double[] out = {mid[0] - ctrLine0[0], mid[1] - ctrLine0[1], mid[2] - ctrLine0[2]};
                double[] n = Mesh.normal(a, b, d);
                boolean outward = Mesh.dot(n, out) >= 0;
                if (outward) { m.quad(a, b, c, d, Material.PLANK, 0, vv, 0.75); m.quad(b, a, d, c, Material.PLANK, 0, vv, 0.45); }
                else { m.quad(b, a, d, c, Material.PLANK, 0, vv, 0.75); m.quad(a, b, c, d, Material.PLANK, 0, vv, 0.45); }
            }
        // Steven vorn und hinten
        for (int end = 0; end <= ns; end += ns) {
            double[] keel = P[end][nr], top = P[end][0];
            double[] tip = {(P[end][0][0] + P[end][2 * nr][0]) / 2, Math.max(P[end][0][1], P[end][2 * nr][1]) + 0.15, (P[end][0][2] + P[end][2 * nr][2]) / 2};
            m.beam(keel, tip, 0.05, 4, Material.POST, true, 0.7, 1);
        }
        m.beam(P[0][nr], P[ns][nr], 0.05, 4, Material.POST, false, 0.6, 0.6);   // Kiel
        // Duchten
        if (sections > 6)
            for (double f : new double[]{-0.2, 0.18}) {
                int i = (int) Math.round((f + 0.5) * ns);
                double[] l = P[i][1], rr = P[i][2 * nr - 1];
                double[] cc = {(l[0] + rr[0]) / 2, (l[1] + rr[1]) / 2 - 0.05, (l[2] + rr[2]) / 2};
                double dx = rr[0] - l[0], dz = rr[2] - l[2], ll = Math.hypot(dx, dz);
                plank(m, cc[0], cc[1], cc[2], -dz / ll, dx / ll, ll / 2, 0.12, 0.025, Material.DECK, (rr[1] - l[1]) / Math.max(1e-3, ll));
            }
        return m;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Bohle quer zur Richtung (dx, dz): halbe Länge hl quer, halbe Breite hb längs, halbe Dicke ht. */
    static void plank(Mesh m, double x, double y, double z, double dx, double dz, double hl, double hb, double ht, Material mat, double cant) {
        double nx = -dz, nz = dx;
        double[][] p = new double[8][];
        int i = 0;
        for (int yy = -1; yy <= 1; yy += 2)
            for (int[] c : new int[][]{{-1, -1}, {1, -1}, {1, 1}, {-1, 1}}) {
                double a = c[0] * hl, b = c[1] * hb;
                p[i++] = new double[]{x + nx * a + dx * b, y + yy * ht + a * cant, z + nz * a + dz * b};
            }
        // p0..3 unten, p4..7 oben (Reihenfolge der Ecken: −−, +−, ++, −+ in (quer, längs))
        quadUp(m, p[4], p[5], p[6], p[7], mat, 1);
        quadUp(m, p[3], p[2], p[1], p[0], mat, 0.5);
        double[] ctr = {x, y, z};
        side(m, p[0], p[1], p[5], p[4], mat, ctr);
        side(m, p[1], p[2], p[6], p[5], mat, ctr);
        side(m, p[2], p[3], p[7], p[6], mat, ctr);
        side(m, p[3], p[0], p[4], p[7], mat, ctr);
    }

    private static void quadUp(Mesh m, double[] a, double[] b, double[] c, double[] d, Material mat, double ao) {
        double[] n = Mesh.normal(a, b, d);
        double want = ao >= 1 ? 1 : -1;
        if (n[1] * want >= 0) m.quad(a, b, c, d, mat, 0, 0, ao);
        else m.quad(a, d, c, b, mat, 0, 0, ao);
    }

    /** Seitenfläche, deren Normale vom Mittelpunkt der Bohle weg zeigt. */
    private static void side(Mesh m, double[] a, double[] b, double[] c, double[] d, Material mat, double[] ctr) {
        double[] mid = {(a[0] + b[0] + c[0] + d[0]) / 4 - ctr[0], (a[1] + b[1] + c[1] + d[1]) / 4 - ctr[1], (a[2] + b[2] + c[2] + d[2]) / 4 - ctr[2]};
        if (Mesh.dot(Mesh.normal(a, b, d), mid) >= 0) m.quad(a, b, c, d, mat, 0, 0, 0.8);
        else m.quad(a, d, c, b, mat, 0, 0, 0.8);
    }

    /** Spitzer Kopf eines Pfostens: Kegel mit 6 Seiten. */
    static void spike(Mesh m, double x, double y, double z, double r, double h) {
        int n = 6;
        for (int i = 0; i < n; i++) {
            double a0 = 2 * Math.PI * i / n, a1 = 2 * Math.PI * (i + 1) / n;
            double[] p0 = {x + Math.cos(a0) * r, y, z + Math.sin(a0) * r}, p1 = {x + Math.cos(a1) * r, y, z + Math.sin(a1) * r}, top = {x, y + h, z};
            double[] nn = Mesh.normal(p0, top, p1);
            if (nn[1] < 0) m.triangle(p0, p1, top, Material.POST, 1); else m.triangle(p0, top, p1, Material.POST, 1);
        }
    }

    interface Step { void at(double x, double z, double dx, double dz, boolean first); }

    interface Seg { void seg(double x0, double z0, double x1, double z1); }

    /** Punkte in festem Abstand entlang der Linie, mit Richtung (Einheitsvektor). */
    static void walk(double[][] line, double step, Step s) {
        double carry = 0;
        boolean first = true;
        for (int i = 0; i + 1 < line.length; i++) {
            double x0 = line[i][0], z0 = line[i][1], x1 = line[i + 1][0], z1 = line[i + 1][1];
            double l = Math.hypot(x1 - x0, z1 - z0);
            if (l < 1e-9) continue;
            double dx = (x1 - x0) / l, dz = (z1 - z0) / l;
            double d = carry;
            for (; d <= l; d += step) {
                s.at(x0 + dx * d, z0 + dz * d, dx, dz, first);
                first = false;
            }
            carry = d - l;
        }
    }

    /** Die Linie in Stücke von höchstens maxLen. */
    static void segments(double[][] line, double maxLen, Seg s) {
        for (int i = 0; i + 1 < line.length; i++) {
            double x0 = line[i][0], z0 = line[i][1], x1 = line[i + 1][0], z1 = line[i + 1][1];
            double l = Math.hypot(x1 - x0, z1 - z0);
            int n = Math.max(1, (int) Math.ceil(l / maxLen));
            for (int k = 0; k < n; k++)
                s.seg(x0 + (x1 - x0) * k / n, z0 + (z1 - z0) * k / n, x0 + (x1 - x0) * (k + 1) / n, z0 + (z1 - z0) * (k + 1) / n);
        }
    }

    /** Länge einer Linie (m). */
    public static double length(double[][] line) {
        double s = 0;
        for (int i = 0; i + 1 < line.length; i++) s += Math.hypot(line[i + 1][0] - line[i][0], line[i + 1][1] - line[i][1]);
        return s;
    }
}
