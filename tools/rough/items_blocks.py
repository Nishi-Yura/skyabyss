"""items_blocks: アイテム9種・ブロック12面を 16x16 のピクセルアートで3案 (A/B/C) 描く。

実行 (リポジトリ直下):  python tools/rough/items_blocks.py
出力:
  docs/roughs/items_blocks/<a|b|c>/item/*.png
  docs/roughs/items_blocks/<a|b|c>/block/*.png
  docs/roughs/items.png, docs/roughs/blocks.png (比較シート、列が A/B/C)
  docs/roughs/items_blocks/<a|b|c>/preview/*.png (ブロックの3x3タイル、立方体)
  docs/roughs/items_blocks/<a|b|c>/voxel/*.png (アイテムの3D 2方向)

絵は手で置いた文字配列。文字 -> 色は案ごとのパレットで決まる。
A = バニラ寄りの色と陰影、B = 高コントラスト + 彩度の高い水色の共鳴アクセント、C = くすんだ古代風。
"""
from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools/rough"))
from voxel import Face, extrude, voxel_faces, render, sheet, icon_big  # noqa: E402

OUT = ROOT / "docs/roughs"
SUB = OUT / "items_blocks"
STYLES = ["a", "b", "c"]
STYLE_NAME = {"a": "A", "b": "B", "c": "C"}

# =====================================================================
# アイテム 16x16。文字の意味 (案ごとに色が変わる):
#   o 輪郭  d 暗部  m 中間  l 明部  h 最明  a アクセント主  b アクセント暗  g 発光  w 白の差し色
# =====================================================================
ITEM_ART = {
    "cloud_jerky": [
        "................",
        "................",
        "...oooooooooo...",
        "..ollllhhmmmmo..",
        ".olllhhmmmaammo.",
        ".olhhmmmaaaammdo",
        "olhmmmaaaaamdmdo",
        "olmmmmaaaamdmmdo",
        ".olmmmmmmmdmmdo.",
        ".odmmmmmmdddmdo.",
        "..oddmmdddddo...",
        "...oooooooooo...",
        "................",
        "................",
        "................",
        "................",
    ],
    "moss_talisman": [
        ".......ww.......",
        "......w..w......",
        "......w..w......",
        ".....oooooo.....",
        "....oShhSSSo....",
        "...oShhSSSSSSo..",
        "..oShSSMMSSSSSo.",
        "..oSMMmMSSSSsSo.",
        ".oSMmMMSSSSsssSo",
        ".oSnMMSSSSSssssd",
        ".oSnMgSSSSSsssdo",
        "..oSnnMSSSsssdo.",
        "..odSnnSSSsssdo.",
        "...odSSSSssdoo..",
        "....oddddddo....",
        ".....oooooo.....",
    ],
    "resonance_charm": [
        ".......oo.......",
        "......oMMo......",
        ".....oM..Mo.....",
        ".....oM..Mo.....",
        "....oooMMooo....",
        "...oMMMhhMMMo...",
        "..oMhhMMMMMMdo..",
        "..oMhaaaaaa.dMo.",
        "..oMhagggggahdo.",
        ".oMMagwggggabdMo",
        ".oMdagggggabbdMo",
        ".oMdaagggaabdMo.",
        "..oMdaaaaabbdo..",
        "..oddMaaabbdd...",
        "...oooddddoo....",
        "....ooooooo.....",
    ],
    "resonance_crystal": [
        "......oo........",
        ".....oll.oo.....",
        ".....olhlo......",
        "....olhhllo.....",
        "....olhlallo....",
        "...olhalaaallo..",
        "...olhalaaallo..",
        "...oalaaaaaldo..",
        "..oalaaaaaaddo..",
        "..oalhaaaaaddo..",
        "..oaaaaaaadbdo..",
        ".oppoaaaaabbdoo.",
        ".opqqppoaabbdo..",
        ".opqqppo.oddo...",
        "..ooqqo..oo.....",
        "....oo..........",
    ],
    "resonance_shard": [
        "................",
        "..........oo....",
        ".........olho...",
        "........olhlao..",
        ".......olhllao..",
        "......olppllqo..",
        "......opplpqo...",
        ".....oppllpqo...",
        ".....oppppqqo...",
        "......opqqqo....",
        "......oqqqoo....",
        ".......oooo.....",
        "................",
        "................",
        "................",
        "................",
    ],
    "sky_key_fragment": [
        "................",
        "....oooooooo....",
        "...ohhmmmmmmo...",
        "..ohmmmmmmmmdo..",
        "..ohmmamaaammdo.",
        ".ohmamaaaaaadmo.",
        ".ohmaaaaaaadmmdo",
        ".ohmmaaaaaammmdo",
        ".omamaaaaaadmmdo",
        ".omamaaaaadmmdo.",
        ".omaaaaaadmmmdo.",
        "..oaaaaadmmdo...",
        "..oaadddmdddo...",
        "...oddddddo.....",
        "....oooooo......",
        "................",
    ],
    "stone_whale_core": [
        "................",
        "...oooooooo.....",
        "..odddmmmmmd....",
        ".odmmmllllmmdo..",
        ".odmllhhhllmmdo.",
        ".odmlhgagllmdmo.",
        ".odmlhagggmmdmo.",
        ".odmmlagggmmdmo.",
        ".odmmlaggmmdmmo.",
        ".odmmmlaaamdmmo.",
        ".odmmmmmmdmdmdo.",
        "..odmmdmmdddddo.",
        "...oddddddddo...",
        "....oooooooo....",
        "................",
        "................",
    ],
    "stone_whale_plating": [
        "................",
        "....oooooooo....",
        "...odllllllllo..",
        "..odlhhhhhhllo..",
        "..olhssssssmdo..",
        ".olhsstttttsmdo.",
        ".olhstgggggtsmo.",
        ".olhstgaaagtsdo.",
        ".olhstgaaagtsdo.",
        ".olhstgggggtsmdo",
        ".odmstttttssmdo.",
        "..odmsssssssdo..",
        "..odddmmmmddo...",
        "...oooooooooo...",
        "................",
        "................",
    ],
    "stone_whale_shard": [
        "................",
        "................",
        ".......oo.......",
        "......olho......",
        ".....olhhlo.....",
        "....olhmmmmlo...",
        "...olhmmcmmmlo..",
        "..olhmmmcmmmmdo.",
        "..olmmmmcmmdmdo.",
        "..olmmmmmcdddo..",
        "..odmmmmcmmddo..",
        "...odmmmcmmdo...",
        "....odddmdoo....",
        "......oooo......",
        "................",
        "................",
    ],
}

