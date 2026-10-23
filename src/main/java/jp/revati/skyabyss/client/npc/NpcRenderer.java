package jp.revati.skyabyss.client.npc;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.npc.ArtisanLook;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.npc.NpcSetup;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;

// 語り部と鯨追いの描画 (GeckoLib + 低ポリのメッシュ)。光る画素が無いアトラスは発光層を描かない (LowPolyGeoRenderer.GlowLayer がマスクの有無で決める)。
// 頭は相手の方を向く。geo の root 骨が yaw 180 度回してあるので、上下の向き (pitch) は符号を反転して入れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class NpcRenderer {
    // 頭の向きの符号 (ゲームで確かめて、逆なら -1 を 1 に)
    static final float PITCH_SIGN = -1f;
    static final float YAW_SIGN = 1f;

    private NpcRenderer() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcSetup.STORYTELLER.get(), ctx -> create(ctx, "storyteller", 0.38f));
        event.registerEntityRenderer(NpcSetup.WHALE_CHASER.get(), ctx -> create(ctx, "whale_chaser", 0.42f));
        // 旅の職人: 形は一つ、章 (variant 1〜4) ごとに絵 (textures/lowpoly/artisan_ch<n>.png) が替わる
        event.registerEntityRenderer(NpcSetup.ARTISAN.get(), ctx -> new LowPolyGeoRenderer<>(ctx, new ArtisanModel(), e -> NpcModels.has("artisan") ? NpcModels.mesh("artisan") : "artisan", 1.0f) {
            {
                this.shadowRadius = 0.42f;
                // v2 (NpcModels): 右手の槌は別のメッシュ
                addRenderLayer(new NpcModels.PropLayer<>(this, e -> NpcModels.has("artisan") ? NpcModels.mesh("artisan") : null));
            }
        });
    }

    // 職人の装いの絵 (章 1〜4。範囲外は一章)
    public static ResourceLocation artisanTexture(int variant) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + ArtisanLook.texture(variant) + ".png");
    }

    static final class ArtisanModel extends Model {
        ArtisanModel() {
            super("artisan");
        }

        // 職人の geo は root を回していない (語り部は yaw 180。180 だと後ろ向きだった)。親が回っていない分、頭の上下の符号を戻す
        @Override
        public void setCustomAnimations(NpcEntity entity, long instanceId, software.bernie.geckolib.animation.AnimationState<NpcEntity> state) {
            super.setCustomAnimations(entity, instanceId, state);
            software.bernie.geckolib.cache.object.GeoBone head = getAnimationProcessor().getBone("head");
            if (head != null) {
                head.setRotX(-head.getRotX());
            }
        }

        // 職人には元の滑らかな版 (cube の geo) が無いので、LowPoly.enabled = false でも低ポリの骨と絵のまま (メッシュは描かれず、消えるだけで落ちない)
        @Override
        public ResourceLocation getModelResource(NpcEntity entity) {
            if (NpcModels.has("artisan")) {
                return NpcModels.geo("artisan");
            }
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/artisan_lowpoly.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(NpcEntity entity) {
            if (NpcModels.has("artisan")) {
                // 章ごとの絵 (npc_artisan_ch<n>.png。形は一つ)
                return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + NpcModels.mesh("artisan") + "_"
                        + ArtisanLook.texture(entity.variant()).substring("artisan_".length()) + ".png");
            }
            return artisanTexture(entity.variant());
        }

        @Override
        public ResourceLocation getAnimationResource(NpcEntity entity) {
            return NpcModels.has("artisan") ? NpcModels.animation("artisan") : super.getAnimationResource(entity);
        }
    }

    // 低ポリ (client/lowpoly): LowPoly.enabled = false で元の滑らかなボクセルの geo (geo/entity/<name>.geo.json) に戻る
    private static LowPolyGeoRenderer<NpcEntity> create(EntityRendererProvider.Context ctx, String name, float shadow) {
        return new LowPolyGeoRenderer<>(ctx, new Model(name), e -> name, 1.0f) {
            {
                this.shadowRadius = shadow;
            }
        };
    }

    static class Model extends LowPolyGeoRenderer.Model<NpcEntity> {
        Model(String name) {
            super(name, false);
        }

        @Override
        public void setCustomAnimations(NpcEntity entity, long instanceId, AnimationState<NpcEntity> state) {
            super.setCustomAnimations(entity, instanceId, state);
            GeoBone head = getAnimationProcessor().getBone("head");
            EntityModelData data = state.getData(DataTickets.ENTITY_MODEL_DATA);
            if (head == null || data == null) {
                return;
            }
            float pitch = Mth.clamp(data.headPitch(), -40f, 40f);
            float yaw = Mth.clamp(data.netHeadYaw(), -70f, 70f);
            // 話している間は、ゆっくり小さくうなずく
            float nod = entity.isTalking() ? Mth.sin((entity.tickCount + state.getPartialTick()) * 0.18f) * 3f : 0f;
            head.setRotX(PITCH_SIGN * (pitch + nod) * Mth.DEG_TO_RAD);
            head.setRotY(YAW_SIGN * yaw * Mth.DEG_TO_RAD);
        }
    }
}
