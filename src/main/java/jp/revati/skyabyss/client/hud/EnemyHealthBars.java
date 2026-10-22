package jp.revati.skyabyss.client.hud;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

// 敵の頭の上の体力の帯 (2026-10-23 の試遊: 雑魚でも残りの体力が見たい)。敵 (Enemy) なら mod の敵もバニラの敵も。
// 画面上の大きな帯が出るボス (ボスの帯の持ち主) と、見えない・死にかけの体は描かない。近い RANGE マスまで。
// 傷を受けていない敵は細い縁だけ薄く (群れのとき画面がうるさくならないように)、傷を受けると赤い帯が濃く出る。
// 白い遅れの帯が、減った分を少し遅れて追う。設定 enemyHealthBars で消せる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class EnemyHealthBars {
    static final double RANGE = 24;
    static final float WIDTH = 1.0f;
    static final float HEIGHT = 0.1f;
    // 遅れの帯 (体の id → 表示中の割合)
    private static final java.util.Map<Integer, Float> LAG = new java.util.HashMap<>();
    private static long lagTick;

    private EnemyHealthBars() {
    }

    @SubscribeEvent
    static void onRender(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity e = event.getEntity();
        Minecraft mc = Minecraft.getInstance();
        if (!(e instanceof Enemy) || !Config.ENEMY_HEALTH_BARS.get() || mc.player == null || mc.options.hideGui
                || !e.isAlive() || e.isInvisible() || e.getHealth() <= 0) {
            return;
        }
        if (e.distanceToSqr(mc.player) > RANGE * RANGE || jp.revati.skyabyss.client.BossOwners.owns(e)) {
            return;
        }
        float max = Math.max(1f, e.getMaxHealth());
        float frac = Mth.clamp(e.getHealth() / max, 0f, 1f);
        boolean hurt = frac < 0.999f;
        // 遅れの帯
        long now = mc.level.getGameTime();
        if (now != lagTick) {
            lagTick = now;
            if (LAG.size() > 256) {
                LAG.clear();
            }
        }
        float lag = LAG.getOrDefault(e.getId(), frac);
        // 減りの速さは 1 tick あたり (描く回数に左右されない)
        float dt = Mth.clamp(mc.getTimer().getRealtimeDeltaTicks(), 0f, 2f);
        lag = lag < frac ? frac : Math.max(frac, lag - Math.max(0.004f, (lag - frac) * 0.08f) * dt);
        LAG.put(e.getId(), Math.max(lag, frac));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(0, e.getBbHeight() + 0.5 + (e.shouldShowName() ? 0.3 : 0), 0);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        // 大きな体ほど少し大きく (最大 2 倍)
        float s = Mth.clamp(e.getBbWidth(), 0.7f, 2f);
        pose.scale(s, s, s);
        Matrix4f m = pose.last().pose();
        MultiBufferSource buffers = event.getMultiBufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.textBackground());
        int alpha = hurt ? 0xFF : 0x70;
        float hw = WIDTH / 2;
        // 縁・溝・遅れ・体力 (奥から手前へ少しずつ前に)
        quad(vc, m, -hw - 0.03f, -0.03f, hw + 0.03f, HEIGHT + 0.03f, 0.000f, (alpha * 3 / 4) << 24 | 0x0A0C10);
        quad(vc, m, -hw, 0, hw, HEIGHT, 0.002f, alpha << 24 | 0x2A1214);
        if (hurt && lag > frac) {
            quad(vc, m, -hw + WIDTH * frac, 0, -hw + WIDTH * lag, HEIGHT, 0.004f, alpha << 24 | 0xF0E6D8);
        }
        int rgb = frac > 0.5f ? 0xD8404A : frac > 0.25f ? 0xE07A34 : 0xF0C040;
        quad(vc, m, -hw, 0, -hw + WIDTH * frac, HEIGHT, 0.006f, alpha << 24 | rgb);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int argb) {
        int light = 0xF000F0;
        // 表と裏の両方 (面の裏を描かない描き方でも見えるように)
        vc.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y0, z).setColor(argb).setLight(light);
        vc.addVertex(m, x1, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y1, z).setColor(argb).setLight(light);
        vc.addVertex(m, x0, y0, z).setColor(argb).setLight(light);
    }
}
