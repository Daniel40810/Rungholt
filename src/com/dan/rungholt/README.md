# com.dan.rungholt – die App

Rungholt – Zweimal am Tag Land: das Nordfriesische Wattenmeer zwischen Pellworm, Südfall und Nordstrand mit seiner Tide, und unter ihm die Stadt Rungholt, die in der Nacht vom 15. auf den 16. Januar 1362 (julianisch; in der Uhr der App, die gregorianisch zählt, vom 23. auf den 24.) unterging. Die App verdrahtet die Element-Packages und zeichnet; sie selbst enthält keine Physik der Elemente.

**Stand: Phase 17, Schliff (fertig). Davor Phase 16: die Datenbank, Phase 15: Regiebücher, Kameras, Kino, Phase 14: der Klang, Phase 13: die Zeitreise und die Spuren, Phase 12: die Grote Mandränke, Phase 11: die Stadt, Phase 10: das Land, Phase 9: Südfall und das Jahr, Phase 8: die Wattvögel, Phase 7: die Seehunde, Phase 6: Priele, Kanten, Strömung, Phase 5: das Watt als Material, Phase 4: die Tide, Phase 3: Himmel und Wetter, Phase 2: das Gelände heute, Phase 1: das Grundgerüst.** Plan und Fortschritt stehen im Artefakt „Rungholt Werkbuch“.

| Paket | Inhalt |
|---|---|
| `app` | `RungholtApp`: Startbild (mit Datenbank), FFrame maximiert, Szene, Bedienfeld, Statuszeile mit dem Speicherort, Sitzung und Chronik, Fehlerdatei `daten/fehler.txt`; `Dauerlauf` (Phase 17) |
| `data` (Phase 16) | `Daten` (Datensätze, Geometrie in Szenenmetern mit WKT, Szenenzeit F33), `Speicher` mit `DbSpeicher` (Schema RUNGHOLT) und `DateiSpeicher` (`daten/`), `Verbindung` (Einrichter), `Datenseite`, `Startdaten` und `StartdatenSql` (die mitgelieferten Daten, `quellen.txt`, `befunde.txt`, `sturmfluten.txt`), `Pegel` (Partialtiden aus der Datenbank, F26), `Chronik`, `Wasserlinie` (nasse Fläche als Mehrfachfläche), `Zustand` („Weiter wie zuletzt“), `Uebernahme`, `Tageskarte` – siehe [db/README.md](../../../../db/README.md) |
| `gl` | Engine aus Glacier, entschlackt: `Renderer` (Himmel aus `com.dan.sky` mit Rundumbild und Augenanpassung, Wolken, Gelände, Bodendecke, Meer, Halme, Regen, Nebel, Blitz, Nachbearbeitung, HUD, Zeitmessung je Durchgang, Auflösungsregelung, Foto F12, Shader neu F5), `TerrainLod` (CDLOD aus Glacier), `SceneView`, `Shader`, `Gpu`, `Mat4`, `HudOverlay`, `ComputeProbe`, `Quality` |
| `gl/shader` | `common.glsl` (Platzhalter-Himmel, Dunst), `sky.frag`, `skypano.frag`, `terrain.vert`, `terrain_height.glsl`, `terrain.frag`, `post.frag`, `overlay.frag`, `probe.comp`; die Bühne von Phase 1 (`stage.frag`) liegt in `_alt/shader_phase1` |
| `camera` | `Camera` (Kreisen, Frei, Fahrt, Mitfahrt), `Fahrt` (Wattwanderung, Über die Warftenkette, seit Phase 15 Prielfahrt), `Ansichten` 1–9 |
| `scene` | `Site` (Ort, Maße, Pegelwerte), `Geo` (transversale Mercator, UTM 32 Nord), `Sun`, `SceneClock` (MEZ/MESZ, 1362 mit Ortszeit, vor 1582 mit dem julianischen Datum), `Terrain` (Gelände 2048² zu 8 m, Art je Zelle), `Buehne` (Höhenfeld der Bühne, Rückfall ohne Daten), `Physik` (eigener Faden, fester Schritt 1/120 s) |
| `scene` (Phase 7) | `Seehundbank`, `Wattwanderer` (seit Phase 8 auch Störer der Wattvögel) |
| `wiring` | 20 Schnittstellen und `Wiring` mit Platzhalter-Pegel, Zeitreise und der Wetterregie (Klima Nordfriesland) – siehe [wiring/README.md](wiring/README.md) |
| `ui` | `ControlPanel` (ZEIT, WETTER, TIDE mit Tidenkurve, WATT, LEBEN, ZEITREISE, REGIEBÜCHER, KLANG, MANDRÄNKE, KAMERA, BILD, CHRONIK, DATEN, TECHNIK), `Startbild` (Stand der Datenbank, Einrichten, Weiter wie zuletzt, Im Kino), `Weiter`, `Karten` (Warft- und Zellkarte aus der Datenbank, eigene Fundstelle), `Tidenkurve`, `AppIcon` (Kirchturm im Watt), `Theme` |
| `tools` | `Tempo`: Tempo-Probe und Standbild ohne Fenster (seit Phase 2 mit `PEGEL=`, seit Phase 3 mit `WETTER=`, `BEDECKUNG=`, `REGEN=`, `NEBEL=`, `SICHT=`, `GEWITTER=`, `BLITZ`, `TAG=`); `GelaendeImport` und `GeoTiff`: Küstenhöhen der BAW einlesen; `Hoerproben` (Phase 14): die Hörproben als WAV und Spektrogramm nach `docs/hoerproben`; `StartdatenSql` (Phase 16): schreibt `db/sql/04_startdaten.sql`; `Quellenliste` (Phase 17): schreibt `docs/QUELLEN.md` |

## Schliff (Phase 17)

