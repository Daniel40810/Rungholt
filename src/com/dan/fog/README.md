# com.dan.fog – Seenebel

Eine flache Nebelschicht über dem Wasser, die in Bänken über die See zieht, mit Licht von Sonne, Mond und Himmel, dazu Nebelschallsignale mit Kennung. Das Package braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/fog/`.

## Was es kann

- **Sicht nach Koschmieder:** Im Kern einer Bank ist der Auslöschungskoeffizient σ = 3,0 / V (5 % Kontrast). `visibilityAt(x, y, z)` gibt die waagerechte Sicht am Ort, `transmittance(a, b)` die Durchlässigkeit eines Wegs.
- **Bänke, die ziehen:** ein Rauschfeld in der Fläche (drei Oktaven Wertrauschen, leicht verwirbelt), das mit 80 % des Winds zieht. Der Anteil bedeckter Fläche (`coverage`) wird über eine Eichtabelle getroffen: 20/45/70 % gewollt ergeben 24/47/71 % gemessen.
- **Obergrenze:** im Mittel `top` Meter über dem Wasser, von Ort zu Ort ±25 %, weich auslaufend. Darüber ist klare Luft; ein Turm von 30 m ragt aus einem Nebel von 18 m heraus.
- **Untergrenze (seit Haithabu Phase 3):** `base` Meter über dem Wasser, darunter ist es klar, mit einem weichen Übergang von 2 · max(1,5 m, 0,3 · base). Damit hebt sich Strahlungsnebel am Morgen als Schicht vom Wasser ab, bevor er sich auflöst. 0 lässt alles wie bisher.
- **Schwaden:** Rauschen in der Dichte, das mit der Höhe versetzt ist.
- **Licht im Nebel:** Sonne und Mond mit Vorwärtsstreuung (Henyey-Greenstein, g = 0,75) und einem Anteil vielfach gestreuten Lichts, gedämpft durch den Nebel über dem Punkt; dazu der Himmel. Gegenlicht leuchtet, tief im Nebel wird es grau.
- **Streufeld für fremde Lichter:** `scatter(x, y, z)` in 1/m, damit ein Leuchtfeuer seinen Strahl im Nebel zeigen kann (Nebeltröpfchen streuen fast alles).
- **Nebelsignale:** `Signal` mit Kennung wie im Leuchtfeuerverzeichnis: `Horn Mo(R) 30s`, `Horn(2) 20s`, `Tyfon 120s`. Ein Signal tönt, solange die Sicht an seinem Ort unter 2 sm liegt, und meldet jeden Ton mit Dauer über `onHorn`.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)` (Seenebel bis Bft 3, Fetzen bis 5, ab 7 reißt ihn der Wind auf), dazu `haze()`, `banks()`, `dense()`, `shallow()`.
- **Reproduzierbar:** gleicher `seed`, gleiche Bänke. **Stufen:** `Quality.LOW` bis `ULTRA` regeln die Schritte des Blicks (8 bis 32) und die Karte der Bänke (256² bis 768²).

## Benutzung

```java
Fog fog = Fog.banks();
fog.visibilityM = 200;          // Sicht im Kern einer Bank
fog.coverage = 0.6;             // 60 % der Fläche
fog.top = 50;                   // Obergrenze über dem Wasser
fog.windSpeed = 3; fog.windFromDeg = 240;
fog.signals.add(new Signal("Roter Sand", "Horn Mo(R) 30s", 0, 28, 0));
fog.onHorn = (x, y, z, strength, seconds, s) -> playHorn(seconds);
fog.step(dt);                    // je Bild
double v = fog.visibilityAt(eyeX, eyeY, eyeZ);

// Grafik: Karte der Bänke, dann ein Bilddurchgang über die fertige Szene (Tiefe als Kopie)
fogGl.update(gl, fog.snapshot(), eye);
fogGl.draw(gl, fog.snapshot(), view, light);   // Mischung ONE, SRC_ALPHA
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Fog` | Kern: Eingänge, Presets, Dichte, Sicht, Durchlässigkeit, Eichung der Bedeckung, Ziehen, Signale |
| `Signal` | Kennung lesen, Takt der Töne, tönt es gerade |
| `gl/FogGl` | Karte der Bänke (RG16F), Blick durch den Nebel als Bilddurchgang, Selbsttest |
| `gl/fog_common.glsl` | das Feld wie `Fog.density` (gleicher Ganzzahl-Hash) |
| `gl/fog.frag`, `fog_map.frag`, `check.frag` | Blick, Karte, Selbsttest |
| `demo/FogDemo` | Fenster mit Reglern; `--bild datei.png [breite]` schreibt sechs Lagen und den Takt dreier Signale |
| `demo/FogScene` | eigene Szene ohne Dreiecke: See, Turm, Pfähle bis 2 km |
| `demo/FogCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.fog.demo.FogDemo` zeigt See, Turm und Pfähle im Nebel. Die Pfähle stehen in 60 m bis 2 km; daran sieht man die Sicht. Am Turm tönt `Horn Mo(R) 30s`.
- `com.dan.fog.demo.FogCheck` prüft Koschmieder, Obergrenze, Eichung, weiche Ränder, das Ziehen, Presets, seed, die Kennungen und dass Signale nur im Nebel tönen.
- `FogGl.selfCheck` rechnet die Dichte an 64 Orten auf der Grafikkarte und vergleicht mit dem Prozessor.

## Grenzen

- Der Nebel ist eine Schicht über einer ebenen See; Gelände kennt er nicht. Über Land liegt er in derselben Höhe über dem Wasserspiegel (im Tal dicht, auf Kuppen klar), sammelt sich aber nicht in Senken.
- Licht von Lampen im Nebel rechnet das Package nicht selbst. Es gibt sein Streufeld her, ein Leuchtfeuer muss damit seinen Strahl zeichnen.
- Vielfachstreuung ist genähert (fester Anteil, gedämpftes Himmelslicht), nicht gerechnet.
- Die Tonlängen der Signale (Punkt 1 s, Strich 3 s, Gruppe 2 s, Tyfon 5 s) sind gestaltet; Periode und Muster kommen aus der Kennung. Den Klang macht ein anderes Package.
