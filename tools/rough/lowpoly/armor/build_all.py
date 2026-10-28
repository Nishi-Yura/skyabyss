"""4 巨神 x 3 案 (+ ★ の案の階級・強化) を作り、メッシュ・アトラス・glowmask・シート・manifest を出す。
実行: python build_all.py  (docs/review/lowpoly_roughs/armor/ と armor.png / armor_manifest.json を書く)"""
from __future__ import annotations

import importlib
import json
import sys
from concurrent.futures import ProcessPoolExecutor

from PIL import Image, ImageDraw, ImageFont

import aview
from common import TITANS, TITAN_JP, SETS
from akit import ROOT, OUT

STAR = "c"                      # 階級・強化を見せる案
RARITY_SET = "whale"            # 階級の行に使う巨神
MODS = {"a": "style_a", "b": "style_b", "c": "style_c"}
NAMES = {"a": "A 外殻", "b": "B 衣と環", "c": "C 骨組み"}
TW, TH = 128, 200               # タイル
SCALE = 4.5
VIEWS = [("正面", 180, 6), ("背面", 0, 6), ("3/4", 215, 16)]
VIEWS_Z = [("正面", 180, 6), ("3/4", 215, 16)]
ZW, ZH, ZS = 210, 310, 7.0
PLAT = (30, 34, 42)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def mod_of(o):
    return importlib.import_module(MODS[o])


def tiles_of(res, T, views=VIEWS, tw=TW, th=TH, scale=SCALE, center=(0, 18.5, 0)):
    return [aview.render(res, T, yaw, pitch, (tw, th), scale, center=center) for _, yaw, pitch in views]


def current_tiles(T):
    """今のボクセルの防具 (armor_kit の描画)。倍率をそろえるため、見えない面で範囲を固定する。"""
    sys.path.insert(0, str(ROOT / "tools/rough"))
    import numpy as np
    import armor_kit as ak
    import voxel
    from armor_v2_common import load_current
    geo, tex, glow = load_current(SETS[T] + "_armor")
    out = []
    S = 209
    for _, yaw, pitch in VIEWS:
        fs = ak.faces_of(geo, tex, glow, ak.POSES["stand"])
        mg, mt = ak.mannequin()
        fs = fs + voxel.load_geo(mg, mt, pose=ak.POSES["stand"])
        dummy = voxel.Face(np.array([-14.5, -0.5, 0.0]), np.array([29.0, 0, 0]), np.array([0, 39.0, 0]),
                           np.zeros((1, 1, 4), np.uint8), None, 1.0)
        fs.append(dummy)
        im = voxel.render(fs, yaw=-yaw, pitch=pitch, size=(S, S))
        im = im.transpose(Image.FLIP_LEFT_RIGHT).convert("RGBA")
        bg = Image.new("RGBA", im.size, PLAT + (255,))
        bg.alpha_composite(im)
        out.append(bg.convert("RGB").crop((40, 7, 40 + TW, 7 + TH)))
    return out, sum(1 for f in fs if True)


def count_cubes(T):
    sys.path.insert(0, str(ROOT / "tools/rough"))
    from armor_v2_common import load_current
    geo, tex, glow = load_current(SETS[T] + "_armor")
    n = 0
    for b in geo["minecraft:geometry"][0]["bones"]:
        n += len(b.get("cubes", []))
    return n


def worker_titan(T):
    aview.SS = 2
    out = {"T": T, "tiles": {}, "tris": {}, "pieces": {}, "zoom": {}}
    out["tiles"]["cur"], _ = current_tiles(T)
    out["cubes"] = count_cubes(T)
    for o in "abc":
        res = aview.build_set(mod_of(o).build(T, "common"), T, "common")
        aview.save_set(res, OUT / o / SETS[T], SETS[T])
        out["tiles"][o] = tiles_of(res, T)
        out["zoom"][o] = tiles_of(res, T, VIEWS_Z, ZW, ZH, ZS)
        out["tris"][o] = aview.set_tris(res)
        out["pieces"][o] = {k: v["tris"] for k, v in res.items()}
        if o == STAR:
            for rar in ("rare", "legend"):
                r2 = aview.build_set(mod_of(o).build(T, rar), T, rar)
                aview.save_set(r2, OUT / o / SETS[T], SETS[T], suffix=f"_{rar}")
                out["tris"][f"{o}_{rar}"] = aview.set_tris(r2)
                out["pieces"][f"{o}_{rar}"] = {k: v["tris"] for k, v in r2.items()}
                for plus in (5, 10):
                    r3 = aview.build_set(mod_of(o).build(T, rar), T, rar, plus)
                    aview.save_set(r3, OUT / o / SETS[T], SETS[T], suffix=f"_{rar}_plus{plus}", mesh=False)
    return out


