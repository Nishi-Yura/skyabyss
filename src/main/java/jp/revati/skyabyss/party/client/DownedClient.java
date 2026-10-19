package jp.revati.skyabyss.party.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.Downed;
import jp.revati.skyabyss.party.DownedPackets;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

// 倒れた仲間の表示と、起こす操作 (docs/design/10 の 10.12)。
//  - 自分が倒れたとき: 画面の中央下に「倒れている」と残り秒の棒、画面の縁に薄い赤み。起こされている間は進みを見せる
//  - 倒れた仲間: 頭の上 (画面の外なら縁) に ×印の札 (名前・残り秒・距離。PartyMarkers の描き方)
//  - 倒れた仲間を見ている (3.5 ブロック以内): 「使う」の長押しで起こす。照準の周りに進みの輪。押している間 2 tick ごとに信号をサーバーへ送る
// 設定 partyMarkers で、仲間の札だけ消せる (自分の画面と起こす操作は消えない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DownedClient {
    private static List<DownedPackets.Entry> entries = List.of();
    private static int tick;

    private DownedClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "downed_hud"), DownedClient::render);
        }
    }

    public static void receive(DownedPackets.Sync sync) {
        entries = sync.entries();
    }

    public static void clear() {
        entries = List.of();
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    // 自分が倒れているか
    public static boolean selfDowned() {
        return self() != null;
    }

    private static DownedPackets.Entry self() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        String id = mc.player.getUUID().toString();
        for (DownedPackets.Entry e : entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    // 照準の先にいる、起こせる倒れた仲間 (なければ null)
    private static DownedPackets.Entry aimed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || entries.isEmpty() || selfDowned()) {
            return null;
        }
        Entity target = mc.crosshairPickEntity;
        if (!(target instanceof Player other) || other == mc.player || mc.player.distanceTo(other) > Downed.REACH) {
            return null;
        }
        String id = other.getUUID().toString();
        for (DownedPackets.Entry e : entries) {
            if (e.id().equals(id)) {
                return e;
            }
        }
        return null;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        tick++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || entries.isEmpty() || tick % 2 != 0 || !mc.options.keyUse.isDown()) {
            return;
        }
        DownedPackets.Entry e = aimed();
        if (e != null) {
            try {
                PacketDistributor.sendToServer(new DownedPackets.Hold(UUID.fromString(e.id())));
            } catch (IllegalArgumentException ex) {
                // 壊れた名前は送らない
            }
        }
    }

    // 倒れた仲間を見ている間は「使う」を食べ物・ブロックに回さない。自分が倒れている間は何も受け付けない
    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (selfDowned() || (event.isUseItem() && aimed() != null)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    // ---------------------------------------------------------------- 描く
    // HUD の要求: 自分が倒れているときの表示は下中央 (手がかりの下)。仲間を起こす輪と文は中央 (照準のまわり。ほかの部品はそこを避ける)
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (entries.isEmpty() || !PartyMarkers.hudAllowed(mc)) {
            return;
        }
        if (self() != null) {
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.DOWNED_SELF, jp.revati.skyabyss.hud.HudLayout.Region.BOTTOM_CENTER, 124, 41));
            return;
        }
        DownedPackets.Entry aim = aimed();
        if (aim != null) {
            boolean active = aim.progressPercent() > 0 || mc.options.keyUse.isDown();
            Component text = Component.translatable(active ? "hud.skyabyss.downed.reviving" : "hud.skyabyss.downed.hold", aim.name());
            int tw = Math.max(28, mc.font.width(text));
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.center(jp.revati.skyabyss.hud.HudLayout.Id.AIM_RING, -tw / 2, -14, tw, 45));
        }
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (entries.isEmpty() || !PartyMarkers.hudAllowed(mc)) {
            return;
        }
        Font font = mc.font;
        int w = g.guiWidth();
        int h = g.guiHeight();
        DownedPackets.Entry me = self();
        if (me != null) {
            jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.DOWNED_SELF);
            renderSelf(g, font, me, w, h, r);
            return;
        }
        // 倒れた仲間の札
        if (Config.PARTY_MARKERS.get()) {
            float partial = delta.getGameTimeDeltaPartialTick(false);
            String here = mc.level.dimension().location().toString();
            List<int[]> taken = new ArrayList<>();
            for (DownedPackets.Entry e : entries) {
                if (!e.dimension().equals(here)) {
                    continue;
                }
                Vec3 pos = new Vec3(e.x() + 0.5, e.y() + 1.2, e.z() + 0.5);
                Player live = null;
                try {
                    live = mc.level.getPlayerByUUID(UUID.fromString(e.id()));
                } catch (IllegalArgumentException ex) {
                    // 控えの名前が壊れていても落ちない
                }
                if (live != null) {
                    pos = live.getPosition(partial).add(0, live.getBbHeight() + 0.9, 0);
                }
                int dist = (int) Math.round(mc.player.position().distanceTo(pos));
                Component label = Component.translatable("hud.skyabyss.downed.mate", e.name(), (e.ticksLeft() + 19) / 20, dist);
                PartyMarkers.drawMarker(g, pos, label, 0xFF7A7A, PartyMarkers.Symbol.DOWNED, -1, taken);
            }
        }
        // 起こす: 照準の周りの輪
        DownedPackets.Entry aim = aimed();
        if (aim != null) {
            int cx = w / 2;
            int cy = h / 2;
            boolean active = aim.progressPercent() > 0 || mc.options.keyUse.isDown();
            drawRing(g, cx, cy, 14, Math.max(0, Math.min(100, aim.progressPercent())), 0xFF9FE8B0);
            Component text = Component.translatable(active ? "hud.skyabyss.downed.reviving" : "hud.skyabyss.downed.hold", aim.name());
            g.drawCenteredString(font, text, cx, cy + 22, 0xFFFFFFFF);
        }
    }

    private static void renderSelf(GuiGraphics g, Font font, DownedPackets.Entry me, int w, int h, @javax.annotation.Nullable jp.revati.skyabyss.hud.HudLayout.Rect r) {
        // 画面の縁の薄い赤み
        g.fill(0, 0, w, 8, 0x30602020);
        g.fill(0, h - 8, w, h, 0x30602020);
        g.fill(0, 8, 8, h - 8, 0x30602020);
        g.fill(w - 8, 8, w, h - 8, 0x30602020);
        if (r == null) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y();
        g.drawCenteredString(font, Component.translatable("hud.skyabyss.downed.self"), cx, y, 0xFFFF8A8A);
        int seconds = (me.ticksLeft() + 19) / 20;
        float f = Math.max(0f, Math.min(1f, me.ticksLeft() / (float) Downed.DOWN_TICKS));
        int bw = 120;
        g.fill(cx - bw / 2, y + 14, cx + bw / 2, y + 18, 0x80000000);
        g.fill(cx - bw / 2, y + 14, cx - bw / 2 + Math.round(bw * f), y + 18, 0xFFFF7A7A);
        boolean rev = me.progressPercent() > 0;
        Component line = Component.translatable(rev ? "hud.skyabyss.downed.self_reviving" : "hud.skyabyss.downed.self_wait", seconds);
        g.drawCenteredString(font, line, cx, y + 24, 0xFFE8E8E8);
        if (rev) {
            g.fill(cx - bw / 2, y + 36, cx + bw / 2, y + 40, 0x80000000);
            g.fill(cx - bw / 2, y + 36, cx - bw / 2 + Math.round(bw * me.progressPercent() / 100f), y + 40, 0xFF9FE8B0);
        }
    }

    // 輪: 32 の点を並べ、percent の分だけ明るく塗る (上から時計回り)
    static void drawRing(GuiGraphics g, int cx, int cy, int radius, int percent, int color) {
        int n = 32;
        int lit = Math.round(n * percent / 100f);
        for (int i = 0; i < n; i++) {
            double a = -Math.PI / 2 + Math.PI * 2 * i / n;
            int x = cx + (int) Math.round(Math.cos(a) * radius);
            int y = cy + (int) Math.round(Math.sin(a) * radius);
            g.fill(x - 1, y - 1, x + 1, y + 1, i < lit ? color : 0x66FFFFFF);
        }
    }
}
