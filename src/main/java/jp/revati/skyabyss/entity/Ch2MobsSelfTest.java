package jp.revati.skyabyss.entity;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.registry.MobSounds;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.story.ch2.Ch2Mobs;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.tide.TideOcean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
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

// 二章の地上の敵 4 種 (P12) の自己テスト。SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ch2 mobs" ./gradlew runServer。
// 結果は SELFTEST ch2 mobs PASSED / FAILED。
//   登録     エンティティ・体力・スポーンエッグ・音・経験値の表・ひとり歩き (章・時刻・地形)・群れの形 (GroupOwned)
//   潮だまり貝 近づくと開く (予告 0.8 秒以上・円と当たりが同じ形)・挟む 5 と鈍足 II 1.5 秒・離れれば挟まずに閉じる・閉じた殻は硬いが倒せる
//   砂潜り   潜っている間は傷を受けない・ひとり歩きの個体は満潮で出ない・干潮と湧き台の個体は出る (予告 0.8 秒以上・噛む 4)・飛びかかりの帯・潮が満ちると潜る
//   宿借り   敵意なし・人を見ると潜る・叩くと一度だけ吐く・行列 (先頭の後ろに 2〜4、前の者を追う・保存しない)
//   潮の番人 人数で体力・突きの予告 1 秒以上 (帯と当たりが同じ形・9)・満ち潮の輪の予告 1 秒以上 (4・鈍足)・輪の中が水になり、時間で元に戻る (浅瀬の部屋も)・倒れると水が引く
//   落とし物 素材 (小真珠・塩結晶・貝の欠け・真珠の護り糸) が登録されていれば割合を見る。無ければ落ちないこと (落とさずに倒れる) を見る
public final class Ch2MobsSelfTest {
    private static final int SX = 6400;
    private static final int SZ = 6400;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private Ch2MobsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("ch2 mobs (load)", Ch2MobsSelfTest::load),
                SelfTestRunner.Step.once("ch2 mobs", 40, Ch2MobsSelfTest::run));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = (SX >> 4) - 3; cx <= (SX >> 4) + 3; cx++) {
            for (int cz = (SZ >> 4) - 3; cz <= (SZ >> 4) + 3; cz++) {
                level.setChunkForced(cx, cz, true);
                level.getChunk(cx, cz);
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
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            registration(level, f, notes);
            clam(level, f, notes);
            burrower(level, f, notes);
            hermit(level, f, notes);
            warden(level, f, notes);
            drops(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch2 mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            Telegraph.record(null);
            server.setDifficulty(before, true);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            for (ShoreFlood.Patch p : ShoreFlood.active()) {
                ShoreFlood.revert(server, p);
            }
            clearItems(level);
            platform(level);
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch2 mobs PASSED (4 mobs registered with eggs/sounds/xp/wanderers; clam opens 0.8 s, pinches + slows, closes when left, hard shell; "
                    + "burrower buried + invulnerable, stays down at high tide (wild), emerges at low tide / from pads, bite line; hermit hides, spits once, procession; "
                    + "warden scaled by party, thrust 1.1 s, flood ring 1.2 s, water reverts; drops) {}", notes);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch2 mobs FAILED: {}", m));
        }
    }

    // ---------------------------------------------------------------- 部品
    private static AABB area() {
        return new AABB(SX - 30, FLOOR - 4, SZ - 30, SX + 30, FLOOR + 20, SZ + 30);
    }

    private static void platform(ServerLevel level) {
        for (int x = SX - 24; x <= SX + 24; x++) {
            for (int z = SZ - 24; z <= SZ + 24; z++) {
                level.setBlock(new BlockPos(x, FLOOR - 1, z), Blocks.SAND.defaultBlockState(), 2);
                for (int y = 0; y < 8; y++) {
                    level.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("ch2 mobs " + tag).getBytes(StandardCharsets.UTF_8)), "[c2_" + tag + "]")) {
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

    private static void clearItems(ServerLevel level) {
        level.getEntitiesOfClass(ItemEntity.class, area()).forEach(ItemEntity::discard);
    }

    private static int count(ServerLevel level, Item item) {
        int n = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, area())) {
            if (item != null && e.getItem().is(item)) {
                n += e.getItem().getCount();
            }
        }
        return n;
    }

    private record Outcome(int firstHurt, TelegraphShape shape) {
    }

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

    private static void sameShape(Outcome o, List<FakePlayer> ps, double[][] start, String name, List<String> f) {
        if (o.shape() == null) {
            f.add(name + ": no telegraph was made");
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

    private static float hurtLoss(Mob m, ServerLevel level, FakePlayer attacker, float amount) {
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        m.hurt(level.damageSources().playerAttack(attacker), amount);
        float loss = m.getMaxHealth() - m.getHealth();
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        return loss;
    }

    private static float round(float v) {
        return Math.round(v * 100f) / 100f;
    }

    private static Holder<Biome> biome(net.minecraft.core.Registry<Biome> reg, net.minecraft.resources.ResourceKey<Biome> key) {
        return reg.getHolderOrThrow(key);
    }

    // ---------------------------------------------------------------- 登録
    private static void registration(ServerLevel level, List<String> f, List<String> notes) {
        record Def(String id, double hp) {
        }
        for (Def d : List.of(new Def(Ch2Mobs.TIDEPOOL_CLAM, 20), new Def(Ch2Mobs.SAND_BURROWER, 16), new Def(Ch2Mobs.DRIFT_HERMIT, 14),
                new Def(Ch2Mobs.SHORE_WARDEN, 160))) {
            ResourceLocation key = ResourceLocation.parse(d.id());
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(key), d.id() + " is not registered", f);
            check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg")), d.id() + " has no spawn egg", f);
            check(GrowthRewards.MOD_ENEMIES.containsKey(d.id()), d.id() + " has no xp entry", f);
            Mob m = (Mob) BuiltInRegistries.ENTITY_TYPE.get(key).create(level);
            check(m != null && m.getMaxHealth() == d.hp(), d.id() + " max health " + (m == null ? -1 : m.getMaxHealth()), f);
            check(m instanceof GroupOwned, d.id() + " is not group owned (SiteSpawner needs it)", f);
            if (m != null) {
                m.discard();
            }
        }
        check(GrowthRewards.MOD_ENEMIES.get(Ch2Mobs.TIDEPOOL_CLAM).xp() <= 10 && GrowthRewards.MOD_ENEMIES.get(Ch2Mobs.SAND_BURROWER).xp() <= 10
                && GrowthRewards.MOD_ENEMIES.get(Ch2Mobs.SHORE_WARDEN).xp() <= 60, "ground mob xp is not small", f);
        check(MobSounds.TIDEPOOL_CLAM_OPEN.get() != null && MobSounds.SAND_BURROWER_BULGE.get() != null && MobSounds.SHORE_WARDEN_RING.get() != null
                && MobSounds.DRIFT_HERMIT_SPIT.get() != null, "skill sounds", f);
        check(ModItems.SHORE_WARDEN_EGG.get() != null, "egg item", f);
        // ひとり歩き: 貝 = 浜の昼夜、砂潜り = 砂浜 (干潮だけ)、宿借り = 砂浜の昼。どれも潮の章、地形つき
        int ok = 0;
        for (var w : ChapterMobSpawner.registered()) {
            switch (w.id()) {
                case "ch2_tidepool_clam" -> ok += w.chapter() == Chapters.TIDE && w.time() == ChapterMobSpawner.Time.ANY && w.entity().equals(Ch2Mobs.TIDEPOOL_CLAM) && w.biome() != null ? 1 : 0;
                case "ch2_sand_burrower" -> ok += w.chapter() == Chapters.TIDE && w.entity().equals(Ch2Mobs.SAND_BURROWER) && w.biome() != null ? 1 : 0;
                case "ch2_drift_hermit" -> ok += w.chapter() == Chapters.TIDE && w.time() == ChapterMobSpawner.Time.DAY && w.entity().equals(Ch2Mobs.DRIFT_HERMIT) && w.biome() != null ? 1 : 0;
                default -> {
                }
            }
        }
        check(ok == 3, "ch2 wanderers registered " + ok + "/3", f);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        check(Ch2Mobs.shoreBiome(biome(biomes, Biomes.BEACH)) && Ch2Mobs.shoreBiome(biome(biomes, Biomes.STONY_SHORE)) && !Ch2Mobs.shoreBiome(biome(biomes, Biomes.PLAINS))
                && !Ch2Mobs.shoreBiome(biome(biomes, Biomes.OCEAN)), "clam biomes", f);
        check(Ch2Mobs.sandBiome(biome(biomes, Biomes.BEACH)) && !Ch2Mobs.sandBiome(biome(biomes, Biomes.STONY_SHORE)) && !Ch2Mobs.sandBiome(biome(biomes, Biomes.DESERT)),
                "sand biomes", f);
        // 素材の ID (別の作業が登録する。無くても動く)
        List<String> missing = new ArrayList<>();
        for (String id : List.of(Ch2Mobs.SMALL_PEARL, Ch2Mobs.SALT_CRYSTAL, Ch2Mobs.SHELL_CHIP, Ch2Mobs.PEARL_WARD_THREAD)) {
            if (Ch2Mobs.item(id) == null) {
                missing.add(id);
            }
        }
        notes.add(missing.isEmpty() ? "all drop materials registered" : "drop materials not registered yet: " + missing);
    }

    // ---------------------------------------------------------------- 潮だまり貝
    private static void clam(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        TidepoolClam c = spawn(level, ModEntities.TIDEPOOL_CLAM.get(), SX + 0.5, SZ + 0.5, 0f);
        try {
            // 円の中 (1.0) と外 (2.1。NOTICE の内だが REACH の外) の人
            FakePlayer in = fake(level, "c_in", SX + 1.5, SZ + 0.5);
            FakePlayer out = fake(level, "c_out", SX + 0.5, SZ + 2.6);
            ps.addAll(List.of(in, out));
            double[][] start = positions(ps);
            check(c.shell() == TidepoolClam.CLOSED, "clam does not start closed", f);
            Outcome o = watch(level, c, 30, in, () -> {
            });
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS && o.firstHurt() >= TidepoolClam.WARN, "clam pinched after only " + o.firstHurt() + " ticks", f);
            check(o.firstHurt() >= 0 && o.firstHurt() <= TidepoolClam.WARN + 3, "clam never pinched in time (" + o.firstHurt() + ")", f);
            check(Math.abs(lost(in) - TidepoolClam.PINCH_DAMAGE) < 0.6f, "clam pinch damage " + lost(in), f);
            MobEffectInstance slow = in.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
            check(slow != null && slow.getAmplifier() == 1 && slow.getDuration() > 10 && slow.getDuration() <= 30, "clam slowness II 1.5 s: " + slow, f);
            check(lost(out) == 0, "clam hurt a player outside its circle", f);
            sameShape(o, ps, start, "clam pinch", f);
            check(c.shell() == TidepoolClam.OPEN, "clam is not open after the pinch (" + c.shell() + ")", f);
            notes.add("clam pinches at " + o.firstHurt() + " ticks for " + round(lost(in)));
            // 開いている間は傷が通り、閉じた殻は硬い
            float open = hurtLoss(c, level, in, 6f);
            drop(ps);
            c.discard();
            c = spawn(level, ModEntities.TIDEPOOL_CLAM.get(), SX + 0.5, SZ + 0.5, 0f);
            FakePlayer far = fake(level, "c_far", SX + 10, SZ);
            ps.add(far);
            float closed = hurtLoss(c, level, far, 6f);
            check(open > 5.5f && closed > 0 && closed < open * 0.5f, "clam open/closed damage " + open + "/" + closed, f);
            notes.add("clam open/closed " + round(open) + "/" + round(closed));
            // 閉じたままでも倒せる (湧き台のクリアをふさがない)
            c.hurt(level.damageSources().playerAttack(far), 100f);
            check(!c.isAlive(), "a closed clam cannot be killed", f);
            drop(ps);
            // 開いている途中で離れると、挟まずに閉じる
            c = spawn(level, ModEntities.TIDEPOOL_CLAM.get(), SX + 0.5, SZ + 0.5, 0f);
            FakePlayer walker = fake(level, "c_walker", SX + 2.2, SZ + 0.5);
            ps.add(walker);
            tick(level, c, 5);
            check(c.shell() == TidepoolClam.OPENING, "clam did not start opening (" + c.shell() + ")", f);
            walker.moveTo(SX + 8, FLOOR, SZ + 0.5);
            tick(level, c, 20);
            check(lost(walker) == 0, "clam pinched a player who walked away", f);
            check(c.shell() == TidepoolClam.CLOSED || c.shell() == TidepoolClam.CLOSING, "clam did not close when left (" + c.shell() + ")", f);
        } finally {
            drop(ps);
            c.discard();
        }
    }

    // ---------------------------------------------------------------- 砂潜り
    private static void burrower(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        SandBurrower b = spawn(level, ModEntities.SAND_BURROWER.get(), SX + 0.5, SZ + 0.5, 0f);
        try {
            // 潮の判定そのもの: 一日のうちに干潮と満潮の両方がある
            boolean seenLow = false;
            boolean seenHigh = false;
            for (long t = 0; t < 48000; t += 200) {
                if (TideOcean.isLowTide(t)) {
                    seenLow = true;
                } else {
                    seenHigh = true;
                }
            }
            check(seenLow && seenHigh, "tide never changes", f);
            check(b.isBuried() && b.isInvisible(), "burrower does not start buried and hidden", f);
            FakePlayer p = fake(level, "b_over", SX + 0.5, SZ + 0.5);
            ps.add(p);
            check(!b.hurt(level.damageSources().playerAttack(p), 5f) && b.getHealth() == b.getMaxHealth(), "a buried burrower took damage", f);
            // ひとり歩きの個体: 満潮では真上を歩いても出ない
            b.testTideBound(true);
            b.testTide(false);
            tick(level, b, 40);
            check(b.isBuried() && lost(p) == 0 && b.actionName().equals("NONE"), "a wild burrower came out at high tide", f);
            // 干潮: 砂が盛り上がり (0.8 秒以上)、飛び出して噛む
            b.testTide(true);
            FakePlayer side = fake(level, "b_side", SX + 3.5, SZ + 0.5);
            ps.add(side);
            double[][] start = positions(ps);
            Outcome o = watch(level, b, 30, p, () -> {
            });
            check(o.firstHurt() >= Telegraph.MIN_WARN_TICKS, "burrower bit after only " + o.firstHurt() + " ticks", f);
            check(o.firstHurt() >= 0 && o.firstHurt() <= SandBurrower.WARN + 3, "burrower never bit in time (" + o.firstHurt() + ")", f);
            check(Math.abs(lost(p) - SandBurrower.BITE_DAMAGE) < 0.6f, "burrower bite damage " + lost(p), f);
            check(!b.isBuried() && !b.isInvisible(), "burrower did not come out", f);
            sameShape(o, ps, start, "burrower bulge", f);
            notes.add("burrower bulge " + o.firstHurt() + " ticks, bites " + round(lost(p)));
            // 出たあと潮が満ちると潜る
            b.testTide(false);
            tick(level, b, 40);
            check(b.isBuried(), "a wild burrower stayed out at high tide", f);
            drop(ps);
            b.discard();

            // 湧き台 (蔵) の個体は潮に関係なく出る
            b = spawn(level, ModEntities.SAND_BURROWER.get(), SX + 0.5, SZ + 0.5, 0f);
            b.addTag(SiteSpawner.TAG);
            b.testTide(false);
            FakePlayer q = fake(level, "b_pad", SX + 0.5, SZ + 0.5);
            ps.add(q);
            tick(level, b, 30);
            check(!b.isBuried() && lost(q) > 0, "a pad burrower stayed buried at high tide", f);
            drop(ps);
            b.discard();

            // 飛びかかり: 床の帯 0.8 秒以上、帯の中だけ当たる
            b = spawn(level, ModEntities.SAND_BURROWER.get(), SX + 0.5, SZ + 0.5, 0f);
            b.addTag(SiteSpawner.TAG);
            FakePlayer bait = fake(level, "b_bait", SX + 0.5, SZ + 0.5);
            ps.add(bait);
            tick(level, b, 30);
            drop(ps);
            check(!b.isBuried(), "burrower did not come out for the bite test", f);
            b.moveTo(SX + 0.5, FLOOR, SZ + 0.5, 0, 0);
            b.setDeltaMovement(0, 0, 0);
            tick(level, b, 40);
            FakePlayer front = fake(level, "b_front", SX + 0.5, SZ + 3.0);
            FakePlayer off = fake(level, "b_off", SX + 2.5, SZ + 1.5);
            ps.addAll(List.of(front, off));
            double[][] s2 = positions(ps);
            SandBurrower fb = b;
            Outcome bite = watch(level, b, 40, front, () -> fb.setTarget(front));
            check(bite.firstHurt() >= Telegraph.MIN_WARN_TICKS, "burrower leap-bite after only " + bite.firstHurt() + " ticks", f);
            check(lost(front) > 0 && lost(off) == 0, "burrower bite hit front " + lost(front) + " off " + lost(off), f);
            sameShape(bite, ps, s2, "burrower bite", f);
            notes.add("burrower bite " + bite.firstHurt() + " ticks");
        } finally {
            drop(ps);
            b.discard();
        }
    }

    // ---------------------------------------------------------------- 宿借り
    private static void hermit(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        DriftHermit h = spawn(level, ModEntities.DRIFT_HERMIT.get(), SX + 0.5, SZ + 0.5, 0f);
        try {
            FakePlayer p = fake(level, "h_near", SX + 3.5, SZ + 0.5);
            ps.add(p);
            check(!h.canAttack(p), "hermit can attack a player", f);
            tick(level, h, 3);
            check(h.isHidden(), "hermit did not hide when a player came near", f);
            tick(level, h, 60);
            check(lost(p) == 0, "hermit hurt a player", f);
            // 叩くと一度だけ吐く
            clearItems(level);
            h.hurt(level.damageSources().playerAttack(p), 1f);
            check(h.hasSpat(), "hermit did not spit when hit", f);
            int first = level.getEntitiesOfClass(ItemEntity.class, area()).size();
            for (int i = 0; i < 5; i++) {
                h.invulnerableTime = 0;
                h.setHealth(h.getMaxHealth());
                h.hurt(level.damageSources().playerAttack(p), 1f);
            }
            int after = level.getEntitiesOfClass(ItemEntity.class, area()).size();
            boolean materials = Ch2Mobs.item(Ch2Mobs.SHELL_CHIP) != null || Ch2Mobs.item(Ch2Mobs.SMALL_PEARL) != null;
            check(after == first && first == (materials ? 1 : 0), "hermit spat " + first + " then " + after + " items (materials registered: " + materials + ")", f);
            check(!h.spit(p), "hermit spat twice", f);
            clearItems(level);
            // 人が離れると出てくる
            p.moveTo(SX + 20, FLOOR, SZ);
            tick(level, h, DriftHermit.UNHIDE_DELAY + 5);
            check(!h.isHidden(), "hermit stayed hidden after the player left", f);
            drop(ps);
            h.discard();

            // 行列: ひとり歩きの一匹 (湧き台の印) の後ろに 2〜4 匹。保存しない連れ。前の者をたどれる
            FakePlayer watcher = fake(level, "h_watch", SX + 20, SZ + 20);
            ps.add(watcher);
            h = spawn(level, ModEntities.DRIFT_HERMIT.get(), SX + 0.5, SZ + 0.5, 0f);
            h.addTag(SiteSpawner.TAG);
            tick(level, h, 2);
            DriftHermit lead = h;
            List<DriftHermit> line = level.getEntitiesOfClass(DriftHermit.class, area(), x -> x != lead && x.getTags().contains(GuardMob.TEMP_TAG));
            check(line.size() >= DriftHermit.MIN_FOLLOWERS && line.size() <= DriftHermit.MAX_FOLLOWERS, "procession of " + line.size() + " followers", f);
            int chained = 0;
            for (DriftHermit x : line) {
                if (x.ahead() != null && !x.shouldBeSaved()) {
                    chained++;
                }
            }
            check(chained == line.size(), "followers not chained / saved: " + chained + "/" + line.size(), f);
            check(lead.shouldBeSaved() == false, "a wanderer leader should not be saved", f);
            // 連れは前の者を追う: 先頭を 6 ブロック動かすと、一匹目が近づく
            if (!line.isEmpty()) {
                DriftHermit firstF = null;
                for (DriftHermit x : line) {
                    if (lead.getUUID().equals(x.ahead())) {
                        firstF = x;
                    }
                }
                check(firstF != null, "no follower walks behind the leader", f);
                if (firstF != null) {
                    lead.moveTo(SX + 0.5, FLOOR, SZ + 7.5, 0f, 0f);
                    double d0 = firstF.distanceTo(lead);
                    for (int i = 0; i < 80; i++) {
                        firstF.tickCount++;
                        firstF.tick();
                    }
                    double d1 = firstF.distanceTo(lead);
                    check(d1 < d0 - 1.0, "follower did not follow (" + round((float) d0) + " -> " + round((float) d1) + ")", f);
                    notes.add("procession of " + line.size() + ", follower " + round((float) d0) + " -> " + round((float) d1));
                }
            }
            line.forEach(DriftHermit::discard);
        } finally {
            drop(ps);
            h.discard();
            level.getEntitiesOfClass(DriftHermit.class, area()).forEach(DriftHermit::discard);
        }
    }

    // ---------------------------------------------------------------- 潮の番人
    private static ShoreWarden wardenAt(ServerLevel level, double x, double z) {
        ShoreWarden w = spawn(level, ModEntities.SHORE_WARDEN.get(), x, z, 0f);
        w.holdAttacks();
        return w;
    }

    private static void warden(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ShoreWarden w = wardenAt(level, SX + 0.5, SZ + 0.5);
        try {
            for (int i = 0; i < 3; i++) {
                ps.add(fake(level, "w_party" + i, SX + 3 + i, SZ + 12));
            }
            tick(level, w, 2);
            check(Math.abs(w.getMaxHealth() - 160 * 2.2f) < 1f, "warden health for 3 players " + w.getMaxHealth(), f);
            drop(ps);
            w.discard();
            w = wardenAt(level, SX + 0.5, SZ + 0.5);
            FakePlayer solo = fake(level, "w_solo", SX + 0.5, SZ + 14);
            ps.add(solo);
            tick(level, w, 2);
            check(w.getMaxHealth() == 160f, "warden health for one player " + w.getMaxHealth(), f);
            drop(ps);

            // 銛の突き: 前 (+z) の帯。中 2 人、横と後ろは外。予告 1 秒以上
            FakePlayer in = fake(level, "w_in", SX + 0.5, SZ + 3.0);
            FakePlayer tip = fake(level, "w_tip", SX + 0.5, SZ + 4.8);
            FakePlayer side = fake(level, "w_side", SX + 2.6, SZ + 2.5);
            FakePlayer back = fake(level, "w_back", SX + 0.5, SZ - 2.5);
            ps.addAll(List.of(in, tip, side, back));
            double[][] start = positions(ps);
            ShoreWarden fw = w;
            Outcome o = watch(level, w, 50, in, () -> fw.forceAction("THRUST", in));
            check(o.firstHurt() >= 20 && o.firstHurt() >= ShoreWarden.THRUST_WARN, "warden thrust hurt after only " + o.firstHurt() + " ticks (needs 1 s)", f);
            check(o.firstHurt() >= 0 && o.firstHurt() <= ShoreWarden.THRUST_WARN + 3, "warden thrust never landed (" + o.firstHurt() + ")", f);
            check(Math.abs(lost(in) - ShoreWarden.THRUST_DAMAGE) < 0.6f, "warden thrust damage " + lost(in), f);
            check(lost(side) == 0 && lost(back) == 0, "warden thrust hit outside the line", f);
            sameShape(o, ps, start, "warden thrust", f);
            notes.add("warden thrust " + o.firstHurt() + " ticks, " + round(lost(in)));
            drop(ps);
            w.discard();

            // 満ち潮の輪 (乾いた床): 狙った人の足もとに輪。予告 1 秒以上、4 と鈍足。輪の中に水が置かれ、時間で消える
            w = wardenAt(level, SX + 0.5, SZ + 0.5);
            FakePlayer target = fake(level, "w_target", SX + 0.5, SZ + 7.5);
            FakePlayer near = fake(level, "w_near", SX + 2.0, SZ + 7.5);
            FakePlayer outside = fake(level, "w_outside", SX + 0.5, SZ + 11.5);
            ps.addAll(List.of(target, near, outside));
            double[][] rs = positions(ps);
            ShoreWarden rw = w;
            Outcome r = watch(level, w, 50, target, () -> rw.forceAction("RING", target));
            check(r.firstHurt() >= 20 && r.firstHurt() >= ShoreWarden.RING_WARN, "warden ring hurt after only " + r.firstHurt() + " ticks (needs 1 s)", f);
            check(Math.abs(lost(target) - ShoreWarden.RING_DAMAGE) < 0.6f, "warden ring damage " + lost(target), f);
            check(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "warden ring did not slow", f);
            sameShape(r, ps, rs, "warden ring", f);
            List<ShoreFlood.Patch> fl = w.floods();
            int cells = fl.isEmpty() ? 0 : fl.get(0).cells().size();
            check(cells >= 12, "flood ring placed only " + cells + " water cells", f);
            boolean wet = !fl.isEmpty() && fl.get(0).cells().stream().allMatch(c -> level.getBlockState(c).is(Blocks.WATER) && c.getY() == FLOOR);
            check(wet, "flood water is not on the dry floor", f);
            long now = level.getGameTime();
            ShoreFlood.revertDue(level.getServer(), now + ShoreWarden.FLOOD_TICKS - 5);
            check(!fl.isEmpty() && level.getBlockState(fl.get(0).cells().get(0)).is(Blocks.WATER), "flood water left too early", f);
            ShoreFlood.revertDue(level.getServer(), now + ShoreWarden.FLOOD_TICKS + 1);
            boolean dry = !fl.isEmpty() && fl.get(0).cells().stream().noneMatch(c -> level.getBlockState(c).is(Blocks.WATER));
            check(dry && w.floods().isEmpty(), "flood water did not revert", f);
            notes.add("warden ring " + r.firstHurt() + " ticks, flood " + cells + " cells reverted");
            drop(ps);
            w.discard();
            platform(level);

            // 浅瀬の部屋 (足もとに 1 段の水): 輪で 2 段になり、戻すと 1 段の浅瀬に戻る。倒れると水がすぐ引く
            for (int x = SX - 8; x <= SX + 8; x++) {
                for (int z = SZ - 8; z <= SZ + 14; z++) {
                    level.setBlock(new BlockPos(x, FLOOR, z), Blocks.WATER.defaultBlockState(), 2);
                }
            }
            w = wardenAt(level, SX + 0.5, SZ + 0.5);
            FakePlayer wader = fake(level, "w_wader", SX + 0.5, SZ + 7.5);
            ps.add(wader);
            ShoreWarden ww = w;
            watch(level, w, 30, wader, () -> ww.forceAction("RING", wader));
            List<ShoreFlood.Patch> pool = w.floods();
            boolean raised = !pool.isEmpty() && !pool.get(0).cells().isEmpty() && pool.get(0).cells().stream().allMatch(c -> c.getY() == FLOOR + 1);
            check(raised, "flood in the shallow room did not raise the water by one", f);
            w.hurt(level.damageSources().playerAttack(wader), 10000f);
            check(!w.isAlive(), "warden did not die", f);
            boolean back2 = !pool.isEmpty() && pool.get(0).cells().stream().noneMatch(c -> level.getBlockState(c).is(Blocks.WATER))
                    && level.getBlockState(new BlockPos(SX, FLOOR, SZ + 7)).is(Blocks.WATER);
            check(back2, "the shallow room did not return to normal when the warden died", f);
            check(ShoreFlood.activeCount() == 0, "flood patches left: " + ShoreFlood.activeCount(), f);
        } finally {
            drop(ps);
            w.discard();
            platform(level);
        }
    }

    // ---------------------------------------------------------------- 落とし物
    private static void drops(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        try {
            FakePlayer killer = fake(level, "d_killer", SX + 12, SZ);
            ps.add(killer);
            Item pearl = Ch2Mobs.item(Ch2Mobs.SMALL_PEARL);
            Item salt = Ch2Mobs.item(Ch2Mobs.SALT_CRYSTAL);
            Item chip = Ch2Mobs.item(Ch2Mobs.SHELL_CHIP);
            Item thread = Ch2Mobs.item(Ch2Mobs.PEARL_WARD_THREAD);
            clearItems(level);
            for (int i = 0; i < 100; i++) {
                spawn(level, ModEntities.TIDEPOOL_CLAM.get(), SX + 0.5, SZ + 0.5, 0f).hurt(level.damageSources().playerAttack(killer), 1000f);
            }
            int pearls = count(level, pearl);
            int salts = count(level, salt);
            if (pearl != null) {
                check(pearls >= 45 && pearls <= 110, "clam pearls " + pearls + "/100 (expected about 75)", f);
            }
            if (salt != null) {
                check(salts >= 8 && salts <= 35, "clam salt " + salts + "/100 (expected about 20)", f);
            }
            notes.add("clam pearls " + pearls + ", salt " + salts);
            clearItems(level);
            for (int i = 0; i < 100; i++) {
                SandBurrower b = spawn(level, ModEntities.SAND_BURROWER.get(), SX + 0.5, SZ + 0.5, 0f);
                b.hurt(level.damageSources().genericKill(), 1000f);
            }
            clearItems(level);
            for (int i = 0; i < 100; i++) {
                SandBurrower b = spawn(level, ModEntities.SAND_BURROWER.get(), SX + 0.5, SZ + 0.5, 0f);
                // 潜ったままでも通る傷を、倒した人つきで (setLastHurtByPlayer は湧いた直後だと tickCount = 0 で「人が倒した」にならない)
                b.hurt(level.damageSources().source(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL, killer), 1000f);
            }
            int bs = count(level, salt);
            if (salt != null) {
                check(bs >= 15 && bs <= 48, "burrower salt " + bs + "/100 (expected about 30)", f);
            }
            notes.add("burrower salt " + bs);
            clearItems(level);
            for (int i = 0; i < 3; i++) {
                ShoreWarden w = spawn(level, ModEntities.SHORE_WARDEN.get(), SX + 0.5, SZ + 0.5, 0f);
                w.hurt(level.damageSources().playerAttack(killer), 100000f);
            }
            int threads = count(level, thread);
            int ws = count(level, salt);
            if (thread != null) {
                check(threads == 3, "warden threads " + threads + "/3", f);
            }
            if (salt != null) {
                check(ws >= 6 && ws <= 12, "warden salt " + ws + " from 3", f);
            }
            notes.add("warden threads " + threads + ", salt " + ws + (chip == null ? "" : ""));
            if (pearl == null && salt == null && chip == null && thread == null) {
                int any = level.getEntitiesOfClass(ItemEntity.class, area()).size();
                check(any == 0, "mobs dropped " + any + " items although no material is registered", f);
            }
            clearItems(level);
        } finally {
            drop(ps);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
        }
    }
}
