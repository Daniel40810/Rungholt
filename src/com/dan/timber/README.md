# com.dan.timber – Wikingerzeitliche Holzbauten

Das Paket baut Häuser, Zäune, Bohlenwege, Palisaden und Brücken aus Holz, Flechtwerk, Lehm und Reet und zeichnet sie zu Hunderten. Der Kern braucht nur Java 21: er erzeugt die Netze der sechs Haustypen in drei Detailstufen, die Bauwerke entlang von Linien, wählt je Bild die Stufe nach der Entfernung und liefert Kollisionskästen und Rauchlöcher. Der Ordner `gl/` zeichnet alles mit JOGL (OpenGL 4.3, instanziert); Maserung, Beilspuren, Fugen, Flechtwerk und Reet entstehen im Fragment-Shader, nicht in Texturen. Das Paket kennt weder Haithabu noch andere Pakete; den Boden gibt der Benutzer über eine kleine Schnittstelle hinein.

Zum Herauskopieren reicht `src/com/dan/timber/`. Nur `demo/TimberDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Sechs Haustypen als Datensätze** (`HouseType`): Hallenhaus 17,5 × 7 m mit Schrägstützen, Wohnhaus 12 × 5 m, Spaltbohlenhaus 9 × 4,5 m, Uferhaus 17 × 3,5 m, Grubenhaus 4 × 3 m (0,7 m eingetieft, Dach bis fast zum Boden), offener Werkstattschuppen 6 × 4 m. Je Typ Wandhöhe, Dachneigung, Wandart (Flechtwerk mit Lehm, Spaltbohlen, bloßes Flechtwerk), Türen nach Seite und Lage, Rauchloch.
- **Bauteile:** Pfosten (eingegraben), Schwellen, Rähm, Spaltbohlen, Flechtwerk mit Lehmputz, Reetdach mit Firstsoden, Türblatt mit Leisten, Rahmen und Schwelle, Rauchloch im Giebeldreieck, Schrägstützen.
- **Drei Stufen:** nah mit allen Teilen (Hallenhaus 1 228 Dreiecke), mittel mit glatten Wänden und vier Eckpfosten (108), fern als Kasten mit Dach (18). `Timber.select` wählt nach Abstand minus halbem Radius gegen `Quality.near/mid/far` und lässt Häuser außerhalb des Blickkegels aus.
- **Häuser** (`House`): Typ, Ort, Firstrichtung in Grad von Nord im Uhrzeigersinn, Streckung in Länge und Breite (0,8 bis 1,25), Zustand 0 = neu … 1 = verwittert, Zufall. Eine Instanz sind acht Zahlen.
- **Bauwerke** (`Structures`): Flechtzaun (Pfosten alle 0,9 m), Bohlenweg (Bohlen alle 0,27 m auf Längshölzern), Palisade (Spitzpfähle alle 0,29 m), Brücke/Landesteg (Pfahlpaare alle 2,5 m bis zum Grund, Joch, Längsträger, Deck). Alle folgen `Structures.Ground`. Für eine ganze Siedlung sammelt `Structures.Set` sie in Kacheln zu 128 m mit feinem und grobem Netz.
- **Hof und Ufer (seit Haithabu Phase 5):** `Structures.rack` (Fischgestell mit getrockneten Dorschen, Werkstoff `FISH`), `Structures.well` (Brunnenkasten aus Bohlen mit Ziehbrunnen-Schwengel), `Structures.boat` (kleines geklinkertes Boot, am Strand aufgezogen und gekippt); alle drei auch in `Structures.Set` mit grobem Netz, dazu `Set.mesh` für beliebige Netze.
- **Wehrbau (seit Haithabu Phase 7, `Fortification`):** `wallWalk` – ein Wehrgang aus Bohlen auf Pfostenpaaren alle 2,5 m, eine feste Höhe über dem Boden, der ihm folgt (für den Kamm eines Walls, der steigt und fällt); `gate` – ein Torbau im Erdwall: Torgasse mit senkrechten Spaltbohlen gefasst, die Oberkante folgt dem Querschnitt des Walls (flach am Kamm, dann die Flanken), Pfosten alle 2 m, zwei Torflügel nach innen offen, eine Plattform in Höhe des Wehrgangs, auf Wunsch ein Torturm mit Brustwehr aus Spitzpfählen und Bretterdach. Ohne Flügel und Turm ist es ein Durchlass (etwa für einen Bach) mit Steg. Beide mit grober Form; in `Structures.Set` als `wallWalk` und `gate`. Ein Tor beschreibt `Fortification.Gate` (Lage, Richtung, Boden innen und außen, Kamm, Breite, Tiefe, Querschnitt, Plattform).
- **Hafenbau (seit Haithabu Phase 8, `Wharf`):** `platform` – ein breites Bohlendeck auf Pfahlreihen (alle 2,5 m längs, höchstens 2,6 m quer, bis zum Grund, auch unter Wasser); `palisadeTo` – eine Pfahlreihe im Wasser vom Grund bis zu einer festen Höhe mit Gurt; `revetment` – Uferbefestigung aus Pfosten und Bohlen; Waren: `barrel` (Fass mit Bauch und Reifen), `bale` (Ballen aus Tuch), `millstone` (Mühlstein aus Basalt, flach oder gelehnt), `pot` (Specksteintopf). Neue Werkstoffe `STONE` (Basalt), `CLOTH`, `SOAPSTONE`. In `Structures.Set` als `platform`, `palisadeTo`, `revetment`. Am Wasser: `TimberGl.Light.waterLevel` (NaN = aus) macht Pfähle und Bohlen unter dem Spiegel schleimig dunkel, darüber nass und an der Wasserlinie eine grünbraune Algenlinie.
- **Feuerschein (seit Haithabu Phase 10):** `TimberGl.Light.pointPos`, `pointRgb` und `pointCount` nehmen bis zu acht Punktlichter (Feuer, Fackeln) mit 1/d², ohne Schatten; unter der Traufe gedämpft wie die Sonne.
- **Brand (seit Haithabu Phase 15):** `Timber.burn` gibt je Haus einen Brand 0..2. Bis 1 verkohlt das Haus fleckig von unten und vom Dach her, über 1 ist es eingestürzt: ein niedriger, schwarzer Haufen, auch in der Schattenkarte. Der Brand steckt in der Instanz im Zufall (2 · Stufe + Zufall, `unpackBurn`); die Ursache (ein Brand aus `com.dan.hearth` etwa) kennt das Paket nicht.
- **Ruß und Moos (seit Haithabu Phase 5):** Um die Rauchlöcher schwärzt der Shader einen Hof, am First einen Schleier im Reet; alte Häuser (Zustand) mehr. Moos wächst auf alten Dächern an der Nordseite.
- **Abfragen:** `Timber.box(house)` (gedrehter Kasten samt Dach und Stützen, `contains`), `Timber.smokeHoles(house)` (Orte für Rauch), `House.radius()`.
- **Grafik (gl):** ein Aufruf je Typ und Stufe (`glDrawElementsInstanced`), Bauwerkskacheln fein bis 1,5 · `near`, grob bis `far`. Licht über `TimberGl.Light`: Sonne, Mond, Himmel (`skyWeight`), Dunst (Farbe fest oder seit Haithabu Phase 5 aus einem Rundumbild `skyTexture` in Blickrichtung), Nässe (dunkler, glänzender), Wolkenschatten von oben und eine eigene Sonnenschattenkarte (`createShadowTarget`, `sunMatrix`, `drawShadow`, 3×3-PCF). Werkstoffe im Shader: Holz mit Maserung, Beilspuren und Rissen, Bohlenfugen, Lehm mit feinen Rissen und Abplatzern, Flechtwerk, Reet mit Strichen, Lagen, Flicken, Moos auf der Nordseite und dunkler Traufe, Soden. Der Zustand lässt Holz vergrauen und Reet dunkeln.

## Friesenhaus (seit Rungholt Phase 9)

- **Werkstoff `BRICK`**: Backstein im Läuferverband (24 × 7,5 cm), helle Fugen, Steine einzeln getönt, einzelne Klinker dunkler, unten Spritzwasser. Darin gemalt weiße Sprossenfenster alle 2,4 m, 0,95 bis 1,85 m über dem Boden (ohne eigene Geometrie; sie beginnen in jedem Wandstück neu, also nie in einer Tür).
- **Typ `FRIESENHAUS`**: 14 × 7,5 m, Wände 2,4 m aus Backstein (36 cm), Reetdach 50°, Haustür an der Südseite, Stalltür im Ostgiebel, kein Rauchloch; gemauert ohne Pfosten und Rähm. Nah 258 Dreiecke. Für die Hallig Südfall; die Kirche von Rungholt (Phase 11) baut auf demselben Werkstoff auf.

## Kirche aus Backstein (seit Rungholt Phase 11)

- **`Church`** baut eine Dorfkirche der Nordseeküste als ein Netz (rund 190 Dreiecke): Westturm mit Pyramidenhelm aus Schindeln und Wetterstange, Schiff mit Reetdach und Firstsoden, eingezogener Chor, halbrunde Apsis mit halbem Reetkegel, Sockel aus Feldstein, Portale im Süden, Norden und Westen. Maße als `Church.Spec`; `Church.rungholt()` mit 41 m Länge, Schiff 24,5 × 13 m, Turm 7,5 m im Geviert mit 24 m Mauer und 9 m Helm [G]. Örtlich wie die Häuser (+x zum Chor); in der App mit `TimberGl.setSpecial` gezeichnet.
- **Werkstoff `CHURCH`**: Klosterformat 28 × 9 cm im Wendischen Verband (zwei Läufer, ein Binder), dunkle Brandfarben und Sinterbrand, Flecken; gemalt schmale Rundbogenfenster (1,1 × 3,4 m ab 3,2 m Höhe, alle 4,2 m) und oben im Turm Schallöffnungen. **`SHINGLE`**: Eichenschindeln in versetzten Reihen, silbergrau; Schnee liegt darauf wie auf Reet.
- `TimberCheck` prüft Netz, Maße, Turmhöhe, Werkstoffe und Lage von Turm und Apsis.

## Benutzung

```java
Timber timber = new Timber();
timber.quality = Timber.Quality.MEDIUM;
List<House> houses = List.of(
        House.of(HouseType.HALLE, 10, 0.4, -30, 75),
        new House(HouseType.GRUBENHAUS, 25, 0.3, -18, 80, 1, 1, 0.6, 7));
