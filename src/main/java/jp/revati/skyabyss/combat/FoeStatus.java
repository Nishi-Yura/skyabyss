package jp.revati.skyabyss.combat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.PlayerMaps;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

// 敵にいまかかっている状態 (燃焼・鈍足・毒・出血・崩れ・剛力 など) を、近くの人へ知らせる (2026-11-12 の声
// 「敵が燃えたり弱体化喰らったり等々デバフやバフがかかっている時、こちらからしてもかかっているかわかりにくい」)。
// バニラは人でない体の状態効果をクライアントへ送らないので、ここで送る。見せ方は client/hud/FoeStatusClient:
// 頭の上の体力の帯の上にアイコンを並べ、ボスの帯のある主は帯の名前の右に並べる。
//   - EVERY tick ごとに、人ごとに RANGE マス内の敵 (Enemy) を見て、かかっている種類と残りの tick を一つの知らせで送る
//     (前に送ったものと種類が同じで、残りの tick も大きく変わらなければ送らない。RESEND tick ごとには送り直す)
//   - 永続 (尽きない) の効果は数えない (ボスの生まれつきの守りなど。いつも出ていると読めない)
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class FoeStatus {
    static final int EVERY = 4;
    static final int RESEND = 40;
    static final double RANGE = 64;
    // 一体にのせる種類の数の上限 (知らせの大きさの上限)
    static final int MAX_KINDS = 8;

    // 種類。番号は textures/gui/foe_status.png の並び (tools/gen/ui/gen_foe_status_icons.py) と同じ。足すときは末尾に
    public enum Kind {
        BURN(false), SLOW(false), WEAK(false), POISON(false), BLEED(false), BLIND(false), LIFT(false), BROKEN(false),
        STRONG(true), GUARD(true), HASTE(true), REGEN(true), SHIELD(true);

        public final boolean buff;

        Kind(boolean buff) {
            this.buff = buff;
        }

        public static final Kind[] ALL = values();
    }

    // 一体の状態: 種類の番号と残りの tick (同じ並び。-1 は終わりの分からないもの)
    public record Entry(int entityId, byte[] kinds, int[] ticks) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Entry::entityId, ByteBufCodecs.BYTE_ARRAY, Entry::kinds,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX_KINDS)).map(l -> l.stream().mapToInt(Integer::intValue).toArray(),
                        a -> java.util.Arrays.stream(a).boxed().toList()),
                Entry::ticks, Entry::new);
    }

    // 人の近くの全部 (前の知らせにあって今ないものは、クライアントが消す)
    public record Payload(List<Entry> entries) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "foe_status"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list(512)), Payload::entries, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 人ごとの、前に送った中身の目印 (種類の並びと、残りの tick を 1 秒単位に丸めたもの)
    private static final Map<UUID, String> LAST = PlayerMaps.track(new HashMap<>());
    private static final Map<UUID, Long> LAST_AT = PlayerMaps.track(new HashMap<>());

    private FoeStatus() {
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        long tick = event.getServer().getTickCount();
        if (tick % EVERY != 0) {
            return;
        }
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            send(p, tick);
        }
    }

    static void send(ServerPlayer p, long tick) {
        List<Entry> list = new ArrayList<>();
        StringBuilder sig = new StringBuilder();
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(RANGE),
                e -> e instanceof Enemy && !(e instanceof Player) && e.isAlive())) {
            Entry en = of(e);
            if (en != null) {
                list.add(en);
                sig.append(en.entityId()).append(':');
                for (int i = 0; i < en.kinds().length; i++) {
                    sig.append(en.kinds()[i]).append('/').append(en.ticks()[i] < 0 ? -1 : en.ticks()[i] / 20).append(',');
                }
                sig.append(';');
            }
        }
        String s = sig.toString();
        Long at = LAST_AT.get(p.getUUID());
        boolean stale = at == null || tick - at >= RESEND;
        if (s.equals(LAST.get(p.getUUID())) && !stale) {
            return;
        }
        // 何もなく、前も何もなかったなら送らない
        if (list.isEmpty() && "".equals(LAST.get(p.getUUID()))) {
            return;
        }
        LAST.put(p.getUUID(), s);
        LAST_AT.put(p.getUUID(), tick);
        PacketDistributor.sendToPlayer(p, new Payload(list));
    }

    // 一体の状態 (何もなければ null)
    static Entry of(LivingEntity e) {
        int[] best = new int[Kind.ALL.length];
        java.util.Arrays.fill(best, Integer.MIN_VALUE);
        if (e.getRemainingFireTicks() > 0 && !e.fireImmune()) {
            best[Kind.BURN.ordinal()] = e.getRemainingFireTicks();
        }
        if (BreakGauge.broken(e)) {
            best[Kind.BROKEN.ordinal()] = -1;
        }
        for (MobEffectInstance in : e.getActiveEffects()) {
            if (in.isInfiniteDuration()) {
                continue;
            }
            Kind k = kindOf(in.getEffect());
            if (k == null) {
                continue;
            }
            int t = in.getDuration();
            if (t > best[k.ordinal()]) {
                best[k.ordinal()] = t;
            }
        }
        int n = 0;
        for (int v : best) {
            if (v != Integer.MIN_VALUE) {
                n++;
            }
        }
        if (n == 0) {
            return null;
        }
        n = Math.min(n, MAX_KINDS);
        byte[] kinds = new byte[n];
        int[] ticks = new int[n];
        int j = 0;
        // 弱体を先に、強化を後に (並びは種類の番号の順)
        for (int i = 0; i < best.length && j < n; i++) {
            if (best[i] != Integer.MIN_VALUE) {
                kinds[j] = (byte) i;
                ticks[j] = best[i];
                j++;
            }
        }
        return new Entry(e.getId(), kinds, ticks);
    }

    static boolean is(Holder<MobEffect> h, Holder<MobEffect> want) {
        return h.value() == want.value();
    }

    // 効果 → 種類 (見せないものは null)
    static Kind kindOf(Holder<MobEffect> h) {
        if (is(h, MobEffects.MOVEMENT_SLOWDOWN) || is(h, MobEffects.DIG_SLOWDOWN)) {
            return Kind.SLOW;
        }
        if (is(h, MobEffects.POISON) || is(h, MobEffects.WITHER)) {
            return Kind.POISON;
        }
        if (is(h, jp.revati.skyabyss.registry.ModEffects.BLEEDING)) {
            return Kind.BLEED;
        }
        if (is(h, MobEffects.BLINDNESS) || is(h, MobEffects.CONFUSION)
                || is(h, MobEffects.DARKNESS)) {
            return Kind.BLIND;
        }
        if (is(h, MobEffects.LEVITATION)) {
            return Kind.LIFT;
        }
        if (is(h, MobEffects.DAMAGE_BOOST)) {
            return Kind.STRONG;
        }
        if (is(h, MobEffects.DAMAGE_RESISTANCE)) {
            return Kind.GUARD;
        }
        if (is(h, MobEffects.MOVEMENT_SPEED) || is(h, MobEffects.DIG_SPEED)) {
            return Kind.HASTE;
        }
        if (is(h, MobEffects.REGENERATION)) {
            return Kind.REGEN;
        }
        if (is(h, MobEffects.ABSORPTION)) {
            return Kind.SHIELD;
        }
        // 発光・透明・火への守りなど、見た目や生まれつきのものは出さない
        if (is(h, MobEffects.GLOWING) || is(h, MobEffects.INVISIBILITY)
                || is(h, MobEffects.FIRE_RESISTANCE) || is(h, MobEffects.NIGHT_VISION)
                || is(h, MobEffects.WATER_BREATHING)) {
            return null;
        }
        // そのほかの害 (弱さ・飢え・死の弱りなど) は「弱体」にまとめる
        return h.value().getCategory() == MobEffectCategory.HARMFUL ? Kind.WEAK : null;
    }
}
