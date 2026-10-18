package jp.revati.skyabyss.readiness;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 扉が閉まる 5 秒の予告のあいだの準備確認 (A2、docs/design/11 の 9.6)。押させない・ふさがない。
//   アクションバーに自分の三灯と扉までの秒:「扉が閉まるまで 3 秒　備え 身● 刃■ 糧▲」
//   パーティのだれかが赤なら、赤でない人には「仲間の誰かの備えが低い」だけ (名指ししない・数も出さない)
//   赤の本人には、はじめの一度だけ、一番低い赤の灯の「理由 + 手段」の一行 (ReadinessAdvice) を字幕で
// 呼ぶ側: GroupBossRoom の集合 (心臓・中ボス)、TitanBackTrials の広間の集合 (番兵長)
public final class ReadinessSummary {
    private ReadinessSummary() {
    }

    // 赤の灯が一つでもある人がいるか
    public static boolean anyoneRed(List<Readiness.Result> results) {
        for (Readiness.Result r : results) {
            if (r.anyRed()) {
                return true;
            }
        }
        return false;
    }

    // アクションバーの一行。自分が赤でなく、ほかのだれかが赤のときだけ「仲間の誰かの備えが低い」を足す (名指しも人数もなし)
    public static MutableComponent barLine(Readiness.Result mine, boolean othersRed, int secondsLeft) {
        MutableComponent out = Component.translatable("message.skyabyss.readiness.summary", secondsLeft, BoardReadiness.lamps(mine));
        if (othersRed && !mine.anyRed()) {
            out.append(Component.translatable("message.skyabyss.readiness.summary.others"));
        }
        return out;
    }

    // 一番低い赤の灯 (赤がなければ null)
    @Nullable
    public static Readiness.Lamp worstRed(Readiness.Result r) {
        Readiness.Lamp worst = null;
        for (Readiness.Lamp l : Readiness.Lamp.values()) {
            if (r.colour(l) == Readiness.Colour.RED && (worst == null || r.value(l) < r.value(worst))) {
                worst = l;
            }
        }
        return worst;
    }

    // 扉の予告の 1 秒ごと。party はこの扉に挑むグループの近くの人。first は予告の最初の呼び出し (字幕を出す)
    public static void show(ServerLevel level, List<ServerPlayer> party, int secondsLeft, boolean first) {
        String titan = Titans.titanOfBack(level.dimension());
        if (titan == null || party.isEmpty()) {
            return;
        }
        List<ServerPlayer> people = new ArrayList<>();
        List<Readiness.Result> results = new ArrayList<>();
        for (ServerPlayer p : party) {
            if (p instanceof FakePlayer || Progression.exempt(p)) {
                continue;
            }
            people.add(p);
            results.add(Readiness.of(p, titan));
        }
        for (int i = 0; i < people.size(); i++) {
            ServerPlayer p = people.get(i);
            Readiness.Result mine = results.get(i);
            List<Readiness.Result> others = new ArrayList<>(results);
            others.remove(i);
            Notice.bar(p, barLine(mine, anyoneRed(others), secondsLeft));
            if (first) {
                Readiness.Lamp worst = worstRed(mine);
                if (worst != null) {
                    Notice.send(p, Notice.Kind.HINT, ReadinessAdvice.line(p, titan, mine, worst));
                }
            }
        }
    }
}
