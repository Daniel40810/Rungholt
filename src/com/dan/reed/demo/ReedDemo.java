package com.dan.reed.demo;

import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.reed.Reed;
import com.dan.reed.gl.ReedGl;
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
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;

/**
 * Eigene Vorführung (Paketvertrag Punkt 3 und 10), ohne Haithabu-Code: ein Ufer mit Schilfgürtel, Binsen am Wasser
 * und Wiese dahinter, Regler für Beaufort und Jahreszeit. Ohne Fenster schreibt
 * {@code ReedDemo --bild datei.png [bft] [jahreszeit 0..1]} ein Standbild.
 */
public final class ReedDemo implements GLEventListener {
    final Reed reed = Reed.breeze();
    final ReedGl gl = new ReedGl();
    final long t0 = System.nanoTime();
    double fixedT = -1;
    int ground, groundVao;
    static final Reed.Density SHORE = (x, z, k) -> switch (k) {
        case SCHILF -> z > -6 && z < 14 ? 0.9 * (0.6 + 0.4 * Math.sin(x * 0.07) * Math.sin(z * 0.11)) : 0;
        case BINSE -> z > -9 && z < -4 ? 0.7 : 0;
        case GRAS -> z > 16 && z < 40 ? 0.9 : 0;
        case STEPPE -> z >= 40 ? 0.8 : 0;       // seit Glacier: Horstgras der Steppe hinter der Wiese
    };
    static final Reed.Ground FLAT = (x, z) -> 0;

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            ReedDemo d = new ReedDemo();
            d.reed.windSpeed = Reed.beaufort(a.length > 2 ? Integer.parseInt(a[2]) : 3).windSpeed;
            if (a.length > 3) d.reed.season = Double.parseDouble(a[3]);
            d.fixedT = 3;
            GLProfile p = GLProfile.get(GLProfile.GL4);
            GLCapabilities caps = new GLCapabilities(p);
            caps.setOnscreen(false);
            caps.setFBO(true);
            caps.setDepthBits(24);
            GLOffscreenAutoDrawable dr = GLDrawableFactory.getFactory(p).createOffscreenAutoDrawable(null, caps, null, 960, 540);
            dr.addGLEventListener(d);
            for (int i = 0; i < 20; i++) { dr.display(); Thread.sleep(30); }
            BufferedImage[] img = new BufferedImage[1];
            dr.invoke(true, x -> { img[0] = grab(x.getGL().getGL4(), 960, 540); return true; });
            javax.imageio.ImageIO.write(img[0], "png", new File(a[1]));
            System.out.println("Bild: " + a[1] + " · " + d.gl.info() + " · " + d.reed.describe());
            dr.destroy();
            System.exit(0);
        }
        SwingUtilities.invokeLater(() -> {
            ReedDemo d = new ReedDemo();
            FFrame f = new FFrame("Reed-Package · Schilf, Binsen und Gras im Wind");
            JPanel root = f.getComponentPane();
            root.setLayout(new BorderLayout());
            root.setBackground(new Color(15, 19, 17));
            // 24 Bit Tiefe: die Voreinstellung (16 Bit) lässt ferne Dächer und Zäune mit dem Boden flimmern (Befund Phase 4)
            GLCapabilities caps = new GLCapabilities(GLProfile.get(GLProfile.GL4));
            caps.setDepthBits(24);
            GLCanvas c = new GLCanvas(caps);
            c.addGLEventListener(d);
            root.add(c, BorderLayout.CENTER);
            FSlider bft = new FSlider(0, 9, 3), season = new FSlider(0, 100, 75);
            JLabel lb = new JLabel("Beaufort 3"), ls = new JLabel("Jahreszeit 0,75");
            lb.setForeground(new Color(232, 230, 222));
            ls.setForeground(new Color(232, 230, 222));
            bft.addChangeListener(e -> { d.reed.windSpeed = Reed.beaufort(bft.getValue()).windSpeed; d.reed.gust = Reed.beaufort(bft.getValue()).gust; lb.setText("Beaufort " + bft.getValue()); });
            season.addChangeListener(e -> { d.reed.season = season.getValue() / 100.0; ls.setText(String.format("Jahreszeit %.2f", d.reed.season)); });
            JPanel south = new JPanel(new GridLayout(2, 2, 10, 4));
            south.setBackground(new Color(15, 19, 17));
            south.add(lb); south.add(bft); south.add(ls); south.add(season);
            root.add(south, BorderLayout.SOUTH);
            f.setPreferredFrameSize(new java.awt.Dimension(1100, 720));
            f.setSize(1100, 720);
            // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in der App und den Lighthouse-Demos)
            f.setBackground(new Color(15, 19, 17));
            f.setLocationRelativeTo(null);
            f.setVisible(true);
            new Animator(c).start();
        });
    }

    @Override public void init(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        gl.init(g);
        String vs = "#version 430 core\nuniform mat4 uVP; out vec3 vW; void main(){ vec2 p=vec2((gl_VertexID&1)==0?-300.0:300.0,(gl_VertexID&2)==0?-300.0:300.0); vW=vec3(p.x,0.0,p.y); gl_Position=uVP*vec4(vW,1.0);}";
        String fs = "#version 430 core\nin vec3 vW; out vec4 o; void main(){ float wet=smoothstep(-4.0,-12.0,vW.z); vec3 c=mix(vec3(0.10,0.11,0.05),vec3(0.06,0.11,0.12),wet); o=vec4(c,1.0);}";
        ground = g.glCreateProgram();
        int a = sh(g, GL4.GL_VERTEX_SHADER, vs), b = sh(g, GL4.GL_FRAGMENT_SHADER, fs);
        g.glAttachShader(ground, a);
        g.glAttachShader(ground, b);
        g.glLinkProgram(ground);
        int[] v = new int[1];
        g.glGenVertexArrays(1, v, 0);
        groundVao = v[0];
    }

    static int sh(GL4 g, int type, String src) {
        int s = g.glCreateShader(type);
        g.glShaderSource(s, 1, new String[]{src}, null, 0);
        g.glCompileShader(s);
        return s;
    }

    @Override public void display(GLAutoDrawable d) {
        GL4 g = d.getGL().getGL4();
        double t = fixedT >= 0 ? fixedT : (System.nanoTime() - t0) / 1e9;
        int w = d.getSurfaceWidth(), h = d.getSurfaceHeight();
        g.glViewport(0, 0, w, h);
        g.glClearColor(0.55f, 0.62f, 0.70f, 1);
        g.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        double[] eye = {-14, 2.2, 26};
        float[] vp = mul(persp(50, w / (double) h, 0.1, 2000), look(eye, new double[]{4, 1.2, 0}));
        g.glEnable(GL.GL_DEPTH_TEST);
        g.glDepthFunc(GL.GL_LESS);
        g.glUseProgram(ground);
        g.glUniformMatrix4fv(g.glGetUniformLocation(ground, "uVP"), 1, false, vp, 0);
        g.glBindVertexArray(groundVao);
        g.glDrawArrays(GL.GL_TRIANGLE_STRIP, 0, 4);
        gl.update(g, reed, eye[0], eye[2], SHORE, FLAT);
        ReedGl.View v = new ReedGl.View();
        v.viewProj = vp;
        v.eye = eye;
        ReedGl.Light l = new ReedGl.Light();
        l.sunDir = new double[]{-0.55, 0.35, -0.76};
        l.sunColor = new double[]{2.4, 2.0, 1.5};
        l.skyColor = new double[]{0.35, 0.42, 0.55};
        l.fogColor = new double[]{0.55, 0.62, 0.70};
        l.fogDistance = 900;
        gl.draw(g, reed, v, l, t);
    }

    @Override public void reshape(GLAutoDrawable d, int x, int y, int w, int h) { }
    @Override public void dispose(GLAutoDrawable d) { gl.dispose(d.getGL().getGL4()); }

    static BufferedImage grab(GL4 g, int w, int h) {
        ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        g.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                int r = Math.min(255, (int) (255 * Math.pow((bb.get(i) & 255) / 255.0, 1 / 2.2)));
                int gg = Math.min(255, (int) (255 * Math.pow((bb.get(i + 1) & 255) / 255.0, 1 / 2.2)));
                int b = Math.min(255, (int) (255 * Math.pow((bb.get(i + 2) & 255) / 255.0, 1 / 2.2)));
                im.setRGB(x, y, (r << 16) | (gg << 8) | b);
            }
        return im;
    }

    // kleine Matrizen (Spaltenreihenfolge), damit die Demo ohne andere Packages auskommt
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
