"""鯨骨の番兵長 (whalebone_captain) 低ポリ 3 案の比較シート + メッシュ書き出し + manifest。

実行: python tools/rough/lowpoly/whalebone_captain/sheet.py
出力: docs/review/lowpoly_roughs/whalebone_captain.png
      docs/review/lowpoly_roughs/whalebone_captain/{a,b,c}/ (mesh json, atlas, glowmask, 鉾の mesh + atlas)
      docs/review/lowpoly_roughs/whalebone_captain_manifest.json
"""
from __future__ import annotations

import sys

from view import *  # noqa
import captain_v4 as c4  # noqa
from voxel import Face as VFace, font as vfont, load_geo  # noqa

PW, GAP = 381, 12
SC = 7.0                    # 図の縮尺 (1 単位あたり px)。今のモデルと同じにそろえる
CEN = (0, 26.5, 0)
SPAN_R, Y0, Y1 = 30.0, -3.0, 58.0


def dummies():
    """voxel.render の自動縮尺を固定するための透明な面 (円周上の点)。"""
    out = []
    for yv in (Y0, Y1):
        for k in range(16):
            a = 2 * math.pi * k / 16
            out.append(VFace(np.array([SPAN_R * math.cos(a), yv, SPAN_R * math.sin(a)]), np.array([0.05, 0, 0]),
                             np.array([0, 0.05, 0]), np.zeros((1, 1, 4), np.uint8), None, 1.0))
    return out


def span_for(pitch):
    p = math.radians(pitch)
    return (Y1 - Y0) * math.cos(p) + 2 * SPAN_R * math.sin(p)


class Current:
    def __init__(self):
        self.geo = GAME / "geo/entity/whalebone_captain.geo.json"
        self.tex = GAME / "textures/entity/whalebone_captain.png"
        self.glow = GAME / "textures/entity/whalebone_captain_glowmask.png"
        self.pgeo, self.ptex = c4.pike_geo()
        g = json.loads(self.geo.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"]
        self.boxes = sum(len(b.get("cubes", [])) for b in g)
        pb = {n: tuple(p) for n, p, _ in c4.NEW_BONES}
        for b in g:
            assert tuple(b["pivot"]) == pb[b["name"]], b["name"]

    def faces(self, pose, pike=True):
        f = load_geo(self.geo, self.tex, self.glow, pose=pose)
        self.nfaces = len(f)
        if pike:
            f += load_geo(self.pgeo, self.ptex, None, pose=pose)
        return f

    def render(self, pose, yaw, pitch, size, scale):
        f = self.faces(pose) + dummies()
        sp = span_for(pitch)
        m = (1 - scale * sp / min(size)) / 2
        return render_v(f, yaw, pitch, size, m)

    def render_head(self, pose, yaw, pitch, size, scale):
        g = json.loads(self.geo.read_text(encoding="utf-8"))
        for b in g["minecraft:geometry"][0]["bones"]:
            if b["name"] != "head":
                b["cubes"] = []
        f = load_geo(g, self.tex, self.glow, pose=pose)
        for y in (30.0, 54.0):
            f.append(VFace(np.array([0.0, y, 0.0]), np.array([0.05, 0, 0]), np.array([0, 0.05, 0]),
                           np.zeros((1, 1, 4), np.uint8), None, 1.0))
        m = (1 - scale * 24.0 * math.cos(math.radians(pitch)) / min(size)) / 2
        return render_v(f, yaw, pitch, size, m)

    def render_pike(self, yaw, pitch, size, scale):
        f = load_geo(self.pgeo, self.ptex, None, pose={})
        # 鉾のまわりに透明な点を置いて、縮尺を決める
        pts = []
        for z in (-30.0, 14.0):
            for y in (-14.0, 4.0):
                pts.append(VFace(np.array([0.0, y, z]) + np.array(c4.PIV["socket_main"]), np.array([0.05, 0, 0]),
                                 np.array([0, 0.05, 0]), np.zeros((1, 1, 4), np.uint8), None, 1.0))
        m = (1 - scale * 44.0 / min(size)) / 2
        return render_v(f + pts, yaw, pitch, size, m)


def render_v(faces, yaw, pitch, size, margin):
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=margin)


