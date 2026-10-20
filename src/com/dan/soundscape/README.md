# com.dan.soundscape – Klang

Erzeugter Ton ohne Audiodateien: Wind mit Äolstönen an Stäben und Seilen, See und Brandung, Regen auf dem Wasser, auf Glas und auf einem Dach, Brecher, Nebelhorn und Typhon, Möwen, Donner aus dem Kanal eines Blitzes und der Diesel eines Schiffs. Seit Haithabu Phase 14 dazu eine Siedlung: Rufe von Vögeln und Haustieren, Werkstätten, die von selbst arbeiten, Ruderschlag, Feuer, Stimmengewirr, Schilf und Reet im Wind und Wellen an Pfählen. Alles gilt für ein Ohr mit Ort und Blickrichtung: Abstand, Laufzeit, Luftdämpfung, Richtung. Drinnen dämpft die Wand und der Raum hallt. Der Kern braucht nur Java 21; die Ausgabe auf den Lautsprecher nimmt `javax.sound.sampled` aus dem JDK. Ein `gl/` gibt es nicht. Zum Herauskopieren reicht `src/com/dan/soundscape/`; nur `demo/SoundscapeDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Pegel in dB SPL:** Jede Quelle hat einen Pegel in 1 m oder am Ohr. Ein Effektivwert von 1 im Signal sind 104 dB (`REF_DB`). Abstand nach 1/r, Laufzeit nach Abstand / c mit c aus der Temperatur (331,3 · √(1 + T/273,15) m/s), die Ferne verliert die Höhen (Tiefpass nach Abstand).
- **Richtung:** Stereo nach dem Winkel zur Blickrichtung des Ohrs, das abgewandte Ohr hört gut 12 dB leiser und bis 0,66 ms später (Kopf). Quellen hinter dem Ohr klingen etwas dumpfer.
- **Wind:** rosa Rauschen in einem Band, das mit dem Wind steigt, dazu das Brausen am Ohr; Böen als Ornstein-Uhlenbeck-Vorgang in Echtzeit (3 s). Pegel 22 + 50 · lg U dB, mit dem logarithmischen Profil über See für die Höhe des Ohrs. Gemessen: Bft 2 34 dB, Bft 6 69 dB, Bft 10 86 dB.
- **Äolstöne:** Jeder Körper in `bodies` (Stab, Rohr, Seil: Ort, Durchmesser, Länge) singt mit f = 0,2 · U / d (Strouhal), mit dem Wind in seiner Höhe; der Ton wandert mit den Böen. Ein Geländerstab von 25 mm singt bei 15 m/s mit 120 Hz.
- **See und Brandung:** Rauschen der Wellen und Schaumkronen, das mit der Periode atmet und in Gruppen kommt. An einem Bauwerk (`surfX`, `surfZ`, `surfRadius`) klatscht jede Welle und läuft ab, im Takt der Periode.
- **Regen:** auf dem Wasser Zischen und einzelne Tropfen (1,3 bis 5 kHz); auf Glas helle Ticks (`glassDrops` je s), auf einem Blechdach tiefer und länger (`roofDrops`). Glas und Dach klingen drinnen, ohne die Wand.
- **Ereignisse:** `event(Kind, x, y, z, Stärke)` für BRECHER, MOEWE, NEBELHORN, TYFON, DONNER; sie erreichen das Ohr nach ihrer Laufzeit.
  - **Brecher:** dumpfer Schlag (60 → 35 Hz), Krachen, dann das zurückfallende Wasser; bis 120 dB in 1 m.
  - **Nebelhorn:** `horn(Kind.NEBELHORN, …, Dauer)`, Diaphon mit 175 Hz und Obertönen, 132 dB in 1 m [G]; am Ende fällt der Ton um ein Fünftel („Grunzen“).
  - **Typhon:** 220 Hz, 138 dB in 1 m (KVR Anlage III: 130 bis 350 Hz für 75 bis 200 m Länge).
  - **Möwe:** Silbermöwe, kurzes „kjau“, Warnruf „kek-kek“ oder langer Ruf aus sechs bis neun Tönen; Grundton 800 bis 1100 Hz, näselnd, rau.
  - **Donner:** `thunder(x, y, z, Pegel am Ohr, Abstände, Gewichte, Schläge, schon angekommen)`: Jedes Stück des Kanals sendet eine N-Welle (Few), die Summe rollt so lange, wie die Abstände auseinanderliegen; fern länger und dumpfer, nah ein Knall. Ohne Kanal rechnet das Paket einen gewundenen Kanal mit drei Ästen.
- **Maschine:** `hums` (Ort, Zündfrequenz, Pegel); `Hum.firing(750, 6)` = 37,5 Hz. Mit `aboard` klingt sie gleichmäßig durch den Rumpf.
- **Raum:** `inside` 0..1 und `room`: `Room.OUTDOORS`, `STONE_TOWER` (−28 dB, bis 320 Hz, Hall 2,2 s), `GLASS_LANTERN` (−14 dB, bis 2,2 kHz, 0,7 s), `WHEELHOUSE` (−18 dB, 1,5 kHz, 0,4 s). Hall nach Schroeder und Moorer.
- **Ohr passt sich an:** `autoGain` zieht den Pegel über 4 s zu 84 dB, höchstens 18 dB hinauf und 12 dB hinab; Donner und Horn bleiben laut, eine stille Nacht wird hörbar. Ein Begrenzer mit 5 ms Vorschau fängt den Rest.
- **Siedlung** (seit Haithabu Phase 14, alles erzeugt [G]):
  - **Rufe** mit `event(Kind, x, y, z, Stärke)`: Krähe, Stockente (Weibchen und Erpel), Rauchschwalbe, Kormoran, die singenden Schwingen des Höckerschwans, Huhn (Gackern, Warnruf ab Stärke 0,9), Hahn, Ziege (Meckern mit 7 bis 10 Hz), Schwein (Grunzen, Quieken), Hund. Jeder Ruf ist eine Folge von Tönen aus Grundton, Obertönen und Rauschen durch Formanten (`Calls`).
  - **Gewerke und Schiffe** mit `event(Kind, x, y, z, Stärke, tone)`: Hammer auf heißem Eisen und Prellschlag auf dem Amboss (Eigentöne eines kleinen Blockambosses ab 2 kHz), Klopfen des Bronzegießers, Beil und Dechsel, Säge und feine Säge (Stoß und Zug), Ruderschlag eines Schiffs (tone = Blätter im Wasser: Eintauchen, Spritzen, Knarren in Pforte oder Dolle, Tropfen), einstürzende brennende Balken (`Crafts`).
  - **Pegel:** lauteste 100 ms in 1 m: Hahn und Hund 100 dB, Hammer 112, Beil 100, Krähe 90, Ziege 88, Ente 85, Säge 84, Huhn 80, Schwein 78, Schwalbe 75, feine Säge 70, Einsturz 116, Ruderschlag 86 + 10 · lg(Blätter / 10).
  - **Werkstätten** (`workshops`, `Workshop`): Schmied, Bronzegießer, Zimmermann, Säger, Kammmacher arbeiten von selbst in Reihen mit Pausen, solange `active`; Takt und Pausen siehe `Workshop`.
  - **Feuer** (`blazes`, `Blaze`): Knistern, Knacken und Brausen nach der Größe (Fackel 0,1, Herd 0,25, Esse 0,4, brennendes Haus 1); ein Feuer mit `indoor` hört man nur drinnen, dann ohne die Wand.
  - **Stimmengewirr** (`crowds`, `Crowd`): acht gedachte Sprecher in Silben ohne Sinn und ein sprachförmiges Rauschen; 60 + 10 · lg(0,35 · Leute) dB am Rand der Menge.
  - **Schilf und Reet** (`reedNear`, `thatchNear` 0..1): Rauschen im Wind (2 bis 8 kHz für Schilf, um 2 kHz für Reet), stärker in Böen.
  - **Pfähle** (`pilings`, {x, z}): Die Wellen glucksen an den nächsten acht in 25 m, mehr mit der Wellenhöhe.
  - **Über Land** (`obstruction`, dB je 100 m): Häuser, Zäune und Boden dämpfen und nehmen die Höhen; in einer Siedlung um 4.
  - **Rauigkeit** (`roughness`, m): Windprofil über dem Boden; 0,0002 über See (wie bisher), um 0,3 zwischen Häusern, wo der Wind am Ohr schwächer rauscht.
  - **Raum** `Room.LONGHOUSE`: Langhaus aus Flechtwerk und Lehm unter Reet, −13 dB, bis 1,1 kHz, Hall 0,55 s.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)` (Wind, Seegang, Regen). **Reproduzierbar:** gleicher `seed`, gleicher Ton. **Stufen:** `Quality.LOW` bis `ULTRA` (22,05 bis 48 kHz, 12 bis 48 Stimmen, 2 bis 10 Äolskörper, Hall ab MEDIUM).

