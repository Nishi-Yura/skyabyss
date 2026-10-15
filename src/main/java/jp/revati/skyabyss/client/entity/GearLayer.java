package jp.revati.skyabyss.client.entity;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
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
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.util.RenderUtil;

// 武器・盾・弓など「モブとは別のモデル」を、モブの受け口の骨 (socket_main / socket_off) に描く層。
// 武器をモブの geo に焼き込まず、表示のときに合体させる (docs/roughs/GEAR_BRIEF.md)。
//
// 使い方:
//   // gear/<gearName> の GeckoLib モデルを受け口に描き、animation が返す名前のアニメーションを流す
//   renderer.addRenderLayer(new GearLayer<>(renderer, "socket_off", "rib_archer_bow", mob -> mob.isAiming() ? "draw" : "idle"));
//   // アイテムの3Dモデルを受け口に描く
//   renderer.addRenderLayer(GearLayer.item(renderer, "socket_main", mob -> new ItemStack(ModItems.CAPTAIN_PIKE.get())));
//
// 素材: geo/gear/<gearName>.geo.json, textures/gear/<gearName>.png (+ _glowmask.png があれば暗闇で光る),
//       animations/gear/<gearName>.animation.json (名前は animation.<gearName>.<名前>)。
// 武器のモデルの原点 (0,0,0) が受け口の骨の pivot (握りの中心) に来る。向きは受け口の骨の回転で決まる。
// アニメーションの繰り返し (loop / hold_on_last_frame) は、アニメーションの JSON に書いた設定に従う。
public class GearLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
    private final String socketBone;
    private final Function<T, String> animation;
    private final GearRenderer gear;
    // モブ一体ごとに武器のアニメーションの状態を持つ (モブが消えれば一緒に消える)
    private final Map<Entity, GearAnimatable> states = new WeakHashMap<>();

    public GearLayer(GeoRenderer<T> renderer, String socketBone, String gearName, Function<T, String> animation) {
        super(renderer);
        this.socketBone = socketBone;
        this.animation = animation;
        this.gear = new GearRenderer(gearName);
    }

    @Override
    public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                              VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
        if (!bone.getName().equals(socketBone)) {
            return;
        }
        GearAnimatable state = states.computeIfAbsent(animatable, e -> new GearAnimatable(gear.name));
        state.current = animation.apply(animatable);
        poseStack.pushPose();
        // ここではもう骨の回転まで掛かっている。原点を骨の pivot へ移すだけ (回転をもう一度掛けない)
        RenderUtil.translateToPivotPoint(poseStack, bone);
        gear.render(poseStack, state, bufferSource, null, null, packedLight, partialTick);
        poseStack.popPose();
    }

    // ---------------------------------------------------------------- アイテムを描く

    // アイテムを、プレイヤーが右手に持ったときと同じ見た目 (THIRD_PERSON_RIGHT_HAND) で受け口に描く。
    // 受け口の骨の回転が 0 のとき、腕を下ろした手 (指先が -Y、前が -Z) で持った向きになる
    public static <T extends Entity & GeoAnimatable> GeoRenderLayer<T> item(GeoRenderer<T> renderer, String socketBone, Function<T, ItemStack> stack) {
        return item(renderer, socketBone, stack, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, GearLayer::vanillaHand);
    }

    // アイテムを描く (表示の種類と、握りを受け口に合わせるための姿勢の調整を指定する)。
    // adjust は受け口の pivot を原点とする座標 (1 = 1ブロック) で、アイテムを描く直前に呼ばれる
    public static <T extends Entity & GeoAnimatable> GeoRenderLayer<T> item(GeoRenderer<T> renderer, String socketBone, Function<T, ItemStack> stack,
                                                                         ItemDisplayContext context, Consumer<PoseStack> adjust) {
        return new BlockAndItemGeoLayer<T>(renderer, (bone, mob) -> bone.getName().equals(socketBone) ? stack.apply(mob) : null, (bone, mob) -> null) {
            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack itemStack, T animatable) {
                return context;
            }

            // BlockAndItemGeoLayer は骨の回転をもう一度掛けてしまうので、原点を pivot へ移すだけにする
            @Override
            public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                      VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                ItemStack itemStack = getStackForBone(bone, animatable);
                if (itemStack == null || itemStack.isEmpty()) {
                    return;
                }
                poseStack.pushPose();
                RenderUtil.translateToPivotPoint(poseStack, bone);
                adjust.accept(poseStack);
                renderStackForBone(poseStack, bone, itemStack, animatable, bufferSource, partialTick, packedLight, packedOverlay);
                poseStack.popPose();
            }
        };
    }

    // バニラの ItemInHandLayer と同じ向きに直す。GeckoLib の座標は、バニラのモデルの座標を Z 軸まわりに 180° 回したもの
    private static void vanillaHand(PoseStack poseStack) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(180));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90));
        poseStack.mulPose(Axis.YP.rotationDegrees(180));
        poseStack.translate(1 / 16f, 0.125f, 0);
    }

    // ---------------------------------------------------------------- 武器のモデル

    // 武器一つぶんのアニメーションの状態 (モブ一体ごとに一つ)
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
            this.model = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/gear/" + name + ".geo.json");
            this.texture = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gear/" + name + ".png");
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

    // GeoObjectRenderer から「ブロックの中心へずらす」移動だけを除いたもの
    private static final class GearRenderer extends GeoObjectRenderer<GearAnimatable> {
        private final String name;
        private boolean glowChecked;

        private GearRenderer(String name) {
            super(new GearModel(name));
            this.name = name;
        }

        @Override
        public void preRender(PoseStack poseStack, GearAnimatable animatable, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            if (!glowChecked) {
                glowChecked = true;
                // 発光マスクがあるときだけ光らせる (無いマスクを読むと GeckoLib が落ちる)
                ResourceLocation mask = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gear/" + name + "_glowmask.png");
                if (Minecraft.getInstance().getResourceManager().getResource(mask).isPresent()) {
                    addRenderLayer(new AutoGlowingGeoLayer<>(this));
                }
            }
            this.animatable = animatable;
            this.objectRenderTranslations = new Matrix4f(poseStack.last().pose());
            scaleModelForRender(this.scaleWidth, this.scaleHeight, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        }
    }
}
