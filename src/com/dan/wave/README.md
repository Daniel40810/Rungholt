# com.dan.wave – eine Welle, die läuft

**Stand: Löser (Phase 8).** Eine Welle aus einem Einschlag läuft über ein Gewässer, läuft am Ufer auf, wird an einer Wand zurückgeworfen und klingt ab. Anders als die Ringwellen in `com.dan.ocean` kennt sie Tiefe und Ufer. Das Package kennt keine App und kein anderes Package. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/WaveDemo` zusätzlich `FStyle.jar`. Wiederverwendbar für einen Felssturz in den Edersee, einen Dammbruch, eine Welle im Noor oder im Hafen.

Zum Herauskopieren reicht `src/com/dan/wave/`.

## Was es rechnet

- **Flachwassergleichungen** auf einem Gitter nach Arakawa C: Wasserstand η in den Zellen, Durchfluss q = u·D auf den Kanten, in Flussform. Das Volumen bleibt also erhalten, bis auf Quellen und die Dämpfung am Rand. Ohne Mitführung (lange Wellen); die Tiefe an einer Kante zählt vom höheren Grund aus. Dadurch läuft Wasser nur über Land, wenn es höher steht als das Land, und Auflaufen und Trockenfallen brauchen keine Sonderfälle. Was aus einer Zelle mehr hinaus will, als drin ist, wird anteilig gekürzt.
- **Zwei Gitter:** grob (Vorgabe 8 m, 1 024², gut 8 km) und darin fein (2 m, 512²). Das feine bekommt am Rand genau den Durchfluss, mit dem das grobe gerechnet hat; das grobe bekommt unter dem feinen dessen Wasserstand zurück. Unter dem feinen hat das grobe den gemittelten Grund des feinen.
- **Zeitschritt** fest nach CFL (0,45), das feine Gitter in Unterschritten. Gerechnet wird nur in einem Fenster, in dem sich etwas bewegt; es wächst mit der Welle. Ein ruhiger See kostet nichts.
- **Reibung** am Grund nach Manning, halb implizit, nur im Flachen unter 30 m.
- **Dämpfung** in einem Saum von 24 Zellen am äußeren Rand: die Welle läuft hinaus, statt zurückzukommen.
- **Wände:** NaN als Tiefe (etwa eine Gletscherfront) oder Land, das höher ist als das Wasser.
- **Quellen:** `addVolume(x, z, m³[, Halbmesser])` als Hügel nach Gauß, auch negativ; `addVolumeOver(…, Sekunden)` verteilt ihn über die Zeit (ein Turm taucht nicht auf einmal ein).
- **Auflaufen:** höchster Stand je Zelle an Land (`runupAt`), Zeit des letzten Nasswerdens (`wetAgo`), Ereignis `onRunup(x, z, Höhe, t)` höchstens einmal je Sekunde und 25-m-Feld.
- **Pegel:** `gauge(name, x, z)` zeichnet alle 0,05 s den Wasserstand auf (die letzten 5 min); `series(sekunden)`, `max()`, `min()`.
- **Abfragen** aus jedem Faden: `heightAt` (0, wo trocken), `levelAt` (NaN, wo trocken), `slopeAt`, `velocityAt`, `depthAt`, `volume()`; `snapshot()` für die Grafik.

## Gehaltener Wasserstand (seit Rungholt Phase 12)

`hold(Hold)`: Zellen, für die `held(x, z)` gilt, stehen nach jedem Schritt des feinen Gitters auf `eta(t)` (über dem Ruhespiegel, nie unter dem Grund) – etwa das offene Meer vor einem Deichbruch, das nicht leerläuft, während das Wasser durch die Bresche stürzt. `heldCount()` zählt sie. `WaveCheck` prüft den Dammbruch nach Ritter: 2 m Meer, festgehalten, trockener Grund dahinter.

## Benutzung

```java
Wave w = Wave.lake();                                  // basin(): ein Gitter 256² zu 1 m, Wände ringsum
w.place(feinX, feinZ, grobX, grobZ).build((x, z) -> spiegel - gelaende(x, z));   // NaN: Wand
Wave.Gauge pegel = w.gauge("Steg", x, z);
w.onRunup = (x, z, h, t) -> nass(x, z);

