package com.dan.season.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Eis auf dem Noor: eine Fläche knapp über dem Wasserspiegel über dem ganzen Grund; ein Bildpunkt trägt Eis, wo das
 * Wasser flacher ist als die Eisgrenze ({@code Year.State#iceDepth}), am Rand ausgefranst. Neues, dünnes Eis am Rand
 * ist dunkel und glatt (Schwarzeis, man sieht das Wasser), dickes Eis grauweiß mit Rissen und Lufteinschlüssen; Schnee
 * liegt in Fahnen darauf, vom Wind verweht. Licht wie die Flächen der App, glatter Glanz zum Himmel (Fresnel).
 * <p>
 * Die Höhe des Grunds kommt als Textur (Wert · Maßstab + Versatz über einem Rechteck), wie beim Noor in
 * {@code com.dan.ocean}.
 */
public final class IceGl {
    /** Abstand der Eisfläche über dem Wasserspiegel (m): genug gegen kleine Wellen unter dem Eis. */
    public static final double LIFT = 0.04;

    /** Was das Eis braucht: Grund, Wasserspiegel und der Stand aus {@code com.dan.season}. */
    public static final class Input {
        public int bedTexture;
        /** x, z der Nordwestecke, Kantenlänge (m); Höhe = Wert · bedScale + bedOffset. */
        public double[] bedRect = {0, 0, 1};
        public double bedScale = 1, bedOffset = 0;
        public double waterLevel;
        /** Eisdicke (m), Grenze als Wassertiefe (m), Schnee auf dem Eis 0..1, Reif 0..1. */
        public double thickness, iceDepth, snow, frost;
        /** Richtung, in die der Wind weht (x, z), für die Schneefahnen. */
        public double[] windTo = {1, 0};
        /** Seit Glacier Phase 11: Eis nur im Rechteck {x0, z0, x1, z1} (etwa ein Seearm); null: überall. */
        public double[] clip;
    }

    private int prog, vao;
    private boolean ready;
    private String info = "";

    public boolean ready() { return ready; }
    public String info() { return info; }

    public void init(GL4 gl) {
        prog = SeasonGl.program(gl, "ice.vert", "ice.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
    }

    /** Liegt Eis? */
    public static boolean any(Input in) { return in != null && in.thickness >= 0.005 && in.iceDepth > 0.05; }

    /** Zeichnet in den gebundenen Bildspeicher (nach dem Wasser, Tiefentest an, gemischt). */
    public void draw(GL4 gl, SeasonGl.View v, SeasonGl.Light l, Input in) {
        if (!ready || !any(in) || in.bedTexture == 0) { info = "kein Eis"; return; }
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(SeasonGl.loc(gl, prog, "uVP"), 1, false, v.viewProj, 0);
        SeasonGl.set3(gl, prog, "uEye", v.eye);
        gl.glUniform4f(SeasonGl.loc(gl, prog, "uRect"), (float) in.bedRect[0], (float) in.bedRect[1], (float) in.bedRect[2], (float) (in.waterLevel + LIFT));
        gl.glUniform3f(SeasonGl.loc(gl, prog, "uBedMap"), (float) in.bedScale, (float) in.bedOffset, (float) in.waterLevel);
        double[] cl = in.clip;
        if (cl != null && cl.length == 4) gl.glUniform4f(SeasonGl.loc(gl, prog, "uClip"), (float) cl[0], (float) cl[1], (float) cl[2], (float) cl[3]);
        else gl.glUniform4f(SeasonGl.loc(gl, prog, "uClip"), 0, 0, 0, 0);
        gl.glUniform4f(SeasonGl.loc(gl, prog, "uIce"), (float) in.thickness, (float) in.iceDepth, (float) in.snow, (float) in.frost);
        double wl = Math.hypot(in.windTo[0], in.windTo[1]);
        gl.glUniform2f(SeasonGl.loc(gl, prog, "uWindTo"), (float) (wl > 1e-6 ? in.windTo[0] / wl : 1), (float) (wl > 1e-6 ? in.windTo[1] / wl : 0));
        SeasonGl.set3(gl, prog, "uSunDir", l.sunDir);
        SeasonGl.set3(gl, prog, "uSunColor", l.sunColor);
        SeasonGl.set3(gl, prog, "uMoonDir", l.moonDir);
        SeasonGl.set3(gl, prog, "uMoonColor", l.moonColor);
        SeasonGl.set3(gl, prog, "uSky", l.sky);
        SeasonGl.set3(gl, prog, "uFogColor", l.fogColor);
        gl.glUniform1f(SeasonGl.loc(gl, prog, "uFogDist"), (float) l.fogDistance);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, in.bedTexture);
        gl.glUniform1i(SeasonGl.loc(gl, prog, "uBed"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL.GL_TEXTURE_2D, l.cloudShadow);
        gl.glUniform1i(SeasonGl.loc(gl, prog, "uCloudShadow"), 1);
        gl.glUniform4f(SeasonGl.loc(gl, prog, "uCloudRect"), (float) l.cloudRect[0], (float) l.cloudRect[1], (float) Math.max(1, l.cloudRect[2]),
                l.cloudShadow != 0 ? 1f : 0f);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glDepthMask(true);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        info = String.format(Locale.GERMAN, "Eis %.0f cm bis %.1f m Wassertiefe, Schnee darauf %.0f %%", in.thickness * 100, in.iceDepth, in.snow * 100);
    }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ready = false;
    }
}
