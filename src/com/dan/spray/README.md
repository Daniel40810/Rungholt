# com.dan.spray – Gischt und Brandung

Das Paket lässt Wellen an Körpern hochschlagen: Spritzwasser, das an der Wand aufsteigt und zurückfällt, Sprühnebel, der im Wind wegzieht, und Gischtfahnen, die der Sturm von den Kämmen reißt. Es tastet die Wände der Körper gegen ein Meer ab, das es nur über eine kleine Schnittstelle kennt, erkennt anlaufende Kämme und wirft daraus Bündel. Der Kern führt nur die Bündel; jeder einzelne Tropfen ist eine Formel aus Bündel, Nummer und Alter. Dieselbe Formel steht Zeile für Zeile im Shader, so zeichnet die Grafikkarte Tausende Tropfen, ohne dass sie gespeichert oder zurückgelesen werden.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet die Tropfen mit JOGL (GL 4.3, ein Speicherpuffer, kein Eckpuffer). Das Paket kennt weder den Leuchtturm noch das Meer der App noch andere Pakete; was nass wird, wo Ringwellen entstehen und was rauscht, meldet es über Ereignisse. Zum Herauskopieren reicht `src/com/dan/spray/`; nur `demo/SprayDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Körper:** Kegelstümpfe (`Body`: Mitte, Unter- und Oberkante, zwei Halbmesser), beliebig viele. Ein Zylinder, eine Mole aus mehreren Pfählen, ein Fels aus gestaffelten Stümpfen.
- **Anschlag:** 20-mal je Sekunde wird jede Wand an 6 Punkten je 10 m Umfang gegen das Meer abgetastet. Steht ein Kamm an der Wand und läuft er auf sie zu, steigt ein Schwall. Wie hoch, hängt von H<sub>s</sub>, vom Kamm und vom Anlauf ab; geeicht auf die Beaufort-Tabelle (im Mittel Bft 5 um 3 m, Bft 7 um 7 m, Bft 11 um 27 m über Mittelwasser; bei Bft 9 jeder zehnte bis 17 m).
- **Spritzwasser:** ein Schwall aus einigen hundert Tropfen, die nacheinander abgehen, verschieden schwer sind und an der Wand entlanggleiten statt durch sie hindurch. Gezeichnet als Streifen entlang der Bewegung (Bewegungsunschärfe).
- **Sprühnebel:** ein bis zwei weiche Wolken je Schwall, die wachsen, im Wind treiben und langsam verblassen. Licht von hinten (Gegenlicht) leuchtet sie auf.
- **Gischtfahnen:** ab Wind 13,9 m/s (Bft 7) reißt der Wind Fahnen von den Kämmen im Umkreis `focusRadius`.
- **Weißwasser:** ab schwerer See (H<sub>s</sub> ab 6,5 m, Bft 9) zerschellt ein großer Schwall am Fuß zu drei bis fünf dichten, hellen Ballen. Sie breiten sich aus, ziehen mit dem Wind ab und zerfallen in drei bis sieben Sekunden. Das ist die Walze, die auf Sturmbildern den Fuß eines Turms verdeckt.
- **Bewegung:** Schwerkraft und eine lineare Luftreibung, die jeden Tropfen an den Wind angleicht (k je Sekunde, kleine Tropfen stärker). Es gibt eine geschlossene Lösung, also kein Schrittverfahren:<br>`x(t) = x0 + u·t + (v0 − u)(1 − e^(−k t))/k` mit `u = Wind + g/k`.
- **Ereignisse:** `onHit` (Wasser trifft eine Wand: Ort, Menge, Tempo), `onReentry` (ein Schwall fällt zurück ins Meer), `onSound` (Schlag an der Wand). Dazu `scatter(x, y, z)`: wie dicht die Luft gerade an diesem Ort mit Wasser gefüllt ist, für Licht und Sicht.
- **Reproduzierbar:** gleicher `seed`, gleiche Gischt. Der Zufall der Tropfen ist ein Ganzzahl-Hash ohne Vorzeichen, Prozessor und Grafikkarte rechnen bitgleich (Abgleich ± 0,0 mm).
- **Stufen:** `LOW`, `MEDIUM`, `HIGH`, `ULTRA` wählen die Zahl der Tropfen je Schwall (160 bis 1 500), je Nebel, je Fahne und je Ballen Weißwasser und die Höchstzahl der Bündel in der Luft (96 bis 384). Wenn es eng wird, fallen zuerst die ältesten Fahnen weg.

## Benutzung

```java
Spray s = Spray.gale();                    // Bft 8; calm(), breeze(), storm(), beaufort(n)
s.addBody(Body.cylinder(0, 0, 5.5, -5, 9)); // Mitte x, z, Halbmesser, Unter- und Oberkante
s.sea = new Sea() {                        // das eigene Meer, nur drei Fragen
    public double height(double x, double z) { return meer.hoehe(x, z); }
    public double[] velocity(double x, double z) { return meer.tempo(x, z); }
    public double level() { return wasserstand; }
};
s.onHit = (x, y, z, menge, tempo, art) -> wand.nass(x, y, z, menge);
s.onReentry = (x, z, staerke) -> meer.ringwelle(x, z, staerke);

