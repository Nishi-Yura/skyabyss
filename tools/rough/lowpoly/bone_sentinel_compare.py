"""骨の番兵: 今のモデルと低ポリ案を並べた比較画像を作る。

実行: python tools/rough/lowpoly/bone_sentinel_compare.py
出力: docs/review/lowpoly_roughs/bone_sentinel_lowpoly.png (+ メッシュ JSON・アトラス。bone_sentinel_lp.py が書く)
"""
import copy
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from bone_sentinel_lp import *  # noqa
from bone_sentinel_model import SOCKET  # noqa

YAW_FRONT, YAW_BACK, PITCH = 212, 32, 13
PW, PH = 380, 640   # 1 パネルの大きさ

# アニメーションの key pose (animations/entity/bone_sentinel.animation.json から)
POSES = {
    "attack_windup": {"right_arm": (-80, 0, 8), "right_forearm": (-25, 0, 0), "body": (-3, 15, 0)},
    "attack_strike": {"right_arm": (-30, 0, -4), "right_forearm": (-12, 0, 0), "body": (8, -20, 0)},
    "block": {"left_arm": (-75, 0, 0), "left_forearm": (0, 15, 90), "socket_off": (90, 0, 0)},
    "walk": {"leg_l": (32, 0, 0), "leg_r": (-32, 0, 0), "left_arm": (-22, 0, -3), "right_arm": (22, 0, 3),
             "left_forearm": (-8, 0, 0), "right_forearm": (-8, 0, 0), "body": (4, 3, 0)},
}


def rot_zyx_geo(rx, ry, rz):
    """GeckoLib の骨の回転 (度)。x, y, z とも符号を反転した Z→Y→X の順 (今のアニメーションの向きに合わせる)。"""
    return rot_z(-rz) @ rot_y(-ry) @ rot_x(-rx)


def pose_point(p, bone, pose, bones=BONES):
    """休めの姿勢の点 p (骨 bone の局所の点) を、pose のときのモデル空間へ。"""
    while bone is not None:
        parent, pivot = bones[bone]
        pv = np.array(pivot, float)
        rx, ry, rz = pose.get(bone, (0, 0, 0))
        p = rot_zyx_geo(rx, ry, rz) @ (p - pv) + pv
        bone = parent
    return p


def pose_faces(faces, pose, shield=None, ss=0):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([pose_point(p, f.bone, pose) for p in f.verts])
        # 法線: 骨ごとの回転を面の中心の差分で求める代わりに、頂点から作り直す
        k = len(g.verts)
        n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % k] - g.verts[1]) if k == 4 else \
            np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
        n = nrm(n)
        if np.dot(n, f.normal) < 0:
            n = -n
        g.normal = n
        out.append(g)
    return out


def shield_in_pose(shield, pose):
    out = []
    for f in shield:
        g = copy.copy(f)
        g.verts = np.array([pose_point(p + SOCKET, "socket_off", pose) for p in f.verts])
        k = len(g.verts)
        n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % k] - g.verts[1]) if k == 4 else \
            np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
        n = nrm(n)
        if np.dot(n, f.normal) < 0:
            n = -n
        g.normal = n
        out.append(g)
    return out


def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_model():
    geo = A / "geo/entity/bone_sentinel.geo.json"
    faces = voxel.load_geo(geo, A / "textures/entity/bone_sentinel.png",
                           A / "textures/entity/bone_sentinel_glowmask.png")
    n_old = sum(len(b.get("cubes", [])) for b in json.loads(geo.read_text(encoding="utf-8"))
                ["minecraft:geometry"][0]["bones"])
    sg = A / "geo/gear/bone_sentinel_shield.geo.json"
    sh = voxel.load_geo(sg, A / "textures/gear/bone_sentinel_shield.png")
    n_sh = sum(len(b.get("cubes", [])) for b in json.loads(sg.read_text(encoding="utf-8"))
               ["minecraft:geometry"][0]["bones"])
    for f in sh:
        f.origin = f.origin + np.array([7.5, 9.0, 0.0])
    return faces + sh, n_old, n_sh


