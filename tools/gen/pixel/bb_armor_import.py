# -*- coding: utf-8 -*-
"""Blockbench で組んだ防具の一式 (.bbmodel) を、ゲームの look5 の防具 (lowpoly/look/a/armor/<組>/<部位>[_階級].json + 絵) に書き出す。

描画は LowPolyArmorRenderer が GearLook.armor = 'a' のとき、この模型を絵のまま貼って描く (点の模型は通らない)。
模型の無い組・階級は前の描き方 (lowpoly/armor/ と点の模型) のまま。階級の模型が無ければ無印を使う。

模型の決まり (tools/models/x32/armor/<組>[_rare|_legend].bbmodel):
- 2 倍の細かさ (1 単位 = 1/32 ブロック = 1 画素)。書き出しで 0.5 倍する。
- 一番外の群の名前が防具の骨 (armorHead / armorBody / armorRightArm / armorLeftArm /
  armorRightLeg / armorLeftLeg / armorRightBoot / armorLeftBoot)。中の群・箱は自由。
- 座標は Blockbench の見たまま (プレイヤーは -Z を向く。右腕は +X 側)。geo は x が逆なので、書き出しで x を反転し、面の向きも戻す。
  骨の pivot (geo、1/16): 頭・胴 (0,24,0)、右腕 (-5,22,0)、左腕 (5,22,0)、右脚・右靴 (-1.9,12,0)、左脚・左靴 (1.9,12,0)。
  Blockbench (x32) では x を反転して 2 倍: 右腕の付け根 = (10,44,0)。
- 名前が _glow で終わる箱と g_ で始まる箱は光る (その面の絵を _glowmask にも写す)。
- テクスチャは 1 枚。部位ごとに同じ絵を写す。

書き出すもの:
  lowpoly/look/a/armor/<組>/<部位><階級>.json
  textures/lowpoly/look/a/armor/<組>/<部位><階級>.png と _glow.png

  python -I tools/gen/pixel/bb_armor_import.py tools/models/x32/armor/stone_whale.bbmodel
"""
import base64
import io
import json
import math
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[3]
ASSETS = ROOT / "src/main/resources/assets/skyabyss"

PIV = {
    "armorHead": (0, 24, 0), "armorBody": (0, 24, 0),
    "armorRightArm": (-5, 22, 0), "armorLeftArm": (5, 22, 0),
    "armorRightLeg": (-1.9, 12, 0), "armorLeftLeg": (1.9, 12, 0),
    "armorRightBoot": (-1.9, 12, 0), "armorLeftBoot": (1.9, 12, 0),
}
PIECES = {
    "helmet": ["armorHead"],
    "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
    "leggings": ["armorRightLeg", "armorLeftLeg"],
    "boots": ["armorRightBoot", "armorLeftBoot"],
}
INSET = 0.02


def rot_point(p, rot, origin):
    x, y, z = (p[i] - origin[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in rot)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + origin[0], y + origin[1], z + origin[2])


def face_corners(frm, to, face):
    """面の 4 隅。順は絵の 左上, 左下, 右下, 右上 (外から見て反時計回り)"""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]


def load_texture(tex):
    src = tex["source"]
    return Image.open(io.BytesIO(base64.b64decode(src.split(",", 1)[1]))).convert("RGBA")


def uv_rect_corners(uv, rotation):
    u0, v0, u1, v1 = uv
    c = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]  # 左上, 左下, 右下, 右上
    k = (rotation or 0) // 90 % 4
    return c[k:] + c[:k] if k else c


def pick_texture(bb):
    """面が一番多く使っている絵（使っていない絵が残っていても選ばない）"""
    from collections import Counter
    c = Counter()
    for e in bb["elements"]:
        for fd in e.get("faces", {}).values():
            t = fd.get("texture")
            if t is not None and t is not False:
                c[t] += 1
    texs = bb["textures"]
    if not c:
        return texs[0]
    key = c.most_common(1)[0][0]
    if isinstance(key, int) and 0 <= key < len(texs):
        return texs[key]
    for t in texs:
        if t.get("uuid") == key or t.get("id") == str(key) or t.get("name") == key:
            return t
    return texs[0]


