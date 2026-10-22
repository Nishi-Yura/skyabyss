"""Writes tools/shots/titan_solid.txt: close / below / side shots of the four titans (docs/review/previews/titan_solid_sheet.png)."""
import os

views = {
    'whale': ('stone_whale', 'whale', [('close', '^30 ^12 ^20 facing ^0 ^14 ^20'), ('below', '^0 ^-3 ^20 facing ^0 ^30 ^20'), ('side', '^120 ^20 ^0 facing ^0 ^20 ^0')]),
    'tide': ('tide_shell', 'tide', [('close', '^40 ^12 ^20 facing ^0 ^14 ^20'), ('below', '^0 ^-3 ^20 facing ^0 ^30 ^20'), ('side', '^140 ^20 ^0 facing ^0 ^20 ^0')]),
    'storm': ('thunder_wing', 'storm', [('close', '^30 ^30 ^10 facing ^0 ^30 ^10'), ('below', '^0 ^2 ^10 facing ^0 ^40 ^10'), ('side', '^140 ^30 ^0 facing ^0 ^30 ^0')]),
    'ember': ('ember_titan', 'ember', [('close', '^50 ^30 ^0 facing ^0 ^30 ^0'), ('below', '^0 ^2 ^0 facing ^0 ^40 ^0'), ('side', '^180 ^40 ^0 facing ^0 ^40 ^0')]),
}
out = ["# Titan solidity sheet: close / below / side per titan. ./gradlew runShots -Pscript=tools/shots/titan_solid.txt -PshotsName=titan_solid",
       "world titan_solid flat", "size 1280 720", "timeout 1200", "render 16", "clouds off", "",
       "cmd time set 6000", "cmd weather clear", "cmd gamemode creative",
       "cmd skyabyss progress grant @p stone_whale", "cmd skyabyss progress grant @p tide_shell", "cmd skyabyss progress grant @p thunder_wing",
       "camera 0.5 -60 0.5 0 0", "waitchunks"]
for k, (ent, cmd, vs) in views.items():
    out += [f"cmd skyabyss titan summon {cmd}", "wait 60", f"cmd skyabyss titan rest {cmd} here", "wait 60", "hud off", "cmd gamemode spectator"]
    for n, tp in vs:
        out += [f"cmd execute as @e[type=skyabyss:{ent},limit=1] at @s run tp @p {tp}", "waitchunks", "wait 40",
                "titanweight off", "wait 6", f"shot {k}_{n}_before", "titanweight on", "wait 6", f"shot {k}_{n}_after"]
    out += [f"cmd kill @e[type=skyabyss:{ent}]", "wait 20", "cmd gamemode creative", "cmd tp @p 0 -50 0"]
out.append("quit")
here = os.path.dirname(os.path.abspath(__file__))
open(os.path.join(here, 'shots', 'titan_solid.txt'), 'w').write("\n".join(out) + "\n")
