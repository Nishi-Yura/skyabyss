"""七つの見た目のラフシートを順に (同時でなく) 作る。使い方: python -I tools/rough/lowpoly/mounts/run_all.py"""
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
for n in ("horn_beast", "ash_deer", "great_lizard", "whale_calf", "shell_tortoise", "storm_chick", "ember_fox"):
    r = subprocess.run([sys.executable, "-I", str(HERE / f"{n}.py")], capture_output=True, text=True)
    for line in (r.stdout + r.stderr).splitlines():
        print(re.sub(r" buried.*?floating", " floating", line)[:170])
