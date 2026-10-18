package jp.revati.skyabyss.story.ch1;

import javax.annotation.Nullable;

import jp.revati.skyabyss.guide.PrepQuests;
import jp.revati.skyabyss.quest.Repeats;
import jp.revati.skyabyss.story.Chapters;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

// 岩鯨の降下の条件 (docs/design/21 の 3.2): 鎖が降りるのは「日 4 の朝以降で、備えの頼みを 2 本終えたとき」か「日 6 以降」。
// それまで岩鯨は雲の下を巡り、遠くに見える (日 3 夕方の初見 B10 は変えない)。
//   日の数え方  Repeats.day (昼夜の時刻と経ったゲーム時間の大きいほう。寝て夜を飛ばしても進む)。0 始まり: 日 4 の朝 = 3、日 6 = 5
//   人ごと      その人が一章 (Chapters.of = WHALE) にいるときだけ条件を見る。一章を過ぎた人 (岩鯨の心臓を止めた人) は条件なし
//   グループ    グループ (パーティ、または一人) のオンラインの誰かが満たせば降りる (PrepQuests.groupAtLeast)。降りた鎖には条件を満たさない人も乗れる
//   備えの頼みが 3 本そろっていない章は条件なし (PrepQuests.defined)
public final class Ch1Descent {
    public static final int MORNING_DAY = 3;
    public static final int FORCED_DAY = 5;
    public static final int NEEDED_PREP = 2;

    // 自己テストが差し替える口 (本番は今の章と今の日)
    static java.util.function.Function<ServerPlayer, Chapters> chapterOf = Chapters::of;
    static java.util.function.ToLongFunction<net.minecraft.server.MinecraftServer> dayOf = Repeats::day;

    private Ch1Descent() {
    }

    // 純粋な式: 日 (0 始まり) と、終えた備えの頼みの本数から
    public static boolean allows(long day, int prepDone) {
        return day >= FORCED_DAY || (day >= MORNING_DAY && prepDone >= NEEDED_PREP);
    }

    // この人のために鎖を降ろしてよいか
    public static boolean qualifies(ServerPlayer player) {
        if (chapterOf.apply(player) != Chapters.WHALE || !PrepQuests.defined(1)) {
            return true;
        }
        long day = dayOf.applyAsLong(player.server);
        if (day >= FORCED_DAY) {
            return true;
        }
        return day >= MORNING_DAY && PrepQuests.groupAtLeast(player, 1, NEEDED_PREP);
    }

    // 岩鯨 (x, z) から range 以内で、鎖を降ろしてよい最も近い人 (いなければ null)
    @Nullable
    public static ServerPlayer pick(ServerLevel level, double x, double z, double range) {
        ServerPlayer best = null;
        double bd = range * range;
        for (ServerPlayer p : level.players()) {
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
