"""背中ダンジョンのブロックとアイテムのテクスチャ (32x32、バニラの2倍の密度) を作る。

使い方: python tools/gen/items/gen_block_textures.py
出力先: src/main/resources/assets/skyabyss/textures/{block,item}/
"""

# ---------------------------------------------------------------------------
# 上書き防止 (docs/review/assets_audit.md の S1): 実行すると採用済みの素材を古い版で上書きする。
#   上書きするもの: textures/block/*.png 12 枚と textures/item/sky_key_fragment.png (採用の 32x32 案 B)
#   採用版の生成元: tools/rough/items_blocks_32.py
# どうしても動かすときだけ、環境変数 SKYABYSS_ALLOW_REGEN=1 を付ける。一覧は tools/README.md。
# ---------------------------------------------------------------------------
import os as _os
import sys as _sys

if __name__ == "__main__" and _os.environ.get("SKYABYSS_ALLOW_REGEN") != "1":
    _sys.exit("tools/gen/items/gen_block_textures.py は停止中です: 採用済みの素材 (textures/block/*.png 12 枚と textures/item/sky_key_fragment.png (採用の 32x32 案 B)) を上書きするため。"
              "採用版の生成元は tools/rough/items_blocks_32.py です。どうしても実行するときは SKYABYSS_ALLOW_REGEN=1 を付けてください (tools/README.md)。")
import math
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent.parent.parent / "src/main/resources/assets/skyabyss/textures"
S = 32


