"""潮の番人 (shore_warden。名づき) 低ポリ ラフ 3 案。珊瑚憑き (coral_thrall2 A「岩礁の溺れ人」) の変種。

作り方: 珊瑚憑きの本体 (coral_thrall2/opt_a.build) をそのまま読み、部品を足して全体を 1.2 倍 (背が高い)。
銛は三案共通の新しい「珊瑚の銛」(返しのある珊瑚の穂先・流木の柄に白い塩の筋と帯)。三案は足す部品だけ違う:
A 塩の冠 (頭と肩に白い塩の結晶、腕に塩の筋) / B 潮の外套 (肩から背へ昆布の外套、肩に真珠層の板、胸に真珠) / C 真珠の胸殻 (肩と胸に二枚貝の殻の鎧、脚に塩の殻)
骨・pivot は珊瑚憑きと同じ (1.2 倍)。geo の空間 (root は Y 180 度)。姿勢の値は GeckoLib の値そのまま (珊瑚憑きの attack と同じ書き方)。

実行: python -I tools/rough/lowpoly/ch2/shore_warden.py  -> docs/review/ch2_roughs/shore_warden/{a,b,c}/ と shore_warden.png・shore_warden_manifest.json
"""
import copy
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
CT = HERE.parent / "coral_thrall2"
sys.path.insert(0, str(CT))
from parts import *  # noqa  (common の全部 + stag / cloth / lumps)
import common as C  # noqa
import opt_a  # noqa
from PIL import Image, ImageDraw  # noqa

OUTD = C.ROOT / "docs/review/ch2_roughs"
SID = "shore_warden"
S = 1.2
BONES_S = {k: (p, [float(x) * S for x in piv]) for k, (p, piv) in C.BONES.items() if k != "statue_coral"}
SOCKET_S = np.array(BONES_S["socket_main"][1])

SALT = [(116, 122, 128), (170, 176, 180), (210, 214, 214), (234, 236, 232), (252, 252, 248)]
NACRE = [(88, 84, 104), (140, 136, 158), (190, 186, 206), (224, 220, 232), (248, 244, 242)]
PEARL = [(170, 160, 176), (214, 206, 218), (240, 234, 240), (255, 252, 250)]
TEALK = [(12, 36, 44), (22, 64, 72), (36, 100, 104), (64, 142, 134), (112, 186, 172)]
PAL = copy.deepcopy(opt_a.PAL)
PAL["mats"].update({
    "salt": dict(ramp=SALT, base=0.74, lit=0.18, crack=0.0, min=0, noise=0.1, facevar=0.06),
    "nacre": dict(ramp=NACRE, base=0.68, lit=0.2, crack=0.0, min=0, noise=0.18, facevar=0.08),
    "pearl": dict(ramp=PEARL, base=0.72, lit=0.18, crack=0.0, min=1),
    "teal": dict(ramp=TEALK, base=0.58, lit=0.2, crack=0.0, min=0, stripe=3, axis="v", noise=0.12),
})
PAL["glow_mats"] = ["glass", "pearl"]


def part(pid, faces):
    for f in faces:
        f.part = pid
    return faces


