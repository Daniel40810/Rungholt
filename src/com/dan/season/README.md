# com.dan.season – das Jahr

Das Paket rechnet das Jahr an einem Ort: Schnee auf dem Boden, auf den Dächern und in den Bäumen, Eis auf einem flachen Gewässer, Reif in klaren Nächten, dazu den Stand der Pflanzen (Gras, Schilf, Laub, Herbstfärbung). Eingänge sind Datum und Stunde, Lufttemperatur, Niederschlag, Sonnenhöhe, Bedeckung und Wind. Ein Sprung im Kalender rechnet den Winter aus einem gewürfelten Jahr nach dem Klima vor. Der Kern braucht nur Java 21. Der Ordner `gl/` zeichnet fallenden Schnee und Eis mit JOGL (OpenGL 4.3, ohne Eckpuffer). Das Paket kennt weder Haithabu noch andere Pakete; wie Gelände, Häuser oder Bäume den Schnee zeigen, entscheiden die Benutzer mit den Zahlen von hier.

Zum Herauskopieren reicht `src/com/dan/season/`; nur `demo/SeasonDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Klima** (`Climate`): Monatsmittel der Temperatur, Monatssummen des Niederschlags und Regentage. `Climate.schleswig()` ist Schleswig an der Schlei (DWD 1991–2020, gerundet: Januar 1,5 °C, Juli 17,2 °C, 809 mm). Für Haithabu um 950 gelten dieselben Zahlen [G].
  - `meanTemp(doy)`: zwischen den Monatsmitten glatt verbunden.
  - `days(seed, year, from, to)`: ein gewürfeltes Jahr Tag für Tag. Tagesmittel = Klima + Abweichung mit Gedächtnis (AR(1), ρ = 0,8, im Winter ±3,5 K, im Sommer ±2,2 K). Regentage als Markow-Kette, Mengen exponentiell verteilt. Über 30 Jahre stimmen die Monatsmittel auf 0,6 K und die Jahressumme auf 1 %.
- **Jahr** (`Year`): der Zustand Stunde für Stunde.
  - **Schnee:** Niederschlag fällt unter 0,5 °C als Schnee, über 2,5 °C als Regen. Neuschnee hat 100 kg/m³ und setzt sich in rund acht Tagen auf 300 (nass 450). Er schmilzt nach Gradstunden (0,15 mm je K und Stunde, also 3,6 mm je Gradtag), durch die Sonne (Neuschnee nimmt 15 %, alter Schnee 40 % auf) und durch Regen.
  - **Dächer:** dazu Wärme aus den Häusern; höchstens rund 60 mm Schneewasser halten auf einem steilen Dach.
  - **Kronen:** 4 mm Schneefall füllen sie. Wind, Tauwetter und Sonne werfen den Schnee in Stunden ab.
  - **Eis:** Erst kühlt das Wasser aus (Zeitkonstante 5 Tage, Gefrierpunkt −0,4 °C für Brackwasser [G]). Dann wächst das Eis nach Stefan, h² = h₀² + α²·FDD mit α = 2,7 cm/√(K·d); unter Schnee wächst es langsamer. Wind über 4 bis 9 m/s hält dünnes Eis offen. Es taut von oben durch Luft und Sonne.
    - `iceDepth` gibt die Wassertiefe, bis zu der das Gewässer zugefroren ist: zuerst die flachen Ränder, ab 8 cm Eis bis 5,6 m Tiefe.
  - **Reif:** in klaren, windstillen Nächten unter 1 °C; die Sonne nimmt ihn am Vormittag.
  - **Pflanzen nach dem Tag:** Gras 0..1 (Mai saftig, Winter matt), Schilf 0 Sommer … 0,75 Herbst … 1 Winter, Laub und Herbstfärbung für Wald in der Ferne. Mitte Oktober gilt Gras 0,5, Schilf 0,75, Herbst 0,5.
  - `settle(year, doy, hour)` rechnet ab dem 1. September des Vorjahrs vor; `step(...)` geht mit dem Wetter des Augenblicks weiter und rechnet bei einem Sprung von mehr als drei Stunden selbst vor. `trace(year, hour)` gibt das ganze Jahr Tag für Tag (für Kurven).
  - `State`: Schneehöhe, Bedeckung, Nässe, Dachschnee, Kronenschnee, Eisdicke, Eisgrenze, Eisanteil, Reif, Wassertemperatur, die Pflanzen, die Rückstrahlung des Bodens für den Himmel, Tag im Jahr und Lufttemperatur. `describe()` gibt eine Zeile Text.
- **Grafik** (`gl/`):
  - `SnowfallGl`: Flocken in einem Würfel von 36 m um das Auge. Sie sinken mit 1 m/s, taumeln und treiben mit dem Wind. 3 mm/h ist dichtes Treiben (120 000 Flocken).
  - `IceGl`: Eis knapp über dem Wasserspiegel, wo der Grund flacher liegt als die Eisgrenze; der Rand ist ausgefranst. Junges Eis am Rand ist schwarz und glatt, älteres grauweiß mit Rissen. Darauf liegen Schneefahnen im Wind, dazu Glanz zum Himmel.
  - Licht wie die Flächen der App: Sonne und Mond als Bestrahlung, Himmel als Bestrahlung/π.

## Südhalbkugel (seit Glacier Phase 11)

- `Climate.elCalafate()`: El Calafate am Lago Argentino, Servicio Meteorológico Nacional 1991–2020 (Januar 13,9 °C, Juli 0,6 °C, 161 mm an 43 Tagen). `Climate.peritoMoreno()`: vor dem Gletscher [G], 1,5 K kühler, fünfmal so viel Niederschlag (rund 800 mm) an zweieinhalbmal so vielen Tagen, im Winter etwas mehr Streuung.
- `Climate.summerDay` (Vorgabe 196; `south()` setzt 15): wann die Streuung der Tage am kleinsten ist.
- `Year.southern`: die Pflanzen (Gras, Schilf, Laub, Herbstfärbung) laufen um ein halbes Jahr versetzt. Das Vorrechnen ab dem 1. September des Vorjahrs bleibt (Ende des Südwinters, kaum Schnee am Ufer).
- `gl/IceGl.Input.clip`: Eis nur in einem Rechteck (etwa einem Seearm).
- Prüfung (`SeasonCheck`): El Calafate wie das SMN, vor dem Perito Moreno in 30 Jahren am 16. Juli 6-mal Schnee am Ufer und 17-mal Eis, am 16. Januar nie; Herbstfärbung im April.

## Nordfriesland (seit Rungholt Phase 9)

- `Climate.nordfriesland()`: die Küste bei Husum (DWD 1991–2020 gerundet [G]: Januar 1,5 °C, Juli 17,3 °C, rund 870 mm, der meiste Regen von August bis Dezember). Mit Startwert 26 hat der Januar 2026 einen Wintereinbruch: am 21. liegen 17 cm Schnee.
- In Rungholt hängt `Year` an der Verdrahtung (`Wiring.Jahreszeit`): Schnee und Reif auf dem Land und den höchsten trockenen Watten, Queller im Herbst rot, Salzwiese im Winter braungrau, Halme und Bodendecke nach Tag und Schnee, Dachschnee auf dem Friesenhaus, `SnowfallGl` unter 2,5 °C; die Abweichung des gewürfelten Tags geht an die Wetterregie (`weather.anomaly`).

## Benutzung

```java
Year jahr = new Year();                        // Klima Schleswig, Startwert 14 (ein Winter mit Schnee und Eis)
Year.State s = jahr.settle(950, 16, 12.0);     // 16. Januar 950, Mittag
// je Bild: Szenenzeit seit dem letzten Bild und das Wetter des Augenblicks
s = jahr.step(dt, 950, doy, stunde, luftC, niederschlagMmH, sonnenhoeheGrad, achtel, windMs);
double schnee = s.snowDepth() * s.snowCover();       // m auf ebenem Boden
boolean eis = s.ice() > 0.005;                        // Eis bis s.iceDepth() m Wassertiefe
double regen = niederschlagMmH * Year.rainFraction(luftC), flocken = niederschlagMmH - regen;
```

Grafik: `snowfall.draw(gl, view, light, flocken, windTo, wind, t)` nach den Flächen; `ice.draw(gl, view, light, input)` nach dem Wasser, mit Tiefentest.

## Demo und Selbsttest

- `demo/SeasonDemo` zeigt ein Jahr als Kurven (Luft, Schnee, Dächer, Eis, Reif, Pflanzen), mit Reglern für Startwert und Temperatur. `SeasonDemo --bild datei.png` schreibt das Jahr 950 als Bild.
- `demo/SeasonCheck` prüft ohne Fenster: Klima und gewürfelte Jahre, Regen oder Schnee, Schmelze und Setzung, Eis nach Stefan, Auskühlen des Wassers, Reif, Dächer, Kronen, Pflanzen, eine Statistik über 30 Jahre, die vier Tage der Abnahme, Sprung im Kalender, Startwert. Endet mit „Alles in Ordnung.“

## Grenzen

- Ein Wert für die ganze Fläche: Verwehungen, Mulden und Schatten verteilt der Benutzer (das Gelände von Haithabu mit Rauschen und Krümmung).
- Schnee setzt sich nur nach der Zeit; Druck und Temperatur im Schnee rechnet das Paket nicht.
- Das Wasser ist eine Schicht mit einer Temperatur; Strömung, Salz und Zufluss eines Bachs halten in Wirklichkeit Stellen offen.
- Die Pflanzen folgen dem Kalender, nicht der Wärmesumme: ein warmer März treibt nicht früher aus.
