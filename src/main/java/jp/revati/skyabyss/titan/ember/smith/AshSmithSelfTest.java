package jp.revati.skyabyss.titan.ember.smith;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 灰の鍛え手の自己テスト (SKYABYSS_SELFTEST。ログに SELFTEST ash smith PASSED / FAILED)。
// 素材 (模型・テクスチャ・発光・白の発光・アニメ・バーの絵・音・武器の模型)、強さ (13 の 5.2節)、部屋の形 (槽・灰溜まり・炉の扉・入口・取っておく場所)、
// 戦いの始まりと封印・保存されない・人数の倍率、技の時間と傷 (鎚打ち 16・噴気 10 と熱・冷えた鎚 24 と輪 12 と噴気なし・鞴の息 8 と熱 35 と弱み 1.5 倍・
// 火花 6 つ 10・怒りの炉の扉と灰被り 2 体・焼き入れの湯気)、放棄、報酬の表 (確率・数・作られていない品は出さない・いない人の分は預かる)、経験値の表、
// 03 の目安で一人で倒せる数値 (倒すのにかかる秒・一撃に耐える回数)、撃破の記録と東の熾り石。
// 余燼の背 (ember_back) の鍛え手の間 (EmberBackLayout.SMITH) に建てて試す。**撃破の記録 (東の石が鳴らせる) を残すので、余燼の背の
// 試練の自己テストよりあとに置く** (13 の 22章)
public final class AshSmithSelfTest {
    private static final int CHUNKS = 2;

