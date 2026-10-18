"""灰被り (ash_thrall) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。

実行: python tools/rough/lowpoly/ash_thrall/sheet.py
出力: docs/review/lowpoly_roughs/lp_ash_thrall.png
      docs/review/lowpoly_roughs/lp_ash_thrall/{a,b,c}/ (本体・燃え上がり・火掻き棒のメッシュ JSON・アトラス・光るマスク)
      docs/review/lowpoly_roughs/lp_ash_thrall_manifest.json
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from shapes import *  # noqa
from view import POSES, get_option  # noqa
from old import old_faces  # noqa
from PIL import Image, ImageDraw  # noqa

CY, GR, WH, OR = (150, 232, 255), (160, 160, 176), (236, 232, 218), (255, 176, 90)
W = 1600
PW, GAP, X0 = 381, 12, 20
xs = [X0 + i * (PW + GAP) for i in range(4)]
NAMES = {"a": "A 灰衣の火番", "b": "B 鉱滓の鍛冶像", "c": "C 窯の人"}
POKER_REST = {"socket_main": (8.0, 0, 0)}     # 休めの傾き (-8 度) を打ち消して棒をまっすぐ立てる


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
        self.tn = {k: tri_state(r["body"], "n") for k, r in self.opts.items()}
        self.tf = {k: tri_state(r["body"], "f") for k, r in self.opts.items()}
        self.ta = {k: tri_count(r["body"]) for k, r in self.opts.items()}
        self.tw = {k: tri_count(r["weapon"]) for k, r in self.opts.items()}
        self.old_cache = {}
        self.canvas = Image.new("RGB", (W, 7200), (16, 16, 22))
        self.d = ImageDraw.Draw(self.canvas)
        self.y = 0

    def old(self, pose, weapon=True, body=True, state="n"):
        key = (tuple(sorted(pose.items())) if pose else (), weapon, body, state)
        if key not in self.old_cache:
            self.old_cache[key] = old_faces(pose or None, weapon, body, state)
        return self.old_cache[key]

    def panel(self, i, y, im, w, h):
        bg = bgpanel(w, h)
        if im.mode == "RGBA":
            bg.paste(im, (0, 0), im)
        else:
            bg.paste(im, (0, 0))
        self.canvas.paste(bg, (xs[i], y))
        self.d.rectangle([xs[i] - 1, y - 1, xs[i] + w, y + h], outline=(60, 60, 78))

    def fit(self, pose, yaw, pitch, w, h, state, body=True, weapon=True, margin=1.1, maxscale=16.0):
        V_ = rot_x(pitch) @ rot_y(yaw)
        pts = []
        for r in self.opts.values():
            fs = (pose_body(vis(r["body"], state), pose) if body else []) + (pose_staff(r["weapon"], pose) if weapon else [])
            for f in fs:
                pts.append(f.verts @ V_.T)
        pts = np.concatenate(pts)
        lo, hi = pts.min(0), pts.max(0)
        sc = min(w / ((hi[0] - lo[0]) * margin), h / ((hi[1] - lo[1]) * margin), maxscale)
        c2 = np.array([(lo[0] + hi[0]) / 2, (lo[1] + hi[1]) / 2, 0.0])
        return tuple(V_.T @ c2), sc

    def row(self, title, ph, pose, yaw, pitch, state="n", scale=None, cen=None, weapon=True, body=True, pose_old=None, tag=""):
        if scale is None:
            cen, scale = self.fit(pose, yaw, pitch, PW, ph, state, body, weapon)
        F = voxel.font
        self.d.text((X0, self.y), title, font=F(20), fill=CY)
        self.y += 30
        faces, n, m = self.old(pose_old if pose_old is not None else pose, weapon, body, state)
        self.panel(0, self.y, old_render(faces, yaw, pitch, (PW, ph), cen, scale), PW, ph)
        self.d.text((xs[0] + 2, self.y + ph + 6),
                    ("今 (箱 %d + 棒 %d)" % (n, m) if body else "今 (棒 箱 %d 個)" % m) if weapon else "今 (箱 %d 個)" % n, font=F(17), fill=WH)
        for i, k in enumerate("abc", start=1):
            r = self.opts[k]
            im = render_pose(r, pose, yaw, pitch, (PW, ph), cen, scale, state=state, body=body, weapon=weapon)
            self.panel(i, self.y, im, PW, ph)
            cnt = (self.tn[k] if state == "n" else self.tf[k]) if body else self.tw[k]
            self.d.text((xs[i] + 2, self.y + ph + 6), "%s  %d 三角形%s" % (NAMES[k], cnt, tag), font=F(17), fill=CY)
        self.y += ph + 38

    def para(self, text, size=15, fill=GR, x=28, lh=None, maxw=1540):
        """日本語の折り返し (ピクセル幅で)。self.y を進める。"""
        f = voxel.font(size)
        line = ""
        for ch in text:
            if self.d.textlength(line + ch, font=f) > maxw:
                self.d.text((x, self.y), line, font=f, fill=fill)
                self.y += lh or size + 5
                line = ch
            else:
                line += ch
        if line:
            self.d.text((x, self.y), line, font=f, fill=fill)
            self.y += lh or size + 5

    def build(self):
        F = voxel.font
        d = self.d
        d.text((28, 14), "灰被り (ash_thrall) — 低ポリ非立方体 3 案 + ドット絵テクスチャ (殻の姿 / 燃え上がりの姿)", font=F(26), fill=WH)
        self.y = 50
        self.para("骨 20 本 (root[Y 180 度] > body > head / arm_r > forearm_r > socket_main / arm_l > forearm_l、leg_r・leg_l、殻 crust_* 8 本、炎 flare_* 2 本) の名前・階層・pivot・"
                  "socket_main の初期回転 (-8 度) は今の geo と同じ。火掻き棒は別メッシュで socket_main に付く。1 ブロック = 32 テクセル。")
        self.para("左端が今のゲーム内のモデル (箱)。三角形は「その姿で見える数」(殻の姿は flare_* を、燃え上がりは crust_* を隠す。アトラスは姿ごとに 2 枚で UV は同じ)。"
                  "余燼の言葉 (灰・熾・玄武岩・冷える溶岩・ひび・鍛冶と窯) だけで作り、他の巨神の敵とは重ねない。")
        self.y = max(self.y + 6, 112)
        self.row("前 3/4 (殻の姿・休めの構え)", 600, {}, 205, 12)
        self.row("後ろ 3/4 (殻の姿)", 600, {}, 25, 12)
        self.row("顔のアップ (殻の姿・棒をはずして)", 380, {}, 200, 6, scale=17.0, cen=(0, 33.0, -1), weapon=False)
        self.row("燃え上がり 前 3/4 (殻が割れ落ちて、体のひびと炎が出る)", 600, {}, 205, 12, state="f")
        self.row("燃え上がり 後ろ 3/4", 600, {}, 25, 12, state="f")
        self.row("燃え上がりの顔のアップ (棒をはずして)", 380, {}, 200, 6, state="f", scale=17.0, cen=(0, 33.0, -1), weapon=False)
        self.row("攻撃・振りかぶり (attack 0.45 s: 右腕を頭上へ、棒を後ろへ倒す) 殻の姿 前 3/4", 700, POSES["wind"], 200, 10)
        self.row("攻撃・振り下ろし (attack 0.55 s) 燃え上がりの姿 横寄り", 700, POSES["slam"], 235, 10, state="f")
        self.row("火掻き棒のアップ (別メッシュ。geo/gear/ash_poker と同じ局所座標)", 420, POKER_REST, 205, 8, scale=11.0, cen=(5.0, 27.0, -2.0),
                 body=False, pose_old=POKER_REST)

        # アトラスと数字
        d.text((X0, self.y), "テクスチャのアトラス (殻の姿 / 燃え上がり / 火掻き棒、等倍。1 ブロック = 32 テクセル)", font=F(20), fill=CY)
        self.y += 30
        top = 0
        for i, k in enumerate("abc", start=1):
            r = self.opts[k]
            x = xs[i - 1] if False else X0 + (i - 1) * 520
            x0 = x
            for sz, arr in ((r["size"], r["atlas"]["n"]), (r["size"], r["atlas"]["f"]), (r["bsize"], r["batlas"])):
                at = Image.fromarray(arr, "RGBA")
                bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
                bgc.alpha_composite(at)
                self.canvas.paste(bgc.convert("RGB"), (x, self.y))
                d.rectangle([x - 1, self.y - 1, x + sz, self.y + sz], outline=(60, 60, 78))
                x += sz + 10
            d.text((x0, self.y + r["size"] + 6), "%s 本体 %d / 燃え上がり %d / 棒 %d" % (NAMES[k], r["size"], r["size"], r["bsize"]), font=F(15), fill=CY)
            top = max(top, r["size"])
        self.y += top + 40
        for k in "abc":
            r = self.opts[k]
            per = r["data"]["bones"]
            g = lambda *ns: sum(per[n]["triangle_count"] for n in ns)
            crust = g(*CRUST)
            flare = g(*FLARE)
            inner = self.ta[k] - crust - flare
            d.text((X0, self.y), "%s:  殻の姿 %d (中身 %d + 殻 %d) / 燃え上がり %d (中身 %d + 炎 %d) / 骨ごと全部 %d / 棒 %d"
                   % (NAMES[k], self.tn[k], inner, crust, self.tf[k], inner, flare, self.ta[k], self.tw[k]), font=F(17), fill=CY)
            self.y += 26
        self.y += 8
        for t in ("パレット (3〜5 色の階調で手描き風): A 灰 5 / 焦げ 4 / 熾 3 / 炎 4 / 鉄 4 / 青銅 4。B 鉱滓 5 + 暗い鉱滓 4 / 鉄 4 + 錆 4 / 熾・炎。"
                  "C 焼き土 5 + 暗い焼き土 4 / 灰の粉 4 / 煤 4 / 熾・炎。",
                  "光る画素: A は目・燃え上がりの体のひびの溶岩・炎・棒の先 (殻の冷えた割れ目は赤いが光らない)。B は細い覗き穴・鉱滓の火の粉 (少し)・火口・炎・棒の先。"
                  "C は通気の覗き穴 3 つ・燃え上がりの煉瓦の目地と赤熱した煉瓦・赤熱の輪・煙突の炎・戸の隙間・熊手の羽根。"):
            self.para(t)
        self.canvas = self.canvas.crop((0, 0, W, self.y + 14))
        out = OUT / "lp_ash_thrall.png"
        self.canvas.save(out)
        print("saved", out, self.canvas.size)


def manifest(s):
    t, tf, ta, tw = s.tn, s.tf, s.ta, s.tw
    sz = {k: (s.opts[k]["size"], s.opts[k]["bsize"]) for k in "abc"}
    data = {
        "id": "lp_ash_thrall",
        "short": "灰被り 低ポリ",
        "kind": "低ポリ 3案",
        "title": "灰被り (ash_thrall) — 低ポリ非立方体 + ドット絵 3 案 (余燼の背の敵。灰の殻をまとう下僕、割れると燃え上がる)",
        "sheets": ["lowpoly_roughs/lp_ash_thrall.png"],
        "extra": (
            "統一モデル様式 (低ポリ + 1 ブロック 32 テクセルのドット絵) の敵。骨 20 本 (root[Y 180 度] > body > head / arm_r > forearm_r > socket_main / arm_l > forearm_l、"
            "leg_r・leg_l、殻 crust_* 8 本、炎 flare_* 2 本) の名前・階層・pivot・socket_main の初期回転 (-8 度) は今の geo のまま、今のアニメ 7 本 "
            "(idle・walk・walk_flared・attack・hurt・shell_break・death) がそのまま動く。殻の姿は flare_* を隠し、燃え上がりは crust_* を隠してアトラスを替える (UV は同じ。今の ash_thrall / ash_thrall_flared と同じ仕組み)。"
            "火掻き棒 (ash_poker) は別メッシュで socket_main に付く (geo/gear と同じ局所座標)。"
            "三角形 (殻の姿 / 燃え上がり / 棒): A %d / %d / %d、B %d / %d / %d、C %d / %d / %d (普通の敵の目安 約 600 以下。棒は 250 以下)。今は箱 328 (殻 302 / 燃え上がり 3619 面) + 棒 20。"
            "アトラス: 本体 A %d・B %d・C %d (殻・燃え上がりで 2 枚) / 棒 A %d・B %d・C %d。光る画素: 目・覗き穴・炎・燃え上がりのひび・棒の先。"
            "全案、余燼の言葉 (灰・熾・玄武岩・冷える溶岩・ひび・鍛冶と窯) だけで作り、象牙の骨・珊瑚・貝・羽根・銅の嵐は使っていない。"
            "シートは今 / A / B / C を前 3/4・後ろ 3/4・顔のアップ (殻の姿と燃え上がり)・攻撃の振りかぶり (0.45 s) と振り下ろし (0.55 s、燃え上がり)・火掻き棒のアップ・アトラスで並べる。"
            "メッシュは lowpoly_roughs/lp_ash_thrall/{a,b,c}/ash_thrall_{a,b,c}_mesh.json (+ _atlas・_glowmask・_atlas_flared・_glowmask_flared) と _poker_mesh.json (+ atlas・glowmask)。"
            "生成: tools/rough/lowpoly/ash_thrall/sheet.py。"
        ) % (t["a"], tf["a"], tw["a"], t["b"], tf["b"], tw["b"], t["c"], tf["c"], tw["c"],
             sz["a"][0], sz["b"][0], sz["c"][0], sz["a"][1], sz["b"][1], sz["c"][1]),
        "options": {
            "a": {"name": "A 灰衣の火番",
                  "desc": "灰をかぶった頭巾と外套の火の番。縦に細長い輪郭。灰の板を重ねた尖り頭巾 (前が開いて、黒い顔に光る目)、肩の二段の灰の板、前が割れた長い外套 (裾は欠け)。"
                          "殻の面には冷えた割れ目と、ところどころ赤い熱の線。殻が割れると黒く焦げた細い体に溶岩のひびが走り、頭と肩・背から炎が上がる。"
                          "棒は鉄・青銅の輪・先の鉤に燠火。今のモデルの延長で、最も読みやすい。"},
            "b": {"name": "B 鉱滓の鍛冶像",
                  "desc": "溶けた鉄のかす (鉱滓) が冷えて固まった、肩幅の広い鍛冶の像。不ぞろいな鉱滓の塊の肩当てと棘、鉄の前掛け (錆びた鉄の板)、平たい兜 (覗き穴が一本の光る線、天辺に鉱滓の棘の冠)。"
                          "ところどころ火の粉が光る。殻が割れると胸の火口が開き、熱い鉄の体に溶岩のひび。肩と背から炎。棒は太い鉄に鉱滓のこぶ、先は鉄床の角の鉤。"
                          "最も重く幅広い輪郭。"},
            "c": {"name": "C 窯の人",
                  "desc": "灰の民の窯がそのまま人になった焼き物の像。煙突の頭 (縁が煤け、前に光る通気の覗き穴)、輪を重ねた焼き物の腹と胸の火口の戸、背に煙道の管、肩に灰のかかった蓋。"
                          "殻が割れると煙突の口から炎が吹き上がり、体の赤いひびが透ける。棒は粘土の握りに熊手の羽根 (縁が光る)。"
                          "煙突の頭と管で、他の 2 案と最も違う縦の輪郭。"},
        },
    }
    (OUT / "lp_ash_thrall_manifest.json").write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    s = Sheet()
    print("tris n", s.tn, "f", s.tf, "all", s.ta, "weapon", s.tw)
    s.build()
    manifest(s)
