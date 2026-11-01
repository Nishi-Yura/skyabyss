package jp.revati.skyabyss.client.rebirth;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rebirth.RebirthConfig;
import jp.revati.skyabyss.rebirth.RebirthData;
import jp.revati.skyabyss.rebirth.RebirthTitles;
import jp.revati.skyabyss.rebirth.RebirthView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;

// 名札の称号 (その人が選んだ称号を、名前の上か下に一行で。位置はその人がプロフィールの画面で選ぶ: RebirthData.titlePos)。
//   転生していない人にも出す (称号は名声・熟練・図鑑などでも解ける)。名前より少し小さく、淡い金の字
//   出すのは名札が出るときだけ、24 マスまで。しゃがんでいる人は壁越しに透かさない (バニラの名札と同じ)。見る側の設定 (RebirthConfig.NAMETAG_TITLE) で切れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TitleTagClient {
    public static final int COLOR = 0xE3D29A;
    static final float SCALE = 0.8f;
    static final double RANGE = 24.0;
    // 一行ぶんの高さ (バニラの名札の行の間 9 × 1.15 を、字の大きさに合わせて)
    static final double LINE = 9 * 1.15 * 0.025 * 0.9;

    private TitleTagClient() {
    }

    // その人の名札の称号 (無い・出さない・解けていない なら null)
    @Nullable
    public static Component text(Player p) {
        RebirthData d = RebirthView.data(p);
        if (d.title().isEmpty() || d.titlePos() == RebirthData.TITLE_OFF) {
            return null;
        }
        RebirthTitles.Title t = RebirthTitles.byId(d.title());
        if (t == null || !RebirthTitles.has(d.unlocked(), t)) {
            return null;
        }
        return Component.translatable("title.skyabyss_rebirth." + t.id()).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(COLOR)));
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof Player p) || !RebirthConfig.on(RebirthConfig.NAMETAG_TITLE)) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.canRender().isFalse() || !shown(mc, p)) {
            return;
        }
        Component title = text(p);
        if (title == null) {
            return;
        }
        Vec3 at = p.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, p.getViewYRot(event.getPartialTick()));
        if (at == null) {
            return;
        }
        boolean above = RebirthView.data(p).titlePos() == RebirthData.TITLE_ABOVE;
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        ps.translate(at.x, at.y + 0.5 + (above ? LINE : -LINE), at.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.025f * SCALE;
        ps.scale(s, -s, s);
        Matrix4f m = ps.last().pose();
        Font font = mc.font;
        float x = -font.width(title) / 2f;
        int bg = (int) (mc.options.getBackgroundOpacity(0.25f) * 255f) << 24;
        boolean through = !p.isDiscrete();
        font.drawInBatch(title, x, 0, 0x20FFFFFF, false, m, event.getMultiBufferSource(), through ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, bg,
                event.getPackedLight());
        if (through) {
            font.drawInBatch(title, x, 0, 0xFFFFFFFF, false, m, event.getMultiBufferSource(), Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        }
        ps.popPose();
    }

    // 名札が出るか (バニラの LivingEntityRenderer.shouldShowName と同じ見方。称号は 24 マスまで)
    private static boolean shown(Minecraft mc, Player p) {
        if (p == mc.getCameraEntity() || p.isVehicle() || p.isInvisibleTo(mc.player) || !Minecraft.renderNames()) {
            return false;
        }
        double range = p.isDiscrete() ? Math.min(RANGE, 32) : RANGE;
        return mc.getEntityRenderDispatcher().distanceToSqr(p) < range * range;
    }
}
