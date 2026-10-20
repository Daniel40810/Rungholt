package com.dan.wave.gl;

import com.dan.wave.Wave;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.ShortBuffer;
import java.nio.FloatBuffer;

/**
 * Die Welle als Texturen für fremde Oberflächen (Paketvertrag: {@code gl/} braucht JOGL, der Kern nicht): je Gitter
 * der Wasserstand als R16F (m über dem Ruhespiegel, trocken {@link Wave#DRY}) und die Zeit des letzten Nasswerdens
 * an Land als R32F. Hochgeladen wird nur das Fenster, in dem sich etwas bewegt hat; das grobe Gitter höchstens jedes
 * zweite Bild, die Nässe alle halbe Sekunde.
 * <pre>
 *   WaveGl wg = new WaveGl();
 *   wg.update(gl, wave);                 // je Bild im GL-Faden
 *   wg.heightTexture(0), wg.rect(0)      // fein: Textur, {x0, z0, Zelle, an}; 1: grob
 * </pre>
 * Abtasten im Shader mit texelFetch und eigener Gewichtung: trockene Zellen zählen nicht (sonst zöge die Filterung
 * den Spiegel am Ufer um Meter hinunter).
 */
public final class WaveGl {
    private final int[] height = new int[2], wet = new int[2], nx = new int[2], nz = new int[2];
    private final double[][] rect = new double[2][];
    private ShortBuffer[] half = new ShortBuffer[2];
    private FloatBuffer[] wetBuf = new FloatBuffer[2];
    private long version = -1, frame;
    private double lastWet = -1;
    private float time;
    private long uploadedBytes;

    /** Je Bild: neuen Stand hochladen, wenn es einen gibt. */
    public void update(GL4 gl, Wave w) {
        frame++;
        Wave.Snapshot s = w.snapshot();
        if (s == null || s.version() == version) return;
        boolean wetNow = s.time() - lastWet > 0.5 || lastWet < 0;
        boolean coarseNow = frame % 2 == 0 || s.calm();
        int[] df = s.dirtyFine(), dc = s.dirtyCoarse();
        level(gl, 0, s.fine(), df, wetNow);
        if (s.coarse() != null && coarseNow) level(gl, 1, s.coarse(), dc, wetNow);
        if (coarseNow || s.coarse() == null) {
            w.clearDirty(s.version());
            version = s.version();
        }
        if (wetNow) lastWet = s.time();
        time = (float) s.time();
    }

    private void level(GL4 gl, int k, Wave.Level l, int[] dirty, boolean wetNow) {
        if (l == null) return;
        int n = l.nx() * l.nz();
        boolean fresh = height[k] == 0 || nx[k] != l.nx() || nz[k] != l.nz();
        if (fresh) {
            release(gl, k);
            height[k] = tex(gl, GL4.GL_R16F, GL.GL_HALF_FLOAT, l.nx(), l.nz());
            wet[k] = tex(gl, GL4.GL_R32F, GL.GL_FLOAT, l.nx(), l.nz());
            nx[k] = l.nx();
            nz[k] = l.nz();
            half[k] = ShortBuffer.allocate(n);
            wetBuf[k] = FloatBuffer.allocate(n);
            dirty = new int[]{0, l.nx() - 1, 0, l.nz() - 1};
            wetNow = true;
        }
        rect[k] = new double[]{l.x0(), l.z0(), l.cell(), 1};
        if (dirty == null || dirty[1] < dirty[0] || dirty[3] < dirty[2]) return;
        int a = Math.max(0, dirty[0]), b = Math.min(l.nx() - 1, dirty[1]), c = Math.max(0, dirty[2]), d = Math.min(l.nz() - 1, dirty[3]);
        float[] eta = l.eta(), wt = l.wet();
        short[] h = half[k].array();
        float[] wb = wetBuf[k].array();
        for (int j = c; j <= d; j++)
            for (int i = a; i <= b; i++) {
                int q = j * l.nx() + i;
                h[q] = Float.floatToFloat16(eta[q]);
                wb[q] = wt[q];
            }
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glPixelStorei(GL4.GL_UNPACK_ROW_LENGTH, l.nx());
        gl.glPixelStorei(GL4.GL_UNPACK_SKIP_PIXELS, a);
        gl.glPixelStorei(GL4.GL_UNPACK_SKIP_ROWS, c);
        gl.glBindTexture(GL.GL_TEXTURE_2D, height[k]);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, a, c, b - a + 1, d - c + 1, GL4.GL_RED, GL.GL_HALF_FLOAT, half[k].rewind());
        uploadedBytes += 2L * (b - a + 1) * (d - c + 1);
        if (wetNow) {
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glBindTexture(GL.GL_TEXTURE_2D, wet[k]);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, a, c, b - a + 1, d - c + 1, GL4.GL_RED, GL.GL_FLOAT, wetBuf[k].rewind());
            uploadedBytes += 4L * (b - a + 1) * (d - c + 1);
        }
        gl.glPixelStorei(GL4.GL_UNPACK_ROW_LENGTH, 0);
        gl.glPixelStorei(GL4.GL_UNPACK_SKIP_PIXELS, 0);
        gl.glPixelStorei(GL4.GL_UNPACK_SKIP_ROWS, 0);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    private static int tex(GL4 gl, int internal, int type, int w, int h) {
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, internal, w, h, 0, GL4.GL_RED, type, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        return t[0];
    }

    /** Wasserstand des Gitters k (0 fein, 1 grob) als Textur (0: keins). */
    public int heightTexture(int k) { return height[k]; }
    /** Zeit des letzten Nasswerdens an Land (s Laufzeit der Welle) als Textur. */
    public int wetTexture(int k) { return wet[k]; }
    /** {x0, z0, Zelle, an (1) / aus (0)} des Gitters k. */
    public double[] rect(int k) { return rect[k] == null ? new double[]{0, 0, 1, 0} : rect[k].clone(); }
    /** Zellen {nx, nz} des Gitters k. */
    public int[] size(int k) { return new int[]{nx[k], nz[k]}; }
    /** Laufzeit der Welle beim letzten Hochladen (s), für das Alter der Nässe. */
    public float time() { return time; }
    /** Hochgeladene Bytes seit dem Start. */
    public long uploaded() { return uploadedBytes; }

    private void release(GL4 gl, int k) {
        if (height[k] != 0) gl.glDeleteTextures(2, new int[]{height[k], wet[k]}, 0);
        height[k] = wet[k] = 0;
    }

    public void dispose(GL4 gl) { release(gl, 0); release(gl, 1); }
}
