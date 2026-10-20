# com.dan.smoke – Rauch aus Firsten und Feuern

Das Paket lässt Rauch aus vielen Quellen aufsteigen: aus Herdfeuern unter Rauchlöchern, Schmieden, offenen Feuern, einem brennenden Haus. Jede Quelle gibt eine Fahne nach den Formeln von Briggs. Sie steigt mit ihrem Auftrieb, knickt im Wind ab und zerfasert; in stabiler Abendluft steigt sie nur wenige Meter und legt sich dann flach über die Dächer. Der Kern braucht nur Java 21. Er rechnet die Fahnen und wählt, welche nah gezeichnet werden; die Dichte an einem Ort liefert er für Licht und Sicht. Der Ordner `gl/` zeichnet jede Fahne als Kette weicher Bälle mit JOGL (OpenGL 4.3, ein Speicherpuffer, kein Eckpuffer). Der Ort jedes Balls ist eine Formel aus Fahne, Nummer und Alter, die im Shader Zeile für Zeile so steht wie im Kern. Nichts wird gespeichert oder zurückgelesen, und hundert Säulen kosten wenig.

Das Paket kennt weder Haithabu noch andere Pakete. Zum Herauskopieren reicht `src/com/dan/smoke/`; nur `demo/SmokeDemo` braucht zusätzlich `FStyle.jar`.

## Was es kann

- **Quellen** (`Source`): Austritt (x, y, z), Wärmeleistung in kW, Rauchmenge 0..1, Schwärze 0..1 (weißer Holzrauch bis schwarzer Rauch aus Reet und Teer), Zufall. `Source.hearth` ist ein Herdfeuer von 10 kW.
- **Aufstieg** (`Plume`):
  - Auftriebsfluss F = g · Q / (π c<sub>p</sub> ρ T), für ein Herdfeuer 0,09 m⁴/s³.
  - Frei steigt die Fahne um Δh = (3F / 2β²)<sup>1/3</sup> · u<sup>−1/3</sup> · a<sup>2/3</sup>.
  - In stabiler Luft endet der Aufstieg bei Δh<sub>e</sub> = 2,6 · (F / u N²)<sup>1/3</sup>; zwischen beiden geht es weich über (tanh).
  - Ein Herdfeuer steigt so am Tag gut 35 m über das Dach, in der Abendinversion knapp 10 m, ein brennendes Haus abends über 60 m.
- **Wind und Breite:**
  - Die Fahne zieht mit dem Wind in Dachhöhe (70 % des Winds in 10 m, mindestens 0,5 m/s).
  - Sie wird breiter mit σ² = (0,4 Δh)² + 2 K a + 0,09 m².
  - Unter der Inversion breitet sie sich seitlich aus und bleibt flach.
- **Stabilität** (`Smoke.stability`): aus Sonnenhöhe, Wind und Bedeckung, 0 neutral bis 1 kräftige Inversion. Sie ist am Abend und in der Nacht bei schwachem Wind und klarem Himmel am stärksten, nach den Pasquill-Klassen E und F.
- **Bälle:**
  - Jede Fahne besteht aus n Bällen im Altersabstand Δa. Ball i hat das Alter (i + φ) · Δa und eine Nummer, die er sein Leben lang behält; aus der Nummer folgen Versatz, Größe und Deckkraft.
  - Die Zeit geht als ganze Schritte und Bruchteil an die Grafikkarte; so rechnet sie auch nach Stunden genau.
  - Die Grafikkarte rechnet dasselbe wie der Prozessor: `SmokeGl.selfCheck` gleicht ab, auf Mesa ± 0,2 mm.
- **Auswahl** (`Smoke.select`): Die nächsten Fahnen werden mit vielen Bällen gezeichnet (Stufe MITTEL: 24 Fahnen zu 40 Bällen), die übrigen bis 4 km mit wenigen großen (10). Ausgeschaltete Fahnen fallen weg.
- **Dichte** (`Smoke.density`): Summe der Fahnen als Gaußsche Wolken um ihre Mittellinie, für Licht, Sicht und Prüfung.
- **Grafik** (`gl/SmokeGl`):
  - Weiche Bälle mit Flocken aus Rauschen. Das Sonnenlicht wird vorwärts gestreut (Henyey-Greenstein), der Himmel hellt als Umgebung auf, der Ball beschattet sich selbst (unten dunkler).
  - Feuerschein von unten: bis 8 Punktlichter, Rauch über dem Herd glüht rot. Dazu Dunst.
  - Weiche Ränder an der Szene über eine Tiefenkopie.
  - Ausgabe vormultipliziert in ein HDR-Ziel.

## Benutzung

```java
Smoke smoke = new Smoke();
smoke.setSources(List.of(Source.hearth(12, 5.6, -40, 1), new Source(30, 3, -10, 30, 0.9, 0.35, 2)));
// je Bild:
smoke.windFromDeg = 245; smoke.windSpeed = 2.5;
smoke.stability = Smoke.stability(sonnenhoehe, smoke.windSpeed, bedeckung);
smoke.step(dt);
smoke.select(auge[0], auge[1], auge[2]);

SmokeGl gl = new SmokeGl();
gl.init(gl4);
gl.update(gl4, smoke);
gl.draw(gl4, view, light, 1, smoke.time());     // nach dem Undurchsichtigen, mit Tiefenkopie in view.depthTexture
String check = gl.selfCheck(gl4, smoke);         // „Grafikkarte = Prozessor: Rauch ± 0,1 mm …“
```

