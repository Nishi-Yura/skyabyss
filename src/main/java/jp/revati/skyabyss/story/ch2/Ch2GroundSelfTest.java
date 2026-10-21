package jp.revati.skyabyss.story.ch2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterXpBudget;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.npc.ArtisanSpawner;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestItems;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch1.Tasks;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 二章の地上 (C2。docs/design/34 の 4.3、21 の 4.1) の自己テスト。SKYABYSS_SELFTEST_ONLY="ch2 ground"。結果は SELFTEST ch2 ground PASSED / FAILED
//   build    空の上 (x 7300) に塩の蔵の部品を開発コマンドと同じ形で置き、浅い水の箱に難破船を置く
//   vault    碑 2 枚の文・印 (樽 3・棚 3・戸 9・喉)・湧き台の立ち位置 (奥の間は膝の水)・潮の喉 (満潮で水と濡れた梯子、干潮で空気)・塩樽
//            (抱える・傷で割れて戻る・棚に三つで戸が開く・開いたあとは抱えられない)・湧き台 (グループの分だけ湧き、倒しきるとクリアと宝)
//   road     塩の道の標を建てる (地面の上・矢印の向き)・近づくと一行、道の段にいれば合図 (人ごとに一度ずつ)
//   wreck    難破船の型が置ける・箱がある (戦利品の表つき)・箱を開けると人ごとに一度、潮の民の頁と潮見の貝
//   trails   支度の段 2 本 (イサナの海の噂のあとに出る・塩の道は標 5 → 蔵の刻み + 番人・船と貝は船 → 難破船の頁 + 貝)・二本で ch2_ready・
//            宿借りの行列 (筏の跡の合図)・職人の立つ所・語り部の報告 (村の記憶の伝承)・杭の読み (分の計算)・経験値の枠・文 (日英)
public final class Ch2GroundSelfTest {
    private static final List<String> F = new ArrayList<>();
    static final BlockPos VAULT_AT = new BlockPos(7300, 150, 7300);
    static final BlockPos WRECK_AT = new BlockPos(7420, 120, 7300);
    static SaltVault.Vault vault;
    static boolean wreckPlaced;
    static BlockPos found;

