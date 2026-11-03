package jp.revati.skyabyss.trial;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.progress.Titans;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 挑戦の加護 (2026-11-03 の案「挑戦の加護」。Wynncraft のレイドの加護の部屋。docs/design/32 の 9.7)。
// 心臓の順路で、歩・智・闘の試練を越えるたびに (TrialRoute.onCleared)、背の中の一人ずつに二つの加護を見せ、一つを選ばせる。
// 選ばずに CHOOSE_TICKS たつと一つ目。加護は順路が終わる・背を出る・抜けるまで続き、重なる (持っている加護は出さない)
//   力 = 与える傷 +15%、守り = 受ける傷 -15%、命 = 体力の上限 +20%、風 = 歩く速さ +12%、癒し = 3 秒ごとに体力 2% 戻る、吸命 = 与えた傷の 4% 戻る
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class RouteBoons {
    public enum Boon {
        MIGHT, WARD, VIGOR, GALE, MENDING, LEECH;

        public String key() {
            return "boon.skyabyss." + name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    static final int CHOOSE_TICKS = 20 * 20;
    static final double MIGHT = 1.15;
    static final double WARD = 0.85;
    static final double LEECH = 0.04;
    static final ResourceLocation VIGOR_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "route_boon_vigor");
    static final ResourceLocation GALE_ID = ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "route_boon_gale");

    private record Pending(Boon a, Boon b, long until) {
    }

    private static final Map<UUID, EnumSet<Boon>> HELD = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private RouteBoons() {
    }

    public static EnumSet<Boon> held(ServerPlayer p) {
        EnumSet<Boon> h = HELD.get(p.getUUID());
        return h == null ? EnumSet.noneOf(Boon.class) : EnumSet.copyOf(h);
    }

    // ---------------------------------------------------------------- 見せる・選ぶ
    // 二つの加護を見せる (持っていないものから)。もう選んでいる途中なら、前の分を一つ目で決めてから
    public static void offer(ServerPlayer p) {
        Pending old = PENDING.remove(p.getUUID());
        if (old != null) {
            grant(p, old.a());
        }
        List<Boon> left = new ArrayList<>();
        for (Boon b : Boon.values()) {
            if (!held(p).contains(b)) {
                left.add(b);
            }
        }
        if (left.size() < 2) {
            return;
        }
        java.util.Collections.shuffle(left, new java.util.Random(p.getRandom().nextLong()));
        Pending n = new Pending(left.get(0), left.get(1), p.serverLevel().getGameTime() + CHOOSE_TICKS);
        PENDING.put(p.getUUID(), n);
        PacketDistributor.sendToPlayer(p, new Offer(true, n.a().ordinal(), n.b().ordinal(), CHOOSE_TICKS / 20));
        p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.8f, 1.1f);
    }

    // 選んだ (0 = 一つ目、1 = 二つ目)。見せていなければ何もしない
    public static void choose(ServerPlayer p, int index) {
        Pending n = PENDING.remove(p.getUUID());
        if (n == null) {
            return;
        }
        grant(p, index == 1 ? n.b() : n.a());
        PacketDistributor.sendToPlayer(p, Offer.closed());
    }

    static void grant(ServerPlayer p, Boon b) {
        HELD.computeIfAbsent(p.getUUID(), k -> EnumSet.noneOf(Boon.class)).add(b);
        attributes(p);
        Notice.gain(p, "message.skyabyss.boon.got", net.minecraft.network.chat.Component.translatable(b.key()));
        p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5f, 1.5f);
    }

    // 命と風は性質の上乗せ (ほかは傷の出入りで見る)
    static void attributes(ServerPlayer p) {
        EnumSet<Boon> h = held(p);
        var hp = p.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.removeModifier(VIGOR_ID);
            if (h.contains(Boon.VIGOR)) {
                hp.addTransientModifier(new AttributeModifier(VIGOR_ID, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
            if (p.getHealth() > p.getMaxHealth()) {
                p.setHealth(p.getMaxHealth());
            }
        }
        var speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(GALE_ID);
            if (h.contains(Boon.GALE)) {
                speed.addTransientModifier(new AttributeModifier(GALE_ID, 0.12, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    // 加護をすべて外す (順路の終わり・背を出た・抜けた)
    public static void clear(ServerPlayer p) {
        boolean had = HELD.remove(p.getUUID()) != null;
        boolean asked = PENDING.remove(p.getUUID()) != null;
        attributes(p);
        if (asked) {
            PacketDistributor.sendToPlayer(p, Offer.closed());
        }
        if (had) {
            Notice.hint(p, "message.skyabyss.boon.faded");
        }
    }

    // 抜けている人の分も消す (心臓を止めたとき、party の id から)
    public static void clear(MinecraftServer server, UUID id) {
        ServerPlayer p = server.getPlayerList().getPlayer(id);
        if (p != null) {
            clear(p);
        } else {
            HELD.remove(id);
            PENDING.remove(id);
        }
    }

    // 見せて、まだ選んでいないか (自己テスト)
    public static boolean offered(ServerPlayer p) {
        return PENDING.containsKey(p.getUUID());
    }

    // ---------------------------------------------------------------- 効き目
    // 傷の量: 受ける人の守り、与える人の力
    static float adjust(@Nullable UUID target, @Nullable UUID by, float amount) {
        EnumSet<Boon> t = target == null ? null : HELD.get(target);
        if (t != null && t.contains(Boon.WARD)) {
            amount *= (float) WARD;
        }
        EnumSet<Boon> b = by == null || by.equals(target) ? null : HELD.get(by);
        if (b != null && b.contains(Boon.MIGHT)) {
            amount *= (float) MIGHT;
        }
        return amount;
    }

    @SubscribeEvent
    static void onIncoming(LivingIncomingDamageEvent event) {
        if (HELD.isEmpty()) {
            return;
        }
        UUID target = event.getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        UUID by = event.getSource().getEntity() instanceof ServerPlayer p ? p.getUUID() : null;
        if (target != null || by != null) {
            event.setAmount(adjust(target, by, event.getAmount()));
        }
    }

    @SubscribeEvent
    static void onDamaged(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof ServerPlayer by && by != event.getEntity() && HELD.containsKey(by.getUUID())
                && HELD.get(by.getUUID()).contains(Boon.LEECH) && event.getNewDamage() > 0) {
            by.heal((float) (event.getNewDamage() * LEECH));
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 9 || (HELD.isEmpty() && PENDING.isEmpty())) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Pending n = PENDING.get(p.getUUID());
            if (n != null && p.serverLevel().getGameTime() >= n.until()) {
                choose(p, 0);
            }
            EnumSet<Boon> h = HELD.get(p.getUUID());
            if (h != null && h.contains(Boon.MENDING) && server.getTickCount() % 60 == 9 && p.isAlive() && p.getHealth() < p.getMaxHealth()) {
                p.heal(p.getMaxHealth() * 0.02f);
            }
        }
    }

    // 背を出た・抜けた人は外す
    @SubscribeEvent
    static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Titans.titanOfBack(event.getFrom()) != null && Titans.titanOfBack(event.getTo()) == null) {
            clear(p);
        }
    }

    // 倒れて起き直すと体は新しくなるので、命と風を付け直す
    @SubscribeEvent
    static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && HELD.containsKey(p.getUUID())) {
            attributes(p);
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HELD.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        HELD.clear();
        PENDING.clear();
    }

    // ---------------------------------------------------------------- 通信
    // サーバー → 人: 二つの加護を見せる (open = false で閉じる)
    public record Offer(boolean open, int a, int b, int seconds) implements CustomPacketPayload {
        public static final Type<Offer> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "route_boon_offer"));
        public static final StreamCodec<FriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buf, o) -> {
                    buf.writeBoolean(o.open());
                    buf.writeVarInt(o.a());
                    buf.writeVarInt(o.b());
                    buf.writeVarInt(o.seconds());
                },
                buf -> new Offer(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        static Offer closed() {
            return new Offer(false, 0, 0, 0);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        @Nullable
        public static Boon boon(int ordinal) {
            return ordinal >= 0 && ordinal < Boon.values().length ? Boon.values()[ordinal] : null;
        }
    }

    // 人 → サーバー: 選んだ加護 (0 か 1)
    public record Pick(int index) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "route_boon_pick"));
        public static final StreamCodec<FriendlyByteBuf, Pick> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeVarInt(p.index()), buf -> new Pick(buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(Offer.TYPE, Offer.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(() -> jp.revati.skyabyss.client.trial.RouteBoonScreen.receive(payload)))
                .playToServer(Pick.TYPE, Pick.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        choose(sp, payload.index());
                    }
                }));
    }
}
