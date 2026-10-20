package jp.revati.skyabyss.client.hud;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.hud.HudLayout.Id;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

// バニラの HUD を空きへずらす (docs/design/18 の 14 章。mod の部品は動かさず、重なるバニラのほうを動かす)。
// ずらす量は HudLayout の計算 (Result.shiftY) が決める。ここは描画層ごとに縦へ平行移動するだけ:
//   効き目の絵 (EFFECTS)・ボスバー (BOSS_OVERLAY)・アクションバー (OVERLAY_MESSAGE)・持ち物の名前 (SELECTED_ITEM_NAME)・
//   字幕 (SUBTITLE_OVERLAY)・スコアボードの横板 (SCOREBOARD_SIDEBAR)・チャット (CHAT)。札 (トースト) は描画層ではないので ToastShiftMixin が同じ量を使う
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class VanillaShift {
    private VanillaShift() {
    }

    private static Id idOf(ResourceLocation layer) {
        if (VanillaGuiLayers.EFFECTS.equals(layer)) {
            return Id.V_EFFECTS;
        }
        if (VanillaGuiLayers.BOSS_OVERLAY.equals(layer)) {
            return Id.V_BOSS_BARS;
        }
        if (VanillaGuiLayers.OVERLAY_MESSAGE.equals(layer)) {
            return Id.V_ACTION_BAR;
        }
        if (VanillaGuiLayers.SELECTED_ITEM_NAME.equals(layer)) {
            return Id.V_ITEM_NAME;
        }
        if (VanillaGuiLayers.SUBTITLE_OVERLAY.equals(layer)) {
            return Id.V_SUBTITLES;
        }
        if (VanillaGuiLayers.SCOREBOARD_SIDEBAR.equals(layer)) {
            return Id.V_SIDEBAR;
        }
        if (VanillaGuiLayers.CHAT.equals(layer)) {
            return Id.V_CHAT;
        }
        return null;
    }

    // いまずらしている量 (HudManager の計算のもの。なければ 0)
    public static int dy(Id id) {
        Integer v = HudManager.result().shiftY.get(id);
        return v == null ? 0 : v;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void pre(RenderGuiLayerEvent.Pre event) {
        Id id = idOf(event.getName());
        if (id == null || event.isCanceled()) {
            return;
        }
        int dy = dy(id);
        if (dy != 0) {
            event.getGuiGraphics().pose().pushPose();
            event.getGuiGraphics().pose().translate(0, dy, 0);
            PUSHED.add(id);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void post(RenderGuiLayerEvent.Post event) {
        Id id = idOf(event.getName());
        if (id != null && PUSHED.remove(id)) {
            event.getGuiGraphics().pose().popPose();
        }
    }

    private static final java.util.EnumSet<Id> PUSHED = java.util.EnumSet.noneOf(Id.class);

    // 札 (トースト) 用: 描く前後に呼ぶ
    public static void beginToasts(GuiGraphics g) {
        int dy = dy(Id.V_TOASTS);
        if (dy != 0) {
            g.pose().pushPose();
            g.pose().translate(0, dy, 0);
        }
        toastShift = dy;
    }

    public static void endToasts(GuiGraphics g) {
        if (toastShift != 0) {
            g.pose().popPose();
        }
        toastShift = 0;
    }

    private static int toastShift;

    public static int toastShift() {
        return toastShift;
    }

    static boolean active() {
        return Minecraft.getInstance().player != null;
    }
}
