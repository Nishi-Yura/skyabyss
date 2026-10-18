"""雷翼・余燼の装飾品 3 案のシート・メッシュ・manifest を出す。
実行: python sheet.py [採りたい案 a|b|c]   (出力: docs/review/lowpoly_roughs/accessories*)"""
from __future__ import annotations

import json
import sys

from PIL import ImageDraw, ImageFont

import ember
import thunder
from common import *
from mock import SKIN, SHIRT, PANTS, HAIR, SHOE, xf

BEST = sys.argv[1] if len(sys.argv) > 1 else "b"
PLAT = (36, 40, 48)
TILE = (30, 34, 42, 255)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc",
         "C:/Windows/Fonts/msgothic.ttc", "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf"]
ALL = {}
for m in (thunder, ember):
    for k, v in m.ITEMS.items():
        ALL[k] = dict(v, titan=m.TITAN)

OPT_NAME = {
    "thunder": dict(a="A 風切羽の重ね", b="B 稲妻の折れ", c="C 翼の輪"),
    "ember": dict(a="A 割れ板", b="B 鉄枠", c="C 岩塊"),
}
BUDGET = dict(worn=200, charm=150, held=150)
RAR_JP = dict(common="コモン", rare="レア", legend="伝説")
KIND_JP = dict(worn="装飾の枠・身につける", charm="護符の枠・身につけない", held="手に持つ")


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def label(dr, xy, text, sz=14, fill=(230, 232, 238)):
    dr.text(xy, text, font=font(sz), fill=fill)


# ------------------------------------------------------------------ プレイヤーの模型 (着けた姿)
def player_parts():
    parts = []

    def bx(c, size, rgb):
        parts.append((block("b", c, size), rgb))

    for sx in (-2, 2):
        bx((sx, 6, 0), (3.9, 12, 3.9), PANTS)
        bx((sx, 0.7, -0.3), (4.0, 1.4, 4.6), SHOE)
    bx((0, 18, 0), (8, 12, 4), SHIRT)
    bx((0, 12.6, 0), (8.4, 1.2, 4.4), (70, 50, 38))
    bx((0, 28, 0), (8, 8, 8), SKIN)
    bx((0, 31.2, 0.3), (8.4, 1.8, 8.4), HAIR)
    bx((0, 28.4, 3.6), (8.4, 6.0, 1.4), HAIR)
    bx((-6, 18, 0), (4, 12, 4), SHIRT)
    bx((6, 18, 0), (4, 12, 4), SHIRT)
    bx((-6, 11.2, 0), (4.1, 2.0, 4.1), SKIN)
    bx((6, 11.2, 0), (4.1, 2.0, 4.1), SKIN)
    return parts


WORN = {  # id: (回転, 平行移動, 倍率, 基点, 視点: yaw, pitch, 中心, 倍率)
    "storm_anklet": (np.eye(3), V(2, 4.2, 0), 0.5, V(0, 0, 0), (150, -22, (1, 4.5, 0), 11)),
    "storm_eye_feather": (rot_z(-20) @ rot_x(25), V(4.3, 29.0, 0.8), 0.8, V(0, -7, 0), (140, -8, (1, 28, 1), 7.5)),
    "ember_lantern": (np.eye(3), V(-2.6, 13.0, -2.7), 0.45, V(0, 7, 0), (160, -12, (-1.5, 11.5, -2), 10.5)),
    "ember_mantle": (np.eye(3), V(0, 24.2, 2.3), 0.85, V(0, 0, 0), (338, -8, (0, 17, 0), 5.2)),
}
# 持ち物欄・3/4・3 枚目 (着けない品は別の角度) の視点: (yaw, pitch, 中心 y, 倍率)
VIEWS = {
    "storm_charm": dict(gui=(180, 0, -1.0, 11.0), three=(150, -22, -1.0, 11.0), third=(25, -10, -1.0, 11.0)),
    "storm_anklet": dict(gui=(180, -16, 0, 14.0), three=(150, -36, 0, 14.0), third=None),
    "storm_eye_feather": dict(gui=(180, 0, 1.0, 11.5), three=(150, -22, 1.0, 11.5), third=None),
    "gale_feather": dict(gui=(180, 0, 0.5, 11.5), three=(150, -22, 0.5, 11.5), third=(40, -12, 0.5, 11.5)),
    "kindling_charm": dict(gui=(180, 0, 0.0, 12.0), three=(150, -22, 0.0, 12.0), third=(25, -10, 0.0, 12.0)),
    "ember_mantle": dict(gui=(0, -4, -8.0, 9.5), three=(30, -14, -8.0, 9.5), third=None),
    "ember_lantern": dict(gui=(180, 0, 0.5, 13.0), three=(150, -22, 0.5, 13.0), third=None),
    "ember_horn": dict(gui=(180, 0, -1.0, 12.5), three=(150, -22, -1.0, 12.5), third=(40, -12, -1.0, 12.5)),
    "cinder_lamp": dict(gui=(180, 0, 1.0, 11.5), three=(150, -22, 1.0, 11.5), third=(40, -12, 1.0, 11.5)),
}


