package com.dan.store;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Zugang zu einer Oracle-Datenbank über {@code db/db.properties} (Schlüssel url, user, password), wie in Heidelberg,
 * Kneiphof, Kīlauea und Lighthouse. Die Datei steht in .gitignore. Ist password leer, wird eine Umgebungsvariable
 * gelesen ({@link Konfig#env}), sonst fragt die App und trägt es nach dem ersten erfolgreichen Verbinden ein
 * ({@link #passwortSpeichern}). Kurze Zeitgrenzen: Ohne Datenbank soll keine App lange warten.
 * <p>
 * Nur Java und {@code java.sql}; den Treiber (ojdbc) bringt die App mit.
 */
public final class Db {
    private Db() { }

    public static final String STANDARD_URL = "jdbc:oracle:thin:@//localhost:1521/PDBORCL";
    /** Zeitlimit für den Verbindungsaufbau in Sekunden. */
    public static int verbindenS = 3;
    /** Zeitlimit je Antwort in Sekunden. */
    public static int antwortS = 30;

    /** Die Zugangsdaten. Das Passwort bleibt im Objekt; {@link #kurz()} zeigt es nie. */
    public static final class Konfig {
        public final String url, user, env;
        public final File datei;
        private final char[] passwort;
        public Konfig(String url, String user, char[] passwort, File datei, String env) {
            this.url = url; this.user = user; this.passwort = passwort; this.datei = datei; this.env = env;
        }
        public boolean hatPasswort() { return passwort != null && passwort.length > 0; }
        public Konfig mitPasswort(char[] pw) { return new Konfig(url, user, pw, datei, env); }
        char[] passwort() { return passwort; }
        /** „HAITHABU@localhost:1521/PDBORCL“, ohne Passwort. */
        public String kurz() { return user + "@" + url.replaceFirst("^jdbc:oracle:thin:@/{0,2}", ""); }
    }

    /**
     * Der Ordner db: die Systemeigenschaft {@code eigenschaft} (etwa {@code haithabu.db.dir}), sonst ./db, sonst db
     * neben dem Jar oder bis zu zwei Ebenen darüber.
     */
    public static File ordner(String eigenschaft, Class<?> anker) {
        String p = eigenschaft == null ? null : System.getProperty(eigenschaft);
        if (p != null && !p.isEmpty()) return new File(p);
        File d = new File("db");
        if (d.isDirectory()) return d;
        try {
            File jar = new File(anker.getProtectionDomain().getCodeSource().getLocation().toURI());
            File base = jar.isFile() ? jar.getParentFile() : jar;
            for (int i = 0; i < 3 && base != null; i++, base = base.getParentFile()) {
                File c = new File(base, "db");
                if (c.isDirectory()) return c;
            }
        } catch (Exception e) { /* weiter */ }
        return d;
    }

    /**
     * Liest eine db.properties. {@code userVorgabe} gilt, wenn user fehlt; {@code env} ist die Umgebungsvariable für
     * ein leeres Passwort. Wirft FileNotFoundException mit lesbarer Meldung, wenn es die Datei nicht gibt.
     */
    public static Konfig konfig(File f, String userVorgabe, String env) throws IOException {
        if (!f.isFile()) throw new FileNotFoundException(f.getPath() + " fehlt (Vorlage: db.properties.example)");
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f.toPath())) { p.load(new InputStreamReader(in, StandardCharsets.UTF_8)); }
        String url = p.getProperty("url", STANDARD_URL).trim();
        String user = p.getProperty("user", userVorgabe).trim();
        String pw = p.getProperty("password", "").trim();
        if (pw.isEmpty() && env != null) {
            String e = System.getenv(env);
            if (e != null) pw = e;
        }
        return new Konfig(url, user, pw.toCharArray(), f, env);
    }

    /** Öffnet eine Verbindung mit kurzen Zeitgrenzen; autoCommit an. */
    public static Connection oeffnen(Konfig k) throws SQLException {
        Properties p = new Properties();
        p.setProperty("user", k.user);
        p.setProperty("password", new String(k.passwort()));
        p.setProperty("oracle.net.CONNECT_TIMEOUT", Integer.toString(verbindenS * 1000));
        p.setProperty("oracle.jdbc.ReadTimeout", Integer.toString(antwortS * 1000));
        DriverManager.setLoginTimeout(verbindenS + 1);
        Connection c = DriverManager.getConnection(k.url, p);
        c.setAutoCommit(true);
        return c;
    }

    /** Verbinden und fragen, wer man ist: „HAITHABU auf PDBORCL · Oracle 21“. */
    public static String pruefen(Konfig k) throws SQLException {
        try (Connection c = oeffnen(k); Statement s = c.createStatement();
             ResultSet r = s.executeQuery("select user, sys_context('USERENV','CON_NAME') from dual")) {
            r.next();
            return r.getString(1) + " auf " + r.getString(2) + " · Oracle " + c.getMetaData().getDatabaseMajorVersion();
        }
    }

    /** Schreibt das Passwort in die Zeile {@code password=}; alle anderen Zeilen und Kommentare bleiben. */
    public static void passwortSpeichern(Konfig k, char[] pw) throws IOException {
        File f = k.datei;
        List<String> zeilen = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
        String neu = "password=" + new String(pw).replace("\\", "\\\\");
        boolean gefunden = false;
        for (int i = 0; i < zeilen.size(); i++) {
            String t = zeilen.get(i).trim();
            if (t.startsWith("password") && t.substring(8).trim().startsWith("=")) { zeilen.set(i, neu); gefunden = true; }
        }
        if (!gefunden) zeilen.add(neu);
        Files.write(f.toPath(), zeilen, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ kleine Helfer für Abfragen

    /** Der erste Wert der ersten Zeile als Text oder null. */
    public static String einWert(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) st.setObject(i + 1, args[i]);
            try (ResultSet r = st.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }

    /** Die erste Spalte aller Zeilen. */
    public static Set<String> namen(Connection c, String sql) throws SQLException {
        Set<String> s = new HashSet<>();
        try (Statement st = c.createStatement(); ResultSet r = st.executeQuery(sql)) {
            while (r.next()) s.add(r.getString(1));
        }
        return s;
    }

    /** Die erste Zeile einer Oracle-Meldung. */
    public static String erste(SQLException e) {
        String m = String.valueOf(e.getMessage());
        return m.split("\\R")[0].strip();
    }
}
