package com.dan.mudflat.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.mudflat.Mudflat;
import com.dan.mudflat.WetField;
import com.dan.mudflat.gl.MudflatGl;
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

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * Vorschau des Materials: ein Stück Watt (das Feld hat 64 × 64 m zu 1 m) unter Sonne und Himmel, gezeichnet mit dem
 * Baustein {@code mudflat.glsl} auf einer Ebene bis zum Horizont. Regler für Nässe, Sediment, Rippel, Richtung der
 * letzten Strömung, Sonnenhöhe und Wind; „Trocknen lassen“ rechnet die Nässe im Zeitraffer mit {@link WetField}.
 * <p>
 * Ohne Fenster: {@code --bild datei.png} zeichnet Sand, Mischwatt und Schlick bei Nässe 1, 0,7, 0,4 und trocken, daneben
 * {@code templates/Watt.png} zum Vergleich, wenn es da ist.
 */
public final class MudflatDemo implements GLEventListener {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(200, 170, 120);
    static final String[] VIEWS = {"Augenhöhe, Sonne seitlich", "Flach gegen die Sonne", "Von oben"};

    final Mudflat mud = Mudflat.wadden();
    final WetField field = new WetField(mud, 64, 64, -32, -32, 1);
    final MudflatGl mgl = new MudflatGl();
    volatile double wetness = 1, sediment = 0.3, ripple = 1, flowDeg = 60, sunElev = 35, wind = 6;
    volatile boolean drying;
    volatile int view;
    volatile double hours;
    private int prog, vao;
    private final float[] flat = new float[64 * 64];

    MudflatDemo() { mgl.minIntervalMs = 0; applyState(); }

    /** Nässe, Sediment und Richtung von Hand auf das ganze Feld. */
    void applyState() {
        Arrays.fill(field.wet, (float) wetness);
        Arrays.fill(field.sediment, (float) sediment);
        Arrays.fill(field.dirX, (float) Math.sin(Math.toRadians(flowDeg)));
        Arrays.fill(field.dirZ, (float) -Math.cos(Math.toRadians(flowDeg)));
        field.version++;
    }

    // ------------------------------------------------------------ GL

    static final String VERT = "#version 430 core\nout vec2 vUV;\nvoid main(){ vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); vUV = p; gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0); }\n";

    static final String FRAG_HEAD = "#version 430 core\nin vec2 vUV;\nout vec4 frag;\nuniform vec3 uCam, uFwd, uRight, uUp, uSun;\nuniform float uAspect, uTan, uRes;\n";

    static final String FRAG_MAIN = """
            vec3 sunC() { float e = max(uSun.y, 0.0); return mix(vec3(1.0, 0.55, 0.3), vec3(1.0, 0.95, 0.88), smoothstep(0.05, 0.5, e)) * 3.0 * smoothstep(-0.02, 0.08, e); }
            vec3 sky(vec3 d) {
                float up = clamp(d.y, 0.0, 1.0);
                vec3 c = mix(vec3(0.62, 0.72, 0.85), vec3(0.18, 0.36, 0.75), pow(up, 0.5)) * 0.9;
                float cs = max(dot(d, uSun), 0.0);
                c += vec3(1.0, 0.85, 0.6) * pow(cs, 60.0) * 0.6 + vec3(40.0) * smoothstep(0.9997, 0.99985, cs);
                return c;
            }
            void main() {
                vec3 d = normalize(uFwd + (vUV.x * 2.0 - 1.0) * uTan * uAspect * uRight + (vUV.y * 2.0 - 1.0) * uTan * uUp);
                vec3 col;
                if (d.y >= -0.0005) col = sky(d);
                else {
                    float t = -uCam.y / d.y;
                    vec3 p = uCam + d * t;
                    vec2 xz = p.xz;
                    float px = t * uTan * 2.0 / uRes / max(0.05, -d.y);
                    float above = 0.25 + 0.22 * sin(xz.x / 9.0) * cos(xz.y / 7.0);   // gedachte Höhe über dem Wasser
                    MudSurface m = mudflat(xz, vec3(0.0, 1.0, 0.0), above, px);
                    vec3 n = m.normal, V = -d;
                    vec3 E = sunC() * max(dot(n, uSun), 0.0) + vec3(0.62, 0.70, 0.82) * 0.9;
                    col = m.albedo / 3.14159 * E;
                    vec3 r = reflect(d, n);
                    float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
                    vec3 refl = sky(vec3(r.x, abs(r.y), r.z));
                    col = mix(col, refl, F * m.film);
                    col += refl * F * 0.08 * (1.0 - m.film) * (1.0 - m.rough);
                    float spec = pow(max(dot(r, uSun), 0.0), mix(30.0, 800.0, 1.0 - m.rough)) * (1.0 - m.rough);
                    col += sunC() * spec * F * 6.0;
                    col = mix(col, sky(normalize(vec3(d.x, 0.0, d.z))), 1.0 - exp(-t / 9000.0));
                }
                col *= 0.9;
                col = col * (2.51 * col + 0.03) / (col * (2.43 * col + 0.59) + 0.14);
                frag = vec4(pow(clamp(col, 0.0, 1.0), vec3(1.0 / 2.2)), 1.0);
            }
            """;

