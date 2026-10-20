# com.dan.weather – Wetterregie

Das Paket führt Regie über das Wetter und liefert nur Zahlen:

- Wind, Richtung, Böigkeit und die Böe des Augenblicks
- Bedeckung in Achteln, hohe Schicht, Wolkenart
- Regen, Sicht, Nebel
- Luftdruck mit Tendenz, Temperatur, Feuchte und Taupunkt
- Dünung und Gewitterstärke

Es kennt weder Meer noch Wolken noch Turm. Wer es einbindet, gibt die Zahlen an seine eigenen Packages weiter.

Der Kern braucht nur Java 21. Ein `gl/` gibt es nicht. Zum Herauskopieren reicht `src/com/dan/weather/`; nur `demo/WeatherDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Sieben Lagen:** klar, aufziehend, Front, Sturm, Gewitter, abklingend, aufklarend. Jede Lage hat Zielwerte, etwa für den Sturm 22 m/s aus 250°, 7,5/8 Nimbostratus, 6 mm/h, 987 hPa und Dünung 3,4 m.
- **Weiche Übergänge:** An den Grenzen gehen die Lagen über bis zu 1,5 h ineinander über. Jede Größe läuft ihrem Ziel mit eigener Trägheit nach, der Wind etwa in 0,8 h, der Luftdruck in 2 h und der Regen in 0,3 h.
- **Vorlauf:**
  - Der Luftdruck folgt dem Ziel von 5 Stunden später. Das Barometer fällt also, bevor der Sturm kommt; im Sturmtag sind es 11 hPa in den 6 Stunden, bevor Bft 9 erreicht ist.
  - Die Dünung läuft dem Wind 3 Stunden voraus.
  - Die Cirren ziehen 2 Stunden vor der Front auf.
  - Die Bedeckung schließt sich gut eine halbe Stunde vor dem Regen.
- **Böen:** ein Ornstein-Uhlenbeck-Vorgang in Echtzeit, damit der Wind auch im Zeitraffer nicht flackert. Die stärkste Böe nach WMO liegt beim (1 + 2,5 · Böigkeit)-fachen des Mittelwinds.
- **Freie Regie:** Eine Markow-Kette mit lognormalen Verweildauern wählt die nächste Lage, etwa klar 16 h, Sturm 7 h und Gewitter 2 h. Über 60 Tage sind Sturm und Gewitter rund 9 % der Zeit.
- **Szenarien:** Ein Regiebuch ist eine Liste von Schritten. Jeder Schritt hat eine Lage, eine Dauer, einen Text, Vorgaben für einzelne Größen und einen Kamerawunsch. Als Text ist es lesbar und schreibbar (`toText`, `parse`), für Datei oder Datenbank. Vorhanden sind „Ein Sturmtag“, „Ein Nebeltag“, „Ruhige Tage“ und seit Haithabu Phase 3 „Ein Nebelmorgen im Herbst“ (`autumnFogMorning()`: Strahlungsnebel nach windstiller Nacht, der nach Sonnenaufgang steigt und am Vormittag fort ist; 10 Stunden ab 05:00, Zeitraffer 240).
- **Handregler:**
  - `set(Var, Wert)` überschreibt eine Größe sofort.
  - `release()` („Regie weiter“) gibt alle zurück; die Größen gleiten dann mit ihrer Trägheit zur Regie.
  - `running = false` hält die Regie an. Die Zeitleiste steht, Handregler wirken weiter.
- **Zustand lesen:** `values()` gibt die Größen, wie sie gerade stehen, `handValues()` die Handregler (NaN = Regie), `scenarioStart()` den Beginn des laufenden Regiebuchs, `target(Phase)` die Zielwerte einer Lage. Damit lässt sich die Regie speichern und an derselben Stelle fortsetzen.
- **Uhr:** `update(t, realDt)` nimmt die Szenenzeit in Sekunden seit 1970. Springt die Uhr rückwärts oder um mehr als 6 Stunden, stellt sich das Wetter sofort auf die neue Zeit ein.
- **Hilfen:**
  - Beaufort nach WMO
  - Taupunkt nach Magnus
  - die Felder eines Dosenbarometers
  - Tendenz in Worten nach WMO (über 3 Stunden)
  - die Lage für einen Regenbogen nach einem Schauer (`showerEnd`)


**Seit Glacier Phase 11:** `summerDay` (Vorgabe 196, Südhalbkugel 15) legt fest, wann Sommer ist: Tagesgang, Gewitter, Stürme und Nebel nach der Jahreszeit folgen ihm.

## „Ein Sturmtag“

28,3 Stunden ab 6 Uhr, im Zeitraffer 420 : 1 gut vier Minuten:

| Uhrzeit | Lage | Schritt |
|---|---|---|
| 06:00 | klar | Morgenstille mit Dunst |
| 09:00 | aufziehend | Dünung und Cirren ziehen auf |
| 12:00 | Front | Front mit Regenvorhang |
| 15:00 | Sturm | Sturm, Bft 9 |
| 18:30 | Gewitter | Gewitter in der Dämmerung |
| 20:30 | Sturm | Sturmnacht mit Leuchtfeuer |
| 03:00 | abklingend | Der Sturm flaut ab |
| 08:00 | abklingend | ein letzter Schauer nach Sonnenaufgang |
| 08:48 | aufklarend | Aufklaren mit Regenbogen |

## Benutzung

```java
Weather w = new Weather();
w.start(Scenario.stormDay(), uhrInSekunden);     // oder w.startFree(t, Weather.Phase.KLAR)
// je Bild
Weather.Snapshot s = w.update(uhrInSekunden, echtzeitDt);
meer.wind = s.wind(); meer.swell = s.swellHeight();
wolken.oktas = s.oktas(); wolken.type = CloudType.valueOf(s.cloud().name());
regen.rate = s.rain(); barometer.hPa = s.pressure();
// Handregler
w.set(Weather.Var.WIND, 25);
w.release();                                     // „Regie weiter“
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Weather` | Lagen, Zielwerte, Zeitleiste, Trägheit und Vorlauf, Böen, Handregler, `Snapshot` |
| `Scenario` | Regiebuch mit Schritten, „Ein Sturmtag“, „Ein Nebeltag“, „Ein Nebelmorgen im Herbst“, „Ruhige Tage“, Text hin und zurück |
| `demo/WeatherChart` | der Verlauf als Bild: Lagen, Luftdruck, Wind mit Böen, Regen mit Gewitter, Bedeckung und Sicht |
| `demo/WeatherDemo` | Fenster mit Kurven und laufendem Zeiger, Szenarien, Zeitraffer, Handregler; `--bild datei.png` schreibt die Barometerkurve |
| `demo/WeatherCheck` | Prüfung ohne Fenster, Ende „Alles in Ordnung.“ |

## Jahreszeit (seit Haithabu Phase 12)

- `climate`: zwölf Monatsmittel der Temperatur (etwa aus `com.dan.season.Climate`, als `double[]`, ohne Abhängigkeit). Damit liegen die Lagen um das Mittel des Tags statt um 11,5 °C.
- `anomaly`: Abweichung des Tags vom Klima in K.
- Der Tagesgang reicht von ±1,7 °C im Januar bis ±3,9 °C im Juli.
- Im Winter sind klare Lagen und Seenebel bis 2,5 K kälter, Fronten und Stürme bis 1,5 K milder [G].
- Die freie Regie gewichtet die Übergänge nach dem Tag: Gewitter fast nur im Sommer, Seenebel öfter im Herbst, Stürme öfter im Winter.
- Ohne `climate` bleibt alles wie bisher.

## Grenzen

- Die Zielwerte sind typisch für ein Sturmtief über der Deutschen Bucht im Herbst, keine Messreihe.
- Es gibt keinen Ort: Das Wetter ist überall gleich. Wo es regnet, entscheiden die Wolken der App.
- Nebel kommt nur aus Szenarien; die freie Regie bildet keinen Seenebel aus Taupunkt und Wassertemperatur.

## Seit Glacier Phase 3: Föhnsturm und eigenes Klima

- Neue Lage `Phase.FOEHN` („Föhnsturm“): stürmischer, böiger, trockener und warmer Fallwind mit großer Sicht und
  wenig tiefen Wolken. Die Kette der Deutschen Bucht erreicht ihn nicht.
- Eine App kann das Klima ersetzen: `transitions` (Übergänge je Lage), `targets` (Zielwerte je Lage, Spalten wie
  `Var`) und `dwellHours` (Verweildauern). Glacier setzt das Klima Patagoniens (Westwind vom Eisfeld, Fronten aus
  West, Föhnsturm, selten Gewitter). Ohne diese Felder läuft alles wie bisher.
- Rückfluss in Phase 16.

## Seit Rungholt Phase 3: Orkan

- Neue Lage `Phase.ORKAN` („Orkan“): Wind um 33 m/s aus Westnordwest, geschlossene Decke, Regen, Sicht 3 km, Druck 965 hPa, hohe See – das Wetter einer Sturmflut [G]. Die Standardkette erreicht ihn nicht; eine App gibt ihn über `transitions` frei oder startet ihn mit `startFree(t, Phase.ORKAN)` oder im Szenario. Danach folgt Sturm oder abklingend.
- Tabellen von Apps mit neun Zeilen (`transitions`, `targets`, `dwellHours` aus der Zeit vor dem Orkan) bleiben gültig: fehlende Zeilen nehmen die Vorgabe, fehlende Spalten zählen als 0. `WeatherCheck` prüft beides.
