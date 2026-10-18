# -*- coding: utf-8 -*-
# 未決の一覧 (docs/design/00) と、この作業で出た判断をまとめて、回答ページ (docs/review/_decisions/index.html) を作る。
# 回答は artifact の db の "answers" に {option, note, at} で入る (option は "now" / "alt1".. / "other")。
import json
import re
from pathlib import Path

here = Path(__file__).parent
src = (here.parent / "design" / "00_decisions_needed.md").read_text(encoding="utf-8")

groups = []
cur = None
for line in src.splitlines():
    m = re.match(r"^## (\d+)\. (.+)$", line)
    if m:
        num = int(m.group(1))
        cur = None if num == 1 else {"title": m.group(2), "items": []}
        if cur:
            groups.append(cur)
        continue
    if line.startswith("## "):
        cur = None
        continue
    if not cur or not line.startswith("| ") or line.startswith("| #") or line.startswith("| ---"):
        continue
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    if len(cells) == 5:
        num, q, now, alts, doc = cells
    elif len(cells) == 4:
        num, q, now, doc = cells
        alts = ""
    else:
        continue
    strip = lambda s: re.sub(r"\[([^\]]+)\]\([^)]+\)", r"\1", s).replace("`", "")
    alt_list = [a.strip() for a in re.split(r"／", strip(alts)) if a.strip() and a.strip() != "—"]
    cur["items"].append({"id": "d" + re.sub(r"[^0-9A-Za-z]", "", num.split("・")[0]), "num": num, "q": strip(q),
                         "now": strip(now), "alts": alt_list, "doc": strip(doc)})