ITEM_PAL = {
    "a": {
        "cloud_jerky": dict(o="#3a1b12", d="#6a2d1c", m="#9a4a2f", l="#b8643f", h="#d9866a", a="#efe3cc", b="#c9b8a0"),
        "moss_talisman": dict(o="#1c1f22", d="#41464b", S="#6d7176", M="#4b9a36", m="#3f8a2e", h="#b5b9bd", l="#8e9297",
                              s="#4a4f54", n="#2b6a22", g="#9ad86c", w="#7a5a3a"),
        "resonance_charm": dict(o="#1b2630", d="#4a5560", M="#8a96a0", m="#6b7782", h="#e4ecf2", a="#3fb8e8", b="#1f6f9a",
                                g="#9ff0ff", w="#ffffff"),
        "resonance_crystal": dict(o="#16384d", d="#1f5f80", a="#3fc2ee", b="#1d7fb0", l="#8ee6ff", h="#f0fdff",
                                  p="#7a4fc2", q="#4b2a86"),
        "resonance_shard": dict(o="#2a1b4d", l="#b9a2ff", h="#f4f0ff", p="#8a5fe0", q="#4f2d9a", a="#7ad8ff"),
        "sky_key_fragment": dict(o="#2a3038", d="#5d6670", m="#8a929b", l="#a9b1ba", h="#d7dde3", a="#3fc8f0",
                                 b="#1c7fb2", g="#8ff0ff", w="#ffffff"),
        "stone_whale_core": dict(o="#1d2530", d="#3c4855", m="#5e6e7c", l="#8394a2", h="#c8d6e0", a="#3fd0f5",
                                 b="#1a6e9a", g="#c5f8ff"),
        "stone_whale_plating": dict(o="#2a1c08", d="#8a5d1c", m="#c08a2c", l="#e6bd5a", h="#fff1b8", s="#2e3440",
                                    t="#4c5566", u="#6b7688", a="#3fd0f5", g="#8ae8ff"),
        "stone_whale_shard": dict(o="#20242a", d="#3a4048", m="#68707a", l="#8c949e", h="#c3cad2", c="#3fd0f5"),
    },
    "b": {
        "cloud_jerky": dict(o="#230a10", d="#7a1f2a", m="#c2353e", l="#e0606a", h="#ffb1a8", a="#ffffff", b="#6fe6ff"),
        "moss_talisman": dict(o="#0c1014", d="#20262e", S="#7f8ea3", M="#2fe07a", m="#17b35e", h="#d4e2f0", l="#9dacc0",
                              s="#354050", n="#0c7a3e", g="#b6ffb0", w="#f0b060"),
        "resonance_charm": dict(o="#0a0d14", d="#20242c", M="#9aa6b8", m="#5c6674", h="#dfe8f5", a="#00d8ff", b="#0066b0",
                                g="#b8fbff", w="#ffffff"),
        "resonance_crystal": dict(o="#041a33", d="#0b4f8a", a="#00d8ff", b="#0088cc", l="#8ff2ff", h="#ffffff",
                                  p="#b04dff", q="#5a1ba8"),
        "resonance_shard": dict(o="#1a0630", l="#d6b2ff", h="#ffffff", p="#a24dff", q="#5a1ba8", a="#00e0ff"),
        "sky_key_fragment": dict(o="#070a10", d="#2c3644", m="#58687c", l="#8ea0b6", h="#e6f0ff", a="#00d8ff",
                                 b="#0a6fb0", g="#b8fbff", w="#ffffff"),
        "stone_whale_core": dict(o="#05080d", d="#1d2736", m="#34445a", l="#5b7290", h="#e1f6ff", a="#00e0ff",
                                 b="#0076b5", g="#d6fdff"),
        "stone_whale_plating": dict(o="#0a0700", d="#a2620a", m="#ffb81f", l="#ffe27a", h="#ffffff", s="#0d1118",
                                    t="#1e2736", u="#34435a", a="#00e0ff", g="#b8fbff"),
        "stone_whale_shard": dict(o="#070a10", d="#1f2630", m="#4a5566", l="#7d8aa0", h="#dce6f2", c="#00e0ff"),
    },
    "c": {
        "cloud_jerky": dict(o="#2e2a25", d="#5a4a3c", m="#7d6a57", l="#97846f", h="#b5a18a", a="#d8cdb5", b="#a39a8a"),
        "moss_talisman": dict(o="#22221f", d="#3c403b", S="#6b716a", M="#6d7a52", m="#5a6845", h="#a7aea2", l="#858c82",
                              s="#4a5049", n="#46503a", g="#9aa67d", w="#6e5a44"),
        "resonance_charm": dict(o="#241f19", d="#4b4032", M="#a8957a", m="#7b6a4f", h="#cdbda5", a="#5fa3b5", b="#35606b",
                                g="#a7d6dc", w="#e6f3ef"),
        "resonance_crystal": dict(o="#243b40", d="#3a6670", a="#6fa9b5", b="#4b7f8a", l="#a7d6dc", h="#e6f3ef",
                                  p="#7d6a8e", q="#54475f"),
        "resonance_shard": dict(o="#2b2633", l="#b7a9c8", h="#ece6f2", p="#8c7aa0", q="#5b4d6a", a="#7fa9b2"),
        "sky_key_fragment": dict(o="#2a2e2c", d="#4f5752", m="#77807a", l="#9aa39c", h="#c7cdc6", a="#6fa9b5",
                                 b="#3f6c76", g="#a7d6dc", w="#e6f3ef"),
        "stone_whale_core": dict(o="#1e2320", d="#3b4540", m="#5d6962", l="#7f8c84", h="#bfcbc3", a="#6fa9b5",
                                 b="#3f6c76", g="#c3e4e8"),
        "stone_whale_plating": dict(o="#2a2116", d="#6b5224", m="#94763a", l="#b89a5a", h="#e2d2a4", s="#2f3431",
                                    t="#4d5550", u="#6a7470", a="#6fa9b5", g="#a7d6dc"),
        "stone_whale_shard": dict(o="#22251f", d="#3a3f38", m="#666d63", l="#858d82", h="#b6beb2", c="#6fa9b5"),
    },
}

