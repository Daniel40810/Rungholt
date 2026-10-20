package com.dan.forest.gl;

import com.dan.forest.ForestField;
import com.dan.forest.Season;
import com.dan.forest.Species;
import com.dan.forest.TreeGenerator;
import com.dan.forest.TreeMesh;
import com.dan.forest.TreeModel;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Zeichnet einen {@link ForestField} mit JOGL (OpenGL 4.3): je Art, Variante und Stufe ein Netz, alle Bäume einer
 * Stufe als Instanzen in einem Aufruf (aus Kīlauea, dort {@code FloraRenderer}). Die Netze werden im Hintergrund für
 * den Tag im Jahr gebacken: Farben nach {@link Season} (Austrieb, Sommer, Herbstfärbung, Schnee), gefallene Blätter
 * weggelassen, kahle Kronen in der Ferne als graue Hülle. Der Wind biegt im Vertex-Shader: der Stamm neigt sich mit
 * dem Quadrat der Höhe, Böen laufen über die Fläche, das Laub zittert.
 * <p>
 * Licht wie {@code com.dan.reed.gl.ReedGl}: Sonne, Mond, Himmel mit Gewicht, Dunst, Wolkenschatten von oben; dazu
 * eine Sonnenschattenkarte des Benutzers ({@link Light#sunShadowTexture}), in die {@link #drawShadow} die Bäume
 * selbst zeichnen kann. Blätter beidseitig, gegen die Sonne durchscheinend.
 * <p>
 * Shader: {@code forest.vert}, {@code forest.frag}, {@code forest_shadow.frag} als Ressourcen in diesem Package.
 */
public final class ForestGl {
    /** Blick: Projektion·Blick, Auge, Tiefenkonvention. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3];
        public boolean reversedZ;
        /** Für Demos ohne eigene Nachbearbeitung: ACES-Tonemapping mit Belichtung und sRGB im Shader. */
        public boolean tonemap;
        public double exposure = 1;
    }

    /** Licht in den Einheiten des Benutzers (linear, HDR). */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, skyColor = {0.3, 0.38, 0.5}, fogColor = {0.6, 0.65, 0.7};
        public double[] moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double fogDistance = 25_000, skyWeight = 1.0, wetness;
        /** Schattenkarte von oben (r = Durchlässigkeit, etwa Wolken; 0 = keine) und ihr Rechteck x, z, Kante (m). */
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 1};
        /** Sonnenschatten: Tiefentextur mit Vergleich (0 = keine), Matrix Welt → Licht (Spalten), Versatz. */
        /**
         * Himmel als Rundumbild (0 = keins, dann gilt fogColor): der Dunst nimmt die Farbe des Himmels am Horizont in
         * Blickrichtung. Abbildung: u = atan(x, −z)/2π + 0,5, v = asin(y)/π + 0,5 (wie com.dan.ocean).
         */
        public int skyTexture;
        public int sunShadowTexture;
        public float[] sunShadowMatrix;
        public double sunShadowBias = 0.0015;
    }

    /** Ecke im Puffer: Lage 3, Normale 3, Farbe 3 (linear, mit Verdeckung), Höhe 0..1, Laub (1 Blatt, 0,5 Kronenhülle, 0 Holz). */
    static final int VSTRIDE = 11;

    private final List<Species> species;
    private final int variants;
    private final long seed;
    private final TreeModel[][] models;
    private final double[] heights;
    private final int buckets;
    private final int[] vao, vbo, ibo, inst, idx, drawn, shadowVao, shadowIdx;
    private int program, shadowProgram;
    private FloatBuffer buf = Buffers.newDirectFloatBuffer(ForestField.STRIDE * 4096);
    private final ExecutorService baker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Wald backen");
        t.setDaemon(true);
        return t;
    });
    private Future<float[][][]> pending;
    private int bakedDay = -999, pendingDay = -999;
    private float bakedSnow = -1, pendingSnow;
    private boolean uploaded;
    private double windToX = 1, windToZ = 0, windSpeed = 4, gust = 0.5;
    private long triangles;
    private String info = "Wald: wird gebacken";
    private final int[] meshTris;

    public ForestGl(List<Species> species, int variants, long seed) {
        this.species = new ArrayList<>(species);
        this.variants = variants;
        this.seed = seed;
        int ns = species.size();
        buckets = ns * variants * ForestField.LODS;
        vao = new int[buckets]; vbo = new int[buckets]; ibo = new int[buckets]; inst = new int[buckets];
        idx = new int[buckets]; drawn = new int[buckets]; shadowVao = new int[buckets]; shadowIdx = new int[buckets];
        meshTris = new int[buckets];
        models = new TreeModel[ns][variants];
        heights = new double[ns];
        for (int s = 0; s < ns; s++) {
            double sum = 0;
            for (int v = 0; v < variants; v++) {
                models[s][v] = TreeGenerator.grow(this.species.get(s), seed + s * 1009L + v * 7919L, 1f);
                sum += models[s][v].height;
            }
            heights[s] = sum / variants;
        }
    }

    /** Mittlere Höhe der Modelle je Art (m), für {@link ForestField#setHeights}. */
    public double[] heights() { return heights.clone(); }
    public boolean ready() { return uploaded; }
    public String info() { return info; }
    public long triangles() { return triangles; }

    private int bucketOf(int s, int v, int lod) { return (s * variants + v) * ForestField.LODS + lod; }

    public void init(GL4 gl) {
        program = link(gl, "forest.vert", "forest.frag", "");
        shadowProgram = link(gl, "forest.vert", "forest_shadow.frag", "#define DEPTH_ONLY\n");
        gl.glGenVertexArrays(buckets, vao, 0);
        gl.glGenVertexArrays(buckets, shadowVao, 0);
        gl.glGenBuffers(buckets, vbo, 0);
        gl.glGenBuffers(buckets, ibo, 0);
        gl.glGenBuffers(buckets, inst, 0);
        for (int b = 0; b < buckets; b++) {
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, ForestField.STRIDE * 4L * 64, null, GL4.GL_STREAM_DRAW);
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Tag im Jahr (1..365) und Schnee 0..1; backt im Hintergrund neu, wenn sich etwas sichtbar ändert. */
    public void setSeason(int day, float snow) {
        if (pending != null) return;
        if (Math.abs(day - bakedDay) < 2 && Math.abs(snow - bakedSnow) < 0.08f) return;
        pendingDay = day;
        pendingSnow = snow;
        pending = baker.submit(() -> bake(day, snow));
    }

    /** Wind: Richtung, in die er weht (x, z), Geschwindigkeit (m/s), Böigkeit 0..1. */
    public void setWind(double toX, double toZ, double speed, double gustiness) {
        double l = Math.hypot(toX, toZ);
        windToX = l > 1e-6 ? toX / l : 1;
        windToZ = l > 1e-6 ? toZ / l : 0;
        windSpeed = speed;
        gust = gustiness;
    }

    // ------------------------------------------------------------------ Backen

    /** Alle Netze für den Tag: [Eimer][0 = Ecken, 1 = Dreiecke als float]. */
    float[][][] bake(int day, float snow) {
        float[][][] out = new float[buckets][][];
        for (int s = 0; s < species.size(); s++) {
            Species sp = species.get(s);
            Season se = Season.of(sp, day, snow);
            for (int v = 0; v < variants; v++) {
                TreeModel m = models[s][v];
                TreeMesh mid = TreeMesh.build(m, 1, 2);
                long k = seed + s * 31 + v * 7;
                out[bucketOf(s, v, 0)] = pack(TreeMesh.build(m, 0), se, k, 1, 1);
                out[bucketOf(s, v, 1)] = pack(mid, se, k + 1, 1, 1);
                // Stufe 2: dieselben Blattbüschel, nur jedes siebte, dafür doppelt so groß (gezackt wie nah, ein Zehntel der Dreiecke)
                out[bucketOf(s, v, 2)] = pack(mid, se, k + 2, 0.15f, 2.0f);
                out[bucketOf(s, v, 3)] = pack(TreeMesh.silhouette(m, 7, 4), se, k + 3, 1, 1);
                out[bucketOf(s, v, 4)] = pack(TreeMesh.silhouette(m, 5, 2), se, k + 4, 1, 1);
            }
        }
        return out;
    }

    /**
     * Ein Netz mit Jahreszeit: Farben, gefallene Blätter weg, Ballen mit gestreuten Normalen. keep &lt; 1 behält nur
     * diesen Anteil der Blattbüschel und vergrößert sie um grow (um ihren Ansatz).
     */
    static float[][] pack(TreeMesh t, Season se, long seed, float keep, float grow) {
        int nv = t.nv;
        float[] col = new float[3 * nv];
        se.colorize(t, col);
        float H = Math.max(1, t.model.height);
        Species sp = t.model.species;
        java.util.Random jr = new java.util.Random(seed * 31 + 7);
        float[] v = new float[nv * VSTRIDE];
        boolean[] gone = new boolean[nv];
        float[] tc = new float[3];
        float tk = 1;
        int lastGrp = -1;
        // kahle Krone in der Ferne: Hülle grau wie feines Geäst, dünner
        float bare = se.evergreen ? 0 : Math.min(1, se.drop * 1.15f);
        for (int i = 0; i < nv; i++) {
            byte p = t.part[i];
            boolean leafy = p == TreeMesh.LEAF || p == TreeMesh.NEEDLES || p == TreeMesh.CLUMP;
            float nx = t.nrm[3 * i], ny = t.nrm[3 * i + 1], nz = t.nrm[3 * i + 2];
            float r = col[3 * i], g = col[3 * i + 1], b = col[3 * i + 2];
            if (p == TreeMesh.CLUMP) {
                double ax = nx * 0.55 + jr.nextGaussian() * 0.55, ay = ny * 0.55 + jr.nextGaussian() * 0.55 + 0.45, az = nz * 0.55 + jr.nextGaussian() * 0.55;
                double l = Math.sqrt(ax * ax + ay * ay + az * az) + 1e-9;
                nx = (float) (ax / l); ny = (float) (ay / l); nz = (float) (az / l);
                // Farbe je Ballen (Stufe 2: 12 Ecken) oder je Ecke der Hülle (Stufe 3, 4) aus einem eigenen Ton: so mischen
                // sich grüne und gefärbte Blätter wie in den feinen Stufen, statt dass jede Krone einen Mittelton trägt
                int grp = t.lod == 2 ? i / 12 : i;
                if (grp != lastGrp) {
                    lastGrp = grp;
                    se.leafColor(sp, (float) jr.nextDouble(), tc);
                    tk = (float) (0.75 + 0.35 * jr.nextDouble());
                }
                float ao = t.ao != null ? t.ao[i] : 1;
                // Hülle der Krone: das Innere liegt im Eigenschatten, die Krone als Ganzes wirft viel weniger zurück als ein Blatt
                float dim = t.lod == 2 ? 0.72f : 0.3f;
                r = tc[0] * ao * tk * dim; g = tc[1] * ao * tk * dim; b = tc[2] * ao * tk * dim;
                if (bare > 0) {
                    // feines Geäst: graubraun, nicht die Farbe der Stammrinde (die Birke wäre sonst weiß)
                    r += (0.085f * ao - r) * bare; g += (0.075f * ao - g) * bare; b += (0.065f * ao - b) * bare;
                }
            }
            if (p == TreeMesh.LEAF && t.leaf[i] >= 0 && se.fallen(t, t.leaf[i], 0)) gone[i] = true;
            float px = t.pos[3 * i], py = t.pos[3 * i + 1], pz = t.pos[3 * i + 2];
            if (keep < 1 && p == TreeMesh.LEAF && t.leaf[i] >= 0) {
                int li = t.leaf[i];
                if (((li * 2654435761L + seed) >>> 8 & 0xffff) / 65536.0 >= keep) gone[i] = true;
                else {
                    int a0 = t.leafVertex[li];
                    float ax = t.pos[3 * a0], ay = t.pos[3 * a0 + 1], az = t.pos[3 * a0 + 2];
                    px = ax + (px - ax) * grow; py = ay + (py - ay) * grow; pz = az + (pz - az) * grow;
                }
            }
            if (p == TreeMesh.LEAF && !se.evergreen && se.foliage <= 0.02f) gone[i] = true;
            int o = i * VSTRIDE;
            v[o] = px; v[o + 1] = py; v[o + 2] = pz;
            v[o + 3] = nx; v[o + 4] = ny; v[o + 5] = nz;
            v[o + 6] = r; v[o + 7] = g; v[o + 8] = b;
            v[o + 9] = Math.max(0, Math.min(1, t.pos[3 * i + 1] / H));
            // Laub 1, Hülle einer Silhouette 0,5 (scheint gegen die Sonne kaum durch: das Innere der Krone ist dunkel), Holz 0
            v[o + 10] = leafy ? (p == TreeMesh.CLUMP && t.lod >= 3 ? 0.5f : 1f) : 0;
        }
        // Laubballen der Stufe 2 (je 12 Ecken ein Ikosaeder) etwas kleiner: sie stehen für lichtes Laub, nicht für Kugeln
        if (t.lod == 2)
            for (int i = 0; i + 12 <= nv; ) {
                if (t.part[i] != TreeMesh.CLUMP) { i++; continue; }
                float cx = 0, cy = 0, cz = 0;
                for (int k = 0; k < 12; k++) { cx += v[(i + k) * VSTRIDE]; cy += v[(i + k) * VSTRIDE + 1]; cz += v[(i + k) * VSTRIDE + 2]; }
                cx /= 12; cy /= 12; cz /= 12;
                for (int k = 0; k < 12; k++) {
                    int o = (i + k) * VSTRIDE;
                    v[o] = cx + (v[o] - cx) * 0.8f; v[o + 1] = cy + (v[o + 1] - cy) * 0.8f; v[o + 2] = cz + (v[o + 2] - cz) * 0.8f;
                }
                i += 12;
            }
        float[] tri = new float[3 * t.nt];
        int k = 0;
        java.util.Random cr = new java.util.Random(seed);
        for (int q = 0; q < t.nt; q++) {
            int a = t.tri[3 * q], b = t.tri[3 * q + 1], c = t.tri[3 * q + 2];
            if (gone[a] || gone[b] || gone[c]) continue;
            // Ballen und Hülle kahler Bäume ausdünnen (fern: lichtes Geäst statt Laubwolke)
            if (bare > 0.3f && t.part[a] == TreeMesh.CLUMP && cr.nextFloat() < (bare - 0.3f) * 0.8f) continue;
            tri[k++] = Float.intBitsToFloat(a); tri[k++] = Float.intBitsToFloat(b); tri[k++] = Float.intBitsToFloat(c);
        }
        return new float[][]{v, java.util.Arrays.copyOf(tri, k)};
    }

    private void upload(GL4 gl, float[][][] meshes) {
        for (int b = 0; b < buckets; b++) {
            float[] v = meshes[b][0], tf = meshes[b][1];
            int[] tri = new int[tf.length];
            for (int i = 0; i < tf.length; i++) tri[i] = Float.floatToRawIntBits(tf[i]);
            gl.glBindVertexArray(vao[b]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, Math.max(4L, v.length * 4L), Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
            meshAttribs(gl);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[b]);
            gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, Math.max(4L, tri.length * 4L), Buffers.newDirectIntBuffer(tri), GL.GL_STATIC_DRAW);
            idx[b] = tri.length;
            meshTris[b] = tri.length / 3;
            instAttribs(gl, inst[b]);
        }
        // Schatten: nahe Bäume (Stufe 0, 1) werfen den Schatten der Stufe 2, alle weiteren den ihrer Silhouette (Stufe 3)
        for (int b = 0; b < buckets; b++) {
            int l = ForestField.lodOf(b), mb = b - l + (l <= 1 ? 2 : Math.max(3, l));
            gl.glBindVertexArray(shadowVao[b]);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo[mb]);
            meshAttribs(gl);
            gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, ibo[mb]);
            shadowIdx[b] = idx[mb];
            instAttribs(gl, inst[b]);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private static void meshAttribs(GL4 gl) {
        int st = VSTRIDE * 4;
        gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 3, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3); gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 36);
    }

    private static void instAttribs(GL4 gl, int buffer) {
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, buffer);
        for (int a = 0; a < 2; a++) {
            gl.glEnableVertexAttribArray(4 + a);
            gl.glVertexAttribPointer(4 + a, 4, GL.GL_FLOAT, false, ForestField.STRIDE * 4, a * 16L);
            gl.glVertexAttribDivisor(4 + a, 1);
        }
    }

    // ------------------------------------------------------------------ je Bild

    /** Übernimmt fertig gebackene Netze und lädt die Eimer des Feldes hoch (nach {@link ForestField#update}). */
    public void update(GL4 gl, ForestField f) {
        if (pending != null && pending.isDone()) {
            try {
                upload(gl, pending.get());
                uploaded = true;
                bakedDay = pendingDay;
                bakedSnow = pendingSnow;
            } catch (Exception e) {
                System.err.println("forest: Backen fehlgeschlagen: " + e);
                bakedDay = pendingDay;
                bakedSnow = pendingSnow;
            }
            pending = null;
        }
        triangles = 0;
        if (!uploaded) { info = "Wald: wird gebacken"; return; }
        int n = Math.min(buckets, f.buckets());
        for (int b = 0; b < n; b++) {
            int c = f.count[b];
            drawn[b] = c;
            if (c == 0) continue;
            if (buf.capacity() < c * ForestField.STRIDE) buf = Buffers.newDirectFloatBuffer(Math.max(c * ForestField.STRIDE, buf.capacity() * 2));
            buf.clear();
            buf.put(f.bucket[b], 0, c * ForestField.STRIDE).flip();
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, inst[b]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) c * ForestField.STRIDE * 4, buf, GL4.GL_STREAM_DRAW);
            triangles += (long) c * meshTris[b];
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        int[] lc = f.lodCounts();
        info = String.format(Locale.GERMAN, "Wald: %,d Bäume (%d/%d/%,d/%,d/%,d je Stufe), %,d Dreiecke, %,d gepflanzt, Tag %d",
                f.shown, lc[0], lc[1], lc[2], lc[3], lc[4], triangles, f.planted, bakedDay);
    }

    /** Zeichnet in den gebundenen Bildspeicher (Tiefentest an, beide Seiten). t: Laufzeit (s). */
    public void draw(GL4 gl, View v, Light l, double t) {
        if (!uploaded) return;
        gl.glUseProgram(program);
        common(gl, program, v.viewProj, v.eye, t);
        gl.glUniform3f(loc(gl, program, "uSunDir"), (float) l.sunDir[0], (float) l.sunDir[1], (float) l.sunDir[2]);
        gl.glUniform3f(loc(gl, program, "uSunColor"), (float) l.sunColor[0], (float) l.sunColor[1], (float) l.sunColor[2]);
        gl.glUniform3f(loc(gl, program, "uSkyColor"), (float) l.skyColor[0], (float) l.skyColor[1], (float) l.skyColor[2]);
        gl.glUniform3f(loc(gl, program, "uFogColor"), (float) l.fogColor[0], (float) l.fogColor[1], (float) l.fogColor[2]);
        gl.glUniform3f(loc(gl, program, "uMoonDir"), (float) l.moonDir[0], (float) l.moonDir[1], (float) l.moonDir[2]);
        gl.glUniform3f(loc(gl, program, "uMoonColor"), (float) l.moonColor[0], (float) l.moonColor[1], (float) l.moonColor[2]);
        gl.glUniform1f(loc(gl, program, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, program, "uSkyWeight"), (float) l.skyWeight);
        gl.glUniform1f(loc(gl, program, "uWet"), (float) l.wetness);
        gl.glUniform2f(loc(gl, program, "uTone"), v.tonemap ? 1f : 0f, (float) v.exposure);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE6);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.sunShadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, program, "uShadow"), 5);
        gl.glUniform4f(loc(gl, program, "uShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1], (float) Math.max(1, l.shadowRect[2]), l.shadowTexture != 0 ? 1f : 0f);
        gl.glUniform1i(loc(gl, program, "uSunShadow"), 6);
        gl.glUniform1i(loc(gl, program, "uSky"), 7);
        gl.glUniform1i(loc(gl, program, "uSkyOn"), l.skyTexture != 0 ? 1 : 0);
        boolean ss = l.sunShadowTexture != 0 && l.sunShadowMatrix != null;
        gl.glUniformMatrix4fv(loc(gl, program, "uSunShadowM"), 1, false, ss ? l.sunShadowMatrix : new float[16], 0);
        gl.glUniform2f(loc(gl, program, "uSunShadowP"), ss ? 1f : 0f, (float) l.sunShadowBias);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        for (int b = 0; b < buckets; b++) {
            if (drawn[b] == 0 || idx[b] == 0) continue;
            gl.glBindVertexArray(vao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, idx[b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /**
     * Zeichnet die Bäume in eine gebundene Tiefen-Schattenkarte (lightVP: Welt → Licht). Nur Bäume bis zur Stufe
     * maxLod, mit groben Netzen (Laubballen statt Blätter).
     */
    public void drawShadow(GL4 gl, float[] lightVP, double t, int maxLod) {
        if (!uploaded) return;
        gl.glUseProgram(shadowProgram);
        common(gl, shadowProgram, lightVP, new double[]{0, 0, 0}, t);
        gl.glDisable(GL.GL_CULL_FACE);
        for (int b = 0; b < buckets; b++) {
            if (drawn[b] == 0 || shadowIdx[b] == 0 || ForestField.lodOf(b) > maxLod) continue;
            gl.glBindVertexArray(shadowVao[b]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, shadowIdx[b], GL.GL_UNSIGNED_INT, 0, drawn[b]);
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void common(GL4 gl, int p, float[] vp, double[] eye, double t) {
        gl.glUniformMatrix4fv(loc(gl, p, "uVP"), 1, false, vp, 0);
        gl.glUniform3f(loc(gl, p, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(loc(gl, p, "uTime"), (float) (t % 3600));
        gl.glUniform4f(loc(gl, p, "uWind"), (float) windToX, (float) windToZ, (float) windSpeed, (float) gust);
    }

    private static int loc(GL4 gl, int p, String name) { return gl.glGetUniformLocation(p, name); }

    public void dispose(GL4 gl) {
        baker.shutdownNow();
        if (program == 0) return;
        gl.glDeleteProgram(program);
        gl.glDeleteProgram(shadowProgram);
        gl.glDeleteVertexArrays(buckets, vao, 0);
        gl.glDeleteVertexArrays(buckets, shadowVao, 0);
        gl.glDeleteBuffers(buckets, vbo, 0);
        gl.glDeleteBuffers(buckets, ibo, 0);
        gl.glDeleteBuffers(buckets, inst, 0);
        program = 0;
        uploaded = false;
    }

    // ------------------------------------------------------------------ Shader

    public static String source(String file) {
        try (InputStream in = ForestGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("forest: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("forest: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag, String defs) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert, defs), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag, defs);
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("forest: Programm lässt sich nicht binden: " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    private static int compile(GL4 gl, int type, String file, String defs) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + defs + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("forest: " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return sh;
    }
}
