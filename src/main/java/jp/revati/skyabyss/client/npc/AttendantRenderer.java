package jp.revati.skyabyss.client.npc;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.facility.AttendantEntity;
import jp.revati.skyabyss.facility.FacilitySetup;
import jp.revati.skyabyss.npc.NpcEntity;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 都の施設の係の人の描画 (docs/design/35 の 2 章)。形・絵・動きは役ごと (NpcModels: lowpoly/npc_<役>.json・textures/lowpoly/npc_<役>.png・
// animations/entity/npc_<役>.animation.json。tools/install/install_npc2.py)。頭は語り部と同じく相手の方を向く (NpcRenderer.Model。root を回していないので上下の符号だけ逆)
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
                // 役の持ち物 (受け口 prop_r / prop_l / prop_back に別のメッシュ)
                addRenderLayer(new NpcModels.PropLayer<>(this, e -> NpcModels.mesh(role(e))));
            }
        });
    }

    static String role(NpcEntity e) {
        return e instanceof AttendantEntity a ? a.roleId() : "guild_clerk";
    }

    static String meshName(NpcEntity e) {
        return NpcModels.mesh(role(e));
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
            return NpcModels.geo(role(entity));
        }

        @Override
        public ResourceLocation getTextureResource(NpcEntity entity) {
            return NpcModels.texture(role(entity));
        }

        // 持ち方に合わせた動き (名前は animation.attendant.*)
        @Override
        public ResourceLocation getAnimationResource(NpcEntity entity) {
            return NpcModels.animation(role(entity));
        }
    }
}
