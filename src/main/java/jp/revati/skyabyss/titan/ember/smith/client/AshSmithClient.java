package jp.revati.skyabyss.titan.ember.smith.client;

import java.util.Comparator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.ember.smith.AshSmith;
import jp.revati.skyabyss.titan.ember.smith.EmberSmithSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.BossEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

// 灰の鍛え手のクライアント側: 描画の登録と、10 段のボスバー (段階なし、橙の帯。13 の 5.2節)。
// 絵は tools/install_ember_smith.py: 顔の印 ash_smith_icon.png (16x16)、枠 ash_smith_frame.png (182x16。溝は x 3〜177、y 3〜8)、
// 帯の上の 10 段の刻み ash_smith_notches.png。名前の横に、鞴が白く光る間は「鞴が開いた」、怒ったあとは「怒り」
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class AshSmithClient {
    public static final int WIDTH = 182;
    public static final int FRAME_HEIGHT = 16;
    public static final int ICON = 16;
    public static final int EXTRA_HEIGHT = 12;
    private static final int TRACK_X = 3;
    private static final int TRACK_W = 175;
    private static final int TRACK_Y = 3;
    private static final int TRACK_H = 6;
    public static final int FILL = 0xEB8028;
    public static final int FILL_TOP = 0xFFC46E;
    public static final int FILL_WEAK = 0xFFF0C8;
    private static final ResourceLocation ICON_TEX = tex("ash_smith_icon");
    private static final ResourceLocation FRAME_TEX = tex("ash_smith_frame");
    private static final ResourceLocation NOTCH_TEX = tex("ash_smith_notches");
    private static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/ash_smith_glowmask.png");
    private static final ResourceLocation GLOW_WHITE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/ash_smith_glowmask_white.png");
    // 低ポリ: アトラスの光る画素の色だけを載せた絵 (tools/install_lowpoly_ember_npc.py が焼く)
    private static final ResourceLocation GLOW_LOW = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/ash_smith_glow.png");
    private static final ResourceLocation GLOW_LOW_WHITE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/ash_smith_glow_white.png");

    private AshSmithClient() {
    }

    private static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/gui/boss/" + name + ".png");
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(EmberSmithSetup.ASH_SMITH.get(), SmithRenderer::new);
    }

    @SubscribeEvent
    static void onBossBar(CustomizeGuiOverlayEvent.BossEventProgress event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BossEvent boss = event.getBossEvent();
        String name = boss.getName().getString();
        AshSmith smith = mc.level.getEntitiesOfClass(AshSmith.class, mc.player.getBoundingBox().inflate(64),
                        s -> s.isAlive() && s.getDisplayName().getString().equals(name))
                .stream().min(Comparator.comparingDouble(s -> s.distanceToSqr(mc.player))).orElse(null);
        if (smith == null) {
            return;
        }
        event.setCanceled(true);
        draw(event.getGuiGraphics(), mc, smith, boss, event.getX(), event.getY());
        event.setIncrement(event.getIncrement() + EXTRA_HEIGHT);
    }

    static void draw(GuiGraphics g, Minecraft mc, AshSmith smith, BossEvent boss, int x, int y) {
        g.blit(ICON_TEX, x, y, 0, 0, ICON, ICON, ICON, ICON);
        Component name = smith.getDisplayName();
        g.drawString(mc.font, name, x + ICON + 4, y + 4, 0xFFFFFF, true);
        Component label = smith.isWeak() ? Component.translatable("gui.skyabyss.boss.ash_smith.weak")
                : smith.isEnraged() ? Component.translatable("gui.skyabyss.boss.ash_smith.enraged") : null;
        if (label != null) {
            g.drawString(mc.font, label, x + ICON + 4 + mc.font.width(name) + 6, y + 4, smith.isWeak() ? 0xFFF0C8 : 0xFF8A4A, true);
        }
        String percent = Math.round(boss.getProgress() * 100) + "%";
        g.drawString(mc.font, percent, x + WIDTH - mc.font.width(percent), y + 4, 0xFFFFFF, true);
        int barY = y + ICON + 2;
        g.blit(FRAME_TEX, x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
        float progress = Math.max(0f, Math.min(1f, boss.getProgress()));
        int fill = Math.round(TRACK_W * progress);
        if (fill > 0) {
            int left = x + TRACK_X;
            int top = barY + TRACK_Y;
            g.fill(left, top, left + fill, top + TRACK_H, 0xFF000000 | (smith.isWeak() ? FILL_WEAK : FILL));
            g.fill(left, top, left + fill, top + 1, 0xFF000000 | FILL_TOP);
        }
        g.blit(NOTCH_TEX, x, barY, 0, 0, WIDTH, FRAME_HEIGHT, WIDTH, FRAME_HEIGHT);
    }

    // 鍛え手の描画。部屋の明るさで色が沈まないよう (08 の 19.8節): 基のテクスチャを明るくしてあり、光る所は glowmask を目の光で足す
    // (GlowLayer。AutoGlowingGeoLayer は光る画素を基から抜いて面の陰を受けるため、暗い部屋で沈む)。
    // 鞴が白く光る間 (吐いたあと) は白の発光、怒ったあとは橙がかった白。怒ったら前掛けを隠す。大鎚は socket_main、火箸は socket_off
    static final class SmithRenderer extends LowPolyGeoRenderer<AshSmith> {
        SmithRenderer(EntityRendererProvider.Context context) {
            // 低ポリ (client/lowpoly)。LowPoly.enabled = false で元のキューブの geo / GearLayer に戻る
            super(context, "ash_smith", 1.0f);
            addRenderLayer(new GlowLayer(this));
            addRenderLayer(new GearLayer<>(this, "socket_main", "smith_hammer", s -> "idle").activeWhen(() -> !LowPoly.enabled));
            addRenderLayer(new GearLayer<>(this, "socket_off", "smith_tongs", s -> "idle").activeWhen(() -> !LowPoly.enabled));
            addRenderLayer(GearLayer.lowPoly(this, "socket_main", "smith_hammer"));
            addRenderLayer(GearLayer.lowPoly(this, "socket_off", "smith_tongs"));
            this.shadowRadius = 1.4f;
        }

        // 発光は下の GlowLayer (目の光で足す)。AutoGlowingGeoLayer は面の陰をかけ、暗い部屋で沈む
        @Override
        protected boolean autoGlow() {
            return false;
        }

        @Override
        protected float getDeathMaxRotation(AshSmith animatable) {
            return 0f;
        }

        @Override
        protected int getBlockLightLevel(AshSmith entity, BlockPos pos) {
            return Math.max(super.getBlockLightLevel(entity, pos), 13);
        }

        @Override
        public void preRender(PoseStack poseStack, AshSmith smith, BakedGeoModel model, MultiBufferSource bufferSource, VertexConsumer buffer,
                              boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
            // モデルは全個体で共有なので、描くたびに決める
            boolean enraged = smith.isEnraged();
            model.getBone("apron").ifPresent(bone -> bone.setHidden(enraged));
            super.preRender(poseStack, smith, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
        }
    }

    static final class GlowLayer extends GeoRenderLayer<AshSmith> {
        GlowLayer(SmithRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, AshSmith smith, BakedGeoModel model, RenderType renderType,
                           MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
            if (smith.isInvisible()) {
                return;
            }
            boolean white = smith.isWeak() || smith.isEnraged();
            RenderType glow = RenderType.eyes(LowPoly.enabled ? (white ? GLOW_LOW_WHITE : GLOW_LOW) : (white ? GLOW_WHITE : GLOW));
            int color = -1;
            if (smith.isEnraged() && !smith.isWeak()) {
                color = FastColor.ARGB32.color(255, 255, 214, 170);
            }
            getRenderer().reRender(model, poseStack, buffers, smith, glow, buffers.getBuffer(glow), partialTick,
                    LightTexture.FULL_BRIGHT, packedOverlay, color);
        }
    }
}
