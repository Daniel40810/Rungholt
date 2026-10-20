# com.dan.lightning – Gewitter

Das Paket erzeugt Blitze und lässt sie ablaufen, wie man sie sieht und hört: den Leitblitz, der in Stufen herabwächst, die Hauptschläge durch denselben Kanal, das Nachleuchten, die Wolke, die von innen leuchtet, und den Donner, der mit Schallgeschwindigkeit ankommt. Es rechnet mit echten Größen. Die Ströme der Schläge folgen den Messungen von Berger (CIGRE), die Schlagweite und der Fang durch hohe Punkte den Formeln aus dem Blitzschutz, das Licht einer Lichtstärke je Meter Kanal.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet mit JOGL (GL 4.3, ohne Compute-Shader). Das Paket kennt weder Wolken noch Meer noch Turm: Wo Gewitterzellen stehen, was den Kanal verdeckt und wo der Boden ist, kommt über kleine Schnittstellen herein; das Licht geht als Lichtpunkte hinaus, die jeder Shader lesen kann. Zum Herauskopieren reicht `src/com/dan/lightning/`; nur `demo/LightningDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Erdblitze:** Der Leitblitz wandert von 0,6 bis 1,2 Ladungshöhen über der Wolkenuntergrenze herab, in Stufen von im Mittel 30 m (Stufe „Hoch“) mit der Windung echter Kanäle (rund 16° je Stufe), und verzweigt sich. Die Äste laufen schräg nach unten aus, ohne den Boden zu erreichen. Kommt die Spitze dem Wasser oder einem hohen Punkt auf die Schlagweite nahe (r = 10 · I^0,65 m, bei 30 kA rund 91 m), springt sie über. Hohe Punkte fangen Blitze in ihrem Anziehungsradius nach Eriksson (Ra = 0,84 · I^0,74 · h^0,6, für einen Mast von 30,7 m rund 80 m).
- **Schläge:** im Mittel 3,6 je Erdblitz; der erste mit einem Median von 30 kA, die folgenden mit 12 kA, im Abstand von rund 45 ms. Gut ein Drittel der Blitze hat Nachstrom, der den Kanal einige Zehntelsekunden glühen lässt. Die Äste leuchten nur beim ersten Schlag.
- **Wolkenblitze, Spinnenblitze, Wetterleuchten:** waagerechte, weit verzweigte Entladungen in der Wolke, knapp unter der Untergrenze oder fern am Horizont, mit flackernden Pulsen statt Schlägen.
- **Helligkeit:** Jeder Schlag erscheint als Puls mit der Trägheit des Auges (35 ms) und einem Nachleuchten des heißen Kanals (120 ms). Die Lichtstärke je Meter ist einstellbar (`candelaPerMetre`, 5000 cd/m bei 30 kA); ein Blitz in 5 km gibt dann rund 0,4 lx, heller als Vollmond.
- **Gewitter:** `setLevel` stellt aus, Wetterleuchten, mäßig, kräftig oder schwer ein (bis 20 Blitze je Minute, Anteil der Erdblitze 22 bis 33 %). Blitze entstehen nach einem Poisson-Prozess dort, wo `cells` es zulässt.
- **Sichtbarkeit:** Jedes Stück des Kanals bekommt beim Entstehen eine Durchlässigkeit zum Auge, aus `occlusion` (zum Beispiel der Wolkendichte) oder, ohne sie, aus der Höhe über der Untergrenze.
- **Licht:** `Snapshot.lights(n, max)` fasst jeden Blitz in bis zu n Lichtpunkte zusammen (Ort, cd, sichtbarer Anteil). `illuminanceAt` gibt die Beleuchtungsstärke an jedem Ort, `flashLuminance` die Leuchtdichte, nach der eine Belichtung im Blitz nachgeben kann. `escape(y, Untergrenze)` sagt, wie viel Licht aus einem Kanal in der Wolke unten herauskommt.
- **Donner:** Er beginnt, wenn der Schall vom nächsten Punkt des Kanals ankommt (Schallgeschwindigkeit aus der Temperatur), und rollt, bis der vom fernsten da ist. Pegel 122 dB in 100 m bei 30 kA, kugelförmig leiser, mit Dämpfung in der Luft; jenseits von 15 km biegt der Schall über das Ohr. Unter 35 dB hört man nichts; ein Blitz in 40 km bleibt stumm, in den Turm vom Fuß aus ist es ein Knall nach 0,1 s.
- **Stufen:** `LOW` bis `ULTRA` wählen die Länge der Stufen, die tiefste Ordnung der Äste und die höchste Zahl der Stücke (700 bis 6000).

## Benutzung

```java
Lightning l = Lightning.storm();                 // calm(), breeze(), gale(), storm(), beaufort(n)
l.setLevel(Lightning.Level.STRONG);
l.cloudBase = 900;
l.cells = (x, z) -> wolken.regen(x, z);          // wo Blitze entstehen
l.occlusion = (px, py, pz, ex, ey, ez) -> wolken.durchlaessigkeit(px, py, pz, ex, ey, ez);
l.attractors.add(new Lightning.Attractor(0, 30.7, 0, "Turm"));
l.onThunder = t -> ton.donner(t.distance(), t.levelDb(), t.duration());

