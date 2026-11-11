package jp.revati.skyabyss.combat;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.util.Players;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

// ボスの登場の画面 (2026-11-11 の声「最初の登場のアニメーションや、画面にボスの名前表示がない」)。
//   RISE: 「〜が坑の底から這い出る」と数え下ろし (ticks = 出てくるまで)。主はまだ地の下
//   NAME: 主の名前の札 (上下の黒い帯、名前が中から広がり、肩書きが上に)。ticks = 札を出す長さ
// 文: gui.skyabyss.boss_intro.rise.<主> (無ければ gui.skyabyss.boss_intro.rise に名前を入れる)、肩書き gui.skyabyss.boss_intro.sub.<主>
@net.neoforged.fml.common.EventBusSubscriber(modid = SkyAbyss.MODID)
public final class BossIntro {
    public static final int RISE = 0;
    public static final int NAME = 1;
    public static final double RANGE = 48;

    private BossIntro() {
    }

    // sub: 肩書き (空なら gui.skyabyss.boss_intro.sub.<boss> を引く)
    // entity: 主の実体の番号 (名前の札の間、カメラが主の顔へ寄る。client/titan/BossCloseUp)。-1 なら寄らない
    public record Payload(int phase, Component name, String boss, int ticks, Component sub, int entity) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "boss_intro"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Payload::phase, ComponentSerialization.TRUSTED_STREAM_CODEC, Payload::name,
                ByteBufCodecs.STRING_UTF8, Payload::boss, ByteBufCodecs.VAR_INT, Payload::ticks, ComponentSerialization.TRUSTED_STREAM_CODEC, Payload::sub,
                ByteBufCodecs.VAR_INT, Payload::entity, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 名前の札を、決まった人たちへ (名前と肩書きを直に。層の主・淵の王・番人)
    public static void card(Iterable<ServerPlayer> players, Component name, Component sub, int ticks) {
        card(players, name, sub, ticks, null);
    }

    public static void card(Iterable<ServerPlayer> players, Component name, Component sub, int ticks, @javax.annotation.Nullable Entity boss) {
        Payload payload = new Payload(NAME, name, "", ticks, sub, boss == null ? -1 : boss.getId());
        boolean seen = false;
        for (ServerPlayer p : players) {
            if (!(p instanceof net.neoforged.neoforge.common.util.FakePlayer)) {
                PacketDistributor.sendToPlayer(p, payload);
                seen = true;
            }
        }
        // 顔のアップを見る人がいるときだけ、主を止める
        if (boss != null && seen) {
            hold(boss, ticks);
        }
    }

    // 主の近くの、主が見える人へ
    public static void send(Entity boss, int phase, String bossKey, int ticks) {
        send(boss, phase, bossKey, ticks, true);
    }

    // closeUp: 名前の札の間に顔のアップ (BossCloseUp) をするか。カメラの見せ場はボスだけ (中ボスは札だけ。2026-11-11 の声)
    public static void send(Entity boss, int phase, String bossKey, int ticks, boolean closeUp) {
        if (!(boss.level() instanceof ServerLevel level)) {
            return;
        }
        Payload payload = new Payload(phase, boss.getDisplayName(), bossKey, ticks, Component.empty(), phase == NAME && closeUp ? boss.getId() : -1);
        boolean seen = false;
        for (ServerPlayer p : Players.snapshot(level.players())) {
            if (p instanceof net.neoforged.neoforge.common.util.FakePlayer) {
                continue;
            }
            if (p.distanceToSqr(boss) < RANGE * RANGE && jp.revati.skyabyss.titan.group.GroupOwned.visibleTo(boss, p)) {
                PacketDistributor.sendToPlayer(p, payload);
                seen = true;
            }
        }
        // 顔のアップを見る人がいるときだけ、主を止める
        if (payload.entity() >= 0 && seen) {
            hold(boss, ticks);
        }
    }

    // 顔のアップの長さ (client/titan/BossCloseUp と同じ式。寄り 10 + 戻り 12 + 見せ場)
    public static int closeUpLength(int ticks) {
        return net.minecraft.util.Mth.clamp(ticks - 20, 32, 80);
    }

    // ---------------------------------------------------------------- アップの間は主を止める
    // カメラが主の顔の正面を撮るので、主が動く・向きを変えると画面がぐらぐら揺れる (2026-11-11 の声「絶対に動き止めて」)。
    // アップの間 (と少しの余り) は、主の場所・向きを毎 tick 元へ戻し、動きを 0 にし、考え (AI) を止める。
    // 登場の演出が先に終わって AI を戻しても、ここで止め直す。終わったら、ここで止めた分だけ AI を戻す
    private static final int HOLD_PAD = 4;

    private static final class Hold {
        final Entity e;
        final net.minecraft.world.phys.Vec3 pos;
        final float yaw, pitch, body, head;
        int left;
        boolean ownsNoAi;

        Hold(Entity e, int left) {
            this.e = e;
            this.pos = e.position();
            this.yaw = e.getYRot();
            this.pitch = e.getXRot();
            this.body = e instanceof net.minecraft.world.entity.LivingEntity le ? le.yBodyRot : yaw;
            this.head = e.getYHeadRot();
            this.left = left;
        }
    }

    private static final java.util.Map<Entity, Hold> HOLDS = new java.util.IdentityHashMap<>();

    public static void hold(Entity boss, int ticks) {
        if (boss.level().isClientSide) {
            return;
        }
        int left = closeUpLength(ticks) + HOLD_PAD;
        Hold old = HOLDS.get(boss);
        if (old != null) {
            old.left = Math.max(old.left, left);
            return;
        }
        Hold h = new Hold(boss, left);
        HOLDS.put(boss, h);
        pin(h);
    }

    // 試験用: 今止めているか
    public static boolean held(Entity boss) {
        return HOLDS.containsKey(boss);
    }

    private static void pin(Hold h) {
        Entity e = h.e;
        if (e instanceof net.minecraft.world.entity.Mob m) {
            if (!m.isNoAi()) {
                m.setNoAi(true);
                h.ownsNoAi = true;
            }
            m.getNavigation().stop();
            m.setTarget(null);
        }
        e.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        if (e.position().distanceToSqr(h.pos) > 1e-6) {
            e.setPos(h.pos);
        }
        e.setYRot(h.yaw);
        e.setXRot(h.pitch);
        e.yRotO = h.yaw;
        e.setYHeadRot(h.head);
        if (e instanceof net.minecraft.world.entity.LivingEntity le) {
            le.setYBodyRot(h.body);
            le.yBodyRotO = h.body;
            le.yHeadRotO = h.head;
        }
    }

    private static void release(Hold h) {
        if (h.ownsNoAi && h.e instanceof net.minecraft.world.entity.Mob m) {
            m.setNoAi(false);
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onEntityTick(net.neoforged.neoforge.event.tick.EntityTickEvent.Post event) {
        if (HOLDS.isEmpty()) {
            return;
        }
        Hold h = HOLDS.get(event.getEntity());
        if (h == null) {
            return;
        }
        if (--h.left < 0 || !h.e.isAlive()) {
            HOLDS.remove(h.e);
            release(h);
            return;
        }
        pin(h);
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onServerTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        // 消えた・読み込みから外れた主は、ここで手放す (毎 tick の知らせが来なくなるので)
        if (!HOLDS.isEmpty()) {
            HOLDS.values().removeIf(h -> {
                if (h.e.isRemoved()) {
                    release(h);
                    return true;
                }
                return false;
            });
        }
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void onStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        HOLDS.values().forEach(BossIntro::release);
        HOLDS.clear();
    }
}
