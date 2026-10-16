"""For each geo model, list the tools/*.py scripts that mention its file name (who can regenerate it)."""
import glob
import os
import re

geos = sorted(glob.glob("src/main/resources/assets/skyabyss/geo/**/*.geo.json", recursive=True))
scripts = {p: open(p, encoding="utf-8", errors="ignore").read() for p in glob.glob("tools/*.py") + glob.glob("tools/rough/*.py")}
for g in geos:
    name = os.path.basename(g).replace(".geo.json", "")
    pat = re.compile(r"[\"'/]" + re.escape(name) + r"(\.geo\.json|[\"'])")
    hits = [os.path.basename(p) for p, t in scripts.items() if pat.search(t) and "/rough/" not in p.replace("\\", "/")]
    rough = [os.path.basename(p) for p, t in scripts.items() if pat.search(t) and "/rough/" in p.replace("\\", "/")]
    print("%-28s tools: %s | rough: %s" % (name, " ".join(hits[:6]), " ".join(rough[:4])))
