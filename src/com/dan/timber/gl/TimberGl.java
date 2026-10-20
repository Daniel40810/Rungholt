package com.dan.timber.gl;

import com.dan.timber.House;
import com.dan.timber.HouseType;
import com.dan.timber.Mesh;
import com.dan.timber.Timber;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet den Holzbau aus {@link Timber} mit JOGL (OpenGL 4.3): je Haustyp und Stufe ein Netz im Grafikspeicher
 * und ein Instanzpuffer, gezeichnet mit einem Aufruf ({@code glDrawElementsInstanced}); dazu ein festes Netz für
 * Zäune, Wege, Palisaden und Brücken ({@link #setStatic}). Holz, Lehm, Flechtwerk, Reet und Soden malt
 * {@code timber.frag} aus Rauschen: Maserung und Beilspuren entlang der Faser, Bohlenfugen, Risse im Lehm, Reet in
 * Lagen mit Moos auf der Nordseite, alles nach dem Zustand des Hauses.
 * <p>
 * Licht wie {@code com.dan.reed}: Sonne, Mond, Himmel, Dunst und eine Schattenkarte von oben (Wolken), in den Einheiten
 * des Benutzers (linear, HDR). Für Vorführungen ohne eigene Nachbearbeitung rechnet {@link View#tonemap} Belichtung,
 * Filmkurve und Gamma gleich im Shader.
 */
public final class TimberGl {
    /** Blick: Projektion·Blick, Auge, Blickrichtung (für das Aussortieren), halber Bildwinkel, Tiefenkonvention. */
    public static final class View {
        public float[] viewProj;
        public double[] eye = new double[3], forward;
        public double fovyDeg = 50, aspect = 16 / 9.0;
        public boolean reversedZ;
        /** Bild gleich fertig (Belichtung, Filmkurve, Gamma), für Vorführungen ohne Nachbearbeitung. */
        public boolean tonemap;
        public double exposure = 1;
    }

    /** Licht (linear, HDR), wie {@code ReedGl.Light}; dazu Nässe 0..1. */
    public static final class Light {
        public double[] sunDir = {0.4, 0.7, 0.3}, sunColor = {2.6, 2.5, 2.3}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        public double[] skyColor = {0.32, 0.38, 0.48}, fogColor = {0.6, 0.65, 0.7};
        public double skyWeight = 1.0, fogDistance = 25_000, wetness;
        public int shadowTexture;
        public double[] shadowRect = {0, 0, 1};
        /**
         * Schattenkarte der Sonne (Tiefentextur mit Vergleich, GL_COMPARE_REF_TO_TEXTURE) und ihre Matrix
         * (Licht-Projektion·Blick, Spaltenreihenfolge); 0 = ohne. Gefüllt mit {@link #drawShadow} (und vom Benutzer mit
         * allem anderen, was Schatten wirft).
         */
        /**
         * Himmel als Rundumbild (0 = keins, dann gilt fogColor) für den Dunst in Blickrichtung, seit Haithabu Phase 5.
         * Abbildung: u = atan(x, −z)/2π + 0,5, v = asin(y)/π + 0,5.
         */
        public int skyTexture;
        public int sunShadowTexture;
        public float[] sunShadowMatrix;
        public double sunShadowBias = 0.0015;
        /**
         * Seit Haithabu Phase 12: Schneehöhe auf den Dächern und auf dem Boden (m), Nässe des Schnees 0..1, Reif 0..1.
         * Schnee liegt auf allem, was nach oben zeigt; über Häusern mit Rauchloch taut er um die Löcher und am First.
         */
        public double roofSnow, groundSnow, snowWet, frost;
        /** Wasserspiegel (m) für Nässe und Algenlinie an Pfählen und Bohlen, seit Haithabu Phase 8; NaN = aus. */
        public double waterLevel = Double.NaN;
        /**
         * Seit Haithabu Phase 10: Punktlichter (Feuer, Fackeln), bis 8: je 4 Zahlen Ort (m) und Größe der Quelle (m),
         * je 4 Zahlen Lichtstärke RGB in den Einheiten von sunColor je m² Abstand (also E = I / d²) und frei.
         */
        public float[] pointPos = new float[32], pointRgb = new float[32];
        public int pointCount;
        /**
         * Seit Haithabu Phase 11: ein begehbarer Raum ({@code Interior.Room#pack}, null = keiner). Flächen innen
         * bekommen die Sonne nur durch die Öffnungen (genau gerechnet, dazu die Schattenkarte), den Himmel nur durch
         * die Öffnungen (Raumwinkel), dazu eine gleichmäßige Rückstrahlung {@link #roomBounce} und die Lichter des
         * Raums statt der Punktlichter draußen; die Rauchschicht unter dem Dach dämpft alles, was sie durchquert.
         */
        public float[] room;
        /** Rauch im Raum: Unterkante der Schicht (m über dem Boden), Auslöschung oben und unten (1/m), Dicke des Übergangs (m). */
        public double[] roomSmoke = {9, 0, 0, 0.3};
        /** Rückstrahlung im Raum als Bestrahlung (Einheiten von sunColor), gleichmäßig von allen Seiten. */
        public double[] roomBounce = {0, 0, 0};
        /** Lichter im Raum (Herd, Lampen), bis 4: Ort und Größe, Lichtstärke wie {@link #pointRgb}. */
        public float[] roomLightPos = new float[16], roomLightRgb = new float[16];
        public int roomLightCount;
    }

    private int program, shadowProgram;
    private final int[][] vao = new int[HouseType.values().length][Timber.LODS], vbo = new int[HouseType.values().length][Timber.LODS],
            ebo = new int[HouseType.values().length][Timber.LODS], ivbo = new int[HouseType.values().length][Timber.LODS],
            nIdx = new int[HouseType.values().length][Timber.LODS];
    private int sVao, sVbo, sEbo, sIvbo, sIdx;
    /** Kacheln der Bauwerke: je Kachel {vao, vbo, ebo, Instanzpuffer, Indexzahl} fein und grob. */
    private final java.util.List<int[][]> tileGl = new java.util.ArrayList<>();
    private java.util.List<com.dan.timber.Structures.Tile> tiles = java.util.List.of();
    private volatile String info = "";
    private Timber.Selection last;
    /** Seit Haithabu Phase 11: ein Haus mit eigenem Netz (begehbar, mit Einrichtung) und seine Puffer. */
    private House special;
    private int[] specialGl;
    private double specialRange = 0;

    public String info() { return info; }
    public Timber.Selection lastSelection() { return last; }

    public void init(GL4 gl, Timber t) {
        program = link(gl, "timber.vert", "timber.frag");
        shadowProgram = link(gl, "timber_shadow.vert", "timber_shadow.frag");
        for (HouseType h : HouseType.values())
            for (int l = 0; l < Timber.LODS; l++) {
                int[] o = upload(gl, t.mesh(h, l));
                vao[h.ordinal()][l] = o[0];
                vbo[h.ordinal()][l] = o[1];
                ebo[h.ordinal()][l] = o[2];
                ivbo[h.ordinal()][l] = o[3];
                nIdx[h.ordinal()][l] = o[4];
            }
    }

    /** Netz hochladen; gibt {vao, vbo, ebo, Instanzpuffer, Indexzahl}. */
    private static int[] upload(GL4 gl, Mesh m) {
        int[] a = new int[1], b = new int[3];
        gl.glGenVertexArrays(1, a, 0);
        gl.glGenBuffers(3, b, 0);
        gl.glBindVertexArray(a[0]);
        float[] v = m.vertexData();
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) v.length * 4, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        int st = Mesh.STRIDE * 4;
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, st, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, st, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 2, GL.GL_FLOAT, false, st, 24);
        gl.glEnableVertexAttribArray(3);
        gl.glVertexAttribPointer(3, 2, GL.GL_FLOAT, false, st, 32);
        int[] idx = m.indexData();
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, (long) idx.length * 4, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[2]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Timber.INSTANCE * 4L, null, GL.GL_DYNAMIC_DRAW);
        gl.glEnableVertexAttribArray(4);
        gl.glVertexAttribPointer(4, 4, GL.GL_FLOAT, false, Timber.INSTANCE * 4, 0);
        gl.glVertexAttribDivisor(4, 1);
        gl.glEnableVertexAttribArray(5);
        gl.glVertexAttribPointer(5, 4, GL.GL_FLOAT, false, Timber.INSTANCE * 4, 16);
        gl.glVertexAttribDivisor(5, 1);
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        return new int[]{a[0], b[0], b[1], b[2], idx.length};
    }

    /**
     * Seit Haithabu Phase 11: ein Haus bekommt ein eigenes Netz (etwa {@code Builder.buildOpen} mit {@code
     * Interior.hall}, örtlich wie die Typen). Bis {@code range} m (0 = bis {@link Timber.Quality#mid}) zeichnen
     * {@link #draw} und {@link #drawShadow} dieses Netz statt der Stufe des Typs. null nimmt es zurück.
     */
    public void setSpecial(GL4 gl, House h, Mesh m, double range) {
        if (specialGl != null) {
            gl.glDeleteVertexArrays(1, new int[]{specialGl[0]}, 0);
            gl.glDeleteBuffers(3, new int[]{specialGl[1], specialGl[2], specialGl[3]}, 0);
            specialGl = null;
        }
        special = h;
        specialRange = range;
        if (h == null || m == null || m.triangles() == 0) { special = null; return; }
        specialGl = upload(gl, m);
        float[] one = new float[Timber.INSTANCE];
        Timber.pack(h, one, 0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, specialGl[3]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, Timber.INSTANCE * 4L, Buffers.newDirectFloatBuffer(one), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Seit Rungholt Phase 13: das eigene Netz an einen anderen Ort oder in eine andere Höhe setzen, ohne es neu
     * hochzuladen (die Kirche steigt bei der Zeitreise aus dem Boden). Das Haus muss in der Liste von {@link #draw}
     * genau so vorkommen, damit es dort nicht doppelt gezeichnet wird.
     */
    public void moveSpecial(GL4 gl, House h) {
        if (specialGl == null || h == null) return;
        special = h;
        float[] one = new float[Timber.INSTANCE];
        Timber.pack(h, one, 0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, specialGl[3]);
        gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, Timber.INSTANCE * 4L, Buffers.newDirectFloatBuffer(one));
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /** Wird das eigene Netz gerade statt des Typs gezeichnet (Auge nah genug)? */
    private boolean specialActive(Timber t, double[] eye) {
        if (special == null) return false;
        double range = specialRange > 0 ? specialRange : t.quality.mid;
        return Math.hypot(special.x() - eye[0], special.z() - eye[2]) - special.radius() < range;
    }

    private List<House> without(List<House> houses, House h) {
        java.util.ArrayList<House> l = new java.util.ArrayList<>(houses.size());
        for (House x : houses) if (!x.equals(h)) l.add(x);
        return l;
    }

    /** Zäune, Wege, Palisaden, Brücken (in Szenenmaßen) als ein festes Netz; ersetzt das vorige. */
    public void setStatic(GL4 gl, Mesh m, double state) {
        disposeStatic(gl);
        if (m == null || m.triangles() == 0) return;
        int[] o = upload(gl, m);
        sVao = o[0]; sVbo = o[1]; sEbo = o[2]; sIvbo = o[3]; sIdx = o[4];
        // Richtung 90° (π/2) ist die Drehung, die örtlich auf die Szene abbildet (örtlich +x = Ost)
        float[] one = {0, 0, 0, (float) (Math.PI / 2), 1, 1, (float) state, 0.5f};
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, sIvbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, 32, Buffers.newDirectFloatBuffer(one), GL.GL_STATIC_DRAW);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    /**
     * Bauwerke in Kacheln (Zäune, Wege, Palisaden, Brücken einer ganzen Siedlung): jede Kachel fein in der Nähe
     * (bis 1,5 · {@link Timber.Quality#near}), grob bis {@link Timber.Quality#far}; ersetzt die vorigen.
     */
    public void setStructures(GL4 gl, com.dan.timber.Structures.Set set, double state) {
        disposeTiles(gl);
        tiles = set == null ? java.util.List.of() : set.tiles();
        float[] one = {0, 0, 0, (float) (Math.PI / 2), 1, 1, (float) state, 0.5f};
        for (com.dan.timber.Structures.Tile t : tiles) {
            int[][] g2 = new int[2][];
            for (int k = 0; k < 2; k++) {
                Mesh m = k == 0 ? t.detail() : t.coarse();
                g2[k] = upload(gl, m);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, g2[k][3]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, 32, Buffers.newDirectFloatBuffer(one), GL.GL_STATIC_DRAW);
            }
            tileGl.add(g2);
        }
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
    }

    private void disposeTiles(GL4 gl) {
        for (int[][] g2 : tileGl)
            for (int[] o : g2) {
                gl.glDeleteVertexArrays(1, new int[]{o[0]}, 0);
                gl.glDeleteBuffers(3, new int[]{o[1], o[2], o[3]}, 0);
            }
        tileGl.clear();
    }

    /** Wählt die Stufen, lädt die Instanzen hoch und zeichnet alles. */
    public void draw(GL4 gl, Timber t, List<House> houses, View v, Light l) {
        double cosHalf = Math.cos(Math.toRadians(Math.max(v.fovyDeg, v.fovyDeg * v.aspect) * 0.5 + 5));
        boolean sp = specialActive(t, v.eye);
        Timber.Selection s = t.select(sp ? without(houses, special) : houses, v.eye, v.forward, cosHalf);
        last = s;
        gl.glUseProgram(program);
        uniforms(gl, v, l);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(v.reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_CULL_FACE);
        gl.glCullFace(GL.GL_BACK);
        gl.glFrontFace(GL.GL_CCW);
        long tris = 0;
        for (HouseType h : HouseType.values())
            for (int lod = 0; lod < Timber.LODS; lod++) {
                int n = s.count[h.ordinal()][lod];
                if (n == 0) continue;
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo[h.ordinal()][lod]);
                gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) n * Timber.INSTANCE * 4, Buffers.newDirectFloatBuffer(s.data[h.ordinal()][lod], 0, n * Timber.INSTANCE), GL4.GL_STREAM_DRAW);
                gl.glBindVertexArray(vao[h.ordinal()][lod]);
                soot(gl, h);
                gl.glDrawElementsInstanced(GL.GL_TRIANGLES, nIdx[h.ordinal()][lod], GL.GL_UNSIGNED_INT, 0, n);
                tris += (long) n * nIdx[h.ordinal()][lod] / 3;
            }
        if (sp) {
            soot(gl, special.type());
            gl.glBindVertexArray(specialGl[0]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, specialGl[4], GL.GL_UNSIGNED_INT, 0, 1);
            tris += specialGl[4] / 3;
        }
        gl.glUniform4f(loc(gl, "uSoot"), 0, 0, 0, 0);
        if (sIdx > 0) {
            gl.glBindVertexArray(sVao);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, sIdx, GL.GL_UNSIGNED_INT, 0, 1);
            tris += sIdx / 3;
        }
        // Bauwerke: Zäune und Wände sind beidseitig gebaut, Bänder einseitig; ohne Rückseitenverwerfen wie die Häuser
        long tt = 0;
        int nFine = 0, nCoarse = 0;
        Timber.Quality q = t.quality;
        for (int i = 0; i < tiles.size(); i++) {
            com.dan.timber.Structures.Tile tl = tiles.get(i);
            double dx = tl.cx() - v.eye[0], dz = tl.cz() - v.eye[2], d = Math.hypot(dx, dz), r = tl.radius();
            if (d - r > q.far) continue;
            if (v.forward != null && d > r) {
                double hz = Math.hypot(v.forward[0], v.forward[2]);
                if (hz > 1e-6 && (dx * v.forward[0] + dz * v.forward[2]) / (d * hz) < Math.cos(Math.min(Math.PI, Math.acos(cosHalf) + Math.asin(Math.min(1, r / d))))) continue;
            }
            int k = d - r < q.near * 1.5 ? 0 : 1;
            int[] o = tileGl.get(i)[k];
            if (o[4] == 0) continue;
            gl.glBindVertexArray(o[0]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, o[4], GL.GL_UNSIGNED_INT, 0, 1);
            tt += o[4] / 3;
            if (k == 0) nFine++; else nCoarse++;
        }
        tris += tt;
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glUseProgram(0);
        info = String.format(Locale.GERMAN, "%d Häuser nah%s, %d mittel, %d fern, %d ausgelassen · %,d Dreiecke%s", s.total(0), sp ? " (+1 begehbar)" : "", s.total(1), s.total(2), s.culled, tris,
                tiles.isEmpty() ? "" : String.format(Locale.GERMAN, " · Bauwerke %d Kacheln fein, %d grob (%,d Dreiecke)", nFine, nCoarse, tt));
    }

    /**
     * Schreibt die Tiefe aller Häuser (Stufe 1, bis {@code range} um den Mittelpunkt) und der Bauwerke in die gerade
     * gebundene Tiefenkarte; lightVP ist die Licht-Projektion·Blick. Der Benutzer bindet das Ziel und setzt den Ausschnitt.
     */
    public void drawShadow(GL4 gl, Timber t, List<House> houses, float[] lightVP, double cx, double cz, double range) {
        gl.glUseProgram(shadowProgram);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(shadowProgram, "uLightVP"), 1, false, lightVP, 0);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_CULL_FACE);
        int nt = HouseType.values().length;
        float[][] data = new float[nt][16 * Timber.INSTANCE];
        int[] cnt = new int[nt];
        // das begehbare Haus mit offenen Türen, Rauchlöchern und Einrichtung: so fällt die Sonne nur durch die Öffnungen
        boolean sp = special != null && Math.hypot(special.x() - cx, special.z() - cz) - special.radius() <= range;
        if (sp) {
            gl.glBindVertexArray(specialGl[0]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, specialGl[4], GL.GL_UNSIGNED_INT, 0, 1);
        }
        for (House h : houses) {
            if (sp && h.equals(special)) continue;
            if (Math.hypot(h.x() - cx, h.z() - cz) - h.radius() > range) continue;
            int k = h.type().ordinal();
            if ((cnt[k] + 1) * Timber.INSTANCE > data[k].length) data[k] = java.util.Arrays.copyOf(data[k], data[k].length * 2);
            Timber.pack(h, data[k], cnt[k] * Timber.INSTANCE, t.burnOf(h));
            cnt[k]++;
        }
        for (HouseType h : HouseType.values()) {
            int k = h.ordinal(), n = cnt[k];
            if (n == 0) continue;
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, ivbo[k][1]);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) n * Timber.INSTANCE * 4, Buffers.newDirectFloatBuffer(data[k], 0, n * Timber.INSTANCE), GL4.GL_STREAM_DRAW);
            gl.glBindVertexArray(vao[k][1]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, nIdx[k][1], GL.GL_UNSIGNED_INT, 0, n);
        }
        if (sIdx > 0) {
            gl.glBindVertexArray(sVao);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, sIdx, GL.GL_UNSIGNED_INT, 0, 1);
        }
        for (int i = 0; i < tiles.size(); i++) {
            com.dan.timber.Structures.Tile tl = tiles.get(i);
            if (Math.hypot(tl.cx() - cx, tl.cz() - cz) - tl.radius() > range) continue;
            int[] o = tileGl.get(i)[1];
            gl.glBindVertexArray(o[0]);
            gl.glDrawElementsInstanced(GL.GL_TRIANGLES, o[4], GL.GL_UNSIGNED_INT, 0, 1);
        }
        gl.glBindVertexArray(0);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        gl.glUseProgram(0);
    }

    /**
     * Legt eine Tiefentextur mit Vergleich an (für {@link Light#sunShadowTexture}) samt Framebuffer; gibt {Textur, FBO}.
     */
    public static int[] createShadowTarget(GL4 gl, int size) {
        int[] t = new int[1], f = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, size, size, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL4.GL_CLAMP_TO_BORDER);
        gl.glTexParameterfv(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1}, 0);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glGenFramebuffers(1, f, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, t[0], 0);
        gl.glDrawBuffer(GL.GL_NONE);
        gl.glReadBuffer(GL.GL_NONE);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        return new int[]{t[0], f[0]};
    }

    /**
     * Licht-Projektion·Blick für eine Schattenkarte: senkrecht zur Sonne (Richtung zur Sonne sunDir), ein Würfel der
     * halben Kantenlänge half um (cx, cy, cz). Spaltenreihenfolge, Tiefe −1..1.
     */
    public static float[] sunMatrix(double[] sunDir, double cx, double cy, double cz, double half) {
        double[] f = {-sunDir[0], -sunDir[1], -sunDir[2]};
        double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        f[0] /= fl; f[1] /= fl; f[2] /= fl;
        double[] up = Math.abs(f[1]) > 0.95 ? new double[]{0, 0, -1} : new double[]{0, 1, 0};
        double[] s = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double sl = Math.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        s[0] /= sl; s[1] /= sl; s[2] /= sl;
        double[] u = {s[1] * f[2] - s[2] * f[1], s[2] * f[0] - s[0] * f[2], s[0] * f[1] - s[1] * f[0]};
        double ex = cx - f[0] * half * 2, ey = cy - f[1] * half * 2, ez = cz - f[2] * half * 2;
        float[] view = new float[16];
        view[0] = (float) s[0]; view[4] = (float) s[1]; view[8] = (float) s[2];
        view[1] = (float) u[0]; view[5] = (float) u[1]; view[9] = (float) u[2];
        view[2] = (float) -f[0]; view[6] = (float) -f[1]; view[10] = (float) -f[2];
        view[12] = (float) -(s[0] * ex + s[1] * ey + s[2] * ez);
        view[13] = (float) -(u[0] * ex + u[1] * ey + u[2] * ez);
        view[14] = (float) (f[0] * ex + f[1] * ey + f[2] * ez);
        view[15] = 1;
        double n = 0.1, fa = half * 4;
        float[] proj = new float[16];
        proj[0] = (float) (1 / half); proj[5] = (float) (1 / half); proj[10] = (float) (-2 / (fa - n)); proj[14] = (float) (-(fa + n) / (fa - n)); proj[15] = 1;
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float sum = 0; for (int k = 0; k < 4; k++) sum += proj[k * 4 + row] * view[c * 4 + k]; r[c * 4 + row] = sum; }
        return r;
    }

    private void uniforms(GL4 gl, View v, Light l) {
        gl.glUniformMatrix4fv(loc(gl, "uVP"), 1, false, v.viewProj, 0);
        gl.glUniform3f(loc(gl, "uCam"), (float) v.eye[0], (float) v.eye[1], (float) v.eye[2]);
        set3(gl, "uSunDir", l.sunDir);
        set3(gl, "uSunColor", l.sunColor);
        set3(gl, "uMoonDir", l.moonDir);
        set3(gl, "uMoonColor", l.moonColor);
        set3(gl, "uSkyColor", l.skyColor);
        set3(gl, "uFogColor", l.fogColor);
        gl.glUniform1f(loc(gl, "uSkyWeight"), (float) l.skyWeight);
        gl.glUniform1f(loc(gl, "uFogDist"), (float) l.fogDistance);
        gl.glUniform1f(loc(gl, "uWet"), (float) l.wetness);
        gl.glUniform1i(loc(gl, "uTonemap"), v.tonemap ? 1 : 0);
        gl.glUniform1f(loc(gl, "uExposure"), (float) v.exposure);
        gl.glActiveTexture(GL.GL_TEXTURE5);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.shadowTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, "uShadow"), 5);
        gl.glUniform4f(loc(gl, "uShadowRect"), (float) l.shadowRect[0], (float) l.shadowRect[1], (float) Math.max(1, l.shadowRect[2]), l.shadowTexture != 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE7);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.skyTexture);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(loc(gl, "uSky"), 7);
        gl.glUniform1i(loc(gl, "uSkyOn"), l.skyTexture != 0 ? 1 : 0);
        boolean sun = l.sunShadowTexture != 0 && l.sunShadowMatrix != null;
        gl.glUniform1i(loc(gl, "uSunShadowOn"), sun ? 1 : 0);
        gl.glUniform4f(loc(gl, "uSnowT"), (float) l.roofSnow, (float) l.groundSnow, (float) l.snowWet, (float) l.frost);
        gl.glUniform2f(loc(gl, "uWater"), Double.isNaN(l.waterLevel) ? 0f : (float) l.waterLevel, Double.isNaN(l.waterLevel) ? 0f : 1f);
        gl.glUniform1i(loc(gl, "uPointN"), Math.min(8, l.pointCount));
        gl.glUniform4fv(loc(gl, "uPointPos[0]"), 8, l.pointPos, 0);
        gl.glUniform4fv(loc(gl, "uPointRgb[0]"), 8, l.pointRgb, 0);
        // Phase 11: der begehbare Raum
        boolean room = l.room != null && l.room.length >= 16;
        float[] rp = room ? l.room : new float[16 + 64];
        gl.glUniform4fv(loc(gl, "uRoomA"), 1, rp, 0);
        gl.glUniform4fv(loc(gl, "uRoomB"), 1, rp, 4);
        gl.glUniform4f(loc(gl, "uRoomC"), rp[8], rp[9], room ? 1f : 0f, 0f);
        gl.glUniform4fv(loc(gl, "uRoomO[0]"), 16, java.util.Arrays.copyOfRange(rp, 16, 16 + 64), 0);
        gl.glUniform4f(loc(gl, "uRoomSmoke"), (float) l.roomSmoke[0], (float) l.roomSmoke[1], (float) l.roomSmoke[2], (float) Math.max(0.05, l.roomSmoke[3]));
        set3(gl, "uRoomBounce", l.roomBounce);
        gl.glUniform1i(loc(gl, "uRoomLightN"), Math.min(4, l.roomLightCount));
        gl.glUniform4fv(loc(gl, "uRoomLightPos[0]"), 4, l.roomLightPos, 0);
        gl.glUniform4fv(loc(gl, "uRoomLightRgb[0]"), 4, l.roomLightRgb, 0);
        if (sun) {
            gl.glActiveTexture(GL.GL_TEXTURE6);
            gl.glBindTexture(GL.GL_TEXTURE_2D, l.sunShadowTexture);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glUniform1i(loc(gl, "uSunShadow"), 6);
            gl.glUniformMatrix4fv(loc(gl, "uSunShadowM"), 1, false, l.sunShadowMatrix, 0);
            gl.glUniform1f(loc(gl, "uSunShadowBias"), (float) l.sunShadowBias);
        } else gl.glUniform1i(loc(gl, "uSunShadow"), 6);
    }

    /**
     * Ruß (seit Haithabu Phase 5) für die Häuser eines Typs: der Rauch des Herdfeuers zieht durch die Rauchlöcher in
     * den Giebeln und durch das Reet am First. Der Shader schwärzt einen Hof um jedes Rauchloch und einen Schleier am
     * First; Häuser ohne Rauchloch bleiben sauber. uSoot: halbe Länge, Höhe des Rauchlochs, Firsthöhe, an.
     */
    private void soot(GL4 gl, HouseType h) {
        double y = h.smokeHole ? com.dan.timber.Builder.smokeHoles(h).get(0)[1] : 0;
        gl.glUniform4f(loc(gl, "uSoot"), (float) (h.length / 2), (float) y, (float) h.ridgeY(), h.smokeHole ? (h == HouseType.GRUBENHAUS ? 0.5f : 1f) : 0f);
    }

    private void set3(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }

    private int loc(GL4 gl, String name) { return gl.glGetUniformLocation(program, name); }

    private void disposeStatic(GL4 gl) {
        if (sVao != 0) gl.glDeleteVertexArrays(1, new int[]{sVao}, 0);
        if (sVbo != 0) gl.glDeleteBuffers(3, new int[]{sVbo, sEbo, sIvbo}, 0);
        sVao = sVbo = sEbo = sIvbo = sIdx = 0;
    }

    public void dispose(GL4 gl) {
        setSpecial(gl, null, null, 0);
        disposeStatic(gl);
        disposeTiles(gl);
        for (HouseType h : HouseType.values())
            for (int l = 0; l < Timber.LODS; l++) {
                int i = h.ordinal();
                if (vao[i][l] != 0) gl.glDeleteVertexArrays(1, new int[]{vao[i][l]}, 0);
                if (vbo[i][l] != 0) gl.glDeleteBuffers(3, new int[]{vbo[i][l], ebo[i][l], ivbo[i][l]}, 0);
                vao[i][l] = vbo[i][l] = 0;
            }
        if (program != 0) gl.glDeleteProgram(program);
        if (shadowProgram != 0) gl.glDeleteProgram(shadowProgram);
        program = shadowProgram = 0;
    }

    // ------------------------------------------------------------------ Shader

    /** Liest eine Shader-Datei aus diesem Package. */
    public static String source(String file) {
        try (InputStream in = TimberGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("timber: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("timber: " + file + ": " + e.getMessage(), e);
        }
    }

    private static int link(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, vert), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag);
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
            throw new IllegalStateException("timber: Programm lässt sich nicht binden: " + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    private static int compile(GL4 gl, int type, String file) {
        int sh = gl.glCreateShader(type);
        gl.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + source(file)}, null, 0);
        gl.glCompileShader(sh);
        int[] ok = new int[1];
        gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
            throw new IllegalStateException("timber: " + file + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return sh;
    }
}
