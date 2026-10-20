# com.dan.figure – Menschen, die gehen

Figuren für Szenen im Freien: ein Mensch aus wenigen Teilen, der geht, steht, sich bückt und zeigt, mit Kleidung nach Monat und Rolle, und eine geführte Gruppe auf einem Weg. Seit Rungholt Phase 9 (die Wattwanderung nach Südfall). Der Kern braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/figure/`.

## Was es kann

- **Körper** (`FigureMesh`): 1,78 m [G], 15 Teile (Becken, Rumpf, Kopf mit Nase und Haar, Ober- und Unterschenkel, Füße, Ober- und Unterarme mit Händen, Rucksack, Mütze mit Krempe), nah 1 491 Dreiecke, fern 717. Gelenke an Hüfte, Knie, Knöchel (der Fuß bleibt waagerecht), Schulter, Ellbogen; der Kopf dreht sich zur Seite.
- **Haltung als vier Zahlen**: Schrittphase, Gang (0 Stehen … 1 Gehen), Bücken, Zeigen. `FigureMesh.pose` rechnet die Winkel, `figure.vert` rechnet dieselben Formeln: Hüfte schwingt ±0,42 rad, das Knie beugt sich im Schwung bis 1,05 rad, die Arme schwingen gegen die Beine, eine Senkung hält den tieferen Fuß auf dem Boden. Schrittlänge 0,76 m (bei 1,25 m/s 1,6 Schritte je Sekunde); in der Stütze wandert der Fuß genau eine Schrittlänge nach hinten, er gleitet nicht.
- **Kleidung** (`Outfit`): Farben je Fläche (Haut, Jacke, Hose, Knie, Unterschenkel, Fuß, Mütze, Haar, Rucksack, Unterarm), Mütze und Rucksack an oder aus. `forMonth`: im Sommer meist barfuß in kurzer oder hochgekrempelter Hose und Hemd, im Frühjahr und Herbst Regenjacke und Gummistiefel, im Winter Winterjacke, Mütze und Stiefel; der Führer in Gelb oder Rot mit Rucksack [G].
- **Gruppe** (`Hike`): ein Führer geht einen Linienzug mit 1,15 m/s (im Schlick bis 40 % langsamer), die Gruppe folgt locker in der Kolonne, Kinder schlendern zur Seite und laufen voraus. Halte (`Stop`): der Führer dreht sich um, bückt sich (zeigt einen Wattwurm) und zeigt in die Weite; die Gruppe steht im Halbkreis vor ihm. Ist der Letzte mehr als 45 m zurück, wartet er; steht vor ihm Wasser tiefer als `maxWade` (0,3 m), wartet die Gruppe, bis es fällt. Je Schritt eine Fußspur (`Steps`: Ort, Richtung, barfuß); die Personen sind Störer für Tiere (`count`, `at`, `centre`).
- **Welt** (`Hike.World`): Grund, Wassertiefe, Schlick am Ort.
- **Grafik** (`gl/FigureGl`): eine Instanz je Person (zwölf Werte), bis 16 Kleidungen als Farbtafel je Aufruf (mehr in Gruppen), nah und fern, Licht vorbelichtet wie die Flächen der App (Sonne, Mond, Himmel, Dunst, Schatten der Wolken als Zahl).

## Benutzung

```java
Hike h = new Hike(new double[][]{{0, 0}, {800, -40}, {1600, 40}}, 13, 7, 1);   // Weg, Personen, Juli, Startwert
h.stops.add(new Hike.Stop(380, 240, "Wattwurm"));
h.step(dt, welt, (x, z, dx, dz, barfuss, groesse) -> watt.addTrack(x, z, dx, dz, barfuss ? 3 : 4));
figureGl.draw(gl, h.people(), view, light);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Outfit` | Kleidung als Farben je Fläche, nach Monat und Rolle |
| `FigureMesh` | Netz des Menschen, Haltung (`pose`), Ort einer Ecke in einer Haltung (`transform`) |
| `Person` | Ort, Richtung, Haltung, Größe, Rolle |
| `Hike` | geführte Gruppe auf einem Weg mit Halten, Warten, Spuren, Störern |
| `gl/FigureGl`, `gl/figure.vert`, `gl/figure.frag` | Figuren zeichnen |
| `demo/DemoFlat` | ein Watt zum Probieren mit Schlickfeld und Priel |
| `demo/FigureDemo` | Fenster: eine Wanderung im Zeitraffer; `--bild datei.png` schreibt Kleidung im Juli und Januar, den Gang, Bücken und Zeigen, die Gruppe unterwegs und am Halt |
| `demo/FigureCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `FigureCheck`: Kleidung nach dem Monat, Netz (Größe, Teile, Dreiecke), Haltung (Füße auf dem Boden beim Stehen, Gehen, Bücken; Schrittlänge; der Standfuß gleitet nicht), eine Wanderung auf `DemoFlat` (Kolonne, Halbkreis an den Halten, Führer bückt sich und zeigt, Spuren, trockener Weg, ankommen), Warten vor dem vollen Priel, Wiederholbarkeit, Zeit (0,08 ms je Sekunde Szenenzeit für 14 Personen).

## Kleidung von 1362 (seit Rungholt Phase 11)

`Outfit.medieval(monat, zufall, kind)`: Kittel aus Wolle bis über das Knie in Naturbraun, Grau, Waid-Blau, Krapp-Rot oder Reseda-Gelb, Beinlinge, Bundschuhe aus Leder oder im Sommer barfuß, oft eine Gugel oder Leinenhaube; kein Rucksack. `FigureCheck` prüft sie.

## Seit Rungholt Phase 13

- `Outfit.archaeologist(month, r)`: Wathose über Gummistiefeln, Ölzeug oder Warnweste, Mütze; für die Grabung im Watt. Die Arbeit (knien, sieben, messen) setzt der Benutzer über Bücken und Zeigen.

## Grenzen

- Kein Gesicht, keine Finger, keine Kleidung, die weht; die Teile sind starre Röhren.
- Der Gang ist einer für alle (nur Größe und Tempo ändern ihn); kein Laufen, kein Rennen, kein Ausrutschen im Schlick.
- Die Gruppe weicht nur einander aus, nicht Hindernissen; der Weg muss frei sein.
