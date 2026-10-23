package jp.revati.skyabyss.trial.raid.ember;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.combat.Telegraph;
import jp.revati.skyabyss.combat.TelegraphShape;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.trial.EmberTrials;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import jp.revati.skyabyss.titan.group.GroupOwned;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidRoom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

// 余燼のレイドの部屋の共通の土台 (docs/design/13_ember.md の「レイドの部屋」)。
//  - 人の一覧 who(ctx): 本番は ctx.standing()。自己テストでは testPlayers で差し替える (順路の無い所でも動かせる)
//  - 敵の湧かせ方・赤だけの予告・熱の傷は、三つの部屋で同じものを使う
abstract class EmberRoom extends RaidRoom {
    // 自己テスト用: 入っていれば、部屋の人はこの一覧
    @Nullable
    List<ServerPlayer> testPlayers;
    // 自己テスト用: ctx.fail を呼んだ印
    boolean failRequested;
    // reset のたびに増える。遅れて実る予告の傷が、リセット前のものを捨てるため
    int generation;

    List<ServerPlayer> who(RaidCtx ctx) {
        if (testPlayers != null) {
            List<ServerPlayer> out = new ArrayList<>();
            for (ServerPlayer p : testPlayers) {
                if (!Downed.isDowned(p)) {
                    out.add(p);
                }
            }
            return out;
        }
        return ctx.standing();
    }

    int scale(RaidCtx ctx) {
        return Math.max(1, testPlayers != null ? testPlayers.size() : ctx.players().size());
    }

    void bar(RaidCtx ctx, Component text) {
        for (ServerPlayer p : who(ctx)) {
            Notice.bar(p, text);
        }
    }

    void fail(RaidCtx ctx, String key) {
        failRequested = true;
        ctx.fail(key);
    }

    static double flat(Vec3 a, double x, double z) {
        double dx = a.x - x;
        double dz = a.z - z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    static double flat(Player p, BlockPos c) {
        return flat(p.position(), c.getX() + 0.5, c.getZ() + 0.5);
    }

    static boolean near(Player p, BlockPos c, double r) {
        return flat(p, c) <= r && Math.abs(p.getY() - c.getY()) < 3;
    }

    static void set(RaidCtx ctx, BlockPos p, BlockState st) {
        ctx.level().setBlock(p, st, 2);
    }

    static void particles(RaidCtx ctx, ParticleOptions type, double x, double y, double z, int n, double spread, double speed) {
        ctx.level().sendParticles(type, x, y, z, n, spread, spread, spread, speed);
    }

    // 熱の傷 (heat_burst)。倒れない程度に: 体力が floor 以下なら与えない
    static boolean heatHurt(RaidCtx ctx, ServerPlayer p, float amount, float floor) {
        if (p.isCreative() || p.isSpectator() || p.getHealth() <= floor) {
            return false;
        }
        p.invulnerableTime = 0;
        return p.hurt(EmberTrials.damage(ctx.level(), EmberIds.HEAT_BURST, null), amount);
    }

    // 赤だけの予告の円。warn tick のあと、円の中の人へ onHit。リセットをまたいだら黙って捨てる
    void redCircle(RaidCtx ctx, double x, double z, double r, int warn, Telegraph.Hit hit) {
        final int gen = generation;
        Telegraph.in(ctx.level(), new TelegraphShape.Circle(x, z, r)).floor(ctx.origin().getY() + 1).warn(warn).danger()
                .onHit((lvl, p) -> {
                    if (gen == generation) {
                        hit.hit(lvl, p);
                    }
                }).start();
    }

    // 余燼の敵を部屋の敵として湧かせる
    @Nullable
    <T extends Mob> Mob spawn(RaidCtx ctx, EntityType<T> type, BlockPos pos) {
        Mob mob = type.create(ctx.level());
        if (mob == null) {
            return null;
        }
        mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, ctx.rng().nextFloat() * 360f, 0f);
        mob.finalizeSpawn(ctx.level(), ctx.level().getCurrentDifficultyAt(pos), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        mob.addTag(EmberMob.TRIAL_TAG);
        if (mob instanceof GroupOwned owned) {
            owned.setGroupId(ctx.group());
        }
        ctx.level().addFreshEntity(mob);
        return ctx.adopt(mob);
    }

    // 人を動かす (本物は teleportTo、偽の人は moveTo)
    static void moveTo(ServerPlayer p, double x, double y, double z) {
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer || p.connection == null) {
            p.moveTo(x, y, z, p.getYRot(), p.getXRot());
        } else {
            p.teleportTo(x, y, z);
        }
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
    }

    static BlockState b(net.minecraft.world.level.block.Block block) {
        return block.defaultBlockState();
    }

    static final BlockState AIR = Blocks.AIR.defaultBlockState();
}
