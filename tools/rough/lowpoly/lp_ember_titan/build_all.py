"""余燼 (lp_ember_titan) の低ポリ 3 案を作り、比較シートと manifest を書く。

実行: python tools/rough/lowpoly/lp_ember_titan/build_all.py [--nosave]
出力: docs/review/lowpoly_roughs/lp_ember_titan/{a,b,c}/{near,mid,far}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_ember_titan.png
      docs/review/lowpoly_roughs/lp_ember_titan_manifest.json
"""
from __future__ import annotations

import importlib
import json
import sys

from common import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
F = voxel.font
CY, GR, WH = (255, 190, 110), (170, 164, 160), (240, 232, 220)
OPTS = [importlib.import_module("opt_" + k).OPT for k in "abc"]
NAMES = {"a": "A 忠実な面取りの火山", "b": "B 玄武岩の柱と大炉の記念碑", "c": "C 皮が岩になる火蜥蜴"}
SHORT = {"a": "A", "b": "B", "c": "C"}
DESC = {
    "a": ("今の姿 (低く平たい火蜥蜴・背の火山・頭の後ろの角 2 本・目の縁・溶岩の継ぎ目) を、斜めの面だけで刻んだ素直な低ポリ。"
          "玄武岩の板を重ねた黒い体に、板と板のあいだと火山の斜面だけが赤く光る。火口は灰色の帽子と赤茶の縁、背骨ぞいに溶岩の細い流れと棘の列。"
          "顔は幅広の平たい頭に、光る目の縁・眉の板・上下に並ぶ牙・後ろに反る 2 本の角 (先が光る)。最も今に近く、遠くからでも輪郭が変わらない。"
          "背のダンジョンは今のブロックの形のまま使える。"),
    "b": ("全身を段 (テラス) と柱状節理の玄武岩で刻んだ、動く鍛冶場の記念碑。体の脇は二段に刻まれ、段の上に六角の玄武岩の柱が並ぶ。"
          "背の火山は四段の火口の段丘 (ジグラット) で、段ごとに溶岩の溝が流れ、隅に灰の民の煉瓦の炉小屋が載る。"
          "火山のうしろには煉瓦の煙突と銅の鞴が並び、溶岩はまっすぐな溝を流れる。足は積んだ石、顔は四角い仮面と柱の冠、角は銅の帯のある太い柱 (先が光る)。"
          "最も人工物らしく、遠くからでも「炉を背負った遺跡」と読める。背のダンジョンは体の上半分の脇が内側に下がる分だけ外形が変わる。"),
    "c": ("生きた火蜥蜴の体 (丸い腹・頬のふくらみ・肩と腰のもりあがり・関節と長い指のある足・まぶたのある目) の背から、冷えかけた玄武岩の殻が広がって皮を覆っていく案。"
          "下は灰茶色の皮で、割れ目の下に溶岩が脈打つ (火山のまわり・尾の先・腹で特に熱い)。上は黒い殻の板が重なり、背骨に棘が並ぶ。"
          "火山は殻を破ってふくらむ傷口のようで、縁から溶岩がたれる。角は後ろへ反る骨の角 (先が光る)、頬にとげ。最も生き物らしく、最も三角形が多い。"
          "背のダンジョンは殻の起伏・丸い断面に合わせて外形を作り直す。"),
}

cache = {}


# ------------------------------------------------------------------ 今のモデル (箱)

def cur_faces():
    if "cur" not in cache:
        cache["cur"] = voxel.load_geo(GEO / "ember_titan.geo.json", ASSETS / "textures/entity/ember_titan.png",
                                      ASSETS / "textures/entity/ember_titan_glowmask.png")
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


