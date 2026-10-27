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
    sounds()
    structure("pad", 17, 5, 17, "minecraft:smooth_stone")
    print("assets written to", ROOT)
