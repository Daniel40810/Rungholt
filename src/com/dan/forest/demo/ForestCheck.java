package com.dan.forest.demo;

import com.dan.forest.Forest;
import com.dan.forest.ForestField;
import com.dan.forest.ForestPlanter;
import com.dan.forest.Ground;
import com.dan.forest.LeafFall;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeAnimator;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.dan.forest.WindField;

/**
 * Selbstprüfung des Wald-Pakets ohne Fenster: jede Art wächst gleich aus demselben Startwert, die
 * Netze haben keine ungültigen Zahlen und bleiben im Rahmen, ohne Wind steht der Baum still, mit Wind
 * bewegt sich die Spitze mehr als der Fuß, das Laub färbt sich und fällt, fallende Blätter landen.
 * Aufruf: {@code java com.dan.forest.demo.ForestCheck}; Ende mit Code 1, wenn etwas nicht stimmt.
 */
public final class ForestCheck {
    private static int fails;

    public static void main(String[] a) {
        for (java.util.function.Supplier<Species> f : Species.ALL) {
            Species s = f.get();
            TreeModel m1 = TreeGenerator.grow(s, 42, 1), m2 = TreeGenerator.grow(s, 42, 1);
            check(s.name + ": gleicher Startwert, gleicher Baum", m1.branches.size() == m2.branches.size() && m1.leaves.size() == m2.leaves.size()
                    && m1.branches.get(m1.branches.size() - 1).x[1] == m2.branches.get(m2.branches.size() - 1).x[1]);
            check(s.name + ": Höhe in der Spanne der Art", m1.height >= s.heightMin * 0.99f && m1.height <= s.heightMax * 1.01f);
            int prevTris = Integer.MAX_VALUE;
            for (int l = 0; l < 4; l++) {
                TreeMesh t = TreeMesh.build(m1, l);
                boolean ok = t.nt > 0;
                for (int i = 0; ok && i < 3 * t.nv; i++) ok = Float.isFinite(t.pos[i]) && Float.isFinite(t.nrm[i]) && Float.isFinite(t.col[i]);
                for (int i = 0; ok && i < 3 * t.nt; i++) ok = t.tri[i] >= 0 && t.tri[i] < t.nv;
                check(s.name + " Stufe " + l + ": gültig (" + t.nt + " Dreiecke)", ok);
                check(s.name + " Stufe " + l + ": weniger Dreiecke als die Stufe davor", t.nt < prevTris);
                prevTris = t.nt;
            }
            // Wind: ohne Wind still, mit Wind bewegt sich die Spitze deutlich, der Fuß kaum
            TreeMesh t = TreeMesh.build(m1, 1);
            TreeAnimator an = new TreeAnimator(t);
            WindField.Sample calm = new WindField.Sample();
            calm.speed = 0;
            an.pose(3.3, calm, null);
            float still = maxMove(t, an.pos, 0, Float.MAX_VALUE);
            check(s.name + ": ohne Wind still", still < 1e-3f);
            WindField.Sample storm = new WindField.Sample();
            storm.speed = 14; storm.gust = 1.3f; storm.turb = 0.6f;
            an.pose(3.3, storm, null);
            float top = maxMove(t, an.pos, m1.height * 0.8f, Float.MAX_VALUE), foot = trunkMove(t, an.pos, 0.5f);
            check(s.name + String.format(": Sturm bewegt die Krone (%.2f m), der Fuß bleibt (%.3f m)", top, foot), top > 0.05f && foot < 0.05f && top < m1.height * 0.3f);
            // Jahreszeit
            Season summer = Season.of(s, 200, 0), winter = Season.of(s, 20, 0);
            check(s.name + ": im Sommer belaubt", summer.foliage > 0.99f);
            check(s.name + (s.deciduous ? ": im Winter kahl" : ": im Winter grün"), s.deciduous ? winter.foliage == 0 : winter.foliage == 1);
            if (s.deciduous) check(s.name + ": färbt sich im Herbst", Season.of(s, s.colorFull, 0).autumn > 0.95f);
        }
        // Nadelbäume: Zapfen, Kronenformen, Rinde, Lärche, Maitriebe
        TreeModel fir = TreeGenerator.grow(Species.subalpineFir(), 3, 1), spr = TreeGenerator.grow(Species.engelmannSpruce(), 3, 1);
        boolean firUp = !fir.cones.isEmpty(), sprDown = !spr.cones.isEmpty();
        float firLow = Float.MAX_VALUE;
        for (TreeModel.Cone c : fir.cones) { firUp &= c.dy > 0.5f; firLow = Math.min(firLow, c.y); }
        for (TreeModel.Cone c : spr.cones) sprDown &= c.dy < -0.5f;
        check("Zapfen: Tanne aufrecht (" + fir.cones.size() + "), Fichte hängend (" + spr.cones.size() + ")", firUp && sprDown);
        check("Zapfen: bei der Tanne nur oben in der Krone", firLow > fir.height * 0.7f);
        TreeMesh fm = TreeMesh.build(fir, 1);
        int coneTris = 0;
        for (int i = 0; i < fm.nt; i++) if (fm.part[fm.tri[3 * i]] == TreeMesh.CONE) coneTris++;
        check("Zapfen: im Netz (" + coneTris + " Dreiecke)", coneTris == 12 * fir.cones.size());
        TreeModel dgl = TreeGenerator.grow(Species.douglasFir(), 3, 1);
        check(String.format("Turm: Felsengebirgs-Tanne schlanker als Douglasie (%.2f / %.2f)", fir.crownRadius / fir.height, dgl.crownRadius / dgl.height),
                fir.crownRadius / fir.height < 0.8f * dgl.crownRadius / dgl.height);
        TreeModel sco = TreeGenerator.grow(Species.scotsPine(), 5, 1);
        float upper = 0, lower = 0;
        for (TreeModel.LeafSpot l : sco.leaves) {
            float r = (float) Math.hypot(l.x, l.z);
            if (l.y > sco.height * 0.85f) upper = Math.max(upper, r);
            else if (l.y < sco.height * 0.72f) lower = Math.max(lower, r);
        }
        check(String.format("Schirm: Waldkiefer oben breiter als unten (%.1f / %.1f m)", upper, lower), upper > lower);
        TreeMesh sm = TreeMesh.build(sco, 1);
        float topR = 0, footR = 1;
        for (int i = 0; i < sm.nv; i++) {
            if (sm.bone[i] != 0) continue;
            float y = sm.pos[3 * i + 1], rr = sm.col[3 * i] / Math.max(1e-4f, sm.col[3 * i + 2]);
            if (y > sco.height * 0.75f) topR = Math.max(topR, rr);
            if (y < sco.height * 0.2f) footR = Math.min(footR, rr);
        }
        check(String.format("Rinde: Waldkiefer oben orange (Rot/Blau %.1f unten %.1f)", topR, footR), topR > 3 && topR > footR * 1.8f);
        Species lar = Species.larch();
        Season lSummer = Season.of(lar, 200, 0), lOct = Season.of(lar, 298, 0), lWinter = Season.of(lar, 20, 0);
        float[] cs = new float[3], co = new float[3];
        lSummer.leafColor(lar, 0.5f, cs);
        lOct.leafColor(lar, 0.5f, co);
        check("Lärche: im Sommer grün, im Oktober golden, im Winter kahl", cs[1] > cs[0] && co[0] > co[1] && lWinter.foliage == 0 && lSummer.foliage == 1);
        Species sp2 = Species.engelmannSpruce();
        float[] tipJune = new float[3], tipAug = new float[3], inJune = new float[3];
        Season.of(sp2, sp2.leafOut + 15, 0).leafColor(sp2, 0.9f, tipJune);
        Season.of(sp2, 230, 0).leafColor(sp2, 0.9f, tipAug);
        Season.of(sp2, sp2.leafOut + 15, 0).leafColor(sp2, 0.2f, inJune);
        check("Maitriebe: im Juni helle Spitzen, innen dunkel, im August vorbei", tipJune[1] > tipAug[1] * 2 && inJune[1] < tipJune[1] * 0.6f);

        // Wald und Laubfall
        Forest forest = new Forest(Ground.FLAT);
        int as = forest.addSpecies(Species.aspen());
        ForestPlanter.plant(forest, -30, -30, 30, 30, 5, null, new int[]{as}, new float[]{1}, 0, Ground.FLAT, 3);
        check("Wald: Bäume gesetzt (" + forest.trees.size() + ")", forest.trees.size() > 30);
        boolean spaced = true;
        for (int i = 0; i < forest.trees.size(); i++) for (int j = i + 1; j < forest.trees.size(); j++) {
            var p = forest.trees.get(i); var q = forest.trees.get(j);
            if (Math.hypot(p.x - q.x, p.z - q.z) < 1.0) spaced = false;
        }
        check("Wald: Mindestabstand eingehalten", spaced);
        forest.wind.speed = 12;
        forest.setSeason(Species.aspen().colorFull + 5, 0);
        LeafFall lf = forest.leaves;
        int maxN = 0, maxLanded = 0;
        for (int k = 0; k < 300; k++) forest.update(k / 10.0, 0.1f, 0, 1.7, 0);
        forest.setSeason(Species.aspen().bare - 3, 0);
        for (int k = 300; k < 600; k++) {
            forest.update(k / 10.0, 0.1f, 0, 1.7, 0);
            maxN = Math.max(maxN, lf.n);
            maxLanded = Math.max(maxLanded, lf.n - lf.flying());
        }
        check("Laubfall: Blätter gefallen (bis " + maxN + ", davon am Boden bis " + maxLanded + ")", maxN > 500 && maxLanded > 100);
        // Wasser: auf der Hälfte x > 0 liegt ein See 0,5 m über dem Boden; Blätter, die dort landen, übernimmt er
        LeafFall wf = new LeafFall(400);
        int[] taken = {0};
        boolean[] wrongSide = {false};
        wf.water = new LeafFall.Water() {
            @Override public float level(float x, float z) { return x > 0 ? 0.5f : Float.NaN; }
            @Override public boolean take(float x, float y, float z, float r, float g, float b, float size) {
                if (x <= 0 || Math.abs(y - 0.5f) > 1e-3f) wrongSide[0] = true;
                taken[0]++;
                return true;
            }
        };
        java.util.Random wr = new java.util.Random(3);
        for (int i = 0; i < 300; i++) wf.add(wr.nextFloat() * 20 - 10, 4 + wr.nextFloat() * 4, wr.nextFloat() * 20 - 10, 0.5f, 0.3f, 0.05f, 0.07f);
        WindField calm = new WindField();
        calm.speed = 0.5f;
        for (int k = 0; k < 300; k++) wf.step(0.05f, calm, k * 0.05, Ground.FLAT);
        // keines der übrigen darf im See liegen
        float[] wq = new float[12 * wf.capacity], wrgb = new float[3 * wf.capacity], wnr = new float[3 * wf.capacity];
        int onGroundWet = 0, nw = wf.quads(wq, wrgb, wnr);
        for (int i = 0; i < nw; i++) if ((wq[12 * i] + wq[12 * i + 6]) / 2 > 0.3f) onGroundWet++;
        check("Laubfall: Blätter auf dem Wasser gehen ans Wasser (" + taken[0] + " übernommen, " + wf.n + " an Land)", taken[0] > 60 && wf.n > 60 && !wrongSide[0] && onGroundWet == 0);
        // Schwarzerle (seit Haithabu Phase 2b): spät und kaum gefärbt
        Species al = Species.alder();
        Season a289 = Season.of(al, 289, 0), b289 = Season.of(Species.birch(), 289, 0);
        check("Schwarzerle: Mitte Oktober noch grün (Färbung " + Math.round(a289.autumn * 100) + " %), die Birke schon gelb (" + Math.round(b289.autumn * 100) + " %)",
                a289.autumn < 0.1 && b289.autumn > 0.5 && Species.byName("Alnus glutinosa") != null);

        // ForestField (seit Haithabu Phase 2b): Kacheln, Dichte je Art, Stufen, Ausdünnung, Reproduzierbarkeit
        ForestField.Density half = (x, z, sp) -> x < 0 ? (sp == 0 ? 0.9 : 0) : (sp == 1 ? 0.5 : 0);
        ForestField ff = new ForestField(2, 3, half, (x, z) -> 2f, 5);
        ff.radius = 900;
        double[] eye = {0, 20, 0};
        ff.completeNow(eye);
        ff.update(eye, null, 0);
        int left = 0, right = 0, wrong = 0;
        boolean ground = true;
        for (int b = 0; b < ff.buckets(); b++)
            for (int k = 0; k < ff.count[b]; k++) {
                float x = ff.bucket[b][8 * k], y = ff.bucket[b][8 * k + 1];
                int sp = ff.speciesOf(b);
                if (x < 0) left++; else right++;
                if ((x < 0) != (sp == 0)) wrong++;
                if (y > 2f || y < 1.7f) ground = false;
            }
        int[] lc = ff.lodCounts();
        check("ForestField: Dichte je Art (links " + left + " Art 0, rechts " + right + " Art 1, " + wrong + " falsch)", wrong == 0 && left > right * 1.3 && right > 0);
        check("ForestField: Bäume stehen auf dem Boden (Fuß leicht eingesenkt)", ground);
        check("ForestField: alle Stufen belegt, fern die meisten (" + lc[0] + "/" + lc[1] + "/" + lc[2] + "/" + lc[3] + "/" + lc[4] + ")",
                lc[0] > 0 && lc[1] > 0 && lc[2] > 0 && lc[3] > 0 && lc[4] > lc[2]);
        double inner = 0, outer = 0;
        for (int b = 0; b < ff.buckets(); b++)
            for (int k = 0; k < ff.count[b]; k++) {
                double d = Math.hypot(ff.bucket[b][8 * k], ff.bucket[b][8 * k + 2]);
                if (d < 300) inner++; else if (d > 600) outer++;
            }
        double areaIn = Math.PI * 300 * 300, areaOut = Math.PI * (900 * 900 - 600 * 600);
        check(String.format(java.util.Locale.GERMANY, "ForestField: fern ausgedünnt (%.4f gegen %.4f Bäume je m²), Kronen dort breiter", outer / areaOut, inner / areaIn),
                outer / areaOut < inner / areaIn * 0.75 && maxWiden(ff) > 1.2f);
        ForestField f2 = new ForestField(2, 3, half, (x, z) -> 2f, 5);
        f2.radius = 900;
        f2.completeNow(eye);
        f2.update(eye, null, 0);
        check("ForestField: gleicher Startwert, gleicher Wald (" + ff.shown + " Bäume)", f2.shown == ff.shown && f2.planted == ff.planted);
        float[] vp = new float[16];
        vp[0] = 1; vp[5] = 1; vp[10] = -1; vp[11] = -1; vp[14] = -0.2f;   // Blick nach −z, 90°
        ff.update(eye, vp, 0);
        int behind = 0;
        for (int b = 0; b < ff.buckets(); b++) for (int k = 0; k < ff.count[b]; k++) if (ff.bucket[b][8 * k + 2] > 200) behind++;
        check("ForestField: Kacheln hinter dem Blick fallen weg (" + ff.shown + " im Bild, " + behind + " weit dahinter)", behind == 0 && ff.shown < f2.shown);
        ff.apply(ForestField.Quality.LOW);
        check("ForestField: Qualität LOW stellt Reichweite und Abstand ein", ff.radius == ForestField.Quality.LOW.radius && ff.spacing == ForestField.Quality.LOW.spacing && ff.planted == 0);

        System.out.println(fails == 0 ? "Alles in Ordnung." : fails + " Prüfungen fehlgeschlagen.");
        if (fails > 0) System.exit(1);
    }

