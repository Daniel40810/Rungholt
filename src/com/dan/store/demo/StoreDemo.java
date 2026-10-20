package com.dan.store.demo;

import com.dan.store.Db;
import com.dan.store.Einrichter;
import com.dan.store.Felder;

import java.io.File;
import java.sql.Connection;

/**
 * Vorführung ohne App: zeigt den Zustand eines Objekts im Textformat und prüft, wenn es einen Zugang gibt, ein
 * Schema mit dem Einrichter (nur lesen). Aufruf: {@code StoreDemo [db/db.properties] [db/sql] [PRÄFIX]}.
 */
public final class StoreDemo {
    public static void main(String[] a) throws Exception {
        System.out.println("Ein Objekt mit öffentlichen Feldern als Text:\n");
        System.out.println(Felder.alsText(Felder.lesen(new StoreCheck.Probe()), "com.dan.store · Textformat"));
        if (a.length < 2) {
            System.out.println("Mit db.properties und dem Ordner der Skripte prüft die Vorführung auch ein Schema (nur lesen).");
            return;
        }
        Db.Konfig k = Db.konfig(new File(a[0]), "HAITHABU", null);
        if (!k.hatPasswort()) { System.out.println("Kein Passwort in " + a[0]); return; }
        System.out.println("Verbinde " + k.kurz() + " …");
        Einrichter e = new Einrichter();
        if (a.length > 2) e.praefix = a[2];
        try (Connection c = Db.oeffnen(k)) {
            System.out.println("Stufe des Schemas: " + e.stufe(c));
            for (String z : e.pruefen(c, new File(a[1])).zeilen()) System.out.println("  " + z);
        }
    }
}