# =====================================================================
# ブロック 16x16 (タイル可能)。文字は案ごとの BLOCK_PAL で色になる。
# =====================================================================
BLOCK_ART = {
    "crumbling_bone_top": [
        "mmmmmmmmmmmmmmmm",
        "mmlllmmmmmmmmmmm",
        "mlmmmmmmmmdmmmmm",
        "mlmmmmmmmdmmmmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmpppmmmmmmmm",
        "mmmmpqqpmmmmmmmm",
        "mmmmmpppmmmmmmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmmmmmmmmlmmm",
        "mmmmmmmmmmmlhlmm",
        "mmmmmmmmmmmmlmmm",
        "mdmmmmmmmmmmmmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmmmmdmmmmmmm",
        "mmmmmmmmmmmmmmmm",
    ],
    "crumbling_bone_side": [
        "mmdmmmmmmmmmdmmm",
        "mmdmmmmmmmmmdmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmpppmmmmmmmm",
        "mmmmmpqpmmmmmmmm",
        "mmmmmmmmmmmmlmmm",
        "mmmmmmmmmmmmlmmm",
        "dmmmmmmmmmmmmmmm",
        "mmmmmmmmmdmmmmmm",
        "mmmmmmmmdmmmmmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmmlllmmmmmmm",
        "mmdmmmmmmmmmmmmm",
        "mmdmmmmmmmmmmmmm",
        "mmmmmmmmmmmmmmmm",
        "mmmmmmmmmmdmmmmm",
    ],
    "resonance_stone": [
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssvvvvssssss",
        "sssssvssssvsssss",
        "ssssvssssssvssss",
        "sssvssssssssvsss",
        "sssvssssssssvsss",
        "sssvssssssssvsss",
        "ssssvssssssvssss",
        "sssssvssssvsssss",
        "ssssssvvvvssssss",
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssssssssssss",
        "ssssssssssssssss",
    ],
    "sealed_gate": [
        "eeeeeeeeeeeeeeee",
        "efeeeeeeeeeeeefe",
        "eeffeeeeeeeeffee",
        "eeeffeeeeeeffeee",
        "eeeeffeeeeffeeee",
        "eeeeeffeeffeeeee",
        "eeeeeeffffeeeeee",
        "eeeeeeeffeeeeeee",
        "eeeeeeeffeeeeeee",
        "eeeeeeffffeeeeee",
        "eeeeeffeeffeeeee",
        "eeeeffeeeeffeeee",
        "eeefeeeeeeeefeee",
        "eeffeeeeeeeeffee",
        "efeeeeeeeeeeeefe",
        "eeeeeeeeeeeeeeee",
    ],
    "warded_stone": [
        "nnnnnnnnccccccnn",
        "nrnnnnnnnnnnnnnn",
        "nrnnnnnnnnnnnnrn",
        "nnnnnnnnnnnnnnnn",
        "cccccccccccccccc",
        "nnnnnnnnnnnnnnnn",
        "nrnnnnnnnnrnnnnn",
        "nrnnnnnzzznnnnnn",
        "nnnnnnnzzzznnnnn",
        "nnnnnnnnzzznnnnn",
        "cccccccccccccccc",
        "nnnnnnnnnnnnnnnn",
        "nnnnnnnnnnnnnnnn",
        "nnnnnnnnnnnnnnnn",
        "cccccccccccccccc",
        "nnnnnnnnnnnnnnnn",
    ],
    "resonance_forge_top": [
        "iiiiiiiiiiiiiiii",
        "iIIiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiia",
        "iiiiiiiiooooiiii",
        "iiiiiiooggggooii",
        "iiiiooggGGGGggoi",
        "iiiogGGGGGGGGgoi",
        "iiiogGGGGGGGGgoi",
        "iiiogGGGGGGGGgoi",
        "iiiiogggggggoiia",
        "iiiiiiooggooiiii",
        "iiiiiiiiiooiiiii",
        "aiiiiiiiiiiiiiia",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiia",
    ],
    "resonance_forge_side": [
        "aaaaaaaaaaaaaaaa",
        "iIIIIIIIIIIIIIIi",
        "iiiiiiiiiiiiiiii",
        "ggggggggggggggga",
        "gGGGGGGGGGGGGGGg",
        "ggggggggggggggga",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "aaaaaaaaaaaaaaaa",
        "iiiiiiiiiiiiiiii",
    ],
    "resonance_forge_front": [
        "aaaaaaaaaaaaaaaa",
        "iIIIIIIIIIIIIIIi",
        "iiiiiiiiiiiiiiii",
        "ggggggggggggggga",
        "gGGGGGGGGGGGGGGg",
        "ggggggggggggggga",
        "iiiiiiiiiiiiiiii",
        "iiiiiiiiiiiiiiii",
        "iiiiooooooooiiii",
        "iiiooOOOOOOOOoiii"[:16],
        "iiiooOGGGGGGOoii",
        "iiiooOGGGGGGOoiia"[:16],
        "iiiooOGGGGGGOoii",
        "iiiooOOOOOOOOoii",
        "aaaaaaaaaaaaaaaa",
        "iiiiiiiiiiiiiiii",
    ],
    "resonance_forge_bottom": [
        "aaaaaaaaaaaaaaaa",
        "aiiiiiiiiiiiiiia",
        "aiaiiiiiiiiiiaia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiiiiiiiiiiiiiia",
        "aiaiiiiiiiiiiaia",
        "aiiiiiiiiiiiiiia",
        "aaaaaaaaaaaaaaaa",
    ],
}

