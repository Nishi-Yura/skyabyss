"""入っている品の低ポリメッシュ (src/main/resources/assets/skyabyss/lowpoly/items/<id>/<段>.json) を、種類ごとに 1 枚へ並べる。
色違いの使い回しを見つけるための目視用 (一品一案の見直し、2026-10-26)。
実行: python tools/rough/lowpoly/gear_style/installed_sheet.py <out_dir> [id ...]
"""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[4]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"


def font(sz):
    for p in ["C:/Windows/Fonts/YuGothB.ttc", "C:/Windows/Fonts/meiryob.ttc"]:
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            pass
    return ImageFont.load_default()


def load(item):
    d = ASSETS / "lowpoly/items" / item
    files = sorted(d.glob("*.json"))
    pick = [f for f in files if "pull" not in f.stem]
    f = pick[-1]
    m = json.loads(f.read_text(encoding="utf-8"))
    tex = Image.open(ASSETS / "textures/lowpoly" / (m["texture"] + ".png")).convert("RGBA")
    return m, np.array(tex), f.stem


def render(m, tex, yaw, size=(220, 300)):
    w, h = size
    vs, uvs, tris = [], [], []
    for b in m["bones"].values():
        off = len(vs)
        vs += b["vertices"]
        uvs += b["uvs"]
        tris += [[a + off for a in t] for t in b["triangles"]]
    V = np.array(vs, float)
    U = np.array(uvs, float)
    T = np.array(tris, int)
    c, s = np.cos(np.radians(yaw)), np.sin(np.radians(yaw))
    R = np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    pit = np.radians(12)
    P = np.array([[1, 0, 0], [0, np.cos(pit), -np.sin(pit)], [0, np.sin(pit), np.cos(pit)]])
    X = V @ R.T @ P.T
    lo, hi = X.min(0), X.max(0)
    sc = min((w - 20) / max(hi[0] - lo[0], 1e-3), (h - 20) / max(hi[1] - lo[1], 1e-3))
    mid = (lo + hi) / 2
    sx = (X[:, 0] - mid[0]) * sc + w / 2
    sy = h / 2 - (X[:, 1] - mid[1]) * sc
    zb = np.full((h, w), -1e9)
    img = np.zeros((h, w, 4), np.uint8)
    th, tw = tex.shape[:2]
    L = np.array([0.4, 0.7, -0.6])
    L /= np.linalg.norm(L)
    for t in T:
        a, b, cc = X[t]
        n = np.cross(b - a, cc - a)
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n /= nn
        uv = U[t].mean(0)
        col = tex[min(int(uv[1] * th), th - 1), min(int(uv[0] * tw), tw - 1)].astype(float)
        if col[3] < 10:
            continue
        shade = 0.55 + 0.45 * abs(n @ L)
        p = np.array([[sx[i], sy[i]] for i in t])
        x0, y0 = np.floor(p.min(0)).astype(int)
        x1, y1 = np.ceil(p.max(0)).astype(int)
        x0, y0, x1, y1 = max(x0, 0), max(y0, 0), min(x1, w - 1), min(y1, h - 1)
        if x1 < x0 or y1 < y0:
            continue
        gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + .5, np.arange(y0, y1 + 1) + .5)
        d = (p[1, 1] - p[2, 1]) * (p[0, 0] - p[2, 0]) + (p[2, 0] - p[1, 0]) * (p[0, 1] - p[2, 1])
        if abs(d) < 1e-9:
            continue
        l0 = ((p[1, 1] - p[2, 1]) * (gx - p[2, 0]) + (p[2, 0] - p[1, 0]) * (gy - p[2, 1])) / d
        l1 = ((p[2, 1] - p[0, 1]) * (gx - p[2, 0]) + (p[0, 0] - p[2, 0]) * (gy - p[2, 1])) / d
        l2 = 1 - l0 - l1
        ins = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
        z = l0 * a[2] * -1 + l1 * b[2] * -1 + l2 * cc[2] * -1
        sub = zb[y0:y1 + 1, x0:x1 + 1]
        ok = ins & (z > sub)
        sub[ok] = z[ok]
        reg = img[y0:y1 + 1, x0:x1 + 1]
        reg[ok] = np.array([*(np.clip(col[:3] * shade, 0, 255)), 255], np.uint8)
    return Image.fromarray(img)


GROUPS = {
    "大剣・剣": ["shrine_sword", "whalebone_greatsword", "ember_greatsword", "abyss_lamp_sword", "abyss_greatsword", "abyss_tide_sword",
              "abyss_storm_sword", "abyss_ember_sword"],
    "鎚・斧": ["shrine_hammer", "whale_hammer", "tide_hammer", "storm_hammer", "ember_war_axe", "smith_hammer", "abyss_whale_hammer",
             "abyss_tide_hammer", "abyss_storm_hammer", "abyss_sinking_anchor"],
    "杖": ["shrine_staff", "whale_staff", "tide_staff", "storm_staff", "ember_staff", "abyss_lamp_staff", "abyss_whale_staff", "abyss_tide_staff",
          "abyss_storm_staff", "abyss_ember_staff"],
    "鈴杖": ["shrine_bell_staff", "whale_bell_staff", "tide_bell_staff", "storm_bell_staff", "ember_censer", "abyss_bell_staff",
           "abyss_toll_bell_staff", "abyss_whale_bell_staff", "abyss_storm_bell_staff", "abyss_ember_bell_staff"],
    "弓": ["shrine_bow", "whale_bow", "tide_bow", "thunder_wing_bow", "ember_bow", "abyss_storm_bow", "abyss_depth_bow", "abyss_whale_bow",
          "abyss_tide_bow", "abyss_ember_bow"],
    "短剣・爪": ["shrine_dagger", "waterway_fang", "tidecaller_claw", "raiju_claw", "ember_dagger", "abyss_pulse_dagger", "abyss_whale_fang",
             "abyss_tide_dagger", "abyss_storm_dagger", "abyss_ember_dagger"],
    "長柄・ほか": ["captain_pike", "coral_harpoon", "tide_shell_trident", "warden_glaive", "thrall_poker", "abyss_ash_scythe",
              "abyss_surveyor_plumb", "coral_shield"],
}


def sheet(title, ids, out):
    cw, ch = 220, 300
    cols = 5
    rows = (len(ids) + cols - 1) // cols
    S = Image.new("RGB", (cols * cw * 2, rows * (ch + 30) + 50), (24, 22, 30))
    D = ImageDraw.Draw(S)
    D.text((12, 10), title, fill=(230, 220, 200), font=font(24))
    for k, i in enumerate(ids):
        try:
            m, tex, stem = load(i)
        except Exception as e:
            print("skip", i, e)
            continue
        x, y = (k % cols) * cw * 2, 50 + (k // cols) * (ch + 30)
        for j, yaw in enumerate((0, 40)):
            im = render(m, tex, yaw, (cw, ch))
            S.paste(im, (x + j * cw, y), im)
        D.text((x + 6, y + ch + 4), f"{i} ({stem})", fill=(200, 190, 170), font=font(15))
    S.save(out)


def main():
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    for n, (t, ids) in enumerate(GROUPS.items()):
        sheet(t, ids, out / f"{n}_{t}.png")
        print(t, flush=True)


if __name__ == "__main__":
    main()
