"""深淵の心臓の間 (abyss_layers.json の印 heart のまわり。半径 30 の筒に丸天井) に、肋の柱を立てる (2026-10-28 夜の見直し)。

心臓の間は高さ 47 の何もない筒で、最後の戦いの場としては空いていた。巨きな胸の内を思わせるように、
壁ぞいに 12 本の骨の肋 (骨のブロック、5 段ごとに方解石の節。二つ前の試しの深層岩は壁と見分けにくかった) を床から丸天井のてっぺんまで這わせ、てっぺんで輪にまとめる。
  - 壁から 2〜3 マスだけ出す (戦いの場 = 心臓から 26 以内には入らない。灯籠の台 beacon_* は半径 18)
  - 入口の通路 (heart_in の向き) の前には立てない
  - 光るブロックは使わない (間の闇と灯籠の決まりを変えない)
二度は立てない (設計図の dress に heart_ribs があればやめる)。建て直しには AbyssBuild.VERSION を上げる。

  python -I tools/abyss/dress_heart.py
"""
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools", "gen", "world"))
import town_outskirts as T  # noqa: E402  (設計図の読み書き)

PATH = os.path.join(T.DIR, "abyss_layers.json")
TAG = "heart_ribs"
RIB = "minecraft:bone_block[axis=y]"
JOINT = "minecraft:calcite"
CAP = "minecraft:bone_block[axis=x]"
SOLID_SKIP = (":air", "cave_air")


def main():
    d, c = T.load(PATH)
    if TAG in d.get("dress", []):
        print("already done")
        return
    hx, hy, hz = d["marks"]["heart"]
    ix, iy, iz = d["marks"]["heart_in"]
    door = math.atan2(iz - hz, ix - hx)

    def air(x, y, z):
        s = c.get((x, y, z))
        return s is not None and s.endswith(SOLID_SKIP)

    floor = hy - 1   # 最初の空気の段 (床は hy - 2)
    top = max(y for y in range(hy, hy + 60) if air(hx + 3, y, hz))   # 丸天井のてっぺん (真ん中の鎖を避けて 3 横で測る)
    placed = 0
    for k in range(12):
        a = math.radians(15 + 30 * k)
        if abs(math.atan2(math.sin(a - door), math.cos(a - door))) < math.radians(20):
            continue
        ca, sa = math.cos(a), math.sin(a)
        for y in range(floor, top + 1):
            r = 0.0
            while r < 40 and air(hx + round(r * ca), y, hz + round(r * sa)):
                r += 0.5
            if r >= 33 or r < 3:
                continue
            depth = 3 if y - floor < 4 else 2   # 壁から 2 マス、足もとは 3
            st = JOINT if (y - floor) % 5 == 4 else RIB
            for dr in range(1, depth + 1):
                rr = r - dr
                for t in (-0.6, 0.0, 0.6):
                    x = hx + round(rr * ca - t * sa)
                    z = hz + round(rr * sa + t * ca)
                    if math.hypot(x - hx, z - hz) < 26 and y < hy + 10:
                        continue
                    if air(x, y, z):
                        c[(x, y, z)] = st
                        placed += 1
    # てっぺんの輪 (肋が集まる所)
    for y in range(top - 1, top + 1):
        for x in range(hx - 4, hx + 5):
            for z in range(hz - 4, hz + 5):
                if 2.2 <= math.hypot(x - hx, z - hz) <= 3.6 and air(x, y, z):
                    c[(x, y, z)] = CAP
                    placed += 1
    d.setdefault("dress", []).append(TAG)
    T.save(PATH, d, c)
    print(f"heart ribs: {placed} blocks (floor {floor}, top {top})")


if __name__ == "__main__":
    main()
