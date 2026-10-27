#!/usr/bin/env python3
"""
Regenerates every Interceptors asset that isn't code or language text: textures, sounds and model/sound JSON.

    python3 -m venv .venv && .venv/bin/pip install numpy soundfile
    .venv/bin/python tools/gen_assets.py

Everything is procedural and seeded, so re-running produces identical files.
Item icons are hand-drawn as ASCII pixel art below; sounds are synthesized with numpy.
soundfile is needed because Minecraft only positions *mono* Ogg Vorbis.
"""
import gzip
import json
import os
import struct
import zlib

import numpy as np
import soundfile as sf

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
NS = "interceptors"
ASSETS = os.path.join(ROOT, "assets", NS)
SR = 22050


def path(*p):
    full = os.path.join(*p)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    return full


def write_json(obj, *p):
    with open(path(*p), "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


# =============================================================================== png

def write_png(img, *p):
    img = np.clip(img, 0, 255).astype(np.uint8)
    h, w, c = img.shape
    if c == 3:
        img = np.concatenate([img, np.full((h, w, 1), 255, np.uint8)], axis=2)
    raw = b"".join(b"\x00" + img[y].tobytes() for y in range(h))

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    with open(path(*p), "wb") as f:
        f.write(png)


def hexrgb(h):
    return [int(h[i:i + 2], 16) for i in (0, 2, 4)] + [255]


def ascii_icon(art, palette):
    rows = [r for r in art.strip("\n").split("\n")]
    img = np.zeros((16, 16, 4), np.uint8)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != "." and ch in palette:
                img[y, x] = hexrgb(palette[ch])
    return img


# Shared palette; vehicles override letters for their own paint.
BASE = {
    "B": "1b1c1e",  # black trim
    "T": "111111",  # tire
    "H": "9a9ea3",  # hub
    "G": "22303c",  # glass
    "Y": "fff2c0",  # headlight
    "R": "e0201a",  # taillight
    "A": "ffa51f",  # amber light
    "S": "6b7075",  # steel
}

ICONS = {
    "gaming_van": ("""
................
................
.BBBBBBBBBBBA...
.WWWWWWWWWWWWW..
.WWGGGGGWWWWGGW.
.WWGGGGGWWWWGGGW
.WWWWWWWWWWWWWWW
.WWWWWWWWWWWWWWY
.WWWWWWWWWWWWWWW
RWWWWWWWWWWWWWWB
.PPPPPPPPPPPPPPB
.BBTTTBBBBBTTTB.
...THT.....THT..
....T.......T...
................
................
""", {"W": "edeff0", "P": "b040ff"}),
    "dominator_1": ("""
................
................
................
....BBBBBBBB....
...BGGGGBGGGB...
..BBGGGGBGGGGB..
.SBBBBBBBBBBBBB.
.SRRRRRRRRRRRRY.
.SBBBBBBBBBBBBB.
.SBBBBBBBBBBBBR.
..BTTTBBBBTTTB..
...THT....THT...
....T......T....
................
................
................
""", {"B": "202326"}),
    "dominator_2": ("""
................
................
................
...BBBBBBBBBB...
..BGGGBGGGBGGB..
.BBGGGBGGGBGGGB.
SBBBBBBBBBBBBBB.
SOOOOOOOOOOOOOY.
SBBBBBBBBBBBBBB.
SBBBBBBBBBBBBBR.
.SSSSSSSSSSSSSS.
..THHT....THHT..
...TT......TT...
................
................
................
""", {"B": "2b2f33", "O": "ff6a13"}),
    "dominator_3": ("""
................
................
.......S........
......SSS.......
..BBBBBBBBB.....
..BBBBBGGGGB....
.BWBWBBGGGGGB...
.BBBBBBBBBBBBBB.
SBBBBBBBBBBBBBY.
SRRRRRRRRRRRRRB.
.SSSSSSSSSSSSSS.
..THHT.....THHT.
...TT.......TT..
................
................
................
""", {"B": "17181a", "W": "c9cdd2"}),
    "tiv_1": ("""
................
................
................
.......BB.......
..OOOOOOOOOO....
..OOOOOOSGSGO...
..OOOOOOOOOOOO..
.SOOOOOOOOOOOOY.
.SOOOOOOOOOOOOO.
.SOOOOOOOOOOOOR.
.SSSSSSSSSSSSSS.
..THHT.....THHT.
...TT.......TT..
................
................
................
""", {"O": "3a3d33"}),
    "doppler_on_wheels": ("""
.....D..........
....DDD.........
...DDDDD........
...DDDDD........
....DDD.........
.....S....WWWW..
.....S....WGGW..
.....S....WGGGW.
.SSSSSSSSSWWWWW.
.KKKKKKKKKWWWWY.
.SSSSSSSSSWWWWB.
..THT.....THT...
...T.......T....
................
................
................
""", {"W": "edeff0", "D": "f4f4f4", "K": "4a4f55"}),
    "rental_car": ("""
................
................
................
................
.....SSSSSS.....
....SGGGSGGGS...
..SSSSSSSSSSSSS.
.SSSSSSSSSSSSSY.
.SSSSSSSSSSSSSS.
RSSSSSSSSSSSSSB.
.BBTTTBBBBTTTB..
...THT....THT...
....T......T....
................
................
................
""", {"S": "b8bec6"}),
}


def textures():
    write_png(np.full((16, 16, 4), 255, np.uint8), ASSETS, "textures", "entity", "white.png")
    for name, (art, paint) in ICONS.items():
        write_png(ascii_icon(art, {**BASE, **paint}), ASSETS, "textures", "item", name + ".png")
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   ASSETS, "models", "item", name + ".json")


