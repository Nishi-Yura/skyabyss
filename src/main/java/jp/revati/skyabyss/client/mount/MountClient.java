package jp.revati.skyabyss.client.mount;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.mount.MountEntity;
import jp.revati.skyabyss.mount.MountNetwork;
import jp.revati.skyabyss.mount.MountSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.EnumMap;
import java.util.Map;

import jp.revati.skyabyss.client.lowpoly.LowPoly;
import jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer;
import jp.revati.skyabyss.mount.MountLook;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.model.GeoModel;

// 乗り物のクライアント側: 描画の登録 (見た目ごとの geo と絵)、厩舎の画面の出し入れ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class MountClient {
    private MountClient() {
    }

    @SubscribeEvent
    static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(MountSetup.MOUNT.get(), MountRenderer::new);
    }

    public static void view(MountNetwork.View view) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen s) {
            s.update(view);
        } else {
            mc.setScreen(new MountShopScreen(view));
        }
    }

    public static void close() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof MountShopScreen) {
            mc.setScreen(null);
        }
    }

    // 見た目ごとの低ポリ (lowpoly/mount_<id>.json、tools/gen/mobs/mounts_lp/)。光る見た目 (雷の雛・炉の狐) は発光マスクで光る
    static final class MountRenderer extends LowPolyGeoRenderer<MountEntity> {
        MountRenderer(EntityRendererProvider.Context ctx) {
            super(ctx, new MountModel(), e -> e.look().model(), 1f);
            this.shadowRadius = 0.8f;
        }
    }

    // 低ポリのときは骨だけの geo と低ポリのアトラス、切ったときは骨ごとの箱の geo。アニメは見た目ごと (骨が違う)
    static final class MountModel extends GeoModel<MountEntity> {
        private static final ResourceLocation DEBUG_TEXTURE = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "textures/lowpoly/debug.png");
        private static final Map<MountLook, ResourceLocation[]> RES = new EnumMap<>(MountLook.class);

        private static ResourceLocation[] res(MountLook look) {
            return RES.computeIfAbsent(look, l -> new ResourceLocation[] {
                    rl("geo/entity/" + l.model() + ".geo.json"), rl("geo/entity/" + l.model() + "_lowpoly.geo.json"),
                    rl("geo/entity/" + l.model() + "_lowpoly_debug.geo.json"), rl("textures/entity/" + l.model() + ".png"),
                    rl("textures/lowpoly/" + l.model() + ".png"), rl("animations/entity/" + l.model() + ".animation.json")});
        }

        private static ResourceLocation rl(String path) {
            return ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, path);
        }

        @Override
        public ResourceLocation getModelResource(MountEntity e) {
            ResourceLocation[] r = res(e.look());
            return LowPoly.enabled ? (LowPoly.debug ? r[2] : r[1]) : r[0];
        }

        @Override
        public ResourceLocation getTextureResource(MountEntity e) {
            ResourceLocation[] r = res(e.look());
            return LowPoly.enabled ? (LowPoly.debug ? DEBUG_TEXTURE : r[4]) : r[3];
        }

        @Override
        public ResourceLocation getAnimationResource(MountEntity e) {
            return res(e.look())[5];
        }

        // 脚の運び (歩き・駆け) は時間でなく歩いた距離で進める: 速さが変わっても足が滑らない
        @Override
        public void applyMolangQueries(AnimationState<MountEntity> state, double animTime) {
            super.applyMolangQueries(state, animTime);
            double d = state.getAnimatable().travel(state.getPartialTick());
            MathParser.setVariable("variable.mount_dist", () -> d);
        }
    }
}
