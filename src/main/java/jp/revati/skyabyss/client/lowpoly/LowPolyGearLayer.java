package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoObjectRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

// 受け口の骨 (socket_main / socket_off) に低ポリのメッシュ (盾・武器) を描く層。GearLayer のアイテム/geo 版と同じ置き方で、
// メッシュの原点 (0,0,0) が受け口の pivot に来る (mesh JSON の "socket": true。頂点は受け口の pivot からの相対座標)。
// 向き・アニメは受け口の骨に付いている。使い方: GearLayer.lowPoly(renderer, "socket_off", "bone_sentinel_shield")
//
// アニメ付き (弓の弦など、メッシュの中の骨が動く) は animation を渡す: メッシュ名 = gear の名前 (rib_archer_bow) で、
// (動くメッシュは今は光らない。)  geo/gear/<名>_lowpoly.geo.json (骨だけ) と animations/gear/<名>.animation.json (animation.<名>.<返した名前>) を使う。
// GearLayer.lowPolyAnimated(renderer, "socket_off", "rib_archer_bow", mob -> mob.isDrawingBow() ? "draw" : "idle")
public class LowPolyGearLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
    private final String socketBone;
    private final String meshName;
    private final Function<T, String> animation; // null = 動かさない静的なメッシュ
    private final AnimatedGear animated;
    private final Map<Entity, GearAnimatable> states = new WeakHashMap<>();

    public LowPolyGearLayer(GeoRenderer<T> renderer, String socketBone, String meshName) {
        this(renderer, socketBone, meshName, null);
    }

    public LowPolyGearLayer(GeoRenderer<T> renderer, String socketBone, String meshName, Function<T, String> animation) {
        super(renderer);
        this.socketBone = socketBone;
        this.meshName = meshName;
        this.animation = animation;
        this.animated = animation == null ? null : new AnimatedGear(meshName);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!LowPoly.enabled || !bone.getName().equals(socketBone)) {
            return;
        }
        LowPolyMesh mesh = LowPolyMeshes.get(meshName);
        if (mesh == null) {
            return;
        }
        poseStack.pushPose();
        // 骨の回転まで掛かっている。原点を骨の pivot へ移すだけ
        RenderUtil.translateToPivotPoint(poseStack, bone);
        if (animated != null) {
            GearAnimatable state = states.computeIfAbsent(animatable, e -> new GearAnimatable(meshName));
            state.current = animation.apply(animatable);
            animated.render(poseStack, state, bufferSource, null, null, packedLight, partialTick);
        } else {
            int white = 0xFFFFFFFF;
            LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), mesh, packedLight, packedOverlay, white);
            if (mesh.glowMask != null) {
                LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentEmissive(mesh.glowMask)), mesh, 0xF000F0, packedOverlay, white);
            }
        }
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- アニメ付き

    private static final class GearAnimatable implements GeoAnimatable {
        private final String gearName;
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private final Map<String, RawAnimation> clips = new HashMap<>();
        private String current = "idle";

        private GearAnimatable(String gearName) {
            this.gearName = gearName;
        }

        @Override
        public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
            controllers.add(new AnimationController<>(this, "gear", 2, state -> state.setAndContinue(
                    clips.computeIfAbsent(current, n -> RawAnimation.begin().thenPlay("animation." + gearName + "." + n)))));
        }

        @Override
        public AnimatableInstanceCache getAnimatableInstanceCache() {
            return cache;
        }

        @Override
        public double getTick(Object object) {
            return RenderUtil.getCurrentTick();
        }
    }

    private static final class GearModel extends GeoModel<GearAnimatable> {
        private final ResourceLocation model;
        private final ResourceLocation texture;
        private final ResourceLocation animation;

        private GearModel(String name) {
            this.model = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/gear/" + name + "_lowpoly.geo.json");
            this.texture = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + name + ".png");
            this.animation = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "animations/gear/" + name + ".animation.json");
        }

        @Override
        public ResourceLocation getModelResource(GearAnimatable animatable) {
            return model;
        }

        @Override
        public ResourceLocation getTextureResource(GearAnimatable animatable) {
            return texture;
        }

        @Override
        public ResourceLocation getAnimationResource(GearAnimatable animatable) {
            return animation;
        }
    }

    // 骨ごとのメッシュを、GeckoLib が骨をアニメさせた姿勢で描く。受け口の pivot へ移す以外の移動 (ブロックの中心へ) は掛けない
    private static final class AnimatedGear extends GeoObjectRenderer<GearAnimatable> {
        private final String name;

        private AnimatedGear(String name) {
            super(new GearModel(name));
            this.name = name;
        }

        @Override
        public void preRender(PoseStack poseStack, GearAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            this.animatable = animatable;
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
            scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        }

        @Override
        public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
            LowPolyMesh mesh = LowPolyMeshes.get(name);
            LowPolyMesh.Bone b = mesh == null ? null : mesh.bones.get(bone.getName());
            if (b != null && !bone.isHidden()) {
                LowPoly.drawBone(poseStack, buffer, b, packedLight, packedOverlay, colour);
            }
        }
    }
}
