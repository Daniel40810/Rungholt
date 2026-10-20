# com.dan.fracture – Bruch und Starrkörper

**Stand: Engine (Phase 6).** Die Kalbung mit ihr folgt in Phase 7 (Werkbuch Glacier, Abschnitt 9).

Ein Körper bricht, weil die Spannung die Festigkeit übersteigt – nicht nach Drehbuch. Das Package kennt kein Eis, kein Wasser und keinen Fels: Dichte, Festigkeit, Boden und die Höhe des Wassers kommen von außen. Darum taugt es genauso für eine Gletscherfront, einen Felssturz, eine Staumauer, ein einstürzendes Haus oder einen Eisgang. Der Kern braucht nur Java 21; die Demo zeichnet in Java2D und braucht keine Grafikkarte.

Zum Herauskopieren reicht `src/com/dan/fracture/`.

## So arbeitet es

1. **Zellen.** `addSolid(shape, n, density, strength)` zerlegt einen Umriss in rund n Zellen (3D-Voronoi). Die Saaten folgen der Dichte (dichter, wo der Bruch erwartet wird) mit einem Mindestabstand gegen Splitter. Jede gemeinsame Fläche zweier Zellen trägt eine Festigkeit: Zug, Druck und Schub nach Mohr-Coulomb aus dem `Material`, örtlich mal `strength` (etwa an einer Spalte), dazu ±20 % Streuung.
2. **Spannung.** Die Zellen sind starr und federn über ihre Flächen (Rigid-Body-Spring-Modell nach Kawai): eine Feder für den Versatz in der Flächenmitte (E·A/d), eine für die Verdrehung (E·I/d). Weil Voronoi-Flächen senkrecht auf der Verbindung der Saaten stehen, ergibt ein gleichmäßiger Spannungszustand genau σ·n auf jeder Fläche – die Handrechnung steckt im Netz. Gelöst wird mit vorkonditionierten konjugierten Gradienten ohne Matrix, mit Warmstart. Lasten: Eigengewicht, Auftrieb des Teils unter Wasser, Lasten von Hand, ein Druck auf Randflächen, und das Gewicht und die Stöße aufliegender Stücke.
3. **Auflager.** *Anker* binden eine Randfläche an festen Grund jenseits von ihr, mit derselben Feder und Festigkeit (mal einem Faktor, etwa für ein Fundament) – auch der Fuß kann abreißen. *Gehaltene Zellen* stehen ganz fest (der Gletscher weit hinter der Front).
4. **Riss.** Liegt eine Fläche über ihrer Festigkeit, bricht sie, und je `crackStep` (0,08 s) die nächsten; je stärker überlastet, desto mehr auf einmal. `onCrack` meldet den wachsenden Weg mit der freigesetzten Energie – Sekunden, bevor ein Stück frei ist.
5. **Lösen.** Was über keine heile Fläche mehr mit einem Auflager verbunden ist, wird ein `Piece` (`onBreak`).
6. **Starrkörper.** Masse und Trägheit aus den Zellen (Hauptachsen), Schwerkraft, Auftrieb aus dem untergetauchten Volumen jeder Zelle, Widerstand im Wasser. Stöße mit dem Boden, dem haltenden Körper und anderen Stücken nach dem Impulsverfahren mit Reibung, Stoßzahl, Warmstart, einer Lagekorrektur, die keine Energie zufügt (split impulse), und einem Saum gegen Durchtunneln schneller Stücke. Ruhe und Schlaf; auf dem Wasser nach `sleepAfter` (30 s) Übergabe (`onSettle`, für buoyancy in Phase 9).
7. **Zerschellen.** Schlägt ein Stück aus mehreren Zellen hart auf, rechnet es die Spannung im Stück aus dem Stoß (Verzögerung über die Stoßdauer) und bricht womöglich weiter.

Fester Schritt 1/120 s. Gleicher `seed`, gleiche Zellen, gleicher Riss, gleicher Sturz.

## Schnittstellen

| Eingang | Bedeutung |
|---|---|
| `Ground ground` | Höhe des Bodens am Ort (NaN: keiner) |
| `Water water` | Höhe des Wassers am Ort zur Zeit t (NaN: keins) |
| `Density`, `Strength` | Dichte der Zellen, Festigkeit als Faktor, je Ort |
| `Material material` | Dichte, E, Zug, Druck, Reibungswinkel, Streuung, Reibung, Stoßzahl |

| Ereignis | Bedeutung |
|---|---|
| `onCrack(path, energy, broken)` | Riss läuft: Flächenmitten des Wegs, Energie (J), ob ein Stück frei ist |
| `onBreak(piece, volume, mass)` | ein Stück ist frei |
| `onImpact(x, y, z, volume, speed, water)` | Aufschlag: ins Wasser je Schritt mit dem verdrängten Volumen, auf festen Grund mit dem Volumen des Stücks |
| `onSettle(piece)` | ruht auf dem Wasser, Übergabe |

## Für die App (Phase 7)

