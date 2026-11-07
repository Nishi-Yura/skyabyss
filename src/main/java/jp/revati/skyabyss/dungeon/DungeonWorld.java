package jp.revati.skyabyss.dungeon;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.tower.TowerTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

// 地下のダンジョンの組ごとの写し (docs/design/55 の 2 章「層の門から先は組ごとの写し」)。次元 skyabyss:dungeon (暗い虚空、天井あり)。
//   区画   ダンジョンごとに SLOTS 個。区画の原点は x = (ダンジョンの番号 × SLOTS + 区画) × SLOT_DX。区画はそのダンジョンだけが使う
//          (流し直しは違うブロックだけ置くので、二度目からは扉・宝箱・仕掛けを戻すだけで済む)
//   層     層 k の原点は z = k × FLOOR_DZ、y = Y (層の手元の y 0 = 立つ高さ)
public final class DungeonWorld {
    public static final ResourceKey<Level> LEVEL = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dungeon"));
    public static final int SLOTS = 12;
    public static final int SLOT_DX = 256;
    public static final int FLOOR_DZ = 320;
    public static final int Y = 96;

    private DungeonWorld() {
    }

    public static ServerLevel level(MinecraftServer server) {
        return server.getLevel(LEVEL);
    }

    public static boolean in(Entity e) {
        return e.level().dimension() == LEVEL;
    }

    public static BlockPos origin(Dungeon d, int slot, int floor) {
        return new BlockPos((d.ordinal() * SLOTS + slot) * SLOT_DX, Y, floor * FLOOR_DZ);
    }

    public static TowerTerrain terrain(Dungeon d, int floor) {
        return TowerTerrain.of("dungeon", d.terrain(floor));
    }

    // 層の印の世界の座標
    public static BlockPos at(Dungeon d, int slot, int floor, String mark) {
        return origin(d, slot, floor).offset(terrain(d, floor).mark(mark));
    }

    public static Vec3 center(Dungeon d, int slot, int floor, String mark) {
        return Vec3.atBottomCenterOf(at(d, slot, floor, mark));
    }

    // 世界の座標から (ダンジョン, 区画, 層, 手元の座標)。写しの外なら null
    public record Place(Dungeon dungeon, int slot, int floor, BlockPos local) {
    }

    public static Place place(BlockPos pos) {
        int g = Math.floorDiv(pos.getX() + SLOT_DX / 2, SLOT_DX);
        int floor = Math.floorDiv(pos.getZ() + FLOOR_DZ / 2, FLOOR_DZ);
        if (g < 0 || floor < 0 || floor >= Dungeon.MAX_FLOORS) {
            return null;
        }
        int di = g / SLOTS;
        if (di >= Dungeon.values().length) {
            return null;
        }
        Dungeon d = Dungeon.values()[di];
        if (floor >= d.floors().length) {
            return null;
        }
        int slot = g % SLOTS;
        BlockPos local = pos.subtract(origin(d, slot, floor));
        return new Place(d, slot, floor, local);
    }

    // 宝箱の受け取りの鍵 (loot/SharedChests): 写しの宝箱は区画によらず「ダンジョン・層・手元の座標」で一つ (人ごとの 20 分の待ちが区画をまたいで効く)
    public static String chestKey(ServerLevel level, BlockPos pos) {
        if (level.dimension() != LEVEL) {
            return null;
        }
        Place p = place(pos);
        return p == null ? null : LEVEL.location() + "/" + p.dungeon().id + "/" + p.floor() + "@" + p.local().asLong();
    }
}
