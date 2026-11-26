# -*- coding: utf-8 -*-
"""旅の地図の絵を、書き出した列 (MapDump の .smap) から 1 ブロック 1 点で描く。

使い方: python -I tools/gen/world/map/render_map.py <書き出し.smap ...> --out <絵.png>

色: ブロックの上の面の絵の平均の色 (バニラは client-extra の jar、mod は assets/skyabyss)。
草・葉・水はバイオームの色で染める。高さの陰は北 (z-1) と比べて明るく・暗く。水は深さで底の色と混ぜる。
"""
import gzip
import json
import struct
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[4]
JAR = ROOT / "build/moddev/artifacts/neoforge-21.1.259-client-extra-aka-minecraft-resources.jar"
MOD = ROOT / "src/main/resources"


def load_smap(path):
    with gzip.open(path, "rb") as f:
        data = f.read()
    assert data[:8] == b"SKYMAP01", path
    n = struct.unpack(">i", data[8:12])[0]
    head = json.loads(data[12:12 + n].decode("utf-8"))
    o = 12 + n
    w, h = head["w"], head["h"]
    c = w * h

    def take(dtype, count):
        nonlocal o
        a = np.frombuffer(data, dtype=dtype, count=count, offset=o)
        o += a.nbytes
        return a.reshape(h, w)

    head["top"] = take("<u2", c)
    head["y"] = take("<i2", c)
    head["depth"] = take("u1", c)
    head["floor"] = take("<u2", c)
    head["biome"] = take("u1", c)
    return head


class Assets:
    def __init__(self):
        self.jar = zipfile.ZipFile(JAR)
        self.cache = {}

    def read(self, ns, path):
        if ns == "minecraft":
            try:
                return self.jar.read(f"assets/minecraft/{path}")
            except KeyError:
                return None
        p = MOD / "assets" / ns / path
        return p.read_bytes() if p.exists() else None

    def js(self, ns, path):
        b = self.read(ns, path)
        return json.loads(b) if b else None

    def model_textures(self, ref):
        ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", "", ref)
        tex = {}
        while p:
            m = self.js(ns, f"models/{p}.json")
            if not m:
                break
            for k, v in m.get("textures", {}).items():
                tex.setdefault(k, v)
            par = m.get("parent")
            if not par:
                break
            ns, _, p = par.partition(":") if ":" in par else ("minecraft", "", par)
        return tex

    def block_texture(self, name):
        ns, _, b = name.partition(":")
        st = self.js(ns, f"blockstates/{b}.json")
        model = None
        if st:
            if "variants" in st:
                v = next(iter(st["variants"].values()))
                model = (v[0] if isinstance(v, list) else v)["model"]
            elif "multipart" in st:
                a = st["multipart"][0]["apply"]
                model = (a[0] if isinstance(a, list) else a)["model"]
        tex = self.model_textures(model) if model else {}
        for key in ("top", "end", "all", "texture", "cross", "plant", "particle", "side"):
            t = tex.get(key)
            seen = 0
            while t and t.startswith("#") and seen < 8:
                t = tex.get(t[1:])
                seen += 1
            if t and not t.startswith("#"):
                return t
        for t in tex.values():
            if not t.startswith("#"):
                return t
        return None

    def color(self, name):
        if name in self.cache:
            return self.cache[name]
        rgb = None
        t = self.block_texture(name)
        if t:
            ns, _, p = t.partition(":") if ":" in t else ("minecraft", "", t)
            b = self.read(ns, f"textures/{p}.png")
            if b:
                import io
                im = Image.open(io.BytesIO(b)).convert("RGBA")
                w = im.width
                a = np.asarray(im.crop((0, 0, w, w)), dtype=float)
                m = a[..., 3] > 16
                if m.any():
                    rgb = a[..., :3][m].mean(axis=0)
        if rgb is None:
            rgb = np.array([255.0, 0, 255])
            print("no colour:", name, t)
        self.cache[name] = rgb
        return rgb


# 草の色で染める・葉の色で染める・固定の色で染めるブロック
GRASS = {"grass_block", "short_grass", "tall_grass", "fern", "large_fern", "sugar_cane"}
FOLIAGE = {"oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine"}
FIXED = {"birch_leaves": 0x80A755, "spruce_leaves": 0x619961, "lily_pad": 0x208030}


def rgb_of(v):
    return np.array([(v >> 16) & 255, (v >> 8) & 255, v & 255], dtype=float)


