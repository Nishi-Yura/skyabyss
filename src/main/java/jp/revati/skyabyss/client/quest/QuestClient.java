package jp.revati.skyabyss.client.quest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.quest.QuestDef;
import jp.revati.skyabyss.quest.QuestNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_U, "key.categories.skyabyss");

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
        mc.getToasts().addToast(new QuestToast(Component.translatable(head), title, def != null && def.main()));
        float pitch = kind == QuestNetwork.Notice.FINISHED ? 0.9f : kind == QuestNetwork.Notice.STARTED ? 0.7f : 1.2f;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.7f));
        QuestHud.flash();
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
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        catalog = Map.of();
        ordered = List.of();
    }
}
