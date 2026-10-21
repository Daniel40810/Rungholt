"""
Rungholt – Vorschauvideo (30 s, 1920x1080, 30 Bilder/s) als Motion Graphics.

Baut aus den Blättern in docs/bilder einen Trailer: Ken-Burns-Fahrten, Übergänge als
steigende Flutlinie, Kapitelzeilen, eine Pegelanzeige, die durch die Tide läuft, den
Jahreszähler 2026 -> 1362 und einen synthetischen Klang (Brandung, Grundton, Glocke).

Aufruf (aus dem Projektverzeichnis):
    python3 tools/vorschau/vorschau.py [ausgabe.mp4]
Braucht Python 3 mit Pillow und NumPy sowie ffmpeg im PATH.
Schriften: Caladea (Titel) und Inter (Beschriftung); fehlen sie, nimmt es DejaVu.
"""
import math
import os
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS, DAUER = 1920, 1080, 30, 30.0
N = int(DAUER * FPS)
WURZEL = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
BILDER = os.path.join(WURZEL, "docs", "bilder")
AUSGABE = sys.argv[1] if len(sys.argv) > 1 else os.path.join(WURZEL, "docs", "video", "Rungholt_Vorschau.mp4")

# ---------------------------------------------------------------- Schriften

def schrift(namen, groesse):
    orte = ["/usr/share/fonts/truetype/crosextra", "/usr/share/fonts/opentype/inter",
            "/usr/share/fonts/truetype/dejavu", "C:/Windows/Fonts"]
    for n in namen:
        for o in orte:
            p = os.path.join(o, n)
            if os.path.exists(p):
                return ImageFont.truetype(p, groesse)
    return ImageFont.load_default(groesse)

SERIF = ["Caladea-Regular.ttf", "DejaVuSerif.ttf", "georgia.ttf"]
SERIF_K = ["Caladea-Italic.ttf", "DejaVuSerif-Italic.ttf", "georgiai.ttf"]
SANS_L = ["Inter-Light.otf", "DejaVuSans-ExtraLight.ttf", "segoeuil.ttf"]
SANS_M = ["Inter-Medium.otf", "DejaVuSans.ttf", "segoeui.ttf"]

F_TITEL = schrift(SERIF, 170)
F_UNTER = schrift(SERIF_K, 52)
F_ZEILE = schrift(SERIF, 64)
F_KAPITEL = schrift(SANS_M, 22)
F_HUD = schrift(SANS_M, 18)
F_HUD_WERT = schrift(SANS_L, 34)
F_JAHR = schrift(SERIF, 260)
F_KLEIN = schrift(SANS_L, 26)

# ---------------------------------------------------------------- Farben

SAND = (236, 226, 204)
TUERKIS = (120, 200, 205)
WEINROT = (170, 45, 55)
NACHT = np.array([12, 18, 28], dtype=np.float32)

# ---------------------------------------------------------------- Hilfen

def clamp(x, a=0.0, b=1.0):
    return max(a, min(b, x))

def glatt(x):
    x = clamp(x)
    return x * x * (3 - 2 * x)

def aus(x):  # easeOutCubic
    x = clamp(x)
    return 1 - (1 - x) ** 3

def ein_aus(x):  # easeInOutCubic
    x = clamp(x)
    return 4 * x ** 3 if x < 0.5 else 1 - (-2 * x + 2) ** 3 / 2

def fenster(t, a, b, rein=0.5, raus=0.5):
    """Deckkraft 0..1 für ein Element, das von a bis b sichtbar ist."""
    if t < a or t > b:
        return 0.0
    return min(aus((t - a) / rein) if rein > 0 else 1.0, glatt((b - t) / raus) if raus > 0 else 1.0)

def lade(name):
    return Image.open(os.path.join(BILDER, name)).convert("RGB")

# ---------------------------------------------------------------- Einstellungen

