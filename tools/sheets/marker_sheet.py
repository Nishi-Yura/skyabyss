# 選定用の絵を縮めて docs/review/markers/ に置き、確認用の一覧 (_contact_*.png) も作る
import os, sys
from PIL import Image
src = "run/shots/markers/shots/markers"
dst = "docs/review/markers"
os.makedirs(dst, exist_ok=True)
names = sorted(f for f in os.listdir(src) if f.endswith(".png"))
for n in names:
    im = Image.open(os.path.join(src, n)).convert("RGB")
    im = im.resize((960, 540), Image.LANCZOS)
    im.save(os.path.join(dst, n[:-4] + ".jpg"), quality=86)
def sheet(prefix, out, cols=4):
    fs = [f for f in names if f.startswith(prefix)]
    w, h = 480, 270
    rows = (len(fs) + cols - 1) // cols
    S = Image.new("RGB", (w * cols, h * rows))
    for i, f in enumerate(fs):
        S.paste(Image.open(os.path.join(src, f)).convert("RGB").resize((w, h)), ((i % cols) * w, (i // cols) * h))
    S.save(out)
    print(out, fs)
if len(sys.argv) > 1:
    sheet(sys.argv[1], os.path.join(os.environ.get("TMP", "."), "contact.png"))
