package jp.revati.skyabyss.client.notice;

import jp.revati.skyabyss.client.hud.HudManager;
import jp.revati.skyabyss.hud.HudLayout.Id;
import jp.revati.skyabyss.hud.HudLayout.Rect;
import jp.revati.skyabyss.hud.HudLayout.Region;
import jp.revati.skyabyss.hud.HudLayout.Request;
import jp.revati.skyabyss.hud.HudLayout.Size;
import jp.revati.skyabyss.client.ui.TextStyle;
import java.util.List;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.notice.NoticeGate;
import jp.revati.skyabyss.notice.NoticePayload;
import jp.revati.skyabyss.notice.NoticeQueue;
import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

// 画面に出す知らせの受け手と描画 (docs/design/18_notices.md)。
//   字幕 (下中央): 世界の声・囁き・手がかり。ゆっくり出て消え、順番に一つずつ
//   中央の演出: レベルの知らせ・技の点
//   上の帯 (中央上): 場所の名前 (大) と戦いの知らせ (小)
//   札 (右上): 獲得・解放。伝承・クエストの札と同じ列
//   アクションバー: 警告・失敗
// 種類ごとの順番待ちは NoticeQueue (重ならない・混んだら縮める・あふれたら手がかりから捨てる)
public final class NoticeClient {
    // 画面の持ち物: 字幕・中央・上の帯。値は表示する文
    record Line(Component text, Component extra) {
    }

    private static final NoticeQueue<Line> CAPTIONS = new NoticeQueue<>();
    private static final NoticeQueue<Line> CENTER = new NoticeQueue<>();
    private static final NoticeQueue<Line> BANNER = new NoticeQueue<>();
    private static long lastMillis = -1;
    private static long lastChime;

    // 上の帯・中央の演出・字幕の置き場は HudManager (docs/design/18 の 14 章) が決める。ここは「いま出したい文と寸法の決め方」を出し、
    // もらった長方形の中に描くだけ。レベルアップの文字の高さ (見出し 1 行 + 線 + 1.5 倍の数)
    public static final int LEVEL_UP_H = 32;

    // 会話・場面の間は札・経験値・中央の演出・手がかりを溜め、閉じたあとに優先の順で間隔をあけて出す (first_hour_log B-2・B-9)
    private static final NoticeGate GATE = new NoticeGate();

    // 画面に重ねて出さない画面: NPC との会話、場面 (カメラの演出・開幕の目覚め)
    public static boolean busy() {
        Minecraft mc = Minecraft.getInstance();
        return mc.screen instanceof jp.revati.skyabyss.client.npc.DialogueScreen || jp.revati.skyabyss.client.opening.OpeningClient.awakeActive();
    }

    // 札・経験値・演出・手がかりを出す仕事を関所に預ける (すぐ出せるときはすぐ出る)。優先は NoticeGate.P_*、gapAfter は次を出すまでの間隔
    public static void gate(int priority, long gapAfter, Runnable run) {
        GATE.offer(priority, gapAfter, run);
        GATE.drain(Util.getMillis(), busy());
    }

    private NoticeClient() {
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Setup {
        private Setup() {
        }

        @SubscribeEvent
        static void onLayers(RegisterGuiLayersEvent event) {
            event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "notices"), NoticeClient::render);
        }