## Eis und See (seit Glacier Phase 12)

Erzeugt und nach Gehör gebaut [G]. Ausgelöst wird mit `event(Kind, x, y, z, Stärke, tone)`.

- **Knacken** (`KNACKEN`): ein bis sechs scharfe Schläge durch kurze Eigentöne des Eises (700 bis 3 300 Hz), darunter ein leiser dumpfer Stoß. Stark wie ein Schuss, 104 + 22 · Stärke dB in 1 m.
- **Ächzen** (`AECHZEN`): tiefes Stöhnen (28 bis 70 Hz, gleitend), stoßweise durch Haften und Gleiten. 96 + 12 · Stärke dB.
- **Aufschlag** (`AUFSCHLAG`, tone = Volumen in m³): dumpfer Schlag, umso tiefer, je größer (45 → 18 Hz), Krachen, das zurückfallende Wasser. 112 + 9 · lg(V / 100) dB, 100 bis 140.
- **Auflaufen** (`AUFLAUFEN`, Stärke 0..1 = 0 bis 2 m): das Wasser rauscht das Ufer hinauf und läuft über Kies zurück. 82 + 14 · lg(h / 0,2) dB.
- **Kondor** (`KONDOR`): Luft durch die gespreizten Handschwingen, selten ein Zischen, 70 dB.
- **Donner einer Kalbung** (`calving(Weg, Volumen)`): Der Weg des Risses ist der Kanal wie beim Blitz. Jedes Stück sendet eine N-Welle, die Summe rollt nach den Abständen zum Ohr. Pegel 136 + 6 · lg(V / 1 000) dB in 1 m.
- Prüfung (`SoundscapeCheck`): Schwerpunkt und Pegel jedes Klangs, Laufzeit des Kalbungsdonners.

