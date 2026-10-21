package jp.revati.skyabyss.titan.ember.back;

import jp.revati.skyabyss.notice.Notice;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.titan.ember.EmberIds;
import jp.revati.skyabyss.titan.ember.EmberNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 余燼の背の「熱」(docs/design/13_ember.md の 3.4節)。一人ずつに 0〜100。背中にいるあいだだけ動き、背中を出る・死ぬと 0。保存しない。
//
// **熱の約束 (11.2節)**: 熱を動かすのは add(player, 量, 理由) と cool(player, 量) だけ (テストとコマンドは set)。
// 試練 (C1〜C3)・敵 (D)・鍛え手 (E)・心臓 (F) はこの API だけを呼ぶ。たまり方の倍率 (火炎耐性 0.5、角杯 0.6、鎧 0.7)、
// ふだんの冷え (1 秒に −3)、溶岩のそば・継ぎ目・灰溜まり・水、焦げの傷、火照りの光、85 の警告は B がこのファイルに書く (gainMultiplier と tickPlayer)。
// 骨組み (担当 0) の今の中身: 値の保持・範囲・帯・背中の外で 0・死んだら 0・目盛りへの同期 (EmberNetwork.Heat)。
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class EmberHeat {
    public static final float MAX = 100f;
    // 帯の下端 (3.4節): 0〜49 ふつう、50〜84 火照り、85〜99 焦げかけ、100 焦げ
    public static final float FLUSHED = 50f;
    public static final float SCORCHING = 85f;

    // 熱が動いた理由 (ログ・自己テスト・倍率の分け方に使う。B が倍率をかけるかを理由ごとに決める)
    public enum Reason {
        LAVA_NEAR,      // 溶岩から 2 ブロック以内 (1 秒に +3)
        HOT_GROUND,     // マグマの継ぎ目・冷え殻の上 (1 秒に +1)
        VENT,           // 噴気の割れ目 (+25)
        MOTH,           // 火の粉蛾に触れた (+8)、蛾が散った火の粉 (+4)
        ROLLER_TRAIL,   // 燠虫の火の粉の帯 (+5)
        ENGULFED,       // 呑まれた (+30、心臓の区画 +40)
        FIRE_KEY,       // 火の鍵を受け取った (+20)
        LIVE_COAL,      // 熾を持っている (1 秒に +1)
        SMITH,          // 鍛え手の技
        HEART,          // 心臓の技 (残り火を含む)
        OTHER
    }

    public enum Band {
        NORMAL, FLUSHED, SCORCHING, SCORCHED;

        public static Band of(float heat) {
            return heat >= MAX ? SCORCHED : heat >= EmberHeat.SCORCHING ? Band.SCORCHING : heat >= EmberHeat.FLUSHED ? Band.FLUSHED : NORMAL;
        }
    }

    private static final Map<UUID, Float> HEAT = new HashMap<>();
    // 最後に送った値 (変わったときだけ送る)
    private static final Map<UUID, Float> SENT = new HashMap<>();

    private EmberHeat() {
    }

    // ---------------------------------------------------------------- API
    // 今の熱 (サーバー側。背中の外・クライアント側は 0)
    public static float get(Player player) {
        return player instanceof ServerPlayer ? HEAT.getOrDefault(player.getUUID(), 0f) : 0f;
    }

    public static Band band(Player player) {
        return Band.of(get(player));
    }

    // 火照り (50 以上。灰の殻への傷 2 倍、鎧の攻撃 +10%)
    public static boolean flushed(Player player) {
        return get(player) >= FLUSHED;
    }

    // 熱をためる。量にはたまり方の倍率 (gainMultiplier) がかかる。背中の外・観戦者・クリエイティブでは何もしない。
    // 負の量は cool と同じ。新しい熱を返す
    public static float add(Player player, float amount, Reason reason) {
        if (!(player instanceof ServerPlayer sp) || !affects(sp)) {
            return get(player);
        }
        if (amount <= 0) {
            return cool(sp, -amount);
        }
        return put(sp, get(sp) + amount * gainMultiplier(sp, reason));
    }

    // 熱を冷ます (倍率はかからない)。新しい熱を返す
    public static float cool(Player player, float amount) {
        if (!(player instanceof ServerPlayer sp) || amount <= 0) {
            return get(player);
        }
        return put(sp, get(sp) - amount);
    }

    // そのまま決める (コマンド emberback heat・自己テスト)。背中の外でも入るが、次の tick で 0 に戻る
    public static void set(Player player, float value) {
        if (player instanceof ServerPlayer sp) {
            put(sp, value);
        }
    }

    // 0 に戻す (背中を出た・死んだ・リセットで試練から広間へ移した)
    public static void reset(Player player) {
        SCORCHED.remove(player.getUUID());
        WARNED.remove(player.getUUID());
        if (player instanceof ServerPlayer sp && HEAT.remove(sp.getUUID()) != null) {
            sync(sp, 0f);
        }
    }

    // 熱が効く人か (余燼の背にいる、観戦者でもクリエイティブでもない)
    public static boolean affects(ServerPlayer player) {
        return player.level().dimension() == EmberIds.BACK && !player.isSpectator() && !player.isCreative();
    }

    // ---------------------------------------------------------------- B が書く中身 (3.4節の表)
    // 1 秒あたりの量
    public static final float NEAR_LAVA = 3f;
    public static final float HOT_GROUND = 1f;
    public static final float PASSIVE_COOL = 3f;
    public static final float ASH_COOL = 15f;
    public static final float WATER_COOL = 25f;
    // 焦げの傷 (1 秒に 2。85 を切るまで)
    public static final float SCORCH_DAMAGE = 2f;
    // 倍率の下限 (3.4節「準備のごほうび」)
    public static final float MIN_MULTIPLIER = 0.4f;

    // 倍率を足す口 (G の品)。exclusive: 火炎耐性 (0.5) と同じ組で、いちばん小さいものだけ効く (熾火の角杯 0.6)。
    // stacking: 掛け合わせる (余燼の鎧 4 部位 0.7、熾火の外套 0.75)。返す値は 1 (効かない) か、それより小さい数
    private static final java.util.List<java.util.function.ToDoubleFunction<ServerPlayer>> EXCLUSIVE = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static final java.util.List<java.util.function.ToDoubleFunction<ServerPlayer>> STACKING = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addModifier(boolean stacking, java.util.function.ToDoubleFunction<ServerPlayer> modifier) {
        (stacking ? STACKING : EXCLUSIVE).add(modifier);
    }

    // たまり方の倍率。火炎耐性 0.5 と角杯 0.6 は強いほうだけ、鎧 0.7・外套 0.75 は重ねる。下限 0.4
    static float gainMultiplier(ServerPlayer player, Reason reason) {
        double ex = player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE) ? 0.5 : 1.0;
        for (var m : EXCLUSIVE) {
            ex = Math.min(ex, m.applyAsDouble(player));
        }
        double st = 1.0;
        for (var m : STACKING) {
            st *= m.applyAsDouble(player);
        }
        return (float) Math.max(MIN_MULTIPLIER, ex * st);
    }

    // 焦げの最中か (100 になってから 85 を切るまで)
    private static final java.util.Set<UUID> SCORCHED = new java.util.HashSet<>();
    // 85 の警告を出したか (85 を切ったら消す)
    private static final java.util.Set<UUID> WARNED = new java.util.HashSet<>();

    // いまいる所の熱の様子 (自己テスト・コマンドが見る)
    public enum Spot {
        NEAR_LAVA, HOT_GROUND, ASH, WATER, REST, NONE
    }

    public static Spot spot(ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos feet = player.blockPosition();
        if (player.isInWater()) {
            return Spot.WATER;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ());
        net.minecraft.world.level.block.state.BlockState under = level.getBlockState(below);
        if (under.is(net.minecraft.world.level.block.Blocks.GRAY_WOOL)) {
            return Spot.ASH;
        }
        // 休み場の焚き火のそば (熱は動かない)
        for (EmberBackLayout.Rest r : EmberBackLayout.RESTS) {
            if (r.area().grow(1).contains(feet)) {
                return Spot.REST;
            }
        }
        // 溶岩から 2 ブロック以内
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    m.set(feet.getX() + dx, feet.getY() + dy, feet.getZ() + dz);
                    if (level.getFluidState(m).is(net.minecraft.tags.FluidTags.LAVA)) {
                        return Spot.NEAR_LAVA;
                    }
                }
            }
        }
        if (under.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || EmberIds.id(EmberIds.COOLED_CRUST).equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(under.getBlock()))) {
            return Spot.HOT_GROUND;
        }
        return Spot.NONE;
    }

    // 1 tick ごと (余燼の背にいる人だけ)。10 tick (0.5 秒) ごとに、いる所で熱を動かす。焦げの傷・火照りの光・85 の警告
    static void tickPlayer(ServerPlayer player) {
        if (player.tickCount % 10 != 0) {
            return;
        }
        float dt = 0.5f;
        switch (spot(player)) {
            case NEAR_LAVA -> add(player, NEAR_LAVA * dt, Reason.LAVA_NEAR);
            case HOT_GROUND -> add(player, HOT_GROUND * dt, Reason.HOT_GROUND);
            case ASH -> cool(player, ASH_COOL * dt);
            case WATER -> cool(player, WATER_COOL * dt);
            case REST -> {
            }
            case NONE -> cool(player, PASSIVE_COOL * dt);
        }
        float h = get(player);
        UUID id = player.getUUID();
        // 85 の警告 (一度。画面の縁の揺れはクライアントの HeatHud)
        if (h >= SCORCHING) {
            if (WARNED.add(id)) {
                player.playNotifySound(jp.revati.skyabyss.titan.ember.EmberSetup.HEAT_WARN.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                Notice.bar(player, jp.revati.skyabyss.ModText.deny(EmberIds.LANG_HEAT + "scorching"));
            }
        } else {
            WARNED.remove(id);
        }
        // 焦げ: 100 になったら、85 を切るまで 1 秒に 2 (ピースフルでは傷を出さない)
        if (h >= MAX) {
            SCORCHED.add(id);
        } else if (h < SCORCHING) {
            SCORCHED.remove(id);
        }
        if (SCORCHED.contains(id) && player.tickCount % 20 == 0
                && player.level().getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL) {
            var types = player.serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE);
            player.hurt(new net.minecraft.world.damagesource.DamageSource(types.getHolderOrThrow(EmberIds.HEAT)), SCORCH_DAMAGE);
            player.playNotifySound(jp.revati.skyabyss.titan.ember.EmberSetup.SCORCH.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.7f, 1f);
        }
        // 火照りの光 (ほかの人から見える粒)
        if (h >= FLUSHED) {
            int n = h >= SCORCHING ? 4 : 2;
            player.serverLevel().sendParticles(h >= SCORCHING ? net.minecraft.core.particles.ParticleTypes.FLAME
                    : net.minecraft.core.particles.ParticleTypes.SMALL_FLAME, player.getX(), player.getY() + 1.0, player.getZ(), n, 0.3, 0.5, 0.3, 0.005);
        }
    }

    public static boolean scorched(Player player) {
        return SCORCHED.contains(player.getUUID());
    }

    // マグマの継ぎ目の「熱い床」の傷は、余燼の背では熱に置きかえる (継ぎ目の上は熱が 1 秒に +1)
    @SubscribeEvent
    static void onIncomingDamage(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event) {
        if (event.getEntity().level().dimension() == EmberIds.BACK
                && event.getSource().is(net.minecraft.world.damagesource.DamageTypes.HOT_FLOOR)) {
            event.setCanceled(true);
        }
    }

    // ---------------------------------------------------------------- 中身 (骨組み)
    private static float put(ServerPlayer player, float value) {
        float v = Mth.clamp(value, 0f, MAX);
        if (v <= 0f) {
            HEAT.remove(player.getUUID());
        } else {
            HEAT.put(player.getUUID(), v);
        }
        return v;
    }

    private static void sync(ServerPlayer player, float value) {
        Float last = SENT.get(player.getUUID());
        if (last != null && Math.abs(last - value) < 0.05f) {
            return;
        }
        SENT.put(player.getUUID(), value);
        PacketDistributor.sendToPlayer(player, new EmberNetwork.Heat(value));
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().dimension() != EmberIds.BACK) {
            // 背中を出たら 0 (ほかのディメンションへ移った人)
            if (HEAT.containsKey(player.getUUID()) || SENT.containsKey(player.getUUID())) {
                HEAT.remove(player.getUUID());
                sync(player, 0f);
                SENT.remove(player.getUUID());
            }
            return;
        }
        if (affects(player)) {
            tickPlayer(player);
        } else if (HEAT.containsKey(player.getUUID())) {
            // 観戦者・クリエイティブには熱を持たせない
            HEAT.remove(player.getUUID());
        }
        // 目盛りへ: 変わったら 5 tick ごと、変わらなくても 2 秒に一度 (着いたばかりの人の目盛りを出すため)
        if (player.tickCount % 5 == 0) {
            if (player.tickCount % 40 == 0) {
                SENT.remove(player.getUUID());
            }
            sync(player, get(player));
        }
    }

    @SubscribeEvent
    static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            reset(player);
        }
    }

    @SubscribeEvent
    static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HEAT.remove(player.getUUID());
            SENT.remove(player.getUUID());
            sync(player, 0f);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HEAT.remove(event.getEntity().getUUID());
        SENT.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        SCORCHED.clear();
        WARNED.clear();
        HEAT.clear();
        SENT.clear();
    }
}
