"""会話画面・クエストログ・左の追跡表示の見本 (ゲームの外で、画面の配置と折り返しを確かめる)。

ゲーム (runClient) を起動せずに、DialogueScreen / QuestLogScreen / QuestHud と同じ寸法・色・折り返しで描く。
字はゲームのフォントではなく Windows の日本語フォントで代用し、字幅は全角 9・半角 6 で折り返す (自己テストと同じ)。
GUI の 1 単位を 2 画素で描く (GUI 拡大率 2 相当)。

実行 (リポジトリ直下):  python tools/rough/npc_ui_mock.py
出力: docs/review/npc_roughs/ui_mock.png
"""
from __future__ import annotations

import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
LANG = json.loads((ROOT / "src/main/resources/assets/skyabyss_npc/lang/ja_jp.json").read_text(encoding="utf-8"))
FACES = ROOT / "src/main/resources/assets/skyabyss/textures/gui/npc/face"
OUT = ROOT / "docs/review/npc_roughs/ui_mock.png"
S = 2  # GUI 1 単位 = 2 画素
NO_START = "、。，．,.・：；:;？！?!」』）)】〉》ー〜…‥ぁぃぅぇぉっゃゅょゎァィゥェォッャュョヮヵヶ々"


def font(px):
    for p in ("C:/Windows/Fonts/meiryo.ttc", "C:/Windows/Fonts/YuGothM.ttc", "C:/Windows/Fonts/msgothic.ttc"):
        if Path(p).exists():
            return ImageFont.truetype(p, px)
    return ImageFont.load_default()


F = font(8 * S)


def width(s):
    return sum(6 if ord(c) < 0x2000 else 9 for c in s)


def wrap(text, w):
    """LoreWrap と同じ考え: 一字ずつ詰め、行頭禁則の字は前の字と一緒に送る。"""
    out = []
    for para in text.split("\n"):
        line = ""
        for ch in para:
            if width(line + ch) > w and line:
                carry = ch
                if ch in NO_START and len(line) > 1:
                    carry = line[-1] + ch
                    line = line[:-1]
                out.append(line)
                line = carry
            else:
                line += ch
        out.append(line)
    return out


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


def text(d, x, y, s, color):
    d.text((x * S, y * S - 2), s, font=F, fill=rgb(color))


def rect(d, x0, y0, x1, y1, color):
    d.rectangle((x0 * S, y0 * S, x1 * S - 1, y1 * S - 1), fill=color)


def outline(d, x, y, w, h, color):
    d.rectangle((x * S, y * S, (x + w) * S - 1, (y + h) * S - 1), outline=rgb(color), width=S)


def scene(w, h):
    """背景: 夕方の村の空と地面 (画面がどこに乗るか分かる程度)。"""
    img = Image.new("RGB", (w * S, h * S))
    d = ImageDraw.Draw(img)
    for y in range(h * S):
        t = y / (h * S)
        c = (int(70 + 60 * t), int(90 + 40 * t), int(120 - 20 * t)) if t < 0.62 else (52, 74, 40)
        d.line((0, y, w * S, y), fill=c)
    return img


