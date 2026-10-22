package jp.revati.skyabyss.story.ch1;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.BoneHauler;
import jp.revati.skyabyss.entity.ElderSentry;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.entity.RibArcher;
import jp.revati.skyabyss.entity.RockShellBeetle;
import jp.revati.skyabyss.entity.SpineHare;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.guide.GuideSetup;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupOwned;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 一章の地上の敵 4 種 (P7。docs/design/21 の 3.1) の自己テスト。SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ch1 mobs" ./gradlew runServer。
// 結果は SELFTEST ch1 mobs PASSED / FAILED。
//   登録     エンティティ・強さ・スポーンエッグ・音・割符・経験値の表・洞の湧き台が新しい敵・ひとり歩き (夜・地形)・骨負いの時刻
//   岩殻虫   前からの傷は半分・立ち上がりの帯と当たりが同じ形で 0.8 秒以上・傷 4・落とし物 (岩殻片 60%・骨片 10%。倒した人がいるときだけ)
//   棘うさぎ 棘の帯と当たりが同じ形・鈍足・撃ったあとに離れる・近づかれると離れる・落とし物
//   骨負い   傷つけず逃げる・集め場へ走る・集め場のまわりに一体だけ (夕方〜夜、人が近いとき)・落とし物・保存しない
//   老いた歩哨 人数で体力・四つの技 (扇・帯・円は印と当たりが同じで 0.8 秒以上、骨笛は射手 2 を 2 体まで) ・正面の傷は半分・割符 (人ごとに一つ)・倒れると射手が消える
public final class Ch1MobsSelfTest {
    private static final int SX = 6000;
    private static final int SZ = 6000;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private Ch1MobsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("ch1 mobs (load)", Ch1MobsSelfTest::load),
                SelfTestRunner.Step.once("ch1 mobs", 40, Ch1MobsSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = (SX >> 4) - 3; cx <= (SX >> 4) + 3; cx++) {
            for (int cz = (SZ >> 4) - 3; cz <= (SZ >> 4) + 3; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz); // 読み込みを待つ (負荷が高いと経路が引けない)
            }
        }
    }

    private static void unload(ServerLevel level) {
        for (int cx = (SX >> 4) - 3; cx <= (SX >> 4) + 3; cx++) {
            for (int cz = (SZ >> 4) - 3; cz <= (SZ >> 4) + 3; cz++) {
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
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        long dayBefore = level.getDayTime();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            registration(level, f, notes);
            beetle(level, f, notes);
            hare(level, f, notes);
            hauler(level, f, notes);
            elder(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            Telegraph.record(null);
            level.setDayTime(dayBefore);
            server.setDifficulty(before, true);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 mobs PASSED (4 mobs registered with eggs/sounds/xp/cave pads/wanderers; beetle shield + telegraphed charge + drops; "
                    + "hare telegraphed spine + slow + retreat; hauler flees to the cache, one per cache at dusk, no damage; elder scaled by party, 4 telegraphed skills, "
                    + "tally once per player, archers capped and cleared) {}", notes);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 mobs FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 部品
    private static AABB area() {
        return new AABB(SX - 30, FLOOR - 4, SZ - 30, SX + 30, FLOOR + 20, SZ + 30);
    }

    private static void platform(ServerLevel level) {
        for (int x = SX - 24; x <= SX + 24; x++) {
            for (int z = SZ - 24; z <= SZ + 24; z++) {
                level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) {
                    level.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("ch1 mobs " + tag).getBytes(StandardCharsets.UTF_8)), "[c1_" + tag + "]")) {
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
        level.addFreshEntity(p);
        return p;
    }

    private static float lost(FakePlayer p) {
        return HEALTH - p.getHealth();
    }

    private static void drop(List<FakePlayer> ps) {
        ps.forEach(FakePlayer::discard);
        ps.clear();
    }

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double z, float yaw) {
        T m = type.create(level);
        m.moveTo(x, FLOOR, z, yaw, 0);
        m.setPersistenceRequired();
        // 狙いを変える仕組み (いちばん近い人を選び直す) を外す。試験が setTarget で決めた相手だけを狙わせる
        // (外さないと、近くの別の人へ狙いが移って線や当たりが変わる揺らぎが出る。全体を回す負荷のときに目立つ)
        try {
            java.lang.reflect.Field selector = Mob.class.getDeclaredField("targetSelector");
            selector.setAccessible(true);
            ((net.minecraft.world.entity.ai.goal.GoalSelector) selector.get(m)).removeAllGoals(goal -> true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not lock the target of " + m, e);
        }
        level.addFreshEntity(m);
        return m;
    }

    private static void tick(ServerLevel level, Mob m, int n) {
        for (int i = 0; i < n; i++) {
            m.tickCount++;
            m.tick();
            Telegraph.tickNow(level);
        }
    }

    private static int count(ServerLevel level, Item item, boolean discard) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area())) {
            if (e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
            if (discard) {
                e.discard();
            }
        }
        return n;
    }

    private static void clearItems(ServerLevel level) {
        level.getEntitiesOfClass(ItemEntity.class, area()).forEach(ItemEntity::discard);
    }

    // 技を出させ、tick を進めて、最初に傷を受けた tick と、人ごとの傷を返す (印と当たりが同じ形かを、記録した形と突き合わせる)
    private record Outcome(int firstHurt, TelegraphShape shape) {
    }

    private static Outcome watch(ServerLevel level, Mob m, int ticks, FakePlayer first) {
        return watch(level, m, ticks, first, () -> {
        });
    }

    // start: 記録を始めたあとに技を出させる (予告は作られた瞬間に記録される)
    private static Outcome watch(ServerLevel level, Mob m, int ticks, FakePlayer first, Runnable start) {
        List<TelegraphShape> shapes = new ArrayList<>();
        Telegraph.record(shapes);
        start.run();
        int firstHurt = -1;
        for (int i = 1; i <= ticks; i++) {
            tick(level, m, 1);
            if (firstHurt < 0 && first.getHealth() < HEALTH) {
                firstHurt = i;
            }
        }
        Telegraph.record(null);
        return new Outcome(firstHurt, shapes.isEmpty() ? null : shapes.get(0));
    }

    // 印の形 (contains) と実際の当たりが一致する: 始めの位置 start[i] の人が、形の中にいたら傷を受け、外なら受けない
    private static void sameShape(Outcome o, List<FakePlayer> ps, double[][] start, String name, List<String> f) {
        if (o.shape() == null) {
            f.add(name + ": no damaging telegraph was made");
            return;
        }
        for (int i = 0; i < ps.size(); i++) {
            boolean inside = o.shape().contains(start[i][0], start[i][1]);
            boolean hurt = lost(ps.get(i)) > 0;
            check(inside == hurt, name + ": player " + i + " at (" + start[i][0] + "," + start[i][1] + ") inside=" + inside + " but hurt=" + hurt, f);
        }
    }

    private static double[][] positions(List<FakePlayer> ps) {
        double[][] out = new double[ps.size()][];
        for (int i = 0; i < ps.size(); i++) {
            out[i] = new double[] {ps.get(i).getX(), ps.get(i).getZ()};
        }
        return out;
    }

    // ---------------------------------------------------------------- 登録
    private static void registration(ServerLevel level, List<String> f, List<String> notes) {
        record Def(String id, double hp, double speedMin, boolean hostile) {
        }
        for (Def d : List.of(new Def(Ch1Mobs.ROCK_SHELL_BEETLE, 22, 0, true), new Def(Ch1Mobs.SPINE_HARE, 12, 0, true), new Def(Ch1Mobs.BONE_HAULER, 16, 0, false),
                new Def(Ch1Mobs.ELDER_SENTRY, 140, 0, true))) {
            check(Ch1Prep.entityExists(d.id()), d.id() + " is not registered", f);
            ResourceLocation key = ResourceLocation.parse(d.id());
            check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg")), d.id() + " has no spawn egg", f);
            check(GrowthRewards.MOD_ENEMIES.containsKey(d.id()), d.id() + " has no xp entry", f);
            EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.get(key);
            Mob m = (Mob) type.create(level);
            check(m != null && m.getMaxHealth() == d.hp(), d.id() + " max health " + (m == null ? -1 : m.getMaxHealth()), f);
            check(m instanceof GroupOwned, d.id() + " is not group owned (SiteSpawner needs it)", f);
            if (m != null) {
                m.discard();
            }
        }
        // 小さな敵の経験値は、章の枠 (350 + 400) を食わない小さな値
        check(GrowthRewards.MOD_ENEMIES.get(Ch1Mobs.ROCK_SHELL_BEETLE).xp() <= 10 && GrowthRewards.MOD_ENEMIES.get(Ch1Mobs.SPINE_HARE).xp() <= 10
                && GrowthRewards.MOD_ENEMIES.get(Ch1Mobs.ELDER_SENTRY).xp() <= 60, "ground mob xp is not small", f);
        check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(Ch1Prep.SENTRY_TALLY)), "sentry tally is not registered", f);
        check(MobSounds.ELDER_SENTRY_HORN.get() != null && MobSounds.SPINE_HARE_SHOOT.get() != null && MobSounds.ROCK_SHELL_BEETLE_CHARGE.get() != null, "skill sounds", f);
        check(ModItems.ELDER_SENTRY_EGG.get() != null, "egg item", f);
        // 洞の湧き台: 新しい敵 (代用が残っていない)
        int beetles = 0;
        int hares = 0;
        int elders = 0;
        for (var pad : CleftCave.pads()) {
            for (var s : pad.spawns()) {
                switch (s.entity()) {
                    case Ch1Mobs.ROCK_SHELL_BEETLE -> beetles += s.count();
                    case Ch1Mobs.SPINE_HARE -> hares += s.count();
                    case Ch1Mobs.ELDER_SENTRY -> elders += s.count();
                    default -> f.add("cave pad still has a stand-in: " + s.entity());
                }
            }
        }
        check(beetles == 3 && hares == 3 && elders == 1, "cave pads: beetles " + beetles + " hares " + hares + " elders " + elders, f);
        // ひとり歩き: 岩殻虫 = 丘・山・荒れ地の昼夜、棘うさぎ = 草原の夜。薄く (一章の地面だけ)
        boolean b = false;
        boolean h = false;
        for (var w : ChapterMobSpawner.registered()) {
            if (w.id().equals("ch1_rock_shell_beetle")) {
                b = w.chapter() == Chapters.WHALE && w.time() == ChapterMobSpawner.Time.ANY && w.entity().equals(Ch1Mobs.ROCK_SHELL_BEETLE) && w.biome() != null;
            }
            if (w.id().equals("ch1_spine_hare")) {
                h = w.chapter() == Chapters.WHALE && w.time() == ChapterMobSpawner.Time.NIGHT && w.entity().equals(Ch1Mobs.SPINE_HARE) && w.biome() != null;
            }
        }
        check(b, "beetle wanderer is not registered as whale-chapter, any time, biome-gated", f);
        check(h, "hare wanderer is not registered as whale-chapter, night, biome-gated", f);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        check(!Ch1Mobs.beetleBiome(biome(biomes, Biomes.PLAINS)) && Ch1Mobs.beetleBiome(biome(biomes, Biomes.JAGGED_PEAKS))
                && Ch1Mobs.beetleBiome(biome(biomes, Biomes.WINDSWEPT_HILLS)) && Ch1Mobs.beetleBiome(biome(biomes, Biomes.BADLANDS))
                && !Ch1Mobs.beetleBiome(biome(biomes, Biomes.OCEAN)), "beetle biomes", f);
        check(Ch1Mobs.hareBiome(biome(biomes, Biomes.PLAINS)) && Ch1Mobs.hareBiome(biome(biomes, Biomes.MEADOW)) && !Ch1Mobs.hareBiome(biome(biomes, Biomes.DESERT))
                && !Ch1Mobs.hareBiome(biome(biomes, Biomes.OCEAN)) && !Ch1Mobs.hareBiome(biome(biomes, Biomes.JAGGED_PEAKS)), "hare biomes", f);
        check(!Ch1Mobs.haulerHours(11499) && Ch1Mobs.haulerHours(11500) && Ch1Mobs.haulerHours(22999) && !Ch1Mobs.haulerHours(23000) && !Ch1Mobs.haulerHours(0)
                && Ch1Mobs.haulerHours(24000 + 15000), "hauler hours (dusk to night)", f);
    }

    private static Holder<Biome> biome(net.minecraft.core.Registry<Biome> reg, net.minecraft.resources.ResourceKey<Biome> key) {
        return reg.getHolderOrThrow(key);
    }

    // ---------------------------------------------------------------- 岩殻虫
    private static void beetle(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        RockShellBeetle b = spawn(level, ModEntities.ROCK_SHELL_BEETLE.get(), SX, SZ, 0f);
        try {
            // 前 (+z) からの傷は半分、背 (-z) からは全部
            FakePlayer front = fake(level, "b_front", SX, SZ + 2);
            FakePlayer back = fake(level, "b_back", SX, SZ - 2);
            ps.addAll(List.of(front, back));
            tick(level, b, 2);
            float full = hurtLoss(b, level, back);
            float half = hurtLoss(b, level, front);
            check(half > 0 && full > 0 && half / full > 0.35 && half / full < 0.65, "beetle front hit " + half + " vs back hit " + full + " (should be about half)", f);
            notes.add("beetle front/back " + round(half) + "/" + round(full));
            drop(ps);
            b.discard();

            // 立ち上がりの帯: 前 2 と帯の端 (3.4) は中、横 1.6・前 4.6・後ろは外
            b = spawn(level, ModEntities.ROCK_SHELL_BEETLE.get(), SX, SZ, 0f);
            FakePlayer in = fake(level, "b_in", SX, SZ + 2.0);
            FakePlayer edge = fake(level, "b_edge", SX, SZ + 3.4);
            FakePlayer side = fake(level, "b_side", SX + 1.6, SZ + 1.5);
            FakePlayer far = fake(level, "b_far", SX, SZ + 4.6);
            FakePlayer behind = fake(level, "b_behind", SX, SZ - 2.0);
            ps.addAll(List.of(in, edge, side, far, behind));
            double[][] start = positions(ps);
            tick(level, b, 2);
            b.setTarget(in);
            Outcome o = watch(level, b, 50, in);
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "beetle hurt after only " + o.firstHurt() + " ticks", f);
            check(o.firstHurt() >= 0 && o.firstHurt() <= RockShellBeetle.WARN + 6, "beetle never hurt in time (" + o.firstHurt() + ")", f);
            check(lost(in) >= 3.5f && lost(in) <= 4.5f, "beetle charge damage " + lost(in), f);
            check(lost(side) == 0 && lost(far) == 0 && lost(behind) == 0, "beetle hurt a player outside the line", f);
            sameShape(o, ps, start, "beetle charge", f);
            notes.add("beetle warns " + o.firstHurt() + " ticks, hits " + round(lost(in)));
            drop(ps);
            b.discard();

            // 落とし物: 倒した人がいるとき 岩殻片 60% (1〜2)・骨片 10%。いないときは落とさない
            clearItems(level);
            FakePlayer killer = fake(level, "b_killer", SX + 10, SZ);
            ps.add(killer);
            for (int i = 0; i < 150; i++) {
                RockShellBeetle d = spawn(level, ModEntities.ROCK_SHELL_BEETLE.get(), SX, SZ, 0f);
                d.hurt(level.damageSources().playerAttack(killer), 1000f);
            }
            int scrap = count(level, GuideSetup.ROCK_SHELL_SCRAP.get(), false);
            int chips = count(level, GuideSetup.BONE_CHIP.get(), true);
            check(scrap >= 95 && scrap <= 175, "beetle scrap drops " + scrap + " from 150 (expected about 135)", f);
            check(chips >= 3 && chips <= 35, "beetle bone chip drops " + chips + " from 150 (expected about 15)", f);
            for (int i = 0; i < 40; i++) {
                spawn(level, ModEntities.ROCK_SHELL_BEETLE.get(), SX, SZ, 0f).kill();
            }
            check(count(level, GuideSetup.ROCK_SHELL_SCRAP.get(), true) == 0, "beetle dropped loot without a player kill", f);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            notes.add("beetle scrap " + scrap + "/150, chips " + chips);

            // 保存しない・遠くで消す (場所の湧き台とひとり歩きが消す。タグ付きは保存しない)
            RockShellBeetle t = spawn(level, ModEntities.ROCK_SHELL_BEETLE.get(), SX, SZ, 0f);
            check(t.shouldBeSaved(), "an egg-spawned beetle should be saved like any mob", f);
            t.addTag(jp.revati.skyabyss.guide.SiteSpawner.TAG);
            check(!t.shouldBeSaved(), "a site/wanderer beetle should not be saved", f);
            check(!t.removeWhenFarAway(1000), "beetles are removed by the spawner, not by distance", f);
            t.discard();
        } finally {
            drop(ps);
            b.discard();
        }
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    // attacker からの一撃で失った体力 (無敵時間と回復を戻す)
    private static float hurtLoss(Mob m, ServerLevel level, FakePlayer attacker) {
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        m.hurt(level.damageSources().playerAttack(attacker), 6f);
        float loss = m.getMaxHealth() - m.getHealth();
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        return loss;
    }

    // ---------------------------------------------------------------- 棘うさぎ
    private static void hare(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        SpineHare h = spawn(level, ModEntities.SPINE_HARE.get(), SX, SZ, 0f);
        try {
            FakePlayer in = fake(level, "h_in", SX, SZ + 8);
            FakePlayer off = fake(level, "h_off", SX + 1.0, SZ + 8);
            FakePlayer behind = fake(level, "h_behind", SX, SZ - 4);
            FakePlayer far = fake(level, "h_far", SX, SZ + 15.5);
            ps.addAll(List.of(in, off, behind, far));
            double[][] start = positions(ps);
            tick(level, h, 2);
            h.setTarget(in);
            double d0 = h.distanceTo(in);
            Outcome o = watch(level, h, 36, in);
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "hare spine hurt after only " + o.firstHurt() + " ticks", f);
            check(o.firstHurt() >= 0, "hare never shot (target=" + h.getTarget() + " canAttack=" + h.canAttack(in) + " los=" + h.hasLineOfSight(in) + " ground=" + h.onGround() + " downed=" + jp.revati.skyabyss.party.Downed.isDowned(in) + " guarded=" + jp.revati.skyabyss.titan.group.ReentryRules.guarded(in) + ")", f);
            check(lost(in) >= 2.5f && lost(in) <= 3.5f, "hare spine damage " + lost(in), f);
            check(in.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "hare spine should slow", f);
            check(lost(off) == 0 && lost(behind) == 0 && lost(far) == 0, "hare spine hurt a player outside the line", f);
            sameShape(o, ps, start, "hare spine", f);
            check(h.distanceTo(in) > d0 + 0.8, "hare should hop away after shooting (" + d0 + " -> " + h.distanceTo(in) + ")", f);
            notes.add("hare warns " + o.firstHurt() + " ticks, hits " + round(lost(in)));
            drop(ps);
            h.discard();

            // 近づかれたら離れる
            h = spawn(level, ModEntities.SPINE_HARE.get(), SX, SZ, 0f);
            FakePlayer close = fake(level, "h_close", SX, SZ + 2);
            ps.add(close);
            tick(level, h, 2);
            h.setTarget(close);
            // 離れる途中で 3 ブロックの線 (棘を立てる距離) に届くと、そこで立ち止まって棘を立てる。だから最後の距離ではなく、25 tick のあいだの一番遠い距離を見る
            double farthest = h.distanceTo(close);
            for (int i = 0; i < 25; i++) {
                tick(level, h, 1);
                farthest = Math.max(farthest, h.distanceTo(close));
            }
            check(farthest >= 3.0, "hare should back away from a close player (farthest " + farthest + ")", f);
            drop(ps);
            h.discard();

            // 落とし物: 棘針 40% (1〜2)・骨片 10%
            clearItems(level);
            FakePlayer killer = fake(level, "h_killer", SX + 10, SZ);
            ps.add(killer);
            for (int i = 0; i < 200; i++) {
                spawn(level, ModEntities.SPINE_HARE.get(), SX, SZ, 0f).hurt(level.damageSources().playerAttack(killer), 1000f);
            }
            int quills = count(level, GuideSetup.HARE_QUILL.get(), false);
            int chips = count(level, GuideSetup.BONE_CHIP.get(), true);
            check(quills >= 80 && quills <= 160, "hare quill drops " + quills + " from 200 (expected about 120)", f);
            check(chips >= 5 && chips <= 40, "hare bone chip drops " + chips + " from 200 (expected about 20)", f);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            notes.add("hare quills " + quills + "/200");
        } finally {
            drop(ps);
            h.discard();
        }
    }

    // ---------------------------------------------------------------- 骨負い
    private static void hauler(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        BoneHauler h = spawn(level, ModEntities.BONE_HAULER.get(), SX, SZ, 0f);
        long dayBefore = level.getDayTime();
        try {
            // 人が近づくと逃げ、集め場が人の背ごしでなければ集め場へ走る。傷つけない
            BlockPos home = new BlockPos(SX + 14, FLOOR, SZ);
            h.setHome(home);
            FakePlayer p = fake(level, "u_p", SX - 5, SZ);
            ps.add(p);
            tick(level, h, 2);
            double dHome0 = Math.hypot(h.getX() - (home.getX() + 0.5), h.getZ() - (home.getZ() + 0.5));
            tick(level, h, 90);
            double dHome = Math.hypot(h.getX() - (home.getX() + 0.5), h.getZ() - (home.getZ() + 0.5));
            check(dHome < dHome0 - 6 && dHome < 7, "hauler should run to the cache when the player is behind it (" + dHome0 + " -> " + dHome + ")", f);
            check(lost(p) == 0 && h.getTarget() == null, "hauler must not attack", f);
            h.addTag(GuardMob.TEMP_TAG);
            check(!h.shouldBeSaved(), "tagged hauler should not be saved", f);
            check(h.removeWhenFarAway(1000), "a hauler far from everyone should go", f);
            // 人の背ごしに集め場があるときは、人から離れる (人の側へは走らない)
            p.moveTo(SX + 8, FLOOR, SZ, 0, 0);
            h.moveTo(SX + 14, FLOOR, SZ, 0, 0);
            h.setHome(new BlockPos(SX, FLOOR, SZ));
            h.getNavigation().stop(); // 前の道 (前の集め場への経路) を引きずらない
            tick(level, h, 60);
            check(h.distanceTo(p) > 9, "hauler should flee away from the player when the cache is behind the player (" + h.distanceTo(p) + ")", f);
            notes.add("hauler cache " + round((float) dHome0) + " -> " + round((float) dHome));
            drop(ps);
            h.discard();

            // 落とし物: 骨片 1〜3 (必ず)
            clearItems(level);
            int min = 9;
            for (int i = 0; i < 40; i++) {
                spawn(level, ModEntities.BONE_HAULER.get(), SX, SZ, 0f).kill();
                int got = count(level, GuideSetup.BONE_CHIP.get(), true);
                min = Math.min(min, got);
                check(got <= 3, "hauler dropped " + got + " chips", f);
            }
            check(min >= 1, "hauler dropped nothing once", f);

            // 集め場のまわりに一体 (夕方〜夜・人が近いとき)
            FakePlayer near = fake(level, "u_near", SX, SZ);
            ps.add(near);
            BlockPos cache = new BlockPos(SX + 6, FLOOR, SZ + 6);
            level.setDayTime(15000);
            int one = Ch1Mobs.syncHauler(level, cache, List.of(near));
            check(one == 1 && Ch1Mobs.haulersAt(level, cache).size() == 1, "dusk with a player near should give one hauler (" + one + ")", f);
            check(Ch1Mobs.syncHauler(level, cache, List.of(near)) == 1 && Ch1Mobs.haulersAt(level, cache).size() == 1, "syncing again must not add another", f);
            BoneHauler spawned = Ch1Mobs.haulersAt(level, cache).get(0);
            check(spawned.home() != null && !spawned.shouldBeSaved() && !spawned.getTags().contains(jp.revati.skyabyss.guide.SiteSpawner.TAG), "spawned hauler: home/unsaved", f);
            level.setDayTime(5000);
            check(Ch1Mobs.syncHauler(level, cache, List.of(near)) == 0 && Ch1Mobs.haulersAt(level, cache).isEmpty(), "daytime should send the hauler away", f);
            level.setDayTime(15000);
            check(Ch1Mobs.syncHauler(level, cache, List.of()) == 0, "no player near should give no hauler", f);
        } finally {
            level.setDayTime(dayBefore);
            drop(ps);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
        }
    }

    // ---------------------------------------------------------------- 老いた歩哨
    private static ElderSentry elderAt(ServerLevel level, double x, double z) {
        ElderSentry e = spawn(level, ModEntities.ELDER_SENTRY.get(), x, z, 0f);
        e.holdAttacks();
        return e;
    }

    private static void elder(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = elderAt(level, SX, SZ);
        try {
            // 人数で体力が増える (番兵長・心臓と同じ式。3 人で 2.2 倍)
            for (int i = 0; i < 3; i++) {
                ps.add(fake(level, "e_party" + i, SX + 3 + i, SZ + 12));
            }
            tick(level, e, 2);
            check(Math.abs(e.getMaxHealth() - 140 * 2.2f) < 1f, "elder health for 3 players " + e.getMaxHealth(), f);
            drop(ps);
            e.discard();
            e = elderAt(level, SX, SZ);
            FakePlayer solo = fake(level, "e_solo", SX, SZ + 12);
            ps.add(solo);
            tick(level, e, 2);
            check(e.getMaxHealth() == 140f, "elder health for one player " + e.getMaxHealth(), f);
            e.discard();

            // 正面 (+z) からの傷は半分
            e = elderAt(level, SX, SZ);
            FakePlayer front = fake(level, "e_front", SX, SZ + 2);
            FakePlayer back = fake(level, "e_back", SX, SZ - 2);
            ps.addAll(List.of(front, back));
            tick(level, e, 2);
            float full = hurtLoss(e, level, back);
            float half = hurtLoss(e, level, front);
            check(half > 0 && full > 0 && half / full > 0.35 && half / full < 0.65, "elder front hit " + half + " vs back hit " + full, f);
            drop(ps);
            e.discard();

            swing(level, f, notes);
            rush(level, f, notes);
            stomp(level, f, notes);
            horn(level, f, notes);
            natural(level, f);
            loot(level, f, notes);
        } finally {
            drop(ps);
            e.discard();
        }
    }

    private static void swing(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = elderAt(level, SX, SZ);
        try {
            FakePlayer in = fake(level, "s_in", SX, SZ + 2.0);
            FakePlayer behind = fake(level, "s_behind", SX, SZ - 2.0);
            FakePlayer side = fake(level, "s_side", SX + 2.8, SZ + 0.5);
            FakePlayer far = fake(level, "s_far", SX, SZ + 3.6);
            ps.addAll(List.of(in, behind, side, far));
            double[][] start = positions(ps);
            tick(level, e, 2);
            e.setTarget(in);
            Outcome o = watch(level, e, 40, in, () -> e.forceAction("SWING", in));
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "elder swing hurt after only " + o.firstHurt() + " ticks", f);
            check(lost(in) >= 6f && lost(in) <= 7.5f, "elder swing damage " + lost(in), f);
            sameShape(o, ps, start, "elder swing", f);
            notes.add("elder swing warns " + o.firstHurt() + " ticks, hits " + round(lost(in)));
        } finally {
            drop(ps);
            e.discard();
        }
    }

    private static void rush(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = elderAt(level, SX, SZ);
        try {
            FakePlayer target = fake(level, "r_t", SX, SZ + 9.5);
            FakePlayer in = fake(level, "r_in", SX, SZ + 6.0);
            FakePlayer off = fake(level, "r_off", SX + 2.6, SZ + 6.0);
            FakePlayer behind = fake(level, "r_behind", SX, SZ - 2.0);
            ps.addAll(List.of(target, in, off, behind));
            double[][] start = positions(ps);
            tick(level, e, 2);
            e.setTarget(target);
            Outcome o = watch(level, e, 50, in, () -> e.forceAction("RUSH", target));
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "elder rush hurt after only " + o.firstHurt() + " ticks", f);
            check(o.firstHurt() >= ElderSentry.RUSH_WARN - 1, "elder rush hit before the line was full (" + o.firstHurt() + ")", f);
            check(lost(in) >= 8f && lost(in) <= 9.5f, "elder rush damage " + lost(in), f);
            sameShape(o, ps, start, "elder rush", f);
            check(e.getZ() > SZ + 3, "elder should rush forward (z " + (e.getZ() - SZ) + ")", f);
            notes.add("elder rush warns " + o.firstHurt() + " ticks, hits " + round(lost(in)));
        } finally {
            drop(ps);
            e.discard();
        }
    }

    private static void stomp(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = elderAt(level, SX, SZ);
        try {
            FakePlayer in = fake(level, "t_in", SX + 2.5, SZ);
            FakePlayer edge = fake(level, "t_edge", SX, SZ - 3.2);
            FakePlayer out = fake(level, "t_out", SX - 4.0, SZ);
            ps.addAll(List.of(in, edge, out));
            double[][] start = positions(ps);
            tick(level, e, 2);
            e.setTarget(in);
            Outcome o = watch(level, e, 50, in, () -> e.forceAction("STOMP", in));
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "elder stomp hurt after only " + o.firstHurt() + " ticks", f);
            check(lost(in) >= 7f && lost(in) <= 8.5f, "elder stomp damage " + lost(in), f);
            sameShape(o, ps, start, "elder stomp", f);
            notes.add("elder stomp warns " + o.firstHurt() + " ticks, hits " + round(lost(in)));
        } finally {
            drop(ps);
            e.discard();
        }
    }

    private static void horn(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = elderAt(level, SX, SZ);
        try {
            FakePlayer p = fake(level, "n_p", SX, SZ + 8);
            ps.add(p);
            tick(level, e, 2);
            e.setTarget(p);
            int before = Telegraph.damagingCreated();
            e.forceAction("HORN", p);
            tick(level, e, 22);
            check(Telegraph.damagingCreated() == before, "the horn should not make a damaging telegraph", f);
            check(e.liveSummons() == 2, "horn should call 2 archers (" + e.liveSummons() + ")", f);
            List<RibArcher> archers = level.getEntitiesOfClass(RibArcher.class, area());
            check(archers.size() == 2 && archers.stream().allMatch(a -> a.getTags().contains(GuardMob.TEMP_TAG) && !a.shouldBeSaved()), "summoned archers: tagged and unsaved", f);
            tick(level, e, 30);
            e.forceAction("HORN", p);
            tick(level, e, 22);
            check(e.liveSummons() == 2 && level.getEntitiesOfClass(RibArcher.class, area()).size() == 2, "a second horn must not exceed 2 archers", f);
            e.hurt(level.damageSources().playerAttack(p), 100000f);
            tick(level, e, 2);
            check(level.getEntitiesOfClass(RibArcher.class, area(), a -> a.isAlive()).isEmpty(), "archers should vanish when the elder falls", f);
            notes.add("elder horn calls 2 archers (cap 2), cleared on death");
        } finally {
            drop(ps);
            e.discard();
            level.getEntitiesOfClass(RibArcher.class, area()).forEach(Mob::discard);
        }
    }

    // 自分で技を選ぶ: 近くにいれば振り下ろしを始める
    private static void natural(ServerLevel level, List<String> f) {
        List<FakePlayer> ps = new ArrayList<>();
        ElderSentry e = spawn(level, ModEntities.ELDER_SENTRY.get(), SX, SZ, 0f);
        try {
            FakePlayer p = fake(level, "a_p", SX, SZ + 2.0);
            ps.add(p);
            tick(level, e, 2);
            e.setTarget(p);
            tick(level, e, 4);
            check(e.busy() && e.actionName().equals("SWING"), "elder should start a swing on a close player (" + e.actionName() + ")", f);
        } finally {
            drop(ps);
            e.discard();
        }
    }

    // 割符は人ごとに一つ、護り糸 70%、骨片 2〜4
    private static void loot(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        try {
            FakePlayer a = fake(level, "l_a", SX + 3, SZ);
            FakePlayer b = fake(level, "l_b", SX - 3, SZ);
            ps.addAll(List.of(a, b));
            Item tally = BuiltInRegistries.ITEM.get(ResourceLocation.parse(Ch1Prep.SENTRY_TALLY));
            clearItems(level);
            int threads = 0;
            int chipMin = 99;
            int chipMax = 0;
            for (int i = 0; i < 40; i++) {
                ElderSentry e = elderAt(level, SX, SZ);
                tick(level, e, 1);
                e.hurt(level.damageSources().playerAttack(a), 100000f);
                if (i == 0) {
                    check(a.getInventory().countItem(tally) == 1 && b.getInventory().countItem(tally) == 1, "each player nearby should get one tally", f);
                }
                threads += count(level, GuideSetup.ROCK_SHELL_THREAD.get(), false);
                int chips = count(level, GuideSetup.BONE_CHIP.get(), false);
                chipMin = Math.min(chipMin, chips);
                chipMax = Math.max(chipMax, chips);
                clearItems(level);
                e.discard();
            }
            check(a.getInventory().countItem(tally) == 1 && b.getInventory().countItem(tally) == 1, "the tally should be once per player", f);
            check(count(level, tally, false) == 0, "no loose tally should be dropped when everyone has one", f);
            check(threads >= 15 && threads <= 38, "elder thread drops " + threads + " from 40 (expected about 28)", f);
            check(chipMin >= 2 && chipMax <= 4, "elder bone chips " + chipMin + ".." + chipMax, f);
            notes.add("elder threads " + threads + "/40");
        } finally {
            drop(ps);
        }
    }
}
