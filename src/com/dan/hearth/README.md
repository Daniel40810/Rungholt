# com.dan.hearth – Feuer, Licht und Brand

Das Paket macht Feuer: Herdfeuer, offene Feuerstellen, Fackeln, Schmiedeessen, Talglampen. Jedes Feuer hat eine Wärmeleistung, eine Flammenhöhe daraus, ein flackerndes Licht und Rauch. Dazu kommt der Brand: Feuer, das von Haus zu Haus springt. Dächer stürzen ein, Wände verkohlen, am Ende glimmt die Glut. Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet Flammenzungen, Glut, Funken und Ruß mit JOGL (OpenGL 4.3, ein Speicherpuffer, kein Eckpuffer); Ort und Alter jedes Funkens rechnet der Shader aus Strahler, Nummer und Zeit. Den Rauch über den Dächern zeichnet ein anderes Paket. Dieses Paket liefert nur Wärme, Rauchmenge und Schwärze dafür und kennt weder Haithabu noch andere Pakete.

Zum Herauskopieren reicht `src/com/dan/hearth/`; nur `demo/HearthDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Feuer** (`Fire`): fünf Arten (`Fire.Kind`) mit Wärmeleistung, Flackern, Rauch und Schwärze.

  | Art | Leistung | Flamme sichtbar |
  |---|---|---|
  | Herdfeuer | 10 kW | nein (im Haus) |
  | Feuerstelle | 25 kW | ja |
  | Fackel | 3 kW | ja |
  | Esse | 30 kW | ja, Takt des Blasebalgs |
  | Talglampe | 80 W | ja |

  - Die Flammenhöhe folgt Heskestad ohne das Durchmesserglied: L = 0,23 · Q<sup>0,4</sup>.
  - Lichtstrom ≈ 150 · Q<sup>0,9</sup> lm, Farbe eines Holzfeuers um 1 800 K.
  - Flackern aus drei Wellen und einem Rauschen: ohne Zustand, gleiche Zeit gibt gleiches Licht. Die Fackel flackert stärker, der Herd ruhiger.
  - Anzünden dauert rund 15 s, Ausgehen gut eine Minute (`lit`, `step`).
- **Brand** (`Blaze`): Häuser als Kasten mit Satteldach (`Blaze.Building`). Jedes hat drei Brandlasten: Dach (Reet oder Bretter), Wände, Gerüst.
  - Jede Last brennt an, wächst zum Höchstwert ihrer Wärmefreisetzung und brennt ab, bis der Brennstoff verbraucht ist. Die Energie geht auf, der Rechenfehler liegt unter 0,1 %.
  - Das Dach zündet die Wände, die Wände das Gerüst.
  - Ist das Gerüst zu einem Drittel verbrannt oder das Dach zu drei Vierteln, stürzt das Dach ein; was übrig ist, brennt im Haus mit einem Schub nieder. `onCollapse` meldet es (für Klang, Funken).
  - Zwischen Häusern wirkt Wärmestrahlung, mit dem Wind nach Lee verstärkt. Ein Reetdach fängt Feuer, wenn es 300 kJ/m² gesammelt hat; nass braucht es mehr. Dazu kommt Flugfeuer auf Reetdächer in Lee, zufällig, aber mit festem Startwert wiederholbar.
  - Ein Wohnhaus von 12 × 5 m brennt in einer Viertelstunde voll und stürzt dann ein; Höchstwert bis 28 MW. Nach drei Stunden ist es Asche.
  - Ausgaben je Haus: Wärmefreisetzung, Rauch und Schwärze (Reet qualmt schwarz, Glut hell), Flammen als Strahler (auf dem Dach, nach dem Einsturz am Boden, zuletzt Glut), Licht, Verkohlung, Dachrest, Phase, Ereignisliste.
  - Zustand als Text (`state()`, `setState(String)`): Zeit, Lasten, gesammelte Wärme und Ereignisse aller Häuser in einer Zeile je Haus. So lässt sich ein Brand sichern und genau dort fortsetzen (Haithabu Phase 16, „Weiter wie zuletzt“); `HearthCheck` prüft hin und zurück.
- **Grafik** (`gl/HearthGl`):
  - **Flammenzungen:** senkrecht, zur Kamera gedreht, im Wind geneigt. Rauschen läuft nach oben und reißt die Spitze in Fetzen. Die Farbe folgt der Temperatur: weißgelber Kern, orange, dunkelroter Rand.
  - **Glut:** flach am Boden, sie atmet.
  - **Funken:** Striche, die mit dem Auftrieb steigen, abkühlen und mit dem Wind treiben.
  - **Ruß:** über dem Hausbrand, von unten rot angestrahlt.
  - Leuchtdichten in cd/m² mal `Light.lumScale`; so passt das Feuer zu jeder Belichtung.

## Benutzung

```java
Fire fackel = new Fire(Fire.Kind.FACKEL, 10, 2.4, -3, 7);
// je Bild:
fackel.step(dt);
double[] cd = fackel.intensity(t);          // Lichtstärke RGB für die Szene
double[] wo = fackel.lightPosition();
Flame f = fackel.flame();                   // für HearthGl, null beim Herd im Haus

