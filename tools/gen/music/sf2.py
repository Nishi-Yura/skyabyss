# -*- coding: utf-8 -*-
"""SoundFont 2 (.sf2) を読んで、一音ずつ鳴らす (外のプログラムは使わない)。

音源: tools/sources/soundfont/GeneralUser-GS.sf2 (GeneralUser GS 2.0.3。使用条件は同じ所の LICENSE.txt。
git には入れない。無ければ README の場所から落とす)。

扱う決まり (SoundFont 2.04 の要点):
- 鍵盤と強さの範囲 (keyRange・velRange) で、プリセットの区分 → 楽器の区分 → サンプルを選ぶ
- 楽器の生成子は値そのもの、プリセットの生成子は足し算。全体の区分 (最初の、楽器・サンプルを持たない区分) は既定値
- 音程: rootKey (overridingRootKey か サンプルの元の高さ)、scaleTuning・coarse・fine・サンプルの補正
- ループ: sampleModes 1 (鳴らすあいだ回す)・3 (離すまで回し、離したら最後まで)
- 音量の包絡 (delay・attack・hold・decay・sustain・release。時間は timecents、sustain は cB)、initialAttenuation (EMU の決まりで ×0.4)
- 強さ → 大きさ (既定の変調。凹の曲線)、強さ → 明るさ (既定の変調。弱いほどこもる)
- 低域の通過 (initialFilterFc・Q。変調の包絡が Fc に掛かるときは包絡の形で動かす)
- ビブラートの LFO (vibLfoToPitch・freqVibLFO・delayVibLFO)、変調の LFO の音程 (modLfoToPitch)
- 左右 (pan)。ステレオのサンプルは左右の区分がそれぞれ pan を持つ
"""
import math
import os
import struct

import numpy as np
from scipy import signal

SR = 44100
HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_PATH = os.path.normpath(os.path.join(HERE, "..", "..", "sources", "soundfont", "GeneralUser-GS.sf2"))

# 生成子の番号
G = dict(startAddrsOffset=0, endAddrsOffset=1, startloopAddrsOffset=2, endloopAddrsOffset=3, startAddrsCoarseOffset=4,
         modLfoToPitch=5, vibLfoToPitch=6, modEnvToPitch=7, initialFilterFc=8, initialFilterQ=9, modLfoToFilterFc=10,
         modEnvToFilterFc=11, endAddrsCoarseOffset=12, modLfoToVolume=13, chorusEffectsSend=15, reverbEffectsSend=16,
         pan=17, delayModLFO=21, freqModLFO=22, delayVibLFO=23, freqVibLFO=24, delayModEnv=25, attackModEnv=26,
         holdModEnv=27, decayModEnv=28, sustainModEnv=29, releaseModEnv=30, keynumToModEnvHold=31,
         keynumToModEnvDecay=32, delayVolEnv=33, attackVolEnv=34, holdVolEnv=35, decayVolEnv=36, sustainVolEnv=37,
         releaseVolEnv=38, keynumToVolEnvHold=39, keynumToVolEnvDecay=40, instrument=41, keyRange=43, velRange=44,
         startloopAddrsCoarseOffset=45, keynum=46, velocity=47, initialAttenuation=48, endloopAddrsCoarseOffset=50,
         coarseTune=51, fineTune=52, sampleID=53, sampleModes=54, scaleTuning=56, exclusiveClass=57,
         overridingRootKey=58)
DEFAULTS = {G["initialFilterFc"]: 13500, G["delayModLFO"]: -12000, G["delayVibLFO"]: -12000,
            G["delayModEnv"]: -12000, G["attackModEnv"]: -12000, G["holdModEnv"]: -12000, G["decayModEnv"]: -12000,
            G["releaseModEnv"]: -12000, G["delayVolEnv"]: -12000, G["attackVolEnv"]: -12000,
            G["holdVolEnv"]: -12000, G["decayVolEnv"]: -12000, G["releaseVolEnv"]: -12000, G["keynum"]: -1,
            G["velocity"]: -1, G["scaleTuning"]: 100, G["overridingRootKey"]: -1}
RANGE = (G["keyRange"], G["velRange"])