        // ボスバーの下端を知る (上の帯・レベルアップ・中央の演出が、その下へずれるため)。ほかの担当が増分を変えたあとに見る
        @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST, receiveCanceled = true)
        static void onBossBar(net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent.BossEventProgress event) {
            jp.revati.skyabyss.client.hud.HudManager.noteBossBottom(event.getY() + event.getIncrement());
        }

        @SubscribeEvent
        static void onKeys(RegisterKeyMappingsEvent event) {
            event.register(LOG_KEY);
        }
    }

    // 知らせの記録を開く (N)
    public static final KeyMapping LOG_KEY = new KeyMapping("key.skyabyss.notice_log", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, "key.categories.skyabyss");

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    public static final class Ticker {
        private Ticker() {
        }

        @SubscribeEvent
        static void onTick(ClientTickEvent.Post event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) {
                GATE.clear();
            } else if (!mc.isPaused()) {
                GATE.drain(Util.getMillis(), busy());
            }
            while (LOG_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new NoticeLogScreen());
                }
            }
        }
    }

    // 札 (右上) を続けて出すときの間隔 (ミリ秒)。同じ瞬間に重ならず、一枚ずつ読める
    public static final long CARD_GAP = 700;

    // 同じ手がかりは、この遊びの間に三度まで (初めの数回だけ出す)
    private static final java.util.Map<String, Integer> HINT_COUNT = new java.util.HashMap<>();
    private static final int HINT_MAX = 3;

    public static void receive(NoticePayload payload) {
        Minecraft mc = Minecraft.getInstance();
        Notice.Kind kind = payload.kind();
        Component body = Notice.body(payload.text());
        String key = body.getString();
        Config.NotifyMode mode = Config.NOTIFY_MODE.get();
        if (kind != Notice.Kind.BAR && kind != Notice.Kind.WARN) {
            NoticeLog.add(kind, fullText(payload));
        }
        if (mode != Config.NotifyMode.TOAST) {
            mc.gui.getChat().addMessage(fullText(payload));
            if (mode == Config.NotifyMode.CHAT) {
                return;
            }
        }
        if (kind == Notice.Kind.HINT && HINT_COUNT.merge(key, 1, Integer::sum) > HINT_MAX) {
            return;
        }
        double speed = Config.NOTICE_SPEED.get();
        switch (kind.channel()) {
            case CAPTION -> {
                Runnable show = () -> {
                    CAPTIONS.setSpeed(speed);
                    CAPTIONS.offer(new Line(body, Component.empty()), kind, key, key.length());
                };
                if (kind == Notice.Kind.HINT) {
                    // 操作の手がかり (J・K・目的の一行など) は、会話・場面のあとに、札と経験値のあとで
                    gate(NoticeGate.P_HINT, 400, show);
                } else {
                    show.run();
                }
            }
            case CENTER -> gate(NoticeGate.P_CENTER, 1500, () -> {
                CENTER.setSpeed(speed);
                CENTER.offer(new Line(body, Component.empty()), kind, key, key.length());
                chime(mc, 1.5f, 0.5f);
            });
            case BANNER -> {
                BANNER.setSpeed(speed);
                BANNER.offer(new Line(body, payload.extra()), kind, key, key.length());
                if (kind == Notice.Kind.CHAPTER) {
                    chime(mc, 0.7f, 0.5f);
                }
            }
            case TOAST -> gate(NoticeGate.P_CARD, CARD_GAP, () -> {
                boolean gain = kind == Notice.Kind.GAIN;
                Component head = Component.translatable(gain ? "gui.skyabyss.notice.head.gain" : "gui.skyabyss.notice.head.unlock");
                mc.getToasts().addToast(new NoticeToast(mc.font, head, body, gain ? 0xF2C14E : 0x7FD6D0));
                chime(mc, gain ? 1.0f : 0.8f, 0.6f);
            });
            case ACTIONBAR -> mc.gui.setOverlayMessage(body, false);
        }
    }

    // 記録・チャットに残す文: 場所・章の札は題と副題を並べ、余韻は見出しと一言を並べる
    private static Component fullText(NoticePayload payload) {
        Notice.Kind kind = payload.kind();
        if (payload.extra().getString().isEmpty() || !(kind == Notice.Kind.PLACE || kind == Notice.Kind.CHAPTER || kind == Notice.Kind.AFTERMATH)) {
            return Notice.plain(payload.text());
        }
        return kind == Notice.Kind.AFTERMATH ? Component.empty().append(payload.extra()).append("  ").append(payload.text())
                : Component.empty().append(payload.text()).append("  ").append(payload.extra());
    }

    private static void chime(Minecraft mc, float pitch, float volume) {
        long now = Util.getMillis();
        if (now - lastChime < 250) {
            return;
        }
        lastChime = now;
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, volume));
    }

    // 開発用 (/skyabyss_hud demo): その列が空いているか (空いたら見本をまた出す)
    public static boolean idle(Notice.Channel channel) {
        return switch (channel) {
            case CAPTION -> CAPTIONS.idle();
            case CENTER -> CENTER.idle();
            case BANNER -> BANNER.idle();
            default -> true;
        };
    }

    // 自己テストや台本の前に、残っている知らせを消す
    public static void clear() {
        GATE.clear();
        CAPTIONS.clear();
        CENTER.clear();
        BANNER.clear();
    }

    private static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long dt = lastMillis < 0 ? 0 : Math.min(200, now - lastMillis);
        lastMillis = now;
        if (!mc.isPaused()) {
            CAPTIONS.advance(dt);
            CENTER.advance(dt);
            BANNER.advance(dt);
        }
        if (mc.options.hideGui) {
            return;
        }
        Font font = mc.font;
        renderBanner(g, font);
        renderCenter(g, font);
        renderCaption(g, font);
    }

    private static int alphaOf(float a) {
        return Mth.clamp((int) (a * 255), 4, 255) << 24;
    }

    // ------------------------------------------------------------ HUD の要求 (HudManager が毎フレーム呼ぶ)
    // 寸法の決め方 (Fit) は、渡された幅と高さの上限に収まるように文を折り返し・行を減らす。決めた中身は plan に覚え、描画が使う
    private static CapPlan capPlan;
    private static CenterPlan centerPlan;
    private static BannerPlan bannerPlan;

    public static void requests(Minecraft mc, int guiW, int guiH, List<Request> out) {
        Font font = mc.font;
        NoticeQueue.Entry<Line> cap = CAPTIONS.current();
        if (cap != null && CAPTIONS.alpha() > 0.01f) {
            Component text = cap.value.text();
            out.add(Request.of(Id.CAPTION, Region.CAPTION, (maxW, maxH) -> {
                CapPlan p = captionPlan(font, text, guiW, maxW, maxH);
                capPlan = p;
                return p == null ? null : new Size(p.w, p.h);
            }).minH(20));
        }
        NoticeQueue.Entry<Line> mid = CENTER.current();
        if (mid != null && CENTER.alpha() > 0.01f) {
            Component text = mid.value.text();
            out.add(Request.of(Id.CENTER_LINE, Region.TOP_CENTER, (maxW, maxH) -> {
                CenterPlan p = centerPlan(font, text, guiW, maxW, maxH);
                centerPlan = p;
                return p == null ? null : new Size(p.w, p.h);
            }).minTop((int) (guiH * 0.36f) - 6));
        }
        NoticeQueue.Entry<Line> ban = BANNER.current();
        if (ban != null && BANNER.alpha() > 0.01f) {
            Notice.Kind kind = ban.kind;
            Component text = ban.value.text();
            Component extra = ban.value.extra();
            boolean big = kind == Notice.Kind.PLACE || kind == Notice.Kind.CHAPTER || kind == Notice.Kind.AFTERMATH;
            out.add(Request.of(Id.BANNER, Region.TOP_CENTER, (maxW, maxH) -> {
                BannerPlan p = bannerPlan(font, kind, text, extra, guiW, maxW);
                if (p != null && p.h > maxH && !p.subLines.isEmpty()) {
                    p = bannerPlan(font, kind, text, Component.empty(), guiW, maxW);
                }
                if (p != null && p.h > maxH) {
                    p = null;
                }
                bannerPlan = p;
                return p == null ? null : new Size(p.w, p.h);
            }).minTop((int) (guiH * (big ? 0.16f : 0.1f))));
        }
    }

    private record CapPlan(List<FormattedCharSequence> lines, int w, int h) {
    }

    // 字幕: 折り返した文 (上限 4 行。高さに収まるまで行を減らす)。帯は文の左右に 12 ずつ
    private static CapPlan captionPlan(Font font, Component text, int guiW, int maxW, int maxH) {
        int wrap = Math.min(Math.min(guiW - 40, Math.max(160, (int) (guiW * 0.6f))), maxW - 24);
        int maxLines = Mth.clamp((maxH - 9) / 11, 0, 4);
        if (wrap < 40 || maxLines < 1) {
            return null;
        }
        List<FormattedCharSequence> lines = font.split(text, wrap);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        return new CapPlan(lines, w + 24, lines.size() * 11 + 9);
    }

    private record CenterPlan(List<FormattedCharSequence> lines, int textW, int w, int h) {
    }

    private static CenterPlan centerPlan(Font font, Component text, int guiW, int maxW, int maxH) {
        int wrap = Math.min(Math.min(guiW - 40, Math.max(180, (int) (guiW * 0.5f))), maxW - 40);
        if (wrap < 40) {
            return null;
        }
        List<FormattedCharSequence> lines = font.split(text, wrap);
        int maxLines = Mth.clamp((maxH - 10) / 11, 0, 2);
        if (maxLines < 1) {
            return null;
        }
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        return new CenterPlan(lines, w, w + 40, lines.size() * 11 + 10);
    }

    // 上の帯: kind = 場所 (PLACE)・章の題字 (CHAPTER)・余韻 (AFTERMATH)・戦いの知らせ (そのほか)。scale = 本文の拡大
    private record BannerPlan(Notice.Kind kind, float scale, List<FormattedCharSequence> lines, List<FormattedCharSequence> subLines, int textW, int w, int h) {
    }

    private static BannerPlan bannerPlan(Font font, Notice.Kind kind, Component text, Component sub, int guiW, int maxW) {
        boolean chapter = kind == Notice.Kind.CHAPTER || kind == Notice.Kind.AFTERMATH;
        boolean title = kind == Notice.Kind.CHAPTER;
        boolean place = kind == Notice.Kind.PLACE;
        float scale = chapter ? (title ? TextStyle.TITLE.scale() : TextStyle.HEADING.scale()) : place ? TextStyle.HEADING.scale() : TextStyle.BODY.scale();
        int wrapPx = chapter ? Math.max(140, Math.min(guiW - 40, (int) (guiW * 0.6f))) : Math.max(120, (int) (guiW * 0.5f));
        wrapPx = Math.min(wrapPx, maxW - (chapter ? 60 : 48));
        if (wrapPx < 40) {
            return null;
        }
        List<FormattedCharSequence> lines = font.split(text, (int) (wrapPx / scale));
        int maxLines = chapter ? (title ? 1 : 3) : 2;
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int tw = 0;
        for (FormattedCharSequence l : lines) {
            tw = Math.max(tw, font.width(l));
        }
        int blockW = (int) (tw * scale);
        List<FormattedCharSequence> subLines = List.of();
        int w;
        int h;
        if (chapter) {
            w = blockW + 60;
            if (title) {
                h = (int) (lines.size() * 11 * scale) + 4 + 6;
                if (!sub.getString().isEmpty()) {
                    float subScale = TextStyle.HEADING.scale();
                    h += (int) (11 * subScale) + 2;
                    w = Math.max(w, (int) (font.width(sub) * subScale) + 8);
                }
            } else {
                h = 20 + (int) (lines.size() * 11 * scale);
                w = Math.max(w, font.width(sub) + 56);
            }
            w = Math.min(w, maxW);
        } else {
            int blockH = (int) (lines.size() * 11 * scale);
            w = blockW + 48;
            h = blockH + 4 + 2;
            if (place && !sub.getString().isEmpty()) {
                List<FormattedCharSequence> sl = font.split(sub, Math.min(Math.min(guiW - 30, 340), maxW - 8));
                subLines = sl.subList(0, Math.min(2, sl.size()));
                int sw = 0;
                for (FormattedCharSequence l : subLines) {
                    sw = Math.max(sw, font.width(l));
                }
                w = Math.max(w, sw + 8);
                h = blockH + 4 + 6 + subLines.size() * 11;
            }
            w = Math.min(w, maxW);
        }
        return new BannerPlan(kind, scale, lines, subLines, tw, w, h);
    }

    // ------------------------------------------------------------ 描く
    // 字幕: 下中央、手がかりの上。暗い帯の上に、折り返した文
    private static void renderCaption(GuiGraphics g, Font font) {
        NoticeQueue.Entry<Line> e = CAPTIONS.current();
        Rect r = HudManager.rect(Id.CAPTION);
        CapPlan p = capPlan;
        if (e == null || r == null || p == null) {
            return;
        }
        float a = CAPTIONS.alpha();
        if (a <= 0.01f) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int top = r.y() + 5;
        int bottom = top + p.lines.size() * 11;
        int x0 = r.x();
        int x1 = r.right();
        // 帯は両端に向かって薄くなるように二段に重ねる
        g.fill(x0 + 10, top - 5, x1 - 10, bottom + 4, ((int) (a * 0x88) << 24) | 0x0A0E12);
        g.fill(x0, top - 3, x1, bottom + 2, ((int) (a * 0x50) << 24) | 0x0A0E12);
        int lineColor = ((int) (a * 0xB0) << 24) | (e.kind == Notice.Kind.HINT ? 0x8C8CA0 : 0x7FD6D0);
        g.fill(x0 + 18, top - 5, x1 - 18, top - 4, lineColor);
        g.fill(x0 + 18, bottom + 3, x1 - 18, bottom + 4, lineColor);
        int y = top;
        for (FormattedCharSequence l : p.lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, alphaOf(a) | 0xFFFFFF, true);
            y += 11;
        }
    }

    // 中央の演出: 細い線で挟んだ一行 (折り返し二行まで)
    private static void renderCenter(GuiGraphics g, Font font) {
        NoticeQueue.Entry<Line> e = CENTER.current();
        Rect r = HudManager.rect(Id.CENTER_LINE);
        CenterPlan p = centerPlan;
        if (e == null || r == null || p == null) {
            return;
        }
        float a = CENTER.alpha();
        if (a <= 0.01f) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y() + 6;
        float grow = Math.min(1f, e.age() / 350f);
        int half = (int) ((p.textW / 2 + 14) * grow);
        int lineColor = ((int) (a * 0xB0) << 24) | 0xF2C14E;
        g.fill(cx - half - 6, y - 6, cx + half + 6, y + p.lines.size() * 11 + 4, ((int) (a * 0x80) << 24) | 0x0A0E12);
        g.fill(cx - half, y - 4, cx + half, y - 3, lineColor);
        for (FormattedCharSequence l : p.lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, alphaOf(a) | 0xFFFFFF, true);
            y += 11;
        }
        g.fill(cx - half, y + 1, cx + half, y + 2, lineColor);
    }

    // 上の帯: 場所の名前は大きな金の文字と、その下に副題。戦いの知らせは小さめの一行。章の題字・余韻の札も同じ置き場 (列の中で一つだけ映る)
    private static void renderBanner(GuiGraphics g, Font font) {
        NoticeQueue.Entry<Line> e = BANNER.current();
        Rect r = HudManager.rect(Id.BANNER);
        BannerPlan p = bannerPlan;
        if (e == null || r == null || p == null) {
            return;
        }
        float a = BANNER.alpha();
        if (a <= 0.01f) {
            return;
        }
        int cx = r.x() + r.w() / 2;
        int y = r.y();
        if (e.kind == Notice.Kind.CHAPTER || e.kind == Notice.Kind.AFTERMATH) {
            renderChapterCard(g, font, cx, y, e, a, p);
            return;
        }
        boolean place = e.kind == Notice.Kind.PLACE;
        float scale = p.scale;
        int blockW = (int) (p.textW * scale);
        int blockH = (int) (p.lines.size() * 11 * scale);
        // 両側の細い線 (出るときに左右へ伸びる)
        int half = (int) (Math.min(1f, e.age() / 500f) * (blockW / 2 + 24));
        int lineColor = ((int) (a * 0x90) << 24) | (place ? 0xF2C14E : 0xB8923A);
        int ly = y + blockH + 4;
        g.fill(cx - half, ly, cx + half, ly + 1, lineColor);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int ty = 0;
        for (FormattedCharSequence l : p.lines) {
            g.drawString(font, l, -font.width(l) / 2, ty, alphaOf(a) | 0xFFFFFF, true);
            ty += 11;
        }
        g.pose().popPose();
        int sy = ly + 6;
        for (FormattedCharSequence l : p.subLines) {
            g.drawString(font, l, cx - font.width(l) / 2, sy, alphaOf(a) | 0xFFFFFF, true);
            sy += 11;
        }
    }

    // 章の題字の札 (CHAPTER) と余韻の札 (AFTERMATH)。
    //   CHAPTER  : 「一章　岩鯨」を TITLE の金の文字で、その下に題字を HEADING で。線が左右へ伸びる
    //   AFTERMATH: 見出し (小さな金) の下に一言を HEADING で、折り返し 3 行まで
    private static void renderChapterCard(GuiGraphics g, Font font, int cx, int y, NoticeQueue.Entry<Line> e, float a, BannerPlan p) {
        boolean title = e.kind == Notice.Kind.CHAPTER;
        float mainScale = p.scale;
        int textColor = title ? 0xF2C14E : 0xE9E3D4;
        int lineColor = ((int) (a * 0x90) << 24) | 0xF2C14E;
        int half = (int) (Math.min(1f, e.age() / 600f) * ((int) (p.textW * mainScale) / 2 + 30));
        Component sub = e.value.extra();
        int by = y;
        if (!title) {
            // 見出しを先に (小さく、線で挟む)
            int sw = font.width(sub);
            g.drawString(font, sub, cx - sw / 2, y, alphaOf(a) | 0xB8923A, true);
            int hw = (int) (Math.min(1f, e.age() / 600f) * (sw / 2 + 28));
            g.fill(cx - hw, y + 12, cx + hw, y + 13, lineColor);
            by = y + 20;
        }
        g.pose().pushPose();
        g.pose().translate(cx, by, 0);
        g.pose().scale(mainScale, mainScale, 1f);
        int ty = 0;
        for (FormattedCharSequence l : p.lines) {
            g.drawString(font, l, -font.width(l) / 2, ty, alphaOf(a) | textColor, true);
            ty += 11;
        }
        g.pose().popPose();
        int endY = by + (int) (p.lines.size() * 11 * mainScale);
        if (title) {
            int ly = endY + 4;
            g.fill(cx - half, ly, cx + half, ly + 1, lineColor);
            endY = ly + 6;
            if (!sub.getString().isEmpty()) {
                float subScale = TextStyle.HEADING.scale();
                int sw = font.width(sub);
                g.pose().pushPose();
                g.pose().translate(cx, endY, 0);
                g.pose().scale(subScale, subScale, 1f);
                g.drawString(font, sub, -sw / 2, 0, alphaOf(a) | 0xE9E3D4, true);
                g.pose().popPose();
            }
        }
    }
}
