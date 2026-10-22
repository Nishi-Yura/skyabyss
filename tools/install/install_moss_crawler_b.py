"""苔這い (moss_crawler) を、ユーザーが選んだ B 案 苔芋虫 の滑らかなモデルに差し替える。

  python tools/install/install_moss_crawler_b.py

形の元: tools/rough/smooth_mob2.py の opt_b (5 節の芋虫・骨の輪・背中の苔・オレンジの一つ目・誘いの光の触角)。
ここで足す手直し (見た目は変えず、見えない所の箱を減らす): 脚の膝の丸 (bone2 の楕円体) を外す。
  672 箱 / 2171 面 -> 646 箱 / 2121 面 (27 の骨)。
出力:
  src/main/resources/assets/skyabyss/geo/entity/moss_crawler.geo.json   (identifier geometry.moss_crawler。root は yaw 180)
  src/main/resources/assets/skyabyss/textures/entity/moss_crawler{,_glowmask}.png (目と触角の光の画素がある)
  src/main/resources/assets/skyabyss/animations/entity/moss_crawler.animation.json (idle walk pounce hurt death)
  docs/review/smooth_mob_roughs2/moss_crawler/b/ (入力としてのラフ。手直し後の物に更新)
そのあと tools/lib/fix_geo_face_sides.py --apply (GeckoLib は west を x の大きい側に置く) と check_overlaps (0) を回す。

角度: ANIM は「意図した向き」(ラフの絵で見える向き。前が +z、頭が上向きに起きる = x が負) で書き、
ファイルには (-x, +y, -z) で書く (GeckoLib は X と Y の回転の符号を反転し、モデルを X で鏡に写す)。
位置 (position) と scale はそのまま書く。molang の sin / cos は度。
"""
from __future__ import annotations

import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parents[1]
ROOT = HERE.parent
sys.path.insert(0, str(HERE / "rough"))
import smooth_kit as K  # noqa: E402
import smooth_mob2 as S  # noqa: E402
import voxel  # noqa: E402

ASSETS = ROOT / "src/main/resources/assets/skyabyss"
ROUGH_B = ROOT / "docs/review/smooth_mob_roughs2/moss_crawler/b"
SIGN = (-1, 1, -1)
C, E = S.C, S.E


# ---------------------------------------------------------------- モデル
def leg(m, pre, parent, hip, knee, foot, r):
    """上腿と下腿の骨。膝の丸は外した (体の下に隠れて、ほとんど見えない)"""
    m.add_bone(pre + "_u", parent, hip, 0.5)
    m.add_bone(pre + "_l", pre + "_u", knee, 0.5)
    m.add(pre + "_u", C(hip, knee, r, r * 0.9), "bone")
    m.add(pre + "_l", C(knee, foot, r * 0.8, r * 0.55), "bone")
    fx = np.array(foot, float)
    m.add(pre + "_l", E((fx[0], fx[1] + 0.35, fx[2] + 0.6), (r * 0.9, 0.55, r * 1.5)), "bone2")


def build():
    S.leg = leg
    m = S.opt_b()
    geo, tex, glow = m.build()
    geo["minecraft:geometry"][0]["description"]["identifier"] = "geometry.moss_crawler"
    return m, geo, tex, glow


# ---------------------------------------------------------------- アニメーション
# 骨の鎖: seg1 (尾の側) -> seg2 -> ... -> seg5 -> head。前の骨ほど、後ろの骨の回転を引き継ぐ
def sn(a, f, p=0):
    return f"{a} * math.sin({f} * query.anim_time + {p})"


def ph(deg):
    return f"{deg:+g}" if deg else "0"


STEP = 48          # 歩きの波の、節ごとの位相のずれ (度)
SEGS = ("seg1", "seg2", "seg3", "seg4", "seg5")
LEGSEG = {1: 2, 2: 3, 3: 5}     # 脚の組 (l1/r1 ...) がついた節


def idle():
    bones = {}
    for i, s in enumerate(SEGS, 1):
        bones[s] = {"rotation": [sn(1.3, 90, -i * 38), sn(1.0, 45, -i * 30), "0"],
                    "position": ["0", sn(0.12, 90, -i * 38), "0"]}
    bones["head"] = {"rotation": [sn(2.0, 90, 45), sn(7, 45), sn(1.5, 45, 90)]}
    bones["jaw"] = {"rotation": [f"3 + {sn(2, 90, 180)}", "0", "0"]}
    bones["ant_l"] = {"rotation": [sn(5, 180, 30), "0", sn(-7, 90)]}
    bones["ant_r"] = {"rotation": [sn(5, 180, 200), "0", sn(7, 90, 150)]}
    bones["lure_l"] = {"scale": [f"1 + {sn(0.18, 360, 0)}"] * 3}
    bones["lure_r"] = {"scale": [f"1 + {sn(0.18, 360, 130)}"] * 3}
    bones["tail"] = {"rotation": [sn(3, 90, -230), sn(9, 90, -200), "0"]}
    # 一つ目: 瞬きと、ゆっくりした見回し (眼球の骨が回る)
    bones["eyes"] = {
        "scale": {0: (1, 1, 1), 2.7: (1, 1, 1), 2.8: (1, 0.12, 1), 2.95: (1, 0.12, 1), 3.1: (1, 1, 1), 4: (1, 1, 1)},
        "rotation": {0: (0, 0, 0), 0.8: (0, 0, 0), 1.1: (0, 18, 0), 2.2: (0, 18, 0), 2.5: (0, -14, 0), 3.4: (0, -14, 0), 3.8: (0, 0, 0), 4: (0, 0, 0)},
    }
    return {"loop": True, "len": 4.0, "bones": bones}


