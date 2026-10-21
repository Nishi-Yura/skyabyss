package jp.revati.skyabyss.titan.tide.chapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.Foreshadow;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import jp.revati.skyabyss.titan.tide.TideShellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 二章「引く海」の自己テスト (SKYABYSS_SELFTEST_ONLY="ch2" で単独に)。結果は SELFTEST ch2 PASSED / FAILED。
//   予兆が 3 拍・段階 1 (二章) でだけ、順に、一度ずつ / 潮見の貝が方角と距離の三段と次の干潮の刻を返し、吹くと頼める (一つの刻に一人) /
//   大干潮が道を通し、安全で、水が戻り、一度きり / 小要素 4 つ・伝承・クエスト・会話・空の段が読める / 経験値の枠
// 海は人工 (岸の x < X+16 が陸、それより東が深さ 7 の海) を、読み込んだ区画に作って試す
public final class TideChapterSelfTest {
    private static final int X = -6000;
    private static final int Z = 6000;
    // 区画 (16 ブロック): 東西 14、南北 12。潮殻の休む場所は体の輪郭 (横 ±67・前 87・後ろ 59) の縁まで読み込まれた開けた海が要るので、
    // 岸から東へ 200 ブロック・南北 190 ブロックの海を作る (8 x 8 では体が収まらず、休む場所が世界の読み込みしだいで見つからなかった)
    private static final int CX = 14;
    private static final int CZ = 12;
    // 人工の海を建てたか、建ててから空の光が届くのを待った回数
    private static boolean built;
    private static int lightWaits;
    // 空の光を待つ上限 (load の呼び出しの回数)
    private static final int LIGHT_WAIT_MAX = 600;
    private static final int SHORE = X + 16;

    private static boolean forced;

    private TideChapterSelfTest() {
    }

    public static List<Step> steps() {
        return List.of(Step.until("ch2 (load)", TideChapterSelfTest::load), Step.once("ch2", 60, TideChapterSelfTest::runSelfTest));
    }

