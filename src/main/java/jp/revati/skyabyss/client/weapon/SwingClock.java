package jp.revati.skyabyss.client.weapon;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// mod 武器の「振りの時計」(クライアントだけ)。
// バニラの振り (attackAnim) は、武器の重さに関係なく 6 tick (約 0.3 秒) で終わる。
// 大剣・大鎚 (攻撃速度 0.8〜1.0 = 次の一撃まで 1 秒前後) が拳と同じ速さで振られて軽く見えたので、
// 振りが始まったのを (バニラの swing の合図で) 見つけたら、武器の攻撃速度から決めた長さの自分の時計を回し、
// 一人称・三人称・体の傾きの動きはその進み (0..1) から作る。
// サーバーへ何も送らず、ほかのプレイヤーの振りも同じバニラの合図 (ClientboundAnimatePacket) から始まるので、同じ長さで見える
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SwingClock {
    // 攻撃速度 1.0 のときの振りの長さ (tick)。長さ = これ / 攻撃速度 (6〜16 tick に収める)
    static final float TICKS_AT_SPEED_1 = 12f;
    static final int MIN_TICKS = 6;
    static final int MAX_TICKS = 16;

    private static final class State {
        boolean active;
        int elapsed;
        int duration = MIN_TICKS;
        // 前の tick のバニラの swingTime (振っていなければ -1)。小さくなったら振り直し
        int lastVanilla = -1;
    }

    // 開発用: true にすると昔の動き (バニラの 6 tick の進みをそのまま使う) に戻す。台本 swingclock off。変更前後の比べ用
    public static volatile boolean legacy = false;

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private SwingClock() {
    }

    // 武器の攻撃速度から決める振りの長さ (tick)
    static int durationFor(LivingEntity entity) {
        double speed = entity.getAttributeValue(Attributes.ATTACK_SPEED);
        if (!(speed > 0.05)) {
            return MAX_TICKS;
        }
        return Mth.clamp(Math.round((float) (TICKS_AT_SPEED_1 / speed)), MIN_TICKS, MAX_TICKS);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        for (Player p : mc.level.players()) {
            State st = STATES.get(p.getId());
            WeaponKind kind = WeaponKind.of(p.getMainHandItem());
            boolean swinging = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND;
            if (kind == null) {
                if (st != null) {
                    STATES.remove(p.getId());
                }
                continue;
            }
            if (st == null) {
                st = new State();
                STATES.put(p.getId(), st);
            }
            boolean started = swinging && (st.lastVanilla < 0 || p.swingTime < st.lastVanilla);
            st.lastVanilla = swinging ? p.swingTime : -1;
            if (started) {
                st.active = true;
                st.elapsed = Math.max(0, p.swingTime);
                st.duration = durationFor(p);
            } else if (st.active) {
                st.elapsed++;
            }
            if (st.active && st.elapsed >= st.duration) {
                st.active = false;
            }
        }
    }

    // 今の振りの進み (0 = 振っていない、0..1)。partial = 描画の補間 (0..1)
    static float progress(LivingEntity entity, float partial) {
        if (legacy) {
            return entity.swingingArm == InteractionHand.MAIN_HAND ? entity.getAttackAnim(partial) : 0f;
        }
        State st = STATES.get(entity.getId());
        if (st == null || !st.active) {
            return 0f;
        }
        return Mth.clamp((st.elapsed + partial) / st.duration, 0f, 1f);
    }

    // 描画中の補間 (腕の構えの変換は補間を受け取らないので、ゲーム全体のものを使う)
    static float partial() {
        return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STATES.clear();
    }
}
