package jp.revati.skyabyss.client.accessory;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.function.Function;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.item.accessory.AccessoryData;
import jp.revati.skyabyss.item.accessory.AccessorySetup;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 装飾の枠 (AccessoryData の 1〜2 番) に付けた装飾を、プレイヤーの体に低ポリメッシュで描く層 (docs/design/15 の 4.6.3)。
// 護符 (0 番) は使って効く品で身につけないので描かない。ほかのプレイヤーにも見える (枠の中身は周りへ同期する)。
// 一人称では自分の体が描かれないので出ない。透明のプレイヤーと `LowPoly.accessoryWorn = false` のときは描かない (戻しの旗)。
// 付け場所 (MOUNTS): 足環 = 右足首、羽根 = 右耳の後ろ、外套 = 背中、灯 = 腰の左、苔の護石 = 腰の右、補強板 = 背中の上、真珠 = 胸の前。
// 座標はモデルの部位の中 (1 単位 = 1/16 ブロック、y は下向き、正面は -z、プレイヤーの右は -x)。
public final class AccessoryWornLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private enum Part {
        HEAD(m -> m.head), BODY(m -> m.body), RIGHT_LEG(m -> m.rightLeg), LEFT_LEG(m -> m.leftLeg);

        final Function<PlayerModel<AbstractClientPlayer>, ModelPart> part;

        Part(Function<PlayerModel<AbstractClientPlayer>, ModelPart> part) {
            this.part = part;
        }
    }

    // part, 位置 (x, y, z)、回転 (度。x, y, z の順に掛ける)、倍率、メッシュの中の留め点 (メッシュ設計の座標。y は上向き、x は設計の右)
    private record Mount(Part part, float x, float y, float z, float rx, float ry, float rz, float scale, float px, float py, float pz) {
    }

    private static final Map<String, Mount> MOUNTS = Map.of(
            "storm_anklet", new Mount(Part.RIGHT_LEG, 0f, 10.2f, 0f, 0, 0, 0, 0.5f, 0, 0, 0),
            "storm_eye_feather", new Mount(Part.HEAD, -4.3f, -5.2f, 1.2f, 25, 0, 22, 0.8f, 0, -7, 0),
            "ember_mantle", new Mount(Part.BODY, 0f, -0.2f, 2.8f, 0, 0, 0, 0.85f, 0, 0, 0),
            "ember_lantern", new Mount(Part.BODY, 3.4f, 10.0f, -3.0f, 0, 0, 0, 0.45f, 0, 7, 0),
            "moss_talisman", new Mount(Part.BODY, -3.4f, 10.0f, -2.9f, 0, 0, 0, 0.5f, 0, 5.8f, 0),
            "stone_whale_plating", new Mount(Part.BODY, 0f, 6.0f, 3.0f, 0, 180, 0, 0.6f, 0, 0, 0),
            "moontide_pearl", new Mount(Part.BODY, 0f, 1.2f, -2.7f, 0, 0, 0, 0.5f, 0, 5.2f, 0));

    public AccessoryWornLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!LowPoly.accessoryWorn || player.isInvisible() || player.isSpectator()) {
            return;
        }
        AccessoryData data = player.getData(AccessorySetup.DATA);
        for (int i = 0; i < AccessoryData.SIZE; i++) {
            if (i == AccessoryData.CHARM) {
                continue;
            }
            ItemStack stack = data.items.get(i);
            if (stack.isEmpty()) {
                continue;
            }
            Mount m = MOUNTS.get(AccessoryMeshes.idOf(stack));
            LowPolyMesh mesh = m == null ? null : AccessoryMeshes.mesh(stack);
            if (mesh == null) {
                continue;
            }
            pose.pushPose();
            m.part.part.apply(getParentModel()).translateAndRotate(pose);
            pose.translate(m.x / 16f, m.y / 16f, m.z / 16f);
            pose.mulPose(Axis.ZP.rotationDegrees(m.rz));
            pose.mulPose(Axis.YP.rotationDegrees(m.ry));
            pose.mulPose(Axis.XP.rotationDegrees(m.rx));
            // モデルの座標は y が下向き。設計 (y 上) からモデルへは y だけ反転。メッシュは x 反転で焼いてあるので、そのまま (x_モデル = 焼いた x) で足りる
            pose.scale(m.scale, -m.scale, m.scale);
            pose.translate(m.px / 16f, -m.py / 16f, -m.pz / 16f);
            pose.translate(0f, 0f, 0f);
            AccessoryMeshes.draw(mesh, pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
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
                    pr.addLayer(new AccessoryWornLayer(pr));
                }
            }
        }
    }
}
