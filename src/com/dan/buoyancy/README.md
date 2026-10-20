# com.dan.buoyancy – Tonnen und Schiffe

Alles, was auf dem Wasser schwimmt: Leuchttonnen an einem Fahrwasser und gelegentlich ein Küstenmotorschiff, das es entlangfährt, mit Tauchen, Stampfen und Rollen auf einer beliebigen Wasseroberfläche. Das Package braucht nur Java 21, der Ordner `gl/` zusätzlich JOGL, der Ordner `demo/` zusätzlich `FStyle.jar`. Zum Herauskopieren reicht `src/com/dan/buoyancy/`.

## Was es kann

- **Schwimmen nach dem Streifenmodell:** Ein Rumpf ist eine Menge senkrechter Säulen. Jede trägt so viel, wie Wasser über ihrem Kiel steht (ρ g a d), gedämpft nach der Geschwindigkeit gegen das Wasser. Daraus kommen Tauchen, Stampfen und Rollen; die Wasserlinie liefert die Formstabilität, der Schwerpunkt die Gewichtsstabilität, Schlingerkiele eine quadratische Rolldämpfung. Lange Dünung reitet der Körper ab, kurze Wellen glättet er.
- **Geprüfte Hydrostatik:** Die Tonne (2,4 m, 5,1 t) schwimmt auf 1,10 m, taucht mit 2,6 s und rollt mit 3,2 s; der Kümo (82 × 12,5 m, 4 277 t) schwimmt auf 4,46 m ohne Trimm, taucht mit 5,8 s, stampft mit 5,4 s und rollt mit 12 s (GM 0,86 m). Alles auf ein paar Prozent wie von Hand gerechnet.
- **Tonnen nach IALA, Region A:** Backbord rot mit Stumpf-Toppzeichen (`Fl R 4s`), Steuerbord grün mit Kegel (`Fl G 4s`), Ansteuerungstonne rot-weiß mit Ball (`Iso 8s`), Kardinaltonne Nord (`Q`). An der Kette mit rund 40 s Eigenperiode, Strom und Wind ziehen sie ein paar Meter aus der Mitte. `layFairway` legt ein Fahrwasser aus.
- **Tonnenfeuer:** `Rhythm` liest kurze Kennungen: `F`, `Fl`, `Fl(n)`, `LFl`, `Q`, `VQ`, `Iso`, `Oc`, `Mo(X)` mit Farbe und Periode.
- **Schiff mit Positionslichtern nach KVR:** zwei Topplichter (das achtere höher) über 225°, Seitenlichter rot und grün über je 112,5°, Hecklicht über 135°, Sektoren mit 2° weichem Rand. Lichtstärken nach Anhang I: 94 cd für 6 sm, 12 cd für 3 sm.
- **Route und Verkehr:** Ein Schiff fährt seine Wegpunkte mit begrenzter Drehrate ab und giert im Seegang. Im Mittel alle 25 Minuten kommt eines (lognormal gestreut), im Sturm seltener. Am Bug meldet es Stöße ins Wasser (`onWake`) für Ringwellen.
- **Brücke als Kamera:** `bridgeView()` gibt Auge, vorn, oben und rechts mit Stampfen und Rollen; `windowWorld(i)` die drei Frontscheiben in der Welt; `Wiper` schwingt weich und gibt die überstrichenen Punkte zum Wegwischen von Tropfen.
- **Netze aus Code:** `Mesh` baut Rumpf mit Sprung und Wasserpass, Lukendeckel, Back, Deckshaus mit Fenstern, Brückennocken, Schornstein, Masten und Lampen, die Brücke von innen mit Pult und Anzeigen, den Wischer und die vier Tonnen mit Gittergerüst und Toppzeichen.
- **Presets:** `calm()`, `breeze()`, `gale()`, `storm()`, `beaufort(n)`. **Reproduzierbar:** gleicher `seed`, gleiche Schiffe. **Stufen:** `Quality.LOW` bis `ULTRA` regeln, wie oft das Meer abgefragt wird, und wie viele Schiffe gleichzeitig fahren.

## Eisberge (seit Glacier Phase 9)

