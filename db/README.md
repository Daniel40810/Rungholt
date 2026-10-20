# db – Datenbank von Rungholt

Schema `RUNGHOLT` auf PDBORCL (angelegt mit `USER.sql`), Präfix `RH_`, Spatial mit SRID 8307 (WGS 84, Länge und Breite). Werkbuch Abschnitt 8, Fragen F25–F36 „alle A“ (20.10.2026). Der Zugang steht in `db.properties` (Vorlage `db.properties.example`, nie ins Git); fehlt die Datei, legt die App sie aus der Vorlage an, fragt im Startbild nach dem Passwort und trägt es nach dem ersten erfolgreichen Verbinden ein. Ohne Datenbank läuft die App weiter und speichert in den Ordner `daten/` des Projekts (F34); beim nächsten Verbinden übernimmt „Übernehmen“ im Block DATEN die Chronik.

## Einrichten

Startbild oder Block DATEN, Knopf **Einrichten**. Der Einrichter (`com.dan.store.Einrichter`) führt die Skripte in `sql/` der Reihe nach aus und prüft danach alles: Tabellen, Spalten mit Typ, Sequenzen, Trigger, Sichten, Spatial-Metadaten, räumliche Indizes und die Zahl der mitgelieferten Zeilen (18 Arten); dazu rechnet er Kette, Kronen, Ausschnitt, Springtiden und Sturmfluten nach. Am Ende steht „alles in Ordnung“ oder genau, was fehlt. Er löscht nie etwas und lässt sich beliebig oft wiederholen. Das erste Einrichten dauert etwas länger (der Tidenkalender hat 5 762 Zeilen).

| Skript | Inhalt |
|---|---|
| `sql/01_tabellen.sql` | 27 Tabellen, 26 Sequenzen, 25 Trigger (Sequence + Trigger, nie IDENTITY), 25 Indizes |
| `sql/02_spatial.sql` | Metadaten und räumlicher Index für elf Geometriespalten; Metadaten für die drei Sichten mit Geometrie (für QGIS) |
| `sql/03_sichten.sql` | Neun Sichten: **`RH_WARFT_V`** (Kronen mit Fläche und den Fundstellen in 100 m), **`RH_RUNGHOLT_V`** (Warften, Bauwerke, Fundstellen als eine Ebene), **`RH_TAGESKARTE_V`** (alles eines Tags als eine Ebene), `RH_SPRINGNIPP_V`, `RH_NW_TAG_V` (Niedrigwasser bei Tag), `RH_TIDE_TAG_V`, `RH_MANDRAENKE_V`, `RH_BEFUND_V`, `RH_LETZTE_SITZUNG_V` |
| `sql/04_startdaten.sql` | 23 Quellen, 29 Befunde, zwei Gelände (heute mit Prüfsummen, 1362), drei Pegelorte mit 30 Partialtiden, der Tidenkalender (Husum und Rand 2026–2027, der Rand im Januar 1362), 54 Warften, 9 Bauwerke, 14 Fundstellen, 13 Sturmfluten, 13 Wetterlagen, 3 Regiebücher mit 100 Stichworten, 9 Ansichten, 3 Fahrten, 75 Presets. Erzeugt aus `com.dan.rungholt.data.Startdaten` mit `tools.StartdatenSql`, nicht von Hand ändern |
| `sql/99_entfernen.sql` | Entfernt alle `RH_`-Objekte samt Inhalt. Nur von Hand für einen Neuanfang, die App führt es nie aus |

Dieselben Skripte lassen sich in SQL Developer als Skript ausführen (F5). Bei einem zweiten Lauf meldet Oracle ORA-00955 (Name schon vergeben); das ist harmlos.

Meldet der räumliche Index ORA-01031 oder ORA-13249, fehlt dem Schema ein direkt erteiltes Recht (eine Rolle reicht dafür nicht). Als DBA: `GRANT CREATE TABLE, CREATE SEQUENCE TO RUNGHOLT;`, dann noch einmal einrichten.

## Prüfen

`test/com/dan/rungholt/test/DbPruefung.java` in NetBeans mit „Run File“ starten. Sie richtet ein, läuft den Einrichter ein zweites Mal, prüft mit ihm, rechnet Kette, Kronen und Ausschnitt auf dem Ellipsoid nach, prüft alle Geometrien auf Gültigkeit, liest die Tide aus der Datenbank (höchstens 1 mm vom Code), fragt Tidenkalender und Sichten, schreibt eine Chronik für den 31.12.2099 (Tide mit Wasserlinie, Zählungen, Lauf der Mandränke mit Bresche, Glocken, Lauf eines Regiebuchs mit Blatt), legt eine eigene Fundstelle an, fragt die Zellkarte, speichert Regiebuch, Ansicht und Preset unter „~Prüfung …“, sichert eine Sitzung, übernimmt Dateien und räumt danach auf. Ende: „Alles in Ordnung.“

## Szenenzeit (F33)

Oracle rechnet vor dem 15.10.1582 julianisch; ein TIMESTAMP für den 23.1.1362 der App wäre dort der 15.1., acht Tage daneben. Die App zählt proleptisch gregorianisch: die Nacht der Mandränke (15./16.1.1362 in den Chroniken, julianisch) ist bei ihr die vom 23. auf den 24. (Phase 17); das HUD nennt das julianische Datum dazu. Darum steht die Szenenzeit als `SZENE_MS` (Millisekunden seit 1970, proleptisch gregorianisch wie die App), als `SZENE_TEXT` („15.01.1362 22:30 Ortszeit“) und als `SZENE_TAG` (`'1362-01-15'`, zum Filtern). Die Sichten geben ab 1583 zusätzlich `SZENENZEIT` als TIMESTAMP in deutscher Zeit. Die Sturmfluten führen ihr Datum so, wie die Quelle zählt, mit `KALENDER` (vor 1700 in Schleswig julianisch).

