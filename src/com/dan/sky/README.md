# com.dan.sky – Himmel und Atmosphäre

Das Paket rechnet den Himmel über einem Ort zu einer Zeit: Sonne, Mond und die fünf hellen Planeten nach Datum, Uhrzeit und Ort, die Streuung des Lichts in der Lufthülle mit Dunst nach Sichtweite, Dämmerung, Erdschatten und den natürlichen Nachthimmel. Dazu 8 920 Sterne, die Milchstraße, die Sternbildfiguren aus dem Projekt Sternbild, Lichtglocken ferner Städte über der Kimm, Regenbogen und Blitzleuchten. Aus demselben Zustand kommt das Licht für eine Szene: Sonne und Mond nach der Auslöschung, Himmelslicht, Dunst, die Helligkeit fürs Auge und die Grenzgröße der Sterne.

Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet denselben Himmel mit JOGL (OpenGL 4.3, ohne Compute-Shader). Das Paket kennt weder den Leuchtturm noch andere Pakete. Zum Herauskopieren reicht `src/com/dan/sky/`; nur `demo/SkyDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Sonne, Mond, Planeten:** Sonne nach VSOP87, Mond nach Meeus Kapitel 47 (beides als Kopie aus FMoon, dort gegen die Beispiele bei Meeus geprüft), Phase, Libration, Erdschein. Planeten nach den Bahnelementen von Standish (JPL), Helligkeit nach Meeus Kapitel 41. Präzession von J2000 auf das Datum, Strahlenbrechung an der Kimm (die Sonne geht abgeplattet unter).
- **Streuung:** Rayleigh (Luft), Mie (Dunst) und Ozon nach Hillaire 2020 mit Mehrfachstreuung: blauer Mittag, weißliche Aureole um die Sonne, Abendrot, Erdschatten und Venusgürtel, blaue Stunde durch das Ozon. Der Dunst kommt aus der meteorologischen Sicht (Koschmieder), 1 bis 220 km.
- **Nacht:** natürlicher Nachthimmel mit 22 mag/″² (Eigenleuchten der Luft, zur Kimm heller), Sterne bis 6,5 mag mit Farbe aus B−V, Auslöschung nach der Luftmasse, Funkeln (an der Kimm stärker) und einer Grenzgröße fürs Auge, die mit Mond und Dämmerung sinkt. Milchstraße nach Helligkeitsstufen. Lichtglocken von Städten über der Kimm (`LightDome`).
- **Mond:** Scheibe mit Phase und Sichelneigung aus der echten Lage von Sonne und Mond, Oberfläche aus FMoon (17 Maria, Strahlenkrater), Libration, Erdschein auf der Nachtseite. Die Helligkeit ist so geeicht, dass die ganze Scheibe genau das Mondlicht bringt, das auch aufs Wasser fällt.
- **Licht für die Szene:** `State` liefert Richtung und Farbe von Sonne und Mond am Auge, Himmelslicht auf eine waagerechte Fläche, Zenit-Strahldichte, Dunst je m, Anpassungsleuchtdichte und Blickfeld-Leuchtdichte (`viewLuminance`) für die Belichtung, den Anteil des Stäbchensehens (`scotopic`) für die Nachbearbeitung.
- **Wetter-Eingänge:** `visibilityKm`, `cloudCover` (bis ein Wolken-Paket zeichnet, graut ein Schleier den Himmel ein), `rainbow`, `flash(...)` in derselben Form wie die Schnittstelle `Flash`.
- **Rundumbild:** dasselbe Format, das `com.dan.ocean` für Spiegelungen liest (Azimut von Nord über die Breite, Höhe −90° bis +90°).

## Benutzung

```java
Sky sky = new Sky(System.currentTimeMillis(), 53.855, 8.079);
sky.eyeHeight = 10;                      // Augenhöhe in m (Kimm, Dunstweg)
sky.visibilityKm = 30;                   // Sicht
sky.domes.add(new Sky.LightDome("Bremerhaven", 137, 48, 1.0));

// je Bild
sky.epochMillis = uhrzeit;
Sky.State st = sky.step(dt);             // Licht rechnet bei Bewegung im Hintergrund nach
double[] sonne = st.sunColor;            // Bestrahlung am Auge (Szeneneinheiten, Sonne außerhalb 2,9)
double belichtung = Sky.State.exposureFor(Math.sqrt(st.adaptation * st.viewLuminance(blick, oben, 55, 16 / 9.0)), 0.85);

