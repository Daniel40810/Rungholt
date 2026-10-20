# com.dan.cloud – Wolken

Das Paket rechnet Wolken über dem Meer: Schönwetter-Cumulus bis zum Gewitterturm mit Amboss, Schichtwolken bis zur geschlossenen grauen Decke, darüber ein hoher Schleier. Die Bedeckung stellt man in Achteln ein, wie sie ein Beobachter von unten sieht; die Wolken ziehen mit dem Wind und wandeln langsam ihre Form. Dazu kommen der Schatten auf dem Wasser und auf der Szene, ein Regenmaß unter dichten Stellen und ein Faktor für die Augenanpassung unter bedecktem Himmel.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet die Wolken mit JOGL (OpenGL 4.3, ohne Compute-Shader) als Raymarching in halber oder viertel Auflösung. Das Paket kennt weder den Leuchtturm noch andere Pakete, auch nicht `com.dan.sky`: das Licht kommt als Zahlen herein. Zum Herauskopieren reicht `src/com/dan/cloud/`; nur `demo/CloudDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Arten:** Cumulus, Stratocumulus, Stratus, Nimbostratus, Cumulonimbus (`CloudType`) mit Basis, Dicke, Türmen, Zellgröße und Dichte nach dem Wolkenatlas der WMO, Basis über See eher niedrig.
- **Form:** Wetterkarte (512², Zellen in Gruppen mit Lücken, große Flecken für Schichten), Grundform aus Perlin-Worley (128³), Fransen aus Worley (32³), nach Schneider (Horizon Zero Dawn, 2015). Haufen haben eine flache Basis und werden nach oben schmaler; Gewittertürme bleiben bis oben dicht, der Amboss sitzt nur über starken Türmen und reicht eine Drittelzelle darüber hinaus.
- **Bedeckung in Achteln:** Eine Eichtabelle (`Clouds.areaFraction`) setzt die Achtel in den Flächenanteil der Karte um, gemessen mit `observedCoverage` an 256 Orten (Richtungen bis 70° vom Zenit, optische Dicke ab 1). Die Prüfung misst an anderen Orten nach: alle Arten auf ±0,15 Achtel.
- **Licht:** Henyey-Greenstein mit zwei Keulen (Silberrand vorn, helle Seite hinten), Mehrfachstreuung in vier Oktaven (Wrenninge 2015), ab der zweiten mit durchmischter Farbe (halb Sonne, halb Himmel), Pulvereffekt an den Rändern, Himmelslicht von oben und Meerlicht von unten. Das Licht des Gestirns kommt für acht Höhen (0 bis 14 km) herein, so wird die Kuppe eines Turms abends noch rot, wenn die Basis schon im Erdschatten liegt. Nachts ist der Mond das Licht.
- **Dunst:** Wolken verblassen mit der Entfernung (Dunst mit 1,2 km Skalenhöhe wie in `com.dan.sky`); eine geschlossene Decke bleibt bis zur Kimm grau, statt durchsichtig zu werden.
- **Zug:** Drift mit dem Wind, Formwandel über die Zeit (`evolve`). `step(dt)` im Lauf, `setTime(t)` für Standbilder; beide ergeben denselben Stand.
- **Schatten:** Schattenkarte 256² über 24 km um das Auge, auf Texel gerastet (kein Flimmern beim Fahren). `Clouds.shadow` rechnet denselben Wert auf dem Prozessor (Licht am Turm, Prüfungen).
- **Hohe Schicht:** dünner Schleier in 6,5 km mit Fasern, eigene Bedeckung 0..1.
- **Stufen:** `FLAT` (2.5D, eine Fläche), `MEDIUM` (1/4 Auflösung, bis 72 Schritte), `HIGH` (1/2, bis 96), `ULTRA` (1/2, bis 128). Die Schrittweite wächst mit der Entfernung, leere Luft wird mit größeren Schritten übersprungen; zitternde Startpunkte und die Wiederverwendung des letzten Bilds glätten das Rauschen.

## Benutzung

```java
Clouds clouds = Clouds.fair();             // 3/8 Cumulus; overcast(), storm()
clouds.oktas = 5;
clouds.type = CloudType.STRATOCUMULUS;
clouds.highCover = 0.3;
clouds.windSpeed = 12;                     // in Wolkenhöhe
clouds.windFromDeg = 260;
clouds.quality = Clouds.Quality.MEDIUM;

// je Bild
clouds.step(dt);
Clouds.Snapshot s = clouds.snapshot();
double licht = Clouds.shadow(s, x, z, richtungZurSonne);   // 0..1 an einem Ort auf Meereshöhe