## Tabellen

| Thema | Tabelle | Inhalt |
|---|---|---|
| Betrieb | `RH_SCHEMA` | Ausbaustufe (Phase 16 = 1) |
| | `RH_SITZUNG` | Sitzungen; Zustand (CLOB) alle zwei Minuten und beim Ende, Szenenzeit, Ära, Zeitraffer, Regiebuch mit Stelle, Pegel, Windstau, Stufe der Mandränke |
| | `RH_LOG` | Protokoll; älter als 90 Tage räumt die App beim Start weg |
| Quellen | `RH_QUELLE`, `RH_BEFUND`, `RH_BEFUND_QUELLE` | Quellen; Befunde je Gegenstand: `BELEGT`, `GEMESSEN` oder `GESTALTET`, mit ihren Quellen |
| Gelände | `RH_GELAENDE` | Heute (BAW, SHA-256 der Dateien in `daten/gelaende`) und 1362 (gerechnet); Ausschnitt als Fläche, Ursprung der Szenenmeter an der Kirche |
| Tide | `RH_PEGELORT`, `RH_PARTIALTIDE` | Husum, Pellworm [G] und der Rand (Norderhever [G]) mit je zehn Partialtiden; die App liest sie beim Start (F26), der Code ist der Rückfall |
| | `RH_TIDENKALENDER` | Jedes Hoch- und Niedrigwasser mit Höhe und Spring/Nipp (F27) |
| Chronik | `RH_TIDE` | Jede Wende am Rand: Pegel, Windstau, trockener Anteil, Pfützen, Strömung; bei Niedrigwasser die nasse Fläche als Mehrfachfläche (64 m, F29) |
| | `RH_ZAEHLUNG` | Je Niedrigwasser Seehunde (liegend, Heuler, auf der Flucht) und Wattvögel je Art, mit Lage |
| | `RH_MANDRAENKE_LAUF` | Jede Nacht der Mandränke: Zeiten der Stufen, höchster Pegel vor dem Deich und in der Marsch, Häuser, Bruchstelle (Punkt), Bresche (Linie) |
| | `RH_GLOCKEN` | Jedes Läuten der Glocken aus dem Watt |
| Rungholt | `RH_WARFT` | Die 54 Warften (F30): Krone als Fläche, Mitte, Höhe, Häuser, Sodenbrunnen, Befund |
| | `RH_BAUWERK` | Kirche, Seedeich, Warftenkette, Sielzüge, Siel, Sieltief, Sielhafen |
| | `RH_FUNDSTELLE` | 14 mitgelieferte (Busch ab 1921, Museen, 2023) und eigene (Umschalt + Klick ins Watt, F31) |
| | `RH_STURMFLUT` | 13 belegte Sturmfluten 1362–2013 mit Quelle; Scheitel Husum nur, wo gemessen (F32) |
| Regie | `RH_WETTERLAGE`, `RH_REGIEBUCH`, `RH_STICHWORT`, `RH_REGIELAUF` | Wetterlagen; Regiebücher als Text und in Stichworten; jeder Lauf mit Blatt (PNG), im Kino oder nicht |
| Kamera | `RH_ANSICHT`, `RH_FAHRT`, `RH_STUETZPUNKT` | Feste Ansichten 1–9 und gemerkte (Taste V) mit Zustand und Vorschau; Fahrten |
| Packages | `RH_PAKET_PRESET`, `RH_PAKET_WERT` | Presets je Package, ein öffentliches Feld je Zeile |

Mitgelieferte Zeilen (`HERKUNFT = 'MITGELIEFERT'`) stellt der Einrichter bei jedem Lauf wieder her; eigene Zeilen (`EIGEN`) fasst er nie an. Die App überschreibt Mitgeliefertes nicht: ein Regiebuch oder Preset bekommt einen eigenen Namen.

## QGIS (Abnahme Phase 16)

1. Satellitenbild als Grundkarte (etwa XYZ-Kacheln „Esri World Imagery“ oder das GLO-30-Satellitenbild aus `templates/`).
2. Neue Verbindung zu Oracle: Datenbank `PDBORCL`, Benutzer `Rungholt`. Unter den Tabellen erscheinen die Geometriespalten mit SRID 8307.
3. Ebenen `RH_WARFT_V` (Geometrie `FLAECHE`, Schlüssel `ID`) und `RH_FUNDSTELLE` (Geometrie `LAGE`, Schlüssel `ID`) hinzufügen; oder alles auf einmal: `RH_RUNGHOLT_V` (Geometrie `GEOM`, Schlüssel `OBJEKT_ID`, Stil nach `EBENE`).
4. Die Tageskarte: `RH_TAGESKARTE_V`, Filter `"TAG" = '2026-07-21' OR "TAG" IS NULL`, Stil nach `EBENE` und `ART`.

Ohne Datenbank: `daten/chronik/*.csv` als „Getrennte Texte“ (Semikolon, Geometrie aus der Spalte `WKT`, EPSG:4326). Dieselbe Karte zeichnet die App als PNG (Block CHRONIK, „Tageskarte“).
