package jp.revati.skyabyss.trial.raid.mid;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.smith.AshSmith;
import jp.revati.skyabyss.titan.ember.smith.AshSmithArena;
import jp.revati.skyabyss.titan.ember.smith.AshSmithLoot;
import jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

// 余燼の中ボス「灰の鍛え手」の間 (元は鍛え手の間 EmberBackLayout.SMITH。27 × 27 の円い間、高さ 14、焼き入れの槽 2・灰溜まり 4・柱 4・奥の炉の扉)。
// 元の間の中身 (AshSmithArena.build) を部屋の中心へずらして建て、鍛え手にもそのずれを渡すので、技 (槽の湯気・炉の扉の灰被り・熱) は元のまま。
// 体力は挑む人数の倍率で受ける傷を割る (一人なら 1 倍)。倒すと越える
public final class EmberMidRoom extends MidRoom {
    public static final String ID = "ERM";
    static final int R = 13;
    static final int HEIGHT = 14;

    // 元の間からのずれ
    static BlockPos shiftOf(RaidCtx ctx) {
        return ctx.origin().subtract(EmberBackLayout.SMITH_CENTER);
    }

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(R, R, HEIGHT, RaidKit.EMBER));
        AshSmithArena.build(ctx.level(), shiftOf(ctx));
        exitLamps = jp.revati.skyabyss.trial.raid.ember.EmberRaidDecor.decorateSmithHall(ctx);
        RaidKit.brightenFloor(ctx, 2);
        RaidKit.restInArena(ctx, 0, -R + 3);
    }

    // 飾りの出口の灯り (倒すと点く)
    private java.util.List<BlockPos> exitLamps = java.util.List.of();

    @Override
    protected void onBossDeath(RaidCtx ctx, Mob dead) {
        super.onBossDeath(ctx, dead);
        if (defeated) {
            jp.revati.skyabyss.trial.raid.ember.EmberRaidDecor.exitLampsPublic(ctx, exitLamps, true);
        }
    }

    @Override
    @Nullable
    protected Mob spawnBoss(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        AshSmith smith = EmberSmithSetup.ASH_SMITH.get().create(level);
        if (smith == null) {
            return null;
        }
        BlockPos shift = shiftOf(ctx);
        Vec3 at = AshSmithArena.home(shift).add(0, 0, 4);
        smith.moveTo(at.x, at.y, at.z, 180f, 0f);
        smith.bind(Math.max(1, ctx.scale()));
        smith.setGroupId(ctx.group());
        smith.bindRoom(shift, s -> onBossDeath(ctx, s));
        if (!level.addFreshEntity(smith)) {
            return null;
        }
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.5, at.z, 40, 1.0, 1.2, 1.0, 0.05);
        WhaleMidRoom.say(ctx, "raid.skyabyss.ERM.begin");
        return ctx.adopt(smith);
    }

    @Override
    protected Vec3 home(RaidCtx ctx) {
        return AshSmithArena.home(shiftOf(ctx));
    }

    @Override
    protected void resetFixtures(RaidCtx ctx) {
        AshSmithArena.buildFurnace(ctx.level(), false, shiftOf(ctx));
        jp.revati.skyabyss.trial.raid.ember.EmberRaidDecor.exitLampsPublic(ctx, exitLamps, false);
    }

    @Override
    protected void reward(RaidCtx ctx, Set<UUID> ids) {
        AshSmithLoot.onDefeat(ctx.level(), ids);
        jp.revati.skyabyss.titan.group.FightBadges.onVictory(ctx.level(), boss, ids, 0);
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.ERM.defeat";
    }
}
