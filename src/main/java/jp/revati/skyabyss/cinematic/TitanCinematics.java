package jp.revati.skyabyss.cinematic;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import jp.revati.skyabyss.entity.HeartBoss;
import jp.revati.skyabyss.entity.HeartCinematicEvent;
import jp.revati.skyabyss.entity.StoneWhaleHeart;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.ember.heart.EmberHeart;
import jp.revati.skyabyss.titan.storm.heart.StormHeart;
import jp.revati.skyabyss.titan.tide.heart.TideHeart;

// 巨神の心臓が現れる場面の呼び口。心臓の登場の演出 (HeartCinematicEvent の INTRO_START) で onHeartCinematic から呼ぶ。
//   使い方: TitanCinematics.heartAppears(level, Titans.STONE_WHALE, heartEntity)
//   戻りは場面を始めた人数。始まった人は場面の間 (約 4 秒) 無敵で動けず、スキップもできる。場面のあとの処理 (戦いの始まりなど) は
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
        return heartAppears(level.players(), titanId, heart, range);
    }

    // 見せる人の一覧を渡す形 (自己テストが、いる人の一覧を作って確かめる)
    public static int heartAppears(Iterable<? extends ServerPlayer> players, String titanId, Entity heart, double range) {
        String scene = Scenes.heartId(titanId);
        if (Scenes.get(scene) == null) {
            SkyAbyss.LOGGER.warn("no cinematic for titan heart '{}'", titanId);
            return 0;
        }
        int started = 0;
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(heart) <= range * range && Cinematics.startOnce(player, scene, heart, heart.position())) {
                started++;
            }
        }
        return started;
    }

    static void onHeartCinematic(HeartCinematicEvent event) {
        HeartBoss boss = event.boss();
        if (event.kind() != HeartCinematicEvent.Kind.INTRO_START || !(boss.level() instanceof ServerLevel level)) {
            return;
        }
        String titan = titanOf(boss);
        if (titan != null) {
            heartAppears(level, titan, boss);
        }
    }

    private static String titanOf(HeartBoss boss) {
        if (boss instanceof StoneWhaleHeart) return Titans.STONE_WHALE;
        if (boss instanceof TideHeart) return Titans.TIDE_SHELL;
        if (boss instanceof StormHeart) return Titans.THUNDER_WING;
        if (boss instanceof EmberHeart) return Titans.EMBER;
        return null;
    }
}
