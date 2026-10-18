"""今のモデルのキーポーズを描いて確認する (開発用)。python cur_poses.py out.png"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from common import *  # noqa
from PIL import Image  # noqa

POSES = {
    "raise": {'claw_big': (25, -150, 10), 'claw_big_pincer': (30, 0, 0), 'body': (6, 0, 0)},
    "beckon": {'claw_big': (55, -20, -5), 'claw_big_pincer': (-10, 0, 0)},
    "strike": {'claw_big': (-5, 5, 10), 'claw_big_pincer': (-10, 0, 0), 'body': (-8, 0, 0)},
}

if __name__ == "__main__":
    W = Image.new('RGB', (1560, 840))
    for j, k in enumerate(("raise", "beckon", "strike")):
        f, n = old_faces(POSES[k])
        for i, (yaw, pitch) in enumerate(((205, 16), (270, 10))):
            W.paste(voxel.render(f, yaw=yaw, pitch=pitch, size=(520, 420), margin=0.02), (j * 520, i * 420))
    W.save(sys.argv[1])
