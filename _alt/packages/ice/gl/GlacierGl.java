package com.dan.ice.gl;

import com.dan.ice.Glacier;
import com.dan.ice.GlacierTiles;
import com.dan.ice.GlacierWall;
import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.jogamp.opengl.GL4;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Ein {@link Glacier} auf der Grafikkarte: die Kacheln der Oberfläche ({@link GlacierTiles}) in ihrer Stufe nach
 * dem Abstand zum Auge, dazu die Wand der Front ({@link GlacierWall}). Netze baut ein eigener Faden; je Bild werden
 * höchstens {@link #uploadsPerFrame} fertige Netze hochgeladen, bis dahin bleibt die alte Stufe stehen. Gezeichnet wird
 * mit {@link IceGl} – {@link #instances} liefert die Körper, die der Aufrufer zusammen mit Eisbergen an
 * {@link IceGl#draw} gibt.
 */
public final class GlacierGl {
    public final Glacier glacier;
    public final GlacierTiles tiles;
    /** Material der Oberfläche und der Wand. */
    public volatile Ice surfaceIce = Ice.tongue(), wallIce = Ice.front();
    /** Grunddicke (m) der Oberfläche und der Wand, wo keine Rückseite zu sehen ist. */
    public volatile double surfaceThickness = 40, wallThickness = 25;
    /** Reichweiten der Stufen (m, Stufe 0 bis 3); Faktor für die Qualität. */
    public volatile double[] range = GlacierTiles.RANGE_MEDIUM.clone();
    public volatile int uploadsPerFrame = 4;

    private final IceGl ice;
    private final Map<Long, Slot> slots = new HashMap<>();
    private final ConcurrentLinkedQueue<Built> done = new ConcurrentLinkedQueue<>();
    private final ExecutorService worker;
    private final AtomicInteger pending = new AtomicInteger();
    private IceGl.Body wall;
    private volatile IceMesh wallMesh;
    private volatile int generation;
    private double builtAdvance = Double.NaN;
    private volatile boolean wallBuilding;
    private int drawnTiles, drawnTris;
    private final int[] perLevel = new int[GlacierTiles.SPACING.length];

    private static final class Slot {
        GlacierTiles.Tile tile;
        int level = -1, wanted = -1, gen = -1, wantedGen = -1;
        IceGl.Body body;
    }

    private record Built(long key, GlacierTiles.Tile tile, int level, int gen, IceMesh mesh) { }

    public GlacierGl(IceGl ice, Glacier glacier) {
        this.ice = ice;
        this.glacier = glacier;
        this.tiles = new GlacierTiles(glacier);
        worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Gletscher-Netze");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 1);
            return t;
        });
    }

    /** Baut alle Kacheln in der gröbsten Stufe und die Wand sofort (im Aufruf), damit das erste Bild kein Loch hat. */
    public void prime(GL4 gl) {
        int coarse = GlacierTiles.SPACING.length - 1;
        for (GlacierTiles.Tile t : tiles.tiles) {
            Slot s = slots.computeIfAbsent(t.key(), k -> new Slot());
            s.tile = t;
            s.body = upload(gl, tiles.build(t, coarse));
            s.level = s.wanted = coarse;
            s.gen = s.wantedGen = generation;
        }
        wallMesh = GlacierWall.build(glacier);
        builtAdvance = glacier.advance();
    }

    /**
     * Setzt den Vorstoß (m). Kacheln und Wand werden nebenher neu gebaut; bis sie fertig sind, steht die alte Front.
     * Kleine Änderungen (unter 1,5 m) werden gesammelt.
     */
    public void setAdvance(double a) {
        if (!Double.isNaN(builtAdvance) && Math.abs(a - builtAdvance) < 1.5) return;
        if (wallBuilding) return;
        builtAdvance = a;
        wallBuilding = true;
        pending.incrementAndGet();
        worker.submit(() -> {
            try {
                glacier.setAdvance(a);
                tiles.rescan();
                wallMesh = GlacierWall.build(glacier);
                generation++;
            } finally {
                wallBuilding = false;
                pending.decrementAndGet();
            }
        });
    }

    /** Alle Kacheln und die Wand neu bauen (nach einer Änderung am Gletscher, etwa {@code crevasseScale}). */
    public void invalidate() {
        pending.incrementAndGet();
        worker.submit(() -> {
            try {
                wallMesh = GlacierWall.build(glacier);
                generation++;
            } finally {
                pending.decrementAndGet();
            }
        });
    }

    /** Je Bild: Stufen wählen, Bauaufträge vergeben, Fertiges hochladen; die Körper, die zu zeichnen sind. */
    public List<IceGl.Instance> instances(GL4 gl, double[] eye, float[] viewProj) {
        // fertige Netze hochladen
        for (int u = 0; u < uploadsPerFrame; u++) {
            Built b = done.poll();
            if (b == null) break;
            Slot s = slots.computeIfAbsent(b.key, k -> new Slot());
            IceGl.Body old = s.body;
            s.body = b.mesh.idx.length > 0 ? upload(gl, b.mesh) : null;
            s.tile = b.tile;
            s.level = b.level;
            s.gen = b.gen;
            if (old != null) ice.release(gl, old);
        }
        IceMesh wm = wallMesh;
        if (wm != null) {
            IceGl.Body old = wall;
            wall = upload(gl, wm);
            wallMesh = null;
            if (old != null) ice.release(gl, old);
        }
        List<IceGl.Instance> out = new ArrayList<>();
        Map<IceGl.Instance, Double> dist = new java.util.IdentityHashMap<>();
        List<GlacierTiles.Tile> list = tiles.tiles;
        java.util.Set<Long> live = new java.util.HashSet<>();
        List<Object[]> wants = new ArrayList<>();
        java.util.Arrays.fill(perLevel, 0);
        int tris = 0;
        int gen = generation;
        double[] r = range;
        for (GlacierTiles.Tile t : list) {
            long key = t.key();
            live.add(key);
            Slot s = slots.computeIfAbsent(key, k -> new Slot());
            if (s.tile == null) s.tile = t;
            int lv = GlacierTiles.level(t, eye, r);
            boolean vis = GlacierTiles.visible(t, viewProj);
            boolean needs = s.level != lv || s.gen != gen, asked = s.wanted == lv && s.wantedGen == gen;
            if (needs && !asked && (vis || s.body == null || s.gen != gen)) wants.add(new Object[]{s, t, lv, key});
            if (vis && s.body != null) {
                IceGl.Instance in = new IceGl.Instance(s.body, surfaceIce).open(surfaceThickness);
                in.cut = true;
                double cx = t.x0 + GlacierTiles.TILE / 2 - eye[0], cz = t.z0 + GlacierTiles.TILE / 2 - eye[2], cy = (t.minY + t.maxY) / 2 - eye[1];
                dist.put(in, cx * cx + cy * cy + cz * cz);
                out.add(in);
                perLevel[s.level]++;
                tris += s.body.triangles();
            }
        }
        // Aufträge nach Nähe: zuerst, was nah ist und fein werden soll
        wants.sort(Comparator.comparingInt(o -> (Integer) o[2]));
        for (Object[] w : wants) {
            if (pending.get() > 24) break;
            Slot s = (Slot) w[0];
            GlacierTiles.Tile t = (GlacierTiles.Tile) w[1];
            int lv = (Integer) w[2];
            long key = (Long) w[3];
            s.wanted = lv;
            s.wantedGen = gen;
            final int g = gen;
            pending.incrementAndGet();
            worker.submit(() -> {
                try {
                    done.add(new Built(key, t, lv, g, tiles.build(t, lv)));
                } catch (RuntimeException e) {
                    System.err.println("ice: Kachel " + t.ix + "/" + t.iz + ": " + e);
                } finally {
                    pending.decrementAndGet();
                }
            });
        }
        // Kacheln, die nach einem Rückzug kein Eis mehr haben
        slots.entrySet().removeIf(e -> {
            if (live.contains(e.getKey())) return false;
            if (e.getValue().body != null) ice.release(gl, e.getValue().body);
            return true;
        });
        // Phase 6 (Befund Status_Phase5a: Eis 5,4 ms an der Front): von vorn nach hinten zeichnen, damit der Tiefentest
        // die Spaltenwände dahinter verwirft, bevor der teure Eis-Shader für sie läuft; die Wand zuerst
        out.sort(Comparator.comparingDouble(dist::get));
        if (wall != null) {
            IceGl.Instance wi = new IceGl.Instance(wall, wallIce).open(wallThickness);
            wi.cut = true;
            out.add(0, wi);
            tris += wall.triangles();
        }
        drawnTiles = out.size() - (wall != null ? 1 : 0);
        drawnTris = tris;
        return out;
    }

    private IceGl.Body upload(GL4 gl, IceMesh m) {
        IceGl.Body b = ice.upload(gl, m);
        b.mesh = null;
        return b;
    }

    /** „Gletscher: 96 Kacheln (2 m: 6, 4 m: 18 …), 410 000 Dreiecke, Wand 174 000, 3 im Bau“ */
    public String info() {
        StringBuilder sb = new StringBuilder();
        for (int l = 0; l < perLevel.length; l++) if (perLevel[l] > 0) sb.append(sb.length() > 0 ? ", " : "").append((int) GlacierTiles.SPACING[l]).append(" m: ").append(perLevel[l]);
        return String.format(Locale.GERMAN, "Gletscher: %d Kacheln (%s), %,d Dreiecke mit Wand, %d im Bau", drawnTiles, sb, drawnTris, pending.get());
    }

    /** Ob noch Netze im Bau sind (für Standbilder: warten, bis alles fein ist). */
    public boolean busy() { return pending.get() > 0 || !done.isEmpty(); }

    public void dispose(GL4 gl) {
        worker.shutdownNow();
        for (Slot s : slots.values()) if (s.body != null) ice.release(gl, s.body);
        slots.clear();
        if (wall != null) ice.release(gl, wall);
        wall = null;
    }
}
