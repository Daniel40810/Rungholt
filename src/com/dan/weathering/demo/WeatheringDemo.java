package com.dan.weathering.demo;

import com.dan.fbutton.FButton;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.weathering.Weathering;
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
import java.util.function.Consumer;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10): ein Stahlpfahl und ein Steinblock im
 * Gezeitenwasser. Regler für Wasserstand (oder Gezeit im Zeitraffer), Seegang, Regen, Sonne und den Zustand der
 * Fläche (Rost, Algen, Salz, Moos), dazu „Brecher“ (ein Schwall auf die Nässekarte) und drei Ansichten: Bild, Nässe,
 * Masken. Ziehen dreht, das Rad zoomt.
 * Ohne Fenster: {@code WeatheringDemo --bild datei.png [breite]} rechnet sechs Lagen nebeneinander und prüft dabei
 * Grafikkarte gegen Prozessor.
 */
public final class WeatheringDemo {
    private WeatheringDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1800);
            return;
        }
        SwingUtilities.invokeLater(WeatheringDemo::open);
    }

    /** Eine Kachel des Standbilds: Name und wie die Lage eingestellt wird. */
    record Tile(String label, Consumer<Weathering> setup, int show) { }

    static void bild(File out, int width) throws Exception {
        Tile[] tiles = {
                new Tile("Niedrigwasser, ruhig", w -> { w.waterLevel = -1.5; w.waveHeight = 0.3; w.settle(); }, 0),
                new Tile("Hochwasser, Bft 5", w -> { w.waterLevel = 1.5; w.waveHeight = 2.0; w.windSpeed = 9.3; w.settle(); }, 0),
                new Tile("Ebbe: nasse Zone bleibt", w -> { w.waterLevel = -0.6; w.waveHeight = 0.6; w.settle(); w.setWetLine(1.3); }, 0),
                new Tile("Regen aus West", w -> { w.waterLevel = -1.0; w.waveHeight = 1.0; w.rainRate = 4; w.windFromDeg = 270; w.settle(); }, 0),
                new Tile("Brecher auf der Nässekarte", w -> {
                    w.waterLevel = -1.2; w.waveHeight = 1.0; w.settle();
                    w.wet(0.62, 0.45, 2.0, 1.0); w.wet(0.70, 0.38, 1.2, 0.8); }, 0),
                new Tile("Masken: Rost rot, Algen grün, Salz weiß", w -> { w.waterLevel = -1.5; w.waveHeight = 1.5; w.salt = 0.9; w.moss = 0.6; w.settle(); }, 2)};
        int cols = 3, tw = width / cols, th = tw * 3 / 4;
        BufferedImage img = new BufferedImage(tw * cols, th * 2, BufferedImage.TYPE_INT_RGB);
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, tw, th);
        String check = "";
        for (int i = 0; i < tiles.length; i++) {
            Weathering w = new Weathering();
            w.windSpeed = 7;
            w.windFromDeg = 240;
            w.sun = 0.6;
            WeatheringScene s = new WeatheringScene(w);
            tiles[i].setup().accept(w);
            s.frozen = true;
            s.tideAuto = false;
            s.show = tiles[i].show();
            s.yaw = i == 3 ? 250 : 215;
            d.addGLEventListener(s);
            d.display();
            if (i == 4) {
                final String[] c = new String[1];
                d.invoke(true, a -> { c[0] = s.check(a); return true; });
                check = c[0];
            }
            final BufferedImage[] g = new BufferedImage[1];
            d.invoke(true, a -> { g[0] = grab(a, tw, th); return true; });
            Graphics2D gg = img.createGraphics();
            int x = (i % cols) * tw, y = (i / cols) * th;
            gg.drawImage(g[0], x, y, null);
            label(gg, tiles[i].label(), x, y);
            gg.dispose();
            d.removeGLEventListener(s);
            d.invoke(true, a -> { s.dispose(a); return true; });
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
        Weathering w = Weathering.breeze();
        WeatheringScene scene = new WeatheringScene(w);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw -= (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-10, Math.min(85, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(5, Math.min(60, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.weathering");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = label(" ", ink), check = label(" ", muted);
        JLabel levelLbl = label("", ink), hsLbl = label("", ink), rainLbl = label("", ink), rustLbl = label("", ink), algLbl = label("", ink),
                saltLbl = label("", ink), mossLbl = label("", ink), sunLbl = label("", ink);
        FSlider level = new FSlider(-20, 20, 0), hs = new FSlider(0, 80, 20), rain = new FSlider(0, 100, 0), rust = new FSlider(0, 100, 60),
                alg = new FSlider(0, 100, 80), salt = new FSlider(0, 100, 50), moss = new FSlider(0, 100, 10), sun = new FSlider(-5, 70, 35);
        FComboBox tide = new FComboBox(new Object[]{"Gezeit im Zeitraffer (600-fach)", "Gezeit 3600-fach", "Wasserstand von Hand"});
        FComboBox view = new FComboBox(new Object[]{"Bild", "Nässe", "Masken"});
        level.addChangeListener(e -> { if (!scene.tideAuto) w.waterLevel = level.getValue() / 10.0; });
        hs.addChangeListener(e -> { w.waveHeight = hs.getValue() / 10.0; w.windSpeed = 4 + hs.getValue() / 3.0; });
        rain.addChangeListener(e -> w.rainRate = rain.getValue() / 10.0);
        rust.addChangeListener(e -> w.rust = rust.getValue() / 100.0);
        alg.addChangeListener(e -> w.algae = alg.getValue() / 100.0);
        salt.addChangeListener(e -> w.salt = salt.getValue() / 100.0);
        moss.addChangeListener(e -> w.moss = moss.getValue() / 100.0);
        sun.addChangeListener(e -> scene.sunAlt = sun.getValue());
        tide.addActionListener(e -> {
            int k = tide.getSelectedIndex();
            scene.tideAuto = k < 2;
            scene.lapse = k == 1 ? 3600 : k == 0 ? 600 : 60;
        });
        view.addActionListener(e -> scene.show = view.getSelectedIndex());
        FButton breaker = new FButton("Brecher");
        breaker.addActionListener(e -> {
            // ein Schwall von der Wetterseite: bis 4 m über das Wasser, unten am meisten
            double u = ((360 - w.windFromDeg + 90) % 360) / 360.0;
            double v = (w.waterLevel + 1.5 - w.mapBottom) / w.mapHeightM;
            w.wet(u, v, 2.5, 1.0);
            w.wet(u + 0.04, v + 0.05, 1.5, 0.8);
        });
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.setOpaque(false);
        String[] pn = {"Ruhig", "Brise", "Sturm", "Regen"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            FButton b = new FButton(pn[i]);
            b.addActionListener(e -> {
                Weathering p = switch (k) { case 0 -> Weathering.calm(); case 2 -> Weathering.storm(); default -> Weathering.breeze(); };
                hs.setValue((int) Math.round(Math.min(8, p.waveHeight) * 10));
                rain.setValue(k == 3 ? 30 : (int) Math.round(p.rainRate * 10));
            });
            presets.add(b);
        }
        Component[] items = {title, state, presets, label("Wasser", ink), tide, levelLbl, level, hsLbl, hs, breaker, rainLbl, rain, sunLbl, sun,
                rustLbl, rust, algLbl, alg, saltLbl, salt, mossLbl, moss, label("Ansicht", ink), view, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(5));
        }
        side.setPreferredSize(new Dimension(320, 10));
        FFrame f = new FFrame("com.dan.weathering – Verwitterung");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(canvas, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1400, 860));
        f.setSize(1400, 860);
        f.setMinimumSize(new Dimension(900, 560));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Animator anim = new Animator(canvas);
        anim.start();
        new Timer(250, e -> {
            if (scene.tideAuto) level.setValue((int) Math.round(w.waterLevel * 10));
            levelLbl.setText(String.format(Locale.GERMAN, "Wasserstand: %+.1f m", w.waterLevel));
            hsLbl.setText(String.format(Locale.GERMAN, "Seegang Hs: %.1f m", w.waveHeight));
            rainLbl.setText(String.format(Locale.GERMAN, "Regen: %.1f mm/h", w.rainRate));
            sunLbl.setText(String.format(Locale.GERMAN, "Sonnenhöhe: %d°", sun.getValue()));
            rustLbl.setText(String.format(Locale.GERMAN, "Rost: %d %%", rust.getValue()));
            algLbl.setText(String.format(Locale.GERMAN, "Algen: %d %%", alg.getValue()));
            saltLbl.setText(String.format(Locale.GERMAN, "Salz: %d %%", salt.getValue()));
            mossLbl.setText(String.format(Locale.GERMAN, "Moos: %d %%", moss.getValue()));
            state.setText("<html><body style='width:280px'>" + scene.status.replace(" · ", "<br>") + "</body></html>");
            if (scene.selfCheck.isEmpty() && !scene.status.isEmpty()) canvas.invoke(false, a -> { scene.check(a); return true; });
            check.setText("<html><body style='width:280px'>" + scene.selfCheck + "</body></html>");
        }).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
