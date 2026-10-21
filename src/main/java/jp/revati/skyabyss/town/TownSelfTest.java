package jp.revati.skyabyss.town;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.quest.Action;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.quest.StoryContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;

// 町のクエスト (docs/design/35 の 3.3・3.5) の自己テスト。段: "town"。
//   データ: 15 本のクエスト・限定の人 8 人の会話・足し書き・絵・肖像・動きがそろい、読み込みの誤りがない
//   入口: 章と前のクエストで受けられるか、宿の噂で限定の人のクエストが始まる
//   流れ: 見習いの鯨追い (話す → 受付 → 倒す・集める → 渡す → 報酬の鐘銭と鉄)、限定の人が現れて終えると消える
//   合図: 頂の鐘の位置、印の近く (墓守の三つ目)、子どもの連れ歩きと宿への到着
//   保存: クエストの記録 (QuestLog) と町の記録 (TownData) の書き出しと読み直し。古井戸は一人一度。酒場の地下の壁が開く
//   帯: 段ごとの行き先 (TownNetwork.spots)
public final class TownSelfTest {
    private TownSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("town", TownSelfTest::run));
    }

    static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        BlockPos base = level.getSharedSpawnPos();
        int y = level.getMaxBuildHeight() - 24;
        // 印はみな出現地点のチャンクの中 (中心から 7 以内。読み込まれていない隣のチャンクで見回りが飛ばさないように)
        BlockPos capital = new BlockPos((base.getX() >> 4 << 4) + 8, y, (base.getZ() >> 4 << 4) + 8);
        Map<String, BlockPos> marks = new HashMap<>();
        marks.put("quest_house_9", capital.offset(4, 0, 4));
        marks.put("quest_house_11", capital.offset(-5, 0, 5));
        marks.put("npc_innkeeper", capital.offset(-5, 0, -6));
        marks.put("inn_bed", capital.offset(-7, 0, -7));
        marks.put("npc_guild_clerk", capital.offset(6, 0, -3));
        marks.put("bell", capital.offset(0, 0, -4));
        marks.put("well_chest", capital.offset(6, 0, 6));
        marks.put("cellar_gap", capital.offset(2, 0, -7));
        TownGuests.testMarks = marks;
        TownGuests.testCapital = capital;
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "town_p"));
        FakePlayer q = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "town_q"));
        p.setPos(capital.getX() + 0.5, capital.getY(), capital.getZ() + 0.5);
        q.setPos(capital.getX() + 0.5, capital.getY(), capital.getZ() + 0.5);
        try {
            data(f);
            entry(f, p);
            apprentice(f, level, capital, p);
            signals(f, server, p);
            escort(f, level, capital, q);
            save(f, server, p);
            well(f, server, p);
            cellar(f, level, marks.get("cellar_gap"));
            spots(f, server, marks);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST town crashed", e);
            f.add("crashed: " + e);
        } finally {
            for (TownGuests.Guest g : TownGuests.ALL) {
                TownGuests.find(level, capital, g.id()).forEach(TownGuestEntity::discard);
            }
            TownGuests.testMarks = null;
            TownGuests.testCapital = null;
            p.getInventory().clearContent();
            q.getInventory().clearContent();
            TownData d = TownData.get(server);
            d.wellTaken.remove(p.getUUID());
            d.setDirty();
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST town PASSED ({} quests, {} guests: data, rumor entry, apprentice flow + reward, guest spawn/leave, bell/spot/escort signals, save/load, well once, cellar, band spots)",
                    TownQuests.ALL.size(), TownGuests.ALL.size());
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST town FAILED: {}", m));
        }
    }

    private static boolean resource(String path) {
        try (InputStream in = TownSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    // ---------------------------------------------------------------- データ
    private static void data(List<String> f) {
        for (String e : QuestManager.loadErrors()) {
            if (e.contains("town/")) {
                f.add("quest load error: " + e);
            }
        }
        for (String e : DialogueManager.loadErrors()) {
            if (e.contains("guest_") || e.contains("_town") || e.contains("town_")) {
                f.add("dialogue load error: " + e);
            }
        }
        for (TownQuests.TownQuest tq : TownQuests.ALL) {
            check(f, QuestManager.quest(tq.id()) != null, "quest missing " + tq.id());
        }
        String a = "/assets/skyabyss/";
        check(f, resource(a + "animations/entity/town_guest.animation.json"), "town_guest animation");
        for (TownGuests.Guest g : TownGuests.ALL) {
            check(f, DialogueManager.dialogue(g.role()) != null, "dialogue missing " + g.role());
            check(f, TownQuests.byId(g.quest()) != null && QuestManager.quest(g.quest()) != null, "guest quest " + g.quest());
            check(f, resource(a + "textures/lowpoly/guest_" + g.id() + ".png"), "texture guest_" + g.id());
            for (String face : Dialogue.FACES) {
                check(f, resource(a + "textures/gui/npc/face/guest_" + g.id() + "_" + face + ".png"), "face guest_" + g.id() + " " + face);
            }
        }
        // 足し書きが係の人の会話に混ざっている (墓守の頼み・酔いどれ・地図屋)
        for (String role : List.of("gravekeeper", "drunkard", "mapmaker", "guild_clerk", "innkeeper")) {
            Dialogue d = DialogueManager.dialogue("skyabyss:attendant_" + role);
            check(f, d != null && d.nodes().keySet().stream().anyMatch(n -> n.startsWith("town_")), "town nodes merged into attendant_" + role);
        }
        List<String> errors = new ArrayList<>();
        com.google.gson.JsonObject bad = new com.google.gson.JsonObject();
        bad.addProperty("type", "town");
        bad.addProperty("act", "nope");
        Action.parseList(bad, errors, "test");
        check(f, !errors.isEmpty(), "unknown town act is a load error");
    }

    // ---------------------------------------------------------------- 入口
    private static void entry(List<String> f, FakePlayer p) {
        QuestLog log = QuestLog.of(p);
        TownQuests.TownQuest ch1 = TownQuests.byId("town/ch1_apprentice");
        TownQuests.TownQuest ch2 = TownQuests.byId("town/ch2_net");
        TownQuests.TownQuest g2 = TownQuests.byId("town/grave_2");
        check(f, TownQuests.available(ch1, 1, log) && !TownQuests.available(ch2, 1, log) && TownQuests.available(ch2, 2, log), "chapter gate");
        check(f, !TownQuests.available(g2, 4, log), "grave_2 needs grave_1 done");
        TownQuests.TownQuest first = TownQuests.nextRumor(1, log);
        check(f, first != null && first.kind() == TownQuests.Kind.GUEST && first.chapter() == 1, "first rumor is a chapter-1 guest quest");
        // 宿の噂で始まる (FakePlayer の章は 1)
        var text = TownQuests.rumor(p);
        check(f, text != null && log.entry("town/ch1_apprentice") == null, "a rumor only offers the quest (it does not start yet)");
        check(f, jp.revati.skyabyss.npc.NpcBonds.of(p).bond(TownQuests.INNKEEPER).flags().contains(TownQuests.OFFER_FLAG), "the innkeeper offers to take it on");
        check(f, TownQuests.acceptOffer(p) && log.entry("town/ch1_apprentice") != null, "taking it on starts the apprentice quest");
        check(f, !jp.revati.skyabyss.npc.NpcBonds.of(p).bond(TownQuests.INNKEEPER).flags().contains(TownQuests.OFFER_FLAG), "the offer is cleared after taking it on");
        check(f, "meet".equals(TownGuests.stageOf(p, "town/ch1_apprentice")), "apprentice starts at meet");
        Dialogue d = DialogueManager.dialogue("skyabyss:guest_apprentice");
        Dialogue.Node n = d == null ? null : d.entryNode(new StoryContext(p, "skyabyss:guest_apprentice", null));
        check(f, n != null && n.id().equals("meet"), "apprentice dialogue opens at meet");
        TownQuests.TownQuest second = TownQuests.nextRumor(1, log);
        check(f, second != null && second.id().equals("town/ch1_bell"), "next rumor is the bell keeper " + (second == null ? null : second.id()));
    }

    // ---------------------------------------------------------------- 見習いの流れ
    private static void apprentice(List<String> f, ServerLevel level, BlockPos capital, FakePlayer p) {
        String qid = "town/ch1_apprentice";
        int placed = TownGuests.keep(level, capital, List.of(p));
        List<TownGuestEntity> found = TownGuests.find(level, capital, "apprentice");
        check(f, placed >= 1 && found.size() == 1, "apprentice appears while the quest is active (" + found.size() + ")");
        if (!found.isEmpty()) {
            BlockPos at = TownGuests.testMarks.get("quest_house_9");
            check(f, found.get(0).blockPosition().closerThan(at, 2), "apprentice stands at quest_house_9");
            check(f, found.get(0).role().equals("skyabyss:guest_apprentice") && !found.get(0).shouldBeSaved(), "guest role and not saved");
        }
        TownGuests.keep(level, capital, List.of(p));
        check(f, TownGuests.find(level, capital, "apprentice").size() == 1, "second keep does not duplicate");
        Quests.onTalk(p, "skyabyss:guest_apprentice", "meet");
        check(f, "clerk".equals(TownGuests.stageOf(p, qid)), "meet -> clerk");
        Quests.onTalk(p, "skyabyss:attendant_guild_clerk", "town_test");
        check(f, "hunt".equals(TownGuests.stageOf(p, qid)), "clerk -> hunt");
        for (int i = 0; i < 4; i++) {
            Quests.onKill(p, "minecraft:zombie", true, 0);
        }
        p.getInventory().add(new ItemStack(Items.BONE, 3));
        Quests.poll(p, true);
        check(f, "hand".equals(TownGuests.stageOf(p, qid)), "hunt -> hand (" + TownGuests.stageOf(p, qid) + ")");
        int coins = Coins.count(p);
        Quests.signal(p, "town_hand:guest_apprentice");
        QuestLog.Entry e = QuestLog.of(p).entry(qid);
        check(f, e != null && e.done, "apprentice quest done");
        check(f, Coins.count(p) == coins + 12, "reward 12 bell coins (" + (Coins.count(p) - coins) + ")");
        check(f, p.getInventory().countItem(Items.IRON_INGOT) >= 2, "reward iron");
        TownGuests.keep(level, capital, List.of(p));
        check(f, TownGuests.find(level, capital, "apprentice").isEmpty(), "apprentice leaves after the quest");
    }

    // ---------------------------------------------------------------- 合図
    private static void signals(List<String> f, MinecraftServer server, FakePlayer p) {
        BlockPos bell = TownGuests.testMarks.get("bell");
        check(f, TownQuests.topBell(server, bell.above(38)) && !TownQuests.topBell(server, bell) && !TownQuests.topBell(server, bell.offset(20, 38, 0)),
                "top bell geometry");
        Quests.start(p, "town/ch1_bell");
        Quests.onTalk(p, "skyabyss:guest_bellkeeper", "meet");
        Quests.signal(p, TownQuests.SIGNAL_TOWER_BELL);
        check(f, "back".equals(TownGuests.stageOf(p, "town/ch1_bell")), "tower bell signal -> back");
        // 印の近く: 墓守の三つ目 (喪の家)
        Quests.start(p, "town/grave_3");
        check(f, TownQuests.wantedSpots(p).contains("town_at:quest_house_11"), "grave_3 wants quest_house_11");
        TownQuests.pollSpots(server, List.of(p));
        check(f, "visit".equals(TownGuests.stageOf(p, "town/grave_3")), "far away does not count");
        BlockPos h = TownGuests.testMarks.get("quest_house_11");
        BlockPos old = p.blockPosition();
        p.setPos(h.getX() + 1.5, h.getY(), h.getZ() + 0.5);
        TownQuests.pollSpots(server, List.of(p));
        check(f, "back".equals(TownGuests.stageOf(p, "town/grave_3")), "standing at the mark -> back");
        check(f, jp.revati.skyabyss.lore.Lore.state(p).has("town/grave_3"), "grave_3 lore");
        p.setPos(old.getX() + 0.5, old.getY(), old.getZ() + 0.5);
    }

    // ---------------------------------------------------------------- 子どもを宿へ
    private static void escort(List<String> f, ServerLevel level, BlockPos capital, FakePlayer q) {
        String qid = "town/ch3_thunder";
        Quests.start(q, qid);
        TownGuests.keep(level, capital, List.of(q));
        List<TownGuestEntity> kids = TownGuests.find(level, capital, "child");
        check(f, kids.size() == 1, "child appears");
        if (kids.isEmpty()) {
            return;
        }
        TownGuestEntity child = kids.get(0);
        Quests.onTalk(q, "skyabyss:guest_child", "find");
        check(f, "escort".equals(TownGuests.stageOf(q, qid)), "find -> escort");
        q.setPos(child.getX() + 2, child.getY(), child.getZ());
        TownGuests.escort(level, List.of(q));
        check(f, q.getUUID().equals(child.follow()), "child follows the player");
        BlockPos inn = TownGuests.testMarks.get("npc_innkeeper");
        child.teleportTo(inn.getX() + 2.5, inn.getY(), inn.getZ() + 0.5);
        q.setPos(inn.getX() + 3.5, inn.getY(), inn.getZ() + 0.5);
        TownGuests.escort(level, List.of(q));
        check(f, "inn".equals(TownGuests.stageOf(q, qid)), "reaching the inn -> inn stage");
        check(f, child.follow() == null, "child stops following at the inn");
        TownGuests.keep(level, capital, List.of(q));
        BlockPos bed = TownGuests.testMarks.get("inn_bed");
        List<TownGuestEntity> now = TownGuests.find(level, capital, "child");
        check(f, now.size() == 1 && now.get(0).blockPosition().closerThan(bed, 2.5),
                "child goes to the inn bed (" + (now.isEmpty() ? "none" : now.get(0).blockPosition().toShortString() + " home " + now.get(0).home()) + ")");
        Quests.onTalk(q, "skyabyss:attendant_innkeeper", "town_child");
        check(f, QuestLog.of(q).entry(qid) != null && QuestLog.of(q).entry(qid).done, "thunder night done");
        boolean drawing = false;
        for (int i = 0; i < q.getInventory().getContainerSize(); i++) {
            drawing |= "child_drawing".equals(jp.revati.skyabyss.quest.QuestItems.token(q.getInventory().getItem(i)));
        }
        check(f, drawing, "child's drawing given");
        TownGuests.keep(level, capital, List.of(q));
        check(f, TownGuests.find(level, capital, "child").isEmpty(), "child leaves after the quest");
    }

    // ---------------------------------------------------------------- 保存
    private static void save(List<String> f, MinecraftServer server, FakePlayer p) {
        QuestLog log = QuestLog.of(p);
        var json = QuestLog.CODEC.encodeStart(JsonOps.INSTANCE, log).getOrThrow();
        QuestLog back = QuestLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        QuestLog.Entry a = back.entry("town/ch1_apprentice");
        QuestLog.Entry b = back.entry("town/ch1_bell");
        check(f, a != null && a.done, "saved apprentice stays done");
        check(f, b != null && !b.done && b.stage == log.entry("town/ch1_bell").stage, "saved bell quest keeps its stage");
        TownData d = new TownData();
        d.cellarOpen = true;
        d.wellTaken.add(p.getUUID());
        CompoundTag tag = d.save(new CompoundTag(), server.registryAccess());
        TownData d2 = new TownData();
        // 読み直し (Factory と同じ道)
        try {
            var m = TownData.class.getDeclaredMethod("load", CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class);
            m.setAccessible(true);
            d2 = (TownData) m.invoke(null, tag, server.registryAccess());
        } catch (ReflectiveOperationException e) {
            f.add("TownData load " + e);
        }
        check(f, d2.cellarOpen && d2.wellTaken.contains(p.getUUID()), "TownData round trip");
    }

    // ---------------------------------------------------------------- 古井戸・地下
    private static void well(List<String> f, MinecraftServer server, FakePlayer p) {
        int coins = Coins.count(p);
        boolean first = TownQuests.claimWell(p);
        boolean second = TownQuests.claimWell(p);
        check(f, first && !second, "well treasure once per player");
        check(f, Coins.count(p) == coins + TownQuests.WELL_COINS, "well coins");
        check(f, p.getInventory().countItem(Items.ECHO_SHARD) >= 1, "well shard");
    }

    private static void cellar(List<String> f, ServerLevel level, BlockPos gap) {
        boolean was = TownCellar.isOpen(level.getServer());
        level.setBlock(gap, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        level.setBlock(gap.above(), Blocks.STONE_BRICKS.defaultBlockState(), 3);
        TownCellar.open(level.getServer());
        check(f, TownCellar.wallGone(level, gap) && TownCellar.isOpen(level.getServer()), "cellar wall opens");
        level.setBlock(gap, Blocks.STONE_BRICKS.defaultBlockState(), 3);
        TownCellar.keepOpen(level.getServer());
        check(f, TownCellar.wallGone(level, gap), "cellar wall reopens after a rebuild");
        if (!was) {
            TownCellar.close(level.getServer());
        }
        level.setBlock(gap, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(gap.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void spots(List<String> f, MinecraftServer server, Map<String, BlockPos> marks) {
        Map<String, BlockPos> s = TownNetwork.spots(server);
        check(f, marks.get("quest_house_9").equals(s.get("town/ch1_apprentice/meet")), "band: apprentice meet -> quest_house_9");
        check(f, marks.get("npc_guild_clerk").equals(s.get("town/ch1_apprentice/clerk")), "band: clerk stage -> npc_guild_clerk");
        check(f, marks.get("bell").equals(s.get("town/ch1_bell/ring")), "band: bell stage -> bell");
        check(f, marks.get("npc_innkeeper").equals(s.get("town/ch3_thunder/escort")), "band: escort -> inn");
        check(f, marks.get("quest_house_11").equals(s.get("town/grave_3/visit")), "band: grave_3 visit -> quest_house_11");
        check(f, !s.containsKey("town/ch1_apprentice/hunt"), "band: no spot for hunting");
        check(f, BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.parse(TownSetup.GUEST_ID)), "town_guest registered");
    }
}
