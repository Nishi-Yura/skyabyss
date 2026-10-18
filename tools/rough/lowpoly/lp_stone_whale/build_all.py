"""岩鯨 (lp_stone_whale) の低ポリ 3 案を作り、比較シートと manifest を書く。

実行: python tools/rough/lowpoly/lp_stone_whale/build_all.py
出力: docs/review/lowpoly_roughs/lp_stone_whale/{a,b,c}/{near,mid,far}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_stone_whale.png
      docs/review/lowpoly_roughs/lp_stone_whale_manifest.json
"""
from __future__ import annotations

import importlib
import json

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
F = voxel.font
CY, GR, WH = (255, 190, 110), (170, 164, 160), (240, 232, 220)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]
NAMES = {"a": "A 忠実な面取りの石の鯨", "b": "B 彫刻の記念碑", "c": "C 皮が岩になる鯨"}
DESC = {
    "a": ("今の姿 (太い青鯨型・背に二つのアーチと塔・骨の背骨・琥珀の目) を、斜めの面だけで刻んだ素直な低ポリ。"
          "青灰色の石の皮に地層の筋と苔のまだら、腹は砂色、顔は 2 段のまぶた + 縦の瞳の線 + 眉 + 唇の黒い帯、上顎の先にひげ板。"
          "背の遺跡 (崩れたアーチ 2・光る刻文の塔・折れた柱) と、わき腹の石の板・藤壺は今と同じ配置。"
          "最も今に近く、遠くから見ても輪郭が変わらない。背のダンジョンは今の形のまま使える。"),
    "b": ("胴の脇を地層のテラス (段) で刻み、全体を一つの古い彫刻にした案。温かい砂岩色の地層の帯に光る刻文の帯が巻き、"
          "背には三段の台に崩れた柱廊・階段・塔・アーチが載る、動く遺跡の都市。顔は彫った仮面 (四角い 2 段のまぶた・分厚い眉・唇に並ぶ石の歯)。"
          "最も背が高く、遠くからでも「建物を背負った鯨」と読める。背のダンジョンの形は大きく変わる (台と柱廊の分、背が盛り上がる)。"),
    "c": ("やわらかい鯨の体 (額のふくらみ・喉のひだ・長い胸びれ・切れ込みの尾びれ) の背から、石の殻が広がって皮を覆っていく案。"
          "下は青灰色の風化した皮、上は割れた茶色い岩。顔は丸みのあるまぶたと額のこぶ、あごに藤壺のような石の円錐。"
          "遺跡は半ば埋もれた傾くアーチと折れた柱だけで、生き物らしさが強い。最も三角形が多く (近景)、背のダンジョンは殻の起伏に合わせて作り直す。"),
}
SHORT = {"a": "A", "b": "B", "c": "C"}

cache = {}


# ------------------------------------------------------------------ 今のモデル (箱)

def cur_faces():
    if "cur" not in cache:
        cache["cur"] = voxel.load_geo(GEO / "stone_whale.geo.json", ASSETS / "textures/entity/stone_whale.png",
                                      ASSETS / "textures/entity/stone_whale_glowmask.png")
    return cache["cur"]


