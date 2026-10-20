package com.dan.fracture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bruch und Starrkörper (Werkbuch Glacier, Abschnitt 4; Phase 6). Kennt kein Eis, kein Wasser und keinen Fels: Dichte,
 * Festigkeit, Boden und die Höhe des Wassers kommen von außen. Darum taugt es für eine Gletscherfront, einen Felssturz,
 * eine Staumauer, einen Eisgang.
 * <ul>
 * <li><b>Zellen:</b> {@link #addSolid} zerlegt einen {@link Shape} in Zellen (3D-Voronoi, dichter wo
 * {@link Density} es will); jede gemeinsame Fläche trägt eine Festigkeit ({@link Material}, örtlich {@link Strength},
 * dazu Streuung).</li>
 * <li><b>Spannung:</b> aus Eigengewicht, Auftrieb und Lasten im Netz starrer Zellen ({@link Stress}); je Fläche Zug,
 * Druck und Schub nach Mohr-Coulomb. Für eine Gletscherfront ist das genau der Balken der Handrechnung: Überhang,
 * Eigengewicht und Auftrieb machen die Zugspannung oben an der Wurzel.</li>
 * <li><b>Riss:</b> liegt eine Fläche über ihrer Festigkeit, bricht sie, dann die nächste (je {@link #crackStep});
 * {@code onCrack} meldet den Weg, solange er wächst. Was über keine heile Fläche mehr mit einem Auflager verbunden
 * ist, löst sich als {@link Piece} ({@code onBreak}).</li>
 * <li><b>Starrkörper:</b> Schwerkraft, Auftrieb aus dem untergetauchten Volumen jeder Zelle, Widerstand im Wasser, Stöße
 * mit Boden, haltendem Körper und anderen Stücken nach dem Impulsverfahren mit Warmstart und Reibung, Schlaf. Schlägt
 * ein Stück hart auf, rechnet es die Spannung im Stück aus dem Stoß und zerbricht womöglich weiter.</li>
 * </ul>
 * Fester Schritt: {@link #step(double)} mit 1/120 s. Gleicher {@link #seed}, gleiche Zellen, gleicher Riss, gleicher
 * Fall.
 */
public final class Fracture {
    public enum Quality { LOW, MEDIUM, HIGH, ULTRA }

    /** Stand des Packages: 1 = Hülle; 6 = Engine. */
    public static final int STAGE = 6;

    /** Ein Riss läuft: Weg als Punkte {x, y, z, …} (m), freigesetzte Energie (J), ob damit ein Stück frei ist. */
    @FunctionalInterface public interface Crack { void crack(double[] path, double energyJ, boolean broken); }
    /** Ein Stück ist frei: Kennung, Volumen (m³), Masse (kg). */
    @FunctionalInterface public interface Break { void broke(int piece, double volumeM3, double massKg); }
    /** Ein Stück schlägt auf: Ort (m), verdrängtes Volumen in diesem Schritt (m³), Tempo (m/s), ob ins Wasser. */
    @FunctionalInterface public interface Impact { void impact(double x, double y, double z, double volumeM3, double speed, boolean water); }
    /** Die Höhe des Wassers am Ort (m) zur Laufzeit t (s); kommt von außen, ohne Wasser NaN. */
    @FunctionalInterface public interface Water { double heightAt(double x, double z, double t); }
    /** Die Höhe des Bodens am Ort (m); NaN, wo kein Boden ist. */
    @FunctionalInterface public interface Ground { double heightAt(double x, double z); }
    /**
     * Seit Glacier Phase 13: Strömung des Wassers an (x, z) in m/s nach out[0] (x) und out[1] (z); false, wo das Wasser
     * steht. Der Widerstand rechnet dann gegen das strömende Wasser: ein Fluss trägt Stücke mit (eine Rinne, ein Dammbruch).
     */
    @FunctionalInterface public interface Current { boolean at(double x, double z, double[] out); }
    /** Dichte der Zellen am Ort, relativ 0..1 (1 = am feinsten). */
    @FunctionalInterface public interface Density { double at(double x, double y, double z); }
    /** Festigkeit am Ort als Faktor (1 = Stoff, 0,3 = an einer Spalte geschwächt). */
    @FunctionalInterface public interface Strength { double at(double x, double y, double z); }
    /** Ein Stück ist auf dem Wasser zur Ruhe gekommen und wird übergeben (an buoyancy). */
    @FunctionalInterface public interface Settle { void settled(Piece p); }

    public long seed = 1;
    public Quality quality = Quality.MEDIUM;
    /** Der Stoff neuer Körper und Stücke (wird kopiert). */
    public Material material = Material.ice();
    /** Erdbeschleunigung (m/s²) und Dichte des Wassers (kg/m³; Gletschersee mit Mehl rund 1 000). */
    public double gravity = 9.81, waterDensity = 1000;
    /** Widerstandsbeiwert im Wasser und Drehdämpfung im Wasser (1/s). */
    public double drag = 1.0, spinDrag = 0.6;
    /** Höchstzahl wacher Stücke und Ruhezeit auf dem Wasser bis zur Übergabe (s) (Werkbuch, Abschnitt 10). */
    public int maxAwake = 1000;
    public double sleepAfter = 30;
    /** Abstand der Spannungsprüfung ohne Riss (s) und zwischen zwei Rissschritten (s). */
    public double checkInterval = 1.0, crackStep = 0.08;
    /** Höchstzahl Flächen je Rissschritt. */
    public int maxBreaksPerStep = 6;
    /** Stoßdauer (s) für die Spannung beim Aufschlag und die Geschwindigkeitsänderung, ab der sie gerechnet wird (m/s). */
    public double contactTime = 0.03, shatterDv = 3.0;
    public boolean shatter = true;
    /** Tempo, ab dem ein Aufschlag auf festen Grund gemeldet wird (m/s). */
    public double impactSpeed = 1.5;
    /**
     * Die Spannung in einem eigenen Faden rechnen (Phase 7, für die App): {@link #step} wartet dann nicht auf das
     * Gleichungssystem, die Stücke fallen weiter, und der Riss geht einen Schritt weiter, sobald die Rechnung fertig ist.
     * Aus, wo es auf Wiederholbarkeit ankommt (Prüfungen, Bildfolgen).
     */
    public volatile boolean asyncSolve;
    /**
     * Grenzen für die App (Phase 7): ab so vielen Stücken zerschellt keins mehr, und Stücke unter so viel Volumen (m³)
     * verschwinden, wenn sie 4 s im Wasser sind (0: nie).
     */
    public int maxPieces = Integer.MAX_VALUE;
    /**
     * Seit Glacier Phase 16: höchstens so viele Stücke zugleich (0 = keine Grenze). Darüber gehen erst ruhende, dann die
     * kleinsten Stücke im Wasser (wie bei {@link #dissolveBelow}); nie eines, das jünger als 2 s ist. Zählt in
     * {@link #budgetRemoved()}.
     */
    public int budget = 0;
    private long budgetRemoved;
    /** Wie viele Stücke die Obergrenze {@link #budget} bisher genommen hat. */
    public long budgetRemoved() { return budgetRemoved; }
    public double dissolveBelow = 0;
    /**
     * Glacier Phase 16 (Dauerlauf): höchstens so viele Berührungen je Schritt. Darüber fallen weitere Zellpaare in diesem
     * Schritt weg (die Stücke sinken einen Schritt lang etwas ein). Ohne Grenze wuchs der Vorrat, wenn ein zweiter Damm
     * auf den Trümmern des ersten einstürzte, auf Millionen Berührungen und blieb so groß.
     */
    public int maxContacts = 40000;
    /** Seit Glacier Phase 10: Stücke, die so lange (s) trocken schlafen, sind weg (0: bleiben); sie liegen auf dem Eis und gehen in ihm auf. */
    public double forgetDryAfter = 0;

    public Crack onCrack;
    public Break onBreak;
    public Impact onImpact;
    public Water water;
    public Ground ground;
    /** Strömung (seit Glacier Phase 13); null: stehendes Wasser. */
    public Current current;
    public Settle onSettle;

    final List<Solid> solids = new ArrayList<>();
    final List<Piece> pieces = new ArrayList<>();
    double time;
    long steps;
    private int nextPiece = 1;
    private final java.util.concurrent.atomic.AtomicInteger nextSolid = new java.util.concurrent.atomic.AtomicInteger(1);
    boolean staticDirty = true;
    private final Contacts contacts = new Contacts(this);
    private double stepMs, solveMs;
    private int shatters, breaks;

    // ------------------------------------------------------------------ Presets
    /** Eis im Labor: 917 kg/m³, Zug 1 MPa, Druck 5 MPa (in den Spannen 0,5–1,8 und 2,4–5,9 MPa). */
    public static Fracture ice() { return new Fracture(); }
    /** Gletschereis im Großen [G]: 900 kg/m³, Zug 400 kPa, Druck 2,5 MPa (seit Phase 7). */
    public static Fracture glacier() { Fracture f = new Fracture(); f.material = Material.glacierIce(); return f; }
    /** Fels (Granit) für einen Felssturz. */
    public static Fracture rock() { Fracture f = new Fracture(); f.material = Material.rock(); return f; }
    /** Beton einer Staumauer. */
    public static Fracture concrete() { Fracture f = new Fracture(); f.material = Material.concrete(); return f; }
    /** Seit Rungholt Phase 12: ein Deich aus Klei ({@link Material#klei}). */
    public static Fracture klei() { Fracture f = new Fracture(); f.material = Material.klei(); return f; }

    /** Fallzeit aus der Höhe h (m) ohne Luft: √(2h/g) (Handrechnung für die Prüfung). */
    public double freeFall(double h) { return Math.sqrt(2 * Math.max(0, h) / gravity); }

    /** Zellen je Körper für die Qualität (Ausgangswert 400 auf „Mittel“). */
    public int cellsFor(int medium) {
        return Math.max(8, (int) Math.round(medium * switch (quality) { case LOW -> 0.4; case MEDIUM -> 1.0; case HIGH -> 2.0; case ULTRA -> 4.0; }));
    }

    int solverIterations() { return switch (quality) { case LOW -> 6; case MEDIUM -> 10; case HIGH -> 16; case ULTRA -> 24; }; }

    // ------------------------------------------------------------------ Aufbau

    /** Zerlegt den Umriss in rund {@code cells} Zellen, gleichmäßig, Stoff {@link #material}. */
    public Solid addSolid(Shape shape, int cells) { return addSolid(shape, cells, null, null); }

    /** Zerlegt den Umriss in rund {@code cells} Zellen mit Dichte und örtlicher Festigkeit (beide dürfen null sein). */
    public Solid addSolid(Shape shape, int cells, Density density, Strength strength) {
        return add(prepare(shape, cells, density, strength));
    }

    /**
     * Zerlegt den Umriss, ohne den Körper schon in die Welt zu setzen (Phase 7): das dauert bei einigen hundert Zellen
     * Zehntelsekunden und darf darum in einem anderen Faden laufen als {@link #step}. Danach {@link #add}.
     */
    public Solid prepare(Shape shape, int cells, Density density, Strength strength) {
        int id = nextSolid.getAndIncrement();
        Voronoi.Result r = Voronoi.build(shape, cells, density, seed * 1_000_003L + id);
        return new Solid(this, id, r, material.copy(), strength, seed + id);
    }

    /** Einen vorbereiteten Körper in die Welt setzen. */
    public synchronized Solid add(Solid s) {
        if (s.world != this) throw new IllegalArgumentException("fracture: Körper aus einer anderen Welt");
        solids.add(s);
        staticDirty = true;
        publish();
        return s;
    }

    /** Einen Körper aus der Welt nehmen (seine Stücke bleiben). */
    public synchronized void remove(Solid s) {
        if (solids.remove(s)) staticDirty = true;
        forgetRest(s);
        publish();
    }

    /**
     * Glacier Phase 16 (Dauerlauf): schlafende Stücke merken sich, worauf sie liegen ({@code Piece.rest}). Geht der Körper,
     * vergessen sie ihn und wachen auf (sie fallen); sonst hielte jedes Stück, das auf einem alten Damm schläft, den ganzen
     * Körper mit allen Zellen im Speicher.
     */
    private void forgetRest(Solid s) {
        for (Piece p : pieces) {
            if (p.rest.isEmpty()) continue;
            if (p.rest.removeIf(r -> r[0] == s) && p.asleep) p.wake();
        }
        // ohne Stücke baut stepPieces die Liste der Zellen nicht neu; dann hielte sie den alten Körper fest
        if (pieces.isEmpty()) { contacts.rebuildStatic(); staticDirty = false; }
    }

    /** Lage eines Stücks zu einem Zeitpunkt: Drehung zeilenweise und Lage. */
    public record Pose(Piece piece, double[] rotation, double[] position) { }

    /**
     * Die Lagen aller Stücke nach dem letzten Schritt (für das Zeichnen in einem anderen Faden): ohne Sperre, damit das
     * Bild nie auf einen Schritt der Physik wartet.
     */
    public List<Pose> poses() { return posesNow; }

    /** Ein freies Stück aus einem Umriss: in {@code cells} Zellen zerlegt, die zusammenhalten, bis ein Stoß sie trennt. */
    public synchronized Piece addPiece(Shape shape, int cells) {
        Solid s = new Solid(this, -1, Voronoi.build(shape, Math.max(1, cells), null, seed * 7_000_003L + nextPiece), material.copy(), null, seed + nextPiece);
        int[] all = new int[s.n];
        for (int i = 0; i < s.n; i++) all[i] = i;
        Piece p = makePiece(s, all, -1);
        pieces.add(p);
        publish();
        return p;
    }

    public synchronized void remove(Piece p) { pieces.remove(p); publish(); }
    public synchronized void clear() { solids.clear(); pieces.clear(); staticDirty = true; time = 0; publish(); }

    public List<Solid> solids() { return Collections.unmodifiableList(solids); }
    /** Die Stücke (Kopie der Liste). */
    public synchronized List<Piece> pieces() { return new ArrayList<>(pieces); }
    public double time() { return time; }
    public long steps() { return steps; }

    // ------------------------------------------------------------------ Schritt

    /** Ein fester Schritt der Länge dt (s): Spannung prüfen, wo es Zeit ist; Stücke bewegen. */
    public synchronized void step(double dt) {
        long t0 = System.nanoTime();
        time += dt;
        steps++;
        // Spannung und Riss
        for (Solid s : solids) {
            s.timer -= dt;
            double u;
            if (s.job != null) {
                // die Rechnung läuft in ihrem Faden; erst wenn sie fertig ist, wird ausgewertet
                if (!s.job.isDone()) continue;
                s.job = null;
                u = s.finish();
                solveMs = solveMs * 0.8 + s.lastSolveNs / 1e6 * 0.2;
            } else {
                if (s.timer > 0) continue;
                long a = System.nanoTime();
                detachAll(s, false);
                if (s.attachedCount() == 0) { s.timer = checkInterval; continue; }
                if (asyncSolve) {
                    s.assemble();
                    s.job = solver().submit(s::crunch);
                    continue;
                }
                u = s.solve();
                solveMs = solveMs * 0.8 + (System.nanoTime() - a) / 1e6 * 0.2;
            }
            if (u > 1) {
                double[] e = new double[1];
                // je stärker überlastet, desto mehr Flächen auf einmal (ein Stoß wartet nicht auf den nächsten Rissschritt)
                int k = s.breakOverloaded(maxBreaksPerStep * (int) Math.min(20, Math.ceil(u * u)), e);
                breaks += k;
                s.crackEnergy += e[0];
                s.cracking = true;
                boolean freed = detachAll(s, true);
                if (onCrack != null) onCrack.crack(path(s), s.crackEnergy, freed);
                s.timer = crackStep;
            } else {
                if (s.cracking) { s.crackPath.clear(); s.crackEnergy = 0; }
                s.cracking = false;
                s.timer = checkInterval;
            }
        }
        // Stücke
        stepPieces(dt);
        for (Solid s : solids) {
            s.settleContacts(dt, contactTime, gravity);
            // neu gerechnet wird höchstens alle crackStep Sekunden
            if (s.dirty) s.timer = Math.min(s.timer, crackStep);
        }
        stepMs = stepMs * 0.95 + (System.nanoTime() - t0) / 1e6 * 0.05;
        publish();
    }

    /** Stand für andere Fäden ohne Sperre: Lagen, Zahl der Körper, Stücke und wachen Stücke. */
    private void publish() {
        List<Pose> out = new ArrayList<>(pieces.size());
        int aw = 0;
        for (Piece p : pieces) {
            out.add(new Pose(p, p.R.clone(), p.p.clone()));
            if (!p.asleep) aw++;
        }
        posesNow = Collections.unmodifiableList(out);
        piecesNow = pieces.size();
        awakeNow = aw;
        solidsNow = solids.size();
    }

    private volatile List<Pose> posesNow = List.of();
    private volatile int piecesNow, awakeNow, solidsNow;
    private static java.util.concurrent.ExecutorService solverPool;

    /** Ein Faden für die Spannung, gemeinsam für alle Welten (die Rechnung eines Körpers läuft dort nacheinander). */
    private static synchronized java.util.concurrent.ExecutorService solver() {
        if (solverPool == null) solverPool = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "fracture-Spannung");
            t.setDaemon(true);
            return t;
        });
        return solverPool;
    }

    /** Zahl der Stücke, der wachen Stücke und der Körper nach dem letzten Schritt (ohne Sperre, für die Anzeige). */
    public int pieceCount() { return piecesNow; }
    public int awakeCount() { return awakeNow; }
    public int solidCount() { return solidsNow; }

    private static double[] path(Solid s) {
        double[] p = new double[s.crackPath.size() * 3];
        for (int k = 0; k < s.crackPath.size(); k++) System.arraycopy(s.crackPath.get(k), 0, p, k * 3, 3);
        return p;
    }

    /** Löst, was nicht mehr hält; gibt zurück, ob ein Stück frei wurde. */
    private boolean detachAll(Solid s, boolean announce) {
        List<int[]> groups = s.detach();
        for (int[] g : groups) {
            Piece p = makePiece(s, g, s.id);
            pieces.add(p);
            if (onBreak != null) onBreak.broke(p.id, p.volume, p.mass);
        }
        if (!groups.isEmpty()) staticDirty = true;
        return !groups.isEmpty();
    }

    private Piece makePiece(Solid s, int[] g, int source) {
        List<Poly> polys = new ArrayList<>();
        List<double[]> seeds = new ArrayList<>();
        Map<Integer, Integer> local = new HashMap<>();
        for (int i : g) {
            local.put(i, polys.size());
            polys.add(s.poly[i]);
            seeds.add(s.seedOf(i));
        }
        List<int[]> bonds = new ArrayList<>();
        List<double[]> data = new ArrayList<>();
        for (int i : g)
            for (int[] e : s.adj[i]) {
                int b = e[0], j = e[1];
                if (j <= i || s.broken[b] || !local.containsKey(j)) continue;
                bonds.add(new int[]{local.get(s.bi[b]), local.get(s.bj[b])});
                data.add(new double[]{s.bc[b * 3], s.bc[b * 3 + 1], s.bc[b * 3 + 2], s.bn[b * 3], s.bn[b * 3 + 1], s.bn[b * 3 + 2],
                        s.bd[b], s.ba[b], s.ft[b], s.fc[b], s.coh[b]});
            }
        return new Piece(nextPiece++, source, s.material, polys, seeds, bonds.toArray(new int[0][]), data.toArray(new double[0][]), g.clone());
    }

    private void stepPieces(double dt) {
        if (pieces.isEmpty()) return;
        if (staticDirty) { contacts.rebuildStatic(); staticDirty = false; }
        // schlafende Stücke liegen mit ihrem Gewicht weiter auf; wache tragen es über ihre Berührungen
        for (Piece p : pieces) {
            if (p.asleep == p.restApplied) continue;
            double sign = p.asleep ? 1 : -1;
            for (Object[] r : p.rest) ((Solid) r[0]).restLoad((Integer) r[1], (double[]) r[2], sign);
            p.restApplied = p.asleep;
            if (!p.asleep) p.rest.clear();
        }
        List<Piece> awake = new ArrayList<>();
        for (Piece p : pieces) if (!p.asleep) awake.add(p);
        if (awake.size() > maxAwake) {
            awake.sort((a, b) -> Double.compare(a.kinetic() / a.mass, b.kinetic() / b.mass));
            for (int k = 0; k < awake.size() - maxAwake; k++) { Piece p = awake.get(k); p.asleep = true; zero(p); }
            awake = awake.subList(awake.size() - maxAwake, awake.size());
            awake = new ArrayList<>(awake);
            awake.sort((a, b) -> Integer.compare(a.id, b.id));
        }
        // Kräfte: Schwerkraft, Auftrieb, Widerstand
        long tp0 = System.nanoTime();
        double[] tmp = new double[3], cur = new double[2];
        for (Piece p : awake) {
            p.age += dt;
            p.impactCool -= dt;
            p.refresh(steps);
            double fx = 0, fy = -p.mass * gravity, fz = 0, tx = 0, ty = 0, tz = 0;
            double sub = 0, sx = 0, sy = 0, sz = 0;
            double hp = water == null ? Double.NaN : water.heightAt(p.p[0], p.p[2], time);
            boolean nearWater = water != null && !(p.p[1] - p.radius > hp + 1);
            if (nearWater) {
                for (int i = 0; i < p.cells.length; i++) {
                    double cx = p.wcen[i * 3], cy = p.wcen[i * 3 + 1], cz = p.wcen[i * 3 + 2];
                    double h = water.heightAt(cx, cz, time);
                    if (Double.isNaN(h) || cy - p.crad[i] > h) continue;
                    double V, bx, by, bz;
                    if (cy + p.crad[i] < h) {
                        V = p.cvol[i]; bx = cx; by = cy; bz = cz;
                    } else {
                        // Ebene y = h im eigenen Achsenkreuz: (Rᵀ e_y) · x ≤ h − p_y
                        Poly c = p.cells[i].copy();
                        c.clip(p.R[3], p.R[4], p.R[5], h - p.p[1], -2);
                        if (c.isEmpty()) continue;
                        double[] ms = c.mass();
                        V = ms[0];
                        double[] wc = p.toWorld(ms[1], ms[2], ms[3]);
                        bx = wc[0]; by = wc[1]; bz = wc[2];
                    }
                    if (V <= 0) continue;
                    sub += V; sx += V * bx; sy += V * by; sz += V * bz;
                    double fb = waterDensity * gravity * V;
                    double[] vc = p.velocityAt(bx, by, bz);
                    if (current != null && current.at(bx, bz, cur)) { vc[0] -= cur[0]; vc[2] -= cur[1]; }
                    double sp = Math.sqrt(vc[0] * vc[0] + vc[1] * vc[1] + vc[2] * vc[2]);
                    double A = Math.cbrt(V * V);
                    double kd = 0.5 * waterDensity * drag * A * sp + 0.5 * waterDensity * V;   // quadratisch + zäh
                    double gx = -kd * vc[0], gy = fb - kd * vc[1], gz = -kd * vc[2];
                    fx += gx; fy += gy; fz += gz;
                    double rx = bx - p.p[0], ry = by - p.p[1], rz = bz - p.p[2];
                    tx += ry * gz - rz * gy; ty += rz * gx - rx * gz; tz += rx * gy - ry * gx;
                    double c2 = spinDrag * waterDensity * V * p.crad[i] * p.crad[i];
                    tx -= c2 * p.w[0]; ty -= c2 * p.w[1]; tz -= c2 * p.w[2];
                }
            }
            // Einschlag ins Wasser: verdrängtes Volumen dieses Schritts
            if (sub > p.submerged + 1e-9 && p.v[1] < -0.3 && onImpact != null) {
                onImpact.impact(sx / sub, water.heightAt(sx / sub, sz / sub, time), sz / sub, sub - p.submerged, -p.v[1], true);
            }
            p.submerged = sub;
            if (sub > 0.02 * p.volume) p.wet += dt; else p.wet = 0;
            p.v[0] += fx * p.invMass * dt; p.v[1] += fy * p.invMass * dt; p.v[2] += fz * p.invMass * dt;
            // ω += I⁻¹ τ dt (ohne Kreiselglied: ausdrücklich gerechnet macht es schlanke Splitter instabil; die Drehung
            // um die Hauptachsen bleibt so trotzdem richtig, nur das Taumeln freier Körper fehlt)
            p.invInertiaWorld(new double[]{tx, ty, tz}, tmp);
            p.w[0] += tmp[0] * dt; p.w[1] += tmp[1] * dt; p.w[2] += tmp[2] * dt;
            p.impulse[0] = p.impulse[1] = p.impulse[2] = 0;
            p.impulseMax = 0;
            p.impulseCell = -1;
        }
        long tf = System.nanoTime();
        forceMs = forceMs * 0.95 + (tf - tp0) / 1e6 * 0.05;
        // Stöße
        contacts.max = maxContacts;
        contacts.solve(awake, dt);
        // Lagen
        for (Piece p : awake) {
            clamp(p.v, MAX_SPEED);
            clamp(p.w, MAX_SPIN);
            p.p[0] += (p.v[0] + p.pv[0]) * dt; p.p[1] += (p.v[1] + p.pv[1]) * dt; p.p[2] += (p.v[2] + p.pv[2]) * dt;
            double[] dq = Mat.qmul(new double[]{0, p.w[0] + p.pw[0], p.w[1] + p.pw[1], p.w[2] + p.pw[2]}, p.q);
            p.pv[0] = p.pv[1] = p.pv[2] = p.pw[0] = p.pw[1] = p.pw[2] = 0;
            for (int k = 0; k < 4; k++) p.q[k] += 0.5 * dt * dq[k];
            Mat.normalize(p.q);
            Mat.fromQuat(p.q, p.R);
            p.stamp = -1;
            // Ruhe
            double e = p.v[0] * p.v[0] + p.v[1] * p.v[1] + p.v[2] * p.v[2] + (p.w[0] * p.w[0] + p.w[1] * p.w[1] + p.w[2] * p.w[2]) * p.radius * p.radius;
            // ruhig: unter 6 cm/s an jedem Punkt; große Stücke brauchen länger, frisch gelöste schlafen nicht (ein Turm, der
            // zu kippen beginnt, ist anfangs fast still)
            double lim = 0.06;
            if (e < lim * lim) p.quiet += dt; else p.quiet = 0;
            if (p.wet > 0 && p.wet > sleepAfter) {
                p.asleep = true;
                p.handedOver = true;
                zero(p);
                if (onSettle != null) onSettle.settled(p);
            } else if (p.wet == 0 && p.quiet > 0.6 + 0.02 * p.radius && p.age > 3) {
                p.asleep = true;
                p.sleptAt = time;
                zero(p);
            }
        }
        // kleine Stücke im Wasser gehen im Treibeis auf (Phase 7: sie kosten Berührungen, sieht man aber kaum)
        if (dissolveBelow > 0 || forgetDryAfter > 0)
            pieces.removeIf(p -> {
                boolean dryOld = forgetDryAfter > 0 && p.asleep && p.wet == 0 && time - p.sleptAt > forgetDryAfter;
                if (!dryOld && (dissolveBelow <= 0 || p.volume >= dissolveBelow || p.wet <= 4 || p.age <= 4)) return false;
                if (p.restApplied) for (Object[] r : p.rest) ((Solid) r[0]).restLoad((Integer) r[1], (double[]) r[2], -1);
                return true;
            });
        // Seit Glacier Phase 16 (Dauerlauf): Obergrenze der Stücke. Darüber gehen zuerst ruhende Stücke (die am längsten
        // ruhen), dann die kleinsten im Wasser, nie eines, das jünger als 2 s ist
        if (budget > 0 && pieces.size() > budget) budgetRemoved += trimToBudget();
        // Zerbrechen beim Aufschlag (nicht, wenn schon zu viele Stücke unterwegs sind)
        if (shatter && pieces.size() < maxPieces) {
            int done = 0;
            for (Piece p : awake) {
                if (done >= 2) break;
                // ein Aufschlag verteilt sich über einige Schritte (erst schließt sich der Spalt, dann die Berührung):
                // die Stöße der letzten Stoßdauer zusammen, gerechnet wird, sobald die Spitze vorbei ist
                double jn = Math.sqrt(p.impulse[0] * p.impulse[0] + p.impulse[1] * p.impulse[1] + p.impulse[2] * p.impulse[2]);
                int win = Math.max(2, (int) Math.round(contactTime * 120));
                double keep = 1 - 1.0 / win;
                for (int k = 0; k < 3; k++) p.impulsePrev[k] = p.impulsePrev[k] * keep + p.impulse[k];
                if (p.impulseCell >= 0) p.hitCell = p.impulseCell;
                boolean falling = jn < p.lastJ;
                p.lastJ = jn;
                if (p.bi.length == 0 || p.hitCell < 0 || !falling) continue;
                double jx = p.impulsePrev[0], jy = p.impulsePrev[1], jz = p.impulsePrev[2];
                double J = Math.sqrt(jx * jx + jy * jy + jz * jz);
                if (J * p.invMass < shatterDv) continue;
                p.impulse[0] = jx; p.impulse[1] = jy; p.impulse[2] = jz;
                p.impulseCell = p.hitCell;
                p.impulsePrev[0] = p.impulsePrev[1] = p.impulsePrev[2] = 0;
                if (shatterPiece(p)) done++;
            }
        }
    }

    /** Grenzen gegen Ausreißer: 120 m/s (ein Fall aus 700 m), 40 Umdrehungen je Sekunde. */
    static final double MAX_SPEED = 120, MAX_SPIN = 250;

    private static void clamp(double[] v, double max) {
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (!(l <= max)) {
            double k = Double.isNaN(l) ? 0 : max / l;
            v[0] *= k; v[1] *= k; v[2] *= k;
            if (Double.isNaN(v[0] + v[1] + v[2])) v[0] = v[1] = v[2] = 0;
        }
    }

    /** Nimmt Stücke heraus, bis höchstens {@link #budget} bleiben; gibt die Zahl zurück. */
    private int trimToBudget() {
        int over = pieces.size() - budget, n = 0;
        List<Piece> cand = new ArrayList<>();
        for (Piece p : pieces) if (p.age > 2 && (p.asleep || p.wet > 0)) cand.add(p);
        // ruhende zuerst (am längsten ruhend vorn), dann im Wasser die kleinsten
        cand.sort((a, b) -> {
            if (a.asleep != b.asleep) return a.asleep ? -1 : 1;
            if (a.asleep) return Double.compare(a.sleptAt, b.sleptAt);
            return Double.compare(a.volume, b.volume);
        });
        for (Piece p : cand) {
            if (n >= over) break;
            if (p.restApplied) for (Object[] r : p.rest) ((Solid) r[0]).restLoad((Integer) r[1], (double[]) r[2], -1);
            pieces.remove(p);
            n++;
        }
        return n;
    }

    private static void zero(Piece p) {
        p.v[0] = p.v[1] = p.v[2] = 0;
        p.w[0] = p.w[1] = p.w[2] = 0;
    }

    /**
     * Spannung im Stück beim Stoß: die Zelle, die aufschlägt, hält; jede andere drückt mit ihrer Masse mal der Verzögerung
     * des Stücks (Stoß / Masse / Stoßdauer) dagegen. Was dabei über der Festigkeit liegt, bricht; die Teile fliegen mit
     * der Bewegung des Stücks an ihrem Ort weiter.
     */
    private boolean shatterPiece(Piece p) {
        int n = p.cells.length, m = p.bi.length;
        Stress s = new Stress();
        s.init(n, m);
        s.young = p.material.young;
        // Stoßdauer: mindestens contactTime, bei großen Stücken länger – ein Turm setzt nicht in 0,03 s auf, sondern
        // über den Weg, den er sich dabei selbst zusammendrückt und zerreibt [G]: τ ≈ 0,3 · V^⅓ / Δv
        double dv = Math.sqrt(p.impulse[0] * p.impulse[0] + p.impulse[1] * p.impulse[1] + p.impulse[2] * p.impulse[2]) * p.invMass;
        double tau = Math.max(contactTime, 0.3 * Math.cbrt(p.volume) / Math.max(1, dv));
        double[] a = Mat.mulT(p.R, new double[]{p.impulse[0] / p.mass / tau, p.impulse[1] / p.mass / tau, p.impulse[2] / p.mass / tau});
        for (int i = 0; i < n; i++) {
            System.arraycopy(p.cseed, i * 3, s.x, i * 3, 3);
            s.fixed[i] = i == p.impulseCell;
            double mi = p.cvol[i] * p.material.density;
            double fx = -mi * a[0], fy = -mi * a[1], fz = -mi * a[2];
            double px = p.ccen[i * 3] - p.cseed[i * 3], py = p.ccen[i * 3 + 1] - p.cseed[i * 3 + 1], pz = p.ccen[i * 3 + 2] - p.cseed[i * 3 + 2];
            s.f[i * 6] = fx; s.f[i * 6 + 1] = fy; s.f[i * 6 + 2] = fz;
            s.f[i * 6 + 3] = py * fz - pz * fy; s.f[i * 6 + 4] = pz * fx - px * fz; s.f[i * 6 + 5] = px * fy - py * fx;
        }
        boolean[] broken = new boolean[m];
        System.arraycopy(p.bi, 0, s.bi, 0, m);
        System.arraycopy(p.bj, 0, s.bj, 0, m);
        System.arraycopy(p.bc, 0, s.c, 0, 3 * m);
        System.arraycopy(p.bn, 0, s.nrm, 0, 3 * m);
        System.arraycopy(p.bd, 0, s.d, 0, m);
        System.arraycopy(p.ba, 0, s.area, 0, m);
        double mu = p.material.mu();
        int total = 0;
        double energy = 0;
        List<double[]> path = new ArrayList<>();
        for (int round = 0; round < 3; round++) {
            for (int b = 0; b < m; b++) s.active[b] = !broken[b];
            // nur der Teil, der noch mit der haltenden Zelle verbunden ist, trägt; der Rest ist frei
            boolean[] reach = reach(n, m, p.bi, p.bj, broken, p.impulseCell);
            for (int b = 0; b < m; b++) if (!reach[p.bi[b]]) s.active[b] = false;
            for (int i = 0; i < n; i++) if (!reach[i]) s.fixed[i] = true;
            s.solve(1e-6, 2000);
            int k = 0;
            for (int b = 0; b < m; b++) {
                if (!s.active[b]) continue;
                double u = Stress.utilization(s.sn[b], s.tau[b], s.sb[b], p.ft[b], p.fc[b], p.coh[b], mu);
                if (u > 1) {
                    broken[b] = true;
                    k++;
                    energy += (s.sn[b] * s.sn[b] + s.tau[b] * s.tau[b]) / (2 * p.material.young) * p.ba[b] * p.bd[b];
                    path.add(p.toWorld(p.bc[b * 3], p.bc[b * 3 + 1], p.bc[b * 3 + 2]));
                }
            }
            total += k;
            if (k == 0) break;
            for (int i = 0; i < n; i++) s.fixed[i] = i == p.impulseCell;
        }
        if (total == 0) return false;
        // Teile
        int[] comp = new int[n];
        java.util.Arrays.fill(comp, -1);
        int nc = 0;
        for (int i = 0; i < n; i++) {
            if (comp[i] >= 0) continue;
            boolean[] r = reach(n, m, p.bi, p.bj, broken, i);
            for (int j = 0; j < n; j++) if (r[j]) comp[j] = nc;
            nc++;
        }
        shatters++;
        breaks += total;
        if (onCrack != null) {
            double[] pp = new double[path.size() * 3];
            for (int k = 0; k < path.size(); k++) System.arraycopy(path.get(k), 0, pp, k * 3, 3);
            onCrack.crack(pp, energy, nc > 1);
        }
        if (nc <= 1) {
            // Flächen gebrochen, Stück hält noch zusammen: die Flächen verlieren ihre Festigkeit
            for (int b = 0; b < m; b++) if (broken[b]) { p.ft[b] = p.fc[b] = p.coh[b] = 0; }
            return true;
        }
        pieces.remove(p);
        for (int c = 0; c < nc; c++) {
            List<Poly> polys = new ArrayList<>();
            List<double[]> seeds = new ArrayList<>();
            Map<Integer, Integer> local = new HashMap<>();
            for (int i = 0; i < n; i++) {
                if (comp[i] != c) continue;
                local.put(i, polys.size());
                polys.add(p.worldCell(i));
                seeds.add(p.toWorld(p.cseed[i * 3], p.cseed[i * 3 + 1], p.cseed[i * 3 + 2]));
            }
            int[] cid = new int[polys.size()];
            for (Map.Entry<Integer, Integer> e : local.entrySet()) cid[e.getValue()] = p.cid[e.getKey()];
            List<int[]> bonds = new ArrayList<>();
            List<double[]> data = new ArrayList<>();
            for (int b = 0; b < m; b++) {
                if (broken[b] || comp[p.bi[b]] != c) continue;
                double[] wc = p.toWorld(p.bc[b * 3], p.bc[b * 3 + 1], p.bc[b * 3 + 2]);
                double nx = p.bn[b * 3], ny = p.bn[b * 3 + 1], nz = p.bn[b * 3 + 2];
                double[] wn = {p.R[0] * nx + p.R[1] * ny + p.R[2] * nz, p.R[3] * nx + p.R[4] * ny + p.R[5] * nz, p.R[6] * nx + p.R[7] * ny + p.R[8] * nz};
                bonds.add(new int[]{local.get(p.bi[b]), local.get(p.bj[b])});
                data.add(new double[]{wc[0], wc[1], wc[2], wn[0], wn[1], wn[2], p.bd[b], p.ba[b], p.ft[b], p.fc[b], p.coh[b]});
            }
            Piece q = new Piece(nextPiece++, p.source, p.material, polys, seeds, bonds.toArray(new int[0][]), data.toArray(new double[0][]), cid);
            q.parentCid = p.cid;
            q.parent = p.parent >= 0 ? p.parent : p.id;
            double[] vq = p.velocityAt(q.p[0], q.p[1], q.p[2]);
            System.arraycopy(vq, 0, q.v, 0, 3);
            System.arraycopy(p.w, 0, q.w, 0, 3);
            q.age = p.age;
            q.submerged = p.submerged * q.volume / p.volume;
            q.impactCool = 0.3;
            pieces.add(q);
            if (onBreak != null) onBreak.broke(q.id, q.volume, q.mass);
        }
        return true;
    }

    private static boolean[] reach(int n, int m, int[] bi, int[] bj, boolean[] broken, int start) {
        boolean[] r = new boolean[n];
        if (start < 0) return r;
        r[start] = true;
        boolean grow = true;
        while (grow) {
            grow = false;
            for (int b = 0; b < m; b++) {
                if (broken[b]) continue;
                if (r[bi[b]] != r[bj[b]]) { r[bi[b]] = r[bj[b]] = true; grow = true; }
            }
        }
        return r;
    }

    // ------------------------------------------------------------------ Auskunft

    public int awake() { int k = 0; for (Piece p : pieces) if (!p.asleep) k++; return k; }
    public double stepMs() { return stepMs; }
    private double forceMs;
    /** Zeiten je Schritt (ms, geglättet): Kräfte und Wasser, Erkennen der Berührungen, Lösen der Stöße. */
    public double[] timings() { return new double[]{forceMs, contacts.detectMs, contacts.solveMs}; }
    public double solveMs() { return solveMs; }
    public int shatters() { return shatters; }
    public int breaks() { return breaks; }
    public int contactCount() { return contacts.count(); }
    /** Phase 16: größte Zahl Berührungen in einem Schritt, Zellpaare über {@link #maxContacts}, Vorrat an Berührungen. */
    public int contactPeak() { return contacts.peak(); }
    public long contactsCapped() { return contacts.capped(); }
    public int contactPool() { return contacts.poolSize(); }
    /** Größte Eindringtiefe (m) beim letzten Schritt zwischen Stücken, gegen Boden und Körper. */
    public double maxPenetration() { return contacts.maxDepth(); }

    /** „fracture: Engine (Phase 6) · Eis 917 kg/m³ · Zug 1,0 MPa · Druck 5,0 MPa · 2 Körper, 14 Stücke (9 wach) · MEDIUM“ */
    public String describe() {
        return String.format(Locale.GERMAN, "fracture: %s · %s · %d Körper, %d Stücke (%d wach), %d Berührungen · %.2f ms je Schritt · %s",
                STAGE == 1 ? "Hülle (Phase 1)" : "Engine (Phase " + STAGE + ")", material, solidsNow, piecesNow, awakeNow, contactCount(), stepMs, quality);
    }
}
