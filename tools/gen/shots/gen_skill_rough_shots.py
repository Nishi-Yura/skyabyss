# -*- coding: utf-8 -*-
"""技の見た目のラフ (今 0 / A / B / C) を同じ場面で撮る台本 tools/shots/skill_rough.txt を書く。
模型は tools/models/skill_rough/<案>/*.bbmodel (Blockbench) → BB_LOOK=<案> python -I tools/gen/pixel/bb_import.py で入れる。
    python -I tools/gen/shots/gen_skill_rough_shots.py [技 ...]        (職は技の名前から決まる。技なしは魔法使いの全部)
    python -I tools/gen/shots/gen_skill_rough_shots.py --job warrior   (その職の全部)
    ./gradlew runShots --offline --no-daemon -Pscript=tools/shots/skill_rough.txt [-PshotsName=skill_rough_warrior]
技を放ったあと、横の決まった位置へカメラを移して撮る (形は放った時の位置に残る)。
変化の節で出る技は、職の木 (skill_tree/<職>.json) から節の道筋を計算して skill node で順に取る。
"""
import json
import math
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
TREES = ROOT / "src/main/resources/assets/skyabyss/skill_tree"
LOOKS = ["0", "a"]

HEAD = """# 技の見た目のラフ (今 0 / A / B / C)。tools/gen/shots/gen_skill_rough_shots.py が書く
world skill_rough flat
size 1280 720
timeout 3000
render 6
clouds off
guiscale 2
cmd time set noon
cmd gamerule doWeatherCycle false
cmd weather clear
cmd gamemode survival
cmd gamerule doMobLoot false
cmd gamerule fallDamage false
camera 0.5 -60 0.5 0 0
hud off
cmd skyabyss cinematic stop
waitchunks
chat clear
wait 200
fov 70
cmd skyabyss level set @p 30
cmd skyabyss job choose {job}
cmd item replace entity @s weapon.mainhand with skyabyss:{weapon}
cmd skyabyss skill forget @s
perspective first
"""


def cam(x, y, z, tx, ty, tz, near=1.0):
    """(x y z) から (tx ty tz) を見るカメラ。yaw 0 = 南 (+z)。near < 1 で的へ寄る"""
    x, y, z = (round(t + (a - t) * near, 2) for a, t in ((x, tx), (y, ty), (z, tz)))
    dx, dy, dz = tx - x, ty - y, tz - z
    h = math.hypot(dx, dz)
    yaw = math.degrees(math.atan2(-dx, dz))
    pitch = math.degrees(math.atan2(-dy, h))
    return f"{x} {y} {z} {yaw:.0f} {pitch:.0f}"


# ---------------------------------------------------------------- 魔法使い
# (技, 放つ向きの pitch, 撮る位置 x y z yaw pitch, 撮る tick の並び)
MAGE = [
    ("frost_tide", 25, "8 -56.5 -3 47 19", [(3, "a")]),
    ("ember_orb", 0, "5 -58.5 7 48 2", [(1, "fly")]),
    ("ember_orb", 0, "5 -58 10 52 15", [(8, "hit")]),
    ("thunder_bolt", 15, "8 -56.5 4 49 15", [(1, "a"), (3, "b")]),
    ("blink", 0, "10 -56 2 67 16", [(2, "a")]),
    # 星屑は雷撃の刻印 1 (最後に置く。刻印は案ごとの覚え直しで外れる)
    ("stardust", 10, "8 -55.5 4 49 24", [(2, "a"), (3, "b"), (6, "c")]),
]


def mage(only):
    out = [HEAD.format(job="mage", weapon="shrine_staff")]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        # 案ごとに覚え直す。雷撃は極めておく (刻印は極めた技だけに付く)
        out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill learn @s thunder_bolt 3\n" + "".join(
            f"cmd skyabyss skill learn @s {k} 1\n" for k in ("star_reading", "ember_orb", "frost_tide", "blink")))
        for name, pitch, cm, shots in MAGE:
            if only and name not in only:
                continue
            if name == "stardust":  # 星屑は雷撃の変化の節 stardust_bolt (mage.json)。木の道筋を順に取る
                out.append("cmd skyabyss skill forget @s\n" + "".join(f"cmd skyabyss skill node @s {n}\n" for n in (
                    "thunder_bolt", "spi_1", "cheap_thunder_bolt", "star_reading", "cheap_blink", "spi_4", "stardust_bolt")))
            out.append(cast(pitch, "thunder_bolt" if name == "stardust" else name, cm))
            for w, tag in shots:
                out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            out.append("wait 40\n")
    return out


