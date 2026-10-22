package jp.revati.skyabyss.abyss.arts.client;

import java.util.Arrays;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.arts.AbyssArt;
import jp.revati.skyabyss.abyss.arts.AbyssArts;
import jp.revati.skyabyss.abyss.arts.ArtData;
import jp.revati.skyabyss.abyss.arts.net.ArtCastPayload;
import jp.revati.skyabyss.abyss.arts.net.ArtCooldownPayload;
import jp.revati.skyabyss.story.ch5.AbyssWorld;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 深淵術の鍵・左の表示 (docs/design/36 の 5.5)。覚えていない・待ち・使える を一行ずつ。鍵はサーバーへ送るだけ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ArtClient {
    public static final KeyMapping[] KEYS = new KeyMapping[AbyssArt.values().length];

    static {
        for (AbyssArt a : AbyssArt.values()) {
            KEYS[a.ordinal()] = new KeyMapping("key.skyabyss.abyss_art_" + a.id, KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM, a.key, "key.categories.skyabyss");
        }
    }

    // 待ちの終わり (ゲームの tick)。サーバーから届く
    private static final long[] UNTIL = new long[AbyssArt.values().length];

    private ArtClient() {
    }

    public static void onCooldown(ArtCooldownPayload p) {
        if (p.art() >= 0 && p.art() < UNTIL.length) {
            UNTIL[p.art()] = p.until();
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Arrays.fill(UNTIL, 0L);
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        for (KeyMapping k : KEYS) {
            event.register(k);
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) {
            return;
        }
        for (AbyssArt a : AbyssArt.values()) {
            while (KEYS[a.ordinal()].consumeClick()) {
                PacketDistributor.sendToServer(new ArtCastPayload(a.ordinal()));
            }
        }
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_arts"), ArtClient::render);
    }

    // 絵の場所と、行の文 (文は残り時間が tick ごとにしか変わらないので、tick ごとに一度だけ作る)
    private static final ResourceLocation[] ICONS = java.util.Arrays.stream(AbyssArt.values())
            .map(a -> ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/abyss_art/" + a.id + ".png")).toArray(ResourceLocation[]::new);
    private static final Component[] LINES = new Component[AbyssArt.values().length];
    // 右の欄が狭いときの短い行 (名前なし。絵で分かる)
    private static final Component[] SHORT = new Component[AbyssArt.values().length];
    private static final int[] COLORS = new int[AbyssArt.values().length];
    private static long linesTick = Long.MIN_VALUE;

    private static void buildLines(Minecraft mc, long now) {
        ArtData data = AbyssArts.data(mc.player);
        for (AbyssArt a : AbyssArt.values()) {
            int rank = data.rank(a);
            String keyName = KEYS[a.ordinal()].getTranslatedKeyMessage().getString();
            Component name = Component.translatable(a.nameKey());
            Component line;
            Component shortLine;
            int color;
            if (rank == 0) {
                line = Component.translatable("hud.skyabyss.art.none", keyName, name);
                shortLine = Component.translatable("hud.skyabyss.art.short_none", keyName);
                color = 0x808080;
            } else {
                long left = Math.max(0, UNTIL[a.ordinal()] - now);
                String secs = String.format("%.1f", left / 20.0);
                line = left > 0
                        ? Component.translatable("hud.skyabyss.art.wait", keyName, name, rank, secs)
                        : Component.translatable("hud.skyabyss.art.ready", keyName, name, rank);
                shortLine = left > 0
                        ? Component.translatable("hud.skyabyss.art.short_wait", keyName, rank, secs)
                        : Component.translatable("hud.skyabyss.art.short_ready", keyName, rank);
                color = left > 0 ? 0xB8B8B8 : 0xE6F4FF;
            }
            LINES[a.ordinal()] = line;
            SHORT[a.ordinal()] = shortLine;
            COLORS[a.ordinal()] = color;
        }
        linesTick = now;
    }

    private static final int ROW = 20;
    private static final int SHORT_W = 80;
    private static int fullW;

    // 右の真ん中あたりに、右寄せで。右上の板 (誓い・背中の目盛り・仲間) とスコアボードを避けるので、置き場は HudLayout に頼む
    public static void requests(Minecraft mc, int guiW, int guiH, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != AbyssWorld.LEVEL) {
            return;
        }
        long now = mc.level.getGameTime();
        if (now != linesTick) {
            buildLines(mc, now);
        }
        int w = 0;
        for (Component c : LINES) {
            w = Math.max(w, mc.font.width(c));
        }
        // 幅は 16 刻みに丸める (待ちの秒で文字の幅が変わるたびに置き場の計算をやり直さない)。右の欄に入らないなら、入る幅まで縮める (短い行になる)
        fullW = (w + 4 + 16 + 4 + 4 + 15) / 16 * 16;
        int fw = fullW;
        int h = ROW * LINES.length;
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_ARTS,
                jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT,
                jp.revati.skyabyss.hud.HudLayout.Fit.keyed("abyss_arts:" + fw + ":" + h,
                        (maxW, maxH) -> maxW >= SHORT_W && maxH >= h ? new jp.revati.skyabyss.hud.HudLayout.Size(Math.min(fw, maxW), h) : null))
                .minTop(guiH / 2 - 24));
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.ABYSS_ARTS);
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != AbyssWorld.LEVEL || r == null) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        if (now != linesTick) {
            buildLines(mc, now);
        }
        int iconX = r.right() - 4 - 16;
        int y = r.y();
        for (AbyssArt a : AbyssArt.values()) {
            int row = y + a.ordinal() * ROW;
            // 絵 (32px の絵を半分の大きさで出す)
            g.pose().pushPose();
            g.pose().scale(0.5f, 0.5f, 1f);
            g.blit(ICONS[a.ordinal()], iconX * 2, row * 2, 0, 0, 32, 32, 32, 32);
            g.pose().popPose();
            Component line = r.w() >= fullW ? LINES[a.ordinal()] : SHORT[a.ordinal()];
            int color = COLORS[a.ordinal()];
            g.drawString(font, line, iconX - 4 - font.width(line), row + 4, color, true);
        }
    }
}
