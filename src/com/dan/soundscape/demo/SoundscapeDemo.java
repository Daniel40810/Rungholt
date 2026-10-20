package com.dan.soundscape.demo;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcombobox.FComboBox;
import com.dan.fframe.FFrame;
import com.dan.fslider.FSlider;
import com.dan.soundscape.Aeolian;
import com.dan.soundscape.Blaze;
import com.dan.soundscape.Crowd;
import com.dan.soundscape.Workshop;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Hum;
import com.dan.soundscape.Room;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Wav;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Demo des Klangs ohne Leuchtturm-App: ein gedachter Turm mit Galerie (Handlauf, Geländerstäbe, ein Seil), See,
 * Wetter nach Beaufort, das Ohr draußen oder in einem Raum, Knöpfe für Ereignisse. Spielt über den Lautsprecher und
 * zeigt das laufende Spektrogramm.
 * <pre>
 * SoundscapeDemo                          Fenster mit Ton
 * SoundscapeDemo --bild datei.png [breite] acht Hörszenen als Spektrogramme mit Pegeln
 * SoundscapeDemo --wav ordner             dieselben acht Hörszenen als WAV (je 15 s)
 * </pre>
 */
public final class SoundscapeDemo {
    private SoundscapeDemo() { }

    public static void main(String[] a) throws Exception {
        if (a.length > 1 && a[0].equals("--bild")) {
            int w = a.length > 2 ? Integer.parseInt(a[2]) : 1800;
            File f = new File(a[1]);
            if (f.getAbsoluteFile().getParentFile() != null) f.getAbsoluteFile().getParentFile().mkdirs();
            ImageIO.write(sheet(scenes(), w), "png", f);
            System.out.println("Bild: " + f.getPath());
            return;
        }
        if (a.length > 1 && a[0].equals("--wav")) {
            File dir = new File(a[1]);
            for (Scene s : scenes()) {
                File f = new File(dir, s.file + ".wav");
                Wav.write(f, s.audio, s.sampleRate);
                System.out.println("WAV: " + f.getPath() + " · " + s.levels);
            }
            return;
        }
        SwingUtilities.invokeLater(SoundscapeDemo::window);
    }

    // ------------------------------------------------------------------ der gedachte Turm

    /** Galerie in 23 m, Ohr am Handlauf auf der Seite, von der der Wind kommt. */
    static void tower(Soundscape s) {
        s.bodies.clear();
        s.bodies.add(new Aeolian("Handlauf", 0, 24.1, -4.2, 0.07, 26));
        s.bodies.add(new Aeolian("Geländerstäbe", 0, 23.6, -4.3, 0.025, 60));
        s.bodies.add(new Aeolian("Seil", 0, 27, -1.5, 0.008, 12));
        s.surfX = 0;
        s.surfZ = 0;
        s.surfRadius = 5.5;
        s.earX = 0;
        s.earY = 24.6;
        s.earZ = -3.9;
        s.earYawDeg = 0;
        s.windFromDeg = 0;
    }

    /** Eine Hörszene: Text, Dateiname, Ton, Pegel. */
    record Scene(String title, String file, float[] audio, int sampleRate, String levels) { }

