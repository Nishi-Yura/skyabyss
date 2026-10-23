package jp.revati.skyabyss.titan.ember.heart;

import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.ProgressRules;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.registry.ModBlocks;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.SkyKey;
import jp.revati.skyabyss.story.StorySetup;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackRooms;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.back.EmberRooms;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.gear.EmberGear;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の心臓の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST ember heart PASSED / FAILED。
// 熾の底の形 (床の輪・溶岩の溜め・橋・歩ける所)、区画の予告と沈みと戻り・呑まれた、血管と灯が消える冷え、
// 戦いの始まり・封印・放棄、段階と咆哮の無敵・段階 2 の 1.2 倍・人数の倍率、10 の技の予告 (傷の前に待つ長さ) と傷、
// 凍え (熱 0 で入り、10 を超えて解ける) と残り火で温まる数値、報酬 (天井・図鑑・欠片は一人一回・初回の選択・巨神ごとの記録・6.4節の模擬)、
// 撃破で進みの記録 (余燼を止めた・レベルの上限 40) と、四つ目の欠片で天の鍵が組める、もう一度挑めるようにする
public final class EmberHeartSelfTest {
    private EmberHeartSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember heart (load)", EmberHeartSelfTest::load),
                SelfTestRunner.Step.once("ember heart telegraph", 30, EmberHeartSelfTest::runTelegraph),
                SelfTestRunner.Step.once("ember heart", 30, EmberHeartSelfTest::runSelfTest));
    }

    // 熾の底のチャンクを読み込んだままにする (人がいないので)
    static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back != null) {
            force(back, true);
        }
    }

    private static void force(ServerLevel back, boolean on) {
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 0; cz++) {
                back.setChunkForced(cx, cz, on);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel back = server.getLevel(EmberIds.BACK);
        try {
            if (back == null) {
                failures.add("ember_back is missing");
            } else {
                // テストの人は一つのパーティにまとめ、心臓はそのパーティ (グループ) のものとして戦う
                testParty = jp.revati.skyabyss.party.PartyData.get(server).create(PARTY_LEAD, "eh_lead");
                run(server, back, failures, notes);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember heart crashed", e);
            failures.add("crashed: " + e);
        } finally {
            if (back != null) {
                EmberHeartBattle.abandon(back);
                EmberHeartArena.cleanup(back);
                forgetGroups(back);
                force(back, false);
            }
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember heart PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST ember heart FAILED: {}", f));
        }
    }

    // 心臓と戦わない、もう一つのグループ (撃破で記録が変わらないことを見る)
    static final UUID OTHER_GROUP = UUID.nameUUIDFromBytes("skyabyss ember heart other group".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    static final UUID PARTY_LEAD = UUID.nameUUIDFromBytes("skyabyss ember heart party lead".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @org.jetbrains.annotations.Nullable
    private static jp.revati.skyabyss.party.PartyData.Party testParty;

    // 自己テストが使ったグループの記録を消す (心臓の撃破・一度は倒した、がほかの自己テストに残らないように)
    private static void forgetGroups(ServerLevel back) {
        var data = jp.revati.skyabyss.titan.group.BackGroupData.get(back);
        if (testParty != null) {
            var parties = jp.revati.skyabyss.party.PartyData.get(back.getServer());
            for (UUID m : List.copyOf(testParty.members)) {
                parties.remove(m);
                data.remove(m);
            }
            data.remove(testParty.id);
            testParty = null;
        }
        data.remove(EmberHeartBattle.TEST_GROUP);
        if (EmberHeartBattle.lastGroup() != null) {
            data.remove(EmberHeartBattle.lastGroup());
        }
        data.remove(OTHER_GROUP);
        jp.revati.skyabyss.titan.group.RoomLocks.release(jp.revati.skyabyss.titan.ember.back.EmberRooms.HEART.lock());
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(MinecraftServer server, ServerLevel back, List<String> f, List<String> notes) {
        checkAssets(f);
        checkNumbers(f, notes);
        checkArena(back, f, notes);
        checkSections(back, f, notes);
        checkCold(back, f);
        checkBattle(back, f, notes);
        checkMoves(back, f, notes);
        checkChill(back, f, notes);
        checkLoot(server, back, f, notes);
        checkSkyKey(server, back, f, notes);
        checkDefeat(server, back, f, notes);
    }

    // ---------------------------------------------------------------- 絵・音・バー
    private static void checkAssets(List<String> f) {
        List<String> paths = new ArrayList<>(List.of("/assets/skyabyss/animations/entity/ember_heart.animation.json",
                "/data/skyabyss/damage_type/heat_burst.json", "/data/skyabyss/damage_type/engulfed.json"));
        for (int n = 1; n <= EmberHeart.PHASE_COUNT; n++) {
            paths.add("/assets/skyabyss/geo/entity/ember_heart_p" + n + "_lowpoly.geo.json");
            paths.add("/assets/skyabyss/textures/lowpoly/ember_heart_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/lowpoly/ember_heart_p" + n + "_glow.png");
            paths.add("/assets/skyabyss/textures/gui/boss/ember_heart_frame_p" + n + ".png");
            paths.add("/assets/skyabyss/textures/gui/boss/ember_heart_icon_p" + n + ".png");
        }
        for (String s : List.of("beat", "roar", "breath", "pulse", "orb", "floor", "gale", "pellet", "chill", "embers", "fading", "echo", "veil", "death")) {
            paths.add("/assets/skyabyss/sounds/ember_heart/" + s + ".ogg");
        }
        for (String p : paths) {
            try (InputStream in = EmberHeartSelfTest.class.getResourceAsStream(p)) {
                check(in != null, "missing asset " + p, f);
            } catch (java.io.IOException e) {
                f.add("could not read " + p);
            }
        }
        // 文: 日英にあり、同じ鍵がそろっている
        for (String code : List.of("ja_jp", "en_us")) {
            try (InputStream in = EmberHeartSelfTest.class.getResourceAsStream("/assets/skyabyss_ember/lang/" + code + ".json")) {
                if (in == null) {
                    f.add("missing lang " + code);
                    continue;
                }
                com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                List<String> keys = new ArrayList<>(List.of("entity.skyabyss.ember_heart", "entity.skyabyss.ash_pellet",
                        "gui.skyabyss.boss.ember_heart.phase.1", "gui.skyabyss.boss.ember_heart.phase.2", "gui.skyabyss.boss.ember_heart.phase.3",
                        "gui.skyabyss.boss.ember_heart.roaring"));
                for (String k : List.of("begin", "asleep", "descend", "exit_hint", "pillar", "defeated", "go", "choice_later", "reward", "frozen", "memory_title")) {
                    keys.add(EmberIds.LANG_HEART + k);
                }
                for (String s : List.of("beat", "roar", "breath", "pulse", "orb", "floor", "gale", "pellet", "chill", "embers", "fading", "echo", "veil", "death")) {
                    keys.add("subtitles.skyabyss.ember_heart_" + s);
                }
                for (String k : keys) {
                    check(o.has(k), "lang " + code + " lacks " + k, f);
                }
            } catch (java.io.IOException | RuntimeException e) {
                f.add("could not read lang " + code + ": " + e);
            }
        }
        // sounds.json に音の鍵がある
        try (InputStream in = EmberHeartSelfTest.class.getResourceAsStream("/assets/skyabyss/sounds.json")) {
            com.google.gson.JsonObject o = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            for (String s : List.of("beat", "roar", "breath", "pulse", "orb", "floor", "gale", "pellet", "chill", "embers", "fading", "echo", "veil", "death")) {
                check(o.has("ember_heart_" + s), "sounds.json lacks ember_heart_" + s, f);
            }
        } catch (java.io.IOException | RuntimeException e) {
            f.add("could not read sounds.json: " + e);
        }
        // 登録: 音・エンティティ
        check(EmberHeartSetup.BEAT.isBound() && EmberHeartSetup.DEATH.isBound() && EmberHeartSetup.ECHO.isBound(), "heart sounds are not registered", f);
        check(EmberHeartSetup.EMBER_HEART.isBound() && EmberHeartSetup.ASH_PELLET.isBound(), "heart entities are not registered", f);
        check(EmberBackRooms.hasBuilder("heart"), "the heart room has no builder registered with EmberBackRooms", f);
    }

    // ---------------------------------------------------------------- 数 (13 の 8.2・8.3節の表)
    private static void checkNumbers(List<String> f, List<String> notes) {
        check(EmberHeart.MAX_HEALTH == jp.revati.skyabyss.combat.HeartBalance.MAX_HP && EmberHeart.BASE_RESIST == 1.84f,
                "max health " + EmberHeart.MAX_HEALTH + " / resist " + EmberHeart.BASE_RESIST + " (want 1000 x 1.84)", f);
        check(EmberHeart.BREATH_DAMAGE == 32f && EmberHeart.BREATH_HEAT == 15f && EmberHeart.PULSE_DAMAGE == 24f && EmberHeart.PULSE_HEAT == 10f
                && EmberHeart.ORB_DAMAGE == 28f && EmberHeart.ORB_FLOOR_DAMAGE == 5f && EmberHeart.ORB_FLOOR_HEAT == 5f
                && EmberHeart.GALE_DAMAGE == 30f && EmberHeart.PELLET_DAMAGE == 18f && EmberHeart.FADING_DAMAGE == 37f
                && EmberHeart.FORGE_DAMAGE == 34f && EmberHeart.FORGE_CORE_DAMAGE == 30f
                && EmberHeart.EMBER_BURN_DAMAGE == 8f && EmberHeart.EMBER_HEAT_PER_SECOND == 12f && EmberHeart.CHILL_PER_SECOND == 8f
                && EmberHeartArena.ENGULF_DAMAGE == 18f && EmberHeartArena.ENGULF_HEAT == 40f, "damage or heat numbers differ from 13 の 8.3節", f);
        check(EmberHeart.GALE_PULL == 0.12 && EmberAshPellet.SPEED == 0.18 && EmberHeart.EMBER_RADIUS == 2.5 && EmberHeart.EMBER_TICKS == 160
                && EmberHeart.EMBER_COUNT == 4 && EmberHeartArena.CRACK_TICKS == 40 && EmberHeartArena.SINK_TICKS == 160 && EmberHeartArena.MAX_SINKING == 2,
                "timing numbers differ from 13 の 5.3節", f);
        // 技は 10 (冷えは段階 3 に入る瞬間から効く常時の技)。三段階に三つずつ
        check(EmberHeart.bagFor(0).length == 3 && EmberHeart.bagFor(1).length == 3 && EmberHeart.bagFor(2).length == 3, "each phase should have 3 moves", f);
        check(EmberHeart.bagFor(0).length + EmberHeart.bagFor(1).length + EmberHeart.bagFor(2).length + 1 == 10, "should be 10 moves in all", f);
        // 技の間: 段階 1 は 1.4〜1.9 秒、段階 2 は 1.1〜1.5 秒、段階 3 は 1.0〜1.4 秒、憤怒は 0.4〜0.7 秒。段階が進むほど短い
        check(EmberHeart.GAP_MIN[0] == 28 && EmberHeart.GAP_MIN[0] + EmberHeart.GAP_SPAN[0] - 1 <= 37
                && EmberHeart.GAP_MIN[1] == 22 && EmberHeart.GAP_MIN[1] + EmberHeart.GAP_SPAN[1] - 1 <= 29
                && EmberHeart.GAP_MIN[2] == 20 && EmberHeart.GAP_MIN[2] + EmberHeart.GAP_SPAN[2] - 1 <= 27
                && EmberHeart.GAP_FURY + EmberHeart.GAP_FURY_SPAN - 1 < EmberHeart.GAP_MIN[2], "move gaps differ from 13", f);
        // 技の組: 二つ目は 16 tick 以上遅れ、組の長さは二つの技が終わるまで。段階 3 と憤怒の組に灰の帳・鞴の嵐・崩れる床は入らない。炉打ちは憤怒の組だけ
        for (EmberHeart.Pair[] set : List.of(EmberHeart.PAIRS_TWO, EmberHeart.PAIRS_THREE, EmberHeart.PAIRS_FURY)) {
            for (EmberHeart.Pair pr : set) {
                check(pr.offset() >= 16 && pr.first() != pr.second(), "pair " + pr + " starts its second move too early", f);
                if (set != EmberHeart.PAIRS_TWO) {
                    for (EmberHeart.Action a : List.of(pr.first(), pr.second())) {
                        check(a != EmberHeart.Action.VEIL && a != EmberHeart.Action.GALE && a != EmberHeart.Action.FLOOR,
                                "pair " + pr + " uses " + a + " in the cold phase", f);
                    }
                }
            }
        }
        check(java.util.Arrays.stream(EmberHeart.PAIRS_FURY).anyMatch(pr -> pr.first() == EmberHeart.Action.FORGE)
                && java.util.Arrays.stream(EmberHeart.PAIRS_THREE).noneMatch(pr -> pr.first() == EmberHeart.Action.FORGE),
                "the forge should only come in the fury pairs", f);
        check(EmberHeart.FORGE_WARN >= Telegraph.MIN_WARN_TICKS && EmberHeart.FORGE_CORE_WARN > EmberHeart.FORGE_WARN + 2 * EmberHeart.FORGE_STEP,
                "forge telegraph timings", f);
        // 段階の境目と受ける傷
        check(EmberHeart.phaseFor(1f) == 0 && EmberHeart.phaseFor(0.67f) == 0 && EmberHeart.phaseFor(0.65f) == 1
                && EmberHeart.phaseFor(0.34f) == 1 && EmberHeart.phaseFor(0.32f) == 2, "phase thresholds are wrong", f);
        check(Math.abs(EmberHeart.takenFor(1, 10, 1) - 12f) < 1e-4 && Math.abs(EmberHeart.takenFor(0, 10, 1) - 10f) < 1e-4
                && Math.abs(EmberHeart.takenFor(2, 18.5f, EmberHeart.scaleFor(2)) - 10f) < 1e-4
                && Math.abs(EmberHeart.toughnessFor(1) - EmberHeart.BASE_RESIST) < 1e-4
                && Math.abs(EmberHeart.toughnessFor(3) - EmberHeart.BASE_RESIST * 2.7f) < 1e-4, "damage taken multipliers are wrong", f);
        // 段階 3 の鼓動: 33% で 2.4 秒、0% で 4 秒へ。だんだん遅く、弱く
        check(EmberHeart.beatInterval(0.33f) == 48 && EmberHeart.beatInterval(0f) == 80 && EmberHeart.beatInterval(0.16f) > 48
                && EmberHeart.beatInterval(0.16f) < 80 && EmberHeart.beatVolume(0f) < EmberHeart.beatVolume(0.33f), "beat interval / volume wrong", f);
        // 凍えの数: 熱 −8/秒、残り火 +12/秒 (差し引き +4/秒)。10 を超えるまで 2.5 秒以内に解ける。凍えの重みは 13 のとおり
        float net = EmberHeart.EMBER_HEAT_PER_SECOND - EmberHeart.CHILL_PER_SECOND;
        float thawSeconds = (EmberHeart.CHILL_THAW_ABOVE + 0.01f) / net;
        check(net > 0 && thawSeconds <= 3f, "an ember cannot thaw a frozen player in 3 s (net " + net + ")", f);
        check(EmberHeart.CHILL_MOVE == -0.30 && EmberHeart.CHILL_ATTACK_SPEED == -0.20 && EmberHeart.CHILL_FREEZE_AT == 0f && EmberHeart.CHILL_THAW_ABOVE == 10f, "chill numbers differ", f);
        // 凍えても勝てる: 残り火の寿命 (8 秒) で、遠くの残り火へ着いて解けるまでに足りる。最も遠い (部屋の端) まで凍えの足で歩いても
        double walk = 4.317 * (1 + EmberHeart.CHILL_MOVE);
        double farthest = 2 * 13.5;   // 部屋の端から反対の端 (残り火は半径 13.5 以内に置く) の最悪
        double secondsToEmber = (farthest - EmberHeart.EMBER_RADIUS) / walk;
        check(secondsToEmber + thawSeconds <= EmberHeart.EMBER_TICKS / 20.0 * 1.6, "a frozen player cannot reach and use an ember in time", f);
        // 凍えの攻撃の速さ −20% は、倒すのにかかる時間を 25% 延ばす。段階 3 (実質の体力 1000 × 2.29 × 0.33) を、想定の装備の一人の 1 秒の傷 (HeartBalance.playerDps) で
        double heartLeft = EmberHeart.MAX_HEALTH * EmberHeart.BASE_RESIST * EmberHeart.PHASE_THREE_AT;
        double dps = jp.revati.skyabyss.combat.HeartBalance.playerDps("ember", 1.0);
        double killNormal = heartLeft / dps;
        double killFrozen = heartLeft / (dps * (1 + EmberHeart.CHILL_ATTACK_SPEED));
        // 温まる代わりの傷: 凍えを解くあいだ (2.5 秒) に残り火の燃え立ちを二度受けても (どちらも 1 秒前に円の予告が出る)、想定の防具で想定の体力 38 の 1 割未満
        double thawCost = 2 * jp.revati.skyabyss.combat.HeartBalance.taken("ember", EmberHeart.EMBER_BURN_DAMAGE);
        check(killFrozen / killNormal > 1.2 && killFrozen / killNormal < 1.3, "frozen kill time ratio " + killFrozen / killNormal, f);
        check(thawCost < 0.1 * jp.revati.skyabyss.combat.HeartBalance.expectedHealth("ember"), "thawing in an ember costs " + thawCost + " (10% of the health or more)", f);
        notes.add(String.format("frozen: thaw %.1f s in an ember (two bursts %.1f dmg), hit phase 3 for %.0f s (normal %.0f s)", thawSeconds, thawCost, killFrozen, killNormal));
        // 6.6節の経験値: MOD_ENEMIES に一行 (Lv 30)
        GrowthRewards.Source src = GrowthRewards.MOD_ENEMIES.get(EmberIds.id(EmberIds.EMBER_HEART).toString());
        check(src != null && src.xp() == 650 && src.contentLevel() == 30, "GrowthRewards.MOD_ENEMIES has no ember heart (650, Lv 30)", f);
    }

    // ---------------------------------------------------------------- 熾の底の形
    private static void checkArena(ServerLevel back, List<String> f, List<String> notes) {
        EmberHeartBattle.abandon(back);
        EmberHeartArena.build(back);
        check(EmberHeartArena.isBuilt(back), "chamber not recognised as built", f);
        int fl = EmberHeartArena.FLOOR;
        int cx = EmberHeartArena.CX;
        int cz = EmberHeartArena.CZ;
        // 箱の中だけに置く (箱の外の隣の 1 層は、壁か B の物で、床と天井は箱の中)
        check(EmberBackLayout.HEART.contains(cx, fl, cz) && EmberBackLayout.HEART.contains(cx, EmberHeartArena.CEILING, cz)
                && EmberBackLayout.HEART.contains(cx, EmberHeartArena.BOTTOM, cz), "chamber falls outside EmberBackLayout.HEART", f);
        check(EmberBackLayout.HEART.contains(cx - EmberHeartArena.RADIUS - 1, fl, cz) && EmberBackLayout.HEART.contains(cx + EmberHeartArena.RADIUS + 1, fl, cz)
                && EmberBackLayout.HEART.contains(cx, fl, cz + EmberHeartArena.RADIUS + 1) && EmberBackLayout.HEART.contains(cx, fl, cz - EmberHeartArena.RADIUS - 1),
                "the wall of the chamber is outside the box", f);
        // 床の輪: 台・区画・溝・溜め
        check(back.getBlockState(new BlockPos(cx, fl, cz)).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "platform center", f);
        check(!back.getBlockState(new BlockPos(cx + 3, fl, cz)).isAir() && !back.getBlockState(new BlockPos(cx + 3, fl, cz)).is(Blocks.LAVA), "platform edge", f);
        check(back.getBlockState(new BlockPos(cx + 9, fl, cz)).getBlock() != Blocks.LAVA && !back.getBlockState(new BlockPos(cx + 9, fl, cz)).isAir(), "section floor is solid", f);
        check(back.getBlockState(new BlockPos(cx + 9, fl - 1, cz)).is(Blocks.LAVA) && back.getBlockState(new BlockPos(cx + 9, fl - 3, cz)).is(Blocks.LAVA)
                && !back.getBlockState(new BlockPos(cx + 9, fl - 4, cz)).isAir() && !back.getBlockState(new BlockPos(cx + 9, fl - 4, cz)).is(Blocks.LAVA),
                "the lava pool under a section is not 3 deep", f);
        check(back.getBlockState(new BlockPos(cx + 15, fl, cz)).is(Blocks.LAVA), "moat has no lava", f);
        check(back.getBlockState(new BlockPos(cx + 16, fl + 3, cz)).getBlock() != Blocks.AIR && !back.getBlockState(new BlockPos(cx + 16, fl + 3, cz)).is(Blocks.LAVA), "wall is open", f);
        check(back.getBlockState(new BlockPos(cx, EmberHeartArena.CEILING, cz + 3)).getBlock() != Blocks.AIR, "chamber has no ceiling", f);
        check(back.getBlockState(new BlockPos(cx, fl + 1, cz + 6)).isAir() && back.getBlockState(new BlockPos(cx + 7, EmberHeartArena.CEILING - 1, cz - 7)).isAir(), "interior is not open", f);
        // 入口: 橋 (幅 3、溝の上) と通り道、封印する壁の 1 枚は空いている
        for (int x = -1; x <= 1; x++) {
            for (int dz = -17; dz <= -15; dz++) {
                BlockPos floor = new BlockPos(cx + x, fl, cz + dz);
                check(!back.getBlockState(floor).isAir() && !back.getBlockState(floor).is(Blocks.LAVA), "bridge is missing at " + floor, f);
                check(back.getBlockState(floor.above()).isAir() && back.getBlockState(floor.above(3)).isAir(), "passage is closed at " + floor.above(), f);
            }
        }
        for (BlockPos g : EmberHeartArena.gate()) {
            check(back.getBlockState(g).is(ModBlocks.SEALED_GATE.get()) && back.getBlockState(g).getValue(jp.revati.skyabyss.titan.dungeon.SealedGateBlock.OPEN),
                    "entrance is not an open gate at " + g, f);
        }
        check(EmberHeartArena.gate().size() == 3 * 4, "the gate is not 3 x 4", f);
        // 部屋の位置
        check(EmberHeartArena.arena().contains(EmberHeartArena.heartHome()), "heart home outside the arena", f);
        check(!EmberHeartArena.arena().contains(EmberHeartArena.anteSpawn()), "antechamber spawn is inside the fight", f);
        check(EmberHeartArena.ante().contains(EmberHeartArena.anteSpawn()), "antechamber spawn is outside the antechamber", f);
        check(EmberHeartArena.arena().contains(EmberHeartArena.insideSpawn(0.5)), "inside spawn is outside the arena", f);
        check(EmberHeartArena.sectorAt(cx + 0.5, cz - 12.5) == 3 && EmberHeartArena.sectorAt(cx + 0.5, cz + 12.5) == 0, "entrance sector is not 3 (south is 0)", f);
        check(EmberHeartArena.onPlatform(cx + 0.5, cz + 2) && !EmberHeartArena.onPlatform(cx + 5.5, cz + 0.5), "platform test is wrong", f);
        // 六つの区画: 大きさがそろい、台と溝に重ならない
        int total = 0;
        for (int k = 0; k < EmberHeartArena.SECTIONS; k++) {
            int n = EmberHeartArena.sectionSize(k);
            total += n;
            check(n >= 95 && n <= 125, "section " + k + " has " + n + " blocks", f);
        }
        check(total > 600 && total < 760, "sections have " + total + " blocks in all", f);
        // 歩いて行ける: 入口の橋の奥から、台と六つの区画のすべてへ (段差は 1 まで)
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = new BlockPos(cx, fl + 1, cz - 14);
        queue.add(start);
        seen.add(start.asLong());
        boolean[] sectorReached = new boolean[EmberHeartArena.SECTIONS];
        boolean platformReached = false;
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            int k = EmberHeartArena.sectorOf(at.getX(), at.getZ());
            if (k >= 0) {
                sectorReached[k] = true;
            } else if (EmberHeartArena.dist(at.getX(), at.getZ()) < EmberHeartArena.PLATFORM_R) {
                platformReached = true;
            }
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                BlockPos n = new BlockPos(at.getX() + d[0], fl + 1, at.getZ() + d[1]);
                if (!seen.add(n.asLong())) {
                    continue;
                }
                var below = back.getBlockState(n.below());
                if (below.isAir() || below.is(Blocks.LAVA) || !back.getBlockState(n).isAir() || !back.getBlockState(n.above()).isAir()) {
                    continue;
                }
                queue.add(n);
            }
        }
        boolean all = platformReached;
        for (boolean b : sectorReached) {
            all &= b;
        }
        check(all, "the platform and all six sections cannot be walked to from the entrance", f);
        notes.add("chamber: 6 sections x ~" + total / 6 + " blocks, " + seen.size() + " standable cells");
    }

    // ---------------------------------------------------------------- 区画の予告・沈み・戻り・呑まれた
    private static FakePlayer fake(ServerLevel level, String tag, double x, double z) {
        // 偽のプレイヤーは傷を受けない (FakePlayer.isInvulnerableTo が真) ので、受けるようにする
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "[eh_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        // 湧いた直後の無敵 (spawnInvulnerableTime 60 tick。偽のプレイヤーは tick されず減らない) を外す
        try {
            java.lang.reflect.Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        p.setHealth(1000);
        p.moveTo(x, EmberHeartArena.SURFACE, z, 0, 0);
        if (testParty != null && !testParty.members.contains(p.getUUID())) {
            jp.revati.skyabyss.party.PartyData.get(level.getServer()).add(testParty, p.getUUID(), "eh_" + tag);
        }
        // 追加に失敗した (同じ UUID の残りがある等) なら、黙って進まず落とす
        if (!level.addFreshEntity(p)) {
            throw new IllegalStateException("could not add the fake player " + tag + " to the level");
        }
        return p;
    }

    private static void checkSections(ServerLevel back, List<String> f, List<String> notes) {
        EmberHeartArena.restoreAll(back);
        EmberHeartArena.resetState();
        int k = 1;
        Vec3Helper c = new Vec3Helper(EmberHeartArena.sectorCenter(k));
        FakePlayer p = fake(back, "section", c.x, c.z);
        try {
            check(back.getEntitiesOfClass(net.minecraft.server.level.ServerPlayer.class, EmberHeartArena.arena()).contains(p),
                    "the fake player is not found in the arena by an entity query (cannot test hits)", f);
            check(EmberHeartArena.sectorAt(p.getX(), p.getZ()) == k, "fake player is not in section " + k, f);
            check(EmberHeartArena.crack(back, k), "could not start cracking section " + k, f);
            check(!EmberHeartArena.crack(back, k), "cracked the same section twice", f);
            check(back.getBlockState(new BlockPos(EmberHeartArena.CX, EmberHeartArena.FLOOR, EmberHeartArena.CZ)).is(Blocks.CHISELED_POLISHED_BLACKSTONE), "platform changed", f);
            // 予告の 2.0 秒のあいだは沈まない (割れて光る)
            boolean glow = false;
            for (net.minecraft.core.BlockPos b : sample(k)) {
                glow |= back.getBlockState(b).is(Blocks.SHROOMLIGHT);
            }
            check(glow, "the cracking floor does not glow red", f);
            for (int i = 0; i < EmberHeartArena.CRACK_TICKS - 1; i++) {
                EmberHeartArena.tick(back, null);
            }
            check(EmberHeartArena.cracking(k) && EmberHeartArena.lavaCount(back, k) == 0, "section sank before the 2.0 s warning ended", f);
            check(p.getHealth() == 1000, "took damage during the warning", f);
            EmberHeartArena.tick(back, null);
            check(EmberHeartArena.sunk(k) && EmberHeartArena.lavaCount(back, k) == EmberHeartArena.sectionSize(k), "section did not turn to lava after the warning", f);
            // 沈んだとき立っていた人は呑まれる: 台へ戻り、傷 12 と熱 +40
            check(EmberHeartArena.onPlatform(p.getX(), p.getZ()), "engulfed player was not put on the platform (" + p.position() + ")", f);
            float lost = 1000 - p.getHealth();
            check(lost > 0 && lost >= 12f * 0.4f && lost <= 12f * 1.6f, "engulfed damage " + lost, f);
            check(EmberHeat.get(p) >= 20f, "engulfed heat " + EmberHeat.get(p), f);
            // 同時に沈むのは最大 2 区画
            check(EmberHeartArena.crack(back, 3) && !EmberHeartArena.crack(back, 5) && EmberHeartArena.sinkingCount() == 2, "more than 2 sections can sink at once", f);
            // 8 秒で戻る
            for (int i = 0; i < EmberHeartArena.SINK_TICKS + 1; i++) {
                EmberHeartArena.tick(back, null);
            }
            check(EmberHeartArena.standing(k) && EmberHeartArena.lavaCount(back, k) == 0
                    && !back.getBlockState(new BlockPos(EmberHeartArena.CX + 8, EmberHeartArena.FLOOR, EmberHeartArena.CZ)).isAir(), "section did not come back after 8 s", f);
            // 呑まれた人の救い上げ: 溝に落ちた人 (床の面より下) も台へ
            EmberHeat.reset(p);
            EmberHeartArena.resetState();
            p.moveTo(EmberHeartArena.CX + 15.5, EmberHeartArena.FLOOR - 0.5, EmberHeartArena.CZ + 0.5);
            // 救い上げは tick が 2 tick に一度だけ呼ぶ (同じ tick のうちに続けて tick を呼んでも、出生の偶奇で動かない) ので直接呼ぶ
            EmberHeartArena.rescue(back, null);
            check(EmberHeartArena.onPlatform(p.getX(), p.getZ()), "a player who fell in the moat was not put back", f);
        } finally {
            EmberHeat.reset(p);
            p.discard();
            EmberHeartArena.restoreAll(back);
        }
        notes.add("sections: warn 2.0 s, sink 8 s, at most 2 at once, engulfed -" + EmberHeartArena.ENGULF_DAMAGE + " / +40 heat");
    }

    private record Vec3Helper(double x, double y, double z) {
        Vec3Helper(net.minecraft.world.phys.Vec3 v) {
            this(v.x, v.y, v.z);
        }
    }

    private static List<BlockPos> sample(int k) {
        List<BlockPos> out = new ArrayList<>();
        double a = Math.toRadians(EmberHeartArena.sectorAngle(k));
        for (double r = 5; r < 14; r += 1) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = EmberHeartArena.CX + (int) Math.round(Math.cos(a) * r);
                int z = EmberHeartArena.CZ + (int) Math.round(Math.sin(a) * r) + dz;
                out.add(new BlockPos(x, EmberHeartArena.FLOOR, z));
                out.add(new BlockPos(x + 1, EmberHeartArena.FLOOR, z));
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 血管と灯が消える冷え
    private static void checkCold(ServerLevel back, List<String> f) {
        EmberHeartArena.setCold(back, false);
        BlockPos vein = findVein(back);
        check(vein != null && back.getBlockState(vein).is(Blocks.MAGMA_BLOCK), "no glowing vein in the wall", f);
        int fl = EmberHeartArena.FLOOR;
        BlockPos moat = new BlockPos(EmberHeartArena.CX + 15, fl, EmberHeartArena.CZ);
        check(back.getBlockState(moat).is(Blocks.LAVA), "moat not lava when warm", f);
        EmberHeartArena.setCold(back, true);
        check(EmberHeartArena.isCold(), "cold flag", f);
        check(vein != null && !back.getBlockState(vein).is(Blocks.MAGMA_BLOCK), "vein did not go dark", f);
        check(!back.getBlockState(moat).is(Blocks.LAVA), "moat lava did not cool", f);
        check(back.getBlockState(new BlockPos(EmberHeartArena.CX + 6, fl + 4, EmberHeartArena.CZ)).is(Blocks.LIGHT)
                || back.getBlockState(new BlockPos(EmberHeartArena.CX, fl + 4, EmberHeartArena.CZ + 6)).is(Blocks.LIGHT)
                || countLights(back) > 0, "no invisible ambient light while cold (the warnings would sink)", f);
        check(EmberHeartArena.isBuilt(back), "the cold room is not recognised as built (it would be rebuilt mid-fight)", f);
        EmberHeartArena.setCold(back, false);
        check(vein != null && back.getBlockState(vein).is(Blocks.MAGMA_BLOCK) && back.getBlockState(moat).is(Blocks.LAVA) && countLights(back) == 0,
                "warming did not restore the veins, moat and remove the ambient light", f);
    }

    private static BlockPos findVein(ServerLevel back) {
        int y = EmberHeartArena.SURFACE + 2;
        for (int dx = -EmberHeartArena.RADIUS - 1; dx <= EmberHeartArena.RADIUS + 1; dx++) {
            BlockPos p = new BlockPos(EmberHeartArena.CX + dx, y, EmberHeartArena.CZ);
            if (back.getBlockState(p).is(Blocks.MAGMA_BLOCK)) {
                return p;
            }
        }
        for (int dz = -EmberHeartArena.RADIUS - 1; dz <= EmberHeartArena.RADIUS + 1; dz++) {
            BlockPos p = new BlockPos(EmberHeartArena.CX, y, EmberHeartArena.CZ + dz);
            if (back.getBlockState(p).is(Blocks.MAGMA_BLOCK)) {
                return p;
            }
        }
        return null;
    }

    private static int countLights(ServerLevel back) {
        int n = 0;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = EmberHeartArena.CX - 16; x <= EmberHeartArena.CX + 16; x++) {
            for (int z = EmberHeartArena.CZ - 16; z <= EmberHeartArena.CZ + 16; z++) {
                for (int y = EmberHeartArena.SURFACE; y <= EmberHeartArena.SURFACE + 4; y++) {
                    p.set(x, y, z);
                    if (back.getBlockState(p).is(Blocks.LIGHT)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 戦い
    private static EmberHeart startHeart(ServerLevel back, List<String> f) {
        EmberHeartBattle.abandon(back);
        EmberHeartBattle.reawaken(back);
        check(testParty != null ? EmberHeartBattle.startNow(back, testParty.id) : EmberHeartBattle.startNow(back), "battle did not start", f);
        EmberHeart heart = EmberHeartBattle.current(back);
        if (heart == null) {
            f.add("no heart after starting (difficulty " + back.getDifficulty() + ")");
        } else {
            heart.skipIntro();
        }
        return heart;
    }

    // 1 tick 進める。tickCount を進めるのは本来はレベル (tickNonPassenger) なので、ここで足す
    private static void step(EmberHeart heart) {
        heart.tickCount++;
        heart.tick();
        Telegraph.tickNow((ServerLevel) heart.level());
    }

    // 倒れる演出 (100 tick) を最後まで流す。撃破の報告は演出が終わってから出る
    private static void finishDeath(EmberHeart heart) {
        for (int i = 0; i < jp.revati.skyabyss.entity.HeartBoss.DEATH_TICKS + 5 && !heart.isRemoved(); i++) {
            step(heart);
        }
    }

    private static void tickHeart(EmberHeart heart, int ticks) {
        for (int i = 0; i < ticks && heart.isAlive(); i++) {
            step(heart);
        }
    }

    private static void checkBattle(ServerLevel back, List<String> f, List<String> notes) {
        EmberHeart heart = startHeart(back, f);
        if (heart == null) {
            return;
        }
        check(EmberHeartArena.sealed(back), "entrance not sealed when the fight starts", f);
        check(heart.getTags().contains(EmberHeartBattle.TAG) && heart.isBound(), "heart not tagged / bound", f);
        check(!heart.shouldBeSaved(), "heart would be saved to disk", f);
        // 心臓は戦うグループのもの: ほかのグループの人には見えず、狙わない
        FakePlayer other = new FakePlayer(back, new GameProfile(OTHER_GROUP, "[eh_other]"));
        check(heart.groupId() != null && heart.groupId().equals(EmberHeartBattle.lastGroup()), "the heart has no group", f);
        check(!jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(heart, other) && !heart.canAttack(other),
                "another group can see or is targeted by the heart", f);
        check(EmberRooms.owner(EmberRooms.HEART) != null && EmberRooms.owner(EmberRooms.HEART).equals(EmberHeartBattle.lastGroup()),
                "the heart chamber is not held by the fighting group", f);
        check(!new EmberAshPellet(EmberHeartSetup.ASH_PELLET.get(), back).shouldBeSaved(), "pellet would be saved", f);
        check(Math.abs(heart.getMaxHealth() - EmberHeart.MAX_HEALTH) < 0.01, "max health is " + heart.getMaxHealth(), f);
        check(heart.getAttributeValue(Attributes.ARMOR) == 9 && heart.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 7
                && heart.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) == 1.0, "armor / toughness / knockback resistance are not 9 / 7 / 1.0", f);
        check(heart.distanceToSqr(EmberHeartArena.heartHome()) < 0.01 && heart.isNoGravity(), "heart is not floating at its home", f);
        // 咆哮: 体力を 60% にすると、咆哮 (無敵) のあと段階 2
        heart.setHealth(heart.getMaxHealth() * 0.6f);
        tickHeart(heart, 3);
        check(heart.isRoaring() && heart.currentAction() == EmberHeart.Action.ROAR, "no roar after dropping to 60%", f);
        float before = heart.getHealth();
        heart.hurt(heart.damageSources().generic(), 50f);
        check(heart.getHealth() == before, "heart took damage while roaring", f);
        heart.invulnerableTime = 0;
        tickHeart(heart, EmberHeart.ROAR_TICKS + 2);
        check(heart.getPhase() == 1 && !heart.isRoaring(), "phase did not advance after the roar (phase " + heart.getPhase() + ")", f);
        check(Math.abs(heart.bossBarProgress() - heart.getHealth() / heart.getMaxHealth()) < 0.01, "boss bar progress does not follow health", f);
        heart.invulnerableTime = 0;
        float h0 = heart.getHealth();
        heart.hurt(heart.damageSources().magic(), 10f);
        float exposed = 12f / heart.toughness();
        check(Math.abs((h0 - heart.getHealth()) - exposed) < 0.3f, "exposed shell should take 1.2x (took " + (h0 - heart.getHealth()) + ", want " + exposed + ")", f);
        // 段階 3 に入る咆哮で部屋が冷える。段階 2 の崩れ床の残りは元に戻る
        check(!EmberHeartArena.isCold(), "room is cold before phase 3", f);
        heart.setHealth(heart.getMaxHealth() * 0.3f);
        tickHeart(heart, 3);
        heart.invulnerableTime = 0;
        tickHeart(heart, EmberHeart.ROAR_TICKS + 2);
        check(heart.getPhase() == 2 && EmberHeartArena.isCold(), "phase 3 did not cool the room", f);
        check(EmberHeartArena.sinkingCount() == 0, "sections still sinking in phase 3", f);
        // 憤怒: 体力 15% 未満で、段階 3 のまま 3 秒の咆哮 (傷を受けない) のあと憤怒。模型は段階 3 のまま
        check(!heart.isEnraged(), "enraged before 15%", f);
        heart.setHealth(heart.getMaxHealth() * 0.12f);
        heart.holdAttacks(true);
        tickHeart(heart, 3);
        check(heart.isRoaring() && heart.currentAction() == EmberHeart.Action.ROAR, "no roar after dropping under 15%", f);
        float fury0 = heart.getHealth();
        heart.invulnerableTime = 0;
        heart.hurt(heart.damageSources().generic(), 50f);
        check(heart.getHealth() == fury0, "heart took damage during the fury roar", f);
        tickHeart(heart, EmberHeart.ROAR_TICKS + 2);
        check(heart.isEnraged() && heart.getPhase() == 2 && !heart.isRoaring() && EmberHeartArena.isCold(), "the fury roar did not enrage the heart (phase " + heart.getPhase() + ")", f);
        heart.holdAttacks(false);
        // 部屋の外へ出た心臓は戻る。誰もいなければ 3 秒で放棄
        heart.moveTo(EmberHeartArena.CX + 0.5, EmberHeartArena.SURFACE + 3, EmberHeartArena.CZ + 30.5);
        EmberHeartBattle.room().tick(back, EmberHeartBattle.HOST);
        check(heart.distanceToSqr(EmberHeartArena.heartHome()) < 1, "heart outside the room was not brought back", f);
        for (int i = 0; i < 7; i++) {
            EmberHeartBattle.room().tick(back, EmberHeartBattle.HOST);
        }
        check(!EmberHeartBattle.room().active() && !heart.isAlive() && !EmberHeartArena.sealed(back), "empty room was not abandoned", f);
        check(!EmberHeartArena.isCold() && EmberHeartArena.sinkingCount() == 0, "abandoning did not warm the room and restore the floor", f);
        // 人数の倍率で最大体力は変わらず、受ける傷を割る
        EmberHeart two = startHeart(back, f);
        if (two != null) {
            two.bindArena(EmberHeartArena.arena(), 3);
            two.invulnerableTime = 0;
            float t0 = two.getHealth();
            two.hurt(two.damageSources().magic(), 27f);
            float want = 27f / (EmberHeart.BASE_RESIST * 2.7f);
            check(Math.abs(two.toughness() - EmberHeart.toughnessFor(3)) < 1e-4 && Math.abs((t0 - two.getHealth()) - want) < 0.3f
                    && two.getMaxHealth() == EmberHeart.MAX_HEALTH, "3 challengers should divide the damage by 2.29 x 2.7 (took " + (t0 - two.getHealth()) + ")", f);
            EmberHeartBattle.abandon(back);
        }
        notes.add("phases 66/33 + fury 15%, roar 3 s invulnerable, exposed x1.2, abandon after 3 s");
    }

    // ---------------------------------------------------------------- 10 の技の予告と傷
    // 予告の長さ (傷の前に待つ tick。13 の表の「予告」) と、傷 (防具で減る前)
    private record Move(EmberHeart.Action action, int minWarn, float damage, int tickLimit) {
    }

    private static void checkMoves(ServerLevel back, List<String> f, List<String> notes) {
        EmberHeart heart = startHeart(back, f);
        if (heart == null) {
            return;
        }
        heart.holdAttacks(true);
        heart.forcePhase(0);
        // 全部の技が最後まで回る (人がいない)。冷えは段階 3 に入る瞬間の演出
        int ran = 0;
        for (int phase = 0; phase < 3; phase++) {
            heart.forcePhase(phase);
            List<EmberHeart.Action> moves = new ArrayList<>(List.of(EmberHeart.bagFor(phase)));
            if (phase == 2) {
                moves.add(EmberHeart.Action.CHILL);
            }
            for (EmberHeart.Action a : moves) {
                heart.forceAction(a);
                for (int i = 0; i < 260 && heart.currentAction() != EmberHeart.Action.NONE; i++) {
                    step(heart);
                    EmberHeartArena.tick(back, heart);
                }
                if (heart.currentAction() == EmberHeart.Action.NONE) {
                    ran++;
                } else {
                    f.add(a + " did not finish");
                }
                EmberHeartArena.restoreAll(back);
            }
        }
        notes.add(ran + "/10 moves ran to the end");
        // 技の組 (段階 2・3・憤怒): どれも組の長さで終わり、予告はどれも 16 tick 以上 (人がいないので傷は見ない)
        int pairsRan = 0;
        int pairWarn = Integer.MAX_VALUE;
        for (int set = 0; set < 3; set++) {
            EmberHeart.Pair[] pairs = set == 0 ? EmberHeart.PAIRS_TWO : set == 1 ? EmberHeart.PAIRS_THREE : EmberHeart.PAIRS_FURY;
            heart.forcePhase(set == 0 ? 1 : 2);
            heart.forceEnraged(set == 2);
            for (EmberHeart.Pair pr : pairs) {
                heart.forcePair(pr);
                int t = 0;
                for (; t < 200 && heart.currentAction() != EmberHeart.Action.NONE; t++) {
                    step(heart);
                    EmberHeartArena.tick(back, heart);
                    for (int w : Telegraph.activeWarns(back)) {
                        pairWarn = Math.min(pairWarn, w);
                    }
                }
                if (heart.currentAction() == EmberHeart.Action.NONE && t >= pr.length() - 1) {
                    pairsRan++;
                } else {
                    f.add("pair " + pr.first() + "+" + pr.second() + " ended after " + t + " ticks (length " + pr.length() + ")");
                }
                Telegraph.cancelAll(heart);
                EmberHeartArena.restoreAll(back);
            }
        }
        heart.forceEnraged(false);
        check(pairWarn >= Telegraph.MIN_WARN_TICKS, "a paired move has a telegraph under 16 ticks (" + pairWarn + ")", f);
        notes.add(pairsRan + " pairs ran to the end, shortest warn " + pairWarn);
        // 傷のある技: 予告の長さと傷を、立っている人で確かめる
        List<Move> moves = List.of(
                new Move(EmberHeart.Action.BREATH, 22, EmberHeart.BREATH_DAMAGE, 60),
                new Move(EmberHeart.Action.ORB, 28, EmberHeart.ORB_DAMAGE, 60),
                new Move(EmberHeart.Action.GALE, 66, EmberHeart.GALE_DAMAGE, 120),
                new Move(EmberHeart.Action.FADING, 38, EmberHeart.FADING_DAMAGE, 80));
        for (Move m : moves) {
            int phase = m.action() == EmberHeart.Action.GALE ? 1 : m.action() == EmberHeart.Action.FADING ? 2 : 0;
            heart.forcePhase(phase);
            // 心臓から見て (0, +5) の位置。心臓は人を向いて撃つので、帯の上に立つ
            FakePlayer p = fake(back, m.action().name().toLowerCase(), EmberHeartArena.CX + 0.5, EmberHeartArena.CZ + 5.5);
            try {
                heart.setTarget(p);
                heart.forceAction(m.action());
                int hitAt = -1;
                for (int i = 1; i <= m.tickLimit() && hitAt < 0; i++) {
                    step(heart);
                    if (p.getHealth() < 1000) {
                        hitAt = i;
                    }
                    p.moveTo(EmberHeartArena.CX + 0.5, EmberHeartArena.SURFACE, EmberHeartArena.CZ + 5.5);
                }
                float lost = 1000 - p.getHealth();
                check(hitAt >= m.minWarn(), m.action() + " hit after only " + hitAt + " ticks (warn at least " + m.minWarn() + ")", f);
                check(lost >= m.damage() * 0.4f && lost <= m.damage() * 1.6f, m.action() + " damage " + lost + " (expected about " + m.damage() + ")", f);
                if (m.action() == EmberHeart.Action.BREATH) {
                    check(EmberHeat.get(p) > 0, "breath added no heat", f);
                }
                notes.add(m.action().name().toLowerCase() + " warns " + hitAt + " ticks, hits " + Math.round(lost));
            } finally {
                EmberHeat.reset(p);
                p.discard();
                heart.setTarget(null);
                tickHeart(heart, 80);
                EmberHeartArena.restoreAll(back);
            }
        }
        // 熱の脈 (床を走る輪) は、跳べば当たらない: 跳んでいる人 (床より 1.2 上) は無傷、地面の人は傷
        // 輪の安全な筋は三本 (筋の向きは試験で決める: 0 度・120 度・240 度。地面の人は 90 度の向きで、筋の外)
        heart.forcePhase(0);
        EmberHeart.testGapBase = 0.0;
        FakePlayer ground = fake(back, "ring_ground", EmberHeartArena.CX + 0.5, EmberHeartArena.CZ + 7.5);
        FakePlayer air = fake(back, "ring_air", EmberHeartArena.CX + 0.5, EmberHeartArena.CZ + 9.5);
        FakePlayer lane = fake(back, "ring_lane", EmberHeartArena.CX + 0.5 + Math.cos(Math.toRadians(120)) * 8, EmberHeartArena.CZ + 0.5 + Math.sin(Math.toRadians(120)) * 8);
        try {
            heart.forceAction(EmberHeart.Action.PULSE);
            int groundHit = -1;
            for (int i = 1; i <= 80; i++) {
                step(heart);
                air.moveTo(EmberHeartArena.CX + 0.5, EmberHeartArena.SURFACE + 1.3, EmberHeartArena.CZ + 9.5);
                lane.moveTo(EmberHeartArena.CX + 0.5 + Math.cos(Math.toRadians(120)) * 8, EmberHeartArena.SURFACE, EmberHeartArena.CZ + 0.5 + Math.sin(Math.toRadians(120)) * 8);
                if (ground.getHealth() < 1000 && groundHit < 0) {
                    groundHit = i;
                }
                ground.moveTo(EmberHeartArena.CX + 0.5, EmberHeartArena.SURFACE, EmberHeartArena.CZ + 7.5);
            }
            check(groundHit >= 18, "pulse ring hit after only " + groundHit + " ticks", f);
            float lost = 1000 - ground.getHealth();
            check(lost >= EmberHeart.PULSE_DAMAGE * 0.4f && lost <= EmberHeart.PULSE_DAMAGE * 1.6f, "pulse damage " + lost, f);
            check(air.getHealth() == 1000, "a jumping player was hit by the pulse ring", f);
            check(lane.getHealth() == 1000, "a player in a safe lane was hit by the pulse ring", f);
            check(EmberHeat.get(ground) > 0, "pulse added no heat", f);
        } finally {
            EmberHeart.testGapBase = null;
            EmberHeat.reset(ground);
            EmberHeat.reset(air);
            ground.discard();
            air.discard();
            lane.discard();
        }
        // 灰の礫: 5 つ浮かぶ (溜め 1.0 秒)。人の足もとの円へ落ち (予告 1.5 秒)、傷は円の中だけで 10。落ちる前に叩けば礫も円も消える
        heart.forcePhase(1);
        FakePlayer target = fake(back, "pellet", EmberHeartArena.CX + 0.5, EmberHeartArena.CZ + 8.5);
        try {
            heart.setTarget(target);
            heart.forceAction(EmberHeart.Action.PELLET);
            tickHeart(heart, 19);
            int early = back.getEntitiesOfClass(EmberAshPellet.class, EmberHeartArena.arena()).size();
            tickHeart(heart, 2);
            List<EmberAshPellet> pellets = back.getEntitiesOfClass(EmberAshPellet.class, EmberHeartArena.arena());
            check(early == 0, "pellets appeared before the 1.0 s charge (" + early + ")", f);
            check(pellets.size() == EmberHeart.pelletCount(1), "pellet count " + pellets.size() + " (want " + EmberHeart.pelletCount(1) + ")", f);
            check(EmberHeart.PELLET_DAMAGE == 18f && EmberAshPellet.LIFE == 160 && EmberHeart.PELLET_WARN >= Telegraph.MIN_WARN_TICKS, "pellet numbers", f);
            check(1000 - target.getHealth() == 0, "a pellet hurt before landing", f);
            tickHeart(heart, EmberHeart.PELLET_WARN + 3);
            float lost = 1000 - target.getHealth();
            check(lost >= EmberHeart.PELLET_DAMAGE * 0.4f && lost <= EmberHeart.PELLET_DAMAGE * 1.6f, "the pellet circle under the player did " + lost, f);
            check(back.getEntitiesOfClass(EmberAshPellet.class, EmberHeartArena.arena()).isEmpty(), "pellets stayed after landing", f);
            // 叩けば消える: すべて叩くと、円も消えて傷はない
            // 床の崩れ (段階 2 の 6〜8 秒ごと) の呑まれが混ざらないよう、段階 1 で
            heart.forcePhase(0);
            jp.revati.skyabyss.combat.Telegraph.cancelAll(heart);
            EmberHeartArena.restoreAll(back);
            target.setHealth(1000);
            target.invulnerableTime = 0;
            heart.forceAction(EmberHeart.Action.PELLET);
            tickHeart(heart, 22);
            for (EmberAshPellet pel : back.getEntitiesOfClass(EmberAshPellet.class, EmberHeartArena.arena())) {
                pel.hurt(back.damageSources().generic(), 1f);
            }
            tickHeart(heart, EmberHeart.PELLET_WARN + 5);
            check(target.getHealth() == 1000, "a struck pellet's circle still hurt (" + (1000 - target.getHealth()) + ")", f);
        } finally {
            target.discard();
            heart.setTarget(null);
            for (EmberAshPellet pel : back.getEntitiesOfClass(EmberAshPellet.class, EmberHeartArena.arena())) {
                pel.discard();
            }
        }
        // 崩れる床: 技の 2.0 秒の予告のあとに区画が沈む。段階 2 は 6〜8 秒ごとに自動でも割れ、同時に 2 区画まで
        heart.forcePhase(1);
        EmberHeartArena.restoreAll(back);
        heart.forceAction(EmberHeart.Action.FLOOR);
        tickHeart(heart, 2);
        check(EmberHeartArena.sinkingCount() == 1, "the floor move did not crack a section", f);
        int crackedAt = 0;
        int sunkAt = -1;
        int maxAtOnce = 0;
        int autoCracks = 0;
        int lastCount = EmberHeartArena.sinkingCount();
        for (int i = 0; i < 700; i++) {
            step(heart);
            EmberHeartArena.tick(back, heart);
            int n = EmberHeartArena.sinkingCount();
            maxAtOnce = Math.max(maxAtOnce, n);
            if (n > lastCount) {
                autoCracks++;
            }
            lastCount = n;
            if (sunkAt < 0) {
                for (int k = 0; k < EmberHeartArena.SECTIONS; k++) {
                    if (EmberHeartArena.sunk(k)) {
                        sunkAt = i;
                    }
                }
            }
        }
        check(sunkAt >= EmberHeartArena.CRACK_TICKS - 4, "a section sank " + sunkAt + " ticks after the move (warning is 2.0 s)", f);
        check(maxAtOnce <= 2, "more than two sections at once (" + maxAtOnce + ")", f);
        check(autoCracks >= 3, "phase 2 did not crack a new section every 6-8 s (" + autoCracks + " in 35 s)", f);
        heart.forcePhase(0);
        EmberHeartArena.restoreAll(back);
        notes.add("floor: cracks, sinks after >=2.0 s, " + autoCracks + " auto cracks in 35 s, max " + maxAtOnce + " at once");
        EmberHeartBattle.abandon(back);
    }

    // ---------------------------------------------------------------- 段階 3: 凍えと残り火
    private static void checkChill(ServerLevel back, List<String> f, List<String> notes) {
        EmberHeart heart = startHeart(back, f);
        if (heart == null) {
            return;
        }
        heart.holdAttacks(true);
        heart.forcePhase(2);
        FakePlayer p = fake(back, "chill", EmberHeartArena.CX + 0.5, EmberHeartArena.CZ + 6.5);
        try {
            double speed0 = p.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double attack0 = p.getAttributeValue(Attributes.ATTACK_SPEED);
            EmberHeat.set(p, 0);
            tickHeart(heart, 25);
            check(heart.chilledPlayers().contains(p.getUUID()), "a player at heat 0 did not freeze", f);
            check(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed0 * 0.7) < 1e-6, "frozen speed is not -30% (" + p.getAttributeValue(Attributes.MOVEMENT_SPEED) + ")", f);
            check(Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - attack0 * 0.8) < 1e-6, "frozen attack speed is not -20%", f);
            // 熱が 10 を超えると解ける
            EmberHeat.set(p, 11);
            tickHeart(heart, 10);
            EmberHeat.set(p, 40);
            tickHeart(heart, 10);
            check(!heart.chilledPlayers().contains(p.getUUID()), "a player above heat 10 did not thaw", f);
            check(Math.abs(p.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed0) < 1e-6 && Math.abs(p.getAttributeValue(Attributes.ATTACK_SPEED) - attack0) < 1e-6,
                    "thawing did not restore the speeds", f);
            // 1 秒に −8
            EmberHeat.set(p, 60);
            float h0 = EmberHeat.get(p);
            tickHeart(heart, 20);
            float lost = h0 - EmberHeat.get(p);
            check(lost >= 7.5f && lost <= 8.5f * 2, "heat fell " + lost + " in 1 s (expected 8)", f);
            // 残り火: 4 つ置く (予告 1.0 秒)。中に立つと熱が上がる (冷えの −8 より多い)。燃え立ちは 5
            heart.forceAction(EmberHeart.Action.EMBERS);
            tickHeart(heart, 19);
            check(heart.emberCount() == 0, "embers appeared before the 1.0 s warning", f);
            tickHeart(heart, 2);
            check(heart.emberCount() == EmberHeart.EMBER_COUNT, "expected 4 embers, got " + heart.emberCount(), f);
            List<net.minecraft.world.phys.Vec3> spots = heart.emberSpots();
            for (int i = 0; i < spots.size(); i++) {
                for (int j = i + 1; j < spots.size(); j++) {
                    check(spots.get(i).distanceTo(spots.get(j)) >= 3.4, "two embers overlap", f);
                }
                double d = Math.hypot(spots.get(i).x - (EmberHeartArena.CX + 0.5), spots.get(i).z - (EmberHeartArena.CZ + 0.5));
                check(d <= 13.6 && !EmberHeartArena.onPlatform(spots.get(i).x, spots.get(i).z) || d > 1.5, "ember out of place", f);
            }
            net.minecraft.world.phys.Vec3 at = spots.get(0);
            heart.setTarget(null);
            for (int i = 0; i < heart.emberCount(); i++) {
                check(EmberHeartArena.arena().contains(spots.get(i)), "ember outside the arena", f);
            }
            EmberHeat.set(p, 0);
            float hp0 = p.getHealth();
            p.moveTo(at.x, EmberHeartArena.SURFACE, at.z);
            tickHeart(heart, 60);
            float gained = EmberHeat.get(p);
            check(gained > 6f, "standing in an ember for 3 s warmed to only " + gained, f);
            float burn = hp0 - p.getHealth();
            check(Math.abs(burn) < 5f * 3 * 1.6f, "ember burn was " + burn, f);
            // 残り火は 8 秒で消える。灯も片づく
            tickHeart(heart, 200);
            check(heart.emberCount() == 0, "embers did not burn out after 8 s", f);
            check(countLights(back) <= EmberHeartArena.SECTIONS * 2 + 2, "ember lights were left behind", f);
            // 灰の帳: 予告 1.0 秒のあと 5 秒。傷はない
            float hv = p.getHealth();
            heart.forceAction(EmberHeart.Action.VEIL);
            tickHeart(heart, 35);
            check(heart.veilTicks() > 0 && heart.veilTicks() <= 100, "veil did not start (" + heart.veilTicks() + ")", f);
            tickHeart(heart, 105);
            check(heart.veilTicks() == 0, "veil did not end after 5 s", f);
            check(p.getHealth() >= hv - 0.01f, "the veil did damage", f);
            notes.add("chill: freezes at heat 0 (-30% move, -20% attack), thaws above 10, embers warm");
        } finally {
            heart.clearChill(back);
            EmberHeat.reset(p);
            p.discard();
            EmberHeartBattle.abandon(back);
        }
        check(back.getEntitiesOfClass(EmberHeart.class, EmberHeartArena.arena().inflate(8)).isEmpty(), "heart left after abandon", f);
    }

    // ---------------------------------------------------------------- 予告 (Telegraph) の自己テスト
    // 心臓の傷のある技 (火の息・熱の脈・熔け玉・崩れる床・鞴の嵐・灰の礫・残り火・消えかけの鼓動) を、一人と六人で実際に回し、
    //  - 印と当たりが同じ: 技が作った傷のある予告の形 (Telegraph.record) の中にいた人だけが傷を受ける
    //  - 予告は 16 tick (0.8 秒) 以上
    //  - 安全な床が 15% 以上残る (Telegraph.probe)
    //  - 六人の技は一人の技より範囲が多い
    //  - いちばん広い一つの予告 (動かない形) が熾の底の床の 4 割以下
    private record Case(EmberHeart.Action action, int phase) {
    }

    private static final List<Case> CASES = List.of(new Case(EmberHeart.Action.BREATH, 0), new Case(EmberHeart.Action.PULSE, 0),
            new Case(EmberHeart.Action.ORB, 0), new Case(EmberHeart.Action.FLOOR, 1), new Case(EmberHeart.Action.GALE, 1),
            new Case(EmberHeart.Action.PELLET, 1), new Case(EmberHeart.Action.EMBERS, 2), new Case(EmberHeart.Action.FADING, 2),
            new Case(EmberHeart.Action.FORGE, 2));

    static void runTelegraph(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel back = server.getLevel(EmberIds.BACK);
        try {
            if (back == null) {
                failures.add("ember_back is missing");
            } else {
                testParty = jp.revati.skyabyss.party.PartyData.get(server).create(PARTY_LEAD, "eh_lead");
                telegraph(back, failures, notes);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember heart telegraph crashed", e);
            failures.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            Telegraph.record(null);
            EmberHeart.testGapBase = null;
            if (back != null) {
                EmberHeartBattle.abandon(back);
                EmberHeartArena.cleanup(back);
                forgetGroups(back);
            }
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST ember heart telegraph note: {}", n));
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember heart telegraph PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ember heart telegraph FAILED: {}", m));
        }
    }

    private static List<Vec3> floorGrid(double radius) {
        List<Vec3> probes = new ArrayList<>();
        double cx = EmberHeartArena.CX + 0.5;
        double cz = EmberHeartArena.CZ + 0.5;
        for (double x = cx - radius; x <= cx + radius; x += 1.0) {
            for (double z = cz - radius; z <= cz + radius; z += 1.0) {
                if (Math.hypot(x - cx, z - cz) <= radius) {
                    probes.add(new Vec3(x, EmberHeartArena.SURFACE, z));
                }
            }
        }
        return probes;
    }

    // 形のどこかの瞬間に点が入るか (動く形は進み 0〜1 を 25 に分けて)
    static boolean everInside(List<TelegraphShape> shapes, double x, double z) {
        for (TelegraphShape shape : shapes) {
            for (int i = 0; i <= 24; i++) {
                if (shape.at(i / 24.0).contains(x, z)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void telegraph(ServerLevel back, List<String> f, List<String> notes) {
        java.util.Map<String, int[]> areas = new java.util.LinkedHashMap<>();
        java.util.Map<String, int[]> safes = new java.util.LinkedHashMap<>();
        double widest = 0;
        double farthest = 0;
        int mismatches = 0;
        int samples = 0;
        for (int n : new int[] {1, 6}) {
            for (Case c : CASES) {
                String name = c.action().name();
                EmberHeart heart = startHeart(back, f);
                if (heart == null) {
                    return;
                }
                heart.holdAttacks(true);
                heart.forcePhase(c.phase());
                List<FakePlayer> ps = new ArrayList<>();
                List<Vec3> spots = new ArrayList<>();
                try {
                    for (int i = 0; i < n; i++) {
                        double a = Math.PI * 2 * i / n + 0.3;
                        double r = 5 + (i % 3) * 2.5;
                        double x = EmberHeartArena.CX + 0.5 + Math.cos(a) * r;
                        double z = EmberHeartArena.CZ + 0.5 + Math.sin(a) * r;
                        spots.add(new Vec3(x, EmberHeartArena.SURFACE, z));
                        ps.add(fake(back, "tg" + n + "_" + i, x, z));
                    }
                    heart.setTarget(ps.get(0));
                    List<Vec3> probes = floorGrid(14);
                    Telegraph.probe(probes);
                    Telegraph.resetStats();
                    List<TelegraphShape> shapes = new ArrayList<>();
                    Telegraph.record(shapes);
                    heart.forceAction(c.action());
                    int minWarn = Integer.MAX_VALUE;
                    boolean done = false;
                    for (int t = 1; t <= 420 && !done; t++) {
                        step(heart);
                        EmberHeartArena.tick(back, heart);
                        for (int w : Telegraph.activeWarns(back)) {
                            minWarn = Math.min(minWarn, w);
                        }
                        for (int i = 0; i < ps.size(); i++) {
                            ps.get(i).moveTo(spots.get(i).x, EmberHeartArena.SURFACE, spots.get(i).z);
                        }
                        done = t > 10 && heart.currentAction() == EmberHeart.Action.NONE && Telegraph.activeCount(back) == 0
                                && (c.action() != EmberHeart.Action.EMBERS || heart.emberCount() == 0);
                    }
                    check(done, name + " (n=" + n + ") did not finish", f);
                    check(minWarn >= Telegraph.MIN_WARN_TICKS, name + " (n=" + n + ") has a telegraph under 16 ticks (" + minWarn + ")", f);
                    double[] reach = {0};
                    boolean[] hits = Telegraph.probeHits();
                    int safe = 0;
                    for (boolean h : hits) {
                        safe += h ? 0 : 1;
                    }
                    int percent = Math.round(100f * safe / hits.length);
                    check(percent >= 15, name + " (n=" + n + ") leaves only " + percent + "% of the floor safe", f);
                    areas.computeIfAbsent(name, k -> new int[2])[n == 1 ? 0 : 1] = Telegraph.damagingCreated();
                    safes.computeIfAbsent(name, k -> new int[2])[n == 1 ? 0 : 1] = percent;
                    // 印と当たりが同じ: 形の中にいた人だけが傷を受けた
                    for (int i = 0; i < ps.size(); i++) {
                        boolean expected = everInside(shapes, spots.get(i).x, spots.get(i).z);
                        boolean hurt = ps.get(i).getHealth() < 1000;
                        samples++;
                        if (expected != hurt) {
                            mismatches++;
                            f.add(name + " (n=" + n + ") player " + i + " at " + Math.round(spots.get(i).x - EmberHeartArena.CX) + ","
                                    + Math.round(spots.get(i).z - EmberHeartArena.CZ) + ": marker says " + expected + ", hurt " + hurt);
                        }
                    }
                    // いちばん遠くへ届く予告 (心臓の位置から、印の輪郭の点までの距離)
                    for (TelegraphShape shape : shapes) {
                        for (int k = 0; k <= 4; k++) {
                            if (shape instanceof TelegraphShape.Circle circ && circ.r() <= 3.0) {
                                continue;
                            }
                            shape.at(k / 4.0).outline((px, pz) -> reach[0] = Math.max(reach[0], Math.hypot(px - heart.getX(), pz - heart.getZ())), 1.0);
                        }
                    }
                    farthest = Math.max(farthest, reach[0]);
                    // いちばん広い一つの予告 (動かない形)。動く輪は、安全な筋と跳び越えがあるので、掃く面ではなく安全な床で見る
                    for (TelegraphShape shape : shapes) {
                        if (shape.at(0.0) != shape) {
                            continue;
                        }
                        int inside = 0;
                        for (Vec3 pt : probes) {
                            inside += shape.contains(pt.x, pt.z) ? 1 : 0;
                        }
                        widest = Math.max(widest, inside / (double) probes.size());
                    }
                } finally {
                    Telegraph.probe(null);
                    Telegraph.record(null);
                    for (FakePlayer pl : ps) {
                        EmberHeat.reset(pl);
                        pl.discard();
                    }
                    heart.setTarget(null);
                    EmberHeartBattle.abandon(back);
                    EmberHeartArena.restoreAll(back);
                    for (int i = 0; i < 3; i++) {
                        Telegraph.tickNow(back);
                    }
                }
            }
        }
        StringBuilder sb = new StringBuilder("heart (areas 1p -> 6p / safe floor 1p -> 6p):");
        for (var e : areas.entrySet()) {
            int[] a = e.getValue();
            int[] sf = safes.get(e.getKey());
            sb.append(' ').append(e.getKey().toLowerCase()).append(' ').append(a[0]).append("->").append(a[1]).append('/').append(sf[0]).append("%->").append(sf[1]).append('%');
            // 残り火と炉打ち (部屋を回る三打と中央。人数で増えない) は除く
            if (!e.getKey().equals("EMBERS") && !e.getKey().equals("FORGE")) {
                check(a[1] > a[0], e.getKey() + ": 6 players made " + a[1] + " areas, 1 player made " + a[0] + " (must grow with the party)", f);
            }
        }
        // 部屋の検査: 使える床の半径 (区画の外の縁) が、いちばん遠くへ届く予告の 0.8 倍以上、天井は床から 12 以上
        double usable = EmberHeartArena.SECTION_R;
        check(usable >= 0.8 * farthest, "usable floor radius " + usable + " is under 0.8 x the farthest telegraph reach " + farthest, f);
        int headroom = EmberHeartArena.CEILING - EmberHeartArena.SURFACE;
        check(headroom >= 12, "the chamber ceiling is only " + headroom + " above the floor (need 12)", f);
        notes.add("room: usable radius " + usable + ", farthest telegraph reach " + Math.round(farthest * 10) / 10.0 + ", headroom " + headroom);
        check(widest <= 0.40, "the widest single telegraph covers " + Math.round(widest * 100) + "% of the chamber floor (limit 40%)", f);
        notes.add(sb.toString());
        notes.add("marker == hit: " + samples + " player positions, " + mismatches + " mismatches; widest single area " + Math.round(widest * 100) + "% of the floor; "
                + "heart size " + EmberHeartSetup.EMBER_HEART.get().getWidth() + " x " + EmberHeartSetup.EMBER_HEART.get().getHeight());
    }

    // ---------------------------------------------------------------- 報酬
    private static void checkLoot(MinecraftServer server, ServerLevel back, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(42);
        TitanMemoryData.Record r = new TitanMemoryData.Record();
        EmberLoot.Roll first = EmberLoot.rollHeart(r, random, id -> true);
        check(first.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.CORE)), "first clear has no core", f);
        check(first.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.SHARD) && d.count() >= 4 && d.count() <= 7), "shards not 4-7", f);
        check(first.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.RESONANCE_SHARD) && d.count() >= 1 && d.count() <= 3), "resonance shards not 1-3", f);
        check(EmberLoot.collection().size() == 10 && EmberLoot.collection().equals(EmberIds.CODEX.stream().map(EmberIds::id).map(Object::toString).toList()),
                "collection should be the 10 pieces of EmberIds.CODEX", f);
        check(EmberLoot.EXCLUSIVES.size() == 3 && EmberLoot.chance(EmberGear.HAMMER) == 0.04f && EmberLoot.chance(EmberGear.LANTERN) == 0.02f
                && EmberLoot.chance(EmberGear.HELMET) == 0.25f && EmberLoot.chance(EmberGear.WAR_AXE) == 0.20f, "chances differ from 6.3節", f);
        // 天の鍵の欠片以外の確率: 心核 初回必ず・以後 25%、共鳴の結晶 40%、角杯 50%
        int cores = 0;
        int crystals = 0;
        int horns = 0;
        int runs = 4000;
        RandomSource r2 = RandomSource.create(7);
        for (int i = 0; i < runs; i++) {
            TitanMemoryData.Record rr = new TitanMemoryData.Record();
            rr.clears = 1;
            EmberLoot.Roll roll = EmberLoot.rollHeart(rr, r2, id -> false);
            cores += roll.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.CORE)) ? 1 : 0;
            crystals += roll.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.RESONANCE_CRYSTAL)) ? 1 : 0;
            horns += roll.draws().stream().anyMatch(d -> d.id().equals(EmberLoot.HORN) && d.count() >= 2 && d.count() <= 3) ? 1 : 0;
        }
        check(Math.abs(cores / (double) runs - 0.25) < 0.04 && Math.abs(crystals / (double) runs - 0.40) < 0.04 && Math.abs(horns / (double) runs - 0.50) < 0.04,
                "core / crystal / horn rates " + cores + " " + crystals + " " + horns, f);
        // ハード天井: 記憶 4 から撃破で 5 になり、未所持を一つ保証
        TitanMemoryData.Record p = new TitanMemoryData.Record();
        p.memory = TitanLoot.HARD_PITY - 1;
        EmberLoot.Roll pity = EmberLoot.rollHeart(p, random, id -> true);
        check(pity.guaranteed() != null && p.memory == 0, "hard pity did not guarantee a new piece", f);
        TitanMemoryData.Record all = new TitanMemoryData.Record();
        all.owned.addAll(EmberLoot.collection());
        all.memory = TitanLoot.HARD_PITY - 1;
        EmberLoot.Roll full = EmberLoot.rollHeart(all, random, id -> true);
        check(full.pityShards() && all.memory == 0, "full collection pity should give shards", f);
        // 作られていない品は出さない
        TitanMemoryData.Record none = new TitanMemoryData.Record();
        for (int i = 0; i < 30; i++) {
            EmberLoot.rollHeart(none, random, id -> false);
        }
        check(none.owned.isEmpty(), "unmade items were recorded", f);
        // 6.4節の模擬 (図鑑 10 品すべて: 平均 13.8)
        double mean = EmberLoot.meanClearsToComplete(4000, 7, id -> true);
        check(mean > 11.5 && mean < 16.5, "mean clears to complete the collection is " + mean + " (6.4節: 13.8)", f);
        notes.add(String.format("collection in %.1f clears on average (design 13.8)", mean));
        // 撃破の報酬 (いない人の分は預かる)。欠片は一人一回。巨神ごとの記録。余燼を止めた記録と上限 40
        UUID fake = UUID.randomUUID();
        TitanMemoryData memory = TitanMemoryData.get(server);
        boolean worldBefore = ProgressData.get(server).worldStopped(EmberLoot.TITAN);
        EmberLoot.onHeartDefeat(back, List.of(), List.of(fake));
        TitanMemoryData.Record rec = memory.peek(fake, EmberLoot.TITAN);
        check(rec != null && rec.clears == 1 && rec.fragment && rec.firstCleared && rec.choicePending, "defeat record is wrong", f);
        check(memory.peek(fake, TitanLoot.STONE_WHALE) == null && memory.peek(fake, "tide_shell") == null, "ember defeat wrote to another titan's record", f);
        check(memory.hasPending(fake), "rewards for an absent player were not held", f);
        TitanMemoryData.Pending held = memory.takePending(fake);
        check(held.items.stream().anyMatch(s -> TitanLoot.id(s.getItem()).equals(EmberLoot.SHARD) && s.getCount() >= 4), "heart reward did not give ember cinders", f);
        long fragments = held.items.stream().filter(s -> TitanLoot.id(s.getItem()).equals(EmberLoot.SKY_KEY_FRAGMENT)).count();
        check(fragments == 1, "first defeat should give one sky key fragment (" + fragments + ")", f);
        EmberLoot.onHeartDefeat(back, List.of(), List.of(fake));
        held = memory.takePending(fake);
        check(held.items.stream().noneMatch(s -> TitanLoot.id(s.getItem()).equals(EmberLoot.SKY_KEY_FRAGMENT)), "second defeat gave another fragment", f);
        check(Progression.stopped(server, fake, EmberLoot.TITAN), "defeat did not record the ember as stopped", f);
        check(ProgressData.get(server).worldStopped(EmberLoot.TITAN), "world progress did not record the ember", f);
        ProgressData.get(server).clearPersonal(fake, EmberLoot.TITAN);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(EmberLoot.TITAN);
        }
        // 四体を止めるとレベルの上限は 40
        check(ProgressRules.levelCap(ProgressRules.TITAN_ORDER.size()) == 40, "level cap after four titans is " + ProgressRules.levelCap(ProgressRules.TITAN_ORDER.size()), f);
    }

    // ---------------------------------------------------------------- 四つ目の欠片で天の鍵 (14 の SkyKey)
    private static void checkSkyKey(MinecraftServer server, ServerLevel back, List<String> f, List<String> notes) {
        FakePlayer p = new FakePlayer(server.overworld(), new GameProfile(UUID.randomUUID(), "[eh_key]"));
        p.setPos(0.5, server.overworld().getMinBuildHeight() + 2, 0.5);
        SkyKey.reset(p);
        List<String> before = new ArrayList<>();
        ProgressData data = ProgressData.get(server);
        for (String t : ProgressRules.TITAN_ORDER) {
            before.add(t + "=" + data.worldStopped(t));
        }
        try {
            // 三体を止め、欠片を三つ持っている: まだ組まない
            for (String t : ProgressRules.TITAN_ORDER) {
                if (!t.equals(EmberLoot.TITAN)) {
                    Progression.onTitanStopped(server, List.of(p.getUUID()), t);
                }
            }
            for (int i = 0; i < 3; i++) {
                p.getInventory().add(new ItemStack(ModItems.SKY_KEY_FRAGMENT.get()));
            }
            check(!SkyKey.ready(p), "the key is ready with three titans and three fragments", f);
            // 余燼の心臓を止める (報酬より先に進みを書き、欠片を一人一つ渡す)
            EmberLoot.onHeartDefeat(back, List.of(), List.of(p.getUUID()));
            check(Progression.stage(p) == ProgressRules.TITAN_ORDER.size() && ProgressRules.levelCap(Progression.stage(p)) == 40, "stage / level cap after the ember heart", f);
            TitanMemoryData.Pending pending = TitanMemoryData.get(server).takePending(p.getUUID());
            int moved = 0;
            for (ItemStack s : pending.items) {
                if (s.is(ModItems.SKY_KEY_FRAGMENT.get())) {
                    p.getInventory().add(s.copy());
                    moved++;
                }
            }
            check(moved == 1, "the ember heart should hand over exactly one (the fourth) fragment (" + moved + ")", f);
            check(SkyKey.fragmentsHeld(p) == 4 && SkyKey.ready(p), "four fragments after the ember heart should make the key ready", f);
            check(SkyKey.assemble(p, false), "the key did not form", f);
            check(SkyKey.fragmentsHeld(p) == 0 && jp.revati.skyabyss.pouch.Pouch.countAll(p, StorySetup.SKY_KEY.get()) == 1, "after forming: fragments " + SkyKey.fragmentsHeld(p)
                    + ", keys " + p.getInventory().countItem(StorySetup.SKY_KEY.get()), f);
            notes.add("the fourth fragment from the ember heart makes the sky key (stage 4, level cap 40)");
        } finally {
            SkyKey.reset(p);
            for (String t : ProgressRules.TITAN_ORDER) {
                data.clearPersonal(p.getUUID(), t);
                if (before.contains(t + "=false")) {
                    data.clearWorld(t);
                }
            }
            TitanMemoryData.get(server).takePending(p.getUUID());
            ModBlocks.SEALED_GATE.get();
        }
    }

    // ---------------------------------------------------------------- 撃破 (進み・報酬・入口・光の柱・もう一度挑める)
    private static void checkDefeat(MinecraftServer server, ServerLevel back, List<String> f, List<String> notes) {
        boolean worldBefore = ProgressData.get(server).worldStopped(EmberLoot.TITAN);
        int otherEpoch = EmberBackState.of(back, OTHER_GROUP).epoch();
        EmberHeart heart = startHeart(back, f);
        if (heart == null) {
            return;
        }
        heart.forcePhase(2);
        heart.setHealth(1f);
        heart.invulnerableTime = 0;
        heart.hurt(heart.damageSources().genericKill(), 1000f);
        check(!heart.isAlive(), "heart did not die", f);
        check(!EmberHeartBattle.isDefeated(back) && EmberHeartBattle.room().active(), "defeat was reported before the death animation finished", f);
        finishDeath(heart);
        check(heart.isRemoved(), "heart was not removed after the death animation", f);
        check(EmberHeartBattle.isDefeated(back), "defeat was not recorded on the back", f);
        check(!EmberHeartBattle.room().active() && !EmberHeartArena.sealed(back), "entrance did not reopen after the defeat", f);
        UUID group = EmberHeartBattle.lastGroup();
        check(group != null && heart.groupId() != null && heart.groupId().equals(group), "the heart did not belong to the fighting group", f);
        check(group != null && EmberBackState.of(back, group).everDefeated(), "the group did not record that it stilled the heart once (trials would not sleep)", f);
        check(!EmberBackState.of(back, OTHER_GROUP).heartDefeated() && EmberBackState.of(back, OTHER_GROUP).epoch() == otherEpoch,
                "another group's record changed when this group stilled the heart", f);
        check(!EmberHeartArena.isCold() && EmberHeartArena.sinkingCount() == 0, "the room was not restored after the defeat", f);
        EmberHeartBattle.reawakenForTest(back);
        check(!EmberHeartBattle.isDefeated(back), "reawaken did not reset the heart", f);
        check(EmberHeartArena.isBuilt(back) && !EmberHeartArena.sealed(back), "reawaken left the chamber broken", f);
        if (!worldBefore) {
            ProgressData.get(server).clearWorld(EmberLoot.TITAN);
        }
        notes.add("defeat: records the world, opens the door, can be fought again");
    }
}