# =============================================================================== sound

def write_ogg(signal, name):
    signal = np.asarray(signal, np.float64)
    peak = np.max(np.abs(signal)) or 1.0
    sf.write(path(ASSETS, "sounds", name + ".ogg"), (signal / peak * 0.85).astype(np.float32), SR, format="OGG", subtype="VORBIS")


def env(n, attack=0.01, release=0.08):
    e = np.ones(n)
    a, r = int(attack * SR), int(release * SR)
    e[:a] = np.linspace(0, 1, a)
    e[-r:] = np.linspace(1, 0, r)
    return e


def horn():
    # Two detuned reedy tones a minor third apart, like a van's dual horn.
    t = np.arange(int(0.7 * SR)) / SR
    s = np.zeros_like(t)
    for f in (370.0, 440.0):
        for k in range(1, 7):
            s += np.sign(np.sin(2 * np.pi * f * k * t)) / k ** 1.6
    return s * env(len(t), 0.02, 0.12)


def hydraulics(rising):
    rng = np.random.default_rng(7 if rising else 8)
    t = np.arange(int(0.9 * SR)) / SR
    sweep = np.linspace(180, 320, len(t)) if rising else np.linspace(320, 160, len(t))
    whine = np.sin(2 * np.pi * np.cumsum(sweep) / SR) * 0.5
    hiss = rng.standard_normal(len(t)) * 0.15
    s = (whine + hiss) * env(len(t), 0.05, 0.2)
    # Metal clunk at the end: spikes hitting the ground, or locking up.
    ct = np.arange(int(0.25 * SR)) / SR
    clunk = (np.sin(2 * np.pi * 95 * ct) + 0.6 * np.sin(2 * np.pi * 233 * ct) + rng.standard_normal(len(ct)) * 0.4) * np.exp(-ct * 22)
    return np.concatenate([s, clunk * 1.6])


def sounds():
    write_ogg(horn(), "horn")
    write_ogg(hydraulics(False), "anchor_down")
    write_ogg(hydraulics(True), "anchor_up")
    write_json({name: {"sounds": [f"{NS}:{name}"], "subtitle": f"subtitles.{NS}.{name}"}
                for name in ("horn", "anchor_down", "anchor_up")}, ASSETS, "sounds.json")


# =============================================================================== realistic vehicle materials
#
# Vehicles are boxes wearing these materials. Each one is painted at 64 pixels per block (4x a vanilla block)
# from layered noise, so it reads as real metal, rubber and glass rather than flat colour. "World" materials
# repeat across a box at that scale, with y=0 (the road) at the bottom of the texture, which is where the mud
# goes; "stretch" materials (rims, lights, dish) fit each face once.

PPB = 64


