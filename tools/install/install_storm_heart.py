"""雷翼の心臓 (第三の巨神「雷翼の巨神」の最終ボス) のラフをゲームに入れる。推奨は C 翼の心臓 (12 の 14.2節)。

使い方 (リポジトリ直下で): python tools/install/install_storm_heart.py [--option a|b|c]
形の元: --option c (既定) は tools/sources/storm_heart_hp/phaseN.geo.json, phaseN.tex.png, phaseN.glow.png
        (0.25 で作り直した高ポリ版。tools/rough/hp_heart_storm.py の C 案、比較の絵は docs/review/high_poly_roughs/storm_heart_hp.png。
        元の 0.5 の形は tools/sources/storm_heart/ に残してある: tools/rough/thunder_heart.py の C 案)
        a / b はまだ選ばれていない案のデータ (docs/review/thunder_wing_roughs/heart/<a|b>/) を読む。
        ただしアニメーションの骨は C の骨 (翼・冠の翼・網) で書いてあるので、a / b に差し替えるときは anims() を直す
出力 (src/main/resources/assets/skyabyss/ 以下):
  geo/entity/storm_heart_p1|p2|p3.geo.json
  textures/entity/storm_heart_p1|p2|p3.png と _glowmask.png
  animations/entity/storm_heart.animation.json
確認用: docs/review/previews/storm_heart.png (GeckoLib の読み方を真似て描いた絵と、ラフの絵を並べる。git の外)

向きの変換は岩鯨・潮殻の心臓 (tools/install/install_heart_v4.py) と同じ。ラフは正面 +Z で描いてあるので、Z を反転した geo を書けば、
ゲームでは正面が前を向き、鏡に映らない (GeckoLib は X を反転して焼き、骨とキーフレームの回転の X と Y の符号を反転する。
回転の順は Z → Y → X)。アニメーションもラフの空間で書き、同じ変換 (H.track) をかける。

段階の切り替えは段階ごとの geo (潮殻と同じ)。

色 (潮殻の 08 の 19.8節の直し方を最初から使う):
- 描画は心臓の光をブロックの光 15 で描き (StormHeartRenderer.getBlockLightLevel)、光る画素は基の色の上に発光の絵の色を
  「足す」(StormHeartRenderer.GlowLayer、RenderType.eyes)。GeckoLib の AutoGlowingGeoLayer は光る画素を基から抜き、
  面の陰を受けるので明るい面と同じ色に沈む。ここで書く _glowmask は「足す光の色」(光らない画素は黒・透明)。
- ラフのテクスチャは面の向きの陰を焼き込んでいて暗い (平均 107)。ゲームではそこへ実体の陰がもう一度掛かるので、
  光らない画素をガンマ 0.8 で持ち上げる (灰青の翼が黒い板に見えないように)。銅と白い骨の色の向きは変えない。
- 段階 3 の稲妻の網は光りすぎて骨が見えにくい (14.2節の弱み) ので、網の骨の足す光は核より弱くし、アニメで点滅させる。
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
sys.path.insert(0, str(ROOT / "tools/rough"))
import install_heart_v4 as H  # noqa: E402  (convert_geo, gecko_faces, track, sample を使う)
from voxel import check_overlaps, load_geo, render, sheet  # noqa: E402

ASSET = ROOT / "src/main/resources/assets/skyabyss"
PREVIEW = ROOT / "docs/review/previews/storm_heart.png"
NAME = "storm_heart"
PIVOT = [0.0, 18.0, 0.0]

track = H.track
Z3 = [0, 0, 0]
ONE = [1, 1, 1]


def S(s):
    return [s, s, s]


def source(option):
    if option == "c":
        return ROOT / "tools/sources/storm_heart_hp"
    return ROOT / f"docs/review/thunder_wing_roughs/heart/{option}"


def tremble(times, amp, axis=2, base=None):
    base = base or [0, 0, 0]
    keys = [(0, list(base))]
    for n, t in enumerate(times):
        v = list(base)
        v[axis] += amp * (1 if n % 2 == 0 else -1)
        keys.append((t, v))
    return keys


def wing(side, keys):
    """左右の翼の回転 (ラフの空間)。side=+1 は左の翼 (-X)。値は左の翼のもの: z が負で翼の先が上がる、y が正で前へたたむ。
    右の翼には y と z の符号を反転して掛ける (鏡)"""
    out = []
    for t, (rx, ry, rz) in keys:
        out.append((t, [rx, ry * side, rz * side]))
    return track(out, "rotation")


def both_wings(keys, names=("wing_l", "wing_r")):
    return {names[0]: {"rotation": wing(1, keys)}, names[1]: {"rotation": wing(-1, keys)}}


def beat_scale(L, peaks=(1.07, 1.04), starts=(0.0,)):
    k = [(0, ONE)]
    for t0 in starts:
        k += [(t0 + 0.18, S(peaks[0])), (t0 + 0.42, ONE), (t0 + 0.56, S(peaks[1])), (t0 + 0.9, ONE)]
    k.append((L, ONE))
    return track(k, "scale")


def anims():
    A = {}
    L = 4.0
    # ---- 待機: 二拍の鼓動。翼はゆっくり呼吸 (段階 1 は包んだまま、段階 2 は開いて羽ばたく、段階 3 は骨が震える)。
    # 冠の翼は揺れ、網はちらつき、抜けた羽はゆっくり沈んで戻る
    idle = {
        "heart": {"scale": beat_scale(L, starts=(0.0, 2.0))},
        "heart_vein": {"scale": beat_scale(L, (1.12, 1.06), starts=(0.0, 2.0))},
        "core_eye": {"scale": track([(0, ONE), (0.2, S(1.2)), (0.5, ONE), (2.2, S(1.2)), (2.5, ONE), (L, ONE)], "scale")},
        "core": {"scale": track([(0, ONE), (0.2, S(1.06)), (0.5, ONE), (2.2, S(1.06)), (2.5, ONE), (L, ONE)], "scale")},
        "vessel": {"rotation": track([(0, Z3), (1.0, [4, 0, 0]), (2.0, Z3), (3.0, [-3, 0, 0]), (L, Z3)], "rotation")},
        "crest_l": {"rotation": wing(1, [(0, [0, 0, 0]), (1.0, [0, 0, -8]), (2.0, [0, 0, 0]), (3.0, [0, 0, -8]), (L, [0, 0, 0])])},
        "crest_r": {"rotation": wing(-1, [(0, [0, 0, 0]), (1.0, [0, 0, -8]), (2.0, [0, 0, 0]), (3.0, [0, 0, -8]), (L, [0, 0, 0])])},
        "web": {"scale": track([(0, ONE), (0.3, S(1.03)), (0.6, [0.98, 1.0, 1.0]), (1.4, S(1.04)), (1.7, ONE),
                                (2.6, S(1.03)), (3.1, [0.98, 1.0, 1.0]), (L, ONE)], "scale")},
        "shed": {"position": track([(0, Z3), (2.0, [0, -1.2, 0]), (L, Z3)], "position"),
                 "rotation": track([(0, Z3), (2.0, [0, 6, 0]), (L, Z3)], "rotation")},
        "finger": {"rotation": track(tremble([0.5, 1.5, 2.5, 3.5], 1.5, axis=0) + [(L, Z3)], "rotation")},
    }
    idle.update(both_wings([(0, [0, 0, 0]), (1.0, [0, 0, -6]), (2.0, [0, 0, 0]), (3.0, [0, 0, -6]), (L, [0, 0, 0])]))
    A["idle"] = {"loop": True, "animation_length": L, "bones": idle}
    A["walk"] = A["idle"]

    # ---- 咆哮 (段階の変わり目、3 秒) ----
    # 段階 1 → 2: 包んでいた翼が震え、左右へ大きく開いて消える (終わりに段階 2 の模型に替わる)。心臓は上がる
    b = {"heart": {"scale": track([(0, ONE), (0.4, S(1.14)), (0.8, S(0.95)), (1.2, S(1.16)), (1.8, ONE), (3.0, ONE)], "scale")},
         "core_eye": {"scale": track([(0, ONE), (0.8, S(1.7)), (2.2, S(1.7)), (3.0, ONE)], "scale")}}
    b.update(both_wings(tremble([0.15, 0.3, 0.45, 0.6, 0.75, 0.9, 1.05], 4) + [(1.2, Z3), (2.2, [0, -30, -70]), (3.0, [0, -30, -70])]))
    for n in ("wing_l", "wing_r"):
        b[n]["scale"] = track([(0, ONE), (2.2, ONE), (2.8, S(0.2)), (3.0, S(0.2))], "scale")
    A["roar_1"] = {"animation_length": 3.0, "bones": b}
    # 段階 2 → 3: 翼が激しく打ち、羽が抜ける (翼と冠の翼が縮んで消え、骨だけの段階 3 に替わる)
    b = {"heart": {"scale": track([(0, ONE), (0.4, S(1.12)), (0.8, S(0.95)), (1.2, S(1.14)), (1.8, ONE), (3.0, ONE)], "scale")},
         "core_eye": {"scale": track([(0, ONE), (1.0, S(1.8)), (2.4, S(1.8)), (3.0, ONE)], "scale")}}
    flap = [(0, Z3), (0.3, [0, 0, -20]), (0.6, [0, 0, 25]), (0.9, [0, 0, -20]), (1.2, [0, 0, 25]), (1.5, [0, 0, -20]),
            (1.8, [0, 0, 25]), (3.0, [0, 0, 40])]
    b.update(both_wings(flap))
    b.update(both_wings(flap, ("crest_l", "crest_r")))
    for n in ("wing_l", "wing_r", "crest_l", "crest_r"):
        b[n]["scale"] = track([(0, ONE), (1.8, ONE), (2.7, S(0.15)), (3.0, S(0.15))], "scale")
        b[n]["position"] = track([(0, Z3), (1.8, Z3), (2.8, [0, -6, 0]), (3.0, [0, -6, 0])], "position")
    A["roar_2"] = {"animation_length": 3.0, "bones": b}

    # ---- 段階 1 (畳んだ翼) ----
    # 招雷 (1.2 秒): 核が強く光り、翼の内側の骨が少し開いて雷を呼ぶ
    b = {"core_eye": {"scale": track([(0, ONE), (1.1, S(1.8)), (1.25, S(0.9)), (1.6, ONE)], "scale")},
         "heart": {"position": track([(0, Z3), (1.1, [0, 1.2, 0]), (1.3, [0, -0.6, 0]), (1.6, Z3)], "position")}}
    b.update(both_wings([(0, Z3), (1.1, [0, 0, -14]), (1.3, [0, 0, 4]), (1.6, Z3)]))
    A["summon"] = {"animation_length": 1.6, "bones": b}
    # 風の刃 (1.2 秒): 翼を片側へ引き絞り、薙ぐ
    b = {"heart": {"rotation": track([(0, Z3), (1.1, [0, -25, 0]), (1.4, [0, 30, 0]), (1.9, Z3)], "rotation")}}
    b.update({"wing_l": {"rotation": track([(0, Z3), (1.1, [0, -35, 10]), (1.4, [0, 25, -6]), (1.9, Z3)], "rotation")},
              "wing_r": {"rotation": track([(0, Z3), (1.1, [0, -35, -10]), (1.4, [0, 25, 6]), (1.9, Z3)], "rotation")}})
    A["blade"] = {"animation_length": 1.9, "bones": b}
    # 雷の鎖 (1.0 秒): 心臓が後ろへ引き、核から前へ放つ
    b = {"core_eye": {"scale": track([(0, ONE), (0.95, S(1.9)), (1.1, S(0.8)), (1.6, ONE)], "scale")},
         "core": {"scale": track([(0, ONE), (0.95, S(1.15)), (1.1, ONE), (1.6, ONE)], "scale")},
         "heart": {"position": track([(0, Z3), (0.95, [0, 0, -1.6]), (1.1, [0, 0, 2.0]), (1.6, Z3)], "position")}}
    A["chain"] = {"animation_length": 1.6, "bones": b}
    # 翼が裂ける (6 秒): 翼が左右へだらりと落ち、心臓が沈んで弱く打つ
    b = {"heart": {"scale": track([(0, ONE), (0.3, S(0.9)), (1.2, S(1.04)), (2.2, S(0.96)), (3.2, S(1.04)), (4.2, S(0.96)),
                                   (5.4, ONE), (6.0, ONE)], "scale")},
         "core_eye": {"scale": track([(0, ONE), (0.3, S(0.5)), (5.4, S(0.5)), (6.0, ONE)], "scale")}}
    b.update(both_wings([(0, Z3), (0.4, [20, 0, 60]), (5.4, [20, 0, 55]), (6.0, Z3)]))
    A["split"] = {"animation_length": 6.0, "bones": b}

    # ---- 段階 2 (開いた翼) ----
    # 羽ばたき (1.5 秒): 翼を高く上げ、打ち下ろす
    b = {"heart": {"position": track([(0, Z3), (1.5, [0, 1.5, 0]), (1.7, [0, -1.0, 0]), (2.4, Z3)], "position")}}
    b.update(both_wings([(0, Z3), (1.5, [0, 0, -22]), (1.75, [0, 0, 32]), (2.4, Z3)]))
    b.update(both_wings([(0, Z3), (1.5, [0, 0, -18]), (1.75, [0, 0, 22]), (2.4, Z3)], ("crest_l", "crest_r")))
    A["wingbeat"] = {"animation_length": 2.4, "bones": b}
    # 帯電の床 (1.5 秒): 翼の先を下へ向けて震わせる
    b = {"core_eye": {"scale": track([(0, ONE), (1.5, S(1.5)), (1.7, ONE), (2.2, ONE)], "scale")}}
    b.update(both_wings(tremble([0.2, 0.4, 0.6, 0.8, 1.0, 1.2, 1.4], 3, base=[0, 0, 30]) + [(1.6, [0, 0, 30]), (2.2, Z3)]))
    A["charge"] = {"animation_length": 2.2, "bones": b}
    # 羽根の雨 (1.2 秒): 冠の翼を立て、羽を振り落とす
    b = {}
    b.update(both_wings([(0, Z3), (1.0, [0, 0, -30]), (1.2, [0, 0, 10]), (1.6, Z3)], ("crest_l", "crest_r")))
    b.update(both_wings(tremble([0.3, 0.6, 0.9, 1.2], 6) + [(1.6, Z3)]))
    A["feather"] = {"animation_length": 1.6, "bones": b}

    # ---- 段階 3 (骨だけの翼) ----
    # 閃光 (2.0 秒): 核が光を吸い込んでふくらみ、網が縮む。終わりに一気に放つ
    b = {"core_eye": {"scale": track([(0, ONE), (1.9, S(2.4)), (2.05, S(0.6)), (2.6, ONE)], "scale")},
         "core": {"scale": track([(0, ONE), (1.9, S(1.25)), (2.05, S(0.9)), (2.6, ONE)], "scale")},
         "web": {"scale": track([(0, ONE), (1.9, S(0.85)), (2.05, S(1.15)), (2.6, ONE)], "scale")}}
    A["flash"] = {"animation_length": 2.6, "bones": b}
    # 雷の網 (1.5 秒): 骨の指が開き、網が張る
    b = {"web": {"scale": track([(0, ONE), (1.5, [1.15, 1.08, 1.15]), (2.0, ONE)], "scale")},
         "finger": {"rotation": track([(0, Z3), (1.5, [-8, 0, 0]), (2.0, Z3)], "rotation")},
         "joint": {"scale": track([(0, ONE), (1.5, S(1.2)), (2.0, ONE)], "scale")}}
    A["web"] = {"animation_length": 2.0, "bones": b}
    # 招雷の連打 (1 秒ごとに 3 回): 核が三度打つ
    k = [(0, ONE)]
    for t0 in (0.0, 1.0, 2.0):
        k += [(t0 + 0.8, S(1.7)), (t0 + 0.95, S(0.9))]
    k.append((3.2, ONE))
    A["barrage"] = {"animation_length": 3.2, "bones": {"core_eye": {"scale": track(k, "scale")},
                                                      "heart": {"scale": beat_scale(3.2, (1.08, 1.0), starts=(0.0, 1.0, 2.0))}}}
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


# 足す光の強さ (基の色に掛ける)。核・心臓は強く、網 (段階 3) は弱めにして骨を見せる
GLOW_GAIN = {"default": 0.55, "web": 0.30}
LIFT_GAMMA = 0.8


def bone_mask(geo, tex_shape, bone):
    """骨の箱が使う画素の範囲 (geo の空間の uv)"""
    g = geo["minecraft:geometry"][0]
    tw, th = g["description"]["texture_width"], g["description"]["texture_height"]
    sx, sy = tex_shape[1] / tw, tex_shape[0] / th
    m = np.zeros(tex_shape[:2], bool)
    for b in g["bones"]:
        if b["name"] != bone:
            continue
        for c in b.get("cubes", []):
            for f in c["uv"].values():
                u, v = f["uv"]
                w, h = f["uv_size"]
                m[int(round(v * sy)):int(round((v + h) * sy)), int(round(u * sx)):int(round((u + w) * sx))] = True
    return m


def colorize(geo, tex, glow):
    t = tex.astype(float)
    a = tex[..., 3] > 0
    lit = glow[..., 3] > 0
    base = a & ~lit
    t[base, :3] = 255.0 * (t[base, :3] / 255.0) ** LIFT_GAMMA
    g = np.zeros_like(glow)
    web = bone_mask(geo, tex.shape, "web")
    gain = np.where(web, GLOW_GAIN["web"], GLOW_GAIN["default"])
    add = np.clip(t[..., :3] * gain[..., None], 0, 255)
    g[lit, :3] = add[lit].astype(np.uint8)
    g[lit, 3] = 255
    return np.clip(t, 0, 255).astype(np.uint8), g


def load_png(p):
    return np.array(Image.open(p).convert("RGBA"))


def checks(ph, geo):
    problems = check_overlaps(geo)
    assert not problems, f"phase {ph}: overlapping cubes: {problems[:5]}"
    for b in geo["minecraft:geometry"][0]["bones"]:
        for c in b.get("cubes", []):
            for x in list(c["origin"]) + list(c["size"]):
                assert abs(x * 4 - round(x * 4)) < 1e-6, f"phase {ph}: cube not on the 0.25 grid in {b['name']}: {c}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--option", default="c", choices=["a", "b", "c"])
    args = ap.parse_args()
    src = source(args.option)
    anim = anims()
    used = sorted({b for a in anim["animations"].values() for b in a["bones"]})
    out_geo = {}
    rough = {}
    for ph in (1, 2, 3):
        geo = json.loads((src / f"phase{ph}.geo.json").read_text(encoding="utf-8"))
        checks(ph, geo)
        tex = load_png(src / f"phase{ph}.tex.png")
        glow = load_png(src / f"phase{ph}.glow.png")
        rough[ph] = (geo, tex, glow)
        g2, t2, gl2 = H.convert_geo(geo, tex, glow)
        t2, gl2 = colorize(g2, t2, gl2)
        g2["minecraft:geometry"][0]["description"]["identifier"] = f"geometry.skyabyss.{NAME}"
        added = ensure_bones(g2, used)
        lit = int((gl2[..., 3] > 0).sum())
        assert lit >= 1, "発光マスクに光る画素がない (GeckoLib が落ちる)"
        (ASSET / f"geo/entity/{NAME}_p{ph}.geo.json").write_text(json.dumps(g2, ensure_ascii=False, indent=1), encoding="utf-8")
        Image.fromarray(t2).save(ASSET / f"textures/entity/{NAME}_p{ph}.png")
        Image.fromarray(gl2).save(ASSET / f"textures/entity/{NAME}_p{ph}_glowmask.png")
        out_geo[ph] = (g2, t2, gl2)
        ncubes = sum(len(b.get("cubes", [])) for b in g2["minecraft:geometry"][0]["bones"])
        print(f"phase {ph}: {ncubes} cubes, glow px {lit}, overlaps 0, empty bones added: {added}")
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
    print("rough vs game-read mean abs diff (色を持ち上げた分を含む):", [round(x, 3) for x in diffs])
    poses = [(1, "summon", 1.1), (1, "blade", 1.1), (1, "split", 2.0), (1, "roar_1", 2.2), (2, "idle", 1.0),
             (2, "wingbeat", 1.5), (2, "roar_2", 2.4), (3, "flash", 1.9), (3, "web", 1.5)]
    cols2 = []
    for ph, name, t in poses:
        g2, t2, gl2 = out_geo[ph]
        p = H.sample(anim["animations"][f"animation.{NAME}.{name}"], t)
        im = render(H.gecko_faces(g2, t2, gl2[..., 3] > 0, p), 25, 15, (size, size))
        cols2.append((f"{name} {t}s", [im]))
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    s1 = sheet(f"雷翼の心臓 {args.option.upper()}: 上 ラフ / 中 ゲームの読み方 / 下 後ろの下から", cols)
    s2 = sheet("アニメーションの要所 (ゲームの読み方)", cols2)
    W = max(s1.width, s2.width)
    out = Image.new("RGB", (W, s1.height + s2.height), (20, 20, 26))
    out.paste(s1, (0, 0))
    out.paste(s2, (0, s1.height))
    out.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
