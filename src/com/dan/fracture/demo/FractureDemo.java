package com.dan.fracture.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;

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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Vorführung von {@code com.dan.fracture} ohne Glacier-Code (Paketvertrag Punkt 3 und 10) mit den drei Fällen aus dem
 * Werkbuch: ein Pfeiler bricht unter Last, ein überhängender Block kippt ins Wasser, ein Fels rollt einen Hang hinab.
 * Ziehen kreist, das Rad ändert den Abstand. „Spannung zeigen“ färbt jede Zelle nach der Auslastung ihrer Flächen.
 * Ohne Fenster: {@code FractureDemo --bild datei.png [breite]} rechnet je Fall drei Augenblicke.
 */
public final class FractureDemo {
    private FractureDemo() { }

    public static void main(String[] args) throws Exception {
        if (args.length > 1 && args[0].equals("--bild")) {
            bild(new File(args[1]), args.length > 2 ? Integer.parseInt(args[2]) : 1920);
            return;
        }
        SwingUtilities.invokeLater(FractureDemo::open);
    }

    /** Je Fall: Zeitpunkte der drei Bilder in Sekunden nach dem Auslösen; das erste zeigt die Spannung. */
    static final double[][] SHOTS = {{0.93, 2.2, 5.0}, {22.6, 26.0, 31.0}, {1.6, 3.9, 7.5}};

    static void bild(File out, int width) throws Exception {
        int tw = width / 3, th = tw * 10 / 16;
        BufferedImage img = new BufferedImage(tw * 3, th * 3, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        FractureScene.Case[] cases = FractureScene.Case.values();
        for (int c = 0; c < cases.length; c++) {
            FractureScene s = new FractureScene();
            s.load(cases[c]);
            s.step(0.5);
            s.trigger();
            double t = 0;
            for (int k = 0; k < 3; k++) {
                s.showStress = k == 0 && cases[c] != FractureScene.Case.FELS;
                while (t < SHOTS[c][k] - 1e-9) { s.step(1 / 60.0); t += 1 / 60.0; }
                BufferedImage tile = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
                Graphics2D tg = tile.createGraphics();
                s.paint(tg, tw, th);
                tg.dispose();
                g.drawImage(tile, k * tw, c * th, null);
                g.setColor(new Color(11, 18, 25));
                g.drawRect(k * tw, c * th, tw - 1, th - 1);
            }
            System.out.println(cases[c].title + ": " + s.status());
        }
        g.dispose();
        ImageIO.write(img, "png", out);
        System.out.println("Bild: " + out.getPath());
    }

    private static void open() {
        FractureScene scene = new FractureScene();
        JPanel view = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                scene.paint((Graphics2D) g, getWidth(), getHeight());
            }
        };
        MouseAdapter m = new MouseAdapter() {
            int lx, ly;
            @Override public void mousePressed(MouseEvent e) { lx = e.getX(); ly = e.getY(); }
            @Override public void mouseDragged(MouseEvent e) {
                scene.yaw -= (e.getX() - lx) * 0.3;
                scene.pitch = Math.max(-5, Math.min(80, scene.pitch + (e.getY() - ly) * 0.2));
                lx = e.getX();
                ly = e.getY();
            }
            @Override public void mouseWheelMoved(java.awt.event.MouseWheelEvent e) {
                scene.dist = Math.max(4, Math.min(200, scene.dist * Math.pow(1.1, e.getPreciseWheelRotation())));
            }
        };
        view.addMouseListener(m);
        view.addMouseMotionListener(m);
        view.addMouseWheelListener(m);

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = new JLabel("com.dan.fracture · Bruch und Starrkörper");
        title.setForeground(new Color(226, 67, 47));
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        JPanel cases = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        cases.setOpaque(false);
        cases.setPreferredSize(new Dimension(320, 120));
        FButton action = new FButton(scene.current.action);
        for (FractureScene.Case c : FractureScene.Case.values()) {
            FButton b = new FButton(c.title);
            b.addActionListener(e -> { scene.load(c); action.setText(c.action); });
            cases.add(b);
        }
        action.addActionListener(e -> scene.trigger());
        FButton reset = new FButton("Neu");
        reset.addActionListener(e -> scene.load(scene.current));
        JLabel dropL = label("", ink), meltL = label("", ink), strL = label("", ink);
        FSlider drop = new FSlider(0, 120, 40), melt = new FSlider(1, 30, 6), str = new FSlider(20, 200, 100);
        Runnable txt = () -> {
            dropL.setText(String.format(Locale.GERMAN, "Fallhöhe des Blocks: %.1f m", drop.getValue() / 10.0));
            meltL.setText(String.format(Locale.GERMAN, "Unterspülung: %.1f m/s (Zeitraffer)", melt.getValue() / 10.0));
            strL.setText(String.format(Locale.GERMAN, "Festigkeit: %d %% (wirkt bei „Neu“)", str.getValue()));
        };
        drop.addChangeListener(e -> { scene.dropHeight = drop.getValue() / 10.0; txt.run(); });
        melt.addChangeListener(e -> { scene.meltSpeed = melt.getValue() / 10.0; txt.run(); });
        str.addChangeListener(e -> { scene.strengthPct = str.getValue(); txt.run(); });
        txt.run();
        FCheckBox stress = new FCheckBox("Spannung zeigen");
        stress.addActionListener(e -> scene.showStress = stress.isSelected());
        FCheckBox pause = new FCheckBox("Anhalten");
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        row.setOpaque(false);
        row.add(action);
        row.add(reset);
        JLabel state = label(" ", muted);
        List<Component> items = new ArrayList<>(List.of(title, cases, row, dropL, drop, meltL, melt, strL, str, stress, pause, state,
                label("<html><body style='width:290px'>Ziehen: kreisen · Rad: Abstand. Die Zellen brechen, wo die Spannung "
                        + "die Festigkeit übersteigt; was frei ist, fällt als Starrkörper.</body></html>", muted)));
        for (Component c : items) {
            if (c instanceof JComponent jc) jc.setAlignmentX(0);
            side.add(c);
            side.add(Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(350, 10));
        FFrame f = new FFrame("com.dan.fracture – Bruch und Starrkörper");
        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(bg);
        root.add(view, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        f.setPreferredFrameSize(new Dimension(1500, 880));
        f.setSize(1500, 880);
        f.setMinimumSize(new Dimension(900, 600));
        f.setBackground(bg);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
        new Timer(16, e -> {
            if (!pause.isSelected()) scene.step(1 / 60.0);
            view.repaint();
        }).start();
        new Timer(400, e -> state.setText("<html><body style='width:290px'>" + scene.status().replace(" · ", "<br>") + "</body></html>")).start();
    }

    private static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        return l;
    }
}
