"""雷翼・余燼の武器 6 つの最終メッシュ (階級 3 段 x 強化 3 段、弓は引き 3 段つき) を出す。
実行: python build_storm_ember.py [item_id ...]
出力: docs/review/lowpoly_roughs/weapons_final/<item_id>/ と docs/review/lowpoly_roughs/weapons_final_storm_ember.png
座標: 1 単位 = 1/16 ブロック、x = 0・z = 0 が中心 (item モデルへ入れるときは +8 ずらす)、+Y が刃先。"""
from __future__ import annotations

import json
import sys

from PIL import ImageDraw, ImageFont

from scene import *
from paint import paint

OUT = ROOT / "docs/review/lowpoly_roughs/weapons_final"
SHEET = ROOT / "docs/review/lowpoly_roughs/weapons_final_storm_ember.png"

# id: (日本語名, 巨神, 形の関数, inlay, edge_mats, 引き絵の段数, 描画: (中心 y, 倍率), 強化の見せ方)
import item_thunder_wing_bow as m_bow
import item_warden_glaive as m_gl
import item_raiju_claw as m_cl
import item_ember_war_axe as m_ax
import item_smith_hammer as m_hm
import item_thrall_poker as m_pk

ITEMS = {
    "thunder_wing_bow": ("雷翼の弓 (翼弓)", "thunder", m_bow.faces, m_bow.inlay, m_bow.EDGE, 4, (8.0, 10.0)),
    "warden_glaive": ("羽守の鉤槍", "thunder", m_gl.faces, m_gl.inlay, m_gl.EDGE, 1, (8.0, 9.6)),
    "raiju_claw": ("雷獣の爪", "thunder", m_cl.faces, m_cl.inlay, m_cl.EDGE, 1, (8.0, 12.0)),
    "ember_war_axe": ("余燼の戦斧", "ember", m_ax.faces, m_ax.inlay, m_ax.EDGE, 1, (8.0, 10.0)),
    "smith_hammer": ("鍛え手の大鎚", "ember", m_hm.faces, m_hm.inlay, m_hm.EDGE, 1, (8.5, 6.8)),
    "thrall_poker": ("灰被りの火かき棒", "ember", m_pk.faces, m_pk.inlay, m_pk.EDGE, 1, (8.0, 10.0)),
}
RAR = ("common", "rare", "legend")
RAR_JP = dict(common="通常", rare="レア", legend="伝説")
PLAT = (36, 40, 48)
FONTS = ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc", "C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/msgothic.ttc"]


def font(sz):
    for p in FONTS:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def make(item, rarity, plus=0, stage=0):
    jp, titan, fn, inlay, em, nst, _ = ITEMS[item]
    faces = fn(rarity, stage) if nst > 1 else fn(rarity)
    faces, size, P, N, M, L = build(faces)
    atlas, glow = paint(faces, size, P, N, M, L, titan, rarity, plus, inlay, em)
    return dict(faces=faces, size=size, atlas=atlas, glow=glow, tris=tri_count(faces))


def stem_of(rarity, stage, plus):
    s = rarity
    if stage > 0:
        s += f"_pull{stage - 1}"
    if plus:
        s += f"_plus{plus}"
    return s


def save_pair(r, d, stem):
    save_img(r["atlas"], d / f"{stem}_atlas.png")
    gm = np.zeros((r["size"], r["size"], 4), np.uint8)
    gm[r["glow"]] = (255, 255, 255, 255)
    save_img(gm, d / f"{stem}_glowmask.png")


def build_item(item):
    jp, titan, fn, inlay, em, nst, _ = ITEMS[item]
    d = OUT / item
    d.mkdir(parents=True, exist_ok=True)
    res = {}
    for rar in RAR:
        for st in range(nst):
            base = None
            for plus in (0, 5, 10):
                r = make(item, rar, plus, st)
                stem = stem_of(rar, st, plus)
                save_pair(r, d, stem)
                if plus == 0:
                    base = r
                    s0 = stem_of(rar, st, 0)
                    export_mesh(r["faces"], r["size"], d / f"{s0}_mesh.json", f"{item}_{s0}",
                                f"{s0}_atlas.png", f"{s0}_glowmask.png")
                else:
                    assert r["size"] == base["size"], "uv layout changed between plus levels"
                res[(rar, st, plus)] = r
    return res


