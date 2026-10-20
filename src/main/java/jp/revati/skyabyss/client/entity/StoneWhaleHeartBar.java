package jp.revati.skyabyss.client.entity;

import java.util.Comparator;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.StoneWhaleHeart;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 岩鯨の心臓のボスバー。バニラの横棒の代わりに、段階ごとの枠と色で描く。
// 近くにいる、名前の同じ心臓から段階と咆哮の有無を読む
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StoneWhaleHeartBar {
    // 枠の大きさ (テクスチャと同じ)。段の高さは文字の行と枠の行
    private static final int WIDTH = 182;
    private static final int FRAME_HEIGHT = 16;
    private static final int ICON = 16;
    // 枠の中の目盛り。帯の中の白い区間は x 3 から 178 まで (175 px)
    private static final int TRACK_X = 3;
    private static final int TRACK_W = 175;
    private static final int TRACK_Y = 3;
    private static final int TRACK_H = 6;

    private static final int[] FILL = {0x5FA8B5, 0xE0802B, 0xE0301E};
    private static final int[] FILL_TOP = {0x8FD0DC, 0xFFB35A, 0xFF6A4D};
    private static final int[] LABEL = {0x9CC6D0, 0xFFB347, 0xFF6A5A};

    private StoneWhaleHeartBar() {
    }

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        StoneWhaleHeart heart = findHeart(mc, boss);
        if (heart == null) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, heart, boss, event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        // バニラの横棒より高い段を空ける
        event.setIncrement(event.getIncrement() + 12);
    }

    // ボスバーの名前は心臓の名前と同じ。近くにいる、その名の心臓を選ぶ (試練の心臓は一つだけ)
    private static StoneWhaleHeart findHeart(Minecraft mc, BossEvent boss) {
        if (mc.player == null) {
            return null;
        }
        return jp.revati.skyabyss.client.BossOwners.find(mc, boss, StoneWhaleHeart.class, 160);
    }

    private static void draw(GuiGraphics g, Minecraft mc, StoneWhaleHeart heart, BossEvent boss, int x, int y) {
        int phase = Math.min(StoneWhaleHeart.PHASE_COUNT - 1, Math.max(0, heart.getPhase()));
        int n = phase + 1;

        // 一段目: 顔の印、名前、段階の名前 (咆哮中なら咆哮と出る)、右に割合
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/stone_whale_heart_icon_p" + n + ".png"),
                x, y, 0, 0, ICON, ICON, ICON, ICON);
        Component name = heart.getDisplayName();
        g.drawString(mc.font, name, x + ICON + 4, y + 4, 0xFFFFFF, true);
        int labelX = x + ICON + 4 + mc.font.width(name) + 6;
        Component label = heart.isRoaring()
                ? Component.translatable("gui.skyabyss.boss.stone_whale_heart.roaring")
                : Component.translatable("gui.skyabyss.boss.stone_whale_heart.phase." + n);
        g.drawString(mc.font, label, labelX, y + 4, heart.isRoaring() ? 0xFF5A5A : LABEL[phase], true);
        String percent = Math.round(boss.getProgress() * 100) + "%";
        g.drawString(mc.font, percent, x + WIDTH - mc.font.width(percent), y + 4, 0xFFFFFF, true);

        // 二段目: 枠、そのなかの帯、目盛り
        int barY = y + ICON + 2;
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/stone_whale_heart_frame_p" + n + ".png"),
                x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, boss.getProgress()));
        int fill = Math.round(TRACK_W * progress);
        if (fill > 0) {
            int left = x + TRACK_X;
            int top = barY + TRACK_Y;
            g.fill(left, top, left + fill, top + TRACK_H, 0xFF000000 | FILL[phase]);
            g.fill(left, top, left + fill, top + 1, 0xFF000000 | FILL_TOP[phase]);
        }
    }
}
