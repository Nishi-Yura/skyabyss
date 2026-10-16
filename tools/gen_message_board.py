"""言伝ての板 (skyabyss:message_board) の素材を作る。

使い方 (リポジトリ直下で実行):
    python tools/gen_message_board.py                 ラフ 3 案を作り、採用案 (RECOMMENDED) を src へ書く
    python tools/gen_message_board.py --option B      採用案を B にして src へ書く (A / B / C から選ぶ)
    python tools/gen_message_board.py --roughs-only   ラフだけ作る (src へは書かない)

出力:
    docs/review/board_roughs/option_{A,B,C}/          案ごとのモデル JSON とテクスチャ (32x32)
    docs/review/board_roughs/compare.png              比較シート (正面 3/4 とアイコン)
    docs/review/previews/message_board_preview.png    src の採用モデルを描いた確認用 (git には入れない)
    src/.../models/block/message_board.json           採用案のモデル (facing=north が正面 -Z)
    src/.../models/item/message_board.json            ブロックのモデルを親にする
    src/.../blockstates/message_board.json            facing の 4 向き
    src/.../textures/block/message_board_{front,side,top,paper,detail}.png  使う物だけ書く (32x32)

座標: モデルの単位 (1 単位 = 1/16 ブロック)。北向きのとき正面は -Z。
    footprint は x 0..16、z 5..11 の中に収める。y は 0..16。
テクスチャ: 32x32 (1 単位 = 2 画素)。uv は巨神の碑 (titan_waymark) と同じく 16 単位の座標で書く。
    板・柱・庇・紙・飾り (麻紐と鯨骨) の 5 枚。紙は立体から 0.5 単位浮かせてある。
何度実行しても同じ物ができる (乱数は使わず、画素の位置から決まる模様)。
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

REPO = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(REPO / "tools/rough"))
from voxel import Face, FACE_SHADE, render, sheet  # noqa: E402

ROOT = REPO / "src/main/resources/assets/skyabyss"
ROUGH_DIR = REPO / "docs/review/board_roughs"
PREVIEW = REPO / "docs/review/previews/message_board_preview.png"

RECOMMENDED = "A"
DIRS = ("north", "south", "east", "west", "up", "down")
MATS = ("front", "side", "top", "paper", "detail")
DEFAULT_KIND = {"front": "planks", "side": "post", "top": "shingle", "paper": "paper", "detail": "bone"}

# 碑と同じ BASE の display (持ち方・地面の置き方)
DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


# ---------------------------------------------------------------- 案の形 (北向き、正面 -Z)
# 共通の決まり: 板の正面は z=7、板の背は z=9、柱や庇の裏は z=9..11。紙は z=6.5..7 (0.5 単位浮く)。
# 同じ場所を取る立体どうしは重ならないように、面どうしを接させる。

def box(name, f, t, default, **over):
    faces = {d: default for d in DIRS}
    faces.update(over)
    return {"name": name, "f": tuple(f), "t": tuple(t), "faces": faces}


def option_a():
    """A: 二本柱の掲示板。板の上に小さな二段の庇 (木の屋根)。紙は三枚、白い骨の留め釘が一つ"""
    return [
        box("post_l", (2, 0, 9), (4, 12, 11), "side"),
        box("post_r", (12, 0, 9), (14, 12, 11), "side"),
        box("board", (1, 2, 7), (15, 12, 9), "side", north="front"),
        box("eave", (0, 12, 5), (16, 13, 11), "side", north="top", up="top"),
        box("eave_cap", (1, 13, 6), (15, 14, 10), "top"),
        box("slip_1", (3, 6, 6.5), (7, 10, 7), "paper"),
        box("slip_2", (9, 7, 6.5), (13, 11, 7), "paper"),
        box("slip_3", (6, 2.5, 6.5), (10, 6, 7), "paper"),
        box("peg_1", (4.5, 9, 6), (5.5, 10, 6.5), "detail:bone"),
    ]


def option_b():
    """B: 横長の一枚板を二本柱で支える。屋根なし。紙を四枚、麻紐で板に結んである"""
    return [
        box("post_l", (1, 0, 9), (3, 14, 11), "side"),
        box("post_r", (13, 0, 9), (15, 14, 11), "side"),
        box("board", (0, 4, 7), (16, 14, 9), "side", north="front"),
        box("slip_1", (2, 8, 6.5), (6, 12, 7), "paper"),
        box("slip_2", (7, 9, 6.5), (11, 13, 7), "paper"),
        box("slip_3", (11.5, 5, 6.5), (14.5, 9, 7), "paper"),
        box("slip_4", (3, 4.5, 6.5), (7, 7.5, 7), "paper"),
        box("twine_1", (2, 11.5, 6), (6, 12, 6.5), "detail:twine"),
        box("twine_2", (7, 12.5, 6), (11, 13, 6.5), "detail:twine"),
        box("twine_3", (11.5, 8.5, 6), (14.5, 9, 6.5), "detail:twine"),
        box("twine_4", (3, 7, 6), (7, 7.5, 6.5), "detail:twine"),
    ]


def option_c():
    """C: 中央の一本柱に大きめの板。上に釘掛けの横木、鯨骨の釘が二本、右端に小さな鉤"""
    return [
        box("post", (7, 0, 9), (9, 11, 11), "side"),
        box("board", (2, 5, 7), (14, 13, 9), "side", north="front"),
        box("rail", (3, 13, 7), (13, 14, 8), "top"),
        box("peg_1", (4, 13, 6), (5, 14, 7), "detail:bone"),
        box("peg_2", (10, 13, 6), (11, 14, 7), "detail:bone"),
        box("hook_arm", (13, 11, 5.5), (15, 11.5, 7), "detail:bone"),
        box("hook_tip", (14.5, 9.5, 5.5), (15, 11, 6), "detail:bone"),
        box("slip_1", (3, 6, 6.5), (7, 10.5, 7), "paper"),
        box("slip_2", (8.5, 7.5, 6.5), (12.5, 11.5, 7), "paper"),
        box("slip_3", (8.5, 5.5, 6.5), (12.5, 7.5, 7), "paper"),
    ]


OPTIONS = {"A": option_a, "B": option_b, "C": option_c}
TITLES = {
    "A": "A 二本柱と小さな庇",
    "B": "B 横長の板 (庇なし)",
    "C": "C 中央の一本柱と釘掛け",
}


# ---------------------------------------------------------------- 検査

def check(elems):
    """重なり (体積を持つ交差) と footprint を調べる。戻り値は (問題の一覧, 外形の箱)"""
    problems = []
    for i, a in enumerate(elems):
        for b in elems[i + 1:]:
            ov = [min(a["t"][k], b["t"][k]) - max(a["f"][k], b["f"][k]) for k in range(3)]
            if all(o > 1e-9 for o in ov):
                problems.append(f"overlap {a['name']} x {b['name']} vol={ov[0] * ov[1] * ov[2]:.3f}")
    lo = [min(e["f"][k] for e in elems) for k in range(3)]
    hi = [max(e["t"][k] for e in elems) for k in range(3)]
    if lo[0] < 0 or hi[0] > 16 or lo[1] < 0 or hi[1] > 16 or lo[2] < 5 or hi[2] > 11:
        problems.append(f"out of range: min={lo} max={hi} (x0..16, y0..16, z5..11)")
    return problems, lo, hi


# ---------------------------------------------------------------- 面の向き・uv

def uv_for(d, f, t):
    """uv を 16 単位の座標で返す (u0, v0, u1, v1)。v は上が小さい"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    if d in ("north", "south"):
        return [x0, 16 - y1, x1, 16 - y0]
    if d in ("east", "west"):
        return [z0, 16 - y1, z1, 16 - y0]
    return [x0, z0, x1, z1]


