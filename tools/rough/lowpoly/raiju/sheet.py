"""雷獣 (raiju) 低ポリ 3 案の比較シート + メッシュ書き出し (opt_*.py を読む)。

実行: python tools/rough/lowpoly/raiju/sheet.py
出力: docs/review/lowpoly_roughs/lp_raiju.png
      docs/review/lowpoly_roughs/lp_raiju/{a,b,c}/ (mesh json, atlas, glowmask)
"""
from __future__ import annotations

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
NAMES = {"a": "A 嵐の狼", "b": "B 羽鬣の獅子", "c": "C 銅角の雷獣"}
# 案ごとの見せ方 (中心, 縮尺)
BODY = {"old": ((0, 30, 8), 3.4), "a": ((0, 33, 8), 3.4), "b": ((0, 35, 8), 3.4), "c": ((0, 44, 8), 3.0)}
HEAD = {"old": ((0, 40, -36), 8.0), "a": ((0, 44, -34), 7.0), "b": ((0, 44, -30), 5.6), "c": ((0, 56, -30), 4.6)}
SIDE = {"old": ((0, 30, 8), 3.4), "a": ((0, 33, 8), 3.4), "b": ((0, 35, 8), 3.4), "c": ((0, 44, 8), 3.0)}
LEAP = {"old": ((0, 30, 6), 2.7), "a": ((0, 32, 6), 2.7), "b": ((0, 33, 6), 2.7), "c": ((0, 42, 6), 2.5)}
CLAW = {"old": ((0, 32, -8), 3.2), "a": ((0, 34, -8), 3.2), "b": ((0, 35, -8), 3.2), "c": ((0, 42, -8), 2.9)}


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
    pts = np.array(pts)
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    S = int(math.ceil(span * scale))
    eff = S / span
    bgf = voxel._background
    voxel._background = lambda W_, H_, bg: np.full((H_, W_, 3), 3.0)
    try:
        im = voxel.render(faces, yaw=yaw, pitch=pitch, size=(S, S), margin=0.0)
    finally:
        voxel._background = bgf
    mask = Image.fromarray((np.asarray(im).max(-1) > 6).astype(np.uint8) * 255)
    cx_, cy_ = (lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2
    px = S / 2 + (sc[0] - cx_) * eff
    py = S / 2 - (sc[1] - cy_) * eff
    out = bgpanel(size[0], size[1])
    x0, y0 = int(round(px - size[0] / 2)), int(round(py - size[1] / 2))
    out.paste(im, (-x0, -y0), mask)
    return out


class Sheet:
    def __init__(self, keys="abc"):
        self.keys = keys
        self.opts = {}
        for k in keys:
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

    def row(self, title, ph, pose, yaw, pitch, table, pw=PW, yaws=None):
        F = voxel.font
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        faces, n = self.old(pose)
        c0, s0 = table["old"]
        self.panel(0, self.y, old_render(faces, (yaws or {}).get("old", yaw), pitch, (pw, ph), c0, s0), pw, ph)
        self.d.text((xs[0] + 2, self.y + ph + 6), "今 (箱 %d 個)" % n, font=F(17), fill=WH)
        for i, k in enumerate(self.keys, start=1):
            r = self.opts[k]
            c1, s1 = table[k]
            im = render_pose(r, POSES[pose], (yaws or {}).get(k, yaw), pitch, (pw, ph), c1, s1)
            self.panel(i, self.y, im, pw, ph)
            self.d.text((xs[i] + 2, self.y + ph + 6), "%s  %d 三角形" % (NAMES[k], self.tri[k]), font=F(17), fill=CY)
        self.y += ph + 38

    def build(self):
        F = voxel.font
        d = self.d
        d.text((28, 14), "雷獣 (raiju) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
        d.text((28, 50), "骨 17 本 (root[Y 180 度] > body > chest > head > jaw / mane / tail > tail_2 > tail_3 / leg_fl・fr・bl・br > *_lo) の名前・階層・pivot は"
                         "今の geo と同じ。武器なし。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
        d.text((28, 72), "左端が今のゲーム内のモデル (箱)。三角形は全身の数 (中ボスの目安 約 1,500 以下)。"
                         "姿勢は今のアニメの値 (pounce_leap 0.5 s、claw 0.35 s)。", font=F(15), fill=GR)
        self.y = 104
        self.row("前 3/4 (休め)", 360, "rest", 212, 14, BODY)
        self.row("後ろ 3/4", 360, "rest", 32, 14, BODY)
        self.row("頭まわりのアップ (目・牙・耳・角/襟)", 400, "rest", 205, 10, HEAD)
        self.row("横から (休め): 輪郭", 280, "rest", 270, 5, SIDE)
        self.row("飛びかかり (pounce_leap 0.5 s: 前脚を伸ばし、尾を立てて跳ぶ)", 340, "leap", 250, 10, LEAP)
        self.row("爪の連打 (claw 0.35 s: 右の前脚を振り上げ、口を開く)", 400, "claw", 215, 12, CLAW)

        # アトラスと数字
        d.text((X0, self.y), "テクスチャのアトラス (512×512 を等倍表示)", font=F(20), fill=CY)
        self.y += 30
        ax = [X0 + i * (512 + GAP) for i in range(3)]
        for i, k in enumerate(self.keys):
            r = self.opts[k]
            at = Image.fromarray(r["atlas"], "RGBA")
            bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
            bgc.alpha_composite(at)
            self.canvas.paste(bgc.convert("RGB"), (ax[i], self.y))
            d.rectangle([ax[i] - 1, self.y - 1, ax[i] + r["size"], self.y + r["size"]], outline=(60, 60, 78))
            d.text((ax[i], self.y + r["size"] + 6), "%s  アトラス %d×%d" % (NAMES[k], r["size"], r["size"]), font=F(16), fill=CY)
        self.y += 512 + 36
        for k in self.keys:
            r = self.opts[k]
            per = r["data"]["bones"]
            g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
            legs = g("leg_fl", "leg_fl_lo", "leg_fr", "leg_fr_lo", "leg_bl", "leg_bl_lo", "leg_br", "leg_br_lo")
            d.text((X0, self.y), "%s:  全身 %d 三角形 (胴+胸 %d / 頭+顎 %d / 鬣 %d / 脚 4 本 %d / 尾 3 節 %d)"
                   % (NAMES[k], self.tri[k], g("body", "chest"), g("head", "jaw"), g("mane"), legs, g("tail", "tail_2", "tail_3")),
                   font=F(17), fill=CY)
            self.y += 26
        self.y += 8
        for t in ("パレット (案ごとに 3〜6 色の階調で手描き風): A 灰青の毛 6 / 霜の白 5 / 銅 5 / 水晶の青 4。"
                  "B 嵐の紺 6 / 羽根 6 + 先の白 2 / 銅 5。C 鉄色 6 / 銅 6+4 / がいしの白 4 / 水晶 4。",
                  "光る画素: A は水晶の鬣・尾の刃・体の稲妻の線・瞳、B は羽根の先の白と瞳、C は水晶の角・牙・尾の球・避雷針の先・瞳。"):
            d.text((X0, self.y), t, font=F(15), fill=GR)
            self.y += 24
        self.canvas = self.canvas.crop((0, 0, W, self.y + 14))
        out = OUT / "lp_raiju.png"
        self.canvas.save(out)
        print("saved", out, self.canvas.size)


if __name__ == "__main__":
    s = Sheet("abc")
    print("tris", s.tri)
    s.build()