# ------------------------------------------------------------------ シート

def tile(r, titan, yaw, pitch, size, scale, cen):
    im = view(r["faces"], r["atlas"], r["glow"], titan, yaw, pitch, size, cen, scale)
    bg = Image.new("RGBA", size, PLAT + (255,))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def label(dr, xy, text, sz=14, fill=(230, 232, 238)):
    dr.text(xy, text, font=font(sz), fill=fill)


def sheet(allres):
    WIDTH = 1700
    TW, TH = 150, 270
    rows = []
    for item, res in allres.items():
        jp, titan, fn, inlay, em, nst, (cy, sc) = ITEMS[item]
        cen = (0, cy, 0)
        isbow = nst > 1
        cx = -2 if isbow else 0
        h = 44 + 2 * (TH + 24) + ((200 + 24) if isbow else 0) + 10
        im = Image.new("RGB", (WIDTH, h), PLAT)
        dr = ImageDraw.Draw(im)
        tr = " / ".join(f"{RAR_JP[a]} {res[(a, 0, 0)]['tris']}" for a in RAR)
        label(dr, (20, 8), f"{jp}   {item}   三角形 {tr}", 20, (255, 232, 160))
        y = 40
        x = 20
        # 行 1: 階級 3 段 x (正面, 3/4)
        for rar in RAR:
            for yaw, pit in ((180, 0), (150, -12)):
                t = tile(res[(rar, 0, 0)], titan, yaw, pit, (TW, TH), sc, (cx, cy, 0))
                im.paste(t, (x, y)); x += TW + 3
            label(dr, (x - 2 * TW - 3, y + TH + 2), f"{RAR_JP[rar]} {res[(rar, 0, 0)]['tris']}面", 14)
            x += 8
        # 行 1 右: 強化 (伝説 +0 / +5 / +10) は下の行へ
        y += TH + 24
        x = 20
        for rar in ("rare", "legend"):
            for plus in (0, 5, 10):
                t = tile(res[(rar, 0, plus)], titan, 165, -8, (TW, TH), sc, (cx, cy, 0))
                im.paste(t, (x, y)); label(dr, (x, y + TH + 2), f"{RAR_JP[rar]} +{plus}", 14); x += TW + 3
            x += 12
        # 通常 +10 も
        t = tile(res[("common", 0, 10)], titan, 165, -8, (TW, TH), sc, (cx, cy, 0))
        im.paste(t, (x, y)); label(dr, (x, y + TH + 2), "通常 +10", 14)
        y += TH + 24
        if isbow:
            sw = 126
            x = 20
            for rar in RAR:
                for st in range(nst):
                    r = res[(rar, st, 0)]
                    t = tile(r, titan, 180, 0, (sw, 200), 8.2, (-2, 8, 0))
                    im.paste(t, (x, y))
                    label(dr, (x + 2, y + 202), f"{RAR_JP[rar]} " + ("休め" if st == 0 else f"引き{st - 1}") + f" {r['tris']}", 12)
                    x += sw + 2
                x += 10
        rows.append(im)
    H = sum(r.size[1] for r in rows) + 70
    W = Image.new("RGB", (WIDTH, H), PLAT)
    dr = ImageDraw.Draw(W)
    label(dr, (20, 8), "雷翼・余燼の武器 6 つ (最終メッシュ。32 テクセル/ブロック。通常 ≤250 / レア ≤300 / 伝説 ≤350)", 22, (255, 255, 255))
    label(dr, (20, 40), "階級 = 輪郭 (部品 +1 / 大きな一点) + 縁の金属 (灰 → 青 → 金)。強化 = 同じメッシュで絵と光る画素だけ (+5 は筋、+10 は縁まで通る)。", 14, (200, 205, 215))
    y = 70
    for r in rows:
        W.paste(r, (0, y)); y += r.size[1]
    W.save(SHEET)
    print("sheet", W.size)


def main():
    items = sys.argv[1:] or list(ITEMS)
    allres = {}
    for it in items:
        res = build_item(it)
        allres[it] = res
        nst = ITEMS[it][5]
        print(it, {a: [res[(a, s, 0)]["tris"] for s in range(nst)] for a in RAR})
    sheet(allres)


if __name__ == "__main__":
    main()