Structures.Set bauwerke = new Structures.Set();
bauwerke.fence(new double[][]{{0, 0}, {30, 0}, {30, 20}}, gelaende::height, 1);
bauwerke.pier(new double[][]{{-5, 10}, {-5, 60}}, 4, 0.9, gelaende::height, 2);

TimberGl gl = new TimberGl();
gl.init(gl4, timber);
gl.setStructures(gl4, bauwerke, 0.4);
// je Bild:
gl.drawShadow(gl4, timber, houses, lightVP, camX, camZ, 120);   // optional, in die eigene Schattenkarte
gl.draw(gl4, timber, houses, view, light);
String hud = gl.info();     // „32 Häuser mittel, 465 fern … · Bauwerke 2 Kacheln fein, 54 grob“
```

## Demo und Selbsttest

- `com.dan.timber.demo.TimberDemo` mit drei Szenen: `typen` (die sechs Typen nebeneinander, davor Zaun, Bohlenweg, Palisade, Brücke), `reet` (Nahaufnahme eines Reetdachs), `dorf` (500 Häuser mit Zäunen und Wegen). Regler für Sonne, Zustand, Nässe und Qualität. `TimberDemo --bild datei.png szene breite höhe [zustand]` rechnet ein Bild ohne Fenster und gibt die Zeit je Bild aus.
- `com.dan.timber.demo.TimberCheck` prüft auch Wehrgang und Torbau (Netze heil, Bohlen 1 m über dem Boden, lichte Höhe, Flügel, Fassung folgt dem Wall, Durchlass ohne Flügel). Seit Phase 11 prüft es auch das begehbare Hallenhaus: offene Rauchlöcher, Raum und Öffnungen, Strahlen aus der Mitte durch Tür und Rauchloch, Himmel durch die Öffnungen, Einrichtung ganz im Raum. Es prüft alle Netze (Indizes, Zahlen, Windung gegen die Normale), die fallenden Dreieckszahlen, Maße, Dach und Rauchlöcher je Typ, die Drehung, Zäune, Bohlenwege, Palisaden, Brücken, die Kacheln und die Auswahl und endet mit „Alles in Ordnung.“.

## Begehbare Häuser (seit Haithabu Phase 11)

- `Builder.buildOpen(typ)` baut Stufe 0 mit offenen Türen (das Blatt steht nach innen an der Wand) und mit echten Rauchlöchern in beiden Giebeln, samt Laibung.
- `Interior.room(haus)` gibt den Raum innen: Hülle (Kasten mit Satteldach) und Öffnungen. Er sagt, ob ein Punkt drin liegt, durch welche Öffnung ein Strahl ins Freie geht (`through`) und wie viel Himmel ein Punkt durch die Öffnungen sieht (`skyFactor`, `daylightFactor`). Mit `pack()` gehen die Zahlen an die Grafik.
- `Interior.hall(haus, seed)` baut die Einrichtung einer Halle [G]:
  - Lehmboden mit Binsen, Langfeuer mit Randsteinen, Wandbänke mit Bohlenfront und Fellen.
  - Sparren, Firstpfette, Querbalken und Innenpfosten.
  - Kessel an der Kette und Gewichtswebstuhl am Giebel.
  - Truhe, Fass, Specksteintöpfe, Hocker, Schilde, Beil, Lanze, zwei Talglampen und Fisch zum Räuchern unter dem First.
- `TimberGl.setSpecial(gl, haus, netz, reichweite)` zeichnet ein Haus mit eigenem Netz statt der Stufe des Typs, auch in der Schattenkarte.
- `TimberGl.Light.room` macht die Flächen im Raum anders hell. Die Sonne fällt nur durch die Öffnungen herein (genau gerechnet, dazu die Schattenkarte) und der Himmel nur über ihren Raumwinkel. Dazu kommen eine gleichmäßige Rückstrahlung und die Lichter des Raums statt der Punktlichter draußen. Eine Rauchschicht (`roomSmoke`) dämpft alles, was sie durchquert.
- Neue Werkstoffe: Lehmboden, Fell, Eisen, Wolle, Herdstelle.

## Schnee (seit Haithabu Phase 12)

- `Light.roofSnow`, `groundSnow`: Schneehöhe in m auf Dächern und Boden. `snowWet` und `frost` gehen von 0 bis 1.
- Schnee liegt auf allem, was nach oben zeigt: auf Reet und Soden bis rund 55° Neigung, auf Bohlen, Pfahlköpfen und Zäunen nur oben. Unter der Traufe liegt keiner.
- Über Häusern mit Rauchloch taut er um die Löcher und am First.
- Reif legt einen hellen Belag auf den Rest.

## Grenzen

- Nur Hallen bekommen die ganze Einrichtung; andere Typen bekommen Boden, Herd und Bänke, gezeichnet wird bisher nur das Versammlungshaus.
- Die Bauwerke werden auf der CPU zu festen Netzen gebaut; wer sie ändert, ruft `setStructures` neu auf.
- Mauerwerk nur als Kirche (`Church`) und Friesenhaus, keine Schiffe, keine Rauchfahnen (dafür liefert `smokeHoles` die Orte). Den Erdwall selbst formt der Benutzer im Gelände; das Paket liefert nur das Holz darauf und darin.
- Die Häuser stehen eben auf ihrem Punkt; an Hängen gleichen die Pfosten 0,3 m aus, mehr nicht.
- Seit Rungholt Phase 13: `TimberGl.moveSpecial(gl, house)` setzt das eigene Netz (etwa die Kirche) an einen neuen Ort oder in eine neue Höhe, ohne es neu hochzuladen.