# ====================================================================== 銛 (三案共通。socket の pivot が原点・先が +Y)
def harpoon():
    B = []
    B += tube("spear", "wood", [(0, -12.0, 0), (0, -2.0, 0), (0, 8.0, 0), (0, 17.0, 0)], ra=[0.55, 0.7, 0.66, 0.6], rb=[0.55, 0.7, 0.66, 0.6],
              sides=5, hint=(1, 0, 0), phase=0, caps=(True, False))
    # 白い塩の帯と、柄に沿う塩の筋 (柄に沈める)
    for y in (-7.0, 1.5, 10.5):
        B += tube("spear", "salt", [(0, y, 0), (0, y + 1.2, 0)], ra=[0.95, 0.95], rb=[0.95, 0.95], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
    for a in (0, 150):
        x, z = 0.5 * math.cos(math.radians(a)), 0.5 * math.sin(math.radians(a))
        B += tube("spear", "salt", [(x, -10.0, z), (x * 1.05, 4.0, z * 1.05), (x, 16.0, z)], ra=[0.18, 0.24, 0.16], sides=3, hint=(0, 0, 1))
    B += spike("spear", "orng", (0, -12.0, 0), (0, -1, 0), 2.0, 0.55, sides=4, rings=1)
    # 穂先: 珊瑚の葉の形 + 後ろ向きの返し 2 + 塩の先
    B += tube("spear", "pink", [(0, 16.5, 0), (0, 19.0, 0), (0, 22.5, 0), (0, 27.5, 0)], ra=[0.9, 1.9, 1.4, 0.0], rb=[0.6, 0.7, 0.55, 0.0],
              sides=4, hint=(1, 0, 0), phase=0)
    for s in (-1, 1):
        B += spike("spear", "orng", (s * 1.2, 19.6, 0), (s * 0.7, -1, 0), 3.4, 0.5, sides=3, rings=1)
    B += spike("spear", "salt", (0, 27.0, 0), (0, 1, 0), 2.2, 0.35, sides=3, rings=1)
    B += tube("spear", "barn", [(0, 15.6, 0), (0, 16.8, 0)], ra=[1.1, 1.1], rb=[1.1, 1.1], sides=5, hint=(1, 0, 0), phase=0, caps=(True, True))
    return B


# ====================================================================== 三案の部品 (珊瑚憑きの単位。作図空間 = 前 -Z)
def add_salt_crown(F):
    """A: 頭と肩に白い塩の結晶、腕に塩の筋、胸に塩の粒。"""
    for (x, y, z, dx, dy, dz, ln) in ((0.8, 30.8, -0.2, 0.2, 1, 0.1, 3.2), (-1.4, 30.6, 0.4, -0.4, 1, 0.2, 2.6), (2.4, 29.6, 0.6, 0.8, 1, 0.1, 2.2),
                                       (-4.6, 25.4, 0.8, -0.5, 1, 0.3, 3.0), (4.4, 25.4, 0.6, 0.6, 1, 0.2, 2.6), (-3.6, 25.6, 1.8, -0.2, 1, 0.6, 2.0)):
        bone = "head" if y > 27 else ("arm_l" if x < 0 else "arm_r")
        F += part(f"salt{x}{y}", spike(bone, "salt", (x, y, z), (dx, dy, dz), ln, 0.7, sides=4, rings=1))
    for nm, s in (("l", -1), ("r", 1)):
        F += part(f"streak{nm}", tube(f"arm_{nm}", "salt", [(s * 4.9, 23.6, -1.2), (s * 5.0, 21.0, -1.3), (s * 4.6, 18.2, -1.0)], ra=[0.2, 0.3, 0.2], sides=3))
    F += part("chestsalt", lumps("body", "salt", [((-1.6, 20.0, -3.9), 0.8), ((0.4, 21.4, -4.0), 0.7), ((1.8, 18.8, -3.6), 0.6)], segs=5))


def add_mantle(F):
    """B: 肩から背へ垂れる青緑の昆布の外套、肩の真珠層の板、胸の真珠。"""
    rows = []
    for y, w, z in ((24.6, 4.6, 2.0), (20.0, 5.0, 3.6), (14.0, 5.0, 4.0), (8.0, 4.4, 3.6), (4.6, 3.6, 3.0)):
        rows.append([V(-w, y, z - 0.6), V(-w * 0.4, y, z + 0.3), V(w * 0.4, y, z + 0.3), V(w, y, z - 0.6)])
    rows[-1] = [p + V(0, (0.8 if i % 2 else -0.6), 0) for i, p in enumerate(rows[-1])]
    F += part("mantle", grid("body", "teal", rows, (0, 14, 0)))
    for nm, s in (("l", -1), ("r", 1)):
        F += part(f"plate{nm}", ellipsoid(f"arm_{nm}", "nacre", (s * 4.4, 25.0, -0.2), (2.4, 1.0, 2.4), segs=6, rings=1))
    F += part("pearl", ellipsoid("body", "pearl", (0.0, 21.6, -4.3), (1.2, 1.2, 1.0), segs=6, rings=2))


def add_shell_armor(F):
    """C: 肩と胸に二枚貝の殻の鎧 (真珠層・珊瑚の縁)、脚に塩の殻。"""
    for nm, s in (("l", -1), ("r", 1)):
        F += part(f"pauldron{nm}", ellipsoid(f"arm_{nm}", "nacre", (s * 4.6, 25.2, -0.3), (2.8, 1.5, 2.8), segs=8, rings=2))
        F += part(f"rim{nm}", tube(f"arm_{nm}", "pink", [(s * 2.6, 24.4, -2.4), (s * 4.6, 24.0, -3.0), (s * 6.8, 24.2, -1.6), (s * 7.2, 24.6, 0.6)], ra=0.4, sides=3))
        F += part(f"legsalt{nm}", ellipsoid(f"leg_{nm}", "salt", (s * 2.2, 9.0, -1.2), (1.6, 2.2, 1.0), segs=5, rings=1))
    F += part("breast", ellipsoid("body", "nacre", (0.0, 19.6, -3.6), (3.4, 3.0, 1.2), segs=8, rings=2))
    for i in range(4):
        x = -2.1 + 1.4 * i
        F += part(f"ridge{i}", tube("body", "salt", [(x * 0.4, 22.4, -4.2), (x, 19.4, -4.8), (x * 1.1, 16.8, -4.0)], ra=[0.15, 0.3, 0.15], sides=3))


ADD = {"a": add_salt_crown, "b": add_mantle, "c": add_shell_armor}
LABELS = {"a": "A 塩の冠", "b": "B 潮の外套", "c": "C 真珠の胸殻"}


def scale_faces(faces, k):
    for f in faces:
        f.verts = np.asarray(f.verts, float) * k
        if f.loc is not None:
            f.loc = np.asarray(f.loc, float) * k
        f.ref = np.asarray(f.ref, float) * k
    return faces


def build(k):
    body, _spear, eyes = opt_a.build()
    ADD[k](body)
    scale_faces(body, S)
    spear = scale_faces(harpoon(), S)
    eyes = [dict(e, c=np.asarray(e["c"], float) * S, rx=e["rx"] * S, ry=e["ry"] * S, pr=(e["pr"][0] * S, e["pr"][1] * S)) for e in eyes]
    return body, spear, eyes


# ====================================================================== 書き出し
def build_option(k):
    body, spear, eyes = build(k)
    d = OUTD / SID / k
    d.mkdir(parents=True, exist_ok=True)
    pre = f"{SID}_{k}"
    size, P, N, M, L = build_atlas(body, 176, 0)
    atlas, glow = C.paint(body, size, P, N, M, PAL, eyes)
    Image.fromarray(atlas, "RGBA").save(d / f"{pre}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{pre}_glowmask.png")
    data = C.export_mesh(body, size, d / f"{pre}_mesh.json", pre, f"{pre}_atlas.png", BONES_S,
                         {"glowmask": f"{pre}_glowmask.png",
                          "note": "coral_thrall2 A + parts, x1.2. geo space (root rotation [0,180,0]); socket_main is empty (harpoon = separate mesh)"})
    ssize, sP, sN, sM, sL = build_atlas(spear, 64, 0)
    satlas, sglow = C.paint(spear, ssize, sP, sN, sM, PAL, ())
    Image.fromarray(satlas, "RGBA").save(d / f"{pre}_harpoon_atlas.png")
    sg = np.zeros((ssize, ssize, 4), np.uint8)
    sg[sglow] = (255, 255, 255, 255)
    Image.fromarray(sg, "RGBA").save(d / f"{pre}_harpoon_glowmask.png")
    sdata = C.export_mesh(spear, ssize, d / f"{pre}_harpoon_mesh.json", f"{pre}_harpoon", f"{pre}_harpoon_atlas.png", C.SPEAR_BONES,
                          {"glowmask": f"{pre}_harpoon_glowmask.png",
                           "attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in SOCKET_S],
                                      "note": "vertices are in the socket bone's local space (origin = socket pivot), tip toward +Y"}}, flip=False)
    return dict(key=k, body=body, spear=spear, size=size, atlas=atlas, glow=glow, ssize=ssize, satlas=satlas, sglow=sglow,
                data=data, sdata=sdata, statue=[])


