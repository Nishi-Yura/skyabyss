package jp.revati.skyabyss.entity.foe;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 大陸の新しい敵 12 体 (docs/design/44) の自己テスト。SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="new foes" ./gradlew runServer。
//   登録   エンティティ・スポーンエッグ・経験値の表・動き (idle / walk / hurt / death と技の名前が animation.json にある)・島と草原の湧きの表
//   技     12 体それぞれ: 予告の形と当たりが同じ (形の外の人は傷つかない)・予告の長さのあとに当たる
//   盾     骸の歩兵は前からの傷が 3 割、後ろからは素通し
//   旗     骸の旗手が旗を突き立てると、そばの骸に守りが付き、倒れるはずの一撃で一度だけ起き上がる (二度目は倒れる)
//   叫び   野盗の頭目が吠えると、そばの野盗が速くなる
public final class FoesOneSelfTest {
    private static final int SX = 6200;
    private static final int SZ = 6600;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;
    static final String[] IDS = {"bandit", "bandit_archer", "bandit_chief", "bone_footman", "bone_bowman", "bone_standard", "grain_rat", "stray_wisp",
        "feral_dog", "moss_dead", "mist_angler", "thunder_wolf"};

    private FoesOneSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("new foes (load)", FoesOneSelfTest::load),
                SelfTestRunner.Step.once("new foes", 40, FoesOneSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = (SX >> 4) - 2; cx <= (SX >> 4) + 2; cx++) {
            for (int cz = (SZ >> 4) - 2; cz <= (SZ >> 4) + 2; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
            }
        }
    }

    private static void unload(ServerLevel level) {
        for (int cx = (SX >> 4) - 2; cx <= (SX >> 4) + 2; cx++) {
            for (int cz = (SZ >> 4) - 2; cz <= (SZ >> 4) + 2; cz++) {
                level.setChunkForced(cx, cz, false);
            }
        }
    }

    private static void check(boolean ok, String what, List<String> f) {
        if (!ok) {
            f.add(what);
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            registration(f);
            attacks(level, f);
            shield(level, f);
            banner(level, f);
            shout(level, f);
            howls(level, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST new foes crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.record(null);
            server.setDifficulty(before, true);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            level.getEntitiesOfClass(FakePlayer.class, area()).forEach(FakePlayer::discard);
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST new foes PASSED (12 foes registered with eggs/xp/animations/spawn rows; every telegraph matches its hit; "
                    + "footman shield 30% from the front; banner ward revives once; chief shout hastes the crew; dog howl sends the pack, wolf howl marks the target)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST new foes FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 登録
    private static void registration(List<String> f) {
        for (String id : IDS) {
            var key = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id);
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(key), id + " is not registered", f);
            check(BuiltInRegistries.ITEM.get(key.withSuffix("_spawn_egg")) instanceof SpawnEggItem, id + " has no spawn egg", f);
            check(GrowthRewards.MOD_ENEMIES.containsKey(key.toString()), id + " gives no xp", f);
            JsonObject anims = json("/assets/skyabyss/animations/entity/" + id + ".animation.json");
            if (anims == null) {
                f.add(id + " has no animation file");
                continue;
            }
            JsonObject a = anims.getAsJsonObject("animations");
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
            List<String> need = new ArrayList<>(List.of("idle", "walk"));
            if (type.create(level) instanceof GuardMob g) {
                for (String act : new String[] {"swing", "shoot", "sweep", "shout", "thrust", "block", "bash", "plant", "rise", "bite", "pulse", "howl", "slam", "spores", "net", "sway", "hurt", "death"}) {
                    if (g.hasAction(act)) {
                        need.add(act);
                    }
                }
                g.discard();
            }
            for (String n : need) {
                check(a.has("animation." + id + "." + n), id + " misses animation " + n, f);
            }
        }
        for (String region : new String[] {"island", "meadow"}) {
            JsonObject t = json("/data/skyabyss/spawns/" + region + ".json");
            check(t != null && t.getAsJsonArray("entries").size() >= 2, region + " spawn table is empty", f);
            if (t != null) {
                t.getAsJsonArray("entries").forEach(e -> {
                    String ent = e.getAsJsonObject().get("entity").getAsString();
                    check(BuiltInRegistries.ENTITY_TYPE.containsKey(net.minecraft.resources.ResourceLocation.parse(ent)), region + " spawns unknown " + ent, f);
                });
            }
        }
    }

    // 登録の確かめで動きの名前を数えるために一体作る世界 (platform で入れる)
    private static ServerLevel level;

    private static JsonObject json(String path) {
        try (InputStream in = FoesOneSelfTest.class.getResourceAsStream(path)) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 技: 予告の形と当たりが同じ
    private record Case(String name, Supplier<EntityType<? extends ContinentFoe>> type, double dist, int warn) {
    }

    private static void attacks(ServerLevel lv, List<String> f) {
        List<Case> cases = List.of(
                new Case("bandit", ModEntities.BANDIT::get, 2.0, Bandit.WARN),
                new Case("bandit_archer", ModEntities.BANDIT_ARCHER::get, 8.0, BanditArcher.WARN),
                new Case("bandit_chief", ModEntities.BANDIT_CHIEF::get, 2.4, BanditChief.SWEEP_WARN),
                new Case("bone_footman", ModEntities.BONE_FOOTMAN::get, 2.2, BoneFootman.WARN),
                new Case("bone_bowman", ModEntities.BONE_BOWMAN::get, 9.0, BoneBowman.SHOOT_WARN),
                new Case("bone_standard", ModEntities.BONE_STANDARD::get, 2.6, BoneStandard.SWEEP_WARN),
                new Case("grain_rat", ModEntities.GRAIN_RAT::get, 1.8, GrainRat.WARN),
                new Case("stray_wisp", ModEntities.STRAY_WISP::get, 2.0, StrayWisp.WARN),
                new Case("feral_dog", ModEntities.FERAL_DOG::get, 2.0, PackBeast.BITE_WARN),
                new Case("moss_dead", ModEntities.MOSS_DEAD::get, 2.4, MossDead.SLAM_WARN),
                new Case("mist_angler", ModEntities.MIST_ANGLER::get, 9.0, MistAngler.NET_WARN),
                new Case("thunder_wolf", ModEntities.THUNDER_WOLF::get, 2.0, PackBeast.BITE_WARN));
        for (Case c : cases) {
            ContinentFoe m = spawn(lv, c.type().get(), SX, SZ, 0f);
            FakePlayer in = fake(lv, c.name() + "_in", SX, SZ + c.dist());
            // 横へ大きく外れた人 (どの形にも入らない)
            FakePlayer out = fake(lv, c.name() + "_out", SX + 6.5, SZ - 6.5);
            m.setTarget(in);
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            int firstHurt = -1;
            for (int i = 1; i <= 80 && firstHurt < 0; i++) {
                tick(lv, m, 1);
                if (in.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            Telegraph.record(null);
            TelegraphShape shape = shapes.stream().filter(s -> s.contains(SX, SZ + c.dist())).findFirst().orElse(null);
            check(shape != null, c.name() + ": no telegraph covered the target", f);
            check(firstHurt >= c.warn(), c.name() + ": the target was hurt at tick " + firstHurt + " (warn " + c.warn() + ")", f);
            check(out.getHealth() == HEALTH, c.name() + ": a player outside every telegraph was hurt", f);
            m.discard();
            in.discard();
            out.discard();
        }
    }

    // ---------------------------------------------------------------- 盾
    private static void shield(ServerLevel lv, List<String> f) {
        BoneFootman m = spawn(lv, ModEntities.BONE_FOOTMAN.get(), SX, SZ, 0f);
        m.setYRot(0f);
        m.setYBodyRot(0f);
        m.setYHeadRot(0f);
        // yaw 0 = +Z を向く。前の人と後ろの人から同じ傷
        FakePlayer front = fake(lv, "front", SX, SZ + 2);
        FakePlayer back = fake(lv, "back", SX, SZ - 2);
        float hp = m.getHealth();
        m.invulnerableTime = 0;
        m.hurt(lv.damageSources().playerAttack(front), 10f);
        float fromFront = hp - m.getHealth();
        m.setHealth(hp);
        m.invulnerableTime = 0;
        m.hurt(lv.damageSources().playerAttack(back), 10f);
        float fromBack = hp - m.getHealth();
        check(fromBack > 0 && fromFront < fromBack * 0.5f, "footman shield: front " + fromFront + " vs back " + fromBack, f);
        m.discard();
        front.discard();
        back.discard();
    }

    // ---------------------------------------------------------------- 旗の守り
    private static void banner(ServerLevel lv, List<String> f) {
        BoneStandard s = spawn(lv, ModEntities.BONE_STANDARD.get(), SX, SZ, 0f);
        BoneFootman soldier = spawn(lv, ModEntities.BONE_FOOTMAN.get(), SX + 3, SZ - 2, 0f);
        FakePlayer p = fake(lv, "banner", SX, SZ + 7);
        s.setTarget(p);
        s.tickCount = 1000;
        for (int i = 0; i < BoneStandard.PLANT_WARN + 6 && !soldier.warded(); i++) {
            tick(lv, s, 1);
        }
        check(soldier.warded(), "the standard-bearer's planted banner did not ward the footman", f);
        soldier.invulnerableTime = 0;
        soldier.hurt(lv.damageSources().generic(), 500f);
        check(soldier.isAlive() && Math.abs(soldier.getHealth() - soldier.getMaxHealth() * 0.5f) < 0.01f && !soldier.warded(),
                "a warded footman did not rise once at half health (alive " + soldier.isAlive() + ", hp " + soldier.getHealth() + ")", f);
        soldier.invulnerableTime = 0;
        soldier.hurt(lv.damageSources().generic(), 500f);
        check(!soldier.isAlive(), "the footman rose twice", f);
        s.discard();
        soldier.discard();
        p.discard();
    }

    // ---------------------------------------------------------------- 叫び
    private static void shout(ServerLevel lv, List<String> f) {
        BanditChief c = spawn(lv, ModEntities.BANDIT_CHIEF.get(), SX, SZ, 0f);
        Bandit crew = spawn(lv, ModEntities.BANDIT.get(), SX - 3, SZ - 3, 0f);
        FakePlayer p = fake(lv, "shout", SX, SZ + 7);
        c.setTarget(p);
        c.tickCount = 1000;
        for (int i = 0; i < BanditChief.SHOUT_AT + 4; i++) {
            tick(lv, c, 1);
        }
        check(crew.hasEffect(MobEffects.MOVEMENT_SPEED), "the chief's shout did not haste the nearby bandit", f);
        c.discard();
        crew.discard();
        p.discard();
    }

    // ---------------------------------------------------------------- 群れの吠え: 野犬は仲間が一斉に跳びかかる、雷狼は狙った人の足もとに円
    private static void howls(ServerLevel lv, List<String> f) {
        FeralDog dog = spawn(lv, ModEntities.FERAL_DOG.get(), SX, SZ, 0f);
        FeralDog mate = spawn(lv, ModEntities.FERAL_DOG.get(), SX + 2, SZ, 0f);
        FakePlayer p = fake(lv, "howl", SX, SZ + 6);
        dog.setTarget(p);
        mate.setTarget(p);
        dog.tickCount = 1000;
        for (int i = 0; i < 24 && !mate.busy(); i++) {
            tick(lv, dog, 1);
        }
        check(mate.busy(), "the feral dog's howl did not send its packmate leaping", f);
        dog.discard();
        mate.discard();
        ThunderWolf wolf = spawn(lv, ModEntities.THUNDER_WOLF.get(), SX, SZ, 0f);
        ThunderWolf wmate = spawn(lv, ModEntities.THUNDER_WOLF.get(), SX + 2, SZ, 0f);
        wolf.setTarget(p);
        wolf.tickCount = 1000;
        List<TelegraphShape> shapes = new ArrayList<>();
        Telegraph.record(shapes);
        for (int i = 0; i < 4; i++) {
            tick(lv, wolf, 1);
        }
        Telegraph.record(null);
        check(shapes.stream().anyMatch(s -> s.contains(SX, SZ + 6) && !s.contains(SX, SZ)), "the thunder wolf's howl did not mark the target's feet", f);
        wolf.discard();
        wmate.discard();
        p.discard();
    }

    // ---------------------------------------------------------------- 部品
    private static AABB area() {
        return new AABB(SX - 30, FLOOR - 4, SZ - 30, SX + 30, FLOOR + 20, SZ + 30);
    }

    private static void platform(ServerLevel lv) {
        level = lv;
        for (int x = SX - 16; x <= SX + 16; x++) {
            for (int z = SZ - 16; z <= SZ + 16; z++) {
                lv.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) {
                    lv.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel lv, String tag, double x, double z) {
        FakePlayer p = new FakePlayer(lv, new GameProfile(UUID.nameUUIDFromBytes(("new foes " + tag).getBytes(StandardCharsets.UTF_8)), "[nf_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        p.setHealth(HEALTH);
        p.moveTo(x, FLOOR, z, 0, 0);
        lv.addFreshEntity(p);
        return p;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Mob> T spawn(ServerLevel lv, EntityType<? extends T> type, double x, double z, float yaw) {
        T m = (T) type.create(lv);
        m.moveTo(x, FLOOR, z, yaw, 0);
        m.setPersistenceRequired();
        try {
            java.lang.reflect.Field selector = Mob.class.getDeclaredField("targetSelector");
            selector.setAccessible(true);
            ((net.minecraft.world.entity.ai.goal.GoalSelector) selector.get(m)).removeAllGoals(goal -> true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not lock the target of " + m, e);
        }
        lv.addFreshEntity(m);
        return m;
    }

    private static void tick(ServerLevel lv, Mob m, int n) {
        for (int i = 0; i < n; i++) {
            m.tickCount++;
            m.tick();
            Telegraph.tickNow(lv);
        }
    }
}
