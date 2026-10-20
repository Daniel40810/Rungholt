package com.dan.fracture.demo;

import com.dan.fracture.Fracture;
import com.dan.fracture.Material;
import com.dan.fracture.Piece;
import com.dan.fracture.Poly;
import com.dan.fracture.Shape;
import com.dan.fracture.Solid;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;

/**
 * Die drei Fälle der Vorführung (Werkbuch, Phase 6) mit eigener Zeichnung in Java2D, damit die Demo ohne Grafikkarte
 * läuft und Standbilder auch ohne Fenster entstehen: ein Pfeiler bricht unter einem fallenden Block, ein überhängender
 * Eisblock kippt ins Wasser, wenn die Schmelzkerbe ihn unterhöhlt, ein Fels rollt einen Hang hinab und zerschellt.
 * Gezeichnet wird nach dem Malerverfahren: alle Flächen nach Abstand sortiert, von hinten nach vorn.
 */
public final class FractureScene {
    public enum Case {
        PFEILER("Pfeiler unter Last", "Block fallen lassen"),
        UEBERHANG("Überhang kippt ins Wasser", "Unterspülung starten"),
        FELS("Fels rollt den Hang hinab", "Fels anstoßen");
        public final String title, action;
        Case(String t, String a) { title = t; action = a; }
    }

    public Fracture world;
    public Solid solid;
    public Case current = Case.PFEILER;
    /** Fallhöhe des Blocks (m), Tempo der Schmelzkerbe (m/s im Zeitraffer), Festigkeit in Prozent. */
    public double dropHeight = 4, meltSpeed = 0.6, strengthPct = 100;
    public boolean showStress, triggered;
    public double yaw = 35, pitch = 14, dist = 18;
    public double[] target = {0, 3, 0};
    public final Deque<String> log = new ArrayDeque<>();
    double edge, waterLevel = Double.NaN;
    Fracture.Ground groundFn;
    double[] groundBox = {-15, -15, 15, 15};
    private final List<double[]> rings = new ArrayList<>(), drops = new ArrayList<>();
    private final SplittableRandom rnd = new SplittableRandom(5);
    private double sim, lastHit = -1;

    public FractureScene() { load(Case.PFEILER); }

    // ------------------------------------------------------------------ Fälle

