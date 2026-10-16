package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

// [仮の実装 / STUB] 招雷の輪 (予告 → 落雷。柱があれば柱へ。12 の 10.3節)。本物は背中の担当 (担当 1) が作る。
// 11.1節の名前と引数だけそろえた空の実装 (何もしない)。統合で置き換える。
// 心臓の招雷は、柱の段の数え方が心臓に固有なので、心臓の側 (StormHeart) が自分で落とす
public final class StormStrike {
    private StormStrike() {
    }

    public static void call(ServerLevel level, Vec3 target, int warnTicks, float damage, @Nullable Entity owner) {
    }
}