# ---------------------------------------------------------------- 戦士
# 技は足もとに出る (SkillProps.place)。体は南 (+z) を向いて放つので、前に伸びる技は z が正の側にある。
# (技, 枠に入れる技, 取る節の並び (None = dev prop で模型だけ置く), 撮る位置, 撮る tick の並び, 前に敵を置くか)
# 岩呼び・盾の踏み込み・鯨骨の振りは、近くに敵がいないと放てない (FAILED)。動かない・倒れない野盗を前に置く
WARRIOR = [
    ("titan_cleave", "titan_cleave", ["titan_cleave"], cam(8.5, -56.5, -3.5, 0.5, -59.5, 2.0, 0.65), [(5, "a")], False),
    ("rock_call", "rock_call", ["rock_call"], cam(7.5, -56, -6.5, 0.5, -59.5, 0.5, 0.65), [(6, "a")], True),
    ("whale_sweep", "whale_sweep", ["whale_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.65), [(5, "a")], False),
    ("shield_rush", "shield_rush", ["shield_rush"], cam(10.5, -56, -2.5, 0.5, -59, 3.5, 0.8), [(3, "a")], True),
    # 盾殴りは今の木では見た目が出ない (盾の踏み込みの刻印 1 は殴りの当たりだけで見た目は踏み込み。直に覚えても枠に入らない)。
    # 模型だけを dev prop で置いて撮る (大きさ 1。技の 1.3 より小さい)
    ("shield_bash", None, None, cam(8.5, -56.5, -3.5, 0.5, -59.5, 1.5, 0.65), [(4, "a")], False),
    # 鯨骨の振り = 巨神斬りの刻印 2 (deep_blade)、回し斬り = 鯨尾薙ぎの刻印 8 (spin_sweep)。技を極めてから刻印を取る
    ("bone_swing", "titan_cleave", ["titan_cleave_2", "titan_cleave_3", "deep_blade"], cam(8.5, -56.5, -3.5, 0.5, -59.5, 1.5, 0.65), [(6, "a")], True),
    ("spin_cut", "whale_sweep", ["whale_sweep_2", "spin_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.65), [(4, "a")], False),
]


def tree_path(job, targets):
    """targets を順に取るための節の並び。requires はどれか 1 つが取れていればよい。取った節や目当てと塞ぎ合う節は通らない"""
    nodes = {n["id"]: n for n in json.loads((TREES / f"{job}.json").read_text(encoding="utf-8"))["nodes"]}
    taken = []

    def blocked(i):
        b = set(nodes[i].get("blocks") or [])
        for t in taken + targets:
            if t in b or i in set(nodes[t].get("blocks") or []):
                return True
        return False

    for goal in targets:
        if goal in taken:
            continue
        # 取れている節から goal まで、幅優先で
        start = [i for i, n in nodes.items() if not n.get("requires")] if not taken else list(taken)
        prev = {s: None for s in start}
        queue = list(start)
        while queue and goal not in prev:
            cur = queue.pop(0)
            for i, n in nodes.items():
                if i in prev or i != goal and blocked(i):
                    continue
                if cur in (n.get("requires") or []):
                    prev[i] = cur
                    queue.append(i)
        if goal not in prev:
            raise SystemExit(f"{job}: {goal} に届かない")
        path, k = [], goal
        while k is not None and k not in taken:
            path.append(k)
            k = prev[k]
        taken += reversed(path)
    return taken


FOE = "cmd summon skyabyss:bandit 0.5 -60 4.5 {NoAI:1b,Silent:1b,PersistenceRequired:1b,Rotation:[180f,0f],active_effects:[{id:\"minecraft:resistance\",amplifier:4,duration:99999,show_particles:0b}]}\n"


def cast(pitch, slot_skill, cm, foe=False):
    return f"""camera 0.5 -60 0.5 0 {pitch}
cmd skyabyss skill slot @s 1 {slot_skill}
cmd kill @e[type=!player]
{FOE if foe else ""}wait 30
cmd skyabyss skill cooldowns @s
cmd skyabyss skill mana @s full
chat clear
cmd skyabyss skill cast @s 1
camera {cm}
hud off
"""


def warrior(only):
    out = [HEAD.format(job="warrior", weapon="shrine_sword")]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, slot_skill, nodes, cm, shots, foe in WARRIOR:
            if only and name not in only:
                continue
            if nodes is None:  # 模型だけを置く
                out.append(f"camera 0.5 -60 0.5 0 0\ncmd kill @e[type=!player]\nwait 30\nchat clear\n"
                           f"cmd skyabyss dev prop ss_{name} rise 0.5 -60 0.5\ncamera {cm}\nhud off\n")
            else:
                # 技ごとに覚え直す (発動の技は 4 つまで・点が足りないと取れない)
                out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill points @s 100\n")
                out.append("".join(f"cmd skyabyss skill node @s {n}\n" for n in tree_path("warrior", nodes)))
                out.append(cast(0, slot_skill, cm, foe))
            for w, tag in shots:
                out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            out.append("wait 40\n")
    return out


JOBS = {"mage": (mage, [s[0] for s in MAGE]), "warrior": (warrior, [s[0] for s in WARRIOR])}


def main():
    args = sys.argv[1:]
    job = None
    if "--job" in args:
        i = args.index("--job")
        job = args[i + 1]
        args = args[:i] + args[i + 2:]
    if job is None:
        job = next((j for j, (_, names) in JOBS.items() if args and args[0] in names), "mage")
    fn, names = JOBS[job]
    bad = [a for a in args if a not in names]
    if bad:
        raise SystemExit(f"{job} の技ではない: {bad}")
    (ROOT / "tools/shots/skill_rough.txt").write_text("".join(fn(args)), encoding="utf-8")
    print(f"wrote tools/shots/skill_rough.txt ({job})")


if __name__ == "__main__":
    main()
