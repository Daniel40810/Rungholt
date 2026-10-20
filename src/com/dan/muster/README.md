# com.dan.muster – Vorlage für ein Element-Package

Dieses Package tut fast nichts: ein ruhiges Rauschfeld, das mit dem Wind stärker wird. Es zeigt aber, wie jedes Element-Package im Projekt Lighthouse gebaut ist, und ist die Vorlage für `ocean`, `spray`, `cloud`, `rain`, `wetglass` und alle weiteren. Es braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`.

Zum Herauskopieren reicht `src/com/dan/muster/`.

## Was es kann

- **Feld ohne Grafikkarte:** `valueAt(x, z, t)` liefert einen Wert von 0 bis 1, der mit dem Wind zieht.
- **Presets nach Beaufort:** `calm()`, `breeze()`, `gale()`, `storm()` und `beaufort(n)`.
- **Offene Werte:** Alle Felder sind öffentlich und zur Laufzeit änderbar.
- **Reproduzierbar:** Gleicher `seed`, gleiches Feld.
- **Qualitätsstufen:** `Quality.LOW` bis `ULTRA` regeln die Zahl der Oktaven.
- **Ereignis nach außen:** `scan(...)` meldet hohe Werte über die eigene Schnittstelle `Peak`.

## Benutzung

```java
Muster m = Muster.beaufort(6);
m.seed = 2026;
m.windFromDeg = 270;                      // Westwind
double v = m.valueAt(120, -40, t);        // 0..1
m.onPeak = (x, z, value) -> System.out.println("Spitze bei " + x + ", " + z);
m.scan(-100, -100, 200, 50, t);
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Muster` | Kern: Feld, Presets, Qualitätsstufen, Schnittstelle `Peak` |
| `gl/MusterGl` | Darstellung mit JOGL, Shader `muster.vert` und `muster.frag` als Ressourcen im Package |
| `demo/MusterDemo` | Fenster mit Beaufort-Regler; `--bild datei.png [bft]` schreibt ein Standbild |
| `demo/MusterCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.muster.demo.MusterDemo` zeigt das Feld mit einem Beaufort-Regler.
- `com.dan.muster.demo.MusterCheck` prüft seed, Presets, Wertebereich und Schnittstelle.
- Das Ant-Target `check-packages` übersetzt `muster` einzeln: zuerst den Kern nur gegen das JDK, dann mit `gl/` gegen JOGL, dann mit `demo/` gegen FStyle.

## Grenzen

- Das Feld ist ein einfaches Wertrauschen und hat keine physikalische Bedeutung.
- Die Darstellung in `gl/` ist nur ein Vollbild-Dreieck.

## Ein neues Package anlegen

1. Ordner `src/com/dan/muster/` kopieren und umbenennen, Package-Namen ersetzen.
2. Im Kern nur `java.*` importieren. Was von außen kommt, als Zahl oder eigene kleine Schnittstelle annehmen.
3. Shader in `gl/` als Ressourcen ablegen, die Klasse liest sie mit `getResourceAsStream`.
4. Prüfklasse und Demo anpassen, README nach diesem Muster schreiben.
5. `ant check-packages` muss grün sein.
