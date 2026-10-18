#!/usr/bin/env python3
"""Write the shot scripts that check the tide-back low-poly installs (docs/design/15_high_poly.md section 9.6).
    python tools/gen_lowpoly_tide_shots.py            -> tools/shots/lp_tide_<name>.txt
    ./gradlew runShots -Pscript=tools/shots/lp_tide_<name>.txt
Pictures land in run/shots/lp_tide_<name>/shots/lp_tide_<name>/. Each view/pose is shot with the mesh (lp_tide_...) and with the cube model (cubes_tide_...)."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_lowpoly_five_shots import HEAD, cam, pose  # noqa: E402

PATH = os.path.join("tools", "shots")


def views2(prefix, d, y, pitch=4):
    """front / three-quarter / side / back, mesh first and then cubes."""
    s = ""
    for mode, pre in (("on", "lp_tide_" + prefix), ("off", "cubes_tide_" + prefix)):
        s += "lowpoly %s\n" % mode
        s += cam(0, y, d, 180, pitch) + "wait 20\nshot %s_front\n" % pre
        s += cam(d * 0.7, y, d * 0.7, 135, pitch) + "wait 12\nshot %s_three_quarter\n" % pre
        s += cam(d, y, 0, 90, pitch) + "wait 12\nshot %s_side\n" % pre
        s += cam(0, y, -d, 0, pitch) + "wait 12\nshot %s_back\n" % pre
    return s + "lowpoly on\n"


def write(name, body, title):
    path = os.path.join(PATH, "lp_tide_%s.txt" % name)
    with open(path, "w", encoding="utf-8") as f:
        f.write(HEAD.format(title=title) + body + "quit\n")
    print("wrote", path)


def summon(t, y, tag="m", extra="NoAI:1b,"):
    return "cmd summon skyabyss:%s 0 %s 0 {%sRotation:[0f,0f],PersistenceRequired:1b,Invulnerable:1b,Tags:[\"%s\"]}\n" % (t, y, extra, tag)


def main():
    b = summon("reef_scuttler", -60) + cam(0, -60, 3, 180, 4) + "waitchunks\nwait 60\n"
    b += views2("scuttler_idle", 2.4, -59.6, 14)
    b += cam(0, -59.4, 1.1, 180, 15) + "wait 12\nshot lp_tide_scuttler_face\n"
    b += pose("lp_tide_scuttler", "@e[tag=m]", "attack", (3, 6, 9, 12, 16), 2.4, -59.6, 14)
    write("scuttler", b, "reef_scuttler low-poly: idle views, face, attack")

    b = summon("bubble_puffer", -59, extra="NoAI:1b,NoGravity:1b,") + cam(0, -59, 4, 180, 2) + "waitchunks\nwait 60\n"
    b += views2("puffer_idle", 3.4, -59, 2)
    b += cam(0, -58.2, 1.5, 180, 10) + "wait 12\nshot lp_tide_puffer_face\n"
    # inflated: a survival player within 2.8 blocks inflates it (spectators are ignored); resistance for the spines
    b += "cmd effect give @p minecraft:resistance infinite 5 true\n"
    for mode, pre in (("on", "lp_tide_puffer_inflated"), ("off", "cubes_tide_puffer_inflated")):
        b += "lowpoly %s\ncmd gamemode survival\n" % mode + cam(2.4, -59, 0.6, 105, 2) + "wait 30\nshot %s_a\n" % pre
        b += "wait 12\nshot %s_b\n" % pre
        b += cam(0.2, -59, 2.4, 180, 2) + "wait 10\nshot %s_front\n" % pre
        b += cam(2.4, -57.8, 0, 90, 40) + "wait 10\nshot %s_top\n" % pre
        b += "cmd gamemode spectator\n" + cam(0, -59, 6, 180, 2) + "wait 70\n"
    b += "lowpoly on\n"
    b += pose("lp_tide_puffer", "@e[tag=m]", "attack", (3, 6, 9, 12, 16), 3.2, -59, 2)
    write("puffer", b, "bubble_puffer low-poly: idle views, face, inflated (survival player nearby), attack")

    b = summon("tide_beckoner", -60) + cam(0, -58, 12, 180, 6) + "waitchunks\nwait 80\n"
    b += views2("beckoner_idle", 9, -58, 8)
    b += cam(0, -58.5, 5.5, 180, 8) + "wait 12\nshot lp_tide_beckoner_face\n"
    for act, ticks in (("slam_raise", (4, 8, 14)), ("slam_strike", (3, 6, 10)), ("beckon", (6, 12, 20)), ("breath", (6, 14)), ("roar", (6, 14))):
        b += pose("lp_tide_beckoner", "@e[tag=m]", act, ticks, 9, -58, 8, cx=9)
    write("beckoner", b, "tide_beckoner low-poly: idle views, face, slam/beckon/breath/roar")

    b = "wait 400\n"
    for ph, hp in ((1, 540), (2, 297), (3, 108)):
        b += "cmd kill @e[type=!minecraft:player,distance=..80]\n"
        b += "lowpoly on\n" + summon("tide_heart", -57, extra="Health:%df," % hp) + cam(0, -56, 9, 180, 0) + "waitchunks\nwait 80\n"
        b += "wait %d\n" % {1: 0, 2: 200, 3: 420}[ph]  # an AI heart roars into the next phase by itself (NoAI stays in phase 1)
        for mode, pre in (("on", "lp_tide_heart_p%d" % ph), ("off", "cubes_tide_heart_p%d" % ph)):
            b += "lowpoly %s\n" % mode
            b += cam(0, -56, 9, 180, 0) + "wait 20\nshot %s_front\n" % pre
            b += cam(5, -55, 6, 150, 10) + "wait 12\nshot %s_three_quarter\n" % pre
            b += cam(9, -56, 0, 90, 0) + "wait 12\nshot %s_side\n" % pre
            b += cam(0, -56, -9, 0, 0) + "wait 12\nshot %s_back\n" % pre
        b += "lowpoly on\n"
        acts = {1: (("roar_1", (6, 14)), ("shell_strike", (6, 12))), 2: (("roar_2", (6, 14)), ("flood", (8, 16))),
                3: (("moon_wave", (8, 16)), ("pearl_rain", (8, 16)))}[ph]
        for act, ticks in acts:
            b += pose("lp_tide_heart_p%d" % ph, "@e[tag=m]", act, ticks, 9, -56, 0)
    b += "cmd kill @e[type=!minecraft:player,distance=..80]\n" + cam(0, -57, 11, 180, -6) + "wait 10\n"
    for mode, pre in (("on", "lp_tide_heart"), ("off", "cubes_tide_heart")):
        b += "lowpoly %s\ncmd kill @e[type=!minecraft:player,distance=..80]\n" % mode
        b += "cmd summon skyabyss:tide_heart 0 -59 0 {Rotation:[0f,0f],PersistenceRequired:1b}\n"
        for t in (8, 24, 40, 56, 72):
            b += "wait %d\nshot %s_intro_%02d\n" % (8 if t == 8 else 16, pre, t)
        b += "wait 20\ncmd kill @e[type=skyabyss:tide_heart]\n"
        last = 0
        for t in (6, 24, 44, 64, 84, 98):
            b += "wait %d\nshot %s_death_%02d\n" % (t - last, pre, t)
            last = t
    b += "lowpoly on\ncmd kill @e[type=!minecraft:player,distance=..80]\n"
    write("heart", b, "tide_heart low-poly: phases 1-3 views and attack poses, intro, death")


if __name__ == "__main__":
    main()
