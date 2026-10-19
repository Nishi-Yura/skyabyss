package jp.revati.skyabyss.story.ch1;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.guide.ChapterXpBudget;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.guide.SiteProgressData;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.guide.WayfarerNotes;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 一章の地上 (P6。docs/design/21 の 3.1〜3.2) の自己テスト。Ch1SelfTest の「ch1 sites」のあと (本物の世界に小要素が建ったあと) に流す。結果は SELFTEST ch1 p6 PASSED / FAILED
//   sites    新しい場所が全部建つ (石標 7・骨の集め場・割れ殻の洞)・拠点から 150 以内・鯨道の石標の道すじ (遠くなる順)・洞の部品が人のブロックを壊さない・
//            洞の部屋の敵の立ち位置が空気 (向きが合っている)・鳴り石と格子の印・荷車の傍らの道・歌の間 (人の建てた物には置かない・置く・戸が開く・壁画)
//   cave     洞の湧き台: 二つのグループが同時に入れる・敵が部屋に立つ・鳴り石の順 (誤りで戻る・低い順で格子が開く)・クリアの記録 (経験値・宝・合図)
//   prep     備えの頼み 3 本: 受けたとき覚書が渡る・集める → 話す・洞の合図・護り糸 → 鼓動守・三本で ch1_ready
//   descent  岩鯨の降下の条件 (日 4 の朝以降 + 備え 2 本、または日 6 以降。人ごと)、経験値の枠
public final class Ch1SitesSelfTest {
    private static final List<String> F = new ArrayList<>();

