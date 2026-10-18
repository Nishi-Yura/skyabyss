"""P0-1: one source of truth for the duplicated storm lang keys.
Names follow docs/design/12 (the Mantle series) = the skyabyss_storm values;
descriptions come from skyabyss_storm_gear (longer). Keys live in skyabyss_storm_gear only.
"""
import json, io
A = 'src/main/resources/assets/'
NAME_FROM_STORM = {
    'item.skyabyss.thunder_wing_helmet', 'item.skyabyss.thunder_wing_chestplate',
    'item.skyabyss.thunder_wing_leggings', 'item.skyabyss.thunder_wing_boots',
    'item.skyabyss.storm_anklet', 'item.skyabyss.thunder_wing_shard',
}


def load(p):
    return json.load(open(p, encoding='utf8'))


def save(p, d):
    with io.open(p, 'w', encoding='utf8', newline='\n') as f:
        f.write(json.dumps(d, ensure_ascii=False, indent=2) + '\n')


for loc in ('ja_jp', 'en_us'):
    ps = A + 'skyabyss_storm/lang/%s.json' % loc
    pg = A + 'skyabyss_storm_gear/lang/%s.json' % loc
    s, g = load(ps), load(pg)
    for k in [k for k in s if k in g]:
        if k in NAME_FROM_STORM:
            g[k] = s[k]
        del s[k]
    save(ps, s)
    save(pg, g)
    # beats/opening
    po = A + 'skyabyss_opening/lang/%s.json' % loc
    pb = A + 'skyabyss_beats/lang/%s.json' % loc
    o, b = load(po), load(pb)
    if 'opening.skyabyss.title' in o and 'opening.skyabyss.title' in b:
        del b['opening.skyabyss.title']
        save(pb, b)
