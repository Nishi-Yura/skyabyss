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
        // 燃え尽きの間 (AttackProps.afterburn) は試作 d だけが描く。ほかの見た目は元の命で消える
        int after = AttackProps.afterburn(e.kind());
        if (after > 0 && jp.revati.skyabyss.client.look.GearLook.skill != 'd' && e.tickCount + partial - e.delay() > e.life() - e.delay() - after) {
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
    private static final java.util.Map<String, Float> DECAL_R = new java.util.HashMap<>();
    private static final net.minecraft.resources.ResourceLocation NONE = net.minecraft.resources.ResourceLocation.withDefaultNamespace("none");
    private static final net.minecraft.resources.ResourceLocation SHADOW = net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/shadow.png");

    private static boolean drawCube(AttackProp e, char look, float partial, PoseStack pose, MultiBufferSource buffers, int light, float sunk) {
        jp.revati.skyabyss.client.lowpoly.LowPolyMesh cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, "prop_" + e.kind());
        if (cube == null && look == 'd') {
            // 試作 d に無い形は a のまま
            look = 'a';
            cube = jp.revati.skyabyss.client.look.GearLookDraw.cube(look, "prop_" + e.kind());
        }
        if (cube == null) {
            return false;
        }
        float age = e.tickCount + partial - e.delay();
        int after = AttackProps.afterburn(e.kind());
        boolean burnLook = look == 'd' && after > 0;
        float life = Math.max(1, e.life() - e.delay() - (burnLook ? 0 : after));
        float u = age / life;
        // 試作 d: 燃え尽き (0..1)。燃える間は縮まず、燃え尽きの間に部位ごとに消える (BurnDraw)
        float burn = burnLook ? net.minecraft.util.Mth.clamp((age - (life - after)) / after, 0f, 1f) : 0f;
        float grow;
        if (age < 3f) {
            float t = Math.max(0f, age / 3f);
            grow = 1f + 2.2f * (float) Math.pow(t - 1f, 3) + 1.2f * (float) Math.pow(t - 1f, 2);
        } else {
            grow = burnLook || u < 0.8f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.2f, 0f, 1f);
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
        // 試作 d: 技は光そのものとして描く (世界の暗さに沈まず、面の向きの陰も付けない)
        int meshLight = look == 'd' ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT : light;
        if (look == 'd') {
            final float ss = e.size();
            jp.revati.skyabyss.client.look.BurnDraw.draw(cube, pose, buffers, 2.5f, e.tickCount + partial, burn, after,
                    (x, y, z, kind) -> burst(e, ss, x, y, z, kind));
        } else {
            jp.revati.skyabyss.client.look.GearLookDraw.mesh(cube, null, pose, buffers, meshLight, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 2.5f, false);
        }
        pose.popPose();
        // 床の絵 (割れ目など。tools/gen/pixel/pixel_extrude.py の DECALS): 4 tick で広がり、命の終わりに合わせて消える
        net.minecraft.resources.ResourceLocation decal = DECALS.computeIfAbsent(look + "/prop_" + e.kind(), key -> {
            String k = key.substring(2);
            net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(jp.revati.skyabyss.SkyAbyss.MODID,
                    "textures/lowpoly/look/" + key.charAt(0) + "/" + k + "_decal.png");
            var res = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) {
                return NONE;
            }
            // 絵の幅で広さを決める (32px = 半径 1.7、64px 以上 = 3.4。128px は同じ広さで細かさ 2 倍)。PNG の幅は 16 バイト目からの 4 バイト
            try (java.io.InputStream in = res.get().open()) {
                byte[] head = in.readNBytes(24);
                int w = ((head[16] & 0xFF) << 24) | ((head[17] & 0xFF) << 16) | ((head[18] & 0xFF) << 8) | (head[19] & 0xFF);
                DECAL_R.put(key, 1.7f * Math.min(64, Math.max(1, w)) / 32f);
            } catch (Exception ex) {
                DECAL_R.put(key, 1.7f);
            }
            return id;
        });
        if (decal != NONE && !flying) {
            pose.pushPose();
            pose.translate(0, sunk, 0);
            float r = DECAL_R.getOrDefault(look + "/prop_" + e.kind(), 1.7f) * net.minecraft.util.Mth.clamp(age / 4f, 0.2f, 1f);
            float fade = burnLook ? net.minecraft.util.Mth.clamp((1f - burn) / 0.3f, 0f, 1f)
                    : u < 0.7f ? 1f : net.minecraft.util.Mth.clamp((1f - u) / 0.3f, 0f, 1f);
            com.mojang.blaze3d.vertex.VertexConsumer buf = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityTranslucentEmissive(decal));
            PoseStack.Pose p = pose.last();
            int c = ((int) (fade * 255f) << 24) | 0xFFFFFF;
            if (burnLook) {
                // ひびが冷える: 黄 → 赤 → 赤茶の焦げ跡
                float cool = net.minecraft.util.Mth.clamp(burn * 1.5f, 0f, 1f);
                int rr = (int) (255f * (1f - 0.55f * cool)), gg = (int) (255f * (1f - 0.85f * cool)), bb = (int) (255f * (1f - 0.9f * cool));
                c = ((int) (fade * 255f) << 24) | (rr << 16) | (gg << 8) | bb;
            }
            float[][] q = {{-r, -r, 0, 0}, {-r, r, 0, 1}, {r, r, 1, 1}, {r, -r, 1, 0}};
            for (float[] v : q) {
                buf.addVertex(p, v[0], 0.03f, v[1]).setColor(c).setUv(v[2], v[3]).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .setLight(net.minecraft.client.renderer.LightTexture.FULL_BRIGHT).setNormal(p, 0, 1, 0);
            }
            pose.popPose();
        }
        return true;
    }

    // 試作 d の燃え尽きの粒 (BurnDraw.Sink)。模型の座標 (Y 軸 180 度回し、技の大きさ倍) を世界へ。同じ所からは 1 度だけ
    private static final java.util.Set<Long> BURST = new java.util.HashSet<>();

    private static void burst(AttackProp e, float s, float x, float y, float z, int kind) {
        long key = ((long) e.getId() << 32) ^ ((long) kind << 28) ^ (Float.floatToIntBits(Math.round(x * 8f) * 31f + Math.round(z * 8f)) & 0x0FFFFFFFL);
        if (!BURST.add(key)) {
            return;
        }
        if (BURST.size() > 8192) {
            BURST.clear();
        }
        net.minecraft.world.level.Level level = e.level();
        double wx = e.getX() - x * s, wy = e.getY() + y * s, wz = e.getZ() - z * s;
        net.minecraft.util.RandomSource rnd = level.random;
        switch (kind) {
            case jp.revati.skyabyss.client.look.BurnDraw.SMOKE -> {
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.4, wz, 0, 0.04, 0);
                level.addParticle(net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.02, 0.03, (rnd.nextFloat() - 0.5f) * 0.02);
            }
            case jp.revati.skyabyss.client.look.BurnDraw.ROCK -> {
                var opt = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                for (int i = 0; i < 6; i++) {
                    level.addParticle(opt, wx, wy + 0.1, wz, (rnd.nextFloat() - 0.5f) * 0.3, 0.15 + rnd.nextFloat() * 0.15, (rnd.nextFloat() - 0.5f) * 0.3);
                }
                level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.1, wz, 0, 0.02, 0);
            }
            default -> {
                var stone = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BLACKSTONE.defaultBlockState());
                var magma = new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.MAGMA_BLOCK.defaultBlockState());
                for (int i = 0; i < 18; i++) {
                    level.addParticle(i % 3 == 0 ? magma : stone, wx, wy + 0.2, wz, (rnd.nextFloat() - 0.5f) * 0.4, 0.1 + rnd.nextFloat() * 0.2, (rnd.nextFloat() - 0.5f) * 0.4);
                }
                for (int i = 0; i < 3; i++) {
                    level.addParticle(net.minecraft.core.particles.ParticleTypes.WHITE_SMOKE, wx, wy + 0.4, wz, (rnd.nextFloat() - 0.5f) * 0.05, 0.05, (rnd.nextFloat() - 0.5f) * 0.05);
                }
            }
        }
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
