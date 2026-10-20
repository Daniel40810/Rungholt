# com.dan.ocean – Meer und Wellen

Das Paket rechnet eine offene See: Windsee aus einem Wellenspektrum, lange Dünung, Schaumköpfe, Schaumstraßen, Brandungsringe um Hindernisse und Ringwellen nach Einschlägen. Der Kern braucht nur Java 21 und beantwortet für jeden Ort, wie hoch das Wasser steht, wie es geneigt ist, wie schnell es sich bewegt und wie viel Schaum dort liegt. Der Ordner `gl/` zeichnet dieselbe See mit JOGL und Compute-Shadern (OpenGL 4.3). Das Paket kennt weder den Leuchtturm noch andere Pakete.

Zum Herauskopieren reicht `src/com/dan/ocean/`. Nur `demo/OceanDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Seegang nach Beaufort und Douglas:** `beaufort(n)` stellt Wind und Dünung ein. Die signifikante Wellenhöhe folgt der Tabelle in `SeaState` genau, von 0,2 m bei Bft 2 bis 14 m bei Bft 12.
- **Windsee als FFT-Ozean:** JONSWAP-Spektrum mit Spitzenperiode aus Wind und Streichlänge, Richtungsverteilung nach Donelan-Banner, verteilt auf drei oder vier Kacheln verschiedener Größe (Seitenverhältnis 6,85). So wiederholt sich nichts sichtbar. Die Kämme werden spitz, die Täler breit (horizontale Verschiebung).
- **Dünung:** vier Gerstner-Züge, getrennt vom Wind regelbar (Höhe, Periode, Richtung). Sie läuft auch bei Windstille.
- **Schaumköpfe:** Schaum entsteht, wo die Welle sich überschlägt (Jacobi-Determinante). Die Schwelle wird so bestimmt, dass die Bedeckung der Tabelle folgt, bei Bft 10 also die Hälfte der Fläche. Schaum klingt in rund 2,5 s nach.
- **Schaumstraßen** in Windrichtung ab Bft 7.
- **Brandungsring** um jedes Hindernis (`Obstacle`, senkrechter Zylinder). Er wird mit dem Seegang breiter und dichter.
- **Ringwellen:** `addImpulse(x, z, stärke)` setzt ein Wellenpaket, das nach außen läuft und in vier Sekunden abklingt.
- **Abfragen ohne Grafikkarte:** `heightAt`, `normalAt`, `velocityAt`, `verticalVelocityAt` (seit Phase 13, so billig wie die Höhe), `foamAt` und `sample` für alles, was schwimmt, spritzt oder fliegt. Sie rechnen mit denselben Startamplituden wie die Grafikkarte und liegen weniger als 1 % von H<sub>s</sub> neben der genauen Summe aller Wellen.
- **Optik (gl):** Fresnel mit Spiegelung eines Himmelspanoramas, Streulicht in Nordsee-Grün, Licht im durchscheinenden Wellenkamm gegen die Sonne, Sonnen- und Mondglitzer, Dunst zur Kimm, Erdkrümmung (die Kimm liegt richtig).
- **Schatten von oben (gl, optional):** zwei Schattenkarten als Draufsicht, jede mit ihrem Rechteck: eine für Wolken (`Light.shadowTexture`), eine für den Schatten eines Körpers, etwa eines Turms oder Schiffs (`Light.bodyShadowTexture`, seit Phase 5). Sie dämpfen nur Sonne und Mond, nicht den Himmel. Das Paket weiß nicht, wer den Schatten wirft.
- **Feine Störung (gl, optional):** eine kachelbare Steigungstextur (`Light.detailTexture`, dh/dx und dh/dz in RG) mit Kachelgröße, Stärke und Reichweite, die auf die Normale gelegt wird, etwa die Ringkarte des Regens (seit Phase 7). Das Paket weiß nicht, woher sie kommt.
- **Einfache Punktlichter (gl, optional, seit Phase 10):** bis zu acht Lichter ohne Lichtverteilung (`Light.flashPos`, `flashRgb`, `flashCount`: Ort, Größe der Quelle, Lichtstärke), etwa Blitze. Sie beleuchten Wasserkörper und Schaum; den Glanz der Quelle selbst zeigt die Spiegelung, wenn sie im Himmelspanorama steht. Das Paket weiß nicht, was leuchtet.
- **Binnengewässer mit kurzer Streichlänge (seit Haithabu Phase 2):** `fetchLimited` begrenzt H<sub>s</sub> nach JONSWAP auf 0,0016 · U · √(F/g) und höchstens 0,6 der Tiefe; das Preset `noor(n)` stellt ein flaches Binnengewässer ein (Streichlänge 1,5 km, Tiefe 3 m, ohne Dünung, kürzere Kämme). `SeaState.fetchLimitedHeight` rechnet den Wert ohne See.
- **Grund und Ufer (gl, optional, seit Haithabu Phase 2):** eine Höhenkarte des Grunds als Draufsicht (`Light.bedTexture` mit `bedRect`, `bedScale`, `bedOffset`, `bedColor`, `bedTurbidity`). Damit werden die Wellen zum Ufer hin flacher, der Grund scheint im seichten Wasser durch, am Ufer liegt ein Schaumsaum, und über Land wird nichts gezeichnet. Das Paket weiß nicht, woher die Karte kommt.
- **Reproduzierbar:** gleicher `seed`, gleiche See. Die See läuft nach 1 000 s ohne Sprung in Schleife.

## Lange Welle von außen (seit Glacier Phase 8)

`OceanGl.Light.longTexture0/1` und `longRect0/1` ({x0, z0, Zelle, an}) nehmen den Wasserstand einer langen Welle als Textur, etwa aus `com.dan.wave`: 0 ist das feine Gitter, 1 das grobe, das feine hat Vorrang. Trockene Texel (≤ −29) zählen beim Abtasten nicht mit, so bleibt der Spiegel am Ufer, wo er ist. Der Scheitelshader hebt die Fläche um die Höhe; wo die Welle den Grund freilegt, taucht sie unter ihn. Seit Rungholt Phase 4 gilt die Tiefe unter diesem Spiegel (Dämpfung, Saum, Durchsicht auf den Grund), und trockene Texel zeichnen kein Wasser – so kann eine Tide je Zelle (`com.dan.tide`) als lange Welle über dem Ruhespiegel `waterLevel` kommen. Der Pixelshader nimmt die Neigung in die Normale und malt Schaum an die steile Stirn. Ohne Textur ändert sich nichts. `Ocean.heightAt` kennt die lange Welle nicht; die App summiert (Schnittstelle `Sea`).

## Treibeis (seit Glacier Phase 9)

`OceanGl.Light.brashTexture` und `brashRect` ({x0, z0, Breite, Tiefe}) nehmen den Anteil der Fläche, den Treibeis bedeckt (0..1), etwa aus `com.dan.buoyancy.BrashField`. Der Pixelshader legt Brocken aus Zellen von rund 1,7 m hinein, so viele, wie das Feld sagt; in der Ferne bleibt ein weißer Schleier. Eisberge legen über `obstacleField` ihren Brandungsring.

## Zweiter Spiegel (seit Glacier Phase 13)

`Light.level` zeichnet die See auf einem anderen Wasserstand, etwa einen gestauten Seearm über dem Hauptsee. `Light.maskTexture` (R8, 1 = dieses Wasser) mit `maskRect` {x0, z0, Kante} und `maskMode` beschränkt das Zeichnen: 1 nur innen, −1 nur außen. Die Glacier-App zeichnet so den Brazo Rico während des Staus ein zweites Mal, ohne lange Welle und Treibeis.

## Benutzung

```java
Ocean ocean = Ocean.beaufort(6);           // Wind 12,3 m/s, H_s 3 m
ocean.windFromDeg = 240;                    // aus Westsüdwest
ocean.swellHeight = 1.2;                    // Dünung 1,2 m …
ocean.swellPeriod = 10;                     // … mit 10 s …
ocean.swellFromDeg = 300;                   // … aus Westnordwest
ocean.addObstacle(new Obstacle(0, 0, 4.5)); // Sockel mit 4,5 m Radius

