package jp.revati.skyabyss.client.weapon;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.weapon.ComboTracker;
import jp.revati.skyabyss.weapon.MoveSet;
import jp.revati.skyabyss.weapon.MoveSets;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

// mod 武器の「動きの時計」(クライアントだけ)。
// バニラの振り (attackAnim) は、武器の重さに関係なく 6 tick (約 0.3 秒) で終わる。振りが始まったのを (バニラの swing の合図で) 見つけたら、
// 武器の攻撃速度から決めた長さ (MoveSet#durationFor) の自分の時計を回し、一人称・三人称・体の傾きの動きはその進み (0..1) と「連撃の何段目か」から作る。
//  - 連撃: 前の振りから (振りの長さ + 猶予) 以内にまた振れば次の段 (ComboTracker。サーバーの WeaponStrikes と同じ数え方)
//  - ヒットストップ: サーバーが「当たった」と知らせたら (StrikePayload)、時計を 1〜3 tick 止める
//  - 点検 (インスペクト): 同じ時計で、かざして回す動き (MoveSet#inspect) を回す
// サーバーへ何も送らず、ほかのプレイヤーの振りも同じバニラの合図 (ClientboundAnimatePacket) から始まるので、同じ長さで見える
@EventBusSubscriber(modid = SkyAbyss.MODID, value = Dist.CLIENT)
public final class SwingClock {
    // 今の動きの一コマ: どの段か、進み t (0..1)、混ぜ具合 w
    public record Active(MoveSet set, MoveSet.Step step, float t, float w, boolean inspect, int stepIndex) {
    }

    private static final class State {
        boolean active;
        int elapsed;
        int duration = MoveSet.MIN_TICKS;
        int step;
        long lastStart = Long.MIN_VALUE / 2;
        int lastDuration = MoveSet.MIN_TICKS;
        int freeze;
        String item = "";
        // 点検 (-1 = していない)
        int inspect = -1;
        // 前の tick のバニラの swingTime (振っていなければ -1)。小さくなったら振り直し
        int lastVanilla = -1;
    }

    // 開発用: true にすると昔の動き (バニラの 6 tick の進みをそのまま使う) に戻す。台本 swingclock off。変更前後の比べ用
    public static volatile boolean legacy = false;
    // 開発用: 0 以上にすると、止めた絵 (swingpose) でその段を使う。台本 swingstep
    public static volatile int forcedStep = 0;
    // 開発用: 0..1 にすると、点検の動きをその進みで止める (台本 inspectpose)。負 = 使わない
    public static volatile float forcedInspect = -1f;

    private static final Int2ObjectOpenHashMap<State> STATES = new Int2ObjectOpenHashMap<>();

    private SwingClock() {
    }

    // 振る・点検する武器か (弓は振らない)。盾も含む (殴りつける)
    static MoveSet setOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!SkyAbyss.MODID.equals(id.getNamespace())) {
            return null;
        }
        MoveSet ms = MoveSets.get(id.getPath());
        return ms == null || "BOW".equals(ms.family) ? null : ms;
    }

    static MoveSet setOf(LivingEntity entity) {
        return setOf(entity.getMainHandItem());
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            STATES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            State st = STATES.get(p.getId());
            MoveSet ms = setOf(p);
            boolean swinging = p.swinging && p.swingingArm == InteractionHand.MAIN_HAND;
            if (ms == null) {
                if (st != null) {
                    STATES.remove(p.getId());
                }
                continue;
            }
            if (st == null) {
                st = new State();
                STATES.put(p.getId(), st);
            }
            boolean sameItem = ms.item.equals(st.item);
            if (!sameItem) {
                st.item = ms.item;
                st.step = -1;
                st.active = false;
                st.inspect = -1;
                st.freeze = 0;
            }
            boolean started = swinging && (st.lastVanilla < 0 || p.swingTime < st.lastVanilla);
            st.lastVanilla = swinging ? p.swingTime : -1;
            if (started) {
                int duration = ms.durationFor(p.getAttributeValue(Attributes.ATTACK_SPEED));
                st.step = ComboTracker.next(st.step, (int) Math.min(Integer.MAX_VALUE, now - st.lastStart), st.lastDuration, ms.window, ms.steps.size());
                st.lastStart = now;
                st.lastDuration = duration;
                st.active = true;
                st.elapsed = Math.max(0, p.swingTime);
                st.duration = duration;
                st.freeze = 0;
                st.inspect = -1;
            } else if (st.active) {
                if (st.freeze > 0) {
                    st.freeze--;
                } else {
                    st.elapsed++;
                }
            }
            if (st.active && st.elapsed >= st.duration) {
                st.active = false;
            }
            if (st.inspect >= 0) {
                if (++st.inspect >= MoveSet.INSPECT_TICKS) {
                    st.inspect = -1;
                } else if (p.isUsingItem()) {
                    st.inspect = -1;
                }
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
        float p = st.freeze > 0 ? 0f : partial;
        return Mth.clamp((st.elapsed + p) / st.duration, 0f, 1f);
    }

    // 今の動き (振り or 点検)。なければ null。開発用の止めた絵 (forcedSwing / forcedInspect) は自分だけ
    public static Active current(LivingEntity entity, float partial) {
        MoveSet ms = setOf(entity);
        if (ms == null) {
            return null;
        }
        boolean self = entity == Minecraft.getInstance().player;
        if (self && WeaponClient.forcedSwing >= 0f) {
            return make(ms, ms.step(forcedStep), WeaponClient.forcedSwing, false, Math.floorMod(forcedStep, ms.steps.size()));
        }
        if (self && forcedInspect >= 0f && ms.inspect != null) {
            return make(ms, ms.inspect, forcedInspect, true, 0);
        }
        State st = STATES.get(entity.getId());
        if (st == null) {
            return null;
        }
        if (st.active || legacy) {
            float t = progress(entity, partial);
            if (t > 0f) {
                return make(ms, ms.step(legacy ? 0 : st.step), t, false, legacy ? 0 : Math.floorMod(st.step, ms.steps.size()));
            }
        }
        if (st.inspect >= 0 && ms.inspect != null) {
            return make(ms, ms.inspect, Mth.clamp((st.inspect + partial) / MoveSet.INSPECT_TICKS, 0f, 1f), true, 0);
        }
        return null;
    }

    private static Active make(MoveSet ms, MoveSet.Step step, float t, boolean inspect, int index) {
        return new Active(ms, step, t, step.weight(t), inspect, index);
    }

    // ヒットストップ: 振りの時計を ticks だけ止める (StrikePayload)
    static void freeze(int entityId, int ticks) {
        State st = STATES.get(entityId);
        if (st != null && st.active) {
            st.freeze = Math.max(st.freeze, Math.min(ticks, MoveSet.MAX_HITSTOP));
        }
    }

    static boolean frozen(LivingEntity entity) {
        State st = STATES.get(entity.getId());
        return st != null && st.active && st.freeze > 0;
    }

    static void startInspect(int entityId) {
        State st = STATES.get(entityId);
        if (st != null && !st.active) {
            st.inspect = 0;
        }
    }

    static boolean inspecting(int entityId) {
        State st = STATES.get(entityId);
        return st != null && st.inspect >= 0;
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
