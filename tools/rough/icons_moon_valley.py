"""新月の谷 (六章 区画 1) の品のアイコン 5 つ × 3 案と、仕掛けのブロックの様式 3 案 (docs/design/46 の 4.3)。

品 (32x32。描き方は icons_phase2.Icon): 星の灯・星の欠片・月の砂・三日月の殻・影の綴じ糸。案ごとに形を変える (一品一案)。
ブロック (32x32 の絵): 欠片の台・鏡の石・星の門・灯りの台・星の溜まり・星の印・影の橋。七つは一つの「月の調度」の様式でそろえる:
  A 黒曜の星図   磨いた黒石に銀の星図の線を彫る。光は青白
  B 白い月石     白い月の石に藍の象嵌。光は淡い紫
  C 古い真鍮と硝子 暗い真鍮の枠に星の硝子。光は白金
影の橋だけは様式に依らない (黒い硝子に星の粒)。

  python tools/rough/icons_moon_valley.py                 -> docs/review/moon_roughs/icons/<品>_<案>.png・compare_items.png、blocks/<様式>/moon_*.png
  python tools/rough/icons_moon_valley.py --install-blocks A  -> 選んだ様式の絵を assets/skyabyss/textures/block/ へ (と模型を書き直す)
  python tools/rough/icons_moon_valley.py --install-items a,b,c,a,b  -> 品の絵 (並びは ITEMS の順)
"""
from __future__ import annotations

import math
import shutil
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from icons_phase2 import Icon, E, P, R, L, sheet  # noqa: E402
import icons_phase2  # noqa: E402

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "docs/review/moon_roughs/icons"
BOUT = ROOT / "docs/review/moon_roughs/blocks"
icons_phase2.OUT = OUT
N = 32

STAR = (214, 234, 255)
STAR_D = (120, 170, 230)
SILVER = (196, 204, 220)
SILVER_D = (120, 128, 146)
BASALT = (52, 56, 70)
BASALT_D = (30, 32, 42)
DUST = (176, 182, 194)
DUST_D = (120, 126, 140)
SHELL = (84, 90, 108)
INDIGO = (40, 44, 76)
BRASS = (150, 118, 64)
GLASS = (150, 190, 230)


# ================================================================== 品
def star_lamp(k):
    ic = Icon()
    if k == 0:   # 提げ手のある小さな角灯。中に星
        ic.put(ic.mask(L([(11, 6), (16, 2), (21, 6)], 1)), SILVER_D, shade=False)
        ic.put(ic.mask(R((10, 6, 22, 8))), BASALT)
        ic.put(ic.mask(R((11, 9, 21, 23))), (60, 80, 120))
        ic.put(ic.mask(R((13, 11, 19, 21))), STAR_D, shade=False)
        ic.put(ic.mask(P([(16, 12), (18, 16), (16, 20), (14, 16)])), STAR, shade=False)
        ic.put(ic.mask(R((10, 23, 22, 26))), BASALT)
        for x in (11, 21):
            ic.put(ic.mask(R((x, 8, x, 23))), SILVER, shade=False)
        d = "提げ手のある小さな角灯 (中に星)"
    elif k == 1:   # 掌にのる星の結晶 (台の上に浮く)
        ic.put(ic.mask(E((8, 24, 24, 29))), BASALT)
        ic.put(ic.mask(P([(16, 3), (22, 12), (16, 22), (10, 12)])), STAR_D)
        ic.put(ic.mask(P([(16, 6), (19, 12), (16, 18), (13, 12)])), STAR, shade=False)
        ic.px([(6, 8), (26, 9), (24, 18), (7, 17)], STAR)
        d = "台の上に浮く星の結晶 (まわりに粒)"
    else:   # 先に星を灯した短い杖
        ic.put(ic.mask(L([(9, 29), (19, 11)], 2)), (110, 84, 60))
        ic.put(ic.mask(L([(15, 17), (19, 19)], 1)), SILVER, shade=False)
        ic.put(ic.mask(E((16, 3, 28, 15))), STAR_D)
        ic.put(ic.mask(E((19, 6, 25, 12))), STAR, shade=False)
        ic.px([(14, 4), (28, 2), (29, 16)], STAR)
        d = "先に星を灯した短い杖"
    return ic.done(), d


