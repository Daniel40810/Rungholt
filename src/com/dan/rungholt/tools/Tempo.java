package com.dan.rungholt.tools;

import com.dan.rungholt.camera.Ansichten;
import com.dan.rungholt.gl.Quality;
import com.dan.rungholt.gl.Renderer;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.wiring.Beaufort;
import com.dan.rungholt.wiring.Wiring;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLOffscreenAutoDrawable;
import com.jogamp.opengl.GLProfile;

import java.io.File;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Tempo-Probe und Standbild ohne Fenster (aus Glacier). Rechnet die Szene unsichtbar in fester Größe, misst die
 * Wanduhr je Bild bis glFinish und die Zeit jedes Durchgangs auf der Grafikkarte und schreibt auf Wunsch ein PNG. So
 * lässt sich jede Phase vorher und nachher vergleichen und im Leistungsbudget (Werkbuch, Abschnitt 10) eintragen.
 * <pre>
 * Tempo [bilder] [breite] [höhe] [png|-] [NIEDRIG|MITTEL|HOCH|ULTRA] [uhrzeit] [datum JJJJ-MM-TT] [beaufort]
 * Tempo 120 1920 1080 docs/status/Tempo_Phase1.png MITTEL 13.5 2026-07-21 4
 * Umgebung: OHNE_HUD, OHNE_MARKEN, ANSICHT=Name einer Ansicht (VomDeich, AufdemWatt, Suedfall, AmPriel, Seehundbank, Vogelauge)
 * oder „oben“, KAMERA=x,z,Richtung,Neigung,Höhe (frei stehend); seit Phase 2 PEGEL=m über NHN (von Hand, sonst der
 * Platzhalter nach der Uhrzeit); seit Phase 3 WETTER=Lage der freien Regie (KLAR, NEBEL, GEWITTER, STURM, ORKAN …),
 * BEDECKUNG=Achtel, REGEN=mm/h, NEBEL=0..1, SICHT=km, GEWITTER=0..4 (von Hand über der Lage), BLITZ (ein Erdblitz in
 * Blickrichtung kurz vor dem Bild), TAG=Stunden mit Komma (ein Tag im Zeitraffer: je Stunde ein Bild, dazu ein Bogen
 * aus allen als _tag.png); seit Phase 4 TIDE=Stunden (wie TAG, beschriftet mit Pegel, Nässe und Pfützen; seit Phase 5 mit der Feuchte des Watts). Vor jedem Bild
 * rechnet die Tide 6 h Vorlauf in 5-min-Schritten, damit die Pfützen stehen. Seit Phase 7: SEEHUNDE=Zahl (sonst nach
 * der Stufe), OHNE_SEEHUNDE, STOERER=Stunde (der Wattwanderer geht zu dieser Szenenzeit zur Bank; mit TIDE= laufen
 * Seehunde und Wanderer zwischen den Bildern in Schritten von höchstens 5 min mit), OHNE_SPUREN. Vor dem Bild setzt
 * sich die Kolonie und lebt 20 Minuten. Seit Phase 8: OHNE_VOEGEL, ANSICHT=Voegel (vor die nächste fressende Gruppe),
 * SCHWARM=Stunde (die nächste Gruppe am Boden fliegt auf; mit TIDE=), FOLGEN=Meter (vor jedem Bild schaut die Kamera auf die
 * nächste Gruppe in der Luft, sonst am Boden, und rückt bis auf so viele Meter heran), VOEGEL_LISTE (jede Gruppe mit Zustand,
 * Ort und Wassertiefe). Seit Phase 9: WANDERUNG=Stunde (die Wattwanderung nach Südfall startet, die Kamera geht mit; mit TIDE=),
 * JAHR=1,4,7,10 (dieselbe Ansicht am selben Tag in diesen Monaten, Bogen _tag.png). Seit Phase 10: AERA=1362 (die Welt von
 * 1362, mit TIDE= etwa eine Springflut am Seedeich), ZEITREISE=0,0.25,0.5,0.75,1 (dieselbe Ansicht bei diesen Ständen der
 * Überblendung). VOEGEL in der Beschriftung zählt die Wattvögel. Seit Phase 11: ANSICHT=Rungholt1362 und ANSICHT=Sielhafen
 * schalten nach 1362 (die Stadt wird mit dem Gelände gebaut); mit TIDE= laufen Kogge und Leute zwischen den Bildern mit.
 * Seit Phase 15: REGIE=Tide|Mandraenke|Glocken (ein Regiebuch mit Blatt), dazu KINO=1 (im Kinomodus, Kinobilder).
 * </pre>
 */
public final class Tempo {
    private Tempo() { }

