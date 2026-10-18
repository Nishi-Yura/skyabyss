"""鍛え手 (ash_smith) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。
実行: python tools/rough/lowpoly/ash_smith/sheet.py
出力: docs/review/lowpoly_roughs/lp_ash_smith.png, lp_ash_smith/{a,b,c}/, lp_ash_smith_manifest.json"""
from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (255, 190, 120), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 玄武の鍛冶王", "b": "B 灰被りの炉守", "c": "C 金床頭の鋳鉄巨人"}
# (view, 高さ, yaw, pitch, 姿勢, {key: (center, scale)})
ROWS = [
    ("前 3/4 (休め)", 400, 212, 12, "rest", {"old": ((0, 40, 0), 3.7), "a": ((0, 40, 0), 3.7), "b": ((0, 42, 0), 3.7), "c": ((0, 40, 0), 3.7)}),
    ("後ろ 3/4 (背の鞴)", 400, 32, 12, "rest", {"old": ((0, 40, 0), 3.7), "a": ((0, 40, 0), 3.7), "b": ((0, 42, 0), 3.7), "c": ((0, 40, 0), 3.7)}),
    ("頭のアップ", 380, 195, 6, "rest", {"old": ((0, 52, -30), 9.0), "a": ((0, 55, -31), 7.5), "b": ((0, 58, -29), 6.6), "c": ((0, 55, -30), 7.5)}),
    ("大鎚の打ち下ろし (slam 0.9 s: 振りかぶりの頂点)", 440, 235, 10, "slam_up", {"old": ((0, 48, 0), 3.0), "a": ((0, 48, 0), 3.0), "b": ((0, 52, 0), 3.0), "c": ((0, 48, 0), 3.0)}),
    ("大鎚の着地 (slam 1.05 s)", 380, 235, 10, "slam_hit", {"old": ((0, 36, -8), 3.4), "a": ((0, 36, -8), 3.4), "b": ((0, 36, -8), 3.4), "c": ((0, 36, -8), 3.4)}),
    ("火箸 (sparks 0.8 s: 左手の塊を前へ差し出す)", 400, 215, 10, "tongs", {"old": ((0, 40, -8), 3.4), "a": ((0, 40, -8), 3.4), "b": ((0, 42, -8), 3.4), "c": ((0, 40, -8), 3.4)}),
]


