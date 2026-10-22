# 画面・HUD の文字があふれないかを、字体の幅の表で調べる (日本語・英語、GUI 倍率 2・3・4)。
# 使い方: python tools/audit/check_text_fit.py [--verbose]   (リポジトリ直下から。ゲームは立てない)
#
# 字の幅は ObjectiveBandSelfTest / tools/audit/check_band_width.py と同じ表 (ASCII は標準字体の送り幅、日本語・記号は 9 px)。
# 画面の幅は 1920x1080 の窓で、倍率 2・3・4 の GUI 幅 960・640・480、高さ 540・360・270。
# 登録簿 (BOXES) の 1 件 = 「このキーの文は、この箱 (幅 px・行数) に入る」。箱はコードを読んで写した値。
#   mode "line"   : 折り返さず 1 行で描く場所。幅 (拡大込み) に入らなければ失敗
#   mode "wrap:N" : 幅で折り返して N 行まで描く場所 (超えた行は切られる)。N 行に入らなければ失敗
# 引数 (%s %d) は、日本語は全角 4 字、英語は 8 字の名前、数は 3 桁で置く。
# 文を直したらこれを流す。画面の寸法を変えたら BOXES も変える。
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCALES = {2: (960, 540), 3: (640, 360), 4: (480, 270)}


def glyph(c):
    if ord(c) >= 0x80:
        return 9
    if c == ' ':
        return 4
    if c in "!,.:;i|":
        return 2
    if c in "'l`":
        return 3
    if c in 'I[]t"(){}*':
        return 4
    if c in "fk<>":
        return 5
    if c in "@~":
        return 7
    return 6


def width(s):
    return sum(glyph(c) for c in s)


def lang(loc):
    out = {}
    for p in sorted(glob.glob(os.path.join(ROOT, "src/main/resources/assets/*/lang/" + loc + ".json"))):
        with open(p, encoding="utf-8") as f:
            out.update(json.load(f))
    return out


JA, EN = lang("ja_jp"), lang("en_us")
ARG = re.compile(r"%(?:\d+\$)?[-+0-9.,]*[sdfx]|%%")


def fill(text, loc, short=True):
    # short=True: 引数は数・短い語 (2 字) とみなす。名前が入る箇所は箱のほうを余裕のある値で見る
    def sub(m):
        t = m.group(0)
        if t == "%%":
            return "%"
        if t.endswith("d") or t.endswith("f") or t.endswith("x"):
            return "999"
        return "99" if short else ("ＸＸＸＸ" if loc == "ja" else "XXXXXXXX")

    t = ARG.sub(sub, text)
    return re.sub(r"§.", "", t)


def wrap(text, w):
    lines = []
    for para in text.split("\n"):
        cur = ""
        for ch in para:
            if width(cur + ch) > w and cur:
                sp = cur.rfind(" ")
                if sp > 0 and ch != " ":
                    lines.append(cur[:sp])
                    cur = cur[sp + 1:] + ch
                else:
                    lines.append(cur)
                    cur = ch.lstrip() if ch == " " else ch
            else:
                cur += ch
        lines.append(cur)
    return lines


class Box:
    def __init__(self, name, pattern, w, mode="line", scale=1.0, where=None, pre="", post="", exclude=None, scales=None):
        # w: 数 (固定幅) か、GUI 幅 g と倍率 s から返す関数 f(g, s)。scale: 文字の拡大 (題字 2.2・見出し 1.5)。
        # pre/post: 同じ行に並ぶ固定の文字 (印・余白) の幅の見積もり (px)
        self.name, self.re, self.w, self.mode, self.scale = name, re.compile(pattern), w, mode, scale
        self.pre, self.post, self.exclude, self.scales = pre, post, exclude and re.compile(exclude), scales or (2, 3, 4)

    def limit(self, s):
        g = SCALES[s][0]
        return self.w(g, s) if callable(self.w) else self.w


BOXES = []


def box(*a, **k):
    BOXES.append(Box(*a, **k))


def keys_of(tbl, pattern, exclude=None):
    r = re.compile(pattern)
    x = exclude and re.compile(exclude)
    return [k for k in tbl if r.fullmatch(k) and not (x and x.search(k))]


