package com.dan.ice.demo;

import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.IceShapes;
import com.dan.ice.gl.IceGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/**
 * Die Szene der Demo: ein Himmel als Verlauf mit Sonne, ein dunkler Sockel aus Schutt, darauf ein Eisblock von 10 m;
 * daneben ein Eisberg, der mit neun Zehnteln im Wasser schwimmt (Wasserlinie aus dem Volumen des Netzes), darüber
 * durchscheinendes Wasser mit Spiegelung. Licht wie in der App vorbelichtet; Ton nach ACES. Die Zeit des Eises misst
 * die Grafikkarte (Zeitstempel um {@link IceGl#draw}).
 */
final class IceScene implements GLEventListener {
    final IceGl iceGl = new IceGl();
    /** Material des Blocks und des Eisbergs (die Regler ändern sie). */
    volatile Ice blockIce = Ice.glacier(), bergIce = Ice.iceberg();
    /** Sonne: Höhe und Azimut (Grad); Blick um den Block: Azimut, Höhe (Grad), Abstand (m); Drehung des Blocks (Grad). */
    volatile double sunAlt = 35, sunAz = 300, yaw = 0, pitch = 9, dist = 42, blockYaw = 28, fovy = 45;
    /** Faktor auf die Dicke (Regler „Dicke“) und Belichtung. */
    volatile double thickness = 1, exposure = 0.55;
    volatile boolean frozen;
    volatile String status = "";
    volatile double iceMs;
    double bergWaterline;

    private int fbo, hdr, depth, fw, fh, progSky, progPad, progWater, progTone, vaoEmpty, vaoPad, vaoWater;
    private IceGl.Body block, berg;
    private final int[] query = new int[2];
    private boolean queryPending;