    /** Acht Hörszenen zu je 15 s, gerechnet ohne Lautsprecher (seit Haithabu Phase 14 dazu zwei in einer Siedlung). */
    static List<Scene> scenes() {
        List<Scene> l = new ArrayList<>();
        double len = 15;
        // 1 ruhige See, Möwen
        Soundscape s = Soundscape.beaufort(2);
        tower(s);
        l.add(scene("Bft 2 auf der Galerie: leise See, Möwen", "1_Bft2_Galerie_Moewen", s, len, (t, x) -> {
            if (t == 2 || t == 6 || t == 9 || t == 12) x.event(Soundscape.Kind.MOEWE, 15 * Math.sin(t), 26, -20 - 10 * Math.cos(t), 1);
        }));
        // 2 frische Brise
        s = Soundscape.beaufort(5);
        tower(s);
        l.add(scene("Bft 5 auf der Galerie: Wind, Brandung, Äolstöne", "2_Bft5_Galerie", s, len, (t, x) -> {
            if (t == 4) x.event(Soundscape.Kind.MOEWE, -20, 30, -10, 1);
        }));
        // 3 Sturm
        s = Soundscape.beaufort(9);
        tower(s);
        l.add(scene("Bft 9 auf der Galerie: Sturm, Regen, Brecher am Sockel", "3_Bft9_Galerie_Sturm", s, len, (t, x) -> {
            if (t == 3 || t == 9 || t == 13) x.event(Soundscape.Kind.BRECHER, 0, 2, -5.5, 0.9);
        }));
        // 4 derselbe Sturm im Turm
        s = Soundscape.beaufort(9);
        tower(s);
        s.earY = 12;
        s.earZ = 0;
        s.inside = 1;
        s.room = Room.STONE_TOWER;
        l.add(scene("Bft 9 im Treppenhaus: dumpf und hallig", "4_Bft9_Treppenhaus", s, len, (t, x) -> {
            if (t == 3 || t == 9 || t == 13) x.event(Soundscape.Kind.BRECHER, 0, 2, -5.5, 0.9);
        }));
        // 5 Nebel
        s = Soundscape.beaufort(2);
        tower(s);
        s.earY = 24.2;
        s.earZ = -1;
        s.inside = 1;
        s.room = Room.GLASS_LANTERN;
        Hum ship = new Hum("Kümo", 900, 3, -600);
        s.hums.add(ship);
        l.add(scene("Nebel im Laternenraum: Nebelhorn Mo(R), Typhon eines Kümos in 1 km", "5_Nebel_Laternenraum", s, len, (t, x) -> {
            if (t == 1) { x.horn(Soundscape.Kind.NEBELHORN, 0, 29, 0, 1, 1); }
            if (t == 3) x.horn(Soundscape.Kind.NEBELHORN, 0, 29, 0, 1, 3);
            if (t == 7) x.horn(Soundscape.Kind.NEBELHORN, 0, 29, 0, 1, 1);
            if (t == 9) x.horn(Soundscape.Kind.TYFON, 900, 12, -600, 1, 5);
        }));
        // 6 Gewitter
        s = Soundscape.beaufort(6);
        tower(s);
        s.earY = 24.2;
        s.earZ = -1;
        s.inside = 1;
        s.room = Room.GLASS_LANTERN;
        s.rainRate = 12;
        s.glassDrops = 70;
        s.roofDrops = 160;
        l.add(scene("Gewitter im Laternenraum: Regen auf Glas und Kuppel, Donner nah und fern", "6_Gewitter_Laternenraum", s, len, (t, x) -> {
            if (t == 1) x.thunder(-3500, 1500, -2500, 92, null, null, new double[]{0, 0.07, 0.16}, true);
            if (t == 9) x.thunder(250, 400, -200, 116, null, null, new double[]{0, 0.05}, true);
        }));
        // 7 seit Haithabu Phase 14: eine Siedlung am Mittag
        s = village(Soundscape.beaufort(3));
        l.add(scene("Siedlung am Mittag: Schmied, Kammmacher, Bootsbauer, Markt, Hühner, Ziegen, Hund, Schilf, Pfähle", "7_Siedlung_Mittag", s, len, (t, x) -> {
            if (t % 4 == 1) x.event(Soundscape.Kind.HUHN, 8 + t, 0.3, -12, 0.6);
            if (t == 3 || t == 11) x.event(Soundscape.Kind.ZIEGE, -40, 6, -50, 0.7);
            if (t == 6) x.event(Soundscape.Kind.HUND, 15, 0.5, 10, 0.8);
            if (t == 9) x.event(Soundscape.Kind.KRAEHE, -30, 12, -30, 1);
            if (t == 2 || t == 13) x.event(Soundscape.Kind.ENTE, 10, 0, 30, 1);
        }));
        // 8 dieselbe Siedlung im Langhaus am Abend, Regen auf dem Reet
        s = village(Soundscape.beaufort(4));
        s.rainRate = 3;
        s.inside = 1;
        s.room = Room.LONGHOUSE;
        s.thatchNear = 1;
        s.blazes.add(new Blaze("Herd", 2.5, 0.4, -1, 0.3, true));
        for (Workshop w : s.workshops) w.active = false;
        s.crowds.get(0).people = 15;
        l.add(scene("Im Langhaus am Abend: Herd knistert, Regen und Wind im Reet, draußen der Hund und die Hühner", "8_Langhaus_Abend", s, len, (t, x) -> {
            if (t == 4) x.event(Soundscape.Kind.HUND, 15, 0.5, 10, 1);
            if (t == 9) x.event(Soundscape.Kind.HAHN, -12, 0.5, -20, 0.8);
        }));
        return l;
    }

