package jp.revati.skyabyss.titan;

import javax.annotation.Nullable;

import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.util.Players;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 初めて乗るときの待ち (fun_review P3-1)。乗り込みの時刻 (雷翼の夕立の刻・余燼の夜) を待たせない。
// まだその巨神の心臓を止めておらず、乗る資格のある人が、決まった場所 (峰・灰の地の口) の近くに WAIT tick 居続けたら、
// 巨神が気づいて来る。時刻はふだんの来方 (味つけ) として残る。決まり: 試練・背は時刻や天気で閉じない (状態は遊び方だけを変える)
public final class FirstBoardWait {
    // 気づくまでの待ち (30 秒)
    public static final int WAIT = 600;
    // 決まった場所のまわりで「待っている」とみなす距離
    public static final double RANGE = 48;

    private int waited;

    // every tick おきに呼ぶ。来るべきときは待っていた人を返す。待つ人がいなければ数えを戻す
    @Nullable
    public ServerPlayer tick(ServerLevel level, String titan, @Nullable BlockPos centre, int every) {
        return count(waiter(level, titan, centre), every);
    }

    // 待つ人 p (いなければ null) を every tick ぶん数える。WAIT に届いたら p を返して数え直す
    @Nullable
    public ServerPlayer count(@Nullable ServerPlayer p, int every) {
        if (p == null) {
            waited = 0;
            return null;
        }
        waited += every;
        if (waited < WAIT) {
            return null;
        }
        waited = 0;
        return p;
    }

    public int waited() {
        return waited;
    }

    // 決まった場所の近くで、初めての乗り込みを待っている人 (いちばん近い人)。centre が null なら誰も待っていない
    @Nullable
    public static ServerPlayer waiter(ServerLevel level, String titan, @Nullable BlockPos centre) {
        if (centre == null) {
            return null;
        }
        ServerPlayer best = null;
        double bestD = RANGE;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            double d = Math.hypot(p.getX() - centre.getX(), p.getZ() - centre.getZ());
            if (d <= bestD && waiting(p, titan)) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    // この人が初めての乗り込みを待てるか (生きていて、まだ止めておらず、乗る資格がある)
    public static boolean waiting(ServerPlayer p, String titan) {
        return TitanKeeper.eligible(p) && !Progression.stopped(p.server, p.getUUID(), titan) && Progression.canBoard(p, titan);
    }
}
