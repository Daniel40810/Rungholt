package com.dan.mudflat.gl;

import com.dan.mudflat.Mudflat;
import com.dan.mudflat.WetField;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Die Grafikseite des Watts (Phase 5): lädt den Zustand eines {@link WetField} als Textur RGBA8 (nur wenn er sich
 * geändert hat) und setzt die Werte für den Baustein {@code mudflat.glsl}, den die App in ihren Pixelshader einsetzt
 * ({@code #include "/com/dan/mudflat/gl/mudflat.glsl"} oder {@link #glsl()}). Zeichnet nicht selbst: das Watt ist ein
 * Material auf dem Gelände der App.
 */
public final class MudflatGl {
    /** Der Baustein als Text (für Apps ohne eigenes #include). */
    public static String glsl() {
        try (InputStream in = MudflatGl.class.getResourceAsStream("mudflat.glsl")) {
            if (in == null) throw new IllegalStateException("mudflat.glsl fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private int tex, texW, texH, uploaded = -1;
    private ByteBuffer buf;
    private byte[] bytes;
    private double uploadMs;
    /** Rippel 0..1 (Bedienfeld), Startwert für Haufen und Flecken. */
    public volatile double ripples = 1;
    public volatile int seed = 1362;
    /**
     * Kürzester Abstand zweier Hochladungen (ms): die Nässe ändert sich langsam (Befund Rungholt Status_Phase5: 2 ms je
     * Hochladen von 1024² alle 100 ms). 0 = jede neue Fassung.
     */
    public volatile int minIntervalMs = 400;
    private long lastUpload;
    /** Zeit für das ablaufende Wasser in den Rinnsalen (s, Phase 6). */
    public volatile double time;

    /** Zustand hochladen, wenn das Feld eine neue Fassung hat (im GL-Faden). */
    private WetField lastField;

    public void upload(GL4 gl, WetField f) {
        if (f == null) return;
        if (tex == 0 || texW != f.nx || texH != f.nz) {
            dispose(gl);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, f.nx, f.nz);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            texW = f.nx;
            texH = f.nz;
            bytes = new byte[f.nx * f.nz * 4];
            buf = ByteBuffer.allocateDirect(bytes.length);
            uploaded = -1;
        }
        if (f != lastField) { uploaded = -1; lastField = f; }   // ein anderes Feld (etwa die Welt von 1362)
        int v = f.version;
        if (v == uploaded) return;
        long t0 = System.nanoTime();
        if (uploaded >= 0 && (t0 - lastUpload) / 1_000_000 < minIntervalMs) return;
        lastUpload = t0;
        f.fill(bytes);
        buf.clear();
        buf.put(bytes).flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, f.nx, f.nz, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, buf);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        uploaded = v;
        uploadMs = uploadMs * 0.9 + (System.nanoTime() - t0) / 1e6 * 0.1;
    }

    /**
     * Die Werte des Bausteins im laufenden Programm setzen: Textur auf {@code unit}, Rechteck des Felds, Rippel,
     * Abdunkeln bei Nässe, Wattwurmhaufen je m², Startwert. Ohne Feld (null) gilt die Nässe aus der Höhe.
     */
    public void bind(GL4 gl, int program, int unit, WetField f, Mudflat m) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, f != null ? tex : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uMudState"), unit);
        if (f != null && tex != 0) gl.glUniform4f(gl.glGetUniformLocation(program, "uMudRect"), (float) f.x0, (float) f.z0, (float) f.cell, 1f);
        else gl.glUniform4f(gl.glGetUniformLocation(program, "uMudRect"), 0, 0, 1, 0);
        gl.glUniform4f(gl.glGetUniformLocation(program, "uMudParams"), (float) ripples, (float) m.wetDarkening, (float) m.lugwormPerM2, (float) (seed % 1000));
        gl.glUniform1f(gl.glGetUniformLocation(program, "uMudTime"), (float) (time % 3600));
    }

    /** Die Werte der Feinform ({@code mudflat_relief.glsl}) setzen (Phase 6). */
    public static void bindRelief(GL4 gl, int program, com.dan.mudflat.Relief r) {
        float[] u = r.uniform();
        gl.glUniform4f(gl.glGetUniformLocation(program, "uMudRelief"), u[0], u[1], u[2], u[3]);
    }

    /** Der Baustein der Feinform als Text. */
    public static String reliefGlsl() {
        try (InputStream in = MudflatGl.class.getResourceAsStream("mudflat_relief.glsl")) {
            if (in == null) throw new IllegalStateException("mudflat_relief.glsl fehlt im Klassenpfad");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    // ------------------------------------------------------------------ Phase 7: Spuren

    private int trackTex, trackN, trackUploaded = -1;
    private ByteBuffer trackBuf;
    private double[] trackRect = {0, 0, 1, 0};

    /** Das Raster der Spuren hochladen, wenn es neu gezeichnet wurde (im GL-Faden). */
    public void uploadTracks(GL4 gl, com.dan.mudflat.TrackMap t) {
        if (t == null) { trackRect = new double[]{0, 0, 1, 0}; return; }
        if (trackTex == 0 || trackN != t.n) {
            if (trackTex != 0) gl.glDeleteTextures(1, new int[]{trackTex}, 0);
            int[] a = new int[1];
            gl.glGenTextures(1, a, 0);
            trackTex = a[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, trackTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R8, t.n, t.n);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            trackN = t.n;
            trackBuf = ByteBuffer.allocateDirect(t.n * t.n);
            trackUploaded = -1;
        }
        int v = t.version;
        if (v != trackUploaded) {
            trackBuf.clear();
            trackBuf.put(t.depth).flip();
            gl.glBindTexture(GL.GL_TEXTURE_2D, trackTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, t.n, t.n, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, trackBuf);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            trackUploaded = v;
        }
        trackRect = new double[]{t.x0, t.z0, t.size(), t.drawn > 0 ? 1 : 0};
    }

    /** Die Spuren im laufenden Programm auf {@code unit} setzen (ohne {@link #uploadTracks}: aus). */
    public void bindTracks(GL4 gl, int program, int unit) {
        gl.glActiveTexture(GL.GL_TEXTURE0 + unit);
        gl.glBindTexture(GL.GL_TEXTURE_2D, trackTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glUniform1i(gl.glGetUniformLocation(program, "uMudTracks"), unit);
        gl.glUniform4f(gl.glGetUniformLocation(program, "uMudTrackRect"), (float) trackRect[0], (float) trackRect[1], (float) trackRect[2], trackTex == 0 ? 0f : (float) trackRect[3]);
    }

    public int texture() { return tex; }

    /** „Watt-Textur 1024² · 0,6 ms je Hochladen“ */
    public String info() { return tex == 0 ? "Watt-Textur: noch keine" : String.format(Locale.GERMAN, "Watt-Textur %d×%d · %.2f ms je Hochladen", texW, texH, uploadMs); }

    public void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        if (trackTex != 0) gl.glDeleteTextures(1, new int[]{trackTex}, 0);
        tex = 0;
        trackTex = 0;
    }
}
