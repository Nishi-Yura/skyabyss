"""潮殻の心臓 (潮殻の巨神の最終ボス) のラフ 案B 巻貝 をゲームに入れる。

使い方 (リポジトリ直下で): python tools/install_tide_heart.py
形の元: tools/sources/tide_heart/phaseN.geo.json, phaseN.tex.png, phaseN.glow.png
        (tools/rough/tide_heart.py の B 案。比較の絵は docs/review/tide_shell_roughs/heart/b)
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/tide_heart_p1|p2|p3.geo.json
  textures/entity/tide_heart_p1|p2|p3.png と _glowmask.png
  animations/entity/tide_heart.animation.json
確認用: docs/review/previews/tide_heart.png (GeckoLib の読み方を真似て描いた絵と、ラフの絵を並べる)

向きの変換は岩鯨の心臓 (tools/install_heart_v4.py) と同じ。ラフは正面 +Z で描いてあるので、
Z を反転した geo を書けば、ゲームでは正面が前を向き、鏡に映らない。アニメーションもラフの空間で書き、同じ変換をかける。

段階は段階ごとの geo で切り替える (骨の表示の切り替えより、箱の数と光る面を段階ごとに素直に持てる)。
段階 3 は箱が少なく疎なので、水滴の輪 (water_drop) と殻の欠片 (orbit) を逆向きに回し、上下に揺らす。
光の粒はゲームの側 (TideHeart.tick) で足す。
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

SRC = ROOT / "tools/sources/tide_heart"
ASSET = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/tide_heart.png"
NAME = "tide_heart"
PIVOT = [0.0, 16.0, 0.0]

track = H.track
Z3 = [0, 0, 0]
ONE = [1, 1, 1]


def S(s):
    return [s, s, s]


def beat(t0, p1, p2, length):
    return [(0, ONE), (t0 + 0.2, S(p1)), (t0 + 0.45, ONE), (t0 + 0.6, S(p2)), (t0 + 1.0, ONE), (length, ONE)]


def tremble(times, amp, axis=2):
    keys = [(0, Z3)]
    for n, t in enumerate(times):
        v = [0, 0, 0]
        v[axis] = amp * (1 if n % 2 == 0 else -1)
        keys.append((t, v))
    return keys


def spin(length, turns, axis=1, sign=1, steps=8):
    """一定の速さで回す (線形補間なので、四分の一回りより細かくキーを打つ)"""
    keys = []
    for k in range(steps + 1):
        v = [0, 0, 0]
        v[axis] = sign * 360.0 * turns * k / steps
        keys.append((length * k / steps, v))
    return keys


def anims():
    A = {}
    L = 4.8
    beats = [(0, ONE)]
    for t0 in (0, 2.4):
        beats += [(t0 + 0.2, S(1.06)), (t0 + 0.45, ONE), (t0 + 0.6, S(1.04)), (t0 + 1.0, ONE)]
    beats.append((L, ONE))
    eye = [(0, ONE), (0.2, S(1.15)), (0.5, ONE), (2.6, S(1.15)), (2.9, ONE), (L, ONE)]
    A["idle"] = {"loop": True, "animation_length": L, "bones": {
        "heart": {"scale": track(beats, "scale")},
        "heart_vein": {"scale": track(beats, "scale")},
        "vessel": {"rotation": track([(0, Z3), (1.2, [4, 0, 0]), (2.4, Z3), (3.6, [-3, 0, 0]), (L, Z3)], "rotation")},
        "core_eye": {"scale": track(eye, "scale")},
        "core_ring": {"scale": track(beats, "scale")},
        # 殻はゆっくり揺れる (第1・第2段階)。第3段階の内側の螺旋は、ゆっくり一回りする
        "spiral": {"rotation": track([(0, [0, 0, -3]), (2.4, [0, 0, 3]), (L, [0, 0, -3])], "rotation")},
        "aperture_lip": {"rotation": track([(0, [0, 0, -3]), (2.4, [0, 0, 3]), (L, [0, 0, -3])], "rotation")},
        "barnacle": {"rotation": track([(0, [0, 0, -3]), (2.4, [0, 0, 3]), (L, [0, 0, -3])], "rotation")},
        "frond": {"rotation": track([(0, [0, 0, -3]), (1.2, [4, 0, 2]), (2.4, [0, 0, 3]), (3.6, [-4, 0, 0]), (L, [0, 0, -3])], "rotation")},
        "chamber": {"scale": track([(0, ONE), (0.2, [1.04, 1.04, 1]), (0.5, ONE), (2.6, [1.04, 1.04, 1]), (2.9, ONE), (L, ONE)], "scale")},
        "shard": {"position": track([(0, Z3), (1.2, [0, 0.8, 0]), (2.4, Z3), (3.6, [0, -0.8, 0]), (L, Z3)], "position"),
                  "rotation": track([(0, Z3), (2.4, [0, 0, 6]), (L, Z3)], "rotation")},
        # 水滴の輪は一回り 4.8 秒、殻の欠片は逆向きに一回り 9.6 秒 (ループの長さで切れ目なし: 半回りを 2 つ)
        "water_drop": {"rotation": track(spin(L, 1, 1, 1), "rotation"),
                       "position": track([(0, Z3), (1.2, [0, 0.7, 0]), (2.4, Z3), (3.6, [0, -0.7, 0]), (L, Z3)], "position")},
        "orbit": {"rotation": track(spin(L, 1, 1, -1), "rotation"),
                  "position": track([(0, [0, -0.5, 0]), (2.4, [0, 0.5, 0]), (L, [0, -0.5, 0])], "position")},
        "moon_crescent": {"position": track([(0, Z3), (2.4, [0, 0.6, 0]), (L, Z3)], "position")},
    }}
    A["walk"] = A["idle"]

    # ---- 咆哮 (段階の変わり目、3 秒) ----
    # 段階1→2: 殻が震えて外の巻が破れる。口の縁・フジツボ・触手が外へ飛んで消え、終わりに段階2の模型に替わる
    bones = {
        "heart": {"scale": track([(0, ONE), (0.4, S(1.14)), (0.8, S(0.95)), (1.2, S(1.16)), (1.8, ONE), (3.0, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (0.8, S(1.6)), (2.0, S(1.6)), (3.0, ONE)], "scale")},
        "spiral": {"rotation": track(tremble([0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4], 4) + [(1.6, Z3), (3.0, Z3)], "rotation"),
                   "scale": track([(0, ONE), (1.4, ONE), (1.6, S(1.08)), (2.2, S(0.94)), (3.0, S(0.94))], "scale")},
    }
    for name, d in (("aperture_lip", [10, -2, 4]), ("barnacle", [0, 3, 6]), ("frond", [8, -10, 4])):
        bones[name] = {"position": track([(0, Z3), (1.5, Z3), (2.4, d), (3.0, d)], "position"),
                       "scale": track([(0, ONE), (1.5, ONE), (2.4, S(0.01)), (3.0, S(0.01))], "scale"),
                       "rotation": track([(0, Z3), (1.5, Z3), (2.4, [30, 0, 40]), (3.0, [30, 0, 40])], "rotation")}
    A["roar_1"] = {"animation_length": 3.0, "bones": bones}
    # 段階2→3: 光る部屋が強く光り、外の殻の欠片が四方へ飛ぶ。螺旋が縮んで、内側だけが残る
    bones = {
        "heart": {"scale": track([(0, ONE), (0.4, S(1.12)), (0.8, S(0.95)), (1.2, S(1.14)), (1.8, ONE), (3.0, ONE)], "scale")},
        "chamber": {"scale": track([(0, ONE), (0.6, [1.12, 1.12, 1]), (1.0, ONE), (1.4, [1.12, 1.12, 1]), (2.2, S(0.7)), (3.0, S(0.7))], "scale")},
        "spiral": {"rotation": track(tremble([0.3, 0.6, 0.9, 1.2], 5) + [(1.4, Z3), (3.0, [0, 0, -40])], "rotation"),
                   "scale": track([(0, ONE), (1.4, ONE), (2.6, S(0.8)), (3.0, S(0.8))], "scale")},
        "shard": {"position": track([(0, Z3), (1.2, Z3), (2.2, [0, 6, 0]), (3.0, [0, 6, 0])], "position"),
                  "scale": track([(0, ONE), (1.4, S(1.2)), (2.4, S(0.01)), (3.0, S(0.01))], "scale")},
    }
    A["roar_2"] = {"animation_length": 3.0, "bones": bones}

    # ---- 段階1 ----
    # 潮の吐息 (1.2 秒の溜め): 核がふくらみ、心臓が後ろへ引いてから前へ押し出す
    A["breath"] = {"animation_length": 2.0, "bones": {
        "core_eye": {"scale": track([(0, ONE), (1.2, S(1.7)), (1.35, S(0.9)), (2.0, ONE)], "scale")},
        "core_ring": {"scale": track([(0, ONE), (1.2, S(1.3)), (1.35, ONE), (2.0, ONE)], "scale")},
        "heart": {"position": track([(0, Z3), (1.15, [0, 0, -1.5]), (1.3, [0, 0, 2.0]), (2.0, Z3)], "position")},
        "spiral": {"position": track([(0, Z3), (1.15, [0, 0, -0.8]), (1.3, [0, 0, 1.0]), (2.0, Z3)], "position")},
    }}
    # 殻打ち (1.0 秒): 殻が開きかけて震え、閉じて床を打つ
    shell = {"rotation": track(tremble([0.15, 0.3, 0.45, 0.6, 0.75, 0.9], 5) + [(1.0, Z3), (2.0, Z3)], "rotation"),
             "scale": track([(0, ONE), (0.9, S(1.1)), (1.0, S(0.96)), (1.3, ONE), (2.0, ONE)], "scale")}
    A["shell_strike"] = {"animation_length": 2.0, "bones": {
        "spiral": shell, "aperture_lip": shell, "barnacle": shell,
        "heart": {"position": track([(0, Z3), (0.9, [0, 1.2, 0]), (1.0, [0, -1.5, 0]), (1.4, Z3), (2.0, Z3)], "position")},
    }}
    # 渦潮 (1.5 秒の溜め、2 秒引く): 殻がまわりはじめ、引く間は速く二回り
    sp = [(0, Z3), (1.5, [0, 0, 90]), (2.5, [0, 0, 360]), (3.5, [0, 0, 720])]
    A["whirl"] = {"animation_length": 3.6, "bones": {
        "spiral": {"rotation": track(sp, "rotation")},
        "aperture_lip": {"rotation": track(sp, "rotation")},
        "barnacle": {"rotation": track(sp, "rotation")},
        "frond": {"rotation": track(sp, "rotation")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.4)), (3.5, S(1.4)), (3.6, ONE)], "scale")},
    }}
    # ---- 段階2 ----
    # 満ち潮 (1.5 秒の溜め): 光る部屋が満ちるように明るく、心臓が持ち上がる
    A["flood"] = {"animation_length": 2.4, "bones": {
        "chamber": {"scale": track([(0, ONE), (1.5, [1.15, 1.15, 1]), (2.4, ONE)], "scale")},
        "heart": {"position": track([(0, Z3), (1.5, [0, 2.0, 0]), (2.4, Z3)], "position")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.5)), (2.4, ONE)], "scale")},
        "shard": {"position": track([(0, Z3), (1.5, [0, 2.5, 0]), (2.4, Z3)], "position")},
    }}
    # 泡の牢 (1.5 秒): 核の輪がふくらみ、はじける
    A["bubble"] = {"animation_length": 2.0, "bones": {
        "core_ring": {"scale": track([(0, ONE), (1.5, S(1.5)), (1.6, S(0.8)), (2.0, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.3)), (1.6, S(0.8)), (2.0, ONE)], "scale")},
        "heart": {"rotation": track([(0, Z3), (0.8, [0, 8, 0]), (1.5, [0, -8, 0]), (2.0, Z3)], "rotation")},
    }}
    # 真珠の脈 (1.0 秒): 心臓が白く打ち、血管がふくらむ
    A["pearl"] = {"animation_length": 1.6, "bones": {
        "heart": {"scale": track([(0, ONE), (0.5, S(1.08)), (0.7, S(0.97)), (1.0, S(1.12)), (1.3, ONE), (1.6, ONE)], "scale")},
        "heart_vein": {"scale": track([(0, ONE), (0.5, S(1.15)), (0.7, ONE), (1.0, S(1.2)), (1.3, ONE), (1.6, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.0, S(1.8)), (1.3, ONE), (1.6, ONE)], "scale")},
    }}
    # ---- 段階3 ----
    # 引き潮 (2 秒の溜め、3 秒引く): 水滴の輪が心臓へ縮み、欠片が速く回る。終わりに輪がはじける
    ebb_sc = [(0, ONE), (2.0, S(0.55)), (4.9, S(0.4)), (5.0, S(1.35)), (5.4, ONE)]
    A["ebb"] = {"animation_length": 5.4, "bones": {
        "water_drop": {"scale": track(ebb_sc, "scale"), "rotation": track(spin(5.4, 3, 1, 1, 24), "rotation")},
        "orbit": {"scale": track([(0, ONE), (2.0, S(0.8)), (4.9, S(0.7)), (5.0, S(1.2)), (5.4, ONE)], "scale"),
                  "rotation": track(spin(5.4, 2, 1, -1, 16), "rotation")},
        "core_eye": {"scale": track([(0, ONE), (2.0, S(1.5)), (5.0, S(1.5)), (5.4, ONE)], "scale")},
    }}
    # 月潮の大波 (2 秒の溜め): 三日月が持ち上がって光り、螺旋が波の来る側へ傾く
    A["moon_wave"] = {"animation_length": 3.4, "bones": {
        "moon_crescent": {"position": track([(0, Z3), (2.0, [0, 4.0, 0]), (2.4, [0, -1.0, 0]), (3.4, Z3)], "position"),
                          "scale": track([(0, ONE), (2.0, S(1.3)), (2.4, ONE), (3.4, ONE)], "scale")},
        "spiral": {"rotation": track([(0, Z3), (2.0, [0, 0, 25]), (2.6, [0, 0, -10]), (3.4, Z3)], "rotation")},
        "chamber": {"rotation": track([(0, Z3), (2.0, [0, 0, 25]), (2.6, [0, 0, -10]), (3.4, Z3)], "rotation")},
    }}
    # 真珠の雨 (1.5 秒): 欠片の輪が外へ広がり、落ちる
    A["pearl_rain"] = {"animation_length": 2.0, "bones": {
        "orbit": {"scale": track([(0, ONE), (1.5, S(1.35)), (1.7, S(0.9)), (2.0, ONE)], "scale"),
                  "position": track([(0, Z3), (1.5, [0, 3, 0]), (1.7, [0, -1, 0]), (2.0, Z3)], "position")},
        "water_drop": {"scale": track([(0, ONE), (1.5, [1.3, 1, 1.3]), (2.0, ONE)], "scale")},
        "core_eye": {"scale": track([(0, ONE), (1.5, S(1.5)), (2.0, ONE)], "scale")},
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
        g2["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.skyabyss.{NAME}"
        added = ensure_bones(g2, used)
        lit = int((gl2[..., 3] > 0).sum())
        assert lit >= 1, "発光マスクに光る画素がない (GeckoLib が落ちる)"
        (ASSET / f"geo/entity/{NAME}_p{ph}.geo.json").write_text(json.dumps(g2, ensure_ascii=False, indent=1), encoding="utf-8")
        Image.fromarray(t2).save(ASSET / f"textures/entity/{NAME}_p{ph}.png")
        Image.fromarray(gl2).save(ASSET / f"textures/entity/{NAME}_p{ph}_glowmask.png")
        out_geo[ph] = (g2, t2, gl2)
        ncubes = sum(len(b.get("cubes", [])) for b in g2["minecraft:geometry"][0]["bones"])
        print(f"phase {ph}: {ncubes} cubes, glow px {lit}, empty bones added: {added}")
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
    poses = [(1, "roar_1", 2.0), (1, "whirl", 2.0), (1, "shell_strike", 0.95), (2, "roar_2", 2.2), (2, "flood", 1.5),
             (3, "idle", 1.2), (3, "ebb", 3.0), (3, "moon_wave", 2.0), (3, "pearl_rain", 1.5)]
    cols2 = []
    for ph, name, t in poses:
        g2, t2, gl2 = out_geo[ph]
        p = H.sample(anim["animations"][f"animation.{NAME}.{name}"], t)
        im = render(H.gecko_faces(g2, t2, gl2[..., 3] > 0, p), 25, 15, (size, size))
        cols2.append((f"{name} {t}s", [im]))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    s1 = sheet("潮殻の心臓 B: 上 ラフ / 中 ゲームの読み方 / 下 下から", cols)
    s2 = sheet("アニメーションの要所 (ゲームの読み方)", cols2)
    W = max(s1.width, s2.width)
    out = Image.new("RGB", (W, s1.height + s2.height), (20, 20, 26))
    out.paste(s1, (0, 0))
    out.paste(s2, (0, s1.height))
    out.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
