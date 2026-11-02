# -*- coding: utf-8 -*-
"""密度上げ (2026-11-02 監査 項目 8) の共通部品。

既存のラフ (群の道具 ch1/ck.py の build) を読み、同じ骨・同じ色の材質で二次的な形 (板・関節の輪・肋・毛束・ひび・縁取り・光る点) を足す。
輪郭・配色・骨の名前は変えない（既存のアニメがそのまま動く）。

使い方 (1 体 = 1 ファイル densify_1102/<id>.py):
    from dens import run_mob
    def extra(o, F, eyes):   # o = 元のラフのモジュール (BONES / PAL / 形の道具を持つ)
        ...; return F
    run_mob("shadow_stitcher", "moon/shadow_stitcher.py", extra)

出力: docs/review/roughs_1102_densify/<id>/{before,after}/ に <id>_<key>_{mesh,atlas,glowmask}.* と
      <id>_<key>_mesh_closed.json（tools/lib/mesh_close.py で水密にしたもの。取り込みはこの形になる）
      docs/review/roughs_1102_densify/<id>.png（前・横・姿勢の比べ絵。左が元、右が密度を上げた案）
"""
from __future__ import annotations

import collections
import importlib.util
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
LOW = HERE.parent                      # tools/rough/lowpoly
ROOT = HERE.parents[3]
TOOLS = ROOT / "tools"
RES = ROOT / "src/main/resources/assets/skyabyss/lowpoly"
OUT = ROOT / "docs/review/roughs_1102_densify"
sys.path.insert(0, str(TOOLS / "lib"))
sys.path.insert(0, str(TOOLS / "audit"))

GROUP_BEST = {"moon": 2656, "cloud": 2652, "abyssB": 1918, "abyssA": 1466, "ch3": 1090,
              "ch4": 1166, "foes": 1978, "ch1": 1456, "ch2": 1182}


def load(path, name):
    """ラフの .py を名前を付けて読む。import 元の kit (ch1/ck) はその中で読まれる。"""
    p = Path(path)
    if not p.is_absolute():
        p = LOW / p
    d = str(p.parent)
    if d not in sys.path:
        sys.path.insert(0, d)
    spec = importlib.util.spec_from_file_location(name, p)
    m = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(m)
    return m


def kit():
    """形と絵の道具 (ch1/ck.py)。群のモジュールを読んだあとで呼ぶ。"""
    import ck  # noqa: E402  (sys.path に ch1 が入っている)
    return ck


def installed(mid):
    """取り込み済みのメッシュ JSON（無ければ None）。abyss A は段ごとの _s1 を見る。"""
    for name in (mid, mid + "_s1", "abyss_" + mid + "_s1"):
        p = RES / f"{name}.json"
        if p.exists():
            return json.loads(p.read_text(encoding="utf-8"))
    return None


def close_data(data):
    import mesh_close as mc
    closed, stats = mc.close_mesh(data, with_collar=True)
    return closed, stats


def open_loops(data):
    """閉じていない輪の数（溶接後・骨ごと）。0 なら水密。"""
    import mesh_close as mc
    n = 0
    for b in data["bones"].values():
        V = np.array(b.get("vertices", []), float).reshape(-1, 3)
        T = np.array(b.get("triangles", []), int).reshape(-1, 3)
        if len(T) == 0:
            continue
        loops = mc.boundary_loops(V, T)[0]
        n += len(loops)
    return n


def tri_total(data):
    return sum(len(b.get("triangles", [])) for b in data["bones"].values())


def _export(ck, faces, size, path, sid, key, bones):
    return ck.export_mesh(faces, size, path, f"{sid}_{key}", f"{sid}_{key}_atlas.png", bones,
                          {"glowmask": f"{sid}_{key}_glowmask.png"})


def measure(ck, orig, key, hint):
    """書き出して閉じたときの三角形数（取り込み後の数と同じ基準）。"""
    import tempfile
    faces, eyes = orig.build(key)
    size, *_ = ck.build_atlas(faces, hint, 0)
    tmp = Path(tempfile.gettempdir()) / f"densify_measure_{key}.json"
    d = _export(ck, faces, size, tmp, "m", key, orig.BONES)
    closed, _ = close_data(json.loads(tmp.read_text(encoding="utf-8")))
    return tri_total(closed)


def choose(ck, orig, mid, hint):
    """取り込み済みの三角形数に一番近い案を選ぶ（moon の CHOICE・cloud・abyss の採用案と合わせる）。"""
    inst = installed(mid)
    if inst is None:
        return "a", None, None
    want = tri_total(inst)
    keys = [k for k in "abc" if hasattr(orig, "build")]
    best = None
    for k in keys:
        try:
            n = measure(ck, orig, k, hint)
        except Exception as e:  # 案が一つだけの群
            continue
        if best is None or abs(n - want) < abs(best[1] - want):
            best = (k, n)
    return best[0], best[1], want