def fbm(h, w, seed, octaves=5, scale=8.0):
    """Smooth fractal noise in [0, 1], tileable horizontally and vertically."""
    rng = np.random.default_rng(seed)
    out = np.zeros((h, w))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        gh, gw = max(2, int(scale * 2 ** o * h / max(h, w))), max(2, int(scale * 2 ** o * w / max(h, w)))
        grid = rng.random((gh, gw))
        ys = np.linspace(0, gh, h, endpoint=False)
        xs = np.linspace(0, gw, w, endpoint=False)
        y0, x0 = ys.astype(int), xs.astype(int)
        fy, fx = (ys - y0)[:, None], (xs - x0)[None, :]
        fy, fx = fy * fy * (3 - 2 * fy), fx * fx * (3 - 2 * fx)
        y1, x1 = (y0 + 1) % gh, (x0 + 1) % gw
        a = grid[y0][:, x0] * (1 - fx) + grid[y0][:, x1] * fx
        b = grid[y1][:, x0] * (1 - fx) + grid[y1][:, x1] * fx
        out += (a * (1 - fy) + b * fy) * amp
        total += amp
        amp *= 0.5
    return out / total


def mix(img, color, mask):
    mask = np.clip(mask, 0, 1)[..., None]
    return img * (1 - mask) + np.array(color, float) * mask


def mud(img, seed, height_px, splatter=3):
    """Mud spray thrown up by the tires: thick at the road, thinning into splatter higher up."""
    h, w = img.shape[:2]
    y = np.arange(h)[:, None] / h
    from_bottom = (1 - y) * h
    n = fbm(h, w, seed, 5, 10)
    reach = np.clip(1 - from_bottom / height_px, 0, 1)
    mask = np.clip((n * 0.9 + reach * 1.2 - 1.0) * 3, 0, 1)
    rng = np.random.default_rng(seed + 1)
    for _ in range(w // splatter):
        cx, cy = rng.integers(0, w), int(h - rng.exponential(height_px * 0.5))
        if 0 <= cy < h:
            r = rng.integers(1, 3)
            mask[max(0, cy - r):cy + r, max(0, cx - r):cx + r] = np.maximum(mask[max(0, cy - r):cy + r, max(0, cx - r):cx + r], 0.8)
    tone = fbm(h, w, seed + 2, 4, 16)[..., None]
    mud_col = np.array([92, 72, 50]) * (0.75 + tone * 0.5)
    m = mask[..., None]
    return img * (1 - m) + mud_col * m


def scratches(img, seed, count, light, dark):
    rng = np.random.default_rng(seed)
    h, w = img.shape[:2]
    for _ in range(count):
        x, y = rng.integers(0, w), rng.integers(0, h)
        length, ang = rng.integers(4, 22), rng.uniform(-0.5, 0.5) + (0 if rng.random() < 0.7 else np.pi / 2)
        col = light if rng.random() < 0.6 else dark
        for t in range(length):
            px, py = int(x + np.cos(ang) * t) % w, int(y + np.sin(ang) * t) % h
            img[py, px] = img[py, px] * 0.4 + np.array(col) * 0.6
    return img


def streaks(img, seed, strength):
    """Vertical grime streaks running down from the top, like rain-washed dirt."""
    h, w = img.shape[:2]
    col = fbm(1, w, seed, 4, 24)[0]
    fade = np.linspace(1, 0.2, h)[:, None]
    return img * (1 - (col[None, :] * fade * strength)[..., None])


def rivets(img, xs, ys, size=2):
    for x in xs:
        for y in ys:
            x, y = int(x), int(y)
            img[y - size:y + size, x - size:x + size] = [150, 152, 150]
            img[y - size:y, x - size:x] = [205, 208, 205]
            img[y + size:y + size + 1, x - size + 1:x + size + 1] = [18, 18, 18]
    return img


def mat_paint():
    h, w = 3 * PPB, 4 * PPB
    img = np.zeros((h, w, 3)) + 238
    img += (fbm(h, w, 10, 5, 6)[..., None] - 0.5) * 14
    for x in range(0, w, 2 * PPB):
        img[:, x:x + 2] = 120
        img[:, x + 2:x + 3] = 250
    img = streaks(img, 11, 0.18)
    img = scratches(img, 12, 10, [255, 255, 255], [222, 222, 222])
    rng = np.random.default_rng(13)
    return mud(img, 14, PPB * 0.7, splatter=12)


def mat_armor(seed, dents):
    h, w = 3 * PPB, 4 * PPB
    img = np.zeros((h, w, 3)) + 62
    img += (fbm(h, w, seed, 6, 10)[..., None] - 0.5) * 22
    pw, ph = 96, 64
    for x in range(0, w, pw):
        img[:, x:x + 2] = 22
        img[:, x + 2:x + 3] = 92
        img = rivets(img, [x + 8 + i * 20 for i in range(4)], [12, ph + 12, 2 * ph + 12])
    for y in range(0, h, ph):
        img[y:y + 2, :] = 22
        img[y + 2:y + 3, :] = 92
    if dents:
        rng = np.random.default_rng(seed + 5)
        yy, xx = np.mgrid[0:h, 0:w]
        for _ in range(9):
            cx, cy, r = rng.integers(0, w), rng.integers(0, h), rng.integers(8, 22)
            d = np.hypot(xx - cx, yy - cy) / r
            shade = np.where(d < 1, (yy - cy) / r * 30 * (1 - d), 0)
            img += shade[..., None]
    # rust drips from rivets and seams
    rng = np.random.default_rng(seed + 7)
    for _ in range(26 if dents else 12):
        x, y = rng.integers(0, w), rng.integers(0, h - 30)
        for t in range(rng.integers(6, 26)):
            img[y + t, x:x + 2] = img[y + t, x:x + 2] * 0.5 + np.array([118, 62, 30]) * 0.5
    img = scratches(img, seed + 8, 45, [120, 120, 116], [30, 30, 30])
    return mud(img, seed + 9, PPB * 1.1)


def mat_steel():
    h = w = 2 * PPB
    rng = np.random.default_rng(20)
    img = np.zeros((h, w, 3)) + 150
    img += (rng.random((h, 1)) - 0.5) * 30  # brushed lines
    img += (fbm(h, w, 21, 4, 6)[..., None] - 0.5) * 30
    img = rivets(img, [10, w - 10], [10, h - 10], 3)
    return scratches(img, 22, 30, [220, 220, 220], [70, 70, 70])


def mat_plastic():
    h = w = 2 * PPB
    img = np.zeros((h, w, 3)) + 34
    img += (np.random.default_rng(30).random((h, w, 1)) - 0.5) * 10
    img += (fbm(h, w, 31, 4, 8)[..., None] - 0.5) * 12
    img = scratches(img, 32, 40, [90, 90, 90], [10, 10, 10])
    return mud(img, 33, PPB * 1.0)


def mat_glass():
    h = w = 2 * PPB
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 4))
    img[..., :3] = np.array([38, 52, 64]) + (1 - yy[..., None] / h) * np.array([40, 50, 60])
    img[..., 3] = 120
    streak = np.abs(((xx + yy * 0.8) % w) - w * 0.35)
    glare = np.clip(1 - streak / 7, 0, 1) * 0.6 + np.clip(1 - np.abs(((xx + yy * 0.8) % w) - w * 0.5) / 3, 0, 1) * 0.4
    img[..., :3] += glare[..., None] * 150
    img[..., 3] += glare * 90
    dirt = (fbm(h, w, 40, 5, 12) > 0.74) & (yy > h * 0.75)
    img[dirt] = [110, 95, 75, 200]
    return np.clip(img, 0, 255)


