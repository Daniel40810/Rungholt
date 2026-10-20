package com.dan.rungholt.ui;

import com.dan.rungholt.data.Daten;
import com.dan.rungholt.data.Zustand;
import com.dan.rungholt.gl.Quality;
import com.dan.rungholt.gl.Renderer;
import com.dan.store.Felder;
import com.dan.store.Protokoll;

import javax.swing.Timer;
import java.util.List;
import java.util.Map;

/**
 * „Weiter wie zuletzt“ (F36 A): einen gesicherten Zustand herstellen, sobald die Überflutung steht (höchstens 60 s
 * warten). Uhr (auch 1362), Ära, Wetter, Pegel von Hand, Kamera und Regler kommen zurück; was gerade floss (die Nacht
 * der Mandränke, ein Regiebuch), beginnt man neu.
 */
public final class Weiter {
    private Weiter() { }

    private static boolean bereit(Renderer r) { return r.wiring != null && r.wiring.level.flood != null; }

    /** Wartet höchstens 60 s auf die Szene, dann stellt es her; danach läuft {@code danach} (darf null sein). */
    public static void anwenden(Renderer r, Daten.Sitzung s, Runnable danach) {
        if (s == null || s.zustand() == null) return;
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            if (!bereit(r) && System.currentTimeMillis() < bis) return;
            t.stop();
            jetzt(r, s);
            if (danach != null) danach.run();
        });
        t.start();
    }

    /** Ein Regiebuch im Kino, sobald die Szene steht (höchstens 60 s warten). */
    public static void kino(Renderer r, com.dan.rungholt.scene.Regie.Book b) {
        long bis = System.currentTimeMillis() + 60_000;
        Timer t = new Timer(250, null);
        t.addActionListener(e -> {
            if (!bereit(r) && System.currentTimeMillis() < bis) return;
            t.stop();
            r.kinoStart(b);
        });
        t.start();
    }

    /** Sofort herstellen (die Szene steht schon). */
    public static void jetzt(Renderer r, Daten.Sitzung s) {
        Map<String, String> z = Felder.ausText(s.zustand());
        Quality q = Zustand.qualitaet(z);
        if (q != null && q != r.quality()) r.setQuality(q);
        if (z.containsKey("app.ton")) r.klang.setOn(Boolean.parseBoolean(z.get("app.ton")));
        List<String> f = Zustand.anwenden(r, z);
        Protokoll.info("Sitzung", "Weiter wie zuletzt (Sitzung " + s.id() + "): " + z.size() + " Werte" + (f.isEmpty() ? "" : ", nicht hergestellt: " + String.join(", ", f)));
        String rest = z.containsKey("mandraenke.stufe") ? " · die Mandränke lief (" + z.get("mandraenke.stufe") + "): Block MANDRÄNKE"
                : z.containsKey("regiebuch.name") ? " · „" + z.get("regiebuch.name") + "“ lief: Block REGIEBÜCHER" : "";
        r.hudMeldung("Weiter wie zuletzt · " + z.getOrDefault("uhr.text", "") + rest);
    }
}
