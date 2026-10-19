package jp.revati.skyabyss.opening.site;

import java.util.LinkedHashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.opening.SiteTargets;
import jp.revati.skyabyss.opening.OpeningSites;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 構造物の場所をクライアントへ送る (目的の帯 ObjectiveStrip.targets のため)。入ったとき、と、建て終わるたびに全員へ
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class SiteNetwork {
    private static final String PROTOCOL_VERSION = "opening-sites-1";

    private SiteNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar(PROTOCOL_VERSION)
                .playToClient(SiteTargetsPayload.TYPE, SiteTargetsPayload.STREAM_CODEC, (payload, context) -> SiteTargets.receive(payload));
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            PacketDistributor.sendToPlayer(p, payload(p.server));
        }
    }

    public static void broadcast(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(payload(server));
    }

    public static SiteTargetsPayload payload(MinecraftServer server) {
        Map<String, BlockPos> m = new LinkedHashMap<>();
        OpeningSites.camp(server).ifPresent(p -> m.put("camp", p));
        OpeningSites.notebook(server).ifPresent(p -> m.put("notebook", p));
        OpeningSites.shelter(server).ifPresent(p -> m.put("shelter", p));
        OpeningSites.bell(server).ifPresent(p -> m.put("bell", p));
        OpeningSites.ribs(server).ifPresent(p -> m.put("ribs", p));
        int i = 0;
        for (BlockPos p : OpeningSites.footprints(server)) {
            m.put(i == 0 ? "footprint" : "footprint" + i, p);
            i++;
        }
        i = 0;
        for (BlockPos p : OpeningSites.cairns(server)) {
            m.put("cairn" + i++, p);
        }
        // 話す相手の居場所 (道しるべの目印): 語り部は宿りの鐘のそば、鯨追いは外れの焚き火
        OpeningSites.bell(server).or(() -> OpeningSites.shelter(server)).ifPresent(bell -> {
            m.put(SiteTargets.NPC_PREFIX + "storyteller", bell);
            if (server.overworld() != null) {
                BlockPos chaser = jp.revati.skyabyss.npc.NpcSpawner.chaserSpot(server.overworld(), bell);
                if (chaser != null) {
                    m.put(SiteTargets.NPC_PREFIX + "whale_chaser", chaser);
                }
            }
        });
        // 巨神の最後の位置 (章のクエストの帯の方角と距離。動くので、下の onTick が変わるたびに送り直す)
        net.minecraft.server.level.ServerLevel overworld = server.overworld();
        if (overworld != null) {
            var data = jp.revati.skyabyss.titan.TitanWorldData.get(overworld);
            for (String titan : jp.revati.skyabyss.progress.Titans.all()) {
                var slot = data.slot(titan);
                if (slot != null && slot.hasLast) {
                    m.put(SiteTargets.TITAN_PREFIX + titan, BlockPos.containing(slot.lastX, slot.lastY, slot.lastZ));
                }
            }
        }
        return new SiteTargetsPayload(m);
    }

    private static Map<String, BlockPos> lastSent = Map.of();

    // 200 tick (10 秒) ごとに、巨神の位置が 8 ブロック以上動いていたら全員へ送り直す
    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 200 != 0 || server.getPlayerList().getPlayers().isEmpty()) {
            return;
        }
        SiteTargetsPayload p = payload(server);
        if (moved(lastSent, p.sites())) {
            lastSent = Map.copyOf(p.sites());
            PacketDistributor.sendToAllPlayers(p);
        }
    }

    public static boolean moved(Map<String, BlockPos> before, Map<String, BlockPos> now) {
        if (before.size() != now.size() || !before.keySet().equals(now.keySet())) {
            return true;
        }
        for (var e : now.entrySet()) {
            if (e.getValue().distSqr(before.get(e.getKey())) >= 64) {
                return true;
            }
        }
        return false;
    }
}
