package jp.revati.skyabyss.story.ch5.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.story.ch5.warden.HeartWarden;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.effect.MobEffects;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.util.Color;

// 心臓の番人の描画 (docs/design/36 の 6.6)。見えるのは光の中だけ。
//   LIT (灯籠の 7 以内か、光の術の灯り)      体を全部描く (本当の番人は明るい色、光る心臓と発光マスク)
//   輪郭だけ (GLOWING、光の外)               体を黒く沈めて描く。周りの白い輪郭は、効きの描画 (鼓動の術) が付ける
//   闇 (それ以外)                            体は描かず、胸の心臓の骨だけを暗く描く (ほのかな核の光。発光は切る)
//   分身 (DECOY)                             同じ形を暗く描く。発光は切る (鼓動の光がない)
// 骨の隠し方は見る者 (AbyssMobsBRenderers の Foe) と同じ: 描かない骨は setHidden で隠す。
public final class WardenRenderer extends LowPolyGeoRenderer<HeartWarden> {
    static final String NAME = "heart_warden";
    // 描く大きさ (2026-10-28「もうすこし大きくていい」。当たりの箱は WardenSetup で同じ割合に)
    static final float SCALE = 1.35f;
    private static final String[] BONES = {"root", "body", "ribs", "heart", "head", "arm_l", "arm_r", "leg_l", "leg_r"};
    private static final String HEART = "heart";

    // 今描いている分で決める (preRender で決め、描画の途中の autoGlow が読む)
    private boolean glowOn;

    public WardenRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new LowPolyGeoRenderer.Model<HeartWarden>(NAME), e -> NAME, SCALE);
        this.shadowRadius = 1.2f;
    }

    private static boolean dark(HeartWarden w) {
        return !w.lit() && !w.hasEffect(MobEffects.GLOWING);
    }

    @Override
    public void preRender(PoseStack poseStack, HeartWarden w, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
            boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        boolean dark = dark(w);
        glowOn = !w.decoy() && !dark;
        for (String b : BONES) {
            // 闇では心臓の骨だけ残す
            boolean hide = dark && !b.equals(HEART);
            model.getBone(b).ifPresent(bone -> bone.setHidden(hide));
        }
        shadowRadius = dark ? 0f : 0.9f;
        super.preRender(poseStack, w, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    protected boolean autoGlow() {
        return glowOn;
    }

    @Override
    public Color getRenderColor(HeartWarden w, float partialTick, int packedLight) {
        if (w.decoy()) {
            return dark(w) ? Color.ofRGB(24, 24, 30) : Color.ofRGB(120, 120, 130);
        }
        if (dark(w)) {
            return Color.ofRGB(40, 40, 46);
        }
        if (!w.lit()) {
            return Color.ofRGB(26, 26, 32);
        }
        return Color.ofRGB(255, 255, 255);
    }
}
