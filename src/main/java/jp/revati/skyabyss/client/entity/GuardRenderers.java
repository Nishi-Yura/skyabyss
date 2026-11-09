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
        event.registerEntityRenderer(ModEntities.WHALEBONE_CAPTAIN.get(), ctx -> {
            LowPolyGeoRenderer<jp.revati.skyabyss.entity.WhaleboneCaptain> r = new LowPolyGeoRenderer<>(ctx, "whalebone_captain", 1.0f);
            r.addRenderLayer(GearLayer.item(r, "socket_main", captainPikeStack(), ItemDisplayContext.NONE, GuardRenderers::captainPike, () -> !LowPoly.enabled));
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "captain_pike"));
            return r;
        });
        // 一章の地上の敵 (低ポリのメッシュ。倒れる動きは death アニメーションで、横倒しにはしない)
        event.registerEntityRenderer(ModEntities.ROCK_SHELL_BEETLE.get(), ctx -> noTilt(ctx, "rock_shell_beetle"));
        event.registerEntityRenderer(ModEntities.SPINE_HARE.get(), ctx -> noTilt(ctx, "spine_hare"));
        event.registerEntityRenderer(ModEntities.BONE_HAULER.get(), ctx -> noTilt(ctx, "bone_hauler"));
        event.registerEntityRenderer(ModEntities.ELDER_SENTRY.get(), ctx -> noTilt(ctx, "elder_sentry"));
        // 二章の地上の敵 (P12)。潮の番人の銛は別メッシュ (受け口 socket_main)
        event.registerEntityRenderer(ModEntities.TIDEPOOL_CLAM.get(), ctx -> noTilt(ctx, "tidepool_clam"));
        event.registerEntityRenderer(ModEntities.SAND_BURROWER.get(), ctx -> noTilt(ctx, "sand_burrower"));
        event.registerEntityRenderer(ModEntities.DRIFT_HERMIT.get(), ctx -> noTilt(ctx, "drift_hermit"));
        event.registerEntityRenderer(ModEntities.SHORE_WARDEN.get(), ctx -> {
            GeoEntityRenderer<jp.revati.skyabyss.entity.ShoreWarden> r = noTilt(ctx, "shore_warden");
            r.addRenderLayer(GearLayer.lowPoly(r, "socket_main", "shore_harpoon"));
            return r;
        });
        // 大陸の新しい敵 (docs/design/44)
        event.registerEntityRenderer(ModEntities.BANDIT.get(), ctx -> noTilt(ctx, "bandit"));
        event.registerEntityRenderer(ModEntities.BANDIT_ARCHER.get(), ctx -> noTilt(ctx, "bandit_archer"));
        event.registerEntityRenderer(ModEntities.BANDIT_CHIEF.get(), ctx -> noTilt(ctx, "bandit_chief"));
        event.registerEntityRenderer(ModEntities.BONE_FOOTMAN.get(), ctx -> noTilt(ctx, "bone_footman"));
        event.registerEntityRenderer(ModEntities.BONE_BOWMAN.get(), ctx -> noTilt(ctx, "bone_bowman"));
        event.registerEntityRenderer(ModEntities.BONE_STANDARD.get(), ctx -> noTilt(ctx, "bone_standard"));
        event.registerEntityRenderer(ModEntities.GRAIN_RAT.get(), ctx -> noTilt(ctx, "grain_rat"));
        event.registerEntityRenderer(ModEntities.STRAY_WISP.get(), ctx -> noTilt(ctx, "stray_wisp"));
        event.registerEntityRenderer(ModEntities.FERAL_DOG.get(), ctx -> noTiltStillHead(ctx, "feral_dog"));
        event.registerEntityRenderer(ModEntities.MOSS_DEAD.get(), ctx -> noTilt(ctx, "moss_dead"));
        event.registerEntityRenderer(ModEntities.MIST_ANGLER.get(), ctx -> noTilt(ctx, "mist_angler"));
        event.registerEntityRenderer(ModEntities.THUNDER_WOLF.get(), ctx -> noTiltStillHead(ctx, "thunder_wolf"));
        event.registerEntityRenderer(ModEntities.EMBER_JACKAL.get(), ctx -> noTiltStillHead(ctx, "ember_jackal"));
        event.registerEntityRenderer(ModEntities.BOLT_SLINGER.get(), ctx -> noTilt(ctx, "bolt_slinger"));
        event.registerEntityRenderer(ModEntities.STORM_JELLY.get(), ctx -> noTilt(ctx, "storm_jelly"));
        event.registerEntityRenderer(ModEntities.CRAG_TORTOISE.get(), ctx -> noTilt(ctx, "crag_tortoise"));
        event.registerEntityRenderer(ModEntities.EMBER_SPITTER.get(), ctx -> noTilt(ctx, "ember_spitter"));
        event.registerEntityRenderer(ModEntities.ASH_WHIRL.get(), ctx -> noTilt(ctx, "ash_whirl"));
        event.registerEntityRenderer(ModEntities.BASALT_SCORPION.get(), ctx -> noTilt(ctx, "basalt_scorpion"));
        event.registerEntityRenderer(ModEntities.ROCK_BOAR.get(), ctx -> noTiltStillHead(ctx, "rock_boar"));
        // 地下のダンジョンの主 (docs/design/55)
        event.registerEntityRenderer(ModEntities.BONE_CENTIPEDE.get(), ctx -> new jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<>(ctx, "bone_centipede", 1.8f));
        event.registerEntityRenderer(ModEntities.SHELL_OCTOPUS.get(), ShellOctopusRenderer::new);
        event.registerEntityRenderer(ModEntities.CRYSTAL_LIZARD.get(), ctx -> new jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<>(ctx, "crystal_lizard", 1.7f));
        event.registerEntityRenderer(ModEntities.FORGE_GIANT.get(), ctx -> new jp.revati.skyabyss.client.lowpoly.LowPolyGeoRenderer<>(ctx, "forge_giant", 1.6f));
        // 地下のダンジョン専用の敵と中ボス (docs/design/55 の 4 の 7)
        event.registerEntityRenderer(ModEntities.PIT_DIGGER.get(), ctx -> noTilt(ctx, "pit_digger"));
        event.registerEntityRenderer(ModEntities.BONE_ROLLER.get(), ctx -> noTilt(ctx, "bone_roller"));
        event.registerEntityRenderer(ModEntities.PIT_FOREMAN.get(), ctx -> noTilt(ctx, "pit_foreman"));
        event.registerEntityRenderer(ModEntities.DROWNED_SAILOR.get(), ctx -> noTilt(ctx, "drowned_sailor"));
        event.registerEntityRenderer(ModEntities.CAVE_JELLY.get(), ctx -> noTilt(ctx, "cave_jelly"));
        event.registerEntityRenderer(ModEntities.BELL_HERMIT.get(), ctx -> noTilt(ctx, "bell_hermit"));
        event.registerEntityRenderer(ModEntities.HORN_BEETLE.get(), ctx -> noTilt(ctx, "horn_beetle"));
        event.registerEntityRenderer(ModEntities.CRYSTAL_EYE.get(), ctx -> noTilt(ctx, "crystal_eye"));
        event.registerEntityRenderer(ModEntities.GEODE_GOLEM.get(), ctx -> noTilt(ctx, "geode_golem"));
        event.registerEntityRenderer(ModEntities.BELLOWS_IMP.get(), ctx -> noTilt(ctx, "bellows_imp"));
        event.registerEntityRenderer(ModEntities.LAVA_SLUG.get(), ctx -> noTilt(ctx, "lava_slug"));
        // 逃げる獣 (倒れる動きは death アニメーションで横倒しにする)
        event.registerEntityRenderer(ModEntities.BELL_HARE.get(), ctx -> noTilt(ctx, "bell_hare"));
        event.registerEntityRenderer(ModEntities.MOSS_ROLLER.get(), ctx -> noTilt(ctx, "moss_roller"));
        event.registerEntityRenderer(ModEntities.PEARL_OTTER.get(), ctx -> noTilt(ctx, "pearl_otter"));
        event.registerEntityRenderer(ModEntities.BOLT_WEASEL.get(), ctx -> noTilt(ctx, "bolt_weasel"));
        event.registerEntityRenderer(ModEntities.EMBER_MOLE.get(), ctx -> noTilt(ctx, "ember_mole"));
        event.registerEntityRenderer(ModEntities.OX_SMITH.get(), ctx -> noTilt(ctx, "ox_smith"));
        event.registerEntityRenderer(ModEntities.TIDE_CRAB.get(), ctx -> noTiltStillHead(ctx, "tide_crab"));
        event.registerEntityRenderer(ModEntities.STORM_RAM.get(), ctx -> noTiltStillHead(ctx, "storm_ram"));
        event.registerEntityRenderer(ModEntities.ASH_LIZARD.get(), ctx -> noTiltStillHead(ctx, "ash_lizard"));
        event.registerEntityRenderer(ModEntities.STONE_WHALE_HEART.get(), StoneWhaleHeartRenderer::new);
        event.registerEntityRenderer(ModEntities.BONE_SPIKE.get(), ctx -> new ArrowRenderer<BoneSpike>(ctx) {
            @Override
            public ResourceLocation getTextureLocation(BoneSpike entity) {
                return SPIKE;
            }
        });
    }

    private static <T extends net.minecraft.world.entity.Entity & GeoAnimatable> GeoEntityRenderer<T> noTilt(EntityRendererProvider.Context ctx, String name) {
        return new LowPolyGeoRenderer<T>(ctx, name, 1.0f) {
            @Override
            protected float getDeathMaxRotation(T animatable) {
                return 0f;
            }
        };
    }

    // 頭の骨を人の方へ向けない (GeckoLib の首振りは head の回転を上書きするので、吠えて天を仰ぐ動きが消える。野犬・雷狼)
    private static <T extends net.minecraft.world.entity.Entity & GeoAnimatable> GeoEntityRenderer<T> noTiltStillHead(EntityRendererProvider.Context ctx, String name) {
        return new LowPolyGeoRenderer<T>(ctx, new LowPolyGeoRenderer.Model<T>(name, false), e -> name, 1.0f) {
            @Override
            protected float getDeathMaxRotation(T animatable) {
                return 0f;
            }
        };
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
    // 鉾の値は前の道具 (captain_v4.py の PIKE_*) から写した。道具は消した。今の値が正本
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
