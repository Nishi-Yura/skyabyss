package jp.revati.skyabyss.titan.group;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanBacks;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

// 背中で倒れて休み場に目覚めた直後の守り (docs/design/02 の 7.3、rpg_brushup A3)。戦いの扉の再入場 (Reentry) と同じ 3 秒:
//  - 光る・傷を受けない (ReentryRules.guard の守りを使う)
//  - その間は、敵に狙われない (GroupEvents.onTarget / GuardMob.canAttack)。すでに狙っていた敵は狙いを外す
//  - 目覚めた場所の半径 8 ブロックには、ZONE_TICKS (30 秒) の間、敵が新しく湧かない・入ってこない
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RespawnGuard {
    public static final int TICKS = Reentry.GLOW_TICKS;
    public static final double RADIUS = 8.0;
    public static final long ZONE_TICKS = 600;

    private record Zone(ResourceKey<Level> dimension, Vec3 center, long until) {
    }

    private static final List<Zone> ZONES = new ArrayList<>();

    private RespawnGuard() {
    }

    // 背中の復活でテレポートしたあとに呼ぶ
    public static void begin(ServerPlayer player) {
        ReentryRules.guard(player, false);
        long now = player.level().getGameTime();
        synchronized (ZONES) {
            ZONES.removeIf(z -> z.until <= now);
            ZONES.add(new Zone(player.level().dimension(), player.position(), now + ZONE_TICKS));
        }
        // すでに狙っていた敵は、狙いを外す
        release(player.level().getEntitiesOfClass(Mob.class, new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(RADIUS * 3)), player);
    }

    // mobs のうち player を狙っているものの狙いを外す
    public static void release(Iterable<? extends Mob> mobs, ServerPlayer player) {
        for (Mob mob : mobs) {
            if (mob.getTarget() == player) {
                mob.setTarget(null);
            }
        }
    }

    // その位置が、復活の場所の近く (敵が湧かない所) か
    public static boolean quiet(ResourceKey<Level> dimension, Vec3 pos, long now) {
        synchronized (ZONES) {
            for (Zone z : ZONES) {
                if (z.until > now && z.dimension.equals(dimension) && z.center.distanceToSqr(pos) <= RADIUS * RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void clear() {
        synchronized (ZONES) {
            ZONES.clear();
        }
    }

    // 敵が背中に新しく加わる (湧く・召喚される) とき、復活の場所の近くなら止める。ワールドから読み込まれる敵・ボス (常駐) は止めない
    @SubscribeEvent
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || event.getLevel().isClientSide() || !(event.getEntity() instanceof Mob mob) || !(mob instanceof Enemy)
                || mob.isPersistenceRequired() || !TitanBacks.isBack(event.getLevel().dimension())) {
            return;
        }
        if (quiet(event.getLevel().dimension(), mob.position(), event.getLevel().getGameTime())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        clear();
    }
}
