#!/usr/bin/env python3
"""Contact sheets for the ember-back / NPC / watcher low-poly installs (docs/design/15_high_poly.md section 9.6).
Reads run/shots/<script>/shots/<script>/ (tools/gen/shots/gen_lowpoly_ember_npc_shots.py scripts) and writes
docs/review/previews/lp_ember_<name>_sheet.png, lp_npc_<name>_sheet.png, lp_watcher_sheet.png: the mesh above the cube model, same pose in the same column.
    python tools/sheets/lowpoly_ember_npc_sheets.py [sheet name ...]"""
import os
import sys

from PIL import Image, ImageDraw

OUT = os.path.join("docs", "review", "previews")
RUN = os.path.join("run", "shots")
W, H = 400, 225
COLS = 5


def idle(m, short=None):
    return [m + "_idle_front", m + "_idle_three_quarter", m + "_idle_side", m + "_idle_back", m + "_face"]


E = ("lp_ember_", "cubes_ember_")
# sheet name -> (script dir, (mesh prefix, cube prefix), stems); a stem may be (script, stem)
SHEETS = {
    "lp_ember_thrall": ("lp_ember_thrall", E,
                        idle("thrall") + ["thrall_attack_09", "thrall_hurt_03", "thrall_shell_break_12", "thrall_death_16", "thrall_flared_face",
                                          "thrall_flared_front", "thrall_flared_three_quarter", "thrall_flared_side", "thrall_flared_back",
                                          "thrall_flared_attack_09"]),
    "lp_ember_moth": ("lp_ember_moth", E,
                      idle("ember_moth") + ["ember_moth_attack_06", "ember_moth_hurt_03", "ember_moth_death_16"]),
    "lp_ember_roller": ("lp_ember_roller", E,
                        idle("roller") + ["roller_hurt_03", "roller_death_16"] + ["roller_ball_" + k for k in "abcdefgh"]),
    "lp_ember_smith": ("lp_ember_smith", E,
                       idle("ash_smith") + ["ash_smith_slam_12", "ash_smith_slam_24", "ash_smith_breath_16", "ash_smith_rage_16", "ash_smith_quench_20",
                                            "ash_smith_death_30"]),
    "lp_ember_heart": (None, E,
                       [("lp_ember_heart_p1", "heart_p1_front"), ("lp_ember_heart_p1", "heart_p1_three_quarter"), ("lp_ember_heart_p1", "heart_p1_core"),
                        ("lp_ember_heart_p1", "heart_p1_back"), ("lp_ember_heart_p1", "heart_p1_breath_12"),
                        ("lp_ember_heart_p2", "heart_p2_front"), ("lp_ember_heart_p2", "heart_p2_three_quarter"),
                        ("lp_ember_heart_p2", "heart_p2_core"), ("lp_ember_heart_p2", "heart_p2_back"), ("lp_ember_heart_p2", "heart_p2_floor_14"),
                        ("lp_ember_heart_p3", "heart_p3_front"), ("lp_ember_heart_p3", "heart_p3_three_quarter"),
                        ("lp_ember_heart_p3", "heart_p3_core"), ("lp_ember_heart_p3", "heart_p3_back"), ("lp_ember_heart_p3", "heart_p3_veil_14")]),
    "lp_ember_heart_cinematic": ("lp_ember_heart_p1", E,
                                 ["heart_intro_24", "heart_intro_56", "heart_death_24", "heart_death_64", "heart_death_98"]),
    "lp_npc_storyteller": ("lp_npc", ("lp_npc_", "cubes_npc_"),
                           idle("storyteller") + ["storyteller_face_side"]),
    "lp_npc_isana": ("lp_npc", ("lp_npc_", "cubes_npc_"),
                     idle("isana") + ["isana_face_side"]),
    "lp_watcher": ("lp_watcher", ("lp_", "cubes_"),
                   ["watcher_14", "watcher_40", "watcher_dusk", "watcher_side", "watcher_side_back"]),
}


def tile(path, label, full=False, gain=1):
    if os.path.exists(path):
        im = Image.open(path).convert("RGB")
        w, h = im.size
        if not full:
            im = im.crop((w // 4, h // 6, w * 3 // 4, h * 5 // 6))
        im = im.resize((W, H), Image.LANCZOS)
        if gain != 1:
            im = im.point(lambda v: min(255, int(v * gain)))
            label += " (x%d brightness)" % gain
    else:
        im = Image.new("RGB", (W, H), (40, 40, 40))
        label += " (missing)"
    ImageDraw.Draw(im).text((6, 4), label, fill=(255, 255, 0))
    return im


def main():
    os.makedirs(OUT, exist_ok=True)
    wanted = set(sys.argv[1:])
    for name, (script, (pm, pc), stems) in SHEETS.items():
        if wanted and name not in wanted:
            continue
        items = []
        for st in stems:
            sc, stem = st if isinstance(st, tuple) else (script, st)
            items.append((sc, pm + stem, pc + stem))
        rows = (len(items) + COLS - 1) // COLS
        cols = min(COLS, len(items))
        sheet = Image.new("RGB", (W * cols, H * 2 * rows))
        for i, (sc, m, c) in enumerate(items):
            x, y = (i % COLS) * W, (i // COLS) * H * 2
            full = name == "lp_watcher"
            gain = 3 if full and m.endswith(("_14", "_40")) else 1
            sheet.paste(tile(os.path.join(RUN, sc, "shots", sc, m + ".png"), "mesh " + m[3:], full, gain), (x, y))
            sheet.paste(tile(os.path.join(RUN, sc, "shots", sc, c + ".png"), "cube " + c[6:], full, gain), (x, y + H))
        path = os.path.join(OUT, name + "_sheet.png")
        sheet.save(path)
        print("wrote", path)


if __name__ == "__main__":
    main()
