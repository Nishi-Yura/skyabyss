package jp.revati.skyabyss.titan.group;

import jp.revati.skyabyss.util.Players;

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
        onRung(back, group, titan, trial, name, null);
    }

    // by: 鳴らした人 (わかれば。「誰が鳴らしたか」の表示のために控える。踏破の数え方には使わない)
    public static void onRung(ServerLevel back, UUID group, String titan, String trial, @Nullable Component name, @Nullable ServerPlayer by) {
        // 試練を越えた: 順路の今の段なら、4 秒後に次の段の入口へ運ぶ (docs/design/32 の 2.3)
        jp.revati.skyabyss.trial.TrialRoute.onCleared(back, group, titan, trial, by);
        if (by != null) {
            jp.revati.skyabyss.tutorial.TutorialHints.start(by, jp.revati.skyabyss.tutorial.TutorialHints.RECORD);
            SteleRingers.get(back.getServer()).put(group, signal(titan, trial), by.getUUID(), by.getGameProfile().getName());
        }
        for (ServerPlayer p : Players.snapshot(back.getServer().getPlayerList().getPlayers())) {
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

    // ---------------------------------------------------------------- 誰が鳴らしたか (表示だけ。rpg_brushup A4)
    public enum Who {
        // 見ている人が鳴らした
        SELF,
        // 仲間が鳴らした (見ている人は踏破として数える)
        OTHER,
        // まだ鳴っていない
        NONE
    }

    public record WhoRang(Who who, String name) {
    }

    // 石の合図 signal の石を、viewer (そのグループの人) の目から見て誰が鳴らしたか。鳴っていなければ NONE。
    // 鳴っているが誰か控えにない (この機能より前に鳴った石・コマンドで鳴らした) ときは OTHER で名前が空
    public static WhoRang who(MinecraftServer server, UUID group, UUID viewer, String signal) {
        if (!rung(server, group, signal)) {
            return new WhoRang(Who.NONE, "");
        }
        SteleRingers.Ringer r = SteleRingers.get(server).get(group, signal);
        if (r == null) {
            return new WhoRang(Who.OTHER, "");
        }
        return new WhoRang(r.id().equals(viewer) ? Who.SELF : Who.OTHER, r.name());
    }

    // 石の触ったときの知らせの文 (そのグループの一人ひとりの目から見た状態)
    public static Component whoText(WhoRang w) {
        return switch (w.who()) {
            case SELF -> Component.translatable("message.skyabyss.steles.who_self");
            case OTHER -> w.name().isEmpty() ? Component.translatable("message.skyabyss.steles.who_other_unknown")
                    : Component.translatable("message.skyabyss.steles.who_other", w.name());
            case NONE -> Component.translatable("message.skyabyss.steles.who_none");
        };
    }

    // 控え帳の頁用: 巨神 (進行の名前: stone_whale / tide_shell / thunder_wing / ember) の石の合図の一覧。石のない巨神は空
    public static List<String> signalsOf(String progressTitan) {
        return switch (progressTitan) {
            case "stone_whale" -> List.of(signal(WHALE, "0"), signal(WHALE, "1"), signal(WHALE, "2"));
            case "tide_shell" -> List.of(signal(TIDE, "west"), signal(TIDE, "east"), signal(TIDE, "front"));
            case "thunder_wing" -> List.of(signal(STORM, "west"), signal(STORM, "east"), signal(STORM, "front"));
            case "ember" -> List.of(signal(EMBER, "west"), signal(EMBER, "east"), signal(EMBER, "south"));
            default -> List.of();
        };
    }

    // [自分が鳴らした数, 仲間が鳴らした数, まだの数]
    public static int[] counts(MinecraftServer server, UUID group, UUID viewer, String progressTitan) {
        int[] n = new int[3];
        for (String signal : signalsOf(progressTitan)) {
            n[switch (who(server, group, viewer, signal).who()) {
                case SELF -> 0;
                case OTHER -> 1;
                case NONE -> 2;
            }]++;
        }
        return n;
    }
}
