# -*- coding: utf-8 -*-
"""終章「雲海」の敵・主のラフの共通部品 (docs/design/50)。月の mk (tools/rough/lowpoly/moon/mk.py) をそのまま使い、出力先と色の言葉だけ替える。

使い方 (1 体 = 1 ファイル cloud/<id>.py。一品一案):
    from mk import *
    run_one(SPEC, build, PAL, BONES, POSES)  -> docs/review/cloud_roughs/<id>/a/ と <id>.png・<id>_manifest.json

色の言葉 (01 の「雲海 = 金と白」): 雲の白 CLOUDW・鐘の青銅 BRONZE・古い漆喰 PLASTER・前の都の石 STONEW・金 GOLDW・灰の外套 ROBE・朝の光 DAWN (光る)。
灯の島: 油引きの外套 OILSKIN・海藻 SEAWEED・網の縄 ROPE・灯の籠の真鍮 BRASS・目の白 SCLERA。
炉の島: 鉄 IRON・革の前掛け LEATHER・灰の肌 ASHSKIN・残り火 EMBER (光る)
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parent / "moon"))
# 月の mk を別の名前で読む (同じ名前 mk のこのファイルと取り違えないように)
import importlib.util as _ilu  # noqa: E402
_spec = _ilu.spec_from_file_location("moon_mk", HERE.parent / "moon" / "mk.py")
_moonmk = _ilu.module_from_spec(_spec)
sys.modules["moon_mk"] = _moonmk
_spec.loader.exec_module(_moonmk)
globals().update({k: v for k, v in vars(_moonmk).items() if not k.startswith("__")})

ROOT = HERE.parents[3]
OUT = ROOT / "docs/review/cloud_roughs"
_moonmk.OUT = OUT
_moonmk._ck.OUT = OUT
_moonmk._c4mod.OUT = OUT
_moonmk._c5.OUT = OUT

CLOUDW = [(170, 176, 190), (206, 210, 220), (230, 232, 238), (246, 246, 250), (255, 255, 255)]
BRONZE = [(52, 40, 28), (92, 70, 44), (136, 104, 62), (176, 140, 84), (214, 182, 116)]
VERDI = [(40, 70, 64), (62, 104, 92), (90, 140, 122), (128, 176, 156)]
PLASTER = [(120, 116, 108), (160, 156, 146), (196, 192, 182), (224, 220, 210)]
STONEW = [(70, 70, 74), (104, 104, 108), (138, 138, 140), (172, 172, 172)]
GOLDW = [(110, 80, 30), (170, 128, 50), (220, 178, 80), (250, 222, 140)]
ROBE = [(30, 32, 40), (52, 54, 64), (78, 80, 92), (108, 110, 122)]
DAWN = [(220, 160, 90), (246, 200, 130), (255, 232, 180), (255, 248, 230)]
HOLLOW = [(6, 6, 10), (14, 14, 20), (24, 24, 32)]
# 灯の島 (前の潮の浜): 漁師の油引きの外套・海藻・網の縄・灯の籠の真鍮・目の白
OILSKIN = [(30, 40, 40), (46, 60, 58), (66, 84, 78), (92, 112, 102)]
SEAWEED = [(28, 40, 24), (44, 62, 34), (64, 86, 44), (90, 112, 60)]
ROPE = [(92, 74, 50), (126, 104, 72), (160, 136, 98), (192, 170, 128)]
BRASS = [(90, 66, 26), (140, 106, 44), (190, 150, 70), (230, 196, 110)]
SCLERA = [(200, 196, 186), (224, 220, 210), (240, 238, 230), (252, 250, 244)]
# 炉の島 (前の灰炉): 鉄・革の前掛け・灰の肌・残り火 (光る)
IRON = [(30, 30, 34), (50, 50, 56), (74, 74, 80), (104, 104, 110)]
LEATHER = [(56, 36, 24), (84, 56, 36), (112, 78, 50), (140, 104, 70)]
ASHSKIN = [(70, 68, 66), (96, 94, 90), (124, 122, 118), (152, 150, 146)]
EMBER = [(220, 90, 30), (250, 140, 50), (255, 190, 90), (255, 230, 160)]


def run_one(spec, build, pal, bones, poses, hint=128):
    return _moonmk.run_one(spec, build, pal, bones, poses, hint)
