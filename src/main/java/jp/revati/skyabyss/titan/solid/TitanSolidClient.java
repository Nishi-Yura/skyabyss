package jp.revati.skyabyss.titan.solid;

import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;

// 三人称のカメラが巨神の体の中へ入らないように、体に当たったらカメラを手前へ引く (人の側は TitanSolids が 3 ブロック外へ押し出すので、一人称は入らない)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class TitanSolidClient {
    private TitanSolidClient() {
    }

    @SubscribeEvent
    static void onCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null || viewer.isSpectator()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        org.joml.Vector3f look = camera.getLookVector();
        // 後ろへ引くカメラの向き (カメラが前向きに反転しているときは前)
        double sign = event.isCameraFlipped() ? 1.0 : -1.0;
        Vec3 dir = new Vec3(look.x(), look.y(), look.z()).scale(sign);
        float wanted = event.getDistance();
        float best = wanted;
        for (Entity titan : TitanSolids.titans()) {
            if (titan.level() != viewer.level() || !(titan instanceof SolidTitan solid) || !solid.solidActive()) {
                continue;
            }
            TitanVolume volume = TitanVolume.get(solid.volumeName());
            if (volume == null) {
                continue;
            }
            List<SolidTitan.Corridor> corridors = solid.corridors();
            for (float d = 0.25f; d <= best; d += 0.25f) {
                Vec3 p = eye.add(dir.scale(d));
                if (TitanSolids.blocked(volume, corridors, TitanSolids.toLocal(titan, p), true)) {
                    best = Math.max(0.0f, d - 0.5f);
                    break;
                }
            }
        }
        if (best < wanted) {
            event.setDistance(best);
        }
    }
}