    private Ch2GroundSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.once("ch2 ground (build)", Ch2GroundSelfTest::build), Step.once("ch2 ground", 40, Ch2GroundSelfTest::run));
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch2_ground_" + suffix).getBytes());
        return FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_c2_" + suffix));
    }

    static void build(MinecraftServer server) {
        ServerLevel level = server.overworld();
        try {
            vault = Ch2Dev.placeVault(level, VAULT_AT);
            // 湧き台の「倒れた」の数え方は、エンティティが動くチャンクだけ (読み込みを保つ)
            forceVault(level, true);
            // 浅い海の箱 (床 y 120、水 121..125 = 深さ 5)
            for (int x = -24; x <= 24; x++) {
                for (int z = -24; z <= 24; z++) {
                    BlockPos b = WRECK_AT.offset(x, 0, z);
                    level.getChunk(b);
                    level.setBlock(b, Blocks.SAND.defaultBlockState(), 2 | 16);
                    for (int y = 1; y <= 5; y++) {
                        level.setBlock(b.above(y), Blocks.WATER.defaultBlockState(), 2 | 16);
                    }
                }
            }
            // 浅瀬の探しは船を置く前に (中心から海の向き +x の 40〜120 に、深さ 5 の箱がある)
            found = Wreck.findSpot(level, WRECK_AT.offset(-60, 6, 0), WRECK_AT.offset(40, 6, 0), WRECK_AT.getY() + 6);
            wreckPlaced = Wreck.place(level, WRECK_AT.above(), Rotation.NONE, null);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("SELFTEST ch2 ground build crashed", e);
            F.add("build crashed: " + e);
        }
    }

    static void forceVault(ServerLevel level, boolean on) {
        for (int cx = (VAULT_AT.getX() - 16) >> 4; cx <= (VAULT_AT.getX() + 64) >> 4; cx++) {
            for (int cz = (VAULT_AT.getZ() - 16) >> 4; cz <= (VAULT_AT.getZ() + 56) >> 4; cz++) {
                level.setChunkForced(cx, cz, on);
            }
        }
    }

    static void run(MinecraftServer server) {
        try {
            if (vault != null) {
                vault(server);
            }
            road(server);
            wreck(server);
            trails(server);
        } catch (Exception e) {
            SkyAbyss.LOGGER.error("SELFTEST ch2 ground crashed", e);
            F.add("crashed: " + e);
        } finally {
            if (vault != null) {
                SaltVault.EXTRA.remove(vault);
                SaltVault.FORCE.remove(vault.pos().asLong());
            }
            SiteSpawner.clearAll(server.overworld());
            forceVault(server.overworld(), false);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch2 ground PASSED (salt vault rooms/throat/barrels/door/pads/clear, salt road marks, wreck chests/page/shell, "
                    + "salt_road + ebb_boat to ch2_ready, drift trail, artisan spot, report memory, stakes, xp budget, texts)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch2 ground FAILED: {}", m));
        }
        F.clear();
    }

    // ---------------------------------------------------------------- vault
    static void vault(MinecraftServer server) {
        ServerLevel level = server.overworld();
        SaltVault.Vault v = vault;
        Blueprint bp = Blueprint.get(SaltVault.SITE);
        for (String m : List.of("tablet", "wall_tablet", "sump_lo", "sump_hi", "exit", "mouth", "room1", "room2", "room3")) {
            check(bp.marks.containsKey(m), "the vault blueprint has no mark " + m);
        }
        for (String[] t : new String[][] {{"tablet", SaltVault.SIGN}, {"wall_tablet", SaltVault.WALL_SIGN}}) {
            BlockPos at = v.mark(t[0]);
            check(at != null && level.getBlockEntity(at) instanceof TabletBlockEntity tb && tb.key().equals(t[1]),
                    "the vault " + t[0] + " does not carry " + t[1] + " (at " + at + ")");
        }
        check(LoreManager.catalog().entry(Ch2Ground.LORE_VAULT) != null, "the vault carving lore is missing");
        int door = 0;
        for (int k = 0; k < SaltVault.DOOR_CELLS; k++) {
            if (level.getBlockState(v.door(k)).is(Blocks.SMOOTH_QUARTZ)) {
                door++;
            }
        }
        check(door == SaltVault.DOOR_CELLS, "the salt door should be nine quartz cells (" + door + ")");
        check(SaltVault.atHome(level, v) == SaltVault.BARRELS && SaltVault.onShelf(level, v) == 0, "three salt barrels should wait on the wet floor: " + SaltVault.describe(level, v));
        for (int k = 0; k < SaltVault.BARRELS; k++) {
            BlockPos s = v.shelf(k);
            check(level.getBlockState(s).isAir() && !level.getBlockState(s.below()).isAir(), "shelf spot " + k + " is not free room on the shelf at " + s);
        }
        // 湧き台の立ち位置 (奥の間は膝の水)
        SiteSpawner.Def def = SiteSpawner.def(SaltVault.SITE);
        check(def != null && def.pads().size() == 6 && def.xp() == SaltVault.XP && def.lastRoom().equals(SaltVault.ROOM_DEEP), "the vault spawn pads are wrong");
        if (def != null) {
            for (SiteSpawner.Pad pad : def.pads()) {
                BlockPos at = def.padAt(pad, v.pos());
                boolean room = (level.getBlockState(at).isAir() || level.getBlockState(at).is(Blocks.WATER)) && level.getBlockState(at.above()).isAir()
                        && level.getBlockState(at.below()).isSolid();
                check(room, "vault pad " + pad.room() + " at " + at + " is not standing room (" + level.getBlockState(at) + " / below " + level.getBlockState(at.below()) + ")");
            }
        }
        // 潮の喉: 満潮で水 (梯子は水に浸かる)、干潮で空気。強制を外すと世界の潮に戻る
        SaltVault.setSump(level, v, false);
        check(!SaltVault.sumpWet(level, v), "the throat did not drain");
        check(SaltVault.setSump(level, v, true) && SaltVault.sumpWet(level, v), "the throat did not flood");
        int wetLadders = 0;
        int water = 0;
        for (int[] c : SaltVault.sumpCells()) {
            var st = level.getBlockState(v.at(c[0], c[1], c[2]));
            if (st.is(Blocks.LADDER) && st.getValue(BlockStateProperties.WATERLOGGED)) {
                wetLadders++;
            } else if (st.is(Blocks.WATER)) {
                water++;
            }
        }
        check(wetLadders == 8 && water >= 40, "the flooded throat should hold 8 wet ladders and water (" + wetLadders + " / " + water + ")");
        // 水が喉の外へ漏れない (喉の上の通路は空気のまま)
        check(level.getBlockState(v.at(0, -11, 12)).isAir() && level.getBlockState(v.at(0, -11, 20)).isAir(), "the throat water leaked into the corridor");
        check(!SaltVault.setSump(level, v, true), "flooding twice should do nothing");
        SaltVault.setSump(level, v, false);
        check(!SaltVault.sumpWet(level, v), "the throat did not drain again");
        // 塩樽
        FakePlayer a = fake(server, "barrel");
        a.getInventory().clearContent();
        try {
            check(SaltVault.lift(a, level, v, 0) && SaltVault.carried(a) == 1 && SaltVault.atHome(level, v) == 2, "lifting a salt barrel failed");
            check(!SaltVault.lift(a, level, v, 1), "a second barrel was lifted while carrying one");
            SaltVault.breakBarrel(a);
            check(SaltVault.carried(a) == 0, "a hit did not break the carried barrel");
            SaltVault.tidyBarrels(level, v, List.of());
            check(SaltVault.atHome(level, v) == 3, "the broken barrel did not come back (" + SaltVault.atHome(level, v) + ")");
            for (int k = 0; k < SaltVault.BARRELS; k++) {
                check(SaltVault.lift(a, level, v, k), "barrel " + k + " could not be lifted");
                check(SaltVault.deliver(a, level, v, v.shelf(k)), "barrel " + k + " could not be set on the shelf");
                if (k < SaltVault.BARRELS - 1) {
                    check(!SaltVault.doorOpen(level, v), "the door opened before three barrels");
                }
            }
            check(SaltVault.doorOpen(level, v) && SaltVault.onShelf(level, v) == 3, "three barrels on the shelf did not open the salt door: " + SaltVault.describe(level, v));
            check(!SaltVault.lift(a, level, v, 0), "a barrel could be lifted after the door opened");
            check(ItemStack.matches(SaltVault.token(), SaltVault.token()) && SaltVault.isToken(SaltVault.token()) && !SaltVault.isToken(new ItemStack(Items.BARREL)),
                    "the salt barrel token is not recognised");
        } finally {
            a.getInventory().clearContent();
        }
        // 湧き台: 一人のグループの分だけ湧き、倒しきるとクリア (経験値・宝)
        FakePlayer b = fake(server, "vault_b");
        SiteProgressData progress = SiteProgressData.get(server);
        try {
            progress.forget(b.getUUID(), SaltVault.SITE);
            progress.forgetTreasure(b.getUUID(), SaltVault.TREASURE);
            b.getInventory().clearContent();
            BlockPos r1 = v.mark("room1");
            b.setPos(r1.getX() + 0.5, r1.getY(), r1.getZ() + 0.5);
            long now = level.getGameTime();
            SiteSpawner.tick(level, List.of(b), now);
            UUID g = Groups.keyOf(b);
            int live = SiteSpawner.liveCount(SaltVault.SITE, v.pos(), g) + SiteSpawner.pendingCount(SaltVault.SITE, v.pos(), g);
            check(live == 6, "the vault should hold 6 enemies for the group (" + live + ")");
            for (UUID id : SiteSpawner.liveIds(SaltVault.SITE, v.pos(), g)) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            SiteSpawner.tick(level, List.of(b), now + 40);
            check(progress.cleared(b.getUUID(), SaltVault.SITE), "clearing the vault was not recorded");
            check(Growth.get(b).discoveries.contains("site:" + SaltVault.SITE), "the vault clear xp was not granted");
            if (itemExists("skyabyss:salt_crystal")) {
                check(QuestItems.count(b, "skyabyss:salt_crystal", null) >= 6, "the first vault clear should hand over salt crystals");
            }
        } finally {
            SiteSpawner.clearAll(level);
            progress.forget(b.getUUID(), SaltVault.SITE);
            progress.forgetTreasure(b.getUUID(), SaltVault.TREASURE);
            Growth.get(b).discoveries.clear();
            b.getInventory().clearContent();
        }
    }

    static boolean itemExists(String id) {
        return BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(id));
    }

    // ---------------------------------------------------------------- road
    static void road(MinecraftServer server) {
        ServerLevel level = server.overworld();
        // 地面の箱の上に標を建てる (矢印は +x へ)
        BlockPos base = new BlockPos(7300, 140, 7400);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.getChunk(base.offset(x, 0, z));
                level.setBlock(base.offset(x, 0, z), Blocks.SAND.defaultBlockState(), 2 | 16);
            }
        }
        BlockPos at = SaltVault.placeMark(level, base.getX(), base.getZ(), base.offset(30, 0, 0));
        check(at.getY() == base.getY() + 1 && level.getBlockState(at).is(Blocks.CALCITE), "the salt mark is not standing on the ground (" + at + " " + level.getBlockState(at) + ")");
        check(level.getBlockState(at.east(2)).is(Blocks.CALCITE), "the salt mark arrow does not point toward the next mark");
        check(SaltVault.rotToward(0, -5) == 0 || Blueprint.transform(0, 0, -1, SaltVault.rotToward(0, -5), false, BlockPos.ZERO).getZ() < 0, "rotToward north is wrong");
        FakePlayer p = fake(server, "road");
        try {
            p.getPersistentData().remove(SaltVault.SEEN);
            p.getPersistentData().remove(SaltVault.COUNTED);
            SaltVault.seeMark(p, 1, false);
            check((p.getPersistentData().getInt(SaltVault.SEEN) & 2) != 0 && p.getPersistentData().getInt(SaltVault.COUNTED) == 0,
                    "seeing a mark outside the road stage should not count it");
        } finally {
            p.getPersistentData().remove(SaltVault.SEEN);
            p.getPersistentData().remove(SaltVault.COUNTED);
        }
    }

    // ---------------------------------------------------------------- wreck
    static void wreck(MinecraftServer server) {
        ServerLevel level = server.overworld();
        check(wreckPlaced, "the shipwreck template could not be placed");
        int chests = 0;
        int loot = 0;
        for (BlockPos q : BlockPos.betweenClosed(WRECK_AT.offset(-24, -3, -24), WRECK_AT.offset(24, 30, 24))) {
            if (level.getBlockState(q).is(Blocks.CHEST)) {
                chests++;
                if (level.getBlockEntity(q) instanceof ChestBlockEntity be && be.getLootTable() != null) {
                    loot++;
                }
            }
        }
        check(chests >= 1 && loot == chests, "the wreck should carry chests with shipwreck loot (" + chests + " chests, " + loot + " with loot)");
        // 浅瀬の探し: 浅い水の箱の中に見つかる
        check(found != null && Math.abs(found.getX() - WRECK_AT.getX()) <= 24, "the wreck spot search did not find the shallow box (" + found + ")");
        FakePlayer p = fake(server, "wreck");
        try {
            Lore.lock(p, Ch2Ground.LORE_WRECK);
            p.getPersistentData().remove("skyabyss_tide_watching_shell_given");
            p.getInventory().clearContent();
            check(Wreck.openChest(p), "the wreck chest gave nothing the first time");
            Tasks.flush();
            check(Lore.state(p).has(Ch2Ground.LORE_WRECK), "the wreck chest did not record the tide people's page");
            check(QuestItems.count(p, "skyabyss:tide_watching_shell", null) == 1, "the wreck chest did not hand over the tide-watching shell");
            check(!Wreck.openChest(p), "the wreck chest gave its page or shell twice");
        } finally {
            Lore.lock(p, Ch2Ground.LORE_WRECK);
            p.getPersistentData().remove("skyabyss_tide_watching_shell_given");
            p.getInventory().clearContent();
        }
    }

    // ---------------------------------------------------------------- trails
    static void trails(MinecraftServer server) {
        FakePlayer p = fake(server, "trail");
        ProgressData progress = ProgressData.get(server);
        List<String> lore = List.of(Ch2Ground.LORE_VAULT, Ch2Ground.LORE_WRECK, Ch2Ground.LORE_RAFT, Ch2Ground.LORE_MEMORY);
        Runnable clean = () -> {
            Quests.forgetAll(p);
            Growth.get(p).discoveries.clear();
            p.getInventory().clearContent();
            lore.forEach(id -> Lore.lock(p, id));
            p.getPersistentData().remove(SaltVault.SEEN);
            p.getPersistentData().remove(SaltVault.COUNTED);
            p.getPersistentData().remove("skyabyss_tide_watching_shell_given");
            p.getPersistentData().remove("skyabyss_ch2_stakes_read");
            SiteProgressData.get(server).forgetTreasure(p.getUUID(), Ch2Ground.RAFT_TREASURE);
            progress.clearPersonal(p.getUUID(), "stone_whale");
        };
        try {
            clean.run();
            check(PrepQuests.defined(2) && PrepQuests.ids(2).equals(Ch2Ground.TRAILS), "the two chapter-2 trail steps are not defined (" + QuestManager.loadErrors() + ")");
            check(QuestManager.loadErrors().stream().noneMatch(s -> s.contains("ch2/") || s.contains("pool_sweep")), "chapter-2 quests were flagged: " + QuestManager.loadErrors());
            for (String id : Ch2Ground.TRAILS) {
                QuestDef def = QuestManager.quest(id);
                check(def != null && def.main() && def.startWhen() != null && !def.repeats() && def.chapter().equals(Chapters.TIDE.id()),
                        id + ": needs to be a main quest with start_when, chapter titan_tide");
            }
            for (String id : List.of(Ch2Ground.Q_DRIFT, Ch2Ground.Q_WADER, "daily_pool_sweep")) {
                check(QuestManager.quest(id) != null, id + " is not loaded");
            }
            // 二章の人 (岩鯨を止めた) にする。メイン「満ち引きの門」の噂の段のあいだは出ない
            progress.markPersonal(p.getUUID(), "stone_whale");
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch2Ground.Q_SALT) == null, "the chapter-2 trail steps appeared before the tide quest");
            Quests.start(p, "main_tide");
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch2Ground.Q_SALT) == null || "see".equals(stageId(p, "main_tide")), "the trail steps appeared during the rumor stage");
            if ("rumor".equals(stageId(p, "main_tide"))) {
                Quests.forceStage(p, "main_tide");
            }
            Quests.poll(p, false);
            for (String id : Ch2Ground.TRAILS) {
                check(QuestLog.of(p).entry(id) != null, id + " did not start after Isana's sea rumor");
            }
            // 塩の道: 標 5 (人ごとに一度ずつ) → 蔵の刻み + 番人
            check(SaltVault.atRoadStage(p), "salt_road should open on the road stage");
            for (int n = 1; n <= SaltVault.MARKS; n++) {
                SaltVault.seeMark(p, n, SaltVault.atRoadStage(p));
                SaltVault.seeMark(p, n, SaltVault.atRoadStage(p));
                Quests.poll(p, true);
                if (n < SaltVault.MARKS) {
                    check(SaltVault.atRoadStage(p), "mark " + n + " ended the road stage early (marks are counted twice?)");
                }
            }
            check("vault".equals(stageId(p, Ch2Ground.Q_SALT)), "five marks did not move salt_road to the vault stage (" + stageId(p, Ch2Ground.Q_SALT) + ")");
            Lore.unlock(p, Ch2Ground.LORE_VAULT, false);
            Quests.poll(p, true);
            check(!QuestLog.of(p).entry(Ch2Ground.Q_SALT).done, "the carving alone finished salt_road");
            Quests.signal(p, SiteSpawner.signal(SaltVault.SITE));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch2Ground.Q_SALT).done, "the carving and the warden did not finish salt_road");
            // 船と貝: 船 → 難破船の頁 + 貝
            p.getInventory().add(new ItemStack(Items.OAK_BOAT));
            Quests.poll(p, true);
            check("wreck".equals(stageId(p, Ch2Ground.Q_BOAT)), "a boat did not move ebb_boat to the wreck stage");
            Wreck.openChest(p);
            Tasks.flush();
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch2Ground.Q_BOAT).done, "the wreck page and the shell did not finish ebb_boat");
            check(PrepQuests.ready(p, 2) && Growth.get(p).discoveries.contains("prep_ready:2"), "the two trail steps did not raise ch2_ready");
            // 宿借りの行列: 筏の跡に着く
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch2Ground.Q_DRIFT) != null, "drift_trail did not start with ebb_boat");
            Ch2Ground.arriveRaft(p);
            Tasks.flush();
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch2Ground.Q_DRIFT) != null && QuestLog.of(p).entry(Ch2Ground.Q_DRIFT).done, "arriving at the raft did not finish drift_trail");
            check(Lore.state(p).has(Ch2Ground.LORE_RAFT), "the raft page was not recorded");
            // 語り部の報告: 村の記憶
            var st = jp.revati.skyabyss.npc.DialogueManager.dialogue(jp.revati.skyabyss.npc.NpcSetup.STORYTELLER_ID);
            check(st != null && st.nodes().containsKey("ms_tide_memory") && st.nodes().containsKey("ms_tide_verse")
                    && "ms_tide_memory".equals(st.nodes().get("ms_tide_back2").next()), "the storyteller's tide report does not lead to the village memory");
            if (st != null && st.nodes().containsKey("ms_tide_memory")) {
                jp.revati.skyabyss.quest.StoryContext ctx = jp.revati.skyabyss.quest.StoryContext.of(p);
                st.nodes().get("ms_tide_memory").actions().forEach(x -> x.run(ctx));
                check(Lore.state(p).has(Ch2Ground.LORE_MEMORY), "the village memory was not recorded");
            }
            // 職人の立つ所 (二章は筏の跡)
            check(ArtisanSpawner.spots().stream().anyMatch(s -> s.chapter() == 2 && s.siteId().equals(Ch2Ground.RAFT)), "the chapter-2 artisan has no spot");
            // 場所の登録 (跡地に置く)
            for (String id : Ch2Ground.SITES) {
                ChapterSites.Element e = ChapterSites.element(id);
                check(e != null && e.atGround() && e.chapter() == Chapters.TIDE, id + " is not registered on the chapter-2 grounds");
            }
            // 杭の読み (分): 10000 は引き潮まで 1000 tick = 1 分、12000 は引き潮の中で満ちるまで 2600 tick = 3 分
            check(Ch2Ground.minutesUntilTurn(10000) == 1 && Ch2Ground.minutesUntilTurn(12000) == 3, "stake minutes are wrong: "
                    + Ch2Ground.minutesUntilTurn(10000) + " " + Ch2Ground.minutesUntilTurn(12000));
            // 経験値の枠
            int fresh = ChapterXpBudget.newXp(QuestManager.all(), 2);
            check(fresh <= ChapterXpBudget.NEW_CAP[2], "chapter 2 new xp " + fresh + " exceeds " + ChapterXpBudget.NEW_CAP[2]);
            check(fresh >= 360 + SaltVault.XP, "chapter 2 new xp " + fresh + " does not count the trail steps and the vault");
            // 文 (日英)
            for (String key : List.of("quest.skyabyss.ch2.salt_road.road.marks", "quest.skyabyss.ch2.ebb_boat.wreck.page", "message.skyabyss.ch2.salt_mark.5",
                    "message.skyabyss.ch2.vault.door", "lore.skyabyss.ch2.why_they_rode.body", "item.skyabyss.ch2_salt_barrel",
                    SaltVault.SIGN + ".1", SaltVault.WALL_SIGN + ".2", Ch2Ground.STAKES_SIGN + ".4")) {
                check(SignFit.lang("ja_jp").containsKey(key) && SignFit.lang("en_us").containsKey(key), "missing ja/en text " + key);
            }
        } finally {
            clean.run();
        }
    }

    static String stageId(FakePlayer p, String quest) {
        QuestLog.Entry e = QuestLog.of(p).entry(quest);
        QuestDef def = QuestManager.quest(quest);
        if (e == null || def == null || e.done || e.stage >= def.stages().size()) {
            return e != null && e.done ? "done" : "";
        }
        return def.stages().get(e.stage).id();
    }
}
