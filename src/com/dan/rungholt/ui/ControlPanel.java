package com.dan.rungholt.ui;

import com.dan.fbutton.FButton;
import com.dan.fcheckbox.FCheckBox;
import com.dan.fcollapsiblepanel.FCollapsiblePanel;
import com.dan.fcombobox.FComboBox;
import com.dan.fslider.FSlider;
import com.dan.rungholt.camera.Ansichten;
import com.dan.rungholt.camera.Camera;
import com.dan.rungholt.gl.ComputeProbe;
import com.dan.rungholt.gl.Quality;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.gl.SceneView;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Sun;
import com.dan.rungholt.wiring.Beaufort;
import com.dan.rungholt.wiring.Wiring;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.prefs.Preferences;

/**
 * Das Bedienfeld rechts als Klappblöcke (aus Glacier, entschlackt). Phase 1: ZEIT mit Datum, Uhrzeit, Zeitraffer und
 * Sonne; WETTER (Phase 3) mit der Regie aus com.dan.weather (Klima Nordfriesland, Orkan aus Westnordwest), Wind,
 * Bedeckung, Regen, Seenebel, Sicht und Gewitter von Hand, Ebenen und einem Blitz in Blickrichtung; TIDE (Phase 4) mit Pegel, Wasser am Ort, Tidenkalender Husum, Windstau, Pegel von Hand und
 * Sprüngen zum nächsten Hoch- und Niedrigwasser; WATT (Phase 5) mit Nässe und Sediment am Ort, Rippeln und
 * Wattwurmhaufen, seit Phase 6 mit der Feinform (Prielkanten, Rinnsale); ZEITREISE mit dem
 * Schalter Heute · 1362 (Taste Z); KAMERA mit Ansichten 1 bis 9; BILD mit Qualität, Auflösung, HUD, Marken, Foto;
 * TECHNIK mit Leistung, Physik, Packages und Verdrahtung. LEBEN (Phase 7) mit den Seehunden und dem Wattwanderer,
 * seit Phase 8 mit den Wattvögeln.
 * MANDRÄNKE (Phase 12) mit Auslöser, Bruchstelle und den Sprüngen durch die Nacht. Seit Phase 16 CHRONIK (Tiden des
 * Tages, letzte Tiden, Tageskarte, Chronik leeren) und DATEN (Verbinden, Einrichten, Prüfen, Weiter wie zuletzt,
 * Übernahme, Sturmfluten, Presets, gemerkte Ansichten, Protokoll), im Block TIDE die Tidenkurve über 25 h. Offen bleibt, was zuletzt offen
 * war (je Rechner gemerkt). Jeder Regler schreibt direkt in die öffentlichen Felder von Uhr, Kamera, Verdrahtung und
 * Renderer; es gibt keinen zweiten Zustand.
 */
public final class ControlPanel extends JPanel implements javax.swing.Scrollable {
    private final SceneView view;
    private final Renderer r;
    private final SceneClock clock;
    private final Wiring wiring;
    private final JLabel dateLbl = label(""), timeLbl = label(""), sunLbl = small(""), windLbl = label(""), dirLbl = label(""),
            levelLbl = label(""), eraLbl = label(""), landLbl = small(""), townLbl = small(""), mandLbl = small(""), spurLbl = small(""), glockLbl = small(""), klangLbl = small(""), buchLbl = small(""), camLbl = small(""), perfLbl = small(""), computeLbl = small(""), glLbl = small(""),
            physLbl = small(""), linksLbl = small(""), hudLbl = small(""), scaleLbl = label(""), qualLbl = small(""), pkgLbl = small(""),
            regieLbl = small(""), skyLbl = small(""), coverLbl = label(""), rainLbl = label(""), fogLbl = label(""), visLbl = label(""),
            waterLbl = small(""), calLbl = small(""), floodLbl = small(""), mudLbl = small(""), sealLbl = small(""), birdLbl = small(""), hikeLbl = small(""), yearLbl = small("");
    private final FSlider time = new FSlider(0, 24 * 60 - 1, 13 * 60 + 30), wind = new FSlider(0, 12, 4), windDir = new FSlider(0, 359, 250),
            scaleSl = new FSlider(50, 100, 85), level = new FSlider(-300, 300, 0),
            cover = new FSlider(0, 80, 25), rainSl = new FSlider(0, 200, 0), fogSl = new FSlider(0, 100, 0), visSl = new FSlider(1, 80, 30),
            ripSl = new FSlider(0, 100, 100), wormSl = new FSlider(0, 40, 20);
    private final FCheckBox free = new FCheckBox("Frei fliegen (F)"), orbit = new FCheckBox("Von selbst kreisen (R)"),
            marks = new FCheckBox("Marken (Raster, Kirche, Warft, Nord)"), autoBox = new FCheckBox("Auflösung selbst regeln"),
            handPegel = new FCheckBox("Pegel von Hand halten");
    private FComboBox lapseBox, qualBox, hudBox, regieBox, thunderBox;
    /** Phase 12: die Schritte der Mandränke (Freigabe je nach Stufe, {@link #mandButtons}). */
    private FButton mStart, mJump, mNow, mAfter, mStop;
    /** Phase 15: Regiebücher im Kino zeigen (auch für „Regiebuch“ unter „Die Glocken“). */
    private FCheckBox imKino;
    private volatile boolean mandBusy;
    /** Phase 3: die Wahl der Regie: von Hand, freie Regie ab einer Lage (Klima Nordfriesland). */
    static final Object[] REGIE = {"von Hand", "frei ab „klar“", "frei ab „aufziehend“", "frei ab „Front“", "frei ab „Sturm“", "frei ab „Gewitter“",
            "frei ab „Seenebel“", "frei ab „Orkan aus WNW“"};
    static final com.dan.weather.Weather.Phase[] REGIE_PHASE = {null, com.dan.weather.Weather.Phase.KLAR, com.dan.weather.Weather.Phase.AUFZIEHEND,
            com.dan.weather.Weather.Phase.FRONT, com.dan.weather.Weather.Phase.STURM, com.dan.weather.Weather.Phase.GEWITTER,
            com.dan.weather.Weather.Phase.NEBEL, com.dan.weather.Weather.Phase.ORKAN};
    private boolean fromScene;
    private int ticks;
    private JPanel target = this;
    private static final Preferences PREFS = prefs();
    private final java.util.List<Object[]> bloecke = new java.util.ArrayList<>();
    private final java.util.List<FCollapsiblePanel> zu = new java.util.ArrayList<>();
    private static final double[] LAPSE = {0, 1, 10, 60, 180, 720, 3600};

    /** Die Element-Packages im Build (Werkbuch, Abschnitt 4) mit Stand. */
    public static final String[] PAKETE = {"tide (neu, Hülle)", "mudflat (neu, Hülle)", "seal (neu, Hülle)", "sky", "cloud", "weather", "rain", "fog",
            "lightning", "ocean (Preset Watt)", "spray", "buoyancy", "weathering", "flock", "forest", "ground", "river", "reed", "season", "soundscape", "store",
            "wave", "fracture", "kino", "timber (aus Haithabu)", "road (aus Haithabu)", "hearth (aus Haithabu)", "smoke (aus Haithabu)", "muster (aus Haithabu)"};

    private static Preferences prefs() {
        try { return Preferences.userRoot().node("com/dan/rungholt/bedienfeld"); } catch (RuntimeException | Error e) { return null; }
    }

    public ControlPanel(SceneView view, Wiring wiring) {
        this.view = view;
        this.r = view.renderer();
        this.clock = r.clock;
        this.wiring = wiring;
        setBackground(Theme.bg());
        setOpaque(true);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(10, 8, 16, 10));

        // ============================================================== ZEIT
        block("ZEIT", true);
        put(dateLbl);
        JPanel days = grid(3);
        FButton minus = new FButton("− Tag"), heute = new FButton("21.7.2026"), plus = new FButton("+ Tag");
        minus.addActionListener(e -> { clock.shiftDays(-1); syncFromScene(); view.requestFocusInWindow(); });
        plus.addActionListener(e -> { clock.shiftDays(1); syncFromScene(); view.requestFocusInWindow(); });
        heute.setToolTipText("Zurück zum Start der Szene: 21. Juli 2026, 13:30 Uhr MESZ");
        heute.addActionListener(e -> { clock.set(SceneClock.START_DATE, SceneClock.START_HOUR); syncFromScene(); view.requestFocusInWindow(); });
        days.add(minus); days.add(heute); days.add(plus);
        put(days);
        FComboBox monthBox = new FComboBox(new Object[]{"Monat springen …", "21. Januar", "21. Februar", "21. März", "21. April", "21. Mai", "21. Juni",
                "21. Juli", "21. August", "21. September", "21. Oktober", "21. November", "21. Dezember"});
        monthBox.setToolTipText("Zum 21. eines Monats im laufenden Jahr, gleiche Uhrzeit (das Jahr mit Vogelzug und Ringelgänsen kommt in Phase 9)");
        monthBox.addActionListener(e -> {
            int i = monthBox.getSelectedIndex();
            if (i <= 0) return;
            clock.set(LocalDate.of(clock.date().getYear(), i, 21), clock.hour());
            monthBox.setSelectedIndex(0);
            syncFromScene();
            view.requestFocusInWindow();
        });
        put(monthBox);
        put(timeLbl);
        put(time);
        time.addChangeListener(e -> { if (!fromScene) clock.setHour(time.getValue() / 60.0); labels(); });
        put(label("Zeitraffer"));
        lapseBox = new FComboBox(new Object[]{"Uhr steht", "Echtzeit", "10-fach", "1 min/s", "3 min/s (eine Tide in 4 min)", "12 min/s (eine Tide in 1 min)", "1 h/s"});
        lapseBox.setSelectedIndex(1);
        lapseBox.addActionListener(e -> { clock.lapse = LAPSE[Math.max(0, lapseBox.getSelectedIndex())]; view.requestFocusInWindow(); });
        put(lapseBox);
        put(sunLbl);
        put(small("<html><body style='width:215px'>Deutsche Zeit (MEZ, im Sommer MESZ). Auf 54,5° Nord steht die Sonne im Sommer gegen 13:25 Uhr im Süden; "
                + "im Juni geht sie nach 22 Uhr unter. Himmel aus com.dan.sky: Sonne, Mond, Planeten und die Sterne des Nordhimmels; "
                + "das Auge passt sich an wie in Glacier (nachts nur zu 85 %, die Nacht bleibt Nacht).</body></html>"));

