package jp.revati.skyabyss.client;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.armor.WhaleRoadArmorRenderer;
import jp.revati.skyabyss.client.lowpoly.LowPolyArmorRenderer;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.guide.TravelGear;
import jp.revati.skyabyss.skin.Skins;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// スキン (skin/Skins) の、防具を着けていない部位。スキンは一式で見せるので、空いた部位にもスキンの形を描く。
// 防具を着けている部位は防具の描画 (LowPolyArmorRenderer) が形を替えて描くので、ここでは描かない。
// 描き方は防具と同じ (GeckoLib の防具の骨をプレイヤーの姿勢に合わせる)。部位ごとに鯨道の旅装の品を「器」に借り、形と絵だけスキンにする
public final class SkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private final Map<EquipmentSlot, LowPolyArmorRenderer<?>> renderers = new EnumMap<>(EquipmentSlot.class);

    public SkinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    private static ItemStack vessel(EquipmentSlot slot) {
        return new ItemStack(switch (slot) {
            case HEAD -> TravelGear.HELMET.get();
            case CHEST -> TravelGear.CHESTPLATE.get();
            case LEGS -> TravelGear.LEGGINGS.get();
            default -> TravelGear.BOOTS.get();
        });
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        String skin = Skins.worn(player);
        if (skin == null || player.isInvisible()) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty() || LowPolyMeshes.get(LowPolyArmorRenderer.skinMesh(skin, slot)) == null) {
                continue;
            }
            LowPolyArmorRenderer<?> r = renderers.computeIfAbsent(slot, s -> new WhaleRoadArmorRenderer());
            r.prepForRender(player, vessel(slot), slot, getParentModel(), buffers, partialTick, limbSwing, limbSwingAmount, netHeadYaw, headPitch);
            getParentModel().copyPropertiesTo(r);
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/" + LowPolyArmorRenderer.skinMesh(skin, slot) + ".png");
            r.renderToBuffer(poseStack, buffers.getBuffer(RenderType.armorCutoutNoCull(tex)), packedLight, OverlayTexture.NO_OVERLAY, -1);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Register {
        @SubscribeEvent
        static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof LivingEntityRenderer<?, ?> r && r.getModel() instanceof PlayerModel<?>) {
                    @SuppressWarnings("unchecked")
                    LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> pr = (LivingEntityRenderer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) r;
                    pr.addLayer(new SkinLayer(pr));
                }
            }
        }
    }
}
