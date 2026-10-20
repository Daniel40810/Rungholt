# com.dan.forest – Bäume und Wald

Das Paket erzeugt Bäume prozedural. Sie bewegen sich im Wind, färben sich mit der Jahreszeit und verlieren ihr Laub. Der Kern braucht nur Java 21 und hängt weder vom Geyser-Projekt noch von einer 3D-Bibliothek ab. Er liefert Dreiecksnetze mit Farben und Normalen. Unter `demo/` liegt ein einfacher CPU-Renderer als Beispiel; seit Haithabu Phase 2b zeichnet der Ordner `gl/` ganze Wälder mit JOGL (OpenGL 4.3, instanziert).

Zum Herauskopieren reicht `src/com/dan/forest/`. Die Demos brauchen zusätzlich `FStyle.jar` für die Oberfläche, `gl/` braucht JOGL.

## Arten

| Name | Art | Kronenform |
|---|---|---|
| Drehkiefer | *Pinus contorta* | schmal, Quirle |
| Espe | *Populus tremuloides* | schmal-oval, starkes Blattzittern |
| Douglasie | *Pseudotsuga menziesii* | kegelförmig |
| Eiche | *Quercus robur* | breit ausladend |
| Buche | *Fagus sylvatica* | rund |
| Birke | *Betula pendula* | oval, hängende Zweige |
| Fichte | *Picea abies* | kegelförmig, Quirle, hängende Zapfen |
| Engelmann-Fichte | *Picea engelmannii* | schmal kegelförmig, blaugrün, hängende Zapfen |
| Felsengebirgs-Tanne | *Abies lasiocarpa* | schmaler Turm bis zum Boden, aufrechte violette Zapfen |
| Weißstämmige Kiefer | *Pinus albicaulis* | rundlich, früh verzweigt, helle Rinde |
| Weißtanne | *Abies alba* | mächtiger Kegel, silbergraue Rinde, aufrechte Zapfen |
| Waldkiefer | *Pinus sylvestris* | Schirm, oben orange Rinde |
| Europäische Lärche | *Larix decidua* | lichter Kegel, im Herbst golden, im Winter kahl |
| Zirbe | *Pinus cembra* | dicht, säulenförmig, aufrechte blaue Zapfen |
| Schwarzerle | *Alnus glutinosa* | kegelig-eiförmig, waagerechte Äste; färbt sich kaum, verliert das Laub spät noch grün |

**Nadelbäume** haben ein paar eigene Werte in `Species`:

- **Kronen:** `Crown.SPIRE` für den schmalen Turm der Hochlagentannen, `Crown.UMBRELLA` für den flachen Schirm alter Kiefern.
- **Zapfen:** `cones`, `coneSize`, `coneColor`, `coneUpright`, `coneZone`. Sie stehen aufrecht oder hängen, und zwar nur im oberen Teil der Krone. Sie bewegen sich mit ihrem Ast und bekommen im Winter Schnee.
- **Rinde oben:** `barkTop`, `barkTopFrom`, zum Beispiel die orange „Spiegelrinde“ der Waldkiefer.
- **Maitriebe:** `shoots` ist die Farbe der neuen Spitzen. Sie treiben ab `leafOut` hell aus und dunkeln bis zum Hochsommer nach.
- **Lärche:** Ein Nadelbaum mit `deciduous = true` wirft seine Nadeln ab wie ein Laubbaum. Die Nadeln färben sich vorher und fallen als Laub.

`Species.ALL` enthält Erzeuger für alle Arten, jeder Aufruf liefert eine frische Kopie. `Species.byName("Buche")` sucht eine Art nach Namen. Alle Felder sind öffentlich und lassen sich ändern, zum Beispiel Höhe, Astebenen, Winkel, Blattform, Biegsamkeit, Farben je Jahreszeit und die Tage für Austrieb, Verfärbung und Laubfall. So entstehen eigene Arten.

## Benutzung

```java
Ground ground = (x, z) -> terrain.height(x, z);   // oder Ground.FLAT
Forest forest = new Forest(ground);
int pine  = forest.addSpecies(Species.lodgepolePine());
int aspen = forest.addSpecies(Species.aspen());

ForestPlanter.plant(forest, -100, -100, 100, 100, 6f, (x, z) -> 1f,
        new int[]{pine, aspen}, new float[]{0.8f, 0.2f}, 25f, ground, 42);
// oder einzeln: forest.place(species, variant, x, z, yaw, age)

forest.wind.speed = 8;  forest.wind.direction = 60;  forest.wind.gustiness = 0.6f;
forest.setSeason(285, 0);                        // Tag im Jahr, Schnee 0..1

// je Bild:
forest.update(t, dt, camX, camY, camZ);          // LOD wählen, Wind, Laubfall
forest.visit((mesh, pos, nrm, col, tree) -> {
    // mesh.tri / mesh.nt: Dreiecke; pos, nrm, col: je Punkt 3 Werte, im Baumraum
    // tree.x/y/z, tree.yaw, tree.scale: Platzierung; mesh.doubleSided[i]: Blätter beidseitig
});
int n = forest.leaves.quads(xyz, rgb, nrm);      // fallende und liegende Blätter
```

