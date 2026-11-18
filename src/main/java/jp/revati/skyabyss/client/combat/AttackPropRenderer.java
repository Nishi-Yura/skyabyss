package jp.revati.skyabyss.client.combat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.combat.prop.AttackProp;
import jp.revati.skyabyss.combat.prop.AttackProps;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 技の形 (combat/prop/AttackProp) の描画: 低ポリの模型 prop_<種類> を、大きさ (size) を掛けて描く。
// RISE は床から突き出る分だけ下へずらし (床の下は床に隠れる)、TOSS は進む向きへ頭を傾け、FALL の待ちの間は描かない
public class AttackPropRenderer extends LowPolyGeoRenderer<AttackProp> {
    // 突き出る深さ (ブロック、size 1 のとき)。模型の高さにおおよそ合わせる
    private static final float RISE_DEPTH = 3.6f;

    public AttackPropRenderer(EntityRendererProvider.Context ctx, String kind) {
        super(ctx, "prop_" + kind, 1f);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(AttackProp e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        if (e.hidden(partial)) {
            return;
        }
        pose.pushPose();
        float s = e.size();
        float sunk = 0f;
        if (e.mode() == AttackProp.RISE) {
            sunk = (1f - e.rise(partial)) * RISE_DEPTH;
            pose.translate(0, -sunk * s, 0);
        }
        pose.scale(s, s, s);
        // 試作 (client/look/GearLook): 自分の技の形は、立方体の模型があればそれを (tools/gen/cube/cube_gear.py)
        char look = jp.revati.skyabyss.client.look.GearLook.skill;
        if (look != '0' && jp.revati.skyabyss.client.look.GearLook.playerProp(e.kind()) && drawCube(e, look, partial, pose, buffers, light, sunk)) {
            pose.popPose();
            return;
        }
        super.render(e, yaw, partial, pose, buffers, light);
        pose.popPose();
    }

    // 試作の立方体の技 (4 回目): 現れるとき 3 tick で少し行き過ぎて大きくなり、命の後ろ 2 割で縮んで消える。
    // 世界の明るさで描き、カメラから 2.5 ブロック以内の面は描かない。床に立つ形には足もとの影と、現れたときの床の土ぼこり (床のブロックの粒) をつける
    private static final java.util.Set<Integer> DUSTED = new java.util.HashSet<>();
    private static final java.util.Map<String, net.minecraft.resources.ResourceLocation> DECALS = new java.util.HashMap<>();
    private static final net.minecraft.resources.ResourceLocation NONE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("none");
    private static final net.minecraft.resources.ResourceLocation SHADOW = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");

    private static boolean drawCube(AttackProp e, char look, float partial, PoseStack pose, MultiBufferSource buffers, int light, float sunk) {
        jp.revati.skyabyss.client.lowpoly.LowPolyMesh cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, "prop_" + e.kind());
        if (cube == null) {
            return false;
        }
        float age = e.tickCount + partial - e.delay();
        float life = Math.max(1, e.life() - e.delay());
        float u = age / life;
        float grow;
        if (age < 3f) {
            float t = Math.max(0f, age / 3f);
            grow = 1f + 2.2f * (float) Math.pow(t - 1f, 3) + 1.2f * (float) Math.pow(t - 1f, 2);
        } else {
            grow = u < 0.8f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.2f, 0f, 1f);
        }
        if (grow <= 0.01f) {
            return true;
        }
        boolean flying = e.mode() == AttackProp.TOSS || e.mode() == AttackProp.FALL;
        if (!flying && age >= 0f && DUSTED.add(e.getId())) {
            if (DUSTED.size() > 512) {
                DUSTED.clear();
            }
            dust(e, e.size());
        }
        if (!flying) {
            pose.pushPose();
            pose.translate(0, sunk, 0);
            shadow(pose, buffers, 0f, 0.02f, 0f, 1.0f, 0.5f * Math.min(1f, grow));
            pose.popPose();
        }
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(180f));
        float pitch = e.pitch(partial);
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
        pose.scale(grow, grow, grow);
        jp.revati.skyabyss.client.look.GearLookDraw.mesh(cube, null, pose, buffers, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 2.5f);
        pose.popPose();
        // 床の絵 (割れ目など。tools/gen/pixel/pixel_extrude.py の DECALS): 4 tick で広がり、命の終わりに合わせて消える
        net.minecraft.resources.ResourceLocation decal = DECALS.computeIfAbsent("prop_" + e.kind(), k -> {
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID,
                    "textures/lowpoly/look/" + look + "/" + k + "_decal.png");
            return net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? id : NONE;
        });
        if (decal != NONE && !flying) {
            pose.pushPose();
            pose.translate(0, sunk, 0);
            float r = 1.7f * net.minecraft.util.Mth.clamp(age / 4f, 0.2f, 1f);
            float fade = u < 0.7f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.3f, 0f, 1f);
            com.mojang.blaze3d.vertex.VertexConsumer buf = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(decal));
            PoseStack.Pose p = pose.last();
            int c = ((int) (fade * 255f) << 24) | 0xFFFFFF;
            float[][] q = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
            for (float[] v : q) {
                buf.addVertex(p, v[0], 0.03f, v[1]).setColor(c).setUv(v[2], v[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            }
            pose.popPose();
        }
        return true;
    }

    // 丸い柔らかい影 (バニラの影の絵) を床に一枚
    private static void shadow(PoseStack pose, MultiBufferSource buffers, float cx, float y, float cz, float r, float alpha) {
        com.mojang.blaze3d.vertex.VertexConsumer buf = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityShadow(SHADOW));
        PoseStack.Pose p = pose.last();
        int c = ((int) (net.minecraft.util.Mth.clamp(alpha, 0f, 1f) * 255f) << 24) | 0xFFFFFF;
        float[][] v = {{cx - r, cz - r, 0, 0}, {cx - r, cz + r, 0, 1}, {cx + r, cz + r, 1, 1}, {cx + r, cz - r, 1, 0}};
        for (float[] q : v) {
            buf.addVertex(p, q[0], y, q[1]).setColor(c).setUv(q[2], q[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
        }
    }

    // 足もとのブロックの粒 (バニラの壊れる粒) を、形のまわりに散らす
    private static void dust(AttackProp e, float s) {
        net.minecraft.world.level.Level level = e.level();
        net.minecraft.core.BlockPos below = e.blockPosition().below();
        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(below);
        if (state.isAir()) {
            below = below.below();
            state = level.getBlockState(below);
        }
        if (state.isAir()) {
            return;
        }
        var opt = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK, state);
        float r = 1.0f * s;
        var rnd = level.random;
        for (int i = 0; i < 16; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double d = 0.3 + rnd.nextDouble() * 0.7;
            level.addParticle(opt, e.getX() + Math.cos(a) * r * d, below.getY() + 1.05, e.getZ() + Math.sin(a) * r * d, Math.cos(a) * 0.08, 0.12 + rnd.nextDouble() * 0.1,
                    Math.sin(a) * 0.08);
        }
    }

    @Override
    protected void applyRotations(AttackProp e, PoseStack pose, float age, float yaw, float partial, float nativeScale) {
        super.applyRotations(e, pose, age, yaw, partial, nativeScale);
        float pitch = e.pitch(partial);
        if (pitch != 0f) {
            pose.translate(0, 0.25, 0);
            pose.mulPose(Axis.XP.rotationDegrees(pitch));
            pose.translate(0, -0.25, 0);
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Register {
        private Register() {
        }

        @SubscribeEvent
        static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
            for (var en : AttackProps.TYPES.entrySet()) {
                String kind = en.getKey();
                event.registerEntityRenderer(en.getValue().get(), ctx -> new AttackPropRenderer(ctx, kind));
            }
        }
    }
}
