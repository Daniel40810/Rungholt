package com.dan.seal.demo;

import com.dan.seal.Colony;
import com.dan.seal.gl.SealGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.image.BufferedImage;

/**
 * Die Bühne der Demo auf der Grafikkarte: Himmel als Verlauf, die Bank aus {@link DemoBank} als Netz (Sand, nass unter
 * dem Wasser dunkler), die Seehunde aus {@link SealGl}, darüber das Wasser als halbdurchsichtige Fläche; gerechnet in
 * einen HDR-Puffer und mit einer Filmkurve ins Bild. Kamera: Auge, Blickpunkt, Bildwinkel.
 */
class SealScene implements GLEventListener {
    final SealGl sealGl = new SealGl();
    final DemoBank world;
    volatile Colony colony;
    /** Tiere von Hand (Posenbogen) statt der Kolonie: je 16 Werte. */
    volatile float[] manual;
    volatile int manualN;
    volatile double[] eye = {-60, 6, 60}, look = {40, 0, 0};
    volatile double fovy = 40, sunAzDeg = 200, sunAltDeg = 35;
    volatile boolean flatGround, showWater = true;
    volatile double waterLevel = Double.NaN;
    private int groundProg, waterProg, postProg, gvao, gvbo, gcount, emptyVao, fbo, colTex, depTex, fw, fh;
    static final int GN = 160;
    static final double GSIZE = 700, GX0 = -290, GZ0 = -350;

