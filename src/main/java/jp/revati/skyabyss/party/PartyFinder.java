package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.Growth;
import jp.revati.skyabyss.ModText;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

// パーティー募集 (2026-11-07 の声「パーティー検索もできるようにしてほしい」。Wynncraft の Party Finder のように)。
// リーダー (一人の人も) が目的を選んで募集を出すと、パーティーに入っていない人の募集の一覧に出る。一覧の「加わる」で、招待を待たずにそのまま入る。
// 募集は、出した人がいなくなった・リーダーでなくなった・満員になったら消える。保存しない (サーバーを止めたら消える)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class PartyFinder {
    // 目的 (gui.skyabyss.party.goal.<id>)
    public static final List<String> GOALS = List.of("any", "story", "route", "dungeon", "event", "tower", "lootrun", "fishing");

    public record Listing(UUID leader, String leaderName, String goal, long since) {
    }

    // 一覧の一行 (画面へ送る)。mine = 見ている人の募集
    public record Row(String leaderName, String goal, int members, int avgLevel, boolean mine) {
    }

    private static final Map<UUID, Listing> LISTINGS = new LinkedHashMap<>();

    private PartyFinder() {
    }

    // 募集を出す (目的を変えるのも同じ)。出せたか
    public static boolean post(ServerPlayer player, String goal) {
        if (!GOALS.contains(goal)) {
            goal = "any";
        }
        PartyData.Party party = Parties.of(player);
        if (party != null && !party.isLeader(player.getUUID())) {
            Notice.bar(player, ModText.deny("message.skyabyss.party.finder.not_leader"));
            return false;
        }
        if (party != null && party.members.size() >= Parties.MAX_MEMBERS) {
            Notice.bar(player, ModText.deny("message.skyabyss.party.full", Parties.MAX_MEMBERS));
            return false;
        }
        boolean fresh = !LISTINGS.containsKey(player.getUUID());
        LISTINGS.put(player.getUUID(), new Listing(player.getUUID(), player.getGameProfile().getName(), goal, player.server.getTickCount()));
        if (fresh) {
            Notice.hint(player, "message.skyabyss.party.finder.posted");
        }
        return true;
    }

    public static boolean close(ServerPlayer player) {
        return LISTINGS.remove(player.getUUID()) != null;
    }

    // その人の募集 (なければ null)
    public static Listing of(UUID leader) {
        return LISTINGS.get(leader);
    }

    // 一覧の募集に加わる。入れたか
    public static boolean join(ServerPlayer player, String leaderName) {
        MinecraftServer server = player.server;
        PartyData data = PartyData.get(server);
        if (data.of(player.getUUID()) != null) {
            Notice.bar(player, ModText.deny("message.skyabyss.party.leave_first"));
            return false;
        }
        Listing listing = null;
        for (Listing l : LISTINGS.values()) {
            if (l.leaderName().equalsIgnoreCase(leaderName)) {
                listing = l;
            }
        }
        ServerPlayer leader = listing == null ? null : server.getPlayerList().getPlayer(listing.leader());
        if (listing == null || leader == null || listing.leader().equals(player.getUUID())) {
            Notice.bar(player, ModText.deny("message.skyabyss.party.finder.gone"));
            return false;
        }
        PartyData.Party party = data.of(leader.getUUID());
        if (party == null) {
            party = data.create(leader.getUUID(), leader.getGameProfile().getName());
        } else if (!party.isLeader(leader.getUUID())) {
            LISTINGS.remove(listing.leader());
            Notice.bar(player, ModText.deny("message.skyabyss.party.finder.gone"));
            return false;
        }
        if (party.members.size() >= Parties.MAX_MEMBERS) {
            LISTINGS.remove(listing.leader());
            Notice.bar(player, ModText.deny("message.skyabyss.party.full", Parties.MAX_MEMBERS));
            return false;
        }
        data.add(party, player.getUUID(), player.getGameProfile().getName());
        for (ServerPlayer m : Parties.onlineMembers(server, party)) {
            Notice.unlock(m, "message.skyabyss.party.finder.joined", player.getDisplayName(),
                    net.minecraft.network.chat.Component.translatable("gui.skyabyss.party.goal." + listing.goal()));
            jp.revati.skyabyss.tutorial.TutorialHints.start(m, jp.revati.skyabyss.tutorial.TutorialHints.PING);
            PartyNetwork.syncSoon(m);
        }
        if (party.members.size() >= Parties.MAX_MEMBERS) {
            LISTINGS.remove(listing.leader());
        }
        SkyAbyss.LOGGER.info("Party finder: {} joined {}'s party ({})", player.getGameProfile().getName(), listing.leaderName(), listing.goal());
        return true;
    }

    // 見ている人に送る一覧 (新しい順。自分の募集を先に)
    public static List<Row> rows(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        prune(server);
        List<Row> out = new ArrayList<>();
        List<Listing> list = new ArrayList<>(LISTINGS.values());
        list.sort((a, b) -> Long.compare(b.since(), a.since()));
        for (Listing l : list) {
            PartyData.Party party = Parties.of(server.getPlayerList().getPlayer(l.leader()));
            List<ServerPlayer> on = party == null ? List.of(server.getPlayerList().getPlayer(l.leader())) : Parties.onlineMembers(server, party);
            int n = party == null ? 1 : party.members.size();
            int sum = 0;
            for (ServerPlayer m : on) {
                sum += Growth.get(m).level;
            }
            Row row = new Row(l.leaderName(), l.goal(), n, on.isEmpty() ? 0 : Math.round((float) sum / on.size()), l.leader().equals(viewer.getUUID()));
            if (row.mine()) {
                out.add(0, row);
            } else {
                out.add(row);
            }
        }
        return out;
    }

    // 出した人がいない・リーダーでない・満員の募集を消す
    static void prune(MinecraftServer server) {
        LISTINGS.values().removeIf(l -> {
            ServerPlayer p = server.getPlayerList().getPlayer(l.leader());
            if (p == null) {
                return true;
            }
            PartyData.Party party = Parties.of(p);
            return party != null && (!party.isLeader(l.leader()) || party.members.size() >= Parties.MAX_MEMBERS);
        });
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 100 == 41 && !LISTINGS.isEmpty()) {
            prune(event.getServer());
        }
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        LISTINGS.clear();
    }

    // 自己テスト用
    public static void clearAll() {
        LISTINGS.clear();
    }
}
