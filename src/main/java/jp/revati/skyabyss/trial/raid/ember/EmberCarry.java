package jp.revati.skyabyss.trial.raid.ember;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

// 歩 ER1「熾火運び」(docs/design/13_ember.md)。噴き口の熾を、冷める前に冷えた炉へ三つ運ぶ。
//  - 熾は噴き口で湧く (最大 2 個。取れるのは一人一個)。運ぶ人は冷める時計 (EMBER_TICKS) を持ち、ゆっくり熱で傷む。時計が尽きると熾は消える
//  - しゃがむと、近くの何も持っていない仲間へ熾を渡せる (時計はそのまま)。手分けして、一人が取り、一人が中継し、一人が炉へ
//  - 道には噴気の穴が六つ。赤い印が 1.5 秒ほど出て、そのあと噴く (HearthVents と同じ傷)
public final class EmberCarry extends EmberRoom {
    public static final String ID = "ER1";
    public static final int NEED = 3;
    public static final int EMBER_TICKS = 560;
    public static final int HEAT_EVERY = 60;
    public static final float HEAT_DAMAGE = 1f;
    public static final int VENT_CYCLE = 120;
    public static final int VENT_WARN = 30;
    public static final int MAX_READY = 2;
    public static final int SPAWN_EVERY = 70;
    private static final int[][] VENTS = {{-5, -8}, {5, -8}, {0, -3}, {-6, 2}, {6, 2}, {0, 6}};
    private static final int[] SOCKET_X = {-2, 0, 2};

