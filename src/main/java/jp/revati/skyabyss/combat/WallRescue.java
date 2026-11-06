package jp.revati.skyabyss.combat;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 壁の中の窒息を受けない: 人はブロックを置けず壊せない (冒険の決まり) ので、壁に埋まるのはいつも部屋の作りか仕掛けのせい。
// 2026-11-07 の声「潮招き、マップがゴミすぎて、ずっと窒息ダメージ喰らってる」(闘の部屋に潮招きの岩の小島・台の段を置くとき、上に立っていた人が埋まった)。
// 窒息の傷 (in_wall) を取り消し、上 (8 まで)、なければまわり (横 3 まで) のいちばん近い立てる所へ出す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class WallRescue {
    static final int UP = 8;
    static final int SIDE = 3;

    private WallRescue() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !event.getSource().is(DamageTypes.IN_WALL) || p.isSpectator()) {
            return;
        }
        event.setCanceled(true);
        free(p);
    }

    // 体が何かに埋まっているか
    public static boolean stuck(ServerPlayer p) {
        return !p.level().noCollision(p, p.getBoundingBox().deflate(0.05));
    }

    // 埋まった人を立てる所へ出す。出したら true
    public static boolean free(ServerPlayer p) {
        Vec3 to = freeSpot(p);
        if (to == null) {
            return false;
        }
        p.teleportTo(to.x, to.y, to.z);
        if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            // 試験の偽の人は通信がないので、位置だけ直接動かす
            p.setPos(to.x, to.y, to.z);
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        return true;
    }

    @Nullable
    static Vec3 freeSpot(ServerPlayer p) {
        BlockPos at = p.blockPosition();
        for (int dy = 1; dy <= UP; dy++) {
            Vec3 v = fits(p, at.above(dy));
            if (v != null) {
                return v;
            }
        }
        for (int r = 1; r <= SIDE; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy = 0; dy <= 2; dy++) {
                        Vec3 v = fits(p, at.offset(dx, dy, dz));
                        if (v != null) {
                            return v;
                        }
                    }
                }
            }
        }
        return null;
    }

    // その足もとに立てるか (体が収まり、下が固い)。立てるならその位置
    @Nullable
    static Vec3 fits(ServerPlayer p, BlockPos feet) {
        Vec3 v = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
        AABB box = p.getDimensions(p.getPose()).makeBoundingBox(v);
        BlockPos below = feet.below();
        if (p.level().noCollision(p, box) && !p.level().getBlockState(below).getCollisionShape(p.level(), below).isEmpty()) {
            return v;
        }
        return null;
    }
}
