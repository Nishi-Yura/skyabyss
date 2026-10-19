package jp.revati.skyabyss.client.rebirth;

import org.joml.Vector3f;

import com.mojang.brigadier.Command;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthConfig;
import jp.revati.skyabyss.rebirth.RebirthNet;
import jp.revati.skyabyss.rebirth.RebirthText;
import jp.revati.skyabyss.rebirth.RebirthTier;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.RebirthView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

// 転生の見せ方の client 側の入口。名札 (C03)・倒れ/立ち上がりの光 (C13)・枠 (C15)・順位表を開く (C11・C21)。
//   名札: 転生した人の名前の左に束ねた記号 (✦✧✺) を段の色で。5 マス以内ではさらに「Lv 50+n」・称号・縁の飾り (金は金の点、プリズムは回る色)。
//   5 マスより遠くは記号と色だけ (飾りは描かない)。飾りは設定 (RebirthConfig) で切れる。バニラの名札の描き方は変えず、中の文だけを足す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class RebirthClient {
    public static final double NEAR = 5.0;

    private RebirthClient() {
    }

    public static long timeMs() {
        return System.currentTimeMillis();
    }

    // プリズムを止めるか (動きを減らす設定)
    public static boolean still() {
        return RebirthConfig.on(RebirthConfig.PRISM_STILL);
    }

    public static int frameStyle(int count) {
        return RebirthTier.frameStyle(count);
    }

    // ---------------------------------------------------------------- 枠 (一覧・記録画面)
    // 段の色の縁。細い実線 (青緑)・二重線 (金)・角に飾り (プリズム。色が巡る)。0 回は何も描かない。色が分からなくても縁の形で段が分かる
    public static void drawFrame(GuiGraphics g, int x, int y, int w, int h, int count) {
        int style = RebirthTier.frameStyle(count);
        if (style <= 0) {
            return;
        }
        long t = timeMs();
        boolean still = still();
        int c = 0xFF000000 | RebirthTier.border(count, t, still, 0f);
        line(g, x, y, w, h, c);
        if (style >= 2) {
            int c2 = 0xFF000000 | RebirthTier.border(count, t, still, 0.25f);
            line(g, x - 1, y - 1, w + 2, h + 2, (c2 & 0xFFFFFF) | 0x80000000);
        }
        if (style >= 3) {
            // 角の飾り (小さな四角)
            int c3 = 0xFF000000 | RebirthTier.border(count, t, still, 0.5f);
            g.fill(x - 2, y - 2, x + 1, y + 1, c3);
            g.fill(x + w - 1, y - 2, x + w + 2, y + 1, c3);
            g.fill(x - 2, y + h - 1, x + 1, y + h + 2, c3);
            g.fill(x + w - 1, y + h - 1, x + w + 2, y + h + 2, c3);
        }
    }

    private static void line(GuiGraphics g, int x, int y, int w, int h, int argb) {
        g.fill(x, y, x + w, y + 1, argb);
        g.fill(x, y + h - 1, x + w, y + h, argb);
        g.fill(x, y, x + 1, y + h, argb);
        g.fill(x + w - 1, y, x + w, y + h, argb);
    }

    // ---------------------------------------------------------------- 名札 (C03)
    @SubscribeEvent
    static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p)) {
            return;
        }
        int n = RebirthView.count(p);
        if (n <= 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean near = mc.player != null && mc.player.distanceToSqr(p) <= NEAR * NEAR;
        event.setContent(nameTag(event.getContent(), n, p, near && RebirthConfig.on(RebirthConfig.NAMETAG_EFFECTS), near, timeMs(), still()));
    }

    // 名札の中身 (自己テストの見た目の確認にも使う)。base = 元の名前
    public static Component nameTag(Component base, int n, Player p, boolean effects, boolean near, long t, boolean still) {
        MutableComponent out = Component.empty();
        out.append(RebirthText.symbols(n, t, still)).append(Component.literal(" "));
        if (effects) {
            out.append(ornament(n, t, still, 0)).append(Component.literal(" "));
        }
        out.append(base);
        if (near) {
            out.append(Component.literal(" ")).append(RebirthText.level(50, n, t, still));
        }
        if (effects) {
            out.append(Component.literal(" ")).append(ornament(n, t, still, 1));
        }
        if (near && RebirthConfig.on(RebirthConfig.NAMETAG_TITLE)) {
            RebirthTitles.Title title = RebirthTitles.byId(RebirthView.data(p).title());
            if (title != null && RebirthTitles.has(RebirthView.data(p).unlocked(), title)) {
                out.append(Component.literal(" ")).append(Component.translatable("title.skyabyss_rebirth." + title.id())
                        .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xA9B4BA)).withItalic(true)));
            }
        }
        return out;
    }

    // 縁の飾り: 青緑 = なし、金 = 光の点がゆっくり明るさを替える、プリズム = 回る色の点 (どちらも小さな 1 文字)
    private static Component ornament(int n, long t, boolean still, int side) {
        return switch (RebirthTier.of(n)) {
            case WHITE, TEAL -> Component.empty();
            case GOLD -> {
                double wave = still ? 1.0 : 0.5 + 0.5 * Math.sin(t / 420.0 + side * Math.PI);
                int base = RebirthTier.GOLD_RGB;
                int rgb = mix(base, 0xFFFFFF, wave * 0.55);
                yield Component.literal("·").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
            }
            case PRISM -> Component.literal("·").withStyle(Style.EMPTY.withColor(TextColor.fromRgb(
                    RebirthTier.border(n, t, still, side * 0.5f))));
        };
    }

    private static int mix(int a, int b, double f) {
        int r = (int) Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * f);
        int g = (int) Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * f);
        int bl = (int) Math.round((a & 255) + ((b & 255) - (a & 255)) * f);
        return (r << 16) | (g << 8) | bl;
    }

    // ---------------------------------------------------------------- 倒れと回収の光 (C13)
    // 段の色の粒を 1 秒以内に散らす (粒の寿命は倍率 0.5 で 0.25〜1 秒)。倒れた = 外へ散る、立ち上がった = 上へ昇る。設定で切れる
    public static void burst(RebirthNet.BurstPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !RebirthConfig.on(RebirthConfig.BURST)) {
            return;
        }
        Entity e = mc.level.getEntity(payload.entityId());
        if (!(e instanceof Player p)) {
            return;
        }
        int n = RebirthView.count(p);
        if (n <= 0) {
            return;
        }
        RandomSource r = mc.level.random;
        int count = RebirthTier.of(n) == RebirthTier.Tier.TEAL ? 14 : 22;
        for (int i = 0; i < count; i++) {
            int rgb = RebirthTier.of(n) == RebirthTier.Tier.PRISM && !still()
                    ? RebirthTier.prism(timeMs(), r.nextFloat())
                    : RebirthTier.border(n, 0L, true, 0f);
            Vector3f col = new Vector3f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f);
            double a = r.nextDouble() * Math.PI * 2;
            double speed = payload.rise() ? 0.02 : 0.07;
            double vy = payload.rise() ? 0.07 + r.nextDouble() * 0.05 : 0.02 + r.nextDouble() * 0.04;
            mc.level.addParticle(new DustParticleOptions(col, 0.5f), p.getX() + Math.cos(a) * 0.3, p.getY() + 0.2 + r.nextDouble() * 1.2, p.getZ() + Math.sin(a) * 0.3,
                    Math.cos(a) * speed, vy, Math.sin(a) * speed);
        }
    }

    // ---------------------------------------------------------------- 画面を開く
    public static void openBoard(RebirthNet.BoardPayload payload) {
        Minecraft.getInstance().setScreen(new BoardScreen(payload));
    }

    @SubscribeEvent
    static void onClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("rebirthfx").executes(ctx -> {
            Minecraft mc = Minecraft.getInstance();
            mc.tell(() -> mc.setScreen(new RebirthScreen(null)));
            return Command.SINGLE_SUCCESS;
        }));
    }
}