## Wildwasser (seit Glacier Phase 13)

`Torrent` ist ein tosender Durchfluss, etwa ein Dammbruch, die Ruptura oder ein Wasserfall. Die App setzt Ort und Durchfluss je Bild (`soundscape.torrents`).

- **Pegel in 1 m:** 70 + 12 · lg(Q) dB [G]. Bei 8 000 m³/s sind das 117 dB, in 150 m rund 74 dB.
- **Drei Teile:** Rollen unter 300 Hz, Rauschen um 1 kHz, Zischen der Gischt (nur nah).
- **Dazu:** Schläge, wenn Eis oder Steine aneinanderstoßen.
- Hörbar bis 3 km; der Pegel zählt zum Teil „See“.

## Das Watt (seit Rungholt Phase 14)

Erzeugt und nach Gehör gebaut [G], keine Aufnahmen. Ausgelöst mit `event(Kind, x, y, z, Stärke, tone)` (`Wadden`).

- **Wattvögel nach Art:** Austernfischer (`AUSTERNFISCHER`, scharfes „kliep“, ab Stärke 0,8 das Trillern, 98 bis 100 dB in 1 m), Alpenstrandläufer (`ALPENSTRANDLAEUFER`, tone = Zahl der Vögel: ein dünnes Trillern im Trupp, 80 + 10 · lg(n) dB), Säbelschnäbler (`SAEBELSCHNAEBLER`, „plüit“, 90 dB), Brandgans (`BRANDGANS`, Kehllaute, der Pfiff des Erpels, 84 bis 88 dB), Lachmöwe (`LACHMOEWE`, 92 dB), Ringelgänse (`RINGELGANS`, tone = Zahl: das Murmeln eines Trupps, 85 + 8 · lg(n) dB), ein wendender Schwarm (`SCHWARM`, tone = Zahl: Flügelrauschen, 70 + 10 · lg(n / 100) dB). Die Silbermöwe bleibt `MOEWE`.
- **Seehunde:** `SEEHUND` (Schnauben oder Knurren, 78 bis 80 dB), `HEULER` (das Rufen des Jungen nach der Mutter, 85 dB).
- **1362:** `RIND` (100 dB), `SCHAF` (85 dB), `GLOCKE` (tone 0 = Prime 311 Hz, 1 = 415 Hz; neun Teiltöne nach dem Muster einer Kirchenglocke, je als schwebendes Paar; 118 dB in 1 m).
- **Die Glocken aus dem Watt** (`GLOCKE_WATT`, die Sage): dieselben Teiltöne, dumpf wie unter Wasser (Tiefpass 900 Hz), mit leichtem Schwanken der Tonhöhe (0,6 % bei 0,4 Hz) und zwei Echos (0,11 und 0,27 s), 130 dB in 1 m [G], damit sie über das Watt tragen.
- **Priele** (`Stream`, `soundscape.streams`): Rauschen und Glucksen an der Kante, lauter und heller mit der Strömung. 52 + 22 · lg(v / 0,5) + 5 · lg(Breite / 10) dB in 1 m. Die App setzt Ort, Strömung und Breite je Bild; die nächsten vier in 600 m klingen. Pegel im Teil „Watt und Priel“.
- **Knistern** (`mudCrackle` 0..1): das Watt, das frisch trockengefallen ist, knackt leise (Luft aus den Gängen und Poren, platzende Bläschen). Kurze helle Knackse, rund 35 dB am Ohr bei voller Fläche.
- **Der Wind trägt den Schall** (`windCarry`, `carryDb`): mit dem Wind bis +5 dB, gegen den Wind bis −24 dB (Schallschatten), stärker mit Wind und Abstand (Maß Wind · √(Abstand / 100 m)), dazu ein Schwanken in Böen (Ornstein-Uhlenbeck, 2,5 s, bis ±6 dB) [G, nach der Größenordnung aus der Schallausbreitung im Freien]. Unter 50 m und ohne `windCarry` 0. Gilt für Ereignisse und Wildwasser.
- **Wind am Ohr** misst seit Phase 14 die Höhe über `waterLevel` (Wasser oder Grund unter dem Ohr), nicht über null.
- Prüfung (`SoundscapeCheck`): Schwerpunkt und Pegel der Rufe, Prime der Glocke, die dumpfe Glocke aus dem Watt, Priel nach Strömung, Knistern, der Wind, der den Schall trägt.

