"""灰被りの変種 2 体 (灰被りの屑 ash_husk・古い灰被り elder_thrall) の低ポリ ラフ 3 案ずつ (docs/design/21 の 6.1、P20)。

作り方 (21 の 1.6「変種は元のモデルに部品を足して作る」): 背中の灰被り (ash_thrall の A「灰衣の火番」、tools/rough/lowpoly/ash_thrall/opt_a) を
そのまま読み、殻 (crust_*) を削る・足すだけ。骨・pivot・アニメは灰被りと同じ (ゲームでは ash_thrall の骨とアニメを使い、大きさは描画の倍率で変える:
屑 0.85、古い灰被り 1.15)。殻のある姿と燃え上がった姿の 2 枚のアトラスも灰被りと同じ。
  灰被りの屑 (未完成の灰被り。殻 15)   A 欠けた殻 / B 灰の繭 / C 練りかけの土偶
  古い灰被り (名づき。殻 40、背に煤の釜) A 煤の釜 / B 厚い灰の鎧 / C 炉の背負い子
古い灰被りの火掻きは、火掻き棒を伸ばして爪を足した別の道具 (elder_rake)。

実行: PYTHONIOENCODING=utf-8 python -I tools/rough/lowpoly/ch4/thrall_variants.py
出力: docs/review/ch4_roughs/{ash_husk,elder_thrall}/{a,b,c}/ (本体・燃え上がり・道具のメッシュとアトラス) と <id>.png・<id>_manifest.json
"""
from __future__ import annotations

import copy
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
TH = HERE.parent / "ash_thrall"
sys.path.insert(0, str(TH))
from shapes import *  # noqa
import core  # noqa
import opt_a  # noqa
from view import POSES  # noqa
from PIL import Image, ImageDraw  # noqa

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/ch4_roughs"
SOOT = [(14, 12, 14), (30, 26, 28), (50, 44, 44), (74, 66, 62)]
BASALT = [(24, 24, 30), (44, 44, 52), (66, 66, 76), (92, 92, 102)]
CLAY = [(70, 60, 54), (110, 98, 88), (148, 136, 122), (184, 172, 156)]


def mats():
    m = copy.deepcopy(opt_a.BASE)
    m["soot"] = dict(ramp=SOOT, base=0.5, lit=0.26, noise=0.14, crack=0.0, min=0)
    m["basalt"] = dict(ramp=BASALT, base=0.55, lit=0.3, noise=0.1, crack=0.0, min=0)
    m["clay"] = dict(ramp=CLAY, base=0.55, lit=0.3, noise=0.08, crack=0.0, min=0, stripe=3, axis="h")
    return m


def lump(bone, mat, c, r, segs=6, rings=1):
    return ellipsoid(bone, mat, c, (r, r * 0.8, r), segs=segs, rings=rings)


def drop(F, pred):
    return [f for f in F if not pred(f)]


def cy(f):
    return float(np.asarray(f.verts).mean(0)[1])


# ====================================================================== 灰被りの屑 (未完成。殻が薄く欠けている)
def husk_a(F):
    """A 欠けた殻: 頭巾の上半分と外套の裾がない。殻の欠けから黒い体と熾がのぞく。"""
    F = drop(F, lambda f: f.bone == "crust_head" and cy(f) > 33.5)
    F = drop(F, lambda f: f.bone == "crust_body" and cy(f) < 8.0)
    F = drop(F, lambda f: f.bone in ("crust_arm_l", "crust_fore_l"))
    F += lump("crust_body", "ashd", (2.6, 21.0, -4.3), 1.4)
    F += lump("crust_body", "ash", (-2.2, 14.0, -4.6), 1.6)
    for s in (1, -1):
        F += fbox("body", "ember", (s * 1.6, 18.0, -2.9), (0.6, 3.0, 0.4), taper=(0.6, 0.6), caps=(False, False))
    return F


