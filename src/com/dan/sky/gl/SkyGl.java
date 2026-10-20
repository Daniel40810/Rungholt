package com.dan.sky.gl;

import com.dan.sky.Atmosphere;
import com.dan.sky.Celestial;
import com.dan.sky.MoonDisc;
import com.dan.sky.Planets;
import com.dan.sky.Scattering;
import com.dan.sky.Sky;
import com.dan.sky.StarCatalog;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Der Himmel auf der Grafikkarte (OpenGL 4.3, ohne Compute-Shader): drei Tabellen nach Hillaire 2020
 * (Durchlässigkeit und Mehrfachstreuung nur bei neuer Atmosphäre, die Himmelsansicht je Bild), dann der Himmel als
 * Vollbild, Sterne und Planeten als Lichtpunkte, auf Wunsch die Sternbildfiguren. Dazu dasselbe als Rundumbild für
 * Spiegelungen (dasselbe Format, das {@code com.dan.ocean} liest: Azimut von Nord über die Breite, Höhe −90…+90°).
 * <p>
 * Ablauf je Bild: {@link #update} (rechnet die Tabellen), {@link #draw} ins gebundene Ziel, {@link #drawPanorama}
 * ins gebundene Rundumbild. Alle Werte werden mit der Vorbelichtung {@link View#preExposure} multipliziert, damit
 * der Nachthimmel (10⁻⁹ der Tagesleuchtdichte) im Halbfloat-Puffer nicht verschwindet.
 * <p>
 * Die Uniforms rechnet {@link #uniforms} ohne Grafikkarte aus dem Zustand; so lassen sie sich prüfen und in andere
 * Werkzeuge übernehmen.
 */
public final class SkyGl {
    public static final int SKYVIEW_W = 256, SKYVIEW_H = 128;

    /** Blick der Kamera: Matrizen (spaltenweise), Auge, Tiefenkonvention, Bildgröße, Öffnung, Vorbelichtung, Laufzeit. */
    public static final class View {
        public float[] viewProj, invViewProj;
        public boolean reversedZ;
        public int width = 1, height = 1;
        public double fovyDeg = 55, preExposure = 1, time;
        /** Unter der Kimm eine schlichte Wasserfläche zeigen (Szenen ohne eigenes Meer). */
        public boolean sea;
    }

    /** Ein Uniform: Art (f = float/vec, m3, m4, v4 = vec4-Feld, i = int) und Werte. */
    public record U(String kind, float[] v) {
        static U f(double... x) {
            float[] a = new float[x.length];
            for (int i = 0; i < x.length; i++) a[i] = (float) x[i];
            return new U("f", a);
        }
        static U i(int x) { return new U("i", new float[]{x}); }
    }

    private int progTrans, progMs, progView, progSky, progPano, progStars, progLines, progPost;
    private int texTrans, texMs, texView, texMilky, texMoon, fboTrans, fboMs, fboView;
    private int vaoEmpty, vaoStars, vboStars, vaoPlanets, vboPlanets, vaoLines, vboLines;
    private int starCount, lineVerts;
    private int builtVersion = -1;
    private boolean ready;
    private String info = "";
    private double[] moonCal;
    private String moonKey = "", viewKey = "";

    public boolean ready() { return ready; }
    /** „Himmel: Tabellen 256×64, 32², 256×128 · 8 920 Sterne · 5 Planeten · 24 Figuren“ */
    public String info() { return info; }

    // ------------------------------------------------------------------ Aufbau

    public void init(GL4 gl) {
        progTrans = Glsl.program(gl, "Durchlässigkeit", new String[0], "fullscreen.vert", "transmittance.frag");
        progMs = Glsl.program(gl, "Mehrfachstreuung", new String[0], "fullscreen.vert", "multiscatter.frag");
        progView = Glsl.program(gl, "Himmelsansicht", new String[0], "fullscreen.vert", "skyview.frag");
        progSky = Glsl.program(gl, "Himmel", new String[0], "fullscreen.vert", "sky.frag");
        progPano = Glsl.program(gl, "Himmel rundum", new String[]{"PANORAMA"}, "fullscreen.vert", "sky.frag");
        progStars = Glsl.program(gl, "Sterne", new String[0], "stars.vert", "stars.frag");
        progLines = Glsl.program(gl, "Sternbilder", new String[0], "lines.vert", "lines.frag");
        progPost = Glsl.program(gl, "Nachbearbeitung", new String[0], "fullscreen.vert", "post.frag");
        texTrans = texture(gl, Scattering.TRANS_W, Scattering.TRANS_H, GL.GL_RGBA32F, false);
        texMs = texture(gl, Scattering.MS_N, Scattering.MS_N, GL.GL_RGBA32F, false);
        texView = texture(gl, SKYVIEW_W, SKYVIEW_H, GL.GL_RGBA32F, true);
        fboTrans = fbo(gl, texTrans);
        fboMs = fbo(gl, texMs);
        fboView = fbo(gl, texView);
        texMilky = milkyWayTexture(gl);
        texMoon = moonTexture(gl);
        int[] v = new int[4];
        gl.glGenVertexArrays(4, v, 0);
        vaoEmpty = v[0];
        vaoStars = v[1];
        vaoPlanets = v[2];
        vaoLines = v[3];
        int[] b = new int[3];
        gl.glGenBuffers(3, b, 0);
        vboStars = b[0];
        vboPlanets = b[1];
        vboLines = b[2];
        StarCatalog cat = StarCatalog.get();
        float[] stars = starVertices(cat);
        starCount = cat.size();
        setupPoints(gl, vaoStars, vboStars, stars, GL.GL_STATIC_DRAW);
        setupPoints(gl, vaoPlanets, vboPlanets, new float[7 * Planets.NAMES.length], GL.GL_DYNAMIC_DRAW);
        float[] lines = lineVertices(cat);
        lineVerts = lines.length / 3;
        gl.glBindVertexArray(vaoLines);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboLines);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) lines.length * 4, Buffers.newDirectFloatBuffer(lines), GL.GL_STATIC_DRAW);
        int a = gl.glGetAttribLocation(progLines, "aDir");
        gl.glEnableVertexAttribArray(a);
        gl.glVertexAttribPointer(a, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindVertexArray(0);
        info = String.format(Locale.GERMAN, "Tabellen %d×%d, %d², %d×%d · %d Sterne · %d Planeten · %d Figuren",
                Scattering.TRANS_W, Scattering.TRANS_H, Scattering.MS_N, SKYVIEW_W, SKYVIEW_H, starCount,
                Planets.NAMES.length, cat.figures.size());
        ready = true;
    }

    private void setupPoints(GL4 gl, int vao, int vbo, float[] data, int usage) {
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) data.length * 4, Buffers.newDirectFloatBuffer(data), usage);
        int[] loc = {gl.glGetAttribLocation(progStars, "aDir"), gl.glGetAttribLocation(progStars, "aMag"), gl.glGetAttribLocation(progStars, "aColor")};
        int[] size = {3, 1, 3}, off = {0, 12, 16};
        for (int i = 0; i < 3; i++) {
            if (loc[i] < 0) continue;
            gl.glEnableVertexAttribArray(loc[i]);
            gl.glVertexAttribPointer(loc[i], size[i], GL.GL_FLOAT, false, 28, off[i]);
        }
        gl.glBindVertexArray(0);
    }

    /** Je Stern 7 Zahlen: Richtung J2000, Helligkeit, Farbe (Leuchtdichte 1). */
    public static float[] starVertices(StarCatalog cat) {
        float[] out = new float[cat.size() * 7];
        for (int i = 0; i < cat.size(); i++) {
            double[] d = Celestial.equatorial(cat.ra[i], cat.dec[i]), c = StarCatalog.color(cat.bv[i]);
            int o = i * 7;
            out[o] = (float) d[0];
            out[o + 1] = (float) d[1];
            out[o + 2] = (float) d[2];
            out[o + 3] = cat.mag[i];
            out[o + 4] = (float) c[0];
            out[o + 5] = (float) c[1];
            out[o + 6] = (float) c[2];
        }
        return out;
    }

    /** Planeten wie Sterne (7 Zahlen je Planet). */
    public static float[] planetVertices(Sky.State st) {
        float[] out = new float[7 * st.planets.size()];
        for (int p = 0; p < st.planets.size(); p++) {
            Planets.Planet pl = st.planets.get(p);
            double[] c = pl.color();
            double y = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
            int o = p * 7;
            for (int k = 0; k < 3; k++) out[o + k] = (float) pl.equatorialJ2000()[k];
            out[o + 3] = (float) pl.magnitude();
            for (int k = 0; k < 3; k++) out[o + 4 + k] = (float) (c[k] / y);
        }
        return out;
    }

    /** Figurenlinien als Großkreisbögen, je Linie 8 Stücke. */
    public static float[] lineVertices(StarCatalog cat) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (StarCatalog.Figure f : cat.figures)
            for (double[] l : f.lines()) {
                double[] a = Celestial.equatorial(l[0], l[1]), b = Celestial.equatorial(l[2], l[3]);
                for (int k = 0; k < 8; k++) {
                    pts.add(slerp(a, b, k / 8.0));
                    pts.add(slerp(a, b, (k + 1) / 8.0));
                }
            }
        float[] out = new float[pts.size() * 3];
        for (int i = 0; i < pts.size(); i++)
            for (int k = 0; k < 3; k++) out[i * 3 + k] = (float) pts.get(i)[k];
        return out;
    }

    static double[] slerp(double[] a, double[] b, double t) {
        double[] v = {a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
        double n = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / n, v[1] / n, v[2] / n};
    }

    // ------------------------------------------------------------------ Uniforms (ohne Grafikkarte)

    /**
     * Alle Uniforms für einen Zustand und einen Blick, nach Programm: "view", "sky", "pano", "stars", "planets",
     * "lines". Matrizen spaltenweise.
     */
    public Map<String, Map<String, U>> uniforms(Sky.State st, View v) {
        Map<String, Map<String, U>> all = new LinkedHashMap<>();
        double eyeR = Atmosphere.BOTTOM + st.eyeHeight;
        float[] atm = st.atmosphere.uniforms();
        U uAtm = new U("f", atm);

        Map<String, U> view = new LinkedHashMap<>();
        view.put("uAtm", uAtm);
        view.put("uEyeR", U.f(eyeR));
        view.put("uSunDir", U.f(st.sunDir));
        view.put("uSunE", U.f(st.sunToa));
        view.put("uMoonDir", U.f(st.moonDir));
        view.put("uMoonE", U.f(st.moonToa));
        view.put("uNight", U.f(Sky.NIGHT_LUMINANCE));
        view.put("uDomes", new U("v4", st.domes));
        view.put("uVis", U.f(st.visibilityKm));
        view.put("uVeil", U.f(st.veil));
        view.put("uOvercast", U.f(st.overcast));
        all.put("view", view);

        double pixelAngle = Math.toRadians(v.fovyDeg) / Math.max(1, v.height);
        double[] cal = moonCalibration(st);
        double[][] fr = MoonDisc.frame(st.moonDirTrue, st.moonNorth);
        double[] ml = {dot(st.moonLight, fr[0]), dot(st.moonLight, fr[1]), dot(st.moonLight, fr[2])};
        double sunR = Math.toRadians(959.63 / 3600 / sunDistance(st));
        Map<String, U> sky = new LinkedHashMap<>();
        sky.put("uAtm", uAtm);
        sky.put("uInvVP", new U("m4", v.invViewProj != null ? v.invViewProj : identity4()));
        sky.put("uReversed", U.i(v.reversedZ ? 1 : 0));
        sky.put("uPre", U.f(v.preExposure));
        sky.put("uSea", U.f(v.sea ? 1 : 0));
        sky.put("uEyeR", U.f(eyeR));
        sky.put("uNight", U.f(Sky.NIGHT_LUMINANCE));
        sky.put("uMilkyGain", U.f(st.milkyWay ? 1 : 0));
        sky.put("uVeil", U.f(st.veil));
        sky.put("uRainbow", U.f(st.rainbow));
        sky.put("uPixelAngle", U.f(pixelAngle));
        sky.put("uSceneToEq", new U("m3", columnMajor3(transpose3(st.eqToScene))));
        sky.put("uSunApp", U.f(st.sunDir));
        sky.put("uSunTrue", U.f(st.sunDirTrue));
        sky.put("uSunE", U.f(st.sunToa));
        sky.put("uSunEye", U.f(st.sunColor));
        sky.put("uSunRadius", U.f(sunR));
        sky.put("uMoonTrue", U.f(st.moonDirTrue));
        sky.put("uMoonX", U.f(fr[0]));
        sky.put("uMoonY", U.f(fr[1]));
        sky.put("uMoonLight", U.f(ml));
        sky.put("uMoonK", U.f(cal[0], cal[1], cal[2]));
        sky.put("uEarthshine", U.f(cal[4], cal[5], cal[6]));
        sky.put("uMoonRadius", U.f(Math.toRadians(st.moonRadiusDeg)));
        sky.put("uMoonPeak", U.f(cal[3]));
        sky.put("uLibration", U.f(st.librationLon, st.librationLat));
        sky.put("uFlash", U.f(st.flashDir[0], st.flashDir[1], st.flashDir[2], st.flash));
        sky.put("uSkyView", U.i(0));
        sky.put("uTrans", U.i(1));
        sky.put("uMs", U.i(2));
        sky.put("uMilky", U.i(3));
        sky.put("uMoonTex", U.i(4));
        all.put("sky", sky);
        Map<String, U> pano = new LinkedHashMap<>(sky);
        pano.put("uSea", U.f(1));
        all.put("pano", pano);

        float[] eq = columnMajor3(st.eqToScene);
        Map<String, U> stars = new LinkedHashMap<>();
        stars.put("uAtm", uAtm);
        stars.put("uVP", new U("m4", v.viewProj != null ? v.viewProj : identity4()));
        stars.put("uEqToScene", new U("m3", eq));
        stars.put("uEyeR", U.f(eyeR));
        stars.put("uPre", U.f(v.preExposure));
        stars.put("uPixelSr", U.f(pixelAngle * pixelAngle));
        stars.put("uLimit", U.f(st.limitingMagnitude));
        stars.put("uGain", U.f(st.stars ? st.starGain : 0));
        stars.put("uTime", U.f(v.time % 1000));
        stars.put("uTwinkle", U.f(1));
        stars.put("uVeil", U.f(st.veil));
        stars.put("uMoonRadius", U.f(Math.toRadians(st.moonRadiusDeg)));
        stars.put("uMoonTrue", U.f(st.moonDirTrue));
        stars.put("uTrans", U.i(1));
        all.put("stars", stars);
        Map<String, U> planets = new LinkedHashMap<>(stars);
        planets.put("uGain", U.f(st.planetsOn ? 1 : 0));
        planets.put("uTwinkle", U.f(0.25));
        planets.put("uLimit", U.f(Math.max(st.limitingMagnitude, -1)));
        all.put("planets", planets);

        double fade = Math.max(0, Math.min(1, (-st.sunAlt - 4) / 6)) * (1 - st.veil);
        Map<String, U> lines = new LinkedHashMap<>();
        lines.put("uAtm", uAtm);
        lines.put("uVP", new U("m4", v.viewProj != null ? v.viewProj : identity4()));
        lines.put("uEqToScene", new U("m3", eq));
        lines.put("uEyeR", U.f(eyeR));
        double g = st.figures ? 0.09 * fade : 0;
        lines.put("uLineColor", U.f(0.32 * g, 0.52 * g, 1.0 * g));
        all.put("lines", lines);
        return all;
    }

    /** Eichung der Mondscheibe, nur neu, wenn sich Phase, Libration oder Helligkeit merklich ändern. */
    private double[] moonCalibration(Sky.State st) {
        String key = String.format(Locale.ROOT, "%.2f|%.2f|%.2f|%.4g|%.3f", st.moonPhaseAngle, st.librationLon, st.librationLat, st.moonToa[1],
                Math.toDegrees(Math.atan2(dot(st.moonLight, MoonDisc.frame(st.moonDirTrue, st.moonNorth)[0]), dot(st.moonLight, MoonDisc.frame(st.moonDirTrue, st.moonNorth)[1]))));
        if (moonCal == null || !key.equals(moonKey)) {
            moonCal = MoonDisc.calibrate(st);
            moonKey = key;
        }
        return moonCal;
    }

    static double sunDistance(Sky.State st) {
        // Bestrahlung außerhalb der Atmosphäre ∝ 1/r²
        return Math.sqrt(Sky.SUN_TOA / Math.max(1e-9, st.sunToa[1]));
    }

    // ------------------------------------------------------------------ je Bild

    /** Tabellen rechnen: Durchlässigkeit und Mehrfachstreuung bei neuer Atmosphäre, die Himmelsansicht immer. */
    public void update(GL4 gl, Sky.State st) {
        int[] prevFbo = new int[1], vp = new int[4];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, prevFbo, 0);
        gl.glGetIntegerv(GL.GL_VIEWPORT, vp, 0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vaoEmpty);
        Map<String, Map<String, U>> u = uniforms(st, new View());
        if (st.atmosphereVersion != builtVersion) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboTrans);
            gl.glViewport(0, 0, Scattering.TRANS_W, Scattering.TRANS_H);
            gl.glUseProgram(progTrans);
            bindLuts(gl, texTrans);
            apply(gl, progTrans, Map.of("uAtm", u.get("view").get("uAtm")));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboMs);
            gl.glViewport(0, 0, Scattering.MS_N, Scattering.MS_N);
            gl.glUseProgram(progMs);
            bindLuts(gl, texMs);
            apply(gl, progMs, Map.of("uAtm", u.get("view").get("uAtm"), "uTrans", U.i(1), "uMs", U.i(2)));
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            builtVersion = st.atmosphereVersion;
        }
        // Himmelsansicht nur neu, wenn sich etwas bewegt hat (Sonne und Mond um mehr als rund 0,005°, Licht, Schleier, Augenhöhe auf 1 m)
        String key = String.format(Locale.ROOT, "%d|%.4f|%.4f|%.4f|%.4f|%.4f|%.4f|%.3g|%.3g|%.3f|%.0f|%s", st.atmosphereVersion,
                st.sunDir[0], st.sunDir[1], st.sunDir[2], st.moonDir[0], st.moonDir[1], st.moonDir[2], st.sunToa[1], st.moonToa[1],
                st.veil, st.eyeHeight, java.util.Arrays.toString(st.domes));
        if (!key.equals(viewKey)) {
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboView);
            gl.glViewport(0, 0, SKYVIEW_W, SKYVIEW_H);
            gl.glUseProgram(progView);
            bindLuts(gl, texView);
            Map<String, U> m = new LinkedHashMap<>(u.get("view"));
            m.put("uTrans", U.i(1));
            m.put("uMs", U.i(2));
            apply(gl, progView, m);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            viewKey = key;
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, prevFbo[0]);
        gl.glViewport(vp[0], vp[1], vp[2], vp[3]);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
    }

    private void bindLuts(GL4 gl) { bindLuts(gl, 0); }

    /** Tabellen an die Einheiten 0..4 binden; das gerade beschriebene Ziel bleibt frei (keine Rückkopplung). */
    private void bindLuts(GL4 gl, int target) {
        int[] tex = {texView, texTrans, texMs, texMilky, texMoon};
        for (int i = 0; i < tex.length; i++) bind(gl, i, tex[i] == target ? 0 : tex[i]);
    }

    private static void bind(GL4 gl, int unit, int tex) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
    }

    /** Himmel, Planeten, Sterne und Figuren ins gebundene Ziel (ohne Tiefentest, ohne Tiefe zu schreiben). */
    public void draw(GL4 gl, Sky.State st, View v) {
        Map<String, Map<String, U>> u = uniforms(st, v);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vaoEmpty);
        gl.glUseProgram(progSky);
        bindLuts(gl);
        apply(gl, progSky, u.get("sky"));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE);
        gl.glEnable(GL4.GL_PROGRAM_POINT_SIZE);
        gl.glUseProgram(progStars);
        if (st.stars && st.limitingMagnitude > -1) {
            apply(gl, progStars, u.get("stars"));
            gl.glBindVertexArray(vaoStars);
            gl.glDrawArrays(GL.GL_POINTS, 0, starCount);
        }
        if (st.planetsOn) {
            float[] pv = planetVertices(st);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboPlanets);
            gl.glBufferSubData(GL.GL_ARRAY_BUFFER, 0, (long) pv.length * 4, Buffers.newDirectFloatBuffer(pv));
            apply(gl, progStars, u.get("planets"));
            gl.glBindVertexArray(vaoPlanets);
            gl.glDrawArrays(GL.GL_POINTS, 0, pv.length / 7);
        }
        gl.glDisable(GL4.GL_PROGRAM_POINT_SIZE);
        if (st.figures) {
            gl.glUseProgram(progLines);
            apply(gl, progLines, u.get("lines"));
            gl.glBindVertexArray(vaoLines);
            gl.glDrawArrays(GL.GL_LINES, 0, lineVerts);
        }
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /** Rundumbild für Spiegelungen ins gebundene Ziel (ganzer Viewport): ohne Scheiben und Sterne. */
    public void drawPanorama(GL4 gl, Sky.State st, double preExposure) {
        View v = new View();
        v.preExposure = preExposure;
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vaoEmpty);
        gl.glUseProgram(progPano);
        bindLuts(gl);
        apply(gl, progPano, uniforms(st, v).get("pano"));
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    /**
     * Hilfe für Szenen ohne eigene Nachbearbeitung: bringt ein HDR-Bild (vorbelichtet) mit Restbelichtung,
     * Stäbchensehen ({@link Sky.State#scotopic()}), Filmkurve und Gamma in das gebundene Ziel.
     */
    public void toneMap(GL4 gl, int hdrTexture, double exposure, double scotopic) {
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(progPost);
        bind(gl, 0, hdrTexture);
        gl.glUniform1i(gl.glGetUniformLocation(progPost, "uHdr"), 0);
        gl.glUniform1f(gl.glGetUniformLocation(progPost, "uExposure"), (float) exposure);
        gl.glUniform1f(gl.glGetUniformLocation(progPost, "uScotopic"), (float) scotopic);
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    static void apply(GL4 gl, int prog, Map<String, U> m) {
        for (Map.Entry<String, U> e : m.entrySet()) {
            int loc = gl.glGetUniformLocation(prog, e.getKey());
            if (loc < 0) continue;
            float[] v = e.getValue().v();
            switch (e.getValue().kind()) {
                case "i" -> gl.glUniform1i(loc, (int) v[0]);
                case "m3" -> gl.glUniformMatrix3fv(loc, 1, false, v, 0);
                case "m4" -> gl.glUniformMatrix4fv(loc, 1, false, v, 0);
                case "v4" -> gl.glUniform4fv(loc, v.length / 4, v, 0);
                default -> {
                    switch (v.length) {
                        case 1 -> gl.glUniform1f(loc, v[0]);
                        case 2 -> gl.glUniform2f(loc, v[0], v[1]);
                        case 3 -> gl.glUniform3f(loc, v[0], v[1], v[2]);
                        default -> gl.glUniform4f(loc, v[0], v[1], v[2], v[3]);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ Selbsttest

    /**
     * Liest die Tabellen der Grafikkarte zurück und vergleicht mit dem Prozessor: Durchlässigkeit und
     * Mehrfachstreuung Texel für Texel, die Himmelsansicht an 64 Richtungen gegen {@link Sky.State#radiance}.
     * Ergebnis: „Grafikkarte = Prozessor: Himmel ± 0,8 %, Durchlässigkeit ± 0,02 %“.
     */
    public String selfCheck(GL4 gl, Sky.State st) {
        float[] t = read(gl, texTrans, Scattering.TRANS_W, Scattering.TRANS_H);
        float[] ms = read(gl, texMs, Scattering.MS_N, Scattering.MS_N);
        float[] sv = read(gl, texView, SKYVIEW_W, SKYVIEW_H);
        return compare(st, t, ms, sv);
    }

    /** Vergleich der zurückgelesenen Tabellen (RGBA je Texel) mit dem Prozessor (auch für Werkzeuge ohne JOGL). */
    public static String compare(Sky.State st, float[] t, float[] ms, float[] sv) {
        Scattering sc = st.scattering();
        float[] ct = sc.transmittanceTable(), cm = sc.multiScatteringTable();
        double wt = 0, wm = 0;
        for (int i = 0; i < ct.length / 3; i++)
            for (int c = 0; c < 3; c++)
                wt = Math.max(wt, Math.abs(t[i * 4 + c] - ct[i * 3 + c]) / Math.max(1e-3, ct[i * 3 + c]));
        // Mehrfachstreuung bezogen auf den größten Wert je Kanal: im tiefen Erdschatten sind die Werte 10⁻¹⁰ klein
        double[] top = new double[3];
        for (int i = 0; i < cm.length / 3; i++) for (int c = 0; c < 3; c++) top[c] = Math.max(top[c], Math.abs(cm[i * 3 + c]));
        for (int i = 0; i < cm.length / 3; i++)
            for (int c = 0; c < 3; c++)
                wm = Math.max(wm, Math.abs(ms[i * 4 + c] - cm[i * 3 + c]) / Math.max(1e-30, top[c]));
        double ws = 0;
        double r = Atmosphere.BOTTOM + st.eyeHeight;
        for (int k = 0; k < 64; k++) {
            int ix = (k * 37) % SKYVIEW_W, iy = 4 + (k * 13) % (SKYVIEW_H / 2 - 8);
            double[] d = skyViewDir((ix + 0.5) / SKYVIEW_W, (iy + 0.5) / SKYVIEW_H, r);
            double[] cpu = st.radiance(d);
            int o = (iy * SKYVIEW_W + ix) * 4;
            double lc = 0.2126 * cpu[0] + 0.7152 * cpu[1] + 0.0722 * cpu[2];
            double lg = 0.2126 * sv[o] + 0.7152 * sv[o + 1] + 0.0722 * sv[o + 2];
            ws = Math.max(ws, Math.abs(lg - lc) / Math.max(1e-30, lc));
        }
        return String.format(Locale.GERMAN, "Grafikkarte = Prozessor: Himmel ± %.1f %%, Durchlässigkeit ± %.2f %%, Mehrfachstreuung ± %.1f %%",
                ws * 100, wt * 100, wm * 100);
    }

    /** Wie {@code skyViewDir} in sky_view.glsl. */
    public static double[] skyViewDir(double u, double v, double r) {
        double vh = Math.sqrt(Math.max(0, r * r - Atmosphere.BOTTOM * Atmosphere.BOTTOM));
        double beta = Math.acos(Math.max(-1, Math.min(1, vh / r))), zh = Math.PI - beta, zen;
        if (v < 0.5) {
            double c = 1 - 2 * v;
            zen = zh * (1 - c * c);
        } else {
            double c = v * 2 - 1;
            zen = zh + beta * c * c;
        }
        double az = u * 2 * Math.PI;
        return new double[]{Math.sin(zen) * Math.sin(az), Math.cos(zen), -Math.sin(zen) * Math.cos(az)};
    }

    private static float[] read(GL4 gl, int tex, int w, int h) {
        FloatBuffer fb = Buffers.newDirectFloatBuffer(w * h * 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_FLOAT, fb);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        float[] out = new float[w * h * 4];
        fb.get(out);
        return out;
    }

    // ------------------------------------------------------------------ Texturen

    private static int texture(GL4 gl, int w, int h, int fmt, boolean wrapS) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, fmt, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, wrapS ? GL.GL_REPEAT : GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private static int fbo(GL4 gl, int tex) {
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        int s = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        if (s != GL.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("sky: Zielpuffer unvollständig (0x" + Integer.toHexString(s) + ")");
        return f[0];
    }

    /** Milchstraße (Helligkeitsstufen aus d3-celestial, BSD) als Graustufen über RA/Dec J2000. */
    private static int milkyWayTexture(GL4 gl) {
        BufferedImage img;
        try (InputStream in = Sky.class.getResourceAsStream("milkyway.png")) {
            if (in == null) throw new IOException("milkyway.png fehlt");
            img = ImageIO.read(in);
        } catch (IOException e) {
            throw new IllegalStateException("sky: " + e.getMessage(), e);
        }
        int w = img.getWidth(), h = img.getHeight();
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h);
        // seit Glacier Phase 3 mit den Magellanschen Wolken (fehlen in der Karte); die Karte ist im Shader quadriert,
        // darum die Wurzel der Helligkeit
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                double ra = (x + 0.5) / w * 360, dec = 90 - (y + 0.5) / h * 180;
                double m = img.getRaster().getSample(x, y, 0) / 255.0, mc = StarCatalog.magellanic(ra, dec);
                double v = Math.sqrt(m * m + mc * mc);
                bb.put((byte) (int) Math.round(Math.min(1, v) * 255));
            }
        bb.flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R8, w, h, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, bb);
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    private static int moonTexture(GL4 gl) {
        float[] a = MoonDisc.albedoTable();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R16F, MoonDisc.TEX_W, MoonDisc.TEX_H, 0, GL4.GL_RED, GL.GL_FLOAT, Buffers.newDirectFloatBuffer(a));
        gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return t[0];
    }

    // ------------------------------------------------------------------ Hilfen

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }

    static double[] transpose3(double[] m) { return new double[]{m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]}; }

    /** Zeilenweise double[9] → spaltenweise float[9] für OpenGL. */
    static float[] columnMajor3(double[] m) {
        return new float[]{(float) m[0], (float) m[3], (float) m[6], (float) m[1], (float) m[4], (float) m[7], (float) m[2], (float) m[5], (float) m[8]};
    }

    static float[] identity4() { return new float[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        for (int p : new int[]{progTrans, progMs, progView, progSky, progPano, progStars, progLines, progPost}) gl.glDeleteProgram(p);
        gl.glDeleteFramebuffers(3, new int[]{fboTrans, fboMs, fboView}, 0);
        gl.glDeleteTextures(5, new int[]{texTrans, texMs, texView, texMilky, texMoon}, 0);
        gl.glDeleteBuffers(3, new int[]{vboStars, vboPlanets, vboLines}, 0);
        gl.glDeleteVertexArrays(4, new int[]{vaoEmpty, vaoStars, vaoPlanets, vaoLines}, 0);
        ready = false;
    }
}
