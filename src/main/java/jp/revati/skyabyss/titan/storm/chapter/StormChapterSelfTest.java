package jp.revati.skyabyss.titan.storm.chapter;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SignFit;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreManager;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.SelfTestRunner.Step;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestLog;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.tablet.TabletBlockEntity;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.sky.SkyPass;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.Foreshadow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

// 三章「風に逆らう嵐」(雷翼。docs/design/12 の 12 章) の自己テスト。結果は SELFTEST storm chapter PASSED / FAILED。
//   (登録)     予兆 3 拍 (順番・名前・章)・小要素 4 種と部品・空の段・クエスト 2 本・イサナの足し書き・伝承 3 つ・日英の文と看板の幅・経験値の予算
//   (段階 2 だけ) 予兆は三章の人にだけ起きる (一章・二章・四章の人には出ない)
//   (焼けた筋) 一直線 (縦・横・斜め)・自然の地面だけ (人が置いた板は残る)・倒れた木は筋の向き・人の前に本当に置ける (burnLine)
//   (伏せる獣) 近くの動物の足が止まる・動物がいなくても起きる・巨神がいなければ稲光の翼は見送る
//   (風見の向き) 八方位 + 斜めで矢が指す向きが ±25 度以内・同じ向きなら何も変えない・人が置いたブロックは触らない・巨神がいない日は風の逆
//   (見せ場)   影と巨神を同時に見る条件 (全部が要る)・一度だけ (記録のあとは起きない)・確実に見せる条件 (予兆 3 拍目のあと、昼、巡航、10 秒)
//   (余韻)     雷翼の心臓を止めた人に一度だけ・伝承の旧セーブには出さない
public final class StormChapterSelfTest {
    // 岩鯨 (4000, 4000)・潮殻 (-4000, 4000)・雷翼のテスト (-6000, -6000) と重ならない所。高い所 (y 230) に平らな台を作る
    private static final int CX = -9400;
    private static final int CZ = -9400;
    private static final int HALF = 72;
    private static final int Y = 230;
    private static final int FORCE = 5;

    private static boolean forced;

    private StormChapterSelfTest() {
    }

