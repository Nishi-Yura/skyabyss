"""骨の砲手 (bone_mortar) 低ポリ ラフ 3 案。背中の遠くから落とす敵: 低い骨の砲台に琥珀の目の小さな頭。
HP 30、動きは遅い。1.2 秒の予告の輪 (半径 2) を人の足もとへ置き、骨の塊を放物線で落とす。骨の盾の後ろにいる。
予告 (telegraph): 砲身を上げ、頭が盾の上からのぞく。撃った直後 (fire): 砲身が反動で下がる。
三角形の目安: 450〜600 (上限 700)。
A 臼砲の籠 (丸い台座 + 底の肋 + 反った高い盾 + 45 度の砲身 + 骨の弾の山) / B 盾の壁 (幅の広い平たい骨の壁、茶の縁枠と上の棘の冠、砲身は壁の後ろから上がる) / C 縦の臼砲 (細い柱の筒 + 釣鐘の口 + 肋の弧、頭は台座の前から大きく出て、予告で肋の頂より上へ持ち上がる)
"""
from ck import *  # noqa

# 三案は骨を別名にして、同じ予告の姿 (POSES) で回せるようにする (案ごとに枢軸が違うため)
BONES = {"root": (None, (0, 0, 0)), "body": ("root", (0, 0, 0)), "shield": ("body", (0, 0, 0)),
         "cannon": ("body", (0, 7.0, -0.5)), "head": ("body", (-3.5, 7.2, -1.5)),
         "cannon_b": ("body", (0, 4.5, -1.0)), "head_b": ("body", (6.5, 3.6, -3.0)),
         "cannon_c": ("body", (0, 3.0, 0.0)), "head_c": ("body", (-4.6, 1.0, -7.0))}
PAL = {"mats": {
    "stone": mat(STONE, 0.66, crack=0.22, stain=dict(ramp=MOSS, thr=0.93, up=0.3, conc=0.2)),
    "rim": mat(BROWN, 0.6, crack=0.1, noise=0.15),
    "bone": mat(BONE, 0.7, crack=0.05),
    "flesh": mat([(40, 34, 36), (80, 66, 60), (118, 98, 84), (152, 130, 108)], 0.62, crack=0.0, noise=0.2),
    "dark": mat(DARK, 0.5, crack=0.0, min=0),
    "moss": mat(MOSS, 0.6, crack=0.0, noise=0.25),
}, "skull_bone": "head", "face_mat": "rim"}


def eye_at(x, y, z, r=0.9):
    return dict(c=V(x, y, z), rx=r, ry=r * 0.6, pr=(0.55, 0.38), tilt=0.15, zr=2.2)


def plate(bone, m, prof, zc, bulge, thick, n=4):
    """反った厚い板 (盾)。prof = [(高さ y, 半幅)] を下から上へ。前の面は zc - bulge (中央) から両端へ戻る。
    断面は 2n 角の閉じた輪で、輪をつなぐ (loft)。上の輪は板で塞ぐ。"""
    rings = []
    for y, hw in prof:
        front = [V(hw * t, y, zc - bulge * (1 - t * t)) for t in np.linspace(-1, 1, n + 1)]
        back = [V(hw * t, y, zc - bulge * (1 - t * t) + thick) for t in np.linspace(1, -1, n + 1)][1:-1]
        rings.append(front + back)
    return loft(bone, m, rings, caps=(False, m))


def path_pts(pivot, d, lengths):
    d = nrm(d)
    return [np.asarray(pivot, float) + d * L for L in lengths]


