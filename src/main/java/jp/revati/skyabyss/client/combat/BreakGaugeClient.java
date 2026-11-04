package jp.revati.skyabyss.client.combat;

import java.util.HashMap;
import java.util.Map;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.combat.BreakGauge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 崩しゲージの帯 (combat/BreakGauge。docs/design/47 の 2.1)。ボスバーの下、次のバーの名前の上に細い帯を一本。
//   溜まり: 琥珀色。PULSE を超えると金に脈打つ。崩れ: 白金の帯が残りの時間で縮む。崩れた瞬間は FLASH_MS のあいだ白く光る
//   どのバーの下か: バーの名前と同じ名の体 (client/BossOwners と同じ見分け)。名前が合わなければ、体の残りの体力とバーの長さが合うもの
//   ほかの描き換え (BossBarLooks・MidBossBar など) のあとに、最後の増分を見て描く (取り消されたバーにも描く)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BreakGaugeClient {
    static final int WIDTH = 182;
    static final int HEIGHT = 2;
    static final float PULSE = 0.85f;
    static final long FLASH_MS = 450;
    // これだけ知らせが来なければ帯を消す (サーバーは 5 tick ごと)
    static final long STALE_MS = 3000;

    private record Shown(float fill, float broken, long flashAt, long at) {
    }

    private static final Map<Integer, Shown> SHOWN = new HashMap<>();

    private BreakGaugeClient() {
    }

    public static void receive(BreakGauge.Payload p) {
        long now = System.currentTimeMillis();
        if (p.fill() < 0) {
            SHOWN.remove(p.entityId());
            return;
        }
        Shown was = SHOWN.get(p.entityId());
        long flashAt = p.flash() ? now : was == null ? 0 : was.flashAt();
        SHOWN.put(p.entityId(), new Shown(p.fill(), p.broken(), flashAt, now));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || SHOWN.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        SHOWN.values().removeIf(s -> now - s.at() > STALE_MS);
        Shown s = match(mc, event.getBossEvent());
        if (s == null) {
            return;
        }
        // 次のバーの名前 (増分 - 9) のすぐ上
        draw(event.getGuiGraphics(), event.getX(), event.getY() + event.getIncrement() - 9 - HEIGHT - 2, s, now);
    }

    static Shown match(Minecraft mc, BossEvent bar) {
        String name = bar.getName().getString();
        Shown byLength = null;
        for (Map.Entry<Integer, Shown> e : SHOWN.entrySet()) {
            Entity en = mc.level.getEntity(e.getKey());
            if (!(en instanceof LivingEntity le)) {
                continue;
            }
            if (le.getDisplayName().getString().equals(name)) {
                return e.getValue();
            }
            if (byLength == null && le.getMaxHealth() > 0 && Math.abs(le.getHealth() / le.getMaxHealth() - bar.getProgress()) < 0.015f) {
                byLength = e.getValue();
            }
        }
        return byLength;
    }

    static void draw(GuiGraphics g, int x, int y, Shown s, long now) {
        g.fill(x - 1, y - 1, x + WIDTH + 1, y + HEIGHT + 1, 0xB0000000);
        if (s.broken() > 0) {
            int w = Math.round(WIDTH * Mth.clamp(s.broken(), 0f, 1f));
            g.fill(x, y, x + w, y + HEIGHT, 0xFFFFE9A8);
        } else if (s.fill() > 0) {
            int w = Math.max(1, Math.round(WIDTH * Mth.clamp(s.fill(), 0f, 1f)));
            int color = 0xFFC9822A;
            if (s.fill() >= PULSE) {
                float t = 0.5f + 0.5f * Mth.sin(now / 90f);
                color = lerp(0xFFD9962E, 0xFFFFE070, t);
            }
            g.fill(x, y, x + w, y + HEIGHT, color);
        }
        long since = now - s.flashAt();
        if (s.flashAt() > 0 && since < FLASH_MS) {
            int a = Math.round(255 * (1f - since / (float) FLASH_MS));
            g.fill(x - 1, y - 2, x + WIDTH + 1, y + HEIGHT + 2, (a << 24) | 0xFFFFFF);
        }
    }

    static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int gr = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = Math.round((a & 255) + ((b & 255) - (a & 255)) * t);
        return 0xFF000000 | (r << 16) | (gr << 8) | bl;
    }
}