Mit `TreeGenerator.grow(species, seed, age)` und `TreeMesh.build(model, lod)` bekommt man einen einzelnen Baum ohne Wald. `TreeMesh.build(model, lod, barkLod)` baut Laub und Geäst in verschiedenen Stufen, zum Beispiel Blätter der Stufe 1 an Ästen der Stufe 2 für viele Laubbäume in mittlerer Entfernung. `TreeMesh.silhouette(model, sides, rings)` liefert eine sehr leichte Hülle mit etwa 100 Dreiecken für ferne Bäume. Das Geyser-Projekt nutzt die Silhouette für die Kiefern tief im Wald. Am Waldrand und bei den Espen nimmt es die feineren Stufen (siehe `com.dan.geyser.world.Grove`).

## Großer Wald auf der Grafikkarte (seit Haithabu Phase 2b)

`ForestField` verteilt Bäume über eine ganze Landschaft: Kacheln zu 64 m, je Platz eines Gitters (Abstand `spacing`) höchstens ein Baum, reproduzierbar aus Startwert und Kachel. Wo welche Art wächst, sagt die Schnittstelle `ForestField.Density` (je Art 0..1), die Höhe `Ground`. Je Bild sortiert `update(auge, projektionBlick, maxNeu)` die Bäume im Blickkegel in Eimer nach Art, Variante und Stufe:

| Stufe | Netz | bis (Mittel) |
|---|---|---|
| 0 | `TreeMesh.build(m, 0)`, alle Blattbüschel | 12 m je 20 m Baumhöhe |
| 1 | `build(m, 1, 2)`, gröbere Büschel an gröberen Ästen | 30 m je 20 m Baumhöhe |
| 2 | dieselben Büschel, nur jedes siebte, doppelt so groß (rund 3 000 Dreiecke) | 150 m |
| 3 | Silhouette 7 × 4 (rund 60 Dreiecke) | 600 m |
| 4 | Silhouette 5 × 2 (unter 40) | Reichweite |

Jenseits von 40 % der Reichweite wird ausgedünnt; die verbleibenden Kronen werden mit 1/√p breiter, so dass ein ferner Waldrand geschlossen bleibt. `ForestField.Quality` (`LOW` bis `ULTRA`) stellt Reichweite (1,1 bis 3,2 km), Stufengrenzen und Abstand ein.

`gl/ForestGl` backt die Netze im Hintergrund für den Tag im Jahr (Farben nach `Season`, gefallene Blätter weg, kahle Kronen in der Ferne als lichtes, graubraunes Geäst) und zeichnet jeden Eimer mit einem Aufruf. Der Wind biegt im Vertex-Shader (Neigung mit U², Böen laufen über die Fläche, Laub zittert). Licht über `ForestGl.Light`: Sonne, Mond, Himmel mit Gewicht, Dunst, Nässe, Wolkenschatten von oben und eine Sonnenschattenkarte des Benutzers; `drawShadow` zeichnet die Bäume selbst hinein (nah mit Stufe 2, sonst mit der Silhouette).

