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
                e -> "attendant_" + AttendantLook.look(e.variant()), 1.0f) {
            {
                this.shadowRadius = 0.42f;
            }
        });
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
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/attendant_" + AttendantLook.look(entity.variant()) + "_lowpoly.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(NpcEntity entity) {
            String role = entity instanceof AttendantEntity a ? a.roleId() : "guild_clerk";
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + AttendantLook.texture(entity.variant(), role) + ".png");
        }
    }
}
