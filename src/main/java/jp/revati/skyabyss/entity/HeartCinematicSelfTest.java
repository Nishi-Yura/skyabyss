package jp.revati.skyabyss.entity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.titan.ember.heart.EmberHeartSetup;
import jp.revati.skyabyss.titan.storm.heart.StormHeartSetup;
import jp.revati.skyabyss.titan.tide.heart.TideHeartSetup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

// 心臓の登場と倒れる演出 (HeartBoss) の自己テスト (SKYABYSS_SELFTEST)。四つの心臓それぞれを、オーバーワールドの空に一体置いて確かめる。
// 登場: 湧いて 1 tick で始まる・傷を受けない・INTRO_TICKS で終わる・終わると傷を受ける。
// 倒れる: 倒すと演出が始まる・演出のあいだは消えず (戦利品・報告もまだ)・DEATH_TICKS で消える。イベントが順に届く。
// 高さの固定 (岩鯨・潮殻・余燼): 何かに押されて上へ動いても、次の tick で元の高さへ戻る
public final class HeartCinematicSelfTest {
    private static final Map<HeartCinematicEvent.Kind, Integer> EVENTS = new EnumMap<>(HeartCinematicEvent.Kind.class);
    private static boolean listening;

    private HeartCinematicSelfTest() {
    }

    public static void load(MinecraftServer server) {
        server.overworld().setChunkForced(0, 0, true);
        if (!listening) {
            listening = true;
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, HeartCinematicEvent.class,
                    e -> EVENTS.merge(e.kind(), 1, Integer::sum));
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        try {
            ServerLevel level = server.overworld();
            Difficulty before = server.getWorldData().getDifficulty();
            if (before == Difficulty.PEACEFUL) {
                server.setDifficulty(Difficulty.NORMAL, true);
            }
            try {
                checkOne("stone_whale_heart", ModEntities.STONE_WHALE_HEART.get(), level, failures, true);
                checkOne("tide_heart", TideHeartSetup.TIDE_HEART.get(), level, failures, true);
                checkOne("storm_heart", StormHeartSetup.STORM_HEART.get(), level, failures, false);
                checkOne("ember_heart", EmberHeartSetup.EMBER_HEART.get(), level, failures, true);
            } finally {
                server.setDifficulty(before, true);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST heart_cinematic crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST heart_cinematic PASSED (4 hearts: intro, invulnerable intro, death animation, events, height hold)");
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST heart_cinematic FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static <T extends HeartBoss> void checkOne(String name, EntityType<T> type, ServerLevel level, List<String> failures, boolean holdsHeight) {
        EVENTS.clear();
        T heart = type.create(level);
        if (heart == null) {
            failures.add(name + ": could not create");
            return;
        }
        heart.moveTo(8.5, 200, 8.5, 0f, 0f);
        heart.setPersistenceRequired();
        level.addFreshEntity(heart);
        try {
            // 登場
            check(!heart.isIntroPlaying(), name + ": intro playing before the first tick", failures);
            heart.tick();
            heart.tick();
            check(heart.isIntroPlaying() && heart.cinematicStage() == HeartBoss.Stage.INTRO, name + ": intro did not start", failures);
            check(EVENTS.getOrDefault(HeartCinematicEvent.Kind.INTRO_START, 0) == 1, name + ": no INTRO_START event", failures);
            float full = heart.getHealth();
            heart.invulnerableTime = 0;
            heart.hurt(heart.damageSources().generic(), 30f);
            check(heart.getHealth() == full, name + ": took damage during the intro", failures);
            for (int i = 0; i < HeartBoss.INTRO_TICKS / 2; i++) {
                heart.tick();
            }
            check(heart.isIntroPlaying() && heart.cinematicProgress(0f) > 0.3f && heart.cinematicProgress(0f) < 0.7f,
                    name + ": intro progress is wrong (" + heart.cinematicProgress(0f) + ")", failures);
            check(heart.focusPoint().y > heart.getY(), name + ": focus point is below the heart", failures);
            for (int i = 0; i < HeartBoss.INTRO_TICKS; i++) {
                heart.tick();
            }
            check(!heart.isIntroPlaying() && heart.cinematicStage() == HeartBoss.Stage.NONE, name + ": intro did not end", failures);
            check(EVENTS.getOrDefault(HeartCinematicEvent.Kind.INTRO_END, 0) == 1, name + ": no INTRO_END event", failures);
            heart.invulnerableTime = 0;
            heart.hurt(heart.damageSources().generic(), 5f);
            check(heart.getHealth() < full, name + ": took no damage after the intro", failures);

            // 高さの固定: 押されて上へ流れても、次の tick で戻る
            if (holdsHeight) {
                double y = heart.getY();
                heart.setPos(heart.getX(), y + 3, heart.getZ());
                heart.setDeltaMovement(0, 0.5, 0);
                heart.tick();
                check(Math.abs(heart.getY() - y) < 0.05, name + ": height was not held (y " + heart.getY() + " from " + y + ")", failures);
            }

            // 倒れる
            heart.invulnerableTime = 0;
            heart.hurt(heart.damageSources().genericKill(), Float.MAX_VALUE);
            check(heart.isDeadOrDying() && heart.cinematicStage() == HeartBoss.Stage.DEATH, name + ": death did not start", failures);
            check(EVENTS.getOrDefault(HeartCinematicEvent.Kind.DEATH_START, 0) == 1, name + ": no DEATH_START event", failures);
            for (int i = 0; i < HeartBoss.DEATH_TICKS / 2; i++) {
                heart.tick();
            }
            check(!heart.isRemoved() && EVENTS.getOrDefault(HeartCinematicEvent.Kind.DEATH_END, 0) == 0, name + ": removed before the death animation finished", failures);
            check(heart.cinematicProgress(0f) > 0.4f && heart.cinematicProgress(0f) < 0.6f, name + ": death progress is wrong (" + heart.cinematicProgress(0f) + ")", failures);
            for (int i = 0; i < HeartBoss.DEATH_TICKS; i++) {
                heart.tick();
            }
            check(heart.isRemoved() && EVENTS.getOrDefault(HeartCinematicEvent.Kind.DEATH_END, 0) == 1, name + ": not removed after the death animation", failures);
        } finally {
            heart.discard();
        }
    }
}
