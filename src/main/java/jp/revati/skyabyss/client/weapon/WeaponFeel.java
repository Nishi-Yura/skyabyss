package jp.revati.skyabyss.client.weapon;

import com.mojang.blaze3d.platform.InputConstants;

import jp.revati.skyabyss.Config;
import jp.revati.skyabyss.KeyDefaults;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.weapon.InspectPayload;
import jp.revati.skyabyss.weapon.MoveSet;
import jp.revati.skyabyss.weapon.StrikePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;

// 武器の「手応え」(クライアントだけ): 当たったときの画面の小さな揺れ、ヒットストップの受け取り、点検のキー。
//  - 揺れは、自分の重い一撃が当たったときだけ。強さは武器の段の shake (0..1) × 設定 weaponShake。画面の揺れ全体を切る設定 (screenShake) でも止まる
//  - ヒットストップは、サーバーの合図 (StrikePayload) で振りの時計を止める。自分もほかの人も同じ
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class WeaponFeel {
    public static final KeyMapping INSPECT_KEY = new KeyMapping("key.skyabyss.weapon_inspect", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, KeyDefaults.WEAPON_INSPECT, "key.categories.skyabyss");

    // 一人称の手元の描画に渡す、今の軌跡の濃さと光の強まり (WeaponClient が毎フレーム入れ、WeaponItemRenderer が読む)
    static volatile float fpTrail;
    static volatile float fpGlow;

    // 揺れ: 強さ (度) と残り tick
    private static float shakeAmp;
    private static int shakeAge;
    private static final int SHAKE_TICKS = 6;

    private WeaponFeel() {
    }

    public static void receiveStrike(StrikePayload p) {
        SwingClock.freeze(p.player(), p.hitstop());
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getId() == p.player() && p.shake() > 0) {
            startShake(p.shake() / 100f);
        }
    }

    public static void receiveInspect(InspectPayload p) {
        SwingClock.startInspect(p.player());
    }

    static void startShake(float amount) {
        if (!Config.SCREEN_SHAKE.get()) {
            return;
        }
        float strength = Config.WEAPON_SHAKE.get().floatValue();
        if (strength <= 0.01f || amount <= 0f) {
            return;
        }
        // 重い一撃で最大 約 1.4 度 (設定が 1 のとき)。ほんの揺れ
        shakeAmp = Math.max(shakeAmp * 0.5f, amount * strength * 1.4f);
        shakeAge = 0;
    }

    @SubscribeEvent
    static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(INSPECT_KEY);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (shakeAmp > 0f && ++shakeAge >= SHAKE_TICKS) {
            shakeAmp = 0f;
        }
        if (mc.player == null) {
            return;
        }
        while (INSPECT_KEY.consumeClick()) {
            if (mc.screen != null || mc.player.isUsingItem()) {
                continue;
            }
            MoveSet ms = SwingClock.setOf(mc.player);
            if (ms != null && ms.inspect != null) {
                // サーバーが、見ている人みんな (自分も) へ返す
                PacketDistributor.sendToServer(new InspectPayload(0));
            }
        }
    }

    @SubscribeEvent
    static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (shakeAmp <= 0f) {
            return;
        }
        float age = shakeAge + (float) event.getPartialTick();
        float k = Math.max(0f, 1f - age / SHAKE_TICKS);
        k *= k;
        event.setRoll(event.getRoll() + (float) Math.sin(age * 2.9) * shakeAmp * k);
        event.setPitch(event.getPitch() + (float) Math.cos(age * 3.7) * shakeAmp * 0.6f * k);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        shakeAmp = 0f;
        fpTrail = 0f;
        fpGlow = 0f;
    }
}
