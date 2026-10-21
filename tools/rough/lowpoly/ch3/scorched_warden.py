"""焼けた羽守 (scorched_warden。名づき) 低ポリ ラフ 3 案。羽守 (feather_warden A「鷲兜の番人」) の変種。

作り方: 羽守の本体と鉤槍 (feather_warden/opt_a.build) をそのまま読み、色を替えて部品を足す。骨・pivot は羽守と同じ (アニメを流用できる)。
A 焼けた外套 (外套と鎧羽が黒く焼け、兜の正面に青い焦げ筋が一本) / B 青い焦げ筋 (羽の色はそのまま、兜・肩・外套に青白い焦げ筋が走り、銅は緑青に) /
C 雷を受けた兜 (兜の冠が一本折れて焦げ、兜を縦に割る稲妻の傷が光る。外套は焼け縮れて短い)
実行: python -I tools/rough/lowpoly/ch3/scorched_warden.py  -> docs/review/ch3_roughs/scorched_warden/{a,b,c}/ と scorched_warden.png・scorched_warden_manifest.json
"""
import copy
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
FW = HERE.parent / "feather_warden"
sys.path.insert(0, str(FW))
from common import *  # noqa  (羽守の共通部分: 骨・描き・書き出し・姿勢)
import common as C  # noqa
import opt_a  # noqa
from PIL import Image, ImageDraw  # noqa

OUTD = C.ROOT / "docs/review/ch3_roughs"
SID = "scorched_warden"

CHAR = [(12, 12, 16), (24, 22, 26), (40, 36, 38), (60, 54, 54), (86, 78, 74), (112, 102, 94)]
CHARF = [(18, 18, 24), (34, 34, 42), (52, 52, 62), (76, 76, 88), (104, 104, 116), (134, 134, 146)]
VERD = [(26, 52, 50), (44, 82, 74), (66, 118, 100), (98, 150, 126), (140, 186, 160), (186, 220, 196)]
GLOW = [(150, 214, 255), (210, 240, 255), (250, 252, 255)]


def pal(k):
    P = copy.deepcopy(opt_a.PAL)
    m = P["mats"]
    m["scorch"] = dict(ramp=GLOW, base=0.8, glow=True)
    if k == "a":
        m["feather"]["ramp"] = CHARF
        m["dkfeather"]["ramp"] = CHAR
    elif k == "b":
        m["copper"]["ramp"] = VERD
        m["copper"].pop("stain", None)
    else:
        m["dkfeather"]["ramp"] = CHAR
        m["copper"]["ramp"] = [(28, 16, 12), (60, 34, 20), (100, 58, 32), (140, 84, 44), (180, 116, 64), (214, 154, 96)]
    return P


def streak(bone, pts, r=0.5):
    return tube(bone, "scorch", [V(*p) for p in pts], ra=[r] * len(pts), rb=[r] * len(pts), sides=3, hint=(1, 0, 0))


def add(k, F):
    if k == "a":
        # 兜の正面の青い焦げ筋 (額から鼻すじへ一本)
        F += streak("head", [(0.0, 35.6, -1.6), (0.3, 34.0, -3.9), (0.5, 32.6, -5.3)])
    elif k == "b":
        F += streak("head", [(-1.6, 35.0, -1.2), (-1.9, 33.6, -3.0), (-2.4, 32.2, -4.5)])
        F += streak("head", [(1.8, 34.6, 1.8), (3.2, 32.6, 2.6), (3.8, 30.0, 2.0)])
        for s, a in ((-1, "arm_l"), (1, "arm_r")):
            F += streak(a, [(s * 5.4, 24.2, -1.4), (s * 6.2, 21.6, -1.7), (s * 6.6, 19.0, -1.9)], 0.28)
        F += streak("body", [(-2.0, 22.8, -3.4), (0.0, 20.6, -3.9), (1.8, 18.0, -3.6)], 0.3)
    else:
        # 兜を縦に割る稲妻の傷 (ジグザグ) と、折れて焦げた冠の一本
        F += streak("head", [(0.0, 35.8, 0.4), (1.0, 34.6, -2.8), (-0.6, 33.4, -4.2), (0.6, 32.0, -5.4)], 0.55)
        F += tube("head", "dkfeather", [V(2.0, 35.0, 1.4), V(3.4, 36.2, 1.8)], ra=[0.8, 0.6], rb=[0.8, 0.6], sides=3, hint=(1, 0, 0))
        F += spike("head", "scorch", V(3.4, 36.2, 1.8), (1, 0.4, 0.2), 1.4, 0.55, sides=3, rings=1)


