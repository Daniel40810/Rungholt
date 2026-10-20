package com.dan.forest.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.forest.ForestField;
import com.dan.forest.Ground;
import com.dan.forest.Species;
import com.dan.forest.gl.ForestGl;
import com.jogamp.common.nio.Buffers;
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
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung des Grafikteils (Paketvertrag Punkt 3), ohne Fremdcode: ein {@link ForestField} aus Eiche, Buche, Birke
 * und Schwarzerle auf welligem Land, gezeichnet mit {@link ForestGl} samt Sonnenschatten. Drei Bilder:
 * <ul>
 * <li><b>hain:</b> ein lichter Hain aus den vier Arten, 40 m vor der Kamera;</li>
 * <li><b>wald:</b> ein großer Wald mit Lichtungen von einer Anhöhe, 2 km Reichweite (Stufen 3 und 4, Ausdünnung);</li>
 * <li><b>rand:</b> ein Waldrand mit Erlen an einer Senke im Gegenlicht.</li>
 * </ul>
 * Regler: Tag im Jahr, Wind, Sonne. Ohne Fenster: {@code ForestGlDemo --bild datei.png [hain|wald|rand] [breite höhe] [tag]}.
 */
public final class ForestGlDemo implements GLEventListener {
    static final List<Species> SPECIES = List.of(Species.oak(), Species.beech(), Species.birch(), Species.alder());
    static final Ground HILLS = (x, z) -> (float) (7 * Math.sin(x / 310.0) * Math.cos(z / 270.0) + 3 * Math.sin((x + z) / 120.0));
    final ForestGl gl = new ForestGl(SPECIES, 4, 950);
    ForestField field;
    volatile String scene = "hain";
    volatile int day = 289;
    volatile double wind = 6, sunAz = 200, yaw, pitch, dist = 1;
    volatile boolean rebuild = true;
    double frameMs;
    int[] shadow;
    static final int SHADOW = 2048;
    int groundProg, groundVao, groundCount;

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            ForestGlDemo d = new ForestGlDemo();
            d.scene = a.length > 2 ? a[2] : "hain";
            int w = a.length > 3 ? Integer.parseInt(a[3]) : 1280, h = a.length > 4 ? Integer.parseInt(a[4]) : 720;
            if (a.length > 5) d.day = Integer.parseInt(a[5]);
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, w, h);
            dr.addGLEventListener(d);
            dr.display();
            for (int i = 0; i < 600 && !d.gl.ready(); i++) { Thread.sleep(50); dr.display(); }
            d.field.completeNow(d.eyeNow());
            long t0 = System.nanoTime();
            int n = 5;
            for (int i = 0; i < n; i++) {
                dr.display();
                dr.getContext().makeCurrent();
                dr.getGL().glFinish();
                dr.getContext().release();
            }
            double ms = (System.nanoTime() - t0) / 1e6 / n;
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), w, h); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println(String.format(Locale.GERMAN, "Bild: %s · %s · %.1f ms je Bild (mit glFinish)", a[1], d.gl.info(), ms));
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            ForestGlDemo d = new ForestGlDemo();
            FFrame f = new FFrame("Forest-Package · Wald auf der Grafikkarte");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            // 24 Bit Tiefe: die Voreinstellung (16 Bit) lässt ferne Dächer und Zäune mit dem Boden flimmern (Befund Phase 4)
            GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
            caps.setDepthBits(24);
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(d);
            c.setPreferredSize(new Dimension(1200, 640));
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
            FComboBox sc = new FComboBox(new Object[]{"hain", "wald", "rand"});
            FSlider dy = new FSlider(1, 365, 289), wd = new FSlider(0, 24, 6), sun = new FSlider(0, 359, 200);
            JLabel ld = new JLabel("Tag 289"), lw = new JLabel("Wind 6 m/s"), ls = new JLabel("Sonne aus 200°"), info = new JLabel(" ");
            for (JLabel l : new JLabel[]{ld, lw, ls, info}) l.setForeground(new Color(232, 230, 222));
            sc.addActionListener(e -> { d.scene = (String) sc.getSelectedItem(); d.yaw = d.pitch = 0; d.dist = 1; d.rebuild = true; });
            dy.addChangeListener(e -> { d.day = dy.getValue(); ld.setText("Tag " + d.day); });
            wd.addChangeListener(e -> { d.wind = wd.getValue(); lw.setText("Wind " + wd.getValue() + " m/s"); });
            sun.addChangeListener(e -> { d.sunAz = sun.getValue(); ls.setText("Sonne aus " + sun.getValue() + "°"); });
            JPanel south = new JPanel(new GridBagLayout());
            south.setBackground(new Color(15, 19, 17));
            GridBagConstraints g = new GridBagConstraints();
            g.insets = new Insets(2, 6, 2, 6);
            g.fill = GridBagConstraints.HORIZONTAL;
            g.weightx = 1;
            java.awt.Component[][] grid = {{sc, ld, lw, ls}, {new JLabel(), dy, wd, sun}};
            for (int y = 0; y < 2; y++)
                for (int x = 0; x < 4; x++) {
                    grid[y][x].setPreferredSize(new Dimension(200, y == 0 && x > 0 ? 22 : 34));
                    g.gridx = x; g.gridy = y;
                    south.add(grid[y][x], g);
                }
            info.setPreferredSize(new Dimension(400, 22));
            g.gridx = 0; g.gridy = 2; g.gridwidth = 4;
            south.add(info, g);
            root.add(south, BorderLayout.SOUTH);
            new javax.swing.Timer(500, e -> info.setText(String.format(Locale.GERMAN, "%.1f ms · %s", d.frameMs, d.gl.info()))).start();
            f.setPreferredFrameSize(new Dimension(1280, 860));
            f.setSize(1280, 860);
            // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    // ------------------------------------------------------------------ Szenen

    /** Wo was wächst: hain = vier Gruppen, wald = Wald mit Lichtungen, rand = Wald mit Erlen in der Senke. */
    ForestField.Density density() {
        return switch (scene) {
            case "hain" -> (x, z, s) -> {
                if (Math.abs(z + 10) > 22 || Math.abs(x) > 70) return 0;
                int grp = (int) Math.floor((x + 70) / 35.0);
                return grp == s ? 0.55 : 0;
            };
            case "rand" -> (x, z, s) -> {
                double y = HILLS.height(x, z);
                double wood = smooth(-40, 10, -z) * (0.85 + 0.15 * Math.sin(x / 23.0));
                double wet = smooth(1.0, -2.5, y);
                return switch (s) {
                    case 0 -> wood * 0.45 * (1 - wet);
                    case 1 -> wood * 0.55 * (1 - wet);
                    case 2 -> smooth(-60, -20, -z) * (1 - smooth(-20, 15, -z)) * 0.25 * (1 - wet);
                    default -> wet * 0.7 * smooth(-60, -5, -z);
                };
            };
            default -> (x, z, s) -> {
                double clearing = smooth(0.42, 0.55, 0.5 + 0.5 * Math.sin(x / 180.0 + 1.3) * Math.cos(z / 150.0 - 0.4));
                double near = smooth(250, 420, Math.hypot(x, z - 600));
                double wood = (1 - clearing) * near;
                return switch (s) { case 0 -> wood * 0.4; case 1 -> wood * 0.5; case 2 -> wood * 0.08; default -> 0.0; };
            };
        };
    }

    double[][] camera() {
        return switch (scene) {
            case "wald" -> new double[][]{{0, 42, 600}, {0, 0, -500}};
            case "rand" -> new double[][]{{30, 3.5, 90}, {0, 9, -10}};
            default -> new double[][]{{0, 5, 48}, {0, 8, -10}};
        };
    }

    double[] eyeNow() { double[][] c = camera(); return orbit(c[0], c[1]); }

    void build() {
        field = new ForestField(SPECIES.size(), 4, density(), HILLS, 7);
        field.setHeights(gl.heights());
        if (scene.equals("wald")) { field.radius = 2200; field.spacing = 7; }
        else { field.radius = 500; field.spacing = scene.equals("hain") ? 9 : 6.5; }
    }

    // ------------------------------------------------------------------ GL

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        gl.setSeason(day, 0);
        shadow = shadowTarget(g, SHADOW);
        groundProg = program(g, GROUND_VS, GROUND_FS);
        // Boden: 4 km Gitter zu 8 m
        int n = 500;
        double s = 8, o = -n * s / 2;
        float[] v = new float[(n + 1) * (n + 1) * 3];
        for (int j = 0, k = 0; j <= n; j++)
            for (int i = 0; i <= n; i++) { double x = o + i * s, z = o + j * s; v[k++] = (float) x; v[k++] = HILLS.height(x, z); v[k++] = (float) z; }
        int[] idx = new int[n * n * 6];
        for (int j = 0, k = 0; j < n; j++)
            for (int i = 0; i < n; i++) {
                int a = j * (n + 1) + i, b = a + 1, c = a + n + 1, e = c + 1;
                idx[k++] = a; idx[k++] = c; idx[k++] = b; idx[k++] = b; idx[k++] = c; idx[k++] = e;
            }
        int[] t = new int[3];
        g.glGenVertexArrays(1, t, 0);
        groundVao = t[0];
        g.glGenBuffers(2, t, 1);
        g.glBindVertexArray(groundVao);
        g.glBindBuffer(GL.GL_ARRAY_BUFFER, t[1]);
        g.glBufferData(GL.GL_ARRAY_BUFFER, v.length * 4L, Buffers.newDirectFloatBuffer(v), GL.GL_STATIC_DRAW);
        g.glEnableVertexAttribArray(0);
        g.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 12, 0);
        g.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER, t[2]);
        g.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER, idx.length * 4L, Buffers.newDirectIntBuffer(idx), GL.GL_STATIC_DRAW);
        groundCount = idx.length;
        g.glBindVertexArray(0);
    }

    @Override public void display(GLAutoDrawable d) {
        long t0 = System.nanoTime();
        GL4 g = d.getGL().getGL4();
        if (rebuild) { rebuild = false; build(); }
        gl.setSeason(day, 0);
        double t = System.nanoTime() / 1e9;
        double az = Math.toRadians(sunAz), el = Math.toRadians(scene.equals("rand") ? 14 : 26);
        double[] sunDir = {Math.sin(az) * Math.cos(el), Math.sin(el), -Math.cos(az) * Math.cos(el)};
        gl.setWind(Math.sin(Math.toRadians(250)), -Math.cos(Math.toRadians(250)), wind, 0.6);
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        double[][] cam = camera();
        double[] at = cam[1], eye = orbit(cam[0], at);
        float[] vp = mul(persp(50, w / (double) h, 0.3, 12000), look(eye, at));
        // Schatten: alle Bäume im Umkreis (ohne Blickkegel), dann die Auswahl für das Bild
        double half = scene.equals("wald") ? 420 : 110;
        double[] fc = {at[0] * 0.5 + eye[0] * 0.5, 0, at[2] * 0.5 + eye[2] * 0.5};
        float[] lightVP = sunMatrix(sunDir, fc[0], fc[1], fc[2], half);
        field.update(eye, null, 6);
        gl.update(g, field);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, shadow[1]);
        g.glViewport(0, 0, SHADOW, SHADOW);
        g.glClearDepth(1);
        g.glClear(GL.GL_DEPTH_BUFFER_BIT);
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LESS);
        gl.drawShadow(g, lightVP, t, 3);
        field.update(eye, vp, 6);
        gl.update(g, field);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, d.getContext().getDefaultDrawFramebuffer());
        g.glViewport(0, 0, w, h);
        double[] fog = {1.0, 1.12, 1.30};
        float[] clr = tone(fog, 0.55);
        g.glClearColor(clr[0], clr[1], clr[2], 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        ForestGl.Light l = new ForestGl.Light();
        l.sunDir = sunDir;
        l.sunColor = new double[]{2.7, 2.4, 2.0};
        l.skyColor = new double[]{0.30, 0.36, 0.46};
        l.fogColor = fog;
        l.fogDistance = scene.equals("wald") ? 3200 : 1500;
        l.sunShadowTexture = shadow[0];
        l.sunShadowMatrix = lightVP;
        l.sunShadowBias = scene.equals("wald") ? 0.0012 : 0.0008;
        // Boden
        g.glUseProgram(groundProg);
        g.glUniformMatrix4fv(g.glGetUniformLocation(groundProg, "uVP"), 1, false, vp, 0);
        g.glUniformMatrix4fv(g.glGetUniformLocation(groundProg, "uSM"), 1, false, lightVP, 0);
        g.glUniform3f(g.glGetUniformLocation(groundProg, "uCam"), (float) eye[0], (float) eye[1], (float) eye[2]);
        g.glUniform3f(g.glGetUniformLocation(groundProg, "uSun"), (float) sunDir[0], (float) sunDir[1], (float) sunDir[2]);
        g.glUniform3f(g.glGetUniformLocation(groundProg, "uFog"), (float) fog[0], (float) fog[1], (float) fog[2]);
        g.glUniform1f(g.glGetUniformLocation(groundProg, "uFogDist"), (float) l.fogDistance);
        g.glActiveTexture(GL.GL_TEXTURE6);
        g.glBindTexture(GL.GL_TEXTURE_2D, shadow[0]);
        g.glActiveTexture(GL.GL_TEXTURE0);
        g.glUniform1i(g.glGetUniformLocation(groundProg, "uShadow"), 6);
        g.glEnable(GL.GL_CULL_FACE);
        g.glCullFace(GL.GL_BACK);
        g.glBindVertexArray(groundVao);
        g.glDrawElements(GL.GL_TRIANGLES, groundCount, GL.GL_UNSIGNED_INT, 0);
        g.glBindVertexArray(0);
        g.glDisable(GL.GL_CULL_FACE);
        ForestGl.View v = new ForestGl.View();
        v.viewProj = vp;
        v.eye = eye;
        v.tonemap = true;
        v.exposure = 0.55;
        gl.draw(g, v, l, t);
        frameMs = frameMs * 0.9 + (System.nanoTime() - t0) / 1e6 * 0.1;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    double[] orbit(double[] eye, double[] at) {
        double dx = eye[0] - at[0], dy = eye[1] - at[1], dz = eye[2] - at[2];
        double r0 = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double r = r0 * dist, a = Math.atan2(dx, dz) + Math.toRadians(yaw);
        double e = Math.asin(dy / r0) + Math.toRadians(pitch);
        e = Math.max(Math.toRadians(-20), Math.min(Math.toRadians(85), e));
        double[] o = {at[0] + r * Math.cos(e) * Math.sin(a), at[1] + r * Math.sin(e), at[2] + r * Math.cos(e) * Math.cos(a)};
        o[1] = Math.max(o[1], HILLS.height(o[0], o[2]) + 1.6);
        return o;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static float[] tone(double[] c, double exp) {
        float[] o = new float[3];
        for (int i = 0; i < 3; i++) {
            double x = c[i] * exp;
            double y = Math.max(0, Math.min(1, (x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14)));
            o[i] = (float) Math.pow(y, 1 / 2.2);
        }
        return o;
    }

    static BufferedImage grab(GL4 g, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        g.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                im.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return im;
    }

    // ------------------------------------------------------------------ Boden und Schatten (nur für die Demo)

    static final String GROUND_VS = """
            layout(location = 0) in vec3 aPos;
            uniform mat4 uVP;
            out vec3 vW;
            void main() { vW = aPos; gl_Position = uVP * vec4(aPos, 1.0); }
            """;
    static final String GROUND_FS = """
            in vec3 vW;
            out vec4 frag;
            uniform vec3 uCam, uSun, uFog;
            uniform float uFogDist;
            uniform mat4 uSM;
            uniform sampler2DShadow uShadow;
            void main() {
                vec3 n = normalize(cross(dFdx(vW), dFdy(vW)));
                if (n.y < 0.0) n = -n;
                vec4 q = uSM * vec4(vW, 1.0);
                vec3 s = q.xyz / q.w * 0.5 + 0.5;
                float sh = 1.0;
                if (all(greaterThan(s.xy, vec2(0.0))) && all(lessThan(s.xy, vec2(1.0)))) {
                    vec2 tx = 1.0 / vec2(textureSize(uShadow, 0));
                    sh = 0.0;
                    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) sh += texture(uShadow, vec3(s.xy + vec2(i, j) * tx, s.z - 0.0015));
                    sh /= 9.0;
                }
                float k = 0.5 + 0.5 * sin(vW.x * 0.05) * sin(vW.z * 0.043);
                vec3 alb = mix(vec3(0.07, 0.085, 0.035), vec3(0.11, 0.09, 0.05), k);
                vec3 c = alb * (vec3(2.7, 2.4, 2.0) * max(dot(n, uSun), 0.0) * sh + vec3(0.30, 0.36, 0.46));
                float d = length(uCam - vW);
                c = mix(c, uFog, 1.0 - exp(-d / uFogDist));
                vec3 x = c * 0.55;
                c = pow(clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0), vec3(1.0 / 2.2));
                frag = vec4(c, 1.0);
            }
            """;

    static int program(GL4 g, String vs, String fs) {
        int p = g.glCreateProgram();
        for (int k = 0; k < 2; k++) {
            int sh = g.glCreateShader(k == 0 ? GL4.GL_VERTEX_SHADER : GL4.GL_FRAGMENT_SHADER);
            g.glShaderSource(sh, 1, new String[]{"#version 430 core\n" + (k == 0 ? vs : fs)}, null, 0);
            g.glCompileShader(sh);
            int[] ok = new int[1];
            g.glGetShaderiv(sh, GL4.GL_COMPILE_STATUS, ok, 0);
            if (ok[0] == 0) {
                byte[] b = new byte[4096];
                int[] len = new int[1];
                g.glGetShaderInfoLog(sh, b.length, len, 0, b, 0);
                throw new IllegalStateException(new String(b, 0, len[0]));
            }
            g.glAttachShader(p, sh);
        }
        g.glLinkProgram(p);
        return p;
    }

    /** Tiefentextur mit Vergleich und ihr Bildspeicher {Textur, FBO}. */
    static int[] shadowTarget(GL4 g, int size) {
        int[] t = new int[1], f = new int[1];
        g.glGenTextures(1, t, 0);
        g.glBindTexture(GL.GL_TEXTURE_2D, t[0]);
        g.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL4.GL_DEPTH_COMPONENT24, size, size, 0, GL4.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_MODE, GL4.GL_COMPARE_REF_TO_TEXTURE);
        g.glTexParameteri(GL.GL_TEXTURE_2D, GL4.GL_TEXTURE_COMPARE_FUNC, GL.GL_LEQUAL);
        g.glGenFramebuffers(1, f, 0);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, f[0]);
        g.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, t[0], 0);
        g.glDrawBuffer(GL.GL_NONE);
        g.glReadBuffer(GL.GL_NONE);
        g.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        return new int[]{t[0], f[0]};
    }

    // kleine Matrizen (Spaltenreihenfolge)
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
        m[0] = (float) sx; m[4] = 0; m[8] = (float) sz;
        m[1] = (float) ux; m[5] = (float) uy; m[9] = (float) uz;
        m[2] = (float) -fx; m[6] = (float) -fy; m[10] = (float) -fz;
        m[12] = (float) -(sx * e[0] + sz * e[2]);
        m[13] = (float) -(ux * e[0] + uy * e[1] + uz * e[2]);
        m[14] = (float) (fx * e[0] + fy * e[1] + fz * e[2]);
        m[15] = 1;
        return m;
    }

    static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) for (int row = 0; row < 4; row++) { float s = 0; for (int k = 0; k < 4; k++) s += a[k * 4 + row] * b[c * 4 + k]; r[c * 4 + row] = s; }
        return r;
    }

    /** Licht-Projektion·Blick: senkrecht zur Sonne, Würfel der halben Kante half um (cx, cy, cz). */
    static float[] sunMatrix(double[] sun, double cx, double cy, double cz, double half) {
        double[] f = {-sun[0], -sun[1], -sun[2]};
        double fl = Math.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        f[0] /= fl; f[1] /= fl; f[2] /= fl;
        double[] e = {cx - f[0] * half * 2, cy - f[1] * half * 2, cz - f[2] * half * 2};
        float[] view = look(e, new double[]{cx, cy, cz});
        double n = 0.1, fa = half * 4;
        float[] proj = new float[16];
        proj[0] = (float) (1 / half); proj[5] = (float) (1 / half); proj[10] = (float) (-2 / (fa - n)); proj[14] = (float) (-(fa + n) / (fa - n)); proj[15] = 1;
        return mul(proj, view);
    }
}
