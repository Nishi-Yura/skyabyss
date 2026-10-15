"""骨棘の射手 (rib_archer) 第4回: 弓を別のモデル (gear) にする版。docs/roughs/GEAR_BRIEF.md の決まりに従う。

直す点:
- 弓と弦を rib_archer.geo.json から外し、geo/gear/rib_archer_bow.geo.json (+ textures/gear, animations/gear) にする。
  弓のモデルは握りの中心が原点。弓は y 方向、腹 (弧のふくらみ) が +z、弦が -z。
- 射手の geo には左の手 (forearm_l) の子に受け口 socket_off を置く (pivot は拳の中心 3, 11.5, 0)。
- 休み・歩き: 弓は手から下へ垂れる。下の端は足もとの少し前、上の端は腕の後ろ (弓の面は体の横で前後に向く)。
- 射る: 左腕を前へ上げ、受け口で弓を体の前に縦に立てる。右手は弦を顎の下まで引く。弦の動きは gear 側の draw。

GeckoLib の回転の約束 (4.9.3 のバイトコードで確かめた):
  geo の骨の回転と、アニメーションの回転 (定数も molang も) は、x と y の符号を反転してから Rz・Ry・Rx で掛け、
  さらに立方体と pivot の x を反転して描く。geo の座標で見ると、書いた値 (rx, ry, rz) は
  標準の回転 R(-rx, ry, -rz) と同じ (R は voxel.py の _rot)。そこで、このスクリプトは姿勢を標準の R で考え、
  ファイルには conv(rx, ry, rz) = (-rx, ry, -rz) を書く。描くときも conv で戻し、最後に x を鏡に映して、
  ゲームと同じ見え方にする (rib_archer は root 骨 [0,180,0] の子なので、ゲームでは作った座標の左右が入れ替わる)。

書き出すもの:
  src/main/resources/assets/skyabyss/geo/entity/rib_archer.geo.json (弓を外し socket_off を足す。root 骨はそのまま)
  src/main/resources/assets/skyabyss/animations/entity/rib_archer.animation.json
  src/main/resources/assets/skyabyss/geo/gear/rib_archer_bow.geo.json
  src/main/resources/assets/skyabyss/textures/gear/rib_archer_bow.png (光る画素はないので glowmask なし)
  src/main/resources/assets/skyabyss/animations/gear/rib_archer_bow.animation.json
  docs/roughs/v4/rib_archer.png (行: 休み / 構え / 放った直後。弓を受け口に合成して描く)

実行 (リポジトリ直下): python tools/rough/rib_archer_v4.py
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import voxel as vx  # noqa: E402
import mobs_small_v2 as s  # noqa: E402

ROOT = vx.ROOT
ASSET = ROOT / "src" / "main" / "resources" / "assets" / "skyabyss"
GEO = ASSET / "geo" / "entity" / "rib_archer.geo.json"
OUT = ROOT / "docs" / "roughs" / "v4"

SH_L = np.array([3.0, 21.0, 0.0])
SH_R = np.array([-3.0, 21.0, 0.0])
EL_R = np.array([-3.0, 16.5, 0.0])
FIST_L = np.array([3.0, 11.5, 0.0])   # 受け口 socket_off の pivot (左の拳の中心)

# 弓 (gear の座標、握りの中心が原点。弓の面は x = 0..0.5、中心 x = 0.25)
HALF = 7.0          # 握りから端までの長さ
DEPTH = 1.0         # 端が握りより弦の側 (-z) へ下がる量
STR_Z = -1.75       # 弦の中心の z (弦の箱は z -2 .. -1.5)
TOP_G = np.array([0.25, HALF, STR_Z])
BOT_G = np.array([0.25, -HALF, STR_Z])
NOCK_G = np.array([0.25, 0.0, STR_Z])

# 休みの受け口の回転 (標準の R)。上の端を後ろと外へ、下の端を前と内へ倒す
REST_SOCKET_STD = (-20.0, 0.0, -9.0)
NOCK_TARGET_Z = 5.75  # 引いた弦 (ノック) の z。顎の前 (顔の前の面は z 4.5)
AIM_PITCH = -99.0     # 左腕の x (標準)。-90 で水平、少し上げて握りを顎の高さへ


def R(rx, ry, rz):
    return vx._rot(rx, ry, rz)


def euler(M):
    b = -math.asin(max(-1.0, min(1.0, M[2, 0])))
    a = math.atan2(M[2, 1], M[2, 2])
    c = math.atan2(M[1, 0], M[0, 0])
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def conv(v):
    """標準の R の角度 <-> GeckoLib に書く角度 (同じ式で行き来できる)。"""
    return [-float(v[0]), float(v[1]), -float(v[2])]


def r1(v):
    return [round(float(a), 1) + 0.0 for a in v]


# ---------------------------------------------------------------- 弓のモデル

def build_bow():
    m = s.Model("rib_archer_bow")
    BN, DK, TR = "bone", "bone_dirty", "trim"
    m.bone("bow", (0.0, 0.0, 0.0))
    m.bone("string_up", tuple(TOP_G), "bow")
    m.bone("string_lo", tuple(BOT_G), "bow")
    # 握り: 巻いた帯 (x -0.5..1、y -1..1、z -0.5..0.5)
    m.box("bow", -0.5, -1.0, -0.5, 1.0, 1.0, 0.5, TR)
    for sign in (1, -1):
        for k in range(12):              # 握りの外から端まで 0.5 ずつ (y 1 .. 7)
            a = 1.0 + 0.5 * k
            t = (a + 0.25) / HALF
            off = -round(2 * DEPTH * t * t) / 2
            y0, y1 = (a, a + 0.5) if sign > 0 else (-a - 0.5, -a)
            if t < 0.3:                  # 握りの近くは太い
                m.box("bow", -0.5, y0, off - 0.5, 1.0, y1, off + 0.5, DK)
            else:
                m.box("bow", 0.0, y0, off - 0.5, 0.5, y1, off, DK)
                if t < 0.7:              # 腹の側の層 (端へ向かって細くなる)
                    m.box("bow", 0.0, y0, off, 0.5, y1, off + 0.5, BN)
        # 端の弦かけ (弦の側へ少し反る)
        y0, y1 = (HALF, HALF + 0.5) if sign > 0 else (-HALF - 0.5, -HALF)
        m.box("bow", 0.0, y0, -2.0, 0.5, y1, -0.5, TR)
    m.box("string_up", 0.0, 0.0, -2.0, 0.5, HALF, -1.5, "trim")
    m.box("string_lo", 0.0, -HALF, -2.0, 0.5, 0.0, -1.5, "trim")
    old_w = s.TEX_W
    s.TEX_W = 64
    try:
        geo, tex, gl = m.finish()
    finally:
        s.TEX_W = old_w
    d = geo["minecraft:geometry"][0]["description"]
    d.update({"identifier": "geometry.rib_archer_bow", "visible_bounds_width": 2,
              "visible_bounds_height": 2, "visible_bounds_offset": [0, 0, 0]})
    return geo, tex, gl


def nock_gear(psi):
    """弦を psi 度 (標準の R、2本とも) 引いたときの、弦の中ほどの位置 (gear の座標)。"""
    up = TOP_G + R(psi, 0, 0) @ (NOCK_G - TOP_G)
    lo = BOT_G + R(-psi, 0, 0) @ (NOCK_G - BOT_G)
    return (up + lo) / 2


# ---------------------------------------------------------------- 射手の姿勢 (標準の R で解く)

def solve_right_arm(N, E0):
    def hand_elbow(v):
        Ra, Rf = R(*v[:3]), R(*v[3:])
        E = SH_R + Ra @ (EL_R - SH_R)
        H = SH_R + Ra @ ((EL_R - SH_R) + Rf @ np.array([0.0, -5.0, 0.0]))
        return H, E

    def cost(v):
        H, E = hand_elbow(v)
        return float(np.sum((H - N) ** 2) + 0.05 * np.sum((E - E0) ** 2) + 1e-5 * np.sum(v ** 2))

    rng = np.random.default_rng(4)
    best_v, best_c = None, 1e9
    for _ in range(60):
        v = rng.uniform(-120, 120, 6)
        c, step = cost(v), 30.0
        while step > 0.05:
            moved = False
            for i in range(6):
                for d in (step, -step):
                    w = v.copy()
                    w[i] += d
                    cw = cost(w)
                    if cw < c:
                        v, c, moved = w, cw, True
            if not moved:
                step *= 0.5
        if c < best_c:
            best_v, best_c = v, c
    H, _ = hand_elbow(best_v)
    return list(best_v[:3]), list(best_v[3:]), H


def poses():
    # 左腕: 前へ上げ、握りが顔の正面 (x 0.5) に来る向きを選ぶ
    best = None
    for phi in np.arange(-40.0, 0.5, 0.5):
        Ra = R(AIM_PITCH, phi, 0)
        G = SH_L + Ra @ (FIST_L - SH_L)
        err = abs(G[0] - 0.5)
        if best is None or err < best[0]:
            best = (err, float(phi), Ra, G)
    _, phi, Ra_l, G = best
    # 受け口: 弓を世界で縦 (y)、腹を前 (+z) にする
    S_aim = Ra_l.T @ np.eye(3)
    sock_aim_std = euler(S_aim)
    # 弦を引く量: ノックが顎の前 (NOCK_TARGET_Z) に来るまで
    psis = np.arange(0.0, 45.0, 0.25)
    psi = float(min(psis, key=lambda p: abs((G + nock_gear(p))[2] - NOCK_TARGET_Z)))
    N = G + nock_gear(psi)
    E0 = np.array([-6.5, 20.0, 2.0])
    ar, fr, H = solve_right_arm(N, E0)
    Nrel = G + NOCK_G + np.array([-0.6, -0.4, -2.2])  # 放った後: 右手は顎の横を後ろへ抜ける
    ar2, fr2, H2 = solve_right_arm(Nrel, E0)
    rest_w = conv(REST_SOCKET_STD)
    aim_w = conv(sock_aim_std)
    sock_delta = [a - b for a, b in zip(aim_w, rest_w)]
    left = conv([AIM_PITCH, phi, 0.0])
    aim = {"arm_l": left, "socket_off": sock_delta, "arm_r": conv(ar), "forearm_r": conv(fr)}
    release = {"arm_l": left, "socket_off": sock_delta, "arm_r": conv(ar2), "forearm_r": conv(fr2)}
    aim = {k: r1(v) for k, v in aim.items()}
    release = {k: r1(v) for k, v in release.items()}
    info = dict(phi=phi, grip=r1(G), psi=psi, nock=r1(N), hand=r1(H), hand_err=round(float(np.linalg.norm(H - N)), 2),
                hand_rel=r1(H2), rel_err=round(float(np.linalg.norm(H2 - Nrel)), 2), sock_aim_written=r1(aim_w))
    return aim, release, psi, info


# ---------------------------------------------------------------- アニメーション

def neg(e):
    e = str(e).strip()
    if e in ("0", "0.0"):
        return "0"
    try:
        return str(-float(e))
    except ValueError:
        return f"-({e})"


def conv_expr(v):
    return [neg(v[0]), str(v[1]), neg(v[2])]


def archer_anims(aim, release):
    # idle / walk は第3回と同じ動き。標準の R で考えた値を conv して書く (x と z の符号を反転)
    idle = {"loop": True, "animation_length": 3.0, "bones": {
        "spine": {"position": ["0", "math.sin(query.anim_time * 120) * 0.3", "0"],
                  "rotation": conv_expr(["math.sin(query.anim_time * 120) * 1.0", "0", "0"])},
        "skull": {"rotation": conv_expr(["math.sin(query.anim_time * 120 + 90) * 1.0",
                                         "math.sin(query.anim_time * 120 + 60) * 3.0", "0"])},
        "arm_l": {"rotation": conv_expr(["math.sin(query.anim_time * 120 + 40) * 1.0", "0", "0"])},
        "arm_r": {"rotation": conv_expr(["math.sin(query.anim_time * 120 - 40) * 2.5", "0", "0"])},
        "forearm_r": {"rotation": conv_expr(["math.sin(query.anim_time * 120) * 2.0", "0", "0"])},
    }}
    walk = {"loop": True, "animation_length": 1.0, "bones": {
        "leg_l": {"rotation": conv_expr(["math.sin(query.anim_time * 360) * 32", "0", "0"])},
        "leg_r": {"rotation": conv_expr(["math.sin(query.anim_time * 360) * -32", "0", "0"])},
        "arm_l": {"rotation": conv_expr(["math.sin(query.anim_time * 360) * -4", "0", "0"])},
        "arm_r": {"rotation": conv_expr(["math.sin(query.anim_time * 360) * 22", "0", "0"])},
        "forearm_r": {"rotation": conv_expr(["-8 + math.sin(query.anim_time * 360 + 90) * 8", "0", "0"])},
        "spine": {"position": ["0", "math.abs(math.sin(query.anim_time * 360)) * 0.8", "0"],
                  "rotation": conv_expr(["4", "math.sin(query.anim_time * 360) * 3", "0"])},
        "skull": {"rotation": conv_expr(["0", "math.sin(query.anim_time * 360 + 180) * 3", "0"])},
    }}
    # shoot (1.0 秒): 0.28 で構え、0.45 まで引いて止め、0.50 で放つ、0.95 で休みへ
    seq = [(0.00, {}), (0.28, aim), (0.45, aim), (0.50, release), (0.66, release), (0.95, {})]
    shoot = {}
    for bn in ("arm_l", "socket_off", "arm_r", "forearm_r"):
        shoot[bn] = {"rotation": {f"{t:.2f}": (p[bn] if bn in p else [0.0, 0.0, 0.0]) for t, p in seq}}
    return {"format_version": "1.8.0", "animations": {
        "animation.rib_archer.idle": idle,
        "animation.rib_archer.walk": walk,
        "animation.rib_archer.shoot": {"animation_length": 1.0, "bones": shoot},
    }}


def bow_anims(psi):
    up = lambda a: conv([a, 0, 0])      # noqa: E731  string_up は +a (標準) で弦の中ほどが -z へ
    lo = lambda a: conv([-a, 0, 0])     # noqa: E731
    keys = [(0.00, 0.0), (0.08, 0.0), (0.28, psi), (0.45, psi), (0.50, -0.25 * psi), (0.55, 0.12 * psi),
            (0.60, -0.05 * psi), (0.66, 0.0), (1.00, 0.0)]
    draw = {"animation_length": 1.0, "bones": {
        "string_up": {"rotation": {f"{t:.2f}": r1(up(a)) for t, a in keys}},
        "string_lo": {"rotation": {f"{t:.2f}": r1(lo(a)) for t, a in keys}},
    }}
    idle = {"loop": True, "animation_length": 1.0, "bones": {
        "string_up": {"rotation": [0, 0, 0]},
        "string_lo": {"rotation": [0, 0, 0]},
    }}
    return {"format_version": "1.8.0", "animations": {
        "animation.rib_archer_bow.idle": idle,
        "animation.rib_archer_bow.draw": draw,
    }}


# ---------------------------------------------------------------- 射手の geo

def archer_geo():
    geo = json.loads(GEO.read_text(encoding="utf-8"))
    g = geo["minecraft:geometry"][0]
    names = [b["name"] for b in g["bones"]]
    assert "root" in names and g["bones"][names.index("root")].get("rotation") == [0, 180, 0], "root 骨の向きの修正がない"
    g["bones"] = [b for b in g["bones"] if b["name"] not in ("bow", "string_up", "string_lo", "socket_off")]
    g["bones"].append({"name": "socket_off", "parent": "forearm_l", "pivot": [float(v) for v in FIST_L],
                       "rotation": r1(conv(REST_SOCKET_STD))})
    g["description"]["identifier"] = "geometry.rib_archer"
    return geo


# ---------------------------------------------------------------- ゲームと同じ見え方で描く

def game_geo(geo, pose):
    """書いた値 (GeckoLib) を標準の R の値へ戻した geo と pose。"""
    gg = copy.deepcopy(geo)
    for b in gg["minecraft:geometry"][0]["bones"]:
        if "rotation" in b:
            b["rotation"] = conv(b["rotation"])
    return gg, {k: conv(v) for k, v in pose.items()}


def attach(archer, bow, socket="socket_off"):
    """弓の骨を受け口の子にした geo (射手の立方体は外す)。弓の座標は受け口の pivot からの差。"""
    a = copy.deepcopy(archer)
    ga = a["minecraft:geometry"][0]
    for b in ga["bones"]:
        b["cubes"] = []
    piv = next(np.array(b["pivot"], float) for b in ga["bones"] if b["name"] == socket)
    for b in copy.deepcopy(bow)["minecraft:geometry"][0]["bones"]:
        b["name"] = "gear_" + b["name"]
        b["parent"] = "gear_" + b["parent"] if "parent" in b else socket
        b["pivot"] = list(np.array(b.get("pivot", [0, 0, 0]), float) + piv)
        for c in b.get("cubes", []):
            c["origin"] = list(np.array(c["origin"], float) + piv)
        ga["bones"].append(b)
    a["minecraft:geometry"][0]["description"] = bow["minecraft:geometry"][0]["description"]
    return a


def mirror_x(faces):
    out = []
    for f in faces:
        M = np.array([-1.0, 1.0, 1.0])
        out.append(vx.Face((f.origin + f.eu) * M, -f.eu * M, f.ev * M, f.tex[:, ::-1],
                           None if f.glow is None else f.glow[:, ::-1], f.shade))
    return out


def render(archer, atex, agl, bow, btex, pose, bow_pose, yaw, pitch):
    ag, ap = game_geo(archer, pose)
    faces = vx.load_geo(ag, atex, agl, pose=ap)
    comb = attach(archer, bow)
    cg, cp = game_geo(comb, {**pose, **{"gear_" + k: v for k, v in bow_pose.items()}})
    faces += vx.load_geo(cg, btex, None, pose=cp)
    return vx.render(mirror_x(faces), yaw=yaw, pitch=pitch, size=(360, 360))


def main():
    bow, btex, bgl = build_bow()
    print("bow overlaps:", vx.check_overlaps(bow) or "none", "boxes:",
          sum(len(b["cubes"]) for b in bow["minecraft:geometry"][0]["bones"]), "tex", btex.shape,
          "glow px:", int((bgl[..., 3] > 0).sum()))
    archer = archer_geo()
    print("archer overlaps:", vx.check_overlaps(archer) or "none")
    aim, release, psi, info = poses()
    print("solve:", info)
    print("aim:", aim)
    print("release:", release)

    (ASSET / "geo" / "gear").mkdir(parents=True, exist_ok=True)
    (ASSET / "textures" / "gear").mkdir(parents=True, exist_ok=True)
    (ASSET / "animations" / "gear").mkdir(parents=True, exist_ok=True)
    GEO.write_text(json.dumps(archer, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (ASSET / "animations" / "entity" / "rib_archer.animation.json").write_text(
        json.dumps(archer_anims(aim, release), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    (ASSET / "geo" / "gear" / "rib_archer_bow.geo.json").write_text(
        json.dumps(bow, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(btex, "RGBA").save(ASSET / "textures" / "gear" / "rib_archer_bow.png")
    (ASSET / "animations" / "gear" / "rib_archer_bow.animation.json").write_text(
        json.dumps(bow_anims(psi), indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    atex = np.array(Image.open(ASSET / "textures" / "entity" / "rib_archer.png").convert("RGBA"))
    agl = np.array(Image.open(ASSET / "textures" / "entity" / "rib_archer_glowmask.png").convert("RGBA"))
    drawn = {"string_up": r1(conv([psi, 0, 0])), "string_lo": r1(conv([-psi, 0, 0]))}
    rows = [({}, {}), (aim, drawn), (release, {})]
    cols = []
    for label, yaw, pitch in (("正面 3/4 (弓の側)", 155, 15), ("横 (弓の側)", 80, 8), ("後ろ 3/4", 25, 15)):
        cols.append((label, [render(archer, atex, agl, bow, btex, p, bp, yaw, pitch) for p, bp in rows]))
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = vx.sheet("rib_archer 第4回 (弓を別のモデルにして受け口に付ける)", cols,
                     "行: 上から 休み / 構え (弦を引く) / 放った直後。ゲームの向きと左右で描く (GeckoLib の回転と鏡を再現)")
    sheet.save(OUT / "rib_archer.png")
    print("wrote", OUT / "rib_archer.png")


if __name__ == "__main__":
    main()