def _tile(cell, rx, ry):
    return ["".join(row * rx) for row in cell] * ry


# 骨の面は小さな周期 (セル) を並べて作る。穴は 2x2 の小さな空洞。
BONE_TOP_CELL = [
    "lmmmmmmd",
    "mppmmmmm",
    "mpqmmmmm",
    "mmmmmmmm",
    "mmmmmppm",
    "mmmmmpqm",
    "mmmmmmmm",
    "dmmmmmmm",
]
BONE_SIDE_CELL = [
    "lmmmmmmmmmmmmmml"[:8],
    "mmmppmmm",
    "mmmmmmmm",
    "dddddddd",
]

# 上書き (重ね) 用の文字配列。'.' は下の絵をそのまま使う。
BLOCK_OVERLAY = {
    "crumbling_bone_top_cracking": (
        "crumbling_bone_top",
        [
            "................",
            "........x.......",
            ".......xx.......",
            "......x.........",
            "......x...x.....",
            ".....xx...xx....",
            ".....x.....x....",
            "....x...........",
            "....x....x......",
            "...xx...xxx.....",
            "....x...x.......",
            "........x.......",
            "..........x.....",
            "...........x....",
            "................",
            "................",
        ],
    ),
    "crumbling_bone_side_cracking": (
        "crumbling_bone_side",
        [
            "................",
            "......x.........",
            "......x.........",
            "......xx........",
            ".......x........",
            ".......xx.......",
            "........x.......",
            "........x.......",
            "........xx......",
            "..........x.....",
            "..........x.....",
            "...........xx...",
            "............x...",
            "............x...",
            "................",
            "................",
        ],
    ),
}