- **Das Datum der Mandränke**: Die Chroniken nennen die Nacht vom 15. auf den 16. Januar 1362, julianisch gezählt. Die Uhr der App zählt proleptisch gregorianisch; dort war am 15. fast Nipptide (Faktor 0,97). Die Nacht liegt darum jetzt am 23./24. Januar (`Mandraenke.DAY = SceneClock.gregorian(1362, 1, 15)`), auf der Springtide (Faktor 1,19). Sie beginnt um 22 Uhr; die Wellen laufen gegen 0:40 über die Krone, der Deich bricht gegen 1:40, das Hochwasser kommt um 4:36, der Morgen danach liegt im Tageslicht. Auf der Springtide steigt die Marsch bis gut NHN +5,2 m, höher als vorher bei Nipp. Vor dem 15.10.1582 nennt die Uhr das julianische Datum dazu: „23.1.1362 (jul. 15.1.) 22:00 Ortszeit“ (`SceneClock.julian`, `gregorian`).
- **Marken aus**: Das 1-km-Raster, der Umriss der Kirche, der Ring um die Warft von Südfall und der Nordpfeil aus Phase 1 sind ab Start aus (`Renderer.showMarks`, Block BILD „Marken“). Sie waren die lange helle Linie im Vogelauge und der orange Strich am Horizont von Südfall auf den Blättern von Status_Phase15.
- **Gischt über der Krone** (offen seit Phase 12): In der Nacht der Mandränke schlagen Brecher an die Seeseite der Deichkrone (`Renderer.stepSpray`, `com.dan.spray`), sobald die Kamera höchstens 1,5 km vom Deich steht. Je näher der Spiegel der Krone kommt, desto öfter und höher, bis 12 je Sekunde auf 1,2 km Deich. Dazu reißt der Orkan Fahnen von den Kämmen. Drei neue Verbindungen (87). Was sonst noch für Phase 17 geplant war (Nässe an `ground` und `weathering`, Eis im Watt, Regen an Hauswänden, die Kogge in der Welle), steht im Ideenspeicher.
- **Qualitätsstufen durchgängig**: `Quality.mudflat()`, `timber()`, `smoke()`, `fracture()`. Die Häuser von 1362 werden bis 2,5 / 4 / 6 / 9 km gezeichnet, der Herdrauch hat 28 bis 72 Ballen, der Kleikörper des Deichs 60 bis 600 Zellen. Die Tabelle steht in der [Projekt-README](../../../../README.md).
- **Dauerlauf** (`app.Dauerlauf`, Block TECHNIK oder `-Drungholt.dauerlauf=Minuten`): Runden aus „Eine Tide“ im Kino, zwei Tiden frei im Zeitraffer 360 und „Die Grote Mandränke“ im Kino, mindestens zwei. Bericht und Kurven kommen nach `docs/status/Dauerlauf_….png` und `.txt`.
- **Symbol und Verknüpfung**: die PNGs in `ui/icon/rungholt_NN.png` (7 Größen), `Rungholt.ico` und `Rungholt_Verknuepfung.bat` im Projektordner.
- **Quellenliste**: `docs/QUELLEN.md` aus `quellen.txt`, `befunde.txt` und `sturmfluten.txt` (`tools.Quellenliste`); die Prüfung vergleicht sie.

## Die Datenbank (Phase 16)

F25–F36 „alle A“. Schema `RUNGHOLT` auf PDBORCL, Präfix `RH_`, 27 Tabellen, neun Sichten, SRID 8307; ohne Datenbank dieselben Daten in `daten/` (CSV mit Spalte WKT für QGIS). Einrichten, Prüfen und QGIS: [db/README.md](../../../../db/README.md).

- **Startbild**: verbindet über `db/db.properties` (fragt nach dem Passwort), prüft die Ausbaustufe, zeigt Warften, Fundstellen, Tidenkalender, Chronik, Regiebücher, die letzte Sitzung und das letzte Ergebnis des Einrichters. Knöpfe „Los“, „Weiter wie zuletzt“, „Einrichten“, „Ohne Datenbank“, dazu ein Regiebuch „Im Kino“.
- **Tide aus der Datenbank** (F26): beim Start liest die App die Partialtiden von `RH_PARTIALTIDE` in die Tide des Rands und den Pegel Husum; der Code ist der Rückfall. Die Prüfung vergleicht beide Kurven (unter 1 mm über ein Jahr). Block TIDE: die **Tidenkurve** über 25 h mit Windstau und den Uhrzeiten der Wenden.
- **Chronik** (F28, F29): jede Wende am Rand, auch im Zeitraffer; bei Niedrigwasser die nasse Fläche als Mehrfachfläche (64 m, höchstens alle 30 s echter Zeit) und die Zählung der Seehunde und Vögel; jeder Lauf der Mandränke mit Zeiten der Stufen, Bruchstelle und Bresche; jedes Läuten der Glocken; jeder Lauf eines Regiebuchs mit Blatt. Block CHRONIK: Tiden des Tages, letzte Tiden („Uhr dorthin“), Tageskarte (PNG), Chronik an/aus, Chronik einer Sitzung leeren.
- **Karten im Bild** (F30, F31, F36): ein Klick auf eine Warft öffnet die Warftkarte, ein Klick ins Watt die **Zellkarte** (Grund heute und 1362, Wasser, Trockenzeit, Strömung, die Wenden am Rand); beide bekommen aus der Datenbank die Zeile der Warft, die Fundstellen in 100 m und wie oft die Stelle bei Niedrigwasser nass blieb. Ein zweiter Klick schließt. **Umschalt + Klick** ins Watt legt eine eigene Fundstelle an.
- **Block DATEN**: Verbinden, Ohne Datenbank, Einrichten, Prüfen, Weiter wie zuletzt, Übernehmen (Dateien in die Datenbank), die belegten Sturmfluten, Presets der Packages (anwenden, speichern unter), gemerkte Ansichten (Taste V), Protokoll.
- **Die milde Nacht**: die Mandränke läuft seit Phase 16 bei 5 °C mit Regen statt Schnee (Orkan aus Westnordwest).

## Regiebücher, Prielfahrt, Kino (Phase 15)

- **Regiebücher als Text** (`scene.Regie`, `scene/regie/*.txt`, aus Glacier): je Zeile ein Stichwort `m:ss befehl argumente | Text`. Befehle: `zeit`, `raffer`, `wetter` (Verlauf im Kopf), `wetter_frei`, `sturm`, `ansicht` (Tasten 1 bis 9, dazu Fuhlehörn, Kirche im Sturm, Grabung, Draufsicht, Kirchwarft, Bruchstelle), `fahrt` (Prielfahrt, Über die Warftenkette), `aera heute|1362`, `mandraenke Kirche|Siel|Ost`, `morgen`, `glocken`, `grabung`, `magnetik an|aus`, `warte`, `foto`, `blatt`, `sage`.
- **`warte`** (neu gegenüber Glacier): die Uhr des Regiebuchs steht, bis die Szene so weit ist: `warte uhr [TT.MM.] hh:mm`, `warte mandraenke`, `warte sturm`, `warte ueberlauf`, `warte bruch`, `warte flut`. Damit hängen die Bilder an der Tide und an der Nacht, nicht an geschätzten Sekunden; nach 10 min geht es mit einer Meldung weiter.
- **Eine Tide**: 21.7.2026, Hochwasser 7:02 bis Hochwasser 19:19 im Zeitraffer 180 (Wetter als Verlauf: Sommermorgen, Mittag mit Quellwolken, stiller Abend): vom Deich, Vogelauge, am Priel, auf dem Watt, Seehundbank, Südfall, die Flut in den Prielen, die Prielfahrt (Zeitraffer 20), zurück an den Deich. Gut 6 min, 11 Fotos.
- **Die Grote Mandränke**: 23.1.1362 (julianisch 15.1.), 22 Uhr: vom Kirchturm, am Sielhafen, auf der Kirchwarft (Zeitraffer 120), wartet auf den Überlauf (Zeitraffer 60) und auf den Bruch auf dem Deich; die Echtzeit der Mandränke (vom Bruch bis 3 min nach der offenen Bresche) füllen Blicke vom Turm, von der Kirchwarft und vom Sielhafen; dann die treibenden Häuser (Zeitraffer 240), die Nacht in einem Sprung, der Morgen des 16. um 9:15. Gut 6 min, 10 Fotos.
- **Die Glocken** (vorher im Code von `scene.Glocken`): 30.11.2026, wie in Phase 13, mit `warte uhr` statt Bedingungen im Code. 7 Fotos.
- **Fotos und Blatt**: die Fotos ohne HUD; am Ende ein Blatt mit allen Fotos, Bilder/s und Grafikkarte in `docs/status/Regiebuch_….png`.
- **Prielfahrt** (`scene.Prielfahrt`, F20 A): mit dem Boot bei Flut den Priel südlich der hohen Watten hinauf nach Nordosten, 1,7 km in 139 s (12 m/s), Auge 1,6 m über dem Wasser, wie es beim Start steht. Der Weg ist der kürzeste über die Rinne (Zellen tiefer als NHN −0,8 m, je tiefer, desto billiger) zwischen zwei Punkten [G]. Das Boot scheucht Seehunde und Vögel (Störer). Ein Boot ist nicht gezeichnet.
- **Kino** (`com.dan.kino` wie in Glacier): „Im Kino zeigen“ im Block REGIEBÜCHER (oder „Regiebuch“ unter „Die Glocken“). Ganzer Bildschirm ohne Bedienfeld, Breitwand 2,39 : 1, Vorspann „Rungholt · Zweimal am Tag Land“, Untertitel aus den Stichworten, Überblendung bei jedem Schnitt, Schwenk ±3°, Filmkorn, Abspann mit Ort und Quellen; der Ton folgt dem Schwarz. Wartet das Regiebuch im Vorspann (die Mandränke baut das Land von 1362), bleibt die Titeltafel stehen. Esc heraus (das Regiebuch läuft weiter), Leertaste hält an, F12 fotografiert mit Balken.
- **Tempo** `REGIE=Tide|Mandraenke|Glocken` (Blatt), dazu `KINO=1` (Kinobilder `…_kino_01.png`); `GLOCKEN=1` entfällt.

