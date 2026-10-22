"""心臓の番人 (HeartWarden, docs/design/36_abyss.md §6) の低ポリ ラフ 3 案。姿は光の中でだけ見える。
A 灯の番 (背の高い外套。胸の空洞を肋の杭が囲み、鼓動の光が見える)
B 肋の獣 (積んだ肋の胴と、鎖の腕の先の錨の爪。背骨が一本)
C 半分割れの形 (人の比率が狂った顔のない形。左半分は塩、右半分は虚の黒。前の縫い目だけが光る)

高さ 51 単位 (3.2 ブロック)、幅 22 単位前後 (1.4 ブロック)。1 単位 = 1/16 ブロック。前 = -Z。
出力: docs/review/warden_roughs/ (メッシュ・アトラス・光る画素のマスク・各案の絵・manifest)
暗闇の絵は「左前の灯り一つ」(半径 20 単位の 2 乗の落ち) で、灯りの外は 4% の明るさにして描く (近似)。
生成: python tools/rough/lowpoly/abyss/warden.py  (numpy / PIL / scipy が要る)
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import ck5 as K  # noqa: E402  (ck4/ck/lpkit の部品と、深淵の色・模様を読み込む)
from ck5 import *  # noqa: E402,F401
import ck as _ck  # noqa: E402
import ck_ab as AB  # noqa: E402

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/warden_roughs"
HINT = 160
PW, PH = 300, 320          # 1 枚の絵の大きさ
SC = 5.4                   # 1 単位あたりの画素
CEN = (0.0, 25.5, 0.0)     # 絵の中心 (高さの中ほど)
LANT = np.array([-16.0, 30.0, -14.0])   # 暗闇の絵の灯り (左前、腰の高さ)
LANT_R = 22.0


def ringn(y, rx, rz, cz=0.0, cx=0.0, n=10):
    return [np.array([cx + rx * math.cos(2 * math.pi * i / n), y, cz + rz * math.sin(2 * math.pi * i / n)]) for i in range(n)]


def side_mats(faces, left_mat, right_mat):
    """x < 0 の面を left_mat、x > 0 の面を right_mat にする (中央の面は左)。"""
    for f in faces:
        cx = float(f.verts[:, 0].mean())
        f.mat = left_mat if cx <= 0.0 else right_mat
    return faces


def cone_(bone, mat, base, direction, length, r, sides=6, rings=3, bend=(0, 0, 0)):
    return AB.cone(bone, mat, base, direction, length, r, sides=sides, rings=rings, bend=bend)


# ============================================================ A 灯の番
BONES_A = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0)), "head": ("body", (0, 44, 0)),
           "arm_l": ("body", (-8.6, 40, 0)), "arm_r": ("body", (8.6, 40, 0))}


def build_a(k):
    F = []
    robe = [ringn(0.0, 10.5, 7.0), ringn(8, 10.0, 7.0), ringn(18, 8.6, 6.4), ringn(28, 7.6, 5.8),
            ringn(36, 8.4, 5.8), ringn(41.5, 9.0, 5.4), ringn(44.0, 5.2, 4.0)]
    F += part("robe", loft("body", "void", robe, caps=(True, True)))
    hood = [ringn(43.5, 5.0, 3.8), ringn(47.5, 4.2, 3.2), ringn(50.5, 2.2, 1.8)]
    F += part("hood", loft("head", "void", hood, caps=(True, True)))
    # 胸の空洞: 光の核を肋の杭 (八本) が囲む。核は外套に半分沈む
    F += part("heart", ellipsoid("body", "teal", (0, 32, -5.4), (2.0, 2.6, 1.3), segs=8, rings=4))
    for i in range(8):
        a = 2 * math.pi * i / 8
        d = np.array([math.cos(a), math.sin(a) * 1.2, -0.45])
        base = np.array([math.cos(a) * 2.4, 32 + math.sin(a) * 3.0, -5.0])
        F += part("rib", cone_("body", "bone", base, d, 4.2, 0.85, sides=6, rings=3, bend=(0, 0, 0)))
    # 袖と爪 (左右)
    for s in (-1, 1):
        bone = "arm_l" if s < 0 else "arm_r"
        sx = -8.6 * s
        sleeve = [np.array([sx, 40, 0.0]), np.array([sx * 1.22, 30, -1.4]), np.array([sx * 1.28, 20, -2.6])]
        F += part("sleeve", tube(bone, "void", sleeve, [2.6, 2.1, 1.6], sides=7, caps=(True, True)))
        F += part("shoulder", ellipsoid(bone, "void", (sx, 40, 0.0), (2.8, 2.8, 2.8), segs=6, rings=3))
        wrist = np.array([sx * 1.28, 19.0, -2.8])
        for j, dx in enumerate((-1.0, 0.0, 1.0)):
            d = np.array([dx * 0.35 * s, -1.0, -0.55])
            F += part("claw", cone_(bone, "bone", wrist + np.array([dx * 0.9, 0, 0]), d, 9.0, 0.8, sides=6, rings=3))
    return F, []


POSES_A = {"idle": {}, "claw": {"arm_l": (-60, 0, 0), "arm_r": (-60, 0, 0)}}


# ============================================================ B 肋の獣
BONES_B = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0)), "head": ("body", (0, 45, -1)),
           "arm_l": ("body", (-6.5, 41, 0)), "arm_r": ("body", (6.5, 41, 0)),
           "leg_l": ("body", (-3.5, 24, 0)), "leg_r": ("body", (3.5, 24, 0))}


def rib_path(y, rx, rz, cz):
    """肋一本: 背骨から横へ出て、前で胸を回り、もう一方の背骨へ戻る。"""
    arc = []
    for i in range(9):
        th = math.pi * i / 8
        arc.append(np.array([rx * math.cos(th), y - 1.2 * math.sin(th), cz - rz * math.sin(th)]))
    return [np.array([2.0, y, cz])] + [arc[0]] + arc[1:] + [np.array([-2.0, y, cz])]


def build_b(k):
    F = []
    cz = 2.5
    ys = [26.0 + 2.2 * i for i in range(8)]
    for y in ys:
        rx = 6.6 + 1.3 * math.sin(math.pi * (y - 24) / 20.0)
        p = rib_path(y, rx, 5.2, cz)
        F += part("rib", tube("body", "bone", p, [0.9] * len(p), sides=6, caps=(True, True)))
    spine = [np.array([0, 20.0, cz]), np.array([0, 30.0, cz]), np.array([0, 40.0, cz]), np.array([0, 46.0, cz])]
    F += part("spine", tube("body", "bone", spine, [1.9, 2.3, 2.3, 1.6], sides=7, caps=(True, True)))
    F += part("core", ellipsoid("body", "void", (0, 34, 0.0), (5.0, 8.6, 3.4), segs=10, rings=6))
    F += part("heart", ellipsoid("body", "teal", (0, 33, -2.6), (1.7, 2.3, 1.1), segs=8, rings=4))
    F += part("pelvis", ellipsoid("body", "void", (0, 24.5, 0.0), (6.0, 4.0, 5.0), segs=10, rings=4))
    neck = [np.array([0, 42.0, cz]), np.array([0, 46.0, -1.0]), np.array([0, 47.0, -3.0])]
    F += part("neck", tube("head", "void", neck, [1.9, 1.6, 1.4], sides=7, caps=(True, True)))
    F += part("skull", ellipsoid("head", "void", (0, 47.0, -5.5), (3.6, 3.2, 4.2), segs=10, rings=5))
    for s in (-1, 1):
        F += part("crown", cone_("head", "bone", np.array([2.4 * s, 49.0, -2.0]), np.array([0.8 * s, 0.9, -0.2]), 5.0, 1.1, sides=6, rings=3, bend=(0.6 * s, 0, 0)))
    # 腕: 鎖の連なり (繋ぎの輪は沈めて重ねる) と、先の錨の爪
    for s in (-1, 1):
        bone = "arm_l" if s < 0 else "arm_r"
        pts = [np.array([-7.0 * s, 41.0, 1.0]), np.array([-9.5 * s, 33.0, 0.6]), np.array([-11.0 * s, 24.0, -0.8]),
               np.array([-12.0 * s, 16.0, -2.4])]
        for i in range(7):
            t = i / 6.0
            c = pts[0] * (1 - t) ** 2 + 2 * (1 - t) * t * pts[1] + 2 * t * (1 - t) * pts[2] + t * t * pts[3]
            rot = rot_x(90.0) if i % 2 else None
            radii = (1.5, 2.5, 1.1) if i % 2 == 0 else (1.1, 1.6, 2.4)
            F += part("chain", ellipsoid(bone, "slate", tuple(c), radii, segs=6, rings=3, rot=rot))
        hand = pts[3]
        F += part("hook", cone_(bone, "bone", hand, np.array([0.0, -1.0, -0.5]), 7.0, 1.2, sides=6, rings=3, bend=(0, 0, -2.0)))
        for dd in (-1.0, 1.0):
            F += part("hook", cone_(bone, "bone", hand, np.array([dd * 0.9 * s, -0.5, 0.0]), 4.4, 0.9, sides=6, rings=3))
    # 脚: 膝が後ろに出る前かがみの脚
    for s in (-1, 1):
        bone = "leg_l" if s < 0 else "leg_r"
        leg = [np.array([3.5 * s, 25.0, 0.0]), np.array([4.5 * s, 14.0, -4.0]), np.array([3.0 * s, 4.0, 1.4]), np.array([3.0 * s, 1.6, -2.6])]
        F += part("leg", tube(bone, "void", leg, [2.6, 1.8, 1.2, 1.0], sides=7, caps=(True, True)))
        F += part("foot", ellipsoid(bone, "void", (3.0 * s, 1.6, -3.4), (2.0, 1.5, 3.0), segs=6, rings=3))
    return F, []


POSES_B = {"idle": {}, "claw": {"arm_l": (-50, 0, 0), "arm_r": (-50, 0, 0)}}


# ============================================================ C 半分割れの形
BONES_C = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0)), "head": ("body", (0, 44, 0)),
           "arm_l": ("body", (-5.0, 42, 0)), "arm_r": ("body", (5.0, 42, 0)),
           "leg_l": ("body", (-2.5, 24, 0)), "leg_r": ("body", (2.5, 24, 0))}


def build_c(k):
    F = []
    torso = [ringn(24, 4.6, 3.0), ringn(30, 4.0, 2.8), ringn(38, 5.2, 3.2), ringn(43, 3.8, 2.6), ringn(44.5, 1.8, 1.6)]
    F += part("torso", loft("body", "void", torso, caps=(True, True)))
    F += part("neck", tube("head", "void", [np.array([0, 43.0, 0]), np.array([0, 47.0, 0.4])], [1.5, 1.3], sides=7, caps=(True, True)))
    F += part("head", ellipsoid("head", "void", (0, 50.5, 0.2), (3.2, 4.6, 3.4), segs=10, rings=6))
    # 前の縫い目 (腰から頭の天辺まで、光る線)
    F += part("seam", ellipsoid("body", "teal", (0, 34.0, -3.0), (0.45, 9.5, 0.45), segs=6, rings=6))
    F += part("seam", ellipsoid("head", "teal", (0, 50.5, -3.3), (0.35, 3.8, 0.35), segs=6, rings=4))
    for s in (-1, 1):
        bone = "arm_l" if s < 0 else "arm_r"
        arm = [np.array([-5.0 * s, 42.0, 0.0]), np.array([-7.5 * s, 30.0, -0.5]), np.array([-9.5 * s, 18.0, -1.0]), np.array([-10.5 * s, 10.0, -1.5])]
        F += part("arm", tube(bone, "void", arm, [1.7, 1.3, 1.1, 0.9], sides=6, caps=(True, True)))
        F += part("arm", ellipsoid(bone, "void", (-5.0 * s, 42.0, 0.0), (1.9, 1.9, 1.9), segs=6, rings=3))
        for dx in (-0.8, 0.0, 0.8):
            F += part("finger", cone_(bone, "void", np.array([-10.5 * s + dx * s, 10.0, -1.5]), np.array([dx * 0.25, -1.0, -0.2]), 6.0, 0.65, sides=5, rings=2))
        leg = [np.array([2.5 * s, 25.0, 0.0]), np.array([2.5 * s, 13.0, 0.0]), np.array([2.5 * s, 2.5, 0.5])]
        bl = "leg_l" if s < 0 else "leg_r"
        F += part("leg", tube(bl, "void", leg, [2.2, 1.6, 1.1], sides=6, caps=(True, True)))
        F += part("foot", ellipsoid(bl, "void", (2.5 * s, 1.6, -1.4), (1.6, 1.4, 2.8), segs=6, rings=3))
    side_mats(F, "salt", "void")
    return F, []


POSES_C = {"idle": {}, "claw": {"arm_l": (-40, 0, 0), "arm_r": (-40, 0, 0)}}


# ============================================================ 材質 (深淵の色)
PAL = K.make_pal({
    "void": (AB.VOID, 0.5, {"crack": 0.0, "noise": 0.12}),
    "bone": (BONEC, 0.6, {"crack": 0.0, "noise": 0.12}),
    "salt": (SALT, 0.72, {"crack": 0.0, "noise": 0.1}),
    "slate": (AB.SLATE, 0.5, {"crack": 0.0, "noise": 0.12}),
    "teal": (AB.TEAL, 0.75, {"crack": 0.0, "noise": 0.06, "min": 2}),
}, glow=["teal"])

OPTS = {
    "a": dict(name="A 灯の番", build=build_a, bones=BONES_A, poses=POSES_A,
              concept="背の高い外套の番人。顔はなく、フードの中は闇。胸に空いた所を肋の杭が囲み、その中で鼓動の光が灯る。武器は持たず、長い袖から三本ずつの爪が出る。",
              dark="暗い所では外套の輪郭が消え、袖の白い骨の筋と、胸の鼓動の光だけが残る。灯りに照らされた側から形が出てくる。"),
    "b": dict(name="B 肋の獣", build=build_b, bones=BONES_B, poses=POSES_B,
              concept="積んだ肋が胴を作る獣。背骨は一本で、肋は背から胸を回って戻る。腕は鎖の連なりで、先に錨の爪が付く。頭は錨の角が二本。",
              dark="暗い所では肋の骨の白い筋だけが浮き、胸の奥の闇と鼓動の光が見える。鎖の輪は灯りが当たったところだけ鈍く光る。"),
    "c": dict(name="C 半分割れの形", build=build_c, bones=BONES_C, poses=POSES_C,
              concept="人の比率が狂った、顔のない形。体は左半分が塩、右半分が虚の黒に割れている。前の縫い目（腰から頭の天辺まで）だけが光る。腕は膝まで届く。",
              dark="暗い所では左半分の塩の結晶だけが灯りに拾われ、右半分の闇に消える。縫い目の光が、人の形を一本の線で描く。"),
}
ROWS = [("正面", 180, 0), ("3/4", 225, 10), ("横", 270, 0)]


def bake(key, o):
    faces, eyes = o["build"](key)
    size, P, N, M, L = _ck.build_atlas(faces, HINT, 0)
    atlas, glow = _ck.paint(faces, size, P, N, M, PAL, eyes)
    d = OUT / key
    d.mkdir(parents=True, exist_ok=True)
    name = f"warden_{key}"
    Image.fromarray(atlas, "RGBA").save(d / f"{name}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{name}_glowmask.png")
    data = _ck.export_mesh(faces, size, d / f"{name}_mesh.json", name, f"{name}_atlas.png", o["bones"],
                           {"glowmask": f"{name}_glowmask.png"})
    r = dict(key=key, faces=faces, size=size, atlas=atlas, glow=glow, data=data, tris=data["triangle_count"])
    r["buried"] = _ck.check_buried(faces)
    r["audit"] = AB.audit(r)
    r["floating"] = AB.float_check(faces)
    r["boxfloat"] = AB.bbox_float(faces)
    # 暗闇の絵: 灯りの外は 4%、灯りの中は距離の二乗で落ちる。光る画素 (鼓動・縫い目) は元の明るさのまま
    fall = np.clip(1.0 - (np.linalg.norm(P - LANT, axis=-1) / LANT_R) ** 2, 0.0, 1.0)
    k_ = 0.04 + 0.96 * fall
    dark = atlas.astype(float)
    dark[..., :3] *= np.where(glow, 1.0, k_)[..., None]
    r["dark"] = np.clip(dark, 0, 255).astype(np.uint8)
    r["lit_frac"] = float((fall[M >= 0] > 0.3).mean()) if (M >= 0).any() else 0.0
    return r


def views(r, o):
    """1 案の絵: 上段は明るい所、下段は暗闇 (灯りは左前の一つ)。"""
    rows = []
    for dark in (False, True):
        row = []
        atlas = r["dark"] if dark else r["atlas"]
        for label, yaw, pitch in ROWS:
            im = _ck.render_view(r, atlas, r["glow"], {}, o["bones"], yaw, pitch, (PW, PH), CEN, SC)
            row.append((label, im))
        rows.append(row)
    return rows


def sheet_one(key, o, rv, r, out_png):
    F = _ck.voxel.font
    GAP, X0, top = 14, 18, 84
    W = X0 * 2 + 3 * PW + 2 * GAP
    H = top + 2 * (30 + PH + 40) + 10
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((X0, 12), f'{o["name"]} — 心臓の番人の低ポリ ラフ', font=F(24), fill=(236, 232, 218))
    d.text((X0, 46), f'{r["tris"]} 三角形 / 高さ 51 単位 (3.2 ブロック)。人 (1.8) の目盛りつき。', font=F(15), fill=(160, 160, 176))
    y = top
    for ri, row in enumerate(rv):
        head = "明るい所 (普通の光)" if ri == 0 else "暗闇 (灯りは左前の一つだけ。光る所だけ見える)"
        d.text((X0, y), head, font=F(19), fill=(150, 232, 255))
        y += 30
        for i, (label, im) in enumerate(row):
            x = X0 + i * (PW + GAP)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
            d.text((x + 2, y + PH + 6), label, font=F(16), fill=(150, 232, 255))
        y += PH + 40
    cv.save(out_png)


def sheet_all(recs, out_png):
    F = _ck.voxel.font
    cells = []
    for key in "abc":
        cells.append(Image.open(OUT / f"warden_{key}_views.png").convert("RGB"))
    W = max(c.width for c in cells)
    H = sum(c.height for c in cells) + 14 * (len(cells) - 1)
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    y = 0
    for c in cells:
        cv.paste(c, (0, y))
        y += c.height + 14
    cv.save(out_png)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    recs = {}
    for key in "abc":
        o = OPTS[key]
        r = bake(key, o)
        recs[key] = r
        print(key, "tris", r["tris"], "atlas", r["size"], "buried", r["buried"], "floating", r["floating"],
              "boxfloat", r["boxfloat"], "audit", {b: v for b, v in r["audit"].items() if any(v)},
              "lit_frac", round(r["lit_frac"], 2))
    for key in "abc":
        o = OPTS[key]
        rv = views(recs[key], o)
        sheet_one(key, o, rv, recs[key], OUT / f"warden_{key}_views.png")
    sheet_all(recs, OUT / "warden_roughs_sheet.png")
    man = {"id": "warden", "short": "心臓の番人", "kind": "深淵の章の番人 (光の中でだけ見える) 3案",
           "title": "心臓の番人 (warden) — 3 案の低ポリ ラフ",
           "sheets": ["warden_roughs/warden_roughs_sheet.png"], "options": {}}
    for key in "abc":
        o, r = OPTS[key], recs[key]
        bad = ", ".join(f"{a}-{b}:{f}" for a, b, f in r["buried"]) or "なし"
        open_bad = {b: v for b, v in r["audit"].items() if any(v)}
        man["options"][key] = {
            "name": f'{o["name"]} ({r["tris"]} 三角形)',
            "desc": o["concept"] + " 暗闇では: " + o["dark"],
            "files": f"docs/review/warden_roughs/{key}/ と warden_{key}_views.png",
            "checks": f"埋まり {bad} / 水密 {'問題なし' if not open_bad else open_bad} / 浮き {len(r['floating'])} / 箱の浮き {len(r['boxfloat'])}",
        }
    (OUT / "warden_manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