def star_shard(k):
    ic = Icon()
    if k == 0:   # 尖った星のかけら (五つの角の一つ)
        ic.put(ic.mask(P([(16, 2), (24, 20), (16, 29), (8, 20)])), STAR_D)
        ic.put(ic.mask(P([(16, 6), (20, 19), (16, 25), (12, 19)])), STAR, shade=False)
        d = "尖った星のかけら"
    elif k == 1:   # 割れた五芒星の半分
        ic.put(ic.mask(P([(16, 3), (19, 12), (29, 12), (21, 18), (24, 28), (16, 22)])), STAR_D)
        ic.put(ic.mask(P([(16, 8), (18, 13), (24, 14), (19, 18), (16, 18)])), STAR, shade=False)
        ic.put(ic.mask(L([(16, 3), (16, 22)], 1)), SILVER_D, shade=False)
        d = "割れた五芒星の半分"
    else:   # 黒い岩に埋まった光る結晶
        ic.put(ic.mask(P([(4, 22), (10, 14), (22, 13), (28, 21), (22, 28), (9, 28)])), BASALT)
        ic.put(ic.mask(P([(12, 18), (15, 4), (19, 17)])), STAR_D)
        ic.put(ic.mask(P([(18, 18), (23, 8), (24, 19)])), STAR)
        d = "黒い岩に埋まった光る結晶"
    return ic.done(), d


def moon_dust(k):
    ic = Icon()
    if k == 0:   # 小さな山 (粒の濃淡)
        ic.put(ic.mask(P([(4, 26), (12, 14), (17, 10), (22, 14), (28, 26)])), DUST)
        ic.px([(10, 22), (14, 19), (19, 16), (22, 23), (16, 24), (12, 25)], DUST_D)
        ic.px([(16, 13), (18, 18), (24, 21)], (226, 230, 236))
        d = "青白い砂の小さな山"
    elif k == 1:   # 口を縛った布袋
        ic.put(ic.mask(E((6, 10, 26, 30))), (120, 112, 100))
        ic.put(ic.mask(R((13, 6, 19, 11))), (120, 112, 100))
        ic.put(ic.mask(L([(11, 11), (21, 11)], 1)), (200, 180, 120), shade=False)
        ic.put(ic.mask(E((9, 3, 23, 9))), DUST)
        d = "口から砂のこぼれる布袋"
    else:   # 硝子の小瓶に入った砂
        ic.put(ic.mask(R((10, 9, 22, 28))), (150, 170, 190))
        ic.put(ic.mask(R((11, 17, 21, 27))), DUST, shade=False)
        ic.put(ic.mask(R((13, 4, 19, 9))), (120, 90, 60))
        d = "硝子の小瓶に詰めた砂"
    return ic.done(), d


def crescent_shell(k):
    ic = Icon()
    if k == 0:   # 三日月の殻板 (縁が銀)
        outer = ic.mask(E((3, 3, 29, 29)))
        inner = ic.mask(E((10, 1, 32, 23)))
        m = outer & ~inner
        ic.put(m, SHELL)
        ring = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
        ic.rgb[ring] = SILVER
        d = "三日月の形の殻板 (縁が銀)"
    elif k == 1:   # 割れた殻の破片を二枚重ね
        ic.put(ic.mask(P([(3, 18), (10, 6), (18, 4), (12, 16), (8, 26)])), SHELL)
        ic.put(ic.mask(P([(14, 26), (20, 14), (29, 10), (24, 22), (18, 29)])), (70, 76, 92))
        ic.put(ic.mask(L([(10, 6), (18, 4)], 1)), SILVER, shade=False)
        ic.put(ic.mask(L([(20, 14), (29, 10)], 1)), SILVER, shade=False)
        d = "割れた殻の破片を二枚重ね"
    else:   # 殻板に星の割れ目 (光る筋)
        outer = ic.mask(E((4, 4, 28, 28)))
        inner = ic.mask(E((12, 2, 32, 22)))
        ic.put(outer & ~inner, SHELL)
        ic.put(ic.mask(L([(9, 12), (12, 18), (10, 24)], 1)), STAR, shade=False)
        d = "殻板に走る星の割れ目"
    return ic.done(), d


