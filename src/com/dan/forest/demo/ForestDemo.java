package com.dan.forest.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.forest.Forest;
import com.dan.forest.ForestPlanter;
import com.dan.forest.Ground;
import com.dan.forest.Species;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;

/**
 * Vorschau des Wald-Pakets: ein einzelner Baum oder ein Wald, im Wind, durchs Jahr. Maus ziehen dreht
 * die Kamera, rechts ziehen verschiebt, das Rad zoomt. Rechts das Bedienfeld (FStyle): Ansicht und
 * Art, Tag im Jahr, Wind, Böen, Richtung, Schnee, Detailstufe, neuer Startwert.
 */
public final class ForestDemo {
    static final Color BG = new Color(14, 20, 24), INK = new Color(226, 230, 226), MUTED = new Color(142, 154, 156), ACCENT = new Color(226, 190, 72);
    static final String[] VIEWS = {"Einzelbaum", "Wald · Yellowstone", "Wald · Mitteleuropa", "Nadelwald · Yellowstone", "Bergwald · Alpen"};
    static final String[] MONTHS = {"Jan.", "Feb.", "März", "Apr.", "Mai", "Juni", "Juli", "Aug.", "Sep.", "Okt.", "Nov.", "Dez."};
    static final int[] MONTH_START = {1, 32, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335};

    private final Forest forest = new Forest(Ground.FLAT);
    private final SoftRenderer renderer = new SoftRenderer();
    private final View view = new View();
    private volatile int mode = 0, speciesIdx = 1, day = 275, lodChoice = 0;
    private volatile long seed = 1;
    private volatile float snow;
    private volatile boolean rebuild = true;
    private volatile double yaw = 0.6, pitch = 0.12, dist = 32, tx = 0, ty = 9, tz = 0;
    private volatile String status = "";
    private final JLabel statusLbl = new JLabel(" ");

    /**
     * Startet die Vorschau; wahlweise mit Ansicht (0 Einzelbaum, 1 Wald Yellowstone, 2 Wald
     * Mitteleuropa, 3 Nadelwald Yellowstone, 4 Bergwald Alpen), Art (0..13, siehe {@link Species#ALL}) und
     * Tag im Jahr, etwa {@code 1 0 280}.
     */
    public static void main(String[] a) {
        ForestDemo d = new ForestDemo();
        if (a.length > 0) d.startMode = Integer.parseInt(a[0]);
        if (a.length > 1) d.speciesIdx = Integer.parseInt(a[1]);
        if (a.length > 2) d.day = Integer.parseInt(a[2]);
        SwingUtilities.invokeLater(d::open);
    }

    private int startMode;

    private void open() {
        FFrame f = new FFrame("Wald · Vorschau des Pakets com.dan.forest");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(BG);
        root.add(view, BorderLayout.CENTER);
        root.add(controls(), BorderLayout.EAST);
        statusLbl.setForeground(MUTED);
        statusLbl.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLbl.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(new Color(8, 12, 15));
        south.add(statusLbl);
        root.add(south, BorderLayout.SOUTH);
        f.setPreferredFrameSize(new Dimension(1400, 860));
        f.setSize(1400, 860);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        Thread t = new Thread(this::loop, "Wald-Vorschau");
        t.setDaemon(true);
        t.start();
        new javax.swing.Timer(250, e -> statusLbl.setText(status)).start();
    }

    // ------------------------------------------------------------ Bedienfeld

    private JPanel panel;
    private final FComboBox lodBox = new FComboBox(new Object[]{"Stufe nach Entfernung", "Stufe 0 · alles", "Stufe 1 · mittel", "Stufe 2 · Laubballen", "Stufe 3 · Silhouette"});
    {
        lodBox.setSelectedIndex(2);
        lodBox.addActionListener(e -> lodChoice = lodBox.getSelectedIndex());
    }

