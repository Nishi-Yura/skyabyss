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

    // つまみを決めて開く (コマンド・撮影用)
    public static void open(ServerPlayer player, int tab) {
        FacilityNetwork.GuildView v = view(player);
        send(player, new FacilityNetwork.GuildView(v.mine(), v.leader(), v.open(), v.guilds(), v.members(), v.applicants(), v.max(), v.rank(),
                v.takeRank(), v.req(), v.pattern(), v.plate(), v.unlocked(), v.stats(), v.banner(), tab));
    }

    public static FacilityNetwork.GuildView view(ServerPlayer player) {
        GuildData data = GuildData.get(player.server);
        GuildData.Guild mine = data.of(player.getUUID());
        List<FacilityNetwork.GuildRow> rows = new ArrayList<>();
        for (GuildData.Guild g : data.all()) {
            rows.add(new FacilityNetwork.GuildRow(g.id, g.name, g.tag, g.color, g.accent, g.members.size(), g.open,
                    g.applicants.contains(player.getUUID()), g.nameOf(g.leader), g.plate));
        }
        List<FacilityNetwork.MemberRow> members = new ArrayList<>();
        List<FacilityNetwork.MemberRow> applicants = new ArrayList<>();
        boolean leader = false;
        if (mine != null) {
            leader = mine.leader.equals(player.getUUID());
            for (UUID u : mine.members) {
                members.add(new FacilityNetwork.MemberRow(u, mine.nameOf(u), u.equals(mine.leader), player.server.getPlayerList().getPlayer(u) != null,
                        mine.rank(u)));
            }
            if (mine.rank(player.getUUID()) >= GuildData.RANK_OFFICER) {
                for (UUID u : mine.applicants) {
                    applicants.add(new FacilityNetwork.MemberRow(u, mine.nameOf(u), false, player.server.getPlayerList().getPlayer(u) != null, 0));
                }
            }
        }
        FacilityNetwork.ReqView req = FacilityNetwork.ReqView.NONE;
        if (mine != null) {
            GuildRequests.Req r = GuildRequests.current(player.server, mine);
            List<java.util.Map.Entry<UUID, Integer>> top = new ArrayList<>(mine.contrib.entrySet());
            top.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            List<String> names = new ArrayList<>();
            List<Integer> counts = new ArrayList<>();
            for (int i = 0; i < top.size() && i < 5; i++) {
                names.add(mine.nameOf(top.get(i).getKey()));
                counts.add(top.get(i).getValue());
            }
            req = new FacilityNetwork.ReqView(r.id(), new net.minecraft.world.item.ItemStack(r.icon()), r.target(), mine.reqProgress, mine.reqDone,
                    r.kill(), mine.contrib.getOrDefault(player.getUUID(), 0), names, counts);
        }
        List<Integer> stats = mine == null ? List.of(0, 0, 0) : List.of(mine.reqCompleted, mine.totalContrib,
                GuildAchievements.value(mine, GuildAchievements.Kind.MEMBERS));
        return new FacilityNetwork.GuildView(mine == null ? "" : mine.id, leader, mine != null && mine.open, rows, members, applicants,
                GuildData.MAX_MEMBERS, mine == null ? 0 : mine.rank(player.getUUID()), mine == null ? GuildData.RANK_OFFICER : mine.takeRank, req,
                mine == null ? GuildEmblems.DEFAULT : mine.pattern, mine == null ? "" : mine.plate,
                mine == null ? List.of() : new ArrayList<>(mine.unlocked), stats, Guilds.BANNER_COST, -1);
    }

    // 画面から: op = create / apply / leave / accept / deny / kick / open / officer / take / emblem / banner / deliver / transfer / disband / storage
    public static Guilds.Result act(ServerPlayer player, FacilityNetwork.GuildAction a) {
        Guilds.Result r = switch (a.op()) {
            case "create" -> Guilds.create(player, a.text(), a.tag(), a.color(), a.accent());
            case "apply" -> Guilds.apply(player, a.target());
            case "leave" -> Guilds.leave(player);
            case "accept", "deny", "kick", "transfer" -> {
                UUID who;
                try {
                    who = UUID.fromString(a.target());
                } catch (IllegalArgumentException e) {
                    yield Guilds.Result.NOT_MEMBER;
                }
                yield switch (a.op()) {
                    case "kick" -> Guilds.kick(player, who);
                    case "transfer" -> Guilds.transfer(player, who);
                    default -> Guilds.answer(player, who, a.op().equals("accept"));
                };
            }
            case "open" -> Guilds.setOpen(player, a.flag());
            case "officer" -> {
                UUID who;
                try {
                    who = UUID.fromString(a.target());
                } catch (IllegalArgumentException e) {
                    yield Guilds.Result.NOT_MEMBER;
                }
                yield Guilds.setOfficer(player, who, a.flag());
            }
            case "take" -> Guilds.setTakeRank(player, a.color());
            case "emblem" -> Guilds.setEmblem(player, a.tag(), a.color(), a.accent(), a.target(), a.text());
            case "disband" -> Guilds.disband(player);
            case "banner" -> Guilds.takeBanner(player);
            case "deliver" -> GuildRequests.deliver(player);
            case "storage" -> {
                GuildStorage.open(player, null);
                yield null;
            }
            default -> Guilds.Result.NO_GUILD;
        };
        if (r == null) {
            return Guilds.Result.OK;
        }
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
