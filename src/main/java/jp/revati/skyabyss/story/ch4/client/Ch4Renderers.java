package jp.revati.skyabyss.story.ch4.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.client.opening.SiteTargets;
import jp.revati.skyabyss.story.ch4.Ch4Ground;
import jp.revati.skyabyss.story.ch4.Ch4Setup;
import jp.revati.skyabyss.story.ch4.mob.AshDragger;
import jp.revati.skyabyss.titan.ember.mob.AshThrall;
import jp.revati.skyabyss.titan.ember.mob.EmberMob;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;

// 四章の地上の敵の描画 (素材は tools/gen/ch4/gen_ch4_ground_mobs.py) と、目的の帯の行き先 (SiteTargets.bind)。
//   熾り蟲・灰ひきずり: 低ポリ (GeckoLib の骨とアニメ + 骨ごとのメッシュ)。灰ひきずりは丸まっている間、脚を隠す (燠虫と同じ)
//   灰被りの屑・古い灰被り: 骨は灰被りのもの (geo/entity/ash_thrall_lowpoly)、メッシュとアトラスは自分のもの (lowpoly/<id>)。
//     殻のある姿と燃え上がった姿で、アトラス (<id> / <id>_flared) と見せる骨 (crust_* / flare_*) を替える (灰被りと同じ)。
//     アニメは屑 = 灰被りのもの、古い灰被り = 自分のもの (灰被り + 亀裂・熱の波)。大きさは描画の倍率 (屑 0.85、古い灰被り 1.15)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class Ch4Renderers {
    private Ch4Renderers() {
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        // 熾り蟲は小さいので 1.5 倍で描く (群れで床を這うのが暗がりでも見えるように)
        event.registerEntityRenderer(Ch4Setup.CINDER_MITE.get(), ctx -> new Mob<>(ctx, new LowPolyGeoRenderer.Model<>("cinder_mite"), "cinder_mite", 1.5f, 0.3f));
        event.registerEntityRenderer(Ch4Setup.ASH_DRAGGER.get(), DraggerRenderer::new);
        event.registerEntityRenderer(Ch4Setup.ASH_HUSK.get(), ctx -> new ThrallVariant<>(ctx, "ash_husk", "ash_thrall", "ash_husk_poker", 0.85f, 0.4f));
        event.registerEntityRenderer(Ch4Setup.ELDER_THRALL.get(), ctx -> new ThrallVariant<>(ctx, "elder_thrall", "elder_thrall", "elder_rake", 1.15f, 0.6f));
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // 四章の支度の段とサブ: 消えた炉の坑・灰の道の標・煤の窯の跡 (Ch4Ground が SiteNetwork.EXTRA_TARGETS で送る)
            SiteTargets.bind("ch4/forge_ruins/pit", Ch4Ground.PIT);
            SiteTargets.bind("ch4/ash_road/trace", Ch4Ground.MARK);
            SiteTargets.bind("ch4/dragger_trail/follow", Ch4Ground.KILN);
            SiteTargets.bind("main_ember/board", SiteTargets.TITAN_PREFIX + "ember");
        });
    }

    static class Mob<T extends EmberMob> extends LowPolyGeoRenderer<T> {
        Mob(EntityRendererProvider.Context ctx, String name, float shadow) {
            super(ctx, name, 1.0f);
            this.shadowRadius = shadow;
        }

        Mob(EntityRendererProvider.Context ctx, LowPolyGeoRenderer.Model<T> model, String mesh, float scale, float shadow) {
            super(ctx, model, t -> mesh, scale);
            this.shadowRadius = shadow;
        }

        @Override
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }
    }

    // ---------------------------------------------------------------- 灰ひきずり
    static final List<String> LEGS = legs();

    static List<String> legs() {
        List<String> out = new ArrayList<>();
        for (String s : List.of("l", "r")) {
            for (int i = 1; i <= 3; i++) {
                out.add("leg_" + s + i);
            }
        }
        return out;
    }

    static final class DraggerRenderer extends Mob<AshDragger> {
        DraggerRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, "ash_dragger", 0.4f);
        }

        @Override
        public void preRender(PoseStack poseStack, AshDragger dragger, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            boolean ball = dragger.isBall();
            for (String b : LEGS) {
                model.getBone(b).ifPresent(bone -> bone.setHidden(ball));
            }
            super.preRender(poseStack, dragger, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }

    // ---------------------------------------------------------------- 灰被りの変種
    static final List<String> CRUST = List.of("crust_body", "crust_head", "crust_arm_r", "crust_arm_l", "crust_fore_r", "crust_fore_l",
            "crust_leg_r", "crust_leg_l");
    static final List<String> FLARE = List.of("flare_body", "flare_head");

    static final class VariantModel<T extends AshThrall> extends LowPolyGeoRenderer.Model<T> {
        private final ResourceLocation lowGeo = id("geo/entity/ash_thrall_lowpoly.geo.json");
        private final ResourceLocation debugGeo = id("geo/entity/ash_thrall_lowpoly_debug.geo.json");
        private final ResourceLocation anim;
        private final ResourceLocation flaredLow;
        private final ResourceLocation flared;

        VariantModel(String name, String animName) {
            super(name);
            this.anim = id("animations/entity/" + animName + ".animation.json");
            this.flaredLow = id("textures/lowpoly/" + name + "_flared.png");
            this.flared = id("textures/entity/" + name + "_flared.png");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            if (LowPoly.enabled) {
                return LowPoly.debug ? debugGeo : lowGeo;
            }
            return super.getModelResource(animatable);
        }

        @Override
        public ResourceLocation getTextureResource(T thrall) {
            if (LowPoly.enabled && !LowPoly.debug) {
                return thrall.isFlared() ? flaredLow : super.getTextureResource(thrall);
            }
            return !LowPoly.enabled && thrall.isFlared() ? flared : super.getTextureResource(thrall);
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            return anim;
        }
    }

    static final class ThrallVariant<T extends AshThrall> extends Mob<T> {
        ThrallVariant(EntityRendererProvider.Context ctx, String name, String animName, String gear, float scale, float shadow) {
            super(ctx, new VariantModel<>(name, animName), name, scale, shadow);
            addRenderLayer(new GearLayer<>(this, "socket_main", "ash_poker", mob -> "idle").activeWhen(() -> !LowPoly.enabled));
            addRenderLayer(GearLayer.lowPoly(this, "socket_main", gear));
        }

        @Override
        public void preRender(PoseStack poseStack, T thrall, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
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
}
