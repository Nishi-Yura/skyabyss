# -*- coding: utf-8 -*-
"""四つの心臓の技の動きを、技ごとに 4 こま撮る台本 tools/shots/heart_anim_check.txt を書く (2026-11-15 の声「巨神の心臓のモデルのアニメーションがおかしくないか」)。
  python -X utf8 -I tools/gen/shots/gen_heart_anim_check.py
撮影: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/heart_anim_check.txt -PshotsName=heart_anim_check
並べる: python -X utf8 -I tools/sheets/heart_anim_sheet.py
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HEARTS = [
    ("stone_whale_heart", ["THRUST", "SWEEP", "LEAP", "CRACK", "RECLOSE", "VOLLEY", "BEAM", "CHAIN", "SHELL_DROP"]),
    ("tide_heart", ["BREATH", "SHELL_STRIKE", "WHIRL", "FLOOD", "BUBBLE", "PEARL", "EBB", "MOON_WAVE", "PEARL_RAIN"]),
    ("thunder_wing_heart", ["SUMMON", "BLADE", "CHAIN", "WINGBEAT", "CHARGE", "FEATHER", "FLASH", "WEB", "BARRAGE"]),
    ("ember_heart", ["BREATH", "PULSE", "ORB", "FLOOR", "GALE", "PELLET", "CHILL", "EMBERS", "FADING", "VEIL"]),
]
FRAMES = (6, 10, 10, 12)  # 技を出してからの待ち (合計 6, 16, 26, 38 tick)


def main():
    L = ["# 四つの心臓の技を技ごとに 4 こま (6・16・26・38 tick)。tools/gen/shots/gen_heart_anim_check.py が書く",
         "world heartanim flat", "size 1280 720", "timeout 2400", "render 8", "clouds off", "",
         "cmd time set noon", "cmd weather clear", "cmd gamemode creative", "cmd skyabyss playtest skip opening",
         "cmd fill -30 -61 -30 30 -61 30 minecraft:smooth_basalt", "hud off",
         "camera minecraft:overworld 0.5 -50 40 180 6", "waitchunks", "wait 260", "cmd skyabyss cinematic stop", "wait 30"]
    for heart, moves in HEARTS:
        L += ["", "# ---- " + heart, "cmd kill @e[type=!minecraft:player,distance=..80]", "wait 5",
              "cmd summon skyabyss:%s 0 -60 0 {PersistenceRequired:1b,Rotation:[0f,0f]}" % heart, "wait 10",
              "cmd skyabyss cinematic stop", "camera minecraft:overworld 0.5 -50 40 180 6", "wait 140"]
        for mv in moves:
            L += ["cmd skyabyss dev force @e[type=skyabyss:%s,limit=1] %s" % (heart, mv)]
            for k, w in enumerate(FRAMES):
                L += ["wait %d" % w, "shot %s_%s_%d" % (heart, mv.lower(), k)]
            L += ["wait 70"]
    out = ROOT / "tools/shots/heart_anim_check.txt"
    out.write_text("\n".join(L) + "\n", encoding="utf-8")
    print("wrote", out, len(L), "lines")


if __name__ == "__main__":
    main()