## Demo und Selbsttest

- `com.dan.smoke.demo.SmokeDemo` zeigt ein Dorf aus 140 Dächern, aus hundert steigt Rauch. Es gibt drei Szenen:
  - `abend`: windstill, die Fahnen legen sich flach über die Dächer.
  - `tag`: Wind 4 m/s, lange Fahnen.
  - `nah`: Schmiede, Herdfeuer und ein brennendes Haus.

  Regler für Wind, Inversion und Sonne. `SmokeDemo --bild datei.png szene breite höhe [sekunden]` rechnet ein Bild ohne Fenster und gleicht Grafikkarte und Prozessor ab.
- `com.dan.smoke.demo.SmokeCheck` prüft:
  - Auftriebsfluss und Endhöhen (Tag, Abend, Wind, großes Feuer) sowie den Verlauf des Aufstiegs.
  - Das Abknicken im Wind und das Zerfasern, flach unter der Inversion.
  - Dass ein Ball über die Zeit seine Nummer und seinen Versatz behält.
  - Die Auswahl nah/fern und die Dichte in Lee: unter der Inversion bleibt der Rauch unten.
  - Die Stabilität nach Tageszeit und Wind.
  - Den Rauch im Raum: Gleichgewicht der Schicht (Fahne = Abfluss), Schicht ohne Rauchlöcher unter dem Türsturz, Füllen und Leeren, Dichte in Schicht, Dunst und Fahne.

  Am Ende steht „Alles in Ordnung.“.

## Rauch im Raum (seit Haithabu Phase 11)

`RoomSmoke` rechnet den Rauch eines Feuers in einem geschlossenen Raum nach dem Zwei-Zonen-Modell des Brandschutzes:

- Die Fahne über dem Feuer reißt Luft mit (Heskestad: ṁ = 0,071 · Q<sub>c</sub><sup>1/3</sup> · z<sup>5/3</sup> · (1 + 0,026 · Q<sub>c</sub><sup>2/3</sup> · z<sup>−5/3</sup>)).
- Oben sammelt sich eine warme Schicht. Sie fließt durch die Teile der Öffnungen ab, die über ihrer Unterkante liegen (C<sub>d</sub> = 0,6, Auftrieb aus der Erwärmung).
- Im Gleichgewicht trägt die Fahne an der Unterkante so viel hinein, wie oben hinausgeht (Halbierung). Die Erwärmung folgt aus der Wärme der Fahne; die Hälfte geht an Wände und Dach [G].
- Dichte: 3 % des verbrannten Holzes werden Rauch [G], 7,6 m²/g Auslöschung, verteilt auf den Volumenstrom, der abfließt. Unter der Schicht bleiben 12 % als Dunst [G].
- Die Schicht füllt und leert sich mit der Zeit Rauminhalt / Abfluss, einige Minuten.

Ein Herdfeuer von 12 kW in einer Halle von 17 × 6 m mit zwei Türen und zwei Rauchlöchern gibt eine Schicht ab 2,4 m, 7 K wärmer, mit 9 m Sicht; ohne Rauchlöcher sinkt sie unter den Türsturz.

`gl/RoomSmokeGl` zeichnet den Raum als Volumen über die Szene (`room_smoke.frag`): Der Blickstrahl wird in der Hülle (Kasten mit Satteldach) bis zur Szenentiefe abgetastet, mit Rauschen verwirbelt. Sonne und Mond fallen nur dort herein, wo der Strahl zu ihnen durch eine Öffnung ins Freie geht; so stehen Lichtstrahlen von Tür und Rauchloch im Rauch. Dazu kommen die Lichter im Raum (Herd, Lampen) und ein gleichmäßiges Licht aus Himmel und Rückstrahlung. Den Raum bekommt es als Zahlenfeld (Lage, Maße, Öffnungen); das Package kennt keinen Holzbau.

## Seit Haithabu Phase 11 auch

- Die Bälle rechnen ihr Licht einmal je Ball im Vertex-Shader; das Rauschen kommt aus einer Textur. Der Befund von Phase 10: Feuer und Rauch kosteten auf der Radeon 4 bis 7 ms.
- Die Deckkraft eines Balls ist min(0,8; 2,6 / (1 + w σ<sub>waag</sub> σ<sub>senk</sub>)).
  - Bis Phase 10 war w = 1; auf dem Statusbild von Phase 10 sah man die flachen Abendfahnen nicht. Phase 11 setzte w = 0,25.
  - Seit Phase 13 hängt w von der Stabilität ab (`Plume.widthFactor`): am Abend 0,25, am Tag in durchmischter Luft 0,9. Auf den Mittagsbildern von Phase 12 standen die Fahnen sonst wie Watte über den Dächern.

## Grenzen

- Keine Strömungsrechnung: Häuser und Gelände lenken den Rauch nicht ab, Fahnen durchdringen einander.
- Der Wind ist überall gleich (eine Richtung, ein Profil), keine Böen in der Fahne.
- Die Bälle werden nicht sortiert. Bei dichtem schwarzem Rauch vor hellem kann die Reihenfolge stellenweise falsch sein; die fernen Fahnen werden deshalb zuerst gezeichnet.
- Im Raum ist die Schicht waagerecht und überall gleich dicht; Zug durch die Türen, Wind am Rauchloch und Rauch aus einem zweiten Feuer rechnet das Modell nicht.
- Die Lichtstrahlen sehen nur die Hülle; Einrichtung und Nachbarhäuser werfen im Rauch keinen Schatten.