def corners(d, f, t):
    """面の一隅 (origin)、横の辺 (eu)、縦の辺 (ev)。voxel.load_geo と同じ向き"""
    x0, y0, z0 = f
    x1, y1, z1 = t
    w, h, dz = x1 - x0, y1 - y0, z1 - z0
    return {
        "north": ((x1, y0, z0), (-w, 0, 0), (0, h, 0)),
        "south": ((x0, y0, z1), (w, 0, 0), (0, h, 0)),
        "east": ((x1, y0, z1), (0, 0, -dz), (0, h, 0)),
        "west": ((x0, y0, z0), (0, 0, dz), (0, h, 0)),
        "up": ((x0, y1, z1), (w, 0, 0), (0, 0, -dz)),
        "down": ((x0, y0, z0), (w, 0, 0), (0, 0, dz)),
    }[d]


# ---------------------------------------------------------------- 絵 (32x32 の画素ごとに描く)

WOOD = (150, 110, 72)
SEAM = (74, 52, 34)
POST = (122, 90, 60)
SHINGLE = (108, 82, 58)
SHINGLE_SEAM = (62, 46, 36)
PAPER = (236, 226, 200)
PAPER_LINE = (184, 172, 150)
PAPER_EDGE = (206, 194, 168)
INK = (46, 128, 126)        # 青緑の墨 (伝承の書と同じ系統)
TWINE = (168, 132, 88)
BONE = (228, 216, 188)
BONE_SPOT = (196, 182, 156)


