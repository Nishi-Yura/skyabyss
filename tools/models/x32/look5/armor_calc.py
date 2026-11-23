# -*- coding: utf-8 -*-
"""防具を Blockbench で手で組むときの電卓（箱は作らない。数値を出すだけ）。回転の順は Blockbench と同じ X → Y → Z。

  python -I armor_calc.py end <原点> <回転> <点> [<点> ...]   回した箱の中の点が、どこへ行くか（節をつなぐ継ぎ目の位置）
  python -I armor_calc.py seg <A> <B>                        A から B へ渡す棒: 長さと回転 [0, ry, rz]（棒は箱の +x 向き、原点は A）
  python -I armor_calc.py mem <Pa> <Pb> <Ea> <Eb>            2 本の畝（根元 P・先 E）の間に張る板: 原点 M、長さ、回転 [rx, ry, 0]（板は +z 向き）と幅
点・回転は x,y,z をカンマで。左右を返すときは x を逆に、回転は [rx, -ry, -rz]。
"""
import math
import sys


def rot_point(p, rot, origin):
    x, y, z = (p[i] - origin[i] for i in range(3))
    ax, ay, az = (math.radians(a) for a in rot)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    x, y = x * math.cos(az) - y * math.sin(az), x * math.sin(az) + y * math.cos(az)
    return (x + origin[0], y + origin[1], z + origin[2])


def seg(a, b):
    d = [b[i] - a[i] for i in range(3)]
    n = math.sqrt(sum(v * v for v in d))
    u = [v / n for v in d]
    return 'A(%.2f,%.2f,%.2f) len %.2f rot[0,%.1f,%.1f]' % (a[0], a[1], a[2], n, -math.degrees(math.asin(u[2])), math.degrees(math.atan2(u[1], u[0])))


def mem(pa, pb, ea, eb):
    mb = [(pa[i] + pb[i]) / 2 for i in range(3)]
    me = [(ea[i] + eb[i]) / 2 for i in range(3)]
    d = [me[i] - mb[i] for i in range(3)]
    n = math.sqrt(sum(v * v for v in d))
    u = [v / n for v in d]
    return 'M(%.2f,%.2f,%.2f) len %.2f rot[%.1f,%.1f,0] width base %.2f end %.2f' % (
        mb[0], mb[1], mb[2], n, -math.degrees(math.asin(u[1])), math.degrees(math.atan2(u[0], u[2])), math.dist(pa, pb), math.dist(ea, eb))


if __name__ == '__main__':
    mode, args = sys.argv[1], [[float(v) for v in a.split(',')] for a in sys.argv[2:]]
    if mode == 'end':
        print(' '.join('(%.2f, %.2f, %.2f)' % rot_point(p, args[1], args[0]) for p in args[2:]))
    elif mode == 'seg':
        print(seg(args[0], args[1]))
    elif mode == 'mem':
        print(mem(*args[:4]))
    else:
        sys.exit(__doc__)