    /** Wie weit sich der Stamm (nicht die Äste) unterhalb von yMax bewegt. */
    private static float trunkMove(TreeMesh t, float[] pos, float yMax) {
        float m = 0;
        for (int i = 0; i < t.nv; i++) {
            if (t.bone[i] != 0 || t.pos[3 * i + 1] > yMax) continue;
            float dx = pos[3 * i] - t.pos[3 * i], dy = pos[3 * i + 1] - t.pos[3 * i + 1], dz = pos[3 * i + 2] - t.pos[3 * i + 2];
            m = Math.max(m, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return m;
    }

    private static float maxMove(TreeMesh t, float[] pos, float yMin, float yMax) {
        float m = 0;
        for (int i = 0; i < t.nv; i++) {
            float y = t.pos[3 * i + 1];
            if (y < yMin || y > yMax) continue;
            float dx = pos[3 * i] - t.pos[3 * i], dy = pos[3 * i + 1] - t.pos[3 * i + 1], dz = pos[3 * i + 2] - t.pos[3 * i + 2];
            m = Math.max(m, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        }
        return m;
    }

    private static float maxWiden(ForestField f) {
        float m = 0;
        for (int b = 0; b < f.buckets(); b++) for (int k = 0; k < f.count[b]; k++) m = Math.max(m, f.bucket[b][8 * k + 7]);
        return m;
    }

    private static void check(String what, boolean ok) {
        System.out.println((ok ? "  ok    " : "  FEHLER ") + what);
        if (!ok) fails++;
    }
}
