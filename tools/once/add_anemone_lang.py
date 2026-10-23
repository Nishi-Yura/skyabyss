"""灯 (docs/design/23) の lang を ja_jp / en_us に足す。一度だけ流す (すでにあれば書き換える)。python -I tools/once/add_anemone_lang.py"""
import json
import re
from pathlib import Path

LANG = Path(__file__).resolve().parents[2] / "src/main/resources/assets/skyabyss/lang"

JA = {
    "item.skyabyss.glow_anemone": "光る海のイソギンチャク",
    "item.skyabyss.glow_anemone.desc": "手に持つと、暗い水をうっすら照らす。",
    "item.skyabyss.glow_anemone.desc2": "闇の水路の灯りの台にともすと、灯が残る。",
    "block.skyabyss.anemone_pedestal": "灯りの台",
    "block.skyabyss.anemone_bed": "灯りの苗床",
    "block.skyabyss.anemone_tally": "灯りの数え石",
    "message.skyabyss.lamp_lit": "灯りがともった（%s / %s）",
    "message.skyabyss.lamp_already": "この台には、もう灯りがともっている",
    "message.skyabyss.lamp_enough": "ともせる灯りは %s つまで。もう十分に明るい",
    "message.skyabyss.lamp_needs": "灯りの台だ。光る海のイソギンチャクを持って触れよう",
    "message.skyabyss.lamp_bed_take": "光る海のイソギンチャクを摘んだ（手持ち %s / %s）",
    "message.skyabyss.lamp_bed_full": "いまは摘めない（ともした灯りと手持ちで %s つぶん）。ともせば、また育つ",
    "message.skyabyss.lamp_tally": "数え石: 灯 %s / %s の台。%s つともせば、出口の戸が開く（灯は %s つまで）",
    "message.skyabyss.lamp_gate_open": "水路の奥で、重い戸が開いた。出口の泡の柱へ",
    "message.skyabyss.lamp_band": "灯りの台に光をともせ（%s / %s・手持ち %s）",
    "message.skyabyss.lamp_band_open": "出口の戸が開いた。泡の柱へ向かえ",
    "message.skyabyss.whisper.maze_entry": "苗床の光る海のイソギンチャクを持って、暗い水の台にともせ。灯りのそばでは魚が静まる。三つともれば、出口の戸が開く",
    "sign.skyabyss.maze.1": "灯を携えよ",
    "sign.skyabyss.maze.2": "暗い水の台に",
    "sign.skyabyss.maze.3": "光をともせば",
    "sign.skyabyss.maze.4": "魚は静まる",
}
EN = {
    "item.skyabyss.glow_anemone": "Glowing Anemone",
    "item.skyabyss.glow_anemone.desc": "Held in the hand, it faintly lights the dark water.",
    "item.skyabyss.glow_anemone.desc2": "Kindle it on a lamp stand in the dark waterway.",
    "block.skyabyss.anemone_pedestal": "Lamp Stand",
    "block.skyabyss.anemone_bed": "Anemone Bed",
    "block.skyabyss.anemone_tally": "Lamp Tally Stone",
    "message.skyabyss.lamp_lit": "A lamp is lit (%s / %s)",
    "message.skyabyss.lamp_already": "This stand is already lit",
    "message.skyabyss.lamp_enough": "You can light only %s lamps. It is bright enough",
    "message.skyabyss.lamp_needs": "A lamp stand. Touch it holding a glowing anemone",
    "message.skyabyss.lamp_bed_take": "You picked a glowing anemone (carrying %s / %s)",
    "message.skyabyss.lamp_bed_full": "Nothing to pick now (lit and carried make %s). Light one and it grows back",
    "message.skyabyss.lamp_tally": "Tally stone: stands lit %s / %s. Light %s and the exit door opens (up to %s lamps)",
    "message.skyabyss.lamp_gate_open": "Deep in the waterway, a heavy door opened. Make for the bubble column",
    "message.skyabyss.lamp_band": "Kindle the lamp stands (%s / %s, carrying %s)",
    "message.skyabyss.lamp_band_open": "The exit door is open. Head for the bubble column",
    "message.skyabyss.whisper.maze_entry": "Take a glowing anemone from the bed and kindle it on the stands in the dark water. Fish grow calm near a lamp. Light three and the exit door opens",
    "sign.skyabyss.maze.1": "Carry the light",
    "sign.skyabyss.maze.2": "Kindle the stands",
    "sign.skyabyss.maze.3": "Fish are calm",
    "sign.skyabyss.maze.4": "near a lamp",
}


def patch(name, entries):
    p = LANG / name
    text = p.read_text(encoding="utf-8")
    json.loads(text)  # 壊れていないこと
    new_keys = []
    for k, v in entries.items():
        line = f'  "{k}": {json.dumps(v, ensure_ascii=False)}'
        pat = re.compile(r'^  "' + re.escape(k) + r'": .*?(,?)$', re.M)
        if pat.search(text):
            text = pat.sub(lambda m: line + m.group(1), text, count=1)
        else:
            new_keys.append(line)
    if new_keys:
        end = text.rstrip().rfind("}")
        body = text[:end].rstrip()
        text = body + ",\n" + ",\n".join(new_keys) + "\n}\n"
    json.loads(text)
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


patch("ja_jp.json", JA)
patch("en_us.json", EN)
print("ok")
