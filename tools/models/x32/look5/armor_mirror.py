# python -I armor_mirror.py <右の箱の並び.json> : 右の箱の並び(JSON)を左へ返す: x を逆に、回転は [rx,-ry,-rz]、名前の _r -> _l
import json,sys
src=json.load(open(sys.argv[1]))
out=[]
for e in src:
    f,t=e['from'],e['to']
    n=dict(name=(e['name'][:-2]+'_l') if e['name'].endswith('_r') else e['name']+'_l',
           **{'from':[-t[0],f[1],f[2]],'to':[-f[0],t[1],t[2]]})
    if 'origin' in e:
        o=e['origin'];n['origin']=[-o[0],o[1],o[2]]
    if 'rotation' in e:
        r=e['rotation'];n['rotation']=[r[0],-r[1],-r[2]]
    out.append(n)
print(json.dumps(out,separators=(',',':')))
