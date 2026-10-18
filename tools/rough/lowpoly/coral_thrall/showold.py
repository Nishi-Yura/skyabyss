from common import *
from old import old_faces
from PIL import Image
import sys

f, n, ns = old_faces()
print(n, ns)
ims = []
for yaw, pose in ((212, None), (32, None), (150, {"arm_r": (48, 0, 0), "forearm_r": (-60, 0, 0), "body": (16, 12, 0)})):
    f, _, _ = old_faces(pose)
    ims.append(voxel.render(f, yaw=yaw, pitch=13, size=(400, 600), margin=0.04))
h, _, _ = old_faces(only_head=True)
ims.append(voxel.render(h, yaw=200, pitch=8, size=(400, 600), margin=0.04))
c = Image.new('RGB', (1600, 600))
for i, im in enumerate(ims):
    c.paste(im, (i * 400, 0))
c.save(sys.argv[1])