# ---------------------------------------------------------------- 登録簿
# (名前, キーの形, 幅, モード, 拡大, ...)
# ---- 技の画面 (K / SkillScreen, W=380)
box("skill: title", r"gui\.skyabyss\.skill\.title", 360)
box("skill: tree name+mark", r"skilltree\.skyabyss\.[a-z_0-9]+", 330 - 60)
box("skill: head mark", r"gui\.skyabyss\.skill\.calling2?_mark", 100)
box("skill: learn button", r"gui\.skyabyss\.skill\.learn(_up|_max)?", 54 - 8)
box("skill: save toggle", r"gui\.skyabyss\.skill\.loadout_save_(mode|on)", 32 - 8)
box("skill: calling short", r"gui\.skyabyss\.skill\.calling_choose_short", 50 - 8)
box("skill: respec button", r"gui\.skyabyss\.skill\.respec(_confirm)?", 50 - 8)
box("skill: tryon button", r"gui\.skyabyss\.skill\.tryon", 36 - 8)
box("skill: page button", r"gui\.skyabyss\.(skill\.page_build|skill\.page_tree|ext\.page)", 68 - 8)
box("skill: page tab (50 wide, shrinks to 0.7x)", r"gui\.skyabyss\.skill\.sub_[a-z]+", int((50 - 6) / 0.7))
box("skill: tryon ok/undo", r"gui\.skyabyss\.skill\.tryon_(ok|undo)", 50 - 8)
box("skill: tryon cancel", r"gui\.skyabyss\.skill\.tryon_cancel", 54 - 8)
box("skill: skill name", r"skill\.skyabyss\.[a-z_0-9]+", 128, "wrap:2", exclude=r"\.(flavor|effect|note)$")
# 技の刻印の名前 (箱 約 42 px) と構えの名前 (37 px) は SkillScreen.drawFit が 0.7 倍まで縮め、足りなければ「…」で切る (全文は下の効果欄に出る)。検査の対象外
box("skill: keystone name", r"keystone\.skyabyss\.[a-z_0-9]+", 162, exclude=r"\.effect$")
box("skill: keystone effect (0.7x)", r"keystone\.skyabyss\.[a-z_0-9]+\.effect", 162, scale=0.7)
box("skill: build heads", r"gui\.skyabyss\.skill\.(stance_head|chain_head|tag_head|keystone_head)", 170)
box("skill: stance cd", r"gui\.skyabyss\.skill\.stance_(cd|ready)", 110)
box("skill: points/mana line", r"gui\.skyabyss\.skill\.(points|points_tryon|mana)", 150)
box("skill: tryon banner", r"gui\.skyabyss\.skill\.tryon_banner", 300)
box("skill: rune head", r"gui\.skyabyss\.skill\.runes_(head|pick)", 180)
box("skill: rune legend", r"gui\.skyabyss\.skill\.rune_legend", 170)
box("skill: kind line", r"gui\.skyabyss\.skill\.(kind_keystone|kind_skill|kind_passive|rank|mastery_short|granted)", 190)
box("skill: info notice (INFO_W)", r"gui\.skyabyss\.skill\.(slot_locked|need_points|tryon_locked|rune_locked|passive_no_slot|slot_put|slot_how)", 186, "wrap:3")
box("skill: before calling", r"gui\.skyabyss\.skill\.before_calling", 186, "wrap:5")
box("skill: need lines", r"gui\.skyabyss\.skill\.(need_level|need_skill|need_calling|need_calling_deep|rank_capped|learn_cost)", 186, "wrap:3")
# ---- 天職の画面 (CallingScreen, W=300)
box("calling: title", r"gui\.skyabyss\.calling\.title2?", 280)
box("calling: lead", r"gui\.skyabyss\.calling\.lead2?", 280)
box("calling: buttons", r"gui\.skyabyss\.calling\.(inherit|confirm)", 70 - 8)
box("calling: later button", r"gui\.skyabyss\.calling\.later", 300 - 98 - 98 - 8)
box("calling: list name", r"calling\.skyabyss\.[a-z]+", 74)
box("calling: right text", r"calling\.skyabyss\.[a-z]+\.(role|trait\.desc)", 170, "wrap:4")
box("calling: right text 2", r"calling\.skyabyss\.[a-z]+\.trait", 170)
# ---- 成長の画面 (GrowthScreen, W=240)
box("growth: title", r"gui\.skyabyss\.growth\.title", 220)
box("growth: buttons", r"gui\.skyabyss\.growth\.(undo)", 44 - 8)
box("growth: confirm button", r"gui\.skyabyss\.growth\.confirm", 46 - 8)
box("growth: respec button", r"gui\.skyabyss\.growth\.respec(_confirm)?", 60 - 8)
box("growth: stat effect line", r"gui\.skyabyss\.growth\.effect\.[a-z]+", 200)
box("growth: points/frag lines", r"gui\.skyabyss\.growth\.(points|fragments|xp|max|hud_level)", 130)
# ---- 記録 (K の記録帳 RecordScreen, 本体 304x192)
box("record: tab", r"skyabyss_record\.tab\.[a-z]+", 64 - 8)
box("record: header", r"skyabyss_record\.h\.[a-z_]+", 120)
box("record: owner line", r"skyabyss_record\.gui\.(owner|inspecting)", 128, mode="line")
box("record: claim button", r"skyabyss_record\.gui\.(claim|recall|choose)", 80 - 8)
box("record: row label (item/plain)", r"skyabyss_record\.r\.[a-z_0-9]+", 100)
box("record: row value", r"skyabyss_record\.v\.[a-z_0-9]+", 114)
# ---- 旅嚢 (PouchScreen)
box("pouch: buttons", r"skyabyss_pouch\.screen\.(make|cant|repair_btn|back_btn|rebuild_btn)", 44 - 6)
box("pouch: relic button", r"skyabyss_reward\.pouch\.button", 44 - 6)
box("pouch: rebuild hint (clipped at W-70)", r"skyabyss_pouch\.screen\.rebuild_hint", 106)
box("pouch: category", r"skyabyss_pouch\.category\.[a-z_]+", 70)
box("pouch: text lines", r"skyabyss_pouch\.screen\.(unknown|unknown_hint|use_key|missing|withdraw_on|unpin|pin)", 152)
box("pouch: info wrap (3 lines under the separator)", r"skyabyss_pouch\.screen\.(empty|kept)", 152, "wrap:3")
box("pouch: count", r"skyabyss_pouch\.screen\.count", 60)
# ---- 鍛冶 (ResonanceForgeScreen, 右の板 152 / 本体 176)
box("forge: tab (scaled to 31)", r"gui\.skyabyss\.forge\.tab_[a-z]+", 31 * 1.5)
box("forge: label", r"gui\.skyabyss\.forge\.(material|inscribe_help|inscribe_no|extract_none|extract_hollow|imprint_rare_only|imprint_help|sockets_none|enhance_help|reforge_help|no_affix|trial_empty|empty)", 132, "wrap:5")
box("forge: lines", r"gui\.skyabyss\.forge\.(extract_rewrite|imprint_has|imprint_ready|imprint_cost|sockets|add_cost|next|max|not_enhanceable|dismantle_yields|trials|crystals|exchange_shards|exchange_owned)", 132)
box("forge: extract cost (drawWrapped 2 lines)", r"gui\.skyabyss\.forge\.extract_cost", 132, "wrap:2")
box("forge: help", r"gui\.skyabyss\.forge\.(insert_help|insert_ready|insert_full|insert_dup)", 132, "wrap:3")
box("forge: exchange state", r"gui\.skyabyss\.forge\.(exchange_state_[a-z_]+|exchange_ready|exchange_wait)", 132, "wrap:2")
for _k, _w in (("enhance", 60), ("dismantle", 60), ("trial", 46), ("pick", 62), ("keep", 66), ("extract_destroy", 60), ("extract_keep", 66),
               ("imprint", 132), ("insert", 132), ("add_socket", 132), ("remove", 36)):
    box("forge: button " + _k, r"gui\.skyabyss\.forge\." + _k, _w - 6)