def worker_rarity(T):
    aview.SS = 2
    mod = mod_of(STAR)
    tiles, tris, zoom = [], {}, []
    for rar, plus in (("common", 0), ("rare", 0), ("legend", 0), ("legend", 10)):
        res = aview.build_set(mod.build(T, rar), T, rar, plus)
        tiles += tiles_of(res, T)
        if plus == 0:
            zoom += tiles_of(res, T, VIEWS_Z, ZW, ZH, ZS)
        tris[f"{rar}+{plus}"] = aview.set_tris(res)
    # 大きく見る (3/4 と背) も作る
    return {"tiles": tiles, "tris": tris, "zoom": zoom}


def zoom_sheet(results, rar):
    LABW = 70
    W = LABW + 6 * ZW + 10
    rows = []
    for T in TITANS:
        r = results[T]
        im = Image.new("RGB", (W, ZH + 30), (24, 28, 36))
        dr = ImageDraw.Draw(im)
        label(dr, (6, 6), TITAN_JP[T], 20, (255, 232, 160))
        label(dr, (6, 34), SETS[T], 11, (160, 168, 180))
        x = LABW
        for o in "abc":
            for i, t in enumerate(r["zoom"][o]):
                im.paste(t, (x + i * ZW, 2))
            label(dr, (x + 4, ZH + 6), f"{NAMES[o]}  三角形 {r['tris'][o]}" + ("  ★" if o == STAR else ""), 15,
                  (255, 232, 160) if o == STAR else (230, 232, 238))
            x += 2 * ZW
        rows.append(im)
    im = Image.new("RGB", (W, ZH + 30), (24, 28, 36))
    dr = ImageDraw.Draw(im)
    label(dr, (6, 6), "階級", 20, (255, 232, 160))
    label(dr, (6, 34), f"{NAMES[STAR]} {TITAN_JP[RARITY_SET]}", 11, (160, 168, 180))
    x = LABW
    for k, (nm, key) in enumerate((("通常", "common+0"), ("レア", "rare+0"), ("伝説", "legend+0"))):
        for i in range(2):
            im.paste(rar["zoom"][k * 2 + i], (x + i * ZW, 2))
        label(dr, (x + 4, ZH + 6), f"{nm}  三角形 {rar['tris'][key]}", 15)
        x += 2 * ZW
    rows.append(im)
    H = sum(r.size[1] for r in rows) + 40
    S = Image.new("RGB", (W, H), (24, 28, 36))
    dr = ImageDraw.Draw(S)
    label(dr, (10, 8), "防具の低ポリ 3 案 (拡大: 各案の正面と 3/4。一番下が ★ の C の階級)", 20, (255, 255, 255))
    y = 40
    for r in rows:
        S.paste(r, (0, y))
        y += r.size[1]
    S.save(ROOT / "docs/review/lowpoly_roughs/armor_zoom.png")
    print("zoom sheet", S.size)


def label(dr, xy, text, sz=15, fill=(230, 232, 238)):
    dr.text(xy, text, font=font(sz), fill=fill)