def panel_bg(w, h, ground=None):
    if ground is not None:
        # 夜の空と、灰の原 (地面)。ground = 地面の上端の高さ (画素)
        yy = np.linspace(0, 1, h)[:, None, None]
        top, bot = np.array([22, 18, 34.0]), np.array([84, 52, 56.0])
        arr = np.broadcast_to(top * (1 - yy) + bot * yy, (h, w, 3)).copy()
        gy = int(ground)
        arr[gy:] = np.array([34, 30, 32.0]) + 6 * np.linspace(0, 1, h - gy)[:, None, None]
        return Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8))
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def new_shot(res, yaw, pitch, size, center, scale, ground=False):
    im = render_ember(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
    g = None
    if ground:
        Vm = rot_x(pitch) @ rot_y(yaw)
        cen = Vm @ np.asarray(center, float)
        # 足もと (y=0) の画面の高さ
        pts = [Vm @ V(x, 0, z) for x in (-100, 100) for z in (-208, 248)]
        g = max(-(p[1] - cen[1]) * scale + size[1] / 2 for p in pts) - 4
        g = min(max(g, 0), size[1] - 10)
    bg = panel_bg(*size, g).convert("RGBA")
    bg.alpha_composite(im)
    return bg.convert("RGB")


# ------------------------------------------------------------------ 視点

VIEWS = {
    "side": dict(yaw=90, pitch=3, center=(0, 70, 20), scale=1.7, size=(780, 330)),
    "top": dict(yaw=90, pitch=90, center=(0, 60, 20), scale=1.7, size=(780, 370)),
    "above": dict(yaw=140, pitch=28, center=(0, 62, 18), scale=1.5, size=(520, 340)),
    "below": dict(yaw=205, pitch=-26, center=(0, 52, 10), scale=1.45, size=(520, 340)),
    "face": dict(yaw=160, pitch=12, center=(0, 30, -184), scale=6.2, size=(520, 340)),
    "symfront": dict(yaw=180, pitch=4, center=(0, 66, 0), scale=2.2, size=(340, 300)),
    "symtop": dict(yaw=90, pitch=90, center=(0, 60, -118), scale=2.4, size=(340, 300)),
    "ground": dict(yaw=148, pitch=3, center=(0, 56, 20), scale=0.98, size=(520, 300)),
}


def view_of(name, res=None):
    v = VIEWS[name]
    if res is None:
        return cur_shot(v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
    return new_shot(res, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"], ground=(name == "ground"))


def hull_area_ratio(opt):
    """型の断面積 / 長方形 (今の箱の外形の断面)"""
    from parts import KEYS  # noqa
    mod = importlib.import_module("opt_" + opt["id"])
    t = mod.TMPL[0]
    a = 0.0
    for (u0, t0), (u1, t1) in zip(t, t[1:]):
        a += (u0 + u1) / 2 * (t1 - t0)
    return a


def main(save=True):
    results = {}
    for opt in OPTS:
        results[opt["id"]] = {}
        for lod in (0, 1, 2):
            r = build_lod(opt, lod, OUTDIR / opt["id"], save=save)
            results[opt["id"]][lod] = r
            print(opt["id"], lod, r["tris"], "tris, atlas", r["size"], flush=True)
    n_box = {0: box_count("ember_titan"), 1: box_count("ember_titan_mid"), 2: box_count("ember_titan_far")}

    W = 1600
    H = 6400
    canvas = Image.new("RGB", (W, H), (16, 12, 14))
    d = ImageDraw.Draw(canvas)
    d.text((28, 12), "余燼 (ember_titan) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (第四の巨神)", font=F(26), fill=WH)
    d.text((28, 48), "骨の名前・階層・pivot は今の geo (body/ridge/head/jaw/leg_fl/fr/rl/rr/tail/smoke_0..3、休めの回転なし) と同じ。全長 約 456 単位 (ゲームでは 約 228 ブロック)、背の火山の頂まで 約 70 ブロック。", font=F(15), fill=GR)
    d.text((28, 70), "近 / 中 / 遠 の 3 段階。テクスチャは近 2・中 1・遠 0.5 テクセル/単位 (= 4・2・1 テクセル/ブロック) の最近傍。左右は鏡像 (尾の曲がり・火山・煙は除く。ずれは最大 4 単位未満)。光る画素 (溶岩・目・角の先) は 3 段階とも残す。", font=F(15), fill=GR)

    y = 104

    def frame(x, yy, w, h):
        d.rectangle([x - 1, yy - 1, x + w, yy + h], outline=(70, 52, 52))

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
        sv, tv = VIEWS["side"], VIEWS["top"]
        canvas.paste(view_of("side", res), (20, y)); frame(20, y, *sv["size"])
        canvas.paste(view_of("top", res), (20 + 800, y)); frame(20 + 800, y, *tv["size"])
        cap(26, y + 4, "横から (頭は左)")
        cap(26 + 800, y + 4, "真上から (頭は左)")
        y += tv["size"][1] + 14
        x = 20
        for nm, lab in (("above", "斜め上から (3/4 の上)"), ("below", "斜め下から (3/4 の下。腹・足の裏)"), ("face", "顔のアップ (目の縁・眉・牙・角)")):
            canvas.paste(view_of(nm, res), (x, y)); frame(x, y, *VIEWS[nm]["size"])
            cap(x + 6, y + 4, lab)
            x += VIEWS[nm]["size"][0] + 20
        y += VIEWS["above"]["size"][1] + 14
        x = 20
        sf, st = VIEWS["symfront"], VIEWS["symtop"]
        a = view_of("symfront", res)
        canvas.paste(a, (x, y)); frame(x, y, *sf["size"])
        d.line([(x + sf["size"][0] // 2, y), (x + sf["size"][0] // 2, y + sf["size"][1])], fill=(255, 80, 80), width=1)
        cap(x + 4, y + 2, "正面 左右の確認 (赤線 = 中心)")
        x += sf["size"][0] + 20
        b = view_of("symtop", res)
        canvas.paste(b, (x, y)); frame(x, y, *st["size"])
        d.line([(x, y + st["size"][1] // 2), (x + st["size"][0], y + st["size"][1] // 2)], fill=(255, 80, 80), width=1)
        cap(x + 4, y + 2, "頭と胸を真上から (赤線 = 中心)")
        x += st["size"][0] + 20
        gv = VIEWS["ground"]
        canvas.paste(view_of("ground", res), (x, y)); frame(x, y, *gv["size"])
        cap(x + 4, y + 2, "地上から 100 ブロック先を見る", WH)
        cap(x + 4, y + 20, "(夜・灰の原。画面に収まるよう縮小)", GR, 13)
        y += gv["size"][1] + 14
        if oid is not None:
            x = 20
            names = ["近", "中", "遠"]
            for lod in (0, 1, 2):
                r = results[oid][lod]
                im = new_shot(r, 90, 3, (520, 215), (0, 66, 20), 1.1)
                canvas.paste(im, (x, y)); frame(x, y, 520, 215)
                cap(x + 6, y + 4, f"{names[lod]}: {r['tris']:,} 三角形 / 箱 {n_box[lod]:,} 個 / {r['tex']:g} テクセル/単位", WH)
                x += 540
            y += 215 + 22
        else:
            y += 8
        y += 18

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
        canvas.save(OUT / "lp_ember_titan.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['tris']:,}・{results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}" for o in OPTS)
        ratios = " / ".join(f"{SHORT[o['id']]} {hull_area_ratio(o) * 100:.0f}%" for o in OPTS)
        manifest = {
            "id": "lp_ember_titan",
            "short": "余燼 低ポリ",
            "kind": "低ポリ 3案",
            "title": "余燼 — 低ポリ非立方体 + ドット絵 3 案 (第四の巨神。近・中・遠の 3 段階)",
            "sheets": ["lowpoly_roughs/lp_ember_titan.png"],
            "extra": ("統一モデル様式 (低ポリ + 低解像度のドット絵) の余燼。骨名・階層・pivot は今の geo と同じ (休めの回転なし。近・中・遠の geo も同じ骨) なので、今のアニメ (swim・sleep・breath_in/out・smoke) がそのまま動く。"
                      "全長 約 456 単位 (ゲームでは約 228 ブロック)、胴の幅 112 単位 (脚を入れて 208)、背の火山の頂は地面から 約 70 ブロック。左右は鏡像 (今と同じく尾は -X へ曲がる。火山と煙を除く頭・胴・足・角・散らした板のずれは最大 4 単位 = 2 ブロック未満)。"
                      f"三角形 (近 / 中 / 遠): {tri}。今は箱 近 {n_box[0]:,} (三角形 約 {n_box[0] * 12:,} 相当) / 中 {n_box[1]:,} / 遠 {n_box[2]:,}。"
                      "予算案: 近 6,000 以下 / 中 2,000 以下 / 遠 500 以下 (3 案ともそれ以下)。"
                      "テクセル密度の提案: 近 2 テクセル/単位 (1 ブロック 4 テクセル、普通のモブの 1/8 の細かさ)、中 1 (2/ブロック)、遠 0.5 (1/ブロック)。近景のアトラスは 3 案とも 1024² 1 枚 (今は 2048x2946)。"
                      "岩鯨の C (皮が岩になる鯨) の選び方に合わせ、C は生き物らしさを最優先 (皮・関節・指・まぶた)、A は今に忠実、B は人工物 (記念碑) の 3 方向。"
                      "背中 (余燼の背) への影響: 体の中にダンジョンがあるので、体の外形は『外側 5 ブロックの殻』としてそのままブロックで写す (tools/gen/items/gen_ember_back_body.py が体の格子から作る。1.7 倍・頭は北 z -175、尾の先 z 210)。"
                      "火山 (火口の縁 y 172) は炉心の広間 (HUB: 火口の真下、y 85〜103) の真上なので、3 案とも火山の位置 (体の中心から 46 単位前・底の半径 56 単位・頂 140 単位) は変えていない。"
                      "口の中 (到着点) は顎の板 (高さ 7.6 単位) が地面に下りた形のまま。"
                      f"胴の断面積 (今の箱の外形を 100% として): {ratios}。"
                      "A は面取りで角が少し減るだけなので今の部屋の並び (広間の南に西・中・東の 3 室) のまま使える。"
                      "B は胴の上半分の脇が約 20% (幅 ±56 → ±45 単位、背中では約 9 ブロック) 内側へ下がる段になり、火山は四段の垂直の壁になる。胴の脇の部屋 (灰の道・鍛えの間) の天井を、段の高さ (背中で y 約 98) 以下に収めるか、上の段の外壁を厚く作り直す必要がある。外側の段・柱は建設時間の整理 (3.9) と相性がよい (大きな直方体で済む)。"
                      "C は断面が丸く、背の上の幅が狭くなる (上端で幅 約 45%、脇の最大幅は変わらない)。脇の部屋は天井が低い所を逃がすか、部屋を内側へ寄せる。火山の根元は皮のふくらみ (傾いた円錐 + 縁のたれ) で、殻を破る形に合わせて火口の縁の位置が 3〜5 単位ずれる。ブロック化は殻の板の起伏の分だけ面倒 (面の細かい凹凸は 1 ブロックの段で丸める)。"
                      "どの案でも、体の内側の壁は今どおり肉と骨の壁で埋めるので、外形の違いは『殻の厚さ』と『屋根の高さ』にしか出ない。"
                      "メッシュは lowpoly_roughs/lp_ember_titan/{a,b,c}/{near,mid,far}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": NAMES[o["id"]], "desc": DESC[o["id"]]} for o in OPTS},
        }
        (OUT / "lp_ember_titan_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main(save="--nosave" not in sys.argv)
