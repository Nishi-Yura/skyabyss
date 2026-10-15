"""骨棘の射手 (rib_archer) 第3回: 採用案 B の弓を左の手に付け直す版。

直す点 (docs/roughs/v3/rib_archer.png で確かめる):
- 弓の根元が肘にあって浮いていた。弓の骨 (bow) を左の手 (forearm_l) の子にして、
  握りの中ほどを pivot にする。休みの姿勢では弓は体の左側に垂直で、弦は体の側。
- 腕を肘で分ける (arm_* と forearm_*)。右手で弦を口もとまで引けるようにするため。
- 弦は上と下の2本の骨 (string_up, string_lo)。引くときは、それぞれ先の角 (弓の端) を pivot にして回す。
- 構えは、左腕を肩の高さで真っすぐ前へ出し、弓を垂直に立てる。右手は弦の所 (口もと) まで引く。

このスクリプトは、docs/roughs/v2/rib_archer/b の幾何を元に作り直して、次を書き出す:
  src/main/resources/assets/skyabyss/geo/entity/rib_archer.geo.json
  src/main/resources/assets/skyabyss/textures/entity/rib_archer.png
  src/main/resources/assets/skyabyss/textures/entity/rib_archer_glowmask.png
  src/main/resources/assets/skyabyss/animations/entity/rib_archer.animation.json
  docs/roughs/v3/rib_archer.png (前回と直した後の比較シート)

実行 (リポジトリ直下): python tools/rough/rib_archer_v3.py
座標: y が上。前は +z。回転は度。合成は voxel.py の _rot と同じ (Rz @ Ry @ Rx)。
"""
from __future__ import annotations

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: 射手の geo / animation を v3 に戻す
#   採用版の生成元: tools/rough/rib_archer_v4.py
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/rough/rib_archer_v3.py は停止中です: 採用済みの素材 (射手の geo / animation を v3 に戻す) を上書きするため。"
              "採用版の生成元は tools/rough/rib_archer_v4.py です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")

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
OLD = ROOT / "docs" / "roughs" / "v2" / "rib_archer" / "b"
OUT = ROOT / "docs" / "roughs" / "v3"

SH_L = np.array([3.0, 21.0, 0.0])     # 左の肩 (arm_l の pivot)
SH_R = np.array([-3.0, 21.0, 0.0])    # 右の肩
EL_L = np.array([3.0, 16.5, 0.0])     # 左のひじ (forearm_l の pivot)
EL_R = np.array([-3.0, 16.5, 0.0])
GRIP = np.array([4.5, 11.5, 0.0])     # 弓の握り (bow の pivot、弓の中ほど)
TOP = np.array([3.25, 18.5, 0.75])    # 弓の上の端 (string_up の pivot)
BOT = np.array([3.25, 4.5, 0.75])     # 弓の下の端 (string_lo の pivot)
REST_NOCK = np.array([3.25, 11.5, 0.75])
NOCK_Z = 4.8                          # 弦を引いた所 (口の前)
BELLY_TO_FRONT = (0.0, -90.0, 0.0)    # 弓の腹 (+x) を前 (+z) へ向ける


def R(rx, ry, rz):
    """度で受けて、Rz @ Ry @ Rx を返す (voxel.py の _rot と同じ)。"""
    rx, ry, rz = (math.radians(a) for a in (rx, ry, rz))
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    Rx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    Ry = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


def euler(M):
    """R(rx, ry, rz) == M になる角度 (度)。"""
    b = -math.asin(max(-1.0, min(1.0, M[2, 0])))
    a = math.atan2(M[2, 1], M[2, 2])
    c = math.atan2(M[1, 0], M[0, 0])
    return [round(math.degrees(a), 1), round(math.degrees(b), 1), round(math.degrees(c), 1)]


def build():
    m, _ = s.archer("b")
    # 旧の弓 (肘に付いていた) と旧の骨 (bow) を外す
    m.boxes = [bx for bx in m.boxes if bx["bone"] != "bow"]
    m.bones = [bn for bn in m.bones if bn["name"] != "bow"]
    # 腕を肘で分ける (肘より下が forearm)
    for bx in m.boxes:
        if bx["bone"] in ("arm_l", "arm_r") and bx["b"][1] < 16.5:
            bx["bone"] = "forearm_" + bx["bone"][-1]
    m.bone("forearm_l", tuple(EL_L), "arm_l")
    m.bone("forearm_r", tuple(EL_R), "arm_r")
    # 弓: 左の手 (forearm_l) の子。握りの中ほどが pivot
    m.bone("bow", tuple(GRIP), "forearm_l")
    m.bone("string_up", tuple(TOP), "bow")
    m.bone("string_lo", tuple(BOT), "bow")
    BN, DK = "bone", "bone_dirty"
    for k in range(28):                       # 弓の弧: y 4.5 .. 18.5
        y = 4.5 + 0.5 * k
        t = (y + 0.25 - GRIP[1]) / 7.0
        c = 3.5 + 2.5 * (1 - t * t)
        x0 = max(3.5, round(c * 2) / 2 - 0.5)  # 端は腕の外側の面に接する
        m.box("bow", x0, y, -0.5, x0 + 0.5, y + 0.5, 0.5, DK)
        m.box("bow", x0, y, -1.0, x0 + 0.5, y + 0.5, -0.5, BN)  # 背の層
    m.box("bow", 3.5, 11.0, -0.5, 5.5, 12.0, 0.5, DK)          # 握り (手に接する)
    m.box("string_up", 3.0, 11.5, 0.5, 3.5, 18.5, 1.0, "trim")  # 弦の上半分
    m.box("string_lo", 3.0, 4.5, 0.5, 3.5, 11.5, 1.0, "trim")   # 弦の下半分
    return m