    public void load(Case c) {
        current = c;
        triggered = false;
        rings.clear();
        drops.clear();
        log.clear();
        sim = 0;
        lastHit = -1;
        Fracture f;
        switch (c) {
            case PFEILER -> {
                f = Fracture.ice();
                f.seed = 11;
                f.material.tensile *= strengthPct / 100;
                f.material.compressive *= strengthPct / 100;
                groundFn = (x, z) -> 0.0;
                groundBox = new double[]{-12, -12, 12, 12};
                waterLevel = Double.NaN;
                f.ground = groundFn;
                solid = f.addSolid(Shape.box(-0.6, 0, -0.6, 0.6, 6, 0.6), f.cellsFor(220));
                solid.anchorBelow(0, 2);
                target = new double[]{0, 3.5, 0};
                yaw = 35; pitch = 12; dist = 19;
            }
            case UEBERHANG -> {
                f = Fracture.glacier();
                f.seed = 21;
                f.material.tensile *= strengthPct / 100;
                f.material.compressive *= strengthPct / 100;
                groundFn = (x, z) -> x < -0.5 ? -6.0 : x > 0.5 ? -25.0 : -6.0 - 19.0 * (x + 0.5);
                groundBox = new double[]{-30, -22, 40, 22};
                waterLevel = 0;
                f.ground = groundFn;
                f.water = (x, z, t) -> 0.0;
                // eine Front: 16 m Eis auf einem Sockel 6 m unter Wasser, vorn über tiefem Wasser; dichter zur Front hin
                solid = f.addSolid(Shape.box(-16, -6, -5, 6, 10, 5), f.cellsFor(600), (x, y, z) -> 0.25 + 0.75 * smooth(-10, 2, x), null);
                // das Bett hält das Eis (angefroren, dreimal so fest wie das Eis selbst [G]); hinten geht der Gletscher weiter
                solid.anchorBelow(-6, 3);
                solid.support(q -> q[0] < -14.5);
                edge = 6;
                target = new double[]{-1, 0, 0};
                yaw = 34; pitch = 13; dist = 50;
            }
            default -> {
                f = Fracture.rock();
                f.seed = 31;
                // verwitterter Fels: Klüfte machen ihn im Großen viel schwächer als das Handstück [G]
                f.material.tensile = 4e5 * strengthPct / 100;
                f.material.compressive = 6e6 * strengthPct / 100;
                f.material.frictionAngle = 40;
                f.material.friction = 0.55;
                f.material.restitution = 0.25;
                groundFn = FractureScene::hill;
                groundBox = new double[]{-30, -16, 40, 16};
                waterLevel = Double.NaN;
                f.ground = groundFn;
                // ein Findling am Fuß des Hangs, der hält
                Material keep = f.material;
                f.material = Material.rock();
                solid = f.addSolid(Shape.box(20, hill(22, 0) - 0.5, -2.5, 24, hill(22, 0) + 3.2, 2.5), f.cellsFor(60));
                solid.anchorBelow(hill(22, 0) - 0.3, 3);
                f.material = keep;
                double y0 = hill(-16, 0);
                // ein Findling: Quader mit abgeschlagenen Ecken
                double r = 1.2, k = 1.55;
                Shape boulder = Shape.convex(new double[]{-r, -r * 0.85, -r, r, r * 0.85, r},
                        1, 1, 1, k, 1, 1, -1, k, 1, -1, 1, k, 1, -1, -1, k, -1, 1, 1, k, -1, 1, -1, k, -1, -1, 1, k, -1, -1, -1, k);
                Piece rock = f.addPiece(boulder, f.cellsFor(16));
                rock.place(-16, y0 + 1.3, 0);
                target = new double[]{8, hill(8, 0) + 1, 0};
                yaw = 18; pitch = 17; dist = 40;
            }
        }
        world = f;
        f.onBreak = (id, v, m) -> note(String.format(Locale.GERMAN, "%5.1f s  Stück %d frei: %.2f m³, %.1f t", world.time(), id, v, m / 1000));
        f.onCrack = (p, e, b) -> {
            if (b) note(String.format(Locale.GERMAN, "%5.1f s  Riss mit %d Flächen, %.0f kJ – bricht", world.time(), p.length / 3, e / 1000));
        };
        f.onImpact = (x, y, z, v, s, water) -> {
            if (water) {
                if (v > 0.05) splash(x, z, v, s);
            } else if (s > 4 && v > 0.5 && world.time() - lastHit > 0.5) {
                lastHit = world.time();
                note(String.format(Locale.GERMAN, "%5.1f s  schlägt auf mit %.1f m/s", world.time(), s));
            }
        };
        note(c.title + ": " + world.material);
    }

    /** Höhe der Unterspülung (m): das warme Wasser frisst die Front vom Bett bis knapp über die Wasserlinie. */
    static final double NOTCH_LOW = -6.5, NOTCH_HIGH = 1.2;

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    /** Hang: 24° Gefälle nach +x, unten flach, ein paar Buckel. */
    static double hill(double x, double z) {
        // 31° bis x = 12, dann ein weicher Übergang in die Ebene
        double t = x - 12, slope = t < -3 ? -0.6 * t : t > 3 ? 0 : 0.6 * (3 - t) * (3 - t) / 12;
        double base = slope + 0.35 * Math.sin(x * 0.7) * Math.cos(z * 0.5) + 0.5 * Math.exp(-((x + 4) * (x + 4) + (z - 1) * (z - 1)) / 3.0);
        return base + 0.016 * z * z;
    }

    void note(String s) {
        log.addFirst(s);
        while (log.size() > 7) log.removeLast();
    }

