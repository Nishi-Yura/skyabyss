package jp.revati.skyabyss.story.ch5;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 深淵で潜るときの圧 (docs/design/36 の 1・A4)。深淵 (AbyssWorld.LEVEL) にいる人だけ。
//   上昇の重み  その人の「底」(lowY) より上にいるほど体が重い: 重さ = clamp((y - lowY - 6) / 40, 0, 1) × (1 - 重さの術の軽さ)。
//               足の速さ -45% × 重さ、跳ぶ力 -50% × 重さ。底は下りると下がり、上では 1 秒に 0.4 ずつ追いつく (休めば軽くなる)。
//               休み場 (印 rest_<n>) の 6 以内では底がその場に合う (すぐ軽くなる)
//   闇がたまる  淵の声 0〜100。足もとの明るさ (+ 光の術の明るさ) が 3 以下で +1.5/秒、4〜7 で +0.3/秒、8 以上で -4/秒。
//               40 から囁き (数秒ごとに魂の音)、70 から暗闇 (バニラの効き目を 3 秒ずつ)、95 で一度だけ一言 (幻の敵はあとで足す)
//   深度の数字  深さ = 縁の上面 (着く所の高さ) - 足の高さ。人ごとの一番深い記録 (持続の印 skyabyss_abyss_max_depth。順位の石はあとで)
//   画面        10 tick ごとに (声・重さ・深さ) を送る (AbyssNetwork → client/AbyssHud)
//   ほかの係から (深淵術。docs/design/36 の 5): weightRelief (0〜1 の軽さ)・lightBonus (光の術の明るさ 0〜15) を差し替える
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class AbyssPressure {
    public static volatile ToDoubleFunction<ServerPlayer> weightRelief = p -> 0.0;
    public static volatile ToIntFunction<ServerPlayer> lightBonus = p -> 0;
    // 闇のたまりの速さの倍 (心臓の間で灯籠が全部消えていると 2。warden.WardenArena が入れる)
    public static volatile ToDoubleFunction<ServerPlayer> voiceScale = p -> 1.0;

    static final double WEIGHT_FREE = 6.0;
    static final double WEIGHT_SPAN = 40.0;
    static final double LOW_CATCH_UP = 0.4;   // 1 秒に
    static final double SPEED_LOSS = 0.45;
    static final double JUMP_LOSS = 0.5;
    static final float VOICE_DARK = 1.5f;
    static final float VOICE_DIM = 0.3f;
    static final float VOICE_LIGHT = -4f;
    static final String MAX_DEPTH = "skyabyss_abyss_max_depth";
    private static final ResourceLocation SPEED_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_weight_speed");
    private static final ResourceLocation JUMP_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "abyss_weight_jump");

    static final class State {
        double lowY = Double.NaN;
        float voice;
        double weight;
        boolean warned;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private AbyssPressure() {
    }

    public static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // 深さの数字。心臓の下の果てない深み (depths) の中は、心臓の間の底からの数字 (DepthRun.totalDepth)。縦穴の中は縁からの数字
    public static int depth(ServerPlayer p) {
        int deep = jp.revati.skyabyss.story.ch5.depths.DepthRun.totalDepth(p);
        if (deep >= 0) {
            return deep;
        }
        return Math.max(0, AbyssWorld.arrival().getY() - Mth.floor(p.getY()));
    }

    @SubscribeEvent
    static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (!AbyssWorld.in(p) || p.isSpectator()) {
            if (STATES.containsKey(p.getUUID())) {
                clear(p);
            }
            return;
        }
        State s = state(p);
        if (p.tickCount % 20 == 0) {
            second(p, s);
        }
        if (p.tickCount % 10 == 0) {
            PacketDistributor.sendToPlayer(p, new AbyssNetwork.Pressure(Math.round(s.voice), (int) Math.round(s.weight * 100), depth(p)));
        }
    }

    // 1 秒ごと
    static void second(ServerPlayer p, State s) {
        double y = p.getY();
        // ---- 上昇の重み
        if (Double.isNaN(s.lowY) || y < s.lowY) {
            s.lowY = y;
        } else {
            s.lowY = Math.min(y, s.lowY + LOW_CATCH_UP);
        }
        if (nearRest(p)) {
            s.lowY = y;
        }
        double relief = Mth.clamp(weightRelief.applyAsDouble(p), 0.0, 1.0);
        s.weight = Mth.clamp((y - s.lowY - WEIGHT_FREE) / WEIGHT_SPAN, 0.0, 1.0) * (1.0 - relief);
        apply(p, Attributes.MOVEMENT_SPEED, SPEED_ID, -SPEED_LOSS * s.weight);
        apply(p, Attributes.JUMP_STRENGTH, JUMP_ID, -JUMP_LOSS * s.weight);
        // ---- 闇がたまる
        BlockPos at = p.blockPosition();
        int light = Math.max(p.serverLevel().getMaxLocalRawBrightness(at), Mth.clamp(lightBonus.applyAsInt(p), 0, 15));
        float d = light <= 3 ? VOICE_DARK : light <= 7 ? VOICE_DIM : VOICE_LIGHT;
        if (d > 0) {
            d *= (float) (voiceScale.applyAsDouble(p) * AbyssRelics.voiceFactor(p));
        }
        s.voice = Mth.clamp(s.voice + d, 0f, 100f);
        if (s.voice >= 40 && p.getRandom().nextInt(5) == 0) {
            p.serverLevel().playSound(null, p.getX() + p.getRandom().nextGaussian() * 6, p.getY(), p.getZ() + p.getRandom().nextGaussian() * 6,
                    SoundEvents.SOUL_ESCAPE.value(), SoundSource.AMBIENT, 0.6f, 0.5f + p.getRandom().nextFloat() * 0.3f);
        }
        if (s.voice >= 70) {
            p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, true, false, false));
        }
        if (s.voice >= 95 && !s.warned) {
            s.warned = true;
            Notice.narrate(p, "message.skyabyss.abyss.voice_full");
        } else if (s.voice < 50) {
            s.warned = false;
        }
        // ---- 深度の記録
        int dep = depth(p);
        if (dep > p.getPersistentData().getInt(MAX_DEPTH)) {
            p.getPersistentData().putInt(MAX_DEPTH, dep);
        }
    }

    static boolean nearRest(ServerPlayer p) {
        if (jp.revati.skyabyss.story.ch5.depths.DepthRun.nearRest(p)) {
            return true;
        }
        for (int i = 1; i <= 5; i++) {
            BlockPos r = AbyssBuild.mark("rest_" + i);
            if (r != null && p.blockPosition().closerThan(r, 6)) {
                return true;
            }
        }
        BlockPos a = AbyssWorld.arrival();
        return p.blockPosition().closerThan(a, 6);
    }

    static void apply(ServerPlayer p, Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier cur = inst.getModifier(id);
        if (Math.abs(amount) < 0.005) {
            if (cur != null) {
                inst.removeModifier(id);
            }
            return;
        }
        if (cur == null || Math.abs(cur.amount() - amount) > 0.01) {
            inst.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    static void clear(ServerPlayer p) {
        STATES.remove(p.getUUID());
        apply(p, Attributes.MOVEMENT_SPEED, SPEED_ID, 0);
        apply(p, Attributes.JUMP_STRENGTH, JUMP_ID, 0);
        PacketDistributor.sendToPlayer(p, new AbyssNetwork.Pressure(-1, 0, 0));
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onChangeDim(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getFrom() == AbyssWorld.LEVEL) {
            clear(p);
        }
    }
}
