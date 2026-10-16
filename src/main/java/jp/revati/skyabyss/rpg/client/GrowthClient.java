package jp.revati.skyabyss.rpg.client;

import java.util.ArrayList;
import java.util.List;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.rpg.net.GrowthGainPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// 成長のクライアント側の状態: 画面の「+12」の列、レベルアップの文字の残り時間、キーで画面を開く
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class GrowthClient {
    // 「+12」一つの表示時間 (tick)
    static final int POPUP_TICKS = 60;
    // レベルアップの文字: 現れる 10、とどまる 50、消える 30
    static final int LEVEL_UP_TICKS = 90;
    static final int MAX_POPUPS = 4;

    static final class Popup {
        int amount;
        final String reason;
        int age;

        Popup(int amount, String reason) {
            this.amount = amount;
            this.reason = reason;
        }
    }

    static final List<Popup> POPUPS = new ArrayList<>();
    static int levelUpAge = -1;
    static int levelUpLevel;
    // バーが光る残り時間 (経験値を得たとき)
    static int barFlash;

    private GrowthClient() {
    }

    // サーバーからの「経験値を得た」(RpgNetwork から呼ばれる)
    public static void onGain(GrowthGainPayload payload) {
        // 理由のない同じ種類 (敵の撃破) が続いたら、一つにまとめる
        if (!POPUPS.isEmpty()) {
            Popup last = POPUPS.get(POPUPS.size() - 1);
            if (last.reason.equals(payload.reason()) && last.age < 20) {
                last.amount += payload.amount();
                last.age = 0;
                afterGain(payload);
                return;
            }
        }
        POPUPS.add(new Popup(payload.amount(), payload.reason()));
        while (POPUPS.size() > MAX_POPUPS) {
            POPUPS.remove(0);
        }
        afterGain(payload);
    }

    private static void afterGain(GrowthGainPayload payload) {
        barFlash = 12;
        if (payload.levels() > 0) {
            levelUpAge = 0;
            // 記録の同期はこの知らせより先に届いている
            Minecraft mc = Minecraft.getInstance();
            levelUpLevel = mc.player != null ? jp.revati.skyabyss.rpg.Growth.get(mc.player).level : 0;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            POPUPS.clear();
            levelUpAge = -1;
            return;
        }
        while (GrowthClientSetup.STATUS_KEY.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new GrowthScreen());
            }
        }
        POPUPS.removeIf(p -> ++p.age > POPUP_TICKS);
        if (levelUpAge >= 0 && ++levelUpAge > LEVEL_UP_TICKS) {
            levelUpAge = -1;
        }
        if (barFlash > 0) {
            barFlash--;
        }
    }
}