    private AshSmithSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ash smith (load)", AshSmithSelfTest::load),
                SelfTestRunner.Step.once("ash smith", 40, AshSmithSelfTest::runSelfTest));
    }

    static void load(MinecraftServer server) {
        ServerLevel back = server.getLevel(EmberIds.BACK);
        if (back != null) {
            force(back, true);
        }
    }

    private static void force(ServerLevel back, boolean on) {
        for (int dx = -CHUNKS; dx <= CHUNKS; dx++) {
            for (int dz = -CHUNKS; dz <= CHUNKS; dz++) {
                back.setChunkForced((AshSmithArena.CX >> 4) + dx, (AshSmithArena.CZ >> 4) + dz, on);
            }
        }
    }

    static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel back = server.getLevel(EmberIds.BACK);
        try {
            if (back == null) {
                failures.add("no ember_back level");
            } else {
                testParty = jp.revati.skyabyss.party.PartyData.get(server).create(PARTY_LEAD, "smith_lead");
                run(server, back, failures, notes);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ash smith crashed", e);
            failures.add("crashed: " + e);
        } finally {
            AshSmith.testFighters = null;
            AshSmith.testLog = null;
            if (back != null) {
                AshSmithBattle.abandon(server);
                force(back, false);
                // 自己テストが使ったグループの記録と部屋の占有を消す
                var data = jp.revati.skyabyss.titan.group.BackGroupData.get(back);
                if (testParty != null) {
                    var parties = jp.revati.skyabyss.party.PartyData.get(server);
                    for (UUID m : List.copyOf(testParty.members)) {
                        parties.remove(m);
                        data.remove(m);
                    }
                    data.remove(testParty.id);
                    testParty = null;
                }
                data.remove(AshSmithBattle.TEST_GROUP);
                data.remove(OTHER_GROUP);
                if (AshSmithBattle.lastGroup() != null) {
                    data.remove(AshSmithBattle.lastGroup());
                }
                jp.revati.skyabyss.titan.group.RoomLocks.release(jp.revati.skyabyss.titan.ember.back.EmberRooms.EAST.lock());
            }
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ash smith PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST ash smith FAILED: {}", f));
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(MinecraftServer server, ServerLevel back, List<String> f, List<String> notes) {
        checkAssets(f);
        checkLang(f);
        checkStats(back, f);
        checkNumbers(f);
        checkStrength(f, notes);
        checkGeometry(f);
        checkArena(back, f);
        checkBattle(server, back, f);
        checkForgeDoor(server, back, f);
        checkMoves(server, back, f, notes);
        checkRage(server, back, f);
        checkAbandon(server, back, f);
        checkLoot(server, f, notes);
        checkDefeat(server, back, f);
    }

    // ---------------------------------------------------------------- 素材と lang
    private static boolean exists(String path) {
        try (InputStream in = AshSmithSelfTest.class.getResourceAsStream(path)) {
            return in != null;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    private static void checkAssets(List<String> f) {
        List<String> paths = new ArrayList<>(List.of(
                "/assets/skyabyss/geo/entity/ash_smith.geo.json",
                "/assets/skyabyss/textures/entity/ash_smith.png",
                "/assets/skyabyss/textures/entity/ash_smith_glowmask.png",
                "/assets/skyabyss/textures/entity/ash_smith_glowmask_white.png",
                "/assets/skyabyss/animations/entity/ash_smith.animation.json",
                "/assets/skyabyss/geo/gear/smith_hammer.geo.json",
                "/assets/skyabyss/textures/gear/smith_hammer.png",
                "/assets/skyabyss/textures/gear/smith_hammer_glowmask.png",
                "/assets/skyabyss/animations/gear/smith_hammer.animation.json",
                "/assets/skyabyss/geo/gear/smith_tongs.geo.json",
                "/assets/skyabyss/textures/gear/smith_tongs.png",
                "/assets/skyabyss/textures/gear/smith_tongs_glowmask.png",
                "/assets/skyabyss/animations/gear/smith_tongs.animation.json",
                "/assets/skyabyss/textures/gui/boss/ash_smith_frame.png",
                "/assets/skyabyss/textures/gui/boss/ash_smith_notches.png",
                "/assets/skyabyss/textures/gui/boss/ash_smith_icon.png"));
        for (String s : EmberSmithSetup.SOUND_NAMES) {
            paths.add("/assets/skyabyss/sounds/ember_smith/" + s + ".ogg");
        }
        for (String p : paths) {
            check(exists(p), "missing asset " + p, f);
        }
        // アニメの名前が Java の使う名前とそろっている
        String anim = text("/assets/skyabyss/animations/entity/ash_smith.animation.json");
        for (String a : new String[] {"idle", "walk", "slam", "slam_cold", "breath", "quench", "sparks", "rage", "hurt", "death"}) {
            check(anim.contains("\"animation.ash_smith." + a + "\""), "animation missing: " + a, f);
        }
        // 受け口の骨 (大鎚・火箸) と鞴・前掛けの骨がある
        String geo = text("/assets/skyabyss/geo/entity/ash_smith.geo.json");
        for (String b : new String[] {"socket_main", "socket_off", "bellows_l", "bellows_r", "apron", "head", "arm_r", "arm_l", "forearm_r", "forearm_l"}) {
            check(geo.contains("\"name\": \"" + b + "\""), "bone missing: " + b, f);
        }
        // 大鎚と火箸は別のモデル (体に焼き込まれていない)
        check(!text("/assets/skyabyss/geo/gear/smith_hammer.geo.json").contains("socket_main"), "the hammer model contains a socket", f);
        // sounds.json に 7 つの音と字幕
        String sounds = text("/assets/skyabyss/sounds.json");
        for (String s : EmberSmithSetup.SOUND_NAMES) {
            check(sounds.contains("\"skyabyss:ember_smith/" + s + "\""), "sounds.json has no " + s, f);
        }
    }

    private static String text(String path) {
        try (InputStream in = AshSmithSelfTest.class.getResourceAsStream(path)) {
            return in == null ? "" : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    static final String[] LANG_KEYS = {"entity.skyabyss.ash_smith", "message.skyabyss.ember.smith.begin", "message.skyabyss.ember.smith.hint_back",
            "message.skyabyss.ember.smith.asleep", "message.skyabyss.ember.smith.breath", "message.skyabyss.ember.smith.quench",
            "message.skyabyss.ember.smith.rage", "message.skyabyss.ember.smith.defeated", "message.skyabyss.ember.smith.after",
            "message.skyabyss.ember.smith.east_stone_ready", "gui.skyabyss.boss.ash_smith.weak", "gui.skyabyss.boss.ash_smith.enraged",
            "subtitles.skyabyss.ash_smith_slam", "subtitles.skyabyss.ash_smith_inhale", "subtitles.skyabyss.ash_smith_blast",
            "subtitles.skyabyss.ash_smith_sparks", "subtitles.skyabyss.ash_smith_rage", "subtitles.skyabyss.ash_smith_hurt",
            "subtitles.skyabyss.ash_smith_death"};

    private static void checkLang(List<String> f) {
        for (String code : new String[] {"ja_jp", "en_us"}) {
            String path = "/assets/skyabyss_ember/lang/" + code + ".json";
            try {
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(text(path)).getAsJsonObject();
                for (String k : LANG_KEYS) {
                    check(json.has(k), code + " has no " + k, f);
                }
            } catch (RuntimeException e) {
                f.add("could not read " + path + ": " + e);
            }
        }
    }

    // ---------------------------------------------------------------- 強さ (5.1節の表)
    private static void checkStats(ServerLevel level, List<String> f) {
        EntityTypeCheck.run(level, f);
    }

    private static final class EntityTypeCheck {
        static void run(ServerLevel level, List<String> f) {
            var type = EmberSmithSetup.ASH_SMITH.get();
            check(BuiltInRegistries.ENTITY_TYPE.getKey(type).equals(EmberIds.id(EmberIds.ASH_SMITH)), "entity id", f);
            AshSmith s = type.create(level);
            check(s != null, "could not create the ash smith", f);
            if (s == null) {
                return;
            }
            check(s.getAttributeBaseValue(Attributes.MAX_HEALTH) == 520.0, "health " + s.getAttributeBaseValue(Attributes.MAX_HEALTH), f);
            check(s.getAttributeBaseValue(Attributes.ARMOR) == 12.0, "armor", f);
            check(s.getAttributeBaseValue(Attributes.ARMOR_TOUGHNESS) == 6.0, "toughness", f);
            check(Math.abs(s.getAttributeBaseValue(Attributes.KNOCKBACK_RESISTANCE) - 0.95) < 1e-6, "knockback resistance", f);
            check(type.fireImmune(), "should not burn in its own fire", f);
            check(Math.abs(type.getHeight() - 4.5f) < 1e-4, "the smith should stand 4.5 blocks tall (" + type.getHeight() + ")", f);
            check(s instanceof EmberMob, "not an EmberMob", f);
            GrowthRewards.Source src = GrowthRewards.MOD_ENEMIES.get(EmberIds.id(EmberIds.ASH_SMITH).toString());
            check(src != null && src.xp() == 220 && src.contentLevel() == 30, "growth source of the smith is not 220 / Lv30: " + src, f);
            check(EmberSmithSetup.FIRST_BONUS == 100, "first-clear bonus should be 100", f);
            s.discard();
        }
    }

    // 技の予告と時間と傷 (13 の 5.2節の表)
    private static void checkNumbers(List<String> f) {
        check(AshSmith.SLAM_WINDUP == 20 && AshSmith.SLAM_RANGE == 7 && AshSmith.SLAM_LENGTH == 8 && AshSmith.SLAM_HALF_WIDTH == 1.5,
                "slam: 1.0 s wind-up, range 7, strip 3 x 8", f);
        check(AshSmith.SLAM_DAMAGE == 16 && AshSmith.VENT_DAMAGE == 10 && AshSmith.VENT_HEAT == 20 && AshSmith.VENT_DELAY == 24,
                "slam: 16, vent 10 and heat +20 after 1.2 s", f);
        check(AshSmith.BREATH_INHALE == 30 && AshSmith.BREATH_RANGE == 10 && AshSmith.BREATH_LENGTH == 10 && AshSmith.BREATH_HALF_ANGLE == 30
                && AshSmith.BREATH_COOLDOWN == 240, "breath: 1.5 s, range 10, 60 degree fan, every 12 s", f);
        check(AshSmith.BREATH_DAMAGE == 8 && AshSmith.BREATH_HEAT == 35 && AshSmith.WEAK_TICKS == 60 && Math.abs(AshSmith.WEAK_MULT - 1.5f) < 1e-4,
                "breath: 8, heat +35, 3 s weak at 1.5x", f);
        check(AshSmith.QUENCH_COOLDOWN == 600 && AshSmith.STEAM_TICKS == 120 && AshSmith.COLD_SLAMS == 3 && AshSmith.COLD_MULT == 1.5f
                && AshSmith.RING_DAMAGE == 12, "quench: every 30 s, steam 6 s, 3 cold slams at 1.5x, ring 12", f);
        check(AshSmith.SPARKS_RANGE == 18 && AshSmith.SPARKS_CHARGE == 16 && AshSmith.SPARK_COUNT == 6 && AshSmith.SPARK_WARN == 20
                && AshSmith.SPARK_DAMAGE == 10, "sparks: range 18, 0.8 s charge, 6 sparks, 1.0 s ring, 10", f);
        check(AshSmith.RAGE_TICKS == 30 && Math.abs(AshSmith.RAGE_SLAM_FACTOR - 0.8f) < 1e-4, "rage: 1.5 s, slam interval x0.8", f);
        check(Math.abs(AshSmith.scaleFor(1) - 1f) < 1e-4 && Math.abs(AshSmith.scaleFor(3) - 2.2f) < 1e-4, "challenger scale", f);
        check(Math.abs(AshSmith.takenFor(10, 2f, true) - 7.5f) < 1e-4 && Math.abs(AshSmith.takenFor(10, 1f, false) - 10f) < 1e-4, "damage taken", f);
        // 形: 扇と帯
        Vec3 o = new Vec3(0, 0, 0);
        Vec3 look = new Vec3(0, 0, 1);
        check(AshSmith.inFan(new Vec3(0, 0, 6), o, look) && AshSmith.inFan(new Vec3(2.9, 0, 6), o, look), "fan: front and 25 degrees in", f);
        check(!AshSmith.inFan(new Vec3(4.5, 0, 6), o, look) && !AshSmith.inFan(new Vec3(0, 0, 11), o, look) && !AshSmith.inFan(new Vec3(0, 0, -6), o, look),
                "fan: 37 degrees, 11 blocks, and behind are out", f);
        check(AshSmith.inStrip(new Vec3(1.2, 0, 7.5), o, look, 8, 1.5) && !AshSmith.inStrip(new Vec3(2.2, 0, 4), o, look, 8, 1.5)
                && !AshSmith.inStrip(new Vec3(0, 0, 8.6), o, look, 8, 1.5) && !AshSmith.inStrip(new Vec3(0, 0, -1), o, look, 8, 1.5), "strip shape", f);
    }

    // 03 の 9.8節の目安 (余燼の段): 武器 +0 は 1 秒あたり 17.5、防具 +0 は防御 22 / 強靭 16、体力 20
    private static void checkStrength(List<String> f, List<String> notes) {
        GearCurve.Stage st = GearCurve.STAGES.get(3);
        double dps = st.dps0();
        double hit = dps / 0.8;
        double perSecond = dps * GearCurve.armorTaken(hit, 12, 6);
        double seconds = AshSmith.HEALTH / perSecond / 0.6;
        check(seconds >= 45 && seconds <= 85, "solo kill time " + String.format("%.0f", seconds) + " s (want 45-85)", f);
        double slam = 20 / (AshSmith.SLAM_DAMAGE * GearCurve.armorTaken(AshSmith.SLAM_DAMAGE, st.armor(), st.toughness()));
        double cold = 20 / (AshSmith.SLAM_DAMAGE * AshSmith.COLD_MULT * GearCurve.armorTaken(AshSmith.SLAM_DAMAGE * AshSmith.COLD_MULT, st.armor(), st.toughness()));
        double breath = 20 / (AshSmith.BREATH_DAMAGE * GearCurve.armorTaken(AshSmith.BREATH_DAMAGE, st.armor(), st.toughness()));
        check(slam >= 5 && slam <= 9, "hits to down a +0 player by a slam " + slam, f);
        check(cold >= 2.5 && cold < slam, "hits to down by a cold slam " + cold, f);
        check(breath >= 10, "hits to down by the breath " + breath, f);
        // 一人 (体力 520) と 4 人 (1 + 0.6 × 3 = 2.8 倍) の差は人数の倍率のとおり
        check(Math.abs(AshSmith.HEALTH * AshSmith.scaleFor(4) - 1456) < 0.5, "4 challengers should face 1456 effective HP", f);
        notes.add(String.format("solo kill %.0f s at 60%% uptime; hits to down: slam %.1f, cold slam %.1f, breath %.1f", seconds, slam, cold, breath));
    }

    // ---------------------------------------------------------------- 部屋の箱
    private static void checkGeometry(List<String> f) {
        EmberBackLayout.Box box = EmberBackLayout.SMITH;
        check(AshSmithArena.BOX.equals(box), "the arena box should be EmberBackLayout.SMITH", f);
        check(box.contains(AshSmithArena.HOME) && box.contains(AshSmithArena.ENTRANCE_INSIDE), "home / entrance outside the box", f);
        check(AshSmithArena.DOOR.equals(EmberBackLayout.SMITH_DOOR), "door", f);
        check(AshSmithArena.ANTE.maxZ() < box.minZ() && !AshSmithArena.ANTE.intersects(box), "ante overlaps the arena", f);
        for (BlockPos t : AshSmithArena.TROUGHS) {
            check(box.contains(t.offset(-2, 1, -2)) && box.contains(t.offset(2, 1, 2)), "trough outside the box at " + t, f);
            check(AshSmithArena.inside(Vec3.atCenterOf(t)), "trough centre not inside", f);
        }
        for (BlockPos p : AshSmithArena.PUDDLES) {
            check(box.contains(p.offset(-1, 0, -1)) && box.contains(p.offset(1, 0, 1)), "puddle outside the box at " + p, f);
        }
        for (BlockPos p : AshSmithArena.PILLARS) {
            check(box.contains(p.offset(1, AshSmithArena.PILLAR_HEIGHT - 1, 1)), "pillar outside the box at " + p, f);
            for (int dx = 0; dx < 2; dx++) {
                for (int dz = 0; dz < 2; dz++) {
                    check(!AshSmithArena.reserved(p.getX() + dx, p.getY(), p.getZ() + dz), "pillar in a reserved place at " + p, f);
                }
            }
        }
        // 取っておく場所に槽・灰溜まり・炉の扉が入っていない
        for (BlockPos t : AshSmithArena.TROUGHS) {
            check(!AshSmithArena.reserved(t.getX() - 2, t.getY() + 1, t.getZ() - 2) && !AshSmithArena.reserved(t.getX() + 2, t.getY() + 1, t.getZ() + 2), "trough in a reserved place", f);
        }
        for (BlockPos p : AshSmithArena.PUDDLES) {
            check(!AshSmithArena.reserved(p.getX(), p.getY(), p.getZ()), "puddle in a reserved place", f);
        }
        check(box.contains(AshSmithArena.FURNACE_DOOR.min()) && box.contains(AshSmithArena.FURNACE_NICHE.max()), "furnace outside the box", f);
        // 槽・灰溜まり・柱は互いに重ならない
        List<BlockPos> centres = new ArrayList<>(AshSmithArena.TROUGHS);
        centres.addAll(AshSmithArena.PUDDLES);
        for (int i = 0; i < centres.size(); i++) {
            for (int j = i + 1; j < centres.size(); j++) {
                int dx = Math.abs(centres.get(i).getX() - centres.get(j).getX());
                int dz = Math.abs(centres.get(i).getZ() - centres.get(j).getZ());
                check(dx > 4 || dz > 4, "trough/puddle overlap " + centres.get(i) + " " + centres.get(j), f);
            }
        }
    }

    private static void checkArena(ServerLevel level, List<String> f) {
        AshSmithBattle.abandon(level.getServer());
        AshSmithArena.build(level, AshSmithArena.BOX);
        check(AshSmithArena.isBuilt(level), "arena not recognised as built", f);
        for (BlockPos t : AshSmithArena.TROUGHS) {
            check(level.getBlockState(t.above()).is(Blocks.WATER) && level.getBlockState(t.above().east(1)).is(Blocks.WATER)
                    && level.getBlockState(t.above().north(1)).is(Blocks.WATER), "trough has no water at " + t, f);
            check(level.getBlockState(t.offset(2, 1, 0)).is(Blocks.POLISHED_BLACKSTONE_BRICKS), "trough has no rim", f);
            check(level.getBlockState(t.offset(3, 1, 0)).isAir() && level.getBlockState(t.offset(-3, 1, 0)).isAir(), "water leaked past the rim", f);
        }
        for (BlockPos p : AshSmithArena.PUDDLES) {
            check(level.getBlockState(p).is(Blocks.GRAY_WOOL) && level.getBlockState(p.offset(1, 0, 1)).is(Blocks.GRAY_WOOL), "puddle is not gray wool at " + p, f);
            check(AshSmithArena.onPuddle(new Vec3(p.getX() + 0.5, p.getY() + 1, p.getZ() + 0.5)), "onPuddle", f);
        }
        check(!AshSmithArena.onPuddle(AshSmithArena.HOME), "the centre is not a puddle", f);
        check(!AshSmithArena.furnaceOpen(level), "furnace is open after a build", f);
        check(level.getBlockState(AshSmithArena.FURNACE_DOOR.min().east()).is(Blocks.IRON_BARS), "furnace door is not closed", f);
        // 中は空き (柱と槽を除く床の上 3 ブロック)、床は全面にある
        check(level.getBlockState(BlockPos.containing(AshSmithArena.HOME)).isAir() && level.getBlockState(BlockPos.containing(AshSmithArena.HOME).above(3)).isAir(),
                "centre is not open", f);
        check(!level.getBlockState(BlockPos.containing(AshSmithArena.ENTRANCE_INSIDE).below()).isAir(), "no floor at the entrance", f);
        check(level.getBlockState(BlockPos.containing(AshSmithArena.ENTRANCE_INSIDE)).isAir(), "entrance is blocked", f);
        // 取っておく場所は空気のまま (B が後で台・石・碑文を置く)
        for (EmberBackLayout.Box r : EmberBackLayout.reserved("smith")) {
            BlockPos mid = r.center();
            check(level.getBlockState(new BlockPos(mid.getX(), r.minY() + 2, mid.getZ())).isAir(), "reserved place was built in at " + mid, f);
        }
        // 入口: 既定は閉じた扉 (火の鍵で開けたグループにだけ開く)。開ける・封じるはそのグループにだけで、扉のブロックはワールドに残る
        UUID g = AshSmithBattle.TEST_GROUP;
        jp.revati.skyabyss.titan.group.GroupGates.place(level, jp.revati.skyabyss.titan.ember.back.EmberBackTrials.boxBlocks(AshSmithArena.DOOR), false);
        check(AshSmithArena.sealed(level, g) && AshSmithArena.sealed(level, OTHER_GROUP), "the smith door is open before any key", f);
        AshSmithArena.open(level, g);
        check(!AshSmithArena.sealed(level, g) && AshSmithArena.sealed(level, OTHER_GROUP)
                && level.getBlockState(AshSmithArena.DOOR.center()).is(jp.revati.skyabyss.registry.ModBlocks.SEALED_GATE.get()),
                "opening the door for one group opened it for another (or removed the door block)", f);
        AshSmithArena.seal(level, g);
        check(AshSmithArena.sealed(level, g), "seal did not close the door", f);
        AshSmithArena.open(level, g);
        check(!AshSmithArena.sealed(level, g), "open did not reopen the door", f);
        // 建て直しても同じ物になる
        AshSmithArena.build(level, AshSmithArena.BOX);
        check(level.getBlockState(AshSmithArena.TROUGHS.get(0).above()).is(Blocks.WATER) && AshSmithArena.isBuilt(level), "rebuild changed the arena", f);
    }

    // ---------------------------------------------------------------- 戦い
    private static AshSmith start(MinecraftServer server, List<String> f) {
        AshSmithBattle.reawaken(server);
        ServerLevel back = AshSmithBattle.level(server);
        check(testParty != null && back != null ? AshSmithBattle.startNow(back, testParty.id) : AshSmithBattle.startNow(server), "battle did not start", f);
        AshSmith s = AshSmithBattle.current(server);
        if (s == null) {
            f.add("no ash smith after starting (boss id " + AshSmithBattle.room().bossId() + ")");
        }
        return s;
    }

    private static void checkBattle(MinecraftServer server, ServerLevel level, List<String> f) {
        AshSmith s = start(server, f);
        if (s == null) {
            return;
        }
        check(AshSmithArena.sealed(level), "door not sealed when the fight starts", f);
        check(s.getTags().contains(TitanBackTrials.HEART_TAG) && s.getTags().contains(AshSmith.SMITH_TAG) && s.getTags().contains(EmberMob.TRIAL_TAG),
                "smith not tagged", f);
        check(!s.shouldBeSaved(), "smith would be saved to disk", f);
        // 鍛え手は戦うグループのもの: ほかのグループの人には見えず、狙わず、その傷を受けない
        FakePlayer other = new FakePlayer(level, new GameProfile(OTHER_GROUP, "smith_other"));
        check(AshSmithBattle.lastGroup() != null && AshSmithBattle.lastGroup().equals(s.groupId()), "the smith has no group", f);
        check(!jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(s, other) && !s.canAttack(other), "another group can see or is targeted by the smith", f);
        float hp = s.getHealth();
        s.invulnerableTime = 0;
        s.hurt(level.damageSources().playerAttack(other), 5f);
        check(s.getHealth() == hp, "another group hurt the smith", f);
        check(Math.abs(s.getMaxHealth() - AshSmith.HEALTH) < 0.01, "max health " + s.getMaxHealth(), f);
        check(AshSmithArena.inside(s.position()), "smith spawned outside the room at " + s.position(), f);
        // 部屋の外へ出たら戻される (BossRoom)
        // 戻す距離 (leash 17) を少し超えるだけ動かし、動かした先のチャンクを先に読んでおく。遠く (+40) の外側のチャンクは、
        // 新しいワールドでは強制読み込みがまだ終わっていないことがあり、そこへ動かした鍛え手が level.getEntity で見つからず
        // BossRoom.tick が「消えた」として戦いを捨て、戻されずにまれに落ちていた
        double outX = AshSmithArena.HOME.x + 22;
        level.getChunk(((int) Math.floor(outX)) >> 4, ((int) Math.floor(AshSmithArena.HOME.z)) >> 4);
        s.moveTo(outX, AshSmithArena.FLOOR + 1, AshSmithArena.HOME.z, 0f, 0f);
        AshSmithBattle.room().tick(level, AshSmithBattle.host());
        check(s.distanceToSqr(AshSmithArena.HOME) < 1, "the smith outside the room was not brought back (alive " + s.isAlive() + ", found "
                + (level.getEntity(s.getUUID()) != null) + ", at " + s.position() + ")", f);
        AshSmithBattle.abandon(server);
    }

    // C2 の扉が開いた知らせ (Forge.onDoorOpened) を、鍛え手が受けている。誰もいなければ戦いは始まらず、部屋は建ったまま
    private static void checkForgeDoor(MinecraftServer server, ServerLevel level, List<String> f) {
        check(jp.revati.skyabyss.titan.ember.back.trial.Forge.doorListeners() >= 1, "the smith did not register on Forge.onDoorOpened", f);
        AshSmithBattle.reawaken(server);
        jp.revati.skyabyss.titan.ember.back.trial.Forge.notifyDoorOpened(level);
        check(!AshSmithBattle.room().active() && AshSmithArena.isBuilt(level), "an empty arena started a fight on the door notice", f);
    }

    // 鍛え手と戦わない、もう一つのグループ
    static final UUID OTHER_GROUP = UUID.nameUUIDFromBytes("skyabyss ash smith other group".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    private static FakePlayer fake(ServerLevel level, String name) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);
        p.setHealth(2000f);
        // テストの人は一つのパーティにまとめ、鍛え手はそのパーティ (グループ) のものとして戦う
        if (testParty != null) {
            jp.revati.skyabyss.party.PartyData.get(level.getServer()).add(testParty, p.getUUID(), name);
        }
        return p;
    }

    static final UUID PARTY_LEAD = UUID.nameUUIDFromBytes("skyabyss ash smith party lead".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    @org.jetbrains.annotations.Nullable
    private static jp.revati.skyabyss.party.PartyData.Party testParty;

    private static void put(FakePlayer p, double x, double y, double z) {
        p.setPos(x, y, z);
        p.setDeltaMovement(Vec3.ZERO);
    }

    private static void tick(AshSmith s, FakePlayer p, int n) {
        for (int i = 0; i < n && s.isAlive(); i++) {
            if (p != null) {
                p.invulnerableTime = 0;
            }
            s.tick();
        }
    }

    // 与えた傷の記録 (種類ごとの合計)。偽のプレイヤーは傷を受けないので AshSmith.testLog で数える
    private static final Map<String, Float> LOG = new HashMap<>();

    private static float lost(FakePlayer p) {
        return LOG.values().stream().reduce(0f, Float::sum);
    }

    private static float got(String kind) {
        return LOG.getOrDefault(kind, 0f);
    }

    private static void heal(FakePlayer p) {
        LOG.clear();
        p.setHealth(p.getMaxHealth());
        p.invulnerableTime = 0;
    }

    // 技を一つずつ回して、傷と時間を見る (人がいないと回らないので、偽のプレイヤーを戦っている人にする)
    private static void checkMoves(MinecraftServer server, ServerLevel level, List<String> f, List<String> notes) {
        AshSmith s = start(server, f);
        if (s == null) {
            return;
        }
        FakePlayer p = fake(level, "ash_smith_test");
        AshSmith.testFighters = List.of(p);
        AshSmith.testLog = (pl, amount, kind) -> LOG.merge(kind, amount, Float::sum);
        LOG.clear();
        s.passive = true;
        double fy = AshSmithArena.FLOOR + 1;
        double bx = AshSmithArena.CX + 0.5;
        // 鎚打ち: 前方の帯の中 (距離 4) は 16、帯の外 (横 4) は無傷
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        s.setNoAi(false);
        put(p, bx, fy, AshSmithArena.CZ - 2.5);
        EmberHeat.reset(p);
        s.forceAction(AshSmith.Action.SLAM);
        tick(s, p, AshSmith.SLAM_WINDUP - 1);
        check(lost(p) == 0, "the slam hit before its 1.0 s wind-up ended (" + lost(p) + ")", f);
        tick(s, p, 2);
        check(Math.abs(got("slam") - AshSmith.SLAM_DAMAGE) < 0.01f, "slam damage should be 16 (" + got("slam") + ")", f);
        check(s.ventCount() == 1, "the slam left no vent (" + s.ventCount() + ")", f);
        tick(s, p, AshSmith.VENT_DELAY - 4);
        check(got("vent") == 0, "the vent hurt before 1.2 s (" + got("vent") + ")", f);
        tick(s, p, 8);
        check(Math.abs(got("vent") - AshSmith.VENT_DAMAGE) < 0.01f, "vent damage should be 10 (" + got("vent") + ")", f);
        check(EmberHeat.get(p) > 7 && EmberHeat.get(p) <= AshSmith.VENT_HEAT + 0.01f, "vent heat should be about +20 (" + EmberHeat.get(p) + ")", f);
        check(s.ventCount() == 0, "the vent did not end", f);
        tick(s, p, 40);
        check(s.currentAction() == AshSmith.Action.NONE, "the slam did not finish (" + s.currentAction() + ")", f);
        heal(p);
        put(p, bx, fy, AshSmithArena.CZ - 2.5);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        s.forceAction(AshSmith.Action.SLAM);
        tick(s, p, 1);
        // 帯の向きは溜めの始まりで決まる。決まったあとで、横へ 4.5 離れる
        put(p, bx + 4.5, fy, AshSmithArena.CZ - 2.5);
        tick(s, p, AshSmith.SLAM_WINDUP + 40);
        check(lost(p) == 0, "a player outside the strip was hurt (" + lost(p) + ")", f);
        // 鞴の息: 扇の中は 8・熱 35・押される、吐いたあと 3 秒は後ろからの傷 1.5 倍
        heal(p);
        EmberHeat.reset(p);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        put(p, bx, fy, AshSmithArena.CZ - 1.5);
        s.forceAction(AshSmith.Action.BREATH);
        tick(s, p, AshSmith.BREATH_INHALE - 1);
        check(lost(p) == 0 && !s.isWeak(), "the breath came before 1.5 s", f);
        tick(s, p, 2);
        check(Math.abs(got("breath") - AshSmith.BREATH_DAMAGE) < 0.01f, "breath damage should be 8 (" + got("breath") + ")", f);
        check(EmberHeat.get(p) > 14 && EmberHeat.get(p) <= AshSmith.BREATH_HEAT + 0.01f, "breath heat should be about +35 (" + EmberHeat.get(p) + ")", f);
        check(p.getDeltaMovement().horizontalDistance() > 1.0, "the breath did not push (" + p.getDeltaMovement() + ")", f);
        check(s.isWeak(), "the bellows did not open after the breath", f);
        tick(s, p, 20);
        // 後ろ (向きの反対) と前から同じ傷を入れる
        float front = hitFrom(s, p, fy, true);
        float rear = hitFrom(s, p, fy, false);
        // 受ける傷が 1.5 倍になる (防具 12 / 強靭 6 を通す前)。通したあとの値で見る
        double wantFront = 10 * GearCurve.armorTaken(10, 12, 6);
        double wantRear = 15 * GearCurve.armorTaken(15, 12, 6);
        check(Math.abs(front * 10 - wantFront) < 0.05 && Math.abs(rear * 10 - wantRear) < 0.05,
                "rear hit should be 10 x1.5 before armor (front " + front * 10 + " want " + wantFront + ", rear " + rear * 10 + " want " + wantRear + ")", f);
        check(rear > front * 1.4, "the rear hit was not clearly bigger than the front hit", f);
        tick(s, p, AshSmith.WEAK_TICKS);
        check(!s.isWeak(), "the weak window did not end after 3 s", f);
        float rear2 = hitFrom(s, p, fy, false);
        check(Math.abs(rear2 - front) < 0.01f, "rear hit after the window should be normal (" + rear2 + " vs " + front + ")", f);
        // 扇の外は無傷
        heal(p);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        put(p, bx, fy, AshSmithArena.CZ - 1.5);
        s.forceAction(AshSmith.Action.BREATH);
        tick(s, p, AshSmith.BREATH_INHALE - 3);
        // 向きは吸っている間に決まる (吐く 6 tick 前まで追う)。決まったあとで、扇の外 (横へ 5) へ出る
        put(p, bx + 5, fy, AshSmithArena.CZ - 3.5);
        tick(s, p, 60);
        check(lost(p) == 0, "a player outside the fan was hurt (" + lost(p) + ")", f);
        // 焼き入れ: 槽へ歩き、湯気 6 秒、鎚が冷える。冷えた鎚打ち 3 回: 24・噴気なし・輪 12 (跳べば当たらない)
        heal(p);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        put(p, bx, fy, AshSmithArena.CZ + 8);
        s.forceAction(AshSmith.Action.QUENCH);
        boolean steamed = false;
        int ticks = 0;
        while (s.currentAction() == AshSmith.Action.QUENCH && ticks < 700) {
            tick(s, p, 1);
            steamed |= s.steamTicks() > 0;
            ticks++;
        }
        check(s.currentAction() == AshSmith.Action.NONE, "the quench did not finish (" + s.currentAction() + ", " + ticks + " ticks)", f);
        int steamLeft = AshSmith.STEAM_TICKS - (AshSmith.QUENCH_DUNK - AshSmith.STEAM_START);
        check(steamed && Math.abs(s.steamTicks() - steamLeft) <= 3, "steam should start 0.6 s into the dunk (" + s.steamTicks() + ", want " + steamLeft + ")", f);
        check(s.isCold() && s.coldSlams() == AshSmith.COLD_SLAMS, "the hammer did not turn cold (" + s.coldSlams() + ")", f);
        double side = s.steamSide();
        check(side == -1 || side == 1, "steam side", f);
        tick(s, p, AshSmith.STEAM_TICKS);
        check(s.steamTicks() == 0, "the steam did not end after 6 s (" + s.steamTicks() + ")", f);
        heal(p);
        put(p, bx, fy, s.getZ() + 3.0);
        s.moveTo(bx, fy, s.getZ(), 0f, 0f);
        float coldSlam = coldSlam(s, p, 0f, fy);
        check(Math.abs(got("slam") - AshSmith.SLAM_DAMAGE * AshSmith.COLD_MULT) < 0.01f, "cold slam should be 24 (" + got("slam") + ")", f);
        check(s.ventCount() == 0 && s.ringCount() == 1, "a cold slam makes a ring and no vent (" + s.ventCount() + ", " + s.ringCount() + ")", f);
        check(s.coldSlams() == 2, "cold slams left " + s.coldSlams(), f);
        // 輪: 立っていれば 12、跳んでいれば当たらない
        heal(p);
        Vec3 impact = s.position().add(0, 0, 3.5);
        put(p, impact.x, fy, impact.z + 6.0);
        tick(s, p, 30);
        check(Math.abs(got("ring") - AshSmith.RING_DAMAGE) < 0.01f, "ring damage should be 12 when standing (" + got("ring") + ")", f);
        heal(p);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        put(p, bx, fy, AshSmithArena.CZ - 3.0);
        s.forceAction(AshSmith.Action.SLAM);
        tick(s, p, AshSmith.SLAM_WINDUP + 1);
        heal(p);
        put(p, bx, fy + 1.4, AshSmithArena.CZ + 4.5);
        tick(s, p, 30);
        check(lost(p) == 0, "a jumping player was hit by the ring (" + lost(p) + ")", f);
        // 3 回めでもとの鎚へ
        for (int i = 0; i < 2 && s.isCold(); i++) {
            s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
            put(p, bx, fy, AshSmithArena.CZ + 10);
            s.forceAction(AshSmith.Action.SLAM);
            tick(s, p, AshSmith.SLAM_WINDUP + 36);
        }
        check(!s.isCold() && s.coldSlams() == 0, "the hammer did not return to normal after 3 cold slams (" + s.coldSlams() + ")", f);
        // 火花: 溜め 0.8 秒、6 つ、それぞれ 1.0 秒の輪のあとに 10
        heal(p);
        s.moveTo(bx, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        put(p, bx, fy, AshSmithArena.CZ + 8);
        s.forceAction(AshSmith.Action.SPARKS);
        tick(s, p, AshSmith.SPARKS_CHARGE - 1);
        check(s.sparkCount() == 0, "sparks appeared before the 0.8 s charge ended", f);
        tick(s, p, 2);
        check(s.sparkCount() == AshSmith.SPARK_COUNT, "sparks should be 6 (" + s.sparkCount() + ")", f);
        check(lost(p) == 0, "a spark landed at once", f);
        int firstLand = AshSmith.SPARK_WARN;
        tick(s, p, firstLand + 2);
        float dmg = got("spark");
        check(dmg >= AshSmith.SPARK_DAMAGE - 0.01f && Math.abs(dmg / AshSmith.SPARK_DAMAGE - Math.round(dmg / AshSmith.SPARK_DAMAGE)) < 0.01f,
                "the spark that aims at the player should do 10 (" + dmg + ")", f);
        tick(s, p, 120);
        check(s.sparkCount() == 0, "sparks did not all land (" + s.sparkCount() + ")", f);
        check(s.currentAction() == AshSmith.Action.NONE, "sparks did not finish", f);
        notes.add("moves ran: slam, vent, cold slam and ring, breath and weak, quench and steam, sparks");
        AshSmith.testFighters = null;
        AshSmithBattle.abandon(server);
    }

    // 後ろ (rear=true)・前から、傷 10 を入れたときに減る体力 (人数 1 人の倍率 1)
    private static float hitFrom(AshSmith s, FakePlayer p, double fy, boolean front) {
        s.moveTo(AshSmithArena.CX + 0.5, fy, AshSmithArena.CZ - 6.5, 0f, 0f);
        s.setYBodyRot(0f);
        double dz = front ? 3.0 : -3.0;
        put(p, s.getX(), fy, s.getZ() + dz);
        s.setHealth(s.getMaxHealth());
        s.invulnerableTime = 0;
        float before = s.getHealth();
        s.hurt(s.damageSources().playerAttack(p), 10f);
        return before - s.getHealth() == 0 ? 0 : (before - s.getHealth()) / 10f * (front ? 1f : 1f);
    }

    private static float coldSlam(AshSmith s, FakePlayer p, float yaw, double fy) {
        s.setYBodyRot(yaw);
        s.forceAction(AshSmith.Action.SLAM);
        float before = lost(p);
        tick(s, p, AshSmith.SLAM_WINDUP + 1);
        return lost(p) - before;
    }

    // 怒り: 体力 50% を切ると 1 度だけ。炉の扉が開き、灰被りが 2 体出る。部屋の中の灰被りは放棄・撃破で消える
    private static void checkRage(MinecraftServer server, ServerLevel level, List<String> f) {
        AshSmith s = start(server, f);
        if (s == null) {
            return;
        }
        FakePlayer p = fake(level, "ash_smith_rage_test");
        AshSmith.testFighters = List.of(p);
        s.passive = true;
        put(p, AshSmithArena.CX + 0.5, AshSmithArena.FLOOR + 1, AshSmithArena.CZ + 2);
        check(!s.raged() && !s.isEnraged() && !AshSmithArena.furnaceOpen(level), "the smith started enraged", f);
        s.setHealth(s.getMaxHealth() * 0.45f);
        tick(s, p, 2);
        check(s.currentAction() == AshSmith.Action.RAGE && s.raged() && s.isEnraged(), "no rage below 50% (" + s.currentAction() + ")", f);
        tick(s, p, AshSmith.RAGE_TICKS / 2 + 1);
        check(AshSmithArena.furnaceOpen(level), "the furnace door did not open", f);
        List<AshThrall> thralls = level.getEntitiesOfClass(AshThrall.class, AshSmithArena.BOX.aabb(), t -> t.getTags().contains(AshSmith.SMITH_TAG));
        check(thralls.size() == 2, "rage should call 2 ash thralls (" + thralls.size() + ")", f);
        for (AshThrall t : thralls) {
            check(!t.shouldBeSaved(), "a called thrall would be saved", f);
            check(AshSmithArena.inside(t.position()), "a called thrall is outside the room", f);
        }
        tick(s, p, AshSmith.RAGE_TICKS);
        check(s.currentAction() != AshSmith.Action.RAGE, "the rage did not end", f);
        s.setHealth(s.getMaxHealth() * 0.4f);
        tick(s, p, 2);
        check(s.currentAction() != AshSmith.Action.RAGE, "the rage came twice", f);
        AshSmith.testFighters = null;
        AshSmithBattle.abandon(server);
        check(level.getEntitiesOfClass(AshThrall.class, AshSmithArena.BOX.aabb().inflate(6), t -> t.getTags().contains(AshSmith.SMITH_TAG)).stream()
                .noneMatch(AshThrall::isAlive) || level.getEntitiesOfClass(AshThrall.class, AshSmithArena.BOX.aabb().inflate(6),
                        t -> t.getTags().contains(AshSmith.SMITH_TAG) && t.isAlive()).isEmpty(), "called thralls stayed after the abandon", f);
        check(!AshSmithArena.furnaceOpen(level) && !AshSmithArena.sealed(level), "the furnace or door was not restored", f);
    }

    // 誰もいなければ 3 秒で放棄
    private static void checkAbandon(MinecraftServer server, ServerLevel level, List<String> f) {
        AshSmith s = start(server, f);
        if (s == null) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            AshSmithBattle.room().tick(level, AshSmithBattle.host());
        }
        check(!AshSmithBattle.room().active() && !s.isAlive() && !AshSmithArena.sealed(level), "the empty room was not abandoned", f);
    }

    // ---------------------------------------------------------------- 報酬
    private static void checkLoot(MinecraftServer server, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(20261017L);
        int n = 4000;
        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (AshSmithLoot.Draw d : AshSmithLoot.roll(random, id -> true)) {
                hits.merge(d.id(), 1, Integer::sum);
                AshSmithLoot.Entry e = AshSmithLoot.TABLE.stream().filter(t -> t.id().equals(d.id())).findFirst().orElseThrow();
                check(d.count() >= e.min() && d.count() <= e.max(), "count out of range for " + d.id() + ": " + d.count(), f);
            }
        }
        for (AshSmithLoot.Entry e : AshSmithLoot.TABLE) {
            double rate = hits.getOrDefault(e.id(), 0) / (double) n;
            check(Math.abs(rate - e.chance()) < 0.03, "chance of " + e.id() + " is " + rate + " (want " + e.chance() + ")", f);
        }
        check(AshSmithLoot.TABLE.size() == 3 && AshSmithLoot.TABLE.get(0).min() == 2 && AshSmithLoot.TABLE.get(0).max() == 4
                && AshSmithLoot.TABLE.get(1).min() == 1 && AshSmithLoot.TABLE.get(1).max() == 2 && AshSmithLoot.TABLE.get(2).min() == 1
                && AshSmithLoot.TABLE.get(2).max() == 2, "loot counts differ from 6.2", f);
        check(Math.abs(AshSmithLoot.HAMMER_CHANCE - 0.05f) < 1e-6 && AshSmithLoot.TABLE.stream().noneMatch(e -> e.id().equals(AshSmithLoot.HAMMER)),
                "the smith hammer should be drawn once at 5% through EmberGearLoot", f);
        int none = 0;
        for (int i = 0; i < 200; i++) {
            none += AshSmithLoot.roll(random, id -> false).size();
        }
        check(none == 0, "unmade items were drawn", f);
        // 大鎚 5% (EmberGearLoot。登録されていれば)
        int hammers = 0;
        for (int i = 0; i < 4000; i++) {
            hammers += jp.revati.skyabyss.titan.ember.gear.EmberGearLoot.rollSmith(null, null, random).size();
        }
        check(Math.abs(hammers / 4000.0 - 0.05) < 0.02 || !AshSmithLoot.registered(AshSmithLoot.HAMMER), "hammer rate " + hammers / 4000.0, f);
        List<String> missing = new ArrayList<>();
        for (AshSmithLoot.Entry e : AshSmithLoot.TABLE) {
            if (!AshSmithLoot.registered(e.id())) {
                missing.add(e.id().substring(e.id().indexOf(':') + 1));
            }
        }
        if (!AshSmithLoot.registered(AshSmithLoot.HAMMER)) {
            missing.add("smith_hammer");
        }
        notes.add("not yet registered (left out of the draw): " + (missing.isEmpty() ? "none" : String.join(", ", missing)));
        // いない人の分は預かる (経験値 50 は必ず)
        UUID fake = UUID.randomUUID();
        AshSmithLoot.onDefeat(server.overworld(), List.of(fake));
        TitanMemoryData memory = TitanMemoryData.get(server);
        check(memory.hasPending(fake), "rewards for an absent player were not held", f);
        TitanMemoryData.Pending held = memory.takePending(fake);
        check(held.xp == AshSmithLoot.VANILLA_XP, "held xp should be " + AshSmithLoot.VANILLA_XP + " (" + held.xp + ")", f);
    }

    // 撃破: 記録 (東の熾り石が鳴らせる)・入口が開く・呼んだ灰被りが消える
    private static void checkDefeat(MinecraftServer server, ServerLevel level, List<String> f) {
        AshSmith s = start(server, f);
        if (s == null) {
            return;
        }
        AshThrall helper = jp.revati.skyabyss.titan.ember.mob.EmberMobSetup.ASH_THRALL.get().create(level);
        helper.moveTo(AshSmithArena.HOME.x + 3, AshSmithArena.FLOOR + 1, AshSmithArena.HOME.z, 0f, 0f);
        helper.addTag(AshSmith.SMITH_TAG);
        level.addFreshEntity(helper);
        s.setHealth(1f);
        s.invulnerableTime = 0;
        s.hurt(s.damageSources().genericKill(), 1000f);
        check(!s.isAlive(), "the smith did not die", f);
        UUID group = AshSmithBattle.lastGroup();
        check(group != null && AshSmithBattle.done(level) && EmberBackState.of(level, group).ready(EmberBackState.Trial.EAST),
                "the defeat did not make the east stone ready", f);
        check(!EmberBackState.of(level, OTHER_GROUP).ready(EmberBackState.Trial.EAST), "the defeat made another group's east stone ready", f);
        check(!AshSmithBattle.room().active() && !AshSmithArena.sealed(level), "the door did not reopen after the defeat", f);
        check(!helper.isAlive(), "the called thrall stayed after the defeat", f);
        // 倒した周は、部屋に入っても戦いは始まらない (BossRoom は動かない)
        check(AshSmithBattle.done(level), "done() should hold after the defeat", f);
    }
}
