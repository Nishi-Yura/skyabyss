"""深淵の生き残り (探掘隊の五人) 低ポリ ラフ 3 案 (docs/design/36 の 8 章)。いまは都の係の人の絵から色を抜いた仮。

都の係の人 (attendants.py) と同じ骨・同じピボットなので、アニメ (idle / walk / talk) をそのまま使う。案ごとにメッシュは 1 つで、五人の違いは絵だけ。
深い所で何日も過ごした探掘隊なので、どの案も装備を体に足し、色は煤と塩でくすませる:
  A 探掘の装束    短い上着と肩掛けに、胸の吊り灯・肩から斜めの綱の束・背の荷枠と寝具
  B 塩の長外套    裾の長い衣に、かぶった頭巾と口覆い・腰の灯り・背の筒 (地図や薬を入れる)
  C 鉱夫の前掛け  上着と前掛けに、灯りつきの革の兜・背に交差させた鶴嘴と綱・腿の袋

  python tools/rough/lowpoly/fac/survivors.py
    -> docs/review/survivor_roughs/{a,b,c}/survivor_<look>_mesh.json, survivor_<look>_<role>_atlas.png, compare.png
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import attendants as A  # noqa: E402
from attendants import CK, part, loft, ring, tube, ellipsoid, build_atlas, export_mesh, check_buried, render_view, voxel  # noqa: E402

OUT = A.ROOT / "docs/review/survivor_roughs"
ROLES = ["survivor_bone", "survivor_salt", "survivor_storm", "survivor_ash", "abyss_porter"]
ROLE_JA = {"survivor_bone": "灯り番ミオ (骨の野営)", "survivor_salt": "測量士ハヤセ (塩の野営)", "survivor_storm": "薬師シズ (雷の野営)",
           "survivor_ash": "隊長トウヤ (灰の野営)", "abyss_porter": "荷運びロク (塩の野営)"}
# 煤と塩でくすんだ色。役の色 (role) だけ少し残す
A.PAL.update({
    "survivor_bone": dict(coat=(92, 86, 78), role=(150, 128, 92), trim=(58, 52, 46), acc=(180, 150, 90), hair=(70, 54, 42), skin="pale", guard=(84, 76, 66), sash=(110, 92, 64), lady=True),
    "survivor_salt": dict(coat=(150, 152, 150), role=(80, 104, 116), trim=(110, 112, 112), acc=(170, 160, 130), hair=(120, 108, 96), skin="mid", guard=(120, 122, 122), sash=(80, 104, 116)),
    "survivor_storm": dict(coat=(70, 86, 84), role=(140, 156, 132), trim=(48, 60, 58), acc=(120, 150, 140), hair=(150, 146, 140), skin="pale", guard=(66, 78, 76), sash=(96, 120, 104), lady=True),
    "survivor_ash": dict(coat=(56, 52, 52), role=(110, 46, 40), trim=(36, 32, 32), acc=(150, 146, 140), hair=(170, 166, 160), skin="old", guard=(62, 58, 56), sash=(86, 40, 36)),
    "abyss_porter": dict(coat=(104, 88, 66), role=(150, 132, 96), trim=(70, 58, 44), acc=(160, 140, 96), hair=(60, 46, 36), skin="brown", guard=(96, 80, 60), sash=(120, 100, 70)),
})


def lantern(pos, s=1.0):
    x, y, z = pos
    F = part("lamp", ellipsoid("body", "acc", (x, y, z), (0.9 * s, 1.2 * s, 0.9 * s), segs=6, rings=2))
    F += part("lamp_cap", ellipsoid("body", "trim", (x, y + 1.2 * s, z), (0.7 * s, 0.35 * s, 0.7 * s), segs=6, rings=1))
    F += part("lamp_ring", tube("body", "trim", [(x, y + 1.4 * s, z), (x, y + 2.1 * s, z)], ra=[0.18, 0.18], sides=4))
    return F


def rope_coil(cx, cy, cz, r=2.0, t=0.5, tilt=0.0):
    import math
    pts = []
    for k in range(13):
        a = math.radians(k * 30)
        pts.append((cx + math.cos(a) * r, cy + math.sin(a) * r * 0.9, cz + math.sin(a) * tilt))
    return part("coil", tube("body", "leather", pts, ra=[t] * len(pts), sides=4))


def look_a():
    F = A.build_c()
    # 胸の吊り灯 (左の胸の帯に下げる) と、肩から斜めの綱
    F += part("chest_strap", tube("body", "leather", [(-3.6, 21.6, -2.9), (0.2, 16.0, -3.5), (3.6, 11.4, -3.2)], ra=[0.28] * 3, sides=4))
    F += lantern((-1.6, 16.6, -4.2), 0.9)
    F += part("rope", tube("body", "leather", [(3.8, 22.4, -2.0), (0.4, 17.0, -3.6), (-4.4, 11.8, -2.8), (-4.8, 12.0, 2.6), (3.8, 22.4, 2.0)],
                           ra=[0.5] * 5, sides=4))
    # 背の荷枠と寝具の巻き
    F += part("frame", loft("body", "trim", [ring(10.5, 3.6, 3.0, 4.6), ring(21.5, 3.8, 3.0, 4.8)], caps=("trim", "trim")))
    F += part("bedroll", tube("body", "role", [(-3.6, 22.6, 4.4), (3.6, 22.6, 4.4)], ra=[1.3, 1.3], sides=6))
    F += part("bag", ellipsoid("body", "cloak", (0, 14.6, 5.2), (3.0, 3.2, 1.6), segs=6, rings=2))
    return F


def look_b():
    F = A.build_b()
    # かぶった頭巾 (頭を包む) と口覆い
    F += part("hood_up", ellipsoid("head", "role", (0, 27.6, 1.8), (4.5, 4.1, 4.0), segs=8, rings=3))
    F += part("hood_brim", tube("head", "trim", [(-3.9, 25.4, -1.8), (0, 30.4, -2.0), (3.9, 25.4, -1.8)], ra=[0.32] * 3, sides=4))
    F += part("scarf", loft("head", "sash", [ring(22.6, 4.3, -2.9, 2.6), ring(24.3, 4.1, -3.1, 2.4)], caps=(False, False)))
    # 腰の灯りと、背の筒 (革の蓋と肩紐)
    F += lantern((4.6, 10.6, -1.4), 0.85)
    F += part("tube_case", tube("body", "leather", [(-2.6, 10.0, 4.0), (2.8, 22.0, 4.2)], ra=[1.1, 1.1], sides=6, caps=(True, True)))
    F += part("tube_cap", tube("body", "acc", [(2.5, 21.4, 4.15), (3.1, 22.8, 4.25)], ra=[1.25, 1.25], sides=6, caps=(True, True)))
    F += part("tube_strap", tube("body", "leather", [(3.6, 22.0, -2.4), (-3.4, 12.0, -3.4)], ra=[0.26] * 2, sides=4))
    return F


def look_c():
    F = A.build_a()
    # 灯りつきの革の兜 (縁と前の小さな灯り)
    F += part("helm", ellipsoid("head", "leather", (0, 29.0, 0.1), (4.4, 2.4, 4.6), segs=8, rings=2))
    F += part("helm_brim", loft("head", "trim", [ring(27.2, 5.0, -5.2, 4.8), ring(27.7, 5.0, -5.2, 4.8)]))
    F += part("helm_lamp", ellipsoid("head", "acc", (0, 28.4, -4.6), (0.9, 0.8, 0.6), segs=6, rings=2))
    # 背に交差させた鶴嘴 2 本と綱の束、腿の袋
    for s in (-1, 1):
        F += part(f"pick{s}", tube("body", "trim", [(-s * 4.0, 9.0, 3.6), (s * 4.2, 23.0, 3.8)], ra=[0.35, 0.35], sides=4))
        F += part(f"pickhead{s}", tube("body", "guard", [(s * 2.6, 23.6, 4.2), (s * 5.8, 22.0, 4.0)], ra=[0.5, 0.25], sides=4))
    F += rope_coil(0, 15.5, 4.2, 2.4, 0.45)
    for s in (-1, 1):
        F += part(f"thighbag{s}", ellipsoid("body", "leather", (s * 2.4, 7.0, -2.0), (1.0, 1.3, 0.8), segs=5, rings=2))
    return F


LOOKS = {"a": look_a, "b": look_b, "c": look_c}
LOOK_JA = {"a": "A 探掘の装束", "b": "B 塩の長外套", "c": "C 鉱夫の前掛け"}
LOOK_DESC = {"a": "短い上着と肩掛けに、胸の吊り灯・肩から斜めの綱・背の荷枠と寝具。",
             "b": "裾の長い衣に、かぶった頭巾と口覆い・腰の灯り・背の筒。",
             "c": "上着と前掛けに、灯りつきの革の兜・背に交差させた鶴嘴と綱・腿の袋。"}


def build_look(k):
    faces = LOOKS[k]()
    d = OUT / k
    d.mkdir(parents=True, exist_ok=True)
    size, P, N, M, L = build_atlas(faces, 256, 0)
    atlases = {}
    for role in ROLES:
        img, glow = A.RA_PAINT(faces, size, P, N, M, A.pal_for(role), ())
        img = A.face_paint(img, faces, P, N, M, role)
        atlases[role] = img
        Image.fromarray(img, "RGBA").save(d / f"survivor_{k}_{role}_atlas.png")
    import numpy as np
    data = export_mesh(faces, size, d / f"survivor_{k}_mesh.json", f"survivor_{k}", f"survivor_{k}_survivor_bone_atlas.png", A.BONES)
    return dict(key=k, faces=faces, size=size, atlases=atlases, glow=np.zeros((size, size), bool), data=data, buried=check_buried(faces), tris=data["triangle_count"])


def view(r, role, yaw=212, pitch=12, size=(220, 330), center=(0, 15.0, 0), scale=9.0, pose="idle"):
    return render_view(r, r["atlases"][role], r["glow"], A.POSES[pose], A.BONES, yaw, pitch, size, center, scale, ref=False)


def compare(results):
    F = voxel.font
    PW, PH, GAP = 200, 300, 8
    cols = len(ROLES) + 1
    W = 200 + cols * (PW + GAP) + 10
    H = 96 + len(results) * (PH + 60) + 10
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), "深淵の生き残り (探掘隊) — 3 案 (前 3/4 と、最後の列は後ろ)", font=F(26), fill=CK.WH)
    for j, role in enumerate(ROLES):
        d.text((200 + j * (PW + GAP) + 4, 62), ROLE_JA[role], font=F(14), fill=CK.CY)
    d.text((200 + len(ROLES) * (PW + GAP) + 4, 62), "後ろ (荷)", font=F(14), fill=CK.CY)
    for i, r in enumerate(results):
        y = 96 + i * (PH + 60)
        d.text((14, y + 20), LOOK_JA[r["key"]], font=F(18), fill=CK.CY)
        d.text((14, y + 50), f"{r['tris']} 三角形", font=F(14), fill=CK.GR)
        yy = y + 80
        line = ""
        for ch in LOOK_DESC[r["key"]]:
            line += ch
            if len(line) >= 11:
                d.text((14, yy), line, font=F(13), fill=CK.GR)
                yy += 18
                line = ""
        if line:
            d.text((14, yy), line, font=F(13), fill=CK.GR)
        for j, role in enumerate(ROLES):
            x = 200 + j * (PW + GAP)
            cv.paste(view(r, role, size=(PW, PH)), (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
        x = 200 + len(ROLES) * (PW + GAP)
        cv.paste(view(r, "abyss_porter", yaw=30, size=(PW, PH)), (x, y))
        d.rectangle([x - 1, y - 1, x + PW, y + PH], outline=(60, 60, 78))
    return cv


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    results = []
    for k in "abc":
        r = build_look(k)
        results.append(r)
        print("look", k, "tris", r["tris"], "buried", r["buried"][:4])
    compare(results).save(OUT / "compare.png")
    print("wrote", OUT)


if __name__ == "__main__":
    main()
