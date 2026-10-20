# com.dan.kino – der Kinomodus

**Stand: Glacier Phase 16.** Neu in Glacier, gebaut zum Herauskopieren nach Haithabu, Lighthouse und jede weitere Szene mit Regiebüchern. Seit Rungholt Phase 15 unverändert in Rungholt (drei Regiebücher, Titel „Rungholt · Zweimal am Tag Land“; wartet das Regiebuch im Vorspann, ruft die App `schritt(0)`, damit die Titeltafel stehen bleibt).

Ein Regiebuch (Drehbuch) läuft wie im Kino: ganzer Bildschirm ohne Bedienfeld und HUD, Breitwand mit Balken, Vorspann und Abspann, Untertitel aus den Stichworten, Überblendung bei jedem Schnitt, eine ruhige Kamera, die nie ganz stillsteht, Filmkorn, und der Ton blendet mit dem Bild. Das Package kennt keine Szene. Es sagt der App jedes Bild, was über dem fertigen Bild liegen soll, und malt es auf Wunsch auch selbst.

Zum Herauskopieren reicht `src/com/dan/kino/`. Der Kern braucht nur Java 21 (Java2D). `gl/` braucht JOGL (OpenGL 4.3). `demo/` läuft ohne Grafikkarte.

## Was der Zuschauer sieht

| Teil | Ablauf (echte Sekunden) |
|---|---|
| **Balken** | oben und unten auf 2,39 : 1, fahren in 1,6 s ein und am Ende wieder heraus |
| **Vorspann** | 5 s Schwarz mit Titeltafel: Kopfzeile (gesperrt), Titel (Serif), feine Linie, Unterzeile (bis drei Zeilen); dann blendet das Bild in 2,5 s auf |
| **Untertitel** | der Text jedes Stichworts (in Regiebüchern der Teil nach „ \| “ und `sage`) mittig im unteren Balken, 3,5 bis 8 s je nach Länge, weich ein und aus; im Vorspann wartet er auf das Bild |
| **Überblendung** | bei jedem Schnitt (Ansicht, Fahrt, Boot) liegt das letzte Bild vor dem Schnitt 1,6 s abnehmend über dem neuen |
| **Schwenk** | ±3° über 70 s, sanft aus der Ruhe, nach jedem Schnitt in die andere Richtung; nur bei stehender Kamera |
| **Film** | Randabdunklung und feines Korn (24-mal je s neu), mit den Balken |
| **Abspann** | 3 s Abblende, dann eine Tafel: Titel groß, darunter Zeilen („Rolle: Name“ zweifarbig), 8 s; dann Ausklang 1,6 s |
| **Ton** | folgt dem Schwarz, im Schwarz noch 15 % |

Alle Zeiten sind Felder von `Kino` (`balkenS`, `titelS`, `aufblendeS`, `ueberblendeS`, `abblendeS`, `abspannS`, `ausklangS`, `schwenkGrad`, `schwenkS`, `filmStaerke`, `untertitelAn`). Schriften und Farben sind Felder von `KinoMaler`.

## Klassen

| Klasse | Aufgabe |
|---|---|
| `Kino` | der Ablauf: `beginnen(kopf, titel, unterzeile)`, `untertitel(text)`, `schnitt()` / `schnittFaellig()` / `geschnitten()`, `beenden(abspannZeilen)`, `abbrechen()`, `schritt(dt)`; Abfragen `balkenPx(w, h)`, `schwarz()`, `ueberblende()`, `tafel()`, `schwenk()`, `ton()`, `film()`, `laeuft()`, `sichtbar()`, `endeMelden()` |
| `KinoMaler` | malt eine `Kino.Tafel` (Titel, Untertitel, Abspann) mit Java2D in ein durchsichtiges Bild in Fenstergröße |
| `KinoBild` | dasselbe wie `KinoGl`, mit Java2D über ein `BufferedImage` (Demo, Prüfung, Szenen ohne GL) |
| `gl/KinoGl` | auf der Grafikkarte: `init(gl)`, `merken(gl, fbo, w, h)` (Bild vor dem Schnitt), `zeichnen(gl, fbo, w, h, sekunden)`, `dispose(gl)`; Shader `kino.vert` und `kino.frag` liegen daneben |
| `demo/KinoDemo` | ein kleiner Film aus vier gemalten Einstellungen; Run File |

## Einbau in eine Szene (so in Glacier, für Haithabu und Lighthouse genauso)

1. **Renderer:** ein Feld `final Kino kino = new Kino()`. Ein `KinoGl` legst du in `init` an, mit `kinoGl.init(gl)` in einem `try` (ohne GL läuft die Szene ohne Kino weiter).
2. **Regiebuch starten:** `kino.beginnen("Szene · Untertitel", buch.name(), buch.about())`, dann das Regiebuch wie bisher starten. Für die Untertitel gibt der Regie-Ablauf den Text jedes Stichworts an `kino.untertitel(text)` weiter (Glacier: `Regie.beiText`).
3. **Schnitte:** Ansicht, Fahrt und Boot laufen über `schnitt(Runnable)`. Wenn `kino.schnitt()` `true` meldet, merkst du dir die Handlung; sonst führst du sie gleich aus.
4. **Jeder Schritt** (`step(dt)`):
   - `kino.schritt(dt)` aufrufen.
   - Wenn das Regiebuch zu Ende ist und das Kino noch läuft: `kino.beenden(abspann)`.
   - Bei stehender Kamera die Änderung von `kino.schwenk()` auf die Blickrichtung legen.
   - Die Lautstärke auf `basis · kino.ton()` setzen.
5. **Jedes Bild,** nach der Nachbearbeitung und nach den Fotos des Regiebuchs (die bleiben so ohne Balken):
   ```java
   if (kino.schnittFaellig()) { kinoGl.merken(gl, fbo, w, h); /* gemerkte Schnitte ausführen */ kino.geschnitten(); }
   kinoGl.zeichnen(gl, fbo, w, h, System.nanoTime() / 1e9);
   ```
   Das HUD zeichnest du nur, wenn `!kino.laeuft()`.
6. **Fenster:**
   - Wenn `kino.laeuft()` umschlägt: Bedienfeld, Statuszeile und Titelleiste aus- oder einblenden, das Fenster auf die Bildschirmgröße setzen und den Mauszeiger verbergen.
   - `kino.endeMelden()` meldet einmal, dass alles vorbei ist.
7. **Tasten im Kino:** Esc ruft `kino.abbrechen()` (das Regiebuch läuft weiter), die Leertaste hält an, alle anderen Tasten und die Maus ruhen.

## Grenzen

- Die Balken verdecken einen Teil des Bilds. Gerechnet wird trotzdem das ganze Bild; ein engerer Bildausschnitt ist nicht vorgesehen.
- Es gibt immer nur eine Texttafel. Der Titel und der Abspann gehen dem Untertitel vor.
- Es gibt keine Musik. Der Ton ist der der Szene.