def build_a(k):
    """A 臼砲の籠: 丸い台座、前の反った高い盾、45 度の砲身。頭は台座の左の奥から盾の上をのぞく。"""
    F = []
    R = [ring(0.0, 8.0, -7.0, 7.0, n=8), ring(3.5, 7.8, -7.4, 7.4, n=8), ring(7.0, 6.2, -6.0, 6.0, n=8)]
    F += part("cradle", loft("body", "stone", R, caps=(False, "bone")))
    band = [p for p in ring(3.5, 7.9, -7.6, 7.6, n=8)] + [ring(3.5, 7.9, -7.6, 7.6, n=8)[0]]
    F += part("cradle_band", tube("body", "bone", band, ra=0.5, sides=4, hint=(0, 1, 0)))
    # 台座の底の骨の肋 (七本。台座の外へ少し出る)
    for x, z in ((7.2, 0.0), (5.2, 5.2), (0.0, 6.9), (-5.2, 5.2), (-7.2, 0.0), (5.2, -5.2), (0.0, -6.9)):
        F += part("base_rib", tube("body", "rim", [(x * 1.02, 0.2, z * 1.02), (x * 0.92, 6.6, z * 0.92)],
                                   ra=0.42, sides=4, hint=(0, 0, 1)))
    # 骨の弾の山 (台座の右の外。塊を三つ積む)
    for (x, y, z, r) in ((10.6, 1.6, -1.0, 1.6), (10.4, 1.4, 1.6, 1.5), (11.6, 4.0, 0.2, 1.4)):
        F += part("ammo", ellipsoid("body", "bone", (x, y, z), (r, r * 0.9, r), segs=5, rings=2))
    # 反った盾 (前)。両側の支えで台座につなぐ。縁の茶の枠 (両端と上)
    prof = [(0, 6.2), (3, 6.6), (6, 5.8), (8.5, 4.2), (10.5, 1.8)]
    F += part("shield", plate("shield", "bone", prof, zc=-9.2, bulge=1.4, thick=1.4))
    for s in (-1, 1):
        edge = [V(s * hw, y, -9.1) for y, hw in prof]
        F += part("shield_rim", tube("shield", "rim", edge, ra=0.45, sides=4, hint=(0, 0, 1)))
    F += part("shield_top", tube("shield", "rim", [V(-1.8, 10.5, -9.1), V(0, 10.9, -9.3), V(1.8, 10.5, -9.1)],
                                 ra=0.45, sides=4, hint=(0, 0, 1)))
    for s in (-1, 1):
        F += part(f"brace{s}", tube("body", "rim", [(s * 4.5, 3.0, -6.6), (s * 4.5, 6.0, -8.2)], ra=0.8, sides=4))
    # 砲身: 45 度。台座の上の枢軸から
    p = path_pts((0, 7.0, -0.5), (0, 0.707, -0.707), [0, 4.5, 9.0, 13.5, 17.6])
    F += part("cannon", tube("cannon", "bone", p, ra=[2.4, 2.4, 2.2, 2.0, 2.0], sides=6, hint=(1, 0, 0)))
    for L in (4.0, 11.0):
        q = path_pts((0, 7.0, -0.5), (0, 0.707, -0.707), [L, L + 0.9])
        F += part("cannon_band", tube("cannon", "rim", q, ra=2.8, sides=6, hint=(1, 0, 0)))
    # 革の綴り (砲身に巻く二本の細い帯)
    for L in (7.0, 15.0):
        q = path_pts((0, 7.0, -0.5), (0, 0.707, -0.707), [L, L + 1.8])
        F += part("lashing", tube("cannon", "rim", q, ra=2.25, sides=4, hint=(0, 0.7, 0.7)))
    q = path_pts((0, 7.0, -0.5), (0, 0.707, -0.707), [16.9, 18.4])
    F += part("muzzle", tube("cannon", "dark", q, ra=2.1, sides=6, hint=(1, 0, 0)))
    # 頭: 台座の左の奥。盾の上からのぞく (予告で持ち上げる)
    F += part("head", ellipsoid("head", "rim", (-4.0, 9.4, -4.6), (2.4, 2.0, 2.6), segs=6, rings=2))
    F += part("head_brow", spike("head", "bone", (-4.0, 10.9, -5.8), (0, -0.2, -1), 2.0, 0.7, sides=3, rings=1))
    eyes = [eye_at(-5.0, 9.8, -6.9, 0.6), eye_at(-3.0, 9.8, -6.9, 0.6)]
    return F, eyes


