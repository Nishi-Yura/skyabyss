package jp.revati.skyabyss.titan;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 巨神が止まって乗れるようになってからの地上での滞在 (四体で共通)。20 分 = 24000 tick。
// 乗れる窓の長さはこの一つで決まる (岩鯨は鎖が降りてから、潮殻は潮門が開いてから、雷翼は止まり木に着いてから、余燼は浮いて眠ってから)。
// 途中で去るのは、心臓が止まって眠るとき (TitanDormancy)、遠くまで人がいなくなって呼び直されるとき (TitanKeeper)、
// 巨神での移動 (titan.travel) が始まるときだけ。残りは各巨神の保存データ (RestTicks / ForcedTicks) に入り、再起動しても続く。
public final class TitanStay {
    public static final int TICKS = 24000;
    // 去る前の知らせ: 残り 2 分と 30 秒
    public static final int WARN_TICKS = 2400;
    public static final int WARN_LAST = 600;
    private static final double RANGE = 200;

    private TitanStay() {
    }

    // 残りが知らせの境ちょうどなら、近くの人に一行 (key = message.skyabyss.stay_ending.<titan>)
    public static void warnIfDue(ServerLevel level, Vec3 at, int left, String key) {
        if (left != WARN_TICKS && left != WARN_LAST) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (TitanKeeper.eligible(p) && p.distanceToSqr(at.x, p.getY(), at.z) < RANGE * RANGE) {
                Notice.bar(p, ModText.lore(key));
            }
        }
    }
}