// zeichnen (im GL-Faden)
SkyGl gl = new SkyGl();
gl.init(gl4);
gl.update(gl4, st);                      // Tabellen
SkyGl.View v = new SkyGl.View();         // Matrizen, Bildgröße, Öffnung, Vorbelichtung
v.preExposure = belichtung;
gl.draw(gl4, st, v);                     // Himmel, Sterne, Planeten, Figuren
gl.drawPanorama(gl4, st, belichtung);    // ins gebundene Rundumbild
```

**Vorbelichtung:** Alle Helligkeiten kommen mit `preExposure` multipliziert heraus. Der Nachthimmel ist 10⁻⁸ so hell wie der Tag; ohne Vorbelichtung verschwände er im Halbfloat-Puffer. Eine Szene multipliziert ihr eigenes Licht (Sonne, Mond) mit derselben Zahl und belichtet danach mit 1.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Sky` | Eingänge, Zustand (`State`), Licht, Belichtungshilfen |
| `Atmosphere` | Rayleigh, Mie, Ozon, Sicht → Dunst |
| `Scattering` | Tabellen und Strahlengang auf dem Prozessor (wie die Grafikkarte) |
| `Celestial` | Präzession, Horizont, Brechung, Luftmasse |
| `Planets` | Merkur bis Saturn |
| `StarCatalog` | Sterne (`stars.bin`), Figuren (`figures.txt`), Sternfarbe |
| `MoonDisc` | Mondoberfläche, Beleuchtung, Eichung |
| `astro/` | Kopie des Rechenkerns aus FMoon |
| `gl/SkyGl` | Tabellen, Himmel, Sterne, Rundumbild, Selbsttest |

## Daten und Herkunft

- `stars.bin`: HYG-Datenbank v4.1 (astronexus, CC BY-SA 4.0), alle Sterne bis 6,5 mag.
- `milkyway.png`: aus den Helligkeitskonturen von d3-celestial (Olaf Frohn, BSD-3) gerastert.
- `figures.txt`: Sternbildfiguren aus dem Projekt Sternbild (BuiltinCatalog).
- `astro/`: Kopie aus FMoon (Meeus, „Astronomical Algorithms“).

## Demo und Selbsttest

- `com.dan.sky.demo.SkyDemo` zeigt den Himmel über einer glatten See mit Reglern für Tag, Uhrzeit, Zeitraffer, Sicht, Bedeckung und Regenbogen, Schaltern für Sterne, Planeten, Milchstraße und Sternbilder, „Blitz“ und vier Voreinstellungen. Ziehen dreht den Blick, das Rad ändert die Öffnung.
- Ohne Fenster rechnet `SkyDemo --bild datei.png [breite] [höhe] [JJJJ-MM-TT]` Morgen, Mittag, Abend und Nacht nebeneinander und vergleicht dabei Grafikkarte und Prozessor.
- `com.dan.sky.demo.SkyCheck` prüft den Rechenkern gegen Meeus, Präzession und Venus, Sonnenaufgang auf zwei Wegen, Streuung gegen die genaue Rechnung und den CIE-Normhimmel, Dämmerung, Nachthimmel, Mond- und Sonnenlicht, Grenzgröße, Katalog, Figuren, Milchstraße und die Uniforms; Ende „Alles in Ordnung.“.
- `SkyGl.selfCheck` liest die Tabellen der Grafikkarte zurück und meldet die größte Abweichung zum Prozessor.

## Grenzen

- Die Mehrfachstreuung ist die Näherung nach Hillaire (isotrop ab der zweiten Streuung). Tief in der Dämmerung (Sonne unter −12°) ist der Himmel dadurch etwas zu dunkel; dort übernimmt der Nachthimmel.
- Dunst liegt gleichmäßig mit 1,2 km Skalenhöhe. Bodennebel und Nebelbänke bringt `fog`, Wolken bringt `cloud`.
- Planeten ohne Lichtlaufzeit und Saturn ohne Ringneigung. Keine Kometen, kein Zodiakallicht, keine Polarlichter.
- Die Sterne stehen an ihrem Ort von J2000; die Eigenbewegung fehlt (in 26 Jahren höchstens 0,04°, bei 61 Cygni).

## Seit Glacier Phase 3: Südhimmel

- Vier Sternbildfiguren des Südhimmels in `figures.txt`: Kreuz des Südens (Cru), Zentaur (Cen, mit α und β als
  Zeiger), Kiel des Schiffs (Car, mit Canopus), Südliches Dreieck (TrA); jetzt 27 Figuren.
- Die Magellanschen Wolken fehlen in den Helligkeitsstufen von d3-celestial. `StarCatalog.magellanic(ra, dec)` gibt
  sie als weiche Flecken (Große Wolke 10° × 9° mit Balken, Kleine 5° × 3°) [G]; `SkyGl` malt sie beim Laden in die
  Milchstraßenkarte. Auf 50° Süd stehen beide und das Kreuz die ganze Nacht über der Kimm.
- Rückfluss nach Haithabu/Lighthouse in Phase 16.
