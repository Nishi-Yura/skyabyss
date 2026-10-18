"""雷乞い (thunder_supplicant) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。

実行: python tools/rough/lowpoly/thunder_supplicant/sheet.py
出力: docs/review/lowpoly_roughs/lp_thunder_supplicant.png
      docs/review/lowpoly_roughs/lp_thunder_supplicant/{a,b,c}/ (本体と杖のメッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/lp_thunder_supplicant_manifest.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from view import POSES, get_option  # noqa
from old import old_faces  # noqa
from PIL import Image, ImageDraw  # noqa

CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 風見の籠", "b": "B 羽の司祭", "c": "C 蓑の雷乞い"}
STAFF_REST = {"socket_main": (25.0, 0, 0)}     # 休めの傾き (-25 度) を打ち消して杖をまっすぐ立てる


def bgpanel(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_render(faces, yaw, pitch, size, center, scale):
    """今のモデル (voxel) を、新しい描画と同じ縮尺・中心で描く (大きく描いてから中心の周りを切り出す)。"""
    cy, sy_ = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp_ = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    Ry = np.array([[cy, 0, sy_], [0, 1, 0], [-sy_, 0, cy]])
    Rx = np.array([[1, 0, 0], [0, cp, -sp_], [0, sp_, cp]])
    V_ = Rx @ Ry
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(V_ @ (f.origin + a * f.eu + b * f.ev))
    sc = V_ @ np.asarray(center, float)
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
            self.opts[k] = r
        self.tri = {k: tri_count(r["body"]) for k, r in self.opts.items()}
        self.stri = {k: tri_count(r["staff"]) for k, r in self.opts.items()}
        self.old_cache = {}
        self.canvas = Image.new("RGB", (W, 5600), (16, 16, 22))
        self.d = ImageDraw.Draw(self.canvas)
        self.y = 0

    def old(self, pose, staff=True, body=True):
        key = (tuple(sorted(pose.items())) if pose else (), staff, body)
        if key not in self.old_cache:
            self.old_cache[key] = old_faces(pose or None, staff, body)
        return self.old_cache[key]

    def panel(self, i, y, im, w, h):
        bg = bgpanel(w, h)
        if im.mode == "RGBA":
            bg.paste(im, (0, 0), im)
        else:
            bg.paste(im, (0, 0))
        self.canvas.paste(bg, (xs[i], y))
        self.d.rectangle([xs[i] - 1, y - 1, xs[i] + w, y + h], outline=(60, 60, 78))

    def fit(self, pose, yaw, pitch, w, h, body=True, staff=True, margin=1.1, maxscale=16.0):
        """3 案の姿勢つきの頂点を全部入れて、中心と縮尺を決める。"""
        V_ = rot_x(pitch) @ rot_y(yaw)
        pts = []
        for r in self.opts.values():
            fs = (pose_body(r["body"], pose) if body else []) + (pose_staff(r["staff"], pose) if staff else [])
            for f in fs:
                pts.append(f.verts @ V_.T)
        pts = np.concatenate(pts)
        lo, hi = pts.min(0), pts.max(0)
        sc = min(w / ((hi[0] - lo[0]) * margin), h / ((hi[1] - lo[1]) * margin), maxscale)
        c2 = np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, 0.0])
        return tuple(V_.T @ c2), sc

    def row(self, title, ph, pose, yaw, pitch, scale=None, cen=None, staff=True, body=True, pose_old=None, old_cen=None, tag=""):
        if scale is None:
            cen, scale = self.fit(pose, yaw, pitch, PW, ph, body, staff)
        F = voxel.font
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        faces, n, m = self.old(pose_old if pose_old is not None else pose, staff, body)
        self.panel(0, self.y, old_render(faces, yaw, pitch, (PW, ph), old_cen or cen, scale), PW, ph)
        self.d.text((xs[0] + 2, self.y + ph + 6), ("今 (箱 %d + 杖 %d)" % (n, m) if body else "今 (杖 箱 %d 個)" % m) if staff else "今 (箱 %d 個)" % n, font=F(17), fill=WH)
        for i, k in enumerate("abc", start=1):
            r = self.opts[k]
            im = render_pose(r, pose, yaw, pitch, (PW, ph), cen, scale, body=body, staff=staff)
            self.panel(i, self.y, im, PW, ph)
            self.d.text((xs[i] + 2, self.y + ph + 6), "%s  %d 三角形%s" % (NAMES[k], self.tri[k] if body else self.stri[k], tag), font=F(17), fill=CY)
        self.y += ph + 38

    def build(self):
        F = voxel.font
        d = self.d
        d.text((28, 14), "雷乞い (thunder_supplicant) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
        d.text((28, 50), "骨 9 本 (root[Y 180 度] > body > head / robe / arm_r > forearm_r > socket_main / arm_l > forearm_l) の名前・階層・pivot は今の geo と同じ。"
                         "杖は別メッシュで socket_main に付く。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
        d.text((28, 72), "左端が今のゲーム内のモデル (箱)。三角形は本体の数 (普通の敵の目安 約 600 以下。杖は別)。祈りは今のアニメ cast の途中 (0.6 秒) の値。"
                         "雷翼の言葉 (嵐・静電気・銅・羽・鳥籠・法衣) だけで、他の巨神の敵とは重ねない。", font=F(15), fill=GR)
        self.y = 104
        self.row("前 3/4 (休めの構え)", 600, {}, 205, 14)
        self.row("後ろ 3/4", 600, {}, 25, 14)
        self.row("鳥籠の頭のアップ (杖をはずして)", 380, {}, 200, 8, 16.0, (0, 26.0, -2), staff=False)
        self.row("祈り・招雷 (cast 0.6 s: 杖を頭上へ掲げ、頭をそらす) 前 3/4", 700, POSES["cast"], 160, 12)
        self.row("祈り・招雷 横から", 700, POSES["cast"], 250, 12)
        # 杖のアップ
        self.row("杖の先のアップ (別メッシュ。geo/gear/supplicant_staff と同じ局所座標)", 400, STAFF_REST, 205, 10, 11.0, (4.8, 36.0, -5.2),
                 body=False, pose_old=STAFF_REST)

        # アトラスと数字
        d.text((X0, self.y), "テクスチャのアトラス (本体 + 杖、等倍・正方形。1 ブロック = 32 テクセル)", font=F(20), fill=CY)
        self.y += 30
        Z = 1
        x = X0
        for i, k in enumerate("abc"):
            r = self.opts[k]
            x0 = x
            for j, (key, sz) in enumerate((("atlas", r["size"]), ("batlas", r["bsize"]))):
                at = Image.fromarray(r[key], "RGBA").resize((sz * Z, sz * Z), Image.NEAREST)
                bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
                bgc.alpha_composite(at)
                self.canvas.paste(bgc.convert("RGB"), (x, self.y))
                d.rectangle([x - 1, self.y - 1, x + sz * Z, self.y + sz * Z], outline=(60, 60, 78))
                x += sz * Z + 12
            d.text((x0, self.y + max(r["size"], r["bsize"]) * Z + 6), "%s 本体 %d 杖 %d" % (NAMES[k], r["size"], r["bsize"]),
                   font=F(16), fill=CY)
            x += 18
        self.y += max(max(r["size"], r["bsize"]) for r in self.opts.values()) * Z + 40
        for k in "abc":
            r = self.opts[k]
            per = r["data"]["bones"]
            g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
            d.text((X0, self.y), "%s:  本体 %d 三角形 (頭 %d / 胴 %d / 法衣 %d / 右腕 %d+前腕 %d / 左腕 %d+前腕 %d) + 杖 %d"
                   % (NAMES[k], self.tri[k], g("head"), g("body"), g("robe"), g("arm_r"), g("forearm_r"), g("arm_l"), g("forearm_l"), self.stri[k]),
                   font=F(17), fill=CY)
            self.y += 26
        self.y += 8
        for t in ("パレット (3〜5 色の階調で手描き風): A 石板の青灰 5 / 羽の白灰 4 / 銅 5 / 緑青 3 / 雷の青白 3。"
                  "B 羽の白灰 5 / 灰青 4 / 銅 5 / 雷の白金 4。C 藁 5 / 藍 4 / 銅 5 / 紙の青白 3 (黒い稲妻の影) / 竹 4。",
                  "光る画素: A は籠の中の稲妻と胸の珠、杖の先。B は籠の中の鳥と杖の羽。C は灯籠の紙の面 (稲妻の影は光らない) と杖の珠。"):
            d.text((X0, self.y), t, font=F(15), fill=GR)
            self.y += 24
        self.canvas = self.canvas.crop((0, 0, W, self.y + 14))
        out = OUT / "lp_thunder_supplicant.png"
        self.canvas.save(out)
        print("saved", out, self.canvas.size)


def manifest(s):
    t, st = s.tri, s.stri
    sz = {k: (s.opts[k]["size"], s.opts[k]["bsize"]) for k in "abc"}
    data = {
        "id": "lp_thunder_supplicant",
        "short": "雷乞い 低ポリ",
        "kind": "低ポリ 3案",
        "title": "雷乞い (thunder_supplicant) — 低ポリ非立方体 + ドット絵 3 案 (雷翼の背の敵。鳥籠の頭の法衣)",
        "sheets": ["lowpoly_roughs/lp_thunder_supplicant.png"],
        "extra": (
            "統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の敵。骨 9 本 (root[Y 180 度] > body > head / robe / arm_r > forearm_r > "
            "socket_main / arm_l > forearm_l) の名前・階層・pivot・socket_main の初期回転 (-25 度) は今の geo のまま、今のアニメ 6 本 "
            "(idle・walk・cast・attack・hurt・death) がそのまま動く。杖 (supplicant_staff) は別メッシュで socket_main に付く (geo/gear と同じ局所座標)。"
            "三角形 (本体 + 杖): A %d + %d / B %d + %d / C %d + %d (普通の敵の目安 約 600 以下)。今は箱 295 + 杖 49。"
            "アトラス: 本体 A %d・B %d・C %d / 杖 %d。光る画素: A 籠の稲妻・胸の珠・杖の先 / B 籠の鳥・杖の羽 / C 灯籠の紙の面・杖の珠。"
            "全案、雷翼の言葉 (嵐・静電気・銅・羽・鳥籠・法衣) だけで作り、岩鯨の骨の敵などとは重ねていない。"
            "シートは今 / A / B / C を前 3/4・後ろ 3/4・籠の頭のアップ・祈り (cast 0.6 s) 前 3/4 と横・杖のアップ・アトラスで並べる。"
            "メッシュは lowpoly_roughs/lp_thunder_supplicant/{a,b,c}/thunder_supplicant_{a,b,c}_mesh.json と _staff_mesh.json (+ atlas・glowmask)。"
            "生成: tools/rough/lowpoly/thunder_supplicant/sheet.py。"
        ) % (t["a"], st["a"], t["b"], st["b"], t["c"], st["c"], sz["a"][0], sz["b"][0], sz["c"][0], sz["a"][1]),
        "options": {
            "a": {"name": "A 風見の籠",
                  "desc": "縦に細長い鐘と灯籠。銅の鳥籠の頭 (腹のふくらんだ 6 本の柱、玉ねぎ屋根、天辺に風見の鶏) の中に、稲妻が前と後ろに走る。"
                          "法衣は襞のある青灰の鐘形で、裾は白い羽の鋸歯、背に尾羽。肩に羽板、銅の襟と棘の飾り、胸に光る珠、袖口は銅。"
                          "杖は銅の避雷針の三叉 (先に光る珠)。今のモデルの延長で、最も落ち着いた輪郭。"},
            "b": {"name": "B 羽の司祭",
                  "desc": "白と灰の羽の外套を着た鳥の司祭。肩から 3 段に垂れる羽の外套、袖から翼のように垂れる羽板。丸い銅の鳥籠の中に翼を広げた金白の雷の鳥、"
                          "頭の後ろに羽の扇、天に銅の避雷針 7 本の冠。杖は止まり木の鉤で、先から金白の羽の灯がぶら下がる。"
                          "針の冠と広がる白い羽で、最も鳥らしく読める輪郭。"},
            "c": {"name": "C 蓑の雷乞い",
                  "desc": "雨乞いの巡礼。藁の蓑を何段も重ねた裾広がりの外套 (藁の鋸歯、銅線がささくれ立つ)、縁から銅線の立つ大きな笠。"
                          "笠の下は六角の紙の灯籠のような籠で、光る面に黒い稲妻の影。藍の細い袖に銅の腕輪。杖は錫杖 (銅の輪の冠に光る珠)。"
                          "大きな笠と藁山の輪郭で、他の 2 案と最も違う。"},
        },
    }
    (OUT / "lp_thunder_supplicant_manifest.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    s = Sheet()
    print("tris", s.tri, s.stri)
    s.build()
    manifest(s)
