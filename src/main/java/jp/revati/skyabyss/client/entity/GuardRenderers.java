package jp.revati.skyabyss.client.entity;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.entity.BoneSpike;
import jp.revati.skyabyss.entity.MossCrawler;
import jp.revati.skyabyss.registry.ModEntities;
import java.util.function.Consumer;
import java.util.function.Function;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import jp.revati.skyabyss.registry.ModItems;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

// 岩鯨の背の番兵たちの描画。どれも GeckoLib のモデルで、目や核を暗闇で光らせる
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GuardRenderers {
    private static final ResourceLocation SPIKE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/entity/bone_spike.png");

    private GuardRenderers() {
    }

    @SubscribeEvent
    static void register(EntityRenderersEvent.RegisterRenderers event) {
        // 骨の番兵は低ポリのメッシュ (client/lowpoly)。LowPoly.enabled = false で元のキューブの geo と GearLayer に戻る
        event.registerEntityRenderer(ModEntities.BONE_SENTINEL.get(), ctx -> {
            LowPolyGeoRenderer<jp.revati.skyabyss.entity.BoneSentinel> r = new LowPolyGeoRenderer<>(ctx, "bone_sentinel", 1.0f);
            r.addRenderLayer(new GearLayer<>(r, "socket_off", "bone_sentinel_shield", mob -> mob.isBlockingAnim() ? "block_impact" : "idle").activeWhen(() -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_off", "bone_sentinel_shield"));
            return r;
        });
        // 肋の射手・苔這い・水路の顎・番兵長・心臓も同じ。LowPoly.enabled = false で元のキューブの geo / GearLayer に戻る
        event.registerEntityRenderer(ModEntities.RIB_ARCHER.get(), ctx -> {
            LowPolyGeoRenderer<jp.revati.skyabyss.entity.RibArcher> r = new LowPolyGeoRenderer<>(ctx, "rib_archer", 1.0f);
            r.addRenderLayer(new GearLayer<>(r, "socket_off", "rib_archer_bow", mob -> mob.isDrawingBow() ? "draw" : "idle").activeWhen(() -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPolyAnimated(r, "socket_off", "rib_archer_bow", mob -> mob.isDrawingBow() ? "draw" : "idle"));
            return r;
        });
        event.registerEntityRenderer(ModEntities.MOSS_CRAWLER.get(), GuardRenderers::mossCrawler);
        event.registerEntityRenderer(ModEntities.WATERWAY_MAW.get(), ctx -> new LowPolyGeoRenderer<jp.revati.skyabyss.entity.WaterwayMaw>(ctx, "waterway_maw", 0.58f));
        event.registerEntityRenderer(ModEntities.WHALEBONE_CAPTAIN.get(), ctx -> {
            LowPolyGeoRenderer<jp.revati.skyabyss.entity.WhaleboneCaptain> r = new LowPolyGeoRenderer<>(ctx, "whalebone_captain", 1.0f);
            r.addRenderLayer(GearLayer.item(r, "socket_main", captainPikeStack(), ItemDisplayContext.NONE, GuardRenderers::captainPike, () -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "captain_pike"));
            return r;
        });
        event.registerEntityRenderer(ModEntities.STONE_WHALE_HEART.get(), StoneWhaleHeartRenderer::new);
        event.registerEntityRenderer(ModEntities.BONE_SPIKE.get(), ctx -> new ArrowRenderer<BoneSpike>(ctx) {
            @Override
            public ResourceLocation getTextureLocation(BoneSpike entity) {
                return SPIKE;
            }
        });
    }

    // 苔芋虫: 倒れるときは横倒しにならず、death アニメーション (丸まる) で終える
    private static GeoEntityRenderer<MossCrawler> mossCrawler(EntityRendererProvider.Context ctx) {
        return new LowPolyGeoRenderer<MossCrawler>(ctx, "moss_crawler", 1.3f) {
            @Override
            protected float getDeathMaxRotation(MossCrawler animatable) {
                return 0f;
            }
        };
    }

    private static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> geo(EntityRendererProvider.Context ctx, String name, float scale) {
        return geo(ctx, name, scale, r -> {
        });
    }

    // extra で、そのモブだけの描画層 (武器の GearLayer など) を足す
    private static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> geo(EntityRendererProvider.Context ctx, String name, float scale,
                                                                            Consumer<GeoEntityRenderer<T>> extra) {
        return geo(ctx, name, scale, true, extra);
    }

    // glow = false のモブには発光層 (AutoGlowingGeoLayer) を付けない。付けると textures/entity/<name>_glowmask.png が要る
    // (光る画素が 0 個のマスクでも落ちる)。光る所のない番兵 (bone_sentinel) は false で、マスクも置いていない
    private static <T extends Entity & GeoAnimatable> GeoEntityRenderer<T> geo(EntityRendererProvider.Context ctx, String name, float scale,
                                                                            boolean glow, Consumer<GeoEntityRenderer<T>> extra) {
        GeoEntityRenderer<T> renderer = new GeoEntityRenderer<>(ctx, new DefaultedEntityGeoModel<T>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, name), true));
        renderer.withScale(scale);
        if (glow) {
            renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
        }
        extra.accept(renderer);
        return renderer;
    }

    // 番兵長の鉾 (アイテムの3Dモデル) を socket_main に合わせる。
    // アイテムの座標 (画素) で、柄の軸は x=5.875 z=8、握りは y=4 (石突きから7画素)。刃は +x、穂先は +y で、-45°傾けて置いてある。
    // 受け口の向き: 穂先が -Z、刃が -Y (腕を前へ水平に出すと、柄が立ち、刃が前を向く)。大きさは 1.8 倍 (番兵長の背丈に合わせる)。
    // tools/rough/captain_v4.py の PIKE_* と同じ値にしておく
    private static <T> Function<T, ItemStack> captainPikeStack() {
        ItemStack stack = new ItemStack(ModItems.CAPTAIN_PIKE.get());
        return mob -> stack;
    }

    private static void captainPike(PoseStack pose) {
        pose.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(0, -1, 0, 0, 0, -1, 1, 0, 0)));
        pose.scale(1.8f, 1.8f, 1.8f);
        pose.translate(2.125f / 16f, 4f / 16f, 0);
        pose.mulPose(Axis.ZP.rotationDegrees(45));
    }
}
