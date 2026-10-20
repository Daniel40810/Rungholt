package com.dan.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt die SQL-Skripte in {@code db/sql} in einzelne Anweisungen, so wie SQL Developer sie ausführt: Eine
 * SQL-Anweisung endet mit ; am Zeilenende, ein PL/SQL-Block (Trigger, BEGIN … END) mit einer Zeile, die nur / enthält.
 * Kommentarzeilen (--) und SQL*Plus-Befehle (SET, PROMPT) fallen weg. Zeichenketten über mehrere Zeilen kommen in den
 * Skripten nicht vor.
 * <p>
 * Dazu liest es aus den CREATE-TABLE-Anweisungen die Spalten heraus: Daraus prüft der Einrichter, ob alle Spalten da
 * sind, und trägt fehlende nach. So steht der Tabellenentwurf an einer Stelle.
 */
public final class Skript {
    private Skript() { }

    /** Eine Anweisung mit der Zeile, in der sie beginnt (für Meldungen). */
    public record Anweisung(String sql, int zeile, boolean plsql) {
        /** Die erste Zeile, gekürzt, für Meldungen. */
        public String kurz() {
            String f = sql.strip().split("\\R", 2)[0];
            return f.length() > 90 ? f.substring(0, 87) + " …" : f;
        }
    }

    /** Eine Spalte aus einer CREATE-TABLE-Anweisung. */
    public record Spalte(String tabelle, String name, String typ, String definition) { }

    private static final Pattern PLSQL = Pattern.compile("^(CREATE\\s+(OR\\s+REPLACE\\s+)?(TRIGGER|PROCEDURE|FUNCTION|PACKAGE|TYPE)\\b|BEGIN\\b|DECLARE\\b)",
            Pattern.CASE_INSENSITIVE);

    public static List<Anweisung> zerlegen(String text) {
        List<Anweisung> l = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int start = 0, no = 0;
        boolean pl = false;
        for (String raw : text.split("\\R", -1)) {
            no++;
            String z = raw.strip();
            if (cur.length() == 0) {
                if (z.isEmpty() || z.startsWith("--") || z.toUpperCase(Locale.ROOT).startsWith("SET ") || z.toUpperCase(Locale.ROOT).startsWith("PROMPT")
                        || z.equals("/")) continue;
                start = no;
                pl = PLSQL.matcher(z).find();
            }
            if (pl) {
                if (z.equals("/")) {
                    l.add(new Anweisung(cur.toString().strip(), start, true));
                    cur.setLength(0);
                    continue;
                }
                cur.append(raw).append('\n');
            } else {
                if (z.startsWith("--")) continue;
                if (z.endsWith(";")) {
                    cur.append(raw, 0, raw.lastIndexOf(';'));
                    l.add(new Anweisung(cur.toString().strip(), start, false));
                    cur.setLength(0);
                } else cur.append(raw).append('\n');
            }
        }
        if (cur.toString().strip().length() > 0) l.add(new Anweisung(cur.toString().strip(), start, pl));
        return l;
    }

    private static final Pattern TABLE = Pattern.compile("^CREATE\\s+TABLE\\s+(\\w+)\\s*\\(", Pattern.CASE_INSENSITIVE);

    /** Die Spalten aller CREATE-TABLE-Anweisungen, je Zeile eine Spalte (wie in 01_tabellen.sql). */
    public static List<Spalte> spalten(List<Anweisung> as) {
        List<Spalte> l = new ArrayList<>();
        for (Anweisung a : as) {
            Matcher m = TABLE.matcher(a.sql());
            if (!m.find()) continue;
            String t = m.group(1).toUpperCase(Locale.ROOT);
            String[] zeilen = a.sql().split("\\R");
            for (int i = 1; i < zeilen.length; i++) {
                String z = zeilen[i].strip();
                if (z.endsWith(",")) z = z.substring(0, z.length() - 1);
                if (z.isEmpty() || z.startsWith(")") || z.toUpperCase(Locale.ROOT).startsWith("CONSTRAINT")) continue;
                String[] p = z.split("\\s+", 2);
                if (p.length < 2) continue;
                String typ = p[1].split("\\s+")[0].toUpperCase(Locale.ROOT);
                l.add(new Spalte(t, p[0].toUpperCase(Locale.ROOT), typ, p[1]));
            }
        }
        return l;
    }

    /** Name des Objekts, das eine CREATE-Anweisung anlegt („TABLE HB_HAUS“), oder null. */
    public static String objekt(String sql) {
        Matcher m = Pattern.compile("^CREATE\\s+(OR\\s+REPLACE\\s+)?(TABLE|SEQUENCE|INDEX|VIEW|TRIGGER)\\s+(\\w+)", Pattern.CASE_INSENSITIVE).matcher(sql.strip());
        return m.find() ? m.group(2).toUpperCase(Locale.ROOT) + " " + m.group(3).toUpperCase(Locale.ROOT) : null;
    }

    /** Die Längengrenze einer VARCHAR2-Spalte in Zeichen oder -1. */
    public static int laenge(Spalte s) {
        Matcher m = Pattern.compile("VARCHAR2\\((\\d+)", Pattern.CASE_INSENSITIVE).matcher(s.definition());
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }
}
