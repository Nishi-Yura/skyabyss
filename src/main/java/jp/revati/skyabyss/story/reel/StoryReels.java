package jp.revati.skyabyss.story.reel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import jp.revati.skyabyss.SkyAbyss;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

// 語りの紙芝居 (Undertale 風のドット絵の語り。2026-10-27 の声「オープニングとか章の変わり目のシーンを豪華に。ドット絵でお話が流れる」)。
// 絵と文はクライアントの資源 (assets/skyabyss/story_reels/<id>.json、絵は textures/gui/story/<id>/、文は lang の story.skyabyss.<id>.<絵>)。
// サーバーは「流せ」と送り、見終わった (飛ばした) 合図を受けて、その語りのあとの出来事 (onDone で登録) を走らせる。
//   見たかどうかは人ごとに保存 (seen)。流している途中で抜けた人には、次に入ったとき最初から流し直す (あとの出来事が抜けないように)
//   自己テストの間と撮影の台本の間は流さず、すぐあとの出来事へ進む
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class StoryReels {
    public static final String OPENING = "opening";
    static final String KEY = "skyabyss_story_reels";
    private static final Map<String, Consumer<ServerPlayer>> AFTER = new HashMap<>();

    private StoryReels() {
    }

    // その語りを見終えた (飛ばした) あとに走らせる出来事
    public static void onDone(String reel, Consumer<ServerPlayer> after) {
        AFTER.put(reel, after);
    }

    static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(KEY)) {
            root.put(KEY, new CompoundTag());
        }
        return root.getCompound(KEY);
    }

    public static boolean seen(ServerPlayer p, String reel) {
        return data(p).getCompound("seen").getBoolean(reel);
    }

    // 語りを流す。流さない場合 (見た・偽の人・自己テスト・撮影) は、すぐあとの出来事を走らせる。流したら true
    public static boolean play(ServerPlayer p, String reel) {
        return play(p, reel, false);
    }

    public static boolean play(ServerPlayer p, String reel, boolean again) {
        if (p instanceof FakePlayer || jp.revati.skyabyss.SelfTestRunner.enabled() || (!again && System.getenv("SKYABYSS_SHOTS") != null)
                || (!again && seen(p, reel))) {
            if (!again) {
                finish(p, reel);
            }
            return false;
        }
        data(p).putString("pending", reel);
        PacketDistributor.sendToPlayer(p, new PlayPayload(reel));
        return true;
    }

    // 見終えた・飛ばした (クライアントから)。待っていた語りなら、記録してあとの出来事へ
    static void finish(ServerPlayer p, String reel) {
        CompoundTag d = data(p);
        boolean waited = reel.equals(d.getString("pending"));
        d.remove("pending");
        CompoundTag seen = d.getCompound("seen");
        boolean first = !seen.getBoolean(reel);
        seen.putBoolean(reel, true);
        d.put("seen", seen);
        if (waited || first) {
            Consumer<ServerPlayer> after = AFTER.get(reel);
            if (after != null) {
                after.accept(p);
            }
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            String pending = data(p).getString("pending");
            if (!pending.isEmpty()) {
                PacketDistributor.sendToPlayer(p, new PlayPayload(pending));
            }
        }
    }

    // /skyabyss reel <id> [player]: もう一度流す (見直し・撮影・試遊)。あとの出来事は走らせない
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).then(Commands.literal("reel")
                .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            PacketDistributor.sendToPlayer(p, new PlayPayload(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")));
                            return 1;
                        })
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> {
                                    ServerPlayer p = EntityArgument.getPlayer(ctx, "player");
                                    PacketDistributor.sendToPlayer(p, new PlayPayload(com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id")));
                                    return 1;
                                })))));
    }

    // ---------------------------------------------------------------- パケット (版 reel-1)
    public record PlayPayload(String reel) implements CustomPacketPayload {
        public static final Type<PlayPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "reel_play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, PlayPayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.reel()),
                buf -> new PlayPayload(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record DonePayload(String reel) implements CustomPacketPayload {
        public static final Type<DonePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "reel_done"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DonePayload> STREAM_CODEC = StreamCodec.of((buf, p) -> buf.writeUtf(p.reel()),
                buf -> new DonePayload(buf.readUtf()));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @EventBusSubscriber(modid = SkyAbyss.MODID, bus = EventBusSubscriber.Bus.MOD)
    static final class Net {
        @SubscribeEvent
        static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("reel-1")
                    .playToClient(PlayPayload.TYPE, PlayPayload.STREAM_CODEC,
                            (payload, context) -> jp.revati.skyabyss.client.story.StoryReelClient.play(payload.reel()))
                    .playToServer(DonePayload.TYPE, DonePayload.STREAM_CODEC, (payload, context) -> {
                        if (context.player() instanceof ServerPlayer p) {
                            context.enqueueWork(() -> finish(p, payload.reel()));
                        }
                    });
        }
    }
}
