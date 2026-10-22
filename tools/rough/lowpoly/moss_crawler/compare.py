"""苔這い: 今のモデルと低ポリ 3 案を並べた比較画像を作る。

  python tools/rough/lowpoly/moss_crawler/compare.py
出力: docs/review/lowpoly_roughs/lp_moss_crawler.png と lp_moss_crawler_manifest.json
      (メッシュ・アトラスは lp.py と同じ docs/review/lowpoly_roughs/lp_moss_crawler/{a,b,c}/)
"""
from __future__ import annotations

import copy
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lp import *  # noqa
import voxel  # noqa
from PIL import ImageDraw  # noqa
import lpkit  # noqa

A = MD.ROOT / "src/main/resources/assets/skyabyss/"
LOW = MD.ROOT / "docs/review/lowpoly_roughs"
SUBJECT = "lp_moss_crawler"
ANIM = json.loads((A / "animations/entity/moss_crawler.animation.json").read_text(encoding="utf-8"))["animations"]
NAMES = {"a": "A 忠実", "b": "B 骨・棘", "c": "C 肉・苔"}
COLS = {"a": (150, 232, 255), "b": (255, 214, 150), "c": (190, 240, 160)}


# ---------------------------------------------------------------------- アニメの評価
def _env(t):
    sd = lambda x: math.sin(math.radians(x))
    cd = lambda x: math.cos(math.radians(x))
    return {"sin_d": sd, "cos_d": cd, "t": t}


def _ev(x, t):
    if isinstance(x, (int, float)):
        return float(x)
    e = str(x).replace("math.sin(", "sin_d(").replace("math.cos(", "cos_d(").replace("query.anim_time", "t")
    return float(eval(e, {"__builtins__": {}}, _env(t)))


def channel(ch, t):
    if isinstance(ch, list):
        return [_ev(x, t) for x in ch]
    ks = sorted(((float(k), v["vector"] if isinstance(v, dict) else v) for k, v in ch.items()))
    if t <= ks[0][0]:
        return [_ev(x, t) for x in ks[0][1]]
    for (t0, v0), (t1, v1) in zip(ks[:-1], ks[1:]):
        if t0 <= t <= t1:
            k = (t - t0) / max(t1 - t0, 1e-9)
            return [_ev(a, t) * (1 - k) + _ev(b, t) * k for a, b in zip(v0, v1)]
    return [_ev(x, t) for x in ks[-1][1]]


def anim_state(name, t):
    """{骨: (回転 (ファイルの値), 位置, 拡大)}。"""
    out = {}
    for b, chs in ANIM["animation.moss_crawler." + name]["bones"].items():
        out[b] = (channel(chs["rotation"], t) if "rotation" in chs else [0, 0, 0],
                  channel(chs["position"], t) if "position" in chs else [0, 0, 0],
                  channel(chs["scale"], t) if "scale" in chs else [1, 1, 1])
    return out


def intent_rot(r):
    """ファイルの回転 -> ラフの向き (x, z の符号を反転する。docs/design/15 と tools/install/install_moss_crawler_b.py)"""
    return (-r[0], r[1], -r[2])


def pose_faces(faces, state, positions=True):
    """休めの姿勢のメッシュの面を、state のポーズへ動かす。最後に root の yaw 180 (前を -Z にする)。"""
    xf = {}

    def get(b):
        if b in xf:
            return xf[b]
        parent, pv = BONES[b]
        pv = np.array(pv)
        if parent is None:
            R = lpkit.rot_y(180)
            xf[b] = (R, np.zeros(3), R)
            return xf[b]
        Ap, tp, Rp = get(parent)
        rot, pos, sc = state.get(b, ([0, 0, 0], [0, 0, 0], [1, 1, 1]))
        rx, ry, rz = intent_rot(rot)
        R = voxel._rot(rx, ry, rz)
        S = np.diag(sc)
        pos = np.array(pos) if positions else np.zeros(3)
        A = Ap @ R @ S
        t = Ap @ (pv - R @ S @ pv + pos) + tp
        xf[b] = (A, t, Rp @ R)
        return xf[b]

    out = []
    for f in faces:
        A_, t_, R_ = get(f.bone)
        g = copy.copy(f)
        g.verts = f.verts @ A_.T + t_
        g.normal = R_ @ f.normal
        out.append(g)
    return out


# ---------------------------------------------------------------------- 描画の補助
def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_faces(pose=None):
    return voxel.load_geo(MD.GEO, A / "textures/entity/moss_crawler.png", A / "textures/entity/moss_crawler_glowmask.png",
                          pose=pose)


