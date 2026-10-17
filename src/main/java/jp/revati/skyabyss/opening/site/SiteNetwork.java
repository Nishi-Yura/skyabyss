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
        return new SiteTargetsPayload(m);
    }
}