def mat_tire():
    h = w = 2 * PPB
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 3)) + 30
    groove = ((xx // 12 + yy // 16) % 2 == 0) & ((xx % 12) < 9) & ((yy % 16) < 13)
    img[~groove] = 16
    img += (fbm(h, w, 50, 5, 10)[..., None] - 0.5) * 14
    dust = fbm(h, w, 51, 4, 12)
    return mix(img, [96, 82, 64], (dust - 0.62) * 2)


def mat_rim():
    s = 2 * PPB
    yy, xx = np.mgrid[0:s, 0:s]
    d = np.hypot(xx - s / 2 + 0.5, yy - s / 2 + 0.5) / (s / 2)
    ang = np.arctan2(yy - s / 2, xx - s / 2)
    img = np.zeros((s, s, 3)) + 20
    img[d < 0.98] = 26
    rim = (d < 0.78)
    img[rim] = 175
    spokes = rim & (np.cos(ang * 5) > 0.55) & (d > 0.22)
    img[rim & ~spokes & (d > 0.24) & (d < 0.7)] = 40  # gaps between spokes show the brake disc
    img[(d < 0.6) & (d > 0.5) & ~spokes] = [70, 68, 66]
    img += ((1 - d) * 40)[..., None] * rim[..., None]
    for k in range(5):
        a = k * 2 * np.pi / 5 + np.pi / 5
        cx, cy = s / 2 + np.cos(a) * s * 0.14, s / 2 + np.sin(a) * s * 0.14
        img[np.hypot(xx - cx, yy - cy) < 3] = 220
    img[d < 0.1] = 120
    return img


def mat_headlight():
    s = PPB
    yy, xx = np.mgrid[0:s, 0:s]
    d = np.hypot(xx - s / 2, yy - s / 2) / (s / 2)
    img = np.zeros((s, s, 3)) + 210
    img += np.clip(1 - d, 0, 1)[..., None] * 45
    img[(d > 0.55) & (d < 0.62)] = 160
    img[d < 0.25] = 255
    return img


def mat_taillight():
    s = PPB
    yy, xx = np.mgrid[0:s, 0:s]
    img = np.zeros((s, s, 3)) + [190, 20, 18]
    img[(yy % 16) < 2] = [120, 10, 10]
    img[(xx % 12) < 1] = [150, 14, 12]
    img += np.clip(1 - np.abs(xx - yy) / 8, 0, 1)[..., None] * 60
    return img


def mat_lightbar():
    h, w = PPB // 4, 2 * PPB
    img = np.zeros((h, w, 3)) + 30
    for x in range(2, w, 8):
        img[3:h - 3, x:x + 6] = [255, 176, 40]
        img[4:h - 4, x + 1:x + 5] = [255, 220, 120]
    return img


def mat_solar():
    h = w = 2 * PPB
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 3)) + [28, 42, 92]
    img += (fbm(h, w, 60, 4, 6)[..., None] - 0.5) * 30
    img[(xx % 32) < 2] = [180, 185, 195]
    img[(yy % 32) < 2] = [180, 185, 195]
    img[(xx % 8) == 4] = img[(xx % 8) == 4] * 0.8 + 40
    glare = np.clip(1 - np.abs(xx - yy - 20) / 10, 0, 1)
    return img + glare[..., None] * 60


