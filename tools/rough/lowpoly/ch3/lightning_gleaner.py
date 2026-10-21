"""雷拾い (lightning_gleaner) 低ポリ ラフ 3 案。追いかけっこ (敵意なし)。HP 12。
銅の脚の長い鳥。焼けた荷の跡を拾って歩く。人が近づくと羽ばたいて逃げ、荷を落とす。追うと雷拾いの原へ導く。
A 銅脚の鷺 (灰青の鷺・長い銅の脚・嘴の先に拾った銅の切れ端) / B 荷負い鳥 (背に銅線の束を括った丸い鳥・短い首) / C 避雷の鶴 (白い鶴・脚に銅線を巻き、頭に光る銅の冠)
骨: root > body > neck > head / wing_l / wing_r / tail / leg_l / leg_r / pack (背の荷。落とすと隠す)。ついばむ = 首を前へ下げる。
"""
import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck3 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 14.0, 0)), "neck": ("body", (0, 16.0, -2.6)), "head": ("neck", (0, 22.0, -4.0)),
         "wing_l": ("body", (-2.6, 16.0, -1.0)), "wing_r": ("body", (2.6, 16.0, -1.0)), "tail": ("body", (0, 14.4, 3.4)),
         "leg_l": ("body", (-1.2, 12.6, 0.2)), "leg_r": ("body", (1.2, 12.6, 0.2)), "pack": ("body", (0, 17.0, 0.8))}

PAL = {"mats": {
    "grey": mat(ROCK, 0.66, crack=0.0, noise=0.2, facevar=0.08),
    "feather": mat(FEATHER, 0.66, crack=0.0, noise=0.12, stripe=3, axis="v"),
    "white": mat(DOWN, 0.72, crack=0.0, noise=0.12, facevar=0.06),
    "copper": mat(COPPER, 0.68, crack=0.0, noise=0.08, stain=dict(ramp=VERD, thr=0.95, up=0.3, conc=0.25)),
    "wire": mat(COPPER, 0.76, crack=0.0, stripe=2, axis="h"),
    "cloth": mat(SAND, 0.56, crack=0.0, noise=0.2),
    "char": mat(CHAR, 0.58, crack=0.0, noise=0.2),
    "glow": mat(BOLT, 0.8, crack=0.0, min=1),
    "eye": mat(DEEP, 0.5, crack=0.0, min=0),
    "eyeb": mat(BOLT, 0.78, crack=0.0, min=2),
}, "skull_bone": "none", "face_mat": "none", "glow_mats": ["glow", "eyeb"]}


def bird(F, body_mat, wing_mat, leg_mat, neck_len=6.0, plump=1.0, head_mat=None, beak_len=4.0):
    head_mat = head_mat or body_mat
    F += part("torso", ellipsoid("body", body_mat, (0, 15.4, 0.2), (2.8 * plump, 2.6 * plump, 4.0), segs=7, rings=2))
    top = 16.0 + neck_len
    F += part("neckt", tube("neck", body_mat, [(0, 16.0, -2.4), (0, 16.0 + neck_len * 0.55, -3.6), (0, top, -4.0)], ra=[1.3, 0.9, 0.8], sides=5, caps=(False, False)))
    F += part("headb", ellipsoid("head", head_mat, (0, top + 0.6, -4.4), (1.3, 1.3, 1.7), segs=6, rings=2))
    F += part("beak", spike("head", "copper", (0, top + 0.4, -5.8), (0, -0.2, -1), beak_len, 0.5, sides=4, rings=1))
    for s in (-1, 1):
        F += part(f"eye{s}", ellipsoid("head", "eye", (s * 1.05, top + 1.0, -5.0), (0.3, 0.36, 0.3), segs=4, rings=1))
    for s, b in ((-1, "wing_l"), (1, "wing_r")):
        # たたんだ翼 (体の脇に沿う、平たい閉じた楕円体。根元は胴に沈める)
        F += part(f"wing{s}", ellipsoid(b, wing_mat, (s * 2.7, 15.8, 0.8), (0.7, 1.9, 4.2), segs=6, rings=1))
    F += part("tailf", ellipsoid("tail", wing_mat, (0, 14.8, 5.2), (1.4, 0.5, 2.0), segs=5, rings=1))
    for s, b in ((-1, "leg_l"), (1, "leg_r")):
        F += part(f"thigh{s}", tube(b, leg_mat, [(s * 1.2, 13.4, 0.2), (s * 1.3, 7.2, 0.6), (s * 1.3, 0.6, -0.2)], ra=[0.5, 0.36, 0.3], sides=4, caps=(False, True)))
        F += part(f"knee{s}", ellipsoid(b, leg_mat, (s * 1.3, 7.2, 0.6), (0.5, 0.6, 0.5), segs=4, rings=1))
        for j, (dx, dz) in enumerate(((0, -1), (-0.6, -0.8), (0.6, -0.8))):
            F += part(f"toe{s}{j}", spike(b, leg_mat, (s * 1.3, 0.5, -0.2), (dx, -0.05, dz), 2.0, 0.26, sides=3, rings=1))


