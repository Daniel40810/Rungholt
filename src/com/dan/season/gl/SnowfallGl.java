package com.dan.season.gl;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Fallender Schnee als Flocken um das Auge: ein Würfel von 36 m Kante wandert mit dem Auge, jede Flocke hat aus ihrer
 * Nummer einen festen Ort darin, sinkt mit 1 m/s (Schneeflocken, Locatelli und Hobbs 1974: 0,5 bis 1,5 m/s), taumelt
 * und treibt mit dem Wind; am Rand des Würfels kommt sie auf der anderen Seite wieder herein. Ohne Puffer: Ort und
 * Größe kommen im Vertex-Shader aus der Nummer der Ecke. Zahl der Flocken nach der Stärke des Schneefalls
 * (Schneewasserwert in mm/h): 3 mm/h ist dichtes Treiben, die Sicht sinkt dann unter einen Kilometer.
 * <p>
 * Die Flocken sind kleine, weiche Scheiben (Albedo 0,8), vom Himmel und etwas von Sonne und Mond beleuchtet; in der
 * Ferne mindestens 1,5 Pixel groß und entsprechend durchsichtiger, damit nichts flimmert.
 */
public final class SnowfallGl {
    /** Kante des Würfels um das Auge (m), Sinkgeschwindigkeit (m/s), größte Zahl der Flocken. */
    public static final double BOX = 36, FALL = 1.0;
    public static final int MAX_FLAKES = 120_000;

    private int prog, vao;
    private boolean ready;
    private String info = "";
    private int drawn;

    public boolean ready() { return ready; }
    public String info() { return info; }

    public void init(GL4 gl) {
        prog = SeasonGl.program(gl, "snowfall.vert", "snowfall.frag");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        ready = true;
    }

    /** Zahl der Flocken bei rate mm/h (Schneewasserwert). */
    public static int flakes(double rate) {
        if (rate <= 0.01) return 0;
        return (int) Math.min(MAX_FLAKES, MAX_FLAKES * Math.pow(Math.min(1, rate / 3), 0.7));
    }

    /**
     * Zeichnet in den gebundenen Bildspeicher (Tiefentest an, kein Schreiben der Tiefe, gemischt). rate: Schneefall in
     * mm/h, windTo: Richtung, in die der Wind weht (x, z), windSpeed in m/s, t Laufzeit in s.
     */
    public void draw(GL4 gl, SeasonGl.View v, SeasonGl.Light l, double rate, double[] windTo, double windSpeed, double t) {
        int n = flakes(rate);
        drawn = n;
        if (!ready || n == 0) { info = "kein Schneefall"; return; }
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(SeasonGl.loc(gl, prog, "uVP"), 1, false, v.viewProj, 0);
        SeasonGl.set3(gl, prog, "uEye", v.eye);
        gl.glUniform1f(SeasonGl.loc(gl, prog, "uTime"), (float) (t % 7200));
        double l2 = Math.hypot(windTo[0], windTo[windTo.length - 1]);
        double wx = l2 > 1e-6 ? windTo[0] / l2 : 1, wz = l2 > 1e-6 ? windTo[windTo.length - 1] / l2 : 0;
        // Wind in Augenhöhe, Flocken folgen ihm fast ganz
        gl.glUniform3f(SeasonGl.loc(gl, prog, "uWind"), (float) (wx * windSpeed * 0.7), 0f, (float) (wz * windSpeed * 0.7));
        gl.glUniform2f(SeasonGl.loc(gl, prog, "uBox"), (float) BOX, (float) FALL);
        double pxPerRad = v.height / (2 * Math.tan(Math.toRadians(v.fovyDeg) / 2));
        gl.glUniform1f(SeasonGl.loc(gl, prog, "uPx"), (float) pxPerRad);
        SeasonGl.set3(gl, prog, "uSunDir", l.sunDir);
        SeasonGl.set3(gl, prog, "uSunColor", l.sunColor);
        SeasonGl.set3(gl, prog, "uMoonDir", l.moonDir);
        SeasonGl.set3(gl, prog, "uMoonColor", l.moonColor);
        SeasonGl.set3(gl, prog, "uSky", l.sky);
        SeasonGl.set3(gl, prog, "uFogColor", l.fogColor);
        gl.glUniform1f(SeasonGl.loc(gl, prog, "uFogDist"), (float) l.fogDistance);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, n * 6);
        gl.glBindVertexArray(0);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        info = String.format(Locale.GERMAN, "%,d Flocken (%.1f mm/h)", n, rate);
    }

    public int drawn() { return drawn; }

    public void dispose(GL4 gl) {
        if (!ready) return;
        gl.glDeleteProgram(prog);
        gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        ready = false;
    }
}