# アクティブ版は、共鳴線 (v) を明るい線 (w) に置き換える (文字の置換)。
ACTIVE_MAP = {"v": "w"}

BLOCK_ART["crumbling_bone_top"] = _tile(BONE_TOP_CELL, 2, 2)
BLOCK_ART["crumbling_bone_side"] = _tile(BONE_SIDE_CELL, 2, 4)

BLOCK_PAL = {
    "a": dict(
        m="#d8d0b4", l="#ece6cc", h="#f6f1de", d="#b3a98a", p="#8f8667", q="#6f6850", x="#4a3f2c",
        s="#3b4a55", t="#4d5f6b", u="#2c3941", v="#4fd8ff", w="#bff6ff",
        e="#2b2636", f="#7a4fc2", j="#9c7ae0", k="#4b2a86", n="#7f7f7f", r="#9a9a9a", c="#4a4a4a", z="#5fd6ff",
        i="#55595e", I="#6b7076", a="#2f3338", g="#2fb6e8", G="#7fe8ff", o="#15181c", O="#2a1640"),
    "b": dict(
        m="#e6dcb6", l="#fff4d4", h="#ffffff", d="#a79a72", p="#7a6c4a", q="#4f4530", x="#1e1608",
        s="#1c2636", t="#2b3a50", u="#0f1622", v="#00e5ff", w="#e0fbff",
        e="#170f26", f="#b04dff", j="#d18cff", k="#5a1ba8", n="#8f949c", r="#b7bcc4", c="#3a3f47", z="#00e5ff",
        i="#3c4350", I="#5a6372", a="#161a22", g="#00b8f0", G="#8ff6ff", o="#05070a", O="#34124f"),
    "c": dict(
        m="#b9b29a", l="#d6cfb6", h="#e8e2cc", d="#8f8875", p="#6b634f", q="#4a4536", x="#2a261c",
        s="#3e4a4f", t="#56656b", u="#2e383c", v="#8fc4cf", w="#d4eef2",
        e="#2d2b33", f="#7d6a8e", j="#a593b8", k="#54475f", n="#6f7070", r="#8a8b8a", c="#4a4c4c", z="#8fbfc8",
        i="#56595a", I="#6e7273", a="#3a3d3e", g="#6fa9b5", G="#a7d6dc", o="#222426", O="#3b2f47"),
}

