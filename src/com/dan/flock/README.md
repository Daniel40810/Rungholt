# com.dan.flock – Möwen und andere Vögel

Silbermöwen um einen Turm auf See: Sie fliegen im Schwarm, stehen im Aufwind an der Luvseite, landen gegen den Wind auf Geländern und Tonnen, schlafen nachts am Turm und suchen im Sturm das Lee. Das Package braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/flock/`.

## Was es kann

- **Flug:** 8 bis 17 m/s gegen die Luft, im Gleitflug 0,6 m/s Sinken, mit Flügelschlag (3,1 Hz) bis 2 m/s Steigen. Gesteuert nach Zielen und nach drei Schwarmregeln (Abstand 4 m, Richtung und Zusammenhalt in 25 m), Hindernissen ausweichen, über den Wellen bleiben. Der Wind folgt dem logarithmischen Profil über See.
- **Aufwind am Turm:** `updraft(x, y, z)`: vor der Luvseite eines Zylinders am stärksten (2,5 m vor der Wand bei Bft 5 gut 5 m/s), nach außen und über der Oberkante abklingend, im Lee nichts. Ab 5,5 m/s Wind stellen sich bis zu fünf Möwen dort hinein und stehen fast still in der Luft.
- **Windschatten:** `windAt(x, y, z)` schwächt den Wind hinter einem Hindernis bis auf ein Fünftel. Dort halten sie im Sturm aus.
- **Landen und Sitzen:** Sitzplätze (`Perch`, auch bewegte wie Tonnen oder zeitweise fehlende wie eine Platte bei Flut) werden über einen Punkt im Lee gegen den Wind angeflogen; die letzten Meter flattert die Möwe gerade auf den Platz. Sie sitzt mit dem Kopf im Wind, angelegten Flügeln und Beinen, ruft beim Landen und Abfliegen.
- **Tag, Nacht, Sturm:** Abends suchen sie einen Platz am Turm, wer keinen findet, fliegt zur Küste; nachts fliegt keine und keine ruft. Ab Bft 8 landen sie nur noch im Lee oder fliegen weg, ab Bft 10 sind alle weg.
- **Rufe:** zufällig mit Abstand, als Ereignis `onCall` (für einen Klang in einem anderen Package).
- **Netz und Flügel:** `GullMesh` baut die Möwe (Rumpf, Kopf, Schnabel, Mantel, Innen- und Außenflügel mit schwarzen Spitzen, Beine); der Vertex-Shader biegt die Flügel um Schulter und Handgelenk: schlagen, gleiten, im Aufwind geknickt, beim Sitzen angelegt.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)`. **Reproduzierbar:** gleicher `seed`, gleicher Flug. **Stufen:** `Quality.LOW` bis `ULTRA` (6 bis 28 Möwen).

## Weitere Arten (seit Haithabu Phase 13)

`Species` nennt sechs Arten mit Maßen, Flug (Geschwindigkeiten, Sinken, Steigen, Schlagfrequenz, Anteil im Gleitflug), Fähigkeiten und Fluchtdistanz [G]:

| Art | Länge / Spannweite | Kann | Flucht |
|---|---|---|---|
| Silbermöwe | 0,60 / 1,40 m | fliegt, sitzt, schwimmt | 6 m |
| Nebelkrähe | 0,47 / 0,98 m | fliegt, sitzt auf Firsten, geht am Boden und pickt | 18 m |
| Rauchschwalbe | 0,19 / 0,33 m | fliegt tief und wendig, nur Mitte April bis Mitte September | 2 m |
| Stockente | 0,58 / 0,90 m | schwimmt, gründelt, fliegt steil auf (Erpel und Weibchen) | 25 m |
| Höckerschwan | 1,50 / 2,30 m | schwimmt, gründelt, schwimmt weg statt aufzufliegen | 12 m |
| Kormoran | 0,85 / 1,40 m | schwimmt tief, taucht, trocknet auf Pfählen die Flügel | 30 m |

