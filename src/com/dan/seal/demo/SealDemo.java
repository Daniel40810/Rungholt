package com.dan.seal.demo;

import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.seal.Colony;
import com.dan.seal.Seal;
import com.dan.seal.SealBody;
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
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorschau des Packages {@code com.dan.seal}: eine Sandbank mit Priel ({@link DemoBank}) im Zeitraffer einer Tide mit
 * einer Kolonie; ein Wanderer läuft auf Knopfdruck auf die Bank zu. Regler für Zeitraffer, Zahl, Fluchtdistanz, Monat
 * (Heuler im Juni und Juli) und den Blick.
 * <p>
 * Ohne Fenster: {@code --bild datei.png} schreibt drei Bögen: die Haltungen von nah ({@code datei.png}), die Bank über
 * eine Tide mit Wanderer ({@code datei_tide.png}, gerendert) und dieselbe Tide als Karte von oben
 * ({@code datei_karte.png}).
 */
public final class SealDemo {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(91, 167, 179);

    /** Ein Wanderer: läuft mit 1,3 m/s von seinem Start zur Bank und zurück. */
    static final class Walker {
        double x, z, tx, tz, sx, sz;
        boolean on, back;

        void start(double fromX, double fromZ, double toX, double toZ) { x = sx = fromX; z = sz = fromZ; tx = toX; tz = toZ; on = true; back = false; }

        void step(double dt) {
            if (!on) return;
            double gx = back ? sx : tx, gz = back ? sz : tz, dx = gx - x, dz = gz - z, d = Math.hypot(dx, dz);
            if (d < 1) { if (back) on = false; else back = true; return; }
            double s = Math.min(d, 1.3 * dt);
            x += dx / d * s;
            z += dz / d * s;
        }
    }

    // ------------------------------------------------------------ Haltungen von Hand

    /** Instanzwerte eines Tiers in der Mitte der Bühne. */
    static float[] one(float[] pose, double x, double z, double headingDeg, double len, double wet, double tone, boolean pup, double ground, double water) {
        float[] o = new float[16];
        double y;
        if (Double.isNaN(water)) y = ground - SealBody.lowest(pose) * len - 0.01 * len;
        else y = water + 0.03 * len - SealBody.head(pose)[1] * len;
        o[0] = (float) x; o[1] = (float) y; o[2] = (float) z; o[3] = (float) len;
        o[4] = (float) Math.toRadians(headingDeg); o[5] = pose[SealBody.ROLL]; o[6] = (float) wet; o[7] = (float) (tone + (pup ? 2 : 0));
        o[8] = pose[SealBody.HEAD_LIFT]; o[9] = pose[SealBody.HEAD_YAW]; o[10] = pose[SealBody.TAIL_LIFT]; o[11] = pose[SealBody.ARCH];
        o[12] = pose[SealBody.TAIL_YAW]; o[13] = pose[SealBody.FORE_SWING]; o[14] = pose[SealBody.HIND_SPREAD]; o[15] = pose[SealBody.PITCH];
        return o;
    }

    static float[] pose(double... v) {
        float[] p = new float[SealBody.N];
        for (int i = 0; i < v.length; i += 2) p[(int) v[i]] = (float) v[i + 1];
        return p;
    }

    /** Die Haltungen des Bogens (wie {@code Colony.setGoal}). */
    static final String[] POSE_NAMES = {"Liegen", "Kopf heben", "Banane (auf der Seite)", "Robben, Schub", "Robben, Zug", "Im Wasser, Kopf über der Fläche", "Schwimmen", "Mutter und Heuler"};