def build(k):
    F, G, eyes = opt_a.build()
    if k == "c":
        # 外套が焼け縮れて短い: cloak の羽の下の端を持ち上げる
        for f in F:
            if f.bone in ("cloak_l", "cloak_r"):
                v = np.asarray(f.verts, float)
                low = v[:, 1] < 12
                v[low, 1] = 12 - (12 - v[low, 1]) * 0.45
                f.verts = v
                if f.loc is not None:
                    f.loc = v.copy()
    add(k, F)
    return F, G, eyes


def build_option(k):
    body, gear, eyes = build(k)
    P = pal(k)
    d = OUTD / SID / k
    d.mkdir(parents=True, exist_ok=True)
    pre = f"{SID}_{k}"
    size, Pp, N, M, L = build_atlas(body, 128, 0)
    atlas, glow = C.paint(body, size, Pp, N, M, P, eyes)
    gsize, gP, gN, gM, gL = build_atlas(gear, 64, 0)
    gatlas, gglow = C.paint(gear, gsize, gP, gN, gM, P, ())
    Image.fromarray(atlas, "RGBA").save(d / f"{pre}_atlas.png")
    gm = np.zeros((size, size, 4), np.uint8)
    gm[glow] = (255, 255, 255, 255)
    Image.fromarray(gm, "RGBA").save(d / f"{pre}_glowmask.png")
    data = C.export_mesh(body, size, d / f"{pre}_mesh.json", pre, f"{pre}_atlas.png", C.BONES,
                         {"glowmask": f"{pre}_glowmask.png",
                          "note": "feather_warden A + parts. geo space (root bone carries rotation [0,180,0]); socket_main keeps its rest rotation [-15,0,0] and is empty (glaive = separate mesh)."})
    Image.fromarray(gatlas, "RGBA").save(d / f"{pre}_spear_atlas.png")
    extra = {"attach": {"socket_bone": "socket_main", "socket_pivot": [float(x) for x in C.SOCKET],
                        "note": "vertices are in the socket bone's local space (origin = socket pivot), shaft along +y"}}
    if gglow.any():
        ggm = np.zeros((gsize, gsize, 4), np.uint8)
        ggm[gglow] = (255, 255, 255, 255)
        Image.fromarray(ggm, "RGBA").save(d / f"{pre}_spear_glowmask.png")
        extra["glowmask"] = f"{pre}_spear_glowmask.png"
    gdata = C.export_mesh(gear, gsize, d / f"{pre}_spear_mesh.json", f"{pre}_spear", f"{pre}_spear_atlas.png", C.GEAR_BONES, extra, flip=False)
    return dict(key=k, body=body, gear=gear, eyes=eyes, size=size, atlas=atlas, glow=glow, data=data,
                gsize=gsize, gatlas=gatlas, gglow=gglow, gdata=gdata, tris=tri_count(body), gtris=tri_count(gear))


# 姿勢 (GeckoLib の値。羽守の動きと同じ書き方)。gen_ch3_ground_mobs.py の動きも同じ値を使う
RING = {"body": (-12, 0, 0), "arm_r": (-40, 0, 30), "arm_l": (-40, 0, -30), "cloak_l": (0, 0, -40), "cloak_r": (0, 0, 40),
        "leg_r": (20, 0, -8), "leg_l": (20, 0, 8), "socket_main": (-60, 0, 0)}
LEAP = {"body": (10, 0, 0), "cloak_l": (0, 0, -95), "cloak_r": (0, 0, 95), "arm_r": (-150, 0, 0), "arm_l": (-30, 0, -40),
        "leg_r": (-30, 0, 0), "leg_l": (-20, 0, 0), "head": (-14, 0, 0)}
LABELS = {"a": "A 焼けた外套", "b": "B 青い焦げ筋", "c": "C 雷を受けた兜"}
OPTS = {
    "a": "外套と鎧羽が黒く焼け、兜の正面に青白い焦げ筋が一本だけ光る。輪郭は羽守のままで、色だけで「焼けた」が分かる。暗い塔の頂で、焦げ筋の光が顔の位置を教える。",
    "b": "羽の色は羽守のまま、兜・肩・胸に青白い焦げ筋が何本も走り、銅は緑青に変わる。雷を浴び続けた番人。筋が多いので遠くからでも羽守と見分けやすい。",
    "c": "兜の冠の一本が折れて先が光り、兜を縦に割る稲妻の傷がジグザグに光る。外套は焼け縮れて短い。「柱の雷を受けた者」という話がいちばん強く、後ろ姿が羽守より小さい。",
}


