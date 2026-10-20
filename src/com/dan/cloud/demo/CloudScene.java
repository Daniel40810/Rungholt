package com.dan.cloud.demo;

import com.dan.cloud.Clouds;
import com.dan.cloud.gl.CloudGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.util.Locale;

/**
 * Die Szene der Demo: ein schlichter Himmel (Verlauf, Sonnenscheibe, Hof) über einer glatten See, darüber die Wolken
 * aus {@code com.dan.cloud}, ihr Schatten auf dem Wasser. Sonne nach Höhe und Richtung, ihr Licht in jeder Höhe
 * nach Rayleigh und Dunst mit Luftmasse und Erdschatten gerechnet (eine kleine Näherung, damit die Demo ohne
 * {@code com.dan.sky} auskommt; Paketvertrag Punkt 3). Belichtung nach dem Himmelslicht, über eine Sekunde geglättet.
 * Die Grafikkarte misst die Zeit der Wolken (Zeitstempel vor und nach {@link CloudGl#draw}).
 */
final class CloudScene implements GLEventListener {
    final Clouds clouds;
    final CloudGl cloudGl = new CloudGl();
    /** Sonne: Höhe und Azimut in Grad; Blick: Azimut und Höhe in Grad, Öffnung. */
    volatile double sunAlt = 30, sunAz = 200, yaw = 20, pitch = 10, fovy = 60;
    /** Sicht in km (Dunst). */
    volatile double visibilityKm = 40;
    /** Zeitraffer der Wolken (Szenensekunden je Sekunde) und der Bedeckung (Achtel je Sekunde, 0 = aus). */
    volatile double lapse = 1, oktasLapse;
    volatile boolean frozen;
    volatile String status = "", selfCheck = "";
    /** Gemessene Zeit der Wolken auf der Grafikkarte in ms (gleitend). */
    volatile double cloudMs;
    private double adapt;
    private long last;
    private int hdrTex, fbo, fw, fh, progSky, progTone, vao;
    private final int[] query = new int[2];
    private boolean queryPending;

    static final double SUN_TOA = 2.9;
    static final double[] RAYLEIGH = {5.8e-6, 13.5e-6, 33.1e-6};

    CloudScene(Clouds clouds) { this.clouds = clouds; }