box("forge: confirm (armed; narrowest button 44)", r"gui\.skyabyss\.forge\.confirm", 44 - 6)
box("forge: exchange pick (44)", r"gui\.skyabyss\.forge\.exchange_pick", 44 - 6)
box("forge: sub tabs 44", r"gui\.skyabyss\.forge\.sub_[a-z]+", 44 - 6)
box("forge: exchange titan button (132)", r"gui\.skyabyss\.forge\.exchange_titan", 132 - 8)
# ---- 試練の板 (TrialBoardScreen, W=360)
box("trial: title (1.5x)", r"gui\.skyabyss\.trial\.title", 100, scale=1.5)
box("trial: tab", r"gui\.skyabyss\.trial\.tab_[a-z]+", 96)
box("trial: abyss tab", r"gui\.skyabyss\.trial\.abyss(\.open|\.locked|\.on)?", ABYSS := 118 - 6)
box("trial: slot", r"trial\.skyabyss\.slot\.[a-z0-9_]+", 54)
box("trial: kind", r"trial\.skyabyss\.kind\.[a-z]+", 70)
box("trial: weekly state", r"gui\.skyabyss\.trial\.weekly_(done|drawn)", 110)
box("trial: help line", r"gui\.skyabyss\.trial\.help", 332)
box("trial: cycle", r"gui\.skyabyss\.trial\.cycle", 150)
box("trial: cleared", r"gui\.skyabyss\.trial\.(cleared_now|codex\.cleared)", 100)
# ---- 転生 (SoulScreen W=380 / RebirthScreen W=320 / BoardScreen W=330)
box("soul: title", r"gui\.skyabyss\.rebirth\.title", 360)
box("soul: C1 line", r"gui\.skyabyss\.rebirth\.(prep|now|count|boon_title|after_label)", 118)
box("soul: cost (C1 line)", r"gui\.skyabyss\.rebirth\.cost", 118)
box("soul: C1 wrap (locked/maxed)", r"gui\.skyabyss\.rebirth\.(maxed|locked)", 118, "wrap:7")
box("soul: C1 wrap (altar)", r"gui\.skyabyss\.rebirth\.altar", 118, "wrap:5")
box("soul: C2 titles", r"gui\.skyabyss\.rebirth\.(kept_title|reset_title|power_title|power_none)", 116)
box("soul: board title/shards (C3 col)", r"gui\.skyabyss\.rebirth\.(board_title|shards)", 118)
box("soul: C2 wrap", r"gui\.skyabyss\.rebirth\.(kept\.[a-z_]+|reset\.[a-z_]+)", 116, "wrap:3")
box("soul: go button (118)", r"gui\.skyabyss\.rebirth\.(button|confirm|go_hub)", 118 - 8)
box("soul: shard button", r"gui\.skyabyss\.rebirth\.shard_btn", 60 - 8)
box("soul: fx", r"gui\.skyabyss\.rebirth\.fx\.[a-z_]+", 116)
box("rebirth: gui title", r"skyabyss_rebirth\.gui\.title", 300)
box("rebirth: toggles (142)", r"skyabyss_rebirth\.gui\.toggle\.[a-z_]+", 142 - 8)
box("rebirth: gui lines", r"skyabyss_rebirth\.gui\.(titles|look|fixed|no_rebirth|locked_how|no_title)", 150)
box("rebirth: board", r"skyabyss_rebirth\.board\.(title|metric\.[a-z]+|empty|times|deaths)", 150)
box("rebirth: records", r"skyabyss_rebirth\.records\.(title|none|by|new)", 300)
box("rebirth: note", r"skyabyss_rebirth\.records\.note", 316)
# ---- 報酬の札 (HeartRewardScreen。倍率 2 の窓で panelW は最小 〜 460)
box("reward: title (2.2x, 1 line)", r"skyabyss_reward\.title\.[a-z_]+", lambda g, s: g - 60, scale=2.2)
box("reward: sub", r"skyabyss_reward\.sub\.[a-z_]+", lambda g, s: g - 60 - 40, "wrap:2")
box("reward: tag owned", r"skyabyss_reward\.tag\.owned", 60)
box("reward: req", r"skyabyss_reward\.(req|req_now)", 127)
box("reward: kind", r"skyabyss_reward\.kind\.[a-z]+", 100)
box("reward: equipped", r"skyabyss_reward\.equipped(\.none)?", 100, "wrap:2")
box("reward: dialog head (1.5x)", r"skyabyss_reward\.(confirm|later)\.title", 300 - 24, scale=1.5)
box("reward: dialog body", r"skyabyss_reward\.(confirm|later)\.body", 300 - 28, "wrap:3")
box("reward: dialog buttons (96)", r"skyabyss_reward\.(confirm|later)\.(yes|no)", 96 - 8)
box("reward: footer keys", r"skyabyss_reward\.keys\.[a-z]+", lambda g, s: g - 60)
box("reward: error", r"skyabyss_reward\.err\.[a-z]+", lambda g, s: g - 60)
box("reward: done (1.5x)", r"skyabyss_reward\.done", lambda g, s: g - 40, scale=1.5)
box("reward: done affix", r"skyabyss_reward\.done_affix", lambda g, s: g - 40)
box("reward: stat label", r"skyabyss_reward\.stat\.[a-z_]+", 70)
# ---- 墓・板・会話・補助
box("grave: title/count", r"gui\.skyabyss\.grave\.(title|count)", 150)
box("grave: share/footer (full line)", r"gui\.skyabyss\.grave\.(share|footer)", 296)
box("grave: pick button (56)", r"gui\.skyabyss\.grave\.pick", 56 - 8)
box("grave: place line", r"gui\.skyabyss\.grave\.place", 316 - 96)
box("grave: contents", r"gui\.skyabyss\.grave\.contents", 150)
box("grave: age", r"gui\.skyabyss\.grave\.age\.[a-z]+", 60)
box("board: title (quota shares the row)", r"gui\.skyabyss\.board\.title(_npc)?", 190)
box("board: accept (52)", r"gui\.skyabyss\.board\.accept", 52 - 8)
box("board: quota/next", r"gui\.skyabyss\.board\.(quota|next)", 150)
box("board: footer", r"gui\.skyabyss\.board\.footer", 296)
box("board: tag", r"gui\.skyabyss\.quest\.repeat\.[a-z]+", 50)
box("questlog: title", r"gui\.skyabyss\.quest\.log", 200)
box("questlog: track (88)", r"gui\.skyabyss\.quest\.(track|untrack)", 88 - 8)
box("questlog: lore hint", r"gui\.skyabyss\.quest\.lore_hint", 310)
box("questlog: locked/hidden rows (112)", r"gui\.skyabyss\.quest\.(locked\.[0-9]|hidden_row|empty)", 110)
box("accessory: labels", r"skyabyss_gear\.screen\.(charm|trinket|level|charm_key|tab_inventory|tab_[a-z]+)", 70)
box("accessory: tab (48)", r"skyabyss_gear\.screen\.tab_[a-z]+", 48 - 6)
box("party: buttons", r"gui\.skyabyss\.party\.(accept|decline)", 56 - 8)
box("party: small buttons (26)", r"gui\.skyabyss\.party\.(promote|kick)", 26 - 6)
box("party: leave/invite", r"gui\.skyabyss\.party\.(leave|invite)", 52 - 8)
box("party: lines", r"gui\.skyabyss\.party\.(members|alone|nobody)", 170)
box("party: full lines", r"gui\.skyabyss\.party\.(story|story_next|story_done|world|invite_from)", 300)
box("party: others head", r"gui\.skyabyss\.party\.others", 130)
box("travel: buttons (60)", r"gui\.skyabyss\.travel\.(depart|departing|disembark)", 60 - 8)
box("travel: title/sub", r"gui\.skyabyss\.travel\.(choose|leader_chooses|title\.[a-z_]+)", 130)
box("travel: detail", r"gui\.skyabyss\.travel\.(detail|here|lost)", 132 - 8)
box("travel: no waymarks", r"gui\.skyabyss\.travel\.no_waymarks2?", 132)
# ---- HUD
box("hud: toast (gain/unlock 160)", None, 144, "wrap:3") if False else None
box("hud: tutorial hint (1 line)", r"tutorial\.skyabyss\.[a-z_.]+", lambda g, s: g - 16 - 28 - 6, exclude=r"\.(hand|short|wheel)$|^tutorial\.skyabyss\.done$|\.(bind)$")
box("hud: boss cue label", r"gui\.skyabyss\.attack\.[a-z_0-9.]+", lambda g, s: g - 16 - 20)
box("hud: boss bar name+label+pct", r"gui\.skyabyss\.boss\.[a-z_0-9]+", 182 - 20 - 6 - 40)
box("hud: goal (ember)", r"gui\.skyabyss\.ember_goal\.[a-z_0-9]+", lambda g, s: g - 16, "wrap:2")
box("hud: chapter label (tab+title)", r"chapter\.skyabyss\.[a-z_0-9]+", lambda g, s: max(112, min(230, int(g * 0.24))) - 8 - 8, "wrap:2")

