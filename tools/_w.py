import json
import numpy as np
for n in ('ember_titan_mid', 'ember_titan_far'):
    m = json.load(open('src/main/resources/assets/skyabyss/lowpoly/%s.json' % n))
    b = m['bones']['head']
    v = np.array(b['vertices'])
    print(n, len(v), m['triangle_count'])
    for t in b['triangles']:
        p = v[list(t)]
        c = p.mean(0)
        if -152 < c[2] < -140 and 0 < c[1] < 19:
            print(np.round(p, 1).tolist())
