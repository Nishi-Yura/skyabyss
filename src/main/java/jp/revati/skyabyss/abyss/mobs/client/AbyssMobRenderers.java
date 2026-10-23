package jp.revati.skyabyss.abyss.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.boss.client.BossRender;
import jp.revati.skyabyss.abyss.mobs.AbyssMob;
import jp.revati.skyabyss.abyss.mobs.AbyssMobKind;
import jp.revati.skyabyss.abyss.mobs.AbyssMobsA;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 深淵の敵 A の描画 (素材は tools/gen/abyss/gen_abyss_mobs_a.py)。段ごとに別のメッシュ・アトラス・発光マスク (abyss_<id>_s<段>)。
// 骨格は三段とも同じ (モデルの名前は段 1 のもの。段ごとのファイルはモデルの差し替えで選ぶ)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssMobRenderers {
    private AbyssMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        for (AbyssMobKind k : AbyssMobKind.values()) {
            event.registerEntityRenderer(AbyssMobsA.type(k).get(), ctx -> new Renderer(ctx, k));
        }
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    static final class Renderer extends LowPolyGeoRenderer<AbyssMob> {
        Renderer(EntityRendererProvider.Context ctx, AbyssMobKind kind) {
            super(ctx, new StageModel(kind), m -> BossRender.look(m) != 0 ? BossRender.mesh(BossRender.look(m)) : stemOf(kind, m.stage()), 1.0f);
            this.shadowRadius = kind.width * 0.6f;
        }

        // 層の主・歪んだ王 (BossLook) は、模型の作りの大きさを戻してから描く
        @Override
        public void render(AbyssMob e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
            if (BossRender.look(e) == 0) {
                super.render(e, yaw, pt, pose, buf, light);
                return;
            }
            pose.pushPose();
            BossRender.undoAuthorScale(pose, e);
            super.render(e, yaw, pt, pose, buf, light);
            pose.popPose();
        }

        @Override
        protected float getDeathMaxRotation(AbyssMob animatable) {
            return 0f;
        }
    }

    // 段ごとのファイル名 (モデルとアトラス)。段 1 の名前を基にする
    static String stemOf(AbyssMobKind kind, int stage) {
        return "abyss_" + kind.id + "_s" + Math.max(1, Math.min(3, stage));
    }

    static final class StageModel extends LowPolyGeoRenderer.Model<AbyssMob> {
        private final AbyssMobKind kind;

        StageModel(AbyssMobKind kind) {
            super(stemOf(kind, 1));
            this.kind = kind;
        }

        @Override
        public ResourceLocation getAnimationResource(AbyssMob a) {
            int look = BossRender.look(a);
            return look != 0 ? BossRender.animation(look) : super.getAnimationResource(a);
        }

        @Override
        public ResourceLocation getModelResource(AbyssMob a) {
            int look = BossRender.look(a);
            if (look != 0) {
                return BossRender.geo(look);
            }
            if (!LowPoly.enabled) {
                return super.getModelResource(a);
            }
            return id("geo/entity/" + stemOf(kind, a.stage()) + (LowPoly.debug ? "_lowpoly_debug" : "_lowpoly") + ".geo.json");
        }

        @Override
        public ResourceLocation getTextureResource(AbyssMob a) {
            int look = BossRender.look(a);
            if (look != 0) {
                return LowPoly.debug ? super.getTextureResource(a) : BossRender.texture(look);
            }
            if (!LowPoly.enabled) {
                return super.getTextureResource(a);
            }
            return id("textures/lowpoly/" + stemOf(kind, a.stage()) + ".png");
        }
    }
}
