package jp.revati.skyabyss.dungeon.mob;

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
import jp.revati.skyabyss.dungeon.Dungeon;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.entity.foe.ContinentFoe;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.rpg.GrowthRewards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 地下のダンジョン専用の敵と中ボス 12 体 (docs/design/55 の 4 の 7) の自己テスト。段の名前は "dungeon foes"。
//  - 登録・卵・経験値・形 (低ポリのメッシュ)・動き (idle・walk・技・hurt・death) がそろう。四つのダンジョンの表が専用の敵を指す
//  - 技: 人を前に置くと予告が人を覆い、予告より前には当たらず、どの形にも入らない人には当たらない
//  - 牛頭の鍛冶番の盾 (前から 0.35 倍)、晶洞の傀儡の胸 (前から 1.5 倍、後ろから 0.6 倍)
public final class DungeonFoesSelfTest {
    private static final int SX = 6600;
    private static final int SZ = 7000;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private record Case(String id, Supplier<EntityType<? extends ContinentFoe>> type, double dist, int warn, String[] actions) {
    }

    private static final List<Case> CASES = List.of(
            new Case("pit_digger", ModEntities.PIT_DIGGER::get, 2.2, PitDigger.WARN, new String[] {"swing"}),
            new Case("bone_roller", ModEntities.BONE_ROLLER::get, 7.0, BoneRoller.WARN, new String[] {"roll"}),
            new Case("pit_foreman", ModEntities.PIT_FOREMAN::get, 8.0, PitForeman.SLAM_WARN, new String[] {"slam", "sweep", "stomp"}),
            new Case("drowned_sailor", ModEntities.DROWNED_SAILOR::get, 8.0, DrownedSailor.WARN, new String[] {"throw"}),
            new Case("cave_jelly", ModEntities.CAVE_JELLY::get, 1.6, CaveJelly.WARN, new String[] {"pulse"}),
            new Case("bell_hermit", ModEntities.BELL_HERMIT::get, 4.0, BellHermit.PINCH_WARN, new String[] {"pinch", "ring", "charge"}),
            new Case("horn_beetle", ModEntities.HORN_BEETLE::get, 6.0, HornBeetle.WARN, new String[] {"bolt"}),
            new Case("crystal_eye", ModEntities.CRYSTAL_EYE::get, 6.0, CrystalEye.WARN, new String[] {"gaze"}),
            new Case("geode_golem", ModEntities.GEODE_GOLEM::get, 4.0, GeodeGolem.POUND_WARN, new String[] {"punch", "pound", "shards"}),
            new Case("bellows_imp", ModEntities.BELLOWS_IMP::get, 4.0, BellowsImp.WARN, new String[] {"breath", "poke"}),
            new Case("lava_slug", ModEntities.LAVA_SLUG::get, 2.2, LavaSlug.WARN, new String[] {"bite"}),
            new Case("ox_smith", ModEntities.OX_SMITH::get, 3.0, OxSmith.BASH_WARN, new String[] {"grab", "bash", "slam"}),
            // 奥のダンジョンの中ボス (2026-11-09)
            new Case("bone_lancer", ModEntities.BONE_LANCER::get, 8.0, jp.revati.skyabyss.dungeon.mob.deep.BoneLancer.CHARGE_WARN, new String[] {"charge", "rear", "sweep"}),
            new Case("jelly_priestess", ModEntities.JELLY_PRIESTESS::get, 8.0, jp.revati.skyabyss.dungeon.mob.deep.JellyPriestess.RAIN_WARN, new String[] {"lash", "rain", "pulse"}),
            new Case("thunder_owl", ModEntities.THUNDER_OWL::get, 8.0, jp.revati.skyabyss.dungeon.mob.deep.ThunderOwl.DIVE_WARN, new String[] {"dive", "gust", "strike"}),
            new Case("lava_toad", ModEntities.LAVA_TOAD::get, 8.0, jp.revati.skyabyss.dungeon.mob.deep.LavaToad.HOP_WARN, new String[] {"hop", "erupt", "tongue"}));

