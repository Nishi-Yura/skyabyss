package jp.revati.skyabyss.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.weapon.MoveSet;
import jp.revati.skyabyss.weapon.MoveSets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

// プレイヤーが mod 武器を持ったときの動き (クライアントだけ)。
//  - 一人称: 手元の動き (IClientItemExtensions#applyForgeHandTransform)。品ごとの MoveSet の段 (連撃・点検) から作る
//  - 三人称・ほかのプレイヤー: 腕の構え (WeaponArmPoses。HumanoidModel.ArmPose の拡張)
//  - 体の傾き: 大きな武器は、振るときに体ごと前へ倒れる (RenderLivingEvent)
// 動きは全部、バニラの「振り」の合図から始めた SwingClock の進み (攻撃速度で長さが変わる) と連撃の段から作るので、サーバーに何も送らず、ほかのプレイヤーにも同じに見える。
// サーバー側の「当たる tick」は weapon/WeaponStrikes が同じ MoveSet から決める
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WeaponClient {
    // 開発用: 0..1 にすると、自分の振りをその進みで止める (台本 swingpose。スクショ用)。負 = 使わない
    public static volatile float forcedSwing = -1f;

    // 体を倒す回転の軸の向き: 描く前にかけた変形を、描いたあとで打ち消すために覚えておく (entity id -> [体の向き, 角度])
    private static final Int2ObjectOpenHashMap<float[]> LEANED = new Int2ObjectOpenHashMap<>();
    // 腰の高さ (体を倒す回転の中心)
    private static final float HIP = 0.85f;

    private WeaponClient() {
    }

    @SubscribeEvent
    static void onRegisterExtensions(RegisterClientExtensionsEvent event) {
        for (var entry : WeaponKind.items()) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, entry.getKey()));
            if (item != Items.AIR) {
                event.registerItem(new Extension(entry.getKey(), entry.getValue()), item);
            }
        }
        event.registerItem(new BowExtension("thunder_wing_bow"), jp.revati.skyabyss.item.storm.StormGearSetup.BOW.get());
        event.registerItem(new BowExtension("abyss_storm_bow"), jp.revati.skyabyss.abyss.AbyssSetup.STORM_BOW_ITEM.get());
        // 職の章の弓と祠の弓 (item/ChapterGear・rpg/skill/JobChoice)
        for (String id : java.util.List.of("whale_bow", "tide_bow", "ember_bow", "shrine_bow", "abyss_whale_bow", "abyss_tide_bow", "abyss_ember_bow",
                "abyss_depth_bow", "whale_rib_longbow", "tide_coral_bow", "storm_gale_bow", "ember_flare_bow", "moon_waning_bow", "moon_starfall_bow", "divine_plume_bow")) {
            event.registerItem(new BowExtension(id), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, id)));
        }
        // 珊瑚の盾: バニラの構え (構えている間)。見た目 (低ポリのメッシュ) と、殴りつける動き・副の手での小さな押し出しだけ足す
        event.registerItem(new ShieldExtension(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "coral_shield")));
        // 市の旅の盾 (item/outfit) も同じ
        event.registerItem(new ShieldExtension(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "travel_shield")));
    }

    // 一人称の手元を、バニラの置き方 (applyItemArmTransform) と同じ位置へ
    private static void baseTransform(PoseStack pose, int i, float equipProcess) {
        pose.translate(i * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);
    }

    // 一人称: 動きの一コマ (MoveSet の段) を手元に足す。回転の軸は、バニラの振りと同じに斜め 45 度 (体の中心へ向かう向き) に取る:
    // 前後に傾ける RX、ひねる RZ、横へ薙ぐ RY。RY・RZ の全回転は、混ぜ具合をかけても巻き戻らないよう角度を丸めてから混ぜる
    // 一人称では重い武器 (大鎚・戦斧・大剣・鎌) は画面を覆わないよう、動きの手元の移動・回転を弱める (三人称の腕・胴の動きはそのまま)
    private static float fpDamp(String id) {
        return switch (id) {
            case "smith_hammer" -> 0.5f;
            case "ember_war_axe", "abyss_ash_scythe" -> 0.65f;
            case "whalebone_greatsword", "abyss_greatsword" -> 0.75f;
            default -> 1f;
        };
    }

    private static void applyStep(PoseStack pose, int i, SwingClock.Active a, boolean frozen, long tick) {
        applyStep(pose, i, a, frozen, tick, 1f);
    }

    private static void applyStep(PoseStack pose, int i, SwingClock.Active a, boolean frozen, long tick, float k) {
        MoveSet.Step st = a.step();
        float t = a.t();
        float w = a.w();
        w *= k;
        pose.translate(i * st.get(MoveSet.TX, t, 0f) * w, st.get(MoveSet.TY, t, 0f) * w, st.get(MoveSet.TZ, t, 0f) * w);
        if (frozen) {
            // ヒットストップ: 止まっている間、ほんの小さく震える
            pose.translate(((tick & 1L) == 0 ? 1 : -1) * 0.006f, 0.003f, 0f);
        }
        pose.mulPose(Axis.YP.rotationDegrees(i * (45f + WeaponArmPoses.wrap(st.get(MoveSet.RY, t, 0f)) * w)));
        pose.mulPose(Axis.ZP.rotationDegrees(i * WeaponArmPoses.wrap(st.get(MoveSet.RZ, t, 0f)) * w));
        pose.mulPose(Axis.XP.rotationDegrees(st.get(MoveSet.RX, t, 0f) * w));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45f));
        WeaponFeel.fpTrail = st.get(MoveSet.TRAIL, t, 0f) * a.w();
        WeaponFeel.fpGlow = st.get(MoveSet.GLOW, t, 0f) * a.w();
    }

    // 一人称の見せ方 (2026-10-27 の声「一人称で武器をもう少し見せて。邪魔にならない程度に」): 待機の間だけ、型ごとに手元をずらす。
    // 短剣・爪は画面の右下に隠れるので内へ上げ、杖は少し内へ、大剣・鎚は画面をふさがないよう少し下げる。振っている間は w (動きの混ぜ具合) の分だけ戻す
    static void show(PoseStack pose, int i, WeaponKind kind, float k) {
        if (kind == null || k <= 0.001f) {
            return;
        }
        switch (kind) {
            case FANG, CLAW -> pose.translate(i * -0.09f * k, 0.13f * k, 0.02f * k);
            case POKER -> pose.translate(i * -0.05f * k, 0.05f * k, 0f);
            case GREATSWORD -> pose.translate(0f, -0.04f * k, 0.03f * k);
            case HAMMER, AXE -> pose.translate(i * 0.02f * k, -0.03f * k, 0.02f * k);
            default -> {
            }
        }
    }

    // 一人称: 待機のゆれ (呼吸)
    private static void applyIdle(PoseStack pose, int i, MoveSet ms, LocalPlayer player, float partialTick) {
        float ph = (player.tickCount + partialTick) / 20f * ms.swayHz * Mth.TWO_PI;
        float s = Mth.sin(ph);
        pose.translate(0f, ms.sway * 0.4f * s, 0f);
        pose.mulPose(Axis.XP.rotationDegrees(ms.sway * 16f * s));
        pose.mulPose(Axis.ZP.rotationDegrees(i * ms.sway * 9f * Mth.cos(ph * 0.7f)));
        WeaponFeel.fpTrail = 0f;
        WeaponFeel.fpGlow = 0f;
    }

    // 雷翼の弓・深淵の雷弓: 引いている間はバニラの引きの動きに、段ごとの震えを足す。放したあとは反動と MoveSet の返し (一人称)
    private static final class BowExtension implements IClientItemExtensions {
        private final String id;

        BowExtension(String id) {
            this.id = id;
        }

        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return WeaponItemRenderer.get();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return hand == InteractionHand.MAIN_HAND ? WeaponArmPoses.BOW.getValue() : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                float equipProcess, float swingProcess) {
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            if (player.isUsingItem() && player.getUseItem() == stack) {
                drawTransform(pose, i, player, stack, partialTick, equipProcess);
                return true;
            }
            float r = BowRecoil.amount(player);
            float fl = BowRecoil.flourish(player);
            baseTransform(pose, i, equipProcess);
            // 待機の弓は画面の端で切れるので、少し内へ上げる
            pose.translate(i * -0.07f, 0.06f, 0f);
            if (r <= 0f && fl < 0f) {
                return true;
            }
            // 手元が前へ弾けて、戻る
            // 放した弓は前へ押し出される (矢の向きへ): 手元が前へ出て、少しだけ上へ
            pose.translate(0f, 0.025f * r, -0.1f * r);
            pose.mulPose(Axis.XP.rotationDegrees(-5f * r));
            pose.mulPose(Axis.ZP.rotationDegrees(i * -4f * r));
            MoveSet ms = MoveSets.get(id);
            if (fl >= 0f && ms != null && !ms.steps.isEmpty()) {
                // 放したあとの返し: 弓がくるりと回って戻る (弦の揺れ)
                MoveSet.Step st = ms.step(0);
                float w = st.weight(fl);
                pose.translate(i * st.get(MoveSet.TX, fl, 0f) * w, st.get(MoveSet.TY, fl, 0f) * w, st.get(MoveSet.TZ, fl, 0f) * w);
                pose.mulPose(Axis.ZP.rotationDegrees(i * WeaponArmPoses.wrap(st.get(MoveSet.RZ, fl, 0f)) * w * 0.35f));
                pose.mulPose(Axis.XP.rotationDegrees(st.get(MoveSet.RX, fl, 0f) * w));
                pose.mulPose(Axis.YP.rotationDegrees(i * st.get(MoveSet.RY, fl, 0f) * w));
            }
            return true;
        }

        // バニラの弓の引きの動き (ItemInHandRenderer の BOW) と同じ数で置き直し、段 (引き 0.65・0.9) ごとの細かな震えと、流れるような小さな横ゆれを足す
        private static void drawTransform(PoseStack pose, int i, LocalPlayer player, ItemStack stack, float partialTick, float equipProcess) {
            baseTransform(pose, i, equipProcess);
            pose.translate(i * -0.2785682f, 0.18344387f, 0.15731531f);
            pose.mulPose(Axis.XP.rotationDegrees(-13.935f));
            pose.mulPose(Axis.YP.rotationDegrees(i * 35.3f));
            pose.mulPose(Axis.ZP.rotationDegrees(i * -9.785f));
            float f5 = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0f);
            float f6 = f5 / 20.0f;
            float pull = f6;
            f6 = (f6 * f6 + f6 * 2.0f) / 3.0f;
            if (f6 > 1.0f) {
                f6 = 1.0f;
            }
            if (f6 > 0.1f) {
                float f7 = Mth.sin((f5 - 0.1f) * 1.3f);
                float f3 = f6 - 0.1f;
                float f4 = f7 * f3;
                pose.translate(f4 * 0.0f, f4 * 0.004f, f4 * 0.0f);
            }
            pose.translate(f6 * 0.0f, f6 * 0.0f, f6 * 0.04f);
            pose.scale(1.0f, 1.0f, 1.0f + f6 * 0.2f);
            pose.mulPose(Axis.YN.rotationDegrees(i * 45.0f));
            // 段: 0.65 から小さく震え、0.9 (満引き) で力がこもって強く震える。引き始めは弓がゆっくり起きる
            int stage = pull >= 0.9f ? 2 : pull >= 0.65f ? 1 : 0;
            float tremble = stage == 2 ? 0.0016f : stage == 1 ? 0.0007f : 0f;
            pose.translate(Mth.sin(f5 * 2.3f) * tremble, Mth.cos(f5 * 1.9f) * tremble, 0f);
            float rise = Mth.clamp(pull * 3f, 0f, 1f);
            pose.mulPose(Axis.ZP.rotationDegrees(i * (1.6f * Mth.sin(f5 * 0.33f) * rise)));
            pose.mulPose(Axis.XP.rotationDegrees((1f - rise) * 4f));
        }
    }

    // 珊瑚の盾: 主の手で殴ると、肩から押し出す (MoveSet)。副の手に持っているときは、主の手の振りに合わせて小さく前へ出る。構えている間はバニラ
    private static final class ShieldExtension implements IClientItemExtensions {
        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return WeaponItemRenderer.get();
        }

        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            // 構えている間 (どちらの手でも) はバニラの「盾を構える」腕 (BLOCK)。主の手の殴る構えのままだと盾が体の横に残る
            if (entity.isUsingItem() && entity.getUseItem() == stack) {
                return null;
            }
            return hand == InteractionHand.MAIN_HAND ? WeaponArmPoses.generic() : null;
        }

        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                float equipProcess, float swingProcess) {
            if (player.isUsingItem() && player.getUseItem() == stack) {
                return false;
            }
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            if (stack == player.getMainHandItem()) {
                SwingClock.Active a = SwingClock.current(player, partialTick);
                if (a == null) {
                    return false;
                }
                baseTransform(pose, i, equipProcess);
                applyStep(pose, i, a, SwingClock.frozen(player), player.tickCount);
                return true;
            }
            // 副の手: 主の手のバニラの振り (0..1) の前半に合わせて、盾が前へ小さく出る
            float t = player.swingingArm == InteractionHand.MAIN_HAND ? player.getAttackAnim(partialTick) : 0f;
            if (t <= 0f) {
                return false;
            }
            float b = Mth.sin(t * Mth.PI);
            baseTransform(pose, i, equipProcess);
            pose.translate(0f, 0.02f * b, -0.1f * b);
            pose.mulPose(Axis.XP.rotationDegrees(-7f * b));
            return true;
        }
    }

    private static final class Extension implements IClientItemExtensions {
        private final String id;
        private final WeaponKind kind;

        Extension(String id, WeaponKind kind) {
            this.id = id;
            this.kind = kind;
        }

        // 手・地面・額縁・頭は低ポリメッシュ (WeaponItemRenderer)
        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return WeaponItemRenderer.get();
        }

        // 三人称の構え: 利き手に持っているときだけ (反対の手はバニラのまま)
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            return hand == InteractionHand.MAIN_HAND ? WeaponArmPoses.of(kind) : null;
        }

        // 一人称: バニラの手元の置き方 (applyItemArmTransform) と同じ位置から、品ごとの動きを足す
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                float equipProcess, float swingProcess) {
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            baseTransform(pose, i, equipProcess);
            MoveSet ms = MoveSets.get(id);
            SwingClock.Active a = stack == player.getMainHandItem() ? SwingClock.current(player, partialTick) : null;
            show(pose, i, kind, a == null ? 1f : 1f - Mth.clamp(a.w(), 0f, 1f));
            if (a != null) {
                applyStep(pose, i, a, SwingClock.frozen(player), player.tickCount, fpDamp(id));
            } else if (ms != null) {
                applyIdle(pose, i, ms, player, partialTick);
            }
            return true;
        }
    }

    // 体の傾き (三人称): 腰を中心に、体の向きに合わせて前へ倒す。pushPose を使わないのは CowerClient と同じ理由
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player player) || event.isCanceled() || player.getVehicle() != null) {
            return;
        }
        SwingClock.Active a = SwingClock.current(player, event.getPartialTick());
        if (a == null || !a.step().has(MoveSet.LEAN)) {
            return;
        }
        float angle = a.step().get(MoveSet.LEAN, a.t(), 0f) * a.w();
        if (Math.abs(angle) < 0.05f) {
            return;
        }
        float yaw = 180f - Mth.rotLerp(event.getPartialTick(), player.yBodyRotO, player.yBodyRot);
        lean(event.getPoseStack(), yaw, -angle);
        LEANED.put(player.getId(), new float[] {yaw, angle});
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onRenderPost(RenderLivingEvent.Post<?, ?> event) {
        float[] applied = LEANED.remove(event.getEntity().getId());
        if (applied != null) {
            lean(event.getPoseStack(), applied[0], applied[1]);
        }
    }

    // 腰を中心に、体の向き (yaw) に対して前へ degrees だけ回す (負 = 前へ倒れる)。同じ yaw で逆の角度をかければ元に戻る
    private static void lean(PoseStack pose, float yaw, float degrees) {
        pose.translate(0, HIP, 0);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(degrees));
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.translate(0, -HIP, 0);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        LEANED.clear();
        forcedSwing = -1f;
        WeaponTrail.clear();
    }
}
