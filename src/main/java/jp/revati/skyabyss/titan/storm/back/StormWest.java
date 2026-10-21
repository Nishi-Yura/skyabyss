package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.util.Players;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.titan.group.Groups;
import jp.revati.skyabyss.titan.storm.StormSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

// 西の試練 風切りの骨 (12 の 4.1節) と、背中の昇り風の穴 (広間・骨・瘤・雷獣の巣の頂)。
// - 風切りの羽根: 肩の門 (w0) で手に入る。西の試練の外へ出ると消える
// - 昇り風の穴: 凪は 4、嵐は 13 上がる (広間・上腕の骨・肋の間の梁)。肋の間は凪の 4 で一段ずつ登れる (25 の 5章 A。拍で行ける行けないを決めない)。雷獣の巣の頂の穴は、雷獣を倒したあといつも 12
// - 拍はその人のグループの舵 (StormHelm) の固定があればそれ、なければ背中の拍
// - 横風: 嵐のあいだ 6 秒ごと。1.5 秒前に雨の筋と笛の音、吹く 2 秒は梁の陰 (風上 3 ブロック以内に壁や柱) にいないと肩の側へ押し戻す
// - 跳躍と低速落下の効き目は、西の試練の中では消す
// グループ: 拍・横風・道の形 (StormBackState.westRoute) は全員に同じ (梁はブロックなので見る人ごとに変えられない)。
// 雷獣の巣の頂の昇り風だけは、雷獣を倒した (東が鳴った・眠っている) グループの人だけを持ち上げる
final class StormWest {
    static final int GUST_PERIOD = 120;
    static final int GUST_WARN = 30;
    static final int GUST_BLOW = 40;
    static final double GUST_PUSH = 0.4;

    // 昇り風の穴: 床の中心、上がる高さ (負なら拍で決める)
    record Vent(BlockPos floor, double fixedHeight) {
    }

    static List<Vent> vents() {
        List<Vent> out = new java.util.ArrayList<>();
        out.add(new Vent(HUB_UPDRAFT, -1));
        for (int x : WEST_VENT_X) {
            out.add(new Vent(new BlockPos(x, BONE_FLOOR, WZ - 1), -1));
        }
        // 肋の間の梁 (床の上の 0 段目から、頂の手前まで)
        for (int i = 0; i < RIB_LAYERS * RIB_HOPS; i++) {
            out.add(new Vent(ribLedge(i), -1));
        }
        return out;
    }

    private StormWest() {
    }

    // 横風の段階 (嵐の拍の中の位置から): 0 = なし、1 = 予告、2 = 吹いている
    static int gust(ServerLevel level) {
        if (StormBackClock.phase(level) != StormBackClock.Phase.STORM) {
            return 0;
        }
        return gustAt(gustT(level));
    }

    static int gustAt(int t) {
        if (t < GUST_WARN) {
            return 1;
        }
        return t < GUST_WARN + GUST_BLOW ? 2 : 0;
    }

    // その人に吹く横風の段階 (舵で嵐に固定していれば固定の始まりから数える。凪に固定していればなし)
    static int gust(ServerLevel level, ServerPlayer p) {
        StormHelm.Lock l = StormHelm.lockOf(level, StormBackState.Trial.WEST, Groups.keyOf(p));
        if (l == null) {
            return gust(level);
        }
        return l.phase() == StormBackClock.Phase.STORM ? gustAt(Math.floorMod(level.getGameTime() - l.start(), GUST_PERIOD)) : 0;
    }

    static int gustT(ServerLevel level) {
        return StormBackClock.forced() != null ? (int) (level.getGameTime() % GUST_PERIOD)
                : Math.floorMod(StormBackState.get(level).clock() - StormBackClock.CYCLE.startOf(2), GUST_PERIOD);
    }

    // その人の風の拍の中の位置 (横風の予告の音を合わせる)
    static int gustT(ServerLevel level, ServerPlayer p) {
        StormHelm.Lock l = StormHelm.lockOf(level, StormBackState.Trial.WEST, Groups.keyOf(p));
        return l == null ? gustT(level) : (int) Math.floorMod(level.getGameTime() - l.start(), (long) GUST_PERIOD);
    }

    static boolean inBone(Vec3 p) {
        return p.x < W0.x1() && p.x > WEST_FORE_X1 - 1 && p.z > BONE_Z1 - 1 && p.z < BONE_Z2 + 2 && p.y > BONE_FLOOR - 2 && p.y < (p.x < RIB_X2 + 2 ? RIB_TOP_Y + 12 : BONE_FLOOR + BONE_HEIGHT + 2);
    }

