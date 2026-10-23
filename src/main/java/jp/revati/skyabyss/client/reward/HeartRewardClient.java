package jp.revati.skyabyss.client.reward;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.item.TitanGearSet;
import jp.revati.skyabyss.loot.reward.HeartRewardNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

// 心臓の遺物の選択画面のクライアント側の入口 (docs/design/30_heart_reward_ui.md)。パケットを受けて画面を開く・結果を渡す・
// 「受け取れる遺物がある」旗 (旅嚢の「遺物」ボタン) を持つ。選ぶ決まりはすべてサーバー。
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class HeartRewardClient {
    // 受け取れる遺物がある (サーバーが State で知らせる)
    private static boolean pending;

    // 鍵の場面 (SkyKeyScene) が出ている間に届いた・場面に押しのけられた選択画面。場面が閉じて何も開いていなくなってから開く
    private static HeartRewardNet.Open deferred;
    // 今開いている選択画面の元 (場面に押しのけられたとき、開き直すため)
    private static HeartRewardNet.Open current;

    private HeartRewardClient() {
    }

    public static boolean pending() {
        return pending;
    }

    public static void onState(HeartRewardNet.State state) {
        Minecraft.getInstance().execute(() -> pending = state.pending());
    }

    // 鍵の場面を出す直前に呼ぶ: 選択画面が開いていれば、場面のあとに開き直す
    public static void interruptedBy(net.minecraft.client.gui.screens.Screen screen) {
        if (screen instanceof HeartRewardScreen && current != null) {
            deferred = current;
        }
    }

    @SubscribeEvent
    static void onTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (deferred != null && mc.player != null && mc.screen == null && !jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
            HeartRewardNet.Open d = deferred;
            deferred = null;
            onOpen(d);
        }
    }

    public static void onOpen(HeartRewardNet.Open open) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof jp.revati.skyabyss.client.story.SkyKeyScene
                    || jp.revati.skyabyss.client.cinematic.CinematicClient.active()) {
                deferred = open;
                return;
            }
            List<ItemStack> stacks = new ArrayList<>();
            for (String id : open.options()) {
                TitanGearSet.item(id).ifPresent(item -> stacks.add(new ItemStack(item)));
            }
            if (stacks.isEmpty()) {
                return;
            }
            pending = true;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof HeartRewardScreen) {
                return;
            }
            current = open;
            deferred = null;
            mc.setScreen(new HeartRewardScreen(open.titan(), stacks, open.ownedMask(), open.auto()));
        });
    }

    public static void onResult(HeartRewardNet.Result result) {
        Minecraft.getInstance().execute(() -> {
            if (Minecraft.getInstance().screen instanceof HeartRewardScreen screen) {
                screen.onResult(result.code(), result.item());
            }
        });
    }

    // 旅嚢・控え帳から: サーバーに開いてもらう
    public static void request() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new HeartRewardNet.Request());
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        pending = false;
        deferred = null;
        current = null;
    }
}
