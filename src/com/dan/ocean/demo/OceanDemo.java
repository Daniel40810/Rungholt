package com.dan.ocean.demo;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.ocean.Ocean;
import com.dan.ocean.SeaState;
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
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10). Im Fenster: eine See mit Reglern für
 * Beaufort, Windrichtung, Dünung, Sonne und Qualität, „Brecher“ setzt eine Ringwelle; ziehen dreht die Kamera, das Rad
 * zoomt. Ohne Fenster: {@code OceanDemo --bild datei.png [breite] [höhe]} rechnet Bft 2, 6 und 10 nebeneinander
 * (gleiche Kamera, gleiche Zeit) und prüft dabei Grafikkarte gegen Prozessor.
 */
public final class OceanDemo {
    private OceanDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            int w = args.length > 2 ? Integer.parseInt(args[2]) : 1800, h = args.length > 3 ? Integer.parseInt(args[3]) : 600;
            bild(new File(args[1]), w, h);
            return;
        }
        SwingUtilities.invokeLater(OceanDemo::open);
    }

    /** Drei Seen nebeneinander als PNG, mit Beschriftung. */
    static void bild(File out, int w, int h) throws Exception {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        Ocean[] o = {Ocean.beaufort(2), Ocean.beaufort(6), Ocean.beaufort(10)};
        for (Ocean x : o) x.setTime(120);
        DemoScene s = new DemoScene(o);
        s.frozen = true;
        s.dist = 70;
        s.targetY = 6;
        s.pitch = 5;
        d.addGLEventListener(s);
        d.display();
        final String[] check = new String[1];
        d.invoke(true, a -> { check[0] = s.check(a); return true; });
        final BufferedImage[] img = new BufferedImage[1];
        d.invoke(true, a -> { img[0] = grab(a, w, h); return true; });
        Graphics2D g = img[0].createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        for (int i = 0; i < o.length; i++) {
            Ocean.State st = o[i].state();
            String t = String.format(Locale.GERMAN, "Bft %d · Hs %.1f m · Tp %.1f s · Schaum %.0f %%", SeaState.beaufort(o[i].windSpeed), st.totalHs(),
                    st.peakPeriod, o[i].measuredFoamCoverage() * 100);
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(i * w / 3 + 10, 10, 360, 30);
            g.setColor(Color.WHITE);
            g.drawString(t, i * w / 3 + 18, 32);
        }
        g.dispose();
        javax.imageio.ImageIO.write(img[0], "png", out);
        System.out.println(check[0]);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static BufferedImage grab(com.jogamp.opengl.GLAutoDrawable a, int w, int h) {
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
        Ocean ocean = Ocean.beaufort(5);
        DemoScene scene = new DemoScene(ocean);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw -= (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-5, Math.min(80, scene.pitch + (e.getY() - ly) * 0.25));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(4, Math.min(2000, scene.dist * Math.pow(1.12, e.getPreciseWheelRotation())));
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.ocean");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        FSlider bft = new FSlider(0, 12, 5), dir = new FSlider(0, 359, 240), swell = new FSlider(0, 40, 9), sunEl = new FSlider(-10, 70, 22), sunAz = new FSlider(0, 359, 210);
        bft.addChangeListener(e -> ocean.setBeaufort(bft.getValue()));
        dir.addChangeListener(e -> ocean.windFromDeg = dir.getValue());
        swell.addChangeListener(e -> ocean.swellHeight = swell.getValue() / 10.0);
        sunEl.addChangeListener(e -> scene.sunEl = sunEl.getValue());
        sunAz.addChangeListener(e -> scene.sunAz = sunAz.getValue());
        FComboBox quality = new FComboBox(Ocean.Quality.values());
        quality.setSelectedItem(ocean.quality);
        quality.addActionListener(e -> ocean.quality = (Ocean.Quality) quality.getSelectedItem());
        FButton breaker = new FButton("Brecher (Ringwelle)");
        breaker.addActionListener(e -> {
            double[] eye = scene.eye();
            ocean.addImpulse(eye[0] * 0.6, eye[2] * 0.6, 1);
        });
        side.add(title);
        for (Object[] row : new Object[][]{{"Beaufort", bft}, {"Wind aus (Grad)", dir}, {"Dünung (0,1 m)", swell}, {"Sonnenhöhe", sunEl}, {"Sonne Azimut", sunAz}, {"Qualität", quality}}) {
            JLabel l = new JLabel((String) row[0]);
            l.setForeground(new Color(147, 161, 175));
            l.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
            side.add(l);
            side.add((Component) row[1]);
        }
        side.add(javax.swing.Box.createVerticalStrut(12));
        side.add(breaker);
        side.add(javax.swing.Box.createVerticalStrut(12));
        side.add(state);
        for (Component c : side.getComponents()) ((javax.swing.JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        side.setPreferredSize(new Dimension(300, 600));
        new javax.swing.Timer(500, e -> state.setText("<html><body style='width:240px'>" + ocean.describe() + "</body></html>")).start();

        FFrame f = new FFrame("Ozean-Demo · com.dan.ocean");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1400, 820));
        f.setSize(1400, 820);
        // deckend: ein GLCanvas in einem durchsichtigen Fenster bleibt unter Windows leer (wie in der App und den Lighthouse-Demos)
        f.setBackground(new Color(15, 19, 17));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }
}