- **`Berg`:** ein beliebiger geschlossener Körper (Dreiecksnetz) als Starrkörper mit sechs Freiheiten. Beim Anlegen wird er in Proben zerlegt (ein Raster im Inneren, 40 bis 120 Würfel, Volumen genau wie das Netz). Je Probe Auftrieb nach dem Anteil unter Wasser, Widerstand im Wasser (senkrecht linear, an der Oberfläche quadratisch) und Wind über Wasser; daraus Schwimmen, Rollen und Kentern von selbst. Gletschereis 900 kg/m³ im Süßwasser: 90 % unter Wasser.
- **Schmelze mit wanderndem Schwerpunkt:** Proben an der Oberfläche verlieren Volumen (m je Tag unter Wasser, an der Wasserlinie, an der Luft); Masse, Schwerpunkt und Trägheit werden neu gerechnet. `pose()` gibt die Modellmatrix mit der Schrumpfung.
- **Grund:** `Sea.bed(x, z)` (neu, Vorgabe: keiner). Proben unter dem Grund werden höchstens eine Probe tief zurückgedrückt (Stranden, an einer Wand nicht hinaufschleudern).
- **`Bergs`:** alle Berge eines Sees mit festem Schritt (1/60 s), Stößen zwischen den Proben an der Oberfläche (Feder und Dämpfer, nach Hüllkugeln sortiert), Höchstzahl, Ereignissen `onCapsize`, `onMelted`.
- **`BrashField`:** Treibeis als Feld (bedeckter Anteil je Zelle), treibt mit 3 % des Winds und der Strömung, schmilzt in Stunden, über Land keins. `gl/BrashGl` lädt es als Textur hoch.
- **Prüfung** `demo/BergCheck`: Schwimmlage 90,0 %, eine stehende Platte 40 × 40 × 8 m kentert nach rund 20 s (Handrechnung BM 0,15 m < BG 2,0 m), Treiben mit 1,4 % des Winds, zwei Tage Schmelze, Stöße, Stranden, Treibeis, 200 Berge in 0,4 ms je Schritt. `BergCheck --bild datei.png` zeigt das Kentern von der Seite.

## Katamaran und mehr (seit Glacier Phase 10)

- **`Catamaran`:** ein Ausflugskatamaran 26 × 9,2 m mit zwei Rümpfen (je 2,3 m breit, 6,9 m zwischen den Mitten), Tiefgang 1,4 m, 132 t, GM 5,8 m (die große Formstabilität eines Katamarans), Tauchen 3,1 s, Rollen 3,7 s. Fährt eine Route aus Wegpunkten mit eigener Fahrt je Punkt (`via(x, z, m/s)`), lenkt mit begrenzter Drehrate ein, wird in engen Kurven langsamer und treibt mit der Strömung aus `Sea.velocity` (die lange Welle eines Abbruchs schiebt ihn). `step(sea, dt)` rechnet alles, das Wasser wird alle 0,05 s abgefragt (`sampleInterval`). `moor` legt ihn fest, `start` legt ab. `eye(port)` und `pose(port)` geben das Auge eines Gasts auf dem Sonnendeck mit Kurs, Stampfen und Rollen; `motion()` Hub und Winkel seit dem Start.
- **`Mesh.catamaran`:** Rümpfe (weiß, unter Wasser rotbraun, blauer Streifen), Brückendeck, Netz vorn, Salon mit Fensterband, Sonnendeck mit Geländer, Steuerhaus, Treppe. **`Mesh.beam(a, b, w, h)`:** ein Balken zwischen zwei Punkten (Geländer, Pfosten, Roste).
- **`BuoyancyGl.drawMeshes`:** beliebige Netze mit Modellmatrix (oder in Weltkoordinaten), einmal hochgeladen; `forget` gibt eines frei.
- **`Berg.topVertex()`, `Berg.vertexWorld(i)`:** die höchste Ecke eines Bergs in der Welt (für Möwen, die auf ihm sitzen).

## Kogge (seit Rungholt Phase 11)