def combined_atlas(r):
    """本体と盾のアトラスを 1 枚の正方形にまとめる (描画用。盾は本体の下)。"""
    S = r["size"]
    T = S + r["ssize"]
    S2 = max(S, r["ssize"])
    T = S + r["ssize"]
    side = max(T, S2)
    big = np.zeros((side, side, 4), np.uint8)
    big[:S, :S] = r["atlas"]
    big[S:S + r["ssize"], :r["ssize"]] = r["satlas"]
    glow = np.zeros((side, side), bool)
    glow[:S, :S] = r["glow"]
    return big, glow, S


def render_pose(r, big, glow, yoff, pose, yaw, pitch, size, center, scale):
    faces = pose_faces(r["body"], pose)
    sh = shield_in_pose(r["shield"], pose)
    for f in sh:
        f.uv = f.uv + np.array([0, yoff])
    return render(faces + sh, big, glow, yaw, pitch, size, center, scale)


def main_compare():
    r = main()
    size, tris, data = r["size"], r["data"]["triangle_count"], r["data"]
    big, bigglow, yoff = combined_atlas(r)
    canvas = Image.new("RGB", (1600, 1840), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    F = voxel.font
    CY, GR = (150, 232, 255), (160, 160, 176)
    d.text((28, 16), "骨の番兵 (bone_sentinel) — 低ポリ非立方体 + ドット絵テクスチャ (ゲームに入れる版)", font=F(26), fill=(236, 232, 218))
    d.text((28, 52), "骨の名前・階層・pivot は今の geo と同じ (今のアニメーションがそのまま動く)。盾は別メッシュで socket_off に付く。"
                     "テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。", font=F(15), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    top = 90
    ofaces, n_old, n_sh = old_model()
    for margin in (0.06, -0.12, -0.2):
        old = voxel.render(ofaces, yaw=YAW_FRONT, pitch=PITCH, size=(PW, PH), margin=margin)
        a = np.array(old).astype(int)
        mask = (np.abs(a - a[5, 5]).sum(-1) > 90)
        rows = np.where(mask.sum(1) > 2)[0]
        if rows.max() - rows.min() > 520:
            break
    sc = 15.0
    cen = (1.6, 17.5, -1.0)
    panels = [("今 (箱 %d 個 + 盾 %d 個)" % (n_old, n_sh), None), ("新 前 3/4", YAW_FRONT), ("新 後ろ 3/4", YAW_BACK)]
    for i, (label, yaw) in enumerate(panels):
        x = 20 + i * (PW + 14)
        bg = panel_bg(PW, PH)
        if yaw is None:
            bg.paste(old, (0, 0))
        else:
            im = render_pose(r, big, bigglow, yoff, {}, yaw, PITCH, (PW, PH), cen, sc)
            bg.paste(im, (0, 0), im)
        canvas.paste(bg, (x, top))
        frame(x, top, PW, PH)
        d.text((x, top + PH + 8), label, font=F(19), fill=(236, 232, 218) if yaw is None else CY)
    # 頭のアップ (右)
    hx, hw, hh = 20 + 3 * (PW + 14), 340, 440
    bg = panel_bg(hw, hh)
    im = render_pose(r, big, bigglow, yoff, {}, 200, 8, (hw, hh), (0, 29.2, 0), 33)
    bg.paste(im, (0, 0), im)
    canvas.paste(bg, (hx, top))
    frame(hx, top, hw, hh)
    d.text((hx, top + hh + 8), "頭のアップ (前)", font=F(19), fill=CY)
    hh2 = 170
    bg = panel_bg(hw, hh2)
    im = render_pose(r, big, bigglow, yoff, {}, 35, 8, (hw, hh2), (0, 30.0, 0), 22)
    bg.paste(im, (0, 0), im)
    canvas.paste(bg, (hx, top + hh + 44))
    frame(hx, top + hh + 44, hw, hh2)
    d.text((hx, top + hh + 44 + hh2 + 6), "頭 (後ろ)", font=F(16), fill=CY)
    # --- 2 段目: ポーズ
    top2 = top + PH + 54
    ph2, pw2 = 600, 322
    labels = {"attack_windup": "攻撃 振りかぶり (0.22 s)", "attack_strike": "攻撃 振り下ろし (0.38 s)",
              "block": "盾を構える (block)", "walk": "歩き (最大の一歩)"}
    yaws = {"attack_windup": 150, "attack_strike": 150, "block": 205, "walk": 245}
    for i, key in enumerate(("attack_windup", "attack_strike", "block", "walk")):
        x = 20 + i * (pw2 + 12)
        bg = panel_bg(pw2, ph2)
        im = render_pose(r, big, bigglow, yoff, POSES[key], yaws[key], PITCH, (pw2, ph2), (0, 17.5, -1.0), 15.0)
        bg.paste(im, (0, 0), im)
        canvas.paste(bg, (x, top2))
        frame(x, top2, pw2, ph2)
        d.text((x, top2 + ph2 + 8), labels[key], font=F(18), fill=CY)
    # 右: 盾 (外側から)
    x = 20 + 4 * (pw2 + 12)
    sw, sh_ = 200, 300
    bg = panel_bg(sw, sh_)
    shf = copy.deepcopy(r["shield"])
    for f in shf:
        f.uv = f.uv + np.array([0, yoff])
    im = render(shf, big, bigglow, 270, 8, (sw, sh_), (0.5, -0.5, 0), 15.0)
    bg.paste(im, (0, 0), im)
    canvas.paste(bg, (x, top2))
    frame(x, top2, sw, sh_)
    d.text((x, top2 + sh_ + 6), "盾 (別メッシュ・外側)", font=F(16), fill=CY)
    # --- 3 段目: アトラスと数字
    top3 = top2 + ph2 + 50
    at = Image.fromarray(r["atlas"], "RGBA")
    bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
    bgc.alpha_composite(at)
    asz = size * 4
    canvas.paste(bgc.convert("RGB").resize((asz, asz), Image.NEAREST), (20, top3))
    frame(20, top3, asz, asz)
    d.text((20, top3 + asz + 6), f"本体のアトラス {size}×{size} (4 倍表示)", font=F(16), fill=CY)
    sat = Image.fromarray(r["satlas"], "RGBA")
    bgc = Image.new("RGBA", sat.size, (40, 40, 52, 255))
    bgc.alpha_composite(sat)
    ssz = r["ssize"] * 4
    canvas.paste(bgc.convert("RGB").resize((ssz, ssz), Image.NEAREST), (20 + asz + 24, top3))
    frame(20 + asz + 24, top3, ssz, ssz)
    d.text((20 + asz + 24, top3 + ssz + 6), f"盾のアトラス {r['ssize']}×{r['ssize']} (4 倍表示)", font=F(16), fill=CY)
    per = data["bones"]
    pt = lambda k: per[k]["triangle_count"]
    tx = 20 + asz + 24 + ssz + 30
    ty = top3
    lines = [
        (f"新: 本体 {tris} 三角形 (盾 {r['sdata']['triangle_count']} 三角形は別メッシュ)", CY, 19),
        (f"アトラス {size}×{size} ・ 1 単位 (1/16 ブロック) = 2 テクセル (1 ブロック 32 テクセル)", GR, 16),
        (f"三角形: 頭 {pt('head')} (顎・歯を含む) / 胴 {pt('body')} / 左腕 {pt('left_arm')}+{pt('left_forearm')} / "
         f"右腕 {pt('right_arm')}+{pt('right_forearm')} / 脚 {pt('leg_l')}×2", GR, 16),
        ("骨: body > head / left_arm > left_forearm > socket_off(空) / right_arm > right_forearm / leg_l / leg_r", GR, 15),
        ("パレット: 象牙 6 階調 / 苔 4 / 石 5 / 闇 3 / 瞳 2。縁の欠け・凹みの暗がり・面に沿ったひびで手描き風", GR, 16),
    ]
    for t, col, sz in lines:
        d.text((tx, ty), t, font=F(sz), fill=col)
        ty += sz + 14
    out = OUT / "bone_sentinel_lowpoly.png"
    canvas = canvas.crop((0, 0, 1600, min(canvas.size[1], max(top3 + asz + 40, top3 + 420))))
    canvas.save(out)
    print("saved", out, "tris", tris)


if __name__ == "__main__":
    main_compare()
