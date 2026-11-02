package jp.revati.skyabyss.trial.raid.ember;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SelfTestRunner;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRooms;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.FakePlayer;

// 余燼のレイドの三つの部屋 (ER1 熾火運び・ER2 鍛冶の手順・ER3 炉を守る) の自己テスト。SKYABYSS_SELFTEST_ONLY="ember raid"
//  それぞれ建てて、手順どおりに解いて越える。間違い・全滅 (リセット) で元に戻る。人は偽の人 (testPlayers) で差し替える
public final class EmberRaidSelfTest {
    private static final int CX = 2000;
    private static final int CZ = 2000;
    private static final int FLOOR = 150;
    private static final float HEALTH = 40f;

    private EmberRaidSelfTest() {
    }

    public static List<SelfTestRunner.Step> steps() {
        return List.of(
                SelfTestRunner.Step.once("ember raid (load)", EmberRaidSelfTest::load),
                SelfTestRunner.Step.once("ember raid", 30, EmberRaidSelfTest::run),
                EmberRaid2SelfTest.steps().get(0));
    }

    static void load(MinecraftServer server) {
        ServerLevel level = server.overworld();
        for (int cx = (CX >> 4) - 3; cx <= (CX >> 4) + 3; cx++) {
            for (int cz = (CZ >> 4) - 3; cz <= (CZ >> 4) + 3; cz++) {
                level.setChunkForced(cx, cz, true);
            }
        }
    }

    static void run(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        ServerLevel level = server.overworld();
        Difficulty before = server.getWorldData().getDifficulty();
        List<FakePlayer> fakes = new ArrayList<>();
        try {
            server.setDifficulty(Difficulty.NORMAL, true);
            check(f, "registered ER1", RaidRooms.create(EmberCarry.ID) instanceof EmberCarry);
            check(f, "registered ER2", RaidRooms.create(EmberForgeOrder.ID) instanceof EmberForgeOrder);
            check(f, "registered ER3", jp.revati.skyabyss.trial.raid.mid.FightBossRoom.unwrap(RaidRooms.create(EmberForgeDefense.ID)) instanceof EmberForgeDefense);
            StringBuilder dump = new StringBuilder("{");
            carry(level, f, fakes, dump);
            order(level, f, fakes, dump);
            defense(level, f, fakes, dump);
            smithHall(level, f);
            dump.append("}");
            try {
                Files.writeString(Path.of("raid_ember_dump.json"), dump.toString());
            } catch (java.io.IOException e) {
                f.add("dump write failed: " + e);
            }
        } catch (RuntimeException | ReflectiveOperationException e) {
            SkyAbyss.LOGGER.error("SELFTEST ember raid crashed", e);
            f.add("crashed: " + e);
        } finally {
            server.setDifficulty(before, true);
            for (FakePlayer p : fakes) {
                p.discard();
            }
            for (int cx = (CX >> 4) - 3; cx <= (CX >> 4) + 3; cx++) {
                for (int cz = (CZ >> 4) - 3; cz <= (CZ >> 4) + 3; cz++) {
                    level.setChunkForced(cx, cz, false);
                }
            }
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST ember raid PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST ember raid FAILED: {}", m));
        }
    }

    static void check(List<String> f, String what, boolean ok) {
        if (!ok) {
            f.add(what);
        }
    }

    // ---- 部品
    static RaidCtx newCtx(ServerLevel level, long seed) throws ReflectiveOperationException {
        Constructor<RaidCtx> c = RaidCtx.class.getDeclaredConstructor(ServerLevel.class, UUID.class, String.class, String.class, int.class, int.class,
                BlockPos.class, long.class);
        c.setAccessible(true);
        return c.newInstance(level, UUID.nameUUIDFromBytes(("ember raid test " + seed).getBytes(java.nio.charset.StandardCharsets.UTF_8)), "ember", "ER", 0, 1,
                new BlockPos(CX, FLOOR, CZ), seed);
    }

    static FakePlayer fake(ServerLevel level, String tag) {
        FakePlayer p = new FakePlayer(level, new GameProfile(UUID.nameUUIDFromBytes(("ember raid " + tag).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                "[er_" + tag + "]")) {
            @Override
            public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
                return false;
            }
        };
        try {
            java.lang.reflect.Field spawn = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            spawn.setAccessible(true);
            spawn.setInt(p, 0);
        } catch (ReflectiveOperationException e) {
            SkyAbyss.LOGGER.warn("could not clear the spawn invulnerability of a fake player", e);
        }
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(HEALTH);
        p.setHealth(HEALTH);
        p.moveTo(CX, FLOOR + 1, CZ, 0, 0);
        p.setOnGround(true);
        level.addFreshEntity(p);
        return p;
    }