def render(maps, assets):
    x0 = min(m["x0"] for m in maps)
    z0 = min(m["z0"] for m in maps)
    x1 = max(m["x0"] + m["w"] for m in maps)
    z1 = max(m["z0"] + m["h"] for m in maps)
    W, H = x1 - x0, z1 - z0
    col = np.zeros((H, W, 3), dtype=np.float32)
    hgt = np.zeros((H, W), dtype=np.float32)
    wat = np.zeros((H, W), dtype=bool)
    # 水の色はバイオームの境で円や線が出ないよう、広くぼかしてなじませる (ゲームの中の水も境でなじむ)
    from scipy import ndimage
    wtint = np.zeros((H, W, 3), dtype=np.float32)
    for m in maps:
        ox, oz = m["x0"] - x0, m["z0"] - z0
        wtint[oz:oz + m["h"], ox:ox + m["w"]] = np.array([rgb_of(b["water"]) for b in m["biomes"]])[m["biome"]]
    for ch in range(3):
        wtint[..., ch] = ndimage.gaussian_filter(wtint[..., ch], 24)
    for m in maps:
        names = [b.split(":", 1)[1] if b.startswith("minecraft:") else b for b in m["blocks"]]
        pal = np.array([assets.color(b if ":" in b else "minecraft:" + b) for b in names])
        bio = m["biomes"]
        top, floor, depth, biome = m["top"], m["floor"], m["depth"], m["biome"]
        c = pal[top].copy()
        # 染める
        tint = np.ones((len(names), 1))
        for i, n in enumerate(names):
            if n in GRASS:
                c_mask = top == i
                gr = np.array([rgb_of(b["grass"]) for b in bio])[biome[c_mask]]
                c[c_mask] = c[c_mask] * gr / 255.0
            elif n in FOLIAGE:
                c_mask = top == i
                fo = np.array([rgb_of(b["foliage"]) for b in bio])[biome[c_mask]]
                c[c_mask] = c[c_mask] * fo / 255.0
            elif n in FIXED:
                c_mask = top == i
                c[c_mask] = c[c_mask] * rgb_of(FIXED[n]) / 255.0
        # 水: 水の色で染めた水と、底の色を深さで混ぜる
        wm = depth > 0
        if wm.any():
            wox, woz = m["x0"] - x0, m["z0"] - z0
            wc = wtint[woz:woz + m["h"], wox:wox + m["w"]][wm]
            water = assets.color("minecraft:water") * wc / 255.0
            fl = pal[floor[wm]]
            k = np.clip(depth[wm].astype(float) / 10.0, 0.25, 0.92)[:, None]
            c[wm] = water * k + fl * (1 - k) * 0.85
        ox, oz = m["x0"] - x0, m["z0"] - z0
        col[oz:oz + m["h"], ox:ox + m["w"]] = c
        hgt[oz:oz + m["h"], ox:ox + m["w"]] = np.where(wm, m["y"].astype(float), m["y"])
        wat[oz:oz + m["h"], ox:ox + m["w"]] = wm
    # 高さの陰 (北と比べる。水の面は平らなので陰なし)
    north = np.vstack([hgt[:1], hgt[:-1]])
    d = hgt - north
    shade = np.where(d > 0, 1.12, np.where(d < 0, 0.86, 1.0))
    shade = np.where(np.abs(d) >= 3, np.where(d > 0, 1.2, 0.76), shade)
    shade = np.where(wat, 1.0, shade)
    col = col * shade[..., None]
    return Image.fromarray(np.clip(col, 0, 255).astype(np.uint8)), (x0, z0)


TILE = 512
TILES = 8
ORIGIN = -2048
TILE_DIR = MOD / "assets/skyabyss/textures/gui/map/blocks"


def main():
    """--out <絵.png>: 一枚に描く (試し)。--tiles: 全土を区画の絵にして mod へ入れる (TravelMapScreen.drawBlocks が読む)。"""
    args = sys.argv[1:]
    files = [a for a in args if a.endswith(".smap")]
    if "--tiles" in args and not files:
        files = sorted(str(p) for p in (ROOT / "run/shots/map_dump/dumps").glob("map_*_*.smap") if p.stem.count("_") == 2)
    maps = [load_smap(f) for f in files]
    img, origin = render(maps, Assets())
    if "--tiles" not in args:
        out = Path(args[args.index("--out") + 1]) if "--out" in args else Path("map.png")
        img.save(out, optimize=True)
        print("wrote", out, img.size, "origin", origin)
        return
    assert origin == (ORIGIN, ORIGIN) and img.size == (TILE * TILES, TILE * TILES), (origin, img.size)
    TILE_DIR.mkdir(parents=True, exist_ok=True)
    total = 0
    for j in range(TILES):
        for i in range(TILES):
            p = TILE_DIR / f"{i}_{j}.png"
            img.crop((i * TILE, j * TILE, (i + 1) * TILE, (j + 1) * TILE)).save(p, optimize=True)
            total += p.stat().st_size
    for n in (1024, 256):
        p = TILE_DIR / f"all_{n}.png"
        img.resize((n, n), Image.BOX).save(p, optimize=True)
        total += p.stat().st_size
    # 絵の外の海の色 (四隅の平均。TravelMapScreen.SEA_COLOR)
    a = np.asarray(img, dtype=float)
    corner = np.concatenate([a[:64, :64].reshape(-1, 3), a[-64:, -64:].reshape(-1, 3)]).mean(axis=0).astype(int)
    print("tiles", TILES * TILES, "+ 2 overviews,", round(total / 1e6, 1), "MB; sea colour 0xFF%02X%02X%02X" % tuple(corner))


if __name__ == "__main__":
    main()
