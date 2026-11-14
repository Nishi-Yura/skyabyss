#!/usr/bin/env python3
"""技の動きに緩急を足す (2026-11-11 の声「この感じで他のボスたちも」。dungeon_bosses_lp/blp.punch と同じ考え)。
出来上がった GeckoLib のアニメ (animations/entity/<名前>.animation.json) を読み、一度だけの動きごとに:
  打つ瞬間 = 前の鍵からの姿勢の変わりがいちばん大きい鍵の時刻 ti、その前の鍵 tw
  ・ tw と ti のあいだ (tw + 0.55 (ti - tw)) に、溜めの姿 (tw) を 1.12 倍にした「震え」の鍵
  ・ ti + 0.08 に、打つ姿 (ti) を 1.15 倍にした「行き過ぎ」の鍵
を足す。打つ時刻は変えない (技の予告と合ったまま)。待ち・歩き・登場・倒れる・被弾は触らない。
二度かけないよう、かけたアニメは tools/gen/anim/punched.json に記す (アニメを書き直す道具を流したら、その名前を消してから流し直す)。

    python -I tools/gen/anim/punch_anims.py <名前> [<名前> ...]     例: stone_whale_heart tide_heart
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
ANIMS = ROOT / "src/main/resources/assets/skyabyss/animations/entity"
LOG = Path(__file__).resolve().parent / "punched.json"
SKIP = ("idle", "walk", "run", "intro", "death", "hurt", "flinch", "stagger", "fly", "swim", "sleep", "rest", "dormant")


def vec(v):
    if isinstance(v, list):
        return v
    if isinstance(v, dict) and isinstance(v.get("vector"), list):
        return v["vector"]
    return None


def setvec(old, new):
    if isinstance(old, list):
        return new
    out = dict(old)
    out["vector"] = new
    return out


def numeric(v):
    return v is not None and all(isinstance(x, (int, float)) for x in v)


def punch_anim(a):
    chans = []
    for bone, b in a.get("bones", {}).items():
        for ch in ("rotation", "position"):
            kv = b.get(ch)
            if isinstance(kv, dict) and len(kv) >= 2 and all(numeric(vec(v)) for v in kv.values()):
                chans.append(kv)
    if not chans:
        return False
    times = sorted({float(t) for kv in chans for t in kv})
    if len(times) < 3:
        return False
    best, ti, tw = 0.0, None, None
    for t0, t1 in zip(times[:-1], times[1:]):
        if t0 == 0.0 and t1 == times[1] and len(times) > 3:
            pass
        d = 0.0
        for kv in chans:
            k0, k1 = f"{t0:.2f}", f"{t1:.2f}"
            m = {f"{float(k):.2f}": vec(v) for k, v in kv.items()}
            if k0 in m and k1 in m:
                d += sum(abs(a1 - a0) for a0, a1 in zip(m[k0], m[k1]))
        # 最初の構えへの動き (0 → 最初の鍵) は溜めなので打つ瞬間にしない
        if d > best and t0 > 0.0:
            best, ti, tw = d, t1, t0
    if ti is None or best < 1.0:
        return False
    tt = round(tw + (ti - tw) * 0.55, 4)
    to = round(ti + 0.08, 4)
    nxt = [t for t in times if t > ti]
    over_ok = (not nxt or nxt[0] - ti > 0.11) and to < float(a.get("animation_length", 99))
    wind_ok = ti - tw > 0.1
    for kv in chans:
        m = {f"{float(k):.2f}": k for k in kv}
        kw, ki = m.get(f"{tw:.2f}"), m.get(f"{ti:.2f}")
        if kw is None or ki is None:
            continue
        if wind_ok:
            kv[f"{tt:.4f}"] = setvec(kv[kw], [round(x * 1.12, 3) for x in vec(kv[kw])])
        if over_ok:
            kv[f"{to:.4f}"] = setvec(kv[ki], [round(x * 1.15, 3) for x in vec(kv[ki])])
    for kv in chans:
        items = sorted(kv.items(), key=lambda it: float(it[0]))
        kv.clear()
        kv.update(items)
    return True


def main(names):
    done = json.loads(LOG.read_text(encoding="utf-8")) if LOG.exists() else {}
    for name in names:
        p = ANIMS / f"{name}.animation.json"
        d = json.loads(p.read_text(encoding="utf-8"))
        hit = []
        for key, a in d["animations"].items():
            short = key.split(".")[-1]
            if a.get("loop") in (True, "loop") or any(short.startswith(s) for s in SKIP) or key in done.get(name, []):
                continue
            if punch_anim(a):
                hit.append(key)
        if hit:
            raw = p.read_text(encoding="utf-8")
            # 元の字下げ・末尾の改行のまま書く (差分を小さく)
            ind = len(raw.split("\n", 2)[1]) - len(raw.split("\n", 2)[1].lstrip(" ")) if "\n" in raw else None
            p.write_text(json.dumps(d, ensure_ascii=False, indent=ind or None) + ("\n" if raw.endswith("\n") else ""), encoding="utf-8")
            done.setdefault(name, []).extend(hit)
        print(name, len(hit), "anims")
    LOG.write_text(json.dumps(done, ensure_ascii=False, indent=1), encoding="utf-8")


if __name__ == "__main__":
    main(sys.argv[1:])
