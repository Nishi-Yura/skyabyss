package jp.revati.skyabyss.story.ch5.client;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.titan.PhasedBossBar;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 深淵の主たちの段のあるボスバー (サーバーの story/ch5/AbyssBossBars)。名前の翻訳キーで型を決め、色で段、刻みで咆哮を読む。
//   層の主 4 (abyss_v2.skyabyss.lord.<層>)・縄張りの主 2 (abyss_v2.skyabyss.elite.<縄張り>)・歪んだ王 (message.skyabyss.depths.king.*)・心臓の番人
//   絵は tools/abyss/gen_abyss_boss_bars.py (textures/gui/boss/<型>_icon_pN・_frame_pN)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssBossBarClient {
    static PhasedBossBar.Style style(String prefix, int[] fill, int[] top, int[] label) {
        return new PhasedBossBar.Style(prefix, fill, top, label, "gui.skyabyss.boss.abyss.phase.", "gui.skyabyss.boss.abyss.roaring", 0xFF6A5A);
    }

    // 骨 = 魂の青白 → 骨の白 → 魂の火の青 / 塩 = 潮の青緑 → 塩の白 → 深い藍 / 雷 = 銅 → 稲妻の黄 → 白金 / 灰 = 熾火の橙 → 赤 → 白熱
    static final Map<String, PhasedBossBar.Style> STYLES = Map.of(
            "abyss_v2.skyabyss.lord.bone", style("abyss_lord_bone", new int[] {0x8FB8D8, 0xE6E0D0, 0x5AD8FF}, new int[] {0xC8E4F8, 0xFFFFF4, 0xB8F4FF},
                    new int[] {0xA8CCE8, 0xF0ECE0, 0x7AE0FF}),
            "abyss_v2.skyabyss.lord.salt", style("abyss_lord_salt", new int[] {0x4FB8B0, 0xEEF2F0, 0x3E6CC8}, new int[] {0x9CE8E0, 0xFFFFFF, 0x88AEF0},
                    new int[] {0x7AD8D0, 0xF4F8F6, 0x7C9CE8}),
            "abyss_v2.skyabyss.lord.storm", style("abyss_lord_storm", new int[] {0xC88A50, 0xF0D040, 0xF6F0C8}, new int[] {0xF0B880, 0xFFF0A0, 0xFFFFF0},
                    new int[] {0xE8A870, 0xFFE070, 0xFFF4C0}),
            "abyss_v2.skyabyss.lord.ash", style("abyss_lord_ash", new int[] {0xE07830, 0xD03828, 0xFFE0A0}, new int[] {0xFFB070, 0xFF7860, 0xFFFFE0},
                    new int[] {0xF09050, 0xF06050, 0xFFE8B0}),
            "abyss_v2.skyabyss.elite.terr_salt", style("abyss_elite", new int[] {0xB0A8C8, 0xC8A0D8, 0xE0D0F0}, new int[] {0xDCD4F0, 0xE8CCF4, 0xF8F0FF},
                    new int[] {0xC8C0E0, 0xD8B8E8, 0xF0E8FF}),
            "abyss_v2.skyabyss.elite.terr_ash", style("abyss_elite", new int[] {0xB0A8C8, 0xC8A0D8, 0xE0D0F0}, new int[] {0xDCD4F0, 0xE8CCF4, 0xF8F0FF},
                    new int[] {0xC8C0E0, 0xD8B8E8, 0xF0E8FF}),
            "entity.skyabyss.heart_warden", style("abyss_warden", new int[] {0x6A8CA0, 0x8A60C0, 0xE04060}, new int[] {0xA0C4D8, 0xC090F0, 0xFF8098},
                    new int[] {0x90B0C8, 0xB088E0, 0xFF7088}));
    static final PhasedBossBar.Style KING = style("abyss_king", new int[] {0x9C7CC0, 0xC060A0, 0xF0E0F0}, new int[] {0xD0B0F0, 0xF090D0, 0xFFFFFF},
            new int[] {0xC0A0E0, 0xE080C0, 0xFFF0FF});

    private AbyssBossBarClient() {
    }

    @Nullable
    static PhasedBossBar.Style styleOf(BossEvent boss) {
        if (!(boss.getName().getContents() instanceof TranslatableContents t)) {
            return null;
        }
        String key = t.getKey();
        PhasedBossBar.Style s = STYLES.get(key);
        if (s != null) {
            return s;
        }
        return key.startsWith("message.skyabyss.depths.king.") ? KING : null;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.isCanceled() || !mc.level.dimension().location().getPath().equals("abyss")) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        PhasedBossBar.Style s = styleOf(boss);
        if (s == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, s, boss.getName(), AbyssBossBars.phaseOf(boss), AbyssBossBars.roaring(boss), boss,
                event.getX(), event.getY() - jp.revati.skyabyss.client.titan.BossBarShift.UP);
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
