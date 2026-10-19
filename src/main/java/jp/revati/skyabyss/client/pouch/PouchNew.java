package jp.revati.skyabyss.client.pouch;

import java.util.HashSet;
import java.util.Set;

import jp.revati.skyabyss.pouch.Pouch;
import jp.revati.skyabyss.pouch.PouchData;
import jp.revati.skyabyss.pouch.PouchNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

// 旅嚢の "NEW" の印 (docs/design/22 の 9.4): 新しく袋に入った品で、まだ画面で見ていないものに付く小さな札。
// 印は持ち主ごとに保存・同期される (PouchData.fresh)。枠にカーソルを重ねる・輪や一枚札で見るとサーバーへ「見た」を送って外す。
public final class PouchNew {
    private static final int BG = 0xFFC0392B;
    private static final int FG = 0xFFFFF4E0;
    // 「見た」を送った品 (同じ品へ毎フレーム送らない。印が外れたら忘れる)
    private static final Set<ResourceLocation> SENT = new HashSet<>();

    private PouchNew() {
    }

    public static boolean isNew(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && Pouch.data(mc.player).isFresh(id);
    }

    public static boolean any() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && Pouch.data(mc.player).anyFresh();
    }

    // 見た: 印を外すようサーバーへ (印が付いていなければ何もしない)
    public static void viewed(ResourceLocation id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        PouchData d = Pouch.data(mc.player);
        if (!d.isFresh(id)) {
            SENT.remove(id);
            return;
        }
        if (SENT.add(id)) {
            PacketDistributor.sendToServer(new PouchNet.Viewed(id));
        }
    }

    // 札を描く。(x, y) は品の絵の左上 (16 画素の枠)。右上の角に重ねる
    public static void badge(GuiGraphics g, Font font, int x, int y) {
        Component label = Component.translatable("skyabyss_pouch.badge.new");
        float scale = 0.6f;
        int w = Math.round(font.width(label) * scale) + 3;
        int h = Math.round(8 * scale) + 2;
        int bx = x + 17 - w;
        int by = y - 2;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(bx, by, bx + w, by + h, BG);
        g.pose().translate(bx + 1.5f, by + 1.2f, 0);
        g.pose().scale(scale, scale, 1f);
        g.drawString(font, label, 0, 0, FG, false);
        g.pose().popPose();
    }
}