def pose_body(faces, pose, pos=None):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([C.flipxz(C.pose_point(C.flipxz(p), f.bone, pose, bones=BONES_S, pos=pos)) for p in f.verts])
        C._refresh(g, f)
        out.append(g)
    return out


def pose_gear(faces, pose, pos=None):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([C.flipxz(C.pose_point(np.asarray(p, float) + SOCKET_S, "socket_main", pose, bones=BONES_S, pos=pos)) for p in f.verts])
        C._refresh(g, f)
        out.append(g)
    return out


def render_pose(r, pose, yaw, pitch, size, center, scale, pos=None, spear=True, body=True):
    big, glow, S0 = C.combined_atlas(r)
    faces = []
    if body:
        faces += pose_body(r["body"], pose, pos)
    if spear:
        faces += pose_gear(C.spear_faces_shifted(r, S0), pose, pos)
    return render(faces, big, glow, yaw, pitch, size, center, scale)


# 姿勢 (GeckoLib の値。珊瑚憑きの attack と同じ書き方)。gen_ch2_ground_mobs.py の動きも同じ値を使う
STANCE = {"arm_r": (-34.0, 0.0, 0.0), "forearm_r": (40.0, 0.0, 0.0), "body": (-10.0, -18.0, 0.0), "head": (8.0, 14.0, 0.0), "arm_l": (24.0, 0.0, 12.0),
          "leg_r": (14.0, 0.0, 0.0), "leg_l": (-12.0, 0.0, 0.0)}
