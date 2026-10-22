package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 歩 TR1「真珠の灯り運び」: 暗い珊瑚の広間。灯りは真珠を持つ人のまわりだけ。真珠 (人数で 1〜3 つ) を奥の祭壇へ運ぶ。
//  - 真珠は台に歩み寄ると持てる (一人一つ)。持つ人は少し遅くなり、まわりが照らされる。敵は持つ人を狙う
//  - 持つ人が、真珠を持たない仲間のそばでしゃがむと、真珠を手渡せる。倒れた人の真珠は元の台へ戻る
//  - 水の仕掛けはない。敵は磯走りと泡吹き (暗がりで自然に湧いた敵は消す)
public final class TidePearlRoom extends RaidRoom {
    static final int HX = 15;
    static final int HZ = 15;
    static final int H = 8;
    static final int ALTAR_Z = -HZ + 3;
    static final int[] SOCKET_X = {-3, 0, 3};
    static final int LIGHT_LEVEL = 14;
    static final double PICKUP_R = 1.7;
    static final double ALTAR_R = 3.2;
    static final double HANDOVER_R = 3.0;
    static final int HANDOVER_COOLDOWN = 40;
    static final int MAX_PEARLS = 3;

    private final BlockPos[] home = new BlockPos[MAX_PEARLS];
    private final boolean[] delivered = new boolean[MAX_PEARLS];
    private final UUID[] carrier = new UUID[MAX_PEARLS];
    private final Map<UUID, BlockPos> lights = new HashMap<>();
    private final Map<UUID, Long> handoverAt = new HashMap<>();
    private final List<AABB> pillars = new ArrayList<>();
    private int count = 1;
    private BlockPos origin;
    private long nextSpawn;
    private int spawned;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        origin = ctx.origin();
        count = Math.max(1, Math.min(MAX_PEARLS, ctx.scale()));
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.TIDE));
        // 暗くする: 天井の灯りを暗い天井材に戻す (入口の間・出口の間の灯りは残る)
        for (int x = -HX + 3; x <= HX - 2; x += 8) {
            for (int z = -HZ + 3; z <= HZ - 2; z += 8) {
                RaidKit.set(level, origin.offset(x, H + 1, z), c.palette().ceiling());
            }
        }
        BlockState brick = Blocks.PRISMARINE_BRICKS.defaultBlockState();
        // 祭壇: 北の壁ぎわの壇と、真珠の窪み三つ (灯るのは運んだ数だけ)
        RaidKit.fill(level, origin.offset(-5, 1, ALTAR_Z - 1), origin.offset(5, 1, ALTAR_Z + 1), brick);
        RaidKit.fill(level, origin.offset(-5, 2, ALTAR_Z - 1), origin.offset(-5, 4, ALTAR_Z - 1), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
        RaidKit.fill(level, origin.offset(5, 2, ALTAR_Z - 1), origin.offset(5, 4, ALTAR_Z - 1), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
        // 暗がりの飾り: 死んだ珊瑚の柱 (2×2、高さ 5)。祭壇の前・戸の前を避けて 6 本
        BlockState[] coral = {Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState(),
                Blocks.DEAD_BUBBLE_CORAL_BLOCK.defaultBlockState(), Blocks.DEAD_HORN_CORAL_BLOCK.defaultBlockState()};
        int tries = 0;
        while (pillars.size() < 6 && tries++ < 150) {
            int x = ctx.rng().nextInt(2 * HX - 5) - HX + 2;
            int z = ctx.rng().nextInt(2 * HZ - 15) - HZ + 7;
            if (Math.abs(x) < 4 && z > HZ - 9) {
                continue;
            }
            AABB box = new AABB(x, 1, z, x + 2, 6, z + 2);
            boolean clash = false;
            for (AABB o : pillars) {
                if (o.inflate(3, 0, 3).intersects(box)) {
                    clash = true;
                }
            }
            if (clash) {
                continue;
            }
            pillars.add(box);
            BlockState st = coral[ctx.rng().nextInt(coral.length)];
            RaidKit.fill(level, origin.offset(x, 1, z), origin.offset(x + 1, 4, z + 1), st);
            RaidKit.fill(level, origin.offset(x, 5, z), origin.offset(x + 1, 5, z + 1), Blocks.DARK_PRISMARINE.defaultBlockState());
        }
        // 床の模様: 紫と青の釉の板を少し混ぜる (灯りを持つと見える)。柱の根元と台座のある所は避ける
        for (int i = 0; i < 40; i++) {
            int x = ctx.rng().nextInt(2 * HX - 1) - HX + 1;
            int z = ctx.rng().nextInt(2 * HZ - 1) - HZ + 1;
            BlockPos p = origin.offset(x, 0, z);
            if (level.getBlockState(p).is(Blocks.PRISMARINE_BRICKS)) {
                RaidKit.set(level, p, ctx.rng().nextBoolean() ? Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState() : Blocks.BLUE_GLAZED_TERRACOTTA.defaultBlockState());
            }
        }
        // 真珠の台: 帯 (中・左・右) に一つずつ。台は二ブロックの低い台と、上の暗い窪み
        int[] bands = {0, -10, 10};
        for (int i = 0; i < MAX_PEARLS; i++) {
            BlockPos at = null;
            for (int k = 0; k < 60 && at == null; k++) {
                int x = bands[i] + ctx.rng().nextInt(5) - 2;
                int z = 1 + ctx.rng().nextInt(9);
                BlockPos p = c.floor(Math.max(-HX + 2, Math.min(HX - 2, x)), z);
                if (!nearPillar(p, 2)) {
                    at = p;
                }
            }
            home[i] = at == null ? c.floor(bands[i], 5) : at;
            if (i < count) {
                RaidKit.set(level, home[i].below(), Blocks.DARK_PRISMARINE.defaultBlockState());
            }
        }
        refreshSockets(ctx);
    }

    private boolean nearPillar(BlockPos p, int margin) {
        int lx = p.getX() - origin.getX();
        int lz = p.getZ() - origin.getZ();
        for (AABB b : pillars) {
            if (lx >= b.minX - margin && lx < b.maxX + margin && lz >= b.minZ - margin && lz < b.maxZ + margin) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void start(RaidCtx ctx) {
        spawned = 0;
        for (int i = 0; i < 1 + ctx.scale(); i++) {
            spawnFoe(ctx);
        }
        nextSpawn = ctx.now() + 160;
    }

    @Override
    public void tick(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        List<ServerPlayer> players = ctx.players();
        long now = ctx.now();
        // 倒れた・いなくなった持ち手は手放す (真珠は元の台へ)
        for (int i = 0; i < count; i++) {
            if (carrier[i] == null || delivered[i]) {
                continue;
            }
            ServerPlayer p = find(players, carrier[i]);
            if (p == null || Downed.isDowned(p)) {
                drop(ctx, i, carrier[i]);
            }
        }
        // 真珠を拾う
        for (int i = 0; i < count; i++) {
            if (delivered[i] || carrier[i] != null) {
                continue;
            }
            for (ServerPlayer p : players) {
                if (Downed.isDowned(p) || holding(p.getUUID()) >= 0) {
                    continue;
                }
                double dx = p.getX() - (home[i].getX() + 0.5);
                double dz = p.getZ() - (home[i].getZ() + 0.5);
                if (dx * dx + dz * dz <= PICKUP_R * PICKUP_R && Math.abs(p.getY() - home[i].getY()) < 2.5) {
                    carrier[i] = p.getUUID();
                    level.playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.2f, 1.4f);
                    break;
                }
            }
        }
        // 持ち手: 少し遅く・灯りを動かす・祭壇・手渡し
        List<ServerPlayer> carriers = new ArrayList<>();
        List<UUID> stillLit = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (carrier[i] == null || delivered[i]) {
                continue;
            }
            ServerPlayer p = find(players, carrier[i]);
            if (p == null) {
                continue;
            }
            carriers.add(p);
            stillLit.add(p.getUUID());
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 12, 0, false, false, false));
            moveLight(ctx, p);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 2.2, p.getZ(), 1, 0.15, 0.1, 0.15, 0.01);
            double dx = p.getX() - (origin.getX() + 0.5);
            double dz = p.getZ() - (origin.getZ() + ALTAR_Z + 0.5);
            if (dx * dx + dz * dz <= ALTAR_R * ALTAR_R && Math.abs(p.getY() - (origin.getY() + 1)) < 3) {
                carrier[i] = null;
                delivered[i] = true;
                stillLit.remove(p.getUUID());
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                place(ctx, i);
                continue;
            }
            if (p.isShiftKeyDown() && now - handoverAt.getOrDefault(p.getUUID(), -1000L) >= HANDOVER_COOLDOWN) {
                for (ServerPlayer q : players) {
                    if (q == p || Downed.isDowned(q) || holding(q.getUUID()) >= 0 || q.distanceToSqr(p) > HANDOVER_R * HANDOVER_R) {
                        continue;
                    }
                    carrier[i] = q.getUUID();
                    handoverAt.put(p.getUUID(), now);
                    handoverAt.put(q.getUUID(), now);
                    stillLit.remove(p.getUUID());
                    p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    level.playSound(null, q.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 1.2f);
                    level.sendParticles(ParticleTypes.END_ROD, q.getX(), q.getY() + 1.5, q.getZ(), 8, 0.3, 0.3, 0.3, 0.05);
                    break;
                }
            }
        }
        for (UUID id : new ArrayList<>(lights.keySet())) {
            if (!stillLit.contains(id)) {
                removeLight(ctx, id);
            }
        }
        // 台に残る真珠のきらめき (小さな粒だけ。灯りは出さない)
        if (now % 10 == 0) {
            for (int i = 0; i < count; i++) {
                if (!delivered[i] && carrier[i] == null) {
                    level.sendParticles(ParticleTypes.END_ROD, home[i].getX() + 0.5, home[i].getY() + 1.1, home[i].getZ() + 0.5, 1, 0.1, 0.1, 0.1, 0.0);
                }
            }
        }
        // 敵: 持ち手を狙う。ときどき湧く
        if (now % 10 == 0) {
            for (Mob m : ctx.aliveMobs()) {
                ServerPlayer t = TideRaid.nearest(carriers.isEmpty() ? ctx.standing() : carriers, m.getX(), m.getZ());
                if (t != null) {
                    m.setTarget(t);
                }
            }
        }
        if (now % 40 == 0) {
            TideRaid.purgeStrays(ctx, ctx.cell().bounds());
        }
        if (now >= nextSpawn && delivered() < count) {
            nextSpawn = now + 200;
            if (ctx.aliveMobs().size() < 3 + 2 * ctx.scale()) {
                spawnFoe(ctx);
            }
        }
    }

    private void spawnFoe(RaidCtx ctx) {
        BlockPos at = null;
        for (int k = 0; k < 30 && at == null; k++) {
            BlockPos p = RaidKit.randomSpot(ctx, 2);
            if (p.getZ() - origin.getZ() > HZ - 6 || nearPillar(p, 1)) {
                continue;
            }
            boolean far = true;
            for (ServerPlayer pl : ctx.players()) {
                if (pl.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 10 * 10) {
                    far = false;
                }
            }
            if (far) {
                at = p;
            }
        }
        if (at == null) {
            at = ctx.cell().floor(-HX + 2, -HZ + 6);
        }
        spawned++;
        boolean puffer = spawned % 4 == 0;
        TideRaid.spawn(ctx, puffer ? TideMobSetup.BUBBLE_PUFFER.get() : TideMobSetup.REEF_SCUTTLER.get(), at);
    }

    @Nullable
    private ServerPlayer find(List<ServerPlayer> players, UUID id) {
        for (ServerPlayer p : players) {
            if (p.getUUID().equals(id)) {
                return p;
            }
        }
        return null;
    }

    private int holding(UUID id) {
        for (int i = 0; i < count; i++) {
            if (!delivered[i] && id.equals(carrier[i])) {
                return i;
            }
        }
        return -1;
    }

    private void drop(RaidCtx ctx, int i, UUID id) {
        carrier[i] = null;
        removeLight(ctx, id);
        ctx.level().playSound(null, home[i], SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 1f, 0.7f);
        ctx.say(Component.translatable("message.skyabyss.raid.TR1.dropped"));
    }

    private void place(RaidCtx ctx, int i) {
        refreshSockets(ctx);
        BlockPos s = origin.offset(SOCKET_X[i], 1, ALTAR_Z);
        ctx.level().playSound(null, s, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.5f, 0.9f);
        ctx.level().sendParticles(ParticleTypes.END_ROD, s.getX() + 0.5, s.getY() + 1.2, s.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.05);
    }

    // 窪みの灯り: 置かれた真珠は海のランタンに
    private void refreshSockets(RaidCtx ctx) {
        for (int i = 0; i < SOCKET_X.length; i++) {
            boolean lit = i < count && delivered[i];
            RaidKit.set(ctx.level(), origin.offset(SOCKET_X[i], 1, ALTAR_Z), lit ? Blocks.SEA_LANTERN.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
        }
    }

    // 灯り: 持ち手の目の高さの空気のブロックに光のブロックを置く
    private void moveLight(RaidCtx ctx, ServerPlayer p) {
        ServerLevel level = ctx.level();
        BlockPos want = BlockPos.containing(p.getX(), p.getEyeY(), p.getZ());
        if (want.equals(lights.get(p.getUUID())) || !level.getBlockState(want).isAir()) {
            return;
        }
        removeLight(ctx, p.getUUID());
        level.setBlock(want, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, LIGHT_LEVEL), 3);
        lights.put(p.getUUID(), want);
    }

    private void removeLight(RaidCtx ctx, UUID id) {
        BlockPos p = lights.remove(id);
        if (p != null && ctx.level().getBlockState(p).is(Blocks.LIGHT)) {
            ctx.level().setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private void removeAllLights(RaidCtx ctx) {
        for (UUID id : new ArrayList<>(lights.keySet())) {
            removeLight(ctx, id);
        }
    }

    private int delivered() {
        int n = 0;
        for (int i = 0; i < count; i++) {
            if (delivered[i]) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean isComplete() {
        return delivered() >= count;
    }

    @Override
    public void reset(RaidCtx ctx) {
        removeAllLights(ctx);
        for (int i = 0; i < MAX_PEARLS; i++) {
            delivered[i] = false;
            carrier[i] = null;
        }
        handoverAt.clear();
        for (ServerPlayer p : ctx.players()) {
            p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        }
        refreshSockets(ctx);
    }

    @Override
    public void end(RaidCtx ctx) {
        removeAllLights(ctx);
    }

    @Override
    public String objectiveKey() {
        for (int i = 0; i < count; i++) {
            if (carrier[i] != null && !delivered[i]) {
                return "raid.skyabyss.TR1.carrying";
            }
        }
        return "raid.skyabyss.TR1.fetch";
    }

    @Override
    public int counter() {
        return delivered();
    }

    @Override
    public int counterMax() {
        return count;
    }

    // 試験用
    public BlockPos pearlHome(int i) {
        return home[i];
    }

    public int pearlCount() {
        return count;
    }

    @Nullable
    public UUID carrierOf(int i) {
        return carrier[i];
    }

    public boolean lit(UUID id) {
        return lights.containsKey(id);
    }

    public boolean isDelivered(int i) {
        return delivered[i];
    }
}