    static final double[] SUN = {2.6, 2.45, 2.2}, TOP = {0.22, 0.36, 0.62}, HOR = {0.62, 0.72, 0.82};
    static final double BLOCK = 10, PAD = 0.25;

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.sin(z) * Math.cos(a), Math.sin(a), -Math.cos(z) * Math.cos(a)};
    }

    /** Sonnenlicht: unter 10° rötlich und schwächer, unter der Kimm aus (Näherung für die Demo). */
    double[] sunColor() { return sunColorAt(sunAlt); }

    static double[] sunColorAt(double a) {
        if (a < -1) return new double[3];
        double f = Math.min(1, (a + 1) / 4), low = Math.max(0, 1 - a / 12);
        return new double[]{SUN[0] * f, SUN[1] * f * (1 - 0.35 * low), SUN[2] * f * (1 - 0.65 * low)};
    }

    double[][] sky() { return skyAt(sunAlt); }

    static double[][] skyAt(double sunAlt) {
        double k = Math.max(0.03, Math.min(1, (sunAlt + 6) / 20));
        return new double[][]{{TOP[0] * k, TOP[1] * k, TOP[2] * k}, {HOR[0] * k, HOR[1] * k * (sunAlt < 8 ? 0.9 : 1), HOR[2] * k * (sunAlt < 8 ? 0.8 : 1)}};
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        iceGl.init(gl);
        block = iceGl.upload(gl, IceShapes.block(BLOCK, 0.35));
        IceMesh m = IceShapes.iceberg(7, 7, 0.8, 3);
        bergWaterline = m.waterline(bergIce.submergedFraction(1000));
        berg = iceGl.upload(gl, IceShapes.shifted(m, 0, -bergWaterline, 0));
        progSky = prog(gl, FULL, SKY);
        progPad = prog(gl, POS, PADF);
        progWater = prog(gl, POS, WATER);
        progTone = prog(gl, FULL, TONE);
        int[] t = new int[3];
        gl.glGenVertexArrays(3, t, 0);
        vaoEmpty = t[0];
        vaoPad = quad(gl, t[1], BLOCK * 0.9, PAD, true);
        vaoWater = quad(gl, t[2], 800, 0, false);
        gl.glGenQueries(2, query, 0);
    }

    /** Ein waagerechtes Quadrat der halben Kante r in der Höhe y; als Sockel mit Seitenwänden bis 2 m unter Wasser. */
    private static int quad(GL4 gl, int vao, double r, double y, boolean skirt) {
        float[] p;
        float a = (float) -r, b = (float) -r, c = (float) r, e = (float) r, h = (float) y, lo = -2;
        if (skirt) {
            p = new float[]{a, h, b, c, h, b, c, h, e, a, h, b, c, h, e, a, h, e,
                    a, lo, b, c, lo, b, c, h, b, a, lo, b, c, h, b, a, h, b,
                    a, lo, e, c, lo, e, c, h, e, a, lo, e, c, h, e, a, h, e,
                    a, lo, b, a, lo, e, a, h, e, a, lo, b, a, h, e, a, h, b,
                    c, lo, b, c, lo, e, c, h, e, c, lo, b, c, h, e, c, h, b};
        } else p = new float[]{a, h, b, c, h, b, c, h, e, a, h, b, c, h, e, a, h, e};
        FloatBuffer fb = Buffers.newDirectFloatBuffer(p);
        int[] t = new int[1];
        gl.glGenBuffers(1, t, 0);
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, t[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, p.length * 4L, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindVertexArray(0);
        return vao;
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double cy = 4, py = Math.toRadians(pitch), ay = Math.toRadians(yaw);
        double[] at = {-6, cy, 0};
        double[] eye = {at[0] + Math.sin(ay) * Math.cos(py) * dist, at[1] + Math.sin(py) * dist, at[2] + Math.cos(ay) * Math.cos(py) * dist};
        eye[1] = Math.max(0.6, eye[1]);
        double[] f = {at[0] - eye[0], at[1] - eye[1], at[2] - eye[2]};
        double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        for (int i = 0; i < 3; i++) f[i] /= fl;
        float[] vp = mul(perspective(fovy, w / (double) h), lookAt(f, eye));
        float[] inv = invert(vp);
        double[] sun = sunDir(), sc = sunColor();
        double[][] sky = sky();

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(progSky);
        mat(gl, progSky, "uInv", inv);
        vec(gl, progSky, "uTop", sky[0]);
        vec(gl, progSky, "uHor", sky[1]);
        vec(gl, progSky, "uSun", sun);
        vec(gl, progSky, "uSunC", sc);
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(progPad);
        mat(gl, progPad, "uVP", vp);
        vec(gl, progPad, "uSun", sun);
        vec(gl, progPad, "uSunC", sc);
        vec(gl, progPad, "uTop", sky[0]);
        gl.glUniform3f(gl.glGetUniformLocation(progPad, "uOff"), (float) -12, 0, 0);
        gl.glBindVertexArray(vaoPad);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 30);

        IceGl.Instance a = new IceGl.Instance(block, blockIce).place(-12, PAD + BLOCK / 2, 0, blockYaw);
        IceGl.Instance b = new IceGl.Instance(berg, bergIce).place(12, 0, 0, 17);
        IceGl.View v = new IceGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.width = w;
        v.height = h;
        IceGl.Light l = new IceGl.Light();
        l.sunDir = sun;
        l.sunColor = sc;
        l.skyTop = sky[0];
        l.skyHorizon = sky[1];
        l.fogColor = sky[1];
        l.fogDistance = 20_000;
        l.waterLevel = 0;
        l.waterAbsorption = new double[]{0.5, 0.16, 0.16};
        l.waterColor = new double[]{0.035, 0.18, 0.165};
        l.thicknessScale = thickness;
        readQuery(gl);
        boolean measure = !queryPending;
        if (measure) gl.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        iceGl.draw(gl, List.of(a, b), v, l);
        if (measure) {
            gl.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
            queryPending = true;
        }

        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glUseProgram(progWater);
        mat(gl, progWater, "uVP", vp);
        gl.glUniform3f(gl.glGetUniformLocation(progWater, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        vec(gl, progWater, "uTop", sky[0]);
        vec(gl, progWater, "uHor", sky[1]);
        vec(gl, progWater, "uSun", sun);
        vec(gl, progWater, "uSunC", sc);
        gl.glUniform3f(gl.glGetUniformLocation(progWater, "uOff"), 0, 0, 0);
        gl.glBindVertexArray(vaoWater);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glDisable(GL.GL_DEPTH_TEST);

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glUseProgram(progTone);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdr);
        gl.glUniform1i(gl.glGetUniformLocation(progTone, "uTex"), 0);
        gl.glUniform1f(gl.glGetUniformLocation(progTone, "uExp"), (float) (exposure / Math.max(0.12, Math.min(1, (sunAlt + 6) / 20))));
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        status = String.format(Locale.GERMAN, "Eis auf der Grafikkarte: %.2f ms · %s · Block: %s · Eisberg: %.0f %% unter Wasser, Wasserlinie %.2f m über dem Fuß",
                iceMs, iceGl.info(), blockIce.describe(), bergIce.submergedFraction(1000) * 100, -berg.mesh.yRange()[0]);
    }

    private void readQuery(GL4 gl) {
        if (!queryPending) return;
        int[] ok = new int[1];
        gl.glGetQueryObjectiv(query[1], GL4.GL_QUERY_RESULT_AVAILABLE, ok, 0);
        if (ok[0] == 0) return;
        long[] a = new long[1], b = new long[1];
        gl.glGetQueryObjecti64v(query[0], GL4.GL_QUERY_RESULT, a, 0);
        gl.glGetQueryObjecti64v(query[1], GL4.GL_QUERY_RESULT, b, 0);
        double ms = (b[0] - a[0]) / 1e6;
        iceMs = iceMs <= 0 ? ms : iceMs * 0.9 + ms * 0.1;
        queryPending = false;
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(2, new int[]{hdr, depth}, 0);
        }
        int[] t = new int[2];
        gl.glGenTextures(2, t, 0);
        hdr = t[0];
        depth = t[1];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdr);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depth);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT32F, w, h, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdr, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depth, 0);
        fw = w;
        fh = h;
    }

    // ------------------------------------------------------------------ Shader der Demo (Himmel, Sockel, Wasser, Ton)

    static final String FULL = "#version 430 core\nout vec2 vUV;\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";
    static final String POS = "#version 430 core\nlayout(location = 0) in vec3 aPos;\nuniform mat4 uVP; uniform vec3 uOff; out vec3 w;\nvoid main() { w = aPos + uOff; gl_Position = uVP * vec4(w, 1.0); }\n";
    static final String SKY = """
            #version 430 core
            in vec2 vUV; out vec4 o; uniform mat4 uInv; uniform vec3 uTop, uHor, uSun, uSunC;
            void main() {
                vec4 a = uInv * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInv * vec4(vUV * 2.0 - 1.0, 1.0, 1.0);
                vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
                vec3 c = d.y < 0.0 ? uHor * 0.5 : mix(uHor, uTop, sqrt(d.y));
                c += uSunC * (pow(max(dot(d, uSun), 0.0), 800.0) * 20.0 + pow(max(dot(d, uSun), 0.0), 12.0) * 0.08);
                o = vec4(c, 1.0);
            }
            """;
    static final String PADF = """
            #version 430 core
            in vec3 w; out vec4 o; uniform vec3 uSun, uSunC, uTop;
            float h(vec2 p) { return fract(sin(dot(floor(p), vec2(12.9898, 78.233))) * 43758.5453); }
            void main() {
                vec3 n = normalize(cross(dFdx(w), dFdy(w)));
                float g = 0.7 + 0.5 * h(w.xz * 3.0 + w.y * 5.0);
                vec3 c = vec3(0.09, 0.085, 0.078) * g * (uSunC * max(dot(n, uSun), 0.0) + uTop * (0.6 + 0.4 * n.y));
                o = vec4(c, 1.0);
            }
            """;
    static final String WATER = """
            #version 430 core
            in vec3 w; uniform vec3 uEye, uTop, uHor, uSun, uSunC; out vec4 o;
            void main() {
                vec3 v = normalize(uEye - w);
                vec3 n = normalize(vec3(0.015 * sin(w.x * 1.3 + w.z * 0.4), 1.0, 0.015 * sin(w.z * 1.1 - w.x * 0.3)));
                float fr = 0.02 + 0.98 * pow(1.0 - max(dot(v, n), 0.0), 5.0);
                vec3 r = reflect(-v, n);
                vec3 sky = mix(uHor, uTop, sqrt(max(r.y, 0.0)));
                vec3 body = vec3(0.04, 0.16, 0.17) * (uSunC * max(uSun.y, 0.0) * 0.5 + uTop);
                vec3 c = mix(body, sky, fr) + uSunC * pow(max(dot(r, uSun), 0.0), 3000.0) * 4.0;
                o = vec4(c, 0.55 + 0.45 * fr);
            }
            """;
    static final String TONE = """
            #version 430 core
            in vec2 vUV; out vec4 o; uniform sampler2D uTex; uniform float uExp;
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() { o = vec4(pow(aces(texture(uTex, vUV).rgb * uExp), vec3(1.0 / 2.2)), 1.0); }
            """;

    static int prog(GL4 gl, String vs, String fs) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int s = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(s, 1, new String[]{k == 0 ? vs : fs}, null, 0);
            gl.glCompileShader(s);
            int[] ok = new int[1];
            gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[4096];
                gl.glGetShaderInfoLog(s, log.length, null, 0, log, 0);
                throw new IllegalStateException("IceDemo: Shader: " + new String(log, StandardCharsets.UTF_8).trim());
            }
            gl.glAttachShader(p, s);
            gl.glDeleteShader(s);
        }
        gl.glLinkProgram(p);
        return p;
    }

    static void mat(GL4 gl, int p, String n, float[] m) { gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, n), 1, false, m, 0); }
    static void vec(GL4 gl, int p, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) v[0], (float) v[1], (float) v[2]); }

    // ------------------------------------------------------------------ Matrizen (spaltenweise)

    static float[] perspective(double fovy, double aspect) {
        double f = 1 / Math.tan(Math.toRadians(fovy) / 2), n = 0.1, far = 5000;
        float[] m = new float[16];
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + n) / (n - far));
        m[11] = -1;
        m[14] = (float) (2 * far * n / (n - far));
        return m;
    }

    static float[] lookAt(double[] f, double[] e) {
        double[] r = {-f[2], 0, f[0]};
        double rn = Math.sqrt(r[0] * r[0] + r[2] * r[2]);
        r[0] /= rn;
        r[2] /= rn;
        double[] u = {r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0]};
        double tx = -(r[0] * e[0] + r[1] * e[1] + r[2] * e[2]), ty = -(u[0] * e[0] + u[1] * e[1] + u[2] * e[2]),
                tz = f[0] * e[0] + f[1] * e[1] + f[2] * e[2];
        return new float[]{(float) r[0], (float) u[0], (float) -f[0], 0, (float) r[1], (float) u[1], (float) -f[1], 0,
                (float) r[2], (float) u[2], (float) -f[2], 0, (float) tx, (float) ty, (float) tz, 1};
    }

    static float[] mul(float[] a, float[] b) {
        float[] m = new float[16];
        for (int c = 0; c < 4; c++)
            for (int r = 0; r < 4; r++) {
                float s = 0;
                for (int k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k];
                m[c * 4 + r] = s;
            }
        return m;
    }

    static float[] invert(float[] m) {
        double[][] a = new double[4][8];
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) a[r][c] = m[c * 4 + r];
            a[r][4 + r] = 1;
        }
        for (int c = 0; c < 4; c++) {
            int p = c;
            for (int r = c + 1; r < 4; r++) if (Math.abs(a[r][c]) > Math.abs(a[p][c])) p = r;
            double[] t = a[c];
            a[c] = a[p];
            a[p] = t;
            double d = a[c][c];
            for (int k = 0; k < 8; k++) a[c][k] /= d;
            for (int r = 0; r < 4; r++)
                if (r != c) {
                    double x = a[r][c];
                    for (int k = 0; k < 8; k++) a[r][k] -= x * a[c][k];
                }
        }
        float[] out = new float[16];
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) out[c * 4 + r] = (float) a[r][4 + c];
        return out;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        iceGl.dispose(gl);
        for (int p : new int[]{progSky, progPad, progWater, progTone}) gl.glDeleteProgram(p);
        gl.glDeleteQueries(2, query, 0);
    }
}