## Der Klang (Phase 14)

`gl.Klang` verdrahtet `com.dan.soundscape` mit der Szene: das Ohr ist die Kamera (Ort, Blickrichtung), der Ton wird in Echtzeit erzeugt (`AudioOut` in eigenem Faden). Bedienfeld KLANG: „Ton an“, Lautstärke, Zeile mit dem Stand.

- **Wetter:** Wind, Böen, Regen und Temperatur aus der Regie; der Wind trägt den Schall (`windCarry`): mit dem Wind bis +5 dB, gegen ihn bis −24 dB.
- **See:** nach dem Abstand zum offenen Wasser (mindestens vier von zwölf Richtungen über 1 m tief; ein Priel zählt nicht als See). Bei Ebbe hört man sie nur von fern, im flachen Wasser höchstens 0,45 · Tiefe hohe Wellen. Steht das Ohr auf Deich, Warft oder Vorland am Wasser, brechen die Wellen an der Kante (Brandung).
- **Knistern:** der Anteil frisch trockengefallenen Watts (0,05 bis 7 h trocken) in 40 m um das Ohr, nur nah am Boden.
- **Priele:** die vier stärksten Strömungen der Überflutung in 500 m, die Quelle an der Kante zum Ohr hin. Die Überflutung rechnet Mittel über 16-m-Zellen; für den Klang zählt die doppelte Zellgeschwindigkeit als Strömung in der Rinne [G].
- **Wattvögel:** die Rufe aus `flock.Waders` (Auffliegen, Landen, Zug) nach Art; dazu rufen Gruppen am Boden in 700 m beim Fressen und Rasten (Austernfischer etwa alle 9 s, Ringelgänse alle 5 s, Brandgänse alle 30 s; beim Rasten seltener, in großen Trupps öfter) [G]; ein wendender Schwarm rauscht.
- **Seehunde:** `seal.onCall` (Alt, Junges, Alarm) als `SEEHUND` oder `HEULER`. **Donner** aus `lightning.onThunder`.
- **1362:** die Herdfeuer der Stadt (aus den Rauchquellen), Rinder und Schafe (eins der acht nächsten Tiere alle 3 bis 12 s), an der Kaje des Sielhafens Wasser an den Pfählen, ein Schiffszimmerer, ein Sägeplatz und Leute beim Laden (bei Tag), die Glocke der Kirche um 6, 12 und 18 Uhr eine Minute lang [G].
- **Mandränke:** Brecher an der Krone, sobald die See über Krone − 1,5 m steht (im Takt der Wellen); beim Bruch der Einsturz und Brocken, danach das Wasser durch die Bresche (`Torrent`, Überfall nach Poleni); Sturmläuten vom Turm, hastig und ungleich, bis das Wasser in den Turm steigt.
- **Die Glocken aus dem Watt** (Sage): solange `scene.Glocken` läutet, Schläge in ungleichen Abständen (1,3 bis 3,5 s) über der Kirche, dumpf, vom Wind getragen: bei Fuhlehörn (4,9 km östlich) im Weststurm in den Pausen zwischen den Böen hörbar, gegen den Wind fast nicht.
- Ereignisse werden im Bildlauf nur geplant; die Synthese läuft in einem eigenen Faden (höchstens 24 in der Warteschlange) und nur bei „Ton an“.
- **Hörproben** (`tools.Hoerproben`, ohne Grafikkarte, ohne automatische Lautstärke; jede Datei zum Anhören auf −1 dBFS Spitze angehoben, die Anhebung steht in `hoerproben.txt`, die beiden Glockenproben mit derselben Anhebung, damit der Unterschied bleibt): `1_Ebbe_am_Priel`, `2_Die_Bank`, `3_Der_Hafen_1362`, `4a_Mandraenke_Sturmlaeuten_auf_der_Kirchwarft`, `4b_Mandraenke_der_Bruch`, `5_Glocken_Fuhlehoern_mit_dem_Wind`, `6_Glocken_Fuhlehoern_gegen_den_Wind`, je als WAV und Spektrogramm, dazu `hoerproben.txt` mit Pegeln und gezählten Rufen.
- Der Schemen der Kirche schwankt nur noch ±0,09 m (vorher ±0,4 m mit dem Wellengang).

## Die Zeitreise und die Spuren (Phase 13)

