# Rungholt – Quellen und Befunde

Erzeugt von `tools/Quellenliste` aus `src/com/dan/rungholt/data/quellen.txt`, `befunde.txt` und `sturmfluten.txt`. Dieselben Zeilen stehen in den Tabellen `RH_QUELLE`, `RH_BEFUND`, `RH_BEFUND_QUELLE` und `RH_STURMFLUT`. Jeder Befund ist **belegt** (eine Quelle nennt es), **gemessen** (aus Daten wie den Höhen der BAW abgeleitet) oder **gestaltet** [G] (so gebaut, dass es glaubhaft ist, ohne Beleg).

23 Quellen · 29 Befunde (15 belegt, 2 gemessen, 12 gestaltet) · 13 belegte Sturmfluten

Datumsangaben vor 1582 zählen die Quellen julianisch. Die Uhr der App zählt proleptisch gregorianisch: Die Nacht der Grote Mandränke, der 15. auf den 16. Januar 1362 der Chroniken, ist bei ihr die vom 23. auf den 24. Januar; dort steht die Springtide.

## Quellen

| Kurz | Art | Titel | Abgerufen | Wofür |
|---|---|---|---|---|
| JGU23 | Netz | [Johannes Gutenberg-Universität Mainz, Pressemitteilung 24.5.2023: Lost since 1362 – Researchers discover the church of a sunken medieval trading place](https://press.uni-mainz.de/lost-since-1362-researchers-discover-the-church-of-a-sunken-medieval-trading-place) |  | Kirche 40 × 15 m, Warftenkette 2 km, 54 Warften, Seedeich mit Sielhafen, Methoden, Beteiligte |
| IDW23 | Netz | [Informationsdienst Wissenschaft, 24.5.2023: dieselbe Mitteilung über die CAU Kiel](https://idw-online.de/de/news814879) |  | Magnetik, Elektromagnetik, Seismik und Bohrungen auf über 10 km² |
| NPGEZ | Netz | [Nationalpark Wattenmeer, Faltblatt „Gezeiten“](https://www.nationalpark-wattenmeer.de/wp-content/uploads/2021/10/FB_Gezeiten-web.pdf) |  | Rhythmus 12 h 25 min, 50 min je Tag, Spring und Nipp, Strömung auf dem Watt und im Priel |
| SHSAGE | Netz | [Land Schleswig-Holstein, Sagen und Legenden aus dem Norden: Rungholt](https://www.schleswig-holstein.de/DE/landesportal/land-und-leute/typisch-sh/sagen-und-legenden-aus-dem-norden/_documents/rungholt) |  | Die Sage, die Glocken aus dem Watt |
| EASYGSH | Artikel | [Sievers et al. (2021): Integrated marine data collection for the German Bight – Part 1. Earth System Science Data 13](https://essd.copernicus.org/articles/13/4053/2021/) |  | EasyGSH-DB, Bathymetrie 10 m |
| BAW | Daten | Bundesanstalt für Wasserbau: Küstenhöhen topography_max_z 2015–2021 und Wattflächen 2021 (10 m, ETRS89/UTM 32, NHN) |  | In templates/rasters_COP30/EasyGeo; daraus daten/gelaende (8 m) |
| VORLAGEN | Daten | Vorlagen im Projekt: Mejer-Karte 1636, Karte Nordstrands vor 1634, Funde im Rungholt-Museum Pellworm, Ausgrabung bei Ebbe, Grundriss und Rekonstruktion der Kirche |  | templates/ |
| GZK26 | Daten | [Gezeitenkalender 2026 für Husum (Hoch- und Niedrigwasserzeiten nach BSH, Hrsg. Husum Tourismus)](https://www.husum-tourismus.de/Service/Kataloge/Download-Center/Gezeitenkalender-2026) |  | 475 Zeiten aus Januar, April, Juli, Oktober: Grundlage der zehn Partialtiden |
| SHEIDER | Netz | [Land Schleswig-Holstein, Küstenschutz: Wasserstände Eider](https://www.schleswig-holstein.de/DE/fachinhalte/K/kuestenschutz_fachplaene/9_Hydrologie/3_Eider/3_1_Wasserstaende) |  | Mittlerer Tidenhub der Husumer Bucht rund 3,50 m |
| TIDEFC | Netz | [tide-forecast.com, Vorhersagen Husum und Pellworm](https://www.tide-forecast.com/tide/Husum-Germany/tide-times) |  | Spannweite Spring und Nipp, Pellworm gegen Husum (nicht amtlich, nur zum Vergleich) |
| APP | Messung | Rungholt, gerechnet in der App (Werkbuch Rungholt) |  | Land von 1362, Warften, Häuser, Brunnen, Fundstellen und Tidenkalender aus scene.Land1362, scene.Spuren, scene.Stadt1362 und com.dan.tide [G] |
| WPMARC | Netz | [Zweite Marcellusflut (Wikipedia)](https://de.wikipedia.org/wiki/Zweite_Marcellusflut) | 20.10.2026 | 15. bis 17. Januar 1362, Rungholt und die Kirchspiele der Edomsharde |
| GSHSF | Netz | [Geschichte Schleswig-Holsteins von A bis Z: Sturmflut](https://geschichte-s-h.de/sh-von-a-bis-z/s/sturmflut/) | 20.10.2026 | Allerheiligenfluten 1436 und 1532, Nordfrieslandflut 1981, Capella 1976 |
| WPBURCH | Netz | [Burchardiflut (Wikipedia)](https://de.wikipedia.org/wiki/Burchardiflut) | 20.10.2026 | 11./12. Oktober 1634, Alt-Nordstrand zerrissen; Husum 1976 4,11 m über MThw |
| WP1717 | Netz | [Weihnachtsflut 1717 (Wikipedia)](https://de.wikipedia.org/wiki/Weihnachtsflut_1717) | 20.10.2026 | Etwa 9000 Tote zwischen Tondern und Emden |
| WP1825 | Netz | [Februarflut 1825 (Wikipedia)](https://de.wikipedia.org/wiki/Februarflut_1825) | 20.10.2026 | Halligflut: Südfall mit allen Bewohnern untergegangen |
| SH1962 | Netz | [Land Schleswig-Holstein, Augenblicke der Landesgeschichte: Sturmflut 1962](https://www.schleswig-holstein.de/DE/Fachinhalte/L/landeskundegeschichte/Chronologie_Augenblicke_Landesgeschichte/1962_Sturmflut.html) | 20.10.2026 | Deichbrüche an der Westküste, keine Toten |
| WPCAPELLA | Netz | [Capella-Orkan (Wikipedia)](https://de.wikipedia.org/wiki/Capella-Orkan) | 20.10.2026 | 3. Januar 1976: Husum 5,66 m über NN, bis heute der höchste Stand |
| WPJAN76 | Netz | [Zweite Januarflut 1976 (Wikipedia)](https://de.wikipedia.org/wiki/Zweite_Januarflut_1976) | 20.10.2026 | Husum 78 cm unter dem 3. Januar und 33 cm unter 1962: daraus 1962 5,21 m und 21.1.1976 4,88 m über NN |
| WPANATOL | Netz | [Orkan Anatol (Wikipedia)](https://de.wikipedia.org/wiki/Orkan_Anatol) | 20.10.2026 | 3. Dezember 1999 |
| WPXAVER | Netz | [Orkan Xaver (Wikipedia)](https://de.wikipedia.org/wiki/Orkan_Xaver) | 20.10.2026 | 5./6. Dezember 2013, Deichbruch in Keitum |
| BSH13 | Netz | [BSH, Sturmflut 6. Dezember 2013 (Ereignisbericht)](https://www.bsh.de/DE/THEMEN/Wasserstand_und_Gezeiten/Sturmfluten/_Anlagen/Downloads/Nordsee_Sturmflut_20131206.pdf) | 20.10.2026 | Tabellen im PDF nicht lesbar gewesen |
| MWCHRON | Netz | [Peter von Holdt, Sturmfluten Deutsche Bucht (private Chronik, mein-wilster.de)](https://mein-wilster.de/Chroniken/2/Eiderstedt,Jadebusen,Flandern,Weser,Holland,Grossbritannien/Sturmfluten+Deutsche+Bucht/13) | 20.10.2026 | Scheitel Husum 1981, 1999, 2007, 2013 ohne Quellenangabe, amtlich nicht bestätigt: darum nicht als Scheitel übernommen |

## Befunde nach Thema

### Gelände

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Lage | belegt | Nordfriesisches Wattenmeer zwischen Pellworm und Nordstrand, östlich der Hallig Südfall; Nationalpark seit 1985, Weltnaturerbe seit 2009. | JGU23 |
| Höhen heute | gemessen | Küstenhöhen und Wattflächen der BAW (10 m, NHN), auf 8 m gebracht: 16,4 × 16,4 km um die Kirche. | BAW, EASYGSH |
| Feinform unter 10 m | gestaltet [G] | Rippel, kleine Rinnen und Wattwurmfelder prozedural aus dem Grund. | APP |
| Gelände 1362 | gestaltet [G] | Marsch NHN +0,5 bis +1 m, Seedeich mit Krone NHN +3,5 m, Warften, Sielzüge; Verlauf von Deich und Kette nach dem Muster der Messbilder. | APP, JGU23 |

### Tide

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Rhythmus | belegt | Zwei Hochwasser in 24 h 50 min, die Zeiten rücken jeden Tag um rund 50 min; Spring und Nipp im Wechsel von gut 7 Tagen. | NPGEZ |
| Hub in Husum | belegt | Mittleres Hochwasser rund NHN +1,6 m, mittleres Niedrigwasser rund NHN −1,9 m, Tidenhub rund 3,50 m. | SHEIDER |
| Partialtiden Husum | gemessen | Zehn Partialtiden (M2, S2, N2, K2, K1, O1, M4, MS4, MN4, M6), angepasst an 475 Hoch- und Niedrigwasserzeiten des Gezeitenkalenders 2026. | GZK26 |
| Pellworm und Norderhever | gestaltet [G] | Husum um 8 bzw. 27 min früher, der Hub 3,3 statt 3,5 m; nicht amtlich, nur mit Vorhersagen verglichen. | TIDEFC |
| Windstau | gestaltet [G] | Westwind drückt Wasser in die Bucht: bei Sturm 1–2 m über dem Rechenwert, bei Orkan 3 m und mehr. | APP |

### Watt

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Boden | belegt | Sandwatt auf den Höhen, Mischwatt dazwischen, Schlickwatt in den Buchten und an den Deichen; Rippel, Wattwurmhaufen, Muschelschill, Pfützen. | NPGEZ |
| Überflutung | gestaltet [G] | Die Flut kommt durch die Priele und schließt die Flächen von den Rinnen her; gerechnet auf 16-m-Zellen mit Laufzeit über das Watt. | APP |

### Leben

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Seehunde | belegt | Seehunde liegen bei Ebbe auf den Sandbänken, Heuler im Juni und Juli. | NPGEZ |
| Wattvögel | belegt | Austernfischer, Alpenstrandläufer in Schwärmen, Säbelschnäbler, Brandgans, Möwen, Ringelgänse im Winter. | NPGEZ |
| Wattwanderung | gestaltet [G] | Von Nordstrand (Fuhlehörn) nach Südfall, rund 6 km bei Ebbe, mit Führer. | APP |

### Himmel

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Wetter | gestaltet [G] | Wetterlagen der Deutschen Bucht mit Orkan aus Westnordwest; Sonne, Mond und Sterne aus der Uhr der Szene. | APP |

### Rungholt 1362

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Kirche | belegt | Mai 2023: die Hauptkirche, 40 × 15 m, auf einer Warft nahe Südfall. | JGU23, IDW23 |
| Warften | belegt | 54 Warften und eine Warftenkette von 2 km; Lage und Größe der einzelnen Warften gestaltet. | JGU23 |
| Seedeich und Sielhafen | belegt | Ein Seedeich mit Sielhafen; Verlauf gestaltet. | JGU23 |
| Zwei kleine Kirchen | belegt | Die Magnetik 2023 zeigt zwei kleine Kirchen; ihre Lage in der Szene ist gestaltet. | JGU23 |
| Häuser | gestaltet [G] | Fachwerk mit Reet wie in Haithabu, auf jeder Warft ein bis vier Häuser. | APP |
| Funde | belegt | Andreas Busch dokumentierte ab 1921 Warften, Siele, Brunnen und Gräben; Scherben, Krüge, grüne Ofenkacheln, ein Pferdeschädel in den Museen Pellworm und Nordstrand. | VORLAGEN |

### Die Grote Mandränke

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Datum | belegt | Grote Mandränke, Zweite Marcellusflut, 15. bis 17. Januar 1362 (julianisch); in der Uhr der Szene (gregorianisch) die Nacht vom 23. auf den 24. Januar, dort mit Springtide. | WPMARC |
| Ablauf in einer Nacht | gestaltet [G] | Orkan aus Westnordwest, Windstau auf einer Springtide, Überlauf, Bruch des Seedeichs, Flut durch die Stadt; die Bruchstelle wählbar. | APP |
| Sturmfluten danach | belegt | Die Sturmfluten an dieser Küste seit 1362 mit Scheitel in Husum, soweit gemessen (RH_STURMFLUT). | WPMARC, WPCAPELLA, WPJAN76 |

### Spuren

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Magnetik und Bohrungen 2023 | belegt | Magnetik, Elektromagnetik, Seismik und Bohrungen auf über 10 km²; die Reste sind stark erodiert, oft nur als Abdruck. | IDW23, JGU23 |
| Bild der Spuren | gestaltet [G] | Flache Kleirücken, dunklere Böden, Sodenbrunnen; Magnetik nach dem Muster der Messbilder, nicht die Daten. | APP |

### Klang

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Glocken aus dem Watt | belegt | In Sturmnächten hört man die Glocken von Rungholt aus dem Watt (Sage). | SHSAGE |
| Klang der Szene | gestaltet [G] | Wind, Brandung, Priele, Vögel, Seehunde und 1362 die Stadt, in Echtzeit erzeugt. | APP |

### Regie

| Gegenstand | Befund | Text | Quellen |
|---|---|---|---|
| Regiebücher | gestaltet [G] | Eine Tide, Die Grote Mandränke, Die Glocken: Text mit Stichworten, im Kino zu zeigen. | APP |

## Die belegten Sturmfluten (1362–2013)

Scheitel am Pegel Husum nur, wo eine Quelle ihn nennt; Werte aus einer Chronik ohne Beleg stehen im Text.

| Jahr | Name | Datum | Kalender | Scheitel Husum | Folgen | Quelle |
|---|---|---|---|---|---|---|
| 1362 | Grote Mandränke (Zweite Marcellusflut) | 15.–17.1.1362, Scheitel in der Nacht zum 16. | julianisch |  | Rungholt und weitere Kirchspiele der Edomsharde gingen unter; rund 100 000 ha Land verloren, die ersten Halligen entstanden. | WPMARC |
| 1436 | Allerheiligenflut 1436 | 1.11.1436 | julianisch |  | Eiderstedt und Strand überflutet, nach der Quelle ohne dauerhaften Landverlust. | GSHSF |
| 1532 | Dritte Allerheiligenflut | 31.10./1.11.1532 | julianisch |  | Mehrere tausend Tote in Nordfriesland; Hochwassermarke in der Kirche von Klixbüll. | GSHSF |
| 1634 | Burchardiflut (Zweite Grote Mandränke) | Nacht 11./12.10.1634 (gregorianisch etwa 21./22.10.) | julianisch |  | Alt-Nordstrand zerriss in Pellworm, Nordstrand, Nordstrandischmoor und Südfall; auf Strand mindestens 6123 Tote, belegt rund 8000. Zeitgenössisch etwa 4 m über MThw am Festland, kein Pegelwert. | WPBURCH |
| 1717 | Weihnachtsflut 1717 | Nacht 24./25.12.1717 | gregorianisch |  | Etwa 9000 Tote zwischen Tondern und Emden; nur in der Bökingharde hielten die meisten Deiche. | WP1717 |
| 1825 | Februarflut 1825 (Halligflut) | 3.–5.2.1825, Scheitel Nacht 3./4.2. | gregorianisch |  | Auf den Halligen 79 von 339 Häusern zerstört, 233 unbewohnbar; Pellworm ganz überflutet; Südfall mit allen 12 Bewohnern untergegangen. | WP1825 |
| 1962 | Februarflut 1962 | Nacht 16./17.2.1962 | gregorianisch | 5,21 m NN (hist.) | Festlandsdeiche auf 150 km schwer beschädigt, Brüche im Dockkoog bei Husum und im Uelvesbüller Koog; Halligen verwüstet; an der Westküste keine Toten. Scheitel aus Wikipedia „Zweite Januarflut 1976“ abgeleitet (566 − 78 + 33). | WPJAN76 |
| 1976 | Erste Januarflut 1976 (Capella) | 3.1.1976 | gregorianisch | 5,66 m NN (hist.) | Bis heute der höchste Stand in Husum (4,11 m über MThw); Schäden am Hindenburgdamm und an den Deichen von Nordstrand und Westerhever; Wasser in den Häusern der Halligen; keine Toten. Die Chronik nennt 5,61 m. | WPCAPELLA |
| 1976 | Zweite Januarflut 1976 | Nacht 20./21.1.1976 | gregorianisch | 4,88 m NN (hist.) | Wieder Land unter auf den Halligen, neue große Schäden auf Amrum. Scheitel abgeleitet (78 cm unter dem 3.1.); die Chronik nennt 4,96 m. | WPJAN76 |
| 1981 | Nordfrieslandflut 1981 | 24.11.1981 | gregorianisch |  | Die höchsten je gemessenen Stände im nördlichen Nordfriesland, größere Schäden blieben aus. Husum 5,15 m über NN nur in einer privaten Chronik. | GSHSF |
| 1999 | Orkan Anatol | 3.12.1999 | gregorianisch |  | Schwere Orkanflut; auf Sylt fiel der Strom aus, auf Föhr Hunderte Bäume umgestürzt, in Dänemark Rømø überflutet. Husum 5,37 m über NN nur in einer privaten Chronik. | WPANATOL |
| 2007 | Sturmtief Tilo | 9.11.2007 | gregorianisch |  | Sehr schwere Sturmflut ohne größere Schäden in Nordfriesland. Husum 4,12 m über NN nur in einer privaten Chronik. | MWCHRON |
| 2013 | Orkan Xaver | 5./6.12.2013, drei Fluten in Folge | gregorianisch |  | Deichbruch in Keitum (Sylt), 23 km Dünenschäden auf Sylt, Landverluste auf Sylt, Föhr und Amrum, die meisten Halligen überspült. Husum 4,70 m (5.12.) und 4,12 m (6.12.) über NN nur in einer privaten Chronik. | WPXAVER |