    static float[][] poses() {
        double ph0 = 0.3, ph1 = 0.3 + Math.PI;
        return new float[][]{
                pose(SealBody.HEAD_LIFT, -0.03, SealBody.HEAD_YAW, 0.15, SealBody.TAIL_LIFT, 0.12, SealBody.ROLL, 0.12, SealBody.FORE_SWING, 0.1, SealBody.HIND_SPREAD, 0.1),
                pose(SealBody.HEAD_LIFT, 0.55, SealBody.HEAD_YAW, -0.35, SealBody.TAIL_LIFT, 0.18, SealBody.FORE_SWING, 0.05, SealBody.HIND_SPREAD, 0.15),
                pose(SealBody.HEAD_LIFT, 0.55, SealBody.HEAD_YAW, 0.1, SealBody.TAIL_LIFT, 0.75, SealBody.ROLL, 1.25, SealBody.FORE_SWING, -0.4, SealBody.HIND_SPREAD, 0.35),
                pose(SealBody.ARCH, 0.22 * Math.sin(ph0), SealBody.HEAD_LIFT, 0.38 + 0.12 * Math.sin(ph0 + 1.2), SealBody.TAIL_LIFT, 0.25 + 0.15 * Math.sin(ph0 + 2.5), SealBody.FORE_SWING, 0.6 * Math.sin(ph0 + 0.5), SealBody.HIND_SPREAD, 0.15),
                pose(SealBody.ARCH, 0.22 * Math.sin(ph1), SealBody.HEAD_LIFT, 0.38 + 0.12 * Math.sin(ph1 + 1.2), SealBody.TAIL_LIFT, 0.25 + 0.15 * Math.sin(ph1 + 2.5), SealBody.FORE_SWING, 0.6 * Math.sin(ph1 + 0.5), SealBody.HIND_SPREAD, 0.15),
                pose(SealBody.PITCH, 0.55, SealBody.HEAD_LIFT, 0.35, SealBody.HEAD_YAW, 0.3, SealBody.FORE_SWING, 0.2, SealBody.HIND_SPREAD, 0.25),
                pose(SealBody.PITCH, 0.12, SealBody.HEAD_LIFT, 0.25, SealBody.TAIL_YAW, 0.3, SealBody.FORE_SWING, 0.1, SealBody.HIND_SPREAD, 0.25),
                pose(SealBody.HEAD_LIFT, 0.2, SealBody.TAIL_LIFT, 0.12, SealBody.ROLL, -0.15, SealBody.FORE_SWING, 0.1, SealBody.HIND_SPREAD, 0.1)};
    }

    // ------------------------------------------------------------ ohne Fenster

    public static void main(String[] a) throws Exception {
        if (a.length >= 2 && a[0].equals("--bild")) { bild(new File(a[1])); System.exit(0); }
        SwingUtilities.invokeLater(() -> new SealDemo().open());
    }

    static GLOffscreenAutoDrawable offscreen(int w, int h) {
        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        return GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
    }

    static void bild(File out) throws Exception {
        File dir = out.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        posen(out);
        tide(new File(out.getPath().replace(".png", "_tide.png")), new File(out.getPath().replace(".png", "_karte.png")));
    }

