package jp.revati.skyabyss.client.notice;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.hud.HudLayout;

// 札 (獲得・進展・クエスト・伝承・進捗) の出口を一つに。置き場は設定 cardPlace (docs/design/18 の 3 章):
//   TOP_RIGHT    = 今までどおり右上の札 (バニラの札の列)
//   BOTTOM_RIGHT = 右下 (ホットバーの右上) に札を最大 2 枚
//   LEFT_FEED    = 左の目的の帯の下に一行ずつ (4 行まで、5 秒で薄れて消える)
//   COMPACT      = 右上のまま、一行ずつの小さな列 (4 行まで。左の列と同じ描き方)
// バニラの進捗の札 (AdvancementToast) は mixin が横取りして「進捗」の札にする。レシピ・操作説明の札は出さない (ToastAddMixin)
public final class CardFeed {
    private CardFeed() {
    }

    private record Entry(Component head, Component body, int accent, long at, net.minecraft.world.item.ItemStack icon) {
    }

    private static final List<Entry> FEED = new ArrayList<>();
    private static final long FEED_LIFE = 5200L;
    private static final long FEED_FADE = 700L;
    private static final int FEED_MAX = 4;
    public static final int FEED_W = 220;
    private static final boolean DEBUG_LOG = System.getenv("SKYABYSS_FEED_LOG") != null;
    private static long lastRender = -1;
    private static int capTop = Integer.MAX_VALUE;
    private static int capW = FEED_W;

    public static Config.CardPlace place() {
        return Config.CARD_PLACE.get();
    }

    // 札を出す。toast = 右上・右下で使う今までの札 (作り方)。head = 小さな見出し (「獲得」など)、body = 本文 (一行目)
    public static void show(Component head, Component body, int accent, Supplier<Toast> toast) {
        show(head, body, accent, net.minecraft.world.item.ItemStack.EMPTY, toast);
    }

    // icon = 見出しの左の小さな絵 (品。空なら種類の印)
    public static void show(Component head, Component body, int accent, net.minecraft.world.item.ItemStack icon, Supplier<Toast> toast) {
        Minecraft mc = Minecraft.getInstance();
        switch (place()) {
            case TOP_RIGHT, BOTTOM_RIGHT -> mc.getToasts().addToast(toast.get());
            case COMPACT, LEFT_FEED -> {
                synchronized (FEED) {
                    FEED.add(new Entry(head, body, accent, net.minecraft.Util.getMillis(), icon));
                    while (FEED.size() > FEED_MAX) {
                        FEED.remove(0);
                    }
                }
            }
        }
    }

