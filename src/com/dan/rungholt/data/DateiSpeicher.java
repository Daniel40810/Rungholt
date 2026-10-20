package com.dan.rungholt.data;

import com.dan.rungholt.scene.Regie;
import com.dan.store.DateiAblage;
import com.dan.store.Felder;
import com.dan.store.Zahl;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Speichern ohne Datenbank (F34 A): Textdateien im Ordner {@code daten/} des Projekts. Die mitgelieferten Einträge
 * kommen aus {@link Startdaten} und stehen nicht in Dateien; eigene liegen je in einer Datei. Die Chronik und die
 * eigenen Fundstellen liegen als CSV mit Semikolon und einer Spalte {@code WKT} in Länge und Breite (QGIS: „Getrennte
 * Texte“, Geometrie WKT, EPSG:4326):
 * <pre>
 * daten/chronik/tiden.csv  zaehlungen.csv  mandraenke.csv  glocken.csv  regielaeufe.csv  fundstellen.csv
 * daten/regiebuecher/*.txt   daten/ansichten/N.txt + N.zustand + N.png   daten/presets/paket/Klasse/*.txt
 * daten/sitzungen/N.txt + N.zustand   daten/protokoll.txt
 * </pre>
 * Beim nächsten Verbinden übernimmt {@link Uebernahme} die Chronik in die Datenbank und benennt die Dateien in
 * {@code *.uebernommen} um.
 */
public final class DateiSpeicher extends DateiAblage implements Speicher {

    public DateiSpeicher(File dir) { super(dir); }

    /** Der Ordner daten neben db (oder -Drungholt.daten). */
    public static File ordner() {
        String p = System.getProperty("rungholt.daten");
        if (p != null && !p.isEmpty()) return new File(p);
        File db = Verbindung.ordner().getAbsoluteFile();
        File base = db.getParentFile();
        return new File(base != null ? base : new File("."), "daten");
    }

    private static void eigen(boolean mitgeliefert, String was) throws Fehler {
        if (mitgeliefert) throw new Fehler("„" + was + "“ ist mitgeliefert und wird nicht überschrieben; bitte unter einem eigenen Namen speichern");
    }

    // ------------------------------------------------------------------ CSV

    /** Eine CSV-Datei: Semikolon, Text in Anführungszeichen, erste Zeile die Spalten. */
    static final class Csv {
        final File f;
        final String[] spalten;
        List<LinkedHashMap<String, String>> zeilen;

        Csv(File f, String... spalten) { this.f = f; this.spalten = spalten; }

        synchronized List<LinkedHashMap<String, String>> lesen() throws Fehler {
            if (zeilen != null) return zeilen;
            zeilen = new ArrayList<>();
            if (!f.isFile()) return zeilen;
            try {
                List<String> l = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
                if (l.isEmpty()) return zeilen;
                String[] kopf = teile(l.get(0));
                for (int i = 1; i < l.size(); i++) {
                    if (l.get(i).isBlank()) continue;
                    String[] v = teile(l.get(i));
                    LinkedHashMap<String, String> m = new LinkedHashMap<>();
                    for (int k = 0; k < kopf.length; k++) m.put(kopf[k], k < v.length ? v[k] : "");
                    zeilen.add(m);
                }
                return zeilen;
            } catch (IOException e) {
                throw new Fehler(f + " nicht lesbar: " + e.getMessage(), e);
            }
        }

        synchronized void schreiben() throws Fehler {
            StringBuilder b = new StringBuilder(String.join(";", spalten)).append('\n');
            for (Map<String, String> m : zeilen) {
                for (int k = 0; k < spalten.length; k++) b.append(k > 0 ? ";" : "").append(feld(m.getOrDefault(spalten[k], "")));
                b.append('\n');
            }
            schreibe(f, b.toString());
        }

        /** Eine Zeile anhängen (die Datei wird ganz geschrieben, die Chronik bleibt klein). */
        synchronized long anhaengen(LinkedHashMap<String, String> m) throws Fehler {
            long id = naechste();
            LinkedHashMap<String, String> z = new LinkedHashMap<>();
            z.put("ID", Long.toString(id));
            z.putAll(m);
            lesen().add(z);
            schreiben();
            return id;
        }

        synchronized long naechste() throws Fehler {
            long n = 0;
            for (Map<String, String> m : lesen()) n = Math.max(n, (long) Zahl.lies(m.getOrDefault("ID", "0")));
            return n + 1;
        }