- **Zahl:** `mix` gibt die Zahl je Art. Die Möwen kommen weiter aus `quality`. Alle Werte 0: nur Möwen wie bisher.
- **Wasser:** `water` gibt die Höhe der Oberfläche oder NaN, wo kein offenes Wasser ist (Land, Eis, zu flach). `waters` sind die Wasserflächen der Schwimmer, `feeding` die Futterplätze der Krähen.
- **Störer:** `disturbers` sind Auge, Hund, Boote. Wer näher kommt als die Fluchtdistanz, scheucht die Vögel auf. Enten fliegen auf und landen woanders wieder, Schwäne schwimmen weg, Kormorane tauchen, Krähen und Möwen fliegen ab.
- **Jahr:** `dayOfYear` steuert die Schwalben. Liegt Eis auf dem ganzen Wasser, sind die Schwimmer fort.
- **Nacht:** Krähen und Schwalben schlafen außerhalb der Szene, Enten und Schwäne auf dem Wasser.
- **Neue Zustände:** `SWIM`, `DIVE`, `WALK`.
- **Sitzplätze:** Eine Maske gibt an, welche Arten einen Platz nehmen (`Perch.only`). Seit Haithabu Phase 14 hat jeder Platz eine Beliebtheit (`Perch.appeal`, 1 üblich): Ein Vogel wählt unter fünf freien Plätzen den mit dem kleinsten (Abstand + 20 m) / Beliebtheit.
- **Netze** (`BirdMesh`): wie die Möwe gebaut, nach den Maßen der Art gestreckt.
  - Der Hals von Schwan und Kormoran ist eine glatte Röhre: sitzend als S, im Flug gestreckt (zweite Spielart).
  - Die Schwalbe hat einen Gabelschwanz, die Ente einen blauen Spiegel.
  - `FlockGl` zeichnet ein Netz je Art und Spielart mit einem Aufruf.
  - Gründeln neigt den Körper Kopf voran unter Wasser, der Kormoran sitzt aufrecht.

## Patagonien (seit Glacier Phase 10)

- **Andenkondor** (`Species.KONDOR`, 1,20 m lang, 3,00 m Spannweite): gleitet mit 16 m/s von Thermik zu Thermik und sinkt dabei, kreist darin mit 40 bis 70 m Halbmesser (die Mitte treibt mit dem Wind) und steigt mit `condorLift()` (Sonne aus `ambientLux`, dazu Hangwind) bis an die Decke. Er schlägt nur, wenn er unter die Hälfte von `condorFloor` sinkt. Thermiken: `thermals` (Kreise über sonnigen Hängen), sonst Kreise im Revier um `condorX`, `condorZ` mit `condorRadius`. Nachts sitzt er außerhalb der Szene an seinem Felsen. Netz mit gefingerten Handschwingen, weißem Feld auf den Armschwingen und grauem Kopf.
- **Dominikanermöwe** (`kelpGulls = true`, Larus dominicanus): verhält sich wie die Silbermöwe, hat aber einen schwarzen Mantel (eigenes Netz, Spielart 1 der Möwe).
- **Sitzplätze, die treiben:** ein `Perch` mit eigenem `Spot` (etwa die höchste Ecke eines Eisbergs) bewegt die sitzende Möwe mit; ist der Platz nicht mehr `usable()`, fliegt sie ab.
- **Prüfung** (`FlockCheck`, Abschnitt Glacier): Kondore bleiben zwischen 110 und 700 m über Grund, sind die Hälfte der Zeit im Kreis und steigen dort mit 1,7 m/s, schlagen fast nie; eine Möwe sitzt 9 min auf einem treibenden Eisberg und treibt mit; nachts sind die Kondore fort.

## Wattvögel (seit Rungholt Phase 8)

Ein zweiter Kern neben `Flock`: `Waders` lässt die Vögel des Watts nach der Tide leben. Die Arten sind Datensätze (`WaderSpecies`), eine neue Art ist ein neuer Eintrag in `WaderSpecies.ALL` [G]:

