"""岩鯨の仔 (young_stone_whale) 低ポリ 3 案: メッシュ書き出し + 案ごとの回転シート + 比較 + 説明 JSON。

実行: python tools/rough/lowpoly/young_stone_whale/sheet.py
出力: tools/rough/lowpoly/young_stone_whale/out/{a,b,c}/ (young_stone_whale_<k>_mesh.json・_atlas.png・_glowmask.png。取り込みの入力)
      docs/review/young_roughs/stone_whale_{a,b,c}.png (8 方向 + 上から・横から 6 人の座り + 泳ぎ・現れの途中)
      docs/review/young_roughs/stone_whale_compare.png、stone_whale_desc.json
検査 (標準出力): 三角形、開いた辺 (0 であること)、鞍の上面の高さ (SEAT_Y = 58 で平ら)。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from view import *  # noqa
from PIL import ImageDraw  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
F = voxel.font
PW, PH = 340, 280
CEN8 = (0, 40, 9)
SC8 = 1.6
YAWS = [(180, "正面"), (225, "右斜め前"), (270, "右横"), (315, "右斜め後ろ"), (0, "後ろ"), (45, "左斜め後ろ"),
        (90, "左横"), (135, "左斜め前")]
TITLE = "岩鯨の仔"
DESC = {
    "a": "いちばん丸くふっくらした石の仔鯨。背の板張りの台を、わき腹から立つ鯨骨の肋が両側から囲む輿にした。"
         "肋の先と額に青緑の歌のかけらが灯り、左わきに親ゆずりの小さな錨石が揺れる。",
    "b": "細身で、頭とわき腹に石の板を重ねた仔鯨。両わきから立つ二枚の大きな鯨骨の背びれが鞍の壁と背もたれになり、"
         "稜線に青緑の光の筋が走る。長い胸びれで翼のように空をかく。",
    "c": "まんまるの仔鯨が、帆立の縁の半分の貝殻を椀にして背負う。椀の中が 6 人の床で、外は藤壺だらけ、"
         "いくつかの藤壺の口が青緑に光る。椀の前の縁から鎖と小さな錨石が垂れる。",
}


def panel(im, label, sub=None):
    W = Image.new("RGB", (PW, PH + 26), (16, 16, 22))
    W.paste(im, (0, 0))
    d = ImageDraw.Draw(W)
    d.text((8, PH + 4), label, font=F(15), fill=CY)
    if sub:
        d.text((PW - 8 - d.textlength(sub, font=F(13)), PH + 6), sub, font=F(13), fill=GR)
    return W


def rnd(r, pose, yaw, pitch, cen=CEN8, sc=SC8, extra=None):
    return on_bg(render_pose(r, pose, yaw, pitch, (PW, PH), cen, sc, extra))


def option_sheet(k, r, name, st):
    ims = [panel(rnd(r, {}, y, 15), lb, f"{(y - 180) % 360}°") for y, lb in YAWS]
    rid = riders()
    strip = [
        panel(rnd(r, {}, 270, 89.9, (0, 40, 9), 1.32, rid), "上から: 6 人の座り", "2 列 × 3 段"),
        panel(rnd(r, {}, 270, 4, (0, 44, 9), 1.32, rid), "横から: 6 人の座り", f"鞍 {SEAT_Y / 16:.3f} ブロック"),
        panel(rnd(r, G.pose_at("move", 0.3), 212, 15), "泳ぎ (move 0.3 s)", "尾・ひれ"),
        panel(rnd(r, G.pose_at("appear", 0.9), 212, 15, (0, 30, 9)), "現れの途中 (appear 0.9 s)", "歌のかけらから育つ"),
    ]
    head = 64
    gap = 8
    W = PW * 4
    H = head + 2 * (PH + 26) + gap + 24 + (PH + 26)
    sheet = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(sheet)
    d.text((12, 8), f"{TITLE} (young_stone_whale) — {name}", font=F(22), fill=WH)
    d.text((12, 38), f"三角形 {st['tris']}・アトラス {st['atlas']}・開いた辺 {st['open_edges']}・"
                     f"鞍の上面 {st['seat_min']:.2f}〜{st['seat_max']:.2f} 単位 (= {SEAT_Y / 16:.3f} ブロック)・"
                     f"大きさ 長さ {(st['hi'][2] - st['lo'][2]) / 16:.1f} × 幅 {(st['hi'][0] - st['lo'][0]) / 16:.1f} × "
                     f"高さ {(st['hi'][1] - st['lo'][1]) / 16:.1f} ブロック", font=F(14), fill=GR)
    for i, im in enumerate(ims):
        sheet.paste(im, (PW * (i % 4), head + (PH + 26) * (i // 4)))
    y = head + 2 * (PH + 26) + gap
    d.text((12, y + 2), "乗り方と動き (灰色の箱 = 座った人。幅 0.6・高さ 1.2 ブロック)", font=F(15), fill=WH)
    y += 24
    for i, im in enumerate(strip):
        sheet.paste(im, (PW * i, y))
    return sheet


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    opts, names, stt = {}, {}, {}
    for k in "abc":
        r, mod = get_option(k)
        opts[k], names[k] = r, mod.NAME
        stt[k] = st = stats(r)
        print(k, {x: st[x] for x in ("tris", "atlas", "open_edges", "open_by_bone", "seat_min", "seat_max", "seat_nan")},
              "lo", st["lo"], "hi", st["hi"])
        assert st["open_edges"] == 0, st["open_by_bone"]
        assert abs(st["seat_min"] - SEAT_Y) < 0.01 and abs(st["seat_max"] - SEAT_Y) < 0.01 and st["seat_nan"] == 0
        option_sheet(k, r, mod.NAME, st).save(OUT / f"stone_whale_{k}.png")
    ims = [panel(on_bg(render_pose(opts[k], {}, 212, 15, (PW + 60, PH + 40), CEN8, 1.6)).resize((PW, PH + 0)), names[k])
           for k in "abc"]
    W = Image.new("RGB", (PW * 3, PH + 26 + 40), (16, 16, 22))
    d = ImageDraw.Draw(W)
    d.text((12, 8), f"{TITLE} — 3 案 (前 3/4)", font=F(20), fill=WH)
    for i, im in enumerate(ims):
        W.paste(im, (PW * i, 40))
    W.save(OUT / "stone_whale_compare.png")
    desc = {"title": TITLE}
    for k in "abc":
        desc[k] = {"title": names[k], "desc": DESC[k]}
    (OUT / "stone_whale_desc.json").write_text(json.dumps(desc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
