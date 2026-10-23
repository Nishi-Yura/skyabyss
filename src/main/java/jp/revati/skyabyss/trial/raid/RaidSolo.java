package jp.revati.skyabyss.trial.raid;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 一人で越える台本 (docs/design/32 の 9 章「どの部屋も一人で越えられる」)。自己テスト raid solo (RaidSoloSelfTest) が、
// 登録したすべての部屋 (と中ボスの間) を、偽の人一人で最初から越えるまで動かす。部屋ごとに台本を一つ register する。
//
// ---- 台本の約束 ----
//  - 人は一人だけ。部屋の中の物の扱いは人ができることだけ: 歩く (put で移す。壁の中へは移さない)、ブロックを叩く・使う (use)、
//    床の物を拾う・置く (部屋の onUse・拾い物の決まりのとおり)、敵を叩く (hit)。部屋の中の数や状態を直に書き換えない
//  - 読むのはよい (見本の形・次の音・どこに何があるか。人が目で見て分かること)
//  - 体力は台本の外で毎 tick 戻す (避ける・食べるの代わり)。倒れたら全滅と同じに数え、部屋をやり直す
//  - 戦いの部屋に台本が無ければ fight (近い敵から叩く) を使う
public final class RaidSolo {
    private RaidSolo() {
    }

    // t = 始まってからの tick。毎 tick、部屋の tick の前に呼ばれる
    @FunctionalInterface
    public interface Script {
        void act(RaidCtx ctx, RaidRoom room, ServerPlayer p, int t);
    }

    private static final Map<String, Script> SCRIPTS = new HashMap<>();
    private static final Map<String, Integer> LIMITS = new HashMap<>();

    // 既定の上限 (8 分)
    public static final int DEFAULT_LIMIT = 20 * 60 * 8;

    public static void register(String id, Script script) {
        SCRIPTS.put(id, script);
    }

    public static void register(String id, int limitTicks, Script script) {
        SCRIPTS.put(id, script);
        LIMITS.put(id, limitTicks);
    }

    @Nullable
    public static Script of(String id) {
        return SCRIPTS.get(id);
    }

    public static int limitOf(String id) {
        return LIMITS.getOrDefault(id, DEFAULT_LIMIT);
    }

    // ---------------------------------------------------------------- 人ができること
    // 足をその場所へ (歩いて行ったことにする)
    public static void put(ServerPlayer p, BlockPos feet) {
        put(p, Vec3.atBottomCenterOf(feet));
    }

    public static void put(ServerPlayer p, Vec3 feet) {
        // 偽の人の teleportTo は通信の口 (FakePlayerNetHandler) を通るだけで動かないので、位置を直に置く
        p.setPos(feet.x, feet.y, feet.z);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ブロックを叩く (attack) か使う。部屋が扱ったか。人はブロックのそばへ行ってから叩く
    public static boolean use(RaidCtx ctx, RaidRoom room, ServerPlayer p, BlockPos pos, boolean attack) {
        if (p.position().distanceTo(Vec3.atCenterOf(pos)) > 4.5) {
            BlockPos stand = standNear(ctx, pos);
            if (stand != null) {
                put(p, stand);
            }
        }
        return room.onUse(ctx, p, pos, attack);
    }

    // pos のそばの立てる所 (足と頭が空いていて、足の下が固い)
    @Nullable
    public static BlockPos standNear(RaidCtx ctx, BlockPos pos) {
        var level = ctx.level();
        for (int r = 1; r <= 3; r++) {
            for (int dy = -2; dy <= 1; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        BlockPos f = pos.offset(dx, dy, dz);
                        if (level.getBlockState(f).isAir() && level.getBlockState(f.above()).isAir()
                                && level.getBlockState(f.below()).isFaceSturdy(level, f.below(), net.minecraft.core.Direction.UP)
                                && ctx.cell().inner().contains(Vec3.atCenterOf(f))) {
                            return f;
                        }
                    }
                }
            }
        }
        return null;
    }

    // 近い敵 (部屋の敵) のそばへ行って叩く。every tick ごとに一度、dmg の傷 (剣の一振りくらい)。叩いた敵 (いなければ null)
    @Nullable
    public static Mob hit(RaidCtx ctx, ServerPlayer p, int t, int every, float dmg) {
        List<Mob> mobs = ctx.aliveMobs();
        Mob best = null;
        double bd = Double.MAX_VALUE;
        for (Mob m : mobs) {
            double d = m.distanceToSqr(p);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        if (best == null || t % every != 0) {
            return best;
        }
        if (bd > 9) {
            BlockPos stand = standNear(ctx, best.blockPosition());
            if (stand != null) {
                put(p, stand);
            }
        }
        best.invulnerableTime = 0;
        best.hurt(p.damageSources().playerAttack(p), dmg);
        return best;
    }

    // 戦いの部屋の既定の台本: 近い敵から叩く (0.6 秒ごとに 7。鉄の剣くらい)
    public static final Script FIGHT = (ctx, room, p, t) -> hit(ctx, p, t, 12, 7f);
}
