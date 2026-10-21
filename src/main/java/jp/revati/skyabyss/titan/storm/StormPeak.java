package jp.revati.skyabyss.titan.storm;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.opening.site.ChapterGrounds;
import jp.revati.skyabyss.opening.site.SiteData;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 雷翼が止まる峰 (docs/design/33 の 3.1)。夕立の刻の止まり木 (choosePerch) と風見鶏で呼ぶ止まり (WeathercockItem) は、三章の跡地 (都の西の峰) だけ。
// 跡地がまだ決まっていない世界 (序盤の場所を作らない設定など) では、今までどおり人の近くの峰 (12 の 2.3)。
// 呼び羽 (09 の 4.4。心臓のあと)・開発コマンド・空の影でうずくまる・眠りの峰は峰に限らない。
public final class StormPeak {
    // 峰にいるとみなす距離 (この中の人のために降りる)
    public static final int NEAR = 256;
    // 峰の頂を探す半径 (跡地の中心から。峰の形は一辺 128)
    public static final int SEARCH = 64;

    // 自己テスト用: 峰に限らない (合成の地形で止まり方を確かめる)
    public static volatile boolean testOff;

    private StormPeak() {
    }

    @Nullable
    static SiteData.Ground ground(MinecraftServer server) {
        return server == null ? null : ChapterGrounds.ground(server, Chapters.STORM);
    }

    // 峰に限るか (三章の跡地が決まっている)
    public static boolean restricted(MinecraftServer server) {
        return !testOff && ground(server) != null;
    }

    // 峰の中心 (跡地の中心)。なければ null
    @Nullable
    public static BlockPos peak(MinecraftServer server) {
        SiteData.Ground g = ground(server);
        return g == null ? null : g.center;
    }

    // 人が峰の近くにいるか (峰に限らない世界ではいつも true)
    public static boolean near(ServerPlayer p) {
        BlockPos b = restricted(p.server) ? peak(p.server) : null;
        return b == null || Math.hypot(p.getX() - b.getX(), p.getZ() - b.getZ()) <= NEAR;
    }

    // 峰の止まり木: 跡地の中心から SEARCH の中で、読み込まれた乾いた地表のいちばん高い所 (StormPerch.find)。なければ null
    @Nullable
    public static StormPerch.Peak perch(ServerLevel level) {
        BlockPos c = peak(level.getServer());
        return c == null ? null : StormPerch.find(level, c.getX() + 0.5, c.getZ() + 0.5, 0, SEARCH);
    }
}
