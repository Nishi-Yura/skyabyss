"""lang の書き方の共通部品: いまある lang を土台に、そのスクリプトが持つキーだけを上書きして返す。

生成スクリプトの出力を丸ごと上書きすると、あとから手で足した文・ほかのスクリプトが足した文が消える。
そこで「持たないキーは残して書く」(gen_ch4_content.py が先にやっていたのと同じ決まり)。
- 既存のキーは位置を変えない。新しいキーだけ末尾に足す (ファイルがまだ無いときだけキー順に並べる)。
- drop に挙げたキーは消す (もうどこも作らないキーを片づけるとき)。
使い方: write_json(path, langmerge.merged(path, DICT))
"""
import json
from pathlib import Path


def merged(path, new, drop=()):
    p = Path(path)
    if p.exists():
        with open(p, encoding="utf-8") as f:
            out = json.load(f)
        for k in drop:
            out.pop(k, None)
        out.update(new)
        return out
    return dict(sorted(new.items()))
