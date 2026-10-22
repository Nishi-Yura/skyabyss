package jp.revati.skyabyss.abyss.mobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBuild;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

// 深淵の敵の自然湧きを、建てた深淵の中で見る (docs/design/36 の 4.2)。SKYABYSS_SELFTEST_ONLY="abyss enemies" (段 "abyss enemies (load)" が先に建てる)。
//   見本: 休み場・着く所・心臓の間・印の溜まり・巣・野営 (層ごと) を中心にして、湧きの決めかたを何度も回す。
//   見るもの: 層に合う種だけ / 形が高さに合う (元の層で 1) / 形は深さと一緒に増える / 床の上で、体が空いている所だけ /
//             休み場と着く所の 24 ブロック内に湧かない / 心臓の間の中に湧かない / 人数の上限 (8) で湧かない /
//             数字は A 組と B 組で同じ決まり (湧いた高さの体力が AbyssScaling と合う) / 種ごとの暗さの決まり
public final class AbyssEnemiesSpawnSelfTest {
    private static final List<String> F = new ArrayList<>();
    private static final int TRIES = 60;
    private static final String[] MARKS = {"arrival", "rest_1", "rest_2", "rest_3", "heart", "den_bone", "pool_bone", "camp_bone",
            "den_salt", "pool_salt", "camp_salt", "den_storm", "pool_storm", "camp_storm", "den_ash", "pool_ash", "camp_ash"};
    private static String summary = "";

    private AbyssEnemiesSpawnSelfTest() {
    }

    private static void check(boolean ok, String what) {
        if (!ok) {
            F.add(what);
        }
    }

    // 深淵の中心の塊 (x と z が -48〜63。縦穴・心臓の間・休み場・印の部屋) を先に読み込み、建てておく
    public static void load(MinecraftServer server) {
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        if (level == null) {
            return;
        }
        hold(level, true);
        if (!AbyssBuild.built(server)) {
            AbyssBuild.finishNow(server);
        }
    }

    public static void run(MinecraftServer server) {
        F.clear();
        ServerLevel level = server.getLevel(AbyssWorld.LEVEL);
        try {
            if (level == null || !AbyssBuild.built(server)) {
                F.add("the abyss is not built");
            } else {
                body(level);
            }
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies spawn crashed", e);
            F.add("crashed: " + e);
        }
        if (level != null) {
            hold(level, false);
        }
        if (F.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST abyss enemies spawn PASSED ({})", summary);
        } else {
            SkyAbyss.LOGGER.error("SELFTEST abyss enemies spawn FAILED");
            F.forEach(x -> SkyAbyss.LOGGER.error("SELFTEST abyss enemies spawn FAILED: {}", x));
        }
    }