    static void at(FakePlayer p, BlockPos pos) {
        p.moveTo(pos.getX() + 0.5, FLOOR + 1, pos.getZ() + 0.5, 0, 0);
        p.setOnGround(true);
    }

    static void ticks(ServerLevel level, EmberRoom room, RaidCtx ctx, int n) {
        for (int i = 0; i < n; i++) {
            Telegraph.tickNow(level);
            room.tick(ctx);
        }
    }

    // 部屋を建てて、上から見た形 (各 x,z の一番上のブロック) を dump へ
    static void dumpPlan(ServerLevel level, RaidCtx ctx, String id, StringBuilder dump) {
        var cell = ctx.cell();
        if (dump.length() > 1) {
            dump.append(",");
        }
        dump.append("\"").append(id).append("\":{\"x0\":").append(cell.bounds().minX).append(",\"z0\":").append(cell.bounds().minZ)
                .append(",\"rows\":[");
        int x0 = (int) cell.bounds().minX;
        int x1 = (int) cell.bounds().maxX;
        int z0 = (int) cell.bounds().minZ;
        int z1 = (int) cell.bounds().maxZ;
        for (int z = z0; z < z1; z++) {
            StringBuilder row = new StringBuilder("[");
            for (int x = x0; x < x1; x++) {
                String name = "air";
                int top = -1;
                for (int y = FLOOR + 14; y >= FLOOR - 1; y--) {
                    BlockState st = level.getBlockState(new BlockPos(x, y, z));
                    if (!st.isAir()) {
                        name = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        top = y;
                        break;
                    }
                }
                // 天井は飛ばして、床・仕掛けを拾う: 天井の下の最も高いもの
                String shown = name;
                for (int y = FLOOR + 12; y >= FLOOR; y--) {
                    BlockState st = level.getBlockState(new BlockPos(x, y, z));
                    if (!st.isAir() && y <= FLOOR + 7) {
                        shown = BuiltInRegistries.BLOCK.getKey(st.getBlock()).getPath();
                        top = y;
                        break;
                    }
                }
                row.append("[\"").append(shown).append("\",").append(top - FLOOR).append("]");
                if (x < x1 - 1) {
                    row.append(",");
                }
            }
            row.append("]");
            dump.append(row);
            if (z < z1 - 1) {
                dump.append(",");
            }
        }
        dump.append("]}");
    }

    // 飾り (docs/design/39 の 6 章の負荷の上限): 飾りのブロック 8 千・明るさ 12 以上 40・ブロックエンティティ 24・飾りのエンティティ 16。
    // 飾りが付いていること (ただの箱に戻っていない) も見る
    static void decor(List<String> f, EmberRoom room, String id) {
        EmberRaidDecor.Audit a = room.decorAudit;
        if (a == null) {
            f.add(id + " has no decoration audit (EmberRaidDecor)");
            return;
        }
        if (!a.ok()) {
            f.add(id + " decoration over budget: " + a);
        }
        if (a.deco() < 800) {
            f.add(id + " has almost no decoration: " + a);
        }
        if (room.exitLamps.isEmpty()) {
            f.add(id + " has no exit lamps");
        }
    }

    // 中ボスの間 ERM の飾り: 建てて、閉じていて、負荷の内か。片付ける
    private static void smithHall(ServerLevel level, List<String> f) throws ReflectiveOperationException {
        RaidCtx ctx = newCtx(level, 77);
        jp.revati.skyabyss.trial.raid.RaidRoom room = jp.revati.skyabyss.trial.raid.mid.MidRooms.create(jp.revati.skyabyss.progress.Titans.EMBER);
        if (room == null) {
            f.add("ERM is not registered");
            return;
        }
        room.build(ctx);
        try {
            sealed(level, ctx, f, "ERM");
            EmberRaidDecor.Audit a = EmberRaidDecor.lastSmithAudit;
            if (a == null || !a.ok() || a.deco() < 800) {
                f.add("ERM decoration missing or over budget: " + a);
            }
            // 元の間の仕掛け (灰溜まり・炉の扉) は残っている
            BlockPos s = ctx.origin().subtract(jp.revati.skyabyss.titan.ember.back.EmberBackLayout.SMITH_CENTER);
            for (BlockPos p : jp.revati.skyabyss.titan.ember.smith.AshSmithArena.PUDDLES) {
                if (!level.getBlockState(p.offset(s)).is(Blocks.GRAY_WOOL)) {
                    f.add("ERM ash puddle covered at " + p.offset(s).toShortString());
                }
            }
            if (jp.revati.skyabyss.titan.ember.smith.AshSmithArena.furnaceOpen(level, s)) {
                f.add("ERM furnace door is open after decorating");
            }
        } finally {
            room.end(ctx);
            jp.revati.skyabyss.trial.raid.RaidKit.clear(level, ctx.cell().bounds());
        }
    }

