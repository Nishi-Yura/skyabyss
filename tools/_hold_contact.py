import sys
from PIL import Image
D=sys.argv[5] if len(sys.argv)>5 else 'run/shots/weapon_hold/shots/weapon_hold/'
names=sys.argv[2].split(','); views=sys.argv[3].split(','); tier=sys.argv[4] if len(sys.argv)>4 else 'common'
W,H=400,360
s=Image.new('RGB',(W*len(views),H*len(names)))
for r,n in enumerate(names):
    for c,v in enumerate(views):
        im=Image.open(f'{D}hold_{n}_{tier}_{v}.png').convert('RGB')
        if v.startswith('tp'): im=im.crop((380,120,680,390)).resize((W,H))   # zoom on the player
        else: im=im.crop((0,180,960,540)).resize((W,H))
        s.paste(im,(c*W,r*H))
s.save(sys.argv[1])
