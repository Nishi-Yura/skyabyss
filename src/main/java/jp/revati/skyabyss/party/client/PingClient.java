package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Ping;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 「ここ」の印 (docs/design/10 の 10.13) のクライアント側。キー (既定 X、設定で変えられる) で見ている先を知らせ、
// 届いた印を PartyMarkers と同じ描き方で出す (友の印 = 菱形・淡い青、敵の印 = 下向きの三角・朱。色に頼らない形)。
// 一人の送り主につき一つ (新しく出すと置き換わる)。TTL (8 秒) で消える。短い音が鳴る。設定 partyMarkers で描画を消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PingClient {
    public static final KeyMapping PING_KEY = new KeyMapping("key.skyabyss.ping", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.PING, "key.categories.skyabyss");

    private record Active(Ping.Show show, int expires) {
    }

    private static final List<Active> ACTIVE = new ArrayList<>();
    private static int tick;

    private PingClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(PING_KEY);
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_pings"), PingClient::render);
        }
    }

    public static void receive(Ping.Show show) {
        ACTIVE.removeIf(a -> a.show.senderId().equals(show.senderId()));
        ACTIVE.add(new Active(show, tick + show.ttl()));
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && show.dimension().equals(mc.level.dimension().location().toString())) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(show.enemy() ? SoundEvents.NOTE_BLOCK_BASS.value() : SoundEvents.EXPERIENCE_ORB_PICKUP,
                    show.enemy() ? 0.7f : 1.5f, 0.7f));
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        ACTIVE.removeIf(a -> a.expires <= tick);
        Minecraft mc = Minecraft.getInstance();
        while (PING_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null && !DownedClient.selfDowned()) {
                PacketDistributor.sendToServer(new Ping.Request());
            }
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (ACTIVE.isEmpty() || !Config.PARTY_MARKERS.get() || !PartyMarkers.hudAllowed(mc)) {
            return;
        }
        float partial = delta.getGameTimeDeltaPartialTick(false);
        String here = mc.level.dimension().location().toString();
        List<int[]> taken = new ArrayList<>();
        for (Active a : ACTIVE) {
            Ping.Show s = a.show;
            if (!s.dimension().equals(here)) {
                continue;
            }
            Vec3 pos = new Vec3(s.x(), s.y(), s.z());
            if (s.entityId() >= 0) {
                Entity e = mc.level.getEntity(s.entityId());
                if (e != null) {
                    pos = e.getPosition(partial).add(0, e.getBbHeight() + 0.4, 0);
                }
            }
            int dist = (int) Math.round(mc.player.position().distanceTo(pos));
            Component label = Component.translatable(s.enemy() ? "hud.skyabyss.ping.enemy" : "hud.skyabyss.ping.here", s.name(), dist);
            PartyMarkers.drawMarker(g, pos, label, s.enemy() ? 0xFF8A70 : 0x8FE0FF, s.enemy() ? PartyMarkers.Symbol.ENEMY : PartyMarkers.Symbol.PING, -1,
                    taken);
        }
    }
}