def pbg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def panel(canvas, x, y, im, w, h, frame=True):
    if im.mode == "RGBA":
        bg = pbg(w, h)
        bg.paste(im, (0, 0), im)
        im = bg
    canvas.paste(im, (x, y))
    d = ImageDraw.Draw(canvas)
    d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))


def main():
    cur = Current()
    opts = {}
    for key in ("a", "b", "c"):
        r = get_option(key)
        export_option(r, key)
        r["big"], r["bglow"], r["yoff"] = combined(r)
        opts[key] = r
    names = {"a": "A 石鎧の将", "b": "B 鯨骨の亡者将軍", "c": "C 岩鯨の石像守"}
    tri = {k: (tri_count(r["body"]), tri_count(r["pike"])) for k, r in opts.items()}
    print("tris", tri)

    F = vfont
    CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)
    W = 1600
    X0 = 20
    xs = [X0 + i * (PW + GAP) for i in range(4)]
    canvas = Image.new("RGB", (W, 4800), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "鯨骨の番兵長 (whalebone_captain) — 低ポリ非立方体 3 案 + ドット絵テクスチャ", font=F(26), fill=WH)
    d.text((28, 50), "骨 20 本 (body / head / leg×2 / arm・forearm・hand・socket×左右 / spine0-7) の名前・階層・pivot は今の geo と同じ。"
                     "鉾は別メッシュ (socket_main の原点・1.8 倍込み)。1 ブロック = 32 テクセル。", font=F(15), fill=GR)
    d.text((28, 72), "左端が今のゲーム内のモデル (箱 + アイテムの鉾の合成)。三角形の数は本体 / 鉾。中ボスの上限は本体 約 1,500・鉾 約 250。", font=F(15), fill=GR)
    y = 104
    idle = pose_from_json(IDLE_JSON)
    PH = 470

    def row(title, ph, makers, ys=None):
        nonlocal y
        d.text((X0, y), title, font=F(20), fill=CY)
        y += 30
        for i, (label, im, col) in enumerate(makers):
            panel(canvas, xs[i], y, im, PW, ph)
            d.text((xs[i] + 2, y + ph + 6), label, font=F(17), fill=col)
        y += ph + 38

    def pose_row(title, pose, yaw, pitch=PITCH, ph=PH, cen=CEN, sc=SC, pike=True):
        mk = [("今 (箱 %d 個)" % cur.boxes, cur.render(pose, yaw, pitch, (PW, ph), sc), WH)]
        for k in "abc":
            r = opts[k]
            im = render_pose(r, r["big"], r["bglow"], r["yoff"], pose, yaw, pitch, (PW, ph), cen, sc, with_pike=pike)
            mk.append((names[k] + "  %d 三角形" % tri[k][0], im, CY))
        row(title, ph, mk)

    pose_row("前 3/4 (idle の構え)", idle, YAW_FRONT)
    pose_row("後ろ 3/4 (idle の構え)", idle, YAW_BACK)
    # 頭のアップ
    for title, yaw_c, size_h, scale_h, mylabel in (("頭のアップ (前 3/4)", 205, 340, 12.0, "頭 (前)"), ("頭 (後ろ 3/4)", 40, 300, 11.0, "頭 (後ろ)")):
        mk = [("今の頭", cur.render_head(idle, yaw_c, 8, (PW, size_h), scale_h), WH)]
        for k in "abc":
            r = opts[k]
            im = render_pose(r, r["big"], r["bglow"], r["yoff"], idle, yaw_c, 8, (PW, size_h), (0, 42.0, -1), scale_h, with_pike=False)
            mk.append((names[k] + " " + mylabel, im, CY))
        row(title, size_h, mk)
    # 鉾
    ph = 270
    mk = [("今の鉾 (アイテムの 3D × 1.8)", cur.render_pike(250, 8, (PW, ph), 8.4), WH)]
    for k in "abc":
        r = opts[k]
        im = render_pose(r, r["big"], r["bglow"], r["yoff"], {}, 250, 8, (PW, ph), (0, -2.0, -7.0), 8.4, with_pike=True) \
            if False else render_pike_only(r, 250, 8, (PW, ph), 8.4)
        mk.append((names[k] + " の鉾 %d 三角形" % tri[k][1], im, CY))
    row("番兵長の鉾 (別メッシュ。受け口 socket_main の原点)", ph, mk)
    sweep = pose_from_json({**IDLE_JSON, **anim_pose("sweep", 0.50)})
    slam = pose_from_json({**IDLE_JSON, **anim_pose("slam", 0.18)})
    pose_row("薙ぎ払い (sweep 0.50 s: 胴をひねって両手で鉾を振る)", sweep, 150, ph=PH)
    pose_row("叩きつけ (slam 0.18 s: 頭上から打ち下ろす)", slam, 150, ph=PH)

    # アトラスと数字
    d.text((X0, y), "テクスチャ (本体のアトラス 256×256 を 2 倍表示。左下は鉾の 128×128 を等倍)", font=F(20), fill=CY)
    y += 30
    ax = [X0, X0 + 512 + GAP, X0 + 2 * (512 + GAP)]
    for i, k in enumerate("abc"):
        r = opts[k]
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        s = r["size"] * 2
        canvas.paste(bgc.convert("RGB").resize((s, s), Image.NEAREST), (ax[i], y))
        panel_frame(d, ax[i], y, s, s)
        pa = Image.fromarray(r["patlas"], "RGBA")
        bgp = Image.new("RGBA", pa.size, (40, 40, 52, 255))
        bgp.alpha_composite(pa)
        canvas.paste(bgp.convert("RGB"), (ax[i] + 4, y + s - pa.size[1] - 4))
        d.text((ax[i], y + s + 6), names[k] + "  アトラス %d×%d" % (r["size"], r["size"]) + (" / 鉾 %d×%d" % (r["psize"], r["psize"])), font=F(16), fill=CY)
    y += 512 + 36
    per = lambda r, k: r["data"]["bones"][k]["triangle_count"]
    for k in "abc":
        r = opts[k]
        sp = sum(per(r, "spine%d" % i) for i in range(8))
        lines = [
            (names[k] + ":  本体 %d 三角形 (頭 %d / 胴 %d / 背 %d / 腕+前腕+手 %d×2 / 脚 %d×2) + 鉾 %d 三角形"
             % (tri[k][0], per(r, "head"), per(r, "body"), sp,
                per(r, "armL") + per(r, "forearmL") + per(r, "handL"), per(r, "legL"), tri[k][1]), CY, 17),
        ]
        for t, col, sz in lines:
            d.text((X0, y), t, font=F(sz), fill=col)
            y += sz + 8
    y += 6
    d.text((X0, y), "パレット (案ごとに少し変える): 骨の象牙 6 階調 / 石 5 / 苔 4 / 革・鉄 4 / 柄の木 5 / 核と瞳の水色 3 + 暗がり 3。縁の欠け・凹みの暗がり・板の縁取りと鋲・面に沿ったひびで手描き風。",
           font=F(15), fill=GR)
    y += 26
    canvas = canvas.crop((0, 0, W, y + 14))
    out = OUT / "whalebone_captain.png"
    canvas.save(out)
    print("saved", out, canvas.size)
    return opts, tri


def panel_frame(d, x, y, w, h):
    d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))


def render_pike_only(r, yaw, pitch, size, scale):
    faces = pose_faces(r["pike"], {}, gear_bone="socket_main", gear_off=SOCK_R)
    for f in faces:
        f.uv = f.uv + np.array([0, r["yoff"]])
    im = render(faces, r["big"], r["bglow"], yaw, pitch, size, SOCK_R + np.array([0, -2.0, -7.0]), scale)
    return im


if __name__ == "__main__":
    main()
