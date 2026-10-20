package com.dan.flock.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.flock.Flock;
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
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): Möwen um einen Turm. Regler für Beaufort und
 * Sonnenhöhe, „Möwenflug“ setzt die Kamera auf eine Möwe. Ziehen dreht, das Rad zoomt.
 * Ohne Fenster: {@code FlockDemo --bild datei.png [breite]} rechnet vier Lagen, das Aufwindfeld im Schnitt und die
 * Flugbahnen von oben.
 */
public final class FlockDemo {
    private FlockDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(FlockDemo::open);
    }

    static final double[] WS = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 2 / 3;
        BufferedImage img = new BufferedImage(tw * 3, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        FlockScene s = new FlockScene();
        s.frozen = true;
        s.flock.windFromDeg = 270;
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        // Bahnen aufzeichnen (Bft 5, 60 s)
        List<double[]>[] tracks = new List[s.flock.quality.birds];
        for (int i = 0; i < tracks.length; i++) tracks[i] = new ArrayList<>();
        s.advance(0.01);
        for (int i = 0; i < 30 * 240; i++) {
            s.advance(1 / 30.0);
            if (i >= 30 * 180 && i % 3 == 0)
                for (Flock.Gull gl : s.flock.gulls()) if (gl.state != Flock.State.AWAY && gl.id < tracks.length) tracks[gl.id].add(new double[]{gl.pos[0], gl.pos[2], gl.state.ordinal()});
        }
        String[] title = {"Bft 5 aus West: Möwen stehen im Aufwind", "Auf dem Geländer der Galerie", "Möwenflug: auf einer Möwe", "Bft 9: im Lee auf dem Geländer"};
        int[][] at = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (int p = 0; p < 4; p++) {
            if (p == 3) {
                s.flock.windSpeed = WS[9];
                for (int i = 0; i < 30 * 300; i++) s.advance(1 / 30.0);
            }
            s.ride = p == 2;
            s.fovy = p == 2 ? 70 : 50;
            switch (p) {
                case 0 -> { s.yaw = 50; s.pitch = -4; s.dist = 42; s.ty = 24; s.fovy = 42; }
                case 1 -> { s.yaw = 30; s.pitch = 12; s.dist = 16; s.ty = 24; }
                case 3 -> { s.yaw = 250; s.pitch = 10; s.dist = 18; s.ty = 24; }
                default -> {
                    // eine Möwe, die gerade im Revier fliegt
                    for (Flock.Gull gl : s.flock.gulls()) if (gl.state == Flock.State.FLY && Math.hypot(gl.pos[0], gl.pos[2]) < 80) { s.rideId = gl.id; break; }
                    for (int i = 0; i < 20; i++) { s.advance(1 / 30.0); s.eyeAndView(); }
                }
            }
            s.check = "";
            for (int i = 0; i < 2; i++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = at[p][0] * tw, y = at[p][1] * th;
            g.drawImage(shot[0], x, y, null);
            label(g, title[p], x, y);
            if (p == 0) System.out.println(s.check);
        }
        s.flock.windSpeed = WS[5];
        section(g, 2 * tw, 0, tw, th, s.flock);
        tracks(g, 2 * tw, th, tw, th, tracks);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    /** Schnitt durch den Turm in Windrichtung: Aufwind als Farbe, Turm grau. */
    static void section(Graphics2D g, int x0, int y0, int w, int h, Flock f) {
        g.setColor(new Color(20, 26, 32));
        g.fillRect(x0, y0, w, h);
        double xmin = -40, xmax = 20, ymax = 45;
        int pw = w - 40, ph = h - 50;
        for (int j = 0; j < ph; j += 2)
            for (int i = 0; i < pw; i += 2) {
                double x = xmin + (xmax - xmin) * i / pw, y = ymax * (1 - j / (double) ph);
                double up = f.updraft(x, y, 0);
                float k = (float) Math.min(1, up / 6);
                g.setColor(new Color(0.08f + 0.85f * k, 0.12f + 0.55f * k, 0.18f + 0.1f * k));
                g.fillRect(x0 + 20 + i, y0 + 30 + j, 2, 2);
            }
        int tx0 = x0 + 20 + (int) ((-FlockScene.TOWER_R - xmin) / (xmax - xmin) * pw), tx1 = x0 + 20 + (int) ((FlockScene.TOWER_R - xmin) / (xmax - xmin) * pw);
        int ty = y0 + 30 + (int) (ph * (1 - FlockScene.TOWER_H / ymax));
        g.setColor(new Color(150, 150, 150));
        g.fillRect(tx0, ty, tx1 - tx0, y0 + 30 + ph - ty);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(Color.WHITE);
        g.drawString(String.format(Locale.GERMAN, "Aufwind am Turm, Wind %.1f m/s von links (gelb = 6 m/s)", f.windSpeed), x0 + 12, y0 + 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("40 m Luv", x0 + 22, y0 + h - 8);
        g.drawString("Lee 20 m", x0 + w - 80, y0 + h - 8);
    }

    /** Bahnen von oben, letzte Minute: Fliegen weiß, im Aufwind gelb, Landen grün. */
    static void tracks(Graphics2D g, int x0, int y0, int w, int h, List<double[]>[] t) {
        g.setColor(new Color(20, 26, 32));
        g.fillRect(x0, y0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double scale = (h - 40) / 320.0;
        int cx = x0 + w / 2, cz = y0 + 20 + (h - 40) / 2;
        g.setColor(new Color(150, 150, 150));
        double r = FlockScene.TOWER_R * scale;
        g.fill(new Ellipse2D.Double(cx - r, cz - r, 2 * r, 2 * r));
        Color[] col = {new Color(220, 225, 230, 150), new Color(226, 180, 63), new Color(111, 196, 140), new Color(200, 120, 220), new Color(220, 225, 230, 150), new Color(120, 140, 160), new Color(0, 0, 0, 0)};
        g.setStroke(new BasicStroke(1.2f));
        for (List<double[]> tr : t) {
            for (int i = 1; i < tr.size(); i++) {
                double[] a = tr.get(i - 1), b = tr.get(i);
                if (Math.hypot(a[0] - b[0], a[1] - b[1]) > 20) continue;
                g.setColor(col[(int) b[2]]);
                g.drawLine((int) (cx + a[0] * scale), (int) (cz + a[1] * scale), (int) (cx + b[0] * scale), (int) (cz + b[1] * scale));
            }
        }
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.drawString("Bahnen der letzten Minute von oben (320 m)", x0 + 12, y0 + 20);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(226, 180, 63));
        g.drawString("im Aufwind", x0 + 12, y0 + h - 10);
        g.setColor(new Color(111, 196, 140));
        g.drawString("Landen", x0 + 100, y0 + h - 10);
        g.setColor(new Color(220, 225, 230));
        g.drawString("Wind von West →", x0 + w - 130, y0 + h - 10);
        Path2D arrow = new Path2D.Double();
        arrow.moveTo(x0 + 20, cz);
        arrow.lineTo(x0 + 60, cz);
        g.draw(arrow);
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
        FlockScene scene = new FlockScene();
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-30, Math.min(85, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(6, Math.min(800, scene.dist * Math.pow(1.12, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.flock");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), bftL = label("", ink), dirL = label("", ink), sunL = label("", ink);
        FSlider bft = new FSlider(0, 12, 5), dir = new FSlider(0, 359, 240), sun = new FSlider(-8, 60, 25);
        bft.addChangeListener(e -> scene.flock.windSpeed = WS[bft.getValue()]);
        dir.addChangeListener(e -> scene.flock.windFromDeg = dir.getValue());
        sun.addChangeListener(e -> scene.sunAlt = sun.getValue());
        FCheckBox ride = new FCheckBox("Möwenflug");
        ride.addActionListener(e -> {
            scene.ride = ride.isSelected();
            scene.fovy = scene.ride ? 70 : 50;
            for (Flock.Gull g : scene.flock.gulls()) if (g.state == Flock.State.FLY) { scene.rideId = g.id; break; }
        });
        Component[] items = {title, state, bftL, bft, dirL, dir, sunL, sun, ride, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.flock – Möwen");
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
            bftL.setText(String.format(Locale.GERMAN, "Beaufort %d (%.1f m/s)", bft.getValue(), WS[bft.getValue()]));
            dirL.setText(String.format(Locale.GERMAN, "Wind aus %d°", dir.getValue()));
            sunL.setText(String.format(Locale.GERMAN, "Sonnenhöhe %d°", sun.getValue()));
            state.setText("<html><body style='width:280px'>" + scene.flock.describe().replace(" · ", "<br>") + "</body></html>");
            check.setText("<html><body style='width:280px'>" + scene.check + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
