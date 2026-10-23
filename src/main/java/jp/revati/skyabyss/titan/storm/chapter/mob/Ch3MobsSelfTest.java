package jp.revati.skyabyss.titan.storm.chapter.mob;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.entity.GuardMob;
import jp.revati.skyabyss.guide.ChapterMobSpawner;
import jp.revati.skyabyss.guide.SiteSpawner;
import jp.revati.skyabyss.rpg.GrowthRewards;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import jp.revati.skyabyss.titan.storm.chapter.ScorchedWatch;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;

// 三章の地上の敵 4 種 (P16。docs/design/21 の 5.1) の自己テスト。SKYABYSS_SELFTEST=1 SKYABYSS_SELFTEST_ONLY="ch3 mobs" ./gradlew runServer。
// 結果は SELFTEST ch3 mobs PASSED / FAILED。
//   登録     エンティティ・体力・スポーンエッグ・音・経験値の表・ひとり歩き (雷翼の章・時刻・地形)・群れの形 (GroupOwned)・焼けた見張り塔の湧き台
//   帯電毛玉 帯電 4 秒 (予告) のあとに放電・固まった二人に渡り、離れた人には渡らない・放電 4 と 0.8 倍
//   峰の雛   上がってから地面の帯 (予告 0.8 秒以上) → 帯の上の人に 5・帯の外は無傷・外すとよろめき、傷 1.5 倍
//   雷拾い   敵意なし・人を見ると逃げる・荷を一度だけ落とす (銅線)・逃げる先は原
//   焼けた羽守 人数で体力・突撃は嵐を待たない・雷の輪 (予告 1 秒以上、中だけ 6)・外套の跳躍 (落ちる先の輪 1 秒以上、8)・ふだん傷 0.5 倍、柱の雷のあと 1.25 倍
//   落とし物 帯電綿・焦げ羽・銅線・雷の護り糸の割合
public final class Ch3MobsSelfTest {
    private static final int SX = 6600;
    private static final int SZ = -6400;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private Ch3MobsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(SelfTestRunner.Step.once("ch3 mobs (load)", Ch3MobsSelfTest::load),
                SelfTestRunner.Step.once("ch3 mobs", 40, Ch3MobsSelfTest::run));
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
            puff(level, f, notes);
            fledgling(level, f, notes);
            gleaner(level, f, notes);
            warden(level, f, notes);
            drops(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch3 mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.record(null);
            server.setDifficulty(before, true);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
            clearItems(level);
            platform(level);
            unload(level);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch3 mobs PASSED (4 mobs registered with eggs/sounds/xp/wanderers/watch pads; puff charges 4 s and chains across a cluster only; "
                    + "fledgling line 0.8 s, hits on the line, staggers on a miss; gleaner flees and drops its pack once; warden scaled, ring and leap telegraphs, pillar exposure; drops) {}", notes);
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch3 mobs FAILED: {}", m));
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
                for (int y = 0; y < 10; y++) {
                    level.setBlock(new BlockPos(x, FLOOR + y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("ch3 mobs " + tag).getBytes(StandardCharsets.UTF_8)), "[c3_" + tag + "]")) {
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

    private static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, double x, double y, double z, float yaw) {
        T m = type.create(level);
        m.moveTo(x, y, z, yaw, 0);
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

    private static float magicLoss(Mob m, ServerLevel level, float amount) {
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        m.hurt(level.damageSources().magic(), amount);
        float loss = m.getMaxHealth() - m.getHealth();
        m.invulnerableTime = 0;
        m.setHealth(m.getMaxHealth());
        return loss;
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

    // ---------------------------------------------------------------- 登録
    private static void registration(ServerLevel level, List<String> f, List<String> notes) {
        record Def(String id, double hp, int maxXp) {
        }
        for (Def d : List.of(new Def(Ch3Mobs.STATIC_PUFF, 10, 10), new Def(Ch3Mobs.PEAK_FLEDGLING, 14, 10), new Def(Ch3Mobs.LIGHTNING_GLEANER, 12, 5),
                new Def(Ch3Mobs.SCORCHED_WARDEN, 190, 60))) {
            ResourceLocation key = ResourceLocation.parse(d.id());
            check(BuiltInRegistries.ENTITY_TYPE.containsKey(key), d.id() + " is not registered", f);
            check(BuiltInRegistries.ITEM.containsKey(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg")), d.id() + " has no spawn egg", f);
            check(GrowthRewards.MOD_ENEMIES.containsKey(d.id()) && GrowthRewards.MOD_ENEMIES.get(d.id()).xp() <= d.maxXp(), d.id() + " has no small xp entry", f);
            Mob m = (Mob) BuiltInRegistries.ENTITY_TYPE.get(key).create(level);
            check(m != null && m.getMaxHealth() == d.hp(), d.id() + " max health " + (m == null ? -1 : m.getMaxHealth()), f);
            check(m instanceof GroupOwned, d.id() + " is not group owned (SiteSpawner needs it)", f);
            if (m != null) {
                m.discard();
            }
        }
        check(Ch3Mobs.PUFF_DISCHARGE.get() != null && Ch3Mobs.FLEDGLING_DIVE.get() != null && Ch3Mobs.GLEANER_DROP.get() != null && Ch3Mobs.WARDEN_RING.get() != null,
                "skill sounds", f);
        int ok = 0;
        for (var w : ChapterMobSpawner.registered()) {
            switch (w.id()) {
                case "ch3_static_puff" -> ok += w.chapter() == Chapters.STORM && w.time() == ChapterMobSpawner.Time.ANY && w.entity().equals(Ch3Mobs.STATIC_PUFF) ? 1 : 0;
                case "ch3_peak_fledgling" -> ok += w.chapter() == Chapters.STORM && w.time() == ChapterMobSpawner.Time.DAY && w.entity().equals(Ch3Mobs.PEAK_FLEDGLING) ? 1 : 0;
                case "ch3_lightning_gleaner" -> ok += w.chapter() == Chapters.STORM && w.time() == ChapterMobSpawner.Time.DAY && w.entity().equals(Ch3Mobs.LIGHTNING_GLEANER) ? 1 : 0;
                default -> {
                }
            }
        }
        check(ok == 3, "ch3 wanderers registered " + ok + "/3", f);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        check(Ch3Mobs.highBiome(biomes.getHolderOrThrow(Biomes.JAGGED_PEAKS)) && Ch3Mobs.highBiome(biomes.getHolderOrThrow(Biomes.MEADOW))
                && !Ch3Mobs.highBiome(biomes.getHolderOrThrow(Biomes.PLAINS)) && !Ch3Mobs.highBiome(biomes.getHolderOrThrow(Biomes.BEACH)), "puff biomes", f);
        check(Ch3Mobs.peakBiome(biomes.getHolderOrThrow(Biomes.STONY_PEAKS)) && !Ch3Mobs.peakBiome(biomes.getHolderOrThrow(Biomes.MEADOW)), "fledgling biomes", f);
        check(Ch3Mobs.plateauBiome(biomes.getHolderOrThrow(Biomes.WINDSWEPT_HILLS)) && !Ch3Mobs.plateauBiome(biomes.getHolderOrThrow(Biomes.DESERT)), "gleaner biomes", f);
        // 焼けた見張り塔の湧き台: 入口の間 峰の雛 2・中の間 帯電毛玉 3・頂の間 焼けた羽守 1 (雷翼の背の敵の代用はもうない)
        SiteSpawner.Def def = SiteSpawner.def(ScorchedWatch.SITE);
        if (def == null) {
            f.add("no scorched watch pads");
        } else {
            int fl = 0;
            int pf = 0;
            int sw = 0;
            int other = 0;
            for (SiteSpawner.Pad pad : def.pads()) {
                for (SiteSpawner.Spawn s : pad.spawns()) {
                    if (s.entity().equals(Ch3Mobs.PEAK_FLEDGLING) && pad.room().equals(ScorchedWatch.ROOM_ENTRY)) {
                        fl += s.count();
                    } else if (s.entity().equals(Ch3Mobs.STATIC_PUFF) && pad.room().equals(ScorchedWatch.ROOM_MIDDLE)) {
                        pf += s.count();
                    } else if (s.entity().equals(Ch3Mobs.SCORCHED_WARDEN) && pad.room().equals(ScorchedWatch.ROOM_TOP)) {
                        sw += s.count();
                    } else {
                        other++;
                    }
                }
            }
            check(fl == 2 && pf == 3 && sw == 1 && other == 0, "watch pads: fledglings " + fl + ", puffs " + pf + ", wardens " + sw + ", others " + other, f);
        }
        List<String> missing = new ArrayList<>();
        for (String id : List.of(Ch3Mobs.SCORCHED_FEATHER, Ch3Mobs.STATIC_COTTON, Ch3Mobs.COPPER_WIRE, Ch3Mobs.THUNDER_WARD_THREAD)) {
            if (Ch3Mobs.item(id) == null) {
                missing.add(id);
            }
        }
        check(missing.isEmpty(), "drop materials not registered: " + missing, f);
    }

    // ---------------------------------------------------------------- 帯電毛玉
    private static void puff(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        StaticPuff p = spawn(level, Ch3Mobs.STATIC_PUFF_TYPE.get(), SX + 0.5, FLOOR + 1.0, SZ + 0.5, 0f);
        try {
            // 固まった二人 (1 の間) と、離れた一人 (8)
            FakePlayer a = fake(level, "p_a", SX + 2.5, SZ + 0.5);
            FakePlayer b = fake(level, "p_b", SX + 3.5, SZ + 0.5);
            FakePlayer far = fake(level, "p_far", SX + 0.5, SZ + 9.5);
            ps.addAll(List.of(a, b, far));
            p.setTarget(a);
            int first = -1;
            boolean bristled = false;
            for (int i = 1; i <= 120 && first < 0; i++) {
                p.setPos(SX + 0.5, FLOOR + 1.0, SZ + 0.5);
                tick(level, p, 1);
                bristled |= p.getCharge() > 0.5f;
                if (lost(a) > 0 || lost(b) > 0) {
                    first = i;
                }
            }
            check(first >= StaticPuff.CHARGE_TICKS - 2 && first >= Telegraph.MIN_WARN_TICKS, "the puff discharged after only " + first + " ticks (charge 4 s)", f);
            check(bristled, "the puff never showed its charge", f);
            check(Math.abs(lost(a) - StaticPuff.DISCHARGE_DAMAGE) < 0.6f && lost(b) > 0 && lost(b) < lost(a), "the discharge did not chain across the pair (" + lost(a) + ", " + lost(b) + ")", f);
            check(lost(far) == 0, "the discharge reached the far player", f);
            check(p.getCharge() < 0.01f, "the puff kept its charge after discharging", f);
            notes.add("puff " + first + " ticks, " + Math.round(lost(a) * 10) / 10f + "/" + Math.round(lost(b) * 10) / 10f);
            drop(ps);
            // 離れて立つ二人 (5 の間): 一人で済む
            FakePlayer c = fake(level, "p_c", SX + 2.5, SZ + 0.5);
            FakePlayer d = fake(level, "p_d", SX + 2.5, SZ + 5.5);
            ps.addAll(List.of(c, d));
            p.setTarget(c);
            p.setCharge(0.99f);
            tick(level, p, 80);
            check(lost(c) > 0 && lost(d) == 0, "a spread-out pair was both hit (" + lost(c) + ", " + lost(d) + ")", f);
            List<StaticPuff.Hit> plan = StaticPuff.plan(new net.minecraft.world.phys.Vec3(SX, FLOOR + 1, SZ), List.of(c, d), e -> true);
            check(plan.size() == 1, "the plan for a spread pair has " + plan.size() + " hits", f);
        } finally {
            drop(ps);
            p.discard();
        }
    }

    // ---------------------------------------------------------------- 峰の雛
    private static void fledgling(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        PeakFledgling b = spawn(level, Ch3Mobs.PEAK_FLEDGLING_TYPE.get(), SX + 0.5, FLOOR, SZ + 0.5, 0f);
        try {
            FakePlayer on = fake(level, "f_on", SX + 0.5, SZ + 6.5);
            FakePlayer off = fake(level, "f_off", SX + 3.5, SZ + 4.5);
            ps.addAll(List.of(on, off));
            tick(level, b, 2);
            b.setTarget(on);
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            b.startRise(on);
            int first = -1;
            int lineAt = -1;
            for (int i = 1; i <= 60; i++) {
                tick(level, b, 1);
                if (lineAt < 0 && !shapes.isEmpty()) {
                    lineAt = i;
                }
                if (first < 0 && lost(on) > 0) {
                    first = i;
                }
            }
            Telegraph.record(null);
            check(lineAt >= PeakFledgling.RISE_TICKS - 1, "the line came before the fledgling rose (" + lineAt + ")", f);
            check(first > 0 && first - lineAt + 1 >= Telegraph.MIN_WARN_TICKS, "the dive hit " + (first - lineAt + 1) + " ticks after the line (needs 0.8 s)", f);
            check(Math.abs(lost(on) - PeakFledgling.DIVE_DAMAGE) < 0.6f && lost(off) == 0, "the dive hit on " + lost(on) + " / off " + lost(off), f);
            check(!shapes.isEmpty() && shapes.get(0).contains(on.getX(), on.getZ()) && !shapes.get(0).contains(off.getX(), off.getZ()), "the line shape does not match the hits", f);
            check(b.state() != PeakFledgling.STAGGER, "the fledgling staggered after a hit", f);
            notes.add("fledgling line " + lineAt + ", hit " + first);
            drop(ps);
            // 外す: 線が出たあと、狙われた人が線の外へ出る -> よろめき、傷 1.5 倍
            b.discard();
            b = spawn(level, Ch3Mobs.PEAK_FLEDGLING_TYPE.get(), SX + 0.5, FLOOR, SZ + 0.5, 0f);
            FakePlayer dodge = fake(level, "f_dodge", SX + 0.5, SZ + 6.5);
            FakePlayer hitter = fake(level, "f_hitter", SX + 10.5, SZ - 8.5);
            ps.addAll(List.of(dodge, hitter));
            tick(level, b, 2);
            b.setTarget(dodge);
            b.startRise(dodge);
            tick(level, b, PeakFledgling.RISE_TICKS + 1);
            dodge.setPos(SX + 4.5, FLOOR, SZ + 6.5);
            boolean staggered = false;
            for (int i = 0; i < 40 && !staggered; i++) {
                tick(level, b, 1);
                staggered = b.isStaggered();
            }
            check(lost(dodge) == 0, "the dodged dive still hit", f);
            check(staggered, "the fledgling did not stagger after missing", f);
            float stag = hurtLoss(b, level, hitter, 4f);
            check(Math.abs(stag - 6f) < 0.3f, "a staggered fledgling took " + stag + " from 4 (want 6)", f);
        } finally {
            drop(ps);
            b.discard();
        }
    }

    // ---------------------------------------------------------------- 雷拾い
    private static void gleaner(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        clearItems(level);
        LightningGleaner g = spawn(level, Ch3Mobs.LIGHTNING_GLEANER_TYPE.get(), SX + 0.5, FLOOR, SZ + 0.5, 0f);
        try {
            FakePlayer p = fake(level, "g_p", SX + 12.5, SZ + 0.5);
            ps.add(p);
            check(!g.canAttack(p), "the gleaner may attack", f);
            tick(level, g, 5);
            check(!g.isFleeing() && g.hasPack(), "the gleaner fled from a far player", f);
            p.setPos(SX + 3.5, FLOOR, SZ + 0.5);
            tick(level, g, 3);
            check(g.isFleeing() && !g.hasPack(), "the gleaner did not flee or keep its pack", f);
            Item wire = Ch3Mobs.item(Ch3Mobs.COPPER_WIRE);
            int wires = count(level, wire);
            check(wires == 1, "the dropped pack held " + wires + " copper wire (want 1)", f);
            p.setPos(SX + 20.5, FLOOR, SZ + 0.5);
            tick(level, g, 5);
            p.setPos(SX + 3.5, FLOOR, SZ + 0.5);
            tick(level, g, 5);
            check(count(level, wire) == 1, "the gleaner dropped its pack twice", f);
            notes.add("gleaner dropped " + wires + " wire");
        } finally {
            drop(ps);
            g.discard();
            clearItems(level);
        }
    }

    // ---------------------------------------------------------------- 焼けた羽守
    private static ScorchedWarden wardenAt(ServerLevel level, double x, double z) {
        ScorchedWarden w = spawn(level, Ch3Mobs.SCORCHED_WARDEN_TYPE.get(), x, FLOOR, z, 0f);
        return w;
    }

    private static void warden(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        ScorchedWarden w = wardenAt(level, SX + 0.5, SZ + 0.5);
        try {
            for (int i = 0; i < 3; i++) {
                ps.add(fake(level, "w_party" + i, SX + 3 + i, SZ + 12));
            }
            tick(level, w, 2);
            check(Math.abs(w.getMaxHealth() - 190 * 2.2f) < 1f, "warden health for 3 players " + w.getMaxHealth(), f);
            drop(ps);
            w.discard();
            w = wardenAt(level, SX + 0.5, SZ + 0.5);
            FakePlayer solo = fake(level, "w_solo", SX + 0.5, SZ + 14);
            ps.add(solo);
            tick(level, w, 2);
            check(w.getMaxHealth() == 190f, "warden health for one player " + w.getMaxHealth(), f);
            check(w.diveAllowed(), "the scorched warden waits for a storm beat to dive", f);
            // ふだんは硬く、柱の雷のあとはよく通る
            // 鎧を通す傷で比べる (羽守の鎧 9 を倍率から外す)
            float guard = magicLoss(w, level, 10f);
            w.struck();
            float open = magicLoss(w, level, 10f);
            check(Math.abs(guard - 10 * ScorchedWarden.GUARD_TAKEN) < 0.6f && Math.abs(open - 10 * ScorchedWarden.EXPOSED_TAKEN) < 0.6f && w.isExposed(),
                    "warden damage guard " + guard + " / exposed " + open, f);
            tick(level, w, ScorchedWarden.EXPOSED_TICKS + 2);
            check(!w.isExposed(), "the exposure did not wear off", f);
            drop(ps);
            // 雷の輪: 足もと。中 1 人、外 1 人。予告 1 秒以上
            FakePlayer in = fake(level, "w_in", SX + 2.0, SZ + 0.5);
            FakePlayer out = fake(level, "w_out", SX + 5.5, SZ + 0.5);
            ps.addAll(List.of(in, out));
            // 露出を待つあいだに一人目へ寄る (跳ぶ) ことがあるので、輪の前に真ん中へ戻す
            w.moveTo(SX + 0.5, FLOOR, SZ + 0.5, 0f, 0f);
            w.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            w.getNavigation().stop();
            net.minecraft.world.phys.Vec3 ringAt = w.position();
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            w.startRing();
            int first = -1;
            for (int i = 1; i <= 40; i++) {
                tick(level, w, 1);
                if (first < 0 && lost(in) > 0) {
                    first = i;
                }
            }
            Telegraph.record(null);
            check(first >= 20 && first >= ScorchedWarden.RING_WARN, "the ring hit after " + first + " ticks (needs 1 s)", f);
            check(Math.abs(lost(in) - ScorchedWarden.RING_DAMAGE) < 0.6f && lost(out) == 0, "ring damage in " + lost(in) + " / out " + lost(out), f);
            check(!shapes.isEmpty() && shapes.get(0).contains(in.getX(), in.getZ()) && !shapes.get(0).contains(out.getX(), out.getZ()), "the ring shape does not match (" + shapes.size() + " shapes, ring at " + ringAt + ", warden " + w.position() + ")", f);
            check(w.skill() == ScorchedWarden.NONE, "the ring never ended", f);
            notes.add("warden ring " + first);
            drop(ps);
            // 外套の跳躍: 落ちる先 (狙った人の足もと) に輪。予告 1 秒以上、8
            FakePlayer target = fake(level, "w_target", SX + 0.5, SZ + 7.5);
            FakePlayer aside = fake(level, "w_aside", SX + 5.5, SZ + 7.5);
            ps.addAll(List.of(target, aside));
            w.startLeap(target);
            first = -1;
            for (int i = 1; i <= 40; i++) {
                tick(level, w, 1);
                if (first < 0 && lost(target) > 0) {
                    first = i;
                }
            }
            check(first >= 20 && first >= ScorchedWarden.LEAP_TICKS, "the leap hit after " + first + " ticks (needs 1 s)", f);
            check(Math.abs(lost(target) - ScorchedWarden.LEAP_DAMAGE) < 0.6f && lost(aside) == 0, "leap damage target " + lost(target) + " / aside " + lost(aside), f);
            check(w.position().distanceTo(new net.minecraft.world.phys.Vec3(SX + 0.5, FLOOR, SZ + 7.5)) < 1.5, "the warden did not land on the ring (" + w.position() + ")", f);
            notes.add("warden leap " + first);
        } finally {
            drop(ps);
            w.discard();
        }
    }

    // ---------------------------------------------------------------- 落とし物
    private static void drops(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        try {
            FakePlayer killer = fake(level, "d_killer", SX + 12, SZ);
            ps.add(killer);
            Item cotton = Ch3Mobs.item(Ch3Mobs.STATIC_COTTON);
            Item feather = Ch3Mobs.item(Ch3Mobs.SCORCHED_FEATHER);
            Item wire = Ch3Mobs.item(Ch3Mobs.COPPER_WIRE);
            Item thread = Ch3Mobs.item(Ch3Mobs.THUNDER_WARD_THREAD);
            clearItems(level);
            for (int i = 0; i < 100; i++) {
                spawn(level, Ch3Mobs.STATIC_PUFF_TYPE.get(), SX + 0.5, FLOOR + 1, SZ + 0.5, 0f).hurt(level.damageSources().playerAttack(killer), 1000f);
            }
            int cs = count(level, cotton);
            int pf = count(level, feather);
            check(cs >= 32 && cs <= 68, "puff cotton " + cs + "/100 (expected about 50)", f);
            check(pf >= 2 && pf <= 22, "puff feathers " + pf + "/100 (expected about 10)", f);
            clearItems(level);
            for (int i = 0; i < 100; i++) {
                spawn(level, Ch3Mobs.PEAK_FLEDGLING_TYPE.get(), SX + 0.5, FLOOR, SZ + 0.5, 0f).hurt(level.damageSources().playerAttack(killer), 1000f);
            }
            int ff = count(level, feather);
            check(ff >= 24 && ff <= 56, "fledgling feathers " + ff + "/100 (expected about 40)", f);
            clearItems(level);
            for (int i = 0; i < 3; i++) {
                spawn(level, Ch3Mobs.SCORCHED_WARDEN_TYPE.get(), SX + 0.5, FLOOR, SZ + 0.5, 0f).hurt(level.damageSources().playerAttack(killer), 100000f);
            }
            int ws = count(level, wire);
            int ts = count(level, thread);
            check(ws >= 6 && ws <= 9, "warden wire " + ws + " from 3 (want 2-3 each)", f);
            notes.add("drops: puff cotton " + cs + " feather " + pf + ", fledgling feather " + ff + ", warden wire " + ws + " thread " + ts);
            clearItems(level);
        } finally {
            drop(ps);
            level.getEntitiesOfClass(Mob.class, area(), m -> m instanceof GuardMob).forEach(Mob::discard);
        }
    }
}