def main(path):
    path = Path(path)
    bb = json.loads(path.read_text(encoding="utf-8"))
    stem = path.stem  # <組>[_rare|_legend]
    tier = ""
    for t in ("_rare", "_legend"):
        if stem.endswith(t):
            stem, tier = stem[: -len(t)], t
    setname = stem
    tex = pick_texture(bb)
    img = load_texture(tex)
    uw, uh = tex.get("uv_width", img.width), tex.get("uv_height", img.height)
    sx, sy = img.width / uw, img.height / uh
    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))

    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    elements = {e["uuid"]: e for e in bb["elements"]}
    chains = {}  # 箱の uuid -> (骨名, 外から順の群)

    def walk(nodes, chain, bone):
        for n in nodes:
            if isinstance(n, str):
                chains[n] = (bone, chain)
            else:
                g = groups.get(n["uuid"], n)
                b = bone or g.get("name")
                walk(n.get("children", []), chain + [g], b)

    walk(bb["outliner"], [], None)

    bones = {}
    for uid, e in elements.items():
        if e.get("type", "cube") != "cube" or e.get("visibility") is False:
            continue
        bone, chain = chains.get(uid, (None, []))
        if bone not in PIV:
            sys.exit(f"箱 {e.get('name')} は防具の骨の群に入っていない ({bone})")
        frm, to = e["from"], e["to"]
        infl = e.get("inflate", 0)
        frm = [frm[i] - infl for i in range(3)]
        to = [to[i] + infl for i in range(3)]
        is_glow = e.get("name", "").endswith("_glow") or e.get("name", "").startswith("g_")
        b = bones.setdefault(bone, dict(parent=None, pivot=list(PIV[bone]), vertices=[], uvs=[], triangles=[]))
        for face, fd in e["faces"].items():
            if fd.get("texture") is None or "uv" not in fd:
                continue
            u0, v0, u1, v1 = fd["uv"]
            if abs(u1 - u0) < 1e-6 or abs(v1 - v0) < 1e-6:
                continue
            pts = face_corners(frm, to, face)
            if any(abs(a) > 1e-6 for a in e.get("rotation", [0, 0, 0])):
                pts = [rot_point(p, e["rotation"], e.get("origin", [0, 0, 0])) for p in pts]
            for g in reversed(chain):
                if any(abs(a) > 1e-6 for a in g.get("rotation", [0, 0, 0])):
                    pts = [rot_point(p, g["rotation"], g.get("origin", [0, 0, 0])) for p in pts]
            uvc = uv_rect_corners(fd["uv"], fd.get("rotation", 0))
            # 画素 -> 正規化。内側へ少し寄せる
            cu = sum(c[0] for c in uvc) / 4
            cv = sum(c[1] for c in uvc) / 4
            uvn = []
            for (u, v) in uvc:
                du, dv = cu - u, cv - v
                n = math.hypot(du * sx, dv * sy)
                k = min(INSET, n * 0.4) / n if n > 1e-9 else 0
                uvn.append([round((u + du * k) * sx / img.width, 5), round((v + dv * k) * sy / img.height, 5)])
            # x32 -> 1/16、x を反転 (geo)。反転で回りが逆になるので並びを逆にする
            gp = [[round(-p[0] * 0.5, 4), round(p[1] * 0.5, 4), round(p[2] * 0.5, 4)] for p in pts]
            gp.reverse()
            uvn.reverse()
            base = len(b["vertices"])
            b["vertices"] += gp
            b["uvs"] += uvn
            b["triangles"] += [[base, base + 1, base + 2], [base, base + 2, base + 3]]
            if is_glow:
                x0, x1 = sorted((u0 * sx, u1 * sx))
                y0, y1 = sorted((v0 * sy, v1 * sy))
                box = (int(round(x0)), int(round(y0)), int(round(x1)), int(round(y1)))
                glow.paste(img.crop(box), box[:2])

    outdir = ASSETS / "lowpoly/look/a/armor" / setname
    texdir = ASSETS / "textures/lowpoly/look/a/armor" / setname
    outdir.mkdir(parents=True, exist_ok=True)
    texdir.mkdir(parents=True, exist_ok=True)
    for piece, names in PIECES.items():
        pb = {n: bones[n] for n in names if n in bones}
        if not pb:
            continue
        total = 0
        for v in pb.values():
            v["triangle_count"] = len(v["triangles"])
            total += v["triangle_count"]
        name = piece + tier
        texname = f"look/a/armor/{setname}/{name}"
        data = {"format_version": 1, "name": f"{setname}_{name}",
                "units": "1/16 block, y up, front = -Z (geo absolute coordinates of the player armor bones)",
                "uv": "normalized, origin top-left (v down)", "texture": texname,
                "texture_size": [img.width, img.height], "texels_per_unit": 2.0, "uv_inset_texels": INSET,
                "bones": pb, "socket": False, "triangle_count": total, "skyabyss_closed": True,
                "source": f"tools/models/x32/armor/{path.name}"}
        (outdir / f"{name}.json").write_text(json.dumps(data, ensure_ascii=False), encoding="utf-8")
        img.save(texdir / f"{name}.png")
        glow.save(texdir / f"{name}_glow.png")
        print(f"{setname}/{name}: {total} tris")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        main(p)
