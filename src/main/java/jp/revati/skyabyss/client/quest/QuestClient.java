package jp.revati.skyabyss.client.quest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestUx;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

// クエストのクライアント側。定義の一覧を持ち、U でログを開き、始まった・進んだ・終わったの札を出す。
// 記録 (QuestLog) は Data Attachment の同期で mc.player に届いている
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class QuestClient {
    public static final KeyMapping LOG_KEY = new KeyMapping("key.skyabyss.quest_log", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, jp.revati.skyabyss.KeyDefaults.QUEST_LOG, "key.categories.skyabyss");

    private static Map<String, QuestDef> catalog = Map.of();
    private static List<QuestDef> ordered = List.of();

    private QuestClient() {
    }

    // Mod のイベントバスの登録は別のクラスでもよいが、ここに置いても NeoForge が振り分ける
    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(LOG_KEY);
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "quest_tracker"), QuestHud::render);
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "objective_strip"), ObjectiveStrip::render);
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "lamp_band"), jp.revati.skyabyss.client.titan.LampBandClient::render);
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "chapter_label"), ChapterLabel::render);
        }
    }

    public static void receiveCatalog(List<QuestDef> quests) {
        Map<String, QuestDef> map = new LinkedHashMap<>();
        quests.forEach(q -> map.put(q.id(), q));
        catalog = map;
        ordered = List.copyOf(quests);
    }

    public static void receiveNotice(byte kind, String questId) {
        Minecraft mc = Minecraft.getInstance();
        QuestDef def = catalog.get(questId);
        Component title = def == null ? Component.literal(questId) : Component.translatable(def.titleKey());
        String head = switch (kind) {
            case QuestNetwork.Notice.STARTED -> "gui.skyabyss.quest.toast.started";
            case QuestNetwork.Notice.FINISHED -> "gui.skyabyss.quest.toast.finished";
            default -> "gui.skyabyss.quest.toast.updated";
        };
        // 会話・場面の最中は溜め、閉じたあとに一枚ずつ (first_hour_log B-2)
        jp.revati.skyabyss.client.notice.NoticeClient.gate(jp.revati.skyabyss.notice.NoticeGate.P_CARD, jp.revati.skyabyss.client.notice.NoticeClient.CARD_GAP, () -> {
            boolean main = def != null && def.main();
            jp.revati.skyabyss.client.notice.CardFeed.show(Component.translatable(head), title, main ? 0xF2C14E : 0x7FD6D0,
                    new net.minecraft.world.item.ItemStack(main ? net.minecraft.world.item.Items.WRITABLE_BOOK : net.minecraft.world.item.Items.PAPER),
                    () -> new QuestToast(Component.translatable(head), title, main));
            playSound(mc, kind, def != null && def.main());
            QuestHud.flash();
        });
        if (def != null && def.main() && kind != QuestNetwork.Notice.FINISHED) {
            // 段の書き出しの一言は、ログが届いてから (始まった知らせはログの同期より先に来る)
            pendingLead.put(questId, LEAD_DELAY);
        }
    }

    // ---------------------------------------------------------------- 段の書き出しの一言 (2026-10-22 の試遊: なぜ今これをするのか分からなかった)
    // メインのクエストが始まった・次の段へ進んだとき、その段に「<段の鍵>.lead」の文があれば、字幕の列に一度だけ出す。
    // 誰に頼まれ、何のためにするのかを短く言う (例: イサナ「鎖は素手では上れない。鉄を着ろ」)。先のことは言わない。
    // 文がない段は何も出さない。同じ段は、この遊びの間に一度だけ
    private static final int LEAD_DELAY = 12;
    private static final Map<String, Integer> pendingLead = new LinkedHashMap<>();
    private static final java.util.Set<String> leadShown = new java.util.HashSet<>();

    // その段の書き出しの文の鍵 (文がなければ null)
    @Nullable
    public static String leadKey(QuestDef def, int stage) {
        if (stage < 0 || stage >= def.stages().size()) {
            return null;
        }
        String key = QuestDef.keyBase(def.id()) + "." + def.stages().get(stage).id() + ".lead";
        return net.minecraft.locale.Language.getInstance().has(key) ? key : null;
    }

    private static void tickLeads(Minecraft mc) {
        if (pendingLead.isEmpty()) {
            return;
        }
        var it = pendingLead.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            if (e.getValue() > 0) {
                e.setValue(e.getValue() - 1);
                continue;
            }
            it.remove();
            QuestDef def = catalog.get(e.getKey());
            jp.revati.skyabyss.quest.QuestLog.Entry entry = jp.revati.skyabyss.quest.QuestLog.of(mc.player).entry(e.getKey());
            if (def == null || entry == null || entry.done) {
                continue;
            }
            String key = leadKey(def, entry.stage);
            if (key != null && leadShown.add(key)) {
                jp.revati.skyabyss.client.notice.NoticeClient.receive(new jp.revati.skyabyss.notice.NoticePayload(
                        jp.revati.skyabyss.notice.Notice.Kind.NARRATE, jp.revati.skyabyss.ModText.lore(key), Component.empty()));
            }
        }
    }

    // 更新の音 (設定 questSound。メインは小さな鐘が二つ、サブはもっと柔らかいひとつ。終わったときは少し低く)
    static void playSound(Minecraft mc, byte kind, boolean main) {
        if (!Config.QUEST_SOUND.get()) {
            return;
        }
        float pitch = kind == QuestNetwork.Notice.FINISHED ? 0.85f : 1.0f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(main ? QuestUx.MAIN_SOUND.get() : QuestUx.SIDE_SOUND.get(), pitch, 1.0f));
    }

    @Nullable
    public static QuestDef quest(String id) {
        return catalog.get(id);
    }

    public static List<QuestDef> all() {
        return ordered;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (LOG_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new QuestLogScreen());
            }
        }
        QuestHud.tick();
        tickLeads(mc);
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        catalog = Map.of();
        ordered = List.of();
        pendingLead.clear();
        leadShown.clear();
    }
}
