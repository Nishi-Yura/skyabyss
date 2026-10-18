import sys
sys.path.insert(0,"tools/rough/lowpoly")
from bone_sentinel_lp import *
faces,size,atlas,glow,data=main()
ims=[render(faces,atlas,glow,y,p,(420,520),(0,32,0),46) for y,p in ((215,12),(180,5),(90,10))]
w=Image.new("RGB",(1260,520),(26,26,34))
for i,im in enumerate(ims): w.paste(im,(i*420,0),im)
w.save(sys.argv[1])