    /** Der Knopf: Block fallen lassen, Schmelzkerbe starten, Fels anstoßen. */
    public void trigger() {
        if (triggered && current != Case.PFEILER) return;
        triggered = true;
        switch (current) {
            case PFEILER -> {
                Material keep = world.material;
                world.material = Material.rock();
                Piece b = world.addPiece(Shape.box(-1.5, 0, -1.5, 1.5, 1.5, 1.5), 1);
                b.place(0.25, 6 + 0.75 + dropHeight, 0.15);
                world.material = keep;
                note(String.format(Locale.GERMAN, "%5.1f s  Block %.0f t aus %.1f m", world.time(), b.mass() / 1000, dropHeight));
            }
            case UEBERHANG -> note(String.format(Locale.GERMAN, "%5.1f s  Unterspülung frisst %.1f m/s (Zeitraffer)", world.time(), meltSpeed));
            default -> {
                for (Piece p : world.pieces()) { p.push(4, 0, 0); p.twirl(0, 0, -3); }
                note(String.format(Locale.GERMAN, "%5.1f s  Fels angestoßen", world.time()));
            }
        }
    }

    /** dt Sekunden weiter (in Schritten von 1/120 s). */
    public void step(double dt) {
        int n = Math.max(1, (int) Math.round(dt * 120));
        for (int k = 0; k < n; k++) {
            if (current == Case.UEBERHANG && triggered && edge > -12) {
                // die Schmelzkerbe an der Wasserlinie frisst sich in die Front: was in ihr liegt, schmilzt weg
                double e = edge - meltSpeed / 120;
                if (Math.floor(e * 2) != Math.floor(edge * 2)) {
                    final double ee = e;
                    solid.melt(c -> c[0] > ee && c[1] > NOTCH_LOW && c[1] < NOTCH_HIGH);
                }
                edge = e;
            }
            world.step(1.0 / 120);
            sim += 1.0 / 120;
        }
        // Gischt
        for (int i = drops.size() - 1; i >= 0; i--) {
            double[] d = drops.get(i);
            d[3] -= 9.81 * dt;
            d[0] += d[2] * dt; d[1] += d[3] * dt; d[5] += d[4] * dt;
            if (d[1] < waterLevel - 0.1) drops.remove(i);
        }
        rings.removeIf(r -> sim - r[2] > 6);
    }

