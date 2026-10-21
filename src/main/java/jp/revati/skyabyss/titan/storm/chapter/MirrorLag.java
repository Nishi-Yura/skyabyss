package jp.revati.skyabyss.titan.storm.chapter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.client.story.MirrorLagClient;
import jp.revati.skyabyss.lore.Lore;
import jp.revati.skyabyss.notice.Notice;
import jp.revati.skyabyss.story.Chapters;
import jp.revati.skyabyss.titan.storm.back.StormBackLayout;
import jp.revati.skyabyss.titan.storm.back.StormBackSetup;
import jp.revati.skyabyss.titan.storm.mob.StormMob;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 眼の間の遅れた影 (docs/design/34 の 1 章・4.4、12 の 4.3)。三章の人が雷翼の背の眼の間に入ると、一度だけ、近くの羽鏡に自分の姿が映り、
// 映った影が少し遅れて動く。説明はしない (正体は三章では言わない。01)。
//   条件   雷翼の背 (storm_back)、章が三章 (Chapters.STORM)、伝承 ch3/mirror_lag をまだ持たない、眼の間に 5 秒いる、
//          3〜10 ブロック先に羽鏡があり、その鏡で映した位置が眼の間の中に収まる
//   映し方 鏡の台の中心を通る面 (人から見て遠い軸と直角) で、人の位置を折り返す。面と影の遅れ (DELAY tick)・長さはクライアントへ送る (MirrorLagPayload)。
//          クライアントは自分の世界にだけ人影 (見る者の形。06) を置き、DELAY 前の自分の位置と向きを折り返して動かす (client/story/MirrorLagClient)。
//          見つめ続けると、影が先にこちらを向いて薄れる
//   文     0 秒「磨いた銅の羽に、自分の姿が映っている。」→ 3 秒「……映った影が、少し遅れて動いた。」→ 9 秒 伝承「遅れて映る影」
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class MirrorLag {
    public static final int DELAY = 24;
    public static final int DURATION = 200;
    public static final int DWELL = 5;
    public static final double MIN_DIST = 3;
    public static final double MAX_DIST = 10;
    private static final String PROTOCOL_VERSION = "ch3g-1";

    // 人 → 眼の間にいた秒数 (保存しない)
    private static final Map<UUID, Integer> DWELLING = new HashMap<>();
    // 見せた人 (伝承が記されるまでの間に二度見せない。保存しない)
    private static final java.util.Set<UUID> SHOWN = new java.util.HashSet<>();

    // 鏡の面: axis 0 = x が一定の面 (x = plane)、1 = z が一定の面
    public record Plane(int axis, double plane) {
        public Vec3 reflect(Vec3 p) {
            return axis == 0 ? new Vec3(2 * plane - p.x, p.y, p.z) : new Vec3(p.x, p.y, 2 * plane - p.z);
        }

        public float reflectYaw(float yaw) {
            return axis == 0 ? -yaw : 180f - yaw;
        }
    }

    public record Payload(int axis, double plane, int delay, int duration) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "ch3_mirror_lag"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Payload> STREAM_CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeVarInt(p.axis());
                    buf.writeDouble(p.plane());
                    buf.writeVarInt(p.delay());
                    buf.writeVarInt(p.duration());
                },
                buf -> new Payload(buf.readVarInt(), buf.readDouble(), buf.readVarInt(), buf.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    private MirrorLag() {
    }

    @SubscribeEvent
    static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // ラムダの中身はクライアントで受けたときだけ動く (専用サーバーでも安全)
        event.registrar(PROTOCOL_VERSION).optional()
                .playToClient(Payload.TYPE, Payload.STREAM_CODEC, (payload, context) -> MirrorLagClient.receive(payload));
    }

    // 人の位置から、いちばん近い羽鏡の面 (映した位置が眼の間に収まるもの)。なければ null
    @Nullable
    public static Plane planeFor(ServerLevel level, Vec3 at) {
        Plane best = null;
        double bestD = Double.MAX_VALUE;
        for (int c = 0; c < 5; c++) {
            for (int r = 0; r < 5; r++) {
                BlockPos cell = StormBackLayout.gridCell(c, r);
                if (!level.getBlockState(cell).is(StormBackSetup.MIRROR.get())) {
                    continue;
                }
                Plane p = planeAt(cell, at);
                double d = Math.hypot(at.x - (cell.getX() + 0.5), at.z - (cell.getZ() + 0.5));
                if (p != null && d < bestD) {
                    best = p;
                    bestD = d;
                }
            }
        }
        return best;
    }

    // 鏡の台 cell で at を映す面 (遠い軸と直角)。距離と、映した位置が眼の間の中かを見る
    @Nullable
    public static Plane planeAt(BlockPos cell, Vec3 at) {
        double cx = cell.getX() + 0.5;
        double cz = cell.getZ() + 0.5;
        double dx = at.x - cx;
        double dz = at.z - cz;
        double d = Math.hypot(dx, dz);
        if (d < MIN_DIST || d > MAX_DIST) {
            return null;
        }
        Plane p = Math.abs(dx) >= Math.abs(dz) ? new Plane(0, cx) : new Plane(1, cz);
        Vec3 r = p.reflect(at);
        StormBackLayout.Area hall = StormBackLayout.EYE_HALL;
        if (r.x < hall.x1() + 1 || r.x > hall.x2() || r.z < hall.z1() + 1 || r.z > hall.z2()) {
            return null;
        }
        return p;
    }

    public static boolean eligible(ServerPlayer p) {
        return !(p instanceof FakePlayer) && p.level().dimension() == StormMob.STORM_BACK && Chapters.of(p) == Chapters.STORM
                && !Lore.state(p).has(Ch3Ground.PAGE_MIRROR) && StormBackLayout.EYE_HALL.contains(p.blockPosition());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 7 || player.level().dimension() == Level.OVERWORLD) {
            return;
        }
        if (!eligible(player) || SHOWN.contains(player.getUUID())) {
            DWELLING.remove(player.getUUID());
            return;
        }
        int t = DWELLING.merge(player.getUUID(), 1, Integer::sum);
        if (t < DWELL) {
            return;
        }
        Plane plane = planeFor(player.serverLevel(), player.position());
        if (plane != null) {
            show(player, plane);
        }
    }

    // 影を見せる (自己テストからも)。伝承は数秒あとに記す (一度だけ)
    public static void show(ServerPlayer player, Plane plane) {
        DWELLING.remove(player.getUUID());
        SHOWN.add(player.getUUID());
        PacketDistributor.sendToPlayer(player, new Payload(plane.axis(), plane.plane(), DELAY, DURATION));
        Notice.narrate(player, "message.skyabyss.ch3g.mirror.1");
        StormChapter.later(player.server, 60, () -> Notice.narrate(player, "message.skyabyss.ch3g.mirror.2"));
        StormChapter.later(player.server, 180, () -> Lore.unlock(player, Ch3Ground.PAGE_MIRROR, true));
    }

    static void forget() {
        DWELLING.clear();
        SHOWN.clear();
    }
}
