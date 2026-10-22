#!/usr/bin/env python3
"""Write the shot scripts that check the storm-back + coral low-poly installs (docs/design/15_high_poly.md section 9.6).
    python tools/gen/shots/gen_lowpoly_storm_shots.py     -> tools/shots/lp_storm_<name>.txt, lp_coral_thrall.txt
    ./gradlew runShots -Pscript=tools/shots/lp_storm_<name>.txt
Pictures land in run/shots/<script>/shots/<script>/. Each view/pose is shot with the mesh (lp_<group>_...) and with the cube model (cubes_<group>_...)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_lowpoly_five_shots import HEAD, cam, pose  # noqa: E402

PATH = os.path.join("tools", "shots")


def views2(group, prefix, d, y, pitch=4):
    """front / three-quarter / side / back, mesh first and then cubes."""
    s = ""
    for mode, pre in (("on", "lp_%s_%s" % (group, prefix)), ("off", "cubes_%s_%s" % (group, prefix))):
        s += "lowpoly %s\n" % mode
        s += cam(0, y, d, 180, pitch) + "wait 20\nshot %s_front\n" % pre
        s += cam(d * 0.7, y, d * 0.7, 135, pitch) + "wait 12\nshot %s_three_quarter\n" % pre
        s += cam(d, y, 0, 90, pitch) + "wait 12\nshot %s_side\n" % pre
        s += cam(0, y, -d, 0, pitch) + "wait 12\nshot %s_back\n" % pre
    return s + "lowpoly on\n"


def write(script, body, title):
    path = os.path.join(PATH, script + ".txt")
    with open(path, "w", encoding="utf-8") as f:
        f.write(HEAD.format(title=title) + body + "quit\n")
    print("wrote", path)


def summon(t, y, extra="NoAI:1b,", x=0, z=0, tag="m"):
    return "cmd summon skyabyss:%s %s %s %s {%sRotation:[0f,0f],PersistenceRequired:1b,Invulnerable:1b,Tags:[\"%s\"]}\n" % (t, x, y, z, extra, tag)


def mob(script, group, name, d, y, face, actions, title, extra="NoAI:1b,", dy=0.4):
    b = summon(name, y, extra) + cam(0, y, d + 2, 180, 4) + "waitchunks\nwait 60\n"
    b += views2(group, name + "_idle", d, y + dy)
    for mode, pre in (("on", "lp_%s_%s_face" % (group, name)), ("off", "cubes_%s_%s_face" % (group, name))):
        b += "lowpoly %s\n" % mode + cam(*face) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    for act, ticks in actions:
        b += pose("lp_%s_%s" % (group, name), "@e[tag=m]", act, ticks, d, y + dy, 4)
    write(script, b, title)


def main():
    # feather warden: hook-spear (socket_main)
    mob("lp_storm_warden", "storm", "feather_warden", 4.5, -60, (0.2, -59.9, 2.0, 180, 0),
        [("attack", (3, 6, 9, 12, 16)), ("dive", (4, 8, 12, 16)), ("crouch", (6, 12)), ("stunned", (6, 14)), ("death", (6, 16, 30))],
        "feather_warden low-poly: idle views, face, attack/dive/crouch/stunned/death")
    # static moth: flying, hover (NoGravity)
    mob("lp_storm_moth", "storm", "static_moth", 3.4, -59.3, (0, -60.9, 1.2, 180, 0),
        [("attack", (3, 6, 9, 12)), ("death", (6, 16, 30))],
        "static_moth low-poly: idle views, face, attack, death", extra="NoAI:1b,NoGravity:1b,", dy=-1.4)
    # thunder supplicant: staff
    mob("lp_storm_supplicant", "storm", "thunder_supplicant", 4.5, -60, (0.2, -59.9, 2.0, 180, 0),
        [("cast", (4, 8, 14, 20)), ("attack", (3, 6, 9, 12)), ("death", (6, 16, 30))],
        "thunder_supplicant low-poly: idle views, face, staff, cast/attack/death")
    # raiju: wolf, mid-boss (no AI; poses via dev anim)
    mob("lp_storm_raiju", "storm", "raiju", 7.5, -60, (0.5, -60.2, 3.5, 180, 4),
        [("claw", (4, 8, 12, 16)), ("roar", (6, 14, 24)), ("pounce_crouch", (6, 12)), ("pounce_leap", (4, 8, 12)),
         ("rail_charge", (6, 14)), ("stun_fall", (6, 14, 30)), ("death", (6, 16, 30))],
        "raiju low-poly: idle views, face, claw/roar/pounce/rail_charge/stun/death")

    # moth charged: AI moth + survival player in range (resistance); the charge builds ~80 ticks, then it discharges onto the player
    b = "cmd effect give @p minecraft:resistance infinite 5 true\n" + cam(0, -60, 6, 180, 0) + "waitchunks\nwait 4\n"
    for mode, pre in (("on", "lp_storm_moth_charged"), ("off", "cubes_storm_moth_charged")):
        b += "lowpoly %s\ncmd gamemode spectator\ncmd kill @e[type=skyabyss:static_moth]\n" % mode
        b += summon("static_moth", -58.5, extra="NoGravity:1b,") + "cmd gamemode survival\n" + cam(0, -59.6, 5, 180, 0)
        for k, t in zip("abcdef", (30, 20, 15, 10, 10, 10)):
            b += "wait %d\nshot %s_%s\n" % (t, pre, k)
    b += "lowpoly on\ncmd gamemode spectator\n"
    write("lp_storm_moth_charged", b, "static_moth low-poly: charge light (survival player in range)")

    # storm heart: one script (one world) per phase, so a phase's heart never overlaps the previous one (a killed heart plays its death for 100 ticks).
    # NoAI keeps phase 1; for phases 2 and 3 an AI heart with low health roars into the phase by itself, then it is frozen in place.
    acts = {1: (("summon", (6, 12, 20)), ("blade", (6, 12, 20))), 2: (("wingbeat", (6, 14, 24)), ("feather", (6, 14, 24))),
            3: (("flash", (6, 14, 24)), ("web", (6, 14, 24)))}
    for ph, hp in ((1, 650), (2, 358), (3, 130)):
        b = "lowpoly on\n" + summon("thunder_wing_heart", -57, extra=("NoAI:1b," if ph == 1 else "") + "Health:%df," % hp) + cam(0, -56, 9, 180, 0)
        b += "waitchunks\nwait %d\n" % (80 if ph == 1 else 400)
        b += "cmd data merge entity @e[tag=m,limit=1] {NoAI:1b}\ncmd tp @e[tag=m,limit=1] 0 -57 0\nwait 20\n"
        for mode, pre in (("on", "lp_storm_heart_p%d" % ph), ("off", "cubes_storm_heart_p%d" % ph)):
            b += "lowpoly %s\n" % mode
            b += cam(0, -56, 9, 180, 0) + "wait 20\nshot %s_front\n" % pre
            b += cam(5, -55, 6, 150, 10) + "wait 12\nshot %s_three_quarter\n" % pre
            b += cam(9, -56, 0, 90, 0) + "wait 12\nshot %s_side\n" % pre
            b += cam(0, -56, -9, 0, 0) + "wait 12\nshot %s_back\n" % pre
            b += cam(0.8, -55.6, 3.6, 175, 4) + "wait 12\nshot %s_core\n" % pre
        b += "lowpoly on\n"
        for act, ticks in acts[ph]:
            b += pose("lp_storm_heart_p%d" % ph, "@e[tag=m]", act, ticks, 9, -56, 0)
        if ph == 1:
            b += "cmd kill @e[type=!minecraft:player,distance=..80]\n" + cam(0, -57, 11, 180, -6) + "wait 10\n"
            for mode, pre in (("on", "lp_storm_heart"), ("off", "cubes_storm_heart")):
                b += "lowpoly %s\ncmd kill @e[type=!minecraft:player,distance=..80]\n" % mode
                b += "cmd summon skyabyss:thunder_wing_heart 0 -59 0 {Rotation:[0f,0f],PersistenceRequired:1b}\n"
                for t in (8, 24, 40, 56, 72):
                    b += "wait %d\nshot %s_intro_%02d\n" % (8 if t == 8 else 16, pre, t)
                b += "wait 20\ncmd kill @e[type=skyabyss:thunder_wing_heart]\n"
                last = 0
                for t in (6, 24, 44, 64, 84, 98):
                    b += "wait %d\nshot %s_death_%02d\n" % (t - last, pre, t)
                    last = t
            b += "lowpoly on\ncmd kill @e[type=!minecraft:player,distance=..80]\n"
        write("lp_storm_heart_p%d" % ph, b, "thunder_wing_heart low-poly: phase %d views and attack poses%s" % (ph, ", intro, death" if ph == 1 else ""))

    # coral thrall + statue
    b = "cmd skyabyss tidemob tide low\n" + summon("coral_thrall", -60) + cam(0, -60, 6.5, 180, 4) + "waitchunks\nwait 60\n"
    b += views2("coral", "thrall_idle", 4.5, -59.6)
    for mode, pre in (("on", "lp_coral_thrall_face"), ("off", "cubes_coral_thrall_face")):
        b += "lowpoly %s\n" % mode + cam(0.2, -59.9, 2.0, 180, 0) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    for act, ticks in (("attack", (3, 6, 9, 12, 16)), ("wake", (6, 14, 24)), ("death", (6, 16, 30))):
        b += pose("lp_coral_thrall", "@e[tag=m]", act, ticks, 4.5, -59.6, 4)
    # the statue only exists while the tide is wet (it wakes at low tide), so hold the tide high
    b += "cmd kill @e[tag=m]\ncmd skyabyss tidemob tide high\n" + summon("coral_thrall", -60, extra="NoAI:1b,Statue:1b,") + "wait 30\n"
    b += views2("coral", "statue_idle", 4.5, -59.6)
    for mode, pre in (("on", "lp_coral_statue_face"), ("off", "cubes_coral_statue_face")):
        b += "lowpoly %s\n" % mode + cam(0.2, -59.9, 2.0, 180, 0) + "wait 12\nshot %s\n" % pre
    b += "lowpoly on\n"
    b += "cmd skyabyss tidemob tide cycle\n"
    write("lp_coral_thrall", b, "coral_thrall low-poly: idle views, face, attack/wake/death, statue variant")

    # perf: 8 feather wardens + 8 static moths + 8 thunder supplicants + 2 raiju = 26 mobs (PERF lines in shots.log): mesh vs cube, three rounds each
    b = ""
    for i in range(8):
        b += "cmd summon skyabyss:feather_warden %d -60 -6 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n" % (-15 + 2 * i)
        b += "cmd summon skyabyss:static_moth %d -59 -2 {NoAI:1b,NoGravity:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n" % (-15 + 2 * i)
        b += "cmd summon skyabyss:thunder_supplicant %d -60 2 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n" % (-15 + 2 * i)
    b += "cmd summon skyabyss:raiju -8 -60 8 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n"
    b += "cmd summon skyabyss:raiju 0 -60 8 {NoAI:1b,Rotation:[0f,0f],PersistenceRequired:1b}\n"
    b += cam(-8, -58.5, 16, 180, 6) + "waitchunks\nwait 100\nshot lp_storm_perf_crowd\n"
    for rnd in range(3):
        b += "lowpoly on\nwait 40\nperf 300 storm26_on_%d\nlowpoly off\nwait 40\nperf 300 storm26_off_%d\n" % (rnd, rnd)
    b += "lowpoly on\n"
    write("lp_storm_perf", b, "storm mobs perf: 26 mobs, mesh vs cube")


if __name__ == "__main__":
    main()