    /** Die Haltungen von nah, je eine Kachel. */
    static void posen(File out) throws Exception {
        int w = 480, h = 300;
        DemoBank bank = new DemoBank();
        SealScene sc = new SealScene(bank);
        sc.flatGround = true;
        GLOffscreenAutoDrawable d = offscreen(w, h);
        d.addGLEventListener(sc);
        float[][] ps = poses();
        int cols = 4, rows = 2;
        BufferedImage sheet = new BufferedImage(cols * w, rows * h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        for (int k = 0; k < ps.length; k++) {
            boolean water = k == 5 || k == 6;
            sc.showWater = water;
            sc.waterLevel = water ? 0.0 : -99;
            List<float[]> l = new ArrayList<>();
            double hd = k == 3 || k == 4 || k == 6 ? 70 : 100;
            l.add(one(ps[k], 0, 0, hd, 1.6, k == 2 || k == 0 ? 0.3 : 1, 0.35, false, water ? -3 : 0, water ? 0.0 : Double.NaN));
            if (k == 7) l.add(one(pose(SealBody.HEAD_LIFT, 0.45, SealBody.TAIL_LIFT, 0.15, SealBody.FORE_SWING, 0.1, SealBody.HIND_SPREAD, 0.1), 0.35, 0.75, hd + 15, 0.9, 0.6, 0.7, true, 0, Double.NaN));
            float[] all = new float[l.size() * 16];
            for (int i = 0; i < l.size(); i++) System.arraycopy(l.get(i), 0, all, i * 16, 16);
            sc.manual = all;
            sc.manualN = l.size();
            sc.eye = k == 2 ? new double[]{-1.5, 0.9, 2.0} : new double[]{-1.2, water ? 0.55 : 0.85, 2.2};
            sc.look = new double[]{0.0, water ? 0.15 : 0.18, 0.0};
            sc.fovy = 34;
            sc.sunAzDeg = 210;
            sc.sunAltDeg = 38;
            d.display();
            final BufferedImage[] img = new BufferedImage[1];
            d.invoke(true, x -> { img[0] = sc.grab(x); return true; });
            int cx = (k % cols) * w, cy = (k / cols) * h;
            g.drawImage(img[0], cx, cy, null);
            label(g, POSE_NAMES[k], cx + 6, cy + 6);
        }
        g.dispose();
        ImageIO.write(sheet, "png", out);
        System.out.println("Bild: " + out.getPath());
        d.destroy();
    }

    static void label(Graphics2D g, String s, int x, int y) {
        int tw = g.getFontMetrics().stringWidth(s);
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(x, y, tw + 12, 22);
        g.setColor(Color.WHITE);
        g.drawString(s, x + 6, y + 16);
    }

    /** Eine Tide an der Bank, Wanderer ab 6,6 h nach Hochwasser: gerendert von schräg oben und als Karte. */
    static void tide(File out, File map) throws Exception {
        DemoBank bank = new DemoBank();
        Seal seal = Seal.harbourSeal();
        seal.seed = 7;
        Colony c = new Colony(seal);
        c.world = bank;
        c.banks.add(DemoBank.bank());
        c.month = 7;
        c.count = 24;
        c.sunAltDeg = 40;
        bank.t = 0;
        c.settle();
        Walker walker = new Walker();
        double[] hours = {2.5, 4.0, 5.0, 6.0, 6.85, 7.05, 7.4, 9.5, 11.0};
        int w = 640, h = 360;
        SealScene sc = new SealScene(bank);
        sc.colony = c;
        GLOffscreenAutoDrawable d = offscreen(w, h);
        d.addGLEventListener(sc);
        int cols = 3, rows = 3;
        BufferedImage sheet = new BufferedImage(cols * w, rows * h, BufferedImage.TYPE_INT_RGB);
        BufferedImage mapSheet = new BufferedImage(cols * 420, rows * 330, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics(), gm = mapSheet.createGraphics();
        for (Graphics2D x : new Graphics2D[]{g, gm}) {
            x.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            x.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            x.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
        }
        double t = 0;
        for (int k = 0; k < hours.length; k++) {
            double target = hours[k] * 3600;
            while (t < target) {
                double dt = Math.min(5, target - t);
                if (!walker.on && t >= 6.6 * 3600 && t < 6.7 * 3600) walker.start(-520, 40, 40, 0);
                walker.step(dt);
                c.disturbers.clear();
                if (walker.on) c.disturbers.add(new Colony.Disturber(walker.x, walker.z, 1));
                t += dt;
                bank.t = t;
                c.step(dt);
            }
            sc.eye = new double[]{-110, 45, 210};
            sc.look = new double[]{70, -1.5, 10};
            sc.fovy = 32;
            sc.sunAzDeg = 230;
            sc.sunAltDeg = 35;
            d.display();
            final BufferedImage[] img = new BufferedImage[1];
            d.invoke(true, x -> { img[0] = sc.grab(x); return true; });
            int cx = (k % cols) * w, cy = (k / cols) * h;
            g.drawImage(img[0], cx, cy, null);
            String lb = String.format(Locale.GERMAN, "%.1f h nach HW · Pegel %+.2f m · %s", hours[k], bank.level(), census(c)) + (walker.on ? String.format(Locale.GERMAN, " · Wanderer %.0f m", Math.hypot(walker.x - 60, walker.z)) : "");
            label(g, lb, cx + 6, cy + 6);
            map(gm, c, bank, walker, (k % cols) * 420, (k / cols) * 330, 420, 330, String.format(Locale.GERMAN, "%.1f h · %+.2f m", hours[k], bank.level()));
            System.out.println(lb);
        }
        g.dispose();
        gm.dispose();
        ImageIO.write(sheet, "png", out);
        ImageIO.write(mapSheet, "png", map);
        System.out.println("Bild: " + out.getPath() + ", " + map.getPath() + " · " + c.describe() + " · Fluchten " + c.flights + ", Spuren " + c.tracks);
        d.destroy();
    }

    static String census(Colony c) {
        int[] n = c.census();
        return String.format(Locale.GERMAN, "%d liegen, %d im Wasser, %d robben, %d flüchten", n[Colony.Activity.LIEGEN.ordinal()],
                n[Colony.Activity.SCHWIMMEN.ordinal()] + n[Colony.Activity.TAUCHEN.ordinal()], n[Colony.Activity.ANLANDEN.ordinal()] + n[Colony.Activity.UMLEGEN.ordinal()], n[Colony.Activity.FLUCHT.ordinal()]);
    }

    /** Die Bank von oben: Höhen, Wasser, Tiere nach Tätigkeit, Wanderer. */
    static void map(Graphics2D g, Colony c, DemoBank bank, Walker walker, int ox, int oy, int w, int h, String title) {
        double x0 = -200, z0 = -170, scale = w / 520.0;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        double lv = bank.level();
        for (int j = 0; j < h; j++)
            for (int i = 0; i < w; i++) {
                double x = x0 + i / scale, z = z0 + j / scale, gr = bank.ground(x, z);
                int rgb;
                if (gr < lv) { double dd = Math.min(1, (lv - gr) / 3); rgb = mix(0x5d8f88, 0x173a40, dd); }
                else { double e = Math.min(1, (gr - lv) / 2); rgb = mix(0x5a4a34, 0xb39a70, e); }
                img.setRGB(i, j, rgb);
            }
        g.drawImage(img, ox, oy, null);
        AffineTransform keep = g.getTransform();
        g.translate(ox, oy);
        for (Colony.Animal a : c.animals()) {
            double px = (a.x - x0) * scale, pz = (a.z - z0) * scale;
            Color col = switch (a.act) {
                case LIEGEN -> new Color(240, 230, 210);
                case SCHWIMMEN -> new Color(150, 220, 255);
                case TAUCHEN -> new Color(60, 120, 200);
                case ANLANDEN, UMLEGEN -> new Color(255, 200, 80);
                case FLUCHT -> new Color(255, 80, 60);
            };
            g.setColor(col);
            double r = a.length * scale * 0.5 + 1.5;
            g.fill(new Ellipse2D.Double(px - r, pz - r, 2 * r, 2 * r));
            g.setColor(Color.BLACK);
            g.drawLine((int) px, (int) pz, (int) (px + Math.sin(a.heading) * r * 1.6), (int) (pz - Math.cos(a.heading) * r * 1.6));
        }
        if (walker.on) {
            double px = (walker.x - x0) * scale, pz = (walker.z - z0) * scale;
            g.setColor(new Color(220, 40, 40));
            g.setStroke(new BasicStroke(2));
            g.drawLine((int) px - 5, (int) pz - 5, (int) px + 5, (int) pz + 5);
            g.drawLine((int) px - 5, (int) pz + 5, (int) px + 5, (int) pz - 5);
            g.setColor(new Color(220, 40, 40, 90));
            double fr = c.seal.flightDistance * scale;
            g.draw(new Ellipse2D.Double(px - fr, pz - fr, 2 * fr, 2 * fr));
        }
        g.setTransform(keep);
        label(g, title + " · " + census(c), ox + 4, oy + 4);
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), gg = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bb = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bb;
    }

    // ------------------------------------------------------------ Fenster

    final DemoBank bank = new DemoBank();
    final Seal seal = Seal.harbourSeal();
    final Colony colony = new Colony(seal);
    final Walker walker = new Walker();
    volatile double lapse = 120;
    private JPanel panel;
    private SealScene scene;

    private void open() {
        colony.world = bank;
        colony.banks.add(DemoBank.bank());
        bank.t = 4.5 * 3600;
        colony.settle();
        scene = new SealScene(bank) {
            long last = System.nanoTime();
            @Override public void display(GLAutoDrawable dr) {
                long now = System.nanoTime();
                double dt = Math.min(0.1, (now - last) / 1e9) * lapse;
                last = now;
                bank.t += dt;
                walker.step(dt);
                colony.disturbers.clear();
                if (walker.on) colony.disturbers.add(new Colony.Disturber(walker.x, walker.z, 1));
                colony.step(dt);
                super.display(dr);
            }
        };
        scene.colony = colony;
        view(0);
        GLCanvas canvas = new GLCanvas(new GLCapabilities(GLProfile.get(GLProfile.GL4)));
        canvas.addGLEventListener(scene);
        FFrame f = new FFrame("Seehunde · Vorschau des Packages com.dan.seal");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(canvas, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        f.setPreferredFrameSize(new java.awt.Dimension(1400, 840));
        f.setSize(1400, 840);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Animator(canvas).start();
    }

    static final String[] VIEWS = {"Bank von schräg oben", "Am Rand der Bank", "Von oben"};

    private void view(int i) {
        switch (i) {
            case 1 -> { scene.eye = new double[]{-60, 2.5, 150}; scene.look = new double[]{60, -1, 40}; scene.fovy = 30; }
            case 2 -> { scene.eye = new double[]{60, 420, 1}; scene.look = new double[]{60, 0, 0}; scene.fovy = 45; }
            default -> { scene.eye = new double[]{-110, 45, 210}; scene.look = new double[]{70, -1.5, 10}; scene.fovy = 32; }
        }
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel head = new JLabel("SEEHUNDE");
        head.setForeground(ACCENT);
        head.setFont(new Font("SansSerif", Font.BOLD, 13));
        add(head);
        FComboBox vb = new FComboBox(VIEWS);
        vb.addActionListener(e -> view(vb.getSelectedIndex()));
        add(vb);
        slider("Zeitraffer", 1, 600, 120, "×", v -> lapse = v);
        slider("Tiere", 0, 60, 36, "", v -> colony.count = v);
        slider("Fluchtdistanz", 50, 500, 300, " m", v -> seal.flightDistance = v);
        slider("Monat", 1, 12, 7, "", v -> colony.month = v);
        slider("Sonnenhöhe", -10, 60, 35, "°", v -> { scene.sunAltDeg = v; colony.sunAltDeg = v; });
        com.dan.fbutton.FButton go = new com.dan.fbutton.FButton("Wanderer zur Bank");
        go.addActionListener(e -> walker.start(-560, 60, 50, 0));
        add(go);
        JLabel info = new JLabel(" ");
        info.setForeground(MUTED);
        info.setFont(new Font("SansSerif", Font.PLAIN, 11));
        add(info);
        new javax.swing.Timer(250, e -> info.setText(String.format(Locale.GERMAN, "<html><body style='width:230px'>Pegel %+.2f m · %.1f h nach Hochwasser<br>%s<br>Fluchten %d · Spuren %d · Rufe %d<br>%s</body></html>",
                bank.level(), (bank.t % DemoBank.PERIOD) / 3600, colony.describe(), colony.flights, colony.tracks, colony.calls, seal.describe()))).start();
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    private void slider(String name, int min, int max, int val, String unit, java.util.function.IntConsumer set) {
        JLabel l = new JLabel(name + "  " + val + unit);
        l.setForeground(INK);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        add(l);
        FSlider s = new FSlider(min, max, val);
        s.addChangeListener(e -> { l.setText(name + "  " + s.getValue() + unit); set.accept(s.getValue()); });
        add(s);
    }

    private void add(Component c) {
        if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(c);
        panel.add(Box.createVerticalStrut(6));
    }
}
