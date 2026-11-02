package jp.revati.skyabyss.trial.raid.mid;

import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.back.StormBlocks;
import jp.revati.skyabyss.titan.storm.back.StormRods;
import jp.revati.skyabyss.titan.storm.raiju.Raiju;
import jp.revati.skyabyss.titan.storm.raiju.RaijuLoot;
import jp.revati.skyabyss.titan.storm.raiju.RaijuNest;
import jp.revati.skyabyss.titan.storm.raiju.RaijuSetup;
import jp.revati.skyabyss.trial.raid.RaidCtx;
import jp.revati.skyabyss.trial.raid.RaidKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;

// 雷翼の中ボス「雷獣」の間 (元は右手首の瘤の巣。直径 26 の丸い洞、高さ 15、避雷の柱 6 本 (半径 9、60 度おき) と接地の栓)。
// 巣の形 (RaijuNest.Site) を部屋の中心へずらして使うので、雷獣の技 (雷渡り・接地で落ちる・降りかかり) は元のまま。
// 栓 (レバー) を右クリックすると柱が 8 秒接地し、そこへ渡った雷獣は痺れて落ちる。一人でも栓を引いて回れる。倒すと越える
public final class StormMidRoom extends MidRoom {
    public static final String ID = "SRM";
    static final int R = jp.revati.skyabyss.titan.storm.back.StormBackLayout.RAIJU_NEST_RADIUS;
    static final int HEIGHT = 15;

    @Nullable
    private RaijuNest.Site site;

    private RaijuNest.Site site(RaidCtx ctx) {
        if (site == null) {
            site = new RaijuNest.Site(ctx.level(), ctx.origin());
        }
        return site;
    }

