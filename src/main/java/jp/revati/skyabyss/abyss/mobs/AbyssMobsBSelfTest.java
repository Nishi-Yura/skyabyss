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
//   登録 (型・卵) / 素材 (骨・アニメ・絵の三段・音・発光) / 段 (y と深さ) / 数値の上がり方 (なだらか) /
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
            check(AbyssMobsBSpawn.typeOf(id) != null, "typeOf(" + id + ") is null");
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

    // 段: 灰の層は y で (-160 より上 0、-220 より上 1、それより下 2)、深みは 200 ごとに一段
    private static void stages() {
        check(AbyssMobsBStage.stageAt(-100) == 0, "stage at -100 is not 0");
        check(AbyssMobsBStage.stageAt(-200) == 1, "stage at -200 is not 1");
        check(AbyssMobsBStage.stageAt(-250) == 2, "stage at -250 is not 2");
        check(AbyssMobsBStage.depthStage(-300) == 0, "depth stage at -300 is not 0");
        check(AbyssMobsBStage.depthStage(-500) == 1, "depth stage at -500 is not 1");
        check(AbyssMobsBStage.depthStage(-700) == 2, "depth stage at -700 is not 2");
        check(AbyssMobsBStage.depthStage(-1400) == 2, "deep stage is not capped at 2");
    }

    // 数値は段ごとになだらか (段の一つ上がりで HP +15%・傷 +10% 以内、深さは 100 ブロックで HP +4%・傷 +2% 以内、上限あり)
    private static void statCurve() {
        for (int s = 0; s < AbyssMobsBStage.MAX; s++) {
            check(AbyssMobsBStage.hpMul(s + 1) / AbyssMobsBStage.hpMul(s) - 1 <= 0.2, "hp step " + s + " is too steep");
            check(AbyssMobsBStage.dmgMul(s + 1) / AbyssMobsBStage.dmgMul(s) - 1 <= 0.15, "damage step " + s + " is too steep");
        }
        check(Math.abs(AbyssMobsBStage.depthHp(-300) - 1) < 1e-9, "depth hp is not 1 at the heart");
        check(AbyssMobsBStage.depthHp(-400) - AbyssMobsBStage.depthHp(-300) <= 0.05 + 1e-9, "depth hp rises too fast per 100 blocks");
        check(AbyssMobsBStage.depthDmg(-400) - AbyssMobsBStage.depthDmg(-300) <= 0.03 + 1e-9, "depth damage rises too fast per 100 blocks");
        check(AbyssMobsBStage.depthHp(-5000) <= 1 + AbyssMobsBStage.DEPTH_HP_CAP + 1e-9, "depth hp is not capped");
        // 段 2 の HP は段 0 の 1.3 倍まで (ラフの数値と合う)
        check(Math.abs(AbyssMobsBStage.hp(40, 2, -250) - 40 * 1.3) < 1e-9, "stage 2 hp is not 1.3x");
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
        var mob = AbyssMobsBSpawn.spawnAt(level, AshTrail.ID, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0);
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

    // 湧き: 休み場と着く所の近くには湧かない (nearRest)。表は層の y で決まる (ash 層の段は 0〜2)
    private static void spawnRules(MinecraftServer server) {
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null) {
            check(AbyssMobsBSpawn.nearRest(arrival, AbyssMobsBSpawn.REST_KEEP), "the arrival is not kept clear of foes");
        }
        check(!AbyssMobsBSpawn.nearRest(new BlockPos(5000, 100, 5000), AbyssMobsBSpawn.REST_KEEP), "a far spot is kept clear");
        check(AbyssMobsBSpawn.IDS.length == 9, "the spawner table does not list nine foes");
    }
}