// je Bild
ocean.step(dt);
double h = ocean.heightAt(x, z);            // Wasserstand an (x, z) in m
Ocean.Sample s = ocean.sample(x, z);        // dazu Normale, Geschwindigkeit, Schaum
ocean.addImpulse(x, z, 0.8);                // ein Brecher fällt zurück

// zeichnen (im GL-Faden)
OceanGl gl = new OceanGl();
gl.init(gl4);
gl.update(gl4, ocean);
OceanGl.View view = new OceanGl.View();     // Projektion·Blick, Auge, Tiefenkonvention
OceanGl.Light light = new OceanGl.Light();  // Sonne, Mond, Himmelspanorama, Dunst
gl.draw(gl4, ocean, view, light);
```

Alle Felder sind öffentlich und zur Laufzeit änderbar. Ändert sich etwas am Spektrum (Wind, Streichlänge, Tiefe, Qualität, seed), baut der Kern es im Hintergrund neu und übernimmt es ohne Ruck. `rebuildNow()` baut sofort, etwa für Standbilder.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Ocean` | Eingänge, Presets, Zustand, Abfragen, Schaumkalibrierung |
| `SeaState` | Beaufort- und Douglas-Tabelle: Wind, H<sub>s</sub>, Schaumbedeckung |
| `Spectrum` | JONSWAP, Richtungsverteilung, Kaskaden mit Startamplituden h₀ |
| `Swell` | Dünung als Gerstner-Wellen |
| `Ripples` | Ringwellen nach Einschlägen |
| `Obstacle` | Hindernis (senkrechter Zylinder) |
| `CpuField`, `Fft` | die See auf dem Prozessor (Kaskade 0 auf 128², Kaskade 1 auf 64²) |
| `gl/OceanGl` | Compute-Shader `spectrum`, `fft`, `assemble` und die Oberfläche `surface.vert/frag` |
| `gl/RingMesh` | Ringgitter um die Kamera, von 12 cm bis 60 km |
| `gl/Backdrop` | einfaches Himmelspanorama und Bild ins Fenster für Szenen ohne eigenen Himmel |