    public static void main(String[] a) throws Exception {
        int frames = a.length > 0 ? Integer.parseInt(a[0]) : 120;
        int w = a.length > 1 ? Integer.parseInt(a[1]) : 1920, h = a.length > 2 ? Integer.parseInt(a[2]) : 1080;
        File png = a.length > 3 && !a[3].equals("-") ? new File(a[3]) : null;
        Quality q = a.length > 4 ? Quality.valueOf(a[4]) : Quality.MITTEL;
        double hour = a.length > 5 ? Double.parseDouble(a[5].replace(',', '.')) : SceneClock.START_HOUR;
        LocalDate day = a.length > 6 && !a[6].equals("-") ? LocalDate.parse(a[6]) : SceneClock.START_DATE;
        int bft = a.length > 7 ? Integer.parseInt(a[7]) : 4;

        GLProfile prof = GLProfile.get(GLProfile.GL4);
        GLCapabilities caps = new GLCapabilities(prof);
        caps.setOnscreen(false);
        caps.setFBO(true);
        caps.setDepthBits(24);
        GLOffscreenAutoDrawable d = GLDrawableFactory.getFactory(prof).createOffscreenAutoDrawable(null, caps, null, w, h);
        d.setAutoSwapBufferMode(false);
        SceneClock clock = new SceneClock(day, hour);
        clock.lapse = 0;
        Wiring wiring = new Wiring(clock);
        wiring.wind.speed = Beaufort.mid(bft);
        String wetter = System.getenv("WETTER");   // Phase 3: freie Regie ab einer Lage
        if (wetter != null) {
            wiring.regie = true;
            wiring.startFree(com.dan.weather.Weather.Phase.valueOf(wetter.toUpperCase(Locale.ROOT)));
            if (a.length > 7) wiring.hand(com.dan.weather.Weather.Var.WIND, Beaufort.mid(bft));
        }
        hand(wiring, "BEDECKUNG", com.dan.weather.Weather.Var.COVER);
        hand(wiring, "REGEN", com.dan.weather.Weather.Var.RAIN);
        hand(wiring, "NEBEL", com.dan.weather.Weather.Var.FOG);
        hand(wiring, "SICHT", com.dan.weather.Weather.Var.VISIBILITY);
        hand(wiring, "GEWITTER", com.dan.weather.Weather.Var.THUNDER);
        Renderer r = new Renderer(clock, wiring);
        r.frozen = true;
        r.setQuality(q);
        r.autoScale = false;
        if (System.getenv("OHNE_HUD") != null) r.showHud = false;
        if (System.getenv("OHNE_MARKEN") != null) r.showMarks = false;
        if (System.getenv("OHNE_WASSER") != null) r.showWater = false;
        if (System.getenv("OHNE_HALME") != null) r.showReed = false;
        if (System.getenv("OHNE_BODEN") != null) r.showGround = false;
        if (System.getenv("OHNE_FEINFORM") != null) r.relief.on = false;
        r.mudGl.minIntervalMs = 0;
        if (System.getenv("OHNE_SEEHUNDE") != null) r.showSeals = false;
        if (System.getenv("OHNE_SPUREN") != null) r.showTracks = false;
        if (System.getenv("OHNE_VOEGEL") != null) r.showBirds = false;
        String sh = System.getenv("SEEHUNDE");
        if (sh != null) { r.sealCount = Integer.parseInt(sh.trim()); r.setQuality(q); }   // Standbilder: jede neue Nässe sofort   // Phase 6: ohne Prielkanten und Rinnsale
        String an = System.getenv("ANSICHT");
        if ("oben".equals(an)) { r.camera.lookAt(0, 0, 0, 0, 89, 9000); r.camera.snap(); }
        else if ("Draufsicht".equalsIgnoreCase(an)) r.placeSurveyView(false);   // Phase 13
        else if (an != null && Ansichten.byName(an) != null) r.placeView(Ansichten.byName(an));   // seit Phase 11: 8 und 9 schalten nach 1362
        String pegel = System.getenv("PEGEL");   // Phase 2: Pegel von Hand (m über NHN), etwa −1,5 für Niedrigwasser
        if (pegel != null) wiring.level.hand = Double.parseDouble(pegel.replace(',', '.'));
        String kam = System.getenv("KAMERA");   // x,z,Richtung,Neigung,Höhe über Grund (frei)
        if (kam != null && !"BRUCH".equalsIgnoreCase(kam)) {
            String[] k = kam.split(",");
            double kx = Double.parseDouble(k[0]), kz = Double.parseDouble(k[1]);
            r.camera.place(kx, r.terrain.height(kx, kz) + Double.parseDouble(k[4]), kz, Double.parseDouble(k[2]), Double.parseDouble(k[3]));
        }
        d.addGLEventListener(r);
        d.display();
        if (r.error() != null) { System.err.println("FEHLER: " + r.error()); System.exit(2); }
        // Phase 4: auf die Überflutungskarte warten, dann selbst rechnen (6 h Vorlauf in 5-min-Schritten, damit Pfützen stehen)
        for (int i = 0; i < 1200 && r.flood() == null && !r.tideStatus().startsWith("!"); i++) Thread.sleep(100);
        r.stopTide();
        // Phase 13: die Spuren (SPUREN=0 aus), die Magnetik als Karte (MAGNETIK=1), die Fundstellen (FUNDE=1)
        if (r.flood() != null && r.spuren == null) r.buildSpuren();
        if ("0".equals(System.getenv("SPUREN"))) r.showSpuren = false;
        if (System.getenv("MAGNETIK") != null) r.magnetik = r.magBlend = Double.parseDouble(System.getenv("MAGNETIK"));
        if (System.getenv("FUNDE") != null) r.showFinds = true;
        String wk = System.getenv("WARFTKARTE");   // Phase 13: die Warftkarte zu einer Warft (Name) oder zum Bildpunkt x,y
        if (wk != null) {
            if (wk.matches("[0-9]+,[0-9]+")) pendingClick = wk;
            else for (com.dan.rungholt.scene.Land1362.Warft x : com.dan.rungholt.scene.Land1362.warften()) if (x.name().equalsIgnoreCase(wk.trim())) r.warftCard = r.warftCard(x);
        }
        // Phase 10: AERA=1362 stellt die Welt von 1362 ein; das Gelände und seine Überflutung werden hier gebaut
        if (r.flood() != null && (System.getenv("AERA") != null || System.getenv("ZEITREISE") != null || wiring.era.target > 0.5)) {
            if (r.land1362 == null) r.buildLand1362();
            if ("1362".equals(System.getenv("AERA"))) wiring.era.target = wiring.era.blend = 1;
            if (wiring.era.target > 0.5) r.selectEra();
            if (an != null && Ansichten.byName(an) != null && Ansichten.byName(an).key() >= 8) r.placeView(Ansichten.byName(an));   // auf dem gebauten Grund
            System.out.println("Rungholt: " + r.tideStatus());
        }
        if (r.flood() != null) {
            double now = clock.epochMillis();
            for (double t = now - 6 * 3_600_000; t < now; t += 300_000) { clock.setEpochMillis(t); r.stepTideNow(); }
            clock.setEpochMillis(now - 20 * 60_000);
            r.stepTideNow();
            // Phase 7: die Kolonie setzt sich und lebt 20 Minuten bis zum Bild
            r.settleSeals();
            for (int k = 1; k <= 20; k++) {
                clock.setEpochMillis(now - (20 - k) * 60_000);
                r.stepTideNow();
                r.stepAnimalsScene(60);
            }
            System.out.println("Rungholt: " + r.sealLine());
            if (System.getenv("SEEHUNDE_LISTE") != null) for (com.dan.seal.Colony.Animal sa : r.colony.animals()) System.out.printf(Locale.ROOT, "  Tier %d %s %.1f %.2f %.1f Grund %.2f L %.2f%n", sa.id, sa.act, sa.x, sa.y, sa.z, r.groundHeight(sa.x, sa.z), sa.length);
            if (an != null && Ansichten.byName(an) != null && Ansichten.byName(an).key() == 5 && r.placeSealView()) System.out.println("Ansicht 5 bei den Seehunden");
            System.out.println("Rungholt: " + r.birdLine());
            if ("Voegel".equalsIgnoreCase(an) && r.placeBirdView()) System.out.println("Kamera bei den Wattvögeln");
        } else System.out.println("ohne Überflutung: " + r.tideStatus());
        // Phase 12: MANDRAENKE=KIRCHE|SIEL|OST startet die Nacht (23.1.1362, 22 Uhr), KAMERA=BRUCH auf den Deich daneben
        String md = System.getenv("MANDRAENKE");
        if (md != null && r.flood() != null) {
            r.manualMandraenke = true;
            com.dan.rungholt.scene.Mandraenke.Stelle st = com.dan.rungholt.scene.Mandraenke.Stelle.KIRCHE;
            for (com.dan.rungholt.scene.Mandraenke.Stelle x : com.dan.rungholt.scene.Mandraenke.Stelle.values()) if (x.name().equalsIgnoreCase(md.trim())) st = x;
            System.out.println("Mandränke: " + (r.startMandraenke(st) ? "läuft, Bruchstelle " + st.label : "nicht gestartet"));
            r.selectEra();
            if ("BRUCH".equalsIgnoreCase(kam)) r.placeBreachView();
            else if (an != null && Ansichten.byName(an) != null) r.placeView(Ansichten.byName(an));
            else if (kam != null) {
                String[] k = kam.split(",");
                double kx = Double.parseDouble(k[0]), kz = Double.parseDouble(k[1]);
                r.camera.place(kx, r.ground1362(kx, kz) + Double.parseDouble(k[4]), kz, Double.parseDouble(k[2]), Double.parseDouble(k[3]));
            }
            if (System.getenv("LIVE") != null && png != null) { live(r, d, clock, png, Double.parseDouble(System.getenv("LIVE"))); d.destroy(); System.exit(0); }
            if (System.getenv("ECHTZEIT") != null && png != null) { echtzeit(r, d, clock, png, System.getenv("ECHTZEIT")); d.destroy(); System.exit(0); }
        }
        for (int i = 0; i < 10; i++) { r.step(1 / 60.0); d.display(); }
        if (pendingClick != null) {   // Phase 13: Klick ins Bild wie mit der Maus
            String[] c = pendingClick.split(",");
            r.warftCard = null;
            r.clickAt(Integer.parseInt(c[0]), Integer.parseInt(c[1]), w, h);
            com.dan.rungholt.scene.Land1362.Warft hit = r.lastWarft;
            System.out.println("Klick " + pendingClick + ": " + (hit == null ? (r.warftCard != null ? "Zellkarte" : "Himmel") : hit.name()));
        }
        String tag = System.getenv("TAG");
        if (tag == null) tag = System.getenv("TIDE");
        if (System.getenv("ZEITREISE") != null && png != null) { zeitreise(r, d, wiring, png, System.getenv("ZEITREISE")); d.destroy(); System.exit(0); }
        if (System.getenv("REGIE") != null) { regie(r, d, System.getenv("REGIE"), png); d.destroy(); System.exit(0); }   // Phase 15
        if (System.getenv("JAHR") != null && png != null) { jahr(r, d, clock, png, System.getenv("JAHR")); d.destroy(); System.exit(0); }
        if (tag != null && png != null) { tag(r, d, clock, png, tag); d.destroy(); System.exit(0); }
        boolean blitz = System.getenv("BLITZ") != null;
        if (blitz || System.getenv("GEWITTER") != null) clock.lapse = 1;   // Blitze laufen in Szenenzeit
        double best = Double.MAX_VALUE, sum = 0;
        for (int i = 0; i < frames; i++) {
            long t0 = System.nanoTime();
            r.step(1 / 60.0);
            d.display();
            d.getContext().makeCurrent();
            d.getGL().glFinish();
            d.getContext().release();
            double ms = (System.nanoTime() - t0) / 1e6;
            best = Math.min(best, ms);
            sum += ms;
            if (blitz && i == frames - 9) r.triggerLightning(com.dan.lightning.Bolt.Kind.CLOUD_GROUND);
        }
        System.out.println(r.glInfo());
        System.out.println(r.compute() == null ? "Compute-Test: nicht gelaufen" : r.compute().line());
        System.out.printf(Locale.GERMAN, "%d×%d %s, %s: bestes Bild %.2f ms, Mittel %.2f ms (%.0f Bilder/s)%n", w, h, q.label, clock.label(), best, sum / frames, 1000 / (sum / frames));
        System.out.println("Durchgänge (ms): " + r.passTimes());
        for (String l : r.hudLines()) System.out.println("  " + l);
        if (png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            javax.imageio.ImageIO.write(img[0], "png", png);
            System.out.println("Bild: " + png.getPath());
        }
        d.destroy();
        System.exit(0);
    }

