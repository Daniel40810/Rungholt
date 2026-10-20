package com.dan.rungholt.app;

import com.dan.fframe.FFrame;
import com.dan.foptionpane.FOptionPane;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.gl.SceneView;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.ui.AppIcon;
import com.dan.rungholt.ui.ControlPanel;
import com.dan.rungholt.ui.Startbild;
import com.dan.rungholt.ui.Theme;
import com.dan.rungholt.wiring.Wiring;
import com.jogamp.opengl.GLProfile;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.IllegalComponentStateException;
import java.awt.Rectangle;
import java.io.File;

/**
 * Rungholt — Zweimal am Tag Land: das Nordfriesische Wattenmeer zwischen Pellworm, Südfall und Nordstrand mit seiner
 * Tide, und unter ihm die Stadt Rungholt, die in der Nacht vom 15. auf den 16. Januar 1362 unterging, als 3D-Szene auf
 * der Grafikkarte (JOGL, OpenGL 4.3).
 * <p>
 * Einstieg: Startbild, dann FFrame beim Start maximiert (Taskleiste bleibt frei), Größe frei veränderbar; Szene in
 * der Mitte, Bedienfeld rechts, Statuszeile unten. Himmel, Wetter, Wasser, Gischt, Welle, Bruch und Klang kommen als
 * eigenständige Packages unter {@code com.dan} aus Glacier, Fachwerk, Wege, Herd, Rauch und Figuren aus Haithabu, neu
 * sind {@code tide}, {@code mudflat} und {@code seal}; diese App verdrahtet sie nur ({@link Wiring}). Das Gerüst ist
 * aus Glacier übernommen und entschlackt (F4 A).
 * <p>
 * Schalter: {@code -Drungholt.splash=false} ohne Startbild, {@code -Drungholt.gljpanel=true} leichter GL-Rückfall,
 * {@code -Drungholt.shaders=<ordner>} Shader von der Platte (F5 lädt neu), {@code -Drungholt.daten=<ordner>} Ordner
 * für Dateien ohne Datenbank.
 */