    @Override public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        String frag = FRAG_HEAD + MudflatGl.glsl() + FRAG_MAIN;
        prog = gl.glCreateProgram();
        int vs = compile(gl, GL4.GL_VERTEX_SHADER, VERT), fs = compile(gl, GL4.GL_FRAGMENT_SHADER, frag);
        gl.glAttachShader(prog, vs);
        gl.glAttachShader(prog, fs);
        gl.glLinkProgram(prog);
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        vao = v[0];
    }

    static int compile(GL4 gl, int type, String src) {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{src}, null, 0);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL4.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL4.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IllegalStateException("Shader: " + new String(b));
        }
        return s;
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }

    @Override public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (drying) {
            hours += 1 / 60.0 * 0.25;   // 15 Szenenminuten je Sekunde bei 60 Bildern
            double sun = Math.max(0, Math.sin(Math.toRadians(sunElev)));
            field.step(hours, flat, null, null, null, new WetField.Weather(sun, wind, 18, 0));
        }
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT);
        mgl.ripples = ripple;
        mgl.upload(gl, field);
        gl.glUseProgram(prog);
        mgl.bind(gl, prog, 0, field, mud);
        double se = Math.toRadians(sunElev), sa = Math.toRadians(view == 1 ? 180 : 120);
        double[] sun = {Math.cos(se) * Math.sin(sa), Math.sin(se), -Math.cos(se) * Math.cos(sa)};
        double[] cam, fwd;
        if (view == 2) { cam = new double[]{0, 25, 0.01}; fwd = new double[]{0, -1, 0.001}; }
        else if (view == 1) { cam = new double[]{0, 1.7, -14}; fwd = new double[]{0, -0.12, 1}; }
        else { cam = new double[]{0, 1.7, 14}; fwd = new double[]{0, -0.18, -1}; }
        fwd = norm(fwd);
        double[] right = norm(cross(fwd, new double[]{0, 1, 0}));
        if (view == 2) right = new double[]{1, 0, 0};
        double[] up = cross(right, fwd);
        u3(gl, "uCam", cam);
        u3(gl, "uFwd", fwd);
        u3(gl, "uRight", right);
        u3(gl, "uUp", up);
        u3(gl, "uSun", sun);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uAspect"), w / (float) h);
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uTan"), (float) Math.tan(Math.toRadians(25)));
        gl.glUniform1f(gl.glGetUniformLocation(prog, "uRes"), h);
        gl.glBindVertexArray(vao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
    }

    void u3(GL4 gl, String n, double[] v) { gl.glUniform3f(gl.glGetUniformLocation(prog, n), (float) v[0], (float) v[1], (float) v[2]); }
    static double[] cross(double[] a, double[] b) { return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static double[] norm(double[] a) { double l = Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]); return new double[]{a[0] / l, a[1] / l, a[2] / l}; }

    @Override public void dispose(GLAutoDrawable d) { mgl.dispose(d.getGL().getGL4()); }

    // ------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("--bild")) { bild(new File(a[1])); return; }
        SwingUtilities.invokeLater(() -> new MudflatDemo().open());
    }

    static void bild(File out) throws Exception {
        int w = 420, h = 260;
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        MudflatDemo demo = new MudflatDemo();
        d.addGLEventListener(demo);
        double[] wets = {1.0, 0.7, 0.45, 0.22};
        double[] seds = {0.1, 0.5, 0.9};
        String[] sn = {"Sand", "Mischwatt", "Schlick"};
        File ref = new File("templates/Watt.png");
        BufferedImage photo = ref.isFile() ? ImageIO.read(ref) : null;
        int pw = photo == null ? 0 : (int) Math.round(photo.getWidth() * (3.0 * h / photo.getHeight()));
        BufferedImage sheet = new BufferedImage(w * wets.length + pw, h * seds.length, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (int r = 0; r < seds.length; r++)
            for (int c = 0; c < wets.length; c++) {
                demo.sediment = seds[r];
                demo.wetness = wets[c];
                demo.view = c == 0 ? 1 : 0;
                demo.applyState();
                d.display();
                final BufferedImage[] img = new BufferedImage[1];
                d.invoke(true, x -> { img[0] = grab(x, w, h); return true; });
                g.drawImage(img[0], c * w, r * h, null);
                g.setColor(new Color(0, 0, 0, 130));
                g.fillRect(c * w + 6, r * h + 6, 250, 22);
                g.setColor(Color.WHITE);
                g.drawString(String.format(Locale.GERMAN, "%s · Nässe %.0f %%%s", sn[r], wets[c] * 100, c == 0 ? " · gegen die Sonne" : ""), c * w + 12, r * h + 22);
            }
        if (photo != null) {
            g.drawImage(photo, w * wets.length, 0, pw, h * seds.length, null);
            g.setColor(new Color(0, 0, 0, 130));
            g.fillRect(w * wets.length + 6, 6, 230, 22);
            g.setColor(Color.WHITE);
            g.drawString("Vorlage templates/Watt.png", w * wets.length + 12, 22);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath() + (photo == null ? " (ohne Vorlage)" : " (mit Vorlage)"));
        d.destroy();
        System.exit(0);
    }

    static BufferedImage grab(GLAutoDrawable a, int w, int h) {
        GL4 gl = a.getGL().getGL4();
        java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
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

    // ------------------------------------------------------------ Fenster

    private JPanel panel;

    private void open() {
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(this);
        FFrame f = new FFrame("Watt · Vorschau des Pakets com.dan.mudflat");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1400, 840));
        f.setSize(1400, 840);
        f.setBackground(new Color(15, 19, 17));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("WATT");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox vb = new FComboBox(VIEWS);
        vb.addActionListener(e -> view = vb.getSelectedIndex());
        add(vb);
        slider("Nässe", 0, 100, 100, "%", v -> { wetness = v / 100.0; drying = false; applyState(); });
        slider("Sediment (Sand → Schlick)", 0, 100, 30, "", v -> { sediment = v / 100.0; applyState(); });
        slider("Rippel", 0, 100, 100, "%", v -> ripple = v / 100.0);
        slider("Letzte Strömung aus", 0, 359, 60, "°", v -> { flowDeg = v; applyState(); });
        slider("Sonnenhöhe", 2, 70, 35, "°", v -> sunElev = v);
        slider("Wind", 0, 20, 6, " m/s", v -> wind = v);
        FCheckBox dry = new FCheckBox("Trocknen lassen (15 min je s)");
        dry.addActionListener(e -> { if (dry.isSelected()) { wetness = 1; applyState(); hours = 0; } drying = dry.isSelected(); });
        add(dry);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(250, e -> info.setText(String.format(Locale.GERMAN, "<html><body style='width:230px'>Nässe %.0f %% nach %.0f min · Halbwertszeit %.0f min<br>%s</body></html>",
                field.wet[0] * 100, hours * 60, field.halfLife(new WetField.Weather(Math.max(0, Math.sin(Math.toRadians(sunElev))), wind, 18, 0)) * 60, mud.describe()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer set) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(l);
        FSlider s = new FSlider(min, max, val);
        s.addChangeListener(e -> { l.setText(name + "  " + s.getValue() + unit); set.accept(s.getValue()); });
        add(s);
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }
}
