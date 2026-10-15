"""鯨骨の番兵長 第3回: 採用案 B (v2) の鉾と手を直す。

直したこと:
- 右手の指を 3段の「C」形の握りにし、柄が指の間 (手の幅の中央) を通るようにした。
  左手にも同じ握りを置く (薙ぎ払いで左手が柄を持つ)。
- 鉾 (pike) を右手の子骨にし、回転の中心を握りの位置 (柄の軸) に置いた。柄は手に対して回さない。
- アニメーションの姿勢は、腕・前腕・手の回転を解いて、柄の向きと握りの位置を決めた。
  薙ぎ払いでは左手の握りが柄の軸の上に来るように解いた。

実行 (リポジトリ直下): python tools/rough/captain_v3.py [--install]
出力:
  docs/roughs/v3/whalebone_captain.png  (列: 前回 B / 直した後。行: 通常 / 薙ぎ払い途中 / 叩きつけ)
  docs/roughs/v3/whalebone_captain/*_keys.png (デバッグ用: 各動作の全キー)
  --install を付けると、src/main/resources の geo / texture / glowmask / animation を書き換える。
"""

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: 番兵長の geo / png / glowmask / animation を v3 に戻す
#   採用版の生成元: tools/rough/captain_v4.py --install
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and "--install" in _sys.argv and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/rough/captain_v3.py は停止中です: 採用済みの素材 (番兵長の geo / png / glowmask / animation を v3 に戻す) を上書きするため。"
              "採用版の生成元は tools/rough/captain_v4.py --install です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.optimize import least_squares

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import captain_v2 as v2  # noqa: E402
from voxel import _rot, load_geo, render, sheet  # noqa: E402

ROOT = HERE.parents[1]
OUT = ROOT / "docs" / "roughs" / "v3"
GAME = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
SUBJECT = "whalebone_captain"
ROWS = [(7.0, 8.0), (8.0, 9.0), (9.0, 10.0)]   # 握りの 3段
GRIP_L_LOCAL = np.array([14.5, 8.5, -5.0])     # 左手の握り (手の中の位置)

NEW_BONES = []
for _n, _p, _par in v2.BONES:
    if _n.startswith("fR"):
        continue
    if _n == "pike":
        _p = (-14.5, 8.5, -5.0)                # 握りの位置を回転の中心にする
    NEW_BONES.append((_n, _p, _par))
PIV = {n: np.array(p, float) for n, p, _ in NEW_BONES}
PAR = {n: par for n, _, par in NEW_BONES}
NAMES = ["armR", "forearmR", "handR", "armL", "forearmL", "handL"]
GROUP = ["body", "head", "legR", "legL"] + NAMES
REST_BODY = (0.0, 0.0, 0.0)


def build_b():
    m = v2.build("b")
    for n in m.bnames:
        if n.startswith("fR"):
            m.owner[m.owner == m.bid(n)] = -1
    for xc, bone in ((-14.5, "handR"), (14.5, "handL")):
        for y0, y1 in ROWS:
            m.add(v2.rbox((xc - 1.5, y0, -5.5), (xc - 0.5, y1, -2.5)), bone, "iron_dk:gv")
            m.add(v2.rbox((xc + 0.5, y0, -5.5), (xc + 1.5, y1, -2.5)), bone, "iron_dk:gv")
            m.add(v2.rbox((xc - 1.5, y0, -6.5), (xc + 1.5, y1, -5.5)), bone, "iron:rv")
    return m


# ---------------------------------------------------------------- 運動学 (GeckoLib と同じ変換)

def xforms(pose):
    cache = {}

    def xf(n):
        if n in cache:
            return cache[n]
        R = _rot(*pose.get(n, (0.0, 0.0, 0.0)))
        p = PIV[n]
        M = (R, p - R @ p)
        if PAR[n]:
            PR, Pt = xf(PAR[n])
            M = (PR @ M[0], PR @ M[1] + Pt)
        cache[n] = M
        return M
    return xf


