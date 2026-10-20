package com.dan.buoyancy.demo;

import com.dan.buoyancy.Buoy;
import com.dan.buoyancy.Buoyancy;
import com.dan.buoyancy.Ship;
import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
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
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): ein Fahrwasser mit Tonnen und ein Kümo im
 * Seegang. Regler für Beaufort und Sonnenhöhe, „Schiff“ setzt eines aufs Fahrwasser, „Brücke“ stellt die Kamera auf
 * seine Brücke, „Wischer“ schaltet den Scheibenwischer. Ziehen dreht, das Rad zoomt.
 * Ohne Fenster: {@code BuoyancyDemo --bild datei.png [breite]} rechnet fünf Lagen und die Bewegungen als Kurven.
 */
public final class BuoyancyDemo {
    private BuoyancyDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(BuoyancyDemo::open);
    }

    static final double[] HS = {0, 0.1, 0.2, 0.6, 1.0, 2.0, 3.0, 4.0, 5.5, 7.0, 9.0, 11.5, 14};
    static final double[] WS = {0.1, 0.9, 2.5, 4.4, 6.7, 9.4, 12.3, 15.5, 19.0, 22.6, 26.5, 30.6, 35.0};

    static void seaState(BuoyancyScene s, int bft) { s.setSea(new DemoSea(HS[bft], WS[bft], 240, 5), WS[bft], 240); }

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 2 / 3;
        BufferedImage img = new BufferedImage(tw * 3, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        BuoyancyScene s = new BuoyancyScene();
        s.frozen = true;
        d.addGLEventListener(s);
        Graphics2D g = img.createGraphics();
        String[] title = {"Tonnen bei Bft 4", "Tonnen bei Bft 9", "Kümo im Seegang, Bft 6", "Kümo nachts: Topplichter, grün und rot", "Von der Brücke, Bft 6, der Wischer läuft"};
        Ship k = null;
        for (int p = 0; p < 5; p++) {
            int bft = p == 0 ? 4 : p == 1 ? 9 : 6;
            seaState(s, bft);
            s.sunAlt = p == 3 ? -6 : 22;
            s.sunAz = p == 3 ? 250 : 150;
            s.exposure = p == 3 ? 40 : 1;
            s.onBridge = false;
            s.fovy = 50;
            if (p <= 1) {
                Buoy b = s.fleet.buoys.get(2);
                s.tx = b.anchorX; s.ty = 3; s.tz = b.anchorZ; s.yaw = 60; s.pitch = 4; s.dist = 38;
                s.advance(20);
            } else {
                s.fleet.ships.clear();
                k = s.fleet.spawn(true, 6500);
                s.advance(20);
                if (p == 2) { s.tx = k.x; s.ty = 6; s.tz = k.z; s.yaw = k.heading + 70; s.pitch = 3; s.dist = 150; }
                if (p == 3) { s.tx = k.x; s.ty = 8; s.tz = k.z; s.yaw = k.heading + 180 - 25; s.pitch = 1; s.dist = 700; }
                if (p == 4) { s.onBridge = true; s.fovy = 62; k.wiper.on = true; s.advance(0.7); }
            }
            s.check = "";
            for (int i = 0; i < 2; i++) d.display();
            final BufferedImage[] shot = new BufferedImage[1];
            d.invoke(true, a -> { shot[0] = grab(a, tw, th); return true; });
            int x = (p % 3) * tw, y = (p / 3) * th;
            g.drawImage(shot[0], x, y, null);
            label(g, title[p], x, y);
            if (p == 2) System.out.println(s.check);
        }
        // Kurven: 90 s bei Bft 6, Tonne und Kümo
        seaState(s, 6);
        s.fleet.ships.clear();
        Ship kk = s.fleet.spawn(true, 3000);
        Buoy bb = s.fleet.buoys.get(2);
        int n = 900;
        double[][] series = new double[5][n];
        for (int i = 0; i < n; i++) {
            s.advance(0.1);
            series[0][i] = s.sea.height(bb.x, bb.z);
            series[1][i] = bb.y;
            series[2][i] = Math.toDegrees(bb.roll);
            series[3][i] = Math.toDegrees(kk.roll);
            series[4][i] = Math.toDegrees(kk.pitch);
        }
        chart(g, 2 * tw, th, tw, th, series);
        g.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    static void chart(Graphics2D g, int x0, int y0, int w, int h, double[][] s) {
        g.setColor(new Color(20, 26, 32));
        g.fillRect(x0, y0, w, h);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String[] name = {"Welle an der Tonne (m)", "Tonne taucht (m)", "Tonne rollt (°)", "Kümo rollt (°)", "Kümo stampft (°)"};
        Color[] col = {new Color(90, 150, 200), new Color(226, 160, 63), new Color(226, 67, 47), new Color(111, 196, 140), new Color(200, 200, 210)};
        int rows = 3, rh = (h - 40) / rows;
        int[][] groups = {{0, 1}, {2}, {3, 4}};
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        for (int r = 0; r < rows; r++) {
            int top = y0 + 12 + r * rh, mid = top + rh / 2;
            double max = 0.1;
            for (int k : groups[r]) for (double v : s[k]) max = Math.max(max, Math.abs(v));
            g.setColor(new Color(60, 70, 80));
            g.drawLine(x0 + 10, mid, x0 + w - 10, mid);
            int ly = top + 12;
            for (int k : groups[r]) {
                Path2D path = new Path2D.Double();
                for (int i = 0; i < s[k].length; i++) {
                    double px = x0 + 10 + (w - 20) * i / (double) (s[k].length - 1), py = mid - s[k][i] / max * (rh / 2.0 - 6);
                    if (i == 0) path.moveTo(px, py); else path.lineTo(px, py);
                }
                g.setColor(col[k]);
                g.setStroke(new BasicStroke(1.4f));
                g.draw(path);
                g.drawString(String.format(Locale.GERMAN, "%s, bis %.1f", name[k], max(s[k])), x0 + 14, ly);
                ly += 14;
            }
        }
        g.setColor(new Color(150, 160, 170));
        g.drawString("90 s bei Bft 6", x0 + w - 100, y0 + h - 10);
    }

    static double max(double[] v) { double m = 0; for (double x : v) m = Math.max(m, Math.abs(x)); return m; }

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
        BuoyancyScene scene = new BuoyancyScene();
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
                scene.dist = Math.max(8, Math.min(3000, scene.dist * Math.pow(1.12, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.buoyancy");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted), bftL = label("", ink), sunL = label("", ink);
        FSlider bft = new FSlider(0, 12, 5), sun = new FSlider(-8, 60, 25);
        bft.addChangeListener(e -> seaState(scene, bft.getValue()));
        sun.addChangeListener(e -> { scene.sunAlt = sun.getValue(); scene.exposure = sun.getValue() < 0 ? 40 : 1; });
        FButton ship = new FButton("Schiff aufs Fahrwasser"), toShip = new FButton("Zum Schiff"), toBuoy = new FButton("Zur Tonne");
        FCheckBox bridge = new FCheckBox("Kamera auf der Brücke"), wiper = new FCheckBox("Scheibenwischer");
        ship.addActionListener(e -> { scene.fleet.ships.clear(); scene.fleet.spawn(true, 5000); });
        toShip.addActionListener(e -> { if (!scene.fleet.ships.isEmpty()) { Ship k = scene.fleet.ships.get(0); scene.tx = k.x; scene.tz = k.z; scene.ty = 6; scene.dist = 160; } });
        toBuoy.addActionListener(e -> { Buoy b = scene.fleet.buoys.get(2); scene.tx = b.anchorX; scene.tz = b.anchorZ; scene.ty = 3; scene.dist = 40; });
        bridge.addActionListener(e -> { scene.onBridge = bridge.isSelected(); scene.fovy = scene.onBridge ? 62 : 50; });
        wiper.addActionListener(e -> { for (Ship k : scene.fleet.ships) k.wiper.on = wiper.isSelected(); });
        Component[] items = {title, state, bftL, bft, sunL, sun, ship, toShip, toBuoy, bridge, wiper, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.buoyancy – Tonnen und Schiffe");
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
            bftL.setText(String.format(Locale.GERMAN, "Beaufort %d (H_s %.1f m)", bft.getValue(), HS[bft.getValue()]));
            sunL.setText(String.format(Locale.GERMAN, "Sonnenhöhe %d°", sun.getValue()));
            double[] eye = scene.eyeAndView()[0];
            state.setText("<html><body style='width:280px'>" + scene.fleet.describe(eye[0], eye[2]).replace(" · ", "<br>") + "</body></html>");
            check.setText("<html><body style='width:280px'>" + scene.check + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }

}
