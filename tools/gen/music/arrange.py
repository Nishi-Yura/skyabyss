# -*- coding: utf-8 -*-
"""曲の組み立て (都の曲 track_capital.py の作りを、ほかの曲でも使える形にまとめたもの)。

曲のファイル (track_<id>.py) は Song を作り、区切りごとに旋律・和音・分散和音・低音・打楽器を置いて build() で返す。
- 楽器は音源 (GeneralUser GS) の (bank, program)。ROLES に名前をつけてある。打楽器は KIT の鍵盤の番号
- 1 周ぶん置き、はみ出た余韻と残響は頭へ折り返す (Song.render の loop)。終わりから頭へ途切れずにつながる
- 組 (group) ごとに鳴っている所の大きさをそろえてから混ぜる (levels。dB)
- 検め (check.py) のために CHORD_PLAN (和音の並びと始まりの小節) と、旋律の組 (MELODY_GROUPS) を曲のファイルに置く
"""
import numpy as np

import gm
import sf2
import synth as S
from score import bass_note, chord, humanize, melody, nearest, pitch, progression, voicing

# 役 → (bank, program, 音程の補い)
ROLES = {
    "flute": (0, 73, 0), "piccolo": (0, 72, 0), "recorder": (0, 74, 0), "whistle": (24, 75, 0), "ocarina": (0, 79, 0),
    "panflute": (0, 75, 0), "shakuhachi": (0, 77, 0), "oboe": (0, 68, 0), "english_horn": (0, 69, 0),
    "clarinet": (0, 71, 0), "bassoon": (0, 70, 0),
    "horn": (0, 60, 0), "horn_solo": (1, 60, 0), "trumpet": (0, 56, 0), "trombone": (0, 57, 0), "brass": (0, 61, 0),
    "violin": (0, 40, 0), "viola": (0, 41, 0), "cello": (0, 42, 0), "contrabass": (0, 43, 0),
    "strings": (0, 49, 0), "strings_fast": (0, 48, 0), "tremolo": (0, 44, 0), "pizz": (0, 45, 0),
    "choir": (0, 52, -20), "oohs": (0, 53, 0),
    "harp": (0, 46, 0), "guitar": (0, 24, 0), "mandolin": (16, 25, 0), "koto": (0, 107, 0), "sitar": (0, 104, 0),
    "dulcimer": (0, 15, 0), "harpsichord": (0, 6, 0), "accordion": (0, 21, 0),
    "celesta": (0, 8, 0), "glock": (0, 9, 0), "musicbox": (0, 10, 0), "tubular": (0, 14, 0), "church": (8, 14, 0),
    "piano": (0, 0, 0),
    "bass": (0, 32, 0), "timpani": (0, 47, -18), "taiko": (0, 116, 0),
    "kit": (120, 48, 0), "std_kit": (120, 0, 0),
}
# 打楽器の鍵盤 (Orchestral Kit)
KIT = {"taiko": 36, "snare": 38, "rim": 37, "tamb": 54, "crash": 57, "cymbal": 59, "triangle": 81, "shaker": 82,
       "jingle": 83, "chimes": 84, "hand": 64, "bongo": 61, "claves": 75, "block": 76, "castanets": 39}


# 手太鼓 (G#3 で鳴る) を音名へずらすセント
HAND_TO = {"G#": 0.0, "A": 117.0, "G": -83.0, "F#": -183.0, "A#": 217.0, "F": -283.0, "B": 317.0, "E": -383.0,
           "C": 417.0, "D": -583.0, "C#": 517.0, "D#": -483.0}


# 和太鼓 (taiko。F1 の 37 セント下で鳴る) を音名へずらすセント (いちばん近い向きへ)
_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
TAIKO_TO = {n: ((i - 5 + 6) % 12 - 6) * 100.0 + 37.0 for i, n in enumerate(_NAMES)}


def play(role, m, dur, vel, bend=0.0):
    b, p, tune = ROLES[role]
    return sf2.bank().note(b, p, int(m), dur, gm.midi_vel(vel), pitch_bend_cents=tune + bend)