def measure(pose):
    xf = xforms(pose)
    Mp = xf("pike")
    gR = Mp[0] @ PIV["pike"] + Mp[1]          # 右の握り (柄の軸上)
    dR = Mp[0] @ np.array([0.0, 1.0, 0.0])    # 柄の向き
    Ml = xf("handL")
    gL = Ml[0] @ GRIP_L_LOCAL + Ml[1]         # 左手の握り
    dL = Ml[0] @ np.array([0.0, 1.0, 0.0])    # 左手の向き
    return gR, dR, gL, dL


def pose_from(x, body, extra):
    pose = {"body": tuple(float(v) for v in body)}
    for i, n in enumerate(NAMES):
        pose[n] = tuple(float(v) for v in x[3 * i:3 * i + 3])
    pose.update(extra)
    return pose


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


def solve(G, d, body, x_guess, extra, left, s0=0.0):
    """右の握りを G、柄の向きを d にする。left なら左手も柄の軸に乗せる。"""
    G = np.asarray(G, float)
    d = unit(d)
    x0 = np.array(x_guess, float)
    free = np.arange(18) if left else np.arange(9)

    def full(z):
        x = x0.copy()
        x[free] = z
        return x

    def res(z):
        x = full(z)
        gR, dR, gL, dL = measure(pose_from(x, body, extra))
        r = [2.0 * (gR - G), 3.0 * (dR - d)]
        if left:
            v = gL - gR
            s = v @ dR
            perp = v - s * dR
            r += [2.0 * perp, 3.0 * (dL - dR), [0.3 * (s - s0)]]
        r.append(0.03 * (x[free] - x0[free]))
        return np.concatenate([np.ravel(a) for a in r])

    best = None
    rng = np.random.default_rng(3)
    starts = [x0[free]] + [x0[free] + rng.normal(0, 40, len(free)) for _ in range(24)]
    for z0 in starts:
        r = least_squares(res, z0, method="trf", max_nfev=2000)
        if best is None or r.cost < best.cost:
            best = r
    return full(best.x)


def report(name, x, body, extra, G, d, left, s0):
    gR, dR, gL, dL = measure(pose_from(x, body, extra))
    line = f"{name}: grip err {np.linalg.norm(gR - np.asarray(G)):.2f}, dir err {1 - dR @ unit(d):.4f}"
    if left:
        v = gL - gR
        s = v @ dR
        perp = np.linalg.norm(v - s * dR)
        line += f"; left on shaft: off-axis {perp:.2f}, along {s:.1f} (shaft runs -8.5..36), left dir err {1 - dL @ dR:.4f}"
    print(line, flush=True)


# ---------------------------------------------------------------- キーフレーム

SPECS = {
    "sweep": [
        dict(t=0.00, G=(-14.5, 21, -4), d=(0.4, 0, 0.9), left=False, body=(0, 15, 0), armL=(-60, 0, 0)),
        dict(t=0.35, G=(-9, 21, -10), d=(0.8, 0, -0.6), left=True, s0=14, body=(0, 0, 0)),
        dict(t=0.80, G=(-4, 21, -10), d=(0.9, 0, -0.4), left=True, s0=16, body=(0, -20, 0)),
    ],
    "slam": [
        dict(t=0.00, rest=True),
        dict(t=0.18, G=(-14.5, 36, -1), d=(0, 0.7, 0.7), body=(-10, 0, 0), armL=(-150, 0, 0)),
        dict(t=0.34, G=(-14.5, 24, -14), d=(0, -0.5, -0.87), body=(12, 0, 0), armL=(60, 0, 0)),
        dict(t=0.70, rest=True),
    ],
    "roar": [
        dict(t=0.00, rest=True),
        dict(t=0.35, G=(-14.5, 34, -2), d=(0, 0.9, -0.43), body=(-10, 0, 0), armL=(150, 0, 0),
             extra={"head": (-35, 0, 0)}),
        dict(t=1.10, G=(-14.5, 34, -2), d=(0, 0.9, -0.43), body=(-10, 0, 0), armL=(150, 0, 0),
             extra={"head": (-35, 0, 0)}),
        dict(t=1.40, rest=True),
    ],
    "leap": [
        dict(t=0.00, rest=True),
        dict(t=0.30, rest=True, body=(-10, 0, 0), extra={"legR": (-35, 0, 0), "legL": (-35, 0, 0)}),
        dict(t=0.50, G=(-14.5, 30, -12), d=(0, -0.2, -0.98), body=(25, 0, 0), armL=(60, 0, 0),
             extra={"legR": (-35, 0, 0), "legL": (-35, 0, 0)}),
        dict(t=0.80, rest=True),
    ],
}