- `prepare(shape, cells, density, strength)` zerlegt einen Körper ohne die Welt zu sperren (in einem eigenen Faden); `add(solid)` setzt ihn in die Welt, `remove(solid)` nimmt ihn wieder heraus.
- `poses()` gibt je Stück Drehung und Lage nach dem letzten Schritt, ohne Sperre (fürs Zeichnen in einem anderen Faden); ebenso `pieceCount()`, `awakeCount()`, `solidCount()`. Das Bild wartet so nie auf einen Schritt.
- `asyncSolve = true` rechnet die Spannung in einem eigenen Faden („fracture-Spannung“): `step` übernimmt Lasten und Flächen, das Gleichungssystem läuft nebenher, ausgewertet wird im ersten Schritt danach. Die Stücke fallen weiter, der Riss geht weiter, sobald die Rechnung fertig ist. Aus (Vorgabe) für Prüfungen und Bildfolgen, weil das Ergebnis dann von der Rechenzeit abhängt.
- `Solid.meltCells(test)` schmilzt Zellen nach einem eigenen Test (die Kerbe nach Überlappung, nicht nach dem Mittelpunkt), `Solid.weaken(x, y, z, r, k)` macht einen Bereich fester oder schwächer, `Solid.version()` zählt jede Änderung der Zellen (für Netze), `brokenBetween(i, j)` sagt, ob zwischen zwei Zellen ein Riss ist.
- `maxPieces`: ab so vielen Stücken zerschellt keins mehr; `dissolveBelow`: Stücke unter so viel m³ verschwinden nach 4 s im Wasser (beides aus in der Vorgabe). Nur Zellen an der Oberfläche eines Stücks oder Körpers werden auf Berührung geprüft.
- Stücke, die beim Aufschlag zerbrechen, tragen `parent()`; `Piece.outer(face)` sagt, ob eine Fläche außen liegt.

## Für die App (Phase 10)

- `GroundCache`: ein teurer Boden (Gletscher mit Spalten) in Kacheln von 64 × 64 Zellen, einmal abgefragt, dann gemischt. Beim Fallen fragt jede Zelle an der Oberfläche den Boden in jedem Schritt ab; mit den Kacheln sinkt das Erkennen der Berührungen in Glacier von 48 auf 2 ms. `clear()`, wenn sich der Boden ändert.
- `forgetDryAfter`: Stücke, die so lange (s) trocken schlafen, sind weg (auf dem Eis gehen sie in ihm auf). `Piece.wetTime()`: wie lange ein Stück im Wasser liegt.

## Strömung (seit Glacier Phase 13)

`world.current = (x, z, out) -> …` gibt die Strömung des Wassers an (m/s nach `out[0]`, `out[1]`). Der Widerstand rechnet dann gegen das strömende Wasser: Ein Fluss trägt Stücke mit. Ohne `current` steht das Wasser wie bisher. Die Ruptura gibt die Strömung in ihrer Rinne an; ebenso ginge ein Dammbruch oder ein Eisgang.

## Presets

| Preset | Stoff |
|---|---|
| `Fracture.ice()` | Eis im Labor: 917 kg/m³, Zug 1 MPa, Druck 5 MPa, φ 30° (Kennwerte nach Wikipedia „Ice“: Zug 0,5–1,8 MPa, Druck 2,4–5,9 MPa; stark abhängig von Temperatur, Korn und Dehnrate) |
| `Fracture.glacier()` | Gletschereis im Großen [G]: 900 kg/m³, Zug 400 kPa, Druck 2,5 MPa (seit Phase 7; vorher 150 kPa / 1 MPa) – Spalten und Fehlstellen machen eine 70-m-Wand schwächer als eine Probe. Eine freie Wand steht unter Zug von etwa 0,2–0,25 ρgH (bei 60 m rund 120 kPa); mit 150 kPa brach der ganze Abschnitt auf einmal. Wo sie wirklich schwach ist, sagen die Spalten über `Strength` |
| `Fracture.rock()` | Granit: 2 700 kg/m³, Zug 10 MPa, Druck 150 MPa, φ 45° |
| `Fracture.concrete()` | Beton: 2 400 kg/m³, Zug 3 MPa, Druck 30 MPa, φ 37° |

## Klei (seit Rungholt Phase 12)

`Material.klei()` / `Fracture.klei()`: nasser Klei eines mittelalterlichen Seedeichs [G]: 1 900 kg/m³, E 30 MPa, Zug 15 kPa, Druck 150 kPa, φ 25°, Streuung 30 %. Ein Deich aus Klei hält den Wasserdruck allein; er bricht, wenn das überlaufende Wasser die Binnenböschung ausspült (`Solid.melt`) und der Rest durchnässt (`Solid.weaken`). In Rungholt bricht so ein Abschnitt von 50 m mit 150 Zellen in rund einer Minute in gut hundert Stücke.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Fracture` | die Welt: Presets, Schritt, Riss, Lösen, Kräfte, Zerschellen, Ereignisse |
| `Material` | der Stoff, Kohäsion aus Mohr-Coulomb |
| `Shape` | Umrisse: Quader, konvex aus Ebenen, Höhenfeld mit Grundriss |
| `Poly` | konvexes Polyeder: schneiden, Masse, Trägheit, Ecken, Tiefe eines Punkts |
| `Voronoi` | Zerlegung in Zellen |
| `Solid` | der haltende Körper: Zellen, Flächen, Anker, Lasten, Spannung, Schmelzen |
| `Stress` | Federnetz und Löser |
| `Piece` | ein Stück als Starrkörper |
| `Contacts` | Erkennen und Lösen der Stöße |
| `demo/FractureDemo` | Vorführung mit drei Fällen; ohne Fenster `--bild datei.png [breite]` |
| `demo/FractureScene` | die drei Fälle und ihre Zeichnung in Java2D |
| `demo/FractureCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Die Demo