    private static Double stoerer, schwarm;
    private static String pendingClick;
    private static boolean walkerSent, flushed;

    /** Phase 12: „Sturm · NHN +3,2 m · Marsch +0,6 m · 12 Häuser treiben“ für den Bogen der Mandränke. */
    static String shortMand(Renderer r) {
        com.dan.rungholt.scene.Mandraenke m = r.mandraenke;
        String s = String.format(Locale.GERMAN, "%s · Pegel NHN %+.2f m", m.stage.label, r.level());
        if (!Double.isNaN(m.polder)) s += String.format(Locale.GERMAN, " · Marsch %+.2f m", m.polder);
        if (m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal()) s += String.format(Locale.GERMAN, " · %d Häuser treiben, %d fort", m.afloat(), m.gone());
        return s;
    }

    /**
     * Phase 12: ECHTZEIT=0,15,30,… – der Bruch in Echtzeit: zum Überlauf springen, bis zum Bruch rechnen, dann Bilder so
     * viele Sekunden nach dem Beginn des Bruchs (Körper aus Klei, Welle, je Sekunde gerechnet); Bogen _tag.png.
     */
    /** Prüfhilfe: LIVE=Sekunden – der Bruch wie im Bildlauf (Fäden, Echtzeit), ein Bild je halbe Sekunde; meldet die längsten Schritte, die Zeit je Bild und die kleinste Belichtung (Blitze). */
    static void live(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, File png, double secs) throws Exception {
        r.manualMandraenke = false;
        r.jumpToOvertop();
        for (int i = 0; i < 30; i++) { r.step(1 / 60.0); d.display(); }
        r.breakDikeNow();
        java.util.List<java.awt.image.BufferedImage> imgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (int f = 0; f * 0.5 < secs; f++) {
            double stepMax = 0;
            double eMin = 1e30;
            for (int i = 0; i < 30; i++) { long a0 = System.nanoTime(); r.step(1 / 60.0); stepMax = Math.max(stepMax, (System.nanoTime() - a0) / 1e6); eMin = Math.min(eMin, r.exposure()); Thread.sleep(16); }
            System.out.println(String.format(Locale.GERMAN, "   Belichtung kleinste %.3g, jetzt %.3g · %d Blitze", eMin, r.exposure(), r.lightning.flashes()));
            long b0 = System.nanoTime();
            d.display();
            System.out.println(String.format(Locale.GERMAN, "   Schritt höchstens %.1f ms, Bild %.0f ms", stepMax, (System.nanoTime() - b0) / 1e6));
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            if (f % 4 == 0) { imgs.add(img[0]); labels.add(String.format(Locale.GERMAN, "%.1f s · %s", f * 0.5, clock.label())); }
            System.out.println(String.format(Locale.GERMAN, "%.1f s · %s · lapse %.0f · %s", f * 0.5, clock.label(), clock.lapse, r.mandraenkeLine()));
        }
        sheet(imgs, labels, png);
    }