    private static void body(ServerLevel level) {
        for (int layer = 0; layer <= AbyssScaling.LAYER_MAX; layer++) {
            check(!AbyssMobSpawner.allowedAt(layer).isEmpty(), "layer " + layer + " has no foes in the table");
        }
        Random r = new Random(7);
        int[] found = new int[AbyssScaling.LAYER_MAX + 1];
        double[] formSum = new double[AbyssScaling.LAYER_MAX + 1];
        int planned = 0;
        int tried = 0;
        for (String name : MARKS) {
            BlockPos c = AbyssBuild.mark(name);
            if (c == null) {
                continue;
            }
            for (int i = 0; i < TRIES; i++) {
                tried++;
                AbyssMobSpawner.Plan p = AbyssMobSpawner.plan(level, c, r, 0);
                if (p == null) {
                    continue;
                }
                planned++;
                checkPlan(level, name, p, found, formSum);
            }
        }
        check(planned >= 40, "the picker found too few spots: " + planned + " of " + tried);
        // 形は深さと一緒に増える (見本が十分ある層どうし)
        double prev = -1;
        StringBuilder mean = new StringBuilder();
        for (int layer = 0; layer <= AbyssScaling.LAYER_MAX; layer++) {
            if (found[layer] == 0) {
                mean.append(" L").append(layer).append(" -");
                continue;
            }
            double m = formSum[layer] / found[layer];
            mean.append(String.format(" L%d %.2f (%d)", layer, m, found[layer]));
            if (found[layer] >= 10) {
                check(m + 1e-9 >= prev, "form does not rise with depth at layer " + layer);
                prev = m;
            }
        }
        // 人数の上限: 休み場の近くに上限いっぱいの敵がいるとき、もう湧かない
        BlockPos arrival = AbyssBuild.mark("arrival");
        if (arrival != null) {
            List<Entity> made = new ArrayList<>();
            for (int i = 0; i < AbyssMobSpawner.CAP_NEAR; i++) {
                Entity e = AbyssMobSpawner.spawnAt(level, AshTrail.ID, arrival.getX() + 0.5, arrival.getY(), arrival.getZ() + 0.5, 1);
                if (e != null) {
                    made.add(e);
                }
            }
            check(made.size() == AbyssMobSpawner.CAP_NEAR, "could not make the cap's worth of foes: " + made.size());
            int pop = AbyssMobSpawner.population(level, arrival);
            check(pop >= AbyssMobSpawner.CAP_NEAR, "the population near the arrival is " + pop);
            check(AbyssMobSpawner.plan(level, arrival, r, pop) == null, "a foe spawned past the cap");
            made.forEach(Entity::discard);
        }
        // 数字: 湧いた高さで決まる。A 組 (骨拾い) と B 組 (灰の這い跡) を心臓の間の中で作って、体力を見る
        Entity a = AbyssMobSpawner.spawnAt(level, AbyssMobKind.BONE_PICKER.id, 0.5, -270, 0.5, 3);
        Entity b = AbyssMobSpawner.spawnAt(level, AshTrail.ID, 0.5, -270, 0.5, 2);
        if (a instanceof LivingEntity la) {
            check(Math.abs(la.getMaxHealth() - AbyssScaling.hp(AbyssMobKind.BONE_PICKER.baseHp, -270)) < 1e-3,
                    "A group hp does not follow the height");
        } else {
            F.add("the bone picker did not spawn for the stat check");
        }
        if (b instanceof LivingEntity lb) {
            check(Math.abs(lb.getMaxHealth() - AbyssScaling.hp(AshTrail.HP, -270)) < 1e-3, "B group hp does not follow the height");
        } else {
            F.add("the ash trail did not spawn for the stat check");
        }
        if (a != null) {
            a.discard();
        }
        if (b != null) {
            b.discard();
        }
        // 暗さの決まり (種ごとの上限): 灰の這い跡は自分の見える明るさ、熱の種と灯喰いと鼓動もどきと縄の人は上限なし
        check(AbyssMobSpawner.maxLight(AshTrail.ID) == AshTrail.SEE_LIGHT, "the ash trail's dark limit is not its sight level");
        check(AbyssMobSpawner.maxLight(AbyssMobKind.BONE_PICKER.id) == AbyssMobSpawner.DARK_LIGHT, "a shaft foe's dark limit is not 10");
        check(AbyssMobSpawner.maxLight(EmberKeeper.ID) == Integer.MAX_VALUE, "the ember keeper has a dark limit");
        summary = "plans " + planned + " of " + tried + " tries; mean form by layer:" + mean;
    }

    private static void checkPlan(ServerLevel level, String where, AbyssMobSpawner.Plan p, int[] found, double[] formSum) {
        int layer = AbyssScaling.layerOf(p.y());
        check(AbyssMobSpawner.allowedAt(layer).contains(p.id()), "a " + p.id() + " spawned at " + where + " in layer " + layer
                + " where the table does not list it");
        check(p.form() == AbyssScaling.form(AbyssMobSpawner.nativeOf(p.id()), p.y()), "form does not match the height for " + p.id());
        check(p.count() >= 1 && p.count() <= 4, "a group of " + p.count() + " " + p.id());
        BlockPos feet = BlockPos.containing(p.x(), p.y(), p.z());
        boolean ground = !p.id().equals(RopeHanger.ID) && !p.id().equals(CeilingWalker.ID);
        if (ground) {
            BlockPos below = feet.below();
            check(level.getBlockState(below).isFaceSturdy(level, below, Direction.UP), "a " + p.id() + " spawned with no floor under it at " + where);
            check(level.getBlockState(feet).isAir() && level.getBlockState(feet.above()).isAir(),
                    "a " + p.id() + " spawned inside blocks at " + where);
        } else if (p.id().equals(CeilingWalker.ID)) {
            check(level.getBlockState(feet).isAir() && !level.getBlockState(feet.above(2)).isAir(),
                    "a ceiling walker has no ceiling over it at " + where);
        }
        check(!AbyssMobSpawner.nearRest(feet, AbyssMobSpawner.REST_KEEP), "a " + p.id() + " spawned near a rest (" + where + ")");
        check(!AbyssMobSpawner.inHeartRoom(feet), "a " + p.id() + " spawned in the heart room (" + where + ")");
        found[layer]++;
        formSum[layer] += p.form();
    }

    private static void hold(ServerLevel level, boolean on) {
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                level.setChunkForced(x, z, on);
            }
        }
    }
}