def build_b(k):
    """B 盾の壁: 幅の広い平たい骨の壁が前を塞ぐ。縁は茶の枠、上に棘の冠。砲身は壁の後ろから上がる。頭は右の脇の細い首の先。"""
    F = []
    R = [ring(0.0, 9.0, -3.0, 5.0, n=8), ring(1.5, 9.4, -3.2, 5.2, n=8), ring(2.5, 8.6, -3.0, 5.0, n=8)]
    F += part("platform", loft("body", "stone", R, caps=(False, "bone")))
    # 壁: 幅 21、高さ 13。ほぼ平ら (反りは小さい)
    prof = [(0, 10.0), (3, 10.4), (7, 10.5), (10.5, 10.0), (12.2, 8.6), (13.2, 6.5)]
    F += part("wall", plate("shield", "bone", prof, zc=-9.0, bulge=0.9, thick=1.8, n=6))
    # 縁の枠 (左右の縦の枠と上の枠)
    for s in (-1, 1):
        F += part("wall_edge", tube("shield", "rim", [(s * 10.3, 0.2, -9.2), (s * 10.3, 6.0, -9.3), (s * 10.0, 11.5, -9.3)],
                                    ra=0.6, sides=4, hint=(0, 0, 1)))
    F += part("wall_top", tube("shield", "rim", [(-8.6, 12.0, -9.4), (0, 12.8, -9.7), (8.6, 12.0, -9.4)],
                               ra=0.7, sides=4, hint=(0, 0, 1)))
    # 上の棘の冠 (五本)
    for x in (-7.5, -3.75, 0.0, 3.75, 7.5):
        F += part("crown", spike("shield", "rim", (x, 12.4, -9.4), (0, 1, 0.05), 2.4, 0.8, sides=3, rings=1))
    # 壁の裏の支え (二本)
    for s in (-1, 1):
        F += part(f"post{s}", tube("body", "rim", [(s * 7.0, 2.0, -4.5), (s * 7.0, 9.0, -7.0)], ra=0.7, sides=4))
    # 砲身: 60 度。壁の後ろの枢軸から。壁の上へ出る
    p = path_pts((0, 4.5, -1.0), (0, 0.866, -0.5), [0, 5.0, 10.0, 15.0, 20.0])
    F += part("cannon_b", tube("cannon_b", "bone", p, ra=[2.0, 2.0, 1.9, 1.8, 1.7], sides=6, hint=(1, 0, 0)))
    for L in (6.0, 14.0):
        q = path_pts((0, 4.5, -1.0), (0, 0.866, -0.5), [L, L + 0.9])
        F += part("cannon_band", tube("cannon_b", "rim", q, ra=2.4, sides=6, hint=(1, 0, 0)))
    q = path_pts((0, 4.5, -1.0), (0, 0.866, -0.5), [19.3, 20.6])
    F += part("muzzle", tube("cannon_b", "dark", q, ra=1.8, sides=6, hint=(1, 0, 0)))
    # 骨の弾の山 (壁の左の足もと、台座の上)
    for (x, y, z, r) in ((-7.0, 4.6, -4.0, 1.5), (-5.2, 4.4, -3.2, 1.3), (-6.0, 6.4, -3.6, 1.2)):
        F += part("ammo", ellipsoid("body", "bone", (x, y, z), (r, r * 0.9, r), segs=5, rings=2))
    # 頭: 右の脇の首の先。壁の端から外へ出る
    F += part("neck", tube("head_b", "flesh", [(6.5, 3.6, -3.0), (9.2, 6.0, -3.4), (10.6, 7.6, -3.6)], ra=[1.3, 1.1, 1.0], sides=5, hint=(0, 0, 1)))
    F += part("head_b", ellipsoid("head_b", "rim", (11.6, 8.0, -3.8), (2.5, 2.1, 2.5), segs=6, rings=2))
    F += part("head_hood", ellipsoid("head_b", "bone", (11.6, 9.4, -3.8), (2.2, 0.9, 2.3), segs=5, rings=1))
    eyes = [eye_at(10.0, 8.0, -6.2, 0.7), eye_at(13.2, 8.0, -6.2, 0.7)]
    return F, eyes


