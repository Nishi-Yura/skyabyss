# 番兵 + 盾 (受け口 socket_off に合成) を描いて docs/roughs/v4/bone_sentinel.png に出す
import copy
import json
import sys
from pathlib import Path

ROOT = Path(r"C:\Users\nisimoto\Desktop\mod")
sys.path.insert(0, str(ROOT / "tools/rough"))
import voxel  # noqa: E402
from PIL import Image  # noqa: E402

A = ROOT / "src/main/resources/assets/skyabyss"
geo = json.loads((A / "geo/entity/bone_sentinel.geo.json").read_text(encoding="utf-8"))
gear = json.loads((A / "geo/gear/bone_sentinel_shield.geo.json").read_text(encoding="utf-8"))
anim = json.loads((A / "animations/entity/bone_sentinel.animation.json").read_text(encoding="utf-8"))["animations"]


def frame(name, t):
    out = {}
    for bone, ch in anim["animation.bone_sentinel." + name]["bones"].items():
        r = ch.get("rotation")
        if isinstance(r, dict):
            ks = sorted(r, key=float)
            v = r[ks[0]]
            for k in ks:
                if float(k) <= t + 1e-6:
                    v = r[k]
            ax, ay, az = (float(a) for a in v)
            # GeckoLib 4.9.3 (バイトコードで確認): geo の x を鏡写し (-x)、回転は x,y を反転して Z→Y→X の順。
            # ファイルの座標で描くと Rz(-az) Ry(ay) Rx(-ax) になる
            out[bone] = (-ax, ay, -az)
    return out


def composed(pose):
    # 番兵の面
    faces = voxel.load_geo(geo, A / "textures/entity/bone_sentinel.png", pose=pose)
    # 盾: 骨を受け口の子にして、立方体を受け口の pivot へ移す
    g = copy.deepcopy(geo)
    gb = g["minecraft:geometry"][0]
    gb["description"].update({k: gear["minecraft:geometry"][0]["description"][k] for k in ("texture_width", "texture_height")})
    sock = next(b for b in gb["bones"] if b["name"] == "socket_off")
    p = sock["pivot"]
    for b in gb["bones"]:
        b.pop("cubes", None)
    for b in gear["minecraft:geometry"][0]["bones"]:
        nb = copy.deepcopy(b)
        nb["name"] = "gear_" + b["name"]
        nb["parent"] = "socket_off" if "parent" not in b else "gear_" + b["parent"]
        nb["pivot"] = [a + c for a, c in zip(b.get("pivot", [0, 0, 0]), p)]
        for c in nb.get("cubes", []):
            c["origin"] = [a + q for a, q in zip(c["origin"], p)]
        gb["bones"].append(nb)
    faces += voxel.load_geo(g, A / "textures/gear/bone_sentinel_shield.png", pose=pose)
    return faces


rows = [("rest", {}), ("guard", frame("block", 0.25)),
        ("attack windup", frame("attack", 0.22)), ("attack strike", frame("attack", 0.38))]
views = [("front", 180, 8), ("front-left 3/4", 215, 18), ("left side", 270, 8), ("front-right 3/4", 145, 18)]
cols = []
for vname, yaw, pitch in views:
    ims = []
    for rname, pose in rows:
        # ゲーム内の見た目 = ファイルの座標の x の鏡写し。正射影なので、yaw を反転して描いた絵を左右反転すれば同じ
        im = voxel.render(composed(pose), yaw=-yaw, pitch=pitch, size=(340, 340)).transpose(Image.FLIP_LEFT_RIGHT)
        ims.append(im)
    cols.append((vname, ims))
sheet = voxel.sheet("bone_sentinel v4 (shield = gear at socket_off)", cols,
                    note="rows: rest / guard / attack windup (0.22s) / attack strike (0.38s)")
out = ROOT / "docs/roughs/v4/bone_sentinel.png"
out.parent.mkdir(parents=True, exist_ok=True)
sheet.save(out)
print(out, sheet.size)
print("overlaps sentinel:", voxel.check_overlaps(geo))
print("overlaps shield:", voxel.check_overlaps(gear))