    SealScene(DemoBank world) { this.world = world; }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        sealGl.init(gl);
        groundProg = prog(gl, GROUND_VS, GROUND_FS);
        waterProg = prog(gl, WATER_VS, WATER_FS);
        postProg = prog(gl, POST_VS, POST_FS);
        int[] t = new int[2];
        gl.glGenVertexArrays(2, t, 0);
        gvao = t[0];
        emptyVao = t[1];
        gl.glGenBuffers(1, t, 0);
        gvbo = t[0];
        buildGround(gl);
    }

    /** Das Netz der Bank: Gitter mit Höhe und Normale (oder eben, für den Posenbogen). */
    void buildGround(GL4 gl) {
        float[] v = new float[GN * GN * 6 * 6];
        int k = 0;
        double c = GSIZE / GN;
        for (int j = 0; j < GN; j++)
            for (int i = 0; i < GN; i++) {
                int[][] q = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] p : q) {
                    double x = GX0 + p[0] * c, z = GZ0 + p[1] * c;
                    double y = h(x, z), dx = h(x + 0.5, z) - h(x - 0.5, z), dz = h(x, z + 0.5) - h(x, z - 0.5);
                    double nl = Math.sqrt(dx * dx + 1 + dz * dz);
                    v[k++] = (float) x; v[k++] = (float) y; v[k++] = (float) z;
                    v[k++] = (float) (-dx / nl); v[k++] = (float) (1 / nl); v[k++] = (float) (-dz / nl);
                }
            }
        gcount = k / 6;
        gl.glBindVertexArray(gvao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, gvbo);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, k * 4L, Buffers.newDirectFloatBuffer(v, 0, k), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 24, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 24, 12);
        gl.glBindVertexArray(0);
    }

    double h(double x, double z) { return flatGround ? 0 : world.ground(x, z); }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double se = Math.toRadians(sunAltDeg), sa = Math.toRadians(sunAzDeg);
        double[] sun = {Math.cos(se) * Math.sin(sa), Math.sin(se), -Math.cos(se) * Math.cos(sa)};
        double sunI = 3.2 * smooth(-0.02, 0.15, sun[1]);
        double[] sunC = {sunI, sunI * (0.80 + 0.15 * smooth(0.05, 0.5, sun[1])), sunI * (0.62 + 0.3 * smooth(0.05, 0.5, sun[1]))};
        double sky = 0.15 + 0.85 * smooth(-0.1, 0.4, sun[1]);
        double[] top = {0.20 * sky, 0.32 * sky, 0.55 * sky}, hor = {0.55 * sky, 0.62 * sky, 0.70 * sky};
        float[] vp = viewProj(w / (double) h);
        double level = Double.isNaN(waterLevel) ? world.level() : waterLevel;

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor((float) hor[0], (float) hor[1], (float) hor[2], 1);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        // Grund
        gl.glUseProgram(groundProg);
        gl.glUniformMatrix4fv(gl.glGetUniformLocation(groundProg, "uVP"), 1, false, vp, 0);
        u3(gl, groundProg, "uSun", sun);
        u3(gl, groundProg, "uSunC", sunC);
        u3(gl, groundProg, "uTop", top);
        u3(gl, groundProg, "uHor", hor);
        u3(gl, groundProg, "uCam", eye);
        gl.glUniform1f(gl.glGetUniformLocation(groundProg, "uLevel"), (float) (showWater ? level : -99));
        gl.glBindVertexArray(gvao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, gcount);
        // Seehunde
        Colony c = colony;
        if (manual != null) sealGl.update(gl, manual, manualN, new float[0], 0);
        else if (c != null) sealGl.update(gl, c);
        SealGl.View v = new SealGl.View();
        v.viewProj = vp;
        v.eye = eye.clone();
        SealGl.Light l = new SealGl.Light();
        l.sunDir = sun;
        l.sunColor = sunC;
        l.skyTop = top;
        l.skyHorizon = hor;
        l.fogDistance = 20000;
        sealGl.draw(gl, v, l);
        // Wasser
        if (showWater) {
            gl.glUseProgram(waterProg);
            gl.glUniformMatrix4fv(gl.glGetUniformLocation(waterProg, "uVP"), 1, false, vp, 0);
            gl.glUniform1f(gl.glGetUniformLocation(waterProg, "uLevel"), (float) level);
            u3(gl, waterProg, "uSun", sun);
            u3(gl, waterProg, "uSunC", sunC);
            u3(gl, waterProg, "uTop", top);
            u3(gl, waterProg, "uHor", hor);
            u3(gl, waterProg, "uCam", eye);
            gl.glEnable(GL.GL_BLEND);
            gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
            gl.glBindVertexArray(emptyVao);
            gl.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
            gl.glDisable(GL.GL_BLEND);
        }
        // Filmkurve ins Bild
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(postProg);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glUniform1i(gl.glGetUniformLocation(postProg, "uHdr"), 0);
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) { gl.glDeleteFramebuffers(1, new int[]{fbo}, 0); gl.glDeleteTextures(2, new int[]{colTex, depTex}, 0); }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        colTex = t[0];
        depTex = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, colTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_DEPTH_COMPONENT32F, w, h);
        gl.glGenFramebuffers(1, t, 0);
        fbo = t[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, colTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        fw = w;
        fh = h;
    }

    float[] viewProj(double aspect) {
        double[] f = norm(new double[]{look[0] - eye[0], look[1] - eye[1], look[2] - eye[2]});
        double[] r = norm(cross(f, new double[]{0, 1, 0}));
        double[] u = cross(r, f);
        double n = 0.1, fa = 5000, t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        double[] view = {
                r[0], u[0], -f[0], 0,
                r[1], u[1], -f[1], 0,
                r[2], u[2], -f[2], 0,
                -dot(r, eye), -dot(u, eye), dot(f, eye), 1};
        double[] proj = {t / aspect, 0, 0, 0, 0, t, 0, 0, 0, 0, (fa + n) / (n - fa), -1, 0, 0, 2 * fa * n / (n - fa), 0};
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int rr = 0; rr < 4; rr++) {
                double s = 0;
                for (int k = 0; k < 4; k++) s += proj[k * 4 + rr] * view[c * 4 + k];
                m[c * 4 + rr] = (float) s;
            }
        return m;
    }

    static double dot(double[] a, double[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(dot(a, a)); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }
    static double smooth(double a, double b, double x) { double t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    static void u3(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) v[0], (float) v[1], (float) v[2]); }

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null);
            gl.glCompileShader(s);
            int[] ok = new int[1];
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(s, b.length, len, 0, b, 0);
                throw new IllegalStateException("Demo-Shader: " + new String(b, 0, len[0]));
            }
            gl.glAttachShader(p, s);
        }
        gl.glLinkProgram(p);
        return p;
    }

    BufferedImage grab(GLAutoDrawable a) {
        GL4 gl = a.getGL().getGL4();
        int w = a.getSurfaceWidth(), h = a.getSurfaceHeight();
        java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultDrawFramebuffer();
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    @Override public void dispose(GLAutoDrawable d) { sealGl.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------ Shader der Bühne

    static final String GROUND_VS = """
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNrm;
            uniform mat4 uVP;
            out vec3 vW, vN;
            void main() { vW = aPos; vN = aNrm; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String GROUND_FS = """
            in vec3 vW, vN;
            out vec4 frag;
            uniform vec3 uSun, uSunC, uTop, uHor, uCam;
            uniform float uLevel;
            float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
            void main() {
                vec3 N = normalize(vN);
                float rip = sin(vW.x * 45.0 + sin(vW.z * 3.0) * 2.0) * 0.5 + 0.5;
                vec3 sand = vec3(0.34, 0.28, 0.19) * (0.92 + 0.12 * rip + 0.08 * h21(floor(vW.xz * 3.0)));
                float wet = smoothstep(0.6, 0.0, vW.y - uLevel);
                sand *= mix(1.0, 0.55, wet);
                vec3 c = sand * (uSunC * max(dot(N, uSun), 0.0) / 3.14159 + mix(uHor, uTop, 0.5));
                vec3 V = normalize(uCam - vW), R = reflect(-V, N);
                c += mix(uHor, uTop, clamp(R.y, 0.0, 1.0)) * wet * (0.02 + 0.6 * pow(1.0 - max(dot(N, V), 0.0), 5.0));
                float d = length(vW - uCam);
                frag = vec4(mix(c, uHor, 1.0 - exp(-d / 3000.0)), 1.0);
            }
            """;
    static final String WATER_VS = """
            uniform mat4 uVP;
            uniform float uLevel;
            out vec3 vW;
            void main() {
                vec2 c = vec2((gl_VertexID & 1) == 0 ? -1.0 : 1.0, (gl_VertexID & 2) == 0 ? -1.0 : 1.0) * 2000.0;
                vW = vec3(c.x, uLevel, c.y);
                gl_Position = uVP * vec4(vW, 1.0);
            }
            """;
    static final String WATER_FS = """
            in vec3 vW;
            out vec4 frag;
            uniform vec3 uSun, uSunC, uTop, uHor, uCam;
            void main() {
                vec3 V = normalize(uCam - vW);
                vec3 N = normalize(vec3(0.02 * sin(vW.x * 1.3 + vW.z * 0.7), 1.0, 0.02 * cos(vW.z * 1.1 - vW.x * 0.4)));
                vec3 R = reflect(-V, N);
                float f = 0.02 + 0.98 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
                vec3 body = vec3(0.035, 0.05, 0.04) * (uSunC * max(uSun.y, 0.0) / 3.14159 + uTop);
                vec3 c = mix(body, mix(uHor, uTop, clamp(R.y * 2.0, 0.0, 1.0)), f) + uSunC * pow(max(dot(R, uSun), 0.0), 400.0) * 2.0;
                frag = vec4(c, mix(0.88, 1.0, f));
            }
            """;
    static final String POST_VS = "void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    static final String POST_FS = """
            uniform sampler2D uHdr;
            out vec4 frag;
            void main() {
                vec3 c = texelFetch(uHdr, ivec2(gl_FragCoord.xy), 0).rgb * 1.6;
                c = c / (1.0 + c);
                frag = vec4(pow(c, vec3(1.0 / 2.2)), 1.0);
            }
            """;
}
