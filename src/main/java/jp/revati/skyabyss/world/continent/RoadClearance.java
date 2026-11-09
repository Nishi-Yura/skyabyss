package jp.revati.skyabyss.world.continent;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

// 置き場所を大陸の道から離す (2026-11-02 の声「道の上に構造物できないようにして」)。
// 建てるとき道にかかるブロックは置かない (SiteJob の keepRoadsClear) ので、道に重なった跡地は道の所だけ欠けた形で建ち、
// 樽や箱 (仕掛けの付いた物) は道の上に残っていた。章の跡地の置き場所探し (opening/site/ChapterSites・ChapterGrounds の道しるべ) と
// 大陸の見どころ (ContinentPois) は、ここで道の縁から離れた所を選ぶ。
// 地上が大陸の世界のときだけ (地上を読み込んだときに決まる。ほかの世界には道がない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RoadClearance {
    private RoadClearance() {
    }

    private static volatile boolean live;

    // 道を避ける世界か
    public static boolean live() {
        return live;
    }

    @SubscribeEvent
    static void onLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD) {
            live = level.getChunkSource().getGenerator() instanceof ContinentChunkGenerator;
        }
    }

    // 点から一番近い道の縁までの距離 (道の上なら負。道がなければ大きな値)
    public static double clearance(double x, double z) {
        double best = 1e9;
        for (List<BlockPos> line : ContinentLayout.get().roads()) {
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double len2 = dx * dx + dz * dz;
                double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x - a.getX()) * dx + (z - a.getZ()) * dz) / len2));
                best = Math.min(best, Math.hypot(x - (a.getX() + u * dx), z - (a.getZ() + u * dz)));
            }
        }
        return best - ContinentChunkGenerator.ROAD_EDGE;
    }

    // 道の線を、base から reach 以内の点 (STEP おき) に刻む。置き場所探しの障害物 (半径 = 道の縁) に使う
    static final int STEP = 6;

    public static List<BlockPos> points(BlockPos base, int reach) {
        List<BlockPos> out = new ArrayList<>();
        if (!live) {
            return out;
        }
        double r2 = (double) reach * reach;
        for (List<BlockPos> line : ContinentLayout.get().roads()) {
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double len = Math.hypot(b.getX() - a.getX(), b.getZ() - a.getZ());
                int n = Math.max(1, (int) Math.ceil(len / STEP));
                for (int k = 0; k <= n; k++) {
                    double t = k / (double) n;
                    int x = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
                    int z = (int) Math.round(a.getZ() + (b.getZ() - a.getZ()) * t);
                    double dx = x - base.getX();
                    double dz = z - base.getZ();
                    if (dx * dx + dz * dz <= r2) {
                        out.add(new BlockPos(x, 0, z));
                    }
                }
            }
        }
        return out;
    }

    // 半径 radius の物を (x, z) に置くと道にかかるか (縁から margin 空ける)
    public static boolean blocks(int x, int z, int radius, int margin) {
        return live && clearance(x, z) < radius + margin;
    }

    // 道にかかる物を、一番近い道から遠ざかる向きにずらす (最大 maxShift)。かからなければ、そのまま
    public static BlockPos pushAway(BlockPos at, int radius, int margin, int maxShift) {
        if (!blocks(at.getX(), at.getZ(), radius, margin)) {
            return at;
        }
        // 一番近い道の点
        double bx = at.getX();
        double bz = at.getZ();
        double best = Double.MAX_VALUE;
        for (List<BlockPos> line : ContinentLayout.get().roads()) {
            for (int i = 0; i + 1 < line.size(); i++) {
                BlockPos a = line.get(i);
                BlockPos b = line.get(i + 1);
                double dx = b.getX() - a.getX();
                double dz = b.getZ() - a.getZ();
                double len2 = dx * dx + dz * dz;
                double u = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((at.getX() - a.getX()) * dx + (at.getZ() - a.getZ()) * dz) / len2));
                double px = a.getX() + u * dx;
                double pz = a.getZ() + u * dz;
                double d = Math.hypot(at.getX() - px, at.getZ() - pz);
                if (d < best) {
                    best = d;
                    bx = px;
                    bz = pz;
                }
            }
        }
        double ux = at.getX() - bx;
        double uz = at.getZ() - bz;
        double len = Math.hypot(ux, uz);
        if (len < 1e-3) {
            ux = 1;
            uz = 0;
        } else {
            ux /= len;
            uz /= len;
        }
        for (int s = 2; s <= maxShift; s += 2) {
            int x = (int) Math.round(at.getX() + ux * s);
            int z = (int) Math.round(at.getZ() + uz * s);
            if (!blocks(x, z, radius, margin)) {
                return new BlockPos(x, at.getY(), z);
            }
        }
        // 遠ざかる向きで空きが無い (二本の道のあいだなど): 八方を近い順に探す
        for (int s = 4; s <= maxShift; s += 4) {
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                int x = (int) Math.round(at.getX() + Math.cos(a) * s);
                int z = (int) Math.round(at.getZ() + Math.sin(a) * s);
                if (!blocks(x, z, radius, margin)) {
                    return new BlockPos(x, at.getY(), z);
                }
            }
        }
        return at;
    }
}
