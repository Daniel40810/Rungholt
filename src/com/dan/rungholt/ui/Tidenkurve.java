package com.dan.rungholt.ui;

import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Site;
import com.dan.rungholt.wiring.Wiring;
import com.dan.tide.Tide;

import javax.swing.JComponent;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;

/**
 * Phase 16 (F36 A): die Tidenkurve über 25 Stunden im Block TIDE, eine Stunde zurück und 24 voraus: der Pegel am Rand
 * (Norderhever) aus den Partialtiden (aus der Datenbank, sonst aus dem Code) mit dem Windstau von jetzt, gestrichelt
 * ohne Windstau; dazu MThw und MTnw, die Hoch- und Niedrigwasser mit Uhrzeit und ein Strich für jetzt. Ein fester Pegel
 * von Hand ist eine waagrechte Linie.
 */
public final class Tidenkurve extends JComponent {
    private final Wiring wiring;
    private final SceneClock clock;
    static final Color KURVE = Theme.PRIEL, ASTRO = new Color(91, 167, 179, 110), MITTEL = new Color(120, 136, 140), JETZT = Theme.GOLD, TEXT = new Color(200, 210, 208),
            HAND = Theme.ZIEGEL;

    public Tidenkurve(Wiring wiring, SceneClock clock) {
        this.wiring = wiring;
        this.clock = clock;
        setOpaque(false);
        setToolTipText("Pegel am Rand des Watts über 25 h (eine Stunde zurück, 24 voraus); gestrichelt ohne Windstau");
    }

    @Override public Dimension getPreferredSize() { return new Dimension(230, 120); }
    @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, 120); }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int w = getWidth(), h = getHeight(), l = 30, rr = 6, o = 14, u = 18;
        Tide t = wiring.level.tide;
        double jetzt = clock.epochMillis() / 3_600_000.0, a = jetzt - 1, b = jetzt + 24, surge = t.surge, hand = wiring.level.hand;
        double lo = Math.min(Site.MTNW - 0.6, -2.6), hi = Math.max(Site.MTHW + 0.6, 2.4);
        double[] ys = new double[w];
        for (int x = 0; x < w - l - rr; x++) {
            double hh = a + (b - a) * x / (double) (w - l - rr - 1);
            ys[x] = t.astronomical(hh);
            hi = Math.max(hi, ys[x] + surge + 0.2);
            lo = Math.min(lo, ys[x] + surge - 0.2);
        }
        if (!Double.isNaN(hand)) { hi = Math.max(hi, hand + 0.2); lo = Math.min(lo, hand - 0.2); }
        final double fLo = lo, fHi = hi;
        java.util.function.DoubleUnaryOperator py = v -> o + (h - o - u) * (fHi - v) / (fHi - fLo);
        java.util.function.DoubleUnaryOperator px = hh -> l + (w - l - rr - 1) * (hh - a) / (b - a);
        g.setColor(new Color(21, 29, 33, 160));
        g.fillRoundRect(l - 2, o - 2, w - l - rr + 4, h - o - u + 4, 6, 6);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 10));
        // MThw, NHN 0, MTnw
        float[] dash = {3, 3};
        for (double[] m : new double[][]{{Site.MTHW}, {0}, {Site.MTNW}}) {
            int y = (int) Math.round(py.applyAsDouble(m[0]));
            g.setColor(MITTEL);
            g.setStroke(new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, dash, 0));
            g.drawLine(l, y, w - rr, y);
            g.setColor(TEXT);
            g.drawString(String.format(Locale.GERMAN, "%+.1f", m[0]), 2, y + 4);
        }
        // Stunden: alle 6 h ein Strich mit Uhrzeit
        ZonedDateTime z0 = Instant.ofEpochMilli((long) (a * 3_600_000)).atZone(SceneClock.ZONE);
        double erste = a + (60 - z0.getMinute()) / 60.0 - z0.getSecond() / 3600.0;
        for (double hh = erste; hh <= b; hh += 1) {
            int stunde = Instant.ofEpochMilli((long) Math.round(hh * 3_600_000)).atZone(SceneClock.ZONE).getHour();
            if (stunde % 6 != 0) continue;
            int x = (int) Math.round(px.applyAsDouble(hh));
            g.setColor(new Color(60, 74, 78));
            g.setStroke(new BasicStroke(1));
            g.drawLine(x, o, x, h - u);
            g.setColor(TEXT);
            String s = stunde + " h";
            g.drawString(s, x - g.getFontMetrics().stringWidth(s) / 2, h - 5);
        }
        // ohne Windstau gestrichelt, mit Windstau voll
        Path2D.Double astro = new Path2D.Double(), voll = new Path2D.Double();
        for (int x = 0; x < w - l - rr; x++) {
            double yA = py.applyAsDouble(ys[x]), yV = py.applyAsDouble(ys[x] + surge);
            if (x == 0) { astro.moveTo(l + x, yA); voll.moveTo(l + x, yV); } else { astro.lineTo(l + x, yA); voll.lineTo(l + x, yV); }
        }
        if (Math.abs(surge) > 0.02) {
            g.setColor(ASTRO);
            g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 1, new float[]{4, 3}, 0));
            g.draw(astro);
        }
        g.setColor(KURVE);
        g.setStroke(new BasicStroke(2f));
        g.draw(voll);
        if (!Double.isNaN(hand)) {
            int y = (int) Math.round(py.applyAsDouble(hand));
            g.setColor(HAND);
            g.setStroke(new BasicStroke(1.6f));
            g.drawLine(l, y, w - rr, y);
            g.drawString("von Hand", w - rr - 46, y - 3);
        }
        // Hoch- und Niedrigwasser mit Uhrzeit
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 10));
        for (Tide.Turn x : t.extremes(a, b)) {
            int cx = (int) Math.round(px.applyAsDouble(x.hours())), cy = (int) Math.round(py.applyAsDouble(x.level() + surge));
            g.setColor(KURVE);
            g.fillOval(cx - 3, cy - 3, 6, 6);
            g.setColor(TEXT);
            String s = ControlPanel.hm(x.hours() * 3_600_000.0);
            int sw = g.getFontMetrics().stringWidth(s);
            g.drawString(s, Math.max(l, Math.min(w - rr - sw, cx - sw / 2)), x.high() ? cy - 5 : cy + 13);
        }
        // jetzt
        int xn = (int) Math.round(px.applyAsDouble(jetzt));
        g.setColor(JETZT);
        g.setStroke(new BasicStroke(1.5f));
        g.drawLine(xn, o, xn, h - u);
        double now = Double.isNaN(hand) ? t.astronomical(jetzt) + surge : hand;
        g.fillOval(xn - 4, (int) Math.round(py.applyAsDouble(now)) - 4, 8, 8);
        g.dispose();
    }
}
