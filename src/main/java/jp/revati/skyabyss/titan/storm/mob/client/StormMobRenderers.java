package jp.revati.skyabyss.titan.storm.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMob;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 雷翼の背の敵の描画。GeckoLib のモデル (geo/entity/<名前>、tools/install/install_storm_mobs.py) と、目・稲妻を暗闇で光らせる層。
// 羽守の鉤槍 (gear/hooked_glaive) と雷乞いの杖 (gear/supplicant_staff) は別モデルで、受け口 socket_main に付ける。
// 雷蛾は、帯電の割合だけ「帯電の光」(static_moth_charge.png) を足して描く (別のモデルは持たない。12 の 14.3節)。
// 死ぬときはバニラの「横に倒れる」回転をせず、モデルの死のアニメーションで倒れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormMobRenderers {
    private StormMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        // 低ポリ (client/lowpoly)。LowPoly.enabled = false で元のキューブの geo / GearLayer に戻る
        event.registerEntityRenderer(StormMobSetup.FEATHER_WARDEN.get(), ctx -> {
            MobRenderer<jp.revati.skyabyss.titan.storm.mob.FeatherWarden> r = new MobRenderer<>(ctx, "feather_warden", 0.45f);
            r.addRenderLayer(new GearLayer<>(r, "socket_main", "hooked_glaive", mob -> "idle").activeWhen(() -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "hooked_glaive"));
            return r;
        });
        event.registerEntityRenderer(StormMobSetup.STATIC_MOTH.get(), ctx -> {
            MobRenderer<StaticMoth> r = new MobRenderer<>(ctx, "static_moth", 0.3f);
            r.addRenderLayer(new ChargeLayer(r));
            return r;
        });
        event.registerEntityRenderer(StormMobSetup.THUNDER_SUPPLICANT.get(), ctx -> {
            MobRenderer<jp.revati.skyabyss.titan.storm.mob.ThunderSupplicant> r = new MobRenderer<>(ctx, "thunder_supplicant", 0.4f);
            r.addRenderLayer(new GearLayer<>(r, "socket_main", "supplicant_staff", mob -> "idle").activeWhen(() -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "supplicant_staff"));
            return r;
        });
    }

    static class MobRenderer<T extends StormMob> extends LowPolyGeoRenderer<T> {
        MobRenderer(EntityRendererProvider.Context ctx, String name, float shadow) {
            super(ctx, name, 1.0f);
            this.shadowRadius = shadow;
        }

        @Override
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }
    }

    // 雷蛾の帯電: 足す光を、帯電の割合の明るさで重ねる (RenderType.eyes は足し合わせるので、色の明るさがそのまま割合になる)
    static final class ChargeLayer extends GeoRenderLayer<StaticMoth> {
        private static final RenderType TYPE = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/static_moth_charge.png"));
        // 低ポリのアトラスの翅の面を明るくした絵 (tools/install/install_lowpoly_storm.py)
        private static final RenderType TYPE_LOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/static_moth_charge.png"));

        ChargeLayer(GeoRenderer<StaticMoth> renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, StaticMoth moth, BakedGeoModel model, RenderType renderType, MultiBufferSource buffers,
                           VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            float c = moth.getCharge();
            if (c <= 0.01f) {
                return;
            }
            // 満ちたら少し瞬く
            if (c >= 0.999f) {
                c = 0.8f + 0.2f * (float) Math.sin((moth.tickCount + partialTick) * 1.3f);
            }
            int v = Math.round(255 * c);
            int colour = 0xFF000000 | (v << 16) | (v << 8) | v;
            RenderType type = LowPoly.enabled ? TYPE_LOW : TYPE;
            getRenderer().reRender(model, poseStack, buffers, moth, type, buffers.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT,
                    packedOverlay, colour);
        }
    }
}