def mat_wood():
    h = w = 2 * PPB
    yy, xx = np.mgrid[0:h, 0:w]
    plank = xx // 21
    rng = np.random.default_rng(70)
    tones = rng.uniform(0.85, 1.12, plank.max() + 1)
    grain = np.sin((xx + fbm(h, w, 71, 4, 4) * 40) * 0.9) * 0.5 + 0.5
    img = (np.array([168, 116, 70]) * (0.82 + grain[..., None] * 0.2)) * tones[plank][..., None]
    img[(xx % 21) == 0] = [70, 46, 28]
    return img


def mat_fabric():
    h = w = PPB
    yy, xx = np.mgrid[0:h, 0:w]
    weave = ((xx + yy) % 4 < 2) * 18 + ((xx - yy) % 4 < 2) * 12
    return np.zeros((h, w, 3)) + 200 + weave[..., None] + (fbm(h, w, 80, 3, 6)[..., None] - 0.5) * 30


def mat_vinyl():
    h = w = 2 * PPB
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.zeros((h, w, 3)) + [128, 120, 110]
    img += (np.sin((yy + fbm(h, w, 90, 4, 4) * 30) * 0.7) * 8)[..., None]
    img[(yy % 32) == 0] = [80, 74, 68]
    img[((xx + (yy // 32) * 50) % 100) == 0] = [80, 74, 68]
    return img


def mat_grille():
    s = PPB
    yy, xx = np.mgrid[0:s, 0:s]
    img = np.zeros((s, s, 3)) + 18
    hexes = ((xx + (yy // 6) % 2 * 4) % 8 < 6) & (yy % 6 < 4)
    img[hexes] = 45
    img[:3] = img[-3:] = 170
    img[:, :3] = img[:, -3:] = 170
    return img


def mat_dish():
    s = 4 * PPB
    yy, xx = np.mgrid[0:s, 0:s]
    d = np.hypot(xx - s / 2, yy - s / 2) / (s / 2)
    ang = np.arctan2(yy - s / 2, xx - s / 2)
    img = np.zeros((s, s, 3)) + 236
    img -= (d * 30)[..., None]
    img[(np.abs((d * 8) % 1 - 0.5) < 0.04)] = 200
    img[(np.abs(np.sin(ang * 6)) < 0.03)] = 195
    img[d < 0.12] = [90, 92, 95]
    img[d > 0.97] = 160
    return streaks(img, 100, 0.12)


def mat_screen():
    s = PPB
    yy, xx = np.mgrid[0:s, 0:s]
    img = np.zeros((s, s, 3)) + [20, 40, 90]
    img[yy > s * 0.62] = [40, 120, 50]
    img[(np.abs(xx - s * 0.6) < s * 0.08 + (s * 0.62 - yy) * 0.15) & (yy < s * 0.62) & (yy > s * 0.2)] = [120, 120, 130]
    img[:4] = [10, 10, 14]
    img[4:6, 4:20] = [240, 240, 240]
    return img


MATERIALS = {
    "paint": mat_paint, "armor": lambda: mat_armor(200, False), "armor_dented": lambda: mat_armor(300, True),
    "steel": mat_steel, "plastic": mat_plastic, "glass": mat_glass, "tire": mat_tire, "rim": mat_rim,
    "headlight": mat_headlight, "taillight": mat_taillight, "lightbar": mat_lightbar, "solar": mat_solar,
    "wood": mat_wood, "fabric": mat_fabric, "vinyl": mat_vinyl, "grille": mat_grille, "dish": mat_dish,
    "screen": mat_screen,
}


def materials():
    for name, make in MATERIALS.items():
        img = make()
        if img.shape[2] == 3:
            write_png(img, ASSETS, "textures", "entity", "material", name + ".png")
        else:
            write_png(img, ASSETS, "textures", "entity", "material", name + ".png")


# =============================================================================== devices (blocks, items, recipes)

def noise(seed, h, w, base, amount):
    rng = np.random.default_rng(seed)
    img = np.zeros((h, w, 3)) + np.array(base, float)
    return img + rng.normal(0, amount, (h, w, 1))


def screen_texture():
    """A tiny radar scope: dark screen, green rings, a red hook echo."""
    img = np.zeros((16, 16, 3)) + [10, 16, 24]
    yy, xx = np.mgrid[0:16, 0:16]
    d = np.hypot(xx - 7.5, yy - 7.5)
    for r in (3, 6):
        img[np.abs(d - r) < 0.5] = [40, 150, 80]
    blob = np.hypot(xx - 10, yy - 5) < 2.2
    img[blob] = [220, 200, 40]
    img[np.hypot(xx - 10.5, yy - 5) < 1.1] = [230, 40, 30]
    img[7:9, 7:9] = [48, 224, 255]
    img[0, :] = img[:, 0] = [30, 36, 44]
    return img


def keyboard_texture(base, key):
    img = np.zeros((16, 16, 3)) + base
    for y in range(3, 14, 3):
        for x in range(1, 15, 2):
            img[y:y + 2, x] = key
    img[13:15, 5:11] = np.array(base) + 20
    return img


def tower_texture():
    img = noise(11, 16, 16, [24, 24, 28], 3)
    for y in range(16):
        r, g, b = [int(c * 255) for c in __import__("colorsys").hsv_to_rgb(y / 16, 0.8, 1)]
        img[y, 2] = [r, g, b]
    img[3:13, 6:14] = [14, 14, 16]
    for y in range(4, 12, 2):
        img[y, 7:13] = [40, 40, 46]
    return img


def cube(fr, to, faces, rot=None):
    el = {"from": fr, "to": to, "faces": {}}
    for face, tex in faces.items():
        el["faces"][face] = {"texture": "#" + tex}
    if rot:
        el["rotation"] = rot
    return el


def all_faces(tex, screen_face=None, screen_tex=None):
    f = {k: tex for k in ("north", "south", "east", "west", "up", "down")}
    if screen_face:
        f[screen_face] = screen_tex
    return f


def device_blocks():
    write_png(noise(1, 16, 16, [22, 23, 26], 2), ASSETS, "textures", "block", "device_bezel.png")
    write_png(noise(2, 16, 16, [52, 55, 60], 3), ASSETS, "textures", "block", "device_case.png")
    write_png(screen_texture(), ASSETS, "textures", "block", "device_screen.png")
    write_png(keyboard_texture([40, 42, 46], [20, 21, 24]), ASSETS, "textures", "block", "laptop_keys.png")
    write_png(tower_texture(), ASSETS, "textures", "block", "pc_tower.png")

    textures = {"bezel": f"{NS}:block/device_bezel", "case": f"{NS}:block/device_case",
                "screen": f"{NS}:block/device_screen", "keys": f"{NS}:block/laptop_keys",
                "tower": f"{NS}:block/pc_tower", "particle": f"{NS}:block/device_case"}
    computer = [
        cube([4, 0, 7], [11, 1, 11], all_faces("case")),
        cube([7, 1, 8], [9, 5, 10], all_faces("case")),
        cube([1, 4, 6], [15, 14, 8], all_faces("bezel", "north", "screen")),
        cube([3, 0, 2], [11, 0.5, 5], all_faces("bezel", "up", "keys")),
        cube([12, 0, 8], [15, 8, 15], all_faces("bezel", "west", "tower")),
    ]
    laptop = [
        cube([2, 0, 3], [14, 1, 12], all_faces("case", "up", "keys")),
        cube([2, 1, 12], [14, 9, 13], all_faces("bezel", "north", "screen")),
    ]
    for name, elements in (("computer", computer), ("laptop", laptop)):
        write_json({"parent": "minecraft:block/block", "textures": textures, "elements": elements},
                   ASSETS, "models", "block", name + ".json")
        write_json({"variants": {f"facing={f}": {"model": f"{NS}:block/{name}", "y": y}
                                 for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}},
                   ASSETS, "blockstates", name + ".json")
        write_json({"parent": f"{NS}:block/{name}"}, ASSETS, "models", "item", name + ".json")
        write_json({"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{name}"}],
                                                          "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
                   ROOT, "data", NS, "loot_table", "blocks", name + ".json")
    write_json({"values": [f"{NS}:computer", f"{NS}:laptop"]}, ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json")


HANDHELD = {
    "repair_kit": ("""
................
................
.....KKKKKK.....
.....K....K.....
..RRRRRRRRRRRR..
..RRRRRRRRRRRR..
..RRRRWWWWRRRR..
..RRRRRWWRRRRR..
..RRRRRWWRRRRR..
..RRRRRRRRRRRR..
..RRRRRRRRRRRR..
..DDDDDDDDDDDD..
................
................
................
................
""", {"K": "2a2c30", "R": "c0201a", "W": "e8e8e8", "D": "7a1410"}),
    "macbook": ("""
................
................
...KKKKKKKKKK...
...KSSSSSSSSK...
...KSSSSSSSSK...
...KSSSGSSSSK...
...KSSSSSSSSK...
...KSSSSSSSSK...
...KKKKKKKKKK...
..MMMMMMMMMMMM..
.MLLLLLLLLLLLLM.
.MMMMMMTTMMMMMM.
..MMMMMMMMMMMM..
................
................
................
""", {"K": "0b0b0c", "S": "18324a", "G": "40d060", "M": "c9cdd2", "L": "1c1d20", "T": "b0b4b9"}),
    "ipad": ("""
................
...KKKKKKKKKK...
..KKKKKKKKKKKK..
..KSSSSSSSSSSK..
..KSSSRSSSSSSK..
..KSSRRSSSSSSK..
..KSSSSSSSSSSK..
..KSSSSSGSSSSK..
..KSSSSSSSSSSK..
..KSSSSSSSSSSK..
..KSSSSSSSSSSK..
..KSSSSSSSSSSK..
..KKKKKKKKKKKK..
...KKKKKKKKKK...
................
................
""", {"K": "2e3034", "S": "102030", "R": "e04030", "G": "40d060"}),
}


def item(tag):
    return {"tag": tag[1:]} if tag.startswith("#") else {"item": tag}


def shaped(name, pattern, key):
    write_json({"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern,
                "key": {k: item(v) for k, v in key.items()}, "result": {"id": f"{NS}:{name}", "count": 1}},
               ROOT, "data", NS, "recipe", name + ".json")


def recipes():
    shaped("computer", ["GGG", "GQG", "IRI"], {"G": "minecraft:glass_pane", "Q": "minecraft:quartz", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"})
    shaped("laptop", ["GQG", "IRI"], {"G": "minecraft:glass_pane", "Q": "minecraft:quartz", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"})
    write_json({"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [item(f"{NS}:laptop"), item("minecraft:apple"), item("minecraft:iron_ingot")],
                "result": {"id": f"{NS}:macbook", "count": 1}}, ROOT, "data", NS, "recipe", "macbook.json")
    shaped("ipad", ["IGI", "IQI", "IRI"], {"G": "minecraft:glass_pane", "Q": "minecraft:quartz", "R": "minecraft:redstone", "I": "minecraft:iron_ingot"})
    rod, cart = "minecraft:lightning_rod", "minecraft:minecart"
    shaped("gaming_van", ["WWW", "WCW", "IMI"], {"W": "minecraft:white_concrete", "C": f"{NS}:computer", "I": "minecraft:iron_ingot", "M": cart})
    shaped("dominator_1", ["III", "BMB", "P P"], {"I": "minecraft:iron_ingot", "B": "minecraft:black_concrete", "M": cart, "P": rod})
    shaped("dominator_2", ["KKK", "K1K", "P P"], {"K": "minecraft:iron_block", "1": f"{NS}:dominator_1", "P": rod})
    shaped("dominator_3", ["OKO", "K2K", "PPP"], {"O": "minecraft:obsidian", "K": "minecraft:iron_block", "2": f"{NS}:dominator_2", "P": rod})
    shaped("tiv_1", ["KKK", "KMK", "OPO"], {"K": "minecraft:iron_block", "M": cart, "O": "minecraft:obsidian", "P": rod})
    shaped("rental_car", ["GGG", "IMI"], {"G": "minecraft:glass_pane", "I": "minecraft:iron_ingot", "M": cart})
    write_json({"type": "minecraft:crafting_shapeless", "category": "misc",
                "ingredients": [item("minecraft:iron_ingot"), item("minecraft:iron_ingot"), item("minecraft:redstone"),
                                item("minecraft:leather"), item("minecraft:slime_ball")],
                "result": {"id": f"{NS}:repair_kit", "count": 2}}, ROOT, "data", NS, "recipe", "repair_kit.json")
    shaped("doppler_on_wheels", [" D ", "WCW", "IMI"], {"D": "minecraft:daylight_detector", "W": "minecraft:white_concrete", "C": f"{NS}:computer", "I": "minecraft:iron_ingot", "M": cart})


def devices():
    device_blocks()
    for name, (art, paint) in HANDHELD.items():
        write_png(ascii_icon(art, {**BASE, **paint}), ASSETS, "textures", "item", name + ".png")
        write_json({"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}},
                   ASSETS, "models", "item", name + ".json")
    recipes()


# =============================================================================== structures

def nbt(tag_type, value):
    """Encodes an NBT payload. Compounds are dicts of name -> (type, value); lists are (elem_type, [values])."""
    if tag_type == 3:
        return struct.pack(">i", value)
    if tag_type == 8:
        b = value.encode()
        return struct.pack(">H", len(b)) + b
    if tag_type == 9:
        elem, items = value
        return struct.pack(">bi", elem if items else 0, len(items)) + b"".join(nbt(elem, v) for v in items)
    if tag_type == 10:
        out = b""
        for name, (t, v) in value.items():
            n = name.encode()
            out += struct.pack(">bH", t, len(n)) + n + nbt(t, v)
        return out + b"\x00"
    raise ValueError(tag_type)


def structure(name, sx, sy, sz, floor):
    """A GameTest arena: a floor of {floor} with air above, sx by sy by sz."""
    blocks = [{"pos": (9, (3, [x, y, z])), "state": (3, 0 if y == 0 else 1)}
              for x in range(sx) for y in range(sy) for z in range(sz)]
    root = {
        "DataVersion": (3, 3955),
        "size": (9, (3, [sx, sy, sz])),
        "palette": (9, (10, [{"Name": (8, floor)}, {"Name": (8, "minecraft:air")}])),
        "blocks": (9, (10, blocks)),
        "entities": (9, (10, [])),
    }
    data = b"\x0a\x00\x00" + nbt(10, root)
    with open(path(ROOT, "data", NS, "structure", name + ".nbt"), "wb") as f:
        f.write(gzip.compress(data, mtime=0))


if __name__ == "__main__":
    textures()
    materials()
    devices()
    sounds()
    structure("pad", 17, 5, 17, "minecraft:smooth_stone")
    print("assets written to", ROOT)