def hnoise(i, j):
    """画素の位置から決まる 0..1 の模様 (乱数の代わり。毎回同じ物ができる)"""
    h = (i * 73856093) ^ (j * 19349663) ^ 0x5F3759DF
    h = (h * 2654435761) & 0xFFFFFFFF
    return ((h >> 8) & 0xFFFF) / 65535.0


def bevel(k, lu, lv, w, h):
    """面の縁を明るく・暗くして、立体の段を見せる (上と左は明るく、下と右は暗く)"""
    if lv < 0.5:
        return k * 1.12
    if h - lv < 0.5:
        return k * 0.78
    if lu < 0.5:
        return k * 1.04
    if w - lu < 0.5:
        return k * 0.86
    return k


def rgba(c, k=1.0):
    return tuple(min(255, int(x * k)) for x in c) + (255,)


def paint_px(kind, u, v, lu, lv, w, h):
    i, j = int(u * 2), int(v * 2)
    n = hnoise(i, j)
    edge = min(lu, w - lu, lv, h - lv)
    if kind == "planks":
        if v % 4.0 < 0.5 or n > 0.985:  # 板の継ぎ目と節
            return rgba(SEAM)
        return rgba(WOOD, bevel(0.94 + 0.12 * hnoise(0, j), lu, lv, w, h))
    if kind == "post":
        return rgba(POST, bevel(0.9 + 0.2 * hnoise(i, 0), lu, lv, w, h))
    if kind == "shingle":
        off = 1.0 if int(math.floor(v)) % 2 else 0.0
        if v % 1.0 < 0.12 or (u + off) % 2.0 < 0.12:
            return rgba(SHINGLE_SEAM)
        return rgba(SHINGLE, 0.94 + 0.12 * n)
    if kind == "paper":
        if edge < 0.25:
            return rgba(PAPER_EDGE)
        if n > 0.975:
            return rgba(INK)
        if v % 2.0 < 0.14 and 0.8 < lu < w - 0.8:  # 書きつけの罫線
            return rgba(PAPER_LINE)
        return rgba(PAPER, 0.97 + 0.06 * n)
    if kind == "twine":
        return rgba(TWINE, 0.85 + (0.25 if (v * 4.0) % 1.0 < 0.5 else 0.0))
    if kind == "bone":
        if n > 0.92:
            return rgba(BONE_SPOT)
        return rgba(BONE, 0.97 + 0.06 * n)
    raise ValueError(kind)


def paint_face(canvas, kind, uvr):
    u0, v0, u1, v1 = uvr
    w, h = u1 - u0, v1 - v0
    for py in range(int(round(v0 * 2)), int(round(v1 * 2))):
        for px in range(int(round(u0 * 2)), int(round(u1 * 2))):
            u, v = (px + 0.5) / 2, (py + 0.5) / 2
            canvas[py, px] = paint_px(kind, u, v, u - u0, v - v0, w, h)


# ---------------------------------------------------------------- モデル JSON と描画

def build(elems):
    """案の形から、モデル JSON と各素材の 32x32 の絵を作る"""
    used = set()
    canvases = {m: np.zeros((32, 32, 4), np.uint8) for m in MATS}
    for e in elems:
        for d in DIRS:
            mat, _, kind = e["faces"][d].partition(":")
            used.add(mat)
            paint_face(canvases[mat], kind or DEFAULT_KIND[mat], uv_for(d, e["f"], e["t"]))
    used.add("side")  # 粒子に使う
    model = {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "textures": {"particle": "skyabyss:block/message_board_side"},
        "elements": [],
        "display": DISPLAY,
    }
    for m in MATS:
        if m in used:
            model["textures"][m] = f"skyabyss:block/message_board_{m}"
    for e in elems:
        faces = {}
        for d in DIRS:
            mat = e["faces"][d].partition(":")[0]
            faces[d] = {"uv": uv_for(d, e["f"], e["t"]), "texture": f"#{mat}"}
        model["elements"].append({"from": list(e["f"]), "to": list(e["t"]), "faces": faces})
    return model, canvases, used