## Demo und Selbsttest

- `com.dan.ocean.demo.OceanDemo` zeigt die See mit Reglern für Beaufort, Windrichtung, Dünung, Sonne und Qualität. „Brecher“ setzt eine Ringwelle. Ziehen dreht die Kamera, das Rad zoomt.
- Ohne Fenster rechnet `OceanDemo --bild datei.png [breite] [höhe]` Bft 2, 6 und 10 nebeneinander und vergleicht dabei die Auslenkung auf der Grafikkarte mit der genauen Rechnung des Kerns.
- `com.dan.ocean.demo.OceanCheck` prüft FFT, Wellenhöhen gegen die Tabelle, Abfragen gegen die genaue Summe, Geschwindigkeit, Schaumbedeckung, Dünung, Ringwellen, Brandungsring und Gitter und endet mit „Alles in Ordnung.“.
- `OceanGl.selfCheck` liest auf der Grafikkarte einige Texel jeder Kaskade zurück und meldet die größte Abweichung zur Rechnung des Kerns.

## Grenzen

- Tiefwasser ist die Vorgabe. Mit `depth` ändert sich die Dispersion, aber Wellen laufen nicht auf, brechen nicht am Ufer und werden nicht gebeugt. Mit Grundkarte werden sie zum Ufer hin nur gedämpft (kein Auflaufen, keine Brandung).
- Hindernisse werfen keine Wellen zurück und schatten nichts ab. Sie bekommen nur den Brandungsring, die Gischt kommt aus dem Package `spray`.
- Schaum aus Überschlag hängt an den zwei größten Kacheln. `foamAt` ist eine Näherung (nur Kachel 0, ohne Nachleuchten).
- Ringwellen sind auf 32 gleichzeitig begrenzt und für einzelne große Ereignisse gedacht. Regen bringt seine eigenen Ringe als feine Störung mit.
- Die Phase läuft in doppelter Genauigkeit (`GL_ARB_gpu_shader_fp64`, ab OpenGL 4.0 in jedem Kernprofil).

## Seit Haithabu Phase 8: viele Pfähle

`Ocean.obstacleField` nimmt ein `ObstacleField`: ein Raster des Abstands zum nächsten Körper (Zelle frei wählbar, gebaut mit `ObstacleField.of(kreise, x0, z0, breite, tiefe, zelle, maxAbstand)`). Um jeden Körper legt sich derselbe Brandungsring wie um ein `Obstacle`, das Wasser zwischen den Pfählen bleibt stehen; so tragen Landebrücken und Pfahlreihen mit Tausenden Pfählen ihren Schaum, ohne die Grenze von acht `Obstacle` zu sprengen. Die Grafikkarte bekommt das Raster als R32F-Textur (Einheit 8). Neu sind auch `ringHsLow` und `ringHsHigh`: ab welcher Wellenhöhe der Ring beginnt und wann er voll ist. Offene See wie bisher 0,15 bis 1,6 m, `Ocean.noor` 0,03 bis 0,35 m, sonst bliebe das flache Noor an den Pfählen glatt. `OceanCheck` prüft beides.

