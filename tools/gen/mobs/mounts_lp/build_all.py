#!/usr/bin/env python3
"""乗り物の低ポリモデルを作って入れる (docs/design/38 の 5)。

    python -I tools/gen/mobs/mounts_lp/build_all.py [id ...] [--no-install] [--no-sheet]

一体ごとに (順に。重い作業は一つずつ):
  1. <id>.py の build() で形と骨を作り、鞍を体に沿わせて置く (席の上面の高さを測る)
  2. 乗る人の脚の向き (legPitch / legSpread) を、脚が体に入らない向きから選び、足先にあぶみを吊る
  3. アトラスを描き、メッシュ・アトラス・光る画素を build/mount_lp/<id>/ へ書く
  4. 箱の geo (骨の定義) geo/entity/mount_<id>.geo.json と、低ポリ (install_lowpoly.py)、アニメ animations/entity/mount_<id>.animation.json を入れる
  5. 比べシート docs/review/mounts_v2/<id>_sheet.png (前・斜め・横・後ろ、乗った人、歩き・駆け・跳びの帯)
最後に MountLook.java の当たりの大きさ・席の高さ・脚の向きを書き換える。
"""
from __future__ import annotations

import importlib
import json
import re
import subprocess
import sys
import time
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import mlp  # noqa: E402
from mlp import *  # noqa: E402,F401,F403

ORDER = ["horn_beast", "ash_deer", "great_lizard", "whale_calf", "shell_tortoise", "storm_chick", "ember_fox"]
LOOK_JAVA = ROOT / "src/main/java/jp/revati/skyabyss/mount/MountLook.java"
PARAMS = WORK / "look_params.json"


def build_one(mid, install=True, sheet=True):
    t0 = time.time()
    mod = importlib.import_module(mid)
    m = mod.build()
    kw = dict(m.saddle_kw)
    seat_top = saddle(m, **kw)
    seat = round((seat_top - 1.0) / 16.0, 3)
    game_top = seat * 16.0
    pitch, spread, n_in = choose_leg_pose(m, game_top, m.prefer_pose)
    foot = foot_point(game_top, pitch, spread, 1)
    if pitch > -62:
        # 脚を下ろして跨ぐ姿のときだけ、足先にあぶみを吊る (前へ伸ばす姿では足が届かない)
        add_stirrups(m, foot, kw.get("cy", 20.0), 0.0, kw.get("seat_half", 34.0), kw.get("blanket", 1.0), kw.get("mats"))
    core = [f for f in m.F if f.part in m.core]
    cv = np.vstack([f.verts for f in core])
    width = float(np.clip(2 * np.abs(cv[:, 0]).max() / 16.0, 1.0, 1.6))
    height = float(np.clip(seat + 0.15, 1.2, 2.4))
    size, atlas, glow = paint_mount(m, m.pal, m.detail, m.hint)
    out = WORK / mid
    data, has_glow = export(m, size, atlas, glow, out, {"seat_y": seat_top, "rider_seat_blocks": seat,
                                                       "rider_leg_pitch": pitch, "rider_leg_spread": spread})
    name = "mount_" + mid
    tris = data["triangle_count"]
    info = dict(id=mid, tris=tris, atlas=size, bones=len(m.bones), seat_top=round(seat_top, 2), seat=seat, pitch=pitch, spread=spread,
                leg_inside=n_in, width=round(width, 2), height=round(height, 2), glow=int(glow.sum()))
    print(mid, info, f"{time.time() - t0:.1f}s", flush=True)
    per = {}
    for f in m.F:
        per[f.part] = per.get(f.part, 0) + len(f.verts) - 2
    top = sorted(per.items(), key=lambda kv: -kv[1])[:14]
    print("  tris by part:", ", ".join(f"{k} {v}" for k, v in top))
    if install:
        cube_geo(m, ASSETS / "geo/entity" / (name + ".geo.json"))
        avg = atlas[..., :3][atlas[..., 3] > 0].mean(0).astype(np.uint8)
        Image.new("RGBA", (16, 16), tuple(int(x) for x in avg) + (255,)).save(ASSETS / "textures/entity" / (name + ".png"))
        cmd = [sys.executable, str(ROOT / "tools/install/install_lowpoly.py"), name, "--mesh", str(out / f"{name}_mesh.json"),
               "--atlas", str(out / f"{name}_atlas.png")]
        cmd += ["--glow", str(out / f"{name}_glowmask.png")] if has_glow else ["--glow", str(out / "none.png")]
        subprocess.check_call(cmd, cwd=str(ROOT))
        write_anims(m.gaits, ASSETS / "animations/entity" / (name + ".animation.json"))
    if sheet:
        make_sheet(m, atlas, glow, seat, pitch, spread, info)
    return info


# ====================================================================== 比べシート

