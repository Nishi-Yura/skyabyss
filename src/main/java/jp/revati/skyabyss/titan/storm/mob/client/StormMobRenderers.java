package jp.revati.skyabyss.titan.storm.mob.client;


import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.entity.GearLayer;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.titan.storm.mob.StaticMoth;
import jp.revati.skyabyss.titan.storm.mob.StormMob;
import jp.revati.skyabyss.titan.storm.mob.StormMobSetup;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

// 雷翼の背の敵の描画。GeckoLib のモデル (geo/entity/<名前>、tools/install/install_storm_mobs.py) と、目・稲妻を暗闇で光らせる層。
// 羽守の鉤槍 (gear/hooked_glaive) と雷乞いの杖 (gear/supplicant_staff) は別モデルで、受け口 socket_main に付ける。
// 雷蛾は、帯電の割合だけ「帯電の光」(static_moth_charge.png) を足して描く (別のモデルは持たない。12 の 14.3節)。
// 死ぬときはバニラの「横に倒れる」回転をせず、モデルの死のアニメーションで倒れる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormMobRenderers {
    private StormMobRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        // 低ポリ (client/lowpoly)。
        event.registerEntityRenderer(StormMobSetup.FEATHER_WARDEN.get(), ctx -> {
            MobRenderer<jp.revati.skyabyss.titan.storm.mob.FeatherWarden> r = new MobRenderer<>(ctx, "feather_warden", 0.45f);
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "hooked_glaive"));
            return r;
        });
        event.registerEntityRenderer(StormMobSetup.STATIC_MOTH.get(), ctx -> {
            MobRenderer<StaticMoth> r = new MobRenderer<>(ctx, "static_moth", 0.3f);
            return r;
        });
        event.registerEntityRenderer(StormMobSetup.THUNDER_SUPPLICANT.get(), ctx -> {
            MobRenderer<jp.revati.skyabyss.titan.storm.mob.ThunderSupplicant> r = new MobRenderer<>(ctx, "thunder_supplicant", 0.4f);
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "supplicant_staff"));
            return r;
        });
    }

    static class MobRenderer<T extends StormMob> extends LowPolyGeoRenderer<T> {
        MobRenderer(EntityRendererProvider.Context ctx, String name, float shadow) {
            super(ctx, name, 1.0f);
            this.shadowRadius = shadow;
        }

        @Override
        protected float getDeathMaxRotation(T animatable) {
            return 0f;
        }
    }

}
