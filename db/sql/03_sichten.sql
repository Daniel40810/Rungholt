-- =====================================================================================================
-- Rungholt · Datenbank · 03_sichten.sql
-- Neun Sichten: die Warften mit Fläche und Fundstellen in 100 m, Rungholt als eine Ebene (Warften, Bauwerke,
-- Fundstellen), die Tageskarte als eine Ebene für QGIS (Abnahme Phase 16), Spring und Nipp je Tag, Niedrigwasser
-- bei Tag für Wattwanderungen, die Tiden je Tag aus der Chronik, die Läufe der Mandränke, die Befunde mit ihren
-- Quellen und die letzte Sitzung für „Weiter wie zuletzt“. Längen und Flächen auf dem Ellipsoid, Toleranz 5 cm.
-- Szenenzeit (F33): SZENE_TEXT und SZENE_TAG wie gespeichert; SZENENZEIT als TIMESTAMP in deutscher Zeit nur ab
-- dem 15.10.1582 (davor rechnet Oracle julianisch). CREATE OR REPLACE: beliebig oft wiederholbar.
-- =====================================================================================================

-- Die Warften mit Mitte als Zahlen, Fläche der Krone auf dem Ellipsoid und den Fundstellen in 100 m um die Krone.
CREATE OR REPLACE VIEW RH_WARFT_V AS
SELECT w.ID,
       w.NR,
       w.NAME,
       w.ART,
       w.MITTE.SDO_POINT.X AS LAENGE,
       w.MITTE.SDO_POINT.Y AS BREITE,
       w.X_M,
       w.Z_M,
       w.KRONE_NHN_M,
       ROUND(MDSYS.SDO_GEOM.SDO_AREA(w.FLAECHE, 0.05, 'unit=SQ_M')) AS KRONE_M2,
       w.HAEUSER,
       w.BRUNNEN,
       (SELECT COUNT(*) FROM RH_FUNDSTELLE f
         WHERE MDSYS.SDO_WITHIN_DISTANCE(f.LAGE, w.FLAECHE, 'distance=100 unit=M') = 'TRUE') AS FUNDSTELLEN,
       (SELECT LISTAGG(f.NAME, '; ') WITHIN GROUP (ORDER BY f.JAHR, f.NAME) FROM RH_FUNDSTELLE f
         WHERE MDSYS.SDO_WITHIN_DISTANCE(f.LAGE, w.FLAECHE, 'distance=100 unit=M') = 'TRUE') AS FUNDE,
       w.BEFUND,
       q.KURZ AS QUELLE,
       w.HERKUNFT,
       w.FLAECHE
FROM RH_WARFT w
LEFT JOIN RH_QUELLE q ON q.ID = w.QUELLE_ID;

-- Rungholt als eine Ebene für QGIS: Warften (Krone), Bauwerke und Fundstellen. OBJEKT_ID = Ebene × 10^9 + ID.
CREATE OR REPLACE VIEW RH_RUNGHOLT_V AS
SELECT 1000000000 + w.ID AS OBJEKT_ID, 'WARFT' AS EBENE, w.ART, w.NAME, w.BEFUND, w.KRONE_NHN_M AS WERT, w.FLAECHE AS GEOM
FROM RH_WARFT w
UNION ALL
SELECT 2000000000 + b.ID, 'BAUWERK', b.ART, b.NAME, b.BEFUND, b.HOEHE_NHN_M, b.GEOM
FROM RH_BAUWERK b
UNION ALL
SELECT 3000000000 + f.ID, 'FUNDSTELLE', CASE WHEN f.HERKUNFT = 'EIGEN' THEN 'EIGEN' ELSE TO_CHAR(f.JAHR) END, f.NAME, f.BEFUND, f.JAHR, f.LAGE
FROM RH_FUNDSTELLE f;

