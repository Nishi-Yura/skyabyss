"""燠虫: 今のモデルと低ポリ 3 案を並べた比較画像とマニフェストを作る。

  python tools/rough/lowpoly/cinder_roller/compare.py
出力: docs/review/lowpoly_roughs/lp_cinder_roller.png と lp_cinder_roller_manifest.json
      (メッシュ・アトラスは lp.py と同じ docs/review/lowpoly_roughs/lp_cinder_roller/{a,b,c}/)
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from lp import *  # noqa
import posing as PO  # noqa
import voxel  # noqa
from PIL import ImageDraw  # noqa

A = MD.ROOT / "src/main/resources/assets/skyabyss/"
LOW = MD.ROOT / "docs/review/lowpoly_roughs"
SUBJECT = "lp_cinder_roller"
NAMES = {"a": "A 玄武の層鎧", "b": "B 熔皮の棘玉", "c": "C 灰炭の団子"}
COLS = {"a": (150, 200, 255), "b": (255, 190, 130), "c": (214, 210, 196)}
HIDE = {"antenna_l", "antenna_r", "uropods"}


def hide_limbs(fs):
    return [f for f in fs if not (f.bone in HIDE or f.bone.startswith("leg") or f.bone.startswith("mem"))]


def old_geo_posed(state):
    """今の geo (箱) にアニメの位置を焼いて (pivot と箱を移動)、回転は voxel のポーズで掛ける。"""
    g = json.loads(MD.GEO.read_text(encoding="utf-8"))
    bones = {b["name"]: b for b in g["minecraft:geometry"][0]["bones"]}

    def shift(name, d):
        b = bones[name]
        b["pivot"] = [b["pivot"][i] + d[i] for i in range(3)]
        for c in b.get("cubes", []):
            c["origin"] = [c["origin"][i] + d[i] for i in range(3)]
            if "pivot" in c:
                c["pivot"] = [c["pivot"][i] + d[i] for i in range(3)]
        for n, bb in bones.items():
            if bb.get("parent") == name:
                shift(n, d)

    for b, (rot, pos) in state.items():
        if any(abs(x) > 1e-9 for x in pos):
            shift(b, [-pos[0], pos[1], pos[2]])
    pose = {b: PO.intent_rot(v[0]) for b, v in state.items()}
    return voxel.load_geo(g, A / "textures/entity/cinder_roller.png", A / "textures/entity/cinder_roller_glowmask.png", pose=pose)


def old_render(faces, yaw, pitch, size, scale):
    V = lpkit.rot_x(pitch) @ lpkit.rot_y(yaw)
    pts = np.array([V @ (f.origin + a * f.eu + b * f.ev) for f in faces for a in (0, 1) for b in (0, 1)])
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1])
    m = (1 - scale * span / min(size)) / 2
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=m)


def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def paste(canvas, im, xy):
    bg = panel_bg(*im.size)
    bg.paste(im, (0, 0), im)
    canvas.paste(bg, xy)


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
    WH = (236, 232, 218)
    W = 1600
    PW = 392
    gap = 10
    x0 = 12
    xs = [x0 + i * (PW + gap) for i in range(4)]
    ntri = {s: res[s]["data"]["triangle_count"] for s in "abc"}
    n_old = sum(len(b.get("cubes", [])) for b in json.loads(MD.GEO.read_text(encoding="utf-8"))["minecraft:geometry"][0]["bones"])
    # (ラベル, 状態, yaw, pitch, 高さ, 中心, 縮尺, 抜き出し, 脚などを隠す)
    st_rest = {}
    st_curl = PO.anim_state("curled", 0.0)
    st_roll = PO.anim_state("rolling", 0.05)
    st_unc = PO.anim_state("uncurl", 0.17)
    st_diz = PO.anim_state("dizzy", 0.2)
    rows = [
        ("休め 前 3/4 (歩く姿)", st_rest, 215, 22, 300, (0, 4.0, 0), 14.5, None, False),
        ("休め 後ろ 3/4", st_rest, 35, 24, 300, (0, 4.0, 0), 14.5, None, False),
        ("顔のアップ (前)", st_rest, 200, 14, 300, (0, 3.5, -12.0), 36.0, "head", False),
        ("丸まった玉 (curled) 前 3/4 — 脚・触角・尾・膜の骨は隠す想定", st_curl, 215, 20, 340, (0, 6.0, 0), 22.0, None, True),
        ("転がり (rolling) 0.05 s — 玉が x 軸まわりに回る。継ぎ目が横の帯になって流れる", st_roll, 245, 12, 340, (0, 6.0, 0), 22.0, None, True),
        ("丸まりを解く (uncurl) 0.17 s — 節が 40 度ずつ開いて甲が重なっていく", st_unc, 225, 20, 330, (0, 5.0, 0), 17.0, None, False),
        ("目を回した (dizzy) — 背を下にして光る腹と脚があらわになる", st_diz, 215, 30, 320, (0, 4.0, 0), 14.5, None, False),
    ]
    top = 150
    tot = top + sum(r[4] + 34 for r in rows) + 460
    canvas = Image.new("RGB", (W, tot), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((16, 12), "燠虫 (cinder_roller) — 低ポリ非立方体 + ドット絵テクスチャ 3 案 (A 玄武の層鎧 / B 熔皮の棘玉 / C 灰炭の団子)", font=F(25), fill=WH)
    d.text((16, 46), "団子虫のまま、熾の巨人の言葉 (灰・熾・玄武の板・冷える溶岩・熱の割れ目) で描き直した。骨 37 本の名前・階層・pivot は今の geo と同じで、今のアニメ (歩く・丸まる・転がる・開く・目を回す) がそのまま動く。", font=F(14), fill=GR)
    d.text((16, 68), "1 ブロック = 32 テクセル・最近傍・限られたパレット。甲は x 軸まわりの回転体の板で作り、玉では 40 度ごとに隙間も交差もなく一周する (殻の面どうしの交差 0 を検査)。", font=F(14), fill=GR)
    heads = {"old": (f"今 (箱 {n_old} 個)", WH)}
    for s in "abc":
        heads[s] = (f"{NAMES[s]}  {ntri[s]} 三角形", COLS[s])
    for i, k in enumerate(("old", "a", "b", "c")):
        d.text((xs[i], top - 52), heads[k][0], font=F(22), fill=heads[k][1])
    brief = {
        "old": "今: 箱を重ねた黒い板と溶岩の継ぎ目の団子虫",
        "a": "広く平たい玄武の板。柱の継ぎ目の熱い割れ目",
        "b": "丸い熔皮の殻に背の棘。割れ目から赤く透ける",
        "c": "丸い炭のかたまり。ワニ皮の割れと灰の積もり",
    }
    for i, k in enumerate(("old", "a", "b", "c")):
        d.text((xs[i], top - 22), brief[k], font=F(13), fill=GR)

    def frame(x, y, w, h):
        d.rectangle([x - 1, y - 1, x + w, y + h], outline=(60, 60, 78))

    y = top
    for label, st, yaw, pitch, h, cen, sc, sel, hide in rows:
        for i, k in enumerate(("old", "a", "b", "c")):
            if k == "old":
                of = old_geo_posed(st)
                if hide:
                    # 玉では脚・触角・尾・膜を隠す想定 (箱版は骨ごとに面を持たないので、玉の外へはみ出すものだけ省く)
                    pass
                if sel == "head":
                    of = [f for f in of if (f.origin + (f.eu + f.ev) / 2)[2] <= -8.8]
                im = old_render(of, yaw, pitch, (PW, h), sc)
                canvas.paste(im, (xs[i], y))
            else:
                r = res[k]
                pf = PO.pose_faces(r["faces"], st)
                if hide:
                    pf = hide_limbs(pf)
                if sel == "head":
                    pf = [f for f in pf if f.verts.mean(0)[2] <= -8.8]
                im = render(pf, r["atlas"], r["glow"], yaw, pitch, (PW, h), cen, sc)
                paste(canvas, im, (xs[i], y))
            frame(xs[i], y, PW, h)
        d.text((x0, y + h + 6), label, font=F(16), fill=WH)
        y += h + 34
    # アトラスと数字
    for i, k in enumerate(("a", "b", "c")):
        r = res[k]
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        rowsused = int(np.nonzero(r["atlas"][..., 3].any(1))[0].max()) + 2
        zoom = 3
        xx = xs[i + 1]
        canvas.paste(bgc.convert("RGB").crop((0, 0, r["size"], rowsused)).resize((zoom * r["size"] if zoom * r["size"] <= PW else PW, zoom * rowsused if zoom * r["size"] <= PW else int(rowsused * PW / r["size"])), Image.NEAREST), (xx, y))
        ah = zoom * rowsused if zoom * r["size"] <= PW else int(rowsused * PW / r["size"])
        frame(xx, y, PW if zoom * r["size"] > PW else zoom * r["size"], ah)
        per = r["data"]["bones"]
        pt = lambda ks: sum(per[k_]["triangle_count"] for k_ in ks)
        body = pt([f"seg{j}" for j in range(9)])
        legs = pt([f"leg{j}_{s}" for j in range(1, 8) for s in "lr"])
        d.text((xx, y + ah + 4), f"{NAMES[k]} のアトラス {r['size']}×{r['size']}", font=F(15), fill=COLS[k])
        lines = [f"甲・頭 {body} / 脚 {legs}", f"触角 {pt(['antenna_l', 'antenna_r'])} / 尾 {pt(['uropods'])} / 膜 {pt([f'mem{j}' for j in range(8)])}",
                 f"光る画素 {int(r['glow'].sum())}"]
        for j, t in enumerate(lines):
            d.text((xx, y + ah + 26 + j * 19), t, font=F(14), fill=GR)
    y2 = y + 250
    d.text((16, y2), "三角形の数 (上限の目安 約 600) と骨・パレット", font=F(16), fill=WH)
    for j, k in enumerate(("a", "b", "c")):
        d.text((16, y2 + 26 + j * 22), f"{NAMES[k]}: {ntri[k]} 三角形 / 骨 37 本 / 光る画素 {int(res[k]['glow'].sum())}", font=F(15), fill=COLS[k])
    d.text((16, y2 + 98), "パレット: A 玄武の石 5 + 溶岩 5 / B 熔皮 5 + 溶岩 5 / C 炭 5 + 灰 4 + 溶岩 5 + 目 3。光る画素 (glowmask) = 熱い割れ目・溶岩の継ぎ目・腹・目・触角の先 (ふだんの板は光らない)", font=F(13), fill=GR)
    d.text((16, y2 + 120), "骨: root > roll > body > seg0..seg8 (各節の下に mem0..mem7 の膜、seg0 の下に uropods、seg1..7 の下に leg*_r / leg*_l、seg8 の下に antenna_r / antenna_l)。目・頭は seg8、尾の板は seg0 のメッシュ", font=F(13), fill=GR)
    d.text((16, y2 + 142), "玉の作り: 各節の甲 = 外の層 (φ -23〜+1 度) + 段 + 次の節の下へ上がる床 (溶岩の継ぎ目)。休めでは隣の甲が床の上にかぶさり、玉では 40 度ごとに一周する。", font=F(13), fill=GR)
    d.text((16, y2 + 164), "玉では脚・触角・尾・膜の骨を隠す (触角と尾は長く玉の外へ出るため)。殻の面どうしの交差は 休め・玉とも 0。", font=F(13), fill=GR)
    canvas = canvas.crop((0, 0, W, min(tot, y2 + 200)))
    out = LOW / f"{SUBJECT}.png"
    canvas.save(out)
    print("saved", out, canvas.size, ntri)
    return res


if __name__ == "__main__":
    main()