def solve_action(name, specs):
    """各キーの (時刻, 姿勢) を返す。G のあるキーは柄の握りを解く。"""
    out = []
    guess = np.zeros(18)
    for s in specs:
        extra = dict(s.get("extra", {}))
        body = s.get("body", REST_BODY)
        left = s.get("left", False)
        armL = s.get("armL", (0, 0, 0))
        if s.get("rest"):
            x = np.zeros(18)
            x[9:12] = armL
        else:
            x = guess.copy()
            x[9:12] = armL
            if not left:
                x[9:12] = armL
            x = solve(s["G"], s["d"], body, x, extra, left, s.get("s0", 0.0))
            if not left:
                x[9:12] = armL
            report(f"{name} t={s['t']:.2f}", x, body, extra, s["G"], s["d"], left, s.get("s0", 0.0))
        guess = x
        out.append((s["t"], pose_from(x, body, extra)))
    return out


# ---------------------------------------------------------------- アニメーション (JSON)

def rot_list(pose, b):
    return [round(float(v), 2) for v in pose.get(b, (0.0, 0.0, 0.0))]


def clip(poses):
    bones = {}
    for b in GROUP:
        if all(all(abs(v) < 1e-9 for v in p.get(b, (0, 0, 0))) for _, p in poses):
            continue
        bones[b] = {"rotation": {f"{t:.2f}": rot_list(p, b) for t, p in poses}}
    return {"loop": False, "animation_length": round(poses[-1][0], 2), "bones": bones}


def molang_clips():
    idle = {
        "loop": True, "animation_length": 3.0,
        "bones": {
            "body": {"rotation": ["1.5 + math.sin(query.anim_time * 120) * 1.5", "0", "0"]},
            "head": {"rotation": ["math.sin(query.anim_time * 120 + 90) * 2",
                                  "math.sin(query.anim_time * 60) * 4", "0"]},
            "armR": {"rotation": ["math.sin(query.anim_time * 120) * 1.5", "0", "0"]},
            "handR": {"rotation": ["0", "0", "math.sin(query.anim_time * 120) * 2"]},
            "armL": {"rotation": ["math.sin(query.anim_time * 120 + 180) * 2", "0", "-3"]},
        },
    }
    walk = {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "legR": {"rotation": ["-math.sin(query.anim_time * 360) * 30", "0", "0"]},
            "legL": {"rotation": ["math.sin(query.anim_time * 360) * 30", "0", "0"]},
            "armR": {"rotation": ["math.sin(query.anim_time * 360) * 15", "0", "0"]},
            "armL": {"rotation": ["-math.sin(query.anim_time * 360) * 15", "0", "0"]},
            "body": {"rotation": ["2", "math.sin(query.anim_time * 360) * 2", "0"]},
        },
    }
    return idle, walk


