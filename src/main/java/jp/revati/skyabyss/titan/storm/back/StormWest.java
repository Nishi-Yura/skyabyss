package jp.revati.skyabyss.titan.storm.back;

import jp.revati.skyabyss.notice.Notice;
import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.List;

import jp.revati.skyabyss.ModText;
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
// - 昇り風の穴: 凪は 4、嵐は 13 上がる (広間・骨・西の瘤)。雷獣の巣の頂の穴は、雷獣を倒したあといつも 12
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
        return List.of(new Vent(HUB_UPDRAFT, -1), new Vent(new BlockPos(WEST_VENT_X[0], BONE_FLOOR, -20), -1),
                new Vent(new BlockPos(WEST_VENT_X[1], BONE_FLOOR, -20), -1), new Vent(WEST_KNOB_VENT, -1));
    }

    private StormWest() {
    }

    // 横風の段階 (嵐の拍の中の位置から): 0 = なし、1 = 予告、2 = 吹いている
    static int gust(ServerLevel level) {
        if (StormBackClock.phase(level) != StormBackClock.Phase.STORM) {
            return 0;
        }
        int t = gustT(level);
        if (t < GUST_WARN) {
            return 1;
        }
        return t < GUST_WARN + GUST_BLOW ? 2 : 0;
    }

    static int gustT(ServerLevel level) {
        return StormBackClock.forced() != null ? (int) (level.getGameTime() % GUST_PERIOD)
                : Math.floorMod(StormBackState.get(level).clock() - StormBackClock.CYCLE.startOf(2), GUST_PERIOD);
    }

    static boolean inBone(Vec3 p) {
        return p.x < W0.x1() && p.x > WEST_FORE_X1 - 1 && p.z > BONE_Z1 - 1 && p.z < BONE_Z2 + 2 && p.y > BONE_FLOOR - 2 && p.y < BONE_FLOOR + BONE_HEIGHT + 2;
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
        // 昇り風の穴 (人がいれば持ち上げ、4 tick ごとに粒)
        for (Vent v : vents()) {
            liftVent(level, v.floor(), liftFor(level, v), gt, p -> true);
        }
        liftVent(level, RAIJU_VENT, EAST_PERCH.floor() + 2 - RAIJU_VENT.getY() - 1, gt, p -> StormProgress.of(level, p).raijuVent());
        int gust = gust(level);
        if (gust == 1 && gt % 6 == 0) {
            // 雨の筋 (手首の側から肩の側へ流れる)
            for (ServerPlayer p : level.players()) {
                if (inBone(p.position())) {
                    for (int i = 0; i < 6; i++) {
                        StormFx.forced(level, ParticleTypes.FALLING_WATER, p.getX() - 6 + level.random.nextDouble() * 4,
                                p.getY() + level.random.nextDouble() * 4, p.getZ() - 4 + level.random.nextDouble() * 8, 0, 1, 0, 0, 0.6);
                        StormFx.forced(level, ParticleTypes.CLOUD, p.getX() - 8, p.getY() + 1 + level.random.nextDouble() * 3,
                                p.getZ() - 5 + level.random.nextDouble() * 10, 0, 1, 0, 0, 0.5);
                    }
                }
            }
        }
        int t = gustT(level);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            boolean bone = inBone(p.position());
            if (bone && gust == 1 && t == 0) {
                level.playSound(null, p.getX() - 6, p.getY() + 2, p.getZ(), StormSetup.CROSSWIND.get(), SoundSource.AMBIENT, 1.6f, 1f);
            }
            if (bone && gust == 2 && !p.isCreative() && !sheltered(level, p)) {
                Vec3 d = p.getDeltaMovement();
                p.setDeltaMovement(Math.max(d.x, GUST_PUSH), d.y, d.z * 0.8);
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

    static void liftVent(ServerLevel level, BlockPos floor, double height, long gt, java.util.function.Predicate<ServerPlayer> who) {
        Vec3 base = new Vec3(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        double maxY = base.y + height;
        boolean any = false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.getAbilities().flying || p.distanceToSqr(base) > 48 * 48 || !who.test(p)) {
                continue;
            }
            any = true;
            double dx = p.getX() - base.x;
            double dz = p.getZ() - base.z;
            if (dx * dx + dz * dz <= 1.6 * 1.6 && p.getY() >= base.y - 0.2 && p.getY() <= maxY + 1.5) {
                Updraft.lift(p, maxY);
            }
        }
        if (gt % 4 == 0 && any) {
            Updraft.particles(level, base, 1.3, height, 3);
        }
    }

    // 風切りの羽根: w0 に入ると一枚。西の試練の外へ出ると消える。跳躍・低速落下は試練の中で消す
    static void tickFeathers(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
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
            } else if (GaleFeather.holds(p)) {
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