def sheet(results, rar):
    LABW = 70
    gap = 8
    colw = 3 * TW
    W = LABW + 4 * colw + 3 * gap + 12
    rows = []
    for T in TITANS:
        r = results[T]
        h = TH + 44
        im = Image.new("RGB", (W, h), (24, 28, 36))
        dr = ImageDraw.Draw(im)
        label(dr, (6, 6), TITAN_JP[T], 20, (255, 232, 160))
        label(dr, (6, 34), SETS[T], 11, (160, 168, 180))
        label(dr, (6, 56), f"現在 {r['cubes']} 箱", 12, (200, 200, 210))
        x = LABW
        for key, nm, extra in (("cur", "現在 (箱の防具)", ""),
                               ("a", NAMES["a"], f"三角形 {r['tris']['a']}"),
                               ("b", NAMES["b"], f"三角形 {r['tris']['b']}"),
                               ("c", NAMES["c"], f"三角形 {r['tris']['c']}")):
            for i, t in enumerate(r["tiles"][key]):
                im.paste(t, (x + i * TW, 4))
            star = " ★" if key == STAR else ""
            label(dr, (x + 4, TH + 8), f"{nm}  {extra}{star}", 15, (255, 232, 160) if key == STAR else (230, 232, 238))
            if key == "cur":
                label(dr, (x + 4, TH + 26), "正面 / 背面 / 3/4", 11, (150, 156, 168))
            x += colw + gap
        rows.append(im)
    # 階級の行
    h = TH + 44
    im = Image.new("RGB", (W, h), (24, 28, 36))
    dr = ImageDraw.Draw(im)
    label(dr, (6, 6), "階級", 20, (255, 232, 160))
    label(dr, (6, 34), f"{NAMES[STAR]}", 12, (200, 200, 210))
    label(dr, (6, 52), f"{TITAN_JP[RARITY_SET]}の一式", 12, (200, 200, 210))
    names = [("通常", "common+0"), ("レア", "rare+0"), ("伝説", "legend+0"), ("伝説 +10", "legend+10")]
    x = LABW
    for i, (nm, k) in enumerate(names):
        for j in range(3):
            im.paste(rar["tiles"][i * 3 + j], (x + j * TW, 4))
        label(dr, (x + 4, TH + 8), f"{nm}  三角形 {rar['tris'][k]}", 15)
        x += colw + gap
        if i == 3:
            break
    rows.append(im)
    H = sum(r.size[1] for r in rows) + 110
    S = Image.new("RGB", (W, H), (24, 28, 36))
    dr = ImageDraw.Draw(S)
    label(dr, (10, 8), "防具の低ポリ 3 案  (32 テクセル/ブロック。4 巨神の一式を A / B / C で。一番左は今の箱の防具)", 22, (255, 255, 255))
    label(dr, (10, 40), "A 外殻 = 巨神の素材の板を成形した重い殻 / B 衣と環 = 頭巾・外套が輪郭を作る布の装束 / C 骨組み = 肋・枝・羽軸の細い部材が体の外へ広がる軽装。"
                         "プレイヤーの顔は 3 案とも開けた。", 14, (200, 205, 215))
    label(dr, (10, 62), "階級 (最下段、★ の C): 通常 = 灰色の鉄の縁 → レア = 青みの縁 + 部品 +1 + 短い光の筋 → 伝説 = 金の縁 + 背の環 (大きな輪郭) + 布 + 筋が増える。"
                         "+10 は絵と光る画素だけが変わる。", 14, (200, 205, 215))
    y = 100
    for r in rows:
        S.paste(r, (0, y))
        y += r.size[1]
    p = ROOT / "docs/review/lowpoly_roughs/armor.png"
    S.save(p)
    print("sheet", S.size)