def build_a(k):
    """A 銅脚の鷺: 灰青の羽の鷺。長い銅の脚と嘴。嘴の先に拾った銅の切れ端をくわえる。"""
    F = []
    bird(F, "grey", "feather", "copper", neck_len=6.4)
    F += part("scrap", tube("head", "wire", [(-0.8, 22.0, -9.8), (0.2, 21.8, -9.6), (1.0, 21.2, -9.9)], ra=0.22, sides=3, caps=(True, True)))
    # 背の荷は小さな焦げた布の包み
    F += part("bundle", ellipsoid("pack", "cloth", (0, 18.2, 1.0), (1.6, 1.0, 2.0), segs=6, rings=1))
    return F, []


def build_b(k):
    """B 荷負い鳥: 首の短い丸い鳥。背に銅線の束を括り、焦げた布で包む。拾い集める鳥らしさがいちばん強い。"""
    F = []
    bird(F, "feather", "grey", "copper", neck_len=4.0, plump=1.2, beak_len=3.0)
    F += part("bundle", ellipsoid("pack", "cloth", (0, 18.6, 0.8), (2.2, 1.4, 2.6), segs=7, rings=2))
    for i, z in enumerate((-0.6, 0.8, 2.2)):
        F += part(f"coil{i}", tube("pack", "wire", [(-2.4, 19.0, z), (0, 20.3, z), (2.4, 19.0, z)], ra=0.3, sides=3, caps=(True, True)))
    F += part("strap", tube("pack", "char", [(-2.6, 17.4, 0.6), (0, 16.9, -2.6), (2.6, 17.4, 0.6)], ra=0.3, sides=3, caps=(True, True)))
    return F, []


def build_c(k):
    """C 避雷の鶴: 白い鶴。脚に銅線を巻き、頭に光る銅の冠 (小さな避雷針)。雷の跡を嗅ぎ分ける鳥。"""
    F = []
    bird(F, "white", "white", "char", neck_len=7.0, head_mat="white")
    for s in (-1, 1):
        for y in (3.0, 5.0, 9.0, 11.0):
            F += part(f"wrap{s}{y}", tube(f"leg_{'l' if s < 0 else 'r'}", "wire", [(s * 1.3 - 0.45, y, 0.4), (s * 1.3 + 0.45, y + 0.4, 0.4)], ra=0.36, sides=4, caps=(True, True)))
    top = 23.0
    F += part("crest", tube("head", "copper", [(0, top + 1.6, -4.2), (0, top + 3.4, -3.6)], ra=[0.3, 0.24], sides=3, caps=(False, False)))
    F += part("tip", spike("head", "glow", (0, top + 3.4, -3.6), (0, 1, 0.2), 1.2, 0.28, sides=3, rings=1))
    F += part("bundle", ellipsoid("pack", "cloth", (0, 18.0, 1.4), (1.4, 0.9, 1.8), segs=6, rings=1))
    return F, []


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "peck": {"neck": (-70, 0, 0), "head": (-30, 0, 0), "body": (-12, 0, 0)},
    "flee": {"wing_l": (0, 0, -70), "wing_r": (0, 0, 70), "neck": (18, 0, 0), "leg_l": (30, 0, 0), "leg_r": (-20, 0, 0), "body": (-10, 0, 0)},
}

SPEC = dict(
    id="lightning_gleaner", short="雷拾い", kind="地上の小物 3案 (P16・敵意なし)",
    title="雷拾い (lightning_gleaner) — 銅の脚の長い鳥・低ポリ + ドット絵 3 案",
    sub=["三章の高台の小物。敵意なし。HP 12。焼けた荷の跡をついばんで歩く。人が近づくと羽ばたいて逃げ、背の荷 (銅線・焦げ羽) を落とす。",
         "逃げる先は雷拾いの原 (追うと原へ導く)。背の高さ 約 1.5 ブロック。左の黄の目盛りは人 (1.8 ブロック)。"],
    labels=["A 銅脚の鷺", "B 荷負い鳥", "C 避雷の鶴"],
    rows=[("前 3/4", "idle", 212, 10, 340, (0, 13, 0), 9.0),
          ("横", "idle", 270, 4, 340, (0, 13, 0), 9.0),
          ("ついばむ (焼けた荷の跡)", "peck", 240, 8, 340, (0, 12, 0), 9.0),
          ("逃げる (羽ばたいて荷を落とす)", "flee", 200, 14, 340, (0, 14, 0), 8.0)],
    options={
        "a": dict(name="A 銅脚の鷺", desc="灰青の羽の鷺。長い銅の脚と銅の嘴で、嘴の先に拾った銅の切れ端をくわえる。峰の高台に一羽だけ立つ姿が絵になる。背の荷は小さな焦げた包み。"),
        "b": dict(name="B 荷負い鳥", desc="首の短い丸い鳥で、背に銅線の束を焦げた布で括って負う。荷を落とすのが一目で分かり、「荷を拾い集める」話がいちばん伝わる。足どりはせかせか。"),
        "c": dict(name="C 避雷の鶴", desc="白い鶴。黒い脚に銅線を巻き、頭に光る銅の冠 (小さな避雷針)。雷の落ちた跡を嗅ぎ分ける鳥という話が付く。白いので遠くから見つけやすく、冠の光で追える。")},
    extra="三章の言葉 (峰の灰青・白・銅・焦げた布・雷の青白)。骨 10 本。背の荷 (pack) は骨ごと隠せる (落としたあと)。逃げるときは羽ばたいて低く跳ぶ。",
    budget="地上の小物 300〜500", atlas_h=200)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