    /** Seit Haithabu Phase 14: ein gedachtes Dorf um das Ohr (Werkstätten, Markt, Feuerstelle, Schilf, Pfähle am Ufer). */
    static Soundscape village(Soundscape s) {
        s.waveHeight = 0.15;
        s.wavePeriod = 2.5;
        s.obstruction = 4;
        s.earX = 0;
        s.earY = 1.7;
        s.earZ = 0;
        s.workshops.add(new Workshop("Schmiede", Workshop.Craft.SCHMIED, -70, 1, -60));
        s.workshops.add(new Workshop("Kammmacher", Workshop.Craft.KAMMMACHER, 16, 1, 7));
        s.workshops.add(new Workshop("Bootsbauer", Workshop.Craft.ZIMMERER, 45, 0.5, 35));
        s.crowds.add(new Crowd("Markt", 90, 1.5, -40, 120));
        s.blazes.add(new Blaze("Feuerstelle", 9, 0.3, -6, 0.3, false));
        s.reedNear = 0.3;
        s.thatchNear = 0.4;
        for (int i = 0; i < 12; i++) s.pilings.add(new double[]{-8 + i * 2.5, 14});
        return s;
    }

    interface Script { void at(int second, Soundscape s); }

    static Scene scene(String title, String file, Soundscape s, double len, Script script) {
        s.autoGain = true;
        s.render(3);
        int sr = s.sampleRate(), n = (int) (len * sr);
        float[] out = new float[2 * n];
        float[] buf = new float[2 * sr];
        for (int sec = 0; sec < (int) len; sec++) {
            script.at(sec, s);
            s.render(buf, sr);
            System.arraycopy(buf, 0, out, 2 * sec * sr, 2 * sr);
        }
        String lv = s.describe();
        return new Scene(title, file, out, sr, lv);
    }

    // ------------------------------------------------------------------ Bild

