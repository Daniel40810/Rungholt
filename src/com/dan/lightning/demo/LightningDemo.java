package com.dan.lightning.demo;

import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.fbutton.FButton;
import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
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
 * Vorführung des Packages ohne Leuchtturm-App (Paketvertrag Punkt 3 und 10): Gewitter über der See bei Nacht, ein
 * Mast von 30 m als Fangpunkt, die Blitze spiegeln sich im Wasser. Rechts: Stärke, „Blitz“, „In den Mast“,
 * „Spinnenblitz“, „Wolkenblitz“, „Wetterleuchten“, Zeitlupe, Regen, Wolkenuntergrenze, Belichtung. Ziehen dreht den
 * Blick.
 * Ohne Fenster: {@code LightningDemo --bild datei.png [breite]} rechnet einen Erdblitz über der See beim ersten und
 * beim dritten Schlag, den Leitblitz in Zeitlupe, einen Einschlag in den Mast, einen Spinnenblitz, einen Wolkenblitz,
 * Wetterleuchten, einen Blitz im Regen und den Ablauf eines Blitzes mit dem Donner als Kurve. Dabei vergleicht es
 * Grafikkarte und Prozessor.
 */
public final class LightningDemo {
    private LightningDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(LightningDemo::open);
    }

    /** Einen Blitz bauen lassen und die Szene auf einen Augenblick nach seinem Start stellen. */
    static Bolt at(LightningScene s, java.util.function.Supplier<Bolt> make, double after) {
        s.lightning.restart();
        s.lightning.setLevel(Lightning.Level.OFF);
        double[] e = s.eye();
        s.lightning.listenerX = e[0];
        s.lightning.listenerY = e[1];
        s.lightning.listenerZ = e[2];
        Bolt b = make.get();
        s.lightning.setTime(b.t0 + after);
        return b;
    }

    static void bild(File out, int width) throws Exception {
        int w2 = width / 2, h2 = w2 * 9 / 16, w3 = width / 3, h3 = w3 * 3 / 4, h4 = width * 9 / 40;
        BufferedImage img = new BufferedImage(width, h2 + 2 * h3 + h4, BufferedImage.TYPE_INT_RGB);
        Graphics2D gg = img.createGraphics();
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w2, h2);
        LightningScene s = new LightningScene();
        s.frozen = true;
        d.addGLEventListener(s);
        d.display();
        Lightning L = s.lightning;
        // 1 Erdblitz über der See: erster Schlag, dritter Schlag
        s.eyeX = 0; s.eyeZ = 600; s.eyeH = 5; s.fovy = 62;
        L.seed = 31;
        Bolt b = at(s, () -> L.strike(-900, -3200), 0);
        s.lookAt(-600, 900, -3200);
        s.pitch = 13;
        L.setTime(b.t0 + b.strokeTime[0] + 0.004);
        d.display();
        String check = "";
        {
            final String[] c = new String[1];
            d.invoke(true, a -> { c[0] = s.check(a); return true; });
            check = c[0];
        }
        d.display();
        gg.drawImage(grab(d, w2, h2), 0, 0, null);
        label(gg, f("Erdblitz über der See, erster Schlag (%.0f kA): Äste leuchten mit, das Wasser spiegelt", b.strokeKA[0]), 0, 0);
        int k3 = Math.min(2, b.strokeTime.length - 1);
        L.setTime(b.t0 + b.strokeTime[k3] + 0.006);
        d.display();
        gg.drawImage(grab(d, w2, h2), w2, 0, null);
        label(gg, f("derselbe Blitz %.0f ms später, %d. Schlag: nur der Hauptkanal", (b.strokeTime[k3] - b.strokeTime[0]) * 1000, k3 + 1), w2, 0);
        // 2 Leitblitz, Mast, Spinnenblitz
        d.setSurfaceSize(w3, h3);
        L.seed = 31;
        b = at(s, () -> L.strike(-900, -3200), 0);
        L.setTime(b.t0 + b.leaderTime * 0.7);
        d.display();
        gg.drawImage(grab(d, w3, h3), 0, h2, null);
        label(gg, f("Zeitlupe: der Leitblitz nach %.0f ms, kurz vor dem Einschlag", b.leaderTime * 0.7 * 1000), 0, h2);
        s.basePre = 1500;
        s.eyeX = 110; s.eyeZ = -140; s.eyeH = 4; s.fovy = 70;
        s.lookAt(s.mastX, 70, s.mastZ);
        L.seed = 3;
        b = at(s, () -> L.strikeAttractor(0), 0);
        L.setTime(b.t0 + b.strokeTime[0] + 0.01);
        s.basePre = 700;
        d.display();
        gg.drawImage(grab(d, w3, h3), w3, h2, null);
        Lightning.Thunder th = L.pendingThunder().isEmpty() ? null : L.pendingThunder().get(0);
        label(gg, f("Blitz in den Mast aus %.0f m: Knall nach %.2f s", Math.hypot(s.eyeX - s.mastX, s.eyeZ - s.mastZ), th != null ? th.time() - b.t0 - b.strokeTime[0] : 0), w3, h2);
        s.basePre = 1500;
        s.eyeX = 0; s.eyeZ = 600; s.eyeH = 5; s.fovy = 75;
        s.yaw = 0; s.pitch = 28;
        L.seed = 8;
        b = at(s, () -> L.discharge(Bolt.Kind.SPIDER, 300, -2600), 0);
        L.setTime(b.t0 + b.strokeTime[Math.min(1, b.strokeTime.length - 1)] + 0.005);
        d.display();
        gg.drawImage(grab(d, w3, h3), 2 * w3, h2, null);
        label(gg, "Spinnenblitz unter der Wolkenuntergrenze", 2 * w3, h2);
        // 3 Wolkenblitz, Wetterleuchten, Regen
        s.yaw = 10; s.pitch = 12; s.fovy = 62;
        L.seed = 5;
        b = at(s, () -> L.discharge(Bolt.Kind.INTRA_CLOUD, 800, -4500), 0);
        L.setTime(b.t0 + b.strokeTime[0] + 0.004);
        d.display();
        gg.drawImage(grab(d, w3, h3), 0, h2 + h3, null);
        label(gg, "Wolkenblitz: die Wolke leuchtet von innen", 0, h2 + h3);
        s.yaw = 30; s.pitch = 3; s.fovy = 50;
        s.cover = 0.55;
        L.seed = 6;
        b = at(s, () -> L.discharge(Bolt.Kind.SHEET, Math.sin(Math.toRadians(30)) * 45_000 + s.eyeX, -Math.cos(Math.toRadians(30)) * 45_000 + s.eyeZ), 0);
        L.setTime(b.t0 + b.strokeTime[0] + 0.004);
        s.basePre = 6000;
        d.display();
        gg.drawImage(grab(d, w3, h3), w3, h2 + h3, null);
        label(gg, "Wetterleuchten in 45 km: kein Kanal, kein Donner", w3, h2 + h3);
        s.basePre = 1500;
        s.cover = 0.8;
        s.yaw = -5; s.pitch = 13; s.fovy = 62;
        s.rain = 8;
        s.visibilityKm = 10;
        L.seed = 31;
        b = at(s, () -> L.strike(-900, -3200), 0);
        s.lookAt(-600, 900, -3200);
        s.pitch = 13;
        L.setTime(b.t0 + b.strokeTime[0] + 0.004);
        d.display();
        gg.drawImage(grab(d, w3, h3), 2 * w3, h2 + h3, null);
        label(gg, "derselbe Erdblitz im Regen 8 mm/h: Hof und leuchtende Luft", 2 * w3, h2 + h3);
        s.rain = 0;
        s.visibilityKm = 25;
        // 4 Ablauf als Kurve
        L.seed = 31;
        b = at(s, () -> L.strike(-900, -3200), 0);
        Lightning.Thunder t2 = L.pendingThunder().isEmpty() ? null : L.pendingThunder().get(0);
        timeline(gg, b, t2, 0, h2 + 2 * h3, width, h4);
        gg.dispose();
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println(check);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
        System.exit(0);
    }

    /** Helligkeit eines Blitzes über der Zeit (log), die Schläge markiert, dazu wann und wie lange der Donner kommt. */
    static void timeline(Graphics2D g, Bolt b, Lightning.Thunder th, int x0, int y0, int w, int h) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 28));
        g.fillRect(x0, y0, w, h);
        int left = 70, right = 30, top = 40, bottom = 34, pw = w - left - right, ph = h - top - bottom;
        // links: Licht über 0,5 s; rechts: Donner auf einer zweiten Zeitachse
        int split = (int) (pw * 0.58);
        double span = Math.max(0.4, b.strokeTime[b.strokeTime.length - 1] + 0.15);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.setColor(new Color(60, 72, 86));
        for (int dec = -3; dec <= 1; dec++) {
            int y = y0 + top + (int) (ph * (1 - (dec + 3) / 4.0));
            g.drawLine(x0 + left, y, x0 + left + split, y);
            g.setColor(new Color(150, 165, 178));
            g.drawString(dec == 0 ? "1" : "10^" + dec, x0 + 18, y + 4);
            g.setColor(new Color(60, 72, 86));
        }
        Path2D p = new Path2D.Double();
        for (int i = 0; i <= split; i++) {
            double t = span * i / split;
            double lv = Math.max(1e-3, b.mainLevel(t) + b.leaderLevel(t));
            double y = y0 + top + ph * (1 - (Math.log10(lv) + 3) / 4.0);
            if (i == 0) p.moveTo(x0 + left + i, y); else p.lineTo(x0 + left + i, y);
        }
        g.setColor(new Color(200, 215, 255));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(p);
        g.setStroke(new BasicStroke(1f));
        for (int k = 0; k < b.strokeTime.length; k++) {
            int x = x0 + left + (int) (split * b.strokeTime[k] / span);
            g.setColor(new Color(226, 67, 47));
            g.drawLine(x, y0 + top + ph, x, y0 + top + ph + 6);
            g.drawString(f("%.0f kA", b.strokeKA[k]), x - 14, y0 + top + ph + 20);
        }
        for (double s = 0; s <= span + 1e-9; s += 0.1) {
            int x = x0 + left + (int) (split * s / span);
            g.setColor(new Color(150, 165, 178));
            g.drawString(f("%.1f s", s), x - 10, y0 + h - 4);
        }
        // Donner
        int dx0 = x0 + left + split + 30, dw = pw - split - 30;
        g.setColor(new Color(60, 72, 86));
        g.drawLine(dx0, y0 + top + ph, dx0 + dw, y0 + top + ph);
        if (th != null) {
            double tEnd = th.time() - b.t0 + th.duration() + 1;
            int xs = dx0 + (int) (dw * (th.time() - b.t0) / tEnd), xe = dx0 + (int) (dw * (th.time() - b.t0 + th.duration()) / tEnd);
            g.setColor(new Color(217, 180, 90, 160));
            g.fillRect(xs, y0 + top + ph / 3, Math.max(2, xe - xs), ph * 2 / 3);
            g.setColor(new Color(217, 180, 90));
            g.drawString(f("Donner nach %.1f s, rollt %.1f s, %.0f dB", th.time() - b.t0, th.duration(), th.levelDb()), dx0, y0 + top + ph / 3 - 8);
            for (int s = 0; s <= tEnd; s += 5) {
                int x = dx0 + (int) (dw * s / tEnd);
                g.setColor(new Color(150, 165, 178));
                g.drawString(s + " s", x - 8, y0 + h - 4);
            }
        }
        label(g, f("Ablauf: Leitblitz %.0f ms, %d Schläge (Helligkeit relativ zu 30 kA, Trägheit des Auges), Donner in %.1f km",
                b.leaderTime * 1000, b.strokeTime.length, th != null ? th.distance() / 1000 : 0), x0, y0);
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }

    static BufferedImage grab(GLOffscreenAutoDrawable d, int w, int h) {
        final BufferedImage[] g = new BufferedImage[1];
        d.invoke(true, a -> { g[0] = read(a, w, h); return true; });
        return g[0];
    }

    static void label(Graphics2D g, String t, int x, int y) {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        g.setColor(new Color(0, 0, 0, 130));
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

    // ------------------------------------------------------------------ Fenster

    private static void open() {
        LightningScene scene = new LightningScene();
        scene.lookAt(0, 700, -4000);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw += (e.getX() - lx) * 0.2;
                scene.pitch = Math.max(-20, Math.min(85, scene.pitch - (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.fovy = Math.max(15, Math.min(90, scene.fovy * Math.pow(1.08, e.getPreciseWheelRotation())));
            }
        };
        canvas.addMouseListener(m);
        canvas.addMouseMotionListener(m);
        canvas.addMouseWheelListener(m);

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178), red = new Color(226, 67, 47);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.lightning");
        title.setForeground(red);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), rainLbl = label("", ink), baseLbl = label("", ink), expLbl = label("", ink), slowLbl = label("", ink), check = label(" ", muted);
        String[] levels = new String[Lightning.Level.values().length];
        for (Lightning.Level l : Lightning.Level.values()) levels[l.ordinal()] = LightningScene.levelName(l);
        FComboBox level = new FComboBox(levels);
        level.setSelectedIndex(Lightning.Level.STRONG.ordinal());
        level.addActionListener(e -> scene.lightning.setLevel(Lightning.Level.values()[level.getSelectedIndex()]));
        FButton strike = new FButton("Blitz"), mast = new FButton("In den Mast"), spider = new FButton("Spinnenblitz"), ic = new FButton("Wolkenblitz"), sheet = new FButton("Wetterleuchten");
        strike.addActionListener(e -> {
            double[] f = scene.forward();
            double r = 2500 + Math.random() * 3000;
            scene.lightning.strike(scene.eyeX + f[0] * r, scene.eyeZ + f[2] * r);
        });
        mast.addActionListener(e -> scene.lightning.strikeAttractor(0));
        spider.addActionListener(e -> scene.lightning.discharge(Bolt.Kind.SPIDER, scene.eyeX + scene.forward()[0] * 2500, scene.eyeZ + scene.forward()[2] * 2500));
        ic.addActionListener(e -> scene.lightning.discharge(Bolt.Kind.INTRA_CLOUD, scene.eyeX + scene.forward()[0] * 4000, scene.eyeZ + scene.forward()[2] * 4000));
        sheet.addActionListener(e -> scene.lightning.discharge(Bolt.Kind.SHEET, scene.eyeX + scene.forward()[0] * 45_000, scene.eyeZ + scene.forward()[2] * 45_000));
        FSlider rain = new FSlider(0, 100, 0), base = new FSlider(300, 3000, 950), exp = new FSlider(-40, 40, 0), slow = new FSlider(0, 300, 0);
        rain.addChangeListener(e -> scene.rain = rain.getValue() == 0 ? 0 : 0.1 * Math.pow(1000, rain.getValue() / 100.0));
        base.addChangeListener(e -> scene.lightning.cloudBase = base.getValue());
        exp.addChangeListener(e -> scene.basePre = 1500 * Math.pow(2, exp.getValue() / 10.0));
        slow.addChangeListener(e -> scene.timeScale = Math.pow(10, -slow.getValue() / 100.0));
        FCheckBox freeze = new FCheckBox("Anhalten");
        freeze.addActionListener(e -> scene.frozen = freeze.isSelected());
        for (Component c : new Component[]{title, state, label("Gewitter", muted), level, strike, mast, spider, ic, sheet, rainLbl, rain, baseLbl, base, slowLbl, slow,
                expLbl, exp, freeze, check}) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(300, 760));
        new Timer(200, e -> {
            rainLbl.setText(f("Regen %.1f mm/h", scene.rain));
            baseLbl.setText(f("Wolkenuntergrenze %.0f m", scene.lightning.cloudBase));
            expLbl.setText(f("Belichtung %+.1f Blenden", Math.log(scene.basePre / 1500) / Math.log(2)));
            slowLbl.setText(scene.timeScale > 0.99 ? "Zeitlupe aus" : f("Zeitlupe 1 : %.0f", 1 / scene.timeScale));
            state.setText("<html><body style='width:240px'>" + scene.status() + "</body></html>");
            if (!scene.check.isEmpty()) check.setText("<html><body style='width:240px'>" + scene.check + "</body></html>");
        }).start();
        // FFrame zeichnet Titel und Rahmen selbst: der Inhalt gehört in getComponentPane()
        FFrame f = new FFrame("com.dan.lightning – Gewitter");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1400, 840));
        f.setSize(1400, 840);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator an = new Animator(canvas);
        an.start();
        // Selbsttest beim ersten Erdblitz
        new Timer(500, e -> {
            if (scene.check.isEmpty() && !scene.lightning.bolts().isEmpty())
                canvas.invoke(false, a -> { scene.check(a); return true; });
        }).start();
    }

    static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }
}
