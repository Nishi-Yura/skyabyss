package jp.revati.skyabyss.client.hud;

import org.joml.Matrix4f;


import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

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

    // GeckoLib の敵は RenderLivingEvent を出さないので、世界の描画段階 (半透明の後) で全部まとめて描く
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null || mc.options.hideGui || !Config.ENEMY_HEALTH_BARS.get()) {
            return;
        }
        var list = level.getEntitiesOfClass(LivingEntity.class, new AABB(mc.player.blockPosition()).inflate(RANGE),
                e -> e instanceof Enemy);
        if (list.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        Vector3f left = event.getCamera().getLeftVector();
        Vector3f up = event.getCamera().getUpVector();
        Matrix4f pose = event.getPoseStack().last().pose();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        for (LivingEntity e : list) {
            any |= draw(buf, mc, e, pose, cam, left, up, partial);
        }
        if (any) {
            BufferUploader.drawWithShader(buf.buildOrThrow());
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static boolean draw(BufferBuilder buf, Minecraft mc, LivingEntity e, Matrix4f pose, Vec3 cam, Vector3f left,
            Vector3f up, float partial) {
        if (!e.isAlive() || e.isInvisible() || e.getHealth() <= 0 || e.distanceToSqr(mc.player) > RANGE * RANGE
                || jp.revati.skyabyss.client.BossOwners.owns(e)) {
            return false;
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

        Vec3 c = new Vec3(Mth.lerp(partial, e.xo, e.getX()), Mth.lerp(partial, e.yo, e.getY()), Mth.lerp(partial, e.zo, e.getZ()))
                .add(0, e.getBbHeight() + 0.5 + (e.shouldShowName() ? 0.3 : 0), 0);
        // 大きな体ほど少し大きく (最大 2 倍)
        float s = Mth.clamp(e.getBbWidth(), 0.7f, 2f);
        int alpha = hurt ? 0xFF : 0x70;
        float hw = WIDTH / 2;
        // 縁・溝・遅れ・体力 (奥から手前へ少しずつ前に)
        quad(buf, pose, cam, left, up, c, s, -hw - 0.03f, -0.03f, hw + 0.03f, HEIGHT + 0.03f, 0.000f, (alpha * 3 / 4) << 24 | 0x0A0C10);
        quad(buf, pose, cam, left, up, c, s, -hw, 0, hw, HEIGHT, 0.002f, alpha << 24 | 0x2A1214);
        if (hurt && lag > frac) {
            quad(buf, pose, cam, left, up, c, s, -hw + WIDTH * frac, 0, -hw + WIDTH * lag, HEIGHT, 0.004f, alpha << 24 | 0xF0E6D8);
        }
        int rgb = frac > 0.5f ? 0xD8404A : frac > 0.25f ? 0xE07A34 : 0xF0C040;
        quad(buf, pose, cam, left, up, c, s, -hw, 0, -hw + WIDTH * frac, HEIGHT, 0.006f, alpha << 24 | rgb);
        return true;
    }

    // z はカメラ向きの手前 (ずれの層)。left は画面の左向きなので x は引く
    private static void quad(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, float s,
            float x0, float y0, float x1, float y1, float z, int argb) {
        // 手前にずらす向き (カメラの方)
        double dx = cam.x - c.x, dy = cam.y - c.y, dz = cam.z - c.z;
        double len = Math.max(1e-4, Math.sqrt(dx * dx + dy * dy + dz * dz));
        float ox = (float) (dx / len) * z, oy = (float) (dy / len) * z, oz = (float) (dz / len) * z;
        corner(buf, pose, cam, left, up, c, s, x0, y0, ox, oy, oz, argb);
        corner(buf, pose, cam, left, up, c, s, x1, y0, ox, oy, oz, argb);
        corner(buf, pose, cam, left, up, c, s, x1, y1, ox, oy, oz, argb);
        corner(buf, pose, cam, left, up, c, s, x0, y1, ox, oy, oz, argb);
    }

    private static void corner(BufferBuilder buf, Matrix4f pose, Vec3 cam, Vector3f left, Vector3f up, Vec3 c, float s,
            float x, float y, float ox, float oy, float oz, int argb) {
        float px = (float) (c.x - cam.x) - left.x * x * s + up.x * y * s + ox;
        float py = (float) (c.y - cam.y) - left.y * x * s + up.y * y * s + oy;
        float pz = (float) (c.z - cam.z) - left.z * x * s + up.z * y * s + oz;
        buf.addVertex(pose, px, py, pz).setColor(argb);
    }
}