    private BlockPos source;
    private BlockPos forge;
    private int delivered;
    private int ready;
    private long t;
    private long nextSpawn;
    private final int[] phase = new int[VENTS.length];
    private final Map<UUID, Integer> carry = new HashMap<>();
    private final Map<UUID, Long> handCooldown = new HashMap<>();
    private boolean done;

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(12, 15, 8, RaidKit.EMBER));
        var lv = ctx.level();
        source = ctx.local(0, 0, -12);
        forge = ctx.local(0, 0, 10);
        // 噴き口の台 (3x3 の玄武岩、真ん中がマグマ)。熾はその上の灯り
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                set(ctx, source.offset(x, 0, z), b(Blocks.SMOOTH_BASALT));
            }
        }
        set(ctx, source, b(Blocks.MAGMA_BLOCK));
        for (int[] c : new int[][] {{-3, -14}, {3, -14}, {-3, -10}, {3, -10}}) {
            RaidKit.fill(lv, ctx.local(c[0], 1, c[1]), ctx.local(c[0], 4, c[1]), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
            set(ctx, ctx.local(c[0], 5, c[1]), b(Blocks.SHROOMLIGHT));
        }
        // 噴気の穴 (3x3 の黒い縁と、マグマの口)
        for (int[] v : VENTS) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    set(ctx, ctx.local(v[0] + x, 0, v[1] + z), b(Blocks.BASALT));
                }
            }
            set(ctx, ctx.local(v[0], 0, v[1]), b(Blocks.MAGMA_BLOCK));
        }
        // 冷えた炉 (台と三つの口、南の壁ぎわの煙突)
        RaidKit.fill(lv, ctx.local(-4, 0, 9), ctx.local(4, 0, 11), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-3, 1, 12), ctx.local(3, 4, 12), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        RaidKit.fill(lv, ctx.local(-1, 5, 12), ctx.local(1, 6, 12), b(Blocks.BLACKSTONE));
        for (int x : SOCKET_X) {
            RaidKit.fill(lv, ctx.local(x, 1, 10), ctx.local(x, 1, 11), b(Blocks.POLISHED_BLACKSTONE_BRICKS));
        }
        resetState(ctx);
    }

    private void resetState(RaidCtx ctx) {
        delivered = 0;
        ready = 1;
        t = 0;
        nextSpawn = SPAWN_EVERY;
        done = false;
        carry.clear();
        handCooldown.clear();
        for (int i = 0; i < phase.length; i++) {
            phase[i] = ctx.rng().nextInt(VENT_CYCLE);
        }
        for (int x : SOCKET_X) {
            set(ctx, ctx.local(x, 2, 10), b(Blocks.COAL_BLOCK));
        }
        showEmber(ctx);
    }

    private void showEmber(RaidCtx ctx) {
        set(ctx, source.above(), ready > 0 ? b(Blocks.SHROOMLIGHT) : AIR);
        set(ctx, source.above(2), ready > 1 ? b(Blocks.SHROOMLIGHT) : AIR);
    }

    @Override
    public void start(RaidCtx ctx) {
        resetState(ctx);
    }

    @Override
    public void reset(RaidCtx ctx) {
        generation++;
        failRequested = false;
        resetState(ctx);
    }

    @Override
    public void tick(RaidCtx ctx) {
        if (done) {
            return;
        }
        t++;
        List<ServerPlayer> ps = who(ctx);
        // 噴気の巡り
        for (int i = 0; i < VENTS.length; i++) {
            int at = (int) ((t + phase[i]) % VENT_CYCLE);
            if (at == 0) {
                BlockPos v = ctx.local(VENTS[i][0], 0, VENTS[i][1]);
                redCircle(ctx, v.getX() + 0.5, v.getZ() + 0.5, 2.0, VENT_WARN, (lvl, p) -> ventBurst(lvl, p, v));
            } else if (at == VENT_WARN) {
                BlockPos v = ctx.local(VENTS[i][0], 1, VENTS[i][1]);
                particles(ctx, ParticleTypes.FLAME, v.getX() + 0.5, v.getY(), v.getZ() + 0.5, 24, 0.5, 0.08);
                ctx.level().playSound(null, v, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 0.7f);
            }
        }
        // 噴き口で熾が湧く
        if (ready < MAX_READY && t >= nextSpawn) {
            ready++;
            nextSpawn = t + SPAWN_EVERY;
            showEmber(ctx);
        }
        // 持っている人の時計と熱
        for (ServerPlayer p : ps) {
            Integer left = carry.get(p.getUUID());
            if (left == null) {
                continue;
            }
            if (left <= 1) {
                carry.remove(p.getUUID());
                particles(ctx, ParticleTypes.SMOKE, p.getX(), p.getY() + 1, p.getZ(), 20, 0.3, 0.02);
                bar(ctx, Component.translatable("raid.skyabyss.ER1.cooled"));
                continue;
            }
            carry.put(p.getUUID(), left - 1);
            if (t % HEAT_EVERY == 0) {
                heatHurt(ctx, p, HEAT_DAMAGE, 5f);
            }
            if (t % 4 == 0) {
                particles(ctx, ParticleTypes.FLAME, p.getX(), p.getY() + 1.2, p.getZ(), 1, 0.2, 0.01);
            }
        }
        // 取る・渡す・納める
        for (ServerPlayer p : ps) {
            UUID id = p.getUUID();
            if (!carry.containsKey(id)) {
                if (ready > 0 && near(p, source, 2.6)) {
                    ready--;
                    carry.put(id, EMBER_TICKS);
                    showEmber(ctx);
                    nextSpawn = Math.max(nextSpawn, t + SPAWN_EVERY / 2);
                    ctx.level().playSound(null, p.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8f, 1f);
                }
                continue;
            }
            if (near(p, forge, 3.6)) {
                carry.remove(id);
                set(ctx, ctx.local(SOCKET_X[Math.min(delivered, NEED - 1)], 2, 10), b(Blocks.SHROOMLIGHT));
                delivered++;
                particles(ctx, ParticleTypes.LAVA, forge.getX() + 0.5, forge.getY() + 2, forge.getZ() + 0.5, 12, 0.6, 0.1);
                ctx.level().playSound(null, forge, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 1.2f, 0.8f);
                if (delivered >= NEED) {
                    done = true;
                    ctx.level().playSound(null, forge, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 1.5f, 0.5f);
                }
                continue;
            }
            if (p.isShiftKeyDown() && handCooldown.getOrDefault(id, 0L) <= t) {
                for (ServerPlayer q : ps) {
                    if (q != p && !carry.containsKey(q.getUUID()) && p.distanceToSqr(q) <= 9) {
                        carry.put(q.getUUID(), carry.remove(id));
                        handCooldown.put(q.getUUID(), t + 20);
                        handCooldown.put(id, t + 20);
                        bar(ctx, Component.translatable("raid.skyabyss.ER1.handed"));
                        break;
                    }
                }
            }
        }
    }

    @Override
    public boolean isComplete() {
        return done;
    }

    @Override
    public String objectiveKey() {
        return carry.isEmpty() ? "raid.skyabyss.ER1.fetch" : "raid.skyabyss.ER1.deliver";
    }

    @Override
    public int counter() {
        return delivered;
    }

    @Override
    public int counterMax() {
        return NEED;
    }

    // いちばん冷めそうな熾の残り
    @Override
    public int timerTicks() {
        int min = -1;
        for (int v : carry.values()) {
            min = min < 0 ? v : Math.min(min, v);
        }
        return min;
    }

    // 自己テスト用の窓
    int carrying() {
        return carry.size();
    }

    int readyEmbers() {
        return ready;
    }

    BlockPos sourcePos() {
        return source;
    }

    BlockPos forgePos() {
        return forge;
    }

    void forceCool(UUID id, int ticks) {
        carry.computeIfPresent(id, (k, v) -> ticks);
    }

    // 行き先の印: 熾を持っている人がいれば冷えた炉、いなければ噴き口
    @Override
    public BlockPos guideTarget(RaidCtx ctx) {
        if (done || source == null) {
            return null;
        }
        return carry.isEmpty() ? source.above() : forge.above();
    }

    // 噴気に当たった: 熱 25・傷 6 と、噴き口から外への跳ね上げ
    private static void ventBurst(net.minecraft.server.level.ServerLevel level, ServerPlayer p, BlockPos vent) {
        if (p.isCreative()) {
            return;
        }
        jp.revati.skyabyss.titan.ember.back.EmberHeat.add(p, VENT_HEAT, jp.revati.skyabyss.titan.ember.back.EmberHeat.Reason.VENT);
        p.invulnerableTime = 0;
        p.hurt(jp.revati.skyabyss.titan.ember.EmberIds.damage(level, jp.revati.skyabyss.titan.ember.EmberIds.HEAT_BURST, null), VENT_DAMAGE);
        Vec3 away = p.position().subtract(vent.getX() + 0.5, 0, vent.getZ() + 0.5).multiply(1, 0, 1);
        Vec3 push = away.lengthSqr() < 1e-3 ? Vec3.ZERO : away.normalize().scale(0.25);
        p.setDeltaMovement(push.x, 0.65, push.z);
        p.hurtMarked = true;
    }

    private static final float VENT_HEAT = 25f;
    private static final float VENT_DAMAGE = 6f;
}
