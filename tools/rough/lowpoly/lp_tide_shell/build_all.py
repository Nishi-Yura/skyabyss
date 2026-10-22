"""潮殻 (lp_tide_shell) の低ポリ 3 案を作り、比較シートと manifest を書く。

実行: python tools/rough/lowpoly/lp_tide_shell/build_all.py
出力: docs/review/lowpoly_roughs/lp_tide_shell/{a,b,c}/{near,mid,far}_{mesh.json,atlas.png,glowmask.png}
      docs/review/lowpoly_roughs/lp_tide_shell.png
      docs/review/lowpoly_roughs/lp_tide_shell_manifest.json
"""
from __future__ import annotations

import importlib
import json

from tcommon import *  # noqa

OUT = ROOT / "docs/review/lowpoly_roughs"
F = voxel.font
CY, GR, WH = (255, 190, 110), (170, 164, 160), (240, 232, 220)
OPTS = [importlib.import_module("topt_" + k).OPT for k in "abc"]
NAMES = {"a": "A 忠実な珊瑚礁の大亀", "b": "B 段になった珊瑚礁の島", "c": "C 生きた老亀"}
DESC = {
    "a": ("今の姿 (平たい丸い甲羅・縁の張り出し・頂の塔・珊瑚・左後ろの潮門・くさび形の頭と淡い嘴) を、斜めの面だけで素直に刻んだ低ポリ。"
          "青緑の甲羅に背 5・肋 4 組・縁 22 枚の鱗板の筋と成長の輪、縁の下は藻の緑、腹は砂色の板。顔は 2 段のまぶた + 眉 + 鼻の穴 + 嘴の稜線 + 口の線。"
          "頂に光る窓の塔、潮の広間の真上に光る井戸の石の輪、崩れたアーチと柱、甲羅の上に橙のまるい珊瑚・桃の枝・紫の管・黄のうちわ。"
          "最も今に近く、遠くから見ても輪郭が変わらない。背のダンジョン (甲羅のブロックの複製) は今の形のまま使える。"),
    "b": ("甲羅を四段の台地にし、縁を胸壁にした「動く遺跡の都市」。砂岩色の地層の帯と、段の壁に巻く光る刻文の帯。"
          "頂に高い塔、第三段に崩れた柱廊、段をつなぐ階段、段のふちに珊瑚の林。頭は四角く彫った仮面 (大きな眉・唇に並ぶ石の歯)。ひれは幅広い櫂。"
          "遠くからは「海に浮かぶ階段状の島」と読め、亀らしさは頭とひれに寄る。生き物らしさは最も弱い。"
          "甲羅の高さが 68 → 94 (頂の塔は 106 → 約 145) に伸びるので、背のダンジョンは段に合わせて作り直す (広間・庭・潮見の間が段ごとの部屋に置ける)。"),
    "c": ("柔らかい老いた海亀。背骨に沿う高い稜線、重なって盛り上がる六角の鱗板 (成長の輪つき)、割れて藻にまみれた皮のような殻、後ろがぎざぎざの縁。"
          "首は持ち上がってしわが寄り、重たいまぶたの目・額の眉・鼻の穴・鉤のくちばし・頬の藤壺。ひれは長く、爪がある。"
          "遺跡は半ば埋もれた傾く塔の残りとアーチ、折れた柱だけで、珊瑚と管は背骨の割れ目から生え、海藻が縁から垂れる。"
          "石の岩鯨 (C 皮が岩になる鯨) と同じく「生きている巨神」の方向。三角形は近景で最も多い。背の高さは鱗板の分 約 +5 (甲羅の頂 68 → 約 76)、頭は首ごと約 14 高い。"),
}
SHORT = {"a": "A", "b": "B", "c": "C"}
cache = {}


def cur_faces():
    if "cur" not in cache:
        cache["cur"] = voxel.load_geo(GEO / "tide_shell.geo.json", ASSETS / "textures/entity/tide_shell.png",
                                      ASSETS / "textures/entity/tide_shell_glowmask.png")
    return cache["cur"]


def cur_shot(yaw, pitch, size, center, scale):
    faces = cur_faces()
    Vm = rot_x(pitch) @ rot_y(yaw)
    key = (yaw, pitch)
    cache.setdefault("bbox", {})
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


def panel_bg(w, h, sky=False):
    if sky:
        yy = np.linspace(0, 1, h)[:, None, None]
        top, bot = np.array([40, 60, 96.0]), np.array([128, 156, 184.0])
        return Image.fromarray(np.clip(np.broadcast_to(top * (1 - yy) + bot * yy, (h, w, 3)), 0, 255).astype(np.uint8))
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def apply_water(bg, yaw, pitch, size, center, scale, water):
    """水面 (y = water) より下を水で隠す。pitch 0 の正射影なので水平の線になる"""
    Vm = rot_x(pitch) @ rot_y(yaw)
    c = Vm @ np.asarray(center, float)
    p = Vm @ np.array([0, water, 0.0])
    sy = int(round(-(p[1] - c[1]) * scale + size[1] / 2))
    sy = max(0, min(size[1], sy))
    arr = np.zeros((size[1] - sy, size[0], 4), np.uint8)
    t = np.linspace(0, 1, size[1] - sy)[:, None]
    arr[..., 0] = (34 - 18 * t)
    arr[..., 1] = (92 - 40 * t)
    arr[..., 2] = (120 - 40 * t)
    arr[..., 3] = 236
    arr[:2, :, :3] = (150, 200, 220)
    bg.alpha_composite(Image.fromarray(arr, "RGBA"), (0, sy))
    return bg


