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
    devices()
    sounds()
    structure("pad", 17, 5, 17, "minecraft:smooth_stone")
    print("assets written to", ROOT)
