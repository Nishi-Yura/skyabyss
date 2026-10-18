"""Rebuild every README image into docs/readme/ (run from tools/readme)."""
import scenes, ui, extras
scenes.make_all()
ui.make_all()
extras.make_all()