def new_shot(res, yaw, pitch, size, center, scale, sky=False, water=None):
    im = render(res["faces"], res["atlas"], res["glow"], yaw, pitch, size, center, scale)
    bg = panel_bg(*size, sky).convert("RGBA")
    bg.alpha_composite(im)
    if water is not None:
        bg = apply_water(bg, yaw, pitch, size, center, scale, water)
    return bg.convert("RGB")


FACE_C = {"a": (0, 28, -136), "b": (0, 28, -136), "c": (0, 38, -136)}
VIEWS = {
    "side": dict(yaw=90, pitch=3, center=(0, 56, -26), scale=2.5, size=(800, 340)),
    "below": dict(yaw=205, pitch=-24, center=(0, 40, -26), scale=2.4, size=(740, 340)),
    "top": dict(yaw=90, pitch=90, center=(0, 50, -26), scale=1.8, size=(580, 560)),
    "face": dict(yaw=125, pitch=7, center=(0, 28, -140), scale=6.6, size=(520, 560)),
    "ground": dict(yaw=148, pitch=0, center=(0, 60, -26), scale=1.4, size=(440, 560)),
}


def view_of(name, res=None, oid=None):
    v = dict(VIEWS[name])
    if name == "face" and oid:
        v["center"] = FACE_C[oid]
    if res is None:
        if name == "ground":
            im = cur_shot(v["yaw"], v["pitch"], v["size"], v["center"], v["scale"]).convert("RGBA")
            return apply_water(im, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"], WATER).convert("RGB")
        return cur_shot(v["yaw"], v["pitch"], v["size"], v["center"], v["scale"])
    return new_shot(res, v["yaw"], v["pitch"], v["size"], v["center"], v["scale"], sky=(name == "ground"),
                    water=WATER if name == "ground" else None)


def main(save=True):
    results = {}
    for opt in OPTS:
        results[opt["id"]] = {}
        for lod in (0, 1, 2):
            r = build_lod(opt, lod, OUTDIR / opt["id"], save=save)
            results[opt["id"]][lod] = r
            print(opt["id"], lod, r["tris"], "tris, atlas", r["size"], r["by_bone"], flush=True)
    n_box = {0: box_count("tide_shell"), 1: box_count("tide_shell_mid"), 2: box_count("tide_shell_far")}

    W, H = 1600, 6400
    canvas = Image.new("RGB", (W, H), (16, 14, 18))
    d = ImageDraw.Draw(canvas)
    d.text((28, 12), "潮殻 (tide_shell) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (第二の巨神)", font=F(26), fill=WH)
    d.text((28, 48), "骨の名前・階層・pivot は今の geo (body/ruins/gate/coral/kelp/head/tail/fin_front_*/fin_rear_*、休めの回転なし) と同じ。全長 約 300 単位 (頭の先から尾まで。ゲームでは約 150 ブロック)、ひれの先から先まで約 300。", font=F(15), fill=GR)
    d.text((28, 70), "近 (<64 ブロック) / 中 (<160) / 遠 の 3 段階。テクスチャは近 2・中 1・遠 0.5 テクセル/単位 (= 4・2・1 テクセル/ブロック) の最近傍。左右は対称 (真上の図の赤線 = 中心)。", font=F(15), fill=GR)
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
        # row 1: side + below
        x = 20
        for nm, lab in (("side", "横から (頭は左)"), ("below", "斜め下から (地上・水中からの見え方)")):
            canvas.paste(view_of(nm, res, oid), (x, y)); frame(x, y, *VIEWS[nm]["size"])
            cap(x + 6, y + 4, lab)
            x += VIEWS[nm]["size"][0] + 20
        y += VIEWS["side"]["size"][1] + 14
        # row 2: top, face, ground
        x = 20
        for nm, lab in (("top", "真上から (頭は左・赤線 = 左右の中心)"), ("face", "顔のアップ (目・まぶた・眉・鼻・嘴・口)"), ("ground", "海岸から見る (水面 = 巡航の高さ)")):
            canvas.paste(view_of(nm, res, oid), (x, y)); frame(x, y, *VIEWS[nm]["size"])
            cap(x + 6, y + 4, lab, WH if nm == "ground" else GR)
            if nm == "top":
                # 赤線: 中心 (x = 0) は画面の縦中央
                cy_ = y + VIEWS["top"]["size"][1] // 2
                d.line([(x, cy_), (x + VIEWS["top"]["size"][0], cy_)], fill=(255, 80, 80), width=1)
            x += VIEWS[nm]["size"][0] + 20
        y += VIEWS["top"]["size"][1] + 14
        # row 3: LOD (new only)
        if oid is not None:
            x = 20
            names = ["近 (<64 ブロック)", "中 (<160)", "遠 (それ以上)"]
            for lod in (0, 1, 2):
                r = results[oid][lod]
                im = new_shot(r, 90, 3, (520, 190), (0, 56, -26), 1.62)
                canvas.paste(im, (x, y)); frame(x, y, 520, 190)
                cap(x + 6, y + 4, f"{names[lod]}: {r['tris']:,} 三角形 / 箱 {n_box[lod]:,} 個 / {r['tex']:g} テクセル/単位", WH)
                x += 540
            y += 190 + 22
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
        canvas.save(OUT / "lp_tide_shell.png")
        print("saved sheet", canvas.size)
        tri = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['tris']:,}・{results[o['id']][1]['tris']:,}・{results[o['id']][2]['tris']:,}" for o in OPTS)
        atl = " / ".join(f"{SHORT[o['id']]} {results[o['id']][0]['size']}²・{results[o['id']][1]['size']}²・{results[o['id']][2]['size']}²" for o in OPTS)
        manifest = {
            "id": "lp_tide_shell",
            "short": "潮殻 低ポリ",
            "kind": "低ポリ 3案",
            "title": "潮殻の巨神 — 低ポリ非立方体 + ドット絵 3 案 (第二の巨神。近・中・遠の 3 段階)",
            "sheets": ["lowpoly_roughs/lp_tide_shell.png"],
            "extra": ("統一モデル様式 (低ポリ + 低解像度のドット絵) の潮殻。骨名・階層・pivot は今の geo と同じ (休めの回転なし) なので、今のアニメがそのまま動く。"
                      "全長 約 300 単位 (頭の先から尾まで。ゲームでは約 150 ブロック)、ひれの先から先まで約 300。左右のひれは完全な鏡像 (今は左右で高さが違う)。"
                      f"三角形 (近 / 中 / 遠): {tri}。今は箱 近 {n_box[0]:,} (三角形 約 {n_box[0] * 12:,} 相当) / 中 {n_box[1]:,} / 遠 {n_box[2]:,}。"
                      "予算案: 近 6,000 以下 / 中 2,000 以下 / 遠 500 以下 (3 案ともそれ以下)。"
                      "テクスチャ密度の提案: 近 2 テクセル/単位 (1 ブロック 4 テクセル、1 テクセルが 1/4 ブロック)、中 1 (2/ブロック)、遠 0.5 (1/ブロック)。"
                      f"アトラス (近 / 中 / 遠): {atl}。今は 2048x2674 の 1 枚。"
                      "【背のダンジョンへの影響】背のディメンション (tide_back) は外見のブロックの複製 (1.7 倍。甲羅 長さ 170・幅 143・高さ 53、頂 y114、頭と首は z -82〜-148) なので、"
                      "どの案を選んでも外形が変わった分だけ複製 (tools/gen/mobs/gen_tide_back_turtle.py 系) を作り直す必要がある。"
                      "A: 甲羅の輪郭・高さ (頂 68〜70。頂の塔は約 110 で今の 106 とほぼ同じ)・潮門 (左後ろの縁)・頭と首の位置は今と同じ範囲に収めてあるので、複製はほぼそのまま (色の材料を甲羅の筋・縁の緑に合わせる程度) で、試し済みの部屋の配置 (庭・三段の溜め・干潟・広間・心臓の間) も動かさずに済む。"
                      "B: 甲羅が四段の台地になり高さ 68 → 94 (頂の塔 約 145)。背の複製 (1 単位 = 0.85 ブロック) では甲羅が約 +22 ブロック高くなる。段ごとに洞を取れるので部屋は増やせるが、ダンジョンの外殻は作り直し、頂の塔の高さ・階段の位置の合意が要る。柱廊・塔の内部を広間として使える。"
                      "C: 甲羅は背骨の稜線 (+9) と鱗板 (+5) で最大 約 76 (複製で約 +7 ブロック)、縁の後ろがぎざぎざ、首が約 14 高く (複製で約 +12 ブロック。頭の窓の位置も上がる)、鱗板の割れ目が洞の天井を波打たせる。複製は殻の起伏に合わせて作り直し、潮見の間の箱の角の殻の厚み (今は 3 段) を再確認する。"
                      "どの案でも: 腹の板 (y 5)・縁の張り出し (±100)・潮門の位置 (x 62〜76, z 53〜75)・頭の窓 (北西・北東の角) は共通。窓から見える首と頭の形は C が最も変わる。"
                      "メッシュは lowpoly_roughs/lp_tide_shell/{a,b,c}/{near,mid,far}_mesh.json (+ atlas・glowmask)。"),
            "options": {o["id"]: {"name": NAMES[o["id"]], "desc": DESC[o["id"]]} for o in OPTS},
        }
        (OUT / "lp_tide_shell_manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    return results, canvas


if __name__ == "__main__":
    main()
