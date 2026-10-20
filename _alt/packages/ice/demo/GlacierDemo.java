package com.dan.ice.demo;

import com.dan.fbutton.FButton;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung des Gletschers aus {@code com.dan.ice} ohne App (Paketvertrag Punkt 3 und 10): eine Zunge in einem
 * ausgedachten Tal mit Eisfall und Front über einem See. Ziehen kreist um den Blickpunkt, das Rad ändert den Abstand,
 * Knöpfe springen zu Front, Eisfall, Zunge und Übersicht. Regler für Spalten, Vorstoß und Sonne.
 * Ohne Fenster: {@code GlacierDemo --bild datei.png [breite]} rechnet die vier Blicke.
 */
public final class GlacierDemo {
    private GlacierDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1920);
            return;
        }
        SwingUtilities.invokeLater(GlacierDemo::open);
    }

    /** Blicke: Name, Blickpunkt x, y, z, Azimut, Höhe, Abstand. */
    static final Object[][] VIEWS = {
            {"Front vom See", -60.0, 40.0, 0.0, 80.0, 4.0, 900.0},
            {"Eisfall", -3000.0, 120.0, 0.0, 120.0, 18.0, 1100.0},
            {"Auf der Zunge", -1200.0, 80.0, 0.0, 95.0, 6.0, 260.0},
            {"Übersicht", -2400.0, 80.0, 0.0, 160.0, 38.0, 4200.0}};

    static void apply(GlacierScene s, int k) {
        Object[] v = VIEWS[k];
        s.target = new double[]{(Double) v[1], (Double) v[2], (Double) v[3] + GlacierValley.center((Double) v[1])};
        s.yaw = (Double) v[4];
        s.pitch = (Double) v[5];
        s.dist = (Double) v[6];
    }

    static void bild(File out, int width) throws Exception {
        int tw = width / 2, th = tw * 9 / 16;
        BufferedImage img = new BufferedImage(tw * 2, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        GlacierScene s = new GlacierScene();
        s.frozen = true;
        d.addGLEventListener(s);
        String status = "";
        for (int i = 0; i < VIEWS.length; i++) {
            apply(s, i);
            for (int k = 0; k < 400; k++) {
                d.display();
                if (k > 8 && !s.busy()) break;
                Thread.sleep(5);
            }
            for (int k = 0; k < 6; k++) d.display();
            if (i == 0) status = s.status;
            final BufferedImage[] g = new BufferedImage[1];
            d.invoke(true, a -> { g[0] = IceDemo.grab(a, tw, th); return true; });
            Graphics2D gg = img.createGraphics();
            gg.drawImage(g[0], (i % 2) * tw, (i / 2) * th, null);
            IceDemo.label(gg, (String) VIEWS[i][0], (i % 2) * tw, (i / 2) * th);
            gg.dispose();
        }
        ImageIO.write(img, "png", out);
        System.out.println(status);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    private static void open() {
        GlacierScene scene = new GlacierScene();
        apply(scene, 0);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                if (e.isShiftDown()) {
                    // Blickpunkt verschieben (in Blickrichtung und quer)
                    double a = Math.toRadians(scene.yaw), k = scene.dist / 600;
                    double[] t = scene.target.clone();
                    double fx = -Math.sin(a), fz = Math.cos(a), rx = -fz, rz = fx;   // vorwärts und rechts in der Ebene
                    double dx = e.getX() - lx, dy = e.getY() - ly;
                    t[0] += (-dx * rx + dy * fx) * k;
                    t[2] += (-dx * rz + dy * fz) * k;
                    t[1] = scene.glacier.covers(t[0], t[2]) ? scene.glacier.base(t[0], t[2]) : Math.max(0, scene.glacier.ground(t[0], t[2]));
                    scene.target = t;
                } else {
                    scene.yaw += (e.getX() - lx) * 0.3;
                    scene.pitch = Math.max(-5, Math.min(85, scene.pitch + (e.getY() - ly) * 0.2));
                }
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(30, Math.min(9000, scene.dist * Math.pow(1.12, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.ice · Gletscher");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = new JLabel(" ");
        state.setForeground(muted);
        JPanel views = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        views.setOpaque(false);
        views.setPreferredSize(new Dimension(300, 80));
        for (int k = 0; k < VIEWS.length; k++) {
            final int q = k;
            FButton b = new FButton((String) VIEWS[k][0]);
            b.addActionListener(e -> apply(scene, q));
            views.add(b);
        }
        JLabel crL = label("", ink), adL = label("", ink), altL = label("", ink), azL = label("", ink);
        FSlider cr = new FSlider(0, 200, 100), ad = new FSlider(-300, 300, 0), alt = new FSlider(-5, 75, 32), az = new FSlider(0, 359, 300);
        Runnable txt = () -> {
            crL.setText(String.format(Locale.GERMAN, "Spalten: %d %%", cr.getValue()));
            adL.setText(String.format(Locale.GERMAN, "Vorstoß der Front: %+d m", ad.getValue()));
            altL.setText(String.format(Locale.GERMAN, "Sonnenhöhe: %d°", alt.getValue()));
            azL.setText(String.format(Locale.GERMAN, "Sonnenrichtung: %d°", az.getValue()));
        };
        cr.addChangeListener(e -> { scene.crevasses = cr.getValue() / 100.0; txt.run(); });
        ad.addChangeListener(e -> { scene.advance = ad.getValue(); txt.run(); });
        alt.addChangeListener(e -> { scene.sunAlt = alt.getValue(); txt.run(); });
        az.addChangeListener(e -> { scene.sunAz = az.getValue(); txt.run(); });
        txt.run();
        List<Component> items = new ArrayList<>(List.of(title, state, views, crL, cr, adL, ad, altL, alt, azL, az,
                label("<html><body style='width:270px'>Ziehen: kreisen · Umschalt + Ziehen: Blickpunkt schieben · Rad: Abstand. "
                        + "„Spalten“ wirkt beim Neubau der Kacheln (nah sofort, fern nach und nach).</body></html>", muted)));
        for (Component c : items) {
            if (c instanceof JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(Box.createVerticalStrut(5));
        }
        side.setPreferredSize(new Dimension(330, 10));
        FFrame f = new FFrame("com.dan.ice – Gletscher");
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
        new Timer(300, e -> state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>")).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
