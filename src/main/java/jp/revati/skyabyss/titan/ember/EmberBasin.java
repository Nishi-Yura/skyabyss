package jp.revati.skyabyss.titan.ember;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.site.ChapterGrounds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 余燼が浮上する灰の地。集まりの刻の浮上 (tryGather) と温石の頼み (request) は、四章の跡地 (都の南の灰の地) だけ。
// 跡地がまだ決まっていない世界では、今までどおり人の近くの陸 (13 の 2.3)。呼び鈴 (09 の 4.4。心臓のあと) と開発コマンドは灰の地に限らない。
public final class EmberBasin {
    // 灰の地にいるとみなす距離 (この中の人のために浮かぶ)
    public static final int NEAR = 256;

    // 自己テスト用: 灰の地に限らない
    public static volatile boolean testOff;

    private EmberBasin() {
    }

    @Nullable
    static SiteData.Ground ground(MinecraftServer server) {
        return server == null ? null : ChapterGrounds.ground(server, Chapters.EMBER);
    }

    // 灰の地に限るか (四章の跡地が決まっている)
    public static boolean restricted(MinecraftServer server) {
        return !testOff && ground(server) != null;
    }

    // 灰の地の中心 (跡地の中心)。なければ null
    @Nullable
    public static BlockPos basin(MinecraftServer server) {
        SiteData.Ground g = ground(server);
        return g == null ? null : g.center;
    }

    // 人が灰の地の近くにいるか (限らない世界ではいつも true)
    public static boolean near(ServerPlayer p) {
        BlockPos b = restricted(p.server) ? basin(p.server) : null;
        return b == null || Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ()) <= NEAR;
    }

    // 灰の地の眠る場所: 中心のまわり (24〜160) の平らな陸に伏せ、口を中心へ向ける (EmberGround.findCallSpot。読み込まれた所だけ)。なければ null
    @Nullable
    public static EmberGround.Spot sleepSpot(ServerLevel level) {
        BlockPos c = basin(level.getServer());
        return c == null ? null : EmberGround.findCallSpot(level, c.getX() + 0.5, c.getZ() + 0.5);
    }
}
