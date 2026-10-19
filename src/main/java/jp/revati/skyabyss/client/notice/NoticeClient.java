package jp.revati.skyabyss.client.notice;

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

    // ---- 画面の中央まわりの置き場 (重ならないための共通の寸法)。上の帯 → レベルアップの文字 → 中央の演出 → 字幕、の順に積む
    // ボスバーの下端 (その枠の描画中に BossBarLayout が記す)、上の帯の下端 (前の枠の値)、レベルアップの文字の下端 (GrowthHud が記す)、中央の演出の下端 (前の枠の値)
    private static int bossBottom;
    private static int bannerBottom;
    private static int levelUpBottom;
    private static int centerBottomPrev;
    private static int centerBottom;
    // レベルアップの文字の高さ (見出し 1 行 + 線 + 1.5 倍の数)
    public static final int LEVEL_UP_H = 32;

    // ボスバーを描いた分の下端を記す (複数なら一番下)
    public static void noteBossBottom(int y) {
        bossBottom = Math.max(bossBottom, y);
    }

    // レベルアップの文字の上端: 画面の中ほどより少し上。上の帯やボスバーが下まで来ていれば、その下へずらす
    public static int levelUpTop(int guiHeight) {
        return Math.max(guiHeight / 2 - 58, Math.max(bannerBottom, bossBottom) + 8);
    }

    // レベルアップの文字を描いたら、その下端を記す
    public static void noteLevelUpBottom(int y) {
        levelUpBottom = Math.max(levelUpBottom, y);
    }

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
            noteBossBottom(event.getY() + event.getIncrement());
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
            bossBottom = 0;
            levelUpBottom = 0;
            return;
        }
        Font font = mc.font;
        int cx = g.guiWidth() / 2;
        // 上の帯を先に決め (下端を中央が使う)、中央、字幕の順に積む。字幕は中央の下端より上へは伸びない
        centerBottom = 0;
        renderBanner(g, font, cx);
        renderCenter(g, font, cx);
        renderCaption(g, font, cx);
        centerBottomPrev = Math.max(centerBottom, levelUpBottom);
        bossBottom = 0;
        levelUpBottom = 0;
    }

    private static int alphaOf(float a) {
        return Mth.clamp((int) (a * 255), 4, 255) << 24;
    }

    // 字幕: 下中央、ホットバーとアクションバーの上。暗い帯の上に、折り返した文
    private static void renderCaption(GuiGraphics g, Font font, int cx) {
        NoticeQueue.Entry<Line> e = CAPTIONS.current();
        if (e == null) {
            return;
        }
        float a = CAPTIONS.alpha();
        if (a <= 0.01f) {
            return;
        }
        int maxW = Math.min(g.guiWidth() - 40, Math.max(160, (int) (g.guiWidth() * 0.6f)));
        List<FormattedCharSequence> lines = font.split(e.value.text(), maxW);
        int bottom = g.guiHeight() - 84;
        // 中央の演出の下端より上へは伸ばさない (小さい画面では行を減らす)
        int above = Math.max(centerBottom, levelUpBottom);
        int maxLines = above <= 0 ? 4 : Mth.clamp((bottom - 6 - (above + 8)) / 11, 1, 4);
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        int h = lines.size() * 11;
        int top = bottom - h;
        int x0 = cx - w / 2 - 12;
        int x1 = cx + w / 2 + 12;
        // 帯は両端に向かって薄くなるように二段に重ねる
        g.fill(x0 + 10, top - 5, x1 - 10, bottom + 4, ((int) (a * 0x88) << 24) | 0x0A0E12);
        g.fill(x0, top - 3, x1, bottom + 2, ((int) (a * 0x50) << 24) | 0x0A0E12);
        int lineColor = ((int) (a * 0xB0) << 24) | (e.kind == Notice.Kind.HINT ? 0x8C8CA0 : 0x7FD6D0);
        g.fill(x0 + 18, top - 5, x1 - 18, top - 4, lineColor);
        g.fill(x0 + 18, bottom + 3, x1 - 18, bottom + 4, lineColor);
        int y = top;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, alphaOf(a) | 0xFFFFFF, true);
            y += 11;
        }
    }

    // 中央の演出: 「Lv」の文字 (GrowthHud) のすぐ下に、細い線で挟んだ一行 (折り返し二行まで)
    private static void renderCenter(GuiGraphics g, Font font, int cx) {
        NoticeQueue.Entry<Line> e = CENTER.current();
        if (e == null) {
            return;
        }
        float a = CENTER.alpha();
        if (a <= 0.01f) {
            return;
        }
        int maxW = Math.min(g.guiWidth() - 40, Math.max(180, (int) (g.guiWidth() * 0.5f)));
        List<FormattedCharSequence> lines = font.split(e.value.text(), maxW);
        if (lines.size() > 2) {
            lines = lines.subList(0, 2);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        // レベルアップの文字が出ているときはその下、上の帯・ボスバーがあるときはその下へ
        int y = Math.max((int) (g.guiHeight() * 0.36f), Math.max(bannerBottom, bossBottom) + 8);
        if (levelUpBottom > 0) {
            y = Math.max(y, levelUpBottom + 8);
        }
        float grow = Math.min(1f, e.age() / 350f);
        int half = (int) ((w / 2 + 14) * grow);
        int lineColor = ((int) (a * 0xB0) << 24) | 0xF2C14E;
        g.fill(cx - half - 6, y - 6, cx + half + 6, y + lines.size() * 11 + 4, ((int) (a * 0x80) << 24) | 0x0A0E12);
        g.fill(cx - half, y - 4, cx + half, y - 3, lineColor);
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, cx - font.width(l) / 2, y, alphaOf(a) | 0xFFFFFF, true);
            y += 11;
        }
        g.fill(cx - half, y + 1, cx + half, y + 2, lineColor);
        centerBottom = y + 3;
    }

    // 上の帯: 場所の名前は大きな金の文字と、その下に副題。戦いの知らせは小さめの一行
    private static void renderBanner(GuiGraphics g, Font font, int cx) {
        NoticeQueue.Entry<Line> e = BANNER.current();
        bannerBottom = 0;
        if (e == null) {
            return;
        }
        float a = BANNER.alpha();
        if (a <= 0.01f) {
            return;
        }
        if (e.kind == Notice.Kind.CHAPTER || e.kind == Notice.Kind.AFTERMATH) {
            bannerBottom = renderChapterCard(g, font, cx, e, a);
            return;
        }
        boolean place = e.kind == Notice.Kind.PLACE;
        float scale = place ? TextStyle.HEADING.scale() : TextStyle.BODY.scale();
        int maxW = (int) (Math.max(120, (int) (g.guiWidth() * 0.5f)) / scale);
        List<FormattedCharSequence> lines = font.split(e.value.text(), maxW);
        if (lines.size() > 2) {
            lines = lines.subList(0, 2);
        }
        // ボスバーがあれば、その下へ
        int y = Math.max((int) (g.guiHeight() * (place ? 0.16f : 0.1f)), bossBottom > 0 ? bossBottom + 6 : 0);
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        int blockW = (int) (w * scale);
        int blockH = (int) (lines.size() * 11 * scale);
        // 両側の細い線 (出るときに左右へ伸びる)
        int half = (int) (Math.min(1f, e.age() / 500f) * (blockW / 2 + 24));
        int lineColor = ((int) (a * 0x90) << 24) | (place ? 0xF2C14E : 0xB8923A);
        int ly = y + blockH + 4;
        g.fill(cx - half, ly, cx + half, ly + 1, lineColor);
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int ty = 0;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, -font.width(l) / 2, ty, alphaOf(a) | 0xFFFFFF, true);
            ty += 11;
        }
        g.pose().popPose();
        Component sub = e.value.extra();
        int endY = ly + 2;
        if (place && !sub.getString().isEmpty()) {
            List<FormattedCharSequence> subLines = font.split(sub, Math.min(g.guiWidth() - 30, 340));
            int sy = ly + 6;
            for (FormattedCharSequence l : subLines.subList(0, Math.min(2, subLines.size()))) {
                g.drawString(font, l, cx - font.width(l) / 2, sy, alphaOf(a) | 0xFFFFFF, true);
                sy += 11;
            }
            endY = sy;
        }
        bannerBottom = endY;
    }

    // 章の題字の札 (CHAPTER) と余韻の札 (AFTERMATH)。上の帯の列の中で一つだけ映る (順番待ち)。下端を返す (レベルアップの文字などがその下へずれる)
    //   CHAPTER  : 「一章　岩鯨」を TITLE の金の文字で、その下に題字を HEADING で。線が左右へ伸びる
    //   AFTERMATH: 見出し (小さな金) の下に一言を HEADING で、折り返し 3 行まで
    private static int renderChapterCard(GuiGraphics g, Font font, int cx, NoticeQueue.Entry<Line> e, float a) {
        boolean title = e.kind == Notice.Kind.CHAPTER;
        int y = Math.max((int) (g.guiHeight() * 0.16f), bossBottom > 0 ? bossBottom + 6 : 0);
        float mainScale = title ? TextStyle.TITLE.scale() : TextStyle.HEADING.scale();
        int maxW = (int) (Math.max(140, Math.min(g.guiWidth() - 40, (int) (g.guiWidth() * 0.6f))) / mainScale);
        List<FormattedCharSequence> lines = font.split(e.value.text(), maxW);
        int maxLines = title ? 1 : 3;
        if (lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        int textColor = title ? 0xF2C14E : 0xE9E3D4;
        int lineColor = ((int) (a * 0x90) << 24) | 0xF2C14E;
        int half = (int) (Math.min(1f, e.age() / 600f) * ((int) (w * mainScale) / 2 + 30));
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
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, -font.width(l) / 2, ty, alphaOf(a) | textColor, true);
            ty += 11;
        }
        g.pose().popPose();
        int endY = by + (int) (lines.size() * 11 * mainScale);
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
                endY += (int) (11 * subScale) + 2;
            }
        }
        return endY;
    }
}
