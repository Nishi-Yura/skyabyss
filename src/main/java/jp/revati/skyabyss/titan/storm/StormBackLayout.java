package jp.revati.skyabyss.titan.storm;

import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// [仮の実装 / STUB] 背中の担当 (12 の 11.2節の担当 1) が出す「嵐の上」の部屋の座標 (11.1節の約束)。
// 心臓の担当が、本物を待たずに作り始めるために置いた最小限のもの。値は 12 の 3.3節の表のまま。
// 統合のときに担当 1 の本物に置き換える (名前と意味はそろえてある)
public final class StormBackLayout {
    // 背中のディメンション skyabyss:storm_back
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back"));

    // 竜骨の間 (3.6節): 中心 (0, 121, -14)、床 121、半径 15、天井 134。入口は北の壁 (z -30)
    public static final BlockPos KEEL_CENTER = new BlockPos(0, 121, -14);
    public static final int KEEL_FLOOR = 121;
    public static final int KEEL_RADIUS = 15;
    public static final int KEEL_CEILING = 134;
    public static final BlockPos KEEL_ENTRANCE = new BlockPos(0, 121, -30);
    // 竜骨の前室 (休み場 heart)。x -3〜3、z -36〜-30
    public static final BlockPos KEEL_ANTE = new BlockPos(0, 121, -34);
    // 雷獣の巣 (右手首の瘤)。中心 (128, 143, -18)
    public static final BlockPos RAIJU_NEST = new BlockPos(128, 143, -18);
    // 尾の間 (心臓の光の柱から戻る所) と、到着 (尾羽の上)
    public static final BlockPos TAIL_HALL = new BlockPos(0, 146, 27);
    public static final BlockPos ARRIVAL = new BlockPos(0, 143, 94);
    // 休み場 (7.3節の 7 つ)
    public static final Map<String, BlockPos> CHECKPOINTS = Map.of(
            "hub", new BlockPos(0, 137, 4),
            "w0", new BlockPos(-20, 137, -6),
            "w1", new BlockPos(-70, 142, -18),
            "e0", new BlockPos(20, 137, -6),
            "e1", new BlockPos(70, 142, -18),
            "f0", new BlockPos(0, 150, -50),
            "heart", KEEL_ANTE);

    private StormBackLayout() {
    }
}