- **Die Spuren** (`scene.Spuren`) [G: wie sie aussehen; belegt sind Art und Lage der Befunde]: eine Karte von 2048² zu 3,5 m um die Kirche aus demselben Land von 1362 wie die Warften, darum liegen die Spuren genau unter ihnen (Prüfung: der Schwerpunkt des Kleis jeder der 54 Warften liegt höchstens 1 m neben ihrer Mitte). Kanäle: Kleirücken (der Rest des Warftkörpers, bis 0,25 m über dem Watt, in Kamera und Shader gleich), Kulturschicht (Torf, Kleibrocken, Ziegelbruch, Muschelkalk), Magnetik, Messfläche. Dazu im Shader die Sodenbrunnen (40, als Liste), die Gräben der Fennen und die Sielzüge als dunkle Bänder (nur hier und da erhalten) und das Fundament der Kirche aus Feldstein und Backsteinbruch mit den Maßen aus `timber.Church`. Nur heute und nur auf dem Watt; in 1362 blenden sie aus.
- **Magnetik** [G, nach dem Muster der veröffentlichten Bilder von 2023, nicht die Daten]: Quellen (Warften leicht positiv, Gräben negativ, Brunnen, Kirche und Siel stark) mit einem geneigten Dipol gefaltet (Inklination 69°, Plus südlich), Streifen der Messfahrten, rund 7 km². Als Karte nach dem Dunst über das Gelände gelegt; Knopf „Draufsicht mit Magnetik“ (2,6 km senkrecht).
- **Fundstellen**: 14 Punkte (Busch ab 1921, Museum Pellworm und Nordstrand, Messung und Bohrung 2023) als Ringe, Farbe nach Herkunft.
- **Die Grabung** (`scene.Grabung`, Figuren aus `com.dan.figure` mit `Outfit.archaeologist`): sechs Leute kommen von Südfall, sobald die Stelle über der Nordwand des Schiffs trocken ist und nach der Tide noch 1¾ h trocken bleibt (Pegel am Rand mit Laufzeit gegen die Verbindungshöhe), nur bei Tag und nicht im Sturm; drei graben an den Rahmen, einer trägt Eimer zum Sieb, einer siebt, einer misst; eine Dreiviertelstunde vor der Flut gehen sie. Gerät als ein Netz (Rahmen, Eimer, Sieb, Messstab auf dem Dreibein, Fluchtstangen, Fundkiste). Stiefelabdrücke, Störer für Seehunde und Vögel.
- **Überblendung komplett**: die Kirche steigt zuerst aus dem Boden, dann die Häuser mit eigenem Einsatz (`Renderer.riseDepth`, die Kirche über `TimberGl.moveSpecial`, Rückfluss nach timber), die Häuser von Südfall versinken; das Gras von 1362 wächst in Achteln über den Sand; Kaje und Boote ab 60 %, Vieh, Leute und Herdrauch ab 90 %. Wer zu Fuß auf einer Warft steht, steht danach auf ihrer Spur.
- **Wege von 1362** aus `com.dan.road`: Kleiweg auf der Warftenkette (1,7 m) und Pfad am Hauptsielzug, ohne Verkehr, im Umkreis von 1,5 km als Netz für `BuoyancyGl`.
- **Warftkarte**: ein Klick ins Bild trifft den Grund (`pickGround`); auf einer Warft oder ihrer Spur öffnet sich die Karte unten rechts (Name [G], Höhe, Häuser, Brunnen, Abdruck heute mit Wasser und Magnetik, Fundstellen in 100 m, Herkunft). Klick daneben schließt.
- **Die Glocken** (`scene.Glocken`, Klang seit Phase 14): nach der Sage nur heute, nach der Dämmerung, ab Bft 8 aus Südwest bis Nordwest und mit mehr als 0,5 m Wasser über der Kirchwarft; 40 bis 120 s Läuten, 10 bis 40 min Pause. Solange sie läuten, steht die Kirche als fahler Schemen über dem Wasser. „Glocken jetzt“ läutet sofort.
- **Regiebuch „Die Glocken“** (im Code, als Text in Phase 15): 30.11.2026, Weststurm Bft 9 vom Deich bei Fuhlehörn, Hochwasser 17:31 mit den Glocken bei der Kirche, die Nacht im Zeitraffer, am Mittag des 1.12. bei Ebbe die Grabung, die Magnetik von oben, auf der Kirchwarft nach 1362 und zurück (gut 2 min).
- **Bedienfeld** ZEITREISE: Schalter Spuren, Fundstellen, Magnetik, Grabung, Wege 1362; „Draufsicht mit Magnetik“, „Zur Grabung an der Kirche“, „Glocken jetzt“, „Regiebuch“.
- Abnahme ohne Fenster: `GLOCKEN=1 Tempo 3 1920 1080 docs/status/Glocken.png MITTEL 16.25 2026-11-30 3`; `ANSICHT=Draufsicht MAGNETIK=1 FUNDE=1 TIDE=13.3 Tempo …`; `WARFTKARTE=Kirchwarft` oder `WARFTKARTE=x,y` (Klick); `SPUREN=0`.

## Die Grote Mandränke (Phase 12)

- **Die Nacht** (`scene.Mandraenke`) [G]: 23. Januar 1362 ab 22 Uhr, julianisch der 15. (Phase 17: am 15. gregorianisch wäre fast Nipp gewesen, am 23. steht die Springtide, Faktor 1,19). Die Regie zieht auf, ab 23:30 Orkan aus Westnordwest (`weather`, Lage `ORKAN`); der Windstau steigt nach Drehbuch auf 3,6 m zum Hochwasser der Nacht (`Wiring.surgeScript`). Die Leute gehen auf die Kirchwarft.
- **Überlauf und Bruch** (`scene.Deichbruch`, `com.dan.fracture`): ab einem Meter unter der Krone schlagen die Wellen über den Deich und spülen an der Bruchstelle die Binnenböschung aus. Nach einer Stunde wird der Abschnitt (50 m) ein Körper aus Klei (150 Zellen, am Fuß verankert), das Gelände darunter ausgehoben; die Uhr läuft in Echtzeit. Die Kopferosion frisst sich durch, das Wasser drückt, in rund einer Minute brechen 50 bis 80 Stücke ab und kippen in die Bresche; die Strömung zieht landwärts, aber Brocken von einigen Kubikmetern Klei bleiben unter Wasser liegen.
- **Die Flut** (`com.dan.wave`): ist die Hälfte fort, stürzt das Wasser durch die Bresche – ein Dammbruch auf 1,6 km im Geviert zu 4 m, die See davor festgehalten (`Wave.hold`), als lange Welle im Meer gezeichnet. Die Marsch ist ein Becken (`Flood.basinMask`) mit Zu- und Abfluss über Bresche und Krone (Wehrformel mit Rückstau): sie steigt in Stunden auf gut NHN +4 m und läuft am Morgen langsam ab. Häuser, die tiefer als 1,5 m im Wasser stehen, schwimmen auf, treiben mit Strömung oder Wind und zerfallen; das Vieh ertrinkt, die Herdfeuer gehen aus, die Leute sitzen in der Kirche. Die Kirche steht.
- **Bedienfeld** MANDRÄNKE: Bruchstelle (vor der Kirche, westlich des Siels, im Osten), „Abend: 23.1.1362, 22 Uhr“, „Vor den Bruch“, „Jetzt brechen“ (Auslöser), „Nach der Flut“, „Beenden“, „Auf den Deich an der Bruchstelle“.
- Abnahme ohne Fenster: `MANDRAENKE=KIRCHE ANSICHT=Rungholt1362 TIDE=22,23.5,24.7,25.7,27,28.6,31,35.6 Tempo 3 1920 1080 docs/status/Nacht.png MITTEL 22 1362-01-23 3` (Stunden über 24: der nächste Tag); `KAMERA=BRUCH` vom Deich; `ECHTZEIT=0,20,40,50,60,75,90,150` der Bruch Sekunde für Sekunde.

## 1362: die Stadt (Phase 11)

