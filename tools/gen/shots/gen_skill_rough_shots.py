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
    ("frost_tide", 25, "9.6 -56.5 -4.4 47 5", [(3, "seq03"), (3, "seq06"), (2, "seq08")] + [(2, "seq%02d" % t) for t in range(10, 29, 2)]),  # look d: alive and shatter
    ("ember_orb", 0, "5 -58.5 7 48 2", [(1, "fly")]),
    ("ember_orb", 0, "5 -58 10 52 15", [(8, "hit")]),
    ("ember_orb", 0, "11.3 -53 5 52 24", [(4, "wide"), (8, "hit_wide")]),  # 広く見下ろす (試作 d の床のひび)
    ("ember_orb", 0, "5 -58 10 52 15", [(8, "burn08")] + [(3, "burn%02d" % (8 + 3 * k)) for k in range(1, 10)]),  # 燃える間と燃え尽き (試作 d)
    ("thunder_bolt", 15, "8 -56.5 4 49 15", [(1, "a"), (3, "b")]),
    ("thunder_bolt", 15, "7.5 -56.5 4.4 58 -2", [(1, "seq%02d" % t) for t in range(1, 15)]),  # look d: strike and burnout
    ("blink", 0, "10 -56 2 67 16", [(2, "a")]),
    ("blink", 0, "5.2 -58.6 6.6 50 2", [(1, "seq%02d" % t) for t in range(1, 15)]),  # look d: afterglow and fade
    # 星屑は雷撃の刻印 1 (最後に置く。刻印は案ごとの覚え直しで外れる)
    ("stardust", 10, "8 -55.5 4 49 24", [(2, "a"), (3, "b"), (6, "c")]),
    ("stardust", 10, "7 -56 4.5 47 16", [(2, "seq%02d" % t) for t in range(2, 31, 2)]),  # look d: field and fade
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
    # look d: 地割れが前へ 7 ブロック・弧が高さ 5.5 ブロックまで届くので引いて撮る。打ち込みから沈むまで
    ("titan_cleave", "titan_cleave", ["titan_cleave"], cam(8.5, -56.5, -3.5, 0.5, -59.5, 2.5, 0.85), [(1, "seq%02d" % t) for t in range(1, 23)], False),
    ("rock_call", "rock_call", ["rock_call"], cam(7.5, -56, -6.5, 0.5, -59.5, 0.5, 0.65), [(6, "a")], True),
    ("rock_call", "rock_call", ["rock_call"], cam(7.5, -56, -6.5, 0.5, -59.5, 0.5, 0.62), [(1, "seq%02d" % t) for t in range(1, 25)], True),  # look d: rise and crumble
    ("whale_sweep", "whale_sweep", ["whale_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.65), [(5, "a")], False),
    ("whale_sweep", "whale_sweep", ["whale_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.75), [(1, "seq%02d" % t) for t in range(1, 21)], False),  # look d: wave and dive
    ("shield_rush", "shield_rush", ["shield_rush"], cam(10.5, -56, -2.5, 0.5, -59, 3.5, 0.8), [(3, "a")], True),
    ("shield_rush", "shield_rush", ["shield_rush"], cam(11.5, -55.5, -1.5, 0.5, -59, 4.0, 0.85), [(1, "seq%02d" % t) for t in range(1, 21)], True),  # look d: rush and shatter
    # 盾殴りは今の木では見た目が出ない (盾の踏み込みの刻印 1 は殴りの当たりだけで見た目は踏み込み。直に覚えても枠に入らない)。
    # 模型だけを dev prop で置いて撮る (大きさ 1。技の 1.3 より小さい)
    ("shield_bash", None, None, cam(8.5, -56.5, -3.5, 0.5, -59.5, 1.5, 0.65), [(4, "a")], False),
    ("shield_bash", None, None, cam(4.5, -57.5, -1.5, 0.5, -59.4, 1.6, 1.0), [(4, "close")], False),  # look d の寄り
    # look d: 盾殴りは踏み込みの刻印 1 (bash_rush) で踏み込みのすぐ後に出る。踏み込みごと撮る
    ("shield_bash", "shield_rush", ["shield_rush", "bash_rush"], cam(7.0, -57.5, -0.5, 0.5, -60.0, 3.0, 1.0), [(1, "seq%02d" % t) for t in range(1, 21)], True),
    # 鯨骨の振り = 巨神斬りの刻印 2 (deep_blade)、回し斬り = 鯨尾薙ぎの刻印 8 (spin_sweep)。技を極めてから刻印を取る
    ("bone_swing", "titan_cleave", ["titan_cleave_2", "titan_cleave_3", "deep_blade"], cam(8.5, -56.5, -3.5, 0.5, -59.5, 1.5, 0.65), [(6, "a")], True),
    ("bone_swing", "titan_cleave", ["titan_cleave_2", "titan_cleave_3", "deep_blade"], cam(8.5, -55.5, -4.5, 0.5, -59.5, 1.5, 0.75), [(1, "seq%02d" % t) for t in range(1, 23)], True),  # look d: swing and crumble
    ("spin_cut", "whale_sweep", ["whale_sweep_2", "spin_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.65), [(4, "a")], False),
    ("spin_cut", "whale_sweep", ["whale_sweep_2", "spin_sweep"], cam(8.5, -55.5, -7.5, 0.5, -59.5, 0.5, 0.75), [(1, "seq%02d" % t) for t in range(1, 21)], False),  # look d: spin and topple
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
{(FOE.replace(" 4.5 ", f" {foe} ") if isinstance(foe, float) else FOE) if foe else ""}wait 30
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
                # 連続撮影: 技で体が流されるので、1 枚ごとにカメラを置き直す
                out.append(f"wait {w}\n" + (f"camera {cm}\n" if tag.startswith("seq") else "") + f"shot {look}_{name}_{tag}\n")
            out.append("wait 40\n")
    return out


# ---------------------------------------------------------------- 暗殺者
# (技, 枠に入れる技, 取る節の並び (None = dev prop で模型だけ置く, "learn" = skill learn で覚える), 撮る位置, 撮る tick の並び, 前に敵を置くか)
# 踏み込み・跳び退き・風裂きは近くに敵がいないと放てない。双牙は敵へ向けて置く (targetOr)
ASSASSIN = [
    ("lunge", "lunge", ["lunge"], cam(10.5, -56, 1.5, 0.5, -59.5, 4.0, 0.8), [(2, "a")], True),
    ("lunge", "lunge", ["lunge"], cam(11.5, -55.5, 4.5, 0.5, -59.5, 6.0, 0.95), [(1, "seq%02d" % t) for t in range(1, 29)], True),  # look d: thrust and sink
    ("backstep", "backstep", ["backstep"], cam(8.5, -56.5, -4.5, 0.5, -59.5, -0.5, 0.65), [(3, "a")], True),
    ("backstep", "backstep", ["backstep"], cam(9.5, -56.0, -4.5, 0.5, -59.5, -0.8, 0.75), [(1, "seq%02d" % t) for t in range(1, 29)], True),  # look d: smoke and sink
    # 双牙は 3.5 ブロックまでしか届かない (FANG_RANGE)。野盗を 2.5 ブロック前に置く
    ("twin_fang", "twin_fang", ["twin_fang"], cam(8.5, -56.5, -1.5, 0.5, -59.5, 3.0, 0.7), [(4, "a"), (5, "b")], 3.0),
    ("twin_fang", "twin_fang", ["twin_fang"], cam(5.0, -56.8, -3.0, 0.5, -59.2, 3.0, 0.85), [(1, "seq%02d" % t) for t in range(1, 41)], 3.0),  # look d: bite and retract
    ("wind_rend", "wind_rend", ["wind_rend"], cam(8.5, -57.5, 0.5, 0.5, -58.5, 3.5, 0.8), [(1, "a"), (1, "b")], True),
    ("wind_rend", "wind_rend", ["wind_rend"], cam(5.5, -57.0, -2.5, 0.5, -58.6, 3.5, 1.0), [(1, "seq%02d" % t) for t in range(1, 29)], True),  # look d: spin and scatter
    # 霧隠れ = 踏み込みの刻印 8「霧の踏み込み」(veil_lunge)。踏み込みのあと足もとに出る
    ("mist_veil", "lunge", ["veil_lunge"], cam(7.5, -56, -3.5, 0.5, -58.5, 2.5, 0.75), [(6, "a")], True),
    ("mist_veil", "lunge", ["veil_lunge"], cam(9.5, -55.5, -3.5, 0.5, -59.0, 3.0, 0.85), [(2, "seq%02d" % t) for t in range(1, 31)], True),  # look d: veil and settle
]


def assassin(only):
    out = [HEAD.format(job="assassin", weapon="shrine_dagger")]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, slot_skill, nodes, cm, shots, foe in ASSASSIN:
            if only and name not in only:
                continue
            if nodes is None:  # 模型だけを置く
                kind = name.replace("_prop", "")
                out.append(f"camera 0.5 -60 0.5 0 0\ncmd kill @e[type=!player]\nwait 30\nchat clear\n"
                           f"cmd skyabyss dev prop ss_{kind} rise 0.5 -60 0.5\ncamera {cm}\nhud off\n")
            else:
                out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill points @s 100\n")
                if nodes == "learn":
                    out.append(f"cmd skyabyss skill learn @s {slot_skill} 1\n")
                else:
                    out.append("".join(f"cmd skyabyss skill node @s {n}\n" for n in tree_path("assassin", nodes)))
                out.append(cast(0, slot_skill, cm, foe))
            # 連続 (seq) は 1 枚に 5 tick ほどかかる (tick rate を落としても同じ)。世界を止めて、1 枚ごとに w tick だけ進める
            slow = any(tag.startswith("seq") for _, tag in shots)
            if slow:
                out.append("cmd tick freeze\n")
            for w, tag in shots:
                if tag.startswith("seq"):
                    out.append(f"cmd tick step {w}\nwait 3\ncamera {cm}\nshot {look}_{name}_{tag}\n")
                else:
                    out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            if slow:
                out.append("cmd tick unfreeze\n")
            out.append("wait 40\n")
    return out


# ---------------------------------------------------------------- 弓使い
# (技, 枠に入れる技, 取る節の並び, 放つ向きの pitch, 撮る位置, 撮る tick の並び, 前に敵を置くか)
# 雲穿ちは矢が要る (撮影の頭で渡す)。空の雨は見ている先の地面へ降るので下を向いて放つ。
# 雪縛り = 雲穿ちの刻印 8「縛りの矢」(snare_pierce)。12 ブロック以内・15 度の中の敵が要る
ARCHER = [
    # 的を置かずに 24 ブロック先へ飛ばす (6 tick)。近い的だと撮る前に飛び切る
    ("cloud_pierce", "cloud_pierce", ["cloud_pierce"], 0, cam(14.5, -57.5, 9.0, 0.5, -58.5, 9.0, 0.9), [(1, "a"), (1, "b")], False),
    ("cloud_pierce", "cloud_pierce", ["cloud_pierce"], 0, cam(5.0, -57.9, 17.0, 0.5, -58.6, 22.5, 1.0), [(1, "seq%02d" % t) for t in range(1, 27)], False),  # look d: pierce and shatter
    ("bone_trap", "bone_trap", ["bone_trap"], 0, cam(5.5, -57.0, -1.0, 0.5, -59.8, 1.8, 0.75), [(5, "a")], False),
    ("bone_trap", "bone_trap", ["bone_trap"], 0, cam(4.5, -57.6, -1.5, 0.5, -59.7, 1.6, 0.85), [(3, "seq01"), (3, "seq02"), (3, "seq03"), (50, "seq04")] + [(2, "seq%02d" % t) for t in range(5, 19)], False),  # look d: snap and sink
    ("sky_rain", "sky_rain", ["sky_rain"], 14, cam(13.5, -54.0, 1.5, 0.5, -59.5, 7.0, 0.85), [(8, "a"), (14, "b")], False),
    ("sky_rain", "sky_rain", ["sky_rain"], 14, cam(12.5, -54.5, 2.0, 0.5, -59.5, 7.5, 0.9), [(2, "seq%02d" % t) for t in range(1, 11)] + [(30, "seq11")] + [(2, "seq%02d" % t) for t in range(12, 31)], False),  # look d: rain and sink
    ("snare_shot", "cloud_pierce", ["snare_pierce"], 0, cam(8.5, -57.0, 1.0, 0.5, -59.0, 4.0, 0.8), [(1, "a"), (6, "b")], 6.0),
    ("snare_shot", "cloud_pierce", ["snare_pierce"], 0, cam(6.5, -57.4, 7.5, 0.5, -59.0, 12.0, 0.85), [(1, "seq%02d" % t) for t in range(1, 7)] + [(30, "seq07")] + [(2, "seq%02d" % t) for t in range(8, 28)], 6.0),  # look d: bind and snap (形は 12 ブロック先に出る)
    # 飛ぶ・降る形は技では速すぎて写らない (雲穿ちは 6 tick で 24 ブロック)。dev prop で遅く飛ばして撮る (40 tick・大きさ 1.4)
    ("cloud_pierce_toss", "toss 0.5 -58.8 0.5 0.5 -58.8 24.5 40", None, 0, cam(9.0, -55.3, 6.0, 0.5, -55.8, 6.0, 0.8), [(10, "a")], False),
    ("snare_shot_toss", "toss 0.5 -58.8 0.5 0.5 -58.8 24.5 40", None, 0, cam(9.0, -55.3, 6.0, 0.5, -55.8, 6.0, 0.8), [(10, "a")], False),
    ("sky_rain_fall", "fall 0.5 -60 5.5 10", None, 0, cam(9.0, -57.0, 5.5, 0.5, -57.5, 5.5, 0.8), [(17, "a")], False),
]


def archer(only):
    out = [HEAD.format(job="archer", weapon="shrine_bow"), "cmd give @s minecraft:arrow 64\n"]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, slot_skill, nodes, pitch, cm, shots, foe in ARCHER:
            if only and name not in only:
                continue
            if nodes is None:  # dev prop で模型だけを飛ばす (slot_skill に toss / fall の引数)
                kind = name.replace("_toss", "_fly")
                out.append(f"camera 0.5 -60 0.5 0 0\ncmd kill @e[type=!player]\nwait 30\nchat clear\ncamera {cm}\nhud off\n"
                           f"cmd skyabyss dev prop ss_{kind} {slot_skill}\n")
            else:
                out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill points @s 100\n")
                out.append("".join(f"cmd skyabyss skill node @s {n}\n" for n in tree_path("archer", nodes)))
                c = cast(pitch, slot_skill, cm, foe)
                if any(tag.startswith("seq") for _, tag in shots):
                    # 矢は数 tick で飛び切るので、放つ前に世界を止める
                    c = c.replace("cmd skyabyss skill cast", "cmd tick freeze\ncmd skyabyss skill cast")
                out.append(c)
            # 連続 (seq) は世界を止めて 1 枚ごとに w tick 進める (assassin() と同じ)
            slow = any(tag.startswith("seq") for _, tag in shots)
            if slow and nodes is None:
                out.append("cmd tick freeze\n")
            for w, tag in shots:
                if tag.startswith("seq"):
                    # 次の tick step は残りの歩みを打ち消すので、歩み切るまで待つ
                    out.append(f"cmd tick step {w}\nwait {max(3, w + 3)}\ncamera {cm}\nshot {look}_{name}_{tag}\n")
                else:
                    out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            if slow:
                out.append("cmd tick unfreeze\n")
            out.append("wait 80\n")
    return out


# ---------------------------------------------------------------- 呪術師
# (技, 枠に入れる技, 取る節の並び, 放つ向きの pitch, 撮る位置, 撮る tick の並び, 前に敵を置くか)
# 引き潮は前 8 ブロック以内の敵が要る (ebbRange)。呪いの印は 16 ブロック以内の狙った敵が要り、印は敵の所に 3 tick 遅れて出る。
# 灯の守り札は見ている先に置くので、少し下を向いて放つ。巡りの唄 = 守りの唄の刻印 8 (cycle_ward)
SHAMAN = [
    ("ward_song", "ward_song", ["ward_song"], 0, cam(6.5, -57.0, -4.5, 0.5, -59.3, 0.5, 0.8), [(4, "a"), (12, "b")], False),
    ("ward_song", "ward_song", ["ward_song"], 0, cam(6.5, -57.0, -4.5, 0.5, -59.3, 0.5, 0.8), [(2, "seq01")] + [(3, "seq%02d" % t) for t in range(2, 17)], False),  # look d: song and close
    ("ebb_tide", "ebb_tide", ["ebb_tide"], 0, cam(8.5, -56.0, -2.0, 0.5, -59.5, 3.0, 0.85), [(3, "a"), (6, "b")], 5.0),
    ("ebb_tide", "ebb_tide", ["ebb_tide"], 0, cam(8.5, -56.0, -2.0, 0.5, -59.5, 3.0, 0.85), [(3, "seq01")] + [(2, "seq%02d" % t) for t in range(2, 17)], 5.0),  # look d: pull back
    ("cycle_song", "ward_song", ["cycle_ward"], 0, cam(6.5, -57.0, -4.5, 0.5, -59.3, 0.5, 0.8), [(4, "a"), (12, "b")], False),
    ("cycle_song", "ward_song", ["cycle_ward"], 0, cam(6.5, -57.0, -4.5, 0.5, -59.3, 0.5, 0.8), [(2, "seq01")] + [(3, "seq%02d" % t) for t in range(2, 17)], False),  # look d: ring and unwind
    ("curse_mark", "curse_mark", ["curse_mark"], 0, cam(6.0, -57.5, 1.5, 0.5, -58.8, 6.0, 0.8), [(6, "a"), (20, "b")], 6.0),
    ("curse_mark", "curse_mark", ["curse_mark"], 0, cam(6.0, -57.5, 1.5, 0.5, -58.8, 6.0, 0.8), [(5, "seq01"), (10, "seq02"), (10, "seq03"), (10, "seq04")] + [(3, "seq%02d" % t) for t in range(5, 16)], 6.0),  # look d: press the seal
    ("lamp_charm", "lamp_charm", ["lamp_charm"], 30, cam(6.5, -57.0, -1.0, 0.5, -59.3, 3.0, 0.8), [(5, "a"), (30, "b")], False),
    ("lamp_charm", "lamp_charm", ["lamp_charm"], 30, cam(6.5, -57.0, -1.0, 0.5, -59.3, 3.0, 0.8), [(5, "seq01"), (30, "seq02"), (50, "seq03"), (15, "seq04")] + [(3, "seq%02d" % t) for t in range(5, 14)], False),  # look d: lamp out
]


def shaman(only):
    out = [HEAD.format(job="shaman", weapon="shrine_bell_staff")]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, slot_skill, nodes, pitch, cm, shots, foe in SHAMAN:
            if only and name not in only:
                continue
            out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill points @s 100\n")
            out.append("".join(f"cmd skyabyss skill node @s {n}\n" for n in tree_path("shaman", nodes)))
            c = cast(pitch, slot_skill, cm, foe)
            slow = any(tag.startswith("seq") for _, tag in shots)
            if slow:
                # 連続 (seq) は放つ前に世界を止め、1 枚ごとに w tick 進める (archer() と同じ)
                c = c.replace("cmd skyabyss skill cast", "cmd tick freeze\ncmd skyabyss skill cast")
            out.append(c)
            for w, tag in shots:
                if tag.startswith("seq"):
                    out.append(f"cmd tick step {w}\nwait {max(3, w + 3)}\ncamera {cm}\nshot {look}_{name}_{tag}\n")
                else:
                    out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            if slow:
                out.append("cmd tick unfreeze\n")
            out.append("wait 120\n")
    return out


# ---------------------------------------------------------------- 鍛冶師
# (技, 枠に入れる技, 取る節の並び, 放つ向きの pitch, 撮る位置, 撮る tick の並び, 前に敵を置くか)
# 灰散らしは 5 ブロック以内の敵が要る。炉の踏みは 2 つ目の輪が 3 tick 遅れて出て、どちらも 10 tick で消える。
# 火花散らし = 炉の踏みの刻印 1 (spark_stomp)。濃い灰 (thick_ash) は灰の風を塞ぐので、ash_wind を目当てに入れて熱い灰から通す
SMITH = [
    ("temper", "temper", ["temper"], 0, cam(6.5, -57.0, -4.5, 0.5, -58.8, 0.5, 0.8), [(4, "a"), (12, "b")], False),
    ("ember_hammer", "ember_hammer", ["ember_hammer"], 0, cam(8.0, -56.5, -1.0, 0.5, -59.0, 2.5, 0.85), [(4, "a"), (9, "b")], False),
    ("ash_scatter", "ash_scatter", ["ash_scatter"], 0, cam(11.0, -53.5, -7.0, 0.5, -59.5, 0.5, 0.85), [(5, "a"), (20, "b")], 3.5),
    ("forge_stomp", "forge_stomp", ["forge_stomp"], 0, cam(10.0, -54.5, -6.0, 0.5, -59.5, 0.5, 0.85), [(2, "a"), (5, "b")], False),
    ("spark_burst", "forge_stomp", ["forge_stomp", "ash_wind", "spark_stomp"], 0, cam(7.5, -56.0, -5.0, 0.5, -59.0, 0.5, 0.85), [(2, "a"), (5, "b")], False),
]


def smith(only):
    out = [HEAD.format(job="smith", weapon="shrine_hammer")]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, slot_skill, nodes, pitch, cm, shots, foe in SMITH:
            if only and name not in only:
                continue
            out.append("cmd skyabyss skill forget @s\ncmd skyabyss skill points @s 100\n")
            out.append("".join(f"cmd skyabyss skill node @s {n}\n" for n in tree_path("smith", nodes)))
            out.append(cast(pitch, slot_skill, cm, foe))
            for w, tag in shots:
                out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            out.append("wait 120\n")
    return out


# ---------------------------------------------------------------- 淵・前世 (どの職でも覚える技。skill learn で覚えて放つ)
# (技, 撮る位置, 撮る tick の並び, 前に敵を置くか)。淵の顎は見ている先 8 ブロック、遺響の一撃は前 2.5 ブロック。墓鐘は降る形 (3 tick で落ちる)
DEEP = [
    ("deep_pull", cam(10.0, -54.5, -6.0, 0.5, -59.5, 0.5, 0.85), [(3, "a"), (8, "b")], 3.0),
    ("deep_echo", cam(6.5, -57.0, -4.5, 0.5, -59.0, 0.5, 0.8), [(4, "a"), (12, "b")], False),
    ("deep_maw", cam(9.0, -55.5, 0.0, 0.5, -59.5, 6.0, 0.85), [(6, "a"), (10, "b")], 6.0),
    ("echo_strike", cam(7.0, -56.5, -1.5, 0.5, -59.0, 2.5, 0.8), [(3, "a"), (12, "b")], 3.0),
    ("grave_bell", cam(7.0, -56.5, -5.0, 0.5, -59.0, 0.5, 0.8), [(1, "a"), (2, "b"), (3, "c")], False),
    ("final_light", cam(11.0, -53.5, -7.0, 0.5, -59.5, 0.5, 0.85), [(4, "a"), (9, "b")], False),
]


def deep(only):
    out = [HEAD.format(job="warrior", weapon="shrine_sword"), "cmd skyabyss level set @p 60\n"]
    for look in LOOKS:
        out.append(f"# ======== {look}\ngearlook skill {look}\n")
        for name, cm, shots, foe in DEEP:
            if only and name not in only:
                continue
            out.append(f"cmd skyabyss skill forget @s\ncmd skyabyss skill learn @s {name} 1\n")
            out.append(cast(0, name, cm, foe))
            for w, tag in shots:
                out.append(f"wait {w}\nshot {look}_{name}_{tag}\n")
            out.append("wait 60\n")
    return out


JOBS = {"mage": (mage, [s[0] for s in MAGE]), "warrior": (warrior, [s[0] for s in WARRIOR]),
        "assassin": (assassin, [s[0] for s in ASSASSIN]), "archer": (archer, [s[0] for s in ARCHER]),
        "shaman": (shaman, [s[0] for s in SHAMAN]), "smith": (smith, [s[0] for s in SMITH]),
        "deep": (deep, [s[0] for s in DEEP])}


def main():
    args = sys.argv[1:]
    job = None
    if "--job" in args:
        i = args.index("--job")
        job = args[i + 1]
        args = args[:i] + args[i + 2:]
    if "--looks" in args:  # 例: --looks a,d
        i = args.index("--looks")
        LOOKS[:] = args[i + 1].split(",")
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
