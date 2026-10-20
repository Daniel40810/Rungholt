package com.dan.ice.demo;

import com.dan.ice.Glacier;
import com.dan.ice.Ice;
import com.dan.ice.IceMesh;
import com.dan.ice.IceShapes;
import com.dan.ice.gl.GlacierGl;
import com.dan.ice.gl.IceGl;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Die Szene der Gletscher-Demo: das ausgedachte Tal ({@link GlacierValley}) mit Fels, See und Himmel, darin der
 * Gletscher aus {@code com.dan.ice} mit Kacheln und Front, davor ein Eisberg. Der Blick kreist um einen Punkt
 * (Azimut, Höhe, Abstand); die Zeit des Eises misst die Grafikkarte.
 */
final class GlacierScene implements GLEventListener {
    final Glacier glacier;
    final IceGl iceGl = new IceGl();
    GlacierGl glacierGl;
    volatile double sunAlt = 32, sunAz = 300, yaw = 250, pitch = 12, dist = 900, fovy = 50, exposure = 0.5;
    volatile double[] target = {-250, 40, 0};
    volatile double advance, crevasses = 1;
    volatile boolean frozen;
    volatile String status = "";
    volatile double iceMs;
    private int fbo, hdr, depth, fw, fh, progSky, progRock, progWater, progTone, vaoEmpty, vaoRock, vaoWater, rockCount;
    private IceGl.Body berg;
    private double bergLine;
    private final int[] query = new int[2];
    private boolean queryPending;

    GlacierScene() {
        glacier = new Glacier(GlacierValley.grid(), 0, 60, 1.5, 3);
    }

    double[] sunDir() {
        double a = Math.toRadians(sunAlt), z = Math.toRadians(sunAz);
        return new double[]{Math.sin(z) * Math.cos(a), Math.sin(a), -Math.cos(z) * Math.cos(a)};
    }

    double[] eye() {
        double py = Math.toRadians(pitch), ay = Math.toRadians(yaw);
        double[] t = target;
        double[] e = {t[0] + Math.sin(ay) * Math.cos(py) * dist, t[1] + Math.sin(py) * dist, t[2] - Math.cos(ay) * Math.cos(py) * dist};
        double ground = glacier.covers(e[0], e[2]) ? glacier.surface(e[0], e[2]) : Math.max(0, glacier.ground(e[0], e[2]));
        e[1] = Math.max(ground + 2, e[1]);
        return e;
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        iceGl.init(gl);
        glacierGl = new GlacierGl(iceGl, glacier);
        glacierGl.prime(gl);
        IceMesh m = IceShapes.iceberg(5, 14, 0.75, 3);
        bergLine = m.waterline(Ice.iceberg().submergedFraction(1000));
        berg = iceGl.upload(gl, IceShapes.shifted(m, 0, -bergLine, 0));
        progSky = IceScene.prog(gl, IceScene.FULL, IceScene.SKY);
        progRock = IceScene.prog(gl, ROCK_V, ROCK_F);
        progWater = IceScene.prog(gl, IceScene.POS, IceScene.WATER);
        progTone = IceScene.prog(gl, IceScene.FULL, IceScene.TONE);
        int[] t = new int[3];
        gl.glGenVertexArrays(3, t, 0);
        vaoEmpty = t[0];
        vaoRock = rock(gl, t[1]);
        vaoWater = t[2];
        float[] wq = {-20000, 0, -20000, 20000, 0, -20000, 20000, 0, 20000, -20000, 0, -20000, 20000, 0, 20000, -20000, 0, 20000};
        int[] b = new int[1];
        gl.glGenBuffers(1, b, 0);
        gl.glBindVertexArray(vaoWater);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, wq.length * 4L, Buffers.newDirectFloatBuffer(wq), GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindVertexArray(0);
        gl.glGenQueries(2, query, 0);
    }

