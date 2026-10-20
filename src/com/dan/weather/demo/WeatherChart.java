package com.dan.weather.demo;

import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Zeichnet den Verlauf eines Szenarios als Bild: oben die Lagen als Band, darunter vier Felder mit eigener Achse und
 * gemeinsamer Zeitachse (Luftdruck, Wind mit Böen, Regen mit Gewitter, Bedeckung und Sicht als Text). Eine Achse je
 * Feld, je Feld eine Farbe; Beschriftung in Textfarben. Der Zeiger {@code now} markiert die laufende Zeit.
 */
public final class WeatherChart {
    static final Color SURFACE = new Color(0xfcfcfb), INK = new Color(0x0b0b0b), INK2 = new Color(0x52514e), MUTED = new Color(0x8a8984),
            GRID = new Color(0xe6e5e1), BAND_A = new Color(0xf1f0ec), BAND_B = new Color(0xe7e6e1),
            PRESSURE = new Color(0x2a78d6), WIND = new Color(0x1baf7a), RAIN = new Color(0x6250d6), COVER = new Color(0x52514e), THUNDER = new Color(0xeda100);

    private WeatherChart() { }

    /** Zeichnet in g (x, y, w, h); now = Szenenzeit des Zeigers oder NaN. */
    public static void draw(Graphics2D g, int x0, int y0, int w, int h, Scenario s, List<Weather.Snapshot> run, double now, ZoneId zone) {
        draw(g, x0, y0, w, h, s, run, now, zone, null);
    }

    /** Wie oben, dazu nummerierte Marken an den Zeiten marks (etwa für Standbilder). */
    public static void draw(Graphics2D g, int x0, int y0, int w, int h, Scenario s, List<Weather.Snapshot> run, double now, ZoneId zone, double[] marks) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(SURFACE);
        g.fillRect(x0, y0, w, h);
        Font title = new Font(Font.SANS_SERIF, Font.BOLD, 18), lab = new Font(Font.SANS_SERIF, Font.PLAIN, 13), small = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        double t0 = run.get(0).time(), t1 = run.get(run.size() - 1).time();
        int left = x0 + 74, right = x0 + w - 90, top = y0 + 64;
        g.setColor(INK);
        g.setFont(title);
        g.drawString("„" + s.name() + "“ · Barometer und Wetter über " + Math.round(s.hours()) + " Stunden", x0 + 24, y0 + 34);
        g.setFont(small);
        g.setColor(INK2);
        g.drawString(String.format(Locale.GERMAN, "com.dan.weather · Zeitraffer %.0f : 1 = %.1f min · ein Punkt je 5 min Szenenzeit", s.lapse(), s.hours() * 60 / s.lapse()), x0 + 24, y0 + 54);