def old_render(faces, yaw, pitch, size, scale, center=None):
    """voxel の描画は全体が収まるよう縮尺を決めるので、新案と同じ縮尺になるよう margin を逆算する。"""
    V = lpkit.rot_x(pitch) @ lpkit.rot_y(yaw)
    pts = np.array([V @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    m = (1 - scale * span / min(size)) / 2
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=m)


def new_render(r, faces, yaw, pitch, size, center, scale):
    return render(faces, r["atlas"], r["glow"], yaw, pitch, size, center, scale)


def paste(canvas, im, xy, new=True):
    bg = panel_bg(*im.size) if new else None
    if new:
        bg.paste(im, (0, 0), im)
        canvas.paste(bg, xy)
    else:
        canvas.paste(im, xy)


def build_all():
    res = {}
    for s in "abc":
        r = build_option(s)
        save_option(r)
        res[s] = r
    return res


def main():
    res = build_all()
    F = voxel.font
    GR = (160, 160, 176)
    W = 1600
    PW = 392
    gap = 10
    x0 = 12
    xs = [x0 + i * (PW + gap) for i in range(4)]
    rows = [("前 3/4", 215, 20, 330, (0, 6.0, 0.0), 13.0, "full"),
            ("後ろ 3/4", 35, 24, 330, (0, 6.0, 0.0), 13.0, "full"),
            ("上から", 200, 78, 250, (0, 4.0, 0.0), 12.0, "full"),
            ("顔のアップ (前)", 200, 10, 330, (0, 5.5, -12.0), 33.0, "head"),
            ("横", 270, 6, 250, (0, 5.0, 0.0), 11.0, "full")]
    # 行の高さを足して全体の高さを決める
    top = 150
    heights = [r[3] for r in rows]
    pose_h = 360
    tot = top + sum(h + 34 for h in heights) + 2 * (pose_h + 34) + 700
    canvas = Image.new("RGB", (W, tot), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((16, 12), "苔這い (moss_crawler) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (A 忠実 / B 骨・棘 / C 肉・苔)", font=F(25), fill=(236, 232, 218))
    d.text((16, 46), "キャラは選んだ「苔芋虫」(5 節・骨の輪・背の苔・オレンジ虹彩の一つ目・誘いの光の触角)。骨 27 本の名前・階層・pivot は今の geo と同じ。", font=F(15), fill=GR)
    d.text((16, 70), "1 ブロック = 32 テクセル・最近傍・限られたパレット。今のアニメがそのまま動く。", font=F(15), fill=GR)
    ntri = {s: res[s]["data"]["triangle_count"] for s in "abc"}
    n_old = sum(len(b.get("cubes", [])) for b in json.loads(MD.GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"])
    heads = {"old": (f"今 (箱 {n_old} 個)", (236, 232, 218))}
    for s in "abc":
        heads[s] = (f"{NAMES[s]}  {ntri[s]} 三角形", COLS[s])
    for i, k in enumerate(("old", "a", "b", "c")):
        d.text((xs[i], top - 52), heads[k][0], font=F(22), fill=heads[k][1])
    brief = {
        "old": "今: 箱を重ねた滑らか版。球のような丸みを箱で作っている",
        "a": "忠実: 丸い八角の節に骨の輪、丸い苔のクッション、丸い一つ目",
        "b": "骨寄り: 背に骨のとげ、頭に角と牙、細い脚。苔は少なめ",
        "c": "肉・苔寄り: ぽってりした肉に厚い苔、短い脚、大きな目",
    }
    for i, k in enumerate(("old", "a", "b", "c")):
        d.text((xs[i], top - 22), brief[k], font=F(13), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    olds = old_faces()
    old_head = [f for f in olds if (f.origin + (f.eu + f.ev) / 2)[2] <= -6.5]
    posed = {s: pose_faces(res[s]["faces"], {}) for s in "abc"}
    y = top
    for label, yaw, pitch, h, cen, sc, sel in rows:
        for i, k in enumerate(("old", "a", "b", "c")):
            if k == "old":
                im = old_render(olds if sel == "full" else old_head, yaw, pitch, (PW, h), sc)
                canvas.paste(im, (xs[i], y))
            else:
                r = res[k]
                fcs = posed[k]
                if sel == "head":
                    fcs = [f for f in fcs if f.verts.mean(0)[2] <= -5.0]
                im = new_render(r, fcs, yaw, pitch, (PW, h), cen, sc)
                paste(canvas, im, (xs[i], y))
            frame(xs[i], y, PW, h)
        d.text((x0, y + h + 6), label, font=F(16), fill=(236, 232, 218))
        y += h + 34
    # ポーズ: 飛びかかり (pounce) の溜め 0.18 s と、体を伸ばして飛び込む 0.30 s
    for label, t, yaw, pitch in (("飛びかかり (pounce) 0.18 s: 前の節を起こし、頭を下げ、顎を開く", 0.18, 235, 14),
                                 ("飛びかかり (pounce) 0.30 s: 体を伸ばして飛び込む (回転のみ。前進の位置移動は省いて今の絵と揃えた)", 0.30, 235, 14)):
        st = anim_state("pounce", t)
        stp = {b: (v[0], [0, 0, 0], v[2]) for b, v in st.items()}
        for i, k in enumerate(("old", "a", "b", "c")):
            if k == "old":
                pose = {b: intent_rot(v[0]) for b, v in st.items()}
                im = old_render(old_faces(pose), yaw, pitch, (PW, pose_h), 13.0)
                canvas.paste(im, (xs[i], y))
            else:
                fcs = pose_faces(res[k]["faces"], stp)
                im = new_render(res[k], fcs, yaw, pitch, (PW, pose_h), (0, 6.5, 0), 13.0)
                paste(canvas, im, (xs[i], y))
            frame(xs[i], y, PW, pose_h)
        d.text((x0, y + pose_h + 6), label, font=F(16), fill=(236, 232, 218))
        y += pose_h + 34
    # アトラスと数字
    for i, k in enumerate(("a", "b", "c")):
        r = res[k]
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        asz = 3 * r["size"]
        xx = xs[i + 1]
        rowsused = int(np.nonzero(r["atlas"][..., 3].any(1))[0].max()) + 2
        canvas.paste(bgc.convert("RGB").crop((0, 0, r["size"], rowsused)).resize((asz, 3 * rowsused), Image.NEAREST), (xx, y))
        frame(xx, y, asz, 3 * rowsused)
        asz = 3 * rowsused
        per = r["data"]["bones"]
        pt = lambda ks: sum(per[k_]["triangle_count"] for k_ in ks)
        segs = pt([f"seg{j}" for j in range(1, 6)])
        head = pt(["head", "eyes", "jaw", "teeth"])
        ants = pt(["ant_l", "ant_r", "lure_l", "lure_r"])
        legs = pt([f"{a}{n}_{b}" for a in "lr" for n in "123" for b in "ul"])
        d.text((xx, y + asz + 4), f"{NAMES[k]} のアトラス {r['size']}×{r['size']} (3 倍表示)", font=F(15), fill=COLS[k])
        lines = [f"節 {segs} / 頭・目・顎・歯 {head}", f"触角・誘いの光 {ants} / 脚 {legs} / 尾 {pt(['tail'])}"]
        for j, t in enumerate(lines):
            d.text((xx, y + asz + 26 + j * 19), t, font=F(14), fill=GR)
    ty = y + 3 * 80 + 72
    d.text((16, ty), "三角形の数 (上限の目安 約 600) と骨・パレット", font=F(16), fill=(236, 232, 218))
    for j, k in enumerate(("a", "b", "c")):
        d.text((16, ty + 26 + j * 22), f"{NAMES[k]}: {ntri[k]} 三角形 / 骨 27 本 / 光る画素 {int(res[k]['glow'].sum())}", font=F(15), fill=COLS[k])
    d.text((16, ty + 98), "パレット: 肉 5 階調 / 骨 6 / 苔 5 / 虹彩 5 / 白目 4 / 誘いの光 4 / 闇 3。光る画素 (glowmask) は虹彩と誘いの光 (瞳とハイライトは光らない)", font=F(13), fill=GR)
    d.text((16, ty + 120), "骨: root > seg1 > seg2 > seg3 > seg4 > seg5 > head > (eyes / jaw > teeth / ant_l > lure_l / ant_r > lure_r)。seg1 > tail。seg2 > l1_u > l1_l と r1_u > r1_l、seg3 > l2 と r2、seg5 > l3 と r3", font=F(13), fill=GR)
    canvas = canvas.crop((0, 0, W, min(tot, y + 3 * 80 + 72 + 150)))
    out = LOW / f"{SUBJECT}.png"
    canvas.save(out)
    print("saved", out, canvas.size, ntri)
    return res


if __name__ == "__main__":
    main()
