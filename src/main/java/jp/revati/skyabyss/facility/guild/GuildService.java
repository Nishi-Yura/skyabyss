package jp.revati.skyabyss.facility.guild;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.facility.FacilityNetwork;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

// ギルドの画面 (組合の受付の書記から。client/facility/GuildScreen) のサーバー側。画面は今の中身を映し、押したことを返すだけ
public final class GuildService {
    private GuildService() {
    }

    public static void open(ServerPlayer player, @Nullable Entity npc) {
        send(player, view(player));
    }

    public static FacilityNetwork.GuildView view(ServerPlayer player) {
        GuildData data = GuildData.get(player.server);
        GuildData.Guild mine = data.of(player.getUUID());
        List<FacilityNetwork.GuildRow> rows = new ArrayList<>();
        for (GuildData.Guild g : data.all()) {
            rows.add(new FacilityNetwork.GuildRow(g.id, g.name, g.tag, g.color, g.accent, g.members.size(), g.open,
                    g.applicants.contains(player.getUUID()), g.nameOf(g.leader)));
        }
        List<FacilityNetwork.MemberRow> members = new ArrayList<>();
        List<FacilityNetwork.MemberRow> applicants = new ArrayList<>();
        boolean leader = false;
        if (mine != null) {
            leader = mine.leader.equals(player.getUUID());
            for (UUID u : mine.members) {
                members.add(new FacilityNetwork.MemberRow(u, mine.nameOf(u), u.equals(mine.leader), player.server.getPlayerList().getPlayer(u) != null));
            }
            if (leader) {
                for (UUID u : mine.applicants) {
                    applicants.add(new FacilityNetwork.MemberRow(u, mine.nameOf(u), false, player.server.getPlayerList().getPlayer(u) != null));
                }
            }
        }
        return new FacilityNetwork.GuildView(mine == null ? "" : mine.id, leader, mine != null && mine.open, rows, members, applicants,
                GuildData.MAX_MEMBERS);
    }

    // 画面から: op = create / apply / leave / accept / deny / kick / open
    public static Guilds.Result act(ServerPlayer player, FacilityNetwork.GuildAction a) {
        Guilds.Result r = switch (a.op()) {
            case "create" -> Guilds.create(player, a.text(), a.tag(), a.color(), a.accent());
            case "apply" -> Guilds.apply(player, a.target());
            case "leave" -> Guilds.leave(player);
            case "accept", "deny", "kick" -> {
                UUID who;
                try {
                    who = UUID.fromString(a.target());
                } catch (IllegalArgumentException e) {
                    yield Guilds.Result.NOT_MEMBER;
                }
                yield a.op().equals("kick") ? Guilds.kick(player, who) : Guilds.answer(player, who, a.op().equals("accept"));
            }
            case "open" -> Guilds.setOpen(player, a.flag());
            default -> Guilds.Result.NO_GUILD;
        };
        Notice.hint(player, r.key);
        send(player, view(player));
        return r;
    }

    static void send(ServerPlayer player, FacilityNetwork.GuildView v) {
        if (player instanceof FakePlayer || player.connection == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, v);
    }
}