def _sheet(ck, spec, rowsets, orig, res_pair, out_png):
    """元 (左) と密度を上げた案 (右) を姿勢ごとに並べる。"""
    F = ck.voxel.font
    rows = spec.get("rows")
    if not rows or any(len(r) < 7 for r in rows):
        rows = [("前", "idle", 212, 12, 300, (0, 16, 0), 6.0), ("横", "idle", 270, 4, 300, (0, 16, 0), 6.0)]
    rows = [tuple(r[:7]) for r in rows]
    PW = spec.get("pw", 420)
    GAP, X0, top = 14, 20, 96
    W = X0 * 2 + 2 * PW + GAP
    ph_tot = sum(30 + r[4] + 34 for r in rows)
    total = top + ph_tot + 80
    cv = Image.new("RGB", (W, total), (16, 16, 22))
    d = ImageDraw.Draw(cv)
    d.text((24, 14), f'{spec["id"]}  元 → 密度を上げた案', font=F(26), fill=ck.WH)
    d.text((24, 50), f'元 {res_pair[0]["closed"]} / 密度を上げた案 {res_pair[1]["closed"]} 三角形（水密後。取り込みと同じ数え方）', font=F(15), fill=ck.GR)
    y = top
    poses = orig.POSES
    for head, pose, yaw, pitch, ph, cen, sc in rows:
        d.text((X0, y), head, font=F(20), fill=ck.CY)
        y += 30
        for i, r in enumerate(res_pair):
            x = X0 + i * (PW + GAP)
            im = ck.render_view(r, r["atlas"], r["glow"], poses.get(pose, {}), orig.BONES, yaw, pitch, (PW, ph), cen, sc)
            cv.paste(im, (x, y))
            d.rectangle([x - 1, y - 1, x + PW, y + ph], outline=(60, 60, 78))
            lab = "元" if i == 0 else "密度を上げた案"
            d.text((x + 2, y + ph + 6), f'{lab}  {r["closed"]} 三角形（水密後）', font=F(17), fill=ck.CY)
        y += ph + 34
    cv = cv.crop((0, 0, W, y + 10))
    cv.save(out_png)


def run_mob(mid, orig_path, extra, letter=None, spec_hint=None):
    """元のラフを読み、採用案 (取り込み済みと同じ三角形数の案) に extra() を足して、before / after を書き出す。"""
    orig = load(orig_path, "orig_" + mid)
    ck = kit()
    ck.OUT = OUT
    inst = installed(mid)
    hint = (inst or {}).get("texture_size", [128, 128])[0] if inst else 128
    if spec_hint:
        hint = spec_hint
    if letter is None:
        letter, closed_n, want = choose(ck, orig, mid, hint)
    else:
        closed_n, want = None, (tri_total(inst) if inst else None)
    pal = _pal(orig, letter)
    r_before = ck.build_option(mid, "before", lambda _k: orig.build(letter), pal, orig.BONES, hint)
    r_after = ck.build_option(mid, "after", lambda _k: _with_extra(orig, letter, extra), pal, orig.BONES, hint)
    res = []
    for key, r in (("before", r_before), ("after", r_after)):
        p = OUT / mid / key / f"{mid}_{key}_mesh.json"
        data = json.loads(p.read_text(encoding="utf-8"))
        closed, stats = close_data(data)
        (p.parent / f"{mid}_{key}_mesh_closed.json").write_text(json.dumps(closed, separators=(",", ":"), ensure_ascii=False), encoding="utf-8")
        r["closed"] = tri_total(closed)
        r["open_loops"] = open_loops(closed)
        r["stats"] = stats
        res.append(r)
    spec = getattr(orig, "SPEC", {"id": mid})
    spec = dict(spec)
    spec["id"] = mid
    _sheet(ck, spec, None, orig, res, OUT / f"{mid}.png")
    summary = dict(id=mid, letter=letter, installed=want, before=res[0]["tris"], before_closed=res[0]["closed"],
                   after=res[1]["tris"], after_closed=res[1]["closed"], open_before=res[0]["open_loops"],
                   open_after=res[1]["open_loops"], buried_before=res[0]["buried"], buried_after=res[1]["buried"])
    (OUT / mid / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False))
    return summary


def _pal(orig, letter):
    """材質の表。群によっては PALS[案] の辞書（ch3 の鳥）。"""
    if hasattr(orig, "PAL"):
        return orig.PAL
    return orig.PALS[letter]


def _with_extra(orig, letter, extra):
    F, eyes = orig.build(letter)
    F = extra(orig, F, eyes)
    return F, eyes


def add(F, pid, faces):
    """部品の名前を付けて F に足す（重なりの検査のため）。faces が None なら何もしない。"""
    if not faces:
        return F
    for f in faces:
        f.part = pid
    F += [f for f in faces if f is not None]
    return F
