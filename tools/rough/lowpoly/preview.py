import sys
sys.path.insert(0,"tools/rough/lowpoly")
from bone_sentinel_lp import *
faces,size,atlas,glow,data=main()
r=[];build_model(r);prev=0
for n,c in r:
    print(n,c-prev);prev=c
def v(yaw,pitch=14,center=(0,18,0),scale=14,sz=(520,640)):
    return render(faces,atlas,glow,yaw,pitch,sz,center,scale)
ims=[v(215),v(35)]
w=Image.new("RGB",(1040,640),(26,26,34))
for i,im in enumerate(ims): w.paste(im,(i*520,0),im)
w.save(sys.argv[1])