// zeichnen (im GL-Faden)
CloudGl gl = new CloudGl();
gl.init(gl4);
CloudGl.Light l = new CloudGl.Light();     // vorbelichtet: Richtung, Farbe in 8 Höhen, Himmel, Meer, Dunst
gl.update(gl4, s, l, auge);                // Schattenkarte
gl.draw(gl4, s, l, view);                  // nach dem Himmel, vor Meer und Szene, ins gebundene HDR-Ziel
gl.drawPanorama(gl4, s, l, auge);          // ins gebundene Rundumbild (für Spiegelungen)
int schatten = gl.shadowTexture();         // R16F, Rechteck aus gl.shadowRect()
```

**Vorbelichtung:** Wie bei `com.dan.sky` kommen alle Lichtwerte mit der Belichtung multipliziert herein. `brightnessFactor()` sagt, wie viel dunkler ein bedeckter Himmel ist (für die Augenanpassung).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `CloudType` | Arten mit ihren Maßen |
| `Noise` | Rauschtexturen (Prozessor, reproduzierbar, kachelbar) |
| `Clouds` | Eingänge, Eichung, Dichte, Schatten, Regen, Bedeckung von unten |
| `gl/CloudGl` | Texturen, Schattenkarte, Raymarching, Wiederverwendung, Auflegen, Rundumbild, Selbsttest |
| `gl/cloud_common.glsl` | Dichte Zeile für Zeile wie `Clouds.density` |

## Demo und Selbsttest

- `com.dan.cloud.demo.CloudDemo` zeigt die Wolken über einer glatten See mit einem schlichten eigenen Himmel. Regler für Bedeckung, Art, hohe Schicht, Wind, Sonnenhöhe und -richtung, Sicht und Stufe, Zeitraffer, „0 → 8/8“ (die Bedeckung läuft langsam hin und her) und vier Voreinstellungen. Unten steht die gemessene Zeit der Wolken auf der Grafikkarte.
- Ohne Fenster rechnet `CloudDemo --bild datei.png [breite]` zwei Reihen (Cumulus 0/8 bis 8/8; die fünf Arten und einen Abend) und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.cloud.demo.CloudCheck` prüft Rauschen (Prüfsummen, kachelbar), die Bedeckung von unten für alle Arten, Höhen und Amboss, den Zug mit dem Wind, Schatten, Regen, Helligkeit und die Uniforms; Ende „Alles in Ordnung.“.
- `CloudGl.selfCheck` liest die Schattenkarte zurück und vergleicht 64 Punkte mit dem Prozessor (Median und Ausreißer an Wolkenrändern).

## Grenzen

- Eine Schicht zur Zeit (plus der hohe Schleier). Cumulus unter einer Altostratus-Decke oder zwei Stockwerke Stratocumulus gibt es nicht.
- Kein Licht zwischen Wolken (Lichtstrahlen, „God rays“).
- Blitzlicht (seit Phase 10, `Light.flashPos`, `flashRgb`, `flashCount`, `flashDiffusion`) wird beim Auflegen am mittleren Ort der Wolke im Blick gerechnet: Licht, das durch die Wolke wandert (Diffusion), und Licht von unten. Es zieht nicht nach (ein Blitz dauert nur wenige Bilder), wirft aber keine Schatten in der Wolke.
- Regen fällt noch nicht sichtbar; `Clouds.rain` liefert das Maß für Phase 7.
- Die Schattenkarte deckt 24 km; dahinter gilt der Rand.

## Seit Glacier Phase 3: Lentikularis

`Clouds.lens` (0..1) und `Clouds.lensFromDeg`: Altocumulus lenticularis im Lee eines Gebirges als bis zu drei
Linsenlagen ab 4,6 km (Abstand 320 m, Stapel). Längs des Winds liegen die Linsen auf den Kämmen einer stehenden
Leewelle (Wellenlänge 9 km), quer dazu als Mandeln aus einem ganzzahligen Rauschen je Kamm (bitgleich in Java und
GLSL). Die Linsen ziehen nicht mit dem Wind. `Clouds.lensTau` gibt die optische Dicke ohne Grafikkarte; Schatten
(`shadow`, `shadow.frag`), Hauptbild und Rundumbild (`clouds.frag`) rechnen sie mit. Die Prüfung misst Anteil,
Stapel, Ortsfestigkeit und die Streckung quer zum Wind. Rückfluss in Phase 16.