    public static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = -FORCE; dx <= FORCE; dx++) {
            for (int dz = -FORCE; dz <= FORCE; dz++) {
                level.setChunkForced((CX >> 4) + dx, (CZ >> 4) + dz, true);
            }
        }
        forced = true;
    }

    public static boolean ready(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = -FORCE; dx <= FORCE; dx++) {
            for (int dz = -FORCE; dz <= FORCE; dz++) {
                if (!level.hasChunk((CX >> 4) + dx, (CZ >> 4) + dz)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        try {
            data(f);
            texts(f);
            stageTwoOnly(server, f);
            platform(level, true);
            try {
                burn(level, server, f);
                beasts(level, server, f);
                vane(level, f);
            } finally {
                platform(level, false);
            }
            showcase(server, f);
            aftermath(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm chapter crashed", e);
            f.add("crashed: " + e);
        } finally {
            StormShowcase.forgetAll();
            if (forced) {
                for (int dx = -FORCE; dx <= FORCE; dx++) {
                    for (int dz = -FORCE; dz <= FORCE; dz++) {
                        level.setChunkForced((CX >> 4) + dx, (CZ >> 4) + dz, false);
                    }
                }
                forced = false;
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm chapter PASSED (3 beats stage-2 only, 4 sites, quests/dialogue/lore/texts, burn line, low beasts, vane bearing, shadow showcase once, aftermath)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST storm chapter FAILED: {}", m));
        }
    }

    private static void check(List<String> f, boolean ok, String what) {
        if (!ok) {
            f.add(what);
        }
    }

    private static FakePlayer fake(MinecraftServer server, String suffix) {
        UUID id = UUID.nameUUIDFromBytes(("skyabyss_storm_chapter_test_" + suffix).getBytes());
        return FakePlayerFactory.get(server.overworld(), new GameProfile(id, "skyabyss_ch3_" + suffix));
    }

    // 高い所の平らな台 (草の上・上は空)。on = false で片づける
    private static void platform(ServerLevel level, boolean on) {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - HALF; x <= CX + HALF; x++) {
            for (int z = CZ - HALF; z <= CZ + HALF; z++) {
                if (on) {
                    level.setBlock(p.set(x, Y - 1, z), dirt, 2);
                    level.setBlock(p.set(x, Y, z), grass, 2);
                    for (int y = Y + 1; y <= Y + 14; y++) {
                        level.setBlock(p.set(x, y, z), air, 2);
                    }
                } else {
                    for (int y = Y - 1; y <= Y + 14; y++) {
                        level.setBlock(p.set(x, y, z), air, 2);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 登録とデータ
    private static void data(List<String> f) {
        List<Beat> beats = Foreshadow.beats(Chapters.STORM);
        check(f, beats.size() == 3 && beats.get(0).name().equals(StormForeshadow.BURN_LINE) && beats.get(1).name().equals(StormForeshadow.LOW_BEASTS)
                && beats.get(2).name().equals(StormForeshadow.WING_FLASH), "the storm chapter should register 3 beats in order: " + beats.stream().map(Beat::id).toList());
        check(f, beats.size() == 3 && beats.get(0).time() == Beat.Time.DAY && beats.get(1).time() == Beat.Time.NIGHT, "beat 1 is for the day and beat 2 for the night");
        check(f, Foreshadow.beats(Chapters.TIDE).stream().noneMatch(b -> b.name().equals(StormForeshadow.BURN_LINE)), "storm beats must not leak into other chapters");
        check(f, ChapterSky.stage(Chapters.STORM).equals(StormForeshadow.SKY_STAGE) && !ChapterSky.stage(Chapters.STORM).equals(ChapterSky.Stage.NONE),
                "the storm chapter should register its sky stage");
        check(f, ChapterSky.stage(Chapters.STORM).moonBias() > 0 && ChapterSky.stage(Chapters.STORM).tintStrength() <= 0.35f, "sky stage within bounds");

        List<String> ids = ChapterSites.elements(Chapters.STORM).stream().map(ChapterSites.Element::id).toList();
        check(f, Set.copyOf(ids).equals(Set.copyOf(StormSites.ELEMENTS)) && ids.size() == 4, "the storm chapter should register 4 small elements: " + ids);
        int total = 0;
        for (ChapterSites.Element e : ChapterSites.elements(Chapters.STORM)) {
            total += e.rules().count();
            Blueprint bp = Blueprint.get(e.blueprint());
            check(f, !bp.cells.isEmpty() && bp.rx1 - bp.rx0 <= 8 && bp.rz1 - bp.rz0 <= 8, e.id() + ": blueprint should be small");
            String mark = switch (e.id()) {
                case StormSites.VANE -> "top";
                case StormSites.RUIN -> "rod";
                case StormSites.PACK -> "barrel";
                default -> "tablet";
            };
            check(f, bp.marks.containsKey(mark), e.id() + ": blueprint needs mark " + mark);
            check(f, e.rules().maxDist() <= 150, e.id() + ": must stay within 150 blocks");
        }
        check(f, total >= 3 && total <= 7, "3 to 7 placed elements (3-5 kinds): " + total);
        ChapterSites.Element vane = ChapterSites.elements(Chapters.STORM).stream().filter(e -> e.id().equals(StormSites.VANE)).findFirst().orElse(null);
        check(f, vane != null && vane.rules().prefer() == ChapterSites.Prefer.HIGH && vane.post() != null && vane.onArrive() != null, "the vane hill wants high ground and has a finish");

        // クエスト・会話・伝承
        QuestDef marks = QuestManager.quest("ch3/sub_wind_marks");
        QuestDef wait = QuestManager.quest("ch3/sub_isana_wait");
        check(f, marks != null && marks.chapterOrNull() == Chapters.STORM && wait != null && wait.chapterOrNull() == Chapters.STORM, "both ch3 quests load with the storm chapter");
        if (marks != null && wait != null) {
            int xp = 0;
            for (QuestDef q : List.of(marks, wait)) {
                xp += q.reward().xp();
                for (QuestDef.Stage s : q.stages()) {
                    xp += s.xp();
                }
            }
            // Lv 18 → 19 に要る経験値 = 50 + 25 * 18 + 2 * 18^2 (04)。小要素は 1 レベル分まで
            check(f, xp > 0 && xp <= 50 + 25 * 18 + 2 * 18 * 18, "small-element xp must stay within one level (" + xp + ")");
            check(f, QuestManager.loadErrors().isEmpty(), "quests load without errors: " + QuestManager.loadErrors());
            check(f, wait.stages().size() == 2 && "skyabyss:whale_chaser".equals(wait.giver()), "the Isana quest has two stages and Isana as the giver");
        }
        Dialogue d = DialogueManager.dialogue("skyabyss:whale_chaser");
        check(f, d != null && d.nodes().containsKey("ch3_wait_offer") && d.nodes().containsKey("ch3_wait_done4") && DialogueManager.loadErrors().isEmpty(),
                "Isana should load with the ch3 extension: " + DialogueManager.loadErrors());
        if (d != null) {
            check(f, d.nodes().keySet().stream().filter(k -> k.startsWith("ch3_")).count() == 6, "ch3 nodes are prefixed ch3_");
        }
        for (String id : List.of(StormChapter.LORE_BURNT_PACK, StormChapter.LORE_TABLETS, StormChapter.LORE_SIZE)) {
            LoreEntry e = LoreManager.catalog().entry(id);
            check(f, e != null && e.chapter().equals("titan_storm") && e.unlockable(), "lore entry " + id + " should exist in titan_storm");
        }
    }

    // ---------------------------------------------------------------- 日英の文
    private static JsonObject lang(String code, List<String> f) {
        try (InputStream in = StormChapterSelfTest.class.getResourceAsStream("/assets/skyabyss_ch3/lang/" + code + ".json")) {
            if (in == null) {
                f.add("missing lang " + code);
                return new JsonObject();
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("could not read lang " + code + ": " + e);
            return new JsonObject();
        }
    }

    private static void texts(List<String> f) {
        List<String> keys = new ArrayList<>();
        for (int i = 1; i <= StormSites.TABLETS; i++) {
            for (int j = 1; j <= 4; j++) {
                keys.add("sign.skyabyss.ch3_wind_" + i + "." + j);
            }
        }
        for (String m : List.of("burn_line", "low_beasts", "low_plain", "wing_flash", "vane", "ruin", "pack", "showcase_1", "showcase_2", "showcase_3", "aftermath")) {
            keys.add("message.skyabyss.ch3." + m);
        }
        keys.add("chapter.skyabyss.aftermath.titan_storm");
        for (String id : List.of("storm_burnt_pack", "storm_wind_tablets", "storm_size")) {
            keys.add("lore.skyabyss.chapter1." + id + ".title");
            keys.add("lore.skyabyss.chapter1." + id + ".body");
        }
        for (String q : List.of("ch3/sub_wind_marks", "ch3/sub_isana_wait")) {
            QuestDef def = QuestManager.quest(q);
            if (def == null) {
                continue;
            }
            keys.add(def.titleKey());
            keys.add(def.summaryKey());
            keys.add(def.doneKey());
            for (QuestDef.Stage s : def.stages()) {
                keys.add(def.journalKey(s));
                def.stages().forEach(st -> st.objectives().forEach(o -> keys.add(def.objectiveKey(st, o))));
            }
        }
        Dialogue d = DialogueManager.dialogue("skyabyss:whale_chaser");
        if (d != null) {
            for (Dialogue.Node n : d.nodes().values()) {
                if (n.id().startsWith("ch3_")) {
                    keys.add(n.textKey());
                    n.options().forEach(o -> keys.add(o.textKey()));
                }
            }
        }
        for (String code : List.of("ja_jp", "en_us")) {
            JsonObject l = lang(code, f);
            for (String k : keys) {
                check(f, l.has(k) && !l.get(k).getAsString().isBlank(), code + ": missing text " + k);
            }
            for (int i = 1; i <= StormSites.TABLETS; i++) {
                for (int j = 1; j <= 4; j++) {
                    String k = "sign.skyabyss.ch3_wind_" + i + "." + j;
                    if (l.has(k)) {
                        String t = l.get(k).getAsString();
                        check(f, SignFit.approxWidth(t) <= SignFit.MAX_LINE_WIDTH, code + ": sign line too wide: " + k + " = " + t);
                        check(f, !code.equals("ja_jp") || t.length() <= 8, code + ": sign line over 8 characters: " + k);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 段階 2 だけ
    private static void stageTwoOnly(MinecraftServer server, List<String> f) {
        FakePlayer p = fake(server, "stage");
        ProgressData data = ProgressData.get(server);
        ArcData arc = ArcData.of(p);
        arc.reset();
        try {
            data.markPersonal(p.getUUID(), Titans.STONE_WHALE);
            check(f, Chapters.of(p) == Chapters.TIDE, "stage 1 is chapter 2");
            check(f, Foreshadow.next(p, Chapters.of(p), 100000, 6000) == null || Foreshadow.next(p, Chapters.of(p), 100000, 6000).chapter() != Chapters.STORM,
                    "a tide-chapter player gets no storm beat");
            data.markPersonal(p.getUUID(), Titans.TIDE_SHELL);
            check(f, Chapters.of(p) == Chapters.STORM, "stage 2 is chapter 3");
            Beat first = Foreshadow.next(p, Chapters.STORM, 100000, 6000);
            check(f, first != null && first.name().equals(StormForeshadow.BURN_LINE), "a storm-chapter player gets the burn line by day first: " + first);
            // 他の章の枠 (一章の拍など) は条件しだいで起きうるので「null」とは言えない。嵐の拍が混ざらないことだけを確かめる
            for (Chapters other : List.of(Chapters.WHALE, Chapters.TIDE, Chapters.EMBER)) {
                Beat got = Foreshadow.next(p, other, 100000, 6000);
                check(f, got == null || got.chapter() != Chapters.STORM, "storm beats do not fire when the current chapter is another one: " + other);
            }
            check(f, Foreshadow.count(p, Chapters.STORM) == 0, "nothing was recorded for other chapters");
            data.markPersonal(p.getUUID(), Titans.THUNDER_WING);
            check(f, Chapters.of(p) == Chapters.EMBER, "stage 3 is chapter 4");
            Beat after = Foreshadow.next(p, Chapters.of(p), 100000, 6000);
            check(f, after == null || after.chapter() != Chapters.STORM, "a player who has stopped the thunder wing no longer gets storm beats");
        } finally {
            data.clearPersonal(p.getUUID(), Titans.THUNDER_WING);
            data.clearPersonal(p.getUUID(), Titans.TIDE_SHELL);
            data.clearPersonal(p.getUUID(), Titans.STONE_WHALE);
            arc.reset();
        }
    }

    // ---------------------------------------------------------------- 焼けた筋
    private static void burn(ServerLevel level, MinecraftServer server, List<String> f) {
        net.minecraft.util.RandomSource rnd = level.random;
        // 縦・横・斜めの三本。どれも筋の列が一直線 (中心線からの距離 ≤ 0.8) で、焼けるのは自然の地面だけ
        double[][] headings = {{1, 0}, {0, 1}, {Math.sqrt(0.5), Math.sqrt(0.5)}};
        int x0 = CX - 20;
        int z0 = CZ - 20;
        for (int h = 0; h < headings.length; h++) {
            double ux = headings[h][0];
            double uz = headings[h][1];
            BlockPos start = new BlockPos(x0, Y, z0 + h * 12);
            // 人の建物: 筋の途中に板を一つ置く (列から外れる)
            double scale = 1.0 / Math.max(Math.abs(ux), Math.abs(uz));
            BlockPos planks = new BlockPos(start.getX() + (int) Math.round(ux * 10 * scale), Y, start.getZ() + (int) Math.round(uz * 10 * scale));
            level.setBlock(planks, Blocks.OAK_PLANKS.defaultBlockState(), 2);
            List<BlockPos> tops = StormForeshadow.line(level, start.below(), ux, uz, StormForeshadow.LINE_LENGTH);
            // 板の列は自然の地面ではない
            level.setBlock(planks, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            check(f, tops.size() >= StormForeshadow.LINE_LENGTH - 3, "heading " + h + ": enough columns for a streak (" + tops.size() + ")");
            for (BlockPos t : tops) {
                double along = (t.getX() - start.getX()) * ux + (t.getZ() - start.getZ()) * uz;
                double off = Math.abs(-(t.getX() - start.getX()) * uz + (t.getZ() - start.getZ()) * ux);
                check(f, off <= 0.75 && along >= -0.8 && along <= StormForeshadow.LINE_LENGTH * 1.5, "heading " + h + ": column off the line " + t);
            }
            Set<BlockPos> logsBefore = logs(level);
            int placed = StormForeshadow.lay(level, tops, ux, uz, rnd);
            check(f, placed == tops.size(), "every listed column is scorched");
            for (BlockPos t : tops) {
                check(f, StormForeshadow.isCharred(level.getBlockState(t)), "heading " + h + ": scorched column " + t);
            }
            Set<BlockPos> fresh = logs(level);
            fresh.removeAll(logsBefore);
            Direction.Axis want = Math.abs(ux) >= Math.abs(uz) ? Direction.Axis.X : Direction.Axis.Z;
            for (BlockPos l : fresh) {
                check(f, level.getBlockState(l).getValue(RotatedPillarBlock.AXIS) == want, "heading " + h + ": a fallen log lies along the streak");
            }
            check(f, fresh.size() % 3 == 0 && fresh.size() <= 9, "heading " + h + ": fallen logs come in threes, up to three trunks (" + fresh.size() + ")");
        }
        // 人が置いた物は焦がさない (板を直接置いた列は natural でない)
        BlockPos pl = new BlockPos(CX + 30, Y, CZ + 30);
        level.setBlock(pl, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        List<BlockPos> tops = StormForeshadow.line(level, pl.below(), 1, 0, 6);
        check(f, tops.stream().noneMatch(t -> t.equals(pl)) && tops.size() == 5, "a player-placed block is never a streak column: " + tops.size());
        level.setBlock(pl, Blocks.GRASS_BLOCK.defaultBlockState(), 2);

        // 人の前に本当に置ける (burnLine): 台の真ん中の人が、前方に筋を作る
        FakePlayer p = fake(server, "burn");
        p.setPos(CX + 0.5, Y + 1, CZ + 0.5);
        p.setYRot(0f);
        int before = countCharred(level);
        boolean fired = StormForeshadow.burnLine(p);
        int after = countCharred(level);
        check(f, fired && after - before >= StormForeshadow.LINE_MIN_COLUMNS, "burnLine lays a streak in front of the player (" + (after - before) + ")");
    }

    private static Set<BlockPos> logs(ServerLevel level) {
        Set<BlockPos> out = new java.util.HashSet<>();
        for (int x = CX - HALF; x <= CX + HALF; x++) {
            for (int z = CZ - HALF; z <= CZ + HALF; z++) {
                BlockPos p = new BlockPos(x, Y + 1, z);
                if (level.getBlockState(p).is(Blocks.DARK_OAK_LOG)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    private static int countCharred(ServerLevel level) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = CX - HALF; x <= CX + HALF; x++) {
            for (int z = CZ - HALF; z <= CZ + HALF; z++) {
                if (StormForeshadow.isCharred(level.getBlockState(p.set(x, Y, z)))) {
                    n++;
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 伏せる獣と稲光
    private static void beasts(ServerLevel level, MinecraftServer server, List<String> f) {
        FakePlayer p = fake(server, "beasts");
        p.setPos(CX + 0.5, Y + 1, CZ - 40.5);
        Cow cow = EntityType.COW.create(level);
        cow.moveTo(CX + 4.5, Y + 1, CZ - 40.5);
        level.addFreshEntity(cow);
        try {
            check(f, StormForeshadow.lowBeasts(p), "the low beasts beat fires");
            check(f, cow.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "an animal nearby is held still");
            cow.discard();
            check(f, StormForeshadow.lowBeasts(p), "the beat fires even without animals (only thunder)");
        } finally {
            cow.discard();
        }
        check(f, jp.revati.skyabyss.titan.storm.StormKeeper.KEEPER.current(level) != null || !StormForeshadow.wingFlash(p),
                "the wing flash waits when there is no titan");
    }

    // ---------------------------------------------------------------- 風見の向き
    private static void vane(ServerLevel level, List<String> f) {
        BlockPos top = new BlockPos(CX + 40, Y + 6, CZ + 40);
        // 柱の頭の位置 (空中)。矢は同じ高さに広がる
        double[] degrees = {0, 45, 90, 135, 180, 225, 270, 315, 20, 100, 200, 333};
        for (double deg : degrees) {
            double a = Math.toRadians(deg);
            StormVane.orient(level, top, a);
            double read = StormVane.readBearing(level, top);
            check(f, !Double.isNaN(read) && StormVane.diff(read, a) <= Math.toRadians(25), "vane at " + deg + " deg reads " + Math.toDegrees(read));
            check(f, !StormVane.orient(level, top, a), "the same bearing changes nothing");
        }
        // 古い矢は消える
        StormVane.orient(level, top, 0);
        StormVane.orient(level, top, Math.PI);
        int rods = 0;
        for (int dx = -StormVane.ARM; dx <= StormVane.ARM; dx++) {
            for (int dz = -StormVane.ARM; dz <= StormVane.ARM; dz++) {
                if ((dx != 0 || dz != 0) && level.getBlockState(top.offset(dx, 0, dz)).is(Blocks.LIGHTNING_ROD)) {
                    rods++;
                    check(f, dx < 0, "after turning west, no rod remains on the east side");
                }
            }
        }
        check(f, rods == StormVane.ARM, "the arrow has " + StormVane.ARM + " rods (" + rods + ")");
        // 人が置いたブロックは触らない
        BlockPos foreign = top.offset(2, 0, 0);
        level.setBlock(foreign, Blocks.OAK_PLANKS.defaultBlockState(), 2);
        StormVane.orient(level, top, 0);
        check(f, level.getBlockState(foreign).is(Blocks.OAK_PLANKS), "a block a player placed on the arrow's path is left alone");
        // 巨神の方角 / 巨神がいない日は風に逆らう
        check(f, StormVane.diff(StormVane.bearingTo(top, new net.minecraft.world.phys.Vec3(top.getX() + 500, 100, top.getZ())), 0) < 0.01
                && StormVane.diff(StormVane.bearingTo(top, new net.minecraft.world.phys.Vec3(top.getX(), 100, top.getZ() - 500)), -Math.PI / 2) < 0.01, "bearing to a titan");
        for (long day = 0; day < 24; day++) {
            check(f, Math.abs(StormVane.diff(StormVane.defaultBearing(day), StormVane.windAngle(day)) - Math.PI) < 1e-9, "without a titan the vane points against the wind");
        }
        for (int dx = -StormVane.ARM; dx <= StormVane.ARM; dx++) {
            for (int dz = -StormVane.ARM; dz <= StormVane.ARM; dz++) {
                level.setBlock(top.offset(dx, 0, dz), Blocks.AIR.defaultBlockState(), 2);
            }
        }
    }

    // ---------------------------------------------------------------- 影と巨神の見せ場
    private static void showcase(MinecraftServer server, List<String> f) {
        double reach = SkyPass.REACH;
        // 条件は全部そろって初めて
        check(f, StormShowcase.witnessing(true, false, false, true, true, 60, 200, true, reach - 1, true, false), "all conditions met: the showcase starts");
        check(f, !StormShowcase.witnessing(false, false, false, true, true, 60, 200, true, 10, true, false), "only in the storm chapter");
        check(f, !StormShowcase.witnessing(true, true, false, true, true, 60, 200, true, 10, true, false), "only once");
        check(f, !StormShowcase.witnessing(true, false, true, true, true, 60, 200, true, 10, true, false), "not while already running");
        check(f, !StormShowcase.witnessing(true, false, false, false, true, 60, 200, true, 10, true, false), "needs the sky shadow");
        check(f, !StormShowcase.witnessing(true, false, false, true, false, 60, 200, true, 10, true, false), "needs the titan cowering");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, StormShowcase.COWER_SETTLE_TICKS - 1, 200, true, 10, true, false), "the titan must have settled");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, 60, StormShowcase.WITNESS_RANGE + 1, true, 10, true, false), "the titan must be close enough to see");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, 60, 200, false, 10, true, false), "needs a line of sight to the titan");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, 60, 200, true, reach + 1, true, false), "the shadow must be near");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, 60, 200, true, 10, false, false), "needs the sky");
        check(f, !StormShowcase.witnessing(true, false, false, true, true, 60, 200, true, 10, true, true), "not during a cinematic");
        // 確実に見せる: 予兆 3 拍目のあと、昼、巡航、120〜360 ブロック、10 秒
        long dwell = StormShowcase.DIRECTED_DWELL_TICKS;
        check(f, StormShowcase.directedReady(true, false, true, false, true, false, true, 200, true, dwell), "directed: all conditions met");
        check(f, !StormShowcase.directedReady(true, false, false, false, true, false, true, 200, true, dwell), "directed: only after the third beat (wing flash)");
        check(f, !StormShowcase.directedReady(true, false, true, true, true, false, true, 200, true, dwell), "directed: not while a shadow is already passing");
        check(f, !StormShowcase.directedReady(true, false, true, false, false, false, true, 200, true, dwell), "directed: by day");
        check(f, !StormShowcase.directedReady(true, false, true, false, true, true, true, 200, true, dwell), "directed: not in rain");
        check(f, !StormShowcase.directedReady(true, false, true, false, true, false, false, 200, true, dwell), "directed: the titan must be cruising");
        check(f, !StormShowcase.directedReady(true, false, true, false, true, false, true, StormShowcase.DIRECTED_MIN - 1, true, dwell), "directed: not too near");
        check(f, !StormShowcase.directedReady(true, false, true, false, true, false, true, StormShowcase.DIRECTED_MAX + 1, true, dwell), "directed: not too far");
        check(f, !StormShowcase.directedReady(true, false, true, false, true, false, true, 200, true, dwell - 1), "directed: ten seconds");
        check(f, !StormShowcase.directedReady(true, true, true, false, true, false, true, 200, true, dwell), "directed: not once it has been seen");
        // 一度だけ: 記録 (伝承) のあとは見せ場が起きない
        FakePlayer p = fake(server, "showcase");
        try {
            check(f, !StormShowcase.done(p), "not seen yet");
            check(f, StormShowcase.begin(p) && !StormShowcase.begin(p), "begin runs once while it is running");
            StormShowcase.finish(p);
            check(f, StormShowcase.done(p), "the lore entry marks the showcase as seen");
            check(f, !StormShowcase.begin(p), "never again after the record");
            check(f, !StormShowcase.witnessing(true, StormShowcase.done(p), false, true, true, 60, 200, true, 10, true, false), "the condition refuses after the record");
        } finally {
            Lore.lock(p, StormChapter.LORE_SIZE);
            StormShowcase.forgetAll();
        }
    }

    // ---------------------------------------------------------------- 章の余韻
    private static void aftermath(MinecraftServer server, List<String> f) {
        FakePlayer p = fake(server, "aftermath");
        ProgressData data = ProgressData.get(server);
        ArcData arc = ArcData.of(p);
        arc.reset();
        try {
            check(f, !StormChapter.aftermath(p), "no aftermath before the thunder wing is stopped");
            data.markPersonal(p.getUUID(), Titans.THUNDER_WING);
            check(f, StormChapter.aftermath(p) && arc.aftermathShown(Chapters.STORM), "the aftermath card is delivered once the heart is stopped");
            check(f, !StormChapter.aftermath(p), "...and only once");
        } finally {
            data.clearPersonal(p.getUUID(), Titans.THUNDER_WING);
            arc.reset();
        }
    }

    // ---------------------------------------------------------------- 本物の世界で小要素を置く (章が開く → 建つ → 仕上げ → 着いたときの合図でクエストが済む)
    private static final UUID SITE_ID = UUID.nameUUIDFromBytes("skyabyss_storm_chapter_test_sites".getBytes());
    private static int siteWait;
    private static BlockPos siteBase;

    public static List<Step> siteSteps() {
        List<Step> steps = new ArrayList<>();
        steps.add(Step.once("storm chapter (sites start)", StormChapterSelfTest::sitesStart));
        steps.add(Step.until("storm chapter (sites wait)", StormChapterSelfTest::sitesBuilt));
        steps.add(Step.once("storm chapter (sites)", StormChapterSelfTest::sitesCheck));
        return steps;
    }

    private static FakePlayer siteFake(MinecraftServer server) {
        return FakePlayerFactory.get(server.overworld(), new GameProfile(SITE_ID, "skyabyss_ch3_sites"));
    }

    private static void sitesStart(MinecraftServer server) {
        siteWait = 0;
        FakePlayer p = siteFake(server);
        siteBase = server.overworld().getSharedSpawnPos();
        p.setPos(siteBase.getX() + 0.5, siteBase.getY(), siteBase.getZ() + 0.5);
        ChapterSites.open(p, Chapters.STORM);
    }

    private static int expectedSites() {
        int n = 0;
        for (ChapterSites.Element e : ChapterSites.elements(Chapters.STORM)) {
            n += e.rules().count();
        }
        return n;
    }

    private static List<ChapterSites.Site> mySites(MinecraftServer server) {
        List<ChapterSites.Site> out = new ArrayList<>();
        for (String id : StormSites.ELEMENTS) {
            out.addAll(ChapterSites.sitesFor(server, SITE_ID, id));
        }
        return out;
    }

    private static boolean sitesBuilt(MinecraftServer server) {
        siteWait++;
        List<ChapterSites.Site> sites = mySites(server);
        return siteWait > 9000 || (siteWait > 100 && !sites.isEmpty() && sites.stream().allMatch(ChapterSites.Site::built));
    }

    private static void sitesCheck(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            ServerLevel level = server.overworld();
            FakePlayer p = siteFake(server);
            List<ChapterSites.Site> sites = mySites(server);
            int want = expectedSites();
            check(f, sites.size() == want, "every element was placed for the storm-chapter player (" + sites.size() + " of " + want + ")");
            for (ChapterSites.Site s : sites) {
                check(f, s.built(), s.id() + "#" + s.index() + " was built");
                check(f, Math.hypot(s.pos().getX() - siteBase.getX(), s.pos().getZ() - siteBase.getZ()) <= 150.5, s.id() + " is within 150 blocks of the base");
            }
            // 仕上げ: 風見の矢・樽の中身・碑の文
            int vanes = 0;
            int tablets = 0;
            int packs = 0;
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (SiteData.Entry e : SiteData.get(level).allEntries()) {
                if (!e.built) {
                    continue;
                }
                BlockPos at = e.pos;
                if (e.siteId.equals(StormSites.VANE) && e.extra("top") != null) {
                    vanes++;
                    double read = StormVane.readBearing(level, e.extra("top"));
                    double target = StormVane.defaultBearing(level.getDayTime() / 24000L);
                    check(f, !Double.isNaN(read) && StormVane.diff(read, target) <= Math.toRadians(25),
                            "the built vane points against the wind (" + Math.toDegrees(read) + " vs " + Math.toDegrees(target) + ")");
                    check(f, level.getBlockState(e.extra("top")).is(Blocks.LIGHTNING_ROD), "the vane has its head rod");
                }
                if (e.siteId.equals(StormSites.TABLET)) {
                    for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 4, 3))) {
                        if (level.getBlockEntity(q) instanceof TabletBlockEntity t && t.key().startsWith("sign.skyabyss.ch3_wind_")) {
                            tablets++;
                            keys.add(t.key());
                            check(f, t.key().equals(StormSites.tabletKey(e.index)), "tablet " + e.index + " carries its own text key: " + t.key());
                        }
                    }
                }
                if (e.siteId.equals(StormSites.PACK)) {
                    for (BlockPos q : BlockPos.betweenClosed(at.offset(-3, -2, -3), at.offset(3, 4, 3))) {
                        if (level.getBlockEntity(q) instanceof BarrelBlockEntity b && b.getItem(2).is(net.minecraft.world.item.Items.LIGHTNING_ROD)) {
                            packs++;
                        }
                    }
                }
            }
            check(f, vanes >= 1 && packs >= 1 && tablets == StormSites.TABLETS && keys.size() == StormSites.TABLETS,
                    "finished: vane " + vanes + ", pack barrels " + packs + ", tablets " + tablets + " (keys " + keys + ")");
            // 重複しない: 要素ごとに決まった数だけ。別の人・再ログイン (開き直し) でも増えず、風見は何度向きを直しても針が増えない
            for (ChapterSites.Element e : ChapterSites.elements(Chapters.STORM)) {
                int n = 0;
                for (SiteData.Entry en : SiteData.get(level).allEntries()) {
                    if (en.siteId.equals(e.id())) {
                        n++;
                    }
                }
                check(f, n == e.rules().count(), e.id() + " exists " + n + " times (expected " + e.rules().count() + ")");
            }
            FakePlayer p2 = FakePlayerFactory.get(level, new GameProfile(UUID.nameUUIDFromBytes("skyabyss_storm_chapter_test_sites_2".getBytes()), "skyabyss_ch3_sites2"));
            p2.setPos(siteBase.getX() + 70.5, siteBase.getY(), siteBase.getZ() + 60.5);
            check(f, !ChapterSites.open(p, Chapters.STORM) && !ChapterSites.open(p, Chapters.STORM), "logging in again plans nothing new");
            ChapterSites.open(p2, Chapters.STORM);
            for (SiteData.Entry en : SiteData.get(level).allEntries()) {
                if (en.siteId.equals(StormSites.VANE) && en.built && en.extra("top") != null) {
                    BlockPos top = en.extra("top");
                    for (int deg : new int[] {45, 200, 315, 135, 90, 180, 0}) {
                        StormVane.orient(level, top, Math.toRadians(deg));
                    }
                    int rods = 0;
                    int bars = 0;
                    for (int dx = -StormVane.ARM; dx <= StormVane.ARM; dx++) {
                        for (int dz = -StormVane.ARM; dz <= StormVane.ARM; dz++) {
                            BlockState bs = level.getBlockState(top.offset(dx, 0, dz));
                            if ((dx != 0 || dz != 0) && bs.is(Blocks.LIGHTNING_ROD)) {
                                rods++;
                            }
                            if (bs.is(Blocks.IRON_BARS)) {
                                bars++;
                            }
                        }
                    }
                    check(f, rods == StormVane.ARM && bars == 1, "after several re-aims the vane has " + rods + " rods and " + bars + " tail bars");
                }
            }
            // 着いたときの知らせ → クエスト「風の跡」が済む (合図の名前と、段の目的が合っている)
            if (QuestLog.of(p).entry("ch3/sub_wind_marks") == null) {
                Quests.start(p, "ch3/sub_wind_marks");
            }
            for (ChapterSites.Element e : ChapterSites.elements(Chapters.STORM)) {
                for (ChapterSites.Site s : sites) {
                    if (s.id().equals(e.id()) && e.onArrive() != null) {
                        e.onArrive().arrive(p, s);
                    }
                }
            }
            Quests.poll(p, true);
            QuestLog.Entry done = QuestLog.of(p).entry("ch3/sub_wind_marks");
            check(f, done != null && done.done, "arriving at the four kinds of sites completes the quest 'Marks of the Wind'");
            check(f, Lore.state(p).has(StormChapter.LORE_BURNT_PACK) && Lore.state(p).has(StormChapter.LORE_TABLETS), "arriving writes the pack page and the tablets entry");
            Lore.lock(p, StormChapter.LORE_BURNT_PACK);
            Lore.lock(p, StormChapter.LORE_TABLETS);
            Quests.forget(p, "ch3/sub_wind_marks");
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST storm chapter (sites) crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST storm chapter (sites) PASSED (placed within 150 blocks, built, vane arrow, tablet keys, pack barrel, arrival signals finish the quest)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST storm chapter (sites) FAILED: {}", m));
        }
    }
}
