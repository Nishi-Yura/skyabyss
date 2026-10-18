package jp.revati.skyabyss.client.weapon;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
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
//  - 一人称: 手元の動き (IClientItemExtensions#applyForgeHandTransform)。種類ごとの SwingMove から作る
//  - 三人称・ほかのプレイヤー: 腕の構え (WeaponArmPoses。HumanoidModel.ArmPose の拡張)
//  - 体の傾き: 大きな武器は、振るときに体ごと前へ倒れる (RenderLivingEvent)
// 動きは全部、バニラの「振り」の合図から始めた SwingClock の進み (攻撃速度で長さが変わる) から作るので、サーバーに何も送らず、ほかのプレイヤーにも同じに見える
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
                event.registerItem(new Extension(entry.getValue()), item);
            }
        }
        event.registerItem(new BowExtension(), jp.revati.skyabyss.item.storm.StormGearSetup.BOW.get());
        // 珊瑚の盾: バニラの構え。見た目 (低ポリのメッシュ) だけ差し替える
        event.registerItem(new MeshOnlyExtension(), BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "coral_shield")));
    }

    private static final class MeshOnlyExtension implements IClientItemExtensions {
        @Override
        public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return WeaponItemRenderer.get();
        }
    }

    // 雷翼の弓: 引いている間はバニラ。放したあとの反動だけ足す
    private static final class BowExtension implements IClientItemExtensions {
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
            float r = BowRecoil.amount(player);
            if (r <= 0f) {
                return false;
            }
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(i * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);
            // 手元が後ろ上へ跳ねて、戻る
            pose.translate(0f, 0.03f * r, 0.08f * r);
            pose.mulPose(Axis.XP.rotationDegrees(8f * r));
            pose.mulPose(Axis.ZP.rotationDegrees(i * -4f * r));
            return true;
        }
    }

    private static final class Extension implements IClientItemExtensions {
        private final WeaponKind kind;

        Extension(WeaponKind kind) {
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

        // 一人称: バニラの手元の置き方 (applyItemArmTransform) と同じ位置から、種類ごとの動きを足す
        @Override
        public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                float equipProcess, float swingProcess) {
            int i = arm == HumanoidArm.RIGHT ? 1 : -1;
            pose.translate(i * 0.56f, -0.52f + equipProcess * -0.6f, -0.72f);
            float t = forcedSwing >= 0 ? forcedSwing : SwingClock.progress(player, partialTick);
            if (t > 0f) {
                SwingMove mv = kind.move;
                float w = mv.weight(t);
                pose.translate(i * mv.get(SwingMove.TX, t, 0f) * w, mv.get(SwingMove.TY, t, 0f) * w, mv.get(SwingMove.TZ, t, 0f) * w);
                // 回転の軸は、バニラの振りと同じに斜め 45 度 (体の中心へ向かう向き) に取る: 前後に傾ける RX、ひねる RZ、横へ薙ぐ RY
                pose.mulPose(Axis.YP.rotationDegrees(i * (45f + mv.get(SwingMove.RY, t, 0f) * w)));
                pose.mulPose(Axis.ZP.rotationDegrees(i * mv.get(SwingMove.RZ, t, 0f) * w));
                pose.mulPose(Axis.XP.rotationDegrees(mv.get(SwingMove.RX, t, 0f) * w));
                pose.mulPose(Axis.YP.rotationDegrees(i * -45f));
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
        WeaponKind kind = WeaponKind.of(player.getMainHandItem());
        if (kind == null || !kind.move.has(SwingMove.LEAN)) {
            return;
        }
        float t = player == Minecraft.getInstance().player && forcedSwing >= 0 ? forcedSwing
                : SwingClock.progress(player, event.getPartialTick());
        if (t <= 0f) {
            return;
        }
        float angle = kind.move.get(SwingMove.LEAN, t, 0f) * kind.move.weight(t);
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
    }
}
