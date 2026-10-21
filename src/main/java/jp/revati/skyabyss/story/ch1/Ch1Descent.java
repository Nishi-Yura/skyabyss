package jp.revati.skyabyss.story.ch1;

import jp.revati.skyabyss.util.Players;

import javax.annotation.Nullable;

import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 岩鯨の降下の条件 (docs/design/21 の 3.2、17 の 2.8): 鎖が降りるのは「その人が岩鯨をもう見た (初見の伝承 chapter1/stone_whale が記された) とき」。
// 日数・時刻・天気・支度の段の本数では止めない (支度は勧めで鍵ではない。34 の 3 の 4。状態で入口を閉じない決まり)。
// 見つけてから背に乗るまでを短くする: 初見の場面が終われば、岩鯨はすぐその人のもとへ降りてくる。
//   人ごと      その人が一章 (Chapters.of = WHALE) にいるときだけ「見たか」を見る。一章を過ぎた人 (岩鯨の心臓を止めた人) は見たものとする
//   場面の間    場面 (シネマティック) を見ている人のところへは降りない。初見の場面の途中で岩鯨が動きだすと、画面から外れるため
//   マルチ      条件を満たす人のうち、いちばん近い人のもとへ降りる。降りた鎖には、まだ見ていない人も乗れる
public final class Ch1Descent {
    // 初見の伝承 (岩鯨に近づくと記される)
    public static final String SEEN = "chapter1/stone_whale";

    // 自己テストが差し替える口 (本番は今の章・伝承・場面)
    static java.util.function.Function<ServerPlayer, Chapters> chapterOf = Chapters::of;
    static java.util.function.Predicate<ServerPlayer> seen = p -> Lore.state(p).has(SEEN);
    static java.util.function.Predicate<ServerPlayer> inScene = jp.revati.skyabyss.cinematic.Cinematics::active;

    private Ch1Descent() {
    }

    // 純粋な式: 岩鯨を見たか (一章を過ぎた人は真)、場面の最中か
    public static boolean allows(boolean seenWhale, boolean watchingScene) {
        return seenWhale && !watchingScene;
    }

    // この人のために鎖を降ろしてよいか
    public static boolean qualifies(ServerPlayer player) {
        boolean seenWhale = chapterOf.apply(player) != Chapters.WHALE || seen.test(player);
        return allows(seenWhale, inScene.test(player));
    }

    // 岩鯨 (x, z) から range 以内で、鎖を降ろしてよい最も近い人 (いなければ null)
    @Nullable
    public static ServerPlayer pick(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p.isSpectator()) {
                continue;
            }
            double d = (p.getX() - x) * (p.getX() - x) + (p.getZ() - z) * (p.getZ() - z);
            if (d < bd && qualifies(p)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
