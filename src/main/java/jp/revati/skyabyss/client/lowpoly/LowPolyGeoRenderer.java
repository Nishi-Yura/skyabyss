package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.splat.SplatCloud;
import jp.revati.skyabyss.client.splat.SplatClouds;
import jp.revati.skyabyss.client.splat.SplatDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// GeckoLib の骨とアニメーションをそのまま使い、点の模型 (client/splat) を描く GeoEntityRenderer。
// geo は cube の無い骨だけの版 (geo/entity/<name>_lowpoly.geo.json、tools/install/install_lowpoly.py が書く)。点の模型の元は低ポリのメッシュ (lowpoly/<名前>.json)。
// 骨のポーズは GeckoLib がかけ終えた poseStack を骨ごとに集め、最後にまとめて描く。向き・アニメ・被弾の赤・死に方・明るさは GeckoLib のまま。
// 光る画素は光る点になるので、光の重ね描きの層は無い
public class LowPolyGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private final java.util.function.Function<T, String> meshName;

    public LowPolyGeoRenderer(EntityRendererProvider.Context ctx, String name, float scale) {
        this(ctx, new Model<T>(name), e -> name, scale);
    }

    // 自前の GeoModel (段階ごとに geo が変わる心臓など)。model は骨だけの geo を返す。
    // meshName は今のモブのメッシュ (lowpoly/<meshName>.json) の名前
    public LowPolyGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, java.util.function.Function<T, String> meshName, float scale) {
        super(ctx, model);
        this.meshName = meshName;
        withScale(scale);
    }

    // 倒れるアニメを持つ番兵の仲間は、バニラの横倒し (90 度) を重ねない (アニメの倒れ方だけにする)
    @Override
    protected float getDeathMaxRotation(T animatable) {
        if (animatable instanceof jp.revati.skyabyss.entity.GuardMob g && g.hasAction("death")) {
            return 0f;
        }
        return super.getDeathMaxRotation(animatable);
    }

    // 光る点 (発光マスクの画素) の明るさ。1 = いつも最大、0 = 世界の明るさのまま。眠る巨神 (DormantTitan) は揺らぎながら消える。
    // 目を開いたときだけ光るモブなどは上書きする
    protected float splatGlow(T animatable, float partialTick) {
        return animatable instanceof jp.revati.skyabyss.titan.end.DormantTitan dt ? dt.glowFactor(partialTick) : 1f;
    }

    // 描く大きさ (withScale の高さの倍率)。ボスの顔のアップ (client/titan/BossCloseUp) が見た目の顔の高さを求めるのに使う
    public float visualScale() {
        return scaleHeight;
    }

    // 今の主の低ポリのメッシュ (無ければ null)
    @Nullable
    public LowPolyMesh meshOf(T entity) {
        return LowPolyMeshes.get(meshName.apply(entity));
    }

    private static final java.util.Map<LowPolyMesh, Float> TOPS = new java.util.WeakHashMap<>();

    // 模型のいちばん高い点 (ブロック単位、足もとから)
    static float meshTop(LowPolyMesh mesh) {
        return TOPS.computeIfAbsent(mesh, m -> {
            float top = 0f;
            for (LowPolyMesh.Bone b : m.bones.values()) {
                for (int i = 1; i < b.pos.length; i += 3) {
                    top = Math.max(top, b.pos[i]);
                }
            }
            return top;
        });
    }

    // 体の大きさの属性 (Attributes.SCALE。深みの王など) を、模型にもかける
    @Override
    public void render(T entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        float s = entity instanceof net.minecraft.world.entity.LivingEntity le ? le.getScale() : 1f;
        // 地の中から這い出る間 (combat/BossEmerge): 床の下へ沈め、主ごとに回す・前のめりにする
        // 呼ぶ側が先に縮めていることがある (層の主・王の作りの大きさを戻す BossRender.undoAuthorScale)。沈める深さは世界のブロックで、
        // 描く模型の高さ (当たりの箱より大きく描く主が多い) と当たりの箱の高い方
        float outer = poseStack.last().pose().getScale(new org.joml.Vector3f()).y;
        float[] em = null;
        if (jp.revati.skyabyss.client.titan.BossEmergeClient.active(entity)) {
            LowPolyMesh mesh = meshOf(entity);
            float h = Math.max(entity.getBbHeight(), mesh == null ? 0f : meshTop(mesh) * scaleHeight * s * outer);
            em = jp.revati.skyabyss.client.titan.BossEmergeClient.pose(entity, h);
        }
        if (em != null) {
            float yaw = entity instanceof net.minecraft.world.entity.LivingEntity l2 ? net.minecraft.util.Mth.rotLerp(partialTick, l2.yBodyRotO, l2.yBodyRot) : entity.getYRot();
            poseStack.pushPose();
            poseStack.translate(0, -em[0] / Math.max(1e-3f, outer), 0);
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-yaw + em[1]));
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-em[2]));
            poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw));
            poseStack.scale(s * em[3], s * em[4], s * em[3]);
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            poseStack.popPose();
            return;
        }
        if (s == 1f) {
            super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        poseStack.pushPose();
        poseStack.scale(s, s, s);
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (!isReRender) {
            activeSplat = splatFor(animatable);
            if (activeSplat != null) {
                activeSplat.begin();
            }
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        // 心臓の核の骨の位置を光へ知らせる (client/fx/CoreGlowFx)
        if (animatable != null && jp.revati.skyabyss.client.fx.CoreGlowFx.wants(animatable)) {
            jp.revati.skyabyss.client.fx.CoreGlowFx.track(animatable, bone.getName(), bone.getPivotX(), bone.getPivotY(), bone.getPivotZ(), poseStack.last().pose());
        }
        // 骨のポーズだけ集め、描くのは renderFinal でまとめて (SplatDraw)。模型がまだ出来ていない (裏で作っている) 間は描かない。
        // ほかの層 (光の層など) の描き直しでも呼ばれるが、同じポーズを覚え直すだけ
        if (activeSplat != null && !bone.isHidden()) {
            activeSplat.capture(bone.getName(), poseStack.last().pose(), packedLight, packedOverlay, colour);
        }
    }

    // 点の模型 (client/splat。docs/review/splat_pilot): 今描いている主の点の描き手 (無ければ null)。点の模型ごとに一つ作って使い回す
    private SplatDraw activeSplat;
    private final Map<String, SplatDraw> splatDraws = new HashMap<>();

    @Nullable
    private SplatDraw splatFor(T animatable) {
        if (animatable == null) {
            return null;
        }
        SplatCloud cloud = SplatClouds.get(meshName.apply(animatable));
        if (cloud == null) {
            return null;
        }
        SplatDraw d = splatDraws.get(cloud.name);
        if (d == null || d.cloud() != cloud) {
            d = new SplatDraw(cloud);
            splatDraws.put(cloud.name, d);
        }
        return d;
    }

    @Override
    public void renderFinal(PoseStack poseStack, T animatable, BakedGeoModel model, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, int colour) {
        if (activeSplat != null) {
            SplatDraw d = activeSplat;
            activeSplat = null;
            d.flush(bufferSource, partialTick, splatGlow(animatable, partialTick));
        }
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, colour);
    }

    // 深淵の心臓は暗い紫に染める (docs/design/31 の B04。心臓四体はどれもこの描画を通る)
    @Override
    public software.bernie.geckolib.util.Color getRenderColor(T animatable, float partialTick, int packedLight) {
        software.bernie.geckolib.util.Color base = animatable instanceof jp.revati.skyabyss.entity.HeartBoss heart && heart.isAbyss()
                ? software.bernie.geckolib.util.Color.ofRGB(112, 88, 148) : super.getRenderColor(animatable, partialTick, packedLight);
        return heated(animatable, partialTick, base);
    }

    // 燃えている敵は体が熱で橙に脈打つ (バニラの炎の板の代わり。client/hud/FoeStatusClient)。ほかの色の掛け算に重ねる
    public static software.bernie.geckolib.util.Color heated(Entity e, float partialTick, software.bernie.geckolib.util.Color base) {
        if (!(e instanceof net.minecraft.world.entity.LivingEntity le) || !jp.revati.skyabyss.client.hud.FoeStatusClient.isFoe(e)) {
            return base;
        }
        int h = jp.revati.skyabyss.client.hud.FoeStatusClient.heat(le, partialTick);
        if (h == 0xFFFFFFFF) {
            return base;
        }
        int c = base.argbInt();
        int r = ((c >> 16) & 255) * ((h >> 16) & 255) / 255;
        int g = ((c >> 8) & 255) * ((h >> 8) & 255) / 255;
        int b = (c & 255) * (h & 255) / 255;
        return software.bernie.geckolib.util.Color.ofARGB((c >>> 24) & 255, r, g, b);
    }

    // 骨だけの geo (geo/entity/<name>_lowpoly.geo.json)。絵は点の模型が使う (textures/lowpoly/<name>.png)
    public static class Model<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
        private final ResourceLocation lowGeo;
        private final ResourceLocation atlas;

        public Model(String name) {
            this(name, true);
        }

        public Model(String name, boolean turnsHead) {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name), turnsHead);
            this.lowGeo = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + name + "_lowpoly.geo.json");
            this.atlas = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + name + ".png");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return lowGeo;
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return atlas;
        }
    }
}