def cur_shot(yaw, pitch, size, center, scale):
    faces = cur_faces()
    cy_, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy_, 0, sy_], [0, 1, 0], [-sy_, 0, cy_]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    Vm = Rx @ Ry
    if "bbox" not in cache:
        cache["bbox"] = {}
    key = (yaw, pitch)
    if key not in cache["bbox"]:
        pts = np.array([Vm @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
        cache["bbox"][key] = (pts.min(0), pts.max(0))
    lo, hi = cache["bbox"][key]
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = int(math.ceil(scale * span))
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(S, S), margin=0.0, ss=1)
    pc = Vm @ np.asarray(center, float)
    cxs, cys = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = (pc[0] - cxs) * scale + S / 2
    py = -(pc[1] - cys) * scale + S / 2
    w, h = size
    pad = max(w, h)
    big = Image.new("RGB", (S + 2 * pad, S + 2 * pad), (14, 14, 20))
    big.paste(im.convert("RGB"), (pad, pad))
    x0, y0 = int(round(px - w / 2)) + pad, int(round(py - h / 2)) + pad
    return big.crop((x0, y0, x0 + w, y0 + h))


def new_shot(res, yaw, pitch, size, center, scale, sky=False):
    im = render(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
    bg = panel_bg(*size, sky).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


def panel_bg(w, h, sky=False):
    if sky:
        yy = np.linspace(0, 1, h)[:, None, None]
        top, bot = np.array([34, 44, 74.0]), np.array([92, 108, 140.0])
        return Image.fromarray(np.clip(np.broadcast_to(top * (1 - yy) + bot * yy, (h, w, 3)), 0, 255).astype(np.uint8))
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


# ------------------------------------------------------------------ 視点

VIEWS = {
    "side": dict(yaw=90, pitch=3, center=(0, 52, 14), scale=2.5, size=(780, 300)),
    "top": dict(yaw=90, pitch=90, center=(0, 50, 14), scale=2.5, size=(780, 360)),
    "below": dict(yaw=205, pitch=-26, center=(0, 40, -30), scale=3.1, size=(470, 360)),
    "face": dict(yaw=128, pitch=6, center=(0, 40, -108), scale=9.0, size=(460, 360)),
    "fintop": dict(yaw=90, pitch=90, center=(0, 30, -45), scale=1.35, size=(260, 200)),
    "finfront": dict(yaw=180, pitch=0, center=(0, 36, -45), scale=1.85, size=(260, 160)),
    "ground": dict(yaw=205, pitch=-34, center=(0, 52, 0), scale=0.95, size=(320, 360)),
}


def view_of(name, res=None):
    v = VIEWS[name]
    if res is None:
        return cur_shot(v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
    return new_shot(res, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"], sky=(name == "ground"))


def main(save=True):
    results = {}
    for opt in OPTS:
        results[opt["id"]] = {}
        for lod in (0, 1, 2):
            r = build_lod(opt, lod, OUTDIR / opt["id"], save=save)
            results[opt["id"]][lod] = r
            print(opt["id"], lod, r["tris"], "tris, atlas", r["size"], flush=True)
    n_box = {0: box_count("stone_whale"), 1: box_count("stone_whale_mid"), 2: box_count("stone_whale_far")}

    W = 1600
    H = 5000
    canvas = Image.new("RGB", (W, H), (16, 14, 18))
    d = ImageDraw.Draw(canvas)
    d.text((28, 12), "岩鯨 (stone_whale) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (第一の巨神)", font=F(26), fill=WH)
    d.text((28, 48), "骨の名前・階層・pivot は今の geo (body/head/jaw/tail/fin_left/fin_right/fluke/ruins/spine/chain、休めの回転なし) と同じ。全長 約 294 単位 (ゲームでは 約 147 ブロック)。", font=F(15), fill=GR)
    d.text((28, 70), "近 (<64 ブロック) / 中 (<160) / 遠 の 3 段階。テクスチャは近 2・中 1・遠 0.5 テクセル/単位 (= 4・2・1 テクセル/ブロック) の最近傍。左右は対称。", font=F(15), fill=GR)

    y = 104

    def frame(x, yy, w, h):
        d.rectangle([x - 1, yy - 1, x + w, yy + h], outline=(66, 56, 60))

    def cap(x, yy, text, col=GR, size=15):
        d.text((x, yy), text, font=F(size), fill=col)

    subjects = [("今 (箱のモデル)", None)] + [(NAMES[o["id"]], o["id"]) for o in OPTS]
    for label, oid in subjects:
        res = None if oid is None else results[oid][0]
        if oid is None:
            tl = f"箱 近 {n_box[0]:,} / 中 {n_box[1]:,} / 遠 {n_box[2]:,} 個 (近 = 三角形 {n_box[0] * 12:,} 相当)"
        else:
            tl = (f"三角形 近 {results[oid][0]['tris']:,} / 中 {results[oid][1]['tris']:,} / 遠 {results[oid][2]['tris']:,}"
                  f"  (アトラス {results[oid][0]['size']}² / {results[oid][1]['size']}² / {results[oid][2]['size']}²)")
        d.text((20, y), label, font=F(28), fill=CY if oid else WH)
        d.text((520, y + 8), tl, font=F(18), fill=WH)
        y += 44
        # row 1: side + top
        sv, tv = VIEWS["side"], VIEWS["top"]
        canvas.paste(view_of("side", res), (20, y)); frame(20, y, *sv["size"])
        canvas.paste(view_of("top", res), (20 + 780 + 20, y)); frame(20 + 800, y, *tv["size"])
        cap(26, y + 4, "横から (頭は左)")
        cap(26 + 800, y + 4, "真上から (頭は左)")
        y += tv["size"][1] + 14
        # row 2
        x = 20
        for nm, lab in (("below", "正面斜め下から (地上の見え方)"), ("face", "顔のアップ (目・まぶた・眉・唇・瞳の線)")):
            canvas.paste(view_of(nm, res), (x, y)); frame(x, y, *VIEWS[nm]["size"])
            cap(x + 6, y + 4, lab)
            x += VIEWS[nm]["size"][0] + 20
        # fin check
        ft, ff = VIEWS["fintop"], VIEWS["finfront"]
        a = view_of("fintop", res)
        b = view_of("finfront", res)
        canvas.paste(a, (x, y)); frame(x, y, *ft["size"])
        canvas.paste(b, (x, y + ft["size"][1] + 10)); frame(x, y + ft["size"][1] + 10, *ff["size"])
        d.line([(x + ft["size"][0] // 2, y), (x + ft["size"][0] // 2, y + ft["size"][1])], fill=(255, 80, 80), width=1)
        d.line([(x + ft["size"][0] // 2, y + ft["size"][1] + 10), (x + ft["size"][0] // 2, y + ft["size"][1] + 10 + ff["size"][1])], fill=(255, 80, 80), width=1)
        cap(x + 4, y + 2, "胸びれ 左右 (上・正面) 赤線 = 中心")
        x += ft["size"][0] + 20
        gv = VIEWS["ground"]
        canvas.paste(view_of("ground", res), (x, y)); frame(x, y, *gv["size"])
        cap(x + 4, y + 2, "地上 100 ブロック先を見上げる", WH)
        cap(x + 4, y + 20, "(画面の高さ約 1/4 の縮小)", GR, 13)
        y += VIEWS["below"]["size"][1] + 14
        # row 3: LOD (new only)
        if oid is not None:
            x = 20
            names = ["近 (<64 ブロック)", "中 (<160)", "遠 (それ以上)"]
            for lod in (0, 1, 2):
                r = results[oid][lod]
                im = new_shot(r, 90, 3, (520, 175), (0, 55, 14), 1.72)
                canvas.paste(im, (x, y)); frame(x, y, 520, 175)
                cap(x + 6, y + 4, f"{names[lod]}: {r['tris']:,} 三角形 / 箱 {n_box[lod]:,} 個 / {r['tex']:g} テクセル/単位", WH)
                x += 540
            y += 175 + 22
        else:
            y += 8
        y += 18

    # texel density test (A)
    d.text((20, y), "テクセル密度の提案 (A の顔。近景)", font=F(24), fill=WH)
    y += 36
    x = 20
    dens = [(1.0, "1 テクセル/単位 (2/ブロック)"), (2.0, "2 テクセル/単位 (4/ブロック) 【提案】"), (4.0, "4 テクセル/単位 (8/ブロック)")]
    optA = OPTS[0]
    for tx, lab in dens:
        if abs(tx - 2.0) < 1e-6:
            r = results["a"][0]
        else:
            r = build_lod(optA, 0, save=False, tex=tx)
        v = VIEWS["face"]
        im = new_shot(r, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
        canvas.paste(im, (x, y)); frame(x, y, *v["size"])
        cap(x + 6, y + 4, f"{lab}: アトラス {r['size']}²", WH)
        x += v["size"][0] + 20
    lpkit.TEX = 2.0
    y += VIEWS["face"][1][1] if False else VIEWS["face"]["size"][1] + 22
    d.text((20, y), "アトラス (近景。そのまま表示・最近傍)", font=F(24), fill=WH)
    y += 36
    x = 20
    for o in OPTS:
        r = results[o["id"]][0]
        at = Image.fromarray(r["atlas"], "RGBA")
        bg = Image.new("RGBA", at.size, (40, 36, 44, 255))
        bg.alpha_composite(at)
        sm = bg.convert("RGB").resize((512, 512), Image.NEAREST if r["size"] <= 512 else Image.BOX)
        canvas.paste(sm, (x, y)); frame(x, y, 512, 512)
        cap(x + 4, y + 516, f"{NAMES[o['id']]} {r['size']}x{r['size']} (512 に縮小)", CY)
        x += 532
    y += 560
    canvas = canvas.crop((0, 0, W, min(H, y)))
    if save:
        canvas.save(OUT / "lp_stone_whale.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['tris']:,}・{results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}" for o in OPTS)
        manifest = {
            "id": "lp_stone_whale",
            "short": "岩鯨 低ポリ",
            "kind": "低ポリ 3案",
            "title": "岩鯨 — 低ポリ非立方体 + ドット絵 3 案 (第一の巨神。近・中・遠の 3 段階)",
            "sheets": ["lowpoly_roughs/lp_stone_whale.png"],
            "extra": ("統一モデル様式 (低ポリ + 低解像度のドット絵) の岩鯨。骨名・階層・pivot は今の geo と同じ (休めの回転なし) なので、今のアニメがそのまま動く。"
                      "全長 約 294 単位 (ゲームでは約 147 ブロック)。左右の胸びれは完全な鏡像 (今は左右でわずかに大きさが違う)。"
                      f"三角形 (近 / 中 / 遠): {tri}。今は箱 近 {n_box[0]:,} (三角形 約 {n_box[0] * 12:,} 相当) / 中 {n_box[1]:,} / 遠 {n_box[2]:,}。"
                      "予算案: 近 6,000 以下 / 中 2,000 以下 / 遠 500 以下 (3 案ともそれ以下)。"
                      "テクセル密度の提案: 近 2 テクセル/単位 (1 ブロック 4 テクセル、1 テクセルが 1/4 ブロック。普通のモブの 1/8 の細かさ)、中 1 (2/ブロック)、遠 0.5 (1/ブロック)。"
                      "近景はアトラス 1024² 1 枚で今の 2048x1058 の約半分、中 512²、遠 256²。遠くから見ても 1 テクセルが 0.15 度以下でドット絵のまま読める。"
                      "背中のダンジョンへの影響: 体の外形 (太さ・長さ・背の高さ) は 3 案とも今と同じ骨格の断面を使うので、鎖の錨石の位置 (+X のわき腹 z=60) と背の広場はそのまま。"
                      "ただし B は背の台・柱廊で背が最大 約 20 単位高くなり、C は背の殻の起伏と額・肩のふくらみで外形が少し変わるので、ダンジョンの外形は新しいメッシュを体積化して作り直す必要がある。"
                      "A は今のブロックの形のまま (色だけ今の格子の材料に合わせる) で済む。"
                      "メッシュは lowpoly_roughs/lp_stone_whale/{a,b,c}/{near,mid,far}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": NAMES[o["id"]], "desc": DESC[o["id"]]} for o in OPTS},
        }
        (OUT / "lp_stone_whale_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