def build_c(k):
    """C 縦の臼砲: 細く高い骨の筒 (上は釣鐘の口)。低い肋の弧が前を囲む。頭は台座の前から大きく出て、予告で肋の頂より上へ持ち上がる。"""
    F = []
    R = [ring(0.0, 8.5, -8.0, 8.0, n=8), ring(1.5, 7.0, -6.0, 6.5, n=8), ring(3.0, 4.0, -3.5, 3.8, n=8)]
    F += part("bowl", loft("body", "stone", R, caps=(False, "stone")))
    # 筒 (砲身): 高さ 3〜19。上で釣鐘に広がる
    tube_rings = [ring(3.0, 3.0, -3.0, 3.0, n=8), ring(6.0, 2.6, -2.6, 2.6, n=8), ring(9.0, 2.4, -2.4, 2.4, n=8),
                  ring(11.0, 2.4, -2.4, 2.4, n=8), ring(13.0, 2.4, -2.4, 2.4, n=8), ring(16.0, 3.2, -3.2, 3.2, n=8),
                  ring(17.5, 3.8, -3.8, 3.8, n=8), ring(19.0, 4.6, -4.6, 4.6, n=8)]
    F += part("barrel", loft("cannon_c", "bone", tube_rings, caps=(False, "dark")))
    F += part("barrel_band", tube("cannon_c", "rim", [(x, 6.0, z) for x, z in [(0, -3.4), (3.4, 0), (0, 3.4), (-3.4, 0), (0, -3.4)]],
                                  ra=0.5, sides=4, hint=(0, 1, 0)))
    for y in (11.0, 15.0):
        F += part("barrel_band", tube("cannon_c", "rim", [(x, y, z) for x, z in [(0, -3.0), (2.9, 0), (0, 3.0), (-2.9, 0), (0, -3.0)]],
                                      ra=0.45, sides=4, hint=(0, 1, 0)))
    # 台座の縁の骨の輪 (九点の閉じた輪)
    rim_pts = [V(4.2 * np.cos(a), 3.2, 3.9 * np.sin(a)) for a in np.linspace(0, 2 * np.pi, 9)]
    F += part("bowl_rim", tube("body", "bone", rim_pts, ra=0.5, sides=4, hint=(0, 1, 0)))
    # 前の肋の弧 (盾)。二本、高さの違う弧
    for h, rr in ((6.8, 1.0), (4.2, 0.7)):
        arc = [(-7.0, 0.0, -6.0), (-6.0, h * 0.5, -9.2), (-3.0, h, -10.4), (0.0, h + 0.4, -10.8), (3.0, h, -10.4),
               (6.0, h * 0.5, -9.2), (7.0, 0.0, -6.0)]
        F += part(f"rib{h}", tube("body", "bone", [V(*p) for p in arc], ra=[rr, rr, rr, rr, rr, rr, rr], sides=4,
                                  hint=(0, 0, 1)))
    # 肋の弧の付け根の骨 (台座に刺さる根元)
    for x in (-7.0, 7.0):
        F += part("rib_foot", spike("body", "rim", (x, 0.0, -6.0), (0, 0.6, -0.4), 2.0, 0.6, sides=3, rings=1))
    # 頭: 台座の前、左寄り。大きく、肋の弧の頂 (6.8) の上へ出る
    F += part("head_c", ellipsoid("head_c", "flesh", (-4.6, 6.4, -8.8), (2.8, 2.6, 2.6), segs=6, rings=2))
    F += part("head_horn", spike("head_c", "bone", (-4.6, 8.8, -9.0), (0.2, 0.9, 0.1), 2.8, 0.8, sides=3, rings=1))
    F += part("head_horn", spike("head_c", "bone", (-6.6, 8.0, -8.8), (-0.5, 0.8, 0.0), 2.0, 0.6, sides=3, rings=1))
    eyes = [eye_at(-6.0, 6.7, -11.2, 0.7), eye_at(-3.2, 6.7, -11.2, 0.7)]
    return F, eyes


