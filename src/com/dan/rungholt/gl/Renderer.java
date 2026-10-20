package com.dan.rungholt.gl;

import com.dan.cloud.Clouds;
import com.dan.cloud.Noise;
import com.dan.cloud.gl.CloudGl;
import com.dan.fog.Fog;
import com.dan.fog.gl.FogGl;
import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.lightning.gl.LightningGl;
import com.dan.rain.Rain;
import com.dan.rain.gl.RainGl;
import com.dan.rungholt.camera.Camera;
import com.dan.sky.Sky;
import com.dan.sky.gl.SkyGl;
import com.dan.tide.Flood;
import com.dan.mudflat.Mudflat;
import com.dan.mudflat.WetField;
import com.dan.mudflat.gl.MudflatGl;
import com.dan.tide.Tide;
import com.dan.rungholt.scene.Buehne;
import com.dan.rungholt.scene.Terrain;
import com.dan.ground.Biome;
import com.dan.ground.Meadow;
import com.dan.ground.gl.GroundGl;
import com.dan.ocean.Ocean;
import com.dan.ocean.gl.OceanGl;
import com.dan.reed.Reed;
import com.dan.reed.gl.ReedGl;
import com.dan.rungholt.scene.Physik;
import com.dan.rungholt.scene.Seehundbank;
import com.dan.rungholt.scene.Wattwanderer;
import com.dan.seal.Colony;
import com.dan.seal.Seal;
import com.dan.seal.gl.SealGl;
import com.dan.flock.Waders;
import com.dan.flock.gl.WaderGl;
import com.dan.figure.Hike;
import com.dan.figure.Outfit;
import com.dan.figure.Person;
import com.dan.figure.gl.FigureGl;
import com.dan.rungholt.scene.Wattwanderung;
import com.dan.rungholt.scene.SceneClock;
import com.dan.rungholt.scene.Site;
import com.dan.rungholt.scene.Sun;
import com.dan.rungholt.wiring.Wiring;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Die Engine auf der Grafikkarte, übernommen aus Glacier und entschlackt (F4 A). Je Bild:
 * <ol>
 * <li>Himmel aus {@code com.dan.sky} (Streuung, Sonne über Süden, Mond, Sterne; Phase 3) in einen HDR-Puffer (RGBA16F,
 * umgekehrte 32F-Tiefe), vorbelichtet nach der Augenanpassung; ohne das Package der Verlauf aus Phase 1.</li>
 * <li>Wolken ({@code com.dan.cloud}) nach der Wetterregie, Kanäle der Blitze ({@code com.dan.lightning}), dann Himmel
 * und Wolken als Rundumbild für die Spiegelungen und das Himmelslicht; die Wolken werfen Schatten auf alles.</li>
 * <li>Das Gelände heute ({@link Terrain}, Phase 2): CDLOD aus Glacier über dem Raster aus den BAW-Küstenhöhen, Watt,
 * Vorland, Deiche und Köge nach der Art je Zelle ({@code terrain.frag}); ohne {@code daten/gelaende} die Bühne von
 * Phase 1 als Raster.</li>
 * <li>Die Bodendecke um das Auge ({@code com.dan.ground}) auf Deich, Koog und Vorland, nur zu Fuß.</li>
 * <li>Das Meer ({@code com.dan.ocean}, Preset „Watt“) auf dem Pegel, das Gelände als Grund (Uferdämpfung, Trübung,
 * Schaumsaum), die Köge ausgeblendet, über den Rand des Rasters hinaus die offene See.</li>
 * <li>Gras auf Deich, Koog und Vorland, Binsen in der Salzwiese ({@code com.dan.reed}).</li>
 * <li>Regen ({@code com.dan.rain}), Seenebel ({@code com.dan.fog}) bis zur Tiefe der Szene, das Leuchten der Luft um
 * die Blitze.</li>
 * <li>Nachbearbeitung: Filmkurve, Stäbchensehen in der Nacht, Gamma; dann das HUD.</li>
 * <li>Phase 4: die Tide ({@code com.dan.tide}) im eigenen Faden: Überflutungskarte aus dem Gelände (Verbindungshöhe und
 * Laufzeit je Zelle, einmal gebaut, danach aus {@code daten/cache}), je Schritt das Wasser je Zelle mit Pfützen und
 * Strömung; das Meer bekommt den Spiegel als Textur (lange Welle über dem Pegel am Rand), das Gelände seine nasse Kante
 * daraus.</li>
 * <li>Phase 7: die Seehunde ({@code com.dan.seal}) an der {@link Seehundbank} nach Tide, Tageszeit und Störung, mit
 * ihren Schatten, nach dem Gelände und vor dem Wasser (Durchgang „Tiere“); ein {@link Wattwanderer} als Störer mit
 * Stiefelabdrücken; die Spuren von Robben und Stiefeln im Watt ({@code mudflat.TrackMap}).</li>
 * <li>Phase 8: die Wattvögel ({@code com.dan.flock.Waders}) im selben Durchgang: fressen bei Ebbe am Saum, fliegen vor
 * der Flut zu den Rastplätzen auf Südfall und am Deich von Nordstrand, der Alpenstrandläufer wendet als Schwarm.</li>
 * </ol>
 * Geblieben aus Glacier und Haithabu: Qualitätsstufen mit Auflösungsregelung, Zeitmessung jedes Durchgangs auf der
 * Grafikkarte (Zeitstempel, ein Bild versetzt gelesen), Compute-Test beim Start, HUD kurz/voll/aus, Foto (F12), Shader
 * neu laden (F5), Standbilder ohne Fenster ({@code tools.Tempo}), der Faden der Physik ({@link Physik}) mit festem
 * Schritt. Im Bildlauf werden keine Dateien gelesen.
 * <p>
 * Augenanpassung wie in Glacier (Phase 3): alle 0,2 s die Leuchtdichte im Blickfeld, mit der des ganzen Himmels gemittelt,
 * über eine Sekunde geglättet, nur zu 85 % angepasst (die Nacht bleibt Nacht), unter Wolken dunkler, im Blitz gibt sie
 * kurz nach. Noch abgestellt (kommt mit der jeweiligen Phase): Sonnenschatten, Bildfolgen, Regiebücher, Kino,
 * Dauerlauf, Klang.
 */
public final class Renderer implements GLEventListener {
    public static final int MIN_MAJOR = 4, MIN_MINOR = 3;
    public static final String VERSION = "Phase 17";
    /** Die Phase, die dieser Stand erfüllt (für Ansichten und HUD). */
    public static final int PHASE = 17;
    public static final String[] PASSES = {"Himmel", "Wolken", "Gelände", "Boden", "Leben", "Wasser", "Halme", "Regen", "Nebel", "Nachbearbeitung", "HUD"};
    private static final int MARKS = PASSES.length + 1;

    public final SceneClock clock;
    public final Wiring wiring;
    public final Camera camera = new Camera();
    /** Der Faden der Physik (Phase 1 ohne Aufgaben). */
    public final Physik physik = new Physik();
    /** Phase 2: das Gelände heute (oder die Bühne von Phase 1 als Raster) und seine Detailstufen. */
    public final Terrain terrain;
    private final TerrainLod lod = new TerrainLod();
    /** Phase 2: das Meer im Watt, Gras und Binsen, die Bodendecke um das Auge. */
    public final Ocean ocean = Ocean.watt(4);
    private final OceanGl oceanGl = new OceanGl();
    public final Reed reed = Reed.beaufort(4);
    private final ReedGl reedGl = new ReedGl();
    public final Meadow meadow;
    /** Phase 10: die Bodendecke der Marsch von 1362 (gebaut mit dem Gelände). */
    public volatile Meadow meadow1362;
    private final GroundGl groundGl = new GroundGl();
    private boolean oceanOk, reedOk, groundOk;
    private String oceanCheck = "";
    /** Ebenen zum Abschalten (Bedienfeld, Block BILD). */
    public volatile boolean showWater = true, showReed = true, showGround = true;
    private int panoTex, panoFbo, maskTex, mask1362Tex;

    /** Phase 3: der Himmel (Kern ohne Grafikkarte; die Uhr gibt Zeit und Ort, die Kamera die Augenhöhe). */
    public final Sky sky = new Sky();
    private final SkyGl skyGl = new SkyGl();
    private volatile boolean skyOk;
    private volatile String skyCheck = "";
    /** Augenanpassung: geglättete Leuchtdichte, Leuchtdichte im Blickfeld, Zeit bis zur nächsten Messung. */
    private double adaptLum = -1, viewLum = -1, viewTimer;
    /** Anteil der zwölf Messstrahlen der Augenanpassung, die auf trockenes Land fallen (Watt bei Ebbe, Deich, Koog). */
    private double landInView;
    /**
     * Land wirft mehr zurück als das Wasser, mit dem {@code com.dan.sky} unter der Kimm rechnet (ein Siebtel des
     * gespiegelten Himmels): besonnter Sand und Gras um das Dreifache (wie Glacier mit dem Eis, dort zwölffach).
     */
    public static final double LAND_GAIN = 3.0;
    /** Wie weit das Auge der Dunkelheit unter Wolken folgt (Glacier: 1; Rungholt 0,6, damit Gewitter und Orkan düster bleiben). */
    public static final double STORM_ADAPT = 0.6;
    /** Anteil der Anpassung (1 = volle, 0 = keine); 0,85 lässt die Nacht Nacht bleiben (wie Haithabu und Glacier). */
    public volatile double adaptation = 0.85;
    /** Phase 3: die Wolken (Bedeckung, Art und Wind in Wolkenhöhe aus der Verdrahtung). */
    public final Clouds clouds = new Clouds();
    private final CloudGl cloudGl = new CloudGl();
    private volatile boolean cloudsOk;
    private volatile String cloudCheck = "";
    private double cloudShade = 1;
    public volatile boolean showClouds = true;
    /** Phase 3: Regen. */
    public final Rain rain = new Rain();
    private final RainGl rainGl = new RainGl();
    private volatile boolean rainOk;
    private volatile String rainCheck = "";
    public volatile boolean showRain = true;
    /** Deckkraft des Regens 0..2 (Bedienfeld). */
    public volatile double rainStrength = 1;
    /** Phase 3: Seenebel über dem Watt. */
    public final Fog fog = new Fog();
    private final FogGl fogGl = new FogGl();
    private volatile boolean fogOk;
    private volatile String fogCheck = "";
    public volatile boolean showFog = true;
    /** Phase 3: Gewitter (an der Küste vor allem im Sommer). */
    public final Lightning lightning = new Lightning();
    private final LightningGl lightningGl = new LightningGl();
    private volatile boolean lightningOk;
    private volatile String lightningCheck = "";
    private Lightning.Snapshot lightningSnap;
    public volatile boolean showLightning = true;
    /** Wie weit die Belichtung im Blitz nachgibt (1 = gar nicht); erholt sich in einer Viertelsekunde. */
    private double flashGive = 1;
    /**
     * Höchstens so weit (Faktor, hier eine Blendenstufe) gibt die Belichtung im Blitz nach. Seit Phase 12: nachts (Belichtung
     * um 10⁶) ergab schon ein ferner Blitz ein Nachgeben um das Tausendfache, das Bild war dann gut zwei Sekunden schwarz
     * und baute sich wieder auf; im Orkan der Mandränke alle paar Sekunden.
     */
    static final double FLASH_GIVE_MAX = 2;
    private double[] lightningSeenFrom;
    private volatile String lastBoltText = "";
    private double weatherCpuMs;
    private int depthCopyTex, depthCopyFbo, depthCopyW, depthCopyH;
    /** Nah- und Fernebene der Projektion (m). */
    static final double NEAR = 0.5, FAR = 80_000;

    /** Phase 4: die Überflutung (null, bis sie gebaut ist) und ihr Faden. */
    private volatile Flood flood;
    /**
     * Phase 10: das Gelände von 1362 ({@link com.dan.rungholt.scene.Land1362}, im Hintergrund gebaut) und je Welt eine
     * Überflutung mit ihrem Watt (0 heute, 1 1362); {@link #flood} und {@link #wetField} sind die der Welt, die man sieht.
     */
    public volatile Terrain land1362;
    private final Flood[] floods = new Flood[2];
    private final WetField[] wetFields = new WetField[2];
    private final float[][] mudDepths = new float[2][];
    private volatile int floodEra;
    private boolean land1362Uploaded;
    private Flood floodShown;
    // (seit Phase 13: reedBlend statt reedEra)
    private volatile Thread tideThread;
    private volatile boolean tideRunning;
    private volatile String tideStatus = "Überflutung: noch nicht gebaut";
    private volatile int floodCoarse;
    /** Auf der Grafikkarte: Spiegel je Zelle über dem Pegel (R32F), zuletzt geladene Fassung. */
    private int floodTex, floodTexN, floodUploaded = -1;
    private float[] floodBuf;
    private java.nio.FloatBuffer floodFb;
    /** Ordner für die Zwischendatei der Überflutungskarte. */
    public volatile File cacheDir = new File("daten/cache");
    /** Phase 5: das Watt als Material (Nässe, Sediment, Rippelrichtung je Zelle der Überflutung). */
    public final Mudflat mudflat = Mudflat.wadden();
    private volatile WetField wetField;
    public final MudflatGl mudGl = new MudflatGl();
    /** Phase 6: die Feinform des Watts (Prielkanten, Rinnsale), im Shader wie hier. */
    public final com.dan.mudflat.Relief relief = new com.dan.mudflat.Relief();
    private float[] mudDepth;
    /** Phase 7: die Seehunde, ihre Bank, der Wattwanderer, die Spuren im Watt. */
    public final Seal sealKind = Seal.harbourSeal();
    public final Colony colony = new Colony(sealKind);
    public final Seehundbank seehundbank;
    public final Wattwanderer walker = new Wattwanderer();
    private final SealGl sealGl = new SealGl();
    /** Phase 9: die Figuren aus com.dan.figure (der Wanderer zur Bank, die geführte Wattwanderung). */
    private final FigureGl figureGl = new FigureGl();
    private volatile boolean sealOk, figurOk;
    /** Phase 9: das Jahr (Schnee, Reif, Salzwiese) und Schneefall aus com.dan.season. */
    private final com.dan.season.gl.SnowfallGl snowGl = new com.dan.season.gl.SnowfallGl();
    /** Phase 9: Südfall im Detail: Friesenhaus und Stall aus com.dan.timber, Fething und Pütten. */
    public final com.dan.timber.Timber timber = new com.dan.timber.Timber();
    private final com.dan.timber.gl.TimberGl timberGl = new com.dan.timber.gl.TimberGl();
    private final TeichGl teichGl = new TeichGl();
    private volatile boolean timberOk, teichOk;
    public volatile boolean showHouses = true;
    /** Die Häuser von Südfall, auf den Grund gestellt (beim ersten Zeichnen). */
    public volatile java.util.List<com.dan.timber.House> houses;
    /** Phase 11: die Stadt von 1362 (Häuser, Kirche, Sielhafen mit Kogge, Vieh, Leute, Herdfeuer); mit dem Gelände gebaut. */
    public volatile Town town;
    private Town townOnGpu;
    public volatile boolean showTown = true, showSmoke = true;
    /**
     * Phase 13: die Spuren von Rungholt im Watt von heute ({@link com.dan.rungholt.scene.Spuren}): Kleirücken der
     * Warften, Kulturschicht, Brunnen, Gräben, Sielzüge, Fundament der Kirche; die Magnetik als Karte (Ziel 0..1, blendet
     * in 1,5 s), die Fundstellen als Punkte.
     */
    public volatile com.dan.rungholt.scene.Spuren spuren;
    public volatile boolean showSpuren = true, showFinds = false;
    public volatile double magnetik, magBlend;
    private int spurTex;
    private final com.dan.buoyancy.gl.BuoyancyGl buoyGl = new com.dan.buoyancy.gl.BuoyancyGl();
    public final com.dan.smoke.Smoke smoke = new com.dan.smoke.Smoke();
    /**
     * Phase 17: die Gischt über der Deichkrone in der Mandränke ({@code com.dan.spray}, offen seit Phase 12): Brecher
     * schlagen an die Seeseite der Krone, sobald der Spiegel keinen Meter mehr unter ihr steht, der Orkan reißt Fahnen
     * von den Kämmen. Nur in der Nacht der Mandränke und nur nahe der Kamera (1,5 km).
     */
    public final com.dan.spray.Spray spray = new com.dan.spray.Spray();
    private final com.dan.spray.gl.SprayGl sprayGl = new com.dan.spray.gl.SprayGl();
    private boolean sprayOk;
    private double sprayTimer;
    /** Brecher an der Krone seit dem Start (für Prüfung und HUD). */
    public long dikeBreakers;
    private final com.dan.smoke.gl.SmokeGl smokeGl = new com.dan.smoke.gl.SmokeGl();
    private volatile boolean buoyOk, smokeOk;
    private final com.dan.buoyancy.Mesh[] cogMeshes = new com.dan.buoyancy.Mesh[3];
    private double townMillis = Double.NaN;

    /** Phase 11: alles, was zur Stadt von 1362 gehört (fest aus dem Gelände von 1362). */
    public static final class Town {
        public final java.util.List<com.dan.timber.House> houses, all;
        public final com.dan.timber.House church;
        public final com.dan.timber.Mesh churchMesh;
        public final com.dan.timber.Structures.Set harbour;
        public final com.dan.rungholt.scene.Sielhafen sielhafen;
        public final java.util.List<com.dan.rungholt.scene.Stadt1362.Beast> beasts;
        public final com.dan.buoyancy.Mesh herd;
        public final com.dan.rungholt.scene.Stadt1362.People people;
        public final java.util.List<com.dan.smoke.Source> hearths;
        public final double[] basin;

        Town(Terrain l, Terrain today, int month) {
            java.util.function.DoubleBinaryOperator g = l::height;
            houses = com.dan.rungholt.scene.Stadt1362.houses(g);
            church = com.dan.rungholt.scene.Stadt1362.church(g);
            churchMesh = com.dan.rungholt.scene.Stadt1362.churchMesh();
            java.util.List<com.dan.timber.House> a = new java.util.ArrayList<>(houses);
            a.add(church);
            all = java.util.List.copyOf(a);
            double[][] tief = com.dan.rungholt.scene.Land1362.sieltief(today);
            basin = com.dan.rungholt.scene.Land1362.harbour(tief);
            harbour = com.dan.rungholt.scene.Stadt1362.harbour(basin, l::height);
            sielhafen = new com.dan.rungholt.scene.Sielhafen(tief, basin, l::height);
            beasts = com.dan.rungholt.scene.Stadt1362.beasts(l);
            herd = com.dan.rungholt.scene.Stadt1362.herdMesh(beasts, g);
            people = new com.dan.rungholt.scene.Stadt1362.People(houses, month, 64);
            hearths = com.dan.rungholt.scene.Stadt1362.hearths(houses, month);
        }

        public int cattle() { int n = 0; for (var b : beasts) if (b.kind() == 0) n++; return n; }
    }

    /** Phase 11: das Wasser für die Kogge: Spiegel aus der Überflutung von 1362, Grund aus dem Gelände von 1362. */
    public final com.dan.buoyancy.Sea harbourSea = new com.dan.buoyancy.Sea() {
        public double height(double x, double z) {
            Terrain l = land1362;
            Flood f = floods[1];
            double bed = l == null ? -3 : l.height(x, z);
            if (f == null) return Math.max(bed, level());
            double d = f.depthAt(x, z), sf = f.surfaceAt(x, z);
            return d > 0.02 && !Double.isNaN(sf) && sf > bed ? sf : bed;
        }
        public double[] velocity(double x, double z) {
            Flood f = floods[1];
            if (f == null) return new double[3];
            double[] u = f.velocityAt(x, z);
            return new double[]{u[0], 0, u[1]};
        }
        public double level() { return Renderer.this.level(); }
        public double bed(double x, double z) { Terrain l = land1362; return l == null ? -3 : l.height(x, z); }
    };
    private volatile boolean snowOk;
    private double seasonMillis = Double.NaN, snowRate;
    public final Person walkerPerson = new Person(0, Person.Role.ADULT, Outfit.walker());
    /** Die Wattwanderung nach Südfall (null = keine unterwegs). */
    public volatile Hike hike;
    public volatile boolean showFigures = true;
    /** Phase 13: die Archäologen bei Ebbe an der Kirche ({@link com.dan.rungholt.scene.Grabung}) und ihr Gerät. */
    public final com.dan.rungholt.scene.Grabung grabung;
    public volatile boolean showDig = true;
    private com.dan.buoyancy.Mesh digMesh;
    private double digMeshEra = Double.NaN;
    public final com.dan.rungholt.scene.Grabung.Welt digWorld = new com.dan.rungholt.scene.Grabung.Welt() {
        @Override public double depth(double x, double z) { return wiring == null ? 0 : wiring.level.depthAt(x, z); }
        @Override public double ground(double x, double z) { return groundHeight(x, z); }
        @Override public boolean wetIn(double x, double z, double hoursAhead) {
            Flood f = flood;
            if (f == null || wiring == null || !f.inside(x, z)) return false;
            double spill = f.spillAt(x, z), travel = f.travelAt(x, z), g = terrain.height(x, z);
            if (Double.isNaN(spill) || Double.isNaN(travel)) return false;
            double lv = wiring.level.floodTide().levelAt(wiring.level.hours() + hoursAhead - travel / 3600);
            return lv > Math.max(spill, g + 0.02);
        }
        @Override public double sunDeg() { return clock.sun()[0]; }
        @Override public double windSpeed() { return wiring == null ? 0 : wiring.wind.speed(); }
    };
    private final Hike.World hikeWorld = new Hike.World() {
        @Override public double ground(double x, double z) { return groundHeight(x, z); }
        @Override public double depth(double x, double z) { return wiring == null ? 0 : wiring.level.depthAt(x, z); }
        @Override public double mud(double x, double z) { WetField wf = wetField; return wf == null ? 0 : wf.sedimentAt(x, z); }
    };
    public volatile boolean showSeals = true, showTracks = true;
    public final com.dan.mudflat.TrackMap trackMap = com.dan.mudflat.TrackMap.standard();
    private double trackTimer;
    /** Szenenzeit des letzten Schritts der Seehunde (ms); NaN = noch keiner. */
    private double sealMillis = Double.NaN;
    /** Phase 8: die Wattvögel, ihr Revier und ihre Rastplätze (aus den Höhen: das Vorland von Südfall und Nordstrand). */
    public final Waders waders = new Waders();
    private double hwHours = Double.NaN;
    private final WaderGl waderGl = new WaderGl();
    private volatile boolean waderOk;
    public volatile boolean showBirds = true;
    /** Seehunde zählen (Bedienfeld: von Hand; −1 = nach der Qualitätsstufe). */
    public volatile int sealCount = -1;
    /** Wetter für das Trocknen, im Bildfaden gesetzt, im Faden der Tide gelesen: Anteil der Sonne nach Wolken und Dunst. */
    private volatile double mudCloud = 1;
    /** Abstand der Schritte der Tide in Echtzeit (ms). */
    public static final int TIDE_STEP_MS = 100;
    static final int PANO_W = 256, PANO_H = 128;
    /** Dunst ohne com.dan.sky (wie common.glsl: 45 km), m. */
    static final double FOG_DISTANCE = 45_000;

    private volatile Quality quality = Quality.MITTEL;
    public volatile double renderScale = quality.renderScale;
    /**
     * Regelt die Auflösung nach der Zeit der Grafikkarte: über dem Budget von {@link #targetFps} sinkt sie in Schritten
     * bis {@link #MIN_SCALE}, mit Luft steigt sie wieder bis zur Stufe (aus Haithabu).
     */
    public volatile boolean autoScale = true;
    public volatile double targetFps = 60;
    public static final double MIN_SCALE = 0.5;
    private long scaleNanos;
    private int scaleSteps;

    public volatile boolean showHud = true, hudFull;
    /** Marken: Raster, Grundriss der Kirche [G], Ring um die Warft von Südfall, Nordstrich. */
    /**
     * Die Marken von Phase 1 im Gelände (1-km-Raster, Umriss der Kirche, Ring um die Warft von Südfall, Nordpfeil). Seit
     * Phase 17 aus, bis man sie im Block BILD einschaltet: Sie waren die lange helle Linie im Vogelauge und der orange
     * Strich am Horizont von Südfall auf den Blättern von Phase 15.
     */
    public volatile boolean showMarks = false;
    /** Szene anhalten (Uhr, Kamera, Physik). */
    public volatile boolean frozen;
    /** Belichtung in Blendenstufen von Hand dazu. */
    public volatile double exposureBias;
    public volatile boolean photoRequest;
    /** Das nächste fertige Fensterbild an diesen Empfänger (einmal; im GL-Faden). */
    public volatile Consumer<BufferedImage> grabRequest;
    /** Ordner für Fotos (F12); ohne Angabe {@code docs/status} im Arbeitsverzeichnis. */
    public volatile File photoDir;

    private final Shader skyShader = new Shader("Himmel", null, "fullscreen.vert", "sky.frag"),
            skyPano = new Shader("Himmelspanorama", null, "fullscreen.vert", "skypano.frag"),
            terrainShader = new Shader("Gelände", null, "terrain.vert", "terrain.frag"),
            post = new Shader("Nachbearbeitung", null, "fullscreen.vert", "post.frag"),
            overlay = new Shader("Überlagerung", null, "fullscreen.vert", "overlay.frag");
    private final Shader[] all = {skyShader, skyPano, terrainShader, post, overlay};
    private final HudOverlay hud = new HudOverlay();
    /** Phase 13: die Warftkarte (Tafel oben rechts), null = zu. */
    private final HudOverlay card = new HudOverlay();
    public volatile String[] warftCard;
    private volatile String[] cardPainted;
    private int emptyVao, hdrTex, depthTex, hdrFbo, hdrW, hdrH, surfW = 1, surfH = 1, defaultFbo;
    private boolean reversedZ, ready;
    private final int[][] stamp = new int[2][MARKS];
    private final double[] passMs = new double[MARKS - 1];
    private int frame;
    private long lastNanos, statsNanos;
    private int statsFrames;
    private double fps, gpuMs, cpuMs, worstMs;
    private volatile String glInfo = "", error;
    private volatile ComputeProbe.Result compute;
    private volatile boolean reloadShaders;
    private Consumer<String> statusListener = s -> { };
    private Consumer<String> errorListener = s -> { };
    private volatile String photoMsg = "";
    private volatile long photoMsgUntil;

    public Renderer(SceneClock clock, Wiring wiring) { this(clock, wiring, Terrain.load(Terrain.defaultDir())); }

    public Renderer(SceneClock clock, Wiring wiring, Terrain terrain) {
        this.clock = clock;
        this.wiring = wiring;
        this.terrain = terrain;
        if (wiring != null) wiring.setTerrain(terrain);
        seehundbank = new Seehundbank(terrain);
        colony.banks.addAll(seehundbank.banks());
        colony.world = new Colony.World() {
            @Override public double ground(double x, double z) { return groundHeight(x, z); }
            @Override public double water(double x, double z) {
                if (Renderer.this.wiring == null) return Double.NaN;
                return Renderer.this.wiring.level.depthAt(x, z) > 0.005 ? Renderer.this.wiring.level.surfaceAt(x, z) : Double.NaN;
            }
        };
        colony.onTrack = (x, z, dx, dz) -> { WetField wf = wetField; if (wf != null) wf.addTrack(x, z, dx, dz, 2); };
        sealKind.seed = 1362;
        waders.seed = 1362;
        waders.revier.add(new Waders.Area(-1000, 200, 2600));
        waders.revier.add(new Waders.Area(2500, 800, 2200));
        // Hochwasserrastplätze: das Vorland von Südfall (Salzwiese um die Warft) und am Deich von Nordstrand bei Fuhlehörn
        waders.roosts.add(new Waders.Area(-817, -129, 140));
        waders.roosts.add(new Waders.Area(-763, 98, 120));
        waders.roosts.add(new Waders.Area(-490, -132, 90));
        waders.roosts.add(new Waders.Area(5138, -2177, 110));
        waders.pastures.add(new Waders.Area(-800, -20, 220));
        waders.world = new Waders.World() {
            @Override public double ground(double x, double z) { return groundHeight(x, z); }
            @Override public double depth(double x, double z) { return Renderer.this.wiring == null ? 0 : Renderer.this.wiring.level.depthAt(x, z); }
            @Override public double mud(double x, double z) { WetField wf = wetField; return wf == null ? 0.4 : wf.sedimentAt(x, z); }
            @Override public boolean marsh(double x, double z) { byte a = Renderer.this.terrain.artAt(x, z); return a != Terrain.WATT; }
            @Override public double highWaterHours() { return hwHours; }
        };
        if (wiring != null) { wiring.haulout = seehundbank; wiring.disturber = walker; }
        grabung = new com.dan.rungholt.scene.Grabung(clock.date().getMonthValue(), 2023);   // Phase 13
        ocean.seed = 1362;
        reed.seed = 1362;
        reed.season = 0.25;
        meadow = new Meadow(Biome.deich(), new Bodendecke(terrain));
        meadow.radius = 18;
        meadow.near = 6.5f;
        meadow.seed = 1362;
        lod.lodRange = 2.6;
        sky.latDeg = Site.LAT;
        sky.lonDeg = Site.LON;
        sky.epochMillis = clock.epochMillis();
        fog.seed = 1362;
        lightning.seed = 1362;
        // das Rauschen der Wolken (8 MB, 0,5 bis 3 s) schon jetzt im Hintergrund rechnen
        Thread nt = new Thread(() -> { Noise.base(); Noise.detail(); Noise.weather(); }, "cloud-noise");
        nt.setDaemon(true);
        nt.start();
        setQuality(quality);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.targetGround = terrain.height(camera.tx, camera.tz);
        camera.snap();
    }

    public Renderer() { this(new SceneClock(), null, Terrain.flat()); }

    /**
     * Die Bodendecke nach der Art des Bodens [G]: Deich, Warft und Koog Weide (Gras, Löwenzahn, Margeriten), das
     * Vorland feucht mit Seggen, im Watt nichts.
     */
    static final class Bodendecke implements com.dan.ground.Site {
        private final Terrain t;
        Bodendecke(Terrain t) { this.t = t; }
        @Override public float height(double x, double z) { return (float) t.height(x, z); }
        @Override public void cover(double x, double z, float[] out) {
            com.dan.ground.Site.MEADOW.cover(x, z, out);
            byte a = t.artAt(x, z);
            if (a == Terrain.WATT || a == Terrain.SIELZUG) { out[0] = out[1] = out[2] = out[3] = 0; return; }
            if (a == Terrain.VORLAND) { out[1] *= 0.2f; out[2] = 0; out[4] = 1; }
            if (a == Terrain.ERHOEHT || a == Terrain.DEICH1362 || a == Terrain.WARFT) { out[1] *= 0.6f; out[2] *= 0.3f; out[3] *= 0.3f; }
            if (a == Terrain.MARSCH) { out[2] = 0; out[4] = Math.max(out[4], 0.3f); }
        }
    }

    /** Wo Gras und Binsen stehen [G]: Gras auf Deich, Warft und Koog, Binsen dicht in der Salzwiese, im Watt nichts. */
    double reedDensity(double x, double z, Reed.Kind k) { return reedDensity(x, z, k, wiring == null || land1362 == null ? 0 : reedBlend); }

