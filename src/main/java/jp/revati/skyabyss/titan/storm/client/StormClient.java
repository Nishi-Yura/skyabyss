package jp.revati.skyabyss.titan.storm.client;

import org.jetbrains.annotations.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.storm.GaleColumnEntity;
import jp.revati.skyabyss.titan.storm.StormSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

// 雷翼の巨神のクライアント側。描画の登録、背中の空 (雲を y 100 に描く。色は空の既定)、白い閃光
// (嵐の雲の中の天気は StormCloudClient)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormClient {
    // 嵐の上の空の効果の名前 (dimension_type の effects と同じ)
    public static final ResourceLocation STORM_BACK_EFFECTS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "storm_back");
    // 雲の高さ (y 100 = 嵐の雲の天井)
    private static final float CLOUD_LEVEL = 100f;

    private static int flashTotal;
    private static int flashLeft;
    private static int flashLeftO;

    private StormClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(StormSetup.THUNDER_WING.get(), StormWingRenderer::new);
        event.registerEntityRenderer(StormSetup.GALE_COLUMN.get(), Invisible::new);
    }

    @SubscribeEvent
    static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(STORM_BACK_EFFECTS, new DimensionSpecialEffects(CLOUD_LEVEL, false, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
            // 霧の色は空の色のまま (バイオームの霧の色を使う)。霧の濃さは既定
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
                return color;
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return false;
            }
        });
    }

    // 白く瞬く (昇り風の頂・背中へ渡る・嵐に投げ返される)。ticks のあいだに薄れて戻る
    public static void flash(int ticks) {
        flashTotal = Math.max(10, ticks);
        flashLeft = flashTotal;
        flashLeftO = flashTotal;
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        flashLeftO = flashLeft;
        if (flashLeft > 0) {
            flashLeft--;
        }
    }

    @SubscribeEvent
    static void onRenderGui(RenderGuiEvent.Post event) {
        if (flashLeft <= 0 && flashLeftO <= 0) {
            return;
        }
        float left = Mth.lerp(event.getPartialTick().getGameTimeDeltaPartialTick(true), flashLeftO, flashLeft);
        float t = left / flashTotal;
        // 前半は白いまま、後半で薄れる
        float a = Mth.clamp(t * 2f, 0f, 1f);
        if (a <= 0.01f) {
            return;
        }
        var g = event.getGuiGraphics();
        int color = ((int) (a * 235) << 24) | 0xF4F7FF;
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), color);
    }

    // 昇り風の柱は見えない (当たり判定だけ)
    static final class Invisible extends EntityRenderer<GaleColumnEntity> {
        Invisible(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        public boolean shouldRender(GaleColumnEntity column, Frustum frustum, double x, double y, double z) {
            return false;
        }

        @Override
        public ResourceLocation getTextureLocation(GaleColumnEntity column) {
            return ResourceLocation.withDefaultNamespace("textures/misc/white.png");
        }
    }

    // 画面の中の資源が読み込まれているか (ないモデルで描くと、GeckoLib がエラーを出し続けるため、なければ描かない)
    static boolean exists(@Nullable ResourceLocation location) {
        return location != null && Minecraft.getInstance().getResourceManager().getResource(location).isPresent();
    }
}
