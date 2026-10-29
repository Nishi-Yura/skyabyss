package jp.revati.skyabyss.story.finale.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.story.finale.FinaleBlocks;
import jp.revati.skyabyss.story.finale.FinaleWorld;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

// 雲海の空 (docs/design/50 の 4.1)。ふつうの空 (夜明けに止めた朝日と、西に沈みかけた月) に、白と淡い金の霧。
// バニラの雲は描かない (足もとが雲の海)。雲 (CloudBlock) の中に目があると、白く霞んで先が見えない
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class CloudSkyClient {
    public static final ResourceLocation EFFECTS = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "cloudsea");
    static final Vec3 FOG = new Vec3(0.96, 0.92, 0.84);

    private CloudSkyClient() {
    }

    @SubscribeEvent
    static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(EFFECTS, new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NORMAL, false, false) {
            @Override
            public Vec3 getBrightnessDependentFogColor(Vec3 color, float sunHeight) {
                return FOG;
            }

            @Override
            public boolean isFoggyAt(int x, int y) {
                return false;
            }

            @Override
            public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack pose, double camX, double camY, double camZ,
                                        Matrix4f modelView, Matrix4f projection) {
                return true;
            }
        });
    }

    static boolean inCloud(Camera camera) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != FinaleWorld.LEVEL) {
            return false;
        }
        return level.getBlockState(BlockPos.containing(camera.getPosition())).is(FinaleBlocks.CLOUD.get());
    }

    @SubscribeEvent
    static void onFog(ViewportEvent.RenderFog event) {
        if (inCloud(event.getCamera())) {
            event.setNearPlaneDistance(0f);
            event.setFarPlaneDistance(4.5f);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != FinaleWorld.LEVEL) {
            return;
        }
        float k = inCloud(event.getCamera()) ? 1f : 0.55f;
        event.setRed((float) (event.getRed() * (1 - k) + FOG.x * k));
        event.setGreen((float) (event.getGreen() * (1 - k) + FOG.y * k));
        event.setBlue((float) (event.getBlue() * (1 - k) + FOG.z * k));
    }
}