    /** Phase 13: Dichte der Halme bei der Überblendung b (auch für die Prüfung). */
    public double reedDensity(double x, double z, Reed.Kind k, double b) {
        // Phase 13: während der Überblendung wächst das Gras von 1362 über den Sand, das von heute vergeht
        if (land1362 == null) b = 0;
        if (b > 0 && b < 1) {
            double grow = Land1362Grow.smooth(0.2, 0.95, b), fade = 1 - Land1362Grow.smooth(0.05, 0.6, b);
            return Math.max(reedDensity(terrain.artAt(x, z), k) * fade, reedDensity(land1362.artAt(x, z), k) * grow);
        }
        return reedDensity((b >= 1 && land1362 != null ? land1362 : b <= 0 ? terrain : land()).artAt(x, z), k);
    }

    /** Stand der Überblendung, mit dem die Halme zuletzt gebaut wurden (in Achteln). */
    private volatile double reedBlend;

    private static final class Land1362Grow { static double smooth(double a, double c, double x) { double t = Math.max(0, Math.min(1, (x - a) / (c - a))); return t * t * (3 - 2 * t); } }

    double reedDensity(byte a, Reed.Kind k) {
        return switch (k) {
            case GRAS -> a == Terrain.ERHOEHT || a == Terrain.DEICH1362 || a == Terrain.WARFT ? 0.9 : a == Terrain.KOOG || a == Terrain.RAND || a == Terrain.MARSCH ? 0.7 : a == Terrain.VORLAND ? 0.35 : 0;
            case BINSE -> a == Terrain.VORLAND ? 0.6 : 0;
            default -> 0;
        };
    }

    // ------------------------------------------------------------------ Zugriff
    public void setStatusListener(Consumer<String> l) { statusListener = l; }
    public void setErrorListener(Consumer<String> l) { errorListener = l; }
    public String glInfo() { return glInfo; }
    public String error() { return error; }
    public double fps() { return fps; }
    public double gpuMs() { return gpuMs; }
    public double cpuMs() { return cpuMs; }
    public boolean reversedDepth() { return reversedZ; }
    public ComputeProbe.Result compute() { return compute; }
    public Quality quality() { return quality; }
    public void requestShaderReload() { reloadShaders = true; }
    public double[] lastPassTimes() { return passMs.clone(); }
    public long frames() { return frame; }
    public double takeWorstFrameMs() { double w = worstMs; worstMs = 0; return w; }

    /** Die Programme der App (für die Prüfung). */
    public static String[][] shaderFiles() {
        return new String[][]{{"fullscreen.vert", "sky.frag"}, {"fullscreen.vert", "skypano.frag"}, {"terrain.vert", "terrain.frag"},
                {"fullscreen.vert", "post.frag"}, {"fullscreen.vert", "overlay.frag"}, {"probe.comp"}};
    }