# =====================================================================
# 描画
# =====================================================================

def grid_to_img(rows, pal, expect=16) -> np.ndarray:
    """文字配列を RGBA 配列にする。'.' は透明。長さ違いはエラーにする。"""
    h = len(rows)
    w = len(rows[0])
    for i, r in enumerate(rows):
        if len(r) != w or w != expect or h != expect:
            raise ValueError(f"row {i} len {len(r)} (want {expect}), rows={h}")
    arr = np.zeros((h, w, 4), np.uint8)
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            if ch not in pal:
                raise KeyError(f"no color for {ch!r}")
            arr[y, x] = hex_rgba(pal[ch])
    return arr


def hex_rgba(h: str):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def overlay(base: np.ndarray, ov_rows, pal, active_map=None) -> np.ndarray:
    out = base.copy()
    for y, r in enumerate(ov_rows):
        for x, ch in enumerate(r):
            if ch == ".":
                continue
            out[y, x] = hex_rgba(pal[ch])
    return out


def resonance_stone_active_tex(style):
    """共鳴石 (通常) の v 線を w 線に変えた版。"""
    pal = BLOCK_PAL[style]
    rows = [r.replace("v", "w") for r in BLOCK_ART["resonance_stone"]]
    base = grid_to_img(rows, pal)
    # 発光の縁として、線の周りを g で少し照らす
    out = base.copy()
    for y in range(16):
        for x in range(16):
            if base[y, x, 0] == hex_rgba(pal["w"])[0] and base[y, x, 1] == hex_rgba(pal["w"])[1]:
                for dy, dx in ((0, 1), (1, 0), (0, -1), (-1, 0)):
                    yy, xx = (y + dy) % 16, (x + dx) % 16
                    if out[yy, xx, 0] < 60:
                        out[yy, xx] = hex_rgba(pal["g"])
    return out


def forge_front_tex(style):
    return grid_to_img(BLOCK_ART["resonance_forge_front"], BLOCK_PAL[style])


ITEM_NAMES = list(ITEM_ART)
BLOCK_NAMES = [
    "crumbling_bone_top", "crumbling_bone_top_cracking",
    "crumbling_bone_side", "crumbling_bone_side_cracking",
    "resonance_stone", "resonance_stone_active",
    "sealed_gate", "warded_stone",
    "resonance_forge_top", "resonance_forge_side", "resonance_forge_front", "resonance_forge_bottom",
]


