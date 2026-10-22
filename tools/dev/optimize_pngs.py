"""PNG を見た目も中身も変えずに圧縮する (可逆)。

使い方 (リポジトリ直下で):  python tools/dev/optimize_pngs.py [ディレクトリ ...]
    既定は src/main/resources。build.gradle の optimizePngs タスクも同じものを流す。

やること: 各 PNG を、デコードした RGBA が元とバイト単位で同じになる形の中で一番小さく書き直す。
  - 色が 256 種類以下 → パレット (透明度は tRNS)
  - 全部不透明 → アルファを落とす (RGB。読み込むと α=255 で同じ)
  - それ以外は同じ色のまま最大圧縮で書き直す
書き直したファイルは、読み直して RGBA が元と一致したときだけ置き換える。小さくならなければ触らない。
.mcmeta など PNG 以外には触らない。何度流しても結果は変わらない (冪等)。
"""
import io
import os
import sys
from multiprocessing import Pool

import numpy as np
from PIL import Image


def build_palette(rgba):
    """RGBA の色が 256 種類以下ならパレット画像を作る。超えたら None。"""
    arr = np.frombuffer(rgba.tobytes(), dtype=np.uint8).reshape(-1, 4)
    packed = arr.view('<u4').ravel()
    uniq, inv = np.unique(packed, return_inverse=True)
    if len(uniq) > 256:
        return None
    w, h = rgba.size
    pal = uniq.view(np.uint8).reshape(-1, 4)
    img = Image.fromarray(inv.reshape(h, w).astype(np.uint8), 'P')
    img.putpalette(pal[:, :3].ravel().tolist())
    alphas = pal[:, 3].tolist()
    if any(a != 255 for a in alphas):
        img.info['transparency'] = bytes(alphas)
    return img


def encode(img):
    buf = io.BytesIO()
    img.save(buf, format='PNG', optimize=True, compress_level=9)
    return buf.getvalue()


def optimize_one(path):
    """(パス, 結果の名前, 元のバイト数, 後のバイト数) を返す。"""
    orig_size = os.path.getsize(path)
    try:
        src = Image.open(path)
        src.load()
        rgba = src.convert('RGBA')
        ref = rgba.tobytes()
        cands = [('same', src.copy())]
        if rgba.getextrema()[3][0] == 255:
            cands.append(('rgb', rgba.convert('RGB')))
        pal = build_palette(rgba)
        if pal is not None:
            cands.append(('palette', pal))
        best = None
        for name, img in cands:
            data = encode(img)
            if best is not None and len(data) >= len(best[1]):
                continue
            back = Image.open(io.BytesIO(data))
            back.load()
            if back.size != rgba.size or back.convert('RGBA').tobytes() != ref:
                continue  # 見た目が変わるものは捨てる
            best = (name, data)
    except Exception as e:  # 壊れた PNG などは触らずに報告だけする
        return (path, 'error:' + str(e), orig_size, orig_size)
    if best is None or len(best[1]) >= orig_size:
        return (path, 'kept', orig_size, orig_size)
    with open(path, 'wb') as f:
        f.write(best[1])
    return (path, best[0], orig_size, len(best[1]))


def collect(roots):
    files = []
    for root in roots:
        for d, _, names in os.walk(root):
            for n in names:
                if n.endswith('.png'):
                    files.append(os.path.join(d, n))
    return sorted(files)


def main(argv):
    roots = argv or ['src/main/resources']
    files = collect(roots)
    with Pool(4) as pool:
        results = pool.map(optimize_one, files, chunksize=4)
    before = sum(r[2] for r in results)
    after = sum(r[3] for r in results)
    changed = [r for r in results if r[3] < r[2]]
    errors = [r for r in results if r[1].startswith('error:')]
    for r in errors:
        print('ERROR', r[0], r[1])
    print('png %d, changed %d, %d -> %d bytes' % (len(files), len(changed), before, after))
    return 1 if errors else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1:]))
