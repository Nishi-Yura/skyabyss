package jp.revati.skyabyss.titan.ember;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LorePageItem;
import jp.revati.skyabyss.npc.Dialogue;
import jp.revati.skyabyss.npc.DialogueManager;
import jp.revati.skyabyss.opening.site.Blueprint;
import jp.revati.skyabyss.opening.site.ChapterSites;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestManager;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.rpg.GrowthRules;
import jp.revati.skyabyss.sky.ChapterSky;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.StoryData;
import jp.revati.skyabyss.story.arc.ArcData;
import jp.revati.skyabyss.story.arc.Beat;
import jp.revati.skyabyss.story.arc.Foreshadow;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 四章 (余燼。P4d) の自己テスト。結果はログに SELFTEST ember chapter PASSED / FAILED。
//   - 登録: 予兆 3 拍 (章・順番)、小要素 3 つ、空の段、部品 (Blueprint)、データ (クエスト・会話・伝承・文の日英)
//   - 予兆が段階 3 でだけ出る (段階 2・段階 4 には出ない)。順番どおり、一度ずつ。3 拍目は巨神の尾根が近くにあるときだけ
//   - 小要素の経験値と伝承は一度だけ。経験値の合計が 1 レベル分の 1.5 倍以内
//   - 夜の浮上が、プレイヤーの建物を一つも変えない (ブロックの状態・箱の中身が浮上の前後で同じ)。ワールドで一度だけ。段階 3 の人にだけ語りと経験値
//   - 余韻: 四章の札 → 語り部の一言 → (鍵がそろっていれば) 終わりの合図の順。鍵の場面のあいだは待つ。段階 4 になるまで何も進まない
public final class EmberChapterSelfTest {
    // 岩鯨 (4000, 4000)・潮殻 (-4000, 4000)・余燼の地上 (4000, -4000) の自己テストと重ならない所
    private static final int TEST_X = 4000;
    private static final int TEST_Z = -4800;
    private static final int PLATFORM_Y = 200;
    private static final int HALF = 40;
    private static final List<String> TITANS = List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING, Titans.EMBER);

    private EmberChapterSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember chapter (load)", EmberChapterSelfTest::load),
                SelfTestRunner.Step.once("ember chapter", 40, EmberChapterSelfTest::run));
    }

    private static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = -3; dx <= 2; dx++) {
            for (int dz = -3; dz <= 2; dz++) {
                level.setChunkForced((TEST_X >> 4) + dx, (TEST_Z >> 4) + dz, true);
            }
        }
    }

    private static void unload(ServerLevel level) {
        for (int dx = -3; dx <= 2; dx++) {
            for (int dz = -3; dz <= 2; dz++) {
                level.setChunkForced((TEST_X >> 4) + dx, (TEST_Z >> 4) + dz, false);
            }
        }
    }

    private static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        EmberChapterData data = EmberChapterData.get(server);
        CompoundTag savedChapter = data.snapshot(level.registryAccess());
        StoryData story = StoryData.get(server);
        CompoundTag savedStory = story.snapshot(level.registryAccess());
        ProgressData progress = ProgressData.get(server);
        List<FakePlayer> fakes = new ArrayList<>();
        try {
            checkRegistration(f);
            checkData(f);
            checkRising(server, level, data, progress, fakes, f);
            checkBeats(server, level, progress, fakes, f);
            checkSites(server, level, progress, fakes, f);
            checkAftermath(server, level, progress, fakes, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember chapter crashed", e);
            f.add("crashed: " + e);
        } finally {
            EmberTitanEntity t = EmberKeeper.KEEPER.current(level);
            if (t != null) {
                t.discard();
            }
            EmberKeeper.KEEPER.releaseTicket(level);
            for (FakePlayer p : fakes) {
                SkyKey.reset(p);
                EmberAftermath.reset(p);
                for (String t2 : TITANS) {
                    progress.clearPersonal(p.getUUID(), t2);
                }
            }
            data.restore(savedChapter, level.registryAccess());
            story.restore(savedStory, level.registryAccess());
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember chapter PASSED (registration, data and lang, beats only at stage 3 and in order, sites once with a small xp budget, "
                    + "the rising changes no block of a player build and happens once, aftermath card then teller then end after the key)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember chapter FAILED: {}", x));
        }
    }

    private static void expect(boolean ok, String failure, List<String> f) {
        if (!ok) {
            f.add(failure);
        }
    }

    private static FakePlayer fake(ServerLevel level, List<FakePlayer> fakes, String name, int stage) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("skyabyss_ember_ch4_" + name).getBytes()), "ember_ch4_" + name));
        fakes.add(p);
        ProgressData progress = ProgressData.get(level.getServer());
        for (int i = 0; i < stage; i++) {
            progress.markPersonal(p.getUUID(), TITANS.get(i));
        }
        return p;
    }

    // ---------------------------------------------------------------- 登録
    private static void checkRegistration(List<String> f) {
        List<Beat> beats = Foreshadow.beats(Chapters.EMBER);
        expect(beats.size() == 3, "the ember chapter should have 3 foreshadowing beats, found " + beats.size(), f);
        for (int i = 0; i < EmberChapter.BEATS.size(); i++) {
            Beat b = Foreshadow.beat(EmberChapter.BEATS.get(i));
            expect(b != null && b.chapter() == Chapters.EMBER && b.order() == i + 1, "beat " + EmberChapter.BEATS.get(i) + " is missing or misplaced", f);
        }
        List<String> sites = new ArrayList<>();
        ChapterSites.elements(Chapters.EMBER).forEach(e -> sites.add(e.id()));
        // 四章の地上の場所 (story/ch4/Ch4Ground の 5 種) も同じ章の要素なので、数の上限は見ない (この 3 つがそろっていること)
        expect(sites.containsAll(EmberChapter.SITES) && sites.size() >= 3, "the ember small elements are not registered: " + sites, f);
        expect(ChapterSky.stage(Chapters.EMBER).equals(EmberChapter.SKY) && EmberChapter.SKY.moonBias() > 0 && EmberChapter.SKY.tintStrength() <= 0.35f,
                "the ember sky stage is not registered", f);
        for (String name : List.of("ember_hearth_ruin", "ember_ash_post", "ember_warm_ledge")) {
            Blueprint bp = Blueprint.get(name);
            expect(!bp.cells.isEmpty() && bp.rx1 - bp.rx0 <= 6 && bp.rz1 - bp.rz0 <= 6, "blueprint " + name + " is empty or too big", f);
            if (!name.equals("ember_ash_post")) {
                expect(bp.marks.containsKey("barrel"), "blueprint " + name + " has no barrel mark", f);
            }
        }
        // 経験値の合計 (小要素 + 浮上 + クエスト) が、着いたときの 1 レベル分の 1.5 倍以内 (01 の 4.1)
        QuestDef quest = QuestManager.quest("ch4/fragments");
        int total = EmberChapter.SITE_XP + EmberChapter.POST_XP + EmberChapter.LEDGE_XP + EmberRising.WITNESS_XP + (quest == null ? 0 : quest.reward().xp());
        int level = Chapters.EMBER.levelFrom();
        expect(total <= GrowthRules.xpToNext(level) * 3 / 2, "the ember chapter xp (" + total + ") is over 1.5 levels at level " + level, f);
    }

    // ---------------------------------------------------------------- データ・文
    private static void checkData(List<String> f) {
        QuestDef quest = QuestManager.quest("ch4/fragments");
        expect(quest != null && "titan_ember".equals(quest.chapter()) && quest.stages().size() == 2 && quest.minStage() == 3,
                "quest ch4/fragments is missing or wrong", f);
        Dialogue teller = DialogueManager.dialogue("skyabyss:storyteller");
        Dialogue isana = DialogueManager.dialogue("skyabyss:whale_chaser");
        expect(teller != null && teller.nodes().containsKey("ch4_s_one") && teller.nodes().containsKey("ch4_s_three"), "the storyteller's ch4 nodes did not load", f);
        expect(isana != null && isana.nodes().containsKey("ch4_i_one") && isana.nodes().containsKey("ch4_i_three"), "Isana's ch4 nodes did not load", f);
        expect(DialogueManager.loadErrors().isEmpty(), "dialogue load errors: " + DialogueManager.loadErrors(), f);
        for (String id : List.of(EmberChapter.LORE_HEARTH, EmberChapter.LORE_PREV_PAGE)) {
            expect(jp.revati.skyabyss.lore.LoreManager.catalog().entry(id) != null, "lore entry missing: " + id, f);
        }
        List<String> keys = new ArrayList<>(List.of(EmberChapter.LINE_GROUND, EmberChapter.LINE_HUM, EmberChapter.LINE_RIDGE, EmberChapter.LINE_HEARTH,
                EmberChapter.LINE_POST, EmberChapter.LINE_LEDGE, EmberChapter.LINE_BASE, EmberRising.LINE_RISE, EmberAftermath.LINE_TELLER,
                EmberAftermath.LINE_END, "chapter.skyabyss.aftermath.titan_ember"));
        for (String q : List.of("title", "summary", "done", "teller.journal", "teller.talk", "isana.journal", "isana.talk")) {
            keys.add("quest.skyabyss.ch4.fragments." + q);
        }
        keys.add("dialogue.skyabyss.whale_chaser.c_menu.ch4");
        keys.add("item.skyabyss.lore_page.desc.ember_hearth");
        for (String n : List.of("ch4_s_one", "ch4_s_two", "ch4_s_three", "ch4_s_three.ok")) {
            keys.add("dialogue.skyabyss.storyteller." + n);
        }
        for (String n : List.of("ch4_i_one", "ch4_i_two", "ch4_i_three", "ch4_i_three.ok")) {
            keys.add("dialogue.skyabyss.whale_chaser." + n);
        }
        for (String lang : List.of("ja_jp", "en_us")) {
            JsonObject ch4 = json("/assets/skyabyss_ch4/lang/" + lang + ".json", f);
            JsonObject ember = json("/assets/skyabyss_ember/lang/" + lang + ".json", f);
            if (ch4 == null || ember == null) {
                continue;
            }
            for (String k : keys) {
                expect(ch4.has(k) && !ch4.get(k).getAsString().isBlank(), lang + ": missing text " + k, f);
            }
            for (String id : List.of("ember_hearth", "ember_prev_page")) {
                expect(ember.has("lore.skyabyss.chapter1." + id + ".title") && ember.has("lore.skyabyss.chapter1." + id + ".body"), lang + ": missing lore text " + id, f);
            }
            if (lang.equals("ja_jp")) {
                // 言いすぎない: 正体の名は出さない (01 の 7、13 の 12.6)
                for (String k : ch4.keySet()) {
                    for (String bad : List.of("見る者", "留まる者", "深淵", "巡礼者")) {
                        expect(!ch4.get(k).getAsString().contains(bad), "ch4 text says too much (" + bad + "): " + k, f);
                    }
                }
            }
        }
    }

    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = EmberChapterSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing resource " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            f.add("could not read " + path + ": " + e);
            return null;
        }
    }

    // ---------------------------------------------------------------- 夜の浮上
    private static void checkRising(MinecraftServer server, ServerLevel level, EmberChapterData data, ProgressData progress, List<FakePlayer> fakes, List<String> f) {
        // 空に浮かべた平らな台 (地形に左右されない)。heightmap の地表がこの台になる
        for (int x = TEST_X - HALF; x <= TEST_X + HALF; x++) {
            for (int z = TEST_Z - HALF; z <= TEST_Z + HALF; z++) {
                level.setBlock(new BlockPos(x, PLATFORM_Y, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = PLATFORM_Y + 1; y <= PLATFORM_Y + 30; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        data.restore(new CompoundTag(), level.registryAccess());
        expect(!data.risen(), "the test could not reset the rising record", f);
        EmberTitanEntity titan = EmberKeeper.KEEPER.spawn(level, TEST_X, TEST_Z, null);
        expect(titan != null, "could not spawn the ember titan on the platform", f);
        if (titan == null) {
            return;
        }
        double ground = EmberGround.surfaceY(level, TEST_X, TEST_Z);
        expect(Math.abs(ground - (PLATFORM_Y + 1)) < 1e-6, "the platform is not the surface: " + ground, f);
        titan.moveTo(TEST_X, ground - EmberTitanEntity.BURROW_SINK, TEST_Z, 0f, 0f);
        // 巨神の浮かぶ所 (口の前でなく、体の上あたり) に、プレイヤーの建物: 板・ガラス・松明・扉・箱 (中身あり)
        Vec3 c = titan.position().add(EmberTitanEntity.offset(0f, 0, 0, 10));
        int bx = (int) Math.floor(c.x) - 3;
        int bz = (int) Math.floor(c.z) - 3;
        int by = PLATFORM_Y + 1;
        for (int dx = 0; dx < 7; dx++) {
            for (int dz = 0; dz < 7; dz++) {
                level.setBlock(new BlockPos(bx + dx, by, bz + dz), Blocks.OAK_PLANKS.defaultBlockState(), 2);
                boolean wall = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                for (int dy = 1; dy <= 3; dy++) {
                    level.setBlock(new BlockPos(bx + dx, by + dy, bz + dz), wall ? (dy == 2 ? Blocks.GLASS : Blocks.COBBLESTONE).defaultBlockState()
                            : Blocks.AIR.defaultBlockState(), 2);
                }
                level.setBlock(new BlockPos(bx + dx, by + 4, bz + dz), Blocks.OAK_SLAB.defaultBlockState(), 2);
            }
        }
        BlockPos chest = new BlockPos(bx + 2, by + 1, bz + 2);
        level.setBlock(chest, Blocks.CHEST.defaultBlockState(), 2);
        level.setBlock(new BlockPos(bx + 4, by + 1, bz + 4), Blocks.TORCH.defaultBlockState(), 2);
        if (level.getBlockEntity(chest) instanceof ChestBlockEntity be) {
            be.setItem(0, new net.minecraft.world.item.ItemStack(Items.DIAMOND, 7));
        }
        List<BlockState> before = snapshot(level);

        EmberGround.Spot spot = new EmberGround.Spot(TEST_X, TEST_Z, 0f, ground);
        titan.beginApproach(spot, EmberTitanEntity.FORCED_SLEEP);
        expect(!data.risen(), "the rising record was set before the titan rose", f);
        titan.startRise(level);
        expect(data.risen(), "the first rise did not record the mid-chapter event", f);
        for (int i = 0; i < EmberTitanEntity.RISE_TICKS + 20; i++) {
            titan.tick();
        }
        expect(titan.mode() == EmberTitanEntity.Mode.SLEEP, "the titan did not finish rising: " + titan.mode(), f);
        List<BlockState> after = snapshot(level);
        // 伏せる体に食い込む地形 (台の石) は取り除いてよい (titan/TitanClearing。docs/design/33)。人の建物 (板・ガラス・丸石・松明・箱) と空気は変わらない
        int changed = 0;
        int carved = 0;
        for (int i = 0; i < Math.min(before.size(), after.size()); i++) {
            if (before.get(i) != after.get(i)) {
                if (jp.revati.skyabyss.titan.TitanClearing.natural(before.get(i)) && after.get(i).isAir()) {
                    carved++;
                } else {
                    changed++;
                }
            }
        }
        expect(before.size() == after.size() && changed == 0, "the rising changed " + changed + " blocks of the player build area (terrain carved " + carved + ")", f);
        expect(level.getBlockEntity(chest) instanceof ChestBlockEntity be2 && be2.getItem(0).is(Items.DIAMOND) && be2.getItem(0).getCount() == 7,
                "the player's chest changed during the rising", f);
        // 一度だけ
        expect(!EmberRising.onRiseStart(level, titan), "the rising event ran twice in one world", f);
        // 段階 3 の人にだけ語りと経験値 (二度目からは経験値なし)
        FakePlayer s3 = fake(level, fakes, "rise3", 3);
        FakePlayer s2 = fake(level, fakes, "rise2", 2);
        s3.setPos(c.x + 5, ground, c.z);
        s2.setPos(c.x - 5, ground, c.z);
        expect(Chapters.of(s3) == Chapters.EMBER && Chapters.of(s2) == Chapters.STORM, "the test players are not in chapters 4 and 3", f);
        List<ServerPlayer> told = EmberRising.perform(level, titan, List.of(s3, s2));
        expect(told.contains(s3) && !told.contains(s2), "the rising story went to the wrong players: " + told, f);
        expect(!Growth.discover(s3, EmberRising.DISCOVER_KEY), "the stage 3 witness did not get the one-time xp", f);
        expect(Growth.discover(s2, EmberRising.DISCOVER_KEY), "the stage 2 player was given the stage 3 witness xp", f);
        // 段階 4 (心臓を止めたあと) の人にも出さない
        FakePlayer s4 = fake(level, fakes, "rise4", 4);
        s4.setPos(c.x, ground, c.z + 5);
        expect(!EmberRising.perform(level, titan, List.of(s4)).contains(s4), "the rising story went to a player who already stopped the heart", f);
    }

    // 建物の範囲のブロックの状態 (同じオブジェクトなら変わっていない)
    private static List<BlockState> snapshot(ServerLevel level) {
        List<BlockState> out = new ArrayList<>();
        for (int x = TEST_X - 20; x <= TEST_X + 20; x++) {
            for (int z = TEST_Z - 20; z <= TEST_Z + 20; z++) {
                for (int y = PLATFORM_Y - 3; y <= PLATFORM_Y + 25; y++) {
                    out.add(level.getBlockState(new BlockPos(x, y, z)));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 予兆 (段階 3 でだけ)
    private static void checkBeats(MinecraftServer server, ServerLevel level, ProgressData progress, List<FakePlayer> fakes, List<String> f) {
        long t = 1_000_000;
        FakePlayer s3 = fake(level, fakes, "beat3", 3);
        ArcData.of(s3).resetBeats();
        // 昼間・朝・夕方 (Beat.Time) に合わせて順に
        expect(Chapters.of(s3) == Chapters.EMBER, "the stage 3 player is not in the ember chapter", f);
        s3.setPos(TEST_X + 1000, PLATFORM_Y + 1, TEST_Z + 1000);
        Beat first = Foreshadow.tryFire(s3, Chapters.of(s3), t, 0);
        expect(first != null && first.id().equals(EmberChapter.BEATS.get(0)), "the dawn beat did not fire first: " + first, f);
        expect(Foreshadow.tryFire(s3, Chapters.of(s3), t + 10, 0) == null, "a second beat fired with no cooldown", f);
        Beat second = Foreshadow.tryFire(s3, Chapters.of(s3), t + 10_000, 12_000);
        expect(second != null && second.id().equals(EmberChapter.BEATS.get(1)), "the dusk beat did not fire second: " + second, f);
        // 3 拍目: 巨神の尾根が近くにないあいだは見送り、近づけば起こる
        expect(Foreshadow.tryFire(s3, Chapters.of(s3), t + 20_000, 6_000) == null, "the ridge beat fired with no titan ridge nearby", f);
        expect(!Foreshadow.done(s3, EmberChapter.BEATS.get(2)), "the ridge beat was recorded while skipped", f);
        EmberTitanEntity titan = EmberKeeper.KEEPER.current(level);
        if (titan != null) {
            Vec3 ridge = titan.ridgePoint();
            s3.setPos(ridge.x + 40, PLATFORM_Y + 1, ridge.z);
            Beat third = Foreshadow.tryFire(s3, Chapters.of(s3), t + 20_000, 6_000);
            expect(third != null && third.id().equals(EmberChapter.BEATS.get(2)), "the ridge beat did not fire near the titan: " + third, f);
        } else {
            f.add("no ember titan for the ridge beat");
        }
        expect(Foreshadow.count(s3, Chapters.EMBER) == 3, "the three ember beats did not all happen once", f);
        expect(Foreshadow.tryFire(s3, Chapters.of(s3), t + 40_000, 0) == null, "a beat fired twice", f);

        // 段階 2 (雷翼の章): 余燼の拍は出ない。段階 4 (心臓を止めたあと): もう出ない
        FakePlayer s2 = fake(level, fakes, "beat2", 2);
        ArcData.of(s2).resetBeats();
        Beat early = Foreshadow.next(s2, Chapters.of(s2), t + 50_000, 0);
        expect(Chapters.of(s2) == Chapters.STORM && (early == null || !early.id().startsWith(Chapters.EMBER.id() + "/")),
                "a stage 2 player got an ember beat: " + early, f);
        for (Beat b : Foreshadow.beats(Chapters.STORM)) {
            expect(!b.id().startsWith(Chapters.EMBER.id() + "/"), "an ember beat is registered under the storm chapter", f);
        }
        FakePlayer s4 = fake(level, fakes, "beat4", 4);
        ArcData.of(s4).resetBeats();
        expect(Chapters.of(s4) == Chapters.EMBER && Foreshadow.next(s4, Chapters.of(s4), t + 60_000, 0) == null
                && Foreshadow.next(s4, Chapters.of(s4), t + 60_000, 12_000) == null, "a player who already stopped the ember heart got a beat", f);

        // 暮らしの変化: 寝床のまわりだけ温かい (段階 3 の人だけ)
        BlockPos bed = new BlockPos(TEST_X, PLATFORM_Y + 1, TEST_Z);
        FakePlayer near = fake(level, fakes, "base3", 3);
        near.setRespawnPosition(Level.OVERWORLD, bed, 0f, true, false);
        near.setPos(bed.getX() + 3, bed.getY(), bed.getZ());
        expect(EmberChapter.warmBase(near) && near.getPersistentData().getBoolean(EmberChapter.BASE_KEY), "the warm ground at the bed did not show for a stage 3 player", f);
        near.setPos(bed.getX() + 60, bed.getY(), bed.getZ());
        expect(!EmberChapter.warmBase(near), "the warm ground showed far from the bed", f);
        FakePlayer other = fake(level, fakes, "base2", 2);
        other.setRespawnPosition(Level.OVERWORLD, bed, 0f, true, false);
        other.setPos(bed.getX() + 3, bed.getY(), bed.getZ());
        expect(!EmberChapter.warmBase(other), "the warm ground showed for a stage 2 player", f);
    }

    // ---------------------------------------------------------------- 小要素
    private static void checkSites(MinecraftServer server, ServerLevel level, ProgressData progress, List<FakePlayer> fakes, List<String> f) {
        FakePlayer p = fake(level, fakes, "sites", 3);
        EmberChapter.arrive(p, "hearth", EmberChapter.SITE_XP, EmberChapter.LINE_HEARTH, true);
        expect(Lore.state(p).has(EmberChapter.LORE_HEARTH), "arriving at the hearth ruin did not write its lore", f);
        expect(!Growth.discover(p, "ch4:hearth"), "arriving at the hearth ruin did not take its one-time xp", f);
        int xp1 = Growth.get(p).xp + Growth.get(p).level * 100000;
        EmberChapter.arrive(p, "hearth", EmberChapter.SITE_XP, EmberChapter.LINE_HEARTH, true);
        expect(Growth.get(p).xp + Growth.get(p).level * 100000 == xp1, "arriving again gave xp twice", f);
        // 炉跡の樽の頁から、前の私の頁の項目が出る
        List<String> ids = new ArrayList<>();
        for (LoreEntry e : LorePageItem.candidates(p, EmberChapter.PAGE_SOURCE)) {
            ids.add(e.id());
        }
        expect(ids.contains(EmberChapter.LORE_PREV_PAGE), "the hearth page does not lead to its lore entry: " + ids, f);
    }

    // ---------------------------------------------------------------- 余韻
    private static void checkAftermath(MinecraftServer server, ServerLevel level, ProgressData progress, List<FakePlayer> fakes, List<String> f) {
        long now = level.getGameTime();
        // 段階 3 の人には何も進まない (記録も作らない)
        FakePlayer s3 = fake(level, fakes, "after3", 3);
        expect(EmberAftermath.poll(s3) == 0 && !s3.getPersistentData().contains(EmberAftermath.KEY), "the aftermath moved for a stage 3 player", f);

        FakePlayer p = fake(level, fakes, "after4", 4);
        p.setPos(TEST_X, PLATFORM_Y + 1, TEST_Z);
        EmberAftermath.reset(p);
        ArcData.of(p).reset();
        expect(EmberAftermath.poll(p) == 0, "the aftermath card came before a quiet moment on the surface", f);
        CompoundTag st = p.getPersistentData().getCompound(EmberAftermath.KEY);
        st.putLong("since", now - 1000);
        expect(EmberAftermath.poll(p) == 1 && EmberAftermath.cardDone(p), "the chapter 4 aftermath card did not come first", f);
        expect(EmberAftermath.poll(p) == 0, "the teller's line came with no delay", f);
        st.putLong("card_at", now - 1000);
        expect(EmberAftermath.poll(p) == 2 && EmberAftermath.tellerDone(p), "the teller's last line did not come second", f);
        st.putLong("told_at", now - 1000);
        expect(EmberAftermath.poll(p) == 0 && !EmberAftermath.endDone(p), "the arc ending came before the sky key", f);
        // 天の鍵: 場面のあいだは待つ。終わってから合図
        expect(SkyKey.assemble(p, false) && SkyKey.assembled(p), "the sky key was not assembled for the test", f);
        expect(st.contains("key_at"), "the sky key did not tell the aftermath", f);
        expect(EmberAftermath.poll(p) == 0 && !EmberAftermath.endDone(p), "the arc ending came during the sky key scene", f);
        st.putLong("key_at", now - EmberAftermath.SCENE_TICKS - 10);
        expect(EmberAftermath.poll(p) == 3 && EmberAftermath.endDone(p), "the arc ending did not come after the sky key scene", f);
        expect(EmberAftermath.poll(p) == 0, "the arc ending came twice", f);
    }
}
