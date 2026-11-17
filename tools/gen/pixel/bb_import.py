# -*- coding: utf-8 -*-
"""Blockbench の模型 (.bbmodel、箱とメッシュ) をゲームの GearLook 模型 (lowpoly/look/a/<名>.json + png) に書き出す。

模型は Blockbench で手で組む (tools/models/<名>.bbmodel)。ここは形を変えず、そのまま写すだけ。
- 単位は Blockbench と同じ 1 = 1/16 ブロック。原点は握り、+y が先。
- 2 倍の細かさの品（tools/models/x32/<名>.bbmodel）は、Blockbench では 1 = 1/32 ブロックで組む（1 画素 = 1 単位のまま）。
  書き出すときに座標を 0.5 倍して 1/16 ブロックに直す（ゲームの中の大きさは同じで、画素だけが 2 倍細かい）。
- 面の UV は bbmodel の UV をそのまま使う (テクスチャは bbmodel に埋め込んだ 1 枚)。
- 名前が _glow で終わる箱は光る (その面の絵を _glow.png にも写す)。
- 回転は Blockbench と同じ順 (x → y → z の順に回す。three.js の 'ZYX')。群の回転も外へ順にかける。

  python -I tools/gen/pixel/bb_import.py tools/models/shrine_staff.bbmodel
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
OUT = "look/a"


def rot_point(p, rot, origin):
    x, y, z = (p[i] - origin[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in rot)
    # x → y → z の順に回す
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + origin[0], y + origin[1], z + origin[2])


def face_corners(frm, to, face):
    """面の 4 隅。順は絵の 左上, 左下, 右下, 右上 (Blockbench の面の向きと同じ)"""
    (x0, y0, z0), (x1, y1, z1) = frm, to
    return {
        "north": [(x1, y1, z0), (x1, y0, z0), (x0, y0, z0), (x0, y1, z0)],
        "south": [(x0, y1, z1), (x0, y0, z1), (x1, y0, z1), (x1, y1, z1)],
        "east": [(x1, y1, z1), (x1, y0, z1), (x1, y0, z0), (x1, y1, z0)],
        "west": [(x0, y1, z0), (x0, y0, z0), (x0, y0, z1), (x0, y1, z1)],
        "up": [(x0, y1, z0), (x0, y1, z1), (x1, y1, z1), (x1, y1, z0)],
        "down": [(x0, y0, z1), (x0, y0, z0), (x1, y0, z0), (x1, y0, z1)],
    }[face]


def face_area(frm, to, face):
    d = [to[i] - frm[i] for i in range(3)]
    w, h = {"north": (d[0], d[1]), "south": (d[0], d[1]), "east": (d[2], d[1]), "west": (d[2], d[1]),
            "up": (d[0], d[2]), "down": (d[0], d[2])}[face]
    return abs(w * h)


def load_texture(tex):
    src = tex["source"]
    data = base64.b64decode(src.split(",", 1)[1])
    return Image.open(io.BytesIO(data)).convert("RGBA")


def main(path):
    scale = 0.5 if "x32" in Path(path).parts else 1.0
    bb = json.loads(Path(path).read_text(encoding="utf-8"))
    name = bb["name"]
    if len(bb["textures"]) != 1:
        sys.exit("テクスチャは 1 枚だけにする")
    tex = bb["textures"][0]
    img = load_texture(tex)
    uw, uh = tex.get("uv_width", img.width), tex.get("uv_height", img.height)
    sx, sy = img.width / uw, img.height / uh

    # 群の回転 (外側から順に)
    groups = {g["uuid"]: g for g in bb.get("groups", [])}
    parents = {}

    def walk(nodes, chain):
        for n in nodes:
            if isinstance(n, str):
                parents[n] = chain
            else:
                g = groups.get(n["uuid"], n)
                walk(n.get("children", []), [g] + chain)

    walk(bb.get("outliner", []), [])

    glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
    V, UV, T = [], [], []
    def add_glow(uvs):
        us, vs = [u for u, _ in uvs], [v for _, v in uvs]
        box = (int(math.floor(min(us) * sx)), int(math.floor(min(vs) * sy)),
               int(math.ceil(max(us) * sx)), int(math.ceil(max(vs) * sy)))
        glow.paste(img.crop(box), box[:2])

    for el in bb["elements"]:
        if not el.get("export", True) or el.get("visibility") is False:
            continue
        if el.get("type") == "mesh":
            # メッシュ: 面の頂点は外から見て反時計回り (Blockbench の並び)。四角は扇に割る
            rot = el.get("rotation", [0, 0, 0])
            origin = el.get("origin", [0, 0, 0])
            for f in el["faces"].values():
                if f.get("texture") is None or len(f["vertices"]) < 3:
                    continue
                keys = f["vertices"]
                pts = [rot_point([el["vertices"][k][i] + origin[i] for i in range(3)], rot, origin) for k in keys]
                for g in parents.get(el["uuid"], []):
                    pts = [rot_point(p, g.get("rotation", [0, 0, 0]), g.get("origin", [0, 0, 0])) for p in pts]
                uvs = [f["uv"][k] for k in keys]
                n0 = len(V)
                V.extend([[round(c * scale, 4) for c in p] for p in pts])
                UV.extend([[u / uw, v / uh] for u, v in uvs])
                for i in range(1, len(keys) - 1):
                    T.append([n0, n0 + i, n0 + i + 1])
                if el["name"].endswith("_glow"):
                    add_glow(uvs)
            continue
        if el.get("type", "cube") != "cube":
            continue
        inf = el.get("inflate", 0)
        frm = [c - inf for c in el["from"]]
        to = [c + inf for c in el["to"]]
        rot = el.get("rotation", [0, 0, 0])
        origin = el.get("origin", [0, 0, 0])
        is_glow = el["name"].endswith("_glow")
        for face, f in el["faces"].items():
            if f.get("texture") is None or face_area(frm, to, face) < 1e-9:
                continue
            if f.get("rotation", 0):
                sys.exit(f"{el['name']} {face}: UV の回転はまだ扱わない")
            u0, v0, u1, v1 = f["uv"]
            pts = [rot_point(p, rot, origin) for p in face_corners(frm, to, face)]
            for g in parents.get(el["uuid"], []):
                pts = [rot_point(p, g.get("rotation", [0, 0, 0]), g.get("origin", [0, 0, 0])) for p in pts]
            n0 = len(V)
            V.extend([[round(c * scale, 4) for c in p] for p in pts])
            UV.extend([[u0 / uw, v0 / uh], [u0 / uw, v1 / uh], [u1 / uw, v1 / uh], [u1 / uw, v0 / uh]])
            T.extend([[n0, n0 + 1, n0 + 2], [n0, n0 + 2, n0 + 3]])
            if is_glow:
                add_glow([(u0, v0), (u1, v1)])

    texname = OUT + "/" + name
    (ASSETS / "textures/lowpoly" / OUT).mkdir(parents=True, exist_ok=True)
    save(img, ASSETS / "textures/lowpoly" / (texname + ".png"))
    save(glow, ASSETS / "textures/lowpoly" / (texname + "_glow.png"))
    doc = {"format_version": 1, "name": name, "units": "1/16 block", "uv": "normalized, origin top-left (v down)",
           "texture": texname, "texture_size": [img.width, img.height],
           "note": "Made by hand in Blockbench (tools/models/%s%s.bbmodel), written by tools/gen/pixel/bb_import.py" % ("x32/" if scale != 1.0 else "", name),
           "bones": {"root": {"pivot": [0, 0, 0], "vertices": V, "uvs": UV, "triangles": T, "triangle_count": len(T)}}}
    (ASSETS / "lowpoly" / OUT / (name + ".json")).write_text(json.dumps(doc, separators=(",", ":")), encoding="utf-8")
    print(name, len(T), "tris")


def save(img, path):
    """Windows では、書いた直後のファイルを書き直すと開けないことがある（見張りのソフトが掴んでいる）。少し待って何度か試す"""
    import time
    for k in range(8):
        try:
            img.save(path)
            return
        except OSError:
            if k == 7:
                raise
            time.sleep(0.25 * (k + 1))


if __name__ == "__main__":
    for p in sys.argv[1:]:
        main(p)
