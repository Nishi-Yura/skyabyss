package jp.revati.skyabyss.titan;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import jp.revati.skyabyss.SkyAbyss;

// 巨神は都 (鐘の宿り) の中に来ない。都の中心から RADIUS の内には、
//  - 泳ぐ・歩く先 (steer の目標) を置かない (境目へ押し出す)
//  - 雷翼のうずくまる峰を選ばない
//  - それでも低く (地面から LOW 以内) 入ったら、境目へ押し戻す (高い空を渡るだけなら、そのまま)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanAvoid {
    private TitanAvoid() {
    }

    public static final double RADIUS = 200;
    private static final double LOW = 70;

    private static BlockPos center(MinecraftServer server) {
        if (server == null || !jp.revati.skyabyss.opening.site.SiteFinder.capital()) {
            return null;
        }
        return jp.revati.skyabyss.opening.OpeningSites.shelter(server).orElse(null);
    }

    public static boolean inside(Level level, double x, double z, double margin) {
        if (level.isClientSide || level.dimension() != Level.OVERWORLD) {
            return false;
        }
        BlockPos c = center(level.getServer());
        return c != null && Math.hypot(x - c.getX(), z - c.getZ()) < RADIUS + margin;
    }

    // 目標を都の外へ (内なら、中心から見て同じ向きの境目へ)。返り値 {x, z}
    public static double[] clamp(Level level, double tx, double tz) {
        if (level.isClientSide || level.dimension() != Level.OVERWORLD) {
            return new double[] {tx, tz};
        }
        BlockPos c = center(level.getServer());
        if (c == null) {
            return new double[] {tx, tz};
        }
        double dx = tx - c.getX();
        double dz = tz - c.getZ();
        double d = Math.hypot(dx, dz);
        if (d >= RADIUS) {
            return new double[] {tx, tz};
        }
        if (d < 1) {
            dx = 1;
            dz = 0;
            d = 1;
        }
        return new double[] {c.getX() + dx / d * RADIUS, c.getZ() + dz / d * RADIUS};
    }

    private static boolean titan(Entity e) {
        return e instanceof StoneWhaleEntity || e instanceof jp.revati.skyabyss.titan.tide.TideShellEntity
                || e instanceof jp.revati.skyabyss.titan.storm.StormWingEntity || e instanceof jp.revati.skyabyss.titan.ember.EmberTitanEntity;
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 3) {
            return;
        }
        BlockPos c = center(server);
        if (c == null) {
            return;
        }
        ServerLevel level = server.overworld();
        double r = RADIUS;
        AABB box = new AABB(c.getX() - r, level.getMinBuildHeight(), c.getZ() - r, c.getX() + r, level.getMaxBuildHeight() + 64, c.getZ() + r);
        for (Entity e : level.getEntities((Entity) null, box, TitanAvoid::titan)) {
            double dx = e.getX() - c.getX();
            double dz = e.getZ() - c.getZ();
            double d = Math.hypot(dx, dz);
            if (d >= RADIUS) {
                continue;
            }
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(e.getX()), (int) Math.floor(e.getZ()));
            if (e.getY() - ground > LOW) {
                continue;
            }
            if (d < 1) {
                dx = 1;
                dz = 0;
                d = 1;
            }
            double step = Math.min(RADIUS - d, 1.5);
            e.setPos(e.getX() + dx / d * step, e.getY(), e.getZ() + dz / d * step);
        }
    }
}
