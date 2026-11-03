package jp.revati.skyabyss.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

// 近接の一撃の決まり (2026-11-04 の声「敵の無敵時間が短すぎて、敵すぐ死んでしまう。連打が最強すぎる」
// 「プレイヤーが同じ敵を殴れるクールタイムがあった方がいい」「チャージ0の時は攻撃力0でもいいかも」「HP回復の武器はフルチャージじゃないと、発動しないように」)。
// 敵の無敵の間は MobHitFrames のまま (ほぼ無い。仲間の一撃は全部通る)。その代わり、人ごとに:
//   - 同じ敵を殴れる間: 当てたあと、その人はその敵を「振りの長さ × COOLDOWN_SHARE」の間だけ殴れない (空振り。ほかの敵・仲間の一撃は通る)
//   - 溜めの強さ: 傷 = 満ちたときの傷 × 溜め² (バニラは 0.2 + 0.8 × 溜め² で、溜め 0 でも 2 割が入る。その 2 割を無くす)
//   - 回復: 溜めが満ちていない (FULL 以下の) 一撃の間に起こる人の回復 (吸血・当てて癒す・再生の効き目 など) は起こらない
//   - 武器の効き目: 満ちていない一撃では、武器・装飾・技の木の「当てたとき」の効き目 (上乗せ・追加効果・数え・溜まり・マナ吸い) も起こらない (weakHit)
// 近接の一撃 = Player.attack。預かりの武器 (weapon/WeaponStrikes) は当たる tick に attack を呼び、それ以外の手 (素手・弓や盾で殴る) はここで包んで呼び直す。
// 技・矢・品の効き目が直接与える傷 (hurt) は対象外 (その一撃の中で起きたものだけ、溜めの強さを掛ける)。
// 偽のプレイヤー (FakePlayer など) は対象にしない (自己テストは testAllowFake)
// 殴れる間に当てて空振りしたら、その人へ Miss を送る (クライアントの溜めの輪 client/weapon/ChargeRing が「弾かれた」を見せる)
// 溜めの手がかり: 溜め WEAK 未満の一撃を WEAK_HITS 回振った人に、戦いが落ち着いてから (近接が CALM_MELEE tick なく、傷を CALM_HURT tick 受けていない。
// 手がかりの字幕は戦いの最中は出ないため) 一度、手がかりの字幕。一人に TIP_MAX 回まで (TIPS_KEY)。満ちた一撃を LEARNED 回当てた人には出さない (FULL_KEY)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MeleeHits {
    // 同じ敵を殴れる間 = 振りの長さ (攻撃速度から。20 / 攻撃速度 tick) のこの割合。最短 MIN_COOLDOWN tick
    public static final double COOLDOWN_SHARE = 0.6;
    public static final int MIN_COOLDOWN = 3;
    // 「満ちた」溜め (バニラが薙ぎ払い・会心に使う境と同じ)
    public static final float FULL = 0.9f;

    static final float WEAK = 0.5f;
    static final int WEAK_HITS = 4;
    static final int TIP_MAX = 2;
    static final int LEARNED = 20;
    static final int CALM_MELEE = 80;
    static final int CALM_HURT = 210;
    public static final String TIPS_KEY = "skyabyss_charge_tips";
    public static final String FULL_KEY = "skyabyss_full_hits";

    // 人 → (敵の id → 次に殴れる tick)
    private static final Map<UUID, Map<Integer, Long>> NEXT = PlayerMaps.track(new HashMap<>());
    // 人 → {弱い一撃の数, 最後に振った tick, 手がかりを待っているか (1)}
    private static final Map<UUID, long[]> COACH = PlayerMaps.track(new HashMap<>());
    // 今解いている一撃 (attack の中だけ。入れ子は前の値へ戻す)
    private static ServerPlayer swinging;
    private static float swingCharge = 1f;

    static boolean testAllowFake;

    // サーバー -> クライアント: 同じ敵を殴れる間の空振り (中身なし)
    public record Miss() implements net.minecraft.network.protocol.common.custom.CustomPacketPayload {
        public static final Type<Miss> TYPE = new Type<>(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "melee_miss"));
        public static final net.minecraft.network.codec.StreamCodec<net.minecraft.network.RegistryFriendlyByteBuf, Miss> STREAM_CODEC =
                net.minecraft.network.codec.StreamCodec.unit(new Miss());

        @Override
        public Type<? extends net.minecraft.network.protocol.common.custom.CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MeleeHits() {
    }

    static boolean applies(Player player) {
        boolean real = player.getClass() == ServerPlayer.class;
        boolean fake = testAllowFake && player instanceof net.neoforged.neoforge.common.util.FakePlayer;
        return (real || fake) && ((ServerPlayer) player).connection != null;
    }

    // 溜め² の掛け目 (バニラの 0.2 + 0.8 × 溜め² を、溜め² に置き換える)
    public static float chargeScale(float charge) {
        float c = Math.max(0f, Math.min(1f, charge));
        return c * c / (0.2f + 0.8f * c * c);
    }

    // 同じ敵を殴れる間の長さ (tick)
    public static int cooldown(Player p) {
        return Math.max(MIN_COOLDOWN, (int) Math.round(p.getCurrentItemAttackStrengthDelay() * COOLDOWN_SHARE));
    }

    // その人がその敵をまだ殴れないか
    public static boolean cooling(Player p, Entity target) {
        Map<Integer, Long> m = NEXT.get(p.getUUID());
        Long until = m == null ? null : m.get(target.getId());
        return until != null && p.level().getGameTime() < until;
    }

    // 近接の一撃を解く。同じ敵を殴れる間なら空振り (溜めの数えは戻す)。当たらなかったら false
    public static boolean attack(ServerPlayer p, Entity target) {
        if (!applies(p)) {
            p.attack(target);
            return true;
        }
        long now = p.level().getGameTime();
        if (cooling(p, target)) {
            p.resetAttackStrengthTicker();
            if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new Miss());
            }
            return false;
        }
        ServerPlayer outer = swinging;
        float outerCharge = swingCharge;
        swinging = p;
        swingCharge = p.getAttackStrengthScale(0.5f);
        coach(p, swingCharge, now);
        try {
            p.attack(target);
        } finally {
            swinging = outer;
            swingCharge = outerCharge;
        }
        Map<Integer, Long> m = NEXT.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        if (m.size() > 32) {
            m.values().removeIf(t -> t <= now);
        }
        m.put(target.getId(), now + cooldown(p));
        return true;
    }

    // 溜めの手がかりの数え
    static void coach(ServerPlayer p, float charge, long now) {
        var pd = p.getPersistentData();
        if (charge > FULL) {
            if (pd.getInt(FULL_KEY) < LEARNED) {
                pd.putInt(FULL_KEY, pd.getInt(FULL_KEY) + 1);
            }
            return;
        }
        if (charge >= WEAK || pd.getInt(TIPS_KEY) >= TIP_MAX || pd.getInt(FULL_KEY) >= LEARNED) {
            return;
        }
        long[] c = COACH.computeIfAbsent(p.getUUID(), k -> new long[3]);
        c[0]++;
        c[1] = now;
        if (c[0] >= WEAK_HITS) {
            c[2] = 1;
        }
    }

    @SubscribeEvent
    static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (COACH.isEmpty() || event.getServer().getTickCount() % 20 != 11) {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            if (!tipDue(p, now)) {
                continue;
            }
            COACH.remove(p.getUUID());
            p.getPersistentData().putInt(TIPS_KEY, p.getPersistentData().getInt(TIPS_KEY) + 1);
            jp.revati.skyabyss.notice.Notice.hint(p, "message.skyabyss.melee.charge_tip");
        }
    }

    // 手がかりを出す時か (弱い一撃が溜まっていて、戦いが落ち着いた)
    static boolean tipDue(ServerPlayer p, long now) {
        long[] c = COACH.get(p.getUUID());
        return c != null && c[2] != 0 && now - c[1] >= CALM_MELEE
                && (p.getLastHurtByMobTimestamp() <= 0 || p.tickCount - p.getLastHurtByMobTimestamp() >= CALM_HURT);
    }

    // 預かりのない近接 (素手・弓や盾で殴る など) を包んで呼び直す。預かりの武器のクリックは WeaponStrikes に任せる (当たる tick に attack が来る)
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p == swinging || !applies(p) || jp.revati.skyabyss.weapon.WeaponStrikes.holds(p)) {
            return;
        }
        event.setCanceled(true);
        attack(p, event.getTarget());
    }

    // 一撃の中で起きた、その人の傷に溜めの強さを掛ける (ほかの決まりの増減のあと)
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (swinging != null && event.getSource().getEntity() == swinging && swingCharge < 1f) {
            event.setAmount(event.getAmount() * chargeScale(swingCharge));
        }
    }

    // 満ちていない一撃の中で起きる人の回復は起こらない (じかの回復も、再生の効き目を付けるのも)
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onHeal(LivingHealEvent event) {
        if (swingingWeak() && event.getEntity() instanceof Player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onEffect(MobEffectEvent.Applicable event) {
        if (swingingWeak() && event.getEntity() instanceof Player && event.getEffectInstance().getEffect().is(MobEffects.REGENERATION)) {
            event.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    // 自己テスト用
    static void forget(Player p) {
        NEXT.remove(p.getUUID());
        COACH.remove(p.getUUID());
    }

    static void during(ServerPlayer p, float charge, Runnable r) {
        ServerPlayer outer = swinging;
        float outerCharge = swingCharge;
        swinging = p;
        swingCharge = charge;
        try {
            r.run();
        } finally {
            swinging = outer;
            swingCharge = outerCharge;
        }
    }

    public static boolean swingingWeak() {
        return swinging != null && swingCharge <= FULL;
    }

    // この人の、満ちていない近接の一撃の最中か。武器の効き目 (上乗せ・追加効果・数え・溜まり・吸い) は起こさない
    // (item/GearLevel.proc と各係の当てたときの口が見る。技・矢・時間ごとの効き目は対象外)
    public static boolean weakHit(net.minecraft.world.entity.Entity who) {
        return swinging != null && swinging == who && swingCharge <= FULL;
    }
}