Blaze brand = new Blaze(haeuser, 1050);
brand.windFromDeg = 90; brand.windSpeed = 6;
brand.ignite(0);
brand.step(dt);
List<Flame> flammen = brand.flames(0);
double kw = brand.hrr(0), schwarz = brand.blackness(0);

HearthGl gl = new HearthGl();
gl.init(gl4);
gl.update(gl4, alleFlammen);
gl.draw(gl4, view, light, t);
```

## Demo und Selbsttest

- `com.dan.hearth.demo.HearthDemo` hat zwei Szenen bei Nacht:
  - `brand`: Zwei Reihen Reethäuser, das erste brennt, der Wind trägt das Feuer weiter; im Zeitraffer.
  - `feuer`: Feuerstelle, zwei Fackeln, Esse, ihr Licht auf Boden und Wänden.

  `HearthDemo --bild datei.png szene breite höhe [Brandzeit in s]` rechnet ein Bild ohne Fenster und schreibt das Ereignisprotokoll.
- `com.dan.hearth.demo.HearthCheck` prüft:
  - Flammenhöhen und Flackern (Mittel 1, Fackel stärker als Herd, gleiche Zeit gibt gleichen Wert).
  - Licht und Farbe, Anzünden und Ausgehen.
  - Den Brand eines Hauses: Höchstwert, Zeit bis zum Einsturz, Asche, Energie, Rauch erst schwarz und dann hell, Flammen erst auf dem Dach und dann am Boden.
  - Die Ausbreitung nach Lee bei Wind und keine bei Windstille; nasses Reet zündet später.
  - Dass der gleiche Startwert den gleichen Brand gibt.

  Am Ende steht „Alles in Ordnung.“.

## Glut (seit Haithabu Phase 11)

`Fire.glow` wächst, solange ein Feuer brennt (in rund 10 min), und glimmt nach dem Ausgehen unter der Asche nach (Zeitkonstante 3 h) [G]. Die Glut leuchtet tiefrot um 1 000 K mit höchstens 30 lm je Größe 1. Sie gibt 1,2 kW Wärme und zeichnet sich als `Flame.EMBER` flach auf dem Herd. `HearthCheck` prüft das: nach 30 min Feuer ist die Glut voll, vier Stunden nach dem Ausgehen glimmt sie noch zu einem Viertel.

## Grenzen

- Keine Strömungsrechnung: Flammen und Funken sind Formeln, kein Feuer, das an Wänden hochläuft.
- Häuser sind Kästen mit drei Brandlasten; einzelne Balken, Türen oder Inhalt gibt es nicht.
- Das Licht ist rundum gleich; Schatten wirft es nicht.
- Löschen gibt es nicht. Regen wirkt nur über die Nässe, die der Benutzer setzt.
