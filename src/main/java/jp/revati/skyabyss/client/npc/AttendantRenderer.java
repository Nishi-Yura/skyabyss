package jp.revati.skyabyss.client.npc;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.facility.AttendantEntity;
import jp.revati.skyabyss.facility.AttendantLook;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 都の施設の係の人の描画 (docs/design/35 の 2 章)。形は見た目の案ごとに一つ (lowpoly/attendant_<案>.json、骨は旅の職人と同じ)、
// 絵は役ごと (textures/lowpoly/attendant_<案>_<役>.png)。案は NpcEntity.variant (0 = 既定の案 AttendantLook.DEFAULT、1〜3 = a〜c)。
// 動きは animations/entity/attendant.animation.json、頭は語り部と同じく相手の方を向く (NpcRenderer.Model。root を回していないので上下の符号だけ逆)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AttendantRenderer {
    private AttendantRenderer() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(FacilitySetup.ATTENDANT.get(), ctx -> new LowPolyGeoRenderer<NpcEntity>(ctx, new Model(),
                AttendantRenderer::meshName, 1.0f) {
            {
                this.shadowRadius = 0.42f;
                // 役の持ち物 (v2 の人だけ。受け口 prop_r / prop_l / prop_back に別のメッシュ)
                addRenderLayer(new NpcModels.PropLayer<>(this, e -> NpcModels.has(role(e)) ? NpcModels.mesh(role(e)) : null));
            }
        });
    }

    static String role(NpcEntity e) {
        return e instanceof AttendantEntity a ? a.roleId() : "guild_clerk";
    }

    // v2 (人ごとの形。NpcModels) が入っている役はその形、無ければ前の作り (見た目の案ごとの共通の形)
    static String meshName(NpcEntity e) {
        String r = role(e);
        return NpcModels.has(r) ? NpcModels.mesh(r) : "attendant_" + AttendantLook.look(e.variant());
    }

    static final class Model extends NpcRenderer.Model {
        Model() {
            super("attendant");
        }

        // 係の人の geo は root を回していない (語り部は yaw 180)。親が回っていない分、頭の上下の向きの符号を戻す
        @Override
        public void setCustomAnimations(NpcEntity entity, long instanceId, software.bernie.geckolib.animation.AnimationState<NpcEntity> state) {
            super.setCustomAnimations(entity, instanceId, state);
            software.bernie.geckolib.cache.object.GeoBone head = getAnimationProcessor().getBone("head");
            if (head != null) {
                head.setRotX(-head.getRotX());
            }
        }

        @Override
        public ResourceLocation getModelResource(NpcEntity entity) {
            String r = role(entity);
            if (NpcModels.has(r)) {
                return NpcModels.geo(r);
            }
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/attendant_" + AttendantLook.look(entity.variant()) + "_lowpoly.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(NpcEntity entity) {
            String r = role(entity);
            if (NpcModels.has(r)) {
                return NpcModels.texture(r);
            }
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + AttendantLook.texture(entity.variant(), r) + ".png");
        }

        // v2 の人は持ち方に合わせた動き (animations/entity/npc_<役>.animation.json。名前は animation.attendant.*)
        @Override
        public ResourceLocation getAnimationResource(NpcEntity entity) {
            String r = role(entity);
            return NpcModels.has(r) ? NpcModels.animation(r) : super.getAnimationResource(entity);
        }
    }
}
