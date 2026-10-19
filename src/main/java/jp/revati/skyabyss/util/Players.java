package jp.revati.skyabyss.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// プレイヤー一覧を回すときの写し。
// level.players() と PlayerList.getPlayers() は生きた一覧で、ループの途中で別の次元へ移す・倒す・切断すると
// 一覧が書き換わり ConcurrentModificationException になる (GaleColumnEntity で実際に落ちた)。
// プレイヤーに触れる処理を呼ぶループは、この写しを回す。
public final class Players {
    private Players() {
    }

    public static <T> List<T> snapshot(Collection<? extends T> live) {
        return new ArrayList<>(live);
    }
}
