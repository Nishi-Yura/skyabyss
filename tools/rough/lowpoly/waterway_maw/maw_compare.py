"""水路の顎: 今のモデルと低ポリ 3 案を並べた比較画像と manifest を作る。

実行: python tools/rough/lowpoly/waterway_maw/maw_compare.py
出力: docs/review/lowpoly_roughs/waterway_maw.png, waterway_maw_manifest.json (+ waterway_maw/{a,b,c}/ のメッシュ・アトラス)
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from maw_lp import *  # noqa
from lpkit import nrm, render, rot_x, rot_y, rot_z  # noqa
import voxel  # noqa

A_ = ROOT / "src/main/resources/assets/skyabyss/"
RO = ROOT / "docs/review/lowpoly_roughs"
F = voxel.font
CY, GR, WH = (150, 232, 255), (160, 160, 176), (236, 232, 218)

YAW_FRONT, YAW_BACK, PITCH = 212, 32, 12
BITE = {"jaw": (-32, 0, 0)}   # animation.waterway_maw.bite の 0.08〜0.18 秒 (顎 -32 度)。geo の回転は Bedrock の向き
BITE_POS = {"head": (0, 0, -5)}


def rot_geo(rx, ry, rz):
    """Bedrock / Blockbench の骨の回転 (度): Z → Y → X の順。voxel.load_geo と同じ向き。"""
    return rot_z(rz) @ rot_y(ry) @ rot_x(rx)


def pose_point(p, bone, pose, pos=None):
    pos = pos or {}
    while bone is not None:
        parent, pivot = BONES[bone]
        pv = np.array(pivot, float)
        rx, ry, rz = pose.get(bone, (0, 0, 0))
        p = rot_geo(rx, ry, rz) @ (p - pv) + pv + np.array(pos.get(bone, (0, 0, 0)), float)
        bone = parent
    return p


def pose_faces(faces, pose, pos=None):
    out = []
    for f in faces:
        g = copy.copy(f)
        g.verts = np.array([pose_point(p, f.bone, pose, pos) for p in f.verts])
        k = len(g.verts)
        n = np.cross(g.verts[2] - g.verts[0], g.verts[3 % k] - g.verts[1]) if k == 4 else \
            np.cross(g.verts[1] - g.verts[0], g.verts[2] - g.verts[0])
        n = nrm(n)
        if np.dot(n, f.normal) < 0:
            n = -n
        g.normal = n
        out.append(g)
    return out


def panel_bg(w, h):
    return Image.fromarray(np.clip(voxel._background(w, h, "dark"), 0, 255).astype(np.uint8))


def old_faces(pose=None):
    return voxel.load_geo(A_ / "geo/entity/waterway_maw.geo.json", A_ / "textures/entity/waterway_maw.png",
                          A_ / "textures/entity/waterway_maw_glowmask.png", pose=pose)


def old_view(faces, yaw, pitch, size, scale, center):
    """voxel.render は全体に合わせて縮尺を決めるので、同じ縮尺・中心になるよう、枠の外に目印の点を置いて合わせる。"""
    return voxel.render(faces, yaw=yaw, pitch=pitch, size=size, margin=0.0, outline_jump=0.6)


def fit(faces_xyz, yaw, pitch, size, margin):
    """voxel.render と同じ縮尺の決め方 (正射影の最大の広がり)。"""
    V = rot_x(pitch) @ rot_y(yaw)
    pts = (V @ faces_xyz.T).T
    lo, hi = pts.min(0), pts.max(0)
    span = max(hi[0] - lo[0], hi[1] - lo[1], 1e-6)
    return min(size) * (1 - 2 * margin) / span, (lo + hi) / 2


def old_pts(faces):
    pts = []
    for f in faces:
        for a in (0, 1):
            for b in (0, 1):
                pts.append(f.origin + a * f.eu + b * f.ev)
    return np.array(pts)


def view_pair(old, new, yaw, pitch, size, margin=0.07, sel=None):
    """今 (voxel) と 3 案 (lpkit) を同じ縮尺・同じ中心で描く。"""
    pts = old_pts(old if sel is None else [f for f in old if sel(f)])
    sc, cen_v = fit(pts, yaw, pitch, size, margin)
    V = rot_x(pitch) @ rot_y(yaw)
    cen = np.linalg.inv(V) @ cen_v    # 画面の中心に置くモデル座標
    return sc, cen


def old_render(faces, yaw, pitch, size, margin=0.07, sel=None):
    sub = faces if sel is None else [f for f in faces if sel(f)]
    return voxel.render(sub, yaw=yaw, pitch=pitch, size=size, margin=margin)


def new_render(r, yaw, pitch, size, sc, cen, pose=None, pos=None):
    faces = r["faces"] if pose is None else pose_faces(r["faces"], pose, pos)
    return render(faces, r["atlas"], r["glow"], yaw, pitch, size, cen, sc)


def main():
    res = build_all()
    old = old_faces()
    old_bite = old_faces(BITE)
    n_old = sum(len(b.get("cubes", [])) for b in json.loads((A_ / "geo/entity/waterway_maw.geo.json").read_text(
        encoding="utf-8"))["minecraft:geometry"][0]["bones"])
    PW, PH = 378, 290
    top = 96
    gap = 12
    canvas = Image.new("RGB", (1600, 2300), (16, 16, 22))
    d = ImageDraw.Draw(canvas)
    d.text((28, 14), "水路の顎 (waterway_maw) — 低ポリ非立方体 + ドット絵テクスチャ 3 案", font=F(26), fill=WH)
    d.text((28, 50), "骨の名前・階層・pivot は今の geo と同じ (seg0〜seg5・tail・head・jaw・lure。今のアニメーションがそのまま動く)。"
                     "歯は上 10 対・下 7 対のまま、先細りの棘。テクスチャは 1 ブロック = 32 テクセル・最近傍・限られた色。",
           font=F(15), fill=GR)
    d.text((28, 72), "A 川カマス型 (細長く鋭い楔)   B 提灯鮟鱇型 (深く丸い頭と突き出る下顎)   C 古代甲冑魚型 (角ばった骨板と刃の歯)",
           font=F(15), fill=CY)

    names = {"a": "A 川カマス型", "b": "B 提灯鮟鱇型", "c": "C 古代甲冑魚型"}
    heads = lambda f: f.origin[2] < -4
    rows = [
        ("前 3/4", YAW_FRONT, PITCH, None, (PW, PH), 0.05),
        ("後ろ 3/4", YAW_BACK, PITCH, None, (PW, PH), 0.05),
        ("顔のアップ (口を閉じた前 3/4)", 204, 7, heads, (PW, 330), 0.03),
        ("噛みつき (顎 -32°・頭 -5。bite の 0.08〜0.18 秒)", 238, 6, heads, (PW, 330), 0.03),
    ]
    y = top
    for ri, (label, yaw, pitch, sel, size, margin) in enumerate(rows):
        pw, ph = size
        d.text((28, y - 2), label, font=F(17), fill=CY)
        y += 22
        pose = ri == 3
        oldf = old_bite if pose else old
        # 縮尺は 3 案の範囲も含めて決める (今の範囲と 3 案の範囲の大きい方)
        pts = [old_pts(oldf if sel is None else [f for f in oldf if sel(f)])]
        for k in "abc":
            fs = res[k]["faces"] if not pose else pose_faces(res[k]["faces"], BITE, BITE_POS)
            allv = np.concatenate([f.verts for f in fs if (sel is None or f.bone in ("head", "jaw", "lure"))])
            pts.append(allv)
        # 頭のアップは頭の骨 (head / jaw / lure) の点で範囲を決める
        V = rot_x(pitch) @ rot_y(yaw)
        sc = None
        lo_all, hi_all = None, None
        for p in pts:
            q = (V @ p.T).T
            lo, hi = q.min(0), q.max(0)
            lo_all = lo if lo_all is None else np.minimum(lo_all, lo)
            hi_all = hi if hi_all is None else np.maximum(hi_all, hi)
        sc = min(pw / max(hi_all[0] - lo_all[0], 1e-6), ph / max(hi_all[1] - lo_all[1], 1e-6)) * (1 - 2 * margin)
        cen_v = (lo_all + hi_all) / 2
        cen = np.linalg.inv(V) @ cen_v
        for i, key in enumerate(("old", "a", "b", "c")):
            x = 20 + i * (pw + gap)
            bg = panel_bg(pw, ph)
            if key == "old":
                sub = oldf if sel is None else [f for f in oldf if sel(f)]
                # 同じ縮尺・中心になるよう、voxel の描画を自前の範囲で描く (margin から縮尺を逆算)
                ptsv = old_pts(sub)
                q = (V @ ptsv.T).T
                span_old = max(q[:, 0].max() - q[:, 0].min(), q[:, 1].max() - q[:, 1].min())
                m_old = 0.5 * (1 - sc * span_old / min(pw, ph))
                # voxel.render は正方形の短辺で縮尺を決める。今の範囲の中心が枠の中心になるので、全体の中心の差だけ平行移動する
                im = voxel.render(sub, yaw=yaw, pitch=pitch, size=(pw, ph), margin=max(m_old, 0.0), bg="flat", bloom=False)
                a_ = np.array(im).astype(int)
                m = (np.abs(a_ - 200).sum(-1) > 14)
                c_old = (q.min(0) + q.max(0)) / 2
                dx = int(round((c_old[0] - cen_v[0]) * sc))
                dy = int(round(-(c_old[1] - cen_v[1]) * sc))
                rgba = np.dstack([a_.clip(0, 255).astype(np.uint8), (m * 255).astype(np.uint8)])
                oi = Image.fromarray(rgba, "RGBA")
                bg.paste(oi, (dx, dy), oi)
            else:
                r = res[key]
                im = new_render(r, yaw, pitch, (pw, ph), sc, cen, BITE if pose else None, BITE_POS if pose else None)
                bg.paste(im, (0, 0), im)
            canvas.paste(bg, (x, y))
            d.rectangle([x - 1, y - 1, x + pw, y + ph], outline=(60, 60, 78))
            if ri == 0:
                lab = "今 (箱 %d 個)" % n_old if key == "old" else names[key]
                d.text((x + 8, y + 6), lab, font=F(18), fill=WH if key == "old" else CY)
        y += ph + 14
    # --- アトラスと数字
    top3 = y + 6
    x = 20
    asz = 256
    for key in "abc":
        r = res[key]
        at = Image.fromarray(r["atlas"], "RGBA")
        bgc = Image.new("RGBA", at.size, (40, 40, 52, 255))
        bgc.alpha_composite(at)
        s = 2 if r["size"] >= 256 else 4
        used = np.where(r['atlas'][..., 3].any(1))[0].max() + 2
        bgc = bgc.crop((0, 0, r['size'], used))
        im = bgc.convert("RGB").resize((r["size"] * s, used * s), Image.NEAREST)
        canvas.paste(im, (x, top3))
        d.rectangle([x - 1, top3 - 1, x + im.size[0], top3 + im.size[1]], outline=(60, 60, 78))
        d.text((x, top3 + im.size[1] + 4), f"{names[key]} のアトラス {r['size']}×{r['size']} ({s} 倍表示)", font=F(15), fill=CY)
        x += im.size[0] + 18
        hmax = max(locals().get('hmax', 0), im.size[1])
    end = top3 + hmax + 30
    # 数字 (右の空き)
    tx = 20
    ty = top3 + hmax + 34
    end = ty
    tris = {k: res[k]["data"]["triangle_count"] for k in "abc"}
    lines = [("三角形の数 (全部 1 枚のメッシュ・骨ごとに分割)", WH, 19)]
    for key in "abc":
        pb = {b: v["triangle_count"] for b, v in res[key]["data"]["bones"].items()}
        body = sum(pb[b] for b in ("seg0", "seg1", "seg2", "seg3", "seg4", "seg5"))
        lines.append((f"{names[key]}: 計 {tris[key]} 三角形  = 頭 {pb['head']} (上あご・上の歯 10 対) / 顎 {pb['jaw']} (下あご・下の歯 7 対) / "
                      f"ルアー {pb['lure']} / 胴 {body} / 尾 {pb['tail']}", CY, 16))
    lines += [("今の geo: 箱 %d 個 (顎の歯 = 上 10 対・下 7 対の 3 段の箱)。光る画素 108。" % n_old, GR, 15),
              ("パレット: 皮膚 6 階調 + 腹 4 + 骨板 6 + ひれ 5 + 口 4 + 歯 6 (または象牙) + 光 3。縁の欠け・凹みの暗がり・面ごとの照りで手描き風。", GR, 15),
              ("光るもの (glowmask): 目 (虹彩・瞳) とルアーの玉。C はさらに頭の刻印。", GR, 15),
              ("口の中は赤黒い肉 (天井に横しわ)、歯の根元は歯茎色、先ほど白い。濡れた艶は上向きの縁と面のまばらな 1 画素の照り。", GR, 15),
              ("噛みつきの姿勢は bite の 0.08〜0.18 秒 (顎 -32°) と頭の突き出し (-5)。今のアニメーションの値のまま。", GR, 15)]
    wrap = lambda s, n: [s[i:i + n] for i in range(0, len(s), n)]
    for t, col, sz in lines:
        for ln in wrap(t, 100 if sz >= 16 else 112):
            d.text((tx, ty), ln, font=F(sz), fill=col)
            ty += sz + 8
        ty += 8
    out = RO / "waterway_maw.png"
    canvas = canvas.crop((0, 0, 1600, max(end, ty + 14)))
    canvas.save(out)
    print("saved", out, tris)
    return res, tris


MANIFEST_EXTRA = ""


def write_manifest(res, tris):
    pb = lambda k: {b: v["triangle_count"] for b, v in res[k]["data"]["bones"].items()}
    ex = ("水路の顎を、全モデル共通の新しい作風 (非立方体の低ポリ + マイクラ調のドット絵 32 テクセル/ブロック) で作った 3 案。"
          "骨の名前・階層・pivot は今の geo のまま (root・seg0〜seg5・tail・head・jaw・lure) なので今のアニメーション (swim・bite) がそのまま動く。"
          "歯は上 10 対・下 7 対の先細りの棘で、噛みつきの姿勢 (顎 -32°・頭 -5) でも読める。"
          f"三角形: A {tris['a']} / B {tris['b']} / C {tris['c']} (今は箱 288 個・1,393 面)。"
          "光るのは目とルアー (C は頭の刻印も)。テクスチャは案ごとに atlas と glowmask の PNG。")
    man = {"id": "lp_waterway_maw", "short": "水路の顎 低ポリ", "kind": "低ポリ 3案", "title": "水路の顎 (waterway_maw) 低ポリ 3案",
           "sheets": ["lowpoly_roughs/waterway_maw.png"], "extra": ex,
           "options": {
               "a": {"name": f"A 川カマス型 ({tris['a']} 三角形)",
                     "desc": "細長く鋭い楔形の頭と、針のように細い歯 (前の二対は長い牙)。鱗のある青緑の濡れた肌、背に紫の刃を連ねたひれ、細い竿の先に光る玉。"
                             "素早く泳ぎ噛みつく水路の捕食者の印象。顎が長く、歯が一番はっきり読める。"},
               "b": {"name": f"B 提灯鮟鱇型 ({tris['b']} 三角形)",
                     "desc": "深く丸い頭と前へ突き出る大きな下あご。前の一対が大きな曲がった牙、後ろへ小さくなる。なめらかな深い青の肉の皮膚と赤黒い口、"
                             "枝分かれした提灯、うちわ形の尾。一番不気味で、闇の水路の怪物らしい。"},
               "c": {"name": f"C 古代甲冑魚型 ({tris['c']} 三角形)",
                     "desc": "角ばった象牙色の骨板を重ねた鎧の頭と、幅の広い刃 (のこぎり) の歯。石肌の胴に背の高い帆、頭に光る刻印、骨の角灯のルアー。"
                             "岩鯨の背の石・骨の世界とよくなじむ。"}}}
    (RO / "waterway_maw_manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    res, tris = main()
    write_manifest(res, tris)
