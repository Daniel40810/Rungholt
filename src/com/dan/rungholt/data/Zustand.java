package com.dan.rungholt.data;

import com.dan.rungholt.camera.Camera;
import com.dan.rungholt.gl.Quality;
import com.dan.rungholt.gl.Renderer;
import com.dan.store.Felder;
import com.dan.store.Zahl;
import com.dan.weather.Scenario;
import com.dan.weather.Weather;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Der ganze Zustand der Szene als Text (F36 A, „Weiter wie zuletzt“): alle öffentlichen Felder der Packages, dazu Uhr
 * (ms seit 1970, auch für 1362), Kamera, Ära, Wetterregie mit Handreglern, Pegel von Hand, das laufende Regiebuch mit
 * Stelle und die Stufe der Mandränke. Dasselbe Format dient für gemerkte Ansichten und die Dateien ohne Datenbank.
 * Was gerade fließt oder fällt (Überflutung, Bresche, Häuser in der Flut), kommt nicht zurück: die Mandränke beginnt
 * wieder im Block MANDRÄNKE, ein Regiebuch im Block REGIEBÜCHER.
 * <p>
 * Schlüssel der Form {@code teil.feld}: {@code ocean.windSpeed}, {@code kamera.x}, {@code regie.hand.WIND}.
 */
public final class Zustand {
    private Zustand() { }

    static final Set<String> OHNE = Set.of("quality");
    /** Anfragen, Zustände des Laufs, abgeleitete Größen. */
    static final Set<String> OHNE_RENDERER = Set.of("photoRequest", "frozen", "quality", "warftCard", "grabRequest", "photoDir", "lastSheet", "kinoFenster",
            "manualMandraenke", "welleOn", "spurForce", "sealCount", "renderScale");
    static final Set<String> OHNE_WIRING = Set.of("cloudsDrawn", "fogBase", "fogTop", "gustNow", "showerEnd", "pressureTendency", "quality", "regie");
    static final Set<String> OHNE_SKY = Set.of("epochMillis", "latDeg", "lonDeg", "quality");
    static final Set<String> OHNE_KLANG = Set.of("note", "offline", "on");

    /** Die Teile mit öffentlichen Feldern: Name im Zustand → Objekt. */
    static Map<String, Object> teile(Renderer r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("renderer", r);
        if (r.wiring != null) {
            m.put("wiring", r.wiring);
            m.put("wind", r.wiring.wind);
        }
        m.put("ocean", r.ocean);
        m.put("reed", r.reed);
        m.put("sky", r.sky);
        m.put("clouds", r.clouds);
        m.put("rain", r.rain);
        m.put("fog", r.fog);
        m.put("lightning", r.lightning);
        m.put("mudflat", r.mudflat);
        m.put("seal", r.sealKind);
        m.put("klang", r.klang);
        m.put("soundscape", r.klang.s);
        return m;
    }

    /** Das laufende Objekt einer Klasse mit Presets (Startdaten.PAKETE, einfacher Name) in der Szene, oder null. */
    public static Object objekt(Renderer r, String klasse) {
        if ("Tide".equals(klasse) && r.wiring != null) return r.wiring.level.tide;
        for (Object o : teile(r).values()) if (o != null && o.getClass().getSimpleName().equals(klasse)) return o;
        return null;
    }

    private static Set<String> ohne(String teil) {
        return switch (teil) {
            case "renderer" -> OHNE_RENDERER;
            case "wiring" -> OHNE_WIRING;
            case "sky" -> OHNE_SKY;
            case "klang" -> OHNE_KLANG;
            default -> OHNE;
        };
    }

