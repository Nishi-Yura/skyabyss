"""Quick look at a current model: python tools/rough/hp_base.py <name> [kind] -> scratchpad/base_<name>.png"""
import sys

sys.path.insert(0, "tools/rough")
import hp_view
import hpkit
import voxel

name = sys.argv[1]
kind = sys.argv[2] if len(sys.argv) > 2 else "entity"
m = hpkit.Model.load(name, kind=kind)
views = [("a", 180, 10), ("b", 215, 22), ("c", 270, 8), ("d", 35, 25), ("top", 180, 80)]
ims = [hp_view.shot(m.faces(), y, p, (420, 420)) for _, y, p in views]
out = "C:/Users/nisimoto/AppData/Local/Temp/claude/C--Users-nisimoto-Desktop-mod/f361c594-8a18-4c15-b5dd-485ee74dd27f/scratchpad/base_%s.png" % name
voxel.sheet(name, [(v[0], [im]) for v, im in zip(views, ims)]).save(out)
print(out, hpkit.stats(m.geo))
