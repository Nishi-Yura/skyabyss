package jp.revati.skyabyss.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyMesh;
import jp.revati.skyabyss.client.lowpoly.LowPolyMeshes;
import jp.revati.skyabyss.skin.Skins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderArmEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

// 体ごとのスキン (Yes Steve Model のような作り。skin/Skins の、形 lowpoly/skin_full/<id>.json があるもの)。
//   着ている人はバニラの体の箱を隠し、スキンの形をプレイヤーの姿勢どおりに描く (手に持つ品・矢などのほかの層はそのまま)。防具は描かない
//   形の JSON が "outfit": true のものは服だけ: バニラの体 (その人のスキンの顔と体) を残し、上に服を重ねる
//   骨: head / body / armR / armL / legR / legL はバニラの体の部分と同じに動く (回転の中心もバニラのまま)。それ以外の骨は親に付いて動き、名前の頭で揺れる:
//     hair・cloth・cape・ribbon・tail = 歩く速さと向きの変わりで後ろへなびき、遅れて戻る (ばね)。plate = 同じだが少しだけ (鎧の板)。skirt は脚の振りでも広がる
//     float = 浮かぶ飾り。ゆっくり上下して揺れる。orbit = 付け根のまわりを回る (子に float を付けて回しながら上下)。wing = ゆっくり羽ばたく (歩くと強く)
//   形は箱で組んでドット絵を描く (tools/rough/skinbox)。座標は geo (1/16 ブロック、足もと y = 0、前 = -Z)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class FullSkinLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final Map<Integer, Sway> SWAY = new HashMap<>();

    public FullSkinLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    // 着ている体ごとのスキンの形 (無ければ null)
    @Nullable
    public static LowPolyMesh mesh(Player p) {
        String id = Skins.worn(p);
        return id == null || !LowPoly.enabled ? null : LowPolyMeshes.get("skin_full/" + id);
    }

    // ---------------------------------------------------------------- 揺れ (人ごと。毎 tick ばねを進める)
    static final class Sway {
        double px;
        double pz;
        float prevYaw;
        boolean init;
        // なびき (前へ歩く・横へ歩く・向きを変える) の今の値と速さ
        float back;
        float backV;
        float side;
        float sideV;
        float speed;

        void tick(Player p) {
            if (!init) {
                px = p.getX();
                pz = p.getZ();
                prevYaw = p.yBodyRot;
                init = true;
            }
            double dx = p.getX() - px;
            double dz = p.getZ() - pz;
            px = p.getX();
            pz = p.getZ();
            float yawRad = p.yBodyRot * Mth.DEG_TO_RAD;
            double fwd = -dx * Mth.sin(yawRad) + dz * Mth.cos(yawRad);
            double right = -dx * Mth.cos(yawRad) - dz * Mth.sin(yawRad);
            float turn = Mth.wrapDegrees(p.yBodyRot - prevYaw);
            prevYaw = p.yBodyRot;
            speed = (float) Math.min(1.0, Math.hypot(dx, dz) / 0.28);
            float targetBack = (float) Mth.clamp(fwd * 260.0, -25, 70) + (p.onGround() ? 0 : 12);
            float targetSide = (float) Mth.clamp(right * 180.0 + turn * 1.6, -35, 35);
            // ばね (遅れて追い、少し行き過ぎて戻る)
            backV += (targetBack - back) * 0.22f;
            backV *= 0.68f;
            back += backV;
            sideV += (targetSide - side) * 0.22f;
            sideV *= 0.66f;
            side += sideV;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            return;
        }
        for (AbstractClientPlayer p : mc.level.players()) {
            if (mesh(p) != null) {
                SWAY.computeIfAbsent(p.getId(), k -> new Sway()).tick(p);
            }
        }
        if (mc.level.getGameTime() % 200 == 0) {
            SWAY.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        }
    }

    // ---------------------------------------------------------------- バニラの体を隠す (描くあいだだけ)
    @SubscribeEvent
    static void onRenderPre(RenderPlayerEvent.Pre event) {
        LowPolyMesh mesh = event.getEntity() instanceof AbstractClientPlayer p ? mesh(p) : null;
        if (mesh != null && !mesh.outfit) {
            setBody(event.getRenderer().getModel(), false);
        }
    }

    @SubscribeEvent
    static void onRenderPost(RenderPlayerEvent.Post event) {
        setBody(event.getRenderer().getModel(), true);
    }

    static void setBody(PlayerModel<?> m, boolean visible) {
        m.head.visible = visible;
        m.hat.visible = visible;
        m.body.visible = visible;
        m.jacket.visible = visible;
        m.leftArm.visible = visible;
        m.rightArm.visible = visible;
        m.leftSleeve.visible = visible;
        m.rightSleeve.visible = visible;
        m.leftLeg.visible = visible;
        m.rightLeg.visible = visible;
        m.leftPants.visible = visible;
        m.rightPants.visible = visible;
    }

    // 一人称の手: バニラの腕の代わりにスキンの腕 (と袖などの子の骨) を描く
    @SubscribeEvent
    static void onRenderArm(RenderArmEvent event) {
        AbstractClientPlayer player = event.getPlayer();
        LowPolyMesh mesh = mesh(player);
        if (mesh == null || !(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer r)) {
            return;
        }
        boolean right = event.getArm() == net.minecraft.world.entity.HumanoidArm.RIGHT;
        LowPolyMesh.Bone bone = mesh.bones.get(right ? "armR" : "armL");
        if (bone == null) {
            return;
        }
        PlayerModel<AbstractClientPlayer> m = r.getModel();
        // 服だけのスキンはバニラの腕 (自分のスキン) をこのあと描かせ、袖だけ重ねる
        if (!mesh.outfit) {
            event.setCanceled(true);
        }
        m.attackTime = 0f;
        m.crouching = false;
        m.swimAmount = 0f;
        m.setupAnim(player, 0f, 0f, 0f, 0f, 0f);
        ModelPart arm = right ? m.rightArm : m.leftArm;
        arm.xRot = 0f;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        arm.translateAndRotate(pose);
        pose.translate(-arm.x / 16f, -arm.y / 16f, -arm.z / 16f);
        pose.translate(0f, 1.5f, 0f);
        pose.scale(-1f, -1f, 1f);
        Sway sway = SWAY.computeIfAbsent(player.getId(), k -> new Sway());
        drawTree(pose, event.getMultiBufferSource().getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), false, mesh, m, bone, sway, player.tickCount,
                event.getPackedLight(), OverlayTexture.NO_OVERLAY, 0f, 0f, false);
        pose.popPose();
    }

    // ---------------------------------------------------------------- 描く
    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
            float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        LowPolyMesh mesh = mesh(player);
        if (mesh == null || player.isInvisible()) {
            return;
        }
        Sway sway = SWAY.computeIfAbsent(player.getId(), k -> new Sway());
        PlayerModel<AbstractClientPlayer> m = getParentModel();
        int overlay = LivingEntityRenderer.getOverlayCoords(player, 0f);
        // 二度に分けて描く (体の絵 → 光る画素)。描き先を一度に二つ取ると、先の方が閉じられる
        drawAll(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(mesh.texture)), false, mesh, m, sway, ageInTicks + partialTick, light, overlay, limbSwing, limbSwingAmount);
        if (mesh.glowMask != null) {
            drawAll(pose, buffers.getBuffer(RenderType.entityTranslucentEmissive(mesh.glowMask)), true, mesh, m, sway, ageInTicks + partialTick, FULL_BRIGHT, overlay, limbSwing,
                    limbSwingAmount);
        }
    }

    private static void drawAll(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buf, boolean glow, LowPolyMesh mesh, PlayerModel<AbstractClientPlayer> m, Sway sway,
            float t, int light, int overlay, float limbSwing, float limbSwingAmount) {
        for (LowPolyMesh.Bone b : mesh.bones.values()) {
            boolean root = b.parent == null || !mesh.bones.containsKey(b.parent);
            if (root || part(m, b.name) != null) {
                pose.pushPose();
                ModelPart part = part(m, b.name);
                if (part != null) {
                    part.translateAndRotate(pose);
                    pose.translate(-part.x / 16f, -part.y / 16f, -part.z / 16f);
                }
                // geo (足もと y = 0、上へ +y。メッシュは x を反転して焼いてある) → 体の空間 (首の上が原点、下へ +y)
                pose.translate(0f, 1.5f, 0f);
                pose.scale(-1f, -1f, 1f);
                drawTree(pose, buf, glow, mesh, m, b, sway, t, light, overlay, limbSwing, limbSwingAmount, part == null);
                pose.popPose();
            }
        }
    }

    private static void drawTree(PoseStack pose, com.mojang.blaze3d.vertex.VertexConsumer buf, boolean glow, LowPolyMesh mesh,
            PlayerModel<AbstractClientPlayer> m, LowPolyMesh.Bone b, Sway sway, float t, int light, int overlay, float limbSwing, float limbAmount, boolean animate) {
        pose.pushPose();
        if (animate) {
            motion(pose, b, sway, t, limbSwing, limbAmount);
        }
        if (glow) {
            LowPoly.drawBone(pose, buf, b, light, overlay, 0xFFFFFFFF, 0f, false);
        } else {
            LowPoly.drawBone(pose, buf, b, light, overlay, 0xFFFFFFFF, 0.25f);
        }
        for (LowPolyMesh.Bone c : mesh.bones.values()) {
            if (b.name.equals(c.parent) && part(m, c.name) == null) {
                drawTree(pose, buf, glow, mesh, m, c, sway, t, light, overlay, limbSwing, limbAmount, true);
            }
        }
        pose.popPose();
    }

    // 骨の名前の頭で決まる揺れ (付け根まわりの回転と、浮かぶ飾りの上下)。回転は描く空間 (x 反転ずみ) の軸
    static void motion(PoseStack pose, LowPolyMesh.Bone b, Sway s, float t, float limbSwing, float limbAmount) {
        String n = b.name.toLowerCase(Locale.ROOT);
        float px = -b.pivot[0] / 16f;
        float py = b.pivot[1] / 16f;
        float pz = b.pivot[2] / 16f;
        float phase = (n.hashCode() & 0xFF) / 40f;
        if (n.startsWith("orbit")) {
            // 付け根のまわりをゆっくり回る (22 秒で一周)
            pose.translate(px, py, pz);
            pose.mulPose(Axis.YP.rotationDegrees((t * 0.8f) % 360f));
            pose.translate(-px, -py, -pz);
            return;
        }
        if (n.startsWith("float")) {
            // ひとつずつ上下し、少し傾き、自分のまわりをゆっくり回る
            pose.translate(0f, Mth.sin(t * 0.07f + phase) * 0.06f, 0f);
            pose.translate(px, py, pz);
            pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(t * 0.03f + phase) * 25f));
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.05f + phase) * 6f));
            pose.translate(-px, -py, -pz);
            return;
        }
        float rx = 0f;
        float rz = 0f;
        float ry = 0f;
        if (n.startsWith("wing")) {
            ry = Mth.sin(t * 0.12f + phase) * (6f + s.speed * 10f) * (b.pivot[0] < 0 ? -1f : 1f);
            rz = Mth.sin(t * 0.12f + phase + 0.6f) * 3f;
        } else if (n.startsWith("hair") || n.startsWith("cloth") || n.startsWith("cape") || n.startsWith("ribbon") || n.startsWith("tail") || n.startsWith("plate")) {
            float k = n.startsWith("plate") ? 0.2f : n.startsWith("hair") ? 0.55f : n.startsWith("cape") ? 1.0f : 0.8f;
            // 前へ進むと下の端が後ろ (+z) へ、右へ進む・右を向くと左 (描く空間の -x) へ
            rx = -s.back * k + Mth.sin(t * 0.08f + phase) * 2.5f;
            rz = -s.side * k * 0.6f + Mth.sin(t * 0.06f + phase * 1.7f) * 1.5f;
        } else if (n.startsWith("skirt")) {
            float legs = Math.abs(Mth.cos(limbSwing * 0.6662f)) * limbAmount * 28f;
            // 脚の振りで外へ広がり (前の布は前へ・後ろは後ろへ・横は横へ)、進むと後ろへなびく
            float front = b.pivot[2] < -0.5f ? 1f : b.pivot[2] > 0.5f ? -1f : 0f;
            float sideDir = b.pivot[0] < -0.5f ? 1f : b.pivot[0] > 0.5f ? -1f : 0f;
            rx = legs * front - s.back * 0.35f;
            rz = legs * 0.6f * sideDir - s.side * 0.3f;
        } else {
            return;
        }
        pose.translate(px, py, pz);
        pose.mulPose(Axis.XP.rotationDegrees(rx));
        pose.mulPose(Axis.ZP.rotationDegrees(rz));
        pose.mulPose(Axis.YP.rotationDegrees(ry));
        pose.translate(-px, -py, -pz);
    }

    @Nullable
    static ModelPart part(PlayerModel<?> m, String name) {
        return switch (name) {
            case "head" -> m.head;
            case "body" -> m.body;
            case "armR", "right_arm" -> m.rightArm;
            case "armL", "left_arm" -> m.leftArm;
            case "legR", "right_leg" -> m.rightLeg;
            case "legL", "left_leg" -> m.leftLeg;
            default -> null;
        };
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
    static final class Register {
        @SubscribeEvent
        static void onAddLayers(EntityRenderersEvent.AddLayers event) {
            for (var skin : event.getSkins()) {
                if (event.getSkin(skin) instanceof PlayerRenderer r) {
                    r.addLayer(new FullSkinLayer(r));
                }
            }
        }
    }
}
