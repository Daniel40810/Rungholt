package com.dan.rungholt.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Das Programmsymbol (F24 A): der Kirchturm von Rungholt, zur Hälfte im Watt versunken, davor ein Priel im
 * Abendlicht, Goldkante wie die anderen Projekte. Phase 1 malt es; ab Phase 17 liegen die PNGs im Jar unter
 * {@code com/dan/rungholt/ui/icon/rungholt_NN.png} und als {@code icon/rungholt.ico} für die Verknüpfung. Fehlt eine
 * Größe, malt die App sie selbst.
 */
public final class AppIcon {
    public static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcon() { }

    public static List<Image> images() {
        List<Image> l = new ArrayList<>();
        for (int s : SIZES) l.add(load(s));
        return l;
    }

    public static BufferedImage load(int size) {
        try (InputStream in = AppIcon.class.getResourceAsStream("icon/rungholt_" + size + ".png")) {
            if (in != null) {
                BufferedImage b = javax.imageio.ImageIO.read(in);
                if (b != null) return b;
            }
        } catch (java.io.IOException e) { /* gemaltes Symbol */ }
        return paint(size);
    }

    /** Das Symbol, in jeder Größe neu gemalt. */
    public static BufferedImage paint(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double r = s * 0.18, inset = Math.max(1, s * 0.035);
        // Goldkante
        g.setPaint(new GradientPaint(0, 0, new Color(240, 210, 120), s, s, new Color(150, 105, 30)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, r * 2, r * 2));
        RoundRectangle2D inner = new RoundRectangle2D.Double(inset, inset, s - 2 * inset, s - 2 * inset, r * 1.7, r * 1.7);
        // Abendhimmel über dem Watt
        double hor = s * 0.58;
        g.setPaint(new GradientPaint(0, 0, new Color(24, 44, 58), 0, (float) hor, new Color(214, 150, 84)));
        g.fill(inner);
        g.setClip(inner);
        // die tiefe Sonne
        g.setColor(new Color(250, 214, 140));
        g.fill(new java.awt.geom.Ellipse2D.Double(s * 0.66, hor - s * 0.10, s * 0.13, s * 0.13));
        // der Kirchturm, zur Hälfte im Watt: Schaft aus Backstein, Satteldach, Schallöffnung
        double tx = s * 0.26, tw = s * 0.20, ttop = s * 0.26;
        g.setPaint(new GradientPaint((float) tx, 0, new Color(150, 70, 46), (float) (tx + tw), 0, new Color(86, 38, 26)));
        g.fill(new Rectangle2D.Double(tx, ttop, tw, hor - ttop + s * 0.02));
        Path2D dach = new Path2D.Double();
        dach.moveTo(tx - s * 0.015, ttop);
        dach.lineTo(tx + tw / 2, ttop - s * 0.13);
        dach.lineTo(tx + tw + s * 0.015, ttop);
        dach.closePath();
        g.setColor(new Color(62, 30, 24));
        g.fill(dach);
        if (s >= 24) {
            g.setColor(new Color(30, 16, 14));
            double ow = tw * 0.22;
            g.fill(new RoundRectangle2D.Double(tx + tw * 0.25, ttop + s * 0.06, ow, s * 0.08, ow, ow));
            g.fill(new RoundRectangle2D.Double(tx + tw * 0.53, ttop + s * 0.06, ow, s * 0.08, ow, ow));
        }
        // das Watt: nasser Sand, der den Himmel spiegelt
        g.setPaint(new GradientPaint(0, (float) hor, new Color(126, 100, 70), 0, s, new Color(70, 56, 42)));
        g.fill(new Rectangle2D.Double(0, hor, s, s - hor));
        g.setColor(new Color(220, 160, 96, 90));
        g.fill(new Rectangle2D.Double(0, hor, s, s * 0.03));
        // der Priel im Abendlicht, von vorn links in die Ferne
        Path2D priel = new Path2D.Double();
        priel.moveTo(s * 0.10, s);
        priel.curveTo(s * 0.40, s * 0.86, s * 0.42, s * 0.70, s * 0.70, hor + s * 0.01);
        priel.lineTo(s * 0.78, hor + s * 0.01);
        priel.curveTo(s * 0.58, s * 0.72, s * 0.62, s * 0.88, s * 0.52, s);
        priel.closePath();
        g.setPaint(new GradientPaint(0, (float) hor, new Color(250, 206, 130), 0, s, new Color(70, 120, 128)));
        g.fill(priel);
        // Spiegelung des Turms im nassen Sand
        if (s >= 32) {
            g.setColor(new Color(60, 30, 22, 90));
            g.fill(new Rectangle2D.Double(tx, hor + s * 0.03, tw, s * 0.12));
            g.setStroke(new BasicStroke((float) Math.max(1, s * 0.012)));
            g.setColor(new Color(255, 240, 210, 70));
            for (int i = 0; i < 4; i++) {
                double y = hor + s * (0.10 + 0.07 * i);
                g.draw(new java.awt.geom.Line2D.Double(s * 0.02, y, s * (0.30 - 0.04 * i), y + s * 0.01));
            }
        }
        g.dispose();
        return im;
    }

    /** Setzt das Symbol am Fenster und, wo unterstützt, in der Taskleiste. */
    public static void install(java.awt.Window w) {
        List<Image> l = images();
        w.setIconImages(l);
        try {
            if (java.awt.Taskbar.isTaskbarSupported()) {
                java.awt.Taskbar tb = java.awt.Taskbar.getTaskbar();
                if (tb.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) tb.setIconImage(l.get(l.size() - 1));
            }
        } catch (Exception | Error ignored) {
            // Windows nimmt ohnehin die Fenstersymbole
        }
    }
}