def main():
    results = [build_option(k) for k in "abc"]
    for r in results:
        print(SID, r["key"], "body", r["data"]["triangle_count"], "glaive", r["gdata"]["triangle_count"], "atlas", r["size"])
    F = voxel.font
    PW, GAP, X0 = 440, 14, 20
    rows = [("前 3/4 (待機)", {}, 212, 10, 460, (0, 22, -1), 10.0),
            ("後ろ 3/4 (外套)", {}, 32, 10, 460, (0, 22, -1), 10.0),
            ("予告 1: 突撃のかがみ (1 秒。床に光る帯)", C.CROUCH, 140, 8, 460, (0, 20, -1), 10.0),
            ("突撃", C.DIVE, 120, 6, 460, (0, 20, -2), 9.5),
            ("予告 2: 足もとの雷の輪 (1.2 秒。外套を開いて身を沈める)", RING, 200, 14, 460, (0, 20, -1), 10.0),
            ("外套を広げて上へ跳ぶ (落ちる先に輪)", LEAP, 200, 8, 500, (0, 24, 0), 9.0)]
    W = X0 * 2 + 3 * PW + 2 * GAP
    H = 110 + sum(30 + r[4] + 34 for r in rows) + 400
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), "焼けた羽守 (scorched_warden) — 羽守の変種・名づき 3 案", font=F(26), fill=(236, 232, 218))
    d.text((24, 50), "焼けた見張り塔の頂の主。羽守 (鷲兜の番人) に部品と色を足す (骨と鉤槍は羽守と同じ)。HP 190 (人数で増える)、鉤槍 10。", font=F(15), fill=(160, 160, 176))
    d.text((24, 72), "技: 突撃 (光る帯) / 外套を広げて上へ跳ぶ / 足もとの雷の輪 (1.2 秒)。柱から雷を受けると傷がよく通る。左の黄の目盛りは人 (1.8 ブロック)。", font=F(15), fill=(160, 160, 176))
    y = 110
    for head, pose, yaw, pitch, ph, cen, sc in rows:
        d.text((X0, y), head, font=F(20), fill=(150, 232, 255))
        y += 30
        for i, r in enumerate(results):
            x = X0 + i * (PW + GAP)
            big, gl, yoff = C.combined_atlas(r)
            im = C.render_pose(r, big, gl, yoff, pose, yaw, pitch, (PW, ph), cen, sc)
            bg = Image.fromarray(np.clip(voxel._background(PW, ph, "dark"), 0, 255).astype(np.uint8))
            dd = ImageDraw.Draw(bg, "RGBA")
            V_ = rot_x(pitch) @ rot_y(yaw)
            c0 = V_ @ np.asarray(cen, float)
            gy = -(0 - c0[1]) * sc + ph / 2
            dd.line([(10, gy), (10, gy - 28.8 * sc)], fill=(255, 220, 140, 150), width=2)
            bg.paste(im, (0, 0), im)
            cv.paste(bg, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
            d.text((x + 2, y + ph + 6), f'{LABELS[r["key"]]}  本体 {r["data"]["triangle_count"]} + 鉤槍 {r["gdata"]["triangle_count"]} 三角形', font=F(17), fill=(150, 232, 255))
        y += ph + 34
    d.text((X0, y), "アトラス (本体)", font=F(20), fill=(150, 232, 255))
    y += 30
    for i, r in enumerate(results):
        x = X0 + i * (PW + GAP)
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        k2 = max(1, min(3, PW // r["size"]))
        cv.paste(bgc.convert("RGB").resize((r["size"] * k2, r["size"] * k2), Image.NEAREST), (x, y))
    cv = cv.crop((0, 0, W, y + max(r["size"] * max(1, min(3, PW // r["size"])) for r in results) + 20))
    cv.save(OUTD / f"{SID}.png")
    tri = " / ".join(f'{r["key"].upper()} {r["data"]["triangle_count"]}+{r["gdata"]["triangle_count"]}' for r in results)
    ent = {"id": "ch3_" + SID, "short": "焼けた羽守", "kind": "塔の主 (名づき・変種) 3案", "title": "焼けた羽守 (scorched_warden) — 羽守の変種・低ポリ 3 案",
           "sheets": [f"ch3_roughs/{SID}.png"],
           "extra": "羽守 (feather_warden A) の本体と鉤槍に色と部品を足す。骨は羽守と同じ。三角形 (本体+鉤槍): " + tri
                    + " (目安 名づき 900〜1,200)。メッシュ・アトラスは docs/review/ch3_roughs/scorched_warden/{a,b,c}/。生成: tools/rough/lowpoly/ch3/scorched_warden.py。",
           "options": {k: {"name": LABELS[k] + f' ({r["data"]["triangle_count"]} + 鉤槍 {r["gdata"]["triangle_count"]} 三角形)', "desc": OPTS[k]}
                       for k, r in zip("abc", results)}}
    (OUTD / f"{SID}_manifest.json").write_text(json.dumps(ent, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main()
