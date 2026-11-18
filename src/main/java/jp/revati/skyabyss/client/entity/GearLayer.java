package jp.revati.skyabyss.client.entity;

import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

// 武器・盾・弓など「モブとは別のモデル」を、モブの受け口の骨 (socket_main / socket_off) に描く層を作る。
// 武器をモブのメッシュに焼き込まず、表示のときに合体させる。持ち物のメッシュも点の模型 (client/splat) で描く (LowPolyGearLayer)。
//
// 使い方:
//   renderer.addRenderLayer(GearLayer.lowPoly(renderer, "socket_off", "bone_sentinel_shield"));
//   renderer.addRenderLayer(GearLayer.item(renderer, "socket_main", mob -> new ItemStack(ModItems.CAPTAIN_PIKE.get())));
// 武器のモデルの原点 (0,0,0) が受け口の骨の pivot (握りの中心) に来る。向きは受け口の骨の回転で決まる。
public final class GearLayer {
    private GearLayer() {
    }

    // lowpoly/<meshName>.json (socket: true のメッシュ) を受け口に描く。使い方は docs/design/15_high_poly.md 9 章
    public static <T extends Entity & GeoAnimatable> GeoRenderLayer<T> lowPoly(GeoRenderer<T> renderer, String socketBone, String meshName) {
        return new jp.revati.skyabyss.client.lowpoly.LowPolyGearLayer<>(renderer, socketBone, meshName);
    }

    // 弓の弦のようにメッシュの中の骨が動くもの: geo/gear/<meshName>_lowpoly.geo.json と animations/gear/<meshName>.animation.json で動かす
    public static <T extends Entity & GeoAnimatable> GeoRenderLayer<T> lowPolyAnimated(GeoRenderer<T> renderer, String socketBone, String meshName,
                                                                                    Function<T, String> animation) {
        return new jp.revati.skyabyss.client.lowpoly.LowPolyGearLayer<>(renderer, socketBone, meshName, animation);
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
}
