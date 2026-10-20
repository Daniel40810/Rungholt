package com.dan.rungholt.tools;

import com.dan.rungholt.scene.Terrain;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Phase 16: schreibt {@code db/sql/04_startdaten.sql} aus {@code data.Startdaten} und der Szene (Gelände aus
 * {@code daten/gelaende} für die Prüfsummen, das Sieltief und den Sielhafen). Aufruf:
 * {@code StartdatenSql [daten/gelaende] [db/sql/04_startdaten.sql]}. Die Prüfung vergleicht die Datei mit dem, was
 * dieses Werkzeug jetzt schreiben würde.
 */
public final class StartdatenSql {
    public static void main(String[] a) throws Exception {
        File dir = new File(a.length > 0 ? a[0] : "daten/gelaende");
        File out = new File(a.length > 1 ? a[1] : "db/sql/04_startdaten.sql");
        String sql = erzeugen(dir);
        Files.writeString(out.toPath(), sql, StandardCharsets.UTF_8);
        System.out.println(out + ": " + sql.length() / 1024 + " kB, " + sql.lines().filter(l -> l.startsWith("-- ZAHLEN")).findFirst().orElse(""));
    }

    /** Das Skript aus dem Gelände in dir. */
    public static String erzeugen(File dir) {
        return com.dan.rungholt.data.StartdatenSql.sql(com.dan.rungholt.data.StartdatenSql.Alles.aus(dir, Terrain.load(dir)));
    }

    private StartdatenSql() { }
}
