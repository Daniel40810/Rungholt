# com.dan.tide – die Tide

**Stand: Phase 4, Pegel und Überflutung.** Die Strömung wird schon gerechnet; ins Bild (Kräuselung, Schaum) kommt sie in Phase 6 (Werkbuch Rungholt, Abschnitt 9).

Wasserstand als Pegelkurve und die Überflutung eines Geländes mit Laufzeit. Das Package kennt keine Grafik, keine App und kein anderes Package; der Kern braucht nur Java 21, `demo/` dazu FStyle. Wiederverwendbar für Lighthouse (die echte Tide am Turm), Haithabu (die kleine Tide im Noor) und jede Hafenszene.

Zum Herauskopieren reicht `src/com/dan/tide/`.

## Was es kann

- **Pegel** (`Tide`) aus zehn Partialtiden: M2, S2, N2, K2, K1, O1 und die Flachwassertiden M4, MS4, MN4, M6. Spring und Nipp, die tägliche Ungleichheit und die schiefe Kurve folgen von selbst. `levelAt(stunden)` mit Stunden seit 1970 (UTC), `slopeAt`, `rising`.
- **Presets**: `husum()` – angepasst an 475 Hoch- und Niedrigwasserzeiten des Gezeitenkalenders 2026 (BSH, Husum; Januar, April, Juli, Oktober) und an den mittleren Hub der Husumer Bucht von 3,50 m; Zeiten im Mittel 6 bis 11 min genau, MThw NHN +1,60 m, MTnw −1,90 m [G]. `pellworm()` und `hever()` als verschobene Kopien [G]. Seit Rungholt Phase 17 `bremerhaven()` – angepasst an 112 Hoch- und Niedrigwasser vom 20.10. bis 17.11.2026 (tide-forecast.com, nicht amtlich; im Mittel 10 min und 14 cm genau, Höhen um das Mittelwasser, Hub 3,6 m) – und `roterSand()` für Lighthouse: eine Stunde früher, Hub 3,0 m im Mittel [G]. `basin()` für die Demo; `shifted(minuten, hubfaktor)` für eigene Orte.
- **Abfragen**: `extremes(von, bis)` (Hoch- und Niedrigwasser auf eine halbe Minute), `next(t, hoch)`, `calendar(tag, zone)` und `calendar(jahr, zone)` (rund 1 410 Einträge), `springFactor(t)` (0,8 bis 1,3), `nextSpring(t)`, `format(turn, zone)`.
- **Windstau**: `surgeTarget(wind, richtung)` [G] – quadratisch im Wind gegen die Küste aus 290°, Sturm (22 m/s) 1,5 m, Orkan (33 m/s) 3,4 m, ablandig höchstens die Hälfte nach unten; `stepSurge(ziel, dt)` führt den Stau mit 3 h Zeitkonstante nach.
- **Ereignisse**: `step(t)` meldet `onHighWater`, `onLowWater`, `onTurn` (Kenterung).
- **Überflutung** (`Flood`): `build(höhen, nx, nz, x0, z0, zelle, rand, gesperrt, bezugspegel, vergröberung)` rechnet je Zelle die Verbindungshöhe (Flutung nach Priorität vom Rand) und die Laufzeit der Tidewelle (kürzester Weg mit 0,7·√(g·Tiefe) bei mittlerem Hochwasser). `step(tide, t)`: Wasser je Zelle `Pegel(t − Laufzeit)` über der Verbindungshöhe, sonst Pfütze bis zur Schwelle (höchstens `maxPuddle` = 0,15 m tief, versickert mit `seepage` = 2 cm/h), Trockenzeit je Zelle (nach dem ersten Schritt oder einem Sprung in der Zeit geschätzt aus dem Pegel der letzten 12,5 h, seit Phase 5), Strömung (Durchfluss durch jede Linie gleicher Laufzeit, verteilt nach Tiefe^3/2; `currentVersion` zählt jede neue Rechnung, seit Phase 6), Statistik. Abfragen `surfaceAt`, `depthAt`, `connectedAt`, `spillAt`, `travelAt`, `dryHoursAt`, `velocityAt`; `fill(textur, basis, trocken)` für eine Wassertextur; `watch(x, z, r)` mit `onDry`/`onWet`; `buildCached(datei, …)` schreibt und liest eine Zwischendatei.

## Becken mit eigenem Spiegel (seit Rungholt Phase 12)

`Flood.basinMask` und `Flood.basinLevel`: in den markierten Zellen steht das Wasser auf einem Spiegel, den die App aus Zu- und Abfluss rechnet (ein Polder hinter einem gebrochenen Deich), statt auf dem Pegel am Rand mit Laufzeit. So läuft die Marsch hinter einer Bresche in Stunden voll und am Morgen langsam leer. `TideCheck` prüft es am Koog des Prüfbeckens.

## Benutzung

```java
Tide tide = Tide.husum();
double h = tide.levelAtMillis(System.currentTimeMillis());          // m über NHN
Tide.Turn hw = tide.next(stunden, true);                            // nächstes Hochwasser
Flood flood = Flood.buildCached(new File("cache.bin"), hoehen, nx, nz, x0, z0, 8, rand, koog, 1.6, 2);
flood.step(tide, stunden);                                           // in einem eigenen Faden
flood.fill(textur, tide.levelAt(stunden), -30f);                    // Spiegel über dem Meer, trocken −30
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Tide` | Partialtiden, Presets, Abfragen, Kalender, Windstau, Ereignisse |
| `Flood` | Überflutungskarte: Bau, Schritt, Pfützen, Trockenzeit, Strömung, Zwischendatei |
| `demo/TideCheck` | Prüfung ohne Fenster: Kalender des BSH, Mittelwerte, Spring und Nipp, Windstau, Ereignisse, Becken mit Priel, Mulde und Deich; endet mit „Alles in Ordnung.“ |
| `demo/TideDemo` | Becken 6 × 4 km mit Priel, Mulden, Hallig und Deich im Zeitraffer (FStyle); ohne Fenster `--bild datei.png stunden…` |

## Grenzen

- Die Überflutung kennt keine Trägheit und keine Dynamik: die Welle läuft mit fester Laufzeit, das Wasser steht in jeder Zelle waagerecht auf dem verzögerten Pegel. Für Sturmfluten mit Deichbruch rechnet `com.dan.wave`.
- Die Konstanten sind aus Zeiten angepasst, nicht aus einer harmonischen Analyse gemessener Wasserstände; keine Knotenkorrektur (18,6 Jahre). Für 1362 stimmen Spring und Nipp und die Mondphase, nicht die Minute.
- Pellworm, Norderhever, Windstau, Reibung der Welle und die Pfützentiefe sind gesetzt [G].
