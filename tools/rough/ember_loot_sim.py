"""余燼の心臓の報酬の模擬 (13_ember.md の 6.4節)。実行: python tools/rough/ember_loot_sim.py
図鑑 10 品の確率は 6.3節、鍛え手の大鎚 5% を別に抽選、初回は灯を選ぶ、天井は 3 で 2 倍・5 で未所持を保証。2 万人ぶん。乱数は人ごとに seed 固定。
"""
import random
import statistics as st

# 余燼の心臓の表 (13 の 6.3節の案)。first clear: choose one legendary (the rarest missing)
items = {"h": .25, "c": .25, "l": .25, "b": .25, "axe": .20, "cloak": .20, "amulet": .20,
         "hammer": .04, "poker": .04, "lamp": .02}
legend = ["hammer", "poker", "lamp"]


def run(seed, mid_hammer=.05):
    r = random.Random(seed)
    have = set()
    mem = 0
    n = 0
    res = {}
    first = True
    triggered = 0
    while True:
        n += 1
        new = False
        if r.random() < mid_hammer and "hammer" not in have:
            have.add("hammer")
            new = True
        mult = 2 if mem >= 3 else 1
        if mult == 2:
            triggered += 1
        for k, p in items.items():
            if r.random() < min(1, p * mult) and k not in have:
                have.add(k)
                new = True
        if first:
            first = False
            miss = [k for k in legend if k not in have]
            if miss:
                have.add("lamp" if "lamp" in miss else miss[0])
                new = True
        if not new:
            mem += 1
            if mem >= 5:
                miss = [k for k in items if k not in have]
                if miss:
                    have.add(r.choice(miss))
                    mem = 0
        else:
            mem = 0
        if "a4" not in res and {"h", "c", "l", "b"} <= have:
            res["a4"] = n
        if "a7" not in res and {"h", "c", "l", "b", "axe", "cloak", "amulet"} <= have:
            res["a7"] = n
        if len(have) == len(items):
            res["all"] = n
            res["trig"] = triggered
            return res


R = [run(i) for i in range(20000)]
for k in ("a4", "a7", "all"):
    v = sorted(x[k] for x in R)
    print(k, round(st.mean(v), 1), v[int(.95 * len(v))], v[-1])
print("trigger share", round(sum(x["trig"] for x in R) / sum(x["all"] for x in R), 3))