    public void setQuality(Quality q) {
        quality = q;
        renderScale = q.renderScale;
        ocean.quality = q.ocean;
        reed.quality = q.reed;
        lod.lodRange = q.terrainRange;
        lod.finest = q.terrainFine <= 2 ? 0 : q.terrainFine <= 4 ? 1 : 2;
        if (meadow != null) meadow.radius = (float) q.groundRadius;
        if (meadow1362 != null) meadow1362.radius = (float) q.groundRadius;
        clouds.quality = q.clouds;
        rain.quality = q.rain;
        fog.quality = q.fog;
        lightning.quality = q.lightning;
        sealKind.quality = q.seal;
        waders.quality = q.birds;
        colony.count = sealCount >= 0 ? sealCount : sealKind.animalsFor(q.seal);
        // Phase 17: die übrigen Packages folgen der Stufe
        mudflat.quality = q.mudflat();
        timber.quality = q.timber();
        smoke.quality = q.smoke();
        spray.quality = q.spray();
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m != null && m.bruch != null && m.bruch.solid == null) m.bruch.world.quality = q.fracture();   // der Körper entsteht beim Bruch
    }

    /** Pegel jetzt (m über NHN): Platzhalter aus M2 und M4 bis {@code tide} in Phase 4. */
    public double level() { return wiring != null ? wiring.level.level() : HEVER.levelAtMillis(clock.epochMillis()); }
    private static final Tide HEVER = Tide.hever();

    // ------------------------------------------------------------------ Phase 4: die Tide

    /** Die Überflutung, sobald gebaut (sonst null). */
    public Flood flood() { return flood; }
    public String tideStatus() { return tideStatus; }

    /**
     * Die Überflutungskarte bauen (oder aus {@link #cacheDir} laden) und den Faden der Tide starten: alle
     * {@link #TIDE_STEP_MS} ms ein Schritt zur Szenenzeit. Ohne Verdrahtung oder ohne Höhendaten nichts.
     */
    public synchronized void startTide() {
        if (wiring == null || !terrain.measured() || tideThread != null) return;
        tideRunning = true;
        Thread t = new Thread(() -> {
            try {
                buildFlood(quality.tide.coarse);
                if (land1362 == null) buildLand1362();
                if (spuren == null) buildSpuren();   // Phase 13
                while (tideRunning) {
                    long t0 = System.nanoTime();
                    if (floodCoarse != quality.tide.coarse) { floods[0] = floods[1] = null; buildFlood(quality.tide.coarse); }
                    selectEra();
                    stepTideNow();
                    long sleep = TIDE_STEP_MS - (System.nanoTime() - t0) / 1_000_000;
                    Thread.sleep(Math.max(5, sleep));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (RuntimeException | OutOfMemoryError e) {
                tideStatus = "!Überflutung: " + e;
                System.err.println("Rungholt: Tide: " + e);
            }
        }, "Tide");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        tideThread = t;
        t.start();
    }

    /**
     * Phase 13: Draufsicht auf die Messfläche: kreisen um einen Punkt 250 m südlich der Kirche, senkrecht von oben aus
     * 3,2 km, Norden oben; schaltet nach heute und blendet die Magnetik ein ({@code mag}).
     */
    public void placeSurveyView(boolean mag) {
        camera.stopFahrt();
        camera.stopMitfahrt();
        camera.diving = false;
        if (wiring != null) wiring.era.target = 0;
        camera.mode = com.dan.rungholt.camera.Camera.Mode.KREISEN;
        camera.lookAt(0, 0, 250, 0, 89, 2600);
        camera.snap();
        if (mag) magnetik = 1;
    }

    /** Phase 13: die Spurenkarte bauen (gut 2 s; im Faden der Tide oder für Werkzeuge). */
    public synchronized void buildSpuren() {
        if (spuren != null) return;
        com.dan.rungholt.scene.Spuren s = com.dan.rungholt.scene.Spuren.build();
        spuren = s;
        System.out.println("Rungholt: " + s.describe());
    }

    /** Phase 10: das Gelände von 1362 bauen (rund 5 s; im Faden der Tide oder für Werkzeuge). */
    public void buildLand1362() {
        if (!terrain.measured()) return;
        long t0 = System.nanoTime();
        Terrain l = com.dan.rungholt.scene.Land1362.build(terrain);
        Meadow m = new Meadow(Biome.marsch(), new Bodendecke(l));
        m.radius = meadow.radius;
        m.near = meadow.near;
        m.seed = 1362;
        meadow1362 = m;
        town = new Town(l, terrain, clock.date().getMonthValue());   // Phase 11
        smoke.setSources(town.hearths);
        land1362 = l;
        System.out.printf(Locale.GERMAN, "Rungholt: Gelände 1362 in %.1f s · %d Warften%n", (System.nanoTime() - t0) / 1e9, com.dan.rungholt.scene.Land1362.warften().size());
    }

    /**
     * Phase 10: die Überflutung der Welt nehmen, die man sieht (ab der Hälfte der Überblendung); die von 1362 beim
     * ersten Mal bauen (oder aus der Zwischendatei laden).
     */
    public void selectEra() {
        int want = wiring != null && wiring.era.is1362() && land1362 != null ? 1 : 0;
        if (want == floodEra && flood == floods[want] && flood != null) return;
        if (floods[want] == null) { floodEra = want; buildFlood(floodCoarse == 0 ? quality.tide.coarse : floodCoarse); return; }
        floodEra = want;
        Flood f = floods[want];
        f.step(wiring.level.floodTide(), wiring.level.hours());
        mudDepth = mudDepths[want];
        flood = f;
        wetField = wetFields[want];
        wiring.level.flood = f;
        wiring.wetness.field = wetFields[want];
    }

    /** Phase 10: die Wege von 1362 für den Shader: die Warftenkette entlang, dann am Hauptsielzug zum Siel (1e9 trennt). */
    static final float[] ROADS_1362 = roads1362();

    static final float[] CANALS_1362 = canals1362();

    private static float[] canals1362() {
        java.util.List<double[]> p = new java.util.ArrayList<>();
        for (double[][] c : com.dan.rungholt.scene.Land1362.CANALS) {
            if (!p.isEmpty()) p.add(new double[]{1e9, 1e9});
            p.addAll(java.util.List.of(c));
        }
        float[] r = new float[p.size() * 2];
        for (int i = 0; i < p.size(); i++) { r[2 * i] = (float) p.get(i)[0]; r[2 * i + 1] = (float) p.get(i)[1]; }
        return r;
    }

    private static float[] roads1362() {
        java.util.List<double[]> p = new java.util.ArrayList<>(java.util.List.of(com.dan.rungholt.scene.Land1362.CHAIN));
        p.add(new double[]{1e9, 1e9});
        for (double[] c : com.dan.rungholt.scene.Land1362.CANALS[0]) p.add(new double[]{c[0] + 4, c[1] - 20});
        float[] r = new float[p.size() * 2];
        for (int i = 0; i < p.size(); i++) { r[2 * i] = (float) p.get(i)[0]; r[2 * i + 1] = (float) p.get(i)[1]; }
        return r;
    }

    /** Phase 10: die Kamera auf die Krone des Seedeichs von 1362, gut 500 m südlich der Kirche, Blick nach Norden. */
    public void placeDikeView() {
        camera.stopFahrt();
        camera.stopMitfahrt();
        camera.diving = false;
        double x = 60, z = 526;
        double crest = land1362 != null ? land1362.height(x, z) : com.dan.rungholt.scene.Land1362.CREST;
        camera.place(x, Math.max(groundHeight(x, z), crest) + 1.7, z, 355, 2);
    }

    /** Phase 11: eine Ansicht einnehmen; 8 und 9 schalten sofort nach 1362 (der Grund ist der von 1362). */
    public boolean placeView(com.dan.rungholt.camera.Ansichten.Ansicht a) {
        if (a.key() == 5 && placeSealView()) return true;
        if (a.key() >= 8 && com.dan.rungholt.camera.Ansichten.ready(a, PHASE)) {
            if (wiring != null) { wiring.era.target = 1; wiring.era.blend = 1; }
            camera.diving = false;
            return com.dan.rungholt.camera.Ansichten.apply(a, camera, PHASE, this::ground1362);
        }
        return com.dan.rungholt.camera.Ansichten.apply(a, camera, PHASE, this::groundHeight);
    }

    /** Der Grund von 1362 (vor dem Bau des Geländes genähert). */
    public double ground1362(double x, double z) {
        Terrain l = land1362;
        return l != null ? l.height(x, z) : com.dan.rungholt.scene.Land1362.estimate(x, z);
    }

    /**
     * Phase 11: Fahrt „Über die Warftenkette“: 30 m über der Marsch, 140 m südlich der Kette von West nach Ost (gut
     * 2,5 km in 150 s), der Blick 55° nach links auf die Stadt, 12° nach unten. Schaltet nach 1362.
     */
    public void startChainFlight() {
        if (wiring != null) { wiring.era.target = 1; wiring.era.blend = 1; }
        double[][] c = com.dan.rungholt.scene.Land1362.CHAIN;
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (int i = 0; i < c.length; i++) {
            int a = Math.max(0, i - 1), b = Math.min(c.length - 1, i + 1);
            double dx = c[b][0] - c[a][0], dz = c[b][1] - c[a][1], l = Math.hypot(dx, dz);
            double nx = -dz / l, nz = dx / l;   // rechts der Fahrtrichtung (nach Süden)
            double x = c[i][0] + nx * 140, z = c[i][1] + nz * 140;
            pts.add(new double[]{x, ground1362(x, z) + 30, z});
        }
        com.dan.rungholt.camera.Fahrt f = new com.dan.rungholt.camera.Fahrt("Über die Warftenkette", pts.toArray(new double[0][]), 150, -55, 12);
        camera.diving = false;
        camera.startFahrt(f);
    }

    /** „Gelände 1362: 54 Warften · Seedeich +3,5 m · Überflutung 1362: nass 18 %“ */
    public String landLine() {
        if (land1362 == null) return terrain.measured() ? "Gelände 1362: wird gebaut …" : "Gelände 1362: ohne Höhendaten nicht";
        Flood f1 = floods[1];
        return String.format(Locale.GERMAN, "Gelände 1362: %d Warften, Kette %.1f km, Seedeich Krone %+.1f m · Überflutung 1362: %s",
                com.dan.rungholt.scene.Land1362.warften().size(), com.dan.rungholt.scene.Land1362.chainLength() / 1000, com.dan.rungholt.scene.Land1362.CREST,
                f1 == null ? "beim ersten Wechsel" : String.format(Locale.GERMAN, "nass %.0f %%", f1.wetShare * 100));
    }

    /** Das Gelände der Welt, die man sieht (heute oder 1362). */
    public Terrain land() { Terrain l = land1362; return l != null && wiring != null && wiring.era.is1362() ? l : terrain; }

    /** Die Karte bauen (im Faden der Tide oder für Werkzeuge) für die Welt {@link #floodEra}. */
    public void buildFlood(int coarse) {
        int n = Terrain.N;
        int era = floodEra == 1 && land1362 != null ? 1 : 0;
        Terrain terrain = era == 1 ? land1362 : this.terrain;
        float[] h = new float[n * n];
        boolean[] seed = new boolean[n * n], blocked = new boolean[n * n];
        for (int k = 0; k < h.length; k++) {
            h[k] = (float) Terrain.decode(terrain.h[k]);
            int i = k % n, j = k / n;
            byte a = terrain.art[k];
            blocked[k] = a == Terrain.KOOG || a == Terrain.RAND || a == Terrain.SIELZUG;
            seed[k] = (i == 0 || j == 0 || i == n - 1 || j == n - 1) && a == Terrain.WATT && h[k] < Site.MTNW;
        }
        tideStatus = "Überflutung: wird gebaut …";
        File cache = cacheDir == null ? null : new File(cacheDir, "tide_" + n + "_" + coarse + (era == 1 ? "_1362" : "") + ".bin");
        Flood f = Flood.buildCached(cache, h, n, n, Terrain.X0, Terrain.Z0, Terrain.CELL, seed, blocked, Site.MTHW, coarse);
        Flood old = flood;
        if (old != null) f.currents = old.currents;
        floodCoarse = coarse;
        Tide td = wiring.level.floodTide();
        f.step(td, wiring.level.hours());
        WetField wf = new WetField(mudflat, f.nx, f.nz, f.x0, f.z0, f.cell);
        wf.classify(f.ground, f.travel, Site.MTNW, Site.MTHW);
        mudDepth = new float[f.nx * f.nz];
        stepMud(f, wf);
        floods[era] = f;
        wetFields[era] = wf;
        mudDepths[era] = mudDepth;
        floodEra = era;
        flood = f;
        wetField = wf;
        wiring.level.flood = f;
        wiring.wetness.field = wf;
        System.out.println("Rungholt: " + f.describe());
        System.out.println("Rungholt: " + wf.describe());
    }

    /** Einen Schritt der Tide zur Szenenzeit (der Faden ruft es; Standbilder auch direkt). */
    public void stepTideNow() {
        Flood f = flood;
        if (f == null || wiring == null) return;
        Tide td = wiring.level.floodTide();
        f.step(td, wiring.level.hours());
        WetField wf = wetField;
        if (wf != null) stepMud(f, wf);
        tideStatus = f.describe();
    }

    /** Phase 5: das Watt nach der Überflutung und dem Wetter trocknen lassen (im Faden der Tide). */
    private void stepMud(Flood f, WetField wf) {
        float[] d = mudDepth;
        for (int k = 0; k < d.length; k++) d[k] = f.surface[k] - f.ground[k];
        double wind = wiring.regie ? wiring.gustNow : wiring.wind.speed();
        double mudSun = mudSun();
        wf.step(wiring.level.hours(), d, f.u, f.v, f.dryHours, new WetField.Weather(mudSun, wind, wiring.airTempC, showRain ? wiring.rainRate : 0));
    }

    /** Sonne am Boden 0..1 für das Trocknen: Höhe zur Szenenzeit (auch ohne Bilder, etwa im Vorlauf), Wolken vom letzten Bild. */
    double mudSun() { return Math.max(0, Math.sin(Math.toRadians(clock.sun()[0]))) * mudCloud; }

    /**
     * Der Grund mit der Feinform des Watts (Phase 6), wie ihn das Bild zeichnet: Raster, auf dem Watt dazu Prielkante,
     * Rinnsale und Wellenrücken aus {@link #relief}.
     */
    /**
     * Phase 13: die Spurenkarte einmal hochladen (Einheit 18, RGBA8 mit Mip-Stufen) und die Werte der Spuren, Brunnen,
     * Fundstellen und der Kirche an das Gelände.
     */
    private void bindSpuren(GL4 gl, Shader sh) {
        com.dan.rungholt.scene.Spuren s = spuren;
        if (s != null && spurTex == 0) {
            int[] t = new int[1];
            gl.glGenTextures(1, t, 0);
            spurTex = t[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, spurTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, com.dan.rungholt.scene.Spuren.N, com.dan.rungholt.scene.Spuren.N, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(s.rgba));
            gl.glGenerateMipmap(GL.GL_TEXTURE_2D);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        }
        gl.glActiveTexture(GL.GL_TEXTURE18);
        gl.glBindTexture(GL.GL_TEXTURE_2D, spurTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        sh.set(gl, "uSpurTex", 18);
        double size = com.dan.rungholt.scene.Spuren.N * com.dan.rungholt.scene.Spuren.CELL;
        sh.set(gl, "uSpurRect", (float) com.dan.rungholt.scene.Spuren.X0, (float) com.dan.rungholt.scene.Spuren.Z0, (float) size, spurTex != 0 ? 1f : 0f);
        sh.set(gl, "uSpuren", showSpuren && spurTex != 0 ? 1f : 0f, (float) magBlend, showFinds ? 1f : 0f, (float) com.dan.rungholt.scene.Spuren.HEIGHT);
        if (!spurUniforms) {
            java.util.List<double[]> ws = com.dan.rungholt.scene.Spuren.wells();
            int nw = Math.min(48, ws.size());
            float[] wv = new float[96];
            for (int i = 0; i < nw; i++) { wv[2 * i] = (float) ws.get(i)[0]; wv[2 * i + 1] = (float) ws.get(i)[1]; }
            java.util.List<com.dan.rungholt.scene.Spuren.Fund> fs = com.dan.rungholt.scene.Spuren.FINDS;
            int nf = Math.min(16, fs.size());
            float[] fv = new float[48];
            for (int i = 0; i < nf; i++) { fv[3 * i] = (float) fs.get(i).x(); fv[3 * i + 1] = (float) fs.get(i).z(); fv[3 * i + 2] = fs.get(i).kind(); }
            spurWells = wv; spurWellN = nw; spurFinds = fv; spurFindN = nf;
            spurUniforms = true;
        }
        gl.glUniform2fv(gl.glGetUniformLocation(sh.id(), "uWells"), 48, spurWells, 0);
        sh.set(gl, "uWellN", spurWellN);
        gl.glUniform3fv(gl.glGetUniformLocation(sh.id(), "uFinds"), 16, spurFinds, 0);
        sh.set(gl, "uFindN", spurFindN);
        com.dan.timber.Church.Spec cs = com.dan.timber.Church.rungholt();
        double x0 = cs.west(), xt = x0 + cs.towerSide(), xn = xt + cs.naveLength(), xc = xn + cs.choirLength();
        sh.set(gl, "uChurchX", (float) x0, (float) xt, (float) xn, (float) xc);
        sh.set(gl, "uChurchW", (float) (cs.towerSide() / 2), (float) (cs.naveWidth() / 2), (float) (cs.choirWidth() / 2), (float) cs.apseRadius());
        double hd = Math.toRadians(com.dan.rungholt.scene.Stadt1362.CHURCH_HEADING);
        sh.set(gl, "uChurchAxis", (float) Math.sin(hd), (float) -Math.cos(hd));
    }
    private boolean spurUniforms;
    private float[] spurWells, spurFinds;
    private int spurWellN, spurFindN;

    public double groundHeight(double x, double z) {
        Terrain old = land1362;
        double b = wiring == null || old == null ? 0 : wiring.era.blend();
        if (b > 0) {   // Phase 10: während und nach der Überblendung gemischt; die Feinform auf dem Watt beider Welten
            double h0 = groundToday(x, z), h1 = old.height(x, z);
            if (relief.on && old.artAt(x, z) == Terrain.WATT && terrain.artAt(x, z) != Terrain.WATT) {
                double hs = old.smoothHeight(x, z);
                double gx = old.smoothHeight(x + 16, z) - old.smoothHeight(x - 16, z), gz = old.smoothHeight(x, z + 16) - old.smoothHeight(x, z - 16);
                h1 += relief.delta(x, z, h1, hs, Math.hypot(gx, gz) / 32);
            } else if (old.artAt(x, z) == Terrain.WATT) h1 += h0 - terrain.height(x, z);
            return h0 + (h1 - h0) * b;
        }
        return groundToday(x, z);
    }

    private double groundToday(double x, double z) {
        double hf = terrain.height(x, z);
        if (terrain.artAt(x, z) != Terrain.WATT) return hf;
        double sp = spurHeight(x, z);   // Phase 13: der Kleirücken der Spuren
        if (!relief.on) return hf + sp;
        double hs = terrain.smoothHeight(x, z);
        double gx = terrain.smoothHeight(x + 16, z) - terrain.smoothHeight(x - 16, z), gz = terrain.smoothHeight(x, z + 16) - terrain.smoothHeight(x, z - 16);
        return hf + sp + relief.delta(x, z, hf, hs, Math.hypot(gx, gz) / 32);
    }

    /** Phase 13: Höhe der Spur über dem Watt (m) an (x, z), wie spurHeight im Shader (ohne die Überblendung). */
    public double spurHeight(double x, double z) {
        com.dan.rungholt.scene.Spuren s = spuren;
        return s == null || !showSpuren || spurTex == 0 && !spurForce ? 0 : s.height(x, z);
    }
    /** Werkzeuge ohne Bild: die Höhe der Spuren auch ohne hochgeladene Textur. */
    public volatile boolean spurForce;

    /** Das Watt (null, bis die Überflutung gebaut ist). */
    public WetField wetField() { return wetField; }

    /** „Watt: trockenes Watt im Mittel 54 % nass · hier Mischwatt, 80 % nass · halb trocken in 35 min · …“ */
    public String mudLine() {
        WetField wf = wetField;
        if (wf == null) return "Watt: wartet auf die Überflutung";
        double x = camera.eye[0], z = camera.eye[2];
        String[] cls = {"Sand", "Mischwatt", "Schlick"};
        double wind = wiring == null ? 6 : wiring.regie ? wiring.gustNow : wiring.wind.speed();
        double half = wf.halfLife(new WetField.Weather(mudSun(), wind, wiring == null ? 15 : wiring.airTempC, wiring == null ? 0 : wiring.rainRate));
        String here = wf.index(x, z) < 0 ? "" : String.format(Locale.GERMAN, " · hier %s, %.0f %% nass", cls[wf.classAt(x, z).ordinal()], wf.wetnessAt(x, z) * 100);
        String cur = wf.index(x, z) < 0 ? "" : String.format(Locale.GERMAN, ", Rippel %.0f %%", wf.currentAt(x, z) * 100);
        return String.format(Locale.GERMAN, "Watt: trockenes Watt im Mittel %.0f %% nass%s%s · halb trocken in %s · Feinform %s · %.1f ms je Schritt · %s",
                wf.meanWet * 100, here, cur, half > 10 ? "> 10 h" : half >= 1 ? String.format(Locale.GERMAN, "%.1f h", half) : String.format(Locale.GERMAN, "%.0f min", half * 60), relief.on ? "an" : "aus", wf.stepMs, mudGl.info());
    }

    /** Den Faden anhalten (Standbilder rechnen dann selbst mit {@link #stepTideNow}). */
    public void stopTide() {
        tideRunning = false;
        Thread t = tideThread;
        if (t != null) { t.interrupt(); try { t.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); } }
        tideThread = null;
    }

    /** Spiegel je Zelle als Textur hochladen, wenn sich etwas geändert hat (im GL-Faden). */
    private void uploadFlood(GL4 gl, double base) {
        Flood f = flood;
        if (f == null) return;
        int n = f.nx;
        if (floodTex == 0 || floodTexN != n) {
            Gpu.deleteTextures(gl, floodTex);
            floodTex = Gpu.texture(gl, n, f.nz, GL4.GL_R32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, floodTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            floodTexN = n;
            floodBuf = new float[n * f.nz];
            floodFb = Buffers.newDirectFloatBuffer(n * f.nz);
            floodUploaded = -1;
        }
        if (f != floodShown) { floodUploaded = -1; flowUploaded = -1; floodShown = f; }   // Phase 10: die andere Welt
        int v = f.version;
        if (v == floodUploaded) return;
        f.fill(floodBuf, base, -30f);
        floodFb.clear();
        floodFb.put(floodBuf).flip();
        gl.glBindTexture(GL.GL_TEXTURE_2D, floodTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, f.nz, GL4.GL_RED, GL.GL_FLOAT, floodFb);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        floodUploaded = v;
    }

    private int flowTex, flowTexN, flowUploaded = -1;
    private short[] flowBuf;
    private double[] flowRect = {0, 0, 1, 0};

    /**
     * Phase 6: die Strömung als RG16F auf halber Auflösung der Überflutung (Mittel aus 2 × 2 Zellen, 512² zu 32 m bei
     * „Mittel“), nur wenn neu gerechnet (jeder vierte Schritt der Tide).
     */
    private void uploadFlow(GL4 gl) {
        Flood f = flood;
        if (f == null) return;
        int m = Math.max(1, f.nx / 2), mz = Math.max(1, f.nz / 2);
        if (flowTex == 0 || flowTexN != m) {
            Gpu.deleteTextures(gl, flowTex);
            flowTex = Gpu.texture(gl, m, mz, GL4.GL_RG16F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, flowTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            flowTexN = m;
            flowBuf = new short[m * mz * 2];
            flowUploaded = -1;
        }
        int ver = f.currentVersion;
        if (ver == flowUploaded) return;
        int nx = f.nx;
        for (int j = 0; j < mz; j++)
            for (int i = 0; i < m; i++) {
                int k = 2 * j * nx + 2 * i;
                float u = (f.u[k] + f.u[k + 1] + f.u[k + nx] + f.u[k + nx + 1]) * 0.25f, v = (f.v[k] + f.v[k + 1] + f.v[k + nx] + f.v[k + nx + 1]) * 0.25f;
                flowBuf[(j * m + i) * 2] = Float.floatToFloat16(u);
                flowBuf[(j * m + i) * 2 + 1] = Float.floatToFloat16(v);
            }
        gl.glBindTexture(GL.GL_TEXTURE_2D, flowTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, m, mz, GL4.GL_RG, GL.GL_HALF_FLOAT, java.nio.ShortBuffer.wrap(flowBuf));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        flowUploaded = ver;
        flowRect = new double[]{f.x0, f.z0, f.cell * 2, 1};
    }

    /** Phase 6: liegt das Auge unter dem Wasserspiegel am Ort? */
    public boolean underWater() { return camera.eye[1] < camera.waterLevel - 0.02 && camera.waterLevel > camera.groundLevel + 0.05; }

    /** {x0, z0, Zelle, an} der Wassertextur. */
    private double[] floodRect() {
        Flood f = flood;
        return f == null || floodTex == 0 ? new double[]{0, 0, 1, 0} : new double[]{f.x0, f.z0, f.cell, 1};
    }

    /**
     * Belichtung. Mit {@code com.dan.sky}: aus der geglätteten Anpassungsleuchtdichte mit nur teilweiser Anpassung
     * ({@link #adaptation}); im Blitz gibt sie kurz nach. Ohne: aus der Helligkeitstabelle von Phase 1.
     */
    public double exposure() {
        if (skyOk && adaptLum > 0) return Sky.State.exposureFor(adaptLum, adaptation) * Math.pow(2, exposureBias) / flashGive;
        double b = Sun.skyBrightness(clock.sun()[0]);
        return 0.6 * Math.pow(b, -0.85) * Math.pow(2, exposureBias);
    }

    public boolean skyRunning() { return skyOk; }
    public boolean cloudsRunning() { return cloudsOk; }
    public boolean rainRunning() { return rainOk; }
    public boolean fogRunning() { return fogOk; }
    public boolean lightningRunning() { return lightningOk; }
    public double cloudShade() { return cloudShade; }
    public String skyCheck() { return skyCheck; }
    public String cloudCheck() { return cloudCheck; }
    public String lastBolt() { return lastBoltText; }

    /** Der aktuelle Himmelszustand (nie null nach dem ersten Schritt). */
    public Sky.State skyState() {
        Sky.State st = sky.state();
        return st != null ? st : sky.update();
    }

    /** Augenanpassung neu (nach Sprüngen der Uhr oder der Kamera). */
    public void readapt() {
        viewLum = -1;
        adaptLum = -1;
        adapt(skyState(), 1);
    }

    /** Senkrechter Bildwinkel in Grad. */
    public double fovy() { return Camera.FOVY; }

    // ------------------------------------------------------------------ Lebenslauf

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        int major = d.getContext().getGLVersionNumber().getMajor(), minor = d.getContext().getGLVersionNumber().getMinor();
        String renderer = gl.glGetString(GL.GL_RENDERER), vendor = gl.glGetString(GL.GL_VENDOR), version = gl.glGetString(GL.GL_VERSION);
        glInfo = renderer + " · OpenGL " + major + "." + minor;
        System.out.println("Rungholt: " + vendor + " · " + renderer + " · " + version);
        if (major < MIN_MAJOR || (major == MIN_MAJOR && minor < MIN_MINOR)) {
            fail("Die Grafikkarte meldet OpenGL " + major + "." + minor + " (" + renderer + ", Treiber " + version
                    + "). Rungholt braucht OpenGL " + MIN_MAJOR + "." + MIN_MINOR + ".\nBitte den Grafiktreiber aktualisieren.");
            return;
        }
        gl.setSwapInterval(1);
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        reversedZ = (major > 4 || minor >= 5) || gl.isExtensionAvailable("GL_ARB_clip_control");
        if (reversedZ) gl.glClipControl(GL4.GL_LOWER_LEFT, GL4.GL_ZERO_TO_ONE);
        try {
            for (Shader s : all) s.build(gl);
        } catch (RuntimeException e) {
            fail(e.getMessage());
            return;
        }
        int[] v = new int[1];
        gl.glGenVertexArrays(1, v, 0);
        emptyVao = v[0];
        for (int[] s : stamp) gl.glGenQueries(MARKS, s, 0);
        compute = ComputeProbe.run(gl);
        System.out.println("Rungholt: " + compute.line());
        // Phase 2: Gelände, Rundumbild, Maske der Köge, Meer, Halme, Bodendecke (jedes für sich; was nicht startet, fehlt)
        lod.upload(gl, terrain);
        System.out.println("Rungholt: Gelände " + terrain.describe());
        panoTex = Gpu.texture(gl, PANO_W, PANO_H, GL.GL_RGBA16F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        panoFbo = Gpu.fbo(gl, panoTex, 0);
        maskTex = maskTexture(gl, terrain);
        if (compute.ok()) {
            try {
                oceanGl.init(gl);
                oceanOk = true;
                System.out.println("Rungholt: Meer " + oceanGl.info());
            } catch (RuntimeException e) {
                System.err.println("Rungholt: Meer nicht verfügbar: " + e.getMessage());
                errorListener.accept("Das Meer (com.dan.ocean) lässt sich nicht starten.\n" + e.getMessage());
            }
        }
        try {
            reedGl.init(gl);
            reedOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Halme nicht verfügbar: " + e.getMessage());
        }
        try {
            groundGl.init(gl);
            groundOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Bodendecke nicht verfügbar: " + e.getMessage());
        }
        // Phase 3: Himmel, Wolken, Regen, Nebel, Blitz (jedes für sich; was nicht startet, fehlt, der Rest läuft)
        try {
            skyGl.init(gl);
            skyOk = true;
            System.out.println("Rungholt: Himmel " + skyGl.info());
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Himmel nicht verfügbar, Verlauf aus Phase 1: " + e.getMessage());
            errorListener.accept("Der Himmel (com.dan.sky) lässt sich nicht starten, es bleibt ein einfacher Verlauf.\n" + e.getMessage());
        }
        if (skyOk) {
            try {
                cloudGl.init(gl);
                cloudsOk = true;
                if (wiring != null) wiring.cloudsDrawn = true;
                System.out.println("Rungholt: Wolken " + cloudGl.info());
            } catch (RuntimeException e) {
                System.err.println("Rungholt: Wolken nicht verfügbar: " + e.getMessage());
                errorListener.accept("Die Wolken (com.dan.cloud) lassen sich nicht starten, es bleibt der Schleier des Himmels.\n" + e.getMessage());
            }
        }
        try {
            sprayGl.init(gl);   // Phase 17
            sprayOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Gischt nicht verfügbar: " + e.getMessage());
        }
        try {
            rainGl.init(gl);
            try { snowGl.init(gl); snowOk = true; } catch (RuntimeException e) { System.err.println("Rungholt: Schneefall nicht verfügbar: " + e.getMessage()); }
            rainOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Regen nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Regen (com.dan.rain) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            fogGl.init(gl);
            fogOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Nebel nicht verfügbar: " + e.getMessage());
            errorListener.accept("Der Nebel (com.dan.fog) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            lightningGl.init(gl);
            lightningOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Gewitter nicht verfügbar: " + e.getMessage());
            errorListener.accept("Das Gewitter (com.dan.lightning) lässt sich nicht starten.\n" + e.getMessage());
        }
        try {
            sealGl.init(gl);
            sealOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Seehunde nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Seehunde (com.dan.seal) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {
            waderGl.init(gl);
            waderOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Wattvögel nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Wattvögel (com.dan.flock) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {
            figureGl.init(gl);
            figurOk = true;
            timberGl.init(gl, timber);
            timberOk = true;
            teichGl.init(gl, com.dan.rungholt.scene.Suedfall.PONDS);
            teichOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Figuren nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Figuren (com.dan.figure) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {
            buoyGl.init(gl);
            buoyOk = true;
            smokeGl.init(gl);
            smokeOk = true;
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Kogge oder Rauch nicht verfügbar: " + e.getMessage());
            errorListener.accept("Die Kogge (com.dan.buoyancy) oder der Rauch (com.dan.smoke) lassen sich nicht starten.\n" + e.getMessage());
        }
        try {   // Phase 15: der Kinomodus
            com.dan.kino.KinoMaler km = new com.dan.kino.KinoMaler();
            km.kopfFarbe = new java.awt.Color(176, 206, 214);
            kinoGl = new com.dan.kino.gl.KinoGl(kino, km);
            kinoGl.init(gl);
        } catch (RuntimeException e) {
            System.err.println("Rungholt: Kino nicht verfügbar (ohne Balken und Blenden): " + e.getMessage());
            kinoGl = null;
        }
        physik.start();
        welleFaden.start();   // Phase 12: die Welle in der Bresche
        startTide();   // Phase 4
        lastNanos = System.nanoTime();
        statsNanos = lastNanos;
        camera.snap();
        readapt();
        ready = true;
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int w, int h) {
        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
    }

    /** Uhr, Physik und Kamera um dt weiterrechnen (vom Bildlauf aufgerufen; Standbilder rufen es mit festem dt). */
    public void step(double dt) {
        clock.step(dt);
        if (wiring != null) wiring.era.step(dt);
        { double d = magnetik - magBlend, k = Math.max(0, dt) / 1.5; magBlend = Math.abs(d) <= k ? magnetik : magBlend + Math.signum(d) * k; }   // Phase 13
        if (wiring != null) {   // Phase 13: die Glocken und ihr Regiebuch
            glocken.step(Math.max(0, dt) * Math.max(0, clock.lapse), sturmnacht());
        }
        regie.step(dt, regieCtx);   // Phase 15: fällige Stichworte des Regiebuchs (Uhr, Wetter, Ansicht)
        kinoSchritt(dt);
        if (wiring != null) try { klang.feed(Math.max(0, dt)); } catch (RuntimeException e) { klang.note = "!" + e; }   // Phase 14
        if (wiring != null && wiring.era.blend() != lastEraBlend) {
            // Phase 13: wer zu Fuß auf einer Warft stand, steht danach auf ihrer Spur (und umgekehrt): das Auge folgt dem Grund
            double g = groundHeight(camera.fx, camera.fz);
            if (!Double.isNaN(lastEraGround) && camera.mode == com.dan.rungholt.camera.Camera.Mode.FREI && camera.fahrt == null && camera.fy - lastEraGround < 8) {
                camera.fy += g - lastEraGround;
                camera.snap();
            }
            lastEraGround = g;
            lastEraBlend = wiring.era.blend();
        } else lastEraGround = groundHeight(camera.fx, camera.fz);
        physik.advance(dt);
        double level = level();
        long t0 = System.nanoTime();
        // Phase 3: zuerst die Wetterregie, dann bekommen alle Packages ihre Eingänge
        if (wiring != null) {
            if (wiring.regie) wiring.feedWeather(dt);
            wiring.feedTide(dt * Math.max(0, clock.lapse) / 3600);   // Phase 4: Windstau
            wiring.feed(ocean);
            wiring.feed(reed);
            reed.season = wiring.season.reedSeason();
            reed.snowDepth = wiring.season.snowDepth();
            reed.frost = wiring.season.frost();
            wiring.feed(sky, camera.eye[1]);
            wiring.feed(clouds);
        } else {
            ocean.waterLevel = level;
            sky.epochMillis = clock.epochMillis();
        }
        Sky.State st = sky.step(dt);
        clouds.step(dt);
        // Wolkenschatten am Auge (für Belichtung und HUD; Gelände, Meer, Halme und Boden lesen die Schattenkarte)
        cloudShade = cloudsOk && showClouds ? Clouds.shadow(clouds.snapshot(), camera.eye[0], camera.eye[2], keyDir(st)) : 1;
        adapt(st, dt);
        mudCloud = cloudShade * skyDim();
        if (wiring != null) {
            wiring.feed(rain, clouds, clouds.snapshot(), cloudsOk && showClouds, camera.eye.clone());
            wiring.feed(fog, st.sunAlt);
            wiring.feed(lightning, clouds.snapshot(), cloudsOk && showClouds, camera.eye);
        }
        if (!showRain) rain.rate = 0;
        // Phase 9: das Jahr weiter (ein Sprung rechnet den Winter vor); unter 2,5 °C fällt ein Teil als Schnee
        double rainShare = 1;
        if (wiring != null) {
            double now = clock.epochMillis(), sd = Double.isNaN(seasonMillis) ? 1e9 : (now - seasonMillis) / 1000;
            seasonMillis = now;
            wiring.season.step(sd);
            rainShare = com.dan.season.Year.rainFraction(wiring.regie ? wiring.airTempC : wiring.season.temperature());
        }
        snowRate = rain.rate * (1 - rainShare);
        rain.rate *= rainShare;
        rain.step(dt);
        fog.step(dt);
        smoke.step(dt);   // Phase 11: Herdrauch (Zeit der Bälle läuft in Echtzeit wie die Wellen)
        stepSpray(dt);   // Phase 17: Gischt über der Deichkrone
        stepLightning(dt);
        weatherCpuMs = weatherCpuMs * 0.95 + (System.nanoTime() - t0) / 1e6 * 0.05;
        ocean.step(dt);
        camera.groundLevel = groundHeight(camera.eye[0], camera.eye[2]);
        camera.targetGround = groundHeight(camera.tx, camera.tz);
        // Phase 4: der Spiegel am Ort aus der Überflutung (Pfützen, verzögerte Flut im Watt)
        camera.waterLevel = wiring != null ? (wiring.level.depthAt(camera.eye[0], camera.eye[2]) > 0 ? wiring.level.surfaceAt(camera.eye[0], camera.eye[2]) : camera.groundLevel)
                : terrain.seaward(camera.eye[0], camera.eye[2]) ? level : camera.groundLevel;
        camera.step(dt);
        stepAnimals(dt);
        welleFaden.advance(dt);
        stepMandraenke(dt, false);   // Phase 12
    }

    // ------------------------------------------------------------------ Phase 7: Seehunde und Wattwanderer

    /**
     * Seehunde und Wanderer nach der Szenenzeit weiter (Bildlauf). Springt die Uhr um mehr als 30 min (Sprung zum
     * Niedrigwasser, anderer Tag), setzt sich die Kolonie gleich in den neuen Zustand ({@link Colony#settle}); steht
     * die Uhr, laufen die Tiere in Echtzeit weiter (wie die Wellen).
     */
    void stepAnimals(double realDt) {
        stepTown(realDt);   // Phase 11: die Stadt läuft auch ohne Seehunde und Vögel
        stepDig(realDt);    // Phase 13: die Grabung
        if (wiring == null || flood == null || (!showSeals && !showBirds)) return;
        double now = clock.epochMillis();
        double d = Double.isNaN(sealMillis) ? Double.NaN : (now - sealMillis) / 1000;
        sealMillis = now;
        feedColony();
        if (Double.isNaN(d) || d < -1 || d > 1800 || colony.stale() || waders.stale()) { colony.settle(); if (showBirds) waders.settle(); return; }
        stepAnimalsScene(clock.lapse > 0 ? Math.max(0, d) : realDt, false);
    }

    /** Phase 11: die Stadt nach der Szenenzeit weiter (Bildlauf); ein Sprung der Uhr setzt die Kogge an ihren Platz. */
    void stepTown(double realDt) {
        Town t = town;
        if (t == null || wiring == null || !wiring.era.is1362()) { townMillis = Double.NaN; return; }
        double now = clock.epochMillis();
        double d = Double.isNaN(townMillis) ? Double.NaN : (now - townMillis) / 1000;
        townMillis = now;
        if (Double.isNaN(d) || d < -1 || d > 1800) { t.sielhafen.settle(harbourSea, wiring.level.hours()); return; }
        stepTownScene(clock.lapse > 0 ? Math.max(0, d) : realDt);
    }

    private double digMillis = Double.NaN;

    /** Phase 13: die Grabung nach der Szenenzeit (Bildlauf); ein Sprung der Uhr setzt sie gleich. */
    void stepDig(double realDt) {
        if (wiring == null || flood == null || wiring.era.is1362() || mandraenke != null) { digMillis = Double.NaN; return; }
        double now = clock.epochMillis();
        double d = Double.isNaN(digMillis) ? Double.NaN : (now - digMillis) / 1000;
        digMillis = now;
        if (Double.isNaN(d) || d < -1 || d > 1800) { grabung.settle(digWorld); return; }
        double dt = clock.lapse > 0 ? Math.max(0, d) : realDt;
        while (dt > 1e-9) { double h = Math.min(dt, 2); dt -= h; grabung.step(h, digWorld, digSteps); }
    }

    private final com.dan.figure.Hike.Steps digSteps = (x, z, dx, dz, bare, sc) -> { WetField wf = wetField; if (wf != null) wf.addTrack(x, z, dx, dz, 4); };

    /** Phase 11: Kogge und Leute um {@code sceneDt} Sekunden weiter (Bildlauf und Standbilder). */
    public void stepTownScene(double sceneDt) {
        Town t = town;
        if (t == null || wiring == null || sceneDt <= 0) return;
        townMillis = clock.epochMillis();
        t.sielhafen.step(harbourSea, sceneDt, wiring.level.hours(), level(), wiring.level.rising());
        double light = Math.max(0, Math.min(1, (clock.sun()[0] + 4) / 8));
        t.people.step(Math.min(sceneDt, 600), this::groundHeight, light);
    }

    /** Kolonie und Wanderer (und seit Phase 11 die Stadt) um {@code sceneDt} Sekunden weiter (Standbilder). */
    public void stepAnimalsScene(double sceneDt) { stepAnimalsScene(sceneDt, true); }

    private void stepAnimalsScene(double sceneDt, boolean withTown) {
        if (withTown && wiring != null && wiring.era.is1362()) stepTownScene(sceneDt);
        if (wiring == null || flood == null) return;
        feedColony();
        sealMillis = clock.epochMillis();
        double rest = sceneDt;
        while (rest > 1e-9) {
            double h = Math.min(rest, 2.0);
            rest -= h;
            walker.step(h, (x, z) -> wiring.level.depthAt(x, z), (x, z, dx, dz) -> { WetField wf = wetField; if (wf != null) wf.addTrack(x, z, dx, dz, 0); });
            Hike hk = hike;
            if (hk != null) hk.step(h, hikeWorld, (x, z, dx, dz, bare, sc) -> { WetField wf = wetField; if (wf != null) wf.addTrack(x, z, dx, dz, bare ? 3 : 4); });
            if (wiring != null && !wiring.era.is1362() && mandraenke == null) { grabung.step(h, digWorld, digSteps); digMillis = clock.epochMillis(); }   // Phase 13
            colony.disturbers.clear();
            if (walker.active) colony.disturbers.add(new Colony.Disturber(walker.x, walker.z, 1));
            double[] hc = hk == null ? null : hk.centre();
            if (hc != null) colony.disturbers.add(new Colony.Disturber(hc[0], hc[1], 1.5));
            double[] gc = grabung.centre();
            if (gc != null) colony.disturbers.add(new Colony.Disturber(gc[0], gc[1], 1.5));
            boolean boat = boatOn();   // Phase 15: das Boot der Prielfahrt
            if (boat) colony.disturbers.add(new Colony.Disturber(camera.eye[0], camera.eye[2], 1.2));
            colony.step(h);
            if (showBirds) {
                waders.disturbers.clear();
                if (walker.active) waders.disturbers.add(new double[]{walker.x, walker.z, 1});
                if (hc != null) waders.disturbers.add(new double[]{hc[0], hc[1], 1.5});
                if (gc != null) waders.disturbers.add(new double[]{gc[0], gc[1], 1.5});
                if (boat) waders.disturbers.add(new double[]{camera.eye[0], camera.eye[2], 1.2});
                waders.step(h);
            }
        }
    }

    /** Monat, Sonne und Störer an die Kolonie (Verdrahtung Clock, Disturber). */
    private void feedColony() {
        colony.month = clock.date().getMonthValue();
        colony.sunAltDeg = clock.sun()[0];
        waders.month = colony.month;
        waders.sunAltDeg = colony.sunAltDeg;
        if (wiring != null) { waders.windSpeed = wiring.wind.speed(); waders.windFromDeg = wiring.wind.fromDeg(); hwHours = highWaterHours(); }
        colony.disturbers.clear();
        if (walker.active) colony.disturbers.add(new Colony.Disturber(walker.x, walker.z, 1));
    }

    /** Die Kolonie sofort neu setzen (Bedienfeld, Standbilder). */
    public void settleSeals() {
        if (flood == null) return;
        feedColony();
        colony.settle();
        waders.settle();
        sealMillis = clock.epochMillis();
    }

    /** Stunden seit dem nächsten Hochwasser am Rand (Norderhever, wie im HUD; negativ davor); für die Rast der Wattvögel. */
    private double highWaterHours() {
        double h = wiring.level.hours();
        com.dan.tide.Tide.Turn n = wiring.level.tide.next(h, true);
        if (n == null) return Double.NaN;
        com.dan.tide.Tide.Turn p = wiring.level.tide.next(n.hours() - 13.5, true);
        double a = n.hours() - h, b = p == null || p.hours() >= n.hours() ? Double.MAX_VALUE : h - p.hours();
        return a < b ? -a : b;
    }

    /** Phase 8: einen Schwarm nahe der Kamera aufscheuchen (Bedienfeld, Standbilder); null, wenn keiner am Boden ist. */
    public Waders.Group flushBirds() { return flood == null ? null : waders.flush(camera.eye[0], camera.eye[2]); }

    /**
     * Phase 8: die Kamera gegen den Wind vor die nächste Gruppe stellen, die frisst (Alpenstrandläufer bevorzugt),
     * Augenhöhe 0,7 m (wie mit dem Fernglas im Liegen), 4 m vor ihrem Rand. false, wenn keine am Boden ist.
     */
    public boolean placeBirdView() {
        Waders.Group best = null;
        double bd = Double.MAX_VALUE;
        for (Waders.Group g : waders.groups()) {
            if (g.mode != Waders.Mode.FRESSEN) continue;
            double d = Math.hypot(g.cx - camera.eye[0], g.cz - camera.eye[2]) * (g.species.murmur() ? 0.3 : 1);
            if (d < bd) { bd = d; best = g; }
        }
        if (best == null) return false;
        double dist = 4 + best.radius(), ang = Math.toRadians(waders.windFromDeg + 180);
        double x = best.cx - Math.sin(ang) * dist, z = best.cz + Math.cos(ang) * dist;
        camera.stopFahrt();
        camera.diving = false;
        camera.place(x, groundHeight(x, z) + 0.7, z, Math.toDegrees(Math.atan2(best.cx - x, -(best.cz - z))), 3);
        return true;
    }

    /**
     * Phase 8, für Standbilder und das Bedienfeld: der Blick folgt der nächsten Gruppe in der Luft (sonst der nächsten am
     * Boden). Ist sie weiter als {@code maxDist} weg, rückt die Kamera auf diese Entfernung heran (Auge 1,6 m über dem
     * Grund, nie unter dem Wasser). false, wenn es keine Vögel gibt.
     */
    public boolean aimAtBirds(double maxDist) {
        Waders.Group best = null;
        double bd = Double.MAX_VALUE;
        for (Waders.Group g : waders.groups()) {
            boolean air = g.mode == Waders.Mode.FLIEGEN || g.mode == Waders.Mode.SCHWARM;
            double[] m = birdMean(g);
            double d = Math.hypot(m[0] - camera.eye[0], m[2] - camera.eye[2]) * (air ? 0.2 : 1);
            if (d < bd) { bd = d; best = g; }
        }
        if (best == null) return false;
        double[] m = birdMean(best);
        double x = camera.eye[0], z = camera.eye[2], y = camera.eye[1];
        double dx = m[0] - x, dz = m[2] - z, dh = Math.hypot(dx, dz);
        if (dh > maxDist) {
            x = m[0] - dx / dh * maxDist; z = m[2] - dz / dh * maxDist;
            double gy = groundHeight(x, z), wl = wiring != null && wiring.level.depthAt(x, z) > 0 ? wiring.level.surfaceAt(x, z) : gy;
            y = Math.max(gy, wl) + 1.6;
            dx = m[0] - x; dz = m[2] - z; dh = Math.hypot(dx, dz);
        }
        camera.stopFahrt();
        camera.diving = false;
        camera.place(x, y, z, Math.toDegrees(Math.atan2(dx, -dz)), -Math.toDegrees(Math.atan2(m[1] - y, Math.max(1, dh))));
        return true;
    }

    private static double[] birdMean(Waders.Group g) {
        double x = 0, y = 0, z = 0;
        for (Waders.Bird b : g.birds) { x += b.x; y += b.y; z += b.z; }
        int n = Math.max(1, g.birds.size());
        return new double[]{x / n, y / n, z / n};
    }

    /** „Wattvögel 1 290 in 11 Gruppen: …“ */
    public String birdLine() {
        if (!showBirds) return "Wattvögel: ausgeblendet";
        if (flood == null) return "Wattvögel: warten auf die Überflutung";
        return waders.describe() + (waderOk ? " · " + waderGl.info() : " · !nicht gezeichnet");
    }

    /** Wattwanderer zur Bank schicken: Ziel ist die Mitte der liegenden Tiere (sonst die Bank). */
    /**
     * Phase 9: die geführte Wattwanderung nach Südfall starten (Kleidung nach dem Monat); die Gruppe steht am Deichfuß
     * bei Fuhlehörn. true, wenn der Weg vor ihr trocken ist.
     */
    public boolean startHike() {
        Hike h = Wattwanderung.create(clock.date().getMonthValue(), 1362 + clock.date().getDayOfYear());
        for (Person p : h.people()) p.y = groundHeight(p.x, p.z);
        hike = h;
        double[] a = h.at(200, 0);
        return wiring == null || wiring.level.depthAt(a[0], a[1]) < h.maxWade;
    }

    /** Die Wanderung beenden (die Gruppe verschwindet). */
    public void stopHike() { hike = null; camera.stopMitfahrt(); }

    /**
     * Fahrt „Wattwanderung“: die Kamera geht 8 m hinter dem Letzten und 9 m rechts neben dem Weg mit, Auge 1,7 m über
     * dem Grund, Blick auf die Mitte der Gruppe (mit der Maus zur Seite). Startet die Wanderung, wenn keine unterwegs ist.
     */
    public boolean followHike() {
        if (hike == null && !startHike()) return false;
        Hike h = hike;
        camera.startMitfahrt(() -> {
            Hike hh = hike;
            if (hh == null) return new double[]{camera.eye[0], camera.eye[1], camera.eye[2], 0};
            // seitlich hinter der Gruppe, Blick auf ihre Mitte (am Halt von vorn auf den Halbkreis)
            double tail = Double.MAX_VALUE;
            for (Person p : hh.people()) tail = Math.min(tail, projected(hh, p));
            double[] c = hh.centre();
            double[] q = hh.at(Math.max(0, tail - 8), 9);
            return new double[]{q[0], groundHeight(q[0], q[1]) + 1.7, q[1], Math.toDegrees(Math.atan2(c[0] - q[0], -(c[1] - q[1])))};
        });
        return h != null;
    }

    /** Wo auf dem Weg die Person ungefähr ist (Strecke ab Start; sucht nur um die Strecke des Führers). */
    private static double projected(Hike h, Person p) {
        double best = h.s, bd = Double.MAX_VALUE;
        for (double s = Math.max(0, h.s - 120); s <= h.s + 5; s += 3) {
            double[] q = h.at(s, 0);
            double d = Math.hypot(q[0] - p.x, q[1] - p.z);
            if (d < bd) { bd = d; best = s; }
        }
        return best;
    }

    /**
     * Phase 11: die Stadt von 1362 im Durchgang „Leben“: Häuser und Kirche aus {@code com.dan.timber} (die Kirche als
     * eigenes Netz, Kaje und Boote als Bauwerke), Leute aus {@code com.dan.figure}, Kogge und Vieh mit
     * {@code com.dan.buoyancy}.
     */
    private void drawTown(GL4 gl, Town tw, float[] vp, SealGl.Light al, double[] sun) {
        double eb = wiring.era.blend();
        boolean full = eb >= 0.999;
        if (townOnGpu != tw) {
            timberGl.setSpecial(gl, tw.church, tw.churchMesh, 30_000);
            timberGl.setStructures(gl, null, 0);
            harbourOnGpu = false;
            townOnGpu = tw;
        }
        boolean harbourWanted = eb >= 0.6;   // Kaje und Boote erst, wenn das Vorland steht
        if (harbourWanted != harbourOnGpu) { timberGl.setStructures(gl, harbourWanted ? tw.harbour : null, 0.45); harbourOnGpu = harbourWanted; }
        com.dan.timber.gl.TimberGl.View tv = new com.dan.timber.gl.TimberGl.View();
        tv.viewProj = vp;
        tv.eye = camera.eye.clone();
        tv.forward = camera.fwd.clone();
        tv.fovyDeg = fovy();
        tv.aspect = hdrW / (double) Math.max(1, hdrH);
        tv.reversedZ = reversedZ;
        com.dan.timber.gl.TimberGl.Light tl = new com.dan.timber.gl.TimberGl.Light();
        tl.sunDir = al.sunDir; tl.sunColor = scale(al.sunColor, al.sunShadow); tl.moonDir = al.moonDir; tl.moonColor = al.moonColor;
        tl.skyColor = al.skyTop; tl.fogColor = al.skyHorizon; tl.fogDistance = al.fogDistance;
        tl.wetness = Math.min(1, rain.rate / 3);
        com.dan.season.Year.State ys = wiring.season.state();
        if (ys != null) { tl.roofSnow = ys.roofSnow(); tl.groundSnow = ys.snowDepth() * ys.snowCover(); tl.snowWet = ys.snowWet(); tl.frost = ys.frost(); }
        tl.waterLevel = level();
        com.dan.rungholt.scene.Mandraenke md = mandraenke;
        java.util.List<com.dan.timber.House> hs = tw.all;
        if (md != null) { hs = new java.util.ArrayList<>(md.houses()); hs.add(tw.church); }   // Phase 12: Häuser in der Flut
        else if (!full) hs = risingHouses(gl, tw, eb);                                          // Phase 13: die Häuser steigen auf
        else if (churchRaised) { timberGl.moveSpecial(gl, tw.church); churchRaised = false; }
        timberGl.draw(gl, timber, hs, tv, tl);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        // Kogge und Vieh
        if (buoyOk) {
            com.dan.buoyancy.gl.BuoyancyGl.View bv = new com.dan.buoyancy.gl.BuoyancyGl.View();
            bv.viewProj = vp;
            bv.eye = camera.eye.clone();
            bv.reversedZ = reversedZ;
            com.dan.buoyancy.gl.BuoyancyGl.Light bl = new com.dan.buoyancy.gl.BuoyancyGl.Light();
            bl.sunDir = al.sunDir; bl.sunColor = scale(al.sunColor, al.sunShadow); bl.moonDir = al.moonDir; bl.moonColor = al.moonColor;
            bl.skyTop = al.skyTop; bl.skyHorizon = al.skyHorizon; bl.fogDistance = al.fogDistance;
            com.dan.buoyancy.Cog cog = tw.sielhafen.cog;
            int k = cog.sail < 0.2 ? 0 : cog.sail < 0.75 ? 1 : 2;
            if (cogMeshes[k] == null) cogMeshes[k] = com.dan.buoyancy.Mesh.cog(cog, k * 0.5);
            java.util.List<Object[]> items = new java.util.ArrayList<>();
            if (Math.hypot(cog.x - camera.eye[0], cog.z - camera.eye[2]) < 12_000) items.add(new Object[]{cogMeshes[k], cog.model()});
            boolean drowned = md != null && md.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal();
            if (Math.hypot(camera.eye[0], camera.eye[2]) < 6000 && !drowned && eb >= 0.9) items.add(new Object[]{tw.herd, null});   // Phase 13: das Vieh zuletzt
            if (md != null) addBreachMeshes(items, md);   // Phase 12: der Abschnitt aus Klei und seine Stücke
            if (eb >= 0.95 && showRoads) { com.dan.buoyancy.Mesh rm = roadMesh(); if (rm != null) items.add(new Object[]{rm, null}); }   // Phase 13: die Wege aus com.dan.road
            for (com.dan.buoyancy.Mesh old : pendingForget) if (old != null) buoyGl.forget(gl, old);
            pendingForget.clear();
            buoyGl.drawMeshes(gl, bv, bl, items, true);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        }
        // die Leute (tagsüber draußen)
        if (figurOk && tw.people.visible && eb >= 0.9) {
            java.util.List<Person> near = new java.util.ArrayList<>();
            for (Person p : tw.people.people) if (Math.hypot(p.x - camera.eye[0], p.z - camera.eye[2]) < 1500) near.add(p);
            if (!near.isEmpty()) {
                FigureGl.View fv = new FigureGl.View();
                fv.viewProj = vp;
                fv.eye = camera.eye.clone();
                FigureGl.Light fl = new FigureGl.Light();
                fl.sunDir = al.sunDir; fl.sunColor = al.sunColor; fl.moonDir = al.moonDir; fl.moonColor = al.moonColor;
                fl.skyTop = al.skyTop; fl.skyHorizon = al.skyHorizon; fl.fogDistance = al.fogDistance; fl.sunShadow = al.sunShadow;
                figureGl.draw(gl, near, fv, fl);
            }
        }
    }

    /** Phase 13: das Gerät der Grabung (Rahmen, Eimer, Sieb, Stab), solange sie da ist; neu gebaut, wenn sich der Grund ändert. */
    private void drawDig(GL4 gl, float[] vp, SealGl.Light al) {
        double key = (showSpuren ? 1 : 0) + (relief.on ? 2 : 0) + (wiring == null ? 0 : wiring.era.blend());
        if (digMesh == null || key != digMeshEra) {
            if (digMesh != null) pendingForget.add(digMesh);
            digMesh = com.dan.rungholt.scene.Grabung.equipment(this::groundHeight);
            digMeshEra = key;
        }
        com.dan.buoyancy.gl.BuoyancyGl.View bv = new com.dan.buoyancy.gl.BuoyancyGl.View();
        bv.viewProj = vp;
        bv.eye = camera.eye.clone();
        bv.reversedZ = reversedZ;
        com.dan.buoyancy.gl.BuoyancyGl.Light bl = new com.dan.buoyancy.gl.BuoyancyGl.Light();
        bl.sunDir = al.sunDir; bl.sunColor = scale(al.sunColor, al.sunShadow); bl.moonDir = al.moonDir; bl.moonColor = al.moonColor;
        bl.skyTop = al.skyTop; bl.skyHorizon = al.skyHorizon; bl.fogDistance = al.fogDistance;
        for (com.dan.buoyancy.Mesh old : pendingForget) if (old != null) buoyGl.forget(gl, old);
        pendingForget.clear();
        java.util.List<Object[]> items = new java.util.ArrayList<>();
        items.add(new Object[]{digMesh, null});
        buoyGl.drawMeshes(gl, bv, bl, items, true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
    }

    private boolean harbourOnGpu, churchRaised;

    // ------------------------------------------------------------------ Phase 13: die Glocken (ohne Ton) und ihr Regiebuch
    public final com.dan.rungholt.scene.Glocken glocken = new com.dan.rungholt.scene.Glocken();
    /** Phase 14: der Klang (Ohr = Kamera; Ton an im Bedienfeld). */
    public final Klang klang = new Klang(this);

    // ================================================================== Phase 15: Regiebücher, Prielfahrt, Kino

    /** Phase 15: die Regiebücher (Text mit Blatt am Ende), ihre Fotos und was sie sagen. */
    public final com.dan.rungholt.scene.Regie regie = new com.dan.rungholt.scene.Regie();
    private final RegieCtx regieCtx = new RegieCtx();
    /**
     * Phase 15: der Kinomodus (com.dan.kino). Ein Regiebuch im Kino: ohne Bedienfeld und HUD, Balken 2,39 : 1, Vor- und
     * Abspann, Untertitel aus den Stichworten, Überblendung bei jedem Schnitt, ruhiger Schwenk, Filmkorn, der Ton folgt
     * dem Schwarz.
     */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    private com.dan.kino.gl.KinoGl kinoGl;
    private final java.util.List<Runnable> kinoSchnitte = new java.util.ArrayList<>();
    private double kinoYaw, kinoTonBasis = Double.NaN;
    private boolean kinoFensterAn;
    private com.dan.rungholt.scene.Regie.Book kinoBuch;
    /** true, wenn das Kino beginnt (Bedienfeld weg, Vollbild), false, wenn es zu Ende ist; im Bildlauf gerufen. */
    public volatile Consumer<Boolean> kinoFenster = an -> { };
    private volatile String captureCaption, sheetName, regieMsg = "";
    private final java.util.List<BufferedImage> album = new java.util.ArrayList<>();
    private final java.util.List<String> albumCaps = new java.util.ArrayList<>();
    private double albumFps, albumGpu;
    private int albumSamples;
    /** Das zuletzt geschriebene Blatt (für Tempo und die Prüfung); null: noch keins. */
    public volatile File lastSheet;
    /** Ohne Blatt (Prüfung). */
    public volatile boolean ohneBlatt;
    /** Der Weg der Prielfahrt (einmal gesucht). */
    private double[][] prielWeg;

    /** Ein Regiebuch von vorn (das Album beginnt leer). */
    public void regieStart(com.dan.rungholt.scene.Regie.Book b) {
        album.clear();
        albumCaps.clear();
        albumFps = albumGpu = 0;
        albumSamples = 0;
        regieMsg = "";
        frozen = false;
        regie.start(b, regieCtx);
    }

    public void regieStop() {
        regie.stop();
        if (kino.laeuft()) kino.abbrechen();
    }

    /** Ein Regiebuch im Kino (Vorspann mit Titel und Beschreibung, am Ende der Abspann). */
    public void kinoStart(com.dan.rungholt.scene.Regie.Book b) {
        frozen = false;
        regie.beiText = kino::untertitel;
        kinoBuch = b;
        kino.beginnen("Rungholt · Zweimal am Tag Land", b.name(), b.about());
        regieStart(b);
    }

    /** Das Kino verlassen (Esc); das Regiebuch läuft im Fenster weiter. */
    public void kinoVerlassen() {
        frozen = false;
        kino.abbrechen();
    }

    /** Die Zeilen des Abspanns (erste Zeile groß). */
    java.util.List<String> abspann(com.dan.rungholt.scene.Regie.Book b) {
        java.util.List<String> z = new java.util.ArrayList<>();
        z.add(b != null ? b.name() : "Rungholt");
        z.add("Ort: das Nordfriesische Wattenmeer zwischen Pellworm, Südfall und Nordstrand");
        z.add("Szene: " + clock.dateLabel());
        z.add("Gelände: Küstenhöhen der BAW 2015–2021 (10 m)");
        z.add("Rungholt: nach den Funden von Andreas Busch und den Messungen von 2023, gestaltet");
        z.add("Tide, Watt, Seehunde, Vögel, Himmel, Wasser und Klang: com.dan");
        if (!album.isEmpty()) z.add("Fotos: " + album.size() + " auf dem Blatt in docs/status");
        z.add("Rungholt · Zweimal am Tag Land · " + VERSION);
        return z;
    }

    /** Ein Schnitt (Ansicht, Fahrt). Im Kino wartet er auf das Merken des Bilds für die Überblendung. */
    private void schnitt(Runnable r) {
        if (kino.schnitt()) kinoSchnitte.add(r);
        else { r.run(); kinoYaw = 0; }
    }

    /** Die wartenden Schnitte ausführen (nach dem Merken oder ohne Kino). */
    private void schnitteJetzt() {
        if (kinoSchnitte.isEmpty()) return;
        java.util.List<Runnable> l = new java.util.ArrayList<>(kinoSchnitte);
        kinoSchnitte.clear();
        for (Runnable x : l) x.run();
        kinoYaw = 0;
    }

    /** Kino weiter, Abspann am Ende des Regiebuchs, Schwenk und Ton. */
    private void kinoSchritt(double dt) {
        com.dan.kino.Kino.Lage l = kino.lage();
        if ((l == com.dan.kino.Kino.Lage.VORSPANN || l == com.dan.kino.Kino.Lage.FILM) && !regie.running() && !regieWantsFrame())
            kino.beenden(abspann(kinoBuch));
        kino.schritt(l == com.dan.kino.Kino.Lage.VORSPANN && regie.waiting() != null ? 0 : dt);   // wartet das Regiebuch im Vorspann, bleibt die Titeltafel
        if (!kino.laeuft() && !kinoSchnitte.isEmpty() && !kino.schnittFaellig()) schnitteJetzt();
        // der Schwenk: nur bei stehender Kamera, als Änderung der Blickrichtung
        double s = kino.schwenk();
        if (camera.fahrt == null && camera.mitfahrt == null && kinoSchnitte.isEmpty()) {
            double d = s - kinoYaw;
            if (camera.mode == Camera.Mode.FREI) camera.fyaw += d; else camera.yaw += d;
        }
        kinoYaw = s;
        // der Ton folgt dem Schwarz
        com.dan.soundscape.Soundscape ss = klang.s;
        if (kino.sichtbar()) {
            if (Double.isNaN(kinoTonBasis)) kinoTonBasis = ss.volume;
            ss.volume = kinoTonBasis * kino.ton();
        } else if (!Double.isNaN(kinoTonBasis)) { ss.volume = kinoTonBasis; kinoTonBasis = Double.NaN; }
    }

    /** Ohne Bild (Prüfung): was der Bildlauf nach dem Zeichnen tut – Schnitte ausführen, Fotos und Blatt verwerfen. */
    public void bildlos() {
        if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        captureCaption = null;
        sheetName = null;
    }

    /** Ob ein Foto oder das Blatt auf das nächste Bild wartet (Tempo zeichnet dann sofort). */
    public boolean regieWantsFrame() { return captureCaption != null || sheetName != null || kino.schnittFaellig(); }

    /** „Regie: Eine Tide · 9:02: im Priel läuft die Ebbe · 1:10 von 5:40 · 3 Fotos“ (HUD). */
    public String regieLine() {
        return "Regie: " + regie.describe() + (album.isEmpty() ? "" : " · " + album.size() + " Fotos") + (regieMsg.isEmpty() ? "" : " · " + regieMsg);
    }

    /** Die drei teuersten Posten der Grafikkarte jetzt: „Gelände 3,1 · Wasser 1,4 · Regen 1,1 ms“. */
    public String topPasses(int n) {
        double[] t = passMs.clone();
        StringBuilder b = new StringBuilder();
        for (int k = 0; k < n; k++) {
            int best = -1;
            for (int i = 0; i < t.length && i < PASSES.length; i++) if (best < 0 || t[i] > t[best]) best = i;
            if (best < 0 || t[best] <= 0) break;
            b.append(k > 0 ? " · " : "").append(PASSES[best]).append(' ').append(String.format(Locale.GERMAN, "%.1f", t[best]));
            t[best] = -1;
        }
        return b.append(" ms").toString();
    }

    /** Eine Meldung für sechs Sekunden im HUD. */
    public void hudMeldung(String text) {
        photoMsg = text;
        photoMsgUntil = System.currentTimeMillis() + 6000;
    }

    /**
     * Phase 15: die Fahrt „Prielfahrt“ (F20 A): mit dem Boot bei Flut den Priel hinauf, 1,6 m über dem Wasser, wie es beim
     * Start steht; der Weg aus {@link com.dan.rungholt.scene.Prielfahrt}. null ohne Überflutung oder ohne Rinne.
     */
    public com.dan.rungholt.camera.Fahrt prielFahrt() {
        Flood f = floods[0];
        if (f == null) return null;
        if (prielWeg == null) prielWeg = com.dan.rungholt.scene.Prielfahrt.weg(f, com.dan.rungholt.scene.Prielfahrt.START_X, com.dan.rungholt.scene.Prielfahrt.START_Z,
                com.dan.rungholt.scene.Prielfahrt.END_X, com.dan.rungholt.scene.Prielfahrt.END_Z);
        double[][] w = prielWeg;
        if (w == null) return null;
        double[][] pts = new double[w.length][];
        for (int i = 0; i < w.length; i++) {
            double x = w[i][0], z = w[i][1];
            double wl = wiring != null && wiring.level.depthAt(x, z) > 0 ? wiring.level.surfaceAt(x, z) : level();
            pts[i] = new double[]{x, Math.max(wl, groundHeight(x, z) + 0.3) + com.dan.rungholt.scene.Prielfahrt.AUGE, z};
        }
        com.dan.rungholt.camera.Fahrt fa = new com.dan.rungholt.camera.Fahrt("Prielfahrt", pts, 1, 0, 3);
        fa.duration = fa.length / com.dan.rungholt.scene.Prielfahrt.TEMPO;
        return fa;
    }

    /** Phase 15: die Prielfahrt beginnen (heute); false ohne Weg. */
    public boolean startPrielfahrt() {
        if (wiring != null) { wiring.era.target = 0; wiring.era.blend = 0; }
        com.dan.rungholt.camera.Fahrt f = prielFahrt();
        if (f == null) return false;
        camera.stopMitfahrt();
        camera.diving = false;
        camera.startFahrt(f);
        return true;
    }

    /** Ob gerade das Boot der Prielfahrt fährt (Störer für Seehunde und Vögel). */
    public boolean boatOn() {
        com.dan.rungholt.camera.Fahrt f = camera.fahrt;
        return f != null && f.running && "Prielfahrt".equals(f.name);
    }

    /** Was die Regiebücher in der App tun. */
    private final class RegieCtx implements com.dan.rungholt.scene.Regie.Ctx {
        @Override public void time(java.time.LocalDate date, double hour) { clock.set(date, hour); readapt(); }
        @Override public void lapse(double x) { clock.lapse = x; }
        @Override public void weather(com.dan.weather.Scenario s) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.resumeRegie();
            wiring.start(s);
        }
        @Override public void weatherFree(com.dan.weather.Weather.Phase p, double bft, double fromDeg) {
            if (wiring == null) return;
            wiring.regie = true;
            wiring.resumeRegie();
            wiring.startFree(p);
            if (!Double.isNaN(bft)) wiring.hand(com.dan.weather.Weather.Var.WIND, com.dan.rungholt.wiring.Beaufort.mid((int) Math.round(bft)));
            if (!Double.isNaN(fromDeg)) wiring.hand(com.dan.weather.Weather.Var.DIRECTION, fromDeg);
        }
        @Override public void sturm(boolean on) { glockenBuehne.sturm(on); }
        @Override public void view(String name) {
            schnitt(() -> {
                camera.stopFahrt();
                camera.stopMitfahrt();
                if (!placeNamed(name)) regieMsg = "Ansicht „" + name + "“ gibt es nicht";
                readapt();
            });
        }
        @Override public void fahrt(String name) {
            schnitt(() -> {
                String k = name.toLowerCase(Locale.ROOT);
                if (k.contains("priel")) { if (!startPrielfahrt()) regieMsg = "die Prielfahrt hat keinen Weg (Überflutung fehlt)"; }
                else if (k.contains("warften")) startChainFlight();
                else regieMsg = "Fahrt „" + name + "“ gibt es nicht";
            });
        }
        @Override public void aera(double target) { if (wiring != null) wiring.era.target = target; }
        @Override public void mandraenke(com.dan.rungholt.scene.Mandraenke.Stelle stelle) {
            // im Bildlauf: baut beim ersten Mal das Land von 1362 (einige Sekunden; im Kino steht solange die Titeltafel)
            manualMandraenke = false;
            if (!startMandraenke(stelle)) regieMsg = "die Mandränke startet nicht (ohne Höhendaten?)";
        }
        @Override public void morgen() { jumpAfterFlood(); }
        @Override public void glocken() { glocken.force(); }
        @Override public void grabung() { grabung.settle(digWorld); }
        @Override public void magnetik(boolean on) { magnetik = on ? 1 : 0; showFinds = on; }
        @Override public boolean ready(com.dan.rungholt.scene.Regie.Warte w) {
            com.dan.rungholt.scene.Mandraenke m = mandraenke;
            int st = m == null ? -1 : m.stage.ordinal();
            return switch (w.was()) {
                case "uhr" -> clock.epochMillis() / 3_600_000.0 >= w.hours();
                case "mandraenke" -> m != null;
                case "sturm" -> st >= com.dan.rungholt.scene.Mandraenke.Stage.STURM.ordinal();
                case "ueberlauf" -> st >= com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF.ordinal();
                case "bruch" -> st >= com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal();
                case "flut" -> st >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal();
                default -> true;
            };
        }
        @Override public void capture(String caption) { captureCaption = caption; }
        @Override public void sheet(String name) { sheetName = name; }
        @Override public void say(String text) { regieMsg = text; kino.untertitel(text); }
    }

    /**
     * Phase 15: eine Ansicht nach Namen: die Tasten 1 bis 9, dazu die Blicke des Regiebuchs „Die Glocken“
     * (Fuhlehörn, Kirche im Sturm, Grabung, Draufsicht, Kirchwarft) und „Bruchstelle“ (der Deich neben dem Bruch).
     */
    public boolean placeNamed(String name) {
        com.dan.rungholt.camera.Ansichten.Ansicht a = com.dan.rungholt.camera.Ansichten.byName(name);
        if (a != null) return placeView(a);
        String k = name.toLowerCase(Locale.ROOT).replace("ö", "oe").replace(" ", "");
        for (com.dan.rungholt.scene.Glocken.Blick b : com.dan.rungholt.scene.Glocken.Blick.values())
            if (b.name().toLowerCase(Locale.ROOT).replace("_", "").equals(k)) { glockenView(b); return true; }
        if (k.startsWith("bruch")) { placeBreachView(); return true; }
        return false;
    }

    /** Die Fotos des Regiebuchs als ein Blatt nach docs/status (zwei Spalten, Unterschrift in zwei Zeilen; aus Glacier). */
    private void writeSheet(String name) {
        if (album.isEmpty() || ohneBlatt) return;
        try {
            int n = album.size(), cols = 2, rows = (n + 1) / 2, cw = 960, ch = (int) Math.round(album.get(0).getHeight() * cw / (double) album.get(0).getWidth()), cap = 50, head = 44;
            BufferedImage out = new BufferedImage(cols * cw, head + rows * (ch + cap), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(new java.awt.Color(11, 18, 25));
            g.fillRect(0, 0, out.getWidth(), out.getHeight());
            g.setColor(new java.awt.Color(230, 236, 240));
            g.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 20));
            g.drawString(String.format(Locale.GERMAN, "Rungholt · Regiebuch „%s“ · %s · %s · Ø %.0f Bilder/s · Grafikkarte Ø %.2f ms · %s", name, clock.dateLabel(), quality.label,
                    albumFps / Math.max(1, albumSamples), albumGpu / Math.max(1, albumSamples), glInfo.replaceAll(" ·.*", "")), 14, 29);
            g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 16));
            for (int i = 0; i < n; i++) {
                int cx = (i % cols) * cw, cy = head + (i / cols) * (ch + cap);
                g.drawImage(album.get(i), cx, cy, cw, ch, null);
                g.setColor(new java.awt.Color(143, 162, 174));
                String[] ln = albumCaps.get(i).split("\n");
                for (int k = 0; k < ln.length; k++) g.drawString(ln[k], cx + 10, cy + ch + 21 + 20 * k);
            }
            g.dispose();
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            String fn = "Regiebuch_" + name.replace("ü", "ue").replace("ö", "oe").replace("ä", "ae").replace("ß", "ss").replace(' ', '_') + "_"
                    + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
            File f = new File(dir, fn);
            javax.imageio.ImageIO.write(out, "png", f);
            photoMsg = "Regiebuch gespeichert: " + f.getPath();
            lastSheet = f;
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Blatt des Regiebuchs fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 6000;
    }

    /** Schnitt mit gemerktem Bild, dann Balken, Schwarz, Untertitel; Fenster auf und zu. */
    private void kinoBild(GL4 gl, long now) {
        if (kinoGl != null && kinoGl.bereit()) {
            try {
                if (kino.schnittFaellig()) {
                    kinoGl.merken(gl, defaultFbo, surfW, surfH);
                    schnitteJetzt();
                    kino.geschnitten();
                }
                kinoGl.zeichnen(gl, defaultFbo, surfW, surfH, now / 1e9);
            } catch (RuntimeException e) {
                System.err.println("Rungholt: Kino: " + e);
                kinoGl = null;
            }
        } else if (kino.schnittFaellig()) { schnitteJetzt(); kino.geschnitten(); }
        boolean an = kino.laeuft();
        if (an != kinoFensterAn) {
            kinoFensterAn = an;
            try { kinoFenster.accept(an); } catch (RuntimeException e) { System.err.println("Rungholt: Kinofenster: " + e); }
        }
        if (kino.endeMelden()) hudMeldung("Kino zu Ende" + (lastSheet != null ? " · Blatt: " + lastSheet.getName() : ""));
    }
    private com.dan.timber.House ghostHouse;
    private com.dan.timber.Mesh ghostMesh;

    /** Die Bühne für das Regiebuch „Die Glocken“: Uhr, Wetter, Kamera, Magnetik, Ära, Grabung. */
    public final com.dan.rungholt.scene.Glocken.Buehne glockenBuehne = new com.dan.rungholt.scene.Glocken.Buehne() {
        @Override public void zeit(java.time.LocalDate day, double hour) { clock.set(day, hour); readapt(); }
        @Override public double stunde() { return wiring == null ? clock.epochMillis() / 3_600_000.0 : wiring.level.hours(); }
        @Override public void zeitraffer(double lapse) { clock.lapse = lapse; }
        @Override public void sturm(boolean an) {
            if (wiring == null) return;
            wiring.regie = true;
            if (an) {
                wiring.startFree(com.dan.weather.Weather.Phase.STURM);
                wiring.hand(com.dan.weather.Weather.Var.WIND, 23);        // Bft 9 [G]
                wiring.hand(com.dan.weather.Weather.Var.DIRECTION, 275);  // aus West
                wiring.hand(com.dan.weather.Weather.Var.RAIN, 3);
                wiring.feedTide(7);   // der Sturm weht seit dem Morgen: der Windstau steht schon (Zeitkonstante 3 h)
            } else {
                wiring.resumeRegie();
                wiring.startFree(com.dan.weather.Weather.Phase.ABKLINGEND);
            }
        }
        @Override public void kamera(com.dan.rungholt.scene.Glocken.Blick b) { glockenView(b); }
        @Override public void magnetik(boolean an) { magnetik = an ? 1 : 0; showFinds = an; }
        @Override public void aera(double ziel) { if (wiring != null) wiring.era.target = ziel; }
        @Override public void grabungSetzen() { grabung.settle(digWorld); }
        @Override public double hochwasser(double nach) { com.dan.tide.Tide.Turn t = wiring.level.tide.next(nach, true); return t == null ? nach + 1 : t.hours(); }
        @Override public double niedrigwasser(double nach) { com.dan.tide.Tide.Turn t = wiring.level.tide.next(nach, false); return t == null ? nach + 1 : t.hours(); }
    };

    /** Die Blicke des Regiebuchs „Die Glocken“ [G]. */
    public void glockenView(com.dan.rungholt.scene.Glocken.Blick b) {
        camera.stopFahrt();
        camera.stopMitfahrt();
        camera.diving = false;
        switch (b) {
            case FUHLEHOERN -> camera.place(4660, groundHeight(4660, -1404) + 1.7, -1404, 253, 1);
            case KIRCHE_IM_STURM -> {   // von Süden, quer auf das Schiff, 270 m
                double x = 60, z = 262, w = wiring == null ? level() : wiring.level.surfaceAt(x, z);
                camera.place(x, Math.max(groundHeight(x, z), Double.isNaN(w) ? level() : w) + 6, z, 347, -1);
            }
            case GRABUNG -> camera.place(4, groundHeight(4, 18) + 3, 18, 347, 6);   // von Süden über das Fundament, die Sonne im Rücken
            case DRAUFSICHT -> placeSurveyView(true);
            case KIRCHWARFT -> camera.place(46.5, groundHeight(46.5, 23.3) + 1.7, 23.3, 297, -3);   // auf der Warft, 52 m östlich der Kirche
        }
    }

    /** Phase 13: die Bedingung der Sage jetzt (heute, Nacht, Sturm aus West, Wasser über der Kirchwarft). */
    public boolean sturmnacht() {
        if (wiring == null) return false;
        double over = wiring.level.depthAt(0, 0);
        return com.dan.rungholt.scene.Glocken.sturmnacht(!wiring.era.is1362(), clock.sun()[0], wiring.wind.speed(), wiring.wind.fromDeg(), over);
    }

    /** „Spuren: … · Grabung … · Magnetik …“ für das HUD (Phase 13). */
    public String spurenLine() {
        com.dan.rungholt.scene.Spuren sp = spuren;
        String s = sp == null ? "Spuren: werden gebaut" : !showSpuren ? "Spuren: ausgeblendet" : sp.describe();
        s += " · " + grabung.describe();
        if (magBlend > 0.01) s += String.format(Locale.GERMAN, " · Magnetik %.0f %% [G]", magBlend * 100);
        if (wiring != null && wiring.era.blend() > 0 && wiring.era.blend() < 1) s += " · " + roadInfo;
        return s;
    }

    /** „Glocken: Sturmnacht …“ für das HUD. */
    public String glockenLine() { return glocken.describe() + String.format(Locale.GERMAN, " · Wasser über der Kirchwarft %.1f m", wiring == null ? 0 : wiring.level.depthAt(0, 0)); }

    /**
     * Der Schemen der Kirche über dem Wasser, solange die Glocken läuten: das Netz der Kirche aus {@code timber}, halb
     * durchsichtig (konstantes Alpha), fahl im Licht der Nacht, leicht wogend; der Fuß 0,2 m über dem Wasserspiegel an
     * der Kirche. Nach dem Meer gezeichnet.
     */
    private void drawGhost(GL4 gl, float[] vp, double[][] L, double fogDist) {
        double a = glocken.ghost;
        if (a < 0.01 || !timberOk || wiring == null || wiring.era.is1362()) return;
        if (ghostMesh == null) ghostMesh = com.dan.rungholt.scene.Stadt1362.churchMesh();
        double surf = wiring.level.depthAt(0, 0) > 0 ? wiring.level.surfaceAt(0, 0) : groundHeight(0, 0);
        // Phase 14: ruhiger (vorher ±0,4 m mit dem Wellengang); der Schemen steht, er schwankt nur kaum
        double wob = 0.06 * Math.sin(clock.t() * 0.35) + 0.03 * Math.sin(clock.t() * 0.83);
        com.dan.timber.House g = new com.dan.timber.House(com.dan.timber.HouseType.HALLE, com.dan.rungholt.scene.Stadt1362.CHURCH_X, surf + 0.2 + wob,
                com.dan.rungholt.scene.Stadt1362.CHURCH_Z, com.dan.rungholt.scene.Stadt1362.CHURCH_HEADING, 1, 1, 0.35, 1362);
        if (townOnGpu != null || ghostHouse == null) { timberGl.setSpecial(gl, g, ghostMesh, 30_000); timberGl.setStructures(gl, null, 0); townOnGpu = null; }
        else timberGl.moveSpecial(gl, g);
        ghostHouse = g;
        com.dan.timber.gl.TimberGl.View tv = new com.dan.timber.gl.TimberGl.View();
        tv.viewProj = vp;
        tv.eye = camera.eye.clone();
        tv.forward = camera.fwd.clone();
        tv.fovyDeg = fovy();
        tv.aspect = hdrW / (double) Math.max(1, hdrH);
        tv.reversedZ = reversedZ;
        com.dan.timber.gl.TimberGl.Light tl = new com.dan.timber.gl.TimberGl.Light();
        double sky = Math.max(1e-6, (L[2][0] + L[2][1] + L[2][2]) / 3);
        tl.sunDir = new double[]{0.35, 0.8, 0.45};
        tl.sunColor = new double[]{sky * 7, sky * 7.6, sky * 8.5};      // fahl, bläulich: heller als die Nacht um ihn
        tl.moonColor = new double[]{0, 0, 0};
        tl.skyColor = new double[]{sky * 5, sky * 5.4, sky * 6};
        tl.fogColor = L[3];
        tl.fogDistance = fogDist * 3;
        tl.frost = 1;          // fahl: wie bereift, Mauer und Dach grauweiß statt Backstein und Reet
        tl.roofSnow = 1;
        tl.groundSnow = 1;
        tl.snowWet = 0;
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL4.GL_CONSTANT_ALPHA, GL4.GL_ONE_MINUS_CONSTANT_ALPHA);
        float flicker = (float) (0.75 + 0.25 * Math.sin(clock.t() * 2.3 + Math.sin(clock.t() * 0.37) * 3));
        gl.glBlendColor(0, 0, 0, (float) (0.25 * a * flicker));
        timberGl.draw(gl, timber, java.util.List.of(g), tv, tl);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
    }

    // ------------------------------------------------------------------ Phase 13: die Wege von 1362 aus com.dan.road
    public volatile boolean showRoads = true;
    private com.dan.road.Roads roads1362;
    private com.dan.buoyancy.Mesh roadMesh;
    private double roadX = Double.NaN, roadZ = Double.NaN;
    private Terrain roadLand;
    public volatile String roadInfo = "Wege 1362: noch nicht gebaut";

    /**
     * Die Wege der Stadt als Netz aus {@code com.dan.road} [G]: der Kleiweg auf der Warftenkette (1,7 m, festgetretener
     * Klei mit Grasrand) und der Pfad am Hauptsielzug zum Siel; ohne Verkehr. Die Stücke im Umkreis von 1,5 km um das
     * Auge werden zu einem Netz für {@code BuoyancyGl}, neu, wenn das Auge 80 m weiter ist oder das Gelände sich ändert
     * (Bresche). Die Linie im Shader bleibt darunter als ausgetretener Rand und für die Ferne.
     */
    private com.dan.buoyancy.Mesh roadMesh() {
        Terrain l = land1362;
        if (l == null) return null;
        if (roads1362 == null || roadLand != l) {
            com.dan.road.Ground g = (x, z) -> (float) l.height(x, z);
            com.dan.road.Network net = new com.dan.road.Network();
            com.dan.road.WayType chain = com.dan.road.WayType.path();
            chain.name = "Kleiweg";
            chain.laneWidth = 1.7f; chain.verge = 0.4f; chain.wander = 0.4f;
            chain.surface = new float[]{0.085f, 0.072f, 0.055f}; chain.worn = new float[]{0.11f, 0.095f, 0.07f};
            double[][] c = com.dan.rungholt.scene.Land1362.CHAIN;
            double[] xz = new double[c.length * 2];
            for (int i = 0; i < c.length; i++) { xz[2 * i] = c[i][0]; xz[2 * i + 1] = c[i][1]; }
            net.add(new com.dan.road.Way(chain, false, xz));
            double[][] k = com.dan.rungholt.scene.Land1362.CANALS[0];
            java.util.List<Double> pp = new java.util.ArrayList<>();
            for (int i = 0; i < k.length; i++) {   // 18 m nördlich neben dem Sielzug
                int a = Math.max(0, i - 1), b = Math.min(k.length - 1, i + 1);
                double dx = k[b][0] - k[a][0], dz = k[b][1] - k[a][1], dl = Math.hypot(dx, dz);
                pp.add(k[i][0] + dz / dl * 18); pp.add(k[i][1] - dx / dl * 18);
            }
            double[] px2 = new double[pp.size()];
            for (int i = 0; i < px2.length; i++) px2[i] = pp.get(i);
            com.dan.road.WayType pfad = com.dan.road.WayType.path();
            pfad.surface = new float[]{0.085f, 0.072f, 0.055f}; pfad.worn = new float[]{0.11f, 0.095f, 0.07f};
            net.add(new com.dan.road.Way(pfad, false, px2));
            net.build(g);
            roads1362 = new com.dan.road.Roads(net, g, 1362);
            roads1362.showTraffic = false;
            roads1362.radius = 1500;
            roadLand = l;
            roadX = Double.NaN;
        }
        if (Double.isNaN(roadX) || Math.hypot(camera.eye[0] - roadX, camera.eye[2] - roadZ) > 80) {
            com.dan.road.Roads rd = roads1362;
            rd.update((float) (clock.t() % 3600), 0, camera.eye[0], camera.eye[1], camera.eye[2]);
            com.dan.road.Batch b = rd.batch;
            com.dan.buoyancy.Mesh m = new com.dan.buoyancy.Mesh();
            double[] col = new double[3];
            for (int t = 0; t < b.nt; t++) {
                int i0 = b.tri[3 * t], i1 = b.tri[3 * t + 1], i2 = b.tri[3 * t + 2];
                if (b.kind[i0] == com.dan.road.Batch.LAMP || b.kind[i0] == com.dan.road.Batch.GLOW) continue;
                col = new double[]{(b.rgb[3 * i0] + b.rgb[3 * i1] + b.rgb[3 * i2]) / 3, (b.rgb[3 * i0 + 1] + b.rgb[3 * i1 + 1] + b.rgb[3 * i2 + 1]) / 3, (b.rgb[3 * i0 + 2] + b.rgb[3 * i1 + 2] + b.rgb[3 * i2 + 2]) / 3};
                m.tri(new double[]{b.xyz[3 * i0], b.xyz[3 * i0 + 1], b.xyz[3 * i0 + 2]}, new double[]{b.nrm[3 * i0], b.nrm[3 * i0 + 1], b.nrm[3 * i0 + 2]},
                        new double[]{b.xyz[3 * i1], b.xyz[3 * i1 + 1], b.xyz[3 * i1 + 2]}, new double[]{b.nrm[3 * i1], b.nrm[3 * i1 + 1], b.nrm[3 * i1 + 2]},
                        new double[]{b.xyz[3 * i2], b.xyz[3 * i2 + 1], b.xyz[3 * i2 + 2]}, new double[]{b.nrm[3 * i2], b.nrm[3 * i2 + 1], b.nrm[3 * i2 + 2]}, col, com.dan.buoyancy.Mesh.PAINT);
            }
            if (roadMesh != null) pendingForget.add(roadMesh);
            roadMesh = m;
            roadX = camera.eye[0]; roadZ = camera.eye[2];
            roadInfo = String.format(Locale.GERMAN, "Wege 1362 (com.dan.road): %d Stücke, %,d Dreiecke, %.1f ms", rd.pieces(), m.triangles(), rd.msBuild);
        }
        return roadMesh;
    }
    private double lastEraBlend = Double.NaN, lastEraGround = Double.NaN;

    /**
     * Phase 13: die Häuser während der Überblendung: jedes steigt mit eigenem Einsatz (nach Zufall und Abstand von der
     * Kirche, die zuerst kommt) aus dem Boden, der selbst gerade steigt; bei 1362 stehen alle auf ihrem Grund. Die
     * Kirche (eigenes Netz) wird mitgesetzt.
     */
    private java.util.List<com.dan.timber.House> risingHouses(GL4 gl, Town tw, double b) {
        java.util.List<com.dan.timber.House> l = new java.util.ArrayList<>(tw.all.size());
        for (com.dan.timber.House h : tw.all) {
            boolean church = h == tw.church;
            double y = h.y() - riseDepth(h, b, church);
            com.dan.timber.House m = new com.dan.timber.House(h.type(), h.x(), y, h.z(), h.headingDeg(), h.lengthScale(), h.widthScale(), h.state(), h.seed());
            if (church) { timberGl.moveSpecial(gl, m); churchRaised = true; }
            l.add(m);
        }
        return l;
    }

    /** Phase 13: wie tief ein Haus bei der Überblendung b noch im Boden steckt (m); 0 = steht. */
    public static double riseDepth(com.dan.timber.House h, double b, boolean church) {
        double start = church ? 0.18 : 0.28 + 0.35 * ((h.seed() >>> 11) % 1000) / 1000.0 + Math.min(0.15, Math.hypot(h.x(), h.z()) / 20_000);
        double t = Math.max(0, Math.min(1, (b - start) / 0.32));
        t = t * t * (3 - 2 * t);
        return (1 - t) * (church ? 36 : 11);
    }

    /** Phase 11: Herdrauch über der Stadt (nach dem Undurchsichtigen, mit Tiefenkopie). */
    private void drawSmoke(GL4 gl, float[] vp, double[] sunDir, double[][] L, double fogDist) {
        smoke.windFromDeg = wiring.wind.fromDeg();
        smoke.windSpeed = wiring.wind.speed();
        smoke.stability = com.dan.smoke.Smoke.stability(clock.sun()[0], smoke.windSpeed, Math.min(1, wiring.oktas / 8));
        smoke.select(camera.eye[0], camera.eye[1], camera.eye[2]);
        smokeGl.update(gl, smoke);
        com.dan.smoke.gl.SmokeGl.View sv = new com.dan.smoke.gl.SmokeGl.View();
        sv.viewProj = vp;
        sv.eye = camera.eye.clone();
        sv.reversedZ = reversedZ;
        sv.near = NEAR;
        sv.far = FAR;
        sv.depthTexture = depthCopyTex;
        com.dan.smoke.gl.SmokeGl.Light sl = new com.dan.smoke.gl.SmokeGl.Light();
        sl.sunDir = sunDir;
        sl.sunColor = L[0];
        sl.moonColor = L[1];
        sl.skyTop = L[2];
        sl.skyHorizon = L[3];
        sl.fogColor = L[3];
        sl.fogDistance = fogDist;
        smokeGl.draw(gl, sv, sl, 1, smoke.time());
    }

    // ------------------------------------------------------------------ Phase 12: die Grote Mandränke

    /** Die Nacht der Mandränke (null: kein Sturm); Ablauf in {@link com.dan.rungholt.scene.Mandraenke}. */
    public volatile com.dan.rungholt.scene.Mandraenke mandraenke;
    /** Standbilder: die Regie läuft nur über {@code stepMandraenke(dt, true)} (nicht im Bildlauf). */
    public volatile boolean manualMandraenke;
    private short[] landBackupH;
    private byte[] landBackupA;
    private double lapseBefore = Double.NaN, mandMillis = Double.NaN;
    private com.dan.rungholt.scene.Mandraenke.Stage lastStage;
    private boolean regieBefore;
    /** Die Welle in der Bresche ({@code com.dan.wave}, feines Gitter 4 m) und ihr Faden. */
    public volatile com.dan.wave.Wave flutWelle;
    private volatile double welleRest;
    public volatile boolean welleOn;
    private final Physik welleFaden = new Physik("Welle");
    private int waveTex, waveTexN;
    private long waveUploaded = -1;
    private float[] waveBuf;
    private java.nio.FloatBuffer waveFb;
    private double[] waveRect = {0, 0, 1, 0};
    private final Physik.Aufgabe breachTask = new Physik.Aufgabe() {
        @Override public void step(double t, double dt) {
            com.dan.rungholt.scene.Mandraenke m = mandraenke;
            if (m != null && wiring != null) m.stepBreach(dt, level(), breachLand(m), wiring.level.hours());
        }
        @Override public String name() { return "Deichbruch"; }
    };
    private final Physik.Aufgabe waveTask = new Physik.Aufgabe() {
        @Override public void step(double t, double dt) {
            com.dan.wave.Wave w = flutWelle;
            if (w != null && welleOn) w.step(dt * Math.max(0, Math.min(20, clock.lapse)));
        }
        @Override public String name() { return "Welle"; }
    };
    private com.dan.buoyancy.Mesh solidMesh;
    private int solidVersion = -1;
    private final java.util.Map<com.dan.fracture.Piece, com.dan.buoyancy.Mesh> pieceMeshes = new java.util.IdentityHashMap<>();

    /**
     * Phase 12: die Mandränke beginnen: 23. Januar 1362, 22 Uhr (julianisch der 15., Phase 17), Welt von 1362 (Gelände und Stadt werden gebaut,
     * falls nötig, gut 8 s), Regie aufziehend, Windstau nach Drehbuch, Bruchstelle s. false ohne Höhendaten.
     */
    public synchronized boolean startMandraenke(com.dan.rungholt.scene.Mandraenke.Stelle stelle) {
        if (!terrain.measured() || wiring == null) return false;
        if (mandraenke != null) stopMandraenke(false);
        if (land1362 == null) buildLand1362();
        if (land1362 == null || town == null) return false;
        clock.set(com.dan.rungholt.scene.Mandraenke.DAY, com.dan.rungholt.scene.Mandraenke.START_HOUR);
        wiring.era.target = 1;
        wiring.era.blend = 1;
        double start = wiring.level.hours();
        com.dan.tide.Tide.Turn hw = wiring.level.tide.next(start + 3, true);
        com.dan.rungholt.scene.Mandraenke m = new com.dan.rungholt.scene.Mandraenke(stelle, start, hw.hours(), town.houses);
        m.bruch.world.quality = quality.fracture();   // Phase 17
        m.hypsometry = hypsometry(land1362);
        landBackupH = land1362.h.clone();
        landBackupA = land1362.art.clone();
        regieBefore = wiring.regie;
        wiring.regie = true;
        wiring.resumeRegie();   // Phase 15: Handregler eines Regiebuchs davor (Weststurm der Glocken) lösen
        wiring.startFree(com.dan.weather.Weather.Phase.AUFZIEHEND);
        wiring.hand(com.dan.weather.Weather.Var.FOG, 0);   // Befund Status_Phase15a: der Seenebel der Lage verschluckte den Abend
        wiring.hand(com.dan.weather.Weather.Var.TEMPERATURE, 5);   // Phase 16: mild, Regen statt Schnee
        wiring.season.mild(5);   // die Schneedecke des gewürfelten Januars taut weg
        wiring.surgeScript = m.surge(start);
        wiring.feedTide(0);
        lastStage = m.stage;
        mandMillis = clock.epochMillis();
        town.people.gather(null, 0);
        town.people.always = false;
        town.people.hidden = false;
        smoke.setSources(town.hearths);
        mandraenke = m;
        return true;
    }

    /** Phase 12: die Mandränke beenden: Deich und Überflutung wie vorher, die Stadt steht wieder, Sommer 1361. */
    public synchronized void stopMandraenke(boolean summer) {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m == null) return;
        mandraenke = null;
        physik.remove(breachTask);
        welleOn = false;
        flutWelle = null;
        if (landBackupH != null && land1362 != null) {
            System.arraycopy(landBackupH, 0, land1362.h, 0, landBackupH.length);
            System.arraycopy(landBackupA, 0, land1362.art, 0, landBackupA.length);
            land1362.refresh();
            land1362Uploaded = false;
            floods[1] = null;
        }
        landBackupH = null;
        if (!Double.isNaN(lapseBefore)) { clock.lapse = lapseBefore; lapseBefore = Double.NaN; }
        if (wiring != null) { wiring.surgeScript = Double.NaN; wiring.resumeRegie(); wiring.regie = regieBefore; wiring.season.mild(0); }
        if (town != null) {
            town.people.gather(null, 0);
            town.people.always = false;
            town.people.hidden = false;
            smoke.setSources(town.hearths);
        }
        solidMesh = null;
        pieceMeshes.clear();
        if (summer) clock.set(java.time.LocalDate.of(1361, 7, 21), 12);
    }

    /** Phase 12: zum Überlauf springen (die Wellen schlagen schon eine Weile über die Krone; der Bruch kommt in Minuten). */
    public void jumpToOvertop() { jumpToOvertop(0.9, 0.95); }

    /**
     * Phase 12, Schritt 2 im Block MANDRÄNKE: vor den Bruch. Die Wellen schlagen seit einer halben Stunde über die Krone,
     * die Kamera steht auf dem Deich neben der Bruchstelle, die Uhr läuft 1 min/s: nach rund 20 s bricht der
     * Deich von selbst (dann Echtzeit, danach wieder 1 min/s).
     */
    public void beforeBreach() {
        jumpToOvertop(0.5, 0.5);
        if (clock.lapse < 60) clock.lapse = 60;
        placeBreachView();
    }

    /** Zum Überlauf: Uhr auf dessen Beginn plus at Stunden, schon ausgespült ist done (Anteil von {@code OVERTOP_HOURS}). */
    private void jumpToOvertop(double at, double done) {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m == null || wiring == null || m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal()) return;
        // die Stunde, zu der der Pegel die Krone weniger 1 m erreicht, suchen
        double h = Math.max(wiring.level.hours(), m.startH);
        for (double t = h; t < m.hwH + 1; t += 1 / 60.0) {
            double lv = wiring.level.tide.astronomical(t) + m.surge(t);
            if (lv >= com.dan.rungholt.scene.Land1362.CREST - com.dan.rungholt.scene.Mandraenke.OVERTOP_BELOW_CREST) { h = t; break; }
        }
        clock.setEpochMillis((h + at * com.dan.rungholt.scene.Mandraenke.OVERTOP_HOURS) * 3_600_000);
        if (m.stage.ordinal() < com.dan.rungholt.scene.Mandraenke.Stage.STURM.ordinal()) m.stage = com.dan.rungholt.scene.Mandraenke.Stage.STURM;
        m.stage = com.dan.rungholt.scene.Mandraenke.Stage.UEBERLAUF;
        m.overtopH = done * com.dan.rungholt.scene.Mandraenke.OVERTOP_HOURS;
        mandMillis = clock.epochMillis();
    }

    /** Phase 12: die Kamera auf den Seedeich 90 m neben der Bruchstelle, Blick auf die Bresche und in die Marsch. */
    public void placeBreachView() {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        com.dan.rungholt.scene.Deichbruch b = m != null ? m.bruch : new com.dan.rungholt.scene.Deichbruch(stelle.x);
        double x = b.x + b.t[0] * 90, z = b.z + b.t[1] * 90;
        double tx = b.x - b.s[0] * 25, tz = b.z - b.s[1] * 25;   // die Krone entlang, die See auf der einen, die Marsch auf der anderen Seite
        camera.stopFahrt();
        camera.diving = false;
        camera.place(x, ground1362(x, z) + 3.5, z, (Math.toDegrees(Math.atan2(tx - x, -(tz - z))) + 360) % 360, 8);
    }

    /** Phase 12: die im Block MANDRÄNKE gewählte Bruchstelle (gilt für den nächsten Abend und, solange der Deich steht, sofort). */
    public volatile com.dan.rungholt.scene.Mandraenke.Stelle stelle = com.dan.rungholt.scene.Mandraenke.Stelle.KIRCHE;

    /** Phase 12: Bruchstelle wählen; true, wenn sie schon in dieser Nacht gilt (oder noch keine läuft). */
    public boolean selectStelle(com.dan.rungholt.scene.Mandraenke.Stelle s) {
        stelle = s;
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        return m == null || m.relocate(s);
    }

    /** Phase 12: der Auslöser von Hand: jetzt bricht der Deich an der Bruchstelle. */
    public void breakDikeNow() {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m != null && wiring != null) m.breakNow(wiring.level.hours());
    }

    /** Phase 12: „nach der Flut“: der Morgen des 24. Januar (julianisch des 16.), Bresche offen, die Stadt zerstört, die Kirche steht. */
    public void jumpAfterFlood() {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m == null || wiring == null) return;
        double hwAstro = wiring.level.tide.astronomical(m.hwH);
        double now = wiring.level.hours();
        m.afterTheFlood(m.peak(hwAstro), now);
        // der Spiegel in der Marsch durch die Nacht (Becken aus Zu- und Abfluss, minutenweise)
        for (double tt = Math.max(now, m.startH); tt < m.hwH + 7; tt += 1 / 60.0) m.stepPolder(60, wiring.level.tide.astronomical(tt) + m.surge(tt));
        clock.setEpochMillis((m.hwH + 7) * 3_600_000);
        mandMillis = clock.epochMillis();
    }

    /** Für die Prüfung: Ruhespiegel der Welle (m NHN). */
    public double welleRestForTest() { return welleRest; }

    /** Wasser auf der Landseite der Bresche (m NHN): aus der Welle oder der Überflutung, sonst der Grund. */
    double breachLand(com.dan.rungholt.scene.Mandraenke m) {
        double x = m.bruch.x - m.bruch.s[0] * 35, z = m.bruch.z - m.bruch.s[1] * 35;
        double w = waterSurface(x, z);
        Terrain l = land1362;
        double g = l == null ? 0.8 : l.height(x, z);
        return Double.isNaN(w) ? g : Math.max(g, w);
    }

    /** Wasserspiegel in der Welt von 1362 (Welle in der Bresche, sonst Überflutung), NaN wo trocken. */
    public double waterSurface(double x, double z) {
        com.dan.wave.Wave w = flutWelle;
        if (w != null && welleOn) {
            double lv = w.levelAt(x, z);
            if (!Double.isNaN(lv)) return lv + welleRest;
            double[][] r = w.rects();
            if (r.length > 0 && r[0] != null && x >= r[0][0] && z >= r[0][1] && x < r[0][0] + r[0][2] && z < r[0][1] + r[0][2]) return Double.NaN;
        }
        Flood f = floods[1];
        if (f == null) return Double.NaN;
        return f.depthAt(x, z) > 0.05 ? f.surfaceAt(x, z) : Double.NaN;
    }

    private double[] waterVelocity(double x, double z) {
        com.dan.wave.Wave w = flutWelle;
        if (w != null && welleOn && !Double.isNaN(w.levelAt(x, z))) { double[] v = w.velocityAt(x, z); return new double[]{v[0], v[1]}; }
        Flood f = floods[1];
        return f == null ? new double[2] : f.velocityAt(x, z);
    }

    /**
     * Phase 12: die Regie der Mandränke um dt weiter (Bildlauf: dt echte Sekunden; Standbilder mit inline = true: dt
     * Sekunden Szenenzeit, Bruch und Welle rechnen dann hier statt in ihren Fäden).
     */
    public void stepMandraenke(double dt, boolean inline) {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m == null || wiring == null || (!inline && manualMandraenke)) return;
        double now = clock.epochMillis();
        double sd = inline ? dt : Double.isNaN(mandMillis) ? 0 : Math.max(0, (now - mandMillis) / 1000);
        mandMillis = now;
        double h = wiring.level.hours(), sea = level();
        wiring.surgeScript = m.surge(h);
        wiring.feedTide(0);
        sea = level();
        m.step(sd, h, sea);
        if (m.stage != lastStage) {
            if (m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.STURM.ordinal() && lastStage.ordinal() < com.dan.rungholt.scene.Mandraenke.Stage.STURM.ordinal()) {
                wiring.startFree(com.dan.weather.Weather.Phase.ORKAN);
                if (town != null) {
                    double[] k = {com.dan.rungholt.scene.Stadt1362.CHURCH_X, com.dan.rungholt.scene.Stadt1362.CHURCH_Z};
                    town.people.gather(k, 38);
                    town.people.always = true;
                }
            }
            if (m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal()) smoke.setSources(java.util.List.of());   // die Herdfeuer sind aus
            lastStage = m.stage;
        }
        if (m.carveNow) {
            m.carveNow = false;
            carveBreach(m);
            if (m.stage == com.dan.rungholt.scene.Mandraenke.Stage.BRUCH) {
                Terrain l = land1362;
                m.bruch.build((x, z) -> l.height(x, z));
                solidVersion = -1;
                if (!inline) physik.add(breachTask);
            }
        }
        if (inline && m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.BRUCH.ordinal()) {
            int n = (int) Math.min(120 * 900, Math.round(sd * 120));
            for (int k = 0; k < n; k++) m.stepBreach(1 / 120.0, level(), breachLand(m), h);
        }
        if (m.openNow) {
            m.openNow = false;
            floods[1] = null;   // der Faden der Tide baut die Überflutung mit dem gebrochenen Deich neu
            if (inline) selectEra();
            if (m.stage == com.dan.rungholt.scene.Mandraenke.Stage.FLUT) startWave(m, inline);
        }
        // Echtzeit vom Bruch bis 3 min nach der offenen Bresche, danach der Zeitraffer von vorher
        if (!inline) {
            if (m.realtime(h)) { if (Double.isNaN(lapseBefore)) { lapseBefore = clock.lapse; clock.lapse = 1; } }
            else if (!Double.isNaN(lapseBefore)) { clock.lapse = lapseBefore; lapseBefore = Double.NaN; }
        }
        com.dan.wave.Wave w = flutWelle;
        if (w != null && welleOn) {
            if (inline) w.step(sd);
            if (clock.lapse > 20 || h > m.openH + 0.5) welleOn = false;   // dann rechnet die Überflutung allein weiter
        }
        Flood f1 = floods[1];
        if (f1 != null && !Double.isNaN(m.polder)) {
            if (f1.basinMask == null) f1.basinMask = polderMask(f1);
            f1.basinLevel = m.polder;
        }
        m.stepHouses(sd, h, new com.dan.rungholt.scene.Mandraenke.Water() {
            public double surface(double x, double z) { return waterSurface(x, z); }
            public double[] velocity(double x, double z) { return waterVelocity(x, z); }
        });
        if (town != null && m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal()) {
            double ws = waterSurface(com.dan.rungholt.scene.Stadt1362.CHURCH_X + 25, com.dan.rungholt.scene.Stadt1362.CHURCH_Z + 15);
            town.people.hidden = !Double.isNaN(ws) && ws > land1362.height(com.dan.rungholt.scene.Stadt1362.CHURCH_X + 25, com.dan.rungholt.scene.Stadt1362.CHURCH_Z + 15) + 0.3;
        }
    }

    private double maskLevel = Double.NaN;
    private long maskWave = -1, maskNanos;
    private byte[] maskBuf;

    /**
     * Phase 12: die Maske des Meers für 1362 neu, wenn die Marsch unter Wasser steht: frei (0) sind Zellen der Marsch, deren
     * Grund unter dem Spiegel des Beckens liegt oder die die Welle in der Bresche nass hat; sonst wie gebaut. Höchstens
     * einmal je Sekunde, und nur wenn sich der Spiegel um 3 cm geändert oder die Welle weitergerechnet hat.
     */
    private void updateMask1362(GL4 gl) {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        Terrain l = land1362;
        if (l == null || mask1362Tex == 0) return;
        double p = m == null ? Double.NaN : m.polder;
        com.dan.wave.Wave w = flutWelle;
        com.dan.wave.Wave.Snapshot ws = w != null && welleOn ? w.snapshot() : null;
        long wv = ws == null ? -1 : ws.version();
        boolean levelChanged = Double.isNaN(p) != Double.isNaN(maskLevel) || (!Double.isNaN(p) && Math.abs(p - maskLevel) >= 0.03);
        if (!levelChanged && wv == maskWave) return;
        long now = System.nanoTime();
        if (!levelChanged && now - maskNanos < 1_000_000_000L) return;
        maskNanos = now;
        maskLevel = p;
        maskWave = wv;
        int n = Terrain.N;
        if (maskBuf == null) maskBuf = new byte[n * n];
        com.dan.wave.Wave.Level f = ws == null ? null : ws.fine();
        float pl = Double.isNaN(p) ? -1e9f : (float) p;
        for (int k = 0; k < maskBuf.length; k++) {
            byte a = l.art[k];
            boolean land = a == Terrain.KOOG || a == Terrain.RAND || a == Terrain.MARSCH || a == Terrain.WARFT || a == Terrain.SIELZUG;
            if (land && Terrain.decode(l.h[k]) < pl - 0.02) land = false;
            if (land && f != null) {
                int i = k % n, j = k / n;
                double x = Terrain.cx(i), z = Terrain.cz(j);
                int wi = (int) Math.floor((x - f.x0()) / f.cell()), wj = (int) Math.floor((z - f.z0()) / f.cell());
                if (wi >= 0 && wj >= 0 && wi < f.nx() && wj < f.nz() && f.eta()[wj * f.nx() + wi] > com.dan.wave.Wave.DRY) land = false;
            }
            maskBuf[k] = (byte) (land ? 255 : 0);
        }
        gl.glBindTexture(GL.GL_TEXTURE_2D, mask1362Tex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(maskBuf));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    }

    /** Fläche der Marsch hinter dem Seedeich unter NHN k·5 cm (m²), k = 0 … 160. */
    static double[] hypsometry(Terrain l) {
        int n = Terrain.N, bins = 161;
        double[] a = new double[bins];
        double cellA = Terrain.CELL * Terrain.CELL;
        for (int j = 0; j < n; j += 2)
            for (int i = 0; i < n; i += 2) {
                double x = Terrain.cx(i), z = Terrain.cz(j);
                if (com.dan.rungholt.scene.Land1362.dikeSide(x, z) <= 15) continue;
                double g = Terrain.decode(l.h[j * n + i]);
                int k = (int) Math.ceil(Math.max(0, g) / 0.05);
                if (k < bins) a[k] += cellA * 4;
            }
        for (int k = 1; k < bins; k++) a[k] += a[k - 1];
        return a;
    }

    /** Die Zellen der Überflutung hinter dem Seedeich (für das Becken mit eigenem Spiegel). */
    static boolean[] polderMask(Flood f) {
        boolean[] m = new boolean[f.nx * f.nz];
        for (int j = 0; j < f.nz; j++)
            for (int i = 0; i < f.nx; i++)
                m[j * f.nx + i] = com.dan.rungholt.scene.Land1362.dikeSide(f.x0 + (i + 0.5) * f.cell, f.z0 + (j + 0.5) * f.cell) > 15;
        return m;
    }

    /** Das Gelände von 1362 in der Bresche aushöhlen und auf die Grafikkarte (Höhe, Art, Maske neu). */
    private void carveBreach(com.dan.rungholt.scene.Mandraenke m) {
        Terrain l = land1362;
        if (l == null) return;
        int n = Terrain.N, r = 14;
        int ci = (int) Math.floor((m.bruch.x - Terrain.X0) / Terrain.CELL), cj = (int) Math.floor((m.bruch.z - Terrain.Z0) / Terrain.CELL);
        for (int j = Math.max(0, cj - r); j <= Math.min(n - 1, cj + r); j++)
            for (int i = Math.max(0, ci - r); i <= Math.min(n - 1, ci + r); i++) {
                int k = j * n + i;
                double x = Terrain.cx(i), z = Terrain.cz(j), old = Terrain.decode(l.h[k]);
                double y = m.bruch.carve(x, z, old);
                if (y < old - 1e-3) {
                    l.h[k] = Terrain.encode(y);
                    if (y < 1.4) l.art[k] = Terrain.WATT;
                }
            }
        l.refresh();
        land1362Uploaded = false;
        reedBlend = -1;   // Halme und Bodendecke neu für die Bresche
    }

    /** Die Welle in der Bresche anlegen: 1,6 km im Geviert zu 4 m, die See vor dem Deich festgehalten. */
    private void startWave(com.dan.rungholt.scene.Mandraenke m, boolean inline) {
        Terrain l = land1362;
        if (l == null) return;
        double rest = level();
        com.dan.wave.Wave w = new com.dan.wave.Wave();
        w.coarseN = 0;
        w.fineN = 400;
        w.fineCell = 4;
        w.spongeCells = 0;
        w.edgeDamping = 0;
        w.manning = 0.03;
        double cx = m.bruch.x - m.bruch.s[0] * 300, cz = m.bruch.z - m.bruch.s[1] * 300;
        w.place(cx, cz, 0, 0).build((x, z) -> rest - l.height(x, z));
        w.initial((x, z) -> com.dan.rungholt.scene.Land1362.dikeSide(x, z) < 0 ? 0 : Math.max(l.height(x, z), com.dan.rungholt.scene.Land1362.INNER_WATER) - rest);
        w.hold(new com.dan.wave.Wave.Hold() {
            public boolean held(double x, double z) {
                return com.dan.rungholt.scene.Land1362.dikeSide(x, z) < -40 && Math.hypot(x - m.bruch.x, z - m.bruch.z) > 110;
            }
            public double eta(double t) { return level() - rest; }
        });
        welleRest = rest;
        flutWelle = w;
        welleOn = true;
        if (!inline) { welleFaden.remove(waveTask); welleFaden.add(waveTask); }
    }

    /** Die Welle als Wasser über dem Pegel für das Meer (lange Welle, Einheit des feinen Gitters). */
    private void uploadWave(GL4 gl, double base) {
        com.dan.wave.Wave w = flutWelle;
        if (w == null || !welleOn) { waveRect = new double[]{0, 0, 1, 0}; return; }
        com.dan.wave.Wave.Snapshot s = w.snapshot();
        if (s == null || s.fine() == null) return;
        com.dan.wave.Wave.Level f = s.fine();
        if (waveTex == 0 || waveTexN != f.nx()) {
            Gpu.deleteTextures(gl, waveTex);
            waveTex = Gpu.texture(gl, f.nx(), f.nz(), GL4.GL_R32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, waveTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            waveTexN = f.nx();
            waveBuf = new float[f.nx() * f.nz()];
            waveFb = Buffers.newDirectFloatBuffer(waveBuf.length);
            waveUploaded = -1;
        }
        if (s.version() != waveUploaded) {
            float[] e = f.eta();
            float off = (float) (welleRest - base);
            for (int k = 0; k < waveBuf.length; k++) waveBuf[k] = e[k] <= com.dan.wave.Wave.DRY ? -30f : e[k] + off;
            waveFb.clear();
            waveFb.put(waveBuf).flip();
            gl.glBindTexture(GL.GL_TEXTURE_2D, waveTex);
            gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
            gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, f.nx(), f.nz(), GL4.GL_RED, GL.GL_FLOAT, waveFb);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            waveUploaded = s.version();
        }
        waveRect = new double[]{f.x0(), f.z0(), f.cell(), 1};
    }

    /** Der Abschnitt aus Klei und seine Stücke als Netze für {@code BuoyancyGl.drawMeshes}. */
    private void addBreachMeshes(java.util.List<Object[]> items, com.dan.rungholt.scene.Mandraenke m) {
        com.dan.fracture.Solid sol = m.bruch.solid;
        if (sol == null) return;
        if (sol.version() != solidVersion || solidMesh == null) {
            com.dan.buoyancy.Mesh me = new com.dan.buoyancy.Mesh();
            for (int i = 0; i < sol.cells(); i++) if (sol.attached(i)) polyMesh(me, sol.cell(i), true);
            if (solidMesh != null) { com.dan.buoyancy.Mesh old = solidMesh; pendingForget.add(old); }
            solidMesh = me;
            solidVersion = sol.version();
        }
        if (solidMesh.triangles() > 0) items.add(new Object[]{solidMesh, null});
        java.util.Set<com.dan.fracture.Piece> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (com.dan.fracture.Fracture.Pose p : m.bruch.world.poses()) {
            com.dan.fracture.Piece pc = p.piece();
            seen.add(pc);
            com.dan.buoyancy.Mesh me = pieceMeshes.get(pc);
            if (me == null) {
                me = new com.dan.buoyancy.Mesh();
                for (int i = 0; i < pc.cellCount(); i++) polyMesh(me, pc.cell(i), false);
                pieceMeshes.put(pc, me);
            }
            double[] R = p.rotation(), t = p.position();
            items.add(new Object[]{me, new double[]{R[0], R[3], R[6], 0, R[1], R[4], R[7], 0, R[2], R[5], R[8], 0, t[0], t[1], t[2], 1}});
        }
        pieceMeshes.keySet().removeIf(pc -> { if (seen.contains(pc)) return false; pendingForget.add(pieceMeshes.get(pc)); return true; });
    }

    private final java.util.List<com.dan.buoyancy.Mesh> pendingForget = new java.util.ArrayList<>();

    /** Ein Polyeder als Dreiecke: Klei, oben auf der Krone Grasnarbe. */
    private static void polyMesh(com.dan.buoyancy.Mesh me, com.dan.fracture.Poly c, boolean world) {
        double[] klei = {0.085, 0.065, 0.045}, wet = {0.05, 0.04, 0.03}, sod = {0.05, 0.075, 0.03};
        for (com.dan.fracture.Poly.Face f : c.faces) {
            int n = f.count();
            if (n < 3) continue;
            double[] cen = f.centroid();
            double[] col = world && f.ny > 0.6 && cen[1] > com.dan.rungholt.scene.Land1362.CREST - 0.6 && f.tag < 0 ? sod : f.tag < 0 ? klei : wet;
            double[] a = {f.p[0], f.p[1], f.p[2]};
            for (int k = 1; k + 1 < n; k++) {
                double[] b = {f.p[k * 3], f.p[k * 3 + 1], f.p[k * 3 + 2]}, d = {f.p[k * 3 + 3], f.p[k * 3 + 4], f.p[k * 3 + 5]};
                double ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], vx = d[0] - a[0], vy = d[1] - a[1], vz = d[2] - a[2];
                double nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
                if (nx * f.nx + ny * f.ny + nz * f.nz >= 0) me.tri(a, b, d, col, com.dan.buoyancy.Mesh.PAINT);
                else me.tri(a, d, b, col, com.dan.buoyancy.Mesh.PAINT);
            }
        }
    }

    /** „Mandränke: der Deich bricht …“ (leer ohne Sturm). */
    public String mandraenkeLine() {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        if (m == null || wiring == null) return "Mandränke: aus (Block MANDRÄNKE)";
        com.dan.wave.Wave w = flutWelle;
        return m.describe(wiring.level.hours(), level()) + (w != null && welleOn ? String.format(Locale.GERMAN, " · Welle %.0f ms je s", w.busyMs()) : "")
                + (stelle != m.stelle ? " · gewählt: " + stelle.label + " (gilt ab dem nächsten Abend)" : "");
    }

    /** „Rungholt 1362: 86 Häuser und die Kirche · 64 Leute draußen · 96 Rinder, 70 Schafe · Kogge: liegt an der Kaje …“ */
    public String townLine() {
        Town t = town;
        if (t == null) return "Rungholt 1362: noch nicht gebaut (Taste Z)";
        com.dan.rungholt.scene.Mandraenke md = mandraenke;
        if (md != null && md.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal()) {   // seit Phase 13 (Befund Status_Phase12)
            return String.format(Locale.GERMAN, "Rungholt 1362: %d von %d Häusern stehen noch, die Kirche steht · %s · das Vieh ist ertrunken · die Herdfeuer sind aus · %s",
                    t.houses.size() - md.gone() - md.afloat(), t.houses.size(), t.people.hidden ? "die Leute sind in der Kirche" : t.people.people.size() + " Leute auf der Kirchwarft",
                    t.sielhafen.describe(harbourSea));
        }
        return String.format(Locale.GERMAN, "Rungholt 1362: %d Häuser und die Kirche · %s · %d Rinder, %d Schafe · %d Herdfeuer · %s",
                t.houses.size(), t.people.visible ? t.people.people.size() + (md != null && t.people.always ? " Leute auf der Kirchwarft" : " Leute draußen") : "die Leute sind im Haus", t.cattle(), t.beasts.size() - t.cattle(),
                t.hearths.size(), t.sielhafen.describe(harbourSea));
    }

    /** „Wattwanderung 16 Personen · 3,2 von 5,8 km · hält: Wattwurm · 1:12 h“ */
    public String hikeLine() {
        Hike h = hike;
        if (h == null) return "Wattwanderung: keine unterwegs";
        return h.describe() + (figurOk ? " · " + figureGl.info() : " · !nicht gezeichnet");
    }

    public boolean sendWalker() {
        double cx = 0, cz = 0;
        int n = 0;
        for (Colony.Animal a : colony.animals()) if (a.act == Colony.Activity.LIEGEN) { cx += a.x; cz += a.z; n++; }
        if (n == 0) { cx = Seehundbank.X; cz = Seehundbank.Z; } else { cx /= n; cz /= n; }
        return walker.startToward(cx, cz, 900, (x, z) -> wiring == null ? 0 : wiring.level.depthAt(x, z));
    }

    /**
     * Ansicht 5 folgt den Seehunden: 18 m vor der dichtesten Stelle der Gruppe auf der Landseite, Blick über die Tiere
     * zum Wasser, Auge 2 m über dem Grund (die Kamera stört nicht; die Fluchtdistanz gilt für Figuren und Boote). Gibt false, wenn keiner an Land ist (dann gilt der feste Ort aus {@code Ansichten}).
     */
    public boolean placeSealView() {
        // die dichteste Stelle: das Tier mit den meisten Nachbarn in 20 m, und deren Mitte
        Colony.Animal best = null;
        int bestN = -1;
        for (Colony.Animal a : colony.animals()) {
            if (!a.onLand() || a.pup) continue;
            int k = 0;
            for (Colony.Animal o : colony.animals()) if (o.onLand() && Math.hypot(o.x - a.x, o.z - a.z) < 20) k++;
            if (k > bestN) { bestN = k; best = a; }
        }
        if (best == null || wiring == null) return false;
        double cx = 0, cz = 0;
        int n = 0;
        for (Colony.Animal a : colony.animals()) if (a.onLand() && Math.hypot(a.x - best.x, a.z - best.z) < 20) { cx += a.x; cz += a.z; n++; }
        cx /= n;
        cz /= n;
        double wx = 0, wz = 0;
        for (double r = 10; r <= 90 && wx == 0 && wz == 0; r += 10)
            for (int k = 0; k < 24; k++) {
                double a = k * Math.PI / 12, dx = Math.sin(a), dz = -Math.cos(a);
                if (wiring.level.depthAt(cx + dx * r, cz + dz * r) > Colony.WATER) { wx += dx; wz += dz; }
            }
        double wl = Math.hypot(wx, wz);
        if (wl < 1e-6) { wx = cx - Seehundbank.X; wz = cz - Seehundbank.Z; wl = Math.max(1e-6, Math.hypot(wx, wz)); }
        wx /= wl;
        wz /= wl;
        double dist = 18, x = cx - wx * dist, z = cz - wz * dist;
        while (dist > 12 && wiring.level.depthAt(x, z) > 0.02) { dist -= 4; x = cx - wx * dist; z = cz - wz * dist; }
        camera.stopFahrt();
        camera.diving = false;
        camera.place(x, groundHeight(x, z) + 2.0, z, Math.toDegrees(Math.atan2(wx, -wz)), 3);
        return true;
    }

    /** „Seehunde 30 (6 Heuler): 22 liegen, …“ und der Wanderer. */
    public String sealLine() {
        if (!showSeals) return "Seehunde: ausgeblendet";
        if (flood == null) return "Seehunde: warten auf die Überflutung";
        return colony.describe() + (walker.active ? " · " + walker.describe(Seehundbank.X, Seehundbank.Z) : "") + (sealOk ? " · " + sealGl.info() : " · !nicht gezeichnet");
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        if (!ready) {
            gl.glClearColor(0.04f, 0.07f, 0.10f, 1);
            gl.glClear(GL.GL_COLOR_BUFFER_BIT);
            return;
        }
        if (reloadShaders) {
            reloadShaders = false;
            try {
                for (Shader s : all) s.build(gl);
                System.out.println("Shader neu geladen.");
            } catch (RuntimeException e) {
                System.err.println(e.getMessage());
                errorListener.accept(e.getMessage());
            }
        }
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        readMarks(gl);
        long now = System.nanoTime();
        double rawMs = (now - lastNanos) / 1e6;
        if (frame > 2 && rawMs > worstMs) worstMs = rawMs;
        double dt = Math.min(0.1, (now - lastNanos) / 1e9);
        lastNanos = now;
        if (!frozen) step(dt);

        surfW = Math.max(1, d.getSurfaceWidth());
        surfH = Math.max(1, d.getSurfaceHeight());
        regulateScale(now);
        int w = Math.max(64, (int) Math.round(surfW * renderScale)), h = Math.max(64, (int) Math.round(surfH * renderScale));
        if (w != hdrW || h != hdrH) resizeTargets(gl, w, h);
        renderFrame(gl, w, h);
        // Phase 15: Fotos eines Regiebuchs (ohne HUD und Balken) und das Blatt am Ende
        String cap = captureCaption;
        if (cap != null) {
            captureCaption = null;
            album.add(readDefault(gl, surfW, surfH));
            albumCaps.add(clock.timeLabel() + " · " + cap + "\nteuer: " + topPasses(3) + String.format(Locale.GERMAN, " · %.0f Bilder/s", fps));
        }
        if (regie.running()) { albumFps += fps; albumGpu += gpuMs; albumSamples++; }
        String sn = sheetName;
        if (sn != null) { sheetName = null; writeSheet(sn); }
        kinoBild(gl, now);   // Phase 15: Kino über das fertige Bild; F12 und Tempo mit
        if (photoRequest) { photoRequest = false; takePhoto(gl, surfW, surfH); }
        Consumer<BufferedImage> gr = grabRequest;
        if (gr != null) { grabRequest = null; try { gr.accept(readDefault(gl, surfW, surfH)); } catch (RuntimeException e) { System.err.println("Rungholt: Bild nicht abgeholt: " + e); } }
        cpuMs = cpuMs * 0.95 + (System.nanoTime() - now) / 1e6 * 0.05;
        frame++;
        stats(now, w, h);
    }

    /** Ein ganzes Bild in Rechengröße w × h, dann ins Fenster. Bei stehender Szene ({@link #frozen}) malt das HUD gleich. */
    void renderFrame(GL4 gl, int w, int h) {
        mark(gl, 0);
        Sky.State st = skyState();
        if (adaptLum <= 0) adapt(st, 1);
        double pre = skyOk ? exposure() : 1;
        double[] sp = clock.sun();
        double[] sun = skyOk ? st.sunDir : Sun.direction(sp[0], sp[1]);
        float[] proj = Mat4.perspective(fovy(), w / (double) h, NEAR, FAR, reversedZ);
        float[] vp = Mat4.mul(proj, camera.view());
        float[] inv = Mat4.invert(vp);
        double level = level();

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glClearColor(0, 0, 0, 1);
        gl.glClearDepth(reversedZ ? 0 : 1);
        gl.glDepthMask(true);
        gl.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
        gl.glBindVertexArray(emptyVao);

        // 1 Himmel: com.dan.sky (Streuung, Sonne über Süden, Mond, Sterne), sonst der Verlauf aus Phase 1
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_CULL_FACE);
        if (skyOk) {
            skyGl.update(gl, st);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            SkyGl.View sv = new SkyGl.View();
            sv.viewProj = vp;
            sv.invViewProj = inv;
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            sv.preExposure = pre;
            sv.time = clock.t();
            skyGl.draw(gl, st, sv);
            if (skyCheck.isEmpty() && frame > 30) skyCheck = skyGl.selfCheck(gl, st);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyShader, vp, inv, sun, level);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        mark(gl, 1);

        // Blitze übernehmen (neue Kanäle hochladen, Lichtpunkte für Wolken, Meer, Gelände)
        if (lightningOk) {
            lightningSnap = lightning.snapshot();
            lightningGl.update(gl, lightningSnap);
        }
        boolean flashNow = lightningOk && showLightning && lightningGl.active();
        double[] air = lightningAir();

        // 2 Wolken über den Himmel, Kanäle der Blitze, dann das Rundumbild (Spiegelung, Himmelslicht)
        boolean cloudsNow = cloudsOk && showClouds;
        Clouds.Snapshot cs = clouds.snapshot();
        CloudGl.Light cl = cloudsNow ? cloudLight(st, pre) : null;
        if (cloudsNow) {
            cloudGl.update(gl, cs, cl, camera.eye.clone());
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            CloudGl.View cv = new CloudGl.View();
            cv.viewProj = vp;
            cv.invViewProj = inv;
            cv.eye = camera.eye.clone();
            cv.reversedZ = reversedZ;
            cv.width = w;
            cv.height = h;
            cloudGl.draw(gl, cs, cl, cv);
            if (cloudCheck.isEmpty() && frame > 40) cloudCheck = cloudGl.selfCheck(gl, cs, cl);
            gl.glBindVertexArray(emptyVao);
        }
        if (flashNow) {
            lightningGl.drawChannels(gl, lightningView(vp, inv, w, h, pre, air));
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, panoFbo);
        gl.glViewport(0, 0, PANO_W, PANO_H);
        if (skyOk) {
            skyGl.drawPanorama(gl, st, pre);
            if (cloudsNow) cloudGl.drawPanorama(gl, cs, cl, camera.eye.clone());
            if (flashNow) lightningGl.drawPanorama(gl, PANO_W, PANO_H, camera.eye.clone(), pre, air[0], air[1], cloudsNow ? clouds.type.base : lightning.cloudBase);
            gl.glBindVertexArray(emptyVao);
        } else {
            common(gl, skyPano, vp, inv, sun, level);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        if (rainOk) {
            rainGl.update(gl, rain.snapshot());
            if (rainCheck.isEmpty() && rain.rate > 0 && frame > 60) rainCheck = rainGl.selfCheck(gl, rain.snapshot());
        }
        if (flashNow && lightningCheck.isEmpty()) {
            for (Bolt b : lightningSnap.bolts())
                if (b.kind == Bolt.Kind.CLOUD_GROUND && lightningSnap.time() > b.t0 + b.leaderTime) {
                    lightningCheck = lightningGl.selfCheck(gl, lightningSnap);
                    lightningGl.update(gl, lightningSnap);
                    break;
                }
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        mark(gl, 2);

        // 3 Gelände (Wolkenschatten aus der Karte, Mond, Blitzlicht, nach Regen dunkler)
        double[] sl = sunLight(sp[0]), zen = skyZenith(sp[0]), hor = skyHorizon(sp[0]);
        double[][] L = ambientLight(st, pre);
        double[][] fla = flashLight(skyOk ? pre : 1);
        double wet = Math.min(1, rain.rate * (showRain ? 1 : 0) / 3);
        gl.glDepthMask(true);
        gl.glDisable(GL.GL_BLEND);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        common(gl, terrainShader, vp, inv, sun, level);
        // Phase 10: das Gelände von 1362 einmal hochladen, sobald es gebaut ist; die Überblendung je Bild
        Terrain l1362 = land1362;
        if (l1362 != null && !land1362Uploaded) { lod.upload1362(gl, l1362); Gpu.deleteTextures(gl, mask1362Tex); mask1362Tex = maskTexture(gl, l1362); land1362Uploaded = true; maskLevel = Double.NaN; }
        updateMask1362(gl);   // Phase 12: die Marsch, soweit sie unter Wasser steht, aus der Maske nehmen
        lod.era = wiring != null && land1362Uploaded ? wiring.era.blend() : 0;
        lod.bind(gl, terrainShader);
        terrainShader.set(gl, "uRoadN", lod.era > 0 ? ROADS_1362.length / 2 : 0);
        gl.glUniform2fv(gl.glGetUniformLocation(terrainShader.id(), "uRoads"), ROADS_1362.length / 2, ROADS_1362, 0);
        terrainShader.set(gl, "uInnerWater", (float) com.dan.rungholt.scene.Land1362.INNER_WATER);
        terrainShader.set(gl, "uCanalN", CANALS_1362.length / 2);   // seit Phase 13 auch heute (die Sielzüge als Spur)
        gl.glUniform2fv(gl.glGetUniformLocation(terrainShader.id(), "uCanals"), CANALS_1362.length / 2, CANALS_1362, 0);
        terrainShader.set(gl, "uCanalHalf", 6.5f);
        uploadFlood(gl, ocean.waterLevel);   // Phase 4
        double[] fr = floodRect();
        gl.glActiveTexture(GL.GL_TEXTURE11);
        gl.glBindTexture(GL.GL_TEXTURE_2D, floodTex);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uFlood", 11);
        terrainShader.set(gl, "uFloodRect", (float) fr[0], (float) fr[1], (float) fr[2], (float) fr[3]);
        WetField wfNow = wetField;   // Phase 5: das Material des Watts (Einheit 14)
        mudGl.upload(gl, wfNow);
        mudGl.time = clock.t();
        mudGl.bind(gl, terrainShader.id(), 14, wfNow, mudflat);
        MudflatGl.bindRelief(gl, terrainShader.id(), relief);
        // Phase 7: Spuren im Watt (Robben, Stiefel) im Fenster um das Auge, höchstens viermal in der Sekunde neu
        if (wfNow != null && showTracks) {
            trackTimer -= Math.max(0.016, frozen ? 1 : 0.016);
            if (trackTimer <= 0) { trackTimer = 0.25; trackMap.update(wfNow.tracks(), camera.eye[0], camera.eye[2]); }
            mudGl.uploadTracks(gl, trackMap);
        } else mudGl.uploadTracks(gl, null);
        mudGl.bindTracks(gl, terrainShader.id(), 15);
        gl.glActiveTexture(GL.GL_TEXTURE12);
        gl.glBindTexture(GL.GL_TEXTURE_2D, cloudsNow ? cloudGl.shadowTexture() : 0);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        terrainShader.set(gl, "uCloudShadow", 12);
        double[] sr = cloudsNow ? cloudGl.shadowRect() : new double[]{0, 0, 1};
        terrainShader.set(gl, "uCloudRect", (float) sr[0], (float) sr[1], (float) Math.max(1, sr[2]), cloudsNow ? 1f : 0f);
        terrainShader.set(gl, "uFlashDir", fla != null ? fla[0] : new double[]{0, 1, 0});
        terrainShader.set(gl, "uFlashRgb", fla != null ? fla[1] : new double[]{0, 0, 0});
        terrainShader.set(gl, "uWet", (float) wet);
        if (wiring != null) terrainShader.set(gl, "uSeason", (float) wiring.season.snowDepth(), (float) wiring.season.marshRed(), (float) wiring.season.marshWinter(), (float) wiring.season.frost());
        else terrainShader.set(gl, "uSeason", 0f, 0f, 0f, 0f);
        terrainShader.set(gl, "uMarks", showMarks ? 1 : 0);
        double[] wp = warft();
        terrainShader.set(gl, "uWarftPos", (float) wp[0], (float) wp[1]);
        terrainShader.set(gl, "uChurch", (float) Site.CHURCH_LENGTH, (float) Site.CHURCH_WIDTH);
        bindSpuren(gl, terrainShader);   // Phase 13
        lod.draw(gl, terrainShader, camera.eye, vp);
        gl.glBindVertexArray(emptyVao);
        mark(gl, 3);

        // 4 Bodendecke um das Auge (nur zu Fuß, nicht im Watt)
        double windSpeed = wiring != null ? (wiring.regie ? wiring.gustNow : wiring.wind.speed()) : 6;
        double[] to = wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0};
        boolean groundNow = groundOk && showGround && camera.eye[1] - camera.groundLevel < 25 && Terrain.grass(land().artAt(camera.eye[0], camera.eye[2]));
        if (groundOk) {
            Meadow md = wiring != null && wiring.era.is1362() && meadow1362 != null ? meadow1362 : meadow;
            md.setSeason(clock.date().getDayOfYear(), wiring == null ? 0 : (float) Math.min(1, wiring.season.snowDepth() / 0.05));
            md.wind.speed = (float) windSpeed;
            md.wind.direction = (float) Math.toDegrees(Math.atan2(to[2], to[0]));
            groundGl.update(gl, md, (float) (clock.t() % 3600), camera.eye[0], camera.eye[2], !groundNow);
        }
        if (groundNow) {
            GroundGl.View gv = new GroundGl.View();
            gv.viewProj = vp;
            gv.eye = camera.eye.clone();
            gv.reversedZ = reversedZ;
            GroundGl.Light gL = new GroundGl.Light();
            gL.sunDir = sun;
            gL.fogColor = L[3];
            gL.skyColor = L[2];
            if (skyOk) {
                gL.skyTexture = panoTex;
                gL.sunColor = scale(st.sunColor, pre);
                gL.moonDir = st.moonDir;
                gL.moonColor = scale(st.moonColor, pre);
                gL.skyWeight = 1.2;
                gL.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    gL.shadowTexture = cloudGl.shadowTexture();
                    gL.shadowRect = cloudGl.shadowRect();
                }
            } else {
                gL.sunColor = sl;
                gL.skyColor = zen;
                gL.fogColor = hor;
                gL.fogDistance = FOG_DISTANCE;
            }
            groundGl.draw(gl, gv, gL);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 4);

        // 5 Phase 7: Seehunde mit Schatten, der Wattwanderer (nach dem Gelände, vor dem Wasser: das Meer deckt zu, was taucht)
        boolean today = wiring == null || !wiring.era.is1362();
        boolean sealsNow = today && sealOk && showSeals && flood != null && !colony.animals().isEmpty() && Math.hypot(camera.eye[0] - Seehundbank.X, camera.eye[2] - Seehundbank.Z) < 6000;
        boolean birdsNow = today && waderOk && showBirds && flood != null && waders.birds() > 0;
        Hike hkNow = hike;
        boolean digNow = today && showDig && grabung.present() && Math.hypot(camera.eye[0] - grabung.site[0], camera.eye[2] - grabung.site[1]) < 3000;   // Phase 13
        boolean figuresNow = today && figurOk && showFigures && (walker.active || hkNow != null || digNow);
        boolean housesNow = today && timberOk && showHouses && Math.hypot(camera.eye[0] + 1000, camera.eye[2]) < timber.quality.far + 200;
        Town tw = town;
        boolean townNow = tw != null && showTown && timberOk && wiring != null && wiring.era.blend() > 0.15 && (!today || wiring.era.target > 0.5 || wiring.era.blend() > 0.15);   // Phase 11; seit 13 schon während der Überblendung
        if (sealsNow || birdsNow || figuresNow || housesNow || townNow || digNow) {
            SealGl.Light al = animalLight(st, pre, L, sl, zen, hor, sun);
            SealGl.View av = new SealGl.View();
            av.viewProj = vp;
            av.eye = camera.eye.clone();
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            if (sealsNow) {
                sealGl.update(gl, colony);
                sealGl.draw(gl, av, al);
            }
            if (birdsNow) {
                WaderGl.View wv = new WaderGl.View();
                wv.viewProj = vp;
                wv.eye = camera.eye.clone();
                WaderGl.Light wl = new WaderGl.Light();
                wl.sunDir = al.sunDir; wl.sunColor = al.sunColor; wl.moonDir = al.moonDir; wl.moonColor = al.moonColor;
                wl.skyTop = al.skyTop; wl.skyHorizon = al.skyHorizon; wl.fogDistance = al.fogDistance;
                waderGl.draw(gl, waders, wv, wl);
            }
            if (housesNow) {
                com.dan.timber.gl.TimberGl.View tv = new com.dan.timber.gl.TimberGl.View();
                tv.viewProj = vp;
                tv.eye = camera.eye.clone();
                tv.forward = camera.fwd.clone();
                tv.fovyDeg = fovy();
                tv.aspect = w / (double) h;
                tv.reversedZ = reversedZ;
                com.dan.timber.gl.TimberGl.Light tl = new com.dan.timber.gl.TimberGl.Light();
                tl.sunDir = al.sunDir; tl.sunColor = scale(al.sunColor, al.sunShadow); tl.moonDir = al.moonDir; tl.moonColor = al.moonColor;
                tl.skyColor = al.skyTop; tl.fogColor = al.skyHorizon; tl.fogDistance = al.fogDistance;
                tl.wetness = Math.min(1, rain.rate / 3);
                if (wiring != null) {
                    com.dan.season.Year.State ys = wiring.season.state();
                    if (ys != null) { tl.roofSnow = ys.roofSnow(); tl.groundSnow = ys.snowDepth() * ys.snowCover(); tl.snowWet = ys.snowWet(); tl.frost = ys.frost(); }
                }
                if (houses == null) houses = com.dan.rungholt.scene.Suedfall.placed(this::groundHeight);
                if ((townOnGpu != null && !townNow) || ghostHouse != null) { timberGl.setSpecial(gl, null, null, 0); timberGl.setStructures(gl, null, 0); townOnGpu = null; ghostHouse = null; }
                java.util.List<com.dan.timber.House> sh = houses;
                double eb = wiring == null ? 0 : wiring.era.blend();
                if (eb > 0) {   // Phase 13: die Häuser von heute versinken, wenn die Zeitreise beginnt
                    double k = Math.min(1, eb / 0.4), dy = k * k * (3 - 2 * k) * 10;
                    sh = new java.util.ArrayList<>();
                    for (com.dan.timber.House hh : houses) sh.add(new com.dan.timber.House(hh.type(), hh.x(), hh.y() - dy, hh.z(), hh.headingDeg(), hh.lengthScale(), hh.widthScale(), hh.state(), hh.seed()));
                }
                timberGl.draw(gl, timber, sh, tv, tl);
                if (teichOk) {
                    com.dan.season.Year.State ys = wiring == null ? null : wiring.season.state();
                    double ice = ys == null ? 0 : Math.min(1, ys.ice() / 0.02);
                    teichGl.draw(gl, vp, camera.eye.clone(), al, ice, ys == null ? 0 : ys.snowDepth(), wiring == null ? 3 : wiring.wind.speed(), clock.t());
                }
                gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            }
            if (townNow) drawTown(gl, tw, vp, al, sun);
            if (digNow && buoyOk) drawDig(gl, vp, al);
            if (figuresNow) {
                java.util.List<Person> people = new java.util.ArrayList<>();
                if (hkNow != null) people.addAll(hkNow.people());
                if (digNow) people.addAll(grabung.people);
                if (walker.active) {
                    Person fig = walkerPerson;
                    fig.x = walker.x; fig.z = walker.z; fig.y = groundHeight(walker.x, walker.z); fig.heading = walker.heading; fig.phase = walker.phase; fig.gait = 1;
                    people.add(fig);
                }
                FigureGl.View fv = new FigureGl.View();
                fv.viewProj = vp;
                fv.eye = camera.eye.clone();
                FigureGl.Light fl = new FigureGl.Light();
                fl.sunDir = al.sunDir; fl.sunColor = al.sunColor; fl.moonDir = al.moonDir; fl.moonColor = al.moonColor;
                fl.skyTop = al.skyTop; fl.skyHorizon = al.skyHorizon; fl.fogDistance = al.fogDistance; fl.sunShadow = al.sunShadow;
                figureGl.draw(gl, people, fv, fl);
            }
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 5);

        // 5 Meer: rechnen (Compute), dann zeichnen; das Gelände ist sein Grund, die Köge bleiben trocken
        if (oceanOk && showWater) {
            oceanGl.update(gl, ocean);
            if (oceanCheck.isEmpty() && frame > 30) oceanCheck = oceanGl.selfCheck(gl, ocean);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            OceanGl.View ov = new OceanGl.View();
            ov.viewProj = vp;
            ov.eye = camera.eye.clone();
            ov.reversedZ = reversedZ;
            OceanGl.Light ol = new OceanGl.Light();
            ol.sunDir = sun;
            ol.skyTexture = panoTex;
            if (skyOk) {
                ol.sunColor = scale(st.sunColor, pre);
                ol.moonDir = st.moonDir;
                ol.moonColor = scale(st.moonColor, pre);
                ol.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    ol.shadowTexture = cloudGl.shadowTexture();
                    ol.shadowRect = cloudGl.shadowRect();
                }
            } else {
                ol.sunColor = sl;
                ol.fogDistance = FOG_DISTANCE;
            }
            if (rainOk && showRain && rain.rate > 0) {
                ol.detailTexture = rainGl.rippleTexture();
                ol.detailTile = rainGl.rippleTile();
                ol.detailStrength = rainGl.rippleStrength() * Math.min(1, rainStrength);
                ol.detailRange = 60;
            }
            if (flashNow) {
                int n = lightningGl.lightCount();
                ol.flashCount = n;
                for (int i = 0; i < n; i++) {
                    double[] lp = lightningGl.lightPos(i), lr = lightningGl.lightRgb(i);
                    for (int k = 0; k < 4; k++) ol.flashPos[i * 4 + k] = (float) lp[k];
                    for (int k = 0; k < 3; k++) ol.flashRgb[i * 4 + k] = (float) (lr[k] * (skyOk ? pre : 1));
                }
                ol.pointSigma = air[0];
            }
            ol.waterColor = Ocean.WATT_WATER;
            ol.sssColor = Ocean.WATT_SSS;
            boolean old = land1362Uploaded && wiring != null && wiring.era.is1362();
            ol.bedTexture = old ? lod.heightTexture1362() : lod.heightTexture();
            ol.bedRect = new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL};
            ol.bedScale = Terrain.HMAX - Terrain.HMIN;
            ol.bedOffset = Terrain.HMIN;
            ol.bedOutside = TerrainLod.OUTSIDE;
            ol.bedTurbidity = 1.2;
            ol.bedColor = new double[]{0.20, 0.17, 0.13};
            ol.maskTexture = old ? mask1362Tex : maskTex;
            ol.maskRect = new double[]{Terrain.X0, Terrain.Z0, Terrain.N * Terrain.CELL};
            ol.maskMode = -1;
            com.dan.rungholt.scene.Mandraenke mo = mandraenke;
            uploadWave(gl, ocean.waterLevel);
            if (waveRect[3] > 0 && old) { ol.longTexture0 = waveTex; ol.longRect0 = waveRect; }
            // Phase 4: das Wasser je Zelle aus com.dan.tide als lange Welle über dem Pegel am Rand
            if (floodTex != 0) {
                ol.longTexture1 = floodTex;
                ol.longRect1 = floodRect();
            }
            // Phase 6: die Strömung aus der Überflutung (Kräuselung, Schaumstreifen, Trübung) und gegen das Kachelmuster
            uploadFlow(gl);
            if (flowTex != 0) {
                ol.flowTexture = flowTex;
                ol.flowRect = flowRect;
            }
            ol.siltColor = new double[]{0.085, 0.075, 0.050};
            ol.siltStrength = 0.6;
            ol.tileBreak = 1;
            ol.under = underWater();
            ol.longSoft = 1;
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            oceanGl.draw(gl, ocean, ov, ol);
            gl.glBindVertexArray(emptyVao);
        }
        if (glocken.ghost > 0.01) { drawGhost(gl, vp, L, skyOk ? fogDistance(st) : FOG_DISTANCE); gl.glBindVertexArray(emptyVao); }   // Phase 13: der Schemen der Kirche
        mark(gl, 6);

        // 6 Gras und Binsen um die Kamera
        if (reedOk && showReed && camera.eye[1] - camera.groundLevel < 400) {
            double rb = wiring == null || land1362 == null ? 0 : Math.round(wiring.era.blend() * 8) / 8.0;   // Phase 13: in Achteln nachwachsen
            if (rb != reedBlend) { reedBlend = rb; reedGl.invalidate(); }
            reedGl.update(gl, reed, camera.eye[0], camera.eye[2], this::reedDensity, (x, z) -> groundHeight(x, z));
            ReedGl.View rv = new ReedGl.View();
            rv.viewProj = vp;
            rv.eye = camera.eye.clone();
            rv.reversedZ = reversedZ;
            ReedGl.Light rl = new ReedGl.Light();
            rl.sunDir = sun;
            rl.fogColor = L[3];
            if (skyOk) {
                // Sonne und Mond ohne den Schatten am Auge: jeder Halm liest die Wolkenschatten selbst
                rl.sunColor = scale(st.sunColor, pre);
                rl.moonDir = st.moonDir;
                rl.moonColor = scale(st.moonColor, pre);
                rl.skyColor = L[2];
                rl.skyWeight = 1.3;
                rl.fogDistance = fogDistance(st);
                if (cloudsNow) {
                    rl.shadowTexture = cloudGl.shadowTexture();
                    rl.shadowRect = cloudGl.shadowRect();
                }
            } else {
                rl.sunColor = sl;
                rl.skyColor = zen;
                rl.fogColor = hor;
                rl.fogDistance = FOG_DISTANCE;
            }
            reedGl.draw(gl, reed, rv, rl, clock.t());
            gl.glBindVertexArray(emptyVao);
        }
        // Phase 6: unter Wasser trübes Wasser über alles (Tiefe als Kopie)
        if (oceanOk && showWater && underWater()) {
            copyDepth(gl, w, h);
            OceanGl.Under u = new OceanGl.Under();
            u.invViewProj = inv;
            u.eye = camera.eye.clone();
            u.reversedZ = reversedZ;
            u.width = w;
            u.height = h;
            u.depthTexture = depthCopyTex;
            u.surface = camera.waterLevel;
            double sy = Math.max(0, sun[1]);
            double[] e = {L[2][0] * Math.PI + L[0][0] * sy, L[2][1] * Math.PI + L[0][1] * sy, L[2][2] * Math.PI + L[0][2] * sy};
            double[] wc = {0.060, 0.064, 0.040};   // Schwebstoff im Watt: grünbraun [G]
            // Streulicht im trüben Wasser: Einfachstreuung mit hoher Albedo des Schwebstoffs, rund 5 % der Bestrahlung [G]
            u.scatter = new double[]{wc[0] * e[0] * 0.6, wc[1] * e[1] * 0.6, wc[2] * e[2] * 0.6};
            u.sigma = 0.9;
            u.darkening = 0.3;
            oceanGl.drawUnderwater(gl, u);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 7);

        // 7 Regen über die Szene (Tiefe als Kopie)
        boolean rainNow = rainOk && showRain && rain.rate > 0;
        boolean fogNow = fogOk && showFog && fog.snapshot().any();
        boolean copied = false;
        boolean smokeNow = smokeOk && showSmoke && town != null && wiring != null && wiring.era.blend() > 0.9 && showTown;   // Phase 11; seit 13 zuletzt
        boolean sprayNow = sprayOk && !spray.parcels().isEmpty();   // Phase 17
        if (rainNow || fogNow || flashNow || smokeNow || sprayNow) { copyDepth(gl, w, h); copied = true; }
        if (rainNow) drawRain(gl, st, vp, inv, w, h, pre, L);
        if (sprayNow) drawSpray(gl, st, vp, w, h, pre, L);
        if (smokeNow) {
            drawSmoke(gl, vp, skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]), L, skyOk ? fogDistance(st) : FOG_DISTANCE);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        if (snowOk && showRain && snowRate > 0.01) {
            com.dan.season.gl.SeasonGl.View sv = new com.dan.season.gl.SeasonGl.View();
            sv.viewProj = vp;
            sv.eye = camera.eye.clone();
            sv.reversedZ = reversedZ;
            sv.width = w;
            sv.height = h;
            sv.fovyDeg = fovy();
            com.dan.season.gl.SeasonGl.Light sl2 = new com.dan.season.gl.SeasonGl.Light();
            sl2.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
            sl2.sunColor = L[0];
            sl2.moonDir = skyOk ? st.moonDir : new double[]{0, -1, 0};
            sl2.moonColor = L[1];
            sl2.sky = L[2];
            sl2.fogColor = L[3];
            sl2.fogDistance = skyOk ? fogDistance(st) : FOG_DISTANCE;
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
            snowGl.draw(gl, sv, sl2, snowRate, wiring != null ? wiring.wind.toVector() : new double[]{1, 0, 0}, wiring != null ? wiring.wind.speed() : 3, clock.t());
            gl.glDisable(GL.GL_BLEND);
            gl.glDepthMask(true);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        mark(gl, 8);

        // 8 Seenebel bis zur Szenentiefe, dann das Leuchten der Luft um die Blitze
        if (fogNow) drawFog(gl, st, inv, w, h, L);
        if (flashNow && copied) {
            LightningGl.View lv = lightningView(vp, inv, w, h, pre, air);
            lv.depthTexture = depthCopyTex;
            lightningGl.drawGlow(gl, lv);
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
            gl.glViewport(0, 0, w, h);
            gl.glBindVertexArray(emptyVao);
        }
        gl.glDisable(GL.GL_DEPTH_TEST);
        mark(gl, 9);

        // 9 Nachbearbeitung ins Fenster (mit com.dan.sky ist das Bild schon vorbelichtet)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, defaultFbo);
        gl.glViewport(0, 0, surfW, surfH);
        post.use(gl);
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hdrTex);
        post.set(gl, "uHdr", 0);
        post.set(gl, "uExposure", skyOk ? 1f : (float) exposure());
        post.set(gl, "uScotopic", (float) (skyOk ? scotopic(st) : 0));
        gl.glBindVertexArray(emptyVao);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        mark(gl, 10);

        // 10 HUD
        if (showHud && !kino.laeuft() && captureCaption == null) {   // Phase 15: nicht im Kino und nicht auf den Fotos des Regiebuchs
            hud.refresh(gl, 250, this::paintHud, frozen);
            hud.draw(gl, overlay, emptyVao, 12, Math.max(0, surfH - hud.height - 12), surfW, surfH);
        }
        String[] wc = warftCard;   // Phase 13: die Warftkarte
        if (wc != null && !kino.laeuft()) {
            if (wc != cardPainted) { card.upload(gl, paintCard(wc)); cardPainted = wc; }
            card.draw(gl, overlay, emptyVao, Math.max(0, surfW - card.width - 12), 12, surfW, surfH);   // unten rechts
        }
        gl.glBindVertexArray(0);
        gl.glUseProgram(0);
        mark(gl, 11);
    }

    // ------------------------------------------------------------------ Phase 3: Licht, Auge, Gewitter

    /**
     * Augenanpassung (wie Haithabu und Glacier): alle 0,2 s die Leuchtdichte im Blickfeld messen (auf dem Prozessor),
     * mit der des ganzen Himmels mitteln und über eine Sekunde glätten (logarithmisch, wie das Auge). Unter Wolken dunkler.
     */
    private void adapt(Sky.State st, double dt) {
        viewTimer -= dt;
        if (viewLum <= 0 || viewTimer <= 0) {
            viewLum = st.viewLuminance(camera.fwd.clone(), new double[]{0, 1, 0}, fovy(), surfW / (double) Math.max(1, surfH));
            landInView = landInView();
            viewLum *= Math.pow(LAND_GAIN, landInView);
            viewTimer = 0.2;
        }
        // unter Wolken dunkler, aber nur zum Teil nachgeführt (Exponent 0,6): ein Gewitter und ein Orkan sollen düster wirken
        double target = Math.sqrt(Math.max(1e-14, st.adaptation) * Math.max(1e-14, viewLum)) * (cloudsOk && showClouds ? Math.pow(clouds.brightnessFactor(), STORM_ADAPT) : 1);
        // Phase 6: unter Wasser ist es dunkel; das Auge stellt sich darauf ein (die Messung kennt nur den Himmel)
        if (underWater()) target *= 0.12;
        if (adaptLum <= 0 || frozen || dt > 0.5) adaptLum = target;
        else adaptLum = Math.exp(Math.log(adaptLum) + (Math.log(target) - Math.log(adaptLum)) * Math.min(1, dt / 1.0));
    }

    /**
     * Wie viele der zwölf Messstrahlen (dasselbe Raster wie {@code Sky.State.viewLuminance}) unter der Kimm auf
     * trockenes Land fallen, als Anteil 0 bis 1 (aus Glacier, dort für das Eis). Jeder Strahl läuft in wachsenden
     * Schritten bis 15 km gegen Gelände und Pegel.
     */
    double landInView() {
        double[] f = camera.fwd, up = {0, 1, 0};
        double[] rt = {f[1] * up[2] - f[2] * up[1], f[2] * up[0] - f[0] * up[2], f[0] * up[1] - f[1] * up[0]};
        double rl = Math.max(1e-9, Math.sqrt(rt[0] * rt[0] + rt[1] * rt[1] + rt[2] * rt[2]));
        rt[0] /= rl; rt[1] /= rl; rt[2] /= rl;
        double[] u = {rt[1] * f[2] - rt[2] * f[1], rt[2] * f[0] - rt[0] * f[2], rt[0] * f[1] - rt[1] * f[0]};
        double ty = Math.tan(Math.toRadians(fovy()) / 2), tx = ty * surfW / (double) Math.max(1, surfH), lv = level();
        int land = 0;
        for (int j = 0; j < 3; j++)
            for (int i = 0; i < 4; i++) {
                double sx = ((i + 0.5) / 4 * 2 - 1) * tx * 0.8, sy = ((j + 0.5) / 3 * 2 - 1) * ty * 0.8;
                double dx = f[0] + rt[0] * sx + u[0] * sy, dy = f[1] + rt[1] * sx + u[1] * sy, dz = f[2] + rt[2] * sx + u[2] * sy;
                double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx /= dl; dy /= dl; dz /= dl;
                if (dy >= 0) continue;
                double t = 1, step = 2;
                while (t < 15000) {
                    double x = camera.eye[0] + dx * t, y = camera.eye[1] + dy * t, z = camera.eye[2] + dz * t;
                    double g = terrain.height(x, z), wl = terrain.seaward(x, z) ? lv : -1e9;
                    if (y <= Math.max(g, wl)) {
                        if (g > wl + 0.05) land++;
                        break;
                    }
                    t += step;
                    step = Math.min(120, step * 1.25);
                }
            }
        return land / 12.0;
    }

    /** Das Gewitter in Szenenzeit weiter, Sichtbarkeit neu nach großen Sprüngen des Auges, Nachgeben der Belichtung. */
    private void stepLightning(double dt) {
        double sceneDt = dt * Math.max(0, clock.lapse);
        int before = lightning.flashes();
        lightning.step(Math.min(sceneDt, 1.0));
        double[] e = camera.eye;
        if (lightningSeenFrom == null || Math.hypot(e[0] - lightningSeenFrom[0], e[2] - lightningSeenFrom[2]) > 300 || Math.abs(e[1] - lightningSeenFrom[1]) > 100) {
            lightning.updateVisibility();
            if (lightningOk) lightningGl.invalidate();
            lightningSeenFrom = e.clone();
        }
        if (lightning.flashes() != before && !lightning.bolts().isEmpty()) lastBoltText = boltText(lightning.bolts().get(lightning.bolts().size() - 1));
        double base = exposure() * flashGive;
        double lf = showLightning ? lightning.snapshot().flashLuminance(e[0], e[1], e[2], lightning.cloudBase) : 0;
        double give = Math.min(FLASH_GIVE_MAX, 1 + base * lf / 1.5);
        flashGive = frozen ? give : Math.max(give, Math.min(FLASH_GIVE_MAX, flashGive) * Math.exp(-Math.max(0, dt) / 0.25));
    }

    /** „Erdblitz 3,4 km NW, 31 kA“ */
    private String boltText(Bolt b) {
        double[] e = camera.eye;
        double d = Math.hypot(b.hitX - e[0], b.hitZ - e[2]), az = (Math.toDegrees(Math.atan2(b.hitX - e[0], -(b.hitZ - e[2]))) + 360) % 360;
        String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        String kind = switch (b.kind) {
            case CLOUD_GROUND -> "Erdblitz";
            case INTRA_CLOUD -> "Wolkenblitz";
            case SPIDER -> "Spinnenblitz";
            case SHEET -> "Wetterleuchten";
        };
        double ka = 0;
        for (double k : b.strokeKA) ka = Math.max(ka, k);
        return String.format(Locale.GERMAN, "%s %.1f km %s, %.0f kA", kind, d / 1000, dirs[(int) Math.round(az / 45) % 8], ka);
    }

    /** Auslöschung und Streuung der Luft für das Licht der Blitze (Dunst, Regen, Nebel am Auge), 1/m. */
    private double[] lightningAir() {
        double haze = wiring != null ? wiring.hazePerM() : 6.7e-5;
        double[] e = camera.eye;
        double r = rainOk && showRain ? rain.scatter(e[0], e[1], e[2]) * 3e-3 : 0;
        double f = fogOk && showFog ? fog.density(e[0], e[1], e[2]) : 0;
        return new double[]{haze + r + f, haze * 0.9 + r * 0.98 + f};
    }

    /** Einen Blitz von Hand auslösen (Bedienfeld): Erdblitz in 1,5 bis 4 km in Blickrichtung. */
    public Bolt triggerLightning(Bolt.Kind kind) {
        if (wiring != null) wiring.feed(lightning, clouds.snapshot(), cloudsOk && showClouds, camera.eye);
        double[] e = camera.eye, f = camera.fwd;
        double h = Math.hypot(f[0], f[2]), fx = h > 1e-6 ? f[0] / h : 0, fz = h > 1e-6 ? f[2] / h : -1, r = 1500 + Math.random() * 2500;
        return kind == Bolt.Kind.CLOUD_GROUND ? lightning.strike(e[0] + fx * r, e[2] + fz * r) : lightning.discharge(kind, e[0] + fx * r, e[2] + fz * r);
    }

    static boolean sunIsKey(Sky.State st) { return st.sunAlt > -4; }

    static double[] keyDir(Sky.State st) { return sunIsKey(st) ? st.sunDirTrue : st.moonDirTrue; }

    /** Licht für die Wolken aus dem Himmelszustand, vorbelichtet (wie Haithabu); von unten Watt, Wasser und Wiesen. */
    CloudGl.Light cloudLight(Sky.State st, double pre) {
        CloudGl.Light l = new CloudGl.Light();
        boolean sunKey = sunIsKey(st);
        l.dir = keyDir(st);
        for (int i = 0; i < 8; i++) l.colorAt[i] = scale(sunKey ? st.sunColorAt(CloudGl.LIGHT_HEIGHTS[i]) : st.moonColorAt(CloudGl.LIGHT_HEIGHTS[i]), pre);
        double[] e = st.skyIrradiance;
        l.ambTop = scale(e, pre / Math.PI);
        double s = Math.max(0, st.sunDir[1]), m = Math.max(0, st.moonDir[1]);
        l.ambBot = new double[3];
        for (int c = 0; c < 3; c++) l.ambBot[c] = (e[c] + st.sunColor[c] * s + st.moonColor[c] * m) * 0.10 / Math.PI * pre;
        l.haze = st.groundExtinction;
        l.flash = new double[]{st.flashDir[0] * 5000, st.flashDir[1] * 5000, st.flashDir[2] * 5000, st.flash * pre};
        l.flashCount = 0;
        if (lightningOk && showLightning && lightningGl.active()) {
            int n = lightningGl.cloudLightCount();
            for (int i = 0; i < n; i++) {
                double[] c = lightningGl.cloudLight(i);
                for (int k = 0; k < 3; k++) {
                    l.flashPos[i * 4 + k] = (float) c[k];
                    l.flashRgb[i * 4 + k] = (float) (c[3] * Lightning.CHANNEL_RGB[k] * pre);
                }
            }
            l.flashCount = n;
        }
        return l;
    }

    /** Phase 7: Licht für Seehunde und Figur (wie Halme und Boden), der Schlagschatten nach Sonne und Wolken. */
    private SealGl.Light animalLight(Sky.State st, double pre, double[][] L, double[] sl, double[] zen, double[] hor, double[] sun) {
        SealGl.Light l = new SealGl.Light();
        l.sunDir = sun;
        if (skyOk) {
            l.sunColor = L[0];
            l.moonDir = st.moonDir;
            l.moonColor = L[1];
            l.skyTop = L[2];
            l.skyHorizon = L[3];
            l.fogDistance = fogDistance(st);
        } else {
            l.sunColor = sl;
            l.skyTop = zen;
            l.skyHorizon = hor;
            l.fogDistance = FOG_DISTANCE;
        }
        l.sunShadow = Math.max(0, Math.min(1, cloudShade)) * smooth(-0.01, 0.08, sun[1]);
        return l;
    }

    /** Licht für Boden, Halme, Nebel und Regen: {Sonne, Mond, Himmel oben, Himmel am Horizont}, vorbelichtet. */
    private double[][] ambientLight(Sky.State st, double pre) {
        if (skyOk) {
            double[] hor = new double[3];
            for (int i = 0; i < 6; i++) {
                double a = Math.PI * i / 3;
                double[] hh = st.radiance(norm3(new double[]{Math.sin(a), 0.08, -Math.cos(a)}));
                for (int k = 0; k < 3; k++) hor[k] += hh[k] / 6;
            }
            // oben: das Himmelslicht als Bestrahlung/π (der tiefblaue Zenit allein gäbe nur ein Viertel davon)
            return new double[][]{scale(st.sunColor, pre * cloudShade), scale(st.moonColor, pre * cloudShade), scale(st.skyIrradiance, pre / Math.PI * skyDim()), scale(hor, pre)};
        }
        double[] sp = clock.sun();
        return new double[][]{sunLight(sp[0]), {0, 0, 0}, skyZenith(sp[0]), skyHorizon(sp[0])};
    }

    /**
     * Wie viel Himmelslicht unter der Wolkendecke ankommt (1 = klarer Himmel). {@code com.dan.sky} rechnet das
     * Himmelslicht ohne Wolken; unter einer dichten, dunklen Decke (Nimbostratus, Cumulonimbus) kommt nur gut die Hälfte
     * an [G]. Gleiche Form wie {@code Clouds.brightnessFactor}, aber halb so stark (ein Teil des Lichts streut die Wolke
     * selbst nach unten).
     */
    public double skyDim() {
        if (!cloudsOk || !showClouds) return 1;
        double low = Math.pow(Math.max(0, Math.min(8, clouds.oktas)) / 8, 1.3) * (1 - clouds.type.overcastBrightness);
        return 1 - 0.55 * low;
    }

    /** Das stärkste Blitzlicht als Richtung und Bestrahlung am Auge (vorbelichtet), oder null. */
    private double[][] flashLight(double pre) {
        if (!lightningOk || !showLightning || !lightningGl.active() || lightningGl.lightCount() == 0) return null;
        double[] e = camera.eye, dir = null;
        double bestL = 0;
        double[] tot = new double[3];
        for (int i = 0; i < lightningGl.lightCount(); i++) {
            double[] p = lightningGl.lightPos(i), c = lightningGl.lightRgb(i);
            double dx = p[0] - e[0], dy = p[1] - e[1], dz = p[2] - e[2], d2 = dx * dx + dy * dy + dz * dz + p[3] * p[3], d = Math.sqrt(dx * dx + dy * dy + dz * dz + 1);
            double el = (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / d2;
            for (int k = 0; k < 3; k++) tot[k] += c[k] / d2 * pre;
            if (el > bestL) { bestL = el; dir = new double[]{dx / d, dy / d, dz / d}; }
        }
        return dir == null ? null : new double[][]{dir, tot};
    }

    /** Dunstlänge für exp(−d/L) aus der Sichtweite (Koschmieder). */
    static double fogDistance(Sky.State st) { return st.visibilityKm * 1000 / 3.912; }

    static double[] scale(double[] v, double k) { return new double[]{v[0] * k, v[1] * k, v[2] * k}; }

    static double[] norm3(double[] v) { double l = Math.max(1e-12, Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])); return new double[]{v[0] / l, v[1] / l, v[2] / l}; }

    private LightningGl.View lightningView(float[] vp, float[] inv, int w, int h, double pre, double[] air) {
        LightningGl.View v = new LightningGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.fovyDeg = fovy();
        v.targetFbo = hdrFbo;
        v.preExposure = skyOk ? pre : 1;
        v.extinction = air[0];
        v.scatter = air[1];
        v.ceiling = cloudsOk && showClouds ? clouds.type.base : lightning.cloudBase;
        return v;
    }

    /** Die Tiefe der Szene als Kopie (Regen, Nebel und Blitz lesen sie für weiche Ränder und ihre Weglänge). */
    private void copyDepth(GL4 gl, int w, int h) {
        if (depthCopyTex == 0 || depthCopyW != w || depthCopyH != h) {
            Gpu.deleteFbos(gl, depthCopyFbo);
            Gpu.deleteTextures(gl, depthCopyTex);
            depthCopyTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
            depthCopyFbo = Gpu.depthFbo(gl, depthCopyTex);
            depthCopyW = w;
            depthCopyH = h;
        }
        gl.glBindFramebuffer(GL4.GL_READ_FRAMEBUFFER, hdrFbo);
        gl.glBindFramebuffer(GL4.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
    }

    /**
     * Phase 17: die Gischt am Deich. Aktiv vom Sturm bis zur Flut in der Marsch, wenn die Kamera höchstens 1,5 km vom Deich
     * entfernt ist: je Sekunde bis zu 12 Brecher an der Seeseite der Krone (600 m beiderseits des nächsten Punkts), umso
     * mehr und höher, je näher der Spiegel der Krone kommt; dazu die Fahnen des Orkans im Kreis um diesen Punkt [G].
     */
    public void stepSpray(double dt) {
        com.dan.rungholt.scene.Mandraenke m = mandraenke;
        boolean on = m != null && wiring != null && wiring.era.blend() > 0.9 && m.stage.ordinal() >= com.dan.rungholt.scene.Mandraenke.Stage.STURM.ordinal()
                && m.stage.ordinal() <= com.dan.rungholt.scene.Mandraenke.Stage.FLUT.ordinal();
        double sea = level();
        double[] p = on ? com.dan.rungholt.scene.Land1362.dikePoint(camera.eye[0], camera.eye[2], 0) : null;
        if (p != null && Math.hypot(camera.eye[0] - p[0], camera.eye[2] - p[1]) > 1500) on = false;
        double reach = sea - (com.dan.rungholt.scene.Land1362.CREST - com.dan.rungholt.scene.Mandraenke.OVERTOP_BELOW_CREST);   // > 0: die Wellen erreichen die Krone
        spray.strength = on ? 1 : 0;
        if (on) {
            final double lv = sea;
            if (spray.sea == null) spray.sea = new com.dan.spray.Sea() {
                @Override public double height(double x, double z) { return level(); }
                @Override public double[] velocity(double x, double z) { return new double[3]; }
                @Override public double level() { return Renderer.this.level(); }
            };
            spray.windSpeed = wiring.wind.speed();
            spray.windFromDeg = wiring.wind.fromDeg();
            spray.waveHeight = Math.max(1, Math.min(6, 2 + reach * 1.5));
            spray.focusX = p[0] + p[4] * 60;
            spray.focusZ = p[1] + p[5] * 60;
            spray.focusRadius = 160;
            double rate = Math.max(0, Math.min(12, 4 + reach * 6));
            sprayTimer += dt * rate;
            java.util.concurrent.ThreadLocalRandom rnd = java.util.concurrent.ThreadLocalRandom.current();
            while (sprayTimer >= 1) {
                sprayTimer -= 1;
                double[] q = com.dan.rungholt.scene.Land1362.dikePoint(camera.eye[0], camera.eye[2], (rnd.nextDouble() * 2 - 1) * 600);
                double off = com.dan.rungholt.scene.Land1362.CREST_HALF + Math.max(1, (com.dan.rungholt.scene.Land1362.CREST - lv) * com.dan.rungholt.scene.Land1362.SEA_SLOPE);
                spray.impact(q[0] + q[4] * off, lv, q[1] + q[5] * off, 6 + rnd.nextDouble() * 30, 6 + rnd.nextDouble() * 6 + Math.max(0, reach) * 3);
                dikeBreakers++;
            }
        } else sprayTimer = 0;
        if (on || !spray.parcels().isEmpty()) spray.step(dt);
    }

    /** Phase 17: die Gischt zeichnen (wie in Glacier: Licht wie der Regen, Tiefe als Kopie). */
    private void drawSpray(GL4 gl, Sky.State st, float[] vp, int w, int h, double pre, double[][] L) {
        sprayGl.update(gl, spray);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        com.dan.spray.gl.SprayGl.View v = new com.dan.spray.gl.SprayGl.View();
        v.viewProj = vp;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        com.dan.spray.gl.SprayGl.Light l = new com.dan.spray.gl.SprayGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        l.moonDir = skyOk ? st.moonDir : new double[]{0, -1, 0};
        l.moonColor = L[1];
        double[][] fl = flashLight(pre);
        if (fl != null && fl[1][1] > l.moonColor[1]) { l.moonDir = fl[0]; l.moonColor = fl[1]; }
        l.skyTop = L[2];
        l.skyHorizon = L[3];
        l.fogDistance = skyOk ? fogDistance(st) : FOG_DISTANCE;
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        sprayGl.draw(gl, v, l, 1);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(emptyVao);
    }

    private void drawRain(GL4 gl, Sky.State st, float[] vp, float[] inv, int w, int h, double pre, double[][] L) {
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        RainGl.View v = new RainGl.View();
        v.viewProj = vp;
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.forward = camera.fwd.clone();
        v.reversedZ = reversedZ;
        v.near = NEAR;
        v.far = FAR;
        v.fovyDeg = fovy();
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        RainGl.Light l = new RainGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        l.moonDir = skyOk ? st.moonDir : new double[]{0, -1, 0};
        l.moonColor = L[1];
        double[][] fl = flashLight(pre);
        if (fl != null && fl[1][1] > l.moonColor[1]) { l.moonDir = fl[0]; l.moonColor = fl[1]; }
        // unter der Wolke: Licht von oben nur, so weit sie es durchlässt
        double under = cloudsOk && showClouds ? Math.min(1, clouds.type.overcastBrightness * 1.6) : 1;
        l.skyTop = skyOk ? scale(st.skyIrradiance, pre / Math.PI * under) : L[2];
        l.skyHorizon = L[3];
        l.fogDistance = skyOk ? fogDistance(st) : FOG_DISTANCE;
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        rainGl.draw(gl, v, l, rainStrength);
        gl.glDisable(GL.GL_BLEND);
        gl.glDepthMask(true);
        gl.glBindVertexArray(emptyVao);
    }

    private void drawFog(GL4 gl, Sky.State st, float[] inv, int w, int h, double[][] L) {
        Fog.Snapshot s = fog.snapshot();
        fogGl.update(gl, s, camera.eye.clone());
        if (fogCheck.isEmpty() && frame > 100) fogCheck = fogGl.selfCheck(gl, fog, camera.eye.clone());
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        FogGl.View v = new FogGl.View();
        v.invViewProj = inv;
        v.eye = camera.eye.clone();
        v.reversedZ = reversedZ;
        v.width = w;
        v.height = h;
        v.depthTexture = depthCopyTex;
        v.jitter = frozen ? 0.5 : -1;
        v.frame = frame;
        FogGl.Light l = new FogGl.Light();
        l.sunDir = skyOk ? st.sunDir : Sun.direction(clock.sun()[0], clock.sun()[1]);
        l.sunColor = L[0];
        if (skyOk) l.moonDir = st.moonDir;
        l.moonColor = L[1];
        l.ambientTop = L[2];
        l.ambientHorizon = L[3];
        fogGl.draw(gl, s, v, l);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, hdrFbo);
        gl.glViewport(0, 0, w, h);
        gl.glBindVertexArray(emptyVao);
    }

    /** Anteil des Stäbchensehens (nachts farblos und bläulich), aus der Anpassung des Himmels. */
    public static double scotopic(Sky.State st) {
        double l = Math.log10(Math.max(1e-12, st.adaptation * Sky.CD_PER_UNIT));
        double t = Math.max(0, Math.min(1, (l - Math.log10(0.003)) / (Math.log10(3) - Math.log10(0.003))));
        return 1 - t * t * (3 - 2 * t);
    }

    /** Die Warft von Südfall (x, z): aus dem Import, sonst aus der Bühne. */
    public double[] warft() {
        try {
            return new double[]{Double.parseDouble(terrain.props.getProperty("warft.x")), Double.parseDouble(terrain.props.getProperty("warft.z"))};
        } catch (RuntimeException e) {
            return new double[]{Buehne.WARFT_X, Buehne.WARFT_Z};
        }
    }

    /** R8-Maske der Köge (1 = hinter dem Deich oder ohne Messwert): dort zeichnet com.dan.ocean kein Meer. */
    static int maskTexture(GL4 gl, Terrain t) {
        int n = Terrain.N;
        byte[] m = new byte[n * n];
        for (int k = 0; k < m.length; k++) {
            byte a = t.art[k];
            m[k] = (byte) (a == Terrain.KOOG || a == Terrain.RAND || a == Terrain.MARSCH || a == Terrain.WARFT || a == Terrain.SIELZUG ? 255 : 0);
        }
        int[] tex = new int[1];
        gl.glGenTextures(1, tex, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R8, n, n);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_UNSIGNED_BYTE, java.nio.ByteBuffer.wrap(m));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return tex[0];
    }

    /** Sonnenlicht des Platzhalter-Himmels (wie sunLight in common.glsl), linear. */
    public static double[] sunLight(double elevDeg) {
        double e = Math.sin(Math.toRadians(elevDeg));
        double vis = smooth(-0.015, 0.01, e), t = smooth(0.02, 0.35, e), f = 2.6 * vis * smooth(-0.02, 0.12, e);
        return new double[]{f, (0.55 + 0.40 * t) * f, (0.28 + 0.60 * t) * f};
    }

    /** Zenit- und Horizontfarbe des Platzhalter-Himmels (wie skyColorPlaceholder in common.glsl, ohne Sonnensaum). */
    public static double[] skyZenith(double elevDeg) { return skyPlaceholder(elevDeg, 1); }
    public static double[] skyHorizon(double elevDeg) { return skyPlaceholder(elevDeg, 0); }

    static double[] skyPlaceholder(double e, double up) {
        double b = Sun.skyBrightness(e);
        double day = smooth(-2, 12, e), night = 1 - smooth(-16, -8, e), tw = Math.max(0, Math.min(1, 1 - day - night));
        double hw = Math.pow(1 - up, 4);
        double[] zen = {0.16 * day + 0.10 * tw + 0.05 * night, 0.34 * day + 0.17 * tw + 0.08 * night, 0.72 * day + 0.42 * tw + 0.20 * night};
        double[] hor = {0.62 * day + 0.6 * tw + 0.07 * night, 0.74 * day + 0.4 * tw + 0.09 * night, 0.88 * day + 0.36 * tw + 0.18 * night};
        double[] c = new double[3];
        for (int i = 0; i < 3; i++) c[i] = (zen[i] + (hor[i] - zen[i]) * hw) * b / 0.32;
        return c;
    }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    private void common(GL4 gl, Shader s, float[] vp, float[] inv, double[] sun, double level) {
        s.use(gl);
        // Phase 3: mit com.dan.sky Himmel aus dem Rundumbild, Sonne und Mond vorbelichtet, Dunst aus der Sicht
        boolean sm = skyOk && s != skyShader && s != skyPano;
        s.set(gl, "uSkyMode", sm ? 1 : 0);
        if (sm) {
            Sky.State st = skyState();
            double pre = exposure();
            gl.glActiveTexture(GL.GL_TEXTURE7);
            gl.glBindTexture(GL.GL_TEXTURE_2D, panoTex);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            s.set(gl, "uPano", 7);
            s.set(gl, "uSunColor", scale(st.sunColor, pre));
            s.set(gl, "uMoonColor", scale(st.moonColor, pre));
            s.set(gl, "uMoonDir", st.moonDir);
            s.set(gl, "uFogDist", (float) fogDistance(st));
            s.set(gl, "uPre", (float) pre);
            s.set(gl, "uSkyAmb", scale(st.skyIrradiance, pre / Math.PI * skyDim()));
        }
        s.mat(gl, "uVP", vp);
        s.mat(gl, "uInvVP", inv);
        s.set(gl, "uCam", camera.eye);
        s.set(gl, "uSun", sun);
        s.set(gl, "uTime", (float) (clock.t() % 10_000));
        s.set(gl, "uLevel", (float) level);
        s.set(gl, "uReversed", reversedZ ? 1 : 0);
    }

    private void resizeTargets(GL4 gl, int w, int h) {
        Gpu.deleteFbos(gl, hdrFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        hdrTex = Gpu.texture(gl, w, h, GL.GL_RGBA16F, false);
        depthTex = Gpu.texture(gl, w, h, GL4.GL_DEPTH_COMPONENT32F, false);
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        hdrFbo = Gpu.fbo(gl, hdrTex, depthTex);
        hdrW = w;
        hdrH = h;
    }

    // ------------------------------------------------------------------ Zeitmessung

    private void mark(GL4 gl, int i) { gl.glQueryCounter(stamp[frame & 1][i], GL4.GL_TIMESTAMP); }

    /** Liest die Zeitstempel des vorigen Bilds, sobald sie da sind (kein Warten im Bildlauf). */
    private void readMarks(GL4 gl) {
        if (frame < 2) return;
        int o = (frame - 1) & 1;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamp[o][MARKS - 1], GL4.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[MARKS];
        for (int i = 0; i < MARKS; i++) gl.glGetQueryObjecti64v(stamp[o][i], GL4.GL_QUERY_RESULT, t, i);
        for (int i = 0; i < MARKS - 1; i++) passMs[i] = passMs[i] * 0.9 + Math.max(0, t[i + 1] - t[i]) / 1e6 * 0.1;
        gpuMs = gpuMs * 0.9 + Math.max(0, t[MARKS - 1] - t[0]) / 1e6 * 0.1;
    }

    /** „Himmel 0,10 · Bühne 0,60 · …“ in ms (gleitendes Mittel). */
    public String passTimes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < passMs.length; i++)
            sb.append(i > 0 ? " · " : "").append(PASSES[i]).append(' ').append(String.format(Locale.GERMAN, "%.2f", passMs[i]));
        return sb.toString();
    }

    private void stats(long now, int w, int h) {
        statsFrames++;
        if (now - statsNanos < 500_000_000L) return;
        fps = statsFrames / ((now - statsNanos) / 1e9);
        statsFrames = 0;
        statsNanos = now;
        statusListener.accept(statusLine(w, h));
    }

    /** Auflösung nach der Zeit der Grafikkarte regeln (siehe {@link #autoScale}). */
    private void regulateScale(long now) {
        if (!autoScale || frame < 60 || now - scaleNanos < 1_500_000_000L) return;
        scaleNanos = now;
        double budget = 1000 / Math.max(10, targetFps), top = quality.renderScale, s = renderScale;
        double want = s * Math.sqrt(budget * 0.9 / Math.max(0.1, gpuMs));
        double next = s;
        if (gpuMs > budget * 1.05) next = Math.max(MIN_SCALE, Math.max(want, s - 0.15));
        else if (gpuMs < budget * 0.7 && s < top) next = Math.min(top, Math.min(want, s + 0.05));
        next = Math.round(next * 20) / 20.0;
        if (Math.abs(next - s) >= 0.045) { renderScale = next; scaleSteps++; }
    }

    /** Wie oft die Regelung die Auflösung geändert hat. */
    public int scaleSteps() { return scaleSteps; }

    /** Für Prüfungen: die Regelung mit vorgegebener Grafikkartenzeit und Bildnummer. */
    public void regulateForTest(double gpuMsNow, long nowNanos) {
        gpuMs = gpuMsNow;
        frame = Math.max(frame, 60);
        regulateScale(nowNanos);
    }

    public String statusLine(int w, int h) {
        String s = String.format(Locale.GERMAN, "%s · %.0f Bilder/s · Grafikkarte %.2f ms · Prozessor %.2f ms · %d×%d (%s%s) · %s",
                clock.label(), fps, gpuMs, cpuMs, w, h, quality.label, autoScale ? String.format(Locale.GERMAN, ", %.0f %%", renderScale * 100) : "", glInfo);
        if (System.currentTimeMillis() < photoMsgUntil) s = photoMsg + " · " + s;
        return s;
    }

    // ------------------------------------------------------------------ HUD

    BufferedImage paintHud() {
        String[] lines = hudFull ? hudLines() : hudShort();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 12);
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        int tw = 0;
        for (String l : lines) tw = Math.max(tw, fm.stringWidth(l.startsWith("!") ? l.substring(1) : l));
        pg.dispose();
        int w = Math.max(200, tw + 22), lh = 17, h = 16 + lines.length * lh;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(11, 18, 25, 180));
        g.fillRoundRect(0, 0, w - 1, h - 1, 10, 10);
        g.setColor(new Color(138, 211, 236, 220));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(8, 1, 60, 1);
        g.setFont(font);
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean warn = l.startsWith("!");
            if (warn) l = l.substring(1);
            g.setColor(i == 0 ? new Color(241, 246, 249) : warn ? new Color(224, 163, 63) : new Color(185, 200, 210));
            g.drawString(l, 10, 18 + i * lh);
        }
        g.dispose();
        return im;
    }

    /** Phase 13: die Warftkarte malen: erste Zeile Titel, Zeilen mit „–“ eingerückt, „#“ als Überschrift. */
    BufferedImage paintCard(String[] lines) {
        Font title = new Font(Font.SERIF, Font.PLAIN, 20), font = new Font(Font.SANS_SERIF, Font.PLAIN, 13), head = new Font(Font.SANS_SERIF, Font.BOLD, 11);
        int w = 430, lh = 18;
        java.util.List<String> wrapped = new java.util.ArrayList<>();
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        java.awt.FontMetrics fm = pg.getFontMetrics(font);
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i];
            if (l.startsWith("#")) { wrapped.add(l); continue; }
            String ind = l.startsWith("–") ? "   " : "";
            StringBuilder cur = new StringBuilder();
            for (String word : l.split(" ")) {
                if (cur.length() > 0 && fm.stringWidth(cur + " " + word) > w - 36) { wrapped.add(cur.toString()); cur = new StringBuilder(ind); }
                if (cur.length() > 0 && !cur.toString().equals(ind)) cur.append(' ');
                cur.append(word);
            }
            wrapped.add(cur.toString());
        }
        pg.dispose();
        int h = 52 + wrapped.size() * lh + 14;
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = im.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(14, 20, 22, 225));
        g.fillRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(new Color(91, 167, 179));
        g.fillRect(0, 0, w, 3);
        g.setFont(title);
        g.setColor(new Color(232, 236, 234));
        g.drawString(lines[0], 14, 32);
        int y = 56;
        for (String l : wrapped) {
            if (l.startsWith("#")) { g.setFont(head); g.setColor(new Color(91, 167, 179)); g.drawString(l.substring(1).toUpperCase(Locale.GERMAN), 14, y + 4); }
            else { g.setFont(font); g.setColor(new Color(200, 210, 208)); g.drawString(l, 14, y); }
            y += lh;
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
        g.setColor(new Color(146, 163, 163));
        g.drawString("Klick schließt", w - 100, h - 8);
        g.dispose();
        return im;
    }

    /** Phase 16: wo der letzte Klick den Grund traf (x, y, z), für Zellkarte und Fundstelle; null: Himmel. */
    public volatile double[] lastHit;
    /** Phase 16: die Warft unter dem letzten Klick (Warftkarte) oder null (Zellkarte). */
    public volatile com.dan.rungholt.scene.Land1362.Warft lastWarft;

    /**
     * Phase 13/16: ein Klick ins Bild (Bildpunkt x, y bei w × h). Ist eine Karte offen, schließt er sie. Sonst trifft der
     * Strahl den Grund: liegt dort eine Warft (1362) oder ihre Spur (heute), öffnet sich die Warftkarte, sonst die
     * Zellkarte des Watts (Phase 16); die App hängt an beide an, was die Datenbank weiß. Gibt true, wenn eine Karte offen ist.
     */
    public boolean clickAt(int px, int py, int w, int h) {
        if (warftCard != null) { warftCard = null; lastWarft = null; return false; }
        double[] hit = pickGround(px, py, w, h);
        lastHit = hit;
        if (hit == null) { lastWarft = null; return false; }
        com.dan.rungholt.scene.Land1362.Warft wf = com.dan.rungholt.scene.Spuren.warftAt(hit[0], hit[2]);
        lastWarft = wf;
        warftCard = wf != null ? warftCard(wf) : zellCard(hit[0], hit[2]);
        return true;
    }

    /** Phase 16: die Zeilen der Zellkarte an (x, z): Grund heute und 1362, Wasser, Trockenzeit, Strömung, die nächsten Wenden am Rand. */
    public String[] zellCard(double x, double z) {
        java.util.List<String> l = new java.util.ArrayList<>();
        double[] ll = Site.latLon(x, z);
        l.add("Watt bei " + Site.dms(ll[0], ll[1]));
        l.add(String.format(Locale.GERMAN, "%.0f m %s, %.0f m %s der Kirche von Rungholt · Zelle %.0f × %.0f m", Math.abs(x), x < 0 ? "westlich" : "östlich", Math.abs(z),
                z < 0 ? "nördlich" : "südlich", Terrain.CELL, Terrain.CELL));
        l.add("#heute");
        byte art = terrain.artAt(x, z);
        double g = terrain.height(x, z);
        Flood f = flood;
        double depth = wiring == null ? 0 : wiring.level.depthAt(x, z);
        String wasser = depth > 0.02 ? String.format(Locale.GERMAN, "%.2f m Wasser", depth)
                : f != null && f.inside(x, z) ? String.format(Locale.GERMAN, "trocken seit %.1f h", f.dryHoursAt(x, z)) : "trocken";
        l.add(String.format(Locale.GERMAN, "%s · Grund NHN %+.2f m · %s", Terrain.ART[Math.max(0, Math.min(Terrain.ART.length - 1, art))], g, wasser));
        if (f != null && f.inside(x, z)) {
            double[] v = f.velocityAt(x, z);
            double sp = Math.hypot(v[0], v[1]);
            l.add(String.format(Locale.GERMAN, "Strömung %.2f m/s%s · bei mittlerem Niedrigwasser (NHN %+.1f m) %s", sp,
                    sp > 0.05 ? String.format(Locale.GERMAN, " nach %03.0f°", (Math.toDegrees(Math.atan2(v[0], -v[1])) + 360) % 360) : "", Site.MTNW,
                    g > Site.MTNW ? "fällt die Stelle trocken" : "bleibt sie nass"));
        }
        l.add("#1362");
        Terrain l1362 = land1362;
        double g1362 = l1362 != null ? l1362.height(x, z) : com.dan.rungholt.scene.Land1362.estimate(x, z);
        double side = com.dan.rungholt.scene.Land1362.dikeSide(x, z);
        l.add(String.format(Locale.GERMAN, "%s · Grund NHN %+.2f m%s", side > 0 ? "Marsch hinter dem Seedeich" : "Vorland und Watt vor dem Seedeich", g1362,
                l1362 == null ? " (geschätzt, das Land von 1362 ist noch nicht gebaut)" : ""));
        if (wiring != null) {
            l.add("#Tide am Rand (Norderhever)");
            double hh = wiring.level.hours();
            com.dan.tide.Tide.Turn hw = wiring.level.tide.next(hh, true), nw = wiring.level.tide.next(hh, false);
            java.time.ZoneId zone = com.dan.rungholt.scene.SceneClock.ZONE;
            l.add((nw != null ? com.dan.tide.Tide.format(nw, zone) : "") + (hw != null ? " · " + com.dan.tide.Tide.format(hw, zone) : ""));
        }
        return l.toArray(new String[0]);
    }

    /** Phase 16: an die offene Karte Zeilen anhängen (aus dem Faden der Daten); nur, wenn sie noch dieselbe ist. */
    public void cardAppend(String[] was, java.util.List<String> mehr) {
        if (warftCard != was || mehr.isEmpty()) return;
        java.util.List<String> l = new java.util.ArrayList<>(java.util.List.of(was));
        l.addAll(mehr);
        warftCard = l.toArray(new String[0]);
    }

    /** Der Punkt auf dem Grund unter dem Bildpunkt (x, y, z) oder null (Himmel). */
    public double[] pickGround(int px, int py, int w, int h) {
        double[] f = camera.fwd, e = camera.eye;
        double rx = -f[2], rz = f[0], rl = Math.hypot(rx, rz);
        if (rl < 1e-9) { rx = 1; rz = 0; rl = 1; }
        rx /= rl; rz /= rl;
        double ux = -rz * f[1], uy = rz * f[0] - rx * f[2], uz = rx * f[1];   // oben = rechts × vorn
        double th = Math.tan(Math.toRadians(fovy()) / 2), asp = w / (double) Math.max(1, h);
        double nx = 2.0 * (px + 0.5) / w - 1, ny = 1 - 2.0 * (py + 0.5) / h;
        double dx = f[0] + rx * nx * th * asp + ux * ny * th, dy = f[1] + ny * th * uy, dz = f[2] + rz * nx * th * asp + uz * ny * th;
        double dl = Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= dl; dy /= dl; dz /= dl;
        double t0 = 0, t = 0.5;
        while (t < 25_000) {
            double x = e[0] + dx * t, y = e[1] + dy * t, z = e[2] + dz * t;
            if (y <= groundHeight(x, z)) {
                for (int k = 0; k < 24; k++) {   // halbieren
                    double tm = (t0 + t) / 2, xm = e[0] + dx * tm, zm = e[2] + dz * tm;
                    if (e[1] + dy * tm <= groundHeight(xm, zm)) t = tm; else t0 = tm;
                }
                return new double[]{e[0] + dx * t, e[1] + dy * t, e[2] + dz * t};
            }
            t0 = t;
            t += Math.max(0.5, t * 0.01);
        }
        return null;
    }

    /** Phase 13: die Zeilen der Warftkarte (Titel zuerst). */
    public String[] warftCard(com.dan.rungholt.scene.Land1362.Warft w) {
        java.util.List<String> l = new java.util.ArrayList<>();
        boolean kirche = w.name().startsWith("Kirchwarft"), kette = w.name().startsWith("Warft ");
        l.add(w.name() + " [G]");
        l.add((kirche ? "Kirchwarft in der Mitte der Kette" : kette ? "Warft der Kette, " + Math.round(w.x()) + " m " + (w.x() < 0 ? "westlich" : "östlich") + " der Kirche" : "Hofwarft in der Marsch")
                + String.format(Locale.GERMAN, " · %.0f m von der Kirche", Math.hypot(w.x(), w.z())));
        l.add("#1362");
        l.add(String.format(Locale.GERMAN, "Krone NHN %+.1f m, die Marsch daneben NHN %+.1f m · Krone %.0f × %.0f m, Fuß gut %.0f m · Achse %03.0f°",
                w.top(), com.dan.rungholt.scene.Land1362.marsh(w.x(), w.z()), 2 * w.a(), 2 * w.b(), 2 * w.radius(), (w.headingDeg() % 180 + 180) % 180));
        Town tw = town;
        if (tw != null) {
            int n = 0;
            java.util.Map<String, Integer> types = new java.util.TreeMap<>();
            for (com.dan.timber.House hs : tw.houses) {
                if (Math.hypot(hs.x() - w.x(), hs.z() - w.z()) > w.radius()) continue;
                n++;
                types.merge(hs.type().name().charAt(0) + hs.type().name().substring(1).toLowerCase(Locale.GERMAN), 1, Integer::sum);
            }
            StringBuilder sb = new StringBuilder((kirche ? "Kirche aus Backstein (41 m, Turm 33 m) und " : "") + n + (n == 1 ? " Haus" : " Häuser"));
            if (!types.isEmpty()) sb.append(": ").append(types.toString().replace("{", "").replace("}", "").replace("=", " "));
            l.add(sb.toString());
        } else l.add("Häuser: die Stadt ist noch nicht gebaut (Taste Z)");
        int wells = 0;
        for (double[] p : com.dan.rungholt.scene.Spuren.wells()) if (Math.hypot(p[0] - w.x(), p[1] - w.z()) < w.radius()) wells++;
        l.add(wells == 0 ? "kein Brunnen" : wells == 1 ? "ein Sodenbrunnen" : wells + " Sodenbrunnen");
        l.add("#heute");
        double g = terrain.height(w.x(), w.z());
        com.dan.rungholt.scene.Spuren sp = spuren;
        String spur = sp == null ? "Spuren noch nicht gebaut" : String.format(Locale.GERMAN, "Kleirücken %.0f cm", 100 * sp.height(w.x(), w.z()));
        double depth = wiring == null || wiring.era.is1362() ? Double.NaN : wiring.level.depthAt(w.x(), w.z());
        l.add(String.format(Locale.GERMAN, "Abdruck im Watt: Grund NHN %+.2f m · %s · %s", g, spur,
                Double.isNaN(depth) ? "Wasser: in 1362 nicht gerechnet" : depth > 0.02 ? String.format(Locale.GERMAN, "%.1f m Wasser darüber", depth) : "trocken"));
        if (sp != null) {
            double mg = sp.magnetik(w.x(), w.z());
            l.add(Double.isNaN(mg) ? "außerhalb der Messfläche" : String.format(Locale.GERMAN, "Magnetik in der Mitte %+.1f nT [G]", mg));
        }
        l.add("#Fundstellen in 100 m");
        java.util.List<com.dan.rungholt.scene.Spuren.Fund> fs = com.dan.rungholt.scene.Spuren.findsNear(w.x(), w.z(), 100);
        if (fs.isEmpty()) l.add("keine");
        for (com.dan.rungholt.scene.Spuren.Fund f : fs) l.add("– " + f.name() + " (" + f.year() + ", " + f.source() + ")");
        l.add("#Herkunft");
        l.add("Belegt: 54 Warften, eine Kette von 2 km, die Kirche 40 × 15 m (Magnetik 2023). Lage, Höhe und Name dieser Warft sind gestaltet [G].");
        return l.toArray(new String[0]);
    }

    /** Das kurze HUD: Tempo, Zeit und Wind, Kamera. Meldungen mit ! aus den vollen Zeilen kommen dazu. */
    String[] hudShort() {
        java.util.List<String> l = new java.util.ArrayList<>();
        String q = autoScale ? String.format(Locale.GERMAN, "%s %.0f %%", quality.label, renderScale * 100) : quality.label;
        l.add(String.format(Locale.GERMAN, "%.0f Bilder/s · Grafikkarte %.1f ms · Prozessor %.1f ms · %s %d×%d", fps, gpuMs, cpuMs, q, hdrW, hdrH));
        l.add(String.format(Locale.GERMAN, "%s · Sonne %.0f° · %s · %s", clock.label(), clock.sun()[0], windLine(), eraLine()));
        l.add(shortWeather());
        l.add(levelLine());
        if (flood != null) l.add(waterLine());
        if (wetField != null && land().artAt(camera.eye[0], camera.eye[2]) == Terrain.WATT) l.add(mudLine());
        if (flood != null && showSeals && (Math.hypot(camera.eye[0] - Seehundbank.X, camera.eye[2] - Seehundbank.Z) < 2500 || walker.active)) l.add(sealLine());
        if (flood != null && showBirds) l.add(birdLine());
        if (hike != null) l.add(hikeLine());
        if (town != null && wiring != null && wiring.era.is1362()) l.add(townLine());
        if (mandraenke != null) l.add(mandraenkeLine());
        if ((grabung.present() && !(wiring != null && wiring.era.is1362())) || magBlend > 0.01 || warftCard != null) l.add(spurenLine());   // Phase 13
        if (glocken.condition || glocken.ghost > 0.01) l.add(glockenLine());
        if (klang.on) l.add(klang.describe());   // Phase 14
        if (regie.running() || !regieMsg.isEmpty() && regie.book() != null) l.add(regieLine());   // Phase 15
        l.add(String.format(Locale.GERMAN, "%s · Auge %.0f m · Blick %03.0f° · %.0f m von der Kirche [G] · %s · H: volles HUD, F1: Tasten",
                camera.modeName(), camera.eye[1], camera.heading(), camera.originDistance(), zone()));
        com.dan.rungholt.camera.Fahrt fa = camera.fahrt;
        if (fa != null) l.add("Fahrt " + fa.describe() + " · Pfeiltaste oder Maus: anhalten");
        for (String s : hudLines()) if (s.startsWith("!")) l.add(s);
        if (System.currentTimeMillis() < photoMsgUntil) l.add(photoMsg);
        return l.toArray(new String[0]);
    }

    /** „Himmel com.dan.sky · Sonne 58,1° · Mond 45 % · Sicht 30 km · Belichtung …“ */
    public String skyLine() {
        if (!skyOk) return "!Himmel: einfacher Verlauf (com.dan.sky nicht gestartet)";
        Sky.State st = skyState();
        return String.format(Locale.GERMAN, "Himmel %s · Sonne %.1f° · Mond %.0f %% · Sicht %.0f km · Belichtung %.3g · Stäbchen %.0f %%%s", skyGl.info(), st.sunAlt,
                st.moonFraction * 100, st.visibilityKm, exposure(), scotopic(st) * 100, skyCheck.isEmpty() ? "" : " · " + skyCheck);
    }

    public String cloudLine() {
        if (!cloudsOk) return "!Wolken: com.dan.cloud nicht gestartet";
        return String.format(Locale.GERMAN, "Wolken %s · Sonne am Auge %.0f %% · %s%s", clouds.describe(), cloudShade * 100, cloudGl.info(), cloudCheck.isEmpty() ? "" : " · " + cloudCheck);
    }

    /** „Wind Bft 7 aus 285° … · 12,0 °C · 998 hPa · Sicht 8 km · Regen 4,0 mm/h · Nebel 0 %“ */
    public String weatherLine() {
        if (wiring == null) return "Wetter —";
        return wiring.weatherLine() + String.format(Locale.GERMAN, " · %.1f °C · %.0f hPa · Sicht %.0f km · Regen %.1f mm/h · Nebel %.0f %%",
                wiring.airTempC, wiring.pressureHPa, wiring.visibilityKm, wiring.rainRate, wiring.fogAmount * 100);
    }

    public String lightningLine() {
        if (!lightningOk) return "!Gewitter: com.dan.lightning nicht gestartet";
        if (lightning.level() == Lightning.Level.OFF && lightning.flashes() == 0) return "Gewitter: keines";
        return String.format(Locale.GERMAN, "Gewitter %s · %d Blitze, %d Erdblitze%s%s", lightning.level().name().toLowerCase(Locale.ROOT), lightning.flashes(),
                lightning.groundFlashes(), lastBoltText.isEmpty() ? "" : " · zuletzt " + lastBoltText, lightningCheck.isEmpty() ? "" : " · " + lightningCheck);
    }

    /** „Sturm · 7/8 Stratus · Regen 6,0 mm/h · Sicht 6 km · Gewitter nah“ (kurzes HUD). */
    public String shortWeatherLine() { return shortWeather(); }

    String shortWeather() {
        if (wiring == null) return "Wetter —";
        com.dan.weather.Weather.Snapshot s = wiring.weather.snapshot();
        String lage = !wiring.regie ? "Wetter von Hand" : s == null ? "Regie startet" : s.phase().label;
        StringBuilder b = new StringBuilder(lage);
        b.append(String.format(Locale.GERMAN, " · %.0f/8 %s", wiring.oktas, wiring.cloudType.label));
        if (wiring.rainRate > 0.05) b.append(String.format(Locale.GERMAN, " · Regen %.1f mm/h", wiring.rainRate));
        b.append(String.format(Locale.GERMAN, " · Sicht %.0f km", wiring.visibilityKm));
        if (wiring.fogAmount > 0.05) b.append(String.format(Locale.GERMAN, " · Seenebel %.0f %%", wiring.fogAmount * 100));
        if (wiring.thunderstorm != Lightning.Level.OFF) b.append(" · Gewitter ").append(wiring.thunderstorm.name().toLowerCase(Locale.ROOT));
        if (skyOk) b.append(String.format(Locale.GERMAN, " · Belichtung %.3g", exposure()));
        return b.toString();
    }

    String windLine() {
        if (wiring == null) return "Wind —";
        var w = wiring.wind;
        return String.format(Locale.GERMAN, "Wind %.1f m/s (Bft %d) aus %03.0f°", w.speed(), w.beaufort(), w.fromDeg());
    }

    /** „Pegel NHN −1,52 m · Ebbe · Hochwasser 19:51 +1,62 m · Spring in 3 Tagen · Windstau +0,40 m“ */
    public String levelLine() {
        if (wiring == null) return String.format(Locale.GERMAN, "Pegel NHN %+.2f m", level());
        boolean hand = !Double.isNaN(wiring.level.hand);
        if (hand) return String.format(Locale.GERMAN, "Pegel NHN %+.2f m · von Hand · Norderhever", level());
        Tide t = wiring.level.tide;
        double h = wiring.level.hours();
        Tide.Turn hw = t.next(h, true), nw = t.next(h, false);
        double sp = (t.nextSpring(h) - h) / 24, f = t.springFactor(h);
        String spring = f > 1.15 ? "Springtide" : f < 0.85 ? "Nipptide" : sp < 1 ? "Springtide morgen" : String.format(Locale.GERMAN, "Spring in %.0f Tagen", sp);
        return String.format(Locale.GERMAN, "Pegel NHN %+.2f m · %s · %s · %s · %s%s", level(), wiring.level.rising() ? "Flut" : "Ebbe",
                hw == null ? "—" : turnShort(hw), nw == null ? "—" : turnShort(nw), spring,
                Math.abs(t.surge) > 0.05 ? String.format(Locale.GERMAN, " · Windstau %+.2f m", t.surge) : "");
    }

    /** „HW 19:51 +1,62“ in Szenenzeit (MEZ/MESZ). */
    String turnShort(Tide.Turn x) {
        java.time.ZonedDateTime z = x.instant().atZone(SceneClock.ZONE);
        return String.format(Locale.GERMAN, "%s %d:%02d %+.2f", x.high() ? "HW" : "NW", z.getHour(), z.getMinute(), x.level() + wiring.level.tide.surge);
    }

    /** „Wasser hier 1,2 m, mit dem Meer verbunden · Strömung 0,4 m/s nach NO · trocken seit 2,1 h“ */
    public String waterLine() {
        if (wiring == null) return "Wasser —";
        Flood f = flood;
        double x = camera.eye[0], z = camera.eye[2];
        if (f == null) return (tideStatus.startsWith("!") ? "" : "") + tideStatus;
        double d = f.depthAt(x, z);
        double[] u = f.velocityAt(x, z);
        double sp = Math.hypot(u[0], u[1]), az = (Math.toDegrees(Math.atan2(u[0], -u[1])) + 360) % 360;
        String[] dirs = {"N", "NO", "O", "SO", "S", "SW", "W", "NW"};
        String here = d > 0.01 ? String.format(Locale.GERMAN, "Wasser hier %.2f m%s", d, f.connectedAt(x, z) ? "" : " (Pfütze)")
                : String.format(Locale.GERMAN, "hier trocken seit %.1f h", f.dryHoursAt(x, z));
        return here + (sp > 0.02 ? String.format(Locale.GERMAN, " · Strömung %.2f m/s nach %s", sp, dirs[(int) Math.round(az / 45) % 8]) : "")
                + String.format(Locale.GERMAN, " · Laufzeit vom Rand %.0f min", f.travelAt(x, z) / 60);
    }

    /** „Rungholt-Watt“, „Südfall“, „Nordstrand“ … für den Ort der Kamera (Namen aus der Bühne, Art aus dem Gelände). */
    public String zone() {
        double x = camera.eye[0], z = camera.eye[2];
        String n = Buehne.zone(x, z);
        if (wiring != null && wiring.era.is1362() && land1362 != null) {   // Phase 10
            byte b = land1362.artAt(x, z);
            if (b == Terrain.WARFT) return Math.hypot(x, z) < 120 ? "Kirchwarft von Rungholt" : "Warft (1362)";
            if (b == Terrain.MARSCH) return com.dan.rungholt.scene.Land1362.chainDist(x, z) < 300 ? "Rungholt, an der Warftenkette" : "Marsch der Edomsharde";
            if (b == Terrain.DEICH1362) return "Seedeich (1362)";
            if (b == Terrain.SIELZUG) return "Sielzug";
            if (b == Terrain.VORLAND) return "Vorland vor dem Seedeich";
            return land1362.height(x, z) < -2.5 ? "Hever" : "Watt vor dem Deich";
        }
        byte a = terrain.artAt(x, z);
        if (a == Terrain.KOOG || a == Terrain.RAND) return n.equals("Pellworm") || n.equals("Nordstrand") ? n + ", Koog" : "Koog";
        if (a == Terrain.ERHOEHT) return Math.hypot(x - warft()[0], z - warft()[1]) < 120 ? "Warft von Südfall" : "Deich";
        if (a == Terrain.VORLAND) return Math.hypot(x - warft()[0], z - warft()[1]) < 900 ? "Südfall" : "Vorland";
        return terrain.height(x, z) < -6 ? "Strom" : terrain.height(x, z) < -2.5 ? "Priel" : "Watt";
    }

    String eraLine() { return wiring == null ? "Heute" : wiring.era.label(); }

    public String[] hudLines() {
        double[] sp = clock.sun();
        ComputeProbe.Result c = compute;
        return new String[]{
                String.format(Locale.GERMAN, "%.0f Bilder/s   Grafikkarte %.2f ms   Prozessor %.2f ms   Budget %.1f ms", fps, gpuMs, cpuMs, quality.budgetMs()),
                passTimes(),
                (c == null ? "!Compute-Test läuft …" : (c.ok() ? "" : "!") + c.line()),
                String.format(Locale.GERMAN, "%s · Sonne %.1f° / %03.0f° · Belichtung %.2g", clock.label(), sp[0], sp[1], exposure()),
                eraLine(),
                levelLine(),
                waterLine(),
                mudLine(),
                sealLine(),
                birdLine(),
                hikeLine(),
                townLine(),
                mandraenkeLine(),
                spurenLine(),
                glockenLine(),
                regieLine(),
                klang.describe(),
                tideStatus,
                skyLine(),
                cloudLine(),
                weatherLine(),
                rainOk ? "Regen " + rainGl.info() + (rainCheck.isEmpty() ? "" : " · " + rainCheck) + String.format(Locale.GERMAN, " · Wetter auf dem Prozessor %.2f ms", weatherCpuMs) : "!Regen: com.dan.rain nicht gestartet",
                fogOk ? "Nebel " + fog.describe() + " · " + fogGl.info() + (fogCheck.isEmpty() ? "" : " · " + fogCheck) : "!Nebel: com.dan.fog nicht gestartet",
                lightningLine(),
                (terrain.measured() ? "" : "!") + "Gelände: " + terrain.describe(),
                String.format(Locale.GERMAN, "Grund hier NHN %+.2f m · %s · Wasser %.2f m · %s", camera.groundLevel, Terrain.ART[land().artAt(camera.eye[0], camera.eye[2])],
                        terrain.waterDepth(camera.eye[0], camera.eye[2], level()), zone()),
                (oceanOk ? "Meer " + ocean.describe() + " · " + oceanGl.info() + (oceanCheck.isEmpty() ? "" : " · " + oceanCheck) : "!Meer: com.dan.ocean nicht gestartet"),
                String.format(Locale.GERMAN, "Gelände %d Knoten, %,d Punkte · Halme %s · %s", lod.nodes, lod.vertices, reedOk ? reedGl.info() : "aus", groundOk ? groundGl.info() : "Bodendecke aus"),
                physik.describe(),
                wiring == null ? "Verdrahtung —" : "Verdrahtung " + wiring.summary(),
                String.format(Locale.GERMAN, "%s · Auge %.1f m über NHN · Blick %03.0f° · %.0f m von der Kirche [G] · %s",
                        camera.modeName(), camera.eye[1], camera.heading(), camera.originDistance(),
                        Site.dms(Site.latLon(camera.eye[0], camera.eye[2])[0], Site.latLon(camera.eye[0], camera.eye[2])[1])),
                String.format(Locale.GERMAN, "%s · %d×%d · %s", quality.label, hdrW, hdrH, reversedZ ? "umgekehrte Tiefe" : "klassische Tiefe"),
                glInfo};
    }

    // ------------------------------------------------------------------ Bilder

    private void takePhoto(GL4 gl, int w, int h) {
        try {
            BufferedImage img = readDefault(gl, w, h);
            File dir = photoDir != null ? photoDir : new File("docs/status");
            if (!dir.isDirectory() && !dir.mkdirs()) throw new java.io.IOException("Ordner " + dir + " lässt sich nicht anlegen");
            File f = new File(dir, "Rungholt_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            photoMsg = "Foto gespeichert: " + f.getPath();
            System.out.println(photoMsg);
        } catch (Exception e) {
            photoMsg = "Foto fehlgeschlagen: " + e.getMessage();
            System.err.println(photoMsg);
        }
        photoMsgUntil = System.currentTimeMillis() + 5000;
    }

    /** Liest das fertige Fensterbild (nach Nachbearbeitung und HUD). */
    BufferedImage readDefault(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_READ_FRAMEBUFFER, defaultFbo);
        gl.glReadBuffer(defaultFbo == 0 ? GL.GL_BACK : GL.GL_COLOR_ATTACHMENT0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            int o = (h - 1 - y) * w * 4;
            for (int x = 0; x < w; x++) {
                int i = o + x * 4;
                row[x] = ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255);
            }
            img.setRGB(0, y, w, 1, row, 0, w);
        }
        return img;
    }

    /** Für Werkzeuge: das letzte Bild als Bild (im GL-Faden aufrufen). */
    public BufferedImage grab(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        defaultFbo = d.getContext().getDefaultDrawFramebuffer();
        return readDefault(gl, d.getSurfaceWidth(), d.getSurfaceHeight());
    }

    // ------------------------------------------------------------------ Ende

    private void fail(String msg) {
        error = msg;
        System.err.println(msg);
        errorListener.accept(msg);
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        klang.setOn(false);
        physik.stop();
        welleFaden.stop();
        stopTide();
        GL4 gl = d.getGL().getGL4();
        Gpu.deleteTextures(gl, floodTex, flowTex);
        mudGl.dispose(gl);
        if (sealOk) sealGl.dispose(gl);
        if (waderOk) waderGl.dispose(gl);
        if (figurOk) figureGl.dispose(gl);
        if (snowOk) snowGl.dispose(gl);
        if (timberOk) timberGl.dispose(gl);
        if (teichOk) teichGl.dispose(gl);
        if (buoyOk) buoyGl.dispose(gl);
        if (smokeOk) smokeGl.dispose(gl);
        for (Shader s : all) s.dispose(gl);
        lod.dispose(gl);
        if (oceanOk) oceanGl.dispose(gl);
        if (reedOk) reedGl.dispose(gl);
        if (groundOk) groundGl.dispose(gl);
        if (skyOk) skyGl.dispose(gl);
        if (cloudsOk) cloudGl.dispose(gl);
        if (rainOk) rainGl.dispose(gl);
        if (sprayOk) sprayGl.dispose(gl);
        if (fogOk) fogGl.dispose(gl);
        if (lightningOk) lightningGl.dispose(gl);
        Gpu.deleteFbos(gl, depthCopyFbo);
        Gpu.deleteTextures(gl, depthCopyTex);
        Gpu.deleteFbos(gl, panoFbo);
        Gpu.deleteTextures(gl, panoTex, maskTex, mask1362Tex);
        hud.dispose(gl);
        card.dispose(gl);
        Gpu.deleteFbos(gl, hdrFbo);
        Gpu.deleteTextures(gl, hdrTex, depthTex);
        if (emptyVao != 0) gl.glDeleteVertexArrays(1, new int[]{emptyVao}, 0);
        for (int[] s : stamp) if (s[0] != 0) gl.glDeleteQueries(MARKS, s, 0);
        ready = false;
    }
}