def husk_b(F):
    """B 灰の繭: 頭巾も肩の板もなく、灰の塊が胴と脚にまだらに付いた繭のような姿。頭はむき出しの炭。"""
    F = drop(F, lambda f: f.bone == "crust_head")
    F = drop(F, lambda f: f.bone in ("crust_arm_r", "crust_arm_l", "crust_fore_r", "crust_fore_l"))
    for (x, y, z, r) in ((-3.0, 24.0, -3.4, 2.0), (2.8, 22.0, -3.8, 2.2), (0.0, 17.5, -4.8, 2.4), (-3.4, 12.5, -4.2, 2.0), (3.2, 11.0, -3.6, 1.8),
                         (0.0, 22.5, 3.2, 2.4), (0.0, 13.0, 4.0, 2.4)):
        F += lump("crust_body", "ash", (x, y, z), r)
    F += lump("crust_head", "ashd", (0.0, 34.6, 0.0), 1.6)
    return F


def husk_c(F):
    """C 練りかけの土偶: 殻が灰を練った土色で、指で撫でた筋が残る。片腕だけ殻がなく、胸に打ちかけの釘。"""
    for f in F:
        if f.bone.startswith("crust_") and f.mat in ("ash", "ashd"):
            f.mat = "clay"
    F = drop(F, lambda f: f.bone in ("crust_arm_l", "crust_fore_l"))
    F = drop(F, lambda f: f.bone == "crust_head" and cy(f) > 36.0)
    F += sp("crust_body", "ember", (0.8, 20.5, -4.6), (0.0, 0.1, -1.0), 2.0, 0.35, sides=3, rings=1)
    F += sp("crust_body", "soot", (-1.4, 22.0, -4.5), (0.1, 0.0, -1.0), 1.6, 0.3, sides=3, rings=1)
    return F


# ====================================================================== 古い灰被り (名づき。殻が厚く、背に煤の釜)
def pot(F, bone="body", z=4.6, r=3.2, y=21.0, rim="ember"):
    F += ellipsoid(bone, "basalt", (0, y, z + r * 0.6), (r, r * 1.05, r * 0.9), segs=8, rings=2)
    F += tube(bone, "soot", [(0, y + r * 0.9, z + r * 0.6), (0, y + r * 1.3, z + r * 0.6)], [r * 0.62, r * 0.66], sides=8, hint=(1, 0, 0), caps=(False, False))
    F += ellipsoid(bone, rim, (0, y + r * 1.32, z + r * 0.6), (r * 0.52, 0.3, r * 0.52), segs=8, rings=1)
    for s in (1, -1):
        F += tube(bone, "soot", [(s * 3.4, 26.5, -0.5), (s * 2.6, y, z + 0.4)], [0.35, 0.35], sides=3, hint=(0, 0, 1))


def thick(F, k=1.0):
    for s in (1, -1):
        F += fbox(f"crust_arm_{'r' if s > 0 else 'l'}", "ashd", (s * 6.6, 27.6, -0.8), (6.2 * k, 1.6, 5.8 * k), R=rot_z(-s * 12), taper=(0.75, 0.75), cut=0.4)
        F += fbox(f"crust_leg_{'r' if s > 0 else 'l'}", "ashd", (s * 2.6, 7.4, -1.4), (3.8, 3.6, 4.4), taper=(0.8, 0.8))
    F += fbox("crust_body", "ash", (0.0, 21.5, -4.9), (6.2 * k, 7.0, 1.4), taper=(0.85, 1.0))


def elder_a(F):
    """A 煤の釜: 灰被りの外套に厚い肩の板を重ね、背に縄で煤の釜を負う。釜の口に熾。"""
    thick(F)
    pot(F)
    return F


def elder_b(F):
    """B 厚い灰の鎧: 殻を二重にし、頭巾のまわりに灰の棘の冠。背の釜は小さく、腰に下げる。"""
    thick(F, 1.12)
    for i, a in enumerate(range(-60, 61, 30)):
        x = 3.4 * np.sin(np.radians(a))
        z = -1.0 - 3.0 * np.cos(np.radians(a))
        F += sp("crust_head", "ashd", (x, 34.0, z), (x * 0.25, 1.0, (z + 1) * 0.15), 2.6, 0.6, sides=3, rings=1)
    pot(F, z=4.0, r=2.2, y=14.5)
    return F


