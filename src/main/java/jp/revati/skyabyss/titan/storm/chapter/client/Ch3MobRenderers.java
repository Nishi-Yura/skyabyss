package jp.revati.skyabyss.titan.storm.chapter.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.chapter.Ch3Mobs;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// 三章の地上の敵の描画 (低ポリのメッシュ。tools/gen_ch3_ground_mobs.py)。焼けた羽守の鉤槍は別メッシュ (受け口 socket_main、名前 scorched_glaive)。
// 倒れるときはバニラの横倒しにせず、死のアニメーションで倒れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class Ch3MobRenderers {
    private Ch3MobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Ch3Mobs.STATIC_PUFF_TYPE.get(), ctx -> noTilt(ctx, "static_puff", 0.3f));
        event.registerEntityRenderer(Ch3Mobs.PEAK_FLEDGLING_TYPE.get(), ctx -> noTilt(ctx, "peak_fledgling", 0.3f));
        event.registerEntityRenderer(Ch3Mobs.LIGHTNING_GLEANER_TYPE.get(), ctx -> noTilt(ctx, "lightning_gleaner", 0.35f));
        event.registerEntityRenderer(Ch3Mobs.SCORCHED_WARDEN_TYPE.get(), ctx -> {
            GeoEntityRenderer<jp.revati.skyabyss.titan.storm.chapter.mob.ScorchedWarden> r = noTilt(ctx, "scorched_warden", 0.45f);
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "scorched_glaive"));
            return r;
        });
    }

    private static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> noTilt(EntityRendererProvider.Context ctx, String name, float shadow) {
        return new LowPolyGeoRenderer<T>(ctx, name, 1.0f) {
            {
                this.shadowRadius = shadow;
            }

            @Override
            protected float getDeathMaxRotation(T animatable) {
                return 0f;
            }
        };
    }
}
