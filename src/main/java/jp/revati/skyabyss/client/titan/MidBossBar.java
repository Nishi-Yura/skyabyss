package jp.revati.skyabyss.client.titan;

import java.util.Comparator;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.entity.ElderSentry;
import jp.revati.skyabyss.entity.WhaleboneCaptain;
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
    private static final Look BECKONER = new Look("tide_beckoner", 0x3FB4AE, 0x86E4DA);

    private MidBossBar() {
    }

    private static <T extends LivingEntity> boolean near(Minecraft mc, Class<T> type, net.minecraft.world.BossEvent boss) {
        return jp.revati.skyabyss.client.BossOwners.find(mc, boss, type, 96) != null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.isCanceled()) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        Look look = near(mc, WhaleboneCaptain.class, boss) ? CAPTAIN : near(mc, ElderSentry.class, boss) ? ELDER : near(mc, TideBeckoner.class, boss) ? BECKONER : null;
        if (look == null && jp.revati.skyabyss.client.hud.HudDemo.has(jp.revati.skyabyss.client.hud.HudDemo.Part.MIDBOSS)) {
            look = CAPTAIN;
        }
        if (look == null) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, look, boss, event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        event.setIncrement(event.getIncrement() + EXTRA);
    }

    private static void draw(GuiGraphics g, Minecraft mc, Look look, BossEvent boss, int x, int y) {
        g.blit(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + look.icon() + "_icon.png"), x, y, 0, 0, ICON, ICON, ICON, ICON);
        g.drawString(mc.font, boss.getName(), x + ICON + 4, y + 4, 0xFFFFFF, true);
        int by = y + ICON + 2;
        // HUD の帯 (Plate.bar。docs/design/56 の 5): 銅の縁の溝に、中ボスごとの色の塗り。減った分は明るい影で残って遅れて縮む。十分の一ごとに目盛り
        float p = Math.max(0f, Math.min(1f, boss.getProgress()));
        long now = net.minecraft.Util.getMillis();
        if (!boss.getId().equals(ghostOf) || p >= ghost) {
            ghostOf = boss.getId();
            ghost = p;
            ghostHold = now + 300;
        } else if (now > ghostHold) {
            ghost = Math.max(p, ghost - 0.7f * Math.min(0.1f, (now - ghostAt) / 1000f));
        }
        ghostAt = now;
        float gh = ghost;
        g.drawManaged(() -> {
            jp.revati.skyabyss.client.ui.Plate.bar(g, x, by, WIDTH, BAR_H, p, gh, look.fill(), 0, 1f);
            jp.revati.skyabyss.client.ui.Plate.barTicks(g, x, by, WIDTH, BAR_H, 10, 1f);
        });
        jp.revati.skyabyss.client.titan.PhasedBossBar.drawHp(g, mc, boss, x, by + BAR_H / 2);
    }

    // 減った分の影 (帯ごと。遅れて縮む)
    private static java.util.UUID ghostOf;
    private static float ghost;
    private static long ghostHold;
    private static long ghostAt;
}
