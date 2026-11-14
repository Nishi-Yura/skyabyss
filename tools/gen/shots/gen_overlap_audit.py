# -*- coding: utf-8 -*-
"""画面の文字と部品の重なりの検査の台本 tools/shots/overlap_audit.txt を書く (2026-11-02)。
  python tools/gen/shots/gen_overlap_audit.py
撮影: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/overlap_audit.txt -PshotsName=overlap_audit
結果: run/shots/overlap_audit/shots/overlap_audit/overlap.txt (client/dev/OverlapAudit)"""
import io
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OPENS = ["inventory", "travel_map", "lore", "quests", "growth", "journey", "profile", "skills", "skills_node:cheap_thunder_bolt", "skills_build",
         "skills_tryon", "skills_deep", "skills_past", "skills_ext", "job_choice", "party", "rebirth", "wheel", "notices",
         "pouchwheel_9", "tower_desk", "chat", "jp.revati.skyabyss.client.box.BoxPouchScreen", "party_finder",
         "shrine_demo", "trial_board_demo", "heart_reward_demo"]
CMDS = [("shop_" + s, "skyabyss facility shop " + s) for s in ["apothecary", "food_shop", "material_shop", "outfit_shop", "smithy", "tool_shop", "tower",
                                                                 "black_market"]]
# guide は町の場面 (CinematicScreen) を始めることがあるので最後に。働きのあとは場面を止める
CMDS += [(a, "skyabyss facility act " + a) for a in ["stash", "guild", "records", "boxes", "trade", "mount_shop", "tower_shop", "secret_arts",
                                                       "journal", "fishing_board", "job_room", "map", "buyback", "circuit", "guide"]]
# 窓と GUI の倍率: 1920x1080 の GUI 4 (480x270)・GUI 2 (960x540)、1280x720 の GUI 3 (427x240)
PASSES = (("1920 1080", 4), ("1920 1080", 2), ("1280 720", 3))


def main():
    L = ["# 画面の文字と部品の重なりの検査 (2026-11-02 の声「UI がたまにおかしいものある。文字や要素が重なったり」)。client/dev/OverlapAudit が overlap.txt に書く。",
         "# 窓 1920x1080 の GUI 4 (480x270)・GUI 2 (960x540)、窓 1280x720 の GUI 3 (427x240) で、開ける画面を全部開く",
         "# bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/overlap_audit.txt -PshotsName=overlap_audit",
         "# 台本は tools/gen/shots/gen_overlap_audit.py が書く",
         "world overlap_audit flat", "size 1920 1080", "timeout 1500", "render 4", "clouds off", "guiscale 4", "",
         "cmd time set noon", "cmd weather clear", "cmd gamemode survival", "cmd skyabyss level set @p 50", "cmd skyabyss job choose mage",
         "cmd skyabyss facility coins 5000", "cmd skyabyss quest start @s main_song",
         "hud on", "camera 0 -60 8 180 0", "waitchunks", "chat clear", "wait 40"]
    for win, gs in PASSES:
        tag = "%s_g%d" % (win.split()[0], gs)
        L += ["", "# ---- %s GUI %d" % (win, gs), "window " + win, "wait 10", "guiscale %d" % gs, "wait 10"]
        for o in OPENS:
            name = o.replace(":", "_").rsplit(".", 1)[-1]
            L += ["open " + o, "wait 8", "overlap %s_%s" % (name, tag), "shot %s_%s" % (name, tag), "close", "wait 2"]
        # 装飾の画面はサーバーが開く (つまみを押したのと同じ)
        L += ["openacc", "wait 12", "overlap accessory_%s" % tag, "shot accessory_%s" % tag, "close", "wait 2"]
        # 旅袋 (キー) と共鳴の炉 (コマンド) もサーバーが開く
        L += ["key skyabyss_pouch.key.open", "wait 12", "overlap pouch_%s" % tag, "shot pouch_%s" % tag, "close", "wait 2"]
        L += ["cmd skyabyss_forge open", "wait 12", "overlap forge_%s" % tag, "shot forge_%s" % tag, "close", "wait 2"]
        # 会話の帯 (道具屋の付き人を呼んで話しかける)
        L += ['cmd execute at @s run summon skyabyss:attendant ~ ~ ~3 {NoAI:1b,Role:"tool_merchant",Variant:1,Rotation:[180f,0f],PersistenceRequired:1b}',
              "wait 5", 'cmd skyabyss dev talk "skyabyss:attendant_tool_merchant"', "wait 12", "overlap dialogue_%s" % tag, "shot dialogue_%s" % tag, "close", "wait 2",
              "cmd kill @e[type=skyabyss:attendant]"]
        for name, c in CMDS:
            L += ["cmd skyabyss cinematic stop", "cmd " + c, "wait 12", "overlap %s_%s" % (name, tag), "shot %s_%s" % (name, tag), "close", "wait 2"]
    io.open(ROOT / "tools/shots/overlap_audit.txt", "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
    print(len(L), "lines")


if __name__ == "__main__":
    main()
