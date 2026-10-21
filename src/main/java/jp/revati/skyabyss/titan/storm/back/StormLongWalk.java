package jp.revati.skyabyss.titan.storm.back;

import static jp.revati.skyabyss.titan.storm.back.StormBackLayout.*;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

// 周回の風切りの骨 (S01) の前の道「風の渡り」(32 の 4 章、R5。版 31。trial/LongWalk)。
// 翼の前の縁の外の空に浮かぶ羽の台をいくつも跳び渡り、翼の付け根の洞 (前の縁を抜ける通路) から肩の部屋 W0 の北の壁の戸へ入る。
//   休み場 wp (7x7 の台。焚き火) → 3x3 の台 12 (1 段ずつ上り下り。台と台の間は 2〜3 マスの溝) → 張り出しの台 → 洞 → 戸 (WP_GATE)
// 落ちると嵐の雲 (CLOUD_Y) の下で投げ返され、最後の休み場 (wp) へ戻る (StormTravel)。凪でも嵐でも同じ (風の羽根も昇り風も使わない)。
// 戸は、周回のグループの今の段が S01 のときだけ、そのグループに開く。初回のグループには閉じた戸 (肩の部屋の北の壁)
public final class StormLongWalk {
    private StormLongWalk() {
    }

    // 台 (中心・床の高さ・半径)。0 番は休み場の台、最後は洞の口の張り出し
    public record Pad(int x, int y, int z, int half) {
        boolean has(int px, int pz) {
            return Math.abs(px - x) <= half && Math.abs(pz - z) <= half;
        }
    }

    public static final List<Pad> PADS = List.of(
            new Pad(-104, 151, -100, 3),
            new Pad(-97, 151, -100, 1), new Pad(-92, 152, -100, 1), new Pad(-92, 152, -95, 1), new Pad(-87, 153, -93, 1),
            new Pad(-82, 153, -90, 1), new Pad(-77, 152, -87, 1), new Pad(-77, 152, -82, 1), new Pad(-72, 151, -79, 1),
            new Pad(-69, 151, -74, 1), new Pad(-66, 152, -69, 1), new Pad(-63, 152, -64, 1), new Pad(-60, 151, -59, 1),
            new Pad(-59, 151, -54, 2));
    // 洞 (翼の前の縁を抜ける通路。床 151、内側の高さ 5) と口の張り出しの北の端
    public static final int PASS_X1 = -60;
    public static final int PASS_X2 = -58;
    public static final int PASS_Z1 = -51;
    public static final int PASS_Z2 = BONE_Z1 - 2;

    // 休み場の台の焚き火と立つ所 (台の真ん中と、その南東)
    public static final BlockPos REST_FIRE = new BlockPos(-104, BONE_FLOOR + 1, -100);
    public static final BlockPos REST_SPAWN = new BlockPos(-102, BONE_FLOOR + 1, -98);
    public static final Area REST_AREA = new Area(-107, -101, -103, -97, BONE_FLOOR, 8);
    // 戸 (肩の部屋 W0 の北の壁)
    public static final List<BlockPos> GATE = gate();

    private static List<BlockPos> gate() {
        List<BlockPos> out = new ArrayList<>();
        for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + 5; y++) {
            for (int x = PASS_X1; x <= PASS_X2; x++) {
                out.add(new BlockPos(x, y, BONE_Z1 - 1));
            }
        }
        return List.copyOf(out);
    }

    // 空へ開いていてよい所 (洞の口。StormBackLayout.openToSky)
    static boolean open(int x, int y, int z) {
        return x >= PASS_X1 - 1 && x <= PASS_X2 + 1 && z >= PASS_Z1 - 1 && z <= PASS_Z1 + 1 && y >= BONE_FLOOR && y <= BONE_FLOOR + 7;
    }

    // 前の道の中 (試練の範囲・休み場の外の数え)
    public static boolean inWalk(double x, double y, double z) {
        return x >= -110 && x <= -54 && z >= -106 && z <= BONE_Z1 - 1 && y >= BONE_FLOOR - 4 && y <= BONE_FLOOR + 12;
    }

    // 建てる (StormBirdBuilder の仕上げ。体を掘ったあと、壁の皮を張る前)
    static void build(StormKit kit) {
        // 洞: 翼の前の縁を抜ける。壁は骨の様式 (掘った所として覚え、あとで皮が張られる)
        kit.room(PASS_X1, PASS_X2, PASS_Z1, PASS_Z2, BONE_FLOOR, 5, StormKit.Style.BONE, 4);
        // 口 (空の側の壁) を開ける
        for (int x = PASS_X1; x <= PASS_X2; x++) {
            for (int y = BONE_FLOOR + 1; y <= BONE_FLOOR + 5; y++) {
                kit.air(x, y, PASS_Z1 - 1);
            }
        }
        // 台
        for (int i = 0; i < PADS.size(); i++) {
            pad(kit, PADS.get(i), i == 0);
        }
        // 戸の前後 (肩の部屋の北の壁を抜く)。戸のブロックはそのあと
        for (BlockPos p : GATE) {
            kit.put(p.getX(), p.getY(), p.getZ(), ModBlocks.SEALED_GATE.get().defaultBlockState());
        }
        // 張り出しの台の灯り (洞の口の両脇の柱の上)
        Pad b = PADS.get(PADS.size() - 1);
        for (int x : new int[] {b.x() - b.half(), b.x() + b.half()}) {
            kit.put(x, b.y() + 1, b.z() - b.half(), StormKit.CALCITE);
            kit.put(x, b.y() + 2, b.z() - b.half(), Blocks.LANTERN.defaultBlockState());
        }
    }

    // 羽の台: 縁は方解石、中は羽の縞。真ん中に海のランタン (休み場は焚き火)。下は先の細い骨の錐。上は空ける
    private static void pad(StormKit kit, Pad p, boolean rest) {
        int h = p.half();
        for (int dx = -h; dx <= h; dx++) {
            for (int dz = -h; dz <= h; dz++) {
                int x = p.x() + dx;
                int z = p.z() + dz;
                boolean edge = Math.abs(dx) == h || Math.abs(dz) == h;
                BlockState top = edge ? StormKit.CALCITE : StormKit.floor(StormKit.Style.FEATHER, x, z);
                if (!rest && dx == 0 && dz == 0) {
                    top = StormKit.LAMP;
                }
                kit.put(x, p.y(), z, top);
                for (int y = p.y() + 1; y <= p.y() + 6; y++) {
                    if (kit.get(x, y, z).isAir()) {
                        continue;
                    }
                    kit.put(x, y, z, StormKit.AIR);
                }
                // 錐: 半径が 1 段ごとに 1 ずつ細る
                int depth = Math.max(1, h + 2 - Math.max(Math.abs(dx), Math.abs(dz)));
                for (int k = 1; k <= depth; k++) {
                    kit.put(x, p.y() - k, z, k == depth ? StormKit.BONE : StormKit.CALCITE);
                }
            }
        }
        if (rest) {
            kit.campfire(REST_FIRE);
            // 台の四隅に低い柱と灯り
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sz = -1; sz <= 1; sz += 2) {
                    int x = p.x() + sx * h;
                    int z = p.z() + sz * h;
                    kit.put(x, p.y() + 1, z, StormKit.CALCITE);
                    kit.put(x, p.y() + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }
}