    /** Fels und Seegrund aus dem Raster; unter dem Eis der Grund (dort liegt der Gletscher darüber). */
    private int rock(GL4 gl, int vao) {
        Glacier.Grid g = glacier.grid;
        int step = 2, nx = g.nx / step, nz = g.nz / step;
        FloatBuffer fb = Buffers.newDirectFloatBuffer(nx * nz * 3);
        for (int j = 0; j < nz; j++)
            for (int i = 0; i < nx; i++) {
                int q = (j * step) * g.nx + i * step;
                double x = g.x0 + (i * step + 0.5) * g.cell, z = g.z0 + (j * step + 0.5) * g.cell;
                double y = g.kind[q] == Glacier.ICE || glacier.iceShare(x, z) > 0.2 ? g.bed[q] : g.height[q];
                fb.put((float) x).put((float) y).put((float) z);
            }
        fb.flip();
        IntBuffer ib = Buffers.newDirectIntBuffer((nx - 1) * (nz - 1) * 6);
        for (int j = 0; j < nz - 1; j++)
            for (int i = 0; i < nx - 1; i++) {
                int a = j * nx + i;
                ib.put(a).put(a + nx).put(a + 1).put(a + 1).put(a + nx).put(a + nx + 1);
            }
        ib.flip();
        rockCount = ib.limit();
        int[] b = new int[2];
        gl.glGenBuffers(2, b, 0);
        gl.glBindVertexArray(vao);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, b[0]);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, fb.limit() * 4L, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        gl.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, b[1]);
        gl.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, ib.limit() * 4L, ib, GL.GL_STATIC_DRAW);
        gl.glBindVertexArray(0);
        return vao;
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        if (w != fw || h != fh) targets(gl, w, h);
        if (glacier.crevasseScale != crevasses) {
            glacier.crevasseScale = crevasses;
            glacierGl.invalidate();
        }
        glacierGl.setAdvance(advance);
        double[] eye = eye(), t = target;
        double[] f = {t[0] - eye[0], t[1] - eye[1], t[2] - eye[2]};
        double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        for (int i = 0; i < 3; i++) f[i] /= fl;
        float[] proj = IceScene.perspective(fovy, w / (double) h);
        proj[10] = (float) ((20000 + 1.0) / (1.0 - 20000));
        proj[14] = (float) (2 * 20000 * 1.0 / (1.0 - 20000));
        float[] vp = IceScene.mul(proj, IceScene.lookAt(f, eye));
        float[] inv = IceScene.invert(vp);
        double[] sun = sunDir(), sc = IceScene.sunColorAt(sunAlt);
        double[][] sky = IceScene.skyAt(sunAlt);

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearDepth(1);
        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        gl.glUseProgram(progSky);
        IceScene.mat(gl, progSky, "uInv", inv);
        IceScene.vec(gl, progSky, "uTop", sky[0]);
        IceScene.vec(gl, progSky, "uHor", sky[1]);
        IceScene.vec(gl, progSky, "uSun", sun);
        IceScene.vec(gl, progSky, "uSunC", sc);
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        gl.glUseProgram(progRock);
        IceScene.mat(gl, progRock, "uVP", vp);
        IceScene.vec(gl, progRock, "uSun", sun);
        IceScene.vec(gl, progRock, "uSunC", sc);
        IceScene.vec(gl, progRock, "uTop", sky[0]);
        IceScene.vec(gl, progRock, "uHor", sky[1]);
        gl.glUniform3f(gl.glGetUniformLocation(progRock, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glBindVertexArray(vaoRock);
        gl.glDrawElements(GL.GL_TRIANGLES, rockCount, GL.GL_UNSIGNED_INT, 0);

        List<IceGl.Instance> list = new ArrayList<>(glacierGl.instances(gl, eye, vp));
        list.add(new IceGl.Instance(berg, Ice.iceberg()).place(520, 0, 140, 33));
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
        l.fogDistance = 25_000;
        l.waterLevel = 0;
        l.waterAbsorption = new double[]{0.5, 0.16, 0.16};
        l.waterColor = new double[]{0.035, 0.18, 0.165};
        readQuery(gl);
        boolean measure = !queryPending;
        if (measure) gl.glQueryCounter(query[0], GL4.GL_TIMESTAMP);
        iceGl.draw(gl, list, v, l);
        if (measure) {
            gl.glQueryCounter(query[1], GL4.GL_TIMESTAMP);
            queryPending = true;
        }

        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glUseProgram(progWater);
        IceScene.mat(gl, progWater, "uVP", vp);
        gl.glUniform3f(gl.glGetUniformLocation(progWater, "uEye"), (float) eye[0], (float) eye[1], (float) eye[2]);
        gl.glUniform3f(gl.glGetUniformLocation(progWater, "uOff"), 0, 0, 0);
        IceScene.vec(gl, progWater, "uTop", sky[0]);
        IceScene.vec(gl, progWater, "uHor", sky[1]);
        IceScene.vec(gl, progWater, "uSun", sun);
        IceScene.vec(gl, progWater, "uSunC", sc);
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
        double[] vv = glacier.velocity(t[0], t[2]);
        status = String.format(Locale.GERMAN, "Eis auf der Grafikkarte: %.2f ms · %s · %s · am Blickpunkt %.1f m/Tag, Spalten %.0f %%",
                iceMs, glacierGl.info(), glacier.describe(), Math.hypot(vv[0], vv[1]), glacier.openness(t[0], t[2]) * 100);
    }

    /** Ob noch Kacheln gebaut werden (Standbild: warten). */
    boolean busy() { return glacierGl != null && glacierGl.busy(); }

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

    static final String ROCK_V = "#version 430 core\nlayout(location = 0) in vec3 aPos;\nuniform mat4 uVP; out vec3 w;\nvoid main() { w = aPos; gl_Position = uVP * vec4(aPos, 1.0); }\n";
    static final String ROCK_F = """
            #version 430 core
            in vec3 w; out vec4 o; uniform vec3 uSun, uSunC, uTop, uHor, uEye;
            float h(vec2 p) { return fract(sin(dot(floor(p), vec2(12.9898, 78.233))) * 43758.5453); }
            void main() {
                vec3 n = normalize(cross(dFdx(w), dFdy(w)));
                if (n.y < 0.0) n = -n;
                float sl = 1.0 - n.y;
                vec3 rock = mix(vec3(0.15, 0.135, 0.12), vec3(0.24, 0.24, 0.245), smoothstep(100.0, 600.0, w.y)) * (0.8 + 0.4 * h(w.xz * 0.2));
                vec3 alb = mix(vec3(0.075, 0.095, 0.04), rock, smoothstep(0.1, 0.35, sl) + smoothstep(250.0, 450.0, w.y));
                if (w.y < 0.5) alb = vec3(0.14, 0.14, 0.13);
                vec3 c = alb * (uSunC * max(dot(n, uSun), 0.0) + uTop * (0.6 + 0.4 * n.y));
                c = mix(c, uHor, 1.0 - exp(-length(uEye - w) / 25000.0));
                o = vec4(c, 1.0);
            }
            """;

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (glacierGl != null) glacierGl.dispose(gl);
        iceGl.dispose(gl);
        for (int p : new int[]{progSky, progRock, progWater, progTone}) gl.glDeleteProgram(p);
        gl.glDeleteQueries(2, query, 0);
    }
}
