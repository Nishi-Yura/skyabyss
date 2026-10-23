package jp.revati.skyabyss.abyss.mobs.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.boss.client.BossRender;
import jp.revati.skyabyss.abyss.mobs.AbyssMobB;
import jp.revati.skyabyss.abyss.mobs.AbyssMobsBSetup;
import jp.revati.skyabyss.abyss.mobs.AshTrail;
import jp.revati.skyabyss.abyss.mobs.CeilingWalker;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;

// 深淵の敵 B 組の描画 (素材は tools/gen/abyss/gen_abyss_mobs_b.py)。形 (stage 1〜3) で絵を替える: 1 = <id>、2 = <id>_s1 (歪み)、3 = <id>_s2 (崩れ)。
//   灰の這い跡: 光の中でだけ体を描く。光がなければ床の灰の跡 (prints の骨) だけ描く。
//   天井歩き: 天井にいるときは逆さに描く (体の上が天井に付く)。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AbyssMobsBRenderers {
    private static final ResourceLocation DEBUG = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
    private static final String[] STAGE_SUFFIX = {"", "_s1", "_s2"};
    private static final String[] TRAIL_BONES = {"root", "body", "head", "leg_l1", "leg_r1", "leg_l2", "leg_r2", "arm_l", "arm_r", "prints"};

    private AbyssMobsBRenderers() {
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(AbyssMobsBSetup.ASH_TRAIL.get(), ctx -> new Foe<>(ctx, AshTrail.ID, 0.25f));
        event.registerEntityRenderer(AbyssMobsBSetup.EMBER_KEEPER.get(), ctx -> new Foe<>(ctx, "ember_keeper", 0.45f));
        event.registerEntityRenderer(AbyssMobsBSetup.SOOT_CHILD.get(), ctx -> new Foe<>(ctx, "soot_child", 0.2f));
        event.registerEntityRenderer(AbyssMobsBSetup.PAST_SHADOW.get(), ctx -> new Foe<>(ctx, "past_shadow", 0.4f));
        event.registerEntityRenderer(AbyssMobsBSetup.NAMELESS_SHAPE.get(), ctx -> new Foe<>(ctx, "nameless_shape", 0.5f));
        event.registerEntityRenderer(AbyssMobsBSetup.LAMP_EATER.get(), ctx -> new Foe<>(ctx, "lamp_eater", 0.3f));
        event.registerEntityRenderer(AbyssMobsBSetup.CEILING_WALKER.get(), ctx -> new Foe<>(ctx, CeilingWalker.ID, 0.2f));
        event.registerEntityRenderer(AbyssMobsBSetup.HEARTBEAT_MIMIC.get(), ctx -> new Foe<>(ctx, "heartbeat_mimic", 0.4f));
        event.registerEntityRenderer(AbyssMobsBSetup.ROPE_HANGER.get(), ctx -> new Foe<>(ctx, "rope_hanger", 0.2f));
    }

    // 段で絵を替える模型 (骨とアニメは同じ。絵だけ替える)
    static final class Model<T extends AbyssMobB> extends LowPolyGeoRenderer.Model<T> {
        private final String name;
        private final ResourceLocation anim;
        private final ResourceLocation entityTexture;

        Model(String name) {
            super(name);
            this.name = name;
            this.anim = id("animations/entity/" + name + ".animation.json");
            this.entityTexture = id("textures/entity/" + name + ".png");
        }

        @Override
        public ResourceLocation getAnimationResource(T animatable) {
            int look = BossRender.look(animatable);
            return look != 0 ? BossRender.animation(look) : anim;
        }

        // 層の主・歪んだ王 (BossLook) は専用の骨だけの geo
        @Override
        public ResourceLocation getModelResource(T mob) {
            int look = BossRender.look(mob);
            return look != 0 ? BossRender.geo(look) : super.getModelResource(mob);
        }

        @Override
        public ResourceLocation getTextureResource(T mob) {
            int look = BossRender.look(mob);
            if (look != 0) {
                return LowPoly.debug ? DEBUG : BossRender.texture(look);
            }
            if (LowPoly.enabled) {
                if (LowPoly.debug) {
                    return DEBUG;
                }
                return id("textures/lowpoly/" + name + STAGE_SUFFIX[Math.max(0, Math.min(2, mob.stage() - 1))] + ".png");
            }
            return entityTexture;
        }
    }

    static final class Foe<T extends AbyssMobB> extends LowPolyGeoRenderer<T> {
        Foe(EntityRendererProvider.Context ctx, String name, float shadow) {
            super(ctx, new Model<>(name), e -> BossRender.look(e) != 0 ? BossRender.mesh(BossRender.look(e)) : name, 1.0f);
            this.shadowRadius = shadow;
        }

        @Override
        public void render(T e, float yaw, float pt, PoseStack pose, MultiBufferSource buf, int light) {
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
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }

        @Override
        public void preRender(PoseStack poseStack, T mob, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            if (mob instanceof AshTrail trail) {
                // 光の外では、床の灰の跡 (prints) だけ描く
                boolean shown = trail.revealed();
                for (String b : TRAIL_BONES) {
                    boolean hide = !shown && !b.equals("prints");
                    model.getBone(b).ifPresent(bone -> bone.setHidden(hide));
                }
                shadowRadius = shown ? 0.25f : 0f;
            }
            if (mob instanceof CeilingWalker walker && walker.onCeiling()) {
                // 天井にいる: 体の上を天井に付けて逆さに描く (体の高さの分だけ上げてから、x の軸で 180 度回す)
                poseStack.translate(0, mob.getBbHeight(), 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(180));
            }
            super.preRender(poseStack, mob, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }
}
