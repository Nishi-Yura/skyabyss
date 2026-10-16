package jp.revati.skyabyss.titan.storm.raiju;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.loot.TitanMemoryData;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CopperBulbBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.phys.Vec3;

// 雷獣の自己テスト (SKYABYSS_SELFTEST)。結果はログに SELFTEST raiju PASSED / FAILED。
// 素材 (模型・テクスチャ・発光・アニメ・バーの絵・音)、巣の形 (柱 6 本・栓・入口・天井)、接地の栓 (8 秒・待ち・見た目)、
// 戦いの始まりと封印、保存されない、体力と人数の倍率、接地した柱へ渡ると痺れて落ちる (受ける傷 1.5 倍)、接地していない柱には乗る、
// すべての技が最後まで回る、50% の咆哮、巣の外へ出た雷獣を戻す、誰もいなければ放棄、報酬の表 (確率・数・作られていない品は出さない・
// いない人の分は預かる)、撃破の記録と、もう一度挑めるようにする。背中がまだ無ければ、仮の巣 (オーバーワールドの空) で試す
public final class RaijuSelfTest {
    private RaijuSelfTest() {
    }

    // 呼ぶのは SelfTestRunner: 読み込み (load) → 決まった tick 待つ → 試す (runSelfTest)
    public static void load(MinecraftServer server) {
        RaijuNest.Site site = RaijuNest.site(server);
        ChunkPos c = new ChunkPos(site.center());
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                site.level().setChunkForced(c.x + dx, c.z + dz, true);
            }
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> failures = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        try {
            run(server, failures, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST raiju crashed", e);
            failures.add("crashed: " + e);
        }
        if (failures.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST raiju PASSED ({})", String.join("; ", notes));
        } else {
            failures.forEach(f -> SkyAbyss.LOGGER.error("SELFTEST raiju FAILED: {}", f));
        }
        RaijuNest.Site site = RaijuNest.site(server);
        ChunkPos c = new ChunkPos(site.center());
        for (int dx = -2; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                site.level().setChunkForced(c.x + dx, c.z + dz, false);
            }
        }
    }

    private static void check(boolean ok, String message, List<String> failures) {
        if (!ok) {
            failures.add(message);
        }
    }

    private static void run(MinecraftServer server, List<String> failures, List<String> notes) {
        RaijuNest.Site site = RaijuNest.site(server);
        notes.add(site.temporary() ? "temporary nest" : "storm back nest");
        checkAssets(failures);
        checkTimings(failures);
        checkLang(failures);
        GrowthRewards.Source src = GrowthRewards.MOD_ENEMIES.get(SkyAbyss.MODID + ":raiju");
        check(src != null && src.xp() == 170 && src.contentLevel() == 24, "growth source for raiju is not 170 / Lv24", failures);
        checkNest(site, failures);
        checkPlugs(site, failures);
        checkBattle(server, site, failures, notes);
        checkLoot(server, failures, notes);
        checkDefeat(server, site, failures);
    }

    private static void checkAssets(List<String> failures) {
        List<String> paths = new ArrayList<>(List.of(
                "/assets/skyabyss/geo/entity/raiju.geo.json",
                "/assets/skyabyss/textures/entity/raiju.png",
                "/assets/skyabyss/textures/entity/raiju_glowmask.png",
                "/assets/skyabyss/animations/entity/raiju.animation.json",
                "/assets/skyabyss/textures/gui/boss/raiju_frame.png",
                "/assets/skyabyss/textures/gui/boss/raiju_notches.png",
                "/assets/skyabyss/textures/gui/boss/raiju_icon.png",
                "/assets/skyabyss_storm/lang/parts/raiju.json"));
        for (String s : RaijuSetup.SOUND_NAMES) {
            paths.add("/assets/skyabyss/sounds/raiju/" + s + ".ogg");
        }
        for (String p : paths) {
            try (InputStream in = RaijuSelfTest.class.getResourceAsStream(p)) {
                check(in != null, "missing asset " + p, failures);
            } catch (java.io.IOException e) {
                failures.add("could not read " + p);
            }
        }
        // アニメの名前が Java の使う名前とそろっている
        try (InputStream in = RaijuSelfTest.class.getResourceAsStream("/assets/skyabyss/animations/entity/raiju.animation.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                for (String a : new String[] {"idle", "walk", "perch", "rail_charge", "land", "pounce_crouch", "pounce_leap", "pounce_land",
                        "claw", "roar", "stun_fall", "stunned", "stand_up", "leap_up", "hurt", "death"}) {
                    check(json.contains("\"animation.raiju." + a + "\""), "animation missing: " + a, failures);
                }
            }
        } catch (java.io.IOException e) {
            failures.add("could not read the raiju animations");
        }
    }

    // 技の予告と時間 (12 の 5.2節): 渡りの線 1.0 秒、飛びかかりの溜め 0.8 秒 (怒り 0.6 秒)、痺れ 4 秒、床で 10 秒、栓 8 秒・待ち 4 秒
    private static void checkTimings(List<String> failures) {
        check(Raiju.RAIL_WARN == 20, "rail warning should be 1.0 s (" + Raiju.RAIL_WARN + " ticks)", failures);
        check(Raiju.POUNCE_WINDUP == 16 && Raiju.POUNCE_WINDUP_ANGRY == 12, "pounce wind-up should be 0.8 s / 0.6 s", failures);
        check(Raiju.STUN_TICKS == 80 && Math.abs(Raiju.STUN_TAKEN - 1.5f) < 1e-4, "stun should be 4 s at 1.5x", failures);
        check(Raiju.GROUND_TICKS == 200, "back to a rod after 10 s on the floor (" + Raiju.GROUND_TICKS + ")", failures);
        check(RaijuNest.GROUND_TICKS == 160 && RaijuNest.PLUG_WAIT == 80, "plug should ground 8 s and wait 4 s", failures);
        check(Raiju.RAIL_DAMAGE == 12 && Raiju.POUNCE_DAMAGE == 11 && Raiju.CLAW_DAMAGE == 5 && Raiju.ROAR_STRIKE_DAMAGE == 9
                && Raiju.ROAR_RINGS == 6, "move damage differs from 12 section 5.2", failures);
    }

    // 文は伝承の担当の skyabyss_storm の lang にまとまっている (鍵が日英ともにある)
    static final String[] LANG_KEYS = {"entity.skyabyss.raiju", "gui.skyabyss.boss.raiju.angry", "gui.skyabyss.boss.raiju.stunned",
            "message.skyabyss.raiju.after", "message.skyabyss.raiju.asleep", "message.skyabyss.raiju.begin", "message.skyabyss.raiju.defeated",
            "message.skyabyss.raiju.east_stone_ready", "message.skyabyss.raiju.go", "message.skyabyss.raiju.go_temporary",
            "message.skyabyss.raiju.stunned", "message.skyabyss.raiju.hint_plug", "message.skyabyss.raiju.plug_already",
            "message.skyabyss.raiju.grounded", "message.skyabyss.raiju.plug_wait", "message.skyabyss.raiju.roar",
            "subtitles.skyabyss.raiju_ambient", "subtitles.skyabyss.raiju_hurt", "subtitles.skyabyss.raiju_death",
            "subtitles.skyabyss.raiju_rail_charge", "subtitles.skyabyss.raiju_leap", "subtitles.skyabyss.raiju_pounce",
            "subtitles.skyabyss.raiju_claw", "subtitles.skyabyss.raiju_roar", "subtitles.skyabyss.raiju_stun", "subtitles.skyabyss.ground_plug"};

    private static void checkLang(List<String> failures) {
        for (String code : new String[] {"ja_jp", "en_us"}) {
            String path = "/assets/skyabyss_storm/lang/" + code + ".json";
            try (InputStream in = RaijuSelfTest.class.getResourceAsStream(path)) {
                if (in == null) {
                    failures.add("missing " + path);
                    continue;
                }
                com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(
                        new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for (String k : LANG_KEYS) {
                    check(json.has(k), code + " has no " + k, failures);
                }
            } catch (java.io.IOException | RuntimeException e) {
                failures.add("could not read " + path + ": " + e);
            }
        }
    }

    private static void checkNest(RaijuNest.Site site, List<String> failures) {
        RaijuBattle.abandon(site.level().getServer());
        RaijuNest.build(site);
        ServerLevel level = site.level();
        check(RaijuNest.isBuilt(site), "nest not recognised as built", failures);
        for (int i = 0; i < RaijuNest.RODS; i++) {
            BlockPos top = site.rodTop(i);
            check(level.getBlockState(top).is(Blocks.LIGHTNING_ROD), "rod " + i + " has no lightning rod on top", failures);
            check(level.getBlockState(top.below()).is(Blocks.WAXED_COPPER_BULB), "rod " + i + " has no bulb", failures);
            check(level.getBlockState(site.plug(i)).is(Blocks.LEVER), "rod " + i + " has no plug", failures);
            check(level.getBlockState(top.above()).isAir() && level.getBlockState(top.above(3)).isAir(), "no room above rod " + i, failures);
            check(site.inside(site.dropPoint(i)), "drop point of rod " + i + " is outside the nest", failures);
            Vec3 base = Vec3.atBottomCenterOf(site.rodBase(i)).add(1.5, 0, 0);
            check(top.equals(StormRods.find(level, base, 3.0)), "StormRods.find does not find rod " + i, failures);
            double r = Math.sqrt(site.rodBase(i).distSqr(site.center().atY(site.floor())));
            check(Math.abs(r - RaijuNest.ROD_RADIUS) < 1.0, "rod " + i + " is not on the radius 9 ring (" + r + ")", failures);
        }
        BlockPos c = site.center();
        check(level.getBlockState(c.offset(3, 1, 3)).isAir() && level.getBlockState(new BlockPos(c.getX() + 3, site.ceiling() - 1, c.getZ() + 3)).isAir(),
                "nest interior is not open", failures);
        check(!level.getBlockState(new BlockPos(c.getX(), site.ceiling(), c.getZ())).isAir(), "nest has no ceiling", failures);
        check(!level.getBlockState(c.below()).isAir(), "nest has no floor", failures);
        check(site.ceiling() - site.floor() == 15, "nest height should be 15 (" + (site.ceiling() - site.floor()) + ")", failures);
        for (BlockPos g : site.gate()) {
            check(level.getBlockState(g).isAir(), "entrance closed at " + g, failures);
        }
        check(site.ante().contains(site.anteSpawn()) && !site.inside(site.anteSpawn()), "antechamber spawn is wrong", failures);
        check(site.inside(site.entranceInside()) && site.inside(site.home()), "entrance / home not inside", failures);
        check(level.getBlockState(BlockPos.containing(site.anteSpawn())).isAir() && !level.getBlockState(BlockPos.containing(site.anteSpawn()).below()).isAir(),
                "cannot stand at the antechamber spawn", failures);
    }

    private static void checkPlugs(RaijuNest.Site site, List<String> failures) {
        ServerLevel level = site.level();
        int i = 2;
        BlockPos top = site.rodTop(i);
        check(!StormRods.isGrounded(top), "rod grounded before pulling", failures);
        check(RaijuNest.pull(site, i, null), "plug could not be pulled", failures);
        check(StormRods.isGrounded(top), "pulled rod is not grounded", failures);
        check(!RaijuNest.pull(site, i, null), "a grounded rod could be pulled again", failures);
        check(!level.getBlockState(top.below()).getValue(CopperBulbBlock.LIT), "grounded rod's bulb still lit", failures);
        check(level.getBlockState(site.plug(i)).getValue(LeverBlock.POWERED), "grounded rod's plug not down", failures);
        check(level.getBlockState(site.rodBase(i).offset(1, -1, 0)).is(Blocks.WAXED_OXIDIZED_COPPER_GRATE), "grounded rod's foot not darkened", failures);
    }

    private static Raiju startRaiju(MinecraftServer server, List<String> failures) {
        RaijuBattle.reawaken(server);
        check(RaijuBattle.startNow(server), "battle did not start", failures);
        Raiju r = RaijuBattle.current(server);
        if (r == null) {
            RaijuNest.Site site = RaijuNest.site(server);
            failures.add("no raiju after starting (difficulty " + site.level().getDifficulty() + ", raiju nearby "
                    + site.level().getEntitiesOfClass(Raiju.class, site.arena().inflate(8)).size() + ", boss id " + RaijuBattle.room().bossId()
                    + ", entity ticking " + site.level().isPositionEntityTicking(site.rodTop(0)) + ")");
        }
        return r;
    }

    private static void tick(Raiju r, int ticks) {
        for (int i = 0; i < ticks && r.isAlive(); i++) {
            r.tick();
        }
    }

    private static void checkBattle(MinecraftServer server, RaijuNest.Site site, List<String> failures, List<String> notes) {
        Raiju r = startRaiju(server, failures);
        if (r == null) {
            return;
        }
        check(RaijuNest.sealed(site), "entrance not sealed when the fight starts", failures);
        check(r.getTags().contains(TitanBackTrials.HEART_TAG) && r.getTags().contains(Raiju.TRIAL_TAG), "raiju not tagged", failures);
        check(!r.shouldBeSaved(), "raiju would be saved to disk", failures);
        check(Math.abs(r.getMaxHealth() - Raiju.HEALTH) < 0.01, "max health is " + r.getMaxHealth(), failures);
        check(Math.abs(Raiju.scaleFor(1) - 1f) < 1e-4 && Math.abs(Raiju.scaleFor(3) - 2.2f) < 1e-4, "challenger scale is wrong", failures);
        check(Math.abs(Raiju.takenFor(10, 2f, true) - 7.5f) < 1e-4 && Math.abs(Raiju.takenFor(10, 1f, false) - 10f) < 1e-4, "damage taken is wrong", failures);
        check(r.mode() == Raiju.MODE_PERCH && r.rod() == 0, "raiju did not start on rod 1 (mode " + r.mode() + ", rod " + r.rod() + ")", failures);
        check(r.isNoGravity(), "perched raiju falls", failures);
        // 接地した柱へ渡ると、痺れて落ちる
        int g = 3;
        StormRods.ground(site.rodTop(g), 400);
        r.railTo(g);
        tick(r, Raiju.RAIL_WARN + Raiju.RAIL_TRAVEL + 2);
        check(r.isStunned(), "raiju did not fall on a grounded rod (mode " + r.mode() + ")", failures);
        check(site.inside(r.position()) && Math.abs(r.getY() - site.floor()) < 0.6, "stunned raiju is not on the floor at " + r.position(), failures);
        float h0 = r.getHealth();
        r.invulnerableTime = 0;
        r.hurt(r.damageSources().magic(), 10f);
        check(Math.abs((h0 - r.getHealth()) - 15f) < 0.6f, "stunned raiju should take 1.5x (took " + (h0 - r.getHealth()) + ")", failures);
        tick(r, Raiju.STUN_TICKS + 2);
        check(r.mode() == Raiju.MODE_GROUND && !r.isNoGravity(), "stun did not end", failures);
        // 接地していない柱へは乗る
        r.railTo(4);
        tick(r, Raiju.RAIL_WARN + Raiju.RAIL_TRAVEL + 2);
        check(r.mode() == Raiju.MODE_PERCH && r.rod() == 4, "raiju did not perch on rod 5 (mode " + r.mode() + ", rod " + r.rod() + ")", failures);
        check(!r.isInvisible(), "raiju stayed invisible after the rail", failures);
        // すべての技が最後まで回る (人がいないので当たらない)
        int ran = 0;
        Map<String, String> failed = new HashMap<>();
        for (Raiju.Action a : Raiju.Action.values()) {
            if (a == Raiju.Action.NONE) {
                continue;
            }
            r.forceAction(a);
            tick(r, 120);
            if (r.currentAction() == Raiju.Action.NONE) {
                ran++;
            } else {
                failed.put(a.name(), r.currentAction().name());
            }
        }
        check(failed.isEmpty(), "moves did not finish: " + failed, failures);
        check(r.roared() && r.isAngry(), "roar did not make the raiju angry", failures);
        notes.add(ran + "/" + (Raiju.Action.values().length - 1) + " moves ran to the end");
        // 巣の外へ出た雷獣は戻る。誰もいなければ 3 秒で放棄
        RaijuBattle.Host host = RaijuBattle.host(server);
        r.forceAction(Raiju.Action.CLAW);
        tick(r, Raiju.CLAW_TICKS + 2);
        r.moveTo(site.home().x + 30, site.floor(), site.home().z);
        RaijuBattle.room().tick(site.level(), host);
        check(r.distanceToSqr(site.home()) < 1, "raiju outside the nest was not brought back", failures);
        for (int i = 0; i < 6; i++) {
            RaijuBattle.room().tick(site.level(), host);
        }
        check(!RaijuBattle.room().active() && !r.isAlive() && !RaijuNest.sealed(site), "empty nest was not abandoned", failures);
        // 体力 50% を切ると咆哮 (自動)
        Raiju r2 = startRaiju(server, failures);
        if (r2 != null) {
            r2.setHealth(r2.getMaxHealth() * 0.45f);
            tick(r2, 2);
            check(r2.currentAction() == Raiju.Action.ROAR, "no roar below 50% (" + r2.currentAction() + ")", failures);
            RaijuBattle.abandon(server);
        }
    }

    private static void checkLoot(MinecraftServer server, List<String> failures, List<String> notes) {
        RandomSource random = RandomSource.create(42);
        int n = 4000;
        Map<String, Integer> hits = new HashMap<>();
        for (int i = 0; i < n; i++) {
            for (RaijuLoot.Draw d : RaijuLoot.roll(random, id -> true)) {
                hits.merge(d.id(), 1, Integer::sum);
                RaijuLoot.Entry e = RaijuLoot.TABLE.stream().filter(t -> t.id().equals(d.id())).findFirst().orElseThrow();
                check(d.count() >= e.min() && d.count() <= e.max(), "count out of range for " + d.id() + ": " + d.count(), failures);
            }
        }
        for (RaijuLoot.Entry e : RaijuLoot.TABLE) {
            double rate = hits.getOrDefault(e.id(), 0) / (double) n;
            check(Math.abs(rate - e.chance()) < 0.03, "chance of " + e.id() + " is " + rate + " (want " + e.chance() + ")", failures);
        }
        int none = 0;
        for (int i = 0; i < 200; i++) {
            none += RaijuLoot.roll(random, id -> false).size();
        }
        check(none == 0, "unmade items were drawn", failures);
        List<String> missing = new ArrayList<>();
        for (RaijuLoot.Entry e : RaijuLoot.TABLE) {
            if (!RaijuLoot.registered(e.id())) {
                missing.add(e.id().substring(e.id().indexOf(':') + 1));
            }
        }
        notes.add("not yet registered (left out of the draw): " + (missing.isEmpty() ? "none" : String.join(", ", missing)));
        // いない人の分は預かる (経験値 50 は必ず)
        UUID fake = UUID.randomUUID();
        RaijuLoot.onDefeat(server.overworld(), List.of(fake));
        TitanMemoryData memory = TitanMemoryData.get(server);
        check(memory.hasPending(fake), "rewards for an absent player were not held", failures);
        TitanMemoryData.Pending held = memory.takePending(fake);
        check(held.xp == RaijuLoot.VANILLA_XP, "held xp should be " + RaijuLoot.VANILLA_XP + " (" + held.xp + ")", failures);
    }

    private static void checkDefeat(MinecraftServer server, RaijuNest.Site site, List<String> failures) {
        Raiju r = startRaiju(server, failures);
        if (r == null) {
            return;
        }
        r.setHealth(1f);
        r.invulnerableTime = 0;
        r.hurt(r.damageSources().genericKill(), 1000f);
        check(!r.isAlive(), "raiju did not die", failures);
        check(RaijuBattle.defeated(server), "defeat was not recorded", failures);
        check(!RaijuBattle.room().active() && !RaijuNest.sealed(site), "entrance did not reopen after the defeat", failures);
        RaijuBattle.reawaken(server);
        check(!RaijuBattle.defeated(server), "reawaken did not reset the raiju", failures);
    }
}
