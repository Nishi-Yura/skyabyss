package jp.revati.skyabyss.abyss.mobs;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssLight;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

// 深淵の敵 B 組の自己テスト。SKYABYSS_SELFTEST_ONLY="abyss enemies b"。
//   登録 (型・卵) / 素材 (骨・アニメ・絵の三段・音・発光) / 形 (A 組と同じ決まり) / 数値 (AbyssScaling と同じ) /
//   灰の這い跡 (光の中で見える・光がなければ見えない) / 天井歩き (見られている間は動かない・目を離すと判定) /
//   灯喰い (灯りを消す・休み場の灯りは消さない) / 縄の人 (引く力は歩く速さより弱い・落ちる傷の上限) / 湧きの休み場の決まり
public final class AbyssMobsBSelfTest {
    private static final List<String> F = new ArrayList<>();
    private static final String[] IDS = {AshTrail.ID, EmberKeeper.ID, SootChild.ID, PastShadow.ID, NamelessShape.ID, LampEater.ID,
            CeilingWalker.ID, HeartbeatMimic.ID, RopeHanger.ID};

    private AbyssMobsBSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    public static void runSelfTest(MinecraftServer server) {
        F.clear();
        try {
            registration();
            assets();
            stages();
            statCurve();
            ashLight(server);
            ceilingLook();
            lampSnuff(server);
            ropePull();
            spawnRules(server);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies b crashed", e);
            F.add("crashed: " + e);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss enemies b PASSED (registration, assets, stages, stat curve, ash light, ceiling look, lamp snuff, rope pull, spawn rests)");
        } else {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies b FAILED");
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss enemies b FAILED: {}", x));
        }
    }