def build_block(name, style):
    pal = BLOCK_PAL[style]
    if name == "crumbling_bone_top":
        return grid_to_img(BLOCK_ART["crumbling_bone_top"], pal)
    if name == "crumbling_bone_side":
        return grid_to_img(BLOCK_ART["crumbling_bone_side"], pal)
    if name == "crumbling_bone_top_cracking":
        return overlay(grid_to_img(BLOCK_ART["crumbling_bone_top"], pal), BLOCK_OVERLAY[name][1], pal)
    if name == "crumbling_bone_side_cracking":
        return overlay(grid_to_img(BLOCK_ART["crumbling_bone_side"], pal), BLOCK_OVERLAY[name][1], pal)
    if name == "resonance_stone":
        return grid_to_img(BLOCK_ART["resonance_stone"], pal)
    if name == "resonance_stone_active":
        return resonance_stone_active_tex(style)
    if name == "sealed_gate":
        return grid_to_img(BLOCK_ART["sealed_gate"], pal)
    if name == "warded_stone":
        return grid_to_img(BLOCK_ART["warded_stone"], pal)
    if name == "resonance_forge_top":
        return grid_to_img(BLOCK_ART["resonance_forge_top"], pal)
    if name == "resonance_forge_side":
        return grid_to_img(BLOCK_ART["resonance_forge_side"], pal)
    if name == "resonance_forge_front":
        return forge_front_tex(style)
    if name == "resonance_forge_bottom":
        return grid_to_img(BLOCK_ART["resonance_forge_bottom"], pal)
    raise KeyError(name)


def build_item(name, style):
    return grid_to_img(ITEM_ART[name], ITEM_PAL[style][name])