```java
List<Species> arten = List.of(Species.oak(), Species.beech(), Species.birch(), Species.alder());
ForestGl gl = new ForestGl(arten, 4, 950);              // 4 Varianten je Art
ForestField wald = new ForestField(4, 4, (x, z, art) -> dichte(x, z, art), gelaende::hoehe, 950);
wald.setHeights(gl.heights());
wald.apply(ForestField.Quality.MEDIUM);
gl.init(gl4);
// je Bild:
gl.setSeason(tagImJahr, schnee);                         // backt bei Bedarf neu
gl.setWind(nachX, nachZ, mProS, boeigkeit);
wald.update(auge, projektionBlick, 24);
gl.update(gl4, wald);
gl.drawShadow(gl4, lichtMatrix, t, 3);                   // optional, in die eigene Schattenkarte
gl.draw(gl4, view, light, t);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Species` | Artbeschreibung und Voreinstellungen |
| `TreeGenerator`, `TreeModel` | Stamm, Äste in mehreren Ebenen, Hüllform der Krone, Blattstellen |
| `TreeMesh` | Netz in vier Stufen: 0 und 1 mit Rinde und Blattbüscheln, 2 mit groben Büscheln, 3 als Silhouette |
| `WindField` | Grundwind mit Böen, die als Rauschen durch den Wald ziehen |
| `TreeAnimator` | Bewegung je Punkt in Hierarchie: Stamm neigt und schwingt, Äste schwingen und wippen, Blätter zittern |
| `Season` | Austrieb, Sommergrün, Verfärbung, kahl, Schnee |
| `LeafFall` | Blätter lösen sich, trudeln, landen, werden braun und verschwinden |
| `ForestPlanter` | Verteilung mit Mindestabstand und Artengruppen |
| `Forest` | hält alles zusammen: Varianten je Art, LOD nach Entfernung, parallele Bewegung |
| `ForestField` | großer Wald in Kacheln für die Grafikkarte: Dichte je Art, fünf Stufen, Ausdünnung, Blickkegel |
| `gl/ForestGl` | Wald mit JOGL: Netze je Tag gebacken, Instanzen, Wind im Shader, Sonnen- und Wolkenschatten |

Mit `forest.leaves.water` lassen sich Blätter, die auf Wasser landen, an ein Wasser übergeben (Schnittstelle `LeafFall.Water`, etwa ein Fluss aus `com.dan.river`, der sie weiterträgt). Nur die `maxAnimated` nächsten Bäume (voreingestellt 40) werden je Punkt bewegt, die übrigen stehen still. Die Entfernungen für die LOD-Stufen stehen in `lodDistance`.

## Demo und Selbsttest

- `com.dan.forest.demo.ForestDemo [ansicht art tag]`: Ansicht 0 = Einzelbaum, 1 = Wald Yellowstone, 2 = Wald Mitteleuropa, 3 = Nadelwald Yellowstone, 4 = Bergwald Alpen; Art 0 bis 13 in der Reihenfolge der Tabelle. Die Regler steuern Wind, Böen, Jahreszeit und Schnee.
- `com.dan.forest.demo.ForestGlDemo [--bild datei.png hain|wald|rand breite höhe tag]`: der Grafikteil mit Eiche, Buche, Birke und Schwarzerle auf welligem Land, mit Sonnenschatten; „wald“ zeigt 2 km Reichweite.
- `com.dan.forest.demo.ForestCheck`: prüft Aufbau, Wind, Jahreszeit, Laubfall und `ForestField` (Dichte je Art, Stufen, Ausdünnung, Blickkegel, Startwert) ohne Fenster und endet mit „Alles in Ordnung.“

## Grenzen

- Im Grafikteil bewegen sich die Bäume nur im Shader als Ganzes (kein Ast für sich, keine fallenden Blätter); Laubfall und feine Bewegung bleiben dem CPU-Weg (`Forest`) vorbehalten.
- Stufe 2 und die Silhouetten sind Näherungen: aus der Nähe wirken die großen Büschel wie Paddel, die Silhouetten wie glatte Kronen.
- Der Beispiel-Renderer arbeitet auf dem Prozessor. Ein Wald mit einigen hundert Bäumen braucht dort etwa 100 ms je Bild. Das Paket selbst (Bewegung und Netz) braucht rund 10 ms.
- Blätter werden als gezackte Büschel gezeichnet, nicht als einzelne Blätter. Nadelbäume bekommen Nadelbürsten.
- Die Artwerte sind nach Augenmaß angenähert und nicht vermessen.
- Die Bewegung ist ein Modell für kleine Winkel und keine Physik. Sturmbruch gibt es nicht.

## Seit Glacier Phase 2: Südbuchen

`Species.lenga()` (Nothofagus pumilio, Waldbaum bis zur Baumgrenze), `Species.nire()` (Nothofagus antarctica,
krumm, an lichten, trockenen Rändern), `Species.guindo()` (Nothofagus betuloides, immergrün, am feuchten Hang) [G].
Alle drei stehen in `Species.ALL`. Ihre Jahresdaten (Austrieb, Herbstfarbe, Laubfall) sind wie bei allen Arten
nordhemisphärisch angegeben; eine App auf der Südhalbkugel verschiebt den Tag im Jahr um 182 Tage (Glacier:
`Wiring.northernDay`). Lenga und Ñire färben sich im Herbst rot bis orange. Rückfluss in Phase 16.
