"""武器の振りを、ほかのプレイヤーの目から確かめる台本 (専用サーバー + 二人) を作る。

使い方: python tools/gen_weapon_swing_mp_shots.py <合図ファイルの置き場> [サーバーのポート (既定 25683)]
作るもの: tools/shots/swing_mp_alice.txt (振る側。合図 go のあと 25 tick おきに振り続ける)
          tools/shots/swing_mp_bob.txt   (見る側。昔の長さ → 今の長さの順に、相手の振りを tick ごとに 60 枚ずつ撮る)
手順: runServer を立て (run/server.properties は tools/README.md の「マルチの確かめ」)、Alice を先に、Bob を後に runShots で入れる
      (-PshotsUser=Alice / Bob)。rcon で Alice に武器を持たせ、Bob を Alice の正面 4 ブロックに置いてから、置き場に go を作る。
絵: run/shots/swing_mp_bob/shots/swing_mp_bob/swing_mp_<old|new>_<連番>.png (自分で出力名を変えたときは -PshotsName)
"""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def main():
    sy = sys.argv[1].rstrip("/")
    port = sys.argv[2] if len(sys.argv) > 2 else "25683"
    a = ["# 武器の振りのマルチ確認: 振る側 (tools/gen_weapon_swing_mp_shots.py が作る)", f"server localhost:{port}", "timeout 900", "size 854 480",
         "wait 100", f"signal {sy}/a_joined", f"waitfile {sy}/go"]
    for _ in range(14):
        a += ["swing", "wait 25"]
    a += [f"signal {sy}/a_done", f"waitfile {sy}/quit", "quit"]
    (ROOT / "tools/shots/swing_mp_alice.txt").write_text("\n".join(a) + "\n", encoding="utf-8")
    b = ["# 武器の振りのマルチ確認: 見る側 (tools/gen_weapon_swing_mp_shots.py が作る)", f"server localhost:{port}", "timeout 900", "size 854 480",
         "wait 100", f"signal {sy}/b_joined", f"waitfile {sy}/go", "hud off"]
    for mode in ("old", "new"):
        b.append("swingclock " + ("off" if mode == "old" else "on"))
        b.append("wait 10")
        for k in range(60):
            b += ["wait 1", f"shot swing_mp_{mode}_{k:02d}"]
    b += [f"signal {sy}/b_done", f"waitfile {sy}/quit", "quit"]
    (ROOT / "tools/shots/swing_mp_bob.txt").write_text("\n".join(b) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
