package jp.revati.skyabyss.titan.ember.mob.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.CinderRoller;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import jp.revati.skyabyss.titan.ember.mob.EmberMobSetup;
import jp.revati.skyabyss.titan.ember.mob.EmberMoth;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 余燼の背の敵の描画 (素材は tools/install_ember_mobs.py)。GeckoLib のモデルと、熾・割れ目・目を暗闇で光らせる層。
// 灰被り: 殻のある姿と燃え上がった姿で、テクスチャ (ash_thrall / ash_thrall_flared) と見せる骨 (crust_* / flare_*) を替える。
//         火掻き棒は別のモデル (geo/gear/ash_poker) を受け口 socket_main に付ける。
// 燠虫: 丸まっている間は脚・触角・尾肢・節の間の膜を隠す (玉の内側にしまう)。
// 死ぬときはバニラの「横に倒れる」回転をせず、モデルの死のアニメーションで倒れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EmberMobRenderers {
    private EmberMobRenderers() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EmberMobSetup.ASH_THRALL.get(), ThrallRenderer::new);
        event.registerEntityRenderer(EmberMobSetup.EMBER_MOTH.get(), ctx -> new MobRenderer<EmberMoth>(ctx, EmberIds.EMBER_MOTH, 0.3f));
        event.registerEntityRenderer(EmberMobSetup.CINDER_ROLLER.get(), RollerRenderer::new);
    }

    static class MobRenderer<T extends EmberMob> extends GeoEntityRenderer<T> {
        MobRenderer(EntityRendererProvider.Context ctx, String name, float shadow) {
            this(ctx, new DefaultedEntityGeoModel<T>(id(name), true), shadow);
        }

        MobRenderer(EntityRendererProvider.Context ctx, DefaultedEntityGeoModel<T> model, float shadow) {
            super(ctx, model);
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = shadow;
        }

        @Override
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }
    }

    // ---------------------------------------------------------------- 灰被り

    static final List<String> CRUST = List.of("crust_body", "crust_head", "crust_arm_r", "crust_arm_l", "crust_fore_r", "crust_fore_l",
            "crust_leg_r", "crust_leg_l");
    static final List<String> FLARE = List.of("flare_body", "flare_head");

    static final class ThrallModel extends DefaultedEntityGeoModel<AshThrall> {
        private static final ResourceLocation FLARED = id("textures/entity/ash_thrall_flared.png");

        ThrallModel() {
            super(id(EmberIds.ASH_THRALL), true);
        }

        @Override
        public ResourceLocation getTextureResource(AshThrall thrall) {
            return thrall.isFlared() ? FLARED : super.getTextureResource(thrall);
        }
    }

    static final class ThrallRenderer extends MobRenderer<AshThrall> {
        ThrallRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new ThrallModel(), 0.45f);
            addRenderLayer(new GearLayer<>(this, "socket_main", "ash_poker", mob -> "idle"));
        }

        @Override
        public void preRender(PoseStack poseStack, AshThrall thrall, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            // モデルは全個体で共有なので、描くたびに決める
            boolean flared = thrall.isFlared();
            for (String b : CRUST) {
                model.getBone(b).ifPresent(bone -> bone.setHidden(flared));
            }
            for (String b : FLARE) {
                model.getBone(b).ifPresent(bone -> bone.setHidden(!flared));
            }
            super.preRender(poseStack, thrall, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }

    // ---------------------------------------------------------------- 燠虫

    static final List<String> LIMBS = limbs();

    private static List<String> limbs() {
        List<String> out = new ArrayList<>();
        for (int k = 1; k < 8; k++) {
            out.add("leg" + k + "_r");
            out.add("leg" + k + "_l");
        }
        for (int k = 0; k < 8; k++) {
            out.add("mem" + k);
        }
        out.add("antenna_r");
        out.add("antenna_l");
        out.add("uropods");
        return out;
    }

    static final class RollerRenderer extends MobRenderer<CinderRoller> {
        RollerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, EmberIds.CINDER_ROLLER, 0.4f);
        }

        @Override
        public void preRender(PoseStack poseStack, CinderRoller roller, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            boolean ball = roller.isBall();
            for (String b : LIMBS) {
                model.getBone(b).ifPresent(bone -> bone.setHidden(ball));
            }
            super.preRender(poseStack, roller, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }
}