def elder_c(F):
    """C 炉の背負い子: 背に木の枠と小さな炉 (黒石の箱、焚き口に熾、上に煙突)。殻は肩だけ厚い。"""
    thick(F, 0.9)
    F += fbox("body", "basalt", (0.0, 21.0, 6.2), (6.0, 7.0, 4.4), taper=(0.9, 0.9))
    F += fbox("body", "ember", (0.0, 19.5, 8.5), (2.4, 1.8, 0.3), taper=(1.0, 1.0), caps=(False, False))
    F += tube("body", "soot", [(1.6, 24.5, 6.4), (1.6, 31.0, 6.8)], [0.9, 0.8], sides=6, hint=(1, 0, 0), caps=(False, True))
    for s in (1, -1):
        F += tube("body", "soot", [(s * 3.2, 27.0, 1.0), (s * 3.2, 15.0, 3.6)], [0.4, 0.4], sides=3, hint=(0, 0, 1))
    return F


def rake(P):
    """古い灰被りの火掻き: 火掻き棒を 1.25 倍に伸ばし、先に 3 本の爪を足す (socket の pivot が原点・先が +Y)。"""
    out = []
    for f in copy.deepcopy(P):
        f.verts = np.asarray(f.verts, float) * np.array([1.0, 1.25, 1.0])
        if f.loc is not None:
            f.loc = np.asarray(f.loc, float) * np.array([1.0, 1.25, 1.0])
        f.ref = np.asarray(f.ref, float) * np.array([1.0, 1.25, 1.0])
        out.append(f)
    for x in (-1.6, 0.0, 1.6):
        out += sp("weapon", "iron", (x, 26.0, 1.0), (x * 0.2, -0.3, 1.0), 4.4, 0.45, sides=3, rings=1)
    return out


SUBJECTS = {
    "ash_husk": dict(
        short="灰被りの屑", ja="灰被りの屑 (ash_husk)", scale=0.85, adds={"a": husk_a, "b": husk_b, "c": husk_c},
        names={"a": "A 欠けた殻", "b": "B 灰の繭", "c": "C 練りかけの土偶"}, weapon=None,
        sub="灰被りの未完成。殻 15 (割れると燃えて速くなる。割る前の傷は半分)。HP 28、殴り 8。消えた炉の坑の鍛えの跡に 2 体。ゲームでは灰被りの 0.85 倍で描く。",
        desc={"a": "頭巾の上半分・外套の裾・左腕の殻がまだない。殻の欠けから黒い体と熾がのぞき、どこを叩けば殻が割れるかが見える。灰被りの「途中」が一番素直に伝わる。",
              "b": "頭巾も肩の板もなく、灰の塊が胴にまだらに付いた繭のような姿。頭はむき出しの炭。灰被りとの見分けが一番つく (遠目にも別の敵)。",
              "c": "殻が灰を練った土色で、撫でた筋が残る。片腕は殻なし、胸に打ちかけの熾の釘。鍛えの跡 (作りかけ) の部屋の話に合う。"}),
    "elder_thrall": dict(
        short="古い灰被り", ja="古い灰被り (elder_thrall)", scale=1.15, adds={"a": elder_a, "b": elder_b, "c": elder_c},
        names={"a": "A 煤の釜", "b": "B 厚い灰の鎧", "c": "C 炉の背負い子"}, weapon=rake,
        sub="消えた炉の坑の主 (名づき)。HP 210、火掻き 12、殻 40、背に煤の釜。振り下ろし・足もとの亀裂 (1.5 秒後に噴く)・殻が割れると熱の波。ゲームでは灰被りの 1.15 倍。",
        desc={"a": "灰被りに厚い肩の板と脚の殻を重ね、背に縄で煤の釜を負う。釜の口に熾。21 の「背に煤の釜」そのままで、灰被りの年を経た姿と読める。",
              "b": "殻を二重にし、頭巾のまわりに灰の棘の冠。釜は小さく腰に下げる。名づきの格が一番出る (遠くから主と分かる)。",
              "c": "背に木の枠と小さな炉 (黒石の箱・熾の焚き口・煙突)。肩だけ厚い殻。「消えた炉」の坑の主として、炉そのものを背負っている。"}),
}


