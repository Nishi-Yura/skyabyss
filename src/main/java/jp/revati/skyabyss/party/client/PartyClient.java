package jp.revati.skyabyss.party.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.party.PartyNetwork;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// パーティのクライアント側。O で「物語と仲間」の画面を開き、画面の右に仲間の名前と体力を小さく出す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PartyClient {
    public static final KeyMapping PARTY_KEY = new KeyMapping("key.skyabyss.party", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.PARTY, "key.categories.skyabyss");

    static PartyNetwork.Sync last = new PartyNetwork.Sync(0, 15, 0, "", 70, List.of(), "");

    private PartyClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(PARTY_KEY);
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "party_members"), PartyClient::render);
        }
    }

    public static void receive(PartyNetwork.Sync sync) {
        last = sync;
        if (sync.members().isEmpty()) {
            PartyMarkers.clear();
        }
        if (Minecraft.getInstance().screen instanceof PartyScreen screen) {
            screen.refresh();
        }
    }

    public static PartyNetwork.Sync state() {
        return last;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        while (PARTY_KEY.consumeClick()) {
            if (mc.screen == null && mc.player != null) {
                mc.setScreen(new PartyScreen());
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        last = new PartyNetwork.Sync(0, 15, 0, "", 70, List.of(), "");
    }

    // ---------------------------------------------------------------- 仲間の表示 (右端。置き場は HudManager)
    static final int W = 84;

    // 一覧に出す仲間 (自分は除く)
    private static List<PartyNetwork.Member> others(Minecraft mc) {
        String self = mc.player.getUUID().toString();
        List<PartyNetwork.Member> out = new java.util.ArrayList<>();
        for (PartyNetwork.Member m : last.members()) {
            if (!m.id().equals(self)) {
                out.add(m);
            }
        }
        return out;
    }

    // 行の組み方 (docs/design/56 の 5 章): ふつうは一行 19 (名前と Lv の下に体力の帯)。右上の列に高さが足りないときは詰めた行 13
    // (体力は札の下の端の 2 画素の帯)。それでも足りなければ「別の場所にいる」の一行 (10) を出さない。仲間は自分を除いて最大 5 人
    private static final int FULL = 0, TIGHT = 1, TIGHT_NO_AWAY = 2;
    private static int mode = FULL;

    private static boolean away(PartyNetwork.Member m) {
        return m.online() && PartyMarkers.elsewhereLine(m.id()) != null;
    }

    private static int rowHeight(PartyNetwork.Member m, int mode) {
        return (mode == FULL ? 19 : 13) + (mode != TIGHT_NO_AWAY && away(m) ? 10 : 0);
    }

    private static int listHeight(List<PartyNetwork.Member> members, int mode) {
        int h = 0;
        for (PartyNetwork.Member m : members) {
            h += rowHeight(m, mode);
        }
        return h;
    }

    // 右上の列で仲間の帯に使える高さ: 前の枠の割り付けから、列にかかる部品のうち上にあるものの下端と、下にあるもの (スキルの枠など) の上端のあいだ。
    // 割り付け (hud/HudLayout) は入らない板を出さないので、ここで入る組み方を選んでから頼む
    private static int room(int guiW, int guiH) {
        jp.revati.skyabyss.hud.HudLayout.Result res = jp.revati.skyabyss.client.hud.HudManager.result();
        int gap = jp.revati.skyabyss.hud.HudLayout.GAP;
        int x0 = guiW - jp.revati.skyabyss.hud.HudLayout.M - (W + 4);
        int x1 = guiW - jp.revati.skyabyss.hud.HudLayout.M;
        int top = guiH / 3;
        int bottom = guiH - 2;
        java.util.List<java.util.Map.Entry<jp.revati.skyabyss.hud.HudLayout.Id, jp.revati.skyabyss.hud.HudLayout.Rect>> all = new java.util.ArrayList<>(res.placed.entrySet());
        all.addAll(res.vanilla.entrySet());
        for (var e : all) {
            jp.revati.skyabyss.hud.HudLayout.Rect p = e.getValue();
            if (e.getKey() == jp.revati.skyabyss.hud.HudLayout.Id.PARTY_LIST || p == null || p.right() <= x0 || p.x() >= x1) {
                continue;
            }
            if (p.y() < guiH / 3) {
                top = Math.max(top, p.bottom() + gap);
            } else if (p.y() >= guiH / 2) {
                bottom = Math.min(bottom, p.y() - gap);
            }
        }
        return bottom - top;
    }

    // HUD の要求: 仲間の行。画面の高さの 1/3 より下から
    public static void requests(Minecraft mc, int guiW, int guiH, List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || last.members().isEmpty()) {
            return;
        }
        List<PartyNetwork.Member> members = others(mc);
        if (members.isEmpty()) {
            return;
        }
        int room = room(guiW, guiH);
        mode = listHeight(members, FULL) <= room ? FULL : listHeight(members, TIGHT) <= room ? TIGHT : TIGHT_NO_AWAY;
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.PARTY_LIST, jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT, W + 4,
                listHeight(members, mode)).minTop(guiH / 3));
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.PARTY_LIST);
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen() || last.members().isEmpty() || r == null) {
            return;
        }
        Font font = mc.font;
        int x = r.x() + 2;
        int y = r.y() + 2;
        jp.revati.skyabyss.client.ui.Plate.Pal pal = jp.revati.skyabyss.client.ui.Plate.pal();
        boolean tight = mode != FULL;
        for (PartyNetwork.Member m : others(mc)) {
            net.minecraft.network.chat.Component away = mode != TIGHT_NO_AWAY && away(m) ? PartyMarkers.elsewhereLine(m.id()) : null;
            // 札の地 (Plate.hudCard)。「別の場所にいる」の一行があれば同じ札の中に入れる
            int cardBottom = y + (tight ? 10 : 15) + (away != null ? 10 : 0);
            jp.revati.skyabyss.client.ui.Plate.hudCard(g, x - 2, y - 2, x + W + 2, cardBottom, 0, 1f);
            // 名前の色: 同じ所にいる = 骨色、別の所 = 添えの灰、いない = まだの灰
            int color = !m.online() ? pal.dim : m.sameDim() ? pal.text : pal.soft;
            // 順路の段 (docs/design/32 の 6 章): 同じ背にいる仲間には、いまの段の番号を添える
            String step = m.sameDim() && jp.revati.skyabyss.client.trial.TrialBoardClient.routeActive(mc)
                    ? net.minecraft.network.chat.Component.translatable("gui.skyabyss.party.route_step", jp.revati.skyabyss.client.trial.TrialBoardClient.route().step()).getString()
                    : "";
            String name = step + (m.leader() ? "★" : "") + m.name();
            jp.revati.skyabyss.client.ui.FitText.draw(g, font, net.minecraft.network.chat.Component.literal(name), x, y, W - 28, color, true);
            if (m.online()) {
                String lv = "Lv" + m.level();
                g.drawString(font, lv, x + W - font.width(lv), y, jp.revati.skyabyss.client.ui.Plate.hudText(1f), true);
                float f = m.maxHealth() <= 0 ? 0 : Mth.clamp(m.health() / m.maxHealth(), 0, 1);
                // 体力の色 (意味の色。半分より上 = 緑、四分の一より上 = 黄、それより下 = 赤。別の所にいる人は灰)
                int hp = !m.sameDim() ? 0xFF7A7A7A : f > 0.5f ? 0xFF6BD06B : f > 0.25f ? 0xFFE8C04A : 0xFFE05050;
                if (tight) {
                    // 詰めた行: 札の下の端の 2 画素を体力の帯にする (上が色、下が一段暗い色。減った分は溝の色)
                    int bx = x - 1;
                    int bw = W + 2;
                    int fill = Math.round(bw * f);
                    g.fill(bx, y + 8, bx + bw, y + 10, pal.wellTL);
                    if (fill > 0) {
                        g.fill(bx, y + 8, bx + fill, y + 9, hp);
                        g.fill(bx, y + 9, bx + fill, y + 10, jp.revati.skyabyss.client.ui.Plate.mix(hp, 0xFF000000, 0.38f));
                    }
                } else {
                    jp.revati.skyabyss.client.ui.Plate.bar(g, x, y + 9, W, 5, f, 0f, hp, 0, 1f);
                }
            }
            if (away != null) {
                int tw = Math.min(font.width(away), W);
                int ay = y + (tight ? 11 : 16);
                g.drawString(font, net.minecraft.locale.Language.getInstance().getVisualOrder(font.substrByWidth(away, tw)), x + W - tw, ay, jp.revati.skyabyss.client.ui.Plate.hudSoft(1f), true);
            }
            y += rowHeight(m, mode);
        }
    }
}
