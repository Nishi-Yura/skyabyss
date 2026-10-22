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
    private static final int[] COLORS = new int[AbyssArt.values().length];
    private static long linesTick = Long.MIN_VALUE;

    private static void buildLines(Minecraft mc, long now) {
        ArtData data = AbyssArts.data(mc.player);
        for (AbyssArt a : AbyssArt.values()) {
            int rank = data.rank(a);
            String keyName = KEYS[a.ordinal()].getTranslatedKeyMessage().getString();
            Component name = Component.translatable(a.nameKey());
            Component line;
            int color;
            if (rank == 0) {
                line = Component.translatable("hud.skyabyss.art.none", keyName, name);
                color = 0x808080;
            } else {
                long left = Math.max(0, UNTIL[a.ordinal()] - now);
                line = left > 0
                        ? Component.translatable("hud.skyabyss.art.wait", keyName, name, rank, String.format("%.1f", left / 20.0))
                        : Component.translatable("hud.skyabyss.art.ready", keyName, name, rank);
                color = left > 0 ? 0xB8B8B8 : 0xE6F4FF;
            }
            LINES[a.ordinal()] = line;
            COLORS[a.ordinal()] = color;
        }
        linesTick = now;
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui || mc.level.dimension() != AbyssWorld.LEVEL) {
            return;
        }
        Font font = mc.font;
        long now = mc.level.getGameTime();
        if (now != linesTick) {
            buildLines(mc, now);
        }
        int x = 4;
        int y = g.guiHeight() / 2 - 24;
        for (AbyssArt a : AbyssArt.values()) {
            int row = y + a.ordinal() * 20;
            // 絵 (32px の絵を半分の大きさで出す)
            g.pose().pushPose();
            g.pose().scale(0.5f, 0.5f, 1f);
            g.blit(ICONS[a.ordinal()], x * 2, row * 2, 0, 0, 32, 32, 32, 32);
            g.pose().popPose();
            Component line = LINES[a.ordinal()];
            int color = COLORS[a.ordinal()];
            g.drawString(font, line, x + 20, row + 4, color, true);
        }
    }
}