def manifest(results, rar):
    tr = lambda T, o: results[T]["tris"][o]
    pc = lambda T, o: results[T]["pieces"][o]
    desc = {
        "a": "外殻: 巨神の素材 (岩・骨・珊瑚・羽・割れた板) の板を成形した重い殻。肩は 3 段の傾けた板で ±13 まで張り、胸は中央が尖り、背に大きな飾り (岩鯨 = 背びれ / 潮殻 = 帆立の殻 / 雷翼 = 肩甲骨の羽 / 余燼 = 割れた岩の欠片)、腰から脚へ開く。最も重く、騎士に近い。",
        "b": "衣と環: 頭巾 (低い円錐が後ろへ垂れる)・背の長い外套・前垂れが輪郭を作る布の装束。板は肩・胸・腕の要所だけで、体は細く縦に長い。裾の形が巨神の素材 (岩鯨 = まっすぐ / 潮殻 = 弧 / 雷翼 = 羽の尖り / 余燼 = 破れ)。額の環に巨神の飾り。静かで、物語・静かなホラーに最も近い。",
        "c": "骨組み: 体は薄い布の下着で、その上に巨神の骨組み (岩鯨 = 肋の弧と背びれ / 潮殻 = 珊瑚の枝 / 雷翼 = 銅の肋と白い羽軸 / 余燼 = 鉄の帯と割れた岩) が輪郭を作る。胸の肋・背の翼・肩の牙/角が体の箱の外へ広がり、すき間が多く軽い。生き物の構造に見える。",
    }
    opts = {}
    for o in "abc":
        sets = " / ".join(f"{TITAN_JP[T]} {tr(T, o)}" for T in TITANS)
        opts[o] = {"name": {"a": "A 外殻 (carapace): 巨神の素材の板を成形した重い殻",
                            "b": "B 衣と環 (vestments): 頭巾・外套・前垂れの布が輪郭を作る",
                            "c": "C 骨組み (frame): 肋・枝・羽軸の細い部材が体の外へ広がる"}[o],
                   "desc": desc[o] + f" 三角形 (一式): {sets}。"}
    extra = (
        "防具を根本から作り直す低ポリのラフ。今の 4 組 (箱 245〜285・面 1,250〜1,463) は『体の箱に板をかぶせた』作りで、輪郭がプレイヤーの四角のままだった。"
        "ここでは、4 巨神 (岩鯨・潮殻・雷翼・余燼) の一式を 3 つの基盤で作った。骨は今と同じ (armorHead / Body / RightArm / LeftArm / RightLeg / LeftLeg / RightBoot / LeftBoot)。"
        "兜・胸 (腕と肩を含む)・脚・靴の 4 部位、非立方体、1 ブロック 32 テクセルのドット絵。顔は 3 案とも覆わない (額の環・耳・後頭だけ)。"
        "材質の言葉は敵と武器と同じ: 岩鯨 = 鯨骨・青灰の岩・苔 / 潮殻 = 珊瑚・真珠層・濡れた石 / 雷翼 = 銅・白い羽・紺の鋼 / 余燼 = 玄武岩 (割れ目が光る)・鍛冶の鉄。"
        "三角形 (一式 = 兜 + 胸 + 脚 + 靴): "
        + " / ".join(f"{o.upper()}: " + "・".join(f"{TITAN_JP[T]} {tr(T, o)}" for T in TITANS) for o in "abc")
        + f"。予算は一式 900 以下 (伝説 1,100 以下)。★ の C は階級も作った (岩鯨): 通常 {tr('whale', 'c')} → レア {tr('whale', 'c_rare')} → 伝説 {tr('whale', 'c_legend')}。"
        "階級のつけ方は武器の土台 と同じ: 通常 = 灰色の鉄の縁 / レア = 部品 +1 (胸の弧 1 段・側頭の飾り・前腕の飾り) + 青みの縁 + 短い光の筋 / "
        "伝説 = 輪郭を変える大きな一点 (頭の後ろの環と背の布) + 金の縁 + 筋が増える。+5 / +10 はメッシュを変えず、絵と glowmask だけを差し替える (縁が一段ずつ明るい)。"
        "シートの 1 行 = 1 巨神。左から 現在 / A / B / C (それぞれ正面・背面・3/4)。最下段が階級。出力: docs/review/lowpoly_roughs/armor/{a,b,c}/<set>/<部位>_{mesh.json,atlas.png,glowmask.png}"
        " (<set> = stone_whale / tide_shell / thunder_wing / ember。★ の岩鯨はレア・伝説の *_rare_* *_legend_* と +5 / +10 の絵つき)。"
        "作り方は tools/rough/lowpoly/armor/ (build_all.py)。採りたい案の目安: C (輪郭が体の外へ出て、4 巨神が重ならず、敵と武器の材質の言葉と合う。面も少ない)。"
        "ただし B の頭巾の静けさ (物語・ホラー) も強いので、B の衣の裾と頭巾を C の骨組みに足す折衷もできる。"
    )
    m = {"id": "lp_armor", "short": "防具", "kind": "低ポリ 3案",
         "title": "防具 — 低ポリ非立方体 + ドット絵 3 案 (4 巨神の一式。階級の見た目つき)",
         "sheets": ["lowpoly_roughs/armor.png", "lowpoly_roughs/armor_zoom.png"], "extra": extra, "options": opts}
    p = ROOT / "docs/review/lowpoly_roughs/armor_manifest.json"
    p.write_text(json.dumps(m, ensure_ascii=False, indent=2), encoding="utf-8")
    print("manifest", p)


def main():
    with ProcessPoolExecutor(5) as ex:
        futs = {T: ex.submit(worker_titan, T) for T in TITANS}
        fr = ex.submit(worker_rarity, RARITY_SET)
        results = {T: f.result() for T, f in futs.items()}
        rar = fr.result()
    for T in TITANS:
        print(T, results[T]["tris"], results[T]["pieces"])
    sheet(results, rar)
    zoom_sheet(results, rar)
    manifest(results, rar)


if __name__ == "__main__":
    main()
