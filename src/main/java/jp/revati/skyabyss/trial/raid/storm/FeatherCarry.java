package jp.revati.skyabyss.trial.raid.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 歩 SR1「避雷の羽運び」: 風の吹く銅の甲板の西で光る雷羽 (三枚) を拾い、東の止まり木へ運ぶ。
//  - 持つと少し遅くなり、走れない。持っている人に、一定の拍で雷が落ちる (まず赤い輪の予告。輪の中に立っていると落ちる)
//  - 落ちるとき、輪の中の運び手の近くに「避雷の護符」を持った仲間が立っていれば、雷はその仲間が引き受ける (軽い傷)。
//    一人のときは護符がない代わりに、甲板の「避雷板」(明るい銅の床と針) の近くで受けると、雷は板へ逃げる
//  - 受けてしまった運び手は羽を落とす (羽は元の場所へ戻る)。落とした・倒れた羽も戻る
//  - 風は一定の周期で甲板を横 (南北) へ押す。傷はない。雷羽・護符・板・柱の場所は挑戦ごとに少し変わる
public final class FeatherCarry extends RaidRoom {
    static final int HX = 16;
    static final int HZ = 13;
    static final int H = 9;
    static final int FEATHERS = 3;
    static final String FEATHER_TAG = "raid_feather";
    static final String CHARM_TAG = "raid_charm";
    static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "raid_feather_slow");
    // 雷の拍 (tick)・予告・輪の半径
    static final int BEAT = 140;
    static final int WARN = 34;
    static final double STRIKE_R = 4.0;
    // 避雷板・護符が効く距離 (運び手から)
    static final double PLATE_R = 2.4;
    static final double CHARM_R = 3.6;
    static final float STRIKE_DAMAGE = 7f;
    static final float CHARM_DAMAGE = 3f;
    static final int LINGER = 50;
    // 止まり木 (原点からの相対 x と z)
    static final int PERCH_X = HX - 3;
    static final int[] PERCH_Z = {-7, 0, 7};
    static final int PLATES = 5;
    static final int WIND_PERIOD = 200;

    public enum Outcome {
        MISS, GROUNDED, REDIRECT, HIT
    }

    // 雷が落ちる瞬間の裁き (世界に依存しない)。carrier = 運び手の位置 (輪の中心が落ちる点)、plates・holders = 避雷板と護符を持つ立っている仲間の位置
    public static Outcome resolve(double cx, double cz, double carrierX, double carrierZ, List<double[]> plates, List<double[]> holders) {
        if (Math.hypot(carrierX - cx, carrierZ - cz) > STRIKE_R) {
            return Outcome.MISS;
        }
        for (double[] p : plates) {
            if (Math.hypot(carrierX - p[0], carrierZ - p[1]) <= PLATE_R) {
                return Outcome.GROUNDED;
            }
        }
        for (double[] h : holders) {
            if (Math.hypot(carrierX - h[0], carrierZ - h[1]) <= CHARM_R) {
                return Outcome.REDIRECT;
            }
        }
        return Outcome.HIT;
    }

    private final BlockPos[] home = new BlockPos[FEATHERS];
    private final int[][] homeRel = new int[FEATHERS][];
    private final boolean[] delivered = new boolean[FEATHERS];
    private final int[] lingering = new int[FEATHERS];
    private final List<int[]> plates = new ArrayList<>();
    private final List<int[]> charmHomes = new ArrayList<>();
    private int count;
    private long age;
    private int beat;
    private int strikeTurn;
    private boolean pending;
    private int generation;
    private boolean carried;
    private boolean started;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.STORM));
        BlockPos o = c.origin();
        BlockState gleam = Blocks.WAXED_COPPER_BLOCK.defaultBlockState();
        BlockState cut = Blocks.WAXED_CUT_COPPER.defaultBlockState();
        // 止まり木: 東の壁ぎわの三つの台 (3×3 の壇と、真ん中の消えた電球)
        for (int pz : PERCH_Z) {
            RaidKit.fill(level, o.offset(PERCH_X - 1, 1, pz - 1), o.offset(PERCH_X + 1, 1, pz + 1), cut);
            setPerch(ctx, pz, false);
            RaidKit.set(level, o.offset(PERCH_X, 3, pz), Blocks.LIGHTNING_ROD.defaultBlockState());
            RaidKit.set(level, o.offset(PERCH_X, 4, pz), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        // 雷羽の戻る場所: 西の三つの帯
        int[] bands = {-8, 0, 8};
        for (int i = 0; i < FEATHERS; i++) {
            int x = -HX + 3 + ctx.rng().nextInt(5);
            int z = bands[i] + ctx.rng().nextInt(5) - 2;
            home[i] = c.floor(x, z);
            homeRel[i] = new int[] {x, z};
            // 羽の台 (小さな銅の円)
            RaidKit.fill(level, o.offset(x - 1, 0, z - 1), o.offset(x + 1, 0, z + 1), gleam);
            RaidKit.set(level, o.offset(x, 0, z), Blocks.OCHRE_FROGLIGHT.defaultBlockState());
        }
        // 避雷板: 甲板の途中に点々と (明るい 3×3 の床と針)
        int[] px = {-8, -3, 2, 7, 10};
        for (int i = 0; i < PLATES; i++) {
            int x = px[i] + ctx.rng().nextInt(3) - 1;
            int z = ctx.rng().nextInt(2 * HZ - 7) - HZ + 3;
            if (Math.abs(z - PERCH_Z[0]) < 3 && x > PERCH_X - 4 || Math.abs(z - PERCH_Z[1]) < 3 && x > PERCH_X - 4 || Math.abs(z - PERCH_Z[2]) < 3 && x > PERCH_X - 4) {
                z = z < 0 ? -HZ + 3 : HZ - 3;
            }
            plates.add(new int[] {x, z});
            RaidKit.fill(level, o.offset(x - 1, 0, z - 1), o.offset(x + 1, 0, z + 1), gleam);
            RaidKit.set(level, o.offset(x, 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
            RaidKit.set(level, o.offset(x, 2, z), Blocks.LIGHTNING_ROD.defaultBlockState());
        }
        // 護符の場所: 部屋の真ん中の西よりに、仲間の数だけ (湧くのは start)
        for (int i = 0; i < 3; i++) {
            charmHomes.add(new int[] {-HX + 6, -3 + 3 * i});
        }
        // 飾り: 銅の柱 (二枚の風見の羽つき)。板・羽の台・止まり木・通り道から離して
        int tries = 0;
        int pillars = 0;
        while (pillars < 4 && tries++ < 80) {
            int x = ctx.rng().nextInt(2 * HX - 5) - HX + 2;
            int z = ctx.rng().nextInt(2 * HZ - 5) - HZ + 2;
            if (clearOf(x, z, 4)) {
                pillars++;
                RaidKit.fill(level, o.offset(x, 1, z), o.offset(x + 1, H - 3, z + 1), cut);
                RaidKit.fill(level, o.offset(x, H - 2, z), o.offset(x + 1, H - 2, z + 1), Blocks.WAXED_CHISELED_COPPER.defaultBlockState());
                RaidKit.set(level, o.offset(x, H - 1, z), Blocks.LIGHTNING_ROD.defaultBlockState());
            }
        }
    }

    // 置いてあるもの (羽・板・止まり木・護符) から r ブロック以上離れた (x, z) か。南の戸の前の通り道 (x ≈ 0 の南寄り) も避ける
    private boolean clearOf(int x, int z, int r) {
        for (int[] h : homeRel) {
            if (Math.abs(h[0] - x) < r && Math.abs(h[1] - z) < r) {
                return false;
            }
        }
        for (int[] p : plates) {
            if (Math.abs(p[0] - x) < r && Math.abs(p[1] - z) < r) {
                return false;
            }
        }
        for (int pz : PERCH_Z) {
            if (Math.abs(PERCH_X - x) < r + 1 && Math.abs(pz - z) < r) {
                return false;
            }
        }
        for (int[] ch : charmHomes) {
            if (Math.abs(ch[0] - x) < r && Math.abs(ch[1] - z) < r) {
                return false;
            }
        }
        return !(Math.abs(x) < 4 && z > HZ - 8);
    }

    private void setPerch(RaidCtx ctx, int pz, boolean on) {
        BlockState bulb = Blocks.WAXED_COPPER_BULB.defaultBlockState().setValue(BlockStateProperties.LIT, on);
        RaidKit.set(ctx.level(), ctx.origin().offset(PERCH_X, 2, pz), bulb);
    }

    @Override
    public void start(RaidCtx ctx) {
        started = true;
        age = 0;
        beat = 0;
        pending = false;
        for (int i = 0; i < FEATHERS; i++) {
            if (!delivered[i]) {
                spawnFeather(ctx, i);
            }
        }
        // 護符: 仲間が二人以上いるとき、人数 − 1 個。一人なら避雷板だけ
        for (int i = 0; i < Math.min(3, ctx.scale() - 1); i++) {
            spawnCharm(ctx, i);
        }
    }

    @Override
    public void tick(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        age++;
        wind(ctx);
        boolean[] located = new boolean[FEATHERS];
        List<ServerPlayer> carriers = new ArrayList<>();
        for (ServerPlayer p : ctx.players()) {
            boolean has = false;
            boolean down = Downed.isDowned(p);
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                int idx = featherIndex(p.getInventory().getItem(slot));
                if (idx < 0) {
                    continue;
                }
                if (down || has || idx >= FEATHERS || delivered[idx] || located[idx]) {
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
        // 床の羽: 元の場所から離れて落ちたまま LINGER たつと戻る
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, ctx.cell().inner().inflate(2))) {
            int idx = featherIndex(e.getItem());
            if (idx < 0) {
                continue;
            }
            if (idx >= FEATHERS || delivered[idx] || located[idx]) {
                e.discard();
                continue;
            }
            located[idx] = true;
            boolean atHome = e.distanceToSqr(home[idx].getX() + 0.5, home[idx].getY() + 0.2, home[idx].getZ() + 0.5) < 2.5 * 2.5;
            if (!atHome && e.onGround() && ++lingering[idx] >= LINGER) {
                e.discard();
                located[idx] = false;
            }
            if (atHome) {
                lingering[idx] = 0;
            }
        }
        for (int i = 0; i < FEATHERS; i++) {
            if (!delivered[i] && !located[i] && started) {
                spawnFeather(ctx, i);
            }
        }
        // 止まり木: 運び手が近くへ来たら、空いている台へ置く
        for (ServerPlayer p : carriers) {
            int best = -1;
            double bd = 3.0 * 3.0;
            for (int k = 0; k < PERCH_Z.length; k++) {
                if (perchTaken[k]) {
                    continue;
                }
                double dx = p.getX() - (ctx.origin().getX() + PERCH_X + 0.5);
                double dz = p.getZ() - (ctx.origin().getZ() + PERCH_Z[k] + 0.5);
                double d = dx * dx + dz * dz;
                if (d <= bd && Math.abs(p.getY() - (ctx.origin().getY() + 1)) < 3) {
                    bd = d;
                    best = k;
                }
            }
            if (best >= 0) {
                for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                    int idx = featherIndex(p.getInventory().getItem(slot));
                    if (idx >= 0 && idx < FEATHERS && !delivered[idx]) {
                        p.getInventory().setItem(slot, ItemStack.EMPTY);
                        applyCarry(p, false);
                        perch(ctx, idx, best);
                        break;
                    }
                }
            }
        }
        // 雷の拍: 羽を持つ人がいる間、一定の間隔で運び手へ
        if (!pending && !carriers.isEmpty() && !isComplete()) {
            if (++beat >= BEAT) {
                beat = 0;
                strike(ctx, carriers.get(strikeTurn++ % carriers.size()));
            }
        } else if (carriers.isEmpty()) {
            beat = Math.min(beat, BEAT / 2);
        }
    }

    private final boolean[] perchTaken = new boolean[PERCH_Z.length];

    // 台へ羽を置いた (idx = 羽、k = 台)
    private void perch(RaidCtx ctx, int idx, int k) {
        delivered[idx] = true;
        perchTaken[k] = true;
        count++;
        setPerch(ctx, PERCH_Z[k], true);
        BlockPos at = ctx.origin().offset(PERCH_X, 3, PERCH_Z[k]);
        ctx.level().playSound(null, at, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.2f, 0.6f + 0.2f * count);
        ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, at.getX() + 0.5, at.getY() + 1.4, at.getZ() + 0.5, 26, 0.3, 0.5, 0.3, 0.08);
    }

    // 試験用: 羽 idx を、空いている最初の台へ置いたことにする
    public void deliverForTest(RaidCtx ctx, int idx) {
        for (int k = 0; k < PERCH_Z.length; k++) {
            if (!perchTaken[k] && !delivered[idx]) {
                perch(ctx, idx, k);
                return;
            }
        }
    }

    // ---- 雷
    private void strike(RaidCtx ctx, ServerPlayer carrier) {
        ServerLevel level = ctx.level();
        double cx = carrier.getX();
        double cz = carrier.getZ();
        UUID who = carrier.getUUID();
        int gen = generation;
        pending = true;
        Telegraph.in(level, TelegraphShape.circle(cx, cz, STRIKE_R))
                .floor(carrier.getY()).warn(WARN).danger().target(carrier)
                .startSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6f, 1.6f)
                .victims(p -> false)
                .onImpact(lvl -> {
                    if (gen != generation) {
                        return;
                    }
                    pending = false;
                    land(ctx, who, cx, cz);
                })
                .start();
    }

    private void land(RaidCtx ctx, UUID who, double cx, double cz) {
        ServerLevel level = ctx.level();
        ServerPlayer carrier = null;
        List<double[]> holders = new ArrayList<>();
        List<ServerPlayer> holderPlayers = new ArrayList<>();
        for (ServerPlayer p : ctx.players()) {
            if (p.getUUID().equals(who)) {
                carrier = p;
            }
        }
        for (ServerPlayer p : ctx.standing()) {
            if (!p.getUUID().equals(who) && hasCharm(p)) {
                holders.add(new double[] {p.getX(), p.getZ()});
                holderPlayers.add(p);
            }
        }
        List<double[]> plateSpots = new ArrayList<>();
        for (int[] pl : plates) {
            plateSpots.add(new double[] {ctx.origin().getX() + pl[0] + 0.5, ctx.origin().getZ() + pl[1] + 0.5});
        }
        boolean has = carrier != null && !Downed.isDowned(carrier) && hasFeather(carrier);
        Outcome out = has ? resolve(cx, cz, carrier.getX(), carrier.getZ(), plateSpots, holders) : Outcome.MISS;
        Vec3 ground = new Vec3(cx, ctx.origin().getY() + 1, cz);
        switch (out) {
            case MISS -> {
                StormRaid.bolt(level, ground);
                StormRaid.zap(level, BlockPos.containing(ground), 1.5f, 1.2f);
            }
            case GROUNDED -> {
                double[] best = null;
                for (double[] pl : plateSpots) {
                    if (best == null || Math.hypot(carrier.getX() - pl[0], carrier.getZ() - pl[1]) < Math.hypot(carrier.getX() - best[0], carrier.getZ() - best[1])) {
                        best = pl;
                    }
                }
                Vec3 at = new Vec3(best[0], ctx.origin().getY() + 3, best[1]);
                StormRaid.bolt(level, at);
                StormRaid.sparks(level, at.add(0, -1, 0), 40, 0.8);
                StormRaid.zap(level, BlockPos.containing(at), 1.5f, 1.4f);
                ctx.say(Component.translatable("message.skyabyss.raid.SR1.grounded"));
            }
            case REDIRECT -> {
                ServerPlayer take = null;
                for (ServerPlayer h : holderPlayers) {
                    if (take == null || Math.hypot(carrier.getX() - h.getX(), carrier.getZ() - h.getZ()) < Math.hypot(carrier.getX() - take.getX(), carrier.getZ() - take.getZ())) {
                        take = h;
                    }
                }
                StormRaid.bolt(level, take.position());
                StormRaid.sparks(level, take.position().add(0, 1, 0), 30, 0.5);
                take.hurt(level.damageSources().lightningBolt(), CHARM_DAMAGE);
                ctx.say(Component.translatable("message.skyabyss.raid.SR1.charm", take.getDisplayName()));
            }
            case HIT -> {
                StormRaid.bolt(level, carrier.position());
                StormRaid.sparks(level, carrier.position().add(0, 1, 0), 30, 0.5);
                carrier.hurt(level.damageSources().lightningBolt(), STRIKE_DAMAGE);
                dropFeather(ctx, carrier);
                ctx.say(Component.translatable("message.skyabyss.raid.SR1.struck"));
            }
        }
    }

    // 受けた運び手の羽は、元の場所へ戻る
    private void dropFeather(RaidCtx ctx, ServerPlayer p) {
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            int idx = featherIndex(p.getInventory().getItem(slot));
            if (idx >= 0) {
                p.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
        applyCarry(p, false);
    }

    // ---- 風 (南北へ押す。傷はない)
    private void wind(RaidCtx ctx) {
        int dir = (age / WIND_PERIOD) % 2 == 0 ? 1 : -1;
        ServerLevel level = ctx.level();
        if (age % 3 == 0) {
            for (int i = 0; i < 3; i++) {
                double x = ctx.origin().getX() + ctx.rng().nextInt(2 * HX - 1) - HX + 0.5;
                double z = ctx.origin().getZ() + ctx.rng().nextInt(2 * HZ - 1) - HZ + 0.5;
                double y = ctx.origin().getY() + 1 + ctx.rng().nextInt(3);
                level.sendParticles(ParticleTypes.CLOUD, x, y, z, 0, 0, 0, dir * 0.5, 1.0);
            }
        }
        for (ServerPlayer p : ctx.players()) {
            if (!Downed.isDowned(p) && ctx.cell().inArena(p.getX(), p.getZ())) {
                p.setDeltaMovement(p.getDeltaMovement().add(0, 0, dir * 0.018));
                p.hurtMarked = true;
            }
        }
    }

    @Override
    public boolean isComplete() {
        return count >= FEATHERS;
    }

    @Override
    public void reset(RaidCtx ctx) {
        cleanup(ctx);
        generation++;
        pending = false;
        for (int i = 0; i < FEATHERS; i++) {
            delivered[i] = false;
            lingering[i] = 0;
        }
        for (int k = 0; k < PERCH_Z.length; k++) {
            perchTaken[k] = false;
            setPerch(ctx, PERCH_Z[k], false);
        }
        count = 0;
        beat = 0;
        started = false;
    }

    @Override
    public void end(RaidCtx ctx) {
        generation++;
        cleanup(ctx);
    }

    @Override
    public String objectiveKey() {
        return carried ? "raid.skyabyss.SR1.carrying" : "raid.skyabyss.SR1.carry";
    }

    @Override
    public int counter() {
        return count;
    }

    @Override
    public int counterMax() {
        return FEATHERS;
    }

    // 次の雷までの tick (羽を持つ人がいる間だけ)
    @Override
    public int timerTicks() {
        return carried && !pending ? BEAT - beat : -1;
    }

    // ---- 中身
    private void cleanup(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        for (ServerPlayer p : ctx.players()) {
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                ItemStack st = p.getInventory().getItem(slot);
                if (featherIndex(st) >= 0 || isCharm(st)) {
                    p.getInventory().setItem(slot, ItemStack.EMPTY);
                }
            }
            applyCarry(p, false);
        }
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, ctx.cell().bounds())) {
            if (featherIndex(e.getItem()) >= 0 || isCharm(e.getItem())) {
                e.discard();
            }
        }
    }

    static int featherIndex(ItemStack st) {
        if (st.isEmpty() || !st.is(Items.FEATHER)) {
            return -1;
        }
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.contains(FEATHER_TAG) ? cd.copyTag().getInt(FEATHER_TAG) : -1;
    }

    static boolean isCharm(ItemStack st) {
        if (st.isEmpty() || !st.is(Items.LIGHTNING_ROD)) {
            return false;
        }
        CustomData cd = st.get(DataComponents.CUSTOM_DATA);
        return cd != null && cd.contains(CHARM_TAG);
    }

    private static boolean hasCharm(ServerPlayer p) {
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            if (isCharm(p.getInventory().getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFeather(ServerPlayer p) {
        for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
            if (featherIndex(p.getInventory().getItem(slot)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private void spawnFeather(RaidCtx ctx, int idx) {
        ItemStack st = new ItemStack(Items.FEATHER);
        CompoundTag tag = new CompoundTag();
        tag.putInt(FEATHER_TAG, idx);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        st.set(DataComponents.CUSTOM_NAME, Component.translatable("item.skyabyss.raid_lightning_feather"));
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        BlockPos h = home[idx];
        ItemEntity e = new ItemEntity(ctx.level(), h.getX() + 0.5, h.getY() + 0.3, h.getZ() + 0.5, st);
        e.setDeltaMovement(0, 0, 0);
        e.setUnlimitedLifetime();
        e.setInvulnerable(true);
        e.setGlowingTag(true);
        e.setPickUpDelay(10);
        ctx.level().addFreshEntity(e);
        lingering[idx] = 0;
        ctx.level().sendParticles(ParticleTypes.ELECTRIC_SPARK, h.getX() + 0.5, h.getY() + 0.8, h.getZ() + 0.5, 14, 0.3, 0.5, 0.3, 0.05);
    }

    private void spawnCharm(RaidCtx ctx, int i) {
        ItemStack st = new ItemStack(Items.LIGHTNING_ROD);
        CompoundTag tag = new CompoundTag();
        tag.putInt(CHARM_TAG, i);
        st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        st.set(DataComponents.CUSTOM_NAME, Component.translatable("item.skyabyss.raid_lightning_charm"));
        st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        int[] at = charmHomes.get(i);
        BlockPos p = ctx.cell().floor(at[0], at[1]);
        ItemEntity e = new ItemEntity(ctx.level(), p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, st);
        e.setDeltaMovement(0, 0, 0);
        e.setUnlimitedLifetime();
        e.setInvulnerable(true);
        e.setGlowingTag(true);
        e.setPickUpDelay(10);
        ctx.level().addFreshEntity(e);
    }

    // 羽を持つ人は遅く、走れない
    private void applyCarry(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        if (on) {
            if (!speed.hasModifier(SLOW_ID)) {
                speed.addTransientModifier(new AttributeModifier(SLOW_ID, -0.25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            p.setSprinting(false);
        } else {
            speed.removeModifier(SLOW_ID);
        }
    }

    // 試験用
    @Nullable
    public BlockPos home(int i) {
        return home[i];
    }

    public List<int[]> plates() {
        return plates;
    }

    public AABB perchBox(RaidCtx ctx) {
        return new AABB(ctx.origin()).inflate(HX, 4, HZ);
    }

    // 行き先の印: 運んでいる間は空いている止まり木 (運び手にいちばん近い台)、誰も運んでいなければ、まだ届けていない羽の場所
    @Override
    @Nullable
    public BlockPos guideTarget(RaidCtx ctx) {
        if (isComplete()) {
            return null;
        }
        if (carried) {
            ServerPlayer carrier = null;
            for (ServerPlayer p : ctx.standing()) {
                if (hasFeather(p)) {
                    carrier = p;
                    break;
                }
            }
            int best = -1;
            double bd = Double.MAX_VALUE;
            for (int k = 0; k < PERCH_Z.length; k++) {
                if (perchTaken[k]) {
                    continue;
                }
                double d = carrier == null ? k : Math.hypot(carrier.getX() - (ctx.origin().getX() + PERCH_X + 0.5), carrier.getZ() - (ctx.origin().getZ() + PERCH_Z[k] + 0.5));
                if (d < bd) {
                    bd = d;
                    best = k;
                }
            }
            return best < 0 ? null : ctx.origin().offset(PERCH_X, 2, PERCH_Z[best]);
        }
        for (int i = 0; i < FEATHERS; i++) {
            if (!delivered[i]) {
                return home[i].above();
            }
        }
        return null;
    }

    // 一人の台本用: 雷の予告が出ているか (赤い輪が見えている)
    public boolean strikePending() {
        return pending;
    }

    public boolean delivered(int i) {
        return delivered[i];
    }
}
