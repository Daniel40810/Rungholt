package com.dan.cloud.demo;

import com.dan.cloud.CloudType;
import com.dan.cloud.Clouds;
import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
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
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10). Im Fenster: Wolken über einer glatten See
 * mit Reglern für Bedeckung (Achtel), Art, hohe Schicht, Wind, Sonnenhöhe und -richtung, Sicht und Stufe, dazu
 * Zeitraffer und „0 → 8/8“ (die Bedeckung läuft langsam hin und her). Ziehen dreht den Blick, das Rad ändert die
 * Öffnung. Die Zeit der Wolken auf der Grafikkarte steht unten.
 * Ohne Fenster: {@code CloudDemo --bild datei.png [breite]} rechnet zwei Reihen (Cumulus 0/8 bis 8/8; die fünf Arten
 * und ein Abend) und prüft dabei Grafikkarte gegen Prozessor.
 */
public final class CloudDemo {
    private CloudDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 2160);
            return;
        }
        SwingUtilities.invokeLater(CloudDemo::open);
    }

    /** Eine Kachel des Standbilds. */
    record Tile(String label, CloudType type, double oktas, double high, double sunAlt, double sunAz, double yaw, double pitch) { }

    static void bild(File out, int width) throws Exception {
        Tile[] row1 = new Tile[9];
        for (int i = 0; i <= 8; i++) row1[i] = new Tile(i + "/8", CloudType.CUMULUS, i, 0, 35, 190, 20, 14);
        Tile[] row2 = {
                new Tile("Cumulus 3/8", CloudType.CUMULUS, 3, 0, 35, 190, 20, 8),
                new Tile("Stratocumulus 6/8", CloudType.STRATOCUMULUS, 6, 0, 30, 190, 20, 8),
                new Tile("Stratus 8/8", CloudType.STRATUS, 8, 0, 30, 190, 20, 8),
                new Tile("Nimbostratus 8/8", CloudType.NIMBOSTRATUS, 8, 0, 30, 190, 20, 8),
                new Tile("Cumulonimbus 2/8 mit Amboss", CloudType.CUMULONIMBUS, 2, 0, 30, 230, 100, 6),
                new Tile("Abend · Cumulus 4/8 + Schleier", CloudType.CUMULUS, 4, 0.4, 2, 250, 250, 6)};
        int w1 = width / 9, h1 = w1 * 2 / 3, w2 = width / 6, h2 = w2 * 9 / 16;
        BufferedImage img = new BufferedImage(w1 * 9, h1 + h2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w1, h1);
        Clouds clouds = new Clouds();
        clouds.windSpeed = 8;
        clouds.setTime(600);
        CloudScene s = new CloudScene(clouds);
        s.frozen = true;
        d.addGLEventListener(s);
        String check = "";
        for (int r = 0; r < 2; r++) {
            Tile[] row = r == 0 ? row1 : row2;
            int tw = r == 0 ? w1 : w2, th = r == 0 ? h1 : h2;
            d.setSurfaceSize(tw, th);
            for (int i = 0; i < row.length; i++) {
                Tile t = row[i];
                clouds.type = t.type();
                clouds.oktas = t.oktas();
                clouds.highCover = t.high();
                s.sunAlt = t.sunAlt();
                s.sunAz = t.sunAz();
                s.yaw = t.yaw();
                s.pitch = t.pitch();
                s.fovy = r == 0 ? 75 : 62;
                for (int k = 0; k < 16; k++) d.display();
                if (r == 1 && i == 0) {
                    final String[] c = new String[1];
                    d.invoke(true, a -> { c[0] = s.check(a); return true; });
                    check = c[0];
                }
                final BufferedImage[] g = new BufferedImage[1];
                d.invoke(true, a -> { g[0] = grab(a, tw, th); return true; });
                Graphics2D gg = img.createGraphics();
                gg.drawImage(g[0], i * tw, r == 0 ? 0 : h1, null);
                label(gg, t.label(), i * tw, r == 0 ? 0 : h1);
                gg.dispose();
            }
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
        Clouds clouds = Clouds.fair();
        clouds.quality = Clouds.Quality.MEDIUM;
        CloudScene scene = new CloudScene(clouds);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw -= (e.getX() - lx) * 0.2;
                scene.pitch = Math.max(-20, Math.min(89, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.fovy = Math.max(10, Math.min(110, scene.fovy * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.cloud");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = new JLabel(" "), check = new JLabel(" ");
        state.setForeground(ink);
        check.setForeground(muted);
        JLabel oktasLbl = label("", ink), highLbl = label("", ink), windLbl = label("", ink), altLbl = label("", ink), azLbl = label("", ink),
                visLbl = label("", ink);
        FSlider oktas = new FSlider(0, 80, 30), high = new FSlider(0, 100, 0), wind = new FSlider(0, 30, 10), alt = new FSlider(-10, 70, 30),
                az = new FSlider(0, 359, 200), vis = new FSlider(5, 100, 40);
        FComboBox type = new FComboBox(CloudType.values());
        FComboBox quality = new FComboBox(new Object[]{"Niedrig (2.5D)", "Mittel", "Hoch", "Ultra"});
        quality.setSelectedIndex(1);
        FComboBox lapse = new FComboBox(new Object[]{"Wolken stehen", "Echtzeit", "10-fach", "60-fach"});
        lapse.setSelectedIndex(1);
        oktas.addChangeListener(e -> { if (scene.oktasLapse == 0) clouds.oktas = oktas.getValue() / 10.0; });
        type.addActionListener(e -> clouds.type = (CloudType) type.getSelectedItem());
        high.addChangeListener(e -> clouds.highCover = high.getValue() / 100.0);
        wind.addChangeListener(e -> clouds.windSpeed = wind.getValue());
        alt.addChangeListener(e -> scene.sunAlt = alt.getValue());
        az.addChangeListener(e -> scene.sunAz = az.getValue());
        vis.addChangeListener(e -> scene.visibilityKm = vis.getValue());
        quality.addActionListener(e -> clouds.quality = Clouds.Quality.values()[quality.getSelectedIndex()]);
        lapse.addActionListener(e -> scene.lapse = switch (lapse.getSelectedIndex()) { case 0 -> 0; case 2 -> 10; case 3 -> 60; default -> 1; });
        FButton sweep = new FButton("0 → 8/8");
        sweep.addActionListener(e -> {
            if (scene.oktasLapse == 0) {
                clouds.oktas = 0;
                scene.oktasLapse = 0.2;
            } else {
                scene.oktasLapse = 0;
            }
        });
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.setOpaque(false);
        String[] pn = {"Schön", "Grau", "Gewitter", "Abend"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            FButton b = new FButton(pn[i]);
            b.addActionListener(e -> {
                scene.oktasLapse = 0;
                Clouds p = switch (k) { case 1 -> Clouds.overcast(); case 2 -> Clouds.storm(); default -> Clouds.fair(); };
                if (k == 3) { p.oktas = 4; p.highCover = 0.4; }
                type.setSelectedItem(p.type);
                oktas.setValue((int) Math.round(p.oktas * 10));
                high.setValue((int) Math.round(p.highCover * 100));
                wind.setValue((int) Math.round(p.windSpeed));
                clouds.precipitation = p.precipitation;
                alt.setValue(k == 3 ? 2 : k == 2 ? 25 : 35);
                az.setValue(k == 3 ? 250 : 200);
                if (k == 3) scene.yaw = 250;
            });
            presets.add(b);
        }
        Component[] items = {title, state, presets, oktasLbl, oktas, sweep, label("Art", ink), type, highLbl, high, windLbl, wind, altLbl, alt,
                azLbl, az, visLbl, vis, label("Stufe", ink), quality, lapse, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        // Inhalt in die Fläche des FFrame (nicht ins ContentPane, dort liegen Titelleiste und Rahmen); das Fenster
        // deckend machen, denn in einem durchscheinenden Fenster zeigt Windows den schweren GL-Canvas nicht
        FFrame f = new FFrame("com.dan.cloud – Wolken");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 860));
        f.setSize(1500, 860);
        f.setMinimumSize(new Dimension(900, 560));
        f.setBackground(bg);
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { roundCorners(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { roundCorners(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> roundCorners(f));
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator anim = new Animator(canvas);
        anim.start();
        new Timer(250, e -> {
            if (scene.oktasLapse != 0) oktas.setValue((int) Math.round(clouds.oktas * 10));
            oktasLbl.setText(String.format(Locale.GERMAN, "Bedeckung: %.1f/8", clouds.oktas));
            highLbl.setText(String.format(Locale.GERMAN, "Hohe Schicht: %d %%", high.getValue()));
            windLbl.setText(String.format(Locale.GERMAN, "Wind in Wolkenhöhe: %d m/s", wind.getValue()));
            altLbl.setText(String.format(Locale.GERMAN, "Sonnenhöhe: %d°", alt.getValue()));
            azLbl.setText(String.format(Locale.GERMAN, "Sonnenrichtung: %d°", az.getValue()));
            visLbl.setText(String.format(Locale.GERMAN, "Sicht: %d km", vis.getValue()));
            state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>");
            if (scene.selfCheck.isEmpty() && scene.cloudMs > 0) canvas.invoke(false, a -> { scene.check(a); return true; });
            check.setText("<html><body style='width:280px'>" + scene.selfCheck + "</body></html>");
        }).start();
    }

    /** Runde Ecken als Fensterform (das Fenster selbst ist deckend); maximiert eckig. */
    private static void roundCorners(FFrame f) {
        try {
            boolean max = (f.getExtendedState() & java.awt.Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | java.awt.IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
