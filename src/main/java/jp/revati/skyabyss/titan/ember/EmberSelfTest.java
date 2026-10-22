package jp.revati.skyabyss.titan.ember;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.ProgressData;
import jp.revati.skyabyss.progress.Titans;
import jp.revati.skyabyss.titan.TitanWorldData;
import jp.revati.skyabyss.titan.tide.TideKeeper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼の巨神 (地上、担当 A) の自己テスト。結果はログに SELFTEST ember surface PASSED / FAILED。
// 夜の刻と息の窓、一体だけ・二体目の取り消し・状態の引き継ぎ・チケット・記録 (岩鯨・潮殻の記録と分かれている)、
// 熱の口と息の扇と口の前の判定 (向きを変えても)、その場で眠る高さ、戻る先 (口の前 6 ブロック、背を向ける)、熱の口のエンティティ、
// 口から乗る (資格のある人は余燼の背へ、ない人は吐き出される)、灰の段、温石 (向き・距離・雷翼を止めた人に一度だけ渡す)、
// モデルのデータ (3 段の LOD の箱の数・重なり 0・骨・光る画素・アニメの名前)
public final class EmberSelfTest {
    // 岩鯨 (4000, 4000)・潮殻 (-4000, 4000) の自己テストと重ならない所
    private static final int TEST_X = 4000;
    private static final int TEST_Z = -4000;

