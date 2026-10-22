package jp.revati.skyabyss.trial.raid.tide;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.titan.tide.mob.ReefScuttler;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

// 闘 TR3「珊瑚の苗を守る」: 三本の珊瑚の苗を、三つの波の潮の敵から守る。敵は苗をかじりに来る。
//  - 苗は体力を持つ (HUD の二つ目の数)。かじられると赤い粒が出て、苗が縮む。人が近くにいる敵は人を相手にする
//  - 全部の波を退けて、苗が一本でも残れば越える。三本とも失えば、部屋は最初からやり直し
//  - 敵は磯走り (苗をかじる) と泡吹き (人を撃つ)。水の仕掛けはない
public final class TideSaplingRoom extends RaidRoom {
    static final int HX = 14;
    static final int HZ = 14;
    static final int H = 9;
    static final int SAPLINGS = 3;
    static final int SAPLING_HP = 30;
    static final int WAVES = 3;
    static final int BITE_EVERY = 20;
    static final double BITE_R = 2.0;
    static final int GAP_TICKS = 100;
    static final int BATCH_TICKS = 50;
    // 苗の位置 (原点からの相対)
    static final int[][] AT = {{-8, -3}, {0, -7}, {8, -3}};
    // 敵の出る所 (壁ぎわの四つの珊瑚の巣穴)
    static final int[][] DEN = {{-12, -11}, {12, -11}, {-12, 6}, {12, 6}};

    private final int[] hp = new int[SAPLINGS];
    private int wave;
    private int toSpawn;
    private int spawnedInWave;
    private long nextBatch;
    private long gapUntil;
    private boolean betweenWaves;
    private int lost;

    @Override
    public void build(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaidKit.Cell c = RaidKit.buildCell(ctx, new RaidKit.Spec(HX, HZ, H, RaidKit.TIDE));
        for (int i = 0; i < SAPLINGS; i++) {
            // 苗床: 3×3 の台
            RaidKit.fill(level, c.origin().offset(AT[i][0] - 1, 1, AT[i][1] - 1), c.origin().offset(AT[i][0] + 1, 1, AT[i][1] + 1), Blocks.PRISMARINE_BRICKS.defaultBlockState());
            RaidKit.set(level, c.origin().offset(AT[i][0], 1, AT[i][1]), Blocks.DARK_PRISMARINE.defaultBlockState());
        }
        // 巣穴: 灰色の珊瑚のこぶ (2×2、低い)
        for (int[] d : DEN) {
            RaidKit.fill(level, c.origin().offset(d[0], 1, d[1]), c.origin().offset(d[0] + 1, 1, d[1] + 1), Blocks.DEAD_BRAIN_CORAL_BLOCK.defaultBlockState());
            RaidKit.set(level, c.origin().offset(d[0], 2, d[1]), Blocks.DEAD_TUBE_CORAL_BLOCK.defaultBlockState());
        }
        resetSaplings(ctx);
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
            ctx.say(Component.translatable("message.skyabyss.raid.TR3.wave", wave + 1));
        }
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
            if (!(m instanceof ReefScuttler)) {
                continue;
            }
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
                m.getNavigation().moveTo(sx, ctx.origin().getY() + 1, sz, 1.1);
            }
        }
        // かじる: 一秒ごと、かじっている数だけ体力が減る (赤い粒)
        if (now % BITE_EVERY == 0) {
            for (int s = 0; s < SAPLINGS; s++) {
                if (biters[s] > 0 && hp[s] > 0) {
                    hp[s] = Math.max(0, hp[s] - biters[s]);
                    level.sendParticles(TideRaid.RED, ctx.origin().getX() + AT[s][0] + 0.5, ctx.origin().getY() + 2.5, ctx.origin().getZ() + AT[s][1] + 0.5, 8, 0.4, 0.5, 0.4, 0);
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
        // 巣穴のこぶの上に湧く (こぶは高さ 1〜2 なので、その上)
        boolean puffer = wave >= 1 && spawnedInWave % 4 == 3;
        spawnedInWave++;
        ServerLevel level = ctx.level();
        level.sendParticles(ParticleTypes.POOF, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
        TideRaid.spawn(ctx, puffer ? TideMobSetup.BUBBLE_PUFFER.get() : TideMobSetup.REEF_SCUTTLER.get(), at.above(puffer ? 1 : 0));
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
                RaidKit.set(level, p, k == 1 ? Blocks.PINK_TERRACOTTA.defaultBlockState() : Blocks.PINK_CONCRETE.defaultBlockState());
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
    public void reset(RaidCtx ctx) {
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

    // 試験用
    public int saplingHp(int s) {
        return hp[s];
    }

    public int wave() {
        return wave;
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
}