    private DungeonFoesSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("dungeon foes (load)", DungeonFoesSelfTest::load),
                SelfTestRunner.Step.once("dungeon foes", 40, DungeonFoesSelfTest::run));
    }

    static void load(MinecraftServer server) {
        forced(server.overworld(), true);
    }

    private static void forced(ServerLevel level, boolean on) {
        for (int cx = (SX >> 4) - 2; cx <= (SX >> 4) + 2; cx++) {
            for (int cz = (SZ >> 4) - 2; cz <= (SZ >> 4) + 2; cz++) {
                level.setChunkForced(cx, cz, on);
                if (on) {
                    level.getChunk(cx, cz);
                }
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
            registration(level, f);
            tables(f);
            attacks(level, f);
            sides(level, f);
            unstick(level, f);
            phases(level, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST dungeon foes crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.record(null);
            server.setDifficulty(before, true);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            level.getEntitiesOfClass(FakePlayer.class, area()).forEach(FakePlayer::discard);
            forced(level, false);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST dungeon foes PASSED (12 dungeon foes and mid bosses registered with eggs/xp/meshes/animations; the four dungeon tables use them; "
                    + "every telegraph covers its target, hits after the warning and spares a player outside; ox smith shield 0.35x from the front, geode golem chest 1.5x / back 0.6x; every boss steps up one block and a boss behind a wall moves to a clear spot beside the player)");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST dungeon foes FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 登録
    private static void registration(ServerLevel level, List<String> f) {
        for (Case c : CASES) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, c.id());
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(key), c.id() + " is not registered", f);
            check(BuiltInRegistries.ITEM.get(key.withSuffix("_spawn_egg")) instanceof SpawnEggItem, c.id() + " has no spawn egg", f);
            check(GrowthRewards.MOD_ENEMIES.containsKey(key.toString()), c.id() + " gives no xp", f);
            check(DungeonFoesSelfTest.class.getResource("/assets/skyabyss/lowpoly/" + c.id() + ".json") != null, c.id() + " has no low-poly mesh", f);
            JsonObject anims = json("/assets/skyabyss/animations/entity/" + c.id() + ".animation.json");
            if (anims == null) {
                f.add(c.id() + " has no animation file");
                continue;
            }
            JsonObject a = anims.getAsJsonObject("animations");
            List<String> need = new ArrayList<>(List.of("idle", "walk", "hurt", "death"));
            need.addAll(List.of(c.actions()));
            for (String n : need) {
                check(a.has("animation." + c.id() + "." + n), c.id() + " misses animation " + n, f);
            }
            if (c.type().get().create(level) instanceof GuardMob g) {
                for (String n : c.actions()) {
                    check(g.hasAction(n), c.id() + " does not play " + n, f);
                }
                g.discard();
            }
        }
    }

    private static void tables(List<String> f) {
        String[][] want = {
            {"bone_pit", "skyabyss:pit_foreman", "skyabyss:pit_digger", "skyabyss:bone_roller"},
            {"tide_cave", "skyabyss:bell_hermit", "skyabyss:drowned_sailor", "skyabyss:cave_jelly"},
            {"storm_hollow", "skyabyss:geode_golem", "skyabyss:horn_beetle", "skyabyss:crystal_eye"},
            {"molten_mine", "skyabyss:ox_smith", "skyabyss:bellows_imp", "skyabyss:lava_slug"}};
        for (String[] w : want) {
            Dungeon d = Dungeon.byId(w[0]);
            if (d == null) {
                f.add("no dungeon " + w[0]);
                continue;
            }
            check(d.midBoss.equals(w[1]), w[0] + ": the mid boss is " + d.midBoss, f);
            check(d.foes.containsValue(w[2]) && d.foes.containsValue(w[3]), w[0] + ": the foe table misses " + w[2] + " / " + w[3] + ": " + d.foes.values(), f);
        }
    }

    // ---------------------------------------------------------------- 技: 予告の形と当たりが同じ
    private static void attacks(ServerLevel lv, List<String> f) {
        for (Case c : CASES) {
            ContinentFoe m = spawn(lv, c.type().get(), SX, SZ, 0f);
            FakePlayer in = fake(lv, c.id() + "_in", SX, SZ + c.dist());
            FakePlayer out = fake(lv, c.id() + "_out", SX + 9.5, SZ - 9.5);
            m.setTarget(in);
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            int firstHurt = -1;
            for (int i = 1; i <= 90 && firstHurt < 0; i++) {
                tick(lv, m, 1);
                if (in.getHealth() < HEALTH) {
                    firstHurt = i;
                }
            }
            Telegraph.record(null);
            boolean covered = shapes.stream().anyMatch(s -> s.contains(in.getX(), in.getZ()) || s.contains(SX, SZ + c.dist()));
            check(covered, c.id() + ": no telegraph covered the target (" + shapes.size() + " shapes)", f);
            check(firstHurt >= Math.max(c.warn(), Telegraph.MIN_WARN_TICKS), c.id() + ": the target was hurt at tick " + firstHurt + " (warn " + c.warn() + ")", f);
            check(out.getHealth() == HEALTH, c.id() + ": a player outside every telegraph was hurt", f);
            m.discard();
            in.discard();
            out.discard();
        }
    }

    // ---------------------------------------------------------------- 盾と胸
    private static void sides(ServerLevel lv, List<String> f) {
        float shield = ratio(lv, ModEntities.OX_SMITH.get());
        check(Math.abs(shield - OxSmith.FRONT_TAKEN) < 0.05f, "ox smith front/back damage ratio " + shield + " (want " + OxSmith.FRONT_TAKEN + ")", f);
        float chest = ratio(lv, ModEntities.GEODE_GOLEM.get());
        float want = GeodeGolem.FRONT_TAKEN / GeodeGolem.BACK_TAKEN;
        check(Math.abs(chest - want) < 0.1f, "geode golem front/back damage ratio " + chest + " (want " + want + ")", f);
    }

    // 前から打った傷 ÷ 後ろから打った傷
    private static float ratio(ServerLevel lv, EntityType<? extends ContinentFoe> type) {
        ContinentFoe m = spawn(lv, type, SX, SZ, 0f);
        m.setYRot(0f);
        m.setYBodyRot(0f);
        m.setYHeadRot(0f);
        m.getAttribute(Attributes.ARMOR).setBaseValue(0);
        // yaw 0 = +Z を向く
        FakePlayer front = fake(lv, "front", SX, SZ + 3);
        FakePlayer back = fake(lv, "back", SX, SZ - 3);
        float hp = m.getHealth();
        m.invulnerableTime = 0;
        m.hurt(lv.damageSources().playerAttack(front), 10f);
        float fromFront = hp - m.getHealth();
        m.setHealth(hp);
        m.invulnerableTime = 0;
        m.hurt(lv.damageSources().playerAttack(back), 10f);
        float fromBack = hp - m.getHealth();
        m.discard();
        front.discard();
        back.discard();
        return fromBack <= 0 ? -1 : fromFront / fromBack;
    }

    // ---------------------------------------------------------------- 部品
    private static JsonObject json(String path) {
        try (InputStream in = DungeonFoesSelfTest.class.getResourceAsStream(path)) {
            return in == null ? null : JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    private static AABB area() {
        return new AABB(SX - 30, FLOOR - 4, SZ - 30, SX + 30, FLOOR + 20, SZ + 30);
    }

    private static void platform(ServerLevel lv) {
        for (int x = SX - 16; x <= SX + 16; x++) {
            for (int z = SZ - 16; z <= SZ + 16; z++) {
                lv.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) {
                    lv.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    // 主のつっかえ直し (2026-11-06): どの主も 1 ブロックの段を上がれる。壁の向こうの人のそばへ、体の収まる・人の見える所を見つけて移る
    private static void unstick(ServerLevel lv, List<String> f) {
        List<EntityType<? extends DungeonBoss>> bosses = List.of(jp.revati.skyabyss.registry.ModEntities.BONE_CENTIPEDE.get(),
                jp.revati.skyabyss.registry.ModEntities.SHELL_OCTOPUS.get(), jp.revati.skyabyss.registry.ModEntities.CRYSTAL_LIZARD.get(),
                jp.revati.skyabyss.registry.ModEntities.FORGE_GIANT.get(), jp.revati.skyabyss.registry.ModEntities.PIT_FOREMAN.get(),
                jp.revati.skyabyss.registry.ModEntities.BELL_HERMIT.get(), jp.revati.skyabyss.registry.ModEntities.GEODE_GOLEM.get(),
                jp.revati.skyabyss.registry.ModEntities.OX_SMITH.get());
        for (EntityType<? extends DungeonBoss> t : bosses) {
            DungeonBoss b = t.create(lv);
            check(b != null && b.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT) >= DungeonBoss.STEP - 1e-6,
                    t.getDescriptionId() + " cannot step up one block", f);
            if (b != null) {
                b.discard();
            }
        }
        // 壁 (高さ 4・幅 13) の向こうの人: 大きな主 (炉の巨人) が、人のそばの見える所へ移る
        FakePlayer p = fake(lv, "unstick", SX + 8.5, SZ + 0.5);
        for (int z = SZ - 6; z <= SZ + 6; z++) {
            for (int y = 0; y < 4; y++) {
                lv.setBlock(new BlockPos(SX, FLOOR + y, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
        DungeonBoss giant = spawn(lv, jp.revati.skyabyss.registry.ModEntities.FORGE_GIANT.get(), SX - 8.5, SZ + 0.5, -90f);
        check(!giant.hasLineOfSight(p), "the wall did not hide the player from the boss", f);
        giant.unstick(p);
        check(giant.distanceTo(p) <= 9.5 && giant.hasLineOfSight(p), "unstick did not move the boss beside the player (distance "
                + (int) giant.distanceTo(p) + ", sees " + giant.hasLineOfSight(p) + ")", f);
        check(lv.noCollision(giant, giant.getBoundingBox()), "unstick put the boss inside blocks", f);
        giant.discard();
        for (int z = SZ - 6; z <= SZ + 6; z++) {
            for (int y = 0; y < 4; y++) {
                lv.setBlock(new BlockPos(SX, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }
        p.discard();
    }

    // 後半 (2026-11-07): 四体の主は体力半分で後半に入り、手下を 2 体呼ぶ。新しい技 (骨の礫・渦・雷の走り・三連打ち・熔けた溝) は予兆を出す
    private static void phases(ServerLevel lv, List<String> f) {
        FakePlayer p = fake(lv, "phases", SX + 6.5, SZ + 0.5);
        List<DungeonBoss> made = new java.util.ArrayList<>();
        try {
            BoneCentipede c = spawn(lv, jp.revati.skyabyss.registry.ModEntities.BONE_CENTIPEDE.get(), SX - 4.5, SZ + 0.5, -90f);
            ShellOctopus o = spawn(lv, jp.revati.skyabyss.registry.ModEntities.SHELL_OCTOPUS.get(), SX - 4.5, SZ + 0.5, -90f);
            CrystalLizard l = spawn(lv, jp.revati.skyabyss.registry.ModEntities.CRYSTAL_LIZARD.get(), SX - 4.5, SZ + 0.5, -90f);
            ForgeGiant g = spawn(lv, jp.revati.skyabyss.registry.ModEntities.FORGE_GIANT.get(), SX - 4.5, SZ + 0.5, -90f);
            made.addAll(List.of(c, o, l, g));
            for (DungeonBoss b : made) {
                int before = jp.revati.skyabyss.combat.Telegraph.activeCount(lv);
                b.setTarget(p);
                b.enterPhase2(p);
                long minions = lv.getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(24), m -> m.getTags().contains("skyabyss_boss_minion")).size();
                check(b.inPhase2() && minions == DungeonBoss.MINIONS, b.getType().getDescriptionId() + " did not enter its second half with "
                        + DungeonBoss.MINIONS + " minions (" + minions + ")", f);
                check(jp.revati.skyabyss.combat.Telegraph.activeCount(lv) > before, b.getType().getDescriptionId() + " roared without a warning ring", f);
                for (Mob m : lv.getEntitiesOfClass(Mob.class, b.getBoundingBox().inflate(24), m -> m.getTags().contains("skyabyss_boss_minion"))) {
                    m.discard();
                }
            }
            int before = jp.revati.skyabyss.combat.Telegraph.activeCount(lv);
            c.spit(p);
            o.whirl(p);
            l.bolts(p);
            g.combo(p);
            g.trench(p);
            check(jp.revati.skyabyss.combat.Telegraph.activeCount(lv) >= before + 3 + 1 + 8 + 3 + 1, "a new boss attack did not show its warning ("
                    + (jp.revati.skyabyss.combat.Telegraph.activeCount(lv) - before) + ")", f);
            // 大百足の三連咬み (2026-11-11): 一つ目の扇を予告し、二つ目と叩きつけは技の途中で人へ向き直って出す
            int combo = jp.revati.skyabyss.combat.Telegraph.activeCount(lv);
            c.combo(p);
            check(jp.revati.skyabyss.combat.Telegraph.activeCount(lv) >= combo + 1, "the centipede triple bite showed no warning", f);
            // 後半の場の技: 降りそそぎ (足もと 1 + まわり 2) と回る薙ぎ (2 本)
            int field = jp.revati.skyabyss.combat.Telegraph.activeCount(lv);
            c.hazardsNow();
            check(jp.revati.skyabyss.combat.Telegraph.activeCount(lv) >= field + 3 + 2 + 1, "the second-half field attacks did not show their warnings ("
                    + (jp.revati.skyabyss.combat.Telegraph.activeCount(lv) - field) + ")", f);
            // 解き放ち: 場の技を三つとも一度に予告し、大きな隙を見せる。封じ・荒ぶりは持たない (大事件の大いなるもの固有)
            int surge = jp.revati.skyabyss.combat.Telegraph.activeCount(lv);
            c.surge(p);
            check(jp.revati.skyabyss.combat.Telegraph.activeCount(lv) >= surge + 3 + 2 + 1, "the surge did not unleash the field attacks ("
                    + (jp.revati.skyabyss.combat.Telegraph.activeCount(lv) - surge) + ")", f);
            check(jp.revati.skyabyss.combat.BossOpenings.isOpen(c), "the surge left no opening", f);
        } finally {
            for (DungeonBoss b : made) {
                b.discard();
            }
            p.discard();
        }
    }

    private static FakePlayer fake(ServerLevel lv, String tag, double x, double z) {
        FakePlayer p = new FakePlayer(lv, new GameProfile(UUID.nameUUIDFromBytes(("dungeon foes " + tag).getBytes(StandardCharsets.UTF_8)), "[df_" + tag + "]")) {
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
