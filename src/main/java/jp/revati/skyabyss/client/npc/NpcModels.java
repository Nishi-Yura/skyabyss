package jp.revati.skyabyss.client.npc;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

import java.util.function.Function;

// 人ごとに形を作った NPC (v2。tools/rough/lowpoly/npc2・tools/install/install_npc2.py)。
// 一人につき: lowpoly/npc_<id>.json (体)、textures/lowpoly/npc_<id>.png、geo/entity/npc_<id>_lowpoly.geo.json (骨だけ)、
// animations/entity/npc_<id>.animation.json (その人の持ち方に合わせた idle / walk / talk)、
// 持ち物は受け口ごとの別メッシュ lowpoly/npc_<id>_<受け口>.json (prop_r / prop_l / prop_back。絵も別)。
// id は係の人の役 (guild_clerk、survivor_bone …)、町の人は guest_<人>。v2 のメッシュが無い人は前の作り (attendant_<案>) のまま。
public final class NpcModels {
    public static final String[] SOCKETS = {"prop_r", "prop_l", "prop_back"};

    private NpcModels() {
    }

    public static String mesh(String id) {
        return "npc_" + id;
    }

    // v2 の体のメッシュが入っているか (リソースの再読み込みで変わる)
    public static boolean has(String id) {
        return id != null && LowPolyMeshes.get(mesh(id)) != null;
    }

    public static ResourceLocation geo(String id) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/entity/" + mesh(id) + "_lowpoly.geo.json");
    }

    public static ResourceLocation texture(String id) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + mesh(id) + ".png");
    }

    public static ResourceLocation animation(String id) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "animations/entity/" + mesh(id) + ".animation.json");
    }

    // 受け口の持ち物を描く層。meshBase は今の体のメッシュの名前 (npc_<id>。v2 でない人は null を返す)
    public static final class PropLayer<T extends Entity & GeoAnimatable> extends GeoRenderLayer<T> {
        private final Function<T, String> meshBase;

        public PropLayer(GeoRenderer<T> renderer, Function<T, String> meshBase) {
            super(renderer);
            this.meshBase = meshBase;
        }

        @Override
        public void renderForBone(PoseStack poseStack, T animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource,
                                  VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (!LowPoly.enabled || !bone.getName().startsWith("prop_") || animatable.isInvisible()) {
                return;
            }
            String base = meshBase.apply(animatable);
            if (base == null) {
                return;
            }
            LowPolyMesh mesh = LowPolyMeshes.get(base + "_" + bone.getName());
            if (mesh == null) {
                return;
            }
            poseStack.pushPose();
            // 骨の回転まで掛かっている。原点を骨の pivot へ移すだけ (持ち物の頂点は受け口の pivot からの相対)
            RenderUtil.translateToPivotPoint(poseStack, bone);
            int white = 0xFFFFFFFF;
            LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), mesh, packedLight, packedOverlay, white);
            if (mesh.glowMask != null) {
                LowPoly.drawAll(poseStack, bufferSource.getBuffer(RenderType.entityTranslucentEmissive(mesh.glowMask)), mesh, 0xF000F0, packedOverlay, white);
            }
            poseStack.popPose();
        }
    }
}
