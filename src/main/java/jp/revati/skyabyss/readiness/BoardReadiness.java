package jp.revati.skyabyss.readiness;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Progression;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 乗り込みの一言と二度押しの確認 (docs/design/21 の 1.2 の 2、docs/design/11 の 9 章)。ふさがない確認で、赤の灯が一つでもあるときだけ一度止める。
//   乗り込み口 (鎖・潮門・昇り風・口) に触れると、乗る資格のある人の画面に三灯の一行が出る
//   赤が一つもなければそのまま乗る
//   赤があれば、一度は乗らずに「このまま乗る？」を出し、5 秒 (100 tick) 以内の二度目で乗る
//     鎖 (右クリックで乗る) は、もう一度右クリック。潮門・昇り風・口 (入ると乗る) は、一度出てから入り直す (入ったままの連続は二度目に数えない)
// 戻り値が true のときだけ、呼んだ側が乗せる。管理者 (クリエイティブ・観戦) と自己テスト用の偽プレイヤーは、いつも true
public final class BoardReadiness {
    // 二度目までの猶予 (tick)
    public static final int WINDOW = 100;
    // 右クリックの二重発火を数えない間 (tick)
    public static final int CLICK_DEBOUNCE = 4;
    // 入り直しとみなす、途切れの長さ (tick)。入口の判定は 2〜4 tick ごとに来る
    public static final int REENTER_GAP = 12;
    // 緑のときの一行を出す間隔 (tick)
    public static final int NOTE_EVERY = 100;

    public enum Mode {
        RIGHT_CLICK, WALK_IN
    }

    public enum Outcome {
        // 赤がない: そのまま乗る
        PROCEED,
        // 二度目: 乗る
        CONFIRMED,
        // 一度目: 乗らずに確認を出す
        PROMPT,
        // 確認の待ちの間 (入ったまま・二重の右クリック): 乗らず、何も出さない
        WAITING
    }

    private record Pending(String titan, long first, long last) {
    }

    // 確認の状態 (人ごと)。式と同じく、ゲームの状態に触れずに試せるよう、時刻は呼ぶ側が渡す
    public static final class Flow {
        private final Map<UUID, Pending> pending = new HashMap<>();

        public Outcome step(UUID who, String titan, long now, boolean red, Mode mode) {
            if (!red) {
                pending.remove(who);
                return Outcome.PROCEED;
            }
            Pending p = pending.get(who);
            if (p != null && p.titan().equals(titan) && now - p.first() <= WINDOW) {
                boolean again = mode == Mode.RIGHT_CLICK ? now - p.last() >= CLICK_DEBOUNCE : now - p.last() > REENTER_GAP;
                if (again) {
                    pending.remove(who);
                    return Outcome.CONFIRMED;
                }
                if (mode == Mode.WALK_IN) {
                    // 入ったままの間は、最後に触れた時刻だけ進める (出て入り直したときの途切れを測るため)
                    pending.put(who, new Pending(titan, p.first(), now));
                }
                return Outcome.WAITING;
            }
            pending.put(who, new Pending(titan, now, now));
            return Outcome.PROMPT;
        }

        public boolean isPending(UUID who) {
            return pending.containsKey(who);
        }

        public void clear() {
            pending.clear();
        }
    }

    private static final Flow FLOW = new Flow();
    // 緑・黄のときの一行を最後に出した時刻
    private static final Map<UUID, Long> NOTED = new HashMap<>();

    private BoardReadiness() {
    }

    // 入口から呼ぶ。true なら乗せる。false なら乗せない (確認を出した、または確認を待っている)
    public static boolean attempt(ServerPlayer player, String titan, Mode mode) {
        if (player instanceof FakePlayer || Progression.exempt(player)) {
            return true;
        }
        long now = player.server.overworld().getGameTime();
        Readiness.Result result = Readiness.of(player, titan);
        Outcome outcome = FLOW.step(player.getUUID(), titan, now, result.anyRed(), mode);
        switch (outcome) {
            case PROCEED -> {
                Long last = NOTED.get(player.getUUID());
                if (last == null || now - last >= NOTE_EVERY) {
                    NOTED.put(player.getUUID(), now);
                    Notice.hint(player, "message.skyabyss.readiness.note", lamps(result));
                }
                return true;
            }
            case CONFIRMED -> {
                return true;
            }
            case PROMPT -> {
                showPrompt(player, result, mode);
                return false;
            }
            default -> {
                return false;
            }
        }
    }

    // 一度目の確認: 三灯の一行、一番低い灯の一言、アクションバーに「このまま乗る？」(開発の撮影用の /skyabyss dev readiness prompt からも使う)
    public static void showPrompt(ServerPlayer player, Readiness.Result result, Mode mode) {
        // 字幕は一つずつ順番に出るので、一言は三灯の一行だけにする。足りない灯の言い回しは控え帳と前室の碑にある
        Notice.hint(player, "message.skyabyss.readiness.note", lamps(result));
        // 赤の灯の「理由 + 手段」(A7)。一番低い灯を一つだけ
        Readiness.Lamp worst = ReadinessSummary.worstRed(result);
        if (worst != null) {
            Notice.send(player, Notice.Kind.HINT, ReadinessAdvice.line(player, result.titan(), result, worst));
        }
        Notice.bar(player, ModText.hint(mode == Mode.RIGHT_CLICK ? "message.skyabyss.readiness.confirm_click"
                : "message.skyabyss.readiness.confirm_walk"));
    }

    // ---------------------------------------------------------------- 灯の描き方
    public static final int RED = 0xD9534F;
    public static final int YELLOW = 0xE6B422;
    public static final int GREEN = 0x4CAF72;

    // 色だけに頼らないよう、形も変える: 緑 ●、黄 ▲、赤 ■
    public static String shape(Readiness.Colour c) {
        return switch (c) {
            case GREEN -> "●";
            case YELLOW -> "▲";
            case RED -> "■";
        };
    }

    public static int rgb(Readiness.Colour c) {
        return switch (c) {
            case GREEN -> GREEN;
            case YELLOW -> YELLOW;
            case RED -> RED;
        };
    }

    // 灯一つ:「身●」
    public static MutableComponent lamp(Readiness.Lamp lamp, Readiness.Colour c) {
        return Component.translatable("message.skyabyss.readiness.lamp." + lamp.id()).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(shape(c)).withColor(rgb(c)));
    }

    // 三灯を並べた一行:「身● 刃▲ 糧■」
    public static MutableComponent lamps(Readiness.Result r) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Readiness.Lamp l : Readiness.Lamp.values()) {
            if (!first) {
                out.append(Component.literal(" "));
            }
            first = false;
            out.append(lamp(l, r.colour(l)));
        }
        return out;
    }

    // 形だけを並べた短い値:「▲ ● ■」(控え帳の一覧の列は狭いので、灯の名前は右の詳細に任せる)
    public static MutableComponent shapes(Readiness.Result r) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Readiness.Lamp l : Readiness.Lamp.values()) {
            if (!first) {
                out.append(Component.literal(" "));
            }
            first = false;
            Readiness.Colour c = r.colour(l);
            out.append(Component.literal(shape(c)).withColor(rgb(c)));
        }
        return out;
    }

    public static void forget(UUID who) {
        NOTED.remove(who);
    }
}
