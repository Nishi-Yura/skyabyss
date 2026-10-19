package jp.revati.skyabyss.client.titan;

import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.registry.ModEntities;
import jp.revati.skyabyss.registry.ModSounds;
import jp.revati.skyabyss.titan.TitanTravel;
import jp.revati.skyabyss.titan.dungeon.TitanBackLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 岩鯨のクライアント側。描画の登録と、背中の上にいるあいだの空気感
// (眼下を流れる雲の海、吹き続ける風、ゆったりした揺れ) を担当する
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TitanClient {
    // 眼下の雲の海の高さと、流れる速さ (巨神が頭の方へ進むので、雲は尾の方へ流れる)
    private static final float CLOUD_SEA_Y = TitanBackLayout.BASE_Y - 55f;
    private static final float CLOUD_DRIFT = 0.25f;
    private static final int CLOUD_STEP = 12;
    private static final int CLOUD_RADIUS = 384;

    @Nullable
    private static WindSound wind;

    private TitanClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.STONE_WHALE.get(), StoneWhaleRenderer::new);
        // 鎖の当たり判定は見た目を持たない (鎖そのものは岩鯨の描画で描く)
        event.registerEntityRenderer(ModEntities.WHALE_CHAIN.get(), NoopRenderer::new);
    }

    private static boolean onBack() {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension() == TitanTravel.TITAN_BACK;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (onBack() && (wind == null || wind.isStopped())) {
            wind = new WindSound();
            mc.getSoundManager().play(wind);
        }
    }

    // 巨神の泳ぎ (8秒周期) に合わせて、足元がゆっくり傾く
    @SubscribeEvent
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!onBack() || !Config.SCREEN_SHAKE.get()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        double seconds = (level.getGameTime() + event.getPartialTick()) / 20.0;
        double phase = seconds * 2.0 * Math.PI / 8.0;
        event.setRoll(event.getRoll() + (float) Math.sin(phase * 0.5) * 0.8f);
        event.setPitch(event.getPitch() + (float) Math.sin(phase) * 0.35f);
    }

    // 眼下に広がり、尾の方へ流れていく雲の海
    @SubscribeEvent
    static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !onBack()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        Vec3 cam = event.getCamera().getPosition();
        double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double drift = time * CLOUD_DRIFT;
        // 昼は白く、夜は青黒く沈む (getSkyDarken は空の明るさ 0.2〜1)
        float brightness = 0.15f + 0.8f * level.getSkyDarken(1f);

        Matrix4f pose = new Matrix4f(event.getModelViewMatrix());
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int minX = Mth.floor((cam.x - CLOUD_RADIUS) / CLOUD_STEP) * CLOUD_STEP;
        int minZ = Mth.floor((cam.z - CLOUD_RADIUS) / CLOUD_STEP) * CLOUD_STEP;
        int cells = CLOUD_RADIUS * 2 / CLOUD_STEP;
        for (int layer = 0; layer < 2; layer++) {
            float y = (float) (CLOUD_SEA_Y - layer * 10 - cam.y);
            double layerDrift = drift * (layer == 0 ? 1.0 : 0.6);
            for (int i = 0; i < cells; i++) {
                for (int k = 0; k < cells; k++) {
                    double x = minX + i * CLOUD_STEP;
                    double z = minZ + k * CLOUD_STEP;
                    float a00 = cloud(x, z, layerDrift, layer, cam);
                    float a10 = cloud(x + CLOUD_STEP, z, layerDrift, layer, cam);
                    float a11 = cloud(x + CLOUD_STEP, z + CLOUD_STEP, layerDrift, layer, cam);
                    float a01 = cloud(x, z + CLOUD_STEP, layerDrift, layer, cam);
                    if (a00 + a10 + a11 + a01 < 0.01f) {
                        continue;
                    }
                    float c = brightness * (layer == 0 ? 1f : 0.82f);
                    float x0 = (float) (x - cam.x);
                    float z0 = (float) (z - cam.z);
                    buffer.addVertex(pose, x0, y, z0).setColor(c, c, c * 1.04f, a00);
                    buffer.addVertex(pose, x0, y, z0 + CLOUD_STEP).setColor(c, c, c * 1.04f, a01);
                    buffer.addVertex(pose, x0 + CLOUD_STEP, y, z0 + CLOUD_STEP).setColor(c, c, c * 1.04f, a11);
                    buffer.addVertex(pose, x0 + CLOUD_STEP, y, z0).setColor(c, c, c * 1.04f, a10);
                }
            }
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private static float cloud(double x, double z, double drift, int layer, Vec3 cam) {
        double zz = z - drift;
        double n = noise(x / 70.0 + layer * 13.1, zz / 70.0) * 0.6 + noise(x / 24.0 - layer * 5.7, zz / 24.0) * 0.4;
        double cover = Mth.clamp((n - (layer == 0 ? 0.35 : 0.25)) / 0.25, 0.0, 1.0);
        double d = Math.hypot(x - cam.x, z - cam.z) / CLOUD_RADIUS;
        double fade = Mth.clamp((1.0 - d) / 0.35, 0.0, 1.0);
        return (float) (cover * fade * (layer == 0 ? 0.9 : 1.0));
    }

    private static double noise(double x, double z) {
        int ix = Mth.floor(x);
        int iz = Mth.floor(z);
        double fx = x - ix;
        double fz = z - iz;
        double ux = fx * fx * (3 - 2 * fx);
        double uz = fz * fz * (3 - 2 * fz);
        return Mth.lerp(uz, Mth.lerp(ux, hash(ix, iz), hash(ix + 1, iz)), Mth.lerp(ux, hash(ix, iz + 1), hash(ix + 1, iz + 1)));
    }

    private static double hash(int x, int z) {
        long h = x * 374761393L + z * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    // 背中の上でだけ鳴り続ける風
    static final float BACK_WIND_VOLUME = 0.4f;

    private static final class WindSound extends AbstractTickableSoundInstance {
        private float indoor;

        WindSound() {
            super(ModSounds.TITAN_WIND.get(), SoundSource.AMBIENT, RandomSource.create());
            this.looping = true;
            this.volume = 0.0001f;
            this.relative = true;
            this.attenuation = SoundInstance.Attenuation.NONE;
        }

        @Override
        public boolean canStartSilent() {
            return true;
        }

        @Override
        public void tick() {
            // 前は 0.7 で一定だった。ogg 側で突風と静けさを作り、全体も下げた (約 -10 dB)。屋内 (空が見えない所) はさらに小さく
            Minecraft mc = Minecraft.getInstance();
            float target = 0f;
            if (onBack() && mc.player != null) {
                boolean open = mc.level.canSeeSky(mc.player.blockPosition().above());
                indoor += ((open ? 0f : 1f) - indoor) * 0.03f;
                target = BACK_WIND_VOLUME * (1f - 0.7f * indoor);
            }
            this.volume += (target - this.volume) * 0.05f;
            if (!onBack() && this.volume < 0.01f) {
                stop();
            }
        }
    }
}
