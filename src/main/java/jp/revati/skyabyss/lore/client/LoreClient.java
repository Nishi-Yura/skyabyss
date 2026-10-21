package jp.revati.skyabyss.lore.client;

import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.lore.LoreCatalog;
import jp.revati.skyabyss.lore.LoreEntry;
import jp.revati.skyabyss.lore.LoreNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 伝承の書のクライアント側。サーバーから受け取った章・項目・記録を持ち、書を開き、記されたときに知らせる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class LoreClient {
    private static LoreCatalog catalog = LoreCatalog.EMPTY;
    private static final Set<String> UNLOCKED = new LinkedHashSet<>();
    private static final Set<String> SEEN = new LinkedHashSet<>();
    // 最後に開いていた項目 (書を開き直したとき、そこから読む)
    @Nullable
    static String lastSelected;

    private LoreClient() {
    }

    public static void receiveSync(LoreNetwork.Sync sync) {
        catalog = sync.catalog();
        UNLOCKED.clear();
        UNLOCKED.addAll(sync.unlocked());
        SEEN.clear();
        SEEN.addAll(sync.seen());
        refreshScreen();
    }

    public static void receiveUnlocked(String id) {
        if (!UNLOCKED.add(id)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LoreEntry entry = catalog.entry(id);
        Component title = entry != null ? Component.translatable(entry.titleKey()) : Component.literal("？？？");
        refreshScreen();
        // 会話・場面の最中は溜め、閉じたあとに一枚ずつ (first_hour_log B-2)
        jp.revati.skyabyss.client.notice.NoticeClient.gate(jp.revati.skyabyss.notice.NoticeGate.P_CARD, jp.revati.skyabyss.client.notice.NoticeClient.CARD_GAP, () -> {
            jp.revati.skyabyss.client.notice.CardFeed.show(Component.translatable("gui.skyabyss.card.lore"), title, 0x9FC7E8, () -> new LoreToast(title));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8f, 0.6f));
        });
    }

    public static boolean has(String id) {
        return UNLOCKED.contains(id);
    }

    public static void openBook() {
        Minecraft.getInstance().setScreen(new LoreBookScreen());
    }

    static LoreCatalog catalog() {
        return catalog;
    }

    static boolean unlocked(String id) {
        return UNLOCKED.contains(id);
    }

    static boolean isNew(String id) {
        return UNLOCKED.contains(id) && !SEEN.contains(id);
    }

    // 最も新しく記され、まだ読んでいない項目
    @Nullable
    static String newestUnread() {
        String newest = null;
        for (String id : UNLOCKED) {
            if (!SEEN.contains(id) && catalog.entry(id) != null) {
                newest = id;
            }
        }
        return newest;
    }

    static void markSeen(String id) {
        if (UNLOCKED.contains(id) && SEEN.add(id)) {
            PacketDistributor.sendToServer(new LoreNetwork.Seen(id));
        }
    }

    private static void refreshScreen() {
        if (Minecraft.getInstance().screen instanceof LoreBookScreen screen) {
            screen.refresh();
        }
    }

    // 別のワールドやサーバーへ持ち越さない
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        catalog = LoreCatalog.EMPTY;
        UNLOCKED.clear();
        SEEN.clear();
        lastSelected = null;
    }
}
