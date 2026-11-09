package jp.revati.skyabyss.dungeon;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.world.continent.ContinentPois;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 層の門で「ふつうに入る / 深く潜る」を選ぶ画面 (2026-11-09 の声「中ダンジョン、しゃがんで入ったら強くなるの表記どこにも書いてないから、
// わかりづらい。しゃがむとかじゃなくて、なんか視覚的にわかる別の方法ないかな」)。前はしゃがんで門を使うと深く潜った。
// 主を一度倒した人が門を使い、まだ組の写しに加わるのでなければ、選ぶ画面を開く (Open)。押した答え (Choose) を、門のそばにいるか確かめて入れる
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class DeepChoice {
    // 門からこれより離れていたら答えを受けない
    static final double NEAR = 8;

    private DeepChoice() {
    }

    // サーバー → 人: 画面を開く (ダンジョンの id・今日の様子の名前の鍵と説明の鍵)
    public record Open(String dungeon, String moodName, String moodDesc) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dungeon_deep_open"));
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.of((buf, o) -> {
            buf.writeUtf(o.dungeon, 64);
            buf.writeUtf(o.moodName, 128);
            buf.writeUtf(o.moodDesc, 128);
        }, buf -> new Open(buf.readUtf(64), buf.readUtf(128), buf.readUtf(128)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    // 人 → サーバー: 選んだ答え
    public record Choose(String dungeon, boolean deep) implements CustomPacketPayload {
        public static final Type<Choose> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "dungeon_deep_choose"));
        public static final StreamCodec<FriendlyByteBuf, Choose> STREAM_CODEC = StreamCodec.of((buf, c) -> {
            buf.writeUtf(c.dungeon, 64);
            buf.writeBoolean(c.deep);
        }, buf -> new Choose(buf.readUtf(64), buf.readBoolean()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("dungeon-deep-1")
                .playToClient(Open.TYPE, Open.STREAM_CODEC, (payload, context) -> context.enqueueWork(
                        () -> jp.revati.skyabyss.client.dungeon.DeepChoiceScreen.open(payload)))
                .playToServer(Choose.TYPE, Choose.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer p) {
                        choose(p, payload);
                    }
                }));
    }

    // 門を使った: 選べる人には画面を、ほかはふつうに入れる
    public static void use(ServerPlayer p, Dungeon d) {
        if (Dungeons.clears(p, d) <= 0 || Dungeons.joinsRun(p, d)) {
            Dungeons.enter(p, d, false);
            return;
        }
        DungeonMood m = DungeonMood.today(d);
        PacketDistributor.sendToPlayer(p, new Open(d.id, m.nameKey(), m.descKey()));
    }

    static void choose(ServerPlayer p, Choose c) {
        Dungeon d = Dungeon.byId(c.dungeon());
        if (d == null || d.mini || DungeonWorld.in(p)) {
            return;
        }
        BlockPos gate = ContinentPois.extraOf(p.serverLevel(), d.poiKind(), "gate");
        if (gate == null || gate.distToCenterSqr(p.position()) > NEAR * NEAR) {
            return;
        }
        Dungeons.enter(p, d, c.deep());
    }
}
