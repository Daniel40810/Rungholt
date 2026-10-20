# com.dan.ice – Eis als Material und als Körper

**Stand: Gletscher (Phase 5).** Material seit Phase 4, der Gletscher als Körper seit Phase 5 (Werkbuch Glacier, Abschnitt 9). Zellen mit Festigkeit für `fracture` folgen in Phase 6.

Eis, das wie Eis aussieht – nicht wie Glas und nicht wie Gips: weiß am Rand, blau in der Tiefe, Risse, die in der Sonne blitzen. Das Package kennt keine App und kein anderes Package; der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` zusätzlich `FStyle.jar`.

Zum Herauskopieren reicht `src/com/dan/ice/`.

## Was das Material rechnet

- **Absorption je Farbkanal** (Beer-Lambert) mit den Werten von reinem Eis: Rot 0,25 /m, Grün 0,035 /m, Blau 0,006 /m [G, Bandmittel nach Warren & Brandt 2008 und Picard et al. 2016]. Rot ist nach 2,8 m halbiert, Grün nach 20 m, Blau erst nach 116 m.
- **Dicke je Bildpunkt** aus einem Rückseiten-Durchgang: Abstand zur Rückseite minus Abstand zur Vorderseite.
- **Streuung durch Luftblasen**, Anteil 0..1 logarithmisch auf 0,05 bis 200 /m, Asymmetrie g = 0,8. Firn streut so stark, dass er weiß ist; altes, gepresstes Eis kommt blau zurück.
- **Kubelka-Munk** für Reflexion R und diffuse Durchlässigkeit T einer Schicht (K = 2α, S = ¾σ(1 − g)), stabil auch für 500 m Dicke. Dazu der ungestreute Anteil, durch den man durch klares Eis hindurchsieht.
- **Verwitterte Kruste**: eine zweite, stark streuende Schicht über dem Eis. Sie ist fleckig, an steilen, frisch gebrochenen Flächen dünn und in Schmelzrinnen weggewaschen.
- **Risse** als Zellgrenzen im Volumen (Kachel 64³, nahtlos). 2 bis 8 Proben längs des gebrochenen Strahls, je nach Stufe. Rissflächen streuen weiß und spiegeln die Sonne (Totalreflexion, Blitzen).
- **Licht von hinten**: Was an besonnten Flächen eintritt, kommt im Schatten gefiltert und blau heraus.
- **Oberfläche**: Moränenbänder, Schnee auf flachen Stellen, Schmelzwasserfilm (glatt, Fresnel), Glitzer der Kristallflächen.
- **Unter Wasser**: Auf dem Weg durchs Wasser wird das Licht geschluckt und vom trüben Wasser überstrahlt, der Fuß eines Eisbergs wird türkis.

## Was der Gletscher rechnet (Phase 5)

Eingabe ist ein Höhenraster (`Glacier.Grid`): Höhe, Grund und Art jeder Zelle (Fels, Wasser, Eis). Das Package kennt keine Höhendaten und keine App; die App gibt das Raster aus ihrem Gelände.

- **Oberfläche:** die gemessene Höhe über dem Eis, geglättet. An der Front wird sie auf `frontHeight` angehoben, denn Höhendaten glätten die Wand zur Rampe. Die Anhebung klingt über 1,5 km ab und läuft zu den Rändern aus.
- **Fließen:** das geglättete Gefälle hinab, in der Mitte am schnellsten (Pfropfen), zur Front hin schneller. `speed` gibt die Geschwindigkeit in der Mitte an der Front an (m/Tag).
- **Spalten aus der Dehnung:** Aus dem Fließfeld folgen die Dehnungsraten. Wo die größere Hauptdehnung eine Schwelle übersteigt, reißt das Eis quer zu ihr:
  - Querspalten, wo das Eis schneller wird;
  - schräge Randspalten im Scherband an den Flanken;
  - Fächerspalten, wo es sich an der Front ausbreitet.
- **Seracs:** Wo beide Hauptdehnungen ziehen, im Eisfall und in den letzten 600 m vor der Front kreuzen sich zwei Scharen. Die Türme sind oben gerundet. Jede Spalte reißt nur stückweise (en échelon), Breite und Tiefe wechseln längs der Spalte.
- **Front:** Linien aus der weichen Eismaske, wo Wasser daneben liegt (Marching Squares, geglättet, alle 2 m). `GlacierWall` baut daraus die Wand:
  - senkrechte Furchen;
  - Abbruchnarben mit ausgefranster Unterkante;
  - Überhang oben;
  - Schmelzkerbe an der Wasserlinie (bis 4,5 m tief) [G].
- **Vorstoß:** `setAdvance(m)` verschiebt die Front längs ihrer Normalen. Vor rückt sie nur ins Wasser, nicht aufs Land.
- **Rinnen:** `addChannel` schneidet Schmelzbächen ihr Bett. `downhill` gibt den Weg des Wassers über die Oberfläche.
- **Kollision:** `surface(x, z)` mit Spalten, `covers(x, z)`.
- **Netze:** `GlacierTiles` liefert Kacheln von 256 m in fünf Stufen (2 bis 32 m). Zum Wasser enden sie auf der Frontlinie, zum Fels tauchen sie unter das Gelände; Schürzen decken die Risse zwischen Stufen. Jede Ecke trägt eine Verdeckung: Im Spaltengrund ist es dunkler, das Licht kommt dort blau durch die Eiswände.
- **Grafikkarte:** `gl/GlacierGl` baut die Netze in einem eigenen Faden und lädt je Bild höchstens vier hoch. Bis die feine Stufe fertig ist, bleibt die grobe stehen.

## Ein Abschnitt der Front (Phase 7)

`FrontBlock.near(glacier, x, z, breite, tiefe, unterWasser)` legt einen Quader an die Front nächst (x, z): längs der Front `breite`, 12 m davor bis `tiefe` ins Eis, vom Grund (höchstens `unterWasser` unter dem Spiegel) bis über die Oberfläche. Er beantwortet nur Fragen in Zahlen, die die App an `fracture` weitergibt:

- `inside(x, y, z)`, `top`, `bottom`, `bounds()`, `planes()`: wo Eis ist (mit Spalten oben, ohne was schon abgebrochen ist: `removeBefore`);
- `front(x, z)`: die Front als senkrechte Ebene an dieser Stelle, damit Zellen an der Wand enden und nicht erst am Quader 12 m davor. `near` schiebt den Abschnitt längs der Front, bis sie über seine ganze Breite reicht (an den Enden einer Frontlinie, am Ufer, ragte er sonst über die Front hinaus);
- `strength(x, y, z)`: offene Spalten schwächen die oberen 30 m bis auf 0,4; dazu Bänder (`Weakness.behind` hinter einem Turm, `Weakness.side` an seinen Flanken);
- `density(x, y, z)`: feine Zellen an der Front und um die Wasserlinie, grobe dahinter;
- `cut()`: der Ausschnitt fürs Zeichnen. `IceGl.setCuts` nimmt bis zu acht; Kacheln und Wand (`Instance.cut`) werfen in ihnen ihre Fragmente weg, die Zellen füllen den Platz.

## Ein Eisdamm (seit Glacier Phase 13)

`IceDam` ist ein Gletscher, der an ein Ufer stößt und einen Seearm abriegelt, als Körper für `fracture`. Er entsteht aus Gelände (`Ground`), dem Gletscher, an dem er anliegt (`Cover`), und der Rinne als Linie von Mündung zu Mündung. Er beantwortet dieselben Fragen wie ein Abschnitt der Front: `inside`, `top`, `bottom`, `bounds`, `planes`, `density`, `strength`.

- **Wo Eis ist:** im Quader längs der Rinne, wo der Grund unter `shoreMax` liegt und kein Gletscher steht.
- **Krone:** mit Spalten, zum Ufer hin dünner, über der Brücke dicker [G].
- **Dichte:** feine Zellen an der Rinne, dort entsteht der Tunnel.
- **Festigkeit:** Spalten schwächen die oberen 12 m; die Brücke ist um 35 % fester.
- **Abfragen an der Rinne:** `channel(x, z)` gibt Bogenlänge, Abstand quer, Sohle und Richtung; `at(s)` gibt den Punkt der Rinne bei s.
- **Abnehmer:** die Ruptura am Perito Moreno; ebenso jeder Eisstausee, etwa ein Gletschersee hinter einer Moräne mit Eiskern.

## Gebrauch

```java
IceGl ice = new IceGl();
ice.init(gl);
IceMesh m = IceShapes.iceberg(seed, 7, 0.85, 3);
double wl = m.waterline(Ice.iceberg().submergedFraction(1000));     // 90 % unter Wasser
IceGl.Body body = ice.upload(gl, IceShapes.shifted(m, 0, -wl, 0));
IceGl.Instance berg = new IceGl.Instance(body, Ice.iceberg()).place(x, waterLevel, z, yaw);
// je Bild, in das gebundene HDR-Ziel (dessen Tiefe gilt):
ice.draw(gl, List.of(berg), view, light);     // view: viewProj, eye, reversedZ, Größe; light: Sonne, Mond, Himmel, Dunst, Wasser
```

Ein Gletscher:

```java
Glacier g = new Glacier(new Glacier.Grid(nx, nz, x0, z0, cell, height, bed, kind), waterLevel, 74, 2.0, seed);
GlacierGl gg = new GlacierGl(ice, g);
gg.prime(gl);                                   // gröbste Stufe und Wand sofort
// je Bild:
List<IceGl.Instance> list = new ArrayList<>(gg.instances(gl, eye, viewProj));
list.addAll(icebergs);
ice.draw(gl, list, view, light);
gg.setAdvance(a);                               // Vorstoß, wird nebenher neu gebaut
```

Das Licht kommt vorbelichtet von außen, wie bei den anderen Packages. Ist ein Rundumbild des Himmels gesetzt (`skyTexture`, Abbildung u = atan(x, −z)/2π + 0,5 wie bei `ocean`), wird es für die Spiegelung genutzt, sonst ein Verlauf aus `skyTop` und `skyHorizon`.

## Presets

| Preset | Blasen | Risse | Kruste | Film | Wofür |
|---|---|---|---|---|---|
| `firn()` | 90 % | 10 % | 100 % | 5 % | junger Schnee, weiß |
| `glacier()` | 50 % | 50 % | 60 % | 20 % | Gletscherfront, Moräne 15 % |
| `blue()` | 10 % | 20 % | 5 % | 60 % | Spaltentiefe, Fuß eines Eisbergs |
| `iceberg()` | 30 % | 35 % | 30 % | 70 % | geschwommen, nass |
| `tongue()` | 55 % | 45 % | 80 % | 25 % | Oberfläche einer Zunge, Mittelmoränen alle 180 m |
| `front()` | 42 % | 55 % | 30 % | 45 % | Wand der Front, frisch gebrochen |

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Ice` | Kern: Presets, offene Werte, Qualitätsstufen, `describe()` |
| `IceOptics` | Optik ohne Grafikkarte, Zeile für Zeile wie `gl/ice_common.glsl` |
| `IceNoise` | Risskachel 64³ (R: Nähe zur Fläche, GBA: deren Normale) |
| `IceMesh` | flaches Dreiecksnetz, Volumen, Volumen unter einer Ebene (genau), Wasserlinie, Schwerpunkt |
| `IceShapes` | Block mit Fasen, Eisberg aus Ikosaederkugel mit Bruchebenen |
| `Glacier` | Gletscher aus einem Höhenraster: Oberfläche, Fließen, Dehnung, Spalten, Seracs, Front, Vorstoß, Rinnen |
| `GlacierTiles` | Kacheln der Oberfläche in fünf Stufen, mit Verdeckung je Ecke |
| `GlacierWall` | Wand der Front mit Furchen, Narben, Überhang, Schmelzkerbe |
| `gl/IceGl` | Rückseiten-Durchgang (R32F) für geschlossene Körper, Vorderseiten ins Ziel des Aufrufers; Shader als Ressourcen |
| `gl/GlacierGl` | Kacheln und Wand auf der Grafikkarte, Bau im eigenen Faden |
| `demo/IceDemo` | Fenster: Block 10 m, Eisberg, Regler; `--bild datei.png [breite] [fotos]` ohne Fenster |
| `demo/GlacierDemo` | Fenster: eine Zunge in einem ausgedachten Tal (Eisfall, Front, See), Blicke, Regler für Spalten, Vorstoß, Sonne; `--bild datei.png` ohne Fenster |
| `demo/IceCheck` | Prüfung ohne Fenster (Optik, Netze, Risse, Shadervertrag, Gletscher), endet mit „Alles in Ordnung.“ |