        static String feld(String v) {
            if (v == null) return "";
            if (v.indexOf(';') < 0 && v.indexOf('"') < 0 && v.indexOf('\n') < 0 && v.indexOf(',') < 0) return v;
            return "\"" + v.replace("\"", "\"\"").replace("\n", " ") + "\"";
        }

        static String[] teile(String z) {
            List<String> l = new ArrayList<>();
            StringBuilder b = new StringBuilder();
            boolean q = false;
            for (int i = 0; i < z.length(); i++) {
                char c = z.charAt(i);
                if (q) {
                    if (c == '"' && i + 1 < z.length() && z.charAt(i + 1) == '"') { b.append('"'); i++; }
                    else if (c == '"') q = false;
                    else b.append(c);
                } else if (c == '"') q = true;
                else if (c == ';') { l.add(b.toString()); b.setLength(0); }
                else b.append(c);
            }
            l.add(b.toString());
            return l.toArray(new String[0]);
        }
    }

    private Csv tiden, zaehl, mandr, glock, regie, funde;

    private synchronized Csv ti() {
        if (tiden == null) tiden = new Csv(sub("chronik", "tiden.csv"), "ID", "SITZUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "AERA", "ART", "PEGEL_M", "ASTRO_M", "WINDSTAU_M",
                "TROCKEN_ANTEIL", "PFUETZEN", "STROEMUNG_MAX", "WKT");
        return tiden;
    }
    private synchronized Csv za() {
        if (zaehl == null) zaehl = new Csv(sub("chronik", "zaehlungen.csv"), "ID", "SITZUNG", "TIDE", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "ART", "GRUPPE", "ANZAHL", "LIEGEND",
                "HEULER", "GESTOERT", "X_M", "Z_M", "WKT");
        return zaehl;
    }
    private synchronized Csv ma() {
        if (mandr == null) mandr = new Csv(sub("chronik", "mandraenke.csv"), "ID", "SITZUNG", "STELLE", "SZENE_TAG", "BEGINN_MS", "STURM_MS", "UEBERLAUF_MS", "BRUCH_MS", "FLUT_MS",
                "MORGEN_MS", "BRUCH_TEXT", "STUFE", "PEGEL_MAX_M", "MARSCH_MAX_M", "HAEUSER", "HAEUSER_ZERFALLEN", "STUECKE", "BRUCH_X", "BRUCH_Z", "REGIEBUCH", "BRESCHE_WKT", "WKT");
        return mandr;
    }
    private synchronized Csv gl() {
        if (glock == null) glock = new Csv(sub("chronik", "glocken.csv"), "ID", "SITZUNG", "SZENE_MS", "SZENE_TEXT", "SZENE_TAG", "DAUER_S", "WIND_MS", "RICHTUNG_GRAD",
                "WASSER_KIRCHWARFT_M", "VON_HAND");
        return glock;
    }
    private synchronized Csv rl() {
        if (regie == null) regie = new Csv(sub("chronik", "regielaeufe.csv"), "ID", "SITZUNG", "NAME", "BEGINN_MS", "ENDE_MS", "VOLLSTAENDIG", "KINO", "BILDER_S_MITTEL",
                "GPU_MS_MITTEL", "GEWARTET_S", "FOTOS", "QUALITAET", "BLATT_DATEI");
        return regie;
    }
    private synchronized Csv fu() {
        if (funde == null) funde = new Csv(sub("chronik", "fundstellen.csv"), "ID", "SITZUNG", "NAME", "FUND", "JAHR", "FINDER", "X_M", "Z_M", "BEFUND", "BEMERKUNG", "WKT");
        return funde;
    }

    /** Die Dateien der Chronik und der eigenen Fundstellen (für die Übernahme). */
    public List<File> chronikDateien() { return List.of(ti().f, za().f, ma().f, gl().f, rl().f, fu().f); }

    private static String n(double v) { return Double.isNaN(v) ? "" : Zahl.text(Math.round(v * 1000) / 1000.0); }
    private static String n(Double v) { return v == null ? "" : n((double) v); }
    private static double d(String s) { return s == null || s.isEmpty() ? Double.NaN : Zahl.lies(s); }
    private static Long ln(String s) { return s == null || s.isEmpty() ? null : (long) Zahl.lies(s); }
    private static Integer in(String s) { return s == null || s.isEmpty() ? null : (int) Zahl.lies(s); }
    private static String s(Long v) { return v == null ? "" : v.toString(); }
    private static String s(Integer v) { return v == null ? "" : v.toString(); }
    private static String w(Daten.Geom g) { return g == null ? "" : g.wkt(); }