def write_textures(canvases, used, folder):
    folder.mkdir(parents=True, exist_ok=True)
    for m in MATS:
        p = folder / f"message_board_{m}.png"
        if m in used:
            Image.fromarray(canvases[m], "RGBA").save(p)
        elif p.exists():
            p.unlink()  # 使わなくなった素材の古い絵は消す


def faces_from_model(model, arrays):
    """モデル JSON (src の物でも、ラフの物でも) から、描く面の一覧を作る。arrays は素材名 -> 32x32 の絵"""
    out = []
    for el in model["elements"]:
        f, t = tuple(el["from"]), tuple(el["to"])
        for d, fc in el["faces"].items():
            u0, v0, u1, v1 = fc["uv"]
            arr = arrays[fc["texture"][1:]]
            tex = arr[int(round(v0 * 2)):int(round(v1 * 2)), int(round(u0 * 2)):int(round(u1 * 2))]
            o, eu, ev = corners(d, f, t)
            out.append(Face(np.array(o, float), np.array(eu, float), np.array(ev, float), tex, None, FACE_SHADE[d]))
    return out


def views(model, arrays, big=440, icon=200):
    faces = faces_from_model(model, arrays)
    front = render(faces, yaw=215, pitch=25, size=(big, big))
    inv = render(faces, yaw=225, pitch=30, size=(icon, icon))
    return front, inv


def write_model_folder(folder, model, canvases, used):
    folder.mkdir(parents=True, exist_ok=True)
    (folder / "message_board.json").write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_textures(canvases, used, folder / "textures")


def install(model, canvases, used):
    (ROOT / "models/block/message_board.json").write_text(json.dumps(model, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (ROOT / "models/item/message_board.json").write_text(
        json.dumps({"parent": "skyabyss:block/message_board"}, indent=2) + "\n", encoding="utf-8")
    variants = {"facing=north": {"model": "skyabyss:block/message_board"}}
    for name, y in (("east", 90), ("south", 180), ("west", 270)):
        variants[f"facing={name}"] = {"model": "skyabyss:block/message_board", "y": y}
    (ROOT / "blockstates/message_board.json").write_text(
        json.dumps({"variants": variants}, indent=2) + "\n", encoding="utf-8")
    write_textures(canvases, used, ROOT / "textures/block")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--option", choices=sorted(OPTIONS), default=RECOMMENDED, help="src へ入れる案 (既定: 推奨の A)")
    ap.add_argument("--roughs-only", action="store_true", help="ラフ (docs) だけ作り、src へは書かない")
    args = ap.parse_args()

    cols = []
    chosen = None
    for key in ("A", "B", "C"):
        elems = OPTIONS[key]()
        problems, lo, hi = check(elems)
        print(f"[{key}] bbox min={lo} max={hi} problems={problems or 'none'}")
        if problems:
            raise SystemExit(f"option {key} has problems: {problems}")
        model, canvases, used = build(elems)
        folder = ROUGH_DIR / f"option_{key}"
        write_model_folder(folder, model, canvases, used)
        front, inv = views(model, {m: canvases[m] for m in MATS})
        cols.append((TITLES[key], [front, inv]))
        if key == args.option:
            chosen = (model, canvases, used, lo, hi)

    ROUGH_DIR.mkdir(parents=True, exist_ok=True)
    sh = sheet("言伝ての板 ラフ 3 案", cols,
               note="上段は正面から斜め (北向き、正面は手前)。下段はアイコンの見え方。")
    sh.save(ROUGH_DIR / "compare.png")
    print("wrote", ROUGH_DIR / "compare.png")

    if args.roughs_only:
        return
    model, canvases, used, lo, hi = chosen
    install(model, canvases, used)
    print(f"installed option {args.option}; bbox min={lo} max={hi}")

    # 確認用: src に入った物から描き直す (入れた物がそのまま描けることを見る)
    tex_dir = ROOT / "textures/block"
    src_model = json.loads((ROOT / "models/block/message_board.json").read_text(encoding="utf-8"))
    arrays = {}
    for name, ref in src_model["textures"].items():
        if name == "particle":
            continue
        arrays[name] = np.array(Image.open(tex_dir / f"{ref.split('/')[-1]}.png").convert("RGBA"))
    front, inv = views(src_model, arrays, big=520, icon=260)
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    canvas = Image.new("RGB", (front.width + inv.width, max(front.height, inv.height)), (18, 18, 26))
    canvas.paste(front, (0, 0))
    canvas.paste(inv, (front.width, 0))
    canvas.save(PREVIEW)
    print("wrote", PREVIEW)


if __name__ == "__main__":
    main()
