# com.dan.seal – Seehunde

**Stand: Körper und Verhalten (Phase 7).**

Seehunde als Körper und Verhalten: Sie kommen mit dem fallenden Wasser auf die Bank, liegen nahe am Wasser mit Abstand zueinander und dem Kopf zum Wasser, heben die Köpfe, liegen in der Sonne als Banane auf der Seite, gehen mit der Flut und flüchten vor Figuren und Booten ins Wasser. Das Package kennt kein Watt; Grund, Wasser, Bänke und Störer kommen von außen. Der Kern braucht nur Java 21, `gl/` zusätzlich JOGL, `demo/` FStyle. Wiederverwendbar für Kegelrobben (Lighthouse), Otter und Robben in jeder Küstenszene.

Zum Herauskopieren reicht `src/com/dan/seal/`.

## Was es kann

- **Körper** (`SealBody`): ein Netz von rund 1 600 Dreiecken in Körperkoordinaten (Länge 1): runder Kopf mit kurzer Schnauze, gestreckter Rumpf, am Bauch flacher, kleine Vorderflossen, zwei aufrechte Hinterflossen als Fächer [G, nach Fotos und Maßen von *Phoca vitulina*]. Eine biegsame Wirbelsäule: Neigung (Kopf heben, Schwanz heben, Buckel, ganze Neigung) und Gierung (Kopf drehen, Schwanzschlag) je Stelle, integriert in zehn Schritten vom Schwerpunkt aus; die Länge bleibt bei jeder Biegung. Danach die Rolle (auf der Seite). Java und `gl/seal.vert` rechnen dasselbe.
- **Haltungen**: liegen, Kopf heben (mit Umschauen), Banane, robben (Buckel und Vorderflossen im Takt der Strecke), im Wasser stehend mit dem Kopf über der Fläche („Flasche“), schwimmen mit Schwanzschlag, tauchen. Langsame Übergänge.
- **Verhalten** (`Colony`): Fällt die Bank trocken (Anteil trockener Punkte), warten die Tiere je nach Tier 4 bis 35 min [G], dann schwimmen sie bis vor einen freien Platz und robben in Schüben mit Pausen an Land. Der Platz: trocken, 3 bis 16 m vom Wasser (je Tier), 1,3 Körperlängen Abstand, zur Gruppe hin, bei steigendem Wasser mindestens 0,3 m über dem Spiegel. Läuft das Wasser weit weg, rücken sie nach. Steigt es über 0,15 m an ihrem Platz, gehen sie mit der Flut. 15 % bleiben am Tag im Wasser, nachts 45 % [G]. Im Wasser treiben sie um die Bank und tauchen.
- **Störung**: ab 1,4-facher Fluchtdistanz heben alle den Kopf; unter der Fluchtdistanz (300 m [G]) robben sie mit 1,3 m/s ins nächste Wasser vom Störer weg, wer flieht, reißt die Nachbarn in 30 m mit; danach 25 min [G] im Wasser. Ein Boot im Wasser: die Schwimmer weichen aus.
- **Heuler** im Juni und Juli: ein gutes Viertel der Weibchen hat ein Junges, das neben ihr liegt, ihr folgt, mit ihr flieht und ruft.
- **Fell**: nass dunkel und glänzend, trocknet auf der Bank (halb in 20 min in der Sonne [G]); Grau bis Graubraun je Tier, gefleckt, Augen, Nase, Schnurrhaarpolster (`gl/seal.frag`). Ein Schatten je Tier an Land: Kontaktschatten und Schlagschatten zur Sonne (`gl/shadow.*`).
- **Ereignisse**: `onCall` (Rufe der Heuler, Knurren bei zu großer Nähe) für den Klang, `onTrack` (Robbenspur alle 35 cm auf trockenem Grund) für das Watt.
- Presets `harbourSeal()`, `greySeal()`; `Quality` regelt die Zahl (21, 36, 51, 60 Tiere bei höchstens 60).

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Seal` | Art: Presets, offene Werte, Regeln |
| `SealBody` | Netz und Biegung (wie `seal.vert`), tiefster Punkt, Kopf |
| `Colony` | Gruppe an ihren Bänken: Verhalten, Haltungen, Lage; `World`, `Bank`, `Disturber` als Eingänge |
| `gl/SealGl` | Netz, Instanzen, Schatten; `seal.vert`, `seal.frag`, `shadow.vert`, `shadow.frag` |
| `demo/SealDemo` | eine Bank mit Priel im Zeitraffer, Wanderer auf Knopfdruck; `--bild datei.png` schreibt die Haltungen, die Tide gerendert und als Karte |
| `demo/SealCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |
| `demo/DemoBank`, `demo/SealScene` | Bank und Bühne der Demo |

## Einsetzen

```java
Colony c = new Colony(Seal.harbourSeal());
c.world = new Colony.World() { ... ground(x, z) ... water(x, z) /* NaN, wo trocken */ };
c.banks.add(new Colony.Bank(x, z, radius));
c.onTrack = (x, z, dx, dz) -> watt.addTrack(x, z, dx, dz, 2);
c.settle();                                    // nach Sprüngen der Uhr
c.disturbers.clear(); c.disturbers.add(new Colony.Disturber(fx, fz, 1));
c.month = 7; c.sunAltDeg = 40;
c.step(szenenSekunden);                        // in Teilschritten von höchstens 1 s
// im GL-Faden: nach dem Gelände, vor dem Wasser
sealGl.update(gl, c); sealGl.draw(gl, view, light);
```

## Grenzen

- Die Tiere liegen waagerecht mit der Neigung des Grunds längs der Nase; quer kippen sie nur mit ihrer Rolle.
- Keine Berührung untereinander außer dem Abstand der Plätze; im Wasser schwimmen sie durcheinander.
- Haltungen und Zeiten sind gesetzt [G] und gegen Fotos gestaltet, nicht gegen Filmaufnahmen abgenommen.
