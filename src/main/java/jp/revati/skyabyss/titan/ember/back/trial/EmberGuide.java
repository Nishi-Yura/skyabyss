package jp.revati.skyabyss.titan.ember.back.trial;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberNetwork;
import jp.revati.skyabyss.titan.ember.back.EmberBackLayout;
import jp.revati.skyabyss.titan.ember.back.EmberBackState;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 余燼の背の目的の板 (13 の 5.7。雷翼の StormGuide・潮殻の TideGuide と同じ型)。画面の右上、熱の目盛りの下に、
// 今いる部屋の「いま何をするか」の一行と進みの点を出す。サーバーが人ごとに決めて EmberNetwork.Goal で送る (変わったときと 5 秒ごと)。
// 試練ごとの一行は 1 手ずつ: 西 = 壺 → 渡る → 石、東 = 鞴 → 色を見て打つ (次の色つき) → 槽 → 扉 → 鍛え手 → 石、南 = 熾 → くべる → 石。
// 決まり: どの部屋も「その部屋の決まりを一つ教え、次にひねりを足す」 (西: 灰の殻は崩れる → 蛾が早く崩す、東: 色で打つ → 打つ順が変わる、南: 熾をくべる → 噴気で灰運びを焼く)
@EventBusSubscriber(modid = jp.revati.skyabyss.SkyAbyss.MODID)
public final class EmberGuide {
    public record Goal(String id, int done, int total, int a, int b) {
        public static final Goal NONE = new Goal("", 0, 0, 0, 0);
    }

    // 目的の板の文の id (lang の gui.skyabyss.ember_goal.<id>)。自己テストが ja・en の両方にあることを見る
    public static final List<String> GOAL_IDS = List.of("west.urn", "west.cross", "west.stone", "west.done", "east.bellows", "east.strike.red",
            "east.strike.orange", "east.strike.yellow", "east.quench", "east.door", "east.smith", "east.stone", "east.done", "south.coal", "south.feed",
            "south.stone", "south.done", "hub.stones", "hub.heart");

    private static final Map<UUID, Goal> LAST = new HashMap<>();

    private EmberGuide() {
    }

    // その人の今の目的。背中の外・試練の外 (広間以外) は NONE
    public static Goal goalFor(ServerLevel level, ServerPlayer p) {
        BlockPos at = p.blockPosition();
        EmberBackState state = EmberBackState.of(level, p);
        if (EmberBackLayout.WEST.contains(at)) {
            if (state.rung(EmberBackState.Trial.WEST)) {
                return new Goal("west.done", 3, 3, 0, 0);
            }
            if (state.ready(EmberBackState.Trial.WEST)) {
                return new Goal("west.stone", 2, 3, 0, 0);
            }
            boolean urn = EmberTrials.count(p, EmberTrialSetup.ASH_URN.get()) > 0;
            return new Goal(urn ? "west.cross" : "west.urn", urn ? 1 : 0, 3, 0, 0);
        }
        if (EmberBackLayout.EAST.contains(at) || EmberBackLayout.SMITH.contains(at)) {
            if (state.rung(EmberBackState.Trial.EAST)) {
                return new Goal("east.done", 5, 5, 0, 0);
            }
            if (state.ready(EmberBackState.Trial.EAST)) {
                return new Goal("east.stone", 4, 5, 0, 0);
            }
            if (EmberBackLayout.SMITH.contains(at)) {
                return new Goal("east.smith", 4, 5, 0, 0);
            }
            if (EmberTrials.count(p, EmberTrialSetup.FIRE_KEY.get()) > 0) {
                return new Goal("east.door", 3, 5, 0, 0);
            }
            if (EmberTrials.count(p, EmberTrialSetup.FIRE_KEY_HOT.get()) > 0) {
                return new Goal("east.quench", 2, 5, 0, 0);
            }
            int[] pattern = Forge.pattern(level);
            int hits = Forge.hits();
            if (hits <= 0 && Forge.bandOf(Forge.heat()) <= Forge.BLACK) {
                return new Goal("east.bellows", 0, 5, 0, pattern.length);
            }
            int next = pattern[Math.min(Math.max(0, hits), pattern.length - 1)];
            String color = next == Forge.RED ? "red" : next == Forge.ORANGE ? "orange" : "yellow";
            return new Goal("east.strike." + color, 1, 5, hits, pattern.length);
        }
        if (EmberBackLayout.SOUTH.contains(at)) {
            if (state.rung(EmberBackState.Trial.SOUTH)) {
                return new Goal("south.done", 4, 4, 0, 0);
            }
            int lv = Hearth.level(Hearth.strength());
            if (state.ready(EmberBackState.Trial.SOUTH) || Hearth.done()) {
                return new Goal("south.stone", 4, 4, 100, 0);
            }
            boolean coal = EmberTrials.count(p, EmberTrialSetup.LIVE_COAL.get()) > 0;
            return new Goal(coal ? "south.feed" : "south.coal", lv, 4, Math.round(Hearth.strength()), 0);
        }
        if (EmberBackLayout.HUB.contains(at) || EmberBackLayout.HUB.contains(at.below(2))) {
            int n = state.rungCount();
            return n >= 3 ? new Goal("hub.heart", 3, 3, 3, 0) : new Goal("hub.stones", n, 3, n, 0);
        }
        return Goal.NONE;
    }

    // 毎 10 tick: 目的が変わった人 (と 5 秒ごと) に送る。背中を出た人には空を送って消す
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 10 != 3) {
            return;
        }
        ServerLevel back = server.getLevel(EmberIds.BACK);
        boolean refresh = server.getTickCount() % 100 == 3;
        if (back != null) {
            for (ServerPlayer p : back.players()) {
                if (p instanceof FakePlayer || p.connection == null) {
                    continue;
                }
                send(p, goalFor(back, p), refresh);
            }
        }
        LAST.entrySet().removeIf(e -> {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p == null) {
                return true;
            }
            if (p.level().dimension() != EmberIds.BACK && !e.getValue().equals(Goal.NONE)) {
                PacketDistributor.sendToPlayer(p, toPacket(Goal.NONE));
                return true;
            }
            return p.level().dimension() != EmberIds.BACK;
        });
    }

    private static void send(ServerPlayer p, Goal g, boolean force) {
        Goal old = LAST.put(p.getUUID(), g);
        if (force || old == null || !old.equals(g)) {
            PacketDistributor.sendToPlayer(p, toPacket(g));
        }
    }

    static EmberNetwork.Goal toPacket(Goal g) {
        return new EmberNetwork.Goal(g.id(), g.done(), g.total(), g.a(), g.b());
    }
}
