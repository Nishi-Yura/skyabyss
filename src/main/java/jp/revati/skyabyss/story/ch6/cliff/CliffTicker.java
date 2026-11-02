package jp.revati.skyabyss.story.ch6.cliff;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.CliffCreeper;
import jp.revati.skyabyss.story.ch6.mob.MoonLeaper;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.MoonScale;
import jp.revati.skyabyss.story.ch6.mob.PushStone;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 上弦の崖の見張り (docs/design/46 の 4.4)。月の世界のサーバーの tick で:
//   毎 tick       天秤の皿 (CliffBalance。人が近くにいる時だけ)
//   20 tick ごと  皿 (主の間) から堀へ落ちた人を入口へ戻す・敵を湧かす (人ごと)・前のループの自分 (二人目) を立たせる・
//                 崖を離れた人の錘を山へ戻す・主の間の片付け
//   落ちる傷      崖の中では、どこから落ちても傷 4 まで (決まり「落ちても死なないが、下の段からやり直し」)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class CliffTicker {
    public static final String PAST_SELF = "moon_past2";
    static final int CAP = 5;
    static final double CAMP_SAFE = 18;
    static final float FALL_CAP = 4f;

    private CliffTicker() {
    }

    public static boolean built(ServerLevel level) {
        return MoonWorld.cliffBuilt(level.getServer());
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL || !built(level)) {
            return;
        }
        CliffBalance.tick(level);
        if (level.getGameTime() % 20 != 0) {
            return;
        }
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) {
                continue;
            }
            if (!MoonCliff.inside(p.blockPosition())) {
                if (CliffBalance.carried(p) > 0) {
                    CliffBalance.dropCarried(p);
                }
                continue;
            }
            if (MoonCliff.inArenaZone(p.position()) && p.getY() < MoonCliff.ARENA_FALL_Y) {
                fellFromPlate(p);
                continue;
            }
            if (guardPlate(p)) {
                continue;
            }
            spawn(level, p);
            pastSelf(level, p);
        }
        CliffArena.tick(level);
    }

    // 月を離れた人の錘も戻す
    @SubscribeEvent
    static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && CliffBalance.carried(p) > 0) {
            CliffBalance.dropCarried(p);
        }
    }

    // ---------------------------------------------------------------- 落ちる傷 (崖の中では 4 まで)
    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != MoonWorld.LEVEL || !MoonCliff.inside(p.blockPosition())) {
            return;
        }
        double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (event.getDistance() > safe + FALL_CAP) {
            event.setDistance((float) (safe + FALL_CAP));
            event.setDamageMultiplier(1.0f);
        }
    }

    // 皿から堀へ落ちた: 主の間の入口へ戻す (傷 4)。門を通れない人 (頂の輪から飛び込んだ人) は門の前の道へ
    static void fellFromPlate(ServerPlayer p) {
        BlockPos to = CliffPuzzles.mayCross(p) ? MoonCliff.mark("arena_entry") : CliffPuzzles.beforeGate(p.serverLevel());
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        p.fallDistance = 0;
        p.hurt(p.damageSources().fellOutOfWorld(), FALL_CAP);
        Notice.narrate(p, "message.skyabyss.moon.plate_fall");
    }

    // 主の皿に、門を通らずに入った人 (月の跳びで岩山や門の枠から跳び移った) は門の前の道へ押し戻す。true = 戻した
    public static boolean guardPlate(ServerPlayer p) {
        if (!MoonCliff.inArenaZone(p.position()) || p.getY() < MoonCliff.ARENA_FALL_Y || CliffPuzzles.mayCross(p)) {
            return false;
        }
        BlockPos to = CliffPuzzles.beforeGate(p.serverLevel());
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 自己テストの偽の人はつながりがないので、その場で動かす
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        Notice.narrate(p, "message.skyabyss.moon.plate_barred");
        return true;
    }

    // ---------------------------------------------------------------- 湧き (道と段々。野営と主の間は湧かない)
    static boolean spawnable(BlockPos p) {
        return MoonCliff.inside(p) && !MoonCliff.inArenaZone(p.getCenter()) && !p.closerThan(MoonCliff.mark("camp"), CAMP_SAFE);
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!spawnable(at) || level.getGameTime() % 40 != 0) {
            return;
        }
        int near = level.getEntitiesOfClass(MoonMob.class, p.getBoundingBox().inflate(40, 24, 40),
                e -> e.isAlive() && !(e instanceof MoonScale) && !e.getTags().contains(CliffArena.TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        float roll = r.nextFloat();
        EntityType<? extends MoonMob> type = roll < 0.45f ? MoonMobsSetup.MOON_LEAPER.get() : roll < 0.75f ? MoonMobsSetup.CLIFF_CREEPER.get()
                : MoonMobsSetup.PUSH_STONE.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, spot);
        }
    }

    static BlockPos spot(ServerLevel level, BlockPos near, RandomSource r) {
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 12 + r.nextDouble() * 12;
            int x = near.getX() + (int) Math.round(Math.cos(a) * d);
            int z = near.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = near.getY() + 8; y >= near.getY() - 12; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                        && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)) {
                    if (!spawnable(p)) {
                        break;
                    }
                    return p;
                }
            }
        }
        return null;
    }

    static MoonMob put(ServerLevel level, EntityType<? extends MoonMob> type, BlockPos at) {
        MoonMob m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY() + (m instanceof PushStone ? 1.5 : 0), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        boolean wx = MoonMob.waxing(level);
        if (m instanceof MoonLeaper) {
            m.tuneMoon(1, wx, false, MoonLeaper.HP, MoonLeaper.DMG);
        } else if (m instanceof PushStone) {
            m.tuneMoon(1, wx, false, PushStone.HP, PushStone.DMG);
        } else if (m instanceof CliffCreeper) {
            m.tuneMoon(1, wx, false, CliffCreeper.HP, CliffCreeper.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }

    // ---------------------------------------------------------------- 前のループの自分 (二人目。野営。頼みが済むまで)
    static void pastSelf(ServerLevel level, ServerPlayer p) {
        BlockPos at = MoonCliff.mark("past_self");
        if (!p.blockPosition().closerThan(at, 48)) {
            return;
        }
        List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(at).inflate(24), e -> e.isAlive() && PAST_SELF.equals(e.guestId()));
        boolean gone = level.players().stream().filter(q -> q.blockPosition().closerThan(at, 48))
                .allMatch(q -> CliffPuzzles.stage(q) >= CliffPuzzles.stageIndex("promise") + 1);
        if (gone) {
            near.forEach(TownGuestEntity::discard);
            return;
        }
        if (!near.isEmpty()) {
            for (int i = 1; i < near.size(); i++) {
                near.get(i).discard();
            }
            return;
        }
        TownGuestEntity e = TownSetup.GUEST.get().create(level);
        if (e == null) {
            return;
        }
        e.setGuest(PAST_SELF);
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 200f, 0f);
        e.setHome(at);
        level.addFreshEntity(e);
    }
}