session = {"title": "今回の作業で出た判断", "items": [
    {"id": "s_kaeshi", "num": "S1", "q": "返し刃（常に効く技）を技の枠に置けるようにするか",
     "now": "枠に置かない（常に効く技は枠不要、と案内を出す）", "alts": ["枠に置ける能動の技に作り直す"], "doc": "04"},
    {"id": "s_dungeon", "num": "S2", "q": "背中のダンジョンをパーティごとの別の場所（インスタンス）にするか",
     "now": "巨神ごとに一つの背中をみんなで共有", "alts": ["パーティ（ソロ含む）ごとに別の複製。同じ背中のディメンション内に間隔をあけて建て、使い終わったら再利用する（推奨）", "共有のまま、他のパーティを見えなくするだけ"], "doc": "02・10"},
    {"id": "s_mp_mid", "num": "S3", "q": "マルチの中くらいの問題（遅れて来た人の影が全員の進行を止める・観戦者が背中にいるとリセットが止まる・1 tick いただけで参加扱い・パーティの放置稼ぎ）を直すか",
     "now": "未対応", "alts": ["今直す（推奨。S2 をインスタンスにするなら一部は不要になる）", "S2 の結論のあとで直す"], "doc": "beta2_proposals 4章"},
    {"id": "s_level5", "num": "S4", "q": "序盤の目的の帯「周りを探索して強くなろう（目安 Lv5）」の目安レベル",
     "now": "Lv5", "alts": ["Lv4", "Lv6", "数字を出さない"], "doc": "17"},
    {"id": "s_bonehill", "num": "S5", "q": "骨の丘の目的の帯に距離と方角を出すか",
     "now": "距離と方角を出す", "alts": ["方角だけ（前触れを効かせる）"], "doc": "17"},
    {"id": "s_song_rain", "num": "S6", "q": "星が欠ける歌を雨の夜には起こさない（晴れるまで遅らせる）",
     "now": "晴れた夜だけ（5 日目の詰み止めあり）", "alts": ["雨でも起こす"], "doc": "06"},
    {"id": "s_tablet", "num": "S7", "q": "看板に代わる碑の文を出す条件",
     "now": "見るだけで出る（4.5 ブロック以内で目を向ける）＋右クリック", "alts": ["右クリックでだけ出す"], "doc": "19"},
    {"id": "s_tablet_look", "num": "S8", "q": "碑の見た目（1 案だけで作った）",
     "now": "今のまま", "alts": ["ラフ 3 案を作って選ぶ"], "doc": "19"},
    {"id": "s_adv", "num": "S9", "q": "進捗の数と隠し方（36 個、潮殻以降と Lv30 は隠し）",
     "now": "今のまま", "alts": ["減らす", "隠しを増やす", "隠しを減らす"], "doc": "19"},
    {"id": "s_cine_lines", "num": "S10", "q": "場面の台詞（天の声「空は、まだ眠っていない。」「煙をたどれ。人の居る所へ。」など）",
     "now": "今のまま", "alts": ["書き直す（メモに方向を）"], "doc": "20"},
    {"id": "s_heart_scene", "num": "S11", "q": "心臓の登場の場面の台詞を巨神ごとに変えるか",
     "now": "4 体共通", "alts": ["巨神ごとに書く"], "doc": "20"},
    {"id": "s_intro_len", "num": "S12", "q": "心臓の登場の長さ",
     "now": "4 秒", "alts": ["2 秒", "6 秒"], "doc": "02・08・12・13"},
    {"id": "s_storm_rhythm", "num": "S13", "q": "雷翼の心臓（段階 2）の高い・低いの拍",
     "now": "高 8 秒 / 低 8 秒", "alts": ["高 6 / 低 10（殴りやすく）", "高 10 / 低 6"], "doc": "12"},
    {"id": "s_weapon_motion", "num": "S14", "q": "プレイヤーの武器ごとの振り（大剣の断ち下ろし・槍の突きなど）と弓の反動",
     "now": "今のまま（試遊で調整）", "alts": ["弓の反動を強く", "珊瑚の盾にも殴りの動きを付ける"], "doc": "15"},
    {"id": "s_boss_sfx", "num": "S15", "q": "岩鯨の心臓・番兵長の技の音（今はバニラの音のまま）を専用の音にするか",
     "now": "バニラのまま", "alts": ["専用の音を作る"], "doc": "02"},
    {"id": "s_grave_mode", "num": "S16", "q": "死亡のゲームルール skyabyssDeathMode を数字（0〜3）でなく名前（grave など）で指定できるようにするか",
     "now": "数字", "alts": ["名前でも指定できるようにする"], "doc": "11"},
    {"id": "s_grave_rescue", "num": "S17", "q": "語り部の救いで、救う墓を選べるようにするか",
     "now": "いちばん古い墓から一つずつ", "alts": ["墓を選べる画面を作る"], "doc": "11"},
    {"id": "s_ebb_once", "num": "S18", "q": "二章の大干潮（世界で一度）のあとに二章に入った人",
     "now": "道は歩けない（床が海底に残るだけ）", "alts": ["人ごとに一度起こす", "二章に入った人がいれば月に一度起こす"], "doc": "08"},
    {"id": "s_conch", "num": "S19", "q": "潮見の貝をなくした人の作り直し",
     "now": "OP のコマンドだけ", "alts": ["語り部が渡し直す", "難破船の箱にも入れる"], "doc": "08"},
    {"id": "s_tide_heart_glow", "num": "S20", "q": "潮殻の心臓（B 珊瑚の殻）の核の水色の光が箱のモデルより目立たない",
     "now": "今のまま", "alts": ["核の光を強くする"], "doc": "15"},
    {"id": "s_storm_back_tone", "num": "S21", "q": "雷翼の背中のブロックがモデル A より少し暗い灰色",
     "now": "今のまま", "alts": ["明るい青灰に寄せる"], "doc": "12"},
    {"id": "s_archer_hood", "num": "S22", "q": "肋の射手 C の頭巾が大きく、遠くで箱版と印象が違う",
     "now": "今のまま", "alts": ["頭巾を小さくする"], "doc": "15"},
    {"id": "s_whale_fin_pivot", "num": "S23", "q": "岩鯨の胸びれの回転の中心が左右で非対称（26 / -31）",
     "now": "今のまま", "alts": ["±28.5 に揃える（アニメも合わせる）"], "doc": "15"},
    {"id": "s_lowpoly_next", "num": "S24", "q": "低ポリ化の次の順番（敵・人物・巨神は決定済み）",
     "now": "武器 → 防具", "alts": ["防具 → 武器", "アイテムの手持ちと地面の見た目も含める"], "doc": "15・16"},
    {"id": "s_ch_later", "num": "S25", "q": "五章（深淵）以降の中身を作り始める時期",
     "now": "巨神編を試遊してから", "alts": ["今から設計を始める"], "doc": "01"},
]}
groups.insert(0, session)

total = sum(len(g["items"]) for g in groups)
data = json.dumps(groups, ensure_ascii=False)
tpl = (here / "decisions_page_template.html").read_text(encoding="utf-8")
out = here / "_decisions"
out.mkdir(exist_ok=True)
(out / "index.html").write_text(tpl.replace("/*DATA*/[]", data).replace("{{TOTAL}}", str(total)), encoding="utf-8")
print(total, "items in", len(groups), "groups")