def noise(seed, scale):
    r = np.random.default_rng(seed)
    g = r.random((S // scale + 2, S // scale + 2))
    img = Image.fromarray((g * 255).astype(np.uint8)).resize((S + 2 * scale, S + 2 * scale), Image.BICUBIC)
    return np.asarray(img, float)[scale:scale + S, scale:scale + S] / 255.0


def stone(seed, light, dark):
    n = noise(seed, 8) * 0.5 + noise(seed + 1, 4) * 0.3 + np.random.default_rng(seed + 2).random((S, S)) * 0.2
    light, dark = np.array(light, float), np.array(dark, float)
    return dark + (light - dark) * n[..., None]


def bevel(img, amount=0.25):
    """石組みらしく、縁を明るく・下と右を暗くする"""
    img[0, :] *= 1 + amount
    img[:, 0] *= 1 + amount
    img[-1, :] *= 1 - amount
    img[:, -1] *= 1 - amount
    return img


def save(img, path, alpha=None):
    out = np.zeros((S, S, 4), np.uint8)
    out[..., :3] = np.clip(img, 0, 255)
    out[..., 3] = 255 if alpha is None else alpha
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGBA").save(path)


def rune_mask(seed):
    """中心の円と、そこから伸びる刻み線の紋様"""
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    cx = cy = S / 2
    r = np.hypot(xx - cx, yy - cy)
    a = np.arctan2(yy - cy, xx - cx)
    ring = np.abs(r - 9) < 1.0
    inner = np.abs(r - 4) < 0.9
    spokes = (np.abs(np.sin(a * 3 + seed)) < 0.12) & (r > 4) & (r < 13)
    return ring | inner | spokes


def resonance_stone(active):
    img = bevel(stone(11, (74, 76, 92), (38, 40, 52)))
    mask = rune_mask(0.4)
    if active:
        img[mask] = (120, 230, 255)
        # 紋様のまわりのにじみ
        glow = np.zeros((S, S))
        glow[mask] = 1
        blur = np.asarray(Image.fromarray((glow * 255).astype(np.uint8)).resize((8, 8), Image.BILINEAR).resize((S, S), Image.BILINEAR), float) / 255
        img = img + np.array([40, 110, 140]) * blur[..., None]
    else:
        img[mask] = img[mask] * 0.55
    return img


def sealed_gate():
    img = stone(21, (52, 44, 66), (26, 22, 34))
    yy, xx = np.mgrid[0:S, 0:S]
    # 斜めに交差する封印の帯
    band = (np.abs((xx - yy) % 16 - 8) < 1.2) | (np.abs((xx + yy) % 16 - 8) < 1.2)
    img[band] = img[band] * 0.4 + np.array([170, 110, 230]) * 0.6
    return bevel(img, 0.15)


def warded_stone():
    img = stone(31, (64, 64, 74), (34, 34, 42))
    yy, xx = np.mgrid[0:S, 0:S]
    joint = (yy % 16 == 0) | ((xx + (yy // 16) * 8) % 16 == 0)
    img[joint] *= 0.6
    # 目地の中にかすかな紫の線
    faint = (yy % 16 == 8) & (xx % 6 < 2)
    img[faint] = img[faint] * 0.7 + np.array([120, 90, 160]) * 0.3
    return img


def key_fragment():
    """天の鍵の欠片: 青白い石の破片に、光る筋が走っている"""
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    poly = [(9, 4), (20, 3), (27, 11), (24, 25), (14, 29), (6, 21), (5, 11)]
    from matplotlib.path import Path as MPath  # noqa: E402 (matplotlibがなければ下の簡易判定を使う)
    inside = MPath(poly).contains_points(np.c_[xx.ravel(), yy.ravel()]).reshape(S, S)
    base = stone(41, (210, 214, 226), (140, 146, 168))
    shade = np.clip(1.15 - (xx + yy) / (2 * S) * 0.5, 0.6, 1.2)
    img = base * shade[..., None]
    vein = np.abs(np.sin(xx * 0.35 + 1.0) * 6 + 16 - yy) < 1.1
    img[vein & inside] = (150, 235, 255)
    alpha = np.where(inside, 255, 0).astype(np.uint8)
    return img, alpha


def forge_top():
    """共鳴の鍛冶台の上面: 暗い石の縁に囲まれた円形の窪みと、中で揺れる淡い水色の光"""
    img = bevel(stone(51, (58, 60, 72), (30, 32, 42)), 0.2)
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    r = np.hypot(xx - S / 2, yy - S / 2)
    # 窪みの縁に落ちる影
    lip = (r >= 10.0) & (r < 11.2)
    img[lip] *= 0.5
    # 石の縁は外側がわずかに明るい
    rim = (r >= 11.2) & (r < 13.5)
    img[rim] = img[rim] * 1.2 + 6
    # 窪みの中: 中心ほど明るい水色、同心円状のかすかな波紋
    basin = r < 10.0
    g = np.clip(1 - r / 10.0, 0, 1)[..., None]
    liquid = np.array([54, 150, 182]) * (1 - g) + np.array([176, 246, 255]) * g
    ripple = 0.93 + 0.07 * np.sin(r * 1.5)
    img[basin] = (liquid * ripple[..., None])[basin]
    return img


def forge_side_base():
    """共鳴の鍛冶台の側面: 磨かれた石レンガ、上部の骨白色の帯、中央の細い水色の紋様線"""
    img = bevel(stone(61, (50, 52, 64), (28, 30, 40)), 0.15)
    yy, xx = np.mgrid[0:S, 0:S]
    # 8px 段のレンガ、段ごとに半レンガ分ずらした目地
    row = yy // 8
    joint = (yy % 8 == 0) | ((xx + row * 8) % 16 == 0)
    img[joint] *= 0.5
    # 各レンガの上縁の光沢
    img[(yy % 8 == 1) & ~joint] *= 1.2
    # 上部の骨白色の帯と、その下の影
    trim = (yy >= 3) & (yy < 6)
    bone = np.array([222, 216, 196]) * (0.85 + 0.15 * noise(81, 4))[..., None]
    img[trim] = bone[trim]
    img[yy == 6] *= 0.5
    # 中央の細い水色の紋様線 (上下にわずかな光のにじみ)
    glow_rows = (yy == 15) | (yy == 17)
    img[glow_rows] = img[glow_rows] * 0.8 + np.array([40, 110, 140]) * 0.2
    rune = (yy == 16) & (xx % 5 != 4)
    img[rune] = (120, 230, 255)
    return img


def forge_front():
    """共鳴の鍛冶台の正面: 側面と同じ石組みに、下中央のアーチ型の開口部と内側の光"""
    img = forge_side_base()
    yy, xx = np.mgrid[0:S, 0:S] + 0.5
    dx = np.abs(xx - 16)

    def arch(e):
        # 下が直線、上が半円のアーチ (e で一回り大きくする)
        return ((dx < 5 + e) & (yy >= 24) & (yy < 30 + e)) | (
            (np.hypot(xx - 16, yy - 24) < 5 + e) & (yy < 24)
        )

    inside = arch(0)
    frame = arch(1) & ~inside
    img[frame] = (150, 142, 126)
    # 開口部の中: 水色の光が中心、外に向かって紫になる
    d = np.clip(np.hypot(xx - 16, yy - 26) / 6, 0, 1)[..., None]
    glow = np.array([120, 240, 255]) * (1 - d) + np.array([140, 80, 230]) * d
    img[inside] = glow[inside]
    return img


def forge_bottom():
    """共鳴の鍛冶台の底面: 模様のない暗い石"""
    return bevel(stone(71, (44, 46, 58), (24, 26, 34)), 0.2)


def save_rgba(rgba, path):
    """S 以外のサイズの RGBA 画像を保存する (status effect アイコン用)"""
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba.astype(np.uint8), "RGBA").save(path)


def bleeding_icon():
    """出血の状態異常アイコン (18x18): 暗赤色の血の雫、白いハイライト、背景は透明"""
    N = 18
    yy, xx = np.mgrid[0:N, 0:N] + 0.5
    cy = 11.5

    def drop(R, top):
        circle = np.hypot(xx - 9, yy - cy) <= R
        tri = (yy >= top) & (yy < cy) & (np.abs(xx - 9) <= R * (yy - top) / (cy - top))
        return circle | tri

    body = drop(5.5, 2.0)
    edge = body & ~drop(4.3, 3.0)
    t = np.clip(((xx - 6) + (yy - 8)) / 16, 0, 1)[..., None]
    col = np.array([200, 24, 32]) * (1 - t) + np.array([96, 4, 14]) * t

    img = np.zeros((N, N, 4))
    img[..., :3] = col
    img[body, 3] = 255
    img[edge, :3] = (52, 0, 8)
    spot = (np.hypot(xx - 6.3, yy - 9.6) <= 1.5) & body
    img[spot, :3] = (250, 170, 170)
    dot = (np.hypot(xx - 5.6, yy - 8.6) <= 0.6) & body
    img[dot, :3] = (255, 240, 240)
    return img


def crack_lines(seed, count, steps, branch=0.06):
    """ひび割れの線 (ランダムに折れ曲がる線。ところどころ枝分かれする)"""
    r = np.random.default_rng(seed)
    m = np.zeros((S, S), bool)
    for _ in range(count):
        x, y = r.uniform(0, S), r.uniform(0, S)
        ang = r.uniform(0, 2 * math.pi)
        for _ in range(steps):
            ang += r.normal(0, 0.7)
            x += math.cos(ang)
            y += math.sin(ang)
            if not (0 <= x < S and 0 <= y < S):
                break
            m[int(y), int(x)] = True
            if r.random() < branch:
                ang2 = ang + r.choice([-1, 1]) * r.uniform(0.6, 1.2)
                bx, by = x, y
                for _ in range(steps // 4):
                    ang2 += r.normal(0, 0.5)
                    bx += math.cos(ang2)
                    by += math.sin(ang2)
                    if not (0 <= bx < S and 0 <= by < S):
                        break
                    m[int(by), int(bx)] = True
    return m


def bone(seed):
    """骨の地色: 黄みがかった白に、細かい筋と斑点"""
    n = noise(seed, 4) * 0.6 + np.random.default_rng(seed + 3).random((S, S)) * 0.4
    base = np.array([224, 214, 186], float)
    return base * (0.86 + 0.18 * n[..., None])


def crumbling_bone(cracking, seed=41):
    """崩れる骨: 骨の地色に、ひび割れと欠けを入れる。cracking のときは、さらに深く割れている"""
    rgb = bone(seed)
    # 側面は縦の筋 (骨の繊維)
    yy, xx = np.mgrid[0:S, 0:S]
    grain = np.sin(xx * 1.9 + noise(seed + 5, 8) * 6) * 0.5 + 0.5
    side = rgb * (0.92 + 0.12 * grain[..., None])
    # 上面は年輪のような同心円の筋
    rr = np.hypot(xx - 15.5, yy - 15.5)
    rings = np.abs((rr % 5.0) - 2.5) < 0.6
    top = rgb.copy()
    top[rings] *= 0.84
    dark = np.array([84, 70, 58], float)
    for img, lines_seed in ((side, seed + 10), (top, seed + 20)):
        count, steps = (6, 24) if cracking else (4, 18)
        m = crack_lines(lines_seed, count, steps)
        img[m] = dark
        if cracking:
            # 割れ目の両側に欠けた穴 (奥の暗い骨が見える)
            r = np.random.default_rng(lines_seed + 1)
            chips = m & (r.random((S, S)) < 0.35)
            img[np.roll(chips, 1, axis=1)] = dark * 0.8
            img[np.roll(chips, -1, axis=0)] = np.array([150, 136, 116], float)
    return side, top


def main():
    save(resonance_stone(False), ROOT / "block/resonance_stone.png")
    save(resonance_stone(True), ROOT / "block/resonance_stone_active.png")
    save(sealed_gate(), ROOT / "block/sealed_gate.png")
    save(warded_stone(), ROOT / "block/warded_stone.png")
    try:
        img, alpha = key_fragment()
    except ImportError:
        raise SystemExit("matplotlib が必要です: python -m pip install matplotlib")
    save(img, ROOT / "item/sky_key_fragment.png", alpha)
    save(forge_top(), ROOT / "block/resonance_forge_top.png")
    save(forge_side_base(), ROOT / "block/resonance_forge_side.png")
    save(forge_front(), ROOT / "block/resonance_forge_front.png")
    save(forge_bottom(), ROOT / "block/resonance_forge_bottom.png")
    save_rgba(bleeding_icon(), ROOT / "mob_effect/bleeding.png")
    # 崩れる骨: ひびの入った骨 (乗る前) と、崩れかけの骨 (ひびが大きく割れる)
    for cracking, suffix in ((False, ""), (True, "_cracking")):
        side, top = crumbling_bone(cracking)
        save(side, ROOT / f"block/crumbling_bone_side{suffix}.png")
        save(top, ROOT / f"block/crumbling_bone_top{suffix}.png")
    print("ok")


if __name__ == "__main__":
    main()
