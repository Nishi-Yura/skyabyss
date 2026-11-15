# -*- coding: utf-8 -*-
"""攻撃の粒を docs/design/59 の決まりに直す (一度だけ流す変換)。

使い方: python -I tools/once/convert_attack_particles.py <属性 STONE|WATER|STORM|EMBER|ABYSS|MOON|PAPER> <java ファイル> [...]

一つのファイルの中の、粒を出す文 (xxx.sendParticles(...) / warn(srv, ...) / level().addParticle(...) / level.addParticle(...)) を見て:
  消す: 見た目つきの予告 (.look(...)) の当たり (.onImpact) の中の粒 (当たりは client/combat/AttackFx の係)、
        赤い警告の点 (dust の赤)、一つずつ並べる線の点 (warn で数 1 の dust。予告は床の印、溜めは光の係)、
        生き物の tick の中で絶えずこぼれる dust (核のきらめき。核の光は崩れている間だけ)
  直す: ほかはその属性の mod の粒 (fx/FxParticles) に置き換え、一度の数を 24 までに
        種類: 破片 = ブロック・溶岩・落ちる物・墨・灰 / 光の粒 = 火花・END_ROD・炎・光・dust / 煙 = 雲・煙・しぶき・泡・爆発
  片づけ: 中身がなくなった for・if (中が局所の変数の宣言だけ) を消す
直した数・消した数を書き出す。直したあとは compileJava と目で確かめる。
"""
import io
import re
import sys

ELEMENT = sys.argv[1].upper()
FILES = sys.argv[2:]

SHARD_T = {"BLOCK", "ITEM", "LAVA", "FALLING_LAVA", "FALLING_WATER", "FALLING_DUST", "DRIPPING_LAVA", "SQUID_INK", "ASH", "ITEM_SNOWBALL",
           "DRIPPING_WATER", "FALLING_OBSIDIAN_TEAR", "LANDING_LAVA", "DAMAGE_INDICATOR"}
SMOKE_T = {"CLOUD", "SMOKE", "LARGE_SMOKE", "POOF", "SPLASH", "BUBBLE", "BUBBLE_POP", "BUBBLE_COLUMN_UP", "CURRENT_DOWN", "EXPLOSION",
           "EXPLOSION_EMITTER", "CAMPFIRE_COSY_SMOKE", "CAMPFIRE_SIGNAL_SMOKE", "WHITE_ASH", "DUST_PLUME", "GUST", "SMALL_GUST", "SNOWFLAKE",
           "SONIC_BOOM", "SWEEP_ATTACK", "SCULK_SOUL", "SOUL", "RAIN", "UNDERWATER", "DOLPHIN"}
# ほかは光の粒

FX = "jp.revati.skyabyss.fx.FxParticles"


def fx_expr(kind):
    return "fx(%s.Kind.%s)" % (FX, kind)


def kind_of(expr):
    m = re.search(r"ParticleTypes\.([A-Z_]+)", expr)
    if expr.strip().startswith("new BlockParticleOption") or expr.strip().startswith("new ItemParticleOption"):
        return "SHARD"
    if m:
        n = m.group(1)
        if n in SHARD_T:
            return "SHARD"
        if n in SMOKE_T:
            return "SMOKE"
        return "GLOW"
    return "GLOW"


def split_args(s):
    """s = 括弧の中身。トップレベルのカンマで分ける"""
    out, depth, cur = [], 0, ""
    for ch in s:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(cur)
            cur = ""
        else:
            cur += ch
    out.append(cur)
    return out


def is_red(expr):
    m = re.search(r"dust\(0x([0-9A-Fa-f]{6})", expr)
    if not m:
        return False
    v = int(m.group(1), 16)
    r, g, b = v >> 16, (v >> 8) & 0xFF, v & 0xFF
    return r >= 0xC0 and g <= 0x90 and b <= 0x90


START = re.compile(r"^\s*(?:[\w.()]+\.sendParticles\(|warn\(\w+,|level\(\)\.addParticle\(|level\.addParticle\(|this\.level\(\)\.addParticle\()")


def stmt_end(lines, i):
    depth = 0
    for j in range(i, len(lines)):
        code = re.sub(r'"(?:\\.|[^"\\])*"', '""', lines[j])
        for ch in code:
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
        if depth <= 0 and code.rstrip().endswith(";"):
            return j
    return i


def in_look_impact(lines, i):
    """見た目つきの予告の .onImpact(...) のラムダの中か (上へ戻って、まだ閉じていない onImpact を探す)"""
    depth = 0
    for j in range(i - 1, max(-1, i - 40), -1):
        code = re.sub(r'"(?:\\.|[^"\\])*"', '""', lines[j])
        for ch in reversed(code):
            if ch == ")" or ch == "}":
                depth += 1
            elif ch == "(" or ch == "{":
                depth -= 1
        if depth < 0 and ".onImpact(" in code:
            # その予告に look があるか (onImpact の行か、その上の数行)
            ctx = "\n".join(lines[max(0, j - 4):j + 1])
            return ".look(" in ctx
    return False


