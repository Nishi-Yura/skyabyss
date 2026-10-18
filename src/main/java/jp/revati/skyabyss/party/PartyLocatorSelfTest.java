package jp.revati.skyabyss.party;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.TitanTravel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;

// 仲間の位置 (docs/design/10 の 10.9) の自己テスト: パーティの人にだけ・仲間だけの位置を・変わったときだけ送り、抜けたら空を一度送って止める
public final class PartyLocatorSelfTest {
    private PartyLocatorSelfTest() {
    }

    public static void runSelfTest(MinecraftServer server) {
        List<String> f = new ArrayList<>();
        try {
            run(server, f);
        } catch (RuntimeException e) {
            SkyAbyss.LOGGER.error("SELFTEST party locator crashed", e);
            f.add("crashed: " + e);
        }
        if (f.isEmpty()) {
            SkyAbyss.LOGGER.info("SELFTEST party locator PASSED");
        } else {
            f.forEach(m -> SkyAbyss.LOGGER.error("SELFTEST party locator FAILED: {}", m));
        }
    }

    private static FakePlayer player(ServerLevel level, String name) {
        return new FakePlayer(level, new GameProfile(UUID.randomUUID(), name));
    }

    private static void run(MinecraftServer server, List<String> f) {
        ServerLevel over = server.overworld();
        ServerLevel back = server.getLevel(TitanTravel.TITAN_BACK);
        if (back == null) {
            f.add("titan_back dimension is missing");
            return;
        }
        FakePlayer a = player(over, "loc_a");
        FakePlayer b = player(over, "loc_b");
        FakePlayer c = player(back, "loc_c");
        FakePlayer out = player(over, "loc_out");
        a.setPos(0, 100, 0);
        b.setPos(30, 100, 40);
        c.setPos(5, 100, 5);
        out.setPos(1, 100, 1);
        Map<UUID, ServerPlayer> online = new HashMap<>();
        for (FakePlayer p : List.of(a, b, c, out)) {
            online.put(p.getUUID(), p);
        }
        List<FakePlayer> all = List.of(a, b, c, out);
        Map<UUID, List<PartyLocator.Pos>> got = new HashMap<>();
        PartyData parties = PartyData.get(server);
        var snapshot = parties.snapshot(server.registryAccess());
        try {
            parties.clearAll();
            PartyLocator.clearForTest();
            PartyData.Party party = parties.create(a.getUUID(), "loc_a");
            parties.add(party, b.getUUID(), "loc_b");
            parties.add(party, c.getUUID(), "loc_c");
            // 1 回目: 仲間 3 人だけに送る。パーティの外の人には送らない
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            if (got.containsKey(out.getUUID())) {
                f.add("a player outside the party received positions");
            }
            for (FakePlayer p : List.of(a, b, c)) {
                List<PartyLocator.Pos> l = got.get(p.getUUID());
                if (l == null || l.size() != 1) {
                    f.add(p.getGameProfile().getName() + " did not get exactly one packet at first");
                    continue;
                }
                List<PartyLocator.Entry> es = l.get(0).entries();
                if (es.size() != 2) {
                    f.add(p.getGameProfile().getName() + " got " + es.size() + " entries, expected the 2 other members");
                }
                for (PartyLocator.Entry e : es) {
                    if (e.id().equals(p.getUUID().toString()) || e.id().equals(out.getUUID().toString())) {
                        f.add("entry list of " + p.getGameProfile().getName() + " has itself or a non-member");
                    }
                }
            }
            // c は別の次元として届く
            PartyLocator.Entry cEntry = got.get(a.getUUID()) == null ? null : got.get(a.getUUID()).get(0).entries().stream()
                    .filter(e -> e.id().equals(c.getUUID().toString())).findFirst().orElse(null);
            if (cEntry == null || !cEntry.dimension().equals(TitanTravel.TITAN_BACK.location().toString())) {
                f.add("a member in the back did not arrive with the back's dimension");
            }
            // 変化なし: 送らない
            got.clear();
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            if (!got.isEmpty()) {
                f.add("packets were sent although nobody moved (spam)");
            }
            // b が動いた: a と c にだけ送る (b 自身の一覧は変わらない)
            b.setPos(60, 100, 40);
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            if (!got.containsKey(a.getUUID()) || !got.containsKey(c.getUUID()) || got.containsKey(b.getUUID()) || got.containsKey(out.getUUID())
                    || got.size() != 2) {
                f.add("after b moved, packets did not go exactly to a and c: " + got.keySet().size());
            }
            // 1 ブロック未満の揺れでは送らない
            got.clear();
            b.setPos(60.3, 100, 40.2);
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            if (!got.isEmpty()) {
                f.add("a sub-block jitter caused packets");
            }
            // c が抜ける: c と、残りの a・b (相手が減る) にだけ。c へは空
            got.clear();
            parties.remove(c.getUUID());
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            List<PartyLocator.Pos> cl = got.get(c.getUUID());
            if (cl == null || cl.size() != 1 || !cl.get(0).entries().isEmpty()) {
                f.add("the member who left did not get one empty list");
            }
            List<PartyLocator.Pos> al = got.get(a.getUUID());
            if (al != null && al.stream().anyMatch(p -> p.entries().stream().anyMatch(e -> e.id().equals(c.getUUID().toString())))) {
                f.add("the remaining members still see the one who left");
            }
            // 解散 (残りが一人) で全員に空を一度だけ、その後は止まる
            got.clear();
            parties.remove(b.getUUID());
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            for (FakePlayer p : List.of(a, b)) {
                List<PartyLocator.Pos> l = got.get(p.getUUID());
                if (l != null && l.stream().anyMatch(x -> !x.entries().isEmpty())) {
                    f.add(p.getGameProfile().getName() + " still got positions after the party disbanded");
                }
            }
            got.clear();
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            PartyLocator.tick(server, all, online::get, (p, pos) -> got.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(pos));
            if (!got.isEmpty()) {
                f.add("packets kept coming after the party disbanded");
            }
        } finally {
            parties.restore(snapshot, server.registryAccess());
            PartyLocator.clearForTest();
        }
    }
}
