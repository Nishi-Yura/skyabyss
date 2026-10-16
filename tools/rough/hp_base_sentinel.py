import sys

sys.path.insert(0, "tools/rough")
import hp_view
import hpkit
import voxel

m = hpkit.Model.load("bone_sentinel")
sh = hpkit.Model.load("bone_sentinel_shield", kind="gear")
faces = m.faces() + hp_view.gear_faces(m.geo, sh.geo, sh.tex, "socket_off")
cols = hp_view.triptych([("now", faces)])
voxel.sheet("sentinel now", cols).save("C:/Users/nisimoto/AppData/Local/Temp/claude/C--Users-nisimoto-Desktop-mod/f361c594-8a18-4c15-b5dd-485ee74dd27f/scratchpad/sent_now.png")
