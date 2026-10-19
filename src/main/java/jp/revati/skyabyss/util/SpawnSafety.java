package jp.revati.skyabyss.util;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

// 敵が壁や地面の中に湧かない決まり (全部の湧き口と波に共通。ユーザーの試遊の声)。
// 敵 (このモッドの MONSTER の生き物と、背中の中の MONSTER) が世界に入る瞬間 (EntityJoinLevelEvent) に、次を調べる:
//   1. 体の箱 (bounding box) がブロックに食い込んでいない (当たりのあるブロックと重ならない)
//   2. 地を歩く敵は、足もと 3 ブロック以内に立てる床がある (水の中・空を飛ぶ敵は不要)
// 満たさなければ、そこから水平 3・上 4・下 3 の範囲で、いちばん近い満たす場所へ動かす。見つからなければ湧かせない (キャンセル)。
// 心臓・巨神・中ボスのような大きな敵 (幅 3.5 超) は、部屋の作りで置き場が決まっているので対象にしない。
// チャンクから読み込まれるだけの再登場 (loadedFromDisk) は触らない。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SpawnSafety {
    public static final double MAX_WIDTH = 3.5;
    public static final int SEARCH_H = 3;
    public static final int SEARCH_UP = 4;
    public static final int SEARCH_DOWN = 3;
    public static final int FLOOR_REACH = 3;

    // 試験用: 動かした数・取りやめた数
    public static int moved;
    public static int refused;

    private SpawnSafety() {
    }

    // この敵に決まりを当てるか
    public static boolean applies(Mob mob) {
        EntityType<?> type = mob.getType();
        if (mob.getBbWidth() > MAX_WIDTH) {
            return false;
        }
        if (type.getCategory() != MobCategory.MONSTER && !(mob instanceof net.minecraft.world.entity.monster.Enemy)) {
            return false;
        }
        boolean ours = BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().startsWith(SkyAbyss.MODID);
        return ours || Titans.titanOfBack(mob.level().dimension()) != null;
    }

    // 空を飛ぶ (床を要らない) 敵か
    public static boolean flier(Mob mob) {
        return mob.isNoGravity() || mob.getMoveControl() instanceof FlyingMoveControl || mob.getNavigation() instanceof FlyingPathNavigation
                || mob instanceof net.minecraft.world.entity.FlyingMob || mob instanceof net.minecraft.world.entity.animal.FlyingAnimal;
    }

    // mob をその位置に置けるか (箱が重ならない・必要なら床がある)
    public static boolean ok(ServerLevel level, Mob mob) {
        return ok(level, mob, mob.getBoundingBox());
    }

    public static boolean ok(ServerLevel level, Mob mob, AABB box) {
        if (!level.noCollision(mob, box)) {
            return false;
        }
        if (flier(mob)) {
            return true;
        }
        // 水の中なら、床は要らない
        BlockPos feet = BlockPos.containing((box.minX + box.maxX) / 2, box.minY + 0.1, (box.minZ + box.maxZ) / 2);
        if (!level.getFluidState(feet).isEmpty()) {
            return true;
        }
        return hasFloor(level, box);
    }

    // 箱の足もとから FLOOR_REACH ブロック以内に、立てる床 (当たりのあるブロック) があるか
    public static boolean hasFloor(ServerLevel level, AABB box) {
        double cx = (box.minX + box.maxX) / 2;
        double cz = (box.minZ + box.maxZ) / 2;
        for (int d = 0; d <= FLOOR_REACH; d++) {
            BlockPos under = BlockPos.containing(cx, box.minY - 0.05 - d, cz);
            BlockState st = level.getBlockState(under);
            if (!st.getCollisionShape(level, under).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // 満たす場所を探して mob を動かす。動かさずに済んだ・動かせた: true / 見つからない: false
    public static boolean settle(ServerLevel level, Mob mob) {
        if (ok(level, mob)) {
            return true;
        }
        double ox = mob.getX();
        double oy = mob.getY();
        double oz = mob.getZ();
        @Nullable double[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int dy = -SEARCH_DOWN; dy <= SEARCH_UP; dy++) {
            for (int dx = -SEARCH_H; dx <= SEARCH_H; dx++) {
                for (int dz = -SEARCH_H; dz <= SEARCH_H; dz++) {
                    double d = dx * dx + dz * dz + dy * dy * 1.5;
                    if (d >= bestD) {
                        continue;
                    }
                    double nx = Math.floor(ox) + 0.5 + dx;
                    double ny = oy + dy;
                    double nz = Math.floor(oz) + 0.5 + dz;
                    AABB box = mob.getBoundingBox().move(nx - ox, ny - oy, nz - oz);
                    if (ok(level, mob, box)) {
                        best = new double[] {nx, ny, nz};
                        bestD = d;
                    }
                }
            }
        }
        if (best == null) {
            return false;
        }
        mob.setPos(best[0], best[1], best[2]);
        moved++;
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof Mob mob) || !applies(mob)) {
            return;
        }
        if (!settle(level, mob)) {
            refused++;
            SkyAbyss.LOGGER.warn("Refused to spawn {} inside blocks at {}", BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()), mob.blockPosition());
            event.setCanceled(true);
        }
    }
}