def tile_img(r, titan, yaw, pitch, cy, sc, size):
    im = view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, (0, cy, 0), sc)
    bg = Image.new("RGBA", size, TILE)
    bg.alpha_composite(im)
    return bg.convert("RGB")


def worn_img(iid, r, titan, size):
    R, t, s, piv, (yaw, pitch, cen, sc) = WORN[iid]
    wf = xf(r["faces"], R, t, s, piv)
    parts = player_parts()
    allf, at_, gl_ = compose([(wf, r["atlas"], r["glow"])], parts)
    im = view(allf, at_, gl_, titan, yaw, pitch, size, cen, sc)
    bg = Image.new("RGBA", size, TILE)
    bg.alpha_composite(im)
    return bg.convert("RGB")


def old_sprite(iid, size=96):
    for p in (ASSETS / f"textures/item/{iid}.png", ASSETS / f"textures/block/{iid}.png"):
        if p.exists():
            im = Image.open(p).convert("RGBA")
            im = im.crop(im.getbbox() or (0, 0, im.width, im.height)) if im.height == im.width else im.crop((0, 0, im.width, im.width))
            return im.resize((size, size), Image.NEAREST)
    return Image.new("RGBA", (size, size), (80, 80, 80, 255))


def main():
    res = {}
    for iid, it in ALL.items():
        for o in "abc":
            r = make(it["fn"][o], it["titan"], it["rar"])
            res[(iid, o, it["rar"])] = r
            save(r, OUT / o, f"{iid}")
    # 階級のはしご (採りたい案)
    for iid, it in ALL.items():
        for rar in ("common", "rare", "legend"):
            r = res.get((iid, BEST, rar)) or make(it["fn"][BEST], it["titan"], rar)
            res[(iid, BEST, rar)] = r
            save(r, OUT / BEST, f"{iid}_{rar}")
    over = [(k, r["tris"]) for k, r in res.items() if r["tris"] > BUDGET[ALL[k[0]]["kind"]] + (0 if k[2] == ALL[k[0]]["rar"] else 40)]
    print("over budget:", over)
    sheet(res)
    manifest(res)


TW, TH = 150, 190
LEFT = 150
X0 = 16


