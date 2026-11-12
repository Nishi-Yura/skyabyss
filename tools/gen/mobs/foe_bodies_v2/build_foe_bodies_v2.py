# -*- coding: utf-8 -*-
"""敵の体の作り直し (2026-11-12。全モデルの確かめで粗いと分かった敵 13 体。直し方は案 A「作り込み」に決まった:
tools/gen/mobs/dungeon_foes_lp/rough_foe_bodies.py / docs/review/foe_bodies/rough_a.png)。

    python -X utf8 -I tools/gen/mobs/foe_bodies_v2/build_foe_bodies_v2.py review [名前 ...]   (今と新しいの比べ: docs/review/foe_bodies/v2_<名前>.png)
    python -X utf8 -I tools/gen/mobs/foe_bodies_v2/build_foe_bodies_v2.py install [名前 ...]

今の姿のまま、面を増やして丸め、部品を足す (服のしわと継ぎ・帯と留め具・指・顔の彫り・毛の房)。
骨の名前・親・pivot は今のメッシュのまま (fb.start)。動きのファイルは触らない。
"""
from __future__ import annotations

import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from fb import *  # noqa: E402,F401,F403
import fb  # noqa: E402
import foes_human as H  # noqa: E402
import foes_beast as B  # noqa: E402
import foes_bandit as BD  # noqa: E402
import foes_abyss as AB  # noqa: E402
import copy  # noqa: E402

ALL = {}
ALL.update(H.ALL)
ALL.update(BD.ALL)
ALL.update(AB.ALL)
ALL.update(B.ALL)


def sheet(name, m, atlas, glow):
    V = np.concatenate([f.verts for f in m.F])
    lo, hi = V.min(0), V.max(0)
    cen = (lo + hi) / 2
    T = 360
    sc = (T - 30) / max(1.0, float(np.max(hi - lo)) * 1.12)
    tris = sum(len(f.verts) - 2 for f in m.F)
    old_tris = sum(len(b.get("triangles") or []) for b in m.old["bones"].values())
    cv = Image.new("RGB", (4 * T + 50, T + 30), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    tiles = [("今", fb.render_now(m.id, 215, 12, (T, T), cen, sc)), ("今 後ろ", fb.render_now(m.id, 35, 12, (T, T), cen, sc)),
             ("新", view(m.F, atlas, glow, 215, 12, (T, T), cen, sc, ground=False)), ("新 後ろ", view(m.F, atlas, glow, 35, 12, (T, T), cen, sc, ground=False))]
    for i, (lab, img) in enumerate(tiles):
        x = 10 + i * (T + 10)
        cv.paste(img, (x, 28), img if img.mode == "RGBA" else None)
        d.text((x, 6), f"{name} {lab} {old_tris if i < 2 else tris}", font=voxel.font(15), fill=(150, 232, 255))
    REVIEW.mkdir(parents=True, exist_ok=True)
    cv.save(REVIEW / f"v2_{name}.png")


def main():
    mode = sys.argv[1] if len(sys.argv) > 1 else "review"
    names = [a for a in sys.argv[2:] if not a.startswith("--")] or list(ALL)
    for n in names:
        m = ALL[n]()
        size, atlas, glow = paint_mount(m, m.pal, m.detail, getattr(m, "hint", 256))
        tris = sum(len(f.verts) - 2 for f in m.F)
        if mode == "review":
            sheet(n, m, atlas, glow)
        else:
            if hasattr(m, "stage_rgb"):
                # 淵の三段: 同じ UV の面を骨の付け根から上下へ伸ばし、絵を暗くして割れ目を光らせる
                for k, (suf, stretch, dark, vein) in enumerate(AB.STAGES):
                    sm = copy.copy(m)
                    sm.F = []
                    for f in m.F:
                        g = copy.copy(f)
                        pv = np.array(m.bones[f.bone][1], float)
                        v = np.array(f.verts, float)
                        if f.bone != "root":
                            v[:, 1] = pv[1] + (v[:, 1] - pv[1]) * stretch
                        g.verts = v
                        sm.F.append(g)
                    a2, g2 = (atlas, glow) if vein == 0 else fb.stage_tex(atlas, glow, m.stage_rgb, dark, vein, 7 + k)
                    fb.install(n + suf, sm, size, a2, g2)
            else:
                stages = m.stages(atlas, glow) if hasattr(m, "stages") else None
                fb.install(n, m, size, atlas, glow, stages)
        print(n, tris, "tris", size, "px", flush=True)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
