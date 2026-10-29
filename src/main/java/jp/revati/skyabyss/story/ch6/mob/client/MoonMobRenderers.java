package jp.revati.skyabyss.story.ch6.mob.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.story.ch6.mob.CliffCreeper;
import jp.revati.skyabyss.story.ch6.mob.DustCrawler;
import jp.revati.skyabyss.story.ch6.mob.MoonLeaper;
import jp.revati.skyabyss.story.ch6.mob.MoonScale;
import jp.revati.skyabyss.story.ch6.mob.PushStone;
import jp.revati.skyabyss.story.ch6.mob.MoonMob;
import jp.revati.skyabyss.story.ch6.mob.MoonMobsSetup;
import jp.revati.skyabyss.story.ch6.mob.ShadowEmbracer;
import jp.revati.skyabyss.story.ch6.mob.ShadowStitcher;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;

// 新月の谷・上弦の崖の敵と主の描画 (素材は tools/gen/ch6/gen_moon_mobs.py)。姿の段 (1〜3) で絵を替える: <id>・<id>_s1・<id>_s2。
//   砂這い     潜っている間は体ごと砂の高さへ下げる (甲羅だけが出る)
//   影の綴じ手 光の外では空ろな輪の頭 (と星の目) だけを描く
//   影抱き     影に潜っている間は描かない (床を走る影の粒だけ)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MoonMobRenderers {
    private static final ResourceLocation DEBUG = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
    private static final String[] STAGE_SUFFIX = {"", "_s1", "_s2"};
    private static final String[] STITCHER_BODY = {"body", "arm_l", "arm_r"};

    private MoonMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MoonMobsSetup.DUST_CRAWLER.get(), ctx -> new Foe<>(ctx, DustCrawler.ID, 0.5f));
        event.registerEntityRenderer(MoonMobsSetup.SHELL_HARE.get(), ctx -> new Foe<>(ctx, "shell_hare", 0.35f));
        event.registerEntityRenderer(MoonMobsSetup.SHADOW_STITCHER.get(), ctx -> new Foe<>(ctx, ShadowStitcher.ID, 0.3f));
        event.registerEntityRenderer(MoonMobsSetup.SHADOW_EMBRACER.get(), ctx -> new Foe<>(ctx, ShadowEmbracer.ID, 1.1f));
        event.registerEntityRenderer(MoonMobsSetup.MOON_LEAPER.get(), ctx -> new Foe<>(ctx, MoonLeaper.ID, 0.4f));
        event.registerEntityRenderer(MoonMobsSetup.PUSH_STONE.get(), ctx -> new Foe<>(ctx, PushStone.ID, 0.0f));
        event.registerEntityRenderer(MoonMobsSetup.CLIFF_CREEPER.get(), ctx -> new Foe<>(ctx, CliffCreeper.ID, 0.45f));
        event.registerEntityRenderer(MoonMobsSetup.MOON_SCALE.get(), ctx -> new Foe<>(ctx, MoonScale.ID, 1.2f, 1.35f));
    }

    static final class Model<T extends MoonMob> extends LowPolyGeoRenderer.Model<T> {
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

    static final class Foe<T extends MoonMob> extends LowPolyGeoRenderer<T> {
        private final float shadow;

        Foe(EntityRendererProvider.Context ctx, String name, float shadow) {
            this(ctx, name, shadow, 1.0f);
        }

        Foe(EntityRendererProvider.Context ctx, String name, float shadow, float scale) {
            super(ctx, new MoonMobRenderers.Model<>(name), e -> name, scale);
            this.shadowRadius = shadow;
            this.shadow = shadow;
        }

        @Override
        public void render(T e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
            if (e instanceof ShadowEmbracer lord && lord.sunk()) {
                return;
            }
            super.render(e, yaw, pt, pose, buf, light);
        }

        @Override
        public void preRender(PoseStack poseStack, T mob, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            if (mob instanceof CliffCreeper cc) {
                // 背の岩を外して落としたあとは、岩が戻るまで背は空
                model.getBone("rock").ifPresent(bone -> bone.setHidden(cc.rockless()));
            }
            if (mob instanceof DustCrawler c && c.buried() && !isReRender) {
                poseStack.translate(0, -5.5 / 16.0, 0);
            }
            if (mob instanceof ShadowStitcher s) {
                boolean shown = s.exposed();
                for (String b : STITCHER_BODY) {
                    model.getBone(b).ifPresent(bone -> {
                        bone.setHidden(!shown);
                        // 体の骨を隠しても、子の頭 (輪と星の目) は描く
                        bone.setChildrenHidden(false);
                    });
                }
                shadowRadius = shown ? shadow : 0f;
            }
            super.preRender(poseStack, mob, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }
}
