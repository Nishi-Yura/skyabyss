package jp.revati.skyabyss.trial.raid.whale;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.registry.ModItems;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

// 歩 WR1「骨玉運び」: 光る骨玉 (部屋のあちこちに三つ) を、北の台座へ運ぶ。
//  - 持つ (持ち物に入る) と遅くなり、跳べない。一人が一つまで。敵は持っている人を狙う
//  - 落とした・倒れた骨玉は元の場所へ戻る。台座の近くで持っていると、置いたことになる (台座の窪みが灯る)
//  - 一人でも運べるが、手分けして同時に運ぶと早い (敵の目が分かれる)。骨玉の場所・遮りの柱・敵の出る所は、挑戦ごとに少し変わる
public final class WhaleCarryRoom extends RaidRoom {
    static final int HX = 16;
    static final int HZ = 16;
    static final int H = 10;
    static final int ORBS = 3;
    static final String ORB_TAG = "raid_orb";
    static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "raid_orb_slow");
    static final ResourceLocation JUMP_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "raid_orb_jump");
    // 台座の位置 (原点からの相対 z)。窪みは x = -3・0・3
    static final int ALTAR_Z = -HZ + 3;
    static final int[] SOCKET_X = {-3, 0, 3};
    // 戻すまでの、置かれた場所に落ちている長さ
    static final int LINGER = 50;

    private final BlockPos[] home = new BlockPos[ORBS];
    private final boolean[] delivered = new boolean[ORBS];
    private final int[] lingering = new int[ORBS];
    private final List<AABB> pillars = new ArrayList<>();
    private int count;
    private long nextSpawn;
    private boolean started;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.BONE));
        BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
        // 台座: 北の壁ぎわの低い壇と、三つの窪み
        RaidKit.fill(level, c.origin().offset(-5, 1, ALTAR_Z - 1), c.origin().offset(5, 1, ALTAR_Z + 1), Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int sx : SOCKET_X) {
            RaidKit.set(level, c.origin().offset(sx, 1, ALTAR_Z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        RaidKit.fill(level, c.origin().offset(-5, 2, ALTAR_Z - 1), c.origin().offset(-5, 4, ALTAR_Z - 1), bone);
        RaidKit.fill(level, c.origin().offset(5, 2, ALTAR_Z - 1), c.origin().offset(5, 4, ALTAR_Z - 1), bone);
        // 遮りの柱 (2×2、高さ 4)。台座と南の戸の前、中央の通り道を避けて 4 本
        int tries = 0;
        while (pillars.size() < 4 && tries++ < 80) {
            int x = ctx.rng().nextInt(2 * HX - 7) - HX + 3;
            int z = ctx.rng().nextInt(2 * HZ - 13) - HZ + 7;
            if (Math.abs(x) < 4 && z > HZ - 8) {
                continue;
            }
            AABB box = new AABB(x, 1, z, x + 2, 5, z + 2);
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
            RaidKit.fill(level, c.origin().offset(x, 1, z), c.origin().offset(x + 1, 3, z + 1), bone);
            RaidKit.set(level, c.origin().offset(x, 4, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            RaidKit.set(level, c.origin().offset(x + 1, 4, z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            RaidKit.set(level, c.origin().offset(x, 4, z + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
            RaidKit.set(level, c.origin().offset(x + 1, 4, z + 1), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
        // 骨玉の戻る場所: 台座から離れた三つの帯 (左・中・右) に一つずつ
        int[] bands = {-11, 0, 11};
        for (int i = 0; i < ORBS; i++) {
            BlockPos at = null;
            for (int k = 0; k < 40 && at == null; k++) {
                int x = bands[i] + ctx.rng().nextInt(7) - 3;
                int z = -2 + ctx.rng().nextInt(13);
                BlockPos p = c.floor(Math.max(-HX + 2, Math.min(HX - 2, x)), z);
                if (!nearPillar(ctx, p, 2)) {
                    at = p;
                }
            }
            home[i] = at == null ? c.floor(bands[i], 4) : at;
        }
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        for (int i = 0; i < ORBS; i++) {
            if (!delivered[i]) {
                spawnOrb(ctx, i);
            }
        }
        for (int i = 0; i < 2 + ctx.scale(); i++) {
            spawnFoe(ctx);
        }
        nextSpawn = ctx.now() + 200;
    }

    @Override
    public void tick(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        boolean[] located = new boolean[ORBS];
        List<ServerPlayer> carriers = new ArrayList<>();
        // 持ち物の骨玉: 一人一つ・倒れたら手放す・済んだ骨玉は消す
        for (ServerPlayer p : ctx.players()) {
            boolean has = false;
            boolean down = Downed.isDowned(p);
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                int idx = orbIndex(p.getInventory().getItem(slot));
                if (idx < 0) {
                    continue;
                }
                if (down || has || idx >= ORBS || delivered[idx] || located[idx]) {
                    p.getInventory().setItem(slot, ItemStack.EMPTY);
                    continue;
                }
                has = true;
                located[idx] = true;
            }
            applyCarry(p, has && !down);
            if (has && !down) {
                carriers.add(p);
            }
        }
        carried = !carriers.isEmpty();
        // 床の骨玉: 元の場所から離れて落ちたまま LINGER たつと戻る
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, ctx.cell().inner().inflate(2))) {
            int idx = orbIndex(e.getItem());
            if (idx < 0) {
                continue;
            }
            if (idx >= ORBS || delivered[idx] || located[idx]) {
                e.discard();
                continue;
            }
            located[idx] = true;
            e.setNoGravity(false);
            boolean atHome = e.distanceToSqr(home[idx].getX() + 0.5, home[idx].getY() + 0.2, home[idx].getZ() + 0.5) < 2.5 * 2.5;
            if (!atHome && e.onGround() && ++lingering[idx] >= LINGER) {
                e.discard();
                located[idx] = false;
                returned(ctx, idx);
            }
            if (atHome) {
                lingering[idx] = 0;
            }
        }
        // どこにもない骨玉は元の場所へ
        for (int i = 0; i < ORBS; i++) {
            if (!delivered[i] && !located[i]) {
                spawnOrb(ctx, i);
            }
        }
        // 台座: 持っている人が近くに来たら置く
        double ax = ctx.origin().getX() + 0.5;
        double az = ctx.origin().getZ() + ALTAR_Z + 0.5;
        for (ServerPlayer p : carriers) {
            double dx = p.getX() - ax;
            double dz = p.getZ() - az;
            if (dx * dx + dz * dz <= 3.2 * 3.2 && Math.abs(p.getY() - (ctx.origin().getY() + 1)) < 3) {
                for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                    int idx = orbIndex(p.getInventory().getItem(slot));
                    if (idx >= 0 && idx < ORBS && !delivered[idx]) {
                        p.getInventory().setItem(slot, ItemStack.EMPTY);
                        place(ctx, idx);
                        applyCarry(p, false);
                        break;
                    }
                }
            }
        }
        // 敵: 骨玉を持つ人を狙う。ときどき湧く
        if (ctx.now() % 10 == 0 && !carriers.isEmpty()) {
            for (Mob m : ctx.aliveMobs()) {
                ServerPlayer best = null;
                for (ServerPlayer p : carriers) {
                    if (best == null || p.distanceToSqr(m) < best.distanceToSqr(m)) {
                        best = p;
                    }
                }
                if (best != null && m.getTarget() != best) {
                    m.setTarget(best);
                }
            }
        }
        if (started && count < ORBS && ctx.now() >= nextSpawn) {
            nextSpawn = ctx.now() + 160;
            if (ctx.aliveMobs().size() < 5 + 2 * ctx.scale()) {
                for (int i = 0; i < 1 + ctx.scale() / 2; i++) {
                    spawnFoe(ctx);
                }
            }
        }
    }

    @Override
    public boolean isComplete() {
        return count >= ORBS;
    }

    @Override
    public void reset(RaidCtx ctx) {
        cleanup(ctx);
        for (int i = 0; i < ORBS; i++) {
            delivered[i] = false;
            lingering[i] = 0;
        }
        count = 0;
        started = false;
        for (int sx : SOCKET_X) {
            RaidKit.set(ctx.level(), ctx.origin().offset(sx, 1, ALTAR_Z), Blocks.CHISELED_STONE_BRICKS.defaultBlockState());
        }
    }

    @Override
    public void end(RaidCtx ctx) {
        cleanup(ctx);
    }

    @Override
    public String objectiveKey() {
        return carried ? "raid.skyabyss.WR1.carrying" : "raid.skyabyss.WR1.carry";
    }

    private boolean carried;

    @Override
    public int counter() {
        return count;
    }

    @Override
    public int counterMax() {
        return ORBS;
    }

    // ---- 中身
    private void cleanup(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        for (ServerPlayer p : ctx.players()) {
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                if (orbIndex(p.getInventory().getItem(slot)) >= 0) {
                    p.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            applyCarry(p, false);
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, ctx.cell().bounds())) {
            if (orbIndex(e.getItem()) >= 0) {
                e.discard();
            }
        }
    }

    private static int orbIndex(ItemStack st) {
        if (st.isEmpty() || !st.is(ModItems.RAID_BONE_ORB.get())) {
            return -1;
        }
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.contains(ORB_TAG) ? cd.copyTag().getInt(ORB_TAG) : -1;
    }

    private void spawnOrb(RaidCtx ctx, int idx) {
        ItemStack st = new ItemStack(ModItems.RAID_BONE_ORB.get());
        CompoundTag tag = new CompoundTag();
        tag.putInt(ORB_TAG, idx);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        BlockPos h = home[idx];
        ItemEntity e = new ItemEntity(ctx.level(), h.getX() + 0.5, h.getY() + 0.3, h.getZ() + 0.5, st);
        e.setDeltaMovement(0, 0, 0);
        e.setUnlimitedLifetime();
        e.setInvulnerable(true);
        e.setGlowingTag(true);
        e.setPickUpDelay(10);
        ctx.level().addFreshEntity(e);
        lingering[idx] = 0;
        ctx.level().sendParticles(ParticleTypes.END_ROD, h.getX() + 0.5, h.getY() + 0.8, h.getZ() + 0.5, 12, 0.3, 0.5, 0.3, 0.02);
    }

    private void returned(RaidCtx ctx, int idx) {
        spawnOrb(ctx, idx);
        ctx.level().playSound(null, home[idx], SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    private void place(RaidCtx ctx, int idx) {
        delivered[idx] = true;
        count++;
        BlockPos at = ctx.origin().offset(SOCKET_X[idx], 1, ALTAR_Z);
        RaidKit.set(ctx.level(), at, Blocks.SEA_LANTERN.defaultBlockState());
        ctx.level().playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f + 0.2f * count);
        ctx.level().sendParticles(ParticleTypes.END_ROD, at.getX() + 0.5, at.getY() + 1.2, at.getZ() + 0.5, 20, 0.3, 0.4, 0.3, 0.04);
    }

    private void spawnFoe(RaidCtx ctx) {
        BlockPos at = null;
        for (int k = 0; k < 12 && at == null; k++) {
            BlockPos p = RaidKit.randomSpot(ctx, 2);
            boolean near = false;
            for (ServerPlayer pl : ctx.players()) {
                if (pl.distanceToSqr(p.getX() + 0.5, p.getY(), p.getZ() + 0.5) < 8 * 8) {
                    near = true;
                }
            }
            if (nearPillar(ctx, p, 1)) {
                near = true;
            }
            if (!near) {
                at = p;
            }
        }
        if (at == null) {
            return;
        }
        int r = ctx.rng().nextInt(5);
        ctx.spawnGuard(r == 0 ? "archer" : r == 1 ? "crawler" : "sentinel", at);
    }

    private boolean nearPillar(RaidCtx ctx, BlockPos p, int margin) {
        double lx = p.getX() + 0.5 - ctx.origin().getX();
        double lz = p.getZ() + 0.5 - ctx.origin().getZ();
        for (AABB b : pillars) {
            if (b.inflate(margin, 0, margin).contains(lx, 2, lz)) {
                return true;
            }
        }
        return false;
    }

    // 骨玉を持つ人は遅く、跳べない
    private void applyCarry(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (speed == null || jump == null) {
            return;
        }
        if (on) {
            if (!speed.hasModifier(SLOW_ID)) {
                speed.addTransientModifier(new AttributeModifier(SLOW_ID, -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (!jump.hasModifier(JUMP_ID)) {
                jump.addTransientModifier(new AttributeModifier(JUMP_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            p.setSprinting(false);
        } else {
            speed.removeModifier(SLOW_ID);
            jump.removeModifier(JUMP_ID);
        }
    }

    // 試験用
    public int delivered() {
        return count;
    }

    @Nullable
    public BlockPos home(int i) {
        return home[i];
    }

    public BlockPos altar(RaidCtx ctx) {
        return ctx.origin().offset(0, 1, ALTAR_Z);
    }
}