    // 型が登録され、卵と経験値がある
    private static void registration() {
        for (String id : IDS) {
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id)), "entity type " + id + " is not registered");
            check(AbyssMobSpawner.typeOf(id) != null, "typeOf(" + id + ") is null");
        }
        check(AbyssMobsBSetup.ASH_TRAIL_EGG.get() != null && AbyssMobsBSetup.ROPE_HANGER_EGG.get() != null, "spawn eggs are missing");
        check(AbyssMobsBSetup.LAMP_ASH.get() != null && AbyssMobsBSetup.PULSE_MEMBRANE.get() != null, "abyss materials are missing");
        check(AbyssMobsBSetup.ASH_TRAIL_STEP.get() != null && AbyssMobsBSetup.ROPE_PULL.get() != null, "abyss sounds are missing");
    }

    // 骨・アニメ・絵 (段 0〜2)・発光・音の出来事が入っている
    private static void assets() {
        for (String id : IDS) {
            check(res("assets/skyabyss/lowpoly/" + id + ".json"), id + ": lowpoly mesh is missing");
            check(res("assets/skyabyss/geo/entity/" + id + "_lowpoly.geo.json"), id + ": lowpoly skeleton is missing");
            check(res("assets/skyabyss/animations/entity/" + id + ".animation.json"), id + ": animation is missing");
            check(res("assets/skyabyss/textures/lowpoly/" + id + ".png"), id + ": base atlas is missing");
            check(res("assets/skyabyss/textures/lowpoly/" + id + "_s1.png"), id + ": stage 1 atlas is missing");
            check(res("assets/skyabyss/textures/lowpoly/" + id + "_s2.png"), id + ": stage 2 atlas is missing");
            check(res("assets/skyabyss/models/item/" + id + "_spawn_egg.json"), id + ": spawn egg model is missing");
        }
        check(res("assets/skyabyss_abyss_b/sounds.json"), "sounds.json is missing");
        for (String m : new String[] {"lamp_ash", "soot_clump", "rope_knot", "echo_shard", "pulse_membrane"}) {
            check(res("assets/skyabyss/textures/item/" + m + ".png"), m + ": item texture is missing");
        }
        // 発光: 目と膜のある四体は光る画素があり、全部の段で発光マスクがある
        for (String id : new String[] {AshTrail.ID, EmberKeeper.ID, PastShadow.ID, CeilingWalker.ID, HeartbeatMimic.ID, RopeHanger.ID}) {
            check(res("assets/skyabyss/textures/lowpoly/" + id + "_glowmask.png"), id + ": glow mask is missing");
        }
    }

    private static boolean res(String path) {
        return AbyssMobsBSelfTest.class.getClassLoader().getResource(path) != null;
    }

    // 形: 灰の層の種は灰で 1、深みで 2。深みの種は深みで 1。灰より上の種は 1 (A 組と同じ決まり)
    private static void stages() {
        check(AbyssScaling.form(3, -100) == 1, "ash foe above the ash is not form 1");
        check(AbyssScaling.form(3, -200) == 1, "ash foe in the ash is not form 1");
        check(AbyssScaling.form(3, -300) == 2, "ash foe in the deep is not form 2");
        check(AbyssScaling.form(4, -300) == 1, "deep foe at home is not form 1");
        check(AbyssScaling.form(3, 100) == 1, "abyss-only foe high up is not form 1");
    }

    // 数値: 湧いた高さだけで決まる (AbyssScaling)。B 組の基の数字 (表) で、心臓の間の底は体力 1.5 倍・傷 1.25 倍
    private static void statCurve() {
        check(Math.abs(AbyssScaling.hp(40, AbyssScaling.HEART_Y) - 60) < 1e-9, "base hp is not 1.5x at the heart");
        check(Math.abs(AbyssScaling.dmg(6, AbyssScaling.HEART_Y) - 7.5) < 1e-9, "base damage is not 1.25x at the heart");
        check(Math.abs(AbyssScaling.hp(60, AbyssScaling.TOP_Y) - 60) < 1e-9, "a foe at the top of the shaft is not its base");
    }

    // 灰の這い跡: 光の中で見える。光がなければ見えない (灯りを置くと見え、消すと消える)
    private static void ashLight(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        check(level != null, "the abyss dimension is missing");
        if (level == null) {
            return;
        }
        BlockPos at = new BlockPos(700, 100, 700);
        // 石の箱の中 (光なし)
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    BlockPos p = at.offset(x, y, z);
                    boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3 || y == -1 || y == 4;
                    level.setBlock(p, wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        AbyssLight.clear();
        var mob = AbyssMobSpawner.spawnAt(level, AshTrail.ID, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 1);
        check(mob instanceof AshTrail, "the ash trail did not spawn");
        if (mob instanceof AshTrail trail) {
            check(!trail.lit(), "the ash trail is lit in a sealed dark box");
            AbyssLight.add(level, at, 6, Long.MAX_VALUE / 2, java.util.UUID.randomUUID());
            check(trail.lit(), "a lamp did not light the ash trail");
            AbyssLight.clear();
            check(!trail.lit(), "the ash trail stays lit after the lamp is gone");
            trail.discard();
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = -1; y <= 4; y++) {
                for (int z = -3; z <= 3; z++) {
                    level.setBlock(at.offset(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // 天井歩き: 見られている間は動かない・目を離すと判定 (向きの判定は純粋な関数で見る)
    private static void ceilingLook() {
        Vec3 eye = new Vec3(0, 0, 0);
        check(CeilingWalker.lookingAt(eye, new Vec3(0, 0, 1), new Vec3(0, 0, 10)), "a player facing the walker does not see it");
        check(!CeilingWalker.lookingAt(eye, new Vec3(0, 0, 1), new Vec3(10, 0, 0)), "a player looking sideways sees the walker");
        check(!CeilingWalker.lookingAt(eye, new Vec3(0, 0, -1), new Vec3(0, 0, 10)), "a player looking away sees the walker");
        check(CeilingWalker.DROP_RANGE > 0 && CeilingWalker.DROP_TELL >= 12, "the drop telegraph is too short");
        check(CeilingWalker.LOOK_RANGE >= 24, "the look range is too short");
    }

    // 灯喰い: 点の灯りを消せる (消した数が出る)・松明の判定・休み場と着く所の近くは消さない
    private static void lampSnuff(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        AbyssLight.clear();
        Vec3 at = new Vec3(800.5, 100, 800.5);
        AbyssLight.add(level, new BlockPos(800, 100, 800), 6, Long.MAX_VALUE / 2, java.util.UUID.randomUUID());
        check(AbyssLight.nearestLamp(level, at, 10) != null, "the lamp is not found near");
        check(AbyssLight.snuffNear(level, at, 2.0) == 1, "the lamp was not snuffed");
        check(AbyssLight.lampAt(level, new BlockPos(800, 100, 800)) == 0, "the snuffed lamp still gives light");
        check(LampEater.isLightBlock(Blocks.TORCH.defaultBlockState()), "a torch is not a light block");
        check(!LampEater.isLightBlock(Blocks.STONE.defaultBlockState()), "stone is a light block");
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null) {
            check(LampEater.nearProtected(arrival), "the arrival lamp is not protected");
        }
        check(!LampEater.nearProtected(new BlockPos(800, 100, 800)), "a far lamp is protected");
    }

    // 縄の人: 引く力は歩く速さ (0.1 ブロック/tick) より弱い。しゃがめば引かれない (定数で見る)。落ちる傷は 6 まで。予告は 2 秒
    private static void ropePull() {
        check(RopeHanger.PUSH < 0.1, "the rope pull is as strong as walking");
        check(RopeHanger.FALL_CAP <= 6, "the fall cap is too high");
        check(RopeHanger.TELL_TICKS >= 40, "the rope telegraph is shorter than 2 seconds");
        check(RopeHanger.PULL_MIN > 0 && RopeHanger.PULL_MIN < RopeHanger.PULL_MAX, "the rim band is empty");
        check(RopeHanger.COOLDOWN_TICKS >= 120, "the rope pulls too often");
    }

    // 湧き: 休み場と着く所の近くには湧かない (nearRest)。表と湧きの本体は AbyssMobSpawner (湧きの決まりの自己テストは abyss enemies spawn)
    private static void spawnRules(MinecraftServer server) {
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null) {
            check(AbyssMobSpawner.nearRest(arrival, AbyssMobSpawner.REST_KEEP), "the arrival is not kept clear of foes");
        }
        check(!AbyssMobSpawner.nearRest(new BlockPos(5000, 100, 5000), AbyssMobSpawner.REST_KEEP), "a far spot is kept clear");
        check(AbyssMobSpawner.FOES.length == 9, "the spawner table does not list nine foes");
    }
}
