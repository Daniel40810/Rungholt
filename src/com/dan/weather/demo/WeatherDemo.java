package com.dan.weather.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
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
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demo der Wetterregie ohne Leuchtturm: der Verlauf des Szenarios als Kurven mit laufendem Zeiger, daneben die Werte
 * der Regie, Szenarien, Zeitraffer, ein Handregler für den Wind, „Regie weiter“ und „Regie läuft“.
 * <pre>
 * WeatherDemo                      Fenster
 * WeatherDemo --bild datei.png     Barometerkurve des Sturmtags als Bild (1800 × 1150)
 * </pre>
 */
public final class WeatherDemo {
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private WeatherDemo() { }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            BufferedImage img = chart(Scenario.stormDay(), 1800, 1150, Double.NaN);
            File f = new File(a[1]);
            if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(img, "png", f);
            System.out.println("Bild: " + f.getPath());
            return;
        }
        SwingUtilities.invokeLater(WeatherDemo::window);
    }

    /** Beginn des Szenarios am 16.10.2026 in Berlin. */
    static double start(Scenario s) {
        return LocalDate.of(2026, 10, 16).atStartOfDay(ZONE).toEpochSecond() + s.startHour() * 3600;
    }

    /** Das Szenario als Bild (alle 5 Minuten Szenenzeit ein Punkt). */
    public static BufferedImage chart(Scenario s, int w, int h, double now) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        WeatherChart.draw(g, 0, 0, w, h, s, Weather.run(s, start(s), s.hours(), 300, 1885), now, ZONE);
        g.dispose();
        return img;
    }

    /** Die nächsten Stunden der freien Regie als Szenario (für die Vorschau). */
    static Scenario preview(Weather w, double t, double hours) {
        List<Scenario.Step> l = new ArrayList<>();
        for (Weather.Segment s : w.segments(t, t + hours * 3600)) {
            double a = Math.max(t, s.t0()), b = Math.min(t + hours * 3600, s.t1());
            if (b - a > 60) l.add(Scenario.Step.of((b - a) / 3600, s.phase(), s.phase().label, ""));
        }
        var z = java.time.Instant.ofEpochSecond((long) t).atZone(ZONE);
        return new Scenario("Freie Regie", z.getHour() + z.getMinute() / 60.0, 600, l);
    }

    static void window() {
        Weather w = new Weather();
        Scenario[] cur = {Scenario.stormDay()};
        double[] t = {start(cur[0])}, lapse = {cur[0].lapse()};
        w.start(cur[0], t[0]);
        List<Weather.Snapshot>[] run = new List[]{Weather.run(cur[0], t[0], cur[0].hours(), 300, w.seed)};
        double[] runStart = {t[0]};

        JComponent plot = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                Scenario s = cur[0];
                WeatherChart.draw(g, 0, 0, getWidth(), getHeight(), s, run[0], t[0], ZONE);
            }
        };
        plot.setPreferredSize(new Dimension(1200, 800));

        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), muted = new Color(150, 165, 178), red = new Color(226, 67, 47);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = label("com.dan.weather", red), state = label(" ", ink), lapseLbl = label("", ink), windLbl = label("Wind von Hand: aus", ink);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FButton storm = new FButton("Ein Sturmtag"), calm = new FButton("Ruhige Tage"), free = new FButton("Freie Regie"), regie = new FButton("Regie weiter");
        Runnable rerun = () -> {
            runStart[0] = t[0];
            run[0] = Weather.run(cur[0], t[0], cur[0].hours(), 300, w.seed);
        };
        storm.addActionListener(e -> { cur[0] = Scenario.stormDay(); t[0] = start(cur[0]); lapse[0] = cur[0].lapse(); w.start(cur[0], t[0]); rerun.run(); });
        calm.addActionListener(e -> { cur[0] = Scenario.calmDays(); t[0] = start(cur[0]); lapse[0] = cur[0].lapse(); w.start(cur[0], t[0]); rerun.run(); });
        free.addActionListener(e -> {
            w.startFree(t[0], w.snapshot().phase());
            cur[0] = preview(w, t[0], 26);
            run[0] = Weather.run(cur[0], t[0], cur[0].hours(), 300, w.seed);
        });
        FSlider wind = new FSlider(0, 35, 5), speed = new FSlider(0, 600, 420);
        wind.addChangeListener(e -> { w.set(Weather.Var.WIND, wind.getValue()); windLbl.setText(f("Wind von Hand: %d m/s", wind.getValue())); });
        regie.addActionListener(e -> { w.release(); windLbl.setText("Wind von Hand: aus"); });
        speed.addChangeListener(e -> lapse[0] = speed.getValue());
        FCheckBox running = new FCheckBox("Regie läuft");
        running.setSelected(true);
        running.addActionListener(e -> w.running = running.isSelected());
        for (Component c : new Component[]{title, state, label("Szenario", muted), storm, calm, free, lapseLbl, speed, windLbl, wind, regie, running}) {
            if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(300, 800));
        long[] last = {System.nanoTime()};
        new Timer(50, e -> {
            long now = System.nanoTime();
            double dt = (now - last[0]) / 1e9;
            last[0] = now;
            t[0] += dt * lapse[0];
            Weather.Snapshot s = w.update(t[0], dt);
            if (t[0] > runStart[0] + cur[0].hours() * 3600) {
                cur[0] = preview(w, t[0], 26);
                rerun.run();
            }
            lapseLbl.setText(f("Zeitraffer %.0f : 1", lapse[0]));
            state.setText("<html><body style='width:250px'>" + describe(s, ZONE) + "</body></html>");
            plot.repaint();
        }).start();
        // FFrame zeichnet Titel und Rahmen selbst: der Inhalt gehört in getComponentPane(), nicht in getContentPane()
        FFrame fr = new FFrame("com.dan.weather – Wetterregie");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(plot, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        fr.setPreferredFrameSize(new Dimension(1500, 860));
        fr.setSize(1500, 860);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    /** Eine Beschreibung der Lage in Worten (auch für HUDs). */
    public static String describe(Weather.Snapshot s, ZoneId zone) {
        var z = java.time.Instant.ofEpochSecond((long) s.time()).atZone(zone);
        return f("%02d:%02d · %s%s<br>Bft %d (%.1f m/s, Böen bis %.0f) aus %03.0f°<br>%.1f/8 %s, hohe Schicht %.0f %%<br>Regen %.1f mm/h · Sicht %.0f km<br>"
                        + "%.1f hPa %s (%+.1f/3 h), Feld „%s“<br>%.1f °C, Taupunkt %.1f °C · Dünung %.1f m · Gewitter %d<br>nächste Lage: %s in %.1f h%s",
                z.getHour(), z.getMinute(), s.stepText(), s.running() ? "" : " (angehalten)", s.beaufort(), s.wind(), s.gustMax(), s.fromDeg(), s.oktas(),
                s.cloud().name().charAt(0) + s.cloud().name().substring(1).toLowerCase(Locale.ROOT), s.high() * 100, s.rain(), s.effectiveVisibilityKm(),
                s.pressure(), s.tendencyText(), s.tendency(), s.barometerWord(), s.temperature(), s.dewPoint(), s.swellHeight(), s.thunder(), s.next().label,
                s.nextInH(), anyOverride(s) ? "<br>Handregler aktiv" : "");
    }

    static boolean anyOverride(Weather.Snapshot s) {
        for (boolean b : s.overridden()) if (b) return true;
        return false;
    }

    static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }

    static String f(String fmt, Object... a) { return String.format(Locale.GERMAN, fmt, a); }
}
