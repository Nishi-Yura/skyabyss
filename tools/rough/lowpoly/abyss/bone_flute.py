"""骨笛 (bone_flute) 低ポリ ラフ 3 案。骨の層の浮かぶ頭蓋。音で他の骨の敵を呼ぶ。
A 笛の頭蓋 (顎から骨の笛が一本出る) / B 角の頭蓋 (頭蓋の両側に角の管、背に脊椎の尾) / C 輪の頭蓋 (脊椎の輪が頭蓋を巻く、口は管)
骨: root > head / jaw / tail。頭蓋は浮かぶ (地面から 4 〜 9 単位離れる)。
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from ck5 import *  # noqa

BONES = {"root": (None, (0, 0, 0)), "head": ("root", (0, 7.0, 0)), "jaw": ("head", (0, 5.2, -1.2)),
         "tail": ("head", (0, 5.6, 1.0))}
PAL = make_pal({
    "bone": (BONEC, 0.62, dict(crack=0.14, noise=0.22)),
    "calc": (CALC, 0.6, dict(crack=0.14, noise=0.2)),
    "hollow": (HOLLOW, 0.5, dict(crack=0.0, noise=0.05, min=0)),
    "glow": ([(150, 46, 16), (210, 84, 24), (255, 146, 40), (255, 210, 96)], 0.7, dict(crack=0.0, noise=0.06, min=1)),
}, glow=["glow"])


def skull(F, mat_="bone"):
    F += part("skull", ellipsoid("head", mat_, (0, 7.0, 0), (2.8, 2.6, 2.6), segs=10, rings=5))
    F += part("eyesock_l", ellipsoid("head", "hollow", (-1.0, 7.4, -2.5), (0.8, 0.8, 0.4), segs=6, rings=2))
    F += part("eyesock_r", ellipsoid("head", "hollow", (1.0, 7.4, -2.5), (0.8, 0.8, 0.4), segs=6, rings=2))
    F += part("nose", ellipsoid("head", "hollow", (0, 6.4, -2.7), (0.35, 0.5, 0.3), segs=4, rings=1))
    F += part("jaw", ellipsoid("jaw", mat_, (0, 5.0, -1.4), (1.7, 0.8, 1.5), segs=8, rings=3))


def build_a(k):
    """A 笛の頭蓋: 頭蓋の顎から骨の笛が一本、斜め前へ出る。笛の穴に熾の粒 (鳴らすと光る)。"""
    F = []
    skull(F)
    F += part("pipe", tube("jaw", "calc", [(0, 5.2, -1.2), (0, 4.4, -3.8), (0.2, 3.6, -6.0)], ra=[0.45, 0.42, 0.38], sides=6, hint=(0, 1, 0)))
    F += part("hole", ellipsoid("jaw", "glow", (0.2, 3.6, -6.1), (0.2, 0.2, 0.2), segs=5, rings=2))
    F += part("tail1", tube("tail", "bone", [(0, 6.6, 1.5), (0.0, 4.6, 3.2), (0.1, 2.4, 4.6)], ra=[0.6, 0.5, 0.4], sides=6, hint=(0, 1, 0)))
    F += part("tail2", tube("tail", "calc", [(0.1, 2.4, 4.6), (0.0, 0.6, 5.6)], ra=[0.4, 0.2], sides=5, hint=(0, 1, 0)))
    return F, [eye_at(-1.0, 7.4, -2.9, 0.4), eye_at(1.0, 7.4, -2.9, 0.4)]


def build_b(k):
    """B 角の頭蓋: 頭蓋の両側から角の管が後ろへ曲がる。背に脊椎の尾が垂れ、浮かんで揺れる。"""
    F = []
    skull(F)
    for s, nm in ((-1, "l"), (1, "r")):
        F += part(f"horn_{nm}", tube("head", "calc", [(s * 2.2, 8.6, 0.4), (s * 3.9, 10.8, 0.9), (s * 4.3, 10.6, 3.0)], ra=[0.6, 0.45, 0.1], sides=5, hint=(0, 1, 0)))
    F += part("spine", tube("tail", "bone", [(0, 6.6, 1.6), (0, 4.6, 3.6), (0, 2.6, 4.8), (0, 0.8, 4.4)], ra=[0.5, 0.42, 0.36, 0.1], sides=6, hint=(1, 0, 0)))
    F += part("rib_l", spike("tail", "calc", (-0.3, 4.4, 3.4), (-1, -0.4, 0.2), 2.0, 0.28, sides=4, rings=2))
    F += part("rib_r", spike("tail", "calc", (0.3, 2.2, 4.4), (1, -0.3, 0.2), 2.0, 0.28, sides=4, rings=2))
    return F, [eye_at(-1.0, 7.4, -2.9, 0.4), eye_at(1.0, 7.4, -2.9, 0.4)]


def build_c(k):
    """C 輪の頭蓋: 脊椎の輪が頭蓋を三つ巻く (輪は頭蓋に沈む)。口は細い管。"""
    F = []
    skull(F, "bone")
    for i, (tilt, y) in enumerate(((0, 7.0), (1.2, 7.2), (-1.2, 6.8))):
        R = [ring(y - 0.4, 3.2, -2.4 + tilt * 0.3, 2.4 + tilt * 0.3, n=10), ring(y, 3.4, -2.6, 2.6, n=10), ring(y + 0.4, 3.2, -2.4 - tilt * 0.3, 2.4 - tilt * 0.3, n=10)]
        F += part(f"hoop{i}", loft("head", "calc", R, caps=("calc", "calc")))
    F += part("mouthpipe", tube("jaw", "bone", [(0, 5.2, -1.2), (0, 5.4, -3.0), (0, 5.6, -4.4)], ra=[0.4, 0.36, 0.3], sides=6, hint=(0, 1, 0)))
    F += part("tail", tube("tail", "calc", [(0, 6.6, 1.0), (0, 4.0, 2.6), (0, 2.4, 2.4)], ra=[0.36, 0.3, 0.1], sides=5, hint=(0, 1, 0)))
    return F, [eye_at(-1.0, 7.4, -2.9, 0.4), eye_at(1.0, 7.4, -2.9, 0.4)]


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


POSES = {
    "idle": {},
    "call": {"head": (-10, 0, 0), "jaw": (-25, 0, 0)},
}

SPEC = dict(
    id="bone_flute", short="骨笛", kind="骨の層の敵 3案 (A3)",
    title="骨笛 (bone_flute) — 音で仲間を呼ぶ浮かぶ頭蓋・低ポリ + ドット絵 3 案",
    sub=["骨の層の浮かぶ敵。地面から少し離れて止まり、笛を鳴らして骨の仲間を呼ぶ。呼ばれた仲間は一度だけ呼ばれた所へ寄る。",
         "叩けば割れる (頭蓋の殻は薄い)。このシートは浮く一体を大きく寄せて描いた (ruler は人の目盛り 1.8 ブロック)。"],
    labels=["A 笛の頭蓋", "B 角の頭蓋", "C 輪の頭蓋"],
    rows=[("前 3/4", "idle", 212, 18, 300, (0, 5.4, 0), 19.0),
          ("横", "idle", 270, 6, 300, (0, 5.4, 0), 19.0),
          ("上から", "idle", 200, 60, 300, (0, 5.4, 0), 19.0),
          ("鳴らす (呼ぶ)", "call", 230, 12, 300, (0, 5.4, 0), 19.0),
          ("後ろ 3/4", "idle", 32, 16, 300, (0, 5.4, 0), 19.0)],
    options={
        "a": dict(name="A 笛の頭蓋", desc="頭蓋の顎から骨の笛が一本出る。笛の穴に熾の粒が光り、鳴らすと粒が跳ねる。一番「楽器」として読める。"),
        "b": dict(name="B 角の頭蓋", desc="頭蓋の両側に角の管が後ろへ曲がり、背から脊椎の尾が垂れる。浮かんで揺れる姿が大きく、遠くから見分けやすい。"),
        "c": dict(name="C 輪の頭蓋", desc="脊椎の輪が頭蓋を三つ巻き、口は細い管。輪が鳴るように揺れる。一番丸く、他の骨の敵の中で埋もれない。")},
    extra="骨の言葉 (骨・方解石・洞の黒・笛の穴の熾)。骨 4 本 (root > head > jaw / tail)。頭蓋は浮かぶので地面に付かない。",
    budget="浮く一体 300〜450", atlas_h=150, pw=420,
    later="歪み = 輪や角が増え、頭蓋の目が大きく穴になる。崩れ = 頭蓋が割れて笛だけが残り、音だけが鳴る (見えない)。",
)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES, hint=256)
