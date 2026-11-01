package jp.revati.skyabyss.skin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import jp.revati.skyabyss.SkyAbyss;
import jp.revati.skyabyss.facility.Coins;
import jp.revati.skyabyss.notice.Notice;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// スキン (docs/design/54 の 6.1。物語と関係のない見た目だけの一式)。着ると今の防具の見た目がスキンに替わる (強さは防具のまま)。
//   持っているスキンと着ているスキンは人ごとの Data Attachment (周りの人にも同期。描き替えは client/SkinLayer と LowPolyArmorRenderer)
//   手に入れ方: 武具屋の会話の「スキンを見る」(仕立て屋) で鐘銭。開いた人だけが TAILOR_TICKS のあいだ買える (プロフィールの「衣装」つまみ)
//   形は tools/rough/lowpoly/armor/skin_one.py (一着一案)、入れるのは tools/install/install_skins.py。メッシュは lowpoly/armor/skin_<id>/<部位>
@EventBusSubscriber(modid = SkyAbyss.MODID)
public final class Skins {
    public record Skin(String id, int price) {
        public String nameKey() {
            return "skin.skyabyss." + id;
        }
    }

    // 並びは衣装棚の順 (足すなら末尾へ)
    public static final List<Skin> ALL = List.of(
            new Skin("samurai", 3000),
            new Skin("pirate", 2500),
            new Skin("knight", 2500),
            new Skin("astronaut", 4000));

    public record Data(List<String> owned, String worn) {
        public static final Data EMPTY = new Data(List.of(), "");
        public static final Codec<Data> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.listOf().optionalFieldOf("owned", List.of()).forGetter(Data::owned),
                Codec.STRING.optionalFieldOf("worn", "").forGetter(Data::worn)).apply(i, Data::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Data::owned,
                ByteBufCodecs.STRING_UTF8, Data::worn,
                Data::new);

        public boolean has(String id) {
            return owned.contains(id);
        }

        Data with(String id) {
            if (has(id)) {
                return this;
            }
            List<String> o = new ArrayList<>(owned);
            o.add(id);
            return new Data(List.copyOf(o), worn);
        }

        Data wearing(String id) {
            return new Data(owned, id);
        }
    }

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, SkyAbyss.MODID);
    public static final Supplier<AttachmentType<Data>> DATA = ATTACHMENTS.register("skins",
            () -> AttachmentType.builder(() -> Data.EMPTY).serialize(Data.CODEC).copyOnDeath().sync((holder, to) -> true, Data.STREAM_CODEC).build());

    // 仕立て屋を開いた人 → 買える期限 (ゲームの tick)
    static final long TAILOR_TICKS = 20 * 60 * 3;
    private static final Map<UUID, Long> TAILOR = new HashMap<>();

    private Skins() {
    }

    public static void register(IEventBus modEventBus) {
        ATTACHMENTS.register(modEventBus);
    }

    @Nullable
    public static Skin byId(String id) {
        for (Skin s : ALL) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }

    public static Data data(Player p) {
        return p.getData(DATA.get());
    }

    // 着ているスキン (無い・持っていない・知らない ID なら null)。手元の描き替えもここを読む
    @Nullable
    public static String worn(Player p) {
        Data d = data(p);
        return !d.worn().isEmpty() && d.has(d.worn()) && byId(d.worn()) != null ? d.worn() : null;
    }

    // 実績・図鑑などから渡す (持っていれば何もしない)
    public static boolean grant(ServerPlayer p, String id) {
        if (byId(id) == null || data(p).has(id)) {
            return false;
        }
        p.setData(DATA.get(), data(p).with(id));
        return true;
    }

    public static boolean wear(ServerPlayer p, String id) {
        if (!id.isEmpty() && !data(p).has(id)) {
            return false;
        }
        p.setData(DATA.get(), data(p).wearing(id));
        return true;
    }

    // 仕立て屋 (武具屋の会話から)。衣装のつまみを買える形で開く
    public static void openTailor(ServerPlayer p) {
        TAILOR.put(p.getUUID(), p.serverLevel().getGameTime() + TAILOR_TICKS);
        if (!(p instanceof FakePlayer) && p.connection != null) {
            PacketDistributor.sendToPlayer(p, new OpenTailor());
        }
    }

    public static boolean atTailor(ServerPlayer p) {
        Long until = TAILOR.get(p.getUUID());
        return until != null && p.serverLevel().getGameTime() <= until;
    }

    public enum Buy { OK, OWNED, FAR, COINS, UNKNOWN }

    public static Buy buy(ServerPlayer p, String id) {
        Skin s = byId(id);
        if (s == null) {
            return Buy.UNKNOWN;
        }
        if (data(p).has(id)) {
            return Buy.OWNED;
        }
        if (!atTailor(p)) {
            return Buy.FAR;
        }
        if (!Coins.take(p, s.price)) {
            return Buy.COINS;
        }
        grant(p, id);
        return Buy.OK;
    }

    static void act(ServerPlayer p, String action, String id) {
        switch (action) {
            case "buy" -> {
                Buy r = buy(p, id);
                Skin s = byId(id);
                switch (r) {
                    case OK -> {
                        Notice.unlock(p, "message.skyabyss.skin.bought", Component.translatable(s.nameKey()));
                        p.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 1f, 1.1f);
                    }
                    case COINS -> Notice.warn(p, "message.skyabyss.skin.coins", s.price);
                    case FAR -> Notice.warn(p, "message.skyabyss.skin.far");
                    default -> {
                    }
                }
            }
            case "wear" -> {
                if (wear(p, id)) {
                    p.playNotifySound(SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1f, 1f);
                }
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- パケット
    public record Action(String action, String id) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skin_action"));
        public static final StreamCodec<FriendlyByteBuf, Action> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(16), Action::action, ByteBufCodecs.stringUtf8(64), Action::id, Action::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record OpenTailor() implements CustomPacketPayload {
        public static final Type<OpenTailor> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(SkyAbyss.MODID, "skin_tailor"));
        public static final StreamCodec<FriendlyByteBuf, OpenTailor> STREAM_CODEC = StreamCodec.unit(new OpenTailor());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    @SubscribeEvent
    static void onPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(Action.TYPE, Action.STREAM_CODEC, (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer sp) {
                        act(sp, payload.action(), payload.id());
                    }
                }))
                .playToClient(OpenTailor.TYPE, OpenTailor.STREAM_CODEC,
                        (payload, context) -> context.enqueueWork(jp.revati.skyabyss.endgame.client.ProfileScreen::openTailor));
    }

    // 開発用: /skyabyss skin give <id>|all・wear <id>・clear・tailor
    @SubscribeEvent
    static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(SkyAbyss.MODID).requires(s -> s.hasPermission(2))
                .then(Commands.literal("skin")
                        .then(Commands.literal("give").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c -> {
                            String id = com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id");
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            if ("all".equals(id)) {
                                ALL.forEach(s -> grant(p, s.id));
                                return 1;
                            }
                            return grant(p, id) ? 1 : 0;
                        })))
                        .then(Commands.literal("wear").then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.word()).executes(c ->
                                wear(c.getSource().getPlayerOrException(), com.mojang.brigadier.arguments.StringArgumentType.getString(c, "id")) ? 1 : 0)))
                        .then(Commands.literal("clear").executes(c -> wear(c.getSource().getPlayerOrException(), "") ? 1 : 0))
                        .then(Commands.literal("tailor").executes(c -> {
                            openTailor(c.getSource().getPlayerOrException());
                            return 1;
                        }))));
    }
}