    private EmberSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember surface (load)", EmberSelfTest::load),
                SelfTestRunner.Step.once("ember surface", 40, EmberSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int dx = -1; dx <= 8; dx++) {
            level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, true);
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        TitanWorldData data = TitanWorldData.get(level);
        CompoundTag saved = data.snapshot(level.registryAccess());
        try {
            checkTimes(f);
            checkModel(f);
            checkStone(f);
            checkTitan(server, level, data, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember surface crashed", e);
            f.add("crashed: " + e);
        } finally {
            EmberTitanEntity t = EmberKeeper.KEEPER.current(level);
            if (t != null) {
                t.discard();
            }
            EmberKeeper.KEEPER.releaseTicket(level);
            data.restoreSlot(EmberKeeper.SLOT, saved, level.registryAccess());
            for (int dx = -1; dx <= 8; dx++) {
                level.setChunkForced((TEST_X >> 4) + dx, TEST_Z >> 4, false);
            }
        }
        CompoundTag after = data.snapshot(level.registryAccess());
        for (String key : List.of("StoneWhaleSpawned", "WhaleId", "LastX", "State")) {
            if (!Objects.equals(saved.get(key), after.get(key))) {
                f.add("the stone whale's record changed: " + key);
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember surface PASSED (night times, breath windows, one titan, carried state, ticket, record, mouth and breath fan, "
                    + "sleep height, landing at the mouth, mouth entity, boarding from the mouth, ash tiers, warm stone, model data)");
        } else {
            f.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST ember surface FAILED: {}", x));
        }
    }

    private static void expect(boolean ok, String failure, List<String> f) {
        if (!ok) {
            f.add(failure);
        }
    }

    // ---- 夜の刻と息
    private static void checkTimes(List<String> f) {
        expect(!EmberGround.isSleepTime(12499) && EmberGround.isSleepTime(12500) && EmberGround.isSleepTime(22999)
                && !EmberGround.isSleepTime(23000) && EmberGround.isSleepTime(24000 * 3 + 18000), "sleep time (12500-23000) is wrong", f);
        expect(!EmberGround.isGathering(11299) && EmberGround.isGathering(11300) && !EmberGround.isGathering(23000),
                "gathering (11300-23000) is wrong", f);
        expect(EmberGround.isRequestTime(11300) && EmberGround.isRequestTime(12499) && !EmberGround.isRequestTime(12500)
                && !EmberGround.isRequestTime(6000), "warm stone request time (11300-12500) is wrong", f);
        expect(EmberGround.nightId(11300) == EmberGround.nightId(24000 + 5000) && EmberGround.nightId(11299) != EmberGround.nightId(11300),
                "a night that crosses midnight must be one night", f);
        expect(EmberGround.ticksUntilSleep(12000) == 500 && EmberGround.ticksUntilSleep(13000) == 0 && EmberGround.ticksUntilSleep(23000) == 13500,
                "ticksUntilSleep is wrong: " + EmberGround.ticksUntilSleep(23000), f);
        int open = 0;
        int out = 0;
        for (int t = 0; t < EmberTitanEntity.BREATH_CYCLE; t++) {
            EmberTitanEntity.Breath b = EmberTitanEntity.breathAt(t);
            open += b == EmberTitanEntity.Breath.IN || b == EmberTitanEntity.Breath.HOLD ? 1 : 0;
            out += b == EmberTitanEntity.Breath.OUT ? 1 : 0;
        }
        expect(EmberTitanEntity.BREATH_CYCLE == 360 && open == 200 && out == 120, "breath cycle is not 18 s with 10 s open and 6 s out", f);
        expect(EmberGround.ashTier(10) == 3 && EmberGround.ashTier(100) == 2 && EmberGround.ashTier(200) == 1 && EmberGround.ashTier(300) == 0,
                "ash tiers are wrong", f);
    }

    // ---- 温石の向きと距離
    private static void checkStone(List<String> f) {
        // 南 (yaw 0) を向いた人: 西 (-X) は右手、東は左手、南は前、北は後ろ
        expect(WarmStoneItem.side(0f, -100, 0) == 1 && WarmStoneItem.side(0f, 100, 0) == -1 && WarmStoneItem.side(0f, 0, 100) == 0
                && WarmStoneItem.side(0f, 0, -100) == 2, "warm stone sides are wrong", f);
        expect(WarmStoneItem.side(90f, 0, -100) == 1, "warm stone side for a player facing west is wrong", f);
        expect(WarmStoneItem.distanceTier(50) == 2 && WarmStoneItem.distanceTier(200) == 1 && WarmStoneItem.distanceTier(800) == 0,
                "warm stone distance tiers are wrong", f);
    }

    // ---- 巨神 (見守り・口・戻る先・乗り込み)
    private static void checkTitan(MinecraftServer server, ServerLevel level, TitanWorldData data, List<String> f) {
        int x = TEST_X + 40;
        int z = TEST_Z + 8;
        EmberTitanEntity first = EmberKeeper.KEEPER.spawn(level, x, z, null);
        expect(EmberKeeper.KEEPER.current(level) == first, "the spawned ember titan is not the current one", f);
        expect(EmberKeeper.KEEPER.ticketPos() != null && EmberKeeper.KEEPER.ticketPos().equals(first.chunkPosition()), "no ticket on the ember titan's chunk", f);
        expect(first.getUUID().equals(data.slot(EmberKeeper.SLOT).id) && !first.getUUID().equals(data.whaleId())
                && !first.getUUID().equals(data.slot(TideKeeper.SLOT).id), "the ember titan is not recorded in its own slot", f);
        expect(first.mode() == EmberTitanEntity.Mode.SWIM && !first.isSurfaced(), "a new ember titan does not start burrowing", f);
        double surface = EmberGround.surfaceY(level, x, z);
        expect(!Double.isNaN(surface) && Math.abs(first.getY() - (surface - EmberTitanEntity.BURROW_SINK)) < 1e-6,
                "the new titan is not sunk under the surface: " + first.getY() + " surface " + surface, f);
        // 潜っていても、光は火山の上 (地面より上) で測る
        Vec3 probe = first.getLightProbePosition(1f);
        expect(probe.y > surface, "the light probe is underground while burrowing", f);
        // 二体目は加わらない
        EmberTitanEntity stray = EmberTitanSetup.EMBER_TITAN.get().create(level);
        if (stray != null) {
            stray.moveTo(x + 20, 40, z, 0f, 0f);
            expect(!level.addFreshEntity(stray) && level.getEntity(stray.getUUID()) == null, "a second ember titan was allowed to join", f);
        }
        // 熱の口・息の扇・戻る所 (向きを変えても)
        for (float yaw : new float[] {0f, 37f, 90f, 200f, -135f}) {
            first.setYRot(yaw);
            Vec3 m = first.mouthCenter();
            expect(first.inMouth(m.x, m.y + 0.1, m.z), "the mouth center is not in the mouth at yaw " + yaw, f);
            expect(!first.inMouth(first.getX(), first.getY() + 3, first.getZ()), "the body center is in the mouth at yaw " + yaw, f);
            Vec3 exit = first.exitPoint();
            expect(!first.inMouth(exit.x, first.getY() + EmberTitanEntity.TONGUE_UP, exit.z), "the exit is in the mouth at yaw " + yaw, f);
            Vec3 l = first.local(exit.x, first.getY(), exit.z);
            expect(Math.abs(l.x) < 1e-3 && Math.abs(l.z - EmberTitanEntity.EXIT_FORWARD) < 1e-3, "local() does not invert offset() at yaw " + yaw, f);
            Vec3 front = first.position().add(EmberTitanEntity.offset(yaw, 0, EmberTitanEntity.TONGUE_UP, EmberTitanEntity.SNOUT_FORWARD + 8));
            Vec3 aside = first.position().add(EmberTitanEntity.offset(yaw, 40, EmberTitanEntity.TONGUE_UP, EmberTitanEntity.SNOUT_FORWARD + 8));
            Vec3 behind = first.position().add(EmberTitanEntity.offset(yaw, 0, EmberTitanEntity.TONGUE_UP, -60));
            expect(first.inBreathFan(front.x, front.y, front.z) && first.inBreathFan(m.x, m.y + 1, m.z), "the breath fan misses the mouth front at yaw " + yaw, f);
            expect(!first.inBreathFan(aside.x, aside.y, aside.z) && !first.inBreathFan(behind.x, behind.y, behind.z),
                    "the breath fan is too wide at yaw " + yaw, f);
        }
        // 鼻先と口の前が、読み込んだチャンクの列 (東へ 8 チャンク) に入る向き (東)
        first.setYRot(-90f);
        // その場で眠る: 舌の上面が鼻先の地面の高さ、口の前 6 ブロックへ戻る (巨神に背を向ける)
        first.sleepHere();
        expect(first.isSleeping() && first.forcedTicks() > 0, "sleepHere did not sleep", f);
        Vec3 snout = first.position().add(EmberTitanEntity.offset(first.getYRot(), 0, 0, EmberTitanEntity.SNOUT_FORWARD));
        double ground = EmberGround.surfaceY(level, snout.x, snout.z);
        expect(Math.abs(first.getY() + EmberTitanEntity.TONGUE_UP - ground) < 1e-6, "the tongue is not on the ground: " + first.getY() + " ground " + ground, f);
        EmberTravel.Landing landing = EmberTravel.landing(level);
        Vec3 exit = first.exitPoint();
        // 口の前に立てる地面があればそこへ (巨神に背を向ける)。なければ (試す所が水の上など) 共有スポーン地点
        Vec3 stand = EmberGround.landingAt(level, exit.x, exit.z, 16);
        if (stand != null) {
            expect(landing.pos().distanceTo(stand) < 1e-6 && Math.hypot(stand.x - exit.x, stand.z - exit.z) < 16.5
                    && Math.abs(landing.yaw() - first.getYRot()) < 1e-3, "leaving a sleeping titan does not land at the mouth front: " + landing.pos() + " exit " + exit, f);
        } else {
            SkyAbyss.LOGGER.warn("SELFTEST ember surface: no standing ground at the test mouth front {}; checked the spawn fallback", exit);
            expect(Float.isNaN(landing.yaw()), "with no ground at the mouth front, leaving must fall back to the spawn", f);
        }
        // 熱の口のエンティティ: 眠ると口の真ん中に出る
        first.setBreathTick(5);
        expect(first.mouthOpen(), "the mouth is not open while breathing in", f);
        first.setBreathTick(EmberTitanEntity.BREATH_IN + EmberTitanEntity.BREATH_HOLD + 5);
        expect(!first.mouthOpen(), "the mouth is open while breathing out", f);
        first.setBreathTick(5);
        for (int i = 0; i < 20; i++) {
            first.tick();
        }
        EmberMouthEntity mouth = null;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof EmberMouthEntity me && me.titan() == first) {
                mouth = me;
            }
        }
        expect(mouth != null && mouth.position().distanceTo(first.mouthCenter()) < 1e-3, "no heat maw entity at the mouth of the sleeping titan", f);
        checkBoarding(server, level, first, f);
        // 状態の引き継ぎ: 最後に眠った場所と寄った夜は残り、潜行から始める。コマンドの眠りは引き継がない
        CompoundTag state = first.saveState();
        EmberTitanEntity second = EmberKeeper.KEEPER.spawn(level, x + 64, z, state);
        expect(first.isRemoved(), "calling the ember titan again left the old one", f);
        expect(EmberKeeper.KEEPER.current(level) == second && second.getUUID().equals(data.slot(EmberKeeper.SLOT).id), "the new ember titan is not registered", f);
        expect(second.mode() == EmberTitanEntity.Mode.SWIM && second.lastSleepSpot() != null && second.forcedTicks() == 0,
                "the carried state is wrong: " + second.saveState(), f);
        // 呼び直したあと (潜っている) は、最後に眠った場所の口の前へ戻す
        EmberTravel.Landing later = EmberTravel.landing(level);
        expect(stand == null || later.pos().distanceTo(stand) < 1e-6, "leaving a burrowing titan does not land at the last sleeping place: " + later.pos(), f);
        if (mouth != null) {
            mouth.tick();
            expect(mouth.isRemoved(), "the heat maw stayed after its titan was called again", f);
        }
        // 記録の保存と読み込み
        TitanWorldData copy = new TitanWorldData();
        copy.restore(data.snapshot(level.registryAccess()), level.registryAccess());
        expect(second.getUUID().equals(copy.slot(EmberKeeper.SLOT).id) && Objects.equals(copy.whaleId(), data.whaleId())
                && copy.slot(EmberKeeper.SLOT).state.contains("LastSleepX"), "the ember record did not survive save and load", f);
        Vec3 rec = EmberTitanEntity.exitFromRecord(copy.slot(EmberKeeper.SLOT).state);
        expect(rec != null && Math.hypot(rec.x - exit.x, rec.z - exit.z) < 1e-6, "the exit from the record is wrong", f);
        long count = 0;
        for (Entity e : level.getAllEntities()) {
            if (e instanceof EmberTitanEntity) {
                count++;
            }
        }
        expect(count == 1, count + " ember titans are loaded", f);
    }

    // 口から乗る: 資格のある人は余燼の背の到着点へ、ない人は吐き出されて地上に残る。温石は雷翼を止めた人に一度だけ
    private static void checkBoarding(MinecraftServer server, ServerLevel level, EmberTitanEntity titan, List<String> f) {
        ProgressData progress = ProgressData.get(server);
        Vec3 m = titan.mouthCenter();
        FakePlayer ok = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_test_mouth"));
        FakePlayer no = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ember_test_early"));
        List<String> titans = List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL, Titans.THUNDER_WING);
        try {
            for (String t : titans) {
                progress.markPersonal(ok.getUUID(), t);
            }
            for (String t : List.of(Titans.STONE_WHALE, Titans.TIDE_SHELL)) {
                progress.markPersonal(no.getUUID(), t);
            }
            ok.moveTo(m.x, m.y, m.z);
            no.moveTo(m.x, m.y, m.z);
            expect(titan.inMouth(ok.getX(), ok.getY(), ok.getZ()), "a player standing on the tongue is not in the mouth", f);
            // 入りやすい口: 舌の横 4・舌の 3 下・唇の先 (前 89) も口。口の前の通り道 (横 8) は押し返さない
            Vec3 side = titan.position().add(EmberTitanEntity.offset(titan.getYRot(), 4.0, EmberTitanEntity.TONGUE_UP - 3.0, 89.0));
            expect(titan.inMouth(side.x, side.y, side.z), "the widened mouth does not reach 4 to the side, 3 below the tongue, at the lips", f);
            jp.revati.skyabyss.titan.solid.TitanVolume vol = jp.revati.skyabyss.titan.solid.TitanVolume.get(titan.volumeName());
            expect(vol != null && !jp.revati.skyabyss.titan.solid.TitanSolids.blocked(vol, titan.corridors(), new Vec3(8.0, EmberTitanEntity.TONGUE_UP + 0.9, 80.0), false)
                    && !jp.revati.skyabyss.titan.solid.TitanSolids.blocked(vol, titan.corridors(), new Vec3(0, -4.0, 95.0), false),
                    "the way into the mouth is closed (8 to the side, or under a raised tongue)", f);
            EmberTravel.boardFromMouth(no, titan);
            expect(no.level().dimension() == Level.OVERWORLD && EmberTravel.PASSAGE.coolingDown(no), "a player who did not stop the thunder wing was let in", f);
            EmberTravel.boardFromMouth(ok, titan);
            expect(ok.level().dimension() == EmberIds.BACK, "a qualified player walking into the open mouth was not taken to the ember back", f);
            // 温石: 雷翼を止めた人に一度だけ
            expect(WarmStoneItem.giveIfEarned(ok) && !WarmStoneItem.giveIfEarned(ok), "the warm stone is not given exactly once", f);
            expect(!WarmStoneItem.giveIfEarned(no), "the warm stone was given to a player who did not stop the thunder wing", f);
            boolean has = false;
            for (int i = 0; i < ok.getInventory().getContainerSize(); i++) {
                has |= ok.getInventory().getItem(i).is(EmberTitanSetup.WARM_STONE.get());
            }
            expect(has, "the warm stone is not in the inventory", f);
        } finally {
            for (FakePlayer p : List.of(ok, no)) {
                if (p.level() instanceof ServerLevel at) {
                    at.removePlayerImmediately(p, Entity.RemovalReason.DISCARDED);
                }
                EmberTravel.PASSAGE.forget(p.getUUID());
                for (String t : titans) {
                    progress.clearPersonal(p.getUUID(), t);
                }
            }
        }
    }

    // ---- モデルのデータ (tools/install/install_ember_titan.py が書いたもの)
    private static final List<String> BONES = List.of("body", "ridge", "head", "jaw", "leg_fl", "leg_fr", "leg_rl", "leg_rr", "tail",
            "smoke_0", "smoke_1", "smoke_2", "smoke_3");
    private static final List<String> ANIMS = List.of("swim", "sleep", "breath_in", "breath_out", "smoke");

    private static void checkModel(List<String> f) {
        int[] counts = new int[3];
        String[] files = {"ember_titan", "ember_titan_mid", "ember_titan_far"};
        for (int i = 0; i < 3; i++) {
            JsonObject geo = json("/assets/skyabyss/geo/entity/" + files[i] + ".geo.json", f);
            if (geo == null) {
                return;
            }
            JsonArray bones = geo.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            Set<String> names = new HashSet<>();
            List<double[]> cubes = new ArrayList<>();
            for (JsonElement b : bones) {
                JsonObject bo = b.getAsJsonObject();
                names.add(bo.get("name").getAsString());
                if (bo.has("cubes")) {
                    for (JsonElement c : bo.getAsJsonArray("cubes")) {
                        JsonArray o = c.getAsJsonObject().getAsJsonArray("origin");
                        JsonArray s = c.getAsJsonObject().getAsJsonArray("size");
                        cubes.add(new double[] {o.get(0).getAsDouble(), o.get(1).getAsDouble(), o.get(2).getAsDouble(),
                                s.get(0).getAsDouble(), s.get(1).getAsDouble(), s.get(2).getAsDouble()});
                    }
                }
            }
            counts[i] = cubes.size();
            expect(names.containsAll(BONES), files[i] + " lacks bones: " + BONES.stream().filter(n -> !names.contains(n)).toList(), f);
            int overlaps = overlaps(cubes);
            expect(overlaps == 0, files[i] + " has " + overlaps + " overlapping cube pairs", f);
            double minY = cubes.stream().mapToDouble(c -> c[1]).min().orElse(0);
            expect(minY >= -1e-6, files[i] + " has cubes under the ground plane (y " + minY + ")", f);
        }
        expect(counts[0] >= 2000 && counts[0] <= 3500, "the near LOD should be about 2500 to 3300 cubes (high-poly trims, design doc 15): " + counts[0], f);
        expect(counts[1] < counts[0] && counts[2] < counts[1] && counts[2] > 0, "the LODs do not get coarser: " + java.util.Arrays.toString(counts), f);
        expect(glowPixels("/assets/skyabyss/textures/entity/ember_titan_glowmask.png", f) > 0, "the ember titan glowmask has no glowing pixels", f);
        JsonObject anim = json("/assets/skyabyss/animations/entity/ember_titan.animation.json", f);
        if (anim != null) {
            for (String a : ANIMS) {
                expect(anim.getAsJsonObject("animations").has("animation.ember_titan." + a), "missing animation " + a, f);
            }
        }
    }

    // 箱どうしが中で重なる組の数 (面で接するのは重なりにしない)
    static int overlaps(List<double[]> cubes) {
        cubes.sort((a, b) -> Double.compare(a[0], b[0]));
        int n = 0;
        double e = 1e-4;
        for (int i = 0; i < cubes.size(); i++) {
            double[] a = cubes.get(i);
            for (int j = i + 1; j < cubes.size(); j++) {
                double[] b = cubes.get(j);
                if (b[0] >= a[0] + a[3] - e) {
                    break;
                }
                if (b[1] < a[1] + a[4] - e && a[1] < b[1] + b[4] - e && b[2] < a[2] + a[5] - e && a[2] < b[2] + b[5] - e) {
                    n++;
                }
            }
        }
        return n;
    }

    private static JsonObject json(String path, List<String> f) {
        try (InputStream in = EmberSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing resource " + path);
                return null;
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            f.add("cannot read " + path + ": " + e);
            return null;
        }
    }

    private static int glowPixels(String path, List<String> f) {
        try (InputStream in = EmberSelfTest.class.getResourceAsStream(path)) {
            if (in == null) {
                f.add("missing resource " + path);
                return 0;
            }
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(in);
            int n = 0;
            for (int y = 0; y < img.getHeight(); y++) {
                for (int x = 0; x < img.getWidth(); x++) {
                    if ((img.getRGB(x, y) >>> 24) > 0) {
                        n++;
                    }
                }
            }
            return n;
        } catch (java.io.IOException | RuntimeException e) {
            f.add("cannot read " + path + ": " + e);
            return 0;
        }
    }
}
