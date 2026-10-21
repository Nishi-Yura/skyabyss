package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

// ギルドの功績 (docs/design/35 の 6.5)。数はギルドの記録 (GuildData.Guild) が持ち、届くと一度だけ開く (unlocked に残る。数が減っても閉じない)。
//   requests_<n> : 週替わりの頼みを n 回済ませた
//   contrib_<n>  : 頼みに納めた品と倒した敵の合計 (ギルドの人みなの分。週が替わっても数え続ける)
//   members_<n>  : 人が一度に n 人そろった
// ほうびは旗の模様 (GuildEmblems.ALL の unlock) と名札の特別な色 (GuildEmblems.PLATES の unlock)
public final class GuildAchievements {
    private GuildAchievements() {
    }

    public enum Kind { REQUESTS, CONTRIB, MEMBERS }

    public record Achievement(String id, Kind kind, int target) {
        public String nameKey() {
            return "gui.skyabyss.guild.ach." + id;
        }
    }

    public static final List<Achievement> ALL = List.of(
            new Achievement("requests_1", Kind.REQUESTS, 1),
            new Achievement("requests_5", Kind.REQUESTS, 5),
            new Achievement("requests_10", Kind.REQUESTS, 10),
            new Achievement("requests_15", Kind.REQUESTS, 15),
            new Achievement("requests_30", Kind.REQUESTS, 30),
            new Achievement("contrib_500", Kind.CONTRIB, 500),
            new Achievement("contrib_1500", Kind.CONTRIB, 1500),
            new Achievement("contrib_4000", Kind.CONTRIB, 4000),
            new Achievement("members_10", Kind.MEMBERS, 10),
            new Achievement("members_25", Kind.MEMBERS, 25));

    public static int value(GuildData.Guild g, Kind k) {
        return switch (k) {
            case REQUESTS -> g.reqCompleted;
            case CONTRIB -> g.totalContrib;
            case MEMBERS -> Math.max(g.peakMembers, g.members.size());
        };
    }

    // ほうびの名前 (模様と名札の色。画面と知らせの文)
    public static List<Component> rewards(String id) {
        List<Component> out = new ArrayList<>();
        for (GuildEmblems.Emblem m : GuildEmblems.ALL) {
            if (id.equals(m.unlock())) {
                out.add(Component.translatable("gui.skyabyss.guild.reward_pattern", Component.translatable(m.nameKey())));
            }
        }
        for (GuildEmblems.Plate p : GuildEmblems.PLATES) {
            if (id.equals(p.unlock())) {
                out.add(Component.translatable("gui.skyabyss.guild.reward_plate", Component.translatable(p.nameKey())));
            }
        }
        return out;
    }

    // 届いた功績を開き、いる人に知らせる。新しく開いた数を返す
    public static int check(MinecraftServer server, GuildData.Guild g) {
        g.peakMembers = Math.max(g.peakMembers, g.members.size());
        int n = 0;
        for (Achievement a : ALL) {
            if (g.unlocked.contains(a.id()) || value(g, a.kind()) < a.target()) {
                continue;
            }
            g.unlocked.add(a.id());
            n++;
            SkyAbyss.LOGGER.info("[guild] {} achieved {}", g.name, a.id());
            Component reward = Component.empty();
            List<Component> rs = rewards(a.id());
            for (int i = 0; i < rs.size(); i++) {
                if (i > 0) {
                    reward = reward.copy().append("・");
                }
                reward = reward.copy().append(rs.get(i));
            }
            for (UUID m : g.members) {
                ServerPlayer p = server.getPlayerList().getPlayer(m);
                if (p != null) {
                    Notice.unlock(p, "message.skyabyss.guild.achieved", g.name, Component.translatable(a.nameKey()), reward);
                }
            }
        }
        if (n > 0) {
            GuildData.get(server).dirty();
        }
        return n;
    }
}
