"""Slower, heavier idle/move curves for the whale and the thunder wing (docs/design/02 + 12, "重さ").  python tools/misc/titan_heavy_anim.py

whale swim: 8 s loop -> 12 s; head/jaw/tail/fluke 90 -> 60 deg/s (a full wave in 6 s instead of 4 s), fins 180 -> 120 deg/s,
  a longer lag down the spine (the head leads, the tail follows 90 degrees later, the fluke 150 later).
thunder wing fly: 3 s loop -> 4.5 s; wing beat 120 -> 80 deg/s (a stroke every 4.5 s), amplitude 28 -> 20 deg, a second harmonic so the down-stroke is
  slower and heavier than the recovery, the hand follows the wing by 80 degrees, and the tail by 60.
Every sine still completes whole turns in the loop length (so the loop has no pop).
"""
import json
import os

D = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'src', 'main', 'resources', 'assets', 'skyabyss', 'animations', 'entity')


def load(name):
    with open(os.path.join(D, name + '.animation.json'), encoding='utf-8') as f:
        return json.load(f)


def save(name, d):
    with open(os.path.join(D, name + '.animation.json'), 'w', encoding='utf-8') as f:
        json.dump(d, f, ensure_ascii=False, indent=1)
        f.write('\n')


w = load('stone_whale')
a = w['animations']['animation.stone_whale.swim']
a['animation_length'] = 12.0
a['bones'] = {
    'head': {'rotation': ['math.sin(query.anim_time * 60 - 60) * 1.4', 'math.sin(query.anim_time * 60) * 2.2', '0']},
    'jaw': {'rotation': ['(0.5 + 0.5 * math.sin(query.anim_time * 60 - 90)) * 6', '0', '0']},
    'tail': {'rotation': ['0', 'math.sin(query.anim_time * 60 - 90) * 4.5', '0']},
    'fluke': {'rotation': ['0', '0', 'math.sin(query.anim_time * 60 - 150) * 7']},
    'fin_left': {'rotation': ['0', '0', 'math.sin(query.anim_time * 120 - 30) * 5']},
    'fin_right': {'rotation': ['0', '0', '-math.sin(query.anim_time * 120 - 30) * 5']},
}
save('stone_whale', w)

t = load('thunder_wing')
f = t['animations']['animation.thunder_wing.fly']
f['animation_length'] = 4.5
stroke = 'math.sin(80 * query.anim_time) + 0.22 * math.sin(160 * query.anim_time - 40)'
hand = 'math.sin(80 * query.anim_time - 80) + 0.22 * math.sin(160 * query.anim_time - 120)'
f['bones'] = {
    'wing_l': {'rotation': ['0', '0', '-20 * (' + stroke + ')']},
    'wing_r': {'rotation': ['0', '0', '20 * (' + stroke + ')']},
    'hand_l': {'rotation': ['0', '0', '-10 * (' + hand + ')']},
    'hand_r': {'rotation': ['0', '0', '10 * (' + hand + ')']},
    'tail': {'rotation': ['0', '3 * math.sin(80 * query.anim_time - 60)', '0']},
}
save('thunder_wing', t)
print('done')
