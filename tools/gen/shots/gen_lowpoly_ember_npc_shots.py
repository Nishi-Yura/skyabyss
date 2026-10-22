#!/usr/bin/env python3
"""Write the shot scripts that check the ember-back / NPC low-poly installs (docs/design/15_high_poly.md section 9.6).
    python tools/gen/shots/gen_lowpoly_ember_npc_shots.py   -> tools/shots/lp_ember_<name>.txt, lp_npc.txt, lp_watcher.txt
    ./gradlew runShots -Pscript=tools/shots/lp_ember_<name>.txt
Pictures land in run/shots/<script>/shots/<script>/. Each view/pose is shot with the mesh (lp_<group>_...) and with the cube model (cubes_<group>_...)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_lowpoly_five_shots import cam, pose  # noqa: E402
import gen_lowpoly_storm_shots as storm  # noqa: E402
from gen_lowpoly_storm_shots import mob, summon, views2  # noqa: E402


_write = storm.write


def write(script, body, title):
    # a fresh world plays the opening title for 360 ticks: let it pass before the first picture
    if script not in ("lp_ember_heart_p2", "lp_ember_heart_p3"):
        body = body.replace("waitchunks\n", "waitchunks\nwait 330\n", 1)
    _write(script, body, title)


storm.write = write  # mob() in the storm module writes through this too



def main():
    # ash thrall: shell (idle views, face, attack/hurt/shell_break/death) and flared; poker on socket_main
    b = summon("ash_thrall", -60) + cam(0, -60, 6.5, 180, 4) + "waitchunks\nwait 60\n"
    b += views2("ember", "thrall_idle", 4.5, -59.6)
    for mode, pre in (("on", "lp_ember_thrall_face"), ("off", "cubes_ember_thrall_face")):
        b += "lowpoly %s\n" % mode + cam(0.2, -59.9, 2.0, 180, 0) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    for act, ticks in (("attack", (3, 6, 9, 12, 16)), ("hurt", (3, 6)), ("shell_break", (6, 12, 20)), ("death", (6, 16, 30))):
        b += pose("lp_ember_thrall", "@e[tag=m]", act, ticks, 4.5, -59.6, 4)
    b += "cmd kill @e[tag=m]\n" + summon("ash_thrall", -60, extra="NoAI:1b,Flared:1b,BurnLeft:999999,") + "wait 30\n"
    b += views2("ember", "thrall_flared", 4.5, -59.6)
    for mode, pre in (("on", "lp_ember_thrall_flared_face"), ("off", "cubes_ember_thrall_flared_face")):
        b += "lowpoly %s\n" % mode + cam(0.2, -59.9, 2.0, 180, 0) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    for act, ticks in (("attack", (3, 6, 9, 12, 16)), ("death", (6, 16, 30))):
        b += pose("lp_ember_thrall_flared", "@e[tag=m]", act, ticks, 4.5, -59.6, 4)
    write("lp_ember_thrall", b, "ash_thrall low-poly: shell and flared, idle views, face, attack/hurt/shell_break/death")

    # ember moth
    mob("lp_ember_moth", "ember", "ember_moth", 2.8, -59.3, (0.9, -60.0, 1.4, 155, 5),
        [("attack", (3, 6, 9, 12)), ("hurt", (3, 6)), ("death", (6, 16, 30))],
        "ember_moth low-poly: idle views, face, attack/hurt/death", extra="NoAI:1b,NoGravity:1b,", dy=-1.3)

    # cinder roller: idle views and face (walking, legs out); then the ball: an AI roller + a survival player in range (3 to 14 blocks)
    # curls (16 ticks) and rolls (40 ticks): shot as a series for the mesh and then for the cubes
    b = summon("cinder_roller", -60) + cam(0, -60, 5, 180, 4) + "waitchunks\nwait 60\n"
    b += views2("ember", "roller_idle", 3.4, -60.9, 8)
    for mode, pre in (("on", "lp_ember_roller_face"), ("off", "cubes_ember_roller_face")):
        b += "lowpoly %s\n" % mode + cam(0.2, -61.0, 1.6, 180, 15) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    for act, ticks in (("hurt", (3, 6)), ("death", (6, 16, 30))):
        b += pose("lp_ember_roller", "@e[tag=m]", act, ticks, 3.4, -60.9, 8)
    for mode, pre in (("on", "lp_ember_roller_ball"), ("off", "cubes_ember_roller_ball")):
        b += "lowpoly %s\ncmd gamemode spectator\ncmd kill @e[type=skyabyss:cinder_roller]\nwait 10\n" % mode
        b += "cmd summon skyabyss:cinder_roller 0 -60 0 {Rotation:[0f,0f],PersistenceRequired:1b}\n"
        b += cam(0, -60, 4, 180, 8) + "cmd gamemode survival\n"
        for k, t in zip("abcdefgh", (6, 6, 6, 8, 8, 10, 10, 12)):
            b += "wait %d\nshot %s_%s\n" % (t, pre, k)
    b += "lowpoly on\ncmd gamemode spectator\ncmd kill @e[type=skyabyss:cinder_roller]\n"
    write("lp_ember_roller", b, "cinder_roller low-poly: idle views, face, hurt/death, curled ball (AI roller with a survival player)")

    # ash smith (4.5 blocks tall): hammer + tongs sockets
    mob("lp_ember_smith", "ember", "ash_smith", 11, -60, (0.5, -57.9, 6.0, 180, 3),
        [("slam", (6, 12, 18, 24)), ("breath", (8, 16, 28)), ("rage", (8, 16, 28)), ("quench", (8, 20)), ("death", (10, 30, 60))],
        "ash_smith low-poly: idle views, face, hammer/tongs, slam/breath/rage/quench/death", dy=2.0)

    # ember heart: one script (one world) per phase. NoAI keeps phase 1; for phases 2 and 3 an AI heart with low health roars into the phase
    # by itself, then it is frozen in place.
    acts = {1: (("breath", (6, 12, 20)), ("pulse", (6, 12, 20)), ("orb", (6, 12, 20))),
            2: (("floor", (6, 14, 24)), ("gale", (6, 14, 24)), ("pellet", (6, 14, 24))),
            3: (("embers", (6, 14, 24)), ("fading", (6, 14, 24)), ("veil", (6, 14, 24)))}
    for ph, hp in ((1, 790), (2, 430), (3, 150)):
        b = "lowpoly on\n" + summon("ember_heart", -57, extra=("NoAI:1b," if ph == 1 else "") + "Health:%df," % hp) + cam(0, -56, 9, 180, 0)
        b += "waitchunks\nwait %d\n" % (80 if ph == 1 else 400)
        if ph > 1:
            b += "cmd data merge entity @e[tag=m,limit=1] {NoAI:1b}\n"
        b += "cmd tp @e[tag=m,limit=1] 0 -57 0\nwait 20\n"
        for mode, pre in (("on", "lp_ember_heart_p%d" % ph), ("off", "cubes_ember_heart_p%d" % ph)):
            b += "lowpoly %s\n" % mode
            b += cam(0, -56, 9, 180, 0) + "wait 20\nshot %s_front\n" % pre
            b += cam(5, -55, 6, 150, 10) + "wait 12\nshot %s_three_quarter\n" % pre
            b += cam(9, -56, 0, 90, 0) + "wait 12\nshot %s_side\n" % pre
            b += cam(0, -56, -9, 0, 0) + "wait 12\nshot %s_back\n" % pre
            b += cam(0.8, -55.6, 3.6, 175, 4) + "wait 12\nshot %s_core\n" % pre
        b += "lowpoly on\n"
        for act, ticks in acts[ph]:
            b += pose("lp_ember_heart_p%d" % ph, "@e[tag=m]", act, ticks, 9, -56, 0)
        if ph == 1:
            b += "cmd execute as @e[type=!minecraft:player,distance=..80] run kill @s\n" + cam(0, -57, 11, 180, -6) + "wait 10\n"
            for mode, pre in (("on", "lp_ember_heart"), ("off", "cubes_ember_heart")):
                b += "lowpoly %s\ncmd execute as @e[type=!minecraft:player,distance=..80] run kill @s\n" % mode
                b += "cmd summon skyabyss:ember_heart 0 -59 0 {Rotation:[0f,0f],PersistenceRequired:1b}\n"
                for t in (8, 24, 40, 56, 72):
                    b += "wait %d\nshot %s_intro_%02d\n" % (8 if t == 8 else 16, pre, t)
                b += "wait 20\ncmd kill @e[type=skyabyss:ember_heart]\n"
                last = 0
                for t in (6, 24, 44, 64, 84, 98):
                    b += "wait %d\nshot %s_death_%02d\n" % (t - last, pre, t)
                    last = t
            b += "lowpoly on\ncmd execute as @e[type=!minecraft:player,distance=..80] run kill @s\n"
        write("lp_ember_heart_p%d" % ph, b, "ember_heart low-poly: phase %d views and attack poses%s" % (ph, ", intro, death" if ph == 1 else ""))

    # NPCs: storyteller and Isana (whale_chaser), daylight: four directions + face close-up
    b = ""
    fy = -60.55
    for name, short in (("storyteller", "storyteller"), ("whale_chaser", "isana")):
        b += ("cmd execute as @e[type=!minecraft:player,distance=..80] run kill @s\n" if short == "isana" else "") + summon(name, -60, extra="NoAI:1b,") + cam(0, -60, 4.5, 180, 4) + "wait 40\n"
        fy = -60.55 if short == "storyteller" else -60.2
        b += views2("npc", short + "_idle", 2.6, -60.9, 0)
        for mode, pre in (("on", "lp_npc_%s_face" % short), ("off", "cubes_npc_%s_face" % short)):
            b += "lowpoly %s\n" % mode + cam(0.3, fy, 1.5, 180, 0) + "wait 12\nshot %s\n" % pre
            b += cam(1.0, fy, 1.4, 155, 0) + "wait 8\nshot %s_side\n" % pre
        b += "lowpoly on\n"
    write("lp_npc", "waitchunks\n" + b,
          "storyteller and whale_chaser low-poly: idle views, face close-up (daylight)")

    # watcher: night, a narrow view at 14 and 40 blocks (the watcher is client-only and holds still: `watcher x y z yaw`). translucency kept.
    # The camera looks +z (yaw 0); the watcher stands at z = distance facing back (yaw 180).
    b = "cmd time set midnight\ncmd weather clear\n" + cam(0, -60.0, 0, 0, -1.5) + "waitchunks\nwait 40\n"
    for dist, fov in ((14, 30), (40, 18)):
        for mode, pre in (("on", "lp_watcher_%d" % dist), ("off", "cubes_watcher_%d" % dist)):
            b += "lowpoly %s\nwatcher 0 -60 %d 180\nfov %d\nwait 40\nshot %s\n" % (mode, dist, fov, pre)
    # twilight: the body can be seen against the sky (at midnight only the eye shows)
    b += "cmd time set 12900\nwatcher 0 -60 14 180\nfov 30\n"
    for mode, pre in (("on", "lp_watcher_dusk"), ("off", "cubes_watcher_dusk")):
        b += "lowpoly %s\nwait 30\nshot %s\n" % (mode, pre)
    # from the side and the back at 14 blocks
    for mode, pre in (("on", "lp_watcher_side"), ("off", "cubes_watcher_side")):
        b += "lowpoly %s\n" % mode + cam(8, -60.0, 14, 90, -1.5) + "wait 20\nshot %s\n" % pre
        b += cam(0, -60.0, 22, 180, -1.5) + "wait 20\nshot %s_back\n" % pre
    b += "fov 70\nlowpoly on\n"
    write("lp_watcher", b, "watcher low-poly: night, 14 and 40 blocks, dusk, side and back")

    # perf: 8 thralls (shell) + 8 moths + 8 rollers + 1 smith = 25 mobs (PERF lines in shots.log): mesh vs cube, three rounds each
    b = ""
    for i in range(8):
        b += "cmd summon skyabyss:ash_thrall %d -60 -6 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b%s}\n" % (-15 + 2 * i, ",Flared:1b,BurnLeft:999999" if i % 2 else "")
        b += "cmd summon skyabyss:ember_moth %d -59 -2 {NoAI:1b,NoGravity:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n" % (-15 + 2 * i)
        b += "cmd summon skyabyss:cinder_roller %d -60 2 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n" % (-15 + 2 * i)
    b += "cmd summon skyabyss:ash_smith -3 -60 10 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n"
    b += cam(-8, -58.5, 20, 180, 6) + "waitchunks\nwait 100\nshot lp_ember_perf_crowd\n"
    for rnd in range(3):
        b += "lowpoly on\nwait 40\nperf 300 ember25_on_%d\nlowpoly off\nwait 40\nperf 300 ember25_off_%d\n" % (rnd, rnd)
    b += "lowpoly on\n"
    write("lp_ember_perf", b, "ember mobs perf: 25 mobs, mesh vs cube")


if __name__ == "__main__":
    main()