    private void splash(double x, double z, double v, double s) {
        // ein Einschlag meldet sich je Schritt, solange er eintaucht: nahe, frische Ringe wachsen mit, statt neu zu entstehen
        double size = Math.min(6, Math.cbrt(v));
        boolean merged = false;
        for (double[] r : rings) {
            if (sim - r[2] < 0.5 && Math.hypot(r[0] - x, r[1] - z) < 4 + r[3]) { r[3] = Math.min(8, Math.cbrt(r[3] * r[3] * r[3] + v)); merged = true; break; }
        }
        if (!merged) rings.add(new double[]{x, z, sim, size});
        if (merged && rnd.nextDouble() > 0.3) return;
        int n = (int) Math.min(40, 4 + v * 2);
        for (int i = 0; i < n && drops.size() < 900; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, sp = (0.3 + rnd.nextDouble()) * Math.min(12, 2 + s * 0.6);
            drops.add(new double[]{x + Math.cos(a) * 0.5, waterLevel, Math.cos(a) * sp * 0.45, sp * (0.8 + rnd.nextDouble() * 0.6), Math.sin(a) * sp * 0.45, z + Math.sin(a) * 0.5});
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    private double[] eye, fw, rt, up;
    private double focal, cw, ch;
    static final double[] LIGHT = norm(new double[]{-0.45, 0.8, 0.35});

    private void camera(int w, int h) {
        double a = Math.toRadians(yaw), p = Math.toRadians(pitch);
        eye = new double[]{target[0] + dist * Math.cos(p) * Math.sin(a), target[1] + dist * Math.sin(p), target[2] + dist * Math.cos(p) * Math.cos(a)};
        fw = norm(new double[]{target[0] - eye[0], target[1] - eye[1], target[2] - eye[2]});
        rt = norm(new double[]{fw[1] * 0 - fw[2] * 1, fw[2] * 0 - fw[0] * 0, fw[0] * 1 - fw[1] * 0});
        up = new double[]{rt[1] * fw[2] - rt[2] * fw[1], rt[2] * fw[0] - rt[0] * fw[2], rt[0] * fw[1] - rt[1] * fw[0]};
        focal = h / 2.0 / Math.tan(Math.toRadians(50) / 2);
        cw = w / 2.0;
        ch = h / 2.0;
    }

    /** Bildpunkt {x, y, Tiefe} oder null hinter dem Auge. */
    private double[] project(double x, double y, double z) {
        double dx = x - eye[0], dy = y - eye[1], dz = z - eye[2];
        double cz = dx * fw[0] + dy * fw[1] + dz * fw[2];
        if (cz < 0.2) return null;
        double cx = dx * rt[0] + dy * rt[1] + dz * rt[2], cy = dx * up[0] + dy * up[1] + dz * up[2];
        return new double[]{cw + focal * cx / cz, ch - focal * cy / cz, cz};
    }

    private record Item(Path2D path, double depth, Color fill, Color edge) { }

    public void paint(Graphics2D g, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        camera(w, h);
        g.setPaint(new GradientPaint(0, 0, new Color(24, 44, 66), 0, h * 0.6f, new Color(122, 160, 186)));
        g.fillRect(0, 0, w, h);
        List<Item> items = new ArrayList<>();
        groundItems(items);
        Solid s = solid;
        // haltender Körper: sichtbar sind Randflächen und Flächen zu gelösten oder gebrochenen Nachbarn
        boolean[] seen = new boolean[s.cells()];
        double[] cellUtil = new double[s.cells()];
        for (int b = 0; b < s.bonds() + s.anchors(); b++) {
            int a = s.bondA(b), c = s.bondB(b);
            double u = s.utilization(b);
            cellUtil[a] = Math.max(cellUtil[a], u);
            if (c >= 0) cellUtil[c] = Math.max(cellUtil[c], u);
        }
        for (int i = 0; i < s.cells(); i++) {
            if (!s.attached(i)) continue;
            seen[i] = true;
            for (Poly.Face f : s.cell(i).faces) {
                boolean fresh = false;
                if (f.tag >= 0) {
                    if (s.attached(f.tag) && !brokenBetween(s, i, f.tag)) continue;
                    fresh = true;
                }
                Color base = showStress ? heat(cellUtil[i]) : color(s.material, fresh, f);
                face(items, f.p, f.nx, f.ny, f.nz, base, fresh && !showStress);
            }
        }
        // Stücke
        for (Piece p : world.pieces()) {
            for (int i = 0; i < p.cellCount(); i++) {
                Poly c = p.worldCell(i);
                for (Poly.Face f : c.faces) face(items, f.p, f.nx, f.ny, f.nz, color(p.material, f.tag >= 0, f), f.tag >= 0);
            }
        }
        waterItems(items);
        items.sort((a, b) -> Double.compare(b.depth, a.depth));
        g.setStroke(new BasicStroke(0.7f));
        for (Item it : items) {
            g.setColor(it.fill);
            g.fill(it.path);
            if (it.edge != null) { g.setColor(it.edge); g.draw(it.path); }
        }
        // Ringe und Gischt
        g.setStroke(new BasicStroke(1.4f));
        for (double[] r : rings) {
            double age = sim - r[2], rad = r[3] * (1.5 + 6 * age);
            int alpha = (int) (170 * Math.max(0, 1 - age / 6));
            g.setColor(new Color(235, 248, 252, alpha));
            Path2D path = new Path2D.Double();
            boolean first = true;
            for (int k = 0; k <= 36; k++) {
                double a = k * Math.PI * 2 / 36;
                double[] q = project(r[0] + Math.cos(a) * rad, waterLevel + 0.05, r[1] + Math.sin(a) * rad);
                if (q == null) { first = true; continue; }
                if (first) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
                first = false;
            }
            g.draw(path);
        }
        g.setColor(new Color(245, 252, 255, 210));
        for (double[] d : drops) {
            double[] q = project(d[0], d[1], d[5]);
            if (q == null) continue;
            double sz = Math.max(1.2, 30 / q[2]);
            g.fill(new java.awt.geom.Ellipse2D.Double(q[0] - sz / 2, q[1] - sz / 2, sz, sz));
        }
        // Text
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(11, h / 70)));
        int lh = g.getFontMetrics().getHeight(), y = lh + 4;
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(0, 0, w, lh * 3 + 8);
        g.setColor(new Color(236, 242, 246));
        g.drawString(current.title + String.format(Locale.GERMAN, " · %.1f s", world.time()) + (current == Case.UEBERHANG ? String.format(Locale.GERMAN, " · unterspült %.1f m", 6 - edge) : ""), 8, y);
        g.setColor(new Color(170, 190, 204));
        g.drawString(world.describe(), 8, y + lh);
        g.drawString(solid.describe(), 8, y + 2 * lh);
        int ly = h - 8;
        for (String l : log) {
            g.setColor(new Color(0, 0, 0, 110));
            g.fillRect(4, ly - lh + 3, g.getFontMetrics().stringWidth(l) + 8, lh);
            g.setColor(new Color(230, 238, 242));
            g.drawString(l, 8, ly);
            ly -= lh;
        }
        if (showStress) legend(g, w, lh);
    }

