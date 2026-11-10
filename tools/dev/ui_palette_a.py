# -*- coding: utf-8 -*-
# UI の色を絵柄 A「夜空の硝子」へ置き換える一度きりの道具 (docs/design/56 の 2 章)。二度流しても同じ (置き換え先は置き換え元に無い)。
import io, os, re, sys
ROOT = 'src/main/java/jp/revati/skyabyss'
DIRS = ['client', 'endgame/client', 'rpg/client', 'party/client', 'story/ch5/client', 'abyss/arts/client', 'story/ch6/client']
SKIP = ('client/dev/', 'client/sky/', 'client/combat/', 'client/lowpoly/', 'client/entity/', 'client/opening/', 'client/prologue/', 'client/weapon/',
        'client/camera/', 'client/guide/', 'client/map/', 'rpg/client/skill/fx/', 'rpg/client/skill/SkillFx.java', 'client/ui/Golden.java',
        'client/cinematic/', 'client/story/', 'client/titan/MarkerArt.java')
MAP = {
    'F2C14E': 'E9C46A', '7FD6D0': '8FD0FF', '9FE8FF': '8FD0FF', '2A5E6A': '364064', '2E5552': 'C9A85C', '1D6E69': '2E4A86',
    '1E3A40': '1A2448', '17302F': '141C3A', '2A3A40': '222C52', '243440': '222C52', '1F4B54': '22386A',
    'E6E2D6': 'ECE8DC', 'A9AEBC': 'B4BBD6', 'E2D9C2': 'ECE8DC', '8E8A7C': '8892B6',
    '6E6654': '8A7440', 'C4BAA0': 'C9A85C', 'A0967C': 'A88C4C',
    '101418': '0A0E1E', '0A0C10': '0A0E1E', '0C0E12': '05070F', '0A0C12': '0A0E1E', '07090E': '05070F', '171B25': '101630', '10131A': '0C1022',
    '0A0E12': '0A0E1E', '2A2E38': '222A48', '343A48': '364064', '1C222C': '141A34', '1B202B': '141A34', '0C0E13': '0A0E1E', '0A0D14': '0A0E1E',
    '0A0D12': '0A0E1E', '4A5060': '4A5680', '12151C': '0C1022', '14181E': '05070F', '181D25': '10162E', '10141A': '0A0E1E', '2E3844': '364064',
    '8C98A4': '9AA4C4', '9AA0B0': 'A4ACC8', '5E6472': '8892B6', '59606F': '5A6690', '6A707C': '7682A6', '22282F': '1C2444',
}
pat = re.compile(r'0x([0-9A-Fa-f]{2})?([0-9A-Fa-f]{6})\b')
total = 0
for d in DIRS:
    for dp, _, fs in os.walk(os.path.join(ROOT, d)):
        for f in fs:
            p = os.path.join(dp, f).replace(os.sep, '/')
            rel = p[len(ROOT) + 1:]
            if not f.endswith('.java') or any(rel.startswith(s) for s in SKIP):
                continue
            raw = io.open(p, encoding='utf-8', newline='').read()
            n = [0]
            def rep(m):
                to = MAP.get(m.group(2).upper())
                if not to:
                    return m.group(0)
                n[0] += 1
                return '0x' + (m.group(1) or '') + to
            out = pat.sub(rep, raw)
            if n[0]:
                io.open(p, 'w', encoding='utf-8', newline='').write(out)
                total += n[0]
                print('%4d %s' % (n[0], rel))
print('total', total)