def write_anim(actions, path):
    idle, walk = molang_clips()
    data = {"format_version": "1.8.0", "animations": {
        "animation.whalebone_captain.idle": idle,
        "animation.whalebone_captain.walk": walk,
    }}
    for name, poses in actions.items():
        data["animations"][f"animation.whalebone_captain.{name}"] = clip(poses)
    path.write_text(json.dumps(data, indent=1), encoding="utf-8")


# ---------------------------------------------------------------- 描画

PREV_POSE = {
    "rest": {},
    "sweep": {"armR": (95, 0, 0), "forearmR": (-20, 0, 0)},
    "slam": {"armR": (120, 0, 0)},
}


def main(install=False):
    actions = {name: solve_action(name, specs) for name, specs in SPECS.items()}

    m = build_b()
    v2.BONES = NEW_BONES
    geo, tex, gl, nbox, nface = v2.export(m, "b", SUBJECT)
    ov = v2.overlaps(m)
    print(f"boxes {nbox} faces {nface} overlaps {len(ov)} texture {tex.shape[1]}x{tex.shape[0]}", flush=True)
    ys = []
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b["cubes"]:
            ys += [c["origin"][1], c["origin"][1] + c["size"][1]]
    print(f"model height (units) {min(ys):.2f} .. {max(ys):.2f}", flush=True)

    sweep_pose = dict(actions["sweep"])[0.35]
    slam_pose = dict(actions["slam"])[0.34]
    rows = [("rest", {}), ("sweep", sweep_pose), ("slam", slam_pose)]

    prev_dir = ROOT / "docs/roughs/v2/whalebone_captain/b"
    prev_geo = json.loads((prev_dir / "geo.json").read_text(encoding="utf-8"))
    prev_tex = np.array(Image.open(prev_dir / "texture.png").convert("RGBA"))
    prev_gl = np.array(Image.open(prev_dir / "glowmask.png").convert("RGBA"))
    prev_imgs = [render(load_geo(prev_geo, prev_tex, prev_gl, pose=PREV_POSE[k]), yaw=145, pitch=14,
                        size=(460, 600), margin=0.03) for k in ("rest", "sweep", "slam")]
    new_imgs = [render(load_geo(geo, tex, gl, pose=p), yaw=145, pitch=14, size=(460, 600), margin=0.03)
                for _, p in rows]
    sheet_img = sheet("鯨骨の番兵長 第3回 (鉾と手を直した B)",
                      [("前回 B (v2)", prev_imgs), ("直した後 (v3)", new_imgs)],
                      "行: 通常 / 薙ぎ払い (途中) / 叩きつけ (着地)。鉾は右手の子骨で、柄は両手の握りを通る")
    OUT.mkdir(parents=True, exist_ok=True)
    sheet_img.save(OUT / f"{SUBJECT}.png")

    dbg = OUT / SUBJECT
    dbg.mkdir(parents=True, exist_ok=True)
    for name, poses in actions.items():
        imgs = [render(load_geo(geo, tex, gl, pose=p), yaw=145, pitch=14, size=(300, 400), margin=0.03)
                for _, p in poses]
        strip = Image.new("RGB", (sum(i.width for i in imgs) + 10 * len(imgs), max(i.height for i in imgs)),
                          (18, 18, 26))
        x = 0
        for i in imgs:
            strip.paste(i, (x, 0))
            x += i.width + 10
        strip.save(dbg / f"{name}_keys.png")

    if install:
        (GAME / "geo/entity" / f"{SUBJECT}.geo.json").write_text(json.dumps(geo, ensure_ascii=False),
                                                                  encoding="utf-8")
        Image.fromarray(tex, "RGBA").save(GAME / "textures/entity" / f"{SUBJECT}.png")
        Image.fromarray(gl, "RGBA").save(GAME / "textures/entity" / f"{SUBJECT}_glowmask.png")
        write_anim(actions, GAME / "animations/entity" / f"{SUBJECT}.animation.json")
        print("installed", flush=True)


if __name__ == "__main__":
    main(install="--install" in sys.argv)
