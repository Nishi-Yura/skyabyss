"""燠の灯 (吊り灯) の持ち物欄の 32x32 の絵を、A 案の低ポリを正面斜めから見て作る。
出力: src/main/resources/assets/skyabyss/textures/item/cinder_lamp.png (今は絵が無く、ブロックの 3D を持ち物欄に出していた)"""
from common import *
import ember

r = make(ember.lamp_a, "ember", "common")
im = view(r["faces"], r["atlas"], r["glow"], "ember", 160, -20, (32, 32), (0, 0.5, 0), 2.35, ss=4, outline=True)
a = np.array(im)
a[..., 3] = np.where(a[..., 3] > 100, 255, 0)
Image.fromarray(a, "RGBA").save(ASSETS / "textures/item/cinder_lamp.png")
Image.fromarray(a, "RGBA").resize((256, 256), Image.NEAREST).save(Path(__import__("sys").argv[1]) if len(__import__("sys").argv) > 1 else "cl_preview.png")
