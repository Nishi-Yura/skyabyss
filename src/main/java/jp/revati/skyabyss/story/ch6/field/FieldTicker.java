package jp.revati.skyabyss.story.ch6.field;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.ch6.MoonWorld;
import jp.revati.skyabyss.story.ch6.mob.FullKeeper;
import jp.revati.skyabyss.story.ch6.mob.LightSwarm;
import jp.revati.skyabyss.story.ch6.mob.MirrorBearer;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadeStrider;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 満月の野の見張り (docs/design/46 の 4.5)。月の世界のサーバーの tick で:
//   毎 tick       光の満ち引き (FieldTide)
//   10 tick ごと  降る光の溝の中の人のまわりに光を降らす
//   20 tick ごと  池・台から落ちた人を戻す・門を通らずに台に上がった人を戻す・敵を湧かす (人ごと)・前のループの自分 (三人目) を立たせる・
//                 主の間の片付け・一瞬止めの札の片付け
//   落ちる傷      野の中では、どこから落ちても傷 4 まで
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FieldTicker {
    public static final String PAST_SELF = "moon_past3";
    static final int CAP = 5;
    static final double CAMP_SAFE = 18;
    static final float FALL_CAP = 4f;
    // 降る光の傷 (区画の倍率をかける前)
    static final float LIGHT_DMG = 6f;

    private FieldTicker() {
    }

    public static boolean built(ServerLevel level) {
        return MoonWorld.fieldBuilt(level.getServer());
    }

    @SubscribeEvent
    static void onTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != MoonWorld.LEVEL || !built(level)) {
            return;
        }
        FieldTide.tick(level);
        long gt = level.getGameTime();
        if (gt % 10 == 0) {
            for (ServerPlayer p : level.players()) {
                if (!p.isSpectator() && MoonField.inGully(p.position())) {
                    gully(level, p);
                }
            }
        }
        if (gt % 20 != 0) {
            return;
        }
        MomentStop.sweep(level);
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || !MoonField.inside(p.blockPosition())) {
                continue;
            }
            if (fell(p)) {
                continue;
            }
            if (guardMesa(p)) {
                continue;
            }
            spawn(level, p);
            pastSelf(level, p);
        }
        FieldArena.tick(level);
    }

    // ---------------------------------------------------------------- 落ちる傷 (野の中では 4 まで)
    @SubscribeEvent
    static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.level().dimension() != MoonWorld.LEVEL || !MoonField.inside(p.blockPosition())) {
            return;
        }
        double safe = p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        if (event.getDistance() > safe + FALL_CAP) {
            event.setDistance((float) (safe + FALL_CAP));
            event.setDamageMultiplier(1.0f);
        }
    }

    // 池の穴へ落ちた: 池の入口の岩棚へ (傷 4)。台から落ちた (台の柱の外の地面より下まで): 主の間の入口 (門を通れない人は門の前)
    static boolean fell(ServerPlayer p) {
        BlockPos to = null;
        String msg = null;
        if (MoonField.inPond(p.position()) && p.getY() < MoonField.POND_FALL_Y) {
            to = MoonField.mark("pond_entry");
            msg = "message.skyabyss.moon.pond_fall";
        } else if (fellFromArena(p)) {
            to = FieldPuzzles.mayCross(p) ? MoonField.mark("arena_entry") : MoonField.mark("gate_front");
            msg = "message.skyabyss.moon.mesa_fall";
        }
        if (to == null) {
            return false;
        }
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        p.hurt(p.damageSources().fellOutOfWorld(), FALL_CAP);
        Notice.narrate(p, msg);
        return true;
    }

    // 主の間に上がった人 (札あり) が台から落ちた (床より 6 低くなった)。帰り口で下りた人は札を外してあるので数えない
    static boolean fellFromArena(ServerPlayer p) {
        if (!p.getTags().contains(FieldArena.TAG)) {
            return false;
        }
        BlockPos c = MoonField.mark("arena_center");
        if (p.getY() >= c.getY() - 6) {
            return false;
        }
        p.removeTag(FieldArena.TAG);
        return MoonField.flat(p.position(), c) < MoonField.MESA_ZONE + 14;
    }

    // 台の上に、門を通らずに上がった人 (月の跳びで根元の岩から跳び上がった) は門の前へ戻す。true = 戻した
    public static boolean guardMesa(ServerPlayer p) {
        BlockPos c = MoonField.mark("arena_center");
        if (!MoonField.inArenaZone(p.position()) || p.getY() < c.getY() - 1.5) {
            return false;
        }
        if (FieldPuzzles.mayCross(p)) {
            p.addTag(FieldArena.TAG);
            return false;
        }
        BlockPos to = MoonField.mark("gate_front");
        p.teleportTo(p.serverLevel(), to.getX() + 0.5, to.getY(), to.getZ() + 0.5, p.getYRot(), 0f);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            p.moveTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        }
        p.fallDistance = 0;
        Notice.narrate(p, "message.skyabyss.moon.mesa_barred");
        return true;
    }

    // ---------------------------------------------------------------- 降る光 (溝の中の人の少し先と足もとへ)
    static void gully(ServerLevel level, ServerPlayer p) {
        BlockPos dais = MoonField.mark("shard_gully");
        if (p.getX() < dais.getX() + 3) {
            return;
        }
        RandomSource r = level.random;
        BlockPos e = MoonField.mark("gully_east");
        double x = p.getX() - 2 - r.nextDouble() * 9;
        double z = p.getZ() + (r.nextDouble() - 0.5) * 7;
        if (level.getGameTime() % 30 == 0) {
            x = p.getX();
            z = p.getZ();
        }
        x = Math.max(dais.getX() + 4, x);
        // 光は人の立つ高さの上 16 から落ち、途中の足場 (尾根・影の窪の屋根) に当たればそこで弾ける
        FallingLight.fall(level, new Vec3(x, Math.max(e.getY(), Math.floor(p.getY())), z), LIGHT_DMG * (float) MoonMob.SECTION_DMG[2]);
    }

    // ---------------------------------------------------------------- 湧き (野の原。野営・台・池・溝・塔は湧かない)
    static boolean spawnable(BlockPos p) {
        Vec3 c = p.getCenter();
        return MoonField.inside(p) && !p.closerThan(MoonField.mark("camp"), CAMP_SAFE) && !MoonField.onMesa(c) && MoonField.flat(c, MoonField.mark("arena_center")) > MoonField.MESA_ZONE + 3
                && !MoonField.inPond(c) && !MoonField.inGully(c) && !MoonField.inTower(c);
    }

    static void spawn(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        if (!spawnable(at) || level.getGameTime() % 40 != 0) {
            return;
        }
        int near = level.getEntitiesOfClass(MoonMob.class, p.getBoundingBox().inflate(40, 24, 40),
                e -> e.isAlive() && !(e instanceof FullKeeper) && !e.getTags().contains(FieldArena.TAG)).size();
        if (near >= CAP) {
            return;
        }
        RandomSource r = level.random;
        float roll = r.nextFloat();
        EntityType<? extends MoonMob> type = roll < 0.4f ? MoonMobsSetup.LIGHT_SWARM.get() : roll < 0.7f ? MoonMobsSetup.SHADE_STRIDER.get()
                : MoonMobsSetup.MIRROR_BEARER.get();
        BlockPos spot = spot(level, at, r);
        if (spot != null) {
            put(level, type, spot);
        }
    }

    static BlockPos spot(ServerLevel level, BlockPos near, RandomSource r) {
        for (int tries = 0; tries < 12; tries++) {
            double a = r.nextDouble() * Math.PI * 2;
            double d = 14 + r.nextDouble() * 12;
            int x = near.getX() + (int) Math.round(Math.cos(a) * d);
            int z = near.getZ() + (int) Math.round(Math.sin(a) * d);
            for (int y = near.getY() + 6; y >= near.getY() - 8; y--) {
                BlockPos p = new BlockPos(x, y, z);
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir() && level.getBlockState(p.above(2)).isAir()
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

    public static MoonMob put(ServerLevel level, EntityType<? extends MoonMob> type, BlockPos at) {
        MoonMob m = type.create(level);
        if (m == null) {
            return null;
        }
        m.moveTo(at.getX() + 0.5, at.getY() + (m instanceof LightSwarm ? 1.2 : 0), at.getZ() + 0.5, level.random.nextFloat() * 360f, 0f);
        boolean full = MoonMob.fullMoon(level);
        if (m instanceof LightSwarm) {
            m.tuneMoon(2, full, false, LightSwarm.HP, LightSwarm.DMG);
        } else if (m instanceof ShadeStrider) {
            m.tuneMoon(2, full, false, ShadeStrider.HP, ShadeStrider.DMG);
        } else if (m instanceof MirrorBearer) {
            m.tuneMoon(2, full, false, MirrorBearer.HP, MirrorBearer.DMG);
        }
        level.addFreshEntity(m);
        return m;
    }

    // ---------------------------------------------------------------- 前のループの自分 (三人目。野営。頼みが済むまで)
    static void pastSelf(ServerLevel level, ServerPlayer p) {
        BlockPos at = MoonField.mark("past_self");
        if (!p.blockPosition().closerThan(at, 48)) {
            return;
        }
        List<TownGuestEntity> near = level.getEntities(TownSetup.GUEST.get(), new AABB(at).inflate(24), e -> e.isAlive() && PAST_SELF.equals(e.guestId()));
        boolean gone = level.players().stream().filter(q -> q.blockPosition().closerThan(at, 48))
                .allMatch(q -> FieldPuzzles.stage(q) >= FieldPuzzles.stageIndex("promise") + 1);
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
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 90f, 0f);
        e.setHome(at);
        level.addFreshEntity(e);
    }
}