THRUST = {"arm_r": (52.0, 0.0, 0.0), "forearm_r": (-62.0, 0.0, 0.0), "body": (18.0, 12.0, 0.0), "head": (-10.0, -8.0, 0.0), "arm_l": (-24.0, 0.0, 10.0),
          "leg_r": (-18.0, 0.0, 0.0), "leg_l": (16.0, 0.0, 0.0)}
THRUST_POS = {"body": (0.0, -0.6, 3.0)}
CAST = {"arm_r": (-160.0, 0.0, -8.0), "forearm_r": (-40.0, 0.0, 0.0), "body": (-8.0, 0.0, 0.0), "head": (14.0, 0.0, 0.0), "arm_l": (-150.0, 0.0, 14.0)}
SLAM = {"arm_r": (-70.0, 0.0, 0.0), "forearm_r": (-70.0, 0.0, 0.0), "body": (20.0, 0.0, 0.0), "head": (-6.0, 0.0, 0.0), "arm_l": (-50.0, 0.0, 10.0),
        "leg_r": (-20.0, 0.0, 0.0), "leg_l": (10.0, 0.0, 0.0)}
SLAM_POS = {"body": (0.0, -1.6, 0.0)}

OPTS = {
    "a": "珊瑚憑きの体のまま背が高く、頭の冠と肩に白い塩の結晶が生え、腕と胸に塩の筋と粒。珊瑚の桃と岩の青に白が刺さり、「塩の蔵の主」が一目で分かる。輪郭は冠と肩の尖りで上に伸びる。",
    "b": "肩から背へ青緑の昆布の外套を垂らし、肩に真珠層の板、胸に光る真珠を一つ。外套で後ろ姿が大きく、真珠の光で暗い蔵の奥でも居場所が分かる。動くと外套が揺れる。",
    "c": "肩と胸に二枚貝の殻を鎧のように着る (真珠層の白と珊瑚の縁)。脚に塩の殻。殻の鎧の重い番人。正面がいちばん白く硬く見える。",
}


