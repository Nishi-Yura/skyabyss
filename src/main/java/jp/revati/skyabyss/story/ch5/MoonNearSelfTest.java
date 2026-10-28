package jp.revati.skyabyss.story.ch5;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.town.TownGuests;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.util.FakePlayer;

// 五章から六章への引き「近づく月」(MoonNear) の自己テスト。段: "moon near"
//   深淵のメインを終えると始まる / 見上げる決まり (夜・空・上向き・地上) / 合図で段が進み、語り部の話で済む / 五章の空の段
public final class MoonNearSelfTest {
    private MoonNearSelfTest() {
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        long time = level.getDayTime();
        try {
            check(f, QuestManager.quest(MoonNear.QUEST) != null, "main_moon_near is not loaded");
            FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "moon_test"));
            int top = level.getMaxBuildHeight() - 4;
            p.moveTo(0.5, top, 0.5, 0f, -60f);
            // 深淵のメインが済むと、見回りで始まる
            Quests.setStage(p, "main_abyss", "report");
            Quests.forceStage(p, "main_abyss");
            Quests.poll(p, false);
            check(f, "look".equals(TownGuests.stageOf(p, MoonNear.QUEST)), "main_moon_near did not start after main_abyss (" + TownGuests.stageOf(p, MoonNear.QUEST) + ")");
            level.setDayTime(6000);
            check(f, !MoonNear.looksAtMoon(p), "the moon was seen at noon");
            level.setDayTime(18000);
            check(f, MoonNear.looksAtMoon(p), "the moon was not seen at night looking up under the open sky");
            p.setXRot(10f);
            check(f, !MoonNear.looksAtMoon(p), "the moon was seen looking down");
            p.setXRot(-60f);
            Quests.signal(p, MoonNear.SIGNAL);
            check(f, "tell".equals(TownGuests.stageOf(p, MoonNear.QUEST)), "the moon signal did not move to tell");
            Quests.onTalk(p, "skyabyss:storyteller", "ch6_moon");
            QuestLog.Entry e = QuestLog.of(p).entry(MoonNear.QUEST);
            check(f, e != null && e.done, "telling the storyteller did not finish main_moon_near");
            check(f, ChapterSky.stage(Chapters.ABYSS).moonBias() > 0.05f, "the abyss chapter sky does not bring the moon nearer");
        } catch (RuntimeException ex) {
            f.add("crashed: " + ex);
        } finally {
            level.setDayTime(time);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST moon near PASSED (starts after the abyss, look-up rule, signal and storyteller, chapter five sky)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST moon near FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String msg) {
        if (!ok) {
            f.add(msg);
        }
    }
}