    /** Erfasst den Zustand (aus dem Swing-Faden oder dem Faden der Daten; die Felder sind volatile). */
    public static LinkedHashMap<String, String> erfassen(Renderer r) {
        LinkedHashMap<String, String> z = new LinkedHashMap<>();
        z.put("app.version", Renderer.VERSION);
        z.put("app.qualitaet", r.quality().name());
        z.put("app.ton", Boolean.toString(r.klang.on));
        z.put("uhr.ms", Long.toString((long) Math.floor(r.clock.epochMillis())));
        z.put("uhr.text", Daten.text(r.clock.epochMillis()));
        z.put("uhr.lapse", Zahl.text(r.clock.lapse));
        Camera c = r.camera;
        z.put("kamera.modus", c.mode == Camera.Mode.KREISEN ? "KREISEN" : "FREI");
        z.put("kamera.kreisen", Boolean.toString(c.autoOrbit));
        double[] k = kamera(r);
        String[] n = {"x", "y", "z", "gier", "nick"};
        for (int i = 0; i < 5; i++) z.put("kamera." + n[i], Zahl.text(Math.round(k[i] * 1000) / 1000.0));
        z.put("kamera.ziel", Zahl.text(c.tx) + " " + Zahl.text(c.ty) + " " + Zahl.text(c.tz) + " " + Zahl.text(c.yaw) + " " + Zahl.text(c.pitch) + " " + Zahl.text(c.dist));
        if (r.wiring != null) {
            z.put("aera.ziel", Zahl.text(r.wiring.era.target));
            if (!Double.isNaN(r.wiring.level.hand)) z.put("pegel.hand", Zahl.text(r.wiring.level.hand));
            z.put("pegel.jetzt", Zahl.text(Math.round(r.wiring.level.level() * 1000) / 1000.0));
            z.put("pegel.windstau", Zahl.text(Math.round(r.wiring.level.tide.surge * 1000) / 1000.0));
            Weather w = r.wiring.weather;
            Weather.Snapshot ws = w.snapshot();
            z.put("regie.an", Boolean.toString(r.wiring.regie));
            z.put("regie.lage", ws != null ? ws.phase().name() : "KLAR");
            Scenario sc = w.scenario();
            if (sc != null && ws != null && ws.step() >= 0) {
                z.put("regie.text", sc.toText());
                z.put("regie.beginn", Zahl.text(w.scenarioStart()));
            }
            double[] h = w.handValues();
            for (int i = 0; i < h.length; i++) if (!Double.isNaN(h[i])) z.put("regie.hand." + Weather.Var.values()[i].name(), Zahl.text(h[i]));
        }
        var bk = r.regie.book();
        if (bk != null) {
            z.put("regiebuch.name", bk.name());
            z.put("regiebuch.sekunde", Zahl.text(Math.round(r.regie.time() * 100) / 100.0));
        }
        var m = r.mandraenke;
        if (m != null) {
            z.put("mandraenke.stufe", m.stage.name());
            z.put("mandraenke.stelle", m.stelle.name());
        }
        for (Map.Entry<String, Object> e : teile(r).entrySet())
            for (Map.Entry<String, String> f : Felder.lesen(e.getValue(), ohne(e.getKey())).entrySet()) z.put(e.getKey() + "." + f.getKey(), f.getValue());
        return z;
    }