def in_client_tick(lines, i):
    ctx = "\n".join(lines[max(0, i - 6):i])
    return "isClientSide" in ctx


def convert(path):
    src = io.open(path, encoding="utf-8").read()
    lines = src.split("\n")
    out = list(lines)
    removed = converted = 0
    i = 0
    while i < len(lines):
        if not START.match(lines[i]):
            i += 1
            continue
        end = stmt_end(lines, i)
        body = "\n".join(lines[i:end + 1])
        if "FxParticles" in body:
            i = end + 1
            continue
        cm = re.search(r"(?:sendParticles|addParticle|warn)\(", body)
        open_at = cm.end() - 1
        head = body[open_at + 1:body.rindex(")")]
        args = split_args(head)
        is_warn = body.lstrip().startswith("warn(")
        tix = 1 if is_warn else 0
        # 人ごとに送る形 sendParticles(相手, 粒, 遠くまで, x, y, z, 数, dx, dy, dz, 速さ) は粒が 2 番目、数が 7 番目
        per_player = ("sendParticles(" in body) and len(args) == 11
        if per_player:
            tix = 1
        if len(args) <= tix:
            i = end + 1
            continue
        texpr = args[tix]
        remove = False
        if in_look_impact(lines, i):
            remove = True
        elif is_red(texpr):
            remove = True
        elif is_warn and "dust(" in texpr and len(args) > tix + 4 and args[tix + 4].strip() == "1":
            remove = True
        elif ("addParticle(" in body) and "dust(" in texpr and in_client_tick(lines, i):
            remove = True
        if remove:
            for j in range(i, end + 1):
                out[j] = None
            removed += 1
            i = end + 1
            continue
        kind = kind_of(texpr)
        new_args = list(args)
        lead = re.match(r"\s*", new_args[tix]).group(0)
        new_args[tix] = lead + fx_expr(kind)
        cix = 6 if per_player else tix + 4
        if "sendParticles" in body or is_warn:
            if len(new_args) > cix:
                c = new_args[cix].strip()
                if re.fullmatch(r"\d+", c) and int(c) > 24:
                    new_args[cix] = new_args[cix].replace(c, "24")
        prefix = body[:open_at + 1]
        suffix = body[body.rindex(")"):]
        newbody = prefix + ",".join(new_args) + suffix
        nl = newbody.split("\n")
        for j in range(i, end + 1):
            out[j] = None
        out[i] = "\n".join(nl)
        converted += 1
        i = end + 1
    text = "\n".join(l for l in out if l is not None)
    # 追い手の跡 (HeartPursuit.Theme) の粒
    text = re.sub(r'(HeartPursuit\.Theme\("[a-z]+", [0-9.]+f, )(ParticleTypes\.[A-Z_]+|new BlockParticleOption\([^;]*?defaultBlockState\(\)\))',
                  lambda m: m.group(1) + fx_expr("SHARD"), text)
    # 片づけ: 中が空 (か局所の宣言だけ) の for / if / else if を消す
    decl = re.compile(r"^\s*(?:final\s+)?(?:double|int|float|long|var|Vec3|BlockPos|boolean)\s+\w+\s*=.*;\s*$")
    changed = True
    while changed:
        changed = False
        ls = text.split("\n")
        for a in range(len(ls)):
            m = re.match(r"^(\s*)(?:for|if) \(.*\) \{\s*$", ls[a])
            if not m:
                continue
            ind = m.group(1)
            b = a + 1
            while b < len(ls) and not re.match("^" + re.escape(ind) + r"\}", ls[b]):
                b += 1
            if b >= len(ls):
                continue
            inner = ls[a + 1:b]
            if all((not x.strip()) or x.strip().startswith("//") or decl.match(x) for x in inner) and not ls[b].strip().startswith("} else"):
                # else が続く if は残す
                if ls[b].strip() != "}":
                    continue
                del ls[a:b + 1]
                text = "\n".join(ls)
                changed = True
                break
    if "fx(" + FX in text and "private static net.minecraft.core.particles.SimpleParticleType fx(" not in text:
        helper = ("    // 攻撃の粒は mod の %s の粒だけ (docs/design/59 の 2 章)\n"
                  "    private static net.minecraft.core.particles.SimpleParticleType fx(%s.Kind kind) {\n"
                  "        return %s.type(%s.Element.%s, kind);\n"
                  "    }\n\n") % (ELEMENT.lower(), FX, FX, FX, ELEMENT)
        # 最後の閉じ括弧の前に足す
        k = text.rstrip().rindex("}")
        text = text[:k] + "\n" + helper.rstrip("\n") + "\n" + text[k:]
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)
    print("%s: converted %d, removed %d" % (path, converted, removed))


for f in FILES:
    convert(f)
