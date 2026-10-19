package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberHeat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// 噴気の割れ目 (13 の 4.3節): 谷の床の割れ目 10 本。16 秒で一周する決まった巡りで、2〜3 本ずつ噴く (4 組。4 秒おき)。
// 噴気は「灰運びの道」を作る: 谷の入口から火床まで、低い壁に挟まれた幅 5 の道 (灰運びの道) が通り、その中に 5 本、両脇の人の通り道に 5 本ある。
// 灰運びは道を歩いて火床へ行くので、噴いた噴気の上を通ると焼かれる (灰が燃え、火床の薪になる。Hearth.jetBurned)。道の脇の壁には 2 か所の隙があり、人が灰運びを誘い込める
// 噴く 1.5 秒前に割れ目が白く光り、しゅうと鳴る (段階 1)。噴く 1.5 秒のあいだ (段階 2)、真上 5 ブロックが噴気になる。
//   人: 熱 +25 (VENT)、6 の傷 (heat_burst)、少し上へ跳ねる。敵: 30 の傷 (灰被りの灰の殻は一度で割れる)。
//   噴気一回につき一人 (一体) に一度だけ当たる
public final class HearthVents {
    public static final int CYCLE = 320;
    public static final int GROUP_SPACING = 80;
    public static final int WARN_TICKS = 30;
    public static final int BURST_TICKS = 30;
    public static final float PLAYER_HEAT = 25f;
    public static final float PLAYER_DAMAGE = 6f;
    public static final float ENEMY_DAMAGE = 30f;
    public static final int HEIGHT = 5;
    public static final int GROUPS = 4;

    // 割れ目の位置 (谷の床 y 83)。組は番号 % 4。0〜4 番は灰運びの道 (x -1〜1) の中、5〜15 番は両脇の人の通り道 (谷は版 20 で x1.6 に広がったので 16 本)
    private static final int F = EmberBackLayout.FLOOR_SOUTH;
    public static final List<BlockPos> VENTS = List.of(
            new BlockPos(0, F, 14), new BlockPos(-1, F, 21), new BlockPos(1, F, 27), new BlockPos(0, F, 34), new BlockPos(-1, F, 40),
            new BlockPos(-9, F, 18), new BlockPos(9, F, 24), new BlockPos(-9, F, 30), new BlockPos(9, F, 37), new BlockPos(-8, F, 43),
            new BlockPos(-13, F, 22), new BlockPos(13, F, 18), new BlockPos(13, F, 31), new BlockPos(-13, F, 37), new BlockPos(13, F, 43),
            new BlockPos(-14, F, 28));
    // 灰運びの道の壁 (x = ±3、z 11〜40、高さ 2)。隙は z 24〜26 の 2 か所 (両側)。HearthRoom が建てる
    public static final int LANE_WALL_X = 3;
    public static final int LANE_Z0 = 11;
    public static final int LANE_Z1 = 40;
    public static final int LANE_GAP_Z0 = 24;
    public static final int LANE_GAP_Z1 = 26;

    public static class VentBlock extends Block {
        // 0 しずか、1 白く光る (噴く前)、2 噴いている
        public static final IntegerProperty PHASE = IntegerProperty.create("phase", 0, 2);
        public static final int[] LIGHT = {3, 11, 15};

