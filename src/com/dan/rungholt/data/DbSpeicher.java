package com.dan.rungholt.data;

import com.dan.rungholt.scene.Regie;
import com.dan.store.Db;
import com.dan.store.Protokoll;
import com.dan.store.Zahl;
import com.dan.weather.Weather;

import java.io.StringReader;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Speichern im Schema RUNGHOLT (Phase 16). Eine Verbindung, alle Zugriffe synchronisiert (der Faden der Daten, die
 * Chronik und die Oberfläche teilen sie). Punkte schreibt sie als {@code SDO_POINT_TYPE} (so lesen die Sichten Länge und
 * Breite als Zahl), Linien und Flächen als WKT über {@code SDO_GEOMETRY(clob, 8307)}; gelesen wird mit
 * {@code SDO_UTIL.TO_WKTGEOMETRY}, ohne {@code sdoapi}. Szenenzeit als ms seit 1970 mit Text und Tag (F33).
 * Mitgelieferte Zeilen werden nie überschrieben.
 */
public final class DbSpeicher implements Speicher {
    private final Db.Konfig konfig;
    private Connection c;
    private final String wo;

    public DbSpeicher(Db.Konfig k) throws Fehler {
        this.konfig = k;
        try {
            c = Db.oeffnen(k);
            String w = Db.einWert(c, "select user || ' auf ' || sys_context('USERENV','CON_NAME') from dual");
            wo = "Datenbank " + w;
        } catch (SQLException e) {
            throw new Fehler("Keine Verbindung zu " + k.kurz() + ": " + Db.erste(e), e);
        }
    }

    /** Für Prüfungen und den Einrichter: die offene Verbindung. */
    public synchronized Connection verbindung() throws Fehler {
        try {
            if (c == null || c.isClosed() || !c.isValid(3)) c = Db.oeffnen(konfig);
            return c;
        } catch (SQLException e) {
            throw new Fehler("Verbindung verloren: " + Db.erste(e), e);
        }
    }

    @Override public String wo() { return wo; }
    @Override public boolean datenbank() { return true; }

    private interface Arbeit<T> { T tun(Connection c) throws SQLException; }