- **Die Stadt** (`scene.Stadt1362`) [G]: auf den Warften der Kette zwei bis vier Häuser aus `com.dan.timber` (Hallenhäuser als Wohnstallhäuser, Wohnhäuser, offene Schuppen), auf den Hofwarften ein Wohnstallhaus und oft eine Scheune – 103 Häuser; alle auf den Grund gestellt (die Mitte zwischen tiefster und höchster Ecke).
- **Die Kirche** auf der Kirchwarft (NHN +3,8 m): neu in `com.dan.timber` die Klasse `Church` – Westturm mit Schindelhelm (33 m), Schiff mit Reetdach (24,5 × 13 m), Chor und halbrunde Apsis im Osten, Feldsteinsockel, Portale; 41 m lang wie der Fund von 2023. Gezeichnet als eigenes Netz (`TimberGl.setSpecial`). Neue Werkstoffe `CHURCH` (Klosterformat im Wendischen Verband, gemalte Rundbogenfenster und Schallöffnungen) und `SHINGLE`.
- **Der Sielhafen** (`scene.Sielhafen`, Hafenbecken im Gelände vor dem Siel, Kaje, Uferbefestigung und drei Boote aus `timber.Structures`): die **Kogge** (neu in `com.dan.buoyancy`: `Cog`, 23,3 × 7,6 m, 2,25 m Tiefgang wie die Bremer Kogge von 1380, Netz `Mesh.cog` mit Achterkastell, Mast, Rahsegel aufgetucht oder gesetzt) liegt auf Reede in der Hever, läuft mit der Flut ein, wenn das Tief sie trägt (über NHN +0,7 m), das Fahrwasser dem tiefsten Wasser nach, das Sieltief hinauf und an die Kaje, sitzt bei Ebbe aufrecht im Schlick und läuft zum nächsten Hochwasser nach frühestens 11 h wieder aus. Das Wasser für sie: Spiegel und Strömung aus der Überflutung von 1362, Grund aus dem Gelände von 1362.
- **Leben**: 64 Leute in Kleidung von 1362 (`Outfit.medieval`: Kittel, Gugel, Bundschuhe oder barfuß) gehen auf der Kette oder arbeiten an den Häusern, nachts sind sie drinnen; 127 Rinder auf den Fennen, 65 Schafe am Seedeich (ein Netz aus `buoyancy.Mesh`); Herdrauch aus den Rauchlöchern mit `com.dan.smoke` (im Wind, abends flach).
- **Ansichten** 8 „Rungholt 1362“ (vom Kirchturm, 19 m über der Warft) und 9 „Sielhafen“ (Deichkrone am Siel) schalten nach 1362; Fahrt „Über die Warftenkette“ (Knopf im Block ZEITREISE); Schalter „Stadt“ und „Herdrauch“.
- Abnahme ohne Fenster: `ANSICHT=Rungholt1362 TIDE=5,8,11,14,17,19.5,21,22.5 Tempo 3 1920 1080 docs/status/Tag1361.png MITTEL 5 1361-07-21 3`; `ANSICHT=Sielhafen TIDE=5.75,5.9,6,6.1,6.2,6.35,9.5,13.5 Tempo …` (die Kogge läuft ein).

## 1362: das Land (Phase 10)

- **Das Gelände von 1362** (`scene.Land1362`, ein zweites `Terrain` aus dem von heute, gut 5 s beim ersten Wechsel) [G]: hinter einem Seedeich (Krone NHN +3,5 m, Seeseite 1 : 6, Landseite 1 : 3, gut 500 m südlich der Kirche, von Pellworm bis Nordstrand) liegt die Marsch der Edomsharde auf NHN +0,5 bis +1 m; durch Rungholt zieht die Warftenkette (2,3 km, Wege auf der Kette), in ihrer Mitte die Kirchwarft (NHN +3,8 m), dazu Hofwarften in der Marsch – 54 Warften. Gräben teilen die Marsch in Fennen; drei Sielzüge führen das Binnenwasser zum Siel bei x = −3600 m, vor dem Siel das Sieltief zur Hever. Vor dem Deich ein schmales Vorland, dann Watt.
- **Neue Arten im Gelände**: `MARSCH`, `WARFT`, `DEICH1362`, `SIELZUG`; Höhe und Art von 1362 liegen auf den Einheiten 16 und 17, `uEra` blendet Höhe und Albedo über (4 s, Taste Z). Gräben, Sielzüge und Wege zeichnet der Shader aus Linienzügen (Wasser mit Himmel im Fresnel).
- **Die Tide läuft weiter**: eine eigene Überflutung für 1362 (Zwischendatei mit „_1362“), Watt und Nässe dazu; bei Springflut steht das Wasser am Deich, die Marsch bleibt trocken. Halme und Bodendecke wechseln mit (neu in `com.dan.ground`: Salzwiese mit Andel, Queller, Strandaster; Marsch mit Weißklee und Hahnenfuß auf Klei). Seehunde, Vögel, Figuren und die Häuser von Südfall gibt es nur heute.
- **Bedienfeld** ZEITREISE: Zeile zum Gelände 1362, Knopf „Auf den Seedeich von 1362“.
- Abnahme ohne Fenster: `KAMERA=60,526,355,2,5.9 ZEITREISE=0,0.25,0.5,0.75,1 OHNE_MARKEN=1 Tempo 3 1920 1080 docs/status/Zeitreise.png MITTEL 17 2026-07-21 3`; Springflut: `AERA=1362 KAMERA=60,548,190,4,5.9 TIDE=6,9,12,15,18,21 Tempo … 6.0 2026-08-04 3`.

## Südfall und das Jahr (Phase 9)

- **Wattwanderung** (`scene.Wattwanderung`, Figuren aus dem neuen Package `com.dan.figure`): ein Führer und 15 Personen gehen von Fuhlehörn (Deichfuß 4692/−1404, wo Ansicht 1 steht) über die hohen Watten (im Mittel NHN +0,8 m, nirgends unter −0,3 m) an der Kirche von Rungholt vorbei auf das Vorland von Südfall bis an die Warft: 5,8 km [G], Halte „Wattwurm“, „Herzmuscheln und Schlick“, „Rungholt: hier stand die Kirche“ (330 m vom Grundriss), „Salzwiese von Südfall“; Kleidung nach dem Monat (im Sommer meist barfuß). Losgehen 1,5 h vor Niedrigwasser, an der Warft nach rund 2,1 h. Fußspuren (barfuß oder Stiefel) bleiben im Watt bis zur Flut; die Gruppe stört Seehunde und Vögel. Der Wanderer zur Seehundbank ist jetzt auch eine Figur aus `com.dan.figure`; `gl/FigurGl` liegt in `_alt/phase7_figur`.
- **Fahrt „Wattwanderung“** (`Renderer.followHike`): die Kamera geht seitlich hinter der Gruppe mit (Mitfahrt, Maus zum Umsehen).
- **Das Jahr** (`wiring.Wiring.Jahreszeit` mit `com.dan.season`, Klima Nordfriesland, Startwert 26): Schnee auf Deich, Warft, Vorland, Koog und den höchsten trockenen Watten, Reif, der Queller im Herbst rot, die Salzwiese im Winter braungrau, Halme und Bodendecke nach Tag und Schnee, Schneefall unter 2,5 °C. Ein Sprung im Kalender rechnet den Winter vor.
- **Südfall im Detail** (`scene.Suedfall`): Friesenhaus (14 × 7,5 m, Backstein, Reet) und Stall auf der Warft aus `com.dan.timber` (neu: `BRICK`, `FRIESENHAUS`), mit Dachschnee; der Fething in der Senke der Warft und drei Pütten auf der Salzwiese (`gl/TeichGl`, `teich.vert/.frag`: stilles Wasser, im Frost Eis mit Schnee).
- **Vogelzug**: Ringelgänse im Keil im Frühjahr nach Nordost, im Herbst nach Südwest.
- Der Durchgang „Tiere“ heißt jetzt „Leben“: Häuser und Teiche von Südfall, Seehunde, Vögel, Figuren.
- **Bedienfeld** LEBEN: „Vogelzug“, Wattwanderung („Losgehen“, „Mitgehen“, „Beenden“, „Figuren“), „Südfall und das Jahr“ (Zeile mit Schnee und Salzwiese, Häuser und Teiche an/aus).
- Abnahme ohne Fenster: `JAHR=1,4,7,10 KAMERA=-905,-40,280,4,5 OHNE_MARKEN=1 Tempo 3 1920 1080 docs/status/Jahr.png MITTEL 13 2026-01-21 3`; `ANSICHT=VomDeich WANDERUNG=12.0 TIDE=12.05,12.2,12.5,12.9,13.3,13.6,13.85,14.1 Tempo … 12.0 2026-07-21 3`. Das Package allein: `FigureDemo --bild docs/status/Figuren.png`.

