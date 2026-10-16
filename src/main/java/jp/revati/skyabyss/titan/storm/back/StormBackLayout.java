package jp.revati.skyabyss.titan.storm.back;

import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

// 【仮の実装】背中の担当 (12 の 11.2節の担当 1) が出す「嵐の上」の部屋の座標 (11.1節の約束)。
// 中ボス (担当 3) と心臓 (担当 4) が本物を待たずに作り始めるために置いた。値は 12 の 3.3節の表のまま。
// 担当 3 と担当 4 の worktree で同じ中身にしてある (合わせるときにぶつからないように)。統合で担当 1 の本物に置き換える
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
    // 右手首の瘤 (雷獣の巣): x 116〜140、z -30〜-6、床 143 (人が立つ高さ)、直径 26、天井 158
    public static final BlockPos RAIJU_NEST = new BlockPos(128, 143, -18);
    public static final int RAIJU_NEST_RADIUS = 13;
    public static final int RAIJU_NEST_CEILING = 158;
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
