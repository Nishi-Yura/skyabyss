package jp.revati.skyabyss.client.hud;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.hud.HudLayout;
import jp.revati.skyabyss.trial.ChallengeBand;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// 挑んでいる所の帯 (trial/ChallengeBand)。右上の板の一番上 (誓いの帯の上) に右寄せで:
//   一行目 金の名前 (ダンジョン・巨神のルート)、二行目 今の層・部屋、三行目 札 (深く潜る・今日の様子・人数・中ボスの部屋…) を色ごとに「・」でつなぐ。
// 字は省かない: 幅が足りなければ札は次の行へ折り返す
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class ChallengeHud {
    private static volatile ChallengeBand.Band current = ChallengeBand.Band.OFF;
    static final int MAX_W = 170;

    private ChallengeHud() {
    }

    public static void receive(ChallengeBand.Band b) {
        current = b;
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        current = ChallengeBand.Band.OFF;
    }

    @SubscribeEvent
    static void onLayers(RegisterGuiLayersEvent event) {
        event.registerBelow(VanillaGuiLayers.CHAT, ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "challenge_band"), ChallengeHud::render);
    }

    // 札を行に並べる (幅 w に収まるように折り返す)
    static List<List<ChallengeBand.Tag>> tagRows(Font font, List<ChallengeBand.Tag> tags, int w) {
        List<List<ChallengeBand.Tag>> rows = new java.util.ArrayList<>();
        List<ChallengeBand.Tag> row = new java.util.ArrayList<>();
        int used = 0;
        int sep = font.width("・");
        for (ChallengeBand.Tag t : tags) {
            int tw = font.width(t.text());
            if (!row.isEmpty() && used + sep + tw > w) {
                rows.add(row);
                row = new java.util.ArrayList<>();
                used = 0;
            }
            used += (row.isEmpty() ? 0 : sep) + tw;
            row.add(t);
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return rows;
    }

    static int lineCount(Font font, ChallengeBand.Band b, int w) {
        int n = 1 + (b.sub().getString().isEmpty() ? 0 : 1);
        return n + tagRows(font, b.tags(), w).size();
    }

    public static void requests(Minecraft mc, List<HudLayout.Request> out) {
        ChallengeBand.Band b = current;
        if (b.off() || !jp.revati.skyabyss.party.client.PartyMarkers.hudAllowed(mc)) {
            return;
        }
        Font font = mc.font;
        int natW = Math.max(font.width(b.title()), font.width(b.sub()));
        int tagW = 0;
        for (ChallengeBand.Tag t : b.tags()) {
            tagW += font.width(t.text()) + font.width("・");
        }
        int want = Math.min(MAX_W, Math.max(natW, tagW));
        out.add(HudLayout.Request.of(HudLayout.Id.CHALLENGE, HudLayout.Region.TOP_RIGHT,
                HudLayout.Fit.keyed(List.of("challenge", want, b.title().getString(), b.sub().getString(), b.tags().size()), (maxW, maxH) -> {
                    int w = Math.min(want, maxW);
                    if (w < Math.max(font.width(b.title()), font.width(b.sub())) && w < MAX_W) {
                        return null;
                    }
                    int h = lineCount(font, b, w) * 11;
                    return h <= maxH ? new HudLayout.Size(w, h) : null;
                })));
    }

    static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        ChallengeBand.Band b = current;
        HudLayout.Rect r = HudManager.rect(HudLayout.Id.CHALLENGE);
        if (b.off() || r == null || !jp.revati.skyabyss.party.client.PartyMarkers.hudAllowed(mc)) {
            return;
        }
        Font font = mc.font;
        int y = r.y();
        g.drawString(font, b.title(), r.right() - font.width(b.title()), y, 0xF2C14E, true);
        y += 11;
        if (!b.sub().getString().isEmpty()) {
            g.drawString(font, b.sub(), r.right() - font.width(b.sub()), y, 0xE9E3D4, true);
            y += 11;
        }
        for (List<ChallengeBand.Tag> row : tagRows(font, b.tags(), r.w())) {
            int w = 0;
            for (int i = 0; i < row.size(); i++) {
                w += font.width(row.get(i).text()) + (i > 0 ? font.width("・") : 0);
            }
            int x = r.right() - w;
            for (int i = 0; i < row.size(); i++) {
                if (i > 0) {
                    g.drawString(font, "・", x, y, 0xA9AEBC, true);
                    x += font.width("・");
                }
                g.drawString(font, row.get(i).text(), x, y, 0xFF000000 | row.get(i).rgb(), true);
                x += font.width(row.get(i).text());
            }
            y += 11;
        }
    }
}
