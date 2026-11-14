package jp.revati.skyabyss.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.combat.prop.AttackProp;
import jp.revati.skyabyss.combat.prop.AttackProps;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 技の形 (combat/prop/AttackProp) の描画: 低ポリの模型 prop_<種類> を、大きさ (size) を掛けて描く。
// RISE は床から突き出る分だけ下へずらし (床の下は床に隠れる)、TOSS は進む向きへ頭を傾け、FALL の待ちの間は描かない
public class AttackPropRenderer extends LowPolyGeoRenderer<AttackProp> {
    // 突き出る深さ (ブロック、size 1 のとき)。模型の高さにおおよそ合わせる
    private static final float RISE_DEPTH = 3.6f;

    public AttackPropRenderer(EntityRendererProvider.Context ctx, String kind) {
        super(ctx, "prop_" + kind, 1f);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(AttackProp e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.hidden(partial)) {
            return;
        }
        pose.pushPose();
        float s = e.size();
        if (e.mode() == AttackProp.RISE) {
            pose.translate(0, -(1f - e.rise(partial)) * RISE_DEPTH * s, 0);
        }
        pose.scale(s, s, s);
        super.render(e, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    @Override
    protected void applyRotations(AttackProp e, PoseStack pose, float age, float yaw, float partial, float nativeScale) {
        super.applyRotations(e, pose, age, yaw, partial, nativeScale);
        float pitch = e.pitch(partial);
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (var en : AttackProps.TYPES.entrySet()) {
                String kind = en.getKey();
                event.registerEntityRenderer(en.getValue().get(), ctx -> new AttackPropRenderer(ctx, kind));
            }
        }
    }
}
