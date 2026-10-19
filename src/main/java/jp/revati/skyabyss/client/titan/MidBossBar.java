package jp.revati.skyabyss.client.titan;

import java.util.Comparator;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.ElderSentry;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
import jp.revati.skyabyss.titan.tide.back.PearlKeeper;
import jp.revati.skyabyss.titan.tide.back.TideBeckoner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 中ボス 4 体 (鯨骨の隊長・古い見張り・真珠の番人・潮呼び) のボスバー。ほかのボス (四つの心臓・雷獣・灰の鍛冶) と同じく、
// 名前の左に 16px のドット絵の印を付ける (docs/design/18 の 14.7)。名前と同じ体が近くにいるときだけ描き換える (ほかはバニラのまま)。
// 帯は暗い石板 + 青緑の縁 + ボスごとの色。高さはほかのボスバーより一段高いので、増分を足して下の部品を押し下げる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MidBossBar {
    public static final int WIDTH = 182;
    private static final int ICON = 16;
    private static final int BAR_H = 9;
    // 印の行 (16) + すき間 (2) + 帯 (9) = 27。バニラの増分 19 との差
    private static final int EXTRA = 9;

    private record Look(String icon, int fill, int top) {
    }

    private static final Look CAPTAIN = new Look("captain", 0x5C8FD0, 0x8CBBF0);
    private static final Look ELDER = new Look("elder_sentry", 0xA6AEC0, 0xD8DEEC);
    private static final Look PEARL = new Look("pearl_keeper", 0xD89AAE, 0xF6CEDA);
    private static final Look BECKONER = new Look("tide_beckoner", 0x3FB4AE, 0x86E4DA);

    private MidBossBar() {
    }

    private static <T extends LivingEntity> boolean near(Minecraft mc, Class<T> type, String name) {
        return !mc.level.getEntitiesOfClass(type, mc.player.getBoundingBox().inflate(96), e -> e.isAlive() && e.getDisplayName().getString().equals(name))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).isEmpty();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.isCanceled()) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        Look look = near(mc, WhaleboneCaptain.class, name) ? CAPTAIN : near(mc, ElderSentry.class, name) ? ELDER
                : near(mc, PearlKeeper.class, name) ? PEARL : near(mc, TideBeckoner.class, name) ? BECKONER : null;
        if (look == null) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, look, boss, event.getX(), event.getY());
        event.setIncrement(event.getIncrement() + EXTRA);
    }

    private static void draw(GuiGraphics g, Minecraft mc, Look look, BossEvent boss, int x, int y) {
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + look.icon() + "_icon.png"), x, y, 0, 0, ICON, ICON, ICON, ICON);
        g.drawString(mc.font, boss.getName(), x + ICON + 4, y + 4, 0xFFFFFF, true);
        String percent = Math.round(boss.getProgress() * 100) + "%";
        g.drawString(mc.font, percent, x + WIDTH - mc.font.width(percent), y + 4, 0xFFFFFF, true);
        int by = y + ICON + 2;
        // 板: 青緑の縁 + 暗い石 + 帯
        g.fill(x, by, x + WIDTH, by + BAR_H, 0xFF2E5552);
        g.fill(x + 1, by + 1, x + WIDTH - 1, by + BAR_H - 1, 0xFF0A0C10);
        float p = Math.max(0f, Math.min(1f, boss.getProgress()));
        int track = WIDTH - 4;
        int fill = Math.round(track * p);
        if (fill > 0) {
            g.fill(x + 2, by + 2, x + 2 + fill, by + BAR_H - 2, 0xFF000000 | look.fill());
            g.fill(x + 2, by + 2, x + 2 + fill, by + 3, 0xFF000000 | look.top());
        }
        // 目盛り: 十分の一ごとに細い線
        for (int i = 1; i < 10; i++) {
            int tx = x + 2 + track * i / 10;
            g.fill(tx, by + 1, tx + 1, by + BAR_H - 1, 0x66000000);
        }
    }
}