def build(k):
    return {"a": build_a, "b": build_b, "c": build_c}[k](k)


# 予告 (telegraph): 砲身を上げ、頭が盾の上からのぞく。撃つ (fire): 砲身が反動で下がる
POSES = {
    "idle": {},
    "telegraph": {"cannon": (22, 0, 0), "head": (45, 0, 0), "cannon_b": (15, 0, 0), "head_b": (-25, 0, 0),
                  "cannon_c": (-22, 0, 0), "head_c": (-38, 0, 0)},
    "fire": {"cannon": (-20, 0, 0), "head": (-15, 0, 0), "cannon_b": (-25, 0, 0), "head_b": (10, 0, 0),
             "cannon_c": (12, 0, 0), "head_c": (30, 0, 0)},
}

SPEC = dict(
    id="bone_mortar", short="骨の砲手", kind="背中の敵 3案 (P7)",
    title="骨の砲手 (bone_mortar) — 背中の遠くの砲台・低ポリ + ドット絵 3 案",
    sub=["背中の遠くから落とす敵。HP 30。骨の盾の後ろにいて、1.2 秒の予告の輪 (半径 2) を足もとへ置いて、骨の塊を放物線で落とす。",
         "左の黄の目盛りは人 (1.8 ブロック) の高さ。予告の姿は「砲身を上げ、頭が盾の上からのぞく」の行。撃つ直後は砲身が下がる。"],
    labels=["A 臼砲の籠", "B 盾の壁", "C 縦の臼砲"],
    rows=[("前から (盾の面)", "idle", 180, 8, 380, (0, 9, 0), 16.0),
          ("横", "idle", 270, 6, 380, (0, 9, 0), 16.0),
          ("前 3/4", "idle", 212, 14, 400, (0, 9, 0), 16.0),
          ("予告: 砲身を上げる (横から)", "telegraph", 270, 6, 440, (0, 12, 0), 16.0),
          ("予告: 砲身を上げる (前 3/4)", "telegraph", 212, 14, 440, (0, 12, 0), 16.0),
          ("撃った直後 (横から)", "fire", 270, 6, 400, (0, 10, 0), 16.0)],
    options={
        "a": dict(name="A 臼砲の籠", desc="丸い台座の石の殻に骨の帯と底の肋。前に反った高い骨の盾が立ち、縁は茶の枠で囲まれ、その上に 45 度の砲身が抜ける。砲身には革の綴りと帯。台座の外には骨の弾の山。頭は台座の左の奥に座り、盾の上からのぞく（琥珀の目 2 つと骨の額の角）。"),
        "b": dict(name="B 盾の壁", desc="幅の広い平たい骨の壁が前を塞ぐ。縁は茶の枠で囲まれ、上には棘の冠が五本並ぶ。砲身は壁の後ろから高く上がり、壁の上へ出る。足もとに骨の弾の山。頭は右の脇の細い首の先にあり、壁の端から外へ出る。横に長い輪郭。"),
        "c": dict(name="C 縦の臼砲", desc="細く高い骨の筒の上が釣鐘の口に広がる。筒には茶の帯が三つ。前に低い肋の弧が二本で囲み、その根元に骨の爪。台座の前から大きな頭が出て、肋の弧の上へ持ち上がる。予告で頭が盾の縁より上に見え、琥珀の目と角がはっきり出る。"),
    },
    extra="一章の言葉 (灰青の殻・骨・琥珀の目・茶の割れ目・苔)。骨 9 本 (body / cannon / head / shield など)。砲身は枢軸 (pivot) から回し、予告は砲身を上げて頭を盾の上へ出す。撃つ直後は砲身を下げる（反動）。三角形の目安は 450〜600 (上限 700)。",
    budget="背中の敵 450〜600 (上限 700)", atlas_h=300)

if __name__ == "__main__":
    run(SPEC, build, PAL, BONES, POSES)