# 場所の札 (Notice.place): 題は 1.5 倍で幅 max(120, g/2) に 2 行まで、副題は幅 min(g-30, 340) に 2 行まで。章の余韻は 1.5 倍で 3 行まで
box("banner: place title (1.5x, 2 lines)", r"title\.skyabyss\.[a-z_]+", lambda g, s: max(120, g // 2), "wrap:2", scale=1.5)
box("banner: place sub (2 lines)", r"title\.skyabyss\.[a-z_]+\.sub", lambda g, s: min(g - 30, 340), "wrap:2")
box("banner: aftermath line (1.5x, 3 lines)", r"chapter\.skyabyss\.aftermath\.(titan_[a-z]+|preview)", lambda g, s: max(140, min(g - 40, int(g * 0.6))), "wrap:3", scale=1.5)
box("hud: downed/party labels", r"hud\.skyabyss\.(downed\.[a-z_]+|party_pos\.[a-z_.]+|ping\.[a-z]+|vow_band(_timer)?)", lambda g, s: g // 2)


# 目的の帯: 文は幅 (帯の幅 - 12) で折り返す。倍率 4 では帯が 115 px しかない
# ---- 文字のはみ出しの総点検で足した箱 (実行時は TextBounded の scissor が最後の守り。ここは「切られずに読める」ことを見る)
box("skill: effect (info panel 186 minus safety; 5 lines before the rune rows cut it)", r"skill\.skyabyss\.[a-z_0-9]+\.effect", 176, "wrap:5")
box("skill: flavor (info panel 186)", r"skill\.skyabyss\.[a-z_0-9]+\.flavor", 186, "wrap:3")
box("skill: signal party message (hint, 3 lines)", r"message\.skyabyss\.signal\.(foe|place)", lambda g, s: min(g - 30, 340), "wrap:3")
box("pouch: info line (item name + count, 176-24)", r"skyabyss_pouch\.screen\.(unknown|unknown_hint)", 152)
box("record: detail wrap (RW)", r"skyabyss_record\.gui\.before", 128, "wrap:3")
box("hud: skill hotbar tip", r"gui\.skyabyss\.skill\.(learn_tip|loadout_save_tip|loadout_load_tip)", 190, "wrap:4")
box("growth: effect desc (200, 4 lines)", r"gui\.skyabyss\.growth\.(desc\.[a-z]+|cap|fragments_desc)", 200, "wrap:4")
box("growth: respec info (200, 4 lines)", r"gui\.skyabyss\.growth\.respec_again", 200, "wrap:3")

def band(g, s):
    return max(112, min(230, int(g * 0.24))) - 12


def objective_keys():
    keys = {}
    for p in glob.glob(os.path.join(ROOT, "src/main/resources/data/skyabyss/quest/**/*.json"), recursive=True):
        with open(p, encoding="utf-8") as f:
            d = json.load(f)
        rel = os.path.relpath(p, os.path.join(ROOT, "src/main/resources/data/skyabyss/quest")).replace("\\", "/")[:-5]
        base = "quest.skyabyss." + rel.replace("/", ".")
        for st in d.get("stages", []):
            for ob in st.get("objectives", []):
                keys[base + "." + st["id"] + "." + ob["id"]] = ob.get("count", 1) > 1
    return keys


# ---------------------------------------------------------------- 知らせ (Notice) の経路
# Java のソースから、Notice.<種類>(player, "message...") の呼び出しを拾い、キー -> 種類 を作る
NOTICE = re.compile(r'Notice\s*\.\s*(narrate|hint|objective|levelUp|gain|unlock|alert|warn)\s*\(\s*[^,()]+(?:\([^()]*\))?\s*,\s*"([a-z0-9_.]+)"')


def notice_routes():
    routes = {}
    for p in glob.glob(os.path.join(ROOT, "src/main/java/**/*.java"), recursive=True):
        with open(p, encoding="utf-8") as f:
            src = f.read()
        for m in NOTICE.finditer(src):
            routes.setdefault(m.group(2), set()).add(m.group(1))
    return routes


# 種類ごとの箱 (NoticeClient の plan と同じ式)。GUI 幅 g から。行数の上限は plan の最大
def notice_box(kind, g):
    if kind in ("narrate", "hint", "objective"):
        return min(g - 40, max(160, int(g * 0.6))) - 0, 4          # 字幕: 4 行まで
    if kind == "levelUp":
        return min(g - 40, max(180, int(g * 0.5))), 2              # 中央: 2 行まで
    if kind == "alert":
        return max(120, int(g * 0.5)), 2                           # 上の帯: 2 行まで
    if kind in ("gain", "unlock"):
        return 160 - 16, 3                                         # 札: 3 行まで
    if kind == "warn":
        return g - 16, 1                                           # アクションバー: 1 行
    return None


def check_all(verbose=False):
    problems = []
    checked = 0
    for loc, tbl in (("ja", JA), ("en", EN)):
        for b in BOXES:
            for k in keys_of(tbl, b.re.pattern, b.exclude.pattern if b.exclude else None):
                text = fill(tbl[k], loc)
                for s in b.scales:
                    lim = b.limit(s)
                    checked += 1
                    if b.mode == "line":
                        w = width(text) * b.scale
                        if w > lim:
                            problems.append((loc, s, b.name, k, round(w), lim, text))
                    else:
                        n = int(b.mode.split(":")[1])
                        lines = wrap(text, int(lim / b.scale))
                        if len(lines) > n:
                            problems.append((loc, s, b.name, k, len(lines), n, text))
        # 目的の帯
        for k, counted in objective_keys().items():
            t = tbl.get(k)
            if t is None:
                continue
            text = "◆ " + fill(t, loc) + ("（9/9）" if counted else "")
            for s in (2, 3, 4):
                g = SCALES[s][0]
                lim = band(g, s)
                lines = wrap(text, lim)
                checked += 1
                if len(lines) > 4:
                    problems.append((loc, s, "hud: objective band (4 lines)", k, len(lines), 4, text))
        for k, v in tbl.items():
            if k.startswith("objective.skyabyss."):
                for s in (2, 3, 4):
                    lim = band(SCALES[s][0], s)
                    lines = wrap("◆ " + fill(v, loc), lim)
                    checked += 1
                    if len(lines) > 4:
                        problems.append((loc, s, "hud: objective band (4 lines)", k, len(lines), 4, v))
        # 知らせ
        routes = notice_routes()
        for k, kinds in routes.items():
            if k not in tbl:
                continue
            text = fill(tbl[k], loc)
            for kind in kinds:
                for s in (2, 3, 4):
                    g = SCALES[s][0]
                    bx = notice_box(kind, g)
                    if bx is None:
                        continue
                    lim, n = bx
                    checked += 1
                    lines = wrap(text, lim)
                    # 字幕・中央は高さで 3 行までに減る画面もある。字幕は 3 行を超えたら注意
                    cap = n if kind != "narrate" and kind != "hint" and kind != "objective" else 3
                    if len(lines) > cap:
                        problems.append((loc, s, "notice " + kind + " (≤" + str(cap) + " lines)", k, len(lines), cap, text))
    return problems, checked


if __name__ == "__main__":
    verbose = "--verbose" in sys.argv
    problems, checked = check_all(verbose)
    seen = set()
    for loc, s, name, k, got, lim, text in sorted(problems, key=lambda p: (p[2], p[3], p[0], -p[1])):
        key = (loc, name, k)
        if key in seen and not verbose:
            continue
        seen.add(key)
        print("%s x%d [%s] %s  got %s > %s  %s" % (loc, s, name, k, got, lim, text[:60].replace("\n", " ")))
    print(checked, "checks;", len(seen if not verbose else problems), "problems")
    sys.exit(1 if problems else 0)