    private Ch1SitesSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch1_p6_" + suffix).getBytes());
        FakePlayer p = FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_p6_" + suffix));
        Growth.get(p).level = 30;
        return p;
    }

    private static final List<net.minecraft.world.level.ChunkPos> FORCED = new ArrayList<>();
    private static int loadWait;

    // 洞のまわりのチャンクを強制的に読み込む (敵の湧きに要る。あとで run が放す)
    public static void load(MinecraftServer server, FakePlayer base) {
        ServerLevel level = server.overworld();
        loadWait = 0;
        for (var c : ChapterSites.sitesFor(server, base.getUUID(), CleftCave.SITE)) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    var cp = new net.minecraft.world.level.ChunkPos((c.pos().getX() >> 4) + dx, (c.pos().getZ() >> 4) + dz);
                    level.setChunkForced(cp.x, cp.z, true);
                    FORCED.add(cp);
                }
            }
        }
    }

    public static boolean loaded(MinecraftServer server) {
        return ++loadWait > 60;
    }

    public static void run(MinecraftServer server, FakePlayer base) {
        F.clear();
        try {
            sites(server, base);
            cave(server, base);
            prep(server);
            descent(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 p6 crashed", e);
            F.add("crashed: " + e);
        } finally {
            for (var cp : FORCED) {
                server.overworld().setChunkForced(cp.x, cp.z, false);
            }
            FORCED.clear();
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 p6 PASSED (7 road cairns outward, bone cache, cleft shell cave with rooms/mechanism/clear, cart track, song chamber, "
                    + "3 prep quests to ch1_ready, whale descent gate, xp within the budget)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 p6 FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- sites
    private static void sites(MinecraftServer server, FakePlayer base) {
        ServerLevel level = server.overworld();
        BlockPos anchor = Ch1SelfTest.baseAt();
        double prev = 0;
        for (String id : Sites.WHALE_P6) {
            var list = ChapterSites.sitesFor(server, base.getUUID(), id);
            check(list.size() == 1 && list.get(0).built(), id + ": should be placed and built exactly once (" + list.size() + ")");
            if (list.isEmpty()) {
                continue;
            }
            BlockPos pos = list.get(0).pos();
            double d = anchor == null ? 0 : Math.hypot(pos.getX() - anchor.getX(), pos.getZ() - anchor.getZ());
            check(anchor == null || d <= 152, id + " is " + (int) d + " from the base");
            if (id.startsWith(Ch1Prep.CAIRN_PREFIX)) {
                // 道すじ: 番号が大きいほど (おおむね) 遠い。n 番目は 24 + 18 (n-1) から。ずらしても 10 は手前にこない
                int n = Integer.parseInt(id.substring(id.lastIndexOf('_') + 1));
                check(anchor == null || d >= 24 + 18 * (n - 1) - 12, id + " is only " + (int) d + " from the base (outward order)");
                // 積み石の部品: 丸石の柱がある
                check(near(level, pos, 3, Blocks.COBBLESTONE) || near(level, pos, 4, Blocks.STONE_BRICKS), id + ": no cairn/shrine blocks near " + pos);
            }
        }
        // 7 つ目は祠
        var seventh = ChapterSites.sitesFor(server, base.getUUID(), Sites.roadCairnId(Ch1Prep.CAIRNS));
        check(!seventh.isEmpty() && near(level, seventh.get(0).pos(), 4, Blocks.CHISELED_STONE_BRICKS), "the seventh cairn should be the shrine");
        // 骨の集め場: 骨のブロック
        var cache = ChapterSites.sitesFor(server, base.getUUID(), Ch1Prep.BONE_CACHE);
        check(!cache.isEmpty() && near(level, cache.get(0).pos(), 4, Blocks.BONE_BLOCK), "the bone cache has no bone blocks");
        // 荷車の傍らの道: 失せ物の荷車の隣に樽と金床、職人の印
        var cart = ChapterSites.sitesFor(server, base.getUUID(), Sites.LOST_CART);
        if (!cart.isEmpty()) {
            SiteData.Entry e = entry(server, Sites.LOST_CART, cart.get(0).pos());
            BlockPos artisan = e == null ? null : e.extra("artisan");
            check(artisan != null && near(level, artisan, 4, Blocks.ANVIL), "the cart track has no anvil beside the lost cart (artisan mark " + artisan + ")");
        }
        // 割れ殻の洞
        var caves = CleftCave.entries(server);
        SiteData.Entry cave = null;
        for (SiteData.Entry c : caves) {
            if (ChapterSites.sitesFor(server, base.getUUID(), CleftCave.SITE).stream().anyMatch(s -> s.pos().equals(c.pos))) {
                cave = c;
            }
        }
        check(cave != null, "the cleft shell cave is not built for the base player");
        if (cave == null) {
            return;
        }
        for (int n = 1; n <= CleftCave.PLATES; n++) {
            BlockPos plate = cave.extra("plate" + n);
            check(plate != null && level.getBlockState(plate).is(Blocks.CHISELED_STONE_BRICKS), "plate " + n + " is missing at " + plate);
            check(plate != null && level.getBlockState(plate.above()).is(Blocks.CANDLE) && level.getBlockState(plate.above()).getValue(net.minecraft.world.level.block.CandleBlock.CANDLES) == n,
                    "plate " + n + " should carry " + n + " candles");
        }
        int bars = 0;
        for (int k = 0; k < CleftCave.GATE_CELLS; k++) {
            BlockPos g = cave.extra("gate" + k);
            if (g != null && level.getBlockState(g).is(Blocks.IRON_BARS)) {
                bars++;
            }
        }
        check(bars == CleftCave.GATE_CELLS || CleftCave.gateOpen(level, cave), "the gate should be nine iron bars (found " + bars + ")");
        // 部屋の敵の立ち位置: 床の上の空気 2 つ (向きに合わせて回している)
        SiteSpawner.Def def = SiteSpawner.def(CleftCave.SITE);
        check(def != null, "the cave has no spawn pads");
        if (def != null) {
            CleftCave.origins(server);
            for (SiteSpawner.Pad pad : def.pads()) {
                BlockPos at = def.padAt(pad, cave.pos);
                check(level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && !level.getBlockState(at.below()).isAir(),
                        "pad " + pad.room() + " at " + at + " is not standing room on a floor (" + level.getBlockState(at) + " / below " + level.getBlockState(at.below()) + ")");
            }
        }
        // 口: 階段の最初の段が地面の高さに、最後の段が部屋に降りる (印 mouth と stand)
        // 人のブロックを壊さない: 部品を置き直す検査は ChapterSites の「ch1 sites」「chapter sites (conflict)」が見る。ここでは、洞の殻の外は触っていない (深さの下限)
        Blueprint bp = Blueprint.get("ch1_cleft_shell_cave");
        check(bp.minY >= -17 && bp.maxY <= 5, "the cave blueprint reaches y " + bp.minY + ".." + bp.maxY);
        songChamber(server, level);
    }

    private static boolean near(ServerLevel level, BlockPos pos, int r, net.minecraft.world.level.block.Block block) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -3, -r), pos.offset(r, 5, r))) {
            if (level.getBlockState(p).is(block)) {
                return true;
            }
        }
        return false;
    }

    private static SiteData.Entry entry(MinecraftServer server, String id, BlockPos pos) {
        for (SiteData.ChapterPlan plan : SiteData.get(server.overworld()).plans()) {
            for (SiteData.Entry e : plan.sites) {
                if (e.siteId.equals(id) && e.pos.equals(pos)) {
                    return e;
                }
            }
        }
        return null;
    }

    // 歌の間 (鐘の下)
    private static void songChamber(MinecraftServer server, ServerLevel level) {
        Ch1Data data = Ch1Data.get(server.overworld());
        int saved = data.chamber;
        LodgeNook.Frame frame = LodgeNook.frame(level);
        if (frame == null) {
            SkyAbyss.LOGGER.warn("[ch1 p6] the shelter is not loaded; the song chamber is not checked");
            return;
        }
        // 人が置いたブロックの上には置かない
        Blueprint bp = Blueprint.get(Ch1Prep.SONG_CHAMBER_BLUEPRINT);
        int[] lectern = bp.marks.get("lectern");
        BlockPos probe = SongChamber.at(frame, lectern[0], lectern[1], lectern[2]);
        var before = level.getBlockState(probe);
        boolean undone = SongChamber.natural(Blocks.OAK_PLANKS.defaultBlockState());
        check(!undone, "planks count as natural ground for the song chamber");
        level.setBlock(probe, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        check(!SongChamber.place(level, frame), "the song chamber was placed over a player's planks");
        check(level.getBlockState(probe).is(Blocks.OAK_PLANKS), "the song chamber replaced a player's block");
        level.setBlock(probe, before, 2);
        // 置く (まだ置いていなければ。置いてあれば自然な検査を通る = 部品そのもの)
        check(SongChamber.place(level, frame), "the song chamber could not be placed (or placed again)");
        int[] mural = bp.marks.get("mural");
        BlockPos m = SongChamber.at(frame, mural[0], mural[1], mural[2]);
        int amethyst = 0;
        for (BlockPos p : BlockPos.betweenClosed(m.offset(-5, 0, -5), m.offset(5, 7, 5))) {
            if (level.getBlockState(p).is(Blocks.AMETHYST_BLOCK)) {
                amethyst++;
            }
        }
        check(amethyst == 5, "the mural should show five pillars (found " + amethyst + " tops)");
        BlockPos door = SongChamber.door(frame);
        check(door != null && level.getBlockState(door).is(Blocks.STONE_BRICKS), "the sealed door should be one stone brick at the ground");
        data.chamber = 1;
        check(SongChamber.open(level) && level.getBlockState(door).is(Blocks.LADDER) && data.chamber == 2, "the song chamber door did not open into a ladder");
        check(!SongChamber.open(level), "the door opened twice");
        data.chamber = saved == 0 ? 2 : saved;
        data.setDirty();
        // 読む順: 壁画の高さは低い順
        check(Ch1Prep.BELL_ORDER.length == 5 && Ch1Prep.BELL_ORDER[0] == 1 && Ch1Prep.BELL_ORDER[4] == 5, "the bell order is not the song order");
    }

    // ---------------------------------------------------------------- cave
    private static void cave(MinecraftServer server, FakePlayer base) {
        ServerLevel level = server.overworld();
        SiteProgressData progress = SiteProgressData.get(server);
        SiteData.Entry cave = null;
        for (SiteData.Entry c : CleftCave.entries(server)) {
            if (ChapterSites.sitesFor(server, base.getUUID(), CleftCave.SITE).stream().anyMatch(s -> s.pos().equals(c.pos))) {
                cave = c;
            }
        }
        if (cave == null) {
            return;
        }
        SiteSpawner.Def def = SiteSpawner.def(CleftCave.SITE);
        if (def == null) {
            return;
        }
        FakePlayer a = fake(server, "cave_a");
        FakePlayer b = fake(server, "cave_b");
        BlockPos origin = cave.pos;
        try {
            progress.forget(a.getUUID(), CleftCave.SITE);
            progress.forget(b.getUUID(), CleftCave.SITE);
            progress.forgetTreasure(a.getUUID(), CleftCave.TREASURE);
            progress.forgetTreasure(b.getUUID(), CleftCave.TREASURE);
            CleftCave.resetProgress();
            a.setPos(origin.getX() + 0.5, origin.getY() - 15, origin.getZ() + 0.5);
            b.setPos(origin.getX() + 3.5, origin.getY() - 15, origin.getZ() + 0.5);
            // 洞の中にいるつもり (向きのせいで部屋の中とは限らないので、基点の近く = 48 以内)
            long now = level.getGameTime();
            int total = def.pads().stream().mapToInt(p -> p.spawns().stream().mapToInt(SiteSpawner.Spawn::count).sum()).sum();
            Quests.start(a, Ch1Prep.Q_BLADE);
            SiteSpawner.tick(level, List.of(a, b), now);
            // a と b は別々のグループ (パーティなし)
            UUID ga = Groups.keyOf(a);
            UUID gb = Groups.keyOf(b);
            check(!ga.equals(gb), "two lone players should be two groups");
            int live = SiteSpawner.liveCount(CleftCave.SITE, origin, ga) + SiteSpawner.pendingCount(CleftCave.SITE, origin, ga);
            check(live == total, "group A's cave should hold " + total + " enemies (" + live + ")");
            check(SiteSpawner.liveCount(CleftCave.SITE, origin, gb) + SiteSpawner.pendingCount(CleftCave.SITE, origin, gb) == total, "group B did not get its own cave at the same time");
            List<Entity> mine = new ArrayList<>();
            for (UUID id : SiteSpawner.liveIds(CleftCave.SITE, origin, ga)) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    mine.add(e);
                }
            }
            check(!mine.isEmpty() && mine.stream().allMatch(e -> e instanceof GroupOwned o && ga.equals(o.groupId())), "cave enemies are not marked with their group");
            // 敵は部屋の湧き台の近くにいる
            boolean placed = true;
            for (Entity e : mine) {
                boolean ok = false;
                for (SiteSpawner.Pad pad : def.pads()) {
                    BlockPos at = def.padAt(pad, origin);
                    if (e.distanceToSqr(at.getX() + 0.5, at.getY(), at.getZ() + 0.5) < 9) {
                        ok = true;
                    }
                }
                placed &= ok;
            }
            check(placed, "a cave enemy is not standing at a room's spawn pad");

            // 鳴り石: 誤り → 戻る、1 → 2 → 3 で格子が開く。進みはグループごと
            check(!CleftCave.gateOpen(level, cave), "the gate should start closed");
            var r = CleftCave.tap(a, level, cave, 2);
            check(r.result() == SongStones.Result.WRONG && CleftCave.progress(ga, cave) == 0, "tapping plate 2 first should fail");
            CleftCave.tap(a, level, cave, 1);
            CleftCave.tap(a, level, cave, 3);
            check(CleftCave.progress(ga, cave) == 0, "tapping out of order should reset");
            CleftCave.tap(a, level, cave, 1);
            CleftCave.tap(b, level, cave, 1);
            CleftCave.tap(a, level, cave, 2);
            check(CleftCave.progress(ga, cave) == 2 && CleftCave.progress(gb, cave) == 1, "plate progress is not per group");
            CleftCave.tap(a, level, cave, 3);
            Tasks.flush();
            check(CleftCave.gateOpen(level, cave), "the gate did not open after plates 1, 2, 3");
            check(CleftCave.tap(b, level, cave, 1).result() == SongStones.Result.SILENT, "a plate should be silent once the gate is open");

            // クリア: a のグループだけ全部倒す → 記録・経験値・宝 (護り糸)・合図 (頼みが洞の段を終える)
            int xpBefore = Growth.get(a).xp;
            for (UUID id : SiteSpawner.liveIds(CleftCave.SITE, origin, ga)) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            SiteSpawner.tick(level, List.of(a), now + 40);
            check(progress.cleared(a.getUUID(), CleftCave.SITE) && !progress.cleared(b.getUUID(), CleftCave.SITE), "the clear was not recorded for exactly group A");
            check(a.getInventory().countItem(GuideSetup.ROCK_SHELL_THREAD.get()) == 1, "the first clear should hand over one ward thread");
            check(a.getInventory().countItem(GuideSetup.ROCK_SHELL_SCRAP.get()) >= 4, "the first clear should hand over shell scraps");
            check(Growth.get(a).discoveries.contains("site:" + CleftCave.SITE) && Growth.get(a).xp >= xpBefore, "the clear xp was not granted");
            QuestLog.Entry e = QuestLog.of(a).entry(Ch1Prep.Q_BLADE);
            check(e != null && e.stage >= 1, "the clear signal did not finish the cave stage of prep_blade");
            check(SiteSpawner.liveCount(CleftCave.SITE, origin, gb) + SiteSpawner.pendingCount(CleftCave.SITE, origin, gb) > 0, "clearing group A changed group B's cave");
        } finally {
            SiteSpawner.clearAll(level);
            for (FakePlayer p : List.of(a, b)) {
                progress.forget(p.getUUID(), CleftCave.SITE);
                progress.forgetTreasure(p.getUUID(), CleftCave.TREASURE);
                Quests.forgetAll(p);
                Growth.get(p).discoveries.clear();
                p.getInventory().clearContent();
            }
            CleftCave.resetProgress();
        }
    }

    // ---------------------------------------------------------------- prep
    private static void prep(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer p = fake(server, "prep");
        FakePlayer q = fake(server, "prep_q");
        try {
            Quests.forgetAll(p);
            Quests.forgetAll(q);
            Growth.get(p).discoveries.clear();
            p.getInventory().clearContent();
            check(PrepQuests.defined(1), "the three chapter-1 prep quests are not all defined (" + QuestManager.loadErrors() + ")");
            check(QuestManager.loadErrors().stream().noneMatch(s -> s.contains("prep")), "prep quests were flagged: " + QuestManager.loadErrors());
            // 受けたとき覚書 (一章) が渡る
            check(!WayfarerNotes.has(p, 1), "the player already has the notes");
            Quests.start(p, Ch1Prep.Q_GEAR);
            check(WayfarerNotes.has(p, 1), "starting prep_gear did not hand over the chapter-1 notes");
            Quests.start(p, Ch1Prep.Q_BLADE);
            Quests.start(p, Ch1Prep.Q_PULSE);
            check(PrepQuests.done(p, 1) == 0 && !PrepQuests.ready(p, 1), "nothing is done yet");

            // 身ごしらえ: 材料を集める → 語り部に頼む
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_GEAR).stage == 0, "prep_gear moved on without the materials");
            p.getInventory().add(new ItemStack(Items.IRON_INGOT, 8));
            p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 12));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_GEAR).stage == 1, "prep_gear did not reach the weave stage with 8 iron and 12 shell scraps");
            Quests.onTalk(p, "skyabyss:storyteller", "ch1p_gear_weave");
            check(QuestLog.of(p).entry(Ch1Prep.Q_GEAR).done, "talking did not finish prep_gear");
            // もう一つの出口: 旅装を作った合図 (P5)
            Quests.start(q, Ch1Prep.Q_GEAR);
            q.getInventory().add(new ItemStack(Items.IRON_INGOT, 8));
            q.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 12));
            Quests.poll(q, true);
            Quests.signal(q, Ch1Prep.SIGNAL_GEAR_MADE);
            check(QuestLog.of(q).entry(Ch1Prep.Q_GEAR).done, "the gear-made signal did not finish prep_gear");

            // 刃を研ぐ: 洞の合図 → 話す
            Quests.signal(p, SiteSpawner.signal(Ch1Prep.CLEFT_CAVE));
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).stage == 1, "the cave signal did not finish the cave stage");
            Quests.onTalk(p, "skyabyss:storyteller", "ch1p_blade_report");
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).done, "reporting did not finish prep_blade");
            check(Lore.state(p).has(Ch1Prep.CLEFT_CAVE_LORE), "prep_blade did not record the cave lore");
            check(PrepQuests.done(p, 1) == 2 && !PrepQuests.ready(p, 1), "two prep quests are done, three are not");

            // 鼓動を聞く: 護り糸 → 編んでもらう → 鼓動守
            p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_THREAD.get(), 1));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_PULSE).stage == 1, "prep_pulse did not reach the weave stage with a ward thread");
            Quests.onTalk(p, "skyabyss:storyteller", "ch1p_pulse_weave");
            check(QuestLog.of(p).entry(Ch1Prep.Q_PULSE).done, "weaving did not finish prep_pulse");
            check(p.getInventory().countItem(GuideSetup.HEARTBEAT_WARD_STONE_WHALE.get()) == 1, "prep_pulse did not give the Stone Whale heartbeat ward");
            check(PrepQuests.done(p, 1) == 3 && PrepQuests.ready(p, 1), "three prep quests should raise ch1_ready");
            check(Growth.get(p).discoveries.contains("prep_ready:1"), "the ch1_ready signal was not sent once");
            // 開始の条件: 一章の人・イサナと会った・日 2 以降
            for (String id : List.of(Ch1Prep.Q_GEAR, Ch1Prep.Q_BLADE, Ch1Prep.Q_PULSE)) {
                var def = QuestManager.quest(id);
                check(def != null && def.startWhen() != null && !def.repeats() && !def.board() && def.chapter().equals(Chapters.WHALE.id()),
                        id + ": needs start_when, chapter titan_whale, no repeat or board");
            }
        } finally {
            Quests.forgetAll(p);
            Quests.forgetAll(q);
            Growth.get(p).discoveries.clear();
            Growth.get(q).discoveries.clear();
            p.getInventory().clearContent();
            q.getInventory().clearContent();
        }
    }

    // ---------------------------------------------------------------- descent・経験値の枠
    private static void descent(MinecraftServer server) {
        // 式
        check(!Ch1Descent.allows(2, 3), "day 3 should not lower the chain, whatever the prep");
        check(!Ch1Descent.allows(3, 1), "day 4 morning with one prep quest should not lower the chain");
        check(Ch1Descent.allows(3, 2), "day 4 morning with two prep quests should lower the chain");
        check(!Ch1Descent.allows(4, 0), "day 5 with no prep should not lower the chain");
        check(Ch1Descent.allows(5, 0) && Ch1Descent.allows(9, 0), "day 6 on should lower the chain with no prep");
        // 降りる速さ (first_hour_log B-5): 遠いほど速く (上限 0.45)、近づくと 0.12。単調で、巡航 (0.2) の 2 倍強まで
        check(jp.revati.skyabyss.titan.StoneWhaleEntity.approachSpeed(700) == 0.45f && jp.revati.skyabyss.titan.StoneWhaleEntity.approachSpeed(20) == 0.12f, "approach speed bounds");
        float prevSpeed = 0f;
        for (int d = 0; d <= 700; d += 10) {
            float v = jp.revati.skyabyss.titan.StoneWhaleEntity.approachSpeed(d);
            check(v >= prevSpeed && v >= 0.12f && v <= 0.45f, "approach speed is not monotonic at " + d);
            prevSpeed = v;
        }
        // 640 ブロックの旅 (見守りが岩鯨を人から保つ限界) が、2 分より十分短い (速度は目標の速さへ 1 tick 5% ずつなじむので、平均は速さの 85% として)
        double ticks = 0;
        for (double dist = 700; dist > 6; ) {
            double v = Math.min(jp.revati.skyabyss.titan.StoneWhaleEntity.approachSpeed(dist), dist * 0.02);
            dist -= Math.max(0.02, v * 0.85);
            ticks++;
        }
        check(ticks < 2400, "a 700 block approach would take " + (int) ticks + " ticks (over 2 minutes)");
        check(jp.revati.skyabyss.titan.StoneWhaleEntity.DESCENT_RANGE >= 640 && jp.revati.skyabyss.titan.StoneWhaleEntity.DESCENT_RECHECK <= 200, "the descent search range or recheck interval is too slow");
        check(jp.revati.skyabyss.quest.ObjectiveRules.waitVariant(0).equals(".wait.prep") && jp.revati.skyabyss.quest.ObjectiveRules.waitVariant(1).equals(".wait.prep")
                && jp.revati.skyabyss.quest.ObjectiveRules.waitVariant(2).equals(".wait.lamps") && jp.revati.skyabyss.quest.ObjectiveRules.waitVariant(3).equals(".wait.lamps"),
                "the chain-wait band variant should switch at two prep quests");
        check(jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(0.4, "S").isEmpty() && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(4.9, "S").isEmpty()
                && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(5, "S").equals("10m S") && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(227, "NE").equals("230m NE"),
                "the distance label should hide under 5 m and round to 10 m");
        // 人ごと (差し替えた章と日で)
        ServerLevel level = server.overworld();
        FakePlayer a = fake(server, "descent_a");
        FakePlayer b = fake(server, "descent_b");
        var chapterBefore = Ch1Descent.chapterOf;
        var dayBefore = Ch1Descent.dayOf;
        long[] day = {2};
        try {
            Ch1Descent.chapterOf = p -> Chapters.WHALE;
            Ch1Descent.dayOf = s -> day[0];
            Quests.forgetAll(a);
            Quests.forgetAll(b);
            a.setPos(0, 200, 0);
            b.setPos(0, 200, 0);
            // a は二本、b は 0 本
            finish(a, 2);
            day[0] = 2;
            check(!Ch1Descent.qualifies(a) && !Ch1Descent.qualifies(b), "nobody qualifies on day 3");
            day[0] = 3;
            check(Ch1Descent.qualifies(a) && !Ch1Descent.qualifies(b), "on day 4 morning only the player with two prep quests qualifies");
            day[0] = 5;
            check(Ch1Descent.qualifies(a) && Ch1Descent.qualifies(b), "everybody qualifies from day 6");
            // 章が違えば条件なし (一章を過ぎた人)
            day[0] = 2;
            Ch1Descent.chapterOf = p -> Chapters.TIDE;
            check(Ch1Descent.qualifies(b), "a player past chapter 1 should not be gated");
            Ch1Descent.chapterOf = p -> Chapters.WHALE;
            // 岩鯨の近くで条件を満たす人を選ぶ
            day[0] = 3;
            var picked = Ch1Descent.pick(level, 0, 0, 100);
            check(picked == null || picked.getUUID().equals(a.getUUID()) || picked != b, "the whale picked a player who does not qualify");
            day[0] = 2;
            check(Ch1Descent.pick(level, 0, 0, 100) == null || level.players().stream().noneMatch(p -> p.getUUID().equals(a.getUUID())),
                    "the whale picked somebody on day 3");
        } finally {
            Ch1Descent.chapterOf = chapterBefore;
            Ch1Descent.dayOf = dayBefore;
            Quests.forgetAll(a);
            Quests.forgetAll(b);
            Growth.get(a).discoveries.clear();
            Growth.get(b).discoveries.clear();
            a.getInventory().clearContent();
            b.getInventory().clearContent();
        }
        // 経験値の枠: 一章の新しい分 (備え 300 + 洞 40) は 400 に収まる
        int fresh = ChapterXpBudget.newXp(QuestManager.all(), 1);
        check(fresh <= ChapterXpBudget.NEW_CAP[1], "chapter 1 new xp " + fresh + " exceeds " + ChapterXpBudget.NEW_CAP[1]);
        check(fresh >= 300 + CleftCave.XP, "chapter 1 new xp " + fresh + " does not count the prep quests and the cave");
        check(ChapterXpBudget.overruns(QuestManager.all(), 1).isEmpty(), "chapter 1 is over its xp budget: " + ChapterXpBudget.overruns(QuestManager.all(), 1));
        int lore = Ch1Prep.LORE_XP.values().stream().mapToInt(Integer::intValue).sum();
        check(ChapterXpBudget.EXISTING[1] + fresh + lore <= (int) (ChapterXpBudget.LEVEL_XP[1] * ChapterXpBudget.MAX_RATIO),
                "chapter 1 small-element xp with the new lore (" + (ChapterXpBudget.EXISTING[1] + fresh + lore) + ") passes the ratio");
    }

    // p に備えの頼みを n 本終えさせる (gear、blade の順)
    private static void finish(FakePlayer p, int n) {
        Quests.start(p, Ch1Prep.Q_GEAR);
        p.getInventory().add(new ItemStack(Items.IRON_INGOT, 8));
        p.getInventory().add(new ItemStack(GuideSetup.ROCK_SHELL_SCRAP.get(), 12));
        Quests.poll(p, true);
        Quests.onTalk(p, "skyabyss:storyteller", "ch1p_gear_weave");
        if (n >= 2) {
            Quests.start(p, Ch1Prep.Q_BLADE);
            Quests.signal(p, SiteSpawner.signal(Ch1Prep.CLEFT_CAVE));
            Quests.onTalk(p, "skyabyss:storyteller", "ch1p_blade_report");
        }
    }
}