def main():
    results = [build_option(k) for k in "abc"]
    for r in results:
        print(SID, r["key"], "body", r["data"]["triangle_count"], "harpoon", r["sdata"]["triangle_count"], "atlas", r["size"])
    F = voxel.font
    PW, GAP, X0 = 480, 14, 20
    rows = [("前 3/4 (待機)", {}, None, 212, 10, 460, (0, 21, -1), 10.0),
            ("後ろ 3/4", {}, None, 32, 10, 460, (0, 21, -1), 10.0),
            ("予告 1: 銛の構え (1 秒以上。床に前の帯)", STANCE, None, 130, 8, 460, (0, 21, -4), 9.4),
            ("突き", THRUST, THRUST_POS, 130, 8, 460, (0, 21, -6), 9.0),
            ("予告 2: 満ち潮の輪 (銛を頭上へ。床に青い輪 1.2 秒)", CAST, None, 200, 8, 520, (0, 26, -1), 8.6),
            ("輪の打ち下ろし (石突を床へ)", SLAM, SLAM_POS, 230, 8, 460, (0, 20, -3), 9.0)]
    W = X0 * 2 + 3 * PW + 2 * GAP
    H = 110 + sum(30 + r[5] + 34 for r in rows) + 600
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), "潮の番人 (shore_warden) — 珊瑚憑きの変種・名づき 3 案", font=F(26), fill=(236, 232, 218))
    d.text((24, 50), "沈んだ塩の蔵の主。珊瑚憑き (岩礁の溺れ人) に部品を足し、全体 1.2 倍 (背 約 2.3 ブロック)。銛は三案共通: 返しのある珊瑚の穂先、流木の柄に白い塩の帯と筋。HP 160 (人数で増える)、突き 9。",
           font=F(15), fill=(160, 160, 176))
    d.text((24, 72), "技: 銛の突き (構え 1.1 秒・床の帯) / 満ち潮の輪 (床の青い輪 1.2 秒のあと、輪の中が数秒水に沈む)。左の黄の目盛りは人 (1.8 ブロック)。", font=F(15), fill=(160, 160, 176))
    y = 110
    for head, pose, pos, yaw, pitch, ph, cen, sc in rows:
        d.text((X0, y), head, font=F(20), fill=(150, 232, 255))
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            im = render_pose(r, pose, yaw, pitch, (PW, ph), cen, sc, pos=pos)
            bg = Image.fromarray(np.clip(voxel._background(PW, ph, "dark"), 0, 255).astype(np.uint8))
            dd = ImageDraw.Draw(bg, "RGBA")
            V_ = rot_x(pitch) @ rot_y(yaw)
            c0 = V_ @ np.asarray(cen, float)
            gy = -(0 - c0[1]) * sc + ph / 2
            dd.line([(10, gy), (10, gy - 28.8 * sc)], fill=(255, 220, 140, 150), width=2)
            bg.paste(im, (0, 0), im)
            cv.paste(bg, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
            d.text((x + 2, y + ph + 6), f'{LABELS[r["key"]]}  本体 {r["data"]["triangle_count"]} + 銛 {r["sdata"]["triangle_count"]} 三角形', font=F(17), fill=(150, 232, 255))
        y += ph + 34
    d.text((X0, y), "アトラス (本体 / 銛)", font=F(20), fill=(150, 232, 255))
    y += 30
    for i, r in enumerate(results):
        x = X0 + i * (PW + GAP)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        k2 = 2 if r["size"] * 2 + r["ssize"] * 2 + 8 <= PW else 1
        cv.paste(bgc.convert("RGB").resize((r["size"] * k2, r["size"] * k2), Image.NEAREST), (x, y))
        sa = Image.fromarray(r["satlas"], "RGBA")
        bgs = Image.new("RGBA", sa.size, (40, 40, 52, 255))
        bgs.alpha_composite(sa)
        cv.paste(bgs.convert("RGB").resize((r["ssize"] * 2, r["ssize"] * 2), Image.NEAREST), (x + r["size"] * k2 + 8, y))
    cv = cv.crop((0, 0, W, y + 2 * max(r["size"] for r in results) + 20))
    cv.save(OUTD / f"{SID}.png")
    tri = " / ".join(f'{r["key"].upper()} {r["data"]["triangle_count"]}+{r["sdata"]["triangle_count"]}' for r in results)
    ent = {"id": "ch2_" + SID, "short": "潮の番人", "kind": "蔵の主 (名づき・変種) 3案", "title": "潮の番人 (shore_warden) — 珊瑚憑きの変種・低ポリ + ドット絵 3 案",
           "sheets": [f"ch2_roughs/{SID}.png"],
           "extra": "珊瑚憑き (coral_thrall2 A) の本体に部品を足し、1.2 倍。骨は珊瑚憑きと同じ。銛 (別メッシュ、socket_main) は三案共通。三角形 (本体+銛): " + tri
                    + " (目安 名づき 900〜1,200)。メッシュ・アトラスは docs/review/ch2_roughs/shore_warden/{a,b,c}/。生成: tools/rough/lowpoly/ch2/shore_warden.py。",
           "options": {k: {"name": LABELS[k] + f' ({r["data"]["triangle_count"]} + 銛 {r["sdata"]["triangle_count"]} 三角形)', "desc": OPTS[k]}
                       for k, r in zip("abc", results)}}
    (OUTD / f"{SID}_manifest.json").write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
