# com.dan.weathering – Verwitterung

Das Paket macht Oberflächen alt und nass: nass nach Gezeit, Seegang und Regen, Rostläufer unter Kanten und Rostflecken, Algen im Gezeitenband, Salzkrusten im Spritzwasser, Moos auf Flächen, die nach oben zeigen. Alles entsteht aus Masken und Rauschen; es wird keine Textur geladen. Der Kern rechnet den Zustand (Wasserstand, nasse Zone, Regennässe, Nässekarte) und dieselben Masken, die der Shader zeichnet, Zeile für Zeile auf dem Prozessor.

Der Kern braucht nur Java 21. Der Ordner `gl/` liefert den Shader-Baustein `weathering.glsl`, den ein fremder Fragment-Shader einbindet, und hält die Nässekarte als Textur; eigene Flächen zeichnet das Paket nicht. Es kennt weder den Leuchtturm noch andere Pakete. Zum Herauskopieren reicht `src/com/dan/weathering/`; nur `demo/WeatheringDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Nass:** unter Wasser ganz, im Auflauf der Wellen (0,15 m + 0,55 · H<sub>s</sub>), darüber fleckig im Spritzwasser. Fällt das Wasser, bleibt die nasse Zone zurück und trocknet mit 0,25 bis gut 1 m/h nach, schneller mit Wind und Sonne. Regen macht die Wetterseite nass, die Leeseite weniger, waagerechte Flächen ganz. Nasse Flächen werden dunkler (Stein und Rost mehr als Farbe) und glatter, dadurch glänzen sie.
- **Nässekarte:** `wet(u, v, radius, menge)` setzt einen Schwall auf eine Karte rund um die Fläche (Brecher, Gischt, ein Eimer Wasser). Wasser läuft herunter: unter dem Treffer bleibt es länger nass. Die Karte trocknet wie der Regen und wird nur hochgeladen, wenn sie sich ändert.
- **Rost:** Läufer unter jeder Kante (der Abstand unter der Kante kommt von der Fläche, Plattenstöße wiederholen ihn), dazu Flecken. Die Anfälligkeit je Fläche reicht von 0 (frische Farbe) bis 1 (blanker Stahl).
- **Algen:** im Gezeitenband zwischen mittlerem Niedrig- und Hochwasser, mit zerfranster Oberkante. Am Niedrigwasser grüner Schleim (Meersalat), darüber und darunter braun (Tang, Seepocken).
- **Salz:** weiße Krusten über dem Auflauf, wo es wieder trocken ist; auf waagerechten Flächen spült der Regen das meiste ab.
- **Moos:** auf Flächen, die nach oben zeigen, über dem Hochwasser.
- **Reproduzierbar:** gleicher `seed`, gleiches Bild. Das Rauschen nimmt einen Ganzzahl-Hash ohne Vorzeichen, Prozessor und Grafikkarte rechnen bitgleich.
- **Stufen:** `LOW`, `MEDIUM`, `HIGH`, `ULTRA` wählen nur die Größe der Nässekarte (64 × 32 bis 256 × 128). Die Masken selbst sehen in jeder Stufe gleich aus.

## Benutzung

```java
Weathering w = Weathering.breeze();        // Bft 5: Wind 9,3 m/s, H_s 2 m; calm(), gale(), storm(), beaufort(n)
w.meanHighWater = 1.45;
w.meanLowWater = -1.45;
w.rust = 0.5; w.algae = 0.8; w.salt = 0.3;
w.mapWidthM = 29; w.mapHeightM = 34; w.mapBottom = -3;   // Nässekarte rund um einen Turmsockel

// je Bild
w.waterLevel = gezeit;                     // m, gleiche Höhe wie die Szene
w.waveHeight = hs;
w.windSpeed = 12; w.windFromDeg = 250;
w.sun = 0.6;                               // Sonne am Ort 0..1, trocknet mit
w.step(dtSzene);
w.wet(u, v, 2.5, 1.0);                     // ein Brecher auf der Wetterseite

// zeichnen (im GL-Faden)
WeatheringGl gl = new WeatheringGl();
gl.init(gl4);
gl.update(gl4, w.snapshot());              // Nässekarte hochladen, wenn geändert
gl.bind(gl4, programm, w.snapshot(), 6);   // Uniforms setzen, Karte an Einheit 6
```

Im eigenen Fragment-Shader, nach `#version`:

```glsl
#include "weathering.glsl"                 // oder WeatheringGl.includeSource() einsetzen
vec4 masken;
float nass = wthApply(albedo, rauheit, porositaet, ort, normale, st, unterKante, rostAnfaelligkeit, kartenUV, masken);
```

`st` sind Flächenkoordinaten in m (s waagerecht, t senkrecht), `unterKante` der Abstand unter der nächsten Kante darüber, `kartenUV` der Ort in der Nässekarte (u rundum, v Höhe; negativ: keine Karte).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Weathering` | Eingänge, Lauf (nasse Zone, Regen, Karte), Voreinstellungen, Masken auf dem Prozessor |
| `gl/WeatheringGl` | Baustein, Uniforms, Nässekarte, Selbsttest |
| `gl/weathering.glsl` | die Masken Zeile für Zeile wie `Weathering` |
| `gl/check.vert`, `gl/check.frag` | Prüfgitter für den Selbsttest |

## Demo und Selbsttest

- `com.dan.weathering.demo.WeatheringDemo` zeigt einen Stahlpfahl (unten schwarz, darüber weiß und rot) mit Plattenstößen und Kragen und einen Steinblock im Gezeitenwasser. Regler für Wasserstand (oder Gezeit im Zeitraffer), Seegang, Regen, Sonne, Rost, Algen, Salz und Moos, dazu „Brecher“ und drei Ansichten: Bild, Nässe, Masken.
- Ohne Fenster rechnet `WeatheringDemo --bild datei.png [breite]` sechs Lagen nebeneinander und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.weathering.demo.WeatheringCheck` prüft Rauschen, Algenband, Rostläufer, Salz, Nässe nach Gezeit, Regen und Karte, Trocknung, Voreinstellungen und Reproduzierbarkeit; Ende „Alles in Ordnung.“.
- `WeatheringGl.selfCheck` rechnet 4 096 Proben auf der Grafikkarte (Nässe, Masken, Farbe, Rauheit) und vergleicht sie mit `Weathering.surface`.

## Grenzen

- Keine eigenen Texturen: Rost hat keine Dicke und keine abblätternde Farbe, Seepocken sind Farbe, kein Relief.
- Die Nässekarte ist eine Karte je Fläche (rundum und in der Höhe); für mehrere Flächen braucht es mehrere `Weathering`.
- Tropfen, Rinnsale und Beschlag auf Glas sind Sache von `com.dan.wetglass` (Phase 8).
- Regen kommt erst mit `com.dan.rain` (Phase 7) wirklich herein, Gischt auf die Nässekarte mit `com.dan.spray` (Phase 6).