    // 風上 (西、-x) の 3 ブロック以内に、足か目の高さで固いブロックがあれば陰
    static boolean sheltered(ServerLevel level, Player p) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = 1; dx <= 3; dx++) {
            for (double h : new double[] {0.2, 1.5}) {
                m.set(p.getX() - dx, p.getY() + h, p.getZ());
                if (level.getBlockState(m).isCollisionShapeFullBlock(level, m) || !level.getBlockState(m).getCollisionShape(level, m).isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    static double liftFor(ServerLevel level, Vent v) {
        return v.fixedHeight() >= 0 ? v.fixedHeight() : StormBackClock.liftHeight(level);
    }

    static void tick(ServerLevel level, boolean peaceful) {
        long gt = level.getGameTime();
        // 昇り風の穴 (人がいれば持ち上げ、4 tick ごとに粒。高さはその人に効く拍で)
        for (Vent v : vents()) {
            liftVent(level, v.floor(), p -> v.fixedHeight() >= 0 ? v.fixedHeight() : StormHelm.liftHeight(level, p), gt, p -> true);
        }
        liftVent(level, RAIJU_VENT, p -> EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1, gt, p -> StormProgress.of(level, p).raijuVent());
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator()) {
                continue;
            }
            boolean bone = inBone(p.position());
            if (!bone) {
                continue;
            }
            int gust = gust(level, p);
            int t = gustT(level, p);
            if (gust == 1 && gt % 6 == 0) {
                // 雨の筋 (手首の側から肩の側へ流れる)
                for (int i = 0; i < 6; i++) {
                    level.sendParticles(p, ParticleTypes.FALLING_WATER, true, p.getX() - 6 + level.random.nextDouble() * 4,
                            p.getY() + level.random.nextDouble() * 4, p.getZ() - 4 + level.random.nextDouble() * 8, 0, 1, 0, 0, 0.6);
                    level.sendParticles(p, ParticleTypes.CLOUD, true, p.getX() - 8, p.getY() + 1 + level.random.nextDouble() * 3,
                            p.getZ() - 5 + level.random.nextDouble() * 10, 0, 1, 0, 0, 0.5);
                }
            }
            if (gust == 1 && t == 0) {
                level.playSound(null, p.getX() - 6, p.getY() + 2, p.getZ(), StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.6f, 1f);
            }
            if (gust == 2 && !p.isCreative() && !sheltered(level, p)) {
                // 実際の動きに足す (サーバー側の古い速さを送り返すと、入力と跳躍が止まる)
                Vec3 d = Updraft.actualMotion(p);
                p.setDeltaMovement(Math.max(d.x, GUST_PUSH), d.y, d.z * 0.9);
                p.hurtMarked = true;
                if (gt % 4 == 0) {
                    StormFx.forced(level, ParticleTypes.CLOUD, p.getX() - 1, p.getY() + 1, p.getZ(), 3, 0.3, 0.6, 0.3, 0.1);
                }
            }
        }
        if (gt % 10 == 0) {
            tickFeathers(level);
        }
    }

    // 昇り風の穴: 立っている人を、その人の高さ (heightFor) まで持ち上げる。粒もその人に見せる高さで
    static void liftVent(ServerLevel level, BlockPos floor, java.util.function.ToDoubleFunction<ServerPlayer> heightFor, long gt,
            java.util.function.Predicate<ServerPlayer> who) {
        Vec3 base = new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator() || p.getAbilities().flying || p.distanceToSqr(base) > 48 * 48 || !who.test(p)) {
                continue;
            }
            double height = heightFor.applyAsDouble(p);
            double maxY = base.y + height;
            double dx = p.getX() - base.x;
            double dz = p.getZ() - base.z;
            if (dx * dx + dz * dz <= 1.6 * 1.6 && p.getY() >= base.y - 0.2 && p.getY() <= maxY + 1.5) {
                Updraft.lift(p, maxY);
            }
            if (gt % 4 == 0 && p.distanceToSqr(base) < 24 * 24) {
                for (int i = 0; i < 3; i++) {
                    double a = level.random.nextDouble() * Math.PI * 2;
                    double r = level.random.nextDouble() * 1.3;
                    double h = level.random.nextDouble() * height;
                    level.sendParticles(p, ParticleTypes.CLOUD, true, base.x + Math.cos(a) * r, base.y + h, base.z + Math.sin(a) * r, 0, 0, 1, 0, 0.25);
                }
                level.sendParticles(p, ParticleTypes.END_ROD, true, base.x, base.y + height, base.z, 1, 0.5, 0.0, 0.5, 0.0);
            }
        }
    }

    // 風切りの羽根: w0 に入ると一枚。西の試練の外へ出ると消える。跳躍・低速落下は試練の中で消す
    static void tickFeathers(ServerLevel level) {
        for (ServerPlayer p : Players.snapshot(level.players())) {
            boolean inWest = inWest(p.position());
            if (inWest) {
                if (W0.contains(p.blockPosition()) && !GaleFeather.holds(p) && p.isAlive()) {
                    if (p.getInventory().add(new ItemStack(StormBackSetup.GALE_FEATHER.get()))) {
                        Notice.bar(p, ModText.hint("message.skyabyss.storm.gale_feather"));
                        level.playSound(null, p.blockPosition(), StormSetup.GALE.get(), SoundSource.PLAYERS, 0.8f, 1.4f);
                    }
                }
                if (!W0.contains(p.blockPosition())) {
                    p.removeEffect(MobEffects.JUMP);
                    p.removeEffect(MobEffects.SLOW_FALLING);
                }
            } else if (GaleFeather.holds(p) && !StormDeck.inGlideCell(p.position())) {
                strip(p);
                Notice.bar(p, ModText.hint("message.skyabyss.storm.gale_feather_gone"));
            }
        }
    }

    static void strip(Player p) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            if (p.getInventory().getItem(i).getItem() instanceof GaleFeather) {
                p.getInventory().setItem(i, ItemStack.EMPTY);
            }
        }
    }
}