def nock_world(psi, Ra):
    """弦を psi 度 (2本とも) 引いたときの、弦の中ほど (ノック) の位置。Ra は左腕の回転。"""
    up = TOP + R(0, 0, -psi) @ (REST_NOCK - TOP)
    lo = BOT + R(0, 0, psi) @ (REST_NOCK - BOT)
    p = (up + lo) / 2
    return SH_L + Ra @ (BOW_LOCAL @ (p - GRIP) + GRIP - SH_L)


BOW_LOCAL = np.eye(3)  # 弓の骨だけの回転 (左腕の先に対して)


def solve_right_arm(N, E0):
    """右腕 (arm_r, forearm_r) の角度。手の先が N に来て、ひじが E0 の近くに来るように探す。"""
    def hand_elbow(v):
        Ra, Rf = R(*v[:3]), R(*v[3:])
        E = SH_R + Ra @ (EL_R - SH_R)
        H = SH_R + Ra @ ((EL_R - SH_R) + Rf @ np.array([0.0, -5.0, 0.0]))
        return H, E

    def cost(v):
        H, E = hand_elbow(v)
        return float(np.sum((H - N) ** 2) + 0.05 * np.sum((E - E0) ** 2))

    rng = np.random.default_rng(3)
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
    H, E = hand_elbow(best_v)
    return [round(float(a), 1) for a in best_v[:3]], [round(float(a), 1) for a in best_v[3:]], H, best_c


def poses():
    global BOW_LOCAL
    # 左腕: 肩から真っすぐ前へ。握りが顔の正面に近くなるように、左右の向き (yaw) を選ぶ
    best = None
    for phi in np.arange(-60.0, 0.5, 0.5):
        Ra = R(-90, phi, 0)
        G = SH_L + Ra @ (GRIP - SH_L)
        err = abs(G[0] - 0.5)
        if best is None or err < best[0]:
            best = (err, float(phi), Ra, G)
    _, phi, Ra_l, G = best
    # 弓の向き: 弓の腹 (+x) を前 (+z)、弓は垂直 (y のまま)。左腕の回転を打ち消す分を弓に持たせる
    W = R(*BELLY_TO_FRONT)
    B = np.linalg.inv(Ra_l) @ W
    bow_rot = euler(B)
    BOW_LOCAL = R(*bow_rot)
    # 弦を引く量: ノックの奥行きが口の前 (NOCK_Z) になるように
    psis = np.arange(0.0, 40.0, 0.25)
    psi = float(min(psis, key=lambda p: abs(nock_world(p, Ra_l)[2] - NOCK_Z)))
    N = nock_world(psi, Ra_l)
    ar, fr, H, err = solve_right_arm(N, np.array([-6.0, 19.0, 2.5]))
    # 放つ直後: 弦は戻り、右手は少し後ろへ抜ける
    Nrel = N + np.array([0.0, -0.3, -0.8])
    ar2, fr2, H2, err2 = solve_right_arm(Nrel, np.array([-6.0, 19.0, 2.5]))
    left = [-90.0, phi, 0.0]
    aim = {"arm_l": left, "bow": bow_rot, "string_up": [0.0, 0.0, -psi], "string_lo": [0.0, 0.0, psi],
           "arm_r": ar, "forearm_r": fr}
    release = {"arm_l": left, "bow": bow_rot, "string_up": [0.0, 0.0, 0.0], "string_lo": [0.0, 0.0, 0.0],
               "arm_r": ar2, "forearm_r": fr2}
    info = dict(phi=phi, grip=[round(float(v), 2) for v in G], psi=psi, nock=[round(float(v), 2) for v in N],
                hand=[round(float(v), 2) for v in H], hand_err=round(err, 3), hand_rel=[round(float(v), 2) for v in H2])
    return aim, release, info


