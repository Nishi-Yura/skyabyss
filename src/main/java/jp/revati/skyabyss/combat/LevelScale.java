package jp.revati.skyabyss.combat;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.GearCurve;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

// 入る人のレベルに合わせた主の硬さ (2026-11-08 の声「再挑戦できる系のボスは、入る人のレベルに合わせて最適な hp になる様にして。
// 周回が楽になりすぎないように。岩鯨やってみたけど、めっちゃ簡単だった」)。
//   主ごとに想定のレベル (巨神の章の推奨レベル: GearCurve.STAGES) がある。戦いの始めに、挑む人の平均のレベルで見込める 1 秒あたりの傷
//   (expectedDps: 装備の段の dps0 × 筋力 × 技の木の上乗せ) を、想定のレベルの見込みで割った倍率を主の永続データ KEY に書き、
//   人から受ける傷をその倍率で割る。上のレベルで来ても、戦いの長さは推奨のレベルの人とほぼ同じになる。下のレベルでは楽にしない (1 のまま)。
//   人数 (PartyScale / 心臓の challengers) や型ごとの硬さとは別に掛かる。
// 参考: Wynncraft のボス・レイドは体力を変えず、入れるレベルの下限と、レベルで変わる報酬で回している (上のレベルで行くと楽になる)。
// ここでは FF14 のレベルシンクのように、主の側を上げて手ごたえを残す
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class LevelScale {
    public static final String KEY = "skyabyss_level_scale";
    // 装備の段の見込み (GearCurve.STAGES) の先は、6 レベルごとに PER_6 倍 (深淵・月・終章の装備の伸びの目安)
    static final double PER_6 = 1.15;
    // 倍率の上限 (Lv 50 で推奨 Lv 12 の主へ入っても、ここまで)
    public static final double MAX = 4.0;

    private LevelScale() {
    }

    // レベル level の人が見込める 1 秒あたりの傷 (相手の防具の前)
    public static double expectedDps(int level) {
        List<GearCurve.Stage> st = GearCurve.STAGES;
        double base;
        GearCurve.Stage first = st.get(0);
        GearCurve.Stage last = st.get(st.size() - 1);
        if (level <= first.level()) {
            base = first.dps0() / Math.pow(PER_6, (first.level() - level) / 6.0);
        } else if (level >= last.level()) {
            base = last.dps0() * Math.pow(PER_6, (level - last.level()) / 6.0);
        } else {
            base = first.dps0();
            for (int i = 1; i < st.size(); i++) {
                GearCurve.Stage a = st.get(i - 1);
                GearCurve.Stage b = st.get(i);
                if (level <= b.level()) {
                    double t = (level - a.level()) / (double) (b.level() - a.level());
                    base = a.dps0() + (b.dps0() - a.dps0()) * t;
                    break;
                }
            }
        }
        return base * GearCurve.strengthMultiplier(level) * (1 + HeartBalance.treeBonus(level));
    }

    // 平均のレベル avg の組が、想定のレベル designLevel の主に向かうときの倍率 (1 から MAX)
    public static double factor(int designLevel, double avg) {
        double k = expectedDps((int) Math.round(avg)) / expectedDps(designLevel);
        return Math.max(1.0, Math.min(MAX, k));
    }

    // 巨神の章の推奨レベル (stone_whale / tide_shell / thunder_wing / ember)。知らなければ -1
    public static int stageLevel(String titan) {
        for (GearCurve.Stage s : GearCurve.STAGES) {
            if (s.id().equals(titan)) {
                return s.level();
            }
        }
        return -1;
    }

    // 戦いの始めに書く (挑む人の平均のレベル)。倍率を返す
    public static double apply(LivingEntity boss, int designLevel, List<ServerPlayer> players) {
        if (designLevel <= 0 || players.isEmpty()) {
            boss.getPersistentData().remove(KEY);
            return 1;
        }
        double sum = 0;
        for (ServerPlayer p : players) {
            sum += jp.revati.skyabyss.rpg.Growth.get(p).level;
        }
        double k = factor(designLevel, sum / players.size());
        if (k > 1.001) {
            boss.getPersistentData().putDouble(KEY, k);
        } else {
            boss.getPersistentData().remove(KEY);
        }
        return k;
    }

    public static double of(LivingEntity e) {
        double k = e.getPersistentData().getDouble(KEY);
        return k > 1 ? k : 1;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide() || target instanceof Player || !(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        double k = of(target);
        if (k > 1) {
            event.setAmount((float) (event.getAmount() / k));
        }
    }
}