        // Lagen als Band
        int bandH = 34, by = top + 6;
        for (int i = 0; i < s.steps().size(); i++) {
            double a = t0 + s.stepStart(i) * 3600, b = a + s.steps().get(i).hours() * 3600;
            int xa = xi(a, t0, t1, left, right), xb = xi(b, t0, t1, left, right);
            g.setColor(i % 2 == 0 ? BAND_A : BAND_B);
            g.fillRect(xa, by, xb - xa - 2, bandH);
            g.setColor(INK);
            g.setFont(small);
            fitText(g, s.steps().get(i).text(), xa + 5, by + 14, xb - xa - 10);
            g.setColor(INK2);
            fitText(g, s.steps().get(i).phase().label, xa + 5, by + 28, xb - xa - 10);
        }
        // Felder
        int py = by + bandH + 34, gap = 40, fh = (y0 + h - 48 - py - 3 * gap) / 4;
        double pMin = 1e9, pMax = -1e9, wMax = 0, rMax = 0;
        for (Weather.Snapshot r : run) { pMin = Math.min(pMin, r.pressure()); pMax = Math.max(pMax, r.pressure()); wMax = Math.max(wMax, r.gustMax()); rMax = Math.max(rMax, r.rain()); }
        double pLo = Math.floor((pMin - 3) / 5) * 5, pHi = Math.ceil((pMax + 3) / 5) * 5;
        // 1 Luftdruck
        panel(g, left, py, right, fh, pLo, pHi, 10, "Luftdruck", "hPa", lab, small, t0, t1, zone, false);
        line(g, run, Weather.Snapshot::pressure, left, py, right, fh, pLo, pHi, t0, t1, PRESSURE, 2.2f);
        annotatePressure(g, s, run, left, py, right, fh, pLo, pHi, t0, t1, small);
        // 2 Wind mit Böen (Band bis zur stärksten Böe)
        int wy = py + fh + gap;
        double wHi = Math.ceil((wMax + 2) / 5) * 5;
        panel(g, left, wy, right, fh, 0, wHi, 5, "Wind", "m/s", lab, small, t0, t1, zone, false);
        band(g, run, Weather.Snapshot::wind, Weather.Snapshot::gustMax, left, wy, right, fh, 0, wHi, t0, t1, new Color(WIND.getRed(), WIND.getGreen(), WIND.getBlue(), 60));
        line(g, run, Weather.Snapshot::wind, left, wy, right, fh, 0, wHi, t0, t1, WIND, 2.2f);
        g.setFont(small);
        for (int b = 2; b <= 12; b += 2) {
            double v = Weather.beaufortMid(b);
            if (v > wHi) break;
            int yy = yi(v, 0, wHi, wy, fh);
            g.setColor(INK2);
            g.drawString("Bft " + b, right + 8, yy + 4);
        }
        g.setColor(INK2);
        g.drawString("Mittel (Linie), stärkste Böe (Band)", left + 8, wy + 14);
        // 3 Regen und Gewitter
        int ry = wy + fh + gap;
        double rHi = Math.max(5, Math.ceil(rMax / 5) * 5);
        panel(g, left, ry, right, fh, 0, rHi, 5, "Regen", "mm/h", lab, small, t0, t1, zone, false);
        area(g, run, Weather.Snapshot::rain, left, ry, right, fh, 0, rHi, t0, t1, new Color(RAIN.getRed(), RAIN.getGreen(), RAIN.getBlue(), 90), RAIN);
        double ta = Double.NaN;
        for (int i = 0; i <= run.size(); i++) {
            boolean on = i < run.size() && run.get(i).thunder() >= 2;
            if (on && Double.isNaN(ta)) ta = run.get(i).time();
            if (!on && !Double.isNaN(ta)) {
                int xa = xi(ta, t0, t1, left, right), xb = xi(run.get(i - 1).time(), t0, t1, left, right);
                g.setColor(THUNDER);
                g.fillRoundRect(xa, ry + 4, Math.max(4, xb - xa), 8, 4, 4);
                g.setColor(INK);
                g.drawString("Gewitter", xa, ry + 26);
                ta = Double.NaN;
            }
        }
        // 4 Bedeckung, dazu die Sicht als Text
        int cy = ry + fh + gap;
        panel(g, left, cy, right, fh, 0, 8, 2, "Bedeckung", "Achtel", lab, small, t0, t1, zone, true);
        line(g, run, Weather.Snapshot::oktas, left, cy, right, fh, 0, 8, t0, t1, COVER, 2f);
        g.setFont(small);
        for (int i = 0; i < s.steps().size(); i++) {
            double a = t0 + (s.stepStart(i) + s.steps().get(i).hours() * 0.5) * 3600;
            Weather.Snapshot r = at(run, a);
            int xa = xi(a, t0, t1, left, right);
            if (xi(s.steps().get(i).hours() * 3600 + t0, t0, t1, left, right) - left < 90) continue;
            g.setColor(INK2);
            String txt = String.format(Locale.GERMAN, "Sicht %.0f km", r.effectiveVisibilityKm());
            g.drawString(txt, xa - g.getFontMetrics().stringWidth(txt) / 2, cy + fh - 6);
        }
        if (marks != null)
            for (int i = 0; i < marks.length; i++) {
                int xm = xi(marks[i], t0, t1, left, right);
                g.setColor(INK2);
                g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{2, 3}, 0));
                g.drawLine(xm, by + bandH + 4, xm, cy + fh);
                g.setStroke(new BasicStroke(1f));
                g.setColor(INK);
                g.fillOval(xm - 10, by + bandH + 6, 20, 20);
                g.setColor(SURFACE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                String n = String.valueOf(i + 1);
                g.drawString(n, xm - g.getFontMetrics().stringWidth(n) / 2, by + bandH + 21);
            }
        if (!Double.isNaN(now) && now >= t0 && now <= t1) {
            int xn = xi(now, t0, t1, left, right);
            g.setColor(INK);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(xn, by - 4, xn, cy + fh);
        }
    }

    interface F { double of(Weather.Snapshot s); }

    static Weather.Snapshot at(List<Weather.Snapshot> run, double t) {
        Weather.Snapshot best = run.get(0);
        for (Weather.Snapshot r : run) if (Math.abs(r.time() - t) < Math.abs(best.time() - t)) best = r;
        return best;
    }

    static int xi(double t, double t0, double t1, int l, int r) { return (int) Math.round(l + (r - l) * (t - t0) / (t1 - t0)); }

    static int yi(double v, double lo, double hi, int y, int h) { return (int) Math.round(y + h - h * (v - lo) / (hi - lo)); }

    static void fitText(Graphics2D g, String t, int x, int y, int w) {
        FontMetrics fm = g.getFontMetrics();
        String s = t;
        while (s.length() > 1 && fm.stringWidth(s) > w) s = s.substring(0, s.length() - 2) + "…";
        if (fm.stringWidth(s) <= w) g.drawString(s, x, y);
    }

    static void panel(Graphics2D g, int l, int y, int r, int h, double lo, double hi, double step, String name, String unit, Font lab, Font small,
                      double t0, double t1, ZoneId zone, boolean timeAxis) {
        g.setStroke(new BasicStroke(1f));
        g.setFont(small);
        for (double v = lo; v <= hi + 1e-9; v += step) {
            int yy = yi(v, lo, hi, y, h);
            g.setColor(GRID);
            g.drawLine(l, yy, r, yy);
            g.setColor(INK2);
            String t = String.format(Locale.GERMAN, "%.0f", v);
            g.drawString(t, l - 8 - g.getFontMetrics().stringWidth(t), yy + 4);
        }
        // Stunden
        long h0 = (long) Math.ceil(t0 / 10800) * 10800;
        for (long t = h0; t <= t1; t += 10800) {
            int xx = xi(t, t0, t1, l, r);
            g.setColor(GRID);
            g.drawLine(xx, y, xx, y + h);
            if (timeAxis) {
                var z = Instant.ofEpochSecond(t).atZone(zone);
                String s = String.format("%02d:00", z.getHour());
                g.setColor(INK2);
                g.drawString(s, xx - g.getFontMetrics().stringWidth(s) / 2, y + h + 16);
            }
        }
        g.setColor(INK);
        g.setFont(lab);
        g.drawString(name + " (" + unit + ")", l - 66, y - 12);
    }

    static void line(Graphics2D g, List<Weather.Snapshot> run, F f, int l, int y, int r, int h, double lo, double hi, double t0, double t1, Color c, float w) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < run.size(); i++) {
            double xx = l + (r - l) * (run.get(i).time() - t0) / (t1 - t0), yy = y + h - h * (f.of(run.get(i)) - lo) / (hi - lo);
            if (i == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
        }
        g.setColor(c);
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(p);
    }

    static void band(Graphics2D g, List<Weather.Snapshot> run, F a, F b, int l, int y, int r, int h, double lo, double hi, double t0, double t1, Color c) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < run.size(); i++) {
            double xx = l + (r - l) * (run.get(i).time() - t0) / (t1 - t0), yy = y + h - h * (b.of(run.get(i)) - lo) / (hi - lo);
            if (i == 0) p.moveTo(xx, yy); else p.lineTo(xx, yy);
        }
        for (int i = run.size() - 1; i >= 0; i--) {
            double xx = l + (r - l) * (run.get(i).time() - t0) / (t1 - t0), yy = y + h - h * (a.of(run.get(i)) - lo) / (hi - lo);
            p.lineTo(xx, yy);
        }
        p.closePath();
        g.setColor(c);
        g.fill(p);
    }

    static void area(Graphics2D g, List<Weather.Snapshot> run, F f, int l, int y, int r, int h, double lo, double hi, double t0, double t1, Color fill, Color edge) {
        Path2D p = new Path2D.Double();
        p.moveTo(l, y + h);
        for (Weather.Snapshot s : run) p.lineTo(l + (r - l) * (s.time() - t0) / (t1 - t0), y + h - h * (Math.min(hi, f.of(s)) - lo) / (hi - lo));
        p.lineTo(r, y + h);
        p.closePath();
        g.setColor(fill);
        g.fill(p);
        line(g, run, f, l, y, r, h, lo, hi, t0, t1, edge, 1.6f);
    }

    /** Pfeil: wie weit das Barometer in den 6 Stunden vor dem Sturm (erstmals Bft 9) gefallen ist. */
    static void annotatePressure(Graphics2D g, Scenario s, List<Weather.Snapshot> run, int l, int y, int r, int h, double lo, double hi, double t0, double t1, Font small) {
        Weather.Snapshot storm = null;
        for (Weather.Snapshot x : run) if (x.beaufort() >= 9) { storm = x; break; }
        if (storm == null) return;
        Weather.Snapshot before = at(run, storm.time() - 6 * 3600);
        int xs = xi(storm.time(), t0, t1, l, r), xb = xi(before.time(), t0, t1, l, r);
        g.setColor(INK2);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1, new float[]{4, 4}, 0));
        g.drawLine(xs, y, xs, y + h);
        g.drawLine(xb, y, xb, y + h);
        g.setFont(small);
        g.setColor(INK);
        String t = String.format(Locale.GERMAN, "−%.0f hPa in den 6 h, bevor Bft 9 erreicht ist", before.pressure() - storm.pressure());
        g.drawString(t, Math.max(l + 4, xb + 6), y + 14);
    }
}