def build_subject(sid):
    spec = SUBJECTS[sid]
    core.OUT = OUT
    core.SUBJ = sid
    core.NAME = sid
    results = {}
    for k in "abc":
        def b(k=k):
            d = opt_a.build()
            m = mats()
            d["body"] = spec["adds"][k](d["body"])
            d["pal_n"] = dict(mats=m, glow_mats=["ember"])
            d["pal_f"] = dict(mats=m, glow_mats=["ember", "flame"])
            if spec["weapon"]:
                d["weapon"] = spec["weapon"](d["weapon"])
            return d
        r = core.build_option(k, b, body_hint=112)
        results[k] = r
        print(sid, k, "tris n", core.tri_state(r["body"], "n"), "f", core.tri_state(r["body"], "f"), "weapon", tri_count(r["weapon"]), "atlas", r["size"])
    return results


def sheet(sid, results):
    spec = SUBJECTS[sid]
    PW, PH = 380, 470
    views = [("前 (殻)", POSES["rest"], 205, 12, "n"), ("後ろ (殻)", POSES["rest"], 25, 12, "n"), ("振りかぶり", POSES["wind"], 200, 10, "n"),
             ("燃え上がり", POSES["rest"], 205, 12, "f")]
    W = 20 + len(views) * (PW + 10) + 10
    H = 100 + 3 * (PH + 40)
    cv = Image.new("RGB", (W, H), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    F = voxel.font
    d.text((20, 12), f'{spec["ja"]} — 灰被りの変種・低ポリ 3 案', font=F(26), fill=(236, 232, 218))
    d.text((20, 52), spec["sub"], font=F(14), fill=(160, 160, 176))
    y = 90
    for k in "abc":
        r = results[k]
        d.text((20, y), f'{spec["names"][k]}  殻 {core.tri_state(r["body"], "n")} / 燃え {core.tri_state(r["body"], "f")} 三角形', font=F(19), fill=(150, 232, 255))
        y += 28
        for i, (name, pose, yaw, pitch, state) in enumerate(views):
            im = core.render_pose(r, pose, yaw, pitch, (PW, PH), (0, 21, -1), 10.0, state=state)
            bg = Image.fromarray(np.clip(voxel._background(PW, PH, "dark"), 0, 255).astype(np.uint8))
            bg.paste(im, (0, 0), im)
            x = 20 + i * (PW + 10)
            cv.paste(bg, (x, y))
            d.text((x + 4, y + 4), name, font=F(14), fill=(255, 220, 140))
        y += PH + 12
    cv = cv.crop((0, 0, W, y + 6))
    cv.save(OUT / f"{sid}.png")
    opts = {k: {"name": spec["names"][k] + f' ({core.tri_state(results[k]["body"], "n")} 三角形)', "desc": spec["desc"][k]} for k in "abc"}
    man = {"id": "ch4_" + sid, "short": spec["short"], "kind": "地上の敵 3案 (P20)", "title": spec["ja"] + " — 灰被りの変種・低ポリ 3 案",
           "sheets": [f"ch4_roughs/{sid}.png"],
           "extra": "背中の灰被り (A 灰衣の火番) の殻を削る・足すだけの変種。骨とアニメは灰被りと同じ。メッシュ・アトラス (殻 / 燃え上がり) は "
                    f"docs/review/ch4_roughs/{sid}/{{a,b,c}}/。生成: tools/rough/lowpoly/ch4/thrall_variants.py。", "options": opts}
    (OUT / f"{sid}_manifest.json").write_text(json.dumps(man, ensure_ascii=False, indent=1), encoding="utf-8")


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    for sid in sys.argv[1:] or list(SUBJECTS):
        sheet(sid, build_subject(sid))


if __name__ == "__main__":
    main()