    private void legend(Graphics2D g, int w, int lh) {
        int x0 = w - 230, y0 = lh * 3 + 18;
        for (int k = 0; k < 200; k++) {
            g.setColor(heat(k / 200.0 * 1.2));
            g.fillRect(x0 + k, y0, 1, 10);
        }
        g.setColor(new Color(230, 238, 242));
        g.drawString("Auslastung 0 … 1 (bricht) …", x0, y0 + 10 + lh);
    }

    private static boolean brokenBetween(Solid s, int i, int j) {
        // die Fläche zwischen i und j suchen (wenige Flächen je Zelle: linear über alle Flächen ist zu teuer, daher über die Zellnummern)
        return s.brokenBetween(i, j);
    }

    private void face(List<Item> items, double[] p, double nx, double ny, double nz, Color base, boolean fresh) {
        int n = p.length / 3;
        double cx = 0, cy = 0, cz = 0;
        for (int k = 0; k < n; k++) { cx += p[k * 3]; cy += p[k * 3 + 1]; cz += p[k * 3 + 2]; }
        cx /= n; cy /= n; cz /= n;
        // Rückseiten weglassen
        if ((eye[0] - cx) * nx + (eye[1] - cy) * ny + (eye[2] - cz) * nz <= 0) return;
        Path2D path = new Path2D.Double();
        for (int k = 0; k < n; k++) {
            double[] q = project(p[k * 3], p[k * 3 + 1], p[k * 3 + 2]);
            if (q == null) return;
            if (k == 0) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
        }
        path.closePath();
        double d = Math.sqrt((cx - eye[0]) * (cx - eye[0]) + (cy - eye[1]) * (cy - eye[1]) + (cz - eye[2]) * (cz - eye[2]));
        double lam = Math.max(0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]);
        double sh = 0.42 + 0.58 * lam;
        boolean under = !Double.isNaN(waterLevel) && cy < waterLevel;
        Color c = shade(base, sh, under);
        Color e = new Color(Math.max(0, c.getRed() - 26), Math.max(0, c.getGreen() - 22), Math.max(0, c.getBlue() - 14), fresh ? 200 : 110);
        items.add(new Item(path, d, c, e));
    }

    private static Color shade(Color c, double k, boolean under) {
        double r = c.getRed() * k, g = c.getGreen() * k, b = c.getBlue() * k;
        if (under) { r = r * 0.45 + 20; g = g * 0.6 + 70; b = b * 0.65 + 80; }
        return new Color(clamp(r), clamp(g), clamp(b), c.getAlpha());
    }

    private static int clamp(double v) { return (int) Math.max(0, Math.min(255, v)); }

    private static Color color(Material m, boolean fresh, Poly.Face f) {
        String n = m.name;
        if (n.toLowerCase(Locale.ROOT).contains("eis")) return fresh ? new Color(96, 176, 222) : new Color(214, 236, 246);
        if (n.contains("Beton")) return fresh ? new Color(170, 168, 160) : new Color(196, 194, 188);
        return fresh ? new Color(176, 160, 140) : new Color(128, 116, 104);
    }

    /** Blau (ruhig) über Gelb nach Rot (bricht). */
    static Color heat(double u) {
        double t = Math.max(0, Math.min(1.2, u));
        if (t < 0.5) return mix(new Color(52, 96, 168), new Color(96, 190, 160), t / 0.5);
        if (t < 0.85) return mix(new Color(96, 190, 160), new Color(236, 206, 70), (t - 0.5) / 0.35);
        if (t < 1.0) return mix(new Color(236, 206, 70), new Color(232, 88, 52), (t - 0.85) / 0.15);
        return new Color(200, 40, 40);
    }

    private static Color mix(Color a, Color b, double t) {
        return new Color(clamp(a.getRed() + (b.getRed() - a.getRed()) * t), clamp(a.getGreen() + (b.getGreen() - a.getGreen()) * t), clamp(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    private void groundItems(List<Item> items) {
        int N = 36;
        double x0 = groundBox[0], z0 = groundBox[1], x1 = groundBox[2], z1 = groundBox[3];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                double ax = x0 + (x1 - x0) * i / N, bx = x0 + (x1 - x0) * (i + 1) / N;
                double az = z0 + (z1 - z0) * j / N, bz = z0 + (z1 - z0) * (j + 1) / N;
                double h00 = groundFn.heightAt(ax, az), h10 = groundFn.heightAt(bx, az), h11 = groundFn.heightAt(bx, bz), h01 = groundFn.heightAt(ax, bz);
                double[] p = {ax, h00, az, ax, h01, bz, bx, h11, bz, bx, h10, az};
                double ux = bx - ax, uy = h10 - h00, vz = bz - az, vy = h01 - h00;
                double[] n = norm(new double[]{-uy * vz, ux * vz, -vy * ux});
                double avg = (h00 + h10 + h11 + h01) / 4;
                boolean sand = !Double.isNaN(waterLevel) && avg < waterLevel;
                double slope = 1 - n[1];
                Color base = sand ? new Color(150, 140, 110) : slope > 0.25 ? new Color(120, 112, 100) : (((i + j) & 1) == 0 ? new Color(102, 124, 84) : new Color(98, 120, 80));
                face(items, p, n[0], n[1], n[2], base, false);
                Item last = items.isEmpty() ? null : items.get(items.size() - 1);
                if (last != null && last.edge != null) items.set(items.size() - 1, new Item(last.path, last.depth + 0.5, last.fill, null));
            }
    }

    private void waterItems(List<Item> items) {
        if (Double.isNaN(waterLevel)) return;
        int N = 24;
        double x0 = groundBox[0], z0 = groundBox[1], x1 = groundBox[2], z1 = groundBox[3];
        for (int i = 0; i < N; i++)
            for (int j = 0; j < N; j++) {
                double ax = x0 + (x1 - x0) * i / N, bx = x0 + (x1 - x0) * (i + 1) / N;
                double az = z0 + (z1 - z0) * j / N, bz = z0 + (z1 - z0) * (j + 1) / N;
                if (groundFn.heightAt((ax + bx) / 2, (az + bz) / 2) > waterLevel) continue;
                double y = waterLevel;
                double[] p = {ax, y, az, ax, y, bz, bx, y, bz, bx, y, az};
                Path2D path = new Path2D.Double();
                boolean ok = true;
                for (int k = 0; k < 4; k++) {
                    double[] q = project(p[k * 3], p[k * 3 + 1], p[k * 3 + 2]);
                    if (q == null) { ok = false; break; }
                    if (k == 0) path.moveTo(q[0], q[1]); else path.lineTo(q[0], q[1]);
                }
                if (!ok) continue;
                path.closePath();
                double cx = (ax + bx) / 2, cz = (az + bz) / 2;
                double d = Math.sqrt((cx - eye[0]) * (cx - eye[0]) + (y - eye[1]) * (y - eye[1]) + (cz - eye[2]) * (cz - eye[2]));
                double fres = Math.max(0, Math.min(1, 1 - Math.abs(eye[1] - y) / d));
                Color c = new Color(clamp(40 + 80 * fres), clamp(150 + 50 * fres), clamp(160 + 50 * fres), 150);
                items.add(new Item(path, d, c, null));
            }
    }

    static double[] norm(double[] v) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return l > 0 ? new double[]{v[0] / l, v[1] / l, v[2] / l} : v;
    }

    public String status() {
        return world.describe() + " · " + solid.describe();
    }
}