    // ------------------------------------------------------------------ Licht (Näherung für die Demo)

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.sin(z) * Math.cos(a), Math.sin(a), -Math.cos(z) * Math.cos(a)};
    }

    /** Sonnenlicht in der Höhe h (m): Rayleigh (8 km) und Dunst (1,2 km) entlang der Luftmasse, unter der Kimm aus. */
    double[] sunAt(double h) {
        double dip = Math.toDegrees(Math.acos(Clouds.EARTH_R / (Clouds.EARTH_R + Math.max(0, h))));
        double alt = sunAlt + dip;
        if (alt < -0.3) return new double[3];
        double am = 1 / (Math.sin(Math.toRadians(Math.max(alt, 0))) + 0.50572 * Math.pow(Math.max(alt, 0) + 6.07995, -1.6364));
        double mie = 3.912 / (visibilityKm * 1000);
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) {
            double tau = (RAYLEIGH[i] * 8000 * Math.exp(-h / 8000) + mie * 1200 * Math.exp(-h / 1200)) * Math.min(am, 40);
            c[i] = SUN_TOA * Math.exp(-tau) * Math.min(1, (alt + 0.3) / 0.6);
        }
        return c;
    }

    /** Himmel im Zenit und an der Kimm (Strahldichte), grob nach Sonnenhöhe; nachts ein dunkles Blau. */
    double[][] skyColors() {
        double s = Math.sin(Math.toRadians(sunAlt));
        double day = Math.max(0, Math.min(1, (sunAlt + 6) / 12));
        double[] sun = sunAt(0);
        double k = Math.max(0, s + 0.1);
        double[] zen = new double[3], hor = new double[3];
        double[] zc = {0.011, 0.028, 0.075}, hc = {0.045, 0.058, 0.08};
        for (int i = 0; i < 3; i++) {
            zen[i] = SUN_TOA * zc[i] * k * day + 2e-5 * (i + 1);
            hor[i] = (SUN_TOA * hc[i] * Math.pow(k, 0.8) * day + sun[i] * (0.04 + 0.3 * Math.max(0, 1 - s * 5))) + 3e-5 * (i + 1);
        }
        return new double[][]{zen, hor};
    }

    CloudGl.Light light(double pre) {
        CloudGl.Light l = new CloudGl.Light();
        l.dir = sunDir();
        for (int i = 0; i < 8; i++) {
            double[] c = sunAt(CloudGl.LIGHT_HEIGHTS[i]);
            for (int k = 0; k < 3; k++) l.colorAt[i][k] = c[k] * pre;
        }
        double[][] sky = skyColors();
        double[] sun = sunAt(0);
        double sy = Math.max(0, l.dir[1]);
        for (int k = 0; k < 3; k++) {
            l.ambTop[k] = (sky[0][k] * 0.6 + sky[1][k] * 0.4) * pre;
            l.ambBot[k] = (l.ambTop[k] * Math.PI / pre + sun[k] * sy) * 0.06 / Math.PI * pre;
        }
        l.haze = 3.912 / (visibilityKm * 1000);
        return l;
    }

    /** Belichtung: Himmelslicht von oben, durch die Wolken gedämpft, mit nur teilweiser Anpassung. */
    double targetAdaptation() {
        double[][] sky = skyColors();
        double[] sun = sunAt(0);
        double e = 0;
        double[] lum = {0.2126, 0.7152, 0.0722};
        double[] high = sunAt(1500);   // was die Wolken beleuchtet (bei tiefer Sonne heller als am Boden)
        for (int k = 0; k < 3; k++) e += lum[k] * (sky[0][k] * Math.PI + sun[k] * Math.max(0, Math.sin(Math.toRadians(sunAlt))) + high[k] * 0.3);
        return Math.max(1e-6, e * clouds.brightnessFactor());
    }

    static double exposure(double adapt) { return Math.min(2000, 5.0 * Math.pow(adapt, -0.75)); }

    // ------------------------------------------------------------------ GL

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        cloudGl.init(gl);
        progSky = program(gl, "Demo-Himmel", SKY_FRAG);
        progTone = program(gl, "Demo-Belichtung", TONE_FRAG);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
        gl.glGenQueries(2, query, 0);
        last = System.nanoTime();
    }

    double[] forward() {
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        return new double[]{Math.sin(y) * Math.cos(p), Math.sin(p), -Math.cos(y) * Math.cos(p)};
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = Math.min(0.1, (now - last) / 1e9);
        last = now;
        if (!frozen) {
            clouds.step(dt * lapse);
            if (oktasLapse != 0) {
                double o = clouds.oktas + oktasLapse * dt;
                if (o > 8 || o < 0) { oktasLapse = -oktasLapse; o = Math.max(0, Math.min(8, o)); }
                clouds.oktas = o;
            }
        }
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        double target = targetAdaptation();
        adapt = adapt <= 0 || frozen ? target : Math.exp(Math.log(adapt) + (Math.log(target) - Math.log(adapt)) * Math.min(1, dt));
        double pre = exposure(adapt);
        Clouds.Snapshot s = clouds.snapshot();
        CloudGl.Light l = light(pre);
        double[] eye = {0, 10, 0};
        cloudGl.update(gl, s, l, eye);

        double[] f = forward();
        float[] vp = mul(perspective(fovy, w / (double) h), lookAt(f, eye));
        float[] inv = invert(vp);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(progSky);
        double[][] sky = skyColors();
        double[] sun = sunAt(0), dir = l.dir, rect = cloudGl.shadowRect();
        mat(gl, progSky, "uInvVP", inv);
        vec(gl, progSky, "uZenith", sky[0], pre);
        vec(gl, progSky, "uHorizon", sky[1], pre);
        vec(gl, progSky, "uSunColor", sun, pre);
        vec(gl, progSky, "uSunDir", dir, 1);
        gl.glUniform4f(gl.glGetUniformLocation(progSky, "uShadowRect"), (float) rect[0], (float) rect[1], (float) rect[2], 1);
        gl.glUniform3f(gl.glGetUniformLocation(progSky, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform1f(gl.glGetUniformLocation(progSky, "uDim"), (float) clouds.brightnessFactor());
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudGl.shadowTexture());
        gl.glUniform1i(gl.glGetUniformLocation(progSky, "uShadow"), 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);

        CloudGl.View v = new CloudGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = eye;
        v.reversedZ = false;
        v.width = w;
        v.height = h;
        if (queryPending) {
            int[] ok = new int[1];
            gl.glGetQueryObjectiv(query[1], GL4.GL_QUERY_RESULT_AVAILABLE, ok, 0);
            if (ok[0] != 0) {
                long[] a = new long[1], b = new long[1];
                gl.glGetQueryObjecti64v(query[0], GL4.GL_QUERY_RESULT, a, 0);
                gl.glGetQueryObjecti64v(query[1], GL4.GL_QUERY_RESULT, b, 0);
                double ms = (b[0] - a[0]) / 1e6;
                cloudMs = cloudMs <= 0 ? ms : cloudMs * 0.9 + ms * 0.1;
                queryPending = false;
            }
        }
        boolean measure = !queryPending;
        if (measure) gl.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        cloudGl.draw(gl, s, l, v);
        if (measure) {
            gl.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
            queryPending = true;
        }

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(vao);
        gl.glUseProgram(progTone);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glUniform1i(gl.glGetUniformLocation(progTone, "uHdr"), 0);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        gl.glUseProgram(0);
        gl.glBindVertexArray(0);
        status = String.format(Locale.GERMAN, "%s · Wolken %.2f ms", clouds.describe(), cloudMs);
    }

    /** Selbsttest Grafikkarte gegen Prozessor (Schattenkarte), im GL-Faden nach einem Bild. */
    String check(GLAutoDrawable d) {
        selfCheck = cloudGl.selfCheck(d.getGL().getGL4(), clouds.snapshot(), light(exposure(Math.max(adapt, 1e-6))));
        return selfCheck;
    }

    private void targets(GL4 gl, int w, int h) {
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{hdrTex}, 0);
        }
        int[] t = new int[1];
        gl.glGenTextures(1, t, 0);
        hdrTex = t[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL.GL_RGBA16F, w, h);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        int[] f = new int[1];
        gl.glGenFramebuffers(1, f, 0);
        fbo = f[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        fw = w;
        fh = h;
    }

    // ------------------------------------------------------------------ Shader der Demo

    static final String VERT = """
            #version 430 core
            out vec2 vUV;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                vUV = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;

    /** Himmel als Verlauf mit Sonne und Hof, darunter glatte See mit Spiegelung, Wolkenschatten und Dunst. */
    static final String SKY_FRAG = """
            #version 430 core
            in vec2 vUV;
            out vec4 frag;
            uniform mat4 uInvVP;
            uniform vec3 uZenith, uHorizon, uSunColor, uSunDir, uEye;
            uniform vec4 uShadowRect;
            uniform float uDim;   // Himmelslicht unter den Wolken (die See spiegelt den klaren Himmel, darum gedämpft)
            uniform sampler2D uShadow;
            vec3 sky(vec3 d) {
                float e = clamp(d.y, 0.0, 1.0);
                vec3 c = mix(uHorizon, uZenith, pow(e, 0.45));
                float cs = dot(d, uSunDir);
                c += uSunColor * (0.012 / (1.0 + 400.0 * (1.0 - cs)) + 0.002 * pow(max(cs, 0.0), 8.0));
                return c;
            }
            void main() {
                vec4 a = uInvVP * vec4(vUV * 2.0 - 1.0, -1.0, 1.0), b = uInvVP * vec4(vUV * 2.0 - 1.0, 0.0, 1.0);
                vec3 d = normalize(b.xyz / b.w - a.xyz / a.w);
                vec3 c;
                if (d.y > -0.002) {
                    c = sky(d);
                    if (dot(d, uSunDir) > 0.999989) c += uSunColor * 4000.0;
                } else {
                    float t = uEye.y / -d.y;
                    vec2 p = uEye.xz + d.xz * t;
                    float sh = textureLod(uShadow, (p - uShadowRect.xy) / uShadowRect.z, 0.0).r;
                    float F = 0.02 + 0.98 * pow(1.0 - abs(d.y), 5.0);
                    vec3 r = reflect(d, vec3(0.0, 1.0, 0.0));
                    vec3 e = uSunColor * sh * max(uSunDir.y, 0.0) + uZenith * 3.5 * uDim;
                    vec3 body = vec3(0.020, 0.072, 0.066) * e * 0.11;
                    c = mix(body, sky(r) * uDim, F);
                    float fog = 1.0 - exp(-t / 40000.0);
                    c = mix(c, uHorizon * uDim, fog);
                }
                frag = vec4(c, 1.0);
            }
            """;

    /** ACES-Kurve (Narkowicz) und sRGB. */
    static final String TONE_FRAG = """
            #version 430 core
            in vec2 vUV;
            out vec4 frag;
            uniform sampler2D uHdr;
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() {
                vec3 c = aces(texture(uHdr, vUV).rgb);
                c = mix(c * 12.92, 1.055 * pow(c, vec3(1.0 / 2.4)) - 0.055, step(0.0031308, c));
                frag = vec4(c, 1.0);
            }
            """;

    static int program(GL4 gl, String name, String frag) {
        int p = gl.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int sh = gl.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            gl.glShaderSource(sh, 1, new String[]{k == 0 ? VERT : frag}, null);
            gl.glCompileShader(sh);
            int[] ok = new int[1];
            gl.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] log = new byte[8192];
                int[] len = new int[1];
                gl.glGetShaderInfoLog(sh, log.length, len, 0, log, 0);
                throw new IllegalStateException("cloud demo: " + name + ": " + new String(log, 0, len[0]));
            }
            gl.glAttachShader(p, sh);
            gl.glDeleteShader(sh);
        }
        gl.glLinkProgram(p);
        return p;
    }

    private static void mat(GL4 gl, int p, String n, float[] m) { gl.glUniformMatrix4fv(gl.glGetUniformLocation(p, n), 1, false, m, 0); }

    private static void vec(GL4 gl, int p, String n, double[] v, double k) {
        gl.glUniform3f(gl.glGetUniformLocation(p, n), (float) (v[0] * k), (float) (v[1] * k), (float) (v[2] * k));
    }

    // ------------------------------------------------------------------ Matrizen (spaltenweise)

    static float[] perspective(double fovy, double aspect) {
        double f = 1 / Math.tan(Math.toRadians(fovy) / 2), n = 0.5, far = 100_000;
        float[] m = new float[16];
        m[0] = (float) (f / aspect);
        m[5] = (float) f;
        m[10] = (float) ((far + n) / (n - far));
        m[11] = -1;
        m[14] = (float) (2 * far * n / (n - far));
        return m;
    }

    /** Blick vom Auge e in Richtung f. */
    static float[] lookAt(double[] f, double[] e) {
        double[] up = {0, 1, 0};
        double[] r = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rn = Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        for (int i = 0; i < 3; i++) r[i] /= rn;
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

    /** Inverse einer 4×4-Matrix (Gauß-Jordan, spaltenweise). */
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
        cloudGl.dispose(gl);
        gl.glDeleteProgram(progSky);
        gl.glDeleteProgram(progTone);
        gl.glDeleteQueries(2, query, 0);
        if (fbo != 0) {
            gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
            gl.glDeleteTextures(1, new int[]{hdrTex}, 0);
        }
    }
}
