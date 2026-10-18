"""潮招き (tide_beckoner) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。

実行: python tools/rough/lowpoly/tide_beckoner/sheet.py
出力: docs/review/lowpoly_roughs/lp_tide_beckoner.png
      docs/review/lowpoly_roughs/lp_tide_beckoner/{a,b,c}/ (mesh json, atlas, glowmask)
      docs/review/lowpoly_roughs/lp_tide_beckoner_manifest.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from view import POSES, get_option  # noqa
from PIL import Image, ImageDraw  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 瓦の大蟹", "b": "B 珊瑚の山", "c": "C 潮見の塔"}
# 案ごとの見せ方 (頭のアップ: 中心, 縮尺 / 鋏のアップ: 中心, 縮尺)
HEAD = {"a": ((0, 40, -20), 7.0), "b": ((0, 42, -14), 6.0), "c": ((0, 52, -16), 4.6), "old": ((0, 40, -20), 7.0)}
CLAW = {"a": ((45, 24, -72), 5.6), "b": ((45, 24, -74), 5.4), "c": ((45, 24, -78), 5.0), "old": ((40, 22, -62), 5.6)}
BODY_CEN = (0, 28, -34)
BODY_SC = 2.7


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_render(faces, yaw, pitch, size, center, scale):
    """今のモデル (voxel) を、新しい描画と同じ縮尺・中心で描く (全体を描いてから、中心の周りを切り出す)。"""
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    V = Rx @ Ry
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(V @ (f.origin + a * f.eu + b * f.ev))
    sc = V @ np.asarray(center, float)
    hx, hy = size[0] / (2 * scale) + 2, size[1] / (2 * scale) + 2
    for a in (-1, 1):
        for b in (-1, 1):
            pts.append(sc + np.array([a * hx, b * hy, 0.0]))
    pts = np.array(pts)
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = int(math.ceil(span * scale))
    eff = S / span
    im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(S, S), margin=0.0)
    cx_, cy_ = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = S / 2 + (sc[0] - cx_) * eff
    py = S / 2 - (sc[1] - cy_) * eff
    out = bgpanel(size[0], size[1])
    x0, y0 = int(round(px - size[0] / 2)), int(round(py - size[1] / 2))
    out.paste(im, (-x0, -y0))
    return out


class Sheet:
    def __init__(self):
        self.opts = {}
        for k in "abc":
            r, mod = get_option(k)
            r["label"] = NAMES[k]
            r["mod"] = mod
            self.opts[k] = r
        self.tri = {k: tri_count(r["body"]) for k, r in self.opts.items()}
        self.old_cache = {}
        self.canvas = Image.new("RGB", (W, 6200), (16, 16, 22))
        self.d = ImageDraw.Draw(self.canvas)
        self.y = 0

    def old(self, pose):
        key = tuple(sorted(POSES[pose].items())) if pose else ()
        if key not in self.old_cache:
            self.old_cache[key] = old_faces(POSES[pose] if pose else None)
        return self.old_cache[key]

    def panel(self, i, y, im, w, h):
        bg = bgpanel(w, h)
        if im.mode == "RGBA":
            bg.paste(im, (0, 0), im)
        else:
            bg.paste(im, (0, 0))
        self.canvas.paste(bg, (xs[i], y))
        self.d.rectangle([xs[i] - 1, y - 1, xs[i] + w, y + h], outline=(60, 60, 78))

    def row(self, title, ph, pose, yaw, pitch, scale=BODY_SC, cen=BODY_CEN, cens=None, sub=None, pw=PW):
        F = voxel.font
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        faces, n = self.old(pose)
        c0 = (cens or {}).get("old", cen)
        s0 = (sub or {}).get("old", scale)
        self.panel(0, self.y, old_render(faces, yaw, pitch, (pw, ph), c0, s0), pw, ph)
        self.d.text((xs[0] + 2, self.y + ph + 6), "今 (箱 %d 個)" % n, font=F(17), fill=WH)
        for i, k in enumerate("abc", start=1):
            r = self.opts[k]
            c1 = (cens or {}).get(k, cen)
            s1 = (sub or {}).get(k, scale)
            im = render_pose(r, POSES[pose], yaw, pitch, (pw, ph), c1, s1)
            self.panel(i, self.y, im, pw, ph)
            self.d.text((xs[i] + 2, self.y + ph + 6), "%s  %d 三角形" % (NAMES[k], self.tri[k]), font=F(17), fill=CY)
        self.y += ph + 38

    def build(self):
        F = voxel.font
        d = self.d
        d.text((28, 14), "潮招き (tide_beckoner) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
        d.text((28, 50), "骨 12 本 (root[Y 180 度] > body > claw_big > claw_big_pincer / claw_small / eyes / leg_r1-3・leg_l1-3) の名前・階層・pivot は今の geo と同じ。"
                         "武器なし。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
        d.text((28, 72), "左端が今のゲーム内のモデル (箱)。三角形は全身の数 (中ボスの目安 約 1,500 以下)。大きいはさみはゲームの右 (画面では左手前)。招く・打ち下ろしの姿勢は今のアニメの値。", font=F(15), fill=GR)
        self.y = 104
        pose = "rest"
        self.row("前 3/4 (休めの構え)", 320, pose, 212, 15)
        self.row("後ろ 3/4", 320, pose, 32, 15)
        self.row("大きなはさみのアップ (前 3/4)", 330, pose, 218, 14,
                 cens={k: v[0] for k, v in CLAW.items()}, sub={k: v[1] for k, v in CLAW.items()})
        self.row("頭まわりのアップ (目・冠・背)", 330, pose, 200, 12,
                 cens={k: v[0] for k, v in HEAD.items()}, sub={k: v[1] for k, v in HEAD.items()})
        self.row("打ち下ろしの構え (slam_raise 1.0 s: はさみを振り上げる)", 380, "raise", 150, 14, cen=(0, 40, -14), scale=2.0)
        self.row("招き潮 (beckon 0.45 s: はさみを持ち上げて手招く)", 400, "beckon", 212, 12, cen=(0, 52, -34), scale=2.0)
        self.row("横から (休め): 長さと高さ", 300, pose, 270, 6, cen=(0, 32, -40), scale=2.1)

        # アトラスと数字
        d.text((X0, self.y), "テクスチャのアトラス (512×512 を等倍表示)", font=F(20), fill=CY)
        self.y += 30
        ax = [X0 + i * (512 + GAP) for i in range(3)]
        for i, k in enumerate("abc"):
            r = self.opts[k]
            at = Image.fromarray(r["atlas"], "RGBA")
            bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
            bgc.alpha_composite(at)
            self.canvas.paste(bgc.convert("RGB"), (ax[i], self.y))
            d.rectangle([ax[i] - 1, self.y - 1, ax[i] + r["size"], self.y + r["size"]], outline=(60, 60, 78))
            d.text((ax[i], self.y + r["size"] + 6), "%s  アトラス %d×%d" % (NAMES[k], r["size"], r["size"]), font=F(16), fill=CY)
        self.y += 512 + 36
        for k in "abc":
            r = self.opts[k]
            per = r["data"]["bones"]
            g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
            d.text((X0, self.y), "%s:  全身 %d 三角形 (胴 %d / 大鋏 %d+指 %d / 小鋏 %d / 目 %d / 脚 %d×6)"
                   % (NAMES[k], self.tri[k], g("body"), g("claw_big"), g("claw_big_pincer"), g("claw_small"), g("eyes"),
                      g("leg_r1")), font=F(17), fill=CY)
            self.y += 26
        self.y += 8
        for t in ("パレット (案ごとに 3〜5 色の階調で手描き風): A 石板の青灰 5 / 赤茶の殻 6 / 藤壺の白 4 / 苔 4 / 海硝子の水色 3。"
                  "B 岩の青緑 5 / 蟹の暗い臙脂 6 / 珊瑚の桃白 5 / 海硝子 3。C 骨白 6 / 潮の青緑 5 / 塔の石 5 / 真鍮 5 / 海硝子 3。",
                  "光る画素は海硝子 (はさみの先・灯・目の瞳) だけ。目盛りの赤 (C) は塗りで、光らない。"):
            d.text((X0, self.y), t, font=F(15), fill=GR)
            self.y += 24
        self.canvas = self.canvas.crop((0, 0, W, self.y + 14))
        out = OUT / "lp_tide_beckoner.png"
        self.canvas.save(out)
        print("saved", out, self.canvas.size)


if __name__ == "__main__":
    s = Sheet()
    print("tris", s.tri)
    s.build()
