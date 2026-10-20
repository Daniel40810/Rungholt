package com.dan.rungholt.data;

import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Site;
import com.dan.store.Zahl;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Die Datensätze, die {@link Speicher} liest und schreibt (Phase 16, Werkbuch Abschnitt 8, F25–F36 „alle A“). Je
 * Tabelle ein Record mit den Spalten, die die App braucht; ID, ANGELEGT und GEAENDERT führt die Datenbank selbst.
 * Lagen stehen in Szenenmetern (x nach Osten, z nach Süden, Ursprung an der Kirche); Länge und Breite rechnet
 * {@link #lonLat}.
 * <p>
 * Szenenzeit (F33): Millisekunden seit 1970 (UTC) im proleptischen gregorianischen Kalender, wie die Uhr der Szene
 * zählt, dazu als Text („16.01.1362 22:00 Ortszeit“) und als Tag („1362-01-16“). Oracle rechnet vor dem 15.10.1582
 * julianisch; darum ist die Zahl der Schlüssel und der TIMESTAMP nur eine Sicht ab 1583.
 */
public final class Daten {
    private Daten() { }

    /** HERKUNFT: mitgeliefert (stellt der Einrichter wieder her) oder eigen (fasst er nie an). */
    public static final String MITGELIEFERT = "MITGELIEFERT", EIGEN = "EIGEN";
    /** BEFUND: belegt, gemessen oder gestaltet [G]. */
    public static final String BELEGT = "BELEGT", GEMESSEN = "GEMESSEN", GESTALTET = "GESTALTET";
    /** Ära einer Zeile. */
    public static final String HEUTE = "HEUTE", A1362 = "1362";

    private static final DateTimeFormatter ZEIT = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm", Locale.GERMANY);

    /** {lon, lat} eines Szenenpunkts (x, z). */
    public static double[] lonLat(double x, double z) {
        double[] ll = Site.latLon(x, z);
        return new double[]{ll[1], ll[0]};
    }

    /** Szenenpunkt {x, z} aus Länge und Breite. */
    public static double[] xz(double lon, double lat) { return Site.xz(new double[]{lat, lon}); }

    /** Zeit der Szene aus ms seit 1970 in deutscher Zeit (vor 1893 Ortszeit). */
    public static ZonedDateTime zeit(double epochMs) {
        return Instant.ofEpochMilli((long) Math.floor(epochMs)).atZone(SceneClock.ZONE);
    }

    /** „16.01.1362 22:00 Ortszeit“, „21.07.2026 13:30 MESZ“ (F33 SZENE_TEXT). */
    public static String text(double epochMs) {
        ZonedDateTime z = zeit(epochMs);
        String zone = z.getYear() < 1893 ? "Ortszeit" : SceneClock.ZONE.getRules().isDaylightSavings(z.toInstant()) ? "MESZ" : "MEZ";
        return ZEIT.format(z) + " " + zone;
    }

    /** „1362-01-16“ (F33 SZENE_TAG). */
    public static String tag(double epochMs) { return zeit(epochMs).toLocalDate().toString(); }

    /** Der Tag als Datum (proleptisch gregorianisch). */
    public static LocalDate datum(double epochMs) { return zeit(epochMs).toLocalDate(); }

    /** Uhrzeit in Stunden (deutsche Zeit, vor 1893 Ortszeit). */
    public static double stunde(double epochMs) {
        ZonedDateTime z = zeit(epochMs);
        return z.getHour() + z.getMinute() / 60.0 + z.getSecond() / 3600.0;
    }

    /** Ära aus der Uhr: vor 1700 gilt 1362. */
    public static String aera(double epochMs) { return zeit(epochMs).getYear() < 1700 ? A1362 : HEUTE; }

    /** Beginn eines Tages (00:00 deutsche Zeit) in ms. */
    public static long beginn(LocalDate tag) { return tag.atStartOfDay(SceneClock.ZONE).toInstant().toEpochMilli(); }

    /**
     * Eine Geometrie in Szenenmetern: PUNKT, LINIE, MULTILINIE, FLAECHE oder MULTIFLAECHE. Bei MULTIFLAECHE ist
     * {@link #ringe} je Teil die Liste der Ringe (erster Ring außen, dann Löcher); sonst stehen die Punkte in
     * {@link #teile}.
     */
    public record Geom(String art, List<double[][]> teile, List<List<double[][]>> ringe) {
        public Geom {
            teile = List.copyOf(teile);
            List<List<double[][]>> r = new ArrayList<>();
            if (ringe != null) for (List<double[][]> x : ringe) r.add(List.copyOf(x));
            ringe = List.copyOf(r);
        }
        public Geom(String art, List<double[][]> teile) { this(art, teile, null); }
        public static Geom punkt(double x, double z) { return new Geom("PUNKT", List.<double[][]>of(new double[][]{{x, z}})); }
        public static Geom linie(double[][] p) { return new Geom("LINIE", List.<double[][]>of(p)); }
        public static Geom multilinie(List<double[][]> p) { return new Geom("MULTILINIE", p); }
        public static Geom flaeche(double[][] p) { return new Geom("FLAECHE", List.<double[][]>of(p)); }
        /** Mehrere Flächen mit Löchern: je Teil {außen, Loch, Loch …}. */
        public static Geom multiflaeche(List<List<double[][]>> teile) {
            List<double[][]> aussen = new ArrayList<>();
            for (List<double[][]> t : teile) aussen.add(t.get(0));
            return new Geom("MULTIFLAECHE", aussen, teile);
        }
        public double[][] xz() { return teile.get(0); }

        private static double[][] ll(double[][] xz) {
            double[][] r = new double[xz.length][];
            for (int i = 0; i < xz.length; i++) r[i] = lonLat(xz[i][0], xz[i][1]);
            return r;
        }

        /** Ein Ring in Länge und Breite, geschlossen, außen gegen den Uhrzeigersinn, Löcher mit ihm (wie Oracle es will). */
        private static String ring(double[][] xz, boolean aussen) {
            double[][] p = ll(xz);
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            if (a > 0 != aussen) {
                double[][] q = new double[p.length][];
                for (int i = 0; i < p.length; i++) q[i] = p[p.length - 1 - i];
                p = q;
            }
            StringBuilder b = new StringBuilder("(");
            for (int i = 0; i <= p.length; i++) {
                double[] q = p[i % p.length];
                b.append(i > 0 ? ", " : "").append(n7(q[0])).append(' ').append(n7(q[1]));
            }
            return b.append(')').toString();
        }

        private static String n7(double v) {
            String s = String.format(Locale.ROOT, "%.7f", v);
            while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
            return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
        }

        /** Als WKT in Länge und Breite (com.dan.store.Wkt; MULTILINESTRING und MULTIPOLYGON hier). */
        public String wkt() {
            return switch (art) {
                case "PUNKT" -> { double[] p = lonLat(xz()[0][0], xz()[0][1]); yield com.dan.store.Wkt.punkt(p[0], p[1]); }
                case "LINIE" -> com.dan.store.Wkt.linie(ll(xz()));
                case "FLAECHE" -> "POLYGON (" + ring(xz(), true) + ")";
                case "MULTIFLAECHE" -> {
                    StringBuilder b = new StringBuilder("MULTIPOLYGON (");
                    for (int t = 0; t < ringe.size(); t++) {
                        b.append(t > 0 ? ", " : "").append('(');
                        List<double[][]> r = ringe.get(t);
                        for (int i = 0; i < r.size(); i++) b.append(i > 0 ? ", " : "").append(ring(r.get(i), i == 0));
                        b.append(')');
                    }
                    yield b.append(')').toString();
                }
                default -> {
                    StringBuilder b = new StringBuilder("MULTILINESTRING (");
                    for (int i = 0; i < teile.size(); i++) {
                        String l = com.dan.store.Wkt.linie(ll(teile.get(i)));
                        b.append(i > 0 ? ", " : "").append(l.substring(l.indexOf('(')));
                    }
                    yield b.append(')').toString();
                }
            };
        }

        /** Aus WKT in Länge und Breite (wie {@code SDO_UTIL.TO_WKTGEOMETRY} es liefert) zurück in Szenenmeter. */
        public static Geom ausWkt(String wkt) {
            if (wkt == null || wkt.isBlank()) return null;
            String w = wkt.strip();
            if (w.startsWith("MULTIPOLYGON") || w.startsWith("POLYGON")) {
                List<List<double[][]>> teile = new ArrayList<>();
                // Ringe einzeln lesen: jede innerste Klammer ist ein Ring; neue Fläche bei „((“
                int tiefe = 0, start = -1;
                List<double[][]> akt = null;
                for (int i = 0; i < w.length(); i++) {
                    char ch = w.charAt(i);
                    if (ch == '(') {
                        tiefe++;
                        int ringTiefe = w.startsWith("MULTI") ? 3 : 2;
                        if (tiefe == ringTiefe - 1) { akt = new ArrayList<>(); teile.add(akt); }
                        if (tiefe == ringTiefe) start = i;
                    } else if (ch == ')') {
                        int ringTiefe = w.startsWith("MULTI") ? 3 : 2;
                        if (tiefe == ringTiefe && start >= 0 && akt != null) {
                            double[][] ll = com.dan.store.Wkt.punkte(w.substring(start, i + 1));
                            int n = ll.length > 3 ? ll.length - 1 : ll.length;
                            double[][] p = new double[n][];
                            for (int k = 0; k < n; k++) p[k] = Daten.xz(ll[k][0], ll[k][1]);
                            akt.add(p);
                            start = -1;
                        }
                        tiefe--;
                    }
                }
                if (w.startsWith("POLYGON")) return new Geom("FLAECHE", List.<double[][]>of(teile.get(0).get(0)));
                return multiflaeche(teile);
            }
            String art = w.startsWith("POINT") ? "PUNKT" : w.startsWith("MULTILINESTRING") ? "MULTILINIE" : "LINIE";
            List<double[][]> teile = new ArrayList<>();
            String[] stuecke = art.equals("MULTILINIE") ? w.substring(w.indexOf('(') + 1, w.lastIndexOf(')')).split("\\)\\s*,\\s*\\(") : new String[]{w};
            for (String s : stuecke) {
                double[][] ll = com.dan.store.Wkt.punkte(s);
                double[][] p = new double[ll.length][];
                for (int i = 0; i < ll.length; i++) p[i] = Daten.xz(ll[i][0], ll[i][1]);
                teile.add(p);
            }
            return new Geom(art, teile);
        }

        /** Länge aller Linien (m, in der Ebene der Szene). */
        public double laenge() {
            double s = 0;
            for (double[][] p : teile) for (int i = 0; i + 1 < p.length; i++) s += Math.hypot(p[i + 1][0] - p[i][0], p[i + 1][1] - p[i][1]);
            return s;
        }

        /** Fläche (m², nach Gauß; FLAECHE und MULTIFLAECHE ohne Löcher abzuziehen außer bei MULTIFLAECHE). */
        public double flaecheM2() {
            if (art.equals("MULTIFLAECHE")) {
                double s = 0;
                for (List<double[][]> t : ringe) for (int i = 0; i < t.size(); i++) s += (i == 0 ? 1 : -1) * gauss(t.get(i));
                return s;
            }
            return gauss(xz());
        }

        static double gauss(double[][] p) {
            double a = 0;
            for (int i = 0; i < p.length; i++) { double[] u = p[i], v = p[(i + 1) % p.length]; a += u[0] * v[1] - v[0] * u[1]; }
            return Math.abs(a) / 2;
        }

        /** Zahl aller Punkte. */
        public int punkte() {
            int n = 0;
            if (art.equals("MULTIFLAECHE")) { for (List<double[][]> t : ringe) for (double[][] r : t) n += r.length; return n; }
            for (double[][] p : teile) n += p.length;
            return n;
        }
    }

    /** Eine Ellipse als Fläche: Mitte, Halbachsen entlang (a) und quer (b), Richtung (Grad von Nord), n Punkte. */
    public static double[][] ellipse(double x, double z, double a, double b, double headingDeg, int n) {
        double h = Math.toRadians(headingDeg), ax = Math.sin(h), az = -Math.cos(h);
        double[][] p = new double[n][];
        for (int i = 0; i < n; i++) {
            double t = 2 * Math.PI * i / n, u = a * Math.cos(t), v = b * Math.sin(t);
            p[i] = new double[]{x + ax * u - az * v, z + az * u + ax * v};
        }
        return p;
    }

    // ================================================================== Stammdaten

    /** Eine Quelle (RH_QUELLE). */
    public record Quelle(String kurz, String titel, String url, String art, LocalDate abgerufen, String bemerkung) { }

    /** Ein Befund (RH_BEFUND) mit den Kurzzeichen seiner Quellen. */
    public record Befund(String thema, String gegenstand, String befund, String text, List<String> quellen) {
        public Befund { quellen = List.copyOf(quellen); }
    }

    /** Ein Gelände (RH_GELAENDE); dateien: Datei → SHA-256 (oder leer). */
    public record Gelaende(String name, String aera, Geom ausschnitt, double ursprungLon, double ursprungLat, double zelleM, int zellen,
                           Map<String, String> dateien, String quelle, String bemerkung) {
        public Gelaende { dateien = new LinkedHashMap<>(dateien); }
        /** „hoehe.r16=ab12…; art.u8=…“ */
        public String dateienText() {
            StringBuilder b = new StringBuilder();
            for (Map.Entry<String, String> e : dateien.entrySet()) b.append(b.length() > 0 ? "; " : "").append(e.getKey()).append('=').append(e.getValue() == null ? "" : e.getValue());
            return b.toString();
        }
    }

    /** Eine Partialtide (RH_PARTIALTIDE). */
    public record Partialtide(String name, double gradJeH, double amplitudeM, double phaseGrad) { }

    /**
     * Ein Pegelort (RH_PEGELORT) mit seinen Partialtiden (F26): mittel ist das Mittelwasser über NHN; versatz und
     * hubFaktor sagen, wie er aus Husum übertragen ist [G]; szene: der Pegel am Rand der Szene (Norderhever).
     */
    public record Pegelort(String name, String kurz, double x, double z, double mittelM, double versatzMin, double hubFaktor, boolean szene,
                           String beschreibung, String quelle, List<Partialtide> tiden) {
        public Pegelort { tiden = List.copyOf(tiden); }
        @Override public String toString() { return name; }
    }

    /** Ein Hoch- oder Niedrigwasser des Tidenkalenders (RH_TIDENKALENDER). */
    public record Kalender(String pegel, long ms, String art, double hoeheM, double springNipp) {
        public String text() { return Daten.text(ms); }
        public String tag() { return Daten.tag(ms); }
        public double stunde() { return Daten.stunde(ms); }
    }

    /** Eine Warft von 1362 (RH_WARFT, F30). */
    public record Warft(int nr, String name, String art, double x, double z, double a, double b, double richtungGrad, double kroneM, int haeuser,
                        int brunnen, String befund, String quelle, String bemerkung) {
        public Geom flaeche() { return Geom.flaeche(ellipse(x, z, a, b, richtungGrad, 24)); }
        @Override public String toString() { return name; }
    }

    /** Ein Bauwerk von 1362 (RH_BAUWERK, F30). */
    public record Bauwerk(String name, String art, Geom geom, Double hoeheM, String befund, String quelle, String bemerkung) { }

    /** Eine Fundstelle (RH_FUNDSTELLE, F31): mitgeliefert aus scene.Spuren oder eigen (Umschalt + Klick). */
    public record Fundstelle(long id, String name, String fund, Integer jahr, String finder, double x, double z, String befund, String quelle,
                             String bemerkung, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Eine belegte Sturmflut (RH_STURMFLUT, F32). */
    public record Sturmflut(String name, String datumText, int jahr, Integer monat, Integer tag, String kalender, Integer scheitelCm, String bezug,
                            String folgen, String quelle) {
        @Override public String toString() { return jahr + " · " + name; }
    }

    // ================================================================== Chronik (F28, F29)

    /** Ein Hoch- oder Niedrigwasser einer Sitzung am Rand (RH_TIDE); wasserlinie nur bei Niedrigwasser. */
    public record Tide(long id, long ms, String aera, String art, double pegelM, double astroM, double windstauM, double trockenAnteil, int pfuetzen,
                       double stroemungMax, Geom wasserlinie) { }

    /** Eine Zählung bei Niedrigwasser (RH_ZAEHLUNG): Seehunde einer Bank oder Vögel einer Art. */
    public record Zaehlung(long ms, String art, String gruppe, int anzahl, Integer liegend, Integer heuler, Integer gestoert, double x, double z) { }

    /** Ein Lauf der Mandränke (RH_MANDRAENKE_LAUF); die Zeiten null, solange nicht erreicht. */
    public record MandraenkeLauf(long id, String stelle, long beginnMs, Long sturmMs, Long ueberlaufMs, Long bruchMs, Long flutMs, Long morgenMs,
                                 String stufe, double pegelMaxM, double marschMaxM, int haeuser, int haeuserZerfallen, int stuecke,
                                 double[] bruchstelle, Geom bresche, String regiebuch) {
        public String bruchText() { return bruchMs == null ? null : Daten.text(bruchMs); }
    }

    /** Ein Läuten der Glocken aus dem Watt (RH_GLOCKEN). */
    public record Glocken(long ms, double dauerS, double windMs, double richtungGrad, double wasserKirchwarftM, boolean vonHand) { }

    /** Ein Lauf eines Regiebuchs (RH_REGIELAUF) mit dem Blatt als PNG (oder null). */
    public record Regielauf(String name, long beginnMs, Long endeMs, boolean vollstaendig, boolean kino, double bilderMittel, double gpuMs,
                            double gewartetS, int fotos, String qualitaet, byte[] blatt, String blattDatei) { }

    // ================================================================== Regie und Kamera

    /** Eine Wetterlage (RH_WETTERLAGE): je {@code Weather.Var} ein Wert oder NaN; die Wolkenart als Zahl, in der Tabelle als Name. */
    public record Wetterlage(String name, Double beaufort, String phase, double[] werte, String monate, String beschreibung, String herkunft) {
        public Wetterlage { werte = werte.clone(); }
        @Override public String toString() { return name; }
    }

    /** Ein Regiebuch als ganzer Text (Format von {@code scene.Regie.parse}). */
    public record Regiebuch(String name, String text, String beschreibung, String datei, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein Stützpunkt einer Kamerafahrt (RH_STUETZPUNKT); Szenenmeter und Grad. */
    public record Stuetzpunkt(int nr, double zeitS, double x, double y, double z, double gier, double nick) { }

    /** Eine Kamerafahrt (RH_FAHRT). */
    public record Fahrt(String name, String art, Double dauerS, Double laengeM, String beschreibung, String herkunft, List<Stuetzpunkt> punkte) {
        public Fahrt { punkte = List.copyOf(punkte); }
        @Override public String toString() { return name; }
    }

    /**
     * Eine Ansicht (RH_ANSICHT): fest (Tasten 1–9) oder gemerkt (Taste V) mit Zustand und Vorschau (PNG oder null).
     * kamera = {x, y, z, Gier, Nick}; y NaN heißt: aus dem Gelände.
     */
    public record Ansicht(long id, String name, Integer taste, String art, double[] kamera, Double augeUeberGrund, String aera, boolean unterWasser,
                          Long szeneMs, String zustand, byte[] vorschau, String herkunft) {
        @Override public String toString() { return name; }
    }

    /** Ein benanntes Preset eines Package (RH_PAKET_PRESET mit RH_PAKET_WERT). */
    public record PaketPreset(String paket, String klasse, String name, String beschreibung, String herkunft, Map<String, String> felder) {
        public PaketPreset { felder = new LinkedHashMap<>(felder); }
        @Override public String toString() { return name; }
    }

    // ================================================================== Betrieb

    /** Die letzte Sitzung für „Weiter wie zuletzt“. */
    public record Sitzung(long id, long beginnMs, Long endeMs, boolean sauber, String zustand, Long zustandMs, String regiebuch, Double regieSekunde,
                          Long szeneMs, Double aera, Double pegelM, String mandraenkeStufe) {
        public String szeneText() { return szeneMs == null ? "" : Daten.text(szeneMs); }
    }

    /** Was das Startbild und der Block DATEN über den Stand zeigen. */
    public record Stand(int warften, int bauwerke, int fundstellen, int eigeneFunde, int sturmfluten, int kalender, int tiden, int zaehlungen,
                        int mandraenken, int glocken, int regiebuecher, int regielaeufe, int ansichten, int presets, int befunde, int quellen, Sitzung letzte) { }

    /**
     * Die Zellkarte (F36): was die Datenbank an einer Stelle des Watts weiß: Fundstellen in 100 m, die Warft von 1362
     * darunter (oder die nächste mit Abstand), wie viele Niedrigwasser der Chronik es gibt und wie oft die Stelle dabei
     * nass blieb (in der Wasserlinie lag).
     */
    public record Zelle(double x, double z, List<String> funde, String warft, double warftAbstandM, int niedrigwasser, int nass) { }
}