def shadow_thread(k):
    ic = Icon()
    if k == 0:   # 光る糸の小さな巻き
        ic.put(ic.mask(E((7, 9, 25, 25))), (34, 36, 60))
        for y in (12, 15, 18, 21):
            ic.put(ic.mask(L([(8, y), (24, y)], 1)), STAR_D, shade=False)
        ic.put(ic.mask(L([(24, 21), (29, 27)], 1)), STAR, shade=False)
        d = "星の光の糸の小さな巻き"
    elif k == 1:   # 針に通した糸 (縫い目の形)
        ic.put(ic.mask(L([(5, 27), (26, 5)], 1)), SILVER)
        ic.px([(25, 6), (24, 7)], (40, 40, 60))
        for t in range(6):
            x, y = 6 + t * 3, 20 - (t % 2) * 4
            ic.put(ic.mask(L([(x, y), (x + 3, 20 - ((t + 1) % 2) * 4)], 1)), STAR, shade=False)
        d = "針に通した、縫い目の形の糸"
    else:   # 影の帯の切れ端を光る糸が留める
        ic.put(ic.mask(P([(6, 4), (14, 4), (18, 28), (10, 28)])), (26, 28, 48))
        ic.put(ic.mask(P([(15, 4), (22, 4), (26, 28), (19, 28)])), (40, 44, 72))
        for y in (9, 16, 23):
            ic.put(ic.mask(L([(9, y), (24, y + 1)], 1)), STAR, shade=False)
        d = "影の帯の切れ端を留める光る糸"
    return ic.done(), d


ITEMS = [("star_lamp", "星の灯", star_lamp), ("star_shard", "星の欠片", star_shard), ("moon_dust", "月の砂", moon_dust),
         ("crescent_shell", "三日月の殻", crescent_shell), ("shadow_thread", "影の綴じ糸", shadow_thread)]


