package jp.revati.skyabyss.client.notice;

import java.util.List;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
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
            while (LOG_KEY.consumeClick()) {
                if (mc.player != null && mc.screen == null) {
                    mc.setScreen(new NoticeLogScreen());
                }
            }
        }
    }

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
            NoticeLog.add(kind, kind == Notice.Kind.PLACE && !payload.extra().getString().isEmpty()
                    ? Component.empty().append(payload.text()).append("  ").append(payload.extra()) : Notice.plain(payload.text()));
        }
        if (mode != Config.NotifyMode.TOAST) {
            Component full = kind == Notice.Kind.PLACE && !payload.extra().getString().isEmpty()
                    ? Component.empty().append(payload.text()).append("  ").append(payload.extra()) : Notice.plain(payload.text());
            mc.gui.getChat().addMessage(full);
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
                CAPTIONS.setSpeed(speed);
                CAPTIONS.offer(new Line(body, Component.empty()), kind, key, key.length());
            }
            case CENTER -> {
                CENTER.setSpeed(speed);
                CENTER.offer(new Line(body, Component.empty()), kind, key, key.length());
                chime(mc, 1.5f, 0.5f);
            }
            case BANNER -> {
                BANNER.setSpeed(speed);
                BANNER.offer(new Line(body, payload.extra()), kind, key, key.length());
            }
            case TOAST -> {
                boolean gain = kind == Notice.Kind.GAIN;
                Component head = Component.translatable(gain ? "gui.skyabyss.notice.head.gain" : "gui.skyabyss.notice.head.unlock");
                mc.getToasts().addToast(new NoticeToast(mc.font, head, body, gain ? 0xF2C14E : 0x7FD6D0));
                chime(mc, gain ? 1.0f : 0.8f, 0.6f);
            }
            case ACTIONBAR -> mc.gui.setOverlayMessage(body, false);
        }
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
        int cx = g.guiWidth() / 2;
        renderCaption(g, font, cx);
        renderCenter(g, font, cx);
        renderBanner(g, font, cx);
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
        if (lines.size() > 4) {
            lines = lines.subList(0, 4);
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        int h = lines.size() * 11;
        int bottom = g.guiHeight() - 84;
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
        int y = (int) (g.guiHeight() * 0.36f);
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
    }

    // 上の帯: 場所の名前は大きな金の文字と、その下に副題。戦いの知らせは小さめの一行
    private static void renderBanner(GuiGraphics g, Font font, int cx) {
        NoticeQueue.Entry<Line> e = BANNER.current();
        if (e == null) {
            return;
        }
        float a = BANNER.alpha();
        if (a <= 0.01f) {
            return;
        }
        boolean place = e.kind == Notice.Kind.PLACE;
        float scale = place ? 1.8f : 1.0f;
        int maxW = (int) (Math.max(120, (int) (g.guiWidth() * 0.5f)) / scale);
        List<FormattedCharSequence> lines = font.split(e.value.text(), maxW);
        if (lines.size() > 2) {
            lines = lines.subList(0, 2);
        }
        int y = (int) (g.guiHeight() * (place ? 0.16f : 0.1f));
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
        if (place && !sub.getString().isEmpty()) {
            List<FormattedCharSequence> subLines = font.split(sub, Math.min(g.guiWidth() - 30, 340));
            int sy = ly + 6;
            for (FormattedCharSequence l : subLines.subList(0, Math.min(2, subLines.size()))) {
                g.drawString(font, l, cx - font.width(l) / 2, sy, alphaOf(a) | 0xFFFFFF, true);
                sy += 11;
            }
        }
    }
}