class Song:
    def __init__(self, bpm, bar, bars, seed=1):
        self.bpm = bpm
        self.bar = bar
        self.bars = bars
        self.spb = 60.0 / bpm
        self.loop = int(round(self.sec(bars * bar) * S.SR))
        self.mix = S.Mix(self.loop / S.SR + 9.0)
        self.rng = np.random.default_rng(seed)

    def sec(self, beat):
        return beat * self.spb

    def _put(self, y, beat, pan, wet, group, jitter=0.006):
        self.mix.add(y, max(0.0, humanize(self.sec(beat), jitter)), pan, 1.0, wet, group)

    # ------------------------------------------------------------ 旋律
    def line(self, role, text, bar0, group, pan=0.0, wet=0.35, vel=0.75, octave=0, legato=0.95, accent=0.06):
        """旋律 (score.melody の書き方)。長い音ほど少し強く"""
        for at, length, p, tie in melody(text, self.bar, bar0):
            if p is None:
                continue
            dur = self.sec(length) * (1.03 if tie else legato)
            v = vel * (1.0 + accent * min(length, 2) - accent * 0.5) * self.rng.uniform(0.95, 1.04)
            self._put(play(role, p + 12 * octave, dur, v), at, pan, wet, group)

    # ------------------------------------------------------------ 和音を伸ばす (敷き)
    def pad(self, role, prog_text, bar0, group, vel=0.5, center=57, count=4, spread=0.6, wet=0.45, overlap=1.04):
        for at, length, name in progression(prog_text, self.bar, bar0):
            vs = voicing(name, center, count)
            for i, p in enumerate(vs):
                pan = -spread + 2 * spread * i / max(1, count - 1)
                self._put(play(role, p, self.sec(length) * overlap, vel), at, pan, wet, group, 0.002)

    # ------------------------------------------------------------ 分散和音
    def arp(self, role, prog_text, bar0, group, pattern, step=0.5, vel=0.6, center=57, count=4, low=(43, 55), pan=0.0,
            wet=0.3, ring=1.6, accent_first=True):
        """pattern: 並べる音の番号の列 (0 = 低音、1.. = 和音の下から)。例 [0, 1, 2, 3, 2, 1]。step: 拍"""
        for at, length, name in progression(prog_text, self.bar, bar0):
            vs = voicing(name, center, count)
            seq = [bass_note(name, low[0], low[1])] + vs
            steps = int(round(length / step))
            for k in range(steps):
                idx = pattern[k % len(pattern)]
                if idx is None:
                    continue
                p = seq[idx % len(seq)] + 12 * (idx // len(seq))
                v = vel * (1.0 if (k == 0 and accent_first) else 0.68 + 0.12 * self.rng.random())
                self._put(play(role, p, self.sec(step) * ring, v), at + k * step, pan, wet, group, 0.007)

    # ------------------------------------------------------------ 低音
    def bassline(self, role, prog_text, bar0, group, pattern=((0, "R", 1.0),), vel=0.7, low=(36, 48), wet=0.1):
        """pattern: (拍, 'R' 根音 | '5' 五度 | '8' 8 度上 | 'P' 次の和音の根音への経過, 長さの割合)"""
        prog = progression(prog_text, self.bar, bar0)
        for i, (at, length, name) in enumerate(prog):
            root = bass_note(name, low[0], low[1])
            _, tones = chord(name)
            for beat, kind, frac in pattern:
                if beat >= length:
                    continue
                if kind == "R":
                    p = root
                elif kind == "5":
                    p = nearest((tones[0] + 7) % 12, root + 7)
                elif kind == "8":
                    p = root + 12
                else:
                    nxt = prog[(i + 1) % len(prog)][2]
                    p = bass_note(nxt, low[0], low[1]) - 1 if i + 1 < len(prog) else root
                dur = self.sec(min(length - beat, length * frac))
                self._put(play(role, p, dur * 0.95, vel), at + beat, 0.0, wet, group, 0.004)

    # ------------------------------------------------------------ 打楽器
    def perc(self, what, bar0, bars, pattern, group, pan=0.0, wet=0.2, role="kit", bend=0.0):
        """pattern: [(拍, 強さ)]。what: KIT の名前、または ('timpani', 音程) のような (役, 音程)。
        bend: 音程のずらし (セント)。手太鼓 (hand) は G#3 で鳴るので、曲の主音か和音の音へずらす (HAND_TO)"""
        for b in range(bars):
            for beat, v in pattern:
                if isinstance(what, tuple):
                    r, key = what
                else:
                    r, key = role, KIT[what]
                self._put(play(r, key, 0.4, v, bend), (bar0 + b) * self.bar + beat, pan, wet, group, 0.004)

    def timp(self, prog_text, bar0, pattern, group, wet=0.35, low=(41, 53)):
        """ティンパニを和音の根音に合わせて叩く (F2〜F3 の音域)。pattern: [(拍, 強さ)] を和音ごとに"""
        for at, length, name in progression(prog_text, self.bar, bar0):
            key = bass_note(name, low[0], low[1])
            for beat, v in pattern:
                if beat < length:
                    self._put(play("timpani", key, 0.6, v), at + beat, 0.0, wet, group, 0.004)

    def hit(self, role, key, beat, vel, group, pan=0.0, wet=0.3, dur=1.0):
        self._put(play(role, key, self.sec(dur), vel), beat, pan, wet, group, 0.0)

    def gliss(self, role, beat, start, scale_root, steps, beats=1.5, vel=0.5, group="harp", pan=0.3, mode=(0, 2, 4, 5, 7, 9, 11), down=False):
        base = pitch(start)
        root = pitch(scale_root + "4") % 12
        notes = []
        p = base
        while len(notes) < steps:
            if (p - root) % 12 in mode:
                notes.append(p)
            p += -1 if down else 1
        for i, q in enumerate(notes):
            self._put(play(role, q, 1.2, vel * (0.7 + 0.3 * i / steps)), beat + beats * i / steps, pan, 0.45, group, 0.0)

    # ------------------------------------------------------------ 書き出し
    def render(self, levels, mute=(), reverb=2.8, damp=0.55, target_rms_db=-19.0):
        lv = dict(levels)
        for g in mute:
            lv[g] = -200
        return self.mix.render(S.make_ir(reverb, damp=damp), lv, fade_in=0.0, fade_out=0.0, loop=self.loop,
                               target_rms_db=target_rms_db)