| Art | Länge / Spannweite | frisst | Gruppen | Flucht | da |
|---|---|---|---|---|---|
| Alpenstrandläufer | 0,18 / 0,38 m | am Saum, 0–25 m von der Kante, stochert 3/s, mag Schlick | 200–3000, wendet im Schwarm | 50 m | ganzjährig, im Juni/Juli wenige |
| Austernfischer | 0,42 / 0,83 m | weiter oben, 15–250 m, stochert | 20–200 | 90 m | ganzjährig |
| Säbelschnäbler | 0,44 / 0,77 m | watet bis 12 cm, säbelt | 20–250 | 120 m | April bis Oktober |
| Brandgans | 0,62 / 1,10 m | seiht im Schlick | 10–120 | 150 m | ganzjährig |
| Silbermöwe | 0,60 / 1,40 m | überall, pickt | 5–60 | 70 m | ganzjährig |
| Lachmöwe | 0,38 / 1,00 m | am Wasser, braune Kappe März bis Juli | 10–150 | 60 m | ganzjährig |
| Ringelgans | 0,58 / 1,15 m | weidet auf der Salzwiese | 50–600 | 200 m | Oktober bis April |

- **Die Welt** (`Waders.World`): Grund, Wassertiefe, Schlick (0..1), Salzwiese und die Stunden seit dem nächsten Hochwasser. Dazu `revier` (wo gefressen wird), `roosts` (Hochwasserrastplätze), `pastures` (Weide der Gänse), `disturbers` ({x, z, Stärke}).
- **Fressen bei Ebbe**: Jede Gruppe sucht unter 24 Orten den besten nach dem Abstand zur Wasserkante ihrer Art, der Watttiefe und dem Schlick; sie läuft langsam, die Vögel stochern und picken. Kommt die Flut in die Gruppe, weicht sie aus, läuft nach oder fliegt ein Stück.
- **Vor der Flut fliegen alle auf**: etwa 1,3 bis 1,8 h vor Hochwasser fliegt jede Gruppe zum Rastplatz (Möwen eine halbe Stunde später), kommt 1,5 bis 2,5 h danach zurück. Gerastet wird dicht, in den Wind, auf dem Trockenen; Möwen und Gänse rasten auch nachts.
- **Schwarm mit Umschlag**: Ein Störer (oder `flush(x, z)`) scheucht die Gruppe auf. Alpenstrandläufer wenden dann eine bis zwei Minuten über dem Watt: Ein Führungspunkt zieht Schleifen, jeder Vogel folgt ihm mit einer Verzögerung nach seinem Platz im Schwarm, die Neigung kommt aus der Querbeschleunigung. So neigt sich der Schwarm in Wellen, zeigt Rücken (dunkel) oder Bäuche (hell). Dann landen sie wieder.
- **Netz**: `WaderMesh` baut jede Art aus ihren Zahlen (Rumpf, zwei Flügel mit Handschwingen, Beine, Hals mit Kopf und Schnabel, gebogen nach `billCurve`), mit Brustband, Kappe, Bauchfleck im Brutkleid; nah bis 1 304 Dreiecke, fern weniger als 60 %. Der Kopf (Teil 4) nickt zum Stochern.
- **Zahl** nach `Waders.Quality` (LOW 0,35 bis ULTRA 3 × Grundzahl, Mittel 1 344 im September) und dem Monat (`presence`).
- **Bild**: `gl/WaderGl` zeichnet eine Instanz je Vogel (14 Floats), je Art Sommer- und Winterkleid, nah und fern; `wader.vert` biegt Flügel, Beine und Kopf, Licht aus `gull.frag`.

- **Vogelzug** (seit Rungholt Phase 9): im Frühjahr (März bis Mai, nach Nordost) und Herbst (September bis November, nach Südwest) ziehen Ringelgänse in Keilen von 15 bis 85 Vögeln in 70 bis 180 m Höhe über die Szene, im Hauptmonat etwa alle 30 Minuten (`migration`, `migrationInterval`, `migrationShare`, `skeins()`). Die Keile gehören nicht zu den Gruppen am Boden und nicht in die Zählung; `WaderGl` zeichnet sie mit.

