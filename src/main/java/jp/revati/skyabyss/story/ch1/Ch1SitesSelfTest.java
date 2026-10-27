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
//   sites    新しい場所が全部建つ (道の祠・骨の集め場・割れ殻の洞)・拠点から 150 以内・洞の部品が人のブロックを壊さない・
//            洞の部屋の敵の立ち位置が空気 (向きが合っている)・鳴り石と格子の印・荷車の傍らの道・歌の間 (人の建てた物には置かない・置く・戸が開く・壁画)
//   cave     洞の湧き台: 二つのグループが同時に入れる・敵が部屋に立つ・鳴り石の順 (誤りで戻る・低い順で格子が開く)・クリアの記録 (経験値・宝・合図)
//   pit      骨の坑 (34 の 4.2.1): 碑 2 枚の文・荷の樽・冷えの戸 9 マス・鉄鉱石の筋・水の坑道・出口の梯子・湧き台の立ち位置、
//            冷えの戸 (鉄なしでは開かない・鉄の兜で開く)、湧き台 (グループの分だけ湧く → 倒しきるとクリアの記録・経験値・粗鉄)
//   trails   支度の段 3 本 (34 の 4.2): イサナの鯨の話のあとに三本とも出る・覚書が渡る・鉄の跡 (荷の頁 → 鉄の防具 4 部位を着る)・
//            折れた剣 (洞の台座の頁 → 職人 → 鉄の剣 + 砥石)・盾と糧 (語り部の話 → 盾 + 固焼き 8)・三本で ch1_ready と鯨追いの印 (一度)
//   descent  岩鯨の降下の条件 (見たら降りる。場面の間は待つ。日・支度の本数は見ない。人ごと)、見つけてから鎖までの長さ、経験値の枠
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
        List<ChapterSites.Site> around = new ArrayList<>(ChapterSites.sitesFor(server, base.getUUID(), CleftCave.SITE));
        around.addAll(ChapterSites.sitesFor(server, base.getUUID(), BonePit.SITE));
        for (var c : around) {
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
            pit(server, base);
            trails(server);
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
            SkyAbyss.LOGGER.info("SELFTEST ch1 p6 PASSED (road shrine, bone cache, cleft shell cave with rooms/mechanism/clear, cart track, song chamber, "
                    + "bone pit with tablets/pack/cold door/clear, 3 trail steps to ch1_ready and the whale chaser's mark, whale descent gate, xp within the budget)");
        } else {
            F.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 p6 FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- sites
    private static void sites(MinecraftServer server, FakePlayer base) {
        ServerLevel level = server.overworld();
        BlockPos base0 = Ch1SelfTest.baseAt();
        BlockPos capital = jp.revati.skyabyss.opening.OpeningSites.shelter(server).orElse(null);
        for (String id : Sites.WHALE_P6) {
            var list = ChapterSites.sitesFor(server, base.getUUID(), id);
            check(list.size() == 1 && list.get(0).built(), id + ": should be placed and built exactly once (" + list.size() + ")");
            if (list.isEmpty()) {
                continue;
            }
            BlockPos pos = list.get(0).pos();
            // 跡地の要素は一章の跡地の中心から 150 以内。道の祠 (anchor = null) は都から跡地への道の上
            BlockPos anchor = base0 == null ? null : ChapterSites.anchorFor(server, id, base0);
            double d = anchor == null ? 0 : Math.hypot(pos.getX() - anchor.getX(), pos.getZ() - anchor.getZ());
            check(anchor == null || d <= 152, id + " is " + (int) d + " from its anchor " + anchor);
            if (id.equals(Ch1Prep.ROAD_SHRINE_ID)) {
                // 道の終わりの祠: 石レンガの祠。跡地がない世界では拠点から外へ
                check(near(level, pos, 4, Blocks.CHISELED_STONE_BRICKS), id + ": no shrine blocks near " + pos);
                if (anchor == null && capital != null) {
                    check(Math.hypot(pos.getX() - capital.getX(), pos.getZ() - capital.getZ()) > 100, id + " is too near the capital");
                }
            }
        }
        // 骨の集め場: 骨のブロック
        var cache = ChapterSites.sitesFor(server, base.getUUID(), Ch1Prep.BONE_CACHE);
        check(!cache.isEmpty() && near(level, cache.get(0).pos(), 4, Blocks.BONE_BLOCK), "the bone cache has no bone blocks");
        // 荷車の傍らの道: 失せ物の荷車の隣に樽と金床、職人の印
        var cart = ChapterSites.sitesFor(server, base.getUUID(), Sites.LOST_CART);
        if (!cart.isEmpty()) {
            SiteData.Entry e = entry(server, Sites.LOST_CART, cart.get(0).pos());
            BlockPos artisan = e == null ? null : e.extra("artisan");
            check(artisan != null && near(level, artisan, 4, Blocks.ANVIL), "the cart track has no anvil beside the lost cart (artisan mark " + artisan + ")");
            // 職人 (P4): 印のそばに章 1 の装いで立てる (読み込み前なら立てない = 確かめない)
            if (artisan != null) {
                jp.revati.skyabyss.npc.NpcEntity npc = jp.revati.skyabyss.npc.ArtisanSpawner.ensure(level, artisan, 1);
                if (npc != null) {
                    check(npc.variant() == 1 && npc.blockPosition().distSqr(artisan) <= 36, "the artisan did not stand at the cart track mark " + artisan + " (stood at " + npc.blockPosition() + ")");
                    npc.discard();
                } else {
                    SkyAbyss.LOGGER.warn("[selftest] the artisan was not stood at {} (entities not loaded there, or already standing)", artisan);
                }
            }
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
            Lore.lock(a, Ch1Prep.PAGE_BLADE);
            Lore.lock(a, Ch1Prep.CLEFT_CAVE_LORE);
            Quests.start(a, Ch1Prep.Q_BLADE);
            // 最初の段 (職人に刃のことを聞く) を済ませておく
            Quests.onTalk(a, "skyabyss:artisan", "ch1t_blade_tell");
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
            CleftCave.resetForTest(level, cave);
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
            check(jp.revati.skyabyss.pouch.Pouch.countAll(a, GuideSetup.ROCK_SHELL_THREAD.get()) == 1, "the first clear should hand over one ward thread");
            check(jp.revati.skyabyss.pouch.Pouch.countAll(a, GuideSetup.ROCK_SHELL_SCRAP.get()) >= 4, "the first clear should hand over shell scraps");
            check(Growth.get(a).discoveries.contains("site:" + CleftCave.SITE) && Growth.get(a).xp >= xpBefore, "the clear xp was not granted");
            // 台座の折れた剣: クリアの場の人に頁 (少し遅れて記す) → 折れた剣の洞の段が済む
            Tasks.flush();
            Quests.poll(a, true);
            check(Lore.state(a).has(Ch1Prep.PAGE_BLADE) && Lore.state(a).has(Ch1Prep.CLEFT_CAVE_LORE), "clearing the cave did not record the broken sword page and the cave lore");
            check(!Lore.state(b).has(Ch1Prep.PAGE_BLADE), "the broken sword page leaked to the other group");
            QuestLog.Entry e = QuestLog.of(a).entry(Ch1Prep.Q_BLADE);
            check(e != null && e.stage >= 2, "the broken sword page did not finish the cave stage of trail_blade");
            check(SiteSpawner.liveCount(CleftCave.SITE, origin, gb) + SiteSpawner.pendingCount(CleftCave.SITE, origin, gb) > 0, "clearing group A changed group B's cave");
        } finally {
            SiteSpawner.clearAll(level);
            for (FakePlayer p : List.of(a, b)) {
                progress.forget(p.getUUID(), CleftCave.SITE);
                progress.forgetTreasure(p.getUUID(), CleftCave.TREASURE);
                Lore.lock(p, Ch1Prep.PAGE_BLADE);
                Lore.lock(p, Ch1Prep.CLEFT_CAVE_LORE);
                Quests.forgetAll(p);
                Growth.get(p).discoveries.clear();
                p.getInventory().clearContent();
            }
            CleftCave.resetProgress();
        }
    }

    // ---------------------------------------------------------------- pit (骨の坑)
    private static void pit(MinecraftServer server, FakePlayer base) {
        ServerLevel level = server.overworld();
        SiteData.Entry pit = null;
        for (SiteData.Entry c : BonePit.entries(server)) {
            if (ChapterSites.sitesFor(server, base.getUUID(), BonePit.SITE).stream().anyMatch(s -> s.pos().equals(c.pos))) {
                pit = c;
            }
        }
        check(pit != null, "the bone pit is not built for the base player");
        if (pit == null) {
            return;
        }
        // 碑 2 枚 (坑口・冷えの戸の奥) と文
        for (String[] t : new String[][] {{"tablet", BonePit.SIGN}, {"cold_tablet", BonePit.COLD_SIGN}}) {
            BlockPos at = pit.extra(t[0]);
            check(at != null && level.getBlockEntity(at) instanceof jp.revati.skyabyss.tablet.TabletBlockEntity tb && tb.key().equals(t[1]),
                    "the " + t[0] + " of the bone pit does not carry " + t[1] + " (at " + at + ")");
        }
        BlockPos packAt = pit.extra("pack");
        check(packAt != null && level.getBlockState(packAt).is(Blocks.BARREL), "the earlier traveller's pack (barrel) is missing at " + packAt);
        int door = 0;
        for (int k = 0; k < BonePit.COLD_CELLS; k++) {
            BlockPos c = pit.extra("cold" + k);
            if (c != null && level.getBlockState(c).is(Blocks.POLISHED_DEEPSLATE)) {
                door++;
            }
        }
        check(door == BonePit.COLD_CELLS || BonePit.doorOpen(level, pit), "the cold door should be nine stone cells (found " + door + ")");
        // 鉄鉱石の筋・水の坑道・出口の梯子 (坑の箱の中を数える。向きは回っているので基点のまわりの広い箱)
        int ore = 0;
        int water = 0;
        int ladder = 0;
        BlockPos o = pit.pos;
        for (BlockPos q : BlockPos.betweenClosed(o.offset(-38, -18, -38), o.offset(38, 1, 38))) {
            var st = level.getBlockState(q);
            if (st.is(Blocks.IRON_ORE)) {
                ore++;
            } else if (st.is(Blocks.WATER)) {
                water++;
            } else if (st.is(Blocks.LADDER)) {
                ladder++;
            }
        }
        check(ore >= BonePit.IRON_VEINS - 2, "the pit walls should carry about " + BonePit.IRON_VEINS + " iron ore (found " + ore + ")");
        check(water >= 100, "the flooded passage is missing (" + water + " water)");
        check(ladder >= 10, "the exit shaft ladder is missing (" + ladder + ")");
        // 湧き台の立ち位置: 床の上の空気 2 つ
        SiteSpawner.Def def = SiteSpawner.def(BonePit.SITE);
        check(def != null, "the bone pit has no spawn pads");
        if (def == null) {
            return;
        }
        BonePit.origins(server);
        for (SiteSpawner.Pad pad : def.pads()) {
            BlockPos at = def.padAt(pad, pit.pos);
            check(level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir() && !level.getBlockState(at.below()).isAir(),
                    "pit pad " + pad.room() + " at " + at + " is not standing room on a floor (" + level.getBlockState(at) + " / below " + level.getBlockState(at.below()) + ")");
        }
        check(def.lastRoom().equals(BonePit.ROOM_PACK), "the pack room should be the room that comes back on a revisit");
        Blueprint bp = Blueprint.get(BonePit.SITE);
        check(bp.minY >= -18 && bp.maxY <= 6, "the pit blueprint reaches y " + bp.minY + ".." + bp.maxY);

        FakePlayer a = fake(server, "pit_a");
        SiteProgressData progress = SiteProgressData.get(server);
        try {
            progress.forget(a.getUUID(), BonePit.SITE);
            progress.forgetTreasure(a.getUUID(), BonePit.TREASURE);
            a.getInventory().clearContent();
            // 冷えの戸: 鉄なしでは開かない、鉄の兜一つで開く (世界に一つ。前の実行で開いていれば確かめない)
            if (!BonePit.doorOpen(level, pit)) {
                check(!BonePit.touchDoor(a, level, pit) && !BonePit.doorOpen(level, pit), "the cold door opened for a player without iron");
                a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_HELMET.get()));
                check(BonePit.ironWorn(a) == 1 && BonePit.touchDoor(a, level, pit) && BonePit.doorOpen(level, pit), "the cold door did not open for a player wearing an iron helmet");
                a.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
            }
            // 湧き台: 一人のグループの分だけ湧き、倒しきるとクリア (経験値・粗鉄 4)
            a.setPos(pit.pos.getX() + 0.5, pit.pos.getY() - 12, pit.pos.getZ() + 0.5);
            long now = level.getGameTime();
            int total = def.pads().stream().mapToInt(p -> p.spawns().stream().mapToInt(SiteSpawner.Spawn::count).sum()).sum();
            SiteSpawner.tick(level, List.of(a), now);
            UUID ga = Groups.keyOf(a);
            int live = SiteSpawner.liveCount(BonePit.SITE, pit.pos, ga) + SiteSpawner.pendingCount(BonePit.SITE, pit.pos, ga);
            check(live == total, "the pit should hold " + total + " enemies for the group (" + live + ")");
            for (UUID id : SiteSpawner.liveIds(BonePit.SITE, pit.pos, ga)) {
                Entity e = level.getEntity(id);
                if (e != null) {
                    e.discard();
                }
            }
            SiteSpawner.tick(level, List.of(a), now + 40);
            check(progress.cleared(a.getUUID(), BonePit.SITE), "clearing the pit was not recorded");
            check(Growth.get(a).discoveries.contains("site:" + BonePit.SITE), "the pit clear xp was not granted");
            check(jp.revati.skyabyss.quest.QuestItems.count(a, "skyabyss:iron_lump", null) >= 4, "the first pit clear should hand over raw iron");
        } finally {
            SiteSpawner.clearAll(level);
            progress.forget(a.getUUID(), BonePit.SITE);
            progress.forgetTreasure(a.getUUID(), BonePit.TREASURE);
            Growth.get(a).discoveries.clear();
            a.getInventory().clearContent();
        }
    }

    // ---------------------------------------------------------------- trails (支度の段 3 本)
    private static void trails(MinecraftServer server) {
        ServerLevel level = server.overworld();
        FakePlayer p = fake(server, "trail");
        List<String> pages = List.of(Ch1Prep.PAGE_IRON, Ch1Prep.PAGE_BLADE, Ch1Prep.PAGE_SHIELD);
        Runnable clean = () -> {
            Quests.forgetAll(p);
            Growth.get(p).discoveries.clear();
            p.getInventory().clearContent();
            for (var slot : List.of(net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
                    net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET)) {
                p.setItemSlot(slot, ItemStack.EMPTY);
            }
            pages.forEach(id -> Lore.lock(p, id));
            SiteProgressData.get(server).forgetTreasure(p.getUUID(), BonePit.PACK);
            jp.revati.skyabyss.npc.NpcBonds.of(p).bond("skyabyss:whale_chaser").flags().clear();
        };
        try {
            clean.run();
            check(PrepQuests.defined(1) && PrepQuests.ids(1).equals(Ch1Prep.TRAILS), "the three chapter-1 trail steps are not all defined (" + QuestManager.loadErrors() + ")");
            check(QuestManager.loadErrors().stream().noneMatch(s -> s.contains("prep")), "trail steps were flagged: " + QuestManager.loadErrors());
            for (String id : Ch1Prep.TRAILS) {
                var def = QuestManager.quest(id);
                check(def != null && def.main() && def.startWhen() != null && !def.repeats() && !def.board() && def.chapter().equals(Chapters.WHALE.id()),
                        id + ": needs to be a main quest with start_when, chapter titan_whale, no repeat or board");
            }
            check(QuestManager.quest("ch1/prep_gear") == null && QuestManager.quest("ch1/prep_blade") == null && QuestManager.quest("ch1/prep_pulse") == null,
                    "the old prep quests should be absorbed");
            // 開始: イサナの鯨の話 (印 c_whale_heard) のあと、鉄の跡だけが出る (一本ずつ順に)。覚書も渡る
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch1Prep.Q_IRON) == null, "the trail steps appeared before Isana's whale talk");
            jp.revati.skyabyss.quest.StoryContext ctx = jp.revati.skyabyss.quest.StoryContext.of(p);
            jp.revati.skyabyss.quest.Action.parseList(com.google.gson.JsonParser.parseString(
                    "{\"type\":\"flag\",\"flag\":\"c_whale_heard\",\"npc\":\"skyabyss:whale_chaser\"}"), new ArrayList<>(), "t").get(0).run(ctx);
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch1Prep.Q_IRON) != null, "the iron trail did not start after Isana's whale talk");
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE) == null && QuestLog.of(p).entry(Ch1Prep.Q_SHIELD) == null,
                    "the blade and shield trails should wait for the iron trail");
            check(WayfarerNotes.has(p, 1), "starting the trail steps did not hand over the chapter-1 notes");

            // 鉄の跡: 骨の坑の荷 (人ごとに一度、鉄 2・松明 8・頁) → 鉄の防具 4 部位を着る
            check(BonePit.openPack(p, level, null), "the pack did not open the first time");
            check(!BonePit.openPack(p, level, null), "the pack gave its contents twice");
            Tasks.flush();
            check(Lore.state(p).has(Ch1Prep.PAGE_IRON), "the pack did not record the page");
            check(jp.revati.skyabyss.quest.QuestItems.count(p, "skyabyss:iron_lump", null) == 2 && jp.revati.skyabyss.quest.QuestItems.count(p, "minecraft:torch", null) == 8,
                    "the pack should hold two iron ingots and eight torches");
            Quests.poll(p, true);
            QuestLog.Entry iron = QuestLog.of(p).entry(Ch1Prep.Q_IRON);
            check(iron.stage == 1 && !iron.done, "the page did not move the iron trail to the armor stage");
            for (var it : List.of(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_HELMET.get(), jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_CHESTPLATE.get(), jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_LEGGINGS.get(), jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_BOOTS.get())) {
                p.getInventory().add(new ItemStack(it));
            }
            Quests.poll(p, true);
            check(!QuestLog.of(p).entry(Ch1Prep.Q_IRON).done && QuestLog.of(p).entry(Ch1Prep.Q_IRON).progress(0) == 0, "carried (not worn) armor counted");
            p.getInventory().clearContent();
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_CHESTPLATE.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_LEGGINGS.get()));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_IRON).progress(0) == 2, "two worn pieces should read 2/4 (" + QuestLog.of(p).entry(Ch1Prep.Q_IRON).progress(0) + ")");
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_HELMET.get()));
            p.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.IRONCLAD_BOOTS.get()));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_IRON).done, "four worn iron pieces did not finish the iron trail");
            check(PrepQuests.done(p, 1) == 1 && !PrepQuests.ready(p, 1), "one trail step should count as one");
            // 鉄の跡を終えると折れた剣が出る (盾と糧はまだ)
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE) != null && QuestLog.of(p).entry(Ch1Prep.Q_SHIELD) == null,
                    "finishing the iron trail should start only the blade trail");

            // 折れた剣: 職人に刃のことを聞く → 洞の台座の頁 → 職人に話す → 鉄の剣と砥石
            Quests.onTalk(p, "skyabyss:artisan", "ch1t_blade_tell");
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).stage == 1, "hearing the artisan did not move trail_blade to the cave stage");
            Lore.unlock(p, Ch1Prep.PAGE_BLADE, false);
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).stage == 2, "the broken sword page did not move trail_blade to the talk stage");
            Quests.onTalk(p, "skyabyss:artisan", "ch1t_blade_show");
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).stage == 3, "talking to the artisan did not move trail_blade to the forge stage");
            p.getInventory().add(new ItemStack(Items.IRON_SWORD));
            Quests.poll(p, true);
            Quests.signal(p, Ch1Prep.SIGNAL_WHETSTONE_USED);
            check(QuestLog.of(p).entry(Ch1Prep.Q_BLADE).done, "an iron sword and the whetstone did not finish trail_blade");
            Quests.poll(p, false);
            check(QuestLog.of(p).entry(Ch1Prep.Q_SHIELD) != null, "finishing the blade trail did not start the shield trail");

            // 盾と糧: 語り部の足音の晩の話 (伝承) → 盾と固焼き 8
            var st = jp.revati.skyabyss.npc.DialogueManager.dialogue(jp.revati.skyabyss.npc.NpcSetup.STORYTELLER_ID);
            check(st != null && st.nodes().containsKey("ch1t_shield_tale"), "the storyteller has no shield tale");
            if (st != null && st.nodes().containsKey("ch1t_shield_tale")) {
                st.nodes().get("ch1t_shield_tale").actions().forEach(x -> x.run(ctx));
                check(Lore.state(p).has(Ch1Prep.PAGE_SHIELD), "the shield tale did not record its page");
            }
            Quests.onTalk(p, "skyabyss:storyteller", "ch1t_shield_tale");
            check(QuestLog.of(p).entry(Ch1Prep.Q_SHIELD).stage == 1, "the tale did not move trail_shield to the pack stage");
            p.getInventory().add(new ItemStack(jp.revati.skyabyss.item.outfit.OutfitGear.TRAVEL_SHIELD.get()));
            p.getInventory().add(new ItemStack(jp.revati.skyabyss.guide.TravelGear.ROAD_HARDTACK.get(), 7));
            Quests.poll(p, true);
            check(!QuestLog.of(p).entry(Ch1Prep.Q_SHIELD).done, "seven hardtack finished trail_shield");
            p.getInventory().add(new ItemStack(jp.revati.skyabyss.guide.TravelGear.ROAD_HARDTACK.get(), 1));
            Quests.poll(p, true);
            check(QuestLog.of(p).entry(Ch1Prep.Q_SHIELD).done, "a shield and eight hardtack did not finish trail_shield");

            // 三本で ch1_ready と鯨追いの印 (一度だけ)
            check(PrepQuests.ready(p, 1) && Growth.get(p).discoveries.contains("prep_ready:1"), "the three trail steps did not raise ch1_ready");
            check(p.getInventory().countItem(GuideSetup.WHALE_CHASER_MARK.get()) == 1, "the whale chaser's mark was not given once");
            Quests.forget(p, Ch1Prep.Q_SHIELD);
            Quests.start(p, Ch1Prep.Q_SHIELD);
            for (int i = 0; i < 3 && !QuestLog.of(p).entry(Ch1Prep.Q_SHIELD).done; i++) {
                Quests.forceStage(p, Ch1Prep.Q_SHIELD);
            }
            check(p.getInventory().countItem(GuideSetup.WHALE_CHASER_MARK.get()) == 1, "the whale chaser's mark was given twice");
            // 文 (日英)
            for (String key : List.of("item.skyabyss.whale_chaser_mark", "item.skyabyss.whale_chaser_mark.lore", "message.skyabyss.ch1.pit.pack",
                    "message.skyabyss.ch1.pit.cold_open", "quest.skyabyss.ch1.trail_iron.armor.wear", "lore.skyabyss.ch1.cold_door.body",
                    BonePit.SIGN + ".1", BonePit.COLD_SIGN + ".4")) {
                check(jp.revati.skyabyss.SignFit.lang("ja_jp").containsKey(key) && jp.revati.skyabyss.SignFit.lang("en_us").containsKey(key),
                        "missing ja/en text " + key);
            }
        } finally {
            clean.run();
        }
    }

    // ---------------------------------------------------------------- descent・経験値の枠
    private static void descent(MinecraftServer server) {
        // 式: 見たら降りる (日・時刻・支度の本数は見ない)。場面の最中は降りない
        check(!Ch1Descent.allows(false, false), "the chain should not come down before the whale is seen");
        check(Ch1Descent.allows(true, false), "the chain should come down once the whale is seen");
        check(!Ch1Descent.allows(true, true), "the chain should wait while the player watches a scene");
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
        // 見つけてから背まで (短くした流れ): 現れてから最初に降りてよくなるまで 10 秒以内。見たあとの調べ直しは 5 秒おき
        check(jp.revati.skyabyss.titan.StoneWhaleEntity.FIRST_DESCENT_WAIT <= 200, "the first descent wait is longer than 10 seconds");
        int worst = jp.revati.skyabyss.titan.StoneWhaleEntity.DESCENT_RECHECK + (int) ticks + 70 * 20 + 20 * 20;
        check(worst <= 5 * 60 * 20, "from the first sight to the chain on the ground could take " + worst / 20 + " s (over 5 minutes)");
        check(jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(0.4, "S").isEmpty() && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(4.9, "S").isEmpty()
                && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(5, "S").equals("10m S") && jp.revati.skyabyss.quest.ObjectiveRules.distanceLabel(227, "NE").equals("230m NE"),
                "the distance label should hide under 5 m and round to 10 m");
        // 鎖の待ちの帯は一本だけ (支度の本数・三灯の版は無い。降りるまでの間がごく短いので「追いかけよう」)
        for (String lang : List.of("ja_jp", "en_us")) {
            java.util.Map<String, String> table;
            try {
                table = jp.revati.skyabyss.LangSelfTest.table(lang);
            } catch (java.io.IOException e) {
                check(false, lang + ": lang table could not be read: " + e);
                continue;
            }
            check(table.containsKey("quest.skyabyss.main_song.board.ride.wait"), lang + ": the chain-wait band text is missing");
            String waitKey = "quest.skyabyss.main_song.board.ride.wait";
            check(!table.containsKey(waitKey + ".prep") && !table.containsKey(waitKey + ".lamps"),
                    lang + ": old chain-wait variants (prep count / lamps) are still in the lang");
        }
        // 人ごと (差し替えた章・伝承・場面で)
        ServerLevel level = server.overworld();
        FakePlayer a = fake(server, "descent_a");
        FakePlayer b = fake(server, "descent_b");
        var chapterBefore = Ch1Descent.chapterOf;
        var seenBefore = Ch1Descent.seen;
        var sceneBefore = Ch1Descent.inScene;
        java.util.Set<java.util.UUID> seenSet = new java.util.HashSet<>();
        java.util.Set<java.util.UUID> sceneSet = new java.util.HashSet<>();
        try {
            Ch1Descent.chapterOf = p -> Chapters.WHALE;
            Ch1Descent.seen = p -> seenSet.contains(p.getUUID());
            Ch1Descent.inScene = p -> sceneSet.contains(p.getUUID());
            Quests.forgetAll(a);
            Quests.forgetAll(b);
            a.setPos(0, 200, 0);
            b.setPos(0, 200, 0);
            check(!Ch1Descent.qualifies(a) && !Ch1Descent.qualifies(b), "nobody qualifies before seeing the whale");
            // a だけ見た。支度は 0 本のまま (支度は鍵ではない)
            seenSet.add(a.getUUID());
            check(Ch1Descent.qualifies(a) && !Ch1Descent.qualifies(b), "only the player who saw the whale qualifies (with no prep)");
            sceneSet.add(a.getUUID());
            check(!Ch1Descent.qualifies(a), "a player inside the first-sight scene should not be picked");
            sceneSet.clear();
            // 章が違えば見たものとする (一章を過ぎた人)
            Ch1Descent.chapterOf = p -> Chapters.TIDE;
            check(Ch1Descent.qualifies(b), "a player past chapter 1 should not be gated");
            Ch1Descent.chapterOf = p -> Chapters.WHALE;
            // 岩鯨の近くで条件を満たす人を選ぶ
            var picked = Ch1Descent.pick(level, 0, 0, 100);
            check(picked == null || !picked.getUUID().equals(b.getUUID()), "the whale picked a player who has not seen it");
            seenSet.clear();
            check(Ch1Descent.pick(level, 0, 0, 100) == null || level.players().stream().noneMatch(p -> p.getUUID().equals(a.getUUID())),
                    "the whale picked somebody before anyone saw it");
        } finally {
            Ch1Descent.chapterOf = chapterBefore;
            Ch1Descent.seen = seenBefore;
            Ch1Descent.inScene = sceneBefore;
            Quests.forgetAll(a);
            Quests.forgetAll(b);
            Growth.get(a).discoveries.clear();
            Growth.get(b).discoveries.clear();
            a.getInventory().clearContent();
            b.getInventory().clearContent();
        }
        // 経験値の枠: 一章の新しい分 (支度の段 250 + 洞 40 + 骨の坑 40) は 400 に収まる
        int fresh = ChapterXpBudget.newXp(QuestManager.all(), 1);
        check(fresh <= ChapterXpBudget.NEW_CAP[1], "chapter 1 new xp " + fresh + " exceeds " + ChapterXpBudget.NEW_CAP[1]);
        check(fresh >= 250 + CleftCave.XP + BonePit.XP, "chapter 1 new xp " + fresh + " does not count the trail steps, the cave and the pit");
        check(ChapterXpBudget.overruns(QuestManager.all(), 1).isEmpty(), "chapter 1 is over its xp budget: " + ChapterXpBudget.overruns(QuestManager.all(), 1));
        int lore = Ch1Prep.LORE_XP.values().stream().mapToInt(Integer::intValue).sum();
        check(ChapterXpBudget.EXISTING[1] + fresh + lore <= (int) (ChapterXpBudget.LEVEL_XP[1] * ChapterXpBudget.MAX_RATIO),
                "chapter 1 small-element xp with the new lore (" + (ChapterXpBudget.EXISTING[1] + fresh + lore) + ") passes the ratio");
    }

    // p に支度の段を n 本終えさせる (鉄の跡、折れた剣の順。段を飛ばす)
    private static void finish(FakePlayer p, int n) {
        for (String id : Ch1Prep.TRAILS.subList(0, Math.min(n, 2))) {
            Quests.start(p, id);
            for (int i = 0; i < 4 && !QuestLog.of(p).entry(id).done; i++) {
                Quests.forceStage(p, id);
            }
        }
    }
}
