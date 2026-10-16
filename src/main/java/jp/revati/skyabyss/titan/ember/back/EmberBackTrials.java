package jp.revati.skyabyss.titan.ember.back;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 余燼の背の試練の進み (13 の 7 章: 熾り石・熾の底の門・リセット・眠る試練・休み場)。
// ほかの担当が呼ぶ API (この名前は変えない):
//   markReady(level, trial)        石を鳴らせるようにする (東: E が鍛え手を倒したとき、南: C3 が火床を 100 にしたとき。西は B が奥の岸で見る)
//   ring(level, trial, player)     石を鳴らす (ふつうは人が石を右クリックしたとき B が呼ぶ。テスト用)
//   sleeping(level, room)          この周、その部屋が眠っているか (east が眠ると smith も)
//   EmberBackState.get(level).seed() リセットの種 (島の並び・打つ順を決める)
public final class EmberBackTrials {
    private EmberBackTrials() {
    }

    public static void markReady(ServerLevel level, EmberBackState.Trial trial) {
        EmberBackState.get(level).markReady(trial);
    }

    public static void ring(ServerLevel level, EmberBackState.Trial trial, @Nullable ServerPlayer by) {
        EmberBackState.get(level).ring(trial);
    }

    public static boolean sleeping(ServerLevel level, String room) {
        return EmberBackState.get(level).roomSleeping(room);
    }
}