        // ============================================================== WETTER
        block("WETTER", true);
        put(label("Regie"));
        regieBox = new FComboBox(REGIE);
        regieBox.setSelectedIndex(wiring.regie ? 1 : 0);
        regieBox.setToolTipText("Freie Regie mit dem Klima Nordfrieslands: Westwind, Fronten aus Südwest, Sommergewitter, Seenebel im Frühjahr und Herbst, Orkan aus Westnordwest");
        regieBox.addActionListener(e -> { if (fromScene) return; regie(regieBox.getSelectedIndex()); view.requestFocusInWindow(); });
        put(regieBox);
        put(regieLbl);
        JPanel rb = grid(2);
        FButton weiter = new FButton("Regie weiter"), orkan = new FButton("Orkan aus WNW");
        weiter.setToolTipText("Alle Handregler lösen; die Größen gleiten mit ihrer Trägheit zurück zur Regie");
        weiter.addActionListener(e -> { wiring.resumeRegie(); view.requestFocusInWindow(); });
        orkan.setToolTipText("Die freie Regie springt in die Lage „Orkan“: um 33 m/s aus 295°, geschlossene Decke, Regen, Sicht 3 km, 965 hPa – das Wetter der Mandränke");
        orkan.addActionListener(e -> { regieBox.setSelectedIndex(REGIE.length - 1); view.requestFocusInWindow(); });
        rb.add(weiter); rb.add(orkan);
        put(rb);
        put(windLbl);
        put(wind);
        wind.addChangeListener(e -> {
            if (fromScene) return;
            if (wiring.regie) wiring.hand(com.dan.weather.Weather.Var.WIND, Beaufort.mid(wind.getValue()));
            else wiring.wind.speed = Beaufort.mid(wind.getValue());
            labels();
        });
        put(dirLbl);
        put(windDir);
        windDir.addChangeListener(e -> {
            if (fromScene) return;
            if (wiring.regie) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, windDir.getValue());
            else wiring.wind.fromDeg = windDir.getValue();
            labels();
        });
        sub("Himmel");
        put(skyLbl);
        put(coverLbl);
        put(cover);
        cover.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.COVER, cover.getValue() / 10.0); labels(); });
        put(rainLbl);
        put(rainSl);
        rainSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.RAIN, rainMm(rainSl.getValue())); labels(); });
        put(fogLbl);
        put(fogSl);
        fogSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.FOG, fogSl.getValue() / 100.0); labels(); });
        put(visLbl);
        put(visSl);
        visSl.addChangeListener(e -> { if (!fromScene) hand(com.dan.weather.Weather.Var.VISIBILITY, visSl.getValue()); labels(); });
        put(label("Gewitter"));
        thunderBox = new FComboBox(new Object[]{"keines", "fern", "nah", "heftig", "über uns"});
        thunderBox.addActionListener(e -> { if (fromScene) return; hand(com.dan.weather.Weather.Var.THUNDER, thunderBox.getSelectedIndex()); view.requestFocusInWindow(); });
        put(thunderBox);
        JPanel sh1 = grid(2), sh2 = grid(2);
        layer(sh1, "Wolken", r.showClouds, v -> r.showClouds = v);
        layer(sh1, "Regen", r.showRain, v -> r.showRain = v);
        layer(sh2, "Nebel", r.showFog, v -> r.showFog = v);
        layer(sh2, "Blitze", r.showLightning, v -> r.showLightning = v);
        put(sh1);
        put(sh2);
        FButton bolt = new FButton("Blitz in Blickrichtung");
        bolt.setToolTipText("Ein Erdblitz in 1,5 bis 4 km vor der Kamera (auch ohne Gewitter)");
        bolt.addActionListener(e -> { r.triggerLightning(com.dan.lightning.Bolt.Kind.CLOUD_GROUND); view.requestFocusInWindow(); });
        put(bolt);
        put(small("<html><body style='width:215px'>Handregler halten eine Größe fest, bis „Regie weiter“. Ab Phase 4 drückt der Wind als Windstau auf die Tide; "
                + "die Glocken im Sturm kommen mit dem Klang (Phase 14).</body></html>"));

        // ============================================================== TIDE
        block("TIDE", true);
        put(levelLbl);
        put(handPegel);
        handPegel.addActionListener(e -> {
            wiring.level.hand = handPegel.isSelected() ? level.getValue() / 100.0 : Double.NaN;
            labels();
            view.requestFocusInWindow();
        });
        put(level);
        level.addChangeListener(e -> {
            if (fromScene) return;
            handPegel.setSelected(true);
            wiring.level.hand = level.getValue() / 100.0;
            labels();
        });
        JPanel jumps = grid(2);
        FButton toHw = new FButton("→ Hochwasser"), toNw = new FButton("→ Niedrigwasser");
        toHw.setToolTipText("Die Uhr springt zum nächsten Hochwasser an der Hallig Südfall (rund 5 min vor Husum)");
        toNw.setToolTipText("Die Uhr springt zum nächsten Niedrigwasser an der Hallig Südfall");
        toHw.addActionListener(e -> jumpTide(true));
        toNw.addActionListener(e -> jumpTide(false));
        jumps.add(toHw); jumps.add(toNw);
        put(jumps);
        put(waterLbl);
        sub("Tidenkurve am Rand, 25 h (Phase 16)");
        tidenkurve = new Tidenkurve(wiring, clock);
        put(tidenkurve);
        put(tideSrcLbl);
        sub("Tidenkalender Husum");
        put(calLbl);
        put(floodLbl);
        put(small("<html><body style='width:215px'>com.dan.tide: zehn Partialtiden, angepasst an den Gezeitenkalender 2026 des BSH für Husum "
                + "(MThw NHN +1,6 m, MTnw −1,9 m); am Rand des Watts 27 min früher [G]. Die Flut läuft mit Laufzeit durch die Priele, "
                + "in Senken bleiben Pfützen; Windstau aus dem Wind gegen die Küste (Orkan aus WNW: über 3 m).</body></html>"));

        // ============================================================== WATT
        block("WATT", true);
        put(mudLbl);
        put(label("Rippel"));
        put(ripSl);
        ripSl.addChangeListener(e -> { if (!fromScene) r.mudGl.ripples = ripSl.getValue() / 100.0; });
        put(label("Wattwurmhaufen je m²"));
        put(wormSl);
        wormSl.addChangeListener(e -> { if (!fromScene) r.mudflat.lugwormPerM2 = wormSl.getValue(); });
        FButton soak = new FButton("Watt nass machen");
        soak.setToolTipText("Setzt die Nässe überall auf 1, als wäre die Flut gerade abgelaufen; dann trocknet es nach dem Wetter");
        soak.addActionListener(e -> {
            com.dan.mudflat.WetField wf = r.wetField();
            if (wf != null) { java.util.Arrays.fill(wf.wet, 1f); wf.version++; }
            view.requestFocusInWindow();
        });
        put(soak);
        FCheckBox relief = new FCheckBox("Feinform (Prielkanten, Rinnsale)");
        relief.setSelected(r.relief.on);
        relief.setToolTipText("Phase 6: Prielkanten mit Abbruch, Rinnsale und Wellenrücken aus com.dan.mudflat über dem Höhenraster");
        relief.addActionListener(e -> { r.relief.on = relief.isSelected(); view.requestFocusInWindow(); });
        put(relief);
        put(small("<html><body style='width:215px'>com.dan.mudflat: Nässe je Zelle der Überflutung, springt mit dem Wasser auf 1 und "
                + "trocknet mit Sonne, Wind und Wärme (Sand halb in 25 min bis 3 h, Schlick bleibt feucht); Sand seewärts und in den "
                + "Rinnen, Schlick in den Buchten am Vorland; Rippel quer zur letzten Strömung mit Wasser in den Tälern, flach bei schwacher "
                + "Strömung [G]. Seit Phase 6 die Feinform: Prielkanten mit Abbruch, Rinnsale, in denen das Wasser zuletzt abläuft.</body></html>"));

        // ============================================================== LEBEN (Phase 7)
        block("LEBEN", true);
        put(sealLbl);
        FCheckBox seals = new FCheckBox("Seehunde (com.dan.seal)");
        seals.setSelected(r.showSeals);
        seals.addActionListener(e -> { r.showSeals = seals.isSelected(); view.requestFocusInWindow(); });
        put(seals);
        FCheckBox spuren = new FCheckBox("Spuren im Watt (Robben, Stiefel)");
        spuren.setSelected(r.showTracks);
        spuren.addActionListener(e -> { r.showTracks = spuren.isSelected(); view.requestFocusInWindow(); });
        put(spuren);
        put(label("Zahl der Seehunde"));
        FSlider countSl = new FSlider(0, 60, r.colony.count);
        countSl.addChangeListener(e -> { if (!countSl.getValueIsAdjusting()) { r.sealCount = countSl.getValue(); r.colony.count = countSl.getValue(); } });
        put(countSl);
        put(label("Fluchtdistanz (m)"));
        FSlider fluchtSl = new FSlider(50, 500, (int) r.sealKind.flightDistance);
        fluchtSl.addChangeListener(e -> r.sealKind.flightDistance = fluchtSl.getValue());
        put(fluchtSl);
        JPanel walk = grid(2);
        FButton go = new FButton("Wanderer zur Bank"), back = new FButton("Wanderer zurück");
        go.setToolTipText("Eine Figur geht über das trockene Watt auf die liegenden Seehunde zu; unter 300 m flüchten sie ins Wasser");
        go.addActionListener(e -> { if (!r.sendWalker()) sealLbl.setText("<html><body style='width:215px'>Kein trockener Weg zur Bank (bei Ebbe versuchen).</body></html>"); view.requestFocusInWindow(); });
        back.addActionListener(e -> { r.walker.turnBack(); view.requestFocusInWindow(); });
        walk.add(go); walk.add(back);
        put(walk);
        sub("Wattvögel (com.dan.flock)");
        put(birdLbl);
        FCheckBox birds = new FCheckBox("Wattvögel");
        birds.setSelected(r.showBirds);
        birds.addActionListener(e -> { r.showBirds = birds.isSelected(); view.requestFocusInWindow(); });
        put(birds);
        JPanel bb = grid(2);
        FButton flush = new FButton("Schwarm aufscheuchen"), toBirds = new FButton("Kamera zu den Vögeln");
        flush.setToolTipText("Die nächste Gruppe am Boden fliegt auf; ein Schwarm Alpenstrandläufer wendet über dem Watt");
        flush.addActionListener(e -> { r.flushBirds(); view.requestFocusInWindow(); });
        toBirds.setToolTipText("Stellt die Kamera vor die nächste Gruppe, die frisst (am liebsten Alpenstrandläufer)");
        toBirds.addActionListener(e -> { r.placeBirdView(); view.requestFocusInWindow(); });
        bb.add(flush); bb.add(toBirds);
        put(bb);
        FCheckBox zug = new FCheckBox("Vogelzug (Ringelgänse im Keil, Frühjahr und Herbst)");
        zug.setSelected(r.waders.migration);
        zug.addActionListener(e -> { r.waders.migration = zug.isSelected(); view.requestFocusInWindow(); });
        put(zug);
        sub("Wattwanderung (com.dan.figure)");
        put(hikeLbl);
        FCheckBox figs = new FCheckBox("Figuren");
        figs.setSelected(r.showFigures);
        figs.addActionListener(e -> { r.showFigures = figs.isSelected(); view.requestFocusInWindow(); });
        put(figs);
        JPanel hkb = grid(3);
        FButton hStart = new FButton("Losgehen"), hFollow = new FButton("Mitgehen"), hStop = new FButton("Beenden");
        hStart.setToolTipText("Die geführte Wattwanderung von Nordstrand (Fuhlehörn) nach Südfall startet am Deich; am besten 1,5 h vor Niedrigwasser");
        hStart.addActionListener(e -> { r.startHike(); view.requestFocusInWindow(); });
        hFollow.setToolTipText("Fahrt „Wattwanderung“: die Kamera geht seitlich hinter der Gruppe mit (Maus: umsehen, Pfeiltaste: aussteigen)");
        hFollow.addActionListener(e -> { r.followHike(); view.requestFocusInWindow(); });
        hStop.addActionListener(e -> { r.stopHike(); view.requestFocusInWindow(); });
        hkb.add(hStart); hkb.add(hFollow); hkb.add(hStop);
        put(hkb);
        sub("Südfall und das Jahr (com.dan.season, com.dan.timber)");
        put(yearLbl);
        FCheckBox houses = new FCheckBox("Friesenhaus, Stall, Fething und Pütten");
        houses.setSelected(r.showHouses);
        houses.addActionListener(e -> { r.showHouses = houses.isSelected(); view.requestFocusInWindow(); });
        put(houses);
        FButton neu = new FButton("Seehunde und Vögel neu setzen");
        neu.addActionListener(e -> { r.settleSeals(); view.requestFocusInWindow(); });
        put(neu);
        put(small("<html><body style='width:215px'>com.dan.seal: Seehunde an der Bank am Priel westlich von Südfall [G]. Sie kommen, wenn die Bank "
                + "trockenfällt, liegen nahe am Wasser mit Abstand zueinander und dem Kopf zum Wasser, heben die Köpfe, liegen in der Sonne als "
                + "Banane und gehen mit der Flut; unter der Fluchtdistanz robben sie ins Wasser. Im Juni und Juli Heuler bei der Mutter. "
                + "Taste 5 stellt die Kamera vor die Gruppe. Seit Phase 8 die Wattvögel: Alpenstrandläufer, Austernfischer, Säbelschnäbler, "
                + "Brandgans, Silber- und Lachmöwe, im Winter Ringelgänse; sie fressen bei Ebbe am Saum und fliegen vor der Flut zum Vorland von Südfall. "
                + "Seit Phase 9 die Wattwanderung von Fuhlehörn nach Südfall (5,8 km über die hohen Watten, vier Halte, an der Kirche von Rungholt vorbei), "
                + "das Jahr mit Schnee, Reif und der roten Salzwiese im Herbst, das Friesenhaus auf der Warft von Südfall.</body></html>"));

        // ============================================================== ZEITREISE
        block("ZEITREISE", false);
        put(eraLbl);
        JPanel eras = grid(2);
        FButton heuteB = new FButton("Heute"), alt = new FButton("1362");
        heuteB.addActionListener(e -> { wiring.era.target = 0; view.requestFocusInWindow(); });
        alt.addActionListener(e -> { wiring.era.target = 1; view.requestFocusInWindow(); });
        eras.add(heuteB); eras.add(alt);
        put(eras);
        put(landLbl);
        FButton deich = new FButton("Auf den Seedeich von 1362");
        deich.setToolTipText("Kamera auf die Krone des Seedeichs gut 500 m südlich der Kirche, Blick nach Norden über die Marsch zur Warftenkette");
        deich.addActionListener(e -> { r.placeDikeView(); view.requestFocusInWindow(); });
        put(deich);
        FButton flug = new FButton("Über die Warftenkette");
        flug.setToolTipText("Fahrt „Über die Warftenkette“: 30 m hoch die Kette entlang von West nach Ost, die Stadt links (1362)");
        flug.addActionListener(e -> { r.startChainFlight(); view.requestFocusInWindow(); });
        put(flug);
        put(townLbl);
        JPanel tl = grid(2);
        layer(tl, "Stadt", r.showTown, b -> r.showTown = b);
        layer(tl, "Herdrauch", r.showSmoke, b -> r.showSmoke = b);
        put(tl);
        put(small("<html><body style='width:215px'>Taste Z schaltet um, die Überblendung dauert 4 s (F17 A): das Watt wird zur Marsch, die Warften "
                + "steigen, der Seedeich wächst aus dem Watt. Seit Phase 10 das Gelände von 1362 [G]: Marsch NHN +0,5 bis +1 m mit Fennen und Gräben, "
                + "54 Warften (die Kette von 2,3 km durch die Kirchwarft und die Hofwarften), Sielzüge zum Siel, der Seedeich (Krone +3,5 m) zur Hever. "
                + "Seit Phase 11 die Stadt [G]: Häuser aus Fachwerk und Reet auf den Warften, die Kirche aus Backstein mit Westturm (41 m, Turm 33 m), "
                + "Herdrauch, Leute, Rinder und Schafe, der Sielhafen mit Kaje und die Kogge, die mit der Flut einläuft und bei Ebbe im Schlick sitzt. "
                + "Ansichten 8 (vom Kirchturm) und 9 (Sielhafen). Seit Phase 13 steigen die Häuser bei der Überblendung aus dem Boden, "
                + "das Gras wächst über den Sand; wer auf einer Warft steht, steht danach auf ihrer Spur.</body></html>"));
        view.setKeyAction(java.awt.event.KeyEvent.VK_Z, () -> wiring.era.toggle());

        // Phase 13: die Spuren, die Magnetik, die Fundstellen, die Grabung, die Glocken
        sub("Spuren im Watt (heute)");
        put(spurLbl);
        JPanel sl = grid(2);
        layer(sl, "Spuren", r.showSpuren, b -> r.showSpuren = b);
        layer(sl, "Fundstellen", r.showFinds, b -> r.showFinds = b);
        layer(sl, "Magnetik", r.magnetik > 0.5, b -> r.magnetik = b ? 1 : 0);
        layer(sl, "Grabung", r.showDig, b -> r.showDig = b);
        layer(sl, "Wege 1362", r.showRoads, b -> r.showRoads = b);
        put(sl);
        FButton oben = new FButton("Draufsicht mit Magnetik");
        oben.setToolTipText("Kamera 2,6 km senkrecht über die Messfläche, Norden oben; blendet die Magnetik und die Fundstellen ein (heute)");
        oben.addActionListener(e -> { r.placeSurveyView(true); r.showFinds = true; view.requestFocusInWindow(); });
        put(oben);
        FButton grab = new FButton("Zur Grabung an der Kirche");
        grab.setToolTipText("Kamera 25 m vor die Grabung über der Nordwand der Kirche, Auge 3 m (heute). Die Archäologen kommen bei Ebbe, wenn die Stelle lange genug trocken bleibt");
        grab.addActionListener(e -> { wiring.era.target = 0; r.glockenView(com.dan.rungholt.scene.Glocken.Blick.GRABUNG); view.requestFocusInWindow(); });
        put(grab);
        put(small("<html><body style='width:215px'>Die Spuren von Rungholt [G]: die Warften als flache Rücken aus Klei im Watt, Kulturschicht mit Torf, "
                + "Ziegelbruch und Muschelkalk, Sodenbrunnen, die Gräben der Fennen und die Sielzüge als dunkle Bänder, das Fundament der Kirche aus "
                + "Feldstein. Alles aus demselben Land von 1362, darum liegen die Spuren genau unter den Warften. Die Magnetik ist ein Bild nach dem Muster "
                + "der Messungen von 2023, nicht deren Daten. Ein Klick auf eine Warft oder ihre Spur öffnet die Warftkarte.</body></html>"));
        sub("Die Glocken (Sage)");
        put(glockLbl);
        JPanel gb = grid(2);
        FButton jetzt = new FButton("Glocken jetzt"), buch = new FButton("Regiebuch");
        jetzt.setToolTipText("Die Glocken läuten sofort (auch ohne Sturmnacht); die Kirche steht für die Dauer des Läutens als Schemen über dem Wasser");
        buch.setToolTipText("Regiebuch „Die Glocken“: 30.11.2026, Weststurm, die Glocken zum Abendhochwasser, die Nacht im Zeitraffer, am Mittag bei Ebbe Spuren, Grabung, Magnetik und die Überblendung nach 1362 und zurück (gut 2 min)");
        jetzt.addActionListener(e -> { r.glocken.force(); view.requestFocusInWindow(); });
        buch.addActionListener(e -> {   // seit Phase 15 das Regiebuch als Text (scene/regie/glocken.txt)
            if (r.regie.running()) r.regieStop();
            else { com.dan.rungholt.scene.Regie.Book b = com.dan.rungholt.scene.Regie.glocken(); if (imKino != null && imKino.isSelected()) r.kinoStart(b); else r.regieStart(b); }
            view.requestFocusInWindow();
        });
        gb.add(jetzt); gb.add(buch);
        put(gb);
        put(small("<html><body style='width:215px'>Nach der Sage läuten die Glocken von Rungholt in Sturmnächten aus dem Watt: heute, nach der "
                + "Dämmerung, Wind ab Bft 8 aus Südwest bis Nordwest und das Wasser über der Kirchwarft. „Regiebuch“ startet das Regiebuch „Die Glocken“ (Block REGIEBÜCHER: im Kino oder nicht), ein zweiter Klick hält es an.</body></html>"));

        // ============================================================== REGIEBÜCHER (Phase 15)
        block("REGIEBÜCHER", false);
        put(buchLbl);
        java.util.List<com.dan.rungholt.scene.Regie.Book> books;
        JLabel buchInfo = small("");
        try { books = com.dan.rungholt.scene.Regie.all(); }
        catch (RuntimeException ex) { books = java.util.List.of(); buchInfo.setText("Regiebücher nicht lesbar: " + ex.getMessage()); }
        FComboBox bookBox = new FComboBox(books.toArray());
        put(bookBox);
        Runnable showAbout = () -> {
            if (bookBox.getSelectedItem() instanceof com.dan.rungholt.scene.Regie.Book b)
                buchInfo.setText("<html><body style='width:215px'>" + esc(b.about()) + String.format(Locale.GERMAN, " · %d:%02d min ohne Warten, %d Fotos", (int) b.length() / 60, (int) b.length() % 60, b.fotos()) + "</body></html>");
        };
        bookBox.addActionListener(e -> { showAbout.run(); view.requestFocusInWindow(); });
        showAbout.run();
        JPanel bookRow = grid(2);
        FButton bookGo = new FButton("Von vorn"), bookStop = new FButton("Anhalten");
        bookGo.setToolTipText("Das Regiebuch von vorn: Datum, Uhrzeit, Wetter, Ansichten, Fahrten; am Ende ein Blatt mit den Fotos in docs/status");
        imKino = new FCheckBox("Im Kino zeigen");
        imKino.setSelected(true);
        imKino.setToolTipText("Ganzer Bildschirm ohne Bedienfeld, Breitwand mit Balken, Vor- und Abspann, Untertitel aus den Stichworten, "
                + "Überblendung bei jedem Schnitt. Esc beendet das Kino (das Regiebuch läuft weiter), Leertaste hält an, F12 fotografiert.");
        bookGo.addActionListener(e -> {
            if (bookBox.getSelectedItem() instanceof com.dan.rungholt.scene.Regie.Book b) { if (imKino.isSelected()) r.kinoStart(b); else r.regieStart(b); }
            view.requestFocusInWindow();
        });
        bookStop.addActionListener(e -> { r.regieStop(); view.requestFocusInWindow(); });
        bookRow.add(bookGo);
        bookRow.add(bookStop);
        put(bookRow);
        put(imKino);
        put(buchInfo);
        put(small("<html><body style='width:215px'>Drei Regiebücher als Text (scene/regie/*.txt): eine Tide, die Grote Mandränke, die Glocken. "
                + "Jedes setzt Datum, Uhr, Zeitraffer und Wetter, wechselt Ansichten und Fahrten, wartet auf die Tide oder die Nacht "
                + "(„warte“) und fotografiert; am Ende liegt ein Blatt mit allen Fotos in docs/status (Regiebuch_….png). "
                + "Im Kino: Esc heraus, Leertaste hält an.</body></html>"));

        // ============================================================== KLANG (Phase 14)
        block("KLANG", false);
        FCheckBox tonBox = new FCheckBox("Ton an (Lautsprecher)");
        tonBox.setToolTipText("Der Klang der Szene aus com.dan.soundscape, in Echtzeit erzeugt: Ohr = Kamera");
        tonBox.addActionListener(e -> { r.klang.setOn(tonBox.isSelected()); tonBox.setSelected(r.klang.on); view.requestFocusInWindow(); });
        put(tonBox);
        put(label("Lautstärke"));
        FSlider volSl = new FSlider(0, 100, 70);
        r.klang.s.volume = 0.7;
        volSl.addChangeListener(e -> r.klang.s.volume = volSl.getValue() / 100.0);
        put(volSl);
        put(klangLbl);
        put(small("<html><body style='width:215px'>Heute: die See nach dem Wasser in der Nähe, das Knistern des trockenfallenden Watts, die Priele "
                + "nach der Strömung, die Wattvögel nach Art, die Seehunde. 1362: Herdfeuer, Rinder und Schafe, die Kaje, die Glocke um 6, 12 "
                + "und 18 Uhr [G]. In der Mandränke Orkan, Brecher, Bruch und Sturmläuten. In Sturmnächten die Glocken aus dem Watt, vom Wind "
                + "getragen: wer westlich der Kirche steht, hört fast nichts.</body></html>"));

        // ============================================================== MANDRÄNKE (Phase 12)
        block("MANDRÄNKE", false);
        put(mandLbl);
        put(label("Bruchstelle"));
        // Einträge als Text (ein eigener Zellenmaler würde den F-Stil der Liste ersetzen); die Wahl über den Index
        com.dan.rungholt.scene.Mandraenke.Stelle[] stellen = com.dan.rungholt.scene.Mandraenke.Stelle.values();
        Object[] stellenText = new Object[stellen.length];
        for (int i = 0; i < stellen.length; i++) stellenText[i] = stellen[i].label;
        FComboBox stelleBox = new FComboBox(stellenText);
        put(stelleBox);
        // Die Nacht in vier Schritten, der Reihe nach von oben nach unten; 2 bis 4 starten die Nacht selbst, wenn sie noch
        // nicht läuft (Befund nach Phase 12: ohne laufende Nacht taten sie nichts, und die Zuordnung war nicht zu erkennen)
        put(small("<html><body style='width:215px'>Die Nacht in vier Schritten, von oben nach unten. Schritt 2 bis 4 starten die Nacht selbst, "
                + "wenn sie noch nicht läuft. Grau heißt: geht jetzt nicht mehr.</body></html>"));
        mStart = new FButton("1 · Abend: 23.1.1362, 22 Uhr");
        mJump = new FButton("2 · Sprung vor den Bruch");
        mNow = new FButton("3 · Deich jetzt brechen");
        mAfter = new FButton("4 · Sprung zum Morgen danach");
        mStop = new FButton("Beenden: zurück in den Sommer");
        mStart.setToolTipText("Beginn der Nacht: 23. Januar 1362, 22 Uhr (julianisch der 15.), Springtide; der Orkan aus WNW zieht auf, gegen 2 Uhr schlagen die Wellen über den Deich, das Hochwasser kommt um 4:36. Baut die Welt von 1362, falls nötig (einige Sekunden)");
        mJump.setToolTipText("Springt in den Überlauf: die Wellen schlagen seit einer halben Stunde über die Krone, die Kamera steht auf dem Deich an der Bruchstelle, die Uhr läuft 1 min/s. Nach rund 20 s bricht der Deich von selbst (dann Echtzeit)");
        mNow.setToolTipText("Bricht den Deich an der Bruchstelle sofort, ohne auf das Ausspülen zu warten (vor dem Überlauf springt die Uhr erst dorthin, damit genug Wasser davor steht); der Bruch läuft in Echtzeit, gut eine Minute");
        mAfter.setToolTipText("Springt zum Morgen des 24. Januar (julianisch des 16.): die Bresche ist offen, die Marsch steht unter Wasser, die meisten Häuser sind fort, die Kirche steht im Wasser");
        mStop.setToolTipText("Deich, Land und Stadt wie vor der Flut, zurück zum 21. Juli 1361");
        // die Wahl gilt sofort, solange der Deich steht; die Kamera geht an die neue Stelle
        stelleBox.addActionListener(e -> {
            r.selectStelle(stellen[Math.max(0, stelleBox.getSelectedIndex())]);
            if (r.mandraenke != null) r.placeBreachView();   // vor dem Start steht das Land von heute: die Kamera bleibt
            view.requestFocusInWindow();
        });
        mStart.addActionListener(e -> nacht(stellen[Math.max(0, stelleBox.getSelectedIndex())], true, null));
        mJump.addActionListener(e -> nacht(stellen[Math.max(0, stelleBox.getSelectedIndex())], false, r::beforeBreach));
        mNow.addActionListener(e -> nacht(stellen[Math.max(0, stelleBox.getSelectedIndex())], false, () -> {
            com.dan.rungholt.scene.Mandraenke m = r.mandraenke;
            if (m != null && m.stage.ordinal() < com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF.ordinal()) r.jumpToOvertop();
            r.breakDikeNow();
            r.placeBreachView();
        }));
        mAfter.addActionListener(e -> nacht(stellen[Math.max(0, stelleBox.getSelectedIndex())], false, r::jumpAfterFlood));
        mStop.addActionListener(e -> { r.stopMandraenke(true); mandButtons(); view.requestFocusInWindow(); });
        for (FButton b : new FButton[]{mStart, mJump, mNow, mAfter, mStop}) { JPanel row = grid(1); row.add(b); put(row); }
        FButton mView = new FButton("Kamera an die Bruchstelle");
        mView.setToolTipText("Kamera auf den Seedeich 90 m neben der Bruchstelle, Blick die Krone entlang: links die See, rechts die Marsch");
        mView.addActionListener(e -> { r.placeBreachView(); view.requestFocusInWindow(); });
        put(mView);
        mandButtons();
        put(small("<html><body style='width:215px'>Die Grote Mandränke als Ereignis aus der Physik (F16 A) [G]: Orkan aus Westnordwest, Windstau bis 3,6 m "
                + "über dem Hochwasser der Nacht; ab einem Meter unter der Krone schlagen die Wellen über den Deich und spülen die Binnenböschung aus. "
                + "Dann wird der Abschnitt zum Körper aus Klei (com.dan.fracture) und bricht in Echtzeit; das Wasser stürzt durch die Bresche "
                + "(com.dan.wave), die Marsch läuft in Stunden voll, Häuser schwimmen auf und treiben, die Leute fliehen auf die Kirchwarft, die "
                + "Kirche steht.</body></html>"));

        // ============================================================== KAMERA
        block("KAMERA", true);
        put(camLbl);
        free.addActionListener(e -> { view.setMode(free.isSelected() ? Camera.Mode.FREI : Camera.Mode.KREISEN); view.requestFocusInWindow(); });
        orbit.addActionListener(e -> { view.setAutoOrbit(orbit.isSelected()); view.requestFocusInWindow(); });
        put(free);
        put(orbit);
        view.setModeListener(m -> javax.swing.SwingUtilities.invokeLater(() -> free.setSelected(m == Camera.Mode.FREI)));
        view.setOrbitListener(b -> javax.swing.SwingUtilities.invokeLater(() -> orbit.setSelected(b)));
        JPanel home = grid(1);
        FButton hb = new FButton("Pos1 Grundansicht");
        hb.addActionListener(e -> { view.goHome(); view.requestFocusInWindow(); });
        home.add(hb);
        put(home);
        sub("Ansichten (Tasten 1 bis 9)");
        JPanel views = new JPanel(new java.awt.GridLayout(0, 2, 6, 6));
        views.setOpaque(false);
        for (Ansichten.Ansicht a : Ansichten.ALLE) {
            FButton b = new FButton(a.key() + " " + a.name());
            boolean ok = Ansichten.ready(a, Renderer.PHASE);
            b.setEnabled(ok);
            b.setToolTipText(Ansichten.describe(a, Renderer.PHASE));
            b.addActionListener(e -> ansicht(a));
            views.add(b);
            view.setKeyAction(java.awt.event.KeyEvent.VK_0 + a.key(), () -> javax.swing.SwingUtilities.invokeLater(() -> ansicht(a)));
        }
        put(views);
        put(small("<html><body style='width:215px'>An echten Orten aus den Höhendaten; „Unter Wasser“ taucht in den Priel (seit Phase 6); 8 und 9 schalten nach 1362 (seit Phase 11).</body></html>"));
        sub("Fahrten");
        JPanel rides = grid(1);
        FButton priel = new FButton("Prielfahrt (mit dem Boot bei Flut)");
        priel.setToolTipText("Fahrt „Prielfahrt“: 1,6 m über dem Wasser den Priel südlich der hohen Watten hinauf nach Nordosten, 12 m/s (heute). Das Boot scheucht Seehunde und Vögel");
        priel.addActionListener(e -> { if (!r.startPrielfahrt()) r.hudMeldung("Prielfahrt: die Überflutung ist noch nicht gerechnet"); view.requestFocusInWindow(); });
        rides.add(priel);
        put(rides);

        // ============================================================== BILD
        block("BILD", false);
        put(label("Qualität"));
        qualBox = new FComboBox(Quality.values());
        qualBox.setSelectedItem(r.quality());
        qualBox.addActionListener(e -> { r.setQuality((Quality) qualBox.getSelectedItem()); view.requestFocusInWindow(); });
        put(qualBox);
        put(qualLbl);
        autoBox.setSelected(r.autoScale);
        autoBox.addActionListener(e -> { r.autoScale = autoBox.isSelected(); if (!r.autoScale) r.renderScale = scaleSl.getValue() / 100.0; view.requestFocusInWindow(); });
        put(autoBox);
        put(scaleLbl);
        put(scaleSl);
        scaleSl.addChangeListener(e -> {
            if (fromScene) return;
            r.autoScale = false;
            autoBox.setSelected(false);
            r.renderScale = scaleSl.getValue() / 100.0;
        });
        put(label("HUD"));
        hudBox = new FComboBox(new Object[]{"kurz", "voll", "aus"});
        hudBox.addActionListener(e -> {
            int i = hudBox.getSelectedIndex();
            r.showHud = i != 2;
            r.hudFull = i == 1;
            view.requestFocusInWindow();
        });
        put(hudBox);
        FCheckBox wasser = new FCheckBox("Meer (com.dan.ocean)"), halme = new FCheckBox("Gras und Binsen (com.dan.reed)"), boden = new FCheckBox("Bodendecke (com.dan.ground)");
        wasser.setSelected(r.showWater);
        halme.setSelected(r.showReed);
        boden.setSelected(r.showGround);
        wasser.addActionListener(e -> { r.showWater = wasser.isSelected(); view.requestFocusInWindow(); });
        halme.addActionListener(e -> { r.showReed = halme.isSelected(); view.requestFocusInWindow(); });
        boden.addActionListener(e -> { r.showGround = boden.isSelected(); view.requestFocusInWindow(); });
        put(wasser);
        put(halme);
        put(boden);
        marks.setSelected(r.showMarks);
        marks.addActionListener(e -> { r.showMarks = marks.isSelected(); view.requestFocusInWindow(); });
        put(marks);
        JPanel pic = grid(2);
        FButton photo = new FButton("Foto (F12)"), shaders = new FButton("Shader neu (F5)");
        photo.addActionListener(e -> { r.photoRequest = true; view.requestFocusInWindow(); });
        shaders.addActionListener(e -> { r.requestShaderReload(); view.requestFocusInWindow(); });
        pic.add(photo); pic.add(shaders);
        put(pic);

        // ============================================================== CHRONIK und DATEN (Phase 16)
        chronikBlock();
        datenBlock();

        // ============================================================== TECHNIK
        block("TECHNIK", false);
        put(perfLbl);
        // Phase 17: der Dauerlauf (Abnahme „fertig“)
        JPanel dlRow = grid(2);
        FButton dlGo = new FButton("Dauerlauf 30 min"), dlStop = new FButton("Abbrechen");
        dlGo.setToolTipText("Runden aus „Eine Tide“ im Kino, zwei Tiden frei im Zeitraffer 360 und „Die Grote Mandränke“ im Kino, mindestens zwei, "
                + "sonst bis 30 Minuten um sind; am Ende Bericht und Kurven in docs/status (Dauerlauf_….png und .txt)");
        dlGo.addActionListener(e -> {
            boolean ok = com.dan.rungholt.app.Dauerlauf.starten(r, 30, f -> r.hudMeldung("Dauerlauf fertig: " + (f != null ? f.getName() : "ohne Bericht")));
            r.hudMeldung(ok ? "Dauerlauf beginnt (mindestens zwei Runden, 30 min)" : "Ein Dauerlauf läuft schon");
            view.requestFocusInWindow();
        });
        dlStop.addActionListener(e -> com.dan.rungholt.app.Dauerlauf.anhalten());
        dlRow.add(dlGo);
        dlRow.add(dlStop);
        put(dlRow);
        put(computeLbl);
        put(glLbl);
        put(physLbl);
        sub("Packages");
        put(pkgLbl);
        sub("Verdrahtung");
        put(linksLbl);
        sub("Alle Zeilen des HUD");
        put(hudLbl);

        for (Object[] b : bloecke) ((FCollapsiblePanel) b[0]).setBodyComponent((JComponent) b[1]);
        for (FCollapsiblePanel p : zu) p.setExpanded(false, false);
        add(Box.createVerticalGlue());
        view.setKeyAction(java.awt.event.KeyEvent.VK_F1, () -> javax.swing.SwingUtilities.invokeLater(this::tasten));
        view.setKeyAction(java.awt.event.KeyEvent.VK_SPACE, () -> javax.swing.SwingUtilities.invokeLater(() ->
                lapseBox.setSelectedIndex(clock.lapse == 0 ? 1 : 0)));
        syncFromScene();
        new Timer(500, e -> {
            // Befund wie Glacier Phase 3: solange eine Auswahlliste offen ist, nichts auffrischen; neue Texte ändern die
            // Höhe der Zeilen, der Block legt sich neu aus, und Swing schließt die Liste, sobald sich ihr Feld bewegt
            if (popupOpen(this) || menuOpen()) return;
            syncFromScene();
            if (ticks++ % 2 == 0) { technik(); datenZeilen(); }
            if (tidenkurve != null && tidenkurve.isShowing()) tidenkurve.repaint();
            nachmessen();
        }).start();
    }

    // ============================================================== Phase 16: CHRONIK und DATEN

    private Tidenkurve tidenkurve;
    private com.dan.rungholt.data.Chronik chronik;
    private final JLabel chronikLbl = small(""), heuteLbl = small(""), datenLbl = small(""), presetLbl = small(""), protLbl = small(""), gemerktLbl = small(""),
            tideSrcLbl = small(""), flutLbl = small("");
    private FComboBox tidenBox, paketBox, presetBox, sitzungBox, gemerktBox, flutBox;
    private java.util.List<com.dan.rungholt.data.Daten.PaketPreset> presetListe = java.util.List.of();
    private java.util.List<com.dan.rungholt.data.Daten.Ansicht> gemerktListe = java.util.List.of();
    private volatile String datenNote = "";
    private long naechsteListe;

    /** Phase 16: die Chronik der laufenden Szene (aus der App, sobald sie steht). */
    public void chronik(com.dan.rungholt.data.Chronik c) {
        chronik = c;
        neuLesen();
    }

    private static com.dan.rungholt.data.Speicher sp() { return com.dan.rungholt.data.Datenseite.speicher(); }

    /** Eine Combo neu füllen, ohne dass ihr Hörer feuert; die Auswahl bleibt, wenn es den Eintrag noch gibt. */
    private void fuellen(FComboBox box, java.util.List<?> l, String wahl) {
        fromScene = true;
        try {
            box.removeAllItems();
            int k = -1;
            for (int i = 0; i < l.size(); i++) { box.addItem(l.get(i)); if (l.get(i).toString().equals(wahl)) k = i; }
            if (k >= 0) box.setSelectedIndex(k); else if (!l.isEmpty()) box.setSelectedIndex(0);
        } finally { fromScene = false; }
    }

    private static String gewaehlt(FComboBox box) { Object o = box.getSelectedItem(); return o == null ? null : o.toString(); }

    /** Im Faden der Daten arbeiten, danach auf dem Swing-Faden weiter; Fehler landen im Protokoll und in einer Meldung. */
    private <T> void hinten(String was, java.util.concurrent.Callable<T> arbeit, java.util.function.Consumer<T> danach) {
        com.dan.store.Dienst.spaeter(() -> {
            try {
                T t = arbeit.call();
                javax.swing.SwingUtilities.invokeLater(() -> danach.accept(t));
            } catch (Exception ex) {
                com.dan.store.Protokoll.fehler("Bedienfeld", was + ": " + ex.getMessage(), ex instanceof RuntimeException ? ex : null);
                javax.swing.SwingUtilities.invokeLater(() -> com.dan.foptionpane.FOptionPane.showMessageDialog(this, was + " ging nicht:\n" + ex.getMessage(),
                        "Rungholt · " + was, com.dan.foptionpane.FOptionPane.ERROR_MESSAGE));
            }
        });
    }

    /** Eine Tide der Chronik in der Auswahl. */
    private record Tidenzeile(com.dan.rungholt.data.Daten.Tide t) {
        @Override public String toString() {
            return (t.art().equals("HW") ? "Hochwasser " : "Niedrigwasser ") + com.dan.rungholt.data.Daten.text(t.ms())
                    + String.format(Locale.GERMAN, " · %+.2f m%s", t.pegelM(), Math.abs(t.windstauM()) > 0.05 ? String.format(Locale.GERMAN, " (Windstau %+.2f)", t.windstauM()) : "")
                    + (Double.isNaN(t.trockenAnteil()) ? "" : String.format(Locale.GERMAN, " · %.0f %% trocken", t.trockenAnteil() * 100));
        }
    }

    /** Eine Sitzung mit Chronik in der Auswahl. */
    private record Sitzungszeile(long id, long beginn, long tiden, long zaehlungen) {
        @Override public String toString() {
            String w = beginn <= 0 ? "" : java.time.format.DateTimeFormatter.ofPattern("dd.MM. HH:mm").format(java.time.Instant.ofEpochMilli(beginn).atZone(java.time.ZoneId.systemDefault()));
            return "Sitzung " + id + " · " + w + " · " + tiden + " Tiden, " + zaehlungen + " Zählungen" + (id == com.dan.store.Dienst.sitzung() ? " (diese)" : "");
        }
    }

    /** Block CHRONIK (F36 A): Zähler, Tiden des Tages, letzte Tiden (Uhr dorthin), Tageskarte, Chronik an/aus, leeren. */
    private void chronikBlock() {
        block("CHRONIK", false);
        put(chronikLbl);
        put(heuteLbl);
        sub("Letzte Tiden der Chronik");
        tidenBox = new FComboBox();
        put(tidenBox);
        JPanel g1 = grid(2);
        FButton hin = new FButton("Uhr dorthin"), karte = new FButton("Tageskarte");
        hin.setToolTipText("Die Uhr springt auf das gewählte Hoch- oder Niedrigwasser");
        hin.addActionListener(e -> {
            if (tidenBox.getSelectedItem() instanceof Tidenzeile z) { clock.setEpochMillis(z.t().ms()); r.hudMeldung("Uhr: " + z); }
            view.requestFocusInWindow();
        });
        karte.setToolTipText("Draufsicht des Szenentags als PNG nach docs/status: Rungholt, Fundstellen, die nasse Fläche bei Niedrigwasser, Zählungen, Bruch (wie RH_TAGESKARTE_V in QGIS)");
        karte.addActionListener(e -> {
            java.time.LocalDate tag = clock.date();
            if (chronik != null) chronik.warten();
            hinten("Tageskarte", () -> com.dan.rungholt.data.Tageskarte.zeichnen(sp(), r.terrain, tag, r.photoDir != null ? r.photoDir : new java.io.File("docs/status")), k -> {
                r.hudMeldung("Tageskarte: " + k.datei().getPath());
                txt(heuteLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Tageskarte %s: %d Tiden%s, %d Zählungen, %d Fundstellen · %s</body></html>",
                        tag, k.tiden(), k.wasserlinie() ? " mit Wasserlinie" : "", k.zaehlungen(), k.fundstellen(), k.datei().getName()));
            });
        });
        g1.add(hin);
        g1.add(karte);
        put(g1);
        JPanel g2 = grid(1);
        FButton schreib = new FButton("Chronik aus");
        schreib.setToolTipText("Die Chronik anhalten oder wieder schreiben lassen (die Szene läuft weiter)");
        schreib.addActionListener(e -> {
            if (chronik == null) return;
            chronik.schreiben(!chronik.schreibt());
            schreib.setText(chronik.schreibt() ? "Chronik aus" : "Chronik an");
        });
        g2.add(schreib);
        put(g2);
        sub("Chronik leeren (eigene Zeilen einer Sitzung)");
        sitzungBox = new FComboBox();
        put(sitzungBox);
        JPanel g3 = grid(1);
        FButton leeren = new FButton("Leeren …");
        leeren.addActionListener(e -> {
            if (!(sitzungBox.getSelectedItem() instanceof Sitzungszeile s)) return;
            if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, "Alle Tiden, Zählungen, Läufe der Mandränke, Glocken und Läufe der Regiebücher der Sitzung "
                    + s.id + " löschen?\n" + s, "Rungholt · Chronik leeren", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) return;
            hinten("Chronik leeren", () -> sp().chronikLeeren(s.id), n -> { r.hudMeldung("Chronik der Sitzung " + s.id + ": " + n + " Zeilen gelöscht"); neuLesen(); });
        });
        g3.add(leeren);
        put(g3);
        put(small("<html><body style='width:215px'>Die Chronik schreibt jedes Hoch- und Niedrigwasser am Rand (auch im Zeitraffer), bei Niedrigwasser die nasse Fläche "
                + "als Mehrfachfläche (64 m, höchstens alle 30 s) und die Zählung der Seehunde und Vögel, jeden Lauf der Mandränke mit Bruchstelle und Bresche, jedes "
                + "Läuten der Glocken und jeden Lauf eines Regiebuchs. Ein Klick ins Watt zeigt die Zellkarte, Umschalt + Klick legt eine eigene Fundstelle an. "
                + "In QGIS: Ebene RH_TAGESKARTE_V (Geometrie GEOM, Schlüssel OBJEKT_ID), Filter auf TAG; ohne Datenbank daten/chronik/*.csv mit Spalte WKT.</body></html>"));
    }

    /** Block DATEN: Speicherort, Verbinden, Einrichten, Prüfen, Weiter wie zuletzt, Übernahme, Sturmfluten, Presets, gemerkte Ansichten, Protokoll. */
    private void datenBlock() {
        block("DATEN", false);
        put(datenLbl);
        JPanel g = new JPanel(new java.awt.GridLayout(0, 2, 6, 4));
        g.setOpaque(false);
        FButton verb = new FButton("Verbinden …"), einr = new FButton("Einrichten"), pruef = new FButton("Prüfen"), ohne = new FButton("Ohne Datenbank"),
                weiter = new FButton("Weiter wie zuletzt"), ueber = new FButton("Übernehmen");
        verb.setToolTipText("Mit dem Schema aus db/db.properties verbinden (fragt nach dem Passwort, wenn keins hinterlegt ist)");
        verb.addActionListener(e -> verbinden());
        einr.setToolTipText("Die Skripte aus db/sql ausführen und alles prüfen; löscht nie etwas");
        einr.addActionListener(e -> einrichter(false));
        pruef.setToolTipText("Nur prüfen: Tabellen, Spalten, Sichten, Metadaten, räumliche Indizes, Zahl der Startdaten");
        pruef.addActionListener(e -> einrichter(true));
        ohne.setToolTipText("Mit Dateien in daten/ weiterarbeiten");
        ohne.addActionListener(e -> {
            com.dan.rungholt.data.Datenseite.setzen(new com.dan.rungholt.data.DateiSpeicher(com.dan.rungholt.data.DateiSpeicher.ordner()));
            neuLesen();
        });
        weiter.setToolTipText("Die letzte gesicherte Sitzung herstellen: Uhr (auch 1362), Ära, Wetter, Pegel von Hand, Kamera, Regler");
        weiter.addActionListener(e -> weiterWieZuletzt());
        ueber.setToolTipText("Chronik, eigene Fundstellen, Regiebücher, Ansichten und Presets aus daten/ in die Datenbank (die CSV heißen danach *.uebernommen)");
        ueber.addActionListener(e -> uebernehmen());
        g.add(verb); g.add(ohne); g.add(einr); g.add(pruef); g.add(weiter); g.add(ueber);
        put(g);
        sub("Sturmfluten an dieser Küste (belegt)");
        flutBox = new FComboBox();
        flutBox.addActionListener(e -> {
            if (fromScene) return;
            if (flutBox.getSelectedItem() instanceof com.dan.rungholt.data.Daten.Sturmflut s)
                txt(flutLbl, "<html><body style='width:215px'>" + esc(s.name()) + " · " + esc(s.datumText()) + " (" + s.kalender().toLowerCase(Locale.GERMAN) + ")"
                        + (s.scheitelCm() == null ? " · Scheitel Husum nicht gemessen" : String.format(Locale.GERMAN, " · Scheitel Husum %.2f m über %s", s.scheitelCm() / 100.0, s.bezug()))
                        + "<br>" + esc(s.folgen() == null ? "" : s.folgen()) + (s.quelle() == null ? "" : " [" + s.quelle() + "]") + "</body></html>");
        });
        put(flutBox);
        put(flutLbl);
        sub("Presets der Packages");
        java.util.List<String> pk = new java.util.ArrayList<>();
        for (String k : com.dan.rungholt.data.Startdaten.PAKETE) pk.add(k.substring(k.lastIndexOf('.') + 1) + " · " + k.substring(0, k.lastIndexOf('.')));
        paketBox = new FComboBox(pk.toArray());
        presetBox = new FComboBox();
        paketBox.addActionListener(e -> { if (!fromScene) listePresets(null); });
        put(paketBox);
        put(presetBox);
        FButton an = new FButton("Anwenden"), unter = new FButton("Speichern unter …");
        an.setToolTipText("Alle Felder des Presets in das laufende Objekt der Szene schreiben");
        an.addActionListener(e -> presetAnwenden());
        unter.setToolTipText("Die Felder, wie sie jetzt in der Szene stehen, als eigenes Preset");
        unter.addActionListener(e -> presetSpeichern());
        JPanel pg = grid(2);
        pg.add(an);
        pg.add(unter);
        put(pg);
        put(presetLbl);
        sub("Gemerkte Ansichten (Taste V merkt)");
        gemerktBox = new FComboBox();
        put(gemerktBox);
        FButton gh = new FButton("Hin"), gl = new FButton("Löschen");
        gh.addActionListener(e -> gemerkteAnsicht());
        gl.addActionListener(e -> {
            int i = gemerktBox.getSelectedIndex();
            if (i < 0 || i >= gemerktListe.size()) return;
            long id = gemerktListe.get(i).id();
            hinten("Ansicht löschen", () -> { sp().loeschenAnsicht(id); return null; }, x -> listeGemerkt());
        });
        JPanel gg = grid(2);
        gg.add(gh);
        gg.add(gl);
        put(gg);
        put(gemerktLbl);
        view.setKeyAction(java.awt.event.KeyEvent.VK_V, () -> javax.swing.SwingUtilities.invokeLater(this::ansichtMerken));
        sub("Protokoll");
        put(protLbl);
        put(small("<html><body style='width:215px'>Das Protokoll geht alle 5 s in RH_LOG (90 Tage) oder daten/protokoll.txt, der Zustand alle 2 min und beim Ende in "
                + "RH_SITZUNG oder daten/sitzungen. Schema RUNGHOLT auf PDBORCL, SRID 8307. In QGIS über dem Satellitenbild: RH_WARFT_V (Kronen), RH_RUNGHOLT_V "
                + "(Warften, Bauwerke, Fundstellen), RH_TAGESKARTE_V.</body></html>"));
    }

    private String paket() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(s.indexOf(" · ") + 3); }
    private String klasse() { String s = gewaehlt(paketBox); return s == null ? null : s.substring(0, s.indexOf(" · ")); }

    private void listePresets(String wahl) {
        String p = paket(), k = klasse();
        if (p == null) return;
        hinten("Presets lesen", () -> sp().presets(p, k), l -> {
            presetListe = l;
            fuellen(presetBox, l, wahl);
            Object o = com.dan.rungholt.data.Zustand.objekt(r, k);
            txt(presetLbl, "<html><body style='width:215px'>" + l.size() + " Presets · " + (o == null ? "nicht in der Szene" : "in der Szene: " + o.getClass().getSimpleName()) + "</body></html>");
        });
    }

    private void presetAnwenden() {
        int i = presetBox.getSelectedIndex();
        if (i < 0 || i >= presetListe.size()) return;
        var pr = presetListe.get(i);
        Object o = com.dan.rungholt.data.Zustand.objekt(r, pr.klasse());
        if (o == null) { txt(presetLbl, pr.klasse() + " läuft nicht in der Szene."); return; }
        java.util.List<String> f = com.dan.store.Felder.schreiben(o, pr.felder());
        txt(presetLbl, "<html><body style='width:215px'>„" + esc(pr.name()) + "“ angewandt: " + (pr.felder().size() - f.size()) + " Felder"
                + (f.isEmpty() ? "" : ", nicht: " + esc(String.join(", ", f))) + "</body></html>");
        com.dan.store.Protokoll.info("Preset", pr.klasse() + " ← „" + pr.name() + "“");
        view.requestFocusInWindow();
    }

    private void presetSpeichern() {
        String p = paket(), k = klasse();
        Object o = com.dan.rungholt.data.Zustand.objekt(r, k);
        if (o == null) { txt(presetLbl, k + " läuft nicht in der Szene."); return; }
        String n = com.dan.foptionpane.FOptionPane.showInputDialog(this, "Name des Presets für " + k, "Rungholt · Preset speichern", com.dan.foptionpane.FOptionPane.QUESTION_MESSAGE);
        if (n == null || n.isBlank()) return;
        var pr = new com.dan.rungholt.data.Daten.PaketPreset(p, k, n.strip(), "Aus der Szene am " + java.time.LocalDate.now(), com.dan.rungholt.data.Daten.EIGEN,
                com.dan.store.Felder.lesen(o));
        hinten("Preset speichern", () -> { sp().speichern(pr); return null; }, x -> listePresets(pr.name()));
    }

    private void listeGemerkt() {
        hinten("Ansichten lesen", () -> sp().ansichten(), l -> {
            java.util.List<com.dan.rungholt.data.Daten.Ansicht> g = new java.util.ArrayList<>();
            for (var a : l) if ("GEMERKT".equals(a.art())) g.add(a);
            gemerktListe = g;
            fuellen(gemerktBox, g, null);
            txt(gemerktLbl, g.size() + " gemerkte Ansichten in " + sp().wo());
        });
    }

    private void ansichtMerken() {
        double[] k = com.dan.rungholt.data.Zustand.kamera(r);
        long ms = (long) clock.epochMillis();
        boolean alt = wiring.era.is1362();
        String name = (alt ? "1362" : "heute") + " · " + com.dan.rungholt.data.Daten.text(ms);
        String z = com.dan.store.Felder.alsText(com.dan.rungholt.data.Zustand.erfassen(r), "Rungholt · gemerkte Ansicht");
        boolean nass = k[1] < wiring.level.surfaceAt(k[0], k[2]);
        r.grabRequest = img -> {
            byte[] png = null;
            try {
                java.awt.image.BufferedImage s = new java.awt.image.BufferedImage(320, (int) Math.round(320.0 * img.getHeight() / img.getWidth()), java.awt.image.BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g2 = s.createGraphics();
                g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(img, 0, 0, s.getWidth(), s.getHeight(), null);
                g2.dispose();
                java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(s, "png", bo);
                png = bo.toByteArray();
            } catch (Exception ex) { /* ohne Vorschau */ }
            byte[] v = png;
            var a = new com.dan.rungholt.data.Daten.Ansicht(0, name, null, "GEMERKT", k, null, alt ? com.dan.rungholt.data.Daten.A1362 : com.dan.rungholt.data.Daten.HEUTE,
                    nass, ms, z, v, com.dan.rungholt.data.Daten.EIGEN);
            long s = com.dan.store.Dienst.sitzung();
            hinten("Ansicht merken", () -> sp().speichern(a, s > 0 ? s : null), id -> { r.hudMeldung("Ansicht gemerkt: " + name); listeGemerkt(); });
        };
    }

    private void gemerkteAnsicht() {
        int i = gemerktBox.getSelectedIndex();
        if (i < 0 || i >= gemerktListe.size()) return;
        long id = gemerktListe.get(i).id();
        hinten("Ansicht lesen", () -> sp().ansicht(id), a -> {
            if (a.zustand() != null) {
                java.util.List<String> f = com.dan.rungholt.data.Zustand.anwenden(r, com.dan.store.Felder.ausText(a.zustand()));
                if (!f.isEmpty()) com.dan.store.Protokoll.warnung("Ansicht", "nicht hergestellt: " + String.join(", ", f));
            } else {
                double[] k = a.kamera();
                r.camera.stopFahrt();
                r.camera.stopMitfahrt();
                r.camera.mode = Camera.Mode.FREI;
                r.camera.place(k[0], k[1], k[2], k[3], k[4]);
                r.camera.snap();
                if (a.szeneMs() != null) clock.setEpochMillis(a.szeneMs());
            }
            view.setMode(r.camera.mode);
            syncFromScene();
            view.requestFocusInWindow();
        });
    }

    /** Nach einem Wechsel des Speichers alle Listen neu lesen. */
    private void neuLesen() {
        if (paketBox == null) return;
        listePresets(null);
        listeGemerkt();
        hinten("Sturmfluten lesen", () -> sp().sturmfluten(), l -> fuellen(flutBox, l, gewaehlt(flutBox)));
        naechsteListe = 0;
        datenZeilen();
    }

    private void verbinden() {
        datenNote = "verbinde …";
        hinten("Verbinden", com.dan.rungholt.data.Verbindung::konfig, k -> {
            com.dan.store.Db.Konfig kk = k;
            char[] neu = null;
            if (!k.hatPasswort()) {
                com.dan.ftextfield.FPasswordfield pw = new com.dan.ftextfield.FPasswordfield();
                JPanel p = new JPanel(new BorderLayout(0, 6));
                p.add(new JLabel("Passwort für " + k.kurz() + ":"), BorderLayout.NORTH);
                p.add(pw, BorderLayout.CENTER);
                if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, p, "Rungholt · Datenbank", com.dan.foptionpane.FOptionPane.OK_CANCEL_OPTION)
                        != com.dan.foptionpane.FOptionPane.OK_OPTION || pw.getPassword().length == 0) { datenNote = ""; return; }
                neu = pw.getPassword();
                kk = k.mitPasswort(neu);
            }
            char[] eingabe = neu;
            com.dan.store.Db.Konfig kf = kk;
            hinten("Verbinden", () -> {
                String[] grund = {""};
                com.dan.rungholt.data.DbSpeicher d = com.dan.rungholt.data.Datenseite.verbinden(kf, grund);
                if (d == null) throw new com.dan.store.Ablage.Fehler(grund[0]);
                if (eingabe != null) try { com.dan.store.Db.passwortSpeichern(kf, eingabe); } catch (Exception ex) { com.dan.store.Protokoll.warnung("Daten", "Passwort nicht eingetragen: " + ex.getMessage()); }
                int st = com.dan.rungholt.data.Verbindung.einrichter().stufe(d.verbindung());
                return new Object[]{d, st};
            }, o -> {
                var d = (com.dan.rungholt.data.DbSpeicher) o[0];
                int st = (Integer) o[1];
                if (st < com.dan.rungholt.data.Verbindung.VERSION) {
                    if (com.dan.foptionpane.FOptionPane.showConfirmDialog(this, d.wo() + " ist noch nicht eingerichtet (Stufe " + st + ").\nJetzt einrichten? (löscht nie etwas)",
                            "Rungholt · Datenbank", com.dan.foptionpane.FOptionPane.YES_NO_OPTION) != com.dan.foptionpane.FOptionPane.YES_OPTION) { d.schliessen(); datenNote = "nicht eingerichtet"; return; }
                    com.dan.rungholt.data.Datenseite.setzen(d);
                    einrichter(false);
                    return;
                }
                com.dan.rungholt.data.Datenseite.setzen(d);
                datenNote = "verbunden";
                com.dan.store.Dienst.spaeter(() -> com.dan.rungholt.data.Pegel.laden(wiring, d));
                neuLesen();
            });
        });
    }

    private void einrichter(boolean nurPruefen) {
        if (!(sp() instanceof com.dan.rungholt.data.DbSpeicher d)) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Rungholt · Einrichter", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        datenNote = nurPruefen ? "prüfe …" : "richte ein …";
        hinten(nurPruefen ? "Prüfen" : "Einrichten", () -> {
            var e = com.dan.rungholt.data.Verbindung.einrichter();
            return nurPruefen ? e.pruefen(d.verbindung(), com.dan.rungholt.data.Verbindung.skripte())
                    : e.einrichten(d.verbindung(), com.dan.rungholt.data.Verbindung.skripte(), (t, a) -> datenNote = String.format(Locale.GERMAN, "%.0f %% · %s", a * 100, t));
        }, er -> {
            Startbild.merkeEinrichter(er.kurz());
            datenNote = "";
            if (er.ok()) {
                com.dan.store.Dienst.spaeter(com.dan.store.Dienst::neuBeginnen);
                com.dan.store.Dienst.spaeter(() -> com.dan.rungholt.data.Pegel.laden(wiring, d));
            }
            com.dan.store.Protokoll.info("Einrichter", (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz());
            for (String z : er.zeilen()) if (!z.startsWith("ok") && !z.contains("alles in Ordnung")) com.dan.store.Protokoll.warnung("Einrichter", z);
            JLabel t = small("<html><body style='width:560px'>" + String.join("<br>", er.zeilen()) + "</body></html>");
            javax.swing.JScrollPane spn = new javax.swing.JScrollPane(t);
            spn.setPreferredSize(new Dimension(640, 420));
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, spn, "Rungholt · " + (nurPruefen ? "Prüfen: " : "Einrichten: ") + er.kurz(),
                    er.ok() ? com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE : com.dan.foptionpane.FOptionPane.WARNING_MESSAGE);
            neuLesen();
        });
    }

    private void uebernehmen() {
        if (!sp().datenbank()) {
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Erst mit der Datenbank verbinden („Verbinden …“).", "Rungholt · Übernehmen", com.dan.foptionpane.FOptionPane.ERROR_MESSAGE);
            return;
        }
        var von = new com.dan.rungholt.data.DateiSpeicher(com.dan.rungholt.data.DateiSpeicher.ordner());
        datenNote = "übernehme …";
        hinten("Übernehmen", () -> com.dan.rungholt.data.Uebernahme.uebernehmen(von, sp()), z -> {
            datenNote = "übernommen";
            com.dan.foptionpane.FOptionPane.showMessageDialog(this, "Aus " + von.wo() + " nach " + sp().wo() + ":\n" + z, "Rungholt · Übernahme",
                    com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
            neuLesen();
        });
    }

    private void weiterWieZuletzt() {
        hinten("Weiter wie zuletzt", () -> {
            var s = sp().letzteSitzung(com.dan.store.Dienst.sitzung());
            if (s == null || s.zustand() == null) throw new com.dan.store.Ablage.Fehler("Keine gesicherte Sitzung in " + sp().wo());
            return s;
        }, s -> { Weiter.jetzt(r, s); view.setMode(r.camera.mode); syncFromScene(); });
    }

    /** Jede Sekunde: Zeilen von CHRONIK und DATEN; alle 10 s die Listen aus dem Speicher. */
    private void datenZeilen() {
        if (chronikLbl == null || tidenBox == null) return;
        var c = chronik;
        txt(tideSrcLbl, "<html><body style='width:215px'>Partialtiden: " + esc(com.dan.rungholt.data.Datenseite.tideHerkunft) + "</body></html>");
        txt(chronikLbl, "<html><body style='width:215px'>" + esc(c == null ? "Chronik: startet mit der Szene" : c.describe()) + " · " + esc(sp().wo()) + "</body></html>");
        long gs = com.dan.store.Dienst.gesichert();
        txt(datenLbl, "<html><body style='width:215px'>" + esc(sp().wo()) + (datenNote.isEmpty() ? "" : " · " + esc(datenNote))
                + "<br>Sitzung " + com.dan.store.Dienst.sitzung() + (gs > 0 ? ", gesichert " + String.format("%tR", gs) + " Uhr (Rechner)" : ", noch nicht gesichert")
                + (com.dan.rungholt.data.Datenseite.einrichterStand.isEmpty() ? "" : "<br>Einrichter: " + esc(com.dan.rungholt.data.Datenseite.einrichterStand))
                + (com.dan.store.Dienst.letzterFehler().isEmpty() ? "" : "<br>Zuletzt: " + esc(com.dan.store.Dienst.letzterFehler())) + "</body></html>");
        long now = System.currentTimeMillis();
        if (now < naechsteListe) return;
        naechsteListe = now + 10_000;
        java.time.LocalDate tag = clock.date();
        com.dan.store.Dienst.spaeter(() -> {
            try {
                var sp = sp();
                var heute = sp.tidenAm(tag);
                var zs = sp.zaehlungenAm(tag);
                var letzte = sp.tiden(12);
                var sitz = sp.sitzungenMitChronik(12);
                var prot = sp.letzteZeilen(6);
                int seehunde = 0, voegel = 0;
                for (var z : zs) if (z.art().equals("SEEHUND")) seehunde += z.anzahl(); else voegel += z.anzahl();
                StringBuilder tl = new StringBuilder();
                for (var t : heute) tl.append(tl.length() > 0 ? ", " : "").append(t.art()).append(' ').append(hm(t.ms()))
                        .append(String.format(Locale.GERMAN, " %+.2f m", t.pegelM()));
                String h = String.format(Locale.GERMAN, "<html><body style='width:215px'>Heute (%s): %s%s</body></html>", tag,
                        heute.isEmpty() ? "noch keine Tide in der Chronik" : esc(tl.toString()),
                        zs.isEmpty() ? "" : String.format(Locale.GERMAN, " · gezählt %d Seehunde, %d Wattvögel", seehunde, voegel));
                java.util.List<Tidenzeile> tz = new java.util.ArrayList<>();
                for (var t : letzte) tz.add(new Tidenzeile(t));
                java.util.List<Sitzungszeile> sl = new java.util.ArrayList<>();
                for (long[] s : sitz) sl.add(new Sitzungszeile(s[0], s[1], s[2], s[3]));
                StringBuilder pb = new StringBuilder("<html><body style='width:215px'>");
                for (String z : prot) pb.append(esc(z.length() > 140 ? z.substring(0, 140) + " …" : z)).append("<br>");
                String ps = pb.append("</body></html>").toString();
                javax.swing.SwingUtilities.invokeLater(() -> {
                    if (popupOpen(this) || menuOpen()) return;
                    if (!heuteLbl.getText().startsWith("<html><body style='width:215px'>Tageskarte")) txt(heuteLbl, h);
                    fuellen(tidenBox, tz, gewaehlt(tidenBox));
                    fuellen(sitzungBox, sl, gewaehlt(sitzungBox));
                    txt(protLbl, ps);
                });
            } catch (Exception ex) {
                javax.swing.SwingUtilities.invokeLater(() -> txt(heuteLbl, "Chronik nicht lesbar: " + ex.getMessage()));
            }
        });
    }

    private final java.util.Map<FCollapsiblePanel, Integer> hoehe = new java.util.HashMap<>();

    /**
     * Wie Glacier: FCollapsiblePanel merkt sich die Höhe des Inhalts beim Aufklappen. Wachsen danach Zeilen (Regie,
     * Himmel), schneidet der Block unten ab. Darum alle halbe Sekunde nachmessen und offene Blöcke neu setzen.
     */
    private void nachmessen() {
        boolean changed = false;
        for (Object[] b : bloecke) {
            FCollapsiblePanel p = (FCollapsiblePanel) b[0];
            if (!p.isExpanded()) { hoehe.remove(p); continue; }
            JComponent body = (JComponent) b[1];
            body.invalidate();
            int h = body.getPreferredSize().height;
            Integer old = hoehe.put(p, h);
            if (old == null || old != h) { p.setExpanded(true, false); changed = true; }
        }
        if (changed) { revalidate(); repaint(); }
    }

    /** Ist irgendwo im Bedienfeld eine Auswahlliste aufgeklappt? */
    private static boolean popupOpen(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JComboBox<?> b && b.isPopupVisible()) return true;
            if (k instanceof java.awt.Container cc && popupOpen(cc)) return true;
        }
        return false;
    }

    /** Ist irgendein Aufklappmenü offen (auch die Liste einer Auswahl)? Dann darf sich nichts neu auslegen. */
    public static boolean menuOpen() { return javax.swing.MenuSelectionManager.defaultManager().getSelectedPath().length > 0; }

    /** Text nur setzen, wenn er sich ändert (sonst legt Swing den Block bei jedem Takt neu aus). */
    private static void txt(JLabel l, String t) { if (!t.equals(l.getText())) l.setText(t); }

    /** Phase 4: die Uhr zum nächsten Hoch- oder Niedrigwasser an Südfall (Husum − 5 min) stellen. */
    private void jumpTide(boolean high) {
        double h = wiring.level.hours() + 0.1;
        com.dan.tide.Tide.Turn t = wiring.level.husum.next(h + 5 / 60.0, high);
        if (t != null) {
            clock.setEpochMillis((t.hours() - 5 / 60.0) * 3_600_000);
            r.readapt();
        }
        syncFromScene();
        view.requestFocusInWindow();
    }

    /** Phase 3: Regie wählen: von Hand oder freie Regie ab einer Lage. */
    private void regie(int i) {
        if (i <= 0) { wiring.regie = false; return; }
        wiring.regie = true;
        wiring.startFree(REGIE_PHASE[Math.min(REGIE_PHASE.length - 1, i)]);
        r.readapt();
        syncFromScene();
    }

    /** Ein Handregler: mit Regie überschreibt er die Größe, ohne setzt er das Feld der Verdrahtung. */
    private void hand(com.dan.weather.Weather.Var v, double value) {
        if (wiring.regie) { wiring.hand(v, value); return; }
        switch (v) {
            case COVER -> wiring.oktas = value;
            case RAIN -> wiring.rainRate = value;
            case FOG -> wiring.fogAmount = value;
            case VISIBILITY -> wiring.visibilityKm = value;
            case THUNDER -> wiring.thunderstorm = com.dan.lightning.Lightning.Level.values()[(int) Math.max(0, Math.min(4, value))];
            default -> { }
        }
    }

    /** Reglerstellung 0..200 → mm/h (0 bis 40, unten fein). */
    static double rainMm(int v) { return v <= 0 ? 0 : 40 * Math.pow(v / 200.0, 2.2); }
    static int rainPos(double mm) { return mm <= 0 ? 0 : (int) Math.round(200 * Math.pow(Math.min(40, mm) / 40, 1 / 2.2)); }

    /** Ein Schalter für eine Ebene des Bildes. */
    private void layer(JPanel p, String name, boolean on, java.util.function.Consumer<Boolean> set) {
        FCheckBox b = new FCheckBox(name);
        b.setSelected(on);
        b.addActionListener(e -> { set.accept(b.isSelected()); view.requestFocusInWindow(); });
        p.add(b);
    }

    private void ansicht(Ansichten.Ansicht a) {
        if (r.placeView(a)) {
            free.setSelected(r.camera.mode == Camera.Mode.FREI);
            orbit.setSelected(false);
        }
        view.requestFocusInWindow();
    }

    private void tasten() {
        StringBuilder an = new StringBuilder();
        for (Ansichten.Ansicht a : Ansichten.ALLE) an.append(an.length() > 0 ? ", " : "").append(a.key()).append(' ').append(a.name());
        com.dan.foptionpane.FOptionPane.showMessageDialog(this, "<html><body style='width:420px'>"
                + "<b>Szene</b><br>Links ziehen: drehen · rechts ziehen: verschieben · Rad, Bild↑/↓, +/−: zoomen<br>"
                + "Pfeile: drehen · W A S D: gleiten · Q/E: ab und auf (frei) · Umschalt: schneller<br>"
                + "F: frei fliegen · R: von selbst kreisen · Pos1: Grundansicht · H: HUD kurz, voll, aus<br>"
                + "Z: Zeitreise Heute · 1362 · Leertaste: Uhr anhalten und weiter · F12: Foto nach docs/status · F5: Shader neu laden<br>"
                + "Ansichten: " + an + "<br><br>"
                + "<b>Fenster</b><br>F1: diese Übersicht · F11: nur die Szene (Bedienfeld und Statuszeile weg und wieder da)</body></html>",
                "Rungholt · Tasten", com.dan.foptionpane.FOptionPane.INFORMATION_MESSAGE);
    }

    /** Regler nach der Szene stellen (alle 0,5 s). */
    /**
     * Phase 12: ein Schritt der Mandränke. Läuft die Nacht noch nicht (oder neu = true), wird sie zuerst gestartet (baut die
     * Welt von 1362, einige Sekunden, im eigenen Faden); danach der Schritt.
     */
    private void nacht(com.dan.rungholt.scene.Mandraenke.Stelle st, boolean neu, Runnable schritt) {
        if (mandBusy) return;
        mandBusy = true;
        mandButtons();
        new Thread(() -> {
            try {
                if (neu || r.mandraenke == null) r.startMandraenke(st);
                if (schritt != null && r.mandraenke != null) schritt.run();
            } finally {
                mandBusy = false;
                javax.swing.SwingUtilities.invokeLater(this::mandButtons);
            }
        }, "Mandränke").start();
        view.requestFocusInWindow();
    }

    /** Phase 12: welche Schritte gerade gehen (nach der Stufe der Nacht). */
    private void mandButtons() {
        if (mStart == null) return;
        com.dan.rungholt.scene.Mandraenke m = r.mandraenke;
        int st = m == null ? -1 : m.stage.ordinal();
        int bruch = com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal(), morgen = com.dan.rungholt.scene.Mandraenke.Stage.MORGEN.ordinal();
        boolean free = !mandBusy;
        mStart.setEnabled(free);
        mJump.setEnabled(free && st < bruch);
        mNow.setEnabled(free && st < bruch);
        mAfter.setEnabled(free && st < morgen);
        mStop.setEnabled(free && m != null);
    }

    private void syncFromScene() {
        fromScene = true;
        int m = (int) Math.round(clock.hour() * 60) % (24 * 60);
        if (!time.getValueIsAdjusting()) time.setValue(m);
        if (!wind.getValueIsAdjusting()) wind.setValue(Beaufort.of(wiring.wind.speed()));
        if (!windDir.getValueIsAdjusting()) windDir.setValue((int) Math.round(wiring.wind.fromDeg()) % 360);
        if (!level.getValueIsAdjusting()) level.setValue((int) Math.round(wiring.level.level() * 100));
        handPegel.setSelected(!Double.isNaN(wiring.level.hand));
        if (!scaleSl.getValueIsAdjusting()) scaleSl.setValue((int) Math.round(r.renderScale * 100));
        if (!cover.getValueIsAdjusting()) cover.setValue((int) Math.round(wiring.oktas * 10));
        if (!rainSl.getValueIsAdjusting()) rainSl.setValue(rainPos(wiring.rainRate));
        if (!fogSl.getValueIsAdjusting()) fogSl.setValue((int) Math.round(wiring.fogAmount * 100));
        if (!visSl.getValueIsAdjusting()) visSl.setValue((int) Math.round(Math.min(80, wiring.visibilityKm)));
        // nur bei Änderung setzen: JComboBox meldet jedes setSelectedIndex als Aktion, und die Aktion holt den Fokus in die Szene
        if (thunderBox != null && !thunderBox.isPopupVisible() && thunderBox.getSelectedIndex() != wiring.thunderstorm.ordinal()) thunderBox.setSelectedIndex(wiring.thunderstorm.ordinal());
        if (regieBox != null && !regieBox.isPopupVisible() && !wiring.regie && regieBox.getSelectedIndex() != 0) regieBox.setSelectedIndex(0);
        fromScene = false;
        labels();
    }

    private void labels() {
        txt(dateLbl, clock.local().format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d. MMMM u", Locale.GERMANY)));
        txt(timeLbl, "Uhrzeit " + clock.timeLabel());
        double[] s = clock.sun();
        double day = clock.local().toLocalDate().atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli();
        double up = Sun.crossing(day, clock.lat, clock.lon, true), down = Sun.crossing(day, clock.lat, clock.lon, false);
        txt(sunLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>Sonne %.1f° hoch, aus %03.0f° (%s) · Aufgang %s, Untergang %s</body></html>",
                s[0], s[1], compass(s[1]), hm(up), hm(down)));
        txt(windLbl, String.format(Locale.GERMAN, "Wind Bft %d · %s · %.1f m/s%s", wiring.wind.beaufort(), Beaufort.NAME[wiring.wind.beaufort()], wiring.wind.speed(),
                wiring.regie ? String.format(Locale.GERMAN, " · Böe %.1f", wiring.gustNow) : ""));
        com.dan.weather.Weather.Snapshot ws = wiring.weather.snapshot();
        txt(regieLbl, "<html><body style='width:215px'>" + (!wiring.regie ? "Alles von Hand; die Regie steht." : ws == null ? "Regie startet …"
                : "Freie Regie: " + ws.phase().label + String.format(Locale.GERMAN, ", als Nächstes %s in %.1f h", ws.next().label, ws.nextInH())
                + String.format(Locale.GERMAN, "<br>%.1f °C, %.0f %% Feuchte, %.0f hPa %s", ws.temperature(), ws.humidity(), ws.pressure(), ws.tendencyText())
                + (wiring.weather.overrides() > 0 ? "<br>" + wiring.weather.overrides() + " Größen von Hand" : "")) + "</body></html>");
        txt(skyLbl, "<html><body style='width:215px'>" + esc(r.skyLine().replace("!", "")) + "</body></html>");
        txt(coverLbl, String.format(Locale.GERMAN, "Bedeckung %.1f/8 · %s", wiring.oktas, wiring.cloudType.label));
        txt(rainLbl, wiring.rainRate <= 0.01 ? "Regen: trocken" : String.format(Locale.GERMAN, "Regen %.1f mm/h", wiring.rainRate));
        txt(fogLbl, String.format(Locale.GERMAN, "Seenebel %.0f %%%s", wiring.fogAmount * 100,
                r.fog.snapshot().any() ? String.format(Locale.GERMAN, " · %.0f bis %.0f m über dem Wasser", wiring.fogBase, wiring.fogTop) : ""));
        txt(visLbl, String.format(Locale.GERMAN, "Sicht %.0f km", wiring.visibilityKm));
        txt(dirLbl, String.format(Locale.GERMAN, "aus %03.0f° (%s)", wiring.wind.fromDeg(), compass(wiring.wind.fromDeg())));
        txt(levelLbl, "<html><body style='width:215px'>" + esc(r.levelLine()) + "</body></html>");
        txt(waterLbl, "<html><body style='width:215px'>" + esc(r.waterLine().replace("!", "")) + "</body></html>");
        StringBuilder cal = new StringBuilder("<html><body style='width:215px'>");
        com.dan.tide.Tide hu = wiring.level.husum;
        for (com.dan.tide.Tide.Turn x : hu.calendar(clock.date(), SceneClock.ZONE))
            cal.append(esc(com.dan.tide.Tide.format(x, SceneClock.ZONE))).append("<br>");
        double sf = hu.springFactor(wiring.level.hours());
        cal.append(String.format(Locale.GERMAN, "Hub heute das %.2f-fache des mittleren (%s)", sf, sf > 1.15 ? "Springtide" : sf < 0.85 ? "Nipptide" : "zwischen Spring und Nipp"));
        txt(calLbl, cal.append("</body></html>").toString());
        com.dan.tide.Flood fl = r.flood();
        txt(floodLbl, "<html><body style='width:215px'>" + esc(fl != null ? fl.describe() : r.tideStatus()) + "</body></html>");
        txt(mudLbl, "<html><body style='width:215px'>" + esc(r.mudLine()) + "</body></html>");
        txt(sealLbl, "<html><body style='width:215px'>" + esc(r.sealLine()) + "</body></html>");
        txt(birdLbl, "<html><body style='width:215px'>" + esc(r.birdLine()) + "</body></html>");
        txt(hikeLbl, "<html><body style='width:215px'>" + esc(r.hikeLine()) + "</body></html>");
        if (r.wiring != null) txt(yearLbl, "<html><body style='width:215px'>" + esc(r.wiring.season.describe()) + "</body></html>");
        txt(eraLbl, "Ära: " + wiring.era.label());
        txt(landLbl, "<html><body style='width:215px'>" + esc(r.landLine()) + "</body></html>");
        txt(townLbl, "<html><body style='width:215px'>" + esc(r.townLine()) + "</body></html>");
        txt(mandLbl, "<html><body style='width:215px'>" + esc(r.mandraenkeLine()) + "</body></html>");
        txt(spurLbl, "<html><body style='width:215px'>" + esc(r.spurenLine()) + "</body></html>");
        txt(glockLbl, "<html><body style='width:215px'>" + esc(r.glockenLine()) + "</body></html>");
        txt(klangLbl, "<html><body style='width:215px'>" + esc(r.klang.describe()) + "</body></html>");
        txt(buchLbl, "<html><body style='width:215px'>" + esc(r.regieLine()) + "</body></html>");
        mandButtons();
        txt(camLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%s · Auge %.0f m · Blick %03.0f° · %.0f m von der Kirche [G] · %s</body></html>",
                r.camera.modeName(), r.camera.eye[1], r.camera.heading(), r.camera.originDistance(),
                r.zone()));
        txt(scaleLbl, String.format(Locale.GERMAN, "Auflösung %.0f %% (%s)", r.renderScale * 100, r.autoScale ? "geregelt, " + r.scaleSteps() + "× geändert" : "fest"));
        txt(qualLbl, "<html><body style='width:215px'>" + r.quality().summary() + "</body></html>");
    }

    private void technik() {
        String dl = com.dan.rungholt.app.Dauerlauf.zeile();
        txt(perfLbl, String.format(Locale.GERMAN, "<html><body style='width:215px'>%.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms<br>%s%s</body></html>",
                r.fps(), r.gpuMs(), r.cpuMs(), r.passTimes(), dl.isEmpty() ? "" : "<br><b>" + dl + "</b>"));
        ComputeProbe.Result c = r.compute();
        txt(computeLbl, "<html><body style='width:215px'>" + (c == null ? "Compute-Test läuft …" : c.line()) + "</body></html>");
        txt(glLbl, "<html><body style='width:215px'>" + r.glInfo() + (r.reversedDepth() ? " · umgekehrte Tiefe" : "") + "</body></html>");
        txt(physLbl, "<html><body style='width:215px'>" + r.physik.describe() + "</body></html>");
        txt(pkgLbl, "<html><body style='width:215px'>" + PAKETE.length + " Packages: " + String.join(", ", PAKETE)
                + ". Abgestellt nach _alt/packages: ice (F7 A). "
                + "Die Isolationsprüfung <i>check-packages</i> läuft nach jedem Übersetzen.</body></html>");
        StringBuilder sb = new StringBuilder("<html><body style='width:215px'>").append(wiring.summary());
        for (Wiring.Link l : wiring.links()) sb.append("<br>✓ ").append(esc(l.toString()));
        for (Wiring.Link l : Wiring.planned()) sb.append("<br>· ").append(esc(l.toString()));
        txt(linksLbl, sb.append("</body></html>").toString().replace("→", "&rarr;"));
        StringBuilder h = new StringBuilder("<html><body style='width:215px'>");
        for (String l : r.hudLines()) h.append(esc(l.startsWith("!") ? l.substring(1) : l)).append("<br>");
        txt(hudLbl, h.append("</body></html>").toString());
    }

    private static String esc(String t) { return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    static String hm(double epochMs) {
        if (Double.isNaN(epochMs)) return "—";
        return String.format("%tR", Instant.ofEpochMilli((long) epochMs).atZone(SceneClock.ZONE));
    }

    static String compass(double deg) {
        String[] n = {"Nord", "Nordnordost", "Nordost", "Ostnordost", "Ost", "Ostsüdost", "Südost", "Südsüdost",
                "Süd", "Südsüdwest", "Südwest", "Westsüdwest", "West", "Westnordwest", "Nordwest", "Nordnordwest"};
        return n[(int) Math.round(((deg % 360) + 360) % 360 / 22.5) % 16];
    }

    // ------------------------------------------------------------ Aufbau

    /** Ein Klappblock (FCollapsiblePanel); offen bleibt, was zuletzt offen war. */
    private void block(String title, boolean offen) {
        FCollapsiblePanel p = new FCollapsiblePanel() {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setTitle(title);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel inhalt = new JPanel();
        inhalt.setOpaque(false);
        inhalt.setLayout(new BoxLayout(inhalt, BoxLayout.Y_AXIS));
        inhalt.setBorder(BorderFactory.createEmptyBorder(6, 10, 10, 8));
        bloecke.add(new Object[]{p, inhalt});
        String key = "offen." + title;
        boolean auf = PREFS == null ? offen : PREFS.getBoolean(key, offen);
        if (!auf) zu.add(p);
        p.addFCollapsiblePanelListener((panel, expanded) -> {
            if (PREFS != null) try { PREFS.putBoolean(key, expanded); } catch (RuntimeException ex) { /* nicht gemerkt */ }
        });
        if (getComponentCount() > 0) add(Box.createVerticalStrut(6));
        add(p);
        target = inhalt;
    }

    /** Eine Zwischenüberschrift im Block. */
    private void sub(String text) {
        JLabel h = new JLabel(text);
        h.setForeground(Theme.accent());
        h.setFont(new Font("SansSerif", Font.BOLD, 11));
        h.setBorder(BorderFactory.createEmptyBorder(10, 0, 3, 0));
        put(h);
    }

    private void put(Component c) { target.add(row(c)); }

    private static JPanel grid(int n) {
        JPanel p = new JPanel(new java.awt.GridLayout(1, n, 6, 0));
        p.setOpaque(false);
        return p;
    }

    /** Eine Zeile, die mit ihrem Inhalt wächst. */
    private static JComponent row(Component c) {
        JPanel p = new JPanel(new BorderLayout()) {
            @Override public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
        };
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        p.add(c, BorderLayout.CENTER);
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    static JLabel label(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.ink());
        l.setFont(new Font("SansSerif", Font.PLAIN, 13));
        return l;
    }

    static JLabel small(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(Theme.muted());
        l.setFont(new Font("SansSerif", Font.PLAIN, 11));
        return l;
    }

    // ------------------------------------------------------------ Scrollable: nur senkrecht
    @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
    @Override public int getScrollableUnitIncrement(java.awt.Rectangle rr, int o, int d) { return 16; }
    @Override public int getScrollableBlockIncrement(java.awt.Rectangle rr, int o, int d) { return 120; }
    @Override public boolean getScrollableTracksViewportWidth() { return true; }
    @Override public boolean getScrollableTracksViewportHeight() { return false; }
}
