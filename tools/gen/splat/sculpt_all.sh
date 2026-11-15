#!/usr/bin/env bash
# 設計から彫る点の模型を全部作り直す (tools/gen/splat/from_gen.py)。リポジトリ直下で: bash tools/gen/splat/sculpt_all.sh
set -u
S=tools/gen/splat/from_gen.py
run() { PYTHONIOENCODING=utf-8 timeout 900 python -I $S "$@" 2>&1 | tail -1 | cut -c1-80; }
G=tools/gen/mobs
for b in forge_giant bone_centipede bone_lancer crystal_lizard fort_warlord ice_lord jelly_priestess lava_toad shell_octopus temple_keeper thunder_owl; do
  run $G/dungeon_bosses_lp/$b.py
done
for f in ash_deer abyss_gecko carriage ember_fox great_lizard horn_beast shell_tortoise sky_ray star_lynx storm_chick whale_calf; do
  run $G/mounts_lp/$f.py --name mount_$f
done
for f in pit_digger bone_roller pit_foreman cave_jelly bell_hermit horn_beetle crystal_eye geode_golem bellows_imp lava_slug ox_smith; do
  run $G/dungeon_foes_lp/foes.py --func $f
done
for f in bell_hare moss_roller pearl_otter bolt_weasel ember_mole; do run $G/rare_beasts_lp/beasts.py --func $f; done
run $G/ember_jackal_lp/build.py --func ember_jackal
for f in salt_husk rib_guard lightning_idol; do run $G/foe_bodies_v2/foes_abyss.py --func $f; done
for f in bandit bandit_archer bandit_chief; do run $G/foe_bodies_v2/foes_bandit.py --func $f; done
for f in static_puff storm_ram ash_whirl core_shard tidepool_clam; do run $G/foe_bodies_v2/foes_beast.py --func $f; done
for f in drowned_sailor ember_keeper ash_striker; do run $G/foe_bodies_v2/foes_human.py --func $f; done