def main():
    opts = {k: get_option(k)[0] for k in "abc"}
    tri = {k: tri_count(r["body"]) for k, r in opts.items()}
    gt = {k: {g: tri_count(v["faces"]) for g, v in r["gear"].items()} for k, r in opts.items()}
    print(tri, gt)
    F = voxel.font
    cv = Image.new("RGB", (W, 6000), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((28, 14), "灰の鍛え手 (ash_smith) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
    d.text((28, 50), "骨 15 本 (root[Y 180 度] > body > belly > apron / head / arm_r > forearm_r > socket_main[休め 45 度] / arm_l > forearm_l > socket_off / "
                     "leg_r・l / bellows_r・l) の名前・階層・pivot は今の geo と同じ。大鎚と火箸は別メッシュ。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
    d.text((28, 72), "左端が今のゲーム内のモデル (箱 1,225 個 (大鎚・火箸を除く))。三角形は本体 (大鎚 / 火箸)。中ボスの上限は本体 約 1,500・武器 各 約 250。"
                     "姿勢は今のアニメの値 (slam・sparks)。", font=F(15), fill=GR)
    y = 104
    for title, ph, yaw, pitch, pose, tab in ROWS:
        d.text((X0, y), title, font=F(20), fill=CY)
        y += 30
        f, n = old_faces(POSES[pose])
        c0, s0 = tab["old"]
        panels = [old_render(f, yaw, pitch, (PW, ph), c0, s0)]
        labels = ["今 (箱 %d 個)" % n]
        for k in "abc":
            c1, s1 = tab[k]
            im = render_pose(opts[k], POSES[pose], yaw, pitch, (PW, ph), c1, s1)
            bg = bgpanel(PW, ph)
            bg.paste(im, (0, 0), im)
            panels.append(bg)
            labels.append("%s  %d 三角形" % (NAMES[k], tri[k]))
        for i, (p, lb) in enumerate(zip(panels, labels)):
            cv.paste(p, (xs[i], y))
            d.rectangle([xs[i] - 1, y - 1, xs[i] + PW, y + ph], outline=(60, 60, 78))
            d.text((xs[i] + 2, y + ph + 6), lb, font=F(17), fill=WH if i == 0 else CY)
        y += ph + 38
    # 武器
    d.text((X0, y), "大鎚と火箸 (別メッシュ。今は箱 85 / 70 個)", font=F(20), fill=CY)
    y += 30
    gview = {"hammer": ((0, 10, 0), 5.0, 250, 8), "tongs": ((0, 28, -8), 5.0, 215, 14)}
    for gname, label in (("hammer", "大鎚"), ("tongs", "火箸")):
        for i, k in enumerate("abc", start=1):
            r = opts[k]
            ph = 300
            P0 = fpivot(SOCKETS[gname])
            im = render_gear(r, gname, 250 if gname == "hammer" else 215, 8, (PW, ph))
            bg = bgpanel(PW, ph)
            bg.paste(im, (0, 0), im)
            cv.paste(bg, (xs[i], y))
            d.rectangle([xs[i] - 1, y - 1, xs[i] + PW, y + ph], outline=(60, 60, 78))
            d.text((xs[i] + 2, y + ph + 6), "%s %s  %d 三角形" % (NAMES[k][0], label, gt[k][gname]), font=F(17), fill=CY)
        f, n = old_faces({}, gear=True)
        y += ph + 38
    # アトラス
    d.text((X0, y), "テクスチャのアトラス (512x512 を 0.75 倍で表示)", font=F(20), fill=CY)
    y += 30
    for i, k in enumerate("abc", start=1):
        at = Image.fromarray(opts[k]["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        im = bgc.convert("RGB").resize((int(at.width * 0.75), int(at.height * 0.75)), Image.NEAREST)
        cv.paste(im, (xs[i] - (PW - im.width) // 2 if False else xs[i], y))
        d.text((xs[i], y + im.height + 4), "%s  アトラス %d" % (NAMES[k], opts[k]["size"]), font=F(15), fill=CY)
    y += int(512 * 0.75) + 36
    for k in "abc":
        r = opts[k]
        per = r["data"]["bones"]
        g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
        d.text((X0, y), "%s:  全身 %d 三角形 (胴・腹・前掛け %d / 頭 %d / 腕 4 節 %d / 脚 %d / 鞴 %d)  大鎚 %d / 火箸 %d"
               % (NAMES[k], tri[k], g("body", "belly", "apron"), g("head"), g("arm_r", "arm_l", "forearm_r", "forearm_l"),
                  g("leg_r", "leg_l"), g("bellows_r", "bellows_l"), gt[k]["hammer"], gt[k]["tongs"]), font=F(17), fill=CY)
        y += 26
    y += 8
    for t in ("光る画素: 熱割れ・覗き窓・目・鞴の煙突の縁・大鎚の打面・火箸の塊だけ (橙〜黄白)。背景色は 3〜6 色の階調 + 手で置いた 2x2 のディザ。",
              "どの案も余燼の主題 (灰・熾・玄武岩・冷えかけの溶岩・熱割れ・鍛冶の道具) だけで組み、前の三体の巨神の敵と形・素材が重ならない。"):
        d.text((X0, y), t, font=F(15), fill=GR)
        y += 24
    cv = cv.crop((0, 0, W, y + 14))
    out = OUT / "lp_ash_smith.png"
    cv.save(out)
    print("saved", out, cv.size)


def render_gear(r, gname, yaw, pitch, size):
    """武器だけを休めの向き (回転なし) で描く。"""
    import copy
    g = r["gear"][gname]
    sock = SOCKETS[gname]
    fs = []
    P0 = fpivot(sock)
    for f in g["faces"]:
        h = copy.copy(f)
        h.verts = f.verts - P0
        h.ref = f.ref - P0
        h.bone = "root"
        fs.append(h)
    ext = np.array([v for f in fs for v in f.verts])
    cen = (ext.min(0) + ext.max(0)) / 2
    span = float((ext.max(0) - ext.min(0)).max())
    return render(fs, g["atlas"], g["glow"], yaw, pitch, size, cen, min(size) * 0.82 / max(span, 1))


if __name__ == "__main__":
    main()
