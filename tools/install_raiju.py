"""中ボス「雷獣」(raiju、12 の 5.2節) をゲームの素材として書き出す (GeckoLib の geo、テクスチャ、発光、アニメーション)。

使い方: リポジトリ直下で  PYTHONIOENCODING=utf-8 python -B tools/install_raiju.py [--option a|b|c] [--no-preview]
        既定の案は RECOMMENDED (12 の 14.4節の推奨 A 狼型)。b・c はラフのまま書き出す (差し替えはこの引数だけ)。
形の元: tools/rough/thunder_raiju.py の部品 (頭・足先・尾・鬣)。A は 14.4節の弱みを直した形をここで組む:
  - 頭を 1.2 倍にして少し前へ (ラフは頭が小さかった)
  - 脚を「太い柱」から犬の脚へ: 上は毛色で太く、下は濃い色で細く、肘と踵の出っ張り (関節の段) を足し、
    後脚は膝が前・踵が後ろへ折れる形にした
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/raiju.geo.json
  textures/entity/raiju.png、textures/entity/raiju_glowmask.png
  animations/entity/raiju.animation.json
確認用 (git には入れない): docs/review/previews/raiju/*.png

決まり (どれも検査する): 立方体の重なり 0、セルは 0.5 単位、テクスチャは 1 セル = 1 画素 (32 px / ブロック)、光る画素は 0 にしない。
骨をまたいで隠れる面も残す (ラフの書き出しは骨をまたいで面を消すが、アニメで骨が動くと穴が開くため。同じ骨の中だけで消す)。

ゲームでの向き (潮招き・潮殻の敵と同じ決まり): 形は前が +Z で作り、書き出す geo の root を yaw 180 度回して前を -Z にする。
角度は意図した向き (ラフの絵で見える向き) で ANIM に書き、ファイルには install_tide_mobs.SIGN = (-1, 1, -1) を掛けて書く
(GeckoLib が X と Z の回転の符号を反転して読むため)。位置 (position) は符号を掛けない。
意図した向き: X 軸の回転は、正で前 (+Z) が下がる (頭なら俯く、前へ伸びた骨なら下へ、下へ垂れた脚なら足先が後ろへ)。

アニメの長さ (秒) は Java (Raiju.java) が技の時間に使うので、変えるときは両方:
  idle 2.4 (loop)、walk 0.7 (loop)、perch 2.0 (loop)、rail_charge 1.0 (hold)、land 0.4、
  pounce_crouch 0.8 (hold)、pounce_leap 0.5 (hold)、pounce_land 0.4、claw 1.6、roar 1.5、
  stun_fall 0.5 (hold)、stunned 1.2 (loop)、stand_up 0.5、leap_up 0.5 (hold)、hurt 0.3、death 2.0 (hold)
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
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import thunder_common as tcm  # noqa: E402
import thunder_raiju as tr  # noqa: E402
import install_tide_mobs as itm  # noqa: E402
from thunder_common import BOLT_B, STORM, STORM_D, SLATE, H, Grid, FACES  # noqa: E402

ROOT = HERE.parent
ASSETS = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/raiju"
NAME = "raiju"
RECOMMENDED = "a"
SIGN = itm.SIGN
S = tr.S
HEAD_SCALE = 1.2


# ---------------------------------------------------------------- A 狼型 (14.4節の直し)

def legs_front(g, fu, dark, hipx=6.5):
    """前脚: 肩から肘は毛色で太く (骨 leg_f*)、肘から下は濃く細い (leg_f*_lo)。肘の後ろに出っ張り、手首に段"""
    for s in S:
        f = "l" if s > 0 else "r"
        x = s * hipx
        shoulder, elbow, wrist = (x, 27, 15), (x, 14, 14.5), (x, 5.0, 18.0)
        tr.T(g, f"leg_f{f}", [shoulder, elbow], [4.8, 3.4], fu)
        tr.E(g, f"leg_f{f}_lo", (x, 13.5, 12.6), (2.6, 2.8, 2.6), dark)          # 肘の出っ張り (後ろへ)
        tr.T(g, f"leg_f{f}_lo", [elbow, wrist], [2.8, 2.2], dark)
        tr.E(g, f"leg_f{f}_lo", (x, 6.0, 17.6), (2.4, 1.8, 2.4), SLATE)          # 手首の段
        tr.paw(g, f"leg_f{f}_lo", x, 20.5)


def legs_back(g, fu, dark, hipx=6.5):
    """後脚 (犬の脚): 大きな腿 (毛色)、膝は前、踵は後ろへ折れて、細い中足で掌へ"""
    for s in S:
        f = "l" if s > 0 else "r"
        x = s * hipx
        hip, knee, hock, ankle = (x, 20.0, -12.5), (x, 12.0, -8.5), (x, 7.0, -17.0), (x, 2.5, -14.5)
        tr.E(g, f"leg_b{f}", hip, (5.4, 6.8, 6.0), fu)
        tr.T(g, f"leg_b{f}", [hip, knee], [4.8, 3.6], fu)
        tr.T(g, f"leg_b{f}_lo", [knee, hock], [3.0, 2.4], dark)
        tr.E(g, f"leg_b{f}_lo", (x, 7.5, -18.2), (2.2, 2.4, 2.2), dark)          # 踵の出っ張り
        tr.T(g, f"leg_b{f}_lo", [hock, ankle], [2.2, 2.0], dark)
        tr.paw(g, f"leg_b{f}_lo", x, -13.0)


def build_a():
    g = Grid(tr.LO, tr.HI, seed=21)
    g.lid("root")
    fu = tr.fur(g)
    dark = STORM_D
    chest = ((0, 27, 14), (9.5, 9.5, 9))
    spine = [((0, 25, 0), (8.5, 8.5, 10)), ((0, 23, -16), (8, 8, 9)), ((0, 24, -24), (7.5, 7.5, 6.5))]
    neck = ((0, 30, 24), (6.5, 6.5, 5.5))
    tr.E(g, "chest", *chest, fu)
    for c, r in spine:
        tr.E(g, "body", c, r, fu)
    tr.T(g, "chest", [(0, 28, 18), (0, 32.5, 28)], [6.8, 6.0], fu)
    ells = [chest] + spine + [neck]

    # 頭は 1.2 倍。首の先へ少し前・上へ出す (頭蓋の中心 y 39、z 36)
    tr.head(g, cy=39, cz=36, sc=HEAD_SCALE, muzzle=8.5, ear_h=9.0, col=fu, dark=dark, jaw_drop=2.2)
    tr.spikes(g, "mane", [22, 18, 14, 10, 6, 2, -2, -6, -10, -14, -18, -22], ells, dark, BOLT_B, glow_every=2)

    legs_front(g, fu, dark)
    legs_back(g, fu, dark)

    P = [(0, 26, -30), (2, 31, -38), (-2, 24, -46), (3, 31, -54), (0, 25, -62), (-1, 31, -69)]
    R = [3.6, 3.0, 2.2, 1.7, 1.2, 1.0]
    tr.tail_seg(g, "tail", P[0:3], R[0:3], STORM)
    tr.tail_seg(g, "tail_2", P[2:5], R[2:5], STORM)
    tr.tail_seg(g, "tail_3", P[4:6], R[4:6], STORM_D)

    pv = {"root": (0, 0, 0), "body": (0, 24, 0), "chest": (0, 26, 14), "head": (0, 36, 31),
          "jaw": (0, 35, 39), "mane": (0, 24, 0),
          "tail": P[0], "tail_2": P[2], "tail_3": P[4]}
    for s in S:
        f = "l" if s > 0 else "r"
        pv[f"leg_f{f}"] = (s * 6.5, 27, 15)
        pv[f"leg_f{f}_lo"] = (s * 6.5, 14, 14.5)
        pv[f"leg_b{f}"] = (s * 6.5, 20, -12.5)
        pv[f"leg_b{f}_lo"] = (s * 6.5, 12, -8.5)
    return g, pv, tr.common_parents()


BUILDERS = {"a": build_a, "b": tr.build_b, "c": tr.build_c}
ALL_BONES = ["root", "body", "chest", "head", "jaw", "mane", "tail", "tail_2", "tail_3",
             "leg_fl", "leg_fl_lo", "leg_fr", "leg_fr_lo", "leg_bl", "leg_bl_lo", "leg_br", "leg_br_lo"]


# ---------------------------------------------------------------- 書き出し (同じ骨の中だけで隠れた面を消す)

def export_same_bone(g, ident, pivots, parents, aw=512):
    """thunder_common.export と同じ箱と uv の組み方。面を消すのは「同じ骨の箱で塞がれた面」だけ"""
    tcm.shade_all(g)
    occ_all = g.lab > 0
    recs, bones_out = [], []
    vol = 0
    for name, lid in g.names.items():
        m = g.lab == lid
        b = {"name": name, "pivot": [float(v) for v in pivots.get(name, (0, 0, 0))], "cubes": []}
        if name in parents:
            b["parent"] = parents[name]
        bones_out.append(b)
        if not m.any():
            continue
        pad = np.pad(m, 1)
        for bx in tcm.greedy(m):
            x0, y0, z0, x1, y1, z1 = bx
            s = (x1 - x0, y1 - y0, z1 - z0)
            vol += s[0] * s[1] * s[2]
            cube = {"origin": [float(g.LO[0] + x0 * H), float(g.LO[1] + y0 * H), float(g.LO[2] + z0 * H)],
                    "size": [s[0] * H, s[1] * H, s[2] * H], "uv": {}}
            seen = False
            for fname, (cof, euf, evf, nrm) in FACES.items():
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
            if seen:
                b["cubes"].append(cube)
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
    have = {b["name"] for b in bones_out}
    for n in ALL_BONES:
        if n not in have:
            # アニメが使う骨のうち、この案にない骨 (b の鬣など) は箱のない骨として足す
            par = tr.common_parents().get(n)
            nb = {"name": n, "pivot": [0.0, 24.0, 0.0], "cubes": []}
            if par:
                nb["parent"] = par
            bones_out.append(nb)
    span = (g.HI - g.LO) / 16.0
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.skyabyss.{ident}",
                        "texture_width": aw, "texture_height": ah,
                        "visible_bounds_width": float(round(max(span[0], span[2]) + 2, 1)),
                        "visible_bounds_height": float(round(span[1] + 2, 1)),
                        "visible_bounds_offset": [0, float(round(span[1] / 2, 1)), 0]},
        "bones": bones_out}]}
    return geo, tex, gtex


def check_rules(geo, tex, gtex):
    """重なり 0、0.5 単位、1 セル = 1 画素、光る画素あり"""
    over = vx.check_overlaps(geo)
    assert not over, over[:5]
    g = geo["minecraft:geometry"][0]
    for b in g["bones"]:
        for c in b["cubes"]:
            for v in c["origin"] + c["size"]:
                assert abs(v * 2 - round(v * 2)) < 1e-6, ("0.5 単位でない", b["name"], c)
            for k, f in c["uv"].items():
                sx, sy, sz = (int(round(v * 2)) for v in c["size"])
                want = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}[k]
                assert tuple(f["uv_size"]) == want, ("1 セル = 1 画素でない", b["name"], k, f, want)
    glow = int((gtex[..., 3] > 0).sum())
    assert glow > 0, "光る画素がない (GeckoLib の発光の層が落ちる)"
    return glow


def to_game(geo):
    """root を yaw 180 度回す (ファイルの値は SIGN を掛けた値。y は +1 なので 180 のまま)"""
    geo = copy.deepcopy(geo)
    for b in geo["minecraft:geometry"][0]["bones"]:
        if b["name"] == "root":
            b["rotation"] = [0.0, 180.0 * SIGN[1], 0.0]
    return geo


# ---------------------------------------------------------------- アニメーション (意図した向きで書く)

def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


# 決めた姿勢 (意図した角度)
PERCH = {"body": (0, 0, 0), "leg_fl": (18, 0, 0), "leg_fr": (18, 0, 0), "leg_fl_lo": (-14, 0, 0), "leg_fr_lo": (-14, 0, 0),
         "leg_bl": (-26, 0, 0), "leg_br": (-26, 0, 0), "leg_bl_lo": (18, 0, 0), "leg_br_lo": (18, 0, 0),
         "tail": (35, 0, 0), "tail_2": (15, 0, 0), "head": (10, 0, 0)}
CROUCH = {"body": (6, 0, 0), "chest": (6, 0, 0), "head": (-6, 0, 0), "jaw": (12, 0, 0),
          "leg_fl": (30, 0, 0), "leg_fr": (30, 0, 0), "leg_fl_lo": (-30, 0, 0), "leg_fr_lo": (-30, 0, 0),
          "leg_bl": (-20, 0, 0), "leg_br": (-20, 0, 0), "leg_bl_lo": (30, 0, 0), "leg_br_lo": (30, 0, 0),
          "tail": (45, 0, 0), "tail_2": (20, 0, 0), "tail_3": (15, 0, 0)}
LEAP = tr.pounce_pose()
SIDE = {"body": (0, 0, 78), "head": (-10, 0, 0), "jaw": (14, 0, 0),
        "leg_fl": (-20, 0, -10), "leg_fr": (10, 0, 10), "leg_bl": (20, 0, -10), "leg_br": (-10, 0, 10),
        "tail": (10, 20, 0), "tail_2": (0, 15, 0)}


def held(pose, t0, t1, rest=None):
    """{骨: {t0: 前, t1: 姿勢}} を作る (rest は前の姿勢。無ければ 0)"""
    out = {}
    for bone, ang in pose.items():
        a0 = (rest or {}).get(bone, (0, 0, 0))
        out[bone] = {"rotation": {t0: a0, t1: ang}}
    return out


def merge(*parts):
    out = {}
    for p in parts:
        for bone, chans in p.items():
            out.setdefault(bone, {}).update(chans)
    return out


ANIM = {
    "idle": {"loop": True, "len": 2.4, "bones": {
        # 息をする。尾の先が稲妻のように小刻みに震え、頭がゆっくり見回す
        "body": {"position": ["0", sn(0.4, 150), "0"]},
        "chest": {"rotation": [sn(1.5, 150), "0", "0"]},
        "head": {"rotation": [sn(3, 150, 40), sn(8, 75), "0"]},
        "tail": {"rotation": [f"-10 + {sn(4, 150)}", sn(8, 150, 30), "0"]},
        "tail_2": {"rotation": [sn(6, 300), sn(10, 150, 60), "0"]},
        "tail_3": {"rotation": [sn(8, 600), sn(12, 300, 90), "0"]},
    }},
    "walk": {"loop": True, "len": 0.7, "bones": {
        # 走り (対角の脚が同じ位相)
        "leg_fl": {"rotation": [sn(28, 514), "0", "0"]},
        "leg_br": {"rotation": [sn(24, 514), "0", "0"]},
        "leg_fr": {"rotation": [sn(28, 514, 180), "0", "0"]},
        "leg_bl": {"rotation": [sn(24, 514, 180), "0", "0"]},
        "leg_fl_lo": {"rotation": [f"-10 + {sn(18, 514, 90)}", "0", "0"]},
        "leg_fr_lo": {"rotation": [f"-10 + {sn(18, 514, 270)}", "0", "0"]},
        "leg_bl_lo": {"rotation": [f"10 + {sn(16, 514, 90)}", "0", "0"]},
        "leg_br_lo": {"rotation": [f"10 + {sn(16, 514, 270)}", "0", "0"]},
        "body": {"position": ["0", "0.8 * math.abs(math.sin(514 * query.anim_time))", "0"],
                 "rotation": [sn(3, 1028), "0", "0"]},
        "head": {"rotation": [sn(-4, 1028), "0", "0"]},
        "tail": {"rotation": ["5", sn(10, 514), "0"]},
        "tail_2": {"rotation": ["0", sn(10, 514, 60), "0"]},
    }},
    "perch": {"loop": True, "len": 2.0, "bones": merge(
        # 柱の先にうずくまる: 脚を寄せ、尾を立て、頭で見下ろす
        {b: {"rotation": list(map(str, a))} for b, a in PERCH.items() if b not in ("tail", "head")},
        {"body": {"position": ["0", f"-3 + {sn(0.4, 180)}", "0"]},
         "tail": {"rotation": [f"35 + {sn(6, 180)}", sn(10, 180, 45), "0"]},
         "tail_3": {"rotation": [sn(10, 720), "0", "0"]},
         "head": {"rotation": [f"10 + {sn(4, 180)}", sn(20, 90), "0"]}})},
    "rail_charge": {"loop": "hold", "len": 1.0, "bones": merge(
        # 雷渡りの溜め: 身を低くし、尾を高く、口を開く
        held(CROUCH, 0, 0.5, PERCH),
        {"body": {"position": {0: (0, -3, 0), 0.5: (0, -5, 0), 1.0: (0, -5.5, 0)}}})},
    "land": {"loop": False, "len": 0.4, "bones": merge(
        held(PERCH, 0, 0.4, CROUCH),
        {"body": {"position": {0: (0, -5, 0), 0.4: (0, -3, 0)}}})},
    "pounce_crouch": {"loop": "hold", "len": 0.8, "bones": merge(
        held(CROUCH, 0, 0.6),
        {"body": {"position": {0: (0, 0, 0), 0.6: (0, -4, 0)}}})},
    "pounce_leap": {"loop": "hold", "len": 0.5, "bones": merge(
        held(LEAP, 0, 0.15, CROUCH),
        {"body": {"position": {0: (0, -4, 0), 0.15: (0, 0, 0)}}})},
    "pounce_land": {"loop": False, "len": 0.4, "bones": {b: {"rotation": {0: a, 0.4: (0, 0, 0)}} for b, a in LEAP.items()}},
    "claw": {"loop": False, "len": 1.6, "bones": {
        # 爪の連打: 0.5・1.0・1.5 秒に、右・左・右の前脚で打ち下ろす
        "leg_fr": {"rotation": {0: (0, 0, 0), 0.35: (-80, 0, 8), 0.5: (12, 0, 0), 0.65: (0, 0, 0),
                                1.25: (0, 0, 0), 1.35: (-80, 0, 8), 1.5: (12, 0, 0), 1.6: (0, 0, 0)}},
        "leg_fl": {"rotation": {0: (0, 0, 0), 0.6: (0, 0, 0), 0.85: (-80, 0, -8), 1.0: (12, 0, 0), 1.15: (0, 0, 0)}},
        "leg_fr_lo": {"rotation": {0: (0, 0, 0), 0.35: (-30, 0, 0), 0.5: (0, 0, 0), 1.35: (-30, 0, 0), 1.5: (0, 0, 0)}},
        "leg_fl_lo": {"rotation": {0: (0, 0, 0), 0.85: (-30, 0, 0), 1.0: (0, 0, 0)}},
        "body": {"rotation": {0: (0, 0, 0), 0.35: (-10, 0, 0), 0.5: (6, 0, 0), 0.85: (-10, 0, 0), 1.0: (6, 0, 0),
                              1.35: (-10, 0, 0), 1.5: (6, 0, 0), 1.6: (0, 0, 0)}},
        "jaw": {"rotation": {0: (0, 0, 0), 0.4: (20, 0, 0), 1.5: (20, 0, 0), 1.6: (0, 0, 0)}},
    }},
    "roar": {"loop": False, "len": 1.5, "bones": {
        # 雷鳴の咆哮: 前を起こし、頭を天井へ、口を大きく開く
        "body": {"rotation": {0: (0, 0, 0), 0.3: (-14, 0, 0), 1.2: (-14, 0, 0), 1.5: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.3: (-32, 0, 0), 1.2: (-32, 0, 0), 1.5: (0, 0, 0)}},
        "jaw": {"rotation": {0: (0, 0, 0), 0.3: (34, 0, 0), 1.2: (34, 0, 0), 1.5: (0, 0, 0)}},
        "leg_fl": {"rotation": {0: (0, 0, 0), 0.3: (14, 0, 0), 1.2: (14, 0, 0), 1.5: (0, 0, 0)}},
        "leg_fr": {"rotation": {0: (0, 0, 0), 0.3: (14, 0, 0), 1.2: (14, 0, 0), 1.5: (0, 0, 0)}},
        "tail": {"rotation": {0: (0, 0, 0), 0.3: (50, 0, 0), 1.2: (50, 0, 0), 1.5: (0, 0, 0)}},
        "tail_2": {"rotation": {0: (0, 0, 0), 0.3: (25, 0, 0), 1.2: (25, 0, 0), 1.5: (0, 0, 0)}},
    }},
    "stun_fall": {"loop": "hold", "len": 0.5, "bones": merge(
        # 痺れて落ちる: 横倒しに
        held(SIDE, 0, 0.35),
        {"body": {"position": {0: (0, 0, 0), 0.35: (0, -12, 0), 0.5: (0, -13, 0)}}})},
    "stunned": {"loop": True, "len": 1.2, "bones": merge(
        {b: {"rotation": list(map(str, a))} for b, a in SIDE.items() if b not in ("leg_fl", "leg_br", "tail_2")},
        {"body": {"position": ["0", "-13", "0"]},
         "leg_fl": {"rotation": [f"-20 + {sn(10, 900)}", "0", "-10"]},
         "leg_br": {"rotation": [f"-10 + {sn(8, 900, 90)}", "0", "10"]},
         "tail_2": {"rotation": [sn(6, 600), f"15 + {sn(8, 900)}", "0"]},
         "tail_3": {"rotation": [sn(12, 1200), "0", "0"]}})},
    "stand_up": {"loop": False, "len": 0.5, "bones": merge(
        # 痺れが切れて起き上がる
        {b: {"rotation": {0: a, 0.5: (0, 0, 0)}} for b, a in SIDE.items()},
        {"body": {"position": {0: (0, -13, 0), 0.5: (0, 0, 0)}}})},
    "leap_up": {"loop": "hold", "len": 0.5, "bones": merge(
        # 柱へ戻る (跳び上がる): 前を大きく起こし、後脚で蹴る
        held({"body": (-35, 0, 0), "leg_fl": (-50, 0, 0), "leg_fr": (-50, 0, 0), "leg_bl": (50, 0, 0), "leg_br": (50, 0, 0),
              "tail": (40, 0, 0)}, 0, 0.2))},
    "hurt": {"loop": False, "len": 0.3, "bones": {
        "body": {"rotation": {0: (0, 0, 0), 0.08: (-5, 0, 4), 0.3: (0, 0, 0)}},
        "head": {"rotation": {0: (0, 0, 0), 0.08: (-12, 8, 0), 0.3: (0, 0, 0)}},
    }},
    "death": {"loop": "hold", "len": 2.0, "bones": merge(
        held(SIDE, 0, 0.8),
        {"body": {"position": {0: (0, 0, 0), 0.8: (0, -13, 0), 2.0: (0, -13, 0)}},
         "head": {"rotation": {0: (0, 0, 0), 0.8: (-10, 0, 0), 2.0: (16, 0, 0)}},
         "jaw": {"rotation": {0: (0, 0, 0), 2.0: (24, 0, 0)}}})},
}


# ---------------------------------------------------------------- 確認の絵

def view(geo, tex, gtex, pose, yaw, pitch):
    return tcm.draw(tcm.faces_of(geo, tex, gtex, pose=pose), yaw=yaw, pitch=pitch, size=420).transpose(tr.FLIP)


def previews(geo, tex, gtex, opt, stats):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    rows = [
        ("3/4 (待機)", None, tr.VIEW34),
        ("横 (待機)", None, tr.VIEW_SIDE),
        ("正面", None, tr.VIEW_FRONT),
        ("柱の上 (perch)", itm.pose_at(ANIM, "perch", 0.0), tr.VIEW34),
        ("雷渡りの溜め", itm.pose_at(ANIM, "rail_charge", 1.0), tr.VIEW34),
        ("跳躍 (pounce_leap)", itm.pose_at(ANIM, "pounce_leap", 0.5), tr.VIEW_POUNCE),
        ("爪 (claw 0.35 秒)", itm.pose_at(ANIM, "claw", 0.35), tr.VIEW34),
        ("咆哮", itm.pose_at(ANIM, "roar", 0.6), tr.VIEW_SIDE),
        ("痺れて落ちた", itm.pose_at(ANIM, "stunned", 0.0), tr.VIEW34),
    ]
    imgs = []
    for title, pose, (yaw, pitch) in rows:
        im = view(geo, tex, gtex, pose, yaw, pitch)
        im = tcm.label(im, title)
        imgs.append(im)
        im.save(PREVIEW / f"{len(imgs):02d}.png")
    night = tcm.night(tcm.draw(tcm.faces_of(geo, tex, gtex) + tcm.player_ref(x=tr.PLAYER_AT[0], z=tr.PLAYER_AT[1]),
                               yaw=tr.VIEW34[0], pitch=tr.VIEW34[1], size=420).transpose(tr.FLIP))
    imgs.append(tcm.label(night, "夜 (人は 2 ブロック)"))
    cols = [("", imgs[i:i + 5]) for i in range(0, len(imgs), 5)]
    vx.sheet(f"雷獣 {tr.OPTION_NAME[opt]} (取り込み後)  箱 {stats['cubes']}、光る画素 {stats['glow']}", cols,
             note="ラフの向きで描いた (ゲームの描画は root を 180 度回す)。角度は意図した向き。").save(PREVIEW / "raiju.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", choices=tr.OPTIONS, default=RECOMMENDED)
    ap.add_argument("--no-preview", action="store_true")
    args = ap.parse_args()

    g, pv, par = BUILDERS[args.option]()
    geo, tex, gtex = export_same_bone(g, NAME, pv, par)
    glow = check_rules(geo, tex, gtex)
    cubes = sum(len(b["cubes"]) for b in geo["minecraft:geometry"][0]["bones"])
    stats = {"cubes": cubes, "glow": glow, "atlas": list(tex.shape[1::-1])}
    game = to_game(geo)
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "geo/entity" / f"{NAME}.geo.json").write_text(json.dumps(game, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}.png", optimize=True)
    Image.fromarray(gtex, "RGBA").save(ASSETS / "textures/entity" / f"{NAME}_glowmask.png", optimize=True)
    itm.write_anim(NAME, ANIM)
    print(f"raiju ({args.option}): 箱 {cubes}、光る画素 {glow}、テクスチャ {stats['atlas']}、重なり 0")
    if not args.no_preview:
        previews(geo, tex, gtex, args.option, stats)
        print(f"preview: {PREVIEW / 'raiju.png'}")


if __name__ == "__main__":
    main()