## Die Wattvögel (Phase 8)

- **Waders** aus `com.dan.flock`: Alpenstrandläufer, Austernfischer, Säbelschnäbler, Brandgans, Silber- und Lachmöwe, im Winterhalbjahr Ringelgänse; Zahl nach der Stufe (`Quality.birds`, Mittel 680 im Juli, 1 344 im September) und dem Monat. Grund mit der Feinform, Wasser aus der Überflutung, Schlick aus dem Sediment des Watts, Salzwiese aus der Art des Geländes, Monat, Sonne und Wind aus Uhr und Wetter, die Stunden seit Hochwasser aus dem Pegel am Rand (Norderhever).
- **Orte** [G] (aus den BAW-Höhen gesucht): Revier im Watt um Südfall und die Hallig bis Nordstrand (−1000/200, 2,6 km, und 2500/800, 2,2 km). Hochwasserrastplätze auf dem Vorland von Südfall (−817/−129, −763/98, −490/−132) und am Deich von Nordstrand (5138/−2177); die Ringelgans weidet auf Südfall (−800/−20). Bei Hochwasser sucht jede Gruppe einen Platz, an dem sie ganz auf dem Trockenen steht.
- **Wattwanderer** ist auch ihr Störer (Fluchtdistanz je Art); die Alpenstrandläufer wenden dann im Schwarm über dem Watt.
- **Bild** im Durchgang „Tiere“ (`flock/gl/WaderGl`), Licht wie die Seehunde.
- **Bedienfeld** LEBEN: „Wattvögel“ an/aus, „Schwarm aufscheuchen“ (die nächste Gruppe am Boden, Alpenstrandläufer zuerst), „Kamera zu den Vögeln“ (gegen den Wind 4 m vor den Rand der nächsten Gruppe, die frisst, Auge 0,7 m). HUD-Zeile „Wattvögel“.
- Abnahme ohne Fenster: `TIDE=13.5,13.553,13.557,13.561 SCHWARM=13.55 FOLGEN=45 ANSICHT=Voegel Tempo 3 1920 1080 docs/status/Schwarm.png MITTEL 13.5 2026-07-21 3`; mit der Flut zum Vorland: `TIDE=16.3,17.0,17.55,18.4 FOLGEN=25 SCHWARM=99 ANSICHT=Voegel Tempo … 16.3 …` (`SCHWARM=99` nur für die Zählung in der Beschriftung, `VOEGEL_LISTE` für jede Gruppe); `OHNE_VOEGEL`. Das Package allein: `WaderDemo --bild docs/status/Wattvoegel.png`.

## Die Seehunde (Phase 7)

- **Kolonie** aus `com.dan.seal` an der `scene.Seehundbank` (Rücken westlich von Südfall, Mitte −2980/200, 280 m [G]; bei Ebbe liegen die Tiere an seinem Rand zum Priel). Grund mit der Feinform (`groundHeight`), Wasser aus der Überflutung; Monat und Sonne aus der Uhr. Zahl nach der Stufe (Mittel 36 und Heuler im Juni und Juli). Springt die Uhr um mehr als 30 min, setzt sich die Kolonie gleich (`settleSeals`); steht die Uhr, leben die Tiere in Echtzeit weiter.
- **Wattwanderer** (`scene.Wattwanderer`): geht bei Ebbe auf dem längsten trockenen Weg (bis 900 m) auf die liegenden Tiere zu, bis 25 m vor sie oder vor das Wasser, und zurück; Störer mit Fluchtdistanz 300 m, Stiefelabdrücke alle 75 cm. Die Figur zeichnet `gl/FigurGl` (`figur.vert`, `figur.frag`), bis die Figuren mit Phase 9 ein Package bekommen.
- **Bild**: neuer Durchgang „Tiere“ nach dem Boden und vor dem Wasser (das Meer deckt zu, was taucht); Licht wie Halme und Boden, Schlagschatten nach Sonne und Wolken. **Spuren**: `mudflat.TrackMap` um die Kamera auf Einheit 15 des Geländes.
- **Bedienfeld** LEBEN: Seehunde und Spuren an/aus, Zahl, Fluchtdistanz, „Wanderer zur Bank“, „Wanderer zurück“, „Seehunde neu setzen“. HUD-Zeile „Seehunde“ in 2,5 km um die Bank. **Taste 5** stellt die Kamera 18 m vor die dichteste Stelle der Gruppe, Blick über die Tiere zum Wasser (ohne Tiere an Land der feste Ort).
- Abnahme ohne Fenster: `TIDE=10.4,11.3,12.05,12.09,12.14,12.9,16.3,16.75 STOERER=12.0 ANSICHT=Seehundbank Tempo 3 1920 1080 docs/status/Seehunde.png MITTEL 10.3 2026-07-21 3`; das Package allein: `SealDemo --bild docs/status/Haltungen.png`.

## Priele, Kanten, Strömung (Phase 6)

