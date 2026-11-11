package jp.revati.skyabyss.client.entity;

import java.util.Set;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.abyss.boss.BossLook;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

// 心臓の後のボスの見せ方 (体のまわりに足すもの。2026-11-08 までの B 切り絵の菱形は、ボスごとの見せ方に替えて外した)。ボスごとに題が違う (2026-11-08 の選定。ラフは docs/review/roughs_boss_looks/):
//   金継ぎ (LORD)   : 五章の淵の主。頭の後ろに巨神の色の輪の後光 (ゆっくり回る)、熾の粉が昇る (tools/gen/mobs/boss_looks_paint.py が体を塗る)
//   螺鈿の月 (MOON) : 六章の月の主。背に銀の三日月、星屑がきらめく
//   喪服の王 (KING) : 五章の深みの王。冠の上に二重の金の輪 (逆に回る)、黒い花びらが降る
//   錆の門番 (WARDEN): 心臓の番人。背に深淵の青緑の輪 (鍵穴の後光)、錆の粉が落ちる
//   雲の喪章 (FOUNDER): 終章の主たち (始祖・最後の鐘守・巡礼者・巻き戻す者・灯の目)。白い羽根が降る (後光はなし)
// 体の大きさに合わせて半径と高さを決める。倒れているあいだは出さない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class PaperCutLayer {
    static final ResourceLocation RING = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/halo_ring.png");
    static final ResourceLocation CRESCENT = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/halo_crescent.png");
    // 終章の主たち (雲の喪章。始祖と同じく白い羽根が降る)
    static final Set<String> CLOUDS = Set.of("founder", "lamp_eye", "last_bellkeeper", "pilgrim_eye", "pilgrim_hand", "pilgrim_moon", "pilgrim_plume", "rewinder");
    static final Set<String> MOONS = Set.of("full_keeper", "moon_core", "moon_scale", "shadow_embracer", "waning_blade");
    // 淵の主の色 (BossLook.LORD_RIB.. の順): 肋 = 骨の金白、塩 = 潮の青緑、嵐 = 紫、灰 = 熾の橙
    static final int[] LORD_RGB = {0xFF5040, 0x5AE6D2, 0xB088FF, 0xFF963C};
    // 深みの王の輪の色 (2026-11-11 の見た目の分け直し。骨 = 赤、塩 = 潮の青緑、雷 = 青、灰 = 熾の橙、影 = 白)。五体とも金の同じ輪だったのをやめる
    static final int[] KING_RGB = {0xFF4A3A, 0x5AE6D2, 0x6AB4FF, 0xFF8A2A, 0xE6E6F5};
    static final float[][] KING_PETAL = {{0.35f, 0.04f, 0.05f}, {0.80f, 0.86f, 0.84f}, {0.40f, 0.60f, 0.95f}, {0.20f, 0.18f, 0.16f}, {0.04f, 0.03f, 0.07f}};

    static int kingIndex(LivingEntity e) {
        int id = e instanceof jp.revati.skyabyss.entity.GuardMob g ? BossLook.id(g.bossLook()) : 0;
        int i = id - BossLook.KING_BONE;
        return i >= 0 && i < KING_RGB.length ? i : 0;
    }

    public enum Style { NONE, LORD, MOON, KING, WARDEN, FOUNDER }

    private PaperCutLayer() {
    }

    public static Style style(LivingEntity e) {
        if (e instanceof jp.revati.skyabyss.entity.GuardMob g && g.bossLook() != 0) {
            int id = BossLook.id(g.bossLook());
            return id >= BossLook.LORD_RIB && id <= BossLook.LORD_ASH ? Style.LORD : Style.KING;
        }
        ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (!key.getNamespace().equals(SkyAbyss.MODID)) {
            return Style.NONE;
        }
        if (key.getPath().equals("heart_warden")) {
            // 番人は光の中でだけ見える (闇の中で後光が居場所を明かさない)
            boolean seen = e instanceof jp.revati.skyabyss.story.ch5.warden.HeartWarden w && !w.decoy()
                    && (w.lit() || w.hasEffect(net.minecraft.world.effect.MobEffects.GLOWING));
            return seen ? Style.WARDEN : Style.NONE;
        }
        if (CLOUDS.contains(key.getPath())) {
            return Style.FOUNDER;
        }
        if (MOONS.contains(key.getPath())) {
            return Style.MOON;
        }
        return Style.NONE;
    }

    public static int lordRgbOf(LivingEntity e) {
        return lordRgb(e);
    }

    static int lordRgb(LivingEntity e) {
        int id = e instanceof jp.revati.skyabyss.entity.GuardMob g ? BossLook.id(g.bossLook()) : 0;
        int i = id - BossLook.LORD_RIB;
        return i >= 0 && i < LORD_RGB.length ? LORD_RGB[i] : 0xFFFFFF;
    }

    // GeckoLib の描き手は RenderLivingEvent を出さないので、世界を描き終えたところで、近くのボスのまわりに描く
    @SubscribeEvent
    static void onStage(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() != net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float pt = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        net.minecraft.world.phys.Vec3 cam = event.getCamera().getPosition();
        org.joml.Quaternionf face = event.getCamera().rotation();
        PoseStack pose = event.getPoseStack();
        net.minecraft.client.renderer.MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (net.minecraft.world.entity.Entity ent : mc.level.entitiesForRendering()) {
            // 地の中から這い出る間 (combat/BossEmerge) は輪を出さない (体が抜けきってから)
            if (!(ent instanceof LivingEntity e) || e.distanceToSqr(cam) > 96 * 96 || e.isDeadOrDying() || e.isInvisible()
                    || jp.revati.skyabyss.client.titan.BossEmergeClient.active(e)) {
                continue;
            }
            Style st = style(e);
            if (st == Style.NONE) {
                continue;
            }
            double x = Mth.lerp(pt, e.xo, e.getX());
            double y = Mth.lerp(pt, e.yo, e.getY());
            double z = Mth.lerp(pt, e.zo, e.getZ());
            float t = (e.tickCount + pt) / 20f;
            pose.pushPose();
            pose.translate(x - cam.x, y - cam.y, z - cam.z);
            switch (st) {
                case LORD -> halo(pose, buf, RING, e, t, face, cam, x, y, z, lordRgb(e), 0.86f, true);
                case MOON -> halo(pose, buf, CRESCENT, e, t, face, cam, x, y, z, 0xDCEBFF, 0.66f, false);
                case WARDEN -> halo(pose, buf, RING, e, t, face, cam, x, y, z, 0x5AE6D2, 0.78f, true);
                case KING -> halo(pose, buf, RING, e, t, face, cam, x, y, z, KING_RGB[kingIndex(e)], 1.02f, true);
                default -> {
                }
            }
            pose.popPose();
        }
        buf.endBatch(RenderType.entityTranslucent(RING));
        buf.endBatch(RenderType.entityTranslucent(CRESCENT));
        buf.endBatch(RenderType.eyes(RING));
        buf.endBatch(RenderType.eyes(CRESCENT));
    }

    // 後光: 体の高さ at の所、カメラから見て体の向こうへ少し下げた所に、カメラを向く板。spin なら板の面の中でゆっくり回る (内側にもう一つ逆回りの輪)
    // 足して描く光は明るい空の前で白く飛ぶ (2026-11-08 の撮影: 昼の王の金の輪が白く見えた) ので、同じ形の暗い影を先に敷いてから光を足す
    static void halo(PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buf, ResourceLocation tex, LivingEntity e, float t, org.joml.Quaternionf face, net.minecraft.world.phys.Vec3 cam,
                     double ex, double ey, double ez, int rgb, float at, boolean spin) {
        // 淵の主の模型は当たりの箱より大きく描かれる (BossLook の型の大きさ) ので、箱より上・大きめに
        boolean big = e instanceof jp.revati.skyabyss.entity.GuardMob g && g.bossLook() != 0;
        float w = e.getBbWidth() * (big ? 1.4f : 1f);
        float h = e.getBbHeight() * (big ? 1.5f : 1f);
        float s = (Math.max(w * 0.55f, h * 0.32f) + 0.6f) * (big ? 1.35f : 1f);
        double dx = ex - cam.x;
        double dz = ez - cam.z;
        double len = Math.max(1e-3, Math.sqrt(dx * dx + dz * dz));
        pose.pushPose();
        pose.translate(dx / len * w * 0.9, h * at, dz / len * w * 0.9);
        pose.mulPose(face);
        float pulse = 0.85f + 0.15f * Mth.sin(t * 2.2f);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(spin ? t * 0.4f : 0.35f * Mth.sin(t * 0.5f)));
        VertexConsumer back = buf.getBuffer(RenderType.entityTranslucent(tex));
        shade(pose.last().pose(), back, s * 1.04f, 150);
        if (spin) {
            org.joml.Matrix4f inner = new org.joml.Matrix4f(pose.last().pose()).rotateZ(-t * 0.9f);
            shade(inner, back, s * 0.68f, 110);
        }
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(tex));
        tinted(pose.last().pose(), vc, s, rgb, pulse);
        if (spin) {
            pose.mulPose(com.mojang.math.Axis.ZP.rotation(-t * 0.9f));
            tinted(pose.last().pose(), vc, s * 0.66f, rgb, pulse * 0.6f);
        }
        pose.popPose();
    }

    // 暗い影 (黒。絵の透けの形のまま)
    private static void shade(Matrix4f m, VertexConsumer vc, float s, int alpha) {
        int light = LightTexture.FULL_BRIGHT;
        int ov = OverlayTexture.NO_OVERLAY;
        vc.addVertex(m, -s, -s, -0.01f).setColor(0, 0, 0, alpha).setUv(0, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, -s, -0.01f).setColor(0, 0, 0, alpha).setUv(1, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, s, -0.01f).setColor(0, 0, 0, alpha).setUv(1, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, -s, s, -0.01f).setColor(0, 0, 0, alpha).setUv(0, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
    }

    private static void tinted(Matrix4f m, VertexConsumer vc, float s, int rgb, float a) {
        int r = (int) (((rgb >> 16) & 0xFF) * a);
        int g = (int) (((rgb >> 8) & 0xFF) * a);
        int b = (int) ((rgb & 0xFF) * a);
        int light = LightTexture.FULL_BRIGHT;
        int ov = OverlayTexture.NO_OVERLAY;
        vc.addVertex(m, -s, -s, 0).setColor(r, g, b, 255).setUv(0, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, -s, 0).setColor(r, g, b, 255).setUv(1, 1).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, s, s, 0).setColor(r, g, b, 255).setUv(1, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
        vc.addVertex(m, -s, s, 0).setColor(r, g, b, 255).setUv(0, 0).setOverlay(ov).setLight(light).setNormal(0, 0, 1);
    }

    // 漂うもの: 淵の主は巨神の色の熾の粉が昇る、月の主は星屑がきらめく (2 tick に二つ)
    @SubscribeEvent
    static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.level.getGameTime() % 2 != 0) {
            return;
        }
        for (net.minecraft.world.entity.Entity ent : mc.level.entitiesForRendering()) {
            if (!(ent instanceof LivingEntity e) || e.distanceToSqr(mc.player) > 64 * 64 || e.isDeadOrDying() || e.isInvisible()
                    || jp.revati.skyabyss.client.titan.BossEmergeClient.active(e)) {
                continue;
            }
            Style st = style(e);
            if (st == Style.NONE) {
                continue;
            }
            var r = e.getRandom();
            float w = e.getBbWidth();
            float h = e.getBbHeight();
            for (int i = 0; i < 2; i++) {
                double px = e.getX() + (r.nextDouble() - 0.5) * w * 1.6;
                double py = e.getY() + r.nextDouble() * h;
                double pz = e.getZ() + (r.nextDouble() - 0.5) * w * 1.6;
                if (st == Style.LORD) {
                    int c = lordRgb(e);
                    mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(((c >> 16) & 0xFF) / 255f, ((c >> 8) & 0xFF) / 255f, (c & 0xFF) / 255f), 1.1f), px, py, pz, 0, 0.04, 0);
                } else if (st == Style.FOUNDER) {
                    // 白い羽根: 頭の上から、揺れながら落ちる (1 tick おきに一つだけ)
                    if (i == 0) {
                        mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.96f, 0.96f, 1f), 1.8f),
                                px, e.getY() + h * (1.0 + r.nextDouble() * 0.5), pz, (r.nextDouble() - 0.5) * 0.04, -0.025, 0);
                    }
                } else if (st == Style.WARDEN) {
                    mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(0.45f, 0.22f, 0.12f), 0.9f), px, py, pz, 0, -0.02, 0);
                } else if (st == Style.KING) {
                    // 黒い花びら: 頭の上から、ゆっくり落ちる
                    float[] pc = KING_PETAL[kingIndex(e)];
                    mc.level.addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(pc[0], pc[1], pc[2]), 1.6f),
                            px, e.getY() + h * (1.1 + r.nextDouble() * 0.4), pz, 0, -0.03, 0);
                } else {
                    mc.level.addParticle(net.minecraft.core.particles.ParticleTypes.END_ROD, px, py, pz, 0, 0.01, 0);
                }
            }
        }
    }
}