    private JComponent controls() {
        panel = new JPanel();
        panel.setBackground(BG);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        head("ANSICHT");
        FComboBox v = new FComboBox(VIEWS);
        mode = startMode;
        v.setSelectedIndex(startMode);
        lodBox.setSelectedIndex(startMode == 0 ? 2 : 0);
        resetCamera();
        v.addActionListener(e -> { mode = v.getSelectedIndex(); lodBox.setSelectedIndex(mode == 0 ? 2 : 0); rebuild = true; resetCamera(); });
        add(v);
        Object[] names = new Object[Species.ALL.size()];
        for (int i = 0; i < names.length; i++) names[i] = Species.ALL.get(i).get().toString();
        FComboBox sp = new FComboBox(names);
        sp.setSelectedIndex(speciesIdx);
        sp.addActionListener(e -> { speciesIdx = sp.getSelectedIndex(); rebuild = true; resetCamera(); });
        add(sp);
        FButton again = new FButton("Neuer Startwert");
        again.addActionListener(e -> { seed++; rebuild = true; });
        add(again);
        note("Einzelbaum zeigt die gewählte Art. Die Wälder mischen die Arten in Gruppen, mit jungen Bäumen am Rand.");

        gap();
        head("JAHR");
        JLabel dayLbl = label(dateLabel(day));
        FSlider d = new FSlider(1, 365, day);
        dayLbl.setText(dateLabel(day));
        d.addChangeListener(e -> { day = d.getValue(); dayLbl.setText(dateLabel(day)); });
        add(d);
        JLabel snowLbl = label("Schnee  0 %");
        FSlider sn = new FSlider(0, 100, 0);
        sn.addChangeListener(e -> { snow = sn.getValue() / 100f; snowLbl.setText("Schnee  " + sn.getValue() + " %"); });
        add(sn);
        note("Laubbäume treiben aus, färben sich und werfen ihr Laub ab; Nadelbäume bleiben grün und werden im Winter dunkler.");

        gap();
        head("WIND");
        JLabel wl = label("Wind  4 m/s");
        FSlider ws = new FSlider(0, 20, 4);
        ws.addChangeListener(e -> { forest.wind.speed = ws.getValue(); wl.setText("Wind  " + ws.getValue() + " m/s"); });
        add(ws);
        JLabel gl = label("Böen  50 %");
        FSlider gs = new FSlider(0, 100, 50);
        gs.addChangeListener(e -> { forest.wind.gustiness = gs.getValue() / 100f; gl.setText("Böen  " + gs.getValue() + " %"); });
        add(gs);
        JLabel dl = label("Richtung  30°");
        FSlider dir = new FSlider(0, 359, 30);
        dir.addChangeListener(e -> { forest.wind.direction = dir.getValue(); dl.setText("Richtung  " + dir.getValue() + "°"); });
        add(dir);
        note("Der Stamm neigt sich und pendelt langsam, die Äste schwingen schneller, die Blätter flattern; Böen laufen als Wellen durch den Wald und reißen im Herbst Laub ab.");

        gap();
        head("BILD");
        add(lodBox);
        FCheckBox half = new FCheckBox("Halbe Auflösung (schneller)");
        half.setSelected(true);
        half.setTextColor(INK);
        half.addActionListener(e -> view.half = half.isSelected());
        add(half);
        note("Ziehen: drehen · rechts ziehen: verschieben · Rad: näher und weiter.");
        panel.add(Box.createVerticalGlue());
        javax.swing.JScrollPane sc = new javax.swing.JScrollPane(panel, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sc.setBorder(BorderFactory.createEmptyBorder());
        sc.getViewport().setBackground(BG);
        sc.setPreferredSize(new Dimension(320, 600));
        lodChoice = lodBox.getSelectedIndex();
        return sc;
    }

    private void head(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.BOLD, 11));
        l.setForeground(ACCENT);
        add(l);
    }

    private JLabel label(String s) {
        JLabel l = new JLabel(s);
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        l.setForeground(INK);
        add(l);
        return l;
    }

    private void note(String s) {
        JLabel l = new JLabel("<html><body style='width:190px'>" + s + "</body></html>");
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        l.setForeground(MUTED);
        add(l);
    }


    private void gap() { panel.add(Box.createVerticalStrut(14)); }

    private void add(Component c) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.add(c);
        p.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height + 4));
        c.setPreferredSize(new Dimension(250, c.getPreferredSize().height));
        panel.add(p);
    }

    static String dateLabel(int d) {
        int m = 11;
        while (m > 0 && d < MONTH_START[m]) m--;
        return "Tag  " + (d - MONTH_START[m] + 1) + ". " + MONTHS[m];
    }

    private void resetCamera() {
        if (mode == 0) { yaw = 0.6; pitch = 0.12; dist = 32; tx = 0; ty = 9; tz = 0; }
        else { yaw = 0.3; pitch = 0.05; dist = 14; tx = 0; ty = 4; tz = 0; }
    }

    // ------------------------------------------------------------ Bildschleife

    private void build() {
        forest.clear();
        forest.species.clear();
        if (mode == 0) {
            Species s = Species.ALL.get(speciesIdx).get();
            forest.variants = 1;
            forest.addSpecies(s);
            forest.prepare();
            forest.place(0, 0, 0, 0, 0, 1);
        } else {
            forest.variants = 4;
            java.util.List<Species> list = mode == 1 ? java.util.List.of(Species.lodgepolePine(), Species.aspen(), Species.douglasFir())
                    : mode == 2 ? java.util.List.of(Species.oak(), Species.beech(), Species.birch(), Species.spruce())
                    : mode == 3 ? java.util.List.of(Species.lodgepolePine(), Species.engelmannSpruce(), Species.subalpineFir(), Species.douglasFir(), Species.whitebarkPine())
                    : java.util.List.of(Species.spruce(), Species.silverFir(), Species.larch(), Species.stonePine(), Species.scotsPine());
            int[] idx = new int[list.size()];
            for (int i = 0; i < idx.length; i++) idx[i] = forest.addSpecies(list.get(i));
            forest.prepare();
            float[] w = mode == 1 ? new float[]{3, 1.2f, 0.8f} : mode == 2 ? new float[]{1, 1.3f, 0.8f, 1}
                    : mode == 3 ? new float[]{2.5f, 1.2f, 1.2f, 0.8f, 0.4f} : new float[]{1.5f, 1, 1, 0.7f, 0.7f};
            // Lichtung um die Kamera, nach außen dichter
            ForestPlanter.plant(forest, -110, -110, 110, 110, mode == 2 ? 8f : mode == 4 ? 6.5f : 5.5f,
                    (x, z) -> (float) Math.min(1, Math.max(0, (Math.hypot(x, z) - (mode >= 3 ? 14 : 8)) / 30)), idx, w, 35, Ground.FLAT, seed);
        }
    }

    private void loop() {
        long last = System.nanoTime();
        double t = 0;
        double fpsAcc = 0;
        int frames = 0;
        double fps = 0;
        while (true) {
            long now = System.nanoTime();
            float dt = (float) Math.min(0.2, (now - last) / 1e9);
            last = now;
            t += dt;
            if (rebuild) { rebuild = false; build(); }
            forest.forceLod = lodChoice == 0 ? -1 : lodChoice - 1;
            forest.setSeason(day, snow);
            double cy = Math.max(0.5, ty + dist * Math.sin(pitch)), cx = tx + dist * Math.cos(pitch) * Math.sin(yaw), cz = tz + dist * Math.cos(pitch) * Math.cos(yaw);
            long u0 = System.nanoTime();
            forest.update(t, dt, cx, cy, cz);
            double uMs = (System.nanoTime() - u0) / 1e6;
            int W = Math.max(64, view.getWidth()), H = Math.max(48, view.getHeight());
            if (view.half) { W /= 2; H /= 2; }
            renderer.setSize(W, H);
            renderer.camera(cx, cy, cz, tx, ty, tz, 55);
            // Sonne und Himmel nach der Jahreszeit (Winter tiefer)
            double el = Math.toRadians(25 + 30 * Math.cos(2 * Math.PI * (day - 172) / 365.0));
            renderer.sunX = (float) (Math.cos(el) * 0.6); renderer.sunY = (float) Math.sin(el); renderer.sunZ = (float) (Math.cos(el) * 0.8);
            renderer.begin();
            float[] grass = day < 100 || day > 320 ? new float[]{0.35f, 0.36f, 0.38f} : new float[]{0.09f, 0.10f, 0.04f};
            if (snow > 0.3f) grass = new float[]{0.6f, 0.62f, 0.65f};
            renderer.ground(tx, tz, 260, 64, null, grass);
            // Bäume parallel aufbereiten: erst Platz für alle Dreiecke, dann je Baum ein Auftrag
            java.util.List<Object[]> items = new java.util.ArrayList<>();
            int[] total = {0};
            forest.visit((m, p, n, c, tr) -> { items.add(new Object[]{m, p, n, c, tr}); total[0] += m.nt; });
            renderer.reserve(total[0]);
            items.parallelStream().forEach(o -> {
                com.dan.forest.TreeMesh m = (com.dan.forest.TreeMesh) o[0];
                com.dan.forest.TreeInstance tr = (com.dan.forest.TreeInstance) o[4];
                float h = m.model.height;
                renderer.mesh((float[]) o[1], (float[]) o[2], (float[]) o[3], m.tri, m.doubleSided, m.nt, m.nv, tr.x, tr.y, tr.z, tr.yaw, tr.scale,
                        h / 2, Math.max(h / 2, m.model.crownRadius) * 1.15f);
            });
            int nl = forest.leaves.n;
            float[] q = new float[12 * nl], rgb = new float[3 * nl], nr = new float[3 * nl];
            renderer.quads(q, rgb, nr, forest.leaves.quads(q, rgb, nr));
            long r0 = System.nanoTime();
            BufferedImage img = renderer.end();
            double rMs = (System.nanoTime() - r0) / 1e6;
            view.show(img);
            frames++;
            fpsAcc += dt;
            if (fpsAcc > 0.5) { fps = frames / fpsAcc; frames = 0; fpsAcc = 0; }
            int[] lc = forest.lodCounts();
            status = String.format(java.util.Locale.GERMANY, "%s  ·  %d Bäume (Stufen %d/%d/%d/%d)  ·  %,d Dreiecke  ·  %d Blätter in der Luft, %d am Boden  ·  Wind %.0f m/s  ·  %.1f Bilder/s (Wald %.0f ms, Bild %.0f ms)  ·  %d × %d",
                    VIEWS[mode], forest.trees.size(), lc[0], lc[1], lc[2], lc[3], renderer.triangles(), forest.leaves.flying(), forest.leaves.n - forest.leaves.flying(),
                    forest.wind.speed, fps, uMs, rMs, W, H);
            long spent = (System.nanoTime() - now) / 1_000_000L;
            try { Thread.sleep(Math.max(1, 16 - spent)); } catch (InterruptedException e) { return; }
        }
    }

    /** Die Bildfläche mit Mauskamera. */
    private final class View extends JPanel {
        volatile boolean half = true;
        private volatile BufferedImage img;
        private int lx, ly;

        View() {
            setBackground(Color.BLACK);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }

                @Override public void mouseDragged(MouseEvent e) {
                    int dx = e.getX() - lx, dy = e.getY() - ly;
                    lx = e.getX(); ly = e.getY();
                    if (SwingUtilities.isRightMouseButton(e)) {
                        double s = dist * 0.002;
                        tx -= (Math.cos(yaw) * dx) * s; tz += (Math.sin(yaw) * dx) * s; ty = Math.max(0.5, ty + dy * s);
                    } else {
                        yaw -= dx * 0.006;
                        pitch = Math.max(-0.2, Math.min(1.4, pitch + dy * 0.005));
                    }
                }

                @Override public void mouseWheelMoved(MouseWheelEvent e) { dist = Math.max(2, Math.min(400, dist * Math.pow(1.12, e.getPreciseWheelRotation()))); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addMouseWheelListener(ma);
        }

        void show(BufferedImage i) {
            img = i;
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            super.paintComponent(g0);
            BufferedImage i = img;
            if (i == null) return;
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(i, 0, 0, getWidth(), getHeight(), null);
        }
    }
}