w.addVolumeOver(x, z, 25_000, 15, 5);                  // ein Turm von 25 000 m³ taucht in 5 s ein
w.step(dt);                                            // im eigenen Faden, fester Schritt intern
double h = w.heightAt(x, z);

// zeichnen (im GL-Faden)
WaveGl wg = new WaveGl();
wg.update(gl, w);                                      // nur das Fenster, das sich geändert hat
int tex = wg.heightTexture(0); double[] r = wg.rect(0); // R16F, {x0, z0, Zelle, an}; 1 = grob
```

Die Höhentexturen tragen trockene Zellen als `Wave.DRY` (−30). Wer sie abtastet, gewichtet nur nasse Texel (wie `com.dan.ocean` seit Glacier Phase 8). Die Nässe (`wetTexture`) ist die Zeit des letzten Nasswerdens; das Alter rechnet der Shader mit `time()`.

## Prüfung (`demo/WaveCheck`)

| Prüfung | Ergebnis | Handrechnung |
|---|---|---|
| Scheitel nach 80 m bei 10 m Tiefe | 7,97 s | √(g·h): 8,08 s |
| Volumen nach 60 s mit Auflaufen in einer Schüssel | 1 500,0 von 1 500 m³ | erhalten |
| Einzelwelle 0,1 m auf 5 m, Strand 1:20 | 0,39 m | Synolakis 1987: 0,48 m |
| an einer senkrechten Wand | 0,100 m | doppelte Höhe 0,100 m |
| aus dem feinen ins grobe Gitter, 600 m bei 50 m | 27,0 s | 27,1 s |
| zurückgeworfen am Übergang | 4 % | |
| Volumen über beide Gitter nach 10 s | 4 000,0 von 4 000 m³ | erhalten |
| Dämpfung am Rand nach 90 s | unter 1 % | |
| 512² und 1 024² bei 100 m Tiefe | rund 290 ms Rechnung je Sekunde Welle (hier) | |

## Demo

`demo/WaveDemo.java` → Run File: ein Becken von 400 × 240 m, links eine Wand über 60 m Wasser, rechts ein Strand 1:12. Klick ins Wasser: ein Einschlag. Draufsicht, Schnitt längs der Mitte (zehnfach überhöht), Pegel am Strand. Ohne Fenster: `WaveDemo --bild datei.png [breite]`.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Wave` | Kern: Presets, zwei Gitter, Quellen, Schritt, Abfragen, Pegel, Stand für die Grafik |
| `Grid` | ein Gitter: Impuls, Kürzung, Masse, Dämpfung, Auflaufen (im Package) |
| `gl/WaveGl` | Höhe (R16F) und Nässe (R32F) je Gitter als Textur, nur das geänderte Fenster |
| `demo/WaveDemo` | Becken mit Wand und Strand in Java2D |
| `demo/WaveCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Grenzen

- Flachwasser ohne Dispersion: kurze Wellen laufen zu schnell, eine Welle bricht nicht (sie steilt sich an der Stirn auf, ohne zu überschlagen). Die kurze Windsee bleibt bei `ocean`; die App summiert beide (Schnittstelle `Sea`).
- Ohne Mitführung: kein Wirbel hinter einer Landspitze, keine Strömung, die die Welle versetzt.
- Ein Einschlag ist ein Volumen, kein Impuls: wie schnell ein Stück einschlägt, wirkt nur über die Zeit, in der es eintaucht.
- Im groben Gitter ist eine Welle unter rund 30 m Länge nicht mehr aufgelöst; Einschläge außerhalb des feinen Gitters brauchen einen Halbmesser von mindestens 16 m.