def sheet(res):
    W = 1640
    ROWH = TH + 40
    items = list(ALL.items())
    head = 230
    ladder_h = 2 * 190 + 90
    H = head + len(items) * ROWH + ladder_h + 40
    im = Image.new("RGB", (W, H), PLAT)
    dr = ImageDraw.Draw(im)
    label(dr, (X0, 10), "雷翼・余燼の装飾品 — 低ポリ非立方体 + ドット絵 3 案 (32 テクセル/ブロック)", 24, (255, 255, 255))
    label(dr, (X0, 46), "材質の言葉は武器・防具と同じ。雷翼 = 銅・白い羽・紺の鋼・白青の光 (弓 B 翼弓) / 余燼 = 玄武岩の黒い板 (割れ目が光る)・鍛冶の鉄・橙の光 (戦斧 A 割れ板)。"
                      "防具 C 骨組みと同じく、細い部材が輪郭を作る。", 15, (200, 205, 215))
    label(dr, (X0, 70), "三角形の予算: 身につける品 200 以下 / 持つ品・護符 150 以下 (数字は階級どおりの面数)。左の絵 = 今 (16px の平らな絵 + minecraft:item/generated)。"
                      "着けた姿は模式 (今は装飾の枠の品は体に描かれない。0.45〜0.85 倍で付けた想定)。", 15, (200, 205, 215))
    label(dr, (X0, 100), "今の状態: 雷翼 4 品 (嵐の護符・嵐纏の足環・嵐の目の羽根・風切りの羽根)・余燼 5 品 (熾りの護符・熾火の外套・余燼の灯・熾火の角杯・燠の灯) は全部 16px の平絵。", 15, (255, 232, 160))
    label(dr, (X0, 124), "岩鯨・潮殻の装飾品 (共鳴の護符・苔の護石・潮鳴りの護符・月潮の真珠・潮見の貝・潮の真珠) も全部 16px の平絵のまま。新しい様式に合っている装飾品はまだ 1 つも無い"
                      " (今回の対象は雷翼・余燼。岩鯨・潮殻は次の回に同じ手順)。", 15, (255, 232, 160))
    y0 = 160
    label(dr, (X0, y0), "列の見方: 各案 = 持ち物欄 (正面) / 3/4 (手に持つ・落ちている) / 着けた姿 (身につける品) または背面・別角度。名前の下に面数。", 15, (200, 205, 215))
    for ci, o in enumerate("abc"):
        x = LEFT + 8 + ci * (3 * (TW + 3) + 10)
        label(dr, (x, y0 + 26), f"案 {o.upper()}  雷翼: {OPT_NAME['thunder'][o][2:]} / 余燼: {OPT_NAME['ember'][o][2:]}", 16, (255, 232, 160))
    y = head
    for iid, it in items:
        titan = it["titan"]
        dr.rectangle([0, y - 4, W, y - 3], fill=(60, 66, 78))
        label(dr, (X0, y), it["jp"], 17, (255, 255, 255))
        label(dr, (X0, y + 22), f"{iid}", 11, (160, 165, 175))
        label(dr, (X0, y + 36), f"{RAR_JP[it['rar']]} / {KIND_JP[it['kind']]}", 12, (190, 195, 205))
        sp = old_sprite(iid, 96)
        bg = Image.new("RGBA", (96, 96), TILE)
        bg.alpha_composite(sp)
        im.paste(bg.convert("RGB"), (X0, y + 56))
        label(dr, (X0, y + 154), "今 (平絵)", 13, (200, 200, 210))
        for ci, o in enumerate("abc"):
            r = res[(iid, o, it["rar"])]
            v = VIEWS[iid]
            x = LEFT + 8 + ci * (3 * (TW + 3) + 10)
            for k, key in enumerate(("gui", "three")):
                yaw, pitch, cy, sc = v[key]
                im.paste(tile_img(r, titan, yaw, pitch, cy, sc, (TW, TH)), (x + k * (TW + 3), y))
            x3 = x + 2 * (TW + 3)
            if iid in WORN:
                im.paste(worn_img(iid, r, titan, (TW, TH)), (x3, y))
                sub = "着けた姿"
            else:
                yaw, pitch, cy, sc = v["third"]
                im.paste(tile_img(r, titan, yaw, pitch, cy, sc, (TW, TH)), (x3, y))
                sub = "背面・別角度"
            ok = r["tris"] <= BUDGET[it["kind"]]
            label(dr, (x, y + TH + 2), f"{OPT_NAME[titan][o]}  {r['tris']} 面" + ("" if ok else " (超過)"), 14,
                  (255, 232, 160) if o == BEST else (230, 232, 238))
            label(dr, (x + 2 * (TW + 3), y + TH + 2), sub, 12, (170, 175, 185))
        y += ROWH
    # ---- 階級のはしご
    dr.rectangle([0, y - 4, W, y - 2], fill=(90, 96, 110))
    label(dr, (X0, y + 2), f"階級のはしご (案 {BEST.upper()} = {OPT_NAME['thunder'][BEST][2:]} / {OPT_NAME['ember'][BEST][2:]}): コモン → レア → 伝説", 20, (255, 255, 255))
    label(dr, (X0, y + 30), "レア = 部品 +1 と青みの縁の金属、伝説 = 輪郭の大きな一点 + 金の縁 + 縁がまだらに光る (武器・防具と同じつけ方)。実際の階級は ★。メッシュ・絵は *_common / *_rare / *_legend。", 14, (200, 205, 215))
    y += 58
    for titan, ids, tw, th in (("thunder", [k for k, v in ALL.items() if v["titan"] == "thunder"], 130, 150),
                               ("ember", [k for k, v in ALL.items() if v["titan"] == "ember"], 104, 150)):
        x = X0
        for iid in ids:
            it = ALL[iid]
            v = VIEWS[iid]
            label(dr, (x, y), it["jp"], 14, (255, 232, 160))
            for rar in ("common", "rare", "legend"):
                r = res[(iid, BEST, rar)]
                yaw, pitch, cy, sc = v["three"]
                sc2 = sc * tw / 150.0 * (1.0 if iid != "ember_mantle" else 1.0)
                im.paste(tile_img(r, titan, yaw, pitch, cy, sc2 * 1.12, (tw, th)), (x, y + 20))
                star = "★" if rar == it["rar"] else ""
                label(dr, (x + 2, y + 22 + th), f"{RAR_JP[rar]}{star} {r['tris']}", 12 if tw < 120 else 13)
                x += tw + 2
            x += 8 if titan == "thunder" else 4
        y += 20 + th + 28
    W_ = ROOT / "docs/review/lowpoly_roughs/accessories.png"
    im = im.crop((0, 0, W, y + 10))
    im.save(W_)
    print("sheet", im.size)


