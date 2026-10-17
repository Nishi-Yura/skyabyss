"""雷翼の背の敵 3 種 (羽守・雷蛾・雷乞い) と、その武器 (鉤槍・雷の杖) を、ゲームの素材として書き出す。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/install_storm_mobs.py [--warden a|b|c] [--moth a|b|c] [--supplicant a|b|c]
既定は 12 の 14 章の推奨 (羽守 B 銅の兜と鎧羽、雷蛾 B 透けた翅の雷光蛾、雷乞い C 鳥籠の頭)。選び直すときは引数で替える。
出力:
  src/main/resources/assets/skyabyss/geo/entity/{feather_warden,static_moth,thunder_supplicant}.geo.json
  src/main/resources/assets/skyabyss/textures/entity/<名前>{,_glowmask}.png
  src/main/resources/assets/skyabyss/textures/entity/static_moth_charge.png  (帯電で足す光。RenderType.eyes で帯電の割合だけ足す)
  src/main/resources/assets/skyabyss/animations/entity/<名前>.animation.json
  src/main/resources/assets/skyabyss/{geo,textures,animations}/gear/{hooked_glaive,supplicant_staff}.*  (受け口 socket_main に付く別モデル)
  docs/review/previews/storm_mobs/*.png  (確認用の絵。git には入れない)

形の元は tools/rough/thunder_mobs.py (その場で作る。docs/review のラフのファイルは読まない)。次を変えた:
- 面の隠れの判定を「同じ骨の中だけ」にした (ラフの export は骨をまたいで消すので、腕・外套・翅が動くと穴が開く)。
- 雷乞い C: 鳥籠と首のつなぎ目のすき間を詰めた (首を太くして籠の底の輪まで通し、襟の輪を体の肩へ下ろした。14.3節の弱み)。
- 雷蛾: 帯電の光を「平常の光に足す分」だけの絵にした (static_moth_charge.png)。
どれも 0.5 単位の格子 (1 セル = テクスチャ 1 画素 = 32 px / ブロック)、立方体の重なり 0 (check_overlaps)、光る画素 0 は不可。

座標: ラフと同じ (前が +z、y が上、1 単位 = 1/16 ブロック)。書き出す geo は root 骨を yaw 180 度回す (tide_mobs と同じ)。
角度: ANIM と骨の初めの角度は「意図した向き」(ラフの絵で見える向き) で書き、ファイルには (-x, +y, -z) で書く
(GeckoLib は X と Y の回転の符号を反転し、モデルを X で鏡に写す。tools/README と install_tide_mobs.py と同じ)。
位置 (position) はそのまま書く。
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "rough"))
import thunder_common as tc  # noqa: E402
import thunder_mobs as tmob  # noqa: E402
import voxel as vx  # noqa: E402

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/storm_mobs"
SIGN = (-1, 1, -1)
H = tc.H


# ---------------------------------------------------------------- 書き出し (面の隠れは同じ骨の中だけで数える)

def export_same_bone(g, ident, pivots=None, parents=None, aw=512, prune_hidden=True):
    """thunder_common.export と同じだが、面が隠れるかを「同じ骨のセル」だけで決める。
    (動く骨の付け根の面を消さない。箱の数は少し増える)"""
    tc.shade_all(g)
    occ_all = g.lab > 0
    pivots = pivots or {}
    parents = parents or {}
    recs, bones_out = [], []
    vol = 0
    hidden = 0
    for name, lid in g.names.items():
        m = g.lab == lid
        if not m.any():
            b = {"name": name, "pivot": list(map(float, pivots.get(name, (0, 0, 0)))), "cubes": []}
            if name in parents:
                b["parent"] = parents[name]
            bones_out.append(b)
            continue
        pad = np.pad(m, 1)
        cubes = []
        for bx in tc.greedy(m):
            x0, y0, z0, x1, y1, z1 = bx
            s = (x1 - x0, y1 - y0, z1 - z0)
            vol += s[0] * s[1] * s[2]
            cube = {"origin": [float(g.LO[0] + x0 * H), float(g.LO[1] + y0 * H), float(g.LO[2] + z0 * H)],
                    "size": [s[0] * H, s[1] * H, s[2] * H], "uv": {}}
            seen = False
            for fname, (cof, euf, evf, nrm) in tc.FACES.items():
                co = np.array(cof(bx), float)
                eu = np.array(euf(s), float)
                ev = np.array(evf(s), float)
                W = int(round(np.abs(eu).sum()))
                Hh = int(round(np.abs(ev).sum()))
                rr, cc = np.meshgrid(np.arange(Hh), np.arange(W), indexing="ij")
                tt = (rr + 0.5) / Hh if fname == "down" else 1 - (rr + 0.5) / Hh
                pos = co[None, None, :] + ((cc + 0.5) / W)[..., None] * eu + tt[..., None] * ev
                cell = np.floor(pos - 0.5 * np.array(nrm)).astype(int)
                xi, yi, zi = cell[..., 0], cell[..., 1], cell[..., 2]
                nb = pad[xi + 1 + nrm[0], yi + 1 + nrm[1], zi + 1 + nrm[2]]
                if nb.all():
                    continue
                seen = True
                tex = np.zeros((Hh, W, 4), np.uint8)
                tex[..., :3] = np.clip(g.col[xi, yi, zi], 0, 255).astype(np.uint8)
                tex[..., 3] = 255
                recs.append({"W": W, "H": Hh, "tex": tex, "gl": g.glow[xi, yi, zi], "cube": cube, "face": fname})
            if seen or not prune_hidden:
                cubes.append(cube)
            else:
                hidden += 1
        b = {"name": name, "pivot": [float(v) for v in pivots.get(name, (0, 0, 0))], "cubes": cubes}
        if name in parents:
            b["parent"] = parents[name]
        bones_out.append(b)
    assert vol == int(occ_all.sum()), "箱の体積の合計がセル数と合わない (重なりか抜けがある)"
    recs.sort(key=lambda r: (-r["H"], -r["W"]))
    x = y = shelf = 0
    for r in recs:
        if x + r["W"] > aw:
            x, y, shelf = 0, y + shelf, 0
        r["u"], r["v"] = x, y
        x += r["W"]
        shelf = max(shelf, r["H"])
    ah = max(16, int(math.ceil((y + shelf) / 16.0) * 16))
    tex = np.zeros((ah, aw, 4), np.uint8)
    gtex = np.zeros((ah, aw, 4), np.uint8)
    for r in recs:
        u, v, W, Hh = r["u"], r["v"], r["W"], r["H"]
        tex[v:v + Hh, u:u + W] = r["tex"]
        gtex[v:v + Hh, u:u + W, :3] = r["tex"][..., :3] * r["gl"][..., None]
        gtex[v:v + Hh, u:u + W, 3] = np.where(r["gl"], 255, 0)
        r["cube"]["uv"][r["face"]] = {"uv": [u, v], "uv_size": [W, Hh]}
    span = (g.HI - g.LO) / 16.0
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.skyabyss.{ident}",
                        "texture_width": aw, "texture_height": ah,
                        "visible_bounds_width": float(round(max(span[0], span[2]) + 2, 1)),
                        "visible_bounds_height": float(round(span[1] + 2, 1)),
                        "visible_bounds_offset": [0, float(round(span[1] / 2, 1)), 0]},
        "bones": bones_out}]}
    stats = {"cubes": sum(len(b["cubes"]) for b in bones_out), "hidden_dropped": hidden, "cells": int(occ_all.sum()),
             "atlas": [aw, ah], "glow_px": int((gtex[..., 3] > 0).sum()), "bones": len(bones_out),
             "size_blocks": [round(float(v), 2) for v in span]}
    return geo, tex, gtex, stats


tc.export = export_same_bone   # thunder_mobs は tc.export を呼ぶ


# ---------------------------------------------------------------- 雷乞い C の首のすき間を詰める

_orig_head_c = tmob._sup_head_c


def _sup_head_c_fixed(g):
    _orig_head_c(g)
    hid = g.lid("head")
    # 首: 体の上 (y 18) から籠の底の輪 (y 22.2) まで、太さ 1.1 の管で通す (籠の中は空けたまま)
    neck = tc.tube(g, [(0, 18.0, 1.4), (0, 22.6, 2.0)], [1.25, 1.05])
    g.put("head", neck & (g.lab == 0), tc.ASH_W_D)
    # 籠の底の板 (輪の内側を塞ぐ。下から見て頭が浮かない)
    g.put("head", tc.plate(g, (0, 22.0, 2.0), (0, 1, 0), 2.4, 0.5) & (g.lab == 0), tc.COPPER_D)
    # 襟の輪を肩まで広げる (首の付け根の影を消す)
    g.put("head", tc.ring(g, (0, 19.2, 1.4), (0, 1, 0), 1.9, 1.2, 0.9) & (g.lab == 0), tc.COPPER_D)
    assert (g.lab == hid).any()


tmob._sup_head_c = _sup_head_c_fixed


# ---------------------------------------------------------------- 共通

def game_geo(geo, ident):
    """ラフの geo (意図した角度) から、ファイルに書く geo (root 骨、符号を直した角度) を作る"""
    g = copy.deepcopy(geo)
    gb = g["minecraft:geometry"][0]
    gb["description"]["identifier"] = "geometry." + ident
    for b in gb["bones"]:
        if "rotation" in b:
            b["rotation"] = [float(b["rotation"][i] * SIGN[i]) for i in range(3)]
        if "parent" not in b:
            b["parent"] = "root"
    gb["bones"].insert(0, {"name": "root", "pivot": [0.0, 0.0, 0.0], "rotation": [0.0, 180.0, 0.0]})
    return g


def check(geo, gl, name):
    over = vx.check_overlaps(geo)
    assert not over, (name, over[:5])
    assert gl[..., 3].any(), f"光る画素がない: {name}"
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            for v in c["origin"] + c["size"]:
                assert abs(v * 2 - round(v * 2)) < 1e-6, f"0.5 単位でない: {name} {c}"


def save_png(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(arr, "RGBA").save(path)


def write_model(name, geo, tex, gl, folder="entity"):
    check(geo, gl, name)
    out = game_geo(geo, name) if folder == "entity" else gear_geo(geo, name)
    (ASSETS / "geo" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo" / folder / f"{name}.geo.json").write_text(json.dumps(out, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    save_png(tex, ASSETS / "textures" / folder / f"{name}.png")
    save_png(gl, ASSETS / "textures" / folder / f"{name}_glowmask.png")
    return out


def gear_geo(geo, name):
    g = copy.deepcopy(geo)
    g["minecraft:geometry"][0]["description"]["identifier"] = "geometry." + name
    return g


# ---------------------------------------------------------------- アニメーション
# 値は意図した向き (度)。{"loop": True|False|"hold", "len": 秒, "bones": {骨: {"rotation"|"position"|"scale": 値}}}
# 値は 3 つの molang の式 (文字列) か、キーフレーム {時刻: (x, y, z)} (直線で補間)。

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


# 羽守 (12 の 5.1節): 凪は歩いて番をする。鉤槍の薙ぎ (前方)、嵐で 1.0 秒かがんで外套を広げ、跳んで一直線に突っ込む。
# 着地のあと 1.5 秒動けない
ANIM_WARDEN = {
    "idle": {"loop": True, "len": 4.0, "bones": {
        "body": {"rotation": [sn(1.2, 90), "0", "0"]},
        "head": {"rotation": [sn(2, 90, 40), sn(8, 45), "0"]},
        "cloak_r": {"rotation": ["0", "0", f"-3 + {sn(2, 90)}"]},
        "cloak_l": {"rotation": ["0", "0", f"3 + {sn(-2, 90)}"]},
        "arm_l": {"rotation": [sn(2, 90, 30), "0", "0"]},
    }},
    "walk": {"loop": True, "len": 1.1, "bones": {
        "leg_r": {"rotation": [sn(24, 327), "0", "0"]},
        "leg_l": {"rotation": [sn(-24, 327), "0", "0"]},
        "arm_l": {"rotation": [sn(-18, 327), "0", "0"]},
        "arm_r": {"rotation": [sn(6, 327, 180), "0", "0"]},
        "body": {"rotation": ["3", sn(3, 327), "0"], "position": ["0", f"0.4 * math.abs(math.sin(327 * query.anim_time))", "0"]},
        "cloak_r": {"rotation": [sn(-6, 327), "0", "-4"]},
        "cloak_l": {"rotation": [sn(6, 327), "0", "4"]},
    }},
    # 薙ぎ (0.8 秒): 0.35 秒で鉤槍を右後ろへ引き、0.5 秒で前を横切って左へ払う (当たりは 0.5 秒)
    "attack": {"loop": False, "len": 0.8, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.35: (4, -30, 0), 0.5: (8, 32, 0), 0.62: (6, 36, 0), 0.8: (0, 0, 0)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 0.35: (-40, 0, -50), 0.5: (-80, 0, 20), 0.62: (-70, 0, 30), 0.8: (0, 0, 0)}},
        "forearm_r": {"rotation": {0: (0, 0, 0), 0.35: (-20, 0, 0), 0.5: (-10, 0, 0), 0.8: (0, 0, 0)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 0.35: (55, 0, 0), 0.5: (70, 0, 0), 0.8: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.35: (-20, 0, 20), 0.5: (10, 0, -10), 0.8: (0, 0, 0)}},
        "cloak_r": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, -25), 0.8: (0, 0, 0)}},
        "cloak_l": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, 15), 0.8: (0, 0, 0)}},
    }},
    # かがみ (1.0 秒、最後で止まる): 膝を曲げ、前へ倒れ、外套を左右へ広げる (突撃の予告)
    "crouch": {"loop": "hold", "len": 1.0, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.6: (24, 0, 0), 1.0: (28, 0, 0)},
                 "position": {0: (0, 0, 0), 0.6: (0, -3, 0), 1.0: (0, -3.5, 0)}},
        "leg_r": {"rotation": {0: (0, 0, 0), 0.6: (-40, 0, 6), 1.0: (-46, 0, 6)},
                  "position": {0: (0, 0, 0), 0.6: (0, -3, 0), 1.0: (0, -3.5, 0)}},
        "leg_l": {"rotation": {0: (0, 0, 0), 0.6: (-34, 0, -6), 1.0: (-40, 0, -6)},
                  "position": {0: (0, 0, 0), 0.6: (0, -3, 0), 1.0: (0, -3.5, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 1.0: (-22, 0, 0)}},
        "cloak_r": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, -50), 1.0: (0, 0, -82)}},
        "cloak_l": {"rotation": {0: (0, 0, 0), 0.5: (0, 0, 50), 1.0: (0, 0, 82)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 1.0: (-60, 0, -10)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 1.0: (105, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 1.0: (20, 0, -30)}},
    }},
    # 突撃 (宙を一直線に): 腕を前へ、鉤槍を前へ突き出し、外套は翼のように広げたまま羽ばたく
    "dive": {"loop": True, "len": 0.4, "bones": {
        "body": {"rotation": ["40", "0", "0"]},
        "head": {"rotation": ["-34", "0", "0"]},
        "arm_r": {"rotation": ["-95", "0", "0"]},
        "socket_main": {"rotation": ["150", "0", "0"]},
        "arm_l": {"rotation": ["-90", "0", "0"]},
        "cloak_r": {"rotation": ["0", sn(6, 900), f"-80 + {sn(6, 900)}"]},
        "cloak_l": {"rotation": ["0", sn(-6, 900), f"80 + {sn(-6, 900)}"]},
        "leg_r": {"rotation": ["30", "0", "0"]},
        "leg_l": {"rotation": ["24", "0", "0"]},
    }},
    # 着地のあと動けない 1.5 秒: 膝をつき、鉤槍を地面に突いて、外套を垂らす。肩で息をする
    "stunned": {"loop": True, "len": 1.5, "bones": {
        "body": {"rotation": [f"30 + {sn(3, 240)}", "0", "4"], "position": ["0", "-5", "0"]},
        "leg_r": {"rotation": ["-80", "0", "0"], "position": ["0", "-5", "0"]},
        "leg_l": {"rotation": ["-20", "0", "0"], "position": ["0", "-5", "0"]},
        "head": {"rotation": [f"20 + {sn(4, 240, 60)}", "0", "-8"]},
        "cloak_r": {"rotation": ["10", "0", "6"]},
        "cloak_l": {"rotation": ["10", "0", "-6"]},
        "arm_r": {"rotation": ["-40", "0", "0"]},
        "socket_main": {"rotation": ["25", "0", "0"]},
        "arm_l": {"rotation": ["10", "0", "-12"]},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.07: (-8, 0, 5), 0.3: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.07: (-12, 6, 0), 0.3: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.2, "bones": {
        # 膝から崩れ、外套が広がったまま後ろへ倒れる (中は空洞)
        "body": {"rotation": {0: (0, 0, 0), 0.3: (16, 0, 0), 0.9: (-80, 0, 6), 1.2: (-86, 0, 6)},
                 "position": {0: (0, 0, 0), 0.3: (0, -3, 0), 0.9: (0, -11, -2), 1.2: (0, -12, -2)}},
        "leg_r": {"rotation": {0: (0, 0, 0), 0.3: (-40, 0, 0), 0.9: (70, 0, 0), 1.2: (80, 0, 0)},
                  "position": {0: (0, 0, 0), 0.3: (0, -3, 0), 0.9: (0, -11, -2), 1.2: (0, -12, -2)}},
        "leg_l": {"rotation": {0: (0, 0, 0), 0.3: (-30, 0, 0), 0.9: (76, 0, 0), 1.2: (84, 0, 0)},
                  "position": {0: (0, 0, 0), 0.3: (0, -3, 0), 0.9: (0, -11, -2), 1.2: (0, -12, -2)}},
        "head": {"rotation": {0: (0, 0, 0), 1.2: (-20, 25, 10)}},
        "cloak_r": {"rotation": {0: (0, 0, 0), 0.6: (0, 0, -40), 1.2: (0, 0, -70)}},
        "cloak_l": {"rotation": {0: (0, 0, 0), 0.6: (0, 0, 40), 1.2: (0, 0, 70)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 1.2: (-150, 0, 20)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 1.2: (-140, 0, -25)}},
    }},
}

# 雷蛾 (5.1節): 宙を舞う。4 秒かけて帯電し (光の層で表す)、満ちると放電
ANIM_MOTH = {
    "idle": {"loop": True, "len": 0.5, "bones": {
        "wing_l": {"rotation": ["0", "0", f"14 + {sn(34, 720)}"]},
        "wing_r": {"rotation": ["0", "0", f"-14 - {sn(34, 720)}"]},
        "body": {"position": ["0", sn(0.8, 720, 90), "0"], "rotation": [sn(4, 360), "0", "0"]},
        "antennae": {"rotation": [sn(6, 360, 90), "0", "0"]},
    }},
    "walk": {"loop": True, "len": 0.35, "bones": {
        "wing_l": {"rotation": ["0", sn(6, 1028), f"10 + {sn(40, 1028)}"]},
        "wing_r": {"rotation": ["0", sn(-6, 1028), f"-10 - {sn(40, 1028)}"]},
        "body": {"position": ["0", sn(1.0, 1028, 90), "0"], "rotation": ["-8", "0", "0"]},
        "antennae": {"rotation": ["-10", "0", "0"]},
    }},
    # 放電 (0.5 秒): 翅を一度平らに張りつめ、体がはねる
    "attack": {"loop": False, "len": 0.5, "bones": {
        "wing_l": {"rotation": {0: (0, 0, 14), 0.1: (0, 0, -8), 0.2: (0, 0, 40), 0.5: (0, 0, 14)}},
        "wing_r": {"rotation": {0: (0, 0, -14), 0.1: (0, 0, 8), 0.2: (0, 0, -40), 0.5: (0, 0, -14)}},
        "body": {"rotation": {0: (0, 0, 0), 0.1: (-18, 0, 0), 0.3: (8, 0, 0), 0.5: (0, 0, 0)},
                 "position": {0: (0, 0, 0), 0.1: (0, 1.5, 0), 0.5: (0, 0, 0)},
                 "scale": {0: (1, 1, 1), 0.1: (1.12, 1.12, 1.12), 0.3: (0.96, 0.96, 0.96), 0.5: (1, 1, 1)}},
        "antennae": {"rotation": {0: (0, 0, 0), 0.1: (-30, 0, 0), 0.5: (0, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.25, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.06: (10, 0, 16), 0.25: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.0, "bones": {
        # 翅をたたみ、きりきり回って落ちる
        "wing_l": {"rotation": {0: (0, 0, 14), 0.4: (0, 0, 70), 1.0: (0, 0, 80)}},
        "wing_r": {"rotation": {0: (0, 0, -14), 0.4: (0, 0, -70), 1.0: (0, 0, -80)}},
        "body": {"rotation": {0: (0, 0, 0), 0.5: (20, 200, 30), 1.0: (30, 360, 90)},
                 "position": {0: (0, 0, 0), 1.0: (0, -5, 0)}},
    }},
}

# 雷乞い (5.1節): 間合いを保ち、杖を頭上へ掲げて (予告)、人の足もとに輪を呼ぶ
ANIM_SUPPLICANT = {
    "idle": {"loop": True, "len": 3.0, "bones": {
        "body": {"rotation": [f"6 + {sn(2, 120)}", "0", sn(1.5, 120, 60)]},
        "head": {"rotation": [sn(3, 120, 40), sn(10, 60), sn(4, 60, 90)]},
        "robe": {"rotation": [sn(-1.5, 120), "0", sn(-1.5, 120, 60)]},
        "arm_l": {"rotation": [sn(4, 120), "0", "0"]},
    }},
    "walk": {"loop": True, "len": 1.4, "bones": {
        "body": {"rotation": ["10", sn(3, 257), sn(3, 257)], "position": ["0", f"0.3 * math.abs(math.sin(257 * query.anim_time))", "0"]},
        "robe": {"rotation": [sn(4, 257), "0", sn(-4, 257)]},
        "arm_l": {"rotation": [sn(-12, 257), "0", "0"]},
        "head": {"rotation": ["-4", sn(-4, 257), "0"]},
    }},
    # 招雷 (1.3 秒): 0.35 秒で杖を頭上へ掲げ、輪が出ている間掲げたまま震え、下ろす
    "cast": {"loop": False, "len": 1.3, "bones": {
        "arm_r": {"rotation": {0: (0, 0, 0), 0.35: (-135, 0, 0), 1.0: (-138, 0, 0), 1.3: (0, 0, 0)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 0.35: (85, 0, 0), 1.0: (85, 0, 0), 1.3: (0, 0, 0)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 0.35: (-70, 0, -10), 1.0: (-75, 0, -12), 1.3: (0, 0, 0)}},
        "body": {"rotation": {0: (6, 0, 0), 0.35: (-12, 0, 0), 0.6: (-10, 0, 3), 0.8: (-12, 0, -3), 1.0: (-10, 0, 0), 1.3: (6, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.35: (-30, 0, 0), 1.0: (-34, 0, 0), 1.3: (0, 0, 0)}},
    }},
    # 杖で突く (0.6 秒、当たりは 0.3 秒)
    "attack": {"loop": False, "len": 0.6, "bones": {
        "arm_r": {"rotation": {0: (0, 0, 0), 0.2: (20, 0, 0), 0.3: (-60, 0, 0), 0.6: (0, 0, 0)}},
        "socket_main": {"rotation": {0: (0, 0, 0), 0.2: (15, 0, 0), 0.3: (70, 0, 0), 0.6: (0, 0, 0)}},
        "body": {"rotation": {0: (6, 0, 0), 0.3: (18, 0, 0), 0.6: (6, 0, 0)}},
    }},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (6, 0, 0), 0.07: (-4, 0, 6), 0.3: (6, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.07: (-10, 10, 0), 0.3: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 1.3, "bones": {
        # 長衣がしぼみ、前へ崩れ、籠の頭が転がる
        "robe": {"scale": {0: (1, 1, 1), 0.8: (1.05, 0.55, 1.05), 1.3: (1.1, 0.4, 1.1)}},
        "body": {"rotation": {0: (6, 0, 0), 0.5: (20, 0, 0), 1.0: (70, 0, 10), 1.3: (78, 0, 10)},
                 "position": {0: (0, 0, 0), 0.8: (0, -6, 0), 1.3: (0, -9, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 1.0: (30, 30, 20), 1.3: (40, 40, 60)}},
        "arm_r": {"rotation": {0: (0, 0, 0), 1.3: (-60, 0, 20)}},
        "arm_l": {"rotation": {0: (0, 0, 0), 1.3: (-50, 0, -20)}},
    }},
}


def _num(v):
    return float(round(v, 4))


def _signed(expr, sgn):
    return expr if sgn == 1 else ("0" if expr.strip() == "0" else f"-({expr})")


def anim_json(prefix, spec):
    out = {}
    for name, a in spec.items():
        bones = {}
        for bone, chans in a["bones"].items():
            bj = {}
            for ch, val in chans.items():
                sg = SIGN if ch == "rotation" else (1, 1, 1)
                if isinstance(val, dict):
                    bj[ch] = {f"{t:.4g}": {"vector": [_num(v[i] * sg[i]) for i in range(3)]} for t, v in sorted(val.items())}
                else:
                    bj[ch] = [_signed(val[i], sg[i]) for i in range(3)]
            bones[bone] = bj
        entry = {"animation_length": a["len"], "bones": bones}
        entry["loop"] = "hold_on_last_frame" if a["loop"] == "hold" else bool(a["loop"])
        out[f"animation.{prefix}.{name}"] = entry
    return {"format_version": "1.8.0", "animations": out}


def write_anim(prefix, spec, folder="entity"):
    (ASSETS / "animations" / folder).mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations" / folder / f"{prefix}.animation.json").write_text(
        json.dumps(anim_json(prefix, spec), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


def eval_channel(val, t):
    if isinstance(val, dict):
        ks = sorted(val)
        if t <= ks[0]:
            return np.array(val[ks[0]], float)
        for k0, k1 in zip(ks, ks[1:]):
            if k0 <= t <= k1:
                u = (t - k0) / (k1 - k0) if k1 > k0 else 0
                return np.array(val[k0], float) * (1 - u) + np.array(val[k1], float) * u
        return np.array(val[ks[-1]], float)
    env = {"msin": lambda d: math.sin(math.radians(d)), "mabs": abs, "t": t}
    return np.array([float(eval(e.replace("math.sin", "msin").replace("math.abs", "mabs").replace("query.anim_time", "t"), {}, env))
                     for e in val])


def pose_at(spec, name, t, rest):
    """確認の絵用: 意図した回転 {骨: (x, y, z)}。GeckoLib はアニメの値を骨の初めの角度に足すので、そのまま足し分として渡す"""
    pose = {}
    for bone, chans in spec[name]["bones"].items():
        if "rotation" in chans:
            pose[bone] = tuple(eval_channel(chans["rotation"], t))
    return pose


# ---------------------------------------------------------------- 武器 (別モデル)

def write_gear(name, built):
    geo, tex, gl, _ = built
    write_model(name, geo, tex, gl, folder="gear")
    anim = {"format_version": "1.8.0", "animations": {f"animation.{name}.idle": {"loop": True, "animation_length": 1.0, "bones": {}}}}
    (ASSETS / "animations/gear").mkdir(parents=True, exist_ok=True)
    (ASSETS / "animations/gear" / f"{name}.animation.json").write_text(json.dumps(anim, indent=1) + "\n", encoding="utf-8")
    return geo, tex, gl


# ---------------------------------------------------------------- 確認の絵

def preview(name, mob, weapon, rows, rest, spec):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    ims = []
    for label, anim, t, yaw, pitch, night, gl in rows:
        pose = pose_at(spec, anim, t, rest) if anim else None
        fs = tmob.mob_faces((mob[0], mob[1], mob[2]), pose, gl=gl, weapon=weapon)
        if night:
            fs = fs + tc.player_ref(x=22)
            im = tc.night(tc.draw(fs, yaw, pitch, size=360))
        else:
            im = tc.draw(fs, yaw, pitch, size=360)
        ims.append(tc.label(im, label, 15))
    return ims


def rest_of(geo):
    return {b["name"]: tuple(b["rotation"]) for b in geo["minecraft:geometry"][0]["bones"] if "rotation" in b}


# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--warden", choices="abc", default="b")
    ap.add_argument("--moth", choices="abc", default="b")
    ap.add_argument("--supplicant", choices="abc", default="c")
    args = ap.parse_args()

    glaive = write_gear("hooked_glaive", tmob.hooked_glaive())
    staff = write_gear("supplicant_staff", tmob.staff_geo(short=args.supplicant == "b"))

    wg, wt, wgl, ws = tmob.warden_geo(args.warden)
    write_model("feather_warden", wg, wt, wgl)
    write_anim("feather_warden", ANIM_WARDEN)

    mg, mt, mgl, mgl_c, ms = tmob.moth_geo(args.moth)
    write_model("static_moth", mg, mt, mgl)
    # 帯電で足す光: 帯電の絵のうち、平常で光っていない画素だけ (平常の光は AutoGlowingGeoLayer がいつも描く)
    extra = mgl_c.copy()
    extra[mgl[..., 3] > 0] = 0
    assert extra[..., 3].any()
    save_png(extra, ASSETS / "textures/entity/static_moth_charge.png")
    write_anim("static_moth", ANIM_MOTH)

    sg, st, sgl, ss = tmob.supplicant_geo(args.supplicant)
    write_model("thunder_supplicant", sg, st, sgl)
    write_anim("thunder_supplicant", ANIM_SUPPLICANT)

    # 15 章 (ハイポリゴン化): 書き出した素の形を、箱の切り直し・面取り・飾りで彫り直す (rough/hp_mobs_storm_ember.py)
    import hp_mobs_storm_ember as hp
    for name in ("feather_warden", "static_moth", "thunder_supplicant"):
        hp.install_mob(name)

    for name, geo, gl, s in (("feather_warden", wg, wgl, ws), ("static_moth", mg, mgl, ms), ("thunder_supplicant", sg, sgl, ss)):
        print(f"{name}: 箱 {s['cubes']}、テクスチャ {s['atlas'][0]}x{s['atlas'][1]}、光る画素 {s['glow_px']}、大きさ {s['size_blocks']}")
    print(f"moth charged extra glow px {int((extra[..., 3] > 0).sum())}")

    cols = []
    wrest = rest_of(wg)
    cols.append((f"羽守 {args.warden.upper()}", preview("feather_warden", (wg, wt, wgl), glaive, [
        ("3/4", None, 0, 35, 14, False, None),
        ("薙ぎ (当たり)", "attack", 0.5, 20, 12, False, None),
        ("かがみ (予告)", "crouch", 1.0, 35, 12, False, None),
        ("突撃", "dive", 0.0, 60, 14, False, None),
        ("着地のあと", "stunned", 0.0, 35, 12, False, None),
        ("死", "death", 1.2, 80, 30, False, None),
        ("夜", None, 0, 30, 10, True, None)], wrest, ANIM_WARDEN)))
    mrest = rest_of(mg)
    mgl_full = np.maximum(mgl, extra)
    cols.append((f"雷蛾 {args.moth.upper()}", preview("static_moth", (mg, mt, mgl), None, [
        ("3/4", "idle", 0.0, 35, 18, False, None),
        ("羽ばたき", "idle", 0.125, 35, 18, False, None),
        ("帯電", "idle", 0.0, 35, 18, False, mgl_full),
        ("放電", "attack", 0.2, 35, 18, False, mgl_full),
        ("正面", "idle", 0.0, 0, 4, False, None),
        ("夜", "idle", 0.0, 30, 10, True, None)], mrest, ANIM_MOTH)))
    srest = rest_of(sg)
    cols.append((f"雷乞い {args.supplicant.upper()}", preview("thunder_supplicant", (sg, st, sgl), staff, [
        ("3/4", None, 0, 35, 14, False, None),
        ("横 (首)", None, 0, 90, 4, False, None),
        ("下から (首)", None, 0, 60, -25, False, None),
        ("招雷 (掲げる)", "cast", 0.6, 35, 12, False, None),
        ("突き", "attack", 0.3, 60, 10, False, None),
        ("死", "death", 1.3, 60, 30, False, None),
        ("夜", None, 0, 30, 10, True, None)], srest, ANIM_SUPPLICANT)))
    vx.sheet("雷翼の背の敵 (取り込み後の確認)", cols, note="ラフの向き (+Z が前) で描いた。角度は意図した向き").save(PREVIEW / "storm_mobs.png")


if __name__ == "__main__":
    main()