def walk():
    bones = {}
    for i, s in enumerate(SEGS, 1):
        p = -i * STEP
        bones[s] = {"rotation": [sn(4.0, 360, p), sn(5.0, 360, p + 90), "0"],
                    "position": ["0", sn(0.15, 360, p), "0"]}
    bones["head"] = {"rotation": [sn(3, 360, -6 * STEP), sn(4, 360, -6 * STEP + 90), "0"]}
    bones["jaw"] = {"rotation": [f"4 + {sn(3, 720, 0)}", "0", "0"]}
    bones["tail"] = {"rotation": ["0", sn(14, 360, -STEP * 0 - 30), "0"]}
    bones["ant_l"] = {"rotation": [sn(7, 720, 0), "0", sn(-4, 360, 0)]}
    bones["ant_r"] = {"rotation": [sn(7, 720, 140), "0", sn(4, 360, 0)]}
    bones["lure_l"] = {"scale": [f"1 + {sn(0.2, 720, 0)}"] * 3}
    bones["lure_r"] = {"scale": [f"1 + {sn(0.2, 720, 140)}"] * 3}
    for k, i in LEGSEG.items():
        for side, off in (("l", 0), ("r", 180)):
            p = -i * STEP + off
            # 前へ振る = 腿が x の負へ。持ち上げるときに下腿を折る
            bones[f"{side}{k}_u"] = {"rotation": [sn(-26, 360, p), "0", "0"]}
            bones[f"{side}{k}_l"] = {"rotation": [f"-14 + {sn(-12, 360, p + 90)}", "0", "0"]}
    return {"loop": True, "len": 1.0, "bones": bones}


def pounce():
    """前の 4 節を起こして構え (0.18 秒)、体を伸ばして前へ飛び込み (0.3)、噛みつきを閉じて (0.38)、戻る (0.9)。
    飛びかかりの開始 (LeapAtTargetGoal) と、噛みついた瞬間のどちらでも流す"""
    T = (0, 0.18, 0.3, 0.55, 0.9)

    def key(*vals):
        return dict(zip(T, vals))

    z = (0, 0, 0)

    def rot(a0, a1, a2=None):
        # 構え -> 飛び込み -> 伸びきり -> 戻る
        return key(z, (a0, 0, 0), (a1, 0, 0), (a2 if a2 is not None else a1 * 0.5, 0, 0), z)

    def lunge(d):
        return key(z, (0, 0, -d * 0.3), (0, 0, d), (0, 0, d * 0.7), z)

    bones = {
        "seg2": {"rotation": rot(-9, 3), "position": lunge(0.7)},
        "seg3": {"rotation": rot(-12, 4), "position": lunge(0.7)},
        "seg4": {"rotation": rot(-12, 4), "position": lunge(0.7)},
        "seg5": {"rotation": rot(-8, 2), "position": lunge(0.7)},
        "head": {"rotation": rot(8, -8, -2), "position": lunge(0.6)},
        "jaw": {"rotation": {0: z, 0.18: (36, 0, 0), 0.3: (40, 0, 0), 0.38: (0, 0, 0), 0.9: z}},
        "ant_l": {"rotation": rot(-30, 12)},
        "ant_r": {"rotation": rot(-30, 12)},
        "eyes": {"scale": key((1, 1, 1), (1.2, 1.2, 1.2), (1.1, 1.1, 1.1), (1, 1, 1), (1, 1, 1))},
        "tail": {"rotation": rot(14, -8, 0)},
        "l3_u": {"rotation": rot(-38, 26, 10)},
        "r3_u": {"rotation": rot(-38, 26, 10)},
        "l2_u": {"rotation": rot(-16, 14, 6)},
        "r2_u": {"rotation": rot(-16, 14, 6)},
        "l1_u": {"rotation": rot(22, -14, -6)},
        "r1_u": {"rotation": rot(22, -14, -6)},
    }
    return {"loop": False, "len": 0.9, "bones": bones}


