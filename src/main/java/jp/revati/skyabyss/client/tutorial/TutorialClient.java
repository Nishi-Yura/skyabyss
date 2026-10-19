package jp.revati.skyabyss.client.tutorial;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.cinematic.CinematicClient;
import jp.revati.skyabyss.client.notice.NoticeClient;
import jp.revati.skyabyss.client.pouch.PouchClient;
import jp.revati.skyabyss.client.record.RecordClient;
import jp.revati.skyabyss.party.client.PingClient;
import jp.revati.skyabyss.pouch.PouchEntries;
import jp.revati.skyabyss.pouch.PouchUsable;
import jp.revati.skyabyss.tutorial.DoneBanners;
import jp.revati.skyabyss.tutorial.TutorialHints;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

// 初回の手引きの表示 (docs/design/18 の 12 章)。サーバーが送る「今出ている手引きの ID」を、今のキー設定の実際のキー名
// (KeyMapping.getTranslatedKeyMessage) で文にして、ホットバーの上に出し続ける。済んだら「できた」を出して薄れる。
// 会話・場面・画面を開いている間は出さない (NoticeGate と同じ関所: NoticeClient.busy。閉じたあとにまた出る。出し続ける札なので溜めはしない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TutorialClient {
    // 「できた」を出している長さ (ミリ秒)
    public static final long DONE_MS = DoneBanners.DONE_MS;
    // 画面に同時に出す行の数
    public static final int MAX_LINES = 2;

    // 今出ている手引き (サーバーの並び)
    private static List<String> active = List.of();
    // 済んだ手引きの「できた」(出した一回だけ。時計は見せている間だけ進む)
    private static final DoneBanners DONE = new DoneBanners();
    private static long lastFrame = -1;

    private TutorialClient() {
    }

    public static void receive(TutorialHints.Sync sync) {
        active = List.copyOf(sync.active());
        for (String id : sync.done()) {
            DONE.add(id);
        }
    }

    public static List<String> active() {
        return active;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = List.of();
        DONE.clear();
        lastFrame = -1;
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "tutorial"), TutorialClient::render);
    }

    // 出してよいか (会話・場面・開いた画面・F1・デバッグ画面の間は出さない)
    static boolean allowed(Minecraft mc) {
        return mc.player != null && mc.level != null && !mc.options.hideGui && mc.screen == null && !mc.getDebugOverlay().showDebugScreen()
                && !CinematicClient.active() && !NoticeClient.busy();
    }

    // ---------------------------------------------------------------- 文
    // 手引きの文 (今のキー設定の実際のキー名と、今の持ち方で変わる)。持っていない・知らない ID は null
    @Nullable
    public static Component text(Minecraft mc, String id) {
        String base = "tutorial.skyabyss." + id;
        return switch (id) {
            case TutorialHints.POUCH -> Component.translatable(base, key(PouchClient.OPEN_KEY));
            case TutorialHints.RECORD -> Component.translatable(base, key(RecordClient.OPEN_KEY));
            case TutorialHints.PING -> Component.translatable(base, key(PingClient.PING_KEY));
            case TutorialHints.LORE_BOOK -> usable(mc, base, i -> i instanceof jp.revati.skyabyss.lore.LoreBookItem);
            case TutorialHints.LORE_PAGE -> usable(mc, base, i -> i instanceof jp.revati.skyabyss.lore.LorePageItem);
            case TutorialHints.WHISTLE -> usable(mc, base, i -> i instanceof jp.revati.skyabyss.titan.travel.TitanCallItem);
            default -> null;
        };
    }

    private static Component key(KeyMapping k) {
        return k.getTranslatedKeyMessage();
    }

    // 使う品の案内: 手に持てるなら右クリック、旅嚢の中なら Y の使い方 (短押し = 前回の道具か、道具が一つだけのとき。長押し = 輪)
    static Component usable(Minecraft mc, String base, Predicate<Item> match) {
        Variant v = variant(mc, match);
        Component y = key(PouchClient.USE_KEY);
        return switch (v) {
            case HAND -> Component.translatable(base + ".hand");
            case SHORT -> Component.translatable(base + ".short", y);
            case WHEEL -> Component.translatable(base + ".wheel", y);
        };
    }

    public enum Variant {
        HAND, SHORT, WHEEL
    }

    // 手 (持ち物) にあれば HAND。旅嚢だけにあるとき、短押しが使う道具 (一枚札) がそれなら SHORT、そうでなければ WHEEL
    public static Variant variant(Minecraft mc, Predicate<Item> match) {
        if (mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (!s.isEmpty() && match.test(s.getItem())) {
                    return Variant.HAND;
                }
            }
            // 短押しが使う道具 (左の札。札が切のときは前回の道具か、使える道具が一つだけのもの) がその品なら SHORT
            ResourceLocation tool = PouchClient.shortTool(mc);
            PouchEntries.Entry e = tool == null ? null : PouchEntries.byId(tool);
            if (e != null && match.test(e.item())) {
                return Variant.SHORT;
            }
        }
        return Variant.WHEEL;
    }

    // ---------------------------------------------------------------- 描く
    // ホットバーの上 (字幕の帯 (下から 84) のすぐ下、アクションバーの上)
    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long dt = lastFrame < 0 ? 0 : Math.min(now - lastFrame, 250);
        lastFrame = now;
        // 出せない間 (会話・画面) は「できた」の時計を止める (閉じたあとに見せる)
        boolean ok = allowed(mc);
        DONE.tick(dt, ok);
        if (!ok) {
            return;
        }
        List<Line> lines = new ArrayList<>();
        for (String id : DONE.ids()) {
            lines.add(new Line(Component.translatable("tutorial.skyabyss.done"), DONE.alpha(id), true));
        }
        for (String id : active) {
            if (lines.size() >= MAX_LINES) {
                break;
            }
            Component t = text(mc, id);
            if (t != null) {
                lines.add(new Line(t, 1f, false));
            }
        }
        if (lines.isEmpty()) {
            return;
        }
        // 点滅しない、ゆっくりした呼吸 (目を引くが邪魔にならない)
        float pulse = 0.85f + 0.15f * Mth.sin(now / 450f);
        int cx = g.guiWidth() / 2;
        int y = g.guiHeight() - 82 - (lines.size() - 1) * 13;
        for (Line l : lines) {
            int w = mc.font.width(l.text);
            int x0 = cx - w / 2 - 14;
            int x1 = cx + w / 2 + 14;
            float a = l.alpha * (l.done ? 1f : pulse);
            g.fill(x0, y - 2, x1, y + 11, ((int) (a * 0x99) << 24) | 0x0A0E12);
            g.fill(x0, y - 2, x0 + 1, y + 11, ((int) (a * 0xC0) << 24) | (l.done ? 0x7FD6D0 : 0xE0B84C));
            String mark = l.done ? "✔" : "◆";
            g.drawString(mc.font, mark, x0 + 5, y, ((int) (a * 255) << 24) | (l.done ? 0x7FD6D0 : 0xE0B84C), true);
            g.drawString(mc.font, l.text, cx - w / 2 + 2, y, ((int) (Mth.clamp(a, 0.05f, 1f) * 255) << 24) | 0xFFFFFF, true);
            y += 13;
        }
    }

    private record Line(Component text, float alpha, boolean done) {
    }
}
