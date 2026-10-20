# com.dan.rain – Regen

Das Paket lässt es regnen: Streifen fallender Tropfen um das Auge, Regenvorhänge, die als Schauerwände über die See ziehen, Ringe auf dem Wasser und Krönchen, wo große Tropfen aufschlagen. Es rechnet mit echten Größen: Tropfengrößen nach Marshall und Palmer, Fallgeschwindigkeit nach Atlas, Sichtweite im Regen. Wo es regnet, sagt ein `Curtain`, meist die Wolken. Jeder gezeichnete Tropfen ist eine Formel aus Nummer, Zeit und Wind. Dieselbe Formel steht im Shader, darum zeichnet die Grafikkarte Zehntausende Tropfen, ohne dass einer gespeichert oder zurückgelesen wird.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3, ohne Eckpuffer) und rechnet die Ringkarte. Das Paket kennt weder Wolken noch Meer noch andere Pakete: Die Ringe gibt es als Textur heraus, die ein Wasser-Shader auf seine Normale legt. Zum Herauskopieren reicht `src/com/dan/rain/`; nur `demo/RainDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Menge:** `rate` in mm/h. Niesel liegt bei 0,3, leichter Regen bei 1–2, mäßiger bei 2–8, starker bei 8–30, darüber beginnt der Wolkenbruch. Die Größen folgen `N(D) = 8000 · e^(−Λ D)` m⁻³ mm⁻¹ mit `Λ = 4,1 · R^(−0,21)`; daraus ergibt sich die Regenmenge zur Probe auf knapp 20 % genau. Die Fallgeschwindigkeit ist `v(D) = 9,65 − 10,3 · e^(−0,6 D)` m/s, ein Tropfen von 2 mm fällt also mit 6,5 m/s.
- **Streifen:** Gezeichnet werden Tropfen von 0,5 bis 6 mm in einem Kasten um das Auge (±10 bis ±16 m je Stufe). Der Kasten ist fest mit der Welt verbunden: Ein Tropfen, der ihn verlässt, kommt auf der anderen Seite wieder herein, und bewegt sich die Kamera, bleiben die Tropfen stehen. Jeder Tropfen fällt mit seiner Fallgeschwindigkeit und treibt mit dem Wind samt Böen. Er ist so lang, wie er in 1/30 s fällt, und mindestens einen Bildpunkt breit; wie viel vom Bildpunkt er wirklich deckt, bestimmt seine Deckkraft. Mehr Regen heißt mehr Streifen: bei 1 mm/h ein Sechstel, ab 40 mm/h alle.
- **Vorhänge:** Ein Bilddurchgang läuft vom Rand des Kastens bis zur Szene, höchstens 8 km weit, bis zur Wolkenuntergrenze. Regen löscht aus mit `0,25 · R^0,63` je km. Daraus folgt die Sicht: 16 km bei 1 mm/h, 5,7 km bei 5 mm/h, 1,2 km bei 60 mm/h. Dazu streut er Licht von Himmel und Sonne; im Gegenlicht leuchtet ein Schauer auf. Senkrechte Schleier, die mit dem Wind ziehen, geben den Vorhängen Struktur.
- **Wo es regnet:** Ein `Curtain` sagt für jeden Ort 0 bis 1. Der Kern legt daraus alle halbe Sekunde eine Karte von 96 × 96 Zellen über 16 km um das Auge an. Tropfen, Vorhänge und Krönchen lesen diese Karte. Ohne Curtain regnet es überall gleich (Landregen).
- **Ringe:** Die Ringkarte ist eine kachelbare Steigungstextur über 3 m (RG16F mit Mipmaps). Jeder Einschlag erzeugt einen Ring, der mit 0,3 m/s nach außen läuft und in gut einer Sekunde abklingt. Wie viele Ringe es gibt, folgt aus der Zahl der Einschläge je m² und Sekunde: bei 5 mm/h rund 730 Tropfen über 1 mm.
- **Krönchen:** In Zellen von 0,6 m um das Auge springen Spritzer im Kranz hoch, in der Mitte ein Säulchen. Die Höhe der Fläche kommt aus der Tiefe der Szene, so landen Krönchen auf Wellen, Platten und Steinen gleichermaßen.
- **Ausgänge:** `rateAt(x, z)` (Verwitterung, Scheiben), `expectedHits(...)` und `sampleDrop(...)` (Tropfen auf einer schrägen Fläche; die Rückseite bleibt trocken), `scatter(x, y, z)` (Streudichte für einen sichtbaren Lichtstrahl), `loudness()` (für den Ton).
- **Reproduzierbar:** gleicher `seed`, gleicher Regen. Der Zufall ist ein Ganzzahl-Hash ohne Vorzeichen; Prozessor und Grafikkarte rechnen bitgleich, im Abgleich auf ± 0,0 mm.
- **Stufen:** `LOW`, `MEDIUM`, `HIGH`, `ULTRA` wählen die Zahl der Tropfen (6 000 bis 60 000), die Größe des Kastens, die Auflösung der Ringkarte (128² bis 512²), die Schritte im Vorhang (16 bis 48) und die Zahl der Krönchen.

## Benutzung

```java
Rain r = Rain.moderate();                  // 5 mm/h; drizzle(), light(), heavy(), cloudburst()
                                           // calm(), breeze(), gale(), storm(), beaufort(n): Wind und eine passende Menge