    /** Die Spalten von RH_SITZUNG aus einem Zustand (für {@code Ablage.Sicherung}). */
    public static Map<String, String> spalten(Map<String, String> z) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(Speicher.SZENE_MS, z.getOrDefault("uhr.ms", ""));
        m.put(Speicher.ZEITRAFFER, z.getOrDefault("uhr.lapse", ""));
        m.put(Speicher.AERA, z.getOrDefault("aera.ziel", ""));
        m.put(Speicher.PEGEL, z.getOrDefault("pegel.jetzt", ""));
        m.put(Speicher.WINDSTAU, z.getOrDefault("pegel.windstau", ""));
        m.put(Speicher.STUFE, z.getOrDefault("mandraenke.stufe", ""));
        if (z.containsKey("regiebuch.name")) { m.put(Speicher.REGIEBUCH, z.get("regiebuch.name")); m.put(Speicher.REGIE_SEKUNDE, z.get("regiebuch.sekunde")); }
        return m;
    }

    /** Kamera für Ansichten: {x, y, z, Gier, Nick} aus dem laufenden Bild. */
    public static double[] kamera(Renderer r) {
        Camera c = r.camera;
        double[] e = c.eye, f = c.fwd;
        double nick = Math.toDegrees(-Math.asin(Math.max(-1, Math.min(1, f[1]))));
        return new double[]{e[0], e[1], e[2], c.heading(), nick};
    }

    /** Szenenzeit des Zustands (ms) oder null. */
    public static Long szeneMs(Map<String, String> z) {
        double v = Zahl.lies(z.get("uhr.ms"));
        return Double.isNaN(v) ? null : (long) v;
    }

    public static Quality qualitaet(Map<String, String> z) {
        try { return Quality.valueOf(z.get("app.qualitaet")); } catch (RuntimeException e) { return null; }
    }

    /**
     * Stellt den Zustand her (aus dem Swing-Faden). Qualität und Ton stellt der Aufrufer ein; hier alles andere. Gibt
     * zurück, was nicht ging.
     */
    public static List<String> anwenden(Renderer r, Map<String, String> z) {
        List<String> fehler = new ArrayList<>();
        for (Map.Entry<String, Object> e : teile(r).entrySet()) {
            Map<String, String> t = Felder.teil(z, e.getKey());
            t.keySet().removeIf(x -> x.contains("."));
            for (String f : Felder.schreiben(e.getValue(), t, ohne(e.getKey()))) fehler.add(e.getKey() + "." + f);
        }
        Long ms = szeneMs(z);
        if (ms != null) r.clock.setEpochMillis(ms);
        double lapse = Zahl.lies(z.get("uhr.lapse"));
        if (!Double.isNaN(lapse)) r.clock.lapse = lapse;
        Camera c = r.camera;
        c.stopFahrt();
        c.stopMitfahrt();
        String[] ziel = z.getOrDefault("kamera.ziel", "").split(" ");
        if ("KREISEN".equals(z.get("kamera.modus")) && ziel.length == 6) {
            c.mode = Camera.Mode.KREISEN;
            c.lookAt(Zahl.lies(ziel[0]), Zahl.lies(ziel[1]), Zahl.lies(ziel[2]), Zahl.lies(ziel[3]), Zahl.lies(ziel[4]), Zahl.lies(ziel[5]));
            c.autoOrbit = "true".equals(z.get("kamera.kreisen"));
            c.snap();
        } else {
            double x = Zahl.lies(z.get("kamera.x")), y = Zahl.lies(z.get("kamera.y")), zz = Zahl.lies(z.get("kamera.z"));
            if (!Double.isNaN(x) && !Double.isNaN(y) && !Double.isNaN(zz)) {
                c.mode = Camera.Mode.FREI;
                c.place(x, y, zz, Zahl.lies(z.get("kamera.gier")), Zahl.lies(z.get("kamera.nick")));
                c.snap();
            }
        }
        if (r.wiring != null) {
            double era = Zahl.lies(z.get("aera.ziel"));
            if (!Double.isNaN(era)) { r.wiring.era.target = era; r.wiring.era.blend = era; }
            double hand = Zahl.lies(z.get("pegel.hand"));
            r.wiring.level.hand = hand;
            Weather w = r.wiring.weather;
            double now = r.clock.epochMillis() / 1000.0;
            String text = z.get("regie.text");
            boolean gestartet = false;
            if (text != null) {
                try {
                    Scenario sc = Scenario.parse(text);
                    double b = Zahl.lies(z.get("regie.beginn"));
                    w.start(sc, Double.isNaN(b) ? now : b);
                    gestartet = true;
                } catch (RuntimeException ex) {
                    fehler.add("regie.text: " + ex.getMessage());
                }
            }
            if (!gestartet) {
                Weather.Phase p;
                try { p = Weather.Phase.valueOf(z.getOrDefault("regie.lage", "KLAR")); } catch (IllegalArgumentException ex) { p = Weather.Phase.KLAR; }
                w.startFree(now, p);
            }
            r.wiring.resumeRegie();
            for (Weather.Var v : Weather.Var.values()) {
                double hv = Zahl.lies(z.get("regie.hand." + v.name()));
                if (!Double.isNaN(hv)) r.wiring.hand(v, hv);
            }
            r.wiring.regie = !"false".equals(z.get("regie.an"));
        }
        return fehler;
    }
}