def save(img_arr, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(img_arr, "RGBA").save(path)


def tiled(arr: np.ndarray, n=3) -> Image.Image:
    rows = [np.tile(arr, (1, n, 1))] * n
    big = np.concatenate(rows, axis=0)
    return Image.fromarray(big, "RGBA").resize((big.shape[1] * 6, big.shape[0] * 6), Image.NEAREST)


def cube_faces(top, side, front=None, bottom=None, s=16):
    """16x16x16 の立方体の面 (外向きの法線)。top/side/front/bottom は 16x16 RGBA。
    front は +z (南) の面、side は残りの横 (北・東・西) の面。"""
    front = side if front is None else front
    bottom = side if bottom is None else bottom
    S = float(s)
    f = [
        Face(np.array([0, S, 0.0]), np.array([0, 0, S]), np.array([S, 0, 0.0]), top, None, 1.0),        # 上 (+y)
        Face(np.array([0, 0, 0.0]), np.array([S, 0, 0.0]), np.array([0, 0, S]), bottom, None, 0.5),      # 下 (-y)
        Face(np.array([0, 0, S]), np.array([S, 0, 0.0]), np.array([0, S, 0.0]), front, None, 0.8),       # 南 (+z)
        Face(np.array([0, 0, 0.0]), np.array([0, S, 0.0]), np.array([S, 0, 0.0]), side, None, 0.8),      # 北 (-z)
        Face(np.array([S, 0, 0.0]), np.array([0, S, 0.0]), np.array([0, 0, S]), side, None, 0.6),        # 東 (+x)
        Face(np.array([0, 0, 0.0]), np.array([0, 0, S]), np.array([0, S, 0.0]), side, None, 0.6),        # 西 (-x)
    ]
    return f


def build_all():
    for style in STYLES:
        sd = SUB / style
        for name in ITEM_NAMES:
            arr = build_item(name, style)
            save(arr, sd / "item" / f"{name}.png")
        for name in BLOCK_NAMES:
            arr = build_block(name, style)
            save(arr, sd / "block" / f"{name}.png")


def make_previews():
    for style in STYLES:
        sd = SUB / style
        (sd / "preview").mkdir(parents=True, exist_ok=True)
        for name in BLOCK_NAMES:
            arr = np.array(Image.open(sd / "block" / f"{name}.png").convert("RGBA"))
            tiled(arr).save(sd / "preview" / f"{name}_tile3x3.png")
        # キューブ (上 top / 横 side / 正面 front / 下 bottom)
        pal_ = BLOCK_PAL[style]
        top = np.array(Image.open(sd / "block" / "crumbling_bone_top.png").convert("RGBA"))
        side = np.array(Image.open(sd / "block" / "crumbling_bone_side.png").convert("RGBA"))
        rtop = np.array(Image.open(sd / "block" / "resonance_forge_top.png").convert("RGBA"))
        rside = np.array(Image.open(sd / "block" / "resonance_forge_side.png").convert("RGBA"))
        rfront = np.array(Image.open(sd / "block" / "resonance_forge_front.png").convert("RGBA"))
        rbot = np.array(Image.open(sd / "block" / "resonance_forge_bottom.png").convert("RGBA"))
        for label, faces in [
            ("crumbling_bone_cube", cube_faces(top, side)),
            ("resonance_forge_cube", cube_faces(rtop, rside, rfront, rbot)),
        ]:
            img = render(faces, yaw=35, pitch=28, size=(360, 360), bg="dark", bloom=False, outline=False, ss=2)
            img.save(sd / "preview" / f"{label}.png")


def make_voxel_items():
    for style in STYLES:
        sd = SUB / style / "voxel"
        sd.mkdir(parents=True, exist_ok=True)
        for name in ITEM_NAMES:
            icon = np.array(Image.open(SUB / style / "item" / f"{name}.png").convert("RGBA"))
            glow = np.zeros(icon.shape[:2], bool)
            # 発光は明るい色の画素 (a 相当の色) を光らせる
            grid, gl = extrude(icon, glow=glow)
            faces = voxel_faces(grid, gl)
            for yaw in (35, 215):
                img = render(faces, yaw=yaw, pitch=25, size=(300, 300), bg="dark")
                img.save(sd / f"{name}_y{yaw}.png")


def make_sheets():
    # アイテム: 列 = A/B/C、各列 = 拡大アイコン + 3D 2方向
    rows_item = []
    for name in ITEM_NAMES:
        row = []
        for style in STYLES:
            icon = np.array(Image.open(SUB / style / "item" / f"{name}.png").convert("RGBA"))
            parts = [icon_big(icon, px=7)]
            for yaw in (35, 215):
                parts.append(Image.open(SUB / style / "voxel" / f"{name}_y{yaw}.png").convert("RGB").resize((168, 168), Image.LANCZOS))
            strip = Image.new("RGB", (sum(p.width for p in parts) + 16, 168), (18, 18, 26))
            x = 0
            for p_ in parts:
                strip.paste(p_, (x, 0))
                x += p_.width + 8
            row.append(strip)
        rows_item.append(row)
    columns = []
    for si, style in enumerate(STYLES):
        imgs = [rows_item[ni][si] for ni in range(len(ITEM_NAMES))]
        columns.append((STYLE_NAME[style], imgs))
    sheet_img = sheet("アイテム 9種: 左=アイコン7倍, 中右=3D 2方向", columns, note="列: A / B / C。行: cloud_jerky, moss_talisman, resonance_charm, resonance_crystal, resonance_shard, sky_key_fragment, stone_whale_core, stone_whale_plating, stone_whale_shard")
    sheet_img.save(OUT / "items.png")

    # 3D 比較
    cols3d = []
    for style in STYLES:
        imgs = []
        for name in ITEM_NAMES:
            im = Image.open(SUB / style / "voxel" / f"{name}_y35.png").resize((150, 150), Image.LANCZOS)
            imgs.append(im)
        cols3d.append((STYLE_NAME[style], imgs))
    sheet("アイテム 3D (ボクセル, yaw 35)", cols3d, note="各列の上から順に ITEM_NAMES の順").save(SUB / "items_3d_sheet.png")

    # ブロック: 列 = A/B/C、行 = 面
    bcolumns = []
    for style in STYLES:
        imgs = []
        for name in BLOCK_NAMES:
            arr = np.array(Image.open(SUB / style / "block" / f"{name}.png").convert("RGBA"))
            imgs.append(icon_big(arr, px=8))
        bcolumns.append((STYLE_NAME[style], imgs))
    sheet("ブロック 12面 (16x16 を6倍)", bcolumns,
          note="行の順: " + " / ".join(BLOCK_NAMES)).save(OUT / "blocks.png")


if __name__ == "__main__":
    build_all()
    make_previews()
    make_voxel_items()
    make_sheets()
    print("done")
