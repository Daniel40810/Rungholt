package com.dan.smoke.gl;

import com.dan.smoke.RoomSmoke;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;

import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * Seit Haithabu Phase 11: zeichnet den Rauch eines Raums ({@link RoomSmoke}) als Volumen mit JOGL (OpenGL 4.3) über die
 * Szene: ein Bild füllendes Dreieck, je Pixel der Blickstrahl durch die Hülle bis zur Szenentiefe abgetastet
 * ({@code room_smoke.frag}). Sonne und Mond fallen nur durch die Öffnungen herein: so stehen Lichtstrahlen von Tür und
 * Rauchloch im Rauch. Dazu die Lichter im Raum (Herd, Lampen) und ein gleichmäßiges Licht aus Himmel und
 * Rückstrahlung. Das Ziel bindet der Benutzer; gemischt wird ONE, SRC_ALPHA (gestreutes Licht plus Durchlässigkeit
 * mal Bild).
 * <p>
 * Der Raum kommt als Zahlenfeld {@link View#room} in derselben Form wie {@code com.dan.timber.Interior.Room#pack}
 * (die App übersetzt, dieses Package kennt timber nicht): Ort x, y, z, Richtung (rad); halbe Länge, halbe Breite,
 * Wandhöhe, Firsthöhe; Neigung (tan), Zahl der Öffnungen, 0, 0; je Öffnung {Seite, a0, a1, Dreieck} {y0, y1, Fläche, 0}.
 */
public final class RoomSmokeGl {
    public static final class View {
        public float[] invViewProj;
        public double[] eye = new double[3], forward = {0, 0, -1};
        public int width = 1, height = 1;
        public boolean reversedZ;
        public double near = 0.2, far = 60_000;
        public int depthTexture;
        /** Der Raum (siehe oben). */
        public float[] room;
        /** Abtastungen je Strahl höchstens (10 bis 64). */
        public int steps = 40;
    }

    public static final class Light {
        public double[] sunDir = {0, 1, 0}, sunColor = {0, 0, 0}, moonDir = {0, -1, 0}, moonColor = {0, 0, 0};
        /**
         * Gleichmäßiges Licht im Raum aus Himmel (durch die Öffnungen) und Rückstrahlung, in der Einheit, in der die
         * Flächen des Benutzers ihr Himmelslicht malen (Streuung = Rückstreuung · dieses Licht).
         */
        public double[] ambient = {0, 0, 0};
        /** Lichter im Raum, bis 4: Ort (Szene) und Größe; Lichtstärke wie sunColor je m². */
        public float[] lightPos = new float[16], lightRgb = new float[16];
        public int lightCount;
    }

    private int prog, compProg, vao, noiseTex;
    /** Seit Phase 11b: Ziel in halber Auflösung (Befund Phase 11: 4,6 ms in voller Auflösung auf der Radeon). */
    private int halfFbo, halfTex, halfW, halfH;
    /** Halbe Auflösung (Standard) oder volle. */
    public volatile boolean half = true;
    private String info = "";

    public String info() { return info; }

    public void init(GL4 gl) {
        String vs = "#version 430 core\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
        prog = SmokeGl.program(gl, vs, "#version 430 core\n" + SmokeGl.read("room_smoke.frag"));
        // hochmischen mit einem weichen Zeltfilter aus vier bilinearen Proben (Befund Phase 12: in halber Auflösung
        // wurde das Rauschen der Abtastung zu sichtbaren 2 × 2-Blöcken am Rand der Lichtstrahlen)
        compProg = SmokeGl.program(gl, vs, "#version 430 core\nout vec4 frag; uniform sampler2D uHalf; uniform vec2 uFull;\n"
                + "void main() { vec2 uv = gl_FragCoord.xy / uFull; vec2 t = 0.75 / vec2(textureSize(uHalf, 0));\n"
                + "  frag = 0.25 * (texture(uHalf, uv + vec2(-t.x, -t.y)) + texture(uHalf, uv + vec2(t.x, -t.y))\n"
                + "               + texture(uHalf, uv + vec2(-t.x, t.y)) + texture(uHalf, uv + vec2(t.x, t.y))); }\n");
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        noiseTex = noise3d(gl, 32, 7);
    }

    /** Wertrauschen 32³, kachelbar, glatt (für Wirbel im Rauch). */
    private static int noise3d(GL4 gl, int n, long seed) {
        java.util.SplittableRandom r = new java.util.SplittableRandom(seed);
        int g = 8;
        float[] lat = new float[g * g * g];
        for (int i = 0; i < lat.length; i++) lat[i] = (float) r.nextDouble();
        ByteBuffer b = Buffers.newDirectByteBuffer(n * n * n);
        for (int z = 0; z < n; z++)
            for (int y = 0; y < n; y++)
                for (int x = 0; x < n; x++) {
                    double fx = x * g / (double) n, fy = y * g / (double) n, fz = z * g / (double) n;
                    int x0 = (int) fx, y0 = (int) fy, z0 = (int) fz;
                    double tx = s(fx - x0), ty = s(fy - y0), tz = s(fz - z0), v = 0;
                    for (int c = 0; c < 8; c++) {
                        int xi = (x0 + (c & 1)) % g, yi = (y0 + ((c >> 1) & 1)) % g, zi = (z0 + ((c >> 2) & 1)) % g;
                        double w = ((c & 1) == 1 ? tx : 1 - tx) * (((c >> 1) & 1) == 1 ? ty : 1 - ty) * (((c >> 2) & 1) == 1 ? tz : 1 - tz);
                        v += w * lat[xi + g * (yi + g * zi)];
                    }
                    b.put((byte) Math.round(v * 255));
                }
        b.flip();
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, t[0]);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage3D(GL2ES2.GL_TEXTURE_3D, 0, GL.GL_R8, n, n, n, 0, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, b);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_REPEAT);
        gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_REPEAT);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
        return t[0];
    }

    private static double s(double t) { return t * t * (3 - 2 * t); }

    private void ensureHalf(GL4 gl, int w, int h) {
        if (halfFbo != 0 && w == halfW && h == halfH) return;
        if (halfFbo != 0) { gl.glDeleteFramebuffers(1, new int[]{halfFbo}, 0); gl.glDeleteTextures(1, new int[]{halfTex}, 0); }
        int[] t = new int[1], f = new int[1];
        gl.glGenTextures(1, t, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glGenFramebuffers(1, f, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, t[0], 0);
        halfFbo = f[0];
        halfTex = t[0];
        halfW = w;
        halfH = h;
    }

    /**
     * Zeichnet den Rauch des Raums ins gebundene Ziel (Größe view.width × view.height). In halber Auflösung ({@link
     * #half}) rechnet es erst in ein eigenes Ziel und mischt das dann hoch. Stärke 0..1,5 (Bedienfeld).
     */
    public void draw(GL4 gl, View view, Light light, RoomSmoke smoke, double strength, double time) {
        if (view.room == null || view.room.length < 16) return;
        int[] target = new int[1];
        gl.glGetIntegerv(GL.GL_FRAMEBUFFER_BINDING, target, 0);
        int w = view.width, h = view.height;
        boolean lo = half && w > 64 && h > 64;
        int rw = lo ? (w + 1) / 2 : w, rh = lo ? (h + 1) / 2 : h;
        if (lo) {
            ensureHalf(gl, rw, rh);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, halfFbo);
            gl.glViewport(0, 0, rw, rh);
            gl.glClearColor(0, 0, 0, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        }
        gl.glUseProgram(prog);
        gl.glUniformMatrix4fv(loc(gl, "uInvVP"), 1, false, view.invViewProj, 0);
        set3(gl, "uEye", view.eye);
        set3(gl, "uForward", view.forward);
        gl.glUniform2f(loc(gl, "uSize"), rw, rh);
        gl.glUniform1f(loc(gl, "uDepthScale"), lo ? (float) w / rw : 1f);
        gl.glUniform1i(loc(gl, "uHasDepth"), view.depthTexture != 0 ? 1 : 0);
        gl.glUniform1i(loc(gl, "uReversed"), view.reversedZ ? 1 : 0);
        gl.glUniform1f(loc(gl, "uNear"), (float) view.near);
        gl.glUniform1f(loc(gl, "uFar"), (float) view.far);
        float[] r = view.room;
        gl.glUniform4fv(loc(gl, "uRoomA"), 1, r, 0);
        gl.glUniform4fv(loc(gl, "uRoomB"), 1, r, 4);
        gl.glUniform4f(loc(gl, "uRoomC"), r[8], r[9], 1f, 0f);
        float[] o = new float[64];
        System.arraycopy(r, 16, o, 0, Math.min(64, r.length - 16));
        gl.glUniform4fv(loc(gl, "uRoomO[0]"), 16, o, 0);
        float[] sp = smoke.pack();
        gl.glUniform4fv(loc(gl, "uSmokeA"), 1, sp, 0);
        gl.glUniform4fv(loc(gl, "uSmokeB"), 1, sp, 4);
        gl.glUniform1f(loc(gl, "uPlumeC"), (float) smoke.plumeCoefficient());
        gl.glUniform1f(loc(gl, "uAlbedo"), (float) RoomSmoke.ALBEDO);
        gl.glUniform1f(loc(gl, "uStrength"), (float) Math.max(0, Math.min(1.5, strength)));
        gl.glUniform1f(loc(gl, "uTime"), (float) (time % 10_000));
        set3(gl, "uSunDir", light.sunDir);
        set3(gl, "uSunColor", light.sunColor);
        set3(gl, "uMoonDir", light.moonDir);
        set3(gl, "uMoonColor", light.moonColor);
        set3(gl, "uAmbient", light.ambient);
        gl.glUniform1i(loc(gl, "uLightN"), Math.min(4, light.lightCount));
        gl.glUniform4fv(loc(gl, "uLightPos[0]"), 4, light.lightPos, 0);
        gl.glUniform4fv(loc(gl, "uLightRgb[0]"), 4, light.lightRgb, 0);
        gl.glUniform1i(loc(gl, "uSteps"), Math.max(10, Math.min(64, view.steps)));
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, view.depthTexture);
        gl.glUniform1i(loc(gl, "uSceneDepth"), 0);
        gl.glActiveTexture(GL.GL_TEXTURE1);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, noiseTex);
        gl.glUniform1i(loc(gl, "uNoise"), 1);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDepthMask(false);
        gl.glDisable(GL.GL_CULL_FACE);
        gl.glBindVertexArray(vao);
        if (lo) {
            gl.glDisable(GL.GL_BLEND);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
            // hochmischen ins Ziel des Benutzers
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, target[0]);
            gl.glViewport(0, 0, w, h);
            gl.glUseProgram(compProg);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, halfTex);
            gl.glUniform1i(gl.glGetUniformLocation(compProg, "uHalf"), 0);
            gl.glUniform2f(gl.glGetUniformLocation(compProg, "uFull"), w, h);
        }
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_SRC_ALPHA);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        gl.glUseProgram(0);
        info = String.format(Locale.GERMAN, "Rauch im Raum: %s · %d×%d", smoke.describe(), rw, rh);
    }

    public void dispose(GL4 gl) {
        if (prog != 0) gl.glDeleteProgram(prog);
        if (compProg != 0) gl.glDeleteProgram(compProg);
        if (halfFbo != 0) { gl.glDeleteFramebuffers(1, new int[]{halfFbo}, 0); gl.glDeleteTextures(1, new int[]{halfTex}, 0); halfFbo = 0; }
        if (vao != 0) gl.glDeleteVertexArrays(1, new int[]{vao}, 0);
        if (noiseTex != 0) gl.glDeleteTextures(1, new int[]{noiseTex}, 0);
        prog = vao = noiseTex = 0;
    }

    private void set3(GL4 gl, String n, double[] c) { gl.glUniform3f(loc(gl, n), (float) c[0], (float) c[1], (float) c[2]); }

    private int loc(GL4 gl, String name) { return gl.glGetUniformLocation(prog, name); }
}
