package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// GeckoLib の骨とアニメーションをそのまま使い、各骨の cube の代わりに低ポリメッシュを流す GeoEntityRenderer。
// geo は cube の無い骨だけの版 (geo/entity/<name>_lowpoly.geo.json、tools/install_lowpoly.py が書く)。
// 骨のポーズは GeckoLib がかけ終えた poseStack をそのまま使うので、向き・アニメ・被弾の赤・死に方・明るさは cube のモデルと同じ。
// LowPoly.enabled が false のときは元の geo / テクスチャに戻り、普通の GeckoLib の描画になる。
public class LowPolyGeoRenderer<T extends Entity & GeoAnimatable> extends GeoEntityRenderer<T> {
    private static final ResourceLocation DEBUG_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
    private final java.util.function.Function<T, String> meshName;
    private MultiBufferSource currentSource;

    public LowPolyGeoRenderer(EntityRendererProvider.Context ctx, String name, float scale) {
        this(ctx, new Model<T>(name), e -> name, scale);
    }

    // 自前の GeoModel (段階ごとに geo が変わる心臓など)。model は LowPoly.enabled のとき lowPolyGeo / lowPolyAtlas を返す。
    // meshName は今のモブのメッシュ (lowpoly/<meshName>.json) の名前
    public LowPolyGeoRenderer(EntityRendererProvider.Context ctx, GeoModel<T> model, java.util.function.Function<T, String> meshName, float scale) {
        super(ctx, model);
        this.meshName = meshName;
        withScale(scale);
        // 光る所があるときだけ (発光マスクの画像があるときだけ) 光らせる。LowPoly.enabled の切り替えにも付いていく
        addRenderLayer(new GlowLayer<>(this, this::autoGlow));
    }

    // false のときは発光層 (AutoGlowingGeoLayer) を描かない。潮殻の心臓のように、元のキューブ版が自前の発光層を持つモブが上書きする
    protected boolean autoGlow() {
        return true;
    }

    @Override
    public void preRender(PoseStack poseStack, T animatable, BakedGeoModel model, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer,
                          boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (bufferSource != null) {
            currentSource = bufferSource;
        }
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        LowPolyMesh mesh = LowPoly.enabled && animatable != null ? LowPolyMeshes.get(meshName.apply(animatable)) : null;
        LowPolyMesh.Bone meshBone = mesh == null ? null : mesh.bones.get(bone.getName());
        if (meshBone == null) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
            return;
        }
        if (bone.isHidden()) {
            return;
        }
        if (LowPoly.debug && currentSource != null) {
            // メッシュを本来のテクスチャで先に描き、そのあと GeckoLib の箱 (半透明の赤) を重ねる
            LowPoly.drawBone(poseStack, currentSource.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), meshBone, packedLight, packedOverlay, colour);
            // バッファを切り替えると渡された buffer は閉じるので、箱の側は取り直す
            super.renderCubesOfBone(poseStack, bone, currentSource.getBuffer(RenderType.entityTranslucent(DEBUG_TEXTURE)), packedLight, packedOverlay, colour);
            return;
        }
        // 巨神の焼き込みの陰 (下ほど暗い) は、光る層の重ね描きでは掛けない
        LowPoly.drawBone(poseStack, buffer, meshBone, packedLight, packedOverlay, colour, 0f, !glowPass);
    }

    // 光る層 (GlowLayer) の重ね描きの最中か
    private boolean glowPass;

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        if (LowPoly.enabled && LowPoly.debug && !animatable.isInvisible()) {
            return RenderType.entityTranslucent(DEBUG_TEXTURE);
        }
        return super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    // 低ポリのときは geo/entity/<name>_lowpoly(.debug).geo.json と textures/lowpoly/<name>.png、そうでなければ元の素材
    public static class Model<T extends GeoAnimatable> extends DefaultedEntityGeoModel<T> {
        private final ResourceLocation lowGeo;
        private final ResourceLocation debugGeo;
        private final ResourceLocation atlas;

        public Model(String name) {
            this(name, true);
        }

        public Model(String name, boolean turnsHead) {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name), turnsHead);
            this.lowGeo = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + name + "_lowpoly.geo.json");
            this.debugGeo = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + name + "_lowpoly_debug.geo.json");
            this.atlas = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + name + ".png");
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            return LowPoly.enabled ? (LowPoly.debug ? debugGeo : lowGeo) : super.getModelResource(animatable);
        }

        @Override
        public ResourceLocation getTextureResource(T animatable) {
            return LowPoly.enabled ? (LowPoly.debug ? DEBUG_TEXTURE : atlas) : super.getTextureResource(animatable);
        }
    }

    // 発光マスク (<テクスチャ>_glowmask.png) があるテクスチャのときだけ AutoGlowingGeoLayer の描画をする
    private static final class GlowLayer<T extends Entity & GeoAnimatable> extends AutoGlowingGeoLayer<T> {
        private static final Map<ResourceLocation, Boolean> HAS_MASK = new HashMap<>();

        private final java.util.function.BooleanSupplier enabled;

        GlowLayer(GeoRenderer<T> renderer, java.util.function.BooleanSupplier enabled) {
            super(renderer);
            this.enabled = enabled;
        }

        @Override
        public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, @Nullable RenderType renderType, MultiBufferSource bufferSource,
                           @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!enabled.getAsBoolean()) {
                return;
            }
            ResourceLocation tex = getTextureResource(animatable);
            boolean has = HAS_MASK.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager().getResource(
                    t.withPath(p -> p.endsWith(".png") ? p.substring(0, p.length() - 4) + "_glowmask.png" : p)).isPresent());
            if (has) {
                LowPolyGeoRenderer<?> owner = getRenderer() instanceof LowPolyGeoRenderer<?> r ? r : null;
                if (owner != null) {
                    owner.glowPass = true;
                }
                try {
                    // 眠る巨神 (DormantTitan) は、光が揺らぎながら消える
                    float factor = animatable instanceof jp.revati.skyabyss.titan.end.DormantTitan d ? d.glowFactor(partialTick) : 1f;
                    if (factor >= 0.999f) {
                        super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
                    } else if (factor > 0.003f) {
                        RenderType glow = getRenderType(animatable, bufferSource);
                        if (glow != null) {
                            int v = net.minecraft.util.Mth.clamp(Math.round(factor * 255f), 0, 255);
                            getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, glow, bufferSource.getBuffer(glow), partialTick,
                                    net.minecraft.client.renderer.LightTexture.FULL_BRIGHT, packedOverlay, 0xFF000000 | (v << 16) | (v << 8) | v);
                        }
                    }
                } finally {
                    if (owner != null) {
                        owner.glowPass = false;
                    }
                }
            }
        }
    }
}