- **Pfeiler unter Last:** eine Eissäule 1,2 × 1,2 × 6 m, ein Felsblock (36 t) fällt aus einstellbarer Höhe. Ab etwa 3 m zerdrückt der Stoß den Fuß.
- **Überhang kippt ins Wasser:** eine Gletscherfront, 16 m hoch auf einem Sockel 6 m unter Wasser. Das Wasser frisst sie von unten an; bei etwa 8 m Unterspülung reißt sie oben hinter der Kante (Handrechnung für den Überhang allein: √(f_t h / 3ρg) ≈ 7 m), der Riss läuft zwei Sekunden, dann kippt der Turm ins Wasser.
- **Fels rollt den Hang hinab:** ein Findling aus verwittertem Fels rollt einen 31°-Hang hinab, bricht an einem Buckel und zerschellt an einem Block, der hält.

„Spannung zeigen“ färbt jede Zelle nach der höchsten Auslastung ihrer Flächen (blau ruhig, rot bricht).

## Prüfung (Auszug)

- Säule 2 × 2 × 10 m Eis: Bruchlast auf 0,0 % an σc·A − G (Werkbuch: 10 %); Kraft im Schnitt auf 0,3 % an der Handrechnung.
- Kragbalken: Moment im Schnitt auf 0,1 %, größte Zugspannung auf 2 % an 3ρgL²/h.
- Kragplatte: hält mit 0,75 · L_c, bricht mit 1,3 · L_c; der Riss meldet sich 3 s vor dem Bruch.
- Fall aus 50 m in 3,19 s; Reibung am 20°-Hang auf 1 %; Eiswürfel schwimmt mit 0,917 unter Wasser.
- 200 Würfel auf einem Haufen: keine Ecke tiefer als 2,4 cm im Boden, keine Energie aus dem Nichts.
- 1 000 wache Stücke im See: um 3 ms je Schritt auf dem Prozessor (Werkbuch: unter 3 ms).

## Grenzen

- Keine finiten Elemente (F8 A): starre Zellen mit Federn; die Querdehnung fehlt (ν = 0), Druckversagen ist Schub nach Mohr-Coulomb auf der ungünstigsten Fläche.
- Linear-statisch: Spannungswellen im Körper gibt es nicht; ein Stoß wirkt über die Stoßdauer geglättet.
- Berührungen über Ecken und Kantenmitten, nicht über Flächen gegen Flächen.
- Ohne Kreiselglied: freie Stücke taumeln nicht.
- Die Rechnung der Spannung kostet bei 600 Zellen rund 10 bis 15 ms; sie läuft nur, wenn sich etwas geändert hat, höchstens alle 0,08 s. Bei 900 Zellen (ein Abschnitt der Front) gibt es Spitzen bis rund 190 ms; mit `asyncSolve` liegen sie im eigenen Faden und halten weder die Physik noch das Bild auf.

## Grenzen für eine App (seit Glacier Phase 16)

`budget` (0 = keine): höchstens so viele Stücke zugleich. Darüber gehen zuerst ruhende Stücke (die am längsten ruhen), dann die kleinsten im Wasser, nie eines, das jünger als 2 s ist; `budgetRemoved()` zählt sie. Zusammen mit `maxPieces` (ab dann zerschellt nichts mehr), `dissolveBelow` und `forgetDryAfter` hält das die Physik in einem festen Rahmen, auch wenn ein ganzer Damm einstürzt.

Seit dem Dauerlauf (Glacier Phase 16) gilt außerdem:

- **`remove(Solid)`:** Ein schlafendes Stück merkt sich, auf welchem Körper es liegt. Geht der Körper, wacht es auf und fällt. Vorher schwebte es weiter und hielt den alten Körper mit allen Zellen im Speicher.
- **`maxContacts` (Vorgabe 40 000):** höchstens so viele Berührungen je Schritt. Je Zellpaar bleiben die 6 tiefsten; die übrigen gehen sofort zurück in den Vorrat. Nach einer Spitze schrumpfen Vorrat und Tabellen wieder. `contactPeak()`, `contactsCapped()` und `contactPool()` zeigen, wie nah die Szene an der Grenze ist.
- **Anlass:** Stürzte ein zweiter Damm auf die Trümmer des ersten, stieg die Zahl der Berührungen auf Millionen je Schritt. Der Vorrat wuchs auf über 2 GB und gab nichts mehr her. Glacier nimmt seitdem auch die Trümmer weg, die ein neuer Damm überbaut (`Ruptura.buried`).
