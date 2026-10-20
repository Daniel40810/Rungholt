package com.dan.rungholt.data;

import com.dan.store.Ablage;
import com.dan.store.Protokoll;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Übernahme aus den Dateien in die Datenbank (F34 A): die Chronik (Tiden mit Wasserlinie, Zählungen, Läufe der
 * Mandränke, Glocken, Läufe der Regiebücher), die eigenen Fundstellen und die eigenen Regiebücher, Ansichten und
 * Presets. Die Nummern der Sitzungen aus den Dateien gibt es in der Datenbank nicht; die Zeilen kommen ohne Sitzung
 * hinein. Danach heißen die CSV {@code *.uebernommen}, damit sie kein zweites Mal kommen; die übrigen Dateien bleiben.
 */
public final class Uebernahme {
    private Uebernahme() { }

    /** Was übernommen wurde, als Text („12 Tiden, 30 Zählungen …“). */
    public static String uebernehmen(DateiSpeicher von, Speicher nach) throws Ablage.Fehler {
        int ti = 0, za = 0, ma = 0, gl = 0, rl = 0, fu = 0, rb = 0, an = 0, pr = 0;
        Map<Long, List<Daten.Zaehlung>> zj = von.zaehlungenJeTide();
        Map<Long, Long> tid = new HashMap<>();
        for (Object[] o : von.tidenAlle()) {
            Daten.Tide t = (Daten.Tide) o[1];
            long neu = nach.tide(t, null);
            tid.put(t.id(), neu);
            ti++;
        }
        for (Map.Entry<Long, List<Daten.Zaehlung>> e : zj.entrySet()) {
            Long neu = e.getKey() == null ? null : tid.get(e.getKey());
            if (neu == null) continue;
            nach.zaehlungen(neu, e.getValue(), null);
            za += e.getValue().size();
        }
        List<Daten.MandraenkeLauf> ml = new ArrayList<>(von.mandraenkeLaeufe());
        java.util.Collections.reverse(ml);
        for (Daten.MandraenkeLauf l : ml) {
            nach.mandraenkeLauf(new Daten.MandraenkeLauf(0, l.stelle(), l.beginnMs(), l.sturmMs(), l.ueberlaufMs(), l.bruchMs(), l.flutMs(), l.morgenMs(), l.stufe(),
                    l.pegelMaxM(), l.marschMaxM(), l.haeuser(), l.haeuserZerfallen(), l.stuecke(), l.bruchstelle(), l.bresche(), l.regiebuch()), null);
            ma++;
        }
        for (Daten.Glocken g : von.glockenAlle()) { nach.glocken(g, null); gl++; }
        for (Daten.Regielauf l : von.regielaeufe()) {
            byte[] png = null;
            if (l.blattDatei() != null && !l.blattDatei().isEmpty()) {
                try { png = java.nio.file.Files.readAllBytes(new File(l.blattDatei()).toPath()); } catch (Exception x) { /* ohne Blatt */ }
            }
            nach.regielauf(new Daten.Regielauf(l.name(), l.beginnMs(), l.endeMs(), l.vollstaendig(), l.kino(), l.bilderMittel(), l.gpuMs(), l.gewartetS(), l.fotos(),
                    l.qualitaet(), png, l.blattDatei()), null);
            rl++;
        }
        for (Daten.Fundstelle f : von.fundstellen()) if (Daten.EIGEN.equals(f.herkunft())) { nach.fundstelle(f, null); fu++; }
        for (Daten.Regiebuch r : von.regiebuecher()) if (Daten.EIGEN.equals(r.herkunft())) { nach.speichern(r); rb++; }
        for (Daten.Ansicht a : von.ansichten()) if (Daten.EIGEN.equals(a.herkunft())) { nach.speichern(von.ansicht(a.id()), null); an++; }
        for (Daten.PaketPreset p : von.presets(null, null)) if (Daten.EIGEN.equals(p.herkunft())) { nach.speichern(p); pr++; }
        List<String> umbenannt = new ArrayList<>();
        for (File f : von.chronikDateien()) {
            if (!f.isFile()) continue;
            File neu = new File(f.getPath() + ".uebernommen");
            for (int i = 2; neu.exists(); i++) neu = new File(f.getPath() + "." + i + ".uebernommen");
            if (f.renameTo(neu)) umbenannt.add(f.getName());
        }
        String t = String.format(java.util.Locale.GERMAN, "%d Tiden, %d Zählungen, %d Läufe der Mandränke, %d Glocken, %d Läufe der Regiebücher, %d Fundstellen, "
                + "%d Regiebücher, %d Ansichten, %d Presets", ti, za, ma, gl, rl, fu, rb, an, pr);
        Protokoll.info("Übernahme", t + " aus " + von.wo() + " nach " + nach.wo() + (umbenannt.isEmpty() ? "" : "; umbenannt: " + String.join(", ", umbenannt)));
        return t;
    }

    /** Ob es in den Dateien etwas zu übernehmen gibt. */
    public static boolean etwasDa(DateiSpeicher d) {
        for (File f : d.chronikDateien()) if (f.isFile()) return true;
        return false;
    }
}
