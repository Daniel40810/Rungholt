package com.dan.rungholt.data;

import com.dan.rungholt.wiring.Wiring;
import com.dan.store.Ablage;
import com.dan.store.Protokoll;
import com.dan.tide.Tide;

import java.util.List;
import java.util.Locale;

/**
 * Die Tide aus der Datenbank (F26 A): beim Start liest die App die Pegelorte mit ihren Partialtiden und setzt sie in die
 * Tide des Rands (Norderhever) und in den Pegel Husum; fehlt etwas, bleibt der Code ({@code Tide.hever()},
 * {@code Tide.husum()}). Die Prüfung vergleicht beide Kurven über ein Jahr (höchstens 1 mm Abstand).
 */
public final class Pegel {
    private Pegel() { }

    /** Setzt Mittel und Partialtiden einer Tide aus einem Pegelort (die Tide bleibt dasselbe Objekt). */
    public static void setzen(Tide t, Daten.Pegelort p) {
        // gleich viele: Stück für Stück ersetzen (set ändert die Liste nicht strukturell, ein Faden, der gerade rechnet, stolpert nicht)
        List<Daten.Partialtide> n = p.tiden();
        t.mean = p.mittelM();
        if (t.constituents.size() == n.size())
            for (int i = 0; i < n.size(); i++) t.constituents.set(i, new Tide.Constituent(n.get(i).name(), n.get(i).gradJeH(), n.get(i).amplitudeM(), n.get(i).phaseGrad()));
        else {
            t.constituents.clear();
            for (Daten.Partialtide c : n) t.add(c.name(), c.gradJeH(), c.amplitudeM(), c.phaseGrad());
        }
    }

    /** Liest die Pegelorte und setzt Rand und Husum; gibt zurück, woher die Tide kommt (für HUD und Block DATEN). */
    public static String laden(Wiring w, Speicher s) {
        try {
            List<Daten.Pegelort> l = s.pegelorte();
            Daten.Pegelort rand = null, husum = null;
            for (Daten.Pegelort p : l) {
                if (p.kurz().equals(Startdaten.RAND)) rand = p;
                if (p.kurz().equals(Startdaten.HUSUM)) husum = p;
            }
            if (rand == null || rand.tiden().isEmpty() || husum == null || husum.tiden().isEmpty()) {
                Datenseite.tideHerkunft = "Code (Pegelorte fehlen in " + s.wo() + ")";
                return Datenseite.tideHerkunft;
            }
            double d = Math.max(abweichung(rand, Tide.hever(), 2026), abweichung(husum, Tide.husum(), 2026));
            setzen(w.level.tide, rand);
            setzen(w.level.husum, husum);
            Datenseite.tideHerkunft = String.format(Locale.GERMAN, "%s (%d + %d Partialtiden, %.2f mm vom Code)", s.datenbank() ? "Datenbank" : "Dateien",
                    rand.tiden().size(), husum.tiden().size(), d * 1000);
            Protokoll.info("Tide", "Pegel aus " + s.wo() + ": " + Datenseite.tideHerkunft);
        } catch (Ablage.Fehler | RuntimeException e) {
            Datenseite.tideHerkunft = "Code (" + e.getMessage() + ")";
            Protokoll.warnung("Tide", "Pegelorte nicht lesbar, die Tide bleibt aus dem Code: " + e.getMessage());
        }
        return Datenseite.tideHerkunft;
    }

    /** Größter Abstand (m) der Kurve eines Pegelorts von einer Tide über ein Jahr, alle 10 Minuten. */
    public static double abweichung(Daten.Pegelort p, Tide code, int jahr) {
        Tide t = Startdaten.tide(p);
        double a = Daten.beginn(java.time.LocalDate.of(jahr, 1, 1)) / 3_600_000.0, b = Daten.beginn(java.time.LocalDate.of(jahr + 1, 1, 1)) / 3_600_000.0, d = 0;
        for (double h = a; h < b; h += 1 / 6.0) d = Math.max(d, Math.abs(t.astronomical(h) - code.astronomical(h)));
        return d;
    }
}
