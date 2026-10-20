package com.dan.spray.demo;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.spray.Body;
import com.dan.spray.Spray;
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
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): Gischt an einem Zylinder, einer Mole oder
 * einem Fels. Regler für Beaufort, Stärke, Sonnenhöhe und -richtung, Zeitlupe und Stufe; „Brecher“ wirft einen Schwall
 * von Hand. Ziehen dreht, das Rad zoomt. Unten die gemessene Zeit der Gischt auf der Grafikkarte.
 * Ohne Fenster: {@code SprayDemo --bild datei.png [breite]} rechnet die drei Körper bei Bft 5, 8 und 11 nebeneinander
 * und vergleicht dabei Grafikkarte und Prozessor.
 */
public final class SprayDemo {
    private SprayDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(SprayDemo::open);
    }

    static void bild(File out, int width) throws Exception {
        int[] bfts = {5, 8, 11};
        int tw = width / 3, th = tw * 3 / 4;
        BufferedImage img = new BufferedImage(tw * 3, th * 3, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        SprayScene s = new SprayScene();
        s.frozen = true;
        d.addGLEventListener(s);
        String check = "";
        for (int set = 0; set < 3; set++)
            for (int k = 0; k < 3; k++) {
                s.set = set;
                s.bft = bfts[k];
                s.setup();
                s.yaw = set == 1 ? 15 : 30;
                s.dist = set == 1 ? 85 : 70;
                s.pitch = 7;
                s.targetY = set == 0 ? 8 : 4;
                d.display();
                s.advance(30);
                s.advanceToSplash(bfts[k] == 5 ? 1.5 : bfts[k] == 8 ? 8 : 18, 60);
                d.display();
                if (set == 0 && k == 2) {
                    final String[] c = new String[1];
                    d.invoke(true, a -> { c[0] = s.check(a); return true; });
                    check = c[0];
                }
                final BufferedImage[] g = new BufferedImage[1];
                d.invoke(true, a -> { g[0] = grab(a, tw, th); return true; });
                Graphics2D gg = img.createGraphics();
                gg.drawImage(g[0], k * tw, set * th, null);
                label(gg, String.format(Locale.GERMAN, "%s · Bft %d · %d Schwälle in der Luft", SprayScene.SETS[set], bfts[k], s.spray.count(0)), k * tw, set * th);
                gg.dispose();
            }
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check);
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
        SprayScene scene = new SprayScene();
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
                scene.dist = Math.max(10, Math.min(400, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.spray");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), bftLbl = label("", ink), strLbl = label("", ink), altLbl = label("", ink), azLbl = label("", ink);
        FComboBox body = new FComboBox(SprayScene.SETS);
        FSlider bft = new FSlider(0, 12, 8), str = new FSlider(0, 200, 100), alt = new FSlider(-5, 70, 25), az = new FSlider(0, 359, 200);
        FComboBox lapse = new FComboBox(new Object[]{"Echtzeit", "Zeitlupe 1/4", "Zeitlupe 1/10", "Steht"});
        FComboBox quality = new FComboBox(new Object[]{"Niedrig", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        body.addActionListener(e -> scene.set = body.getSelectedIndex());
        bft.addChangeListener(e -> scene.bft = bft.getValue());
        str.addChangeListener(e -> scene.spray.strength = str.getValue() / 100.0);
        alt.addChangeListener(e -> scene.sunAlt = alt.getValue());
        az.addChangeListener(e -> scene.sunAz = az.getValue());
        lapse.addActionListener(e -> scene.lapse = switch (lapse.getSelectedIndex()) { case 1 -> 0.25; case 2 -> 0.1; case 3 -> 0; default -> 1; });
        quality.addActionListener(e -> scene.spray.quality = Spray.Quality.values()[quality.getSelectedIndex()]);
        FButton breaker = new FButton("Brecher");
        breaker.addActionListener(e -> {
            Body b = scene.spray.bodies().get(0);
            double from = Math.toRadians(scene.spray.windFromDeg);
            scene.spray.splash(b, from, scene.sea.level(), 6 + scene.spray.waveHeight * 1.5);
        });
        Component[] items = {title, state, label("Körper", ink), body, bftLbl, bft, strLbl, str, breaker, altLbl, alt, azLbl, az, label("Zeit", ink), lapse,
                label("Stufe", ink), quality, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.spray – Gischt und Brandung");
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
            bftLbl.setText(String.format(Locale.GERMAN, "Beaufort %d", bft.getValue()));
            strLbl.setText(String.format(Locale.GERMAN, "Stärke %d %%", str.getValue()));
            altLbl.setText(String.format(Locale.GERMAN, "Sonnenhöhe %d°", alt.getValue()));
            azLbl.setText(String.format(Locale.GERMAN, "Sonnenrichtung %d°", az.getValue()));
            state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>");
            if (scene.selfCheck.isEmpty() && scene.spray.parcels().size() > 10) canvas.invoke(false, a -> { scene.check(a); return true; });
            check.setText("<html><body style='width:280px'>" + scene.selfCheck + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