## Benutzung

```java
Soundscape s = Soundscape.beaufort(7);
s.bodies.add(new Aeolian("Handlauf", 0, 24.1, -4.2, 0.07, 26));
s.surfRadius = 5.5;
s.earX = …; s.earY = …; s.earZ = …; s.earYawDeg = …;   // je Bild
s.inside = 1; s.room = Room.GLASS_LANTERN;
AudioOut out = new AudioOut(s);
out.start();                                            // eigener Faden, Puffer gut 90 ms
s.event(Soundscape.Kind.MOEWE, x, y, z, 1);
s.horn(Soundscape.Kind.NEBELHORN, x, y, z, 1, 3.0);
```

Ohne Lautsprecher: `s.render(Sekunden)` gibt verschränktes Stereo, `Wav.write(datei, ton, s.sampleRate())` schreibt es.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Soundscape` | Eingänge, Betten (Wind, Äolstöne, See, Brandung, Regen, Glas, Dach, Maschine), Ereignisse, Mischer mit Ort und Richtung, Wand, Hall, Anpassung, Begrenzer, Pegel |
| `Voice` | Ereignisse: Brecher, Horn, Möwe, vorgerechneter Klang |
| `Calls`, `Crafts` | Rufe der Tiere, Gewerke, Ruder, Einsturz (seit Haithabu Phase 14) |
| `Workshop`, `Blaze`, `Crowd` | Werkstätten mit eigenem Takt, Feuer, Mengen (seit Haithabu Phase 14) |
| `Thunder` | Donner aus dem Kanal (N-Wellen, Luft, Echos) |
| `Dsp` | Rauschen, Biquads, Glättung, Hall, Begrenzer |
| `Aeolian`, `Hum`, `Room` | singende Körper, dauernde Quellen, Räume |
| `AudioOut`, `Wav` | Lautsprecher (eigener Faden), WAV-Datei |
| `demo/SoundscapeDemo` | Fenster mit Ton, Reglern und Pegeln; `--bild` acht Hörszenen als Spektrogramme, `--wav` dieselben als WAV |
| `demo/SoundscapeCheck` | Prüfung ohne Lautsprecher, endet mit „Alles in Ordnung.“ |
| `demo/Analysis` | FFT, Spektrum, Spitze, Schwerpunkt, Einsatz, Spektrogramm |

## Demo und Selbsttest

- `com.dan.soundscape.demo.SoundscapeDemo` spielt einen gedachten Turm mit Galerie; Beaufort, Regen, Ort des Ohrs, Knöpfe für Nebelhorn, Typhon, Möwe, Brecher, Donner nah und fern. Seit Phase 14: „Dorf“ legt Werkstätten, Markt, Feuer und Pfähle um den Turm, eine Liste spielt jeden Ruf und Klang der Siedlung, das Langhaus ist als Raum wählbar.
- `com.dan.soundscape.demo.SoundscapeCheck` prüft mit FFT: Rauschfarben, Filter, Wind nach Beaufort, Äolston nach Strouhal, Nebelhorn und Typhon nach KVR, Laufzeit, Abstand, Richtung und Ohrenlaufzeit, Luftdämpfung, Wand, Nachhallzeit, Regen auf Wasser und Glas, Donner, Möwe, Diesel, Begrenzer, Anpassung, seed, Stufen und Rechenzeit; seit Phase 14 dazu die Pegel aller 19 Rufe und Klänge, Meckern und Krähen, den Takt der Werkstätten, Ruderschlag, Feuer, Stimmengewirr, Schilf, Pfähle, Langhaus, Dämpfung über Land und die Rechenzeit einer Siedlung.

## Grenzen

- Kein Doppler, keine Bodenreflexion; Richtung nur in der Ebene. Der Wind trägt den Schall nur als Pegel (seit Rungholt Phase 14), ohne Krümmung der Strahlen oder Laufzeit.
- Die Pegel der Betten sind geschätzt und an Erfahrungswerte angelehnt, nicht gemessen; Hörner und Typhon folgen den Vorschriften.
- Die Möwe ist eine Silbermöwe. Die Rufe der Wattvögel sind Arten, keine Individuen und kein Gesang. Die Rufe der übrigen Arten sind nach Gehör und Beschreibungen gebaut, keine Aufnahmen; man erkennt die Art, nicht das einzelne Tier. Das Stimmengewirr hat keine Wörter. Das Nebelhorn ist ein Diaphon nach Gehör, kein bestimmtes Horn.
- Der Hall ist ein Schroeder-Hall, kein Abbild des Raums.
