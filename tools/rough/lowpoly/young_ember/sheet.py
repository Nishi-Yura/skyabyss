"""余燼の仔 (young_ember) 低ポリ 3 案: メッシュ書き出し + 案ごとの回転シート + 比較 + 説明 json。

実行: python tools/rough/lowpoly/young_ember/sheet.py
出力: tools/rough/lowpoly/young_ember/out/{a,b,c}/young_ember_<k>_mesh.json・_atlas.png・_glowmask.png (取り込みの入力)
      docs/review/young_roughs/ember_{a,b,c}.png (8 方向 + 乗り手 6 人の上・横 + move・appear の途中)
      docs/review/young_roughs/ember_compare.png、ember_desc.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (255, 190, 120), (170, 166, 176), (236, 232, 218)
F = voxel.font
PW, PH = 400, 300
CEN3 = (0, 30, -6)
SC3 = 1.45
YAWS = [(180, "正面"), (225, "前 45°"), (270, "横 90°"), (315, "後ろ 135°"), (0, "真後ろ"), (45, "後ろ 225°"),
        (90, "横 270°"), (135, "前 315°")]
DESC = {
    "a": "ずんぐりした黒い玄武岩の仔熊。首の骨の鞍が背骨に沿って伸び、肋骨の板を並べた長椅子になって両脇の肋骨が胴を抱く。"
         "肩の瘤で頭を高く持ち上げ、腰には親の火山を縮めた小さな火口。胸の核が歌のかけらとして光る。",
    "b": "親の「皮が岩になる火蜥蜴」をいちばんそのまま縮めた、低く長い火蜥蜴の仔。脚は横へ張り出し、尾は横へ巻く。"
         "背の両脇に黒い殻の板が並び、そのあいだに灰色の平たい石の板が鞍として載る。額の燠の結晶の冠と胸の核が光る。",
    "c": "丸い岩の塊のような亀の仔。甲羅は黒い殻の板と玄武岩の帯で、上は平らに削れた石の台地になり、ふちを燠の結晶がぐるりと囲む。"
         "柱の脚でのしのし歩く。甲羅の前のふちに歌のかけらの核が埋まる。",
}


def panel(r, pose, yaw, pitch, cen=CEN3, sc=SC3, ex=None):
    return on_bg(render_pose(r, pose or {}, yaw, pitch, (PW, PH), cen, sc, ex))


def grid(ims, labels, cols, title=None, sub=None):
    top = (58 if sub else 36) if title else 0
    rows = (len(ims) + cols - 1) // cols
    W = Image.new("RGB", (PW * cols, top + rows * (PH + 28)), (16, 16, 22))
    d = ImageDraw.Draw(W)
    if title:
        d.text((10, 6), title, font=F(20), fill=WH)
    if sub:
        d.text((10, 34), sub, font=F(14), fill=GR)
    for i, (im, lb) in enumerate(zip(ims, labels)):
        x, y = PW * (i % cols), top + (PH + 28) * (i // cols)
        W.paste(im, (x, y))
        d.text((x + 8, y + PH + 4), lb, font=F(15), fill=CY)
    return W


def main():
    REVIEW.mkdir(parents=True, exist_ok=True)
    opts, mods, st = {}, {}, {}
    for k in "abc":
        r, mod = get_option(k)
        opts[k], mods[k] = r, mod
        st[k] = stats(r)
        print(k, st[k])
    for k, r in opts.items():
        s = st[k]
        turn = [panel(r, {}, y, 15, (0, 34, -8), 1.8) for y, _ in YAWS]
        top = grid(turn, [lb for _, lb in YAWS], 4,
                   "余燼の仔 (young_ember) — %s" % mods[k].NAME,
                   "三角形 %d・アトラス %d・開いた辺 %d・鞍の上面 %.2f ブロック (座席 6: x ±1.0、z -2 / 0 / +2)・大きさ 幅 %.1f × 高さ %.1f × 長さ %.1f ブロック"
                   % (s["tris"], s["atlas"], s["open_edges"], SEAT_Y / 16, s["size_blocks"][0], s["size_blocks"][1],
                      s["size_blocks"][2]))
        strip = [panel(r, {}, 180, 90, (0, 30, -6), SC3, riders()),
                 panel(r, {}, 270, 0, (0, 34, -6), SC3, riders()),
                 panel(r, G.pose_at("move", 0.3), 225, 15, (0, 34, -8), 1.7),
                 panel(r, G.pose_at("appear", 0.8), 225, 15, (0, 34, -8), 1.7)]
        bot = grid(strip, ["真上から: 乗り手 6 人 (2 列 × 3 段)", "横から: 乗り手 6 人", "move (速歩 0.3 秒)",
                           "appear の途中 (0.8 秒。歌のかけらから育つ)"], 4)
        W = Image.new("RGB", (top.width, top.height + bot.height + 6), (16, 16, 22))
        W.paste(top, (0, 0))
        W.paste(bot, (0, top.height + 6))
        W.save(REVIEW / f"ember_{k}.png")
    cmp_ims = [panel(opts[k], {}, 215, 15, CEN3, 1.6) for k in "abc"]
    grid(cmp_ims, [mods[k].NAME for k in "abc"], 3, "余燼の仔 (young_ember) — 3 案 (前 3/4)").save(REVIEW / "ember_compare.png")
    desc = {"title": "余燼の仔"}
    for k in "abc":
        desc[k] = {"title": mods[k].NAME, "desc": DESC[k]}
    (REVIEW / "ember_desc.json").write_text(json.dumps(desc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (HERE / "out" / "stats.json").write_text(json.dumps(st, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