class Einstellung:
    def __init__(self, bild, a, b, zoom, mitte, grad=None, kapitel=None, zeile=None):
        self.a, self.b = a, b
        self.zoom = zoom      # (von, bis): 1 = Bild füllt das Format
        self.mitte = mitte    # ((x0, y0), (x1, y1)) relativ 0..1
        self.grad = grad
        self.kapitel, self.zeile = kapitel, zeile
        src = lade(bild)
        # auf das 1,6-Fache des Formats vorskalieren, damit der Zoom Reserven hat
        s = max(W * 1.6 / src.width, H * 1.6 / src.height)
        self.src = src.resize((round(src.width * s), round(src.height * s)), Image.LANCZOS)
        if grad:
            self.src = grad(self.src)

    def bild(self, t):
        u = ein_aus((t - self.a) / (self.b - self.a))
        z = self.zoom[0] + (self.zoom[1] - self.zoom[0]) * u
        cx = self.mitte[0][0] + (self.mitte[1][0] - self.mitte[0][0]) * u
        cy = self.mitte[0][1] + (self.mitte[1][1] - self.mitte[0][1]) * u
        sw, sh = self.src.size
        # Ausschnitt mit Seitenverhältnis 16:9, der das Bild nicht verlässt
        bw = min(sw, sh * W / H) / z
        bh = bw * H / W
        x0 = clamp(cx * sw - bw / 2, 0, sw - bw)
        y0 = clamp(cy * sh - bh / 2, 0, sh - bh)
        return np.asarray(self.src.resize((W, H), Image.BICUBIC, box=(x0, y0, x0 + bw, y0 + bh)),
                          dtype=np.float32)

def nacht_grad(img):
    a = np.asarray(img, dtype=np.float32)
    a = (a - 40) * 1.35 * np.array([0.8, 0.92, 1.12]) + 18
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

def warm_grad(img):
    a = np.asarray(img, dtype=np.float32)
    a = a * np.array([1.06, 1.0, 0.92])
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))

EINST = [
    Einstellung("vogelauge_ebbe.jpg", 3.0, 7.4, (1.0, 1.25), ((0.5, 0.45), (0.55, 0.55)),
                kapitel="01  ·  EBBE", zeile="Alle zwölfeinhalb Stunden läuft das Wasser ab"),
    Einstellung("flut_ueber_sand.jpg", 6.8, 10.4, (1.3, 1.05), ((0.4, 0.6), (0.5, 0.5)),
                kapitel="02  ·  DIE SÄNDE", zeile="Die Sandbänke kommen hoch"),
    Einstellung("nasser_sand.jpg", 9.8, 13.4, (1.05, 1.35), ((0.5, 0.5), (0.5, 0.62)),
                kapitel="03  ·  NASSER SAND", zeile="Der Himmel im Wasserfilm"),
    Einstellung("seehundbank.jpg", 12.8, 16.4, (1.2, 1.45), ((0.35, 0.62), (0.6, 0.66)),
                kapitel="04  ·  DIE BANK", zeile="Seehunde ziehen auf den Sand"),
    Einstellung("rungholt_1362.jpg", 18.6, 22.0, (1.0, 1.3), ((0.35, 0.5), (0.58, 0.35)), grad=warm_grad,
                kapitel="1362  ·  EDOMSHARDE", zeile="Rungholt, die Handelsstadt"),
    Einstellung("mandraenke_flut.jpg", 21.4, 25.0, (1.0, 1.2), ((0.6, 0.5), (0.72, 0.42)), grad=nacht_grad,
                kapitel="16. JANUAR 1362", zeile="Die Grote Mandränke"),
    Einstellung("mandraenke_morgen.jpg", 24.4, 27.6, (1.15, 1.0), ((0.5, 0.6), (0.5, 0.5)),
                kapitel="AM MORGEN DANACH", zeile="Die Kirchwarft steht im Meer"),
]
UEBERGANG = 0.6

# ---------------------------------------------------------------- Flutlinie

XS = np.arange(W, dtype=np.float32)
YS = np.arange(H, dtype=np.float32)[:, None]