    // 進捗 (バニラ・mod の進捗の札の代わり)
    public static void advancement(Component title, boolean challenge, net.minecraft.world.item.ItemStack icon) {
        int accent = challenge ? 0xB58AE0 : 0xF2C14E;
        Component head = Component.translatable(challenge ? "gui.skyabyss.card.challenge" : "gui.skyabyss.card.advancement");
        // ほかの札と同じく、会話・場面の最中は溜めて閉じたあとに (NoticeGate)。出るときに音 (挑戦はバニラの挑戦の音、ふつうは小さな鐘)
        NoticeClient.gate(jp.revati.skyabyss.notice.NoticeGate.P_CARD, NoticeClient.CARD_GAP, () -> {
            show(head, title, accent, icon, () -> new NoticeToast(Minecraft.getInstance().font, head, title, accent));
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(challenge
                    ? net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.7f)
                    : net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_CHIME, 1.25f, 0.9f));
        });
    }

    // ---------------------------------------------------------------- 左の一行の列
    private static List<Entry> live(long now) {
        synchronized (FEED) {
            FEED.removeIf(e -> now - e.at > FEED_LIFE);
            return new ArrayList<>(FEED);
        }
    }

    // 配置の計算 (HudLayout) には載せない: 左は目的の帯の下からチャット欄の上まで、右上は右上の板の下に、直接置く (render)
    public static void requests(Minecraft mc, List<HudLayout.Request> out) {
    }

    static void render(GuiGraphics g, net.minecraft.client.DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Config.CardPlace p = place();
        if ((p != Config.CardPlace.LEFT_FEED && p != Config.CardPlace.COMPACT) || mc.player == null || mc.options.hideGui) {
            return;
        }
        HudLayout.Rect rect;
        capTop = Integer.MAX_VALUE;
        capW = FEED_W;
        if (p == Config.CardPlace.COMPACT) {
            rect = topRight(g);
        } else {
            // 左: 章の札・目的の帯・灯の帯・追跡中の依頼・経験値の列の下から、チャット欄の上まで
            int y = 4;
            for (HudLayout.Id id : new HudLayout.Id[] {HudLayout.Id.STATUS_TL, HudLayout.Id.SLOTS_TL, HudLayout.Id.CHAPTER, HudLayout.Id.OBJECTIVE,
                    HudLayout.Id.LAMP, HudLayout.Id.QUEST, HudLayout.Id.POPUPS_TL}) {
                HudLayout.Rect r = HudManager.rect(id);
                if (r != null && r.x() < g.guiWidth() / 2) {
                    y = Math.max(y, r.bottom() + 3);
                }
            }
            int limit = g.guiHeight() - 62;
            HudLayout.Rect chat = HudManager.result().vanilla.get(HudLayout.Id.V_CHAT);
            if (chat != null && chat.h() > 0 && mc.screen == null) {
                limit = Math.min(limit, chat.y() - 3);
            }
            // 幅は画面の中央の字幕 (と手がかり) にかからない所まで。字幕が今出ているならその左の端まで
            int w = Math.max(110, Math.min(FEED_W, g.guiWidth() / 2 - 182));
            // 字幕 (帯を含めて中央から左右へ広がる) にかかる高さまでは今の幅、そこから下は字幕の左の端まで細くした幅で続ける
            HudLayout.Rect cap = HudManager.rect(HudLayout.Id.CAPTION);
            capTop = Integer.MAX_VALUE;
            capW = w;
            if (cap != null && cap.x() < 3 + w + 4 && cap.bottom() > y) {
                capTop = cap.y() - 2;
                capW = cap.x() - 3 - 18;
                if (capW < 90) {
                    limit = Math.min(limit, capTop - 1);
                }
            }
            rect = new HudLayout.Rect(3, y, w, Math.max(0, limit - y));
        }
        if (rect == null) {
            return;
        }
        long now = net.minecraft.Util.getMillis();
        Font font = mc.font;
        int y = rect.y() + 1;
        List<Entry> shown = live(now);
        // 入るだけ (新しいものを残す)
        int used = 0;
        int from = shown.size();
        while (from > 0) {
            int hh = lines(font, shown.get(from - 1), Math.min(rect.w(), capW)).size() * 10 + 2;
            if (used + hh > rect.h()) {
                break;
            }
            used += hh;
            from--;
        }
        shown = shown.subList(from, shown.size());
        // 左に一行も入らない (GUI の倍率が大きい・字幕が長い) ときは、右上に出す
        if (shown.isEmpty() && p == Config.CardPlace.LEFT_FEED && !live(now).isEmpty()) {
            rect = topRight(g);
            capTop = Integer.MAX_VALUE;
            capW = rect.w();
            shown = live(now);
            used = 0;
            from = shown.size();
            while (from > 0) {
                int hh = lines(font, shown.get(from - 1), rect.w()).size() * 10 + 2;
                if (used + hh > rect.h()) {
                    break;
                }
                used += hh;
                from--;
            }
            shown = shown.subList(from, shown.size());
            y = rect.y() + 1;
        }
        // どこにも入らないあいだは時計を止める (混んだ場面が過ぎてから出る。消えて見逃さない)
        long dt = lastRender < 0 ? 0 : Math.min(250, now - lastRender);
        lastRender = now;
        if (shown.isEmpty() && dt > 0) {
            synchronized (FEED) {
                for (int i = 0; i < FEED.size(); i++) {
                    Entry e = FEED.get(i);
                    FEED.set(i, new Entry(e.head, e.body, e.accent, e.at + dt, e.icon));
                }
            }
            return;
        }
        if (DEBUG_LOG && HudManager.frame() % 40 == 0) {
            SkyAbyss.LOGGER.info("[cardfeed] rect={} capTop={} capW={} live={} shown={}", rect, capTop, capW, live(now).size(), shown.size());
        }
        int capTop0 = capTop;
        int capW0 = capW;
        for (Entry e : shown) {
            long age = now - e.at;
            float a = age > FEED_LIFE - FEED_FADE ? Mth.clamp((FEED_LIFE - age) / (float) FEED_FADE, 0f, 1f) : Mth.clamp(age / 180f, 0f, 1f);
            int alpha = (int) (a * 255);
            if (alpha < 8) {
                continue;
            }
            int x = rect.x();
            // 出てくるときに左から少し滑る
            int slide = age < 180 ? (int) ((1f - age / 180f) * 8) : 0;
            String head = e.head.getString();
            int iw = 12;
            int hw = font.width(head) + iw;
            int ew = y + 24 > capTop0 ? Math.min(rect.w(), capW0) : rect.w();
            List<FormattedCharSequence> lines = lines(font, e, ew);
            int hh = lines.size() * 10 + 1;
            g.fill(x - slide, y, x + ew - slide, y + hh, ((int) (alpha * 0.55f)) << 24 | 0x0A0C10);
            g.fill(x - slide, y, x + 1 - slide, y + hh, alpha << 24 | e.accent);
            // 小さな絵 (品を 10 px に縮めて)。品が無ければ種類の色の菱形
            if (!e.icon.isEmpty() && alpha > 160) {
                g.pose().pushPose();
                g.pose().translate(x + 3 - slide, y + 1 - 0.5f, 0);
                g.pose().scale(0.625f, 0.625f, 1f);
                g.renderItem(e.icon, 0, 0);
                g.pose().popPose();
            } else {
                int cx = x + 7 - slide;
                int cy = y + 5;
                int col = alpha << 24 | e.accent;
                g.fill(cx - 1, cy - 3, cx + 1, cy + 4, col);
                g.fill(cx - 2, cy - 2, cx + 2, cy + 3, col);
                g.fill(cx - 3, cy - 1, cx + 3, cy + 2, col);
                g.fill(cx - 1, cy - 1, cx + 1, cy + 2, alpha << 24 | 0x0A0C10);
            }
            g.drawString(font, head, x + 4 + iw - slide, y + 2, alpha << 24 | e.accent, true);
            int ly = y + 2;
            for (FormattedCharSequence line : lines) {
                g.drawString(font, line, x + 9 + hw - slide, ly, alpha << 24 | 0xE9E3D4, true);
                ly += 10;
            }
            y += hh + 1;
        }
    }

    // 本文は見出しの右に一行 (長い文は終わりを「…」で切る。全文は N の知らせの記録)
    private static List<FormattedCharSequence> lines(Font font, Entry e, int w) {
        int room = Math.max(40, w - font.width(e.head.getString()) - 14 - 12);
        List<FormattedCharSequence> l = font.split(e.body, room);
        if (l.size() <= 2) {
            return l.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : l;
        }
        // 3 行目からは切る: 2 行目の終わりを「…」に
        String all = e.body.getString();
        String first = font.plainSubstrByWidth(all, room);
        String rest = all.substring(first.length());
        while (rest.length() > 1 && font.width(rest + "…") > room) {
            rest = rest.substring(0, rest.length() - 1);
        }
        return List.of(Component.literal(first).getVisualOrderText(), Component.literal(rest + "…").getVisualOrderText());
    }

    private static int rows(Font font, Entry e) {
        return lines(font, e, FEED_W).size();
    }

    // 右上 (右上の板とバニラの効果のアイコンの下)
    private static HudLayout.Rect topRight(GuiGraphics g) {
        int y = 4;
        for (HudLayout.Id id : new HudLayout.Id[] {HudLayout.Id.BOSS_DOMAIN, HudLayout.Id.VOW, HudLayout.Id.BACK_GAUGE, HudLayout.Id.PARTY_LIST}) {
            HudLayout.Rect r = HudManager.rect(id);
            if (r != null) {
                y = Math.max(y, r.bottom() + 3);
            }
        }
        HudLayout.Rect fx = HudManager.result().vanilla.get(HudLayout.Id.V_EFFECTS);
        if (fx != null && fx.h() > 0 && fx.x() + fx.w() > g.guiWidth() - FEED_W) {
            y = Math.max(y, fx.bottom() + 3);
        }
        int w = Math.min(FEED_W, Math.max(110, g.guiWidth() / 2 - 100));
        int x = g.guiWidth() - w - 4;
        // 上中央の帯 (場所の名前・戦いの知らせ・技の名前・レベルアップ・中央の一行) と横で重なるなら、その下から
        for (HudLayout.Id id : new HudLayout.Id[] {HudLayout.Id.BOSS_CUE, HudLayout.Id.BANNER, HudLayout.Id.LEVEL_UP, HudLayout.Id.CENTER_LINE}) {
            HudLayout.Rect r = HudManager.rect(id);
            if (r != null && r.x() + r.w() > x && r.bottom() + 3 > y && r.y() < g.guiHeight() / 2) {
                y = r.bottom() + 3;
            }
        }
        return new HudLayout.Rect(x, y, w, Math.max(0, g.guiHeight() / 2 - 20 - y));
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "card_feed"), CardFeed::render);
        }
    }

}
