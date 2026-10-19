package jp.revati.skyabyss.titan.dungeon;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.BoneGull;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout.Box;
import jp.revati.skyabyss.titan.group.FirstRun;
import jp.revati.skyabyss.titan.group.Groups;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

// 岩鯨の背の「開けた」場所 (西の翼の竪穴 WB・崩れ橋 WC。天井が高く、空が見える) に出る骨鳥 (21 の 3.3。鐘楼・胸びれの小物の先取り)。
// 広間の波ではなく、グループごとの環境の敵: そのグループの人が場所に入ると 2 体 (近くの人が増えるごとに半数、最大 4)、倒しきると
// 90 秒あとにまた出る。人が離れる・グループが戻る (resetGroup) と消える。MOB_TAG つきなので保存されない。
// 初回だけの部屋 (FirstRun) として登録された場所では、既知の道のグループには出さない (allowSpawnAt)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BackAmbientMobs {
    public static final List<Box> GULL_ZONES = List.of(TitanBackLayout.WB, TitanBackLayout.WC);
    public static final int RESPAWN_TICKS = 20 * 90;
    private static final long PENDING = Long.MAX_VALUE;
    // (グループ, 場所) → 次に出してよい時刻 (PENDING = 出したあと、倒しきるのを待っている)
    private static final Map<String, Long> NEXT = new HashMap<>();

    private BackAmbientMobs() {
    }

    // 近くの人数に応じた骨鳥の数: 2 体 + 2 人増えるごとに 1 体、最大 4
    public static int gullCount(int players) {
        return Math.min(4, 2 + Math.max(0, players - 1) / 2);
    }

    @SubscribeEvent
    static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == TitanTravel.TITAN_BACK && level.getGameTime() % 20 == 7) {
            tick(level);
        }
    }

    public static void clear() {
        NEXT.clear();
    }

    // 試験用にも使う。1 回ぶんの見回り
    public static void tick(ServerLevel level) {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !TitanBackData.get(level).isBuilt()) {
            return;
        }
        for (Box zone : GULL_ZONES) {
            AABB box = zone.aabb();
            // 人のいなくなった場所の骨鳥は消す
            for (BoneGull gull : level.getEntitiesOfClass(BoneGull.class, box.inflate(48), g -> g.getTags().contains(TitanBackTrials.MOB_TAG))) {
                UUID group = gull.groupId();
                if (group != null && Groups.inBox(level, group, box.inflate(36)).isEmpty()) {
                    gull.discard();
                }
            }
            for (UUID group : Groups.groupsIn(level, box)) {
                if (!FirstRun.allowSpawnAt(level, group, zone.center())) {
                    continue;
                }
                String key = group + "|" + zone.x1() + "," + zone.z1();
                int alive = level.getEntitiesOfClass(BoneGull.class, box.inflate(24), g -> g.isAlive() && group.equals(g.groupId())).size();
                long now = level.getGameTime();
                long next = NEXT.getOrDefault(key, 0L);
                if (alive > 0) {
                    continue;
                }
                if (next == PENDING) {
                    NEXT.put(key, now + RESPAWN_TICKS);
                    continue;
                }
                if (now < next) {
                    continue;
                }
                List<ServerPlayer> inside = Groups.inBox(level, group, box);
                if (inside.isEmpty()) {
                    continue;
                }
                spawn(level, zone, group, gullCount(inside.size()));
                NEXT.put(key, PENDING);
            }
        }
    }

    // 場所の上の空き (床の上 5〜7) に散らして出す
    public static List<BoneGull> spawn(ServerLevel level, Box zone, UUID group, int count) {
        List<BoneGull> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            for (int attempt = 0; attempt < 12; attempt++) {
                int x = zone.x1() + 2 + level.random.nextInt(Math.max(1, zone.x2() - zone.x1() - 3));
                int z = zone.z1() + 2 + level.random.nextInt(Math.max(1, zone.z2() - zone.z1() - 3));
                BlockPos at = new BlockPos(x, zone.floorY() + 5 + level.random.nextInt(3), z);
                if (level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()) {
                    Mob mob = TitanBackTrials.spawnGuard(level, "gull", at, group);
                    if (mob instanceof BoneGull gull) {
                        out.add(gull);
                    }
                    break;
                }
            }
        }
        return out;
    }
}
