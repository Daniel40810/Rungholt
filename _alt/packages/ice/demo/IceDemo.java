package com.dan.ice.demo;

import com.dan.fbutton.FButton;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.ice.Ice;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): ein Eisblock von 10 m auf einem Sockel aus
 * Schutt, daneben ein Eisberg mit neun Zehnteln unter Wasser, Sonne und Himmel. Ziehen dreht den Blick um die Szene,
 * mit gedrückter Umschalttaste den Block; das Rad ändert den Abstand. Regler für Blasen, Risse, Dicke, Kruste, Moräne,
 * Film, Schnee, Glitzer, Sonnenhöhe und -richtung; Presets Firn, Gletscher, Blaueis, Eisberg.
 * Ohne Fenster: {@code IceDemo --bild datei.png [breite] [vorlagen-ordner]} rechnet eine Reihe (die vier Presets, Gegenlicht,
 * tiefe Sonne) und stellt, wenn ein Ordner mit Fotos angegeben ist, die Fotos darunter.
 */
public final class IceDemo {
    private IceDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1920, args.length > 3 ? new File(args[3]) : null);
            return;
        }
        SwingUtilities.invokeLater(IceDemo::open);
    }

    record Tile(String label, Ice block, double sunAlt, double sunAz, double yaw, double blockYaw) { }

    static void bild(File out, int width, File photos) throws Exception {
        Ice bl = Ice.blue();
        Tile[] row = {
                new Tile("Gletschereis", Ice.glacier(), 35, 300, 0, 28),
                new Tile("Firn", Ice.firn(), 35, 300, 0, 28),
                new Tile("Blaueis", bl, 35, 300, 0, 28),
                new Tile("Eisberg-Eis", Ice.iceberg(), 35, 300, 0, 28),
                new Tile("Gegenlicht", Ice.glacier(), 18, 15, 0, 28),
                new Tile("Tiefe Sonne", Ice.glacier(), 4, 250, 30, 28)};
        int cols = 3, tw = width / cols, th = tw * 9 / 16;
        List<BufferedImage> ph = new ArrayList<>();
        if (photos != null && photos.isDirectory()) {
            File[] fs = photos.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).matches(".*\\.(jpe?g|png)"));
            if (fs != null) {
                java.util.Arrays.sort(fs);
                for (File f : fs) if (ph.size() < cols) ph.add(ImageIO.read(f));
            }
        }
        int rows = (row.length + cols - 1) / cols;
        BufferedImage img = new BufferedImage(tw * cols, th * rows + (ph.isEmpty() ? 0 : th), BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        IceScene s = new IceScene();
        s.frozen = true;
        d.addGLEventListener(s);
        String status = "";
        for (int i = 0; i < row.length; i++) {
            Tile t = row[i];
            s.blockIce = t.block();
            s.sunAlt = t.sunAlt();
            s.sunAz = t.sunAz();
            s.yaw = t.yaw();
            s.blockYaw = t.blockYaw();
            for (int k = 0; k < 6; k++) d.display();
            if (i == 0) status = s.status;
            final BufferedImage[] g = new BufferedImage[1];
            d.invoke(true, a -> { g[0] = grab(a, tw, th); return true; });
            Graphics2D gg = img.createGraphics();
            gg.drawImage(g[0], (i % cols) * tw, (i / cols) * th, null);
            label(gg, t.label(), (i % cols) * tw, (i / cols) * th);
            gg.dispose();
        }
        Graphics2D gg = img.createGraphics();
        gg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int i = 0; i < ph.size(); i++) {
            BufferedImage p = ph.get(i);
            double sc = Math.max(tw / (double) p.getWidth(), th / (double) p.getHeight());
            int w = (int) (p.getWidth() * sc), h = (int) (p.getHeight() * sc);
            gg.setClip(i * tw, rows * th, tw, th);
            gg.drawImage(p, i * tw + (tw - w) / 2, rows * th + (th - h) / 2, w, h, null);
            gg.setClip(null);
            label(gg, "Foto " + (i + 1), i * tw, rows * th);
        }
        gg.dispose();
        ImageIO.write(img, "png", out);
        System.out.println(status);
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
        IceScene scene = new IceScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (e.isShiftDown()) scene.blockYaw += (e.getX() - lx) * 0.4;
                else {
                    scene.yaw -= (e.getX() - lx) * 0.3;
                    scene.pitch = Math.max(-5, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
                }
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(10, Math.min(200, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.ice");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = new JLabel(" ");
        state.setForeground(muted);
        List<Component> items = new ArrayList<>(List.of(title, state));
        List<Runnable> sync = new ArrayList<>();
        // Regler fürs Material des Blocks (der Eisberg folgt mit Blasen und Rissen)
        Object[][] mats = {
                {"Blasen", (java.util.function.ToDoubleFunction<Ice>) i -> i.bubbles, (Setter) (i, v) -> i.bubbles = v},
                {"Risse", (java.util.function.ToDoubleFunction<Ice>) i -> i.cracks, (Setter) (i, v) -> i.cracks = v},
                {"Kruste (Verwitterung)", (java.util.function.ToDoubleFunction<Ice>) i -> i.weathering, (Setter) (i, v) -> i.weathering = v},
                {"Moräne", (java.util.function.ToDoubleFunction<Ice>) i -> i.moraine, (Setter) (i, v) -> i.moraine = v},
                {"Schmelzfilm", (java.util.function.ToDoubleFunction<Ice>) i -> i.melt, (Setter) (i, v) -> i.melt = v},
                {"Schnee", (java.util.function.ToDoubleFunction<Ice>) i -> i.snow, (Setter) (i, v) -> i.snow = v},
                {"Glitzer", (java.util.function.ToDoubleFunction<Ice>) i -> i.glitter, (Setter) (i, v) -> i.glitter = v}};
        for (Object[] r : mats) {
            String name = (String) r[0];
            @SuppressWarnings("unchecked") java.util.function.ToDoubleFunction<Ice> get = (java.util.function.ToDoubleFunction<Ice>) r[1];
            Setter set = (Setter) r[2];
            FSlider s = new FSlider(0, 100, (int) Math.round(get.applyAsDouble(scene.blockIce) * 100));
            JLabel l = label("", ink);
            Runnable txt = () -> l.setText(String.format(Locale.GERMAN, "%s: %d %%", name, s.getValue()));
            s.addChangeListener(e -> {
                Ice i = scene.blockIce.copy();
                set.set(i, s.getValue() / 100.0);
                scene.blockIce = i;
                if (name.equals("Blasen") || name.equals("Risse")) {
                    Ice b = scene.bergIce.copy();
                    set.set(b, s.getValue() / 100.0 * 0.7);
                    scene.bergIce = b;
                }
                txt.run();
            });
            sync.add(() -> { s.setValue((int) Math.round(get.applyAsDouble(scene.blockIce) * 100)); txt.run(); });
            txt.run();
            items.add(l);
            items.add(s);
        }
        JLabel thickL = label("", ink), altL = label("", ink), azL = label("", ink);
        FSlider thick = new FSlider(10, 300, 100), alt = new FSlider(-5, 75, 35), az = new FSlider(0, 359, 300);
        Consumer<Void> lbls = x -> {
            thickL.setText(String.format(Locale.GERMAN, "Dicke: %.1f-fach", thick.getValue() / 100.0));
            altL.setText(String.format(Locale.GERMAN, "Sonnenhöhe: %d°", alt.getValue()));
            azL.setText(String.format(Locale.GERMAN, "Sonnenrichtung: %d°", az.getValue()));
        };
        thick.addChangeListener(e -> { scene.thickness = thick.getValue() / 100.0; lbls.accept(null); });
        alt.addChangeListener(e -> { scene.sunAlt = alt.getValue(); lbls.accept(null); });
        az.addChangeListener(e -> { scene.sunAz = az.getValue(); lbls.accept(null); });
        lbls.accept(null);
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.setOpaque(false);
        String[] pn = {"Firn", "Gletscher", "Blaueis", "Eisberg"};
        for (int k = 0; k < pn.length; k++) {
            final int q = k;
            FButton b = new FButton(pn[k]);
            b.addActionListener(e -> {
                scene.blockIce = switch (q) { case 0 -> Ice.firn(); case 2 -> Ice.blue(); case 3 -> Ice.iceberg(); default -> Ice.glacier(); };
                sync.forEach(Runnable::run);
            });
            presets.add(b);
        }
        items.add(2, presets);
        items.addAll(List.of(thickL, thick, altL, alt, azL, az,
                label("<html><body style='width:270px'>Ziehen: Blick · Umschalt + Ziehen: Block drehen · Rad: Abstand</body></html>", muted)));
        for (Component c : items) {
            if (c instanceof JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(Box.createVerticalStrut(5));
        }
        side.setPreferredSize(new Dimension(330, 10));
        FFrame f = new FFrame("com.dan.ice – Eis");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 880));
        f.setSize(1500, 880);
        f.setMinimumSize(new Dimension(900, 600));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
        new Timer(250, e -> state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>")).start();
    }

    @FunctionalInterface private interface Setter { void set(Ice i, double v); }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