    @Override
    public void build(RaidCtx ctx) {
        RaidKit.buildCell(ctx, new RaidKit.Spec(R, R, HEIGHT, RaidKit.STORM));
        ServerLevel level = ctx.level();
        RaijuNest.Site s = site(ctx);
        // 丸い洞: 半径の外は壁 (入口・出口の戸の列は残す)
        double rr = (R + 0.5) * (R + 0.5);
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                if (x * x + z * z > rr && Math.abs(x) > 1) {
                    RaidKit.fill(level, ctx.local(x, 1, z), ctx.local(x, HEIGHT, z), RaidKit.STORM.wall());
                }
            }
        }
        placeRods(ctx);
        RaidKit.restInArena(ctx, 0, R - 3);
        // 建築 (docs/design/39 の 4.3): 壁の銅の帯と羽の段・雲の天井と稲妻・中央の吊りの尖塔と雲の床
        jp.revati.skyabyss.trial.raid.storm.StormRoomDeco.dressSRM(ctx);
    }

    // 闘の部屋に: 避雷の柱 6 本と接地の栓 (間と同じ形。部屋の真ん中のまわり)
    @Override
    protected void fitInto(RaidCtx ctx) {
        placeRods(ctx);
    }

    // 避雷の柱と栓
    private void placeRods(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaijuNest.Site s = site(ctx);
        for (int i = 0; i < RaijuNest.RODS; i++) {
            BlockPos r = s.rodBase(i);
            RaidKit.set(level, r, StormBackSetup.ROD_BASE.get().defaultBlockState());
            for (int k = 0; k < 3; k++) {
                RaidKit.set(level, r.above(1 + k), StormBackSetup.ROD_BULB.get().defaultBlockState().setValue(StormBlocks.RodBulb.LIT, true));
            }
            RaidKit.set(level, r.above(4), StormBackSetup.ROD_SHAFT.get().defaultBlockState());
            RaidKit.set(level, r.above(5), StormBackSetup.ROD_TIP.get().defaultBlockState());
            int dx = Integer.signum(ctx.origin().getX() - r.getX());
            int dz = Integer.signum(ctx.origin().getZ() - r.getZ());
            BlockPos lever = r.offset(Math.abs(dx) >= Math.abs(dz) ? dx : 0, 0, Math.abs(dx) >= Math.abs(dz) ? 0 : dz);
            Direction face = Direction.getNearest(lever.getX() - r.getX(), 0, lever.getZ() - r.getZ());
            RaidKit.set(level, lever.above(), jp.revati.skyabyss.relic.RelicBlocks.leverBase(jp.revati.skyabyss.relic.RelicStyle.STORM)
                    .setValue(LeverBlock.FACE, AttachFace.FLOOR).setValue(LeverBlock.FACING, face));
            RaidKit.set(level, lever, Blocks.WAXED_CUT_COPPER.defaultBlockState());
        }
        registerRods(ctx);
    }

    private void registerRods(RaidCtx ctx) {
        RaijuNest.Site s = site(ctx);
        for (int i = 0; i < RaijuNest.RODS; i++) {
            BlockPos rod = s.rod(i);
            StormRods.register(ctx.level(), rod);
            StormRods.charge(ctx.level(), rod, StormRods.FULL);
            RaijuNest.show(s, i, false);
        }
    }

    @Override
    @Nullable
    protected Mob spawnBoss(RaidCtx ctx) {
        ServerLevel level = ctx.level();
        RaijuNest.Site s = site(ctx);
        Raiju raiju = RaijuSetup.RAIJU.get().create(level);
        if (raiju == null) {
            return null;
        }
        int start = 0;
        raiju.moveTo(s.home().x, s.floor() + 7, s.home().z, 90f, 0f);
        raiju.bind(s, Math.max(1, ctx.scale()), start);
        raiju.setGroupId(ctx.group());
        raiju.bindRoom(r -> onBossDeath(ctx, r));
        if (!level.addFreshEntity(raiju)) {
            return null;
        }
        Vec3 top = Vec3.atBottomCenterOf(s.rodTop(start));
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(top.x, top.y, top.z);
            level.addFreshEntity(bolt);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, top.x, top.y + 1, top.z, 80, 1.0, 1.0, 1.0, 0.4);
        WhaleMidRoom.say(ctx, "raid.skyabyss.SRM.begin");
        return ctx.adopt(raiju);
    }

    @Override
    protected Vec3 home(RaidCtx ctx) {
        return site(ctx).home();
    }

    @Override
    protected void tickFight(RaidCtx ctx) {
        if (ctx.now() % 5 == 0) {
            RaijuNest.Site s = site(ctx);
            if (StormRods.get(ctx.level(), s.rod(0)) == null) {
                registerRods(ctx);
            }
            RaijuNest.tickRods(s);
        }
    }

    @Override
    public boolean onUse(RaidCtx ctx, ServerPlayer player, BlockPos pos, boolean attack) {
        RaijuNest.Site s = site(ctx);
        int i = RaijuNest.rodIndexOfPlug(s, pos);
        if (i < 0 || attack) {
            return false;
        }
        RaijuNest.pull(s, i, player);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    // 栓を引く (自己テスト用。人がいなくても)
    public boolean pullForTest(RaidCtx ctx, int i) {
        return RaijuNest.pull(site(ctx), i, null);
    }

    @Override
    protected void resetFixtures(RaidCtx ctx) {
        registerRods(ctx);
        if (!inRoom) {
            jp.revati.skyabyss.trial.raid.storm.StormRoomDeco.exitLit(ctx, false);
        }
    }

    @Override
    public void end(RaidCtx ctx) {
        super.end(ctx);
        RaijuNest.Site s = site(ctx);
        for (int i = 0; i < RaijuNest.RODS; i++) {
            StormRods.unregister(ctx.level(), s.rod(i));
        }
    }

    @Override
    protected void reward(RaidCtx ctx, Set<UUID> ids) {
        RaijuLoot.onDefeat(ctx.level(), ids);
        if (!inRoom) {
            jp.revati.skyabyss.trial.raid.storm.StormRoomDeco.exitLit(ctx, true);
        }
        jp.revati.skyabyss.titan.group.FightBadges.onVictory(ctx.level(), boss, ids, 0);
    }

    @Override
    public String objectiveKey() {
        return "raid.skyabyss.SRM.defeat";
    }
}
