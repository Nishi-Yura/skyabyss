package jp.revati.skyabyss.story.finale.founder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.Rebirths;
import jp.revati.skyabyss.story.finale.FinaleEnding;
import jp.revati.skyabyss.story.finale.FinaleWorld;
import jp.revati.skyabyss.story.finale.mob.FounderBoss;
import jp.revati.skyabyss.util.LifeKeep;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 裏ボス「初代の空見」の自己テスト (docs/design/53)。段: "founder"
//   素材と文 (頼み・伝承・品・称号・形) / 鐘の内の地形 (床の天然磁石・帰りの台・銅の壁・舌の鎖) /
//   頼みの流れ (結末二つでは始まらない → 三つで始まる → 語り部 → 鐘の内 → 退ける → 報酬・称号・最速 → 語り部で済む) /
//   前の版の結末も見たものに数える / 倒れても消えない印 (LifeKeep) / 肩掛けを着る・脱ぐ /
//   始祖: 体力・聞き耳の返し (傷が通らない)・受ける傷・膝をつく (傷が通らない)・片付け
public final class FounderSelfTest {
    private FounderSelfTest() {
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    public static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel cloud = server.getLevel(FinaleWorld.LEVEL);
        try {
            check(f, cloud != null, "the cloud sea dimension is missing");
            if (cloud == null) {
                report(f);
                return;
            }
            assets(f);
            terrain(cloud, f);
            FakePlayer p = FakePlayerFactory.get(cloud, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000f0d1"), "founder_test"));
            FakePlayer q = FakePlayerFactory.get(cloud, new GameProfile(UUID.fromString("7a0b1d00-0000-4000-8000-00000000f0d2"), "founder_test_b"));
            flow(cloud, p, f);
            keep(p, q, f);
            boss(cloud, p, f);
            for (FakePlayer x : List.of(p, q)) {
                x.getPersistentData().remove(FinaleEnding.KEY);
                x.getPersistentData().remove(FinaleEnding.SEEN);
                x.getPersistentData().remove(Founder.KEY);
                x.getPersistentData().remove(Founder.BEST);
                FounderItems.setWearing(x, false);
                Quests.forgetAll(x);
                x.getInventory().clearContent();
            }
        } catch (Exception ex) {
            SkyAbyss.LOGGER.error("SELFTEST founder crashed", ex);
            f.add("crashed: " + ex);
        }
        report(f);
    }

    static boolean res(String path) {
        return FounderSelfTest.class.getResource(path) != null;
    }

    static void assets(List<String> f) {
        QuestDef def = QuestManager.quest(Founder.QUEST);
        check(f, def != null && def.startWhen() != null && def.stages().size() == 4 && def.chapter().equals("finale"), "the side_founder quest is missing or malformed");
        check(f, res("/data/skyabyss/lore/entry/finale/founder.json") && res("/data/skyabyss/lore/entry/finale/eighth_verse.json"), "the founder lore entries are missing");
        check(f, res("/data/skyabyss/dialogue/finale/storyteller_founder.json"), "the storyteller's eighth verse dialogue is missing");
        check(f, res("/data/skyabyss/finale/founder.json"), "the bell interior terrain is missing");
        for (String a : List.of("/assets/skyabyss/lowpoly/founder.json", "/assets/skyabyss/animations/entity/founder.animation.json",
                "/assets/skyabyss/geo/entity/founder_lowpoly.geo.json", "/assets/skyabyss/textures/lowpoly/founder.png",
                "/assets/skyabyss/lowpoly/items/founder_shawl/common.json", "/assets/skyabyss/textures/lowpoly/items/founder_shawl/common_atlas.png",
                "/assets/skyabyss/textures/item/founder_shawl.png", "/assets/skyabyss/textures/item/founder_handbell.png",
                "/assets/skyabyss/models/item/founder_shawl.json", "/assets/skyabyss/models/item/founder_handbell.json")) {
            check(f, res(a), "missing asset " + a);
        }
        check(f, RebirthTitles.byId("yattsume") != null && RebirthTitles.all().size() <= RebirthTitles.MAX, "the founder title is missing");
        check(f, BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "founder_shawl"))
                && BuiltInRegistries.ITEM.containsKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "founder_handbell")), "the founder items are not registered");
    }

    static void terrain(ServerLevel cloud, List<String> f) {
        FinaleWorld.build(cloud);
        check(f, FinaleWorld.version(cloud.getServer()) >= 8, "the cloud sea world was not built to version 8 (bell interior)");
        FounderArena.terrain().pour(cloud);
        BlockPos c = FounderArena.mark("center");
        cloud.getChunk(c);
        check(f, cloud.getBlockState(c.below()).is(Blocks.LODESTONE), "the bell floor has no lodestone at its center");
        check(f, cloud.getBlockState(FounderArena.mark("return")).is(Blocks.LODESTONE), "the return stone is missing");
        check(f, BuiltInRegistries.BLOCK.getKey(cloud.getBlockState(c.offset(16, 9, 0)).getBlock()).getPath().contains("copper"), "the bell wall is not copper");
        check(f, cloud.getBlockState(c.above(20)).is(Blocks.CHAIN) && cloud.getBlockState(c.above(5)).isAir(), "the clapper chain or the head room over the floor is wrong");
        for (int i = 1; i <= 7; i++) {
            check(f, FounderArena.terrain().marks.containsKey("verse_" + i), "verse mark " + i + " is missing");
        }
    }

    static void flow(ServerLevel cloud, FakePlayer p, List<String> f) {
        Quests.forgetAll(p);
        p.getPersistentData().remove(Founder.KEY);
        p.getPersistentData().remove(Founder.BEST);
        p.getPersistentData().remove(FinaleEnding.KEY);
        p.getPersistentData().putString(FinaleEnding.SEEN, "calm,break");
        Quests.poll(p, false);
        check(f, QuestLog.of(p).entry(Founder.QUEST) == null, "the founder quest started after only two endings");
        // 前の版で結末を一つ選んでいた人: 今の結末も見たものに数える
        p.getPersistentData().putString(FinaleEnding.KEY, "accept");
        check(f, FinaleEnding.seen(p).size() == 3, "the current ending did not count as seen: " + FinaleEnding.seen(p));
        Quests.poll(p, false);
        QuestLog.Entry e = QuestLog.of(p).entry(Founder.QUEST);
        check(f, e != null && !e.done && e.stage == 0, "the founder quest did not start after all three endings");
        check(f, !Founder.available(p), "the bell interior was open before the storyteller sang the eighth verse");
        Quests.onTalk(p, "skyabyss:storyteller", "founder_verse");
        check(f, Founder.available(p), "the bell interior did not open after the storyteller's verse");
        Quests.signal(p, Founder.ENTER_SIGNAL);
        e = QuestLog.of(p).entry(Founder.QUEST);
        check(f, e != null && e.stage == 2, "entering the bell did not advance the quest: " + (e == null ? "none" : e.stage));
        Founder.onDefeat(cloud, List.of(p), 2400);
        check(f, Founder.beaten(p) && Founder.best(p) == 2400, "defeating the founder did not record it");
        int shawls = p.getInventory().countItem(FounderItems.SHAWL_ITEM.get());
        int bells = p.getInventory().countItem(FounderItems.HANDBELL.get());
        check(f, shawls == 1 && bells == 1, "the founder rewards were not given: shawl " + shawls + " bell " + bells);
        check(f, Lore.state(p).has(Founder.LORE_FOUNDER) && Lore.state(p).has(Founder.LORE_VERSE), "the founder lore pages were not unlocked");
        check(f, RebirthTitles.has(RebirthTitles.unlockedMask(Rebirths.facts(p)), "yattsume"), "the founder title was not unlocked");
        // 二度目: 品は増えず、最速だけ縮む
        Founder.onDefeat(cloud, List.of(p), 1800);
        check(f, p.getInventory().countItem(FounderItems.SHAWL_ITEM.get()) == 1 && Founder.best(p) == 1800, "a second win gave items again or did not lower the best time");
        e = QuestLog.of(p).entry(Founder.QUEST);
        check(f, e != null && e.stage == 3, "defeating the founder did not advance the quest to the storyteller");
        Quests.onTalk(p, "skyabyss:storyteller", "founder_after");
        e = QuestLog.of(p).entry(Founder.QUEST);
        check(f, e != null && e.done && Founder.available(p), "the founder quest did not finish (or the bell closed after it)");
        FounderItems.setWearing(p, true);
        check(f, FounderItems.wearing(p), "the shawl did not go on");
        FounderItems.setWearing(p, false);
        check(f, !FounderItems.wearing(p), "the shawl did not come off");
    }

    static void keep(FakePlayer p, FakePlayer q, List<String> f) {
        for (String k : List.of(Founder.KEY, Founder.BEST, FinaleEnding.SEEN, FinaleEnding.KEY, "skyabyss_moon_trial.core")) {
            q.getPersistentData().remove(k);
        }
        p.getPersistentData().putString(FinaleEnding.KEY, "calm");
        p.getPersistentData().putInt("skyabyss_moon_trial.core", 900);
        LifeKeep.copy(p, q);
        check(f, q.getPersistentData().getBoolean(Founder.KEY) && q.getPersistentData().contains(Founder.BEST)
                && "calm".equals(q.getPersistentData().getString(FinaleEnding.KEY)) && q.getPersistentData().contains(FinaleEnding.SEEN)
                && q.getPersistentData().getInt("skyabyss_moon_trial.core") == 900, "late-chapter marks are lost on death (LifeKeep)");
        p.getPersistentData().remove("skyabyss_moon_trial.core");
        q.getPersistentData().remove("skyabyss_moon_trial.core");
    }

    static void boss(ServerLevel cloud, FakePlayer p, List<String> f) {
        FounderArena.clear(cloud);
        FounderBoss b = FounderArena.summon(cloud);
        check(f, b != null, "the founder could not be summoned");
        if (b == null) {
            return;
        }
        check(f, Math.abs(b.getMaxHealth() - FounderBoss.HP) < 1 && FounderArena.real(cloud) == b, "the founder's health or lookup is wrong: " + b.getMaxHealth());
        float hp = b.getHealth();
        b.devListen();
        boolean took = b.hurt(cloud.damageSources().playerAttack(p), 50);
        check(f, !took && b.getHealth() == hp, "a hit during listening was not countered");
        b.devStopListen();
        b.hurt(cloud.damageSources().playerAttack(p), 50);
        check(f, b.getHealth() < hp, "an ordinary hit did not land: " + b.getHealth());
        b.devFinish();
        b.hurt(cloud.damageSources().playerAttack(p), 500);
        check(f, b.finishing() && b.isAlive() && b.getHealth() >= 1, "the founder died instead of kneeling to sing");
        FounderArena.clear(cloud);
        check(f, FounderArena.real(cloud) == null, "the bell interior was not cleared");
    }

    static void report(List<String> f) {
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST founder PASSED (quest, lore, items, title, model; bell interior floor, return stone, copper wall, clapper, verse marks; "
                    + "quest flow from three endings to the storyteller, rewards once, best time; legacy ending counted; LifeKeep marks; shawl; founder health, counter, hit, kneel, clear)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST founder FAILED: {}", m));
        }
    }
}
