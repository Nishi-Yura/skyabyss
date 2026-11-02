package jp.revati.skyabyss.tower;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// あいだの塔の次元 (skyabyss:tower。暗い虚空、data/skyabyss/dimension*/tower.json) と区画 (docs/design/54 の 2.4)。
//   区画   SLOTS 個。区画 n の原点は x = n × SLOT_DX。一つの隊が一つの区画を使う (同じ時刻に何隊でも)
//   舞台   橋 (ARENA。tools/gen/world/build_tower.py)。原点は橋の真ん中の上面 (y ARENA_Y)
//   敵     湧く敵は各章の世界の敵をそのまま使い、強さは塔の数字で入れ、人数で伸ばす (FOE_STEP)
public final class TowerWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tower"));
    public static final int SLOTS = 16;
    public static final int SLOT_DX = 256;
    public static final String ARENA = "bridge";
    public static final int ARENA_Y = 120;
    public static final String TAG = "skyabyss_tower_foe";
    // 階の敵の体力と傷の、一人増えるごとの伸び
    public static final double FOE_STEP = 0.5;
    // 落ちたとみなす高さ (橋の上面より下)
    public static final int FALL = 12;

    private TowerWorld() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean in(net.minecraft.world.entity.Entity e) {
        return e.level().dimension() == LEVEL;
    }

    public static BlockPos origin(int slot) {
        return new BlockPos(slot * SLOT_DX, ARENA_Y, 0);
    }

    // 区画 slot の橋の印の世界の座標
    public static BlockPos at(int slot, String mark) {
        return origin(slot).offset(TowerTerrain.of(ARENA).mark(mark));
    }

    public static Vec3 center(int slot, String mark) {
        return Vec3.atBottomCenterOf(at(slot, mark));
    }

    public static int pour(ServerLevel level, int slot) {
        return TowerTerrain.of(ARENA).pour(level, origin(slot));
    }

    // ---------------------------------------------------------------- 敵
    public static double foeScale(int players) {
        return 1 + FOE_STEP * (Math.max(1, Math.min(12, players)) - 1);
    }

    // 塔の敵を湧かせる (強さは高い所で入れてから置く: 深淵の敵は湧いた高さで強くなるので)
    public static Mob foe(ServerLevel level, EntityType<? extends Mob> type, Vec3 at, double hp, double dmg, int players, int runId) {
        Mob m = type.create(level);
        if (m == null) {
            return null;
        }
        double k = foeScale(players);
        m.moveTo(at.x, 300, at.z, level.random.nextFloat() * 360f, 0f);
        if (m instanceof CloudMob c) {
            c.tuneCloud(1, hp * k, dmg * (1 + 0.15 * (Math.min(12, players) - 1) / 11.0));
        } else if (m instanceof MoonMob mm) {
            mm.tuneMoon(0, false, false, hp * k / MoonMob.SECTION_HP[0], dmg / MoonMob.SECTION_DMG[0]);
        } else if (m instanceof jp.revati.skyabyss.abyss.mobs.AbyssMobB a) {
            a.tune(1, hp * k, dmg);
        }
        m.moveTo(at.x, at.y, at.z, m.getYRot(), 0f);
        m.addTag(TAG);
        m.addTag(TAG + "_" + runId);
        level.addFreshEntity(m);
        return m;
    }
}