## Seit Haithabu Phase 9: Rümpfe

`Ocean.hulls` nimmt eine Liste von `HullMask` (Mitte, Kurs, halbe Länge und halbe Breite in der Wasserlinie, Völligkeit; bis 8 werden gezeichnet). Der Umriss ist eine spitze Linse mit der Halbbreite `halfBeam · (1 − |u|^Völligkeit)^0,8` (u = −1 … 1 von Heck zu Bug); 2 passt zu schlanken Rümpfen, bauchige brauchen mehr. Innerhalb des Umrisses zeichnet `surface.frag` kein Wasser; so schneidet die Oberfläche nicht durch offene Boote, und man sieht die Innenhaut. Außen liegt ein schmaler Schaumsaum, eine angedeutete Bugwelle. Das Paket kennt keine Schiffe: Der Benutzer füllt die Liste je Bild. `HullMask.distance` gibt den Abstand zum Umriss auch ohne Grafikkarte; `OceanCheck` prüft ihn.

## Seit Glacier Phase 2: Gletschersee

`Ocean.lago(beaufort)` – ein Binnensee statt des Meeres: Wind über rund 10 km Anlauf, 100 m tief, keine Dünung.
Dazu die Farben des milchig-türkisen Gletscherwassers `LAGO_WATER` und `LAGO_SSS` (Gletschermehl streut, das Licht
kommt grün-türkis aus dem Wasser zurück) [G]. Die App reicht das Gelände als Grund (`Light.bedTexture`, `bedRect`,
`bedScale`, `bedOffset`) und eine hohe Trübung (`bedTurbidity`), damit der Grund nur am Ufer durchscheint.
Rückfluss in Phase 16.

## Seit Rungholt Phase 2: Wattenmeer

- Preset `watt(n)`: Streichlänge 6 km, Tiefe 4 m, schwache Dünung (0,25 m, 7 s aus Westnordwest), kurze steile See; Farben `WATT_WATER`, `WATT_SSS` (trübes Grüngrau vom Schwebstoff) [G].
- `Light.bedOutside`: Höhe des Grunds außerhalb von `bedRect`. Ohne Wert (NaN) bleibt es wie bisher (dort kein Wasser); mit Wert geht das Meer über den Rand der Höhentextur hinaus, etwa auf die offene See vor dem Ausschnitt.
- Die Tide setzt der Benutzer über `waterLevel` (in Rungholt erst ein Platzhalter, ab Phase 4 aus `com.dan.tide`); die Köge hinter den Deichen blendet `maskTexture` mit `maskMode` −1 aus.

## Seit Rungholt Phase 6: Strömung, Kachelmuster, unter Wasser

Alles abschaltbar; ohne die neuen Felder zeichnet das Paket wie bisher.

- `Light.flowTexture` mit `flowRect` ({x0, z0, Zelle, an}): Strömung als RG16F/RG32F (m/s nach Osten und Süden). Die Fläche kräuselt sich darin (Rauschen, das mit dem Wasser zieht, zwei Phasen überblendet), Schaum zieht ab 0,4 m/s in Streifen mit, das Wasser wird trüb (`siltColor`, `siltStrength`), der Grund verschwindet dann schneller.
- `Light.tileBreak` 0..1: die beiden größten Kaskaden und die Dünung schwanken mit flachem Rauschen in der Stärke (0,45 bis 1,55, im Mittel 1). Gegen das Rautenmuster im flachen Wasser, wenn die größte Kachel klein ist (Watt: 24 m). `Ocean.heightAt` rechnet ohne diese Schwankung.
- `Light.longSoft` 0..1: am Rand der nassen Zellen der langen Welle sinkt der Spiegel mit Rauschen über eine halbe Zelle unter den Grund; die Wasserlinie läuft weich statt in Treppen des Gitters (Befund Rungholt: Flutsaum über flachen Bänken in 16-m-Stufen).
- `Light.under` und `drawUnderwater(gl, Under)`: liegt das Auge unter dem Spiegel, zeigt die Fläche ihre Unterseite (im Snellschen Fenster bis 48,6° vom Lot der gebrochene Himmel, außerhalb die Totalreflexion des Wassers), und ein Vollbild-Durchgang legt trübes Wasser über die Szene: Durchlässigkeit exp(−σ · Weg), Streulicht in Wasserfarbe, mit der Tiefe dunkler. Braucht die Tiefe der Szene als Textur.
