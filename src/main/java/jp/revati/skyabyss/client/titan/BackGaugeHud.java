package jp.revati.skyabyss.client.titan;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

// 巨神の背中の右上の目盛りの板の共通の部品 (13 の 10.2節。元は潮殻の TideGaugeHud。潮殻の 20.12節で直した
// 「文字切れ・効き目の絵とぶつかる・仲間の表示と重なる」を、巨神ごとに直し直さないため)。
// 板の置き場所 (右上、効き目の絵の下へずらす)、幅の合わせ方 (文に合わせ、ボスバーとの間をあける)、角を落とした枠、
// 枠からはみ出さない文、仲間の表示 (PartyClient) を板の下へ並べるための下端を持つ。中身 (潮の柱・熱の柱) は各巨神が描く。
//
// 使い方: 各巨神の目盛りのクラス (@EventBusSubscriber のクライアント側) の static 初期化で register(gauge) を一度呼び、
// RenderGuiEvent.Post で x = left(g, w)、y = top(mc)、w = width(g, 中身の幅, 最小の幅) に描く
public final class BackGaugeHud {
    // 右上の板の高さ (潮殻・余燼とも 30)
    public static final int HEIGHT = 30;
    private static final int MARGIN = 6;

    // 目盛りの一つ (いま出ているか、板の高さ)
    public interface Gauge {
        boolean shown(Minecraft mc);

        default int height() {
            return HEIGHT;
        }
    }

    private static final List<Gauge> GAUGES = new CopyOnWriteArrayList<>();

    private BackGaugeHud() {
    }

    public static void register(Gauge gauge) {
        if (!GAUGES.contains(gauge)) {
            GAUGES.add(gauge);
        }
    }

    // 板の上端: 置き場は HudManager (右上。誓いの帯の下、効き目の絵・札の下)。もらえていなければ従来の位置
    public static int top(Minecraft mc) {
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.BACK_GAUGE);
        if (r != null) {
            return r.y();
        }
        if (mc.player == null || mc.player.getActiveEffects().isEmpty()) {
            return MARGIN;
        }
        boolean other = mc.player.getActiveEffects().stream().anyMatch(e -> e.showIcon() && !e.getEffect().value().isBeneficial());
        boolean good = mc.player.getActiveEffects().stream().anyMatch(e -> e.showIcon() && e.getEffect().value().isBeneficial());
        return other ? 54 : good ? 28 : MARGIN;
    }

    // HUD の要求: 出ている板の全部ぶん (一番高いもの。目的の板があれば板の下に続く) を一つの塊として右上に
    public static void requests(Minecraft mc, java.util.List<jp.revati.skyabyss.hud.HudLayout.Request> out) {
        if (mc.level == null || mc.options.hideGui) {
            return;
        }
        int h = 0;
        for (Gauge g : GAUGES) {
            if (g.shown(mc)) {
                h = Math.max(h, g.height());
            }
        }
        if (h > 0) {
            int guiW = mc.getWindow().getGuiScaledWidth();
            out.add(jp.revati.skyabyss.hud.HudLayout.Request.of(jp.revati.skyabyss.hud.HudLayout.Id.BACK_GAUGE, jp.revati.skyabyss.hud.HudLayout.Region.TOP_RIGHT,
                    Mth.clamp(150, 112, Math.max(112, guiW / 2 - 91 - 4 - MARGIN)), h));
        }
    }

    // 出ている板の下端 (仲間の表示がこれより下に来るように)。板が出ていなければ 0
    public static int bottom() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) {
            return 0;
        }
        jp.revati.skyabyss.hud.HudLayout.Rect r = jp.revati.skyabyss.client.hud.HudManager.rect(jp.revati.skyabyss.hud.HudLayout.Id.BACK_GAUGE);
        return r == null ? 0 : r.bottom();
    }

    // 板の幅: 中身の幅 wanted を、最小 min から、ボスバー (中央の幅 182) との間を 4 あけた幅までに収める
    public static int width(GuiGraphics g, int wanted, int min) {
        return Mth.clamp(wanted, min, Math.max(min, g.guiWidth() / 2 - 91 - 4 - MARGIN));
    }

    // 板の左端 (右から MARGIN)
    public static int left(GuiGraphics g, int w) {
        return g.guiWidth() - w - MARGIN;
    }

    // 角を落とした板と枠 (1 px)
    public static void frame(GuiGraphics g, int x, int y, int w, int h, int fill, int border) {
        g.fill(x + 2, y, x + w - 2, y + h, fill);
        g.fill(x, y + 2, x + w, y + h - 2, fill);
        g.fill(x + 2, y, x + w - 2, y + 1, border);
        g.fill(x + 2, y + h - 1, x + w - 2, y + h, border);
        g.fill(x, y + 2, x + 1, y + h - 2, border);
        g.fill(x + w - 1, y + 2, x + w, y + h - 2, border);
    }

    // 文を幅 room に収めて描く (狭い画面で幅が足りなければ、はみ出す分を切る。枠の外へは出さない)
    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int room, int color) {
        g.drawString(font, Language.getInstance().getVisualOrder(font.substrByWidth(text, room)), x, y, color, true);
    }
}
