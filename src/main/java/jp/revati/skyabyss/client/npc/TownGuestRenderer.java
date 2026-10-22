package jp.revati.skyabyss.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.npc.NpcEntity;
import jp.revati.skyabyss.town.TownGuestEntity;
import jp.revati.skyabyss.town.TownGuests;
import jp.revati.skyabyss.town.TownSetup;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 町のクエストの限定の人の描画 (docs/design/35 の 3.5)。形は係の人の案 A (lowpoly/attendant_a.json・geo attendant_a_lowpoly)、
// 絵は人ごと (textures/lowpoly/guest_<id>.png。tools/gen/mobs/gen_town_guests.py)。動きは animations/entity/town_guest.animation.json
// (係の人のものの写し)。子どもは TownGuests.Guest.scale で小さく描く
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TownGuestRenderer {
    static final String MESH = "attendant_a";

    private TownGuestRenderer() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TownSetup.GUEST.get(), ctx -> new LowPolyGeoRenderer<NpcEntity>(ctx, new Model(), e -> MESH, 1.0f) {
            {
                this.shadowRadius = 0.42f;
            }

            @Override
            public void render(NpcEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
                float s = scale(entity);
                if (s == 1f) {
                    super.render(entity, yaw, partialTick, pose, buffers, light);
                    return;
                }
                pose.pushPose();
                pose.scale(s, s, s);
                super.render(entity, yaw, partialTick, pose, buffers, light);
                pose.popPose();
            }
        });
    }

    static float scale(NpcEntity entity) {
        if (entity instanceof TownGuestEntity g) {
            TownGuests.Guest def = TownGuests.byId(g.guestId());
            return def == null ? 1f : def.scale();
        }
        return 1f;
    }

    static final class Model extends NpcRenderer.Model {
        Model() {
            super("town_guest");
        }

        // 係の人と同じく geo の root を回していないので、頭の上下の符号を戻す
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
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + MESH + "_lowpoly.geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(NpcEntity entity) {
            String id = entity instanceof TownGuestEntity g ? g.guestId() : "apprentice";
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/guest_" + id + ".png");
        }
    }
}