// je Bild
s.windSpeed = 17; s.windFromDeg = 240; s.waveHeight = 5.5;
s.step(dtSzene);

// zeichnen (im GL-Faden, nach dem Undurchsichtigen, in einen HDR-Puffer)
SprayGl gl = new SprayGl();
gl.init(gl4);
gl.update(gl4, s);                         // Bündel in den Speicherpuffer
SprayGl.View v = new SprayGl.View();       // viewProj, eye, forward, reversedZ, near, far, Tiefenkopie
SprayGl.Light l = new SprayGl.Light();     // Sonne, Mond, Himmel oben und am Horizont, Dunst
gl.draw(gl4, v, l, s.strength);
```

`View.depthTexture` ist eine Kopie der Tiefe der Szene; damit bekommen Tropfen und Nebel weiche Ränder, wo sie in Wasser oder Wand stecken. Die Ausgabe ist vormultipliziert (`ONE, ONE_MINUS_SRC_ALPHA`), die Farben sind linear und vorbelichtet wie der Rest der Szene. `View.lodPixels` (seit Phase 13, Vorgabe 110) dünnt Nebel- und Weißwasserbälle aus, die im Bild größer als so viele Bildpunkte sind: gezeichnet wird ein Ball mit Wahrscheinlichkeit (lodPixels / Durchmesser)², Nebel höchstens auf ein Achtel, Weißwasser höchstens auf die Hälfte (es deckt schon bis 45 %), und er deckt dafür entsprechend mehr; die Schwelle für unsichtbare Ränder gilt vor dem Ausdünnen. Die mittlere Trübung bleibt (gemessen: Helligkeit vor dem Turm bei Bft 10 aus 60 m 181,3 gegen 181,4, Streuung 33,0 gegen 32,0), die Gischt kostet dort gut die Hälfte; 0 schaltet es ab.

### Aufschlag (Phase 7)

`s.impact(x, h, z, volumen, tempo)` – ein Stück schlägt ins Wasser, ohne dass es als Körper in der Gischt steht: ein Ring von Spritzern um die Stelle, die Strahlhöhe wächst mit dem Tempo und schwach mit dem Volumen (höchstens 60 m). `impacts()` zählt sie.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Spray` | Eingänge, Abtastung der Wände, Schwälle, Fahnen, Ereignisse, Voreinstellungen, Stufen |
| `Parcel` | ein Bündel und seine Tropfen (`particle(i, t)`), Zeile für Zeile wie der Shader |
| `Body` | Kegelstumpf, Abstand eines Punkts zur Wand |
| `Sea` | was das Paket vom Meer wissen muss: Höhe, Tempo, Wasserstand |
| `gl/SprayGl` | Speicherpuffer, Zeichnen in vier Schichten (Nebel, Weißwasser, Spritzwasser, Fahnen), Selbsttest |
| `gl/spray_common.glsl` | der Tropfen aus dem Bündel, wie `Parcel.particle` |
| `gl/spray.vert`, `gl/spray.frag` | Tafeln, Streifen, Gleiten an Wänden, Licht, Gegenlicht, weiche Ränder |
| `gl/check.vert`, `gl/check.frag` | Selbsttest: 64 Bündel × 64 Tropfen auf der Grafikkarte |

## Demo und Selbsttest

- `com.dan.spray.demo.SprayDemo` zeigt ein Meer aus zwölf Wellenzügen (`SineSea`, Pierson-Moskowitz) mit drei Körpern zur Wahl: Zylinder, Mole, Fels. Regler für Beaufort, Stärke, Sonnenhöhe und -richtung, Zeitlupe und Stufe; „Brecher“ wirft einen Schwall von Hand. Ziehen dreht, das Rad zoomt. Unten die gemessene Zeit der Gischt auf der Grafikkarte.
- Ohne Fenster rechnet `SprayDemo --bild datei.png [breite]` die drei Körper bei Bft 5, 8 und 11 und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.spray.demo.SprayCheck` prüft die Bewegungsformel gegen ein feines Schrittverfahren, Lebensdauer, Grenzgeschwindigkeit, Wurfhöhe, Zufall der Tropfen, Eichung der Schwallhöhen je Beaufort, Treffer, Wiedereintritt und Klang, Gischtfahnen und Grenzen der Bündel, Streudichte, Weißwasser, Voreinstellungen und Reproduzierbarkeit; Ende „Alles in Ordnung.“.
- `SprayGl.selfCheck` rechnet bis zu 4 096 Tropfen (64 Bündel × 64) auf der Grafikkarte und vergleicht Ort und Größe mit `Parcel.particle`.

## Grenzen

- Keine echte Strömungsrechnung: Der Schwall ist ein Wurf mit Reibung, keine Wasserwand, die am Körper hochläuft und sich überschlägt. Grünes Wasser über einer Mole gibt es nicht.
- Tropfen treffen nur Körper, nicht einander und nicht die Kamera. Nasse Linsen sind Sache von `com.dan.wetglass` (Phase 8).
- Schaum auf dem Wasser zeichnet das Meer (`com.dan.ocean`), nicht dieses Paket; die Ringwellen beim Wiedereintritt auch.
- Nebel wird nicht geschattet; im Schatten eines Körpers leuchtet er so hell wie in der Sonne.
- Regen kommt mit `com.dan.rain` (Phase 7).