    static void echtzeit(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, File png, String secs) throws Exception {
        r.jumpToOvertop();
        com.dan.rungholt.scene.Mandraenke m = r.mandraenke;
        for (int k = 0; k < 600 && m.stage.ordinal() < com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal(); k++) {
            clock.setEpochMillis(clock.epochMillis() + 30_000);
            r.stepTideNow();
            r.stepMandraenke(30, true);
        }
        double t0 = clock.epochMillis(), done = 0;
        java.util.List<java.awt.image.BufferedImage> imgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String sx : secs.split(",")) {
            double target = Double.parseDouble(sx.trim());
            while (done < target - 1e-9) {
                double st = Math.min(1, target - done);
                done += st;
                clock.setEpochMillis(t0 + done * 1000);
                if (Math.round(done) % 20 == 0) r.stepTideNow();
                r.stepMandraenke(st, true);
            }
            if (r.wiring != null && r.wiring.regie) { r.wiring.feedWeather(1 / 60.0); r.clouds.step(1); }
            for (int i = 0; i < 90; i++) r.stepSpray(1 / 30.0);   // Phase 17: die Gischt über der Krone 3 s vorlaufen lassen
            for (int i = 0; i < 8; i++) { r.step(1 / 60.0); d.display(); }
            Thread.sleep(400);
            r.sky.epochMillis = clock.epochMillis();
            r.sky.updateNow();
            r.readapt();
            for (int i = 0; i < 6; i++) { r.step(1 / 60.0); d.display(); }
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            File out = new File(png.getPath().replace(".png", String.format("_%03d.png", (int) target)));
            javax.imageio.ImageIO.write(img[0], "png", out);
            imgs.add(img[0]);
            labels.add(String.format(Locale.GERMAN, "Bruch + %.0f s · %s", target, m.bruch.describe().replace("Bresche 50 m: ", "")));
            System.out.println("Bild " + out.getPath() + " · " + labels.get(labels.size() - 1) + " · " + r.mandraenkeLine());
        }
        sheet(imgs, labels, png);
    }

    /** „Pegel −1,20 m · Flut · nass 41 % · 812 Pfützenzellen“ für den Bogen einer Tide. */
    static String tideLabel(Renderer r) {
        com.dan.tide.Flood f = r.flood();
        String s = String.format(Locale.GERMAN, "Pegel NHN %+.2f m · %s", r.level(), r.wiring != null && r.wiring.level.rising() ? "Flut" : "Ebbe");
        com.dan.mudflat.WetField wf = r.wetField();
        if (System.getenv("SCHWARM") != null || "Voegel".equalsIgnoreCase(System.getenv("ANSICHT"))) {
            int[] c = r.waders.census();
            if (System.getenv("VOEGEL_LISTE") != null) System.out.println("  Stunden seit Hochwasser " + r.waders.world.highWaterHours() + " Sonne " + r.waders.sunAltDeg);
            if (System.getenv("VOEGEL_LISTE") != null) for (com.dan.flock.Waders.Group g : r.waders.groups()) System.out.printf(Locale.ROOT, "  Gruppe %s %s %.0f %.0f Tiefe %.2f%n", g.species.label(), g.mode, g.cx, g.cz, r.wiring.level.depthAt(g.cx, g.cz));
            return s + String.format(Locale.GERMAN, " · Vögel: %d fressen, %d rasten, %d fliegen, %d wenden", c[0], c[1], c[2], c[3]);
        }
        if (System.getenv("SEEHUNDE") != null || System.getenv("STOERER") != null) {
            int[] c = r.colony.census();
            return s + String.format(Locale.GERMAN, " · %d liegen, %d im Wasser, %d robben%s%s", c[com.dan.seal.Colony.Activity.LIEGEN.ordinal()],
                    c[com.dan.seal.Colony.Activity.SCHWIMMEN.ordinal()] + c[com.dan.seal.Colony.Activity.TAUCHEN.ordinal()],
                    c[com.dan.seal.Colony.Activity.ANLANDEN.ordinal()] + c[com.dan.seal.Colony.Activity.UMLEGEN.ordinal()],
                    c[com.dan.seal.Colony.Activity.FLUCHT.ordinal()] > 0 ? ", " + c[com.dan.seal.Colony.Activity.FLUCHT.ordinal()] + " fliehen" : "",
                    r.walker.active ? String.format(Locale.GERMAN, " · Wanderer %.0f m", Math.hypot(r.walker.x - com.dan.rungholt.scene.Seehundbank.X, r.walker.z - com.dan.rungholt.scene.Seehundbank.Z)) : "");
        }
        return f == null ? s : s + String.format(Locale.GERMAN, " · nass %.0f %% · %d Pfützenzellen", f.wetShare * 100, f.puddles)
                + (wf == null ? "" : String.format(Locale.GERMAN, " · Watt %.0f %% feucht", wf.meanWet * 100));
    }

    /** Eine Größe der Regie von Hand aus der Umgebung (ohne Regie: das Feld der Verdrahtung direkt). */
    static void hand(Wiring w, String env, com.dan.weather.Weather.Var v) {
        String s = System.getenv(env);
        if (s == null) return;
        double x = Double.parseDouble(s.replace(',', '.'));
        if (w.regie) { w.hand(v, x); return; }
        switch (v) {
            case COVER -> w.oktas = x;
            case RAIN -> w.rainRate = x;
            case FOG -> w.fogAmount = x;
            case VISIBILITY -> w.visibilityKm = x;
            case THUNDER -> w.thunderstorm = com.dan.lightning.Lightning.Level.values()[(int) Math.max(0, Math.min(4, x))];
            default -> { }
        }
    }

    /**
     * Phase 3, Abnahme „ein Tag im Zeitraffer über dem Watt“: dieselbe Ansicht zu jeder Stunde in {@code hours}
     * (etwa „4,6,9,13,17,20,21.5,23“), je ein Bild _HHMM.png und ein Bogen aus allen (_tag.png, vier je Zeile).
     * Die Regie läuft mit der Uhr mit (die Wolken ziehen, das Wetter wechselt); die Augenanpassung beginnt je Bild neu.
     */
    static void tag(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, File png, String hours) throws Exception {
        String[] hs = hours.split(",");
        String wa = System.getenv("WANDERUNG");
        Double wanderung = wa == null ? null : Double.parseDouble(wa.replace(',', '.'));
        boolean hikeStarted = false;
        String sw = System.getenv("SCHWARM");
        schwarm = sw == null ? null : Double.parseDouble(sw.replace(',', '.'));
        flushed = false;
        String st = System.getenv("STOERER");
        stoerer = st == null ? null : Double.parseDouble(st.replace(',', '.'));
        walkerSent = false;
        java.util.List<java.awt.image.BufferedImage> imgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        LocalDate day = clock.date();
        double prev = clock.epochMillis();
        for (String hx : hs) {
            double hour = Double.parseDouble(hx.trim());
            clock.set(day.plusDays((long) Math.floor(hour / 24)), hour % 24);   // seit Phase 12: Stunden über 24 sind der nächste Tag
            {
                // Regie, Wolken und Tide bis zur neuen Stunde in 5-min-Schritten nachziehen (die Pfützen bleiben stehen)
                double target = clock.epochMillis();
                for (double t = prev; t < target; ) {
                    double step = Math.min(300_000, target - t);
                    if (schwarm != null && !flushed) {   // genau zur Stunde aufscheuchen, damit das Bild den Schwarm noch in der Luft hat
                        double sMs = target - (hour - schwarm) * 3_600_000;
                        if (sMs > t && sMs < t + step) step = sMs - t;
                    }
                    t += step;
                    clock.setEpochMillis(t);
                    if (r.wiring != null && r.wiring.regie) { r.wiring.feedWeather(1 / 60.0); r.clouds.step(step / 1000); }
                    r.stepTideNow();
                    // Phase 7: Wanderer zur gewünschten Stunde losschicken, Seehunde und Wanderer mitlaufen lassen
                    if (stoerer != null && !walkerSent && clock.hour() >= stoerer) { walkerSent = r.sendWalker(); System.out.println("Wattwanderer: " + (walkerSent ? String.format(Locale.ROOT, "unterwegs von %.0f,%.0f", r.walker.x, r.walker.z) : "kein trockener Weg")); }
                    r.stepAnimalsScene(step / 1000);
                    r.stepMandraenke(step / 1000, true);   // Phase 12
                    // Phase 9: die Wattwanderung zur Stunde losschicken, die Kamera geht mit
                    if (wanderung != null && !hikeStarted && clock.hour() >= wanderung - 1e-6) { hikeStarted = true; System.out.println("Wattwanderung: " + (r.followHike() ? "unterwegs" : "Weg nass")); }
                    // Phase 8: nach dem Schritt, der bis zur Stunde reicht, aufscheuchen
                    if (schwarm != null && !flushed && clock.hour() >= schwarm - 1e-6) { com.dan.flock.Waders.Group fg = r.flushBirds(); flushed = true; System.out.println("Schwarm: " + (fg == null ? "keiner am Boden" : fg.species.label() + " " + fg.size())); }
                }
                clock.setEpochMillis(target);
                r.stepTideNow();
                prev = target;
            }
            String folgen = System.getenv("FOLGEN");   // Phase 8: der Blick folgt den Vögeln (höchstens so viele m entfernt)
            if (folgen != null) r.aimAtBirds(Double.parseDouble(folgen.replace(',', '.')));
            int settle = Integer.parseInt(System.getenv().getOrDefault("RUHE", "12"));
            for (int i = 0; i < settle; i++) { r.step(1 / 60.0); d.display(); }
            // Licht des Himmels für die neue Stunde sofort (ein Rechenauftrag im Hintergrund könnte noch das alte bringen)
            Thread.sleep(400);
            r.sky.epochMillis = clock.epochMillis();
            r.sky.updateNow();
            r.readapt();
            for (int i = 0; i < 4; i++) { r.step(1 / 60.0); d.display(); }
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            File out = new File(png.getPath().replace(".png", String.format("_%02d%02d.png", (int) hour, (int) Math.round(hour % 1 * 60))));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            javax.imageio.ImageIO.write(img[0], "png", out);
            imgs.add(img[0]);
            labels.add(clock.timeLabel() + (r.mandraenke != null ? " · " + shortMand(r) : r.hike != null ? " · " + r.hike.describe().replace("Wattwanderung ", "") : System.getenv("TIDE") != null ? " · " + tideLabel(r) : " · Sonne " + String.format(Locale.GERMAN, "%.0f°", clock.sun()[0]) + " · " + r.shortWeatherLine()));
            System.out.println("Bild " + out.getPath() + " · " + labels.get(labels.size() - 1));
        }
        sheet(imgs, labels, png);
    }

    /**
     * Phase 9: JAHR=1,4,7,10 – dieselbe Ansicht am selben Tag und zur selben Stunde in diesen Monaten; vor jedem Bild
     * setzt sich die Szene (Tide eine Stunde in 5-min-Schritten, Seehunde und Vögel, das Jahr rechnet den Winter vor).
     */
    static void jahr(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, File png, String months) throws Exception {
        java.util.List<java.awt.image.BufferedImage> imgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        LocalDate day0 = clock.date();
        double hour = clock.hour();
        for (String m : months.split(",")) {
            LocalDate day = day0.withMonth(Integer.parseInt(m.trim()));
            clock.set(day, hour - 1);
            r.stepTideNow();
            r.step(1 / 60.0);
            r.settleSeals();
            for (int k = 1; k <= 12; k++) {
                clock.setEpochMillis(clock.epochMillis() + 300_000);
                r.stepTideNow();
                r.stepAnimalsScene(300);
            }
            clock.set(day, hour);
            r.stepTideNow();
            int settle = Integer.parseInt(System.getenv().getOrDefault("RUHE", "12"));
            for (int i = 0; i < settle; i++) { r.step(1 / 60.0); d.display(); }
            Thread.sleep(400);
            r.sky.epochMillis = clock.epochMillis();
            r.sky.updateNow();
            r.readapt();
            for (int i = 0; i < 4; i++) { r.step(1 / 60.0); d.display(); }
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            File out = new File(png.getPath().replace(".png", String.format("_%02d.png", day.getMonthValue())));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            javax.imageio.ImageIO.write(img[0], "png", out);
            imgs.add(img[0]);
            String l = day.getDayOfMonth() + "." + day.getMonthValue() + ". " + clock.timeLabel() + " · " + r.wiring.season.describe().replace("Jahr: ", "") + " · " + r.birdLine().replaceAll(" · [0-9]+ Netze.*", "");
            labels.add(l);
            System.out.println("Bild " + out.getPath() + " · " + l);
        }
        sheet(imgs, labels, png);
    }

    /** Phase 10: ZEITREISE=0,0.25,0.5,0.75,1 – dieselbe Ansicht bei diesen Ständen der Überblendung (heute → 1362). */
    static void zeitreise(Renderer r, GLOffscreenAutoDrawable d, Wiring w, File png, String blends) throws Exception {
        java.util.List<java.awt.image.BufferedImage> imgs = new java.util.ArrayList<>();
        java.util.List<String> labels = new java.util.ArrayList<>();
        for (String b : blends.split(",")) {
            double v = Double.parseDouble(b.trim());
            w.era.target = w.era.blend = v;
            r.selectEra();
            r.stepTideNow();
            int settle = Integer.parseInt(System.getenv().getOrDefault("RUHE", "12"));
            for (int i = 0; i < settle; i++) { w.era.target = w.era.blend = v; r.step(1 / 60.0); d.display(); }
            final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
            d.invoke(true, x -> { img[0] = r.grab(x); return true; });
            File out = new File(png.getPath().replace(".png", String.format("_%03d.png", (int) Math.round(v * 100))));
            File dir = out.getAbsoluteFile().getParentFile();
            if (dir != null) dir.mkdirs();
            javax.imageio.ImageIO.write(img[0], "png", out);
            imgs.add(img[0]);
            String l = w.era.label() + " · " + tideLabel(r) + " · " + r.zone();
            labels.add(l);
            System.out.println("Bild " + out.getPath() + " · " + l);
        }
        sheet(imgs, labels, png);
    }

    /**
     * Phase 15: REGIE=Name eines Regiebuchs (Tide, Mandraenke, Glocken): das Regiebuch läuft ohne Fenster in Schritten von
     * 1/30 s durch (Tide und Physik inline), jedes zehnte Bild und jedes Foto wird gezeichnet; am Ende liegt das Blatt
     * neben dem PNG. Mit KINO=1 im Kinomodus; die Kinobilder (Titel, Aufblende, jedes Foto, Abspann) als png_kino_01.png …
     */
    static void regie(Renderer r, GLOffscreenAutoDrawable d, String name, File png) throws Exception {
        com.dan.rungholt.scene.Regie.Book b = com.dan.rungholt.scene.Regie.byName(name);
        if (b == null) { System.err.println("FEHLER: kein Regiebuch „" + name + "“"); return; }
        if (png != null) r.photoDir = png.getAbsoluteFile().getParentFile();
        boolean kino = System.getenv("KINO") != null;
        java.util.List<java.awt.image.BufferedImage> kinoBilder = new java.util.ArrayList<>();
        if (kino) r.kinoStart(b); else r.regieStart(b);
        r.frozen = true;   // die Szene läuft nur über r.step(dt) in festen Schritten
        double dt = 1 / 30.0, sek = 0, abspannSeit = -1;
        long n = 0;
        int fotos = 0;
        while (r.regie.running() || r.regieWantsFrame() || (kino && r.kino.laeuft())) {
            sek += dt;
            r.step(dt);
            if (n % 6 == 0) r.stepTideNow();
            boolean grab = false;
            if (kino) {
                boolean foto = r.regieWantsFrame() && !r.kino.schnittFaellig();
                if (r.kino.lage() == com.dan.kino.Kino.Lage.ABSPANN) abspannSeit = abspannSeit < 0 ? 0 : abspannSeit + dt;
                grab = Math.abs(sek - 3) < dt / 2 || Math.abs(sek - 8.5) < dt / 2 || Math.abs(abspannSeit - 6) < dt / 2 || (foto && fotos++ < 99);
            }
            if (++n % 10 == 0 || r.regieWantsFrame() || grab) {
                if (grab) r.grabRequest = kinoBilder::add;
                d.display();
            }
            if (n % 900 == 0) System.out.println("  " + r.regieLine() + " · " + clock(r));
            if (sek > 3600) { System.err.println("FEHLER: Regiebuch läuft über eine Stunde"); break; }
        }
        d.display();
        System.out.println("Blatt: " + r.lastSheet + " nach " + Math.round(sek) + " s");
        if (kino && png != null) {
            File dir = png.getAbsoluteFile().getParentFile();
            String stem = png.getName().replaceFirst("\\.png$", "");
            for (int i = 0; i < kinoBilder.size(); i++)
                javax.imageio.ImageIO.write(kinoBilder.get(i), "png", new File(dir, String.format(Locale.ROOT, "%s_kino_%02d.png", stem, i + 1)));
            System.out.println(kinoBilder.size() + " Kinobilder nach " + dir);
        }
    }

    static String clock(Renderer r) { return r.clock.label(); }

    /** Uhr, Wetter, Tide, Tiere und Grabung in 5-min-Schritten bis zur Stunde (seit 1970). */
    static void advance(Renderer r, SceneClock clock, double untilHours) {
        double target = untilHours * 3_600_000;
        for (double t = clock.epochMillis(); t < target; ) {
            double step = Math.min(300_000, target - t);
            t += step;
            clock.setEpochMillis(t);
            if (r.wiring.regie) { r.wiring.feedWeather(1 / 60.0); r.clouds.step(step / 1000); }
            r.wiring.feedTide(step / 3_600_000.0);
            r.stepTideNow();
            r.stepAnimalsScene(step / 1000);
            r.glocken.step(step / 1000, r.sturmnacht());
        }
    }

    static void shot(Renderer r, GLOffscreenAutoDrawable d, SceneClock clock, File png, String tag, String label, java.util.List<java.awt.image.BufferedImage> imgs, java.util.List<String> labels) throws Exception {
        int settle = Integer.parseInt(System.getenv().getOrDefault("RUHE", "12"));
        for (int i = 0; i < settle; i++) { r.step(1 / 60.0); d.display(); }
        Thread.sleep(400);
        r.sky.epochMillis = clock.epochMillis();
        r.sky.updateNow();
        r.readapt();
        for (int i = 0; i < 4; i++) { r.step(1 / 60.0); d.display(); }
        final java.awt.image.BufferedImage[] img = new java.awt.image.BufferedImage[1];
        d.invoke(true, x -> { img[0] = r.grab(x); return true; });
        File out = new File(png.getPath().replace(".png", "_" + tag + ".png"));
        File dir = out.getAbsoluteFile().getParentFile();
        if (dir != null) dir.mkdirs();
        javax.imageio.ImageIO.write(img[0], "png", out);
        imgs.add(img[0]);
        String l = clock.timeLabel() + " · " + label + String.format(Locale.GERMAN, " · Pegel NHN %+.2f m", r.level());
        labels.add(l);
        System.out.println("Bild " + out.getPath() + " · " + l + " · " + r.glocken.describe() + " · " + r.grabung.describe() + " · " + r.shortWeatherLine());
    }

    static void sheet(java.util.List<java.awt.image.BufferedImage> imgs, java.util.List<String> labels, File png) throws Exception {
        int cols = Math.min(4, imgs.size()), rows = (imgs.size() + cols - 1) / cols, tw = 480, th = tw * imgs.get(0).getHeight() / imgs.get(0).getWidth();
        java.awt.image.BufferedImage sheet = new java.awt.image.BufferedImage(cols * tw, rows * (th + 22), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(11, 18, 25));
        g.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 11));
        for (int k = 0; k < imgs.size(); k++) {
            int x = (k % cols) * tw, y = (k / cols) * (th + 22);
            g.drawImage(imgs.get(k), x, y, tw, th, null);
            g.setColor(new java.awt.Color(200, 214, 224));
            String l = labels.get(k);
            g.drawString(l.length() > 80 ? l.substring(0, 80) + "…" : l, x + 6, y + th + 15);
        }
        g.dispose();
        File out = new File(png.getPath().replace(".png", "_tag.png"));
        javax.imageio.ImageIO.write(sheet, "png", out);
        System.out.println("Bogen: " + out.getPath());
    }
}
