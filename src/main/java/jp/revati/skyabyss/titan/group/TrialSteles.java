package jp.revati.skyabyss.titan.group;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.quest.Quests;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackTrials;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import jp.revati.skyabyss.titan.storm.StormTravel;
import jp.revati.skyabyss.titan.storm.back.StormBackState;
import jp.revati.skyabyss.titan.storm.back.StormProgress;
import jp.revati.skyabyss.titan.tide.TideTravel;
import jp.revati.skyabyss.titan.tide.back.TideBackState;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

// 試練のあとの石 (共鳴石・潮鳴り石・風鳴り石・熾り石) は、パーティ (一人ならその人) のもの。
// 誰かが鳴らせば、そのグループの全員が「踏破した」ことになる (docs/design/10 の 10.9)。
//  - 石の状態 (鳴ったか) は、最初からグループの記録 (BackGroupData) にある
//  - 人ごとのクエストの「石の合図」(`<titan>_trial_<west|east|front|south>`、岩鯨は `whale_trial_<0|1|2>`) は、
//    その人のグループの記録から数える。どこにいても (別の世界・背中の遠く)、あとからログインしても、あとからパーティに入っても、
//    Quests.poll (1 秒ごと) が今の記録に合わせる。数は増えるだけで戻らない (グループが試練を戻しても、済んだ目的は戻らない)
//  - 経験値などの個人の報酬は変えない: 鳴らした瞬間に、石の近くの人へ満額・オンラインの仲間へ分け前を一度だけ。
//    あとから入った人・離れていた人が、記録から追加で受け取ることはない (記録を使った稼ぎはできない)
public final class TrialSteles {
    private TrialSteles() {
    }

    public static final String TIDE = "tide";
    public static final String STORM = "storm";
    public static final String EMBER = "ember";
    public static final String WHALE = "whale";

    public static String signal(String titan, String trial) {
        return titan + "_trial_" + trial;
    }

    // 石の合図の名前から、そのグループの石が鳴っているか。合図でなければ false
    public static boolean rung(MinecraftServer server, UUID group, String signal) {
        int cut = signal.indexOf("_trial_");
        if (cut < 0) {
            return false;
        }
        String titan = signal.substring(0, cut);
        String trial = signal.substring(cut + "_trial_".length());
        ResourceKey<Level> dim = switch (titan) {
            case TIDE -> TideTravel.TIDE_BACK;
            case STORM -> StormTravel.STORM_BACK;
            case EMBER -> EmberIds.BACK;
            case WHALE -> TitanTravel.TITAN_BACK;
            default -> null;
        };
        ServerLevel level = dim == null ? null : server.getLevel(dim);
        if (level == null || BackGroupData.get(level).peek(group) == null) {
            return false;
        }
        String up = trial.toUpperCase(Locale.ROOT);
        try {
            return switch (titan) {
                case TIDE -> TideBackState.of(level, group).rung(TideBackState.Trial.valueOf(up));
                case STORM -> StormProgress.of(level, group).rung(StormBackState.Trial.valueOf(up));
                case EMBER -> EmberBackState.of(level, group).rung(EmberBackState.Trial.valueOf(up));
                default -> BackGroupData.get(level).of(group).flag(TitanBackTrials.STONE_FLAG + Integer.parseInt(trial));
            };
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // クエストの「石の合図」の目的のうち、そのプレイヤーのグループで鳴っている石の数 (合図でない目的は 0)
    public static int cleared(ServerPlayer player, List<String> targets) {
        int n = 0;
        UUID group = null;
        for (String t : targets) {
            if (!t.contains("_trial_")) {
                continue;
            }
            if (group == null) {
                group = Groups.keyOf(player);
            }
            if (rung(player.server, group, t)) {
                n++;
            }
        }
        return n;
    }

    // 石が鳴った直後に呼ぶ。そのグループのオンラインの人のうち、石のある背中にいない人へ静かに一度だけ知らせ、
    // クエストを今の記録に合わせる (オフラインの人は次のログインで poll が合わせる)。背中の中の人は、石の鳴った知らせをすでに受けている
    public static void onRung(ServerLevel back, UUID group, String titan, String trial, @Nullable Component name) {
        for (ServerPlayer p : back.getServer().getPlayerList().getPlayers()) {
            if (!Groups.isMember(p, group)) {
                continue;
            }
            if (p.level() != back) {
                Notice.hint(p, "message.skyabyss.steles.party_rung",
                        name == null ? Component.translatable("trial.skyabyss." + titan + "_" + trial) : name);
            }
            Quests.poll(p, false);
        }
    }
}