- **Gelände am Auge auf 2 m** (`TerrainLod` mit zehn Stufen; „Niedrig“ bleibt bei 8 m): darauf legt `terrain.vert` die Feinform aus `com.dan.mudflat` (`mudflat_relief.glsl`): Prielkanten mit Abbruch bis 3 km, Rinnsale und Wellenrücken bis 400 m. Kamera und Ansichten stehen auf demselben Grund (`Renderer.groundHeight`, `mudflat.Relief`). Im Pixelshader die Rinnsale je Bildpunkt: ihre Neigung in der Normale, bei Ebbe Wasser darin, das bergab läuft.
- **Rippel ohne Ringe**: die Feinheit aus den Ableitungen des Orts (Befund Status_Phase5); weniger Schill, Wattwurmhaufen heller mit dunklem Loch; flache Rippel, wo die Strömung schwach war.
- **Strömung im Meer**: die Strömung der Überflutung als RG16F (512² zu 32 m, nur wenn neu gerechnet) an `com.dan.ocean`: Kräuselung, die mit dem Wasser zieht, Schaumstreifen ab 0,4 m/s, Trübung vom Schwebstoff.
- **Kein Rautenmuster mehr** (Befund Status_Phase4): die beiden größten Kaskaden und die Dünung schwanken mit flachem Rauschen in der Stärke (`tileBreak`).
- **Flutsaum ohne Treppen**: am Rand der nassen Zellen sinkt der Spiegel mit Rauschen unter den Grund (`longSoft`).
- **Unter Wasser**: Ansicht 7 taucht 1,2 m über dem Grund des Priels an der Kante (bei Niedrigwasser noch 1,4 m tief); die Fläche von unten mit Snellschem Fenster, trübes Wasser mit 0,9/m (Sicht gut 3 m). Ansicht 4 „Am Priel“ steht jetzt an der steilsten Kante im Osten (5 m Abbruch auf 60 m in den BAW-Höhen).
- Bedienfeld WATT: „Feinform (Prielkanten, Rinnsale)“; HUD-Zeile „Watt“ mit Rippelstärke. `Tempo`: `OHNE_FEINFORM`.
- Abnahme ohne Fenster: `TIDE=10,11,12.5,14.5,16,17.5 BEDECKUNG=1 ANSICHT=AmPriel Tempo 3 1920 1080 docs/status/Priel.png MITTEL 13.5 2026-07-21 3`, `TIDE=17.5 ANSICHT=UnterWasser …`.

## Das Watt (Phase 5)

- **Material** aus `com.dan.mudflat`: ein `WetField` auf dem Gitter der Überflutung (1024² zu 16 m bei „Mittel“) hält je Zelle Nässe, Sediment, Richtung der letzten Strömung und Trockenzeit. Das Sediment kommt einmal aus der Lage (Laufzeit der Flut, Höhe zwischen MTnw und MThw, Hang): Sand seewärts und in den Rinnen, Schlick in den Buchten am Vorland.
- **Trocknen** im Faden der Tide nach jedem Schritt: unter Wasser nass, die Rippel richten sich quer zur Strömung aus; danach trocknet der Sand mit Sonne (Höhe zur Szenenzeit, Wolken und Dunst vom letzten Bild), Wind, Lufttemperatur und Regen aus der Regie. Nach einem Sprung in der Zeit schätzt die Überflutung die Trockenzeit aus der Tide der letzten 12,5 h.
- **Bild**: `terrain.frag` setzt `mudflat.glsl` ein (`#include "/com/dan/mudflat/gl/mudflat.glsl"`, Textur auf Einheit 14) und zeichnet das Watt mit dessen Albedo, Rippelnormale, Wasserfilm und Rauigkeit; der Film spiegelt den Himmel nach Fresnel und die Sonne, umso schärfer, je glatter.
- **Bedienfeld** WATT: Nässe und Sediment am Ort, Halbwertszeit des Trocknens, Rippel, Wattwurmhaufen je m², „Watt nass machen“. HUD: Zeile „Watt“, wenn das Auge über dem Watt steht. Verdrahtung: `Wiring.WattNaesse` ist `Wetness`.
- Abnahme ohne Fenster: `TIDE=10.5,11.5,12.5,14 BEDECKUNG=1 ANSICHT=AufdemWatt Tempo 3 1920 1080 docs/status/Watt.png MITTEL 13.5 2026-07-21 3`; das Material allein: `MudflatDemo --bild docs/status/Material.png`.

## Die Tide (Phase 4)

- **Pegel** aus `com.dan.tide`: zehn Partialtiden, angepasst an den Gezeitenkalender 2026 des BSH für Husum (Zeiten im Mittel 6 bis 11 min genau, MThw NHN +1,60 m, MTnw −1,90 m, mittlerer Hub 3,50 m). Am seeseitigen Rand des Rasters (Norderhever) 27 min früher, Hub wie Pellworm [G]; mit der Laufzeit bis Südfall (22 min) hat die Hallig ihr Hochwasser rund 5 min vor Husum. `Wiring.TidePegel` ersetzt den Platzhalter von Phase 1 und ist zugleich `Level`, `Water` und `Current`.
- **Windstau** aus dem Wind der Regie gegen die Küste (aus 290°): Sturm 1,5 m, Orkan aus WNW 3,4 m [G], Zeitkonstante 3 h.
- **Überflutung** im eigenen Faden (alle 100 ms): einmal beim Start aus dem Gelände je Zelle die Verbindungshöhe und die Laufzeit der Tidewelle (Rand: Watt unter MTnw am Rand des Rasters; Köge gesperrt), 2048² zu 8 m in rund 4 s, danach aus `daten/cache/tide_2048_2.bin` (nicht im Git). Gerechnet wird auf 1024² zu 16 m („Mittel“, „Hoch“), 512² („Niedrig“) oder 2048² („Ultra“): Wasser je Zelle mit Laufzeit, Pfützen (höchstens 0,15 m, versickern), Trockenzeit, Strömung.
- **Bild**: das Meer (`com.dan.ocean`) bekommt den Spiegel je Zelle als lange Welle über dem Pegel am Rand (Rückfluss: die Tiefe unter diesem Spiegel zählt, trockene Zellen zeichnen kein Wasser); das Gelände nimmt seine nasse Kante aus derselben Textur. Kamera und HUD lesen den Spiegel am Ort.
- **Bedienfeld** TIDE: Pegel mit Flut/Ebbe, nächstem Hoch- und Niedrigwasser, Spring und Nipp, Windstau; Wasser am Ort mit Strömung und Laufzeit; „→ Hochwasser“, „→ Niedrigwasser“; der Tidenkalender Husum des Tags; Pegel von Hand.
- Abnahme ohne Fenster: `TIDE=7.7,9,10,11,12.5,14,16.5,19 ANSICHT=Vogelauge Tempo 3 1920 1080 docs/status/Tide.png MITTEL 1.5 2026-07-28` (ein Bogen `_tag.png` über eine Flut).

## Himmel und Wetter (Phase 3)

