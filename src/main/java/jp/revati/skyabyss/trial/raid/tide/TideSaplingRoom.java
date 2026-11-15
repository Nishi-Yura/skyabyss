package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.tide.mob.ReefScuttler;
import jp.revati.skyabyss.titan.tide.mob.TideMobSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

// 闘 TR3「珊瑚の苗を守る」: 三本の珊瑚の苗を、三つの波の潮の敵から守る。敵は潮と一緒に来て、苗をかじる。
//  - 波ごとに部屋に本当に潮が満ちる (南の端から北へ、低い床が水に沈む)。最初の敵は潮の先に乗って南から来る。波を退けると潮が引く
//  - 潮のあいだ、人は水に足を取られて遅く、磯走りは潮に乗って速い。乾いているのは苗の台と四つの岩場 (一段高い)
//  - 苗は体力を持つ (HUD の二つ目の数)。かじられると赤い粒が出て、苗が縮む。人が近くにいる敵は人を相手にする
//  - 全部の波を退けて、苗が一本でも残れば越える。三本とも失えば、部屋は最初からやり直し
//  - 敵は磯走り (苗をかじる) と泡吹き (人を撃つ)
//  - 闘の試練では (32 の 9.5) 波のあと床が砂になり、潮招きが砂から現れる。招き潮のあいだの逃げ場は苗の台 (一段上げる。苗はそのまま)
public final class TideSaplingRoom extends RaidRoom {
    static final int HX = 14;
    static final int HZ = 14;
    static final int H = 9;
    static final int SAPLINGS = 3;
    static final int SAPLING_HP = 40;
    static final int WAVES = 3;
    static final int BITE_EVERY = 25;
    static final double BITE_R = 2.0;
    static final int GAP_TICKS = 100;
    static final int BATCH_TICKS = 50;
    // 苗の位置 (原点からの相対)
    static final int[][] AT = {{-8, -3}, {0, -7}, {8, -3}};
    // 敵の出る所 (壁ぎわの四つの珊瑚の巣穴)
    static final int[][] DEN = {{-12, -11}, {12, -11}, {-12, 6}, {12, 6}};
    // 岩場 (2×2、一段高い。潮のあいだも乾いている)
    static final int[][] ROCK = {{-5, 1}, {4, 1}, {-9, 9}, {8, 9}};
    // 潮の満ちる所: 床の一段上 (y = 1) の |z| <= FLOOD_Z。その外の一列 (z = ±(HZ - 1)) は縁石の板で、水を戸へ流さない
    static final int FLOOD_Z = HZ - 2;
    // 潮に乗った磯走りの速さ (乾いているときは 1.1)
    static final double SWIFT = 1.5;