def tc(v):
    """timecents → 秒"""
    return 2.0 ** (v / 1200.0)


class Zone:
    def __init__(self, gens):
        self.gens = gens  # 番号 → 値 (範囲は (lo, hi))

    def key_ok(self, key, vel):
        kr = self.gens.get(G["keyRange"], (0, 127))
        vr = self.gens.get(G["velRange"], (0, 127))
        return kr[0] <= key <= kr[1] and vr[0] <= vel <= vr[1]


class SoundFont:
    def __init__(self, path=DEFAULT_PATH):
        if not os.path.exists(path):
            raise FileNotFoundError(path + " が無い。tools/sources/soundfont/README.md の場所から落とす")
        data = open(path, "rb").read()
        assert data[:4] == b"RIFF" and data[8:12] == b"sfbk"
        self.chunks = {}
        self._walk(data, 12, len(data))
        raw = np.frombuffer(self.chunks[b"smpl"], dtype="<i2")
        self.samples = raw.astype(np.float32) / 32768.0
        self._parse_pdta()
        self._cache = {}

    def _walk(self, data, a, b):
        while a + 8 <= b:
            cid = data[a:a + 4]
            size = struct.unpack("<I", data[a + 4:a + 8])[0]
            if cid == b"LIST":
                self._walk(data, a + 12, a + 8 + size)
            else:
                self.chunks[cid] = data[a + 8:a + 8 + size]
            a += 8 + size + (size & 1)

    @staticmethod
    def _recs(buf, fmt):
        n = struct.calcsize(fmt)
        return [struct.unpack(fmt, buf[i:i + n]) for i in range(0, len(buf) - n + 1, n)]

    @staticmethod
    def _gens(recs, a, b):
        out = {}
        for oper, amt in recs[a:b]:
            if oper in RANGE:
                out[oper] = (amt & 0xFF, (amt >> 8) & 0xFF)
            else:
                out[oper] = struct.unpack("<h", struct.pack("<H", amt))[0]
        return out

    def _zones(self, bags, gens, a, b, link):
        zones = [Zone(self._gens(gens, bags[i][0], bags[i + 1][0])) for i in range(a, b)]
        glob = {}
        if zones and link not in zones[0].gens:
            glob = zones[0].gens
            zones = zones[1:]
        return glob, zones

    def _parse_pdta(self):
        c = self.chunks
        phdr = self._recs(c[b"phdr"], "<20sHHHIII")
        pbag = self._recs(c[b"pbag"], "<HH")
        pgen = self._recs(c[b"pgen"], "<HH")
        inst = self._recs(c[b"inst"], "<20sH")
        ibag = self._recs(c[b"ibag"], "<HH")
        igen = self._recs(c[b"igen"], "<HH")
        self.shdr = self._recs(c[b"shdr"], "<20sIIIIIBbHH")
        self.instruments = []
        for i in range(len(inst) - 1):
            glob, zones = self._zones(ibag, igen, inst[i][1], inst[i + 1][1], G["sampleID"])
            self.instruments.append((inst[i][0].split(b"\0")[0].decode("latin1"), glob, zones))
        self.presets = {}
        for i in range(len(phdr) - 1):
            name, prog, bank, bag = phdr[i][0].split(b"\0")[0].decode("latin1"), phdr[i][1], phdr[i][2], phdr[i][3]
            glob, zones = self._zones(pbag, pgen, bag, phdr[i + 1][3], G["instrument"])
            self.presets[(bank, prog)] = (name, glob, zones)

    def list_presets(self):
        return sorted((b, p, v[0]) for (b, p), v in self.presets.items())

    # ------------------------------------------------------------ 一音
    def voices(self, bank, prog, key, vel):
        """鳴らす区分ごとの生成子 (楽器の値 + プリセットの足し) とサンプルの番号"""
        _, pglob, pzones = self.presets[(bank, prog)]
        out = []
        for pz in pzones:
            pg = dict(pglob)
            pg.update(pz.gens)
            if not Zone(pg).key_ok(key, vel):
                continue
            _, iglob, izones = self.instruments[pg[G["instrument"]]]
            for iz in izones:
                ig = dict(DEFAULTS)
                ig.update(iglob)
                ig.update(iz.gens)
                if not Zone(ig).key_ok(key, vel):
                    continue
                g = dict(ig)
                for k, v in pg.items():
                    if k in RANGE or k in (G["instrument"], G["sampleID"]):
                        continue
                    g[k] = g.get(k, 0) + v
                out.append(g)
        return out

    def note(self, bank, prog, key, dur, vel=96, pitch_bend_cents=0.0, vibrato=None):
        """一音を鳴らして stereo (n, 2) の配列を返す。dur は押さえている秒。vibrato = (深さセント, 速さ Hz, 遅れ秒) で上書き"""
        vel = int(max(1, min(127, vel)))
        out = None
        for g in self.voices(bank, prog, key, vel):
            y = self._voice(g, key, vel, dur, pitch_bend_cents, vibrato)
            if out is None:
                out = y
            else:
                if len(y) > len(out):
                    y, out = out, y
                out[:len(y)] += y
        if out is None:
            return np.zeros((int(0.1 * SR), 2))
        return out

    def _voice(self, g, key, vel, dur, bend, vibrato):
        sh = self.shdr[g[G["sampleID"]]]
        _, s0, s1, l0, l1, srate, opitch, pcorr, _, stype = sh
        s0 += g.get(G["startAddrsOffset"], 0) + 32768 * g.get(G["startAddrsCoarseOffset"], 0)
        s1 += g.get(G["endAddrsOffset"], 0) + 32768 * g.get(G["endAddrsCoarseOffset"], 0)
        l0 += g.get(G["startloopAddrsOffset"], 0) + 32768 * g.get(G["startloopAddrsCoarseOffset"], 0)
        l1 += g.get(G["endloopAddrsOffset"], 0) + 32768 * g.get(G["endloopAddrsCoarseOffset"], 0)
        mode = g.get(G["sampleModes"], 0) & 3
        if g.get(G["keynum"], -1) >= 0:
            key = g[G["keynum"]]
        root = g[G["overridingRootKey"]] if g.get(G["overridingRootKey"], -1) >= 0 else opitch
        cents = (key - root) * g.get(G["scaleTuning"], 100) + 100 * g.get(G["coarseTune"], 0) + g.get(G["fineTune"], 0) \
            + pcorr + bend
        step = 2.0 ** (cents / 1200.0) * srate / SR

        # 包絡の時間
        kn = key - 60
        d_ = tc(g[G["delayVolEnv"]])
        a_ = tc(g[G["attackVolEnv"]])
        h_ = tc(g[G["holdVolEnv"]] - kn * g.get(G["keynumToVolEnvHold"], 0))
        dc = tc(g[G["decayVolEnv"]] - kn * g.get(G["keynumToVolEnvDecay"], 0))
        sus_db = -max(0, min(1440, g.get(G["sustainVolEnv"], 0))) / 10.0
        rel = max(0.006, tc(g[G["releaseVolEnv"]]))
        total = dur + min(rel, 8.0) + 0.05
        n = int(total * SR)
        t = np.arange(n) / SR

        # 音程の揺れ (LFO)
        cents_mod = np.zeros(n)
        vib_amt = g.get(G["vibLfoToPitch"], 0)
        vib_f = 8.176 * tc(g.get(G["freqVibLFO"], 0))
        vib_d = tc(g[G["delayVibLFO"]])
        if vibrato is not None:
            vib_amt, vib_f, vib_d = vibrato
        if vib_amt:
            ramp = np.clip((t - vib_d) / 0.4, 0, 1)
            cents_mod += vib_amt * ramp * np.sin(2 * math.pi * vib_f * np.maximum(0, t - vib_d))
        mod_amt = g.get(G["modLfoToPitch"], 0)
        if mod_amt:
            mf = 8.176 * tc(g.get(G["freqModLFO"], 0))
            md = tc(g[G["delayModLFO"]])
            cents_mod += mod_amt * np.sin(2 * math.pi * mf * np.maximum(0, t - md)) * (t > md)
        rate = step * (2.0 ** (cents_mod / 1200.0)) if np.any(cents_mod) else np.full(n, step)
        pos = np.concatenate([[0.0], np.cumsum(rate[:-1])]) + s0

        # ループ
        held = t < dur
        if mode in (1, 3) and l1 > l0 + 1:
            ln = l1 - l0
            over = pos >= l0
            if mode == 3:
                # 離したあとはループを抜けて最後まで: 離した時点の位置から続ける
                rel_i = int(dur * SR)
                wrapped = pos.copy()
                m = over & held
                wrapped[m] = l0 + np.mod(pos[m] - l0, ln)
                if rel_i < n:
                    p_rel = wrapped[rel_i - 1] if rel_i > 0 else pos[0]
                    wrapped[rel_i:] = p_rel + (pos[rel_i:] - pos[rel_i - 1 if rel_i > 0 else 0])
                pos = wrapped
            else:
                pos = np.where(over, l0 + np.mod(pos - l0, ln), pos)
        valid = pos < s1 - 1
        pos = np.minimum(pos, s1 - 2)
        i0 = np.floor(pos).astype(np.int64)
        fr = (pos - i0).astype(np.float32)
        y = self.samples[i0] * (1 - fr) + self.samples[i0 + 1] * fr
        y = y * valid

        # 音量の包絡 (dB で)
        env_db = np.full(n, sus_db)
        m = t < d_
        env_db[m] = -200
        m = (t >= d_) & (t < d_ + a_)
        lin = (t[m] - d_) / max(a_, 1e-4)
        env_db[m] = 20 * np.log10(np.maximum(lin, 1e-5))
        m = (t >= d_ + a_) & (t < d_ + a_ + h_)
        env_db[m] = 0.0
        t2 = d_ + a_ + h_
        m = (t >= t2) & (t < t2 + dc)
        # decay は 0 dB → -96 dB を dc 秒で進む速さで、sustain で止まる
        env_db[m] = np.maximum(sus_db, -96.0 * (t[m] - t2) / max(dc, 1e-4))
        # release: 離した時点の値から 96 dB を rel 秒で
        ri = int(dur * SR)
        if ri < n:
            v0 = env_db[ri - 1] if ri > 0 else -200
            env_db[ri:] = v0 - 96.0 * (t[ri:] - dur) / rel
        att_cb = 0.4 * g.get(G["initialAttenuation"], 0)
        # 強さ → 大きさ (既定の変調。凹の曲線、最大 96 dB の 0.4 倍ではなく SF2 の既定 960 cB を凹で)
        vel_db = 40 * math.log10(vel / 127.0)
        amp = 10 ** ((env_db - att_cb / 10.0 + vel_db) / 20.0)
        y = y * amp

        # 低域の通過 (Fc は絶対セント)。強さが弱いほど少しこもる (既定の変調 -2400 セントの軽い版)
        fc = g.get(G["initialFilterFc"], 13500) - 1200 * (1 - vel / 127.0)
        me = g.get(G["modEnvToFilterFc"], 0)
        if me:
            # 変調の包絡は持続の値で近似 (立ち上がりの明るさは捨てる)
            sus_mod = 1 - max(0, min(1000, g.get(G["sustainModEnv"], 0))) / 1000.0
            fc += me * (0.5 + 0.5 * sus_mod)
        hz = 8.176 * 2 ** (fc / 1200.0)
        if hz < 16000:
            q_db = g.get(G["initialFilterQ"], 0) / 10.0
            sos = signal.butter(2, max(60.0, hz), "low", fs=SR, output="sos")
            y = signal.sosfilt(sos, y)
            if q_db > 3:
                # 共鳴の山を少し足す
                peak = signal.butter(2, [max(50.0, hz * 0.85), min(SR * 0.45, hz * 1.15)], "band", fs=SR, output="sos")
                y = y + (10 ** (q_db / 20) - 1) * 0.3 * signal.sosfilt(peak, y)

        pan = max(-500, min(500, g.get(G["pan"], 0))) / 500.0
        a = (pan + 1) * math.pi / 4
        return np.stack([y * math.cos(a), y * math.sin(a)], axis=1)


_SF = None


def bank():
    global _SF
    if _SF is None:
        _SF = SoundFont()
    return _SF


if __name__ == "__main__":
    sfnt = bank()
    for b, p, name in sfnt.list_presets():
        print("%3d %3d %s" % (b, p, name))