def manifest(res):
    def n(iid, o):
        return res[(iid, o, ALL[iid]["rar"])]["tris"]

    def line(titan, o):
        return " / ".join(f"{ALL[i]['jp']} {n(i, o)}" for i in ALL if ALL[i]["titan"] == titan)

    desc = dict(
        a=dict(thunder="風切羽の重ね: 白い羽の扇や重ね羽が輪郭。足環は後ろへ垂れる羽、護符は下へ垂れる羽、目の羽根は大羽と脇羽、風切りは 3 枚の重ね羽。",
               ember="割れ板: 黒い板が割れ、割れ目が光る。護符は斜めに割れた六角の板、外套は肩の割れ板、灯は 2 段の板の間の光、角杯は割れ目の走る角、吊り灯は割れた殻。"),
        b=dict(thunder="稲妻の折れ: 銅の折れ板と光る筋。護符は稲妻の形そのもの、足環は銅と鋼を交互にして上へ刃、目の羽根は割れた羽に稲妻の筋、風切りは鋼の羽に光る芯。",
               ember="鉄枠: 鍛冶の鉄の枠が光る芯を抱く。護符は鉄の環と十字の桟、外套は鉄と黒い板の鱗が重なる、灯は鉄の籠、角杯は鉄の帯、吊り灯は鉄の火鉢。"),
        c=dict(thunder="翼の輪: 輪と左右の翼。護符は輪に光る珠と翼、足環は輪から左右へ翼 (ヘルメスの靴)、目の羽根は目の環、風切りは裂けた先。",
               ember="岩塊: 積んだ岩と溶けた滴。護符は岩を積み光る継ぎ目と滴、外套は肩の岩塊とぼろの裾、灯は火口と冠の棘、角杯は岩の杯、吊り灯は滴る岩塊。"))
    options = {}
    for o in "abc":
        options[o] = {"name": f"{o.upper()} 雷翼 = {OPT_NAME['thunder'][o][2:]} / 余燼 = {OPT_NAME['ember'][o][2:]}",
                      "desc": f"雷翼: {desc[o]['thunder']} 三角形: {line('thunder', o)}。 余燼: {desc[o]['ember']} 三角形: {line('ember', o)}。"}
    lad = " / ".join(f"{ALL[i]['jp']} {res[(i, BEST, 'common')]['tris']}→{res[(i, BEST, 'rare')]['tris']}→{res[(i, BEST, 'legend')]['tris']}"
                     for i in ALL)
    extra = (
        "雷翼・余燼の装飾品 (装飾の枠・護符の枠の品と、手に持つ補助の品) を、武器 (雷翼 B 翼弓・余燼 A 割れ板)・防具 C (骨組み) と同じ低ポリ + ドット絵にするラフ。"
        "対象 9 品: 雷翼 = 嵐の護符 (護符・レア)・嵐纏の足環 (装飾・レア・足首)・嵐の目の羽根 (装飾・伝説・髪に挿す)・風切りの羽根 (滑空の道具)。"
        "余燼 = 熾りの護符 (護符・レア)・熾火の外套 (装飾・レア・背中)・余燼の灯 (装飾・伝説・腰)・熾火の角杯 (飲み物)・燠の灯 (吊り灯のブロック)。"
        "今は全部 16px の平絵 (item/generated)。岩鯨・潮殻の装飾品 (共鳴の護符・苔の護石・潮鳴りの護符・月潮の真珠) も平絵のままで、新しい様式の装飾品はまだ無い (今回は対象外。選んだ案を同じ手順で後から)。"
        "3 案を全品に同じ考えで当てた: A = 巨神の素材の板・羽を重ねる / B = 輪郭を折る・枠にする (稲妻の折れ・鉄の枠) / C = 輪・岩を積む (翼の輪・岩塊)。"
        "シートは 1 行 = 1 品。左から 今の絵 / 案 A・B・C (それぞれ 持ち物欄・3/4・着けた姿または別角度)。身につける品 (足環・羽根・外套・灯) は模式のプレイヤーに付けた姿つき (今は装飾の枠の品は体に描かれない。付けるなら新しい描画が要る)。"
        "予算: 身につける品 200 以下、護符・持つ品 150 以下 (案内の数字は階級どおりの面数)。"
        f"階級のはしご (案 {BEST.upper()}、コモン→レア→伝説の面数): {lad}。"
        "階級のつけ方は武器と同じ: レア = 部品 +1 と青みの縁の金属、伝説 = 輪郭の大きな一点と金の縁、縁がまだらに光る。装飾品は鍛えない (強化の段なし)。"
        f"出力: docs/review/lowpoly_roughs/accessories/{{a,b,c}}/<品の id>_{{mesh.json,atlas.png,glowmask.png}} (案 {BEST.upper()} には *_common / *_rare / *_legend も)。作り方は tools/rough/lowpoly/accessories/ (sheet.py)。"
        "採りたい案の目安: 案 B (稲妻の折れ・鉄枠。折れた輪郭が一目で巨神ごとに分かれ、護符と灯が重ならない)。ただし A の羽の重ねと割れ板は武器の言葉に最も近い。"
    )
    m = {"id": "lp_accessories", "short": "装飾品", "kind": "低ポリ 3案", "title": "雷翼・余燼の装飾品",
         "sheets": ["lowpoly_roughs/accessories.png"], "extra": extra, "options": options}
    p = ROOT / "docs/review/lowpoly_roughs/accessories_manifest.json"
    p.write_text(json.dumps(m, ensure_ascii=False, indent=2), encoding="utf-8")
    print("manifest", p)


if __name__ == "__main__":
    main()