```java
Waders v = new Waders();
v.world = welt;                                  // Grund, Tiefe, Schlick, Salzwiese, Stunden seit Hochwasser
v.revier.add(new Waders.Area(-1000, 200, 2600));
v.roosts.add(new Waders.Area(-817, -129, 140));
v.month = 9; v.windFromDeg = 250;
v.settle();                                      // nach Sprüngen der Uhr
v.step(dt);                                      // je Bild oder in Szenenzeit
waderGl.draw(gl, v, view, light);
```

- **Demo** `demo/WaderDemo` (eigenes kleines Watt `DemoWatt`, Regler für Zeitraffer und Abstand, „Aufscheuchen“); `--bild datei.png` schreibt die Arten nah, einen Schwarm und eine Karte. **Prüfung** `demo/WaderCheck`: Arten, Netze, eine Tide (fressen bei Ebbe in ihrem Abstand zur Kante, keiner tiefer als er waten kann, stochern, vor der Flut alle auf), Störer und Schwarm mit Umschlag, Wiederholbarkeit, Zeit (0,4 ms je 5 s für 1 344 Vögel).

## Benutzung

```java
Flock f = Flock.beaufort(5);
f.windFromDeg = 250;
f.obstacles.add(new Flock.Obstacle(0, 0, 3.5, -2, 27.6));                 // der Turm
f.perches.add(new Flock.Perch("Geländer", 4.3, 24.1, 0));
f.perches.add(new Flock.Perch("Tonne", () -> buoy.lightPosition(), () -> true));
f.ground = (x, z) -> ocean.heightAt(x, z);
f.onCall = (x, y, z, s, g) -> sound.gull(x, y, z, s);
f.ambientLux = lux;
f.step(dt);                                                             // je Bild

flockGl.draw(gl, f, view, light);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Flock` | Kern: Möwen, Zustände, Flug, Schwarm, Aufwind, Windschatten, Landen, Tag und Nacht, Rufe, Presets |
| `GullMesh` | Netz der Möwe und Flügelstellung je Zustand |
| `gl/FlockGl` | eine Instanz je Möwe, Flügel im Vertex-Shader (`gull.vert`), Licht in `gull.frag`, Selbsttest |
| `demo/FlockDemo` | Fenster mit Reglern und Möwenflug; `--bild datei.png [breite]` schreibt vier Lagen, den Aufwind im Schnitt und die Bahnen von oben |
| `demo/FlockScene` | eigene Szene ohne Dreiecke: See, Turm mit Galerie |
| `demo/FlockCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |
| `WaderSpecies`, `WaderMesh`, `Waders` | Wattvögel: Arten als Datensätze, Netze, Verhalten nach der Tide (seit Rungholt Phase 8) |
| `gl/WaderGl`, `gl/wader.vert` | Wattvögel zeichnen |
| `demo/DemoWatt`, `demo/WaderDemo`, `demo/WaderCheck` | Watt zum Probieren, Demo, Prüfung der Wattvögel |

## Demo und Selbsttest

- `com.dan.flock.demo.FlockDemo` zeigt Möwen um einen Turm; „Möwenflug“ reitet auf einer.
- `com.dan.flock.demo.FlockCheck` prüft das Aufwindfeld, das Stehen im Aufwind, die Geschwindigkeiten, nie im Turm und nie im Wasser, die Schlagfrequenz, Landen und Sitzen, Nacht, Lee bei Bft 9, Wegfliegen bei Bft 11, seed und die Flügelstellung.
- `FlockGl.selfCheck` rechnet die Flügelspitzen auf der Grafikkarte und vergleicht mit dem Prozessor.

## Grenzen

- Aufwind und Windschatten sind Näherungen nach der Form eines Zylinders, kein Strömungsfeld.
- Fischen und Fressen sind nur Haltungen: gründeln, tauchen, picken. Es gibt keine Beute.
- Kein Gefieder im Wind, keine einzelnen Federn; die Flügel haben zwei Glieder.
- Rufe sind Ereignisse ohne Klang; den macht ein anderes Package.
- Wattvögel: Futter ist eine Bewertung des Orts, keine Beute; die Gruppen gehen sich nur aus dem Weg, wenn sie Plätze suchen. Der Schwarm wendet um einen Führungspunkt, nicht nach Nachbarn je Vogel (bei 3 000 Vögeln zu teuer).