    private synchronized <T> T mit(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            return a.tun(cc);
        } catch (SQLException e) {
            throw new Fehler(was + ": " + Db.erste(e), e);
        }
    }

    /** In einer Transaktion: alles oder nichts. */
    private synchronized <T> T zusammen(String was, Arbeit<T> a) throws Fehler {
        Connection cc = verbindung();
        try {
            cc.setAutoCommit(false);
            T t = a.tun(cc);
            cc.commit();
            return t;
        } catch (SQLException e) {
            try { cc.rollback(); } catch (SQLException e2) { /* weiter */ }
            throw new Fehler(was + ": " + Db.erste(e), e);
        } finally {
            try { cc.setAutoCommit(true); } catch (SQLException e) { /* weiter */ }
        }
    }

    // ------------------------------------------------------------------ Hilfen

    private static int zahl(Connection c, String sql, Object... a) throws SQLException {
        String v = Db.einWert(c, sql, a);
        return v == null ? 0 : Integer.parseInt(v);
    }

    private static long neueId(Connection c, String seq) throws SQLException { return Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(" + seq + ".CURRVAL) FROM DUAL")); }

    static void setD(PreparedStatement st, int i, Double v) throws SQLException {
        if (v == null || Double.isNaN(v) || Double.isInfinite(v)) st.setNull(i, Types.NUMERIC); else st.setDouble(i, v);
    }

    static void setS(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null || v.isEmpty()) st.setNull(i, Types.VARCHAR);
        else if (v.length() > 3900) st.setCharacterStream(i, new StringReader(v), v.length());
        else st.setString(i, v);
    }

    private static void setClob(PreparedStatement st, int i, String v) throws SQLException {
        if (v == null) st.setNull(i, Types.CLOB); else st.setCharacterStream(i, new StringReader(v), v.length());
    }

    private static void setL(PreparedStatement st, int i, Long v) throws SQLException {
        if (v == null || v < 0) st.setNull(i, Types.NUMERIC); else st.setLong(i, v);
    }

    /** Eine Szenenzeit: ms (darf negativ sein), Text, Tag ab Index i; gibt den nächsten Index zurück. */
    private static int setSz(PreparedStatement st, int i, Long ms) throws SQLException {
        if (ms == null) { st.setNull(i, Types.NUMERIC); st.setNull(i + 1, Types.VARCHAR); st.setNull(i + 2, Types.VARCHAR); return i + 3; }
        st.setLong(i, ms);
        st.setString(i + 1, Daten.text(ms));
        st.setString(i + 2, Daten.tag(ms));
        return i + 3;
    }

    private static void setMs(PreparedStatement st, int i, Long ms) throws SQLException {
        if (ms == null) st.setNull(i, Types.NUMERIC); else st.setLong(i, ms);
    }

    private static Long ms(ResultSet r, String col) throws SQLException {
        long v = r.getLong(col);
        return r.wasNull() ? null : v;
    }

    private static Double dbl(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? null : v;
    }

    private static double d(ResultSet r, String col) throws SQLException {
        double v = r.getDouble(col);
        return r.wasNull() ? Double.NaN : v;
    }

    private static Integer integer(ResultSet r, String col) throws SQLException {
        int v = r.getInt(col);
        return r.wasNull() ? null : v;
    }

    private static String clob(ResultSet r, String col) throws SQLException {
        Clob cl = r.getClob(col);
        return cl == null ? null : cl.getSubString(1, (int) cl.length());
    }

    private static Daten.Geom geom(ResultSet r, String col) throws SQLException {
        String w = clob(r, col);
        return w == null ? null : Daten.Geom.ausWkt(w);
    }

    /** SQL für eine Geometrie: Punkt als SDO_POINT_TYPE mit zwei Platzhaltern, sonst WKT mit einem; null: NULL. */
    private static String geomSql(Daten.Geom g) {
        if (g == null) return "NULL";
        if (g.art().equals("PUNKT")) return "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL)";
        return "MDSYS.SDO_GEOMETRY(?, 8307)";
    }

    /** Die Platzhalter von {@link #geomSql} füllen; gibt den nächsten Index zurück. */
    private static int setGeom(PreparedStatement st, int i, Daten.Geom g) throws SQLException {
        if (g == null) return i;
        if (g.art().equals("PUNKT")) {
            double[] ll = Daten.lonLat(g.xz()[0][0], g.xz()[0][1]);
            st.setDouble(i, Math.round(ll[0] * 1e8) / 1e8);
            st.setDouble(i + 1, Math.round(ll[1] * 1e8) / 1e8);
            return i + 2;
        }
        setClob(st, i, g.wkt());
        return i + 1;
    }

    private static boolean mitgeliefert(Connection c, String tabelle, String name) throws SQLException {
        return zahl(c, "SELECT COUNT(*) FROM " + tabelle + " WHERE NAME = ? AND HERKUNFT = 'MITGELIEFERT'", name) > 0;
    }

    private void loeschen(String tabelle, String name) throws Fehler {
        zusammen("Löschen", c -> {
            if (mitgeliefert(c, tabelle, name)) throw new SQLException("„" + name + "“ ist mitgeliefert und wird nicht gelöscht");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM " + tabelle + " WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, name); st.executeUpdate(); }
            return null;
        });
    }

    private static final String REGIEBUCH_ID = "(SELECT MAX(ID) FROM RH_REGIEBUCH WHERE NAME = ?)";

    // ------------------------------------------------------------------ Stand

    @Override public Daten.Stand stand() throws Fehler {
        return mit("Stand", c -> new Daten.Stand(zahl(c, "SELECT COUNT(*) FROM RH_WARFT"), zahl(c, "SELECT COUNT(*) FROM RH_BAUWERK"),
                zahl(c, "SELECT COUNT(*) FROM RH_FUNDSTELLE"), zahl(c, "SELECT COUNT(*) FROM RH_FUNDSTELLE WHERE HERKUNFT = 'EIGEN'"),
                zahl(c, "SELECT COUNT(*) FROM RH_STURMFLUT"), zahl(c, "SELECT COUNT(*) FROM RH_TIDENKALENDER"), zahl(c, "SELECT COUNT(*) FROM RH_TIDE"),
                zahl(c, "SELECT COUNT(*) FROM RH_ZAEHLUNG"), zahl(c, "SELECT COUNT(*) FROM RH_MANDRAENKE_LAUF"), zahl(c, "SELECT COUNT(*) FROM RH_GLOCKEN"),
                zahl(c, "SELECT COUNT(*) FROM RH_REGIEBUCH"), zahl(c, "SELECT COUNT(*) FROM RH_REGIELAUF"), zahl(c, "SELECT COUNT(*) FROM RH_ANSICHT"),
                zahl(c, "SELECT COUNT(*) FROM RH_PAKET_PRESET"), zahl(c, "SELECT COUNT(*) FROM RH_BEFUND"), zahl(c, "SELECT COUNT(*) FROM RH_QUELLE"), letzte(c, -1)));
    }

    // ------------------------------------------------------------------ Quellen, Befunde, Sturmfluten

    @Override public List<Daten.Quelle> quellen() throws Fehler {
        return mit("Quellen", c -> {
            List<Daten.Quelle> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT KURZ, TITEL, URL, ART, ABGERUFEN, BEMERKUNG FROM RH_QUELLE ORDER BY ID")) {
                while (r.next()) {
                    java.sql.Date ab = r.getDate(5);
                    l.add(new Daten.Quelle(r.getString(1), r.getString(2), r.getString(3), r.getString(4), ab == null ? null : ab.toLocalDate(), r.getString(6)));
                }
            }
            return l;
        });
    }

    @Override public List<Daten.Befund> befunde() throws Fehler {
        return mit("Befunde", c -> {
            Map<Long, List<String>> q = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT x.BEFUND_ID, q.KURZ FROM RH_BEFUND_QUELLE x JOIN RH_QUELLE q ON q.ID = x.QUELLE_ID ORDER BY x.ID")) {
                while (r.next()) q.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(r.getString(2));
            }
            List<Daten.Befund> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, THEMA, GEGENSTAND, BEFUND, TEXT FROM RH_BEFUND ORDER BY ID")) {
                while (r.next()) l.add(new Daten.Befund(r.getString(2), r.getString(3), r.getString(4), r.getString(5), q.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    @Override public List<Daten.Sturmflut> sturmfluten() throws Fehler {
        return mit("Sturmfluten", c -> {
            List<Daten.Sturmflut> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT s.NAME, s.DATUM_TEXT, s.JAHR, s.MONAT, s.TAG, s.KALENDER, s.SCHEITEL_HUSUM_CM, s.BEZUG, "
                    + "s.FOLGEN, q.KURZ FROM RH_STURMFLUT s LEFT JOIN RH_QUELLE q ON q.ID = s.QUELLE_ID ORDER BY s.JAHR, s.MONAT, s.TAG")) {
                while (r.next())
                    l.add(new Daten.Sturmflut(r.getString(1), r.getString(2), r.getInt(3), integer(r, "MONAT"), integer(r, "TAG"), r.getString(6), integer(r, "SCHEITEL_HUSUM_CM"),
                            r.getString(8), r.getString(9), r.getString(10)));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Pegel und Tidenkalender

    @Override public List<Daten.Pegelort> pegelorte() throws Fehler {
        return mit("Pegelorte", c -> {
            Map<Long, List<Daten.Partialtide>> t = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT PEGELORT_ID, NAME, GRAD_JE_H, AMPLITUDE_M, PHASE_GRAD FROM RH_PARTIALTIDE ORDER BY PEGELORT_ID, ID")) {
                while (r.next()) t.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(new Daten.Partialtide(r.getString(2), r.getDouble(3), r.getDouble(4), r.getDouble(5)));
            }
            List<Daten.Pegelort> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT p.ID, p.NAME, p.KURZ, p.X_M, p.Z_M, p.MITTEL_M, p.VERSATZ_MIN, p.HUB_FAKTOR, p.SZENE, "
                    + "p.BESCHREIBUNG, q.KURZ AS QUELLE FROM RH_PEGELORT p LEFT JOIN RH_QUELLE q ON q.ID = p.QUELLE_ID ORDER BY p.ID")) {
                while (r.next())
                    l.add(new Daten.Pegelort(r.getString(2), r.getString(3), d(r, "X_M"), d(r, "Z_M"), r.getDouble(6), r.getDouble(7), r.getDouble(8), r.getInt(9) == 1,
                            r.getString(10), r.getString(11), t.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    @Override public List<Daten.Kalender> kalender(String pegel, long vonMs, long bisMs) throws Fehler {
        return mit("Tidenkalender", c -> {
            List<Daten.Kalender> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT k.SZENE_MS, k.ART, k.HOEHE_M, k.SPRING_NIPP FROM RH_TIDENKALENDER k JOIN RH_PEGELORT p ON p.ID = k.PEGELORT_ID "
                    + "WHERE p.KURZ = ? AND k.SZENE_MS >= ? AND k.SZENE_MS < ? ORDER BY k.SZENE_MS")) {
                st.setString(1, pegel);
                st.setLong(2, vonMs);
                st.setLong(3, bisMs);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next()) l.add(new Daten.Kalender(pegel, r.getLong(1), r.getString(2), r.getDouble(3), d(r, "SPRING_NIPP")));
                }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Rungholt

    @Override public List<Daten.Warft> warften() throws Fehler {
        return mit("Warften", c -> {
            List<Daten.Warft> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT w.NR, w.NAME, w.ART, w.X_M, w.Z_M, w.HALBACHSE_A_M, w.HALBACHSE_B_M, w.RICHTUNG_GRAD, "
                    + "w.KRONE_NHN_M, w.HAEUSER, w.BRUNNEN, w.BEFUND, q.KURZ AS QUELLE, w.BEMERKUNG FROM RH_WARFT w LEFT JOIN RH_QUELLE q ON q.ID = w.QUELLE_ID ORDER BY w.NR")) {
                while (r.next())
                    l.add(new Daten.Warft(r.getInt(1), r.getString(2), r.getString(3), r.getDouble(4), r.getDouble(5), d(r, "HALBACHSE_A_M"), d(r, "HALBACHSE_B_M"),
                            d(r, "RICHTUNG_GRAD"), d(r, "KRONE_NHN_M"), r.getInt(10), r.getInt(11), r.getString(12), r.getString(13), r.getString(14)));
            }
            return l;
        });
    }

    @Override public List<Daten.Bauwerk> bauwerke() throws Fehler {
        return mit("Bauwerke", c -> {
            List<Daten.Bauwerk> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT b.NAME, b.ART, MDSYS.SDO_UTIL.TO_WKTGEOMETRY(b.GEOM) AS WKT, b.HOEHE_NHN_M, b.BEFUND, "
                    + "q.KURZ AS QUELLE, b.BEMERKUNG FROM RH_BAUWERK b LEFT JOIN RH_QUELLE q ON q.ID = b.QUELLE_ID ORDER BY b.ID")) {
                while (r.next()) l.add(new Daten.Bauwerk(r.getString(1), r.getString(2), geom(r, "WKT"), dbl(r, "HOEHE_NHN_M"), r.getString(5), r.getString(6), r.getString(7)));
            }
            return l;
        });
    }

    private static final String FUND_SQL = "SELECT f.ID, f.NAME, f.FUND, f.JAHR, f.FINDER, f.X_M, f.Z_M, f.BEFUND, q.KURZ AS QUELLE, f.BEMERKUNG, f.HERKUNFT "
            + "FROM RH_FUNDSTELLE f LEFT JOIN RH_QUELLE q ON q.ID = f.QUELLE_ID ";

    private static Daten.Fundstelle fund(ResultSet r) throws SQLException {
        return new Daten.Fundstelle(r.getLong(1), r.getString(2), r.getString(3), integer(r, "JAHR"), r.getString(5), r.getDouble(6), r.getDouble(7), r.getString(8),
                r.getString(9), r.getString(10), r.getString(11));
    }

    @Override public List<Daten.Fundstelle> fundstellen() throws Fehler {
        return mit("Fundstellen", c -> {
            List<Daten.Fundstelle> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(FUND_SQL + "ORDER BY CASE f.HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, "
                    + "CASE f.HERKUNFT WHEN 'MITGELIEFERT' THEN f.ID ELSE -f.ID END")) {
                while (r.next()) l.add(fund(r));
            }
            return l;
        });
    }

    @Override public long fundstelle(Daten.Fundstelle f, Long sitzung) throws Fehler {
        Daten.Geom lage = Daten.Geom.punkt(f.x(), f.z());
        return zusammen("Fundstelle anlegen", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_FUNDSTELLE (NAME, FUND, JAHR, FINDER, LAGE, X_M, Z_M, BEFUND, SITZUNG_ID, BEMERKUNG, HERKUNFT) "
                    + "VALUES (?, ?, ?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, f.name());
                setS(st, 2, f.fund());
                if (f.jahr() == null) st.setNull(3, Types.NUMERIC); else st.setInt(3, f.jahr());
                setS(st, 4, f.finder());
                int i = setGeom(st, 5, lage);
                st.setDouble(i, Math.round(f.x() * 10) / 10.0);
                st.setDouble(i + 1, Math.round(f.z() * 10) / 10.0);
                st.setString(i + 2, f.befund() == null ? Daten.GESTALTET : f.befund());
                setL(st, i + 3, sitzung);
                setS(st, i + 4, f.bemerkung());
                st.executeUpdate();
            }
            return neueId(c, "RH_FUNDSTELLE_SEQ");
        });
    }

    @Override public void loeschenFundstelle(long id) throws Fehler {
        zusammen("Fundstelle löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM RH_FUNDSTELLE WHERE ID = ? AND HERKUNFT = 'EIGEN'")) {
                st.setLong(1, id);
                if (st.executeUpdate() == 0) throw new SQLException("Fundstelle " + id + " ist mitgeliefert oder gibt es nicht");
            }
            return null;
        });
    }

    @Override public Daten.Zelle zelle(double x, double z) throws Fehler {
        double[] ll = Daten.lonLat(x, z);
        String punkt = "MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL)";
        return mit("Zellkarte", c -> {
            List<String> funde = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT f.NAME, f.JAHR, f.HERKUNFT, ROUND(MDSYS.SDO_GEOM.SDO_DISTANCE(f.LAGE, " + punkt + ", 0.05, 'unit=M')) AS M "
                    + "FROM RH_FUNDSTELLE f WHERE MDSYS.SDO_WITHIN_DISTANCE(f.LAGE, " + punkt + ", 'distance=100 unit=M') = 'TRUE' ORDER BY 4")) {
                st.setDouble(1, ll[0]); st.setDouble(2, ll[1]); st.setDouble(3, ll[0]); st.setDouble(4, ll[1]);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        funde.add(r.getString(1) + " (" + (r.getObject(2) == null ? "" : r.getInt(2) + ", ") + ("EIGEN".equals(r.getString(3)) ? "eigen, " : "") + r.getInt(4) + " m)");
                }
            }
            String warft = null;
            double ab = Double.NaN;
            try (PreparedStatement st = c.prepareStatement("SELECT w.NAME, ROUND(MDSYS.SDO_GEOM.SDO_DISTANCE(w.FLAECHE, " + punkt + ", 0.05, 'unit=M'), 1) AS M FROM RH_WARFT w "
                    + "WHERE MDSYS.SDO_NN(w.FLAECHE, " + punkt + ", 'sdo_num_res=1', 1) = 'TRUE'")) {
                st.setDouble(1, ll[0]); st.setDouble(2, ll[1]); st.setDouble(3, ll[0]); st.setDouble(4, ll[1]);
                try (ResultSet r = st.executeQuery()) { if (r.next()) { warft = r.getString(1); ab = r.getDouble(2); } }
            }
            int nw = zahl(c, "SELECT COUNT(*) FROM RH_TIDE WHERE ART = 'NW' AND WASSERLINIE IS NOT NULL"), nass;
            // räumliche Operatoren nur im WHERE: wie oft lag die Stelle bei Niedrigwasser in der nassen Fläche
            try (PreparedStatement st = c.prepareStatement("SELECT COUNT(*) FROM RH_TIDE t WHERE t.ART = 'NW' AND MDSYS.SDO_ANYINTERACT(t.WASSERLINIE, " + punkt + ") = 'TRUE'")) {
                st.setDouble(1, ll[0]); st.setDouble(2, ll[1]);
                try (ResultSet r = st.executeQuery()) { nass = r.next() ? r.getInt(1) : 0; }
            }
            return new Daten.Zelle(x, z, funde, warft, ab, nw, nass);
        });
    }

    // ------------------------------------------------------------------ Chronik

    @Override public long tide(Daten.Tide t, Long sitzung) throws Fehler {
        return zusammen("Tide schreiben", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_TIDE (SITZUNG_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, AERA, ART, PEGEL_M, ASTRO_M, WINDSTAU_M, "
                    + "TROCKEN_ANTEIL, PFUETZEN, STROEMUNG_MAX, WASSERLINIE, RINGE) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + geomSql(t.wasserlinie()) + ", ?)")) {
                setL(st, 1, sitzung);
                int i = setSz(st, 2, t.ms());
                st.setString(i, t.aera());
                st.setString(i + 1, t.art());
                setD(st, i + 2, r3(t.pegelM()));
                setD(st, i + 3, r3(t.astroM()));
                setD(st, i + 4, r3(t.windstauM()));
                setD(st, i + 5, Math.round(t.trockenAnteil() * 1e4) / 1e4);
                st.setInt(i + 6, t.pfuetzen());
                setD(st, i + 7, Math.round(t.stroemungMax() * 100) / 100.0);
                int k = setGeom(st, i + 8, t.wasserlinie());
                if (t.wasserlinie() == null) st.setNull(k, Types.NUMERIC); else st.setInt(k, t.wasserlinie().ringe().stream().mapToInt(List::size).sum());
                st.executeUpdate();
            }
            return neueId(c, "RH_TIDE_SEQ");
        });
    }

    static Double r3(double v) { return Double.isNaN(v) ? null : Math.round(v * 1000) / 1000.0; }

    @Override public void zaehlungen(long tide, List<Daten.Zaehlung> zs, Long sitzung) throws Fehler {
        if (zs.isEmpty()) return;
        zusammen("Zählung schreiben", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_ZAEHLUNG (SITZUNG_ID, TIDE_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, ART, GRUPPE, ANZAHL, LIEGEND, HEULER, "
                    + "GESTOERT, LAGE, X_M, Z_M) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, MDSYS.SDO_GEOMETRY(2001, 8307, MDSYS.SDO_POINT_TYPE(?, ?, NULL), NULL, NULL), ?, ?)")) {
                for (Daten.Zaehlung z : zs) {
                    setL(st, 1, sitzung);
                    setL(st, 2, tide);
                    int i = setSz(st, 3, z.ms());
                    st.setString(i, z.art());
                    st.setString(i + 1, z.gruppe());
                    st.setInt(i + 2, z.anzahl());
                    for (int k = 0; k < 3; k++) {
                        Integer v = k == 0 ? z.liegend() : k == 1 ? z.heuler() : z.gestoert();
                        if (v == null) st.setNull(i + 3 + k, Types.NUMERIC); else st.setInt(i + 3 + k, v);
                    }
                    double[] ll = Daten.lonLat(z.x(), z.z());
                    st.setDouble(i + 6, Math.round(ll[0] * 1e8) / 1e8);
                    st.setDouble(i + 7, Math.round(ll[1] * 1e8) / 1e8);
                    st.setDouble(i + 8, Math.round(z.x() * 10) / 10.0);
                    st.setDouble(i + 9, Math.round(z.z() * 10) / 10.0);
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public long mandraenkeLauf(Daten.MandraenkeLauf l, Long sitzung) throws Fehler {
        Daten.Geom bs = l.bruchstelle() == null ? null : Daten.Geom.punkt(l.bruchstelle()[0], l.bruchstelle()[1]);
        return zusammen("Lauf der Mandränke", c -> {
            String cols = "STELLE = ?, SZENE_TAG = ?, BEGINN_MS = ?, STURM_MS = ?, UEBERLAUF_MS = ?, BRUCH_MS = ?, FLUT_MS = ?, MORGEN_MS = ?, BRUCH_TEXT = ?, STUFE = ?, "
                    + "PEGEL_MAX_M = ?, MARSCH_MAX_M = ?, HAEUSER = ?, HAEUSER_ZERFALLEN = ?, STUECKE = ?, BRUCHSTELLE = " + geomSql(bs) + ", BRESCHE = " + geomSql(l.bresche())
                    + ", REGIEBUCH_ID = " + REGIEBUCH_ID;
            boolean neu = l.id() <= 0;
            String sql = neu
                    ? "INSERT INTO RH_MANDRAENKE_LAUF (STELLE, SZENE_TAG, BEGINN_MS, STURM_MS, UEBERLAUF_MS, BRUCH_MS, FLUT_MS, MORGEN_MS, BRUCH_TEXT, STUFE, PEGEL_MAX_M, MARSCH_MAX_M, "
                    + "HAEUSER, HAEUSER_ZERFALLEN, STUECKE, BRUCHSTELLE, BRESCHE, REGIEBUCH_ID, SITZUNG_ID) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, " + geomSql(bs) + ", "
                    + geomSql(l.bresche()) + ", " + REGIEBUCH_ID + ", ?)"
                    : "UPDATE RH_MANDRAENKE_LAUF SET " + cols + " WHERE ID = ?";
            try (PreparedStatement st = c.prepareStatement(sql)) {
                st.setString(1, l.stelle());
                st.setString(2, Daten.tag(l.beginnMs()));
                st.setLong(3, l.beginnMs());
                setMs(st, 4, l.sturmMs());
                setMs(st, 5, l.ueberlaufMs());
                setMs(st, 6, l.bruchMs());
                setMs(st, 7, l.flutMs());
                setMs(st, 8, l.morgenMs());
                setS(st, 9, l.bruchText());
                st.setString(10, l.stufe());
                setD(st, 11, r3(l.pegelMaxM()));
                setD(st, 12, r3(l.marschMaxM()));
                st.setInt(13, l.haeuser());
                st.setInt(14, l.haeuserZerfallen());
                st.setInt(15, l.stuecke());
                int i = setGeom(st, 16, bs);
                i = setGeom(st, i, l.bresche());
                setS(st, i, l.regiebuch());
                if (neu) setL(st, i + 1, sitzung); else st.setLong(i + 1, l.id());
                st.executeUpdate();
            }
            return neu ? neueId(c, "RH_MANDRAENKE_LAUF_SEQ") : l.id();
        });
    }

    @Override public void glocken(Daten.Glocken g, Long sitzung) throws Fehler {
        zusammen("Glocken", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_GLOCKEN (SITZUNG_ID, SZENE_MS, SZENE_TEXT, SZENE_TAG, DAUER_S, WIND_MS, RICHTUNG_GRAD, "
                    + "WASSER_KIRCHWARFT_M, VON_HAND) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                setL(st, 1, sitzung);
                int i = setSz(st, 2, g.ms());
                setD(st, i, Math.round(g.dauerS() * 10) / 10.0);
                setD(st, i + 1, Math.round(g.windMs() * 100) / 100.0);
                setD(st, i + 2, Math.round(g.richtungGrad() * 10) / 10.0);
                setD(st, i + 3, Math.round(g.wasserKirchwarftM() * 100) / 100.0);
                st.setInt(i + 4, g.vonHand() ? 1 : 0);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void regielauf(Daten.Regielauf l, Long sitzung) throws Fehler {
        zusammen("Lauf eines Regiebuchs", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_REGIELAUF (REGIEBUCH_ID, NAME, BEGINN, ENDE, VOLLSTAENDIG, KINO, BILDER_S_MITTEL, GPU_MS_MITTEL, "
                    + "GEWARTET_S, FOTOS, QUALITAET, BLATT, BLATT_DATEI, SITZUNG_ID) VALUES (" + REGIEBUCH_ID + ", ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                st.setString(1, l.name());
                st.setString(2, l.name());
                st.setTimestamp(3, new Timestamp(l.beginnMs()));
                if (l.endeMs() == null) st.setNull(4, Types.TIMESTAMP); else st.setTimestamp(4, new Timestamp(l.endeMs()));
                st.setInt(5, l.vollstaendig() ? 1 : 0);
                st.setInt(6, l.kino() ? 1 : 0);
                setD(st, 7, Math.round(l.bilderMittel() * 100) / 100.0);
                setD(st, 8, Math.round(l.gpuMs() * 1000) / 1000.0);
                setD(st, 9, Math.round(l.gewartetS() * 10) / 10.0);
                st.setInt(10, l.fotos());
                setS(st, 11, l.qualitaet());
                if (l.blatt() == null) st.setNull(12, Types.BLOB); else st.setBytes(12, l.blatt());
                setS(st, 13, l.blattDatei());
                setL(st, 14, sitzung);
                st.executeUpdate();
            }
            return null;
        });
    }

    private static Daten.Tide tideAus(ResultSet r, boolean linie) throws SQLException {
        return new Daten.Tide(r.getLong("ID"), r.getLong("SZENE_MS"), r.getString("AERA"), r.getString("ART"), r.getDouble("PEGEL_M"), d(r, "ASTRO_M"), d(r, "WINDSTAU_M"),
                d(r, "TROCKEN_ANTEIL"), r.getInt("PFUETZEN"), d(r, "STROEMUNG_MAX"), linie ? geom(r, "WKT") : null);
    }

    private static final String TIDE_SQL = "SELECT ID, SZENE_MS, AERA, ART, PEGEL_M, ASTRO_M, WINDSTAU_M, TROCKEN_ANTEIL, PFUETZEN, STROEMUNG_MAX";

    @Override public List<Daten.Tide> tidenAm(LocalDate tag) throws Fehler {
        return mit("Tiden des Tages", c -> {
            List<Daten.Tide> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(TIDE_SQL + ", MDSYS.SDO_UTIL.TO_WKTGEOMETRY(WASSERLINIE) AS WKT FROM RH_TIDE WHERE SZENE_TAG = ? ORDER BY SZENE_MS")) {
                st.setString(1, tag.toString());
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(tideAus(r, true)); }
            }
            return l;
        });
    }

    @Override public List<Daten.Tide> tiden(int n) throws Fehler {
        return mit("Tiden", c -> {
            List<Daten.Tide> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement(TIDE_SQL + " FROM RH_TIDE ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(tideAus(r, false)); }
            }
            return l;
        });
    }

    @Override public List<Daten.Zaehlung> zaehlungenAm(LocalDate tag) throws Fehler {
        return mit("Zählungen des Tages", c -> {
            List<Daten.Zaehlung> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT SZENE_MS, ART, GRUPPE, ANZAHL, LIEGEND, HEULER, GESTOERT, X_M, Z_M FROM RH_ZAEHLUNG WHERE SZENE_TAG = ? "
                    + "ORDER BY SZENE_MS, ID")) {
                st.setString(1, tag.toString());
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(new Daten.Zaehlung(r.getLong(1), r.getString(2), r.getString(3), r.getInt(4), integer(r, "LIEGEND"), integer(r, "HEULER"), integer(r, "GESTOERT"),
                                r.getDouble(8), r.getDouble(9)));
                }
            }
            return l;
        });
    }

    @Override public List<Daten.MandraenkeLauf> mandraenkeLaeufe() throws Fehler {
        return mit("Läufe der Mandränke", c -> {
            List<Daten.MandraenkeLauf> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT m.ID, m.STELLE, m.BEGINN_MS, m.STURM_MS, m.UEBERLAUF_MS, m.BRUCH_MS, m.FLUT_MS, m.MORGEN_MS, "
                    + "m.STUFE, m.PEGEL_MAX_M, m.MARSCH_MAX_M, m.HAEUSER, m.HAEUSER_ZERFALLEN, m.STUECKE, m.BRUCHSTELLE.SDO_POINT.X AS LON, m.BRUCHSTELLE.SDO_POINT.Y AS LAT, "
                    + "MDSYS.SDO_UTIL.TO_WKTGEOMETRY(m.BRESCHE) AS WKT, r.NAME AS REGIEBUCH FROM RH_MANDRAENKE_LAUF m LEFT JOIN RH_REGIEBUCH r ON r.ID = m.REGIEBUCH_ID "
                    + "ORDER BY m.ID DESC FETCH FIRST 50 ROWS ONLY")) {
                while (r.next()) {
                    Double lon = dbl(r, "LON"), lat = dbl(r, "LAT");
                    l.add(new Daten.MandraenkeLauf(r.getLong(1), r.getString(2), r.getLong(3), ms(r, "STURM_MS"), ms(r, "UEBERLAUF_MS"), ms(r, "BRUCH_MS"), ms(r, "FLUT_MS"),
                            ms(r, "MORGEN_MS"), r.getString(9), d(r, "PEGEL_MAX_M"), d(r, "MARSCH_MAX_M"), r.getInt(12), r.getInt(13), r.getInt(14),
                            lon == null || lat == null ? null : Daten.xz(lon, lat), geom(r, "WKT"), r.getString("REGIEBUCH")));
                }
            }
            return l;
        });
    }

    @Override public int chronikLeeren(long sitzung) throws Fehler {
        return zusammen("Chronik leeren", c -> {
            int n = 0;
            for (String t : new String[]{"RH_ZAEHLUNG", "RH_TIDE", "RH_MANDRAENKE_LAUF", "RH_GLOCKEN", "RH_REGIELAUF"})
                try (PreparedStatement st = c.prepareStatement("DELETE FROM " + t + " WHERE SITZUNG_ID = ?")) { st.setLong(1, sitzung); n += st.executeUpdate(); }
            return n;
        });
    }

    @Override public List<long[]> sitzungenMitChronik(int n) throws Fehler {
        return mit("Sitzungen mit Chronik", c -> {
            List<long[]> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, (SELECT COUNT(*) FROM RH_TIDE t WHERE t.SITZUNG_ID = s.ID) AS T, "
                    + "(SELECT COUNT(*) FROM RH_ZAEHLUNG z WHERE z.SITZUNG_ID = s.ID) AS Z FROM RH_SITZUNG s WHERE EXISTS (SELECT 1 FROM RH_TIDE t WHERE t.SITZUNG_ID = s.ID) "
                    + "OR EXISTS (SELECT 1 FROM RH_MANDRAENKE_LAUF m WHERE m.SITZUNG_ID = s.ID) OR EXISTS (SELECT 1 FROM RH_GLOCKEN g WHERE g.SITZUNG_ID = s.ID) "
                    + "OR EXISTS (SELECT 1 FROM RH_REGIELAUF r WHERE r.SITZUNG_ID = s.ID) ORDER BY s.ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) { while (r.next()) l.add(new long[]{r.getLong(1), r.getTimestamp(2).getTime(), r.getLong(3), r.getLong(4)}); }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Wetterlagen

    @Override public List<Daten.Wetterlage> wetterlagen() throws Fehler {
        return mit("Wetterlagen", c -> {
            List<Daten.Wetterlage> l = new ArrayList<>();
            StringBuilder cols = new StringBuilder();
            for (String s : Verbindung.WETTER.values()) cols.append(", ").append(s);
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, BEAUFORT, PHASE, MONATE, BESCHREIBUNG, HERKUNFT" + cols
                    + " FROM RH_WETTERLAGE ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next()) {
                    double[] v = new double[Weather.Var.values().length];
                    java.util.Arrays.fill(v, Double.NaN);
                    for (Map.Entry<Weather.Var, String> e : Verbindung.WETTER.entrySet()) {
                        if (e.getKey() == Weather.Var.CLOUD) {
                            String k = r.getString(e.getValue());
                            if (k != null) try { v[e.getKey().ordinal()] = Weather.CloudKind.valueOf(k).ordinal(); } catch (IllegalArgumentException ex) { /* leer */ }
                        } else v[e.getKey().ordinal()] = d(r, e.getValue());
                    }
                    l.add(new Daten.Wetterlage(r.getString(1), dbl(r, "BEAUFORT"), r.getString(3), v, r.getString(4), r.getString(5), r.getString(6)));
                }
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Regiebücher

    @Override public List<Daten.Regiebuch> regiebuecher() throws Fehler {
        return mit("Regiebücher", c -> {
            List<Daten.Regiebuch> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT NAME, TEXT, BESCHREIBUNG, DATEI, HERKUNFT FROM RH_REGIEBUCH "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next()) l.add(new Daten.Regiebuch(r.getString(1), clob(r, "TEXT"), r.getString(3), r.getString(4), r.getString(5)));
            }
            return l;
        });
    }

    @Override public void speichern(Daten.Regiebuch rb) throws Fehler {
        Regie.Book b;
        try { b = Regie.parse(rb.text()); } catch (RuntimeException e) { throw new Fehler("Regiebuch nicht lesbar: " + e.getMessage(), e); }
        if (!b.name().equals(rb.name())) throw new Fehler("Der Name im Text („" + b.name() + "“) passt nicht zu „" + rb.name() + "“");
        Startdaten.Kopf k = Startdaten.Kopf.aus(b);
        zusammen("Regiebuch speichern", c -> {
            if (mitgeliefert(c, "RH_REGIEBUCH", b.name())) throw new SQLException("„" + b.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM RH_REGIEBUCH WHERE NAME = ? AND HERKUNFT = 'EIGEN'")) { st.setString(1, b.name()); st.executeUpdate(); }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_REGIEBUCH (NAME, BESCHREIBUNG, DATEI, SZENE_MS, SZENE_TEXT, ZEITRAFFER, LAENGE_S, FOTOS, WARTEN, BLATT, "
                    + "WETTER, TEXT, HERKUNFT) VALUES (?, ?, NULL, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, b.name());
                setS(st, 2, b.about());
                setMs(st, 3, k.szeneMs());
                setS(st, 4, k.szeneMs() == null ? null : Daten.text(k.szeneMs()));
                setD(st, 5, k.raffer());
                st.setDouble(6, b.length());
                st.setInt(7, k.fotos());
                st.setInt(8, k.warten());
                st.setInt(9, k.blatt() ? 1 : 0);
                setS(st, 10, Startdaten.wetterBlock(rb.text()));
                setClob(st, 11, rb.text());
                st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM RH_REGIEBUCH WHERE NAME = ?", b.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_STICHWORT (REGIEBUCH_ID, NR, SEKUNDE, BEFEHL, ARGUMENTE, TEXT) VALUES (?, ?, ?, ?, ?, ?)")) {
                int nr = 0;
                for (Regie.Zeile z : b.zeilen()) {
                    st.setLong(1, id);
                    st.setInt(2, ++nr);
                    st.setDouble(3, z.sekunde());
                    st.setString(4, z.befehl().toUpperCase(Locale.ROOT));
                    setS(st, 5, z.argumente());
                    setS(st, 6, z.text());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenRegiebuch(String name) throws Fehler { loeschen("RH_REGIEBUCH", name); }

    // ------------------------------------------------------------------ Fahrten

    @Override public List<Daten.Fahrt> fahrten() throws Fehler {
        return mit("Fahrten", c -> {
            Map<Long, List<Daten.Stuetzpunkt>> p = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT FAHRT_ID, NR, ZEIT_S, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD "
                    + "FROM RH_STUETZPUNKT ORDER BY FAHRT_ID, NR")) {
                while (r.next())
                    p.computeIfAbsent(r.getLong(1), k -> new ArrayList<>()).add(new Daten.Stuetzpunkt(r.getInt(2), r.getDouble(3), d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"),
                            d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD")));
            }
            List<Daten.Fahrt> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT ID, NAME, ART, DAUER_S, LAENGE_M, BESCHREIBUNG, HERKUNFT FROM RH_FAHRT "
                    + "ORDER BY CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, ID")) {
                while (r.next())
                    l.add(new Daten.Fahrt(r.getString(2), r.getString(3), dbl(r, "DAUER_S"), dbl(r, "LAENGE_M"), r.getString(6), r.getString(7),
                            p.getOrDefault(r.getLong(1), List.of())));
            }
            return l;
        });
    }

    // ------------------------------------------------------------------ Ansichten

    private static final String ANSICHT_SQL = "SELECT ID, NAME, TASTE, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, AUGE_UEBER_GRUND_M, AERA, UNTER_WASSER, "
            + "SZENE_MS, HERKUNFT";

    private static Daten.Ansicht ansicht(ResultSet r, String zustand, byte[] png) throws SQLException {
        double[] k = {d(r, "KAM_X"), d(r, "KAM_Y"), d(r, "KAM_Z"), d(r, "KAM_GIER_GRAD"), d(r, "KAM_NICK_GRAD")};
        return new Daten.Ansicht(r.getLong("ID"), r.getString("NAME"), integer(r, "TASTE"), r.getString("ART"), k, dbl(r, "AUGE_UEBER_GRUND_M"), r.getString("AERA"),
                r.getInt("UNTER_WASSER") == 1, ms(r, "SZENE_MS"), zustand, png, r.getString("HERKUNFT"));
    }

    @Override public List<Daten.Ansicht> ansichten() throws Fehler {
        return mit("Ansichten", c -> {
            List<Daten.Ansicht> l = new ArrayList<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(ANSICHT_SQL + " FROM RH_ANSICHT ORDER BY CASE ART WHEN 'FEST' THEN 0 ELSE 1 END, TASTE, ID DESC")) {
                while (r.next()) l.add(ansicht(r, null, null));
            }
            return l;
        });
    }

    @Override public Daten.Ansicht ansicht(long id) throws Fehler {
        return mit("Ansicht", c -> {
            try (PreparedStatement st = c.prepareStatement(ANSICHT_SQL + ", ZUSTAND, VORSCHAU FROM RH_ANSICHT WHERE ID = ?")) {
                st.setLong(1, id);
                try (ResultSet r = st.executeQuery()) {
                    if (!r.next()) throw new SQLException("Ansicht " + id + " gibt es nicht");
                    return ansicht(r, clob(r, "ZUSTAND"), r.getBytes("VORSCHAU"));
                }
            }
        });
    }

    @Override public long speichern(Daten.Ansicht a, Long sitzung) throws Fehler {
        Daten.Geom lage = Daten.Geom.punkt(a.kamera()[0], a.kamera()[2]);
        return zusammen("Ansicht merken", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_ANSICHT (NAME, ART, KAM_X, KAM_Y, KAM_Z, KAM_GIER_GRAD, KAM_NICK_GRAD, AERA, UNTER_WASSER, LAGE, "
                    + "SZENE_MS, SZENE_TEXT, ZUSTAND, VORSCHAU, SITZUNG_ID, HERKUNFT) VALUES (?, 'GEMERKT', ?, ?, ?, ?, ?, ?, ?, " + geomSql(lage) + ", ?, ?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, a.name());
                for (int i = 0; i < 5; i++) setD(st, 2 + i, Math.round(a.kamera()[i] * 100) / 100.0);
                st.setString(7, a.aera() == null ? Daten.HEUTE : a.aera());
                st.setInt(8, a.unterWasser() ? 1 : 0);
                int i = setGeom(st, 9, lage);
                setMs(st, i, a.szeneMs());
                setS(st, i + 1, a.szeneMs() == null ? null : Daten.text(a.szeneMs()));
                setClob(st, i + 2, a.zustand());
                if (a.vorschau() == null) st.setNull(i + 3, Types.BLOB); else st.setBytes(i + 3, a.vorschau());
                setL(st, i + 4, sitzung);
                st.executeUpdate();
            }
            return neueId(c, "RH_ANSICHT_SEQ");
        });
    }

    @Override public void loeschenAnsicht(long id) throws Fehler {
        zusammen("Ansicht löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM RH_ANSICHT WHERE ID = ? AND HERKUNFT = 'EIGEN'")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    // ------------------------------------------------------------------ Presets

    @Override public List<Daten.PaketPreset> presets(String paket, String klasse) throws Fehler {
        return mit("Presets", c -> {
            Map<Long, Map<String, String>> w = new LinkedHashMap<>();
            try (Statement st = c.createStatement(); ResultSet r = st.executeQuery("SELECT PAKET_PRESET_ID, FELD, WERT FROM RH_PAKET_WERT ORDER BY PAKET_PRESET_ID, ID")) {
                while (r.next()) w.computeIfAbsent(r.getLong(1), k -> new LinkedHashMap<>()).put(r.getString(2), r.getString(3) == null ? "" : r.getString(3));
            }
            List<Daten.PaketPreset> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ID, PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT FROM RH_PAKET_PRESET WHERE (? IS NULL OR PAKET = ?) "
                    + "AND (? IS NULL OR KLASSE = ?) ORDER BY PAKET, KLASSE, CASE HERKUNFT WHEN 'MITGELIEFERT' THEN 0 ELSE 1 END, NAME")) {
                st.setString(1, paket);
                st.setString(2, paket);
                st.setString(3, klasse);
                st.setString(4, klasse);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(new Daten.PaketPreset(r.getString(2), r.getString(3), r.getString(4), r.getString(5), r.getString(6), w.getOrDefault(r.getLong(1), Map.of())));
                }
            }
            return l;
        });
    }

    @Override public void speichern(Daten.PaketPreset p) throws Fehler {
        Map<String, String> typ = Startdaten.typen(p);
        zusammen("Preset speichern", c -> {
            if (zahl(c, "SELECT COUNT(*) FROM RH_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'MITGELIEFERT'", p.paket(), p.klasse(), p.name()) > 0)
                throw new SQLException("„" + p.name() + "“ ist mitgeliefert; bitte unter einem eigenen Namen speichern");
            try (PreparedStatement st = c.prepareStatement("DELETE FROM RH_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); st.executeUpdate();
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_PAKET_PRESET (PAKET, KLASSE, NAME, BESCHREIBUNG, HERKUNFT) VALUES (?, ?, ?, ?, 'EIGEN')")) {
                st.setString(1, p.paket()); st.setString(2, p.klasse()); st.setString(3, p.name()); setS(st, 4, p.beschreibung()); st.executeUpdate();
            }
            long id = Long.parseLong(Db.einWert(c, "SELECT TO_CHAR(ID) FROM RH_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ?", p.paket(), p.klasse(), p.name()));
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_PAKET_WERT (PAKET_PRESET_ID, FELD, TYP, WERT) VALUES (?, ?, ?, ?)")) {
                for (Map.Entry<String, String> e : p.felder().entrySet()) {
                    st.setLong(1, id);
                    st.setString(2, e.getKey());
                    st.setString(3, typ.getOrDefault(e.getKey(), "STRING"));
                    setS(st, 4, e.getValue());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public void loeschenPreset(String paket, String klasse, String name) throws Fehler {
        zusammen("Preset löschen", c -> {
            try (PreparedStatement st = c.prepareStatement("DELETE FROM RH_PAKET_PRESET WHERE PAKET = ? AND KLASSE = ? AND NAME = ? AND HERKUNFT = 'EIGEN'")) {
                st.setString(1, paket); st.setString(2, klasse); st.setString(3, name);
                if (st.executeUpdate() == 0) throw new SQLException("„" + name + "“ ist mitgeliefert oder gibt es nicht");
            }
            return null;
        });
    }

    // ------------------------------------------------------------------ Sitzungen

    @Override public long sitzungBeginnen(String version, String gpu, String qualitaet) throws Fehler {
        return zusammen("Sitzung beginnen", c -> {
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_SITZUNG (RECHNER, BENUTZER, APP_VERSION, GPU, QUALITAET) VALUES (?, ?, ?, ?, ?)")) {
                setS(st, 1, com.dan.store.DateiAblage.rechner());
                setS(st, 2, System.getProperty("user.name", ""));
                st.setString(3, version.length() > 20 ? version.substring(0, 20) : version);
                setS(st, 4, gpu == null ? null : gpu.length() > 120 ? gpu.substring(0, 120) : gpu);
                setS(st, 5, qualitaet);
                st.executeUpdate();
            }
            long id = neueId(c, "RH_SITZUNG_SEQ");
            try (Statement st = c.createStatement()) { st.executeUpdate("DELETE FROM RH_LOG WHERE ZEIT < SYSTIMESTAMP - INTERVAL '90' DAY"); }
            return id;
        });
    }

    @Override public void sitzungSichern(long id, Sicherung s) throws Fehler {
        Map<String, String> sp = s.spalten();
        double rs = Zahl.lies(sp.get(REGIE_SEKUNDE)), zr = Zahl.lies(sp.get(ZEITRAFFER)), ms = Zahl.lies(sp.get(SZENE_MS)), ae = Zahl.lies(sp.get(AERA)),
                pg = Zahl.lies(sp.get(PEGEL)), ws = Zahl.lies(sp.get(WINDSTAU));
        zusammen("Sitzung sichern", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE RH_SITZUNG SET ZUSTAND = ?, ZUSTAND_ZEIT = SYSTIMESTAMP, REGIEBUCH_ID = " + REGIEBUCH_ID + ", REGIE_SEKUNDE = ?, "
                    + "SZENE_MS = ?, SZENE_TEXT = ?, AERA = ?, ZEITRAFFER = ?, PEGEL_M = ?, WINDSTAU_M = ?, MANDRAENKE_STUFE = ? WHERE ID = ?")) {
                setClob(st, 1, s.zustand());
                setS(st, 2, sp.get(REGIEBUCH));
                setD(st, 3, rs);
                if (Double.isNaN(ms)) { st.setNull(4, Types.NUMERIC); st.setNull(5, Types.VARCHAR); }
                else { st.setLong(4, (long) ms); st.setString(5, Daten.text(ms)); }
                setD(st, 6, Double.isNaN(ae) ? null : Math.round(ae * 1000) / 1000.0);
                setD(st, 7, zr);
                setD(st, 8, r3(pg));
                setD(st, 9, r3(ws));
                setS(st, 10, sp.get(STUFE));
                st.setLong(11, id);
                st.executeUpdate();
            }
            return null;
        });
    }

    @Override public void sitzungBeenden(long id) throws Fehler {
        zusammen("Sitzung beenden", c -> {
            try (PreparedStatement st = c.prepareStatement("UPDATE RH_SITZUNG SET ENDE = SYSTIMESTAMP, SAUBER_BEENDET = 1 WHERE ID = ?")) { st.setLong(1, id); st.executeUpdate(); }
            return null;
        });
    }

    private static Daten.Sitzung letzte(Connection c, long ausser) throws SQLException {
        try (PreparedStatement st = c.prepareStatement("SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.ZUSTAND, s.ZUSTAND_ZEIT, r.NAME AS REGIEBUCH, s.REGIE_SEKUNDE, "
                + "s.SZENE_MS, s.AERA, s.PEGEL_M, s.MANDRAENKE_STUFE FROM RH_SITZUNG s LEFT JOIN RH_REGIEBUCH r ON r.ID = s.REGIEBUCH_ID WHERE s.ZUSTAND IS NOT NULL AND s.ID <> ? "
                + "ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC FETCH FIRST 1 ROWS ONLY")) {
            st.setLong(1, ausser);
            try (ResultSet r = st.executeQuery()) {
                if (!r.next()) return null;
                Timestamp b = r.getTimestamp(2), e = r.getTimestamp(3), zz = r.getTimestamp(6);
                return new Daten.Sitzung(r.getLong(1), b.getTime(), e == null ? null : e.getTime(), r.getInt(4) == 1, clob(r, "ZUSTAND"), zz == null ? null : zz.getTime(),
                        r.getString(7), dbl(r, "REGIE_SEKUNDE"), ms(r, "SZENE_MS"), dbl(r, "AERA"), dbl(r, "PEGEL_M"), r.getString("MANDRAENKE_STUFE"));
            }
        }
    }

    @Override public Daten.Sitzung letzteSitzung(long ausser) throws Fehler { return mit("Letzte Sitzung", c -> letzte(c, ausser)); }

    // ------------------------------------------------------------------ Protokoll

    @Override public void protokoll(List<Protokoll.Zeile> zeilen, Long sitzung) throws Fehler {
        if (zeilen.isEmpty()) return;
        zusammen("Protokoll", c -> {
            List<Long> ids = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT RH_LOG_SEQ.NEXTVAL FROM DUAL CONNECT BY LEVEL <= ?")) {
                st.setInt(1, zeilen.size());
                try (ResultSet r = st.executeQuery()) { while (r.next()) ids.add(r.getLong(1)); }
            }
            try (PreparedStatement st = c.prepareStatement("INSERT INTO RH_LOG (ID, SITZUNG_ID, ZEIT, STUFE, QUELLE, TEXT, SZENENZEIT, ORT, DETAIL) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                for (int i = 0; i < zeilen.size(); i++) {
                    Protokoll.Zeile z = zeilen.get(i);
                    st.setLong(1, ids.get(i));
                    setL(st, 2, sitzung);
                    st.setTimestamp(3, new Timestamp(z.zeitMs()));
                    st.setString(4, z.stufe());
                    st.setString(5, z.quelle().length() > 60 ? z.quelle().substring(0, 60) : z.quelle());
                    st.setString(6, z.text().length() > 1000 ? z.text().substring(0, 997) + " …" : z.text());
                    setS(st, 7, z.szenenzeit() == null ? null : z.szenenzeit().length() > 40 ? z.szenenzeit().substring(0, 40) : z.szenenzeit());
                    setS(st, 8, z.ort() == null ? null : z.ort().length() > 40 ? z.ort().substring(0, 40) : z.ort());
                    setClob(st, 9, z.detail());
                    st.addBatch();
                }
                st.executeBatch();
            }
            return null;
        });
    }

    @Override public List<String> letzteZeilen(int n) throws Fehler {
        return mit("Protokoll lesen", c -> {
            List<String> l = new ArrayList<>();
            try (PreparedStatement st = c.prepareStatement("SELECT ZEIT, STUFE, QUELLE, TEXT FROM RH_LOG ORDER BY ID DESC FETCH FIRST ? ROWS ONLY")) {
                st.setInt(1, n);
                try (ResultSet r = st.executeQuery()) {
                    while (r.next())
                        l.add(0, Protokoll.zeile(new Protokoll.Zeile(r.getTimestamp(1).getTime(), r.getString(2), r.getString(3), r.getString(4), null, null, null)));
                }
            }
            return l;
        });
    }

    @Override public synchronized void schliessen() {
        try { if (c != null) c.close(); } catch (SQLException e) { /* zu */ }
        c = null;
    }
}
