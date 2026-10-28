#!/usr/bin/env python3
"""技の見た目の撮影台本 (tools/shots/skill_fx.txt) を作る。手で直さず、この生成器を直す。

使い方 (リポジトリ直下で):  python tools/gen/shots/gen_skill_fx_shots.py
同時に tools/shots/skill_fx_clips.json を書く (動画の fps と、当たりの位置。tools/site/encode_skill_fx_clips.py が読む)。

台本の作り (25 本の能動の技、一本ずつ、昼の三人称の背中側だけ。一本が一つの動画):
  - 的は、自分の前 3.8 ブロック・右へ 2.2 ブロック寄せたMod の敵 (野盗。NoAI・燃えない)。撃つ前に同じ名のハスクを消してから置く
    (kill は対象が無いと「No entity was found」で止まるので、execute if で守る)。
  - 三人称は後ろ (THIRD_PERSON_BACK) の視点。カメラは体の後ろ 4 ブロック、的は前 3.8 ブロックなので、
    カメラと的は 6〜8 ブロックの距離になる。カメラが的の中に入らない。
    真横からの視点は、今の camera 台本の作りでは作れない (カメラは体の向き = 視線に従うので、視線の先に的を置くと後ろからになる)。
  - 録る範囲: 撃つ CLIP_PRE tick 前から録り始め、残り (linger) の後も少し録る (clip_ticks)。
    record の間に cmd・wait を進められるので、撃つ・待つは普通の手順のまま書く。
  - 録画の間は /tick rate を CLIP_TICK_RATE (5) に落とす。描画は 1 tick の間に何枚も描かれるので、
    論理時刻 (tick + 割合) で CLIP_FPS (30) 枚ずつ撮れば、動きが滑らかになる。再生は 20 tick/s の実時間に戻る。
    FX (client/skill/fx) は level.getGameTime() + partialTick で進むので、遅くしても時刻が合う。
  - 弓の技 (雲穿ち・天の驟雨) は弓と矢を持たせてから撃つ。
  - 技の表 (SKILLS) の 放ち・当たり・残り の tick は client/skill/fx の c.at の tick と合わせてある (変えたら両方直す)。
  - 天の驟雨は、降る光が RAIN_INTERVAL * RAIN_PULSES tick の間続く (SkillActions の RAINS)。終わるまで次の技を撮らない。
  - 撮る前に、天候を晴れにして、前の技の的・矢・落ちた肉を消す。
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OUT = ROOT / "tools/shots/skill_fx.txt"
CLIPS = ROOT / "tools/shots/skill_fx_clips.json"

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
    # 木 v2 (48) で足した技 (client/skill/fx/FxTree の c.at と合わせる)
    ("shield_bash", 0, 2, 14, False),
    ("bone_swing", 3, 6, 22, False),
    ("spin_cut", 0, 5, 18, False),
    ("twin_fang", 0, 4, 14, False),
    ("spark_burst", 0, 1, 16, False),
    ("snare_shot", 0, 2, 16, True),
    ("stardust", 0, 4, 20, False),
    ("deep_pull", 0, 6, 16, False),
    ("deep_echo", 2, 12, 30, False),
    ("deep_maw", 0, 8, 16, False),
    ("echo_strike", 1, 9, 14, False),
    ("grave_bell", 3, 9, 20, False),
    ("final_light", 0, 6, 20, False),
]

# 技の職 (職の木。淵・前世の技はどの職でも使えるので戦士で撮る)
JOB_OF = {
    "titan_cleave": "warrior", "rock_call": "warrior", "whale_sweep": "warrior", "shield_bash": "warrior", "bone_swing": "warrior", "spin_cut": "warrior",
    "lunge": "assassin", "backstep": "assassin", "wind_rend": "assassin", "twin_fang": "assassin",
    "ward_song": "shaman", "ebb_tide": "shaman", "cycle_song": "shaman",
    "temper": "smith", "ember_hammer": "smith", "ash_scatter": "smith", "spark_burst": "smith",
    "cloud_pierce": "archer", "bone_trap": "archer", "sky_rain": "archer", "snare_shot": "archer",
    "stardust": "mage",
}
STARTER = {"warrior": "shrine_sword", "smith": "shrine_hammer", "mage": "shrine_staff", "archer": "shrine_bow", "assassin": "shrine_dagger", "shaman": "shrine_bell_staff"}

# 録る時の tick の割合 (論理時刻)。動画は CLIP_FPS 枚/秒で、20 tick/s の実時間に合わせて再生する
CLIP_TICK_RATE = 5
CLIP_FPS = 30
# 撃つ前から録り始める tick 数 (撃つ前の静けさを少し入れる)
CLIP_PRE = 4
# 録画を撃ってからの最低の長さ (残りが短い技も、消えるまで見せる)
CLIP_MIN_AFTER = 32
# 天の驟雨の降る光が続く tick (SkillRules.RAIN_INTERVAL * RAIN_PULSES)。次の技を撮るのは、これが終わってから
SKY_RAIN_TICKS = 10 * 8

# 体の置き場所と向き (絶対座標・yaw 0 = 南向き、前は +z、右は -x)。毎技この場所へ戻して撮る (枠のずれをなくす)
CASTER_POS = "0.5 -60 0.5 0 0"
# 的: 体の前 3 ブロック・右へ 1.5 (約 27 度)、絶対座標で置く (毎技 殺して置き直す)
TARGET_POS = "-1.0 -60 3.5"
# 突進する技は、的を通り抜けると的が体の後ろ・カメラの真横に来て、黒い箱のように映る。
# 砂の突進は的を進む先 (6.5 ブロック) の右側へ置く (突進の終点で隣に並ぶ)
# 突進は前 25 度の中に的が無いと「的がない」で撃てない (27 度の既定の的では失敗していた)。的を真っすぐ前へ
# 突進 (lunge) は的を体の真正面に置くと、後ろのカメラから見て体に隠れて消える (撮った絵で無いのを確かめた)。右へずらす
# 足止めの矢は狙いの角が狭い (15 度) ので、的を正面に置く
TARGET_BY_SKILL = {"lunge": "-1.6 -60 6.5", "sky_rain": "-0.5 -60 7.5", "snare_shot": "0.2 -60 6.0"}
# 体の向きを技ごとに変える (yaw pitch)。天の驟雨は視線の先 (24 ブロック) の地面に降るので、少し下を向いて前 7 ブロックへ降らせる
# (目の高さ 1.62 から 13 度下 = 前 7 ブロックの地面。的はその 1 ブロック右で、降る輪の中)
# 合図も視線の先 (地面・相手の足もと) に立つので、同じく前 7 ブロックの地面へ
CASTER_BY_SKILL = {"sky_rain": "0.5 -60 0.5 0 13", "signal": "0.5 -60 0.5 0 13"}


# 的の生き物を技ごとに変える。突進は的の真ん前で止まるので、後ろのカメラからは体に隠れる。体より大きいラヴェジャーなら肩の向こうに見える
# 的は Mod の敵 (44: バニラの敵はどの湧き方でも世界に入らない)。踏み込みは大きめの猪
TARGET_MOB_BY_SKILL = {"lunge": "skyabyss:rock_boar"}
TARGET_MOB_DEFAULT = "skyabyss:bandit"


def husk_for(skill: str) -> str:
    # 体力を 500 にして、当たりで倒れない (倒れると死の煙が出る)
    return (f"summon {TARGET_MOB_BY_SKILL.get(skill, TARGET_MOB_DEFAULT)} {TARGET_BY_SKILL.get(skill, TARGET_POS)} "
            "{NoAI:1b,Silent:1b,Health:500f,attributes:[{id:\"minecraft:max_health\",base:500.0d}]}")


# 三人称は視野を狭めて、体と的を大きく撮る (カメラの距離は既定の 4 ブロックのまま。FOV だけ変える)
FOV_BACK = 48


def clip_ticks(lin: int, sky: bool) -> int:
    """録画の長さ (tick)。撃つ前の CLIP_PRE と、残りが消えてから少しの間 (CLIP_MIN_AFTER か 残り + 8 の長い方)"""
    after = max(CLIP_MIN_AFTER, lin + 8)
    if sky:
        after = max(after, SKY_RAIN_TICKS + 4)
    return CLIP_PRE + after


def block(skill: str, lin: int, bow: bool, total: int, sky: bool) -> list[str]:
    """一つの技の撮影 (一つの動画)"""
    lines: list[str] = []
    lines.append("perspective back")
    lines.append(f"fov {FOV_BACK}")
    # 毎技、体を同じ場所・同じ向きへ戻す (絶対座標)。前の技の移動 (突進・跳び退き・砂の突進) は先の撮影が終わってから
    lines.append(f"cmd tp @s {CASTER_BY_SKILL.get(skill, CASTER_POS)}")
    # 開幕の題字と HUD を消してから撮る (cinematic の停止と、部品の一括オフ)
    # 天候は毎技そろえる (前の技の雨が残らないように)。時刻は昼のまま
    lines.append("cmd gamerule doWeatherCycle false")
    lines.append("cmd weather clear")
    lines.append("cmd skyabyss cinematic stop")
    lines.append("huddemo off")
    lines.append("hud off")
    lines.append("wait 30")
    # 職は職の間で替える (48)。技の職にして、その職の始めの武器を持つ (弓の技は弓と矢)
    job = JOB_OF.get(skill, "warrior")
    lines.append(f"cmd skyabyss job choose {job}")
    if bow:
        lines.append("cmd item replace entity @s weapon.mainhand with minecraft:bow")
        lines.append("cmd give @s minecraft:arrow 64")
    else:
        lines.append(f"cmd item replace entity @s weapon.mainhand with skyabyss:{STARTER[job]}")
    lines.append(f"cmd skyabyss skill learn @s {skill} 3")
    lines.append(f"cmd skyabyss skill slot @s 1 {skill}")
    lines.append("cmd skyabyss skill cooldowns @s")
    # 前の的は奈落へ送って消す (その場で kill すると死の煙が 1 秒ほど残り、落とした腐った肉と一緒に「当たりの白い粒」に見えていた)
    lines.append("cmd execute as @e[type=skyabyss:bandit] run tp @s ~ -400 ~")
    lines.append("cmd execute as @e[type=skyabyss:rock_boar] run tp @s ~ -400 ~")
    lines.append("cmd execute if entity @e[type=minecraft:item] run kill @e[type=minecraft:item]")
    lines.append(f"cmd {husk_for(skill)}")
    lines.append("wait 10")
    lines.append("cmd skyabyss skill mana @s full")
    # 録画は、撃つ CLIP_PRE tick 前から。撃つのは cmd で、録画の間に進む
    lines.append(f"record {skill} {total} {CLIP_FPS}")
    lines.append(f"wait {CLIP_PRE}")
    lines.append("cmd skyabyss skill cast @s 1")
    lines.append("recwait")
    lines.append("cmd skyabyss skill cooldowns @s")
    # 移動する技が落ち着くまで待ってから、次の撮影へ (体を戻す前)。天の驟雨は降る光が終わるまで
    lines.append("wait 30")
    if sky:
        lines.append(f"wait {SKY_RAIN_TICKS}")
    return lines


def main() -> None:
    out: list[str] = []
    out.append("# 技の見た目 (撮影台本)。tools/gen/shots/gen_skill_fx_shots.py が作る。手で直さず、生成器を直す。")
    out.append("# 撮影: bash tools/dev/mc_run.sh runShots -Pscript=tools/shots/skill_fx.txt -PshotsName=skill_fx")
    out.append("# 一技 = 一つの動画 (record <技> <tick> <fps>)。run/shots/skill_fx/<技>/0000.png … に書く。")
    out.append("# 録画の間は /tick rate 5 (論理時刻で撮る)。終わったら tools/site/encode_skill_fx_clips.py で webp にする。")
    out.append("world skill_fx flat")
    out.append("size 1280 720")
    out.append("timeout 7200")
    out.append("render 6")
    out.append("clouds off")
    out.append("guiscale 2")
    out.append("cmd time set noon")
    out.append("cmd gamerule doWeatherCycle false")
    out.append("cmd weather clear")
    out.append("cmd gamemode survival")
    out.append("cmd gamerule doMobLoot false")
    out.append("camera 0 -60 0 0 0")
    out.append("hud off")
    out.append("cmd skyabyss cinematic stop")
    out.append("waitchunks")
    out.append("chat clear")
    out.append("wait 200")
    # 入ったあとの題字 (最初の一本に残っていた) が消えてから、遅くして撮る
    out.append("wait 240")
    out.append(f"cmd tick rate {CLIP_TICK_RATE}")
    out.append("wait 20")
    out.append("")
    clips: dict[str, dict] = {}
    for skill, rel, imp, lin, bow in SKILLS:
        sky = skill == "sky_rain"
        total = clip_ticks(lin, sky)
        # 当たりの絵 (印): 撃った tick からの当たり (imp) の 1 tick 後。録り始めからは CLIP_PRE を足す
        impact_tick = CLIP_PRE + imp + 1
        clips[skill] = {
            "fps": CLIP_FPS,
            "ticks": total,
            "frames": -(-total * CLIP_FPS // 20),
            "impact_frame": round(impact_tick * CLIP_FPS / 20),
        }
        out.append(f"# ---- {skill} (昼・三人称の背中側。{total} tick)")
        out.extend(block(skill, lin, bow, total, sky))
        out.append("")
    out.append("cmd tick rate 20")
    out.append("perspective first")
    out.append("cmd time set noon")
    out.append("cmd skyabyss skill cooldowns @s")
    out.append("quit")
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(out) + "\n")
    with open(CLIPS, "w", encoding="utf-8", newline="\n") as f:
        json.dump(clips, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print("wrote", OUT, "lines", len(out), "and", CLIPS.name)


if __name__ == "__main__":
    main()
