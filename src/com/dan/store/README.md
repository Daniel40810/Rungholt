# com.dan.store – Datenseite einer Szene

Das Paket bündelt, was eine Szene braucht, um mit oder ohne Oracle zu speichern: Presets und Zustand über die öffentlichen Felder der Packages, SQL-Skripte, die man in SQL Developer lesen kann, einen Einrichter, der nie etwas löscht, ein Protokoll und die Sitzung für „Weiter wie zuletzt“. Es stammt aus Lighthouse (Phase 15) und wurde in Haithabu (Phase 16, F25 A) herausgelöst, damit die nächste Szene es nicht ein drittes Mal kopieren muss.

Der Kern braucht nur Java 21 mit `java.sql`. Den Treiber `ojdbc11.jar` bringt die App mit. Das Paket kennt keine App: Präfix, Skripte, Sichten und Startdaten stellt die App in die öffentlichen Felder des Einrichters. Zum Herauskopieren reicht `src/com/dan/store/`.

## Was es kann

- **Felder** (`Felder`): liest und schreibt die öffentlichen, nicht statischen, nicht finalen Felder einfacher Art (double, float, int, long, boolean, String, Aufzählungen). Falsche Werte und unbekannte Felder werden gemeldet, nicht geschrieben. Neue Felder späterer Phasen kommen von selbst mit.
- **Textformat** (`Felder.alsText`, `Felder.ausText`): Zeilen `schluessel=wert`, Kommentare mit `#`, Zeilenumbruch als `\n`. Dasselbe Format dient für Presets, den ganzen Zustand, gemerkte Ansichten und die Dateien ohne Datenbank.
- **Zahlen** (`Zahl`): Punkt als Dezimalzeichen, keine überflüssigen Nullen; gelesen werden Punkt und Komma.
- **Skripte** (`Skript`): zerlegt SQL wie SQL Developer: `;` am Zeilenende, PL/SQL-Blöcke bis `/`. Aus CREATE TABLE liest es die Spalten; so steht der Tabellenentwurf an einer Stelle.
- **Einrichter** (`Einrichter`): führt die Skripte der Reihe nach aus. Meldungen, die beim Wiederholen kommen (Name vergeben, Spalte da, Zeile da, Metadaten da), zählen nicht als Fehler. Fehlende Spalten trägt er nach, ungültige Trigger und Sichten übersetzt er neu. Danach prüft er Tabellen, Spalten mit Typ, Sequenzen, Trigger, Indizes, räumliche Indizes, Spatial-Metadaten, Sichten, die Ausbaustufe und die Zahl der mitgelieferten Zeilen. Er meldet „alles in Ordnung“ oder genau, was fehlt.
- **Verbindung** (`Db`): `db/db.properties` mit url, user, password; leeres Passwort aus einer Umgebungsvariablen oder von der App erfragt und nach dem ersten Erfolg eingetragen. Kurze Zeitgrenzen (3 s Aufbau, 30 s je Antwort).
- **Ablage** (`Ablage`, `DateiAblage`): Sitzungen und Protokoll in die Datenbank oder in Dateien. `DateiAblage` schreibt je Eintrag eine Datei über eine Zwischendatei, behält zehn Sitzungen und hängt das Protokoll an `protokoll.txt`.
- **Dienst** (`Dienst`): ein Faden niedriger Priorität schreibt das Protokoll alle 5 s und sichert den Zustand alle 120 s und beim Ende. Scheitert die Datenbank, geht das Protokoll in die Ersatzablage.
- **Protokoll** (`Protokoll`): Fehler mit Stapel, Warnungen, Bedienschritte, je mit Szenenzeit und Ort.
- **Geometrie als Text** (`Wkt`): Punkt, Linie, Fläche für `SDO_GEOMETRY(wkt, 8307)`, ohne `sdoapi`. Flächen werden gegen den Uhrzeigersinn gedreht, wie Oracle es will.

## Benutzung

```java
Einrichter e = new Einrichter();
e.praefix = "HB_";
e.sichten = new String[]{"HB_HAUS_V"};
e.metadaten = new String[]{"HB_HAUS.LAGE"};
e.raeumlich = new String[]{"HB_HAUS_LAGE_SX"};
try (Connection c = Db.oeffnen(Db.konfig(new File("db/db.properties"), "HAITHABU", "HAITHABU_DB_PASSWORD"))) {
    Einrichter.Ergebnis r = e.einrichten(c, new File("db/sql"), (text, anteil) -> System.out.println(text));
    System.out.println(r.kurz());
}

// Zustand eines Packages als Text und zurück
String t = Felder.alsText(Felder.lesen(ocean), "Noor");
Felder.schreiben(ocean, Felder.ausText(t));
```

## Prüfung und Vorführung

- `demo/StoreCheck` prüft ohne Fenster und ohne Datenbank: Felder und Textformat hin und zurück, falsche Werte, Skripte mit PL/SQL, WKT, Dateiablage, Sitzung und Protokoll über den Dienst. Endet mit „Alles in Ordnung.“
- `demo/StoreDemo` zeigt das Textformat und prüft mit `db.properties`, Skriptordner und Präfix ein Schema, nur lesend.

## Herkunft

Aus `com.dan.lighthouse.db` (Lighthouse Phase 15): `Felder`, `Zahl` und `Skript` unverändert, `Einrichter`, `Db`, `Protokoll` und `Dienst` ohne Bezug auf Lighthouse, `DateiAblage` aus dem allgemeinen Teil von `DateiSpeicher`. `Wkt` ist neu. Der Rückfluss nach Lighthouse ist für Haithabu Phase 17 geplant.
