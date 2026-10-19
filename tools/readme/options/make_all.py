"""Rebuild every README concept image and the preview page (run from tools/readme/options)."""
import make_a, make_b, make_c, build_preview
from common import outdir

outdir("a")
for f in (make_a.cover, make_a.page_sky, make_a.page_titans, make_a.page_pack, make_a.page_keys, make_a.page_end, make_a.page_torn, make_a.page_admin):
    f()
outdir("b")
make_b.top()
make_b.make_strips()
outdir("c")
for f in (make_c.title, make_c.four, make_c.pack, make_c.end, make_c.ornament):
    f()
build_preview.main()
