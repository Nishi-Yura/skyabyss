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

// 旅袋の "NEW" の印 (docs/design/22 の 9.4): 新しく袋に入った品で、まだ画面で見ていないものに付く小さな札。
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
    // 2026-11-04 (docs/design/56): 0.6 倍の「NEW」の字は画素がつぶれて読めなかったので、字のない小さな菱形の印にした (色は札と同じ)
    public static void badge(GuiGraphics g, Font font, int x, int y) {
        int cx = x + 14;
        int cy = y + 1;
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        for (int d = -3; d <= 3; d++) {
            int half = 3 - Math.abs(d);
            g.fill(cx - half - 1, cy + d, cx + half + 2, cy + d + 1, 0xFF0A0C10);
        }
        for (int d = -2; d <= 2; d++) {
            int half = 2 - Math.abs(d);
            g.fill(cx - half, cy + d, cx + half + 1, cy + d + 1, BG | 0xFF000000);
        }
        g.fill(cx, cy - 1, cx + 1, cy, FG | 0xFF000000);
        g.pose().popPose();
    }
}
