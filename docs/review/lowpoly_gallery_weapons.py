# -*- coding: utf-8 -*-
# 武器の低ポリ 3 案の選定ページの manifest をまとめる。案は武器ごとに選べるよう、同じシートで 4 つの題に分ける
import json
from pathlib import Path

here = Path(__file__).parent
base = json.loads((here / "lowpoly_roughs" / "weapons_manifest.json").read_text(encoding="utf-8"))
sheet = base["sheets"]
weapons = [
    ("lp_w_whale", "鯨骨の大剣", {"a": "肋の曲刃", "b": "牙の大剣（尾びれの鍔）", "c": "背骨の大剣（椎骨の柱）"}),
    ("lp_w_tide", "潮殻の三叉槍", {"a": "珊瑚の枝槍", "b": "巻貝と帆立", "c": "三叉の牙（返しつきの銛）"}),
    ("lp_w_thunder", "雷翼の弓", {"a": "羽の剛弓", "b": "翼弓（風切羽が重なる）", "c": "雷の弓（稲妻状に折れる）"}),
    ("lp_w_ember", "余燼の戦斧", {"a": "割れ板の斧", "b": "二枚刃の鍛造斧", "c": "岩塊の戦斧"}),
]
subjects = []
for i, (sid, name, opts) in enumerate(weapons):
    subjects.append({
        "id": sid, "short": name, "kind": "低ポリ 3案", "title": name,
        "sheets": sheet if i == 0 else [],
        "extra": (base.get("extra", "") if i == 0 else "シートは一つ目の題（鯨骨の大剣）にまとめて載せています。"),
        "options": {k: {"name": v, "desc": ""} for k, v in opts.items()},
    })
subjects.append({
    "id": "lp_w_rarity", "short": "階級と強化", "kind": "決まりの確認", "title": "階級（通常・レア・伝説）と強化（+5・+10）の見た目",
    "sheets": [],
    "extra": "通常は枠と巨神の飾り1つ。レアは部品を1つ足し、縁は青み、刃の根元に短い光の筋。伝説は輪郭を変える大きな一点を足し、縁は金、光の筋が増える。強化はメッシュを変えず、+5 で縁が一段明るく破線の光、+10 で筋が端まで通り刃の稜が光る（粒子なし）。",
    "options": {"a": {"name": "このままでよい", "desc": ""}, "b": {"name": "直してほしい", "desc": "下の欄に書いてください"}},
})
manifest = {
    "title": "武器 低ポリ 3案",
    "intro": "武器を防具と同じ考え方で作り直す3案です。どの武器もその巨神の敵と同じ材質で作りました。A は1本の太い輪郭、B は輪郭を広げる（翼・扇・二枚刃）、C は部品を積む・尖らせる案です。"
             "武器ごとに選べます。階級と強化の見た目は、★の案（岩鯨 A・潮殻 A・雷翼 B・余燼 A）で例を作りました。選んだ方向で残りの武器もすべて作ります。",
    "done": ["巨神4体の背中を、選んだ巨神の形と色に建て直した"],
    "subjects": subjects,
}
(here / "lowpoly_gallery_weapons.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1), encoding="utf-8")
print([s["id"] for s in subjects])