def shoot_anim(aim, release):
    t_hold = 0.45
    shoot = {}
    rest_bone = ["arm_l", "forearm_l", "bow", "string_up", "string_lo", "arm_r", "forearm_r"]
    # 時刻ごとの姿勢 (0 は休み)。0.28 で構え、0.50 で放つ、0.95 で休みへ戻る
    seq = {
        0.00: {},
        0.28: aim,
        t_hold: aim,
        0.50: release,
        0.66: release,
        0.95: {},
    }
    for bn in rest_bone:
        keys = {}
        for tk, pose in seq.items():
            if bn in pose:
                keys[f"{tk:.2f}"] = [round(float(a), 2) for a in pose[bn]]
            else:
                keys[f"{tk:.2f}"] = [0.0, 0.0, 0.0]
        shoot[bn] = {"rotation": keys}
    return {"animation_length": 1.0, "bones": shoot}


def anim_file(aim, release):
    idle = {"loop": True, "animation_length": 3.0, "bones": {
        "spine": {"position": ["0", "math.sin(query.anim_time * 120) * 0.3", "0"],
                  "rotation": ["math.sin(query.anim_time * 120) * 1.0", "0", "0"]},
        "skull": {"rotation": ["math.sin(query.anim_time * 120 + 90) * 1.0",
                               "math.sin(query.anim_time * 120 + 60) * 3.0", "0"]},
        "arm_l": {"rotation": ["math.sin(query.anim_time * 120 + 40) * 1.0", "0", "0"]},
        "arm_r": {"rotation": ["math.sin(query.anim_time * 120 - 40) * 2.5", "0", "0"]},
        "forearm_r": {"rotation": ["math.sin(query.anim_time * 120) * 2.0", "0", "0"]},
    }}
    walk = {"loop": True, "animation_length": 1.0, "bones": {
        "leg_l": {"rotation": ["math.sin(query.anim_time * 360) * 32", "0", "0"]},
        "leg_r": {"rotation": ["math.sin(query.anim_time * 360) * -32", "0", "0"]},
        "arm_l": {"rotation": ["math.sin(query.anim_time * 360) * -4", "0", "0"]},
        "arm_r": {"rotation": ["math.sin(query.anim_time * 360) * 22", "0", "0"]},
        "forearm_r": {"rotation": ["-8 + math.sin(query.anim_time * 360 + 90) * 8", "0", "0"]},
        "spine": {"position": ["0", "math.abs(math.sin(query.anim_time * 360)) * 0.8", "0"],
                  "rotation": ["4", "math.sin(query.anim_time * 360) * 3", "0"]},
        "skull": {"rotation": ["0", "math.sin(query.anim_time * 360 + 180) * 3", "0"]},
    }}
    return {"format_version": "1.8.0", "animations": {
        "animation.rib_archer.idle": idle,
        "animation.rib_archer.walk": walk,
        "animation.rib_archer.shoot": shoot_anim(aim, release),
    }}


def render_pose(geo, tex, gl, pose, yaw, pitch):
    faces = vx.load_geo(geo, tex, gl, pose=pose)
    return vx.render(faces, yaw=yaw, pitch=pitch, size=(360, 360))


def main():
    m = build()
    geo, tex, gl = m.finish()
    overlaps = vx.check_overlaps(geo)
    print("overlaps:", overlaps if overlaps else "none", "boxes:", len(m.boxes), "tex H:", tex.shape[0])
    aim, release, info = poses()
    print("solve:", info)

    # 素材を src に入れる
    geo_path = ASSET / "geo" / "entity" / "rib_archer.geo.json"
    geo_path.write_text(json.dumps(geo, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    Image.fromarray(tex, "RGBA").save(ASSET / "textures" / "entity" / "rib_archer.png")
    Image.fromarray(gl, "RGBA").save(ASSET / "textures" / "entity" / "rib_archer_glowmask.png")
    anim = anim_file(aim, release)
    (ASSET / "animations" / "entity" / "rib_archer.animation.json").write_text(
        json.dumps(anim, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    # 比較シート: 列 = 前回 / 直した後 (正面・横)、行 = 休み / 構え / 射った直後
    old_geo = json.loads((OLD / "geo.json").read_text(encoding="utf-8"))
    old_tex = np.array(Image.open(OLD / "texture.png").convert("RGBA"))
    old_gl = np.array(Image.open(OLD / "glowmask.png").convert("RGBA"))
    old_aim = {"arm_l": (-90, 0, 0), "bow": (90, 0, 0), "arm_r": (-90, -20, 0)}
    rows = [({}, {}), (old_aim, aim), (old_aim, release)]
    new_rows = [{}, aim, release]
    cols = []
    for view_name, yaw, pitch in (("正面", 25, 18), ("横", 80, 10)):
        cols.append((f"前回 B ({view_name})", [render_pose(old_geo, old_tex, old_gl, p_old, yaw, pitch)
                                             for p_old, _ in rows]))
        cols.append((f"直した後 ({view_name})", [render_pose(geo, tex, gl, p_new, yaw, pitch)
                                              for p_new in new_rows]))
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = vx.sheet("rib_archer 第3回 (弓を左の手に付け直す)", cols,
                     "行: 上から 休み / 構え / 射った直後。列: 前回の B と直した後、正面と横")
    sheet.save(OUT / "rib_archer.png")
    print("wrote", OUT / "rib_archer.png")


if __name__ == "__main__":
    main()