- **`Cog`**: eine Kogge wie die Bremer Kogge von 1380: 23,3 × 7,6 m, 2,25 m Tiefgang, Schwerpunkt auf der Wasserlinie (Ballast), GM 0,7 m; Säulen in drei Reihen über die Breite. Fährt eine Route (`via`, `start`, `go` für die Rückfahrt ab dem jetzigen Ort), langsam zum letzten Punkt, Segel 0..1 folgt der Fahrt in 20 s. **Trockenfallen**: liegt der Grund (`Sea.bed`) über dem Kiel, sitzt sie aufrecht auf ihrem flachen Boden, bis das Wasser sie wieder trägt (`grounded()`, `underKeel`).
- **`Mesh.cog(c, segel)`**: geklinkerter Rumpf mit flachem Boden und Sprung, geneigter Vordersteven, Ruder, Achterkastell mit Brüstung, Mast mit Mars, Rah, Wanten und Vorstag; das Segel an der Rah aufgetucht oder gesetzt mit Bauch, rot-weiß gestreift [G].
- `BuoyancyCheck` prüft: schwimmt auf der Konstruktionswasserlinie, fällt bei Ebbe aufrecht trocken, schwimmt mit der Flut wieder auf, fährt zwei Wegpunkte ab und tucht das Segel am Ziel auf.

## Benutzung

```java
Buoyancy b = Buoyancy.beaufort(5);
b.sea = new Sea() {                       // das eigene Meer hineingeben
    public double height(double x, double z) { return ocean.heightAt(x, z); }
    public double[] velocity(double x, double z) { return ocean.velocityAt(x, z); }
    public double level() { return tide; }
};
b.fairwayX = 900; b.fairwayDeg = 330;
b.layFairway(220, 900, 4);
b.onWake = (x, z, s) -> ocean.addImpulse(x, z, s);
b.step(dt);                                // je Bild
for (LightPoint l : b.lights(eyeX, eyeY, eyeZ)) drawGlare(l);

Ship s = b.spawn(true, 3000);              // ein Schiff von Hand
double[][] v = s.bridgeView();             // Auge, vorn, oben, rechts
s.wiper.on = true;
```

## Aufbau

| Klasse | Aufgabe |
|---|---|
| `Floater` | Streifenmodell: Säulen, Hydrostatik, Eigenperioden, Integration, Modellmatrix |
| `Buoy` | Leuchttonne: Säulen, Verankerung, Feuer |
| `Ship` | Kümo: Säulen aus Wasserlinie und Tiefgang, Route, Lichter nach KVR, Brücke, Frontscheiben |
| `Wiper` | Scheibenwischer: Winkel, Blatt, überstrichene Punkte |
| `Rhythm` | Kennung eines Tonnenfeuers |
| `Mesh` | Netze in Java |
| `Buoyancy` | Tonnen, Schiffe, Verkehr, Lichter, Presets |
| `Sea`, `LightPoint` | eigene kleine Schnittstellen |
| `gl/BuoyancyGl` | Netze hochladen und mit ihrer Modellmatrix zeichnen, Selbsttest |
| `demo/BuoyancyDemo` | Fenster mit Reglern; `--bild datei.png [breite]` schreibt fünf Lagen und die Bewegungen als Kurven |
| `demo/BuoyancyScene`, `DemoSea` | eigene See aus zwölf Wellenzügen, Himmel, Lichtpunkte |
| `demo/BuoyancyCheck` | Prüfung ohne Fenster, endet mit „Alles in Ordnung.“ |

## Demo und Selbsttest

- `com.dan.buoyancy.demo.BuoyancyDemo` zeigt das Fahrwasser im Seegang, nachts mit Feuern und Positionslichtern, und den Blick von der Brücke mit Wischer.
- `com.dan.buoyancy.demo.BuoyancyCheck` prüft Tiefgang, Stabilität, Eigenperioden, das Mitgehen in Dünung, die Verankerung bei Bft 10, die Route, die Lichtsektoren, die Kennungen, den Wischer, seed und Stabilität bei Bft 12.
- `BuoyancyGl.selfCheck` legt 64 Punkte mit der Modellmatrix auf der Grafikkarte in die Welt und vergleicht mit dem Prozessor.

## Grenzen

- Die Säulen tragen senkrecht; Wellenkräfte quer (Abdrift, Schlagen der See) und das Gieren aus Wellen sind nur genähert.
- Das Schiff fährt nach Route, nicht nach Ruder und Maschine; es weicht niemandem aus.
- Keine Bugwelle und kein Kielwasser als Netz; nur Stöße ins Wasser für ein Meer, das Ringwellen kann.
- Die Brücke von innen ist ein Rahmen mit Pult, kein ausgebautes Steuerhaus.
