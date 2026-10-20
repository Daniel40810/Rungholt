package com.dan.fog.demo;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fog.Fog;
import com.dan.fog.Signal;
import com.dan.fslider.FSlider;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): See, Turm und Pfähle im Seenebel. Regler für
 * Sicht, Bedeckung, Obergrenze, Wind und Sonnenhöhe, Voreinstellungen Dunst, Bänke, dicht, flach. Ziehen dreht, das
 * Rad hebt und senkt das Auge. Am Turm tönt ein Nebelhorn.
 * Ohne Fenster: {@code FogDemo --bild datei.png [breite]} rechnet sechs Lagen und darunter den Takt dreier Signale.
 */
public final class FogDemo {
    private FogDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(FogDemo::open);
    }

    static final String[] PRESETS = {"klar", "Dunst", "Nebelbänke", "dichter Nebel", "flacher Seenebel"};

    static void preset(Fog f, int i) {
        Fog p = switch (i) { case 1 -> Fog.haze(); case 2 -> Fog.banks(); case 3 -> Fog.dense(); case 4 -> Fog.shallow(); default -> Fog.banks(); };
        f.visibilityM = p.visibilityM;
        f.coverage = p.coverage;
        f.top = p.top;
        f.softness = p.softness;
        f.wisps = p.wisps;
        f.amount = i == 0 ? 0 : 1;
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 9 / 16, sh = width / 7;
        BufferedImage img = new BufferedImage(tw * 3, th * 2 + sh, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        FogScene s = new FogScene();
        s.frozen = true;
        d.addGLEventListener(s);
        String[] title = {"klar", "Dunst, Sicht 3 km", "Nebelbänke von oben", "dichter Nebel, 70 m", "flacher Seenebel, der Turm ragt heraus", "Abendsonne im Nebel"};
        int[] pre = {0, 1, 2, 3, 4, 2};
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(20, 26, 32));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int k = 0; k < 6; k++) {
            preset(s.fog, pre[k]);
            s.fog.seed = 7;
            s.fog.windSpeed = 3;
            s.fog.windFromDeg = 240;
            s.sunAlt = k == 5 ? 4 : 24;
            s.sunAz = k == 5 ? 175 : 140;
            s.exposure = k == 5 ? 1.6 : 1;
            s.eyeX = 0; s.eyeY = 6; s.eyeZ = 0; s.yaw = 0; s.pitch = 1.5; s.fovy = 60;
            if (k == 5) { s.yaw = 175; s.eyeZ = -900; }
            if (k == 2) { s.eyeY = 420; s.eyeZ = 900; s.pitch = 24; s.fovy = 62; }
            if (k == 4) { s.eyeY = 48; s.eyeZ = -60; s.pitch = 3.5; s.fovy = 50; }
            s.advance(30);
            s.check = "";
            for (int i = 0; i < 3; i++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (k % 3) * tw, y = (k / 3) * th;
            g.drawImage(shot[0], x, y, null);
            double vis = s.fog.visibilityAt(s.eyeX, s.eyeY, s.eyeZ);
            label(g, String.format(Locale.GERMAN, "%s · Sicht am Auge %s", title[k], vis >= 1e5 ? "frei" : String.format(Locale.GERMAN, "%.0f m", vis)), x, y);
            if (k == 3) System.out.println(s.check);
        }
        // Takt dreier Signale über 60 s
        Signal[] sig = {new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 0, 0), new Signal("Feuerschiff", "Horn(2) 20s", 0, 0, 0), new Signal("Schiff in Fahrt", "Tyfon 120s", 0, 0, 0)};
        int y0 = th * 2 + 14, lh = (sh - 40) / 3, x0 = 220, x1 = tw * 3 - 30;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        for (int i = 0; i < sig.length; i++) {
            int yy = y0 + i * lh;
            g.setColor(new Color(200, 210, 220));
            g.drawString(sig[i].text, 16, yy + lh / 2 + 4);
            g.setColor(new Color(60, 70, 80));
            g.fillRect(x0, yy + lh / 2 - 1, x1 - x0, 2);
            g.setColor(new Color(226, 160, 63));
            for (double t = 0; t < 60; t += 0.05) if (sig[i].soundingAt(t)) g.fillRect(x0 + (int) (t / 60 * (x1 - x0)), yy + 6, Math.max(1, (int) (0.05 / 60 * (x1 - x0)) + 1), lh - 12);
        }
        g.setColor(new Color(150, 160, 170));
        for (int t = 0; t <= 60; t += 10) g.drawString(t + " s", x0 + (int) (t / 60.0 * (x1 - x0)) - 8, th * 2 + sh - 6);
        g.setStroke(new BasicStroke(1));
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    static BufferedImage grab(GLAutoDrawable a, int w, int h) {
        com.jogamp.opengl.GL4 gl = a.getGL().getGL4();
        java.nio.ByteBuffer bb = com.jogamp.common.nio.Buffers.newDirectByteBuffer(w * h * 4);
        int fb = a.getContext().getDefaultDrawFramebuffer();
        gl.glBindFramebuffer(com.jogamp.opengl.GL.GL_READ_FRAMEBUFFER, fb);
        gl.glReadBuffer(fb == 0 ? com.jogamp.opengl.GL.GL_BACK : com.jogamp.opengl.GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(com.jogamp.opengl.GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, com.jogamp.opengl.GL.GL_RGBA, com.jogamp.opengl.GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
            }
        return img;
    }

    private static void open() {
        FogScene scene = new FogScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-40, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.eyeY = Math.max(2, Math.min(800, scene.eyeY * Math.pow(1.15, -e.getPreciseWheelRotation())));
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);
        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.fog");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), visL = label("", ink), covL = label("", ink), topL = label("", ink), windL = label("", ink), sunL = label("", ink);
        FComboBox kind = new FComboBox(PRESETS);
        kind.setSelectedIndex(2);
        FSlider vis = new FSlider(30, 5000, 250), cov = new FSlider(0, 100, 45), top = new FSlider(5, 200, 55), wind = new FSlider(0, 12, 3), sun = new FSlider(-6, 60, 22);
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(2);
        kind.addActionListener(e -> {
            preset(scene.fog, kind.getSelectedIndex());
            vis.setValue((int) scene.fog.visibilityM);
            cov.setValue((int) Math.round(scene.fog.coverage * 100));
            top.setValue((int) scene.fog.top);
        });
        vis.addChangeListener(e -> scene.fog.visibilityM = vis.getValue());
        cov.addChangeListener(e -> scene.fog.coverage = cov.getValue() / 100.0);
        top.addChangeListener(e -> scene.fog.top = top.getValue());
        wind.addChangeListener(e -> scene.fog.windSpeed = wind.getValue());
        sun.addChangeListener(e -> scene.sunAlt = sun.getValue());
        quality.addActionListener(e -> scene.fog.quality = Fog.Quality.values()[quality.getSelectedIndex()]);
        FButton up = new FButton("Auf 400 m"), down = new FButton("Auf 6 m");
        up.addActionListener(e -> { scene.eyeY = 400; scene.pitch = 22; });
        down.addActionListener(e -> { scene.eyeY = 6; scene.pitch = 2; });
        Component[] items = {title, state, label("Lage", ink), kind, visL, vis, covL, cov, topL, top, windL, wind, sunL, sun, up, down, label("Stufe", ink), quality, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.fog – Seenebel");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 860));
        f.setSize(1500, 860);
        f.setMinimumSize(new Dimension(900, 560));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator anim = new Animator(canvas);
        anim.start();
        new Timer(250, e -> {
            visL.setText(String.format(Locale.GERMAN, "Sicht in der Bank %d m", vis.getValue()));
            covL.setText(String.format(Locale.GERMAN, "Bedeckung %d %%", cov.getValue()));
            topL.setText(String.format(Locale.GERMAN, "Obergrenze %d m", top.getValue()));
            windL.setText(String.format(Locale.GERMAN, "Wind %d m/s", wind.getValue()));
            sunL.setText(String.format(Locale.GERMAN, "Sonnenhöhe %d°", sun.getValue()));
            String horn = scene.horn.active() ? (scene.horn.soundingAt(scene.fog.time()) ? "Nebelhorn tönt" : "Nebelhorn an, " + scene.horn.count() + " Töne") : "Nebelhorn aus (Sicht über 2 sm)";
            state.setText("<html><body style='width:280px'>" + scene.fog.describe() + "<br>Sicht am Auge " + fmtVis(scene.fog.visibilityAt(scene.eyeX, scene.eyeY, scene.eyeZ))
                    + "<br>" + scene.horn.describe() + "<br>" + horn + "</body></html>");
            check.setText("<html><body style='width:280px'>" + scene.check + "</body></html>");
        }).start();
    }

    static String fmtVis(double v) { return v >= 1e5 ? "frei" : String.format(Locale.GERMAN, "%.0f m", v); }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
