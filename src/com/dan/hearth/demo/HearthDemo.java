package com.dan.hearth.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.hearth.Blaze;
import com.dan.hearth.Fire;
import com.dan.hearth.Flame;
import com.dan.hearth.gl.HearthGl;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Eigene Vorführung (Paketvertrag Punkt 3 und 10), ohne Haithabu-Code. Zwei Szenen bei Nacht:
 * <ul>
 * <li><b>feuer:</b> eine Feuerstelle, zwei Fackeln an einem Tor, eine Esse; ihr Licht auf Boden und Wänden.</li>
 * <li><b>brand:</b> ein Haus brennt ab. Zwei Reihen Reethäuser, das erste wird angezündet; der Wind trägt das Feuer
 * weiter. Dächer stürzen ein, die Wände verkohlen, am Ende glimmt die Glut. Die Brandzeit läuft im Zeitraffer.</li>
 * </ul>
 * Fenster: Ziehen dreht, Rad zoomt. Ohne Fenster: {@code HearthDemo --bild datei.png [feuer|brand] [breite höhe] [sekunden Brandzeit]}.
 */
public final class HearthDemo implements GLEventListener {
    final HearthGl gl = new HearthGl();
    final List<Fire> fires = new ArrayList<>();
    Blaze blaze;
    volatile String scene = "brand";
    volatile double yaw, pitch, dist = 1, lapse = 15, windSpeed = 4;
    volatile boolean rebuild = true;
    double time, frameMs;
    long last;
    int boxProg, postProg, emptyVao, boxVao, boxBuf, hdrFbo, hdrTex, hdrDepth, hdrW, hdrH;
    int boxCount;

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            HearthDemo d = new HearthDemo();
            d.scene = a.length > 2 ? a[2] : "brand";
            int w = a.length > 3 ? Integer.parseInt(a[3]) : 1280, h = a.length > 4 ? Integer.parseInt(a[4]) : 720;
            double secs = a.length > 5 ? Double.parseDouble(a[5]) : 600;
            d.build();
            d.rebuild = false;
            if (d.blaze != null) d.blaze.step(secs);
            d.time = 12.3;
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
            d.last = -1;
            d.lapse = 0;
            dr.addGLEventListener(d);
            dr.display();
            dr.display();
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), w, h); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println("Bild: " + a[1] + " · " + d.gl.info() + (d.blaze != null ? " · " + d.blaze.describe() : ""));
            if (d.blaze != null) d.blaze.log().forEach(s -> System.out.println("  " + s));
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            HearthDemo d = new HearthDemo();
            FFrame f = new FFrame("Hearth-Package · Feuer, Licht und Brand");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
            caps.setDepthBits(24);
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(d);
            root.add(c, BorderLayout.CENTER);
            MouseAdapter ma = new MouseAdapter() {
                int lx, ly;
                @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
                @Override public void mouseDragged(MouseEvent e) {
                    d.yaw += (e.getX() - lx) * 0.3;
                    d.pitch = Math.max(-25, Math.min(60, d.pitch + (e.getY() - ly) * 0.2));
                    lx = e.getX(); ly = e.getY();
                }
                @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) { d.dist = Math.max(0.2, Math.min(4, d.dist * Math.pow(1.1, e.getWheelRotation()))); }
            };
            c.addMouseListener(ma);
            c.addMouseMotionListener(ma);
            c.addMouseWheelListener(ma);
            FComboBox sc = new FComboBox(new Object[]{"brand", "feuer"});
            FSlider lap = new FSlider(1, 60, 15), wind = new FSlider(0, 120, 40);
            JLabel ll = new JLabel("Zeitraffer 15×"), lw = new JLabel("Wind 4,0 m/s"), info = new JLabel(" ");
            for (JLabel l : new JLabel[]{ll, lw, info}) l.setForeground(new Color(232, 230, 222));
            sc.addActionListener(e -> { d.scene = (String) sc.getSelectedItem(); d.yaw = d.pitch = 0; d.dist = 1; d.rebuild = true; });
            lap.addChangeListener(e -> { d.lapse = lap.getValue(); ll.setText("Zeitraffer " + lap.getValue() + "×"); });
            wind.addChangeListener(e -> { d.windSpeed = wind.getValue() / 10.0; lw.setText(String.format(Locale.GERMAN, "Wind %.1f m/s", d.windSpeed)); });
            JPanel south = new JPanel(new java.awt.GridBagLayout());
            south.setBackground(new Color(15, 19, 17));
            south.setBorder(javax.swing.BorderFactory.createEmptyBorder(6, 10, 6, 10));
            java.awt.GridBagConstraints g = new java.awt.GridBagConstraints();
            g.insets = new java.awt.Insets(2, 6, 2, 6);
            g.fill = java.awt.GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            java.awt.Component[][] grid = {{new JLabel(" "), ll, lw}, {sc, lap, wind}};
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 3; x++) {
                    java.awt.Component k = grid[y][x];
                    k.setPreferredSize(new java.awt.Dimension(200, y == 0 ? 22 : 34));
                    g.gridx = x; g.gridy = y;
                    south.add(k, g);
                }
            info.setPreferredSize(new java.awt.Dimension(400, 22));
            g.gridx = 0; g.gridy = 2; g.gridwidth = 3;
            south.add(info, g);
            root.add(south, BorderLayout.SOUTH);
            c.setPreferredSize(new java.awt.Dimension(1200, 640));
            new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN, "%.1f ms · %s%s", d.frameMs, d.gl.info(), d.blaze != null ? " · " + d.blaze.describe() : ""))).start();
            f.setPreferredFrameSize(new java.awt.Dimension(1280, 860));
            f.setSize(1280, 860);
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    // ------------------------------------------------------------------ Szenen

    void build() {
        fires.clear();
        blaze = null;
        if (scene.equals("feuer")) {
            Fire camp = new Fire(Fire.Kind.FEUERSTELLE, 0, 0, 0, 1);
            camp.scale = 1.4;
            fires.add(camp);
            fires.add(new Fire(Fire.Kind.FACKEL, -9, 2.4, -6, 2));
            fires.add(new Fire(Fire.Kind.FACKEL, -9, 2.4, 6, 3));
            fires.add(new Fire(Fire.Kind.SCHMIEDE, 9, 0.9, -3, 4));
        } else {
            List<Blaze.Building> l = new ArrayList<>();
            for (int i = 0; i < 8; i++) l.add(new Blaze.Building((i % 4) * 15 - 22, 0, (i / 4) * 14 - 7, 90, 6, 2.5, 2, 6, true));
            blaze = new Blaze(l, 950);
            blaze.windFromDeg = 270;
            blaze.windSpeed = windSpeed;
            blaze.ignite(0);
        }
        boxCount = -1;
    }

    // ------------------------------------------------------------------ GL

    static final String BOX_VS = """
            layout(location = 0) in vec3 aPos;
            layout(location = 1) in vec3 aNrm;
            layout(location = 2) in float aChar;
            uniform mat4 uVP;
            out vec3 vN; out vec3 vW; out float vChar;
            void main() { vN = aNrm; vW = aPos; vChar = aChar; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String BOX_FS = """
            in vec3 vN; in vec3 vW; in float vChar;
            out vec4 frag;
            uniform vec3 uMoon, uSky, uCam;
            uniform int uN; uniform vec4 uLP[8]; uniform vec4 uLC[8];
            void main() {
                vec3 n = normalize(vN);
                vec3 alb = vW.y < 0.05 ? vec3(0.06, 0.055, 0.04) : n.y > 0.3 ? vec3(0.17, 0.14, 0.08) : vec3(0.22, 0.18, 0.12);
                alb = mix(alb, vec3(0.02, 0.018, 0.016), vChar);
                vec3 e = uMoon * max(dot(n, normalize(vec3(0.3, 0.8, 0.2))), 0.0) + uSky * (0.5 + 0.5 * n.y);
                for (int i = 0; i < 8; i++) { if (i >= uN) break; vec3 d = uLP[i].xyz - vW; float d2 = dot(d, d) + 0.5;
                    e += uLC[i].rgb * max(dot(n, normalize(d)), 0.0) / d2; }
                frag = vec4(alb * e + vec3(1.2, 0.3, 0.05) * vChar * vChar * 0.02 * step(0.95, vChar), 1.0);
            }
            """;
    static final String POST_VS = """
            out vec2 vUv;
            void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUv = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }
            """;
    static final String POST_FS = """
            in vec2 vUv; out vec4 frag; uniform sampler2D uHdr; uniform float uExp;
            vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
            void main() { frag = vec4(pow(aces(texture(uHdr, vUv).rgb * uExp), vec3(1.0 / 2.2)), 1.0); }
            """;

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        boxProg = program(g, BOX_VS, BOX_FS);
        postProg = program(g, POST_VS, POST_FS);
        int[] v = new int[2];
        g.glGenVertexArrays(2, v, 0);
        emptyVao = v[0];
        boxVao = v[1];
        g.glGenBuffers(1, v, 0);
        boxBuf = v[0];
        g.glBindVertexArray(boxVao);
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, boxBuf);
        g.glEnableVertexAttribArray(0);
        g.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 28, 0);
        g.glEnableVertexAttribArray(1);
        g.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 28, 12);
        g.glEnableVertexAttribArray(2);
        g.glVertexAttribPointer(2, 1, GL.GL_FLOAT, false, 28, 24);
        g.glBindVertexArray(0);
    }

    /** Boden und Häuser (verkohlt, Dach nach dem Einsturz weg, Wände sinken in der Glut). */
    void uploadBoxes(GL4 g) {
        List<float[]> t = new ArrayList<>();
        double half = 200;
        quad(t, new double[]{-half, 0, -half}, new double[]{half, 0, -half}, new double[]{half, 0, half}, new double[]{-half, 0, half}, 0);
        if (blaze != null) {
            for (int i = 0; i < blaze.size(); i++) {
                Blaze.Building b = blaze.building(i);
                double ch = Math.min(1, blaze.char_(i) * 1.6);
                double eave = b.eave() * (1 - 0.7 * Math.max(0, blaze.char_(i) - 0.5) * 2);
                house(t, b.x(), b.z(), b.halfLength(), b.halfWidth(), eave, blaze.collapsed(i) ? -1 : b.ridge(), ch);
            }
        } else {
            house(t, -11, -10, 4, 3, 2.6, 4.5, 0);
            house(t, -11, 10, 4, 3, 2.6, 4.5, 0);
            house(t, 14, -3, 3, 2.5, 2.0, 3.6, 0);
            house(t, 2, -16, 6, 2.5, 2.0, 6, 0);
        }
        float[] buf = new float[t.size() * 7];
        int o = 0;
        for (float[] v : t) { System.arraycopy(v, 0, buf, o, 7); o += 7; }
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, boxBuf);
        g.glBufferData(GL.GL_ARRAY_BUFFER, buf.length * 4L, com.jogamp.common.nio.Buffers.newDirectFloatBuffer(buf), GL.GL_DYNAMIC_DRAW);
        boxCount = t.size();
    }

    static void house(List<float[]> t, double x, double z, double hl, double hw, double eave, double ridge, double ch) {
        double[][] p = {{x - hl, 0, z - hw}, {x + hl, 0, z - hw}, {x + hl, 0, z + hw}, {x - hl, 0, z + hw}};
        for (int k = 0; k < 4; k++) {
            double[] a = p[k], b = p[(k + 1) % 4];
            quad(t, a, b, new double[]{b[0], eave, b[2]}, new double[]{a[0], eave, a[2]}, ch);
        }
        if (ridge > 0) {
            quad(t, new double[]{x - hl - 0.4, eave - 0.3, z - hw - 0.6}, new double[]{x + hl + 0.4, eave - 0.3, z - hw - 0.6}, new double[]{x + hl + 0.4, ridge, z}, new double[]{x - hl - 0.4, ridge, z}, ch * 0.8);
            quad(t, new double[]{x + hl + 0.4, eave - 0.3, z + hw + 0.6}, new double[]{x - hl - 0.4, eave - 0.3, z + hw + 0.6}, new double[]{x - hl - 0.4, ridge, z}, new double[]{x + hl + 0.4, ridge, z}, ch * 0.8);
        }
    }

    static void quad(List<float[]> t, double[] a, double[] b, double[] c, double[] d, double ch) {
        double[] u = {b[0] - a[0], b[1] - a[1], b[2] - a[2]}, w = {d[0] - a[0], d[1] - a[1], d[2] - a[2]};
        double[] n = {u[1] * w[2] - u[2] * w[1], u[2] * w[0] - u[0] * w[2], u[0] * w[1] - u[1] * w[0]};
        double l = Math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]);
        if (n[1] < -1e-6) l = -l;
        for (double[] q : new double[][]{a, b, c, a, c, d}) t.add(new float[]{(float) q[0], (float) q[1], (float) q[2], (float) (n[0] / l), (float) (n[1] / l), (float) (n[2] / l), (float) ch});
    }

    @Override public void display(GLAutoDrawable d) {
        long now = System.nanoTime();
        if (rebuild) { rebuild = false; build(); }
        double dt = last > 0 ? Math.min(0.1, (now - last) / 1e9) : 0;
        last = now;
        time += dt;
        for (Fire f : fires) f.step(dt);
        if (blaze != null) { blaze.windSpeed = windSpeed; blaze.step(dt * lapse); }
        GL4 g = d.getGL().getGL4();
        uploadBoxes(g);
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        ensureHdr(g, w, h);
        double[] eye, at;
        if (scene.equals("feuer")) { eye = new double[]{-6, 3.5, 22}; at = new double[]{0, 1, -2}; }
        else { eye = new double[]{-55, 18, 50}; at = new double[]{0, 4, 0}; }
        eye = orbit(eye, at);
        float[] vp = mul(persp(50, w / (double) h, 0.3, 4000), look(eye, at));
        // Licht: Mond und Nachthimmel, die stärksten Feuer als Punktlichter (Einheit: Ausgabe = cd/m² · lumScale)
        double lum = 1 / 40.0;
        List<double[][]> lights = new ArrayList<>();
        List<Flame> flames = new ArrayList<>();
        for (Fire f : fires) {
            double[] p = f.lightPosition(), c = f.intensity(time);
            lights.add(new double[][]{p, c});
            Flame fl = f.flame();
            if (fl != null) flames.add(fl);
        }
        if (blaze != null)
            for (int i = 0; i < blaze.size(); i++) {
                double[][] l = blaze.light(i, time);
                if (l != null) lights.add(l);
                flames.addAll(blaze.flames(i));
            }
        lights.sort((p, q) -> Double.compare(q[1][1], p[1][1]));
        float[] lp = new float[32], lc = new float[32];
        int n = Math.min(8, lights.size());
        for (int i = 0; i < n; i++) for (int k = 0; k < 3; k++) { lp[i * 4 + k] = (float) lights.get(i)[0][k]; lc[i * 4 + k] = (float) (lights.get(i)[1][k] * lum / Math.PI); }
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        g.glViewport(0, 0, w, h);
        g.glClearColor(0.004f, 0.006f, 0.012f, 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LESS);
        g.glUseProgram(boxProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(boxProg, "uVP"), 1, false, vp, 0);
        g.glUniform3f(g.glGetUniformLocation(boxProg, "uMoon"), 0.02f, 0.025f, 0.035f);
        g.glUniform3f(g.glGetUniformLocation(boxProg, "uSky"), 0.006f, 0.008f, 0.014f);
        g.glUniform1i(g.glGetUniformLocation(boxProg, "uN"), n);
        g.glUniform4fv(g.glGetUniformLocation(boxProg, "uLP[0]"), 8, lp, 0);
        g.glUniform4fv(g.glGetUniformLocation(boxProg, "uLC[0]"), 8, lc, 0);
        g.glBindVertexArray(boxVao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, boxCount);
        g.glBindVertexArray(0);
        gl.update(g, flames);
        HearthGl.View v = new HearthGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.near = 0.3;
        v.far = 4000;
        HearthGl.Light l = new HearthGl.Light();
        l.lumScale = lum;
        l.skyTop = new double[]{0.006, 0.008, 0.014};
        l.skyHorizon = new double[]{0.01, 0.012, 0.018};
        l.fogColor = l.skyHorizon;
        l.fogDistance = 3000;
        l.windX = 1;
        l.windZ = 0;
        l.windSpeed = windSpeed;
        gl.draw(g, v, l, time);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, w, h);
        g.glDisable(GL.GL_DEPTH_TEST);
        g.glUseProgram(postProg);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glUniform1i(g.glGetUniformLocation(postProg, "uHdr"), 0);
        g.glUniform1f(g.glGetUniformLocation(postProg, "uExp"), 2.5f);
        g.glBindVertexArray(emptyVao);
        g.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        g.glBindVertexArray(0);
        frameMs = frameMs * 0.9 + (System.nanoTime() - now) / 1e6 * 0.1;
    }

    void ensureHdr(GL4 g, int w, int h) {
        if (hdrFbo != 0 && w == hdrW && h == hdrH) return;
        int[] t = new int[2];
        g.glGenTextures(2, t, 0);
        hdrTex = t[0];
        hdrDepth = t[1];
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        g.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        g.glBindTexture(GL.GL_TEXTURE_2D, hdrDepth);
        g.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT24, w, h, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_FLOAT, null);
        int[] f = new int[1];
        g.glGenFramebuffers(1, f, 0);
        hdrFbo = f[0];
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, hdrTex, 0);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, hdrDepth, 0);
        hdrW = w;
        hdrH = h;
    }

    double[] orbit(double[] eye, double[] at) {
        double dx = eye[0] - at[0], dy = eye[1] - at[1], dz = eye[2] - at[2];
        double r0 = Math.sqrt(dx * dx + dy * dy + dz * dz), r = r0 * dist, a = Math.atan2(dx, dz) + Math.toRadians(yaw);
        double e = Math.asin(dy / r0) + Math.toRadians(pitch);
        e = Math.max(Math.toRadians(1), Math.min(Math.toRadians(85), e));
        return new double[]{at[0] + r * Math.cos(e) * Math.sin(a), at[1] + r * Math.sin(e), at[2] + r * Math.cos(e) * Math.cos(a)};
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    static int program(GL4 g, String vs, String fs) {
        int p = g.glCreateProgram();
        for (int[] s : new int[][]{{GL4.GL_VERTEX_SHADER, 0}, {GL4.GL_FRAGMENT_SHADER, 1}}) {
            int sh = g.glCreateShader(s[0]);
            g.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + (s[1] == 0 ? vs : fs)}, null, 0);
            g.glCompileShader(sh);
            int[] ok = new int[1];
            g.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                g.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException("Demo-Shader: " + new String(b, 0, len[0]));
            }
            g.glAttachShader(p, sh);
        }
        g.glLinkProgram(p);
        return p;
    }

    static BufferedImage grab(GL4 g, int w, int h) {
        ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        g.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    static float[] persp(double fovy, double aspect, double n, double f) {
        double t = 1 / Math.tan(Math.toRadians(fovy) / 2);
        float[] m = new float[16];
        m[0] = (float) (t / aspect); m[5] = (float) t; m[10] = (float) ((f + n) / (n - f)); m[11] = -1; m[14] = (float) (2 * f * n / (n - f));
        return m;
    }

    static float[] look(double[] e, double[] c) {
        double fx = c[0] - e[0], fy = c[1] - e[1], fz = c[2] - e[2], fl = Math.sqrt(fx * fx + fy * fy + fz * fz);
        fx /= fl; fy /= fl; fz /= fl;
        double sx = -fz, sz = fx, sl = Math.hypot(sx, sz);
        sx /= sl; sz /= sl;
        double ux = -sz * fy, uy = sz * fx - sx * fz, uz = sx * fy;
        float[] m = new float[16];
        m[0] = (float) sx; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]); m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]); m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k]; r[c * 4 + row] = s; }
        return r;
    }
}
