package jp.revati.skyabyss.titan.solid;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.travel.Voyages;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

// 巨神の体に入れない (docs/design/02 の「体に入れない」、08・12・13 の同じ節)。
// 巨神ごとの体積 (TitanVolume) の中へ入った人を、いちばん近い外へやさしく押し出す。矢や弾は体で消える。乗り場の通り道 (SolidTitan.corridors) は入れる。
// 押し出しはサーバーの tick で行う (入った人のそばに巨神があるあいだだけ。巨神は数体なので、調べるのは巨神の数だけ)。カメラは TitanSolidClient が入れない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class TitanSolids {
    private static final Set<Entity> TITANS = ConcurrentHashMap.newKeySet();
    // 体積の中に居続けた tick 数 (押し出しが効かないときの保険)
    private static final Map<UUID, Integer> INSIDE = new HashMap<>();
    // これだけ水平に離れていれば調べない (翼のいちばん遠くまで 130 ほど + 余白)
    private static final double RANGE = 190.0;
    private static final int TELEPORT_AFTER = 40;
    private static final double TELEPORT_DEPTH = 7.0;

    private TitanSolids() {
    }

    // 試験用: 入れた巨神だけを数える
    static void track(Entity titan) {
        TITANS.add(titan);
    }

    static void untrack(Entity titan) {
        TITANS.remove(titan);
    }

    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.getEntity() instanceof SolidTitan) {
            TITANS.add(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onLeave(EntityLeaveLevelEvent event) {
        if (event.getEntity() instanceof SolidTitan) {
            TITANS.remove(event.getEntity());
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        INSIDE.remove(event.getEntity().getUUID());
    }

    // 今の世界にいる体のある巨神 (サーバー側とクライアント側で別の実体)
    public static Iterable<Entity> titans() {
        return TITANS;
    }

    // ---------------------------------------------------------------- 座標

    // 世界の点の、巨神から見た局所 (横 = 左が +, 上, 前)
    public static Vec3 toLocal(Entity titan, Vec3 world) {
        return toLocal(titan.getX(), titan.getY(), titan.getZ(), titan.getYRot(), world);
    }

    public static Vec3 toLocal(double tx, double ty, double tz, float yaw, Vec3 world) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        double dx = world.x - tx;
        double dz = world.z - tz;
        return new Vec3(dx * fz - dz * fx, world.y - ty, dx * fx + dz * fz);
    }

    // 局所の移動量 (横, 上, 前) を世界の移動量へ
    public static Vec3 toWorldDelta(float yaw, Vec3 local) {
        double fx = -Mth.sin(yaw * Mth.DEG_TO_RAD);
        double fz = Mth.cos(yaw * Mth.DEG_TO_RAD);
        return new Vec3(fx * local.z + fz * local.x, local.y, fz * local.z - fx * local.x);
    }

    // ---------------------------------------------------------------- 判定 (試験からも使う)

    // 局所の点が、この巨神の立入禁止の中 (通り道を除く) か。core = true なら体そのもの (0.5 膨らませた方)
    public static boolean blocked(TitanVolume volume, java.util.List<SolidTitan.Corridor> corridors, Vec3 local, boolean core) {
        boolean in = core ? volume.inCore(local.x, local.y, local.z) : volume.inKeepOut(local.x, local.y, local.z);
        if (!in) {
            return false;
        }
        for (SolidTitan.Corridor c : corridors) {
            if (c.contains(local.x, local.y, local.z)) {
                return false;
            }
        }
        return true;
    }

    // 局所の点から、立入禁止の外へのいちばん近い移動 (局所)。外なら null
    public static Vec3 pushOutLocal(TitanVolume volume, java.util.List<SolidTitan.Corridor> corridors, Vec3 local, Predicate<Vec3> canStand) {
        return volume.exit(local.x, local.y, local.z, p -> blocked(volume, corridors, p, false), canStand);
    }

    // この人は体に入れない扱いをするか (観戦者・乗っている人・乗り場の昇りや旅の途中の人は除く)
    public static boolean exempt(net.minecraft.world.entity.player.Player player) {
        if (player.isSpectator() || player.isPassenger() || !player.isAlive()) {
            return true;
        }
        UUID id = player.getUUID();
        return TitanTravel.isLifting(id) || Voyages.involvesPlayer(id);
    }

    // ---------------------------------------------------------------- 人

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || TITANS.isEmpty()) {
            return;
        }
        boolean in = false;
        if (!exempt(player)) {
            for (Entity titan : TITANS) {
                if (titan.level() != player.level() || !(titan instanceof SolidTitan solid) || !solid.solidActive()) {
                    continue;
                }
                if (Math.abs(titan.getX() - player.getX()) > RANGE || Math.abs(titan.getZ() - player.getZ()) > RANGE) {
                    continue;
                }
                if (pushPlayer(player, titan, solid)) {
                    in = true;
                    break;
                }
            }
        }
        if (in) {
            INSIDE.merge(player.getUUID(), 1, Integer::sum);
        } else {
            INSIDE.remove(player.getUUID());
        }
    }

    // 立入禁止の縁から depth ブロック入ったときの押し出しの速さ (ブロック/tick)。縁では歩き (0.2)、奥ほど強く、飛んでいても 3 ブロックの内には入れない
    public static double pushSpeed(double depth) {
        return Math.min(1.4, 0.2 + 0.35 * depth);
    }

    // 押し出したら true
    private static boolean pushPlayer(ServerPlayer player, Entity titan, SolidTitan solid) {
        TitanVolume volume = TitanVolume.get(solid.volumeName());
        if (volume == null) {
            return false;
        }
        java.util.List<SolidTitan.Corridor> corridors = solid.corridors();
        float yaw = titan.getYRot();
        // 体の真ん中 (足の 0.9 上) で測る
        Vec3 center = player.position().add(0, 0.9, 0);
        Vec3 local = toLocal(titan, center);
        if (!blocked(volume, corridors, local, false)) {
            return false;
        }
        boolean hard = blocked(volume, corridors, local, true);
        // 行き先に立てるか (地面や壁の中へは押し込まない)
        Predicate<Vec3> canStand = p -> {
            Vec3 move = toWorldDelta(yaw, p.subtract(local));
            return player.level().noCollision(player, player.getBoundingBox().move(move));
        };
        Vec3 out = pushOutLocal(volume, corridors, local, canStand);
        if (out == null) {
            return false;
        }
        Vec3 world = toWorldDelta(yaw, out);
        double len = world.length();
        int inside = INSIDE.getOrDefault(player.getUUID(), 0);
        if (hard || inside >= TELEPORT_AFTER || len > TELEPORT_DEPTH) {
            Vec3 to = player.position().add(world);
            player.setPos(to.x, to.y, to.z);
            player.connection.teleport(to.x, to.y, to.z, player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            player.fallDistance = 0;
            return true;
        }
        Vec3 dir = world.scale(1.0 / len);
        double speed = pushSpeed(len);
        Vec3 dm = player.getDeltaMovement();
        double along = dm.dot(dir);
        if (along < 0) {
            dm = dm.subtract(dir.scale(along));
        }
        player.setDeltaMovement(dm.add(dir.scale(speed)));
        player.hurtMarked = true;
        player.fallDistance = 0;
        return true;
    }

    // ---------------------------------------------------------------- 矢・弾

    // 飛んでいる矢や弾が体に入ったら消す (前の tick の位置から今の位置までを 1 ブロックごとに調べる。細いひれを抜けないように)
    @SubscribeEvent
    static void onProjectileTick(EntityTickEvent.Post event) {
        if (TITANS.isEmpty() || !(event.getEntity() instanceof Projectile projectile) || !(projectile.level() instanceof ServerLevel level) || projectile.isRemoved()) {
            return;
        }
        Vec3 now = projectile.position();
        Vec3 before = new Vec3(projectile.xo, projectile.yo, projectile.zo);
        for (Entity titan : TITANS) {
            if (titan.level() != level || !(titan instanceof SolidTitan solid) || !solid.solidActive()) {
                continue;
            }
            if (Math.abs(titan.getX() - now.x) > RANGE || Math.abs(titan.getZ() - now.z) > RANGE) {
                continue;
            }
            TitanVolume volume = TitanVolume.get(solid.volumeName());
            if (volume == null) {
                continue;
            }
            java.util.List<SolidTitan.Corridor> corridors = solid.corridors();
            double dist = now.distanceTo(before);
            int steps = Math.max(1, (int) Math.ceil(dist));
            for (int i = 0; i <= steps; i++) {
                Vec3 p = before.lerp(now, (double) i / steps);
                if (blocked(volume, corridors, toLocal(titan, p), true)) {
                    level.sendParticles(ParticleTypes.POOF, p.x, p.y, p.z, 4, 0.1, 0.1, 0.1, 0.02);
                    projectile.discard();
                    return;
                }
            }
        }
    }
}