- **Himmel** aus `com.dan.sky`: Streuung nach Sichtweite, Sonne über Süden, Mond, Planeten, Sterne des Nordhimmels; Rückstrahlung des Bodens 0,10 (nasses Watt, Wasser, Wiesen). Ohne das Package bleibt der Verlauf aus Phase 1.
- **Augenanpassung** wie Glacier: alle 0,2 s die Leuchtdichte im Blickfeld (zwölf Strahlen), mit der des Himmels gemittelt, über eine Sekunde geglättet, nur zu 85 % angepasst (die Nacht bleibt Nacht), Stäbchensehen in der Nacht. Neu: Strahlen, die auf trockenes Land fallen (Watt bei Ebbe, Deich, Koog), zählen dreifach (`LAND_GAIN`; Glacier: Eis zwölffach); unter Wolken folgt das Auge der Dunkelheit nur zum Teil (`STORM_ADAPT` 0,6), damit Gewitter und Orkan düster bleiben.
- **Wetterregie** aus `com.dan.weather` mit dem Klima „Nordfriesland“ (`Wiring.NORDFRIESLAND`, Monatsmittel der Luft bei Husum [G], Sommer Mitte Juli, Ortszeit Europe/Berlin) und den Lagen der Deutschen Bucht; neu im Package die Lage **Orkan aus Westnordwest** (um 33 m/s aus 295°, 965 hPa, 8/8, Regen, Sicht 3 km). Die Regie läuft beim Start ab „klar“; Block WETTER: Regie, „Regie weiter“, „Orkan aus WNW“, Wind, Richtung, Bedeckung, Regen, Seenebel, Sicht, Gewitter, Ebenen, Blitz in Blickrichtung.
- **Wolken** (`com.dan.cloud`) mit Schatten auf Gelände, Meer, Gras und Bodendecke; **Regen** (`com.dan.rain`) mit Ringen auf dem Wasser, nassem Gras; **Seenebel** (`com.dan.fog`) 15 bis 105 m über dem Pegel, er hebt sich mit der Sonne und reißt bei Wind über 6 bis 11 m/s auf; **Blitz** (`com.dan.lightning`) mit Einschlag auf dem Wasser im Watt und auf dem Boden der Köge.
- Das Gelände rechnet mit dem Licht aus `com.dan.sky` physikalisch (Albedo/π); das Raster der Marken ist nah am Auge aus und nie breiter als sein Bildpunkt (Befund Status_Phase2: das breite Band im streifenden Blick).
- Die Bodendecke ist jetzt der Deich (`Biome.deich()`, gut halb so dicht wie die Blumenwiese) auf 18 m bei „Mittel“ (Befund Status_Phase2: 8,3 ms auf dem Prozessor).
- Abnahme ohne Fenster: `TAG=4.5,6,9,13,17,20.5,21.75,23.5 WETTER=KLAR KAMERA=-700,300,235,4,35 Tempo 3 1920 1080 docs/status/Tag.png` (je Stunde ein Bild und ein Bogen `_tag.png`), `WETTER=NEBEL ANSICHT=Suedfall Tempo 3 1920 1080 docs/status/Nebel.png MITTEL 7.6 2026-09-22 2`, `WETTER=GEWITTER SICHT=12 BLITZ=1 ANSICHT=VomDeich Tempo 20 1920 1080 docs/status/Gewitter.png MITTEL 17.5`.

## Gelände heute (Phase 2)

`tools.GelaendeImport` liest den Zuschnitt der BAW-Küstenhöhen und schreibt `daten/gelaende`:

```
gdal_translate -projwin 472500 6044000 490000 6026500 -co COMPRESS=LZW topography_max_z_2015-2021_ger.tif templates/rasters_BAW/rungholt_topo_10m.tif
java -cp build/classes com.dan.rungholt.tools.GelaendeImport templates/rasters_BAW/rungholt_topo_10m.tif daten/gelaende docs/status/Gelaende_Import.png
```

- Raster 2048² zu 8 m (16,4 km) um den Ausschnitt F1, Höhen über NHN in 16 bit (−40 bis +25 m, 1 mm Stufe); jede Zelle nach UTM 32 umgerechnet und bilinear gelesen.
- Art je Zelle aus einer Flutung vom offenen Meer über alles unter NHN +3,5 m: Watt (unter MThw + 0,3 m), Vorland (Hallig, Salzwiese), Deich/Warft (ab +3,5 m), Koog (dahinter). Die Köge auf dem Festland und auf Nordstrand liegen nicht in den Daten; sie werden auf NHN +0,5 m [G] gefüllt.
- Ergebnis: NHN −30,4 bis +9,9 m, Warft von Südfall +5,5 m (im 8-m-Raster; 5,8 m in der höchsten 10-m-Zelle), 128 km² Watt zwischen MTnw und MThw im Raster.
- Fehlt `daten/gelaende`, zeigt die App die Bühne von Phase 1 als Raster (`Terrain.flat()`).

Das Meer ist `com.dan.ocean` mit dem neuen Preset `watt(n)`: auf dem Platzhalter-Pegel, das Gelände als Grund (Uferdämpfung, Trübung, Schaumsaum), die Köge über eine Maske trocken, über den Rand des Rasters hinaus offene See auf NHN −4 m (neu: `Light.bedOutside`). Gras auf Deich, Warft und Koog, Binsen in der Salzwiese (`com.dan.reed`), die Bodendecke zu Fuß (`com.dan.ground`, Wiese). Das Watt selbst ist bis Phase 5 eine Vorschau in `terrain.frag`: Sand und Schlick nach Höhe und Kante, feucht, nass und spiegelnd am Saum und in Pfützen.

## Die Bühne (Phase 1, Rückfall ohne Daten)

Bis das Gelände aus den Höhendaten kommt (Phase 2), steht die Szene auf einem Höhenfeld aus wenigen Formen [G], die Lage und Größe nach der Datenprüfung grob nachstellen: Watt um NHN −0,5 m, die Norderhever von Südwest nach Nordost bis NHN −18 m, ein Priel im Süden, Südfall auf NHN +1,8 m mit der Warft auf NHN +5,8 m (Lage aus den Höhendaten), Pellworm und Nordstrand als Köge hinter Deichen von NHN +8 m. `stage.frag` zeichnet dasselbe Feld wie `Buehne.height()`; die Prüfung vergleicht beide.

Das Meer steht auf einem Platzhalter-Pegel aus M2 und M4 mit Spring-Nipp-Gang (Hub rund 3,5 m wie am Pegel Husum [G]); im Block TIDE lässt er sich von Hand halten. Hinter den Deichen steht kein Meer. Am Wassersaum ist der Sand nass, dunkel und spiegelt – nur als Vorschau auf `mudflat`.

Ursprung der Szene ist die Kirche [G: 1 km östlich der Warft von Südfall], x nach Osten, Nord = −z, y = 0 ist NHN.

## Tasten

Maus: links ziehen drehen, rechts ziehen verschieben, Rad zoomen · W A S D Q E: fliegen (frei) · F frei, R kreisen, Pos1 Grundansicht · 1–9 Ansichten · Z Zeitreise Heute · 1362 · H HUD · Leertaste Uhr anhalten · F12 Foto · F5 Shader neu · F11 nur die Szene · F1 Übersicht.

## Prüfen

- `ant pruefung` (oder Run File auf `test/com/dan/rungholt/test/Pruefung.java`): endet mit „ERGEBNIS: alles in Ordnung“.
- `check-packages` läuft nach jedem Übersetzen und übersetzt jedes der 29 Element-Packages für sich.
- Standbild ohne Fenster: `java -cp build/classes;lib/* com.dan.rungholt.tools.Tempo 120 1920 1080 docs/status/Tempo.png MITTEL 13.5`.
- Wetter: `WeatherCheck` (mit dem Orkan), `GroundCheck` (mit dem Deich) im `demo/` der Packages; die Prüfung der App hat einen Abschnitt „Himmel und Wetter“.
