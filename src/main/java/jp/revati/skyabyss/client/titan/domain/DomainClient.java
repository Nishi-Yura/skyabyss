package jp.revati.skyabyss.client.titan.domain;

import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.cinematic.VoiceTiming;
import jp.revati.skyabyss.client.notice.NoticeClient;
import jp.revati.skyabyss.titan.domain.DomainPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 心臓の領域の画面 (titan/domain/HeartDomain。docs/design の各心臓の「領域」の節):
//  - 宣言: 上下の黒帯・暗転・題字 (領域の名前) と、天の声の一行 (20 の 場面の字幕と同じ金の字・金の細い線・字が一定の速さで出る)
//  - 領域のあいだ: 霧の色、画面の縁のうすい色、床の紋 (うすく、予告の橙・青白・緑とは違う色)、残りの時間と「砕く」進み
//  - 終わり: 砕かれたら白い瞬き、時間切れなら静かに色が戻る
// 画面の効果の強さの設定 (screenEffectScale) が低い人には、暗転・瞬きを弱める (戻りを速くはしない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class DomainClient {
    // 領域ごとの色: 霧 (0..1 の RGB)、縁と題字の色、床の紋の色
    private record Theme(float[] fog, int accent, int floor) {
    }

    private static Theme theme(String id) {
        return switch (id) {
            case "stone_sea" -> new Theme(new float[] {0.30f, 0.36f, 0.33f}, 0xB7C9B0, 0xA8B8A0);
            case "rising_tide" -> new Theme(new float[] {0.05f, 0.18f, 0.40f}, 0x6FA8FF, 0x3A6FD8);
            case "thunder_cage" -> new Theme(new float[] {0.14f, 0.12f, 0.30f}, 0xC8B8FF, 0xB8A4FF);
            default -> new Theme(new float[] {0.34f, 0.07f, 0.04f}, 0xFF8A4A, 0x8A2A18);
        };
    }

    private static final class Run {
        String id;
        long start;
        int declare;
        int active;
        double cx;
        double cz;
        double floor;
        double radius;
        int percent;
        int remaining;
        int round = 1;
        int rounds = 1;
        // 手順の案内: 常時の一行 (key / a / b) と、一瞬の合図 (正解・失敗・状況)
        String standKey = "";
        int standA;
        int standB;
        String flashKey = "";
        int flashA;
        int flashB;
        int flashMood;
        long flashAt;
        // 終わり: 0 = 続いている、1 = 時間切れ、2 = 砕かれた
        int ended;
        long endedAt;
        Theme theme;
    }

    private static Run run;

    private DomainClient() {
    }

    public static void receive(DomainPayload p) {
        Minecraft mc = Minecraft.getInstance();
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        switch (p.state()) {
            case DomainPayload.START -> {
                Run r = new Run();
                r.id = p.id();
                r.start = now;
                r.declare = p.a();
                r.active = p.b();
                r.cx = p.cx();
                r.cz = p.cz();
                r.floor = p.floor();
                r.radius = p.radius();
                r.remaining = 0;
                r.theme = theme(p.id());
                run = r;
            }
            case DomainPayload.PROGRESS -> {
                if (run != null && run.ended == 0) {
                    run.percent = p.a();
                    run.remaining = p.b();
                }
            }
            case DomainPayload.ROUND -> {
                if (run != null && run.ended == 0) {
                    run.round = p.a();
                    run.rounds = p.b();
                }
            }
            case DomainPayload.HINT -> {
                if (run != null && run.ended == 0) {
                    if (p.mood() == 0) {
                        run.standKey = p.key();
                        run.standA = p.a();
                        run.standB = p.b();
                    } else {
                        run.flashKey = p.key();
                        run.flashA = p.a();
                        run.flashB = p.b();
                        run.flashMood = p.mood();
                        run.flashAt = now;
                    }
                }
            }
            default -> {
                if (run != null && run.ended == 0) {
                    run.ended = p.a() == 1 ? 2 : 1;
                    run.endedAt = now;
                }
            }
        }
    }

    public static boolean active() {
        return run != null;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        run = null;
    }

    private static float now(float partial) {
        Minecraft mc = Minecraft.getInstance();
        return (mc.level == null ? 0 : mc.level.getGameTime()) + partial;
    }

    // 0..1: 領域の色の濃さ (宣言で立ち上がり、終わりで引く)
    private static float intensity(Run r, float t) {
        float age = t - r.start;
        float in = Mth.clamp(age / 24f, 0f, 1f);
        if (r.ended != 0) {
            float out = Mth.clamp(1f - (t - r.endedAt) / (r.ended == 2 ? 14f : 50f), 0f, 1f);
            return in * out;
        }
        return in;
    }

    private static void expire(float t) {
        if (run != null && run.ended != 0 && t - run.endedAt > 60) {
            run = null;
        }
        if (run != null && run.ended == 0 && t - run.start > run.declare + run.active + 200) {
            // 終わりの通信が届かなかったとき
            run = null;
        }
    }

    // ---------------------------------------------------------------- 霧の色
    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        if (run == null) {
            return;
        }
        float k = intensity(run, now((float) event.getPartialTick())) * 0.62f;
        if (k < 0.005f) {
            return;
        }
        float[] f = run.theme.fog;
        event.setRed(Mth.lerp(k, event.getRed(), f[0]));
        event.setGreen(Mth.lerp(k, event.getGreen(), f[1]));
        event.setBlue(Mth.lerp(k, event.getBlue(), f[2]));
    }

    // ---------------------------------------------------------------- 画面
    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        float t = now(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        expire(t);
        Run r = run;
        if (r == null) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float scale = Math.max(0.45f, mc.options.screenEffectScale().get().floatValue());
        float age = t - r.start;
        float in = intensity(r, t);
        int rgb = r.theme.accent;
        // 縁のうすい色
        if (in > 0.01f) {
            float pulse = 0.85f + 0.15f * (float) Math.sin(t * 0.14f);
            int a = (int) (in * 0.30f * pulse * scale * 255);
            int edge = (a << 24) | (rgb & 0xFFFFFF);
            int clear = rgb & 0xFFFFFF;
            int band = Math.max(24, h / 5);
            g.fillGradient(0, 0, w, band, edge, clear);
            g.fillGradient(0, h - band, w, h, clear, edge);
        }
        if (age < r.declare + 16 && r.ended == 0) {
            drawDeclaration(g, mc, r, age, w, h, scale);
        }
        if (r.ended == 2) {
            float f = (t - r.endedAt) / 14f;
            if (f < 1f) {
                g.fill(0, 0, w, h, ((int) ((1f - f) * 0.8f * scale * 255) << 24) | 0xFFFFFF);
            }
        }
        if (age >= r.declare - 8 || r.ended != 0) {
            drawHud(g, mc, r, t, w);
        }
    }

    // 宣言: 黒帯と暗転、題字、天の声の一行
    private static void drawDeclaration(GuiGraphics g, Minecraft mc, Run r, float age, int w, int h, float scale) {
        float slideIn = Mth.clamp(age / 8f, 0f, 1f);
        float slideOut = Mth.clamp((age - r.declare) / 12f, 0f, 1f);
        float bars = slideIn * (1f - slideOut);
        int barH = (int) (h * 0.11f * bars);
        float dark = Math.min(0.5f * scale, 0.5f) * bars;
        g.fill(0, 0, w, h, ((int) (dark * 255) << 24) | 0x04060C);
        g.fill(0, 0, w, barH, 0xFF000000);
        g.fill(0, h - barH, w, h, 0xFF000000);
        int cx = w / 2;
        int rgb = r.theme.accent;
        // 題字: 10〜(宣言の終わり + 8) で出入り
        float a = Math.min(Mth.clamp((age - 10f) / 10f, 0f, 1f), Mth.clamp((r.declare + 10f - age) / 10f, 0f, 1f));
        if (a > 0.02f) {
            Component name = Component.translatable("gui.skyabyss.domain." + r.id + ".name");
            int nameW = mc.font.width(name);
            float s = Math.min(3.4f, (w * 0.7f) / Math.max(1, nameW));
            int y = (int) (h * 0.34f);
            g.pose().pushPose();
            g.pose().translate(cx, y, 0);
            g.pose().scale(s, s, 1f);
            // 字は少し左右へ広がりながら出る
            float grow = 1f + (1f - a) * 0.06f;
            g.pose().scale(grow, 1f, 1f);
            g.drawString(mc.font, name, -nameW / 2, 0, (Mth.clamp((int) (a * 255f), 4, 255) << 24) | 0xFFFFFF, true);
            g.pose().popPose();
            int lineW = (int) (Math.max(nameW * s, 120) * 0.62f * a);
            int ia = (int) (a * 0xC0) << 24;
            g.fill(cx - lineW / 2, y - 8, cx + lineW / 2, y - 7, ia | (rgb & 0xFFFFFF));
            g.fill(cx - lineW / 2, y + (int) (9 * s) + 6, cx + lineW / 2, y + (int) (9 * s) + 7, ia | (rgb & 0xFFFFFF));
            Component label = Component.translatable("gui.skyabyss.domain.label");
            g.drawCenteredString(mc.font, label, cx, y - 20, (Mth.clamp((int) (a * 255f), 4, 255) << 24) | (rgb & 0xFFFFFF));
        }
        // 天の声の一行 (金。場面の字幕と同じ作り)
        float la = Math.min((float) Mth.smoothstep(Mth.clamp((age - 24f) / 8f, 0f, 1f)), Mth.clamp((r.declare + 14f - age) / 9f, 0f, 1f));
        if (la > 0.02f) {
            int alpha = Mth.clamp((int) (la * 255f), 4, 255) << 24;
            Component text = Component.translatable("gui.skyabyss.domain." + r.id + ".line").withStyle(Style.EMPTY.withColor(0xEBD07F));
            int maxW = Math.min(w - 60, Math.max(180, (int) (w * 0.62f)));
            List<FormattedCharSequence> rows = mc.font.split(text, maxW);
            if (rows.size() > 2) {
                rows = rows.subList(0, 2);
            }
            int blockH = rows.size() * 12 + 13;
            int bottom = h - barH - 22;
            int top = bottom - blockH;
            g.fillGradient(0, top - 22, w, bottom + 8, 0x00000000, ((int) (la * 0x99) << 24) | 0x05080C);
            g.fill(cx - 36, top - 6, cx + 36, top - 5, ((int) (la * 0xB0) << 24) | 0xD8B24A);
            g.drawCenteredString(mc.font, Component.translatable("cinematic.skyabyss.speaker.heaven"), cx, top, alpha | 0xC9A94F);
            int total = 0;
            for (FormattedCharSequence row : rows) {
                total += countChars(row);
            }
            int shown = total;
            if (Config.SUBTITLE_REVEAL.get() == Config.SubtitleReveal.SYNCED) {
                shown = VoiceTiming.revealSteady((r.declare - 26) / 20f, (age - 26f) / 20f, total, Config.STEADY_REVEAL_CPS.get().floatValue());
            }
            int left = shown;
            int y = top + 13;
            for (FormattedCharSequence row : rows) {
                int n = countChars(row);
                int x = cx - mc.font.width(row) / 2;
                if (left >= n) {
                    g.drawString(mc.font, row, x, y, alpha | 0xFFFFFF, true);
                } else if (left > 0) {
                    g.drawString(mc.font, prefix(row, left), x, y, alpha | 0xFFFFFF, true);
                }
                left = Math.max(0, left - n);
                y += 12;
            }
        }
    }

    private static int countChars(FormattedCharSequence seq) {
        int[] n = {0};
        seq.accept((i, style, cp) -> {
            n[0]++;
            return true;
        });
        return n[0];
    }

    private static FormattedCharSequence prefix(FormattedCharSequence seq, int count) {
        return sink -> {
            int[] i = {0};
            return seq.accept((idx, style, cp) -> i[0]++ < count ? sink.accept(idx, style, cp) : false);
        };
    }

    private static Component hudHead(Run r) {
        Component name = Component.translatable("gui.skyabyss.domain." + r.id + ".name");
        if (r.ended == 2) {
            return Component.translatable("gui.skyabyss.domain.broken");
        } else if (r.ended == 1) {
            return Component.translatable("gui.skyabyss.domain.faded");
        }
        return Component.translatable("gui.skyabyss.domain.hud", name);
    }

    // 手順の板 (右上。HudLayout の BOSS_DOMAIN。高さは固定で、ボスの技の名前が出ても動かない)
    public static final int PANEL_W = 196;
    public static final int PANEL_H = 52;

    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        Run r = run;
        if (r == null || mc.level == null) {
            return;
        }
        float t = now(0);
        if (!(t - r.start >= r.declare - 8 || r.ended != 0)) {
            return;
        }
        int h = r.ended == 0 ? PANEL_H : 16;
        out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.BOSS_DOMAIN, jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT,
                jp.revati.skyabyss.hud.HudLayout.Fit.keyed(java.util.List.of("domain", h), (maxW, maxH) -> h <= maxH && maxW >= 100 ? new jp.revati.skyabyss.hud.HudLayout.Size(Math.min(PANEL_W, maxW), h) : null)));
    }

    private static final int FLASH_TICKS = 50;

    // 領域の名前・解いた数・進みの帯・いまの手順 (大きく二行まで)・正解/失敗の合図
    private static void drawHud(GuiGraphics g, Minecraft mc, Run r, float t, int w) {
        jp.revati.skyabyss.hud.HudLayout.Rect rect = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.BOSS_DOMAIN);
        if (rect == null) {
            return;
        }
        float fadeIn = Mth.clamp((t - r.start - r.declare + 8f) / 10f, 0f, 1f);
        float fadeOut = r.ended != 0 ? Mth.clamp(1f - (t - r.endedAt) / 40f, 0f, 1f) : 1f;
        float a = fadeIn * fadeOut;
        if (a < 0.02f) {
            return;
        }
        int ia = Mth.clamp((int) (a * 255f), 4, 255) << 24;
        int rgb = r.theme.accent & 0xFFFFFF;
        int x = rect.x();
        int y = rect.y();
        int pw = rect.w();
        int ph = rect.h();
        float since = t - r.flashAt;
        boolean flashing = r.ended == 0 && !r.flashKey.isEmpty() && since < FLASH_TICKS;
        int tint = 0x0A0E18;
        if (flashing && r.flashMood == 1) {
            tint = blend(tint, 0x1F8A4A, 1f - since / FLASH_TICKS);
        } else if (flashing && r.flashMood == 2) {
            tint = blend(tint, 0xB02A20, 1f - since / FLASH_TICKS);
        }
        g.fill(x, y, x + pw, y + ph, ((int) (a * 190) << 24) | tint);
        int edge = flashing && r.flashMood == 1 ? 0x66E08A : flashing && r.flashMood == 2 ? 0xFF6A4D : rgb;
        g.fill(x, y, x + pw, y + 1, ia | edge);
        g.fill(x, y + ph - 1, x + pw, y + ph, ia | edge);
        g.fill(x, y, x + 1, y + ph, ia | edge);
        g.fill(x + pw - 1, y, x + pw, y + ph, ia | edge);
        Component head = hudHead(r);
        g.drawString(mc.font, head, x + 5, y + 4, ia | 0xFFFFFF, false);
        if (r.ended != 0) {
            return;
        }
        String count = r.remaining + " / " + jp.revati.skyabyss.titan.domain.HeartDomain.NODES;
        g.drawString(mc.font, count, x + pw - 5 - mc.font.width(count), y + 4, ia | 0xC8CCD8, false);
        if (r.rounds > 1) {
            Component rd = Component.translatable("gui.skyabyss.domain.round", r.round, r.rounds);
            g.drawString(mc.font, rd, x + pw - 5 - mc.font.width(count) - 8 - mc.font.width(rd), y + 4, ia | 0xFFE9A8, false);
        }
        int by = y + 14;
        int bw = pw - 10;
        g.fill(x + 5, by, x + 5 + bw, by + 3, ((int) (a * 170) << 24) | 0x101828);
        g.fill(x + 5, by, x + 5 + Math.round(bw * Mth.clamp(r.percent / 100f, 0f, 1f)), by + 3, ia | rgb);
        // いまの手順 (常時。二行まで)
        Component stand = Component.translatable(r.standKey.isEmpty() ? "gui.skyabyss.domain." + r.id + ".goal" : r.standKey, r.standA, r.standB);
        int ty = y + 20;
        for (FormattedCharSequence row : mc.font.split(stand, pw - 10).stream().limit(2).toList()) {
            g.drawString(mc.font, row, x + 5, ty, ia | 0xFFF4D0, true);
            ty += 10;
        }
        // 正解・失敗・状況の合図 (一つ。数秒で薄れる)
        if (flashing) {
            float fa = since < FLASH_TICKS - 12 ? 1f : (FLASH_TICKS - since) / 12f;
            int fc = r.flashMood == 1 ? 0x7DF0A0 : r.flashMood == 2 ? 0xFF8A72 : 0xE9E3D4;
            String mark = r.flashMood == 1 ? "◎ " : r.flashMood == 2 ? "✖ " : "";
            Component fl = Component.literal(mark).append(Component.translatable(r.flashKey, r.flashA, r.flashB));
            int fy = y + 41;
            for (FormattedCharSequence row : mc.font.split(fl, pw - 10).stream().limit(1).toList()) {
                g.drawString(mc.font, row, x + 5, fy, (Mth.clamp((int) (a * fa * 255), 4, 255) << 24) | fc, true);
                fy += 10;
            }
        }
    }

    private static int blend(int from, int to, float k) {
        k = Mth.clamp(k, 0f, 1f);
        int r = (int) Mth.lerp(k, (from >> 16) & 255, (to >> 16) & 255);
        int gg = (int) Mth.lerp(k, (from >> 8) & 255, (to >> 8) & 255);
        int b = (int) Mth.lerp(k, from & 255, to & 255);
        return (r << 16) | (gg << 8) | b;
    }

    // ---------------------------------------------------------------- 床の紋 (うすく。予告の面より常に弱い)
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || run == null) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Run r = run;
        if (level == null) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float t = now(partial);
        float in = intensity(r, t);
        if (in < 0.02f) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Matrix4f pose = event.getPoseStack().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Floor f = new Floor(buffer, pose, cam, r.floor + 0.045, r.theme.floor, in);
        float sec = (t - r.start) / 20f;
        double R = r.radius;
        switch (r.id) {
            case "stone_sea" -> {
                // 石の波紋: 外へ広がる輪
                for (int k = 0; k < 4; k++) {
                    double rr = (sec * 2.4 + k * R / 4) % R;
                    f.ring(r.cx, r.cz, rr - 0.45, rr + 0.45, (float) (0.22 * (1 - rr / R)));
                }
                f.disc(r.cx, r.cz, R, 0.04f);
            }
            case "rising_tide" -> {
                // 満ち潮: 外から内へ縮む輪と、うすい水面
                f.disc(r.cx, r.cz, R, 0.07f + 0.02f * (float) Math.sin(sec * 1.4));
                for (int k = 0; k < 4; k++) {
                    double rr = R - ((sec * 1.9 + k * R / 4) % R);
                    f.ring(r.cx, r.cz, rr - 0.3, rr + 0.3, (float) (0.20 * rr / R + 0.05));
                }
            }
            case "thunder_cage" -> {
                // 雷の格子: 7 マスごとの細い線が明滅する
                float flick = 0.14f + 0.12f * (float) Math.abs(Math.sin(sec * 7.0)) * (Math.sin(sec * 2.3) > 0.2 ? 1f : 0.4f);
                for (double d = -R; d <= R + 0.01; d += 7) {
                    f.rect(r.cx + d - 0.1, r.cz - R, r.cx + d + 0.1, r.cz + R, flick);
                    f.rect(r.cx - R, r.cz + d - 0.1, r.cx + R, r.cz + d + 0.1, flick);
                }
            }
            default -> {
                // 炉心: 熾の放射の筋と、脈打つ中心
                float pulse = 0.5f + 0.5f * (float) Math.sin(sec * 3.0);
                for (int k = 0; k < 16; k++) {
                    double a0 = k * Math.PI / 8 + sec * 0.08;
                    f.wedge(r.cx, r.cz, 2.5, R, a0, a0 + Math.PI / 32, 0.10f + 0.06f * pulse);
                }
                f.ring(r.cx, r.cz, 0, 3.2, 0.12f + 0.10f * pulse);
            }
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.enablePolygonOffset();
        RenderSystem.polygonOffset(-1.0f, -2.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.polygonOffset(0.0f, 0.0f);
        RenderSystem.disablePolygonOffset();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // 床の高さに置く平らな面 (カメラからの相対座標)
    private static final class Floor {
        private final BufferBuilder b;
        private final Matrix4f pose;
        private final Vec3 cam;
        private final double y;
        private final int rgb;
        private final float k;

        Floor(BufferBuilder b, Matrix4f pose, Vec3 cam, double y, int rgb, float k) {
            this.b = b;
            this.pose = pose;
            this.cam = cam;
            this.y = y;
            this.rgb = rgb;
            this.k = k;
        }

        private void v(double x, double z, int r, int g, int bl, int a) {
            b.addVertex(pose, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(r, g, bl, a);
        }

        private int alpha(float a) {
            return Mth.clamp((int) (a * k * 255f), 0, 255);
        }

        void rect(double x0, double z0, double x1, double z1, float a) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int bl = rgb & 0xFF;
            int al = alpha(a);
            v(x0, z0, r, g, bl, al);
            v(x0, z1, r, g, bl, al);
            v(x1, z1, r, g, bl, al);
            v(x1, z0, r, g, bl, al);
        }

        void wedge(double cx, double cz, double r0, double r1, double a0, double a1, float a) {
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int bl = rgb & 0xFF;
            int al = alpha(a);
            v(cx + Math.cos(a0) * r0, cz + Math.sin(a0) * r0, r, g, bl, al);
            v(cx + Math.cos(a0) * r1, cz + Math.sin(a0) * r1, r, g, bl, al);
            v(cx + Math.cos(a1) * r1, cz + Math.sin(a1) * r1, r, g, bl, al);
            v(cx + Math.cos(a1) * r0, cz + Math.sin(a1) * r0, r, g, bl, al);
        }

        void ring(double cx, double cz, double r0, double r1, float a) {
            if (a <= 0.003f || r1 <= 0) {
                return;
            }
            double in = Math.max(0, r0);
            int seg = 48;
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg;
                double a1 = Math.PI * 2 * (i + 1) / seg;
                wedge(cx, cz, in, r1, a0, a1, a);
            }
        }

        void disc(double cx, double cz, double r, float a) {
            ring(cx, cz, 0, r, a);
        }
    }
}