    static BufferedImage sheet(List<Scene> sc, int w) {
        int rowH = 210, head = 34;
        BufferedImage img = new BufferedImage(w, sc.size() * (rowH + head) + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 17, 24));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int y = 8;
        int axis = 64;
        for (Scene s : sc) {
            g.setColor(new Color(230, 234, 238));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            g.drawString(s.title, 10, y + 18);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
            g.setColor(new Color(150, 165, 178));
            g.drawString(s.levels, 10, y + 32);
            int top = y + head;
            Analysis.spectrogram(g, s.audio, s.sampleRate, axis, top, w - axis - 10, rowH - 6, -40);
            g.setColor(new Color(150, 165, 178));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            for (double f : new double[]{50, 100, 200, 500, 1000, 2000, 5000, 10_000}) {
                double fy = 1 - Math.log(f / 30) / Math.log(16_000 / 30.0);
                int yy = top + (int) (fy * (rowH - 7));
                g.drawString(f >= 1000 ? String.format(Locale.GERMAN, "%.0f kHz", f / 1000) : String.format(Locale.GERMAN, "%.0f Hz", f), 6, yy + 4);
            }
            y += rowH + head;
        }
        g.setColor(new Color(150, 165, 178));
        g.drawString("Spektrogramme, je 15 s, Frequenz logarithmisch 30 Hz bis 16 kHz; hell = laut. Gerechnet ohne Lautsprecher, mit Anpassung des Ohrs.", 10, y + 22);
        g.dispose();
        return img;
    }

    // ------------------------------------------------------------------ Fenster

    static void window() {
        Soundscape s = Soundscape.beaufort(5);
        tower(s);
        AudioOut out = new AudioOut(s);
        out.start();
        JComponent spec = new JComponent() {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0;
                g.setColor(new Color(11, 17, 24));
                g.fillRect(0, 0, getWidth(), getHeight());
                double[] lv = s.levels();
                int bw = (getWidth() - 90) / Soundscape.PARTS.length;
                for (int p = 0; p < lv.length; p++) {
                    int x = 70 + p * bw, h = (int) (Math.max(0, lv[p] - 20) / 90 * (getHeight() - 80));
                    g.setColor(p == lv.length - 1 ? new Color(226, 67, 47) : new Color(60, 160, 170));
                    g.fillRect(x + 6, getHeight() - 30 - h, bw - 12, h);
                    g.setColor(new Color(200, 210, 218));
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    g.drawString(Soundscape.PARTS[p], x + 6, getHeight() - 14);
                    g.drawString(lv[p] < 20 ? "still" : String.format(Locale.GERMAN, "%.0f dB", lv[p]), x + 6, getHeight() - 34 - h);
                }
                g.setColor(new Color(90, 100, 110));
                g.setStroke(new BasicStroke(1));
                for (int db = 40; db <= 100; db += 20) {
                    int yy = getHeight() - 30 - (int) ((db - 20) / 90.0 * (getHeight() - 80));
                    g.drawLine(66, yy, getWidth() - 16, yy);
                    g.drawString(db + " dB", 14, yy + 4);
                }
            }
        };
        spec.setPreferredSize(new Dimension(1000, 600));
        Color bg = new Color(11, 17, 24), ink = new Color(230, 234, 238), red = new Color(226, 67, 47);
        JPanel side = new JPanel();
        side.setBackground(bg);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        JLabel title = label("com.dan.soundscape", red), state = label(" ", ink), bftLbl = label("Bft 5", ink), rainLbl = label("Regen 0 mm/h", ink), volLbl = label("Lautstärke 100 %", ink);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
        FSlider bft = new FSlider(0, 12, 5), rain = new FSlider(0, 30, 0), vol = new FSlider(0, 200, 100);
        bft.addChangeListener(e -> {
            Soundscape p = Soundscape.beaufort(bft.getValue());
            s.windSpeed = p.windSpeed;
            s.waveHeight = p.waveHeight;
            s.wavePeriod = p.wavePeriod;
            s.gustiness = p.gustiness;
            bftLbl.setText("Bft " + bft.getValue());
        });
        rain.addChangeListener(e -> { s.rainRate = rain.getValue(); rainLbl.setText("Regen " + rain.getValue() + " mm/h"); });
        vol.addChangeListener(e -> { s.volume = vol.getValue() / 100.0; volLbl.setText("Lautstärke " + vol.getValue() + " %"); });
        FComboBox where = new FComboBox(new Object[]{"Galerie (draußen)", "Laternenraum", "Treppenhaus", "Steuerhaus", "Langhaus"});
        where.addActionListener(e -> {
            int i = where.getSelectedIndex();
            s.inside = i == 0 ? 0 : 1;
            s.room = switch (i) { case 1 -> Room.GLASS_LANTERN; case 2 -> Room.STONE_TOWER; case 3 -> Room.WHEELHOUSE; case 4 -> Room.LONGHOUSE; default -> Room.OUTDOORS; };
            s.glassDrops = i == 1 || i == 3 ? s.rainRate * 6 : 0;
            s.roofDrops = i == 1 ? s.rainRate * 15 : 0;
        });
        FButton horn = new FButton("Nebelhorn"), typhon = new FButton("Typhon in 1 km"), gull = new FButton("Möwe"), breaker = new FButton("Brecher"),
                near = new FButton("Donner nah"), far = new FButton("Donner fern");
        horn.addActionListener(e -> s.horn(Soundscape.Kind.NEBELHORN, 0, 29, 0, 1, 3));
        typhon.addActionListener(e -> s.horn(Soundscape.Kind.TYFON, 900, 12, -600, 1, 5));
        gull.addActionListener(e -> s.event(Soundscape.Kind.MOEWE, 20 * (Math.random() - 0.5), 26, -15, 1));
        breaker.addActionListener(e -> s.event(Soundscape.Kind.BRECHER, 0, 2, -5.5, 1));
        near.addActionListener(e -> s.thunder(250, 400, -200, 114, null, null, new double[]{0, 0.05}, true));
        far.addActionListener(e -> s.thunder(-3500, 1500, -2500, 90, null, null, new double[]{0, 0.07, 0.16}, false));
        // seit Haithabu Phase 14: ein Dorf um das Ohr, Rufe und Klänge der Siedlung
        FCheckBox dorf = new FCheckBox("Dorf (Werkstätten, Markt, Feuer, Pfähle)");
        Soundscape dorfVorlage = village(Soundscape.beaufort(0));
        dorf.addActionListener(e -> {
            if (dorf.isSelected()) {
                for (Workshop w : dorfVorlage.workshops) s.workshops.add(new Workshop(w.name, w.craft, w.x, w.y + 24, w.z));
                for (Crowd c : dorfVorlage.crowds) s.crowds.add(new Crowd(c.name, c.x, c.y + 24, c.z, c.people));
                for (Blaze b : dorfVorlage.blazes) s.blazes.add(new Blaze(b.name, b.x, b.y + 24, b.z, b.size, false));
                s.pilings.addAll(dorfVorlage.pilings);
                s.reedNear = 0.3;
                s.obstruction = 4;
            } else {
                s.workshops.clear(); s.crowds.clear(); s.blazes.clear(); s.pilings.clear();
                s.reedNear = 0;
                s.obstruction = 0;
            }
        });
        java.util.List<Soundscape.Kind> calls = new ArrayList<>();
        for (Soundscape.Kind k : Soundscape.Kind.values()) if (k.animal() || k.craft()) calls.add(k);
        FComboBox callBox = new FComboBox(calls.toArray());
        FButton call = new FButton("Ruf oder Klang");
        call.addActionListener(e -> s.event((Soundscape.Kind) callBox.getSelectedItem(), 25 * (Math.random() - 0.5), 22, -15, 1,
                callBox.getSelectedItem() == Soundscape.Kind.RUDER ? 10 : Math.random()));
        FCheckBox agc = new FCheckBox("Ohr passt sich an");
        agc.setSelected(true);
        agc.addActionListener(e -> s.autoGain = agc.isSelected());
        for (Component c : new Component[]{title, state, bftLbl, bft, rainLbl, rain, where, horn, typhon, gull, breaker, near, far, dorf, callBox, call, agc, volLbl, vol}) {
            if (c instanceof JComponent jc) jc.setAlignmentX(Component.LEFT_ALIGNMENT);
            side.add(c);
            side.add(javax.swing.Box.createVerticalStrut(6));
        }
        side.setPreferredSize(new Dimension(320, 600));
        new Timer(200, e -> {
            state.setText("<html><body style='width:270px'>" + s.describe() + (out.error().isEmpty() ? String.format(Locale.GERMAN, "<br>Puffer %.0f ms · %.1f ms Rechnen je s",
                    out.bufferMs(), s.msPerSecond()) : "<br>" + out.error()) + "</body></html>");
            spec.repaint();
        }).start();
        // FFrame zeichnet Titel und Rahmen selbst: der Inhalt gehört in getComponentPane(), nicht in getContentPane()
        FFrame fr = new FFrame("com.dan.soundscape – Klang");
        JPanel root = fr.getComponentPane();
        root.setLayout(new BorderLayout());
        root.add(spec, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        fr.setPreferredFrameSize(new Dimension(1360, 680));
        fr.setSize(1360, 680);
        fr.setLocationRelativeTo(null);
        fr.setVisible(true);
    }

    static JLabel label(String t, Color c) {
        JLabel l = new JLabel(t);
        l.setForeground(c);
        l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        return l;
    }
}
