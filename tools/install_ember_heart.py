"""余燼の心臓 (余燼の巨神の最終ボス) のラフ 案B 熔け殻 をゲームに入れる。

使い方 (リポジトリ直下で): python tools/install_ember_heart.py
形の元: tools/sources/ember_heart_hp/phaseN.geo.json, phaseN.tex.png, phaseN.glow.png (tools/rough/hp_heart_ember.py の B 案で 0.25 に彫り直したもの。元の 0.5 は tools/sources/ember_heart)
        (tools/rough/ember_heart.py の B 案。比較の絵は docs/review/ember_roughs/heart/b。選ばれなかった A 炉・C 黒曜の棘は同じ docs/review/ember_roughs/heart/a・c に残る)
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/ember_heart_p1|p2|p3.geo.json
  textures/entity/ember_heart_p1|p2|p3.png と _glowmask.png
  animations/entity/ember_heart.animation.json
確認用: docs/review/previews/ember_heart.png (GeckoLib の読み方を真似て描いた絵と、ラフの絵を並べる。git の外)

向きの変換は岩鯨・潮殻の心臓 (tools/install_heart_v4.py) と同じ。ラフは正面 +Z で描いてあるので、
Z を反転した geo を書けば、ゲームでは正面が前を向き、鏡に映らない。アニメーションもラフの空間で書き、同じ変換をかける。

段階は段階ごとの geo で切り替える (骨の表示の切り替えより、箱の数と光る面を段階ごとに素直に持てる)。
段階 3 は箱が少なく疎なので、火の粉 (cinders) を上下に揺らし、灰の殻 (ash_shell) をゆっくり揺らす。光の粒はゲームの側 (EmberHeart.tick) で足す。

色 (潮殻の心臓 08 の 19.8節の直し方に倣う):
- ラフの黒い玄武岩の殻は明るさの中央が 34 / 255 と暗く、ゲームの暗い部屋 (段階 3 は血管が消える) で面の陰を受けると黒く沈む。
  殻 (彩りの薄い画素) を段階ごとの色へ寄せ、明るさの幅を持ち上げる (段階 1・2 は暗い鉱滓色 → 48〜104、段階 3 は灰の白のまま少し持ち上げる)。
- 光る画素は、基のテクスチャの色 (殻と同じく陰を受ける) に、発光の絵の色を「足す」(EmberHeartRenderer.GlowLayer の eyes)。
  足す色が光の強さ。殻より明るく、陰の面でも沈まない。段階 1 は橙、段階 2 は白熱の黄、段階 3 は暗い赤 (少ない画素)。光らない画素は黒。
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import install_heart_v4 as H  # noqa: E402  (convert_geo, gecko_faces, track, sample を使う)
from voxel import load_geo, render, sheet  # noqa: E402

SRC = ROOT / "tools/sources/ember_heart_hp"
ASSET = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/ember_heart.png"
NAME = "ember_heart"
PIVOT = [0.0, 16.0, 0.0]

track = H.track
Z3 = [0, 0, 0]
ONE = [1, 1, 1]


def S(s):
    return [s, s, s]


def tremble(times, amp, axis=2):
    keys = [(0, Z3)]
    for n, t in enumerate(times):
        v = [0, 0, 0]
        v[axis] = amp * (1 if n % 2 == 0 else -1)
        keys.append((t, v))
    return keys


def bob(length, amp, steps=2):
    keys = [(0, Z3)]
    for k in range(1, steps * 2):
        keys.append((length * k / (steps * 2), [0, amp if k % 2 == 1 else -amp, 0]))
    keys.append((length, Z3))
    return keys


def anims():
    A = {}
    L = 3.6
    beats = [(0, ONE)]
    for t0 in (0, 1.8):
        beats += [(t0 + 0.2, S(1.06)), (t0 + 0.4, ONE), (t0 + 0.55, S(1.04)), (t0 + 0.9, ONE)]
    beats.append((L, ONE))
    eye = [(0, ONE), (0.2, S(1.2)), (0.45, ONE), (2.0, S(1.2)), (2.25, ONE), (L, ONE)]
    A["idle"] = {"loop": True, "animation_length": L, "bones": {
        "heart": {"scale": track(beats, "scale")},
        "heart_vein": {"scale": track(beats, "scale")},
        "heart_crust": {"scale": track(beats, "scale")},
        "vessel": {"rotation": track([(0, Z3), (0.9, [4, 0, 0]), (1.8, Z3), (2.7, [-3, 0, 0]), (L, Z3)], "rotation")},
        "core_eye": {"scale": track(eye, "scale")},
        "core_ring": {"scale": track(beats, "scale")},
        "crust_glow": {"scale": track([(0, ONE), (0.2, S(1.015)), (0.45, ONE), (2.0, S(1.015)), (2.25, ONE), (L, ONE)], "scale")},
        # 殻はゆっくり揺れる (段階 1・2)。灰の殻 (段階 3) はもっとゆっくり
        "crust": {"rotation": track([(0, [0, 0, -1.5]), (1.8, [0, 0, 1.5]), (L, [0, 0, -1.5])], "rotation")},
        "ash_shell": {"rotation": track([(0, [0, 0, -1]), (1.8, [0, 0, 1]), (L, [0, 0, -1])], "rotation")},
        # 垂れる熔岩は少しずつ伸び縮み、落ちる滴は上下に揺れる
        "drips": {"scale": track([(0, ONE), (1.8, [1, 1.06, 1]), (L, ONE)], "scale")},
        "drops": {"position": track([(0, Z3), (1.2, [0, -1.0, 0]), (2.4, [0, 0.6, 0]), (L, Z3)], "position")},
        "ash_drips": {"scale": track([(0, ONE), (1.8, [1, 1.05, 1]), (L, ONE)], "scale")},
        # 火の粉は上へ漂って戻る (疎な段階 3 の火は、ここがほぼ全部)
        "cinders": {"position": track([(0, Z3), (1.8, [0, 1.6, 0]), (L, Z3)], "position"),
                    "rotation": track([(0, Z3), (1.8, [0, 12, 0]), (L, Z3)], "rotation")},
    }}
    A["walk"] = A["idle"]

    # ---- 咆哮 (段階の変わり目、3 秒) ----
    # 段階 1 → 2: 殻が震え、上が熔けはじめる (割れ目が強く光る)。終わりに段階 2 の模型に替わる
    A["roar_1"] = {"animation_length": 3.0, "bones": {
        "heart": {"scale": track([(0, ONE), (0.4, S(1.14)), (0.8, S(0.95)), (1.2, S(1.16)), (1.8, ONE), (3.0, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (0.8, S(1.7)), (2.0, S(1.7)), (3.0, ONE)], "scale")},
        "crust": {"rotation": track(tremble([0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0], 3) + [(2.2, Z3), (3.0, Z3)], "rotation"),
                  "scale": track([(0, ONE), (1.8, S(1.0)), (2.2, S(1.08)), (3.0, S(1.08))], "scale")},
        "crust_glow": {"scale": track([(0, ONE), (1.0, S(1.03)), (2.0, S(1.08)), (3.0, S(1.08))], "scale")},
        "cinders": {"position": track([(0, Z3), (2.0, [0, 5, 0]), (3.0, [0, 5, 0])], "position")},
    }}
    # 段階 2 → 3: 白熱の心臓が最後に強く燃え、灰の殻に変わる (核が小さく暗くなる)。終わりに段階 3 の模型に替わる
    A["roar_2"] = {"animation_length": 3.0, "bones": {
        "heart": {"scale": track([(0, ONE), (0.4, S(1.12)), (0.8, S(0.95)), (1.2, S(1.14)), (1.8, ONE), (3.0, S(0.94))], "scale")},
        "core_eye": {"scale": track([(0, ONE), (0.6, S(1.8)), (1.4, S(1.8)), (2.4, S(0.5)), (3.0, S(0.5))], "scale")},
        "crust": {"rotation": track(tremble([0.3, 0.6, 0.9, 1.2], 3) + [(1.4, Z3), (3.0, Z3)], "rotation")},
        "drips": {"scale": track([(0, ONE), (1.4, [1, 1.3, 1]), (2.4, [1, 0.6, 1]), (3.0, [1, 0.6, 1])], "scale")},
        "cinders": {"position": track([(0, Z3), (2.0, [0, 6, 0]), (3.0, [0, 6, 0])], "position")},
    }}

    # ---- 段階 1 ----
    # 火の息 (1.2 秒の溜め): 核がふくらみ、心臓が後ろへ引いてから前へ押し出す
    A["breath"] = {"animation_length": 2.0, "bones": {
        "core_eye": {"scale": track([(0, ONE), (1.2, S(1.7)), (1.35, S(0.9)), (2.0, ONE)], "scale")},
        "core_ring": {"scale": track([(0, ONE), (1.2, S(1.3)), (1.35, ONE), (2.0, ONE)], "scale")},
        "heart": {"position": track([(0, Z3), (1.15, [0, 0, -1.5]), (1.3, [0, 0, 2.0]), (2.0, Z3)], "position")},
        "crust": {"position": track([(0, Z3), (1.15, [0, 0, -0.8]), (1.3, [0, 0, 1.0]), (2.0, Z3)], "position")},
        "crust_glow": {"scale": track([(0, ONE), (1.2, S(1.06)), (1.35, ONE), (2.0, ONE)], "scale")},
    }}
    # 熱の脈 (1.0 秒): 一度強く光り、膨らんで床を打つ
    A["pulse"] = {"animation_length": 1.6, "bones": {
        "heart": {"scale": track([(0, ONE), (0.5, S(1.08)), (0.7, S(0.97)), (1.0, S(1.14)), (1.3, ONE), (1.6, ONE)], "scale"),
                  "position": track([(0, Z3), (0.9, [0, 1.2, 0]), (1.0, [0, -1.5, 0]), (1.3, Z3), (1.6, Z3)], "position")},
        "heart_vein": {"scale": track([(0, ONE), (0.5, S(1.15)), (0.7, ONE), (1.0, S(1.25)), (1.3, ONE), (1.6, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.0, S(1.9)), (1.3, ONE), (1.6, ONE)], "scale")},
        "crust_glow": {"scale": track([(0, ONE), (1.0, S(1.08)), (1.3, ONE), (1.6, ONE)], "scale")},
    }}
    # 熔け玉 (1.5 秒): 核の輪がふくらみ、心臓が持ち上がって火の粉を吹き上げる
    A["orb"] = {"animation_length": 2.4, "bones": {
        "core_ring": {"scale": track([(0, ONE), (1.5, S(1.5)), (1.6, S(0.8)), (2.4, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.4)), (1.6, S(0.8)), (2.4, ONE)], "scale")},
        "heart": {"position": track([(0, Z3), (1.5, [0, 2.0, 0]), (1.6, [0, -0.5, 0]), (2.4, Z3)], "position")},
        "cinders": {"position": track([(0, Z3), (1.5, [0, 4.0, 0]), (2.4, Z3)], "position")},
    }}

    # ---- 段階 2 ----
    # 崩れる床 (2.0 秒): 心臓が震え、熔岩の垂れが伸びる (床が割れる)
    A["floor"] = {"animation_length": 2.8, "bones": {
        "heart": {"rotation": track(tremble([0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0], 3, 1) + [(2.2, Z3), (2.8, Z3)], "rotation")},
        "drips": {"scale": track([(0, ONE), (2.0, [1.1, 1.4, 1.1]), (2.8, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (2.0, S(1.5)), (2.8, ONE)], "scale")},
        "crust_glow": {"scale": track([(0, ONE), (2.0, S(1.06)), (2.8, ONE)], "scale")},
    }}
    # 鞴の嵐 (1.5 秒の溜め、2 秒引く): 吸い込むように縮み、終わりに弾ける
    A["gale"] = {"animation_length": 3.8, "bones": {
        "heart": {"scale": track([(0, ONE), (1.5, S(0.92)), (3.4, S(0.9)), (3.5, S(1.18)), (3.8, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.4)), (3.4, S(1.6)), (3.5, S(0.9)), (3.8, ONE)], "scale")},
        "cinders": {"position": track([(0, Z3), (1.5, [0, -1.0, 0]), (3.4, [0, -2.0, 0]), (3.5, [0, 6.0, 0]), (3.8, Z3)], "position")},
        "drips": {"scale": track([(0, ONE), (1.5, [0.8, 1.2, 0.8]), (3.5, [1.3, 0.8, 1.3]), (3.8, ONE)], "scale")},
    }}
    # 灰の礫 (1.0 秒): 心臓が黒く脈打ち、礫を吐く
    A["pellet"] = {"animation_length": 1.6, "bones": {
        "heart": {"scale": track([(0, ONE), (0.5, S(1.08)), (0.7, S(0.97)), (1.0, S(1.12)), (1.3, ONE), (1.6, ONE)], "scale")},
        "heart_vein": {"scale": track([(0, ONE), (0.5, S(1.15)), (0.7, ONE), (1.0, S(1.2)), (1.3, ONE), (1.6, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.0, S(1.8)), (1.3, ONE), (1.6, ONE)], "scale")},
        "drops": {"position": track([(0, Z3), (1.0, [0, 2.0, 0]), (1.6, Z3)], "position")},
    }}

    # ---- 段階 3 ----
    # 冷え (段階 3 に入る瞬間): 核がしぼんで暗くなり、灰の殻が縮む
    A["chill"] = {"animation_length": 1.2, "bones": {
        "core_eye": {"scale": track([(0, ONE), (0.5, S(0.45)), (1.2, S(0.8))], "scale")},
        "heart": {"scale": track([(0, ONE), (0.5, S(0.93)), (1.2, ONE)], "scale")},
        "ash_shell": {"scale": track([(0, ONE), (0.5, S(0.97)), (1.2, ONE)], "scale")},
    }}
    # 残り火 (1.0 秒): 核が一度明るくなり、火の粉が飛ぶ
    A["embers"] = {"animation_length": 1.6, "bones": {
        "core_eye": {"scale": track([(0, ONE), (1.0, S(1.9)), (1.3, ONE), (1.6, ONE)], "scale")},
        "core_ring": {"scale": track([(0, ONE), (1.0, S(1.4)), (1.3, ONE), (1.6, ONE)], "scale")},
        "cinders": {"position": track([(0, Z3), (1.0, [0, 3.0, 0]), (1.6, Z3)], "position"),
                    "scale": track([(0, ONE), (1.0, S(1.5)), (1.6, ONE)], "scale")},
        "heart": {"scale": track([(0, ONE), (1.0, S(1.05)), (1.3, ONE), (1.6, ONE)], "scale")},
    }}
    # 消えかけの鼓動 (2.0 秒): 核が暗くなり (縮む)、低い一拍で一度だけ大きく打つ
    A["fading"] = {"animation_length": 2.8, "bones": {
        "core_eye": {"scale": track([(0, ONE), (1.6, S(0.3)), (2.0, S(1.6)), (2.4, S(0.7)), (2.8, ONE)], "scale")},
        "core_ring": {"scale": track([(0, ONE), (1.6, S(0.6)), (2.0, S(1.3)), (2.8, ONE)], "scale")},
        "heart": {"scale": track([(0, ONE), (1.6, S(0.88)), (2.0, S(1.14)), (2.4, S(0.98)), (2.8, ONE)], "scale")},
        "ash_shell": {"scale": track([(0, ONE), (1.6, S(0.97)), (2.0, S(1.06)), (2.8, ONE)], "scale")},
    }}
    # 灰の帳 (1.5 秒): 灰の殻が震えて灰を撒く
    A["veil"] = {"animation_length": 2.4, "bones": {
        "ash_shell": {"rotation": track(tremble([0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4], 3) + [(1.6, Z3), (2.4, Z3)], "rotation")},
        "ash_drips": {"scale": track([(0, ONE), (1.5, [1.2, 1.5, 1.2]), (2.4, ONE)], "scale")},
        "cinders": {"position": track([(0, Z3), (1.5, [0, 2.0, 0]), (2.4, Z3)], "position")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.3)), (2.4, ONE)], "scale")},
    }}
    return {"format_version": "1.8.0",
            "animations": {f"animation.{NAME}.{k}": v for k, v in A.items()}}


def ensure_bones(geo, names):
    g = geo["minecraft:geometry"][0]
    have = {b["name"] for b in g["bones"]}
    added = []
    for n in names:
        if n not in have:
            g["bones"].append({"name": n, "pivot": list(PIVOT), "cubes": []})
            added.append(n)
    return added


# ---- ゲームの中の色 (13 の 21章以降の「心臓の実装の記録」。潮殻 08 の 19.8節の直し方) ----
# 殻 (彩りの薄い画素) を、明るさの幅を持ち上げた段階ごとの色へ寄せる。
#   段階 1・2: 暗い鉱滓 (玄武岩) を 52〜108 に。ラフの 34 前後だと、面の陰で黒く沈む
#   段階 3: 灰の白を、そのまま少しだけ持ち上げる (中央 104 → 130)
SHELL = {1: ((58, 52, 56), 52, 108),   # (基の色, 明るさの下限, 上限)
         2: ((66, 56, 54), 58, 116),
         3: ((176, 168, 160), 96, 232)}
# 光る画素: 基のテクスチャの色 (殻と同じく陰を受ける)、発光の絵の色 (足す光)。段階 3 は火が消えかけで画素が少ない
GLOW = {1: ((255, 138, 40), (150, 70, 18)),     # 割れ目と核: 熾の橙 + 足す橙
        2: ((255, 214, 96), (150, 120, 40)),    # 白熱: 熔けた黄 + 足す黄
        3: ((200, 52, 40), (118, 22, 14))}      # 残り火: くすんだ赤 + 足す赤


def recolor(tex, glow, ph):
    t = tex.astype(float)
    a = tex[..., 3] > 0
    lit = glow[..., 3] > 0
    rgb = t[..., :3]
    sat = rgb.max(-1) - rgb.min(-1)
    # 殻: 光らない画素。彩りの強いもの (煉瓦色・錆) はそのまま残し、くすんだ画素だけ寄せる
    shell = a & ~lit & (sat <= 60)
    L = rgb.mean(-1)
    lo, hi = np.percentile(L[shell], [3, 97])
    base, kmin, kmax = SHELL[ph]
    k = np.clip((L - lo) / max(hi - lo, 1), 0, 1)
    target = kmin + (kmax - kmin) * k
    v, u = np.mgrid[0:tex.shape[0], 0:tex.shape[1]]
    w = 0.5 + 0.5 * np.sin(u * 0.37 + v * 0.61) * np.cos(u * 0.17 - v * 0.23)
    tint = (1 - w)[..., None] * np.array([1.02, 0.99, 0.97]) + w[..., None] * np.array([0.98, 1.0, 1.03])
    out = np.array(base, float) / max(np.mean(base), 1) * target[..., None] * tint
    t[shell, :3] = out[shell]
    # 殻に近いが彩りが少しある画素 (薄い錆) も、明るさだけ持ち上げる
    warm = a & ~lit & ~shell
    if warm.any():
        Lw = np.maximum(rgb[warm].mean(-1), 1)
        t[warm, :3] = np.clip(rgb[warm] * (np.maximum(Lw, kmin * 0.9) / Lw)[:, None], 0, 255)
    g = glow.astype(float)
    g[..., :3] = 0
    bc, add = GLOW[ph]
    # 光る画素: 元の色の濃淡 (橙の中の黄など) は残し、基の色と足す光を段階の色に寄せる
    hot = rgb[lit]
    rel = hot / np.maximum(hot.max(-1, keepdims=True), 1)
    t[lit, :3] = np.clip(np.array(bc, float) * (0.75 + 0.25 * rel.mean(-1, keepdims=True)) * 1.0, 0, 255)
    g[lit, :3] = np.array(add, float)
    return np.clip(t, 0, 255).astype(np.uint8), np.clip(g, 0, 255).astype(np.uint8)


def load_png(p):
    return np.array(Image.open(p).convert("RGBA"))


def main():
    anim = anims()
    used = sorted({b for a in anim["animations"].values() for b in a["bones"]})
    out_geo = {}
    rough = {}
    for ph in (1, 2, 3):
        geo = json.loads((SRC / f"phase{ph}.geo.json").read_text(encoding="utf-8"))
        tex = load_png(SRC / f"phase{ph}.tex.png")
        glow = load_png(SRC / f"phase{ph}.glow.png")
        rough[ph] = (geo, tex, glow)
        g2, t2, gl2 = H.convert_geo(geo, tex, glow)
        t2, gl2 = recolor(t2, gl2, ph)
        g2["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.skyabyss.{NAME}"
        added = ensure_bones(g2, used)
        lit = int((gl2[..., 3] > 0).sum())
        assert lit >= 1, "発光マスクに光る画素がない (GeckoLib が落ちる)"
        (ASSET / f"geo/entity/{NAME}_p{ph}.geo.json").write_text(json.dumps(g2, ensure_ascii=False, indent=1), encoding="utf-8")
        Image.fromarray(t2).save(ASSET / f"textures/entity/{NAME}_p{ph}.png")
        Image.fromarray(gl2).save(ASSET / f"textures/entity/{NAME}_p{ph}_glowmask.png")
        out_geo[ph] = (g2, t2, gl2)
        ncubes = sum(len(b.get("cubes", [])) for b in g2["minecraft:geometry"][0]["bones"])
        shell_l = t2[(t2[..., 3] > 0) & ~(gl2[..., 3] > 0)][:, :3].mean()
        print(f"phase {ph}: {ncubes} cubes, glow px {lit}, shell mean brightness {shell_l:.0f}, empty bones added: {added}")
    (ASSET / f"animations/entity/{NAME}.animation.json").write_text(json.dumps(anim, ensure_ascii=False, indent=1), encoding="utf-8")

    size = 320
    cols = []
    diffs = []
    for ph in (1, 2, 3):
        geo, tex, glow = rough[ph]
        yaw, pitch = 25, 15
        a = render(load_geo(geo, tex, glow), yaw, pitch, (size, size))
        g2, t2, gl2 = out_geo[ph]
        b = render(H.gecko_faces(g2, t2, gl2[..., 3] > 0), yaw, pitch, (size, size))
        b2 = render(H.gecko_faces(g2, t2, gl2[..., 3] > 0), yaw + 180, -30, (size, size))
        diffs.append(np.abs(np.asarray(a, float) - np.asarray(b, float)).mean())
        cols.append((f"第{ph}段階", [a, b, b2]))
    print("rough vs game-read mean abs diff:", [round(x, 3) for x in diffs])
    poses = [(1, "roar_1", 2.0), (1, "breath", 1.2), (1, "pulse", 1.0), (2, "roar_2", 2.2), (2, "floor", 2.0),
             (2, "gale", 3.5), (3, "idle", 1.2), (3, "fading", 2.0), (3, "embers", 1.0)]
    cols2 = []
    for ph, name, t in poses:
        g2, t2, gl2 = out_geo[ph]
        p = H.sample(anim["animations"][f"animation.{NAME}.{name}"], t)
        im = render(H.gecko_faces(g2, t2, gl2[..., 3] > 0, p), 25, 15, (size, size))
        cols2.append((f"{name} {t}s", [im]))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    s1 = sheet("余燼の心臓 B: 上 ラフ / 中 ゲームの読み方 / 下 下から", cols)
    s2 = sheet("アニメーションの要所 (ゲームの読み方)", cols2)
    W = max(s1.width, s2.width)
    out = Image.new("RGB", (W, s1.height + s2.height), (20, 20, 26))
    out.paste(s1, (0, 0))
    out.paste(s2, (0, s1.height))
    out.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