def make_sheet(m, atlas, glow, seat, pitch, spread, info):
    REVIEW.mkdir(parents=True, exist_ok=True)
    F = voxel.font
    cen, sc = np.array(m.view["center"], float), m.view["scale"]
    PW, PH = 330, 300
    rider = rider_faces(seat * 16.0, pitch, spread)
    rider_atlas = atlas.copy()
    # 人の箱はアトラスの外の色で塗る (材質の色を直に)
    rows = []
    rows.append(("立ち姿 (前 / 斜め前 / 横 / 斜め後ろ)", [(m.F, 180, 8), (m.F, 215, 14), (m.F, 270, 6), (m.F, 330, 14)]))
    rows.append((f"乗った人 (席 {seat:.2f} ブロック、脚 {pitch}° 前・{spread}° 開き)", [("R", 215, 12), ("R", 270, 4), ("R", 180, 6), ("R", 135, 18)]))
    gaits = {g.name: g for g in m.gaits}
    strips = []
    w = gaits["walk"]
    strips.append(("歩き (一歩の 4 こま)", [w.pose_at(0, w.stride * i / 4) for i in range(4)]))
    r = gaits["run"]
    strips.append(("駆け (一歩の 4 こま)", [r.pose_at(0, r.stride * i / 4) for i in range(4)]))
    j = gaits["jump"]
    strips.append(("跳び (踏み切り → 空中)", [j.pose_at(t, 0) for t in (0.0, 0.12, 0.32, 0.7)]))
    strips.insert(0, ("寄り (鞍 / 頭)", "ZOOM"))
    i_ = gaits["idle"]
    strips.append(("待機 (息・尾・飾りの動き)", [i_.pose_at(i_.period * k / 4, 0) for k in range(4)]))
    W_ = 20 * 2 + 4 * PW + 3 * 12
    H_ = 90 + (len(rows) + len(strips)) * (PH + 40) + 20
    cv = Image.new("RGB", (W_, H_), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((20, 16), f"乗り物「{m.title}」 低ポリ ({info['tris']} 三角形・骨 {info['bones']}・アトラス {info['atlas']})", font=F(28), fill=(236, 232, 218))
    d.text((20, 56), "左の黄色い目盛りが人 (1.8 ブロック)。動きの帯は横から見た姿 (ゲームと同じ式で動かした)。", font=F(17), fill=(176, 176, 192))
    y = 90
    for head, cells in rows:
        d.text((20, y), head, font=F(19), fill=(150, 232, 255))
        y += 30
        for i, (fs, yaw, pt) in enumerate(cells):
            x = 20 + i * (PW + 12)
            if fs == "R":
                im = view_with_rider(m, rider, atlas, glow, yaw, pt, (PW, PH), cen + np.array([0, 4, 0]), sc * 0.92)
            else:
                im = view(fs, atlas, glow, yaw, pt, (PW, PH), cen, sc)
            cv.paste(im, (x, y))
        y += PH + 10
    for head, poses in strips:
        d.text((20, y), head, font=F(19), fill=(150, 232, 255))
        y += 30
        if poses == "ZOOM":
            zc = m.view.get("zoom", [((0, 34, 0), 215, 22, 2.4), ((0, 26, -20), 200, 10, 2.4), ((0, 30, 0), 300, 30, 2.4), ((0, 12, 0), 230, 4, 2.4)])
            for i, (c_, yaw, pt, k) in enumerate(zc):
                x = 20 + i * (PW + 12)
                cv.paste(view(m.F, atlas, glow, yaw, pt, (PW, PH), np.array(c_, float), sc * k, ground=False), (x, y))
            y += PH + 10
            continue
        for i, pose in enumerate(poses):
            x = 20 + i * (PW + 12)
            im = view(posed(m, pose), atlas, glow, 270, 6, (PW, PH), cen, sc)
            cv.paste(im, (x, y))
        y += PH + 10
    cv.save(REVIEW / f"{m.id}_sheet.png")


def view_with_rider(m, rider, atlas, glow, yaw, pitch, size, center, scale):
    """乗り物と人を一緒に描く (人は平らな色)。"""
    base = view(m.F, atlas, glow, yaw, pitch, size, center, scale)
    # 人は単色のアトラスで別に描き、z を比べられないので「人 → 乗り物」の順に重ねる代わりに、両方の面を一つの絵に入れる
    cols = {"plc": (70, 92, 150), "pls": (206, 160, 124)}
    S = atlas.shape[0]
    big = np.zeros((S + 8, S + 8, 4), np.uint8)
    big[:S, :S] = atlas
    big[S:S + 2, :] = cols["plc"] + (255,)
    big[S + 2:S + 4, :] = cols["pls"] + (255,)
    gl = np.zeros(big.shape[:2], bool)
    gl[:S, :S] = glow
    fs = []
    for f in rider:
        g = copy.copy(f)
        row = S if f.mat == "plc" else S + 2
        g.uv = np.array([[1, row + 0.5]] * len(f.verts), float)
        fs.append(g)
    img = render(m.F + fs, big, gl, yaw, pitch, size, center, scale)
    bg = bg_panel(*size)
    bg.paste(img, (0, 0), img)
    return bg


# ====================================================================== MountLook.java

def update_look_java(params):
    s = LOOK_JAVA.read_text(encoding="utf-8")
    for p in params.values():
        enum = p["id"].upper()
        pat = re.compile(r"(\n    " + enum + r"\(\"" + p["id"] + r"\", )(\d+), (\d+), [^\n]*?\)([,;])")
        mm = pat.search(s)
        assert mm, enum
        line = f'{mm.group(1)}{mm.group(2)}, {mm.group(3)}, {p["width"]:.2f}f, {p["height"]:.2f}f, {p["seat"]:.3f}, {p["pitch"]}f, {p["spread"]}f){mm.group(4)}'
        s = s[:mm.start()] + line + s[mm.end():]
    LOOK_JAVA.write_text(s, encoding="utf-8")


def main(argv):
    ids = [a for a in argv if not a.startswith("--")] or ORDER
    install = "--no-install" not in argv
    sheet = "--no-sheet" not in argv
    WORK.mkdir(parents=True, exist_ok=True)
    params = json.loads(PARAMS.read_text(encoding="utf-8")) if PARAMS.exists() else {}
    for mid in ids:
        params[mid] = build_one(mid, install, sheet)
        PARAMS.write_text(json.dumps(params, ensure_ascii=False, indent=1), encoding="utf-8")
    if install and all(k in params for k in ORDER):
        update_look_java(params)
        print("MountLook.java updated")


if __name__ == "__main__":
    main(sys.argv[1:])
