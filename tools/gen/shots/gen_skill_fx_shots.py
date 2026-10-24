#!/usr/bin/env python3
"""技の見た目の撮影台本 (tools/shots/skill_fx.txt) を作る。手で直さず、この生成器を直す。

使い方 (リポジトリ直下で):  python tools/gen/shots/gen_skill_fx_shots.py

台本の作り (25 本の能動の技、一本ずつ):
  - 的は、自分の前 3.8 ブロック・右へ 2.2 ブロック寄せたハスク (NoAI・燃えない)。撃つ前に同じ名のハスクを消してから置く
    (kill は対象が無いと「No entity was found」で止まるので、execute if で守る)。
  - 三人称は後ろ (THIRD_PERSON_BACK) の視点。カメラは体の後ろ 4 ブロック、的は前 3.8 ブロックなので、
    カメラと的は 6〜8 ブロックの距離になる。カメラが的の中に入らない。
    真横からの視点は、今の camera 台本の作りでは作れない (カメラは体の向き = 視線に従うので、視線の先に的を置くと後ろからになる)。
  - 撮る時刻は、撃った tick から数えて: a 溜め (1)・b 放ち (放ちの tick + 1)・c 当たり (当たりの tick + 1)・d 残り (残りの tick)。
    放ち・当たり・残りの tick は下の表 (SKILLS) に書く。client/skill/fx の c.at の tick と合わせてある (変えたら両方直す)。
  - 撮る順: 昼の一人称 (a b c d)、昼の三人称 (a b c d)、夜の一人称 (b c)、夜の三人称 (b c)。
  - 弓の技 (雲穿ち・天の驟雨) は弓と矢を持たせてから撃つ。
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "tools/shots/skill_fx.txt"

# 技 id, 放ち tick, 当たり tick, 残り tick, 弓を持つか
SKILLS = [
    ("titan_cleave", 2, 5, 14, False),
    ("rock_call", 0, 4, 12, False),
    ("whale_sweep", 1, 8, 16, False),
    ("lunge", 0, 6, 12, False),
    ("backstep", 1, 5, 12, False),
    ("wind_rend", 1, 5, 14, False),
    ("ward_song", 1, 8, 14, False),
    ("ebb_tide", 1, 6, 14, False),
    ("cycle_song", 1, 8, 18, False),
    ("temper", 0, 6, 20, False),
    ("ember_hammer", 1, 5, 20, False),
    ("ash_scatter", 0, 8, 20, False),
    ("cloud_pierce", 1, 30, 34, True),
    ("bone_trap", 0, 11, 20, False),
    ("sky_rain", 0, 4, 40, True),
    ("signal", 1, 6, 20, False),
    ("dust_charge", 1, 3, 14, False),
    ("steam_bell", 3, 4, 20, False),
    ("cinder_volley", 1, 7, 14, False),
    ("deep_pull", 0, 6, 16, False),
    ("deep_echo", 2, 12, 30, False),
    ("deep_maw", 0, 8, 16, False),
    ("echo_strike", 1, 9, 14, False),
    ("grave_bell", 3, 9, 20, False),
    ("final_light", 0, 6, 20, False),
]

# 体の置き場所と向き (絶対座標・yaw 0 = 南向き、前は +z、右は -x)。毎技この場所へ戻して撮る (枠のずれをなくす)
CASTER_POS = "0.5 -60 0.5 0 0"
# 的: 体の前 3 ブロック・右へ 1.5 (約 27 度)、絶対座標で置く (毎技 殺して置き直す)
TARGET_POS = "-1.0 -60 3.5"
# 突進する技は、的を通り抜けると的が体の後ろ・カメラの真横に来て、黒い箱のように映る。
# 砂の突進は的を進む先 (6.5 ブロック) の右側へ置く (突進の終点で隣に並ぶ)
TARGET_BY_SKILL = {"dust_charge": "-0.7 -60 7.0"}
def husk_for(skill: str) -> str:
    # 体力を 500 にして、当たりで倒れない (倒れると死の煙が出て、白い粒と見分けがつかなくなる)
    return (f"summon minecraft:husk {TARGET_BY_SKILL.get(skill, TARGET_POS)} "
            "{NoAI:1b,Silent:1b,Health:500f,attributes:[{id:\"minecraft:max_health\",base:500.0d}]}")
# 三人称は視野を狭めて、体と的を大きく撮る (カメラの距離は既定の 4 ブロックのまま。FOV だけ変える)
FOV_BACK = 48
FOV_NORMAL = 70


def schedule(rel: int, imp: int, lin: int, full: bool) -> list[tuple[int, str]]:
    """撃った tick からの撮る時刻 (tick, 名の印)。full なら a b c d、夜は b c だけ"""
    a = 1
    b = max(2, rel + 1)
    c = imp + 1
    if c <= b:
        c = b + 2
    d = max(lin, c + 3)
    marks = [(a, "a"), (b, "b"), (c, "c"), (d, "d")]
    return marks if full else [(b, "b"), (c, "c")]


def block(skill: str, rel: int, imp: int, lin: int, bow: bool, view: str, phase: str, full: bool) -> list[str]:
    """一つの (技・時間・視点) の撮影。view は first か back、phase は day か night"""
    lines: list[str] = []
    lines.append(f"perspective {'first' if view == 'first' else 'back'}")
    lines.append(f"fov {FOV_NORMAL if view == 'first' else FOV_BACK}")
    # 毎技、体を同じ場所・同じ向きへ戻す (絶対座標)。前の技の移動 (突進・跳び退き・砂の突進) は先の撮影が終わってから
    lines.append(f"cmd tp @s {CASTER_POS}")
    # 開幕の題字と HUD を消してから撮る (cinematic の停止と、部品の一括オフ)
    # 天候は毎技そろえる (前の技の雨が残らないように)。夜の撮影は time set midnight のまま
    lines.append("cmd gamerule doWeatherCycle false")
    lines.append("cmd weather clear")
    lines.append("cmd skyabyss cinematic stop")
    lines.append("huddemo off")
    lines.append("hud off")
    lines.append("wait 30")
    if bow:
        lines.append("cmd item replace entity @s weapon.mainhand with minecraft:bow")
        lines.append("cmd give @s minecraft:arrow 64")
    lines.append(f"cmd skyabyss skill learn @s {skill} 3")
    lines.append(f"cmd skyabyss skill slot @s 1 {skill}")
    lines.append("cmd skyabyss skill cooldowns @s")
    lines.append("cmd execute if entity @e[type=minecraft:husk] run kill @e[type=minecraft:husk]")
    lines.append(f"cmd {husk_for(skill)}")
    lines.append("wait 10")
    lines.append("cmd skyabyss skill mana @s full")
    lines.append("cmd skyabyss skill cast @s 1")
    now = 0
    prefix = f"{skill}_{'' if phase == 'day' else 'night_'}{'1p' if view == 'first' else '3p'}"
    for tick, mark in schedule(rel, imp, lin, full):
        if tick > now:
            lines.append(f"wait {tick - now}")
            now = tick
        lines.append(f"shot {prefix}_{mark}")
    lines.append("cmd skyabyss skill cooldowns @s")
    # 移動する技が落ち着くまで待ってから、次の撮影へ (体を戻す前)
    lines.append("wait 30")
    return lines


def main() -> None:
    out: list[str] = []
    out.append("# 技の見た目 (撮影台本)。tools/gen/shots/gen_skill_fx_shots.py が作る。手で直さず、生成器を直す。")
    out.append("# 撮影: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/skill_fx.txt -PshotsName=skill_fx")
    out.append("# 名前: <技>_1p_<a|b|c|d> (昼の一人称)、<技>_3p_<…> (昼の三人称)、<技>_night_1p_<b|c>、<技>_night_3p_<b|c>")
    out.append("# a = 溜め (撃った 1 tick 後)、b = 放ち、c = 当たり、d = 残り")
    out.append("world skill_fx flat")
    out.append("size 1280 720")
    out.append("timeout 3600")
    out.append("render 6")
    out.append("clouds off")
    out.append("guiscale 2")
    out.append("cmd time set noon")
    out.append("cmd gamerule doWeatherCycle false")
    out.append("cmd weather clear")
    out.append("cmd gamemode survival")
    out.append("camera 0 -60 0 0 0")
    out.append("hud off")
    out.append("cmd skyabyss cinematic stop")
    out.append("waitchunks")
    out.append("chat clear")
    out.append("wait 200")
    out.append("")
    for skill, rel, imp, lin, bow in SKILLS:
        out.append(f"# ---- {skill} (昼)")
        out.extend(block(skill, rel, imp, lin, bow, "first", "day", True))
        out.append("")
        out.extend(block(skill, rel, imp, lin, bow, "back", "day", True))
        out.append("")
    out.append("perspective first")
    out.append("cmd time set midnight")
    out.append("wait 20")
    for skill, rel, imp, lin, bow in SKILLS:
        out.append(f"# ---- {skill} (夜)")
        out.extend(block(skill, rel, imp, lin, bow, "first", "night", False))
        out.append("")
        out.extend(block(skill, rel, imp, lin, bow, "back", "night", False))
        out.append("")
    out.append("perspective first")
    out.append("cmd time set noon")
    out.append("cmd skyabyss skill cooldowns @s")
    out.append("quit")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    print("wrote", OUT, "lines", len(out))


if __name__ == "__main__":
    main()
