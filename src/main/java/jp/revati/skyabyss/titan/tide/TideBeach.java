package jp.revati.skyabyss.titan.tide;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.site.ChapterGrounds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

// 潮殻が寄る浜。干潮の刻 (tryGather) と潮見の貝 (request) で寄るのは、二章の跡地 (東の海辺) の浜だけ。
// 跡地がまだ決まっていない世界 (序盤の場所を作らない設定など) では、今までどおり人の近くの海 (08 の 2.3)。
// 呼び貝 (09 の 4.4。心臓のあと) と開発コマンドは浜に限らない。
public final class TideBeach {
    // 浜にいるとみなす距離 (この中の人のために寄る)
    public static final int NEAR = 256;

    private TideBeach() {
    }

    @Nullable
    static SiteData.Ground ground(MinecraftServer server) {
        return server == null ? null : ChapterGrounds.ground(server, Chapters.TIDE);
    }

    // 自己テスト用: 浜に限らない (合成の海で寄り方を確かめる)
    public static volatile boolean testOff;

    // 浜に限るか (二章の跡地が決まっている)
    public static boolean restricted(MinecraftServer server) {
        return !testOff && ground(server) != null;
    }

    // 浜の中心 (跡地の中心)。なければ null
    @Nullable
    public static BlockPos beach(MinecraftServer server) {
        SiteData.Ground g = ground(server);
        return g == null ? null : g.center;
    }

    // 人が浜の近くにいるか (浜に限らない世界ではいつも true)
    public static boolean near(ServerPlayer p) {
        BlockPos b = beach(p.server);
        return b == null || Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ()) <= NEAR;
    }

    // 浜の休む場所 (x, 向き, z)。掘った入り江なら決まった場所と向き。自然の海辺なら、浜の中心のまわりの開けた海 (TideOcean.findRestSpot。読み込まれた所だけ)。
    // なければ null
    @Nullable
    public static Vec3 restSpot(ServerLevel level) {
        SiteData.Ground g = ground(level.getServer());
        if (g == null) {
            return null;
        }
        if (g.fixedRest && g.sea != null) {
            return new Vec3(g.sea.getX() + 0.5, g.restYaw, g.sea.getZ() + 0.5);
        }
        return TideOcean.findRestSpot(level, g.center.getX() + 0.5, g.center.getZ() + 0.5);
    }
}
