package jp.revati.skyabyss.client.lowpoly;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 4 組の立体防具の描画の共通部分 (docs/design/16_armor_design.md 5 章)。
// 骨の付け方 (頭・胴・腕・脚・靴をプレイヤーの骨へ) は GeckoLib の GeoArmorRenderer のまま。cube の代わりに、骨ごとの低ポリメッシュを流す。
//  - 低ポリ: geo/armor/lowpoly_armor.geo.json (骨だけ) + lowpoly/armor/<組>/<部位>[_階級].json + 強化段階の絵 (LowPolyArmor)。光る画素は <絵>_glowmask.png を AutoGlowingGeoLayer が重ねる
//  - LowPolyArmor.enabled = false: 元の geo/armor/<組>_armor.geo.json と textures/armor/<組>_armor.png (cube)
// 防具の層はバニラの防具モデルを GeckoLib のこの描画に置き換えるので、バニラの層は描かれない
public abstract class LowPolyArmorRenderer<T extends Item & GeoItem> extends GeoArmorRenderer<T> {
    private static final ResourceLocation LOW_GEO = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "geo/armor/lowpoly_armor.geo.json");

    private final String set;

    protected LowPolyArmorRenderer(String set) {
        super(new Model<T>(set));
        this.set = set;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    private boolean lowPoly() {
        return currentSlot != null && LowPolyArmor.usable(set, currentSlot);
    }

    @Override
    public ResourceLocation getTextureLocation(T animatable) {
        if (currentSlot != null && currentStack != null && LowPolyArmor.usable(set, currentSlot)) {
            ResourceLocation atlas = LowPolyArmor.atlas(set, currentSlot, currentStack);
            if (atlas != null) {
                return atlas;
            }
        }
        return super.getTextureLocation(animatable);
    }

    @Override
    public void renderCubesOfBone(PoseStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (!lowPoly()) {
            super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
            return;
        }
        if (bone.isHidden()) {
            return;
        }
        LowPolyMesh mesh = LowPolyMeshes.get(LowPolyArmor.meshName(set, currentSlot, currentStack));
        LowPolyMesh.Bone meshBone = mesh == null ? null : mesh.bones.get(bone.getName());
        if (meshBone != null) {
            LowPoly.drawBone(poseStack, buffer, meshBone, packedLight, packedOverlay, colour);
        }
    }

    // 低ポリのときは骨だけの geo、そうでなければ元の cube の geo / 絵
    private static final class Model<T extends Item & GeoItem> extends DefaultedGeoModel<T> {
        private final String set;

        private Model(String set) {
            super(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, set + "_armor"));
            this.set = set;
        }

        @Override
        protected String subtype() {
            return "armor";
        }

        @Override
        public ResourceLocation getModelResource(T animatable) {
            if (animatable instanceof ArmorItem armor) {
                EquipmentSlot slot = armor.getType().getSlot();
                if (LowPolyArmor.usable(set, slot)) {
                    return LOW_GEO;
                }
            }
            return super.getModelResource(animatable);
        }
    }
}
