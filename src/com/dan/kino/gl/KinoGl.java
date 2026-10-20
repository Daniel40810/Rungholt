package com.dan.kino.gl;

import com.dan.kino.Kino;
import com.dan.kino.KinoMaler;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Der Kinomodus auf der Grafikkarte (OpenGL 4.3, JOGL). Zwei Aufrufe im Bildlauf der App, nach der Nachbearbeitung:
 * <pre>
 *   if (kino.schnittFaellig()) { kinoGl.merken(gl, fbo, w, h); schneiden(); kino.geschnitten(); }
 *   kinoGl.zeichnen(gl, fbo, w, h, sekunden);
 * </pre>
 * {@code merken} kopiert das fertige Bild (auch aus einem Rahmenpuffer mit Kantenglättung) in eine eigene Textur;
 * {@code zeichnen} legt darüber: das gemerkte Bild der Überblendung, Randabdunklung und Korn, das Schwarz, die Balken und
 * die Texttafel. Die Tafel malt {@link KinoMaler} in einem eigenen Faden, nur wenn sich ihr Text ändert; die Deckkraft
 * kommt im Shader dazu. Nichts davon kennt die Szene.
 */
public final class KinoGl {
    private final Kino kino;
    private final KinoMaler maler;
    private int prog, vao, merkFbo, merkTex, merkW, merkH, textTex, textW, textH;
    private boolean gemerkt;
    private int textNummer = -1, textFuerW, textFuerH;
    private volatile boolean malt;
    private volatile Fertig fertig;
    private record Fertig(ByteBuffer px, int w, int h, int nummer) { }
    private static final ExecutorService MALER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Kino-Tafel");
        t.setDaemon(true);
        return t;
    });

    public KinoGl(Kino kino, KinoMaler maler) {
        this.kino = kino;
        this.maler = maler != null ? maler : new KinoMaler();
    }

    /** Shader und Puffer anlegen (einmal, im Bildlauf). */
    public void init(GL4 gl) {
        prog = program(gl, "#version 430 core\n" + read("kino.vert"), "#version 430 core\n" + read("kino.frag"));
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vao = a[0];
    }

    public boolean bereit() { return prog != 0; }

    /** Das fertige Bild im Rahmenpuffer fbo (w × h) für die Überblendung merken. */
    public void merken(GL4 gl, int fbo, int w, int h) {
        if (prog == 0) return;
        if (merkTex == 0 || w != merkW || h != merkH) {
            if (merkTex != 0) { gl.glDeleteTextures(1, new int[]{merkTex}, 0); gl.glDeleteFramebuffers(1, new int[]{merkFbo}, 0); }
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            merkTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, merkTex);
            gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, w, h);
            filter(gl);
            gl.glGenFramebuffers(1, t, 0);
            merkFbo = t[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, merkFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, merkTex, 0);
            merkW = w;
            merkH = h;
        }
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, fbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, merkFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_COLOR_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gemerkt = true;
    }

    /** Alles, was der Kinomodus jetzt über das Bild legt; zeit in Sekunden (fürs Korn). */
    public void zeichnen(GL4 gl, int fbo, int w, int h, double zeit) {
        if (prog == 0 || !kino.sichtbar()) { holeText(gl, null, w, h); return; }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glUseProgram(prog);
        gl.glBindVertexArray(vao);
        uni(gl, "uSize", w, h);
        uni(gl, "uZeit", (float) zeit);
        // 1 die Überblendung: das Bild vor dem Schnitt, abnehmend
        double ub = kino.ueberblende();
        if (ub > 0 && gemerkt && merkW == w && merkH == h) textur(gl, merkTex, 0, (float) ub);
        // 2 Film: Randabdunklung und Korn
        double film = kino.film();
        if (film > 0) { uni(gl, "uMode", 1); uni(gl, "uGain", (float) film); gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3); }
        // 3 Schwarz (Vorspann, Abspann)
        double s = kino.schwarz();
        if (s > 0) { uni(gl, "uMode", 2); uni(gl, "uColor", 0, 0, 0); uni(gl, "uGain", (float) s); gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3); }
        // 4 Balken
        int bar = kino.balkenPx(w, h);
        if (bar > 0) {
            gl.glEnable(GL.GL_SCISSOR_TEST);
            gl.glClearColor(0, 0, 0, 1);
            gl.glScissor(0, 0, w, bar);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            gl.glScissor(0, h - bar, w, bar);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            gl.glDisable(GL.GL_SCISSOR_TEST);
        }
        // 5 die Texttafel
        Kino.Tafel t = kino.tafel();
        holeText(gl, t, w, h);
        if (t != null && textTex != 0 && textNummer == t.nummer() && textFuerW == w && textFuerH == h) textur(gl, textTex, 1, (float) t.deckkraft());
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
    }

    private void textur(GL4 gl, int tex, int flip, float gain) {
        uni(gl, "uMode", 0);
        uni(gl, "uFlipY", flip);
        uni(gl, "uGain", gain);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        uni(gl, "uTex", 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
    }

    /** Neue Tafel im Hintergrund malen lassen; ein fertiges Bild hochladen. */
    private void holeText(GL4 gl, Kino.Tafel t, int w, int h) {
        Fertig f = fertig;
        if (f != null) {
            fertig = null;
            if (textTex == 0 || f.w() != textW || f.h() != textH) {
                if (textTex != 0) gl.glDeleteTextures(1, new int[]{textTex}, 0);
                int[] a = new int[1];
                gl.glGenTextures(1, a, 0);
                textTex = a[0];
                gl.glBindTexture(GL.GL_TEXTURE_2D, textTex);
                gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA8, f.w(), f.h());
                filter(gl);
                textW = f.w();
                textH = f.h();
            } else gl.glBindTexture(GL.GL_TEXTURE_2D, textTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, f.w(), f.h(), GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, f.px());
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            textNummer = f.nummer();
            textFuerW = f.w();
            textFuerH = f.h();
        }
        if (t == null || malt) return;
        if (t.nummer() == textNummer && w == textFuerW && h == textFuerH) return;
        malt = true;
        Kino.Tafel tt = t;
        MALER.execute(() -> {
            try {
                BufferedImage im = maler.malen(tt, w, h);
                fertig = new Fertig(pixel(im), w, h, tt.nummer());
            } catch (RuntimeException e) {
                System.err.println("Kino: Tafel nicht gemalt: " + e);
            } finally {
                malt = false;
            }
        });
    }

    public void dispose(GL4 gl) {
        if (merkTex != 0) gl.glDeleteTextures(1, new int[]{merkTex}, 0);
        if (merkFbo != 0) gl.glDeleteFramebuffers(1, new int[]{merkFbo}, 0);
        if (textTex != 0) gl.glDeleteTextures(1, new int[]{textTex}, 0);
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (prog != 0) gl.glDeleteProgram(prog);
        merkTex = merkFbo = textTex = vao = prog = 0;
    }

    // ------------------------------------------------------------------ Hilfen

    /** Bild in vormultiplizierte RGBA-Bytes, Zeile 0 oben (darum uFlipY = 1). */
    static ByteBuffer pixel(BufferedImage im) {
        int w = im.getWidth(), h = im.getHeight();
        int[] px = im.getRGB(0, 0, w, h, null, 0, w);
        ByteBuffer b = Buffers.newDirectByteBuffer(w * h * 4);
        for (int p : px) {
            int a = p >>> 24;
            b.put((byte) (((p >> 16) & 255) * a / 255)).put((byte) (((p >> 8) & 255) * a / 255)).put((byte) ((p & 255) * a / 255)).put((byte) a);
        }
        b.flip();
        return b;
    }

    private static void filter(GL4 gl) {
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    }

    private void uni(GL4 gl, String n, int v) { gl.glUniform1i(gl.glGetUniformLocation(prog, n), v); }
    private void uni(GL4 gl, String n, float v) { gl.glUniform1f(gl.glGetUniformLocation(prog, n), v); }
    private void uni(GL4 gl, String n, float a, float b) { gl.glUniform2f(gl.glGetUniformLocation(prog, n), a, b); }
    private void uni(GL4 gl, String n, float a, float b, float c) { gl.glUniform3f(gl.glGetUniformLocation(prog, n), a, b, c); }

    static String read(String file) {
        try (InputStream in = KinoGl.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("kino: Shader-Datei fehlt: " + file);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("kino: " + file + ": " + e.getMessage(), e);
        }
    }

    static int program(GL4 gl, String vert, String frag) {
        int p = gl.glCreateProgram();
        int vs = stage(gl, GL4.GL_VERTEX_SHADER, vert, "kino.vert"), fs = stage(gl, GL4.GL_FRAGMENT_SHADER, frag, "kino.frag");
        gl.glAttachShader(p, vs);
        gl.glAttachShader(p, fs);
        gl.glLinkProgram(p);
        gl.glDeleteShader(vs);
        gl.glDeleteShader(fs);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL4.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, len, 0, b, 0);
            throw new IllegalStateException("kino: Programm lässt sich nicht binden:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return p;
    }

    static int stage(GL4 gl, int type, String src, String name) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
            gl.glDeleteShader(s);
            throw new IllegalStateException("kino: " + name + " lässt sich nicht übersetzen:\n" + new String(b, 0, Math.max(0, len[0]), StandardCharsets.UTF_8));
        }
        return s;
    }
}
