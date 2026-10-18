package jp.revati.skyabyss.readiness;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;

// 灯が赤・黄のときの「理由 + 手段」の一行 (A7、docs/design/11 の 9.5)。例:「刃: 今の武器は目安の 62%。砥石で鍛えるか、強い武器を作ろう」
// 理由は割合の数字つき。手段は Readiness.registerRemedy の登録 (鼓動守など) があればその名前、なければ灯ごとの一般の言い回し。
// 控え帳の詳細・乗り込みの一言・扉の予告 (ReadinessSummary) が同じ一行を使う
public final class ReadinessAdvice {
    private static final String P = "message.skyabyss.readiness.";

    private ReadinessAdvice() {
    }

    // 理由の言い回しの鍵 (割合が 0 = 何も持っていない、身は防具と体力の低い方)
    public static String reasonKey(Readiness.Lamp lamp, double value, double armorRatio, double healthRatio) {
        return switch (lamp) {
            case BODY -> armorRatio <= healthRatio ? P + "reason.body.armor" : P + "reason.body.health";
            case BLADE -> value <= 0 ? P + "reason.blade.none" : P + "reason.blade";
            case PROVISIONS -> value <= 0 ? P + "reason.provisions.none" : P + "reason.provisions";
        };
    }

    // 理由に出す割合 (%)。身は低い方の内訳
    public static int percent(Readiness.Lamp lamp, double value, double armorRatio, double healthRatio) {
        double v = lamp == Readiness.Lamp.BODY ? Math.min(armorRatio, healthRatio) : value;
        return (int) Math.round(Math.min(Readiness.CAP, Math.max(0, v)) * 100);
    }

    // 手段の言い回しの鍵 (登録された品があれば「〇〇を…」、なければ一般の言い回し)
    // 登録された品の名前があれば .with、刃で砥石が登録されていれば .whetstone、どちらもなければ今ある物だけの一般の言い回し (.generic)
    public static String remedyKey(Readiness.Lamp lamp, boolean bodyHealth, boolean registered, boolean whetstone) {
        String base = P + "remedy." + lamp.id() + (lamp == Readiness.Lamp.BODY && bodyHealth ? ".health" : "");
        if (registered) {
            return base + ".with";
        }
        return base + (lamp == Readiness.Lamp.BLADE && whetstone ? ".whetstone" : ".generic");
    }

    // 「刃: 今の武器は目安の 62%。…」。値と内訳を渡す (ゲームの状態に触れない)
    public static MutableComponent line(Readiness.Lamp lamp, double value, double armorRatio, double healthRatio, List<Component> remedies, boolean whetstone) {
        boolean bodyHealth = lamp == Readiness.Lamp.BODY && armorRatio > healthRatio;
        MutableComponent reason = Component.translatable(reasonKey(lamp, value, armorRatio, healthRatio), percent(lamp, value, armorRatio, healthRatio));
        boolean registered = !remedies.isEmpty() && !bodyHealth;
        MutableComponent remedy;
        if (registered) {
            MutableComponent names = Component.empty();
            for (int i = 0; i < remedies.size(); i++) {
                if (i > 0) {
                    names.append(Component.translatable(P + "remedy.sep"));
                }
                names.append(remedies.get(i));
            }
            remedy = Component.translatable(remedyKey(lamp, false, true, whetstone), names);
        } else {
            remedy = Component.translatable(remedyKey(lamp, bodyHealth, false, whetstone));
        }
        return Component.translatable(P + "line", Component.translatable(P + "name." + lamp.id()), reason, remedy);
    }

    // プレイヤーの今の持ち物から
    public static MutableComponent line(Player player, String titan, Readiness.Result r, Readiness.Lamp lamp) {
        double[] parts = lamp == Readiness.Lamp.BODY ? Readiness.bodyParts(player, titan) : new double[] {Readiness.CAP, Readiness.CAP};
        return line(lamp, r.value(lamp), parts[0], parts[1], Readiness.remedies(lamp), Readiness.hasWhetstone());
    }
}
