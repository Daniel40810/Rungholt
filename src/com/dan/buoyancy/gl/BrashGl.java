package com.dan.buoyancy.gl;

import com.dan.buoyancy.BrashField;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.nio.FloatBuffer;

/**
 * Das Treibeis ({@link BrashField}) als Textur R16F für eine fremde Wasseroberfläche (seit Glacier Phase 9). Neu
 * hochgeladen wird höchstens zweimal je Sekunde und nur, wenn sich das Feld geändert hat.
 */
public final class BrashGl {
    private int tex, w, h;
    private long version = -1;
    private double last = -1;

    /** Je Bild im GL-Faden; {@code now} in Sekunden. */
    public void update(GL4 gl, BrashField f, double now) {
        if (f == null) return;
        if (tex == 0 || w != f.nx || h != f.nz) {
            if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            tex = t[0];
            w = f.nx;
            h = f.nz;
            gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_R16F, w, h, 0, GL4.GL_RED, GL.GL_FLOAT, null);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            version = -1;
        }
        if (f.version() == version || now - last < 0.5 && last >= 0) return;
        version = f.version();
        last = now;
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, w, h, GL4.GL_RED, GL.GL_FLOAT, FloatBuffer.wrap(f.copy()));
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    public int texture() { return tex; }

    public void dispose(GL4 gl) {
        if (tex != 0) gl.glDeleteTextures(1, new int[]{tex}, 0);
        tex = 0;
    }
}