# ================================================================== ブロックの絵 (様式 A・B・C)
def h2(x, y, s=0):
    v = (x * 374761393 + y * 668265263 + s * 974634901) & 0xFFFFFFFF
    v = ((v ^ (v >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((v ^ (v >> 16)) & 0xFFFF) / 65535.0


def canvas(base, var=10, seed=1):
    a = np.zeros((N, N, 4), np.int32)
    for y in range(N):
        for x in range(N):
            n = (h2(x // 2, y // 2, seed) - 0.5) * 2 * var + (h2(x, y, seed + 7) - 0.5) * var * 0.6
            a[y, x, :3] = np.clip(np.array(base) + n, 0, 255)
            a[y, x, 3] = 255
    return a


def bevel(a, light, dark, w=1):
    a[:w, :, :3] = light
    a[:, :w, :3] = light
    a[-w:, :, :3] = dark
    a[:, -w:, :3] = dark
    return a


def line(a, pts, col, w=1):
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(y1 - y0)) + 1
        for i in range(n):
            t = i / max(1, n - 1)
            x, y = int(round(x0 + (x1 - x0) * t)), int(round(y0 + (y1 - y0) * t))
            for dx in range(w):
                for dy in range(w):
                    if 0 <= x + dx < N and 0 <= y + dy < N:
                        a[y + dy, x + dx, :3] = col
    return a


def star_pts(a, pts, col):
    for x, y in pts:
        a[y, x, :3] = col
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 0 <= x + dx < N and 0 <= y + dy < N:
                a[y + dy, x + dx, :3] = (np.array(col) * 0.6 + a[y + dy, x + dx, :3] * 0.4).astype(int)
    return a


def img(a):
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8), "RGBA")


STYLES = {
    "A": dict(name="黒曜の星図", stone=(36, 34, 46), stone_l=(70, 68, 84), stone_d=(18, 16, 24), inlay=(186, 200, 226), glow=(196, 226, 255),
              glow_d=(90, 140, 210), frame=(150, 158, 176), pool=(60, 110, 190)),
    "B": dict(name="白い月石", stone=(196, 196, 204), stone_l=(232, 232, 238), stone_d=(140, 140, 152), inlay=(54, 62, 120), glow=(220, 200, 255),
              glow_d=(140, 110, 210), frame=(90, 100, 150), pool=(150, 120, 220)),
    "C": dict(name="古い真鍮と硝子", stone=(60, 52, 44), stone_l=(110, 92, 64), stone_d=(30, 26, 22), inlay=(170, 136, 72), glow=(255, 246, 220),
              glow_d=(220, 190, 120), frame=(160, 126, 66), pool=(200, 190, 150)),
}


def block_set(key):
    S = STYLES[key]
    out = {}
    # 台の側面: 石に、様式の線 (A 星図の線と星・B 藍の象嵌の帯・C 真鍮の枠と鋲)
    a = canvas(S["stone"], 8, 3)
    bevel(a, S["stone_l"], S["stone_d"])
    if key == "A":
        line(a, [(4, 24), (10, 14), (18, 18), (26, 8)], S["inlay"])
        star_pts(a, [(4, 24), (10, 14), (18, 18), (26, 8)], S["glow"])
    elif key == "B":
        for y in (6, 25):
            a[y:y + 2, 2:30, :3] = S["inlay"]
        line(a, [(8, 12), (16, 19), (24, 12)], S["inlay"])
    else:
        a[3:5, :, :3] = S["frame"]
        a[27:29, :, :3] = S["frame"]
        for x in (5, 16, 27):
            a[3:5, x:x + 2, :3] = S["glow_d"]
            a[27:29, x:x + 2, :3] = S["glow_d"]
    out["moon_pedestal"] = img(a)
    # 台の上面: 星の形 (A)・同心の輪 (B)・十字の真鍮 (C)
    a = canvas(S["stone"], 6, 5)
    bevel(a, S["stone_l"], S["stone_d"], 2)
    if key == "A":
        for i in range(5):
            t = i / 5 * 2 * math.pi - math.pi / 2
            line(a, [(16, 16), (int(16 + math.cos(t) * 12), int(16 + math.sin(t) * 12))], S["inlay"])
    elif key == "B":
        yy, xx = np.mgrid[0:N, 0:N]
        r = np.hypot(xx - 15.5, yy - 15.5)
        a[(np.abs(r - 11) < 0.8) | (np.abs(r - 6) < 0.8), :3] = S["inlay"]
    else:
        a[15:17, 3:29, :3] = S["frame"]
        a[3:29, 15:17, :3] = S["frame"]
    out["moon_pedestal_top"] = img(a)
    # 星の結晶 (光る)
    a = canvas(S["glow_d"], 14, 9)
    for y in range(N):
        for x in range(N):
            if (x + y) % 9 < 2 or (x - y) % 11 == 0:
                a[y, x, :3] = S["glow"]
    out["moon_star"] = img(a)
    # 鏡: 台・枠・面
    a = canvas(S["stone"], 8, 11)
    bevel(a, S["stone_l"], S["stone_d"])
    out["moon_mirror_base"] = img(a)
    a = canvas(S["frame"], 10, 13)
    bevel(a, np.clip(np.array(S["frame"]) * 1.3, 0, 255), np.array(S["frame"]) * 0.6, 2)
    out["moon_mirror_frame"] = img(a)
    a = canvas((200, 214, 232) if key != "C" else (220, 214, 190), 6, 15)
    line(a, [(4, 27), (27, 4)], (250, 252, 255), 2)
    line(a, [(10, 28), (28, 10)], (236, 240, 248))
    out["moon_mirror"] = img(a)
    # 星の門: 深い藍の夜空に星 (A)・白い石の扉に藍の星座 (B)・真鍮の格子の奥の光 (C)。真ん中の石は明るい
    if key == "A":
        a = canvas((14, 16, 34), 8, 17)
        star_pts(a, [(5, 6), (22, 4), (12, 15), (27, 19), (7, 26), (19, 27)], S["glow"])
    elif key == "B":
        a = canvas(S["stone"], 6, 17)
        bevel(a, S["stone_l"], S["stone_d"])
        line(a, [(6, 8), (13, 14), (20, 10), (26, 22)], S["inlay"])
        star_pts(a, [(6, 8), (13, 14), (20, 10), (26, 22)], S["glow_d"])
    else:
        a = canvas((30, 26, 22), 6, 17)
        for v in range(4, N, 8):
            a[:, v:v + 2, :3] = S["frame"]
            a[v:v + 2, :, :3] = S["frame"]
    out["moon_star_gate"] = img(a)
    a = canvas(S["glow_d"], 12, 19)
    yy, xx = np.mgrid[0:N, 0:N]
    r = np.hypot(xx - 15.5, yy - 15.5)
    a[r < 9, :3] = np.array(S["glow"])
    a[r < 4, :3] = (255, 255, 255)
    out["moon_star_gate_core"] = img(a)
    # 灯籠 (暗い・灯った)
    a = canvas(S["stone_d"], 6, 21)
    a[2:30, 2:30, :3] = (np.array(S["glow_d"]) * 0.25).astype(int)
    if key == "C":
        a[:, 14:18, :3] = S["frame"]
    out["moon_lamp_dark"] = img(a)
    a = canvas(S["glow"], 10, 23)
    a[r < 6, :3] = (255, 255, 255)
    if key == "C":
        a[:, 14:18, :3] = S["frame"]
    out["moon_lamp_lit"] = img(a)
    # 星の溜まり: 縁の石・光る水面
    a = canvas(S["stone"] if key != "A" else (210, 214, 220), 6, 25)
    bevel(a, S["stone_l"], S["stone_d"])
    out["moon_pool_rim"] = img(a)
    a = canvas(S["pool"], 12, 27)
    star_pts(a, [(6, 6), (20, 9), (12, 18), (26, 24), (8, 27)], S["glow"])
    out["moon_pool"] = img(a)
    # 星の印 (交差の模型。背景は透ける)
    a = np.zeros((N, N, 4), np.int32)
    for y in range(N):
        for x in range(N):
            dx, dy = abs(x - 15.5), 31 - y
            if dy < 22 and dx < (22 - dy) * 0.35 + 1:
                a[y, x] = list(S["glow"] if dx < 2.5 else S["glow_d"]) + [255]
    out["moon_star_mark"] = img(a)
    return out


def bridge_textures():
    a = canvas((10, 10, 22), 4, 31)
    a[..., 3] = 200
    star_pts(a, [(4, 5), (19, 3), (11, 13), (27, 15), (6, 24), (21, 26), (15, 20)], (120, 150, 210))
    bevel(a, (40, 42, 70), (4, 4, 10))
    out = {"moon_shadow_bridge": img(a)}
    b = np.zeros((N, N, 4), np.int32)
    b[..., :3] = (20, 22, 40)
    b[..., 3] = 60
    for x, y in [(4, 5), (19, 3), (11, 13), (27, 15), (6, 24), (21, 26)]:
        b[y, x] = (110, 140, 200, 160)
    out["moon_shadow_bridge_faded"] = img(b)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    rows = []
    for iid, jp, fn in ITEMS:
        opts = []
        for k in range(3):
            im, d = fn(k)
            im.save(OUT / f"{iid}_{'abc'[k]}.png")
            opts.append((im, d))
        rows.append((jp, opts))
    sheet(rows, "compare_items.png")
    for key in STYLES:
        d = BOUT / key
        d.mkdir(parents=True, exist_ok=True)
        for name, im in {**block_set(key), **bridge_textures()}.items():
            im.save(d / f"{name}.png")
    print("ok")


def install_blocks(key):
    dst = ROOT / "src/main/resources/assets/skyabyss/textures/block"
    for p in (BOUT / key).glob("moon_*.png"):
        shutil.copyfile(p, dst / p.name)
    subprocess.check_call([sys.executable, "-I", str(ROOT / "tools/gen/ch6/gen_valley_assets.py")], cwd=ROOT)


def install_items(picks):
    dst = ROOT / "src/main/resources/assets/skyabyss/textures/item"
    for (iid, _, _), k in zip(ITEMS, picks):
        shutil.copyfile(OUT / f"{iid}_{k}.png", dst / f"{iid}.png")
    subprocess.check_call([sys.executable, "-I", str(ROOT / "tools/gen/ch6/gen_valley_assets.py")], cwd=ROOT)


if __name__ == "__main__":
    if "--install-blocks" in sys.argv:
        install_blocks(sys.argv[sys.argv.index("--install-blocks") + 1])
    elif "--install-items" in sys.argv:
        install_items(sys.argv[sys.argv.index("--install-items") + 1].split(","))
    else:
        main()
