package jp.revati.skyabyss.entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.loot.TitanLoot;
import jp.revati.skyabyss.registry.Ch1BackEntities;
import jp.revati.skyabyss.titan.dungeon.BackAmbientMobs;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

// 岩鯨の背の新しい敵 3 体 (骨の砲手・骨鳥・老番兵。docs/design/21 の 3.3) の自己テスト。SKYABYSS_SELFTEST_ONLY="ch1 back mobs"。
//  A. 骨の砲手: 印の円 (半径 2) = 当たり (内側は傷・外側は無傷・予告 24 tick ≥ 16)、人数で同時に落とす数が増える (最大 3)、正面は半分
//  B. 骨鳥: 印の線 = 当たり、予告 16 tick 以上、降りたあとのよろめき、1 体ずつ降りる
//  C. 老番兵: 突進の線 = 当たり、足踏みの円 = 当たり、骨笛 (傷のない円・人数で射手が増える)、一度に呼べる数の上限
//  D. 広間の波 (第一の広間): 配置の数・砲手が奥の壁ぎわ・老番兵の体力が人数で増える
//  E. 落とす物・経験値・スポーンエッグ・骨鳥の場所の数・床の 4 割の規則
public final class Ch1BackMobsSelfTest {
    private static final double SX = 110.5;
    private static final double SZ = 110.5;
    private static final int FLOOR = 200;
    private static final float HEALTH = 1000f;