public final class RungholtApp {
    /** Für Werkzeuge: Fenster und Szene, sobald sie stehen. */
    public static volatile FFrame frame;
    public static volatile SceneView scene;
    public static volatile ControlPanel controls;

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
        // Der GL-Canvas ist ein schweres AWT-Element: Menüs und Tooltips müssen darüber liegen können
        JPopupMenu.setDefaultLightWeightPopupEnabled(false);
        ToolTipManager.sharedInstance().setLightWeightPopupEnabled(false);
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            Dauerlauf.FEHLER.incrementAndGet();   // Phase 17
            System.err.println("Faden " + t.getName() + ": " + e);
            e.printStackTrace();
            fehlerDatei("Faden " + t.getName(), e);
        });
        GLProfile.initSingleton();
        SwingUtilities.invokeLater(() -> {
            try {
                open();
            } catch (Throwable e) {
                // ohne Konsole (javaw, Verknüpfung) bliebe ein Fehler beim Start sonst unsichtbar (Befund aus Haithabu)
                e.printStackTrace();
                File f = fehlerDatei("Start", e);
                FOptionPane.showMessageDialog(null, "Rungholt ließ sich nicht starten:\n" + e + (e.getStackTrace().length > 0 ? "\n  bei " + e.getStackTrace()[0] : "")
                        + "\n\nDie ganze Meldung steht in\n" + (f != null ? f.getAbsolutePath() : "der Konsole") + ".", "Rungholt", FOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }

    /** Ordner für Dateien der App ohne Datenbank (ab Phase 16 auch Zustand und Presets): {@code daten/}. */
    public static File datenOrdner() {
        String d = System.getProperty("rungholt.daten");
        return new File(d != null ? d : "daten");
    }

    /** Schreibt einen Fehler mit Stapel nach daten/fehler.txt (angehängt) und gibt die Datei zurück, oder null. */
    static File fehlerDatei(String wo, Throwable e) {
        try {
            File f = new File(datenOrdner(), "fehler.txt");
            f.getParentFile().mkdirs();
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            String t = java.time.LocalDateTime.now().withNano(0) + " · " + Renderer.VERSION + " · Java " + System.getProperty("java.version") + " · " + wo + "\n" + sw + "\n";
            java.nio.file.Files.writeString(f.toPath(), t, java.nio.charset.StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            return f;
        } catch (Exception x) {
            return null;
        }
    }

    /** Phase 16: die Chronik der laufenden Szene (Tiden, Zählungen, Mandränke, Glocken, Regiebücher). */
    public static volatile com.dan.rungholt.data.Chronik chronik;

    private static void open() {
        // Phase 16: das Startbild verbindet mit der Datenbank (oder daten/); „Weiter wie zuletzt“ bringt eine Sitzung mit
        Startbild.Wahl wahl = "false".equals(System.getProperty("rungholt.splash")) ? Startbild.stumm() : Startbild.zeigen();
        SceneClock clock = new SceneClock();
        Wiring wiring = new Wiring(clock);
        // F26: die Partialtiden aus der Datenbank (oder den Startdaten), der Code ist der Rückfall
        com.dan.rungholt.data.Pegel.laden(wiring, com.dan.rungholt.data.Datenseite.speicher());
        // Phase 3: die Wetterregie mit dem Klima Nordfrieslands läuft von Anfang an, ab einem klaren Sommertag
        wiring.regie = true;
        wiring.startFree(com.dan.weather.Weather.Phase.KLAR);
        Renderer rd = new Renderer(clock, wiring);
        FFrame f = new FFrame("Rungholt · Zweimal am Tag Land · " + Renderer.VERSION);
        AppIcon.install(f);
        if (!Boolean.getBoolean("rungholt.gljpanel")) makeOpaque(f);
        SceneView view;
        try {
            view = new SceneView(rd);
            final Renderer rdc = rd;
            // Phase 13: die Warftkarte; Phase 16: die Zellkarte des Watts, beide mit dem, was die Datenbank weiß
            view.setClickListener((x, y, w, h) -> { if (rdc.clickAt(x, y, w, h)) com.dan.rungholt.ui.Karten.ergaenzen(rdc); });
            // Phase 16 (F31): Umschalt + Klick ins Watt legt eine eigene Fundstelle an
            view.setShiftClickListener((x, y, w, h) -> com.dan.rungholt.ui.Karten.fundstelle(frame, rdc, x, y, w, h));
        } catch (RuntimeException e) {
            FOptionPane.showMessageDialog(null, "OpenGL lässt sich nicht starten:\n" + e.getMessage()
                    + "\n\nBitte den Grafiktreiber aktualisieren. Als Rückfall: Start mit -Drungholt.gljpanel=true.", "Rungholt", FOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        ControlPanel controls = new ControlPanel(view, wiring);
        JLabel status = new JLabel("Die Szene startet …");
        status.setForeground(new Color(170, 186, 196));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JLabel dbStatus = new JLabel(Startbild.statuszeile());
        new javax.swing.Timer(2000, e -> dbStatus.setText(Startbild.statuszeile())).start();
        dbStatus.setFont(new Font("SansSerif", Font.PLAIN, 12));
        dbStatus.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        dbStatus.setForeground(new Color(224, 190, 120));
        rd.setStatusListener(s -> SwingUtilities.invokeLater(() -> status.setText(s)));
        rd.setErrorListener(msg -> SwingUtilities.invokeLater(() -> FOptionPane.showMessageDialog(f, msg, "Rungholt · Grafik", FOptionPane.ERROR_MESSAGE)));

        JPanel root = f.getComponentPane();
        root.setLayout(new BorderLayout());
        root.setBackground(Theme.bg());
        root.add(view, BorderLayout.CENTER);
        JScrollPane side = new JScrollPane(controls, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.line()));
        side.getViewport().setBackground(Theme.bg());
        side.setBackground(Theme.bg());
        try {
            com.dan.fscrollbar.FScrollBar bar = new com.dan.fscrollbar.FScrollBar(javax.swing.JScrollBar.VERTICAL);
            bar.setUnitIncrement(16);
            bar.setBlockIncrement(120);
            side.setVerticalScrollBar(bar);
        } catch (RuntimeException | LinkageError e) {
            side.getVerticalScrollBar().setUnitIncrement(16);
        }
        side.setPreferredSize(new Dimension(320, 600));
        root.add(side, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setBackground(Theme.status());
        south.add(status, BorderLayout.CENTER);
        south.add(dbStatus, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);

        f.setPreferredFrameSize(new Dimension(1480, 900));
        f.setSize(1480, 900);
        f.setMinimumSize(new Dimension(800, 520));
        f.setLocationRelativeTo(null);
        f.setResizable(true);
        f.setVisible(true);
        startMaximized(f);
        view.start();
        // Phase 16 (F28, F36): Sitzung, Protokoll alle 5 s, Zustand alle 2 min und beim Ende; die Chronik
        com.dan.store.Protokoll.kontext(() -> com.dan.rungholt.data.Daten.text(clock.epochMillis()),
                () -> String.format(java.util.Locale.ROOT, "%.0f %.0f %.0f", rd.camera.eye[0], rd.camera.eye[1], rd.camera.eye[2]));
        com.dan.store.Dienst.starten(Renderer.VERSION, "", rd.quality().name(), () -> sicherung(rd));
        com.dan.store.Protokoll.info("App", "Rungholt " + Renderer.VERSION + " gestartet · " + com.dan.rungholt.data.Datenseite.wo() + " · Tide: "
                + com.dan.rungholt.data.Datenseite.tideHerkunft);
        com.dan.rungholt.data.Chronik ch = new com.dan.rungholt.data.Chronik(rd);
        chronik = ch;
        controls.chronik(ch);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { ch.beenden(); } catch (RuntimeException e) { System.err.println("Rungholt: Chronik nicht beendet: " + e); }
            try { com.dan.store.Dienst.beenden(); } catch (RuntimeException e) { System.err.println("Rungholt: Sitzung nicht beendet: " + e); }
            view.stop();
        }, "Rungholt-Ende"));
        if (wahl.weiter() != null) com.dan.rungholt.ui.Weiter.anwenden(rd, wahl.weiter(), null);
        if (wahl.kino() != null) com.dan.rungholt.ui.Weiter.kino(rd, wahl.kino());
        // F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)
        view.setKeyAction(java.awt.event.KeyEvent.VK_F11, () -> SwingUtilities.invokeLater(() -> {
            boolean an = !side.isVisible();
            side.setVisible(an);
            south.setVisible(an);
            root.revalidate();
        }));
        // Phase 15: der Kinomodus – Bedienfeld, Statuszeile und Titelleiste weg, das Fenster über den ganzen Bildschirm
        rd.kinoFenster = an -> SwingUtilities.invokeLater(() -> kinoFenster(f, view, side, south, root, an));
        // Phase 17: -Drungholt.dauerlauf=Minuten startet den Dauerlauf gleich
        String dl = System.getProperty("rungholt.dauerlauf");
        if (dl != null) Dauerlauf.starten(rd, Double.parseDouble(dl), f2 -> rd.hudMeldung("Dauerlauf fertig: " + (f2 != null ? f2.getName() : "ohne Bericht")));
        view.requestFocusInWindow();
        frame = f;
        scene = view;
        RungholtApp.controls = controls;
        System.out.println("Rungholt " + Renderer.VERSION + " gestartet · " + clock.label());
    }

    /** Der Zustand für die Sitzung (alle 2 min und beim Ende). */
    private static com.dan.store.Ablage.Sicherung sicherung(Renderer r) {
        java.util.LinkedHashMap<String, String> z = com.dan.rungholt.data.Zustand.erfassen(r);
        return new com.dan.store.Ablage.Sicherung(com.dan.store.Felder.alsText(z, "Rungholt · Sitzung · " + Renderer.VERSION), com.dan.rungholt.data.Zustand.spalten(z));
    }

    /**
     * Startet maximiert (MAXIMIZED_BOTH), ohne die Taskleiste zu verdecken. Die normale Größe von 1480 × 900 bleibt
     * als Rückfall; „Wiederherstellen“ des FFrame stellt sie wieder her (wie in Haithabu).
     */
    private static void startMaximized(FFrame f) {
        Rectangle normal = f.getBounds();
        f.setMaximizedBounds(GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds());
        f.setExtendedState(f.getExtendedState() | Frame.MAXIMIZED_BOTH);
        try {
            // Die Titelleiste des FFrame führt einen eigenen Maximiert-Zustand; ihn angleichen
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                Object bar = fd.get(f);
                Class<?> bc = bar.getClass();
                java.lang.reflect.Field mx = bc.getDeclaredField("maximized"), rb = bc.getDeclaredField("restoreBounds"),
                        btn = bc.getDeclaredField("btnMaximize");
                mx.setAccessible(true);
                rb.setAccessible(true);
                btn.setAccessible(true);
                mx.setBoolean(bar, true);
                rb.set(bar, normal);
                Object icon = btn.get(bar);
                icon.getClass().getMethod("setType", com.dan.ficons.FIconType.class).invoke(icon, com.dan.ficons.FIconType.RESTORE);
                ((javax.swing.JComponent) icon).setToolTipText("Wiederherstellen");
                ((java.awt.Component) icon).addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                        SwingUtilities.invokeLater(() -> {
                            if ((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0) {
                                f.setExtendedState(f.getExtendedState() & ~Frame.MAXIMIZED_BOTH);
                                f.setBounds(normal);
                            }
                        });
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht angeglichen: " + e);
        }
    }

    /**
     * Das FFrame ist ein durchscheinendes Fenster (runde Ecken). Darin zeigt Windows schwere Elemente wie den
     * GL-Canvas nicht; darum wird es deckend gemacht und die Rundung als Fensterform gesetzt.
     */
    private static void makeOpaque(FFrame f) {
        f.setBackground(Theme.bg());
        java.awt.event.ComponentAdapter shape = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { reshape(f); }
            @Override public void componentShown(java.awt.event.ComponentEvent e) { reshape(f); }
        };
        f.addComponentListener(shape);
        f.addWindowStateListener(e -> reshape(f));
    }

    private static boolean kinoVoll, kinoSeite = true, kinoSueden = true;
    private static int kinoZustand;
    private static Rectangle kinoRahmen;

    /** Phase 15: Vollbild für das Kino und zurück (aus Glacier). */
    private static void kinoFenster(FFrame f, com.dan.rungholt.gl.SceneView view, java.awt.Component side, java.awt.Component south, JPanel root, boolean an) {
        if (an == kinoVoll) return;
        java.awt.Component bar = taskbar(f);
        if (an) {
            kinoSeite = side.isVisible();
            kinoSueden = south.isVisible();
            kinoZustand = f.getExtendedState();
            kinoRahmen = f.getBounds();
            kinoVoll = true;
            side.setVisible(false);
            south.setVisible(false);
            if (bar != null) bar.setVisible(false);
            Rectangle schirm = f.getGraphicsConfiguration().getBounds();
            f.setExtendedState(Frame.NORMAL);
            f.setBounds(schirm);
            reshape(f);
            view.zeigerVerbergen(true);
            f.toFront();
        } else {
            kinoVoll = false;
            side.setVisible(kinoSeite);
            south.setVisible(kinoSueden);
            if (bar != null) bar.setVisible(true);
            if ((kinoZustand & Frame.MAXIMIZED_BOTH) != 0) f.setExtendedState(kinoZustand);
            else if (kinoRahmen != null) f.setBounds(kinoRahmen);
            reshape(f);
            view.zeigerVerbergen(false);
        }
        root.revalidate();
        root.repaint();
        view.requestFocusInWindow();
    }

    private static java.awt.Component taskbar(FFrame f) {
        try {
            for (java.lang.reflect.Field fd : FFrame.class.getDeclaredFields()) {
                if (!fd.getType().getSimpleName().equals("FTaskbar")) continue;
                fd.setAccessible(true);
                return (java.awt.Component) fd.get(f);
            }
        } catch (Exception e) {
            System.err.println("FFrame-Titelleiste nicht gefunden: " + e);
        }
        return null;
    }

    private static void reshape(FFrame f) {
        try {
            boolean max = kinoVoll || (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0;
            f.setShape(max ? null : new java.awt.geom.RoundRectangle2D.Double(0, 0, f.getWidth(), f.getHeight(), 20, 20));
        } catch (UnsupportedOperationException | IllegalComponentStateException e) {
            // Form nicht möglich: dann eben eckig
        }
    }

    private RungholtApp() { }
}
