package jp.revati.skyabyss.item.storm.client;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.storm.StormGearEvents;
import jp.revati.skyabyss.item.storm.StormGearSetup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 雷翼の装備の画面側:
//  - 雷翼の弓の引き絵 (バニラの pull / pulling の印。バニラは Items.BOW にしか登録しない)
//  - 空駆けのキー: 空中で跳躍のキーを押し直したら、手元ですぐ動かしてサーバーへ知らせる (サーバーが確かめて同じ向きに動かし、待ちを付ける)
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class StormGearClient {
    private static boolean jumpWasDown;
    private static long lastDash = Long.MIN_VALUE / 2;

    private StormGearClient() {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(StormGearSetup.BOW.get(), ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
                if (entity == null || entity.getUseItem() != stack) {
                    return 0f;
                }
                return (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20f;
            });
            ItemProperties.register(StormGearSetup.BOW.get(), ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1f : 0f);
        });
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.screen != null) {
            jumpWasDown = false;
            return;
        }
        boolean down = mc.options.keyJump.isDown();
        // 押し始め (地面で押した跳躍は onGround なので数えない)
        long now = p.level().getGameTime();
        // サーバーの待ちが届くまでの間に二度跳ばないよう、手元でも待つ
        if (down && !jumpWasDown && now - lastDash >= jp.revati.skyabyss.item.storm.StormGear.DASH_COOLDOWN && StormGearEvents.canDash(p)) {
            lastDash = now;
            p.setDeltaMovement(StormGearEvents.dashVelocity(p));
            p.fallDistance = 0;
            PacketDistributor.sendToServer(new StormGearEvents.DashPayload());
        }
        jumpWasDown = down;
    }
}