    private Ch1BackMobsSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ch1 back mobs (load)", Ch1BackMobsSelfTest::load),
                SelfTestRunner.Step.once("ch1 back mobs", 30, Ch1BackMobsSelfTest::run));
    }

    private static void force(ServerLevel level, boolean on) {
        for (int cx = 5; cx <= 9; cx++) {
            for (int cz = 5; cz <= 9; cz++) {
                level.setChunkForced(cx, cz, on);
                if (on) {
                    level.getChunk(cx, cz);
                }
            }
        }
        // 第一の広間 E1・番兵の詰所 CK・西の竪穴 WB (背の座標のまま、オーバーワールドに足場を作る)
        for (TitanBackLayout.Box box : List.of(TitanBackLayout.E1, TitanBackLayout.CK, TitanBackLayout.WB)) {
            for (int cx = (box.x1() - 3) >> 4; cx <= (box.x2() + 3) >> 4; cx++) {
                for (int cz = (box.z1() - 3) >> 4; cz <= (box.z2() + 3) >> 4; cz++) {
                    level.setChunkForced(cx, cz, on);
                    if (on) {
                        level.getChunk(cx, cz);
                    }
                }
            }
        }
    }

    static void load(MinecraftServer server) {
        force(server.overworld(), true);
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            platform(level);
            mortar(level, f, notes);
            gull(level, f, notes);
            elder(level, f, notes);
            waves(level, f, notes);
            misc(level, f, notes);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST ch1 back mobs crashed", e);
            f.add("crashed: " + e);
        } finally {
            Telegraph.probe(null);
            Telegraph.record(null);
            server.setDifficulty(before, true);
            force(level, false);
        }
        notes.forEach(n -> SkyAbyss.LOGGER.info("SELFTEST ch1 back mobs note: {}", n));
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ch1 back mobs PASSED ({})", String.join("; ", notes));
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ch1 back mobs FAILED: {}", m));
        }
    }

    private static void check(boolean ok, String message, List<String> f) {
        if (!ok) {
            f.add(message);
        }
    }

    // ---------------------------------------------------------------- 部品

    private static void flat(ServerLevel level, int x1, int z1, int x2, int z2, int floorY, int height) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                level.setBlock(new BlockPos(x, floorY, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = floorY + 1; y <= floorY + height; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    private static void platform(ServerLevel level) {
        flat(level, 90, 90, 130, 130, FLOOR - 1, 12);
        TitanBackLayout.Box e1 = TitanBackLayout.E1;
        flat(level, e1.x1() - 2, e1.z1() - 2, e1.x2() + 2, e1.z2() + 2, e1.floorY(), 8);
        TitanBackLayout.Box ck = TitanBackLayout.CK;
        flat(level, ck.x1() - 2, ck.z1() - 2, ck.x2() + 2, ck.z2() + 2, ck.floorY(), 8);
        TitanBackLayout.Box wb = TitanBackLayout.WB;
        for (int x = wb.x1(); x <= wb.x2(); x++) {
            for (int z = wb.z1(); z <= wb.z2(); z++) {
                for (int y = wb.floorY() + 1; y <= wb.floorY() + 9; y++) {
                    level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
    }

    private static FakePlayer fake(ServerLevel level, String tag, double x, double y, double z) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("ch1back " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "[cb_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            Field spawn = net.minecraft.server.level.ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        p.setHealth(HEALTH);
        p.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(p);
        return p;
    }

    private static void drop(List<? extends Entity> list) {
        for (Entity e : list) {
            e.discard();
        }
        list.clear();
    }

    private static float lost(FakePlayer p) {
        return HEALTH - p.getHealth();
    }

    private static void tick(ServerLevel level, Mob mob, int ticks) {
        for (int i = 0; i < ticks; i++) {
            mob.tickCount++;
            mob.tick();
            Telegraph.tickNow(level);
        }
    }

    // mob を ticks 回進め、player が最初に傷を受けた tick (1 始まり、受けなければ -1) を返す
    private static int firstHurt(ServerLevel level, Mob mob, FakePlayer player, int ticks) {
        int first = -1;
        for (int i = 1; i <= ticks; i++) {
            tick(level, mob, 1);
            if (first < 0 && player.getHealth() < HEALTH) {
                first = i;
            }
        }
        return first;
    }

    private static <T extends Mob> T spawn(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, double x, double y, double z, float yaw) {
        T mob = type.create(level);
        mob.moveTo(x, y, z, yaw, 0);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
        return mob;
    }

    // ---------------------------------------------------------------- A. 骨の砲手

    private static void mortar(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        // 印の場所 spot の円に、後から入る (傷)・外へ出る (無傷)。印は始めの場所に固定されている
        for (int variant = 0; variant < 3; variant++) {
            BoneMortar m = spawn(level, Ch1BackEntities.BONE_MORTAR.get(), SX, FLOOR, SZ, 0f);
            m.holdAttacks();
            try {
                Vec3 spot = new Vec3(SX, FLOOR, SZ + 10);
                FakePlayer p = fake(level, "mortar" + variant, spot.x, FLOOR, spot.z);
                ps.add(p);
                m.setTarget(p);
                List<TelegraphShape> shapes = new ArrayList<>();
                Telegraph.record(shapes);
                Telegraph.resetStats();
                m.forceVolley();
                tick(level, m, 2);
                check(m.isFiring(), "mortar did not start its volley", f);
                check(Telegraph.damagingCreated() == 1, "a single target got " + Telegraph.damagingCreated() + " shells (expected 1)", f);
                List<Integer> warns = Telegraph.activeWarns(level);
                check(!warns.isEmpty() && warns.stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS && w == BoneMortar.WARN),
                        "mortar warn is " + warns + " (expected " + BoneMortar.WARN + ")", f);
                check(BoneMortar.WARN - BoneMortar.FLIGHT >= 8, "the mortar fires too early for the barrel to rise", f);
                TelegraphShape shape = shapes.get(0);
                check(shape.contains(spot.x, spot.z) && shape.contains(spot.x + 1.9, spot.z) && !shape.contains(spot.x + 2.1, spot.z)
                        && !shape.contains(spot.x, spot.z - 2.1), "the mortar circle is not radius " + BoneMortar.RADIUS + " around the target", f);
                Telegraph.record(null);
                // 0: 動かない (中心) 1: 縁の内側 1.8 へ動く 2: 外 2.6 へ動く
                double move = variant == 0 ? 0 : variant == 1 ? 1.8 : 2.6;
                p.moveTo(spot.x + move, FLOOR, spot.z, 0, 0);
                int first = firstHurt(level, m, p, 40);
                if (variant < 2) {
                    check(first >= Telegraph.MIN_WARN_TICKS && first <= BoneMortar.WARN + 3, "mortar variant " + variant + " hit at tick " + first, f);
                    check(lost(p) >= BoneMortar.DAMAGE * 0.7f && lost(p) <= BoneMortar.DAMAGE * 1.1f, "mortar damage " + lost(p), f);
                    notes.add("mortar warns " + first + " ticks (" + first * 50 + " ms), hits " + Math.round(lost(p)));
                } else {
                    check(lost(p) == 0, "a player outside the mortar circle lost " + lost(p), f);
                }
            } finally {
                Telegraph.record(null);
                drop(ps);
                m.discard();
            }
        }
        // 人数で増える: 近くに 4 人いれば同時に 3 つ (最大)
        BoneMortar m = spawn(level, Ch1BackEntities.BONE_MORTAR.get(), SX, FLOOR, SZ, 0f);
        m.holdAttacks();
        try {
            for (int i = 0; i < 4; i++) {
                ps.add(fake(level, "mp" + i, SX - 6 + i * 4, FLOOR, SZ + 9));
            }
            m.setTarget(ps.get(0));
            Telegraph.resetStats();
            m.forceVolley();
            tick(level, m, 2);
            check(Telegraph.damagingCreated() == BoneMortar.MAX_SHELLS, "4 players got " + Telegraph.damagingCreated() + " shells (expected " + BoneMortar.MAX_SHELLS + ")", f);
            notes.add("mortar shells with 4 players: " + Telegraph.damagingCreated());
        } finally {
            drop(ps);
            m.discard();
        }
        // 正面の盾: 正面 (前) からの傷は、背中からの傷より小さい
        m = spawn(level, Ch1BackEntities.BONE_MORTAR.get(), SX + 20, FLOOR, SZ, 0f);
        try {
            check(shieldRatio(level, m) < 0.75f, "the mortar shield does not soften frontal hits", f);
        } finally {
            m.discard();
        }
    }

    // 前からと後ろからの同じ一撃の傷の比 (前 / 後ろ)。向きは yaw 0 = +z
    static float shieldRatio(ServerLevel level, Mob mob) {
        ArmorStand front = new ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
        front.moveTo(mob.getX(), mob.getY(), mob.getZ() + 3);
        ArmorStand back = new ArmorStand(net.minecraft.world.entity.EntityType.ARMOR_STAND, level);
        back.moveTo(mob.getX(), mob.getY(), mob.getZ() - 3);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
        mob.setHealth(500);
        mob.invulnerableTime = 0;
        mob.hurt(level.damageSources().mobAttack(front), 10f);
        float frontLost = 500 - mob.getHealth();
        mob.setHealth(500);
        mob.invulnerableTime = 0;
        mob.hurt(level.damageSources().mobAttack(back), 10f);
        float backLost = 500 - mob.getHealth();
        return backLost <= 0 ? 1f : frontLost / backLost;
    }

    // ---------------------------------------------------------------- B. 骨鳥

    private static void gull(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        BoneGull g = spawn(level, Ch1BackEntities.BONE_GULL.get(), SX, FLOOR + 4, SZ, 0f);
        try {
            FakePlayer inLine = fake(level, "g_in", SX, FLOOR, SZ + 9);
            FakePlayer side = fake(level, "g_side", SX + 3, FLOOR, SZ + 6);
            ps.addAll(List.of(inLine, side));
            g.setTarget(inLine);
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            Telegraph.resetStats();
            g.forceDive();
            tick(level, g, 1);
            Telegraph.record(null);
            check(g.isDiving(), "the gull did not begin its dive", f);
            check(Telegraph.damagingCreated() == 1 && shapes.size() == 1, "the gull made " + Telegraph.damagingCreated() + " damaging marks", f);
            check(Telegraph.activeWarns(level).stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS) && BoneGull.CRY_TICKS >= Telegraph.MIN_WARN_TICKS,
                    "the gull cry is shorter than 16 ticks", f);
            double length = BoneGull.lineLength(9);
            if (!shapes.isEmpty()) {
                TelegraphShape line = shapes.get(0);
                check(line.contains(SX, SZ + length - 0.5) && !line.contains(SX, SZ + length + 0.5) && line.contains(SX + 0.8, SZ + 6)
                        && !line.contains(SX + 1.0, SZ + 6), "the dive line is not the marked line (length " + length + ")", f);
            }
            int first = firstHurt(level, g, inLine, 40);
            check(first >= BoneGull.CRY_TICKS && first <= BoneGull.WARN + 3, "the gull hit at tick " + first + " (cry " + BoneGull.CRY_TICKS + ")", f);
            check(lost(inLine) >= BoneGull.DAMAGE * 0.7f && lost(inLine) <= BoneGull.DAMAGE * 1.1f, "the gull dive damage " + lost(inLine), f);
            check(lost(side) == 0, "a player beside the dive line was hurt", f);
            check(g.isRecovering() || !g.isDiving(), "the gull did not stumble after the dive", f);
            check(Math.abs(g.getZ() - (SZ + length)) < 2.0 && g.getY() < FLOOR + 2.5, "the gull ended at (" + g.getX() + ", " + g.getY() + ", " + g.getZ() + ") not at the line's end", f);
            notes.add("gull cry " + BoneGull.CRY_TICKS + " + dive " + BoneGull.DIVE_TICKS + " ticks, hit at " + first + ", damage " + Math.round(lost(inLine)));
        } finally {
            Telegraph.record(null);
            drop(ps);
            g.discard();
        }
        // 1 体ずつ降りる
        BoneGull a = spawn(level, Ch1BackEntities.BONE_GULL.get(), SX, FLOOR + 4, SZ, 0f);
        BoneGull b = spawn(level, Ch1BackEntities.BONE_GULL.get(), SX + 1.5, FLOOR + 4, SZ, 0f);
        try {
            FakePlayer p = fake(level, "g_two", SX, FLOOR, SZ + 9);
            ps.add(p);
            a.setTarget(p);
            b.setTarget(p);
            a.forceDive();
            b.forceDive();
            for (int i = 0; i < 6; i++) {
                tick(level, a, 1);
                tick(level, b, 1);
            }
            int diving = (a.isDiving() ? 1 : 0) + (b.isDiving() ? 1 : 0);
            check(diving == 1, diving + " gulls were diving at once (expected 1)", f);
            notes.add("gulls dive one at a time");
        } finally {
            drop(ps);
            a.discard();
            b.discard();
        }
        // 場所に出す数 (人数で増える) と置き場所
        check(BackAmbientMobs.gullCount(1) == 2 && BackAmbientMobs.gullCount(3) == 3 && BackAmbientMobs.gullCount(6) == 4, "gull counts per party size are off", f);
        UUID group = UUID.randomUUID();
        for (TitanBackLayout.Box zone : BackAmbientMobs.GULL_ZONES) {
            // 場所の空気を作る (オーバーワールドの仮の置き場。床 + 4〜9 を空に)
            for (int x = zone.x1(); x <= zone.x2(); x++) {
                for (int z = zone.z1(); z <= zone.z2(); z++) {
                    for (int y = zone.floorY() + 1; y <= zone.floorY() + 9; y++) {
                        level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
                    }
                }
            }
            List<BoneGull> gulls = BackAmbientMobs.spawn(level, zone, group, 3);
            try {
                check(gulls.size() == 3, "only " + gulls.size() + " gulls were placed in the zone", f);
                for (BoneGull gull : gulls) {
                    check(zone.contains(gull.blockPosition()) && gull.blockPosition().getY() >= zone.floorY() + 5 && gull.groupId() != null && group.equals(gull.groupId())
                            && gull.getTags().contains(TitanBackTrials.MOB_TAG), "a gull was placed badly: " + gull.blockPosition(), f);
                    check(!gull.shouldBeSaved(), "an ambient gull would be saved to disk", f);
                }
            } finally {
                gulls.forEach(Entity::discard);
            }
        }
        notes.add("gull zones: WB, WC");
    }

    // ---------------------------------------------------------------- C. 老番兵

    private static ElderSentinel spawnElder(ServerLevel level, float yaw) {
        ElderSentinel e = spawn(level, Ch1BackEntities.ELDER_SENTINEL.get(), SX, FLOOR, SZ, yaw);
        e.holdAttacks();
        return e;
    }

    private static void elder(ServerLevel level, List<String> f, List<String> notes) {
        List<FakePlayer> ps = new ArrayList<>();
        // 盾の突進: 前 7 に狙われた人 (線の中)、前 10.8 は線の先の外、横 3 は外
        ElderSentinel e = spawnElder(level, 0f);
        try {
            FakePlayer a = fake(level, "r_a", SX, FLOOR, SZ + 7);
            FakePlayer beyond = fake(level, "r_b", SX, FLOOR, SZ + 11.2);
            FakePlayer side = fake(level, "r_s", SX + 3, FLOOR, SZ + 5);
            ps.addAll(List.of(a, beyond, side));
            e.setTarget(a);
            List<TelegraphShape> shapes = new ArrayList<>();
            Telegraph.record(shapes);
            Telegraph.resetStats();
            e.forceAction("RUSH");
            tick(level, e, 1);
            Telegraph.record(null);
            check(Telegraph.damagingCreated() == 1 && Telegraph.activeWarns(level).stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS && w == ElderSentinel.RUSH_WARN),
                    "the elder rush mark is wrong " + Telegraph.activeWarns(level), f);
            if (!shapes.isEmpty()) {
                TelegraphShape line = shapes.get(0);
                check(line.contains(SX, SZ + 9.5) && !line.contains(SX, SZ + 10.5) && line.contains(SX + 1.1, SZ + 5) && !line.contains(SX + 1.3, SZ + 5),
                        "the rush line is not the marked line", f);
            }
            int first = firstHurt(level, e, a, 40);
            check(first >= Telegraph.MIN_WARN_TICKS && first <= ElderSentinel.RUSH_WARN + 3, "the elder rush hit at tick " + first, f);
            check(lost(a) >= ElderSentinel.RUSH_DAMAGE * 0.7f && lost(a) <= ElderSentinel.RUSH_DAMAGE * 1.1f, "the elder rush damage " + lost(a), f);
            check(lost(beyond) == 0 && lost(side) == 0, "the elder rush hurt a player outside the line", f);
            check(Math.abs(e.getZ() - (SZ + 10)) < 1.5, "the elder ended the rush at z " + (e.getZ() - SZ), f);
            notes.add("elder rush warns " + first + " ticks, damage " + Math.round(lost(a)));
        } finally {
            Telegraph.record(null);
            drop(ps);
            e.discard();
        }
        // 足踏み: 半径 3.5。3.0 は中、4.2 は外
        e = spawnElder(level, 0f);
        try {
            FakePlayer in = fake(level, "s_i", SX + 3.0, FLOOR, SZ);
            FakePlayer out = fake(level, "s_o", SX + 4.2, FLOOR, SZ);
            ps.addAll(List.of(in, out));
            e.setTarget(in);
            e.forceAction("STOMP");
            int first = firstHurt(level, e, in, 40);
            check(first >= Telegraph.MIN_WARN_TICKS && first <= ElderSentinel.STOMP_WARN + 3, "the elder stomp hit at tick " + first, f);
            check(lost(in) >= ElderSentinel.STOMP_DAMAGE * 0.7f && lost(in) <= ElderSentinel.STOMP_DAMAGE * 1.1f && lost(out) == 0,
                    "the elder stomp: inside lost " + lost(in) + ", outside lost " + lost(out), f);
            notes.add("elder stomp warns " + first + " ticks, damage " + Math.round(lost(in)));
        } finally {
            drop(ps);
            e.discard();
        }
        // 骨笛: 傷のない円が人数に応じて (1 人 2 つ、3 人 4 つ)、時間が来ると射手が出る。普通の殴りは持たない
        check(ElderSentinel.summonCount(1) == 2 && ElderSentinel.summonCount(3) == 4 && ElderSentinel.summonCount(6) == ElderSentinel.MAX_SUMMONS, "the flute summon counts are off", f);
        for (int players : new int[] {1, 3}) {
            e = spawnElder(level, 0f);
            try {
                for (int i = 0; i < players; i++) {
                    ps.add(fake(level, "f" + players + "_" + i, SX - 4 + i * 4, FLOOR, SZ + 8));
                }
                e.setTarget(ps.get(0));
                Telegraph.resetStats();
                e.forceAction("FLUTE");
                tick(level, e, 2);
                List<Integer> warns = Telegraph.activeWarns(level);
                int expected = ElderSentinel.summonCount(players);
                check(Telegraph.damagingCreated() == 0, "the flute marks hurt (" + Telegraph.damagingCreated() + " damaging)", f);
                check(warns.size() == expected && warns.stream().allMatch(w -> w >= Telegraph.MIN_WARN_TICKS), "the flute made " + warns + " circles (expected " + expected + ")", f);
                tick(level, e, ElderSentinel.FLUTE_WARN + 2);
                AABB around = e.getBoundingBox().inflate(30, 10, 30);
                List<RibArcher> archers = level.getEntitiesOfClass(RibArcher.class, around);
                check(archers.size() == expected, players + " players: " + archers.size() + " archers came (expected " + expected + ")", f);
                check(archers.stream().allMatch(a -> a.getTags().contains(TitanBackTrials.MOB_TAG)), "a summoned archer is not a trial mob", f);
                notes.add("elder flute with " + players + " player(s): " + archers.size() + " archers");
                archers.forEach(Entity::discard);
            } finally {
                drop(ps);
                e.discard();
            }
        }
        check(!spawnElderNoMelee(level), "the elder has a plain melee attack", f);
        e = spawnElder(level, 0f);
        try {
            check(shieldRatio(level, e) < 0.75f, "the elder shield does not soften frontal hits", f);
        } finally {
            e.discard();
        }
    }

    private static boolean spawnElderNoMelee(ServerLevel level) {
        ElderSentinel e = spawnElder(level, 0f);
        try {
            FakePlayer p = fake(level, "melee", SX, FLOOR, SZ + 1);
            try {
                return e.doHurtTarget(p);
            } finally {
                p.discard();
            }
        } finally {
            e.discard();
        }
    }

    // ---------------------------------------------------------------- D. 第一の広間の波 (TitanBackTrials.spawnWave を、広間の座標のまま作った足場の上で)

    private static void waves(ServerLevel level, List<String> f, List<String> notes) {
        try {
            Class<?> trials = TitanBackTrials.class;
            Field hallsField = trials.getDeclaredField("HALLS");
            hallsField.setAccessible(true);
            Object hall = ((List<?>) hallsField.get(null)).get(0);
            Class<?> hallClass = hall.getClass();
            Method waves = hallClass.getDeclaredMethod("waves");
            waves.setAccessible(true);
            int waveCount = ((List<?>) waves.invoke(hall)).size();
            check(waveCount == 2, "the first hall has " + waveCount + " waves (expected 2: melee, archers + mortar; the elder moved to the guard post)", f);
            Class<?> stateClass = Class.forName(trials.getName() + "$HallState");
            Constructor<?> ctor = stateClass.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            Field waveField = stateClass.getDeclaredField("wave");
            waveField.setAccessible(true);
            Field playersField = stateClass.getDeclaredField("players");
            playersField.setAccessible(true);
            Method spawnWave = trials.getDeclaredMethod("spawnWave", ServerLevel.class, hallClass, stateClass);
            spawnWave.setAccessible(true);
            AABB box = TitanBackLayout.E1.aabb().inflate(2);
            Object ckHall = ((List<?>) hallsField.get(null)).get(2);
            for (int players : new int[] {1, 3, 4}) {
                for (int wave = 2; wave <= 3; wave++) {
                    // 波 3 = 番兵の詰所 (ck) の老番兵の波。第一の広間は 2 波まで
                    Object hall2 = wave == 3 ? ckHall : hall;
                    int waveNo = wave == 3 ? 2 : wave;
                    Object state = ctor.newInstance(UUID.randomUUID(), hall2);
                    waveField.setInt(state, waveNo);
                    playersField.setInt(state, players);
                    AABB box2 = wave == 3 ? TitanBackLayout.CK.aabb().inflate(2) : box;
                    // 広間のチャンクを読み込んでから湧かせる (読み込まれていないと、湧いた敵が見つからない)
                    for (int cx = (int) Math.floor(box2.minX) >> 4; cx <= (int) Math.floor(box2.maxX) >> 4; cx++) {
                        for (int cz = (int) Math.floor(box2.minZ) >> 4; cz <= (int) Math.floor(box2.maxZ) >> 4; cz++) {
                            level.getChunk(cx, cz);
                        }
                    }
                    spawnWave.invoke(null, level, hall2, state);
                    List<Mob> mobs = level.getEntitiesOfClass(Mob.class, box2, m -> m.getTags().contains(TitanBackTrials.MOB_TAG));
                    List<BoneMortar> mortars = mobs.stream().filter(m -> m instanceof BoneMortar).map(m -> (BoneMortar) m).toList();
                    List<ElderSentinel> elders = mobs.stream().filter(m -> m instanceof ElderSentinel).map(m -> (ElderSentinel) m).toList();
                    long archers = mobs.stream().filter(m -> m instanceof RibArcher).count();
                    long sentinels = mobs.stream().filter(m -> m instanceof BoneSentinel).count();
                    String label = "wave " + wave + " / " + players + " player(s)";
                    if (wave == 2) {
                        int expectedMortars = players >= 4 ? 2 : 1;
                        check(mortars.size() == expectedMortars, label + ": " + mortars.size() + " mortars (expected " + expectedMortars + ")", f);
                        check(archers == 3 + 2L * (players - 1) && sentinels == 1 + 0, label + ": " + archers + " archers, " + sentinels + " sentinels", f);
                        for (BoneMortar m : mortars) {
                            check(m.getZ() <= TitanBackLayout.E1.z1() + 3.5 && TitanBackLayout.E1.contains(m.blockPosition()) && m.getTags().contains(TitanBackTrials.MOB_TAG),
                                    label + ": a mortar is not on the back line: " + m.position(), f);
                        }
                    } else {
                        check(elders.size() == 1 && archers == 0, label + ": " + elders.size() + " elders, " + archers + " archers", f);
                        check(sentinels == 2L * (players - 1), label + ": " + sentinels + " sentinels (expected " + (2 * (players - 1)) + ")", f);
                        if (elders.size() == 1) {
                            double want = 220 * StoneWhaleHeart.scaleFor(players);
                            check(Math.abs(elders.get(0).getMaxHealth() - want) < 1.0 && elders.get(0).getHealth() == elders.get(0).getMaxHealth(),
                                    label + ": elder health " + elders.get(0).getMaxHealth() + " (expected " + want + ")", f);
                            notes.add("elder health with " + players + " player(s): " + Math.round(elders.get(0).getMaxHealth()));
                        }
                    }
                    mobs.forEach(Entity::discard);
                }
            }
            notes.add("first hall: 2 waves; guard post: shield wall + elder");
        } catch (ReflectiveOperationException e) {
            f.add("could not drive TitanBackTrials.spawnWave: " + e);
        }
    }

    // ---------------------------------------------------------------- E. 落とす物・経験値・卵・床の規則

    private static void misc(ServerLevel level, List<String> f, List<String> notes) {
        RandomSource random = RandomSource.create(4242);
        BoneMortar mortar = spawn(level, Ch1BackEntities.BONE_MORTAR.get(), SX + 25, FLOOR, SZ, 0f);
        BoneGull gull = spawn(level, Ch1BackEntities.BONE_GULL.get(), SX + 25, FLOOR + 3, SZ + 4, 0f);
        ElderSentinel elder = spawn(level, Ch1BackEntities.ELDER_SENTINEL.get(), SX + 25, FLOOR, SZ + 8, 0f);
        try {
            int mortarShard = 0;
            int gullDrops = 0;
            int mortarRolls = 400;
            int elderArmor = 0;
            boolean elderAlways = true;
            for (int i = 0; i < mortarRolls; i++) {
                List<ItemStack> m = TitanLoot.rollGuard(mortar, null, random);
                mortarShard += m.stream().anyMatch(s -> s.is(jp.revati.skyabyss.registry.ModItems.STONE_WHALE_SHARD.get())) ? 1 : 0;
                gullDrops += TitanLoot.rollGuard(gull, null, random).isEmpty() ? 0 : 1;
                List<ItemStack> e = TitanLoot.rollGuard(elder, null, random);
                elderAlways &= e.stream().anyMatch(s -> s.is(jp.revati.skyabyss.registry.ModItems.STONE_WHALE_SHARD.get()) && s.getCount() >= 2)
                        && e.stream().anyMatch(s -> s.is(jp.revati.skyabyss.guide.GuideSetup.BONE_CHIP.get()) && s.getCount() >= 2);
                elderArmor += e.stream().anyMatch(s -> TitanLoot.armorSet().contains(s.getItem())) ? 1 : 0;
            }
            check(mortarShard > 40 && mortarShard < 170, "the mortar dropped a shard in " + mortarShard + " of " + mortarRolls + " rolls (about 25%)", f);
            check(gullDrops > 50 && gullDrops < 220, "the gull dropped something in " + gullDrops + " of " + mortarRolls + " rolls", f);
            check(elderAlways, "the elder did not always drop shards and bone chips", f);
            check(elderArmor > 5 && elderArmor < 80, "the elder dropped armor in " + elderArmor + " of " + mortarRolls + " rolls (about 8%)", f);
            notes.add("drops: mortar shard " + mortarShard + "/" + mortarRolls + ", gull " + gullDrops + ", elder armor " + elderArmor);
        } finally {
            mortar.discard();
            gull.discard();
            elder.discard();
        }
        for (String id : List.of("bone_mortar", "bone_gull", "elder_sentinel")) {
            check(jp.revati.skyabyss.rpg.GrowthRewards.MOD_ENEMIES.containsKey(SkyAbyss.MODID + ":" + id), id + " gives no growth XP", f);
        }
        var eggs = List.of(Ch1BackEntities.BONE_MORTAR_EGG, Ch1BackEntities.BONE_GULL_EGG, Ch1BackEntities.ELDER_SENTINEL_EGG);
        var types = List.of(Ch1BackEntities.BONE_MORTAR.get(), Ch1BackEntities.BONE_GULL.get(), Ch1BackEntities.ELDER_SENTINEL.get());
        for (int i = 0; i < 3; i++) {
            check(eggs.get(i).get() instanceof SpawnEggItem egg && egg.getType(new ItemStack(egg)) == types.get(i), "spawn egg " + i + " has no matching type", f);
            check(types.get(i).create(level) != null, "entity " + i + " cannot be created", f);
        }
        // 床の 4 割の規則: 一つの技の範囲 (砲手は同時に最大 3 つ) が、置く部屋の床の 4 割以下
        TitanBackLayout.Box e1 = TitanBackLayout.E1;
        double e1Floor = (e1.x2() - e1.x1() + 1) * (double) (e1.z2() - e1.z1() + 1);
        double mortarVolley = BoneMortar.MAX_SHELLS * Math.PI * BoneMortar.RADIUS * BoneMortar.RADIUS;
        double rush = 2 * ElderSentinel.RUSH_HALF_WIDTH * ElderSentinel.RUSH_MAX;
        double stomp = Math.PI * ElderSentinel.STOMP_RADIUS * ElderSentinel.STOMP_RADIUS;
        double widest = Math.max(mortarVolley, Math.max(rush, stomp));
        check(widest / e1Floor <= 0.4, "the widest area is " + Math.round(widest / e1Floor * 100) + "% of the first hall (limit 40%)", f);
        double gullLine = 2 * BoneGull.HALF_WIDTH * BoneGull.MAX_LINE;
        for (TitanBackLayout.Box zone : BackAmbientMobs.GULL_ZONES) {
            double floor = (zone.x2() - zone.x1() + 1) * (double) (zone.z2() - zone.z1() + 1);
            check(gullLine / floor <= 0.4, "the gull line is " + Math.round(gullLine / floor * 100) + "% of a gull zone (limit 40%)", f);
        }
        notes.add("rooms: first hall " + Math.round(widest / e1Floor * 100) + "% widest (mortar x3 " + Math.round(mortarVolley) + ", rush " + Math.round(rush) + ", stomp " + Math.round(stomp) + " of " + Math.round(e1Floor) + ")");
    }
}
