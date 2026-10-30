package jp.revati.skyabyss.story.finale.mob.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.story.finale.mob.BellMourner;
import jp.revati.skyabyss.story.finale.mob.CloudMob;
import jp.revati.skyabyss.story.finale.mob.CloudMobsSetup;
import jp.revati.skyabyss.story.finale.mob.LastBellkeeper;
import jp.revati.skyabyss.story.finale.mob.MistBird;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 雲海の敵と主の描画 (素材は tools/gen/finale/gen_cloud_mobs.py)。姿の段 (1〜3) で絵を替える: <id>・<id>_s1・<id>_s2
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CloudMobRenderers {
    private static final ResourceLocation DEBUG = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
    private static final String[] STAGE_SUFFIX = {"", "_s1", "_s2"};

    private CloudMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(CloudMobsSetup.BELL_MOURNER.get(), ctx -> new Foe<>(ctx, BellMourner.ID, 0.45f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.MIST_BIRD.get(), ctx -> new Foe<>(ctx, MistBird.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.LAST_BELLKEEPER.get(), ctx -> new Foe<>(ctx, LastBellkeeper.ID, 1.1f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.NET_CASTER.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.NetCaster.ID, 0.45f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.LAMP_MOTH.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.LampMoth.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.LAMP_EYE.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.LampEye.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.ASH_STRIKER.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.AshStriker.ID, 0.5f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.BACKFIRE.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.Backfire.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.REWINDER.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.Rewinder.ID, 0.9f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.FALLEN_PLUME.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.FallenPlume.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.PLUME_WARDEN.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.PlumeWarden.ID, 0.5f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.PILGRIM_PLUME.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.PilgrimPlume.ID, 0.0f, 1.0f));
        event.registerEntityRenderer(CloudMobsSetup.PILGRIM_HAND.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.PilgrimHand.ID, 0.0f, 1.6f));
        event.registerEntityRenderer(CloudMobsSetup.PILGRIM_EYE.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.PilgrimEye.ID, 0.0f, 1.5f));
        event.registerEntityRenderer(CloudMobsSetup.PILGRIM_MOON.get(), ctx -> new Foe<>(ctx, jp.revati.skyabyss.story.finale.mob.PilgrimMoon.ID, 0.0f, 1.4f));
    }

    static final class Model<T extends CloudMob> extends LowPolyGeoRenderer.Model<T> {
        private final String name;

        Model(String name) {
            super(name);
            this.name = name;
        }

        @Override
        public ResourceLocation getTextureResource(T mob) {
            ResourceLocation low = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID,
                    "textures/lowpoly/" + name + STAGE_SUFFIX[Math.max(0, Math.min(2, mob.stage() - 1))] + ".png");
            if (LowPoly.enabled) {
                return LowPoly.debug ? DEBUG : low;
            }
            return LowPoly.cubeOr(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/" + name + ".png"), low);
        }
    }

    static final class Foe<T extends CloudMob> extends LowPolyGeoRenderer<T> {
        Foe(EntityRendererProvider.Context ctx, String name, float shadow, float scale) {
            super(ctx, new CloudMobRenderers.Model<>(name), e -> name, scale);
            this.shadowRadius = shadow;
        }
    }
}