-- Die Tageskarte: Rungholt (ohne Tag, immer da), die Pegelorte, die Wasserlinie jedes Niedrigwassers, die Zählungen
-- der Seehunde und Vögel, Bruchstelle und Bresche der Mandränke. QGIS: Ebene RH_TAGESKARTE_V, Schlüssel OBJEKT_ID,
-- Filter  "TAG" = '2026-07-21' OR "TAG" IS NULL , Stil nach EBENE und ART.
CREATE OR REPLACE VIEW RH_TAGESKARTE_V AS
SELECT r.OBJEKT_ID, r.EBENE, CAST(NULL AS VARCHAR2(10)) AS TAG, r.ART, r.NAME AS TEXT, r.WERT, r.GEOM
FROM RH_RUNGHOLT_V r
UNION ALL
SELECT 4000000000 + p.ID, 'PEGEL', NULL, p.KURZ, p.NAME, p.MITTEL_M, p.LAGE
FROM RH_PEGELORT p WHERE p.LAGE IS NOT NULL
UNION ALL
SELECT 5000000000 + t.ID, 'WASSERLINIE', t.SZENE_TAG, t.ART, t.SZENE_TEXT, t.PEGEL_M, t.WASSERLINIE
FROM RH_TIDE t WHERE t.WASSERLINIE IS NOT NULL
UNION ALL
SELECT 6000000000 + z.ID, 'ZAEHLUNG', z.SZENE_TAG, z.ART, z.GRUPPE || ' ' || z.ANZAHL, z.ANZAHL, z.LAGE
FROM RH_ZAEHLUNG z WHERE z.LAGE IS NOT NULL
UNION ALL
SELECT 7000000000 + m.ID, 'BRUCHSTELLE', m.SZENE_TAG, m.STELLE, NVL(m.BRUCH_TEXT, 'Mandränke'), m.MARSCH_MAX_M, m.BRUCHSTELLE
FROM RH_MANDRAENKE_LAUF m WHERE m.BRUCHSTELLE IS NOT NULL
UNION ALL
SELECT 8000000000 + m.ID, 'BRESCHE', m.SZENE_TAG, m.STELLE, NVL(m.BRUCH_TEXT, 'Mandränke'), m.STUECKE, m.BRESCHE
FROM RH_MANDRAENKE_LAUF m WHERE m.BRESCHE IS NOT NULL;

-- Spring und Nipp je Tag und Pegelort aus dem Tidenkalender (über 1,1 Spring, unter 0,9 Nipp).
CREATE OR REPLACE VIEW RH_SPRINGNIPP_V AS
SELECT p.KURZ AS PEGEL,
       k.SZENE_TAG AS TAG,
       ROUND(MAX(k.SPRING_NIPP), 3) AS FAKTOR,
       CASE WHEN MAX(k.SPRING_NIPP) > 1.1 THEN 'SPRING' WHEN MAX(k.SPRING_NIPP) < 0.9 THEN 'NIPP' END AS TIDE,
       MAX(CASE WHEN k.ART = 'HW' THEN k.HOEHE_M END) AS HW_MAX_M,
       MIN(CASE WHEN k.ART = 'NW' THEN k.HOEHE_M END) AS NW_MIN_M,
       ROUND(MAX(CASE WHEN k.ART = 'HW' THEN k.HOEHE_M END) - MIN(CASE WHEN k.ART = 'NW' THEN k.HOEHE_M END), 3) AS HUB_M
FROM RH_TIDENKALENDER k
JOIN RH_PEGELORT p ON p.ID = k.PEGELORT_ID
GROUP BY p.KURZ, k.SZENE_TAG;

-- Niedrigwasser bei Tag am Rand der Szene (8 bis 17 Uhr): wann eine Wattwanderung nach Südfall geht.
CREATE OR REPLACE VIEW RH_NW_TAG_V AS
SELECT k.SZENE_TAG AS TAG,
       k.SZENE_TEXT,
       k.STUNDE,
       k.HOEHE_M,
       k.SPRING_NIPP,
       CASE WHEN k.SPRING_NIPP > 1.1 THEN 'SPRING' WHEN k.SPRING_NIPP < 0.9 THEN 'NIPP' END AS TIDE
FROM RH_TIDENKALENDER k
JOIN RH_PEGELORT p ON p.ID = k.PEGELORT_ID
WHERE k.ART = 'NW' AND p.SZENE = 1 AND k.STUNDE BETWEEN 8 AND 17;

