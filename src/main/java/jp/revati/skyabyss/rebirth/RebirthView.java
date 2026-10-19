package jp.revati.skyabyss.rebirth;

import net.minecraft.world.entity.player.Player;

// 転生の回数を読む入口 (見せ方の側はここだけを見る)。サーバーでもクライアントでも同じ呼び方。
// 今の元は「同期する Data Attachment (RebirthSetup.DATA) の count」(転生の本体ができるまでの仮。開発用コマンド /skyabyss dev rebirth <n> で書く)。
// 転生の本体 (別の担当) が API を出したら、起動時に RebirthView.useSource(player -> <本体の回数>) を一行呼ぶだけで切り替わる
// (クライアントでも読めること: ほかのプレイヤーの回数も見えるので、本体の値を同期してあること)
public final class RebirthView {
    public interface Source {
        int count(Player player);
    }

    private static final Source STUB = p -> p.getData(RebirthSetup.DATA.get()).count();
    private static volatile Source source = STUB;

    private RebirthView() {
    }

    public static int count(Player player) {
        return RebirthTier.clamp(source.count(player));
    }

    public static RebirthData data(Player player) {
        return player.getData(RebirthSetup.DATA.get());
    }

    public static boolean isStub() {
        return source == STUB;
    }

    public static void useSource(Source s) {
        source = s == null ? STUB : s;
    }
}
