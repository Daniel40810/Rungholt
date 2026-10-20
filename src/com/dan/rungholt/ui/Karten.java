package com.dan.rungholt.ui;

import com.dan.foptionpane.FOptionPane;
import com.dan.rungholt.data.Daten;
import com.dan.rungholt.data.Datenseite;
import com.dan.rungholt.data.Startdaten;
import com.dan.rungholt.gl.Renderer;
import com.dan.store.Dienst;
import com.dan.store.Protokoll;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Phase 16 (F30, F31, F36): was die Datenbank an die Karten im Bild hängt. Die Warftkarte bekommt die Zeile ihrer
 * Warft aus RH_WARFT (Häuser, Brunnen, Befund, Quelle) und die Fundstellen in 100 m (auch eigene), die Zellkarte des
 * Watts die Fundstellen, die Warft darunter oder die nächste, wie oft die Stelle bei Niedrigwasser nass blieb (aus der
 * Chronik) und die Wenden des Tages aus dem Tidenkalender. Umschalt + Klick ins Watt legt eine eigene Fundstelle an.
 * Gelesen wird im Faden der Daten; das Bild wartet nicht.
 */
public final class Karten {
    private Karten() { }

    /** Hängt an die eben geöffnete Karte an, was der Speicher weiß. */
    public static void ergaenzen(Renderer r) {
        String[] karte = r.warftCard;
        double[] hit = r.lastHit;
        var wf = r.lastWarft;
        if (karte == null || hit == null) return;
        Dienst.spaeter(() -> {
            List<String> l = new ArrayList<>();
            try {
                var sp = Datenseite.speicher();
                l.add("#" + (sp.datenbank() ? "Datenbank" : "Dateien in daten/"));
                double x = wf != null ? wf.x() : hit[0], z = wf != null ? wf.z() : hit[2];
                if (wf != null)
                    for (Daten.Warft w : sp.warften())
                        if (w.name().equals(wf.name())) {
                            l.add(String.format(Locale.GERMAN, "RH_WARFT Nr. %d · %s · %d %s, %d %s · Krone %.0f m² · %s (%s)", w.nr(), w.art(), w.haeuser(),
                                    w.haeuser() == 1 ? "Haus" : "Häuser", w.brunnen(), w.brunnen() == 1 ? "Brunnen" : "Brunnen", w.flaeche().flaecheM2(),
                                    w.befund().toLowerCase(Locale.GERMAN), w.quelle()));
                            break;
                        }
                Daten.Zelle c = sp.zelle(x, z);
                if (wf == null && c.warft() != null)
                    l.add(c.warftAbstandM() <= 0.5 ? "Auf der " + c.warft() + " von 1362" : String.format(Locale.GERMAN, "Nächste Warft von 1362: %s in %.0f m", c.warft(), c.warftAbstandM()));
                if (wf == null) {
                    if (c.niedrigwasser() == 0) l.add("Chronik: noch kein Niedrigwasser mit Wasserlinie");
                    else l.add(String.format(Locale.GERMAN, "Chronik: bei %d von %d Niedrigwassern nass, %d-mal trocken", c.nass(), c.niedrigwasser(), c.niedrigwasser() - c.nass()));
                    long tag = Daten.beginn(r.clock.date());
                    StringBuilder k = new StringBuilder();
                    for (Daten.Kalender kk : sp.kalender(Startdaten.RAND, tag, tag + 86_400_000L))
                        k.append(k.length() > 0 ? " · " : "").append(kk.art()).append(' ').append(kk.text(), 11, 16).append(String.format(Locale.GERMAN, " %+.2f m", kk.hoeheM()));
                    if (k.length() > 0) l.add("Tidenkalender (Rand): " + k);
                }
                l.add(wf != null ? "#Fundstellen in 100 m (Datenbank, mit eigenen)" : "#Fundstellen in 100 m");
                if (c.funde().isEmpty()) l.add("keine");
                for (String f : c.funde()) l.add("– " + f);
                if (wf == null) l.add("Umschalt + Klick legt hier eine eigene Fundstelle an.");
            } catch (Exception e) {
                l.add("#Daten");
                l.add("nicht lesbar: " + e.getMessage());
            }
            r.cardAppend(karte, l);
        });
    }

    /** Umschalt + Klick: eine eigene Fundstelle an der Stelle im Watt (fragt nach dem Namen). */
    public static void fundstelle(Component parent, Renderer r, int px, int py, int w, int h) {
        double[] hit = r.pickGround(px, py, w, h);
        if (hit == null) return;
        SwingUtilities.invokeLater(() -> {
            double[] ll = com.dan.rungholt.scene.Site.latLon(hit[0], hit[2]);
            String name = FOptionPane.showInputDialog(parent, "Eigene Fundstelle bei " + com.dan.rungholt.scene.Site.dms(ll[0], ll[1])
                    + String.format(Locale.GERMAN, " (%.0f m %s, %.0f m %s der Kirche).%nWas liegt hier?", Math.abs(hit[0]), hit[0] < 0 ? "westlich" : "östlich",
                    Math.abs(hit[2]), hit[2] < 0 ? "nördlich" : "südlich"), "Rungholt · Fundstelle", FOptionPane.QUESTION_MESSAGE);
            if (name == null || name.isBlank()) return;
            String n = name.strip().length() > 80 ? name.strip().substring(0, 80) : name.strip();
            var f = new Daten.Fundstelle(0, n, "eigener Fund", r.clock.date().getYear(), System.getProperty("user.name", ""), hit[0], hit[2], Daten.GESTALTET, null,
                    "Umschalt + Klick am " + Daten.text(r.clock.epochMillis()), Daten.EIGEN);
            Dienst.spaeter(() -> {
                try {
                    long id = Datenseite.speicher().fundstelle(f, Dienst.sitzung() > 0 ? Dienst.sitzung() : null);
                    Protokoll.info("Fundstelle", "„" + n + "“ angelegt (" + id + ") in " + Datenseite.wo());
                    r.hudMeldung("Fundstelle „" + n + "“ angelegt · " + Datenseite.wo());
                } catch (Exception e) {
                    Protokoll.warnung("Fundstelle", "nicht angelegt: " + e.getMessage());
                    r.hudMeldung("!Fundstelle nicht angelegt: " + e.getMessage());
                }
            });
        });
    }
}