-- Die Tiden je Szenentag aus der Chronik: wie viele, höchstes Hoch- und tiefstes Niedrigwasser, trockener Anteil, Windstau.
CREATE OR REPLACE VIEW RH_TIDE_TAG_V AS
SELECT t.SZENE_TAG AS TAG,
       t.AERA,
       COUNT(*) AS TIDEN,
       MAX(CASE WHEN t.ART = 'HW' THEN t.PEGEL_M END) AS HW_MAX_M,
       MIN(CASE WHEN t.ART = 'NW' THEN t.PEGEL_M END) AS NW_MIN_M,
       ROUND(MAX(CASE WHEN t.ART = 'NW' THEN t.TROCKEN_ANTEIL END), 4) AS TROCKEN_MAX,
       ROUND(MAX(ABS(t.WINDSTAU_M)), 3) AS WINDSTAU_MAX_M,
       (SELECT SUM(z.ANZAHL) FROM RH_ZAEHLUNG z WHERE z.SZENE_TAG = t.SZENE_TAG AND z.ART = 'SEEHUND') AS SEEHUNDE,
       (SELECT SUM(z.ANZAHL) FROM RH_ZAEHLUNG z WHERE z.SZENE_TAG = t.SZENE_TAG AND z.ART = 'VOGEL') AS VOEGEL
FROM RH_TIDE t
GROUP BY t.SZENE_TAG, t.AERA;

-- Die Läufe der Mandränke mit den Dauern in Minuten und der Breite der Bresche auf dem Ellipsoid.
CREATE OR REPLACE VIEW RH_MANDRAENKE_V AS
SELECT m.ID,
       m.STELLE,
       m.SZENE_TAG AS TAG,
       m.BRUCH_TEXT,
       m.STUFE,
       ROUND((m.UEBERLAUF_MS - m.BEGINN_MS) / 60000) AS BIS_UEBERLAUF_MIN,
       ROUND((m.BRUCH_MS - m.UEBERLAUF_MS) / 60000, 1) AS UEBERLAUF_MIN,
       ROUND((m.FLUT_MS - m.BRUCH_MS) / 1000) AS BRUCH_S,
       m.PEGEL_MAX_M,
       m.MARSCH_MAX_M,
       m.HAEUSER,
       m.HAEUSER_ZERFALLEN,
       m.STUECKE,
       CASE WHEN m.BRESCHE IS NOT NULL THEN ROUND(MDSYS.SDO_GEOM.SDO_LENGTH(m.BRESCHE, 0.05, 'unit=M'), 1) END AS BRESCHE_M,
       m.BRUCHSTELLE.SDO_POINT.X AS LAENGE,
       m.BRUCHSTELLE.SDO_POINT.Y AS BREITE,
       r.NAME AS REGIEBUCH,
       m.SITZUNG_ID
FROM RH_MANDRAENKE_LAUF m
LEFT JOIN RH_REGIEBUCH r ON r.ID = m.REGIEBUCH_ID;

-- Befunde mit ihren Quellen (Kurzzeichen) in einer Zeile.
CREATE OR REPLACE VIEW RH_BEFUND_V AS
SELECT b.ID,
       b.THEMA,
       b.GEGENSTAND,
       b.BEFUND,
       b.TEXT,
       (SELECT LISTAGG(q.KURZ, ', ') WITHIN GROUP (ORDER BY q.KURZ)
          FROM RH_BEFUND_QUELLE bq JOIN RH_QUELLE q ON q.ID = bq.QUELLE_ID
         WHERE bq.BEFUND_ID = b.ID) AS QUELLEN
FROM RH_BEFUND b;

-- Die jüngste Sitzung mit gesichertem Zustand, ob sauber beendet oder abgebrochen.
CREATE OR REPLACE VIEW RH_LETZTE_SITZUNG_V AS
SELECT *
FROM (SELECT s.ID, s.BEGINN, s.ENDE, s.SAUBER_BEENDET, s.APP_VERSION, s.REGIEBUCH_ID, s.REGIE_SEKUNDE, s.SZENE_MS, s.SZENE_TEXT,
             CASE WHEN s.SZENE_MS >= -12219292800000
                  THEN CAST(FROM_TZ(TIMESTAMP '1970-01-01 00:00:00' + NUMTODSINTERVAL(s.SZENE_MS / 1000, 'SECOND'), 'UTC') AT TIME ZONE 'Europe/Berlin' AS TIMESTAMP)
             END AS SZENENZEIT,
             s.AERA, s.ZEITRAFFER, s.PEGEL_M, s.WINDSTAU_M, s.MANDRAENKE_STUFE, s.ZUSTAND_ZEIT
      FROM RH_SITZUNG s
      WHERE s.ZUSTAND IS NOT NULL
      ORDER BY s.ZUSTAND_ZEIT DESC NULLS LAST, s.ID DESC)
WHERE ROWNUM = 1;

-- Ende 03_sichten.sql