    private final int[] hp = new int[SAPLINGS];
    private int wave;
    private int toSpawn;
    private int spawnedInWave;
    private long nextBatch;
    private long gapUntil;
    private boolean betweenWaves;
    private int lost;
    // 潮: 満ちた列の数 (南から)。-1 = 引いている
    private int floodRows = -1;
    private final java.util.Map<BlockPos, net.minecraft.world.level.block.state.BlockState> drowned = new java.util.HashMap<>();

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, TideBuild.PALETTE));
        for (int i = 0; i < SAPLINGS; i++) {
            // 苗床: 3×3 の台 (縁は石英、芯は暗い海晶)
            RaidKit.fill(level, c.origin().offset(AT[i][0] - 1, 1, AT[i][1] - 1), c.origin().offset(AT[i][0] + 1, 1, AT[i][1] + 1), Blocks.SMOOTH_QUARTZ.defaultBlockState());
            RaidKit.set(level, c.origin().offset(AT[i][0], 1, AT[i][1]), Blocks.DARK_PRISMARINE.defaultBlockState());
        }
        // 岩場: 一段高い暗い海晶 (潮のあいだの足場)
        for (int[] r : ROCK) {
            RaidKit.fill(level, c.origin().offset(r[0], 1, r[1]), c.origin().offset(r[0] + 1, 1, r[1] + 1), Blocks.DARK_PRISMARINE.defaultBlockState());
        }
        // 縁石: 南北の端の一列を板にする (潮を入口・出口の戸へ流さない。歩いて越えられる)
        for (int x = -HX; x <= HX; x++) {
            RaidKit.set(level, c.origin().offset(x, 1, HZ - 1), Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState());
            RaidKit.set(level, c.origin().offset(x, 1, -HZ + 1), Blocks.PRISMARINE_BRICK_SLAB.defaultBlockState());
        }
        // 巣穴: 灰色の珊瑚のこぶ (2×2、低い)
        for (int[] d : DEN) {
            RaidKit.fill(level, c.origin().offset(d[0], 1, d[1]), c.origin().offset(d[0] + 1, 1, d[1] + 1), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
            RaidKit.set(level, c.origin().offset(d[0], 2, d[1]), Blocks.AIR.defaultBlockState());
        }
        dress(ctx);
        resetSaplings(ctx);
    }

    // 飾り (docs/design/39 の 4.2「守る 苗」): 苗ごとに床の桃の輪 (半径 3) と天井から垂れる珊瑚の天蓋 (下に灯り = 部屋で一番明るい所)。
    // 床には守る物を囲む巻貝の渦の線 (平らな模様。通り道は塞がない)。巣穴は壁ぎわの暗い珊瑚の庇。苗のまわり半径 3 と巣穴のまわりは空ける
    private void dress(RaidCtx ctx) {
        TideBuild d = new TideBuild(ctx);
        for (int[] a : AT) {
            d.keep(a[0] - 3, 1, a[1] - 3, a[0] + 3, H - 3, a[1] + 3);
        }
        for (int[] den : DEN) {
            d.keep(den[0] - 1, 1, den[1] - 1, den[0] + 2, 4, den[1] + 2);
        }
        for (int[] r : ROCK) {
            d.keep(r[0] - 1, 1, r[1] - 1, r[0] + 2, 3, r[1] + 2);
        }
        // 潮の満ちる床の一段上には飾りを置かない (水に沈む・流される)
        d.keep(-HX, 1, -HZ, HX, 1, HZ);
        d.shell(2);
        d.floorSpiral(0, -4, 12, 2.5, Blocks.CUT_SANDSTONE.defaultBlockState());
        for (int[] a : AT) {
            d.floorRing(a[0], a[1], 2.6, Blocks.CHERRY_PLANKS.defaultBlockState());
            // 天蓋: 天井の板の下に三段 (広い → 狭い)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    int r2 = dx * dx + dz * dz;
                    if (r2 <= 5) {
                        d.mark(a[0] + dx, H, a[1] + dz, r2 >= 4 ? Blocks.MAGENTA_TERRACOTTA.defaultBlockState() : Blocks.CHERRY_PLANKS.defaultBlockState());
                    }
                    if (r2 <= 2) {
                        d.mark(a[0] + dx, H - 1, a[1] + dz, r2 == 0 ? Blocks.CALCITE.defaultBlockState() : Blocks.PURPUR_BLOCK.defaultBlockState());
                    }
                }
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                d.mark(a[0] + 2 * dir.getStepX(), H - 1, a[1] + 2 * dir.getStepZ(), TideBuild.rod(Direction.DOWN));
            }
            d.mark(a[0], H - 2, a[1], Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            d.light(a[0], H - 3, a[1], 12);
        }
        // 巣穴の庇: 壁と巣穴のあいだを死んだ珊瑚で埋め、上 (高さ 5) に庇
        for (int[] den : DEN) {
            int sx = den[0] > 0 ? 1 : -1;
            int wallX = sx * HX;
            int inner = sx > 0 ? den[0] + 2 : den[0] - 1;
            for (int x = Math.min(inner, wallX); x <= Math.max(inner, wallX); x++) {
                for (int z = den[1] - 1; z <= den[1] + 2; z++) {
                    for (int y = 1; y <= 4; y++) {
                        d.mark(x, y, z, y == 4 ? Blocks.DARK_PRISMARINE.defaultBlockState() : Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState());
                    }
                }
            }
            for (int x = den[0] - 1; x <= den[0] + 2; x++) {
                for (int z = den[1] - 1; z <= den[1] + 2; z++) {
                    d.mark(x, 5, z, (x + z) % 2 == 0 ? Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState() : Blocks.DARK_PRISMARINE.defaultBlockState());
                }
            }
        }
        d.hiddenLights(11, 4, true).wallBranches(12, 5, 2).shards(4);
        d.finish();
    }

    @Override
    public void start(RaidCtx ctx) {
        wave = 0;
        lost = 0;
        beginWave(ctx, 0);
    }

    private void beginWave(RaidCtx ctx, int delay) {
        betweenWaves = true;
        gapUntil = ctx.now() + delay;
        spawnedInWave = 0;
        toSpawn = 0;
    }

    private int waveSize(RaidCtx ctx, int w) {
        int base = switch (w) {
            case 0 -> 3;
            case 1 -> 5;
            default -> 7;
        };
        int per = switch (w) {
            case 0 -> 2;
            case 1 -> 3;
            default -> 4;
        };
        return base + (ctx.scale() - 1) * per / 2;
    }

    @Override
    public void tick(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        long now = ctx.now();
        if (now % 40 == 0) {
            TideRaid.purgeStrays(ctx, ctx.cell().bounds());
        }
        if (betweenWaves) {
            if (now < gapUntil) {
                return;
            }
            if (wave >= WAVES) {
                return;
            }
            betweenWaves = false;
            toSpawn = waveSize(ctx, wave);
            spawnedInWave = 0;
            nextBatch = now;
            floodRows = 0;
            ctx.say(Component.translatable("message.skyabyss.raid.TR3.wave", wave + 1));
            level.playSound(null, ctx.origin().offset(0, 2, HZ - 2), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundSource.HOSTILE, 2f, 0.7f);
        }
        rise(ctx);
        // 湧き: 一度に数体ずつ
        if (spawnedInWave < toSpawn && now >= nextBatch) {
            nextBatch = now + BATCH_TICKS;
            int batch = Math.min(toSpawn - spawnedInWave, 2 + ctx.scale() / 2);
            for (int k = 0; k < batch; k++) {
                spawnOne(ctx);
            }
        }
        // 敵の動き: 苗へ。人が近くにいる敵は人を相手にする
        List<ServerPlayer> standing = ctx.standing();
        int[] biters = new int[SAPLINGS];
        for (Mob m : ctx.aliveMobs()) {
            if (!(m instanceof ReefScuttler rs)) {
                continue;
            }
            rs.rideTide(flooded());
            int s = nearestSapling(ctx, m.getX(), m.getZ());
            if (s < 0) {
                continue;
            }
            double sx = ctx.origin().getX() + AT[s][0] + 0.5;
            double sz = ctx.origin().getZ() + AT[s][1] + 0.5;
            double d2 = (m.getX() - sx) * (m.getX() - sx) + (m.getZ() - sz) * (m.getZ() - sz);
            boolean engaged = false;
            for (ServerPlayer p : standing) {
                if (p.distanceToSqr(m) < 4.5 * 4.5) {
                    engaged = true;
                }
            }
            if (d2 <= BITE_R * BITE_R && !engaged) {
                biters[s]++;
                m.getNavigation().stop();
                m.setTarget(null);
                m.getLookControl().setLookAt(sx, ctx.origin().getY() + 2, sz);
            } else if (!engaged && now % 10 == 0) {
                m.setTarget(null);
                m.getNavigation().moveTo(sx, ctx.origin().getY() + 1, sz, flooded() ? SWIFT : 1.1);
            }
        }
        // かじる: 一秒ごと、かじっている数だけ体力が減る (赤い粒)
        if (now % BITE_EVERY == 0) {
            for (int s = 0; s < SAPLINGS; s++) {
                if (biters[s] > 0 && hp[s] > 0) {
                    hp[s] = Math.max(0, hp[s] - biters[s]);
                    level.sendParticles(fx(jp.revati.skyabyss.fx.FxParticles.Kind.GLOW), ctx.origin().getX() + AT[s][0] + 0.5, ctx.origin().getY() + 2.5, ctx.origin().getZ() + AT[s][1] + 0.5, 8, 0.4, 0.5, 0.4, 0);
                    level.playSound(null, ctx.origin().offset(AT[s][0], 2, AT[s][1]), SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1f, 0.7f);
                    showSapling(ctx, s);
                    if (hp[s] == 0) {
                        lost++;
                        level.playSound(null, ctx.origin().offset(AT[s][0], 2, AT[s][1]), SoundEvents.CORAL_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.6f);
                        ctx.say(Component.translatable("message.skyabyss.raid.TR3.sapling_lost"));
                    }
                }
            }
        }
        if (alive() == 0) {
            ctx.fail("message.skyabyss.raid.TR3.lost");
            return;
        }
        // 波が尽きた
        if (!betweenWaves && spawnedInWave >= toSpawn && ctx.aliveMobs().isEmpty()) {
            ebb(ctx);
            wave++;
            if (wave < WAVES) {
                beginWave(ctx, GAP_TICKS);
            } else {
                betweenWaves = true;
                gapUntil = Long.MAX_VALUE;
            }
        }
    }

    private void spawnOne(RaidCtx ctx) {
        int[] d = DEN[ctx.rng().nextInt(DEN.length)];
        BlockPos at = ctx.origin().offset(d[0] + ctx.rng().nextInt(2), 2, d[1] + ctx.rng().nextInt(2));
        // 波の最初の一群は、潮の先に乗って南の端から来る
        if (spawnedInWave < 2 + ctx.scale() / 2) {
            at = ctx.origin().offset(ctx.rng().nextInt(2 * HX - 5) - HX + 3, 1, FLOOD_Z - 1 - ctx.rng().nextInt(2));
        }
        // 巣穴のこぶの上に湧く (こぶは高さ 1〜2 なので、その上)
        boolean puffer = wave >= 1 && spawnedInWave % 4 == 3;
        spawnedInWave++;
        ServerLevel level = ctx.level();
        level.sendParticles(fx(jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        TideRaid.spawn(ctx, puffer ? TideMobSetup.BUBBLE_PUFFER.get() : TideMobSetup.REEF_SCUTTLER.get(), at.above(puffer ? 1 : 0));
    }

    // ---- 潮
    public boolean flooded() {
        return floodRows >= 0;
    }

    // 潮の先を一 tick に一列ずつ北へ進める (南の端 z = FLOOD_Z から)。水に沈む所の、流れで壊れる物は覚えておいて、引いたときに戻す
    private void rise(RaidCtx ctx) {
        int rows = 2 * FLOOD_Z + 1;
        if (floodRows < 0 || floodRows >= rows) {
            return;
        }
        ServerLevel level = ctx.level();
        int z = FLOOD_Z - floodRows;
        for (int x = -HX; x <= HX; x++) {
            BlockPos p = ctx.origin().offset(x, 1, z);
            net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
            boolean wet = st.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
            if (!st.isAir() && !wet) {
                if (!st.canBeReplaced(net.minecraft.world.level.material.Fluids.FLOWING_WATER) || st.isSolid()) {
                    continue;
                }
                drowned.put(p, st);
            }
            RaidKit.set(level, p, Blocks.WATER.defaultBlockState());
        }
        if (floodRows % 3 == 0) {
            level.sendParticles(fx(jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), ctx.origin().getX() + 0.5, ctx.origin().getY() + 1.6, ctx.origin().getZ() + z + 0.5, 24, HX * 0.6, 0.2, 0.3, 0.1);
        }
        floodRows++;
    }

    // 潮が引く: 満ちた水 (流れた水も) を一度に消し、沈めた物を戻す
    private void ebb(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        if (ctx.cell() == null) {
            floodRows = -1;
            return;
        }
        for (int x = -HX; x <= HX; x++) {
            for (int z = -FLOOD_Z; z <= FLOOD_Z; z++) {
                BlockPos p = ctx.origin().offset(x, 1, z);
                if (level.getBlockState(p).is(Blocks.WATER)) {
                    RaidKit.set(level, p, Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (var e : drowned.entrySet()) {
            RaidKit.set(level, e.getKey(), e.getValue());
        }
        drowned.clear();
        if (floodRows >= 0) {
            level.playSound(null, ctx.origin().offset(0, 2, 0), SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundSource.HOSTILE, 2f, 0.8f);
            level.sendParticles(fx(jp.revati.skyabyss.fx.FxParticles.Kind.SMOKE), ctx.origin().getX() + 0.5, ctx.origin().getY() + 1.4, ctx.origin().getZ() + 0.5, 24, HX * 0.6, 0.2, FLOOD_Z * 0.6, 0.1);
            if (wave + 1 < WAVES) {
                ctx.say(Component.translatable("message.skyabyss.raid.TR3.ebb"));
            }
        }
        floodRows = -1;
    }

    @Override
    public void end(RaidCtx ctx) {
        ebb(ctx);
    }

    private int nearestSapling(RaidCtx ctx, double x, double z) {
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int s = 0; s < SAPLINGS; s++) {
            if (hp[s] <= 0) {
                continue;
            }
            double dx = x - (ctx.origin().getX() + AT[s][0] + 0.5);
            double dz = z - (ctx.origin().getZ() + AT[s][1] + 0.5);
            double d = dx * dx + dz * dz;
            if (d < bd) {
                bd = d;
                best = s;
            }
        }
        return best;
    }

    // 苗の見た目: 体力で背丈が縮む (三段 → 一段)。失われたら死んだ珊瑚
    private void showSapling(RaidCtx ctx, int s) {
        ServerLevel level = ctx.level();
        int h = hp[s] <= 0 ? 0 : hp[s] > SAPLING_HP * 2 / 3 ? 3 : hp[s] > SAPLING_HP / 3 ? 2 : 1;
        for (int k = 1; k <= 3; k++) {
            BlockPos p = ctx.origin().offset(AT[s][0], 1 + k, AT[s][1]);
            if (k <= h) {
                RaidKit.set(level, p, k == h ? Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState() : Blocks.WARPED_WART_BLOCK.defaultBlockState());
            } else {
                RaidKit.set(level, p, k == 1 && h == 0 ? Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState());
            }
        }
    }

    private void resetSaplings(RaidCtx ctx) {
        for (int s = 0; s < SAPLINGS; s++) {
            hp[s] = SAPLING_HP;
            showSapling(ctx, s);
        }
        lost = 0;
    }

    private int alive() {
        int n = 0;
        for (int v : hp) {
            if (v > 0) {
                n++;
            }
        }
        return n;
    }

    @Override
    public boolean isComplete() {
        return wave >= WAVES && alive() > 0;
    }

    @Override
    public void bossFit(RaidCtx ctx, jp.revati.skyabyss.trial.raid.mid.MidRoom mid) {
        if (mid instanceof jp.revati.skyabyss.trial.raid.mid.TideMidRoom t) {
            t.useBeds(AT);
        }
    }

    @Override
    public void bossBegin(RaidCtx ctx, Mob boss) {
        ctx.say(Component.translatable("message.skyabyss.raid.TR3.beckoner"));
    }

    @Override
    public void reset(RaidCtx ctx) {
        ebb(ctx);
        wave = 0;
        betweenWaves = true;
        gapUntil = Long.MAX_VALUE;
        resetSaplings(ctx);
    }

    @Override
    public String objectiveKey() {
        return betweenWaves && wave < WAVES ? "raid.skyabyss.TR3.ready" : "raid.skyabyss.TR3.defend";
    }

    @Override
    public int counter() {
        return Math.min(WAVES, wave);
    }

    @Override
    public int counterMax() {
        return WAVES;
    }

    @Override
    public String secondaryKey() {
        return "raid.skyabyss.TR3.sapling";
    }

    @Override
    public int secondary() {
        int n = 0;
        for (int v : hp) {
            n += v;
        }
        return n;
    }

    @Override
    public int secondaryMax() {
        return SAPLINGS * SAPLING_HP;
    }

    // 試験用: 湧いた敵の様子 (詰まったときの調べ)
    public String debug(RaidCtx ctx) {
        StringBuilder sb = new StringBuilder("wave " + wave + " between=" + betweenWaves + " spawned=" + spawnedInWave + "/" + toSpawn + " mobs=" + ctx.aliveMobs().size());
        for (Mob m : ctx.aliveMobs()) {
            sb.append(" [" + m.getType().getDescriptionId() + " " + m.blockPosition().toShortString() + " nav=" + !m.getNavigation().isDone() + " tick=" + m.tickCount + " ground=" + m.onGround() + " y=" + m.getY() + " ai=" + !m.isNoAi() + " target=" + m.getTarget() + "]");
        }
        return sb.toString();
    }

    // 試験用
    public int saplingHp(int s) {
        return hp[s];
    }

    public int wave() {
        return wave;
    }

    // 試験用: 床の一段上の水の数 (満ちた所)
    public int wetCells(RaidCtx ctx) {
        int n = 0;
        for (int x = -HX; x <= HX; x++) {
            for (int z = -HZ; z <= HZ; z++) {
                if (ctx.level().getBlockState(ctx.origin().offset(x, 1, z)).getFluidState().is(net.minecraft.tags.FluidTags.WATER)) {
                    n++;
                }
            }
        }
        return n;
    }

    // 試験用: 入口・出口の戸の前 (縁石の外) が濡れていないか
    public boolean doorsDry(RaidCtx ctx) {
        for (int x = -2; x <= 2; x++) {
            for (int z : new int[] {HZ, -HZ}) {
                if (!ctx.level().getBlockState(ctx.origin().offset(x, 1, z)).getFluidState().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<int[]> saplingSpots() {
        List<int[]> out = new ArrayList<>();
        for (int[] a : AT) {
            out.add(a.clone());
        }
        return out;
    }

    // 試験用: 苗の体力を直接減らす (一本が尽きれば失われる)
    public void damage(RaidCtx ctx, int s, int amount) {
        hp[s] = Math.max(0, hp[s] - amount);
        showSapling(ctx, s);
    }

    // 攻撃の粒は mod の water の粒だけ (docs/design/59 の 2 章)
    private static net.minecraft.core.particles.SimpleParticleType fx(jp.revati.skyabyss.fx.FxParticles.Kind kind) {
        return jp.revati.skyabyss.fx.FxParticles.type(jp.revati.skyabyss.fx.FxParticles.Element.WATER, kind);
    }
}