    static boolean sealed(ServerLevel level, RaidCtx ctx, List<String> f, String id) {
        // 閉じている: 部屋の外周 (殻) に空気がない。入口・出口の戸の外側は戸が閉じていること
        var b = ctx.cell().bounds();
        int bad = 0;
        for (int x = (int) b.minX; x < b.maxX; x++) {
            for (int y = (int) b.minY; y < b.maxY; y++) {
                for (int z : new int[] {(int) b.minZ, (int) b.maxZ - 1}) {
                    if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        bad++;
                    }
                }
            }
        }
        for (int z = (int) b.minZ; z < b.maxZ; z++) {
            for (int y = (int) b.minY; y < b.maxY; y++) {
                for (int x : new int[] {(int) b.minX, (int) b.maxX - 1}) {
                    if (level.getBlockState(new BlockPos(x, y, z)).isAir()) {
                        bad++;
                    }
                }
            }
        }
        for (int x = (int) b.minX; x < b.maxX; x++) {
            for (int z = (int) b.minZ; z < b.maxZ; z++) {
                if (level.getBlockState(new BlockPos(x, (int) b.minY, z)).isAir() || level.getBlockState(new BlockPos(x, (int) b.maxY - 1, z)).isAir()) {
                    bad++;
                }
            }
        }
        if (bad > 0) {
            f.add(id + " shell has " + bad + " air blocks (not watertight)");
        }
        return bad == 0;
    }

    // ---- ER1
    private static void carry(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberCarry room = new EmberCarry();
        RaidCtx ctx = newCtx(level, 11);
        room.build(ctx);
        sealed(level, ctx, f, "ER1");
        EmberRaidSelfTest.decor(f, room, "ER1");
        dumpPlan(level, ctx, "ER1", dump);
        room.start(ctx);
        FakePlayer a = fake(level, "a1");
        FakePlayer c = fake(level, "c1");
        fakes.add(a);
        fakes.add(c);
        room.testPlayers = List.of(a, c);
        check(f, "ER1 starts with one ember ready", room.readyEmbers() == 1);
        at(a, room.sourcePos());
        at(c, room.forgePos().offset(0, 0, -7));
        ticks(level, room, ctx, 2);
        check(f, "ER1 picking up at the vent", room.carrying() == 1 && room.readyEmbers() == 0);
        check(f, "ER1 HUD timer shown while carrying", room.timerTicks() > 0 && room.timerTicks() <= EmberCarry.EMBER_TICKS);
        // 運ぶ人は熱で傷む
        float hp0 = a.getHealth();
        at(a, room.forgePos().offset(0, 0, -7).offset(-3, 0, 0));
        at(c, a.blockPosition().offset(1, 0, 0));
        a.setHealth(hp0);
        // 渡す前に熱: 一周ぶん
        ticks(level, room, ctx, EmberCarry.HEAT_EVERY * 2);
        check(f, "ER1 carrier takes heat damage but not to death", a.getHealth() < hp0 && a.getHealth() > 0);
        // 渡す
        a.setShiftKeyDown(true);
        ticks(level, room, ctx, 2);
        a.setShiftKeyDown(false);
        check(f, "ER1 hand-over moves the ember to the ally", room.carrying() == 1 && room.timerTicks() > 0);
        // 納める (c が運んでいるはず)
        at(c, room.forgePos());
        at(a, room.sourcePos().offset(4, 0, 0));
        ticks(level, room, ctx, 2);
        check(f, "ER1 delivery counts", room.counter() == 1);
        // 冷める
        at(a, room.sourcePos());
        ticks(level, room, ctx, EmberCarry.SPAWN_EVERY * 2);
        check(f, "ER1 a new ember appeared and was taken", room.carrying() == 1);
        room.forceCool(a.getUUID(), 2);
        at(a, room.sourcePos().offset(5, 0, 5));
        ticks(level, room, ctx, 3);
        check(f, "ER1 ember cools and vanishes", room.carrying() == 0 && room.counter() == 1);
        // 残りを運ぶ (一人で)
        room.testPlayers = List.of(a);
        for (int i = 0; i < 6 && !room.isComplete(); i++) {
            at(a, room.sourcePos());
            ticks(level, room, ctx, EmberCarry.SPAWN_EVERY + 5);
            at(a, room.forgePos());
            ticks(level, room, ctx, 2);
        }
        check(f, "ER1 solvable by one player (3 delivered)", room.isComplete() && room.counter() == 3);
        // 全滅 / やり直し
        room.reset(ctx);
        room.start(ctx);
        check(f, "ER1 reset restores the room", !room.isComplete() && room.counter() == 0 && room.carrying() == 0 && room.readyEmbers() == 1);
        check(f, "ER1 sockets are cold again", level.getBlockState(ctx.local(0, 2, 10)).is(Blocks.COAL_BLOCK));
        // 噴気の赤い予告のあとに当たる (誰も持っていない人が穴の上に立つ)
        FakePlayer v = fake(level, "v1");
        fakes.add(v);
        room.testPlayers = List.of(v);
        float vhp = v.getHealth();
        at(v, ctx.local(0, 0, -3));
        for (int i = 0; i < EmberCarry.VENT_CYCLE * 2 && v.getHealth() >= vhp; i++) {
            ticks(level, room, ctx, 1);
            v.invulnerableTime = 0;
        }
        check(f, "ER1 a vent burst hurts someone standing in it", v.getHealth() < vhp);
    }

    // ---- ER2
    private static void order(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberForgeOrder room = new EmberForgeOrder();
        RaidCtx ctx = newCtx(level, 21);
        room.build(ctx);
        sealed(level, ctx, f, "ER2");
        EmberRaidSelfTest.decor(f, room, "ER2");
        dumpPlan(level, ctx, "ER2", dump);
        room.start(ctx);
        FakePlayer a = fake(level, "a2");
        FakePlayer b = fake(level, "b2");
        fakes.add(a);
        fakes.add(b);
        room.testPlayers = List.of(a, b);
        // 順は毎回変わる
        java.util.Set<String> orders = new java.util.HashSet<>();
        for (int s = 0; s < 6; s++) {
            EmberForgeOrder r = new EmberForgeOrder();
            RaidCtx c = newCtx(level, 100 + s * 7);
            r.build(c);
            orders.add(java.util.Arrays.toString(r.order()));
        }
        check(f, "ER2 the order varies between runs", orders.size() >= 2);
        // 作り直したので、本番の部屋をもう一度建てて解く
        room = new EmberForgeOrder();
        ctx = newCtx(level, 21);
        room.build(ctx);
        room.start(ctx);
        room.testPlayers = List.of(a, b);
        int[] ord = room.order();
        // 二人で手分けして支度: a が二つ、b が二つ
        for (int i = 0; i < 4; i++) {
            FakePlayer who = i < 2 ? a : b;
            BlockPos pad = room.padOf(ctx, ord[i]);
            who.setShiftKeyDown(false);
            at(who, pad);
            ticks(level, room, ctx, EmberForgeOrder.CHARGE_TICKS + 2);
        }
        at(a, ctx.local(0, 0, 0));
        at(b, ctx.local(0, 0, 0));
        check(f, "ER2 all four stations pre-readied", room.readyCount() == 4);
        // 間違い: 二番目から使う → 全部戻る・傷なし
        float hp = a.getHealth();
        BlockPos wrong = room.padOf(ctx, ord[1]);
        at(a, wrong);
        a.setShiftKeyDown(true);
        ticks(level, room, ctx, 2);
        a.setShiftKeyDown(false);
        ticks(level, room, ctx, 1);
        check(f, "ER2 wrong order resets the stations", room.readyCount() == 0 && room.next() == 0 && room.wrongs() == 1);
        check(f, "ER2 wrong order deals no damage", a.getHealth() >= hp);
        // 正しい順
        for (int i = 0; i < 4; i++) {
            BlockPos pad = room.padOf(ctx, ord[i]);
            a.setShiftKeyDown(false);
            at(a, pad);
            ticks(level, room, ctx, EmberForgeOrder.CHARGE_TICKS + 2);
        }
        for (int i = 0; i < 4; i++) {
            at(a, room.padOf(ctx, ord[i]));
            a.setShiftKeyDown(false);
            ticks(level, room, ctx, 1);
            a.setShiftKeyDown(true);
            ticks(level, room, ctx, 1);
        }
        a.setShiftKeyDown(false);
        check(f, "ER2 solved in the mural order", room.isComplete() && room.next() == 4);
        room.reset(ctx);
        room.start(ctx);
        check(f, "ER2 reset restores the room", !room.isComplete() && room.next() == 0 && room.readyCount() == 0);
    }

    // ---- ER3
    private static void defense(ServerLevel level, List<String> f, List<FakePlayer> fakes, StringBuilder dump) throws ReflectiveOperationException {
        EmberForgeDefense room = new EmberForgeDefense();
        RaidCtx ctx = newCtx(level, 31);
        room.build(ctx);
        sealed(level, ctx, f, "ER3");
        EmberRaidSelfTest.decor(f, room, "ER3");
        dumpPlan(level, ctx, "ER3", dump);
        room.start(ctx);
        FakePlayer a = fake(level, "a3");
        fakes.add(a);
        room.testPlayers = List.of(a);
        at(a, ctx.local(0, 0, 8));
        check(f, "ER3 forge starts full", room.forgeHp() == EmberForgeDefense.FORGE_MAX);
        ticks(level, room, ctx, EmberForgeDefense.FIRST_DELAY + 2);
        List<Mob> wave = new ArrayList<>(ctx.aliveMobs());
        check(f, "ER3 first wave spawned (" + wave.size() + ")", room.wavesSpawned() == 1 && !wave.isEmpty());
        // 敵が炉のそばで火を消す
        if (!wave.isEmpty()) {
            Mob m = wave.get(0);
            int hp0 = room.forgeHp();
            for (int i = 0; i < 80; i++) {
                m.moveTo(room.forgePos().getX() + 2.5, FLOOR + 1, room.forgePos().getZ() + 0.5);
                m.setDeltaMovement(0, 0, 0);
                ticks(level, room, ctx, 1);
            }
            check(f, "ER3 enemies at the forge douse it", room.forgeHp() < hp0);
            // 火鉢を消す → 人が灯し直す
            room.setForgeHp(EmberForgeDefense.FORGE_MAX);
            BlockPos br = room.brazierPos(ctx, 0);
            check(f, "ER3 brazier starts lit", room.brazierLit(0));
            m.moveTo(br.getX() + 0.5, FLOOR + 1, br.getZ() + 0.5);
            ticks(level, room, ctx, 2);
            check(f, "ER3 an enemy dowses a brazier", !room.brazierLit(0));
            m.moveTo(room.forgePos().getX() + 8.5, FLOOR + 1, room.forgePos().getZ() + 8.5);
            at(a, br);
            ticks(level, room, ctx, EmberForgeDefense.RELIGHT_TICKS + 2);
            check(f, "ER3 a player relights the brazier", room.brazierLit(1) && room.brazierLit(0));
            // 灯った火鉢のそばで敵が遅くなる
            m.removeAllEffects();
            m.moveTo(br.getX() + 3.5, FLOOR + 1, br.getZ() + 3.5);
            at(a, ctx.local(0, 0, 8));
            ticks(level, room, ctx, 12);
            check(f, "ER3 lit braziers slow attackers", m.hasEffect(MobEffects.MOVEMENT_SLOWDOWN));
        }
        // 三つの波を片付ける
        for (int i = 0; i < 2000 && !room.isComplete(); i++) {
            if (i % 5 == 0) {
                for (Mob m : new ArrayList<>(ctx.aliveMobs())) {
                    m.discard();
                }
            }
            ticks(level, room, ctx, 1);
        }
        check(f, "ER3 three waves cleared completes the room", room.isComplete() && room.wavesSpawned() == EmberForgeDefense.WAVES);
        // 炉が消える → リセット
        room.reset(ctx);
        ctx.clearMobs();
        room.start(ctx);
        check(f, "ER3 reset restores the room", !room.isComplete() && room.forgeHp() == EmberForgeDefense.FORGE_MAX && room.wavesSpawned() == 0
                && ctx.aliveMobs().isEmpty());
        room.setForgeHp(0);
        ticks(level, room, ctx, 1);
        check(f, "ER3 forge lost asks for a reset", room.failRequested);
        room.reset(ctx);
        ctx.clearMobs();
        room.start(ctx);
        check(f, "ER3 reset after loss restores the forge", room.forgeHp() == EmberForgeDefense.FORGE_MAX && !room.failRequested);
    }
}