// je Bild
l.listenerX = auge[0]; l.listenerY = auge[1]; l.listenerZ = auge[2];
l.step(dt);

// von Hand
l.strike(x, z);                                  // Erdblitz, der bei (x, z) herunterkommt
l.strikeAttractor(0);                            // in den Turm
l.discharge(Bolt.Kind.SPIDER, x, z);

// auf der Grafikkarte
LightningGl gl = new LightningGl();
gl.init(gl4);
gl.update(gl4, l.snapshot());
gl.drawPanorama(gl4, 256, 128, auge, vorbelichtung, sigma, streuung, untergrenze);   // für Spiegelungen
gl.bind(gl4, turmShader, vorbelichtung, sigma);  // lightning.glsl: ltgIrradiance, ltgSpecular
LightningGl.View v = new LightningGl.View();     // viewProj, invViewProj, eye, targetFbo, preExposure, extinction, scatter, ceiling …
gl.drawChannels(gl4, v);                         // nach Himmel und Wolken; die Szene verdeckt danach
gl.drawGlow(gl4, v);                             // nach der Szene, mit einer Kopie ihrer Tiefe
```

Alles, was ins Bild geht, ist linear und vorbelichtet und wird mit ONE, ONE addiert. Wolken, Meer und andere Packages, die `lightning.glsl` nicht einbinden wollen, bekommen die Lichtpunkte über `lightCount`, `lightPos`, `lightRgb` und `cloudLight` als Zahlen.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Lightning` | Eingänge, Rate, Bau der Blitze (Leitblitz, Äste, Fang), Donner, `Snapshot` mit Licht |
| `Bolt` | ein Blitz: Stücke, Ordnung, Weg des Leitblitzes, Sichtbarkeit; Schläge, Ströme, Nachstrom; Helligkeit zur Zeit t |
| `gl/LightningGl` | Speicherpuffer der Stücke, Kanäle, Leuchten der Luft, Rundumbild, Lichtpunkte, Selbsttest |
| `gl/lightning.glsl` | Baustein für fremde Shader |
| `gl/bolt.vert`, `gl/bolt.frag` | der Kanal als Band quer zum Blick: Kern und Hof |
| `gl/glow.frag` | Einfachstreuung um die Lichtpunkte, geschlossen gerechnet |

## Demo und Selbsttest

- `com.dan.lightning.demo.LightningDemo` zeigt Gewitter über der See bei Nacht mit einem Mast als Fangpunkt; das Wasser spiegelt die Blitze. Rechts: Stärke, „Blitz“, „In den Mast“, „Spinnenblitz“, „Wolkenblitz“, „Wetterleuchten“, Regen, Wolkenuntergrenze, Zeitlupe und Belichtung, dazu der nächste Donner.
- Ohne Fenster rechnet `LightningDemo --bild datei.png [breite]` einen Erdblitz beim ersten und beim dritten Schlag, den Leitblitz, einen Einschlag in den Mast, einen Spinnenblitz, einen Wolkenblitz, Wetterleuchten, denselben Blitz im Regen und den Ablauf als Kurve mit dem Donner. Dabei vergleicht es Grafikkarte und Prozessor.
- `com.dan.lightning.demo.LightningCheck` prüft ohne Fenster und ohne Grafikkarte, mit 30 Prüfungen; Ende „Alles in Ordnung.“: Presets und Wiederholbarkeit, Form des Kanals, Statistik der Schläge, Fang durch einen Turm, Rate und Zellen, Sichtbarkeit, Licht und Donner.
- `LightningGl.selfCheck` vergleicht die Beleuchtungsstärke aus `lightning.glsl` an 64 Orten mit dem Prozessor und die Lichtmenge eines gezeichneten Kanals (Summe aller Bildpunkte) mit seiner Beleuchtungsstärke am Auge.

## Grenzen

- Der Kanal wirft keine Schatten; sein Licht kommt aus bis zu acht Punkten.
- Das Leuchten der Luft ist Einfachstreuung. Wie Licht in der Wolke wandert, nähert eine Diffusion mit einer Länge von 1,3 km; Wolken, die das Paket nicht kennt, rechnen das selbst (die App: `com.dan.cloud`).
- Positive Blitze aus dem Amboss, Kobolde und Kugelblitze fehlen.
- Der Kanal hängt still; im Wind wandert ein echter Kanal während des Nachstroms um einige Meter.
- Der Donner ist ein Ereignis mit Zeit, Dauer und Pegel; wie er klingt, macht `com.dan.soundscape` (Phase 14).