def flutmaske(p, phase):
    """Maske 0..1: die neue Einstellung steigt als Wasserlinie von unten herauf."""
    if p <= 0:
        return np.zeros((H, 1), np.float32), None
    if p >= 1:
        return np.ones((H, 1), np.float32), None
    basis = H * (1.08 - 1.16 * ein_aus(p))
    welle = (18 * np.sin(XS / 130 + phase * 6) + 9 * np.sin(XS / 47 - phase * 9)
             + 5 * np.sin(XS / 23 + phase * 4)) * math.sin(math.pi * p)
    linie = basis + welle
    m = np.clip((YS - linie[None, :]) / 6 + 0.5, 0, 1)
    return m.astype(np.float32), linie

# ---------------------------------------------------------------- Vignette, Korn

yy, xx = np.mgrid[0:H, 0:W].astype(np.float32)
r = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
VIGNETTE = (1 - 0.38 * np.clip(r - 0.45, 0, 1) ** 1.6)[..., None].astype(np.float32)
del yy, xx, r
RNG = np.random.default_rng(1362)
KORN = [RNG.normal(0, 1.8, (H // 2, W // 2)).astype(np.float32) for _ in range(8)]

# ---------------------------------------------------------------- Pegel

def pegel(t):
    """Pegel am Bildschirm: erst eine Tide im Zeitraffer, ab 18 s die Sturmflut."""
    if t < 17:
        return 1.45 * math.cos(2 * math.pi * (t - 3) / 16.0 + 0.4)
    if t < 21.5:
        return 1.2
    s = glatt((t - 21.5) / 3.5)
    return 1.2 + 3.4 * s

def uhrzeit(t):
    m = (6 * 60 + (t - 3) / 16.0 * 12.42 * 60) % (24 * 60)
    return f"{int(m // 60):02d}:{int(m % 60):02d}"

def zeichne_hud(d, t, a):
    if a <= 0:
        return
    x0, y0, bw, bh = W - 470, H - 190, 400, 120
    alpha = int(255 * a)
    sturm = t >= 18
    farbe = (*(WEINROT if t > 21.5 else TUERKIS), alpha)
    d.rectangle((x0, y0, x0 + bw, y0 + bh), fill=(8, 14, 22, int(120 * a)))
    d.line((x0, y0, x0 + 40, y0), fill=(*SAND, alpha), width=2)
    titel = "PEGEL RUNGHOLT · 15.01.1362" if sturm else "PEGEL PELLWORM"
    d.text((x0 + 16, y0 + 12), titel, font=F_HUD, fill=(*SAND, int(200 * a)))
    p = pegel(t)
    wert = f"{'+' if p >= 0 else '−'}{abs(p):.2f} m".replace(".", ",")
    d.text((x0 + 16, y0 + 40), wert, font=F_HUD_WERT, fill=(*SAND, alpha))
    rechts = "NACHT" if sturm else uhrzeit(t)
    d.text((x0 + bw - 16, y0 + 14), rechts, font=F_HUD, fill=(*SAND, int(160 * a)), anchor="ra")
    # Kurve über 6 s rund um den Augenblick
    gx0, gx1, gy = x0 + 190, x0 + bw - 16, y0 + 80
    pts = []
    for i in range(80):
        tt = t - 3 + 6 * i / 79
        pts.append((gx0 + (gx1 - gx0) * i / 79, gy - pegel(tt) * 9))
    d.line(pts, fill=(*SAND, int(90 * a)), width=2)
    mx = gx0 + (gx1 - gx0) / 2
    my = gy - p * 9
    d.line((mx, y0 + 40, mx, y0 + bh - 10), fill=(*SAND, int(60 * a)), width=1)
    d.ellipse((mx - 6, my - 6, mx + 6, my + 6), fill=farbe)
    d.ellipse((mx - 12, my - 12, mx + 12, my + 12), outline=(*farbe[:3], int(alpha * 0.5)), width=2)

# ---------------------------------------------------------------- Texte

def gesperrt(d, xy, text, font, fill, sperr, anker="la"):
    """Text mit Sperrung; anker 'la' links, 'ma' mittig."""
    breiten = [d.textlength(c, font=font) for c in text]
    gesamt = sum(breiten) + sperr * (len(text) - 1)
    x, y = xy
    if anker == "ma":
        x -= gesamt / 2
    for c, b in zip(text, breiten):
        d.text((x, y), c, font=font, fill=fill)
        x += b + sperr
    return gesamt

def zeichne_zeile(d, e, t):
    a = fenster(t, e.a + 0.5, e.b - 0.35, 0.7, 0.45)
    if a <= 0:
        return
    hoch = (1 - aus((t - e.a - 0.5) / 0.9)) * 30
    x, y = 120, H - 250 + hoch
    laenge = 70 * aus((t - e.a - 0.4) / 0.8)
    d.line((x, y - 22, x + laenge, y - 22), fill=(*SAND, int(255 * a)), width=3)
    gesperrt(d, (x, y), e.kapitel, F_KAPITEL, (*SAND, int(220 * a)), 6)
    # Schatten für Lesbarkeit
    d.text((x + 2, y + 44), e.zeile, font=F_ZEILE, fill=(0, 0, 0, int(110 * a)))
    d.text((x, y + 42), e.zeile, font=F_ZEILE, fill=(255, 255, 255, int(255 * a)))

def titelkarte(d, t, a, unter, extra=None):
    if a <= 0:
        return
    breite = gesperrt(d, (0, -500), "RUNGHOLT", F_TITEL, (0, 0, 0, 0), 28)
    # Buchstaben einzeln aus der Wasserlinie auftauchen lassen
    x = W / 2 - breite / 2
    for i, c in enumerate("RUNGHOLT"):
        u = aus((a * 1.6) - i * 0.07)
        yb = H / 2 - 130 + (1 - u) * 50
        d.text((x, yb), c, font=F_TITEL, fill=(*SAND, int(255 * u * a)))
        x += d.textlength(c, font=F_TITEL) + 28
    lw = 520 * aus(a * 1.3 - 0.2)
    d.line((W / 2 - lw / 2, H / 2 + 75, W / 2 + lw / 2, H / 2 + 75), fill=(*TUERKIS, int(255 * a)), width=2)
    au = clamp(a * 1.5 - 0.5)
    d.text((W / 2, H / 2 + 100), unter, font=F_UNTER, fill=(*SAND, int(230 * au)), anchor="ma")
    if extra:
        gesperrt(d, (W / 2, H / 2 + 185), extra, F_KAPITEL, (*SAND, int(170 * au)), 6, "ma")

def wasserlinie_deko(d, t, a, y, farbe=TUERKIS):
    """Animierte Tidenlinie quer durchs Bild."""
    if a <= 0:
        return
    pts = []
    sicht = aus(a * 1.2)
    for i in range(0, int(W * sicht) + 1, 8):
        pts.append((i, y + 14 * math.sin(i / 160 - t * 2.0) + 6 * math.sin(i / 53 + t * 3)))
    if len(pts) > 1:
        d.line(pts, fill=(*farbe, int(140 * a)), width=2)

# ---------------------------------------------------------------- Zwischentafel 2026 -> 1362

def ringe(d, t, a, cx, cy):
    for k in range(5):
        ph = ((t * 0.35) + k / 5) % 1.0
        rr = 60 + ph * 700
        al = int(80 * a * (1 - ph))
        d.ellipse((cx - rr, cy - rr * 0.32, cx + rr, cy + rr * 0.32), outline=(*TUERKIS, al), width=2)

def jahr(t):
    u = ein_aus((t - 16.9) / 1.5)
    return round(2026 - (2026 - 1362) * u)

# ---------------------------------------------------------------- Bild

def hintergrund(t):
    aktiv = [e for e in EINST if e.a - 1e-6 <= t <= e.b]
    if not aktiv:
        return None
    out = aktiv[0].bild(t)
    for e in aktiv[1:]:
        p = (t - e.a) / UEBERGANG
        m, linie = flutmaske(p, t)
        neu = e.bild(t)
        out = out * (1 - m[..., None]) + neu * m[..., None]
        if linie is not None:
            # helle Gischtkante an der Linie
            kante = np.exp(-((YS - linie[None, :]) / 3.0) ** 2) * 0.55 * math.sin(math.pi * p)
            out = out + kante[..., None] * 255
    return out

def frame(i):
    t = i / FPS
    bg = hintergrund(t)
    if bg is None:
        bg = np.broadcast_to(NACHT, (H, W, 3)).copy()
    else:
        # Ein- und Ausblenden an den Rändern der Bildstrecken
        dunkel = 0.0
        dunkel = max(dunkel, 1 - glatt((t - 3.0) / 0.8))           # aus dem Titel
        dunkel = max(dunkel, glatt((t - 15.8) / 0.6) if t < 18.6 else 0)  # in die Zwischentafel
        dunkel = max(dunkel, 1 - glatt((t - 18.6) / 0.6) if t >= 18.6 else 0)
        dunkel = max(dunkel, glatt((t - 26.9) / 0.7))              # in die Endtafel
        bg = bg * (1 - dunkel) + NACHT * dunkel
    # Blitze in der Mandränke
    for tb in (22.25, 23.4, 23.55):
        if tb <= t < tb + 0.25:
            bg = bg + (1 - (t - tb) / 0.25) ** 2 * 120
    bg = bg * VIGNETTE
    k = KORN[i % len(KORN)]
    bg = bg + np.repeat(np.repeat(k, 2, 0), 2, 1)[..., None]
    img = Image.fromarray(np.clip(bg, 0, 255).astype(np.uint8)).convert("RGBA")

    lage = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(lage)

    # Titel 0..3,2 s
    a = fenster(t, 0.15, 3.1, 1.6, 0.5)
    titelkarte(d, t, a, "Zweimal am Tag Land")
    wasserlinie_deko(d, t, fenster(t, 0.0, 3.1, 1.2, 0.5), H / 2 + 300)

    for e in EINST:
        zeichne_zeile(d, e, t)

    # Zwischentafel
    a = fenster(t, 16.0, 18.7, 0.5, 0.45)
    if a > 0:
        ringe(d, t, a, W / 2, H / 2 + 40)
        d.text((W / 2, H / 2 - 230), "Unter diesem Watt liegt Rungholt", font=F_UNTER,
               fill=(*SAND, int(220 * a)), anchor="ma")
        jz = jahr(t)
        farbe = SAND if jz > 1362 else (230, 120, 110)
        d.text((W / 2, H / 2 - 150), str(jz), font=F_JAHR, fill=(*farbe, int(255 * a)), anchor="ma")
        gesperrt(d, (W / 2, H / 2 + 150), "NORDFRIESLAND  ·  EDOMSHARDE", F_KAPITEL,
                 (*SAND, int(170 * a)), 6, "ma")

    zeichne_hud(d, t, fenster(t, 3.6, 27.0, 0.6, 0.5) * (0 if 16.0 < t < 18.8 else 1))

    # Endtafel
    a = fenster(t, 27.3, 30.5, 1.4, 0.1)
    titelkarte(d, t, a, "Das Watt in Echtzeit-3D – heute und 1362",
               "JAVA 21  ·  JOGL  ·  OPENGL 4.3")
    wasserlinie_deko(d, t, fenster(t, 27.2, 30.5, 1.2, 0.1), H / 2 + 300)

    # dünne Kinobalken
    d.rectangle((0, 0, W, 44), fill=(0, 0, 0, 255))
    d.rectangle((0, H - 44, W, H), fill=(0, 0, 0, 255))

    img = Image.alpha_composite(img, lage).convert("RGB")
    # Schwarzblende ganz am Ende
    if t > 29.4:
        img = Image.blend(img, Image.new("RGB", (W, H)), glatt((t - 29.4) / 0.6))
    if t < 0.4:
        img = Image.blend(Image.new("RGB", (W, H)), img, glatt(t / 0.4))
    return img

# ---------------------------------------------------------------- Klang

def klang(pfad, sr=48000):
    n = int(DAUER * sr)
    t = np.arange(n) / sr
    rng = np.random.default_rng(7)
    # Brandung: braunes Rauschen, langsam atmend
    weiss = rng.normal(0, 1, n)
    braun = np.cumsum(weiss)
    braun -= np.convolve(braun, np.ones(4801) / 4801, mode="same")
    braun /= np.max(np.abs(braun)) + 1e-9
    atem = 0.55 + 0.45 * np.sin(2 * np.pi * t / 6.5 - 1.2) ** 2
    sturm = np.clip((t - 21.0) / 1.0, 0, 1) * np.clip((25.5 - t) / 1.5, 0, 1)
    meer = braun * (0.35 * atem + 0.9 * sturm)
    # Grundton: A1 / E2 / A2 mit leichtem Schweben
    grund = sum(g * np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 0.1 * t))
                for f, g in ((55, 0.35), (82.4, 0.22), (110.3, 0.16), (164.8, 0.06)))
    grund *= np.clip(t / 3, 0, 1) * np.clip((30 - t) / 2.5, 0, 1)
    grund *= 1 - 0.5 * np.clip((t - 16) / 0.5, 0, 1) * np.clip((18.6 - t) / 0.5, 0, 1)
    # Glocken (die Glocken von Rungholt): Partialtöne mit Ausklang
    glocke = np.zeros(n)
    for start, f0, g in ((16.2, 196.0, 0.5), (18.4, 146.8, 0.6), (27.4, 196.0, 0.45)):
        tt = t - start
        m = tt >= 0
        for teil, amp, ab in ((0.5, 0.6, 1.2), (1.0, 1.0, 0.9), (1.19, 0.5, 1.5), (1.5, 0.4, 1.8),
                              (2.0, 0.35, 2.4), (2.74, 0.2, 3.5)):
            glocke[m] += g * amp * np.sin(2 * np.pi * f0 * teil * tt[m]) * np.exp(-tt[m] * ab)
    # Donner zu den Blitzen
    donner = np.zeros(n)
    for tb in (22.45, 23.7):
        tt = t - tb
        m = tt >= 0
        donner[m] += braun[m] * 1.6 * np.exp(-tt[m] * 1.4) * (1 - np.exp(-tt[m] * 30))
    mix = 0.5 * meer + 0.35 * grund + 0.25 * glocke + 0.5 * donner
    mix *= np.clip(t / 1.0, 0, 1) * np.clip((DAUER - t) / 1.2, 0, 1)
    mix /= np.max(np.abs(mix)) + 1e-9
    mix *= 0.8
    stereo = np.stack([mix, np.roll(mix, 240)], axis=1)
    pcm = (stereo * 32767).astype(np.int16)
    with wave.open(pfad, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(sr)
        w.writeframes(pcm.tobytes())

# ---------------------------------------------------------------- Ausgabe

def main():
    os.makedirs(os.path.dirname(AUSGABE), exist_ok=True)
    wav = AUSGABE + ".klang.wav"
    klang(wav)
    if "--bilder" in sys.argv:
        for s in (1.6, 5, 7.1, 11, 14.5, 17.5, 20, 22.3, 26, 29):
            frame(int(s * FPS)).save(AUSGABE + f".{s:04.1f}s.jpg", quality=88)
        return
    cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
           "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-i", wav,
           "-c:v", "libx264", "-preset", "slow", "-crf", "23", "-pix_fmt", "yuv420p",
           "-movflags", "+faststart", "-c:a", "aac", "-b:a", "160k", "-shortest", AUSGABE]
    ff = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for i in range(N):
        ff.stdin.write(frame(i).tobytes())
        if i % 60 == 0:
            print(f"{i / FPS:5.1f} s", flush=True)
    ff.stdin.close()
    ff.wait()
    os.remove(wav)
    print("fertig:", AUSGABE)

if __name__ == "__main__":
    main()
