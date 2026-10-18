"""磯走り: 今のモデルと低ポリ 3 案の比較画像・manifest を作り、メッシュ・アトラスも書き出す。

実行: python tools/rough/lowpoly/reef_scuttler/compare.py
出力: docs/review/lowpoly_roughs/reef_scuttler/{a,b,c}/ (メッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/reef_scuttler.png と reef_scuttler_manifest.json
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from scut import *  # noqa
from PIL import ImageDraw  # noqa
import rs_a, rs_b, rs_c  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
OPTS = [("a", "A 岩礁蟹", rs_a), ("b", "B 真珠の扇", rs_b), ("c", "C 蜘蛛蟹", rs_c)]
COL = {"a": (255, 190, 130), "b": (190, 215, 245), "c": (160, 232, 180)}
PW, GAP, X0 = 388, 10, 8
F = voxel.font


def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_faces(pose=None):
    pose = {k: tuple(-a for a in v) for k, v in (pose or {}).items()}
    g = json.loads(GEO.read_text(encoding="utf-8"))
    return voxel.load_geo(g, ASSETS / "textures/entity/reef_scuttler.png", ASSETS / "textures/entity/reef_scuttler_glowmask.png", pose)


def old_render(faces, yaw, pitch, size, scale):
    V_ = rot_x(pitch) @ rot_y(yaw)
    pts = np.array([V_ @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    m = (1 - scale * span / min(size)) / 2
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=m)


def new_render(r, faces, yaw, pitch, size, scale, cen=None):
    if cen is None:
        allv = np.concatenate([f.verts for f in faces])
        cen = (allv.min(0) + allv.max(0)) / 2
    return render(faces, r["atlas"], r["glow"], yaw, pitch, size, cen, scale)


def wrap(d, text, font, width):
    lines, cur = [], ""
    for ch in text:
        if d.textlength(cur + ch, font=font) > width:
            lines.append(cur)
            cur = ch
        else:
            cur += ch
    if cur:
        lines.append(cur)
    return lines


BRIEF = {
    "old": "今: 箱を重ねた銅色の蟹。目の柄は光る、背に灰色の石板、前に鋏、脚は三本ずつ",
    "a": "今のキャラに忠実。銅色の平たい甲羅、縁のフジツボ、背の海石の板、大小の鋏、折れ脚",
    "b": "背に貝の扇 (ホタテ) を斜めに立てる青灰の楔形。象牙色の鎌の鋏、太く短い脚、目は縁から覗く",
    "c": "緑青の矢尻形の甲羅を長い細脚で持ち上げた走り屋。背びれの棘列、鼻の角、珊瑚色の刃の鋏",
}
DESC = {
    "a": "素直に今の磯走りを面の割れた低ポリにした形。面取りした 4 段の甲羅 (銅色の外殻、藻の斑)、背に斜めに乗る海石の板、縁と鋏の関節のフジツボ、八の字に張る関節の太い脚。右の鋏が大きい。「潮殻の背中の岩礁の小蟹」と一目で分かる、いちばん安全な案。",
    "b": "背負った貝の扇が輪郭の主役 (ホタテ型、蝶番側がピンク、縁がギザギザ)。体は青灰色の石の楔で前が尖り、脚は太く短く前向き。鋏は細く反った象牙色の鎌で、先が錆色。珊瑚の庭の「貝をかぶった蟹」で、真珠の庭と潮殻の遺跡の色に合う。",
    "c": "矢尻形の細い甲羅を、長い 3 角柱の脚 4 本ずつで高く持ち上げる。鼻先に長い角、背に後ろへ流れる棘の列、長く伸びた珊瑚色の刃の鋏 (内側に歯、先が交わる)。「速い群れ」の脅威が輪郭から読める。酸化した銅 (緑青) の緑と珊瑚色の対比。",
}


def main():
    results = []
    for key, label, mod in OPTS:
        r = build_option(key, mod.build, mod.PAL, getattr(mod, "HINT", 128))
        r["label"] = label
        r["mod"] = mod
        results.append(r)
        print(key, "tris", r["data"]["triangle_count"], r["size"], "glow", int(r["glow"].sum()))
    n_old = sum(len(b.get("cubes", [])) for b in json.loads(GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"])
    xs = [X0 + i * (PW + GAP) for i in range(4)]
    W = 1600
    canvas = Image.new("RGB", (W, 3600), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((16, 12), "磯走り (reef_scuttler) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (A 岩礁蟹 / B 真珠の扇 / C 蜘蛛蟹)", font=F(25), fill=WH)
    d.text((16, 46), "潮殻の背の群れの小蟹 (体力 10・速い・群れで追う・裏返ると弱い)。骨は body / leg_r / leg_l / claw_r / claw_l (今の geo と同じ名前・階層・pivot) で、"
                     "今のアニメ (idle / walk / attack / hurt / death / flipped) がそのまま動く。", font=F(15), fill=GR)
    d.text((16, 70), "1 ブロック = 32 テクセル・最近傍・限られたパレット・手で打った陰影。目の光 (シアン) だけが光る画素 (glowmask)。目安は 600 三角形まで。", font=F(15), fill=GR)
    top = 140
    heads = [("今 (箱 %d 個)" % n_old, WH)] + [(f"{r['label']}  {r['data']['triangle_count']} 三角形", COL[r["key"]]) for r in results]
    for i, (t, c) in enumerate(heads):
        d.text((xs[i], top - 52), t, font=F(22), fill=c)
    briefs = ["old"] + [r["key"] for r in results]
    for i, k in enumerate(briefs):
        for j, ln in enumerate(wrap(d, BRIEF[k], F(13), PW)):
            d.text((xs[i], top - 24 + j * 16), ln, font=F(13), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    olds = old_faces()
    old_front = [f for f in olds if (f.origin + (f.eu + f.ev) / 2)[2] <= -2.0]
    y = top + 24
    rows = [("前 3/4", 212, 18, 280, 16.0, "full"),
            ("後ろ 3/4", 32, 20, 280, 16.0, "full"),
            ("顔と鋏のアップ (前)", 196, 12, 300, 21.0, "front"),
            ("上から", 200, 80, 260, 16.0, "full")]
    state = {}
    for label, yaw, pitch, h, sc, sel in rows:
        for i in range(4):
            bgp = panel_bg(PW, h)
            if i == 0:
                im = old_render(old_front if sel == "front" else olds, yaw, pitch, (PW, h), sc)
                bgp = im.convert("RGB")
            else:
                r = results[i - 1]
                fs = r["faces"]
                if sel == "front":
                    fs = [f for f in fs if f.verts.mean(0)[2] <= -2.0]
                im = new_render(r, fs, yaw, pitch, (PW, h), sc * r['mod'].VIEW.get('k', 1.0))
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (xs[i], y))
            frame(xs[i], y, PW, h)
        d.text((X0, y + h + 4), label, font=F(15), fill=WH)
        y += h + 28
    # ---- ポーズ
    for label, anim, t, yaw, pitch, hh, sc in (
            ("攻撃 (attack) 0.22 s: 鋏を打ち下ろして噛む", "attack", 0.22, 212, 18, 280, 16.0),
            ("裏返り (flipped): 会心で腹を見せて脚をばたつかせる (3 秒動けない)", "flipped", 0.10, 212, 16, 280, 16.0)):
        st = anim_pose(anim, t)
        for i in range(4):
            bgp = panel_bg(PW, hh)
            if i == 0:
                im = old_render(old_faces(st), yaw, pitch, (PW, hh), sc)
                bgp = im.convert("RGB")
            else:
                r = results[i - 1]
                im = new_render(r, pose_faces(r["faces"], st), yaw, pitch, (PW, hh), sc * r['mod'].VIEW.get('k', 1.0))
                bgp.paste(im, (0, 0), im)
            canvas.paste(bgp, (xs[i], y))
            frame(xs[i], y, PW, hh)
        d.text((X0, y + hh + 4), label, font=F(15), fill=WH)
        y += hh + 28
    # ---- アトラスと説明
    y += 6
    tx = Image.open(ASSETS / "textures/entity/reef_scuttler.png").convert("RGBA")
    bgc = Image.new("RGBA", tx.size, (40, 40, 52, 255))
    bgc.alpha_composite(tx)
    canvas.paste(bgc.convert("RGB"), (xs[0], y))
    d.text((xs[0], y + tx.height + 4), f"今のテクスチャ {tx.width}×{tx.height}", font=F(14), fill=WH)
    yy = y + tx.height + 30
    for t in (f"今: 箱 {n_old} 個", "新: 三角形 ≤ 約 600 / 1 ブロック = 32 テクセル", "骨: root(Y 180 度) > body >", "   leg_r / leg_l / claw_r / claw_l",
              "目の光 = glowmask (シアンの画素だけ)"):
        d.text((xs[0], yy), t, font=F(13), fill=GR)
        yy += 19
    bot = yy
    for i, r in enumerate(results):
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        rowsused = int(np.nonzero(r["atlas"][..., 3].any(1))[0].max()) + 2
        asz = 3 * r["size"]
        x = xs[i + 1]
        canvas.paste(bgc.convert("RGB").crop((0, 0, r["size"], rowsused)).resize((asz, 3 * rowsused), Image.NEAREST), (x, y))
        frame(x, y, asz, 3 * rowsused)
        yy = y + 3 * rowsused + 6
        d.text((x, yy), f"{r['label']} のアトラス {r['size']}×{r['size']} (3 倍)", font=F(14), fill=COL[r["key"]])
        per = r["data"]["bones"]
        pt = lambda k: per[k]["triangle_count"]
        lines = [f"合計 {r['data']['triangle_count']} 三角形 / 光る画素 {int(r['glow'].sum())}",
                 f"体 {pt('body')} / 脚 {pt('leg_r')}+{pt('leg_l')} / 鋏 {pt('claw_r')}+{pt('claw_l')}"]
        yy += 24
        for t in lines:
            d.text((x, yy), t, font=F(14), fill=GR)
            yy += 20
        for ln in wrap(d, DESC[r["key"]], F(14), PW):
            d.text((x, yy), ln, font=F(14), fill=GR)
            yy += 19
        bot = max(bot, yy)
    canvas = canvas.crop((0, 0, W, bot + 20))
    out = OUT / "reef_scuttler.png"
    canvas.save(out)
    print("saved", out, canvas.size)
    return results


if __name__ == "__main__":
    main()