        public VentBlock(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(PHASE, 0));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(PHASE);
        }
    }

    // 噴気ごとの「もう当たった」の記録 (噴気の通し番号 + 割れ目の番号 → 当たった者)
    private static final Map<Long, java.util.Set<UUID>> HIT = new HashMap<>();
    private static final int[] PHASES = new int[VENTS.size()];

    private HearthVents() {
    }

    static void clearMemory() {
        HIT.clear();
        java.util.Arrays.fill(PHASES, 0);
    }

    public static int groupOf(int index) {
        return index % GROUPS;
    }

    // 巡りの中での割れ目の段階: 噴く時刻は 組 * 100 から 30 tick。その 30 tick 前から光る
    public static int phaseAt(int index, long now) {
        long t = Math.floorMod(now, (long) CYCLE);
        long start = groupOf(index) * (long) GROUP_SPACING;
        if (t >= start && t < start + BURST_TICKS) {
            return 2;
        }
        long untilBurst = Math.floorMod(start - t, (long) CYCLE);
        return untilBurst > 0 && untilBurst <= WARN_TICKS ? 1 : 0;
    }

    // 何回目の噴気か (当たりの記録の鍵)
    private static long burstId(int index, long now) {
        long start = groupOf(index) * (long) GROUP_SPACING;
        return Math.floorDiv(now - start, (long) CYCLE) * 16 + index;
    }

    public static AABB columnOf(BlockPos vent) {
        return new AABB(vent.getX() - 0.4, vent.getY() + 1, vent.getZ() - 0.4, vent.getX() + 1.4, vent.getY() + 1 + HEIGHT, vent.getZ() + 1.4);
    }

    // 毎 tick (谷が使われているあいだ)。active が false (火床が済んだ) なら、しずかにする
    static void tick(ServerLevel level, long now, boolean active) {
        for (int i = 0; i < VENTS.size(); i++) {
            BlockPos pos = VENTS.get(i);
            int phase = active ? phaseAt(i, now) : 0;
            if (phase != PHASES[i]) {
                PHASES[i] = phase;
                BlockState s = level.getBlockState(pos);
                if (s.is(EmberTrialSetup.HEAT_VENT.get())) {
                    level.setBlock(pos, s.setValue(VentBlock.PHASE, phase), Block.UPDATE_CLIENTS);
                }
                if (phase == 1) {
                    level.playSound(null, pos, EmberTrialSetup.VENT_HISS.get(), SoundSource.BLOCKS, 1.0f, 1f);
                } else if (phase == 2) {
                    level.playSound(null, pos, EmberTrialSetup.VENT_BURST.get(), SoundSource.BLOCKS, 1.4f, 1f);
                }
            }
            if (phase == 1 && now % 3 == 0) {
                level.sendParticles(ParticleTypes.CLOUD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 2, 0.25, 0.05, 0.25, 0.02);
            } else if (phase == 2) {
                level.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 4, 0.3, 1.4, 0.3, 0.05);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 2.5, pos.getZ() + 0.5, 2, 0.3, 1.2, 0.3, 0.03);
                burst(level, i, now);
            }
        }
        if (now % 200 == 0) {
            HIT.keySet().removeIf(k -> k < Math.floorDiv(now, (long) CYCLE) * 16 - 32);
        }
    }

    // 噴いている最中の割れ目の上にいる者に当てる (噴気一回につき一度)
    static void burst(ServerLevel level, int index, long now) {
        BlockPos pos = VENTS.get(index);
        java.util.Set<UUID> done = HIT.computeIfAbsent(burstId(index, now), k -> new java.util.HashSet<>());
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, columnOf(pos), x -> x.isAlive() && !x.isSpectator())) {
            if (done.add(e.getUUID())) {
                hit(level, e, pos);
            }
        }
    }

    public static void hit(ServerLevel level, LivingEntity e, BlockPos pos) {
        if (e instanceof Player p) {
            if (p.isCreative()) {
                return;
            }
            if (p instanceof ServerPlayer sp) {
                EmberHeat.add(sp, PLAYER_HEAT, EmberHeat.Reason.VENT);
            }
            p.invulnerableTime = 0;
            p.hurt(EmberTrials.damage(level, EmberIds.HEAT_BURST, null), PLAYER_DAMAGE);
            Vec3 away = p.position().subtract(pos.getX() + 0.5, 0, pos.getZ() + 0.5).multiply(1, 0, 1);
            Vec3 push = away.lengthSqr() < 1e-3 ? Vec3.ZERO : away.normalize().scale(0.25);
            p.setDeltaMovement(push.x, 0.65, push.z);
            p.hurtMarked = true;
        } else {
            e.invulnerableTime = 0;
            e.hurt(EmberTrials.damage(level, EmberIds.HEAT_BURST, null), ENEMY_DAMAGE);
            // 灰を運ぶ敵が焼かれたら、運んでいた灰が燃えて火床の薪になる (灰はもうかけられない)
            if (e.getTags().contains(Hearth.CARRIER_TAG)) {
                e.removeTag(Hearth.CARRIER_TAG);
                Hearth.jetBurned(level, e);
            }
        }
    }
}
