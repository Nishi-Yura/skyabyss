package jp.revati.skyabyss.client.titan;

import java.util.Map;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.ch5.AbyssBossBars;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

// 残りのボスの帯 (月・雲海・塔・雷の章・熾の章・潮の番人・淵の杭・名のある強敵) を PhasedBossBar の型で描く。
// 型は名前の翻訳キーで決める。段のあるボス (月・雲海) は段を色で読む (AbyssBossBars.phase/roar をサーバーが送る)。
// 名のある強敵の「大いなる〜」(WorldEvents) は、中の名前の土地の型を使う。絵は tools/gen/ui/gen_boss_bars_more.py。
// 深淵の主たちとその他 (心臓・中ボス) は、それぞれの帯のクラスが描く (ここでは取らない)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class BossBarLooks {
    private static final String PHASE = "gui.skyabyss.boss.abyss.phase.";
    private static final String ROARING = "gui.skyabyss.boss.abyss.roaring";
    private static final String WORLD_EVENT = "entity.skyabyss.world_event.name";

    private static PhasedBossBar.Style phased(String icon, String frame, int[] fill, int[] top, int[] label, int roaringColor) {
        return new PhasedBossBar.Style(icon, frame, fill, top, label, PHASE, ROARING, roaringColor);
    }

    private static PhasedBossBar.Style solo(String icon, String frame, int fill, int top, int label) {
        return new PhasedBossBar.Style(icon, frame, new int[] {fill}, new int[] {top}, new int[] {label}, null, null, label);
    }

    // 月の族: 白銀と青 (段 3 つ)
    private static final int[] MOON_FILL = {0x8FA8C8, 0xB8CCE6, 0x7FB0E8};
    private static final int[] MOON_TOP = {0xD8E6F8, 0xEAF2FF, 0xC0DCFF};
    private static final int[] MOON_LABEL = {0xB0C4DC, 0xD0DEF0, 0x9CC8F4};
    // 雲海の族: 白と金 (段 3 つ)
    private static final int[] CLOUD_FILL = {0xD8CCA0, 0xF0DC80, 0xFFC830};
    private static final int[] CLOUD_TOP = {0xFFFFFF, 0xFFF8D8, 0xFFF0A0};
    private static final int[] CLOUD_LABEL = {0xF0E4B8, 0xFFE68A, 0xFFD040};

    static final Map<String, PhasedBossBar.Style> STYLES = Map.ofEntries(
            // 月の族 (段 3)
            Map.entry("entity.skyabyss.full_keeper", phased("full_keeper", "moon", MOON_FILL, MOON_TOP, MOON_LABEL, 0xD8E8FF)),
            Map.entry("entity.skyabyss.moon_core", phased("moon_core", "moon", MOON_FILL, MOON_TOP, MOON_LABEL, 0xD8E8FF)),
            Map.entry("entity.skyabyss.moon_scale", phased("moon_scale", "moon", MOON_FILL, MOON_TOP, MOON_LABEL, 0xD8E8FF)),
            Map.entry("entity.skyabyss.shadow_embracer", phased("shadow_embracer", "moon", MOON_FILL, MOON_TOP, MOON_LABEL, 0xD8E8FF)),
            Map.entry("entity.skyabyss.waning_blade", phased("waning_blade", "moon", MOON_FILL, MOON_TOP, MOON_LABEL, 0xD8E8FF)),
            // 雲海の族 (段 3)
            Map.entry("entity.skyabyss.lamp_eye", phased("lamp_eye", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.last_bellkeeper", phased("last_bellkeeper", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.pilgrim_eye", phased("pilgrim_eye", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.pilgrim_moon", phased("pilgrim_moon", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.pilgrim_plume", phased("pilgrim_plume", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.rewinder", phased("rewinder", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            Map.entry("entity.skyabyss.founder", phased("founder", "cloud", CLOUD_FILL, CLOUD_TOP, CLOUD_LABEL, 0xFFF0A0)),
            // 段のない雲海の一つ (手・淵の杭)
            Map.entry("entity.skyabyss.pilgrim_hand", solo("pilgrim_hand", "cloud_solo", 0xD8CCA0, 0xFFFFFF, 0xF0E4B8)),
            Map.entry("entity.skyabyss.circuit_stake", solo("circuit_stake", "cloud_solo", 0x9AB0CC, 0xD0E4FF, 0xC8D8EE)),
            // 段のない: 塔・雷の章・熾の章・潮の番人
            Map.entry("entity.skyabyss.tower_master", solo("tower_master", "tower_solo", 0xE8D27A, 0xFFF8D8, 0xE8D27A)),
            Map.entry("entity.skyabyss.scorched_warden", solo("scorched_warden", "storm_solo", 0xC8803C, 0xF0B060, 0xE8A058)),
            // 避雷の塔の跡の小ダンジョンの主 (2026-11-08 の声「風守のボスバーない」: 細いバニラの帯だけで、見えていなかった)
            Map.entry("entity.skyabyss.feather_warden", solo("named_ochre", "storm_solo", 0x6FA8D8, 0xD8ECFF, 0xA8CCEE)),
            Map.entry("entity.skyabyss.elder_thrall", solo("elder_thrall", "ember_solo", 0xD85A2A, 0xFF9A60, 0xF08048)),
            Map.entry("entity.skyabyss.shore_warden", solo("shore_warden", "shore_solo", 0x6CC8C0, 0xD8FFF8, 0x9CE8E0)),
            // 名のある強敵 (土地ごとの土の色)
            Map.entry("entity.skyabyss.named.meadow", solo("named_meadow", "named_meadow", 0x8E9E4C, 0xC8D878, 0xB0C06A)),
            Map.entry("entity.skyabyss.rock_boar", solo("named_stone", "named_stone", 0x9A9080, 0xD8D0C0, 0xC0B8A8)),
            Map.entry("entity.skyabyss.tide_crab", solo("named_tide", "named_tide", 0x4E9A94, 0x9CE0D4, 0x84CCC2)),
            Map.entry("entity.skyabyss.storm_ram", solo("named_ochre", "named_ochre", 0xB08A3C, 0xE8C472, 0xD8AC5C)),
            Map.entry("entity.skyabyss.ash_lizard", solo("named_ash", "named_ash", 0x8E4E34, 0xCC8054, 0xC0744C)),
            // 地下のダンジョンの主と中ボス (docs/design/55)
            Map.entry("entity.skyabyss.bone_centipede", solo("named_stone", "named_stone", 0xCFC6AE, 0xF4EEDC, 0xE0D8C2)),
            Map.entry("entity.skyabyss.shell_octopus", solo("named_tide", "named_tide", 0xC88C78, 0xF4D2C0, 0xE0B4A0)),
            Map.entry("entity.skyabyss.crystal_lizard", solo("named_ochre", "named_ochre", 0x7CB8F0, 0xD8F0FF, 0xA8D4FA)),
            Map.entry("entity.skyabyss.forge_giant", solo("named_ash", "named_ash", 0xE07830, 0xFFC080, 0xF09850)),
            Map.entry("entity.skyabyss.dungeon_mid", solo("named_stone", "named_stone", 0x9A9080, 0xD8D0C0, 0xC0B8A8)));

    private BossBarLooks() {
    }

    @Nullable
    static PhasedBossBar.Style styleOf(BossEvent boss) {
        String key = entityKey(boss.getName(), 0);
        return key == null ? null : STYLES.get(key);
    }

    // 帯の名前の中から、Mod の生き物の名前のキーを探す。名前は「封じ 2/3」「荒ぶる〜」(WorldEventFight)、「深淵の〜」(HeartBoss)、
    // チームの色付け (Component.empty() + 名前) などで包まれることがあり、外側のキーだけ見ると型が外れて、ときどきバニラの帯に戻っていた
    // (2026-11-08 の声「ボスバーがたまに既存デザインになる」)。包みの引数と子をたどって、いちばん内側の entity.skyabyss.* を返す
    @Nullable
    public static String entityKey(Component c, int depth) {
        if (c == null || depth > 6) {
            return null;
        }
        if (c.getContents() instanceof TranslatableContents t) {
            for (Object a : t.getArgs()) {
                if (a instanceof Component inner) {
                    String k = entityKey(inner, depth + 1);
                    if (k != null) {
                        return k;
                    }
                }
            }
            if (t.getKey().startsWith("entity.skyabyss.") && !t.getKey().equals(WORLD_EVENT)) {
                return t.getKey();
            }
        }
        for (Component sib : c.getSiblings()) {
            String k = entityKey(sib, depth + 1);
            if (k != null) {
                return k;
            }
        }
        return null;
    }

    // 型を持たない Mod の主・中ボス (地下のダンジョンの中ボスなど) は、石の色の型で出す (細いバニラの帯にしない)。
    // ほかの担当 (心臓・船長・番人など) が描いた帯には触れないよう、いちばん後に見る
    static final PhasedBossBar.Style DEFAULT = solo("named_stone", "named_stone", 0x9A9080, 0xD8D0C0, 0xC0B8A8);

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onPlainBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        BossEvent boss = event.getBossEvent();
        if (mc.level == null || mc.player == null || event.isCanceled() || entityKey(boss.getName(), 0) == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, DEFAULT, boss.getName(), AbyssBossBars.phaseOf(boss), AbyssBossBars.roaring(boss), boss,
                event.getX(), event.getY() - BossBarShift.UP);
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || event.isCanceled()) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        PhasedBossBar.Style s = styleOf(boss);
        if (s == null) {
            return;
        }
        event.setCanceled(true);
        PhasedBossBar.draw(event.getGuiGraphics(), mc, s, boss.getName(), AbyssBossBars.phaseOf(boss), AbyssBossBars.roaring(boss), boss,
                event.getX(), event.getY() - BossBarShift.UP);
        event.setIncrement(event.getIncrement() + PhasedBossBar.EXTRA_HEIGHT);
    }
}