def dialogue(role, node, options, w=480, h=270):
    img = scene(w, h)
    d = ImageDraw.Draw(img, "RGBA")
    base = f"dialogue.skyabyss.{role}.{node}"
    panel_w = min(400, w - 16)
    text_x = 16 + 40 + 14
    text_w = panel_w - text_x - 12
    lines = wrap(LANG[base], text_w)
    opts = [(LANG[f"{base}.{o}"], k) for o, k in options]
    opt_lines = [wrap(t, text_w - 14) for t, _ in opts]
    opts_h = sum(max(13, len(ol) * 11 + 2) for ol in opt_lines)
    panel_h = max(40 + 54, 10 + len(lines) * 11 + 8 + opts_h + 10)
    x = (w - panel_w) // 2
    y = h - panel_h - 10
    for yy in range(h // 2, h):
        a = int(0x90 * (yy - h // 2) / (h // 2))
        d.line((0, yy * S, w * S, yy * S), fill=(0, 0, 0, a), width=S)
    rect(d, x, y, x + panel_w, y + panel_h, (16, 19, 24, 230))
    outline(d, x, y, panel_w, panel_h, 0x2E5552)
    outline(d, x + 2, y + 2, panel_w - 4, panel_h - 4, 0x17302F)
    fx, fy = x + 12, y + 10
    rect(d, fx - 2, fy - 2, fx + 42, fy + 42, (10, 12, 16, 255))
    outline(d, fx - 2, fy - 2, 44, 44, 0x2E5552)
    face = Image.open(FACES / f"{role}_neutral.png").convert("RGBA").resize((40 * S, 40 * S), Image.NEAREST)
    img.paste(face, (fx * S, fy * S), face)
    name = LANG[f"entity.skyabyss.{role}"]
    text(d, fx + 20 - width(name) // 2, fy + 46, name, 0x7FD6D0)
    tier = LANG["gui.skyabyss.npc.tier.2"]
    text(d, fx + 20 - width(tier) // 2, fy + 57, tier, 0x9A9AAE)
    text(d, fx + 20 - 18, fy + 68, "◆◆◇◇", 0x5FB8B0)
    ty = y + 10
    for l in lines:
        text(d, x + text_x, ty, l, 0xE9E3D4)
        ty += 11
    ty += 8
    for i, ((t, kind), ol) in enumerate(zip(opts, opt_lines)):
        hh = max(13, len(ol) * 11 + 2)
        if i == 0:
            rect(d, x + text_x - 4, ty - 2, x + panel_w - 8, ty + hh - 2, (46, 143, 136, 64))
        color = {"quest": 0xF2C14E, "leave": 0x9A9AAE}.get(kind, 0xE9E3D4)
        mark = "◆" if kind == "quest" else f"{i + 1}."
        text(d, x + text_x, ty, mark, color)
        ly = ty
        for l in ol:
            text(d, x + text_x + 14, ly, l, color)
            ly += 11
        ty += hh
    return img


def quest_log(w=480, h=270):
    img = scene(w, h)
    d = ImageDraw.Draw(img, "RGBA")
    rect(d, 0, 0, w, h, (0, 0, 0, 128))
    W, H, LW = 330, 206, 112
    left, top = (w - W) // 2, (h - H) // 2
    rect(d, left, top, left + W, top + H, (16, 19, 24, 240))
    outline(d, left, top, W, H, 0x2E5552)
    outline(d, left + 2, top + 2, W - 4, H - 4, 0x17302F)
    text(d, left + 10, top + 8, LANG["gui.skyabyss.quest.log"], 0x7FD6D0)
    rect(d, left + 8, top + 19, left + W - 8, top + 20, (23, 48, 47, 255))
    rect(d, left + LW + 10, top + 24, left + LW + 11, top + H - 8, (23, 48, 47, 255))
    rows = [("gui.skyabyss.quest.section.main", None, 0x5C5C6C), ("quest.skyabyss.main_song.title", True, 0xF2C14E),
            ("gui.skyabyss.quest.section.sub", None, 0x5C5C6C), ("quest.skyabyss.sub_song.title", False, 0x7FD6D0),
            ("gui.skyabyss.quest.section.done", None, 0x5C5C6C), ("quest.skyabyss.sub_shards.title", False, 0x8C8CA0)]
    y = top + 26
    for key, sel, color in rows:
        if sel is None:
            text(d, left + 10, y + 2, LANG[key], color)
        else:
            if sel:
                rect(d, left + 8, y, left + LW + 6, y + 12, (46, 143, 136, 80))
                text(d, left + 9, y + 2, "▸", color)
            text(d, left + 16, y + 2, LANG[key], color)
        y += 12
    bx = left + LW + 18
    bw = W - LW - 30
    text(d, bx, top + 26, LANG["quest.skyabyss.main_song.title"], 0xF2C14E)
    by = top + 40
    body = [(LANG["gui.skyabyss.quest.giver"].replace("%s", LANG["entity.skyabyss.storyteller"]), 0x8C8CA0), ("", 0)]
    body += [(l, 0xE9E3D4) for l in wrap(LANG["quest.skyabyss.main_song.inscriptions.journal"], bw)]
    body += [("", 0), (LANG["gui.skyabyss.quest.objectives"], 0x7FD6D0)]
    body += [("□ " + LANG["quest.skyabyss.main_song.inscriptions.read"] + "（2/3）", 0xE9E3D4), ("", 0)]
    body += [(LANG["gui.skyabyss.quest.progress"].replace("%s", "5", 1).replace("%s", "7", 1), 0x5C5C6C)]
    body += [(l, 0xF2C14E) for l in wrap(LANG["gui.skyabyss.quest.reward"].replace("%s", "経験値 470"), bw)]
    for l, c in body:
        if l:
            text(d, bx, by, l, c)
        by += 10
    text(d, left + 10, top + H - 19, LANG["gui.skyabyss.quest.lore_hint"], 0x5C5C6C)
    rect(d, left + W - 98, top + H - 24, left + W - 10, top + H - 8, (60, 60, 60, 255))
    outline(d, left + W - 98, top + H - 24, 88, 16, 0x000000)
    t = LANG["gui.skyabyss.quest.untrack"]
    text(d, left + W - 54 - width(t) // 2, top + H - 20, t, 0xFFFFFF)
    return img


def hud(w=480, h=270):
    img = scene(w, h)
    d = ImageDraw.Draw(img, "RGBA")
    text(d, 6, 6, "Lv 12", 0x9FE8FF)
    rect(d, 6, 17, 70, 22, (30, 40, 50, 255))
    rect(d, 7, 18, 40, 21, (127, 214, 208, 255))
    X, Y, WD = 6, 70, 150
    lines = []
    t = LANG["quest.skyabyss.main_song.inscriptions.read"] + "（2/3）"
    for k, l in enumerate(wrap(t, WD - 12)):
        lines.append((("・" if k == 0 else "") + l, 0 if k == 0 else 9))
    hh = 12 + len(lines) * 10 + 3
    rect(d, X - 3, Y - 3, X + WD + 3, Y + hh - 2, (10, 12, 16, 80))
    rect(d, X - 3, Y - 3, X - 2, Y + hh - 2, (242, 193, 78, 255))
    text(d, X + 1, Y, LANG["quest.skyabyss.main_song.title"], 0xF2C14E)
    yy = Y + 12
    for l, ind in lines:
        text(d, X + ind, yy, l, 0xE0DCD0)
        yy += 10
    return img


def main():
    shots = [
        ("会話: 語り部 (shadow3、メインの1段目が済む節)", dialogue("storyteller", "shadow3", [("go", "talk"), ("why", "talk")])),
        ("会話: 語り部の話題の節 (話題が多いとき)", dialogue("storyteller", "menu", [("shadow_seen", "quest"), ("song", "talk"), ("watcher_seen", "talk"),
                                                                       ("book", "talk"), ("whale", "talk"), ("bye", "leave")])),
        ("会話: 鯨追い (c_whale2、続き ▼ の節)", dialogue("whale_chaser", "c_whale2", [])),
        ("クエストログ (K)", quest_log()),
        ("左の追跡表示", hud()),
    ]
    pad, head = 16, 30
    w = max(i.width for _, i in shots)
    total_h = sum(i.height + head + pad for _, i in shots) + pad
    out = Image.new("RGB", (w + 2 * pad, total_h), (20, 20, 28))
    d = ImageDraw.Draw(out)
    y = pad
    for title, img in shots:
        d.text((pad, y), title, font=font(18), fill=(230, 230, 240))
        out.paste(img, (pad, y + head))
        y += img.height + head + pad
    OUT.parent.mkdir(parents=True, exist_ok=True)
    out.save(OUT)
    print("wrote", OUT)


if __name__ == "__main__":
    main()
