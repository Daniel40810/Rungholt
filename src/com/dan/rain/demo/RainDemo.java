package com.dan.rain.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.rain.Rain;
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
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): Regen über Wasser mit Pfählen und einem Steg.
 * Regler für die Regenmenge (Niesel bis Wolkenbruch), Wind, Landregen oder Schauer, Sonne, Zeitlupe und Stufe. Ziehen
 * dreht, das Rad zoomt. Unten die gemessene Zeit des Regens auf der Grafikkarte.
 * Ohne Fenster: {@code RainDemo --bild datei.png [breite]} rechnet oben Niesel bis Wolkenbruch nebeneinander und darunter
 * einen Schauer, der über die See zieht, und vergleicht dabei Grafikkarte und Prozessor.
 */
public final class RainDemo {
    private RainDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(RainDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        double[] rates = {0.3, 1.5, 5, 15, 60};
        String[] names = {"Niesel", "Leichter Regen", "Mäßiger Regen", "Starker Regen", "Wolkenbruch"};
        int tw = width / 5, th = tw * 4 / 5, sw = width / 4, sh = sw * 9 / 16;
        BufferedImage img = new BufferedImage(width, th + sh, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        RainScene s = new RainScene();
        s.frozen = true;
        d.addGLEventListener(s);
        String check = "";
        Graphics2D gg = img.createGraphics();
        for (int k = 0; k < rates.length; k++) {
            s.mode = 0;
            s.bft = k < 3 ? 4 : 6;
            s.setup();
            s.rain.rate = rates[k];
            s.rain.quality = Rain.Quality.HIGH;
            s.yaw = 205;
            s.pitch = 3;
            s.dist = 16;
            s.targetY = 1.2;
            s.sunAlt = 18;
            d.display();
            s.advance(2);
            d.display();
            if (k == 3) {
                final String[] c = new String[1];
                d.invoke(true, a -> { c[0] = s.check(a); return true; });
                check = c[0];
            }
            gg.drawImage(grab(d, tw, th), k * tw, 0, null);
            label(gg, String.format(Locale.GERMAN, "%s · %s mm/h · Sicht %.1f km", names[k], fmt(rates[k]), Rain.visibilityKm(rates[k])), k * tw, 0);
        }
        // Ein Schauer zieht heran, über die See und weiter (Blick gegen den Wind)
        d.setSurfaceSize(sw, sh);
        double[] centerKm = {5, 2.2, 0, -2.6};
        String[] what = {"Schauer in 5 km", "in 2 km", "über uns", "zieht ab (Blick mit dem Wind)"};
        s.mode = 1;
        s.bft = 6;
        s.setup();
        s.rain.rate = 20;
        double[] wt = s.rain.windTo();
        double wl = Math.hypot(wt[0], wt[1]);
        for (int k = 0; k < 4; k++) {
            // Mitte des Schauers auf der Linie gegen den Wind, centerKm stromauf
            double cx = -wt[0] / wl * centerKm[k] * 1000, cz = -wt[1] / wl * centerKm[k] * 1000;
            ShowerField f = ShowerField.singleAt(cx, cz, 1000, s.showers.windX, s.showers.windZ, s.t);
            s.showers = f;
            s.setup();
            s.yaw = Math.toDegrees(Math.atan2(-wt[0], wt[1])) + (k == 3 ? 180 : 0);
            s.pitch = 5;
            s.dist = 24;
            s.targetY = 6;
            s.sunAlt = 25;
            s.sunAz = 180;
            s.advance(1.2);
            d.display();
            gg.drawImage(grab(d, sw, sh), k * sw, th, null);
            label(gg, String.format(Locale.GERMAN, "Schauer 20 mm/h · %s · hier %.1f mm/h", what[k], s.rain.rateAt(s.eye()[0], s.eye()[2])), k * sw, th);
        }
        gg.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static String fmt(double v) { return v < 1 ? String.format(Locale.GERMAN, "%.1f", v) : String.format(Locale.GERMAN, "%.0f", v); }

    static BufferedImage grab(GLOffscreenAutoDrawable d, int w, int h) {
        final BufferedImage[] g = new BufferedImage[1];
        d.invoke(true, a -> { g[0] = read(a, w, h); return true; });
        return g[0];
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 120));
        g.fillRect(x + 6, y + 6, g.getFontMetrics().stringWidth(t) + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(t, x + 12, y + 22);
    }

    static BufferedImage read(GLAutoDrawable a, int w, int h) {
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
        RainScene scene = new RainScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-5, Math.min(85, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(3, Math.min(400, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.rain");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), rateLbl = label("", ink), bftLbl = label("", ink), strLbl = label("", ink),
                altLbl = label("", ink), azLbl = label("", ink);
        // Regenmenge logarithmisch: 0 → 0,1 mm/h, 100 → 100 mm/h
        FSlider rate = new FSlider(0, 100, 55), bft = new FSlider(0, 12, 5), str = new FSlider(0, 200, 100), alt = new FSlider(-5, 70, 20), az = new FSlider(0, 359, 220);
        FComboBox mode = new FComboBox(RainScene.MODES);
        mode.setSelectedIndex(1);
        FComboBox lapse = new FComboBox(new Object[]{"Echtzeit", "Zeitlupe 1/4", "Zeitlupe 1/10", "Steht", "Zeitraffer 20×"});
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        rate.addChangeListener(e -> scene.rain.rate = rateOf(rate.getValue()));
        scene.rain.rate = rateOf(rate.getValue());
        bft.addChangeListener(e -> { scene.bft = bft.getValue(); scene.setup(); });
        mode.addActionListener(e -> { scene.mode = mode.getSelectedIndex(); scene.setup(); });
        str.addChangeListener(e -> scene.strength = str.getValue() / 100.0);
        alt.addChangeListener(e -> scene.sunAlt = alt.getValue());
        az.addChangeListener(e -> scene.sunAz = az.getValue());
        lapse.addActionListener(e -> scene.lapse = switch (lapse.getSelectedIndex()) { case 1 -> 0.25; case 2 -> 0.1; case 3 -> 0; case 4 -> 20; default -> 1; });
        quality.addActionListener(e -> scene.rain.quality = Rain.Quality.values()[quality.getSelectedIndex()]);
        Component[] items = {title, state, rateLbl, rate, label("Vorhang", ink), mode, bftLbl, bft, strLbl, str, altLbl, alt, azLbl, az,
                label("Zeit", ink), lapse, label("Stufe", ink), quality, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.rain – Regen");
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
            rateLbl.setText(String.format(Locale.GERMAN, "Regen %s mm/h", fmt(scene.rain.rate)));
            bftLbl.setText(String.format(Locale.GERMAN, "Wind Beaufort %d", bft.getValue()));
            strLbl.setText(String.format(Locale.GERMAN, "Stärke %d %%", str.getValue()));
            altLbl.setText(String.format(Locale.GERMAN, "Sonnenhöhe %d°", alt.getValue()));
            azLbl.setText(String.format(Locale.GERMAN, "Sonnenrichtung %d°", az.getValue()));
            state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>");
            if (scene.selfCheck.isEmpty() && scene.rain.rate > 0) canvas.invoke(false, a -> { scene.check(a); return true; });
            check.setText("<html><body style='width:280px'>" + scene.selfCheck + "</body></html>");
        }).start();
    }

    static double rateOf(int v) { return 0.1 * Math.pow(1000, v / 100.0); }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
