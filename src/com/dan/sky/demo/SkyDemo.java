package com.dan.sky.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.sky.Sky;
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
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Vorführung des Packages ohne Leuchtturm (Paketvertrag Punkt 3 und 10). Im Fenster: der Himmel über einer glatten
 * See mit Reglern für Tag, Uhrzeit, Zeitraffer, Sicht, Bedeckung und Regenbogen, Schaltern für Sterne, Planeten,
 * Milchstraße und Sternbildfiguren, „Blitz“ für ein Aufleuchten; ziehen dreht den Blick, das Rad ändert die Öffnung.
 * Ohne Fenster: {@code SkyDemo --bild datei.png [breite] [höhe] [JJJJ-MM-TT]} rechnet Morgen, Mittag, Abend und
 * Nacht nebeneinander und prüft dabei Grafikkarte gegen Prozessor.
 */
public final class SkyDemo {
    private SkyDemo() { }

    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            int w = args.length > 2 ? Integer.parseInt(args[2]) : 2000, h = args.length > 3 ? Integer.parseInt(args[3]) : 560;
            LocalDate day = args.length > 4 ? LocalDate.parse(args[4]) : LocalDate.now(ZONE);
            bild(new File(args[1]), w, h, day);
            return;
        }
        SwingUtilities.invokeLater(SkyDemo::open);
    }

    static double epoch(LocalDate day, double hour) {
        int s = (int) Math.round(hour * 3600);
        return ZonedDateTime.of(day, LocalTime.ofSecondOfDay(Math.min(86_399, s)), ZONE).toInstant().toEpochMilli();
    }

    /** Morgen, Mittag, Abend, Nacht nebeneinander als PNG, mit Beschriftung. */
    static void bild(File out, int w, int h, LocalDate day) throws Exception {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        Sky probe = new Sky();
        double rise = crossing(day, probe.latDeg, probe.lonDeg, true), set = crossing(day, probe.latDeg, probe.lonDeg, false);
        double[] hours = {rise + 0.05, 13.0, set - 0.25, 23.5};
        String[] names = {"Morgen", "Mittag", "Abend", "Nacht"};
        Sky[] skies = new Sky[4];
        for (int i = 0; i < 4; i++) {
            skies[i] = new Sky(epoch(day, hours[i]), probe.latDeg, probe.lonDeg);
            skies[i].rebuildNow();
        }
        SkyScene s = new SkyScene(skies);
        s.frozen = true;
        s.fovy = 60;
        for (int i = 0; i < 4; i++) {
            Sky.State st = skies[i].state();
            s.yaw[i] = i == 3 ? 180 : st.sunAz + (i == 1 ? 25 : 0);
            s.pitch[i] = i == 3 ? 28 : i == 1 ? 18 : 8;
        }
        d.addGLEventListener(s);
        d.display();
        final String[] check = new String[1];
        d.invoke(true, a -> { check[0] = s.check(a); return true; });
        final BufferedImage[] img = new BufferedImage[1];
        d.invoke(true, a -> { img[0] = grab(a, w, h); return true; });
        Graphics2D g = img[0].createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        for (int i = 0; i < 4; i++) {
            Sky.State st = skies[i].state();
            String t = String.format(Locale.GERMAN, "%s %s · Sonne %.1f° · %s", names[i], LocalTime.ofSecondOfDay((long) (hours[i] * 3600)).withNano(0),
                    st.sunAlt, st.twilight());
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRect(i * w / 4 + 8, 8, g.getFontMetrics().stringWidth(t) + 16, 26);
            g.setColor(Color.WHITE);
            g.drawString(t, i * w / 4 + 16, 26);
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
        Sky sky = new Sky();
        LocalDate today = LocalDate.now(ZONE);
        sky.epochMillis = epoch(today, 20);
        SkyScene scene = new SkyScene(sky);
        scene.yaw[0] = 250;
        scene.pitch[0] = 15;
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw[0] -= (e.getX() - lx) * 0.2;
                scene.pitch[0] = Math.max(-20, Math.min(89, scene.pitch[0] + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.fovy = Math.max(5, Math.min(110, scene.fovy * Math.pow(1.1, e.getPreciseWheelRotation())));
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
        JLabel title = new JLabel("com.dan.sky");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JLabel state = new JLabel(" ");
        state.setForeground(ink);
        JLabel dayLbl = new JLabel(), timeLbl = new JLabel(), visLbl = new JLabel(), check = new JLabel(" ");
        for (JLabel l : new JLabel[]{dayLbl, timeLbl, visLbl}) l.setForeground(ink);
        check.setForeground(muted);
        FSlider day = new FSlider(0, 365, today.getDayOfYear() - 1), time = new FSlider(0, 24 * 60 - 1, 20 * 60), vis = new FSlider(2, 150, 40),
                cover = new FSlider(0, 100, 0), bow = new FSlider(0, 100, 0);
        Runnable setTime = () -> {
            LocalDate dd = LocalDate.ofYearDay(today.getYear(), Math.min(today.lengthOfYear(), day.getValue() + 1));
            sky.epochMillis = epoch(dd, time.getValue() / 60.0);
        };
        day.addChangeListener(e -> setTime.run());
        time.addChangeListener(e -> { if (time.getValueIsAdjusting()) setTime.run(); });
        vis.addChangeListener(e -> sky.visibilityKm = vis.getValue());
        cover.addChangeListener(e -> sky.cloudCover = cover.getValue() / 100.0);
        bow.addChangeListener(e -> sky.rainbow = bow.getValue() / 100.0);
        FComboBox lapse = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "1 Minute je Sekunde", "10 Minuten je Sekunde"});
        lapse.setSelectedIndex(1);
        lapse.addActionListener(e -> scene.lapse = switch (lapse.getSelectedIndex()) { case 0 -> 0; case 2 -> 60; case 3 -> 600; default -> 1; });
        FCheckBox stars = new FCheckBox("Sterne"), planets = new FCheckBox("Planeten"), milky = new FCheckBox("Milchstraße"), figures = new FCheckBox("Sternbilder");
        stars.setSelected(true);
        planets.setSelected(true);
        milky.setSelected(true);
        stars.addActionListener(e -> sky.stars = stars.isSelected());
        planets.addActionListener(e -> sky.planets = planets.isSelected());
        milky.addActionListener(e -> sky.milkyWay = milky.isSelected());
        figures.addActionListener(e -> sky.figures = figures.isSelected());
        JPanel presets = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        presets.setOpaque(false);
        String[] pn = {"Morgen", "Mittag", "Abend", "Nacht"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            FButton b = new FButton(pn[i]);
            b.addActionListener(e -> {
                LocalDate dd = java.time.Instant.ofEpochMilli((long) sky.epochMillis).atZone(ZONE).toLocalDate();
                double hr = switch (k) {
                    case 0 -> crossing(dd, sky.latDeg, sky.lonDeg, true) + 0.05;
                    case 1 -> 13;
                    case 2 -> crossing(dd, sky.latDeg, sky.lonDeg, false) - 0.25;
                    default -> 23.5;
                };
                time.setValue((int) Math.round(hr * 60));
                setTime.run();
                Sky.State now = sky.updateNow();
                scene.yaw[0] = k == 3 ? 180 : now.sunAz;
                scene.pitch[0] = k == 3 ? 28 : k == 1 ? 18 : 8;
            });
            presets.add(b);
        }
        FButton flash = new FButton("Blitz");
        flash.addActionListener(e -> {
            double a = Math.toRadians(scene.yaw[0] + (Math.random() - 0.5) * 40);
            sky.flash(Math.sin(a) * 6000, 2500, -Math.cos(a) * 6000, 1, sky.time(), 0.5);
        });
        Component[] items = {title, state, presets, dayLbl, day, timeLbl, time, lapse, visLbl, vis, label("Bedeckung (Schleier bis zu den Wolken)", ink), cover,
                label("Regenbogen", ink), bow, stars, planets, milky, figures, flash, check};
        for (Component c : items) {
            if (c instanceof javax.swing.JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 10));
        // Inhalt in die Fläche des FFrame (nicht ins ContentPane, dort liegen Titelleiste und Rahmen); das Fenster
        // deckend machen, denn in einem durchscheinenden Fenster zeigt Windows den schweren GL-Canvas nicht
        FFrame f = new FFrame("com.dan.sky – Himmel");
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
            Sky.State st = sky.state();
            if (st == null) return;
            ZonedDateTime z = java.time.Instant.ofEpochMilli((long) sky.epochMillis).atZone(ZONE);
            dayLbl.setText(String.format(Locale.GERMAN, "Tag: %td.%tm.%tY", z, z, z));
            timeLbl.setText(String.format(Locale.GERMAN, "Uhrzeit: %tH:%tM", z, z));
            if (!time.getValueIsAdjusting()) time.setValue(z.getHour() * 60 + z.getMinute());
            visLbl.setText(String.format(Locale.GERMAN, "Sicht: %.0f km (%s)", st.visibilityKm, st.visibilityWord()));
            state.setText("<html><body style='width:280px'>" + st.describe().replace(" · ", "<br>") + "<br>Mond: " + st.moonPhaseName
                    + String.format(Locale.GERMAN, ", %.1f Tage alt", st.moonAgeDays) + "</body></html>");
            if (scene.selfCheck.isEmpty()) canvas.invoke(false, a -> { scene.check(a); return true; });
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

    /** Uhrzeit (Stunden, Ortszeit) von Sonnenauf- oder -untergang (Mittelpunkt an der Kimm) an einem Tag. */
    static double crossing(LocalDate day, double lat, double lon, boolean rise) {
        double prev = Double.NaN;
        for (double hr = 3; hr < 23; hr += 1 / 60.0) {
            double a = Sky.sunAltAz(epoch(day, hr), lat, lon)[0];
            if (!Double.isNaN(prev) && (rise ? prev < 0 && a >= 0 : prev > 0 && a <= 0)) return hr;
            prev = a;
        }
        return rise ? 7 : 18;
    }
}