def hurt():
    z = (0, 0, 0)
    bones = {
        "seg2": {"rotation": {0: z, 0.08: (3, 6, 0), 0.18: (-2, -6, 0), 0.3: (1, 2, 0), 0.4: z}},
        "seg3": {"rotation": {0: z, 0.08: (6, -7, 0), 0.18: (-3, 7, 0), 0.3: (1, -2, 0), 0.4: z}},
        "seg4": {"rotation": {0: z, 0.08: (4, 6, 0), 0.18: (-2, -5, 0), 0.3: z, 0.4: z}},
        "head": {"rotation": {0: z, 0.08: (14, 10, 0), 0.2: (6, -8, 0), 0.4: z}},
        "jaw": {"rotation": {0: z, 0.08: (22, 0, 0), 0.3: (6, 0, 0), 0.4: z}},
        "eyes": {"scale": {0: (1, 1, 1), 0.07: (1, 0.25, 1), 0.25: (1, 0.4, 1), 0.4: (1, 1, 1)}},
        "ant_l": {"rotation": {0: z, 0.08: (-26, 0, 14), 0.4: z}},
        "ant_r": {"rotation": {0: z, 0.08: (-26, 0, -14), 0.4: z}},
    }
    return {"loop": False, "len": 0.4, "bones": bones}


def death():
    """渦を巻いて丸まる (節ごとに同じ向きへ曲がり、輪になる)。脚をたたみ、目を閉じ、光が消える。終わった姿で止まる"""
    T = (0, 0.3, 0.9)
    bones = {}
    for i, s in enumerate(SEGS, 1):
        a = 26 + (i == 1) * 4
        bones[s] = {"rotation": {0: (0, 0, 0), 0.3: (-2, a * 0.35, 0), 0.9: (0, a, 0)}}
    bones["seg1"]["position"] = {0: (0, 0, 0), 0.9: (0, -1.2, 0)}
    bones["head"] = {"rotation": {0: (0, 0, 0), 0.3: (10, 12, 0), 0.9: (14, 30, 0)}}
    bones["tail"] = {"rotation": {0: (0, 0, 0), 0.9: (0, 22, 0)}}
    bones["jaw"] = {"rotation": {0: (0, 0, 0), 0.3: (24, 0, 0), 0.9: (12, 0, 0)}}
    bones["eyes"] = {"scale": {0: (1, 1, 1), 0.3: (1, 0.3, 1), 0.55: (1, 0.1, 1), 0.9: (1, 0.1, 1)}}
    bones["lure_l"] = {"scale": {0: (1, 1, 1), 0.9: (0.05, 0.05, 0.05)}}
    bones["lure_r"] = {"scale": {0: (1, 1, 1), 0.9: (0.05, 0.05, 0.05)}}
    bones["ant_l"] = {"rotation": {0: (0, 0, 0), 0.9: (30, 0, -62)}}
    bones["ant_r"] = {"rotation": {0: (0, 0, 0), 0.9: (30, 0, 62)}}
    for k in (1, 2, 3):
        bones[f"l{k}_u"] = {"rotation": {0: (0, 0, 0), 0.9: (0, 0, -36)}}
        bones[f"r{k}_u"] = {"rotation": {0: (0, 0, 0), 0.9: (0, 0, 36)}}
        bones[f"l{k}_l"] = {"rotation": {0: (0, 0, 0), 0.9: (30, 0, 0)}}
        bones[f"r{k}_l"] = {"rotation": {0: (0, 0, 0), 0.9: (30, 0, 0)}}
    return {"loop": "hold", "len": 0.9, "bones": bones}


def _num(v):
    v = round(float(v), 4)
    return 0 if v == 0 else v


def _signed(expr, sgn):
    return expr if sgn == 1 else ("0" if expr.strip() == "0" else f"-({expr})")


def anim_json(spec):
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
        out[f"animation.moss_crawler.{name}"] = entry
    return {"format_version": "1.8.0", "animations": out}


# ---------------------------------------------------------------- 書き出し
def main():
    m, geo, tex, glow = build()
    print("stats", m.stats)
    if not glow[..., 3].any():
        raise SystemExit("glowmask has no lit pixels (AutoGlowingGeoLayer would crash)")
    print("lit glow pixels", int((glow[..., 3] > 0).sum()))
    K.save(m, ROUGH_B, "moss_crawler", geo, tex, glow)
    (ASSETS / "geo/entity/moss_crawler.geo.json").write_text(json.dumps(geo, indent=1), encoding="utf-8")
    Image.fromarray(tex).save(ASSETS / "textures/entity/moss_crawler.png")
    Image.fromarray(glow).save(ASSETS / "textures/entity/moss_crawler_glowmask.png")
    spec = {"idle": idle(), "walk": walk(), "pounce": pounce(), "hurt": hurt(), "death": death()}
    (ASSETS / "animations/entity/moss_crawler.animation.json").write_text(
        json.dumps(anim_json(spec), indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    subprocess.check_call([sys.executable, str(HERE / "lib" / "fix_geo_face_sides.py"), "--apply", "moss_crawler"])
    g = json.loads((ASSETS / "geo/entity/moss_crawler.geo.json").read_text(encoding="utf-8"))
    ov = voxel.check_overlaps(g)
    n_box = sum(len(b.get("cubes", [])) for b in g["minecraft:geometry"][0]["bones"])
    n_face = sum(len(c["uv"]) for b in g["minecraft:geometry"][0]["bones"] for c in b.get("cubes", []))
    print(f"installed: {n_box} boxes / {n_face} faces, overlaps {len(ov)}")
    if ov:
        raise SystemExit("overlaps: " + "; ".join(ov[:5]))


if __name__ == "__main__":
    main()
