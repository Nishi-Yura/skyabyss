package jp.revati.skyabyss.client.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

// 装飾品・補助の品 16 種を、手に持つ・地面・額縁・頭で低ポリメッシュとして描く (docs/design/15 の 4.6.3)。武器の WeaponItemRenderer と同じ作り。
// 品の item モデル (models/item/<id>.json) は base が builtin/entity (display は持ち方の表)、gui は 32x32 の絵 (perspectives)。
// `LowPoly.accessoryMeshes = false` なら元の平絵のモデル (models/item_flat/<id>.json) を同じ置き方で描く。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AccessoryItemRenderer extends BlockEntityWithoutLevelRenderer {
    private static AccessoryItemRenderer instance;

    private AccessoryItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static synchronized AccessoryItemRenderer get() {
        if (instance == null) {
            instance = new AccessoryItemRenderer();
        }
        return instance;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (LowPoly.accessoryMeshes) {
            LowPolyMesh mesh = AccessoryMeshes.mesh(stack);
            if (mesh != null) {
                pose.pushPose();
                // 原点は block の角 (-0.5 ずみ)。メッシュは中心が原点で、正面が -Z。item の平絵は +Z が表なので 180 度回して、バニラの generated の display をそのまま使う。
                // メッシュは x を反転して焼いてある (GeckoLib 向け) ので、ここで戻す
                pose.translate(0.5f, 0.5f, 0.5f);
                pose.mulPose(Axis.YP.rotationDegrees(180f));
                pose.scale(-1f, 1f, 1f);
                AccessoryMeshes.draw(mesh, pose, buffers, light, overlay);
                pose.popPose();
                return;
            }
        }
        renderFlat(stack, pose, buffers, light, overlay);
    }

    private static void renderFlat(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(flatId(AccessoryMeshes.idOf(stack)));
        RenderType type = ItemBlockRenderTypes.getRenderType(stack, true);
        VertexConsumer consumer = ItemRenderer.getFoilBuffer(buffers, type, true, stack.hasFoil());
        Minecraft.getInstance().getItemRenderer().renderModelLists(model, stack, light, overlay, pose, consumer);
    }

    private static ModelResourceLocation flatId(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "item_flat/" + name));
    }

    @SubscribeEvent
    static void registerFlatModels(ModelEvent.RegisterAdditional event) {
        for (String name : AccessoryMeshes.lookNames()) {
            event.register(flatId(name));
        }
    }

    @SubscribeEvent
    static void registerExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions ext = new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return AccessoryItemRenderer.get();
            }
        };
        for (String name : AccessoryMeshes.IDS) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name));
            if (item != net.minecraft.world.item.Items.AIR) {
                event.registerItem(ext, item);
            }
        }
    }
}
