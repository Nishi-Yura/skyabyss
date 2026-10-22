import json, glob, collections
for loc in ('ja_jp', 'en_us'):
    d = collections.defaultdict(dict)
    for f in glob.glob('src/main/resources/assets/*/lang/%s.json' % loc):
        ns = f.replace('\\', '/').split('/')[-3]
        for k, v in json.load(open(f, encoding='utf8')).items():
            d[k][ns] = v
    for k, v in d.items():
        if len(v) > 1:
            print(loc, k, v)