## Qualitätsstufen

| Stufe | Proben durch die Risse |
|---|---|
| LOW | 2 |
| MEDIUM | 4 |
| HIGH | 6 |
| ULTRA | 8 |

## Grenzen

- Die Absorptionswerte sind Bandmittel und gerundet [G]. Die Messungen für Blau streuen um den Faktor zehn (Warren 2006 gegen Picard 2016).
- Die Dicke kommt aus dem vordersten Paar von Vorder- und Rückseite. Ein Körper hinter einem anderen sieht die Rückseite des vorderen nicht.
- Keine Brechung des Hintergrunds, die Durchsicht nimmt den Himmel in Richtung des gebrochenen Strahls.
- Kein Schatten von Türmen auf das Eis; die Verdeckung je Ecke ersetzt ihn im Spaltengrund.
- Das Fließfeld ist eine Näherung aus dem Gefälle, keine Rechnung der Eisdynamik [G]. Die Richtungen der Spalten folgen daraus, ihre Muster sind gestaltet.
- Zwischen Kacheln verschiedener Stufe kann eine Stufe von einigen Metern stehen, wo die grobe Stufe eine Spalte anders trifft; die Schürze deckt das Loch, nicht die Kante.

**Seit Glacier Phase 12:** `FrontBlock.near` nimmt an einer gewellten Front den besten Versuch, wenn die Enden höchstens 12 % der Breite (mindestens 8 m) neben der Front liegen.
