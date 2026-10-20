# com.dan.mudflat – das Watt als Material

**Stand: Material, Feinform und Spuren (Phase 7).** Das Gelände selbst (Kacheln, LOD) zeichnet die App; das Paket gibt ihr die Feinform unter 10 m und das Material.

Nasser Sand, der trocknet. Das Watt ist ein Bild aus Nässe, und Nässe ist Zeit: Mit dem Wasser springt die Nässe auf 1, danach trocknet der Sand mit Sonne, Wind und Wärme, dunkel und spiegelnd zuerst, hell und matt nach einer Stunde Sonne; Schlick bleibt feucht. Das Package kennt keine Tide und keine App; Wasser, Strömung und Wetter kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL. Wiederverwendbar für Strände (Lighthouse, Thunder), Flussufer, Hafenschlick.

Zum Herauskopieren reicht `src/com/dan/mudflat/`.

## Was es kann

- **Zustand je Zelle** (`WetField`): Nässe, Sediment (0 Sand … 1 Schlick), Richtung der letzten Strömung, Trockenzeit.
- **Sediment aus der Lage** (`classify`): Sand seewärts, in Rinnen und an steilen Kanten, Schlick landwärts in den Buchten und auf den oberen Watten am Vorland, Flecken aus Rauschen [G].
- **Trocknen** (`step`): unter Wasser 1, die Rippel richten sich quer zur Strömung aus; trocken halbiert sich die Nässe über der Restnässe (das Watt liegt über dem Grundwasser: Sand 22 %, Schlick 52 %) nach einer Halbwertszeit zwischen 3 h (Schatten, Windstille) und 25 min (Sonne, frischer Wind); Frost bremst, Regen macht nass. Sprünge in der Zeit setzen die Nässe aus der übergebenen Trockenzeit.
- **Spuren** (`addTrack`), die die nächste Flut löscht; seit Phase 7 gezeichnet: `TrackMap` rastert sie in einem Fenster um die Kamera (1024² zu 6 cm): Stiefel links und rechts im Wechsel, Robben als flaches Band mit Flossenabdrücken an den Rändern, Räder als zwei Linien; im Baustein werden sie zu Mulden (bis 3 cm), in denen die Rippel flach sind, der Sand dunkler und das Wasser zuletzt steht.
- **Strömung macht die Rippel** (seit Phase 6): die Länge der gemerkten Richtung ist die Stärke (1 ab 0,5 m/s); bei schwacher Strömung werden die Rippel flach, starke baut sie in wenigen Minuten um. `currentAt` fragt sie ab.
- **Feinform** (`Relief`, `gl/mudflat_relief.glsl`, seit Phase 6): Prielkanten mit Abbruch (wo der Grund nahe seinem geglätteten Mittel liegt und der Hang mehr als 2 % fällt, wird er steiler, bis 0,45 m, oben eine Stufe von 0,3 m, Buchten aus Rauschen), Rinnsale (Netz flacher Rinnen, grob 20 bis 30 m, fein 7 m, 12 cm tief, nicht in Prielen) und Wellenrücken (5 cm). Java und Shader rechnen dasselbe mit Ganzzahl-Hashes; Kamera und Bild sehen denselben Boden. Im Material steht in den Rinnsalen bei Ebbe zuletzt Wasser und läuft bergab (`mudflatR`, `uMudTime`).
- **Material im Shader** (`gl/mudflat.glsl`, Funktion `MudSurface mudflat(xz, n, above, px)`): Albedo nach Sediment und Nässe, Flecken, Rippel quer zur Strömung (14 cm auf Sand, 7 cm auf Schlick) mit einer zweiten Schar, Wasser in den Rippeltälern, Wasserfilm gleich nach dem Trockenfallen, Pfützen, Schill in Rinnen, Wattwurmhaufen (20 je m²), Algenmatten auf Schlick. Er beleuchtet nicht selbst: Die App mischt Licht und Himmelsspiegel nach Fresnel mit `film` und `rough`.
- **Textur** (`gl/MudflatGl`): lädt den Zustand als RGBA8, nur wenn er sich geändert hat, und setzt die Werte des Bausteins.
- **Abfragen** `wetnessAt`, `sedimentAt`, `classAt`, `dryHoursAt`, `colorAt`, `shares`, `halfLife`.
- Aus der Hülle: Presets `wadden()`, `beach()`, `fresnel(cos)`, `dryAlbedo`, `albedo`, `wetnessAfter`.

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Mudflat` | Kern: Presets, offene Werte, Formeln |
| `WetField` | Zustand je Zelle, Sediment, Trocknen, Spuren, Abfragen |
| `gl/mudflat.glsl` | Baustein für den Pixelshader der App (`#include "/com/dan/mudflat/gl/mudflat.glsl"`) |
| `gl/MudflatGl` | Textur des Zustands, Werte des Bausteins, `bindRelief`, seit Phase 7 `uploadTracks`/`bindTracks` |
| `TrackMap` | Spuren als Raster um die Kamera (Phase 7) |
| `Relief` | Feinform: Prielkante, Rinnsale, Wellenrücken (Java-Seite) |
| `gl/mudflat_relief.glsl` | Feinform im Scheitel- und Pixelshader (`mudEdge`, `mudRunnelDepth`, `mudRunnels`) |
| `demo/MudflatDemo` | ein Stück Watt unter Sonne und Himmel mit Reglern für Nässe, Sediment, Rippel, Strömung, Sonne, Wind und Trocknen im Zeitraffer; `--bild datei.png` zeichnet Sand, Mischwatt und Schlick bei vier Nässen neben `templates/Watt.png` |
| `demo/MudflatCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Einsetzen

```java
WetField wet = new WetField(Mudflat.wadden(), nx, nz, x0, z0, cell);
wet.classify(ground, travel, mtnw, mthw);                 // einmal
wet.step(hours, depth, u, v, dryHours, new WetField.Weather(sun, windMs, airC, rainMmH));   // je Schritt der Tide
// im GL-Faden
mudGl.upload(gl, wet);
mudGl.bind(gl, program, 14, wet, material);
```

Im Shader: `MudSurface m = mudflat(xz, normal, hoeheUeberDemWasser, bildpunktInMetern);` und daraus `m.albedo`, `m.normal`, `m.film`, `m.rough`; mit Rinnsalen `mudflatR(xz, normal, über, bildpunkt, rinnsal, bergab)`.

Feinform (seit Phase 6): im Scheitelshader `h += mudEdge(xz, hRaster, hGeglaettet, neigung) + mudRunnelDepth(xz, hGeglaettet, neigung)`, auf dem Prozessor `relief.delta(x, z, hRaster, hGeglaettet, neigung)`; `MudflatGl.bindRelief(gl, programm, relief)` setzt `uMudRelief`. Die Feinheit der Rippel nimmt der Baustein aus dem, was die App als Bildpunktgröße gibt; am besten `max(length(dFdx(xz)), length(dFdy(xz)))` (Befund Rungholt Status_Phase5: Ringe um das Auge).

## Spuren der Figuren (seit Rungholt Phase 9)

- `WetField.addTrack` kennt zwei Arten mehr: 3 nackter Fuß (Sohle, Ballen, fünf Zehen), 4 Stiefel genau am Ort (der Fuß einer Figur aus `com.dan.figure`, schon zur Seite versetzt). Bis zu 30 000 Spuren (`MAX_TRACKS`); eine Wattwanderung von 16 Personen über 5,8 km hinterlässt rund 28 000.

## Grenzen

- Die Feinform braucht ein feines Gitter (Rungholt: 2 m am Auge); Rinnsale unter 1 m Breite zeigt sonst nur der Pixelshader.
- Das Gitter der App hat 16 m: Nässe und Sediment sind auf 16 m genau, alles Feinere (Rippel, Pfützen, Haufen) kommt aus dem Shader.
- Trocknungszeiten, Restnässe und Helligkeiten sind gesetzt [G] und gegen `templates/Watt.png` verglichen (Farbton R/B), nicht gemessen.
- Spuren nur im Fenster von 61 m um die Kamera; höchstens 4 000 bleiben gemerkt.
