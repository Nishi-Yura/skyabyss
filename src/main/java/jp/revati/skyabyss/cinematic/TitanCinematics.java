package jp.revati.skyabyss.cinematic;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

// 巨神の心臓が現れる場面の呼び口 (ボスの担当が、心臓の登場・戦いの始まりの直前に呼ぶ。まだどこからも呼ばれていない = 何も起きない)。
//   使い方: TitanCinematics.heartAppears(level, Titans.STONE_WHALE, heartEntity)
//   戻りは場面を始めた人数。始まった人は場面の間 (約 8 秒) 無敵で動けず、スキップもできる。場面のあとの処理 (戦いの始まりなど) は
//   Cinematics.onEnd(Scenes.heartId(Titans.STONE_WHALE), (player, skipped) -> ...) に掛ける (スキップしても呼ばれる)
//   見せるのは「その巨神の心臓の初見」だけ (人ごとに一度)。ほかの人の画面は変わらない
public final class TitanCinematics {
    // 心臓から何ブロック以内の人に見せるか
    public static final double RANGE = 64.0;

    private TitanCinematics() {
    }

    public static int heartAppears(ServerLevel level, String titanId, Entity heart) {
        return heartAppears(level, titanId, heart, RANGE);
    }

    public static int heartAppears(ServerLevel level, String titanId, Entity heart, double range) {
        String scene = Scenes.heartId(titanId);
        if (Scenes.get(scene) == null) {
            SkyAbyss.LOGGER.warn("no cinematic for titan heart '{}'", titanId);
            return 0;
        }
        int started = 0;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(heart) <= range * range && Cinematics.startOnce(player, scene, heart, heart.position())) {
                started++;
            }
        }
        return started;
    }
}