r.curtain = (x, z) -> wolken.regen(x, z);  // 0..1, null = überall gleich
r.cloudBase = 700;

// je Bild
r.windSpeed = 12; r.windFromDeg = 240; r.gust = 0.3;
r.eyeX = auge[0]; r.eyeY = auge[1]; r.eyeZ = auge[2];
r.waterLevel = gezeit;
r.step(dtSzene);
wand.regen(r.rateAt(0, 0));                // mm/h am Turm

// vor dem Wasser: Karte hochladen, Ringe rechnen
RainGl gl = new RainGl();
gl.init(gl4);
gl.update(gl4, r.snapshot());
wasser.feineStoerung(gl.rippleTexture(), gl.rippleTile(), gl.rippleStrength());

// nach der Szene, ins HDR-Ziel, mit einer Kopie der Szenentiefe
RainGl.View v = new RainGl.View();         // viewProj, invViewProj, eye, forward, reversedZ, near, far, fovyDeg, depthTexture
RainGl.Light l = new RainGl.Light();       // Sonne, Mond, Himmel oben und am Horizont, Dunst (vorbelichtet)
gl.draw(gl4, v, l, 1.0);
```

Die Ausgabe ist vormultipliziert (`ONE, ONE_MINUS_SRC_ALPHA`), linear und vorbelichtet wie der Rest der Szene. Die Ringkarte nimmt zum Beispiel `com.dan.ocean` über `OceanGl.Light.detailTexture`, ohne den Regen zu kennen.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Rain` | Eingänge, Physik (Verteilung, Fallgeschwindigkeit, Einschläge, Sicht), Böen und Drift, Karte der Vorhänge, Ausgänge, Voreinstellungen, Stufen |
| `Rain.Snapshot` | unveränderlicher Zustand; `drop(i)` rechnet einen Tropfen Zeile für Zeile wie der Shader |
| `gl/RainGl` | Karte als Textur, Ringkarte, Vorhänge, Streifen, Krönchen, Selbsttest |
| `gl/rain_common.glsl` | der Tropfen aus Nummer, Zeit und Wind, wie `Snapshot.drop` |
| `gl/streak.vert`, `gl/streak.frag` | Streifen (und Spritzer): Breite mindestens ein Bildpunkt, Licht im Tropfen, weiche Ränder |
| `gl/curtain.frag` | Vorhänge: Auslöschung und Streuung durch die Karte bis zur Wolkenuntergrenze |
| `gl/crown.vert` | Krönchen: Zellen um das Auge, Fläche aus der Szenentiefe |
| `gl/ripple.frag`, `gl/slope.frag` | Ringkarte: Höhe aus Einschlägen, dann die Steigung |
| `gl/check.vert`, `gl/check.frag` | Selbsttest: 4 096 Tropfen auf der Grafikkarte |

## Demo und Selbsttest

- `com.dan.rain.demo.RainDemo` zeigt Wasser mit drei Pfählen und einem Steg. Es gibt Regler für die Menge (logarithmisch, 0,1 bis 100 mm/h), Landregen oder Schauer, Wind, Deckkraft, Sonnenhöhe und -richtung, Zeitlupe, Zeitraffer und Stufe. Ziehen dreht die Ansicht, das Rad zoomt. Unten steht die gemessene Zeit des Regens auf der Grafikkarte.
- Ohne Fenster rechnet `RainDemo --bild datei.png [breite]` oben Niesel bis Wolkenbruch nebeneinander. Darunter zieht ein Schauer heran, über die See und weiter. Dabei vergleicht es Grafikkarte und Prozessor.
- `com.dan.rain.demo.RainCheck` prüft ohne Fenster und ohne Grafikkarte, mit 19 Prüfungen; Ende „Alles in Ordnung.“:
  - Regenmenge aus der Verteilung, Fallgeschwindigkeit gegen Gunn und Kinzer, Tropfendichte, Sicht, Einschläge
  - Tropfenkasten, Anteil lebender Tropfen, Größenverteilung
  - Festigkeit in der Welt, Fall und Drift
  - Karte der Vorhänge, Streudichte, Treffer auf Flächen
  - Voreinstellungen, Stufen, Reproduzierbarkeit
- `RainGl.selfCheck` rechnet 4 096 Tropfen auf der Grafikkarte und vergleicht ihre Lage mit `Snapshot.drop`.

## Grenzen

- Kleine Tropfen unter 0,5 mm werden nicht als Streifen gezeichnet. Sie stecken im Vorhang (Auslöschung und Streuung) und in der Deckkraft der großen.
- Tropfen fallen durch Dächer und Wände; nur die Tiefe der Szene verdeckt sie. Ausnahme sind bis zu vier runde Unterstände (`shelter`: x, z, Halbmesser, Oberkante), unter denen weder Streifen noch Krönchen erscheinen, etwa eine Laterne unter ihrer Kuppel.
- Die Vorhänge reichen höchstens 8 km weit (die halbe Karte); dahinter übernimmt der Dunst der Szene. Die Wolkenuntergrenze ist eine Ebene.
- Ringe sind eine Kachel von 3 m und wiederholen sich; sie klingen über rund 60 m aus. Krönchen sind kleine Spritzer ohne eigenen Schatten.
- Tropfen auf Scheiben sind Sache von `com.dan.wetglass` (Phase 8), der Lichtstrahl im Regen von `com.dan.beacon` (Phase 9).