    private static boolean load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (!forced) {
            forced = true;
            for (int dx = 0; dx < CX; dx++) {
                for (int dz = 0; dz < CZ; dz++) {
                    level.setChunkForced((X >> 4) + dx, (Z >> 4) + dz, true);
                }
            }
        }
        for (int dx = 0; dx < CX; dx++) {
            for (int dz = 0; dz < CZ; dz++) {
                if (!level.getChunkSource().hasChunk((X >> 4) + dx, (Z >> 4) + dz)) {
                    return false;
                }
            }
        }
        // 海は読み込んでから建て、空の光 (光のエンジンは別のスレッドで後から追いつく) が岸に届くまで待つ。
        // 同じ tick で建てて調べると、元の丘の影が残って「空が見える」拍が世界の種しだいで落ちた
        if (!built) {
            if (!built) {
                buildSea(level);
            }
            built = true;
            lightWaits = 0;
            return false;
        }
        BlockPos probe = new BlockPos(SHORE - 6, level.getSeaLevel() + 1, Z + 64);
        if (!level.canSeeSky(probe) && ++lightWaits < LIGHT_WAIT_MAX) {
            return false;
        }
        if (lightWaits >= LIGHT_WAIT_MAX) {
            SkyAbyss.LOGGER.warn("SELFTEST ch2: the sky light did not reach the synthetic shore in time");
        }
        return true;
    }

    public static void runSelfTest(MinecraftServer server) {
        ServerLevel level = server.overworld();
        List<String> f = new ArrayList<>();
        long dayBefore = level.getDayTime();
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        TideEbb.EbbData ebb = TideEbb.EbbData.get(level);
        List<TideEbb.Run> ebbRuns = new ArrayList<>(ebb.runs);
        java.util.Set<UUID> ebbSeen = new java.util.HashSet<>(ebb.witnessed);
        try {
            data(f);
            buildSea(level);
            beats(server, level, f);
            conch(server, level, data, f);
            ebb(server, level, data, f);
            conchRegrant(level, f);
            budget(f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch2 crashed", e);
            f.add("crashed: " + e);
        } finally {
            level.setDayTime(dayBefore);
            TideShellEntity t = TideKeeper.KEEPER.current(level);
            if (t != null) {
                t.discard();
            }
            TideKeeper.KEEPER.releaseTicket(level);
            data.restoreSlot(TideKeeper.SLOT, saved, level.registryAccess());
            TideEbb.testEligible = null;
            ebb.runs.clear();
            ebb.runs.addAll(ebbRuns);
            ebb.witnessed.clear();
            ebb.witnessed.addAll(ebbSeen);
            ebb.setDirty();
            for (int dx = 0; dx < CX; dx++) {
                for (int dz = 0; dz < CZ; dz++) {
                    level.setChunkForced((X >> 4) + dx, (Z >> 4) + dz, false);
                }
            }
            forced = false;
            built = false;
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch2 PASSED (3 beats only in chapter 2 and once each, conch direction/tier/request, great ebb road/safe/restored/once per player/shared by neighbours, elements, lore, quests, dialogue, sky, xp)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch2 FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(ServerLevel level, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_ch2_test_" + suffix).getBytes());
        return FakePlayerFactory.get(level, new GameProfile(id, "skyabyss_ch2_" + suffix));
    }

    // ---------------------------------------------------------------- データが読める
    private static void data(List<String> f) {
        // 小要素 3〜5 つ + 二章の地上 (34 の C2: 塩の蔵・塩の道の標・潮だまり・筏・杭) も同じ表に入るので、合わせて 3〜12。部品が読める
        List<ChapterSites.Element> els = ChapterSites.elements(Chapters.TIDE);
        check(f, els.size() >= 3 && els.size() <= 12, "chapter 2 should have 3-12 chapter elements: " + els.size());
        for (String id : List.of(TideChapter.CAIRN_A, TideChapter.CAIRN_B, TideChapter.CARGO, TideChapter.STELE)) {
            check(f, els.stream().anyMatch(e -> e.id().equals(id)), "element not registered: " + id);
        }
        for (ChapterSites.Element e : els) {
            Blueprint bp = Blueprint.get(e.blueprint());
            check(f, !bp.cells.isEmpty(), "empty blueprint " + e.blueprint());
        }
        check(f, Blueprint.get("tide_cargo").marks.containsKey("chest"), "the cargo blueprint needs a chest mark");
        // 予兆は 3 拍・二章
        List<Beat> beats = Foreshadow.beats(Chapters.TIDE);
        check(f, beats.size() == 3 && beats.stream().map(Beat::order).toList().equals(List.of(1, 2, 3)), "three tide beats in order expected: " + beats.size());
        // 伝承・クエスト・会話
        for (String id : List.of(TideChapter.LORE_DRIFT, TideChapter.LORE_STELE, TideEbb.LORE_PAGE)) {
            LoreEntry entry = LoreManager.catalog().entry(id);
            check(f, entry != null && entry.unlockable() && entry.chapter().equals("titan_tide"), "lore entry missing or not tide: " + id);
        }
        for (String id : List.of("ch2/shore_signs", TideEbb.QUEST)) {
            QuestDef q = QuestManager.quest(id);
            check(f, q != null && q.chapter().equals("titan_tide"), "quest missing or not chapter titan_tide: " + id);
        }
        var dialogue = DialogueManager.dialogue("skyabyss:storyteller");
        check(f, dialogue != null && dialogue.nodes().containsKey("ch2_ledger") && dialogue.nodes().containsKey("ch2_ledger4"), "the storyteller ledger talk is not merged");
        // 空の段はごくわずか
        ChapterSky.Stage sky = ChapterSky.stage(Chapters.TIDE);
        check(f, !sky.equals(ChapterSky.Stage.NONE) && sky.moonBias() > 0 && sky.moonBias() <= 0.05f && sky.tintStrength() <= 0.08f, "the tide sky stage should be subtle: " + sky);
        check(f, TideChapterSetup.TIDE_WATCHING_SHELL.get() instanceof TideConchItem, "the shell item is not registered");
    }

    // ---------------------------------------------------------------- 人工の海
    private static void buildSea(ServerLevel level) {
        int sea = level.getSeaLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState water = Blocks.WATER.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = X; x < X + CX * 16; x++) {
            for (int z = Z; z < Z + CZ * 16; z++) {
                boolean land = x < SHORE;
                for (int y = sea - 14; y <= sea + 8; y++) {
                    BlockState s;
                    if (land) {
                        s = y <= sea ? stone : air;
                    } else {
                        s = y <= sea - 8 ? stone : y <= sea - 1 ? water : air;
                    }
                    level.setBlock(new BlockPos(x, y, z), s, 2 | 16);
                }
                // 海面 + 8 より上の地形 (丘・木) も消す。残すと、人工の海の上に浮いた丘が空と高さマップをふさぎ、
                // 空の見える拍・休む場所の海・大干潮の道の探しが世界の種しだいで落ちる
                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = sea + 9; y < top; y++) {
                    level.setBlock(new BlockPos(x, y, z), air, 2 | 16);
                }
            }
        }
    }

    // ---------------------------------------------------------------- 予兆
    private static void beats(MinecraftServer server, ServerLevel level, List<String> f) {
        FakePlayer p = fake(level, "beats");
        ArcData arc = ArcData.of(p);
        arc.reset();
        p.setPos(SHORE - 6, level.getSeaLevel() + 1, Z + 64);
        // 巨神 (3 拍目の距離の条件のため): 岸から東 80 の沖
        TideShellEntity titan = TideKeeper.KEEPER.spawn(level, SHORE + 80, Z + 64, null);
        check(f, titan != null, "could not spawn the tide shell");
        // 二章の人だけ: 一章・三章・四章の人には出ない
        for (Chapters other : List.of(Chapters.WHALE, Chapters.STORM, Chapters.EMBER, Chapters.PROLOGUE)) {
            // 他の章の枠の拍は条件しだいで出うる。潮殻の拍が混ざらないことだけを確かめる
            Beat got = Foreshadow.next(p, other, 100000, 6000);
            check(f, got == null || got.chapter() != Chapters.TIDE, "tide beats must not be eligible for " + other);
        }
        check(f, Chapters.of(p) != Chapters.TIDE, "a stage-0 player should not be in chapter 2 (so the real tick never fires the beats)");
        // 1 拍目: 昼、水辺。まだ荷などが置かれていない人は水辺で足りる
        level.setDayTime(6000);
        Beat b = Foreshadow.tryFire(p, Chapters.TIDE, 100000, 6000);
        check(f, b != null && b.order() == 1, "beat 1 should fire by the shore in daylight: " + (b == null ? null : b.id()));
        check(f, Foreshadow.done(p, "titan_tide/" + TideChapter.BEAT_CAIRNS), "beat 1 should be recorded");
        // 昼のままでは 2 拍目 (夕暮れ・夜明け) は出ない。間隔も空ける
        check(f, Foreshadow.tryFire(p, Chapters.TIDE, 100010, 6000) == null, "no beat right after a beat (cooldown)");
        level.setDayTime(6000);
        check(f, Foreshadow.next(p, Chapters.TIDE, 120000, 6000) == null, "beat 2 needs dusk or dawn and beat 3 needs the low tide");
        // 夕暮れ: 2 拍目
        level.setDayTime(12000);
        b = Foreshadow.tryFire(p, Chapters.TIDE, 120000, 12000);
        check(f, b != null && b.order() == 2, "beat 2 should fire at dusk: " + (b == null ? null : b.id()));
        // 干潮の刻: 3 拍目 (沖の島までの距離 40〜420)
        level.setDayTime(11000);
        b = Foreshadow.tryFire(p, Chapters.TIDE, 140000, 11000);
        check(f, b != null && b.order() == 3, "beat 3 should fire at low tide with the island in sight distance: " + (b == null ? null : b.id()));
        // 冪等
        check(f, Foreshadow.tryFire(p, Chapters.TIDE, 900000, 11000) == null && arc.beatCount(Chapters.TIDE) == 3, "each beat fires once");
        // 3 拍目は沖の島が遠すぎると出ない (別の人を、島から 600 ブロック西に置く。距離の条件が先に見られるのでチャンクは読まない)
        FakePlayer q = fake(level, "beats_far");
        ArcData.of(q).reset();
        q.setPos(SHORE - 520, level.getSeaLevel() + 1, Z + 64);
        level.setDayTime(11000);
        ArcData.of(q).markBeat("titan_tide/" + TideChapter.BEAT_CAIRNS, 0);
        ArcData.of(q).markBeat("titan_tide/" + TideChapter.BEAT_CONCH, 0);
        check(f, Foreshadow.next(q, Chapters.TIDE, 900000, 11000) == null, "beat 3 must not fire when the island is beyond sight distance");
        arc.reset();
        ArcData.of(q).reset();
    }

    // ---------------------------------------------------------------- 潮見の貝
    private static void conch(MinecraftServer server, ServerLevel level, TitanWorldData data, List<String> f) {
        // 純粋な部分: 八方位・距離の三段・次の刻まで
        check(f, TideConchItem.compass(0, -10) == 0 && TideConchItem.compass(10, -10) == 1 && TideConchItem.compass(10, 0) == 2
                && TideConchItem.compass(10, 10) == 3 && TideConchItem.compass(0, 10) == 4 && TideConchItem.compass(-10, 10) == 5
                && TideConchItem.compass(-10, 0) == 6 && TideConchItem.compass(-10, -10) == 7, "compass points are wrong");
        check(f, TideConchItem.tier(10) == 2 && TideConchItem.tier(200) == 1 && TideConchItem.tier(900) == 0, "distance tiers are wrong");
        TideConchItem.Reading r = TideConchItem.reading(new Vec3(0, 64, 0), new Vec3(300, 64, 0), 10000, "");
        check(f, r.found() && r.compass() == 2 && r.tier() == 1 && r.minutesToLow() == 1 && !r.lowNow(), "reading is wrong: " + r);
        r = TideConchItem.reading(new Vec3(0, 64, 0), new Vec3(0, 64, -50), 12500, "x");
        check(f, r.compass() == 0 && r.tier() == 2 && r.lowNow() && r.asked().equals("x"), "reading at low tide is wrong: " + r);
        check(f, !TideConchItem.describe(r).getString().isEmpty() && !TideConchItem.describe(TideConchItem.Reading.NONE).getString().isEmpty(), "describe is empty");

        FakePlayer p = fake(level, "conch");
        p.setPos(SHORE - 6, level.getSeaLevel() + 1, Z + 64);
        // 巨神がまだいなければ「黙っている」。いれば本物の向き (東) を返す
        TideShellEntity titan = TideKeeper.KEEPER.current(level);
        if (titan != null) {
            titan.discard();
        }
        TideKeeper.KEEPER.releaseTicket(level);
        data.restoreSlot(TideKeeper.SLOT, new CompoundTag(), level.registryAccess());
        check(f, !TideConchItem.read(p).found(), "the shell should be silent when no titan has appeared");
        titan = TideKeeper.KEEPER.spawn(level, SHORE + 100, Z + 64, null);
        TideConchItem.Reading real = TideConchItem.read(p);
        check(f, real.found() && real.compass() == 2 && real.tier() == 1, "the shell should point east at a near titan: " + real);

        // 吹く: 刻の 1 分前から。巡航中なら頼みが通り、寄る場所を決めて向かう。一つの刻に一人
        level.setDayTime(10000);
        check(f, titan.saveState().getInt("Mode") == 0, "the titan should start cruising");
        // 二章の跡地があれば、浜から遠い人の頼みは通らない (FAR。33 の 3.1)。この先の合成の海の検査は、浜に限らずに流す
        if (jp.revati.skyabyss.titan.tide.TideBeach.restricted(server) && !jp.revati.skyabyss.titan.tide.TideBeach.near(p)) {
            check(f, titan.request(p) == TideShellEntity.RequestResult.FAR, "far from the east beach the shell should not call the island");
        }
        jp.revati.skyabyss.titan.tide.TideBeach.testOff = true;
        TideShellEntity.RequestResult first = TideConchItem.blow(p, level);
        check(f, first == TideShellEntity.RequestResult.OK, "blowing the shell near the sea before the ebb should be accepted: " + first);
        check(f, titan.saveState().getInt("Mode") == 1 && titan.requesterNow().equals(p.getGameProfile().getName()), "the titan should approach the caller: " + titan.saveState());
        FakePlayer other = fake(level, "conch_other");
        other.setPos(SHORE - 6, level.getSeaLevel() + 1, Z + 70);
        check(f, TideConchItem.blow(other, level) == TideShellEntity.RequestResult.TAKEN, "a second caller in the same window should be refused");
        check(f, TideConchItem.blow(p, level) == TideShellEntity.RequestResult.NOT_NOW, "the same caller cannot call twice");
        // 保存・引き継ぎ
        TideShellEntity second = TideKeeper.KEEPER.spawn(level, SHORE + 100, Z + 64, titan.saveState());
        check(f, second.requesterNow().equals(p.getGameProfile().getName()), "the caller should be carried to the next instance");
        // 刻の外では吹けない
        level.setDayTime(6000);
        TideShellEntity third = TideKeeper.KEEPER.spawn(level, SHORE + 100, Z + 64, null);
        check(f, TideConchItem.blow(p, level) == TideShellEntity.RequestResult.NOT_NOW, "far from the ebb the shell cannot call the island");
        // 海から遠いと吹けない (null)
        p.setPos(X - 200, level.getSeaLevel() + 1, Z + 64);
        level.setDayTime(10000);
        check(f, TideConchItem.blow(p, level) == null, "the shell must refuse far from the sea");
        // 一人に一つ
        FakePlayer g = fake(level, "conch_gift");
        g.getPersistentData().remove(TideChapter.SHELL_GIVEN);
        check(f, TideChapter.giveShell(g) && !TideChapter.giveShell(g), "the shell is given once per player");
        check(f, jp.revati.skyabyss.pouch.Pouch.countAll(g, TideChapterSetup.TIDE_WATCHING_SHELL.get()) == 1, "the shell is in the pouch or the inventory");
        g.getPersistentData().remove(TideChapter.SHELL_GIVEN);
        if (third != null) {
            third.discard();
        }
        shoreBoard(server, level, f);
        jp.revati.skyabyss.titan.tide.TideBeach.testOff = false;
    }

    // 浜の乗り場: 岸から 20 ブロック沖に乗り場の輪が来るように休んだ潮殻は、岸に光の輪を立てる。岸を歩いてその輪に入った人は背中へ渡る
    private static void shoreBoard(MinecraftServer server, ServerLevel level, List<String> f) {
        jp.revati.skyabyss.progress.ProgressData progress = jp.revati.skyabyss.progress.ProgressData.get(server);
        // 左 (潮門) を西の岸へ向ける: 向き 180 で左は -x
        TideShellEntity shell = TideKeeper.KEEPER.spawn(level, SHORE + (int) TideShellEntity.DOCK_SIDE + 20, Z + 64, null);
        FakePlayer walker = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "skyabyss_ch2_shore_walker"));
        try {
            if (shell == null) {
                f.add("no tide shell for the shore dock test");
                return;
            }
            shell.setYRot(180f);
            shell.restHere();
            shell.searchShoreDockNow();
            Vec3 sd = shell.shoreDock();
            Vec3 dock = shell.dockCenter();
            check(f, sd != null, "a resting shell 20 blocks off the shore has no shore dock (dock " + dock + ")");
            if (sd == null) {
                return;
            }
            check(f, sd.x <= SHORE + 1 && Math.hypot(sd.x - dock.x, sd.z - dock.z) <= 32, "the shore dock is not on the shore near the dock ring: " + sd + " (dock " + dock + ")");
            check(f, !level.getFluidState(BlockPos.containing(sd.x, sd.y - 1, sd.z)).is(net.minecraft.tags.FluidTags.WATER), "the shore dock stands in water: " + sd);
            progress.markPersonal(walker.getUUID(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
            // 前の自己テスト (心臓の戦い) が潮殻を眠らせたままなら、起こしてから (眠っている巨神には乗れない)
            jp.revati.skyabyss.titan.end.TitanDormancy.clear(level.getServer(), jp.revati.skyabyss.progress.Titans.TIDE_SHELL);
            // 岸を歩いてくる: 輪の 6 ブロック手前 (陸) では渡らず、輪の中で渡る
            walker.moveTo(sd.x - 6, sd.y, sd.z);
            check(f, !shell.tryShoreBoard(walker), "a player 6 blocks from the shore ring crossed");
            walker.moveTo(sd.x + 0.5, sd.y, sd.z);
            check(f, shell.tryShoreBoard(walker) && walker.level().dimension() == jp.revati.skyabyss.titan.tide.TideTravel.TIDE_BACK,
                    "walking into the shore ring did not take the player to the tide back");
        } finally {
            if (walker.level() instanceof ServerLevel at) {
                at.removePlayerImmediately(walker, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
            }
            progress.clearPersonal(walker.getUUID(), jp.revati.skyabyss.progress.Titans.STONE_WHALE);
            jp.revati.skyabyss.titan.tide.TideTravel.PASSAGE_FORGET.accept(walker.getUUID());
            if (shell != null) {
                shell.discard();
            }
        }
    }

    // ---------------------------------------------------------------- 大干潮
    private static void ebb(MinecraftServer server, ServerLevel level, TitanWorldData data, List<String> f) {
        int sea = level.getSeaLevel();
        TideEbb.EbbData d = TideEbb.EbbData.get(level);
        d.runs.clear();
        d.witnessed.clear();
        FakePlayer p = fake(level, "ebb");
        p.setPos(X + 10, sea + 1, Z + 64);
        TideEbb.Plan plan = TideEbb.plan(level, X + 10, Z + 64);
        check(f, plan != null, "no ebb road planned on the synthetic sea");
        if (plan == null) {
            return;
        }
        check(f, plan.dx() == 1 && plan.dz() == 0 && plan.startX() == SHORE && plan.len() >= TideEbb.LEN_MIN && plan.len() <= TideEbb.LEN_MAX,
                "the road should run east from the shore: " + plan.startX() + "," + plan.startZ() + " d" + plan.dx() + "," + plan.dz() + " len " + plan.len());
        // 巨神が現れていて夜なら始められる (まだ現れていなければ始まらない)
        level.setDayTime(15000);
        TideKeeper.KEEPER.releaseTicket(level);
        TideShellEntity gone = TideKeeper.KEEPER.current(level);
        if (gone != null) {
            gone.discard();
        }
        data.restoreSlot(TideKeeper.SLOT, new CompoundTag(), level.registryAccess());
        check(f, !TideEbb.mayBegin(level, d), "the great ebb must wait for the titan to appear");
        TideKeeper.KEEPER.spawn(level, SHORE + 100, Z + 64, null);
        check(f, TideEbb.mayBegin(level, d), "the great ebb should be possible at night once the titan has appeared");
        level.setDayTime(6000);
        check(f, !TideEbb.mayBegin(level, d), "the great ebb must not begin in daylight");
        level.setDayTime(15000);
        // 二章の人 (段階 1) だけが引き金: 段階 0 の人 (と FakePlayer) では始まらない
        TideEbb.tryTrigger(level, d);
        check(f, !d.active(), "nobody in chapter 2 is here, so the ebb must not begin");

        // 始める
        long now = level.getGameTime();
        check(f, TideEbb.begin(level, d, plan) && d.active(), "the great ebb should begin");
        TideEbb.Run run = d.runs.get(0);
        check(f, run.endTick - now > 1000 && run.endTick - now <= TideEbb.MAX_TICKS, "the ebb should last 1000-" + TideEbb.MAX_TICKS + " ticks: " + (run.endTick - now));
        int i = Math.min(10, plan.len() - 5);
        BlockPos c = plan.center(i);
        int fy = c.getY();
        check(f, !level.getBlockState(c).isAir() && !level.getBlockState(c).is(Blocks.WATER), "the road floor should be stone");
        check(f, level.getBlockState(c.above()).is(Blocks.WATER), "the road should hold knee-deep water");
        check(f, level.getBlockState(c.above(2)).isAir() && level.getBlockState(c.above(sea - 1 - fy)).isAir(), "the road should be open air above the knee-deep water");
        BlockPos wall = new BlockPos(c.getX(), fy + 2, c.getZ() + 2);
        check(f, !level.getBlockState(wall).isAir() && !level.getFluidState(wall).isSource(), "the road should have walls on both sides");
        check(f, !level.getBlockState(new BlockPos(c.getX(), fy + 2, c.getZ() - 2)).isAir(), "the other wall is missing");
        BlockPos capCell = plan.center(plan.len());
        check(f, !level.getBlockState(new BlockPos(capCell.getX(), fy + 2, capCell.getZ())).isAir(), "the road should be closed at the far end");
        // 壁の外は元の海のまま (水が壁を越えて入ってこない: 道の中を上から見て空気)
        check(f, level.getFluidState(new BlockPos(c.getX(), fy + 3, c.getZ() + 3)).isSource(), "the sea outside the walls should stay");
        // 道の中から、敵を除く
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z != null) {
            z.moveTo(c.getX() + 0.5, fy + 1, c.getZ() + 0.5, 0, 0);
            level.addFreshEntity(z);
        }
        // 同じ所に重ねては始まらない (途中の道と重なる計画は断る)
        check(f, !TideEbb.begin(level, d, plan) && d.runs.size() == 1, "a second ebb must not overlap one that is running");
        // 保存の往復
        TideEbb.EbbData back = TideEbb.EbbData.load(d.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(f, back.runs.size() == 1 && back.runs.get(0).plan.len() == plan.len() && back.runs.get(0).plan.startX() == plan.startX()
                && java.util.Arrays.equals(back.runs.get(0).plan.floor(), plan.floor()) && back.runs.get(0).endTick == run.endTick && back.witnessed.equals(d.witnessed), "the ebb record should survive save and load");
        // 途中の見回り: 敵が消え、台に着いた人に頁と合図
        BlockPos dais = plan.dais();
        p.setPos(dais.getX() + 0.5, dais.getY(), dais.getZ() + 0.5);
        Lore.lock(p, TideEbb.LORE_PAGE);
        TideEbb.tickActive(level, d, List.of(p));
        check(f, z == null || z.isRemoved(), "a hostile mob inside the road should be removed");
        check(f, Lore.state(p).has(TideEbb.LORE_PAGE), "the page should be given at the end of the road");
        check(f, d.active(), "the ebb should still be under way");
        // 終わり: 中にいる人は岸へ戻され、水が戻る
        check(f, TideEbb.inside(level, plan).contains(p.position()), "the player should be inside the road box before the end");
        TideEbb.end(level, d, List.of(p));
        check(f, !d.active(), "the ebb should be done");
        // (FakePlayer は teleportTo で動かないので、戻し先が道の外の陸であることを見る)
        BlockPos land = TideEbb.landfall(level, plan);
        check(f, !TideEbb.inside(level, plan).contains(new Vec3(land.getX() + 0.5, land.getY(), land.getZ() + 0.5)) && land.getX() < SHORE, "the landing spot for people caught in the road should be on land outside it: " + land);
        check(f, level.getBlockState(new BlockPos(c.getX(), fy + 2, c.getZ())).is(Blocks.WATER) && level.getBlockState(wall).is(Blocks.WATER)
                && level.getBlockState(new BlockPos(c.getX(), sea - 1, c.getZ())).is(Blocks.WATER), "the sea should be back inside and over the walls");
        check(f, !level.getBlockState(c).isAir() && !level.getBlockState(c).is(Blocks.WATER), "the road floor should remain on the seabed");
        // 後始末の水: 全部水に戻っている (台の海のランタンも)
        for (int k = 0; k <= plan.len(); k++) {
            BlockPos cc = plan.center(k);
            for (int w = -2; w <= 2; w++) {
                for (int y = cc.getY() + 1; y <= sea - 1; y++) {
                    BlockPos at = new BlockPos(cc.getX(), y, cc.getZ() + w);
                    if (!level.getBlockState(at).is(Blocks.WATER)) {
                        f.add("water did not come back at " + at + " (" + level.getBlockState(at) + ")");
                        return;
                    }
                }
            }
        }
        perPlayer(level, d, f);
    }

    // 大干潮は人ごとに一度ずつ: 近くの二章の人は同じものを迎え、迎えた人には二度起きず、まだの人には（前のが終わっていても）新しく起きる
    private static void perPlayer(ServerLevel level, TideEbb.EbbData d, List<String> f) {
        int sea = level.getSeaLevel();
        FakePlayer a = fake(level, "ebb_a");
        FakePlayer b = fake(level, "ebb_b");
        FakePlayer c = fake(level, "ebb_c");
        TideEbb.testEligible = pl -> pl == a || pl == b || pl == c;
        d.runs.clear();
        d.witnessed.clear();
        level.setDayTime(15000);
        a.setPos(X + 10, sea + 1, Z + 64);
        b.setPos(X + 14, sea + 1, Z + 66);
        c.setPos(X + 12, sea + 1, Z + 62);
        TideEbb.tryTrigger(level, d, List.of(a));
        check(f, d.runs.size() == 1 && d.witnessed.contains(a.getUUID()) && !d.witnessed.contains(b.getUUID()), "a chapter 2 player near the sea should get an ebb of their own");
        TideEbb.tryTrigger(level, d, List.of(a, b));
        check(f, d.runs.size() == 1 && d.witnessed.contains(b.getUUID()), "a neighbour should share the running ebb, not start a second road: runs " + d.runs.size());
        TideEbb.tryTrigger(level, d, List.of(a, b));
        check(f, d.runs.size() == 1, "people who already had their ebb must not start another");
        TideEbb.end(level, d, List.of(a, b));
        check(f, !d.active(), "the shared ebb should end");
        TideEbb.tryTrigger(level, d, List.of(a, b));
        check(f, !d.active(), "a and b already had their ebb: no second one for them");
        TideEbb.tryTrigger(level, d, List.of(a, b, c));
        check(f, d.runs.size() == 1 && d.witnessed.contains(c.getUUID()), "a player who has not had their ebb gets one even after an earlier ebb ended");
        TideEbb.EbbData back = TideEbb.EbbData.load(d.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(f, back.witnessed.size() == 3 && back.witnessed.contains(a.getUUID()), "who had their ebb should survive save and load");
        TideEbb.end(level, d, List.of(a, b, c));
        check(f, !d.active(), "the last ebb should end");
        TideEbb.testEligible = null;
    }

    // 潮見の貝をなくした人に、語り部が新しい貝をくれる (選択肢は二章以降でどこにも持っていない人にだけ)
    private static void conchRegrant(ServerLevel level, List<String> f) {
        var dialogue = DialogueManager.dialogue("skyabyss:storyteller");
        check(f, dialogue != null && dialogue.nodes().containsKey("ch2_conch_ask") && dialogue.nodes().containsKey("ch2_conch_done"), "the storyteller conch nodes are not merged");
        if (dialogue == null) {
            return;
        }
        check(f, DialogueManager.loadErrors().stream().noneMatch(e -> e.contains("ch2")), "dialogue load errors: " + DialogueManager.loadErrors());
        // 話題の入口は墓の話題と一つ (「なくしたものがある」)。話題の枠は 13 まで
        var menu = dialogue.nodes().get("menu");
        check(f, menu.options().size() <= 13, "the storyteller menu should hold within 13 options");
        var lost = dialogue.nodes().get("lost_ask");
        check(f, lost != null, "the storyteller 'lost something' node is missing");
        if (lost == null) {
            return;
        }
        var option = lost.options().stream().filter(o -> o.id().equals("conch")).findFirst().orElse(null);
        var yes = dialogue.nodes().get("ch2_conch_ask").options().stream().filter(o -> o.id().equals("yes")).findFirst().orElse(null);
        check(f, option != null && yes != null, "the conch option or its yes answer is missing");
        if (option == null || yes == null) {
            return;
        }
        FakePlayer p = fake(level, "regrant");
        p.getInventory().clearContent();
        var ctx = jp.revati.skyabyss.quest.StoryContext.of(p);
        var shell = TideChapterSetup.TIDE_WATCHING_SHELL.get();
        // 段階 0 (一章の人) には出ない。持っていない二章以降の人には出る (段階の条件は人工的に外して見る)
        check(f, !option.condition().test(ctx), "a chapter 1 player should not be offered a new conch");
        var menuLost = menu.options().stream().filter(o -> o.id().equals("grave")).findFirst().orElse(null);
        check(f, menuLost != null && !menuLost.condition().test(ctx), "a chapter 1 player with no grave should not see the lost-something topic");
        check(f, option.target().equals("ch2_conch_ask"), "the conch option should lead to the storyteller's conch node");
        check(f, yes.condition().test(ctx), "without a conch, the answer should be possible");
        yes.actions().forEach(a -> a.run(ctx));
        check(f, jp.revati.skyabyss.record.Keepsakes.locate(p, shell).total() == 1, "the storyteller should give one conch");
        check(f, !yes.condition().test(ctx), "with a conch in the pack, no second conch");
        // 手もとのどこかにあれば (箱の中でも) 数える
        var chest = p.getEnderChestInventory();
        check(f, !yes.condition().test(ctx) && jp.revati.skyabyss.pouch.Pouch.count(p, shell) == 1, "a conch in the pouch counts as owned");
        jp.revati.skyabyss.pouch.Pouch.clear(p);
        p.getInventory().clearContent();
        chest.setItem(0, new net.minecraft.world.item.ItemStack(shell));
        check(f, !yes.condition().test(ctx), "a conch in the ender chest counts as owned");
        chest.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
        check(f, yes.condition().test(ctx), "an empty ender chest and pack should allow a new conch");
        p.getInventory().clearContent();
    }

    // ---------------------------------------------------------------- 経験値の枠 (小要素の経験値は 1〜1.5 レベル分まで)
    private static void budget(List<String> f) {
        int xp = 0;
        for (String id : List.of("ch2/shore_signs", TideEbb.QUEST)) {
            QuestDef q = QuestManager.quest(id);
            if (q == null) {
                continue;
            }
            xp += q.reward().xp();
            for (QuestDef.Stage s : q.stages()) {
                xp += s.xp();
            }
        }
        int level = Chapters.TIDE.levelFrom();
        int oneLevel = 50 + 25 * level + 2 * level * level;
        check(f, xp <= oneLevel * 1.5, "chapter 2 small elements give " + xp + " xp, over 1.5 levels (" + oneLevel + " per level at Lv " + level + ")");
        check(f, xp >= oneLevel / 2, "chapter 2 small elements give only " + xp + " xp");
    }
}