    /** Szenenzeit in drei Spalten (F33). */
    private static void sz(Map<String, String> m, long ms) {
        m.put("SZENE_MS", Long.toString(ms));
        m.put("SZENE_TEXT", Daten.text(ms));
        m.put("SZENE_TAG", Daten.tag(ms));
    }

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        return new Daten.Stand(Startdaten.warften().size(), Startdaten.bauwerke(null).size(), fundstellen().size(), fu().lesen().size(), sturmfluten().size(),
                Startdaten.kalender().size(), ti().lesen().size(), za().lesen().size(), ma().lesen().size(), gl().lesen().size(), regiebuecher().size(), rl().lesen().size(),
                ansichten().size(), presets(null, null).size(), befunde().size(), quellen().size(), letzteSitzung(-1));
    }

    @Override public List<Daten.Quelle> quellen() { return Startdaten.quellen(); }
    @Override public List<Daten.Befund> befunde() { return Startdaten.befunde(); }
    @Override public List<Daten.Sturmflut> sturmfluten() { return Startdaten.sturmfluten(); }
    @Override public List<Daten.Pegelort> pegelorte() { return Startdaten.pegelorte(); }

    @Override public List<Daten.Kalender> kalender(String pegel, long vonMs, long bisMs) {
        for (Daten.Pegelort p : Startdaten.pegelorte())
            if (p.kurz().equals(pegel)) {
                LocalDate a = Daten.datum(vonMs), b = Daten.datum(bisMs).plusDays(1);
                List<Daten.Kalender> l = new ArrayList<>();
                for (Daten.Kalender k : Startdaten.kalender(p, a, b)) if (k.ms() >= vonMs && k.ms() < bisMs) l.add(k);
                return l;
            }
        return List.of();
    }

    // ------------------------------------------------------------------ Rungholt

    @Override public List<Daten.Warft> warften() { return Startdaten.warften(); }

    /** Ohne Gelände: Sieltief und Sielhafen fehlen in den Dateien. */
    @Override public List<Daten.Bauwerk> bauwerke() { return Startdaten.bauwerke(null); }

    @Override public List<Daten.Fundstelle> fundstellen() throws Fehler {
        List<Daten.Fundstelle> l = new ArrayList<>(Startdaten.fundstellen());
        List<Daten.Fundstelle> e = new ArrayList<>();
        Csv c = fu();
        synchronized (c) {
            for (Map<String, String> m : c.lesen())
                e.add(new Daten.Fundstelle((long) d(m.get("ID")), m.get("NAME"), m.get("FUND"), in(m.get("JAHR")), m.get("FINDER"), d(m.get("X_M")), d(m.get("Z_M")),
                        m.get("BEFUND"), null, m.get("BEMERKUNG"), Daten.EIGEN));
        }
        Collections.reverse(e);
        l.addAll(e);
        return l;
    }

    @Override public long fundstelle(Daten.Fundstelle f, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        m.put("NAME", f.name());
        m.put("FUND", f.fund() == null ? "" : f.fund());
        m.put("JAHR", s(f.jahr()));
        m.put("FINDER", f.finder() == null ? "" : f.finder());
        m.put("X_M", n(Math.round(f.x() * 10) / 10.0));
        m.put("Z_M", n(Math.round(f.z() * 10) / 10.0));
        m.put("BEFUND", f.befund() == null ? Daten.GESTALTET : f.befund());
        m.put("BEMERKUNG", f.bemerkung() == null ? "" : f.bemerkung());
        m.put("WKT", Daten.Geom.punkt(f.x(), f.z()).wkt());
        return fu().anhaengen(m);
    }

    @Override public void loeschenFundstelle(long id) throws Fehler {
        Csv c = fu();
        synchronized (c) {
            String sid = Long.toString(id);
            if (!c.lesen().removeIf(m -> sid.equals(m.get("ID")))) throw new Fehler("Fundstelle " + id + " ist mitgeliefert oder gibt es nicht");
            c.schreiben();
        }
    }

    /** Abstand eines Punkts zu einer Fläche (0 innen), m. */
    static double abstand(double[][] p, double x, double z) {
        boolean innen = false;
        double best = Double.MAX_VALUE;
        for (int i = 0, j = p.length - 1; i < p.length; j = i++) {
            double[] a = p[j], b = p[i];
            if ((b[1] > z) != (a[1] > z) && x < (a[0] - b[0]) * (z - b[1]) / (a[1] - b[1]) + b[0]) innen = !innen;
            double dx = b[0] - a[0], dz = b[1] - a[1], l2 = dx * dx + dz * dz;
            double t = l2 < 1e-12 ? 0 : Math.max(0, Math.min(1, ((x - a[0]) * dx + (z - a[1]) * dz) / l2));
            best = Math.min(best, Math.hypot(x - a[0] - t * dx, z - a[1] - t * dz));
        }
        return innen ? 0 : best;
    }

    /** Liegt (x, z) in der Mehrfachfläche (außen ja, in keinem Loch)? */
    static boolean innen(Daten.Geom g, double x, double z) {
        if (g == null) return false;
        List<List<double[][]>> rs = g.art().equals("MULTIFLAECHE") ? g.ringe() : List.of(List.<double[][]>of(g.xz()));
        for (List<double[][]> teil : rs) {
            if (abstand(teil.get(0), x, z) > 0) continue;
            boolean loch = false;
            for (int i = 1; i < teil.size() && !loch; i++) if (abstand(teil.get(i), x, z) == 0) loch = true;
            if (!loch) return true;
        }
        return false;
    }

    @Override public Daten.Zelle zelle(double x, double z) throws Fehler {
        List<String> fs = new ArrayList<>();
        for (Daten.Fundstelle f : fundstellen()) {
            double dd = Math.hypot(f.x() - x, f.z() - z);
            if (dd <= 100) fs.add(f.name() + " (" + (f.jahr() == null ? "" : f.jahr() + ", ") + (Daten.EIGEN.equals(f.herkunft()) ? "eigen, " : "") + Math.round(dd) + " m)");
        }
        String warft = null;
        double ab = Double.MAX_VALUE;
        for (Daten.Warft w : Startdaten.warften()) {
            double dd = abstand(w.flaeche().xz(), x, z);
            if (dd < ab) { ab = dd; warft = w.name(); }
        }
        int nw = 0, nass = 0;
        Csv c = ti();
        synchronized (c) {
            for (Map<String, String> m : c.lesen()) {
                if (!"NW".equals(m.get("ART")) || m.getOrDefault("WKT", "").isEmpty()) continue;
                nw++;
                if (innen(Daten.Geom.ausWkt(m.get("WKT")), x, z)) nass++;
            }
        }
        return new Daten.Zelle(x, z, fs, warft, Math.round(ab * 10) / 10.0, nw, nass);
    }

    // ------------------------------------------------------------------ Chronik

    @Override public long tide(Daten.Tide t, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        sz(m, t.ms());
        m.put("AERA", t.aera());
        m.put("ART", t.art());
        m.put("PEGEL_M", n(t.pegelM()));
        m.put("ASTRO_M", n(t.astroM()));
        m.put("WINDSTAU_M", n(t.windstauM()));
        m.put("TROCKEN_ANTEIL", Zahl.text(Math.round(t.trockenAnteil() * 1e4) / 1e4));
        m.put("PFUETZEN", Integer.toString(t.pfuetzen()));
        m.put("STROEMUNG_MAX", n(t.stroemungMax()));
        m.put("WKT", w(t.wasserlinie()));
        return ti().anhaengen(m);
    }

    @Override public void zaehlungen(long tide, List<Daten.Zaehlung> zs, Long sitzung) throws Fehler {
        Csv c = za();
        synchronized (c) {
            for (Daten.Zaehlung z : zs) {
                LinkedHashMap<String, String> m = new LinkedHashMap<>();
                m.put("ID", Long.toString(c.naechste()));
                m.put("SITZUNG", s(sitzung));
                m.put("TIDE", Long.toString(tide));
                sz(m, z.ms());
                m.put("ART", z.art());
                m.put("GRUPPE", z.gruppe());
                m.put("ANZAHL", Integer.toString(z.anzahl()));
                m.put("LIEGEND", s(z.liegend()));
                m.put("HEULER", s(z.heuler()));
                m.put("GESTOERT", s(z.gestoert()));
                m.put("X_M", n(Math.round(z.x() * 10) / 10.0));
                m.put("Z_M", n(Math.round(z.z() * 10) / 10.0));
                m.put("WKT", Daten.Geom.punkt(z.x(), z.z()).wkt());
                c.lesen().add(m);
            }
            c.schreiben();
        }
    }

    @Override public long mandraenkeLauf(Daten.MandraenkeLauf l, Long sitzung) throws Fehler {
        Csv c = ma();
        synchronized (c) {
            LinkedHashMap<String, String> m = new LinkedHashMap<>();
            long id = l.id() > 0 ? l.id() : c.naechste();
            m.put("ID", Long.toString(id));
            m.put("SITZUNG", s(sitzung));
            m.put("STELLE", l.stelle());
            m.put("SZENE_TAG", Daten.tag(l.beginnMs()));
            m.put("BEGINN_MS", Long.toString(l.beginnMs()));
            m.put("STURM_MS", s(l.sturmMs()));
            m.put("UEBERLAUF_MS", s(l.ueberlaufMs()));
            m.put("BRUCH_MS", s(l.bruchMs()));
            m.put("FLUT_MS", s(l.flutMs()));
            m.put("MORGEN_MS", s(l.morgenMs()));
            m.put("BRUCH_TEXT", l.bruchText() == null ? "" : l.bruchText());
            m.put("STUFE", l.stufe());
            m.put("PEGEL_MAX_M", n(l.pegelMaxM()));
            m.put("MARSCH_MAX_M", n(l.marschMaxM()));
            m.put("HAEUSER", Integer.toString(l.haeuser()));
            m.put("HAEUSER_ZERFALLEN", Integer.toString(l.haeuserZerfallen()));
            m.put("STUECKE", Integer.toString(l.stuecke()));
            m.put("BRUCH_X", l.bruchstelle() == null ? "" : n(l.bruchstelle()[0]));
            m.put("BRUCH_Z", l.bruchstelle() == null ? "" : n(l.bruchstelle()[1]));
            m.put("REGIEBUCH", l.regiebuch() == null ? "" : l.regiebuch());
            m.put("BRESCHE_WKT", w(l.bresche()));
            m.put("WKT", l.bruchstelle() == null ? "" : Daten.Geom.punkt(l.bruchstelle()[0], l.bruchstelle()[1]).wkt());
            String sid = Long.toString(id);
            boolean da = false;
            for (int i = 0; i < c.lesen().size(); i++) if (sid.equals(c.lesen().get(i).get("ID"))) { c.lesen().set(i, m); da = true; break; }
            if (!da) c.lesen().add(m);
            c.schreiben();
            return id;
        }
    }

    @Override public void glocken(Daten.Glocken g, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        sz(m, g.ms());
        m.put("DAUER_S", n(g.dauerS()));
        m.put("WIND_MS", n(g.windMs()));
        m.put("RICHTUNG_GRAD", n(g.richtungGrad()));
        m.put("WASSER_KIRCHWARFT_M", n(g.wasserKirchwarftM()));
        m.put("VON_HAND", g.vonHand() ? "1" : "0");
        gl().anhaengen(m);
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("SITZUNG", s(sitzung));
        m.put("NAME", l.name());
        m.put("BEGINN_MS", Long.toString(l.beginnMs()));
        m.put("ENDE_MS", s(l.endeMs()));
        m.put("VOLLSTAENDIG", l.vollstaendig() ? "1" : "0");
        m.put("KINO", l.kino() ? "1" : "0");
        m.put("BILDER_S_MITTEL", n(l.bilderMittel()));
        m.put("GPU_MS_MITTEL", n(l.gpuMs()));
        m.put("GEWARTET_S", n(l.gewartetS()));
        m.put("FOTOS", Integer.toString(l.fotos()));
        m.put("QUALITAET", l.qualitaet() == null ? "" : l.qualitaet());
        m.put("BLATT_DATEI", l.blattDatei() == null ? "" : l.blattDatei());
        rl().anhaengen(m);
    }

    private static Daten.Tide tide(Map<String, String> m, boolean linie) {
        return new Daten.Tide((long) d(m.get("ID")), (long) d(m.get("SZENE_MS")), m.get("AERA"), m.get("ART"), d(m.get("PEGEL_M")), d(m.get("ASTRO_M")), d(m.get("WINDSTAU_M")),
                d(m.get("TROCKEN_ANTEIL")), (int) Math.max(0, d(m.get("PFUETZEN"))), d(m.get("STROEMUNG_MAX")), linie ? Daten.Geom.ausWkt(m.get("WKT")) : null);
    }

    @Override public List<Daten.Tide> tidenAm(LocalDate tag) throws Fehler {
        List<Daten.Tide> l = new ArrayList<>();
        Csv c = ti();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tag.toString().equals(m.get("SZENE_TAG"))) l.add(tide(m, true)); }
        l.sort((a, b) -> Long.compare(a.ms(), b.ms()));
        return l;
    }

    @Override public List<Daten.Tide> tiden(int n) throws Fehler {
        List<Daten.Tide> l = new ArrayList<>();
        Csv c = ti();
        synchronized (c) {
            List<LinkedHashMap<String, String>> z = c.lesen();
            for (int i = z.size() - 1; i >= 0 && l.size() < n; i--) l.add(tide(z.get(i), false));
        }
        return l;
    }

    /** Alle Tiden mit Sitzung (für die Übernahme): {Sitzung oder null, Tide, alte Nummer}. */
    public List<Object[]> tidenAlle() throws Fehler {
        List<Object[]> l = new ArrayList<>();
        Csv c = ti();
        synchronized (c) { for (Map<String, String> m : c.lesen()) l.add(new Object[]{ln(m.get("SITZUNG")), tide(m, true)}); }
        return l;
    }

    @Override public List<Daten.Zaehlung> zaehlungenAm(LocalDate tag) throws Fehler {
        List<Daten.Zaehlung> l = new ArrayList<>();
        Csv c = za();
        synchronized (c) { for (Map<String, String> m : c.lesen()) if (tag.toString().equals(m.get("SZENE_TAG"))) l.add(zaehlung(m)); }
        return l;
    }

    private static Daten.Zaehlung zaehlung(Map<String, String> m) {
        return new Daten.Zaehlung((long) d(m.get("SZENE_MS")), m.get("ART"), m.get("GRUPPE"), (int) d(m.get("ANZAHL")), in(m.get("LIEGEND")), in(m.get("HEULER")),
                in(m.get("GESTOERT")), d(m.get("X_M")), d(m.get("Z_M")));
    }

    /** Die Zählungen je alter Tide (für die Übernahme). */
    public Map<Long, List<Daten.Zaehlung>> zaehlungenJeTide() throws Fehler {
        Map<Long, List<Daten.Zaehlung>> r = new LinkedHashMap<>();
        Csv c = za();
        synchronized (c) { for (Map<String, String> m : c.lesen()) r.computeIfAbsent(ln(m.get("TIDE")), k -> new ArrayList<>()).add(zaehlung(m)); }
        return r;
    }

    @Override public List<Daten.MandraenkeLauf> mandraenkeLaeufe() throws Fehler {
        List<Daten.MandraenkeLauf> l = new ArrayList<>();
        Csv c = ma();
        synchronized (c) {
            for (Map<String, String> m : c.lesen()) {
                double bx = d(m.get("BRUCH_X")), bz = d(m.get("BRUCH_Z"));
                l.add(new Daten.MandraenkeLauf((long) d(m.get("ID")), m.get("STELLE"), (long) d(m.get("BEGINN_MS")), ln(m.get("STURM_MS")), ln(m.get("UEBERLAUF_MS")),
                        ln(m.get("BRUCH_MS")), ln(m.get("FLUT_MS")), ln(m.get("MORGEN_MS")), m.get("STUFE"), d(m.get("PEGEL_MAX_M")), d(m.get("MARSCH_MAX_M")),
                        (int) d(m.get("HAEUSER")), (int) d(m.get("HAEUSER_ZERFALLEN")), (int) d(m.get("STUECKE")), Double.isNaN(bx) ? null : new double[]{bx, bz},
                        Daten.Geom.ausWkt(m.get("BRESCHE_WKT")), m.get("REGIEBUCH").isEmpty() ? null : m.get("REGIEBUCH")));
            }
        }
        Collections.reverse(l);
        return l;
    }

    /** Glocken und Läufe der Regiebücher (für die Übernahme). */
    public List<Daten.Glocken> glockenAlle() throws Fehler {
        List<Daten.Glocken> l = new ArrayList<>();
        for (Map<String, String> m : gl().lesen())
            l.add(new Daten.Glocken((long) d(m.get("SZENE_MS")), d(m.get("DAUER_S")), d(m.get("WIND_MS")), d(m.get("RICHTUNG_GRAD")), d(m.get("WASSER_KIRCHWARFT_M")),
                    "1".equals(m.get("VON_HAND"))));
        return l;
    }

    public List<Daten.Regielauf> regielaeufe() throws Fehler {
        List<Daten.Regielauf> l = new ArrayList<>();
        for (Map<String, String> m : rl().lesen())
            l.add(new Daten.Regielauf(m.get("NAME"), (long) d(m.get("BEGINN_MS")), ln(m.get("ENDE_MS")), "1".equals(m.get("VOLLSTAENDIG")), "1".equals(m.get("KINO")),
                    d(m.get("BILDER_S_MITTEL")), d(m.get("GPU_MS_MITTEL")), d(m.get("GEWARTET_S")), (int) Math.max(0, d(m.get("FOTOS"))), m.get("QUALITAET"), null,
                    m.get("BLATT_DATEI")));
        return l;
    }

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        int n = 0;
        String sid = Long.toString(sitzung);
        for (Csv c : new Csv[]{ti(), za(), ma(), gl(), rl()}) {
            synchronized (c) {
                int vor = c.lesen().size();
                c.lesen().removeIf(m -> sid.equals(m.get("SITZUNG")));
                n += vor - c.lesen().size();
                if (vor != c.lesen().size()) c.schreiben();
            }
        }
        return n;
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        Map<Long, long[]> m = new java.util.TreeMap<>(Collections.reverseOrder());
        for (Map<String, String> z : ti().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[2]++; }
        for (Map<String, String> z : za().lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0})[3]++; }
        for (Csv c : new Csv[]{ma(), gl(), rl()})
            for (Map<String, String> z : c.lesen()) { Long s = ln(z.get("SITZUNG")); if (s != null) m.computeIfAbsent(s, k -> new long[]{k, 0, 0, 0}); }
        List<long[]> l = new ArrayList<>();
        for (long[] v : m.values()) {
            try { v[1] = Long.parseLong(sitzung(v[0]).getOrDefault("beginn", "0")); } catch (Fehler | RuntimeException e) { v[1] = 0; }
            l.add(v);
            if (l.size() >= n) break;
        }
        return l;
    }

    // ------------------------------------------------------------------ Wetterlagen, Regiebücher, Fahrten

    @Override public List<Daten.Wetterlage> wetterlagen() { return Startdaten.wetterlagen(); }

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        List<Daten.Regiebuch> l = new ArrayList<>(Startdaten.regiebuecher());
        for (File f : dateien(sub("regiebuecher"))) {
            try {
                String t = Files.readString(f.toPath(), StandardCharsets.UTF_8);
                Regie.Book b = Regie.parse(t);
                l.add(new Daten.Regiebuch(b.name(), t, b.about(), null, Daten.EIGEN));
            } catch (IOException | RuntimeException e) {
                com.dan.store.Protokoll.warnung("Dateien", "Regiebuch " + f.getName() + " nicht lesbar: " + e.getMessage());
            }
        }
        return l;
    }

    private static boolean mitgeliefertesBuch(String name) {
        for (Daten.Regiebuch r : Startdaten.regiebuecher()) if (r.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.Regiebuch r) throws Fehler {
        eigen(mitgeliefertesBuch(r.name()), r.name());
        try { Regie.parse(r.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        schreibe(sub("regiebuecher", dateiname(r.name())), r.text());
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler { eigen(mitgeliefertesBuch(name), name); loeschen("regiebuecher", name); }

    @Override public List<Daten.Fahrt> fahrten() { return Startdaten.fahrten(); }

    // ------------------------------------------------------------------ Ansichten

    private static final String[] KAM = {"x", "y", "z", "gier", "nick"};

    private static Daten.Ansicht ansichtAus(Map<String, String> m, String zustand, byte[] png) {
        double[] k = new double[5];
        for (int i = 0; i < 5; i++) k[i] = Zahl.lies(m.get("kamera." + KAM[i]));
        String sm = m.get("szeneMs");
        return new Daten.Ansicht(Long.parseLong(m.get("id")), m.get("name"), null, "GEMERKT", k, null, m.getOrDefault("aera", Daten.HEUTE), "true".equals(m.get("unterWasser")),
                sm == null || sm.isEmpty() ? null : Long.parseLong(sm), zustand, png, Daten.EIGEN);
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        List<Daten.Ansicht> l = new ArrayList<>(Startdaten.ansichten());
        List<Daten.Ansicht> g = new ArrayList<>();
        for (File f : dateien(sub("ansichten"))) {
            LinkedHashMap<String, String> m = lies(f);
            if (m.get("id") != null) g.add(ansichtAus(m, null, null));
        }
        g.sort((a, b) -> Long.compare(b.id(), a.id()));
        l.addAll(g);
        return l;
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        File f = sub("ansichten", id + ".txt");
        if (!f.isFile()) throw new Fehler("Ansicht " + id + " gibt es nicht");
        LinkedHashMap<String, String> m = lies(f);
        File z = sub("ansichten", id + ".zustand"), p = sub("ansichten", id + ".png");
        try {
            return ansichtAus(m, z.isFile() ? Files.readString(z.toPath(), StandardCharsets.UTF_8) : null, p.isFile() ? Files.readAllBytes(p.toPath()) : null);
        } catch (IOException e) {
            throw new Fehler("Ansicht " + id + " nicht lesbar: " + e.getMessage(), e);
        }
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        long id = naechsteNummer(sub("ansichten"));
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("id", Long.toString(id));
        m.put("name", a.name());
        for (int i = 0; i < 5; i++) m.put("kamera." + KAM[i], Zahl.text(a.kamera()[i]));
        m.put("aera", a.aera() == null ? Daten.HEUTE : a.aera());
        m.put("unterWasser", Boolean.toString(a.unterWasser()));
        if (a.szeneMs() != null) { m.put("szeneMs", a.szeneMs().toString()); m.put("szeneText", Daten.text(a.szeneMs())); }
        if (sitzung != null) m.put("sitzung", sitzung.toString());
        schreibe(sub("ansichten", id + ".txt"), Felder.alsText(m, "Ansicht " + a.name()));
        if (a.zustand() != null) schreibe(sub("ansichten", id + ".zustand"), a.zustand());
        if (a.vorschau() != null) schreibe(sub("ansichten", id + ".png"), a.vorschau());
        return id;
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        loesche(sub("ansichten", id + ".txt"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".zustand"), "Ansicht " + id);
        loesche(sub("ansichten", id + ".png"), "Ansicht " + id);
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        List<Daten.PaketPreset> l = new ArrayList<>();
        for (Daten.PaketPreset p : Startdaten.presets())
            if ((paket == null || p.paket().equals(paket)) && (klasse == null || p.klasse().equals(klasse))) l.add(p);
        File base = sub("presets");
        File[] ps = base.listFiles(File::isDirectory);
        if (ps == null) return l;
        for (File pd : ps) {
            if (paket != null && !pd.getName().equals(paket)) continue;
            File[] ks = pd.listFiles(File::isDirectory);
            if (ks == null) continue;
            for (File kd : ks) {
                if (klasse != null && !kd.getName().equals(klasse)) continue;
                for (File f : dateien(kd)) {
                    LinkedHashMap<String, String> m = lies(f);
                    String name = m.remove("#name"), about = m.remove("#beschreibung");
                    l.add(new Daten.PaketPreset(pd.getName(), kd.getName(), name == null ? f.getName().replace(".txt", "") : name, about, Daten.EIGEN, m));
                }
            }
        }
        return l;
    }

    private static boolean presetMitgeliefert(String paket, String klasse, String name) {
        for (Daten.PaketPreset p : Startdaten.presets()) if (p.paket().equals(paket) && p.klasse().equals(klasse) && p.name().equals(name)) return true;
        return false;
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        eigen(presetMitgeliefert(p.paket(), p.klasse(), p.name()), p.name());
        LinkedHashMap<String, String> m = new LinkedHashMap<>();
        m.put("#name", p.name());
        m.put("#beschreibung", p.beschreibung() == null ? "" : p.beschreibung());
        m.putAll(p.felder());
        schreibe(sub("presets", p.paket(), p.klasse(), dateiname(p.name())), Felder.alsText(m, "Preset " + p.paket() + "." + p.klasse() + " · " + p.name()));
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        eigen(presetMitgeliefert(paket, klasse, name), name);
        loesche(sub("presets", paket, klasse, dateiname(name)), name);
    }

    // ------------------------------------------------------------------ Sitzung

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler {
        LinkedHashMap<String, String> m = super.letzteSitzungText(ausser);
        if (m == null) return null;
        String ende = m.get("ende"), zz = m.get("zustandZeit");
        double rs = Zahl.lies(m.get(REGIE_SEKUNDE)), ms = Zahl.lies(m.get(SZENE_MS)), ae = Zahl.lies(m.get(AERA)), pg = Zahl.lies(m.get(PEGEL));
        return new Daten.Sitzung(Long.parseLong(m.get("id")), Long.parseLong(m.get("beginn")), ende == null || ende.isEmpty() ? null : Long.parseLong(ende),
                "true".equals(m.get("sauber")), m.get("zustand"), zz == null ? null : Long.parseLong(zz), leer(m.get(REGIEBUCH)), Double.isNaN(rs) ? null : rs,
                Double.isNaN(ms) ? null : (long) ms, Double.isNaN(ae) ? null : ae, Double.isNaN(pg) ? null : pg, leer(m.get(STUFE)));
    }

    private static String leer(String s) { return s == null || s.isEmpty() ? null : s; }
}
